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

/** Cached GET /api/games (name, price, RTP, top prize) — used by the hero strip and book cards. */
let _gamesCache = null;
async function loadGames() {
    if (!_gamesCache) _gamesCache = await Api.games();
    return _gamesCache;
}

function tierLabel(tier) {
    return String(tier || '').replace('TIER_', 'Tier ');
}

/** One game in the hero strip: ticket art, price, top prize, and the RTP stated up front. */
function gameStripCard(g) {
    const art = TICKET_ART[g.mechanic];
    return `
        <div class="game-card">
            ${art ? `<img class="game-art" src="${art}" alt="${escapeHtml(g.gameName)} ticket" loading="lazy">` : ''}
            <div class="game-card-body">
                <h3>${MECHANIC_EMOJI[g.mechanic] || '🎟️'} ${escapeHtml(g.gameName)}</h3>
                <div class="game-facts">
                    <span class="price-tag">${money(g.ticketPrice)} coins</span>
                    <span class="fact">Top prize <b>${money(g.topPrize)}</b></span>
                    <span class="fact">Returns <b>${Math.round(Number(g.payoutRatio) * 100)}%</b> of every coin</span>
                </div>
            </div>
        </div>`;
}

async function renderDealers() {
    view.innerHTML = `
        <section class="hero">
            <div class="hero-text">
                <h2>Every ticket is already decided.</h2>
                <p>Buy from a shop, scratch the foil, and watch the math the lottery never shows you.
                    Free coins, real odds, no stakes — the house edge is printed right on the games.</p>
                <div class="hero-actions">
                    <a class="btn" href="#dealer">Browse the shops</a>
                    <a class="btn secondary" href="#house">See the house's books</a>
                </div>
            </div>
            <div class="game-strip" id="game-strip">${skeletonCards(2)}</div>
        </section>
        <div class="section-title"><h2>Shops</h2>
            <span class="hint">NPC storefronts, each run by an owner. Tap a shop to see what it stocks.</span></div>
        <div id="grid">${skeletonCards(6)}</div>
        <div class="section-title"><h2>Shop leaderboard</h2>
            <span class="hint">Ranked by books sold out — throughput, not luck.</span></div>
        <div id="leaderboard" class="leaderboard">${skeletonCards(1)}</div>`;
    loadGames().then((games) => {
        const strip = document.getElementById('game-strip');
        if (strip) strip.innerHTML = games.map(gameStripCard).join('');
    }).catch(() => { /* hero strip is decorative */ });
    renderLeaderboard();
    try {
        const dealers = await Api.dealers();
        const grid = document.getElementById('grid');
        if (!grid) return; // navigated away
        grid.className = 'grid';
        if (!dealers.length) { grid.innerHTML = `<p class="empty">No shops seeded.</p>`; return; }
        grid.innerHTML = dealers.map((d) => `
            <div class="card shop-card" data-dealer="${d.dealerId}" role="button" tabindex="0">
                <div class="shop-head">
                    ${avatarHtml(d)}
                    <div>
                        <h3>${escapeHtml(d.shopName)}</h3>
                        <p class="shop-owner">${escapeHtml(d.ownerName)}</p>
                    </div>
                    <span class="tier-badge t${(d.tier || '').slice(-1)}">${tierLabel(d.tier)}</span>
                </div>
                <div class="badges">
                    ${d.games.map((g) => `<span class="badge">${escapeHtml(g.gameName)}</span>`).join('')
                        || '<span class="badge muted">No games</span>'}
                </div>
                <div class="meta">
                    <span>Books in stock <b>${d.activeBooks}</b></span>
                    <span>Books sold out <b>${d.booksDepleted}</b></span>
                </div>
                <span class="card-cta">Browse →</span>
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

/** A medal for the top three ranks, else the bare number. */
function rankMedal(rank) {
    return { 1: '🥇', 2: '🥈', 3: '🥉' }[rank] || `#${rank}`;
}

/** The shop leaderboard panel: ranked list with medals for the top three, plus the flywheel debunk. */
async function renderLeaderboard() {
    const panel = document.getElementById('leaderboard');
    if (!panel) return;
    try {
        const rankings = await Api.rankings();
        if (!document.getElementById('leaderboard')) return; // navigated away
        if (!rankings.length) { panel.innerHTML = `<p class="empty">No shops seeded.</p>`; return; }
        panel.innerHTML = `
            <ol class="rank-list">
                ${rankings.map((r) => `
                    <li class="rank-row${r.rank <= 3 ? ' rank-top' : ''}">
                        <span class="rank-medal">${rankMedal(r.rank)}</span>
                        <span class="rank-shop">
                            <b>${escapeHtml(r.shopName)}</b>
                            <small>${escapeHtml(r.ownerName)}</small>
                        </span>
                        <span class="tier-badge t${(r.tier || '').slice(-1)}">${tierLabel(r.tier)} · ${escapeHtml(r.quartile)}</span>
                        <span class="rank-stat">Sold out <b>${r.booksDepleted}</b></span>
                        <span class="rank-stat">Selling <b>${r.activeBooks}</b></span>
                    </li>`).join('')}
            </ol>
            <p class="hint debunk">Every book of a game has identical per-ticket odds. A "hot shop"
                isn't luckier — it just sells more tickets, so it lands more (and bigger) winners by sheer
                volume. Rank measures throughput, not luck.</p>`;
    } catch (e) {
        panel.innerHTML = `<p class="empty">${escapeHtml(e.message)}</p>`;
    }
}

/** A single shop's storefront: its books grouped by game, each buyable, with the price up front. */
async function renderDealerBooks(dealerId) {
    state.lastShop = dealerId; // so "Buy another" returns to this shop
    view.innerHTML = `<div class="section-title"><h2>Shop</h2>
        <span class="hint"><a href="#dealer">← All shops</a></span></div>
        <div id="shop-body">${skeletonCards(3)}</div>`;
    try {
        const [dealer, mine, games] = await Promise.all([
            Api.dealer(dealerId), Api.dealerBooks(dealerId), loadGames().catch(() => []),
        ]);
        const topPrizeByGame = new Map(games.map((g) => [g.gameId, g.topPrize]));
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
            const topPrize = topPrizeByGame.get(group.books[0].gameId);
            html += `<section class="game-group">
                <h3 class="game-group-title">${MECHANIC_EMOJI[group.books[0].mechanic] || '🎟️'} ${escapeHtml(group.name)}
                    ${topPrize ? `<span class="top-prize">Top prize ${money(topPrize)} coins</span>` : ''}</h3>
                <div class="grid">
                    ${group.books.map((b, i) => bookCard(b, i)).join('')}
                </div>
            </section>`;
        }
        body.innerHTML = html;
        body.querySelectorAll('button[data-book]').forEach((btn) => {
            btn.onclick = () => buyTicket(btn.dataset.book, btn.dataset.mechanic, btn);
        });
    } catch (e) {
        const body = document.getElementById('shop-body');
        if (body) body.innerHTML = `<p class="empty">${escapeHtml(e.message)}</p>`;
    }
}

/* How a book's metadata-visibility tier renders: an honest badge plus a one-line reminder that seeing
   the roll's history changes nothing about the sealed ticket you're about to buy. */
const VISIBILITY_BADGE = {
    NONE: { cls: 'vis-none', label: '🙈 hidden roll' },
    PARTIAL: { cls: 'vis-partial', label: '🔍 partial' },
    FULL: { cls: 'vis-full', label: '📖 open book' },
};

/** The visibility panel for a book: badge, the depletion facts the tier permits, and an education hint. */
function bookVisibility(b) {
    const badge = VISIBILITY_BADGE[b.visibility];
    if (!badge) return '';
    let facts = '';
    if (b.percentDispensed != null) {
        facts += `<span>Roll dispensed <b>${Math.round(Number(b.percentDispensed))}%</b></span>`;
    }
    if (b.estimatedRemainingValue != null) {
        facts += `<span>Prizes left (est.) <b>${money(b.estimatedRemainingValue)}</b></span>`;
    }
    if (b.winFrequencySoFar != null) {
        facts += `<span>Winners revealed <b>${b.winFrequencySoFar}</b></span>`;
    }
    // The debunk: reading the roll is reading the PAST. The pool was fixed at print time, so a sealed
    // ticket's odds are the same whether the roll is hidden or wide open.
    const hint = b.visibility === 'NONE'
        ? 'This roll hides its state — just like a real scratch card. You can\'t "read" it.'
        : 'Reading the roll shows only what\'s already happened. Your sealed ticket\'s odds were fixed at print time — a transparent roll doesn\'t change them.';
    return `
        <div class="book-vis">
            <span class="vis-badge ${badge.cls}">${badge.label}</span>
            ${facts ? `<div class="vis-facts">${facts}</div>` : ''}
            <p class="vis-hint">${hint}</p>
        </div>`;
}

/** One buyable book: ticket art, price up front, a stock bar instead of bare counts. */
function bookCard(b, i) {
    const soldOut = !b.ticketsRemaining;
    const pct = b.totalTickets ? Math.round((b.ticketsRemaining / b.totalTickets) * 100) : 0;
    const art = TICKET_ART[b.mechanic];
    return `
        <div class="card book-card${soldOut ? ' sold-out' : ''}">
            ${art ? `<img class="book-art" src="${art}" alt="" loading="lazy">` : ''}
            <div class="book-head">
                <h3>Book #${i + 1}</h3>
                <span class="price-tag">${money(b.ticketPrice)} coins</span>
            </div>
            <p class="book-sub">${escapeHtml(b.gameName)} · per ticket</p>
            <div class="stock-bar" title="${b.ticketsRemaining} of ${b.totalTickets} tickets left">
                <div class="stock-fill" style="width:${pct}%"></div>
            </div>
            <p class="stock-label">${soldOut ? 'Sold out' : `${b.ticketsRemaining} of ${b.totalTickets} tickets left`}</p>
            ${bookVisibility(b)}
            <button class="btn block" data-book="${b.bookId}" data-mechanic="${escapeHtml(b.mechanic || '')}"
                ${soldOut ? 'disabled' : ''}>
                ${soldOut ? 'Sold out' : `Buy a ticket — ${money(b.ticketPrice)} coins`}</button>
        </div>`;
}

async function buyTicket(bookId, mechanic, btn) {
    if (!state.player) return;
    const label = btn ? btn.textContent : '';
    if (btn) { btn.disabled = true; btn.textContent = 'Buying…'; }
    try {
        const result = await Api.purchase(bookId, state.player.playerId);
        refreshPlayer().catch(() => {});
        // The book card already knows its mechanic; only fall back to a ticket fetch without it.
        if (!mechanic) mechanic = (await Api.ticket(result.ticketId)).mechanic;
        // bookId rides along so "Buy another" can re-buy from this same book.
        setPendingTicket({ ticketId: result.ticketId, mechanic, bookId: result.bookId });
        toast(`Bought a ticket for ${money(result.coinsDeducted)} coins. Scratch it!`);
        // Setting an unchanged hash fires no hashchange event, so re-render explicitly.
        if (location.hash === '#scratch') route();
        else location.hash = '#scratch';
    } catch (e) {
        if (btn) { btn.disabled = false; btn.textContent = label; }
        if (e.code === 'INSUFFICIENT_BALANCE') toast('Not enough coins — borrow some first.', true);
        else if (e.code === 'BOOK_DEPLETED') {
            toast('That book is sold out — pick another from the shop.', true);
            if (state.lastShop) location.hash = `#dealer/${state.lastShop}`;
        } else toast(e.message, true);
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

/* Renders the ticket's REAL hidden values into the dark reveal layer, one element per scratch zone.
   Each element carries its zone id so the scratch engine's 'zonereveal' events can pop it. */
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
        el.dataset.zone = z.id;
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
    // A failed init (e.g. server still starting) must not strand the flow on a null player.
    if (!state.player) {
        try { await ensurePlayer(); renderPlayerBar(); } catch (e) { /* handled below as transient */ }
    }

    const art = TICKET_ART[t.mechanic] || TICKET_ART.CELESTIAL_FORTUNE;
    // The ticket PNG IS the coating: it is drawn onto the scratch canvas. Beneath the canvas sits the
    // dark reveal layer (a DOM div) holding the ticket's REAL values, one per scratch zone. Scratching
    // the PNG away uncovers them.
    view.innerHTML = `
        <div class="section-title"><h2>Scratch your ticket</h2>
            <span class="hint">${t.mechanic.replace(/_/g, ' ')}</span></div>
        <div class="scratch-wrap">
            <div class="scratch-stage" id="stage">
                <div id="reveal-layer" class="reveal-layer"></div>
                <canvas id="scratch" class="scratch-canvas" width="360" height="640"></canvas>
                <canvas id="fx-canvas" class="fx-canvas" width="360" height="640"></canvas>
                <div id="banner" class="result-banner" hidden></div>
            </div>
            <p class="scratch-instructions" id="scratch-progress">Scratch each panel to uncover what this
                ticket was always going to be.</p>
            <div class="scratch-actions">
                <button class="btn ghost" id="reveal-all">Reveal everything</button>
                <button class="btn secondary" id="buy-another" hidden>Buy another</button>
                <button class="btn secondary" id="back-shop" hidden>Back to the shop</button>
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
    let outcome = null;
    let loadError = null;
    try {
        if (!state.player) throw new Error('player unavailable');
        outcome = await Api.reveal(t.ticketId, state.player.playerId);
    } catch (e) {
        loadError = e;
        outcome = await Api.ticket(t.ticketId).catch((e2) => { loadError = e2; return null; });
    }
    if (!document.getElementById('reveal-layer')) return; // navigated away during the await
    if (!outcome || !outcome.grid) {
        // Two very different failures land here, and only one should cost the player their ticket:
        //  - 404: the id no longer exists (the demo database was reset since it was bought).
        //    Clear it and explain — the server-side pending list is the safety net.
        //  - anything else (server restarting, network blip): KEEP the ticket and offer a retry.
        if (loadError && (loadError.status === 404 || loadError.code === 'NOT_FOUND')) {
            setPendingTicket(null);
            return renderPendingTickets(
                'That ticket was bought against an older demo database that has since been reset, '
                + 'so it no longer exists. It has been cleared — any current tickets appear below.');
        }
        const wrap = view.querySelector('.scratch-wrap');
        if (wrap) {
            wrap.innerHTML = `<p class="empty">Could not reach the server to load this ticket —
                it is still yours.</p>
                <div class="scratch-actions"><button class="btn" id="retry-scratch">Try again</button></div>`;
            const retry = document.getElementById('retry-scratch');
            if (retry) retry.onclick = () => renderScratch();
        }
        return;
    }

    const won = !!(outcome.isWinner ?? outcome.winner) && Number(outcome.prizeAmount || 0) > 0;
    const gridData = t.mechanic === 'DEMON_SEAL'
        ? demonZoneSeals(outcome.grid)
        : celestialZoneValues(outcome.grid);
    drawRevealLayer(revealLayer, zones, t.mechanic, gridData);

    const onReveal = async () => {
        showResult(outcome, won, outcomeDetail(t.mechanic, gridData, won), t);
        await refreshPlayer();
    };
    const controller = initScratch(canvas, art, onReveal);

    // Particle layer: shavings off the pointer while scratching, a glint burst per cleared panel.
    const fx = createStageFx(document.getElementById('fx-canvas'));
    canvas.addEventListener('scratchstroke', (e) => fx.shavings(e.detail.x, e.detail.y));

    // Per-zone feedback: pop the uncovered tile and advance the progress line.
    canvas.addEventListener('zonereveal', (e) => {
        const tile = revealLayer.querySelector(`[data-zone="${e.detail.zoneId}"]`);
        if (tile) tile.classList.add('revealed');
        const zone = zones.find((z) => z.id === e.detail.zoneId);
        if (zone) {
            const cx = (zone.shape === 'circle' ? zone.cx : zone.x + zone.w / 2) * canvas.width;
            const cy = (zone.shape === 'circle' ? zone.cy : zone.y + zone.h / 2) * canvas.height;
            fx.burst(cx, cy);
        }
        const progress = document.getElementById('scratch-progress');
        if (progress && e.detail.total) {
            progress.textContent = `${e.detail.revealed} of ${e.detail.total} panels revealed`;
        }
    });
    const revealAllBtn = document.getElementById('reveal-all');
    if (revealAllBtn) revealAllBtn.onclick = () => controller.revealAll();

    attachTilt(document.getElementById('stage'));
    startTwinkles(document.getElementById('stage'));
}

/** No ticket in hand: offer any bought-but-unscratched tickets to resume, else point at the shops. */
async function renderPendingTickets(notice) {
    view.innerHTML = `<div class="section-title"><h2>Scratch</h2></div>
        ${notice ? `<p class="notice">${escapeHtml(notice)}</p>` : ''}
        <div id="pending-body">${skeletonCards(2)}</div>`;
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
                    <button class="btn block" data-ticket="${p.ticketId}" data-mechanic="${escapeHtml(p.mechanic)}"
                        data-book="${p.bookId || ''}">Scratch it</button>
                </div>`).join('')}
        </div>`;
    body.querySelectorAll('button[data-ticket]').forEach((btn) => {
        btn.onclick = () => {
            setPendingTicket({
                ticketId: btn.dataset.ticket,
                mechanic: btn.dataset.mechanic,
                bookId: btn.dataset.book || null,
            });
            renderScratch();
        };
    });
}

function showResult(outcome, won, detail, ticket) {
    const banner = document.getElementById('banner');
    if (!banner) return;
    banner.className = 'result-banner ' + (won ? 'win' : 'lose');
    banner.innerHTML = `
        <div class="result-headline">${won ? `🎉 WIN ${money(outcome.prizeAmount)} coins!` : 'No win this time.'}</div>
        ${detail ? `<div class="result-detail">${escapeHtml(detail)}</div>` : ''}
        <div class="result-edu" id="result-edu"></div>`;
    banner.hidden = false;
    if (won) burstConfetti(document.getElementById('stage'));
    const revealAllBtn = document.getElementById('reveal-all');
    if (revealAllBtn) revealAllBtn.hidden = true;
    const again = document.getElementById('buy-another');
    const shop = document.getElementById('back-shop');
    const ledger = document.getElementById('see-ledger');
    again.hidden = false;
    ledger.hidden = false;
    if (shop) shop.hidden = false;
    // "Buy another" buys the next ticket from the SAME book, straight into a fresh scratch —
    // exactly the loss-chasing loop the simulator wants the player to feel (and the ledger to show).
    if (ticket && ticket.bookId) {
        again.classList.remove('secondary');
        again.onclick = () => buyTicket(ticket.bookId, ticket.mechanic, again);
    } else {
        again.onclick = () => {
            location.hash = state.lastShop ? `#dealer/${state.lastShop}` : '#dealer';
        };
    }
    if (shop) {
        shop.onclick = () => {
            location.hash = state.lastShop ? `#dealer/${state.lastShop}` : '#dealer';
        };
    }
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
        <div id="ledger-body">${skeletonCards(3)}</div>`;
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

// ---- the house -------------------------------------------------------------

/**
 * The operator's dashboard. The overview (pool economics + totals) is public — surfacing that every
 * pool is built to keep a fixed share IS the lesson — so it renders for everyone. The master-only
 * layer (per-player table, coin grants, restocking) appears only for a signed-in operator; anonymous
 * visitors get a compact "log in" card instead.
 */
async function renderHouse() {
    view.innerHTML = `<div class="section-title"><h2>🏛️ The House</h2>
        <span class="hint" id="house-hint">The operator's side of the table — every pool's economics, in the open.</span></div>
        <div id="house-body">${skeletonCards(3)}</div>`;
    try {
        if (!state.master) {
            const me = await Api.me().catch(() => null);
            if (me && me.authenticated) state.master = { username: me.username };
        }
        let isMaster = !!state.master;

        // House overview is public; the player roster needs the master session. A 401/403 on the
        // roster means the session lapsed since login — drop the stale master state and render the
        // anonymous view (log-in card) instead of dead operator tools. Any other roster failure
        // keeps the session and just omits the panel.
        const [o, roster] = await Promise.all([
            Api.house(),
            isMaster ? Api.masterPlayers().then((p) => ({ players: p }), (e) => ({ error: e }))
                     : Promise.resolve(null),
        ]);
        let players = null;
        if (roster) {
            if (roster.error) {
                if (roster.error.status === 401 || roster.error.status === 403) {
                    state.master = null;
                    isMaster = false;
                }
            } else {
                players = roster.players;
            }
        }
        const body = document.getElementById('house-body');
        if (!body) return; // navigated away
        const t = o.totals;
        const profit = Number(t.houseProfit);
        body.innerHTML = `
            ${isMaster ? masterBar(state.master.username) : masterToolsCard()}
            <div class="stats">
                ${stat('Players', t.players)}
                ${stat('Tickets sold', t.ticketsSold)}
                ${stat('Coins taken in', money(t.revenue))}
                ${stat('Coins paid out', money(t.paidOut))}
                ${stat('House profit', (profit >= 0 ? '+' : '') + money(profit), profit >= 0 ? 'gold' : 'bad')}
                ${stat('Books selling', t.activeBooks)}
            </div>
            <p class="house-note">Every number below was fixed at generation time — before the first
                ticket was sold, the house knew exactly how much each game would keep.</p>
            ${o.games.map((g) => houseGamePanel(g, isMaster)).join('')}
            ${isMaster && players ? playersPanel(players) : ''}`;

        if (!isMaster) {
            const link = document.getElementById('master-login-link');
            if (link) link.onclick = (e) => { e.preventDefault(); renderMasterLogin(); };
            return;
        }

        document.getElementById('logout-btn').onclick = async () => {
            try { await Api.logout(); } catch (e) { /* session may already be gone */ }
            state.master = null;
            toast('Logged out of the master account.');
            renderHouse();
        };
        body.querySelectorAll('button[data-restock]').forEach((btn) => {
            btn.onclick = async () => {
                btn.disabled = true;
                btn.textContent = 'Generating…';
                try {
                    const r = await Api.restock(btn.dataset.restock);
                    toast(`Restocked: ${r.booksAdded} new books, ${r.ticketsAdded} tickets — verified before sale.`);
                    renderHouse();
                } catch (e) {
                    btn.disabled = false;
                    btn.textContent = 'Restock books';
                    toast(e.message, true);
                }
            };
        });
        body.querySelectorAll('button[data-grant]').forEach((btn) => {
            btn.onclick = async () => {
                const input = body.querySelector(`input[data-grant-for="${btn.dataset.grant}"]`);
                const amount = Number(input && input.value);
                if (!amount || amount <= 0) { toast('Enter a positive amount to grant.', true); return; }
                try {
                    await Api.grant(btn.dataset.grant, amount);
                    toast(`Granted ${money(amount)} coins (recorded as a bank loan).`);
                    if (state.player && state.player.playerId === btn.dataset.grant) await refreshPlayer();
                    renderHouse();
                } catch (e) { toast(e.message, true); }
            };
        });
    } catch (e) {
        if (e.status === 401 || e.status === 403) { state.master = null; return renderMasterLogin(); }
        const body = document.getElementById('house-body');
        if (body) body.innerHTML = `<p class="empty">${escapeHtml(e.message)}</p>`;
    }
}

/** The signed-in operator's identity chip plus a log-out button. */
function masterBar(username) {
    return `<div class="master-bar">
        <span class="player-chip">🏛️ Signed in as <strong>${escapeHtml(username)}</strong></span>
        <button class="btn secondary" id="logout-btn">Log out</button>
    </div>`;
}

/** Shown to anonymous visitors: the overview is theirs to read, but the operator tools are gated. */
function masterToolsCard() {
    return `<div class="master-bar">
        <span class="player-chip">🔒 Master tools — players, coin grants, and restocking — need an operator login.</span>
        <a href="#house" class="btn secondary" id="master-login-link">Log in</a>
    </div>`;
}

/** The gate in front of the operator tools: one operator account, session login. */
function renderMasterLogin() {
    const body = document.getElementById('house-body');
    if (!body) return;
    const hint = document.getElementById('house-hint');
    if (hint) hint.textContent = 'Master login required — players never see this side of the table.';
    body.innerHTML = `
        <div class="login-card card">
            <h3>Master login</h3>
            <p class="book-sub">The operator's books: every pool's economics, every player, restocking.</p>
            <form id="login-form" autocomplete="off">
                <label class="login-label">Username
                    <input class="login-input" name="username" value="master" required></label>
                <label class="login-label">Password
                    <input class="login-input" name="password" type="password" required
                        placeholder="password"></label>
                <p class="login-error" id="login-error" hidden></p>
                <button class="btn block" type="submit">Open the books</button>
            </form>
        </div>`;
    document.getElementById('login-form').onsubmit = async (e) => {
        e.preventDefault();
        const form = e.target;
        const err = document.getElementById('login-error');
        err.hidden = true;
        form.querySelector('button').disabled = true;
        try {
            const session = await Api.login(form.username.value.trim(), form.password.value);
            state.master = { username: session.username };
            toast('Master session opened.');
            renderHouse();
        } catch (ex) {
            form.querySelector('button').disabled = false;
            err.textContent = ex.code === 'BAD_CREDENTIALS' ? 'Wrong username or password.' : ex.message;
            err.hidden = false;
        }
    };
}

/** Master-only: every player's bankroll, activity, and a coin-grant control. */
function playersPanel(players) {
    if (!players.length) {
        return `<div class="chart-panel"><h3>Players</h3><p class="empty">No players yet.</p></div>`;
    }
    return `<div class="chart-panel">
        <h3>Players</h3>
        <p class="chart-sub">Each row is one anonymous browser session. Grants are recorded as bank
            loans — the ledger stays append-only.</p>
        <div class="table-scroll"><table>
            <thead><tr><th>Player</th><th>Balance</th><th>Borrowed</th><th>Spent</th><th>Won</th>
                <th>Net</th><th>Unscratched</th><th>Grant coins</th></tr></thead>
            <tbody>
                ${players.map((p) => {
                    const net = Number(p.netPosition);
                    return `<tr>
                        <td title="${p.playerId}">${escapeHtml(p.displayName)}</td>
                        <td>${money(p.coinBalance)}</td>
                        <td>${money(p.totalBorrowed)}</td>
                        <td>${money(p.totalSpent)}</td>
                        <td>${money(p.totalWon)}</td>
                        <td class="${net >= 0 ? 'net-good' : 'net-bad'}">${(net >= 0 ? '+' : '') + money(net)}</td>
                        <td>${p.pendingTickets}</td>
                        <td class="grant-cell">
                            <input class="grant-input" type="number" min="1" step="1" placeholder="100"
                                data-grant-for="${p.playerId}">
                            <button class="btn secondary" data-grant="${p.playerId}">Grant</button>
                        </td>
                    </tr>`;
                }).join('')}
            </tbody>
        </table></div>
    </div>`;
}

function houseGamePanel(g, isMaster) {
    const rtp = Math.round(Number(g.payoutRatio) * 1000) / 10;
    const fundPct = Math.min(100, (Number(g.prizeFund) / Math.max(1, Number(g.maxRevenue))) * 100);
    const soldPct = g.totalTickets ? (g.ticketsSold / g.totalTickets) * 100 : 0;
    return `
    <div class="house-game card">
        <div class="house-game-head">
            <h3>${MECHANIC_EMOJI[g.mechanic] || '🎟️'} ${escapeHtml(g.gameName)}</h3>
            <span class="badge ${g.verificationPassed ? 'ok' : 'fail'}">
                ${g.verificationPassed ? '✓ verified' : '✗ unverified'}</span>
            <span class="badge">RTP ${rtp}%</span>
            <span class="badge">${money(g.ticketPrice)} coins / ticket</span>
            <span class="badge">Top prize ${money(g.topPrize)}</span>
        </div>
        <div class="house-bar-row">
            <span class="house-bar-label">Built to pay back</span>
            <div class="house-bar">
                <div class="house-fill fund" style="width:${fundPct.toFixed(1)}%"></div>
            </div>
            <span class="house-bar-value">${money(g.prizeFund)} of ${money(g.maxRevenue)} coins
                <span class="cmp-sub">— the house keeps ${money(Number(g.maxRevenue) - Number(g.prizeFund))}, guaranteed</span></span>
        </div>
        <div class="house-bar-row">
            <span class="house-bar-label">Tickets sold</span>
            <div class="house-bar">
                <div class="house-fill sold" style="width:${soldPct.toFixed(1)}%"></div>
            </div>
            <span class="house-bar-value">${g.ticketsSold} of ${g.totalTickets}
                <span class="cmp-sub">(${g.ticketsRevealed} scratched)</span></span>
        </div>
        <div class="meta house-meta">
            <span>Books <b>${g.books.active} selling</b> · ${g.books.depleted} sold out · ${g.books.total} total</span>
            <span>Taken in <b>${money(g.revenue)}</b> · paid out <b>${money(g.paidOut)}</b></span>
            <span>Engineered near-miss rate <b>${Math.round(Number(g.nearMissRate) * 100)}%</b> of losers</span>
            <span>Generated in <b>${g.generationTimeMs}ms</b></span>
        </div>
        ${isMaster ? `<button class="btn secondary restock-btn" data-restock="${g.gameId}">Restock books</button>` : ''}
    </div>`;
}

// ---- router ----------------------------------------------------------------

const ROUTES = { dealer: renderDealers, scratch: renderScratch, ledger: renderLedger, house: renderHouse };

function route() {
    const raw = location.hash.replace('#', '') || 'dealer';
    const [name, param] = raw.split('/');
    document.querySelectorAll('.tabs a').forEach((a) =>
        a.classList.toggle('active', a.dataset.route === name));
    // Re-trigger the view entrance animation on every route change.
    view.classList.remove('view-enter');
    void view.offsetWidth;
    view.classList.add('view-enter');
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
