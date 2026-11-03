'use strict';
/**
 * RPT-18 (§۶.۱۰، فاز ۶) — «گزارشِ همهٔ شعبه‌ها در یک جا» برای صاحبی که چند دکان (شعبه) دارد.
 *
 *   شعبه ── هر دکانی که کاربرِ توکن **صاحبِ فعالِ** آن است (`shop_members.role = 'owner'`، عضویت و دکانِ فعال؛ همان
 *           `shops.membershipsOf` ِ «انتخابِ دکان»). مدیر و کارمند هیچ عددی نمی‌بینند (`owner: false`).
 *   پرچم ── `shop.branches_report` (همان قاعدهٔ `orders.flagOf`، ⛔ پیش‌فرض خاموش): خاموش برای دکانِ همین نشست ⇒ فقط
 *           `{enabled:false}`؛ هر شعبه فقط وقتی پرچمِ **خودش** روشن است در گزارش می‌آید (بقیه فقط شمرده: `skipped`) ⇒
 *           تعلیقِ سرویسِ یک دکان (`shop_holds`) دادهٔ همان دکان را از گزارش بیرون می‌برد.
 *   عدد ─── برای هر شعبه **همان** خلاصهٔ گزارشِ خودکارِ صاحب (`owner-report.buildSummary`: فقط ردیف‌های همگام‌شدهٔ همان
 *           دکان و هستهٔ مشترکِ `report-core`)، با روزِ محلیِ **همان** دکان. هر شعبه جدا می‌آید؛ «جمعِ همه» فقط جمعِ
 *           عددهای پولیِ همان خلاصه‌هاست (هر دکان به افغانی، واحدِ کوچک) — نه ادغامِ کالا یا مشتری میانِ شعبه‌ها.
 *   سقف ─── بازه حداکثر [MAX_DAYS] روز و حداکثر [MAX_SHOPS] شعبه (بیشتر ⇒ `truncated: true`)؛ خواندنی است و هیچ ردیفی
 *           نمی‌نویسد.
 */
const { badRequest } = require('../middleware/errors');
const fw = require('./finance-window');
const rep = require('./owner-report');
const shops = require('./shops');
const { flagOf: orderFlagOf } = require('./orders');

const FLAG_KEY = 'shop.branches_report';
const MAX_DAYS = 31;
const MAX_SHOPS = 20;
const SUM_KEYS = Object.freeze(['invoices', 'sales', 'discount', 'returnCount', 'returns', 'netSales', 'cashNet', 'otherNet']);
const YMD = /^\d{4}-\d{2}-\d{2}$/;

const flagOf = (shopId) => orderFlagOf(shopId, FLAG_KEY);

function validYmd(s) {
  if (!YMD.test(s)) return false;
  return fw.ymdAdd(s, 0) === s;
}

/** بازهٔ درخواست ⇒ {from, to} یا null (یعنی «امروزِ هر دکان») */
function rangeOf(q = {}) {
  const from = String(q.from || '');
  const to = String(q.to || from);
  if (!from && !q.to) return null;
  if (!validYmd(from) || !validYmd(to)) throw badRequest('تاریخ به شکلِ YYYY-MM-DD', 'bad_date');
  if (to < from) throw badRequest('«تا» پیش از «از» است', 'bad_range');
  let days = 1;
  for (let d = from; d < to && days <= MAX_DAYS; d = fw.ymdAdd(d, 1)) days++;
  if (days > MAX_DAYS) throw badRequest(`بازه حداکثر ${MAX_DAYS} روز`, 'range_too_long');
  return { from, to };
}

function windowOf(range, zone, at) {
  const today = fw.localYmd(at, zone);
  const from = range ? range.from : today;
  const to = range ? range.to : today;
  return { frequency: 'range', from, to, fromAt: fw.dayStart(from, zone), until: fw.dayStart(to, zone, 1) };
}

/**
 * گزارشِ همهٔ شعبه‌های صاحب. ⇒ `{enabled:false}` | `{enabled:true, owner:false}` | گزارش.
 * @param member عضویتِ نشست (`req.member`: shop_id و role)
 */
async function reportFor(userId, member, query = {}, at = Date.now()) {
  if (!(await flagOf(member.shop_id))) return { enabled: false };
  if (member.role !== 'owner') return { enabled: true, owner: false };
  const range = rangeOf(query);
  const owned = (await shops.membershipsOf(userId)).filter((m) => m.role === 'owner');
  const out = [];
  let skipped = 0;
  for (const m of owned) {
    if (!(await flagOf(m.shop_id))) { skipped++; continue; }
    out.push(m);
  }
  const truncated = out.length > MAX_SHOPS;
  const list = [];
  for (const m of out.slice(0, MAX_SHOPS)) {
    const zone = await rep.timezoneOf(m.shop_id);
    const w = windowOf(range, zone, at);
    const s = await rep.buildSummary(m.shop_id, w, zone);
    list.push({
      shop_id: m.shop_id, name: m.shop_name, current: m.shop_id === member.shop_id, timezone: zone,
      from: w.from, to: w.to,
      summary: {
        ...Object.fromEntries(SUM_KEYS.map((k) => [k, Number(s[k] || 0)])),
        payments: s.payments, topProducts: s.topProducts, lowStock: s.lowStock, partial: !!s.partial,
      },
    });
  }
  //  شعبهٔ همین نشست اول، بقیه به نام
  list.sort((a, b) => (Number(b.current) - Number(a.current)) || String(a.name).localeCompare(String(b.name), 'fa'));
  const totals = { shops: list.length, partial: list.some((x) => x.summary.partial) };
  for (const k of SUM_KEYS) totals[k] = list.reduce((sum, x) => sum + x.summary[k], 0);
  return {
    enabled: true, owner: true, from: range ? range.from : null, to: range ? range.to : null,
    shops: list, totals, skipped, truncated,
  };
}

module.exports = { FLAG_KEY, MAX_DAYS, MAX_SHOPS, flagOf, rangeOf, windowOf, reportFor };
