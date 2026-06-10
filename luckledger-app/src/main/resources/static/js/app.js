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
};

const view = document.getElementById('view');
const PLAYER_KEY = 'luckledger.playerId';
const PENDING_KEY = 'luckledger.pendingTicket';

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

// ---- shops -----------------------------------------------------------------

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

/** A single shop's storefront: its books grouped by game, each buyable, with the price up front. */
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
                <h3 class="game-group-title">${MECHANIC_EMOJI[group.books[0].mechanic] || '🎟️'} ${escapeHtml(group.name)}</h3>
                <div class="grid">
                    ${group.books.map((b, i) => bookCard(b, i)).join('')}
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

/** One buyable book: price up front, a stock bar instead of bare counts. */
function bookCard(b, i) {
    const soldOut = !b.ticketsRemaining;
    const pct = b.totalTickets ? Math.round((b.ticketsRemaining / b.totalTickets) * 100) : 0;
    return `
        <div class="card book-card">
            <div class="book-head">
                <h3>Book #${i + 1}</h3>
                <span class="price-tag">${money(b.ticketPrice)} coins</span>
            </div>
            <p class="book-sub">${escapeHtml(b.gameName)} · per ticket</p>
            <div class="stock-bar" title="${b.ticketsRemaining} of ${b.totalTickets} tickets left">
                <div class="stock-fill" style="width:${pct}%"></div>
            </div>
            <p class="stock-label">${soldOut ? 'Sold out' : `${b.ticketsRemaining} of ${b.totalTickets} tickets left`}</p>
            <button class="btn block" data-book="${b.bookId}" ${soldOut ? 'disabled' : ''}>
                ${soldOut ? 'Sold out' : `Buy a ticket — ${money(b.ticketPrice)} coins`}</button>
        </div>`;
}

async function buyTicket(bookId) {
    if (!state.player) return;
    try {
        const result = await Api.purchase(bookId, state.player.playerId);
        await refreshPlayer();
        const ticket = await Api.ticket(result.ticketId);
        setPendingTicket({ ticketId: ticket.ticketId, mechanic: ticket.mechanic });
        toast(`Bought a ticket for ${money(result.coinsDeducted)} coins. Scratch it!`);
        location.hash = '#scratch';
    } catch (e) {
        if (e.code === 'INSUFFICIENT_BALANCE') toast('Not enough coins — borrow some first.', true);
        else if (e.code === 'BOOK_DEPLETED') toast('That book is sold out.', true);
        else toast(e.message, true);
    }
}

// ---- scratch ---------------------------------------------------------------

// Cached scratch-zone config (config/scratch-zones.json); loaded once and reused.
let _scratchZonesCache = null;
async function loadScratchZones() {
    if (_scratchZonesCache) return _scratchZonesCache;
    const res = await fetch('config/scratch-zones.json');
    _scratchZonesCache = await res.json();
    return _scratchZonesCache;
}

/* Cells of the served grid in row-major order (the layout contract both mechanics rely on). */
function cellsRowMajor(grid) {
    return [...grid.cells].sort((a, b) => a.row - b.row || a.col - b.col);
}

/* Celestial Fortune: row 0 holds the 4 winning numbers, rows 1–2 the player's 8 numbers, row 3 inert
   decoys. Maps each scratch zone (win-1..4, num-1..12) to its real value and flags actual matches. */
function celestialZoneValues(grid) {
    const cells = cellsRowMajor(grid);
    const winningCells = cells.filter((c) => c.row === 0);
    const winningSet = new Set(winningCells.map((c) => c.abstractSymbol));
    const byZone = {};
    winningCells.forEach((c, i) => {
        byZone[`win-${i + 1}`] = { text: c.displayLabel || c.abstractSymbol, kind: 'win' };
    });
    let matchCount = 0;
    cells.filter((c) => c.row >= 1).forEach((c, i) => {
        const matched = c.row <= 2 && winningSet.has(c.abstractSymbol); // decoy row (3) never matches
        if (matched) matchCount++;
        byZone[`num-${i + 1}`] = { text: c.displayLabel || c.abstractSymbol, kind: matched ? 'match' : 'plain' };
    });
    return { byZone, matchCount };
}

/* Demon Seal: the grid holds exactly 6 seal cells (GOLD/SILVER/BROKEN) among themed decoys. Maps the
   seals, row-major, onto the 6 seal zones and computes the score T = 2×gold + silver (win at T ≥ 4). */
function demonZoneSeals(grid) {
    const seals = cellsRowMajor(grid).filter((c) => SEAL_ICON[c.abstractSymbol]);
    const byZone = {};
    seals.forEach((c, i) => { if (SEAL_ZONE_ORDER[i]) byZone[SEAL_ZONE_ORDER[i]] = c.abstractSymbol; });
    const gold = seals.filter((c) => c.abstractSymbol === 'GOLD').length;
    const silver = seals.filter((c) => c.abstractSymbol === 'SILVER').length;
    return { byZone, gold, silver, score: 2 * gold + silver };
}

/* Renders the ticket's REAL hidden values into the dark reveal layer, one element per scratch zone. */
function drawRevealLayer(layer, zones, mechanic, gridData) {
    layer.innerHTML = '';
    for (const z of zones) {
        const cx = z.shape === 'circle' ? z.cx : z.x + z.w / 2;
        const cy = z.shape === 'circle' ? z.cy : z.y + z.h / 2;
        let el;
        if (mechanic === 'DEMON_SEAL') {
            const kind = gridData.byZone[z.id] || 'BROKEN';
            el = document.createElement('div');
            el.className = 'seal-reveal ' + kind.toLowerCase();
            el.innerHTML = `<span class="seal-icon">${SEAL_ICON[kind]}</span><span class="seal-label">${kind}</span>`;
        } else {
            const v = gridData.byZone[z.id] || { text: '?', kind: 'plain' };
            el = document.createElement('span');
            el.className = 'value-label' + (v.kind !== 'plain' ? ` ${v.kind}` : '');
            el.textContent = v.text;
        }
        el.style.left = `${cx * 100}%`;
        el.style.top = `${cy * 100}%`;
        layer.appendChild(el);
    }
}

/* Mechanic-specific outcome detail for the result panel, derived from the real grid. */
function outcomeDetail(mechanic, gridData, won) {
    if (mechanic === 'CELESTIAL_FORTUNE') {
        const n = gridData.matchCount;
        if (won) return `You matched ${n} of the 4 winning numbers.`;
        if (n === 1) return 'You matched 1 winning number — one short of a prize. That near-miss is by design.';
        return 'None of your numbers matched the winning row.';
    }
    if (mechanic === 'DEMON_SEAL') {
        const { gold, silver, score } = gridData;
        const tally = `✦×${gold} gold, ✧×${silver} silver — seal score ${score}`;
        if (won) return `${tally}. The demon is sealed.`;
        if (score === 3) return `${tally}. One point short of the 4 needed — that near-miss is by design.`;
        return `${tally}. You needed 4 to win.`;
    }
    return '';
}

async function renderScratch() {
    const t = loadPendingTicket();
    if (!t) { return renderPendingTickets(); }

    const art = TICKET_ART[t.mechanic] || TICKET_ART.CELESTIAL_FORTUNE;
    // The ticket PNG IS the coating: it is drawn onto the scratch canvas. Beneath the canvas sits the
    // dark reveal layer (a DOM div) holding the ticket's REAL values, one per scratch zone. Scratching
    // the PNG away uncovers them.
    view.innerHTML = `
        <div class="section-title"><h2>Scratch your ticket</h2>
            <span class="hint">${t.mechanic.replace(/_/g, ' ')}</span></div>
        <div class="scratch-wrap">
            <div class="scratch-stage">
                <div id="reveal-layer" class="reveal-layer"></div>
                <canvas id="scratch" class="scratch-canvas" width="360" height="640"></canvas>
                <div id="banner" class="result-banner" hidden></div>
            </div>
            <p class="scratch-instructions">Scratch each panel to uncover what this ticket was always going to be.</p>
            <div class="scratch-actions">
                <button class="btn secondary" id="buy-another" hidden>Buy another</button>
                <a class="btn secondary" id="see-ledger" href="#ledger" hidden>See your ledger</a>
            </div>
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

    // Reveal server-side up front (idempotent) — this is what serves the ticket's real grid. The
    // result banner is held back until the player has scratched the coating away.
    let outcome;
    try {
        outcome = await Api.reveal(t.ticketId, state.player.playerId);
    } catch (e) {
        outcome = await Api.ticket(t.ticketId).catch(() => null);
    }
    if (!document.getElementById('reveal-layer')) return; // navigated away during the await
    if (!outcome || !outcome.grid) {
        view.querySelector('.scratch-wrap').innerHTML =
            `<p class="empty">Could not load this ticket. <a href="#scratch">Try again</a>.</p>`;
        return;
    }

    const won = !!(outcome.isWinner ?? outcome.winner) && Number(outcome.prizeAmount || 0) > 0;
    const gridData = t.mechanic === 'DEMON_SEAL'
        ? demonZoneSeals(outcome.grid)
        : celestialZoneValues(outcome.grid);
    drawRevealLayer(revealLayer, zones, t.mechanic, gridData);

    const onReveal = async () => {
        showResult(outcome, won, outcomeDetail(t.mechanic, gridData, won));
        await refreshPlayer();
    };
    initScratch(canvas, art, onReveal);
}

/** No ticket in hand: offer any bought-but-unscratched tickets to resume, else point at the shops. */
async function renderPendingTickets() {
    view.innerHTML = `<div class="section-title"><h2>Scratch</h2></div>
        <div id="pending-body"><p class="empty">Loading…</p></div>`;
    let pending = [];
    try {
        if (state.player) pending = await Api.pendingTickets(state.player.playerId);
    } catch (e) { /* fall through to the empty state */ }
    const body = document.getElementById('pending-body');
    if (!body) return; // navigated away
    if (!pending.length) {
        body.innerHTML = `<p class="empty">No ticket in hand. Visit a <a href="#dealer">shop</a> and buy one.</p>`;
        return;
    }
    body.innerHTML = `
        <p class="hint">You have ${pending.length} unscratched ticket${pending.length > 1 ? 's' : ''} —
            already paid for, waiting on you.</p>
        <div class="grid">
            ${pending.map((p) => `
                <div class="card pending-card">
                    <h3>${MECHANIC_EMOJI[p.mechanic] || '🎟️'} ${escapeHtml(p.gameName)}</h3>
                    <p class="book-sub">Unscratched ticket</p>
                    <button class="btn block" data-ticket="${p.ticketId}" data-mechanic="${escapeHtml(p.mechanic)}">
                        Scratch it</button>
                </div>`).join('')}
        </div>`;
    body.querySelectorAll('button[data-ticket]').forEach((btn) => {
        btn.onclick = () => {
            setPendingTicket({ ticketId: btn.dataset.ticket, mechanic: btn.dataset.mechanic });
            renderScratch();
        };
    });
}

function showResult(outcome, won, detail) {
    const banner = document.getElementById('banner');
    if (!banner) return;
    banner.className = 'result-banner ' + (won ? 'win' : 'lose');
    banner.innerHTML = `
        <div class="result-headline">${won ? `🎉 WIN ${money(outcome.prizeAmount)} coins!` : 'No win this time.'}</div>
        ${detail ? `<div class="result-detail">${escapeHtml(detail)}</div>` : ''}
        <div class="result-edu" id="result-edu"></div>`;
    banner.hidden = false;
    const again = document.getElementById('buy-another');
    const ledger = document.getElementById('see-ledger');
    again.hidden = false;
    ledger.hidden = false;
    again.onclick = () => {
        location.hash = state.lastShop ? `#dealer/${state.lastShop}` : '#dealer';
    };
    setPendingTicket(null); // consumed

    // Losing tickets get the awareness payload: how often this game is built to look "close".
    if (!won && outcome.gameId) {
        Api.nearMisses(outcome.gameId).then((nm) => {
            const el = document.getElementById('result-edu');
            if (!el || !nm || !nm.totalLosers) return;
            const pct = Math.round(Number(nm.nearMissRate) * 100);
            if (pct > 0) el.textContent =
                `${pct}% of this game's losing tickets are engineered to land one step from a win.`;
        }).catch(() => { /* education is best-effort */ });
    }
}

// ---- ledger ----------------------------------------------------------------

async function renderLedger() {
    if (!state.player) return;
    view.innerHTML = `<div class="section-title"><h2>Your ledger</h2>
        <span class="hint">Every coin movement, and what the numbers really say.</span></div>
        <div id="ledger-body"><p class="empty">Loading…</p></div>`;
    try {
        const pid = state.player.playerId;
        const [p, txns, insights, curve, dealerCmp] = await Promise.all([
            Api.getPlayer(pid),
            Api.transactions(pid, 25),
            Api.insights(pid).catch(() => []),
            Api.curve(pid).catch(() => []),
            Api.dealerComparison(pid).catch(() => ({})),
        ]);
        const net = Number(p.netPosition);
        const body = document.getElementById('ledger-body');
        if (!body) return;
        body.innerHTML = `
            <div class="stats">
                ${stat('Balance', money(p.coinBalance) + ' coins')}
                ${stat('Borrowed', money(p.totalBorrowed))}
                ${stat('Spent', money(p.totalSpent))}
                ${stat('Won', money(p.totalWon))}
                ${stat('Net position', (net >= 0 ? '+' : '') + money(net), net >= 0 ? 'good' : 'bad')}
            </div>
            ${curveSection(curve)}
            ${dealerComparisonSection(dealerCmp)}
            ${insights.length ? `<h3>Insights</h3>${insights.map(renderInsight).join('')}` : ''}
            <h3>Recent transactions</h3>
            ${txns.length ? txnTable(txns) : '<p class="empty">No transactions yet — borrow and play.</p>'}`;
    } catch (e) {
        document.getElementById('ledger-body').innerHTML = `<p class="empty">${escapeHtml(e.message)}</p>`;
    }
}

/** The inevitability curve: cumulative spent vs cumulative won, ticket by ticket. */
function curveSection(points) {
    if (!points || points.length < 2) {
        return `<div class="chart-panel"><h3>The inevitability curve</h3>
            <p class="empty">Scratch a few tickets and your spent-vs-won curve appears here.</p></div>`;
    }
    const W = 640, H = 220, P = 34;
    const maxX = points[points.length - 1].ticketNumber;
    const maxY = Math.max(1, ...points.map((p) => Math.max(Number(p.cumulativeSpent), Number(p.cumulativeWon))));
    const x = (v) => P + ((v - 1) / Math.max(1, maxX - 1)) * (W - 2 * P);
    const y = (v) => H - P - (v / maxY) * (H - 2 * P);
    const path = (key) => points
        .map((p, i) => `${i ? 'L' : 'M'}${x(p.ticketNumber).toFixed(1)},${y(Number(p[key])).toFixed(1)}`)
        .join(' ');
    const last = points[points.length - 1];
    const gap = Number(last.cumulativeSpent) - Number(last.cumulativeWon);
    return `<div class="chart-panel">
        <h3>The inevitability curve</h3>
        <p class="chart-sub">Coins in vs coins out, ticket by ticket. The gap is the house's share —
            it only widens.</p>
        <svg class="curve-chart" viewBox="0 0 ${W} ${H}" role="img"
             aria-label="Cumulative spent versus won over ${maxX} tickets">
            <line x1="${P}" y1="${H - P}" x2="${W - P}" y2="${H - P}" class="axis"/>
            <line x1="${P}" y1="${P}" x2="${P}" y2="${H - P}" class="axis"/>
            <text x="${P}" y="${P - 8}" class="axis-label">${money(maxY)}</text>
            <text x="${W - P}" y="${H - P + 16}" class="axis-label" text-anchor="end">ticket ${maxX}</text>
            <path d="${path('cumulativeSpent')}" class="line spent"/>
            <path d="${path('cumulativeWon')}" class="line won"/>
        </svg>
        <div class="legend">
            <span class="legend-item"><span class="swatch spent"></span>Spent ${money(last.cumulativeSpent)}</span>
            <span class="legend-item"><span class="swatch won"></span>Won ${money(last.cumulativeWon)}</span>
            <span class="legend-item">${gap >= 0 ? `House is up ${money(gap)}` : `You are up ${money(-gap)} — for now`}</span>
        </div>
    </div>`;
}

/** Return rate per shop — debunks "the lucky store" by showing every shop pays back less than it takes. */
function dealerComparisonSection(cmp) {
    const stats = Object.values(cmp || {}).filter((d) => d.ticketsBought > 0);
    if (!stats.length) return '';
    stats.sort((a, b) => Number(b.returnRate) - Number(a.returnRate));
    const maxRate = Math.max(1, ...stats.map((d) => Number(d.returnRate)));
    return `<div class="chart-panel">
        <h3>Shop by shop</h3>
        <p class="chart-sub">Return rate per shop. The "lucky" one is just variance — none of them
            pays back what you put in.</p>
        ${stats.map((d) => {
            const rate = Number(d.returnRate);
            const pct = Math.round(rate * 100);
            return `<div class="cmp-row">
                <span class="cmp-name">${escapeHtml(d.dealerName)}</span>
                <div class="cmp-bar"><div class="cmp-fill ${rate >= 1 ? 'ahead' : ''}"
                    style="width:${Math.min(100, (rate / maxRate) * 100)}%"></div></div>
                <span class="cmp-rate">${pct}% <span class="cmp-sub">(${d.ticketsBought} tickets)</span></span>
            </div>`;
        }).join('')}
    </div>`;
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
