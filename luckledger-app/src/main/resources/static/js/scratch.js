/* Canvas scratch surface. The ticket art lives in a sibling <img> *behind* the canvas (see
 * renderScratch in app.js); this canvas holds only an opaque metallic coating. Pointer events erase
 * the coating with `globalCompositeOperation = 'destination-out'`, so the art shows through wherever
 * the player scratches. Once enough coating is gone it fires `onReveal` exactly once (the caller then
 * hits the reveal API). Erasing uses a wide round-capped brush with line interpolation between pointer
 * events, so a drag clears a continuous swathe rather than disconnected dots. */

class ScratchCard {
    constructor(canvas, onReveal) {
        this.canvas = canvas;
        this.ctx = canvas.getContext('2d', { willReadFrequently: true });
        this.onReveal = onReveal;
        this.revealed = false;
        this.scratching = false;
        this.threshold = 0.5; // fraction of coating cleared before auto-reveal
        this.brush = 46;       // erase brush width in canvas px
        this.W = canvas.width;
        this.H = canvas.height;
        this.last = null;      // previous pointer position, for line interpolation

        this._coat();
        this._bind();
    }

    /** Lays down the metallic silver coating that hides the art beneath. */
    _coat() {
        const { ctx, W, H } = this;
        ctx.globalCompositeOperation = 'source-over';

        // brushed-metal base gradient
        const g = ctx.createLinearGradient(0, 0, W, H);
        g.addColorStop(0.0, '#b7b8c2');
        g.addColorStop(0.25, '#e9eaf0');
        g.addColorStop(0.5, '#9c9da9');
        g.addColorStop(0.75, '#d2d3db');
        g.addColorStop(1.0, '#8a8b96');
        ctx.fillStyle = g;
        ctx.fillRect(0, 0, W, H);

        // faint diagonal streaks for a brushed, metallic sheen
        ctx.strokeStyle = 'rgba(255,255,255,0.06)';
        ctx.lineWidth = 2;
        for (let x = -H; x < W; x += 7) {
            ctx.beginPath();
            ctx.moveTo(x, 0);
            ctx.lineTo(x + H, H);
            ctx.stroke();
        }

        // prompt text
        ctx.fillStyle = 'rgba(40,40,48,.55)';
        ctx.textAlign = 'center';
        ctx.font = '700 22px "Segoe UI", system-ui, sans-serif';
        ctx.fillText('SCRATCH HERE', W / 2, H / 2);
        ctx.font = '13px "Segoe UI", system-ui, sans-serif';
        ctx.fillText('drag to reveal your ticket', W / 2, H / 2 + 26);
    }

    _bind() {
        const c = this.canvas;
        c.addEventListener('pointerdown', (e) => {
            if (this.revealed) return;
            this.scratching = true;
            this.last = null;
            c.setPointerCapture(e.pointerId);
            this._stroke(this._pos(e));
        });
        c.addEventListener('pointermove', (e) => {
            if (!this.scratching || this.revealed) return;
            this._stroke(this._pos(e));
        });
        const end = () => { this.scratching = false; this.last = null; this._check(); };
        c.addEventListener('pointerup', end);
        c.addEventListener('pointercancel', end);
        c.addEventListener('pointerleave', () => { this.scratching = false; this.last = null; });
    }

    _pos(e) {
        const r = this.canvas.getBoundingClientRect();
        return {
            x: (e.clientX - r.left) * (this.W / r.width),
            y: (e.clientY - r.top) * (this.H / r.height),
        };
    }

    /** Erases a round dot at the point and, if dragging, a fat line from the previous point to it. */
    _stroke({ x, y }) {
        const { ctx } = this;
        ctx.globalCompositeOperation = 'destination-out';
        ctx.fillStyle = 'rgba(0,0,0,1)';
        ctx.strokeStyle = 'rgba(0,0,0,1)';
        ctx.lineWidth = this.brush;
        ctx.lineCap = 'round';
        ctx.lineJoin = 'round';

        if (this.last) {
            ctx.beginPath();
            ctx.moveTo(this.last.x, this.last.y);
            ctx.lineTo(x, y);
            ctx.stroke();
        }
        ctx.beginPath();
        ctx.arc(x, y, this.brush / 2, 0, Math.PI * 2);
        ctx.fill();

        ctx.globalCompositeOperation = 'source-over';
        this.last = { x, y };
    }

    _check() {
        if (this.revealed) return;
        if (this._clearedFraction() >= this.threshold) {
            this.revealed = true;
            this.onReveal();
        }
    }

    /** Fraction of pixels whose coating has been erased (alpha near zero), sampled coarsely. */
    _clearedFraction() {
        const { ctx, W, H } = this;
        const data = ctx.getImageData(0, 0, W, H).data;
        let cleared = 0, total = 0;
        for (let i = 3; i < data.length; i += 40) { // every 10th pixel (4 bytes each)
            total++;
            if (data[i] < 40) cleared++;
        }
        return total ? cleared / total : 0;
    }

    /** Clears all remaining coating (used once the result is shown). */
    finish() {
        this.revealed = true;
        this.ctx.globalCompositeOperation = 'destination-out';
        this.ctx.fillRect(0, 0, this.W, this.H);
        this.ctx.globalCompositeOperation = 'source-over';
    }
}
