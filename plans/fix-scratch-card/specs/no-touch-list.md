# Files That Must NOT Be Modified

## Backend (all of it)
- luckledger-*/src/main/java/**
- luckledger-*/src/test/java/**
- luckledger-*/pom.xml
- luckledger-app/src/main/resources/db/migration/**
- luckledger-app/src/main/resources/application*.yml

## Frontend (non-scratch parts)
- config/scratch-zones.json (zone positions are calibrated)
- Ticket PNG assets
- js/app.js dealer section, book section, ledger section, API client
- css/style.css everything except .scratch-* classes

## Project config
- .claude/**
- .beads/**
- CLAUDE.md, DESIGN.md, BLUEPRINT.md
- pom.xml (root)
