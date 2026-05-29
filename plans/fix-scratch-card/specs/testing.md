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
