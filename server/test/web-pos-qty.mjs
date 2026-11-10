/**
 * نسخهٔ وب — پیشوندِ شمار «۳*» پیش از بارکد در صندوق (POS-03) و اسکنِ دوبارهٔ همان کالا (POS-02)، با کرومیومِ واقعی.
 *
 *   • پیش‌فرض خاموش: «۳*کد» همان بارکدِ ناشناسِ دیروز است (پنجرهٔ «محصول جدید»، چیزی به سبد نمی‌رود)؛ کلید در «تنظیمات» ⇐
 *     «دوربین بارکدخوان» تیک‌نخورده، و پس از روشن کردن با بارگذاریِ دوباره هم روشن می‌ماند. axe (اگر هست) بر همان پنل.
 *   • روشن: «۳*کد»، «۲×کد» و رقمِ فارسی ⇒ همان یک ردیف با شمارِ درست (اسکنِ دوباره فقط عدد را بالا می‌برد)؛ «×۵» ِ «هر اسکن»
 *     پس از پیشوند برمی‌گردد؛ کالای کیلویی با اعشار؛ اعشار برای کالای شمردنی و شمارِ صفر ⇒ پیام و هیچ؛ «۳*» ِ تنها ⇒ اسکنِ بعدیِ
 *     بارکدخوان (بیرون از کادر) سه تا و کادر خالی؛ کالای ناشناس ⇒ «محصول جدید» با خودِ کد؛ بارکدِ «12x34» همان کالا؛ کالای
 *     غیرفعال رد؛ انگلیسی و پشتو متنِ خودشان؛ خاموش کردنِ دوباره ⇒ همان رفتارِ دیروز. هیچ خطای صفحه.
 *
 * اجرا:  node test/web-pos-qty.mjs        (WEB_ROOT=<پوشه> برای سنجیدنِ نسخهٔ دیگری از صفحه، مثلاً main پیش از این تغییر)
 * نبودِ پلی‌رایت قرمز است، نه «رد» (F0-17)؛ ردِ آگاهانه فقط با SKIP_BROWSER_TESTS=1.
 */
import { createRequire } from 'node:module';
import { missingTool, PLAYWRIGHT_INSTALL } from './browser-guard.mjs';
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

const ROOT = process.env.WEB_ROOT ? path.resolve(process.env.WEB_ROOT) : path.join(import.meta.dirname, '..', '..');
const TYPES = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.json': 'application/json', '.png': 'image/png', '.svg': 'image/svg+xml', '.webmanifest': 'application/manifest+json' };
const server = http.createServer((req, res) => {
  const file = path.join(ROOT, decodeURIComponent(req.url.split('?')[0]).replace(/^\/+/, '') || 'index.html');
  if (!file.startsWith(ROOT) || !fs.existsSync(file) || fs.statSync(file).isDirectory()) { res.statusCode = 404; return res.end(); }
  res.setHeader('Content-Type', TYPES[path.extname(file)] || 'application/octet-stream');
  fs.createReadStream(file).pipe(res);
});
await new Promise(r => server.listen(0, '127.0.0.1', r));
const url = `http://127.0.0.1:${server.address().port}/index.html`;

const DATA_KEY = 'tohid-shop-data-v1';
const PREF = 'tohid-pos-qty-prefix-v1';
const LEDGER = {
  products: [
    { id: 'p1', name: 'برنج', unit: 'عدد', salePrice: 300, purchasePrice: 200, barcodes: ['62611'] },
    { id: 'p2', name: 'روغنِ کهنه', unit: 'عدد', salePrice: 150, purchasePrice: 100, barcodes: ['62633'], active: false },
    { id: 'p3', name: 'شکر', unit: 'کیلوگرم', salePrice: 80, purchasePrice: 60, barcodes: ['62622'] },
    { id: 'p4', name: 'باتری', unit: 'عدد', salePrice: 40, purchasePrice: 30, barcodes: ['12x34'] },
  ],
  warehouseEntries: [
    { id: 'w1', productId: 'p1', units: 500, date: '2026-10-01' },
    { id: 'w3', productId: 'p3', units: 500, date: '2026-10-01' },
    { id: 'w4', productId: 'p4', units: 500, date: '2026-10-01' },
  ],
  sales: [], saleItems: [], nextInvoiceNo: 1,
};

const browser = await pw.chromium.launch();
let failed = 0;
const step = async (name, fn) => {
  try { await fn(); console.log(`✅  ${name}`); } catch (e) { failed++; console.log(`❌  ${name}\n    ${e.message}`); }
};

const ctx = await browser.newContext({ viewport: { width: 1280, height: 900 } });
await ctx.addInitScript(([dataKey, seedJson]) => {
  if (localStorage.getItem('seeded-profile')) return;
  localStorage.setItem(dataKey, seedJson);
  localStorage.setItem('tohid-shop-auth-v1', JSON.stringify({ method: 'local', value: '', name: 'آزمون', loginAt: 1 }));
  //  «جستجو» ِ کادرِ دستی پشتِ اشتراکِ «اسکنر بارکد» است (`vip.js`)؛ دکانِ مشترک
  localStorage.setItem('tohid-entitlement-v1', JSON.stringify({ source: 'paid', isPaid: true, features: ['sales', 'debtors', 'barcode'], trial: { used: true, active: false, daysLeft: 0 } }));
  localStorage.setItem('seeded-profile', '1');
}, [DATA_KEY, JSON.stringify(LEDGER)]);
const pg = await ctx.newPage();
const errors = [];
pg.on('pageerror', e => errors.push(String(e)));

async function open() {
  await pg.goto(url);
  await pg.waitForFunction(() => typeof window.goTo === 'function' && document.readyState === 'complete');
  await pg.waitForTimeout(600);
  const skip = pg.getByText('ادامه بدون حساب');
  if (await skip.first().isVisible().catch(() => false)) { await skip.first().click(); await pg.waitForTimeout(200); }
}
const lines = () => pg.evaluate(() => cart.map(c => [c.productId, Math.round(c.quantity * 1000) / 1000]));
const qty = async (id) => { const l = (await lines()).find(x => x[0] === id); return l ? l[1] : 0; };
const toast = () => pg.locator('#toast-msg').innerText().catch(() => '');
const box = () => pg.locator('#in-manual-barcode');
/** نوشتن در کادرِ دستی و Enter (همان راهِ صفحه: Enter ⇐ «جستجو») */
async function enter(text) {
  await pg.evaluate(() => { const t = document.getElementById('toast-msg'); if (t) t.textContent = ''; });
  await box().fill(text);
  await box().press('Enter');
  await pg.waitForTimeout(120);
}
/** بارکدخوانِ سخت‌افزاری بیرون از هر کادر: کلید پشتِ کلید در یک لحظه و Enter (همان `scan` ِ web-digits.mjs؛ بی فاصلهٔ ۲۰۰ms ِ بافر) */
async function wedge(code) {
  await pg.evaluate((c) => {
    if (document.activeElement && document.activeElement !== document.body) document.activeElement.blur();
    for (const key of c) document.dispatchEvent(new KeyboardEvent('keydown', { key, code: /[0-9]/.test(key) ? `Digit${key}` : '', bubbles: true }));
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', code: 'Enter', bubbles: true }));
  }, code);
  await pg.waitForTimeout(100);
}
const closeModals = () => pg.evaluate(() => { document.querySelectorAll('.modal-overlay.open,.modal.open,[id^="modal-"].open').forEach(m => { try { closeModal(m.id); } catch { m.classList.remove('open'); } }); });
const toSale = async () => { await closeModals(); await pg.evaluate(() => { goTo('sale'); }); await pg.waitForTimeout(150); };
const clearCart = () => pg.evaluate(() => { cart = []; saveDraftCart(); renderCart(); });

await open();

await step('خاموش (پیش‌فرض): «۳*کد» همان بارکدِ ناشناسِ دیروز است؛ کلید در تنظیمات تیک‌نخورده', async () => {
  await toSale();
  await enter('3*62611');
  assert.equal(await qty('p1'), 0, 'هیچ چیز به سبد نرفت');
  assert.deepEqual(await pg.evaluate(() => bpRows.map(r => r.barcode)), ['3*62611'], 'پنجرهٔ «محصول جدید» با همان متنِ دیروز');
  await closeModals();
  await pg.evaluate(() => goTo('settings'));
  const toggle = pg.locator('#in-pos-qty-prefix');
  await toggle.waitFor({ state: 'attached', timeout: 5000 });
  assert.equal(await toggle.isChecked(), false);
  assert.equal(await pg.evaluate((k) => localStorage.getItem(k), PREF), null);
  assert.equal(await box().evaluate((el) => el.inputMode), 'numeric', 'صفحه‌کلیدِ همان دیروز');
});

await step('کلید در «دوربین بارکدخوان» روشن می‌شود و پس از بارگذاریِ دوباره روشن می‌ماند؛ axe بر همان پنل', async () => {
  const toggle = pg.locator('#in-pos-qty-prefix');
  const panelTitle = await toggle.evaluate((el) => el.closest('.panel').querySelector('.panel-head h2').textContent);
  assert.equal(panelTitle.trim(), 'دوربین بارکدخوان');
  await toggle.check();
  assert.equal(await pg.evaluate((k) => localStorage.getItem(k), PREF), '1');
  await open();
  await pg.evaluate(() => goTo('settings'));
  await pg.locator('#in-pos-qty-prefix').waitFor({ state: 'attached', timeout: 5000 });
  assert.equal(await pg.locator('#in-pos-qty-prefix').isChecked(), true);
  assert.equal(await box().evaluate((el) => el.inputMode), 'tel', 'صفحه‌کلیدِ گوشی با «*»');
  assert.match(await pg.locator('#pos-qty-prefix-hint').innerText(), /اسکنِ بعدی را سه تا/);
  if (!axeSrc) {
    if (process.env.SKIP_BROWSER_TESTS === '1') { console.log('⏭  axe-core پیدا نشد و SKIP_BROWSER_TESTS=1 است — آگاهانه رد شد'); return; }
    throw new Error('axe-core پیدا نشد (npm i -g axe-core@4.10.3، یا AXE_PATH) — «قبول» نیست');
  }
  await pg.addScriptTag({ content: axeSrc });
  const v = await pg.evaluate(async () => (await window.axe.run(document.getElementById('in-pos-qty-prefix').closest('.panel'), { runOnly: ['wcag2a', 'wcag2aa'] })).violations.map((x) => `${x.id}: ${x.nodes.map((n) => n.target.join(' ')).join(', ')}`));
  assert.deepEqual(v, []);
});

await step('روشن: «۳*کد» و «۲×کد» با رقمِ فارسی ⇒ همان یک ردیف (POS-02)؛ کادر خالی؛ بارکدِ تنها همان یکی', async () => {
  await toSale();
  await clearCart();
  await enter('۳*62611');
  assert.deepEqual(await lines(), [['p1', 3]]);
  assert.equal(await box().inputValue(), '', 'کادر پس از افزودن خالی');
  assert.match(await toast(), /برنج به سبد اضافه شد/);
  await enter('2×۶۲۶۱۱');
  assert.deepEqual(await lines(), [['p1', 5]], 'اسکنِ دوباره فقط عدد را بالا می‌برد');
  await enter('62611');
  assert.deepEqual(await lines(), [['p1', 6]], 'بی پیشوند همان یکی');
  await wedge('62611');
  assert.deepEqual(await lines(), [['p1', 7]], 'بارکدخوانِ بیرون از کادر همان یک ردیف');
});

await step('«هر اسکن: ×۵» پس از پیشوند برمی‌گردد؛ پیشوند به‌جای آن است', async () => {
  await clearCart();
  await pg.click('#scan-mult-btns [data-mult="5"]');
  await enter('2*62611');
  assert.equal(await qty('p1'), 2, 'پیشوند به‌جای ×۵');
  await enter('62611');
  assert.equal(await qty('p1'), 7, 'سپس همان ×۵');
  await pg.click('#scan-mult-btns [data-mult="1"]');
});

await step('کیلویی با اعشار؛ اعشار برای کالای شمردنی و شمارِ صفر ⇒ پیام و هیچ، متن در کادر می‌ماند', async () => {
  await clearCart();
  await enter('۰٫۲۵*62622');
  assert.deepEqual(await lines(), [['p3', 0.25]]);
  await enter('1.5*62611');
  assert.equal(await qty('p1'), 0);
  assert.match(await toast(), /اعشار نمی‌گیرد/);
  assert.equal(await box().inputValue(), '1.5*62611');
  await enter('0*62611');
  assert.equal(await qty('p1'), 0);
  assert.match(await toast(), /بیشتر از صفر/);
  assert.equal(await box().inputValue(), '0*62611');
});

await step('«۳*» ِ تنها ⇒ پیام؛ اسکنِ بعدیِ بارکدخوان (بیرون از کادر) سه تا و کادر خالی؛ اسکنِ پس از آن یکی', async () => {
  await clearCart();
  await enter('۳*');
  assert.match(await toast(), /اسکنِ بعدی 3 تا به سبد می‌رود/);
  assert.equal(await box().inputValue(), '3*', 'پیشوند در کادر می‌ماند (رقمِ فارسی را صفحه لاتین می‌کند)');
  assert.deepEqual(await lines(), []);
  await wedge('62611');
  assert.deepEqual(await lines(), [['p1', 3]]);
  assert.equal(await box().inputValue(), '', 'پیشوند یک بار به کار رفت');
  await wedge('62611');
  assert.deepEqual(await lines(), [['p1', 4]]);
  //  «۳*» و بعد بارکدخوانی که در همان کادر می‌نویسد = همان «۳*کد» + Enter
  await box().fill('3*');
  await box().focus();
  await pg.keyboard.type('62611', { delay: 15 });
  await pg.keyboard.press('Enter');
  await pg.waitForTimeout(120);
  assert.deepEqual(await lines(), [['p1', 7]]);
});

await step('کالای ناشناس ⇒ «محصول جدید» با خودِ کد؛ «12x34» که بارکدِ کالاست همان کالا؛ کالای غیرفعال رد', async () => {
  await clearCart();
  await enter('4*99999');
  assert.deepEqual(await pg.evaluate(() => bpRows.map(r => r.barcode)), ['99999'], 'بی «۴*»');
  assert.deepEqual(await lines(), []);
  await toSale();
  await enter('12x34');
  assert.deepEqual(await lines(), [['p4', 1]], 'بارکدِ کامل بر پیشوند پیش است');
  await enter('2*12x34');
  assert.deepEqual(await lines(), [['p4', 3]]);
  await enter('3*62633');
  assert.match(await toast(), /غیرفعال است/);
  assert.equal(await qty('p2'), 0);
});

await step('انگلیسی و پشتو: برچسب و راهنمای کلید و پیامِ «اسکنِ بعدی» به زبانِ صفحه؛ فارسی برمی‌گردد', async () => {
  await pg.evaluate(() => goTo('settings'));
  const label = () => pg.locator('#in-pos-qty-prefix').evaluate((el) => el.parentElement.textContent.trim());
  await pg.evaluate(() => TohidWebI18n.setLang('en'));
  await pg.waitForFunction(() => /Quantity prefix/.test(document.getElementById('in-pos-qty-prefix').parentElement.textContent), null, { timeout: 5000 });
  assert.equal(await label(), 'Quantity prefix before a barcode');
  assert.match(await pg.locator('#pos-qty-prefix-hint').innerText(), /makes the next scan three/);
  await toSale();
  await enter('5*');
  await pg.waitForFunction(() => /next scan adds 5/.test(document.getElementById('toast-msg').textContent), null, { timeout: 5000 });
  await box().fill('');
  await pg.evaluate(() => TohidWebI18n.setLang('ps'));
  await pg.evaluate(() => goTo('settings'));
  await pg.waitForFunction(() => /مختاړی/.test(document.getElementById('in-pos-qty-prefix').parentElement.textContent), null, { timeout: 5000 });
  await pg.evaluate(() => TohidWebI18n.setLang('fa'));
  await pg.waitForFunction(() => /پیشوندِ شمار/.test(document.getElementById('in-pos-qty-prefix').parentElement.textContent), null, { timeout: 5000 });
});

await step('خاموش کردنِ دوباره ⇒ همان رفتارِ دیروز؛ هیچ خطای صفحه', async () => {
  await clearCart();
  await pg.evaluate(() => goTo('settings'));
  await pg.locator('#in-pos-qty-prefix').uncheck();
  assert.equal(await pg.evaluate((k) => localStorage.getItem(k), PREF), null);
  assert.equal(await box().evaluate((el) => el.inputMode), 'numeric');
  await toSale();
  await enter('3*62611');
  assert.equal(await qty('p1'), 0);
  assert.deepEqual(await pg.evaluate(() => bpRows.map(r => r.barcode)), ['3*62611']);
  await closeModals();
  assert.deepEqual(errors, []);
});

await browser.close();
server.close();
if (failed) { console.log(`\n${failed} گام سرخ`); process.exit(1); }
console.log('\nهمهٔ گام‌ها سبز');
