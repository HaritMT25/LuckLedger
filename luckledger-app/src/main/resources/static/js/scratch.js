/* Canvas scratch surface. The ticket art lives in a sibling <img> behind the canvas (see renderScratch
 * in app.js). Over each scratch zone (coin circles, crystal cells, talismans) this canvas paints a
 * *frosted copy of that symbol* taken from the ticket art itself — not a plain silver patch — so the
 * symbol is what you scratch. Pointer events erase the frost with destination-out, revealing the crisp
 * symbol beneath.
 *
 * Each zone is INDEPENDENT: it tracks its own scratched fraction, and reveals (snaps fully clear) only
 * itself once scratched past the threshold — scratching one symbol never uncovers the others. When
 * every zone has been revealed, `onReveal` fires once (the caller then hits the reveal API). */

class ScratchCard {
    /**
     * @param canvas  overlay canvas (its width/height set the working resolution)
     * @param img     the loaded ticket <img> (source for the frosted coating); may be null → plain frost
     * @param zones   scratch zones in image fractions: {shape:'circle',cx,cy,r} | {shape:'rect',x,y,w,h}
     * @param onReveal called once when every zone has been scratched clear
     */
    constructor(canvas, img, zones, onReveal) {
        this.canvas = canvas;
        this.ctx = canvas.getContext('2d', { willReadFrequently: true });
        this.img = img;
        this.zones = (zones && zones.length) ? zones : [{ shape: 'rect', x: 0, y: 0, w: 1, h: 1 }];
        this.onReveal = onReveal;
        this.revealed = false;
        this.scratching = false;
        this.zoneThreshold = 0.55; // fraction of a single zone cleared before it snaps fully revealed
        this.brush = 34;
        this.W = canvas.width;
        this.H = canvas.height;
        this.last = null;

        this._coat();
        this._bind();
    }

    /** Paints the frosted symbol coating over each zone and records each zone's initial coated area. */
    _coat() {
        const { ctx, W, H } = this;
        ctx.globalCompositeOperation = 'source-over';
        ctx.clearRect(0, 0, W, H);
        for (const z of this.zones) this._paintZone(z);

        const data = ctx.getImageData(0, 0, W, H).data;
        this.state = this.zones.map((z) => {
            const bb = this._bbox(z);
            return { z, bb, initial: Math.max(1, this._coatedIn(data, bb)), done: false };
        });
    }

    _paintZone(z) {
        const { ctx } = this;
        const bb = this._bbox(z);
        ctx.save();
        ctx.beginPath();
        if (z.shape === 'circle') ctx.arc(z.cx * this.W, z.cy * this.H, z.r * this.W, 0, Math.PI * 2);
        else this._roundRectPath(bb.x, bb.y, bb.w, bb.h, Math.min(bb.w, bb.h) * 0.18);
        ctx.clip();

        if (this.img && this.img.naturalWidth) {
            // Frosted copy of the symbol itself, lifted from the ticket art.
            const s = this._srcRect(z);
            ctx.filter = 'blur(2px) brightness(1.35) saturate(0.55)';
            ctx.drawImage(this.img, s.x, s.y, s.w, s.h, bb.x, bb.y, bb.w, bb.h);
            ctx.filter = 'none';
            ctx.fillStyle = 'rgba(226,232,240,0.42)'; // icy veil so it reads as "unscratched"
            ctx.fillRect(bb.x, bb.y, bb.w, bb.h);
        } else {
            const g = ctx.createLinearGradient(bb.x, bb.y, bb.x + bb.w, bb.y + bb.h);
            g.addColorStop(0, '#dfe1ea'); g.addColorStop(0.5, '#b6b8c4'); g.addColorStop(1, '#d2d4dc');
            ctx.fillStyle = g;
            ctx.fillRect(bb.x, bb.y, bb.w, bb.h);
        }

        // diagonal sheen streaks
        ctx.strokeStyle = 'rgba(255,255,255,0.10)';
        ctx.lineWidth = 2;
        for (let x = bb.x - bb.h; x < bb.x + bb.w; x += 6) {
            ctx.beginPath();
            ctx.moveTo(x, bb.y);
            ctx.lineTo(x + bb.h, bb.y + bb.h);
            ctx.stroke();
        }
        ctx.restore();
    }

    /** Source rectangle (natural image pixels) for a zone — what to lift from the ticket art. */
    _srcRect(z) {
        const nw = this.img.naturalWidth, nh = this.img.naturalHeight;
        if (z.shape === 'circle') {
            const rp = z.r * nw;
            return { x: z.cx * nw - rp, y: z.cy * nh - rp, w: rp * 2, h: rp * 2 };
        }
        return { x: z.x * nw, y: z.y * nh, w: z.w * nw, h: z.h * nh };
    }

    _bbox(z) {
        const { W, H } = this;
        if (z.shape === 'circle') {
            return { x: z.cx * W - z.r * W, y: z.cy * H - z.r * W, w: z.r * 2 * W, h: z.r * 2 * W };
        }
        return { x: z.x * W, y: z.y * H, w: z.w * W, h: z.h * H };
    }

    _roundRectPath(x, y, w, h, r) {
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

    /** Per-zone progress: a zone that crosses its threshold snaps fully clear — only itself. */
    _check() {
        if (this.revealed) return;
        const data = this.ctx.getImageData(0, 0, this.W, this.H).data;
        for (const st of this.state) {
            if (st.done) continue;
            const cur = this._coatedIn(data, st.bb);
            if ((st.initial - cur) / st.initial >= this.zoneThreshold) {
                st.done = true;
                this._clearZone(st.z); // reveal this zone in full, leaving the others coated
            }
        }
        if (this.state.every((s) => s.done)) {
            this.revealed = true;
            this.onReveal();
        }
    }

    /** Coated (alpha-bearing) sample count within a bounding box. */
    _coatedIn(data, bb) {
        const x0 = Math.max(0, Math.floor(bb.x)), x1 = Math.min(this.W, Math.ceil(bb.x + bb.w));
        const y0 = Math.max(0, Math.floor(bb.y)), y1 = Math.min(this.H, Math.ceil(bb.y + bb.h));
        let coated = 0;
        for (let y = y0; y < y1; y += 2) {
            for (let x = x0; x < x1; x += 2) {
                if (data[(y * this.W + x) * 4 + 3] > 40) coated++;
            }
        }
        return coated;
    }

    /** Fully clears a single zone's coating (its shape), revealing the symbol beneath. */
    _clearZone(z) {
        const { ctx } = this;
        const bb = this._bbox(z);
        ctx.globalCompositeOperation = 'destination-out';
        ctx.beginPath();
        if (z.shape === 'circle') ctx.arc(z.cx * this.W, z.cy * this.H, z.r * this.W, 0, Math.PI * 2);
        else this._roundRectPath(bb.x, bb.y, bb.w, bb.h, Math.min(bb.w, bb.h) * 0.18);
        ctx.fill();
        ctx.globalCompositeOperation = 'source-over';
    }
}
