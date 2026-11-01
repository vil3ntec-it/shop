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
const sfm = require('../lib/storefront-media');
const config = require('../config');
const areas = require('../lib/storefront-area-requests');
const extras = require('../lib/storefront-extras');
const audit = require('../lib/audit');
const idempotency = require('../lib/idempotency');
const { can } = require('../lib/permissions');
const { requireUser, requireShop, requirePermission } = require('../middleware/auth');
const { rateLimit, clientIp } = require('../middleware/ratelimit');
const { ApiError, forbidden, notFound, badRequest } = require('../middleware/errors');

const router = express.Router();
router.use(requireUser, requireShop);

const needFlag = async (req, res, next) => {
  const st = await sf.stateOf(req.shopId);
  if (!st.flag) return next(forbidden('ویترین برای این دکان روشن نیست', 'storefront_disabled'));
  req.storeState = st;
  next();
};

const needWrite = (req, res, next) => {
  const { entitled, ent } = req.storeState;
  if (entitled) return next();
  return next(ent.trial.used && !ent.trial.active
    ? forbidden('اشتراکِ این دکان به پایان رسیده است.', 'subscription_expired')
    : forbidden('این قابلیت نیازمندِ اشتراک است', 'subscription_required'));
};

router.get('/config', async (req, res) => {
  const { ent, ...st } = await sf.stateOf(req.shopId);
  res.json({
    ...st,
    settings: st.flag ? await sf.settingsOf(req.shopId) : null,
    permissions: { manage: can(req.role, 'settings.write') },
    //  برگهٔ «ارسال و زمان» فقط با پرچمِ جدای `shop.storefront.zones`
    zones: st.flag ? await sfd.flagOn(req.shopId) : false,
    //  انتشارِ گروهی در یک درخواست (M2) فقط با پرچمِ جدای `shop.storefront.bulk`
    bulk: st.flag ? await sf.bulkFlagOn(req.shopId) : false,
    //  M3: لوگو و تصویرِ سر فقط با پرچمِ جدای `shop.storefront.media`
    media: st.flag ? await sfm.flagOn(req.shopId) : false,
  });
});

router.put('/', requirePermission('settings.write'), needFlag, needWrite, async (req, res) => {
  const settings = await sf.saveSettings(req.shopId, req.body, req.user.id);
  await audit.log({
    shopId: req.shopId, userId: req.user.id, action: 'storefront.settings',
    targetType: 'storefront', targetId: settings.slug,
    detail: { slug: settings.slug, enabled: settings.enabled }, ip: clientIp(req),
  });
  res.json({ settings });
});

//  PRES-10: نامِ دکانِ دیگر در بدنه ⇒ ۴۰۳ (پیش‌تر نادیده گرفته می‌شد و
//  اعلام روی دکانِ خودِ توکن می‌نشست). خودِ دکان همیشه از عضویت است.
const SHOP_KEYS = ['shopId', 'shop_id', 'storeId', 'store_id'];
const ownShopOnly = (req, res, next) => {
  const b = req.body && typeof req.body === 'object' ? req.body : {};
  //  هر کدام از کلیدها که پر و بیگانه باشد بس است (نه فقط نخستینِ پر)
  const foreign = SHOP_KEYS.some((k) => b[k] !== undefined && b[k] !== null && b[k] !== '' && String(b[k]) !== req.shopId);
  if (foreign) return next(forbidden('این اعلام برای دکانِ دیگری است', 'foreign_shop'));
  next();
};

router.post('/presence', ownShopOnly, requirePermission('settings.write'), needFlag, needWrite, async (req, res) => {
  const out = await sf.declarePresence(req.shopId, req.body, {
    userId: req.user.id, deviceId: req.get('x-device') || '',
  });
  res.json(out);
});

/**
 * PRES-10: اعلامِ ردشدهٔ کاربرِ واردشده (دکانِ دیگر، بدنهٔ دست‌کاری‌شده،
 * بی‌اجازه، بی اشتراک، …) در سابقه می‌نشیند و همان خطا برمی‌گردد.
 * ⛔ ضدِ سیل: برای هر (کاربر، دلیل) در هر ۱۰ دقیقه یک ردیف. بی‌توکن یا بی دکان ⇒
 * هیچ ردیف. بدنه و شناسهٔ دکانِ دیگر نوشته نمی‌شوند.
 */
const REJECT_AUDIT_MS = 10 * 60 * 1000;
const lastRejectAudit = new Map();
function shouldAuditReject(key, at = Date.now()) {
  const last = lastRejectAudit.get(key);
  if (last !== undefined && at - last < REJECT_AUDIT_MS) return false;
  if (lastRejectAudit.size >= 10_000) {
    for (const [k, t] of lastRejectAudit) if (at - t >= REJECT_AUDIT_MS) lastRejectAudit.delete(k);
    if (lastRejectAudit.size >= 10_000) lastRejectAudit.clear();
  }
  lastRejectAudit.set(key, at);
  return true;
}

router.use('/presence', (err, req, res, next) => {
  const status = Number(err && err.status);
  if (req.method !== 'POST' || !req.user || !req.shopId || !(status >= 400 && status < 500)) return next(err);
  if (!shouldAuditReject(`${req.user.id}|${String(err.code || '')}`)) return next(err);
  audit.log({
    shopId: req.shopId, userId: req.user.id, action: 'storefront.presence_rejected',
    targetType: 'storefront', targetId: req.shopId,
    //  ⛔ شناسهٔ دکانِ دیگر نوشته نمی‌شود: سابقهٔ این دکان را اعضایش می‌بینند
    detail: { status, reason: String(err.code || '') },
    ip: clientIp(req),
  }).catch(() => {}).then(() => next(err));
});

//  ── WEB-24: بنرهای زمان‌بندی‌شده (پرچمِ خاموشِ `shop.storefront.banners` ⇒ ۴۰۳) ─────────
const needBanners = async (req, res, next) => {
  if (!(await extras.bannersOn(req.shopId))) return next(forbidden('بنرِ ویترین برای این دکان روشن نیست', 'banners_disabled'));
  next();
};

router.get('/banners', requirePermission('data.read'), needFlag, needBanners, async (req, res) => {
  res.json({ banners: await extras.banners(req.shopId), max: extras.MAX_BANNERS });
});

router.put('/banners', requirePermission('settings.write'), needFlag, needBanners, needWrite, async (req, res) => {
  const out = await extras.saveBanners(req.shopId, req.body, req.user.id);
  await audit.log({
    shopId: req.shopId, userId: req.user.id, action: 'storefront.banners',
    targetType: 'storefront', targetId: req.shopId, detail: { count: out.length }, ip: clientIp(req),
  });
  res.json({ banners: out, max: extras.MAX_BANNERS });
});

//  ── ناحیه‌های ارسال و بازه‌های زمان‌بندی (CHK-06/07/10/12) ─────────────
const needZones = async (req, res, next) => {
  if (!(await sfd.flagOn(req.shopId))) return next(forbidden('ناحیه و زمان‌بندی برای این دکان روشن نیست', 'zones_disabled'));
  next();
};

router.get('/delivery', requirePermission('data.read'), needFlag, needZones, async (req, res) => {
  res.json(await sfd.settingsOf(req.shopId));
});

router.put('/delivery', requirePermission('settings.write'), needFlag, needZones, needWrite, async (req, res) => {
  const out = await sfd.saveSettings(req.shopId, req.body, req.user.id);
  await audit.log({
    shopId: req.shopId, userId: req.user.id, action: 'storefront.delivery',
    targetType: 'storefront', targetId: req.shopId,
    detail: { zones: out.zones.length, slotRules: out.slotRules.length, allowAsap: out.allowAsap, slotDays: out.slotDays },
    ip: clientIp(req),
  });
  res.json(out);
});

//  ── CHK-10/S34: «خبرم کن» ِ بیرونِ محدوده ─────────────────────────────
//  تقاضا به ازای خانهٔ شبکه (بی شماره) و شمارهٔ کسانی که ناحیهٔ امروز پوشانده است.
//  ⛔ شمارهٔ مشتری ⇒ فقط صاحب و مدیر (`settings.write`)، نه هر عضو.
const needArea = async (req, res, next) => {
  if (!(await areas.flagOn(req.shopId))) return next(forbidden('«خبرم کن» برای این دکان روشن نیست', 'notify_area_disabled'));
  next();
};

router.get('/area-requests', requirePermission('settings.write'), needFlag, needZones, needArea, async (req, res) => {
  res.set('Cache-Control', 'no-store');
  res.json(await areas.forShop(req.shopId));
});

//  «خبر دادم» ⇒ درخواست پاک می‌شود؛ در سابقه نامِ ناحیه، هرگز شماره
router.post('/area-requests/:id/notified', requirePermission('settings.write'), needFlag, needZones, needArea, needWrite, async (req, res) => {
  const out = await areas.markNotified(req.shopId, req.params.id);
  await audit.log({
    shopId: req.shopId, userId: req.user.id, action: 'storefront.area_request_notified',
    targetType: 'storefront', targetId: req.shopId, detail: { zone: out.zone }, ip: clientIp(req),
  });
  res.json(out);
});

router.get('/products', requirePermission('data.read'), needFlag, async (req, res) => {
  const page = await sf.staffProducts(req.shopId, { cursor: req.query.cursor, limit: req.query.limit });
  //  M2: دلیلِ «در بازار نمی‌آید» فقط با پرچمِ گروهی؛ بی آن پاسخ همان قبلی است
  res.json(await sf.bulkFlagOn(req.shopId) ? await sf.withReasons(req.shopId, page) : page);
});

//  ── انتشارِ گروهی (M2): تا ۵۰۰ کالا در یک درخواست، کلیدِ یکتایی اجباری ──
const needBulk = async (req, res, next) => {
  if (!(await sf.bulkFlagOn(req.shopId))) return next(forbidden('انتشارِ گروهی برای این دکان روشن نیست', 'bulk_disabled'));
  next();
};

router.put('/products', requirePermission('settings.write'), needFlag, needBulk, needWrite, async (req, res) => {
  const idem = idempotency.fromRequest(req, `storefront-bulk:${req.shopId}`);
  if (!idem) throw badRequest('کلیدِ یکتایی (Idempotency-Key) لازم است', 'idempotency_key_required');
  const out = await sf.setProducts(req.shopId, req.body, req.user.id, idem);
  if (out.replayed) res.set('Idempotent-Replayed', 'true');
  else {
    await audit.log({
      shopId: req.shopId, userId: req.user.id, action: 'storefront.products_bulk',
      targetType: 'storefront', targetId: req.shopId,
      detail: { applied: out.applied, notFound: out.notFound, published: out.published }, ip: clientIp(req),
    });
  }
  res.json(out);
});

router.put('/products/:productId', requirePermission('settings.write'), needFlag, needWrite, async (req, res) => {
  const out = await sf.setProduct(req.shopId, req.params.productId, req.body, req.user.id);
  await audit.log({
    shopId: req.shopId, userId: req.user.id, action: 'storefront.product',
    targetType: 'product', targetId: out.productId,
    detail: { published: out.published, onlinePriceMinor: out.onlinePriceMinor }, ip: clientIp(req),
  });
  res.json(out);
});

//  ── M3: لوگو و تصویرِ سرِ ویترین (قراردادِ بازار §۱.۱) ──────────────
//  ⛔ پرچمِ `shop.storefront.media` (پیش‌فرض خاموش ⇒ ۴۰۳ `media_disabled`، بی هیچ نوشتن).
//  نوشتن: `settings.write` (صاحب/مدیر یا کارمندی که صاحب اجازه داده) + اشتراک (needWrite).
//  نوع فقط از محتوا (`image-pipeline`)، سقفِ حجم و ابعادِ ثابت در `lib/storefront-media.js`.
const MEDIA_BASE = '/api/shop/storefront/media';
const needMedia = async (req, res, next) => {
  if (!(await sfm.flagOn(req.shopId))) return next(forbidden('لوگو و تصویرِ ویترین برای این دکان روشن نیست', 'media_disabled'));
  next();
};
const mediaLimit = rateLimit({
  max: config.rateLimit.storefrontMediaDailyMax, windowMs: 24 * 60 * 60 * 1000,
  keyPrefix: 'storefront-media-day', key: (req) => req.shopId,
});
const rawMedia = (req, res, next) => express.raw({ type: () => true, limit: sfm.MAX_BYTES + 1024 })(req, res, (err) => {
  if (err && err.type === 'entity.too.large') return next(new ApiError(413, 'too_large', 'عکس بیش از اندازه بزرگ است'));
  next(err);
});

router.get('/media', requirePermission('data.read'), needFlag, needMedia, async (req, res) => {
  res.set('Cache-Control', 'private, no-cache');
  res.json({ enabled: true, specs: sfm.SPECS, types: sfm.MIMES, ...(await sfm.staffView(req.shopId, MEDIA_BASE)) });
});

router.get('/media/:kind/:version', requirePermission('data.read'), needFlag, needMedia, async (req, res) => {
  const f = await sfm.file(req.shopId, sfm.kindOf(req.params.kind), req.params.version);
  if (!f) throw notFound('این عکس نیست', 'media_not_found');
  res.set('Cache-Control', 'private, max-age=31536000, immutable');
  res.set('ETag', `"${f.hash}"`);
  res.set('X-Content-Type-Options', 'nosniff');
  if (req.get('if-none-match') === `"${f.hash}"`) return res.status(304).end();
  res.type(f.mime).send(f.data);
});

router.put('/media/:kind', requirePermission('settings.write'), needFlag, needMedia, needWrite, mediaLimit, rawMedia, async (req, res) => {
  const kind = sfm.kindOf(req.params.kind);
  const out = await sfm.put(req.shopId, kind, Buffer.isBuffer(req.body) ? req.body : Buffer.alloc(0), req.user.id);
  if (!out.replayed) {
    await audit.log({
      shopId: req.shopId, userId: req.user.id, action: 'storefront.media', targetType: 'storefront', targetId: req.shopId,
      detail: { kind, version: out.version }, ip: clientIp(req),
    });
  }
  res.json({ ...out, ...(await sfm.staffView(req.shopId, MEDIA_BASE)) });
});

router.delete('/media/:kind', requirePermission('settings.write'), needFlag, needMedia, needWrite, async (req, res) => {
  const kind = sfm.kindOf(req.params.kind);
  const out = await sfm.remove(req.shopId, kind);
  if (out.removed) {
    await audit.log({
      shopId: req.shopId, userId: req.user.id, action: 'storefront.media_removed', targetType: 'storefront', targetId: req.shopId,
      detail: { kind }, ip: clientIp(req),
    });
  }
  res.json({ ...out, ...(await sfm.staffView(req.shopId, MEDIA_BASE)) });
});

module.exports = router;
module.exports.resetRejectAudit = () => lastRejectAudit.clear();
