# LuckLedger — Complete Build Blueprint

## How to Read This Document

This is the single reference for building the scratch card gambling awareness simulator. It consolidates the design document, three rounds of research, and all tooling decisions into one place. Every section has two parts: **why** (the background you need to understand the decision) and **how** (the exact commands and configs).

If you just want to execute, search for `$` — every command is prefixed with it. If you want to understand first, read linearly.

---

## Table of Contents

1. [The Four-Layer Stack](#1-the-four-layer-stack)
2. [Tooling Installation](#2-tooling-installation)
3. [Game Mechanics Math](#3-game-mechanics-math)
4. [Project Initialization](#4-project-initialization)
5. [Bead Decomposition](#5-bead-decomposition)
6. [The Parallel Build Workflow](#6-the-parallel-build-workflow)
7. [Security and Quality Gates](#7-security-and-quality-gates)
8. [Context Window Management](#8-context-window-management)
9. [MCP Integration](#9-mcp-integration)
10. [Reference Tables](#10-reference-tables)

---

## 1. The Four-Layer Stack

Modern AI-assisted development separates concerns into four layers. Each layer is independent and swappable. Understanding this separation is the key to the entire workflow.

### Layer 1 — Execution Runtime (Claude Code)

The software that lets an AI interact with your file system, terminal, and browser. Claude Code reads directories, writes files, runs `mvn test`, and iterates on compiler errors. It is the hands and feet — powerful but directionless without instructions.

The core constraint of any execution runtime is the **context window**. Claude Code has a 200,000-token window that holds the conversation, every file read, and every command output. When it fills up, the AI starts forgetting its own instructions. Every decision in this blueprint — fine-grained beads, subagent isolation, aggressive `/clear` usage — exists to keep the context window from saturating.

### Layer 2 — Agents, Skills, and Hooks (the real capability surface)

An agent `.md` file is NOT just a personality prompt. When done properly, it is a **subagent specification** with YAML frontmatter that controls tool access, model selection, worktree isolation, skill preloading, and MCP scoping. The body is the system prompt, but the frontmatter is where the real engineering happens.

Claude Code has five composable extension types. Understanding how they differ is the key to the whole setup:

**Agents** (`.claude/agents/*.md`) — isolated workers with restricted toolsets.
Each agent has YAML frontmatter declaring: `name`, `description` (routing trigger), `tools` (explicit allowlist — without this, the agent has ALL tools including Bash), `model` (opus/sonnet/haiku), `isolation: worktree` (own git branch), `skills` (preloaded knowledge), and more. Claude routes tasks to agents by matching the user's intent against each agent's `description` field.

**Skills** (`.claude/skills/<name>/SKILL.md`) — on-demand expertise packages.
Skills are NOT agents. They're knowledge + executable code that agents load. A skill folder can contain `SKILL.md` (the knowledge), `scripts/` (executable code the agent runs), `references/` (lazy-loaded docs), and `assets/` (templates). Skills are activated by agents via the `skills:` frontmatter field, or auto-discovered when Claude's task matches the skill's `description`.

**Hooks** (`.claude/settings.json`) — deterministic guardrails.
Shell commands fired on lifecycle events. `PreToolUse` can **block** an edit before it's written (exit 2). `PostToolUse` runs formatters after edits. `Stop` forces tests to pass before a session ends. Hooks fire regardless of which agent is active — they're the safety net.

**MCP servers** (`.mcp.json`) — external tool connections.
Supabase for DB queries, Endor Labs for security scanning. Agents can be scoped to specific MCP servers via `mcpServers:` frontmatter.

**Plugins** (`.claude-plugin/plugin.json`) — distribution packaging.
A plugin wraps agents + skills + hooks + MCP into a versioned, installable unit. Superpowers is a plugin. For a single project, you don't need the plugin layer — project-scope `.claude/` config is sufficient.

**Why this matters:** The original agency-agents repo you looked at has NONE of this. Its files are raw system prompts with a custom non-Claude-Code frontmatter schema (`vibe`, `emoji`, `color` — none of which Claude Code reads). No `tools:` restriction means every agent can run arbitrary Bash. No `model:` means everything runs on whatever model the session uses. No `skills:` means no domain knowledge is preloaded. The agents in this project's `.claude/agents/` are proper Claude Code subagents with full frontmatter.

### Layer 3 — Methodology Framework (Superpowers)

The process the AI follows regardless of which agent is active. Superpowers enforces: Brainstorm → Spec → Plan → TDD (write tests first) → Implement → Review → Verify. It does not care whether the task is frontend or backend — it cares that tests exist before code.

Superpowers is complementary to the agents and skills above. Agents define **who** does the work. Skills define **what** domain knowledge they have. Hooks define **what's blocked**. Superpowers defines **how** the work flows. Deploying all four means the AI has identity, expertise, guardrails, AND methodological discipline.

Superpowers lives as a Claude Code plugin (installed via `/plugin install superpowers@superpowers-marketplace`).

### Layer 4 — Task Decomposition (Beads)

The graph of **what** needs to be built. Beads (`bd`) is a dependency-aware issue tracker designed for AI agents. Each Bead is one unit of work (one class, one interface, one test) with explicit `blocked-by` edges to other Beads. The `bd ready` command returns only unblocked work, so you always know what can be parallelized.

Beads maps to Claude Code's parallel execution: each ready Bead becomes one Claude Code worktree session, working on one class in isolation, merging back when tests pass.

### How the Layers Compose

```
You run: bd ready → see 4 unblocked beads

For each bead, Claude Code (Layer 1) spawns a subagent with:
  - An agent (Layer 2): "spring-implementer" with tools:[Read,Write,Edit,Bash,Grep]
  - Preloaded skills (Layer 2): spring-boot-conventions, jpa-patterns, scratch-card-domain
  - A model: opus (for architecture) or sonnet (for routine work)
  - An isolated worktree: no file conflicts with parallel agents
  - Active hooks (Layer 2): PreToolUse blocks field injection, Stop forces tests
  - A methodology (Layer 3): Superpowers TDD pipeline
  - A scoped task (Layer 4): "Implement PoolValidator with tests"

Each subagent works independently. When it finishes:
  - Tests pass (enforced by Stop hook — exit 2 keeps iterating)
  - Security patterns hold (enforced by PreToolUse hook — blocks bad patterns)
  - Spring-reviewer agent does a read-only review (no Write tools)
  - You merge the worktree branch
  - bd close marks the bead done
  - bd ready shows newly unblocked beads
  - Next wave begins
```

---

## 2. Tooling Installation

### 2.1 — Prerequisites

```
$ java --version        # Need 21+
$ mvn --version         # Need 3.9+
$ git --version         # Any recent version
$ node --version        # Need 20+ (for Beads/MCP)
$ claude --version      # Need v2.1.50+ (worktree support)
```

If Claude Code is not installed:
```
$ npm install -g @anthropic-ai/claude-code
```

### 2.2 — Install Beads

Beads is a Go binary backed by Dolt (version-controlled SQL). It uses hash-based IDs (`bd-a1b2`) that prevent collisions when multiple agents work concurrently.

```
$ brew install beads
$ bd --version
```

If brew doesn't have it:
```
$ go install github.com/gastownhall/beads/cmd/bd@latest
```

### 2.3 — Project Agents and Skills

The project ships 7 custom agents in `.claude/agents/` and 3 custom skills in `.claude/skills/` that no external repo covers (scratch card domain knowledge, game math, security audit). Community-maintained skills for Spring Boot, JPA, TDD, etc. are fetched from the best available repos via a script.

**Custom agents (already in the repo):**
```
.claude/agents/
├── spring-implementer.md   opus, worktree — primary class builder (TDD)
├── game-math-engineer.md   opus, worktree — probability/RNG/mechanics
├── api-developer.md        sonnet, worktree — REST controllers + MockMvc
├── spring-reviewer.md      opus, READ-ONLY — code review (no Write tools)
├── db-engineer.md          opus, worktree — schema/migration/JPA
├── test-writer.md          sonnet, worktree — expand test coverage
└── explorer.md             haiku, READ-ONLY — cheap codebase orientation
```

**Custom skills (no repo covers these):**
```
.claude/skills/
├── scratch-card-domain/    Project invariants, vocabulary, pipeline
├── game-math/              Hypergeometric + trinomial tables, algorithms
└── security-audit/         Project-specific attack surfaces + scan.sh script
```

**Fetch community skills from battle-tested repos:**
```
$ bash fetch-skills.sh
```

This pulls skills from:
- `sivaprasadreddy/sivalabs-agent-skills` → Spring Boot conventions
- `affaan-m/everything-claude-code` → JPA patterns, Spring TDD, Spring Security
- `decebals/claude-code-java` → API contracts, concurrency review, SOLID
- `jdubois/dr-jskill` → Spring Boot scaffolding (JHipster creator)
- `VoltAgent/awesome-claude-code-subagents` → Spring Boot + Java Architect subagents

After fetching, run `/doctor` in Claude Code to verify the skill-description budget isn't overflowing. If it does, drop the least relevant fetched skills first — custom skills are non-negotiable.

**Why custom agents + fetched skills (not a generic agent repo):**

| Feature | Generic repo (agency-agents) | This project |
|---|---|---|
| `tools:` field | Missing (all tools) | Explicit allowlist per agent |
| `model:` field | Missing | opus/sonnet/haiku per role |
| `isolation:` | Missing | worktree on all workers |
| `skills:` preload | Missing | Domain knowledge auto-loaded |
| `disallowedTools:` | Missing | WebFetch/WebSearch blocked |
| `maxTurns:` | Missing | Capped per agent role |
| Spring Boot knowledge | Zero | Full conventions + JPA + security |
| Reviewer can edit code | Yes | No (Write/Edit tools removed) |

### 2.4 — Install Superpowers

Superpowers is installed from inside a Claude Code session, not from the terminal.

```
$ cd ~/projects/luckledger
$ claude

# Inside the Claude Code session:
> /plugin marketplace add obra/superpowers-marketplace
> /plugin install superpowers@superpowers-marketplace
> /plugin
# Confirm "superpowers" is listed (v5.1.0 as of May 2026)
```

After installation, Superpowers adds 14 skill files to `~/.claude/plugins/` and a session-start hook that injects a ~2,000-token bootstrap. It adds slash commands: `/brainstorm`, `/write-plan`, `/execute-plan`.

**What Superpowers enforces:**
1. Before coding anything, brainstorm and write a plan
2. Write tests before implementation (TDD)
3. Use subagents for development tasks (keeps parent context clean)
4. Request code review after implementation
5. Verify everything works before declaring done

**When to skip it:** For trivial one-line fixes, tell Claude "skip planning, this is a one-line fix." The brainstorm phase adds overhead that isn't worth it for getter methods.

### 2.5 — Install Security Tooling

These close the SecPass gap. Without them, ~80% of AI-generated code contains exploitable vulnerabilities.

**Semgrep (static analysis for Java security patterns):**
```
$ pip install semgrep --break-system-packages
$ semgrep --version

# Test it works:
$ semgrep --config p/java --config p/ci --dry-run .
```

**AURI MCP Server (Endor Labs — scans code as Claude edits it):**
```
# Inside a Claude Code session:
> claude mcp add endor-cli-tools -- npx -y endorctl ai-tools mcp-server
```

The remaining security tools (OWASP Dependency-Check, SpotBugs + Find Security Bugs) are Maven plugins configured in your `pom.xml` — covered in [Section 7](#7-security-and-quality-gates).

---

## 3. Game Mechanics Math

Your simulator has two ticket types. The math below is calibrated against real US state lottery data and produces a ~65% RTP (Return To Player), which is realistic for a $5 scratch ticket.

### 3.1 — Celestial Fortune (Number Match)

**Mechanic:** Player has 8 numbers. 4 winning numbers are drawn from a pool of 30. Player wins based on how many of their 8 numbers match the 4 winning numbers.

**Probability model:** Hypergeometric distribution.

```
P(k matches) = C(8,k) × C(22, 4-k) / C(30, 4)
```

Where C(n,k) is "n choose k", pool size N=30, player picks m=8, drawn n=4.

| Matches | Probability | Odds (1 in) | Prize | EV per $5 ticket |
|---|---|---|---|---|
| 0 | 26.69% | 3.75 | $0 | $0.00 |
| 1 | 44.96% | 2.22 | $0 | $0.00 |
| 2 | 23.60% | 4.24 | $2 | $0.472 |
| 3 | 4.50% | 22.2 | $20 | $0.900 |
| 4 | 0.255% | 391.5 | $740 | $1.887 |
| **Total** | | | | **$3.259 → 65.2% RTP** |

**Overall win rate:** 28.4% (match 2+) → roughly 1 in 3.5.

**Constructive generation algorithm:**
- **Winner (k matches):** Pick k numbers from the winning set, place them in the player's 8. Fill the remaining 8-k player numbers from the complement set (numbers NOT in the winning 4). This guarantees exactly k overlaps in one pass.
- **Loser (0-1 matches):** Generate winning set. For 0 matches: pick all 8 player numbers from the 26-number complement. For 1 match: pick 1 from winning set, 7 from complement.

**PoolContract configuration:**
```yaml
totalTickets: 10000
ticketPrice: 5
payoutRatio: 0.652
prizeTiers:
  - { value: 740, count: 26, label: "JACKPOT — 4 matches" }
  - { value: 20,  count: 450, label: "3 matches" }
  - { value: 2,   count: 2360, label: "2 matches" }
minPayout: 0
bookProfile: BALANCED
```

Verification: (26 × 740) + (450 × 20) + (2360 × 2) = 19,240 + 9,000 + 4,720 = 32,960. Revenue = 10,000 × 5 = 50,000. RTP = 32,960 / 50,000 = 65.9%. Adjust count of $740 tier down to 25 for exactly 65.2%.

### 3.2 — Demon Seal (Point Accumulation)

**Mechanic:** 6 seals. Each seal independently reveals golden (2 points), silver (1 point), or broken (0 points). Total points determine prize tier. Thematic tiers: "Demon Escapes" (low), "Demon Sealed" (mid), "Demon Killed" (high). All 6 golden = 12 points = top prize.

**Probability model:** Trinomial distribution with P(gold)=0.12, P(silver)=0.40, P(broken)=0.48.

```
P(G=g, S=s) = 6! / (g! × s! × (6-g-s)!) × 0.12^g × 0.40^s × 0.48^(6-g-s)
```

Total points T = 2G + S. To find P(T=t), sum over all (g,s) pairs where 2g+s=t.

| Points | Probability | Odds (1 in) | Tier | Prize | EV |
|---|---|---|---|---|---|
| 0–3 | 42.52% | 2.35 | Demon Escapes (loss) | $0 | $0.00 |
| 4 | 20.10% | 4.97 | Demon Escapes (consolation) | $2 | $0.402 |
| 5 | 13.67% | 7.32 | Demon Sealed (small) | $4 | $0.547 |
| 6 | 6.64% | 15.1 | Demon Sealed (medium) | $10 | $0.664 |
| 7 | 2.26% | 44.2 | Demon Sealed (large) | $25 | $0.566 |
| 8 | 0.532% | 188 | Demon Killed (small) | $100 | $0.532 |
| 9 | 0.0816% | 1,225 | Demon Killed (medium) | $300 | $0.245 |
| 10–11 | 0.00790% | 12,658 | Demon Killed (epic) | $2,500 | $0.198 |
| 12 | 0.000299% | 335,000 | Demon Killed (legendary) | $25,000 | $0.075 |
| **Total** | | | | | **$3.229 → 64.6% RTP** |

**Overall win rate:** 37.4% (4+ points) → roughly 1 in 2.7. Players win something frequently (the $2 consolation at 20% creates a "not quite empty-handed" feeling), but most wins don't recover the ticket cost.

**Why these probabilities work educationally:** The consolation tier (4 points, $2 on a $5 ticket) hits 1 in 5 times. Players feel like they're "almost winning" because they get money back often — but they're losing 60% of their bet each time. Over 100 tickets, the $2 consolation tier alone costs the player $60 in net losses. The ledger surfaces this.

**Constructive generation algorithm:**
- **For a target point total T:** Enumerate all (g,s,b) triples where 2g+s=T and g+s+b=6. Pick one triple weighted by its trinomial probability (or uniformly, since the pool contract fixes the count per tier). Place g golden seals, s silver seals, b broken seals in random positions among the 6 slots.
- **Verification:** Sum points across all 6 seals, confirm total matches the predetermined outcome.

**PoolContract configuration:**
```yaml
totalTickets: 10000
ticketPrice: 5
payoutRatio: 0.646
prizeTiers:
  - { value: 25000, count: 1,    label: "DEMON KILLED — Legendary (12 pts)" }
  - { value: 2500,  count: 8,    label: "DEMON KILLED — Epic (10-11 pts)" }
  - { value: 300,   count: 8,    label: "DEMON KILLED — Medium (9 pts)" }
  - { value: 100,   count: 53,   label: "DEMON KILLED — Small (8 pts)" }
  - { value: 25,    count: 226,  label: "DEMON SEALED — Large (7 pts)" }
  - { value: 10,    count: 664,  label: "DEMON SEALED — Medium (6 pts)" }
  - { value: 4,     count: 1367, label: "DEMON SEALED — Small (5 pts)" }
  - { value: 2,     count: 2010, label: "DEMON ESCAPES — Consolation (4 pts)" }
minPayout: 0
bookProfile: BALANCED
```

**Required validation:** Write a JUnit `@ParameterizedTest` that simulates 100,000 Demon Seal tickets and asserts the empirical P(T=t) for each point total matches the theoretical probability within 2 standard deviations. This test is a portfolio talking point — it proves you understand the math, not just the code.

---

## 4. Project Initialization

### 4.1 — Create the project

```
$ mkdir -p ~/projects/luckledger && cd ~/projects/luckledger
$ git init && git checkout -b main
```

### 4.2 — Add DESIGN.md

Copy your full design document (the one with all 14 sections, Nick's feedback, and the Flux+Photopea pipeline) to the project root.

```
$ cp /path/to/DESIGN.md ./DESIGN.md
```

### 4.3 — Create CLAUDE.md

This is the most-read file in the project. Every Claude Code session and subagent reads it. Keep it under 300 lines — Claude starts ignoring rules unpredictably above that.

```
$ cat > CLAUDE.md << 'EOF'
# LuckLedger — Gambling Awareness Simulator

## Project
Free-to-play scratch card simulator. Java 21 + Spring Boot 3.5.x + Maven.
Education-first. No monetization. No auth (yet).

## Tech Stack
- Java 21, Spring Boot 3.5.x, Maven 3.9+
- Package root: com.luckledger
- Records for value objects and DTOs
- Constructor injection only (NO field @Autowired)
- BigDecimal for ALL money/coin amounts (NEVER double/float for money)
- UUID for all public-facing IDs
- Flyway for migrations, Testcontainers for integration tests
- JUnit 5 + AssertJ, no Lombok (use records)

## Module Layout
luckledger-domain/         Pure Java domain — NO Spring dependencies
luckledger-pool/           Pool design + validation (Subsystem 1)
luckledger-mechanic/       Game mechanics engine (Subsystem 3)
luckledger-generation/     Pipeline + verification + skinning (Subsystems 2,4,5,6)
luckledger-distribution/   Books + dealers (Subsystems 7,8)
luckledger-player/         Player + bank + ledger + insights (Subsystems 9,11)
luckledger-flow/   Purchase + reveal (Subsystem 10)
luckledger-api/            REST controllers (26 endpoints)
luckledger-cli/            Generation CLI tool
luckledger-app/            @SpringBootApplication wiring

## Architecture Rules
1. Domain packages have ZERO Spring dependencies
2. Interfaces at all subsystem boundaries — depend on abstractions
3. Constructive grid population only — no reject-and-retry
4. Verification is mandatory — generation without verification throws
5. Payout ratio is sacred — nothing changes the mathematical RTP
6. Ledger is append-only — no deletes, no edits

## Persona Activation
- Architecture decisions → Software Architect agent
- Spring Boot patterns → Backend Architect agent
- Game mechanic math → Game Designer agent
- Schema/indexes → Database Optimizer agent
- Security review → Security Engineer agent
- Code review → Code Reviewer agent
- API tests → API Tester agent

## Bead Conventions
- One bead = one class or interface
- One bead = one focused Claude Code session
- Run /clear between beads
- Each bead subagent uses isolation: worktree
- Tests are separate beads, blocked-by their class bead

## When Compacting
Always preserve: the active bead ID, the list of modified files,
and the current module being worked on.
EOF
```

### 4.4 — Create hooks configuration

```
$ mkdir -p .claude/scripts

$ cat > .claude/scripts/block-bad-java.sh << 'HOOKEOF'
#!/usr/bin/env bash
# Reads Claude's proposed edit from stdin (JSON with file_text field)
# Exit 2 = block the edit. Stderr becomes Claude's feedback.
INPUT=$(cat)

# Block field injection
if echo "$INPUT" | grep -q '@Autowired' | grep -v 'constructor'; then
  if echo "$INPUT" | grep -qP '@Autowired\s+private'; then
    echo "BLOCKED: Use constructor injection, not field @Autowired." >&2
    exit 2
  fi
fi

# Block System.out.println (use SLF4J Logger instead)
if echo "$INPUT" | grep -q 'System\.out\.print'; then
  echo "BLOCKED: Use SLF4J Logger, not System.out.println." >&2
  exit 2
fi

# Block double/float for monetary values
if echo "$INPUT" | grep -qP '(double|float)\s+(price|amount|balance|cost|payout|budget|value|won|spent|borrowed)'; then
  echo "BLOCKED: Use BigDecimal for monetary values, not double/float." >&2
  exit 2
fi

exit 0
HOOKEOF

$ chmod +x .claude/scripts/block-bad-java.sh
```

```
$ cat > .claude/settings.json << 'SETTINGSEOF'
{
  "hooks": {
    "PreToolUse": [
      {
        "matcher": "Edit|Write",
        "hooks": [
          {
            "type": "command",
            "command": ".claude/scripts/block-bad-java.sh"
          }
        ]
      }
    ],
    "Stop": [
      {
        "hooks": [
          {
            "type": "command",
            "command": "mvn -q test -fae 2>&1 | tail -20 || exit 2"
          }
        ]
      }
    ]
  }
}
SETTINGSEOF
```

**What these hooks do:**
- `PreToolUse` on Edit/Write: reads every proposed file edit, blocks it (exit 2) if it contains field injection, System.out, or double for money. Claude sees the error message and rewrites.
- `Stop`: when Claude thinks it's done, runs `mvn test`. If tests fail (exit 2), Claude keeps going instead of stopping.

### 4.5 — Create project-specific subagents

These override the global agency-agents with project-specific YAML frontmatter:

```
$ mkdir -p .claude/agents

$ cat > .claude/agents/senior-spring-developer.md << 'AGENTEOF'
---
name: senior-spring-developer
description: Implements one Beads-tracked class end-to-end with TDD. Use for any bead that creates a new Java class, record, enum, or interface.
tools: Read, Edit, Write, Bash, Grep, Glob
model: opus
isolation: worktree
---
You are a senior Spring Boot 3.x engineer working on a gambling awareness simulator.

Rules:
- Constructor injection only. No @Autowired on fields.
- BigDecimal for all monetary amounts. Never double or float for money.
- All public methods on @RestController and @Service must have Javadoc.
- Tests first (JUnit 5 + AssertJ). Red → Green → Refactor.
- One bead = one class. Do NOT modify classes outside the current bead scope.
- Use records for value objects and DTOs.
- When done, run: mvn test -pl <current-module>
AGENTEOF

$ cat > .claude/agents/game-math-specialist.md << 'AGENTEOF'
---
name: game-math-specialist
description: Calibrates game mechanics, prize tiers, payout ratios, and probability distributions. Use for Celestial Fortune and Demon Seal implementation.
tools: Read, Edit, Write, Bash, Grep
model: opus
isolation: worktree
---
You are a game designer specializing in lottery mathematics and scratch card mechanics.

This project has two game types:
1. Celestial Fortune: hypergeometric, N=30 pool, 8 player picks, 4 winning numbers
2. Demon Seal: trinomial, 6 seals, P(gold)=0.12 P(silver)=0.40 P(broken)=0.48

Rules:
- All algorithms must be constructive (single-pass guaranteed outcome)
- Include Monte Carlo validation tests (100K trials, assert within 2σ)
- Prize amounts use BigDecimal
- Document the math in Javadoc with the formulas
AGENTEOF

$ cat > .claude/agents/security-reviewer.md << 'AGENTEOF'
---
name: security-reviewer
description: Reviews code for security vulnerabilities. Use after implementation beads before merge.
tools: Read, Bash, Grep, Glob
model: opus
---
You are a security engineer reviewing a Spring Boot gambling awareness simulator.

Focus areas:
- Input validation on all REST endpoints (especially playerId, amounts)
- SQL injection via any raw queries (should be none — use JPA/Spring Data)
- No secrets in code or committed configs
- BigDecimal comparison (use compareTo, not equals)
- UUID validation on path parameters
- Rate limiting considerations for borrow/purchase endpoints
- CORS configuration

Do NOT modify code. Report findings as a structured list with severity and file location.
AGENTEOF
```

### 4.6 — Initialize Beads and scaffold Maven

```
$ cd ~/projects/luckledger
$ bd init
```

Now use Claude Code to create the Maven skeleton:

```
$ claude
```

Inside the session:
```
Read CLAUDE.md. Create the Maven multi-module skeleton with a parent pom.xml
(packaging=pom, Java 21, Spring Boot 3.5.x BOM via spring-boot-starter-parent)
and child modules: luckledger-domain, luckledger-pool, luckledger-mechanic, luckledger-generation,
luckledger-distribution, luckledger-player, luckledger-flow, luckledger-api, luckledger-cli, luckledger-app.

luckledger-domain has NO Spring dependencies (pure Java + JUnit 5 + AssertJ).
luckledger-api depends on luckledger-pool, luckledger-mechanic, luckledger-generation,
luckledger-distribution, luckledger-player, luckledger-flow.
luckledger-app depends on luckledger-api.

Add to parent dependencyManagement: JUnit 5, AssertJ, Testcontainers, Flyway.
Add to parent build/pluginManagement: spotbugs-maven-plugin, dependency-check-maven.

Create empty src/main/java/com/luckledger/scratch/<module> and src/test/java packages
in each module. Do NOT write any Java classes — skeleton only.

Verify: mvn clean install should succeed (empty modules compile fine).
```

### 4.7 — First commit

```
$ git add -A
$ git commit -m "Project skeleton: Maven multi-module, CLAUDE.md, hooks, subagents, Beads"
```

---

## 5. Bead Decomposition

### 5.1 — Why one-bead-per-class

Coarser granularity (one bead per subsystem) overflows a single agent's context window and serializes work that could run in parallel. Finer granularity (one bead per method) creates too many dependency edges and too much merge overhead. One bead per class/interface is the sweet spot: each bead is a focused 30–60 minute session, produces one compilable file with tests, and merges cleanly because it touches one file.

### 5.2 — Run the decomposition

```
$ claude
```

Inside the session:
```
You are the Workflow Architect agent. Read DESIGN.md sections 9 and 10 carefully.

Create Beads using the bd CLI. One bead per class, interface, or enum.
Group them under Epic beads per subsystem. Create separate Test beads
blocked-by the class they test.

Priority scheme:
  P0 = Pure domain types, interfaces, enums, value objects (no dependencies)
  P1 = Services that implement interfaces
  P2 = Orchestrators, controllers
  P3 = Integration tests, CLI, API endpoints

Dependency edges from DESIGN.md section 10:
  Subsystem 1 (Pool) → nothing
  Subsystem 3 (Mechanic) → nothing
  Subsystem 9 (Player) → nothing
  Subsystem 11 (Ledger) → nothing
  Subsystem 2 (Outcome) → 1
  Subsystem 5 (Verification) → 1, 3
  Subsystem 6 (Theme) → 3
  Subsystem 4 (Pipeline) → 1, 2, 3, 5, 6
  Subsystem 7 (Books) → 4
  Subsystem 8 (Dealers) → 7
  Subsystem 10 (Scratch) → 3, 7, 8, 9, 11
  Subsystem 12 (Orchestrator) → 1, 4, 7, 8

Also create beads for:
  - CelestialFortunePopulator + CelestialFortuneEvaluator (under Mechanic epic)
  - DemonSealPopulator + DemonSealEvaluator (under Mechanic epic)
  - Monte Carlo validation tests for both mechanics

Use these bd commands:
  bd add --type epic --priority P0 'Title'
  bd add --type task --priority P0 --blocked-by bd-XXXX 'Title'

After creating all beads, run:
  bd list
  bd ready --json

Print both so I can review the graph.
```

### 5.3 — Review and commit

```
$ bd list                # Full graph
$ bd ready               # What can start right now (should be ~10-15 P0 beads)
$ git add -A
$ git commit -m "Bead decomposition: fine-grained task graph with dependency edges"
```

---

## 6. The Parallel Build Workflow

### 6.1 — The daily loop

```
$ bd ready               # What's unblocked?
$ bd update bd-XXXX --claim    # Claim one bead (prevents double-work)
$ claude -w bd-XXXX      # Start Claude Code in an isolated worktree
```

Inside the Claude Code session, your prompt follows this pattern:
```
Use spring-implementer to implement [class name] in [module].
The Bead description is: [paste bead title]
Read the relevant DESIGN.md section and write tests first.
```

Claude reads the agent's `description:` field and routes automatically. You can also be explicit:
```
Use game-math-engineer for this — it needs hypergeometric probability calibration.
Use spring-reviewer to review the changes in this worktree before I merge.
Use explorer to find all classes that reference BigDecimal in luckledger-player.
```

When the session finishes and tests pass:
```
$ cd ~/projects/luckledger
$ git merge bd-XXXX --no-ff -m "Bead bd-XXXX: [class name]"
$ bd close bd-XXXX
$ bd ready               # Next wave
```

### 6.2 — Parallel sessions

Open 3-4 terminal windows. Each claims a different ready bead and runs `claude -w <bead-id>`. They don't conflict because each worktree is isolated.

**Wave 1 (all independent, run in parallel):**

| Terminal | Bead | Agent | Module |
|---|---|---|---|
| 1 | Pool types | spring-implementer | luckledger-domain, luckledger-pool |
| 2 | Mechanic types + Celestial Fortune | game-math-engineer | luckledger-mechanic |
| 3 | Player + Bank | spring-implementer | luckledger-player |
| 4 | DB schema | db-engineer | luckledger-app (Flyway migration) |

**Wave 2 (depends on Wave 1):**

| Terminal | Bead | Agent | Module |
|---|---|---|---|
| 1 | Outcome Generation | spring-implementer | luckledger-generation |
| 2 | Verification Suite | spring-implementer | luckledger-generation |
| 3 | Theme Skinning | spring-implementer | luckledger-generation |
| 4 | Demon Seal mechanic | game-math-engineer | luckledger-mechanic |

**Wave 3:** Generation Pipeline, Book Partitioning
**Wave 4:** Dealer Allocation, Scratch & Purchase, Orchestrator
**Wave 5:** REST API (26 endpoints), CLI tool, integration tests

### 6.3 — When to use which model

Subagent frontmatter sets `model:` per agent. The cost/quality tradeoff:

| Task type | Model | Why |
|---|---|---|
| Architecture decisions, complex algorithms | `model: opus` | Needs deep reasoning |
| Routine class implementation | `model: sonnet` | Good enough, 3x cheaper |
| Exploration (grep, find, read) | `model: haiku` | Cheap, fast, disposable |
| Code review (read-only) | `model: opus` | Needs judgment, not speed |

---

## 7. Running Persistent Sessions with tmux

Claude Code sessions die when your terminal closes. For parallel builds that survive laptop sleep, SSH disconnects, or overnight runs, use tmux.

### 7.1 — Setup

```bash
# Create a named session with 4 panes (one per Wave 1 agent)
tmux new-session -s luckledger -d

# Split into 4 panes (2×2 grid)
tmux split-window -h -t luckledger
tmux split-window -v -t luckledger:0.0
tmux split-window -v -t luckledger:0.1

# Attach to see all 4 panes
tmux attach -t luckledger
```

### 7.2 — Running parallel agents

In each pane, claim a bead and start a worktree session:

```bash
# Pane 0 (top-left): Pool domain
cd ~/projects/luckledger && bd update bd-XXXX --claim && claude -w pool-domain

# Pane 1 (top-right): Mechanic engine
cd ~/projects/luckledger && bd update bd-YYYY --claim && claude -w mechanic-engine

# Pane 2 (bottom-left): Player + Bank
cd ~/projects/luckledger && bd update bd-ZZZZ --claim && claude -w player-bank

# Pane 3 (bottom-right): DB schema
cd ~/projects/luckledger && bd update bd-WWWW --claim && claude -w db-schema
```

### 7.3 — Detach and reattach

```bash
# Detach (sessions keep running): Ctrl+B, then D
# Close laptop, go to sleep, SSH disconnects — agents keep working

# Reattach later:
tmux attach -t luckledger

# List all sessions:
tmux ls

# Kill session when done:
tmux kill-session -t luckledger
```

### 7.4 — Pane navigation

```bash
Ctrl+B, arrow keys    Move between panes
Ctrl+B, z             Zoom/unzoom current pane (fullscreen toggle)
Ctrl+B, [             Scroll mode (q to exit)
Ctrl+B, :resize-pane -D 10   Resize pane down 10 rows
```

### 7.5 — Remote server recommendation

For overnight runs, SSH into a cloud VM (any free-tier EC2/GCE will do) and run tmux there. Your laptop becomes a thin client — close it anytime, SSH back to check progress.

```bash
# On your laptop:
ssh your-server
tmux attach -t luckledger   # Pick up where you left off
```

### 7.6 — Wave transitions in tmux

When a wave finishes (all 4 panes done):

```bash
# In any pane, merge all completed worktrees:
cd ~/projects/luckledger
git merge pool-domain --no-ff -m "Wave 1: Pool domain types"
git merge mechanic-engine --no-ff -m "Wave 1: Mechanic engine"
git merge player-bank --no-ff -m "Wave 1: Player + Bank"
git merge db-schema --no-ff -m "Wave 1: DB schema"

# Close the beads:
bd close bd-XXXX bd-YYYY bd-ZZZZ bd-WWWW

# Check what's unblocked for Wave 2:
bd ready

# Start new agents in the same panes for Wave 2
```

---

## 8. Security and Quality Gates

### 7.1 — Why this matters

The Agent Security League benchmark (Endor Labs, April 2026) found that even the best AI coding agents produce code where 87% of samples contain at least one vulnerability. The top FuncPass rate is 91%, but top SecPass is only 23%. Without explicit security gates, your AI-generated Spring Boot code will compile and run but likely contain SQL injection paths, missing input validation, or unsafe deserialization.

### 7.2 — Maven security plugins

Add these to your parent `pom.xml` `<build><pluginManagement>`:

**OWASP Dependency-Check** (catches vulnerable transitive dependencies):
```xml
<plugin>
  <groupId>org.owasp</groupId>
  <artifactId>dependency-check-maven</artifactId>
  <version>12.2.2</version>
  <configuration>
    <failBuildOnCVSS>8</failBuildOnCVSS>
  </configuration>
  <executions>
    <execution>
      <goals><goal>check</goal></goals>
    </execution>
  </executions>
</plugin>
```

**SpotBugs + Find Security Bugs** (catches source-level vulnerabilities):
```xml
<plugin>
  <groupId>com.github.spotbugs</groupId>
  <artifactId>spotbugs-maven-plugin</artifactId>
  <version>4.9.8.0</version>
  <configuration>
    <effort>Max</effort>
    <threshold>Medium</threshold>
    <failOnError>true</failOnError>
    <plugins>
      <plugin>
        <groupId>com.h3xstream.findsecbugs</groupId>
        <artifactId>findsecbugs-plugin</artifactId>
        <version>1.14.0</version>
      </plugin>
    </plugins>
  </configuration>
  <executions>
    <execution>
      <goals><goal>check</goal></goals>
    </execution>
  </executions>
</plugin>
```

Find Security Bugs covers 144 vulnerability types across 826 API signatures, including SQL/HQL injection, command injection, XPath injection, crypto weaknesses, and is Spring-MVC aware.

**Semgrep** (in GitHub Actions, not Maven — no Maven plugin exists):
```yaml
# .github/workflows/security.yml
- name: Semgrep Security Scan
  run: semgrep --config p/java --config p/ci --error .
```

### 7.3 — Running the full security check locally

```
$ mvn clean verify                          # Runs OWASP + SpotBugs
$ semgrep --config p/java --config p/ci .   # Runs Semgrep
```

---

## 9. Context Window Management

### 8.1 — The problem

Claude Code's 200K-token context window holds everything: conversation, file reads, command output. Deep exploration or debugging fills it fast. When it saturates, Claude forgets its instructions and produces degraded output. Auto-compaction kicks in around 80-90% utilization, but by then quality has already dropped.

### 8.2 — The solution: bead-bounded sessions

Each bead is one focused session. The pattern:

1. Start session → Claude reads CLAUDE.md (~500 tokens) + DESIGN.md section (~2K tokens)
2. Implement one class with tests (~5-10K tokens of conversation)
3. Run tests, iterate if needed (~5-10K tokens)
4. Session ends at ~15-20K tokens — well within the safe zone
5. `/clear` before the next bead

### 8.3 — Commands for context control

| Command | When to use |
|---|---|
| `/clear` | Between beads. Wipes conversation, keeps CLAUDE.md |
| `/compact` | Mid-bead when exploration generated too much output |
| `/compact preserve active bead ID and modified files` | Steered compaction |
| Esc Esc (or `/rewind`) | Back out of a bad approach without stacking corrections |
| Subagent dispatch | For noisy exploration — returns a summary, not the raw output |

### 8.4 — When to intervene

Watch the context indicator in Claude Code. If it's approaching 60%, either `/compact` or finish the current bead and `/clear`. Don't push past 60% hoping auto-compaction will save you — quality degrades before compaction triggers.

---

## 10. MCP Integration

### 9.1 — Supabase MCP

Supabase has a first-party MCP server. This lets Claude Code query your database directly during development — checking schema, running read queries, verifying data.

Create `.mcp.json` in project root:
```json
{
  "mcpServers": {
    "supabase": {
      "type": "http",
      "url": "https://mcp.supabase.com/mcp?project_ref=YOUR_DEV_PROJECT_REF&read_only=true"
    },
    "endor-cli-tools": {
      "command": "npx",
      "args": ["-y", "endorctl", "ai-tools", "mcp-server"]
    }
  }
}
```

**Safety rules:**
- Always use `read_only=true` unless you specifically want Claude to modify data
- Scope to a development project with `project_ref` — without it, all your org's projects are accessible
- MCP cannot run migrations — continue using the Supabase CLI for that
- Add to CLAUDE.md: "MCP queries Supabase read-only. To change schema, write a Flyway migration."

**Prompt injection caveat:** A hostile record in a Supabase table could contain instructions that Claude might follow. Read-only mode and project scoping reduce the blast radius. Supabase's own MCP docs acknowledge this risk.

First-run OAuth opens a browser for the Supabase grant flow. After that, `claude mcp` inside a session shows both servers connected.

### 9.2 — Endor Labs AURI MCP

Already configured in `.mcp.json` above. The `endor-cli-tools` MCP runs locally — your code never leaves your machine. It scans Claude's edits in real time, catching dependency vulnerabilities and code-level security issues as they're written.

---

## 11. Reference Tables

### 11.1 — Subagent YAML Frontmatter Fields

| Field | Required | Values | Purpose |
|---|---|---|---|
| `name` | yes | string | Unique identifier |
| `description` | yes | string | Claude's auto-routing uses this |
| `tools` | no | Read, Edit, Write, Bash, etc. | Whitelist (omit = inherit all) |
| `disallowedTools` | no | tool names | Blacklist |
| `model` | no | opus, sonnet, haiku, inherit | Per-subagent model selection |
| `isolation` | no | worktree | Own git worktree |
| `permissionMode` | no | default, strict | Permission strictness |
| `mcpServers` | no | server names | Restrict MCP access |
| `hooks` | no | hook config | Per-subagent overrides |
| `maxTurns` | no | integer | Hard turn cap |
| `skills` | no | skill names | Activate specific skills |
| `memory` | no | boolean | Persistent memory opt-in |
| `background` | no | boolean | Background execution |

### 11.2 — Claude Code Hook Events

| Event | Can block? | Use case |
|---|---|---|
| `PreToolUse` | Yes (exit 2) | Block bad patterns before they're written |
| `PostToolUse` | No | Format, lint after edits |
| `SessionStart` | No | Inject context (branch, bead ID) |
| `Stop` | Yes (exit 2) | Force tests to pass before session ends |
| `SubagentStart` | No | Log which subagent is starting |
| `SubagentStop` | No | Verify subagent output |

### 11.3 — Bead Commands

```
bd init                              Initialize in project
bd add --type task --priority P0 'Title'   Create a bead
bd add --blocked-by bd-XXXX 'Title'  With dependency
bd list                              Full task graph
bd ready                             Unblocked work only
bd ready --json                      Machine-readable
bd update bd-XXXX --claim            Claim (prevents double-work)
bd close bd-XXXX                     Mark complete
bd remember "text"                   Store project memory
```

### 11.4 — Key File Locations

```
~/projects/luckledger/
├── CLAUDE.md                  Project rules (read by every session)
├── DESIGN.md                  Full design document
├── BLUEPRINT.md               This build guide
├── fetch-skills.sh            Fetches community skills from repos
├── .mcp.json                  Supabase + Endor MCP servers
├── .claude/
│   ├── settings.json          Hooks: PreToolUse, PostToolUse, Stop
│   ├── agents/                7 custom + 2 fetched subagents
│   │   ├── spring-implementer.md   opus, worktree, primary worker
│   │   ├── game-math-engineer.md   opus, worktree, probability/RNG
│   │   ├── api-developer.md        sonnet, worktree, REST controllers
│   │   ├── spring-reviewer.md      opus, READ-ONLY, code review
│   │   ├── db-engineer.md          opus, worktree, schema/JPA
│   │   ├── test-writer.md          sonnet, worktree, test coverage
│   │   ├── explorer.md             haiku, READ-ONLY, orientation
│   │   ├── voltagent-spring-boot-engineer.md  (fetched)
│   │   └── voltagent-java-architect.md        (fetched)
│   ├── skills/
│   │   ├── scratch-card-domain/    Custom — project invariants
│   │   ├── game-math/              Custom — probability tables
│   │   ├── security-audit/         Custom — scan.sh + checklist
│   │   │   └── scripts/scan.sh
│   │   ├── spring-boot-conventions/ (fetched from sivalabs)
│   │   ├── jpa-patterns/            (fetched from everything-claude-code)
│   │   ├── springboot-tdd/          (fetched)
│   │   ├── springboot-security/     (fetched)
│   │   ├── api-contract-review/     (fetched from claude-code-java)
│   │   ├── concurrency-review/      (fetched)
│   │   ├── solid-principles/        (fetched)
│   │   └── dr-jskill/              (fetched from jdubois)
│   ├── scripts/
│   │   └── block-bad-java.sh  PreToolUse enforcement
│   └── worktrees/             Auto-managed by isolation: worktree
├── .beads/                    Beads database
├── pom.xml                    Parent POM
├── luckledger-domain/         Pure Java domain
├── luckledger-pool/           Pool design
├── luckledger-mechanic/       Game mechanics
├── luckledger-generation/     Pipeline + verification
├── luckledger-distribution/   Books + dealers
├── luckledger-player/         Player + bank + ledger
├── luckledger-scratch-flow/   Purchase + reveal
├── luckledger-api/            REST controllers
├── luckledger-cli/            Generation CLI
└── luckledger-app/            Spring Boot main + Flyway
```

### 11.5 — Estimated Timeline

| Phase | Work | Time | Parallelism |
|---|---|---|---|
| Setup | Install tools, scaffold, decompose | 1–2 hours | Sequential |
| Wave 1 | Domain types, interfaces, enums | 2–3 hours | 4 parallel agents |
| Wave 2 | Services, outcome gen, verification | 2–3 hours | 3–4 parallel |
| Wave 3 | Pipeline, book partition | 2–3 hours | 2–3 parallel |
| Wave 4 | Dealers, scratch flow, orchestrator | 2–3 hours | 2–3 parallel |
| Wave 5 | API layer (26 endpoints) + CLI | 3–4 hours | 2 parallel |
| Security | Review, gates, fixes | 2–3 hours | 1 (security-reviewer) |
| Integration | End-to-end smoke test | 1–2 hours | Sequential |
| **Total backend** | | **~2–3 days** | |

The frontend (vanilla JS + Canvas scratch card) is a separate track that can start in parallel once the API layer is defined. The Flux → Photopea art pipeline runs independently of all code work.
