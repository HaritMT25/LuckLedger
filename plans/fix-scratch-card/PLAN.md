# Plan: Fix Scratch Card Architecture

## Problem
The scratch card currently draws metallic rectangles/circles as a coating over the ticket. Scratching reveals numbers on opaque circles/rectangles. This is wrong.

## Correct Architecture
The ticket PNG IS the scratch coating. The crystals/seals in the PNG are what the player scratches off. Underneath is a dark background with revealed numbers/values.

  Bottom: <div class="reveal-layer">
            dark background
            positioned number/value labels from API reveal data
          </div>

  Top:    <canvas id="scratch-surface">
            ticket PNG drawn onto canvas at init
            destination-out erases PNG pixels on pointer move
          </canvas>

## Sequence
1. Fix scratch.js — scratch engine (slot: slots/js/scratch.js.md)
2. Fix app.js scratch section only (slot: slots/js/app-scratch-section.md)
3. Fix CSS scratch styles only (slot: slots/css/scratch-styles.md)
4. Test manually — verify PNG loads, scratch erases, numbers appear
5. Review — spring-reviewer checks no other pages broken

## Success Criteria
- Ticket PNG covers the full scratch card area
- Scratching erases the PNG pixels (crystals/seals disappear)
- Numbers/values appear underneath on dark background
- 70% scratch threshold triggers full reveal + result banner
- Dealer page, book page, ledger page are UNCHANGED
- Backend code is UNCHANGED
- Zone positions from scratch-zones.json are preserved

## Files To Modify
- luckledger-app/src/main/resources/static/js/scratch.js
- luckledger-app/src/main/resources/static/js/app.js (scratch section ONLY)
- luckledger-app/src/main/resources/static/css/style.css (scratch section ONLY)

## Dependencies
- Ticket PNGs: check actual paths in static/assets/
- Zone config: config/scratch-zones.json (if exists)
- API: POST /api/tickets/{id}/reveal returns grid data with numbers/seals
