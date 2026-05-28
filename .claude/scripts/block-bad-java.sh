#!/usr/bin/env bash
# PreToolUse hook: Exit 0=allow, Exit 2=block. Stderr → Claude's feedback.
INPUT=$(cat)

# Field injection
echo "$INPUT" | grep -qE '@Autowired\s+(private|protected|public)' 2>/dev/null && \
  { echo "BLOCKED: Field injection. Use constructor injection." >&2; exit 2; }

# System.out (allow in tests)
echo "$INPUT" | grep -q 'System\.\(out\|err\)\.print' 2>/dev/null && \
  ! echo "$INPUT" | grep -q 'src/test/' 2>/dev/null && \
  { echo "BLOCKED: Use SLF4J Logger, not System.out/err." >&2; exit 2; }

# double/float for money
echo "$INPUT" | grep -qE '(private|public|protected)\s+(double|float)\s+(price|amount|balance|cost|payout|budget|value|won|spent|borrowed|revenue|floor)' 2>/dev/null && \
  { echo "BLOCKED: Use BigDecimal for money." >&2; exit 2; }

# SQL concatenation
echo "$INPUT" | grep -qE '"[^"]*\b(SELECT|INSERT|UPDATE|DELETE)\b[^"]*"\s*\+' 2>/dev/null && \
  { echo "BLOCKED: No SQL concatenation. Use @Query with named params." >&2; exit 2; }

# BigDecimal.equals()
echo "$INPUT" | grep -qE '\.equals\(\s*(BigDecimal\.|new BigDecimal|ZERO)' 2>/dev/null && \
  { echo "BLOCKED: BigDecimal.equals() is scale-sensitive. Use .compareTo() or .signum()." >&2; exit 2; }

exit 0
