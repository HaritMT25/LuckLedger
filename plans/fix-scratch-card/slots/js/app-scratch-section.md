# Slot: app.js — Scratch Section Integration

## Purpose
Wire the scratch engine into the app scratch page.

## What To Change
ONLY the section of app.js that handles the #scratch hash route.

## Flow
1. When navigating to #scratch with a purchased ticket:
   - Determine mechanic type from ticket data
   - Pick PNG path based on mechanic type:
     CELESTIAL_FORTUNE -> check actual path in static/assets/
     DEMON_SEAL -> check actual path in static/assets/
   - Create reveal layer div with positioned number/value labels
   - Call initScratch(canvas, pngPath, onRevealCallback)
2. onRevealCallback:
   - POST /api/tickets/{id}/reveal
   - Display result banner (win/loss amount)
   - Update balance display

## What You Must NOT Change
- Dealer page rendering
- Book page rendering
- Ledger page rendering
- API client functions
- Navigation/routing logic
- Player creation/borrow logic
