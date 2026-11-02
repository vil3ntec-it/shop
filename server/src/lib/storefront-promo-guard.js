'use strict';
/**
 * تخفیفِ اولین خرید (MKT-02) و سدِ سوءاستفاده از تخفیفِ ویترین (RSK-09) — پشتِ پرچمِ خاموشِ
 * `shop.storefront.promo_guard` (و همراهِ `shop.storefront.promos`، چون از راهِ همان `storefront-promos.wrap` می‌آید).
 *
 *   اولین خرید ── تنظیمِ صاحب (`storefront_promo_guard`، مهاجرتِ ۱۹۲): درصد (`bp`) یا مبلغ، سقف، کفِ سبد،
 *                 «ترکیب» یا «انحصاری». فقط وقتی شمارهٔ تأییدشده هیچ سفارشِ پیشینی در این دکان ندارد و نه
 *                 این شماره و نه شناسهٔ دستگاه (اگر برنامهٔ خرید/وب فرستاد) پیش‌تر آن را نگرفته‌اند.
 *                 ⛔ درونِ تراکنشِ `orders.create` (پشتِ قفلِ دکان) و با قیدِ یکتای پایگاه روی (دکان، شماره) و
 *                 (دکان، دستگاه): دو ثبتِ هم‌زمان هر دو آن را نمی‌گیرند؛ درجِ ناکام ⇒ همان سفارش بی این تخفیف.
 *   ترکیب ─────── قاعدهٔ «انحصاری» ِ اعمال‌شده (`stack: 'exclusive'`) تنها می‌ماند و اولین خرید مصرف نمی‌شود؛
 *                 اولین خریدِ «انحصاری» با قاعده‌های دیگر روی هم نه: بی کد، بزرگ‌تر می‌ماند؛ با کدِ مشتری، همان کد.
 *   سقف‌ها ────── هر کد برای هر شماره و هر دستگاه (`codePerPhone`/`codePerDevice`، ۰ = بی سقف) ⇒ ۴۰۹
 *                 `promo_code_rejected` با `limit_reached`؛ سقفِ تخفیفِ هر سفارش (`maxDiscountMinor`)؛ سقفِ
 *                 دفترِ PRM-02 (`maxUses`، `shop_promo_redemptions`) همان پیشین می‌ماند.
 *   حدس زدنِ کد ─ کدِ نادرست/ناپذیرفته در پیش‌نمایش و ثبت شمرده می‌شود (IP، دستگاه، شماره، و کلِ دکان)؛
 *                 بیش از سقف ⇒ ۴۲۹ `promo_attempts` با `Retry-After`، حتی برای کدِ درست — حدس‌زن نمی‌فهمد کِی رسید.
 *                 حافظه‌ای، مثلِ `middleware/ratelimit` (سرورِ تک‌نمونه).
 *   دفتر ──────── `storefront_promo_claims`: هر تخفیفِ نشسته (اولین خرید، کد، خودکار) با شماره و چکیدهٔ دستگاه، و
 *                 جفتِ (شماره، دستگاه) ِ هر ثبت (`order`) ⇒ فهرستِ «مشکوک» برای صاحب.
 *
 * ⛔ شناسهٔ دستگاه از خودِ مشتری می‌آید (بی امضا): فقط سدِ «همان گوشی، شمارهٔ تازه» است؛ سدِ سخت همان
 * شمارهٔ تأییدشده با کدِ یک‌بارمصرف است. در پایگاه فقط چکیدهٔ SHA-256 ِ آن.
 */
const crypto = require('node:crypto');
const db = require('../db');
const orders = require('./orders');
const { phoneKey, phoneForms } = require('./validate');
const { ApiError, badRequest } = require('../middleware/errors');

const FLAG = 'shop.storefront.promo_guard';
const STEP_MINOR = 100;
const MAX_MINOR = 100_000_000;        //  یک میلیون افغانی
const MAX_PER = 1000;
const DEVICE_RE = /^[A-Za-z0-9_-]{8,128}$/;
const SUSPECT_AT = 3;
const SUSPECT_WINDOW_MS = 90 * 24 * 60 * 60 * 1000;
const ATTEMPTS = Object.freeze({ windowMs: 15 * 60 * 1000, perActor: 8, perShop: 60 });

const flagOn = (shopId) => orders.flagOf(shopId, FLAG);

const DEFAULTS = Object.freeze({
  firstOrder: Object.freeze({ enabled: false, kind: 'percent', bp: 0, amountMinor: 0, maxMinor: 0, minSubtotalMinor: 0, stack: 'combine' }),
  limits: Object.freeze({ codePerPhone: 0, codePerDevice: 0, maxDiscountMinor: 0 }),
});

const obj = (v) => (v && typeof v === 'object' && !Array.isArray(v) ? v : {});
const asJson = (v) => {
  if (v && typeof v === 'object') return v;
  try { return JSON.parse(v) || {}; } catch { return {}; }
};

/** دادهٔ ذخیره‌شده ⇒ تنظیمِ کامل (هر چیزِ نبوده/نادرست ⇒ پیش‌فرض) */
function normalize(data) {
  const d = obj(asJson(data));
  const f = obj(d.firstOrder);
  const l = obj(d.limits);
  const n = (v, def) => (Number.isSafeInteger(v) && v >= 0 ? v : def);
  return {
    firstOrder: {
      enabled: f.enabled === true,
      kind: f.kind === 'amount' ? 'amount' : 'percent',
      bp: n(f.bp, 0), amountMinor: n(f.amountMinor, 0), maxMinor: n(f.maxMinor, 0), minSubtotalMinor: n(f.minSubtotalMinor, 0),
      stack: f.stack === 'exclusive' ? 'exclusive' : 'combine',
    },
    limits: { codePerPhone: n(l.codePerPhone, 0), codePerDevice: n(l.codePerDevice, 0), maxDiscountMinor: n(l.maxDiscountMinor, 0) },
  };
}

/** بدنهٔ PUT ِ صاحب ⇒ تنظیمِ کامل (جایگزینِ همه)؛ نادرست ⇒ ۴۰۰ `bad_promo_guard` */
function parseSettings(body) {
  const b = obj(body);
  const bad = (what) => badRequest(`${what} معتبر نیست`, 'bad_promo_guard');
  const int = (v, what, { min = 0, max = MAX_MINOR, step = 1 } = {}) => {
    if (v === undefined || v === null) return 0;
    if (!Number.isSafeInteger(v) || v < min || v > max || v % step !== 0) throw bad(what);
    return v;
  };
  const f = obj(b.firstOrder);
  const l = obj(b.limits);
  if (f.enabled !== undefined && typeof f.enabled !== 'boolean') throw bad('روشن بودنِ اولین خرید');
  const kind = f.kind === undefined ? 'percent' : f.kind;
  if (!['percent', 'amount'].includes(kind)) throw bad('نوعِ تخفیفِ اولین خرید');
  const stack = f.stack === undefined ? 'combine' : f.stack;
  if (!['combine', 'exclusive'].includes(stack)) throw bad('ترکیبِ اولین خرید');
  const out = {
    firstOrder: {
      enabled: f.enabled === true, kind,
      bp: kind === 'percent' ? int(f.bp, 'درصدِ اولین خرید', { max: 10000 }) : 0,
      amountMinor: kind === 'amount' ? int(f.amountMinor, 'مبلغِ اولین خرید', { step: STEP_MINOR }) : 0,
      maxMinor: int(f.maxMinor, 'سقفِ اولین خرید', { step: STEP_MINOR }),
      minSubtotalMinor: int(f.minSubtotalMinor, 'کفِ سبدِ اولین خرید', { step: STEP_MINOR }),
      stack,
    },
    limits: {
      codePerPhone: int(l.codePerPhone, 'سقفِ کد برای هر شماره', { max: MAX_PER }),
      codePerDevice: int(l.codePerDevice, 'سقفِ کد برای هر دستگاه', { max: MAX_PER }),
      maxDiscountMinor: int(l.maxDiscountMinor, 'سقفِ تخفیفِ هر سفارش', { step: STEP_MINOR }),
    },
  };
  if (out.firstOrder.enabled && (kind === 'percent' ? out.firstOrder.bp : out.firstOrder.amountMinor) <= 0) throw bad('مقدارِ اولین خرید');
  return out;
}

async function settingsOf(shopId, run = (t, p) => db.query(t, p)) {
  const row = (await run('SELECT data FROM storefront_promo_guard WHERE shop_id=$1', [shopId])).rows[0];
  return normalize(row ? row.data : {});
}

async function saveSettings(shopId, body, userId) {
  const s = parseSettings(body);
  const t = db.now();
  await db.query(
    `INSERT INTO storefront_promo_guard (shop_id, data, updated_at, updated_by) VALUES ($1,$2,$3,$4)
     ON CONFLICT (shop_id) DO UPDATE SET data=EXCLUDED.data, updated_at=EXCLUDED.updated_at, updated_by=EXCLUDED.updated_by`,
    [shopId, JSON.stringify(s), t, userId || '']
  );
  return { settings: s, updatedAt: t };
}

/** شناسهٔ دستگاهِ مشتری (`deviceId` ِ بدنه) ⇒ چکیده؛ نبود یا نادرست ⇒ '' (یعنی «نامعلوم»، نه خطا) */
function deviceKeyOf(raw) {
  if (typeof raw !== 'string' || !DEVICE_RE.test(raw)) return '';
  return crypto.createHash('sha256').update(`sf-device\n${raw}`).digest('hex').slice(0, 32);
}

/**
 * پیش از تراکنش (فقط استخر، نه اتصالِ قفل‌دار): پرچم و تنظیم. خاموش ⇒ null.
 * `phone` رشته یا تابعی که فقط با پرچمِ روشن صدا زده می‌شود (پیش‌نمایش: بلیتِ نادرست ⇒ «نامعلوم»، نه خطا).
 * ⇒ `{ shopId, settings, phone, phoneKey, deviceKey, ip }`
 */
async function prepare(shopId, { body, phone = '', ip = '' } = {}) {
  if (!(await flagOn(shopId))) return null;
  if (typeof phone === 'function') phone = await Promise.resolve().then(phone).catch(() => '');
  return {
    shopId, settings: await settingsOf(shopId), phone: phone || '', phoneKey: phone ? phoneKey(phone) : '',
    deviceKey: deviceKeyOf(obj(body).deviceId), ip: String(ip || ''),
  };
}

// ── حدس زدنِ کد (حافظه‌ای) ──────────────────────────────────────────
const tries = new Map();
function actors(g) {
  const out = [[`s:${g.shopId}`, ATTEMPTS.perShop]];
  if (g.ip) out.push([`i:${g.shopId}:${g.ip}`, ATTEMPTS.perActor]);
  if (g.deviceKey) out.push([`d:${g.shopId}:${g.deviceKey}`, ATTEMPTS.perActor]);
  if (g.phoneKey) out.push([`p:${g.shopId}:${g.phoneKey}`, ATTEMPTS.perActor]);
  return out;
}
function hitsOf(k, t) {
  const a = tries.get(k) || [];
  while (a.length && a[0] <= t - ATTEMPTS.windowMs) a.shift();
  if (a.length) tries.set(k, a); else tries.delete(k);
  return a;
}
/** پیش از سنجیدنِ هر کد: سقفِ تلاشِ ناکام پر ⇒ ۴۲۹ `promo_attempts` (با `retryAfter` ِ ثانیه) */
function gate(g, t = Date.now()) {
  for (const [k, max] of actors(g)) {
    const a = hitsOf(k, t);
    if (a.length >= max) {
      throw Object.assign(new ApiError(429, 'promo_attempts', 'کدِ تخفیف بیش از اندازه آزموده شد؛ کمی بعد دوباره'), {
        retryAfter: Math.max(1, Math.ceil((a[0] + ATTEMPTS.windowMs - t) / 1000)),
      });
    }
  }
}
function failed(g, t = Date.now()) {
  for (const [k] of actors(g)) { const a = hitsOf(k, t); a.push(t); tries.set(k, a); }
}
function resetAttempts() { tries.clear(); }
const sweeper = setInterval(() => { const t = Date.now(); for (const k of [...tries.keys()]) hitsOf(k, t); }, 60_000);
if (sweeper.unref) sweeper.unref();

// ── درونِ تراکنش ────────────────────────────────────────────────────

/** سقفِ هر کد برای هر شماره/دستگاه پر شده؟ (`exceptKey` ⇒ همین ثبت شمرده نمی‌شود) */
async function overLimit(run, g, ruleIds, exceptKey = '') {
  const { codePerPhone, codePerDevice } = g.settings.limits;
  if (!ruleIds.length) return false;
  const count = async (col, val, max) => {
    if (!(max > 0) || !val) return false;
    const rows = (await run(
      `SELECT promo_id, count(*)::int AS n FROM storefront_promo_claims
        WHERE shop_id=$1 AND kind='code' AND promo_id = ANY($2::text[]) AND ${col}=$3 AND client_key <> $4 GROUP BY promo_id`,
      [g.shopId, ruleIds, val, exceptKey]
    )).rows;
    return rows.some((r) => Number(r.n) >= max);
  };
  return (await count('phone_key', g.phoneKey, codePerPhone)) || (await count('device_key', g.deviceKey, codePerDevice));
}

/** اولین خرید هنوز باز است؟ بی شمارهٔ تأییدشده ⇒ null (نامعلوم) */
async function firstOrderOpen(run, g) {
  if (!g.phoneKey) return null;
  const prior = (await run(
    'SELECT 1 FROM online_orders WHERE shop_id=$1 AND customer_phone = ANY($2::text[]) LIMIT 1', [g.shopId, phoneForms(g.phone)]
  )).rows.length;
  if (prior) return false;
  const used = (await run(
    `SELECT 1 FROM storefront_promo_claims WHERE shop_id=$1 AND kind='first_order'
        AND (phone_key=$2 OR ($3 <> '' AND device_key=$3)) LIMIT 1`, [g.shopId, g.phoneKey, g.deviceKey]
  )).rows.length;
  return !used;
}

function amountOf(f, baseMinor) {
  if (!(baseMinor > 0)) return 0;
  let d = f.kind === 'percent' ? Math.floor((baseMinor * f.bp) / 10000) : f.amountMinor;
  if (f.maxMinor > 0) d = Math.min(d, f.maxMinor);
  d = Math.min(d, baseMinor);
  return Math.floor(d / STEP_MINOR) * STEP_MINOR;
}

/**
 * نتیجهٔ قاعده‌ها (`e` ِ `storefront-promos.evaluate`) + اولین خرید + سقفِ هر سفارش ⇒ سهمِ هرکدام.
 * ⇒ `{ rulesMinor, firstOrderMinor, discountMinor, dropRules }`
 */
function split(s, e, subtotalMinor, eligible, hasCode) {
  const f = s.firstOrder;
  let rules = e.discountMinor;
  let fo = 0;
  let dropRules = false;
  //  قاعدهٔ «انحصاری» ِ اعمال‌شده تنها می‌ماند (همان معنای هسته: انحصاری بر هر قاعدهٔ دیگر)
  if (eligible && f.enabled && subtotalMinor >= f.minSubtotalMinor && !e.exclusive) {
    if (f.stack === 'combine' || rules === 0) fo = amountOf(f, subtotalMinor - rules);
    else if (!hasCode) {
      const alone = amountOf(f, subtotalMinor);
      if (alone > rules) { fo = alone; rules = 0; dropRules = true; }
    }
  }
  const cap = s.limits.maxDiscountMinor;
  if (cap > 0) { rules = Math.min(rules, cap); fo = Math.min(fo, cap - rules); }
  return { rulesMinor: rules, firstOrderMinor: fo, discountMinor: rules + fo, dropRules };
}

/** پیش‌نمایش: همان حسابِ ثبت، بی نوشتن. ⇒ `{...split, firstOrder: 'applied'|'verify'|'used'|'none'|null}` */
async function preview(run, g, { e, subtotalMinor, code }) {
  const open = g.settings.firstOrder.enabled ? await firstOrderOpen(run, g) : false;
  const r = split(g.settings, e, subtotalMinor, open === true, Boolean(code));
  const firstOrder = !g.settings.firstOrder.enabled ? null
    : open === null ? 'verify' : open === false ? 'used' : r.firstOrderMinor > 0 ? 'applied' : 'none';
  return { ...r, firstOrder };
}

/**
 * ثبت (درونِ تراکنشِ `orders.create`): حساب، درجِ یکتای اولین خرید (ناکام ⇒ بی آن)، و دفترِ مصرف.
 * ⛔ هر خطای پس از این (جمعِ دیده‌شده…) کلِ تراکنش و همین ردیف‌ها را برمی‌گرداند.
 */
async function settle(run, g, { e, subtotalMinor, code, key, at }) {
  const open = g.settings.firstOrder.enabled ? (await firstOrderOpen(run, g)) === true : false;
  let r = split(g.settings, e, subtotalMinor, open, Boolean(code));
  const claim = (kind, promoId, c, minor) => run(
    `INSERT INTO storefront_promo_claims (shop_id, client_key, kind, promo_id, code, phone_key, device_key, discount_minor, created_at)
     VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9) ON CONFLICT DO NOTHING RETURNING kind`,
    [g.shopId, key, kind, promoId, c, g.phoneKey, g.deviceKey, minor, at]
  );
  if (r.firstOrderMinor > 0 && !(await claim('first_order', '', '', r.firstOrderMinor)).rows.length) {
    r = split(g.settings, e, subtotalMinor, false, Boolean(code));   //  ثبتِ هم‌زمانِ دیگری همین شماره/دستگاه را گرفت
  }
  if (!r.dropRules) {
    let left = r.rulesMinor;
    for (const a of e.applied) {
      const minor = Math.min(a.discountMinor, left);
      left -= minor;
      const coded = e.codeRuleIds.includes(a.id);
      if (minor > 0 || coded) await claim(coded ? 'code' : 'auto', a.id, coded ? code : '', minor);
    }
  }
  if (g.deviceKey) await claim('order', '', '', 0);
  return r;
}

// ── برای صاحبِ دکان ─────────────────────────────────────────────────
const deviceLabel = (k) => (k ? k.slice(0, 8) : '');

/** تنظیم، دفترِ اخیر و فهرستِ «مشکوک» (چند شماره از یک دستگاه / چند دستگاه برای یک شماره، ۹۰ روزِ اخیر) */
async function ownerView(shopId, at = db.now()) {
  const since = at - SUSPECT_WINDOW_MS;
  const [settings, recent, devices, phones] = await Promise.all([
    settingsOf(shopId),
    db.query(
      `SELECT c.kind, c.promo_id, c.code, c.phone_key, c.device_key, c.discount_minor, c.created_at, o.order_no
         FROM storefront_promo_claims c
         LEFT JOIN online_orders o ON o.shop_id = c.shop_id AND o.client_key = c.client_key
        WHERE c.shop_id=$1 AND c.kind <> 'order' ORDER BY c.created_at DESC, c.client_key LIMIT 100`, [shopId]),
    db.query(
      `SELECT device_key, count(DISTINCT phone_key)::int AS n, array_agg(DISTINCT phone_key) AS keys, max(created_at) AS last_at
         FROM storefront_promo_claims WHERE shop_id=$1 AND device_key <> '' AND created_at >= $2
        GROUP BY device_key HAVING count(DISTINCT phone_key) >= $3 ORDER BY n DESC, last_at DESC LIMIT 50`, [shopId, since, SUSPECT_AT]),
    db.query(
      `SELECT phone_key, count(DISTINCT device_key)::int AS n, array_agg(DISTINCT device_key) AS keys, max(created_at) AS last_at
         FROM storefront_promo_claims WHERE shop_id=$1 AND device_key <> '' AND created_at >= $2
        GROUP BY phone_key HAVING count(DISTINCT device_key) >= $3 ORDER BY n DESC, last_at DESC LIMIT 50`, [shopId, since, SUSPECT_AT]),
  ]);
  const keys = (v) => (Array.isArray(v) ? v : []).filter(Boolean).sort();
  return {
    settings,
    recent: recent.rows.map((r) => ({
      kind: r.kind, promoId: r.promo_id, code: r.code, phone: r.phone_key, device: deviceLabel(r.device_key),
      discountMinor: Number(r.discount_minor), at: Number(r.created_at), orderNo: r.order_no === null ? null : Number(r.order_no),
    })),
    suspicious: {
      threshold: SUSPECT_AT,
      devices: devices.rows.map((r) => ({ device: deviceLabel(r.device_key), phones: keys(r.keys), count: Number(r.n), lastAt: Number(r.last_at) })),
      phones: phones.rows.map((r) => ({ phone: r.phone_key, devices: keys(r.keys).map(deviceLabel), count: Number(r.n), lastAt: Number(r.last_at) })),
    },
  };
}

/** فهرستِ عمومیِ ویترین (`GET /promos`، فقط وقتی روشن است): اولین خرید بی هیچ دادهٔ مشتری */
function publicFirstOrder(s) {
  const f = s.firstOrder;
  if (!f.enabled) return null;
  return {
    kind: f.kind, ...(f.kind === 'percent' ? { bp: f.bp } : { amountMinor: f.amountMinor }),
    maxMinor: f.maxMinor, minSubtotalMinor: f.minSubtotalMinor,
  };
}

module.exports = {
  FLAG, ATTEMPTS, SUSPECT_AT, flagOn, normalize, parseSettings, settingsOf, saveSettings, deviceKeyOf, prepare,
  gate, failed, resetAttempts, overLimit, firstOrderOpen, amountOf, split, preview, settle, ownerView, publicFirstOrder,
};
