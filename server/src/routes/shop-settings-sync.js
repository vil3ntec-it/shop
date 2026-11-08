'use strict';
/**
 * همگام‌سازیِ زندهٔ تنظیماتِ دکان — `/api/shop/settings-sync` (منطق و قاعدهٔ تعارض: `lib/shop-settings-sync.js`).
 *
 *   GET /?since=<rev>   هر عضو ⇒ `{enabled, rev?, rows?, more?, permissions?}` — ردیف‌های تازه‌تر از `since` (هر کلید:
 *                       `{store, key, t, v, rev, at, by}`؛ `t: 'del'` یعنی برداشته شد)
 *   PUT /               صاحب و مدیر ⇒ `{entries: [{store, key, t, v}], device_id?}` ⇒ `{enabled, rev, applied, unchanged}`
 *                       و خبرِ سوکتِ زنده `{event:'settings', rev}` به دستگاه‌های همان دکان
 *
 * ⛔ پیش‌فرض خاموش: بی پرچمِ `shop.settings_sync` ⇒ `GET` فقط `{enabled:false}` و `PUT` ۴۰۳ `settings_sync_disabled`.
 * ⛔ shop_id فقط از عضویتِ توکن. سقف: سطلِ IP پیش از احراز، سپس سطلِ هر کاربر.
 * ⛔ کلیدِ اشتراک ندارد (SUB-10: FREE): تنظیمِ دکان بخشی از هستهٔ دکان است، نه قابلیتِ پولی.
 */
const express = require('express');
const config = require('../config');
const sync = require('../lib/shop-settings-sync');
const audit = require('../lib/audit');
const perms = require('../lib/staff-perms');
const { requireUser, requireShop, requirePermission } = require('../middleware/auth');
const { rateLimit, clientIp } = require('../middleware/ratelimit');
const { forbidden } = require('../middleware/errors');

const router = express.Router();
router.use(rateLimit({ max: config.rateLimit.generalMax, keyPrefix: 'shop-settings-sync-ip' }));
const limit = rateLimit({ max: 120, windowMs: 60 * 1000, keyPrefix: 'shop-settings-sync', key: (req) => req.user && req.user.id });
router.use(requireUser, requireShop, limit, requirePermission('data.read'));

const needFlag = async (req, res, next) => {
  if (!(await sync.flagOn(req.shopId))) return next(forbidden('همگام‌سازیِ تنظیماتِ دکان برای این دکان روشن نیست', 'settings_sync_disabled'));
  next();
};

/**
 * `permissions.write` ِ پاسخ = همان سنجشِ `PUT` (`requirePermission` ⇐ `memberAllows`): سقفِ نقش **و** لایه‌های دکان، کاربر و
 * نقشِ سفارشی (STF-01..03). ⛔ فقط نقش بس نیست: مدیری که صاحب «تنظیمات» را برایش بسته وگرنه `write: true` می‌گرفت و
 * برنامه (`ShopSettingsPlan.plan`) تغییرِ محلی را هر دور می‌فرستاد (۴۰۳) و قاعدهٔ پولِ دکان را هرگز نمی‌نشاند.
 */
const writer = async (req) => sync.WRITERS.includes(req.role) && perms.memberAllows(req, 'settings.write');

router.get('/', async (req, res) => {
  if (!(await sync.flagOn(req.shopId))) return res.json({ enabled: false });
  const since = Number.parseInt(typeof req.query.since === 'string' ? req.query.since : '0', 10);
  const out = await sync.read(req.shopId, Number.isSafeInteger(since) ? since : 0);
  res.json({ enabled: true, ...out, permissions: { write: await writer(req) } });
});

router.put('/', requirePermission('settings.write'), needFlag, async (req, res) => {
  const out = await sync.write(req.shopId, { userId: req.user.id, role: req.role }, req.body);
  if (out.applied.length) {
    await audit.log({
      shopId: req.shopId, userId: req.user.id, action: 'settings.sync', targetType: 'shop', targetId: req.shopId,
      //  رشته، نه آرایه: `audit.scrub` آرایه را شیء می‌کند
      detail: { rev: out.rev, changed: out.applied.slice(0, 50).map((a) => `${a.store}:${a.key}`).join(','), count: out.applied.length },
      ip: clientIp(req),
    });
    //  خبر به دستگاه‌های دیگرِ همان دکان — خودِ داده از این در نمی‌رود (همان `photos`)
    try {
      require('../lib/sync-v1-live').relay({ app: 'shop', accountKind: 'shop', accountId: String(req.shopId) }, { event: 'settings', rev: out.rev });
    } catch { /* سوکت نبود: دستگاه‌ها با `hello` ِ بعدی می‌گیرند */ }
  }
  res.json({ enabled: true, ...out });
});

module.exports = router;
