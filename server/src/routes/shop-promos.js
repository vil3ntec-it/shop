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
 *
 * زیرِ پیشوندِ موجودِ `/shop` ⇒ `ACCOUNT_PREFIXES` چیزِ تازه‌ای نمی‌خواهد.
 * ⚠️ در app.js پیش از `/shop` سوار می‌شود.
 */
const express = require('express');
const P = require('../lib/promo-core');
const audit = require('../lib/audit');
const { can } = require('../lib/permissions');
const { stripControl } = require('../lib/validate');
const { query, one, many, now } = require('../db');
const { requireUser, requireShop, requirePermission } = require('../middleware/auth');
const { clientIp } = require('../middleware/ratelimit');
const { badRequest, forbidden, conflict, notFound } = require('../middleware/errors');

const { flagOf } = require('../lib/orders');

/** پرچمِ `live_config` ِ نشانِ تخفیف روی فاکتور — همان `receipt-public.js` */
const STAMP_FLAG = 'shop.promo_stamp';

const router = express.Router();
router.use(requireUser, requireShop);

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

function ruleRow(r) {
  return { ...r.data, rev: Number(r.rev), updatedAt: Number(r.updated_at) };
}

async function stateOf(shopId) {
  const s = await one('SELECT enabled, updated_at FROM shop_promo_settings WHERE shop_id=$1', [shopId]);
  const rows = await many(
    `SELECT data, rev, updated_at FROM shop_promotions WHERE shop_id=$1 AND deleted=false ORDER BY created_at, id`, [shopId]);
  return { enabled: !!(s && s.enabled), updatedAt: Number(s?.updated_at || 0), rules: rows.map(ruleRow) };
}

router.get('/', requirePermission('data.read'), async (req, res) => {
  res.json({
    ...(await stateOf(req.shopId)),
    stamp: await flagOf(req.shopId, STAMP_FLAG),
    version: P.VERSION,
    permissions: { manage: can(req.role, 'settings.write'), belowCost: req.role === 'owner' },
  });
});

router.put('/settings', requirePermission('settings.write'), async (req, res) => {
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
});

router.put('/rules/:id', requirePermission('settings.write'), async (req, res) => {
  const id = String(req.params.id || '');
  if (!ID_RE.test(id)) throw badRequest('شناسهٔ قاعده نادرست است', 'invalid_id');
  const body = req.body && req.body.rule;
  if (!body || typeof body !== 'object' || Array.isArray(body)) throw badRequest('قاعده لازم است', 'invalid_rule');
  if (Buffer.byteLength(JSON.stringify(body), 'utf8') > MAX_BYTES) throw badRequest('قاعده خیلی بزرگ است', 'too_large');

  const prev = await one('SELECT data, deleted FROM shop_promotions WHERE shop_id=$1 AND id=$2', [req.shopId, id]);
  //  `uses` را فقط سرور می‌شمارد؛ مقدارِ برنامه نادیده
  const max = body.maxUses;
  if (max !== undefined && max !== null && max !== 0) {
    const kept = prev && typeof prev.data.maxUses === 'number' && prev.data.maxUses > 0 && max === prev.data.maxUses;
    if (!kept) throw badRequest('«سقفِ مصرفِ کد» هنوز شمرده نمی‌شود؛ آن را خالی بگذارید (بی سقف)', 'max_uses_unsupported');
  }
  const rule = P.ruleOf({ ...body, id, maxUses: 0, uses: prev ? Number(prev.data.uses || 0) : 0 });
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
     RETURNING data, rev, updated_at`,
    [req.shopId, id, JSON.stringify(rule), rule.code, t, req.user.id]);
  await audit.log({
    shopId: req.shopId, userId: req.user.id, action: prev && !prev.deleted ? 'promos.update' : 'promos.create',
    targetType: 'promotion', targetId: id,
    detail: { name: rule.name, kind: rule.kind, active: rule.active, code: rule.code, allowBelowCost: rule.allowBelowCost },
    ip: clientIp(req),
  });
  res.json({ rule: ruleRow(row) });
});

router.delete('/rules/:id', requirePermission('settings.write'), async (req, res) => {
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
});

module.exports = router;
module.exports.MESSAGES = MESSAGES;
