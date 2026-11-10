'use strict';
/**
 * سفارشِ خرید از تأمین‌کننده — `/api/shop/purchase-orders` (PUR-02، PUR-04؛ §۶.۶).
 *
 * ⛔ پیش‌فرض خاموش: بی پرچمِ `shop.purchase_orders` فقط `GET /config` جواب می‌دهد
 *    (`enabled: false`) تا برنامه بخش را نشان ندهد؛ بقیه ۴۰۳ `purchase_orders_disabled`.
 * ⛔ shop_id فقط از عضویتِ کاربرِ توکن (INV-11). کلیدِ اشتراکِ `purchasing` رایگان است، پس
 *    اشتراکِ تمام‌شده این درها را نمی‌بندد (SUB-10: دستهٔ FREE).
 * مجوز: دیدن `stock.view` (همه)؛ ساختن، ویرایش، فرستادن و لغو `stock.adjust` (مالک و مدیر)؛
 * رسیدِ دریافت `stock.move` (شاگرد هم کالا را تحویل می‌گیرد، ولی فی را عوض نمی‌کند و بیش از
 * سفارش نمی‌پذیرد — lib/purchase-orders.js).
 *
 * زیرِ پیشوندِ موجودِ `/shop` است، پس `ACCOUNT_PREFIXES` ِ پنلِ خانگی چیزِ تازه‌ای نمی‌خواهد.
 *
 * فهرست: `GET /?state=&status=&supplierId=&cursor=&limit=` — `status` (draft|sent|partial|complete|cancelled|open)
 *   وضعیتِ نمایشی را روی سرور می‌پالاید؛ صفحهٔ کم‌تر از `limit` با `next` ممکن است (lib/purchase-orders.js ‹list›).
 *
 * PUR-14 — فاکتورِ جدای تأمین‌کننده (مهاجرتِ ۲۰۸، شرح در lib/purchase-orders.js):
 *   POST /:id/bills                  `{id, billNo, billDate?, note?, lines:[{lineId?, productId?, qtyMilli, totalMinor}]}`
 *                                    ⇒ ۲۰۱ (همان کلید و بدنه ⇒ ۲۰۰ و `replayed`)؛ فقط `stock.adjust` (بدهیِ تأمین‌کننده)
 *   POST /:id/bills/:billId/void     `{reason}` ⇒ ۲۰۰؛ تکرار بی اثر
 * ⛔ سقفِ نرخ برای هر IP **پیش از** احراز (همان سقفِ عمومی؛ سطلِ جدا).
 */
const express = require('express');
const po = require('../lib/purchase-orders');
const audit = require('../lib/audit');
const { can } = require('../lib/permissions');
const { requireUser, requireShop, requirePermission } = require('../middleware/auth');
const { rateLimit, clientIp } = require('../middleware/ratelimit');
const config = require('../config');

const router = express.Router();
router.use(rateLimit({ max: config.rateLimit.generalMax, keyPrefix: 'purchase-orders-ip' }));
router.use(requireUser, requireShop, requirePermission('stock.view'));

const needFlag = async (req, res, next) => { await po.requireEnabled(req.shopId); next(); };

const log = (req, action, targetId, detail) => audit.log({
  shopId: req.shopId, userId: req.user.id, action, targetType: 'purchase_order', targetId, detail, ip: clientIp(req),
});

router.get('/config', async (req, res) => {
  res.json({
    enabled: await po.enabled(req.shopId),
    permissions: { manage: can(req.role, 'stock.adjust'), receive: can(req.role, 'stock.move') },
    limits: { lines: require('../lib/purchase-order-core').MAX_LINES, qtyMilli: po.MAX_QTY_MILLI, priceMinor: po.MAX_PRICE_MINOR },
  });
});

router.get('/', needFlag, async (req, res) => {
  //  `status` (PUR-02/03): وضعیتِ نمایشی — بخشی/کامل/«باز» از رسیدها روی سرور (lib/purchase-orders.js ‹list›)
  res.json(await po.list(req.shopId, {
    state: req.query.state, status: req.query.status, supplierId: req.query.supplierId, cursor: req.query.cursor, limit: req.query.limit,
  }));
});

router.get('/:id', needFlag, async (req, res) => {
  res.json({ order: await po.get(req.shopId, req.params.id) });
});

router.post('/', needFlag, requirePermission('stock.adjust'), async (req, res) => {
  const key = po.clientKeyOf(req);
  const { order, replayed } = await po.create(req.shopId, { key, body: req.body, userId: req.user.id });
  if (!replayed) await log(req, 'purchase_order.create', order.id, { no: order.no, lines: order.lines.length });
  if (replayed) res.set('Idempotent-Replayed', 'true');
  res.status(replayed ? 200 : 201).json({ order, replayed });
});

router.put('/:id', needFlag, requirePermission('stock.adjust'), async (req, res) => {
  const order = await po.update(req.shopId, req.params.id, { body: req.body });
  await log(req, 'purchase_order.update', order.id, { version: order.version, lines: order.lines.length });
  res.json({ order });
});

router.post('/:id/send', needFlag, requirePermission('stock.adjust'), async (req, res) => {
  const { order, changed } = await po.transition(req.shopId, req.params.id, 'send');
  if (changed) await log(req, 'purchase_order.send', order.id, { no: order.no });
  res.json({ order, changed });
});

router.post('/:id/cancel', needFlag, requirePermission('stock.adjust'), async (req, res) => {
  const { order, changed } = await po.transition(req.shopId, req.params.id, 'cancel', { reason: req.body?.reason });
  if (changed) await log(req, 'purchase_order.cancel', order.id, { no: order.no, reason: order.cancelReason || '' });
  res.json({ order, changed });
});

router.post('/:id/receipts', needFlag, requirePermission('stock.move'), async (req, res) => {
  const headerKey = typeof req.get === 'function' ? req.get('idempotency-key') : undefined;
  const out = await po.receive(req.shopId, req.params.id, {
    body: req.body, headerKey, userId: req.user.id, role: req.role,
  });
  if (!out.replayed) {
    await log(req, 'purchase_order.receive', out.order.id, {
      receipt: out.receipt.id, totalMinor: out.receipt.totalMinor, status: out.order.status,
    });
  } else res.set('Idempotent-Replayed', 'true');
  res.status(out.replayed ? 200 : 201).json(out);
});

router.post('/:id/bills', needFlag, requirePermission('stock.adjust'), async (req, res) => {
  const headerKey = typeof req.get === 'function' ? req.get('idempotency-key') : undefined;
  const out = await po.bill(req.shopId, req.params.id, { body: req.body, headerKey, userId: req.user.id });
  if (!out.replayed) {
    await log(req, 'purchase_order.bill', out.order.id, { bill: out.bill.id, billNo: out.bill.billNo, totalMinor: out.bill.totalMinor });
  } else res.set('Idempotent-Replayed', 'true');
  res.status(out.replayed ? 200 : 201).json(out);
});

router.post('/:id/bills/:billId/void', needFlag, requirePermission('stock.adjust'), async (req, res) => {
  const out = await po.voidBill(req.shopId, req.params.id, req.params.billId, { reason: req.body?.reason, userId: req.user.id });
  if (out.changed) await log(req, 'purchase_order.bill_void', out.order.id, { bill: out.bill.id, reason: out.bill.voidReason });
  res.json(out);
});

module.exports = router;
