/* LuckLedger SPA — house (operator) view.
 * renderHouse, the master bar/tools card/login, the per-game economics panel, and the
 * player roster. Campaign helpers live in views/campaigns.js. Order managed by index.html. */

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
        const [o, roster, campaignsRes] = await Promise.all([
            Api.house(),
            isMaster ? Api.masterPlayers().then((p) => ({ players: p }), (e) => ({ error: e }))
                     : Promise.resolve(null),
            isMaster ? Api.campaigns().then((c) => ({ campaigns: c }), (e) => ({ error: e }))
                     : Promise.resolve(null),
        ]);
        let players = null;
        let campaigns = null;
        // A lapsed session (401/403) on EITHER master call drops the stale master state and falls
        // back to the anonymous view, exactly like the roster path — never a dead operator UI.
        const lapsed = (r) => r && r.error && (r.error.status === 401 || r.error.status === 403);
        if (lapsed(roster) || lapsed(campaignsRes)) {
            state.master = null;
            isMaster = false;
        }
        if (isMaster) {
            if (roster && !roster.error) players = roster.players;
            if (campaignsRes && !campaignsRes.error) campaigns = campaignsRes.campaigns;
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
            ${isMaster ? campaignsPanel(campaigns) : ''}
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
        wireCampaignsPanel(body);
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
