/**
 * LOC-01، LOC-05، LOC-07، LOC-09 در وب — با مرورگرِ واقعی و همان `index.html`.
 *
 *   • پیش‌فرض همان دیروز است: تاریخِ صفحه همان خروجیِ Intl (طبقِ بخشِ ۳.۱۴ ِ
 *     سند، وب تاریخ را با Intl می‌سازد)، نامِ ماهِ دری، مبلغ و فاکتور همان
 *     formatMoney با «افغانی»، و هیچ ویژگیِ فونتی
 *   • نمادِ پولِ فاکتور: «؋» به انتخابِ دکان
 *   • «رقمِ فارسی»: صفحه با ss01 ِ وزیرمتن (پیکسل‌به‌پیکسل همان ۱۲۳) و فاکتور
 *     با ۱٬۲۵۰٬۰۰۰ افغانی — ولی دادهٔ ذخیره‌شده ASCII می‌ماند
 *   • «پشتو»: نامِ ماه‌ها از هستهٔ تقویم، پس از بارگذاریِ دوباره هم
 *   • تلفن: ذخیره با ‎+93، نمایش با LRM، جست‌وجوی «07…» و رقمِ فارسی؛
 *     مقدارِ قدیمی دست نمی‌خورد و هر چه دیروز پیدا می‌شد باز پیدا می‌شود
 *
 * اجرا:  node test/web-locale.mjs   (نبودِ پلی‌رایت قرمز است، نه «رد» — F0-17)
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

const ROOT = path.join(import.meta.dirname, '..', '..');
const TYPES = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.json': 'application/json', '.png': 'image/png', '.svg': 'image/svg+xml', '.ttf': 'font/ttf', '.webmanifest': 'application/manifest+json' };
const server = http.createServer((req, res) => {
  const file = path.join(ROOT, decodeURIComponent(req.url.split('?')[0]).replace(/^\/+/, '') || 'index.html');
  if (!file.startsWith(ROOT) || !fs.existsSync(file) || fs.statSync(file).isDirectory()) { res.statusCode = 404; return res.end(); }
  res.setHeader('Content-Type', TYPES[path.extname(file)] || 'application/octet-stream');
  fs.createReadStream(file).pipe(res);
});
await new Promise(r => server.listen(0, '127.0.0.1', r));
const url = `http://127.0.0.1:${server.address().port}/index.html`;

const LEDGER = {
  products: [{ id: 'p1', name: 'برنج', unit: 'کیلوگرم', salePrice: 1250000, purchasePrice: 990000, category: 'خوراکی', minStock: 2 }],
  warehouseEntries: [{ id: 'w', productId: 'p1', units: 10, date: '2026-10-01' }],
  debtors: [
    { id: 'c1', name: 'احمد کریمی', phone: '0700123456' },
    { id: 'c2', name: 'محمود', phone: '+93788000111' },
  ],
  transactions: [],
  sales: [{ id: 's1', invoiceNumber: 7, total: 1250000, discount: 0, finalTotal: 1250000, paidAmount: 1250000, remaining: 0,
    date: '2026-10-01', createdAt: Date.UTC(2026, 9, 1, 9, 5), status: 'completed', paymentMethod: 'cash' }],
  saleItems: [{ id: 'i1', saleId: 's1', productId: 'p1', quantity: 1, unitPrice: 1250000, totalPrice: 1250000 }],
  suppliers: [],
};

const browser = await pw.chromium.launch();
let failed = 0;
const step = async (name, fn) => {
  try { await fn(); console.log(`✅  ${name}`); } catch (e) { failed++; console.log(`❌  ${name}\n    ${e.message}`); }
};

const ctx = await browser.newContext({ viewport: { width: 1000, height: 900 } });
const pg = await ctx.newPage();
const errors = [];
pg.on('pageerror', e => errors.push(String(e)));
//  Google Fonts بیرون از آزمون است؛ وزیرمتنِ محلی همان فایل‌های مخزن است
await pg.route(/fonts\.(googleapis|gstatic)\.com/, r => r.abort());
await pg.goto(url);
await pg.evaluate(d => {
  localStorage.setItem('tohid-shop-data-v1', JSON.stringify(d));
  localStorage.setItem('tohid-shop-auth-v1', JSON.stringify({ method: 'local', value: '', name: 'آزمون', loginAt: 1 }));
}, LEDGER);
await pg.reload();
await pg.waitForFunction(() => typeof goTo === 'function' && !!window.TohidText && !!window.TohidCalendar);
await pg.evaluate(() => document.fonts.ready);

await step('پیش‌فرض همان دیروز است: تاریخ، ماهِ دری، مبلغ و فاکتور', async () => {
  const r = await pg.evaluate(() => {
    const old = new Intl.DateTimeFormat('fa-IR-u-nu-latn', { year: 'numeric', month: '2-digit', day: '2-digit' });
    const bad = [];
    for (let t = Date.UTC(2000, 0, 1); t < Date.UTC(2040, 0, 1); t += 86400000) {
      const iso = new Date(t).toISOString().slice(0, 10);
      const a = old.format(new Date(iso + 'T00:00:00'));
      if (formatDate(iso) !== a) bad.push(`${iso}: ${formatDate(iso)} ≠ ${a}`);
    }
    const money = [0, 1, 0.5, 1.5, 2.5, 999, 1000, 1250000, 1234567.89, -1234567.5, -2, 1e12];
    const moneyBad = money.filter(x => rcNum(x) !== formatMoney(x) || rcAfn(x) !== formatMoney(x) + ' افغانی');
    const sale = sales.find(s => s.id === 's1');
    return {
      bad: bad.slice(0, 5), moneyBad,
      digitsAttr: document.documentElement.getAttribute('data-digits'),
      feat: getComputedStyle(document.body).fontFeatureSettings,
      months: JALALI_MONTHS.slice(),
      html: invoiceHtml(sale).replace(/\s+/g, ' '),
      receipt: invoiceReceipt(sale),
      today: formatDate('2026-10-01'),
    };
  });
  assert.deepEqual(r.bad, [], 'formatDate با Intl ِ دیروز فرق کرد');
  assert.deepEqual(r.moneyBad, [], 'مبلغِ فاکتور با formatMoney ِ دیروز فرق کرد');
  assert.equal(r.digitsAttr, null);
  assert.equal(r.feat, 'normal');
  assert.deepEqual(r.months, ['حمل', 'ثور', 'جوزا', 'سرطان', 'اسد', 'سنبله', 'میزان', 'عقرب', 'قوس', 'جدی', 'دلو', 'حوت']);
  assert.ok(r.html.includes('<b>1,250,000 افغانی</b>'), r.html.slice(0, 400));
  assert.ok(r.html.includes(`تاریخ: ${r.today} — `), r.html.slice(0, 400));
  assert.ok(r.html.includes('فاکتور فروش #7'));
  assert.equal(r.receipt.totals[0].left, '1,250,000');
  assert.equal(r.receipt.rows[1].right.trim(), '1 کیلوگرم × 1,250,000');
  assert.ok(r.receipt.meta[1].startsWith('1405/07/09 — '), r.receipt.meta[1]);
});

await step('رقمِ فارسی: صفحه با ss01 (همان شکلِ ۱۲۳)، فاکتور با ۱٬۲۵۰٬۰۰۰ افغانی، داده ASCII', async () => {
  await pg.evaluate(() => goTo('settings'));
  await pg.click('#digits-toggle [data-digits-choice="fa"]');
  const r = await pg.evaluate(() => {
    const sale = sales.find(s => s.id === 's1');
    return {
      attr: document.documentElement.getAttribute('data-digits'),
      feat: getComputedStyle(document.body).fontFeatureSettings,
      active: document.querySelector('#digits-toggle .active')?.dataset.digitsChoice,
      saved: JSON.parse(localStorage.getItem('tohid-shop-settings-v1')).digits,
      html: invoiceHtml(sale).replace(/\s+/g, ' '),
      receipt: invoiceReceipt(sale),
      //  صفحه: متن همان ASCII است، فقط شکلش فارسی
      screenText: formatMoney(1250000) + ' ' + formatDate('2026-10-01'),
    };
  });
  assert.equal(r.attr, 'fa');
  assert.match(r.feat, /ss01/);
  assert.equal(r.active, 'fa');
  assert.equal(r.saved, 'fa');
  assert.ok(r.html.includes('<b>۱٬۲۵۰٬۰۰۰ افغانی</b>'), r.html.slice(0, 400));
  assert.ok(r.html.includes('تاریخ: ۱۴۰۵/۰۷/۰۹ — '), r.html.slice(0, 400));
  assert.ok(r.html.includes('فاکتور فروش #۷'));
  assert.equal(r.receipt.totals[0].left, '۱٬۲۵۰٬۰۰۰');
  assert.ok(r.receipt.meta[1].startsWith('۱۴۰۵/۰۷/۰۹ — '), r.receipt.meta[1]);
  assert.equal(r.screenText, '1,250,000 1405/07/09');

  //  پیکسل‌به‌پیکسل: «1234567890» با ss01 همان «۱۲۳۴۵۶۷۸۹۰» است، و بی ss01 نیست
  await pg.evaluate(() => {
    const box = document.createElement('div');
    box.id = 'loc-probe';
    box.style.cssText = 'position:fixed;top:0;left:0;z-index:99999;background:#fff;color:#000;font-size:32px;padding:4px;direction:ltr';
    const row = 'display:block;width:320px;height:48px;line-height:48px;';
    box.innerHTML = `<div id="p-latn" style="${row}">1234567890</div><div id="p-fa" style="${row}">۱۲۳۴۵۶۷۸۹۰</div>`
      + `<div id="p-off" style="${row}font-feature-settings:normal">1234567890</div>`;
    document.body.appendChild(box);
  });
  await pg.evaluate(() => document.fonts.ready);
  const fam = await pg.evaluate(() => getComputedStyle(document.getElementById('p-latn')).fontFamily);
  assert.match(fam, /^"?Vazirmatn Local/, fam);
  const [a, b, c] = await Promise.all(['#p-latn', '#p-fa', '#p-off'].map(s => pg.locator(s).screenshot()));
  assert.ok(a.equals(b), 'رقمِ لاتین با ss01 به شکلِ فارسی کشیده نشد');
  assert.ok(!a.equals(c), 'بی ss01 هم همان شکل بود — سنجه چیزی نمی‌سنجد');
  await pg.evaluate(() => document.getElementById('loc-probe').remove());
});

await step('تلفن: ذخیره با ‎+93، نمایش با LRM، جست‌وجوی 07… و رقمِ فارسی؛ مقدارِ قدیمی دست نمی‌خورد', async () => {
  await pg.evaluate(() => { goTo('debtors'); openAddDebtor(); });
  await pg.fill('#in-debtor-name', 'کریم');
  await pg.fill('#in-debtor-phone', '۰۷۹۹ ۱۲۳ ۴۵۶');
  await pg.click('#form-debtor button[type="submit"]');
  const r = await pg.evaluate(() => {
    const stored = JSON.parse(localStorage.getItem('tohid-shop-data-v1')).debtors;
    const find = (q) => { renderDebtors(q); return Array.from(document.querySelectorAll('#debtor-list .debtor-meta span')).map(x => x.textContent); };
    return {
      stored: stored.map(d => [d.name, d.phone]),
      all: find(''),
      by0799: find('0799'), byFa: find('۰۷۹۹'), by0700: find('0700'), by788: find('788'), byPlus: find('+93788'),
      supplier: phoneForSave(' 93 700 000 001 '), junk: phoneForSave(' دفتر ۰۷۸۸ '), empty: phoneForSave('  '),
    };
  });
  assert.deepEqual(r.stored, [['احمد کریمی', '0700123456'], ['محمود', '+93788000111'], ['کریم', '+93799123456']]);
  assert.ok(r.all.includes('‎+93 79 912 3456'), r.all.join(' | '));
  assert.ok(r.all.includes('0700123456'), 'مقدارِ قدیمی همان‌طور نشان داده نشد');
  assert.deepEqual(r.by0799, ['‎+93 79 912 3456']);
  assert.deepEqual(r.byFa, ['‎+93 79 912 3456']);
  assert.deepEqual(r.by0700, ['0700123456'], 'آنچه دیروز پیدا می‌شد، گم شد');
  assert.deepEqual(r.by788, ['‎+93 78 800 0111']);
  assert.deepEqual(r.byPlus, ['‎+93 78 800 0111'], 'آنچه دیروز پیدا می‌شد، گم شد');
  assert.equal(r.supplier, '+93700000001');
  assert.equal(r.junk, 'دفتر 0788');
  assert.equal(r.empty, '');
});

await step('نمادِ پولِ فاکتور: «؋» به انتخابِ دکان، بی اعشار (LOC-07)', async () => {
  await pg.evaluate(() => goTo('settings'));
  const before = await pg.evaluate(() => document.querySelector('#currency-toggle .active')?.dataset.currencyChoice);
  assert.equal(before, 'word', 'پیش‌فرض «افغانی» است');
  await pg.click('#currency-toggle [data-currency-choice="sign"]');
  const r = await pg.evaluate(() => {
    const sale = sales.find(s => s.id === 's1');
    return {
      afn: rcAfn(1250000.4),
      html: invoiceHtml(sale).replace(/\s+/g, ' '),
      saved: JSON.parse(localStorage.getItem('tohid-shop-settings-v1')).currency,
      active: document.querySelector('#currency-toggle .active')?.dataset.currencyChoice,
    };
  });
  //  رقمِ فارسی هنوز روشن است
  assert.equal(r.afn, '۱٬۲۵۰٬۰۰۰ ؋');
  assert.ok(r.html.includes('<b>۱٬۲۵۰٬۰۰۰ ؋</b>'), r.html.slice(0, 400));
  assert.equal(r.saved, 'sign');
  assert.equal(r.active, 'sign');
});

await step('ماهِ پشتو از هستهٔ تقویم، و پس از بارگذاریِ دوباره هم', async () => {
  await pg.evaluate(() => goTo('settings'));
  await pg.click('#months-toggle [data-months-choice="ps"]');
  await pg.reload();
  await pg.waitForFunction(() => typeof goTo === 'function');
  const r = await pg.evaluate(() => {
    goTo('reports');
    const rep = document.getElementById('rep-sales-jmonth');
    return {
      months: JALALI_MONTHS.slice(),
      rep: rep ? Array.from(rep.options).map(o => o.textContent) : null,
      digits: document.documentElement.getAttribute('data-digits'),
      active: document.querySelector('#months-toggle .active')?.dataset.monthsChoice,
      sample: document.getElementById('months-sample').textContent,
    };
  });
  assert.deepEqual(r.months, ['وری', 'غویی', 'غبرګولی', 'چنګاښ', 'زمری', 'وږی', 'تله', 'لړم', 'لیندۍ', 'مرغومی', 'سلواغه', 'کب']);
  if (r.rep && r.rep.length) assert.equal(r.rep[0], 'وری');
  assert.equal(r.digits, 'fa', 'انتخابِ رقم پس از بارگذاری نماند');
  assert.equal(r.active, 'ps');
  assert.match(r.sample, /^\d+ (وری|غویی|غبرګولی|چنګاښ|زمری|وږی|تله|لړم|لیندۍ|مرغومی|سلواغه|کب) \d+$/);

  //  برگشت به دری و لاتین ⇒ همان دیروز
  await pg.evaluate(() => goTo('settings'));
  await pg.click('#months-toggle [data-months-choice="fa"]');
  await pg.click('#digits-toggle [data-digits-choice="latn"]');
  await pg.click('#currency-toggle [data-currency-choice="word"]');
  const back = await pg.evaluate(() => ({ m: JALALI_MONTHS[0], d: document.documentElement.getAttribute('data-digits'), r: rcAfn(1250000) }));
  assert.deepEqual(back, { m: 'حمل', d: null, r: '1,250,000 افغانی' });
});

await step('هیچ خطای صفحه‌ای', async () => {
  assert.deepEqual(errors, []);
});

await browser.close();
server.close();
console.log(failed ? `\n${failed} سنجه قرمز` : '\nهمه سبز');
process.exit(failed ? 1 : 0);
