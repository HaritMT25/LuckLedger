/* Scratch card canvas engine — the ticket PNG IS the coating, scratched off per zone.
 *
 * The full ticket PNG is drawn onto this canvas; it is the silver foil. Scratching is confined to the
 * calibrated zones in config/scratch-zones.json: a pointer only lifts coating within the zone it is
 * currently inside (each erase is clipped to that zone's outline), so the spaces between zones, the
 * title, badges and the centre demon stay covered. Each zone tracks its own scratched fraction and
 * clears independently once ~70% of *its* coating is gone, uncovering the reveal layer beneath — the
 * ticket's real values (numbers or seal tiles), which js/views/play.js renders into #reveal-layer from the grid
 * served by the API. When every zone has revealed, `onReveal` fires exactly once. If the zone config
 * can't be matched the engine falls back to the legacy whole-surface scratch so the card still works.
 *
 * FEEL: the surface stays GPU-accelerated — there are NO pixel readbacks (no getImageData, no
 * willReadFrequently). Coverage is tracked geometrically: each zone owns a coarse boolean cell grid,
 * and every erase stamp marks the cells it touches, so progress mirrors exactly what was rubbed off.
 * Erasing uses a soft feathered brush stamped along interpolated, coalesced pointer paths and batched
 * once per animation frame; a crossed zone dissolves its remaining coating out over ~220ms rather than
 * popping. On retina the backing store is scaled by devicePixelRatio while every computation below stays
 * in the canvas's LOGICAL units, so the coating and stamps render crisp. */

/**
 * Initialise a scratch surface on a canvas, using a ticket PNG as the full coating.
 * @param {HTMLCanvasElement} canvas the scratch canvas
 * @param {string} pngPath path to the ticket PNG (the coating drawn over the dark reveal layer)
 * @param {Function} onReveal invoked exactly once when the reveal threshold (70%) is reached
 * @returns {{revealAll: Function, reset: Function}} a small controller; revealAll() lifts everything at
 *   once, reset() re-coats and allows scratching again
 */
function initScratch(canvas, pngPath, onReveal = () => {}) {
    // LOGICAL size: the canvas's authored width/height (play.js writes width="360" height="640", the
    // e2e sweep and every fraction in scratch-zones.json are relative to these). Read them BEFORE we
    // grow the backing store — from here on W/H are the only coordinate space this file speaks.
    const W = canvas.width;
    const H = canvas.height;
    // Drop willReadFrequently (there are no readbacks) so the canvas stays GPU-accelerated, and ask for
    // a desynchronized surface for lower pointer-to-paint latency where the browser supports it.
    const ctx = canvas.getContext('2d', { desynchronized: true });
    // Retina crispness: back the canvas with logical × DPR device pixels (capped at 2 so a 3x phone
    // doesn't quadruple fill cost), then scale the drawing matrix so every draw below can keep using
    // logical units. Resizing the canvas resets its context state, so the transform is set AFTER.
    const dpr = Math.min(window.devicePixelRatio || 1, 2);
    canvas.width = Math.round(W * dpr);
    canvas.height = Math.round(H * dpr);
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);

    const brush = Math.max(28, Math.round(W * 0.08)); // ~ a fingertip/coin on a 360px-wide canvas
    const stampR = brush / 2;                          // nominal erase radius (logical)
    const step = brush / 3;                            // interpolation + grid-cell granularity
    const threshold = 0.7;

    let loaded = false;
    let revealed = false;
    let scratching = false;
    let last = null;           // last stamped point (logical), so segments interpolate; null breaks the line
    let pointerType = 'mouse'; // pointerType of the active pointer (drives touch-only haptics)

    const FALLBACK_COLOR = '#241c42'; // opaque coating used when the PNG can't be drawn

    function reducedMotion() {
        // core.js publishes REDUCED_MOTION in the shared classic-script scope; guard with typeof so the
        // engine still runs (motion on) if ever loaded on its own.
        return typeof REDUCED_MOTION !== 'undefined' && REDUCED_MOTION;
    }

    // ----- Soft brush stamp ---------------------------------------------------------------------
    // One offscreen brush built at init: an opaque core out to 60% of the radius, feathering to fully
    // transparent at the edge. Erasing draws this with destination-out, so the ragged, soft-edged alpha
    // profile is what lifts — no hard-edged discs. Supersampled ×2 for a smooth gradient when scaled.
    const stamp = document.createElement('canvas');
    const stampSize = Math.max(2, Math.ceil(brush) * 2);
    stamp.width = stamp.height = stampSize;
    (function paintStamp() {
        const sctx = stamp.getContext('2d');
        const c = stampSize / 2;
        const g = sctx.createRadialGradient(c, c, 0, c, c, c);
        g.addColorStop(0, 'rgba(0,0,0,1)');
        g.addColorStop(0.6, 'rgba(0,0,0,1)');
        g.addColorStop(1, 'rgba(0,0,0,0)');
        sctx.fillStyle = g;
        sctx.beginPath();
        sctx.arc(c, c, c, 0, Math.PI * 2);
        sctx.fill();
    })();

    // Radius for a single stamp: ±8% jitter for a subtly ragged scraped edge, scaled by pen pressure
    // when the pointer reports a usable value (mouse/touch report 0 here and stamp at the nominal size).
    function _radiusFor(pressure) {
        const jitter = 0.92 + Math.random() * 0.16;
        const pScale = pressure > 0 ? (0.8 + 0.4 * pressure) : 1;
        return stampR * jitter * pScale;
    }

    // ----- Per-zone scratch model ---------------------------------------------------------------
    let zones = null;          // array of scratchable zones once loaded; null while loading
    let zonesFailed = false;   // true if config missing/unmatched => fall back to whole-surface scratch
    let lastZoneId = null;     // zone the last stroke was in, so the line never bridges across a gap
    const revealedZones = new Set();
    const dissolving = new Set(); // zones currently animating their coating out (guards re-triggering)
    let fallbackDissolving = false;

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
    // A zone's outline as a reusable Path2D, precomputed once so per-frame clip/fill never rebuilds it.
    function _zonePath(z) {
        const p = new Path2D();
        if (z.shape === 'circle') p.arc(z.cx * W, z.cy * H, z.r * W, 0, Math.PI * 2);
        else p.rect(z.x * W, z.y * H, z.w * W, z.h * H);
        return p;
    }

    // ----- Geometric coverage grid --------------------------------------------------------------
    // A coarse boolean grid over a bounds box (cell ≈ brush/3). Only cells whose centre lies inside the
    // zone shape count toward `total`. Erase stamps mark the cells within their radius, so fraction =
    // marked/total exactly tracks what the (zone-clipped) stamps removed — no pixel sampling needed.
    function _buildGrid(b, z) {
        const cols = Math.max(1, Math.ceil(b.w / step));
        const rows = Math.max(1, Math.ceil(b.h / step));
        const inside = new Uint8Array(cols * rows);
        const mark = new Uint8Array(cols * rows);
        let total = 0;
        for (let ry = 0; ry < rows; ry++) {
            for (let rx = 0; rx < cols; rx++) {
                const cxp = b.x + (rx + 0.5) * step;
                const cyp = b.y + (ry + 0.5) * step;
                let inZone = true;
                if (z && z.shape === 'circle') {
                    const dx = cxp - z.cx * W, dy = cyp - z.cy * H, r = z.r * W;
                    inZone = dx * dx + dy * dy <= r * r; // only cells truly under the circle count
                }
                if (inZone) { inside[ry * cols + rx] = 1; total++; }
            }
        }
        return { cols, rows, ox: b.x, oy: b.y, inside, mark, total, count: 0 };
    }
    // Mark every countable cell whose centre is within radius R of an erase stamp at (x,y).
    function _markGrid(grid, x, y, R) {
        if (!grid) return;
        const { cols, rows, ox, oy } = grid;
        const minX = Math.max(0, Math.floor((x - R - ox) / step));
        const maxX = Math.min(cols - 1, Math.floor((x + R - ox) / step));
        const minY = Math.max(0, Math.floor((y - R - oy) / step));
        const maxY = Math.min(rows - 1, Math.floor((y + R - oy) / step));
        const R2 = R * R;
        for (let ry = minY; ry <= maxY; ry++) {
            for (let rx = minX; rx <= maxX; rx++) {
                const idx = ry * cols + rx;
                if (grid.mark[idx] || !grid.inside[idx]) continue;
                const dx = ox + (rx + 0.5) * step - x, dy = oy + (ry + 0.5) * step - y;
                if (dx * dx + dy * dy <= R2) { grid.mark[idx] = 1; grid.count++; }
            }
        }
    }
    function _fraction(grid) { return grid && grid.total ? grid.count / grid.total : 0; }

    // Grid over the WHOLE canvas, used only by the legacy fallback (config unmatched).
    const fallbackGrid = _buildGrid({ x: 0, y: 0, w: W, h: H }, null);

    // Precompute each zone's Path2D + coverage grid once it is known.
    function _prepZones() {
        if (!zones) return;
        for (const z of zones) { z._path = _zonePath(z); z._grid = _buildGrid(_bounds(z), z); }
    }

    // Lift a zone's coating instantly (no animation) — shared by the reduced-motion path, revealAll(),
    // and the dissolve's final settle.
    function _clearZone(z) {
        ctx.save();
        ctx.globalCompositeOperation = 'destination-out';
        ctx.globalAlpha = 1;
        ctx.fill(z._path);
        ctx.restore();
    }
    // Finalise a zone: ensure it is fully clear, record it, announce 'zonereveal', and fire onReveal once
    // every zone is open. Idempotent so an interrupted dissolve (or revealAll mid-flight) is safe.
    function _completeZone(z) {
        dissolving.delete(z.id);
        if (revealedZones.has(z.id)) return;
        _clearZone(z);
        revealedZones.add(z.id);
        canvas.dispatchEvent(new CustomEvent('zonereveal', {
            detail: { zoneId: z.id, revealed: revealedZones.size, total: zones.length },
        }));
        if (zones.length && revealedZones.size >= zones.length && !revealed) {
            revealed = true;
            onReveal();
        }
    }
    // Animate a zone's remaining coating out over ~220ms: each frame removes an ease-out slice via a
    // clipped destination-out fill (incremental alpha, so the leftover multiplies down to nothing), THEN
    // completes. Under reduced motion it clears instantly, exactly as before.
    function _dissolveZone(z) {
        if (revealedZones.has(z.id) || dissolving.has(z.id)) return;
        if (reducedMotion()) { _completeZone(z); return; }
        dissolving.add(z.id);
        const start = performance.now();
        let prevE = 0;
        const frame = (now) => {
            const t = Math.min(1, (now - start) / 220);
            const e = 1 - (1 - t) * (1 - t);                 // easeOutQuad: fraction removed so far
            const a = e >= 1 ? 1 : (e - prevE) / (1 - prevE); // slice of the *remaining* coating this frame
            prevE = e;
            ctx.save();
            ctx.globalCompositeOperation = 'destination-out';
            ctx.globalAlpha = a;
            ctx.fill(z._path);
            ctx.restore();
            if (t < 1 && dissolving.has(z.id)) requestAnimationFrame(frame);
            else _completeZone(z);
        };
        requestAnimationFrame(frame);
    }

    // Legacy fallback reveal: dissolve (or, reduced-motion, clear) the whole surface once, then fire.
    function _completeFallback() {
        fallbackDissolving = false;
        if (revealed) return;
        ctx.globalCompositeOperation = 'source-over';
        ctx.clearRect(0, 0, W, H);
        revealed = true;
        onReveal();
    }
    function _dissolveFallback() {
        if (revealed || fallbackDissolving) return;
        if (reducedMotion()) { _completeFallback(); return; }
        fallbackDissolving = true;
        const start = performance.now();
        let prevE = 0;
        const frame = (now) => {
            const t = Math.min(1, (now - start) / 220);
            const e = 1 - (1 - t) * (1 - t);
            const a = e >= 1 ? 1 : (e - prevE) / (1 - prevE);
            prevE = e;
            ctx.save();
            ctx.globalCompositeOperation = 'destination-out';
            ctx.globalAlpha = a;
            ctx.fillRect(0, 0, W, H);
            ctx.restore();
            if (t < 1) requestAnimationFrame(frame);
            else _completeFallback();
        };
        requestAnimationFrame(frame);
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
                if (!zones) { zonesFailed = true; return; } // unmatched PNG => legacy whole-surface scratch
                _prepZones();
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

    // Map client coords to canvas coords, accounting for CSS scaling. W/H are logical, so this yields
    // logical coordinates regardless of the DPR-scaled backing store.
    function _pos(e) {
        const r = canvas.getBoundingClientRect();
        return {
            x: (e.clientX - r.left) * (W / r.width),
            y: (e.clientY - r.top) * (H / r.height),
        };
    }

    // ----- Per-frame batching -------------------------------------------------------------------
    // Incoming points queue here (each tagged with its zone + pressure) and flush once per animation
    // frame. Interpolating segments into ≤ step-spaced stamps gives even coverage and natural edges,
    // and batching means one save/clip/restore per zone per frame instead of per point.
    let pending = [];
    let flushScheduled = false;
    let lastVibe = 0;

    function _scheduleFlush() {
        if (!flushScheduled) { flushScheduled = true; requestAnimationFrame(_flush); }
    }
    // Queue the stamps for the segment from `last` to `p` (or just `p` when the line was broken), each at
    // ≤ step spacing so no fat single line — a run of soft stamps instead.
    function _enqueueSegment(p, zone, pressure) {
        if (last) {
            const dx = p.x - last.x, dy = p.y - last.y;
            const n = Math.max(1, Math.ceil(Math.hypot(dx, dy) / step));
            for (let i = 1; i <= n; i++) {
                const f = i / n;
                pending.push({ x: last.x + dx * f, y: last.y + dy * f, zone, pressure });
            }
        } else {
            pending.push({ x: p.x, y: p.y, zone, pressure });
        }
        last = { x: p.x, y: p.y };
    }
    // Throttled, touch-only haptic tick on erase (≥90ms apart), best-effort.
    function _maybeVibrate() {
        if (pointerType !== 'touch') return;
        const now = performance.now();
        if (now - lastVibe < 90) return;
        lastVibe = now;
        try { navigator.vibrate && navigator.vibrate(8); } catch (_) { /* unsupported */ }
    }
    // Announce at most two representative points per flushed frame as 'scratchstroke' so the UI's foil
    // shavings + scratch sound keep a cadence close to the old every-other-move rate.
    function _emitStrokes(pts) {
        if (!pts.length) return;
        const reps = pts.length === 1 ? [pts[0]] : [pts[0], pts[(pts.length / 2) | 0]];
        for (const pt of reps) {
            canvas.dispatchEvent(new CustomEvent('scratchstroke', { detail: { x: pt.x, y: pt.y } }));
        }
    }
    // Flush the queue: group points by zone, then per zone do ONE clipped destination-out pass stamping
    // every point (marking the coverage grid as we go). A zone that crosses 70% dissolves out.
    function _flush() {
        flushScheduled = false;
        if (!pending.length || revealed) { pending = []; return; }
        const pts = pending;
        pending = [];
        const groups = new Map();
        for (const pt of pts) {
            const key = pt.zone ? pt.zone.id : '__all__';
            let g = groups.get(key);
            if (!g) { g = { zone: pt.zone, points: [] }; groups.set(key, g); }
            g.points.push(pt);
        }
        for (const g of groups.values()) {
            const z = g.zone;
            if (z && (revealedZones.has(z.id) || dissolving.has(z.id))) continue;
            if (!z && (revealed || fallbackDissolving)) continue;
            const grid = z ? z._grid : fallbackGrid;
            ctx.save();
            if (z) ctx.clip(z._path); // only this zone's coating lifts
            ctx.globalCompositeOperation = 'destination-out';
            for (const pt of g.points) {
                const r = _radiusFor(pt.pressure);
                ctx.drawImage(stamp, pt.x - r, pt.y - r, r * 2, r * 2);
                _markGrid(grid, pt.x, pt.y, r);
            }
            ctx.restore();
            if (_fraction(grid) >= threshold) {
                if (z) _dissolveZone(z); else _dissolveFallback();
            }
        }
        _emitStrokes(pts);
        _maybeVibrate();
    }

    canvas.addEventListener('pointerdown', (e) => {
        if (!loaded || revealed) return;
        scratching = true;
        last = null;
        pointerType = e.pointerType || 'mouse';
        try { canvas.setPointerCapture(e.pointerId); } catch (_) { /* unsupported */ }
        const p = _pos(e);
        const pressure = (e.pressure > 0 && e.pressure < 1) ? e.pressure : 0;
        if (zonesFailed) { lastZoneId = null; _enqueueSegment(p, null, pressure); _scheduleFlush(); return; } // config unmatched: whole-surface
        if (!zones) return; // zones still loading: wait rather than scratch the whole (unclipped) surface
        const zone = _zoneAt(p);
        lastZoneId = zone ? zone.id : null;
        if (zone) { _enqueueSegment(p, zone, pressure); _scheduleFlush(); } // outside any zone: nothing scratches
    });
    canvas.addEventListener('pointermove', (e) => {
        if (!scratching || revealed) return;
        pointerType = e.pointerType || 'mouse';
        // Coalesced events recover the sub-frame points the browser merged into this one, so fast drags
        // stay continuous; each is stamped, with interpolation filling the gaps between them.
        const events = e.getCoalescedEvents ? e.getCoalescedEvents() : null;
        const evs = (events && events.length) ? events : [e];
        for (const ce of evs) {
            const p = _pos(ce);
            const pressure = (ce.pressure > 0 && ce.pressure < 1) ? ce.pressure : 0;
            if (zonesFailed) { _enqueueSegment(p, null, pressure); continue; } // config unmatched: whole-surface
            if (!zones) return; // zones still loading
            const zone = _zoneAt(p);
            if (!zone) { last = null; lastZoneId = null; continue; } // between zones: don't scratch, break the line
            if (zone.id !== lastZoneId) { last = null; lastZoneId = zone.id; } // entered a new zone: no bridging stroke
            _enqueueSegment(p, zone, pressure);
        }
        _scheduleFlush();
    });
    const end = () => { if (!scratching) return; scratching = false; last = null; lastZoneId = null; };
    canvas.addEventListener('pointerup', end);
    canvas.addEventListener('pointercancel', () => { scratching = false; last = null; lastZoneId = null; });
    canvas.addEventListener('pointerleave', () => { scratching = false; last = null; lastZoneId = null; });

    return {
        /** Lifts all remaining coating at once (the "reveal everything" shortcut); fires onReveal. */
        revealAll() {
            if (revealed || !loaded) return;
            if (zones && zones.length) {
                for (const z of zones) if (!revealedZones.has(z.id)) _completeZone(z); // last zone fires onReveal
            } else {
                ctx.globalCompositeOperation = 'source-over';
                ctx.clearRect(0, 0, W, H);
                revealed = true;
                onReveal();
            }
        },
        reset() {
            revealed = false;
            scratching = false;
            last = null;
            lastZoneId = null;
            pending = [];
            flushScheduled = false;
            fallbackDissolving = false;
            revealedZones.clear();
            dissolving.clear();
            if (zones) for (const z of zones) { if (z._grid) { z._grid.mark.fill(0); z._grid.count = 0; } }
            fallbackGrid.mark.fill(0);
            fallbackGrid.count = 0;
            paint(); // re-coat; the reveal-layer (numbers or seal tiles) stays as it was rendered
        },
    };
}
