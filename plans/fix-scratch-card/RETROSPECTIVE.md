# Retrospective: fix-scratch-card

Date: 2026-05-29 · Orchestrator: staff-engineer · Outcome: ✅ merged (`fae7e22`)

## What was built
Rebuilt the scratch interaction to the correct architecture: the ticket PNG IS the coating,
drawn onto a single `<canvas>` and erased via `destination-out`; beneath it a dark
`.reveal-layer` div carries positioned `.value-label` spans. Frontend-only — `scratch.js`,
`app.js` (scratch section), `style.css` (scratch classes). Backend untouched.

## What worked
- **One agent, one worktree, all three coupled files.** scratch.js / app.js / css share a tight
  DOM+CSS+function contract (canvas id, `.reveal-layer`, `.value-label`, `initScratch` signature).
  Splitting them across parallel worktrees would have guaranteed an integration mismatch. Bundling
  them into a single implementer task kept the contract coherent. "One slot = one worktree" is the
  wrong default when slots share an interface — group by contract, not by file.
- **Reviewer caught a real bug.** spring-reviewer flagged that a PNG load failure left a transparent
  canvas that reads as already-scratched (tiny scratch > 70% → instant false reveal). A surgical fix
  (guard draw on `naturalWidth > 0`, `onerror` fallback coating) followed. Pure code-by-spec would
  have shipped it.
- **Surfacing the plan's internal contradiction before coding.** The slots asked for real per-cell
  numbers, but the reveal API returns only `{isWinner, prizeAmount}` and the no-touch-list forbids
  backend changes. Asked the user (frontend-only vs. expand scope) instead of guessing → avoided
  either an out-of-scope backend change or shipping a feature with no data source.

## What broke / friction
- **Worktree branched from the WRONG base.** The first `Agent(isolation: worktree)` branched from
  `fd11ccf` (old main) instead of the current `frontend-fixes-and-shops` HEAD, so its `app.js` lacked
  the shops frontend. The merge conflicted and would have reverted shops work. **Fix forward:** when
  isolation matters AND base matters, create the worktree yourself (`git worktree add -b … HEAD`) and
  dispatch a non-isolated agent into it, rather than relying on the tool's default base. Verify
  `git merge-base` equals the intended HEAD before trusting a worktree branch.
- **Subagent `git add -A` polluted the SHARED branch.** A subagent ran `git add -A` from inside the
  nested `.claude/worktrees/` tree and committed a worktree gitlink (`160000`) onto the shared branch.
  It self-reverted, but with 50+ nested worktrees this is a live hazard. **Fix forward:** instruct
  subagents to `git add <explicit paths>`, never `-A`; verify `git ls-files -s | grep 160000` is empty
  before merging. (Saved to beads via `bd remember`.)
- **`SendMessage` unavailable** in this harness, so follow-up fixes couldn't "continue" the same agent
  with its context — worked around by dispatching a fresh agent pointed at the existing worktree path.
- **Severe tool-output buffering** made each verify/commit cycle slow, and one batch of parallel bash
  probes hit a cascade-cancellation when a single probe exited non-zero. **Fix forward:** for
  state-changing git ops, prefer a single sequential `cmd && cmd` command over parallel probe fan-out.
- **Docker daemon down** → `mvn test` could not run. Acceptable (zero backend change) but the Stop
  hook (`blocks if mvn test fails`) assumes Docker is up.

## Carry-forward
- Real engine grid still not exposed (client-generated numbers) — HANDOFF priority #2.
- Zone coordinates and the whole visual were never verified against rendered pixels — needs a browser
  pass (HANDOFF priorities #3, #4).
