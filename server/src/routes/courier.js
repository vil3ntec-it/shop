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
const express = require('express');
const delivery = require('../lib/delivery');
const audit = require('../lib/audit');
const { requireUser, requireShop, allowCourierScope } = require('../middleware/auth');
const { clientIp } = require('../middleware/ratelimit');
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
    //  DLV-02: دکمهٔ «تماس با مشتری» فقط وقتی دیده می‌شود که دکان روشنش کرده باشد
    callEnabled: await delivery.callFlagOf(req.shopId),
    serverTime: Date.now(),
  });
}));

router.get('/missions', wrap(async (req, res) => {
  res.json(await delivery.missions(req.shopId, req.courier.id, {
    scope: String(req.query.scope || 'active'), limit: req.query.limit,
  }));
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

//  ⛔ تهِ درِ پیک: هیچ نشانیِ دیگری از این‌جا به روترهای بعدی نمی‌رود
router.use((req, res, next) => next(notFound('این نشانی در درِ پیک نیست', 'not_found')));

module.exports = router;
