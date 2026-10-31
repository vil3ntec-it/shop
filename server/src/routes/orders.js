'use strict';
/**
 * بخشِ «سفارش‌ها»ی برنامهٔ دکان — `/api/shop/orders` (فاز ۴، بخشِ ۳.۹).
 *
 * ⛔ پیش‌فرض خاموش: بی پرچمِ `shop.orders` در «تنظیماتِ زنده» فقط
 * `GET /config` جواب می‌دهد (تا برنامه بداند بخش را نشان ندهد).
 * ⛔ خواندن با اشتراکِ تمام‌شده هم باز است (داده گروگان نیست)؛ نوشتن
 * کلیدِ اشتراکِ `online_orders` را هم می‌خواهد.
 * ⛔ shop_id فقط از عضویتِ کاربرِ توکن می‌آید، هرگز از درخواست.
 *
 * زیرِ پیشوندِ موجودِ `/shop` است، پس `ACCOUNT_PREFIXES`ِ پنلِ خانگی
 * چیزِ تازه‌ای نمی‌خواهد.
 */
const express = require('express');
const orders = require('../lib/orders');
const refunds = require('../lib/order-refunds');
const delay = require('../lib/order-delay');
const amend = require('../lib/order-amend');
const presence = require('../lib/order-presence');
const S = require('../lib/order-states');
const audit = require('../lib/audit');
const { can } = require('../lib/permissions');
const { requireUser, requireShop, requirePermission } = require('../middleware/auth');
const { clientIp } = require('../middleware/ratelimit');
const { forbidden, conflict } = require('../middleware/errors');

const router = express.Router();
router.use(requireUser, requireShop, requirePermission('orders.view'));

const actorOf = (req) => ({ type: 'user', id: req.user.id, role: req.role });
const wrap = (fn) => (req, res, next) => Promise.resolve(fn(req, res, next)).catch(next);

/** پرچم لازم است (خواندن و نوشتن). */
const needFlag = wrap(async (req, res, next) => {
  if (!(await orders.flagOf(req.shopId))) {
    return next(forbidden('سفارشِ آنلاین برای این دکان روشن نیست', 'orders_disabled'));
  }
  next();
});

/** نوشتن: پرچم + کلیدِ اشتراک (همان پیام‌های `requireFeature`). */
const needWrite = wrap(async (req, res, next) => {
  const { entitled, ent } = await orders.entitledOf(req.shopId);
  if (!entitled) {
    const err = ent.trial.used && !ent.trial.active
      ? forbidden('اشتراکِ این دکان به پایان رسیده است.', 'subscription_expired')
      : forbidden('این قابلیت نیازمندِ اشتراک است', 'subscription_required');
    return next(err);
  }
  next();
});

router.get('/config', wrap(async (req, res) => {
  const st = await orders.stateOf(req.shopId);
  const ledger = require('../lib/order-ledger');
  //  برنامه‌ای که «فروشِ تحویل» دارد (`caps=saleOnHandover`) ⇒ کفِ تحویلِ این دکان (یک بار، همین
  //  لحظه)؛ برنامهٔ کهنه یا پنل ⇒ فقط خواندن (تا هیچ برنامه‌ای کف نگذاشته: null ⇒ هیچ فروشی)
  const caps = String(req.query.caps || '').split(',').map((s) => s.trim());
  const handoverSince = caps.includes(ledger.SALE_CAP)
    ? await ledger.claimFloor(req.shopId, req.user.id)
    : await ledger.sinceOf(req.shopId);
  res.json({
    ...st,
    //  دفترِ بازپرداخت (§۳۸.۷، FIN-01) — پیش‌فرض خاموش
    refundLedger: await refunds.flagOf(req.shopId),
    tabs: S.TABS,
    reasons: S.REASONS,
    sources: orders.STAFF_SOURCES,
    //  ORD-07 «عقب انداختن» (پرچمِ `shop.orders.delay`) و ORD-03 نشانگرِ «باز / بسته» (پرچمِ
    //  `shop.orders.presence`) — خاموش ⇒ null/false و برنامه هیچ دکمه یا نشانگری نمی‌سازد
    delay: (await delay.flagOf(req.shopId)) ? delay.configOf() : null,
    presence: await presence.flagOf(req.shopId),
    //  ORD-14 ویرایشِ سفارش (پرچمِ `shop.orders.amend`) — خاموش ⇒ کلید نیست (همان پاسخِ پیشین)
    ...((await amend.flagOf(req.shopId)) ? { amend: amend.configOf() } : {}),
    //  «فروشِ تحویل در برنامه» (تصمیمِ صاحبِ مخزن): برنامه فقط وقتی سرور این را می‌گوید فروش
    //  می‌سازد (سرورِ کهنه هنوز خودش کم می‌کرد ⇒ دو بار)؛ تحویلِ پیش از `handoverSince` هرگز.
    saleOnHandover: true,
    handoverSince,
    permissions: {
      ...Object.fromEntries(['create', 'manage', 'contact', 'refund'].map((k) => [k, can(req.role, `orders.${k}`)])),
      //  اجازهٔ کلیِ نقش و اشتراک (برای نمایش)؛ تصمیمِ هر سفارش `recordable`/`reversible` ِ `GET /handover` است
      ...(await ledger.capsFor(req.shopId, req.role)),
    },
  });
}));

router.get('/summary', needFlag, wrap(async (req, res) => {
  const out = await orders.summary(req.shopId);
  //  ORD-03: همان «باز / بسته» که مشتری در ویترین می‌بیند، در همان پرسشِ ۲۰ثانیه‌ای
  if (await presence.flagOf(req.shopId)) out.presence = await presence.presenceOf(req.shopId, out.serverTime);
  res.json(out);
}));

router.get('/', needFlag, wrap(async (req, res) => {
  res.json(await orders.list(req.shopId, {
    tab: String(req.query.tab || 'new'), limit: req.query.limit, cursor: req.query.cursor,
  }, actorOf(req)));
}));

/**
 * تطبیقِ دریافت و بازپرداخت (§۳۸.۷): جمع‌های بازه + صفِ اختلاف‌ها.
 * فقط‌خواندنی؛ هر کس بازپرداخت می‌کند (صاحب و مدیر) آن را می‌بیند.
 * پیش از `/:id` تا با شناسهٔ سفارش اشتباه نشود.
 */
router.get('/reconciliation', needFlag, requirePermission('orders.refund'), wrap(async (req, res) => {
  res.json(await refunds.report(req.shopId, { from: req.query.from, to: req.query.to }));
}));

/**
 * سفارش‌های تحویل‌شده‌ای که فروششان هنوز در دفترِ دکان نیست (`lib/order-ledger.js`) —
 * برنامهٔ دکان برای هر کدام فروشِ عادی با شناسهٔ ثابتِ `o-<سفارش>` می‌سازد. فقط‌خواندنی؛
 * همان کسی که سفارش را تحویل می‌دهد (`orders.manage`). پیش از `/:id`.
 */
router.get('/handover', needFlag, requirePermission('orders.manage'), wrap(async (req, res) => {
  res.json(await require('../lib/order-ledger').handoverPending(req.shopId, {
    since: req.query.since, limit: req.query.limit, after: req.query.after, orderId: req.query.orderId,
  }, actorOf(req)));
}));

/**
 * «بررسی شد» ِ صاحب یا مدیر برای ردیفِ بررسیِ فروشِ تحویل (`before_floor`، `reverse_blocked`) —
 * `lib/order-ledger.ackReview`؛ ردیف برای همهٔ دستگاه‌ها می‌رود. تکرار بی‌اثر. مثلِ هر نوشتنِ
 * دیگرِ سفارش پشتِ `needWrite` (SUB-10).
 */
router.post('/:id/review-ack', needFlag, requirePermission('orders.refund'), needWrite, wrap(async (req, res) => {
  res.json(await require('../lib/order-ledger').ackReview(req.shopId, req.params.id, req.body?.kind, actorOf(req)));
}));

router.get('/:id', needFlag, wrap(async (req, res) => {
  const order = await orders.get(req.shopId, req.params.id, actorOf(req));
  //  دفترِ بازپرداختِ همین سفارش — مبلغِ برگشته، مانده و اختلاف (FIN-01)
  order.refund = await refunds.summary(req.shopId, order.id);
  //  ORD-14: آخرین پیشنهاد/ویرایش — فقط با پرچمِ `shop.orders.amend` (خاموش ⇒ همان پاسخِ پیشین)
  if (await amend.flagOf(req.shopId)) order.amendment = await amend.staffOf(req.shopId, order.id);
  res.json({ order });
}));

/**
 * سفارشِ دستیِ کارمند — تلفنی، تلگرام، واتس‌اپ یا دستی (ORD-21)؛ با
 * `test: true` «سفارشِ آزمایشی» است تا صف، هشدار و چاپ بی مشتریِ واقعی
 * آزموده شوند. کلیدِ یکتایی لازم است (CHK-19).
 */
router.post('/', needFlag, requirePermission('orders.create'), needWrite, wrap(async (req, res) => {
  const key = orders.clientKeyOf(req);
  const out = await orders.create(req.shopId, { key, body: req.body, actor: actorOf(req) });
  if (out.replayed) {
    res.set('Idempotent-Replayed', 'true');
    return res.status(200).json({ order: out.order, replayed: true });
  }
  await audit.log({
    shopId: req.shopId, userId: req.user.id, action: 'order.create',
    targetType: 'online_order', targetId: out.order.id,
    detail: { orderNo: out.order.orderNo, source: out.order.source, isTest: out.order.isTest, totalMinor: out.order.totalMinor },
    ip: clientIp(req),
  });
  res.status(201).json({ order: out.order });
}));

router.post('/:id/transition', needFlag, requirePermission('orders.manage'), needWrite, wrap(async (req, res) => {
  //  دفترِ بازپرداخت روشن ⇒ «بازپرداخت‌شده» بی مبلغ پذیرفته نیست؛ فقط از راهِ `/refunds`
  if (S.MONEY_TARGETS.includes(String(req.body?.to || '').trim()) && (await refunds.flagOf(req.shopId))) {
    throw conflict('بازپرداخت را با مبلغش از «ثبتِ بازپرداخت» بزنید', 'refund_via_ledger');
  }
  let out;
  try {
    out = await orders.transition(req.shopId, req.params.id, req.body || {}, actorOf(req));
  } catch (err) {
    if (err.code === 'status_changed') {
      return res.status(409).json({ error: { code: err.code, message: err.message, current: err.current } });
    }
    throw err;
  }
  if (!out.unchanged) {
    await audit.log({
      shopId: req.shopId, userId: req.user.id, action: 'order.transition',
      targetType: 'online_order', targetId: out.order.id,
      detail: { orderNo: out.order.orderNo, from: out.from, to: out.to, reason: out.order.statusReason },
      ip: clientIp(req),
    });
  }
  res.json({ order: out.order, unchanged: out.unchanged });
}));

/**
 * ثبتِ بازپرداخت با مبلغ (§۳۸.۲/۳۸.۷، FIN-01) — پشتِ پرچمِ خاموشِ
 * `shop.orders.refunds`. کلیدِ یکتایی لازم است؛ تکرارِ همان درخواست
 * ردیفِ دوم نمی‌سازد. سفارش باید «درخواستِ برگشت» باشد.
 */
const needLedger = wrap(async (req, res, next) => {
  if (!(await refunds.flagOf(req.shopId))) {
    return next(forbidden('دفترِ بازپرداخت برای این دکان روشن نیست', 'refund_ledger_disabled'));
  }
  next();
});

router.post('/:id/refunds', needFlag, requirePermission('orders.refund'), needWrite, needLedger, wrap(async (req, res) => {
  const key = orders.clientKeyOf(req);
  let out;
  try {
    out = await refunds.record(req.shopId, req.params.id, { key, body: req.body, actor: actorOf(req) });
  } catch (err) {
    if (err.code === 'refund_exceeds_remaining') {
      return res.status(409).json({ error: { code: err.code, message: err.message, remainingMinor: err.remainingMinor } });
    }
    throw err;
  }
  const order = await orders.get(req.shopId, req.params.id, actorOf(req));
  order.refund = await refunds.summary(req.shopId, order.id);
  if (out.replayed) {
    res.set('Idempotent-Replayed', 'true');
    return res.status(200).json({ order, entry: out.entry, replayed: true });
  }
  await audit.log({
    shopId: req.shopId, userId: req.user.id, action: 'order.refund',
    targetType: 'online_order', targetId: order.id,
    detail: { orderNo: order.orderNo, amountMinor: out.entry.amountMinor, seq: out.entry.seq, from: out.from, to: out.to },
    ip: clientIp(req),
  });
  res.status(201).json({ order, entry: out.entry });
}));

/**
 * ORD-07 — عقب انداختنِ زمانِ آماده‌شدنِ سفارشِ قبول‌شده (`lib/order-delay.js`) — پشتِ پرچمِ
 * خاموشِ `shop.orders.delay`، با `orders.manage` و اشتراک (SUB-10: نوشتنِ پولی). کلیدِ یکتایی
 * لازم است؛ تکرارِ همان درخواست دوباره عقب نمی‌برد. `expectedReadyAt` ِ کهنه ⇒ ۴۰۹ `ready_changed`.
 */
const needDelay = wrap(async (req, res, next) => {
  if (!(await delay.flagOf(req.shopId))) {
    return next(forbidden('عقب انداختنِ سفارش برای این دکان روشن نیست', 'order_delay_disabled'));
  }
  next();
});

router.post('/:id/delay', needFlag, requirePermission('orders.manage'), needWrite, needDelay, wrap(async (req, res) => {
  const key = orders.clientKeyOf(req);
  let out;
  try {
    out = await delay.delay(req.shopId, req.params.id, { key, body: req.body, actor: actorOf(req) });
  } catch (err) {
    if (err.code === 'ready_changed' || err.code === 'delay_not_allowed') {
      return res.status(409).json({ error: { code: err.code, message: err.message, current: err.current } });
    }
    throw err;
  }
  if (out.replayed) {
    res.set('Idempotent-Replayed', 'true');
    return res.status(200).json({ order: out.order, replayed: true });
  }
  await audit.log({
    shopId: req.shopId, userId: req.user.id, action: 'order.delay',
    targetType: 'online_order', targetId: out.order.id,
    detail: {
      orderNo: out.orderNo, readyAt: out.readyAt, prevReadyAt: out.prevReadyAt,
      minutes: req.body.minutes, reason: String(req.body.reason || '').trim(),
    },
    ip: clientIp(req),
  });
  res.json({ order: out.order, replayed: false });
}));

/**
 * ORD-14 — ویرایشِ سفارش پس از ثبت (`lib/order-amend.js`) پشتِ پرچمِ خاموشِ `shop.orders.amend`، با
 * `orders.manage` و اشتراک (SUB-10). سفارشِ ویترین ⇒ پیشنهاد که خودِ مشتری با پیوندِ پیگیری می‌پذیرد
 * (۲۰۱، `status: pending`)؛ سفارشِ کارمند ⇒ با رضایتِ ثبت‌شده همان دم (`status: applied`). کلیدِ یکتایی لازم است.
 */
async function needAmend(req, res, next) {
  if (!(await amend.flagOf(req.shopId))) {
    return next(forbidden('ویرایشِ سفارش برای این دکان روشن نیست', 'order_amend_disabled'));
  }
  next();
}

router.post('/:id/amendments', needFlag, requirePermission('orders.manage'), needWrite, needAmend, async (req, res) => {
  const key = orders.clientKeyOf(req);
  let out;
  try {
    out = await amend.propose(req.shopId, req.params.id, { key, body: req.body, actor: actorOf(req) });
  } catch (err) {
    if (err.code === 'amend_not_allowed' || err.code === 'amend_paid_order') {
      return res.status(409).json({ error: { code: err.code, message: err.message, current: err.current } });
    }
    throw err;
  }
  if (out.replayed) {
    res.set('Idempotent-Replayed', 'true');
    return res.status(200).json({ amendment: out.amendment, order: out.order, replayed: true });
  }
  await audit.log({
    shopId: req.shopId, userId: req.user.id, action: 'order.amend',
    targetType: 'online_order', targetId: out.order.id,
    detail: {
      orderNo: out.order.orderNo, amendId: out.amendment.id, status: out.amendment.status,
      prevTotalMinor: out.amendment.prevTotalMinor, totalMinor: out.amendment.totalMinor,
      consent: out.amendment.consent ? out.amendment.consent.via : 'link',
    },
    ip: clientIp(req),
  });
  res.status(201).json({ amendment: out.amendment, order: out.order, replayed: false });
});

router.post('/:id/amendments/:amendId/withdraw', needFlag, requirePermission('orders.manage'), needWrite, needAmend, async (req, res) => {
  const out = await amend.decide(req.shopId, req.params.id, req.params.amendId, 'withdraw', actorOf(req));
  if (out.code) {
    return res.status(409).json({ error: { code: out.code, message: 'این پیشنهاد دیگر باز نیست', status: out.status } });
  }
  res.json({ status: out.status, unchanged: out.unchanged, order: await orders.get(req.shopId, req.params.id, actorOf(req)) });
});

/**
 * RSK-03: سابقهٔ همین مشتری در همین دکان (تحویل، رد، لغو، تحویل‌نشده، برگشت) و نشانه‌های توضیح‌دار
 * پیش از ارسال — پشتِ پرچمِ `shop.order_risk` (خاموش ⇒ `{enabled:false}`). فقط خواندن؛ بی شماره.
 * سفارشِ دکانِ دیگر ⇒ ۴۰۴.
 */
router.get('/:id/risk', needFlag, wrap(async (req, res) => {
  const out = await require('../lib/order-risk').riskOf(req.shopId, req.params.id);
  if (!out) throw require('../middleware/errors').notFound('سفارش پیدا نشد', 'order_not_found');
  res.json(out);
}));

module.exports = router;
