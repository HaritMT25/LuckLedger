---
name: scratch-card-domain
description: "Domain invariants, vocabulary, generation pipeline, and business rules for LuckLedger. MUST be loaded for any agent working on this project."
---

## What LuckLedger Is
Free-to-play scratch card simulator. Gambling awareness tool. Virtual currency,
no real money. Persistent ledger surfaces that the house always wins.
No monetization. Education-first.

## Vocabulary
| Term | Definition |
|------|-----------|
| PoolContract | Immutable spec: total tickets, price, payout ratio, prize tiers |
| TicketOutcome | (UUID, BigDecimal prizeAmount) — no visuals, Layer 2 output |
| TicketLayout | Outcome + mechanic grid — Layer 3 output |
| TicketCard | Layout + themed visuals — fully renderable, Layer 4 output |
| TicketBook | Ordered sequence, sold sequentially (#1 first, then #2...) |
| Dealer | NPC storefront receiving books by rank |
| Ledger | Append-only transaction log — BORROW, SPEND, WIN |
| Insight | Educational observation from ledger (loss rate, loss chasing, etc.) |
| RTP | Return To Player — sum(prizes) / sum(revenue), target ~65% |

## Six Invariants (violations are blocking defects)
1. **Payout Ratio Sacred** — 65% game = 65% everywhere, always
2. **Pre-Generated Pools** — outcomes determined before any ticket exists
3. **Verification Mandatory** — generation throws without verification pass
4. **Constructive Only** — grid populators guarantee outcome in one pass
5. **Ledger Append-Only** — no updates, no deletes, complete history
6. **No Money In/Out** — bank gives free coins, no purchase, no cash-out

## Generation Pipeline
```
Layer 1: PoolContract → immutable spec
Layer 2: OutcomeGenerator → (UUID, BigDecimal) list → ShuffleService
Layer 3: GridPopulator → reverse-engineer grid → VERIFICATION (mandatory)
Layer 4: ThemeSkinning → abstract symbols → themed visuals
```

## Purchase Flow (two separate operations — NEVER combined)
```
Purchase: debit → getNextTicket → SOLD → SPEND transaction
  ↓ (anticipation gap)
Reveal: evaluate grid → REVEALED → if winner: credit → WIN transaction
```

## Running Totals
Player stores totalBorrowed/Spent/Won as BigDecimal, updated transactionally
with each ledger insert. Hot path reads O(1) fields, never aggregates transactions.

## Dealer Economy
Equal books per cycle for all dealers. Rank controls WHICH books (quality from
book-value distribution), not HOW MANY. Prevents "lucky store" flywheel.

## Package root: com.luckledger
