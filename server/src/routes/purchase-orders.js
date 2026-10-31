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
 */
const express = require('express');
const po = require('../lib/purchase-orders');
const audit = require('../lib/audit');
const { can } = require('../lib/permissions');
const { requireUser, requireShop, requirePermission } = require('../middleware/auth');
const { clientIp } = require('../middleware/ratelimit');

const router = express.Router();
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
  res.json(await po.list(req.shopId, {
    state: req.query.state, supplierId: req.query.supplierId, cursor: req.query.cursor, limit: req.query.limit,
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

module.exports = router;
