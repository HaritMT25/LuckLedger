/* LuckLedger SPA: hash-routed views over the REST API, with a Canvas scratch flow. */

const TICKET_ART = {
    CELESTIAL_FORTUNE: 'assets/tickets/celestial.png',
    DEMON_SEAL: 'assets/tickets/demon.png',
};

const state = {
    player: null,            // PlayerDto
    pendingTicket: null,     // { ticketId, mechanic } awaiting scratch
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

async function renderDealers() {
    view.innerHTML = `<div class="section-title"><h2>Dealers</h2>
        <span class="hint">NPC storefronts. Tier and quartile shape which book values they get.</span></div>
        <div class="grid" id="grid"><p class="empty">Loading…</p></div>`;
    try {
        const dealers = await Api.dealers();
        const container = document.getElementById('grid');
        if (!dealers.length) { container.innerHTML = `<p class="empty">No dealers seeded.</p>`; return; }

        // Group by game so the same-named storefronts from different games don't read as duplicates.
        const byGame = new Map();
        for (const d of dealers) {
            const key = d.gameId || 'unknown';
            if (!byGame.has(key)) byGame.set(key, { name: d.gameName || 'Game', dealers: [] });
            byGame.get(key).dealers.push(d);
        }

        container.classList.remove('grid');
        container.innerHTML = [...byGame.values()].map((group) => `
            <section class="game-group">
                <h3 class="game-group-title">${escapeHtml(group.name)}</h3>
                <div class="grid">
                    ${group.dealers.map((d) => `
                        <div class="card">
                            <h3>${escapeHtml(d.name)}</h3>
                            <div class="meta">
                                <span>Tier <b>${d.tier}</b></span>
                                <span>Quartile <b>${d.quartile}</b></span>
                                <span>Active books <b>${d.activeBooks}</b></span>
                                <span>Books depleted <b>${d.booksDepleted}</b></span>
                            </div>
                        </div>`).join('')}
                </div>
            </section>`).join('');
    } catch (e) { view.querySelector('#grid').innerHTML = `<p class="empty">${escapeHtml(e.message)}</p>`; }
}

async function renderBooks() {
    view.innerHTML = `<div class="section-title"><h2>Books</h2>
        <span class="hint">Each book sells its tickets in a fixed order. Buy one to scratch it.</span></div>
        <div class="grid" id="grid"><p class="empty">Loading…</p></div>`;
    try {
        const books = await Api.books();
        const grid = document.getElementById('grid');
        if (!books.length) { grid.innerHTML = `<p class="empty">No books available.</p>`; return; }
        // Stable order (by id) so each book keeps the same friendly number across reloads.
        books.sort((a, b) => a.bookId.localeCompare(b.bookId));
        grid.innerHTML = books.map((b, i) => `
            <div class="card">
                <h3>Book #${i + 1}</h3>
                <div class="meta">
                    <span>Tickets <b>${b.totalTickets}</b></span>
                    <span>Remaining <b>${b.ticketsRemaining}</b></span>
                </div>
                <button class="btn block" data-book="${b.bookId}" ${b.ticketsRemaining ? '' : 'disabled'}>
                    Buy &amp; Scratch</button>
            </div>`).join('');
        grid.querySelectorAll('button[data-book]').forEach((btn) => {
            btn.onclick = () => buyTicket(btn.dataset.book);
        });
    } catch (e) { view.querySelector('#grid').innerHTML = `<p class="empty">${escapeHtml(e.message)}</p>`; }
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

function renderScratch() {
    const t = state.pendingTicket;
    if (!t) {
        view.innerHTML = `<div class="section-title"><h2>Scratch</h2></div>
            <p class="empty">No ticket in hand. Go to <a href="#book">Books</a> and buy one.</p>`;
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
            <p class="scratch-instructions">Drag across the silver coating to reveal the ticket.</p>
            <button class="btn secondary" id="buy-another" hidden>Buy another</button>
        </div>`;

    const card = new ScratchCard(document.getElementById('scratch'), async () => {
        try {
            const outcome = await Api.reveal(t.ticketId, state.player.playerId);
            showResult(outcome);
            card.finish();
            await refreshPlayer();
        } catch (e) { toast(e.message, true); }
    });
}

function showResult(outcome) {
    const banner = document.getElementById('banner');
    const won = outcome.isWinner && Number(outcome.prizeAmount) > 0;
    banner.className = 'result-banner ' + (won ? 'win' : 'lose');
    banner.textContent = won ? `🎉 WIN ${money(outcome.prizeAmount)} coins!` : 'No win this time.';
    banner.hidden = false;
    const again = document.getElementById('buy-another');
    again.hidden = false;
    again.onclick = () => { state.pendingTicket = null; location.hash = '#book'; };
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

const ROUTES = { dealer: renderDealers, book: renderBooks, scratch: renderScratch, ledger: renderLedger };

function route() {
    const name = (location.hash.replace('#', '') || 'dealer');
    document.querySelectorAll('.tabs a').forEach((a) =>
        a.classList.toggle('active', a.dataset.route === name));
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
