'use strict';
/**
 * قاعدهٔ یکسانِ امتیازِ وفاداری برای همهٔ دستگاه‌های دکان — PRM-13 (`/api/shop/loyalty`، مهاجرتِ ۲۰۲).
 *
 * ⛔ پیش‌فرض خاموش: پرچمِ `shop.loyalty_rule` (`live_config`). خاموش ⇒ `GET` فقط `{enabled:false}` (برنامه قاعدهٔ
 *    همین دستگاه را نگه می‌دارد) و `PUT` ۴۰۳ `loyalty_rule_disabled`؛ هیچ ردیفی نوشته نمی‌شود.
 * ⛔ نوشتن فقط صاحب و مدیر (`settings.write`، روی سرور)؛ shop_id فقط از عضویتِ توکن.
 * ⛔ هر عدد **صحیح** و در همان بازهٔ `Loyalty.Rule.valid` ِ هستهٔ برنامه (رشته، اعشار و بولی رد ⇒ ۴۰۰ `bad_rule`)؛
 *    `expectedVersion` ِ اختیاری ⇒ دو نوشتنِ هم‌زمان: دومی ۴۰۹ `version_conflict`.
 * • «حسابِ من» ِ ویترین (CRM-13) امتیاز و سطح را فقط با همین قاعدهٔ روشن نشان می‌دهد (`store-account-history.js`):
 *   قاعدهٔ فقط‌دستگاهی روی سرور نیست، پس سطح و انقضا را سرور نمی‌داند.
 */
const orders = require('./orders');
const { one, now } = require('../db');
const { badRequest, conflict } = require('../middleware/errors');
const { MAX_EXPIRY_MONTHS } = require('./loyalty-balance');

const FLAG = 'shop.loyalty_rule';
const T = 1000000000000;

const flagOn = (shopId) => orders.flagOf(shopId, FLAG);

const FIELDS = Object.freeze([
  ['everyAfn', 1, 1000000], ['pointAfn', 1, 1000000], ['maxSharePct', 1, 100],
  ['silverAfn', 1, T], ['goldAfn', 1, T], ['expiryMonths', 0, MAX_EXPIRY_MONTHS],
]);

/** قاعدهٔ درست یا ۴۰۰ — همان `Loyalty.Rule.valid` (طلایی بیش از نقره‌ای) */
function ruleOf(x) {
  if (!x || typeof x !== 'object' || Array.isArray(x)) throw badRequest('قاعده لازم است', 'bad_rule');
  const out = {};
  for (const [k, min, max] of FIELDS) {
    const v = x[k] === undefined && k === 'expiryMonths' ? 0 : x[k];
    if (!Number.isSafeInteger(v) || v < min || v > max) throw badRequest(`عددِ «${k}» درست نیست`, 'bad_rule');
    out[k] = v;
  }
  if (out.goldAfn <= out.silverAfn) throw badRequest('سطحِ طلایی باید بیش از نقره‌ای باشد', 'bad_rule');
  return out;
}

function view(row) {
  return {
    on: row.enabled === true,
    rule: {
      everyAfn: Number(row.every_afn), pointAfn: Number(row.point_afn), maxSharePct: Number(row.max_share_pct),
      silverAfn: Number(row.silver_afn), goldAfn: Number(row.gold_afn), expiryMonths: Number(row.expiry_months),
    },
    version: Number(row.version),
    updatedAt: Number(row.updated_at),
  };
}

/** قاعدهٔ ذخیره‌شدهٔ دکان یا `null` (هنوز ذخیره نشده) */
async function read(shopId) {
  const row = await one('SELECT * FROM shop_loyalty_rules WHERE shop_id=$1', [String(shopId)]);
  return row ? view(row) : null;
}

/** `{on, rule, expectedVersion?}` ⇒ ذخیره؛ نسخهٔ کهنه ⇒ ۴۰۹ */
async function save(shopId, body, userId = '') {
  const b = body && typeof body === 'object' && !Array.isArray(body) ? body : {};
  if (typeof b.on !== 'boolean') throw badRequest('روشن/خاموش باید true یا false باشد', 'bad_on');
  const rule = ruleOf(b.rule);
  const prev = await read(shopId);
  const version = prev ? prev.version : 0;
  const stale = () => Object.assign(conflict('قاعده همین حالا جای دیگری عوض شد', 'version_conflict'), { data: { version } });
  if (b.expectedVersion !== undefined && b.expectedVersion !== null && Number(b.expectedVersion) !== version) throw stale();
  const row = await one(
    `INSERT INTO shop_loyalty_rules
       (shop_id, enabled, every_afn, point_afn, max_share_pct, silver_afn, gold_afn, expiry_months, version, updated_by, updated_at)
     VALUES ($1,$2,$3,$4,$5,$6,$7,$8,1,$9,$10)
     ON CONFLICT (shop_id) DO UPDATE SET enabled=EXCLUDED.enabled, every_afn=EXCLUDED.every_afn, point_afn=EXCLUDED.point_afn,
       max_share_pct=EXCLUDED.max_share_pct, silver_afn=EXCLUDED.silver_afn, gold_afn=EXCLUDED.gold_afn,
       expiry_months=EXCLUDED.expiry_months, version=shop_loyalty_rules.version + 1,
       updated_by=EXCLUDED.updated_by, updated_at=EXCLUDED.updated_at
     WHERE shop_loyalty_rules.version = $11
     RETURNING *`,
    [String(shopId), b.on, rule.everyAfn, rule.pointAfn, rule.maxSharePct, rule.silverAfn, rule.goldAfn, rule.expiryMonths,
      String(userId || ''), now(), version]
  );
  if (!row) throw stale();
  return view(row);
}

/** قاعدهٔ روشنِ دکان برای «حسابِ من» — پرچم خاموش، ذخیره‌نشده یا خاموش ⇒ `null` */
async function activeRule(shopId) {
  if (!(await flagOn(shopId))) return null;
  const r = await read(shopId);
  return r && r.on ? r.rule : null;
}

module.exports = { FLAG, flagOn, ruleOf, read, save, activeRule };
