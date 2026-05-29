# Slot: style.css — Scratch Card Styles

## What To Change
ONLY the .scratch-card related CSS classes.

## Layout
.scratch-container:
  position: relative
  max-width: 400px (adjust to match ticket PNG aspect ratio)
  margin: 0 auto

.reveal-layer:
  position: absolute
  top: 0; left: 0
  width: 100%; height: 100%
  background: #1a1a2e (dark background)
  contains positioned number/value labels

.reveal-layer .value-label:
  position: absolute
  color: #FFD700 (gold)
  font-weight: bold
  font-size: 1.2rem
  text-align: center
  positioned via inline styles from zone config

.scratch-canvas:
  position: absolute
  top: 0; left: 0
  width: 100%; height: 100%
  cursor: pointer
  touch-action: none (prevent scroll while scratching)

## What You Must NOT Change
- Any CSS for .dealer-*, .book-*, .ledger-* classes
- Navigation styles
- Header/footer styles
- Color variables / theme
