/* LuckLedger SPA — core module.
 * Shared constants, app state, the #view element ref, and cross-view helpers
 * (toast/money/escapeHtml/skeletonCards/stat, player bar, tilt + stage FX, game cache).
 * Load order is managed by index.html (this loads first, after api/scratch/sounds). */

/* LuckLedger SPA: hash-routed views over the REST API, with a Canvas scratch flow.
 *
 * The scratch view renders the ticket's REAL grid (served by the API once the ticket is revealed
 * server-side) underneath the PNG coating — the numbers and seals the player uncovers are the ones
 * generated and verified by the backend, not client-side decoration. */

const TICKET_ART = {
    CELESTIAL_FORTUNE: 'assets/tickets/celestial.png',
    DEMON_SEAL: 'assets/tickets/demon.png',
};

const MECHANIC_EMOJI = { CELESTIAL_FORTUNE: '🌟', DEMON_SEAL: '😈' };

/* The 6 demon-seal scratch zones, in the order the grid's seal cells (row-major) map onto them. */
const SEAL_ZONE_ORDER = [
    'seal-top', 'seal-upper-right', 'seal-lower-right', 'seal-bottom', 'seal-lower-left', 'seal-upper-left',
];
const SEAL_ICON = { GOLD: '✦', SILVER: '✧', BROKEN: '✗' };

const state = {
    player: null,            // PlayerDto
    pendingTicket: null,     // { ticketId, mechanic } awaiting scratch
    lastShop: null,          // dealerId of the shop last browsed (for "Buy another")
    master: null,            // { username } when the operator session is live
};

const view = document.getElementById('view');
const PLAYER_KEY = 'luckledger.playerId';
const PENDING_KEY = 'luckledger.pendingTicket';
const REDUCED_MOTION = window.matchMedia
    && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
// ---- helpers ---------------------------------------------------------------

function toast(message, isError) {
    const el = document.getElementById('toast');
    el.textContent = message;
    el.className = 'toast' + (isError ? ' error' : '');
    // Errors are assertive (role="alert"); ordinary notices are polite (role="status").
    el.setAttribute('role', isError ? 'alert' : 'status');
    el.hidden = false;
    clearTimeout(toast._t);
    toast._t = setTimeout(() => { el.hidden = true; }, 3200);
}

function money(n) {
    return Number(n).toLocaleString(undefined, { minimumFractionDigits: 0, maximumFractionDigits: 2 });
}

/* The pending ticket survives refreshes: it is mirrored to localStorage until its result is shown. */
function setPendingTicket(ticket) {
    state.pendingTicket = ticket;
    if (ticket) localStorage.setItem(PENDING_KEY, JSON.stringify(ticket));
    else localStorage.removeItem(PENDING_KEY);
}

function loadPendingTicket() {
    if (state.pendingTicket) return state.pendingTicket;
    try {
        const stored = JSON.parse(localStorage.getItem(PENDING_KEY));
        if (stored && stored.ticketId) { state.pendingTicket = stored; return stored; }
    } catch (e) { localStorage.removeItem(PENDING_KEY); }
    return null;
}

async function refreshPlayer() {
    if (!state.player) return;
    state.player = await Api.getPlayer(state.player.playerId);
    renderPlayerBar();
}

async function ensurePlayer() {
    const stored = localStorage.getItem(PLAYER_KEY);
    if (stored) {
        try {
            state.player = await Api.getPlayer(stored);
            return;
        } catch (e) {
            localStorage.removeItem(PLAYER_KEY); // stale id (e.g. DB reset)
        }
    }
    state.player = await Api.createPlayer('Guest Player');
    localStorage.setItem(PLAYER_KEY, state.player.playerId);
}

let _lastBalance = null;

function renderPlayerBar() {
    const bar = document.getElementById('player-bar');
    const p = state.player;
    if (!p) { bar.innerHTML = ''; return; }
    const balance = Number(p.coinBalance);
    const moved = _lastBalance !== null && balance !== _lastBalance;
    const dir = moved && balance > _lastBalance ? 'up' : 'down';
    _lastBalance = balance;
    bar.innerHTML = `
        <span class="player-chip">${escapeHtml(p.displayName)}</span>
        <span class="player-chip balance${moved ? ` bump ${dir}` : ''}">Balance
            <strong>${money(p.coinBalance)}</strong> coins</span>
        <button class="btn secondary" id="borrow-btn">Borrow 100</button>
        <button class="btn secondary" id="sound-btn" title="Toggle sound" aria-label="Toggle sound"
            aria-pressed="${Sounds.enabled()}">${Sounds.enabled() ? '🔊' : '🔇'}</button>`;
    document.getElementById('borrow-btn').onclick = async () => {
        try {
            state.player = await Api.borrow(p.playerId, 100);
            renderPlayerBar();
            toast('Borrowed 100 free coins from the bank.');
        } catch (e) { toast(e.message, true); }
    };
    const soundBtn = document.getElementById('sound-btn');
    if (soundBtn) soundBtn.onclick = () => {
        const enabled = Sounds.toggle();
        soundBtn.textContent = enabled ? '🔊' : '🔇';
        soundBtn.setAttribute('aria-pressed', String(enabled));
    };
}

function escapeHtml(s) {
    return String(s).replace(/[&<>"']/g, (c) =>
        ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

/** Shimmering placeholder cards shown while a list view loads. */
function skeletonCards(n) {
    return `<div class="grid">${Array.from({ length: n }, () => `
        <div class="card skel">
            <div class="skel-line w60"></div>
            <div class="skel-line w90"></div>
            <div class="skel-line w40"></div>
        </div>`).join('')}</div>`;
}

/* Gentle 3D tilt toward the pointer. The card snaps flat the instant a scratch stroke starts so
   canvas coordinates stay exact. Skipped entirely under prefers-reduced-motion. */
function attachTilt(el) {
    if (REDUCED_MOTION) return;
    const set = (rx, ry) => {
        el.style.setProperty('--rx', `${rx}deg`);
        el.style.setProperty('--ry', `${ry}deg`);
    };
    const flat = () => set(0, 0);
    el.addEventListener('pointermove', (e) => {
        if (e.buttons) { flat(); return; } // mid-scratch: keep the card flat
        const r = el.getBoundingClientRect();
        const px = (e.clientX - r.left) / r.width;
        const py = (e.clientY - r.top) / r.height;
        set((py - 0.5) * -7, (px - 0.5) * 9);
    });
    el.addEventListener('pointerdown', flat);
    el.addEventListener('pointerleave', flat);
}

/* Stage particle effects (scratchall-style): silver foil shavings thrown off the pointer while
   scratching, and a small glint burst when a panel's coating clears. One shared rAF loop that
   sleeps whenever no particle is alive. */
function createStageFx(fxCanvas) {
    if (REDUCED_MOTION || !fxCanvas) return { shavings() {}, burst() {} };
    const ctx = fxCanvas.getContext('2d');
    const W = fxCanvas.width;
    const H = fxCanvas.height;
    const FOIL = ['#cfd3dc', '#aab0bd', '#e8ebf2', '#d9c989'];
    const parts = [];
    let raf = null;
    function loop() {
        ctx.clearRect(0, 0, W, H);
        for (let i = parts.length - 1; i >= 0; i--) {
            const p = parts[i];
            p.x += p.vx; p.y += p.vy; p.vy += p.g; p.life--;
            if (p.life <= 0) { parts.splice(i, 1); continue; }
            const a = Math.min(1, p.life / 18);
            ctx.globalAlpha = a;
            if (p.kind === 'glint') { // a 4-point star that shrinks as it fades
                const s = p.s * a;
                ctx.strokeStyle = p.col;
                ctx.lineWidth = 1.5;
                ctx.beginPath();
                ctx.moveTo(p.x - s, p.y); ctx.lineTo(p.x + s, p.y);
                ctx.moveTo(p.x, p.y - s); ctx.lineTo(p.x, p.y + s);
                ctx.stroke();
            } else { // a foil fleck
                ctx.fillStyle = p.col;
                ctx.fillRect(p.x, p.y, p.s, p.s * 0.7);
            }
        }
        ctx.globalAlpha = 1;
        raf = parts.length ? requestAnimationFrame(loop) : null;
    }
    const wake = () => { if (!raf) raf = requestAnimationFrame(loop); };
    return {
        shavings(x, y) {
            for (let i = 0; i < 3; i++) {
                parts.push({
                    kind: 'fleck', x, y,
                    vx: (Math.random() - 0.5) * 2.4, vy: -0.5 - Math.random() * 1.5, g: 0.12,
                    s: 1.5 + Math.random() * 2.5,
                    col: FOIL[(Math.random() * FOIL.length) | 0],
                    life: 24 + Math.random() * 20,
                });
            }
            if (parts.length > 240) parts.splice(0, parts.length - 240); // cap the system
            wake();
        },
        burst(x, y) {
            for (let i = 0; i < 6; i++) {
                const ang = Math.random() * Math.PI * 2;
                const speed = 0.5 + Math.random() * 1.5;
                parts.push({
                    kind: 'glint',
                    x: x + Math.cos(ang) * 6, y: y + Math.sin(ang) * 6,
                    vx: Math.cos(ang) * speed, vy: Math.sin(ang) * speed, g: 0,
                    s: 3 + Math.random() * 4,
                    col: i % 2 ? '#ffe9a8' : '#ffffff',
                    life: 22 + Math.random() * 14,
                });
            }
            wake();
        },
    };
}

/* Ambient twinkles: little stars that wink at random spots on the ticket, scratchall-style. The
   interval kills itself once the stage leaves the DOM. */
function startTwinkles(stage) {
    if (REDUCED_MOTION || !stage) return;
    const spawn = () => {
        if (!stage.isConnected) { clearInterval(timer); return; }
        const tw = document.createElement('span');
        tw.className = 'twinkle';
        tw.textContent = '✦';
        tw.style.left = `${8 + Math.random() * 84}%`;
        tw.style.top = `${6 + Math.random() * 88}%`;
        tw.style.fontSize = `${8 + Math.random() * 10}px`;
        stage.appendChild(tw);
        setTimeout(() => tw.remove(), 1300);
    };
    const timer = setInterval(spawn, 650);
    spawn();
}

/** A short confetti burst inside the scratch stage — the win moment. */
function burstConfetti(stage) {
    if (REDUCED_MOTION || !stage) return;
    const c = document.createElement('canvas');
    c.className = 'confetti';
    c.width = stage.clientWidth || 360;
    c.height = stage.clientHeight || 640;
    stage.appendChild(c);
    const ctx = c.getContext('2d');
    const COLORS = ['#f3c969', '#ffd700', '#8b5cf6', '#ffffff', '#4ade80'];
    const parts = Array.from({ length: 90 }, () => ({
        x: c.width / 2, y: c.height * 0.55,
        vx: (Math.random() - 0.5) * 9, vy: -4 - Math.random() * 7,
        s: 3 + Math.random() * 4, rot: Math.random() * Math.PI,
        vr: (Math.random() - 0.5) * 0.3,
        col: COLORS[(Math.random() * COLORS.length) | 0],
        life: 70 + Math.random() * 50,
    }));
    let frame = 0;
    (function tick() {
        frame++;
        ctx.clearRect(0, 0, c.width, c.height);
        let alive = 0;
        for (const p of parts) {
            if (frame > p.life) continue;
            alive++;
            p.x += p.vx; p.y += p.vy; p.vy += 0.22; p.rot += p.vr;
            ctx.save();
            ctx.translate(p.x, p.y);
            ctx.rotate(p.rot);
            ctx.globalAlpha = Math.max(0, 1 - frame / p.life);
            ctx.fillStyle = p.col;
            ctx.fillRect(-p.s / 2, -p.s / 2, p.s, p.s * 0.6);
            ctx.restore();
        }
        if (alive) requestAnimationFrame(tick); else c.remove();
    })();
}
/** Cached GET /api/games (name, price, RTP, top prize) — used by the hero strip and book cards. */
let _gamesCache = null;
async function loadGames() {
    if (!_gamesCache) _gamesCache = await Api.games();
    return _gamesCache;
}
function stat(label, value, cls) {
    return `<div class="stat"><div class="label">${label}</div>
        <div class="value ${cls || ''}">${value}</div></div>`;
}
