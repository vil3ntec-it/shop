/**
 * R10 (بازبینیِ دومِ دستهٔ ۴۴) — خانهٔ CSV ِ نسخهٔ وبِ دکان بی «فرمول» در اکسل، LibreOffice و Google Sheets.
 *
 * `csvCell` ِ `index.html` (همان `downloadCsv` ِ «گزارش‌ها»، «صورت‌حسابِ مشتری»، «انبارگردانی» و «گزارشِ تازه» ِ
 * `report-ui.js`) متنِ آغازشده با `=`/`+`/`-`/`@` را همان‌طور می‌نوشت ⇒ نامِ کالا یا مشتریِ «=HYPERLINK(…)» در اکسلِ
 * صاحب‌کار فرمولِ زنده می‌شد. `index.html` یخ‌زده است ⇒ بارکننده `shop-sync.js` و همین پرونده `csvCell` ِ سراسریِ صفحه
 * را با `cell` عوض می‌کند (`downloadCsv` نام را هنگامِ فراخوان از `window` می‌خواند ⇒ هر چهار خروجی، بی نسخهٔ دوم).
 *
 * ⛔ قاعده همان `cell` ِ `server/src/lib/finance-reports.js` (سرور و وب یک‌جور): null/undefined ⇒ خالی؛ **عدد** (typeof number)
 *    همان عدد (۵۰۰- در ستونِ مبلغ عدد می‌ماند)؛ هر متنِ آغازشده با `=` `+` `-` `@` تب یا CR ⇒ «'» پیشش (OWASP CSV Injection)،
 *    متنِ عددنما هم (همان سرور). نقل‌قول: `"` `,` CR LF ِ سرور + `;` ِ پیشینِ صفحه (اکسلِ جداکنندهٔ «;») — مقدارِ خانه یکی است.
 * آزمون: `server/test/web-csv-cell.test.js` (برابر با سرور) و `server/test/web-csv-export.mjs` (دانلودِ واقعی در کرومیوم).
 */
(function (root, factory) {
  'use strict';
  const api = factory(root);
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  if (typeof window !== 'undefined' && root === window) {
    root.TohidCsvCell = api;
    api.install();
  }
})(typeof window !== 'undefined' ? window : globalThis, function (root) {
  'use strict';

  const FORMULA = /^[=+\-@\t\r]/;
  const QUOTE = /[",;\r\n]/;

  /** یک خانهٔ CSV — همان `finance-reports.cell` */
  function cell(v) {
    if (v === null || v === undefined) return '';
    if (typeof v === 'number') return String(v);
    let s = String(v);
    if (FORMULA.test(s)) s = "'" + s;
    return QUOTE.test(s) ? '"' + s.replace(/"/g, '""') + '"' : s;
  }

  /** `csvCell` ِ صفحه ⇐ `cell`؛ صفحهٔ بی آن (صفحهٔ دیگر یا Node) ⇒ هیچ */
  function install() {
    if (typeof root.csvCell !== 'function') return false;
    if (root.csvCell !== cell) root.csvCell = cell;
    return root.csvCell === cell;
  }

  return { cell, install };
});
