'use strict';
/**
 * PRM-13 — ماندهٔ امتیازِ وفاداری از دفترِ همگام‌شده، با انقضای FIFO. پیادهٔ JS ِ همان `Loyalty.state` ِ هستهٔ
 * برنامه (`core/promo/Loyalty.kt`)؛ همان بردارها در `test/loyalty-balance.test.js` و `LoyaltyExpiryTest.kt`.
 *
 * خالص و فقط عددِ صحیح: نه پایگاه، نه ساعت. رویداد = `{ id, kind, points, saleId, at }` با `kind` یکی از
 * `earn`، `spend`، `earn_back`، `spend_back`، `adjust` (اصلاحِ دستی با علامت). هیچ ردیفِ «سوخت» نوشته نمی‌شود.
 *   • افزوده‌ها اول کسری را صاف می‌کنند، بقیه دستهٔ تازه (`spend_back` از لحظهٔ همان مصرف می‌شمارد).
 *   • کم‌شده‌ها از کهنه‌ترین دستهٔ زنده؛ `earn_back` اول از دستهٔ همان فاکتور؛ نبودِ دسته ⇒ کسری.
 *   • ماه = ۳۶۵ روز ÷ ۱۲؛ در همان لحظهٔ `expiresAt` سوخته است؛ هیچ دسته‌ای پس از `now` نمی‌سوزد.
 */

const MONTH_MS = 2628000000;
const MAX_EXPIRY_MONTHS = 120;
const MAX_POINTS = 1000000000;
const SOON_MS = 30 * 24 * 60 * 60 * 1000;
const INF = Number.MAX_SAFE_INTEGER;

const TYPES = Object.freeze({
  loyalty_earn: 'earn', loyalty_spend: 'spend', loyalty_earn_back: 'earn_back', loyalty_spend_back: 'spend_back', loyalty_adjust: 'adjust',
});

const clampPoints = (n) => Math.max(-MAX_POINTS, Math.min(MAX_POINTS, n));

/** امتیازِ علامت‌دارِ یک رویداد (همان `Loyalty.signed`) */
function signed(e) {
  const p = Number.isSafeInteger(e.points) ? e.points : 0;
  switch (e.kind) {
    case 'earn': case 'spend_back': return Math.max(0, p);
    case 'spend': case 'earn_back': return -Math.max(0, p);
    case 'adjust': return clampPoints(p);
    default: return 0;
  }
}

/** حالِ امتیاز در `now`: `{balance, expired, soon, soonAt}` */
function state(events, rule, now, soonMs = SOON_MS) {
  const m = Number.isSafeInteger(rule?.expiryMonths) ? rule.expiryMonths : 0;
  const months = Math.max(0, Math.min(MAX_EXPIRY_MONTHS, m));
  const life = months * MONTH_MS;
  const mine = [...events].sort((a, b) => (a.at - b.at) || (a.id < b.id ? -1 : a.id > b.id ? 1 : 0));
  const spentAt = new Map();
  for (const e of mine) {
    if (e.kind === 'spend' && e.saleId) spentAt.set(e.saleId, Math.min(spentAt.has(e.saleId) ? spentAt.get(e.saleId) : e.at, e.at));
  }
  let lots = [];
  let debt = 0;
  let expired = 0;
  let seq = 0;
  const burn = (t) => {
    if (months === 0) return;
    lots = lots.filter((l) => { if (l.expiresAt <= t) { expired += l.left; return false; } return true; });
  };
  for (const e of mine) {
    const t = Math.min(e.at, now);
    burn(t);
    const v = signed(e);
    if (v > 0) {
      const pay = Math.min(debt, v);
      debt -= pay;
      const rest = v - pay;
      if (rest <= 0) continue;
      const born = e.kind === 'spend_back' && spentAt.has(e.saleId) ? spentAt.get(e.saleId) : e.at;
      const ends = months === 0 ? INF : born + life;
      if (months > 0 && ends <= t) { expired += rest; continue; }
      lots.push({ at: born, saleId: e.saleId, earn: e.kind === 'earn', left: rest, expiresAt: ends, seq: seq++ });
      lots.sort((a, b) => (a.at - b.at) || (a.seq - b.seq));
    } else if (v < 0) {
      let need = -v;
      const own = (l) => l.earn && l.saleId === e.saleId;
      const order = e.kind === 'earn_back' ? [...lots.filter(own), ...lots.filter((l) => !own(l))] : lots;
      for (const l of order) {
        if (need === 0) break;
        const take = Math.min(l.left, need);
        l.left -= take;
        need -= take;
      }
      lots = lots.filter((l) => l.left > 0);
      debt += need;
    }
  }
  burn(now);
  const until = now + soonMs;
  const soonLots = months === 0 ? [] : lots.filter((l) => l.expiresAt <= until);
  const sum = (xs) => xs.reduce((a, l) => a + l.left, 0);
  return {
    balance: sum(lots) - debt,
    expired,
    soon: sum(soonLots),
    soonAt: soonLots.length ? Math.min(...soonLots.map((l) => l.expiresAt)) : null,
  };
}

/** سطح از خریدِ ۱۲ ماهه (افغانی) — همان `Loyalty.tier`؛ برنزی ×۱، نقره‌ای ×۱٫۲۵، طلایی ×۱٫۵ */
function tier(rule, spendAfn) {
  if (spendAfn >= rule.goldAfn) return 'gold';
  if (spendAfn >= rule.silverAfn) return 'silver';
  return 'bronze';
}

module.exports = { MONTH_MS, MAX_EXPIRY_MONTHS, MAX_POINTS, SOON_MS, TYPES, signed, state, tier };
