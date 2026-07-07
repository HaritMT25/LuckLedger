#!/usr/bin/env bash
# Stop hook: run tests → if pass → commit → push. If fail → exit 2 (agent keeps going).
#
# The suite uses Testcontainers, which require a running Docker daemon. When Docker is
# unavailable the tests cannot run — the gate FAILS CLOSED: nothing is committed or
# pushed on an unverified tree. Export LUCKLEDGER_ALLOW_UNTESTED_COMMIT=1 for an
# explicit one-time override (commits with a warning, no test run).
# When Docker IS up, the full suite runs and still gates commit/push as before.

cd "$(git rev-parse --show-toplevel)" 2>/dev/null || exit 0

# Skip if no Java modules exist yet
ls luckledger-*/pom.xml >/dev/null 2>&1 || exit 0

# Testcontainers needs Docker. If the daemon is down, block commit/push (fail-closed):
# an unverified tree must not reach the remote. LUCKLEDGER_ALLOW_UNTESTED_COMMIT=1 overrides.
if ! docker info >/dev/null 2>&1; then
  if [ "${LUCKLEDGER_ALLOW_UNTESTED_COMMIT:-}" = "1" ]; then
    echo "Docker daemon unavailable — LUCKLEDGER_ALLOW_UNTESTED_COMMIT=1 override set; committing WITHOUT the test gate." >&2
  else
    echo "Docker daemon unavailable — tests cannot run, so commit/push is BLOCKED (fail-closed)." >&2
    echo "Start Docker to re-enable the gate, or export LUCKLEDGER_ALLOW_UNTESTED_COMMIT=1 to override once." >&2
    exit 2
  fi
else
  # Run tests
  mvn -q test -fae 2>&1 | tail -30
  if [ ${PIPESTATUS[0]} -ne 0 ]; then
    echo "Tests failed. Keep iterating." >&2
    exit 2
  fi
fi

# Commit if there are changes — excluding the nested CC worktrees, whose gitlinks must never
# be committed onto a real branch (a `git add -A` here would otherwise stage 160000 entries).
PENDING="$(git status --porcelain -- ':!.claude/worktrees' 2>/dev/null)"
if [ -n "$PENDING" ]; then
  BRANCH=$(git branch --show-current)
  git add -A -- ':!.claude/worktrees'
  git commit -m "auto: ${BRANCH} — tests passing"
  git push -u origin "$BRANCH" 2>&1 | tail -3
  echo "Committed and pushed to ${BRANCH}."
fi

exit 0
