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
 * ⛔ STF-06 (`lib/staff-scope.js`، پرچمِ خاموشِ `shop.staff_scope`): عضوِ محدود فقط مکان‌های محدوده‌اش را
 * می‌بیند و می‌نویسد (۴۰۳ `location_out_of_scope`)؛ پرچم خاموش، مالک یا محدودهٔ خالی ⇒ همان امروز.
 *
 * زیرِ پیشوندِ موجودِ `/shop` است، پس `ACCOUNT_PREFIXES`ِ پنلِ خانگی
 * چیزِ تازه‌ای نمی‌خواهد.
 */
const express = require('express');
const catalog = require('../lib/catalog');
const stock = require('../lib/stock');
const transfers = require('../lib/stock-transfers');
const scope = require('../lib/staff-scope');
const audit = require('../lib/audit');
const { now } = require('../db');
const { can } = require('../lib/permissions');
const { requireUser, requireShop, requirePermission } = require('../middleware/auth');
const { clientIp } = require('../middleware/ratelimit');
const { badRequest, forbidden } = require('../middleware/errors');

const router = express.Router();
router.use(requireUser, requireShop, requirePermission('stock.view'));

const needFlag = async (req, res, next) => { await catalog.requireEnabled(req.shopId); next(); };

router.get('/config', async (req, res) => {
  const sc = await scope.scopeOfReq(req);
  res.json({
    enabled: await stock.flagOf(req.shopId),
    settings: await stock.settingsOf(req.shopId),
    locations: sc ? sc.slice() : stock.LOCATIONS,
    sellableChoices: stock.SELLABLE_CHOICES,
    kinds: Object.keys(catalog.MANUAL),
    permissions: {
      move: can(req.role, 'stock.move'),
      adjust: can(req.role, 'stock.adjust'),
      settings: can(req.role, 'settings.write'),
    },
    ...(sc ? { scope: sc } : {}),
  });
});

router.put('/settings', needFlag, requirePermission('settings.write'), async (req, res) => {
  scope.requireAll(await scope.scopeOfReq(req));
  const settings = await stock.saveSettings(req.shopId, req.body, req.user.id, now());
  await audit.log({
    shopId: req.shopId, userId: req.user.id, action: 'stock.settings', targetType: 'catalog_settings',
    targetId: req.shopId, detail: settings, ip: clientIp(req),
  });
  res.json({ settings });
});

/**
 * خواندنِ دوبارهٔ دفترِ دستگاه همین حالا (`full: true` ⇒ از اول؛ بی‌خطر چون فقط تفاوت نوشته می‌شود).
 * ⛔ `full` همهٔ دفتر را زیرِ قفلِ موجودیِ دکان می‌خواند و تا پایانش ثبتِ سفارش منتظر می‌ماند
 * ⇒ فقط مالک و مدیر (QA #263). خواندنِ تفاوت همان `stock.move` می‌ماند.
 */
const FULL_REFRESH_ROLES = new Set(['owner', 'manager']);
router.post('/refresh', needFlag, requirePermission('stock.move'), async (req, res) => {
  const full = req.body?.full === true;
  if (full && !FULL_REFRESH_ROLES.has(req.role)) {
    throw forbidden('خواندنِ کاملِ دفتر فقط برای مالک و مدیر است', 'full_refresh_forbidden');
  }
  res.json(await catalog.refresh(req.shopId, { full }));
});

router.get('/products', needFlag, async (req, res) => {
  const sc = await scope.scopeOfReq(req);
  const out = await catalog.list(req.shopId, { q: req.query.q, cursor: req.query.cursor, limit: req.query.limit });
  res.json(sc ? { ...out, items: out.items.map((p) => scope.trimProduct(p, sc)) } : out);
});

router.get('/products/:id', needFlag, async (req, res) => {
  const sc = await scope.scopeOfReq(req);
  const out = await catalog.detail(req.shopId, req.params.id);
  res.json(sc ? { ...out, product: scope.trimProduct(out.product, sc), lowByLocation: scope.trimLows(out.lowByLocation, sc) } : out);
});

router.put('/products/:id/low', needFlag, requirePermission('settings.write'), async (req, res) => {
  scope.checkLocation(await scope.scopeOfReq(req), req.body?.location);
  res.json(await catalog.setLow(req.shopId, req.params.id, req.body));
});

router.get('/moves', needFlag, async (req, res) => {
  res.json(await catalog.moves(req.shopId, {
    productId: req.query.productId, cursor: req.query.cursor, limit: req.query.limit, places: await scope.scopeOfReq(req),
  }));
});

router.get('/report', needFlag, requirePermission('reports.view'), async (req, res) => {
  scope.requireAll(await scope.scopeOfReq(req));
  res.json(await stock.reconcile(req.shopId, { productId: String(req.query.productId || '') }));
});

router.get('/tasks', needFlag, async (req, res) => {
  const places = await scope.scopeOfReq(req);
  res.json({ tasks: await catalog.tasks(req.shopId, { status: String(req.query.status || 'open'), limit: req.query.limit, places }) });
});

router.post('/tasks/:id/resolve', needFlag, requirePermission('stock.adjust'), async (req, res) => {
  const sc = await scope.scopeOfReq(req);
  const task = sc ? await scope.taskOf(req.shopId, req.params.id) : null;
  if (task) scope.check(sc, scope.taskPlaces(task));
  const out = await catalog.resolveTask(req.shopId, req.params.id, req.body, req.user.id);
  await audit.log({
    shopId: req.shopId, userId: req.user.id, action: 'stock.task', targetType: 'stock_task',
    targetId: out.id, detail: { status: out.status }, ip: clientIp(req),
  });
  res.json(out);
});

/**
 * حرکتِ دستی: transfer (stock.move)؛ damage، writeoff، return، adjust، count
 * (stock.adjust، با دلیل). کلیدِ یکتایی: سرآیندِ `Idempotency-Key`.
 */
router.post('/moves', needFlag, async (req, res) => {
  const spec = catalog.MANUAL[String(req.body?.kind || '')];
  if (!spec) throw badRequest('نوعِ حرکت معتبر نیست', 'bad_kind');
  if (!can(req.role, spec.permission)) throw forbidden('این کار در حد دسترسی شما نیست', 'permission_denied');
  const sc = await scope.scopeOfReq(req);
  if (sc) scope.check(sc, scope.manualPlaces(catalog.parseManual(req.body)));
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
});

/**
 * انتقالِ دومرحله‌ای با «در مسیر» و قبول یا رد در مقصد (INV-11، `lib/stock-transfers.js`).
 * هر سه نوشتن اجازهٔ `stock.move` و کلیدِ یکتایی (`Idempotency-Key`) می‌خواهند؛ کسری و رد دلیل.
 */
router.get('/transfers', needFlag, async (req, res) => {
  const places = await scope.scopeOfReq(req);
  res.json({ transfers: await transfers.list(req.shopId, { status: req.query.status, limit: req.query.limit, places }) });
});

async function transferDone(req, res, action, out) {
  if (out.replayed) {
    res.set('Idempotent-Replayed', 'true');
    return res.status(200).json(out);
  }
  const t = out.transfer;
  await audit.log({
    shopId: req.shopId, userId: req.user.id, action, targetType: 'stock_transfer', targetId: t.id,
    detail: { from: t.from, to: t.to, status: t.status, lines: t.lines.map((l) => [l.productId, l.sentMilli, l.receivedMilli]), reason: t.reason },
    ip: clientIp(req),
  });
  return res.status(action === 'stock.transfer_send' ? 201 : 200).json(out);
}

/** عضوِ محدود: ارسال از مبدأِ محدوده؛ دریافت و رد در مقصدِ محدوده (همان «قبول یا رد در مقصد») */
async function checkClose(req) {
  const sc = await scope.scopeOfReq(req);
  const to = sc ? await scope.transferTo(req.shopId, req.params.id) : null;
  if (to) scope.check(sc, [to]);
}

router.post('/transfers', needFlag, requirePermission('stock.move'), async (req, res) => {
  const sc = await scope.scopeOfReq(req);
  if (sc) scope.check(sc, [transfers.parseSend(req.body).from]);
  const out = await transfers.send(req.shopId, {
    key: req.get('idempotency-key') || req.body?.clientKey, body: req.body, actor: { id: req.user.id, role: req.role },
  });
  await transferDone(req, res, 'stock.transfer_send', out);
});

router.post('/transfers/:id/receive', needFlag, requirePermission('stock.move'), async (req, res) => {
  await checkClose(req);
  const out = await transfers.receive(req.shopId, req.params.id, {
    key: req.get('idempotency-key') || req.body?.clientKey, body: req.body, actor: { id: req.user.id, role: req.role },
  });
  await transferDone(req, res, 'stock.transfer_receive', out);
});

router.post('/transfers/:id/reject', needFlag, requirePermission('stock.move'), async (req, res) => {
  await checkClose(req);
  const out = await transfers.reject(req.shopId, req.params.id, {
    key: req.get('idempotency-key') || req.body?.clientKey, body: req.body, actor: { id: req.user.id, role: req.role },
  });
  await transferDone(req, res, 'stock.transfer_reject', out);
});

module.exports = router;
