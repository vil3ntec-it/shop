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
 *     via      راهی که **واقعاً** به کار رفت (کوچک‌حرف می‌شود): sms | webhook |
 *              whatsapp = «واقعی»؛ log = «آزمایشی»؛ هر چیزِ دیگر (email، provider ِ
 *              ناشناخته) دستهٔ خودش، «other» — نه آزمایشی، نه هزینه‌دار
 *     ok       ⛔ فقط `ok === true` یعنی provider پذیرفت؛ نداده یا هر چیزِ دیگر ⇒ ناموفق.
 *              از `lib/otp.js`: `ok: out?.delivered === true` (senders.* ⇒ { delivered, via })؛
 *              پرتابِ خطا از sender ⇒ `ok: false`
 *     parts    شمارِ تکه‌ها (پیش‌فرض ۱، سقف ۲۰)
 *     at       نداده/null ⇒ همین لحظه
 *   هزینه = بهای واحدِ همین لحظه × parts، فقط برای راهِ واقعی و ok. راهِ `log`
 *   (آزمایشی) هزینه ندارد ولی شمرده می‌شود تا «آزمایشی» پنهان نماند.
 *   ⛔ شماره، متن و کد نمی‌گیرد و نمی‌نویسد.
 *   ⚠️ خطای دیتابیس بالا می‌رود؛ صدازننده تصمیم می‌گیرد ورود به خاطرِ دفتر
 *      نشکند (`.catch`) یا سقف سخت بماند.
 *
 * ── خواندن ───────────────────────────────────────────────────────────
 *   countSince(sinceMs, { okOnly })   ⇒ شمارِ **تلاش‌ها** (ردیف‌ها، هر راهی، موفق یا نه) از
 *     sinceMs در همهٔ دکان‌ها؛ okOnly ⇒ فقط پذیرفته‌ها — سقفِ روزانهٔ SEC-06؛ نمایهٔ (at)
 *   costSince({ since, via, shopId }) ⇒ جمعِ cost_minor از since (via/shopId نداده ⇒ همه) — سقفِ
 *     هزینهٔ روزانهٔ SEC-06 برای هر کانال، کلِ سرور یا یک دکان
 *   totals({ since, until, shopId })  ⇒ { attempts, sent, failed, mock, other, parts, costMinor }
 *     (shopId نداده ⇒ همهٔ دکان‌ها و بی‌دکان — جمعِ روزانهٔ سراسریِ SEC-06)
 *   report({ days, limit })            ⇒ هر دکان جدا، برای پنل؛ `truncated` اگر بیش از سقف
 *   unitCost()                         ⇒ بهای هر تکه به واحدِ خُرد (`app_config`)
 *   setUnitCost(minor, actor)          ⇒ نوشتن + ردیفِ سابقه با همین `actor` (شناسهٔ مدیر یا
 *     { adminId, ip })؛ ⛔ مسیرِ HTTPِ آن باید پشتِ `requireSuperAdmin` باشد
 */
const { query, one, many, newId, now } = require('../db');
const { badRequest } = require('../middleware/errors');
const audit = require('./audit');

const UNIT_KEY = 'sms_unit_cost_minor';
/** پیامکِ پلتفرم را provider ِ داخلی به افغانی حساب می‌کند. */
const CURRENCY = 'AFN';
const REAL_VIA = Object.freeze(['sms', 'webhook', 'whatsapp']);
const MOCK_VIA = 'log';
const LIMITS = Object.freeze({ maxUnitMinor: 100_000_000, maxParts: 20, defDays: 30, maxDays: 366, maxShops: 500 });
const DAY = 24 * 60 * 60 * 1000;

const cut = (v, n) => String(v ?? '').slice(0, n);
const intOr = (v, def) => (v != null && v !== '' && Number.isSafeInteger(Number(v)) ? Number(v) : def);
const viaOf = (v) => String(v ?? '').trim().toLowerCase().slice(0, 20);

/** بهای هر تکهٔ پیامک به واحدِ خُرد؛ تنظیم‌نشده ⇒ ۰. */
async function unitCost() {
  const r = await one('SELECT value FROM app_config WHERE key=$1', [UNIT_KEY]);
  const n = Number(r?.value);
  return Number.isSafeInteger(n) && n >= 0 ? n : 0;
}

/**
 * فقط عددِ صحیحِ نامنفی و محدود؛ چیزِ دیگر ۴۰۰ و هیچ نوشتنی نیست. سابقه را
 * خودش می‌نویسد (پیش و پس). ⛔ صدازنندهٔ HTTP فقط پشتِ `requireSuperAdmin`.
 */
async function setUnitCost(raw, actor) {
  const adminId = typeof actor === 'string' ? actor : String(actor?.adminId || '');
  if (!adminId) throw new TypeError('setUnitCost: actor (شناسهٔ مدیر) لازم است');
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
  await audit.log({
    actorType: 'admin', userId: adminId, ip: String(actor?.ip || ''), action: 'admin.sms_unit_cost_set',
    targetType: 'app_config', targetId: UNIT_KEY, detail: { before, after: n, currency: CURRENCY },
  });
  return { before, unitCostMinor: n, currency: CURRENCY };
}

async function record({ shopId = '', app = '', purpose = '', via = '', ok, parts = 1, at } = {}) {
  const v = viaOf(via);
  const p = Math.min(LIMITS.maxParts, Math.max(1, intOr(parts, 1)));
  const good = ok === true;
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
       COUNT(*) FILTER (WHERE P.ok AND P.via = ANY($VIA::text[]))::int AS sent,
       COUNT(*) FILTER (WHERE NOT P.ok)::int AS failed,
       COUNT(*) FILTER (WHERE P.ok AND P.via = '${MOCK_VIA}')::int AS mock,
       COUNT(*) FILTER (WHERE P.ok AND P.via <> '${MOCK_VIA}' AND NOT (P.via = ANY($VIA::text[])))::int AS other,
       COALESCE(SUM(P.parts) FILTER (WHERE P.ok AND P.via = ANY($VIA::text[])), 0)::bigint AS parts,
       COALESCE(SUM(P.cost_minor), 0)::bigint AS cost_minor`;
/** جمع‌ها با پارامترِ $n برای فهرستِ راه‌های واقعی و پیشوندِ جدول (`u`). */
const sums = (n, alias) => SUMS.replace(/\$VIA/g, `$${n}`).replace(/\bP\./g, `${alias}.`);

const shape = (r) => ({
  attempts: Number(r?.attempts) || 0, sent: Number(r?.sent) || 0, failed: Number(r?.failed) || 0,
  mock: Number(r?.mock) || 0, other: Number(r?.other) || 0, parts: Number(r?.parts) || 0, costMinor: Number(r?.cost_minor) || 0,
});

/** شمارِ تلاش‌ها (ردیف‌ها، هر راه و هر نتیجه) از `sinceMs` در همهٔ دکان‌ها؛ `okOnly` ⇒ فقط پذیرفته‌ها. */
async function countSince(sinceMs, { okOnly = false } = {}) {
  const r = await one(
    `SELECT COUNT(*)::int AS n FROM sms_usage WHERE at >= $1${okOnly ? ' AND ok' : ''}`,
    [intOr(sinceMs, 0)]
  );
  return Number(r?.n) || 0;
}

/** جمعِ هزینه (واحدِ خُرد) از `since`؛ `via` و `shopId` اختیاری‌اند (نداده ⇒ همه). */
async function costSince({ since = 0, via, shopId } = {}) {
  const args = [intOr(since, 0)];
  let where = 'at >= $1';
  if (via !== undefined) { args.push(viaOf(via)); where += ` AND via = $${args.length}`; }
  if (shopId !== undefined) { args.push(cut(shopId, 80)); where += ` AND shop_id = $${args.length}`; }
  const r = await one(`SELECT COALESCE(SUM(cost_minor), 0)::bigint AS c FROM sms_usage WHERE ${where}`, args);
  return Number(r?.c) || 0;
}

/** جمعِ یک بازه؛ بی shopId ⇒ سراسری. */
async function totals({ since = 0, until = null, shopId } = {}) {
  const args = [intOr(since, 0), until == null ? Number.MAX_SAFE_INTEGER : intOr(until, Number.MAX_SAFE_INTEGER), [...REAL_VIA]];
  let where = 'u.at >= $1 AND u.at < $2';
  if (shopId !== undefined) { args.push(cut(shopId, 80)); where += ` AND u.shop_id = $${args.length}`; }
  return shape(await one(`SELECT ${sums(3, 'u')} FROM sms_usage u WHERE ${where}`, args));
}

/** گزارشِ پنل: هر دکان جدا (پرهزینه‌ترین بالا) + بی‌دکان + جمع. */
async function report({ days, limit } = {}) {
  const d = Math.min(LIMITS.maxDays, Math.max(1, intOr(days, LIMITS.defDays)));
  const lim = Math.min(LIMITS.maxShops, Math.max(1, intOr(limit, LIMITS.maxShops)));
  const t = now();
  const since = t - d * DAY;
  const [rows, total, unit] = await Promise.all([
    many(
      `SELECT u.shop_id, COALESCE(s.name, '') AS shop_name, ${sums(2, 'u')}, MAX(u.at) AS last_at
         FROM sms_usage u LEFT JOIN shops s ON s.id = u.shop_id
        WHERE u.at >= $1
        GROUP BY u.shop_id, s.name
        ORDER BY COALESCE(SUM(u.cost_minor), 0) DESC, COUNT(*) DESC, u.shop_id
        LIMIT ${lim + 1}`,
      [since, [...REAL_VIA]]
    ),
    totals({ since }),
    unitCost(),
  ]);
  return {
    days: d, since, checkedAt: t, currency: CURRENCY, unitCostMinor: unit,
    total,
    truncated: rows.length > lim,
    shops: rows.slice(0, lim).map((r) => ({
      shopId: r.shop_id, shopName: r.shop_name, ...shape(r), lastAt: Number(r.last_at) || null,
    })),
  };
}

module.exports = { UNIT_KEY, CURRENCY, REAL_VIA, MOCK_VIA, LIMITS, unitCost, setUnitCost, record, countSince, costSince, totals, report };
