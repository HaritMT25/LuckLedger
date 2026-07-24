/* LuckLedger SPA — shops view.
 * Dealer grid, leaderboard, a shop's books + visibility, buy action, and the
 * "how this game works" mechanic modal. Load order is managed by index.html. */

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
            const mechanic = group.books[0].mechanic;
            html += `<section class="game-group">
                <h3 class="game-group-title">${MECHANIC_EMOJI[mechanic] || '🎟️'} ${escapeHtml(group.name)}
                    ${topPrize ? `<span class="top-prize">Top prize ${money(topPrize)} coins</span>` : ''}
                    ${mechanic ? `<button type="button" class="how-it-works" data-mechanic-info="${escapeHtml(mechanic)}">❓ How this game works</button>` : ''}</h3>
                <div class="grid">
                    ${group.books.map((b, i) => bookCard(b, i)).join('')}
                </div>
            </section>`;
        }
        body.innerHTML = html;
        body.querySelectorAll('button[data-book]').forEach((btn) => {
            btn.onclick = () => buyTicket(btn.dataset.book, btn.dataset.mechanic, btn);
        });
        body.querySelectorAll('button[data-mechanic-info]').forEach((btn) => {
            btn.onclick = () => openMechanicModal(btn.dataset.mechanicInfo);
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

// ---- "how this game works" modal -------------------------------------------

/* The compact glyph for an example-grid cell: seal symbols become their icon, numbers show as-is. */
function miniCellGlyph(symbol) {
    return SEAL_ICON[symbol] || symbol;
}

/* Renders a mechanic's example grid (a GridDto) as a compact glyph grid. */
function miniGrid(grid) {
    if (!grid || !grid.cells) return '';
    const cells = [...grid.cells].sort((a, b) => a.row - b.row || a.col - b.col);
    const dim = grid.dimension || Math.round(Math.sqrt(cells.length));
    return `<div class="mini-grid" style="grid-template-columns:repeat(${dim},1fr)">
        ${cells.map((c) => {
            const seal = SEAL_ICON[c.symbol];
            return `<span class="mini-cell${seal ? ` seal-${String(c.symbol).toLowerCase()}` : ''}">${escapeHtml(miniCellGlyph(c.symbol))}</span>`;
        }).join('')}
    </div>`;
}

/* An education modal for a mechanic: display name, description, the fixed prize ladder, and two
   example mini-grids (win + loss). The ladder is printed at generation time — seeing it changes
   nothing about the sealed pool. */
async function openMechanicModal(type) {
    const overlay = document.createElement('div');
    overlay.className = 'modal-overlay';
    overlay.innerHTML = `<div class="modal-card"><p class="empty">Loading…</p></div>`;
    document.body.appendChild(overlay);
    const close = () => overlay.remove();
    overlay.addEventListener('click', (e) => { if (e.target === overlay) close(); });
    document.addEventListener('keydown', function esc(e) {
        if (e.key === 'Escape') { close(); document.removeEventListener('keydown', esc); }
    });

    const card = overlay.querySelector('.modal-card');
    let detail;
    try {
        detail = await Api.mechanicDetail(type);
    } catch (e) {
        card.innerHTML = `<button class="modal-close" aria-label="Close">✕</button>
            <p class="empty">${escapeHtml(e.message)}</p>`;
        card.querySelector('.modal-close').onclick = close;
        return;
    }
    const rules = (detail.winRules || []).map((r) => `
        <tr><td>${r.threshold}</td><td>${escapeHtml(r.description)}</td></tr>`).join('');
    card.innerHTML = `
        <button class="modal-close" aria-label="Close">✕</button>
        <h3>${MECHANIC_EMOJI[detail.type] || '🎟️'} ${escapeHtml(detail.displayName)} — how it works</h3>
        <p class="modal-desc">${escapeHtml(detail.description)}</p>
        <table class="rules-table">
            <thead><tr><th>Reach</th><th>Prize</th></tr></thead>
            <tbody>${rules}</tbody>
        </table>
        <div class="mini-examples">
            <figure><figcaption>Example win</figcaption>${miniGrid(detail.exampleWin)}</figure>
            <figure><figcaption>Example loss</figcaption>${miniGrid(detail.exampleLoss)}</figure>
        </div>
        <p class="hint">The prize ladder is fixed when the cards are printed. Seeing the rules doesn't
            change the sealed pool — every ticket was already decided.</p>`;
    card.querySelector('.modal-close').onclick = close;
}
