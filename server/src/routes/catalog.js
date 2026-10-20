'use strict';
/**
 * کاتالوگ و موجودیِ سرور — `/api/shop/catalog` (v3 §۳۲، §۳۸.۳).
 *
 * ⛔ پیش‌فرض خاموش: بی پرچمِ `shop.catalog` در «تنظیماتِ زنده» فقط
 * `GET /config` جواب می‌دهد (تا برنامه بداند بخش را نشان ندهد).
 * ⛔ shop_id فقط از عضویتِ کاربرِ توکن می‌آید، هرگز از درخواست (INV-11).
 * ⛔ خواندن همیشه باز است (داده گروگان نیست)؛ هر نوشتن اجازهٔ خودش را دارد
 * و حرکتِ دستی کلیدِ یکتایی و (جز انتقال) دلیل می‌خواهد (INV-12، INV-15).
 *
 * زیرِ پیشوندِ موجودِ `/shop` است، پس `ACCOUNT_PREFIXES`ِ پنلِ خانگی
 * چیزِ تازه‌ای نمی‌خواهد.
 */
const express = require('express');
const catalog = require('../lib/catalog');
const stock = require('../lib/stock');
const audit = require('../lib/audit');
const { now } = require('../db');
const { can } = require('../lib/permissions');
const { requireUser, requireShop, requirePermission } = require('../middleware/auth');
const { clientIp } = require('../middleware/ratelimit');
const { badRequest, forbidden } = require('../middleware/errors');

const router = express.Router();
router.use(requireUser, requireShop, requirePermission('stock.view'));

const wrap = (fn) => (req, res, next) => Promise.resolve(fn(req, res, next)).catch(next);
const needFlag = wrap(async (req, res, next) => { await catalog.requireEnabled(req.shopId); next(); });

router.get('/config', wrap(async (req, res) => {
  res.json({
    enabled: await stock.flagOf(req.shopId),
    settings: await stock.settingsOf(req.shopId),
    locations: stock.LOCATIONS,
    sellableChoices: stock.SELLABLE_CHOICES,
    kinds: Object.keys(catalog.MANUAL),
    permissions: {
      move: can(req.role, 'stock.move'),
      adjust: can(req.role, 'stock.adjust'),
      settings: can(req.role, 'settings.write'),
    },
  });
}));

router.put('/settings', needFlag, requirePermission('settings.write'), wrap(async (req, res) => {
  const settings = await stock.saveSettings(req.shopId, req.body, req.user.id, now());
  await audit.log({
    shopId: req.shopId, userId: req.user.id, action: 'stock.settings', targetType: 'catalog_settings',
    targetId: req.shopId, detail: settings, ip: clientIp(req),
  });
  res.json({ settings });
}));

/**
 * خواندنِ دوبارهٔ دفترِ دستگاه همین حالا (`full: true` ⇒ از اول؛ بی‌خطر چون فقط تفاوت نوشته می‌شود).
 * ⛔ `full` همهٔ دفتر را زیرِ قفلِ موجودیِ دکان می‌خواند و تا پایانش ثبتِ سفارش منتظر می‌ماند
 * ⇒ فقط مالک و مدیر (QA #263). خواندنِ تفاوت همان `stock.move` می‌ماند.
 */
const FULL_REFRESH_ROLES = new Set(['owner', 'manager']);
router.post('/refresh', needFlag, requirePermission('stock.move'), wrap(async (req, res) => {
  const full = req.body?.full === true;
  if (full && !FULL_REFRESH_ROLES.has(req.role)) {
    throw forbidden('خواندنِ کاملِ دفتر فقط برای مالک و مدیر است', 'full_refresh_forbidden');
  }
  res.json(await catalog.refresh(req.shopId, { full }));
}));

router.get('/products', needFlag, wrap(async (req, res) => {
  res.json(await catalog.list(req.shopId, { q: req.query.q, cursor: req.query.cursor, limit: req.query.limit }));
}));

router.get('/products/:id', needFlag, wrap(async (req, res) => {
  res.json(await catalog.detail(req.shopId, req.params.id));
}));

router.put('/products/:id/low', needFlag, requirePermission('settings.write'), wrap(async (req, res) => {
  res.json(await catalog.setLow(req.shopId, req.params.id, req.body));
}));

router.get('/moves', needFlag, wrap(async (req, res) => {
  res.json(await catalog.moves(req.shopId, {
    productId: req.query.productId, cursor: req.query.cursor, limit: req.query.limit,
  }));
}));

router.get('/report', needFlag, requirePermission('reports.view'), wrap(async (req, res) => {
  res.json(await stock.reconcile(req.shopId, { productId: String(req.query.productId || '') }));
}));

router.get('/reservations', needFlag, requirePermission('orders.view'), wrap(async (req, res) => {
  const orderId = String(req.query.orderId || '');
  if (!orderId) throw badRequest('شناسهٔ سفارش لازم است', 'order_required');
  res.json({ reservations: await catalog.reservationsOf(req.shopId, orderId) });
}));

router.get('/tasks', needFlag, wrap(async (req, res) => {
  res.json({ tasks: await catalog.tasks(req.shopId, { status: String(req.query.status || 'open'), limit: req.query.limit }) });
}));

router.post('/tasks/:id/resolve', needFlag, requirePermission('stock.adjust'), wrap(async (req, res) => {
  const out = await catalog.resolveTask(req.shopId, req.params.id, req.body, req.user.id);
  await audit.log({
    shopId: req.shopId, userId: req.user.id, action: 'stock.task', targetType: 'stock_task',
    targetId: out.id, detail: { status: out.status }, ip: clientIp(req),
  });
  res.json(out);
}));

/**
 * حرکتِ دستی: transfer (stock.move)؛ damage، writeoff، return، adjust، count
 * (stock.adjust، با دلیل). کلیدِ یکتایی: سرآیندِ `Idempotency-Key`.
 */
router.post('/moves', needFlag, wrap(async (req, res) => {
  const spec = catalog.MANUAL[String(req.body?.kind || '')];
  if (!spec) throw badRequest('نوعِ حرکت معتبر نیست', 'bad_kind');
  if (!can(req.role, spec.permission)) throw forbidden('این کار در حد دسترسی شما نیست', 'permission_denied');
  const out = await catalog.manualMove(req.shopId, {
    key: req.get('idempotency-key') || req.body?.clientKey, body: req.body,
    actor: { type: 'user', id: req.user.id, role: req.role },
  });
  if (out.replayed) {
    res.set('Idempotent-Replayed', 'true');
    return res.status(200).json(out);
  }
  if (!out.unchanged) {
    await audit.log({
      shopId: req.shopId, userId: req.user.id, action: 'stock.move', targetType: 'product',
      targetId: String(req.body.productId || ''),
      detail: { kind: req.body.kind, reason: req.body.reason || '', moves: out.moves.map((m) => [m.type, m.location, m.qtyMilli]) },
      ip: clientIp(req),
    });
  }
  res.status(out.unchanged ? 200 : 201).json(out);
}));

module.exports = router;
