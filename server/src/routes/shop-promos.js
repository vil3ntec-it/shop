'use strict';
/**
 * تخفیف‌ها و قاعده‌های قیمتِ دکان — `/api/shop/promos` (PRM-01..09، 11، 12، 17).
 *
 * ⛔ پیش‌فرض خاموش: تا صاحب یا مدیر «تخفیف‌های خودکار در صندوق» را روشن
 * نکند، `enabled=false` است و صندوق همان جمعِ امروز را حساب می‌کند.
 * ⛔ shop_id فقط از عضویتِ کاربرِ توکن می‌آید.
 * ⛔ دیدن برای هر عضو (`data.read` — صندوقدار باید قاعده‌ها را داشته باشد تا
 *    آفلاین هم حساب کند)؛ نوشتن کارِ صاحب و مدیر (`settings.write`).
 * ⛔ «زیرِ بهای خرید» (`allowBelowCost`) فقط با صاحبِ دکان.
 * ⛔ هر قاعده پیش از نوشتن با `validate`ِ همان هستهٔ صندوق سنجیده می‌شود
 *    (`lib/promo-core.js` = بایت‌به‌بایت `license/promo-core.js`).
 * ⛔ پاک کردن نرم است و کدِ تخفیف حتی پس از پاک شدن آزاد نمی‌شود (PRM-02).
 * ⛔ «سقفِ مصرف» تا شمارشِ مصرف نیامده پذیرفته نمی‌شود (`max_uses_unsupported`):
 *    `uses` را هنوز هیچ فروشی بالا نمی‌برد، پس سقف فقط روی کاغذ بود. هر `maxUses` جز
 *    «نبودن» و `0` پیش از `ruleOf` رد می‌شود (رشتهٔ "10" بی‌صدا صفر نمی‌شد) — مگر همان
 *    مقدارِ ذخیره‌شدهٔ ردیفِ قبلی باشد: آن سقف هرگز کار نمی‌کرد و برنامهٔ پیشین (که فرمش
 *    سقف را نشان نمی‌دهد و از قاعدهٔ پایه نگه می‌دارد) باید بتواند ویرایش کند ⇒ `0` می‌نشیند.
 * ⛔ `stamp` در پاسخِ GET = پرچمِ `shop.promo_stamp` (`live_config`، پیش‌فرض خاموش): نشانِ
 *    تخفیف روی فاکتور و ردیف‌های تازهٔ فاکتور در برنامه، و ردیفِ آگاهیِ فاکتورِ عمومی.
 * ⛔ PRM-02، دفترِ مصرف (`shop_promo_redemptions`، مهاجرتِ ۱۳۶) پشتِ پرچمِ `shop.promo_uses`
 *    (`live_config`، پیش‌فرض خاموش؛ `redeem` در پاسخِ GET). خاموش ⇒ همان رفتارِ بالا و redeem/release
 *    ۴۰۳ `promo_uses_disabled`. روشن ⇒ `maxUses` (عددِ صحیحِ ۰ تا ۱٬۰۰۰٬۰۰۰) پذیرفته است و:
 *      • `POST /redeem {saleId, codes}`: پیش از ثبتِ فاکتور؛ ردیفِ قاعده‌ها `FOR UPDATE` قفل می‌شود و
 *        سقف در همان تراکنش شمرده می‌شود ⇒ آخرین ظرفیت فقط یک بار، حتی از دو صندوقِ هم‌زمان.
 *        همان فاکتور دوباره ⇒ «already» بی مصرفِ دوم (تلاشِ پس از قطعِ شبکه). چند کد همه یا هیچ:
 *        یکی رد ⇒ هیچ‌کدام نمی‌نشیند (۴۰۹ `coupon_rejected` با حالِ هر کد).
 *      • `POST /redeem/release {saleId, codes?}`: آزاد کردنِ نرم (ردیف می‌ماند). خودِ صندوقدار فقط
 *        تا `RELEASE_WINDOW_MS` پس از گرفتن (سبدِ رهاشده/کدِ برداشته)؛ بعد از آن فقط دارندهٔ
 *        `sales.cancel` (لغوِ فاکتور).
 *      • `uses` ِ هر قاعده در پاسخ‌ها = شمارِ مصرفِ آزادنشدهٔ دفتر؛ عددِ برنامه همیشه نادیده.
 *      • سقفِ نرخ برای هر کاربر (`RATE_PROMO_REDEEM_MAX` در دقیقه، پیش‌فرض ۱۲۰): شاگرد نمی‌تواند
 *        با فاکتورهای ساختگی ظرفیتِ کد را در یک لحظه بسوزاند؛ هر مصرف هم سابقه (`promos.redeem`) دارد.
 *    سرور پنجرهٔ زمانی/کانال را دوباره نمی‌سنجد (کارِ هستهٔ صندوق است)؛ فقط قاعدهٔ زنده و سقف را.
 *
 * زیرِ پیشوندِ موجودِ `/shop` ⇒ `ACCOUNT_PREFIXES` چیزِ تازه‌ای نمی‌خواهد.
 * ⚠️ در app.js پیش از `/shop` سوار می‌شود.
 */
const express = require('express');
const P = require('../lib/promo-core');
const audit = require('../lib/audit');
const { can } = require('../lib/permissions');
const { stripControl } = require('../lib/validate');
const { query, one, many, now, tx } = require('../db');
const { requireUser, requireShop, requirePermission } = require('../middleware/auth');
const { clientIp, rateLimit } = require('../middleware/ratelimit');
const { badRequest, forbidden, conflict, notFound } = require('../middleware/errors');

const { flagOf } = require('../lib/orders');

/** پرچمِ `live_config` ِ نشانِ تخفیف روی فاکتور — همان `receipt-public.js` */
const STAMP_FLAG = 'shop.promo_stamp';
/** پرچمِ `live_config` ِ دفترِ مصرفِ کد (PRM-02) */
const USES_FLAG = 'shop.promo_uses';
const MAX_USES = 1000000;
/** همان سقفِ `Sale.coupons` ِ برنامه */
const MAX_CODES = 10;
const SALE_RE = /^[A-Za-z0-9_-]{1,64}$/;
/** خودِ صندوقدار کدی را که گرفته تا این مدت آزاد می‌کند (سبدِ رهاشده، کدِ برداشته) */
const RELEASE_WINDOW_MS = 15 * 60 * 1000;
const redeemLimit = rateLimit({
  max: Number(process.env.RATE_PROMO_REDEEM_MAX) > 0 ? Number(process.env.RATE_PROMO_REDEEM_MAX) : 120,
  windowMs: 60 * 1000, keyPrefix: 'promo-redeem', key: (req) => (req.user ? `${req.shopId}:${req.user.id}` : ''),
});

const router = express.Router();
router.use(requireUser, requireShop);
const wrap = (fn) => (req, res, next) => Promise.resolve(fn(req, res, next)).catch(next);

const MAX_RULES = 200;
const MAX_BYTES = 16 * 1024;
const ID_RE = /^[A-Za-z0-9_-]{1,64}$/;

/** پیامِ فارسیِ هر خطای `validate` — همان فهرستِ صفحهٔ «تخفیف‌ها»ی برنامه (`PromoText.kt`) */
const MESSAGES = {
  id: 'شناسه لازم است', name: 'نام لازم است', kind: 'نوعِ تخفیف ناشناخته است', stack: 'شیوهٔ ترکیب ناشناخته است',
  priority: 'اولویت باید بینِ ‎-۱۰۰۰ و ۱۰۰۰ باشد', code: 'کد باید ۳ تا ۳۲ حرف یا رقمِ لاتین باشد', uses: 'سقفِ مصرف نادرست است',
  channel: 'کانال ناشناخته است', window: 'بازهٔ زمانی نادرست است', bp: 'درصد باید بیش از ۰ و حداکثر ۱۰۰ باشد',
  amount: 'مبلغ باید مثبت و افغانیِ کامل باشد', prices: 'فهرستِ قیمت‌های ویژه نادرست است',
  tiers: 'پله‌ها باید صعودی باشند (هم آستانه، هم تخفیف)', bxgy: '«بخر» و «بگیر» دست‌کم ۱ و درصدِ معتبر', bundle: 'بسته دست‌کم ۲ قلم و قیمتِ افغانیِ کامل',
};

/** `uses` همیشه از دفترِ مصرف (PRM-02) — عددِ ذخیره‌شده در `data` و عددِ برنامه هیچ‌کدام مرجع نیستند */
function ruleRow(r, uses = 0) {
  return { ...r.data, uses, rev: Number(r.rev), updatedAt: Number(r.updated_at) };
}

/** مصرفِ آزادنشدهٔ هر قاعدهٔ دکان: Map(promoId ⇒ n) */
async function liveUses(shopId, promoId = null) {
  const rows = await many(
    `SELECT promo_id, count(*)::int AS n FROM shop_promo_redemptions
      WHERE shop_id=$1 AND released_at IS NULL ${promoId === null ? '' : 'AND promo_id=$2'} GROUP BY promo_id`,
    promoId === null ? [shopId] : [shopId, promoId]);
  return new Map(rows.map((r) => [r.promo_id, Number(r.n)]));
}

async function stateOf(shopId) {
  const s = await one('SELECT enabled, updated_at FROM shop_promo_settings WHERE shop_id=$1', [shopId]);
  const rows = await many(
    `SELECT id, data, rev, updated_at FROM shop_promotions WHERE shop_id=$1 AND deleted=false ORDER BY created_at, id`, [shopId]);
  const uses = await liveUses(shopId);
  return {
    enabled: !!(s && s.enabled), updatedAt: Number(s?.updated_at || 0),
    rules: rows.map((r) => ruleRow(r, uses.get(r.id) || 0)),
  };
}

/** پرچمِ دفترِ مصرف خاموش ⇒ ۴۰۳ (هیچ نوشتنی) */
async function needUses(req) {
  if (!(await flagOf(req.shopId, USES_FLAG))) {
    throw forbidden('شمارشِ مصرفِ کدِ تخفیف برای این دکان روشن نیست', 'promo_uses_disabled');
  }
}

function saleIdOf(body) {
  const id = body && body.saleId;
  if (typeof id !== 'string' || !SALE_RE.test(id)) throw badRequest('شناسهٔ فاکتور نادرست است', 'invalid_sale_id');
  return id;
}

/** کدهای نرمال، یکتا و به ترتیبِ آمدن؛ `optional` ⇒ نبودن = همه */
function codesOf(body, { optional = false } = {}) {
  const raw = body && body.codes;
  if (raw === undefined && optional) return null;
  if (!Array.isArray(raw) || raw.length < 1 || raw.length > MAX_CODES) {
    throw badRequest(`۱ تا ${MAX_CODES} کدِ تخفیف لازم است`, 'invalid_codes');
  }
  const out = [];
  for (const c of raw) {
    if (typeof c !== 'string' || c.length > 64) throw badRequest('کدِ تخفیف نادرست است', 'invalid_codes');
    const n = P.normalizeCode(c);
    if (!n) throw badRequest('کدِ تخفیف نادرست است', 'invalid_codes');
    if (!out.includes(n)) out.push(n);
  }
  return out;
}

/** صاحب/مدیر یا هر کسی که لغوِ فاکتور را دارد (سقفِ نقش، سپس لایه‌های دکان/کاربر) */
async function mayCancel(req) {
  if (!can(req.role, 'sales.cancel')) return false;
  const perms = require('../lib/staff-perms');
  return !perms.isLayered('sales.cancel') || perms.memberAllows(req, 'sales.cancel');
}

const deviceOf = (req) => (req.device ? String(req.device.device_uid || req.device.id || '') : '');

router.get('/', requirePermission('data.read'), wrap(async (req, res) => {
  res.json({
    ...(await stateOf(req.shopId)),
    stamp: await flagOf(req.shopId, STAMP_FLAG),
    redeem: await flagOf(req.shopId, USES_FLAG),
    version: P.VERSION,
    permissions: { manage: can(req.role, 'settings.write'), belowCost: req.role === 'owner' },
  });
}));

router.put('/settings', requirePermission('settings.write'), wrap(async (req, res) => {
  const on = req.body && req.body.enabled;
  if (typeof on !== 'boolean') throw badRequest('enabled باید true یا false باشد', 'invalid_setting');
  const t = now();
  await query(
    `INSERT INTO shop_promo_settings (shop_id, enabled, updated_at, updated_by) VALUES ($1,$2,$3,$4)
     ON CONFLICT (shop_id) DO UPDATE SET enabled=excluded.enabled, updated_at=excluded.updated_at, updated_by=excluded.updated_by`,
    [req.shopId, on, t, req.user.id]);
  await audit.log({
    shopId: req.shopId, userId: req.user.id, action: 'promos.settings',
    targetType: 'shop', targetId: req.shopId, detail: { enabled: on }, ip: clientIp(req),
  });
  res.json({ enabled: on, updatedAt: t });
}));

router.put('/rules/:id', requirePermission('settings.write'), wrap(async (req, res) => {
  const id = String(req.params.id || '');
  if (!ID_RE.test(id)) throw badRequest('شناسهٔ قاعده نادرست است', 'invalid_id');
  const body = req.body && req.body.rule;
  if (!body || typeof body !== 'object' || Array.isArray(body)) throw badRequest('قاعده لازم است', 'invalid_rule');
  if (Buffer.byteLength(JSON.stringify(body), 'utf8') > MAX_BYTES) throw badRequest('قاعده خیلی بزرگ است', 'too_large');

  const prev = await one('SELECT data, deleted FROM shop_promotions WHERE shop_id=$1 AND id=$2', [req.shopId, id]);
  //  `uses` را فقط سرور می‌شمارد (دفترِ مصرف)؛ مقدارِ برنامه نادیده
  const max = body.maxUses;
  let maxUses = 0;
  if (await flagOf(req.shopId, USES_FLAG)) {
    //  PRM-02: سقف شمرده می‌شود ⇒ فقط عددِ صحیح؛ "10"، ۲٫۵، منفی و بولی بی‌صدا عددِ دیگری نمی‌شوند
    if (max !== undefined && max !== null) {
      if (!Number.isInteger(max) || max < 0 || max > MAX_USES) {
        return res.status(400).json({ error: { code: 'invalid_rule', message: MESSAGES.uses, fields: ['uses'] } });
      }
      maxUses = max;
    }
  } else if (max !== undefined && max !== null && max !== 0) {
    const kept = prev && typeof prev.data.maxUses === 'number' && prev.data.maxUses > 0 && max === prev.data.maxUses;
    if (!kept) throw badRequest('«سقفِ مصرفِ کد» هنوز شمرده نمی‌شود؛ آن را خالی بگذارید (بی سقف)', 'max_uses_unsupported');
  }
  const rule = P.ruleOf({ ...body, id, maxUses, uses: prev ? Number(prev.data.uses || 0) : 0 });
  rule.name = stripControl(rule.name).trim().slice(0, 80);
  rule.code = P.normalizeCode(rule.code);
  const errors = P.validate(rule, 100);
  if (errors.length) {
    return res.status(400).json({
      error: { code: 'invalid_rule', message: errors.map((e) => MESSAGES[e] || e).join('؛ '), fields: errors },
    });
  }
  if (rule.allowBelowCost && req.role !== 'owner') {
    throw forbidden('فروشِ زیرِ بهای خرید را فقط صاحبِ دکان اجازه می‌دهد', 'below_cost_owner_only');
  }
  if (!prev || prev.deleted) {
    const n = await one('SELECT count(*)::int AS n FROM shop_promotions WHERE shop_id=$1 AND deleted=false', [req.shopId]);
    if (n.n >= MAX_RULES) throw conflict(`حداکثر ${MAX_RULES} قاعده`, 'too_many_rules');
  }
  if (rule.code) {
    const taken = await one('SELECT id FROM shop_promotions WHERE shop_id=$1 AND code=$2 AND id<>$3', [req.shopId, rule.code, id]);
    if (taken) throw conflict('این کد پیش‌تر در این دکان به کار رفته است', 'code_taken');
  }
  const t = now();
  const row = await one(
    `INSERT INTO shop_promotions (shop_id, id, data, code, rev, created_at, updated_at, updated_by, deleted)
     VALUES ($1,$2,$3::jsonb,$4,1,$5,$5,$6,false)
     ON CONFLICT (shop_id, id) DO UPDATE SET data=excluded.data, code=excluded.code, rev=shop_promotions.rev+1,
       updated_at=excluded.updated_at, updated_by=excluded.updated_by, deleted=false
     RETURNING id, data, rev, updated_at`,
    [req.shopId, id, JSON.stringify(rule), rule.code, t, req.user.id]);
  await audit.log({
    shopId: req.shopId, userId: req.user.id, action: prev && !prev.deleted ? 'promos.update' : 'promos.create',
    targetType: 'promotion', targetId: id,
    detail: { name: rule.name, kind: rule.kind, active: rule.active, code: rule.code, allowBelowCost: rule.allowBelowCost },
    ip: clientIp(req),
  });
  res.json({ rule: ruleRow(row, (await liveUses(req.shopId, id)).get(id) || 0) });
}));

router.delete('/rules/:id', requirePermission('settings.write'), wrap(async (req, res) => {
  const id = String(req.params.id || '');
  const row = await one(
    `UPDATE shop_promotions SET deleted=true, rev=rev+1, updated_at=$3, updated_by=$4
      WHERE shop_id=$1 AND id=$2 AND deleted=false RETURNING id`,
    [req.shopId, id, now(), req.user.id]);
  if (!row) throw notFound('قاعده پیدا نشد', 'rule_not_found');
  await audit.log({
    shopId: req.shopId, userId: req.user.id, action: 'promos.delete',
    targetType: 'promotion', targetId: id, detail: {}, ip: clientIp(req),
  });
  res.json({ ok: true });
}));

/* ── PRM-02: دفترِ مصرفِ کد ─────────────────────────────────────────── */

router.post('/redeem', requirePermission('data.write'), redeemLimit, wrap(async (req, res) => {
  await needUses(req);
  const saleId = saleIdOf(req.body);
  const codes = codesOf(req.body);
  const at = now();
  const out = await tx(async (c) => {
    //  قفلِ ردیفِ قاعده‌ها به ترتیبِ شناسه (بی بن‌بست): شمارش و نوشتنِ هر دو صندوق پشتِ سرِ هم
    const rules = (await c.query(
      `SELECT id, code, data, deleted FROM shop_promotions
        WHERE shop_id=$1 AND code = ANY($2::text[]) ORDER BY id FOR UPDATE`,
      [req.shopId, codes])).rows;
    const results = [];
    for (const code of codes) {
      const r = rules.find((x) => x.code === code);
      if (!r || r.deleted) { results.push({ code, status: 'unknown_code' }); continue; }
      const maxUses = Number(r.data.maxUses) || 0;
      const n = Number((await c.query(
        `SELECT count(*)::int AS n FROM shop_promo_redemptions WHERE shop_id=$1 AND promo_id=$2 AND released_at IS NULL`,
        [req.shopId, r.id])).rows[0].n);
      const base = { code, promoId: r.id };
      if (r.data.active === false) { results.push({ ...base, status: 'inactive', uses: n, maxUses }); continue; }
      const held = (await c.query(
        `SELECT 1 FROM shop_promo_redemptions WHERE shop_id=$1 AND promo_id=$2 AND sale_id=$3 AND released_at IS NULL`,
        [req.shopId, r.id, saleId])).rows.length > 0;
      if (held) results.push({ ...base, status: 'already', uses: n, maxUses });
      else if (maxUses > 0 && n >= maxUses) results.push({ ...base, status: 'used_up', uses: n, maxUses });
      else results.push({ ...base, status: 'ok', uses: n, maxUses });
    }
    if (results.some((x) => !['ok', 'already'].includes(x.status))) return { rejected: true, results };
    for (const x of results) {
      if (x.status !== 'ok') continue;
      await c.query(
        `INSERT INTO shop_promo_redemptions (shop_id, promo_id, sale_id, code, user_id, device_id, created_at, updated_at)
         VALUES ($1,$2,$3,$4,$5,$6,$7,$7)
         ON CONFLICT (shop_id, promo_id, sale_id) DO UPDATE SET code=excluded.code, user_id=excluded.user_id,
           device_id=excluded.device_id, updated_at=excluded.updated_at, released_at=NULL, released_by=NULL
         WHERE shop_promo_redemptions.released_at IS NOT NULL`,
        [req.shopId, x.promoId, saleId, x.code, req.user.id, deviceOf(req), at]);
      x.status = 'redeemed';
      x.uses += 1;
    }
    return { rejected: false, results };
  });
  if (out.rejected) {
    return res.status(409).json({
      error: { code: 'coupon_rejected', message: 'کدِ تخفیف پذیرفته نشد؛ آن را از سبد بردارید', codes: out.results },
    });
  }
  //  سابقه با شناسهٔ قاعده، به‌صورتِ متن (کلیدِ «code» را `audit.scrub` عمداً دور می‌ریزد و آرایه را شیء می‌کند)
  const fresh = out.results.filter((x) => x.status === 'redeemed').map((x) => x.promoId);
  if (fresh.length) {
    await audit.log({
      shopId: req.shopId, userId: req.user.id, action: 'promos.redeem',
      targetType: 'sale', targetId: saleId, detail: { promos: fresh.join(',') }, ip: clientIp(req),
    });
  }
  res.json({ codes: out.results });
}));

router.post('/redeem/release', requirePermission('data.write'), wrap(async (req, res) => {
  await needUses(req);
  const saleId = saleIdOf(req.body);
  const codes = codesOf(req.body, { optional: true });
  const rows = await many(
    `SELECT promo_id, code, user_id, updated_at FROM shop_promo_redemptions
      WHERE shop_id=$1 AND sale_id=$2 AND released_at IS NULL ${codes ? 'AND code = ANY($3::text[])' : ''}`,
    codes ? [req.shopId, saleId, codes] : [req.shopId, saleId]);
  if (!rows.length) return res.json({ released: 0 });
  const t = now();
  const mine = rows.every((r) => r.user_id === req.user.id && t - Number(r.updated_at) <= RELEASE_WINDOW_MS);
  if (!mine && !(await mayCancel(req))) {
    throw forbidden('آزاد کردنِ کدِ مصرف‌شده کارِ کسی است که فاکتور را لغو می‌کند', 'release_not_allowed');
  }
  const done = await many(
    `UPDATE shop_promo_redemptions SET released_at=$3, released_by=$4, updated_at=$3
      WHERE shop_id=$1 AND sale_id=$2 AND released_at IS NULL AND promo_id = ANY($5::text[]) RETURNING promo_id`,
    [req.shopId, saleId, t, req.user.id, rows.map((r) => r.promo_id)]);
  if (done.length) {
    await audit.log({
      shopId: req.shopId, userId: req.user.id, action: 'promos.release',
      targetType: 'sale', targetId: saleId, detail: { promos: done.map((d) => d.promo_id).join(',') }, ip: clientIp(req),
    });
  }
  res.json({ released: done.length });
}));

module.exports = router;
module.exports.MESSAGES = MESSAGES;
