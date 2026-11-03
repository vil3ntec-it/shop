'use strict';
/**
 * «به فاکتور» ِ یک‌ضربِ سفارشِ آنلاین — `POST /api/shop/orders/:id/invoice` (`lib/order-flow.claimInvoice`).
 *
 * سرور فقط «فاکتور شد» را یک بار و زیرِ قفلِ ردیف می‌نشاند (رویدادِ `note` ِ `invoice`، فقط از «بسته شد» به بعد)؛
 * «دریافت از دکان» ِ پرداخت‌نشده فقط همراهِ «مشتری گرفت» (از «بسته شد» ۴۰۹ `invoice_at_handover`؛ از «آمادهٔ دریافت»
 * همین درخواست تحویل می‌دهد؛ زمانِ فاکتور = زمانِ تحویل).
 * فروش را برنامهٔ دکان با شناسهٔ ثابتِ `o-<سفارش>` و زمانِ همین پاسخ می‌نویسد (`OrderFulfillment.fulfill`) ⇒ دو
 * بار زدن یا دو دستگاه یک فاکتور و یک بار کسرِ موجودی.
 *
 * ⛔ سقف: سطلِ IP (`order-invoice-ip`) پیش از احراز. ⛔ shop_id فقط از عضویتِ کاربرِ توکن.
 * ⛔ پشتِ همان پرچمِ `shop.orders` (خاموش ⇒ ۴۰۳ `orders_disabled`) و نوشتن با کلیدِ اشتراکِ `online_orders`
 *    (SUB-10؛ همان پیام‌های `routes/orders.js`)؛ `orders.manage` **و** همان اجازهٔ ثبتِ فروشِ همین سفارش که دکمهٔ
 *    برنامه را نشان می‌دهد (`order-ledger.judgeOf().recordable`: نوشتنِ فروش، اشتراکِ چنددستگاهی برای شاگرد، سقفِ
 *    تخفیف) — وگرنه جمع‌آور با تماسِ مستقیم «فاکتور شد» می‌نشاند و حساب‌پیِ مشتری را می‌بست (۴۰۳ `permission_denied`).
 * ⚠️ در app.js پیش از `/shop/orders` سوار می‌شود.
 */
const express = require('express');
const config = require('../config');
const orders = require('../lib/orders');
const flow = require('../lib/order-flow');
const ledger = require('../lib/order-ledger');
const audit = require('../lib/audit');
const { requireUser, requireShop, requirePermission } = require('../middleware/auth');
const { rateLimit, clientIp } = require('../middleware/ratelimit');
const { forbidden } = require('../middleware/errors');

const router = express.Router();

router.use('/:id/invoice', rateLimit({ max: config.rateLimit.generalMax, keyPrefix: 'order-invoice-ip' }));
router.use('/:id/invoice', requireUser, requireShop, requirePermission('orders.view'));

const needFlag = async (req, res, next) => {
  if (!(await orders.flagOf(req.shopId))) return next(forbidden('سفارشِ آنلاین برای این دکان روشن نیست', 'orders_disabled'));
  next();
};

const needWrite = async (req, res, next) => {
  const { entitled, ent } = await orders.entitledOf(req.shopId);
  if (entitled) return next();
  return next(ent.trial.used && !ent.trial.active
    ? forbidden('اشتراکِ این دکان به پایان رسیده است.', 'subscription_expired')
    : forbidden('این قابلیت نیازمندِ اشتراک است', 'subscription_required'));
};

router.post('/:id/invoice', needFlag, requirePermission('orders.manage'), needWrite, async (req, res) => {
  const actor = { type: 'user', id: req.user.id, role: req.role };
  const before = await orders.get(req.shopId, req.params.id, actor);   //  دکانِ دیگر/نیست ⇒ ۴۰۴
  if (!(await ledger.judgeOf(req.shopId, actor)).recordable(before)) {
    throw forbidden('ثبتِ فروشِ این سفارش در حد دسترسی شما نیست', 'permission_denied');
  }
  const out = await flow.claimInvoice(req.shopId, req.params.id, actor);
  const order = await orders.get(req.shopId, req.params.id, actor);
  Object.assign(order, await flow.staffView(req.shopId, order));
  //  «دریافت از دکان» ِ پرداخت‌نشده: همین فاکتور «مشتری گرفت» را زد ⇒ همان ممیزیِ گذارِ `routes/orders.js`
  if (out.handedOver) {
    await audit.log({
      shopId: req.shopId, userId: req.user.id, action: 'order.transition', targetType: 'online_order', targetId: order.id,
      detail: { orderNo: order.orderNo, from: 'ready_for_pickup', to: 'picked_up', reason: order.statusReason }, ip: clientIp(req),
    });
  }
  if (out.created) {
    await audit.log({
      shopId: req.shopId, userId: req.user.id, action: 'order.invoice', targetType: 'online_order', targetId: order.id,
      detail: { orderNo: order.orderNo, saleId: out.invoice.saleId, totalMinor: order.totalMinor }, ip: clientIp(req),
    });
  }
  res.status(out.created ? 201 : 200).json({ invoice: out.invoice, created: out.created, order });
});

module.exports = router;
