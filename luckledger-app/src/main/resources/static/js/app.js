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
    // Canvas resolution matches the ticket art's 1080×1920 (9:16) aspect, scaled down.
    view.innerHTML = `
        <div class="section-title"><h2>Scratch your ticket</h2>
            <span class="hint">${t.mechanic.replace('_', ' ')}</span></div>
        <div class="scratch-wrap">
            <div class="scratch-stage">
                <img class="scratch-art" src="${art}" alt="ticket art" draggable="false">
                <canvas id="scratch" class="scratch-canvas" width="360" height="640"></canvas>
                <div id="banner" class="result-banner" hidden></div>
            </div>
            <p class="scratch-instructions">Scratch each symbol to reveal it — they uncover independently.</p>
            <button class="btn secondary" id="buy-another" hidden>Buy another</button>
        </div>`;

    // Coat only the scratch zones for this ticket, so the rest of the art stays visible.
    const cfg = await loadScratchZones();
    const ticket = cfg && cfg.tickets && cfg.tickets[t.mechanic];
    const zones = ticket ? ticket.zones.filter((z) => z.scratch && z.shape !== 'path') : [];

    const canvas = document.getElementById('scratch');
    const artImg = document.querySelector('.scratch-art');
    if (!canvas || !artImg) return; // navigated away while loading

    const onAllScratched = async () => {
        try {
            const outcome = await Api.reveal(t.ticketId, state.player.playerId);
            showResult(outcome);
            await refreshPlayer();
        } catch (e) { toast(e.message, true); }
    };
    // The coating is lifted from the ticket art, so wait until the image has decoded.
    const start = () => new ScratchCard(canvas, artImg, zones, onAllScratched);
    if (artImg.complete && artImg.naturalWidth) start();
    else artImg.addEventListener('load', start, { once: true });
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
