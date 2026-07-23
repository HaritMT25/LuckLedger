/* Hands-on UI pass for LuckLedger — headless chromium click-through of the whole SPA. */
const { chromium } = require('playwright');

const MASTER_PW = process.env.LUCKLEDGER_MASTER_PASSWORD || process.argv[2];
if (!MASTER_PW) {
    console.error('ERROR: master password required. Set LUCKLEDGER_MASTER_PASSWORD ' +
        'or pass it as the first CLI argument (node ui-pass.js <password>).');
    process.exit(2);
}
const BASE = process.env.E2E_BASE_URL || 'http://localhost:8080';
const SHOTS = __dirname + '/shots';
require('fs').mkdirSync(SHOTS, { recursive: true });

// Unique per run so reruns against a persistent DB never collide.
const CAMPAIGN_NAME = 'UI Pass Campaign ' + process.pid + '-' + Date.now();

const results = [];
function check(name, cond, detail) {
    results.push({ name, ok: !!cond, detail });
    console.log((cond ? 'PASS ' : 'FAIL ') + name + (cond ? '' : `  [${String(detail).slice(0, 160)}]`));
}

(async () => {
    const browser = await chromium.launch({ headless: true });
    const page = await browser.newPage({ viewport: { width: 1280, height: 900 } });
    const consoleErrors = [];
    page.on('console', (m) => { if (m.type() === 'error') consoleErrors.push(m.text()); });
    page.on('pageerror', (e) => consoleErrors.push('PAGEERROR: ' + e.message));
    page.on('dialog', (d) => d.accept());

    // ---- 1. load + player bar + sound toggle -------------------------------
    await page.goto(BASE, { waitUntil: 'networkidle' });
    await page.waitForSelector('#sound-btn', { timeout: 10000 });
    check('page loads, player bar renders with sound toggle', true);
    const pressed = await page.getAttribute('#sound-btn', 'aria-pressed');
    await page.click('#sound-btn');
    const pressed2 = await page.getAttribute('#sound-btn', 'aria-pressed');
    check('sound toggle flips aria-pressed', pressed !== pressed2, `${pressed}->${pressed2}`);
    await page.click('#sound-btn'); // back on

    // fund the fresh guest player (starts broke by design — borrowing IS the lesson)
    for (let i = 0; i < 2; i++) { await page.click('#borrow-btn'); await page.waitForTimeout(400); }

    // ---- 2. shops view: cards + leaderboard + debunk -----------------------
    await page.waitForSelector('.shop-card', { timeout: 10000 });
    const nShops = (await page.$$('.shop-card')).length;
    check('shop cards render', nShops > 0, nShops);
    const lb = await page.waitForSelector('.leaderboard, #leaderboard', { timeout: 10000 }).catch(() => null);
    check('leaderboard panel renders', !!lb);
    const debunk = await page.textContent('body');
    check('per-book-odds debunk line present', /identical per-ticket odds/i.test(debunk));
    await page.screenshot({ path: SHOTS + '/1-shops.png' });

    // ---- 3. shop books: visibility badges + mechanic modal -----------------
    await page.click('.shop-card');
    await page.waitForSelector('.book-card', { timeout: 10000 });
    const badges = (await page.$$('.vis-badge')).length;
    check('book cards show visibility badges', badges > 0, badges);
    const how = await page.$('text=How this game works');
    check('"How this game works" affordance present', !!how);
    if (how) {
        await how.click();
        await page.waitForSelector('.modal-card .rules-table tr', { timeout: 8000 }).catch(() => null);
        const rows = (await page.$$('.modal-card .rules-table tr')).length;
        check('mechanic modal opens with win-rules ladder', rows >= 3, rows);
        await page.screenshot({ path: SHOTS + '/2-mechanic-modal.png' });
        await page.click('.modal-close');
    }

    // ---- 4. buy + scratch + banner/narrative -------------------------------
    const buy = await page.$('.book-card:not(.sold-out) button:has-text("Buy a ticket")');
    check('buy button available', !!buy);
    await buy.click();
    await page.waitForSelector('.scratch-canvas', { timeout: 30000 });
    await page.waitForTimeout(1500); // let the coating PNG land before scratching
    const cursor = await page.$eval('.scratch-canvas', (el) => getComputedStyle(el).cursor);
    check('coin cursor on scratch canvas', cursor.includes('url("data:'), cursor.slice(0, 60));
    const prog = await page.getAttribute('#scratch-progress', 'aria-live');
    check('#scratch-progress aria-live', prog === 'polite', prog);
    const bannerRole = await page.getAttribute('#banner', 'role');
    const bannerLive = await page.getAttribute('#banner', 'aria-live');
    check('banner role=status aria-live=polite', bannerRole === 'status' && bannerLive === 'polite',
        `${bannerRole}/${bannerLive}`);

    // scratch: sweep the whole canvas in dense rows until the banner appears
    const box = await (await page.$('.scratch-canvas')).boundingBox();
    for (let pass = 0; pass < 3; pass++) {
        for (let y = 4; y < box.height; y += 7) {
            await page.mouse.move(box.x + 2, box.y + y);
            await page.mouse.down();
            for (let x = 2; x < box.width; x += 12) await page.mouse.move(box.x + x, box.y + y);
            await page.mouse.up();
        }
        const done = await page.$('#banner:not([hidden])');
        if (done) break;
    }
    const banner = await page.waitForSelector('#banner:not([hidden])', { timeout: 15000 }).catch(() => null);
    check('scratch completes, result banner shows', !!banner);
    if (banner) {
        const text = (await banner.textContent()).trim();
        check('banner carries backend narrative detail', text.length > 12, text.slice(0, 100));
        console.log('   banner: ' + text.slice(0, 140));
    }
    await page.screenshot({ path: SHOTS + '/3-scratched.png' });

    // ---- 5. ledger view ----------------------------------------------------
    await page.click('a[data-route="ledger"], a[href="#ledger"]');
    await page.waitForTimeout(800);
    check('ledger view renders without console errors so far', consoleErrors.length === 0, consoleErrors[0]);

    // ---- 6. house anonymous ------------------------------------------------
    await page.goto(BASE + '/#house', { waitUntil: 'networkidle' });
    await page.waitForSelector('#master-login-link', { timeout: 10000 });
    check('anonymous house shows log-in card', true);
    check('anonymous house hides campaigns panel', !(await page.$('#toggle-campaign-form')));
    await page.screenshot({ path: SHOTS + '/4-house-anon.png' });

    // ---- 7. master login ---------------------------------------------------
    await page.click('#master-login-link');
    await page.waitForSelector('#login-form', { timeout: 10000 });
    await page.fill('#login-form input[name="username"], #login-form input[type="text"]', 'master');
    await page.fill('#login-form input[type="password"]', MASTER_PW);
    await page.click('#login-form button[type="submit"], #login-form .btn');
    await page.waitForSelector('#logout-btn', { timeout: 10000 });
    check('master login succeeds, master bar renders', true);

    // ---- 8. campaigns panel + create form ----------------------------------
    await page.waitForSelector('#toggle-campaign-form', { timeout: 10000 });
    const cards = (await page.$$('[data-retire], [data-activate]')).length;
    check('campaign cards render (seeded games stamped)', cards >= 2, cards);
    await page.click('#toggle-campaign-form');
    await page.waitForSelector('#campaign-form:visible', { timeout: 5000 });
    await page.fill('#campaign-form input[name="name"]', CAMPAIGN_NAME);
    await page.waitForSelector('#tier-rows select', { timeout: 8000 });
    const ladderOpts = await page.$$eval('#tier-rows select >> nth=0 >> option', (os) => os.map((o) => o.value));
    check('tier value select driven by real ladder', ladderOpts.length >= 3, ladderOpts.join(','));
    await page.waitForSelector('#shop-checks input[type="checkbox"]', { timeout: 8000 });
    const boxes = await page.$$('#shop-checks input[type="checkbox"]');
    for (const b of boxes.slice(0, 3)) await b.check();
    await page.waitForFunction(() => {
        const el = document.querySelector('#rtp-preview');
        return el && /%/.test(el.textContent);
    }, { timeout: 10000 }).catch(() => null);
    const preview = await page.textContent('#rtp-preview');
    check('live RTP preview shows server truth', /%/.test(preview || ''), (preview || '').slice(0, 80));
    await page.screenshot({ path: SHOTS + '/5-campaign-form.png' });
    await page.waitForFunction(() => !document.querySelector('#create-campaign').disabled, { timeout: 10000 })
        .catch(() => null);
    const createEnabled = await page.$eval('#create-campaign', (el) => !el.disabled);
    check('Create enabled once server preview valid', createEnabled);
    if (createEnabled) {
        await page.click('#create-campaign');
        await page.waitForFunction((name) => document.body.textContent.includes(name),
            CAMPAIGN_NAME, { timeout: 15000 });
        check('campaign created, card appears', true);
    }
    await page.screenshot({ path: SHOTS + '/6-campaigns.png' });

    // ---- 9. analytics route + retire/activate ------------------------------
    const analyticsBtn = await page.$('a[href*="#house/campaign/"], button:has-text("Analytics")');
    check('analytics affordance present', !!analyticsBtn);
    if (analyticsBtn) {
        await analyticsBtn.click();
        // Wait for the analytics route to take hold AND its own stat tiles to render,
        // rather than racing the post-create re-render that flips the view back. Scope
        // the wait to #analytics-body (the analytics view's container) so we never count
        // the House overview's stat tiles by accident, and give a cold post-create
        // analytics query room to resolve.
        await page.waitForFunction(() => location.hash.includes('campaign'), { timeout: 10000 })
            .catch(() => null);
        await page.waitForSelector('#analytics-body .stat', { timeout: 15000 }).catch(() => null);
        const statTiles = (await page.$$('#analytics-body .stat')).length;
        check('analytics detail renders stat tiles', statTiles >= 4, statTiles);
        await page.screenshot({ path: SHOTS + '/7-analytics.png' });
        await page.goto(BASE + '/#house', { waitUntil: 'networkidle' });
        await page.waitForSelector('#toggle-campaign-form', { timeout: 10000 });
    }
    const retire = await page.$('button[data-retire]');
    if (retire) {
        await retire.click(); // dialog auto-accepted
        await page.waitForSelector('button[data-activate]', { timeout: 10000 });
        check('retire flips card to RETIRED (activate appears)', true);
        const act = await page.$('button[data-activate]');
        await act.click();
        await page.waitForFunction(() => document.querySelectorAll('button[data-retire]').length >= 1,
            { timeout: 10000 });
        check('activate restores ACTIVE', true);
    }

    // ---- 10. console errors ------------------------------------------------
    check('zero console/page errors across the whole pass', consoleErrors.length === 0,
        consoleErrors.slice(0, 3).join(' | '));

    await browser.close();
    const fails = results.filter((r) => !r.ok);
    console.log(`\n=== UI PASS: ${results.length - fails.length}/${results.length} passed ===`);
    fails.forEach((f) => console.log('FAILED: ' + f.name + ' — ' + String(f.detail).slice(0, 200)));
    process.exit(fails.length ? 1 : 0);
})().catch((e) => { console.error('SCRIPT ERROR:', e.message); process.exit(2); });
