/* LuckLedger SPA — your-story (ledger) view.
 * renderLedger plus its section helpers: designed-RTP, RTP-convergence and inevitability
 * charts, dealer comparison, insights, and the transaction table. Order managed by index.html. */

// ---- ledger ----------------------------------------------------------------

async function renderLedger() {
    if (!state.player) return;
    view.innerHTML = `<div class="section-title"><h2>📖 Your story</h2>
        <span class="hint">Here is what gambling actually did to your balance — every coin, and what the
            numbers really say.</span></div>
        <div id="ledger-body">${skeletonCards(3)}</div>`;
    try {
        const pid = state.player.playerId;
        const [p, txns, insights, curve, dealerCmp, games] = await Promise.all([
            Api.getPlayer(pid),
            Api.transactions(pid, 200),
            Api.insights(pid).catch(() => []),
            Api.curve(pid).catch(() => []),
            Api.dealerComparison(pid).catch(() => ({})),
            loadGames().catch(() => []),
        ]);
        // Personal RTP and net position are derived here, straight from the player's own totals, so the
        // headline mirrors exactly what the ledger recorded (won ÷ spent, won − spent).
        const spent = Number(p.totalSpent);
        const won = Number(p.totalWon);
        const net = won - spent;
        const personalRtp = spent > 0 ? `${Math.round((won / spent) * 100)}%` : '—';
        const designedRtp = await designedRtpFraction(txns, games); // fraction, or null when unknown
        const body = document.getElementById('ledger-body');
        if (!body) return;
        body.innerHTML = `
            <div class="stats">
                ${stat('Borrowed', money(p.totalBorrowed))}
                ${stat('Spent', money(p.totalSpent))}
                ${stat('Won back', money(p.totalWon))}
                ${stat('Your RTP', personalRtp)}
                ${stat('Net position', (net >= 0 ? '+' : '') + money(net), net >= 0 ? 'good' : 'bad')}
            </div>
            ${rtpConvergenceSection(curve, designedRtp)}
            ${curveSection(curve)}
            ${dealerComparisonSection(dealerCmp)}
            ${insightsSection(insights)}
            <div class="section-title"><h3>Every transaction</h3>
                <a class="hint download-csv" href="/api/ledger/${pid}/transactions.csv">⬇ Download CSV</a></div>
            <p class="hint">Your whole history, yours to keep — open it in any spreadsheet.</p>
            ${txns.length ? txnTable(txns.slice(0, 25)) : '<p class="empty">No transactions yet — borrow and play.</p>'}`;
    } catch (e) {
        document.getElementById('ledger-body').innerHTML = `<p class="empty">${escapeHtml(e.message)}</p>`;
    }
}

/**
 * Designed payout ratio for THIS player's chart, as a fraction (null when nothing is known).
 * Weighted by the player's own spend per game — a flat average across every game in the catalogue
 * would let an unplayed campaign drag the "designed" line away from the games the player actually
 * bought into. Falls back to the plain catalogue average when the player has no attributable spend.
 */
async function designedRtpFraction(txns, games) {
    const ratioByGame = new Map((games || [])
        .filter((g) => Number.isFinite(Number(g.payoutRatio)) && Number(g.payoutRatio) > 0)
        .map((g) => [g.gameId, Number(g.payoutRatio)]));
    const spendByBook = new Map();
    (txns || []).forEach((t) => {
        if (t.type !== 'SPEND' || !t.bookId) return;
        spendByBook.set(t.bookId, (spendByBook.get(t.bookId) || 0) + Number(t.amount));
    });
    const books = await Promise.all(
        [...spendByBook.keys()].map((id) => Api.book(id).catch(() => null)));
    let weighted = 0;
    let weight = 0;
    books.filter(Boolean).forEach((b) => {
        const ratio = ratioByGame.get(b.gameId);
        if (ratio === undefined) return;
        const spend = spendByBook.get(b.bookId) || 0;
        weighted += ratio * spend;
        weight += spend;
    });
    if (weight > 0) return weighted / weight;
    const all = [...ratioByGame.values()];
    return all.length ? all.reduce((a, b) => a + b, 0) / all.length : null;
}

/* The lesson chart: the player's own cumulative RTP (won ÷ spent after each ticket) walking toward the
   RTP the house designed in. Fed by /curve — each CurvePoint carries cumulativeSpent and cumulativeWon,
   so RTP at ticket n is simply cumulativeWon(n) / cumulativeSpent(n). Points before any spend (spent = 0)
   are skipped; with fewer than two plottable points we show copy instead of an empty chart. */
function rtpConvergenceSection(curve, designedFrac) {
    const pts = (curve || [])
        .filter((p) => Number(p.cumulativeSpent) > 0)
        .map((p) => ({ n: p.ticketNumber, rtp: (Number(p.cumulativeWon) / Number(p.cumulativeSpent)) * 100 }));
    const designedPct = designedFrac != null ? designedFrac * 100 : null;
    if (pts.length < 2) {
        return `<div class="chart-panel"><h3>Your RTP, ticket by ticket</h3>
            <p class="empty">Scratch a few tickets and watch your personal return-to-player walk toward
                the house's number.</p></div>`;
    }
    const W = 640, H = 220, P = 40;
    const minX = pts[0].n, maxX = pts[pts.length - 1].n;
    const rtps = pts.map((p) => p.rtp);
    // Headroom so an early lucky spike and the designed line both sit inside the frame; rounded to 20s.
    let top = Math.max(100, designedPct || 0, ...rtps) * 1.1;
    top = Math.max(20, Math.ceil(top / 20) * 20);
    const x = (v) => P + ((v - minX) / Math.max(1, maxX - minX)) * (W - 2 * P);
    const y = (v) => H - P - (Math.min(v, top) / top) * (H - 2 * P);
    const poly = pts.map((p) => `${x(p.n).toFixed(1)},${y(p.rtp).toFixed(1)}`).join(' ');
    const lastRtp = Math.round(rtps[rtps.length - 1]);
    const designedRound = designedPct != null ? Math.round(designedPct) : null;
    const designedLine = designedPct != null
        ? `<line class="designed-line" x1="${P}" y1="${y(designedPct).toFixed(1)}"
               x2="${W - P}" y2="${y(designedPct).toFixed(1)}"/>
           <text class="designed-label" x="${W - P}" y="${(y(designedPct) - 6).toFixed(1)}"
               text-anchor="end">designed ${designedRound}%</text>`
        : '';
    const aria = designedRound != null
        ? `Your RTP after ${maxX} tickets: ${lastRtp}% versus designed ${designedRound}%`
        : `Your RTP after ${maxX} tickets: ${lastRtp}%`;
    const caption = designedRound != null
        ? `Early wins and losses are just variance. The longer you play, the closer your real return
            crawls toward the number the house built in — about ${designedRound}%. That was fixed before
            you scratched a thing.`
        : `Early wins and losses are just variance. The longer you play, the closer your real return
            crawls toward the house's designed number — fixed before you scratched a thing.`;
    return `<div class="chart-panel">
        <h3>Your RTP, ticket by ticket</h3>
        <p class="chart-sub">${caption}</p>
        <svg class="rtp-chart" viewBox="0 0 ${W} ${H}" role="img" aria-label="${escapeHtml(aria)}">
            <line x1="${P}" y1="${H - P}" x2="${W - P}" y2="${H - P}" class="axis"/>
            <line x1="${P}" y1="${P}" x2="${P}" y2="${H - P}" class="axis"/>
            <text x="${P - 6}" y="${P + 4}" class="axis-label" text-anchor="end">${top}%</text>
            <text x="${P - 6}" y="${H - P}" class="axis-label" text-anchor="end">0%</text>
            <text x="${W - P}" y="${H - P + 16}" class="axis-label" text-anchor="end">ticket ${maxX}</text>
            ${designedLine}
            <polyline class="line rtp" points="${poly}"/>
        </svg>
        <div class="legend">
            <span class="legend-item"><span class="swatch rtp"></span>Your RTP now <b>${lastRtp}%</b></span>
            ${designedRound != null
                ? `<span class="legend-item"><span class="swatch designed"></span>Designed ${designedRound}%</span>`
                : ''}
        </div>
    </div>`;
}

/** The surfaced insights: every observation the domain returned, its numbers shown verbatim. */
function insightsSection(insights) {
    if (!insights || !insights.length) {
        return `<div class="chart-panel"><h3>What your story says</h3>
            <p class="empty">Play a few tickets and your story starts writing itself.</p></div>`;
    }
    return `<div class="story-insights">
        <h3>What your story says</h3>
        ${insights.map(renderInsight).join('')}
    </div>`;
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
function renderInsight(ins) {
    const title = ins.title || ins.headline || ins.type || 'Insight';
    const msg = ins.message || ins.description || ins.detail || '';
    // Colour the card by the domain's own severity (INFO / WARNING / CRITICAL); the message already
    // carries the real numbers, so it is shown verbatim rather than paraphrased.
    const sev = String(ins.severity || '').toLowerCase();
    const sevClass = ['info', 'warning', 'critical'].includes(sev) ? ` sev-${sev}` : '';
    return `<div class="insight${sevClass}"><h4>${escapeHtml(title)}</h4><p>${escapeHtml(msg)}</p></div>`;
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
