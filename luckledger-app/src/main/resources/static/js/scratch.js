/* Canvas scratch surface. Draws the ticket art, lays an opaque coating over it, and erases the
 * coating under the pointer with `globalCompositeOperation = 'destination-out'`. Once enough of the
 * coating is gone it fires `onReveal` exactly once (the caller then hits the reveal API). */

class ScratchCard {
    constructor(canvas, imageSrc, onReveal) {
        this.canvas = canvas;
        this.ctx = canvas.getContext('2d', { willReadFrequently: true });
        this.onReveal = onReveal;
        this.revealed = false;
        this.scratching = false;
        this.threshold = 0.5; // fraction of coating cleared before auto-reveal
        this.W = canvas.width;
        this.H = canvas.height;

        const img = new Image();
        img.onload = () => this._init(img);
        img.onerror = () => this._init(null);
        img.src = imageSrc;
    }

    _init(img) {
        const { ctx, W, H } = this;
        // base layer: the ticket art (cover-fit), or a fallback gradient
        if (img) {
            const scale = Math.max(W / img.width, H / img.height);
            const w = img.width * scale, h = img.height * scale;
            ctx.drawImage(img, (W - w) / 2, (H - h) / 2, w, h);
        } else {
            const g = ctx.createLinearGradient(0, 0, W, H);
            g.addColorStop(0, '#241c42');
            g.addColorStop(1, '#0e0b16');
            ctx.fillStyle = g;
            ctx.fillRect(0, 0, W, H);
        }
        this._coat();
        this._bind();
    }

    _coat() {
        const { ctx, W, H } = this;
        const g = ctx.createLinearGradient(0, 0, W, H);
        g.addColorStop(0, '#9a9a9a');
        g.addColorStop(0.5, '#c4c4c4');
        g.addColorStop(1, '#8c8c8c');
        ctx.fillStyle = g;
        ctx.fillRect(0, 0, W, H);
        ctx.fillStyle = 'rgba(40,40,40,.55)';
        ctx.font = 'bold 22px "Segoe UI", sans-serif';
        ctx.textAlign = 'center';
        ctx.fillText('SCRATCH HERE', W / 2, H / 2);
        ctx.font = '13px "Segoe UI", sans-serif';
        ctx.fillText('drag to reveal your ticket', W / 2, H / 2 + 26);
    }

    _bind() {
        const c = this.canvas;
        const move = (e) => this._move(e);
        c.addEventListener('pointerdown', (e) => { this.scratching = true; c.setPointerCapture(e.pointerId); this._erase(e); });
        c.addEventListener('pointerup', () => { this.scratching = false; this._check(); });
        c.addEventListener('pointerleave', () => { this.scratching = false; });
        c.addEventListener('pointermove', move);
    }

    _pos(e) {
        const r = this.canvas.getBoundingClientRect();
        return {
            x: (e.clientX - r.left) * (this.W / r.width),
            y: (e.clientY - r.top) * (this.H / r.height),
        };
    }

    _erase(e) {
        const { ctx } = this;
        const { x, y } = this._pos(e);
        ctx.globalCompositeOperation = 'destination-out';
        ctx.beginPath();
        ctx.arc(x, y, 24, 0, Math.PI * 2);
        ctx.fill();
        ctx.globalCompositeOperation = 'source-over';
    }

    _move(e) {
        if (!this.scratching || this.revealed) return;
        this._erase(e);
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
