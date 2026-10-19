/**
 * نسخهٔ وب — «درخواستِ تأیید» و PINِ هر کاربر، با مرورگرِ واقعی (STF-02/03/04، S07، S08).
 *
 *   ۱. بی لایه همان امروز: مدیر تخفیف می‌دهد و چیزی نمی‌پرسد؛ شاگرد کادرِ تخفیف ندارد
 *   ۲. PINِ تازه با صفحه‌کلید (دو بار) ⇒ کلیدِ عمومی در سرور ثبت می‌شود؛ هشِ PIN هست، خودِ PIN نه
 *   ۳. S08: مدیر با سقفِ ۱۰٪، تخفیفِ ۲۰٪ ⇒ «نیازمند تأیید»، ثبت بسته؛ PINِ غلط ⇒ «۴ تلاش مانده»؛
 *      PINِ درست ⇒ «تأیید شد» و فروش با برگهٔ امضاشده ثبت می‌شود
 *   ۴. امضای WebCrypto را همان کدِ سرور (`verifySignature`، `approval-core`) می‌پذیرد
 *   ۵. S07: Esc / انصراف ⇒ هیچ چیزی ثبت نمی‌شود؛ ۵ خطا ⇒ قفل
 *   ۶. axe روی پنجرهٔ تأیید
 *
 * اجرا:  node test/web-staff-approval.mjs
 * نبودِ پلی‌رایت قرمز است، نه «رد» (F0-17)؛ ردِ آگاهانه فقط با SKIP_BROWSER_TESTS=1.
 */
import { createRequire } from 'node:module';
import { missingTool, PLAYWRIGHT_INSTALL } from './browser-guard.mjs';
import assert from 'node:assert/strict';
import { execSync } from 'node:child_process';
import crypto from 'node:crypto';
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';

const require = createRequire(import.meta.url);
const globalRoot = (() => { try { return execSync('npm root -g', { encoding: 'utf8' }).trim(); } catch { return ''; } })();
const pick = (name) => { for (const p of [`${globalRoot}/${name}`, name]) { try { return require(p); } catch { /* بعدی */ } } return null; };
const pw = pick('playwright');
if (!pw) missingTool('پلی‌رایت', PLAYWRIGHT_INSTALL);
const axePath = (() => { for (const p of [`${globalRoot}/axe-core/axe.min.js`]) if (fs.existsSync(p)) return p; return null; })();

const approvals = require('../src/lib/staff-approval.js');
const slipCore = require('../src/lib/approval-core.js');

const ROOT = process.env.WEB_ROOT ? path.resolve(process.env.WEB_ROOT) : path.join(import.meta.dirname, '..', '..');
const TYPES = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.json': 'application/json', '.png': 'image/png', '.svg': 'image/svg+xml', '.webmanifest': 'application/manifest+json' };
const server = http.createServer((req, res) => {
  const file = path.join(ROOT, decodeURIComponent(req.url.split('?')[0]).replace(/^\/+/, '') || 'index.html');
  if (!file.startsWith(ROOT) || !fs.existsSync(file) || fs.statSync(file).isDirectory()) { res.statusCode = 404; return res.end(); }
  res.setHeader('Content-Type', TYPES[path.extname(file)] || 'application/octet-stream');
  fs.createReadStream(file).pipe(res);
});
await new Promise(r => server.listen(0, '127.0.0.1', r));
const origin = `http://127.0.0.1:${server.address().port}`;
const url = `${origin}/index.html`;

const DATA_KEY = 'tohid-shop-data-v1';
const STATE_KEY = 'tohid-sync-state-v1';
const ACCT_KEY = 'tohid-license-v1';
const PINS_KEY = 'tohid-staff-pins-v1';
const SHOP = 'shp_web_ap';
const LEDGER = {
  products: [{ id: 'p1', name: 'برنج', unit: 'عدد', salePrice: 1000, purchasePrice: 700, barcodes: ['111'] }],
  warehouseEntries: [{ id: 'w1', productId: 'p1', units: 50, date: '2026-10-01' }],
  sales: [], saleItems: [], nextInvoiceNo: 1,
};
const CAP10 = { v: 1, role: 'manager', layers: [{ 'sales.discount': { caps: { maxPct: 10 } } }, {}] };

const browser = await pw.chromium.launch();
let failed = 0;
const step = async (name, fn) => {
  try { await fn(); console.log(`✅  ${name}`); } catch (e) { failed++; console.log(`❌  ${name}\n    ${e.stack || e.message}`); }
};

/** کلیدهایی که «سرور» (همین آزمون) ثبت کرد: شناسه ⇐ SPKI */
const registered = new Map();

async function profile({ role = 'manager', userId = 'usr_mgr', policy = CAP10, pins = null } = {}) {
  const ctx = await browser.newContext({ viewport: { width: 390, height: 820 } });
  const seed = {
    [DATA_KEY]: LEDGER,
    [STATE_KEY]: { rev: 0, lastSyncAt: Date.now(), members: [], invoiceBlock: 1000,
      shop: { id: SHOP, name: 'دکانِ آزمون', role, isOwner: role === 'owner', policy } },
    [ACCT_KEY]: { userId, userLabel: role === 'manager' ? 'مدیرِ آزمون' : 'کاربر', accessToken: 'tok-' + userId },
    'tohid-license-server-url': origin,
    ...(pins ? { [PINS_KEY]: pins } : {}),
  };
  await ctx.addInitScript(([seedJson]) => {
    if (localStorage.getItem('seeded-profile')) return;
    const s = JSON.parse(seedJson);
    for (const [k, v] of Object.entries(s)) localStorage.setItem(k, typeof v === 'string' ? v : JSON.stringify(v));
    localStorage.setItem('tohid-shop-auth-v1', JSON.stringify({ method: 'local', value: '', name: 'آزمون', loginAt: 1 }));
    localStorage.setItem('seeded-profile', '1');
  }, [JSON.stringify(seed)]);
  //  «سرورِ» ثبتِ کلید؛ بقیهٔ API بسته (هیچ درخواستی به بیرون نمی‌رود)
  //  نشانیِ سرور در خودِ index.html نشسته است (`tohid-api-base`)؛ هر مبدایی که باشد همین‌جا جواب می‌گیرد
  const CORS = { 'Access-Control-Allow-Origin': '*', 'Access-Control-Allow-Headers': '*', 'Access-Control-Allow-Methods': '*' };
  await ctx.route('**/*', async (route) => {
    const req = route.request();
    const u = new URL(req.url());
    if (u.pathname.startsWith('/api/')) {
      if (req.method() === 'OPTIONS') return route.fulfill({ status: 204, headers: CORS });
      if (u.pathname === '/api/v1/shop/approver-keys' && req.method() === 'POST') {
        const body = JSON.parse(req.postData() || '{}');
        const id = approvals.keyIdOf(Buffer.from(body.public_key, 'base64'));
        registered.set(id, body.public_key);
        return route.fulfill({ status: 201, headers: CORS, contentType: 'application/json', body: JSON.stringify({ key: { id, alg: 'ES256' } }) });
      }
      return route.fulfill({ status: 503, headers: CORS, contentType: 'application/json', body: '{"ok":false}' });
    }
    if (u.origin !== origin) return route.abort();
    return route.continue();
  });
  return ctx;
}
async function openIn(ctx) {
  const pg = await ctx.newPage();
  const errors = [];
  pg.on('pageerror', e => errors.push(String(e)));
  await pg.goto(url);
  await pg.waitForFunction(() => typeof window.goTo === 'function' && document.readyState === 'complete'
    && window.TohidStaffApproval && window.TohidPerm && window.TohidApproval);
  await pg.waitForTimeout(400);
  const skip = pg.getByText('ادامه بدون حساب');
  if (await skip.first().isVisible().catch(() => false)) { await skip.first().click(); await pg.waitForTimeout(200); }
  return { pg, errors };
}
const ledger = (pg) => pg.evaluate((k) => JSON.parse(localStorage.getItem(k) || '{}'), DATA_KEY);
const typePin = async (pg, pin) => { for (const d of pin) await pg.keyboard.press(d); };
const scrim = (pg) => pg.locator('.sa-scrim');
const pinHint = (pg) => pg.locator('.sa-scrim .sa-pad-hint[role="status"]').innerText();

/** ردیفِ آمادهٔ صاحب دکان روی همین مرورگر (کلیدِ ثبت‌شده) — ۱۰۰۰ دور تا آزمون تند باشد */
async function ownerRecord(pg, pin = '482917') {
  return pg.evaluate(async ([p]) => {
    const SA = window.TohidStaffApproval;
    const r = await SA.create('usr_owner', 'صاحبِ دکان', 'owner', p, 1000);
    return SA.put(Object.assign({}, r, { registered: true }));
  }, [pin]);
}

async function checkoutWithDiscount(pg, pct) {
  await pg.evaluate(() => { goTo('sale'); addToCart('p1', 1); openSaleCheckoutModal(); });
  await pg.fill('#co-discount-value', String(pct));
  await pg.dispatchEvent('#co-discount-value', 'input');
}

await step('آزمونِ مرجع: بی لایه، مدیر تخفیفِ ۲۰٪ می‌دهد و چیزی نمی‌پرسد؛ شاگرد کادرِ تخفیف ندارد', async () => {
  let ctx = await profile({ policy: null });
  let { pg, errors } = await openIn(ctx);
  await checkoutWithDiscount(pg, 20);
  assert.equal(await pg.locator('#co-approval').isHidden(), true);
  await pg.click('#form-sale-checkout [type="submit"]');
  await pg.waitForTimeout(300);
  const s = (await ledger(pg)).sales;
  assert.equal(s.length, 1);
  assert.equal(s[0].discount, 200);
  assert.equal(s[0].approval, undefined);
  assert.deepEqual(errors, []);
  await ctx.close();

  ctx = await profile({ role: 'staff', userId: 'usr_staff', policy: null });
  ({ pg, errors } = await openIn(ctx));
  await pg.evaluate(() => { goTo('sale'); addToCart('p1', 1); openSaleCheckoutModal(); });
  assert.equal(await pg.locator('#co-discount-value').isVisible(), false, 'شاگرد کادرِ تخفیف نمی‌بیند');
  assert.deepEqual(errors, []);
  await ctx.close();
});

await step('PINِ تازه با صفحه‌کلید (دو بار) ⇒ کلید در سرور ثبت می‌شود؛ PINِ ساده و ناجور رد', async () => {
  const ctx = await profile({ role: 'owner', userId: 'usr_owner' });
  const { pg, errors } = await openIn(ctx);
  const done = pg.evaluate(() => window.TohidStaffApproval.setup({ userId: 'usr_owner', name: 'صاحبِ دکان', role: 'owner', iter: 1000, label: 'مرورگر' }));
  await scrim(pg).waitFor();
  await typePin(pg, '123456');
  assert.match(await pinHint(pg), /ساده است/);
  await typePin(pg, '482917');
  await typePin(pg, '482918');
  assert.match(await pinHint(pg), /یکی نبودند/);
  await typePin(pg, '482917');
  await typePin(pg, '482917');
  const rec = await done;
  assert.equal(rec.registered, true, 'شناسهٔ سرور با کلید خواند');
  assert.ok(registered.has(rec.keyId));
  assert.equal(registered.get(rec.keyId), rec.spki);
  const raw = await pg.evaluate((k) => localStorage.getItem(k), PINS_KEY);
  assert.ok(!raw.includes('482917'), 'خودِ PIN هیچ‌جا نیست');
  assert.match(rec.check, /^[0-9a-f]{64}$/);
  assert.deepEqual(errors, []);
  await ctx.close();
});

let signedSale = null;
let ownerSpki = '';

await step('S08: مدیرِ با سقفِ ۱۰٪، تخفیفِ ۲۰٪ ⇒ «نیازمند تأیید»؛ PINِ غلط شمرده می‌شود؛ PINِ صاحب دکان ⇒ فروش با برگه', async () => {
  const ctx = await profile();
  const { pg, errors } = await openIn(ctx);
  ownerSpki = (await ownerRecord(pg)).spki;
  await checkoutWithDiscount(pg, 5);
  assert.equal(await pg.locator('#co-approval').isHidden(), true, '۵٪ زیرِ سقف است');
  await pg.fill('#co-discount-value', '20');
  await pg.dispatchEvent('#co-discount-value', 'input');
  assert.match(await pg.locator('#co-approval').innerText(), /نیازمند تأیید/);
  assert.match(await pg.locator('#co-approval').innerText(), /سقفِ (10|۱۰)٪/);
  //  ثبت بی برگه نمی‌شود — پنجرهٔ تأیید باز می‌شود
  await pg.click('#form-sale-checkout [type="submit"]');
  await scrim(pg).waitFor();
  assert.equal((await ledger(pg)).sales.length, 0);
  await pg.fill('[data-sa-reason]', 'مشتریِ عمده');
  await pg.locator('.sa-keys button').first().focus();
  await typePin(pg, '111112');
  await pg.waitForFunction(() => /تلاش مانده/.test(document.querySelector('.sa-scrim .sa-pad-hint[role="status"]').textContent));
  assert.match(await pinHint(pg), /۴ تلاش مانده/);
  await typePin(pg, '482917');
  await scrim(pg).waitFor({ state: 'detached' });
  assert.match(await pg.locator('#co-approval').innerText(), /تأیید شد/);
  await pg.click('#form-sale-checkout [type="submit"]');
  await pg.waitForTimeout(300);
  const sales = (await ledger(pg)).sales;
  assert.equal(sales.length, 1);
  signedSale = sales[0];
  assert.equal(signedSale.discount, 200);
  assert.equal(signedSale.approval.perm, 'sales.discount');
  assert.equal(signedSale.approval.doc, `sales/${signedSale.id}`);
  assert.equal(signedSale.approval.by, 'usr_owner');
  assert.equal(signedSale.approval.reason, 'مشتریِ عمده');
  assert.equal(signedSale.approval.shop, SHOP);
  const failures = await pg.evaluate((k) => JSON.parse(localStorage.getItem(k)).usr_owner.failures, PINS_KEY);
  assert.equal(failures, 0, 'PINِ درست شمارِ خطا را صفر می‌کند');
  assert.deepEqual(errors, []);
  await ctx.close();
});

await step('امضای WebCrypto را همان کدِ سرور می‌پذیرد؛ برگه همان فروش را می‌پوشاند و دست‌خورده‌اش نه', () => {
  const s = signedSale.approval;
  assert.equal(slipCore.check(s), null);
  assert.equal(Buffer.from(s.sig, 'base64').length, 64, 'WebCrypto امضای خامِ ۶۴ بایتی');
  assert.equal(approvals.verifySignature(ownerSpki, slipCore.payload(s), s.sig), true);
  assert.equal(approvals.verifySignature(ownerSpki, slipCore.payload({ ...s, amount: s.amount + 1 }), s.sig), false);
  //  همان «نیاز»ی که سرور از opِ v1ِ همین فروش می‌سازد
  const need = approvals.needOfV1({ table: 'sales', row_id: signedSale.id, type: 'insert', fields: signedSale });
  assert.equal(need.perm, 'sales.discount');
  assert.equal(slipCore.covers(s, { ...need, at: signedSale.createdAt }), null);
});

await step('S07: Esc و «انصراف» ⇒ هیچ چیزی ثبت نمی‌شود؛ ۵ خطا ⇒ قفل', async () => {
  const ctx = await profile();
  const { pg, errors } = await openIn(ctx);
  await ownerRecord(pg);
  await checkoutWithDiscount(pg, 30);
  await pg.click('#co-approval-ask');
  await scrim(pg).waitFor();
  await pg.keyboard.press('Escape');
  await scrim(pg).waitFor({ state: 'detached' });
  assert.match(await pg.locator('#co-approval').innerText(), /نیازمند تأیید/);
  await pg.click('#co-approval-ask');
  await scrim(pg).waitFor();
  await pg.click('[data-sa-cancel]');
  await scrim(pg).waitFor({ state: 'detached' });
  assert.equal((await ledger(pg)).sales.length, 0);

  await pg.click('#co-approval-ask');
  await scrim(pg).waitFor();
  await pg.fill('[data-sa-reason]', 'آزمون');
  await pg.locator('.sa-keys button').first().focus();
  for (let i = 0; i < 5; i++) {
    await typePin(pg, '000000');
    await pg.waitForFunction((n) => {
      const r = JSON.parse(localStorage.getItem('tohid-staff-pins-v1')).usr_owner;
      return r.failures === n || r.locked;
    }, i + 1);
  }
  assert.match(await pinHint(pg), /قفل شد/);
  assert.equal(await pg.locator('.sa-keys button').first().isDisabled(), true);
  await typePin(pg, '482917');
  await pg.waitForTimeout(200);
  assert.match(await pinHint(pg), /قفل شد/, 'قفل با PINِ درست هم باز نمی‌شود');
  await pg.click('[data-sa-cancel]');
  assert.equal((await ledger(pg)).sales.length, 0);
  assert.deepEqual(errors, []);
  await ctx.close();
});

await step('بی تأییدکنندهٔ آماده روی این مرورگر ⇒ پیامِ روشن، نه دکمهٔ بی‌اثر', async () => {
  const ctx = await profile();
  const { pg, errors } = await openIn(ctx);
  await checkoutWithDiscount(pg, 30);
  await pg.click('#co-approval-ask');
  await scrim(pg).waitFor();
  assert.match(await pg.locator('.sa-scrim').innerText(), /PIN و کلیدِ تأییدِ ثبت‌شده ندارد/);
  assert.equal(await pg.locator('.sa-scrim .sa-keys').count(), 0);
  await pg.keyboard.press('Escape');
  assert.deepEqual(errors, []);
  await ctx.close();
});

await step('axe: پنجرهٔ تأیید بی خطای جدیِ دسترس‌پذیری', async () => {
  if (!axePath) throw new Error('axe-core پیدا نشد (npm i -g axe-core)');
  const ctx = await profile();
  const { pg } = await openIn(ctx);
  await ownerRecord(pg);
  await checkoutWithDiscount(pg, 30);
  await pg.click('#co-approval-ask');
  await scrim(pg).waitFor();
  await pg.addScriptTag({ path: axePath });
  const res = await pg.evaluate(async () => window.axe.run(document.querySelector('.sa-scrim'), {
    resultTypes: ['violations'], runOnly: { type: 'tag', values: ['wcag2a', 'wcag2aa'] },
  }));
  const serious = res.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical');
  assert.deepEqual(serious.map((v) => `${v.id}: ${v.nodes.map((n) => n.target.join(' ')).join(', ')}`), []);
  await ctx.close();
});

await browser.close();
server.close();
if (failed) { console.log(`\n${failed} مورد شکست خورد`); process.exit(1); }
console.log('\nهمه سبز');
