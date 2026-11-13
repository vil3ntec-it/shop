'use strict';
/**
 * گزارشِ شعبه‌های درونِ یک دکان و «جمعِ همه» (تصمیمِ ۱b ِ صاحب‌کار؛ RPT-18 ِ درونِ یک دکان؛ `docs/BRANCHES-fa.md`).
 *
 *   صاحب  ⇐ هر شعبه (فعال و بایگانی؛ دادهٔ بایگانی هم دادهٔ دکان است) جدا + `totals` = جمعِ عددهای پولیِ همان خلاصه‌ها.
 *   دیگران ⇐ فقط شعبه‌های فعالِ دسترسیِ خودش و **بی** `totals` (کارمندِ هر شعبه فقط شعبهٔ خودش). درِ آن
 *            `reports.view` (نقش + لایه‌ها) هم می‌خواهد.
 *   عدد    ⇐ برای هر شعبه همان خلاصهٔ گزارشِ خودکارِ صاحب (`owner-report.ledgerOf` + `summarize`، هستهٔ مشترکِ
 *            `report-core`)، فقط از دفترِ همگام‌شدهٔ **همان** شعبه: اصلی = حسابِ دکان با قالبِ امروزش (v1/v3)، شعبهٔ
 *            دیگر = بخشِ `<دکان>#<شعبه>` (همیشه v3). روزِ محلیِ دکان. نه ادغامِ کالا/مشتری میانِ شعبه‌ها.
 *   سقف    ⇐ بازه ≤ ۳۱ روز (همان `branches-report.rangeOf`)؛ شمارِ شعبه ≤ `branches.MAX_BRANCHES` + اصلی. فقط خواندنی.
 */
const rep = require('./owner-report');
const range = require('./branches-report');
const branches = require('./branches');

const SUM_KEYS = Object.freeze(['invoices', 'sales', 'discount', 'returnCount', 'returns', 'netSales', 'cashNet', 'otherNet']);

async function summaryOf(shopId, branchId, w, zone) {
  const isMain = branchId === branches.MAIN;
  const L = await rep.ledgerOf(branches.partitionOf(shopId, branchId), w, isMain ? null : 'v3');
  const s = rep.summarize(L.raw, w, zone, { partial: L.partial, lowStock: null });
  return {
    ...Object.fromEntries(SUM_KEYS.map((k) => [k, Number(s[k] || 0)])),
    payments: s.payments, topProducts: s.topProducts, partial: !!s.partial,
  };
}

/**
 * @param access `branches.accessOf` ِ همین عضو (پرچم پیش‌تر سنجیده شده)
 * @param only   شعبهٔ خواسته (سنجیده‌شده با دسترسی) یا null = همه
 */
async function reportFor(shopId, access, query = {}, only = null, at = Date.now()) {
  const r = range.rangeOf(query);
  const zone = await rep.timezoneOf(shopId);
  const w = range.windowOf(r, zone, at);
  const all = await branches.list(shopId);
  const seen = all.filter((b) => (access.all ? true : b.status === 'active' && access.ids.includes(b.id)))
    .filter((b) => only === null || b.id === only);
  const list = [];
  for (const b of seen) {
    list.push({ id: b.id, name: b.name, main: b.main, status: b.status, summary: await summaryOf(shopId, b.id, w, zone) });
  }
  const out = { enabled: true, from: w.from, to: w.to, timezone: zone, branches: list };
  if (access.all && only === null) {
    const totals = { branches: list.length, partial: list.some((x) => x.summary.partial) };
    for (const k of SUM_KEYS) totals[k] = list.reduce((sum, x) => sum + x.summary[k], 0);
    out.totals = totals;
  }
  return out;
}

module.exports = { SUM_KEYS, reportFor };
