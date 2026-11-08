/**
 * چهار زبانِ نسخهٔ وبِ دکان با مرورگرِ واقعی و همان `index.html` ِ یخ‌زده — فارسی، دری، انگلیسی، پشتو.
 *
 *   • فارسی (پیش‌فرض) همان دیروز است: هیچ واژه‌نامه‌ای بار نمی‌شود، `confirm` بومی می‌ماند، و متنِ صفحه بایت‌به‌بایت همان.
 *   • گزینش‌گرِ زبان در تنظیمات؛ زبان پس از بارگذاریِ دوباره می‌ماند.
 *   • هر زبان: `lang`/`dir` ِ درست (انگلیسی چپ‌به‌راست)، صفحه‌های اصلی (داشبورد، فروش، کالا، انبار، قرض‌داران، مصارف،
 *     تاریخچهٔ فروش، گزارش، خرید، تنظیمات) ترجمه‌شده: انگلیسی هیچ حرفِ فارسی جز دادهٔ دفتر ندارد، پشتو هیچ کلیدِ
 *     فارسیِ ترجمه‌نشده، دری همان فارسی از لایهٔ واژه («انبار» ⇐ «گدام»).
 *   • ⛔ داده دست نمی‌خورد: نامِ کالا/دسته/مشتری که خودش واژهٔ لایه است («گوشی») در دری همان می‌ماند؛ دفترِ ذخیره‌شده
 *     پس از رفت‌وبرگشتِ زبان‌ها بایت‌به‌بایت همان.
 *   • پیش‌نمایشِ فاکتور و چاپِ حرارتی (canvas): برچسب‌ها همان `receipt.<زبان>.json` (StrReceipt.kt)، دری = لایهٔ واژه،
 *     انگلیسی چپ‌به‌راست با رقمِ لاتین؛ نامِ کالا و واحد دست‌نخورده.
 *   • CSP: هیچ `securitypolicyviolation` و هیچ خطای صفحه.
 *
 * اجرا:  node test/web-shop-i18n.mjs   (نبودِ پلی‌رایت قرمز است، نه «رد» — F0-17)
 */
import { createRequire } from 'node:module';
import { missingTool, PLAYWRIGHT_INSTALL, closeSafeRoute } from './browser-guard.mjs';
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

const ROOT = path.join(import.meta.dirname, '..', '..');
const I18N = (f) => JSON.parse(fs.readFileSync(path.join(ROOT, 'license', 'i18n', f), 'utf8'));
const RECEIPT = { fa: I18N('receipt.fa.json'), en: I18N('receipt.en.json'), ps: I18N('receipt.ps.json') };
const FA_KEYS = new Set(I18N('web.fa.json'));
const PS = I18N('web.ps.json');
const LETTER = /[\u0621-\u064A\u066E-\u06D3\u06D5\u06FA-\u06FF]/;
const TYPES = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.json': 'application/json', '.png': 'image/png', '.svg': 'image/svg+xml', '.ttf': 'font/ttf', '.webmanifest': 'application/manifest+json', '.mp3': 'audio/mpeg' };
const server = http.createServer((req, res) => {
  const file = path.join(ROOT, decodeURIComponent(req.url.split('?')[0]).replace(/^\/+/, '') || 'index.html');
  if (!file.startsWith(ROOT) || !fs.existsSync(file) || fs.statSync(file).isDirectory()) { res.statusCode = 404; return res.end(); }
  res.setHeader('Content-Type', TYPES[path.extname(file)] || 'application/octet-stream');
  fs.createReadStream(file).pipe(res);
});
await new Promise(r => server.listen(0, '127.0.0.1', r));
const url = `http://127.0.0.1:${server.address().port}/index.html`;

//  «گوشی» و «انبار» واژه‌های لایهٔ دری‌اند (⇐ «مبایل»، «گدام»)؛ این‌جا نامِ کالا، دسته و مشتری‌اند، پس باید همان بمانند
const DATA = ['برنج', 'کیلوگرم', 'عدد', 'خوراکی', 'گوشی', 'انبار نور', 'احمد کریمی', 'برق', 'برق ماه', 'آزمون'];
const LEDGER = {
  products: [
    { id: 'p1', name: 'برنج', unit: 'کیلوگرم', salePrice: 1250000, purchasePrice: 990000, category: 'خوراکی', minStock: 2 },
    { id: 'p2', name: 'گوشی', unit: 'عدد', salePrice: 9000000, purchasePrice: 8000000, category: 'گوشی', minStock: 1 },
  ],
  warehouseEntries: [{ id: 'w1', productId: 'p1', units: 10, date: '2026-10-01' }, { id: 'w2', productId: 'p2', units: 3, date: '2026-10-01' }],
  debtors: [{ id: 'c1', name: 'احمد کریمی', phone: '0700123456' }, { id: 'c2', name: 'انبار نور', phone: '' }],
  transactions: [{ id: 't1', debtorId: 'c1', type: 'give', amount: 500000, date: '2026-10-02', createdAt: 2 }],
  sales: [{ id: 's1', invoiceNumber: 7, total: 1250000, discount: 0, finalTotal: 1250000, paidAmount: 1250000, remaining: 0,
    date: '2026-10-01', createdAt: Date.UTC(2026, 9, 1, 9, 5), status: 'completed', paymentMethod: 'cash' }],
  saleItems: [{ id: 'i1', saleId: 's1', productId: 'p1', quantity: 1, unitPrice: 1250000, totalPrice: 1250000 }],
  suppliers: [],
  expenses: [{ id: 'e1', title: 'برق ماه', category: 'برق', amount: 300000, date: '2026-10-03', createdAt: 3 }],
};
const PAGES = ['dashboard', 'sale', 'products', 'warehouse', 'debtors', 'expenses', 'sales', 'reports', 'purchasing', 'more', 'settings'];

const browser = await pw.chromium.launch();
let failed = 0;
const step = async (name, fn) => {
  try { await fn(); console.log(`✅  ${name}`); } catch (e) { failed++; console.log(`❌  ${name}\n    ${e.message}`); }
};

const ctx = await browser.newContext({ viewport: { width: 1200, height: 900 } });
const pg = await ctx.newPage();
//  فقط درخواستِ خودِ صفحه (سرویس‌ورکر همه را برای آفلاین پیش‌کش می‌کند و آن جدا است)
const requests = [];
pg.on('request', (r) => requests.push(new URL(r.url()).pathname));
const errors = [];
pg.on('pageerror', e => errors.push(String(e)));
await pg.route(/fonts\.(googleapis|gstatic)\.com/, closeSafeRoute(r => r.abort()));
await pg.route(/api\.vill3n\.top/, closeSafeRoute(r => r.abort()));
await pg.addInitScript(() => {
  window.__csp = [];
  document.addEventListener('securitypolicyviolation', (e) => window.__csp.push(`${e.violatedDirective} ${e.blockedURI}`));
});
await pg.goto(url);
await pg.evaluate(d => {
  localStorage.setItem('tohid-shop-data-v1', JSON.stringify(d));
  localStorage.setItem('tohid-shop-auth-v1', JSON.stringify({ method: 'local', value: '', name: 'آزمون', loginAt: 1 }));
}, LEDGER);
requests.length = 0;
await pg.reload();
await pg.waitForFunction(() => window.TohidWebI18n && typeof goTo === 'function' && window.TohidThermal);
await pg.evaluate(() => window.TohidWebI18n.ready());

/** متنِ دیدنیِ صفحهٔ کنونی (گره به گره) و صفت‌های دیدنی */
const visibleTexts = () => pg.evaluate(() => {
  const out = [];
  const vis = (el) => { const r = el.getBoundingClientRect(); const cs = getComputedStyle(el); return r.width > 0 && r.height > 0 && cs.visibility !== 'hidden' && cs.display !== 'none'; };
  const w = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
  for (let n = w.nextNode(); n; n = w.nextNode()) {
    const t = n.nodeValue.replace(/\s+/g, ' ').trim();
    if (t && n.parentElement && !n.parentElement.closest('script,style,textarea,#wi-lang-panel') && vis(n.parentElement)) out.push(t);
  }
  for (const el of document.querySelectorAll('[placeholder],[aria-label],[title]')) {
    if (!vis(el)) continue;
    for (const a of ['placeholder', 'aria-label', 'title']) { const v = el.getAttribute(a); if (v) out.push(v.replace(/\s+/g, ' ').trim()); }
  }
  return out;
});
const visit = async (p) => { await pg.evaluate((p) => goTo(p), p); await pg.waitForTimeout(150); return visibleTexts(); };
/** پیش‌نمایشِ فاکتور و نوشته‌های canvas ِ چاپِ حرارتی */
const receipt = () => pg.evaluate(async () => {
  openInvoiceModal('s1');
  //  لایه با MutationObserver می‌گرداند: یک گام صبر تا گره‌های تازه ترجمه شوند
  await new Promise((r) => setTimeout(r, 0));
  const area = document.getElementById('invoice-print-area');
  const html = area.innerText.replace(/\s+/g, ' ').trim();
  const C = CanvasRenderingContext2D.prototype;
  const orig = C.fillText;
  const paper = [];
  C.fillText = function (t, ...a) { paper.push({ t: String(t), align: this.textAlign, dir: this.direction }); return orig.call(this, t, ...a); };
  try { TohidThermal.renderCanvas(invoiceReceipt(sales.find((s) => s.id === 's1')), TohidThermal.WIDTH_80MM); } finally { C.fillText = orig; }
  document.querySelector('[data-close-modal="modal-invoice"]')?.click();
  return { html, paper };
});

let faTexts = null;
let faReceipt = null;
const ledgerBefore = await pg.evaluate(() => localStorage.getItem('tohid-shop-data-v1'));

await step('فارسی (پیش‌فرض) همان دیروز: lang=fa، rtl، هیچ واژه‌نامهٔ صفحه، confirm ِ بومی', async () => {
  const r = await pg.evaluate(() => ({
    lang: document.documentElement.lang, dir: document.documentElement.dir, active: TohidWebI18n.lang(),
    native: /\[native code\]/.test(Function.prototype.toString.call(window.confirm)),
  }));
  faTexts = {};
  for (const p of PAGES) faTexts[p] = await visit(p);
  assert.deepEqual(r, { lang: 'fa', dir: 'rtl', active: 'fa', native: true });
  const dicts = requests.filter((u) => /license\/(i18n\/web\.|dari\.js)/.test(u));
  assert.deepEqual(dicts, [], 'فارسی نباید واژه‌نامهٔ صفحه یا لایهٔ دری بار کند');
  assert.ok(faTexts.dashboard.includes('داشبورد'));
  faReceipt = await receipt();
  assert.match(faReceipt.html, /جمع اقلام 1,250,000 افغانی/);
  assert.ok(faReceipt.paper.some((x) => x.t === 'ممنون از خرید شما' && x.dir === 'rtl'));
});

await step('گزینش‌گرِ زبان در تنظیمات: چهار زبان به نامِ خودشان، با دسترس‌پذیری', async () => {
  await pg.evaluate(() => goTo('settings'));
  const r = await pg.evaluate(() => ({
    btns: [...document.querySelectorAll('#lang-toggle [data-lang-choice]')].map((b) => [b.dataset.langChoice, b.textContent, b.getAttribute('aria-checked')]),
    title: document.getElementById('wi-lang-title').textContent,
    role: document.getElementById('lang-toggle').getAttribute('role'),
  }));
  assert.deepEqual(r.btns, [['fa', 'فارسی', 'true'], ['prs', 'دری', 'false'], ['en', 'English', 'false'], ['ps', 'پښتو', 'false']]);
  assert.equal(r.title, 'زبانِ برنامه');
  assert.equal(r.role, 'radiogroup');
});

for (const lang of ['prs', 'en', 'ps']) {
  await step(`${lang}: انتخاب از تنظیمات ⇒ lang/dir ِ درست و صفحه‌های اصلی ترجمه‌شده`, async () => {
    await pg.evaluate(() => goTo('settings'));
    await pg.click(`#lang-toggle [data-lang-choice="${lang}"]`);
    await pg.evaluate(() => TohidWebI18n.ready());
    const r = await pg.evaluate(() => [document.documentElement.lang, document.documentElement.dir, TohidWebI18n.lang()]);
    assert.deepEqual(r, [lang, lang === 'en' ? 'ltr' : 'rtl', lang]);
    const bad = [];
    for (const p of PAGES) {
      const texts = await visit(p);
      for (const t of texts) {
        //  تک‌حرف = حرفِ آغازِ یک نام روی آواتار (داده)
        if (DATA.some((d) => t === d) || t.length === 1) continue;
        if (lang === 'en' && LETTER.test(t)) {
          //  فقط دادهٔ دفتر (نام، واحد، دسته) می‌تواند فارسی بماند
          const rest = DATA.reduce((s, d) => s.split(d).join(''), t);
          if (LETTER.test(rest)) bad.push(`${p}: ${t}`);
        }
        if (lang === 'ps' && FA_KEYS.has(t) && PS[t] !== t) bad.push(`${p}: ${t}`);
      }
      if (lang === 'prs') {
        assert.ok(!texts.includes('انبار'), `${p}: «انبار» در دری «گدام» است`);
        if (p === 'products') assert.ok(texts.includes('گوشی'), 'نامِ کالای «گوشی» داده است و در دری همان می‌ماند');
      }
    }
    assert.deepEqual(bad, [], `ترجمه‌نشده (${lang})`);
    if (lang === 'prs') {
      const nav = await pg.evaluate(() => document.querySelector('.nav-item[data-page="warehouse"]').textContent.replace(/\s+/g, ' ').trim());
      assert.equal(nav, 'گدام');
    }
    if (lang === 'en') {
      const nav = await pg.evaluate(() => document.querySelector('.nav-item[data-page="dashboard"]').textContent.replace(/\s+/g, ' ').trim());
      assert.equal(nav, 'Dashboard');
      assert.equal(await pg.evaluate(() => document.documentElement.getAttribute('data-digits')), null, 'انگلیسی همیشه رقمِ لاتین');
    }
  });

  await step(`${lang}: پیش‌نمایشِ فاکتور و چاپِ حرارتی از StrReceipt، با جهت و رقمِ درست، و داده دست‌نخورده`, async () => {
    const r = await receipt();
    const L = lang === 'prs' ? null : RECEIPT[lang];
    const paper = r.paper.map((x) => x.t);
    //  داده: نامِ کالا و واحد
    assert.ok(paper.includes('برنج'), 'نامِ کالا روی کاغذ');
    assert.ok(r.html.includes('برنج') && r.html.includes('کیلوگرم'), 'نامِ کالا و واحد در پیش‌نمایش');
    if (L) {
      for (const k of ['subtotal', 'total', 'paid', 'footer', 'kind', 'cash']) assert.ok(paper.includes(L[k]), `کاغذ: ${k} = ${L[k]}`);
      assert.ok(paper.includes(L.fallbackShop), 'عنوانِ پیش‌فرضِ دکان');
      for (const k of ['subtotal', 'discount', 'total', 'paid', 'remaining']) assert.ok(r.html.includes(L[k]), `پیش‌نمایش: ${k} = ${L[k]}`);
      assert.ok(paper.some((t) => t.startsWith(L.invoiceNo)), 'سطرِ شمارهٔ فاکتور');
    } else {
      //  دری = همان برچسب‌های صفحه از لایهٔ واژه: «فروشگاه» ⇐ «دکان»؛ بقیه همان فارسی
      assert.ok(paper.includes('دکان') && !paper.includes('فروشگاه'));
      assert.ok(paper.includes('جمع اقلام') && paper.includes('ممنون از خرید شما'));
    }
    const label = r.paper.find((x) => x.t === (L ? L.total : 'مبلغ نهایی'));
    assert.ok(label, 'برچسبِ مبلغِ نهایی');
    if (lang === 'en') {
      assert.deepEqual([label.dir, label.align], ['ltr', 'left'], 'انگلیسی: برچسب چپ');
      assert.ok(r.paper.some((x) => x.t === '1,250,000' && x.align === 'right'), 'انگلیسی: مبلغ راست، رقمِ لاتین');
      assert.ok(!/[۰-۹]/.test(r.paper.map((x) => x.t).join(' ')), 'انگلیسی: هیچ رقمِ فارسی روی کاغذ');
    } else {
      assert.deepEqual([label.dir, label.align], ['rtl', 'right']);
    }
  });
}

await step('برگشت به فارسی: متنِ هر صفحه و کاغذ دقیقاً همان پیش از رفت‌وبرگشت، confirm ِ بومی', async () => {
  await pg.evaluate(() => TohidWebI18n.setLang('fa'));
  const r = await pg.evaluate(() => ({ lang: document.documentElement.lang, dir: document.documentElement.dir,
    native: /\[native code\]/.test(Function.prototype.toString.call(window.confirm)) }));
  assert.deepEqual(r, { lang: 'fa', dir: 'rtl', native: true });
  //  «فروش» دوربین را خودکار روشن می‌کند و حالِ آن (در حالِ آماده‌سازی / پیدا نشد) به زمان بند است، نه به زبان
  for (const p of PAGES.filter((x) => x !== 'sale')) assert.deepEqual(await visit(p), faTexts[p], `صفحهٔ ${p}`);
  assert.deepEqual(await receipt(), faReceipt);
});

await step('زبان پس از بارگذاریِ دوباره می‌ماند', async () => {
  await pg.evaluate(() => TohidWebI18n.setLang('ps'));
  await pg.reload();
  await pg.waitForFunction(() => window.TohidWebI18n && typeof goTo === 'function');
  await pg.evaluate(() => TohidWebI18n.ready());
  const r = await pg.evaluate(() => [document.documentElement.lang, document.getElementById('page-title').textContent.trim()]);
  assert.equal(r[0], 'ps');
  assert.notEqual(r[1], 'داشبورد');
  await pg.evaluate(() => TohidWebI18n.setLang('fa'));
});

await step('⛔ داده: دفترِ ذخیره‌شده پس از همهٔ رفت‌وبرگشت‌ها بایت‌به‌بایت همان', async () => {
  assert.equal(await pg.evaluate(() => localStorage.getItem('tohid-shop-data-v1')), ledgerBefore);
});

await step('CSP بدتر نشد و هیچ خطای صفحه نیست', async () => {
  assert.deepEqual(await pg.evaluate(() => window.__csp), []);
  assert.deepEqual(errors, []);
});

await browser.close();
server.close();
console.log(failed ? `\n${failed} سنجه قرمز` : '\nهمه سبز');
process.exit(failed ? 1 : 0);
