'use strict';
/**
 * دفترِ مصرف و هزینهٔ پیامک — OPS-09 (پنلِ VILL3N) و SEC-06 (سقفِ هزینه).
 *
 * یک دفتر برای هر دو (`migrations/120_sms_usage.sql`). این فایل تنها نویسنده
 * و تنها خوانندهٔ `sms_usage` است.
 *
 * ── نوشتن (قرارداد با `lib/otp.js` و هر فرستندهٔ دیگر) ───────────────
 *   await record({ shopId, app, purpose, via, ok, parts, at })  ⇒ { id, costMinor }
 *     shopId   دکانی که پیامک به خاطرِ آن رفت؛ '' = بی دکان (ورود، ثبت‌نام …)
 *     app      'shop' | 'pump' | …  (اختیاری)
 *     purpose  همان `purpose`ِ کد (login، register، store_phone، vip_code …)
 *     via      راهی که **واقعاً** به کار رفت: sms | webhook | whatsapp | log
 *     ok       provider پذیرفت؟ false ⇒ هزینه ۰
 *     parts    شمارِ تکه‌ها (پیش‌فرض ۱)
 *   هزینه = بهای واحدِ همین لحظه × parts، فقط برای راهِ واقعی و ok. راهِ `log`
 *   (آزمایشی) هزینه ندارد ولی شمرده می‌شود تا «آزمایشی» پنهان نماند.
 *   ⛔ شماره، متن و کد نمی‌گیرد و نمی‌نویسد.
 *   ⚠️ خطای دیتابیس بالا می‌رود؛ صدازننده تصمیم می‌گیرد ورود به خاطرِ دفتر
 *      نشکند (`.catch`) یا سقف سخت بماند.
 *
 * ── خواندن ───────────────────────────────────────────────────────────
 *   countSince(sinceMs, { okOnly })   ⇒ شمارِ ردیف‌ها از sinceMs، همهٔ دکان‌ها (okOnly ⇒ فقط ok)
 *     — سقفِ روزانهٔ سراسریِ SEC-06؛ روی نمایهٔ (at)
 *   totals({ since, until, shopId })  ⇒ { attempts, sent, failed, mock, parts, costMinor }
 *     (shopId نداده ⇒ همهٔ دکان‌ها و بی‌دکان — جمعِ روزانهٔ سراسریِ SEC-06)
 *   report({ days })                   ⇒ هر دکان جدا، برای پنل
 *   unitCost() / setUnitCost(minor)    ⇒ بهای هر تکه به واحدِ خُرد (`app_config`)
 */
const { query, one, many, newId, now } = require('../db');
const { badRequest } = require('../middleware/errors');

const UNIT_KEY = 'sms_unit_cost_minor';
/** پیامکِ پلتفرم را provider ِ داخلی به افغانی حساب می‌کند. */
const CURRENCY = 'AFN';
const REAL_VIA = Object.freeze(['sms', 'webhook', 'whatsapp']);
const LIMITS = Object.freeze({ maxUnitMinor: 100_000_000, maxParts: 20, defDays: 30, maxDays: 366, maxShops: 500 });
const DAY = 24 * 60 * 60 * 1000;

const cut = (v, n) => String(v ?? '').slice(0, n);
const intOr = (v, def) => (Number.isSafeInteger(Number(v)) ? Number(v) : def);

/** بهای هر تکهٔ پیامک به واحدِ خُرد؛ تنظیم‌نشده ⇒ ۰. */
async function unitCost() {
  const r = await one('SELECT value FROM app_config WHERE key=$1', [UNIT_KEY]);
  const n = Number(r?.value);
  return Number.isSafeInteger(n) && n >= 0 ? n : 0;
}

/** فقط عددِ صحیحِ نامنفی و محدود؛ چیزِ دیگر ۴۰۰ و هیچ نوشتنی نیست. */
async function setUnitCost(raw) {
  const n = typeof raw === 'string' && /^\d+$/.test(raw.trim()) ? Number(raw.trim()) : raw;
  if (!Number.isSafeInteger(n) || n < 0 || n > LIMITS.maxUnitMinor) {
    throw badRequest('بهای پیامک باید عددِ صحیحِ نامنفی به واحدِ خُرد باشد', 'bad_sms_cost');
  }
  const before = await unitCost();
  await query(
    `INSERT INTO app_config (key, value, updated_at) VALUES ($1,$2,$3)
     ON CONFLICT (key) DO UPDATE SET value=excluded.value, updated_at=excluded.updated_at`,
    [UNIT_KEY, String(n), now()]
  );
  return { before, unitCostMinor: n, currency: CURRENCY };
}

async function record({ shopId = '', app = '', purpose = '', via = '', ok = true, parts = 1, at } = {}) {
  const v = cut(via, 20);
  const p = Math.min(LIMITS.maxParts, Math.max(1, intOr(parts, 1)));
  const good = ok !== false;
  const costMinor = good && REAL_VIA.includes(v) ? (await unitCost()) * p : 0;
  const id = newId('smsu');
  await query(
    `INSERT INTO sms_usage (id, shop_id, app, purpose, via, ok, parts, cost_minor, at)
     VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9)`,
    [id, cut(shopId, 80), cut(app, 20), cut(purpose, 40), v, good, p, costMinor, intOr(at, now())]
  );
  return { id, costMinor };
}

const SUMS = `COUNT(*)::int AS attempts,
       COUNT(*) FILTER (WHERE ok AND via = ANY($VIA::text[]))::int AS sent,
       COUNT(*) FILTER (WHERE NOT ok)::int AS failed,
       COUNT(*) FILTER (WHERE ok AND NOT (via = ANY($VIA::text[])))::int AS mock,
       COALESCE(SUM(parts) FILTER (WHERE ok AND via = ANY($VIA::text[])), 0)::int AS parts,
       COALESCE(SUM(cost_minor), 0)::bigint AS cost_minor`;
const sums = (n) => SUMS.replace(/\$VIA/g, `$${n}`);

const shape = (r) => ({
  attempts: Number(r?.attempts) || 0, sent: Number(r?.sent) || 0, failed: Number(r?.failed) || 0,
  mock: Number(r?.mock) || 0, parts: Number(r?.parts) || 0, costMinor: Number(r?.cost_minor) || 0,
});

/** شمارِ ردیف‌ها از `sinceMs` در همهٔ دکان‌ها؛ `okOnly` ⇒ فقط پذیرفته‌ها. */
async function countSince(sinceMs, { okOnly = false } = {}) {
  const r = await one(
    `SELECT COUNT(*)::int AS n FROM sms_usage WHERE at >= $1${okOnly ? ' AND ok' : ''}`,
    [intOr(sinceMs, 0)]
  );
  return Number(r?.n) || 0;
}

/** جمعِ یک بازه؛ بی shopId ⇒ سراسری. */
async function totals({ since = 0, until = null, shopId } = {}) {
  const args = [intOr(since, 0), until == null ? Number.MAX_SAFE_INTEGER : intOr(until, Number.MAX_SAFE_INTEGER), [...REAL_VIA]];
  let where = 'at >= $1 AND at < $2';
  if (shopId !== undefined) { args.push(cut(shopId, 80)); where += ` AND shop_id = $${args.length}`; }
  return shape(await one(`SELECT ${sums(3)} FROM sms_usage WHERE ${where}`, args));
}

/** گزارشِ پنل: هر دکان جدا (پرهزینه‌ترین بالا) + بی‌دکان + جمع. */
async function report({ days } = {}) {
  const d = Math.min(LIMITS.maxDays, Math.max(1, intOr(days, LIMITS.defDays)));
  const t = now();
  const since = t - d * DAY;
  const [rows, total, unit] = await Promise.all([
    many(
      `SELECT u.shop_id, COALESCE(s.name, '') AS shop_name, ${sums(2)}, MAX(u.at) AS last_at
         FROM sms_usage u LEFT JOIN shops s ON s.id = u.shop_id
        WHERE u.at >= $1
        GROUP BY u.shop_id, s.name
        ORDER BY COALESCE(SUM(u.cost_minor), 0) DESC, COUNT(*) DESC, u.shop_id
        LIMIT ${LIMITS.maxShops}`,
      [since, [...REAL_VIA]]
    ),
    totals({ since }),
    unitCost(),
  ]);
  return {
    days: d, since, checkedAt: t, currency: CURRENCY, unitCostMinor: unit,
    total,
    shops: rows.map((r) => ({
      shopId: r.shop_id, shopName: r.shop_name, ...shape(r), lastAt: Number(r.last_at) || null,
    })),
  };
}

module.exports = { UNIT_KEY, CURRENCY, REAL_VIA, LIMITS, unitCost, setUnitCost, record, countSince, totals, report };
