/**
 * bench — کارایی نسخهٔ وب با مرورگرِ واقعی (F0-26، بودجه‌های بخشِ ۹.۲).
 *
 *   node bench/web.mjs                                  # ۱۰۰۰ و ۲۰۰۰۰ کالا، ۵ دور
 *   node bench/web.mjs --products 1000,20000 --reps 7 --scans 40
 *   node bench/web.mjs --cpu 4                          # کندسازیِ CPU (CDP) — تقریبِ کامپیوترِ ضعیف
 *   node bench/web.mjs --channel msedge                 # Edge/Chrome ِ نصب‌شده به جای Chromium ِ پلی‌رایت
 *   node bench/web.mjs --json out.json                  # خروجیِ ماشین‌خوان
 *   node bench/web.mjs --compare base.json --max-regress 10   # افتِ بیش از ۱۰٪ ⇒ کدِ خروج ۱
 *
 * چه چیزی سنجیده می‌شود (همه با `performance.now()` داخلِ خودِ صفحه):
 *   • بالا آمدن: از شروعِ ناوبری تا نخستین فریم پس از رویدادِ load
 *   • باز شدنِ هر صفحه: `goTo(صفحه)` تا نخستین فریمِ کشیده‌شده (rAF + setTimeout)
 *   • اسکن تا ردیف: Enter ِ بارکدخوانِ کیبوردی (همان مسیرِ handleHardwareScannerKeydown)
 *     تا نخستین فریمی که ردیفِ تازه در سبد است
 *   • حجمِ دفتر و این‌که در localStorage جا می‌شود یا نه؛ حافظهٔ JS (performance.memory)
 *
 * اگر دفتر در localStorage جا نشود (سقفِ حدودِ ۵ میلیون نویسه)، همان کاری
 * می‌شود که همگام‌سازی می‌کند: دفتر با `applyServerLedger` فقط در حافظهٔ صفحه
 * می‌نشیند، و این در خروجی با «inMemory» نوشته می‌شود — بالا آمدن در آن حالت
 * با دفترِ خالی سنجیده شده و قابلِ مقایسه نیست.
 *
 * نیاز: Node 20+ و پلی‌رایت (`npm i -g playwright@1.56.1` و
 * `npx playwright install chromium`، یا --channel با مرورگرِ نصب‌شده).
 */
import { createRequire } from 'node:module';
import { execSync } from 'node:child_process';
import http from 'node:http';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { makeLedger, ean13, FIXED } from './datagen.mjs';

const arg = (k, d) => { const i = process.argv.indexOf(k); return i > 0 && process.argv[i + 1] ? process.argv[i + 1] : d; };
const SIZES = arg('--products', '1000,20000').split(',').map(Number).filter(Boolean);
const REPS = Number(arg('--reps', 5));
const SCANS = Number(arg('--scans', 30));
const CPU = Number(arg('--cpu', 1));
const CHANNEL = arg('--channel', '');
const JSON_OUT = arg('--json', '');
const COMPARE = arg('--compare', '');
const MAX_REGRESS = Number(arg('--max-regress', 10));

const require = createRequire(import.meta.url);
const globalRoot = (() => { try { return execSync('npm root -g', { encoding: 'utf8' }).trim(); } catch { return ''; } })();
const pw = (() => { for (const p of [`${globalRoot}/playwright`, 'playwright']) { try { return require(p); } catch { /* بعدی */ } } return null; })();
if (!pw) { console.error('پلی‌رایت پیدا نشد: npm i -g playwright@1.56.1 && npx playwright install chromium'); process.exit(2); }

const ROOT = path.join(import.meta.dirname, '..');
const TYPES = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.json': 'application/json', '.png': 'image/png', '.svg': 'image/svg+xml', '.webmanifest': 'application/manifest+json', '.woff2': 'font/woff2', '.mp3': 'audio/mpeg' };
const server = http.createServer((req, res) => {
  const file = path.join(ROOT, decodeURIComponent(req.url.split('?')[0]).replace(/^\/+/, '') || 'index.html');
  if (!file.startsWith(ROOT) || !fs.existsSync(file) || fs.statSync(file).isDirectory()) { res.statusCode = 404; return res.end(); }
  res.setHeader('Content-Type', TYPES[path.extname(file)] || 'application/octet-stream');
  fs.createReadStream(file).pipe(res);
});
await new Promise((r) => server.listen(0, '127.0.0.1', r));
const URL_ = `http://127.0.0.1:${server.address().port}/index.html`;

const PAGES = ['dashboard', 'products', 'warehouse', 'sale', 'sales', 'debtors', 'expenses', 'reports', 'purchasing', 'settings', 'more'];
const AUTH = JSON.stringify({ method: 'local', value: '', name: 'bench', loginAt: 1 });

const stat = (xs) => {
  const s = xs.slice().sort((a, b) => a - b);
  const q = (p) => s[Math.min(s.length - 1, Math.ceil(p * s.length) - 1)];
  const r1 = (v) => Math.round(v * 10) / 10;
  return { n: s.length, p50: r1(q(0.5)), p95: r1(q(0.95)), max: r1(s[s.length - 1]) };
};

const browser = await pw.chromium.launch({ ...(CHANNEL ? { channel: CHANNEL } : {}), args: ['--enable-precise-memory-info'] });
const result = {
  at: new Date().toISOString(),
  env: { cpu: os.cpus()[0]?.model, cores: os.cpus().length, memGB: Math.round(os.totalmem() / 2 ** 30), os: `${os.platform()} ${os.release()}`, node: process.version, browser: `${CHANNEL || 'chromium'} ${browser.version()}`, cpuThrottle: CPU },
  fixed: FIXED, sizes: [],
};

for (const N of SIZES) {
  const ledger = makeLedger(N);
  const json = JSON.stringify(ledger);
  const ctx = await browser.newContext({ viewport: { width: 1366, height: 768 } });
  await ctx.addInitScript(() => {
    addEventListener('load', () => requestAnimationFrame(() => setTimeout(() => { window.__benchReady = performance.now(); }, 0)));
  });
  const page = await ctx.newPage();
  const errors = [];
  page.on('pageerror', (e) => errors.push(String(e)));
  if (CPU > 1) { const cdp = await ctx.newCDPSession(page); await cdp.send('Emulation.setCPUThrottlingRate', { rate: CPU }); }

  await page.goto(URL_);
  const stored = await page.evaluate(({ d, auth }) => {
    localStorage.clear();
    localStorage.setItem('tohid-shop-auth-v1', auth);
    try { localStorage.setItem('tohid-shop-data-v1', d); return { ok: true }; } catch (e) { return { ok: false, err: e.name }; }
  }, { d: json, auth: AUTH });

  const row = { products: N, ledgerChars: json.length, inLocalStorage: stored.ok, storageError: stored.err || null, boot: null, pages: {}, scan: null, heapMB: null, errors: [] };

  // ── بالا آمدن ──
  const boots = [];
  for (let i = 0; i < REPS; i++) {
    await page.reload({ waitUntil: 'load' });
    await page.waitForFunction(() => window.__benchReady, null, { timeout: 120000 });
    boots.push(await page.evaluate(() => window.__benchReady));
  }
  row.boot = stat(boots);
  row.loaded = await page.evaluate(() => products.length);
  if (!stored.ok) {
    // همان مسیرِ همگام‌سازی: دفتر فقط در حافظهٔ صفحه
    await page.evaluate((d) => { try { applyServerLedger(JSON.parse(d)); } catch (e) { window.__benchApplyError = String(e); } }, json);
    row.inMemory = true;
    row.loaded = await page.evaluate(() => products.length);
  }

  // ── باز شدنِ صفحه‌ها ──
  const per = Object.fromEntries(PAGES.map((p) => [p, []]));
  for (let i = 0; i < REPS; i++) {
    for (const p of PAGES) {
      per[p].push(await page.evaluate(async (p) => {
        const t0 = performance.now();
        goTo(p);
        await new Promise((r) => requestAnimationFrame(() => setTimeout(r, 0)));
        return performance.now() - t0;
      }, p));
    }
  }
  for (const p of PAGES) row.pages[p] = stat(per[p]);

  // ── اسکن تا ردیف ──
  await page.evaluate(() => { cart = []; renderCart(); goTo('sale'); });
  await page.evaluate(() => {
    window.__scan = [];
    addEventListener('keydown', (e) => {
      if (e.key !== 'Enter') return;
      const t0 = performance.now();
      requestAnimationFrame(() => setTimeout(() => {
        const list = document.getElementById('cart-list');
        window.__scan.push({ ms: performance.now() - t0, rows: list ? list.children.length : 0 });
      }, 0));
    }, true);
  });
  const focused = await page.evaluate(() => { const a = document.activeElement; const tag = a && a.tagName; if (a && a.blur) a.blur(); return tag; });
  for (let i = 0; i < SCANS; i++) {
    const idx = Math.floor((i + 0.5) * N / SCANS);
    await page.keyboard.type(ean13(idx));
    await page.keyboard.press('Enter');
    await page.waitForFunction((k) => window.__scan.length > k, i, { timeout: 30000 });
  }
  const scans = await page.evaluate(() => window.__scan);
  row.scan = { ...stat(scans.map((s) => s.ms)), rowsAtEnd: scans.at(-1)?.rows ?? 0, focusedBefore: focused };
  row.heapMB = await page.evaluate(() => performance.memory ? Math.round(performance.memory.usedJSHeapSize / 1048576) : null);
  row.errors = errors.slice(0, 5);
  row.applyError = await page.evaluate(() => window.__benchApplyError || null);
  result.sizes.push(row);
  await ctx.close();

  console.log(`\n── ${N} کالا — دفتر ${(json.length / 1e6).toFixed(2)}M نویسه، localStorage: ${stored.ok ? 'جا شد' : 'جا نشد (' + stored.err + ') ⇒ فقط در حافظه'}، بارشده: ${row.loaded}`);
  console.log(`  بالا آمدن            p50 ${row.boot.p50}ms  p95 ${row.boot.p95}ms${stored.ok ? '' : '  (با دفترِ خالی)'}`);
  for (const p of PAGES) console.log(`  صفحهٔ ${p.padEnd(12)}   p50 ${String(row.pages[p].p50).padStart(7)}ms  p95 ${String(row.pages[p].p95).padStart(7)}ms`);
  console.log(`  اسکن تا ردیف         p50 ${row.scan.p50}ms  p95 ${row.scan.p95}ms  (${row.scan.n} اسکن، ${row.scan.rowsAtEnd} ردیف در سبد)`);
  console.log(`  حافظهٔ JS            ${row.heapMB} MB   خطای صفحه: ${errors.length}`);
}

await browser.close();
server.close();
if (JSON_OUT) fs.writeFileSync(JSON_OUT, JSON.stringify(result, null, 2));

// ── مقایسه با خط پایه: فقط افتِ نسبی (بخشِ ۹.۲ — CI) ──
if (COMPARE) {
  const base = JSON.parse(fs.readFileSync(COMPARE, 'utf8'));
  const worse = [];
  for (const row of result.sizes) {
    const b = base.sizes.find((x) => x.products === row.products);
    if (!b) continue;
    const pairs = [['boot', row.boot, b.boot], ['scan', row.scan, b.scan], ...Object.keys(row.pages).map((p) => [`page:${p}`, row.pages[p], b.pages[p]])];
    for (const [k, now, was] of pairs) {
      if (!now || !was || !was.p50) continue;
      const pct = ((now.p50 - was.p50) / was.p50) * 100;
      if (pct > MAX_REGRESS) worse.push(`${row.products}/${k}: ${was.p50} → ${now.p50}ms (+${pct.toFixed(0)}%)`);
    }
  }
  console.log(worse.length ? `\n❌ افتِ بیش از ${MAX_REGRESS}٪:\n  ${worse.join('\n  ')}` : `\n✅ هیچ افتی بیش از ${MAX_REGRESS}٪ نیست`);
  process.exit(worse.length ? 1 : 0);
}
