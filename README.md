# LuckLedger

A free-to-play scratch-card simulator built as a **gambling-awareness** tool. Virtual
currency only — no real money in, no cash-out — with a persistent, append-only ledger
that quietly makes one thing obvious: over time, the house always wins.

Java 21 · Spring Boot 3.5 · multi-module Maven · Postgres.

## Quickstart

With Docker running, from the repo root:

```bash
docker compose -f compose.prod.yaml up --build
```

Then open <http://localhost:8080>.

This builds the app image and starts it against a throwaway Postgres on the compose
network (nothing is published on 5432, so it won't clash with a local database). First
build downloads Maven dependencies and can take a few minutes; subsequent builds are cached.

Tear everything down, including the database volume:

```bash
docker compose -f compose.prod.yaml down -v
```

## Master (operator) login

Most of the app is anonymous. The operator-only screens under `/api/master/**` sit behind a
single master account that is **fail-closed** — there is no committed default password.

- Leave it unset and the app mints a random one-time password at startup and prints it once:

  ```bash
  docker compose -f compose.prod.yaml logs app | grep -i "one-time password"
  ```

- Or pin your own before starting:

  ```bash
  LUCKLEDGER_MASTER_PASSWORD=change-me docker compose -f compose.prod.yaml up --build
  ```

## Local development (without Docker for the app)

`mvn -pl luckledger-app spring-boot:run` starts the app and auto-launches the dev Postgres in
`luckledger-app/compose.yaml` via Spring Boot's docker-compose support. Run `mvn test` for the
full suite (integration tests use Testcontainers).

## Architecture

See [HANDOFF.md](HANDOFF.md) for the module map, generation pipeline, and the domain invariants
(payout ratio sacred, pre-generated pools, mandatory verification, append-only ledger).
