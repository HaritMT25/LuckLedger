/* LuckLedger SPA — play (scratch) view.
 * The scratch flow: zone placement, reveal layer, commit-reveal proof, renderScratch,
 * pending tickets, and the result banner. Load order is managed by index.html. */

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
   decoys. PURE PLACEMENT: maps each scratch zone (win-1..4, num-1..12) to its cell's value and grid
   coordinates. It no longer decides what "counts" — the backend narrative (matchedPositions) drives
   the match highlighting. */
function celestialZoneValues(grid) {
    const cells = cellsRowMajor(grid);
    const byZone = {};
    cells.filter((c) => c.row === 0).forEach((c, i) => {
        byZone[`win-${i + 1}`] = { text: c.displayLabel || c.abstractSymbol, kind: 'win', row: c.row, col: c.col };
    });
    cells.filter((c) => c.row >= 1).forEach((c, i) => {
        byZone[`num-${i + 1}`] = { text: c.displayLabel || c.abstractSymbol, kind: 'plain', row: c.row, col: c.col };
    });
    return { byZone };
}

/* Demon Seal: the grid holds exactly 6 seal cells (GOLD/SILVER/BROKEN) among themed decoys. PURE
   PLACEMENT: maps the seals, row-major, onto the 6 seal zones with their grid coordinates. Scoring
   (T = 2×gold + silver) is the backend's job — the narrative supplies the summary and the scoring
   positions. */
function demonZoneSeals(grid) {
    const seals = cellsRowMajor(grid).filter((c) => SEAL_ICON[c.abstractSymbol]);
    const byZone = {};
    seals.forEach((c, i) => {
        if (SEAL_ZONE_ORDER[i]) byZone[SEAL_ZONE_ORDER[i]] = { symbol: c.abstractSymbol, row: c.row, col: c.col };
    });
    return { byZone };
}

/* Renders the ticket's REAL hidden values into the dark reveal layer, one element per scratch zone.
   Each element carries its zone id so the scratch engine's 'zonereveal' events can pop it. The
   backend narrative's matched/scoring positions (a Set of "row,col") drive the highlight class —
   the client no longer decides which cells count. */
function drawRevealLayer(layer, zones, mechanic, gridData, matchedSet) {
    layer.innerHTML = '';
    for (const z of zones) {
        const cx = z.shape === 'circle' ? z.cx : z.x + z.w / 2;
        const cy = z.shape === 'circle' ? z.cy : z.y + z.h / 2;
        let el;
        if (mechanic === 'DEMON_SEAL') {
            const cell = gridData.byZone[z.id] || { symbol: 'BROKEN' };
            const symbol = cell.symbol || 'BROKEN';
            const scoring = matchedSet.has(`${cell.row},${cell.col}`);
            el = document.createElement('div');
            el.className = 'seal-reveal ' + symbol.toLowerCase() + (scoring ? ' scoring' : '');
            el.innerHTML = `<span class="seal-icon">${SEAL_ICON[symbol]}</span><span class="seal-label">${symbol}</span>`;
        } else {
            const v = gridData.byZone[z.id] || { text: '?', kind: 'plain' };
            const matched = matchedSet.has(`${v.row},${v.col}`);
            const kind = matched ? 'match' : v.kind;
            el = document.createElement('span');
            el.className = 'value-label' + (kind !== 'plain' ? ` ${kind}` : '');
            el.textContent = v.text;
        }
        el.dataset.zone = z.id;
        el.style.left = `${cx * 100}%`;
        el.style.top = `${cy * 100}%`;
        layer.appendChild(el);
    }
}

/* The set of "row,col" keys the backend narrative flagged as matched/scoring, for cell highlighting.
   Empty when there is no narrative (e.g. the sacred-payout guard tripped). */
function matchedPositionSet(narrative) {
    const set = new Set();
    if (narrative && Array.isArray(narrative.matchedPositions)) {
        for (const p of narrative.matchedPositions) set.add(`${p.row},${p.col}`);
    }
    return set;
}

/* --- commit-reveal proof -----------------------------------------------------
   The backend stamps every ticket, at generation, with a SHA-256 commitment over its grid plus a
   random salt (see GridCommitment.java). The commitment is public from purchase; the salt is withheld
   until reveal. After the player scratches, we re-hash the now-visible grid with the disclosed salt and
   confirm it matches the commitment that was fixed before purchase — proving the outcome pre-existed. */

/* Rebuilds the canonical string the backend hashed.
   MUST match GridCommitment.java's canonical encoding:
     salt + "|" + rows + "x" + cols + "|" + symbols.join(",")
   Symbols are each cell's abstract (mechanic) symbol in row-major order. The reveal serves the THEMED
   grid, whose cells carry `abstractSymbol` — which is exactly the mechanic symbol the backend hashed
   (the themed cell is skinned from the mechanic cell at the same position). Only the symbol is
   canonicalized; the prize value is deliberately excluded. */
function commitmentCanonical(grid, salt) {
    const dim = grid.dimension;
    const cells = [...grid.cells].sort((a, b) => a.row - b.row || a.col - b.col);
    const symbols = cells.map((c) => c.abstractSymbol).join(',');
    return `${salt}|${dim}x${dim}|${symbols}`;
}

/* Lowercase-hex SHA-256 of a string via WebCrypto. Returns null if crypto.subtle is unavailable
   (an insecure context) so callers can degrade to showing the commitment without verification. */
async function sha256Hex(text) {
    if (!(window.crypto && crypto.subtle && crypto.subtle.digest)) return null;
    const bytes = new TextEncoder().encode(text);
    const digest = await crypto.subtle.digest('SHA-256', bytes);
    return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, '0')).join('');
}

/* Before scratching: shows the committed hash under the card, proving it was fixed before purchase.
   Renders nothing when there is no commitment (legacy tickets). Leaves an empty verdict slot that
   verifyCommitmentProof() fills in once the ticket is scratched. */
function renderCommitmentProof(container, commitment) {
    if (!container) return;
    if (!commitment) { container.innerHTML = ''; return; }
    const short = escapeHtml(commitment.slice(0, 16));
    container.innerHTML = `
        <p class="commitment-line" title="${escapeHtml(commitment)}">🔒 Committed: sha256 ${short}…</p>
        <p class="commitment-note">This hash was fixed when the pool was printed — before you bought the ticket.</p>
        <p class="commitment-verdict" id="commitment-verdict" hidden></p>`;
}

/* After reveal: recomputes the hash client-side from the revealed grid + disclosed salt and compares
   it to the commitment. Match => the exact grid pre-existed; mismatch should never happen. If WebCrypto
   is unavailable, degrades silently (the committed line already stands on its own). */
async function verifyCommitmentProof(outcome) {
    const verdict = document.getElementById('commitment-verdict');
    if (!verdict) return;
    if (!outcome.gridCommitment || !outcome.commitmentSalt || !outcome.grid) return;
    let recomputed;
    try {
        recomputed = await sha256Hex(commitmentCanonical(outcome.grid, outcome.commitmentSalt));
    } catch (e) { recomputed = null; }
    if (recomputed === null) return; // insecure context: no error, just no verification badge
    verdict.hidden = false;
    if (recomputed === outcome.gridCommitment) {
        verdict.className = 'commitment-verdict ok';
        verdict.textContent = '✓ Proof checks out — this exact grid existed before purchase';
    } else {
        verdict.className = 'commitment-verdict bad';
        verdict.textContent = '⚠ commitment mismatch';
    }
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
                <div id="banner" class="result-banner" role="status" aria-live="polite" hidden></div>
            </div>
            <p class="scratch-instructions" id="scratch-progress" aria-live="polite">Scratch each panel to uncover what this
                ticket was always going to be.</p>
            <div class="commitment-proof" id="commitment-proof"></div>
            <div class="loss-chasing-panel" id="loss-chasing" role="status" hidden></div>
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
    // Highlighting and the result detail are backend-served: the narrative flags the cells that count
    // and supplies the summary sentence. If it is absent (guard tripped), degrade gracefully — the
    // banner shows the prize only, with no detail line, and no cells are highlighted.
    const matchedSet = matchedPositionSet(outcome.narrative);
    drawRevealLayer(revealLayer, zones, t.mechanic, gridData, matchedSet);

    // Commit-reveal: show the committed hash NOW (before the player scratches), then verify it against
    // the revealed grid + salt once the coating is off.
    renderCommitmentProof(document.getElementById('commitment-proof'), outcome.gridCommitment);

    const onReveal = async () => {
        const detail = outcome.narrative ? outcome.narrative.summary : '';
        showResult(outcome, won, detail, t);
        verifyCommitmentProof(outcome);
        await refreshPlayer();
    };
    const controller = initScratch(canvas, art, onReveal);

    // Particle layer: shavings off the pointer while scratching, a glint burst per cleared panel.
    const fx = createStageFx(document.getElementById('fx-canvas'));
    canvas.addEventListener('scratchstroke', (e) => { fx.shavings(e.detail.x, e.detail.y); Sounds.scratch(); });

    // Per-zone feedback: pop the uncovered tile and advance the progress line.
    canvas.addEventListener('zonereveal', (e) => {
        Sounds.zone();
        const tile = revealLayer.querySelector(`[data-zone="${e.detail.zoneId}"]`);
        if (tile) tile.classList.add('revealed');
        const zone = zones.find((z) => z.id === e.detail.zoneId);
        if (zone) {
            // Map the zone's fractional centre onto the FX layer's own pixel space. The scratch canvas's
            // backing store is DPR-scaled by the engine, so its width/height are device pixels; the fx
            // canvas stays at the logical 360×640 the burst draws into — use its dims (fall back to the
            // scratch canvas if it is ever absent).
            const fxc = document.getElementById('fx-canvas') || canvas;
            const cx = (zone.shape === 'circle' ? zone.cx : zone.x + zone.w / 2) * fxc.width;
            const cy = (zone.shape === 'circle' ? zone.cy : zone.y + zone.h / 2) * fxc.height;
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
    if (won) { Sounds.win(); burstConfetti(document.getElementById('stage')); } else { Sounds.lose(); }
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

    // In-the-moment loss-chasing nudge: only after a LOSS, and only if the domain's loss-chasing
    // insight actually fires. Non-blocking, never shown on a win, and silent if the fetch fails.
    if (!won && state.player) {
        Api.insights(state.player.playerId).then((list) => {
            const panel = document.getElementById('loss-chasing');
            if (!panel) return; // navigated away
            const lc = (list || []).find((i) => i.type === 'LOSS_CHASING');
            if (!lc) return;
            panel.innerHTML = `
                <span class="lc-title">⚠ ${escapeHtml(lc.title || 'Loss chasing')}</span>
                <span class="lc-msg">${escapeHtml(lc.message || '')}</span>
                <a class="lc-link" href="#ledger">See your story →</a>`;
            panel.hidden = false;
        }).catch(() => { /* best-effort: on failure, show nothing */ });
    }
}
