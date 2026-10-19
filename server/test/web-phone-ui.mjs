/**
 * گوشیِ وب (phone-ui) با کرومیومِ واقعی — نوارِ پایینِ قرصی، و (در PRهای بعدی)
 * «حساب من»، «تنظیمات» و نوار/برگهٔ سبدِ صندوق.
 *
 *   • پهنای ۳۶۰، ۳۹۰ و ۷۶۸: بی بیرون‌زدگیِ افقی، هر تب ≥ ۴۸ پیکسل؛
 *   • تبِ فعال قرصِ تیره (`--nav-pill`) با برچسبِ دیدنی و `aria-current`؛ بقیه
 *     فقط آیکن ولی با نامِ دسترس‌پذیر؛
 *   • axe-core: کنتراست و نامِ پیوند؛ «کاهشِ حرکت» انیمیشن را خاموش می‌کند.
 *
 * اجرا:  node test/web-phone-ui.mjs
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
if (!axeSrc && !SKIP) missingTool('axe-core', 'npm i -g axe-core@4.10.3');

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
  products: [
    { id: 'p1', name: 'برنج سیلا', unit: 'کیلو', salePrice: 120, purchasePrice: 95, category: 'خوراکی' },
    { id: 'p2', name: 'روغن آفتاب‌گردان', unit: 'بوتل', salePrice: 310, purchasePrice: 260, category: 'خوراکی' },
    { id: 'p3', name: 'صابون گل', unit: 'دانه', salePrice: 40, purchasePrice: 30, category: 'بهداشتی' },
    { id: 'p4', name: 'چای سبز', unit: 'بسته', salePrice: 150, purchasePrice: 120, category: 'خوراکی' },
  ],
  warehouseEntries: ['p1', 'p2', 'p3', 'p4'].map((p, i) => ({ id: 'w' + i, productId: p, units: 50, date: '2026-10-01' })),
  debtors: [{ id: 'c1', name: 'احمد کریمی' }],
  transactions: [],
  sales: [],
  saleItems: [],
};

const browser = await pw.chromium.launch();
let failed = 0;
const step = async (name, fn) => {
  try { await fn(); console.log(`✅  ${name}`); } catch (e) { failed++; console.log(`❌  ${name}\n    ${e.message}`); }
};

async function open(width, opts = {}) {
  const ctx = await browser.newContext({ viewport: { width, height: 800 }, reducedMotion: opts.reducedMotion || 'no-preference' });
  const pg = await ctx.newPage();
  const errors = [];
  pg.on('pageerror', e => errors.push(String(e)));
  await pg.addInitScript((d) => {
    if (localStorage.getItem('seeded-phone-ui')) return;
    localStorage.setItem('tohid-shop-data-v1', JSON.stringify(d));
    localStorage.setItem('tohid-shop-auth-v1', JSON.stringify({ method: 'local', value: '', name: 'آزمون', loginAt: 1 }));
    localStorage.setItem('seeded-phone-ui', '1');
  }, LEDGER);
  await pg.goto(url);
  await pg.waitForTimeout(1000);
  const skip = pg.getByText('ادامه بدون حساب');
  if (await skip.first().isVisible().catch(() => false)) { await skip.first().click(); await pg.waitForTimeout(300); }
  return { pg, ctx, errors };
}

const overflow = (pg) => pg.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
async function axe(pg, selector, rules) {
  if (!axeSrc) return [];
  await pg.addScriptTag({ content: axeSrc });
  return pg.evaluate(async ([sel, r]) => (await window.axe.run(sel, { runOnly: r })).violations
    .map(v => `${v.id}: ${v.nodes.slice(0, 3).map(n => n.target.join(' ')).join(' ; ')}`), [selector, rules]);
}

/* ============================ نوارِ پایینِ قرصی ============================ */
for (const width of [360, 390, 768]) {
  await step(`نوارِ پایین در ${width}: قرصِ تیرهٔ تبِ فعال، برچسب و ۴۸ پیکسل`, async () => {
    const { pg, ctx, errors } = await open(width);
    for (const page of ['dashboard', 'sale', 'debtors', 'products', 'more']) {
      await pg.evaluate((p) => goTo(p), page);
      await pg.waitForTimeout(800);
      const nav = await pg.evaluate(() => {
        const css = (el, k) => getComputedStyle(el).getPropertyValue(k).trim();
        const root = document.documentElement;
        const items = [...document.querySelectorAll('#bottom-nav .bn-item')].map(a => {
          const r = a.getBoundingClientRect();
          const label = a.querySelector('.bn-label');
          return {
            page: a.dataset.page, active: a.classList.contains('active'), current: a.getAttribute('aria-current'),
            w: r.width, h: r.height, bg: getComputedStyle(a).backgroundColor,
            labelW: label ? label.getBoundingClientRect().width : 0, name: (label ? label.textContent : '').trim(),
          };
        });
        const probe = document.createElement('i');
        probe.style.background = css(root, '--nav-pill');
        document.body.appendChild(probe);
        const pill = getComputedStyle(probe).backgroundColor;
        probe.remove();
        return { items, pill };
      });
      assert.equal(nav.items.length, 5, 'پنج تب');
      const active = nav.items.filter(i => i.active);
      assert.equal(active.length, 1, `${page}: یک تبِ فعال`);
      assert.equal(active[0].page, page);
      assert.equal(active[0].current, 'page', 'aria-current روی تبِ فعال');
      assert.equal(active[0].bg, nav.pill, 'قرصِ فعال همان --nav-pill');
      assert.ok(active[0].labelW > 20, 'برچسبِ تبِ فعال دیده می‌شود');
      for (const i of nav.items) {
        assert.ok(i.w >= 48 && i.h >= 48, `${i.page}: ${i.w}×${i.h} زیرِ ۴۸`);
        assert.ok(i.name.length > 0, `${i.page}: نامِ دسترس‌پذیر`);
        if (!i.active) { assert.ok(i.labelW <= 1, `${i.page}: برچسبِ غیرفعال پنهانِ دیدنی`); assert.equal(i.current, null); }
      }
      assert.ok(await overflow(pg) <= 0, `${page}: بیرون‌زدگیِ افقی`);
    }
    const v = await axe(pg, '#bottom-nav', ['color-contrast', 'link-name']);
    assert.deepEqual(v, [], 'axe روی نوار');
    assert.deepEqual(errors, []);
    await ctx.close();
  });
}

await step('نوارِ پایین در ۱۲۸۰ پنهان است و منوی کناری می‌آید', async () => {
  const { pg, ctx } = await open(1280);
  assert.equal(await pg.evaluate(() => getComputedStyle(document.getElementById('bottom-nav')).display), 'none');
  await ctx.close();
});

await step('شب: قرصِ روشن روی نوارِ تیره، axe سبز', async () => {
  const { pg, ctx } = await open(390);
  //  «قرض‌داران» بی اشتراک کم‌رنگ است (`vip-locked-el`، رفتارِ پیشین) — تبِ آزاد سنجیده می‌شود
  await pg.evaluate(() => { document.documentElement.setAttribute('data-theme', 'dark'); goTo('more'); });
  await pg.waitForTimeout(900);
  const v = await axe(pg, '#bottom-nav', ['color-contrast', 'link-name']);
  assert.deepEqual(v, []);
  await ctx.close();
});

await step('«کاهشِ حرکت» (تنظیمِ «مثل گوشی»): جابه‌جاییِ قرص بی انیمیشن', async () => {
  const { pg, ctx } = await open(390, { reducedMotion: 'reduce' });
  await pg.evaluate(() => document.documentElement.removeAttribute('data-motion'));
  const dur = await pg.evaluate(() => getComputedStyle(document.querySelector('#bottom-nav .bn-item')).transitionDuration
    .split(',').map(s => parseFloat(s)).reduce((a, b) => Math.max(a, b), 0));
  assert.ok(dur < 0.01, `transition ${dur}s`);
  await ctx.close();
});

await browser.close();
server.close();
console.log(failed ? `\n${failed} مورد قرمز` : '\nهمه سبز');
process.exit(failed ? 1 : 0);
