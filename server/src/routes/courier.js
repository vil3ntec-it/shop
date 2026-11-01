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
const audit = require('../lib/audit');
const orders = require('../lib/orders');
const courierMap = require('../lib/courier-map');
const courierChecks = require('../lib/courier-checks');
const courierRoute = require('../lib/courier-route');
const ratings = require('../lib/delivery-ratings');
const { requireUser, requireShop, allowCourierScope } = require('../middleware/auth');
const { clientIp } = require('../middleware/ratelimit');
const { forbidden, notFound } = require('../middleware/errors');

const router = express.Router();

/*
 *  DLV-01 (§۳.۱۱): **تنها** دری که توکنِ دامنه‌دارِ پیک را می‌پذیرد. پرچمِ
 *  `req.courierScope` فقط همین‌جا روشن می‌شود و تهِ همین روتر هر نشانیِ
 *  ناشناخته ۴۰۴ است، تا درخواست با پرچمِ روشن به روترِ دیگری سُر نخورد.
 */
router.use(allowCourierScope, requireUser, requireShop, async (req, res, next) => {
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
});

router.get('/me', async (req, res) => {
  const c = req.courier;
  res.json({
    courier: { id: c.id, name: c.name, active: c.active === true, cashCapMinor: Number(c.cash_cap_minor) },
    //  DLV-02: دکمهٔ «تماس با مشتری» فقط وقتی دیده می‌شود که دکان روشنش کرده باشد
    callEnabled: await delivery.callFlagOf(req.shopId),
    serverTime: Date.now(),
  });
});

/**
 * «نقدِ من» و آمار (DLV-11/12، فقط خواندن): نقدِ در دست، سقف و جای خالی،
 * تحویل‌شده‌های تسویه‌نشده، تسویه‌های خودش، شمارِ امروز/هفته و نام و شمارهٔ
 * دکان (برای «تماس با دکان»). مرزِ روز و هفته را سرور به وقتِ دکان می‌سازد؛
 * `?today=&week=` ِ برنامهٔ کهنه نادیده گرفته می‌شود (ساعتِ گوشی مرجع نیست).
 * ⛔ فقط دادهٔ همین پیک؛ پیکِ دیگر یا دکانِ دیگر هیچ‌چیزِ این را نمی‌بیند.
 */
router.get('/summary', async (req, res) => {
  res.set('Cache-Control', 'no-store');
  res.json(await delivery.courierSummary(req.shopId, req.courier));   //  مرزِ روز و هفته را سرور به وقتِ دکان می‌سازد
});

router.get('/missions', async (req, res) => {
  //  DLV-05/DLV-16: پیوندهای مسیریاب و «موقعیت فقط در راه» فقط با پرچم (`courierMap.decorate`)؛
  //  DLV-07/DLV-17: شمارِ کیسه‌های اسکنی و فاصلهٔ مجاز از پین، هم فقط با پرچم (`courierChecks.decorate`)
  res.json(await courierChecks.decorate(req.shopId, await courierMap.decorate(req.shopId, await delivery.missions(req.shopId, req.courier.id, {
    scope: String(req.query.scope || 'active'), limit: req.query.limit,
  }))));
});

/*
 *  DLV-04 (پشتِ پرچمِ خاموشِ `shop.delivery_route`): ترتیبِ پیشنهادیِ مأموریت‌های پذیرفتهٔ همین پیک در یک مسیر.
 *  `?lat=&lng=` موقعیتِ همین لحظه است و فقط برای همین پاسخ خوانده می‌شود (نگه داشته نمی‌شود، DLV-16).
 */
router.get('/route', async (req, res, next) => {
  if (!(await orders.flagOf(req.shopId, courierRoute.FLAG))) {
    return next(notFound('مسیرِ پیشنهادی برای این دکان روشن نیست', 'delivery_route_disabled'));
  }
  const start = courierRoute.startOf(req.query);
  const list = await delivery.missions(req.shopId, req.courier.id, { scope: 'active', limit: 100 });
  res.set('Cache-Control', 'no-store');
  res.json({ ...courierRoute.routeOf(list.items, start), serverTime: list.serverTime });
});

/**
 * صفِ رویدادها (DLV-10): `{deviceId?, events:[{eventId, assignmentId, type,
 * deviceTime, deviceSeq, reason?, note?, cashCollectedMinor?, cashReason?, code?, bags? (DLV-07), position? (DLV-17)}]}`.
 * پاسخ برای هر رویداد: applied | duplicate | unchanged | rejected(code).
 */
router.post('/events', async (req, res) => {
  res.json(await delivery.courierEvents(req.shopId, req.body, req.courierActor));
});

router.post('/missions/:id/photos', async (req, res) => {
  const out = await delivery.putPhoto(req.shopId, req.params.id, req.body, req.courierActor);
  res.status(out.replayed ? 200 : 201).json(out);
});

/**
 * «تماس با مشتری» (DLV-02، §۳۸): شمارهٔ کامل فقط همین لحظه و فقط برای مأموریتِ پذیرفته و
 * زندهٔ همین پیک، پشتِ پرچمِ خاموشِ `shop.delivery_courier_call`. POST است چون هر بار در
 * سابقهٔ دکان می‌نشیند (بی خودِ شماره) و نباید پیش‌واکشی یا نگه داشته شود.
 * دستگیره بی `wrap` است: Express 5 خطای async را خودش به `next` می‌دهد.
 */
router.post('/missions/:id/call', async (req, res) => {
  const out = await delivery.callCustomer(req.shopId, req.params.id, req.courierActor);
  await audit.log({
    shopId: req.shopId, userId: req.user.id, action: 'delivery.courier.call',
    targetType: 'delivery_assignment', targetId: out.assignmentId,
    detail: { orderId: out.orderId, courierId: req.courier.id, status: out.status }, ip: clientIp(req),
  });
  res.set('Cache-Control', 'no-store');
  res.json({ phone: out.phone, status: out.status });
});

/*
 *  DLV-18 (پشتِ پرچمِ خاموشِ `shop.delivery_ratings`): پیک پس از «تحویل شد» به همان تحویل امتیاز می‌دهد
 *  (`{stars, tags}`، فقط برچسبِ فهرستِ ثابت). یک بار؛ همان امتیاز دوباره ⇒ همان پاسخ.
 */
router.post('/missions/:id/rating', async (req, res) => {
  const out = await ratings.rateByCourier(req.shopId, req.params.id, req.body, req.courierActor);
  res.status(out.replayed ? 200 : 201).json(out);
});

/*
 *  S5 (پشتِ پرچم‌های خاموشِ `shop.delivery_customer_view` و جدای
 *  `shop.delivery_live_location`): «تقریباً کی می‌رسم» و آخرین نقطه — فقط
 *  مأموریتِ خودِ پیک و فقط «در راه». مشتری فقط از سرور می‌خواند.
 */
router.post('/missions/:id/eta', async (req, res) => {
  res.json(await delivery.putEta(req.shopId, req.params.id, req.body, req.courierActor));
});

router.post('/missions/:id/location', async (req, res) => {
  res.json(await delivery.putLocation(req.shopId, req.params.id, req.body, req.courierActor));
});

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
