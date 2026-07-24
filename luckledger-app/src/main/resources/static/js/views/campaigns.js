/* LuckLedger SPA — master campaigns view.
 * The campaigns panel, campaign cards, the create form and its live RTP preview wiring,
 * and the campaign analytics route. Order managed by index.html. */

// ---- master campaigns ------------------------------------------------------

/* Plain-language captions for the near-miss modes and visibility tiers — the operator is choosing
   how the game manipulates perception, so the choice is spelled out, not hidden behind an enum. */
const NEAR_MISS_CAPTION = {
    CLEAN: 'CLEAN — losers look like losers.',
    REALISTIC: 'REALISTIC — 35% of losers are engineered to look one step from a win.',
};
const VISIBILITY_CAPTION = {
    NONE: 'NONE — the roll is sealed; players can\'t read its state.',
    PARTIAL: 'PARTIAL — players see how depleted the roll is.',
    FULL: 'FULL — the whole roll is open to players.',
};

/**
 * The master-only campaigns panel: a grid of campaign cards plus the collapsible "new campaign"
 * create form. Anonymous visitors never reach this — renderHouse only calls it when isMaster.
 */
function campaignsPanel(campaigns) {
    const cards = (campaigns && campaigns.length)
        ? `<div class="grid campaign-grid">${campaigns.map(campaignCard).join('')}</div>`
        : `<p class="empty">No campaigns yet. Design one below.</p>`;
    return `<div class="chart-panel campaigns-panel">
        <div class="campaigns-head">
            <h3>Campaigns</h3>
            <button class="btn secondary" id="toggle-campaign-form" type="button">+ New campaign</button>
        </div>
        <p class="chart-sub">Each campaign is a fixed pool: its return-to-player is Σ(prize×count) ÷
            (price×tickets), decided at print time. The house edge is chosen here, once — this dashboard
            just makes that explicit.</p>
        ${cards}
        <div id="campaign-form-wrap" hidden>${campaignForm()}</div>
    </div>`;
}

/** One campaign card: name (legacy games fall back to their game name), status pill, sold bar, actions. */
function campaignCard(c) {
    const name = c.name || c.gameName || 'Campaign';
    const emoji = MECHANIC_EMOJI[c.mechanic] || '🎟️';
    const active = c.status === 'ACTIVE';
    const pct = c.total ? Math.min(100, (c.sold / c.total) * 100) : 0;
    return `<div class="card campaign-card">
        <div class="campaign-card-head">
            <h4>${emoji} ${escapeHtml(name)}</h4>
            <span class="status-pill ${active ? 'status-active' : 'status-retired'}">${escapeHtml(c.status)}</span>
        </div>
        <p class="book-sub">${money(c.ticketPrice)} coins / ticket</p>
        <div class="house-bar"><div class="house-fill sold" style="width:${pct.toFixed(1)}%"></div></div>
        <p class="stock-label">${c.sold} of ${c.total} tickets sold</p>
        <div class="campaign-actions">
            <a class="btn secondary" href="#house/campaign/${c.gameId}">Analytics</a>
            <button class="btn secondary" data-restock="${c.gameId}">Restock books</button>
            ${active
                ? `<button class="btn secondary" data-retire="${c.gameId}">Retire</button>`
                : `<button class="btn secondary" data-activate="${c.gameId}">Activate</button>`}
        </div>
    </div>`;
}

/** The create-campaign form markup. Dynamic bits (tier value ladders, shops) are filled by setup. */
function campaignForm() {
    const mechOpts = Object.keys(MECHANIC_EMOJI)
        .map((m) => `<option value="${m}">${MECHANIC_EMOJI[m]} ${m.replace(/_/g, ' ')}</option>`)
        .join('');
    const nmRadios = ['REALISTIC', 'CLEAN'].map((m, i) => `
        <label class="radio-row">
            <input type="radio" name="nearMissMode" value="${m}"${i === 0 ? ' checked' : ''}>
            <span>${escapeHtml(NEAR_MISS_CAPTION[m])}</span>
        </label>`).join('');
    const visOpts = ['NONE', 'PARTIAL', 'FULL']
        .map((v) => `<option value="${v}">${escapeHtml(VISIBILITY_CAPTION[v])}</option>`).join('');
    return `<form id="campaign-form" class="campaign-form" autocomplete="off">
        <label class="field">Name
            <input class="campaign-input" name="name" maxlength="120" placeholder="Winter Fortune" required></label>
        <div class="field-row">
            <label class="field">Mechanic
                <select class="campaign-input" name="mechanicType">${mechOpts}</select></label>
            <label class="field">Ticket price
                <input class="campaign-input" name="price" type="number" min="0" step="0.01" value="5"></label>
        </div>
        <div class="field">Prize tiers
            <p class="field-note">Tier values must sit on the mechanic's fixed prize ladder — anything off
                the ladder fails verification at print time.</p>
            <div id="tier-rows"></div>
            <button type="button" class="btn ghost" id="add-tier">+ Add tier</button>
        </div>
        <div class="field-row">
            <label class="field">Total tickets (50–20000)
                <input class="campaign-input" name="totalTickets" type="number" min="50" max="20000" value="120"></label>
            <label class="field">Books (1–500)
                <input class="campaign-input" name="books" type="number" min="1" max="500" value="6"></label>
        </div>
        <div class="field">Near-miss mode
            <div class="radio-group">${nmRadios}</div></div>
        <label class="field">Book visibility
            <select class="campaign-input" name="bookVisibility">${visOpts}</select></label>
        <div class="field">Stock in shops
            <div id="shop-checks" class="shop-checks"><span class="cmp-sub">Loading shops…</span></div></div>
        <div class="rtp-preview" id="rtp-preview"></div>
        <button type="button" class="btn block" id="create-campaign" disabled>Create campaign</button>
    </form>`;
}

/** Attaches retire/activate confirmations, the form toggle, and the dynamic create form. */
function wireCampaignsPanel(body) {
    body.querySelectorAll('button[data-retire]').forEach((btn) => {
        btn.onclick = async () => {
            if (!confirm('Retire this campaign? Players can no longer buy it — sold tickets still '
                + 'reveal and pay out.')) return;
            btn.disabled = true;
            try { await Api.retireCampaign(btn.dataset.retire); toast('Campaign retired.'); renderHouse(); }
            catch (e) { btn.disabled = false; toast(e.message, true); }
        };
    });
    body.querySelectorAll('button[data-activate]').forEach((btn) => {
        btn.onclick = async () => {
            if (!confirm('Reactivate this campaign so players can buy it again?')) return;
            btn.disabled = true;
            try { await Api.activateCampaign(btn.dataset.activate); toast('Campaign reactivated.'); renderHouse(); }
            catch (e) { btn.disabled = false; toast(e.message, true); }
        };
    });
    const toggle = body.querySelector('#toggle-campaign-form');
    const wrap = body.querySelector('#campaign-form-wrap');
    if (toggle && wrap) {
        toggle.onclick = () => {
            const hidden = wrap.hasAttribute('hidden');
            if (hidden) wrap.removeAttribute('hidden'); else wrap.setAttribute('hidden', '');
            toggle.textContent = hidden ? '− New campaign' : '+ New campaign';
        };
    }
    setupCampaignForm(body);
}

/* The create form's live behaviour: a mechanic-driven prize-ladder select per tier, add/remove tier
   rows, and an RTP preview that shows an instant client-side estimate on every change plus a debounced
   POST /preview for the server's authoritative verdict. Create stays disabled until the server says the
   design is economically valid. */
function setupCampaignForm(root) {
    const form = root.querySelector('#campaign-form');
    if (!form) return;
    const tierBox = form.querySelector('#tier-rows');
    const mechSel = form.querySelector('[name=mechanicType]');
    const shopBox = form.querySelector('#shop-checks');
    const previewBox = form.querySelector('#rtp-preview');
    const createBtn = form.querySelector('#create-campaign');
    let ladder = [];       // [{ value }] distinct prize rungs, ascending
    let timer = null;

    const pct = (v) => `${(Number(v) * 100).toFixed(1)}%`;

    function ladderOptions(selected) {
        if (!ladder.length) return '<option value="">(ladder unavailable)</option>';
        return ladder.map((l) =>
            `<option value="${l.value}"${selected === l.value ? ' selected' : ''}>${money(l.value)} coins</option>`
        ).join('');
    }

    function addTierRow(value) {
        if (tierBox.querySelectorAll('.tier-row-form').length >= 10) {
            toast('A campaign can have at most 10 tiers.', true);
            return;
        }
        const div = document.createElement('div');
        div.className = 'tier-row-form';
        div.innerHTML = `
            <select class="campaign-input tier-value">${ladderOptions(value)}</select>
            <input class="campaign-input tier-count" type="number" min="1" step="1" value="1" title="How many tickets win this tier">
            <input class="campaign-input tier-label" type="text" maxlength="40" value="Prize" title="Tier label">
            <button type="button" class="btn secondary tier-remove" title="Remove tier">✕</button>`;
        tierBox.appendChild(div);
        div.querySelector('.tier-remove').onclick = () => { div.remove(); schedule(); };
        div.querySelectorAll('input, select').forEach((el) => el.addEventListener('input', schedule));
    }

    async function loadLadder() {
        try {
            const detail = await Api.mechanicDetail(mechSel.value);
            const seen = new Set();
            ladder = (detail.winRules || [])
                .map((r) => Number(r.prize))
                .filter((v) => v > 0 && !seen.has(v) && seen.add(v))
                .sort((a, b) => a - b)
                .map((v) => ({ value: v }));
        } catch (e) { ladder = []; }
        tierBox.innerHTML = '';
        addTierRow(ladder[0] && ladder[0].value);
        schedule();
    }

    function gather() {
        const tiers = [...tierBox.querySelectorAll('.tier-row-form')].map((r) => ({
            value: Number(r.querySelector('.tier-value').value),
            count: Number(r.querySelector('.tier-count').value),
            label: r.querySelector('.tier-label').value.trim() || 'Prize',
        }));
        const shopIds = [...shopBox.querySelectorAll('input:checked')].map((c) => c.value);
        const nmEl = form.querySelector('[name=nearMissMode]:checked');
        return {
            name: form.querySelector('[name=name]').value.trim(),
            mechanicType: mechSel.value,
            price: Number(form.querySelector('[name=price]').value),
            tiers,
            totalTickets: Number(form.querySelector('[name=totalTickets]').value),
            books: Number(form.querySelector('[name=books]').value),
            nearMissMode: nmEl ? nmEl.value : 'REALISTIC',
            bookVisibility: form.querySelector('[name=bookVisibility]').value,
            shopIds,
        };
    }

    function clientRtp(b) {
        const budget = b.tiers.reduce((s, t) => s + (t.value || 0) * (t.count || 0), 0);
        const denom = b.price * b.totalTickets;
        return denom > 0 ? budget / denom : 0;
    }

    /* Only bother the server once the request is structurally complete — otherwise @Valid just 400s. */
    function ready(b) {
        return !!b.name && b.price > 0 && b.totalTickets >= 50 && b.totalTickets <= 20000
            && b.books >= 1 && b.books <= 500 && b.shopIds.length > 0
            && b.tiers.length > 0 && b.tiers.every((t) => t.value > 0 && t.count > 0 && t.label);
    }

    function showEstimate(b) {
        previewBox.innerHTML = `
            <div class="preview-rtp">Estimated RTP <b>${pct(clientRtp(b))}</b>
                <span class="cmp-sub">${ready(b) ? 'checking with the server…' : 'complete the form for the server\'s verdict'}</span></div>`;
    }

    function showServer(pv, b) {
        const errs = (pv.errors || []).length
            ? `<ul class="preview-errors">${pv.errors.map((e) => `<li>${escapeHtml(e)}</li>`).join('')}</ul>`
            : '';
        previewBox.innerHTML = `
            <div class="preview-grid">
                <div><span class="preview-label">Designed RTP</span><b>${pct(pv.designedRtp)}</b></div>
                <div><span class="preview-label">Winners</span><b>${pv.winnerCount}</b></div>
                <div><span class="preview-label">Prize budget</span><b>${money(pv.prizeBudget)}</b></div>
                <div><span class="preview-label">Win frequency</span><b>${pct(pv.winFrequency)}</b></div>
            </div>
            <p class="preview-note ${pv.valid ? 'good' : 'bad'}">${pv.valid
                ? '✓ Economically valid — ready to print.'
                : '✗ This design cannot be printed.'}</p>
            ${errs}`;
    }

    function schedule() {
        const b = gather();
        createBtn.disabled = true;
        showEstimate(b);
        clearTimeout(timer);
        if (!ready(b)) return;
        timer = setTimeout(async () => {
            try {
                const pv = await Api.previewCampaign(b);
                showServer(pv, b);
                createBtn.disabled = !pv.valid;
            } catch (e) {
                previewBox.innerHTML = `<p class="preview-note bad">${escapeHtml(e.message)}</p>`;
                createBtn.disabled = true;
            }
        }, 400);
    }

    async function loadShops() {
        try {
            const dealers = await Api.dealers();
            if (!dealers.length) { shopBox.innerHTML = '<span class="cmp-sub">No shops seeded.</span>'; return; }
            shopBox.innerHTML = dealers.map((d) => `
                <label class="shop-check">
                    <input type="checkbox" value="${d.dealerId}">
                    <span>${escapeHtml(d.shopName)}</span></label>`).join('');
            shopBox.querySelectorAll('input').forEach((el) => el.addEventListener('change', schedule));
        } catch (e) { shopBox.innerHTML = `<span class="cmp-sub">${escapeHtml(e.message)}</span>`; }
    }

    // Any change to the non-tier fields re-runs the preview.
    form.querySelectorAll('[name=name], [name=price], [name=totalTickets], [name=books], '
        + '[name=nearMissMode], [name=bookVisibility]').forEach((el) => el.addEventListener('input', schedule));
    mechSel.addEventListener('change', loadLadder);
    form.querySelector('#add-tier').onclick = () => { addTierRow(ladder[0] && ladder[0].value); schedule(); };

    createBtn.onclick = async () => {
        const b = gather();
        if (!ready(b)) { toast('Complete the form before creating a campaign.', true); return; }
        createBtn.disabled = true;
        createBtn.textContent = 'Printing…';
        try {
            await Api.createCampaign(b);
            toast('Campaign created and stocked — verified before sale.');
            renderHouse();
        } catch (e) {
            createBtn.disabled = false;
            createBtn.textContent = 'Create campaign';
            toast(e.message, true);
        }
    };

    loadShops();
    loadLadder();
}

/** The analytics detail route (#house/campaign/<id>): design-vs-realized, per-shop, per-book depletion. */
async function renderCampaignAnalytics(gameId) {
    view.innerHTML = `<div class="section-title"><h2>📊 Campaign analytics</h2>
        <span class="hint"><a href="#house">← Back to the House</a></span></div>
        <div id="analytics-body">${skeletonCards(3)}</div>`;
    let a;
    try {
        a = await Api.campaignAnalytics(gameId);
    } catch (e) {
        // A lapsed session degrades to the anonymous house view, never a dead screen.
        if (e.status === 401 || e.status === 403) { state.master = null; location.hash = '#house'; return; }
        const b = document.getElementById('analytics-body');
        if (b) b.innerHTML = `<p class="empty">${escapeHtml(e.message)}</p>`;
        return;
    }
    const body = document.getElementById('analytics-body');
    if (!body) return; // navigated away
    const net = Number(a.houseNet);
    body.innerHTML = `
        <div class="analytics-head">
            <h3>${MECHANIC_EMOJI[a.mechanic] || '🎟️'} ${escapeHtml(a.name)}</h3>
            <span class="status-pill ${a.status === 'ACTIVE' ? 'status-active' : 'status-retired'}">${escapeHtml(a.status)}</span>
            <span class="badge">${money(a.ticketPrice)} coins / ticket</span>
        </div>
        <div class="stats">
            ${stat('Sold', a.sold)}
            ${stat('Remaining', a.remaining)}
            ${stat('Revealed', a.revealed)}
            ${stat('Gross sales', money(a.grossSales))}
            ${stat('Paid out', money(a.paidOut))}
            ${stat('House net', (net >= 0 ? '+' : '') + money(net), net >= 0 ? 'gold' : 'bad')}
        </div>
        <div class="chart-panel">
            <h3>Designed vs realized return</h3>
            <p class="chart-sub">The design was fixed at print time. What players actually get back drifts
                around it with variance — but never past it.</p>
            ${rtpBar('Designed RTP', a.designedRtp, 'fund', 'legacy game — no stored design')}
            ${rtpBar('Realized RTP', a.realizedRtp, 'sold', 'no sales yet')}
        </div>
        ${analyticsShops(a.shops)}
        ${analyticsBooks(a.books)}
        <div class="chart-panel">
            <h3>Near-miss engineering</h3>
            <p class="chart-sub">How this pool's losing tickets are shaped.</p>
            <p>${escapeHtml(NEAR_MISS_CAPTION[a.nearMissMode] || a.nearMissMode || 'Unknown')}</p>
            <p class="stock-label">Engineered near-miss rate
                <b>${Math.round(Number(a.engineeredNearMissRate) * 100)}%</b> of losing tickets.</p>
        </div>`;
}

/** One labelled RTP bar; renders a fallback message when the value is null. */
function rtpBar(label, rtp, fillCls, nullMsg) {
    if (rtp == null) {
        return `<div class="house-bar-row">
            <span class="house-bar-label">${label}</span>
            <div class="house-bar"><div class="house-fill ${fillCls}" style="width:0%"></div></div>
            <span class="house-bar-value">${escapeHtml(nullMsg)}</span></div>`;
    }
    const v = Number(rtp);
    return `<div class="house-bar-row">
        <span class="house-bar-label">${label}</span>
        <div class="house-bar"><div class="house-fill ${fillCls}" style="width:${Math.min(100, v * 100).toFixed(1)}%"></div></div>
        <span class="house-bar-value">${(v * 100).toFixed(1)}%</span></div>`;
}

/** Per-shop contribution table for a campaign's analytics. */
function analyticsShops(shops) {
    if (!shops || !shops.length) {
        return `<div class="chart-panel"><h3>By shop</h3><p class="empty">Not stocked in any shop yet.</p></div>`;
    }
    return `<div class="chart-panel">
        <h3>By shop</h3>
        <div class="table-scroll"><table>
            <thead><tr><th>Shop</th><th>Sold</th><th>Gross</th><th>Paid out</th><th>Net to house</th></tr></thead>
            <tbody>${shops.map((s) => {
                const net = Number(s.netToHouse);
                return `<tr>
                    <td>${escapeHtml(s.shopName)}</td>
                    <td>${s.ticketsSold}</td>
                    <td>${money(s.grossSales)}</td>
                    <td>${money(s.paidOut)}</td>
                    <td class="${net >= 0 ? 'net-good' : 'net-bad'}">${(net >= 0 ? '+' : '') + money(net)}</td>
                </tr>`;
            }).join('')}</tbody>
        </table></div>
    </div>`;
}

/** Per-book depletion bars for a campaign's analytics, each with its visibility badge. */
function analyticsBooks(books) {
    if (!books || !books.length) {
        return `<div class="chart-panel"><h3>By book</h3><p class="empty">No books.</p></div>`;
    }
    return `<div class="chart-panel">
        <h3>By book</h3>
        <p class="chart-sub">Each book depletes independently — same odds, different luck of the draw.</p>
        ${books.map((b) => {
            const pctSold = b.totalTickets ? Math.min(100, (b.sold / b.totalTickets) * 100) : 0;
            const badge = VISIBILITY_BADGE[b.visibility];
            return `<div class="book-depletion">
                <span class="book-dep-id">${escapeHtml(String(b.bookId).slice(0, 8))}</span>
                <span class="vis-badge ${badge ? badge.cls : ''}">${badge ? badge.label : escapeHtml(b.visibility || '')}</span>
                <div class="house-bar"><div class="house-fill sold" style="width:${pctSold.toFixed(1)}%"></div></div>
                <span class="book-dep-stat">${b.sold}/${b.totalTickets} · ${b.winsSoFar} wins ·
                    ${money(b.remainingValue)} left</span>
            </div>`;
        }).join('')}
    </div>`;
}
