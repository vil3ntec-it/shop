/**
 * F0-27 — کانالِ آزمایشیِ وب: `/shop/` (پایدار) و `/shop/beta/` روی یک دامنه.
 *
 * هر دو همان `sw.js` را دارند، ولی کش‌های Service Worker و حافظهٔ مرورگر مالِ
 * **کلِ دامنه**اند. پیش از این کار:
 *   • سرویس‌ورکرِ پایدار ناوبریِ `/shop/beta/` را هم می‌گرفت و پاسخش را جای
 *     `index.html`ِ **خودش** کش می‌کرد ⇒ پایدارِ آفلاین صفحهٔ آزمایشی را باز
 *     می‌کرد؛
 *   • فعال شدنِ نسخهٔ آزمایشیِ تازه‌تر همهٔ کش‌های «نه از نسخهٔ من» را پاک می‌کرد
 *     ⇒ پوستهٔ آفلاینِ پایدار می‌رفت.
 *
 * این‌جا یک وب‌سرورِ ایستا همان چیدمانِ GitHub Pages را می‌سازد (`sw.js`ِ واقعیِ
 * مخزن در هر دو؛ نسخهٔ آزمایشی یک شماره جلوتر) و کرومیوم هر دو را باز می‌کند،
 * بعد بی اینترنت.
 */
import { createRequire } from 'node:module';
import { missingTool, PLAYWRIGHT_INSTALL } from './browser-guard.mjs';
import assert from 'node:assert/strict';
import { execSync } from 'node:child_process';
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';

const require = createRequire(import.meta.url);
let chromium = null;
for (const p of [(() => { try { return `${execSync('npm root -g', { encoding: 'utf8' }).trim()}/playwright`; } catch { return null; } })(), 'playwright']) {
  if (!p) continue;
  try { ({ chromium } = require(p)); break; } catch { /* بعدی */ }
}
if (!chromium) missingTool('پلی‌رایت', PLAYWRIGHT_INSTALL);

const ROOT = path.join(import.meta.dirname, '..', '..');
const sw = fs.readFileSync(path.join(ROOT, 'sw.js'), 'utf8');
//  نسخهٔ آزمایشی همیشه جلوتر از پایدار است
const betaSw = sw.replace(/(tohid-shop-v)(\d+)/, (_, a, n) => a + (Number(n) + 1));
assert.notEqual(betaSw, sw, 'شمارهٔ VERSION در sw.js پیدا نشد');

const page = (channel) => `<!doctype html><html lang="fa"><meta charset="utf-8"><title>${channel}</title>
<body data-channel="${channel}">${channel}<script>navigator.serviceWorker.register('sw.js')</script></body></html>`;

const files = {
  '/shop/index.html': ['text/html', page('stable')],
  '/shop/sw.js': ['text/javascript', sw],
  '/shop/beta/index.html': ['text/html', page('beta')],
  '/shop/beta/sw.js': ['text/javascript', betaSw],
};
//  «بی اینترنت»: اتصال بی پاسخ بسته می‌شود. `context.setOffline` در کرومیوم به
//  fetchِ خودِ سرویس‌ورکر نمی‌رسد، پس این‌جا خودِ سرور قطع می‌شود
let down = false;
const server = http.createServer((req, res) => {
  if (down) { req.socket.destroy(); return; }
  let p = new URL(req.url, 'http://x').pathname;
  if (p.endsWith('/')) p += 'index.html';
  const f = files[p];
  if (!f) { res.writeHead(404); res.end(); return; }
  res.writeHead(200, { 'content-type': f[0] + '; charset=utf-8', 'cache-control': 'no-store' });
  res.end(f[1]);
});
await new Promise((r) => server.listen(0, '127.0.0.1', r));
const base = `http://127.0.0.1:${server.address().port}`;

let failures = 0;
const results = [];
async function step(name, fn) {
  try { await fn(); results.push(['✅', name]); }
  catch (err) { failures++; results.push(['❌', `${name} — ${err.message}`]); }
}

const browser = await chromium.launch();
try {
  const ctx = await browser.newContext();
  const tab = await ctx.newPage();
  const activeAndControlled = async () => {
    await tab.evaluate(() => navigator.serviceWorker.ready);
    if (!(await tab.evaluate(() => !!navigator.serviceWorker.controller))) await tab.reload();
    await tab.evaluate(() => navigator.serviceWorker.ready);
  };
  const cachedIndex = (url) => tab.evaluate(async (u) => {
    const hit = await caches.match(u);
    return hit ? hit.text() : null;
  }, url);
  //  هر بازکردن در زبانهٔ تازه، تا صفحهٔ خطای قبلی ناوبریِ بعدی را نبرد
  const channelAt = async (url) => {
    const t = await ctx.newPage();
    try {
      await t.goto(url);
      return await t.evaluate(() => document.body && document.body.dataset.channel);
    } finally { await t.close(); }
  };

  //  دکانی که پایدار را دارد و بعد یک بار آزمایشی را باز می‌کند
  await tab.goto(`${base}/shop/`);
  await activeAndControlled();
  await tab.goto(`${base}/shop/beta/`);
  await activeAndControlled();

  await step('ناوبریِ /beta/ جای index.htmlِ پایدار کش نمی‌شود', async () => {
    const html = await cachedIndex(`${base}/shop/index.html`);
    assert.ok(html && html.includes('data-channel="stable"'), `کشِ پایدار: ${html && html.slice(0, 120)}`);
  });

  await step('فعال شدنِ آزمایشی کشِ پایدار را پاک نمی‌کند', async () => {
    const keys = await tab.evaluate(() => caches.keys());
    assert.ok(keys.some((k) => /^tohid-shop-v\d+-shell$/.test(k)), `کش‌ها: ${keys.join(', ')}`);
    assert.ok(keys.some((k) => /^beta-tohid-shop-v\d+-shell$/.test(k)), `کش‌ها: ${keys.join(', ')}`);
  });

  down = true;
  await step('بی اینترنت: /shop/ همان پایدار است', async () => {
    assert.equal(await channelAt(`${base}/shop/`), 'stable');
  });
  await step('بی اینترنت: /shop/beta/ همان آزمایشی است', async () => {
    assert.equal(await channelAt(`${base}/shop/beta/`), 'beta');
  });
  down = false;

  //  و برعکس: نسخهٔ پایدارِ تازه (پس از Run workflow ⇒ stable) کشِ آزمایشی را نمی‌برد
  await step('فعال شدنِ پایدارِ تازه کشِ آزمایشی را پاک نمی‌کند', async () => {
    files['/shop/sw.js'][1] = sw.replace(/(tohid-shop-v)(\d+)/, (_, a, n) => a + (Number(n) + 2));
    const t = await ctx.newPage();
    await t.goto(`${base}/shop/`);
    await t.evaluate(async () => {
      const reg = await navigator.serviceWorker.getRegistration();
      await reg.update();
      const w = reg.installing || reg.waiting;
      if (w) {
        await new Promise((r) => {
          const done = () => { if (w.state === 'installed' || w.state === 'activated') r(); };
          w.addEventListener('statechange', done); done();
        });
        w.postMessage('skip-waiting');
        await new Promise((r) => {
          if (w.state === 'activated') { r(); return; }
          w.addEventListener('statechange', () => { if (w.state === 'activated') r(); });
        });
      }
    });
    const keys = await t.evaluate(() => caches.keys());
    await t.close();
    assert.ok(keys.some((k) => /^beta-tohid-shop-v\d+-shell$/.test(k)), `کش‌ها: ${keys.join(', ')}`);
    assert.ok(keys.some((k) => k.startsWith('tohid-shop-v') && k.endsWith('-shell')), `کش‌ها: ${keys.join(', ')}`);
  });
} finally {
  await browser.close();
  server.close();
}

for (const [mark, name] of results) console.log(`${mark} ${name}`);
if (failures) {
  console.error(`\n${failures} سنجه رد شد`);
  process.exit(1);
}
console.log(`\nهمه ${results.length} سنجه قبول`);
