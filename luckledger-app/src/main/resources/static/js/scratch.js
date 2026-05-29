/* Scratch card canvas engine — the ticket PNG IS the coating.
 *
 * The full ticket PNG is drawn onto this canvas; it is the silver foil. The player scratches anywhere on
 * it: erasing with destination-out lifts the PNG pixels, uncovering the dark reveal layer (with its
 * number labels) sitting in the DOM beneath the canvas. There is no per-zone clipping — the whole PNG is
 * the coating. When ~70% of the canvas has been scratched away, everything is cleared and `onReveal`
 * fires exactly once. */

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

    // The PNG itself is the coating: draw it to fill the whole canvas.
    const img = new Image();
    function paintCoating() {
        loaded = true;
        ctx.globalCompositeOperation = 'source-over';
        ctx.clearRect(0, 0, W, H);
        ctx.drawImage(img, 0, 0, W, H);
    }
    img.onload = paintCoating;
    img.src = pngPath;
    if (img.complete && img.naturalWidth) paintCoating(); // already cached

    // Map client coords to canvas coords, accounting for CSS scaling.
    function _pos(e) {
        const r = canvas.getBoundingClientRect();
        return {
            x: (e.clientX - r.left) * (W / r.width),
            y: (e.clientY - r.top) * (H / r.height),
        };
    }

    // Erase a round dot and, while dragging, a continuous fat line from the previous point.
    function _stroke({ x, y }) {
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
        last = { x, y };
    }

    // Sample the whole canvas; if >= 70% is transparent, clear everything and reveal once.
    function _check() {
        if (revealed) return;
        const data = ctx.getImageData(0, 0, W, H).data;
        let clear = 0;
        let total = 0;
        const step = 4 * 3; // every 3rd pixel, alpha channel only
        for (let i = 3; i < data.length; i += step) {
            total++;
            if (data[i] < 128) clear++;
        }
        if (total > 0 && clear / total >= threshold) {
            ctx.globalCompositeOperation = 'source-over';
            ctx.clearRect(0, 0, W, H);
            revealed = true;
            onReveal();
        }
    }

    canvas.addEventListener('pointerdown', (e) => {
        if (!loaded || revealed) return;
        scratching = true;
        last = null;
        try { canvas.setPointerCapture(e.pointerId); } catch (_) { /* unsupported */ }
        _stroke(_pos(e));
    });
    canvas.addEventListener('pointermove', (e) => {
        if (!scratching || revealed) return;
        _stroke(_pos(e));
        if (++moveCount % 10 === 0) _check();
    });
    const end = () => { if (!scratching) return; scratching = false; last = null; _check(); };
    canvas.addEventListener('pointerup', end);
    canvas.addEventListener('pointercancel', () => { scratching = false; last = null; });
    canvas.addEventListener('pointerleave', () => { scratching = false; last = null; });

    return {
        reset() {
            revealed = false;
            scratching = false;
            moveCount = 0;
            last = null;
            if (img.complete && img.naturalWidth) paintCoating();
        },
    };
}
