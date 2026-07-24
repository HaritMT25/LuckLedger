/* LuckLedger SPA — router + bootstrap.
 * ROUTES, route(), and ALL load-time bootstrapping (init, hashchange listener, initial route).
 * MUST load LAST: every name referenced at load time here is defined by an earlier module. */

// ---- router ----------------------------------------------------------------

const ROUTES = { dealer: renderDealers, scratch: renderScratch, ledger: renderLedger, house: renderHouse };

function route() {
    const raw = location.hash.replace('#', '') || 'dealer';
    const parts = raw.split('/');
    const name = parts[0];
    const param = parts[1];
    document.querySelectorAll('.tabs a').forEach((a) =>
        a.classList.toggle('active', a.dataset.route === name));
    // Re-trigger the view entrance animation on every route change.
    view.classList.remove('view-enter');
    void view.offsetWidth;
    view.classList.add('view-enter');
    if (name === 'dealer' && param) { renderDealerBooks(decodeURIComponent(param)); return; }
    // #house/campaign/<id> — the campaign analytics detail, following the #dealer/<id> pattern.
    if (name === 'house' && param === 'campaign' && parts[2]) {
        renderCampaignAnalytics(decodeURIComponent(parts[2]));
        return;
    }
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
