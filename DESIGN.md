# LuckLedger — Scratch Card Gambling Awareness Simulator

## Full Project Context

## Methodology

This is a portfolio and learning project. The developer is on an F-1 visa, which prohibits operating a business, having customers, or generating revenue. The simulator cannot be monetized in any form during the development period. This constraint makes the project legally bulletproof (see §3.1) and reframes priorities: the build focuses on the generation pipeline, the economy model, and the educational ledger — not on user acquisition, payments, or growth features.

Claude provides design discussion, architecture review, and tradeoff analysis. The developer makes design decisions and writes code.

---

## 1. Product Vision

A free-to-play scratch card web simulator that functions as a **gambling awareness tool**. Users experience the full dopamine loop of real scratch cards — scratching, reveals, wins, losses, near-misses, "hot streaks" — but with virtual currency that has no real-world value. A persistent ledger tracks every coin borrowed, spent, won, and lost, surfacing the cold math that the house always wins.

The simulator is not a casino, not a sweepstakes, and not a game. It is a **mirror**. Users play for fun, and over time the data tells them what gambling actually does to a bankroll.

### Why It Exists

Real gambling education is ineffective because it is abstract — "the odds are against you" means nothing until a user has felt 30 losing tickets in a row and checked their ledger. This simulator lets people feel the psychology of gambling in a zero-risk environment, then shows them the receipts.

The academic literature (Gainsbury et al., 2019; study of 736 EGM gamblers) suggests gambling simulators can actually *increase* real gambling rather than deter it. The ledger and awareness layer is the counterargument — this app does not just let users gamble, it forces them to confront what gambling does. Whether that is sufficient is an open question and part of the educational honesty.

### Who It Is For

- People curious about scratch cards who have never bought one
- People who already gamble and want to understand their own patterns
- Students, educators, anyone interested in probability and behavioral economics
- Portfolio piece demonstrating generation pipeline design, game theory, applied combinatorics, and full-stack architecture

### What This Is NOT

- Not a lottery or gambling platform (no real-money wagering, no purchase path, no cash-out)
- Not a social casino (no leaderboard-driven monetization, no diamond purchases)
- Not a ScratchAll clone (ScratchAll is entertainment-first with monetization; this is education-first with no monetization)
- Not a state-lottery affiliate or simulator of any specific jurisdiction's games

### Prior Art: ScratchAll.com

Studied as a reference implementation. Key findings from source analysis:

| Dimension | ScratchAll | This Project |
|-----------|-----------|--------------|
| Backend | Supabase (Postgres + auth) | Supabase or equivalent (TBD) |
| Frontend | Vanilla JS + Canvas 2D | Vanilla JS + Canvas 2D |
| Payments | Stripe (diamond purchases $0.99–$11.99) | None — prohibited by design |
| Monetization | Diamonds + Google AdSense + rewarded ads | None |
| Prize logic | Per-ticket resolution at load time | Pre-generated pools partitioned into books |
| Economy model | Flat — player ↔ tickets | Three-layer — player ↔ dealer ↔ books ↔ pool |
| Educational layer | None | Persistent ledger with loss tracking, dealer debunking, variance analysis |
| Location | Pittsburgh, Pennsylvania (solo dev or small team) | — |

ScratchAll's rendering pipeline (Canvas 2D with `destination-out` compositing, three quality tiers, line interpolation, rAF batching, retina scaling) is the reference for the scratch-feel implementation. Their economy model is the reference for what *not* to build — this project's differentiator is the dealer layer and the educational ledger, neither of which ScratchAll has.

### Direct Feedback from ScratchAll Developer

Nick (ScratchAll's solo developer) responded to a direct inquiry about his tech stack and asset pipeline. His feedback provides ground-truth corrections to assumptions made from source analysis alone:

**On the rendering architecture:** The ticket is **not** pure Canvas 2D. It is a **hybrid div + canvas layered setup** — HTML/CSS divs handle the ticket structure, layout, and static content, with 2D canvas elements layered on top specifically for the scratch-to-erase interaction. "The ticket setup is pure CSS with images where needed." This means the visual layout (backgrounds, prize text, borders, symbols) is standard web rendering, and Canvas is used only for the `destination-out` erase mechanic. This is a significant architectural distinction from "render everything in Canvas."

**On sparkle and particle effects:** Sparkle effects are **GIF overlays**, not procedurally generated canvas animations. Particle systems (dust on scratch, win celebrations) are "based on existing JS particle systems available online" and "take a good amount of fine-tuning." The implication: use established open-source particle libraries (e.g., tsParticles, particles.js) and invest time in parameter tuning, not in writing particle physics from scratch.

**On ticket art creation:** Nick creates ticket art **manually in Photoshop** (using a university license). His workflow for overcoming design blocks: "take an existing design from a local lottery website and recreate it in Photoshop." This is a learning/reference technique — study real lottery ticket designs, then rebuild them as original art. For elements beyond his illustration skill, he uses "Google image generation and clipart/free assets," but cautions these "require a great deal of tuning and I typically only use them for background elements." His explicit assessment: **"I think it would be difficult to get the final result from the current level of AI generators alone."** The design is "a labor of love."

**On the technology choice:** Nick noted that if starting fresh, "I would probably use a game engine like unity because of the complexity of the game play." This validates that the web-based approach has inherent friction for interactive scratch-card mechanics — the hybrid div+canvas architecture is a pragmatic workaround, not an ideal solution. This project proceeds with the web approach regardless (portfolio piece, no distribution through app stores, education-first), but the friction is acknowledged.

**On ticket config storage:** ScratchAll uses "a JSON array to store ticket configs and then plug that into my logic system." This confirms JSON as the natural format for ticket configuration — aligns with this project's approach of `PoolContract` as a JSON-serializable specification.

**Implications for this project's rendering pipeline (§6):**
- Adopt the hybrid div+canvas architecture: HTML/CSS for ticket layout, Canvas only for the scratch erase layer
- Use GIF or animated sprite overlays for sparkle effects, not procedural canvas sparkle generation
- Use an open-source particle library for scratch dust and win celebrations, budget time for parameter tuning
- For theme art: use Flux for raw asset generation (backgrounds, decorative elements), Photopea for compositing and polish. Study real lottery ticket designs for composition reference. Expect the Photopea stage to be the bulk of the design work — Flux provides materials, not finished designs.
- Accept that the ticket design pipeline is partially manual — the generation pipeline automates prize logic, not visual design

---

## 2. How It Works — End to End

1. **System generates a prize pool** → pool contract defines total tickets, payout ratio, prize tiers with exact counts. All outcomes are determined before any ticket exists.
2. **Pool is partitioned into books** → tickets are shuffled, split into ordered books. Each book has a calculable total value based on which prizes landed in it.
3. **Books are allocated to dealers** → NPC dealer stores receive books based on tier/rank. Higher-ranked dealers draw from the upper tail of the book-value distribution.
4. **Player borrows coins from the bank** → virtual currency, freely available, unlimited, no purchase path. The bank is the legal shield (see §3.1).
5. **Player picks a dealer and a book** → browses dealer stores, sees available books with partial metadata (tickets remaining, payouts dispensed so far).
6. **Player buys and scratches a ticket** → coins deducted, canvas scratch interaction, predetermined outcome revealed.
7. **Ledger updates in real time** → borrowed, spent, won, net position, per-dealer breakdown, rolling return rate.
8. **Educational insights surface over time** → loss rate warnings, loss-chasing detection, "lucky store" myth debunking, variance explanations.

---

## 3. Key Design Decisions

### 3.1 Legal Positioning: No Money In, No Money Out

The simulator eliminates both **consideration** (no purchase required — virtual currency is freely available from an in-game bank) and **prize** (no real-world value — coins cannot be withdrawn or converted) from the three-element lottery test (prize + chance + consideration). This places the project outside gambling law in all US jurisdictions.

**Key case law awareness:** *Kater v. Churchill Downs* (9th Circuit, 2018) ruled that purchasable virtual chips are a "thing of value" even without cash-out. The court's reasoning: "if a user runs out of virtual chips and wants to continue playing, she must buy more chips to have 'the privilege of playing the game.'" This project avoids that entirely — the bank gives currency for free with no purchase path. There are no diamonds, no IAP, no ads, no monetization of any kind.

An 18+ age gate is still recommended. Gambling-style mechanics trigger platform and regulatory expectations regardless of legal classification. Social casino platforms universally require 18+ even when no real money is involved.

### 3.2 Pre-Generated Prize Pools (Not Per-Ticket Randomization)

Tickets are NOT generated with random probability at scratch time. The entire ticket pool is built deterministically from the pool contract — exact counts of winners at each tier — then shuffled using Fisher-Yates and partitioned into books. This guarantees the math is exact: payout budget is never exceeded or underdelivered. The shuffle randomizes arrangement, not outcome.

This is the same principle as dealing from a pre-built deck, and it mirrors how physical lottery scratch cards are manufactured. The outcome is decided first; the visual presentation is reverse-engineered to produce it.

**Why not per-ticket resolution (the ScratchAll model):** Per-ticket resolution is simpler but makes the educational features impossible. Without a fixed pool, there is no book-level variance to analyze, no "lucky store" myth to debunk, no sequential depletion to observe. The pre-generated pool is more complex to build but enables the entire dealer economy and educational ledger.

### 3.3 Four-Layer Generation Pipeline

The generation pipeline has four stages, each with a clear input/output contract:

- **Layer 1 — Pool Contract.** Business config. Defines total tickets, ticket price, payout ratio, prize tiers with values and counts, minimum payout floor. Immutable specification. Output: `PoolContract`.
- **Layer 2 — Outcome Assignment.** Creates N `TicketOutcome` objects from the contract, each with a UUID and a prize amount (including $0 for losers). Shuffled. No visual representation yet. Output: list of `TicketOutcome`.
- **Layer 3 — Mechanic Mapping.** Takes each `TicketOutcome` and a `GameMechanic` type (match-3, number-match, bingo, etc.) and reverse-engineers a valid symbol layout that guarantees that outcome. A winner must have a valid winning pattern. A loser must have no accidental winning pattern. Output: `TicketLayout`.
- **Layer 4 — Theme Skinning.** Maps abstract symbols to themed visuals. "Symbol A in position [2,5,8]" becomes 🤠🤠🤠. Purely cosmetic. Output: `TicketCard`.

A mandatory **verification pass** runs after generation, validating the entire batch against the original pool contract (see §3.4).

### 3.4 Verification Pass Is Mandatory

After every batch generation, a verification suite validates:

- **Exact count validation** — each prize tier count matches the contract exactly.
- **Payout ratio check** — sum of all prizes / total revenue matches the target within floating-point tolerance.
- **No false-positive losers** — every $0 ticket is evaluated through the win-condition engine for its mechanic type. Zero accidental winning patterns.
- **No broken winners** — every winning ticket contains a valid winning pattern when evaluated.
- **Distribution sanity** — winners are spread across the pool, not clumped.
- **Near-miss analysis** (optional, educational) — count of loser tickets with 2-of-3 near-miss patterns, reported to the educational layer.

This is the QA step in the pipeline. Generation without verification is a bug.

### 3.5 Dealers Are NPCs, Not Players

The dealer/store layer was initially considered as a player role. This was rejected because the bank provides unlimited free currency, which eliminates scarcity, risk, and the possibility of a balanced dealer economy. Real dealer economics work because retailers put up their own capital to buy inventory and take a real loss if they cannot sell. With free money, that tension disappears.

Dealers are NPC storefronts — the system's distribution mechanism between the generation pipeline and the player. Each dealer has a name, a tier, a rank, and a catalog of books. The player picks a dealer, picks a book, buys tickets from it. Dealers never interact with each other.

### 3.6 Ranking Controls Book Allocation Quality, Not Volume

Every dealer receives the **same number of books per cycle** regardless of rank. This is the throughput cap that prevents the flywheel effect (see §4.4). Rank determines *which* books from the book-value distribution a dealer receives, not *how many*.

When books are generated and sorted by total value, higher-ranked dealers are allocated books from the upper tail of the distribution. Lower-ranked dealers draw from the lower tail. The payout ratio of the underlying game is untouched — the ranking nudge operates exclusively on the natural variance between books that arises from the partition (see §4.3).

Dealer rank is based on **books depleted** (fully sold out), not on profit or volume. Book allocation has diminishing returns: a high-tier dealer gets more *variety* (more concurrent book types) but not more *throughput* (same total books per cycle).

### 3.7 Payout Ratio Is Sacred

No dealer, no ranking tier, no book profile, no allocation strategy changes the payout ratio of any game. A 65% game is a 65% game at every dealer, in every book, at every tier. The educational integrity of the simulator depends on this invariant. What changes is the *shape* of the prize distribution (many small wins vs. one jackpot) and the *allocation of natural book-level variance* — never the expected value per coin spent.

### 3.8 Minimum Payout Floor Eats From the Prize Budget

A `PoolContract` with `min_payout > 0` guarantees every ticket pays at least that amount. The floor cost is deducted from the prize budget before prize tiers are allocated:

```
floor_cost = (total_tickets - sum(tier.count)) × min_payout
remaining_budget = prize_budget - floor_cost
CONSTRAINT: sum(tier.value × tier.count) == remaining_budget
```

A $1 floor on 33 loser tickets consumes $33 from the prize budget, compressing mid-tier prize counts. The floor makes the game *feel* better (nobody walks away empty) but mathematically guts the larger prizes. This mirrors real lottery "free play" prizes — "1 in 4 tickets wins!" but most wins are just free tickets worth $0.65 in expected value.

The floor also reduces book-level variance (§4.2) because every book has a guaranteed minimum value. This means ranking matters less for floor-heavy games.

### 3.9 Game Mechanics Are Swappable Via Strategy Pattern

Each game mechanic bundles a `GridPopulator` (how to build the grid from a predetermined outcome) and a `WinEvaluator` (how to determine the outcome from a grid). Switching between MATCH_3, NUMBER_MATCH, BINGO, or CROSSWORD changes one config field. The entire pipeline — pool generation, book partition, dealer allocation, scratch, ledger — is mechanic-agnostic.

Adding a new game type requires only implementing a new `GameMechanic` with its populator and evaluator. No other subsystem changes.

### 3.10 Mechanic Mapping Is Reverse-Engineering

Layer 3 of the generation pipeline (§3.3) does not randomly populate a grid and check if it matches the outcome. It **reverse-engineers** a valid symbol layout from the predetermined outcome:

- **For a winner:** place the winning pattern (e.g., three matching symbols in valid positions), assign the correct prize amount, fill remaining cells with non-matching symbols ensuring no accidental second win.
- **For a loser:** guarantee no winning pattern exists. For match-3, ensure no symbol appears 3+ times. For number-match, ensure zero overlap between the two number sets.

This is a per-mechanic constraint solver. Each `GridPopulator` implementation contains the constraint logic for its mechanic. Bad generation logic can create accidental wins (false positives) or impossible-to-read winners (false negatives). The verification pass (§3.4) catches both.

### 3.11 Near-Misses Are a Design Choice

Real lotteries deliberately engineer near-misses — loser tickets with two matching symbols in a row (but not three). This makes losers feel like they "almost won," which drives repeat play. The simulator offers two modes:

- **Realistic mode:** near-misses are engineered into loser tickets at a configurable rate (default: 30-40% of losers have a near-miss). Mirrors real lottery design.
- **Clean mode:** near-misses are avoided. Loser tickets have no almost-winning patterns.

Both modes are available. The educational layer reports the near-miss rate and explains its psychological purpose. "34% of your losing tickets showed 2-of-3 matching symbols. This is deliberate. Real lotteries use this to make you feel like you almost won."

### 3.12 Sequential Book Depletion

Tickets within a book are sold **sequentially** — ticket #1 first, then #2, then #3. This mirrors physical lottery scratch card rolls at retail stores. A player buying early from a book gets a random draw from the full book; a player buying late gets whatever is left.

This creates observable dynamics: if a book's jackpot is ticket #73, the first 72 buyers lose and the dealer profits. Then #73 hits. Players who track a dealer's "tickets remaining" and "payouts so far" can infer whether the book likely has big winners left — which is *exactly what real lottery players do* when they ask clerks for fresh rolls.

The book metadata visibility (how much the player can see about depletion state) is a configurable parameter per game, not a universal setting.

### 3.13 The Ledger Is Everything

The persistent ledger is the educational core of the product. It surfaces:

- **Personal loss rate** — "You've spent 4,800 coins and won back 3,200. That's a 33% loss rate."
- **Session psychology** — flags when users borrow more after a loss streak (loss-chasing behavior).
- **Dealer comparison** — "Your return rate at Joe's was 72%, at Downtown it was 58%. Both books had 65% payout ratios. The difference was variance and timing, not skill."
- **Flywheel debunking** — "Downtown had more jackpots because they sold 20× more books. Their per-book hit rate is statistically identical to every other dealer."
- **The inevitability curve** — a chart showing cumulative borrowed vs. cumulative winnings over time, with the gap widening.
- **Near-miss reporting** — "34% of your losing tickets had 2-of-3 near-misses. This is an engineered psychological manipulation."

Without the ledger, this is just another scratch card game. With the ledger, every scratch is a data point in the user's growing understanding of how gambling works.

---

## 4. Variance Model

The system has three distinct levels of variance. Understanding their sources, dependencies, and controllability is essential to the economy design.

### 4.1 Level 1 — Total Pool Variance

**Source:** Prize tier design — the values, counts, and weights chosen by the game creator.
**Controller:** Fully controllable. Set once per game.
**Impact:** Determines the *potential* for book-level variance (Level 2).

Three configurations with identical 65% payout ratio on a 10,000-ticket pool (10,000 coins revenue, 6,500 coins prize budget):

```
Config A "Flat":
  6,500 × $1 prizes, 3,500 losers
  → nearly every ticket wins, wins are tiny

Config B "Moderate":
  1 × $2,000  +  10 × $100  +  200 × $10  +  500 × $3
  → some real wins, mostly losers

Config C "Top Heavy":
  1 × $5,000  +  10 × $100  +  100 × $5
  → one life-changing win, almost everyone else gets nothing
```

All three produce 6,500 coins in total payout. The player experience is wildly different.

### 4.2 Level 2 — Inter-Book Variance

**Source:** The remainder layer — prizes that cannot divide evenly across books.
**Controller:** Sampling strategy (STRATIFIED / RANDOM / CLUSTERED). Target: low but nonzero.
**Impact:** Determines which books are "fat" (above-average value) and which are "thin" (below-average). This is where the ranking nudge operates.

Every pool decomposes into two layers when partitioned into books:

- **Stratifiable layer:** Prizes where `count ≥ number_of_books`. These distribute evenly — zero variance. Example: 200 prizes of $2 across 100 books = exactly 2 per book.
- **Remainder layer:** Prizes where `count < number_of_books`. These cannot distribute evenly — unavoidable variance. Example: 10 prizes of $50 across 100 books = 10 books get one, 90 books get zero.

The remainder layer is **always the big prizes** (by definition, there are few of them). This means all meaningful book-level variance comes from the prizes players care about most.

The relationship between Level 1 and Level 2: a top-heavy prize table (Config C) creates more indivisible remainders and therefore more inter-book variance. A flat prize table (Config A) creates fewer remainders and therefore less inter-book variance. The game creator's prize tier design implicitly controls how much book-level variance exists.

**Sampling strategy decision:** RANDOM (Fisher-Yates shuffle, deal sequentially into books). This creates natural variance from the remainder layer without artificial manipulation. The variance is defensibly "fair" — nobody manipulated anything — but it is nonzero by mathematical necessity. This is where the ranking nudge lives (§3.6).

### 4.3 Level 3 — Intra-Book Variance

**Source:** Which prizes from the remainder layer landed in a specific book.
**Controller:** Not controllable. Emergent.
**Impact:** Determines the player's *felt experience* within a single book.

A book that received a $50 jackpot has high internal variance — one ticket is worth 86% of the entire book, 99 tickets share the remaining 14%. A book with only $2 prizes has low internal variance — value is spread evenly across several tickets.

The dependency chain:

```
Level 1 (pool design)
  → determines how many prizes are indivisible
    → Level 2 (inter-book allocation)
      → determines which books get the big prizes
        → Level 3 (intra-book experience)
          → books WITH big prizes: high internal variance
             (drought punctuated by euphoria)
          → books WITHOUT big prizes: low internal variance
             (gentle, predictable, no big highs or lows)
```

Intra-book variance is the one nobody controls. It emerges from the collision of Level 1 and Level 2. And it is the one the player actually *feels*. This makes it the most important thing the educational ledger reveals — the experience of gambling is shaped by a variance level that nobody designed and nobody can predict per-book.

### 4.4 The Throughput Flywheel

**Problem:** Without caps, high-volume dealers cycle through more books → encounter more jackpots by statistical inevitability → build a reputation as "lucky" → attract more players → sell more books → encounter more jackpots. The rich get richer not because of rigging but because volume creates statistical certainty.

This is a real phenomenon. "Lucky stores" that lottery commissions advertise are not lucky — they sell more tickets. The perception of luck drives traffic, which drives volume, which creates more visible winners.

**Solution:** Equal books per cycle for all dealers regardless of rank. Rank controls allocation quality (which slice of the book-value distribution), not volume (how many books). The flywheel cannot spin because more sales do not compound into more books.

### 4.5 Inter-Book Variance as a Design Lever

High inter-book variance concentrates "stories" at specific dealer locations. One store has a jackpot winner and customers are celebrating; nine stores have nothing. The euphoric store creates social proof that drives traffic — and the net engagement across the system may increase even though only one store is "hot," because excitement is nonlinear.

This is how real casinos engineer slot placement: loose machines near entrances and aisles maximize witness count per jackpot.

A potential experimental mode ("Lottery Commissioner") would give the user a slider for inter-book variance and let them observe how distribution strategy affects perception while the underlying payout ratio remains constant. This is deferred — documented for future implementation.

### Variance Summary

| Level | Source | Controller | Desired State |
|-------|--------|------------|---------------|
| Total pool | Prize tier design | Game creator | Design choice |
| Inter-book | Remainder layer | Partition algorithm | Low, nonzero |
| Intra-book | Big prize presence | Emergent | Not controllable |

---

## 5. Architecture

### 5.1 System Layers

Four layers, strict one-directional dependencies:

```
Pool Layer      → defines the math (depends on nothing)
Generation Layer → creates tickets from pools (depends on Pool)
Distribution Layer → partitions tickets into books, allocates to dealers (depends on Generation)
Player Layer    → scratch interaction, ledger, educational insights (depends on Distribution)
```

### 5.2 Object Model

```
Pool Layer:
  PoolContract
    ├── total_tickets: int
    ├── ticket_price: float
    ├── payout_ratio: float
    ├── prize_tiers: List<PrizeTier>
    │     └── PrizeTier { value: float, count: int, label: String }
    ├── min_payout: float  (0 = pure losers exist, >0 = floor)
    └── validate(): boolean  (constraint: tier_cost + floor_cost == prize_budget)

  GameMechanic (interface)
    ├── getPopulator(): GridPopulator
    ├── getEvaluator(): WinEvaluator
    └── implementations: Match3Mechanic, NumberMatchMechanic,
                         BingoMechanic, CrosswordMechanic, TicTacToeMechanic

  Theme
    ├── name: String
    ├── symbol_map: Map<AbstractSymbol, ThemedVisual>
    ├── background_art: ImageRef
    ├── color_palette: ColorPalette
    └── scratch_coating_texture: TextureRef

Generation Layer:
  TicketOutcome
    ├── outcome_id: UUID
    └── prize_amount: float  (0 for losers, min_payout for floor tickets)

  TicketLayout
    ├── outcome_id: UUID
    ├── mechanic_type: GameMechanic
    ├── grid: AbstractCell[][]
    └── winning_positions: List<Position>  (empty for losers)

  TicketCard
    ├── ticket_id: UUID
    ├── layout: TicketLayout
    ├── theme: Theme
    └── skinned_grid: ThemedCell[][]

  GridPopulator (interface)
    └── populate(GridSize, PrizeTier, mechanic constraints): TicketLayout

  WinEvaluator (interface)
    └── evaluate(TicketLayout): float  (prize amount, 0 if loser)

  VerificationSuite
    ├── validateTierCounts(List<TicketOutcome>, PoolContract): boolean
    ├── validatePayoutRatio(List<TicketOutcome>, PoolContract): boolean
    ├── validateNoFalsePositives(List<TicketLayout>, WinEvaluator): boolean
    ├── validateNoBrokenWinners(List<TicketLayout>, WinEvaluator): boolean
    └── reportNearMisses(List<TicketLayout>): NearMissReport

Distribution Layer:
  TicketBook
    ├── book_id: UUID
    ├── pool_contract_id: UUID
    ├── tickets: List<TicketCard>  (ordered, sold sequentially)
    ├── total_tickets: int
    ├── tickets_remaining: int
    ├── total_prize_pool: float  (sum of all prizes in this book)
    ├── prizes_dispensed: float
    ├── book_value: float  (= total_prize_pool, used for sorting)
    └── metadata_visibility: MetadataVisibility  (NONE / PARTIAL / FULL)

  Dealer (NPC)
    ├── dealer_id: UUID
    ├── name: String
    ├── tier: DealerTier  (TIER_1 / TIER_2 / TIER_3)
    ├── rank_score: int
    ├── active_books: List<TicketBook>
    ├── books_per_cycle: int  (SAME for all dealers — throughput cap)
    ├── allocation_priority: AllocationPriority  (rank-based quartile)
    └── books_depleted_total: int  (drives ranking)

  BookPartitioner
    ├── partition(List<TicketCard>, int booksCount): List<TicketBook>
    └── internal: shuffle → deal sequentially → calculate book_value per book

  DealerAllocator
    ├── allocate(List<TicketBook>, List<Dealer>): Map<Dealer, List<TicketBook>>
    └── internal: sort books by book_value → assign by dealer rank quartile

Player Layer:
  Player
    ├── player_id: UUID
    ├── coin_balance: float
    ├── bank_loans_total: float
    ├── tickets_purchased: int
    ├── total_spent: float
    ├── total_won: float
    ├── net_position: float  (= total_won - total_spent)
    └── favorite_dealer: Dealer (nullable)

  Ledger
    ├── transaction_history: List<Transaction>
    ├── per_dealer_breakdown: Map<Dealer, DealerStats>
    ├── per_book_breakdown: Map<TicketBook, BookStats>
    ├── rolling_return_rate: float
    └── educational_insights: List<Insight>

  Transaction
    ├── timestamp: Instant
    ├── type: TransactionType  (BORROW / SPEND / WIN)
    ├── amount: float
    ├── dealer_id: UUID
    ├── book_id: UUID
    └── ticket_id: UUID

  Insight (interface)
    └── implementations: LossRateWarning, LossChasingDetection,
                         LuckyStoreDebunk, VarianceExplanation,
                         NearMissReport, InevitabilityCurve
```

### 5.3 Game Mechanic Types

The generation pipeline supports multiple mechanic types. Each requires its own `GridPopulator` (reverse-engineering symbol layouts from outcomes) and `WinEvaluator` (determining outcomes from symbol layouts). Roughly 30 distinct mechanics exist in real-world lotteries; the following are prioritized for implementation:

| Mechanic | Win Condition | Grid Structure | Constraint Complexity |
|----------|--------------|----------------|----------------------|
| **Match-3** | 3 identical symbols anywhere (or in a row) | N×N grid | Medium — must prevent accidental 3+ matches on losers |
| **Number Match** | YOUR NUMBERS overlap with WINNING NUMBERS | Two zones: winning numbers + your numbers with prizes | Medium — must prevent accidental overlap on losers |
| **Key Symbol** | One "key" revealed; any cell matching the key wins its prize | Key zone + play zone | Low — key is separate from play area |
| **Bingo** | Complete a line on a grid from called numbers | Caller card + 5×5 grid | High — must prevent accidental line completion on losers |
| **Crossword** | Revealed letters complete words on a crossword grid | Letter zone + crossword grid | High — word completion is combinatorially complex |
| **Tic-Tac-Toe** | Three matching symbols in a row/column/diagonal on 3×3 | 3×3 grid | Low — small grid, few configurations |

**Adding a new mechanic** requires implementing one `GridPopulator` and one `WinEvaluator`. No other subsystem changes. The pool contract, book partition, dealer allocation, ledger, and educational layer are all mechanic-agnostic.

### 5.4 Book Profiles

Different book profiles maintain the same payout ratio but with different prize *distribution shapes*:

```
BookProfile:
  CONSERVATIVE — many small prizes, high win frequency, low max prize
  BALANCED     — moderate spread
  JACKPOT      — one huge prize, mostly losers
```

All profiles are achieved by varying the `PoolContract` prize tiers, not by any post-generation manipulation. A CONSERVATIVE pool has many low-value tiers with high counts. A JACKPOT pool has one high-value tier with count 1 and most tickets as losers. The payout ratio is identical across profiles.

Dealer tier determines which profiles are available:

| Dealer Tier | Available Profiles |
|-------------|-------------------|
| Tier 1 | CONSERVATIVE, some BALANCED |
| Tier 2 | BALANCED, some JACKPOT |
| Tier 3 | All profiles, higher JACKPOT allocation |

The educational trap: the expected return per coin is identical at every dealer. The Tier 3 dealer is not "better." Players are trading many small wins for a tiny chance at a huge one. Most players lose *more consistently* at the JACKPOT dealer because 94 of 100 tickets pay nothing, versus the CONSERVATIVE dealer where something wins half the time. But the JACKPOT dealer *feels* better because someone hit 600 coins there last week. This is literally how real lottery pricing works — $30 scratchers with million-dollar prizes have worse per-ticket win rates than $1 scratchers with $5 prizes.

---

## 6. Rendering Pipeline

The scratch-feel quality is a separate concern from the generation pipeline. Based on analysis of ScratchAll's implementation, direct feedback from ScratchAll's developer (§1), and standard Canvas 2D best practices, the rendering uses a **hybrid HTML/CSS + Canvas architecture**:

### 6.0 Hybrid Architecture (from ScratchAll Feedback)

The ticket is NOT rendered entirely in Canvas. The architecture is a **layered div + canvas stack**:

- **Bottom layer (HTML/CSS):** Ticket background image, prize text, grid cells with symbols, header/footer text, borders, layout. Standard web rendering — CSS Grid/Flexbox for positioning, `<img>` tags for theme art, styled `<div>` elements for cells. This layer handles everything the player sees *after* scratching.
- **Top layer (Canvas 2D):** The scratch coating only. A `<canvas>` element is positioned absolutely over the HTML content. The coating texture (metallic gradient, hatch pattern, sparkle dots) is drawn to this canvas at init time. `destination-out` compositing erases the canvas as the player scratches, revealing the HTML content beneath.

This hybrid approach has three advantages over pure Canvas rendering:
1. **HTML/CSS for layout is faster to build and iterate on.** Ticket designs change frequently; CSS changes are trivial while Canvas layout code is verbose.
2. **Text rendering is native.** Browser text rendering (kerning, ligatures, accessibility, selection) is free with HTML. Canvas text rendering requires manual measurement and positioning.
3. **Separation of concerns.** The scratch mechanic (Canvas) and the ticket content (HTML) are independent layers. Changing the ticket layout does not touch the scratch code, and vice versa.

### 6.1 Scratch Interaction (Canvas Layer)

The Canvas layer handles only the scratch-to-reveal interaction:

- **Line interpolation between points.** Every `pointermove` event draws a line from the previous point to the current point using `lineCap: "round"`, not isolated circles. This eliminates gaps at high finger speeds.
- **requestAnimationFrame batching.** Scratch coordinates are queued per frame and batch-rendered at 60fps, preventing overdraw from 100+ events per second on high-refresh displays.
- **High-DPI canvas scaling.** Canvas dimensions multiplied by `devicePixelRatio`, CSS dimensions set to display size. Prevents blurriness on retina screens.
- **Integer coordinate snapping.** All scratch coordinates rounded via `Math.floor()` to avoid unnecessary sub-pixel anti-aliasing calculations.
- **Pointer Events (unified input).** Single event system for mouse, touch, and pen. `touch-action: none` on the canvas prevents page scroll during scratch.
- **Offscreen canvas compositing.** Scratch mask rendered to an offscreen canvas, composited onto the main canvas via `drawImage`. Separates expensive pixel manipulation from the display pipeline.
- **Three quality tiers.** FASTEST (no texture, no particles), FAST (basic texture), FANCY (full metallic gradient, sparkle dots, scratch effects). Configurable by the user.
- **`destination-out` compositing.** Standard technique — drawing on the scratch layer erases pixels, revealing the HTML content beneath.

### 6.2 Effects Pipeline (from ScratchAll Feedback)

**Sparkle effects:** Use **GIF or animated sprite overlays**, not procedural canvas sparkle generation. An animated sparkle GIF is layered via CSS (`position: absolute`, `pointer-events: none`) over the scratch area. This is ScratchAll's approach and avoids complex canvas animation code for a purely cosmetic effect. Reference: CSS-based sparkle/glitter GIF libraries (search "CSS sparkle overlay GIF").

**Particle systems (scratch dust, win celebrations):** Use an **open-source JS particle library** (e.g., tsParticles, particles.js, or similar lightweight options). ScratchAll's developer confirms particle systems "take a good amount of fine-tuning" but are "based on existing JS particle systems available online." Budget iteration time for parameter tuning — particle count, velocity, fade rate, gravity — not for writing the physics engine. The particle canvas is a separate layer above the scratch canvas, drawn on its own rAF loop.

**Win reveal animations:** When the scratch threshold is met (e.g., 70% of coating erased), trigger a win/loss reveal sequence:
- Losers: fade remaining coating, brief subtle animation (or none).
- Small winners: confetti particle burst, prize amount highlight animation.
- Big winners: full celebration — screen flash, extended particle shower, prize amount scaling animation with haptic feedback (Vibration API on mobile).

### 6.3 Theme Creation Workflow

A theme is a complete visual package applied to a batch of tickets. It includes background art, a symbol set, a scratch coating texture, a color palette, and layout parameters. Creating a new theme follows a five-step pipeline — each step produces assets consumed by the next.

**Step 1 — Art Direction.** Define the theme concept, target color palette, and mood. Examples: "Texas Cowboy" (warm earth tones, sunset oranges, leather browns), "Neon City" (dark background, electric purples, hot pinks), "Desert Gold" (turquoise and terracotta, southwestern patterns). This step is creative, not technical — output is a brief (theme name, 3-5 reference colors, mood keywords, 2-3 reference images for style).

**Step 2 — Background Art & Asset Generation.** The art pipeline is a two-stage process: **Flux (web-based AI image generator) for raw asset generation**, then **Photopea (free browser-based editor) for compositing and heavy editing**.

**Stage A — Flux generation.** Generate individual visual artifacts in Flux: the ticket background illustration, decorative elements (borders, corner ornaments, pattern tiles), and any standalone graphic pieces (e.g., a desert landscape, a neon cityscape, a themed banner). Each element is generated separately so it can be positioned, scaled, and layered independently in Photopea.

Prompt structure for Flux: `[subject] [setting] [style] [color constraints] [composition constraints]`
Example: `"saguaro cactus desert landscape at golden hour, vintage national park poster style, warm terracotta and turquoise palette, horizontal composition with open sky in upper third for header text, no text in image"`

Generate 3-5 variants per element, select the strongest candidates for compositing.

**Stage B — Photopea compositing.** Bring all Flux-generated assets into Photopea for heavy editing: compositing the background with decorative elements, adjusting saturation/contrast to work behind semi-transparent grid overlays, adding layer effects (gradients, textures, blending modes), masking and cropping to ticket aspect ratio, and final polish. This is where the ticket art comes together as a cohesive design — Flux provides the raw materials, Photopea is where the actual design work happens. Export as PNG at 2x resolution for retina.

ScratchAll's developer uses a similar two-stage pipeline (Photoshop instead of Photopea, Google image generation instead of Flux) and confirms this approach is necessary: "I think it would be difficult to get the final result from the current level of AI generators alone." His workflow for overcoming design blocks: "take an existing design from a local lottery website and recreate it" — study the composition, color balance, and visual hierarchy of published state lottery tickets for reference, then create original art.

Key constraints for ticket backgrounds:
- Horizontal or 3:4 aspect ratio to match ticket dimensions
- Upper 15% should be visually quiet (header zone overlays here)
- Lower 15% should be visually quiet (footer zone overlays here)
- Center 70% is the grid area — must work as a background behind semi-transparent cell overlays
- No text in the generated image (text is rendered programmatically via HTML)

**Step 3 — Symbol Set Design.** Each game mechanic needs a set of 6-12 distinct symbols. These are the icons that appear in grid cells. Three approaches, in order of effort:

- **Emoji set (zero effort):** Use Unicode emoji. Works immediately, cross-platform, no asset creation. Symbols like 🤠🐂⛽🌵⭐🥾 for Texas. Sufficient for MVP.
- **AI-generated icons (low effort):** Generate themed icons in Flux with a consistent style prompt. Example: `"flat vector icon of a cowboy boot, minimal detail, warm brown on transparent background, consistent line weight, 128x128px"`. Generate one per symbol, clean up in Photopea (remove artifacts, normalize sizing, ensure transparent backgrounds), export as PNG. Ensures visual cohesion across the set. **Per ScratchAll feedback:** AI-generated icons "require a great deal of tuning" and work best for background elements. For foreground symbols that need to be instantly recognizable at 48-80px, emoji or manually curated clipart/free assets may produce more consistent results.
- **Hand-drawn / designer icons (high effort):** Custom illustration. Maximum quality, maximum time. Not in scope for portfolio build.

Symbol sets must satisfy mechanic constraints: for Match-3, all symbols must be visually distinct at cell size (48-80px). For Number Match, symbols are numbers (1-20) rendered in the theme's font and color.

**Step 4 — Scratch Coating Texture.** The metallic overlay that the player scratches away. Generated procedurally at canvas init time, not as a static asset. The theme defines parameters consumed by the canvas renderer:

- `coatingBaseColor` — the primary metallic color (silver, gold, copper)
- `coatingGradientStops` — gradient colors for the metallic sheen effect
- `coatingNoiseIntensity` — density of random sparkle dots (0 = flat, 1 = dense glitter)
- `coatingHatchAngle` — angle of the diagonal cross-hatch lines that add texture
- `coatingHatchSpacing` — spacing between hatch lines in pixels

The renderer uses these parameters at init to draw the coating on the overlay canvas: fill with gradient, overlay hatch pattern, scatter sparkle dots. This canvas is composited over the HTML ticket content. `destination-out` compositing erases it as the player scratches. An animated sparkle GIF may be layered on top of the coating canvas for additional shimmer (see §6.2).

**Step 5 — Theme Assembly.** Combine all assets into a `ThemeRef` value object:

```
ThemeRef:
  themeId: "texas-cowboy"
  name: "Texas Gold Rush"
  symbolMap:
    "SYM_A" → { emoji: "🤠", imageUrl: null, label: "Cowboy Hat" }
    "SYM_B" → { emoji: "🐂", imageUrl: null, label: "Longhorn" }
    "SYM_C" → { emoji: "⛽", imageUrl: null, label: "Oil Barrel" }
    ...
  palette:
    primary: "#8B6914"
    secondary: "#C4A535"
    accent: "#FFD700"
    background: "#1A0F00"
    text: "#E8D5B0"
  backgroundArt: "/assets/themes/texas/background.png"
  coatingConfig:
    baseColor: "#C4A535"
    gradientStops: ["#8B6914", "#C4A535", "#DAB94A", "#C4A535", "#8B6914"]
    noiseIntensity: 0.6
    hatchAngle: 45
    hatchSpacing: 5
  sparkleGif: "/assets/effects/gold-sparkle.gif"  # optional overlay
```

Themes are data, not code (§9, Subsystem 6 design note). Adding a new theme is creating a new JSON entry and dropping the background PNG into the assets directory. No code changes, no deployments.

**Ticket rendering at play time** layers these assets in a div stack:
1. Container `<div>` with ticket dimensions, `position: relative`, `overflow: hidden`
2. Background `<img>` — theme background art, full ticket area, `position: absolute`
3. Grid container `<div>` — CSS Grid layout over center 70%, semi-transparent cell backgrounds with themed borders
4. Symbol elements — emoji `<span>` or `<img>` tags inside grid cells (hidden initially via CSS `visibility: hidden` or covered by coating)
5. Header `<div>` — theme name, dealer name, book info, positioned top 15%
6. Footer `<div>` — ticket ID, "scratch to reveal" prompt, positioned bottom 15%
7. Scratch `<canvas>` — `position: absolute`, full ticket dimensions, `z-index` above all content layers. Coating drawn at init time. `destination-out` compositing on scratch interaction erases the canvas, revealing HTML layers 2-6 beneath.
8. Effects layer — sparkle GIF overlay and particle canvas, `pointer-events: none`, highest z-index

No licensed IP. Original themes only. ScratchAll uses Studio Ghibli, Hello Kitty, Betty Boop, and The White Lotus without apparent licensing — a legal risk this project avoids.

---

## 7. Target Tech Stack

| Layer | Technology | Notes |
|-------|-----------|-------|
| Backend | Supabase (Postgres + auth + edge functions) | Free tier covers project needs. Prize pool generation as server-side logic. Book partition and dealer allocation as database operations. |
| Frontend | Vanilla JS + HTML/CSS + Canvas 2D | No framework. Hybrid architecture: HTML/CSS for ticket layout, Canvas for scratch interaction only (per §6.0). |
| Effects | Open-source particle library + GIF overlays | tsParticles or equivalent for scratch dust and win celebrations. Animated GIFs for sparkle coating effects. Budget time for parameter tuning. |
| Art pipeline | Flux (web AI generator) → Photopea (browser editor) | Flux generates raw assets (backgrounds, decorative elements, icons). Photopea composites, edits, and polishes into final ticket art. Two-stage: AI for materials, manual for design. |
| Ticket config | JSON | Ticket configurations stored as JSON arrays (confirmed by ScratchAll's approach). Pool contracts, theme refs, and mechanic configs all JSON-serializable. |
| Hosting | Cloudflare Pages or Vercel (free tier) | Static frontend + Supabase backend. |
| Monetization | None | F1 visa constraint. By design. |

---

## 8. Explicit Non-Goals & Rejected Ideas

These were considered and intentionally excluded:

- **Player-as-dealer role.** Unlimited free currency eliminates the scarcity and risk that make dealer economics self-balancing. Dealers are NPCs (§3.5).
- **Per-ticket resolution (ScratchAll model).** Simpler but makes the educational features impossible — no book-level variance, no dealer economy, no "lucky store" debunking (§3.2).
- **IAP or any monetization.** F1 visa prohibits it. Also a design choice: zero monetization makes the app legally bulletproof and the educational message uncompromised (§3.1).
- **Leaderboard-driven engagement.** ScratchAll uses leaderboards to drive diamond purchases. This project has no purchases, so leaderboards would serve no purpose. The ledger replaces the leaderboard as the primary engagement metric — but it shows you your losses, not your rank.
- **Stratified book sampling.** Forces proportional representation per book, minimizing inter-book variance to near-zero. Makes ranking meaningless because there is nothing to differentiate between books. RANDOM sampling preserved instead (§4.2).
- **Clustered book sampling.** Deliberately groups winners into fewer books, maximizing variance. Creates too-large ranking advantages that dominate the player experience. RANDOM sampling struck the right balance.
- **State-lottery affiliation.** The original concept was a US map where users click a state and get state-themed cards. Decoupled from state lottery legality — themes are cosmetic, not regulatory. The dealer mechanism replaced state affiliation as the primary organizational structure. State themes can still exist as a skinning layer if desired.
- **Real-time multiplayer or social features.** Would dilute focus on the core educational loop. The simulator is single-player with NPC dealers.
- **The "Lottery Commissioner" experimental mode.** A slider for inter-book variance that lets users observe distribution effects. Compelling but deferred — requires the core pipeline to be complete first. Documented in §4.5 for future implementation.
- **Pure Canvas rendering.** Rejected in favor of hybrid HTML/CSS + Canvas after ScratchAll developer feedback (§6.0). Full Canvas rendering is more work for layout, harder to iterate on, and provides no benefit for static ticket content. Canvas is used only for the scratch erase mechanic.
- **Custom particle physics.** Rejected in favor of open-source particle libraries. ScratchAll's developer confirms this approach — invest time in tuning parameters, not writing physics code.
- **Game engine (Unity/Godot).** ScratchAll's developer said he'd use Unity if starting fresh, but a game engine conflicts with this project's goals: web-native, no app store distribution, portfolio demonstration of web engineering, and education-first simplicity. The web approach has friction for interactive mechanics, but the hybrid architecture (§6.0) is the pragmatic mitigation.

---

## 9. Subsystem Details

### Design Principles

The subsystem architecture follows five structural rules:

1. **Single Responsibility at every level.** The pool does not know about books. Books do not know about dealers. The ledger does not make game decisions. The generation pipeline is four separate stages (§3.3), not one monolith. Each class does one thing; each subsystem owns one concern.
2. **Open/Closed via interfaces.** New game mechanics, new insight types, new allocation strategies all plug in by implementing an interface. No existing code changes. The pipeline is mechanic-agnostic — a new mechanic is a new `GridPopulator` + `WinEvaluator`, nothing else.
3. **Interface Segregation.** Populating a grid and evaluating a grid are separate interfaces (`GridPopulator`, `WinEvaluator`), not one combined `GameEngine`. Partitioning books and allocating them to dealers are separate (`BookPartitioner`, `DealerAllocator`). Validating a pool and creating outcomes from it are separate (`PoolValidator`, `OutcomeGenerator`).
4. **Dependency Inversion.** The `GenerationPipeline` depends on `GridPopulator` (interface), not `Match3Populator` (implementation). The `LedgerService` depends on `InsightGenerator` (interface), not `LossRateAnalyzer` (implementation). Concrete types are injected, never constructed internally by orchestrators.
5. **DRY through composition.** Prize budget calculation is a method on `PoolContract`, called by the validator, the generator, and the verification suite — not reimplemented in each. Win evaluation uses the same `WinEvaluator` interface for both generation-time verification and play-time reveal — same logic, two contexts. Grid utility functions (random symbol selection, non-colliding fill) live in `GridUtils`, shared across all `GridPopulator` implementations via composition, not inheritance.

---

### Subsystem 1: Pool Design
*Pure domain logic — zero external dependencies*

The pool contract is the mathematical foundation. It defines the rules for a batch of tickets before any ticket exists. All other subsystems derive their behavior from a validated `PoolContract`.

**Enums:**
- `BookProfile` — CONSERVATIVE, BALANCED, JACKPOT

**Value Objects:**
- `PrizeTier` — value (double), count (int), label (String). Immutable. `getTierCost()` returns `value × count`.
- `LosingTier` — extends `PrizeTier` with `value = 0` (or `min_payout` if floor is set), `consolationMessage` (String). Count is derived: `totalTickets - sum(winningTier.count)`.

**Core Class:**
- `PoolContract` — Immutable after construction. Built via `PoolContract.Builder`.
  - `totalTickets` (int)
  - `ticketPrice` (double)
  - `payoutRatio` (double)
  - `prizeTiers` (List\<PrizeTier>) — winning tiers only, sorted by value descending
  - `minPayout` (double) — 0 = pure losers exist, >0 = floor
  - `bookProfile` (BookProfile)
  - `getTotalRevenue()`: double — `totalTickets × ticketPrice`
  - `getPrizeBudget()`: double — `getTotalRevenue() × payoutRatio`
  - `getLoserCount()`: int — `totalTickets - sum(tier.count)`
  - `getFloorCost()`: double — `getLoserCount() × minPayout`
  - `getTierCost()`: double — `sum(tier.getTierCost())`
  - `getWinnerCount()`: int — `sum(tier.count)`
  - `getWinFrequency()`: double — `getWinnerCount() / totalTickets`

**Validation — `PoolValidator`:**

Separated from `PoolContract` because validation rules may evolve independently of the data structure (SRP), and because validation may need context the value object should not hold (e.g., system-wide limits).

- `validate(PoolContract)`: ValidationResult — runs all checks, returns pass/fail with error list
  - Check: `tierCost + floorCost == prizeBudget` (within floating-point tolerance)
  - Check: every `tier.count > 0`
  - Check: every `tier.value > minPayout` (winning tiers must exceed the floor)
  - Check: `totalTickets > 0`
  - Check: `payoutRatio` in range (0, 1) exclusive
  - Check: `loserCount >= 0` (tier counts don't exceed total tickets)
  - Check: no duplicate `tier.value` entries (ambiguous prize amounts)

**Factory — `PoolFactory`:**

Separated from `PoolValidator` because creating a valid pool (which involves computing the losing tier) is a different responsibility from checking if a pool is valid (ISP). The factory uses the validator internally.

- `create(PoolContract.Builder)`: PoolContract — validates, computes `LosingTier`, returns immutable contract. Throws `InvalidPoolException` on constraint violation.
- `createWithAutoBalance(totalTickets, ticketPrice, payoutRatio, List<PrizeTier>)`: PoolContract — adjusts the lowest-value tier's count to make the math fit exactly. Useful when the creator wants to set high tiers manually and let the system fill the rest.

---

### Subsystem 2: Outcome Generation
*Pure domain logic — depends on Subsystem 1 (Pool Design)*

Takes a validated `PoolContract` and produces a flat list of predetermined outcomes. No grids, no symbols, no visuals. Just (id, prize_amount) pairs.

**Value Objects:**
- `TicketOutcome` — outcomeId (UUID), prizeAmount (double). Immutable. `isWinner()` returns `prizeAmount > minPayout`. `isLoser()` returns `prizeAmount == 0` or `prizeAmount == minPayout`.

**Service — `OutcomeGenerator`:**
- `generate(PoolContract)`: List\<TicketOutcome> — creates exactly `tier.count` outcomes per tier, plus `loserCount` outcomes at $0 (or `minPayout`). Total list size == `totalTickets`. Each outcome gets a fresh UUID. List is **unshuffled** at this point — winners are grouped by tier.

**Service — `ShuffleService`:**
- `shuffle(List<TicketOutcome>)`: List\<TicketOutcome> — Fisher-Yates in-place shuffle. Returns the same list, reordered. Shuffle randomizes arrangement, not outcomes. Deterministic if seeded (for reproducible test runs).
- `shuffle(List<TicketOutcome>, long seed)`: List\<TicketOutcome> — seeded variant for testing.

**Design note:** `OutcomeGenerator` and `ShuffleService` are separate because generation is deterministic (same input → same output) while shuffling is stochastic. Testing generation without shuffle is trivial; testing shuffle without generation is trivial. Combining them would make both harder to test (SRP).

---

### Subsystem 3: Mechanic Engine
*Pure domain logic — depends on nothing. Used by Subsystem 4 (Generation Pipeline) and Subsystem 8 (Scratch & Reveal).*

The mechanic engine is used in two contexts: at generation time (reverse-engineering layouts from outcomes) and at play time (evaluating revealed grids). The same interfaces serve both — DRY across the two contexts.

**Enums:**
- `MechanicType` — MATCH_3, NUMBER_MATCH, KEY_SYMBOL, BINGO, CROSSWORD, TIC_TAC_TOE
- `GridSize` — THREE(3), FOUR(4), FIVE(5)

**Value Objects:**
- `Cell` — position (row, col), symbol (String), prizeValue (double). Immutable.
- `Grid` — size (GridSize), cells (Cell[][]). Immutable after population. `getCell(row, col)`: Cell. `getAllCells()`: List\<Cell>. `getCellsBySymbol(symbol)`: List\<Cell>.
- `EvaluationResult` — isWinner (boolean), prizeAmount (double), winningPositions (List\<Position>), matchDetails (Map\<String, Integer> — symbol → count of matches). The `matchDetails` field enables near-miss analysis without a separate evaluation pass (DRY).

**Interfaces:**

`GridPopulator` — reverse-engineers a symbol layout from a predetermined outcome.
- `populate(GridSize, double prizeAmount, List<String> symbolPool)`: Grid — given a grid size, the target prize amount (0 for losers), and the available symbols, produces a valid grid. For winners, the grid contains a winning pattern that evaluates to exactly `prizeAmount`. For losers, the grid contains no accidental winning patterns.

`WinEvaluator` — determines the outcome of a grid.
- `evaluate(Grid)`: EvaluationResult — scans the grid for winning patterns, returns the result including match details. The same evaluator instance is used at generation time (verification) and at play time (reveal). One implementation, two contexts.

`GameMechanic` — bundles a populator and evaluator for a specific mechanic type. Factory interface.
- `getType()`: MechanicType
- `createPopulator()`: GridPopulator
- `createEvaluator()`: WinEvaluator
- `getDefaultSymbolPool()`: List\<String> — symbols appropriate for this mechanic (numbers for NUMBER_MATCH, fruits/icons for MATCH_3, letters for CROSSWORD, etc.)

**Implementations:**

| Mechanic | Populator | Evaluator | Constraint Logic |
|----------|-----------|-----------|-----------------|
| Match3 | `Match3Populator` | `Match3Evaluator` | Winner: place exactly 3 of one symbol. Loser: no symbol appears 3+ times. |
| NumberMatch | `NumberMatchPopulator` | `NumberMatchEvaluator` | Winner: at least one overlap between YOUR NUMBERS and WINNING NUMBERS with prize beneath. Loser: zero overlap between the two sets. |
| KeySymbol | `KeySymbolPopulator` | `KeySymbolEvaluator` | Winner: key symbol matches at least one cell in play area. Loser: key matches nothing. |
| TicTacToe | `TicTacToePopulator` | `TicTacToeEvaluator` | Winner: three identical symbols in a row/column/diagonal. Loser: no three-in-a-row exists. |
| Bingo | `BingoPopulator` | `BingoEvaluator` | Winner: called numbers complete a line on the bingo card. Loser: no complete line. |
| Crossword | `CrosswordPopulator` | `CrosswordEvaluator` | Winner: revealed letters complete N+ words. Loser: fewer than threshold words completed. |

**Constructive algorithms only — no reject-and-retry.** Every `GridPopulator` implementation must build the layout mathematically to guarantee the outcome in a single pass. The alternative — generate a random layout, check if it matches the target outcome, discard and retry if not — has unbounded runtime. For loser tickets on combinatorially dense mechanics (Bingo, Crossword), the probability of a random layout accidentally satisfying "no winning pattern exists" can be low, causing retry counts to spike exponentially. This is why Bingo and Crossword are deferred (§13) — their constructive constraint solvers are non-trivial to write. Match-3 and Number Match have straightforward constructive solutions:

- **Match-3 constructive winner:** pick the winning symbol, place exactly 3 at valid positions, fill remaining cells via `GridUtils.fillRemaining` with the winning symbol in the exclusion set, run `hasAccidentalWin` once as a safety check. O(grid_size).
- **Match-3 constructive loser:** fill cells with random symbols from the pool, constrained so no symbol appears more than 2 times. O(grid_size).
- **Number Match constructive winner:** generate the WINNING NUMBERS set, select K numbers from it to place in YOUR NUMBERS (guaranteeing overlap), fill remaining YOUR NUMBERS from the complement set. O(set_size).
- **Number Match constructive loser:** generate WINNING NUMBERS and YOUR NUMBERS from disjoint subsets of the number pool. O(set_size).

**Utility — `GridUtils`:**

Shared helper functions used by all `GridPopulator` implementations via composition. Not an abstract base class — no inheritance hierarchy for populators.

- `fillRemaining(Grid, List<String> symbolPool, Set<String> excludeSymbols)`: Grid — fills empty cells with random symbols from the pool, excluding specified symbols to prevent accidental pattern creation.
- `getRandomSymbols(List<String> pool, int count, Set<String> exclude)`: List\<String> — selects `count` distinct symbols from the pool, never picking from `exclude`.
- `hasAccidentalWin(Grid, WinEvaluator)`: boolean — runs the evaluator against a grid to check for unintended winning patterns. Used after filling remaining cells on loser tickets.
- `placeSymbolsAtPositions(Grid, String symbol, List<Position>)`: Grid — places a specific symbol at given positions. Used by populators when constructing winning patterns.

**Near-Miss Analysis — `NearMissAnalyzer`:**

Depends on `EvaluationResult.matchDetails` rather than re-evaluating the grid (DRY — reuses the data already computed by `WinEvaluator`).

- `analyze(EvaluationResult, MechanicType)`: NearMissResult — determines whether a losing ticket is "close" to winning. For MATCH_3: `maxMatchCount == 2` out of required 3. For NUMBER_MATCH: one number away from a match. Returns `isNearMiss` (boolean), `distance` (int — how far from a win), `description` (String — human-readable for the educational layer).

---

### Subsystem 4: Generation Pipeline (Orchestration)
*Orchestrates Subsystems 1–3 and the verification suite. Depends on interfaces, not implementations (DI).*

The pipeline composes the four generation layers (§3.3) into a single orchestrated flow. Each step is a separate class; the pipeline wires them together.

**Value Objects:**
- `TicketLayout` — outcomeId (UUID), grid (Grid), mechanicType (MechanicType). Intermediate representation after mechanic mapping, before theme skinning.
- `TicketCard` — ticketId (UUID), layout (TicketLayout), skinnedGrid (ThemedGrid), theme (ThemeRef). Final output — fully renderable.
- `GenerationResult` — tickets (List\<TicketCard>), verificationReport (VerificationReport), nearMissReport (NearMissReport), generationTimeMs (long).

**Service — `GenerationPipeline`:**

Constructor-injected dependencies: `OutcomeGenerator`, `ShuffleService`, `GameMechanic`, `ThemeSkinningService`, `VerificationSuite`.

- `generate(PoolContract, MechanicType, ThemeRef)`: GenerationResult
  1. Validate pool via `PoolValidator`
  2. Generate outcomes via `OutcomeGenerator.generate(pool)`
  3. Shuffle via `ShuffleService.shuffle(outcomes)`
  4. For each outcome: map to layout via `mechanic.createPopulator().populate(gridSize, outcome.prizeAmount, symbolPool)`
  5. Verify all layouts via `VerificationSuite.verify(layouts, pool, mechanic.createEvaluator())`
  6. If verification fails: throw `GenerationIntegrityException` (generation is aborted, not patched)
  7. Skin each layout via `ThemeSkinningService.skin(layout, theme)`
  8. Return `GenerationResult` with all tickets, verification report, and timing

**Design note:** The pipeline does not retry or patch failed tickets. If verification fails, the entire batch is rejected. This is deliberate — a partially valid batch is more dangerous than no batch (a false-positive loser could silently grant prizes). The creator adjusts the pool contract and regenerates.

---

### Subsystem 5: Verification
*Pure domain logic — depends on Subsystem 1 (Pool Design) and Subsystem 3 (Mechanic Engine)*

The verification suite is separated from the generation pipeline because it has a different lifecycle — it may be run independently (e.g., re-verifying a stored batch after a code change) and its rules evolve independently of generation logic (SRP).

**Value Objects:**
- `VerificationReport` — passed (boolean), checks (List\<CheckResult>). Each check has a name, passed/failed, and a message.
- `NearMissReport` — totalLosers (int), nearMissCount (int), nearMissRate (double), distribution (Map\<Integer, Integer> — distance → count).

**Service — `VerificationSuite`:**

Constructor-injected: `PoolValidator`, `NearMissAnalyzer`.

- `verify(List<TicketLayout>, PoolContract, WinEvaluator)`: VerificationReport — runs all checks:
  - `checkTierCounts(layouts, pool)`: CheckResult — count of tickets at each prize amount matches contract exactly.
  - `checkPayoutRatio(layouts, pool)`: CheckResult — total prize sum / total revenue within tolerance.
  - `checkNoFalsePositives(layouts, evaluator)`: CheckResult — every $0 ticket evaluates to `isWinner == false`. Zero tolerance.
  - `checkNoBrokenWinners(layouts, evaluator)`: CheckResult — every winning ticket evaluates to `isWinner == true` with the correct `prizeAmount`.
  - `checkDistributionSpread(layouts, pool)`: CheckResult — winners are not clumped. Uses a chi-squared test against uniform distribution across positional segments.

- `analyzeNearMisses(List<TicketLayout>, WinEvaluator, MechanicType)`: NearMissReport — runs `NearMissAnalyzer` on every loser ticket, aggregates results. This is informational, not a pass/fail — near-misses are expected (and in realistic mode, deliberately engineered).

---

### Subsystem 6: Theme Skinning
*Pure domain logic with external asset references*

Theme skinning is the final generation layer — mapping abstract symbols to visual representations. It is separated from mechanic mapping because the same mechanic (MATCH_3) can be skinned with dozens of different themes (cowboy, desert, neon). Skinning depends on mechanic output but not mechanic logic (SRP).

**Value Objects:**
- `ThemeRef` — themeId (String), name (String), symbolMap (Map\<String, ThemedSymbol>), palette (ColorPalette), backgroundArt (AssetRef), coatingConfig (CoatingConfig), sparkleGif (AssetRef, nullable).
- `ThemedSymbol` — abstractSymbol (String), displayEmoji (String), displayImageUrl (String, nullable), displayLabel (String).
- `ThemedGrid` — size (GridSize), cells (ThemedCell[][]). Each `ThemedCell` wraps a `Cell` with its `ThemedSymbol`.
- `ColorPalette` — primary (String), secondary (String), accent (String), background (String), text (String).
- `CoatingConfig` — baseColor (String), gradientStops (List\<String>), noiseIntensity (double), hatchAngle (int), hatchSpacing (int).

**Service — `ThemeSkinningService`:**
- `skin(TicketLayout, ThemeRef)`: TicketCard — maps every abstract symbol in the layout to its themed visual using the theme's `symbolMap`. Returns a fully renderable `TicketCard`.
- `getAvailableThemes()`: List\<ThemeRef>
- `getTheme(themeId)`: ThemeRef

**Design note:** Themes are data, not code. Adding a new theme is a configuration change (new JSON/data entry), not a code change. No `CowboyTheme extends Theme` classes. The `ThemeRef` is a value object loaded from storage — a theme registry, not a class hierarchy. This aligns with ScratchAll's confirmed approach of using JSON arrays for ticket configuration.

---

### Subsystem 7: Distribution — Book Partitioning
*Pure domain logic — depends on Subsystem 4 (Generation Pipeline output)*

Partitioning is the process of splitting a flat list of generated tickets into ordered books. This subsystem is concerned only with the partition — not with who receives the books (that is Subsystem 8).

**Value Objects:**
- `TicketBook` — bookId (UUID), tickets (List\<TicketCard>, ordered), poolContractId (UUID).
  - `getTotalTickets()`: int
  - `getTicketsRemaining()`: int
  - `getBookValue()`: double — `sum(ticket.layout.prizeAmount)` for all tickets. Derived, not stored separately.
  - `getPrizesDispensed()`: double — sum of prizes on sold tickets.
  - `getNextTicket()`: TicketCard — returns the next unsold ticket in sequence (§3.12). Throws `BookDepletedException` if empty.
  - `isDepleted()`: boolean
- `PartitionResult` — books (List\<TicketBook>), bookValueStats (BookValueStats).
- `BookValueStats` — min (double), max (double), mean (double), stddev (double), median (double). Computed from the book values. Characterizes the inter-book variance for this partition.

**Service — `BookPartitioner`:**
- `partition(List<TicketCard>, int bookCount)`: PartitionResult — takes the shuffled ticket list (already shuffled by `ShuffleService` in the generation pipeline), deals tickets sequentially into `bookCount` books (round-robin or sequential chunks — design TBD), computes `BookValueStats`, returns result.

**Design note:** The partitioner does not re-shuffle. The tickets arrive pre-shuffled from the generation pipeline. The partitioner only deals them into books. This separation means the shuffle can be seeded for testing independently of the partition logic (SRP).

The `BookValueStats` output is used by the `DealerAllocator` (Subsystem 8) to sort books by value and by the educational ledger to explain inter-book variance to the player.

---

### Subsystem 8: Distribution — Dealer Allocation
*Domain logic with persistence — depends on Subsystem 7 (Book Partitioning)*

Allocation assigns books to dealer NPCs based on their rank. Separated from partitioning because the criteria for "which dealer gets which book" changes independently of "how tickets are split into books" (SRP). The partitioner produces books; the allocator distributes them.

**Enums:**
- `DealerTier` — TIER_1, TIER_2, TIER_3
- `AllocationQuartile` — LOWER, MIDDLE, UPPER

**Entity — `Dealer`:**
- dealerId (UUID)
- name (String)
- tier (DealerTier)
- rankScore (int)
- activeBooks (List\<TicketBook>)
- booksPerCycle (int) — **same for all dealers** (throughput cap, §3.6)
- booksDepleted (int) — lifetime count, drives ranking
- `getAllocationQuartile()`: AllocationQuartile — derived from tier. TIER_1 → LOWER, TIER_2 → MIDDLE, TIER_3 → UPPER.
- `canAcceptBooks()`: boolean — `activeBooks.size() < booksPerCycle`
- `addBook(TicketBook)`: void
- `onBookDepleted(TicketBook)`: void — increments `booksDepleted`, removes from `activeBooks`

**Service — `DealerAllocator`:**

Constructor-injected: `DealerTierResolver`.

- `allocate(List<TicketBook>, List<Dealer>)`: Map\<Dealer, List\<TicketBook>> — sorts books by `getBookValue()` ascending. Divides the sorted list into quartile segments. Assigns books from each quartile to dealers in the matching `AllocationQuartile`. Within a quartile, assignment is random (no further ordering among same-tier dealers).
- Constraint: each dealer receives at most `booksPerCycle` books. Excess books remain unallocated (returned in a separate "overflow" list for the next cycle).

**Service — `DealerTierResolver`:**
- `resolve(Dealer)`: DealerTier — maps `booksDepleted` to tier. Current formula (subject to tuning):
  - `booksDepleted < 10` → TIER_1
  - `booksDepleted < 50` → TIER_2
  - `booksDepleted >= 50` → TIER_3
- `resolveAll(List<Dealer>)`: void — batch update all dealer tiers. Called once per allocation cycle.

**Service — `DealerRegistry`:**
- `getAllDealers()`: List\<Dealer>
- `getDealer(dealerId)`: Dealer
- `getDealersByTier(DealerTier)`: List\<Dealer>
- `initializeDealers(int count)`: List\<Dealer> — creates NPC dealers with names and starting state. Called once during system setup.

---

### Subsystem 9: Player & Bank
*Domain logic with persistence*

The player and bank are separated because the bank's rules (unlimited borrowing, no interest, no repayment obligation) are a distinct concern from the player's identity and state. The bank is the legal shield (§3.1) — its design decisions are driven by legal positioning, not game balance.

**Entity — `Player`:**
- playerId (UUID)
- displayName (String)
- coinBalance (double)
- totalBorrowed (double) — running total, updated transactionally on every BORROW
- totalSpent (double) — running total, updated transactionally on every SPEND
- totalWon (double) — running total, updated transactionally on every WIN
- ticketCount (int) — running total, incremented on every purchase
- `getNetPosition()`: double — `totalWon - totalSpent`. Derived, not stored.
- `getRollingReturnRate()`: double — `totalWon / totalSpent`. Derived. Returns 0 if totalSpent is 0.
- `canAfford(double amount)`: boolean
- `debit(double amount)`: void — reduces coinBalance, increments totalSpent. Throws `InsufficientBalanceException` if balance < amount.
- `credit(double amount)`: void — increases coinBalance, increments totalWon.
- `recordBorrow(double amount)`: void — increases coinBalance, increments totalBorrowed.

**Design note on running totals:** The feedback identified that computing `LedgerSnapshot` by aggregating all transactions on every request is an O(N) bottleneck that worsens as the player buys more tickets. Running totals on the `Player` entity are updated transactionally alongside each ledger insert — the `BankService` calls `player.recordBorrow(amount)` and `TransactionRecorder.record(transaction)` in the same transaction. The snapshot reads these pre-computed totals (single row read) instead of scanning the transactions table. The transactions table still exists for history, drill-down, and per-dealer/per-book breakdowns, but the hot path (balance check, insight generation, dashboard render) never aggregates it.

**Service — `BankService`:**
- `borrow(Player, double amount)`: Transaction — calls `player.recordBorrow(amount)`, records a BORROW transaction on the ledger. No limit, no interest, no repayment deadline. The bank never says no.
- `getBorrowedTotal(Player)`: double — returns `player.totalBorrowed`. No aggregation needed.

**Design note:** There is no `Loan` entity. Borrowing is a one-way transfer with no repayment flow. Tracking it as a ledger transaction is sufficient. Adding loan objects, repayment schedules, or interest would imply a game mechanic that contradicts the "free money" legal requirement.

---

### Subsystem 10: Scratch & Purchase
*Domain logic with persistence — depends on Subsystems 7, 8, 9*

The scratch flow is the player-facing interaction: pick a dealer, pick a book, buy a ticket, reveal the result. This subsystem orchestrates the purchase (debit coins, get next ticket from book) and the reveal (return the grid result).

**Enums:**
- `TicketStatus` — AVAILABLE, SOLD, REVEALED

**Value Objects:**
- `PurchaseResult` — ticketId (UUID), ticketStatus (TicketStatus), coinsDeducted (double), dealerId (UUID), bookId (UUID).
- `RevealResult` — ticketId (UUID), skinnedGrid (ThemedGrid), evaluationResult (EvaluationResult), prizeAmount (double), isWinner (boolean).

**Service — `TicketPurchaseService`:**

Constructor-injected: `BankService`, `TransactionRecorder`.

- `purchase(Player, Dealer, TicketBook)`: PurchaseResult — validates player can afford ticket price, validates book is not depleted, calls `player.debit(price)`, calls `book.getNextTicket()`, marks ticket as SOLD, records a SPEND transaction on the ledger.

**Service — `ScratchRevealService`:**

Constructor-injected: `WinEvaluator` (injected per mechanic type), `TransactionRecorder`.

- `reveal(Player, TicketCard)`: RevealResult — runs `evaluator.evaluate(ticket.layout.grid)`, marks ticket as REVEALED, if winner: calls `player.credit(prizeAmount)` and records a WIN transaction on the ledger. Returns full reveal result for frontend rendering.
- `getRevealedResult(ticketId)`: RevealResult — returns the stored result for an already-revealed ticket. Idempotent — revisiting a revealed ticket returns the same data.

**Design note:** Purchase and reveal are separate operations, not a single "scratch" call. This matches real lottery behavior — you buy the ticket (money leaves your hand) and then scratch it (outcome is revealed). The separation creates a moment where the player has spent coins but doesn't yet know the result. That anticipation gap is a core part of the gambling psychology the simulator aims to surface.

---

### Subsystem 11: Ledger & Educational Insights
*Domain logic with persistence — depends on Subsystems 9, 10 for transaction data*

The ledger is the educational core (§3.13). It records every transaction and periodically generates insights. The insight generation uses the Strategy pattern — new insight types are added by implementing `InsightGenerator`, without changing the ledger itself (OCP).

**Enums:**
- `TransactionType` — BORROW, SPEND, WIN
- `InsightSeverity` — INFO, WARNING, CRITICAL

**Value Objects:**
- `Transaction` — transactionId (UUID), playerId (UUID), type (TransactionType), amount (double), dealerId (UUID, nullable), bookId (UUID, nullable), ticketId (UUID, nullable), timestamp (Instant). Immutable.
- `LedgerSnapshot` — read-only aggregate of a player's ledger state at a point in time. Consumed by `InsightGenerator` implementations. Contains:
  - totalBorrowed (double)
  - totalSpent (double)
  - totalWon (double)
  - netPosition (double) — `totalWon - totalSpent`
  - ticketCount (int)
  - rollingReturnRate (double) — `totalWon / totalSpent`
  - perDealerStats (Map\<UUID, DealerStats>) — per-dealer spend, win, return rate
  - perBookStats (Map\<UUID, BookStats>) — per-book spend, win, count
  - recentTransactions (List\<Transaction>) — last N transactions for streak/pattern detection
  - sessionBorrowEvents (List\<Transaction>) — borrow events in current session, for loss-chasing detection
- `Insight` — type (String), severity (InsightSeverity), title (String), message (String), data (Map\<String, Object> — supporting numbers for frontend rendering), timestamp (Instant). Immutable.

**Interface — `InsightGenerator`:**
- `evaluate(LedgerSnapshot)`: Optional\<Insight> — examines the snapshot, returns an insight if the trigger condition is met, empty otherwise. Each implementation encapsulates one educational observation.

**Implementations:**

| Implementation | Trigger | Message Example |
|---------------|---------|-----------------|
| `LossRateInsight` | `rollingReturnRate < 0.70` and `ticketCount >= 10` | "You've spent 4,800 coins and won back 3,200. That's a 33% loss rate." |
| `LossChasingInsight` | ≥3 BORROW events within the last 10 transactions (borrowing more to chase losses) | "You've borrowed coins 3 times in the last 10 transactions. In real gambling, this pattern is called loss chasing." |
| `LuckyStoreDebunkInsight` | Player's per-dealer return rates vary by >15 percentage points and `ticketCount >= 20` | "Your return at Joe's was 72%, at Downtown it was 58%. Both books had 65% payout ratios. The difference was variance, not the store." |
| `VarianceExplanationInsight` | Player has bought from ≥3 books | "Book #4 paid 82% return. Book #7 paid 41%. Both came from the same pool. This is how natural book variance works." |
| `NearMissInsight` | ≥5 revealed tickets where `EvaluationResult.matchDetails` shows near-misses | "34% of your losing tickets had 2-of-3 matching symbols. This is an engineered manipulation, not bad luck." |
| `InevitabilityCurveInsight` | `ticketCount >= 25` and `netPosition < 0` | "Over 25 tickets, your cumulative winnings have fallen further behind your cumulative spending. This gap widens over time. It always does." |

**Service — `TransactionRecorder`:**
- `record(Transaction)`: void — persists a transaction. Called by `BankService` (BORROW), `TicketPurchaseService` (SPEND), and `ScratchRevealService` (WIN).
- `getTransactions(playerId)`: List\<Transaction>
- `getTransactions(playerId, TransactionType)`: List\<Transaction>
- `getRecentTransactions(playerId, int limit)`: List\<Transaction>

**Service — `LedgerService`:**

Constructor-injected: `TransactionRecorder`, `List<InsightGenerator>` (all registered generators).

- `getSnapshot(playerId)`: LedgerSnapshot — reads pre-computed running totals from the `Player` entity (single row read), then queries per-dealer and per-book breakdowns from the transactions table only for the drill-down fields. The hot-path fields (totalBorrowed, totalSpent, totalWon, netPosition, ticketCount, rollingReturnRate) are O(1) reads, not aggregations.
- `generateInsights(playerId)`: List\<Insight> — creates a snapshot, runs every registered `InsightGenerator` against it, collects non-empty results. Called after every transaction (or on a polling interval from the frontend).
- `getDealerComparison(playerId)`: Map\<UUID, DealerStats> — extracted from snapshot. Shows per-dealer return rates side by side.
- `getInevitabilityCurve(playerId)`: List\<CurvePoint> — time-series of cumulative spent vs. cumulative won, for chart rendering.

**Design note:** `InsightGenerator` implementations are stateless — they receive a snapshot and return a result. They do not store whether an insight has been shown before. De-duplication (don't show the same insight repeatedly) is a frontend concern, not a domain concern. The ledger generates insights; the UI decides when to display them.

---

### Subsystem 12: Generation Orchestration (Full Pipeline)
*Orchestrates Subsystems 1–8 into a complete generation-to-allocation flow*

This is the top-level orchestrator that takes a game configuration and produces a fully playable dealer economy with stocked books.

**Value Objects:**
- `GameConfig` — poolContract (PoolContract), mechanicType (MechanicType), themeId (String), bookCount (int), dealerCount (int). Immutable. Everything needed to generate a full game.
- `GameSetupResult` — dealers (List\<Dealer> with books assigned), generationResult (GenerationResult), partitionResult (PartitionResult), allocationMap (Map\<Dealer, List\<TicketBook>>).

**Service — `GameOrchestrator`:**

Constructor-injected: `PoolFactory`, `GenerationPipeline`, `BookPartitioner`, `DealerAllocator`, `DealerRegistry`.

- `setup(GameConfig)`: GameSetupResult
  1. `PoolFactory.create(config.poolContract)` — validate and finalize pool
  2. `GenerationPipeline.generate(pool, mechanicType, themeRef)` — generate all tickets with verification
  3. `BookPartitioner.partition(tickets, config.bookCount)` — split into books
  4. `DealerRegistry.initializeDealers(config.dealerCount)` — create NPC dealers (or load existing)
  5. `DealerAllocator.allocate(books, dealers)` — assign books to dealers by rank
  6. Return complete setup result

- `restockCycle(GameConfig)`: GameSetupResult — generates a fresh batch and allocates to existing dealers. Used when all books are depleted and the economy needs new inventory. Dealer ranks persist across cycles; book allocation reflects updated rankings.

**Design note:** `GameOrchestrator` is a thin orchestrator — it holds no logic of its own, only the sequencing of calls to subsystem services. Every decision (validation, generation, partitioning, allocation) lives in the responsible subsystem. The orchestrator is the only class that depends on all subsystems; no other class has this breadth of coupling. This makes it the natural integration test boundary.

---

## 10. Dependency Graph

```
Subsystem 1: Pool Design              ← depends on nothing
Subsystem 2: Outcome Generation        ← depends on 1
Subsystem 3: Mechanic Engine           ← depends on nothing
Subsystem 4: Generation Pipeline       ← depends on 1, 2, 3, 5, 6
Subsystem 5: Verification             ← depends on 1, 3
Subsystem 6: Theme Skinning           ← depends on 3 (uses Grid)
Subsystem 7: Book Partitioning        ← depends on 4 (uses TicketCard)
Subsystem 8: Dealer Allocation        ← depends on 7
Subsystem 9: Player & Bank            ← depends on nothing (domain-only)
Subsystem 10: Scratch & Purchase      ← depends on 3, 7, 8, 9, 11
Subsystem 11: Ledger & Insights       ← depends on nothing (receives transactions)
Subsystem 12: Orchestration           ← depends on 1, 4, 7, 8
```

No circular dependencies. All arrows point "downward" or "sideways," never "upward." The core domain subsystems (1, 2, 3, 5, 6, 9, 11) depend on nothing external. The orchestration layer (4, 12) depends on domain subsystems via interfaces. The interaction layer (10) depends on both.

---

## 11. REST API

All endpoints return JSON. Error responses use a consistent envelope: `{ "error": "message", "code": "ERROR_CODE" }`. Successful mutations return the created/updated resource. Successful reads return the resource directly.

There is no authentication. This is a single-player simulator with no user accounts, no multi-tenancy, and no sensitive data. The player ID is passed as a path parameter or created on first visit. If authentication is added later, it layers on top without changing endpoint signatures.

### Health & System
- `GET /api/health` — dependency health check (Supabase, any external services). Returns 200 if system is functional, 503 if critical dependencies are down.

### Game Setup

These endpoints provide read access to pre-generated game data. Game generation runs as a local CLI tool (see §12), not as a live API endpoint.

- `GET /api/games` — list all available games. Returns game ID, mechanic type, theme, pool summary (total tickets, payout ratio), creation timestamp.
- `GET /api/games/{gameId}` — get game details. Returns pool contract, mechanic type, theme, book count, dealer count, generation timestamp, verification status.
- `GET /api/games/{gameId}/verification` — get full verification report for a game. Returns tier count checks, payout ratio check, false-positive/broken-winner check results, distribution spread test. Generated by the CLI during the generation step and stored alongside the game data.
- `GET /api/games/{gameId}/near-misses` — get near-miss analysis for a game. Returns total losers, near-miss count, near-miss rate, distance distribution. Educational data for the ledger layer.

### Player

- `POST /api/players` — create a new player. Body: `{ "displayName": "string" }`. Returns player with ID, starting balance of 0, empty ledger.
- `GET /api/players/{playerId}` — get player state. Returns balance, total borrowed, total spent, total won, net position, ticket count.
- `POST /api/players/{playerId}/borrow` — borrow coins from the bank. Body: `{ "amount": 200 }`. No limit, no interest, no repayment. Returns updated balance and a BORROW transaction record.

### Dealers

- `GET /api/games/{gameId}/dealers` — list all dealers in a game. Returns dealer ID, name, tier, rank score, number of active books, books depleted. Sorted by rank descending.
- `GET /api/dealers/{dealerId}` — get dealer details. Returns full dealer profile: name, tier, rank, active book summaries, lifetime stats (books depleted, total tickets sold through this dealer).
- `GET /api/games/{gameId}/dealers/rankings` — dealer rankings table. Returns all dealers sorted by books depleted with tier labels. Designed for a frontend leaderboard view — but this ranks *dealers*, not players.

### Books

- `GET /api/dealers/{dealerId}/books` — list active books at a dealer. Returns book ID, total tickets, tickets remaining, ticket price, and metadata based on the book's `MetadataVisibility` setting:
  - `NONE`: only ticket count and remaining count
  - `PARTIAL`: adds prizes dispensed so far (as a percentage of book value)
  - `FULL`: adds exact prizes dispensed, estimated remaining value, win frequency so far
- `GET /api/books/{bookId}` — get book details. Same metadata visibility rules as the list endpoint. Also returns pool contract summary (payout ratio, mechanic type) so the player knows what kind of game they are buying into.

### Tickets & Scratch Flow

The core player interaction. Three-step flow: browse → purchase → reveal. Purchase and reveal are deliberately separate (§3, Subsystem 10 design note).

- `POST /api/books/{bookId}/purchase` — buy the next ticket from a book. Body: `{ "playerId": "uuid" }`. Validates player can afford the ticket price. Validates book is not depleted. Debits player balance. Returns the next ticket in sequence with status SOLD, ticket ID, and the masked grid (symbols hidden, grid dimensions visible). Records a SPEND transaction on the ledger. Returns 402 if insufficient balance. Returns 410 if book is depleted.
- `GET /api/tickets/{ticketId}` — get ticket state. If AVAILABLE: should not be directly accessible (tickets are purchased through books). If SOLD: returns masked grid awaiting reveal. If REVEALED: returns full skinned grid with evaluation result, prize amount, winning positions. Idempotent — revisiting a revealed ticket returns the same data.
- `POST /api/tickets/{ticketId}/reveal` — reveal the ticket outcome. Body: `{ "playerId": "uuid" }`. Runs the `WinEvaluator` against the ticket's grid. Transitions ticket to REVEALED. If winner: credits player balance and records a WIN transaction. Returns `RevealResult`: full skinned grid, evaluation result (isWinner, prizeAmount, winningPositions, matchDetails for near-miss data), and the player's updated balance. Idempotent — revealing an already-revealed ticket returns the stored result, not a 409. Returns 404 if ticket does not exist. Returns 403 if the requesting player did not purchase this ticket.

### Ledger & Insights

The educational core. All ledger endpoints are read-only — the ledger is populated by side effects of borrow, purchase, and reveal operations.

- `GET /api/players/{playerId}/ledger` — full ledger snapshot. Returns `LedgerSnapshot`: total borrowed, total spent, total won, net position, ticket count, rolling return rate, per-dealer breakdown summary, per-book breakdown summary.
- `GET /api/players/{playerId}/ledger/transactions` — paginated transaction history. Query params: `type` (BORROW | SPEND | WIN, optional filter), `dealerId` (optional filter), `limit` (default 50), `offset` (default 0). Returns list of transactions with type, amount, dealer name, book ID, ticket ID, timestamp.
- `GET /api/players/{playerId}/ledger/insights` — current educational insights. Runs all registered `InsightGenerator` implementations against the player's ledger snapshot. Returns list of `Insight` objects: type, severity, title, message, supporting data. The frontend decides which to show and how to deduplicate across sessions.
- `GET /api/players/{playerId}/ledger/dealer-comparison` — per-dealer statistics side by side. Returns a list of `{ dealerId, dealerName, ticketsBought, totalSpent, totalWon, returnRate }` for every dealer the player has bought from. Sorted by return rate descending. This is the data that powers the "lucky store" debunking insight.
- `GET /api/players/{playerId}/ledger/curve` — inevitability curve data. Returns a time-ordered list of `{ ticketNumber, cumulativeSpent, cumulativeWon, netPosition }` data points. One entry per ticket purchased. Designed for chart rendering — the frontend draws cumulative spent vs. cumulative won, showing the gap widening over time.
- `GET /api/players/{playerId}/ledger/books` — per-book statistics. Returns `{ bookId, dealerName, ticketsBought, spent, won, returnRate, bookPayoutRatio }` for every book the player has bought from. Includes the book's actual payout ratio so the player can compare their personal return rate against the book's mathematical expectation.

### Themes

- `GET /api/themes` — list all available themes. Returns theme ID, name, preview palette, preview image URL.
- `GET /api/themes/{themeId}` — get full theme details. Returns symbol map, color palette, background art reference, coating texture reference.

### Mechanic Info

- `GET /api/mechanics` — list all available game mechanics. Returns mechanic type, display name, description, default grid size, default symbol pool. Informational — helps a frontend show what game types are available.
- `GET /api/mechanics/{type}` — get mechanic details including win-condition rules, example winning/losing grids.

### Endpoint Summary

| Group | Endpoints | Methods | Auth Required |
|-------|-----------|---------|:---:|
| Health | 1 | GET | — |
| Game Setup | 4 | GET | — |
| Player | 3 | POST, GET | — |
| Dealers | 3 | GET | — |
| Books | 2 | GET | — |
| Tickets & Scratch | 3 | POST, GET | — |
| Ledger & Insights | 6 | GET | — |
| Themes | 2 | GET | — |
| Mechanic Info | 2 | GET | — |
| **Total** | **26** | | |

### Response Codes

| Code | Meaning | Used By |
|------|---------|---------|
| 200 | Success | All GET endpoints, idempotent reveal retries |
| 201 | Created | POST /api/players |
| 402 | Insufficient balance | POST /api/books/{bookId}/purchase when player cannot afford ticket |
| 403 | Forbidden | POST /api/tickets/{ticketId}/reveal when requesting player did not purchase the ticket |
| 404 | Not found | Any resource lookup with invalid ID |
| 409 | Conflict | True concurrent race on ticket state transition (not retries — retries are idempotent) |
| 410 | Gone | POST /api/books/{bookId}/purchase when book is depleted |
| 422 | Validation error | POST /api/games with invalid pool contract (tier costs ≠ budget, etc.) |
| 500 | Internal error | Unexpected failures |
| 503 | Service unavailable | GET /api/health when critical dependencies are down |

### Design Notes

**No PATCH endpoints.** Pool contracts are immutable after creation. Players cannot edit their profile (display name is set once). Dealers are NPCs managed by the system. Books cannot be modified. The only state mutations are: create a game, create a player, borrow coins, purchase a ticket, reveal a ticket, and restock. All mutations are POST (create) not PATCH (modify).

**No DELETE endpoints.** Nothing is deletable. Games, players, tickets, transactions, and insights are permanent records. The ledger's educational value depends on complete, unmodifiable history. A player who could delete their transaction history could hide their losses from themselves — defeating the purpose.

**Ledger endpoints are read-only.** The ledger is never written to directly via API. It is populated exclusively by side effects of the borrow, purchase, and reveal endpoints. This ensures the ledger is always consistent with the actual game state — there is no way for the ledger to disagree with reality.

**Reveal is idempotent but purchase is not.** Revealing an already-revealed ticket returns the stored result (200, not 409). This handles network retries gracefully. Purchasing is not idempotent — each POST buys the next ticket in sequence. Duplicate purchase requests (e.g., double-click) are prevented by the frontend (disable button after first click) and by the book's sequential pointer (the second request gets the second ticket, not a duplicate of the first).

**Book metadata visibility is enforced server-side.** The `GET /api/dealers/{dealerId}/books` and `GET /api/books/{bookId}` endpoints respect the `MetadataVisibility` setting on each book. A NONE-visibility book returns only ticket counts; the server never sends prize data that the frontend should not show. The frontend cannot bypass visibility — it never receives the hidden data.

---

## 12. Deployment Architecture

```
Browser (mobile-first)
  ├── Scratch page (HTML/CSS ticket + Canvas scratch layer)  ─┐
  ├── Dealer browser                                          │
  ├── Ledger dashboard                                        ├──→  Supabase Edge Functions (play & read API)
  └── Insight visualizations                                  │         ├──→  Supabase Postgres (pools, books, tickets, ledger)
                                                              │         └──→  Supabase Auth (player sessions, if added later)
                                                              │
Static assets (ticket art, themes, sparkle GIFs, particles) ─┘──→  Cloudflare Pages / Vercel (static hosting)

Local admin CLI (Node/Python)  ──────→  Supabase Postgres (bulk insert generated batches)
```

**Why Supabase, not a custom backend:** This is a portfolio project with zero revenue. Supabase's free tier provides Postgres, auth, edge functions, and a REST layer out of the box. There is no ops burden, no server to maintain, no Docker to deploy. If the project ever needs to migrate to a custom backend, the domain model is framework-free — the subsystem code moves unchanged; only the HTTP routing layer changes.

**Generation runs locally, not on edge functions.** Supabase Edge Functions (Deno-based) have strict wall-clock and CPU limits on the free tier (2-5 seconds). The generation pipeline — validating a pool, generating 10,000 outcomes, running constraint solvers to reverse-engineer layouts, evaluating all 10,000 for false positives, shuffling, partitioning into books, and bulk-inserting — will exceed this timeout by an order of magnitude. The pipeline runs as a local Node or Python CLI tool that acts as an admin client, pushing pre-generated batches into Supabase via the client library. Edge functions serve only the play and read endpoints — get dealer, get book, purchase ticket, reveal, ledger — which are simple DB reads and writes that complete in milliseconds.

This means `POST /api/games` does not exist as a live endpoint. The game setup flow is: run the CLI locally → CLI executes the full pipeline → CLI inserts pools, books, tickets, and dealers into Supabase → the edge function API serves the generated data to the frontend. The `GET /api/games/{gameId}` endpoint still exists for reading game metadata. The generation pipeline is a build step, not a runtime operation.

**Frontend:** Single static site hosted on Cloudflare Pages or Vercel (free tier). No framework — vanilla JS/TS with hybrid HTML/CSS + Canvas 2D for scratch mechanics (§6.0). Pages: dealer browser, book selector, scratch canvas, ledger dashboard, insight charts.

**Database:** Supabase Postgres. Tables map directly to the domain model:
- `pool_contracts` — immutable pool definitions
- `ticket_outcomes` — generated outcomes with prize amounts
- `ticket_cards` — full ticket data including JSONB grid and JSONB skinned grid
- `books` — book metadata, foreign key to pool_contract
- `book_tickets` — join table maintaining sequential order within a book
- `dealers` — NPC dealer state, tier, rank
- `dealer_books` — which books are allocated to which dealer
- `players` — balance, display name, running totals (totalBorrowed, totalSpent, totalWon)
- `transactions` — every borrow, spend, win event (append-only, used for history and drill-down, not aggregation)
- `themes` — theme JSON data (symbol maps, palettes, coating configs, asset references)
- `games` — game metadata linking pool contract, mechanic type, theme, verification report

**Static assets:** Ticket art (Flux-generated backgrounds composited in Photopea, symbol images, coating textures, sparkle GIFs) are pre-generated and served as static files from the hosting provider. No S3, no CDN configuration — Cloudflare Pages handles caching automatically.

---

## 13. Build Scope

This is a portfolio and learning project. There are no users, no launch date, and no commercial intent. The build scope is defined by what demonstrates engineering skill and what teaches the developer something new — not by what a hypothetical user needs.

### What Gets Built

**Core generation pipeline — the hard engineering problem:**
- `PoolContract` with validation and constraint solving
- `OutcomeGenerator` + `ShuffleService`
- At least two `GameMechanic` implementations (Match-3, Number Match) with full constraint-solving populators and evaluators
- `VerificationSuite` with all five checks
- `ThemeSkinningService` with at least two themes
- `BookPartitioner` with `BookValueStats`
- `DealerAllocator` with tier-based quartile assignment
- `GameOrchestrator` wiring the full pipeline end-to-end

**Player interaction layer:**
- `Player` + `BankService`
- `TicketPurchaseService` + `ScratchRevealService`
- `TransactionRecorder` + `LedgerService`
- At least three `InsightGenerator` implementations (LossRate, LossChasing, LuckyStoreDebunk)

**Frontend:**
- Dealer browser with book selection
- Hybrid HTML/CSS + Canvas scratch card (§6.0): CSS ticket layout, Canvas scratch-to-erase layer, GIF sparkle overlay, particle library integration for scratch dust and win celebrations
- Ledger dashboard with transaction history, dealer comparison, and inevitability curve chart

**Infrastructure:**
- Supabase Postgres with schema matching the domain model (11 tables)
- Supabase Edge Functions for the play and read API (26 endpoints)
- Local CLI tool (Node or Python) for running the generation pipeline and bulk-inserting into Supabase
- Static hosting on Cloudflare Pages or Vercel

### What Stays in the Doc

- Bingo and Crossword mechanics (combinatorially complex constraint solvers — valuable engineering but not required for the core demo)
- Lottery Commissioner experimental mode (§4.5)
- Near-miss engineering in realistic mode (§3.11)
- Authentication and session management
- More than two themes
- Mobile-specific optimizations beyond basic responsive layout
- Custom-illustrated symbol sets (emoji set is sufficient for MVP)

### Class & Endpoint Count

| Metric | Count |
|--------|------:|
| Domain classes / value objects | ~35 |
| Interfaces | 6 (GameMechanic, GridPopulator, WinEvaluator, InsightGenerator, + 2 internal) |
| Concrete implementations | ~20 (2 mechanics × 2 each, 3 insights, utilities, services) |
| REST endpoints | 26 |
| Postgres tables | 11 |

---

## 14. Open Design Questions

These are identified but not yet resolved:

1. **PoolContract constraint solver.** The formal validation logic that rejects internally inconsistent prize tables (tier costs + floor costs ≠ prize budget). Interface is clear; implementation details are not.
2. **Partition algorithm.** The exact code logic that takes a shuffled pool and splits it into books while the remainder layer creates natural but bounded variance. The mathematical properties of this partition (expected book-value distribution as a function of prize table shape) are not yet formalized.
3. **Mechanic-specific constraint solvers.** The reverse-engineering step (Layer 3 of §3.3) requires a constraint solver per mechanic type. Match-3 and number-match are straightforward; bingo and crossword are combinatorially complex. Implementation order and fallback strategies (reject-and-retry vs. constructive generation) are not yet decided.
4. **Dealer ranking formula.** How `books_depleted_total` maps to tier progression. Linear? Logarithmic? Step-function with thresholds? The formula determines how quickly the dealer economy differentiates and how stable the tier structure is over time.
5. **Book metadata visibility rules.** How much a player can see about a book's current depletion state (tickets remaining, payouts dispensed, estimated remaining value). More visibility enables "smart" play (inferring whether big winners are left); less visibility preserves the gambler's uncertainty. The right balance is a design choice with educational implications.
6. **Educational insight triggers.** When and how the ledger surfaces insights. After N tickets? After a loss streak of length K? After the rolling return rate drops below a threshold? Too early is annoying; too late misses the teaching moment.
7. **Dealer NPC generation.** How many dealers, what names, what visual identities. Static set or procedurally generated? How many books per cycle defines the economy's turnover rate.
8. **Near-miss engineering specifics.** The exact algorithm for placing 2-of-3 near-misses on loser tickets in realistic mode. Must avoid creating patterns that a mechanic-specific evaluator would flag as wins.
