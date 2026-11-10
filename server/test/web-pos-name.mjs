/**
 * نسخهٔ وب — جست‌وجوی نامِ کالا در کادرِ صندوق (POS-04، `license/catalog-match.js`)، با کرومیومِ واقعی.
 *
 *   • پیش‌فرض خاموش: نامِ کالا + Enter همان بارکدِ ناشناسِ دیروز است (پنجرهٔ «محصول جدید»)؛ کادر بی صفتِ تازه، فهرستی
 *     ساخته نمی‌شود؛ کلید در «تنظیمات» ⇐ «دوربین بارکدخوان» تیک‌نخورده و پس از روشن کردن با بارگذاریِ دوباره روشن می‌ماند.
 *   • روشن: «ك/ي» ِ عربی (در نامِ ذخیره‌شده و در پرس‌وجو)، نیم‌فاصله، غلطِ تایپی ⇒ فهرستِ زیرِ کادر؛ Enter نخستین را، ↓ و
 *     Enter دومی را، کلیک و لمس (گوشی) هر ردیف را برمی‌دارد؛ «هر اسکن: ×۵» و «۳*نام» ِ پیشوندِ روشن شمارند؛ اعشار برای کالای
 *     شمردنی ⇒ پیامِ صفحه و هیچ. بارکدِ کامل («12x34»، «62611») اول و همان راهِ صفحه؛ بی جور ⇒ «محصول جدید» ِ دیروز؛ کالای
 *     غیرفعال در فهرست نمی‌آید؛ Esc فهرست را می‌بندد؛ کالای تازه پس از ثبت پیدا می‌شود. axe بر پنلِ اسکن با فهرستِ باز و
 *     پنلِ تنظیم. انگلیسی متنِ خودش؛ خاموش کردنِ دوباره ⇒ همان رفتارِ دیروز. هیچ خطای صفحه.
 *
 * اجرا:  node test/web-pos-name.mjs        (WEB_ROOT=<پوشه> برای سنجیدنِ نسخهٔ دیگری از صفحه، مثلاً پیش از این تغییر)
 * نبودِ پلی‌رایت یا axe قرمز است، نه «رد» (F0-17)؛ ردِ آگاهانه فقط با SKIP_BROWSER_TESTS=1.
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
if (!axeSrc) missingTool('axe-core', 'npm i -g axe-core@4.10.3 (یا AXE_PATH)');

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
const PREF = 'tohid-pos-name-search-v1';
const LEDGER = {
  products: [
    { id: 'p1', name: 'برنج هندی', unit: 'عدد', salePrice: 300, purchasePrice: 200, barcodes: ['62611'] },
    { id: 'p2', name: 'روغنِ کهنه', unit: 'عدد', salePrice: 150, purchasePrice: 100, barcodes: ['62633'], active: false },
    { id: 'p3', name: 'شکر', unit: 'کیلوگرم', salePrice: 80, purchasePrice: 60, barcodes: ['62622'] },
    { id: 'p4', name: 'باتری قلمی', unit: 'عدد', salePrice: 40, purchasePrice: 30, barcodes: ['12x34'] },
    { id: 'p5', name: 'چای‌سبز احمد', unit: 'عدد', salePrice: 120, purchasePrice: 90, barcodes: [] },
    { id: 'p6', name: 'كيك شكلاتي', unit: 'عدد', salePrice: 25, purchasePrice: 15, barcodes: [] },
    { id: 'p7', name: 'چای سیاه', unit: 'عدد', salePrice: 110, purchasePrice: 80, barcodes: [] },
    { id: 'p8', name: 'پیچ', unit: 'عدد', salePrice: 2, purchasePrice: 1, barcodes: ['34'] },
  ],
  warehouseEntries: ['p1', 'p3', 'p4', 'p5', 'p6', 'p7', 'p8'].map((p, i) => ({ id: `w${i}`, productId: p, units: 500, date: '2026-10-01' })),
  sales: [], saleItems: [], nextInvoiceNo: 1,
};

const browser = await pw.chromium.launch();
let failed = 0;
const step = async (name, fn) => {
  try { await fn(); console.log(`✅  ${name}`); } catch (e) { failed++; console.log(`❌  ${name}\n    ${e.message}`); }
};
const errors = [];
const seed = ([dataKey, seedJson]) => {
  if (localStorage.getItem('seeded-profile')) return;
  localStorage.setItem(dataKey, seedJson);
  localStorage.setItem('tohid-shop-auth-v1', JSON.stringify({ method: 'local', value: '', name: 'آزمون', loginAt: 1 }));
  //  «جستجو» ِ کادرِ دستی پشتِ اشتراکِ «اسکنر بارکد» است (`vip.js`)؛ دکانِ مشترک
  localStorage.setItem('tohid-entitlement-v1', JSON.stringify({ source: 'paid', isPaid: true, features: ['sales', 'debtors', 'barcode'], trial: { used: true, active: false, daysLeft: 0 } }));
  localStorage.setItem('seeded-profile', '1');
};
async function page(opts) {
  const ctx = await browser.newContext(opts);
  await ctx.addInitScript(seed, [DATA_KEY, JSON.stringify(LEDGER)]);
  const pg = await ctx.newPage();
  pg.on('pageerror', e => errors.push(String(e)));
  return pg;
}
const pg = await page({ viewport: { width: 1280, height: 900 } });

async function open(p = pg) {
  await p.goto(url);
  await p.waitForFunction(() => typeof window.goTo === 'function' && document.readyState === 'complete');
  await p.waitForTimeout(600);
  const skip = p.getByText('ادامه بدون حساب');
  if (await skip.first().isVisible().catch(() => false)) { await skip.first().click(); await p.waitForTimeout(200); }
}
const lines = (p = pg) => p.evaluate(() => cart.map(c => [c.productId, Math.round(c.quantity * 1000) / 1000]));
const toast = () => pg.locator('#toast-msg').innerText().catch(() => '');
const box = (p = pg) => p.locator('#in-manual-barcode');
const list = (p = pg) => p.locator('#pos-name-results');
const options = (p = pg) => p.locator('#pos-name-results [role="option"]').allInnerTexts();
const shown = (p = pg) => p.evaluate(() => { const el = document.getElementById('pos-name-results'); return !!el && getComputedStyle(el).display !== 'none'; });
const clearToast = () => pg.evaluate(() => { const t = document.getElementById('toast-msg'); if (t) t.textContent = ''; });
/** نوشتن در کادرِ دستی، بی Enter */
async function type(text, p = pg) {
  if (p === pg) await clearToast();
  await box(p).fill(text);
  await p.waitForTimeout(60);
}
async function enter(text) { await type(text); await box().press('Enter'); await pg.waitForTimeout(120); }
const closeModals = (p = pg) => p.evaluate(() => { document.querySelectorAll('.modal-overlay.open,.modal.open,[id^="modal-"].open').forEach(m => { try { closeModal(m.id); } catch { m.classList.remove('open'); } }); });
const toSale = async (p = pg) => { await closeModals(p); await p.evaluate(() => { goTo('sale'); }); await p.waitForTimeout(150); };
const clearCart = (p = pg) => p.evaluate(() => { cart = []; saveDraftCart(); renderCart(); });
async function axe(sel) {
  await pg.addScriptTag({ content: axeSrc });
  return pg.evaluate(async (s) => (await window.axe.run(document.querySelector(s), { runOnly: ['wcag2a', 'wcag2aa'] })).violations
    .map((x) => `${x.id}: ${x.nodes.map((n) => n.target.join(' ')).join(', ')}`), sel);
}

await open();

await step('خاموش (پیش‌فرض): نامِ کالا + Enter همان بارکدِ ناشناسِ دیروز است؛ کادر بی صفتِ تازه؛ کلید تیک‌نخورده', async () => {
  await toSale();
  await type('برنج');
  assert.equal(await pg.locator('#pos-name-results').count(), 0, 'فهرستی ساخته نمی‌شود');
  assert.equal(await box().getAttribute('role'), null);
  assert.equal(await box().getAttribute('placeholder'), 'یا بارکد را دستی وارد کنید');
  assert.equal(await box().evaluate((el) => el.inputMode), 'numeric');
  await box().press('Enter');
  await pg.waitForTimeout(150);
  assert.deepEqual(await lines(), []);
  assert.deepEqual(await pg.evaluate(() => bpRows.map(r => r.barcode)), ['برنج'], 'پنجرهٔ «محصول جدید» با همان متن');
  await closeModals();
  await pg.evaluate(() => goTo('settings'));
  const toggle = pg.locator('#in-pos-name-search');
  await toggle.waitFor({ state: 'attached', timeout: 5000 });
  assert.equal(await toggle.isChecked(), false);
  assert.equal(await pg.evaluate((k) => localStorage.getItem(k), PREF), null);
});

await step('کلید در «دوربین بارکدخوان» روشن می‌شود و پس از بارگذاریِ دوباره روشن می‌ماند؛ کادر combobox با صفحه‌کلیدِ حرف؛ axe بر پنل', async () => {
  const toggle = pg.locator('#in-pos-name-search');
  const panelTitle = await toggle.evaluate((el) => el.closest('.panel').querySelector('.panel-head h2').textContent);
  assert.equal(panelTitle.trim(), 'دوربین بارکدخوان');
  await toggle.check();
  assert.equal(await pg.evaluate((k) => localStorage.getItem(k), PREF), '1');
  await open();
  await pg.evaluate(() => goTo('settings'));
  await pg.locator('#in-pos-name-search').waitFor({ state: 'attached', timeout: 5000 });
  assert.equal(await pg.locator('#in-pos-name-search').isChecked(), true);
  assert.match(await pg.locator('#pos-name-search-hint').innerText(), /بارکدِ کامل اول است/);
  assert.equal(await box().getAttribute('role'), 'combobox');
  assert.equal(await box().getAttribute('aria-expanded'), 'false');
  assert.equal(await box().getAttribute('placeholder'), 'بارکد یا نامِ کالا را بنویسید');
  assert.equal(await box().evaluate((el) => el.inputMode), 'text', 'صفحه‌کلیدِ گوشی با حرف');
  assert.deepEqual(await axe('.panel:has(#in-pos-name-search)'), []);
});

await step('روشن: «ك/ي» ِ عربی و نیم‌فاصله پیدا می‌شوند؛ Enter نخستین را برمی‌دارد؛ کادر خالی و فهرست بسته', async () => {
  await toSale();
  await clearCart();
  await type('کیک');
  assert.ok(await shown(), 'فهرست باز است');
  assert.equal((await options()).length, 1);
  assert.match((await options())[0], /كيك شكلاتي/);
  assert.equal(await box().getAttribute('aria-expanded'), 'true');
  assert.equal(await box().getAttribute('aria-activedescendant'), 'pos-name-results-0');
  await box().press('Enter');
  await pg.waitForTimeout(120);
  assert.deepEqual(await lines(), [['p6', 1]]);
  assert.equal(await box().inputValue(), '');
  assert.equal(await shown(), false);
  assert.match(await toast(), /كيك شكلاتي به سبد اضافه شد/);
  await enter('شكلات');
  assert.deepEqual(await lines(), [['p6', 2]], 'همان ردیف (POS-02)');
  await enter('چای سبز');
  assert.deepEqual((await lines()).find((l) => l[0] === 'p5'), ['p5', 1], 'نیم‌فاصلهٔ نامِ ذخیره‌شده');
});

await step('↓ و Enter دومی را برمی‌دارد؛ ↑ برمی‌گردد؛ Esc می‌بندد و متن می‌ماند؛ کلیکِ ردیف همان را', async () => {
  await clearCart();
  await type('چای');
  assert.equal((await options()).length, 2);
  assert.match((await options())[0], /چای‌سبز احمد/);
  assert.match((await options())[1], /چای سیاه/);
  await box().press('ArrowDown');
  assert.equal(await box().getAttribute('aria-activedescendant'), 'pos-name-results-1');
  assert.equal(await pg.locator('#pos-name-results-1').getAttribute('aria-selected'), 'true');
  await box().press('ArrowUp');
  await box().press('ArrowUp');
  assert.equal(await box().getAttribute('aria-activedescendant'), 'pos-name-results-1', 'دور می‌زند');
  await box().press('Enter');
  await pg.waitForTimeout(120);
  assert.deepEqual(await lines(), [['p7', 1]]);
  await type('چای');
  await box().press('Escape');
  assert.equal(await shown(), false);
  assert.equal(await box().inputValue(), 'چای');
  assert.equal(await box().getAttribute('aria-expanded'), 'false');
  await box().press('ArrowDown');
  assert.equal(await shown(), true, '↓ دوباره باز می‌کند');
  await pg.locator('#pos-name-results-0').click();
  await pg.waitForTimeout(120);
  assert.deepEqual(await lines(), [['p7', 1], ['p5', 1]]);
  assert.equal(await box().inputValue(), '');
  assert.equal(await pg.evaluate(() => document.activeElement && document.activeElement.id), 'in-manual-barcode', 'تمرکز در کادر می‌ماند');
});

await step('بارکدِ کامل اول و همان راهِ صفحه («12x34»، «62611»)؛ بی جور ⇒ «محصول جدید»؛ کالای غیرفعال در فهرست نیست', async () => {
  await clearCart();
  await type('12x34');
  assert.equal(await shown(), false, 'متنی که بارکد است فهرست نمی‌گیرد');
  await box().press('Enter');
  await pg.waitForTimeout(120);
  assert.deepEqual(await lines(), [['p4', 1]]);
  await enter('62611');
  assert.deepEqual(await lines(), [['p4', 1], ['p1', 1]]);
  assert.match(await toast(), /برنج هندی به سبد اضافه شد/);
  await type('روغن');
  assert.equal(await shown(), false, 'کالای غیرفعال نه');
  await enter('زعفران');
  assert.deepEqual(await pg.evaluate(() => bpRows.map(r => r.barcode)), ['زعفران'], 'بی جور ⇒ همان «محصول جدید»');
  assert.deepEqual(await lines(), [['p4', 1], ['p1', 1]]);
  await toSale();
});

await step('غلطِ تایپی («باطری»)، «ي» ِ عربی در پرس‌وجو و «هر اسکن: ×۵»', async () => {
  await clearCart();
  await type('باطری');
  assert.match((await options())[0], /باتری قلمی/);
  await box().press('Escape');
  await pg.click('#scan-mult-btns [data-mult="5"]');
  await enter('برنج هندي');
  assert.deepEqual(await lines(), [['p1', 5]]);
  await pg.click('#scan-mult-btns [data-mult="1"]');
});

await step('با پیشوندِ روشن: «۳*نام» سه تا، «۳*کد» همان cart-query.js، اعشار برای شمردنی ⇒ پیام و هیچ', async () => {
  await pg.evaluate(() => goTo('settings'));
  await pg.locator('#in-pos-qty-prefix').check();
  await toSale();
  await clearCart();
  assert.equal(await box().evaluate((el) => el.inputMode), 'text', 'پیشوند صفحه‌کلیدِ حرف را عوض نمی‌کند');
  await type('۳*برنج');
  assert.match((await options())[0], /برنج هندی/);
  await box().press('Enter');
  await pg.waitForTimeout(120);
  assert.deepEqual(await lines(), [['p1', 3]]);
  await enter('2*62611');
  assert.deepEqual(await lines(), [['p1', 5]]);
  await enter('1.5*چای سیاه');
  assert.match(await toast(), /اعشار نمی‌گیرد/);
  assert.equal(await box().inputValue(), '1.5*چای سیاه');
  assert.deepEqual(await lines(), [['p1', 5]]);
  await enter('0.5*شکر');
  assert.deepEqual(await lines(), [['p1', 5], ['p3', 0.5]]);
  await pg.evaluate(() => goTo('settings'));
  await pg.locator('#in-pos-qty-prefix').uncheck();
  await toSale();
});

await step('کالای تازه پس از ثبت پیدا می‌شود (نمایه با تغییرِ کالاها، نه با هر حرف)', async () => {
  await clearCart();
  await pg.evaluate(() => { products.push({ id: 'p9', name: 'زعفران قائنات', unit: 'عدد', salePrice: 900, purchasePrice: 700, barcodes: [] }); rebuildBarcodeIndex(); });
  await enter('زعفران');
  assert.deepEqual(await lines(), [['p9', 1]]);
  await pg.evaluate(() => { products.find((p) => p.id === 'p9').name = 'زعفرانِ اعلا'; rebuildBarcodeIndex(); });
  await type('اعلا');
  assert.match((await options())[0], /زعفرانِ اعلا/);
  await box().fill('');
});

await step('axe بر پنلِ اسکن با فهرستِ باز', async () => {
  await type('چای');
  assert.ok(await shown());
  assert.deepEqual(await axe('.scanner-panel'), []);
  await box().fill('');
});

await step('انگلیسی: برچسب، راهنما، placeholder، نامِ فهرست و قیمت به زبانِ صفحه؛ نامِ کالا دست نمی‌خورد؛ فارسی برمی‌گردد', async () => {
  await pg.evaluate(() => TohidWebI18n.setLang('en'));
  await pg.evaluate(() => goTo('settings'));
  await pg.waitForFunction(() => /Product name search/.test(document.getElementById('in-pos-name-search').parentElement.textContent), null, { timeout: 5000 });
  assert.equal(await pg.locator('#in-pos-name-search').evaluate((el) => el.parentElement.textContent.trim()), 'Product name search in the sale box');
  assert.match(await pg.locator('#pos-name-search-hint').innerText(), /full barcode comes first/);
  await toSale();
  await pg.waitForFunction(() => document.getElementById('in-manual-barcode').placeholder === 'Type a barcode or product name', null, { timeout: 5000 });
  await type('چای');
  await pg.waitForFunction(() => /AFN/.test(document.getElementById('pos-name-results').textContent), null, { timeout: 5000 });
  assert.match((await options())[0], /چای‌سبز احمد/, 'نامِ کالا داده است');
  assert.equal(await list().getAttribute('aria-label'), 'Products whose name matches');
  await box().fill('');
  await pg.evaluate(() => TohidWebI18n.setLang('fa'));
  await pg.waitForFunction(() => document.getElementById('in-manual-barcode').placeholder === 'بارکد یا نامِ کالا را بنویسید', null, { timeout: 5000 });
});

await step('گوشی (لمس): ردیف با لمس برداشته می‌شود؛ کادر روشن صفحه‌کلیدِ حرف دارد', async () => {
  const ph = await page({ viewport: { width: 390, height: 844 }, hasTouch: true, isMobile: true });
  await open(ph);
  await ph.evaluate((k) => localStorage.setItem(k, '1'), PREF);
  await open(ph);
  await toSale(ph);
  await clearCart(ph);
  await type('كيك', ph);
  await ph.locator('#pos-name-results-0').tap();
  await ph.waitForTimeout(150);
  assert.deepEqual(await lines(ph), [['p6', 1]]);
  assert.equal(await box(ph).inputValue(), '');
  await type('چای', ph);
  const tall = await ph.locator('#pos-name-results-1').evaluate((el) => el.getBoundingClientRect().height);
  assert.ok(tall >= 44, `ردیفِ لمسی ${tall}px`);
  await ph.locator('#pos-name-results-1').tap();
  await ph.waitForTimeout(150);
  assert.deepEqual(await lines(ph), [['p6', 1], ['p7', 1]]);
  await ph.context().close();
});

await step('خاموش کردنِ دوباره ⇒ همان رفتارِ دیروز؛ هیچ خطای صفحه', async () => {
  await clearCart();
  await pg.evaluate(() => goTo('settings'));
  await pg.locator('#in-pos-name-search').uncheck();
  assert.equal(await pg.evaluate((k) => localStorage.getItem(k), PREF), null);
  assert.equal(await box().getAttribute('role'), null);
  assert.equal(await box().getAttribute('aria-expanded'), null);
  assert.equal(await pg.locator('#pos-name-results').count(), 0, 'فهرست برداشته شد');
  assert.equal(await box().evaluate((el) => el.closest('.manual-scan-row').getAttribute('style')), '', 'سطرِ کادر همان');
  assert.equal(await box().getAttribute('placeholder'), 'یا بارکد را دستی وارد کنید');
  assert.equal(await box().evaluate((el) => el.inputMode), 'numeric');
  await toSale();
  await type('برنج');
  assert.equal(await shown(), false);
  await box().press('Enter');
  await pg.waitForTimeout(150);
  assert.deepEqual(await lines(), []);
  assert.deepEqual(await pg.evaluate(() => bpRows.map(r => r.barcode)), ['برنج']);
  await closeModals();
  assert.deepEqual(errors, []);
});

await browser.close();
server.close();
if (failed) { console.log(`\n${failed} گام سرخ`); process.exit(1); }
console.log('\nهمهٔ گام‌ها سبز');
