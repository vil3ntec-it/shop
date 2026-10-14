/**
 * نسخهٔ آیفون (وب) — چیدمان و دسترس‌پذیری با مرورگرِ واقعی (بندِ ۳۱ِ ممیزی).
 *
 *   • هیچ صفحه‌ای در پهنای ۳۲۰، ۳۹۰، ۷۶۸ و ۱۳۶۶ افقی بیرون نمی‌زند
 *   • رنگِ متن‌ها ≥ ۴٫۵:۱ (WCAG AA)، هر دکمه نام دارد، هر فیلد برچسب دارد (axe-core)
 *   • هیچ خطای صفحه‌ای
 *
 * اجرا:  node test/web-ui.mjs
 * نبودِ پلی‌رایت یا axe-core قرمز است، نه «رد» (F0-17)؛ ردِ آگاهانه فقط با SKIP_BROWSER_TESTS=1.
 */
import { createRequire } from 'node:module';
import { missingTool, PLAYWRIGHT_INSTALL, SKIP } from './browser-guard.mjs';
import assert from 'node:assert/strict';
import { execSync } from 'node:child_process';
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';

const require = createRequire(import.meta.url);
const globalRoot = (() => { try { return execSync('npm root -g', { encoding: 'utf8' }).trim(); } catch { return ''; } })();
const pick = (name) => { for (const p of [`${globalRoot}/${name}`, name]) { try { return require(p); } catch { /* بعدی */ } } return null; };
const pw = pick('playwright');
if (!pw) missingTool('پلی‌رایت', PLAYWRIGHT_INSTALL);
let axeSrc = null;
for (const p of [`${globalRoot}/axe-core/axe.min.js`, path.join(import.meta.dirname, '..', 'node_modules', 'axe-core', 'axe.min.js'), process.env.AXE_PATH || '']) {
  if (p && fs.existsSync(p)) { axeSrc = fs.readFileSync(p, 'utf8'); break; }
}

const ROOT = path.join(import.meta.dirname, '..', '..');
const TYPES = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.json': 'application/json', '.png': 'image/png', '.svg': 'image/svg+xml', '.webmanifest': 'application/manifest+json' };
const server = http.createServer((req, res) => {
  const file = path.join(ROOT, decodeURIComponent(req.url.split('?')[0]).replace(/^\/+/, '') || 'index.html');
  if (!file.startsWith(ROOT) || !fs.existsSync(file) || fs.statSync(file).isDirectory()) { res.statusCode = 404; return res.end(); }
  res.setHeader('Content-Type', TYPES[path.extname(file)] || 'application/octet-stream');
  fs.createReadStream(file).pipe(res);
});
await new Promise(r => server.listen(0, '127.0.0.1', r));
const url = `http://127.0.0.1:${server.address().port}/index.html`;

const LEDGER = {
  products: [{ id: 'p1', name: 'برنج درجه یک ایرانی بسته‌بندی ده کیلویی', unit: 'کیلوگرم', salePrice: 1250000, purchasePrice: 990000, category: 'خوراکی', minStock: 20 }],
  warehouseEntries: [{ id: 'w', productId: 'p1', units: 10, date: '2026-10-01', expiryDate: '2026-10-05' }],
  debtors: [{ id: 'c1', name: 'احمد کریمی' }],
  transactions: [{ id: 't', debtorId: 'c1', type: 'give', amount: 5000000, date: '2026-10-01', createdAt: 1 }],
  sales: [{ id: 's1', invoiceNumber: 1, finalTotal: 1250000, total: 1250000, date: '2026-10-01', createdAt: 1, status: 'completed', paymentMethod: 'cash' }],
  saleItems: [],
};
const PAGES = ['dashboard', 'admin', 'devices', 'products', 'warehouse', 'sale', 'sales', 'debtors', 'expenses', 'reports', 'settings', 'more', 'purchasing'];

const browser = await pw.chromium.launch();
let failed = 0;
const step = async (name, fn) => {
  try { await fn(); console.log(`✅  ${name}`); } catch (e) { failed++; console.log(`❌  ${name}\n    ${e.message}`); }
};

async function open(width) {
  const pg = await browser.newPage({ viewport: { width, height: 800 } });
  const errors = [];
  pg.on('pageerror', e => errors.push(String(e)));
  await pg.goto(url);
  await pg.evaluate(d => localStorage.setItem('tohid-shop-data-v1', JSON.stringify(d)), LEDGER);
  await pg.reload();
  await pg.waitForTimeout(1200);
  const skip = pg.getByText('ادامه بدون حساب');
  if (await skip.first().isVisible().catch(() => false)) { await skip.first().click(); await pg.waitForTimeout(300); }
  return { pg, errors };
}

// F0-23: دفترِ وب در localStorage است (سقفِ Chromium ۵٬۲۴۲٬۸۸۰ نویسه برای کلِ سایت).
// زیرِ ۷۰٪ هیچ پنجره‌ای نباید بیاید؛ از ۷۰٪ پیشنهادِ خروجی با درصدِ واقعی باید
// خودش باز شود و دکمه‌اش همان فایلِ پشتیبان را بدهد.
await step('حافظهٔ مرورگر: ≥۷۰٪ ⇒ هشدار با درصد و پیشنهادِ اجباریِ خروجی؛ کمتر ⇒ هیچ', async () => {
  const small = await open(1366);
  await small.pg.waitForTimeout(3000);
  assert.equal(await small.pg.locator('#modal-storage.open').count(), 0, 'با دفترِ کوچک نباید هشداری بیاید');
  await small.pg.close();

  const pg = await browser.newPage({ viewport: { width: 1366, height: 800 }, acceptDownloads: true });
  const errors = [];
  pg.on('pageerror', (e) => errors.push(String(e)));
  await pg.goto(url);
  const big = JSON.parse(JSON.stringify(LEDGER));
  big.products[0].notes = 'ی'.repeat(Math.floor(5242880 * 0.75));
  await pg.evaluate((d) => {
    localStorage.setItem('tohid-shop-auth-v1', JSON.stringify({ method: 'local', value: '', name: 'آزمون', loginAt: 1 }));
    localStorage.setItem('tohid-shop-data-v1', d);
  }, JSON.stringify(big));
  await pg.reload();
  await pg.waitForSelector('#modal-storage.open', { timeout: 10000 });
  const text = await pg.locator('#modal-storage').innerText();
  assert.match(text, /7[5-6]٪/, `درصدِ واقعی در پنجره نیست: ${text}`);
  const [dl] = await Promise.all([pg.waitForEvent('download'), pg.click('#btn-storage-export')]);
  assert.match(dl.suggestedFilename(), /^tohid-shop-backup-.*\.json$/);
  await pg.waitForFunction(() => !document.querySelector('#modal-storage.open'));
  assert.deepEqual(errors, []);
  await pg.close();
});

for (const width of [320, 390, 768, 1366]) {
  await step(`پهنای ${width}: هیچ صفحه‌ای افقی بیرون نمی‌زند و خطایی نیست`, async () => {
    const { pg, errors } = await open(width);
    const bad = [];
    for (const p of PAGES) {
      await pg.evaluate(x => { try { goTo(x); } catch { /* صفحه‌ای که نیست */ } }, p);
      await pg.waitForTimeout(120);
      const over = await pg.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
      if (over > 1) bad.push(`${p} (+${over}px)`);
    }
    await pg.close();
    assert.deepEqual(bad, [], `بیرون‌زده: ${bad.join('، ')}`);
    assert.deepEqual(errors, [], `خطای صفحه: ${errors.join(' | ')}`);
  });
}

if (!axeSrc && SKIP) {
  console.log('⏭  axe-core پیدا نشد و SKIP_BROWSER_TESTS=1 است — سنجهٔ دسترس‌پذیری آگاهانه رد شد');
} else if (!axeSrc) {
  failed++;
  console.log('❌  axe-core پیدا نشد — سنجهٔ دسترس‌پذیری اجرا نشد و «قبول» هم نیست (npm i -g axe-core@4.10.3، یا AXE_PATH)');
} else {
  await step('دسترس‌پذیری: کنتراست، نامِ دکمه، برچسبِ فیلد، متنِ جایگزینِ تصویر', async () => {
    const { pg } = await open(390);
    await pg.addScriptTag({ content: axeSrc });
    const bad = [];
    for (const p of PAGES) {
      await pg.evaluate(x => { try { goTo(x); } catch { /* */ } }, p);
      await pg.waitForTimeout(900);   // پس از پایانِ پویانماییِ ورود — وگرنه رنگِ نیمه‌شفاف سنجیده می‌شود
      const v = await pg.evaluate(async () => {
        const out = await window.axe.run(document.querySelector('.page.active') || document,
          { runOnly: ['color-contrast', 'button-name', 'label', 'image-alt', 'link-name'] });
        return out.violations.map(x => `${x.id}: ${x.nodes.slice(0, 2).map(n => n.target.join(' ')).join(' ; ')}`);
      });
      v.forEach(x => bad.push(`${p} — ${x}`));
    }
    await pg.close();
    assert.deepEqual(bad, [], bad.join('\n    '));
  });
}

// F0-22: CSP ِ واقعیِ مرورگر — سرورِ همین نسخه باز، هر میزبانِ دیگری بسته.
// fetch ها با route جواب می‌گیرند، پس بی CSP همه «ok» می‌شدند و «blocked» فقط
// کارِ CSP است. WebSocket را route نمی‌کنیم (پلی‌رایت خودش جانشینش می‌کند و CSP
// دیگر سنجیده نمی‌شود)؛ برای آن گزارشِ securitypolicyviolation ِ خودِ مرورگر
// ملاک است، و فقط به میزبانِ بیگانه وصل می‌شود — نه به سرورِ واقعی.
await step('CSP: اتصال فقط به سرورِ همین نسخه — میزبانِ دیگر بسته است', async () => {
  const { pg } = await open(390);
  const api = await pg.evaluate(() => window.TohidApiConfig.baseUrl());
  await pg.route(`${api}/**`, (r) => r.fulfill({ status: 200, contentType: 'application/json', body: '{}' }));
  await pg.route('https://evil.example/**', (r) => r.fulfill({ status: 200, body: 'leak' }));
  await pg.route(/^http:\/\/192\.168\.0\.102:4700\//, (r) => r.fulfill({ status: 200, body: 'leak' }));
  const out = await pg.evaluate(async (api) => {
    const blocked = [];
    document.addEventListener('securitypolicyviolation', (e) => blocked.push(e.blockedURI));
    const tryFetch = (u) => fetch(u).then(() => 'ok', () => 'blocked');
    const r = {
      api: await tryFetch(api + '/api/v1/plans'),
      evil: await tryFetch('https://evil.example/x'),
      lan: await tryFetch('http://192.168.0.102:4700/x'),
    };
    try { new WebSocket('ws://192.168.0.102:4700').onerror = () => {}; } catch { /* بسته */ }
    await new Promise((x) => setTimeout(x, 300));
    return { r, blocked };
  }, api);
  await pg.close();
  assert.deepEqual(out.r, { api: 'ok', evil: 'blocked', lan: 'blocked' });
  const saw = (p) => out.blocked.some((u) => u.startsWith(p));
  assert.ok(saw('https://evil.example') && saw('http://192.168.0.102:4700'), `CSP گزارشِ fetch ِ بیگانه را نداد: ${out.blocked.join(' ')}`);
  assert.ok(saw('ws://192.168.0.102:4700'), `CSP جلوِ WebSocket ِ بیگانه را نگرفت: ${out.blocked.join(' ')}`);
  assert.ok(!saw(api), 'CSP سرورِ خودِ برنامه را بست');
});

await browser.close();
server.close();
console.log(failed ? `\n${failed} سنجه قرمز` : '\nهمه سبز');
process.exit(failed ? 1 : 0);
