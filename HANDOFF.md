# LuckLedger — Session Handoff

Branch: `better-complete`
App: Spring Boot 3.5 / Java 21, runs on **http://localhost:8080** (Postgres via Docker Compose
`luckledger-app/compose.yaml`). Vanilla-JS SPA served from `luckledger-app/src/main/resources/static/`.

This is a gambling-awareness scratch-card simulator. Virtual coins only — no real money, no purchase,
no cash-out. The point is that the ledger makes the house's edge visible.

---

## How to run

```bash
cd ~/projects/LuckLedger
mvn -pl luckledger-app spring-boot:run    # auto-starts Postgres via compose, seeds at startup
# app: http://localhost:8080   zone tool: http://localhost:8080/tools/map-zones.html
```

- DB is seeded once at startup (`GameSeeder`), idempotent (skips if the `game` table is non-empty).
- Force a re-seed (e.g. after changing seed data) by clearing the game graph, then restart:
  ```bash
  docker exec luckledger-app-postgres-1 psql -U luckledger -d luckledger \
    -c "DELETE FROM ticket; DELETE FROM ticket_book; DELETE FROM dealer; DELETE FROM game;"
  ```
- Full test suite: `mvn test` (Testcontainers → Docker required). Green as of this handoff.

---

## Architecture at a glance

Multi-module Maven build under `com.luckledger`. The domain is pure Java (no Spring); Spring lives
in `luckledger-api` and `luckledger-app`.

- **luckledger-domain** — pure Java, the heart of the model: `com.luckledger.domain.pool`
  (PoolContract, PoolValidator, PoolFactory), mechanic types, generation, ledger, player, scratch.
- **luckledger-pool** — placeholder module (only `.gitkeep`); the actual pool classes live in
  `luckledger-domain`. Left in the reactor for historical module mapping.
- **luckledger-mechanic** — GameMechanic impls, grid populators/evaluators, `NearMissAnalyzer`.
- **luckledger-generation** — `GenerationPipeline`, `VerificationSuite`, theme skinning.
- **luckledger-distribution** — `BookPartitioner`, `DealerAllocator`, `DealerRegistry`.
- **luckledger-player** — `BankService`, `LedgerService`, insight generators.
- **luckledger-scratch-flow** — `TicketPurchaseService`, `ScratchRevealService` (domain-level).
- **luckledger-api** — 38 REST endpoints across 14 controllers + JPA persistence + Spring Security.
- **luckledger-cli** — generation CLI / `GameOrchestrator`.
- **luckledger-app** — `@SpringBootApplication`, Flyway (`V001`–`V007`), `GameSeeder`, the SPA.

---

## What is real and done (Phases 0–3 of the improvement plan)

### Persistence — real, not in-memory
Everything is Postgres-backed via JPA: players, dealers, games, ticket books, tickets, and the
append-only transaction ledger (see `luckledger-api/.../persistence/`). Migrations `V001`–`V007`.
`ApiConfig` wires the pure-domain services into beans; `TransactionRecorder` is the Postgres-backed
`JpaTransactionRecorder`.

### Generation → persistence pipeline
At startup `GameSeeder` generates each game's full finite pool (predetermined outcomes), verifies it
(verification is mandatory — generation throws without it), partitions into books, allocates books to
shops, and persists it all. Reveal reads the stored outcome — no RNG at scratch time.

### RTP lives in the pool (seeded demo games in `ApiConfig`)
- **Celestial Fortune** — 1000 tickets @ $5 across 25 books; tiers 2×$740, 65×$20, 250×$2 →
  RTP **65.6%** (near the mechanic's natural ~65.2%). ~317 winners ≈ 1-in-3.
- **Demon Seal** — 500 tickets @ $5 across 20 books; tiers 2×$300, 2×$100, 8×$25, 30×$10, 40×$4,
  80×$2 → RTP **64.8%** (near the mechanic's natural ~64.4%). ~162 winners ≈ 1-in-3.

Retuning = change tier counts in `ApiConfig`; the payout validator must reconcile. **Payout ratio is
sacred** — nothing outside the pool contract changes RTP.

### Grid exposure — done and real
`POST /api/tickets/{id}/reveal` returns the ticket's **real** themed grid AND the underlying mechanic
grid (both verified at generation). The frontend draws the player's actual symbols under the coating;
the numbers/seals beneath the foil are the TRUE engine values, not client-generated. The reveal is
idempotent (first reveal flips persisted flags; later reveals return stored flags without re-crediting).

Grid semantics:
- **Celestial** (`GridSize.FOUR`, 4×4, symbols `"1".."30"`): row 0 = 4 winning numbers; rows 1–2 = 8
  player numbers; row 3 = 4 decoy (inert). A player number equal to a winning number is a real match
  (k matches → $2/$20/$740 for k = 2/3/4).
- **Demon** (`GridSize.THREE`, 3×3): exactly 6 seal cells (`GOLD`/`SILVER`/`BROKEN`) among 9, rest
  filler. Score T = 2·gold + silver → prize ladder.

### Near-miss engineering (awareness feature)
Both seeded games use `NearMissMode.REALISTIC`. `GenerationPipeline.ENGINEERED_NEAR_MISS_RATE = 0.35`:
35% of the **$0 losers** are rearranged into near-misses, uniformly spread across books (each book gets
its proportional share). RTP-neutral — only losing grids are rearranged, tier counts and payouts are
untouched. `NearMissInsight` surfaces this in the ledger.

### Purchase / reveal integrity (concurrency-safe)
Purchase and reveal are separate operations. Both use pessimistic row locks in a **fixed writer lock
order to make deadlock impossible**:
- **Purchase** (`PurchaseGateway`): book → ticket → player. Book lock serializes the depleted-check and
  sale-cursor advance; ticket lock + sold-check makes the sale row-race-safe; player locked last
  (insufficient balance rolls back the whole tx). Dealer depletion counter is an atomic in-place
  increment (no lock, no lost update). Retired campaigns reject new purchases (409).
- **Reveal** (`RevealGateway`): ticket → player. Ownership is enforced — reveal is where money enters
  an account, so a ticket owned by someone else is refused **403** (generic body), an unsold ticket is
  **409**. Purchase is not gated (players are anonymous; the buyer is whoever holds the id).

### Auth — fail-closed
One master (operator) account, BCrypt-hashed. `/api/master/**` requires `ROLE_MASTER` (session-based
login at `POST /api/auth/login`); `/api/house/**` is deliberately **public read-only**. If no master
password is configured (`luckledger.master.password` / `LUCKLEDGER_MASTER_PASSWORD`), `SecurityConfig`
mints a random one-time password with `SecureRandom` and logs it once at WARN — no guessable committed
default. See `SecurityConfig`.

### Campaigns (master dashboard)
Migration `V007`, `CampaignService` + `CampaignController`. Operators create games from the dashboard
with a live RTP preview, view per-game analytics, and drive lifecycle (activate / retire). Restock is
campaign-aware (regenerates identical economics from the stored config). Frontend: campaign cards,
create form, analytics route in `app.js`.

### Validation + error mapping
Bean validation on request DTOs; `GlobalExceptionHandler` maps domain/validation exceptions to a
consistent JSON envelope (`{ code, message }`) with correct HTTP statuses.

### Book metadata visibility
Server-side visibility tiers on book metadata (`V006`) so the frontend only sees what a shop is meant
to expose.

---

## Frontend (SPA, vanilla JS)

`luckledger-app/src/main/resources/static/`:
- `index.html`, `css/style.css`
- `js/api.js` (REST wrapper + CSRF), `js/app.js` (SPA router + all views + scratch wiring),
  `js/scratch.js` (canvas scratch engine), `js/sounds.js` (pure WebAudio SFX)
- `config/scratch-zones.json` — **presentation geometry only**: WHERE each scratchable panel sits on
  the ticket art (fractions of image w/h). The authoritative outcome is the backend mechanic grid.
  `ScratchZoneContractTest` (luckledger-app) pins the scratch-zone counts/order to the mechanics —
  **Celestial Fortune = 16** (4 win coins above 12 crystals), **Demon Seal = 6** seals — so the config
  cannot silently drift from the engine.
- `tools/map-zones.html` — zone editor (place/drag/resize circle & rect zones, export percentage JSON).
- `assets/tickets/celestial.png`, `assets/tickets/demon.png` — ticket art.

Scratch engine: the ticket PNG IS the coating, drawn onto a `<canvas>` and erased via `destination-out`
per scratch zone (each zone clears independently at ~70% cleared). A dark `.reveal-layer` beneath holds
the real per-zone values. `zonereveal`/`scratchstroke` custom events drive UI feedback (tile pop,
progress line, particle FX, sounds). Falls back to whole-surface scratch if the zone config can't match.

Accessibility: the reveal banner is `role="status" aria-live="polite"`; the toast is `role="alert"` on
error else `role="status"`; `#scratch-progress` is `aria-live` so panel progress is announced.

Sound: `js/sounds.js` is pure WebAudio synthesis (zero audio assets) — throttled scratch noise, a zone
ping, a win fanfare, a lose thud. Lazy `AudioContext` on first gesture; 🔊/🔇 toggle in the player bar,
persisted to `localStorage` (`luckledger.sound`), default on. Independent of `prefers-reduced-motion`
(which gates visual FX only). A gold coin cursor (inline-SVG data-URI) sits on `.scratch-canvas`.

---

## What a next contributor should know

- **Do not change RTP outside the pool contract.** Tier counts in `ApiConfig` (or a campaign's config)
  are the single source of truth; the payout validator enforces it.
- **Ledger is append-only** — no updates/deletes. Running totals on the player are updated
  transactionally alongside each ledger insert.
- **Purchase and reveal stay separate.** Keep the writer lock order (book → ticket → player for
  purchase; ticket → player for reveal) if you touch either gateway.
- **`luckledger-pool` is empty on purpose** — pool code is in `luckledger-domain.pool`.
- Endpoint count is currently **38** (grep `@GetMapping`/`@PostMapping` across `luckledger-api`);
  update `CLAUDE.md` if you add/remove any.
- Tests need Docker (Testcontainers). Run `mvn test` before handing off.

## Possible next work
- Verify/refine zone coordinates visually against rendered pixels in `/tools/map-zones.html`.
- Broader master analytics; more educational insights from the ledger.
- Further polish on the scratch stage (already has particles, tilt, confetti, twinkles, sounds).
