/* Scratch-engine performance probe — reaches a real scratch canvas, then dispatches ~300 synthetic
 * pointer events across the card while a PerformanceObserver records long-task (main-thread-blocking)
 * time. Also grabs a half-scratched screenshot so the brush edge can be inspected. Run BEFORE and AFTER
 * the engine change (same app, rebuilt static) and compare the printed numbers.
 *   node perf-probe.js <label>     (label defaults to $PROBE_LABEL or 'run'; used in the shot name) */
const { chromium } = require('playwright');

const MASTER_PW = process.env.LUCKLEDGER_MASTER_PASSWORD || 'ci-e2e-password';
const BASE = process.env.E2E_BASE_URL || 'http://localhost:8080';
const LABEL = process.argv[2] || process.env.PROBE_LABEL || 'run';
const SHOTS = __dirname + '/shots';
require('fs').mkdirSync(SHOTS, { recursive: true });

(async () => {
    const browser = await chromium.launch({ headless: true });
    const page = await browser.newPage({ viewport: { width: 1280, height: 900 } });
    page.on('dialog', (d) => d.accept());

    // ---- navigate to a fresh scratch canvas (fund → shop → book → buy) --------
    await page.goto(BASE, { waitUntil: 'networkidle' });
    await page.waitForSelector('#borrow-btn', { timeout: 10000 });
    for (let i = 0; i < 2; i++) { await page.click('#borrow-btn'); await page.waitForTimeout(300); }
    await page.waitForSelector('.shop-card', { timeout: 10000 });
    await page.click('.shop-card');
    await page.waitForSelector('.book-card', { timeout: 10000 });
    const buy = await page.$('.book-card:not(.sold-out) button:has-text("Buy a ticket")');
    if (!buy) { console.error('no buyable book'); process.exit(2); }
    await buy.click();
    await page.waitForSelector('.scratch-canvas', { timeout: 30000 });
    await page.waitForTimeout(1500); // let the coating PNG land

    // ---- install a long-task observer in the page -----------------------------
    await page.evaluate(() => {
        window.__perf = { dispatchMs: 0, longtaskMs: 0, longtaskCount: 0 };
        try {
            const po = new PerformanceObserver((list) => {
                for (const e of list.getEntries()) { window.__perf.longtaskMs += e.duration; window.__perf.longtaskCount++; }
            });
            po.observe({ entryTypes: ['longtask'] });
            window.__perfObserver = po;
        } catch (_) { /* longtask unsupported: numbers stay 0 */ }
    });

    // A synchronous burst of pointer events over a rectangular band of the canvas. Records the wall time
    // the main thread spent inside the (synchronous) handlers — the input-latency cost we set out to cut.
    async function dispatchBand(yFrom, yTo, cols, down, up) {
        return page.evaluate(({ yFrom, yTo, cols, down, up }) => {
            const canvas = document.querySelector('.scratch-canvas');
            const r = canvas.getBoundingClientRect();
            const ev = (type, x, y) => canvas.dispatchEvent(new PointerEvent(type, {
                clientX: x, clientY: y, pointerId: 1, isPrimary: true, pressure: 0.5, bubbles: true, cancelable: true,
            }));
            const t0 = performance.now();
            const y0 = r.top + r.height * yFrom, y1 = r.top + r.height * yTo;
            const rows = 18;
            if (down) ev('pointerdown', r.left + 4, y0);
            for (let ri = 0; ri < rows; ri++) {
                const y = y0 + (y1 - y0) * (ri / (rows - 1));
                for (let ci = 0; ci < cols; ci++) {
                    const f = ri % 2 ? (1 - ci / (cols - 1)) : ci / (cols - 1); // serpentine
                    ev('pointermove', r.left + 4 + (r.width - 8) * f, y);
                }
            }
            if (up) ev('pointerup', r.left + r.width - 4, y1);
            window.__perf.dispatchMs += performance.now() - t0;
        }, { yFrom, yTo, cols, down, up });
    }

    // Chunk 1: scratch the TOP ~55% of the card, let it paint, snapshot the half-scratched state.
    await dispatchBand(0.02, 0.55, 8, true, false);
    await page.waitForTimeout(250); // let the batched rAF flush render
    await page.screenshot({ path: `${SHOTS}/perf-halfscratch-${LABEL}.png` });

    // Chunk 2: finish the rest of the card.
    await dispatchBand(0.55, 0.98, 8, false, true);
    await page.waitForTimeout(1200); // let rAF flushes + any dissolve settle while the observer runs

    const perf = await page.evaluate(() => { try { window.__perfObserver.disconnect(); } catch (_) {} return window.__perf; });
    console.log(JSON.stringify({ label: LABEL, ...perf }));

    await browser.close();
    process.exit(0);
})().catch((e) => { console.error('PROBE ERROR:', e.message); process.exit(2); });
