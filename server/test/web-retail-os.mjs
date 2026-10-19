/**
 * «Professional Retail OS» در وب (docs/VISUAL-DIRECTION-fa.md) — با مرورگرِ واقعی.
 *
 *   • پهنای ۳۶۰، ۷۶۸ و ۱۲۸۰: هیچ صفحه‌ای افقی بیرون نمی‌زند؛ صندوق در کامپیوتر سه‌ستونه
 *     است (دسته‌ها راست، کالاها وسط، سفارش چپ)، در تبلت سبد همان نوارِ پایین است و در
 *     گوشی چیدمانِ پیشین می‌ماند
 *   • axe در روز و شب (کنتراست ≥ ۴٫۵:۱، نامِ دکمه و برچسب)
 *   • نمای کارتی/جدولی برای هر صفحه جدا ذخیره می‌شود و پس از بارگذاریِ دوباره می‌ماند؛
 *     روی گوشی کلید پنهان و نما کارت است
 *   • فروش با کیبورد: «/»، بارکد + Enter، نام + Enter، Ctrl+Enter و Enter ⇐ فروش ثبت؛
 *     بارکدخوانِ سخت‌افزاری (بی فوکوس) هم همان‌طور
 *
 * اجرا:  node test/web-retail-os.mjs
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

const CATS = ['خوراکی', 'نوشیدنی', 'بهداشتی'];
const products = [
  { id: 'p1', name: 'برنج درجه یک', unit: 'کیلوگرم', salePrice: 1000, purchasePrice: 800, category: 'خوراکی', minStock: 5, barcodes: ['111111'] },
  { id: 'p2', name: 'چای سبز', unit: 'عدد', salePrice: 500, purchasePrice: 400, category: 'نوشیدنی', minStock: 2, barcodes: ['222222'] },
  ...Array.from({ length: 10 }, (_, i) => ({ id: `q${i}`, name: `کالای نمونه ${i + 1}`, unit: 'عدد', salePrice: 100 + i * 10, purchasePrice: 80, category: CATS[i % 3], minStock: 3, barcodes: [`90000${i}`] })),
  { id: 'off', name: 'کالای خاموش', unit: 'عدد', salePrice: 10, purchasePrice: 5, category: 'خوراکی', active: false, barcodes: ['333333'] },
];
const LEDGER = {
  products,
  warehouseEntries: products.map((p, i) => ({ id: `w${i}`, productId: p.id, units: i === 3 ? 0 : 20, date: '2026-10-01' })),
  debtors: [{ id: 'c1', name: 'احمد کریمی' }, { id: 'c2', name: 'محمود رحیمی' }],
  transactions: [{ id: 't', debtorId: 'c1', type: 'give', amount: 5000, date: '2026-10-01', createdAt: 1 }],
  sales: [], saleItems: [],
};
const PAGES = ['dashboard', 'sale', 'products', 'debtors'];

const browser = await pw.chromium.launch();
let failed = 0;
const step = async (name, fn) => {
  try { await fn(); console.log(`✅  ${name}`); } catch (e) { failed++; console.log(`❌  ${name}\n    ${e.message}`); }
};

async function open(width, { height = 800, keepStorage = null } = {}) {
  const pg = await browser.newPage({ viewport: { width, height } });
  const errors = [];
  pg.on('pageerror', e => errors.push(String(e)));
  await pg.goto(url);
  await pg.evaluate(([d, extra]) => {
    localStorage.setItem('tohid-shop-data-v1', JSON.stringify(d));
    if (extra) Object.entries(extra).forEach(([k, v]) => localStorage.setItem(k, v));
  }, [LEDGER, keepStorage]);
  await pg.reload();
  await pg.waitForTimeout(1200);
  const skip = pg.getByText('ادامه بدون حساب');
  if (await skip.first().isVisible().catch(() => false)) { await skip.first().click(); await pg.waitForTimeout(600); }
  return { pg, errors };
}
const go = (pg, p) => pg.evaluate(x => goTo(x), p).then(() => pg.waitForTimeout(350));
const rect = (pg, sel) => pg.evaluate(s => { const e = document.querySelector(s); if (!e) return null; const r = e.getBoundingClientRect(); return { l: r.left, r: r.right, t: r.top, b: r.bottom, w: r.width, h: r.height, d: getComputedStyle(e).display, pos: getComputedStyle(e).position }; }, sel);

for (const width of [360, 768, 1280]) {
  await step(`پهنای ${width}: بی بیرون‌زدگیِ افقی و بی خطا (${PAGES.join('، ')}، با سبدِ پر و نمای جدولی)`, async () => {
    const { pg, errors } = await open(width);
    const bad = [];
    const check = async (tag) => {
      const over = await pg.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
      if (over > 1) bad.push(`${tag} (+${over}px)`);
    };
    for (const p of PAGES) { await go(pg, p); await check(p); }
    await go(pg, 'sale');
    await pg.evaluate(() => { addToCart('p1', 2); addToCart('p2', 1); });
    await pg.waitForTimeout(200);
    await check('sale+cart');
    await pg.evaluate(() => document.querySelectorAll('.page.active [data-view="table"]').forEach(b => b.click()));
    await pg.waitForTimeout(200);
    await check('sale+table');
    await pg.close();
    assert.deepEqual(bad, [], `بیرون‌زده: ${bad.join('، ')}`);
    assert.deepEqual(errors, [], `خطای صفحه: ${errors.join(' | ')}`);
  });
}

await step('صندوقِ کامپیوتر (۱۲۸۰): دسته‌ها راست، کالاها وسط، سفارش چپ؛ سبد درونِ ستون؛ نوارِ کار با کلیدها', async () => {
  const { pg } = await open(1280);
  await go(pg, 'sale');
  await pg.evaluate(() => addToCart('p1', 1));
  await pg.waitForTimeout(200);
  const rail = await rect(pg, '#pos-rail'), main = await rect(pg, '.pos-main'), order = await rect(pg, '#pos-order');
  assert.ok(rail.l >= main.r - 1, `ستونِ دسته‌ها باید راستِ کالاها باشد: ${JSON.stringify({ rail, main })}`);
  assert.ok(main.l >= order.r - 1, `پنلِ سفارش باید چپِ کالاها باشد: ${JSON.stringify({ main, order })}`);
  const drawer = await rect(pg, '#cart-drawer');
  assert.equal(drawer.pos, 'static', 'سبد در کامپیوتر درونِ ستونِ سفارش است، نه نوارِ ثابت');
  const cards = await pg.locator('#pos-products .pos-card').count();
  assert.equal(cards, products.filter(p => p.active !== false).length, 'همهٔ کالاهای فعال، بی کالای خاموش');
  const tiles = await pg.locator('#pos-rail [data-pos-cat]').allInnerTexts();
  assert.equal(tiles.length, 1 + CATS.length, `«همه» و دسته‌های واقعی: ${tiles.join(' | ')}`);
  const pay = await pg.locator('#pos-pay [data-pos-pay]').allInnerTexts();
  assert.deepEqual(pay.map(s => s.trim()), ['نقد', 'قرض (نسیه)'], 'فقط روش‌هایی که موتورِ فروش ثبت می‌کند');
  const acts = await pg.locator('#pos-actions .pos-act').allInnerTexts();
  assert.ok(acts.some(a => a.includes('Ctrl+Enter')) && acts.some(a => a.includes('Alt+C')), `برچسبِ کلیدها: ${acts.join(' | ')}`);
  //  دسته: فقط کالاهای همان دسته
  await pg.evaluate(() => document.querySelector('#pos-rail [data-pos-cat="نوشیدنی"]').click());
  const names = await pg.locator('#pos-products .pos-name').allInnerTexts();
  assert.ok(names.length > 0 && names.every(n => products.find(p => p.name === n)?.category === 'نوشیدنی'), names.join('، '));
  //  − / تعداد / + روی کارت همان سبد را عوض می‌کند
  await pg.evaluate(() => document.querySelector('#pos-products [data-pos-plus="p2"]').click());
  await pg.evaluate(() => document.querySelector('#pos-products [data-pos-plus="p2"]').click());
  assert.equal(await pg.locator('[data-pos-qty="p2"]').innerText(), '2');
  await pg.evaluate(() => document.querySelector('#pos-products [data-pos-minus="p2"]').click());
  assert.deepEqual(await pg.evaluate(() => cart.map(c => [c.productId, c.quantity])), [['p1', 1], ['p2', 1]]);
  assert.equal(await pg.locator('#pos-lines-count').innerText(), '2 قلم');
  await pg.close();
});

await step('تبلت (۷۶۸): ردیفِ دسته‌ها و شبکه، سبد همان نوارِ چسبیده به پایین؛ گوشی (۳۶۰): چیدمانِ پیشین', async () => {
  const t = await open(768);
  await go(t.pg, 'sale');
  await t.pg.evaluate(() => addToCart('p1', 1));
  await t.pg.waitForTimeout(300);
  const d = await rect(t.pg, '#cart-drawer');
  assert.equal(d.pos, 'fixed');
  assert.ok(d.b <= 800 + 1 && d.t < 800, `نوارِ سبد باید در دید باشد: ${JSON.stringify(d)}`);
  assert.equal((await rect(t.pg, '#pos-products')).d, 'block');
  await t.pg.close();
  const p = await open(360);
  await go(p.pg, 'sale');
  assert.equal((await rect(p.pg, '#pos-rail')).d, 'none');
  assert.equal((await rect(p.pg, '#pos-products')).d, 'none');
  assert.equal((await rect(p.pg, '.manual-scan-row')).d, 'flex', 'ورودِ دستیِ بارکد روی گوشی سرِ جایش');
  await p.pg.close();
});

await step('نمای کارتی/جدولی: پیش‌فرضِ فروش کارت، انتخاب برای هر صفحه جدا می‌ماند؛ گوشی همیشه کارت و بی کلید', async () => {
  const { pg } = await open(1280);
  await go(pg, 'sale');
  assert.equal(await pg.getAttribute('#pos-products', 'data-view'), 'card');
  await pg.evaluate(() => document.querySelector('#pos-view [data-view="table"]').click());
  await pg.waitForTimeout(150);
  assert.equal(await pg.getAttribute('#pos-products', 'data-view'), 'table');
  assert.ok(await pg.locator('#pos-products table.ro-table').count() === 1, 'جدول ساخته شد');
  assert.equal(await pg.getAttribute('#pos-view [data-view="table"]', 'aria-pressed'), 'true');
  const stored = await pg.evaluate(() => Object.fromEntries(Object.keys(localStorage).filter(k => k.startsWith('tohid-ui:')).map(k => [k, localStorage.getItem(k)])));
  assert.deepEqual(stored, { 'tohid-ui:view_mode:local:sale': 'table' });
  await pg.reload(); await pg.waitForTimeout(1200);
  const skip = pg.getByText('ادامه بدون حساب');
  if (await skip.first().isVisible().catch(() => false)) { await skip.first().click(); await pg.waitForTimeout(500); }
  await go(pg, 'sale');
  assert.equal(await pg.getAttribute('#pos-products', 'data-view'), 'table', 'پس از بارگذاریِ دوباره همان جدول');
  //  گوشی: کارت، کلید پنهان، و انتخابِ جدول پاک نمی‌شود
  await pg.setViewportSize({ width: 390, height: 844 });
  await pg.waitForTimeout(250);
  assert.equal(await pg.getAttribute('#pos-products', 'data-view'), 'card');
  assert.equal(await pg.evaluate(() => document.getElementById('pos-view').hidden), true);
  assert.equal(await pg.evaluate(() => localStorage.getItem('tohid-ui:view_mode:local:sale')), 'table');
  await pg.close();
});

await step('فروش با کیبورد: «/» ⇐ بارکد + Enter ⇐ نام + Enter ⇐ Ctrl+Enter ⇐ Enter = فروش ثبت؛ بارکدخوانِ سخت‌افزاری هم', async () => {
  const { pg, errors } = await open(1280);
  await go(pg, 'sale');
  await pg.evaluate(() => { stopSaleScanner(); document.activeElement && document.activeElement.blur(); });
  await pg.keyboard.press('/');
  assert.equal(await pg.evaluate(() => document.activeElement.id), 'pos-search', '«/» کادرِ جست‌وجوی صندوق را می‌گیرد');
  await pg.keyboard.type('111111');
  await pg.keyboard.press('Enter');
  await pg.keyboard.type('چای سبز');
  await pg.keyboard.press('Enter');
  assert.deepEqual(await pg.evaluate(() => cart.map(c => c.productId).sort()), ['p1', 'p2']);
  assert.equal(await pg.evaluate(() => document.activeElement.id), 'pos-search', 'فوکوس برای قلمِ بعدی می‌ماند');
  //  بارکدخوانِ سخت‌افزاری: بی فوکوس، نویسه‌های تند و Enter
  await pg.evaluate(() => document.activeElement.blur());
  await pg.keyboard.type('222222', { delay: 5 });
  await pg.keyboard.press('Enter');
  assert.deepEqual(await pg.evaluate(() => cart.find(c => c.productId === 'p2').quantity), 2);
  //  کالای خاموش با Enter هم نمی‌آید
  await pg.keyboard.press('/');
  await pg.keyboard.type('333333');
  await pg.keyboard.press('Enter');
  assert.equal(await pg.evaluate(() => cart.some(c => c.productId === 'off')), false);
  await pg.evaluate(() => { document.getElementById('pos-search').value = ''; document.activeElement.blur(); });
  //  Alt+C ⇐ انتخابِ قرض‌دار؛ Esc می‌بندد
  await pg.keyboard.press('Alt+KeyC');
  assert.equal(await pg.locator('#modal-pick-debtor.open').count(), 1, 'Alt+C پنجرهٔ قرض‌دار را باز می‌کند');
  await pg.keyboard.press('Escape');
  assert.equal(await pg.locator('#modal-pick-debtor.open').count(), 0);
  //  Ctrl+Enter ⇐ پنجرهٔ ثبت با فوکوس روی «تایید»؛ Enter ⇐ ثبت
  await pg.keyboard.press('Control+Enter');
  await pg.waitForTimeout(100);
  assert.equal(await pg.locator('#modal-sale-checkout.open').count(), 1);
  assert.equal(await pg.evaluate(() => document.activeElement.matches('#form-sale-checkout [type="submit"]')), true);
  await pg.keyboard.press('Enter');
  await pg.waitForTimeout(500);
  const out = await pg.evaluate(() => ({ n: sales.length, total: sales[0] && sales[0].finalTotal, method: sales[0] && sales[0].paymentMethod, cart: cart.length }));
  assert.deepEqual(out, { n: 1, total: 1000 + 500 * 2, method: 'cash', cart: 0 });
  assert.equal(await pg.locator('#pos-lines-count').innerText(), '0 قلم');
  await pg.close();
  assert.deepEqual(errors, []);
});

if (!axeSrc && SKIP) {
  console.log('⏭  axe-core پیدا نشد و SKIP_BROWSER_TESTS=1 است — سنجهٔ دسترس‌پذیری آگاهانه رد شد');
} else if (!axeSrc) {
  failed++;
  console.log('❌  axe-core پیدا نشد — سنجهٔ دسترس‌پذیری اجرا نشد و «قبول» هم نیست (npm i -g axe-core@4.10.3، یا AXE_PATH)');
} else {
  for (const theme of ['light', 'dark']) {
    for (const width of [390, 1280]) {
      await step(`axe (${theme}، ${width}): کنتراست ≥ ۴٫۵:۱، نامِ دکمه، برچسب، ARIA`, async () => {
        const { pg } = await open(width, { keepStorage: { 'tohid-theme': theme } });
        await pg.evaluate(t => document.documentElement.setAttribute('data-theme', t), theme);
        await pg.addScriptTag({ content: axeSrc });
        const bad = [];
        for (const p of PAGES) {
          await go(pg, p);
          if (p === 'sale') await pg.evaluate(() => { addToCart('p1', 1); document.querySelectorAll('.page.active [data-view="table"]').forEach(b => b.click()); });
          await pg.waitForTimeout(700);
          for (const scope of ['.page.active', '.sidebar', '.header']) {
            const v = await pg.evaluate(async (sel) => {
              const el = document.querySelector(sel);
              if (!el || !el.getClientRects().length) return [];
              const out = await window.axe.run(el, { runOnly: ['color-contrast', 'button-name', 'label', 'image-alt', 'link-name', 'aria-allowed-attr', 'aria-valid-attr-value', 'nested-interactive'] });
              return out.violations.map(x => `${x.id}: ${x.nodes.slice(0, 3).map(n => n.target.join(' ')).join(' ; ')}`);
            }, scope);
            v.forEach(x => bad.push(`${p} ${scope} — ${x}`));
          }
        }
        await pg.close();
        assert.deepEqual([...new Set(bad)], [], [...new Set(bad)].join('\n    '));
      });
    }
  }
}

await browser.close();
server.close();
console.log(failed ? `\n${failed} سنجه قرمز` : '\nهمه سبز');
process.exit(failed ? 1 : 0);
