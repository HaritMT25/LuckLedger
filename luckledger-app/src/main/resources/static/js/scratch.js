/* Canvas scratch surface. The ticket art lives in a sibling <img> behind the canvas (see renderScratch
 * in app.js); this canvas paints a metallic coating ONLY over the configured scratch zones (the coin
 * circles, crystal cells, seals, …), leaving the rest of the ticket art fully visible. Pointer events
 * erase the coating with `globalCompositeOperation = 'destination-out'`, revealing the symbol beneath.
 * Once enough of the coated area is cleared it fires `onReveal` once (the caller then hits the reveal
 * API). Erasing uses a wide round-capped brush with line interpolation, so a drag clears a swathe. */

class ScratchCard {
    /**
     * @param canvas  the overlay canvas (its width/height set the working resolution)
     * @param zones   scratch zones in image fractions: {shape:'circle',cx,cy,r} | {shape:'rect',x,y,w,h}
     * @param onReveal called once when enough coating is cleared
     */
    constructor(canvas, zones, onReveal) {
        this.canvas = canvas;
        this.ctx = canvas.getContext('2d', { willReadFrequently: true });
        this.zones = (zones && zones.length) ? zones : [{ shape: 'rect', x: 0, y: 0, w: 1, h: 1 }];
        this.onReveal = onReveal;
        this.revealed = false;
        this.scratching = false;
        this.threshold = 0.5;  // fraction of the COATED area cleared before auto-reveal
        this.brush = 38;
        this.W = canvas.width;
        this.H = canvas.height;
        this.last = null;
        this.initialCoated = 1;

        this._coat();
        this._bind();
    }

    /** Paints the metallic coating over each scratch zone; everything else stays transparent. */
    _coat() {
        const { ctx, W, H } = this;
        ctx.globalCompositeOperation = 'source-over';
        ctx.clearRect(0, 0, W, H);
        for (const z of this.zones) this._paintZone(z);
        this.initialCoated = Math.max(1, this._coatedCount());
    }

    _paintZone(z) {
        const { ctx, W, H } = this;
        const bb = this._bbox(z);
        ctx.save();
        ctx.beginPath();
        if (z.shape === 'circle') {
            ctx.arc(z.cx * W, z.cy * H, z.r * W, 0, Math.PI * 2);
        } else {
            this._roundRect(bb.x, bb.y, bb.w, bb.h, Math.min(bb.w, bb.h) * 0.18);
        }
        ctx.clip();

        // brushed-metal fill
        const g = ctx.createLinearGradient(bb.x, bb.y, bb.x + bb.w, bb.y + bb.h);
        g.addColorStop(0.0, '#aeb0bb');
        g.addColorStop(0.3, '#e6e7ee');
        g.addColorStop(0.55, '#9a9ca8');
        g.addColorStop(0.8, '#cdced6');
        g.addColorStop(1.0, '#868792');
        ctx.fillStyle = g;
        ctx.fillRect(bb.x, bb.y, bb.w, bb.h);

        // diagonal sheen streaks
        ctx.strokeStyle = 'rgba(255,255,255,0.07)';
        ctx.lineWidth = 2;
        for (let x = bb.x - bb.h; x < bb.x + bb.w; x += 6) {
            ctx.beginPath();
            ctx.moveTo(x, bb.y);
            ctx.lineTo(x + bb.h, bb.y + bb.h);
            ctx.stroke();
        }
        ctx.restore();
    }

    _bbox(z) {
        const { W, H } = this;
        if (z.shape === 'circle') {
            return { x: z.cx * W - z.r * W, y: z.cy * H - z.r * W, w: z.r * 2 * W, h: z.r * 2 * W };
        }
        return { x: z.x * W, y: z.y * H, w: z.w * W, h: z.h * H };
    }

    _roundRect(x, y, w, h, r) {
        const ctx = this.ctx;
        ctx.moveTo(x + r, y);
        ctx.arcTo(x + w, y, x + w, y + h, r);
        ctx.arcTo(x + w, y + h, x, y + h, r);
        ctx.arcTo(x, y + h, x, y, r);
        ctx.arcTo(x, y, x + w, y, r);
        ctx.closePath();
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

    /** Erases a round dot and, if dragging, a fat line from the previous point — coating only. */
    _stroke({ x, y }) {
        const { ctx } = this;
        ctx.globalCompositeOperation = 'destination-out';
        ctx.fillStyle = ctx.strokeStyle = 'rgba(0,0,0,1)';
        ctx.lineWidth = this.brush;
        ctx.lineCap = ctx.lineJoin = 'round';
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

    /** Count of still-coated sampled pixels (alpha above a small threshold). */
    _coatedCount() {
        const data = this.ctx.getImageData(0, 0, this.W, this.H).data;
        let coated = 0;
        for (let i = 3; i < data.length; i += 40) if (data[i] > 40) coated++;
        return coated;
    }

    /** Fraction of the originally-coated area that has been scratched away. */
    _clearedFraction() {
        return (this.initialCoated - this._coatedCount()) / this.initialCoated;
    }

    /** Clears all remaining coating (used once the result is shown). */
    finish() {
        this.revealed = true;
        this.ctx.globalCompositeOperation = 'destination-out';
        this.ctx.fillRect(0, 0, this.W, this.H);
        this.ctx.globalCompositeOperation = 'source-over';
    }
}
