#!/usr/bin/env bash
set -euo pipefail
cd ~/projects/LuckLedger

echo "=== Step 5: Creating Staff Engineer agent ==="

cat > .claude/agents/staff-engineer.md << 'AGENTEOF'
---
name: staff-engineer
description: "Top-level orchestrator with full project ownership. Writes plans, dispatches subagents to worktrees, reviews output, maintains cross-session coherence. Never writes implementation code directly."
tools:
  - Read
  - Write
  - Edit
  - Bash
  - Grep
  - Glob
  - Agent(spring-implementer)
  - Agent(game-math-engineer)
  - Agent(api-developer)
  - Agent(db-engineer)
  - Agent(test-writer)
  - Agent(spring-reviewer)
  - Agent(explorer)
model: opus
effort: high
maxTurns: 200
skills:
  - scratch-card-domain
---

You are a staff engineer with full ownership of LuckLedger. You do NOT write implementation code yourself. You plan, delegate, review, and maintain coherence.

## Your Workflow

### 1. Plan Before Implementing
For any task, first create a plan under plans/:

plans/<plan-name>/
  PLAN.md              — overview, sequence, dependencies, success criteria
  slots/               — mirrors intended code structure
    <module>/<path>/<ClassName>.md   — spec per file to create/modify
  specs/               — cross-cutting concerns
    no-touch-list.md   — files that must NOT be modified
    testing.md         — what tests are needed
    integration.md     — how pieces connect

Each slot file describes WHAT to implement (inputs, outputs, invariants, edge cases), not HOW (no code snippets unless they are interface contracts). The subagent reads the slot and implements it.

### 2. Dispatch Subagents With Worktree Isolation
One subagent per worktree. Clear file ownership — never two agents touching the same file.

Route to the right specialist:
- Mechanic/probability/RNG beads -> Agent(game-math-engineer)
- REST controllers/DTOs -> Agent(api-developer)
- Schema/migration/JPA -> Agent(db-engineer)
- Test expansion -> Agent(test-writer)
- Everything else -> Agent(spring-implementer)

Dispatch pattern:
Agent(spring-implementer): "Read plans/<name>/slots/<path>/<File>.md and implement exactly what it describes. Read CLAUDE.md for project rules. Tests first (TDD). When done: git add -A && git commit -m '<description>' && git push -u origin HEAD."

### 3. Review Before Merging
After each subagent finishes:
Agent(spring-reviewer): "Review the changes in worktree <name> against the spec in plans/<name>/slots/<path>/<File>.md. Check architecture rules, BigDecimal for money, constructor injection, test coverage."

Only merge to main after reviewer approves.

### 4. Maintain Project Memory
After each task:
- Update HANDOFF.md with current state, what is done, what is next
- Create plans/<completed>/RETROSPECTIVE.md — what worked, what broke
- Use bd remember for cross-session insights

After each session:
- HANDOFF.md must be current
- All work committed and pushed
- No uncommitted changes

## Rules (Violations Are Blocking)
1. Never write Java/JS/CSS/HTML yourself — always delegate to a subagent
2. Always create a plan before dispatching any subagent
3. One subagent per worktree, one worktree per task, clear file ownership
4. Review every subagent output via spring-reviewer before merging
5. Push after every successful review
6. Update HANDOFF.md at session end — this is non-negotiable
7. Never modify files listed in specs/no-touch-list.md
8. Subagents do NOT have access to CLAUDE.md — put all rules they need in the slot spec
9. If a subagent output fails review, send specific feedback and re-dispatch — do not fix it yourself

## Project Context
- Package root: com.luckledger
- 10 Maven modules (see CLAUDE.md)
- Read HANDOFF.md for current state
- Read DESIGN.md for full spec
- BigDecimal for ALL money. Constructor injection only. Records for VOs.
- 6 invariants in scratch-card-domain skill — memorize them

## Git Workflow
- Main branch: main (always green, always pushed)
- Subagent branches: worktree-<task-name>
- Merge with: git merge origin/worktree-<name> --no-ff -m "description"
- Never force push main
- Auto-push hook fires on Stop if tests pass
AGENTEOF

echo "  staff-engineer.md created"

echo "=== Step 6: Creating fix-scratch-card plan ==="

mkdir -p plans/fix-scratch-card/slots/js
mkdir -p plans/fix-scratch-card/slots/css
mkdir -p plans/fix-scratch-card/specs

cat > plans/fix-scratch-card/PLAN.md << 'PLANEOF'
# Plan: Fix Scratch Card Architecture

## Problem
The scratch card currently draws metallic rectangles/circles as a coating over the ticket. Scratching reveals numbers on opaque circles/rectangles. This is wrong.

## Correct Architecture
The ticket PNG IS the scratch coating. The crystals/seals in the PNG are what the player scratches off. Underneath is a dark background with revealed numbers/values.

  Bottom: <div class="reveal-layer">
            dark background
            positioned number/value labels from API reveal data
          </div>

  Top:    <canvas id="scratch-surface">
            ticket PNG drawn onto canvas at init
            destination-out erases PNG pixels on pointer move
          </canvas>

## Sequence
1. Fix scratch.js — scratch engine (slot: slots/js/scratch.js.md)
2. Fix app.js scratch section only (slot: slots/js/app-scratch-section.md)
3. Fix CSS scratch styles only (slot: slots/css/scratch-styles.md)
4. Test manually — verify PNG loads, scratch erases, numbers appear
5. Review — spring-reviewer checks no other pages broken

## Success Criteria
- Ticket PNG covers the full scratch card area
- Scratching erases the PNG pixels (crystals/seals disappear)
- Numbers/values appear underneath on dark background
- 70% scratch threshold triggers full reveal + result banner
- Dealer page, book page, ledger page are UNCHANGED
- Backend code is UNCHANGED
- Zone positions from scratch-zones.json are preserved

## Files To Modify
- luckledger-app/src/main/resources/static/js/scratch.js
- luckledger-app/src/main/resources/static/js/app.js (scratch section ONLY)
- luckledger-app/src/main/resources/static/css/style.css (scratch section ONLY)

## Dependencies
- Ticket PNGs: check actual paths in static/assets/
- Zone config: config/scratch-zones.json (if exists)
- API: POST /api/tickets/{id}/reveal returns grid data with numbers/seals
PLANEOF

cat > plans/fix-scratch-card/slots/js/scratch.js.md << 'SLOTEOF'
# Slot: scratch.js — Scratch Engine

## Purpose
Provides the scratch-to-reveal interaction for ticket cards.

## Architecture
The ticket PNG IS the scratch surface. No metallic overlay. No rectangles. No circles. The crystals and seals in the PNG are the coating.

## Init Flow
1. Receive ticket data (mechanic type, PNG path, zone positions)
2. Create canvas matching ticket container dimensions
3. Load ticket PNG as Image()
4. On image load: ctx.drawImage(img, 0, 0, canvas.width, canvas.height)
5. The drawn PNG IS the coating — nothing else on top

## Scratch Interaction
On pointerdown:
  lastX = event.offsetX
  lastY = event.offsetY
  isScratching = true

On pointermove (if isScratching):
  ctx.globalCompositeOperation = 'destination-out'
  ctx.lineWidth = 50
  ctx.lineCap = 'round'
  ctx.lineJoin = 'round'
  ctx.beginPath()
  ctx.moveTo(lastX, lastY)
  ctx.lineTo(event.offsetX, event.offsetY)
  ctx.stroke()
  lastX = event.offsetX
  lastY = event.offsetY

On pointerup / pointerleave:
  isScratching = false

## Threshold Detection
- Every 10 pointer events, check scratch percentage
- Use getImageData on full canvas
- Count pixels with alpha < 128 / total pixels
- At 70% transparent:
  ctx.clearRect(0, 0, w, h) — reveal everything
  Call the reveal callback (triggers API call + result display)

## Zone Boundaries (OPTIONAL — only if scratch-zones.json exists)
- Read zones from config/scratch-zones.json
- Each zone has: id, x%, y%, size%, shape
- Clip scratch operations to zone boundaries
- Track per-zone scratch percentage independently

## What You Must NOT Change
- Zone positions in scratch-zones.json
- PNG file paths or assets
- API reveal call logic (that is in app.js)
- Dealer page, book page, or ledger page code
- Any backend Java code

## Exports
function initScratch(canvasElement, pngPath, onReveal) { ... }
function resetScratch() { ... }
SLOTEOF

cat > plans/fix-scratch-card/slots/js/app-scratch-section.md << 'SLOTEOF'
# Slot: app.js — Scratch Section Integration

## Purpose
Wire the scratch engine into the app scratch page.

## What To Change
ONLY the section of app.js that handles the #scratch hash route.

## Flow
1. When navigating to #scratch with a purchased ticket:
   - Determine mechanic type from ticket data
   - Pick PNG path based on mechanic type:
     CELESTIAL_FORTUNE -> check actual path in static/assets/
     DEMON_SEAL -> check actual path in static/assets/
   - Create reveal layer div with positioned number/value labels
   - Call initScratch(canvas, pngPath, onRevealCallback)
2. onRevealCallback:
   - POST /api/tickets/{id}/reveal
   - Display result banner (win/loss amount)
   - Update balance display

## What You Must NOT Change
- Dealer page rendering
- Book page rendering
- Ledger page rendering
- API client functions
- Navigation/routing logic
- Player creation/borrow logic
SLOTEOF

cat > plans/fix-scratch-card/slots/css/scratch-styles.md << 'SLOTEOF'
# Slot: style.css — Scratch Card Styles

## What To Change
ONLY the .scratch-card related CSS classes.

## Layout
.scratch-container:
  position: relative
  max-width: 400px (adjust to match ticket PNG aspect ratio)
  margin: 0 auto

.reveal-layer:
  position: absolute
  top: 0; left: 0
  width: 100%; height: 100%
  background: #1a1a2e (dark background)
  contains positioned number/value labels

.reveal-layer .value-label:
  position: absolute
  color: #FFD700 (gold)
  font-weight: bold
  font-size: 1.2rem
  text-align: center
  positioned via inline styles from zone config

.scratch-canvas:
  position: absolute
  top: 0; left: 0
  width: 100%; height: 100%
  cursor: pointer
  touch-action: none (prevent scroll while scratching)

## What You Must NOT Change
- Any CSS for .dealer-*, .book-*, .ledger-* classes
- Navigation styles
- Header/footer styles
- Color variables / theme
SLOTEOF

cat > plans/fix-scratch-card/specs/no-touch-list.md << 'SLOTEOF'
# Files That Must NOT Be Modified

## Backend (all of it)
- luckledger-*/src/main/java/**
- luckledger-*/src/test/java/**
- luckledger-*/pom.xml
- luckledger-app/src/main/resources/db/migration/**
- luckledger-app/src/main/resources/application*.yml

## Frontend (non-scratch parts)
- config/scratch-zones.json (zone positions are calibrated)
- Ticket PNG assets
- js/app.js dealer section, book section, ledger section, API client
- css/style.css everything except .scratch-* classes

## Project config
- .claude/**
- .beads/**
- CLAUDE.md, DESIGN.md, BLUEPRINT.md
- pom.xml (root)
SLOTEOF

cat > plans/fix-scratch-card/specs/testing.md << 'SLOTEOF'
# Testing Strategy

## Manual Testing (required before merge)
1. mvn -pl luckledger-app spring-boot:run
2. Open http://localhost:8080
3. Verify dealer page loads correctly (unchanged)
4. Click a dealer -> see their books (unchanged)
5. Buy a ticket -> navigate to scratch page
6. Verify: ticket PNG is visible as the scratch surface
7. Scratch with mouse -> PNG pixels disappear, numbers visible underneath
8. Scratch 70% -> full reveal, result banner shows
9. Check ledger page (unchanged)

## Automated Testing
- mvn clean test must still pass (no backend changes)
- No new automated frontend tests needed for this fix
SLOTEOF

echo "  Plan created with 3 slot specs + 2 spec files"

echo "=== Step 6b: Committing plan ==="
git add -A
git commit -m "Add staff-engineer agent + fix-scratch-card plan" 2>/dev/null || echo "  Nothing new to commit"
git push 2>/dev/null || echo "  Push skipped"

echo ""
echo "=== DONE ==="
echo ""
echo "Now run:"
echo "  tmux new-session -s luckledger"
echo "  claude --dangerously-skip-permissions"
echo ""
echo "Then paste:"
echo '  You are the staff-engineer agent. Read your role from'
echo '  .claude/agents/staff-engineer.md.'
echo '  Read HANDOFF.md for current project state.'
echo '  Read plans/fix-scratch-card/PLAN.md for the current task.'
echo '  Execute the plan: dispatch subagents per slot, review each,'
echo '  merge to main, push. Update HANDOFF.md when done.'
