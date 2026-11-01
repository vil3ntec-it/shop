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
const onlineReport = require('../lib/order-online-report');
const S = require('../lib/order-states');
const audit = require('../lib/audit');
const { can } = require('../lib/permissions');
const { requireUser, requireShop, requirePermission } = require('../middleware/auth');
const { clientIp } = require('../middleware/ratelimit');
const { forbidden, conflict, notFound } = require('../middleware/errors');

const router = express.Router();
router.use(requireUser, requireShop, requirePermission('orders.view'));

const actorOf = (req) => ({ type: 'user', id: req.user.id, role: req.role });

/** پرچم لازم است (خواندن و نوشتن). */
const needFlag = async (req, res, next) => {
  if (!(await orders.flagOf(req.shopId))) {
    return next(forbidden('سفارشِ آنلاین برای این دکان روشن نیست', 'orders_disabled'));
  }
  next();
};

/** نوشتن: پرچم + کلیدِ اشتراک (همان پیام‌های `requireFeature`). */
const needWrite = async (req, res, next) => {
  const { entitled, ent } = await orders.entitledOf(req.shopId);
  if (!entitled) {
    const err = ent.trial.used && !ent.trial.active
      ? forbidden('اشتراکِ این دکان به پایان رسیده است.', 'subscription_expired')
      : forbidden('این قابلیت نیازمندِ اشتراک است', 'subscription_required');
    return next(err);
  }
  next();
};

router.get('/config', async (req, res) => {
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
    //  جمع‌آوری و بسته‌بندی (PCK، §۶.۱۷) — پیش‌فرض خاموش
    picking: await require('../lib/order-picking').flagOf(req.shopId),
    //  «با اسکناسِ … پرداخت می‌کنم» و ویرایشِ دستورِ تحویل تا پیش از ارسال (CHK-14، CHK-24) — پیش‌فرض خاموش
    deliveryPrefs: await require('../lib/delivery-prefs').flagOn(req.shopId),
    //  ترجیحِ کلیِ جایگزینی در فرمِ سفارشِ دستی و نامِ «کالای مشخص» (CHK-15) — پیش‌فرض خاموش
    substitution: await require('../lib/order-substitution').flagOn(req.shopId),
    tabs: S.TABS,
    reasons: S.REASONS,
    sources: orders.STAFF_SOURCES,
    //  ORD-07 «عقب انداختن» (پرچمِ `shop.orders.delay`) و ORD-03 نشانگرِ «باز / بسته» (پرچمِ
    //  `shop.orders.presence`) — خاموش ⇒ null/false و برنامه هیچ دکمه یا نشانگری نمی‌سازد
    delay: (await delay.flagOf(req.shopId)) ? delay.configOf() : null,
    presence: await presence.flagOf(req.shopId),
    //  ORD-14 ویرایشِ سفارش (پرچمِ `shop.orders.amend`) — خاموش ⇒ کلید نیست (همان پاسخِ پیشین)
    //  + سفارشِ پرداخت‌شده (پرچمِ جدای `shop.orders.amend_paid`): `paid` و روشِ تسویه؛ خاموش ⇒ همان شیءِ پیشین
    ...((await amend.flagOf(req.shopId))
      ? { amend: { ...amend.configOf(), ...((await amend.paidFlagOf(req.shopId)) ? { paid: true, settleMethods: [...amend.SETTLE_METHODS] } : {}) } }
      : {}),
    //  ORD-19 کارِ گروهی (پرچمِ `shop.orders.bulk`) — خاموش ⇒ کلید نیست و برنامه انتخابِ چندتایی نمی‌سازد
    ...((await require('../lib/order-bulk').flagOf(req.shopId)) ? { bulk: true } : {}),
    //  ORD-16 «فرستادنِ دوبارهٔ» پیامِ نرفته (پرچمِ `shop.orders.notice_resend`) — خاموش ⇒ کلید نیست
    ...((await require('../lib/notice-resend').flagOf(req.shopId)) ? { noticeResend: require('../lib/notice-resend').configOf() } : {}),
    //  CHK-10/S34: فهرستِ «خبرم کن» ِ ویترین (پرچمِ `shop.storefront.notify_area` روی ناحیه‌ها) فقط برای صاحب و مدیر
    //  (`settings.write`، همان درِ `GET /storefront/area-requests`) — خاموش یا نقشِ دیگر ⇒ کلید نیست (همان پاسخِ پیشین)
    ...(can(req.role, 'settings.write') && (await require('../lib/storefront-area-requests').flagOn(req.shopId)) ? { notifyArea: true } : {}),
    //  «فروشِ تحویل در برنامه» (تصمیمِ صاحبِ مخزن): برنامه فقط وقتی سرور این را می‌گوید فروش
    //  می‌سازد (سرورِ کهنه هنوز خودش کم می‌کرد ⇒ دو بار)؛ تحویلِ پیش از `handoverSince` هرگز.
    saleOnHandover: true,
    handoverSince,
    permissions: {
      ...Object.fromEntries(['create', 'manage', 'contact', 'refund'].map((k) => [k, can(req.role, `orders.${k}`)])),
      //  PCK-13/14: «شاخص‌ها» و «پیدا نشد» در میزِ جمع‌آوری — همان اجازهٔ درهای خودشان (فقط برای نمایش)
      pickMetrics: can(req.role, 'reports.view'), pickIssues: can(req.role, 'stock.adjust'),
      //  اجازهٔ کلیِ نقش و اشتراک (برای نمایش)؛ تصمیمِ هر سفارش `recordable`/`reversible` ِ `GET /handover` است
      ...(await ledger.capsFor(req.shopId, req.role)),
    },
  });
});

router.get('/summary', needFlag, async (req, res) => {
  const out = await orders.summary(req.shopId);
  //  ORD-03: همان «باز / بسته» که مشتری در ویترین می‌بیند، در همان پرسشِ ۲۰ثانیه‌ای
  if (await presence.flagOf(req.shopId)) out.presence = await presence.presenceOf(req.shopId, out.serverTime);
  //  ORD-18: هشدارهای «شروعِ سفارشِ زمان‌بندی‌شده» که هنوز شروع نشده‌اند (پرچمِ `shop.orders.start_alert`؛ خاموش ⇒ بی کلید)
  const sa = require('../lib/order-start-alert');
  if (await sa.flagOf(req.shopId)) out.startAlerts = await sa.pendingOf(req.shopId, out.serverTime);
  res.json(out);
});

router.get('/', needFlag, async (req, res) => {
  res.json(await orders.list(req.shopId, {
    tab: String(req.query.tab || 'new'), limit: req.query.limit, cursor: req.query.cursor,
  }, actorOf(req)));
});

/**
 * تطبیقِ دریافت و بازپرداخت (§۳۸.۷): جمع‌های بازه + صفِ اختلاف‌ها.
 * فقط‌خواندنی؛ هر کس بازپرداخت می‌کند (صاحب و مدیر) آن را می‌بیند.
 * پیش از `/:id` تا با شناسهٔ سفارش اشتباه نشود.
 */
router.get('/reconciliation', needFlag, requirePermission('orders.refund'), async (req, res) => {
  const out = await refunds.report(req.shopId, { from: req.query.from, to: req.query.to });
  //  ORD-14: تسویهٔ ویرایشِ سفارشِ پرداخت‌شده — بدهیِ باز و تسویهٔ بازه (پرچمِ `shop.orders.amend_paid`؛ خاموش ⇒ بی کلید)
  if (await amend.paidFlagOf(req.shopId)) out.amendSettlements = await amend.settlementsOf(req.shopId, out.window);
  res.json(out);
});

/**
 * RPT-14 — گزارشِ فروشِ اینترنتی (`lib/order-online-report.js`): قیفِ سفارش، زمانِ قبول،
 * آماده‌سازی و تحویل، نرخِ ازدست‌رفته و ناموفق، و جست‌وجوهای بی‌نتیجهٔ ویترین. فقط‌خواندنی؛
 * هر کس گزارش می‌بیند (`reports.view`). پرچمِ خاموشِ `shop.orders.online_report` ⇒ ۴۰۳. پیش از `/:id`.
 */
router.get('/online-report', needFlag, requirePermission('reports.view'), async (req, res) => {
  if (!(await onlineReport.flagOf(req.shopId))) {
    throw forbidden('گزارشِ فروشِ اینترنتی برای این دکان روشن نیست', 'online_report_disabled');
  }
  res.json(await onlineReport.report(req.shopId, { from: req.query.from, to: req.query.to }));
});

/**
 * سفارش‌های تحویل‌شده‌ای که فروششان هنوز در دفترِ دکان نیست (`lib/order-ledger.js`) —
 * برنامهٔ دکان برای هر کدام فروشِ عادی با شناسهٔ ثابتِ `o-<سفارش>` می‌سازد؛ و بازپرداخت‌هایی
 * که سندِ برگشتشان (`o-<سفارش>-rf-<کلید>`) هنوز در دفتر ننشسته (`refunds`). فقط‌خواندنی؛
 * همان کسی که سفارش را تحویل می‌دهد (`orders.manage`). پیش از `/:id`.
 */
router.get('/handover', needFlag, requirePermission('orders.manage'), async (req, res) => {
  res.json(await require('../lib/order-ledger').handoverPending(req.shopId, {
    since: req.query.since, limit: req.query.limit, after: req.query.after, orderId: req.query.orderId,
  }, actorOf(req)));
});

/**
 * «بررسی شد» ِ صاحب یا مدیر برای ردیفِ بررسیِ فروشِ تحویل (`before_floor`، `reverse_blocked`) —
 * `lib/order-ledger.ackReview`؛ ردیف برای همهٔ دستگاه‌ها می‌رود. تکرار بی‌اثر. مثلِ هر نوشتنِ
 * دیگرِ سفارش پشتِ `needWrite` (SUB-10).
 */
router.post('/:id/review-ack', needFlag, requirePermission('orders.refund'), needWrite, async (req, res) => {
  res.json(await require('../lib/order-ledger').ackReview(req.shopId, req.params.id, req.body?.kind, actorOf(req)));
});

/**
 * TEN-03 — «باطل کردنِ لینکِ پیگیری» ِ سفارشِ ویترین (پرچمِ `shop.storefront.link_expiry`):
 * هر لینکِ پیشینِ مشتری ۴۱۰ `link_revoked` می‌گیرد و لینکِ تازه برمی‌گردد تا دکان دوباره بفرستد.
 * فقط صاحب و مدیر (`orders.contact`، همان‌ها که شمارهٔ مشتری را می‌بینند)؛ مثلِ هر نوشتنِ
 * سفارش پشتِ `needWrite` (SUB-10، دستهٔ PAID).
 */
router.post('/:id/track-link/revoke', needFlag, requirePermission('orders.contact'), needWrite, async (req, res) => {
  const out = await require('../lib/storefront').revokeLink(req.shopId, String(req.params.id || '').slice(0, 40));
  await audit.log({
    shopId: req.shopId, userId: req.user.id, action: 'order.track_link_revoked',
    targetType: 'online_order', targetId: String(req.params.id), detail: { gen: out.gen }, ip: clientIp(req),
  });
  res.json({ ok: true, token: out.token });
});

router.get('/:id', needFlag, async (req, res) => {
  const order = await orders.get(req.shopId, req.params.id, actorOf(req));
  //  دفترِ بازپرداختِ همین سفارش — مبلغِ برگشته، مانده و اختلاف (FIN-01)
  order.refund = await refunds.summary(req.shopId, order.id);
  //  ORD-14: آخرین پیشنهاد/ویرایش — فقط با پرچمِ `shop.orders.amend` (خاموش ⇒ همان پاسخِ پیشین)
  if (await amend.flagOf(req.shopId)) order.amendment = await amend.staffOf(req.shopId, order.id);
  res.json({ order });
});

/**
 * NTF-01/04: پیام‌های وضعیت به مشتریِ همین سفارش — کدام رویداد، از کدام کانال، چه شد — و حالِ
 * کانال‌ها. فقط خواندن؛ بی شماره، متن یا لینک. سفارشِ دکانِ دیگر ⇒ ۴۰۴.
 */
router.get('/:id/notices', needFlag, async (req, res) => {
  const out = await require('../lib/customer-notify').forOrder(req.shopId, req.params.id);
  if (!out) throw notFound('سفارش پیدا نشد', 'order_not_found');
  res.json(out);
});

/**
 * ORD-16 — «فرستادنِ دوبارهٔ» پیامِ نرفتهٔ همین سفارش (`lib/notice-resend.js`)، پشتِ پرچمِ خاموشِ
 * `shop.orders.notice_resend`، با `orders.manage` و اشتراک (SUB-10). کلیدِ یکتایی لازم است (تکرار ⇒ همان پاسخ).
 */
router.post('/:id/notices/:noticeId/resend', needFlag, requirePermission('orders.manage'), needWrite, async (req, res) => {
  const nr = require('../lib/notice-resend');
  if (!(await nr.flagOf(req.shopId))) throw forbidden('فرستادنِ دوبارهٔ پیام برای این دکان روشن نیست', 'notice_resend_disabled');
  const key = orders.clientKeyOf(req);
  let out;
  try {
    out = await nr.resend(req.shopId, req.params.id, req.params.noticeId, { key, actor: actorOf(req) });
  } catch (err) {
    if (err.status === 409 && err.code !== 'idempotency_key_reused') {
      return res.status(409).json({ error: { code: err.code, message: err.message, current: err.current, max: err.max } });
    }
    throw err;
  }
  if (out.replayed) res.set('Idempotent-Replayed', 'true');
  else {
    await audit.log({
      shopId: req.shopId, userId: req.user.id, action: 'order.notice_resend', targetType: 'online_order', targetId: String(req.params.id),
      detail: { orderNo: out.orderNo, noticeId: out.notice.id, event: out.notice.event, prevStatus: out.prevStatus },
      ip: clientIp(req),
    });
  }
  res.json({ notice: out.notice, replayed: out.replayed });
});

/**
 * سفارشِ دستیِ کارمند — تلفنی، تلگرام، واتس‌اپ یا دستی (ORD-21)؛ با
 * `test: true` «سفارشِ آزمایشی» است تا صف، هشدار و چاپ بی مشتریِ واقعی
 * آزموده شوند. کلیدِ یکتایی لازم است (CHK-19).
 */
router.post('/', needFlag, requirePermission('orders.create'), needWrite, async (req, res) => {
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
});

router.post('/:id/transition', needFlag, requirePermission('orders.manage'), needWrite, async (req, res) => {
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
});

/**
 * ویرایشِ دستورِ تحویل تا پیش از ارسال (CHK-24) — «پیش از رسیدن زنگ بزنید»، «تحویل به…»، نشانه و
 * مبلغِ اسکناسِ مشتری (CHK-14). پشتِ پرچمِ خاموشِ `shop.orders.delivery_prefs` (۴۰۳ در خودِ lib)؛
 * مثلِ هر نوشتنِ دیگرِ سفارش پشتِ `needWrite` (SUB-10). `expectedVersion` ِ ناهمخوان ⇒ ۴۰۹.
 * POST و نه PATCH: `HttpURLConnection` ِ برنامهٔ کامپیوتر PATCH نمی‌فرستد.
 */
router.post('/:id/delivery', needFlag, requirePermission('orders.manage'), needWrite, async (req, res) => {
  const out = await require('../lib/delivery-prefs').edit(req.shopId, req.params.id, req.body || {}, actorOf(req));
  if (!out.unchanged) {
    await audit.log({
      shopId: req.shopId, userId: req.user.id, action: 'order.delivery_edit',
      targetType: 'online_order', targetId: out.order.id,
      detail: { orderNo: out.order.orderNo, changed: out.changed },
      ip: clientIp(req),
    });
  }
  res.json({ order: out.order, unchanged: out.unchanged });
});

/**
 * ثبتِ بازپرداخت با مبلغ (§۳۸.۲/۳۸.۷، FIN-01) — پشتِ پرچمِ خاموشِ
 * `shop.orders.refunds`. کلیدِ یکتایی لازم است؛ تکرارِ همان درخواست
 * ردیفِ دوم نمی‌سازد. سفارش باید «درخواستِ برگشت» باشد.
 */
const needLedger = async (req, res, next) => {
  if (!(await refunds.flagOf(req.shopId))) {
    return next(forbidden('دفترِ بازپرداخت برای این دکان روشن نیست', 'refund_ledger_disabled'));
  }
  next();
};

router.post('/:id/refunds', needFlag, requirePermission('orders.refund'), needWrite, needLedger, async (req, res) => {
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
});

/**
 * ORD-07 — عقب انداختنِ زمانِ آماده‌شدنِ سفارشِ قبول‌شده (`lib/order-delay.js`) — پشتِ پرچمِ
 * خاموشِ `shop.orders.delay`، با `orders.manage` و اشتراک (SUB-10: نوشتنِ پولی). کلیدِ یکتایی
 * لازم است؛ تکرارِ همان درخواست دوباره عقب نمی‌برد. `expectedReadyAt` ِ کهنه ⇒ ۴۰۹ `ready_changed`.
 */
const needDelay = async (req, res, next) => {
  if (!(await delay.flagOf(req.shopId))) {
    return next(forbidden('عقب انداختنِ سفارش برای این دکان روشن نیست', 'order_delay_disabled'));
  }
  next();
};

router.post('/:id/delay', needFlag, requirePermission('orders.manage'), needWrite, needDelay, async (req, res) => {
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
});

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
 * ORD-14 — تسویهٔ تفاوتِ ویرایشِ سفارشِ **پرداخت‌شده** (`amend.settle`؛ پرچمِ خاموشِ `shop.orders.amend_paid`،
 * ۴۰۳ در خودِ lib): فقط ثبت، نقد، بی درگاه؛ پول جابه‌جا می‌شود ⇒ `orders.refund` (صاحب و مدیر) و اشتراک.
 */
router.post('/:id/amendments/:amendId/settlement', needFlag, requirePermission('orders.refund'), needWrite, needAmend, async (req, res) => {
  const key = orders.clientKeyOf(req);
  const out = await amend.settle(req.shopId, req.params.id, req.params.amendId, { key, body: req.body, actor: actorOf(req) });
  if (out.replayed) res.set('Idempotent-Replayed', 'true');
  else {
    await audit.log({
      shopId: req.shopId, userId: req.user.id, action: 'order.amend_settle', targetType: 'online_order', targetId: out.order.id,
      detail: { orderNo: out.orderNo, amendId: req.params.amendId, kind: out.kind, amountMinor: out.amountMinor, method: req.body.method },
      ip: clientIp(req),
    });
  }
  res.json({ amendment: out.amendment, order: out.order, replayed: out.replayed });
});

/**
 * RSK-03: سابقهٔ همین مشتری در همین دکان (تحویل، رد، لغو، تحویل‌نشده، برگشت) و نشانه‌های توضیح‌دار
 * پیش از ارسال — پشتِ پرچمِ `shop.order_risk` (خاموش ⇒ `{enabled:false}`). فقط خواندن؛ بی شماره.
 * سفارشِ دکانِ دیگر ⇒ ۴۰۴.
 */
router.get('/:id/risk', needFlag, async (req, res) => {
  const out = await require('../lib/order-risk').riskOf(req.shopId, req.params.id);
  if (!out) throw require('../middleware/errors').notFound('سفارش پیدا نشد', 'order_not_found');
  res.json(out);
});

//  جمع‌آوری و بسته‌بندی (PCK، §۶.۱۷) — پشتِ پرچمِ خاموشِ `shop.orders.picking`؛ مسیرها همه
//  دو بخشی یا بیشترند (`/pick/…`، `/:id/pick…`) و با `/:id`ِ بالا قاطی نمی‌شوند.
router.use(require('./order-picking'));

/**
 * RSK-04: فهرستِ سیاه و سفیدِ ویترینِ همین دکان (شماره یا پین، با دلیل و انقضا) — فقط صاحب و مدیر
 * (`orders.contact`، همان‌ها که شمارهٔ کامل را می‌بینند). نوشتن مثلِ هر نوشتنِ سفارش پشتِ `needWrite`
 * (SUB-10، دستهٔ PAID). پرچمِ `shop.order_risk` خاموش ⇒ ۴۰۳ `order_risk_disabled` (پس از سدِ اشتراک).
 * ثبت و برداشتن در سابقهٔ عملیات، بی شماره و بی دلیل.
 */
const riskLists = () => require('../lib/risk-lists');
const needRisk = async (req) => {
  if (!(await require('../lib/order-risk').flagIn((t, p) => require('../db').query(t, p), req.shopId))) {
    throw forbidden('ریسکِ سفارش برای این دکان روشن نیست', 'order_risk_disabled');
  }
};

router.get('/risk/lists', needFlag, requirePermission('orders.contact'), async (req, res) => {
  await needRisk(req);
  res.json(await riskLists().list(req.shopId));
});

router.post('/risk/lists', needFlag, requirePermission('orders.contact'), needWrite, async (req, res) => {
  await needRisk(req);
  const entry = await riskLists().add(req.shopId, req.body, req.user.id);
  await audit.log({
    shopId: req.shopId, userId: req.user.id, action: 'order.risk_list_add', targetType: 'risk_list', targetId: entry.id,
    detail: { list: entry.list, kind: entry.kind, expiresAt: entry.expiresAt }, ip: clientIp(req),
  });
  res.status(201).json({ entry });
});

router.delete('/risk/lists/:entryId', needFlag, requirePermission('orders.contact'), needWrite, async (req, res) => {
  await needRisk(req);
  const out = await riskLists().remove(req.shopId, req.params.entryId, req.user.id);
  if (out.removed) {
    await audit.log({
      shopId: req.shopId, userId: req.user.id, action: 'order.risk_list_remove', targetType: 'risk_list', targetId: out.id,
      detail: {}, ip: clientIp(req),
    });
  }
  res.json(out);
});

module.exports = router;
