/**
 * نسخهٔ آیفون (وب) — چیدمان و دسترس‌پذیری با مرورگرِ واقعی (بندِ ۳۱ِ ممیزی).
 *
 *   • هیچ صفحه‌ای در پهنای ۳۲۰، ۳۹۰، ۷۶۸ و ۱۳۶۶ افقی بیرون نمی‌زند
 *   • رنگِ متن‌ها ≥ ۴٫۵:۱ (WCAG AA)، هر دکمه نام دارد، هر فیلد برچسب دارد (axe-core)
 *   • هیچ خطای صفحه‌ای
 *
 * اجرا:  node test/web-ui.mjs   (axe-core اگر نبود، فقط بخشِ دسترس‌پذیری رد می‌شود)
 */
import { createRequire } from 'node:module';
import assert from 'node:assert/strict';
import { execSync } from 'node:child_process';
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';

const require = createRequire(import.meta.url);
const globalRoot = (() => { try { return execSync('npm root -g', { encoding: 'utf8' }).trim(); } catch { return ''; } })();
const pick = (name) => { for (const p of [`${globalRoot}/${name}`, name]) { try { return require(p); } catch { /* بعدی */ } } return null; };
const pw = pick('playwright');
if (!pw) { console.log('⏭  پلی‌رایت پیدا نشد — این سنجه رد شد'); process.exit(0); }
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

if (!axeSrc) {
  console.log('⏭  axe-core پیدا نشد — سنجهٔ دسترس‌پذیری رد شد (npm i -g axe-core)');
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

await browser.close();
server.close();
console.log(failed ? `\n${failed} سنجه قرمز` : '\nهمه سبز');
process.exit(failed ? 1 : 0);
