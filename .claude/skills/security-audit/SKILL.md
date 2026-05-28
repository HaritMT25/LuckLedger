---
name: security-audit
description: "Run a security audit on LuckLedger. Checks SQL injection, input validation, BigDecimal misuse, secrets, field injection. Invoke as /security-audit."
context: fork
agent: explorer
allowed-tools:
  - Bash
  - Read
  - Grep
  - Glob
---

## Security Audit for LuckLedger

Run the automated scan first:
```bash
bash .claude/skills/security-audit/scripts/scan.sh
```

Then manually verify:

### Ticket Ownership
- POST /tickets/{id}/reveal MUST check ticket.purchasedBy == requestingPlayerId
- GET /tickets/{id} MUST NOT return prize data when status=SOLD

### Concurrency
- Book purchase uses @Version (optimistic locking)
- Service catches OptimisticLockException → 409

### Spring Security
- CORS: explicit origins, never *
- Actuator: disabled or secured in production
- Exception messages: no internal state leaks
