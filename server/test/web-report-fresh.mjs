/**
 * نسخهٔ وب — «گزارشِ تازه» (RPT-01/02/03/06/09/11) و کارت‌های خانه (v3 §۱۹.۵ بند ۲)، با مرورگرِ واقعی.
 *
 *   • پیش‌فرض خاموش: داشبورد و صفحهٔ گزارش همان امروزند
 *   • هستهٔ گزارش و تابلو در مرورگر همان پاسخِ آزمون‌های مرجع را می‌دهند (report-v1 و report-board-v1)
 *   • کارتِ خانه = جمعِ فهرستی که با کلیک باز می‌شود؛ نشانِ وضعیت «متن + رنگ + نقطه»؛ سود «تقریبی»
 *   • مرتب‌سازی با سرستون، کارت/جدول (ترجیحِ همین دستگاه)، CSV، گوشی همیشه کارت، axe
 *
 * اجرا:  node test/web-report-fresh.mjs
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
const CLOCK = { offsetMinutes: 270, dayStartMinute: 0 };
const HOME = B.build(LEDGER, { date: DATE, clock: CLOCK });
const AF = new Intl.NumberFormat('en-US', { maximumFractionDigits: 0 });
const money = (minor) => AF.format(Math.round(minor / 100));

const browser = await pw.chromium.launch();
let failed = 0;
const step = async (name, fn) => {
  try { await fn(); console.log(`✅  ${name}`); } catch (e) { failed++; console.log(`❌  ${name}\n    ${e.message}`); }
};

async function open(width = 1366, fresh = null) {
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
  }, [LEDGER, fresh]);
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

await step('سود و حاشیه همیشه «تقریبی»؛ مرتب‌سازی با سرستون؛ کارت/جدول؛ CSV', async () => {
  const { pg, ctx, errors } = await open(1366, '1');
  await go(pg, 'reports');
  await pg.click('#rep-fresh button[data-rtab="profit"]');
  await pg.click('#rep-fresh button[data-span="all"]');
  const title = await pg.locator('#rep-fresh table.rf-table caption').textContent();
  assert.ok(title.includes('تقریبی'), title);
  assert.ok((await pg.locator('#rep-fresh .rf-note').textContent()).includes('سود تقریبی'));
  assert.ok((await pg.locator('#rep-fresh .rf-stats').textContent()).includes('سود ناخالص (تقریبی)'));
  const all = C.sales(LEDGER, { clock: CLOCK });
  assert.equal(await footCell(pg, 'سود ناخالص'), money(all.totals.grossProfit));

  //  LOC-02: «این سه‌ماهه» = ۱ میزان ۱۴۰۵ (۲۰۲۶-۰۹-۲۳) تا امروز؛ «سه‌ماههٔ پیش» = سرطان–سنبله، کامل
  assert.deepEqual(B.range('quarter', DATE), { from: '2026-09-23', to: DATE });
  assert.deepEqual(B.range('lastQuarter', DATE), { from: '2026-06-22', to: '2026-09-22' });
  await pg.click('#rep-fresh button[data-span="quarter"]');
  assert.deepEqual(await pressed(pg, 'data-span'), ['quarter']);
  const q = C.sales(LEDGER, { from: '2026-09-23', to: DATE, clock: CLOCK });
  assert.equal(await footCell(pg, 'سود ناخالص'), money(q.totals.grossProfit));
  await pg.click('#rep-fresh button[data-span="lastQuarter"]');
  assert.deepEqual(await pressed(pg, 'data-span'), ['lastQuarter']);
  assert.equal(C.sales(LEDGER, { from: '2026-06-22', to: '2026-09-22', clock: CLOCK }).totals.netSales, 0, 'دفترِ نمونه در سه‌ماههٔ پیش فروشی ندارد');
  assert.equal((await pg.locator('#rep-fresh tbody').textContent()).trim(), 'در این بازه چیزی نیست.', 'سه‌ماههٔ پیش: هیچ فروش');
  assert.equal(await footCell(pg, 'سود ناخالص'), money(0));
  await pg.click('#rep-fresh button[data-span="all"]');

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

if (axeSrc) {
  await step('دسترس‌پذیری (axe): خانه و هر زبانهٔ گزارش، روز و شب', async () => {
    const bad = [];
    for (const scheme of ['light', 'dark']) {
      const { pg, ctx } = await open(1366, '1');
      await pg.emulateMedia({ colorScheme: scheme });
      await pg.addScriptTag({ content: axeSrc });
      const check = async (label) => {
        await pg.waitForTimeout(700);
        const v = await pg.evaluate(async () => {
          const out = await window.axe.run(document.querySelector('.page.active'),
            { runOnly: ['color-contrast', 'button-name', 'label', 'aria-allowed-attr', 'aria-valid-attr-value', 'th-has-data-cells', 'nested-interactive'] });
          return out.violations.map(x => `${x.id}: ${x.nodes.slice(0, 2).map(n => n.target.join(' ')).join(' ; ')}`);
        });
        v.forEach(x => bad.push(`${scheme} ${label} — ${x}`));
      };
      await go(pg, 'dashboard'); await check('خانه');
      await go(pg, 'reports');
      for (const t of ['today', 'sales', 'profit', 'debts', 'fraud']) {
        await pg.click(`#rep-fresh button[data-rtab="${t}"]`);
        await check(t);
      }
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
