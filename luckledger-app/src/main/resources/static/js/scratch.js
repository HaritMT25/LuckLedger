/* Scratch card canvas engine — the ticket PNG IS the coating, scratched off per zone.
 *
 * The full ticket PNG is drawn onto this canvas; it is the silver foil. Scratching is confined to the
 * calibrated zones in config/scratch-zones.json: a pointer only lifts coating within the zone it is
 * currently inside (each stroke is clipped to that zone's circle/rect), so the spaces between zones,
 * the title, badges and the centre demon stay covered. Each zone tracks its own scratched fraction and
 * clears independently once ~70% of *its* coating is gone, uncovering the reveal layer beneath — the
 * ticket's real values (numbers or seal tiles), which app.js renders into #reveal-layer from the grid
 * served by the API. When every zone has revealed, `onReveal` fires exactly once. If the zone config
 * can't be matched the engine falls back to the legacy whole-surface scratch so the card still works. */

/**
 * Initialise a scratch surface on a canvas, using a ticket PNG as the full coating.
 * @param {HTMLCanvasElement} canvas the scratch canvas
 * @param {string} pngPath path to the ticket PNG (the coating drawn over the dark reveal layer)
 * @param {Function} onReveal invoked exactly once when the reveal threshold (70%) is reached
 * @returns {{reset: Function}} a small controller; call reset() to re-coat and allow scratching again
 */
function initScratch(canvas, pngPath, onReveal = () => {}) {
    const ctx = canvas.getContext('2d', { willReadFrequently: true });
    const W = canvas.width;
    const H = canvas.height;
    const brush = Math.max(28, Math.round(W * 0.08)); // ~ a fingertip/coin on a 360px-wide canvas
    const threshold = 0.7;

    let loaded = false;
    let revealed = false;
    let scratching = false;
    let moveCount = 0;
    let last = null;

    const FALLBACK_COLOR = '#241c42'; // opaque coating used when the PNG can't be drawn

    // ----- Per-zone scratch model ---------------------------------------------------------------
    let zones = null;          // array of scratchable zones once loaded; null while loading
    let zonesFailed = false;   // true if config missing/unmatched => fall back to whole-surface scratch
    let lastZoneId = null;     // zone the last stroke was in, so the line never bridges across a gap
    const revealedZones = new Set();

    // Zone geometry in canvas pixels. Circles: cx/cy fractions of W/H, r a fraction of W. Rects:
    // x/w fractions of W, y/h fractions of H. (Matches the scratch-zones.json note.)
    function _bounds(z) {
        if (z.shape === 'circle') { const r = z.r * W; return { x: z.cx * W - r, y: z.cy * H - r, w: r * 2, h: r * 2 }; }
        return { x: z.x * W, y: z.y * H, w: z.w * W, h: z.h * H };
    }
    // The scratch zone under a canvas point, or null if the point is between/outside zones.
    function _zoneAt(p) {
        if (!zones) return null;
        for (const z of zones) {
            if (z.shape === 'circle') {
                const dx = p.x - z.cx * W, dy = p.y - z.cy * H, r = z.r * W;
                if (dx * dx + dy * dy <= r * r) return z;
            } else if (p.x >= z.x * W && p.x <= (z.x + z.w) * W && p.y >= z.y * H && p.y <= (z.y + z.h) * H) {
                return z;
            }
        }
        return null;
    }
    // Trace a zone's outline as the current path (caller clips or fills it).
    function _zonePath(z) {
        ctx.beginPath();
        if (z.shape === 'circle') ctx.arc(z.cx * W, z.cy * H, z.r * W, 0, Math.PI * 2);
        else ctx.rect(z.x * W, z.y * H, z.w * W, z.h * H);
    }
    // Fraction (0..1) of a single zone's coating that has been scratched away.
    function _zoneClearFraction(z) {
        const b = _bounds(z);
        const bx = Math.max(0, Math.floor(b.x)), by = Math.max(0, Math.floor(b.y));
        const bw = Math.min(W - bx, Math.ceil(b.w)), bh = Math.min(H - by, Math.ceil(b.h));
        if (bw <= 0 || bh <= 0) return 0;
        const data = ctx.getImageData(bx, by, bw, bh).data;
        const cxp = z.cx * W, cyp = z.cy * H, rp2 = (z.r * W) * (z.r * W);
        let clear = 0, total = 0;
        for (let yy = 0; yy < bh; yy += 2) {
            for (let xx = 0; xx < bw; xx += 2) {
                if (z.shape === 'circle') {
                    const dx = bx + xx - cxp, dy = by + yy - cyp;
                    if (dx * dx + dy * dy > rp2) continue; // only count pixels inside the circle
                }
                total++;
                if (data[(yy * bw + xx) * 4 + 3] < 128) clear++;
            }
        }
        return total ? clear / total : 0;
    }
    // Lift a zone's remaining coating and mark it revealed; fire onReveal once every zone is open.
    function _revealZone(z) {
        if (revealedZones.has(z.id)) return;
        ctx.save();
        ctx.globalCompositeOperation = 'destination-out';
        _zonePath(z);
        ctx.fillStyle = 'rgba(0,0,0,1)';
        ctx.fill();
        ctx.restore();
        revealedZones.add(z.id);
        if (zones.length && revealedZones.size >= zones.length && !revealed) {
            revealed = true;
            onReveal();
        }
    }

    // Identify the mechanic from the ticket PNG filename, then load that ticket's scratch zones.
    function _loadZones() {
        fetch('config/scratch-zones.json')
            .then((r) => r.json())
            .then((cfg) => {
                const tickets = (cfg && cfg.tickets) || {};
                const file = pngPath.split('/').pop();
                for (const t of Object.values(tickets)) {
                    if (t.image && t.image.split('/').pop() === file) {
                        zones = (t.zones || []).filter((z) => z.scratch && z.shape !== 'path');
                        break;
                    }
                }
                if (!zones) { zonesFailed = true; } // unmatched PNG => legacy whole-surface scratch
            })
            .catch(() => { zonesFailed = true; });
    }

    // True only once the image has genuinely decoded. A `complete` image that failed to load reports
    // naturalWidth === 0, so checking both guards against treating a broken PNG as ready.
    const img = new Image();
    function imageReady() {
        return img.complete && img.naturalWidth > 0;
    }

    // The PNG itself is the coating: draw it to fill the whole canvas. Only ever called once we know the
    // image actually decoded — otherwise the canvas would stay transparent and read as fully scratched.
    function paintCoating() {
        loaded = true;
        ctx.globalCompositeOperation = 'source-over';
        ctx.clearRect(0, 0, W, H);
        ctx.drawImage(img, 0, 0, W, H);
    }

    // If the PNG 404s or fails to decode, lay down an opaque placeholder coating instead of leaving the
    // canvas transparent. This keeps the 70% threshold honest (there is real coating to scratch through)
    // and never fires onReveal by itself.
    function paintFallback() {
        loaded = true;
        ctx.globalCompositeOperation = 'source-over';
        ctx.clearRect(0, 0, W, H);
        ctx.fillStyle = FALLBACK_COLOR;
        ctx.fillRect(0, 0, W, H);
    }

    // Draw the real coating if the image decoded, otherwise the opaque fallback.
    function paint() {
        if (imageReady()) {
            paintCoating();
        } else {
            paintFallback();
        }
    }

    img.onload = () => {
        // Guard: only treat the PNG as the coating when it truly decoded; otherwise fall back.
        if (imageReady()) {
            paintCoating();
        } else {
            paintFallback();
        }
    };
    img.onerror = paintFallback; // 404 / decode failure: never present as an already-scratched card
    img.src = pngPath;
    if (imageReady()) paintCoating(); // already cached and decoded
    _loadZones(); // resolve mechanic + zones (async); scratching falls back to whole-surface until ready

    // Map client coords to canvas coords, accounting for CSS scaling.
    function _pos(e) {
        const r = canvas.getBoundingClientRect();
        return {
            x: (e.clientX - r.left) * (W / r.width),
            y: (e.clientY - r.top) * (H / r.height),
        };
    }

    // Erase a round dot and, while dragging, a continuous fat line from the previous point. When `zone`
    // is given the erase is clipped to that zone's outline, so only its coating lifts; without a zone
    // (legacy fallback) the whole surface erases.
    function _stroke({ x, y }, zone) {
        ctx.save();
        if (zone) { _zonePath(zone); ctx.clip(); }
        ctx.globalCompositeOperation = 'destination-out';
        ctx.lineCap = 'round';
        ctx.lineJoin = 'round';
        ctx.lineWidth = brush;
        ctx.strokeStyle = 'rgba(0,0,0,1)';
        ctx.fillStyle = 'rgba(0,0,0,1)';
        if (last) {
            ctx.beginPath();
            ctx.moveTo(last.x, last.y);
            ctx.lineTo(x, y);
            ctx.stroke();
        }
        ctx.beginPath();
        ctx.arc(x, y, brush / 2, 0, Math.PI * 2);
        ctx.fill();
        ctx.restore();
        last = { x, y };
    }

    // Reveal logic. With zones loaded, each zone clears independently once ~70% of *its* coating is
    // gone (passing `zone` checks just that one — cheap, for the active stroke). Without zones (config
    // unmatched) it falls back to the legacy whole-surface 70% check that clears everything at once.
    function _check(zone) {
        if (revealed || !loaded) return; // no coating yet => never reveal
        if (!zones && !zonesFailed) return; // zones still loading: nothing to check yet
        if (zonesFailed) {
            const data = ctx.getImageData(0, 0, W, H).data;
            let clear = 0;
            let total = 0;
            // Sample every 4th pixel in both x and y (alpha channel only) to cut per-check cost.
            const rowBytes = W * 4;
            for (let y = 0; y < H; y += 4) {
                const rowStart = y * rowBytes;
                for (let x = 0; x < W; x += 4) {
                    const alpha = data[rowStart + x * 4 + 3];
                    total++;
                    if (alpha < 128) clear++;
                }
            }
            if (total > 0 && clear / total >= threshold) {
                ctx.globalCompositeOperation = 'source-over';
                ctx.clearRect(0, 0, W, H);
                revealed = true;
                onReveal();
            }
            return;
        }
        for (const z of (zone ? [zone] : zones)) {
            if (revealedZones.has(z.id)) continue;
            if (_zoneClearFraction(z) >= threshold) _revealZone(z);
        }
    }

    canvas.addEventListener('pointerdown', (e) => {
        if (!loaded || revealed) return;
        scratching = true;
        last = null;
        try { canvas.setPointerCapture(e.pointerId); } catch (_) { /* unsupported */ }
        const p = _pos(e);
        if (zonesFailed) { lastZoneId = null; _stroke(p, null); return; } // config unmatched: whole-surface
        if (!zones) return; // zones still loading: wait rather than scratch the whole (unclipped) surface
        const zone = _zoneAt(p);
        lastZoneId = zone ? zone.id : null;
        if (zone) _stroke(p, zone); // outside any zone: nothing scratches
    });
    canvas.addEventListener('pointermove', (e) => {
        if (!scratching || revealed) return;
        const p = _pos(e);
        if (zonesFailed) { // config unmatched: whole-surface scratch
            _stroke(p, null);
            if (++moveCount % 10 === 0) _check();
            return;
        }
        if (!zones) return; // zones still loading
        const zone = _zoneAt(p);
        if (!zone) { last = null; lastZoneId = null; return; } // between zones: don't scratch, break the line
        if (zone.id !== lastZoneId) { last = null; lastZoneId = zone.id; } // entered a new zone: no bridging stroke
        _stroke(p, zone);
        if (++moveCount % 6 === 0) _check(zone);
    });
    const end = () => { if (!scratching) return; scratching = false; last = null; lastZoneId = null; _check(); };
    canvas.addEventListener('pointerup', end);
    canvas.addEventListener('pointercancel', () => { scratching = false; last = null; lastZoneId = null; });
    canvas.addEventListener('pointerleave', () => { scratching = false; last = null; lastZoneId = null; });

    return {
        reset() {
            revealed = false;
            scratching = false;
            moveCount = 0;
            last = null;
            lastZoneId = null;
            revealedZones.clear();
            paint(); // re-coat; the reveal-layer (numbers or seal tiles) stays as it was rendered
        },
    };
}
