#!/usr/bin/env bash
# Stop hook: run tests → if pass → commit → push. If fail → exit 2 (agent keeps going).

cd "$(git rev-parse --show-toplevel)" 2>/dev/null || exit 0

# Skip if no Java modules exist yet
ls luckledger-*/pom.xml >/dev/null 2>&1 || exit 0

# Run tests
mvn -q test -fae 2>&1 | tail -30
if [ ${PIPESTATUS[0]} -ne 0 ]; then
  echo "Tests failed. Keep iterating." >&2
  exit 2
fi

# Tests passed — commit if there are changes
if [ -n "$(git status --porcelain)" ]; then
  BRANCH=$(git branch --show-current)
  git add -A
  git commit -m "auto: ${BRANCH} — tests passing"
  git push -u origin "$BRANCH" 2>&1 | tail -3
  echo "Committed and pushed to ${BRANCH}."
fi

exit 0
