# Slot: scratch.js — Scratch Engine

## Purpose
Provides the scratch-to-reveal interaction for ticket cards.

## Architecture
The ticket PNG IS the scratch surface. No metallic overlay. No rectangles. No circles. The crystals and seals in the PNG are the coating.

## Init Flow
1. Receive ticket data (mechanic type, PNG path, zone positions)
2. Create canvas matching ticket container dimensions
3. Load ticket PNG as Image()
4. On image load: ctx.drawImage(img, 0, 0, canvas.width, canvas.height)
5. The drawn PNG IS the coating — nothing else on top

## Scratch Interaction
On pointerdown:
  lastX = event.offsetX
  lastY = event.offsetY
  isScratching = true

On pointermove (if isScratching):
  ctx.globalCompositeOperation = 'destination-out'
  ctx.lineWidth = 50
  ctx.lineCap = 'round'
  ctx.lineJoin = 'round'
  ctx.beginPath()
  ctx.moveTo(lastX, lastY)
  ctx.lineTo(event.offsetX, event.offsetY)
  ctx.stroke()
  lastX = event.offsetX
  lastY = event.offsetY

On pointerup / pointerleave:
  isScratching = false

## Threshold Detection
- Every 10 pointer events, check scratch percentage
- Use getImageData on full canvas
- Count pixels with alpha < 128 / total pixels
- At 70% transparent:
  ctx.clearRect(0, 0, w, h) — reveal everything
  Call the reveal callback (triggers API call + result display)

## Zone Boundaries (OPTIONAL — only if scratch-zones.json exists)
- Read zones from config/scratch-zones.json
- Each zone has: id, x%, y%, size%, shape
- Clip scratch operations to zone boundaries
- Track per-zone scratch percentage independently

## What You Must NOT Change
- Zone positions in scratch-zones.json
- PNG file paths or assets
- API reveal call logic (that is in app.js)
- Dealer page, book page, or ledger page code
- Any backend Java code

## Exports
function initScratch(canvasElement, pngPath, onReveal) { ... }
function resetScratch() { ... }
