/* LuckLedger SPA: hash-routed views over the REST API, with a Canvas scratch flow. */

const TICKET_ART = {
    CELESTIAL_FORTUNE: 'assets/tickets/celestial.png',
    DEMON_SEAL: 'assets/tickets/demon.png',
};

const state = {
    player: null,            // PlayerDto
    pendingTicket: null,     // { ticketId, mechanic } awaiting scratch
    lastShop: null,          // dealerId of the shop last browsed (for "Buy another")
};

const view = document.getElementById('view');
const PLAYER_KEY = 'luckledger.playerId';

// ---- helpers ---------------------------------------------------------------

function toast(message, isError) {
    const el = document.getElementById('toast');
    el.textContent = message;
    el.className = 'toast' + (isError ? ' error' : '');
    el.hidden = false;
    clearTimeout(toast._t);
    toast._t = setTimeout(() => { el.hidden = true; }, 3200);
}

function money(n) {
    return Number(n).toLocaleString(undefined, { minimumFractionDigits: 0, maximumFractionDigits: 2 });
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

function renderPlayerBar() {
    const bar = document.getElementById('player-bar');
    const p = state.player;
    if (!p) { bar.innerHTML = ''; return; }
    bar.innerHTML = `
        <span class="player-chip">${escapeHtml(p.displayName)}</span>
        <span class="player-chip">Balance <strong>${money(p.coinBalance)}</strong> coins</span>
        <button class="btn secondary" id="borrow-btn">Borrow 100</button>`;
    document.getElementById('borrow-btn').onclick = async () => {
        try {
            state.player = await Api.borrow(p.playerId, 100);
            renderPlayerBar();
            toast('Borrowed 100 free coins from the bank.');
        } catch (e) { toast(e.message, true); }
    };
}

function escapeHtml(s) {
    return String(s).replace(/[&<>"']/g, (c) =>
        ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

// ---- views -----------------------------------------------------------------

/** Owner initials for the placeholder avatar: "Old Chen" → "OC", "Sam" → "S". */
function initials(name) {
    const parts = String(name || '').trim().split(/\s+/).filter(Boolean);
    return (parts.slice(0, 2).map((p) => p[0]).join('') || '?').toUpperCase();
}

/** A real avatar image if present, otherwise an initials circle. */
function avatarHtml(dealer) {
    if (dealer.avatar) {
        return `<img class="shop-avatar" src="${escapeHtml(dealer.avatar)}" alt="${escapeHtml(dealer.ownerName)}">`;
    }
    return `<span class="shop-avatar shop-avatar--initials">${escapeHtml(initials(dealer.ownerName))}</span>`;
}

async function renderDealers() {
    view.innerHTML = `<div class="section-title"><h2>Shops</h2>
        <span class="hint">NPC storefronts, each run by an owner. Tap a shop to see what it stocks.</span></div>
        <div class="grid" id="grid"><p class="empty">Loading…</p></div>`;
    try {
        const dealers = await Api.dealers();
        const grid = document.getElementById('grid');
        if (!dealers.length) { grid.innerHTML = `<p class="empty">No shops seeded.</p>`; return; }
        grid.innerHTML = dealers.map((d) => `
            <div class="card shop-card" data-dealer="${d.dealerId}" role="button" tabindex="0">
                <div class="shop-head">
                    ${avatarHtml(d)}
                    <div>
                        <h3>${escapeHtml(d.shopName)}</h3>
                        <p class="shop-owner">${escapeHtml(d.ownerName)}</p>
                    </div>
                </div>
                <div class="badges">
                    ${d.games.map((g) => `<span class="badge">${escapeHtml(g.gameName)}</span>`).join('')
                        || '<span class="badge muted">No games</span>'}
                </div>
                <div class="meta">
                    <span>Tier <b>${d.tier}</b></span>
                    <span>Active books <b>${d.activeBooks}</b></span>
                </div>
            </div>`).join('');
        grid.querySelectorAll('[data-dealer]').forEach((el) => {
            const open = () => { location.hash = `#dealer/${el.dataset.dealer}`; };
            el.addEventListener('click', open);
            el.addEventListener('keydown', (e) => {
                if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); open(); }
            });
        });
    } catch (e) { view.querySelector('#grid').innerHTML = `<p class="empty">${escapeHtml(e.message)}</p>`; }
}

/** A single shop's storefront: its books grouped by game, each buyable. */
async function renderDealerBooks(dealerId) {
    state.lastShop = dealerId; // so "Buy another" returns to this shop
    view.innerHTML = `<div class="section-title"><h2>Shop</h2>
        <span class="hint"><a href="#dealer">← All shops</a></span></div>
        <div id="shop-body"><p class="empty">Loading…</p></div>`;
    try {
        const [dealer, mine] = await Promise.all([Api.dealer(dealerId), Api.dealerBooks(dealerId)]);
        const body = document.getElementById('shop-body');

        let html = `<div class="shop-detail-head">
            ${avatarHtml(dealer)}
            <div><h2>${escapeHtml(dealer.shopName)}</h2>
                <p class="shop-owner">Run by ${escapeHtml(dealer.ownerName)}</p></div></div>`;

        if (!mine.length) {
            body.innerHTML = html + `<p class="empty">This shop has no books in stock right now.</p>`;
            return;
        }

        const byGame = new Map();
        for (const b of mine) {
            if (!byGame.has(b.gameId)) byGame.set(b.gameId, { name: b.gameName, books: [] });
            byGame.get(b.gameId).books.push(b);
        }
        for (const group of byGame.values()) {
            group.books.sort((a, b) => a.bookId.localeCompare(b.bookId));
            html += `<section class="game-group">
                <h3 class="game-group-title">${escapeHtml(group.name)}</h3>
                <div class="grid">
                    ${group.books.map((b, i) => `
                        <div class="card">
                            <h3>Book #${i + 1}</h3>
                            <div class="meta">
                                <span>Tickets <b>${b.totalTickets}</b></span>
                                <span>Remaining <b>${b.ticketsRemaining}</b></span>
                            </div>
                            <button class="btn block" data-book="${b.bookId}" ${b.ticketsRemaining ? '' : 'disabled'}>
                                Buy &amp; Scratch</button>
                        </div>`).join('')}
                </div>
            </section>`;
        }
        body.innerHTML = html;
        body.querySelectorAll('button[data-book]').forEach((btn) => {
            btn.onclick = () => buyTicket(btn.dataset.book);
        });
    } catch (e) {
        const body = document.getElementById('shop-body');
        if (body) body.innerHTML = `<p class="empty">${escapeHtml(e.message)}</p>`;
    }
}

async function buyTicket(bookId) {
    if (!state.player) return;
    try {
        const result = await Api.purchase(bookId, state.player.playerId);
        await refreshPlayer();
        const ticket = await Api.ticket(result.ticketId);
        state.pendingTicket = { ticketId: ticket.ticketId, mechanic: ticket.mechanic };
        toast(`Bought a ticket for ${money(result.coinsDeducted)} coins. Scratch it!`);
        location.hash = '#scratch';
    } catch (e) {
        if (e.code === 'INSUFFICIENT_BALANCE') toast('Not enough coins — borrow some first.', true);
        else if (e.code === 'BOOK_DEPLETED') toast('That book is sold out.', true);
        else toast(e.message, true);
    }
}

// Scratch-zone config (coin circles, crystal cells, seals, …), loaded once. See config/scratch-zones.json.
let _zonesPromise = null;
function loadScratchZones() {
    if (!_zonesPromise) {
        _zonesPromise = fetch('config/scratch-zones.json').then((r) => r.json()).catch(() => null);
    }
    return _zonesPromise;
}

async function renderScratch() {
    const t = state.pendingTicket;
    if (!t) {
        view.innerHTML = `<div class="section-title"><h2>Scratch</h2></div>
            <p class="empty">No ticket in hand. Visit a <a href="#dealer">shop</a> and buy one.</p>`;
        return;
    }
    const art = TICKET_ART[t.mechanic] || TICKET_ART.CELESTIAL_FORTUNE;
    // Three layers (bottom→top): ticket art · reveal canvas (real numbers/seals) · metallic coating.
    view.innerHTML = `
        <div class="section-title"><h2>Scratch your ticket</h2>
            <span class="hint">${t.mechanic.replace('_', ' ')}</span></div>
        <div class="scratch-wrap">
            <div class="scratch-stage">
                <img class="scratch-art" src="${art}" alt="ticket art" draggable="false">
                <canvas id="reveal" class="scratch-canvas" width="360" height="640"></canvas>
                <canvas id="scratch" class="scratch-canvas" width="360" height="640"></canvas>
                <div id="banner" class="result-banner" hidden></div>
            </div>
            <p class="scratch-instructions">Scratch the silver panels to reveal what's underneath.</p>
            <button class="btn secondary" id="buy-another" hidden>Buy another</button>
        </div>`;

    const cfg = await loadScratchZones();
    const ticket = cfg && cfg.tickets && cfg.tickets[t.mechanic];
    const zones = ticket ? ticket.zones.filter((z) => z.scratch && z.shape !== 'path') : [];

    const foil = document.getElementById('scratch');
    const reveal = document.getElementById('reveal');
    const stage = view.querySelector('.scratch-stage');
    if (!foil || !reveal) return; // navigated away while loading

    // Reveal the outcome up front (server-side; idempotent) so the hidden numbers are the real ones.
    // The result banner / celebration is held back until the player has scratched enough.
    let outcome;
    try {
        outcome = await Api.reveal(t.ticketId, state.player.playerId);
    } catch (e) {
        outcome = await Api.ticket(t.ticketId).catch(() => ({ isWinner: false, prizeAmount: 0 }));
    }

    // Draw the real hidden symbols (from the engine grid) on the reveal layer, under the coating.
    const values = buildRevealValues(zones, t.mechanic, t.ticketId, outcome);
    drawHiddenNumbers(reveal, zones, values);

    const won = !!(outcome && outcome.isWinner) && Number(outcome.prizeAmount || 0) > 0;
    const onReveal = async () => {
        revealFlash(stage);
        if (won) launchConfetti(stage);
        showResult(outcome);
        await refreshPlayer();
    };
    new ScratchCard(foil, zones, onReveal);
}

/** A quick gold shine sweep across the ticket when the coating clears. */
function revealFlash(stage) {
    if (!stage) return;
    const flash = document.createElement('div');
    flash.className = 'reveal-flash';
    stage.appendChild(flash);
    setTimeout(() => flash.remove(), 700);
}

/** A short confetti burst over the ticket on a win (vanilla canvas particles, no assets). */
function launchConfetti(stage) {
    if (!stage) return;
    const canvas = document.createElement('canvas');
    canvas.className = 'confetti';
    const rect = stage.getBoundingClientRect();
    canvas.width = rect.width; canvas.height = rect.height;
    stage.appendChild(canvas);
    const ctx = canvas.getContext('2d');
    const colors = ['#f3c969', '#8b5cf6', '#4ade80', '#ffffff', '#e0796f'];
    const parts = Array.from({ length: 90 }, (_, i) => ({
        x: canvas.width / 2, y: canvas.height * 0.42,
        vx: (((i * 73) % 100) / 100 - 0.5) * 9,
        vy: -6 - (((i * 31) % 100) / 100) * 7,
        w: 4 + (i % 4) * 2, h: 6 + (i % 3) * 3,
        rot: i, vr: (((i * 17) % 100) / 100 - 0.5) * 0.5,
        color: colors[i % colors.length],
    }));
    let frame = 0;
    const tick = () => {
        frame++;
        ctx.clearRect(0, 0, canvas.width, canvas.height);
        for (const p of parts) {
            p.vy += 0.28; p.x += p.vx; p.y += p.vy; p.rot += p.vr;
            ctx.save();
            ctx.translate(p.x, p.y); ctx.rotate(p.rot);
            ctx.globalAlpha = Math.max(0, 1 - frame / 90);
            ctx.fillStyle = p.color;
            ctx.fillRect(-p.w / 2, -p.h / 2, p.w, p.h);
            ctx.restore();
        }
        if (frame < 90) requestAnimationFrame(tick);
        else canvas.remove();
    };
    requestAnimationFrame(tick);
}

// ---- hidden numbers (the reveal layer under the foil) ----------------------

/** Small deterministic PRNG so a ticket always shows the same numbers. */
function seededRandom(str) {
    let h = 1779033703 ^ str.length;
    for (let i = 0; i < str.length; i++) { h = Math.imul(h ^ str.charCodeAt(i), 3432918353); h = (h << 13) | (h >>> 19); }
    return function () { h = Math.imul(h ^ (h >>> 16), 2246822507); h = Math.imul(h ^ (h >>> 13), 3266489909); return ((h ^= h >>> 16) >>> 0) / 4294967296; };
}

const SEAL_DISPLAY = {
    GOLD: { text: 'GOLD', color: '#f3c969' },
    SILVER: { text: 'SILVER', color: '#d7dae3' },
    BROKEN: { text: 'BROKEN', color: '#e0796f' },
};

/**
 * The value to reveal under each zone, keyed by zone id → { text, color }. Uses the REAL engine grid
 * from the reveal response (zones are listed in grid row-major order; for Demon only the 6 seal cells
 * are used). Falls back to outcome-consistent generated numbers if the grid is unavailable.
 */
function buildRevealValues(zones, mechanic, ticketId, outcome) {
    const grid = outcome && outcome.grid;
    const out = {};
    if (grid && grid.length) {
        const cells = [...grid].sort((a, b) => a.row - b.row || a.col - b.col);
        if (mechanic === 'DEMON_SEAL') {
            const seals = cells.filter((c) => SEAL_DISPLAY[c.symbol]);
            zones.forEach((z, i) => { out[z.id] = seals[i] ? SEAL_DISPLAY[seals[i].symbol] : { text: '?', color: '#f3c969' }; });
        } else {
            // Celestial (row-major number game): scratch zone i ↔ grid cell i.
            zones.forEach((z, i) => { out[z.id] = { text: cells[i] ? cells[i].symbol : '?', color: '#f3c969' }; });
        }
        return out;
    }
    // Fallback: generated numbers consistent with win/loss (used only if the grid wasn't returned).
    const fab = numbersForTicket(zones, mechanic, ticketId, outcome);
    zones.forEach((z) => { out[z.id] = { text: String(fab[z.id]), color: '#f3c969' }; });
    return out;
}

/** Numbers for each zone, consistent with the outcome (a winner shows a match; a loser never does). */
function numbersForTicket(zones, mechanic, ticketId, outcome) {
    const rnd = seededRandom(ticketId + ':' + mechanic);
    const pick = (max) => 1 + Math.floor(rnd() * max);
    const won = !!(outcome && (outcome.isWinner ?? outcome.winner)) && Number(outcome.prizeAmount || 0) > 0;
    const out = {};

    if (mechanic === 'CELESTIAL_FORTUNE') {
        const winning = [];
        while (winning.length < 4) { const n = pick(60); if (!winning.includes(n)) winning.push(n); }
        zones.filter((z) => z.id.startsWith('win-')).forEach((z, i) => { out[z.id] = winning[i] ?? pick(60); });
        const crystals = zones.filter((z) => z.id.startsWith('num-'));
        const matchAt = won ? Math.floor(rnd() * crystals.length) : -1;
        crystals.forEach((z, i) => {
            if (i === matchAt) { out[z.id] = winning[Math.floor(rnd() * winning.length)]; return; }
            let n; do { n = pick(60); } while (winning.includes(n)); // never an accidental match
            out[z.id] = n;
        });
    } else {
        // Demon Seal and any other mechanic: a value per scratch zone; the banner is the official result.
        zones.forEach((z) => { out[z.id] = won ? '★' : pick(99); });
    }
    return out;
}

/** Paints each zone's hidden value (from `values`: id → {text,color}) on a plaque on the reveal canvas. */
function drawHiddenNumbers(canvas, zones, values) {
    const ctx = canvas.getContext('2d');
    const W = canvas.width, H = canvas.height;
    ctx.clearRect(0, 0, W, H);
    for (const z of zones) {
        const bb = z.shape === 'circle'
            ? { x: z.cx * W - z.r * W, y: z.cy * H - z.r * W, w: z.r * 2 * W, h: z.r * 2 * W }
            : { x: z.x * W, y: z.y * H, w: z.w * W, h: z.h * H };
        const cx = bb.x + bb.w / 2, cy = bb.y + bb.h / 2;
        const v = values[z.id] || { text: '?', color: '#f3c969' };

        ctx.save();
        ctx.beginPath();
        if (z.shape === 'circle') ctx.arc(cx, cy, Math.min(bb.w, bb.h) / 2, 0, Math.PI * 2);
        else { const r = Math.min(bb.w, bb.h) * 0.18, x = bb.x, y = bb.y, w = bb.w, h = bb.h;
            ctx.moveTo(x + r, y); ctx.arcTo(x + w, y, x + w, y + h, r); ctx.arcTo(x + w, y + h, x, y + h, r);
            ctx.arcTo(x, y + h, x, y, r); ctx.arcTo(x, y, x + w, y, r); ctx.closePath(); }
        ctx.fillStyle = '#140f22';
        ctx.fill();
        ctx.lineWidth = 2; ctx.strokeStyle = 'rgba(243,201,105,0.55)'; ctx.stroke();

        // Fit the text to the plaque width.
        const text = String(v.text);
        let fs = Math.round(Math.min(bb.w, bb.h) * 0.5);
        ctx.font = `700 ${fs}px "Segoe UI", system-ui, sans-serif`;
        while (fs > 7 && ctx.measureText(text).width > bb.w * 0.84) {
            fs -= 1;
            ctx.font = `700 ${fs}px "Segoe UI", system-ui, sans-serif`;
        }
        ctx.fillStyle = v.color || '#f3c969';
        ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
        ctx.fillText(text, cx, cy);
        ctx.restore();
    }
}

function showResult(outcome) {
    const banner = document.getElementById('banner');
    const won = outcome.isWinner && Number(outcome.prizeAmount) > 0;
    banner.className = 'result-banner show ' + (won ? 'win' : 'lose');
    banner.textContent = won ? `🎉 WIN ${money(outcome.prizeAmount)} coins!` : 'No win this time.';
    banner.hidden = false;
    const again = document.getElementById('buy-another');
    again.hidden = false;
    again.onclick = () => {
        state.pendingTicket = null;
        location.hash = state.lastShop ? `#dealer/${state.lastShop}` : '#dealer';
    };
    state.pendingTicket = null; // consumed
}

async function renderLedger() {
    if (!state.player) return;
    view.innerHTML = `<div class="section-title"><h2>Your ledger</h2>
        <span class="hint">Every coin movement, and what the numbers really say.</span></div>
        <div id="ledger-body"><p class="empty">Loading…</p></div>`;
    try {
        const [p, txns, insights] = await Promise.all([
            Api.getPlayer(state.player.playerId),
            Api.transactions(state.player.playerId, 25),
            Api.insights(state.player.playerId).catch(() => []),
        ]);
        const net = Number(p.netPosition);
        const body = document.getElementById('ledger-body');
        body.innerHTML = `
            <div class="stats">
                ${stat('Balance', money(p.coinBalance) + ' coins')}
                ${stat('Borrowed', money(p.totalBorrowed))}
                ${stat('Spent', money(p.totalSpent))}
                ${stat('Won', money(p.totalWon))}
                ${stat('Net position', (net >= 0 ? '+' : '') + money(net), net >= 0 ? 'good' : 'bad')}
            </div>
            ${insights.length ? `<h3>Insights</h3>${insights.map(renderInsight).join('')}` : ''}
            <h3>Recent transactions</h3>
            ${txns.length ? txnTable(txns) : '<p class="empty">No transactions yet — borrow and play.</p>'}`;
    } catch (e) {
        document.getElementById('ledger-body').innerHTML = `<p class="empty">${escapeHtml(e.message)}</p>`;
    }
}

function stat(label, value, cls) {
    return `<div class="stat"><div class="label">${label}</div>
        <div class="value ${cls || ''}">${value}</div></div>`;
}

function renderInsight(ins) {
    const title = ins.title || ins.headline || ins.type || 'Insight';
    const msg = ins.message || ins.description || ins.detail || '';
    return `<div class="insight"><h4>${escapeHtml(title)}</h4><p>${escapeHtml(msg)}</p></div>`;
}

function txnTable(txns) {
    const rows = txns.map((t) => `
        <tr>
            <td><span class="tag ${t.type}">${t.type}</span></td>
            <td>${money(t.amount)}</td>
            <td>${t.timestamp ? new Date(t.timestamp).toLocaleString() : ''}</td>
        </tr>`).join('');
    return `<table><thead><tr><th>Type</th><th>Amount</th><th>When</th></tr></thead><tbody>${rows}</tbody></table>`;
}

// ---- router ----------------------------------------------------------------

const ROUTES = { dealer: renderDealers, scratch: renderScratch, ledger: renderLedger };

function route() {
    const raw = location.hash.replace('#', '') || 'dealer';
    const [name, param] = raw.split('/');
    document.querySelectorAll('.tabs a').forEach((a) =>
        a.classList.toggle('active', a.dataset.route === name));
    if (name === 'dealer' && param) { renderDealerBooks(decodeURIComponent(param)); return; }
    (ROUTES[name] || renderDealers)();
}

async function init() {
    try {
        await ensurePlayer();
        renderPlayerBar();
    } catch (e) {
        toast('Could not reach the server: ' + e.message, true);
    }
    window.addEventListener('hashchange', route);
    if (!location.hash) location.hash = '#dealer';
    else route();
}

init();
