'use strict';
/**
 * درهای پیک — `/api/courier` (v3 §۳.۱۱، DLV-01/02/08/10).
 *
 * پیک عضوِ همین دکان است که ردیفِ `shop_couriers` دارد. فقط مأموریت‌های
 * خودش را می‌بیند: نشانه، پین، شمارهٔ **پوشیده**، مبلغِ موردِ انتظار و
 * کرایه — بی زمان‌سنج (DLV-20). نشانی پس از پایانِ مأموریت دیده نمی‌شود.
 *
 * ⛔ پیش‌فرض خاموش: بی پرچم‌های `shop.orders` و `shop.delivery` همه ۴۰۴.
 * ⛔ رویدادِ مأموریتِ گرفته‌شده با اشتراکِ تمام‌شده یا پیکِ غیرفعال هم
 * پذیرفته می‌شود (پولِ گرفته‌شده واقعیت است و بی‌صدا دور ریخته نمی‌شود،
 * §۲۸.۶)؛ «قبولِ» کارِ تازه نه.
 *
 * ⚠️ پیشوندِ تازه است: باید به ACCOUNT_PREFIXESِ پنلِ خانگی (ریپوی دیگر)
 * هم افزوده شود — دستورش در شرحِ PR است.
 */
const path = require('path');
const express = require('express');
const delivery = require('../lib/delivery');
const orders = require('../lib/orders');
const courierMap = require('../lib/courier-map');
const { requireUser, requireShop, allowCourierScope } = require('../middleware/auth');
const { forbidden, notFound } = require('../middleware/errors');

const router = express.Router();
const wrap = (fn) => (req, res, next) => Promise.resolve(fn(req, res, next)).catch(next);

/*
 *  DLV-01 (§۳.۱۱): **تنها** دری که توکنِ دامنه‌دارِ پیک را می‌پذیرد. پرچمِ
 *  `req.courierScope` فقط همین‌جا روشن می‌شود و تهِ همین روتر هر نشانیِ
 *  ناشناخته ۴۰۴ است، تا درخواست با پرچمِ روشن به روترِ دیگری سُر نخورد.
 */
router.use(allowCourierScope, requireUser, requireShop, wrap(async (req, res, next) => {
  const st = await delivery.stateOf(req.shopId);
  if (!st.readable) return next(notFound('تحویل و پیک برای این دکان روشن نیست', 'delivery_disabled'));
  const courier = await delivery.courierOfUser(req.shopId, req.user.id);
  if (!courier) return next(forbidden('این حساب پیکِ این دکان نیست', 'not_a_courier'));
  req.courier = courier;
  req.courierActor = {
    userId: req.user.id, role: req.role, courierId: courier.id, active: courier.active === true,
    deviceId: req.device?.id || '',
  };
  next();
}));

router.get('/me', wrap(async (req, res) => {
  const c = req.courier;
  res.json({
    courier: { id: c.id, name: c.name, active: c.active === true, cashCapMinor: Number(c.cash_cap_minor) },
    serverTime: Date.now(),
  });
}));

router.get('/missions', wrap(async (req, res) => {
  //  DLV-05/DLV-16: پیوندهای مسیریاب و «موقعیت فقط در راه» فقط با پرچم (`courierMap.decorate`)
  res.json(await courierMap.decorate(req.shopId, await delivery.missions(req.shopId, req.courier.id, {
    scope: String(req.query.scope || 'active'), limit: req.query.limit,
  })));
}));

/**
 * صفِ رویدادها (DLV-10): `{deviceId?, events:[{eventId, assignmentId, type,
 * deviceTime, deviceSeq, reason?, note?, cashCollectedMinor?, cashReason?, code?}]}`.
 * پاسخ برای هر رویداد: applied | duplicate | unchanged | rejected(code).
 */
router.post('/events', wrap(async (req, res) => {
  res.json(await delivery.courierEvents(req.shopId, req.body, req.courierActor));
}));

router.post('/missions/:id/photos', wrap(async (req, res) => {
  const out = await delivery.putPhoto(req.shopId, req.params.id, req.body, req.courierActor);
  res.status(out.replayed ? 200 : 201).json(out);
}));

/*
 *  S5 (پشتِ پرچم‌های خاموشِ `shop.delivery_customer_view` و جدای
 *  `shop.delivery_live_location`): «تقریباً کی می‌رسم» و آخرین نقطه — فقط
 *  مأموریتِ خودِ پیک و فقط «در راه». مشتری فقط از سرور می‌خواند.
 */
router.post('/missions/:id/eta', wrap(async (req, res) => {
  res.json(await delivery.putEta(req.shopId, req.params.id, req.body, req.courierActor));
}));

router.post('/missions/:id/location', wrap(async (req, res) => {
  res.json(await delivery.putLocation(req.shopId, req.params.id, req.body, req.courierActor));
}));

/*
 *  DLV-05 (پشتِ پرچمِ خاموشِ `shop.delivery_map`): نقشهٔ آفلاینِ شهر. فهرست با
 *  اندازه و `sha256`؛ خودِ فایل با `Range` (ادامهٔ دانلودِ بریده). شناسه فقط از
 *  فهرستِ پوشهٔ نقشهٔ سرور است — هیچ مسیری از درخواست به دیسک نمی‌رسد.
 */
async function needMap(req, res, next) {
  if (!(await orders.flagOf(req.shopId, courierMap.FLAG))) {
    return next(notFound('نقشهٔ آفلاین برای این دکان روشن نیست', 'delivery_map_disabled'));
  }
  next();
}

router.get('/map', needMap, async (req, res) => {
  res.set('Cache-Control', 'no-store');
  res.json({
    attribution: courierMap.ATTRIBUTION,
    packs: (await courierMap.packs()).map((p) => ({ ...p, url: `/api/courier/map/${p.id}` })),
  });
});

router.get('/map/:id', needMap, async (req, res, next) => {
  const file = await courierMap.packFile(req.params.id);
  if (!file) return next(notFound('این نقشه روی سرور نیست', 'map_not_found'));
  //  `root` + نامِ سنجیده: send خودش هم از بیرون رفتن از پوشه جلوگیری می‌کند
  res.sendFile(path.basename(file), {
    root: path.dirname(file), acceptRanges: true, etag: true, lastModified: true,
    headers: { 'Content-Type': 'application/octet-stream', 'Cache-Control': 'private, max-age=3600' },
  }, (err) => { if (err && !res.headersSent) next(err); });
});

//  ⛔ تهِ درِ پیک: هیچ نشانیِ دیگری از این‌جا به روترهای بعدی نمی‌رود
router.use((req, res, next) => next(notFound('این نشانی در درِ پیک نیست', 'not_found')));

module.exports = router;
