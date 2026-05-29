# LuckLedger — Session Handoff

Date: 2026-05-29
Branch: `frontend-fixes-and-shops` (pushed to `origin`, GitHub `HaritMT25/LuckLedger`)
App: Spring Boot, runs on **http://localhost:8080** (Postgres via Docker Compose `luckledger-app/compose.yaml`).

This session did frontend bug-fixes, a dealer→shop rework, books-only-via-shop, and several
scratch-card iterations. The **scratch-card architecture is wrong** (see below) and is being reverted.
A fresh session will reimplement it with correct instructions.

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

## What's BROKEN — the scratch-card architecture

Current implementation is wrong and is being reverted:
- Draws metallic rectangles/circles as a **coating layer on top of** the ticket.
- Scratching reveals a number on an **opaque circle/rectangle**.
- The ticket PNG art is treated as a passive **background decoration**.

### CORRECT architecture (to build in the fresh session — DO NOT build yet)
- **The ticket PNG IS the coating.** The crystals/seals drawn in the PNG ARE what you scratch off.
- Underneath the PNG is a **dark background with the revealed numbers/values**.
- The PNG itself is erased via `destination-out` compositing.
- Celestial: scratch a crystal → the crystal disappears, the number underneath appears.
- Demon: scratch a seal → the golden talisman disappears, gold/silver/broken status appears.
- Layering:
  - **Bottom:** dark `<div>` with positioned number/value labels (per zone).
  - **Top:** `<canvas>` with the **ticket PNG drawn onto it** as the scratch surface.
  - **Scratch:** `destination-out` erases the PNG pixels in the brush path to reveal the labels below.
- Keep: per-zone positions from `config/scratch-zones.json`, `lineTo` round-cap brush interpolation,
  getImageData percentage tracking.

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

## What needs fixing (priority order for the fresh session)

1. **Rebuild the scratch card to the CORRECT architecture above** (PNG-as-coating erased via
   destination-out; dark layer with real numbers/seals beneath; keep per-zone config + lineTo brush).
2. **Re-expose the real grid** on `/api/tickets/{id}/reveal` (recover from `a082e3a`) so the numbers
   beneath are the true engine values (consistent with win/loss; preserves "actual odds").
3. **Re-apply the hand-tuned zone coordinates** (recover from `a082e3a`) or re-tune in the map tool so
   the erased PNG regions line up with the crystals/seals.
4. Optional polish discussed but not built: scratch dust/particles, coin cursor, scratch/win sounds,
   reveal animation/confetti, holographic tilt.

## Known caveats
- Visual/pixel feel was never verifiable in-session (no browser/headless rendering available here);
  all scratch behavior was validated by code + API, not by looking at rendered pixels.
- Demo RTPs (88%/30%) are the configured tier counts, not the mechanics' calibrated rates.
