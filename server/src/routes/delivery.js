'use strict';
/**
 * بخشِ «تحویل و پیک»ِ دکان — `/api/shop/delivery` (v3 §۲۲.۲ مرحلهٔ C).
 *
 * ⛔ پیش‌فرض خاموش: بی پرچم‌های `shop.orders` و `shop.delivery` همه‌چیز ۴۰۴
 * است بجز `GET /config` (تا برنامه بداند بخش را نشان ندهد).
 * ⛔ خواندن با اشتراکِ تمام‌شده هم باز است؛ نوشتن کلیدهای اشتراکِ
 * `online_orders` و `delivery` را هم می‌خواهد.
 * ⛔ shop_id فقط از عضویتِ کاربرِ توکن می‌آید.
 *
 * زیرِ پیشوندِ موجودِ `/shop` است، پس ACCOUNT_PREFIXESِ پنلِ خانگی چیزِ
 * تازه‌ای نمی‌خواهد.
 */
const express = require('express');
const delivery = require('../lib/delivery');
const D = require('../lib/delivery-states');
const { cashReport } = require('../lib/delivery-cash-report');
const audit = require('../lib/audit');
const { can } = require('../lib/permissions');
const { requireUser, requireShop, requirePermission } = require('../middleware/auth');
const { clientIp } = require('../middleware/ratelimit');
const { forbidden, notFound } = require('../middleware/errors');

const router = express.Router();
router.use(requireUser, requireShop, requirePermission('delivery.view'));

const keyOf = (req) => delivery.eventKey(req.get('idempotency-key'), { required: false });

/** بی پرچم ⇒ ۴۰۴ (بخش برای این دکان وجود ندارد). */
const needFlag = async (req, res, next) => {
  const st = await delivery.stateOf(req.shopId);
  if (!st.readable) return next(notFound('تحویل و پیک برای این دکان روشن نیست', 'delivery_disabled'));
  req.deliveryState = st;
  next();
};

/** نوشتن: اشتراک (همان پیام‌های `requireFeature`). */
const needWrite = (req, res, next) => {
  const st = req.deliveryState;
  if (st && st.entitled) return next();
  const ent = st?.ent;
  return next(ent && ent.trial.used && !ent.trial.active
    ? forbidden('اشتراکِ این دکان به پایان رسیده است.', 'subscription_expired')
    : forbidden('این قابلیت نیازمندِ اشتراک است', 'subscription_required'));
};

const log = (req, action, targetType, targetId, detail) => audit.log({
  shopId: req.shopId, userId: req.user.id, action, targetType, targetId, detail, ip: clientIp(req),
});

router.get('/config', async (req, res) => {
  const { ent, ...st } = await delivery.stateOf(req.shopId);
  res.json({
    ...st,
    //  §۲۰.۶: شبکهٔ پیکِ VILL3N هنوز هیچ‌جا فعال نیست — گزینه نشان داده نمی‌شود
    modes: { shop_courier: true, external: true, pickup: true, platform: false },
    reasons: D.REASONS,
    defaultCashCapMinor: delivery.DEFAULT_CASH_CAP_MINOR,
    permissions: Object.fromEntries(
      ['view', 'assign', 'handover', 'couriers', 'settle', 'photo'].map((k) => [k, can(req.role, `delivery.${k}`)])
    ),
  });
});

router.use(needFlag);

// ── پیک‌ها ───────────────────────────────────────────────────────────

router.get('/couriers', async (req, res) => {
  res.json({ items: await delivery.listCouriers(req.shopId) });
});

router.post('/couriers', requirePermission('delivery.couriers'), needWrite, async (req, res) => {
  const courier = await delivery.createCourier(req.shopId, req.body, delivery.staffActor(req));
  await log(req, 'delivery.courier.create', 'shop_courier', courier.id, { userId: courier.userId, cashCapMinor: courier.cashCapMinor });
  res.status(201).json({ courier });
});

const updateCourier = async (req, res) => {
  const courier = await delivery.updateCourier(req.shopId, req.params.id, req.body);
  await log(req, 'delivery.courier.update', 'shop_courier', courier.id, { active: courier.active, cashCapMinor: courier.cashCapMinor });
  res.json({ courier });
};
router.patch('/couriers/:id', requirePermission('delivery.couriers'), needWrite, updateCourier);
//  همان کار با POST — `HttpURLConnection`ِ JVM (برنامهٔ کامپیوتر) PATCH را نمی‌فرستد
router.post('/couriers/:id', requirePermission('delivery.couriers'), needWrite, updateCourier);

// ── مأموریت‌ها ───────────────────────────────────────────────────────

router.get('/assignments', async (req, res) => {
  res.json(await delivery.board(req.shopId, {
    scope: String(req.query.scope || 'live'), limit: req.query.limit, before: req.query.before,
  }));
});

router.get('/orders/:orderId', async (req, res) => {
  res.json(await delivery.forOrder(req.shopId, req.params.orderId));
});

/**
 * سپردنِ سفارش (DLV-03 دستی). کدِ تحویل — اگر ساخته شود — فقط همین یک‌بار
 * در پاسخ می‌آید؛ در دیتابیس فقط HMACش است.
 */
router.post('/orders/:orderId/assign', requirePermission('delivery.assign'), needWrite, async (req, res) => {
  const out = await delivery.assign(req.shopId, req.params.orderId, req.body, delivery.staffActor(req), keyOf(req));
  if (out.replayed) {
    res.set('Idempotent-Replayed', 'true');
    return res.json({ assignment: out.assignment, replayed: true });
  }
  await log(req, 'delivery.assign', 'delivery_assignment', out.assignment.id, {
    orderId: out.assignment.orderId, mode: out.assignment.mode, courierId: out.assignment.courierId,
    fareMinor: out.assignment.fareMinor,
  });
  res.status(201).json({ assignment: out.assignment, ...(out.code ? { code: out.code } : {}) });
});

router.post('/assignments/:id/code', requirePermission('delivery.assign'), needWrite, async (req, res) => {
  const out = await delivery.regenerateCode(req.shopId, req.params.id, delivery.staffActor(req));
  await log(req, 'delivery.code.regenerate', 'delivery_assignment', req.params.id, {});
  res.json(out);
});

/** گذارِ کارمند: پس‌گرفتن، برگشت، دریافتِ حضوری با کد، شرکتِ بیرونی، تأییدِ دستی. */
router.post('/assignments/:id/status', needWrite, async (req, res) => {
  let out;
  try {
    out = await delivery.staffEvent(req.shopId, req.params.id, req.body, delivery.staffActor(req), keyOf(req));
  } catch (err) {
    if (err.current) return res.status(err.status).json({ error: { code: err.code, message: err.message, current: err.current } });
    throw err;
  }
  if (out.status === 'applied') {
    await log(req, 'delivery.status', 'delivery_assignment', out.assignment.id, {
      to: out.assignment.status, reason: out.assignment.statusReason,
      cashCollectedMinor: out.assignment.cashCollectedMinor,
    });
  }
  res.json(out);
});

/** لغو پس از حرکتِ پیک (۲۲.۲ C بندِ ۶). */
router.post('/assignments/:id/recall', requirePermission('delivery.assign'), needWrite, async (req, res) => {
  const out = await delivery.recall(req.shopId, req.params.id, req.body, delivery.staffActor(req), keyOf(req));
  if (!out.unchanged) {
    await log(req, 'delivery.recall', 'delivery_assignment', out.assignment.id, { reason: out.assignment.recall?.reason });
  }
  res.json(out);
});

// ── عکسِ تحویل: فقط صاحب و مدیر ──────────────────────────────────────

router.get('/assignments/:id/photos', requirePermission('delivery.photo'), async (req, res) => {
  res.json({ items: await delivery.listPhotos(req.shopId, req.params.id) });
});

router.get('/photos/:photoId', requirePermission('delivery.photo'), async (req, res) => {
  const p = await delivery.getPhoto(req.shopId, req.params.photoId);
  await log(req, 'delivery.photo.view', 'delivery_photo', req.params.photoId, { orderId: p.orderId });
  res.set('Content-Type', p.mime);
  res.set('Cache-Control', 'private, no-store');
  res.send(p.data);
});

// ── شرکتِ پیکِ بیرونی (DLV-19) ──────────────────────────────────────

/**
 * فهرستِ شرکت‌ها کرایه‌ها را نشان می‌دهد ⇒ فقط کسی که می‌سپارد (`delivery.assign`)،
 * نه هر نقشی که `delivery.view` دارد (صندوق‌دار، جمع‌کننده…).
 */
router.get('/external', requirePermission('delivery.assign'), async (req, res) => {
  res.json(await delivery.externalCompanies(req.shopId));
});

/** تطبیقِ صورت‌حساب: پول است ⇒ همان دسترسیِ تسویه (صاحب/مدیر). */
router.get('/external/statement', requirePermission('delivery.settle'), async (req, res) => {
  res.json(await delivery.externalStatement(req.shopId, { name: req.query.name, from: req.query.from, to: req.query.to }));
});

// ── تسویهٔ پیک ───────────────────────────────────────────────────────

router.get('/couriers/:id/settlement-preview', requirePermission('delivery.settle'), async (req, res) => {
  res.json(await delivery.settlementPreview(req.shopId, req.params.id));
});

router.post('/couriers/:id/settlements', requirePermission('delivery.settle'), needWrite, async (req, res) => {
  const key = delivery.eventKey(req.get('idempotency-key') || req.body?.clientKey);
  const out = await delivery.settle(req.shopId, req.params.id, req.body, delivery.staffActor(req), key);
  if (out.replayed) {
    res.set('Idempotent-Replayed', 'true');
    return res.json({ ...out });
  }
  await log(req, 'delivery.settle', 'courier_settlement', out.settlement.id, {
    courierId: out.settlement.courierId, count: out.settlement.assignmentsCount,
    handoverDueMinor: out.settlement.handoverDueMinor, receivedMinor: out.settlement.receivedMinor,
    shortageMinor: out.settlement.shortageMinor, excessMinor: out.settlement.excessMinor,
  });
  res.status(201).json(out);
});

router.get('/settlements', requirePermission('delivery.settle'), async (req, res) => {
  res.json(await delivery.listSettlements(req.shopId, {
    courierId: req.query.courierId, from: req.query.from, to: req.query.to, limit: req.query.limit,
  }));
});

//  RPT-15: نقدِ پیک‌ها و تسویه در یک بازه — فقط خواندن، همان اجازهٔ دفترِ تسویه
router.get('/cash-report', requirePermission('delivery.settle'), async (req, res) => {
  res.json(await cashReport(req.shopId, { from: req.query.from, to: req.query.to }));
});

router.get('/settlements/:id', requirePermission('delivery.settle'), async (req, res) => {
  res.json(await delivery.getSettlement(req.shopId, req.params.id));
});

module.exports = router;
