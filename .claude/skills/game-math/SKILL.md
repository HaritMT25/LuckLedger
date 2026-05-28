---
name: game-math
description: "Probability distributions, prize calibrations, and constructive generation algorithms for Celestial Fortune (hypergeometric) and Demon Seal (trinomial) scratch card mechanics."
---

## Celestial Fortune — Hypergeometric

N=30 pool, m=8 player picks, n=4 winning. P(k) = C(8,k)×C(22,4-k)/27405

| k | P(k) | 1-in | Prize ($5) | EV |
|---|-------|------|-----------|-----|
| 0 | 0.26693 | 3.75 | $0 | $0 |
| 1 | 0.44956 | 2.22 | $0 | $0 |
| 2 | 0.23601 | 4.24 | $2 | $0.472 |
| 3 | 0.04496 | 22.2 | $20 | $0.899 |
| 4 | 0.00255 | 391 | $740 | $1.888 |
| **RTP** | | | | **$3.259 → 65.2%** |

Constructive winner(k): pick k from winning set, fill 8-k from complement.
Constructive loser: all 8 from complement. Zero overlap guaranteed.

## Demon Seal — Trinomial

6 seals. P(gold)=0.12, P(silver)=0.40, P(broken)=0.48. T = 2G + S.

| T | P(T) | 1-in | Tier | Prize | EV |
|---|------|------|------|-------|-----|
| 0-3 | 0.4252 | 2.35 | Escapes | $0 | $0 |
| 4 | 0.2010 | 5.0 | Consolation | $2 | $0.402 |
| 5 | 0.1367 | 7.3 | Sealed-S | $4 | $0.547 |
| 6 | 0.0664 | 15 | Sealed-M | $10 | $0.664 |
| 7 | 0.0226 | 44 | Sealed-L | $25 | $0.566 |
| 8 | 0.00532 | 188 | Killed-S | $100 | $0.532 |
| 9 | 0.000816 | 1225 | Killed-M | $300 | $0.245 |
| 10-11 | 0.0000791 | 12.6k | Killed-E | $2500 | $0.188 |
| 12 | 0.00000299 | 335k | Killed-L | $25000 | $0.075 |
| **RTP** | | | | | **$3.219 → 64.4%** |

Constructive(T): enumerate (g,s,b) where 2g+s=T, g+s+b=6. Pick one.
Build [g×GOLD, s×SILVER, b×BROKEN]. Fisher-Yates shuffle. Verify sum=T.

## Required Tests (every mechanic)
1. Deterministic with seeded RNG → exact layout
2. Round-trip: generate → evaluate → prize matches
3. False-positive: 10K losers → 0 winners
4. Monte Carlo RTP: 100K trials → ±1% of target
5. Monte Carlo distribution: each P within 2σ
