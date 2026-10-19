'use strict';
/**
 * ویترینِ عمومی — `/api/store/:slug/…` (فاز ۴؛ WEB، CHK، بخش‌های ۲۳ و ۳۱).
 *
 * ⛔ بی‌توکن و عمومی، ولی پیش‌فرض خاموش: پرچم‌های `shop.orders` و
 * `shop.storefront` + روشن بودنِ ویترینِ خودِ دکان. هر کدام نباشد ⇒ ۴۰۴
 * (همان جوابِ «چنین ویترینی نیست») و هیچ ردیفی ساخته نمی‌شود.
 * ⛔ shop_id فقط از `slug` ِ ویترینِ روشن می‌آید، هرگز از بدنه.
 * ⛔ سقفِ درخواست: خواندن سطلِ خودش را با `generalMax` دارد (مثلِ کیو‌آرِ
 * پمپ)، ثبتِ سفارش و فرستادنِ کد `storeCheckoutMax`، زدنِ کد `verifyMax` —
 * همه به‌ازای IP، مثلِ بقیهٔ مسیرهای عمومی.
 *
 * پیشوندِ تازهٔ `apiRouter` است ⇒ `ACCOUNT_PREFIXES` ِ پنلِ خانگی باید
 * `/store` را هم بگیرد (ریپوی دیگر؛ دستورالعمل در PR).
 */
const express = require('express');
const config = require('../config');
const sf = require('../lib/storefront');
const orders = require('../lib/orders');
const audit = require('../lib/audit');
const { sniff } = require('../lib/product-photos');
const { rateLimit, clientIp } = require('../middleware/ratelimit');
const { notFound } = require('../middleware/errors');

const router = express.Router();
const wrap = (fn) => (req, res, next) => Promise.resolve(fn(req, res, next)).catch(next);

const readLimit = rateLimit({ max: config.rateLimit.generalMax, keyPrefix: 'store-read' });
const checkoutLimit = rateLimit({ max: config.rateLimit.storeCheckoutMax, keyPrefix: 'store-checkout' });
const sendCodeLimit = rateLimit({ max: config.rateLimit.storeCheckoutMax, keyPrefix: 'store-verify' });
const codeLimit = rateLimit({ max: config.rateLimit.verifyMax, keyPrefix: 'store-verify-code' });

/** ویترینِ روشن — یا ۴۰۴. پاسخ‌ها کش نمی‌شوند (قیمت و وضعیت زنده‌اند). */
const store = wrap(async (req, res, next) => {
  req.store = await sf.open(req.params.slug);
  res.set('Cache-Control', 'no-store');
  next();
});

const ticketOf = (req) => req.get('x-store-ticket') || '';

router.get('/:slug', readLimit, store, wrap(async (req, res) => {
  const [cats, status] = await Promise.all([sf.categories(req.store.shop_id), sf.statusOf(req.store)]);
  res.json({
    store: sf.profile(req.store, cats, status), verification: { available: await sf.verificationAvailable() },
    //  پیوندِ «بازارچه» فقط وقتی بازارچه باز است (پرچمِ `market.directory`)
    market: { available: await require('../lib/market').directoryOn() },
  });
}));

router.get('/:slug/products', readLimit, store, wrap(async (req, res) => {
  const out = req.query.ids !== undefined
    ? await sf.catalogByIds(req.store.shop_id, req.query.ids)
    : await sf.catalog(req.store.shop_id, {
      q: req.query.q, category: req.query.category, cursor: req.query.cursor, limit: req.query.limit,
    });
  //  ۳۲.۳: با پرچمِ `shop.catalog` وضعیتِ موجودی از دفترِ سرور (#263)؛ خاموش ⇒ null
  //  و همان «در حال بررسی». موجودیِ نامعلوم/کهنه هرگز «موجود» نیست (`badgeOf`).
  const stock = out.items.length
    ? await require('../lib/catalog').publicStock(req.store.shop_id, out.items.map((p) => p.id)) : null;
  if (stock) out.items = out.items.map((p) => ({ ...p, stock: stock.get(p.id) || p.stock }));
  res.json(out);
}));

router.get('/:slug/products/:id/photo', readLimit, store, wrap(async (req, res) => {
  const p = await sf.photo(req.store.shop_id, req.params.id);
  if (!p) throw notFound('عکس نیست', 'photo_not_found');
  const etag = `"${p.hash}"`;
  res.set('Cache-Control', 'public, max-age=300');
  res.set('ETag', etag);
  if (req.get('if-none-match') === etag) return res.status(304).end();
  res.type(sniff(p.data) || 'application/octet-stream').send(p.data);
}));

//  ── ۲۳: تأییدِ شماره پیش از ثبتِ سفارش ──────────────────────────────
router.post('/:slug/verify/start', sendCodeLimit, store, wrap(async (req, res) => {
  res.json(await sf.startVerification(req.body, { ip: clientIp(req) }));
}));

router.post('/:slug/verify/check', codeLimit, store, wrap(async (req, res) => {
  res.json(await sf.checkVerification(req.body));
}));

//  ── ثبت: فقط از راهِ `orders.create` با کلیدِ یکتایی (CHK-19) ───────
router.post('/:slug/checkout', checkoutLimit, store, wrap(async (req, res) => {
  const key = orders.clientKeyOf(req);
  const out = await sf.checkout(req.store, { key, body: req.body, ticket: ticketOf(req) });
  if (out.replayed) {
    res.set('Idempotent-Replayed', 'true');
    return res.status(200).json({ order: out.order, replayed: true });
  }
  await audit.log({
    shopId: req.store.shop_id, actorType: 'customer', action: 'order.create',
    targetType: 'online_order', targetId: out.orderId,
    detail: { orderNo: out.order.orderNo, source: 'storefront', totalMinor: out.order.totalMinor },
    ip: clientIp(req),
  });
  res.status(201).json({ order: out.order });
}));

router.get('/:slug/orders', readLimit, store, wrap(async (req, res) => {
  res.json({ orders: await sf.myOrders(req.store, ticketOf(req)) });
}));

router.get('/:slug/orders/:token', readLimit, store, wrap(async (req, res) => {
  res.json({ order: await sf.track(req.store, req.params.token) });
}));

router.post('/:slug/orders/:token/cancel', checkoutLimit, store, wrap(async (req, res) => {
  res.json(await sf.cancel(req.store, req.params.token));
}));

/** خطایی که دادهٔ همراه دارد (برگهٔ قیمتِ تازه، کالای ناموجود، …) — همه‌اش برود. */
// eslint-disable-next-line no-unused-vars
router.use((err, req, res, next) => {
  if (!err || !err.data || err.expose !== true) return next(err);
  res.status(err.status).json({ error: { code: err.code, message: err.message, ...err.data } });
});

module.exports = router;
