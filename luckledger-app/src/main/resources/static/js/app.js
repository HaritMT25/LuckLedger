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

// Cached scratch-zone config (config/scratch-zones.json); loaded once and reused.
let _scratchZonesCache = null;
async function loadScratchZones() {
    if (_scratchZonesCache) return _scratchZonesCache;
    const res = await fetch('config/scratch-zones.json');
    _scratchZonesCache = await res.json();
    return _scratchZonesCache;
}

/* Small deterministic PRNG so a ticket always shows the same numbers across reveals/reloads. */
function seededRandom(str) {
    let h = 1779033703 ^ str.length;
    for (let i = 0; i < str.length; i++) { h = Math.imul(h ^ str.charCodeAt(i), 3432918353); h = (h << 13) | (h >>> 19); }
    return function () { h = Math.imul(h ^ (h >>> 16), 2246822507); h = Math.imul(h ^ (h >>> 13), 3266489909); return ((h ^= h >>> 16) >>> 0) / 4294967296; };
}

/* Per-zone display values, consistent with the outcome: a winner shows a match, a loser never does.
   Returns an array aligned with the given scratch zones. */
function numbersForTicket(zones, mechanic, ticketId, outcome) {
    const rnd = seededRandom(ticketId + ':' + mechanic);
    const pick = (max) => 1 + Math.floor(rnd() * max);
    const won = !!(outcome && (outcome.isWinner ?? outcome.winner)) && Number(outcome.prizeAmount || 0) > 0;

    if (mechanic === 'CELESTIAL_FORTUNE') {
        const byId = {};
        const winning = [];
        while (winning.length < 4) { const n = pick(60); if (!winning.includes(n)) winning.push(n); }
        const winZones = zones.filter((z) => z.id && z.id.startsWith('win-'));
        winZones.forEach((z, i) => { byId[z.id] = winning[i] ?? pick(60); });
        const crystals = zones.filter((z) => z.id && z.id.startsWith('num-'));
        const matchAt = won ? Math.floor(rnd() * crystals.length) : -1;
        crystals.forEach((z, i) => {
            if (i === matchAt) { byId[z.id] = winning[Math.floor(rnd() * winning.length)]; return; }
            let n; do { n = pick(60); } while (winning.includes(n)); // never an accidental match
            byId[z.id] = n;
        });
        return zones.map((z) => String(byId[z.id] ?? pick(60)));
    }
    // Demon Seal and any other mechanic: a value per scratch zone; the banner is the official result.
    return zones.map(() => (won ? '★' : String(pick(99))));
}

/* Renders each zone's hidden value as a DOM label, centred on its zone, in the dark reveal layer. */
function drawHiddenNumbers(layer, zones, mechanic, ticketId, outcome) {
    const values = numbersForTicket(zones, mechanic, ticketId, outcome);
    layer.innerHTML = '';
    zones.forEach((z, i) => {
        // Zone coords are fractions (0..1). Centre: circle → (cx, cy); rect → (x + w/2, y + h/2).
        const cx = z.shape === 'circle' ? z.cx : z.x + z.w / 2;
        const cy = z.shape === 'circle' ? z.cy : z.y + z.h / 2;
        const span = document.createElement('span');
        span.className = 'value-label';
        span.textContent = values[i];
        span.style.left = `${cx * 100}%`;
        span.style.top = `${cy * 100}%`;
        layer.appendChild(span);
    });
}

async function renderScratch() {
    const t = state.pendingTicket;
    if (!t) {
        view.innerHTML = `<div class="section-title"><h2>Scratch</h2></div>
            <p class="empty">No ticket in hand. Visit a <a href="#dealer">shop</a> and buy one.</p>`;
        return;
    }
    const art = TICKET_ART[t.mechanic] || TICKET_ART.CELESTIAL_FORTUNE;
    // The ticket PNG IS the coating: it is drawn onto the scratch canvas. Beneath the canvas sits the
    // dark reveal layer (a DOM div) holding one number label per scratch zone. Scratching the PNG away
    // uncovers those labels.
    view.innerHTML = `
        <div class="section-title"><h2>Scratch your ticket</h2>
            <span class="hint">${t.mechanic.replace('_', ' ')}</span></div>
        <div class="scratch-wrap">
            <div class="scratch-stage">
                <div id="reveal-layer" class="reveal-layer"></div>
                <canvas id="scratch" class="scratch-canvas" width="360" height="640"></canvas>
                <div id="banner" class="result-banner" hidden></div>
            </div>
            <p class="scratch-instructions">Scratch the ticket off to reveal the numbers underneath.</p>
            <button class="btn secondary" id="buy-another" hidden>Buy another</button>
        </div>`;

    const canvas = document.getElementById('scratch');
    const revealLayer = document.getElementById('reveal-layer');
    if (!canvas || !revealLayer) return; // navigated away while rendering

    // Scratch zones for this mechanic (fractions of width/height); skip non-scratch and path shapes.
    let zones = [];
    try {
        const cfg = await loadScratchZones();
        const ticket = cfg && cfg.tickets && cfg.tickets[t.mechanic];
        zones = ticket ? ticket.zones.filter((z) => z.scratch && z.shape !== 'path') : [];
    } catch (e) { zones = []; }
    if (!document.getElementById('reveal-layer')) return; // navigated away during the await

    // Reveal the outcome up front (server-side; idempotent) so the hidden numbers are consistent with
    // win/loss. The result banner is held back until the player has scratched the foil away.
    let outcome;
    try {
        outcome = await Api.reveal(t.ticketId, state.player.playerId);
    } catch (e) {
        outcome = await Api.ticket(t.ticketId).catch(() => ({ isWinner: false, prizeAmount: 0 }));
    }
    if (!document.getElementById('reveal-layer')) return; // navigated away during the await

    // Render the hidden numbers as DOM labels on the dark reveal layer (under the canvas).
    drawHiddenNumbers(revealLayer, zones, t.mechanic, t.ticketId, outcome);

    const onReveal = async () => {
        showResult(outcome);
        await refreshPlayer();
    };
    initScratch(canvas, art, onReveal);
}

function showResult(outcome) {
    const banner = document.getElementById('banner');
    const won = outcome.isWinner && Number(outcome.prizeAmount) > 0;
    banner.className = 'result-banner ' + (won ? 'win' : 'lose');
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
