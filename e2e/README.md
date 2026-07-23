# LuckLedger e2e (Playwright)

1. Boot the app with a pinned master password:
   `LUCKLEDGER_MASTER_PASSWORD=ci-e2e-password mvn -pl luckledger-app spring-boot:run` (waits on Docker postgres).
2. Wait for readiness: `curl -sf http://localhost:8080/api/health`.
3. Install + run: `cd e2e && npm ci && LUCKLEDGER_MASTER_PASSWORD=ci-e2e-password npm test`
   (override the target with `E2E_BASE_URL`; screenshots land in `e2e/shots/`).
