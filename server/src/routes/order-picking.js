'use strict';
/**
 * جمع‌آوری و بسته‌بندی (PCK، v3 §۶.۱۷) — زیرِ `/api/shop/orders` (`router.use` در `routes/orders.js`،
 * پس از `requireUser`، `requireShop` و `orders.view`).
 *
 * ⛔ پیش‌فرض خاموش: بی پرچمِ `shop.orders` ⇒ ۴۰۳ `orders_disabled` (همان درِ سفارش)؛ بی پرچمِ
 * `shop.orders.picking` ⇒ ۴۰۳ `picking_disabled`. هر نوشتن کلیدِ اشتراکِ `online_orders` را هم
 * می‌خواهد (`needWrite`، SUB-10 — همان دستهٔ PAIDِ `/shop/orders`) و پیش از پرچمِ جمع‌آوری
 * سنجیده می‌شود تا اشتراکِ تمام‌شده همیشه همان جوابِ اشتراکی را بدهد.
 * ⛔ shop_id فقط از عضویتِ کاربرِ توکن می‌آید، هرگز از درخواست.
 *
 *   GET  /pick/issues                     وظیفه‌های «پیدا نکردم» (PCK-14)
 *   POST /pick/issues/:issueId/resolve    رسیدگیِ مدیر — `stock.adjust`
 *   PUT  /pick/locations/:productId       جای قفسهٔ کالا (PCK-01) — `orders.manage`
 *   GET  /pick/metrics                    شاخص‌ها (PCK-13) — `reports.view`
 *   GET  /:id/pick                        برگهٔ جمع‌آوری
 *   POST /:id/pick/start                  شروع — `orders.manage`
 *   POST /:id/pick/events                 کارهای جمع‌آور با کلیدِ یکتا — `orders.manage`
 *   POST /:id/pick/pack                   بسته‌بندی با کلیدِ یکتایی — `orders.manage`
 *
 *   جایگزینی (PCK-05/06، `lib/pick-substitutes.js`):
 *   PUT  /pick/products/:productId        حلال بودنِ کالا برای جایگزینِ امن — `stock.adjust`
 *   GET  /:id/pick/substitutes?lineNo=    دو جایگزینِ امنِ پیشنهادی برای یک قلم
 *   POST /:id/pick/substitutions          پیشنهاد (با اسکن) برای قلمِ «پیدا نشد» — `orders.manage`
 *   POST /:id/pick/substitutions/decide   تصمیمِ کارمند پس از تماس/واتس‌اپ — `orders.manage`
 *   قفسهٔ انتظار و جمع‌آوریِ گروهی (PCK-10/12، `lib/pick-shelf.js`):
 *   GET  /pick/shelf                      آن‌چه روی قفسهٔ انتظار است
 *   POST /pick/shelf/scan                 اسکنِ برچسبِ کیسه روی یک جا ⇒ «آماده» — `orders.manage`
 *   POST /pick/batches                    دستهٔ ۲ تا ۱۰ سفارش با کلیدِ یکتایی — `orders.manage`
 *   GET  /pick/batches/:batchId           فهرستِ یک‌جای دسته به ترتیبِ قفسه
 */
const express = require('express');
const orders = require('../lib/orders');
const picking = require('../lib/order-picking');
const subs = require('../lib/pick-substitutes');
const shelf = require('../lib/pick-shelf');
const audit = require('../lib/audit');
const { requirePermission } = require('../middleware/auth');
const { clientIp } = require('../middleware/ratelimit');
const { forbidden } = require('../middleware/errors');

const router = express.Router();

const actorOf = (req) => ({ type: 'user', id: req.user.id, role: req.role });

/** درِ سفارش باید روشن باشد (همان پیامِ `routes/orders.js`). */
const needOrders = async (req, res, next) => {
  if (!(await orders.flagOf(req.shopId))) {
    return next(forbidden('سفارشِ آنلاین برای این دکان روشن نیست', 'orders_disabled'));
  }
  next();
};

/** نوشتن: کلیدِ اشتراکِ `online_orders` (همان پیام‌های `requireFeature`). */
const needWrite = async (req, res, next) => {
  const { entitled, ent } = await orders.entitledOf(req.shopId);
  if (!entitled) {
    return next(ent.trial.used && !ent.trial.active
      ? forbidden('اشتراکِ این دکان به پایان رسیده است.', 'subscription_expired')
      : forbidden('این قابلیت نیازمندِ اشتراک است', 'subscription_required'));
  }
  next();
};

/** پرچمِ جمع‌آوری — پیش‌فرض خاموش. */
const needPick = async (req, res, next) => {
  if (!(await picking.flagOf(req.shopId))) {
    return next(forbidden('جمع‌آوری و بسته‌بندی برای این دکان روشن نیست', 'picking_disabled'));
  }
  next();
};

const manage = requirePermission('orders.manage');

const log = (req, action, targetId, detail) => audit.log({
  shopId: req.shopId, userId: req.user.id, action, targetType: 'online_order', targetId, detail, ip: clientIp(req),
});

// ── مسیرهای بی شناسهٔ سفارش (پیش از `/:id/…`) ─────────────────────────

router.get('/pick/issues', needOrders, needPick, async (req, res) => {
  res.json(await picking.issues(req.shopId, { status: String(req.query.status || 'open'), limit: req.query.limit }));
});

router.post('/pick/issues/:issueId/resolve', needOrders, requirePermission('stock.adjust'), needWrite, needPick, async (req, res) => {
  const out = await picking.resolveIssue(req.shopId, req.params.issueId, req.body, req.user.id);
  await audit.log({
    shopId: req.shopId, userId: req.user.id, action: 'pick.issue_resolve', targetType: 'pick_issue', targetId: out.issue.id,
    detail: { productId: out.issue.productId, status: out.issue.status }, ip: clientIp(req),
  });
  res.json(out);
});

router.put('/pick/locations/:productId', needOrders, manage, needWrite, needPick, async (req, res) => {
  res.json(await picking.setLocation(req.shopId, req.params.productId, req.body, req.user.id));
});

router.get('/pick/metrics', needOrders, requirePermission('reports.view'), needPick, async (req, res) => {
  res.json(await picking.metrics(req.shopId, { from: req.query.from, to: req.query.to }));
});

//  PCK-06: حلال بودن (جمع‌آور نمی‌تواند برای باز کردنِ جایگزین عوضش کند — انباردار/مدیر)
router.put('/pick/products/:productId', needOrders, requirePermission('stock.adjust'), needWrite, needPick, async (req, res) => {
  const out = await subs.setAttrs(req.shopId, req.params.productId, req.body, req.user.id);
  await audit.log({
    shopId: req.shopId, userId: req.user.id, action: 'pick.product_attrs', targetType: 'product', targetId: out.productId,
    detail: { halal: out.halal }, ip: clientIp(req),
  });
  res.json(out);
});

// ── برگهٔ یک سفارش ─────────────────────────────────────────────────

//  هنوز شروع نشده ⇒ `{ pick: null }`؛ سفارشِ دکانِ دیگر ⇒ ۴۰۴
router.get('/:id/pick', needOrders, needPick, async (req, res) => {
  res.json({ pick: await picking.sheet(req.shopId, req.params.id) });
});

router.post('/:id/pick/start', needOrders, manage, needWrite, needPick, async (req, res) => {
  const out = await picking.start(req.shopId, req.params.id, actorOf(req));
  if (!out.replayed) await log(req, 'pick.start', out.sheet.orderId, { orderNo: out.sheet.orderNo, lines: out.sheet.progress.total });
  res.status(out.replayed ? 200 : 201).json({ pick: out.sheet, replayed: out.replayed });
});

router.post('/:id/pick/events', needOrders, manage, needWrite, needPick, async (req, res) => {
  const out = await picking.events(req.shopId, req.params.id, req.body, actorOf(req));
  res.json({ results: out.results, pick: out.sheet });
});

// ── جایگزینی (PCK-05/06) ───────────────────────────────────────────

router.get('/:id/pick/substitutes', needOrders, needPick, async (req, res) => {
  res.json(await subs.suggestions(req.shopId, req.params.id, req.query.lineNo));
});

router.post('/:id/pick/substitutions', needOrders, manage, needWrite, needPick, async (req, res) => {
  const out = await subs.propose(req.shopId, req.params.id, req.body, actorOf(req));
  if (!out.replayed) {
    await log(req, 'pick.substitute_propose', req.params.id, {
      lineNo: out.substitution.lineNo, productId: out.substitution.productId, totalMinor: out.substitution.totalMinor,
    });
  }
  res.status(out.replayed ? 200 : 201).json(out);
});

router.post('/:id/pick/substitutions/decide', needOrders, manage, needWrite, needPick, async (req, res) => {
  const b = req.body && typeof req.body === 'object' ? req.body : {};
  const out = await subs.decide(req.shopId, req.params.id, b.substitutionId, b, { by: 'staff', actorId: req.user.id });
  if (!out.replayed) {
    await log(req, 'pick.substitute_decide', req.params.id, {
      substitutionId: out.substitution.id, lineNo: out.substitution.lineNo, status: out.substitution.status,
    });
  }
  res.json(out);
});

router.post('/:id/pick/pack', needOrders, manage, needWrite, needPick, async (req, res) => {
  const key = orders.clientKeyOf(req);
  const out = await picking.pack(req.shopId, req.params.id, { key, body: req.body }, actorOf(req));
  if (out.replayed) {
    res.set('Idempotent-Replayed', 'true');
    return res.json({ pick: out.sheet, replayed: true });
  }
  await log(req, 'pick.pack', out.sheet.orderId, {
    orderNo: out.sheet.orderNo, bags: out.sheet.bags, totalMinor: out.sheet.totals.totalMinor,
    originalTotalMinor: out.sheet.totals.originalTotalMinor, deductedMinor: out.deductedMinor,
  });
  res.json({ pick: out.sheet, replayed: false });
});

// ── قفسهٔ انتظار (PCK-10) و جمع‌آوریِ گروهی (PCK-12) ─────────────────

router.get('/pick/shelf', needOrders, needPick, async (req, res) => {
  res.json(await shelf.shelf(req.shopId));
});

router.post('/pick/shelf/scan', needOrders, manage, needWrite, needPick, async (req, res) => {
  const out = await shelf.scan(req.shopId, req.body, actorOf(req));
  if (out.readyNow) await log(req, 'pick.ready', out.orderId, { orderNo: out.orderNo, slot: out.slot, bags: out.bags });
  res.json(out);
});

router.post('/pick/batches', needOrders, manage, needWrite, needPick, async (req, res) => {
  const out = await shelf.createBatch(req.shopId, { key: orders.clientKeyOf(req), body: req.body }, actorOf(req));
  if (!out.replayed) {
    await audit.log({
      shopId: req.shopId, userId: req.user.id, action: 'pick.batch', targetType: 'pick_batch', targetId: out.batch.batchId,
      detail: { orders: out.batch.orders.map((o) => o.orderNo) }, ip: clientIp(req),
    });
  }
  res.status(out.replayed ? 200 : 201).json(out);
});

router.get('/pick/batches/:batchId', needOrders, needPick, async (req, res) => {
  res.json({ batch: await shelf.batch(req.shopId, req.params.batchId) });
});

module.exports = router;
