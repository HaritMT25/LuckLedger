#!/usr/bin/env bash
# Fetch community-maintained skills from the best available repos.
# Run from project root: bash fetch-skills.sh
# Only fetches skills that external repos cover better than custom ones.
# Custom skills (scratch-card-domain, game-math, security-audit) are NOT replaced.

set -euo pipefail
SKILLS_DIR=".claude/skills"
TMP="/tmp/luckledger-skill-fetch-$$"
mkdir -p "$TMP"

echo "=== Fetching community skills for LuckLedger ==="

# 1. Spring Boot conventions + patterns (Siva Katamreddy — Spring educator)
echo "[1/5] sivalabs-agent-skills → spring-boot, spring-data-jpa"
git clone --depth 1 --quiet https://github.com/sivaprasadreddy/sivalabs-agent-skills.git "$TMP/sivalabs"
cp -r "$TMP/sivalabs/skills/spring-boot" "$SKILLS_DIR/spring-boot-conventions" 2>/dev/null || \
  echo "  WARN: spring-boot skill not found at expected path, check repo structure"

# 2. JPA patterns + Spring Boot TDD + Security (everything-claude-code — 175K+ stars)
echo "[2/5] everything-claude-code → jpa-patterns, springboot-tdd, springboot-security"
git clone --depth 1 --quiet https://github.com/affaan-m/everything-claude-code.git "$TMP/ecc"
for SKILL in jpa-patterns springboot-tdd springboot-security springboot-patterns; do
  if [ -d "$TMP/ecc/skills/$SKILL" ]; then
    cp -r "$TMP/ecc/skills/$SKILL" "$SKILLS_DIR/$SKILL"
    echo "  OK: $SKILL"
  elif [ -d "$TMP/ecc/.claude/skills/$SKILL" ]; then
    cp -r "$TMP/ecc/.claude/skills/$SKILL" "$SKILLS_DIR/$SKILL"
    echo "  OK: $SKILL (from .claude/skills)"
  else
    echo "  SKIP: $SKILL not found (check repo structure)"
  fi
done

# 3. Java code quality — SOLID, concurrency, API contracts (decebals/claude-code-java)
echo "[3/5] claude-code-java → api-contract-review, concurrency-review, solid-principles"
git clone --depth 1 --quiet https://github.com/decebals/claude-code-java.git "$TMP/ccj"
for SKILL in api-contract-review concurrency-review solid-principles; do
  if [ -d "$TMP/ccj/.claude/skills/$SKILL" ]; then
    cp -r "$TMP/ccj/.claude/skills/$SKILL" "$SKILLS_DIR/$SKILL"
    echo "  OK: $SKILL"
  else
    echo "  SKIP: $SKILL not found"
  fi
done

# 4. Spring Boot scaffolding skill (JHipster creator — Julien Dubois)
echo "[4/5] dr-jskill → spring-boot-scaffolding"
git clone --depth 1 --quiet https://github.com/jdubois/dr-jskill.git "$TMP/drj"
if [ -f "$TMP/drj/SKILL.md" ]; then
  mkdir -p "$SKILLS_DIR/dr-jskill"
  cp -r "$TMP/drj/"* "$SKILLS_DIR/dr-jskill/" 2>/dev/null
  echo "  OK: dr-jskill"
else
  echo "  SKIP: SKILL.md not found"
fi

# 5. VoltAgent subagents (proper frontmatter Spring Boot agents)
echo "[5/5] VoltAgent → spring-boot-engineer, java-architect agents"
git clone --depth 1 --quiet https://github.com/VoltAgent/awesome-claude-code-subagents.git "$TMP/volt"
AGENTS_DIR=".claude/agents"
for AGENT in spring-boot-engineer java-architect; do
  SRC="$TMP/volt/categories/02-language-specialists/$AGENT.md"
  if [ -f "$SRC" ]; then
    cp "$SRC" "$AGENTS_DIR/voltagent-$AGENT.md"
    echo "  OK: voltagent-$AGENT"
  else
    echo "  SKIP: $AGENT not found"
  fi
done

# Cleanup
rm -rf "$TMP"

echo ""
echo "=== Done. Fetched skills:"
ls -1 "$SKILLS_DIR"/
echo ""
echo "=== Agents:"
ls -1 "$AGENTS_DIR"/
echo ""
echo "Run '/doctor' in Claude Code to verify skill-description budget is not overflowing."
echo "If it overflows, remove the least-relevant fetched skills first."
