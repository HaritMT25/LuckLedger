# Plan v2: Fix Scratch Card — PRECISE REQUIREMENTS

## What Is Currently Wrong
1. ENTIRE PNG is one giant scratch surface — should be individual zones only
2. Demon Seal reveals NUMBERS underneath — should reveal GOLD/SILVER/BROKEN icons
3. The scratch-zones.json calibration (from map-zones.html) is IGNORED
4. All zones clear together at 70% — each zone should clear independently

## What CORRECT Behavior Looks Like

### Celestial Fortune
- 4 dragon coin zones at top (winning numbers) — each independently scratchable
- 12 crystal zones below (player numbers) — each independently scratchable
- Scratching a crystal erases ONLY that crystal from the PNG
- Underneath: the NUMBER on dark background (e.g. "7", "23", "14")
- When all 16 zones are scratched: result banner shows matches + prize
- Areas BETWEEN zones are NOT scratchable (background stays visible)

### Demon Seal
- 6 seal zones in hexagonal arrangement — each independently scratchable
- Scratching a seal erases ONLY that seal from the PNG
- Underneath: GOLD seal icon (glowing), SILVER seal icon, or BROKEN/CRACKED icon
- NOT a number — this is a seal reveal mechanic, not number match
- When all 6 seals are scratched: result banner shows total points + prize
- Center demon is NOT scratchable — it is decoration

### Technical Architecture
- The PNG is drawn onto the canvas as before (correct)
- scratch-zones.json defines circular/rectangular clip regions
- Pointer events ONLY erase within the zone the pointer is currently in
- Each zone tracks its own scratch percentage independently
- When a zone hits 70% scratched: auto-clear that zone, reveal its content
- When ALL zones are revealed: trigger the result/prize display

### Zone Clipping (the key missing piece)
For each pointer event:
  1. Check which zone (if any) the pointer is inside
  2. If not in any zone: do nothing (no scratching)
  3. If in a zone: ctx.save() → ctx.beginPath() → draw zone circle/rect
     → ctx.clip() → then do the destination-out line stroke → ctx.restore()
  4. This ensures erasing only happens within that zone boundary

### Reveal Content Per Mechanic
- CELESTIAL_FORTUNE: div with the number in gold text, dark background
- DEMON_SEAL: div with an icon/emoji + label:
  - GOLDEN: gold circle + "GOLD" + glow effect
  - SILVER: silver circle + "SILVER" 
  - BROKEN: cracked dark circle + "BROKEN" + dimmed

## Files To Modify
- js/scratch.js ONLY
- css/style.css scratch section ONLY (for reveal styling)

## Files To NOT Touch
- js/app.js (dealer, book, ledger, routing, API calls)
- config/scratch-zones.json (positions are calibrated)
- Any backend Java code
- Any PNG assets
- css/style.css non-scratch sections
