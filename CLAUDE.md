# LuckLedger — Gambling Awareness Scratch Card Simulator

## Project
Free-to-play scratch card simulator. Java 21 + Spring Boot 3.5.x + Maven.
Education-first. No monetization. No auth (yet).

## Tech
- Java 21, Spring Boot 3.5.x, Maven 3.9+
- Package root: com.luckledger
- Records for value objects/DTOs. Constructor injection only.
- BigDecimal for ALL money. UUID for public IDs. Instant for timestamps.
- Flyway migrations. Testcontainers for integration tests.
- JUnit 5 + AssertJ. No Lombok.

## Modules
luckledger-domain         Pure Java — NO Spring
luckledger-pool           PoolContract, PoolValidator, PoolFactory
luckledger-mechanic       GridPopulator, WinEvaluator, GameMechanic impls
luckledger-generation     Outcome, Shuffle, Pipeline, Verification, Theme
luckledger-distribution   BookPartitioner, DealerAllocator, DealerRegistry
luckledger-player         Player, BankService, Ledger, Insights
luckledger-scratch-flow   TicketPurchase, ScratchReveal
luckledger-api            26 REST endpoints
luckledger-cli            Generation CLI
luckledger-app            @SpringBootApplication + Flyway

## Architecture (violations are blocking)
1. Domain packages: ZERO Spring dependencies
2. Interfaces at subsystem boundaries
3. Constructive grid population only — no reject-and-retry
4. Verification mandatory — generation throws without it
5. Payout ratio sacred — nothing changes RTP
6. Ledger append-only
7. Purchase and reveal are separate operations

## Agents (.claude/agents/)
spring-implementer  opus, worktree — primary class builder
game-math-engineer  opus, worktree — probability/RNG/mechanics
api-developer       sonnet, worktree — REST controllers
spring-reviewer     opus, READ-ONLY — code review
db-engineer         opus, worktree — schema/JPA
test-writer         sonnet, worktree — test coverage
explorer            haiku, READ-ONLY — codebase orientation

## Skills
Custom (in .claude/skills/):          Fetched (via fetch-skills.sh):
  scratch-card-domain                   spring-boot-conventions (sivalabs)
  game-math                             jpa-patterns (everything-claude-code)
  security-audit (with scan.sh)         springboot-tdd (everything-claude-code)
                                        springboot-security (everything-claude-code)
                                        api-contract-review (claude-code-java)
                                        concurrency-review (claude-code-java)
                                        solid-principles (claude-code-java)
                                        dr-jskill (jdubois)

## Hooks
PreToolUse(Edit/Write)  Blocks field injection, System.out, double for money,
                        SQL concat, BigDecimal.equals()
PreToolUse(Bash)        Blocks flyway clean, DROP TABLE, rm -rf
Stop                    Blocks if mvn test fails

## Bead Conventions
One bead = one class = one session = /clear between beads.
Subagents use isolation: worktree. Tests blocked-by their class bead.

## When Compacting
Preserve: active bead ID, modified files, current module.


<!-- BEGIN BEADS INTEGRATION v:1 profile:minimal hash:7510c1e2 -->
## Beads Issue Tracker

This project uses **bd (beads)** for issue tracking. Run `bd prime` to see full workflow context and commands.

### Quick Reference

```bash
bd ready              # Find available work
bd show <id>          # View issue details
bd update <id> --claim  # Claim work
bd close <id>         # Complete work
```

### Rules

- Use `bd` for ALL task tracking — do NOT use TodoWrite, TaskCreate, or markdown TODO lists
- Run `bd prime` for detailed command reference and session close protocol
- Use `bd remember` for persistent knowledge — do NOT use MEMORY.md files

**Architecture in one line:** issues live in a local Dolt DB; sync uses `refs/dolt/data` on your git remote; `.beads/issues.jsonl` is a passive export. See https://github.com/gastownhall/beads/blob/main/docs/SYNC_CONCEPTS.md for details and anti-patterns.

## Session Completion

**When ending a work session**, you MUST complete ALL steps below. Work is NOT complete until `git push` succeeds.

**MANDATORY WORKFLOW:**

1. **File issues for remaining work** - Create issues for anything that needs follow-up
2. **Run quality gates** (if code changed) - Tests, linters, builds
3. **Update issue status** - Close finished work, update in-progress items
4. **PUSH TO REMOTE** - This is MANDATORY:
   ```bash
   git pull --rebase
   git push
   git status  # MUST show "up to date with origin"
   ```
5. **Clean up** - Clear stashes, prune remote branches
6. **Verify** - All changes committed AND pushed
7. **Hand off** - Provide context for next session

**CRITICAL RULES:**
- Work is NOT complete until `git push` succeeds
- NEVER stop before pushing - that leaves work stranded locally
- NEVER say "ready to push when you are" - YOU must push
- If push fails, resolve and retry until it succeeds
<!-- END BEADS INTEGRATION -->
