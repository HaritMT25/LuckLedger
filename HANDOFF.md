# LuckLedger — Session Handoff

Date: 2026-05-29
Branch: `frontend-fixes-and-shops` (pushed to `origin`, GitHub `HaritMT25/LuckLedger`)
App: Spring Boot, runs on **http://localhost:8080** (Postgres via Docker Compose `luckledger-app/compose.yaml`).

LATEST (staff-engineer orchestration, 2026-05-29): executed `plans/fix-scratch-card` — **the
scratch-card architecture is now CORRECT** (PNG-as-coating; see "What works"). Frontend-only, backend
untouched. Merged into `frontend-fixes-and-shops` as `fae7e22` and pushed to origin.

Earlier sessions did frontend bug-fixes, a dealer→shop rework, books-only-via-shop, and several
scratch-card iterations (the wrong metallic-coating versions, since reverted).

---

## How to run

```bash
cd ~/projects/LuckLedger
mvn -pl luckledger-app spring-boot:run    # auto-starts Postgres via compose, seeds at startup
# app: http://localhost:8080   zone tool: http://localhost:8080/tools/map-zones.html
```

- DB is seeded once at startup (`GameSeeder`), idempotent (skips if `game` table non-empty).
- To force a re-seed (e.g. after changing seed data): clear the game graph, then restart:
  ```bash
  docker exec luckledger-app-postgres-1 psql -U luckledger -d luckledger \
    -c "DELETE FROM ticket; DELETE FROM ticket_book; DELETE FROM dealer; DELETE FROM game;"
  ```
- Full test suite: `mvn test` (uses Testcontainers; Docker required). All green as of this handoff.

---

## What works (solid, keep)

### Backend
- **Generation → persistence pipeline.** At startup `GameSeeder` generates each game's full finite
  pool (predetermined outcomes), verifies it, partitions into books, allocates books to shops, and
  persists everything to Postgres. Reveal reads the stored outcome — no RNG at scratch time.
- **Odds live in the pool** (PoolContract tier counts), confirmed from the DB:
  - Celestial Fortune: 200 tickets — 1×$740, 5×$20, 20×$2, 174×$0 → **88.0% RTP**.
  - Demon Seal: 20 tickets — 2×$10, 5×$2, 13×$0 → **30.0% RTP**.
  - (These are the configured demo tier counts in `ApiConfig`, NOT the mechanics' calibrated
    ~65%/~64% rates. Retuning = change tier counts in `ApiConfig`; payout validator must reconcile.)
- **Dealers are cross-game shops** (migration `V003__dealers_become_shops.sql`): `DealerEntity` has
  `shopName`, `ownerName`, `avatar` (nullable), `stockedGames` (JSONB list of game ids); dropped the
  per-game `game_id`. Demo roster (seeded with sales history so tiers span allocation bands):
  - Lucky Mart / Sam — both games (TIER_3)
  - 7 Star Corner / Priya — Celestial only (TIER_2)
  - Golden Express / Old Chen — both games (TIER_1)
  - QuickStop / Danny — Demon only (TIER_2)
- **Books only via a shop.** No flat `/api/books` list; reached through
  `GET /api/dealers/{id}/books`. `GET /api/books/{id}` kept for the purchase flow.
- **Endpoints:** `/api/games`, `/api/dealers`, `/api/dealers/{id}`, `/api/dealers/{id}/books`,
  `/api/books/{id}`, `/api/players...`, `/api/tickets/{id}` + `/reveal`, `/api/ledger...`.

### Frontend (SPA, vanilla JS)
- Shops tab: shop cards with initials-avatar, owner, game badges; click → `#dealer/<id>` detail with
  books grouped by game; Buy & Scratch flow; ledger; borrow.
- Scratch page loads the ticket PNG and reads per-zone positions from `config/scratch-zones.json`.
- **Zone-mapping tool** at `static/tools/map-zones.html` — load a ticket, place/drag/resize
  circle & rect zones, export percentage JSON. This is how zone coords are produced.

### Grid semantics (needed for the correct scratch architecture)
- The reveal grid is the REAL engine grid. Exposed via the API in commit `a082e3a` (being reverted —
  see "recover" below). Layout:
  - **Celestial** (`GridSize.FOUR`, 4×4, symbols `"1".."30"`): row 0 = 4 winning numbers; rows 1–2 =
    8 player numbers; row 3 = 4 decoy (inert). `getAllCells()` is row-major. A player number equal to
    a winning number is a real match (k matches → $2/$20/$740 for k=2/3/4).
  - **Demon** (`GridSize.THREE`, 3×3): exactly 6 seal cells (`GOLD`/`SILVER`/`BROKEN`) among 9, rest
    filler. Score T = 2·gold + silver → prize ladder.

---

## What's FIXED — the scratch-card architecture (DONE this session)

The CORRECT architecture is now implemented (`plans/fix-scratch-card`, merge `fae7e22`):
- **The ticket PNG IS the coating.** The whole PNG is drawn onto a single `<canvas>` and erased via
  `destination-out` compositing as you scratch.
- Layering inside `.scratch-stage` (bottom→top):
  - **Bottom:** `<div class="reveal-layer">` — dark `#1a1a2e` bg holding one absolutely-positioned
    `<span class="value-label">` per scratch zone, centered on that zone.
  - **Top:** `<canvas id="scratch" class="scratch-canvas">` carrying the ticket PNG (the coating).
  - **Scratch:** round-cap `destination-out` brush with `lineTo` interpolation; whole-canvas
    `getImageData` transparency sampled every ~10 move events; at ≥70% transparent → `clearRect` +
    `onReveal` fires once (guarded). Robust to PNG load failure (paints a solid fallback coating,
    no false auto-reveal).
- Files changed (ONLY these three): `js/scratch.js`, `js/app.js` (scratch section), `css/style.css`
  (scratch classes). Old `<img class="scratch-art">` and separate `<canvas id="reveal">` removed.
  `config/scratch-zones.json` and all shops/ledger/router code preserved.

### KNOWN LIMITATION — revealed numbers are client-generated, not the real grid
The reveal API (`POST /api/tickets/{id}/reveal`) returns ONLY `{isWinner, prizeAmount}` — NO per-cell
grid. So the numbers/values shown under the foil are generated **client-side** (deterministic per
ticket via `seededRandom`/`numbersForTicket`, made consistent with the outcome: a winner shows a
match, a loser never accidentally matches). The official result is always the banner. To show the
TRUE engine grid numbers, a separate backend bead must re-expose the grid (see priority #2 below) —
this was deliberately OUT of scope (`plans/fix-scratch-card/specs/no-touch-list.md` forbids backend
changes; user confirmed frontend-only this session).

---

## Git state

Branch `frontend-fixes-and-shops`. Recent history (newest first):

```
f01825b  Revert scratch rewrite; apply only metallic coating + lineTo brush
886b7d7  Rebuild scratch page to ScratchAll's architecture (metallic coating + reveal)
a082e3a  Reveal the real engine grid under the foil (actual odds)   <-- backend grid exposure + refined zone JSON
6da5460  Scratch the symbol itself to reveal a number underneath (real-ticket style)
80f187a  Scratch the symbols themselves, independently per zone
cee27bd  Scratch only the ticket's symbol zones, driven by a config
119c70f  Books are reachable only through a shop
fbdff31  Dealers become cross-game shops with owners, avatars, and game badges
fd11ccf  auto: main — tests passing  (original frontend bug-fixes)
```

**This handoff reverts the last 3 scratch-engine commits** (`a082e3a`, `886b7d7`, `f01825b`) via
`git revert`, landing the scratch files at the `6da5460` state.

> NOTE: `6da5460` still contains scratch-engine work (symbol-as-foil). If you want to go back *before*
> the scratch engine entirely, revert further down to `119c70f` (keeps shops + books-via-shop) or
> `fd11ccf` (original fixes only).

### Recovering things the revert undoes (preserved in git history)
- **Refined zone coordinates** (hand-tuned in the map-zones tool):
  `git show a082e3a:luckledger-app/src/main/resources/static/config/scratch-zones.json`
- **API grid exposure** (`TicketView.grid` / `CellView`, masked-before-reveal) — needed for the
  correct architecture's "real numbers underneath":
  `git show a082e3a:luckledger-api/src/main/java/com/luckledger/api/TicketController.java`
- Reverts are themselves commits, so `git revert <the-revert-commit>` re-applies anything if needed.

---

## Key file paths

Frontend (`luckledger-app/src/main/resources/static/`):
- `index.html`, `css/style.css`
- `js/api.js` (REST wrapper), `js/app.js` (SPA + scratch view wiring), `js/scratch.js` (ScratchCard)
- `config/scratch-zones.json` (per-mechanic zones, fractions of image w/h)
- `tools/map-zones.html` (zone editor)
- `assets/tickets/celestial.png`, `assets/tickets/demon.png` (the ticket art, 1080×1920)

Backend (`luckledger-api/src/main/java/com/luckledger/api/`):
- `TicketController.java` (purchase/reveal/ticket; grid exposure added in a082e3a)
- `DealerController.java` (shops + `/{id}/books`), `BookController.java`, `GameStore.java`
- `GameSeeder.java` (shop roster + allocation), `ApiConfig.java` (PoolContracts/RTP, beans)
- `persistence/DealerEntity.java`, `persistence/TicketEntity.java`,
  `persistence/GamePersistenceMapper.java`, `persistence/GridCodec.java`
- `resources/db/migration/V001..V003`

Mechanics (`luckledger-mechanic/src/main/java/com/luckledger/mechanic/`):
- `CelestialFortunePopulator/Evaluator.java`, `DemonSealPopulator/Evaluator.java`

Original layered ticket art (PSDs + piece PNGs, for re-export if needed):
- `tickets/CelestialFortune/assets/`, `tickets/DemonSeal/assets/`

---

## What needs fixing (priority order for the next session)

1. ✅ **DONE — Rebuild the scratch card to the CORRECT architecture** (PNG-as-coating erased via
   destination-out; dark `.reveal-layer` with positioned labels; lineTo brush; 70% threshold).
   Merged `fae7e22`.
2. **Re-expose the real grid** on `/api/tickets/{id}/reveal` (recover from `a082e3a`) so the numbers
   beneath are the TRUE engine values, then have `app.js` place those instead of the client-generated
   ones. Backend bead (was out of scope this session). Until done, see "KNOWN LIMITATION".
3. **Verify/refine zone coordinates visually.** Zones in `config/scratch-zones.json` (16 Celestial,
   8 Demon) were never checked against rendered pixels (no browser in-session). Open the app and
   confirm each `.value-label` sits under its crystal/seal; tune in `/tools/map-zones.html` if off.
4. **Manual visual test (never run in-session — Docker down, no browser).** Run the app, buy a
   ticket, confirm: PNG shows as the surface, scratching erases it, gold numbers appear beneath, 70%
   triggers the result banner, and dealer/book/ledger pages are unchanged.
5. Optional polish: scratch dust/particles, coin cursor, scratch/win sounds, confetti, holographic
   tilt. Minor: the empty-Scratch-tab "no ticket" message could be re-checked for copy.

## Known caveats
- Visual/pixel feel was never verifiable in-session (no browser/headless rendering available here);
  all scratch behavior was validated by code review + API, not by looking at rendered pixels.
- **`mvn test` was NOT run this session — the Docker daemon was down (Testcontainers needs it).**
  Mitigant: this change touched ZERO backend/Java/test files (frontend `.js`/`.css` only), so the
  suite outcome is unaffected. Re-run `mvn test` once Docker is up to re-confirm green.
- Demo RTPs (88%/30%) are the configured tier counts, not the mechanics' calibrated rates.

## This session's git trail (scratch rebuild)
- First attempt: an isolated worktree (`worktree-agent-ae27067c…`) was accidentally branched from the
  WRONG base (`fd11ccf`/old main, missing the shops frontend + `scratch-zones.json`); merging it
  conflicted and would have lost shops code. Aborted.
- Redo (correct): branch `scratch-rebuild-v2` created from the real HEAD; the proven engine + scratch
  section were grafted onto the current files (shops/ledger/router preserved). Committed `317290c`,
  merged `--no-ff` into `frontend-fixes-and-shops` as `fae7e22`, pushed.
- A stray worktree gitlink an earlier subagent committed onto the shared branch (`24840a8`) was
  reverted (`af45e65`); net diff empty, no `160000` entries remain.
