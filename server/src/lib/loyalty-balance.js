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

// ── مصرف (STF-03، `lib/promo-loyalty-review.js`): همتای دقیقِ `Loyalty.Rule.valid`، `cap`، `valueMinor` و `spend` ──
//  بردارِ مشترک: `core/test-vectors/loyalty-spend-v1.json` (`test/loyalty-spend.test.js` و `LoyaltySpendVectorsTest.kt`).
//  همه BigInt: `points × pointAfn × 100` تا ۱۰^۱۷ می‌رسد (Long ِ Kotlin)؛ بیرون از ±(2^53−1) ⇒ null (ناامن، بسته‌به‌خطا).

const MINOR = 100n;
const MAX_SAFE = BigInt(Number.MAX_SAFE_INTEGER);
const isInt = (v) => Number.isSafeInteger(v);
const inRange = (v, lo, hi) => isInt(v) && v >= lo && v <= hi;
const back = (b) => (b <= MAX_SAFE && b >= -MAX_SAFE ? Number(b) : null);

/** همان `Loyalty.Rule.valid` (طلایی بیش از نقره‌ای؛ انقضا ۰ تا ۱۲۰ ماه) */
function validRule(r) {
  if (!r || typeof r !== 'object') return false;
  const exp = r.expiryMonths === undefined ? 0 : r.expiryMonths;
  return inRange(r.everyAfn, 1, 1000000) && inRange(r.pointAfn, 1, 1000000) && inRange(r.maxSharePct, 1, 100)
    && inRange(r.silverAfn, 1, 1000000000000) && inRange(r.goldAfn, r.silverAfn + 1, 1000000000000) && inRange(exp, 0, MAX_EXPIRY_MONTHS);
}

/** بیشترین امتیازِ قابلِ مصرف روی فاکتورِ [invoiceMinor] — همان `Loyalty.cap` */
function cap(rule, balance, invoiceMinor) {
  if (!validRule(rule) || !isInt(balance) || !isInt(invoiceMinor) || balance <= 0 || invoiceMinor <= 0) return 0;
  const share = (BigInt(invoiceMinor) * BigInt(rule.maxSharePct)) / 100n / (BigInt(rule.pointAfn) * MINOR);
  const out = [BigInt(balance), share, BigInt(MAX_POINTS)].reduce((a, b) => (b < a ? b : a));
  return Number(out);
}

/** ارزشِ [points] امتیاز به Minor — همان `Loyalty.valueMinor` (null = بیرون از بازهٔ امنِ JS) */
function valueMinor(rule, points) {
  if (!rule || !isInt(rule.pointAfn) || !isInt(points)) return null;
  const p = BigInt(Math.max(0, Math.min(MAX_POINTS, points)));
  return back(p * BigInt(rule.pointAfn) * MINOR);
}

const SPEND_CODE = Object.freeze({ BAD_RULE: 'BAD_RULE', BAD_POINTS: 'BAD_POINTS', OVER_BALANCE: 'OVER_BALANCE', OVER_SHARE: 'OVER_SHARE' });

/** سنجشِ مصرفِ [points] روی فاکتورِ [invoiceMinor] با ماندهٔ [balance] — همان `Loyalty.spend` ⇒ {ok, points, valueMinor} | {ok:false, code} */
function spend(rule, balance, invoiceMinor, points) {
  if (!validRule(rule)) return { ok: false, code: SPEND_CODE.BAD_RULE };
  if (!isInt(points) || points <= 0 || points > MAX_POINTS) return { ok: false, code: SPEND_CODE.BAD_POINTS };
  if (!isInt(balance) || points > balance) return { ok: false, code: SPEND_CODE.OVER_BALANCE };
  if (points > cap(rule, balance, invoiceMinor)) return { ok: false, code: SPEND_CODE.OVER_SHARE };
  return { ok: true, points, valueMinor: valueMinor(rule, points) };
}

module.exports = {
  MONTH_MS, MAX_EXPIRY_MONTHS, MAX_POINTS, SOON_MS, TYPES, SPEND_CODE, signed, state, tier, validRule, cap, valueMinor, spend,
};
