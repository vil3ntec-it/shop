/**
 * نسخهٔ وب — «گزارشِ تازه» (RPT-01/02/03/06/09/11) و کارت‌های خانه (v3 §۱۹.۵ بند ۲)، با مرورگرِ واقعی.
 *
 *   • پیش‌فرض خاموش: داشبورد و صفحهٔ گزارش همان امروزند
 *   • هستهٔ گزارش و تابلو در مرورگر همان پاسخِ آزمون‌های مرجع را می‌دهند (report-v1 و report-board-v1)
 *   • کارتِ خانه = جمعِ فهرستی که با کلیک باز می‌شود؛ نشانِ وضعیت «متن + رنگ + نقطه»
 *   • RPT-03: سود با روشِ مصوب (میانگینِ موزون، `costApproved`)؛ «قیمت خرید روی قلم» انتخابِ همین مرورگر و همیشه «تقریبی»
 *   • RPT-11: اکسل = ‎.xlsx‎ ِ واقعی (عدد عدد)، PDF ِ مستقیم (A4، صفحه‌بندی، سقفِ ۵۰۰ صفحه)
 *   • مرتب‌سازی با سرستون، کارت/جدول (ترجیحِ همین دستگاه)، CSV، گوشی همیشه کارت، axe
 *   • INV-16: زبانهٔ «ارزش موجودی» (`stock-value-core.js`): همان عددِ هسته، کم‌بودها زیرِ جدول، فقط برای کسی که پول را می‌بیند
 *   • RPT-13 (§۵.۲): Ctrl+P / Ctrl+E، − / + ِ دوره، Alt+C ِ مقایسه (چیدمانِ فارسی هم)، Enter ِ ریزِ ردیف و Esc — فقط از keymap.js،
 *     نه در کادرِ نوشتن و نه در صفحهٔ دیگر؛ عددِ دوره و ریز همان هسته (`report-period-core.js`، `invoicesOf`/`dayInvoices`)
 *   • RPT-08: زبانهٔ «ساعت و روز» (`sales-heatmap-core.js`): جدولِ ۷ × ۲۴ با عددِ هر خانه، سرخطِ ردیف و ستون، همان هسته
 *
 * اجرا:  node test/web-report-fresh.mjs
 */
import { createRequire } from 'node:module';
import assert from 'node:assert/strict';
import { execSync } from 'node:child_process';
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { animationsSettled } from './browser-guard.mjs';

const require = createRequire(import.meta.url);
const globalRoot = (() => { try { return execSync('npm root -g', { encoding: 'utf8' }).trim(); } catch { return ''; } })();
const pick = (name) => { for (const p of [`${globalRoot}/${name}`, name]) { try { return require(p); } catch { /* بعدی */ } } return null; };
const pw = pick('playwright');
if (!pw) { console.log('⏭  پلی‌رایت پیدا نشد — این سنجه رد شد'); process.exit(0); }
let axeSrc = null;
for (const p of [`${globalRoot}/axe-core/axe.min.js`, process.env.AXE_PATH || '']) {
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

const VECTORS = JSON.parse(fs.readFileSync(path.join(ROOT, 'core/test-vectors/report-v1.json'), 'utf8'));
const BOARD = JSON.parse(fs.readFileSync(path.join(ROOT, 'core/test-vectors/report-board-v1.json'), 'utf8'));
const LEDGER = VECTORS.ledgers.kabul;
const DATE = '2026-10-14';
//  همان چیزی که صفحه با ساعتِ کابل می‌سازد — مستقل از مرورگر
const B = require(path.join(ROOT, 'license/report-board.js'));
const C = require(path.join(ROOT, 'license/report-core.js'));
const SV = require(path.join(ROOT, 'license/stock-value-core.js'));
const H = require(path.join(ROOT, 'license/sales-heatmap-core.js'));
const CLOCK = { offsetMinutes: 270, dayStartMinute: 0 };
//  RPT-03: روشِ پیش‌فرضِ صفحه همان روشِ مصوب (D-23، میانگینِ موزون) با تأییدِ هسته
const MAVG = { costing: 'mavg', costApproved: true };
const HOME = B.build(LEDGER, { date: DATE, clock: CLOCK, ...MAVG });
//  همان دفتر بی فاکتورِ قلمِ بی‌کالا و بی فاکتورِ بی‌قلم ⇒ هیچ قلمِ بی‌بها: سودِ میانگینِ موزون «قطعی» است
const FINAL = (() => {
  const L = JSON.parse(JSON.stringify(LEDGER));
  const has = new Set(L.saleItems.map((i) => i.saleId));
  const drop = new Set([...L.saleItems.filter((i) => !i.productId).map((i) => i.saleId), ...L.sales.filter((x) => !has.has(x.id)).map((x) => x.id)]);
  L.sales = L.sales.filter((x) => !drop.has(x.id)); L.saleItems = L.saleItems.filter((i) => !drop.has(i.saleId)); L.returns = L.returns.filter((r) => !drop.has(r.saleId));
  return L;
})();
const P = require(path.join(ROOT, 'license/report-pdf.js'));
/** پرونده‌های یک ZIP (‎.xlsx‎) ⇐ {نام: متن}؛ فقط stored و deflate */
function unzip(buf) {
  const zlib = require('node:zlib'), out = {};
  let at = 0;
  while (buf.readUInt32LE(at) === 0x04034b50) {
    const method = buf.readUInt16LE(at + 8), size = buf.readUInt32LE(at + 18), nl = buf.readUInt16LE(at + 26), xl = buf.readUInt16LE(at + 28);
    const name = buf.subarray(at + 30, at + 30 + nl).toString('utf8'), data = buf.subarray(at + 30 + nl + xl, at + 30 + nl + xl + size);
    out[name] = (method === 8 ? zlib.inflateRawSync(data) : data).toString('utf8');
    at += 30 + nl + xl + size;
  }
  return out;
}
/** نقطه‌های سیاهِ هر صفحهٔ PDF ِ تصویری (همان RunLengthDecode) */
function pdfPages(buf) {
  const t = buf.toString('latin1'), pages = [];
  for (const m of t.matchAll(/\/Width (\d+) \/Height (\d+) [^>]*\/Length (\d+) >>\nstream\n/g)) {
    const bits = P.runLengthDecode(buf.subarray(m.index + m[0].length, m.index + m[0].length + Number(m[3])));
    let black = 0;
    for (const b of bits) for (let k = b; k; k &= k - 1) black++;
    pages.push({ w: Number(m[1]), h: Number(m[2]), bytes: bits.length, black });
  }
  return pages;
}
const AF = new Intl.NumberFormat('en-US', { maximumFractionDigits: 0 });
const money = (minor) => AF.format(Math.round(minor / 100));

const browser = await pw.chromium.launch();
let failed = 0;
const step = async (name, fn) => {
  try { await fn(); console.log(`✅  ${name}`); } catch (e) { failed++; console.log(`❌  ${name}\n    ${e.message}`); }
};

async function open(width = 1366, fresh = null, ledger = LEDGER) {
  const ctx = await browser.newContext({ viewport: { width, height: 900 }, timezoneId: 'Asia/Kabul', locale: 'fa-AF' });
  const pg = await ctx.newPage();
  //  «امروز» = ۱۴ مهر ۲۰۲۶، ۱۰:۰۰ کابل — تاریخِ دفترِ آزمون‌های مرجع
  await pg.clock.setFixedTime(new Date('2026-10-14T05:30:00Z'));
  const errors = [];
  pg.on('pageerror', e => errors.push(String(e)));
  await pg.goto(url);
  await pg.evaluate(([d, f]) => {
    localStorage.setItem('tohid-shop-data-v1', JSON.stringify(d));
    if (f !== null) localStorage.setItem('tohid-report-fresh', f);
  }, [ledger, fresh]);
  await pg.reload();
  await pg.waitForTimeout(1200);
  const skip = pg.getByText('ادامه بدون حساب');
  if (await skip.first().isVisible().catch(() => false)) { await skip.first().click(); await pg.waitForTimeout(300); }
  return { pg, ctx, errors };
}
const go = (pg, p) => pg.evaluate(x => goTo(x), p);
const pressed = (pg, attr) => pg.evaluate(a => [...document.querySelectorAll(`#rep-fresh button[${a}][aria-pressed="true"]`)].map(b => b.getAttribute(a)), attr);
/** خانهٔ ردیفِ جمعِ نخستین جدول، با نامِ ستون */
const footCell = (pg, label) => pg.evaluate(l => {
  const t = document.querySelector('#rep-fresh table.rf-table');
  const i = [...t.querySelectorAll('thead th')].findIndex(th => th.textContent.trim().replace(/ [▲▼]$/, '') === l);
  return i < 0 ? null : t.querySelectorAll('tfoot td')[i].textContent.trim();
}, label);

await step('پیش‌فرض خاموش: داشبورد و گزارش همان امروزند', async () => {
  const { pg, ctx, errors } = await open();
  await go(pg, 'dashboard');
  assert.equal(await pg.locator('#dash-fresh').isHidden(), true);
  assert.equal(await pg.locator('#dash-fresh .rf-kpi').count(), 0);
  await go(pg, 'reports');
  assert.equal(await pg.locator('#rep-legacy').isVisible(), true);
  assert.equal(await pg.locator('#rep-fresh').isHidden(), true);
  assert.equal(await pg.locator('#rep-fresh-toggle').isChecked(), false);
  assert.equal(await pg.locator('#reports-tabs').isVisible(), true);
  assert.deepEqual(errors, []);
  await ctx.close();
});

await step('هستهٔ گزارش و تابلو در مرورگر همان پاسخِ آزمون‌های مرجع را می‌دهند', async () => {
  const { pg, ctx } = await open();
  const bad = await pg.evaluate(([v, b]) => {
    const C = window.TohidReportCore, B = window.TohidReportBoard, out = [];
    const same = (x, y) => JSON.stringify(x) === JSON.stringify(y);
    for (const c of v.cases) {
      const L = v.ledgers[c.ledger];
      let got;
      if (c.fn === 'tables') {
        const rep = c.of === 'sales' ? C.sales(L, c.opts) : c.of === 'today' ? C.today(L, c.opts) : c.of === 'debts' ? C.debts(L, c.opts) : C.fraud(L, c.opts);
        got = C.tables(c.of, rep).map(C.plain);
      } else got = C[c.fn](L, c.opts);
      if (!same(JSON.parse(JSON.stringify(got)), c.expect)) out.push(c.name);
    }
    for (const c of b.cases) {
      const got = c.fn === 'build' ? B.build(v.ledgers[c.ledger], c.opts) : c.fn === 'pct' ? c.input.map(B.pct)
        : c.fn === 'range' ? c.today.map(t => c.input.map(s => B.range(s, t))) : B.sortRows(c.rows, c.col, c.desc);
      if (!same(JSON.parse(JSON.stringify(got)), c.expect)) out.push(c.name);
    }
    return out;
  }, [VECTORS, BOARD]);
  assert.deepEqual(bad, []);
  await ctx.close();
});

await step('روشن کردن از صفحهٔ گزارش: گزارشِ تازه جای گزارشِ امروز، و ترجیحِ همین دستگاه می‌ماند', async () => {
  const { pg, ctx, errors } = await open();
  await go(pg, 'reports');
  await pg.locator('#rep-fresh-toggle').check();
  assert.equal(await pg.locator('#rep-fresh').isVisible(), true);
  assert.equal(await pg.locator('#rep-legacy').isHidden(), true);
  assert.equal(await pg.evaluate(() => localStorage.getItem('tohid-report-fresh')), '1');
  assert.deepEqual(await pressed(pg, 'data-rtab'), ['today']);
  assert.equal(await pg.locator('#rep-fresh .rf-kpi').count(), 5);
  await pg.reload(); await pg.waitForTimeout(900);
  await go(pg, 'reports');
  assert.equal(await pg.locator('#rep-fresh').isVisible(), true);
  await pg.locator('#rep-fresh-toggle').uncheck();
  assert.equal(await pg.locator('#rep-legacy').isVisible(), true);
  assert.equal(await pg.locator('#rep-fresh').isHidden(), true);
  assert.deepEqual(errors, []);
  await ctx.close();
});

await step('خانه: کارت‌ها از همان هسته، نشان‌های وضعیت، کلیک ⇒ فهرستِ فیلترشده با همان عدد', async () => {
  const { pg, ctx, errors } = await open(1366, '1');
  await go(pg, 'dashboard');
  await pg.waitForTimeout(200);
  assert.equal(await pg.locator('#dash-fresh').isVisible(), true);
  const cards = await pg.$$eval('#dash-fresh .rf-kpi', els => els.map(e => ({ id: e.dataset.kpi, v: e.querySelector('.v').textContent.trim(), t: e.querySelector('.t').textContent.trim() })));
  assert.deepEqual(cards.map(c => c.id), HOME.kpis.map(k => k.id));
  for (const k of HOME.kpis) {
    const c = cards.find(x => x.id === k.id);
    assert.equal(c.v, k.kind === 'money' ? `${money(k.value)} ؋` : String(k.value), k.id);
    assert.equal(c.t, k.title);
  }
  assert.ok(cards.find(c => c.id === 'grossProfit').t.includes('تقریبی'));
  const pills = await pg.$$eval('#dash-fresh .rf-pill', els => els.map(e => ({ id: e.dataset.pill, cls: e.className, text: e.textContent.trim(), dot: !!e.querySelector('.dot') })));
  assert.deepEqual(pills.map(p => p.id), HOME.pills.map(p => p.id));
  HOME.pills.forEach((p, i) => {
    assert.equal(pills[i].text, p.text);
    assert.ok(pills[i].cls.includes('rf-' + p.tone) && pills[i].dot, `نشانِ ${p.id} متن + رنگ + نقطه ندارد`);
  });

  //  کارتِ «فروش خالص امروز» ⇒ فروش، امروز، به تفکیکِ کالا؛ جمعِ جدول همان عددِ کارت
  await pg.click('#dash-fresh .rf-kpi[data-kpi="netSales"]');
  await pg.waitForTimeout(200);
  assert.equal(await pg.locator('#page-reports').evaluate(e => e.classList.contains('active')), true);
  assert.deepEqual(await pressed(pg, 'data-rtab'), ['sales']);
  assert.deepEqual(await pressed(pg, 'data-span'), ['today']);
  assert.deepEqual(await pressed(pg, 'data-dim'), ['product']);
  assert.equal(await footCell(pg, 'فروش خالص'), money(HOME.kpis.find(k => k.id === 'netSales').value));

  //  کارتِ «طلب از مشتریان» ⇒ سنِ بدهی؛ جمعِ مانده همان عدد
  await go(pg, 'dashboard');
  await pg.click('#dash-fresh .rf-kpi[data-kpi="receivable"]');
  await pg.waitForTimeout(200);
  assert.deepEqual(await pressed(pg, 'data-rtab'), ['debts']);
  assert.equal(await footCell(pg, 'مانده'), money(HOME.kpis.find(k => k.id === 'receivable').value));
  assert.equal(await pg.locator('#rep-fresh h3.rf-title').count(), 2);

  //  نشانِ ضدتقلب ⇒ ضدتقلبِ امروز
  await go(pg, 'dashboard');
  await pg.click('#dash-fresh .rf-pill[data-pill="fraud"]');
  await pg.waitForTimeout(200);
  assert.deepEqual(await pressed(pg, 'data-rtab'), ['fraud']);
  assert.deepEqual(await pressed(pg, 'data-span'), ['today']);
  const fr = C.fraud(LEDGER, { from: DATE, to: DATE, clock: CLOCK }).totals;
  assert.equal(await footCell(pg, 'لغو (بار)'), String(fr.voidCount));
  assert.deepEqual(errors, []);
  await ctx.close();
});

await step('RSK-10: هشدارِ رفتارِ غیرعادیِ صندوقدار — نشانِ خانه و فهرستِ زبانهٔ ضدتقلب، همان هستهٔ تابلو', async () => {
  //  همان دفتر + چهار «کشو بی فروش» ِ دیگرِ کریم در همان روز ⇒ ۵ در برابرِ ۰ ِ بقیه
  const L = JSON.parse(JSON.stringify(LEDGER));
  for (let i = 0; i < 4; i++) L.auditLog.push({ id: `rsk${i}`, type: 'no_sale', by: 'کریم', reason: 'change', at: 1791966600000 + i * 60000 });
  const want = B.alertsOf(C.fraud(C.ledger(L), { from: DATE, to: DATE, clock: CLOCK }));
  assert.equal(want.length, 1);
  assert.ok(!HOME.pills.some((p) => p.id === 'cashier'), 'دفترِ مرجع هشداری ندارد');
  const { pg, ctx, errors } = await open(1366, '1', L);
  await go(pg, 'dashboard');
  assert.equal(await pg.locator('#dash-fresh .rf-pill[data-pill="cashier"].rf-danger').count(), 1);
  await pg.click('#dash-fresh .rf-pill[data-pill="cashier"]');
  await pg.waitForTimeout(200);
  assert.deepEqual(await pressed(pg, 'data-rtab'), ['fraud']);
  const items = await pg.locator('#rep-fresh .rf-alerts li').allTextContents();
  assert.deepEqual(items, ['کریم: 5 کشو بی فروش در 1 فاکتور — بقیه 0 در 3']);
  //  دفترِ بی هشدار: نه نشان، نه کادر
  await ctx.close();
  const quiet = await open(1366, '1');
  await go(quiet.pg, 'reports');
  await quiet.pg.click('#rep-fresh button[data-rtab="fraud"]');
  assert.equal(await quiet.pg.locator('#rep-fresh .rf-alerts').count(), 0);
  assert.deepEqual([...errors, ...quiet.errors], []);
  await quiet.ctx.close();
});

await step('RPT-03: سود و حاشیه با روشِ مصوب (میانگینِ موزون)؛ مرتب‌سازی با سرستون؛ کارت/جدول؛ CSV', async () => {
  const { pg, ctx, errors } = await open(1366, '1');
  await go(pg, 'reports');
  await pg.click('#rep-fresh button[data-rtab="profit"]');
  await pg.click('#rep-fresh button[data-span="all"]');
  const all = C.sales(LEDGER, { clock: CLOCK, ...MAVG });
  assert.equal(all.totals.profitApproximate, true, 'دفترِ مرجع قلمِ بی‌بها دارد');
  assert.notEqual(all.totals.grossProfit, C.sales(LEDGER, { clock: CLOCK }).totals.grossProfit, 'دو روش در دفترِ مرجع یک عدد نمی‌دهند');
  assert.equal(await footCell(pg, 'سود ناخالص'), money(all.totals.grossProfit));
  assert.equal(await footCell(pg, 'بهای تمام‌شده'), money(all.totals.cogs));
  //  روشِ مصوب ⇒ برچسب بی «تقریبی»؛ قلمِ بی‌بها ⇒ نشانِ «سود تقریبی» و شمارش، با شرحِ روش
  const title = await pg.locator('#rep-fresh table.rf-table caption').textContent();
  assert.equal(title, 'سود و حاشیه به تفکیک کالا');
  const stats = await pg.locator('#rep-fresh .rf-stats .rf-stat .t').allTextContents();
  assert.deepEqual(stats, ['فروش خالص', 'بهای تمام‌شده', 'سود ناخالص', 'حاشیه']);
  const note = await pg.locator('#rep-fresh .rf-note').textContent();
  assert.ok(note.includes('سود تقریبی') && note.includes(B.COST.explain('mavg')) && note.includes(`${all.totals.costEstimatedLines} قلم بی بهای خرید است.`), note);
  assert.deepEqual(await pressed(pg, 'data-cost'), ['mavg']);

  //  مرتب‌سازی: «سود ناخالص» نزولی، بعد صعودی — با کیبورد هم (دکمهٔ سرستون)
  const col = await pg.evaluate(() => [...document.querySelectorAll('#rep-fresh thead th')].findIndex(th => th.textContent.trim() === 'سود ناخالص'));
  const sortBtn = `#rep-fresh thead th:nth-child(${col + 1}) button`;
  await pg.focus(sortBtn); await pg.keyboard.press('Enter');
  const rowsOf = () => pg.$$eval('#rep-fresh tbody tr', (trs, c) => trs.map(tr => Number(tr.children[c].textContent.replace(/,/g, ''))), col);
  const desc = await rowsOf();
  assert.deepEqual(desc, [...desc].sort((a, b) => b - a));
  assert.equal(await pg.locator(`#rep-fresh thead th:nth-child(${col + 1})`).getAttribute('aria-sort'), 'descending');
  assert.equal(await pg.evaluate(() => document.activeElement && document.activeElement.dataset.sort !== undefined), true);
  await pg.click(sortBtn);
  const asc = await rowsOf();
  assert.deepEqual(asc, [...asc].sort((a, b) => a - b));

  //  تفکیک‌ها: هر شش تا جدولِ خودش را دارد و جمعِ «فروش خالص» همه یکی است
  await pg.click('#rep-fresh button[data-rtab="sales"]');
  const totals = [];
  for (const d of ['product', 'category', 'user', 'hour', 'payment', 'channel']) {
    await pg.click(`#rep-fresh button[data-dim="${d}"]`);
    totals.push(await footCell(pg, 'فروش خالص'));
  }
  assert.equal(new Set(totals).size, 1, totals.join(' / '));
  assert.equal(totals[0], money(all.totals.netSales));

  //  نمای کارتی — ترجیحِ همین دستگاه، پس از بارگذاریِ دوباره هم
  await pg.click('#rep-fresh button[data-vm="card"]');
  assert.equal(await pg.locator('#rep-fresh .rf-cards .rf-card').count() > 0, true);
  assert.equal(await pg.locator('#rep-fresh table.rf-table').count(), 0);
  assert.equal(await pg.evaluate(() => localStorage.getItem('view_mode:local:report-fresh')), 'card');

  //  CSV: همان جدولِ ساده با سرستون‌ها
  const [dl] = await Promise.all([pg.waitForEvent('download'), pg.click('#rep-fresh button[data-act="csv"]')]);
  const csv = fs.readFileSync(await dl.path(), 'utf8');
  //  نامِ فایلِ فارسی در کرومِ بی‌سر «download» می‌شود (همان `downloadCsv`ِ گزارشِ امروز) — فقط محتوا سنجیده می‌شود
  assert.ok(csv.includes('فروش خالص') && csv.includes('جمع'), csv.slice(0, 200));

  await pg.reload(); await pg.waitForTimeout(900);
  await go(pg, 'reports');
  assert.equal(await pg.locator('#rep-fresh .rf-cards').count() > 0, true);
  assert.deepEqual(errors, []);
  await ctx.close();
});

await step('RPT-03: بی قلمِ بی‌بها ⇒ «سود قطعی»؛ «قیمت خرید روی قلم» انتخابِ همین مرورگر و همیشه «تقریبی»', async () => {
  const { pg, ctx, errors } = await open(1366, '1', FINAL);
  const want = { mavg: C.sales(FINAL, { clock: CLOCK, ...MAVG }).totals, line: C.sales(FINAL, { clock: CLOCK }).totals };
  assert.equal(want.mavg.profitApproximate, false);
  assert.equal(want.line.profitApproximate, true);
  //  کارتِ خانه با همان روش و همان تأیید
  await go(pg, 'dashboard');
  const homeOf = (o) => B.build(FINAL, { date: DATE, clock: CLOCK, ...o }).kpis.find((k) => k.id === 'grossProfit');
  const card = async () => pg.$eval('#dash-fresh .rf-kpi[data-kpi="grossProfit"]', (e) => ({ t: e.querySelector('.t').textContent.trim(), v: e.querySelector('.v').textContent.trim() }));
  assert.deepEqual(await card(), { t: homeOf(MAVG).title, v: `${money(homeOf(MAVG).value)} ؋` });
  assert.equal(homeOf(MAVG).approximate, false);
  await go(pg, 'reports');
  await pg.click('#rep-fresh button[data-rtab="profit"]');
  await pg.click('#rep-fresh button[data-span="all"]');
  assert.equal(await footCell(pg, 'سود ناخالص'), money(want.mavg.grossProfit));
  assert.equal(await pg.locator('#rep-fresh .rf-note .rf-pill.rf-success').textContent(), 'سود قطعی');
  assert.equal(await pg.locator('#rep-fresh .rf-note .rf-warning').count(), 0);

  //  «قیمت خرید روی قلم»: عددِ همان روش، برچسبِ «(تقریبی، قیمت خرید روی قلم)» و شرحِ روشِ مصوب؛ فوکوس روی همان دکمه
  await pg.focus('#rep-fresh button[data-cost="line"]'); await pg.keyboard.press('Enter');
  assert.equal(await pg.evaluate(() => localStorage.getItem('tohid-report-cost')), 'line');
  assert.equal(await pg.evaluate(() => document.activeElement && document.activeElement.dataset.cost), 'line');
  assert.equal(await footCell(pg, 'سود ناخالص'), money(want.line.grossProfit));
  assert.equal(await pg.locator('#rep-fresh table.rf-table caption').textContent(), 'سود و حاشیه به تفکیک کالا (تقریبی، قیمت خرید روی قلم)');
  assert.deepEqual(await pg.locator('#rep-fresh .rf-stats .rf-stat .t').allTextContents(),
    ['فروش خالص', 'بهای تمام‌شده (تقریبی، قیمت خرید روی قلم)', 'سود ناخالص (تقریبی، قیمت خرید روی قلم)', 'حاشیه (تقریبی، قیمت خرید روی قلم)']);
  const note = await pg.locator('#rep-fresh .rf-note').textContent();
  assert.ok(note.includes('سود تقریبی') && note.includes('«میانگین موزون»'), note);
  //  ترجیحِ همین مرورگر: پس از بارگذاری و در کارتِ خانه هم
  await pg.reload(); await pg.waitForTimeout(900);
  await go(pg, 'dashboard');
  assert.deepEqual(await card(), { t: homeOf({}).title, v: `${money(homeOf({}).value)} ؋` });
  assert.ok(homeOf({}).title.includes('تقریبی'));
  await go(pg, 'reports');
  await pg.click('#rep-fresh button[data-rtab="profit"]');
  assert.deepEqual(await pressed(pg, 'data-cost'), ['line']);
  await pg.click('#rep-fresh button[data-cost="mavg"]');
  assert.equal(await pg.evaluate(() => localStorage.getItem('tohid-report-cost')), 'mavg');
  assert.deepEqual(errors, []);
  await ctx.close();
});

await step('RPT-11: اکسل = ‎.xlsx‎ ِ واقعی (عدد عدد، راست‌به‌چپ) و PDF ِ مستقیم (A4، صفحه‌بندی، سقفِ صفحه)', async () => {
  const { pg, ctx, errors } = await open(1366, '1');
  await go(pg, 'reports');
  await pg.click('#rep-fresh button[data-rtab="profit"]');
  await pg.click('#rep-fresh button[data-span="all"]');
  const t = C.plain(C.tables('sales', C.sales(LEDGER, { clock: CLOCK, ...MAVG })).find((x) => x.id === 'sales.product'));
  const gp = t.head.indexOf('سود ناخالص');

  const [dx] = await Promise.all([pg.waitForEvent('download'), pg.click('#rep-fresh button[data-act="xlsx"]')]);
  const zip = fs.readFileSync(await dx.path());
  assert.equal(zip.subarray(0, 2).toString(), 'PK');
  const files = unzip(zip);
  const sheet = files['xl/worksheets/sheet1.xml'];
  assert.ok(sheet && files['xl/workbook.xml'] && files['[Content_Types].xml'], Object.keys(files).join(','));
  assert.ok(sheet.includes('rightToLeft="1"') && sheet.includes('state="frozen"'));
  assert.ok(sheet.includes('<t xml:space="preserve">سود ناخالص</t>') && sheet.includes('<t xml:space="preserve">جمع</t>'));
  //  جمعِ «سود ناخالص» عددِ اکسل است، نه متن
  assert.ok(sheet.includes(`<v>${t.total[gp]}</v>`), `${t.total[gp]} عدد نیست`);
  assert.ok(!sheet.includes(`>${t.total[gp]}</t>`));

  const [dp] = await Promise.all([pg.waitForEvent('download'), pg.click('#rep-fresh button[data-act="pdf"]')]);
  const pdf = fs.readFileSync(await dp.path());
  assert.equal(pdf.subarray(0, 9).toString(), '%PDF-1.4\n');
  const pages = pdfPages(pdf);
  assert.equal(pages.length, 1);
  assert.deepEqual([pages[0].w, pages[0].h, pages[0].bytes], [1240, 1754, 155 * 1754]);
  //  جدول و نوشته روی صفحه هست، صفحه سیاه نیست
  assert.ok(pages[0].black > 3000 && pages[0].black < 1240 * 1754 / 4, String(pages[0].black));
  assert.ok(pdf.toString('latin1').includes('/MediaBox [0 0 595.2 841.92]'));
  if (fs.existsSync('/usr/bin/pdfinfo')) {
    const f = path.join(path.dirname(await dp.path()), 'rpt11.pdf');
    fs.writeFileSync(f, pdf);
    const info = execSync(`pdfinfo "${f}"`, { encoding: 'utf8' });
    assert.ok(/Pages:\s+1\b/.test(info) && /595\.2 x 841\.92 pts \(A4\)/.test(info), info);
  }
  assert.equal(await pg.locator('#rep-fresh button[data-act="pdf"]').isDisabled(), false);

  //  صفحه‌بندی: ۲۰۰ ردیف ⇒ چند صفحه، هر کدام با سرستون؛ بیش از سقف ⇒ خطای روشن، نه PDF ِ ناقص
  const multi = await pg.evaluate(() => {
    const rows = [['کالا', 'فروش', 'سود']];
    for (let i = 0; i < 200; i++) rows.push(['کالای ' + i, String(i * 10), String(i)]);
    return window.TohidReportPdf.render(rows, { title: 'آزمون' }).map((p) => { let n = 0; for (const b of p.bits) for (let k = b; k; k &= k - 1) n++; return n; });
  });
  assert.ok(multi.length >= 3 && multi.every((n) => n > 2000), multi.join(','));
  const cap = await pg.evaluate(() => {
    const rows = [['کالا']];
    for (let i = 0; i < 40000; i++) rows.push(['x']);
    try { window.TohidReportPdf.render(rows, {}); return 'made'; } catch (e) { return `${e.code}:${e.max}`; }
  });
  assert.equal(cap, 'too_many_pages:500');

  assert.deepEqual(errors, []);
  await ctx.close();
});

//  INV-16 — دفترِ مرجع + کالای بی قیمتِ فروش، کالای فروخته‌شدهٔ بی ورود (منفی) و یک کیت (موجودیِ خودش را ندارد)
const STOCK_L = (() => {
  const L = JSON.parse(JSON.stringify(LEDGER));
  L.products.push(
    { id: 'p4', name: 'نمک', category: 'خوراکی', purchasePrice: 3, salePrice: 0 },
    { id: 'p5', name: 'بی ورود', category: 'خوراکی', purchasePrice: 2, salePrice: 10 },
    { id: 'p6', name: 'بستهٔ هدیه', category: 'هدیه', salePrice: 30, kit: [{ productId: 'p1', qty: 1 }] },
  );
  L.warehouseEntries.push({ id: 'w-p4', productId: 'p4', units: 2, price: 3, date: '2026-10-01', createdAt: 1790829060000 });
  L.sales.push({ id: 's-inv16', invoiceNumber: 99, total: 40, discount: 0, finalTotal: 40, paymentMethod: 'cash', paidAmount: 40, status: 'completed', seller: 'احمد', date: '2026-10-08', createdAt: 1791437400000 });
  L.saleItems.push(
    { id: 'i-p5', saleId: 's-inv16', productId: 'p5', quantity: 1, unitPrice: 10, purchasePrice: 2, totalPrice: 10, returnedQty: 0 },
    { id: 'i-p6', saleId: 's-inv16', productId: 'p6', quantity: 1, unitPrice: 30, purchasePrice: 0, totalPrice: 30, returnedQty: 0 },
  );
  return L;
})();

await step('INV-16: «ارزش موجودی» — همان عددِ هسته، کم‌بودها زیرِ جدول، CSV؛ صندوق‌دار نمی‌بیند', async () => {
  const want = SV.fromLedger(STOCK_L, C);
  const v = want.valuation;
  //  دفترِ مرجع با دست: چای ۱۴٫۵ به میانگینِ موزونِ ۱۱۵٫۷۹ (نه آخرین قیمتِ خریدِ ۱۳۰)، قند ۴۱ × ۴۰، صابون ۱۹ × ۲۵، نمک ۲ × ۳
  assert.equal(v.costMinor, 167895 + 164000 + 47500 + 600);
  assert.deepEqual([v.missingPrice, v.negative, v.inStock], [['p4'], ['p5'], 4]);
  const { pg, ctx, errors } = await open(1366, '1', STOCK_L);
  await pg.waitForFunction(() => !!window.TohidStockValue);
  await go(pg, 'reports');
  await pg.click('#rep-fresh button[data-rtab="stock-value"]');
  assert.deepEqual(await pressed(pg, 'data-rtab'), ['stock-value']);
  assert.equal(await pg.locator('#rep-fresh button[data-span]').count(), 0, 'بازهٔ زمانی ندارد');
  assert.equal(await pg.locator('#rep-fresh table.rf-table caption').textContent(), 'ارزش موجودی و سود بالقوه');
  assert.equal(await footCell(pg, 'ارزش موجودی'), money(v.costMinor));
  assert.equal(await footCell(pg, 'ارزش به قیمت فروش'), money(v.retailMinor));
  assert.equal(await footCell(pg, 'سود بالقوه'), money(v.potentialProfitMinor));
  const names = await pg.$$eval('#rep-fresh tbody tr', (trs) => trs.map((tr) => tr.children[0].textContent.trim()));
  assert.deepEqual(names, want.rows.map((r) => r.name));
  assert.ok(!names.includes('بستهٔ هدیه') && !names.includes('بی ورود'), 'کیت و منفی در جدول نیستند');
  //  بی قیمتِ فروش ⇒ سودِ همان ردیف «—»، نه صفرِ ساختگی
  const salt = await pg.$$eval('#rep-fresh tbody tr', (trs) => trs.find((tr) => tr.children[0].textContent.trim() === 'نمک').children[5].textContent.trim());
  assert.equal(salt, '—');
  const stats = await pg.locator('#rep-fresh .rf-stats').textContent();
  assert.ok(stats.includes('سود بالقوه') && stats.includes(money(v.potentialProfitMinor)), stats);
  const notes = await pg.locator('#rep-fresh ul.rf-note li').allTextContents();
  assert.deepEqual(notes, ['1 کالای موجود بی قیمت فروش', '1 کالا موجودیِ منفی دارد و در جمع نیست.']);
  assert.ok((await pg.locator('#rep-fresh p.rf-note').textContent()).includes('D-23'));
  const [dl] = await Promise.all([pg.waitForEvent('download'), pg.click('#rep-fresh button[data-act="csv"]')]);
  const csv = fs.readFileSync(await dl.path(), 'utf8');
  assert.ok(csv.includes('سود بالقوه') && csv.includes((v.costMinor / 100).toFixed(2)), csv.slice(0, 300));
  assert.deepEqual(errors, []);
  await ctx.close();

  //  صندوق‌دار (`money: false`): فقط «سنِ بدهی»، هیچ زبانهٔ ارزش
  const staff = await open(1366, '1', STOCK_L);
  await staff.pg.evaluate(() => localStorage.setItem('tohid-sync-state-v1', JSON.stringify({ rev: 0, lastSyncAt: 0, members: [], shop: { role: 'staff' } })));
  await staff.pg.reload(); await staff.pg.waitForTimeout(900);
  await staff.pg.waitForFunction(() => !!window.TohidStockValue);
  await go(staff.pg, 'reports');
  assert.equal(await staff.pg.evaluate(() => window.tohidReportEnv().money), false);
  assert.equal(await staff.pg.locator('#rep-fresh button[data-rtab="stock-value"]').count(), 0);
  assert.deepEqual(staff.errors, []);
  await staff.ctx.close();
});

await step('گوشی (۳۹۰): همیشه کارت، بی کلیدِ نما؛ خانه دو ستونه', async () => {
  const { pg, ctx, errors } = await open(390, '1');
  await go(pg, 'dashboard');
  assert.equal(await pg.locator('#dash-fresh .rf-kpi').count(), HOME.kpis.length);
  await go(pg, 'reports');
  await pg.click('#rep-fresh button[data-rtab="debts"]');
  assert.equal(await pg.locator('#rep-fresh button[data-vm]').count(), 0);
  assert.equal(await pg.locator('#rep-fresh .rf-cards').count(), 2);
  const overflow = await pg.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
  assert.ok(overflow <= 1, `اسکرولِ افقی: ${overflow}px`);
  assert.deepEqual(errors, []);
  await ctx.close();
});

/** گزارشِ هسته برای بازهٔ [from, to] با همان ساعت و روشِ صفحه */
const salesOf = (from, to) => C.sales(LEDGER, { from, to, clock: CLOCK, ...MAVG });
const periodText = (pg) => pg.locator('#rep-fresh .rf-period .rf-range').textContent();
const shiftOf = (pg) => pg.evaluate(() => window.TohidReportUi.state.shift);

await step('RPT-13: − / + دورهٔ قبل و بعد (کلید و دکمه)، بی دورهٔ آینده؛ کادرِ نوشتن و صفحهٔ دیگر کلید نمی‌گیرند', async () => {
  const { pg, ctx, errors } = await open(1366, '1');
  await go(pg, 'reports');
  await pg.waitForFunction(() => !!(window.TohidKeymap && window.TohidReportPeriod));
  await pg.click('#rep-fresh button[data-rtab="sales"]');
  await pg.click('#rep-fresh button[data-span="week"]');
  const now = salesOf('2026-10-08', DATE).totals, prev = salesOf('2026-10-01', '2026-10-07').totals;
  assert.equal(await footCell(pg, 'فروش خالص'), money(now.netSales));
  const label = async (f, t) => pg.evaluate(([a, b]) => `${formatDate(a)} تا ${formatDate(b)}`, [f, t]);
  assert.equal(await periodText(pg), await label('2026-10-08', DATE));
  //  دورهٔ جاری: «دورهٔ بعد» خاموش و + کاری نمی‌کند
  assert.equal(await pg.locator('#rep-fresh button[data-period="1"]').isDisabled(), true);
  await pg.keyboard.press('NumpadAdd');
  assert.equal(await shiftOf(pg), 0);
  //  − ⇒ همان ۷ روز پیش از آن، با همان عددِ هسته
  await pg.keyboard.press('Minus');
  assert.equal(await shiftOf(pg), -1);
  assert.equal(await periodText(pg), await label('2026-10-01', '2026-10-07'));
  assert.equal(await footCell(pg, 'فروش خالص'), money(prev.netSales));
  assert.equal(await pg.locator('#rep-fresh .rf-stats .rf-stat').first().locator('.v').textContent(), `${money(prev.netSales)} ؋`);
  assert.equal(await pg.locator('#rep-fresh-live').textContent(), await label('2026-10-01', '2026-10-07'), 'صفحه‌خوان بازهٔ تازه را می‌شنود');
  //  صفحهٔ عددی هم؛ بعد + (Shift و = ِ ردیفِ بالا) دو بار ⇒ دوباره جاری
  await pg.keyboard.press('NumpadSubtract');
  assert.equal(await shiftOf(pg), -2);
  await pg.keyboard.press('Shift+Equal');
  await pg.keyboard.press('Shift+Equal');
  assert.equal(await shiftOf(pg), 0);
  assert.equal(await footCell(pg, 'فروش خالص'), money(now.netSales));
  //  دکمه هم همان کار؛ فوکوس روی همان دکمه می‌ماند
  await pg.click('#rep-fresh button[data-period="-1"]');
  assert.equal(await shiftOf(pg), -1);
  assert.equal(await pg.evaluate(() => document.activeElement && document.activeElement.getAttribute('data-period')), '-1');
  //  «این ماه» ⇒ ماهِ میلادیِ کامل پیش از آن؛ عوض کردنِ بازه دوره را صفر می‌کند
  await pg.click('#rep-fresh button[data-span="month"]');
  assert.equal(await shiftOf(pg), 0);
  await pg.keyboard.press('Minus');
  assert.equal(await periodText(pg), await label('2026-09-01', '2026-09-30'));
  //  «همه» دوره ندارد: نه دکمه، نه کلید
  await pg.click('#rep-fresh button[data-span="all"]');
  assert.equal(await pg.locator('#rep-fresh .rf-period').count(), 0);
  await pg.keyboard.press('Minus');
  assert.equal(await shiftOf(pg), 0);
  //  کادرِ نوشتن: − نویسه است، نه دوره
  await pg.click('#rep-fresh button[data-span="week"]');
  await pg.evaluate(() => { const i = document.createElement('input'); i.id = 'rf-probe'; document.getElementById('page-reports').appendChild(i); });
  await pg.focus('#rf-probe');
  await pg.keyboard.press('Minus');
  assert.equal(await shiftOf(pg), 0);
  assert.equal(await pg.inputValue('#rf-probe'), '-');
  //  صفحهٔ دیگر: − مالِ آن صفحه است
  await go(pg, 'sale');
  await pg.evaluate(() => document.activeElement && document.activeElement.blur());
  await pg.keyboard.press('Minus');
  assert.equal(await shiftOf(pg), 0);
  assert.deepEqual(errors, []);
  await ctx.close();
});

await step('RPT-13: Alt+C «مقایسه با دورهٔ پیش» (چیدمانِ فارسی هم) — ستونِ «دورهٔ قبل» و «تغییر» و خطِ مقایسه با همان عددِ هسته', async () => {
  const { pg, ctx, errors } = await open(1366, '1');
  await go(pg, 'reports');
  await pg.waitForFunction(() => !!(window.TohidKeymap && window.TohidReportPeriod));
  await pg.click('#rep-fresh button[data-rtab="sales"]');
  await pg.click('#rep-fresh button[data-span="week"]');
  assert.equal(await pg.locator('#rep-fresh button[data-compare]').getAttribute('aria-pressed'), 'false');
  //  KeyC با حرفِ «ز» ِ چیدمانِ فارسی: کلید با کدِ فیزیکی خوانده می‌شود
  await pg.evaluate(() => document.body.dispatchEvent(new KeyboardEvent('keydown', { code: 'KeyC', key: 'ز', altKey: true, bubbles: true, cancelable: true })));
  assert.equal(await pg.locator('#rep-fresh button[data-compare]').getAttribute('aria-pressed'), 'true');
  const now = salesOf('2026-10-08', DATE), prev = salesOf('2026-10-01', '2026-10-07');
  assert.equal(await footCell(pg, 'دورهٔ قبل'), money(prev.totals.netSales));
  assert.equal(await footCell(pg, 'تغییر'), money(now.totals.netSales - prev.totals.netSales));
  //  ردیف‌به‌ردیف با کلیدِ ردیف؛ ردیفِ فقط-دورهٔ-قبل در انتها با خانه‌های خالیِ این دوره
  const rows = await pg.$$eval('#rep-fresh tbody tr', (trs) => trs.map((tr) => [...tr.children].map((td) => td.textContent.trim())));
  const head = await pg.$$eval('#rep-fresh thead th', (ths) => ths.map((th) => th.textContent.trim()));
  assert.deepEqual(head.slice(-2), ['دورهٔ قبل', 'تغییر']);
  const want = now.product.map((r) => {
    const p = prev.product.find((x) => x.key === r.key);
    return [p ? money(p.netSales) : '—', money(r.netSales - (p ? p.netSales : 0))];
  }).concat(prev.product.filter((p) => !now.product.some((r) => r.key === p.key)).map((p) => [money(p.netSales), money(-p.netSales)]));
  assert.deepEqual(rows.map((r) => r.slice(-2)), want);
  const line = await pg.locator('#rep-fresh .rf-compare').textContent();
  const pct = (a, b) => { const bp = Math.round(((a - b) * 10000) / Math.abs(b)); const t = Math.floor((Math.abs(bp) + 5) / 10); return (bp > 0 ? '+' : bp < 0 ? '-' : '') + Math.floor(t / 10) + (t % 10 ? `.${t % 10}` : '') + '٪'; };
  assert.ok(line.includes(`+${money(now.totals.netSales - prev.totals.netSales)} ؋`) && line.includes(pct(now.totals.netSales, prev.totals.netSales)), line);
  assert.ok(line.includes('فاکتورها') && line.includes(`+${now.totals.invoices - prev.totals.invoices}`), line);
  assert.equal((await pg.locator('#rep-fresh-live').textContent()).replace(/\s+/g, ' '), 'مقایسه با دورهٔ پیش: روشن');
  //  Playwright ِ Alt+C ِ لاتین هم؛ دوباره ⇒ خاموش
  await pg.keyboard.press('Alt+KeyC');
  assert.equal(await pg.locator('#rep-fresh button[data-compare]').getAttribute('aria-pressed'), 'false');
  assert.equal(await pg.locator('#rep-fresh thead th').filter({ hasText: 'دورهٔ قبل' }).count(), 0);
  //  «سود و حاشیه» ⇒ سودِ ناخالص مقایسه می‌شود؛ دورهٔ پیش از نخستین فروش ⇒ صفر، و تغییرِ «بی پایه»
  await pg.click('#rep-fresh button[data-compare]');
  await pg.click('#rep-fresh button[data-rtab="profit"]');
  assert.equal(await footCell(pg, 'دورهٔ قبل'), money(prev.totals.grossProfit));
  await pg.keyboard.press('Minus');
  assert.equal(await footCell(pg, 'دورهٔ قبل'), money(0));
  assert.ok((await pg.locator('#rep-fresh .rf-compare').textContent()).includes('بی پایه'));
  //  «ضدتقلب» مقایسه ندارد
  await pg.click('#rep-fresh button[data-rtab="fraud"]');
  assert.equal(await pg.locator('#rep-fresh button[data-compare]').count(), 0);
  assert.deepEqual(errors, []);
  await ctx.close();
});

await step('RPT-13: Ctrl+P چاپ و Ctrl+E اکسل ِ همان برگه (پیش‌فرضِ مرورگر گرفته می‌شود)', async () => {
  const { pg, ctx, errors } = await open(1366, '1');
  await go(pg, 'reports');
  await pg.waitForFunction(() => !!window.TohidKeymap);
  await pg.click('#rep-fresh button[data-rtab="sales"]');
  await pg.click('#rep-fresh button[data-span="all"]');
  await pg.evaluate(() => window.addEventListener('keydown', (e) => { window.__dp = e.defaultPrevented; }));
  const [popup] = await Promise.all([pg.waitForEvent('popup'), pg.keyboard.press('Control+KeyP')]);
  await popup.waitForLoadState();
  const printed = await popup.locator('table').textContent();
  assert.ok(printed.includes('فروش خالص') && printed.includes((salesOf('', '').totals.netSales / 100).toFixed(2)), printed.slice(0, 200));
  assert.equal(await pg.evaluate(() => window.__dp), true, 'Ctrl+P ِ مرورگر گرفته نشد');
  await popup.close();
  const [dl] = await Promise.all([pg.waitForEvent('download'), pg.keyboard.press('Control+KeyE')]);
  assert.equal(fs.readFileSync(await dl.path()).subarray(0, 2).toString(), 'PK', 'همان ‎.xlsx‎ ِ دکمهٔ «خروجی اکسل»');
  assert.equal(await pg.evaluate(() => window.__dp), true);
  //  برچسبِ کلید کنارِ نامِ دکمه، و aria-keyshortcuts
  assert.equal(await pg.locator('#rep-fresh button[data-act="print"] kbd').textContent(), 'Ctrl+P');
  assert.equal(await pg.locator('#rep-fresh button[data-act="xlsx"]').getAttribute('aria-keyshortcuts'), 'Control+E');
  assert.deepEqual(errors, []);
  await ctx.close();
});

await step('RPT-13: Enter روی ردیف ⇒ ریزِ فاکتورهای همان ردیف (جمع = همان ردیف)؛ Esc می‌بندد و فوکوس برمی‌گردد', async () => {
  const { pg, ctx, errors } = await open(1366, '1');
  await go(pg, 'reports');
  await pg.waitForFunction(() => !!window.TohidKeymap);
  await pg.click('#rep-fresh button[data-rtab="sales"]');
  await pg.click('#rep-fresh button[data-span="all"]');
  const all = salesOf('', '');
  //  مرتب‌سازی با «فروش خالص» نزولی: ریزِ ردیفِ نخستینِ دیده‌شده همان کالا است، نه ردیفِ نخستِ هسته
  const col = await pg.evaluate(() => [...document.querySelectorAll('#rep-fresh thead th')].findIndex((th) => th.textContent.trim() === 'فروش خالص'));
  await pg.click(`#rep-fresh thead th:nth-child(${col + 1}) button`);
  const top = [...all.product].sort((a, b) => b.netSales - a.netSales)[0];
  const first = pg.locator('#rep-fresh tbody tr').first().locator('button[data-drill]');
  assert.equal(await first.getAttribute('aria-label'), `ریزِ ${top.name || top.key}`);
  await first.focus();
  await pg.keyboard.press('Enter');
  assert.equal(await pg.locator('#rep-fresh .rf-drill').count(), 1);
  assert.equal(await pg.evaluate(() => document.activeElement && document.activeElement.id), 'rf-drill-h');
  const list = C.invoicesOf(LEDGER, { dim: 'product', key: top.key, clock: CLOCK });
  assert.equal(list.length, top.invoices);
  assert.equal(await pg.locator('#rf-drill-h').textContent(), `ریزِ «${top.name || top.key}» — ${top.invoices} فاکتور`);
  const drillFoot = await pg.$$eval('#rep-fresh .rf-drill tfoot td', (tds) => tds.map((td) => td.textContent.trim()));
  assert.equal(drillFoot.at(-1), money(top.sales), 'جمعِ ریز همان «فروش» ِ ردیف');
  const docs = await pg.$$eval('#rep-fresh .rf-drill tbody tr', (trs) => trs.map((tr) => tr.children[1].textContent.trim()));
  assert.deepEqual(docs, list.map((x) => String(LEDGER.sales.find((s) => s.id === x.saleId).invoiceNumber)));
  //  Esc ⇒ بسته، فوکوس روی همان ردیف
  await pg.keyboard.press('Escape');
  assert.equal(await pg.locator('#rep-fresh .rf-drill').count(), 0);
  assert.equal(await pg.evaluate(() => document.activeElement && document.activeElement.getAttribute('aria-label')), `ریزِ ${top.name || top.key}`);
  //  «امروز»: ریزِ روز همان «فروش» و «فاکتور» ِ همان ردیف
  await pg.click('#rep-fresh button[data-rtab="today"]');
  const t = C.today(LEDGER, { date: DATE, clock: CLOCK, ...MAVG }).today;
  await pg.click('#rep-fresh tbody tr:first-child button[data-drill]');
  assert.equal(await pg.locator('#rep-fresh .rf-drill tbody tr').count(), t.invoices);
  assert.equal((await pg.$$eval('#rep-fresh .rf-drill tfoot td', (tds) => tds.map((td) => td.textContent.trim()))).at(-1), money(t.sales));
  await pg.click('#rep-fresh button[data-act="drill-close"]');
  assert.equal(await pg.locator('#rep-fresh .rf-drill').count(), 0);
  //  «سنِ بدهی» ریز ندارد (هنوز)؛ نمای کارتی هم نه
  await pg.click('#rep-fresh button[data-rtab="debts"]');
  assert.equal(await pg.locator('#rep-fresh button[data-drill]').count(), 0);
  assert.deepEqual(errors, []);
  await ctx.close();
});

await step('RPT-08: «ساعت و روز» — جدولِ ۷ × ۲۴ با عددِ هر خانه و سرخطِ ردیف و ستون، همان هسته؛ پرفروش‌ترین ساعت؛ CSV؛ صندوق‌دار نمی‌بیند', async () => {
  const { pg, ctx, errors } = await open(1366, '1');
  await go(pg, 'reports');
  await pg.waitForFunction(() => !!window.TohidSalesHeatmap);
  await pg.click('#rep-fresh button[data-rtab="heatmap"]');
  await pg.click('#rep-fresh button[data-span="all"]');
  const h = H.build(H.events(C.ledger(LEDGER), '0000-01-01', '9999-12-31'), CLOCK.offsetMinutes, 0, 6);
  assert.equal(h.total.count, 5, 'پنج فاکتورِ لغونشدهٔ بالحظه (s7 لحظه ندارد، s5 لغو)');
  const grid = pg.locator('#rep-fresh table.rf-heat');
  assert.equal(await grid.locator('caption').textContent(), 'شمارِ فروش: روز × ساعت');
  assert.equal(await grid.locator('thead th[scope="col"]').count(), 26);
  assert.deepEqual(await grid.locator('tbody th[scope="row"]').allTextContents(), ['شنبه', 'یکشنبه', 'دوشنبه', 'سه‌شنبه', 'چهارشنبه', 'پنجشنبه', 'جمعه']);
  const cells = await grid.locator('tbody tr').evaluateAll((trs) => trs.map((tr) => [...tr.querySelectorAll('td')].map((td) => [td.textContent.trim(), td.className])));
  cells.forEach((line, r) => {
    line.slice(0, 24).forEach(([text, cls], hour) => {
      assert.equal(text, String(h.cells[r][hour].count), `${r}×${hour}`);
      assert.ok(cls.split(' ').includes(`rf-h${H.level(h, r, hour, 4)}`), `${r}×${hour}: ${cls}`);
    });
    assert.equal(line[24][0], String(h.rowTotals[r].count));
  });
  //  کابل: s1 چهارشنبه ۱۰:۰۰، s3 ِ ۰۲:۳۰ ِ چهارشنبه (۲۲:۰۰ UTC) — همان ساعتِ دیواری
  assert.equal(cells[4][2][0], '1', 'چهارشنبه ۰۲:۰۰');
  const peak = await pg.locator('#rep-fresh .rf-stats .rf-stat').first().textContent();
  assert.ok(peak.includes(`${H.dayName(h.weekdays[h.peak[0]])} ${H.hourLabel(h.peak[1])}`) && peak.includes(`${h.maxCount} فروش`), peak);
  assert.equal(await grid.locator('tfoot td').last().textContent(), String(h.total.count));
  const [, hours, days] = H.tables(h);
  assert.equal(await pg.locator('#rep-fresh table.rf-table').nth(1).locator('tfoot td').last().textContent(), money(hours.total[2]));
  assert.equal(await pg.locator('#rep-fresh table.rf-table').nth(2).locator('tfoot td').last().textContent(), money(days.total[2]));
  //  برگشتِ r1 (۱۰:۰۰ ِ چهارشنبه) در مبلغِ ساعتِ خودش کم شد و شمرده نشد
  assert.equal(hours.rows[10][1], 1);
  assert.equal(hours.rows[10][2], 30000 - 14000);
  //  − ⇒ ۷ روزِ پیش از این هفته هم نقشه دارد (همان دوره‌ها)
  await pg.click('#rep-fresh button[data-span="week"]');
  await pg.keyboard.press('Minus');
  const w = H.build(H.events(C.ledger(LEDGER), '2026-10-01', '2026-10-07'), CLOCK.offsetMinutes, 0, 6);
  assert.equal(await pg.locator('#rep-fresh table.rf-heat tfoot td').last().textContent(), String(w.total.count));
  const [dl] = await Promise.all([pg.waitForEvent('download'), pg.click('#rep-fresh button[data-act="csv"]')]);
  const csv = fs.readFileSync(await dl.path(), 'utf8');
  assert.ok(csv.includes('شمارِ فروش: روز × ساعت') && csv.includes('به تفکیکِ ساعت') && csv.includes('09:00'), csv.slice(0, 300));
  assert.deepEqual(errors, []);
  await ctx.close();
  const staff = await open(1366, '1');
  await staff.pg.evaluate(() => localStorage.setItem('tohid-sync-state-v1', JSON.stringify({ rev: 0, lastSyncAt: 0, members: [], shop: { role: 'staff' } })));
  await staff.pg.reload(); await staff.pg.waitForTimeout(900);
  await staff.pg.waitForFunction(() => !!window.TohidSalesHeatmap);
  await go(staff.pg, 'reports');
  assert.equal(await staff.pg.locator('#rep-fresh button[data-rtab="heatmap"]').count(), 0);
  assert.deepEqual(staff.errors, []);
  await staff.ctx.close();
});

if (axeSrc) {
  await step('دسترس‌پذیری (axe): خانه و هر زبانهٔ گزارش، روز و شب', async () => {
    const bad = [];
    for (const scheme of ['light', 'dark']) {
      const { pg, ctx } = await open(1366, '1');
      await pg.emulateMedia({ colorScheme: scheme });
      await pg.addScriptTag({ content: axeSrc });
      const check = async (label) => {
        await pg.waitForTimeout(700);
        await animationsSettled(pg, '.page.active');
        const v = await pg.evaluate(async () => {
          const out = await window.axe.run(document.querySelector('.page.active'),
            { runOnly: ['color-contrast', 'button-name', 'label', 'aria-allowed-attr', 'aria-valid-attr-value', 'th-has-data-cells', 'nested-interactive'] });
          return out.violations.map(x => `${x.id}: ${x.nodes.slice(0, 2).map(n => n.target.join(' ')).join(' ; ')}`);
        });
        v.forEach(x => bad.push(`${scheme} ${label} — ${x}`));
      };
      await go(pg, 'dashboard'); await check('خانه');
      await go(pg, 'reports');
      await pg.waitForFunction(() => !!(window.TohidStockValue && window.TohidSalesHeatmap && window.TohidKeymap));
      for (const t of ['today', 'sales', 'profit', 'debts', 'fraud', 'heatmap', 'stock-value']) {
        await pg.click(`#rep-fresh button[data-rtab="${t}"]`);
        await check(t);
      }
      await ctx.close();
    }
    assert.deepEqual(bad, [], bad.join('\n    '));
  });

  await step('دسترس‌پذیری (axe): RPT-13 دوره، مقایسه و ریز، و RPT-08 نقشه — روشن و تیرهٔ واقعیِ صفحه (data-theme)', async () => {
    const bad = [];
    for (const theme of ['light', 'dark']) {
      const { pg, ctx } = await open(1366, '1');
      await pg.evaluate((t) => localStorage.setItem('tohid-shop-settings-v1', JSON.stringify({ theme: t })), theme);
      await pg.reload(); await pg.waitForTimeout(900);
      await pg.addScriptTag({ content: axeSrc });
      const rules = ['color-contrast', 'button-name', 'label', 'aria-allowed-attr', 'aria-valid-attr-value', 'th-has-data-cells', 'td-headers-attr',
        'nested-interactive', 'scope-attr-valid', 'table-duplicate-name', 'empty-table-header', 'aria-allowed-role'];
      const check = async (label) => {
        await pg.waitForTimeout(500);
        await animationsSettled(pg, '.page.active');
        const v = await pg.evaluate(async (r) => {
          const out = await window.axe.run(document.querySelector('#rep-fresh'), { runOnly: r });
          return out.violations.map(x => `${x.id}: ${x.nodes.slice(0, 2).map(n => n.target.join(' ')).join(' ; ')}`);
        }, rules);
        v.forEach(x => bad.push(`${theme} ${label} — ${x}`));
      };
      assert.equal(await pg.evaluate(() => document.documentElement.getAttribute('data-theme') || 'light'), theme);
      await go(pg, 'reports');
      await pg.waitForFunction(() => !!(window.TohidSalesHeatmap && window.TohidKeymap && window.TohidReportPeriod));
      await pg.click('#rep-fresh button[data-rtab="sales"]');
      await pg.click('#rep-fresh button[data-span="week"]');
      await pg.keyboard.press('Minus');
      await pg.click('#rep-fresh button[data-compare]');
      await check('دوره و مقایسه');
      await pg.click('#rep-fresh button[data-span="all"]');
      await pg.click('#rep-fresh tbody tr:first-child button[data-drill]');
      await check('ریز');
      await pg.click('#rep-fresh button[data-rtab="heatmap"]');
      await check('نقشه');
      await ctx.close();
    }
    assert.deepEqual(bad, [], bad.join('\n    '));
  });
} else {
  console.log('⏭  axe-core پیدا نشد — سنجهٔ دسترس‌پذیریِ این صفحه رد شد');
}

await browser.close();
server.close();
console.log(failed ? `\n${failed} سنجه قرمز` : '\nهمه سبز');
process.exit(failed ? 1 : 0);
