'use strict';
/**
 * تنظیمِ ویترین از برنامهٔ دکان — `/api/shop/storefront` (فاز ۴؛ WEB-01/03،
 * PRD-27، بخشِ ۳۱).
 *
 * ⛔ پیش‌فرض خاموش: بی پرچم‌های `shop.orders` و `shop.storefront` فقط
 * `GET /config` جواب می‌دهد (تا برنامه بداند بخش را نشان ندهد) و هیچ ردیفی
 * ساخته نمی‌شود. نوشتن کلیدهای اشتراک را هم می‌خواهد.
 * ⛔ shop_id فقط از عضویتِ کاربرِ توکن می‌آید. تنظیم و انتشار کارِ صاحب و
 * مدیر است (`settings.write`)؛ دیدنِ فهرست برای هر عضو (`data.read`).
 *
 * زیرِ پیشوندِ موجودِ `/shop` است ⇒ `ACCOUNT_PREFIXES` چیزِ تازه‌ای نمی‌خواهد.
 * ⚠️ در app.js پیش از `/shop` سوار می‌شود.
 */
const express = require('express');
const sf = require('../lib/storefront');
const sfd = require('../lib/storefront-delivery');
const audit = require('../lib/audit');
const { can } = require('../lib/permissions');
const { requireUser, requireShop, requirePermission } = require('../middleware/auth');
const { clientIp } = require('../middleware/ratelimit');
const { forbidden } = require('../middleware/errors');

const router = express.Router();
router.use(requireUser, requireShop);
const wrap = (fn) => (req, res, next) => Promise.resolve(fn(req, res, next)).catch(next);

const needFlag = wrap(async (req, res, next) => {
  const st = await sf.stateOf(req.shopId);
  if (!st.flag) return next(forbidden('ویترین برای این دکان روشن نیست', 'storefront_disabled'));
  req.storeState = st;
  next();
});

const needWrite = (req, res, next) => {
  const { entitled, ent } = req.storeState;
  if (entitled) return next();
  return next(ent.trial.used && !ent.trial.active
    ? forbidden('اشتراکِ این دکان به پایان رسیده است.', 'subscription_expired')
    : forbidden('این قابلیت نیازمندِ اشتراک است', 'subscription_required'));
};

router.get('/config', wrap(async (req, res) => {
  const { ent, ...st } = await sf.stateOf(req.shopId);
  res.json({
    ...st,
    settings: st.flag ? await sf.settingsOf(req.shopId) : null,
    permissions: { manage: can(req.role, 'settings.write') },
    //  برگهٔ «ارسال و زمان» فقط با پرچمِ جدای `shop.storefront.zones`
    zones: st.flag ? await sfd.flagOn(req.shopId) : false,
  });
}));

router.put('/', requirePermission('settings.write'), needFlag, needWrite, wrap(async (req, res) => {
  const settings = await sf.saveSettings(req.shopId, req.body, req.user.id);
  await audit.log({
    shopId: req.shopId, userId: req.user.id, action: 'storefront.settings',
    targetType: 'storefront', targetId: settings.slug,
    detail: { slug: settings.slug, enabled: settings.enabled }, ip: clientIp(req),
  });
  res.json({ settings });
}));

//  PRES-10: نامِ دکانِ دیگر در بدنه ⇒ ۴۰۳ (پیش‌تر نادیده گرفته می‌شد و
//  اعلام روی دکانِ خودِ توکن می‌نشست). خودِ دکان همیشه از عضویت است.
const SHOP_KEYS = ['shopId', 'shop_id', 'storeId', 'store_id'];
const claimedShopOf = (req) => {
  const b = req.body && typeof req.body === 'object' ? req.body : {};
  return SHOP_KEYS.map((k) => b[k]).find((v) => v !== undefined && v !== null && v !== '');
};
const ownShopOnly = (req, res, next) => {
  const claimed = claimedShopOf(req);
  if (claimed !== undefined && String(claimed) !== req.shopId) {
    return next(forbidden('این اعلام برای دکانِ دیگری است', 'foreign_shop'));
  }
  next();
};

router.post('/presence', ownShopOnly, requirePermission('settings.write'), needFlag, needWrite, wrap(async (req, res) => {
  const out = await sf.declarePresence(req.shopId, req.body, {
    userId: req.user.id, deviceId: req.get('x-device') || '',
  });
  res.json(out);
}));

/**
 * PRES-10: هر اعلامِ ردشدهٔ کاربرِ واردشده (دکانِ دیگر، بدنهٔ دست‌کاری‌شده،
 * بی‌اجازه، بی اشتراک، …) در سابقه می‌نشیند و همان خطا برمی‌گردد. بی‌توکن ⇒
 * ثبت نمی‌شود (سابقه پرِ درخواستِ ناشناس نشود). بدنه خودش نوشته نمی‌شود.
 */
router.use('/presence', (err, req, res, next) => {
  const status = Number(err && err.status);
  if (req.method !== 'POST' || !req.user || !(status >= 400 && status < 500)) return next(err);
  const claimed = claimedShopOf(req);
  audit.log({
    shopId: req.shopId || '', userId: req.user.id, action: 'storefront.presence_rejected',
    targetType: 'storefront', targetId: req.shopId || '',
    detail: {
      status, reason: String(err.code || ''),
      ...(claimed !== undefined ? { claimedShop: String(claimed).slice(0, 80) } : {}),
    },
    ip: clientIp(req),
  }).catch(() => {}).then(() => next(err));
});

//  ── ناحیه‌های ارسال و بازه‌های زمان‌بندی (CHK-06/07/10/12) ─────────────
const needZones = wrap(async (req, res, next) => {
  if (!(await sfd.flagOn(req.shopId))) return next(forbidden('ناحیه و زمان‌بندی برای این دکان روشن نیست', 'zones_disabled'));
  next();
});

router.get('/delivery', requirePermission('data.read'), needFlag, needZones, wrap(async (req, res) => {
  res.json(await sfd.settingsOf(req.shopId));
}));

router.put('/delivery', requirePermission('settings.write'), needFlag, needZones, needWrite, wrap(async (req, res) => {
  const out = await sfd.saveSettings(req.shopId, req.body, req.user.id);
  await audit.log({
    shopId: req.shopId, userId: req.user.id, action: 'storefront.delivery',
    targetType: 'storefront', targetId: req.shopId,
    detail: { zones: out.zones.length, slotRules: out.slotRules.length, allowAsap: out.allowAsap, slotDays: out.slotDays },
    ip: clientIp(req),
  });
  res.json(out);
}));

router.get('/products', requirePermission('data.read'), needFlag, wrap(async (req, res) => {
  res.json(await sf.staffProducts(req.shopId, { cursor: req.query.cursor, limit: req.query.limit }));
}));

router.put('/products/:productId', requirePermission('settings.write'), needFlag, needWrite, wrap(async (req, res) => {
  const out = await sf.setProduct(req.shopId, req.params.productId, req.body, req.user.id);
  await audit.log({
    shopId: req.shopId, userId: req.user.id, action: 'storefront.product',
    targetType: 'product', targetId: out.productId,
    detail: { published: out.published, onlinePriceMinor: out.onlinePriceMinor }, ip: clientIp(req),
  });
  res.json(out);
}));

module.exports = router;
