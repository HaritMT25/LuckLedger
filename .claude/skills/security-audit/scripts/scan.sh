#!/usr/bin/env bash
# Security scan for LuckLedger. Exit 0=clean, 1=issues.
set -uo pipefail
ISSUES=0
echo "=== LuckLedger Security Scan ==="

for CHECK in \
  "SQL concat:\"SELECT\|INSERT\|UPDATE\|DELETE.*\"\s*+" \
  "BigDecimal.equals:\.equals(BigDecimal\.\|\.equals(new BigDecimal\|\.equals(ZERO)" \
  "double for money:(double\|float)\s\+\(price\|amount\|balance\|cost\|payout\|value\|won\|spent\|borrowed\)" \
  "Field injection:@Autowired.*\(private\|protected\|public\)" \
  "System.out:System\.\(out\|err\)\.print" \
  "printStackTrace:printStackTrace()"
do
  NAME="${CHECK%%:*}"
  PATTERN="${CHECK#*:}"
  echo -n "--- $NAME: "
  if FOUND=$(grep -rn "$PATTERN" --include="*.java" . 2>/dev/null | grep -v '/test/'); then
    echo "FOUND"
    echo "$FOUND"
    ISSUES=$((ISSUES + 1))
  else
    echo "PASS"
  fi
done

echo ""
echo "=== $ISSUES issue(s) ==="
[ $ISSUES -eq 0 ] && exit 0 || exit 1
