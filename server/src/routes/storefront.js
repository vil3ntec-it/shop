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
 * همه به‌ازای IP، مثلِ بقیهٔ مسیرهای عمومی. ثبتِ سفارش افزون بر آن سقفِ
 * شمارهٔ تأییدشده را هم دارد (RSK-07، `lib/order-rate`).
 *
 * پیشوندِ تازهٔ `apiRouter` است ⇒ `ACCOUNT_PREFIXES` ِ پنلِ خانگی باید
 * `/store` را هم بگیرد (ریپوی دیگر؛ دستورالعمل در PR).
 */
const express = require('express');
const config = require('../config');
const sf = require('../lib/storefront');
const sfd = require('../lib/storefront-delivery');
const customers = require('../lib/store-customers');
const orders = require('../lib/orders');
const orderTimeout = require('../lib/order-timeout');
const audit = require('../lib/audit');
const { sniff } = require('../lib/product-photos');
const { rateLimit, clientIp } = require('../middleware/ratelimit');
const { notFound, ApiError } = require('../middleware/errors');

const router = express.Router();
const wrap = (fn) => (req, res, next) => Promise.resolve(fn(req, res, next)).catch(next);

const readLimit = rateLimit({ max: config.rateLimit.generalMax, keyPrefix: 'store-read' });
const checkoutLimit = rateLimit({ max: config.rateLimit.storeCheckoutMax, keyPrefix: 'store-checkout' });
const sendCodeLimit = rateLimit({ max: config.rateLimit.storeCheckoutMax, keyPrefix: 'store-verify' });
const codeLimit = rateLimit({ max: config.rateLimit.verifyMax, keyPrefix: 'store-verify-code' });
//  ⛔ پیشگوی «درون/بیرون»: سقفِ روزانه به‌ازای (IP، ویترین) — نه سطلِ ربع‌ساعتهٔ خواندن
const locateLimit = rateLimit({
  max: config.rateLimit.storeLocateDailyMax, windowMs: 24 * 60 * 60 * 1000, keyPrefix: 'store-locate',
  key: (req) => `${clientIp(req)}|${String(req.params.slug || '').toLowerCase()}`,
});

/**
 * ویترینِ روشن — یا ۴۰۴. پاسخ‌ها کش نمی‌شوند (قیمت و وضعیت زنده‌اند).
 * S3 (پرچمِ `market.public_id`): نشانیِ پیشینِ دکانی که نشانی‌اش را عوض کرده ⇒
 * ۳۰۸ به همان مسیر با نشانیِ تازه (روش و بدنه می‌مانند). پرچمِ خاموش ⇒ همان ۴۰۴.
 */
const store = wrap(async (req, res, next) => {
  try {
    req.store = await sf.open(req.params.slug);
  } catch (err) {
    const moved = err && err.status === 404 ? await sf.movedTo(req.params.slug) : null;
    if (!moved) throw err;
    const location = `${req.baseUrl}${req.url.replace(/^\/[^/?]*/, `/${moved}`)}`;
    res.set('Cache-Control', 'no-store').location(location);
    return res.status(308).json({ error: { code: 'store_moved', message: 'نشانیِ این ویترین عوض شده است', slug: moved } });
  }
  res.set('Cache-Control', 'no-store');
  next();
});

const ticketOf = (req) => req.get('x-store-ticket') || '';
const sessionOf = (req) => req.get('x-store-session') || '';

/** حسابِ مشتری (نشستِ `X-Store-Session`) — یا ۴۰۱. */
const customer = wrap(async (req, res, next) => {
  const a = await customers.auth(sessionOf(req));
  if (!a) throw new ApiError(401, 'session_required', 'برای این کار وارد حسابِ خود شوید');
  req.customer = a.customer;
  req.session = a;
  next();
});

//  ── S3: ویترین با شناسهٔ عمومیِ پایدار — `/api/store?id=<ULID>` ⇒ نشانیِ امروز ──
//  (پرچمِ خاموش، ویترینِ بسته یا شناسهٔ ناموجود ⇒ همان ۴۰۴ ِ امروزِ این نشانی)
router.get('/', (req, res, next) => next(typeof req.query.id === 'string' ? undefined : 'route'), readLimit, wrap(async (req, res, next) => {
  const hit = await sf.byPublicId(req.query.id);
  if (!hit) return next();
  res.set('Cache-Control', 'no-store').json(hit);
}));

router.get('/:slug', readLimit, store, wrap(async (req, res) => {
  const [cats, status, delivery, pid] = await Promise.all([
    sf.categories(req.store.shop_id), sf.statusOf(req.store), sfd.publicInfo(req.store), sf.publicIdOn(req.store.shop_id),
  ]);
  res.json({
    store: { ...sf.profile(req.store, cats, status), delivery, ...(pid ? { publicId: req.store.public_id } : {}) },
    verification: { available: await sf.verificationAvailable() },
    //  پیوندِ «بازارچه» فقط وقتی بازارچه باز است (پرچمِ `market.directory`)
    market: { available: await require('../lib/market').directoryOn() },
  });
}));

//  ── WEB-02: پرفروش‌ها و تازه‌ها (دادهٔ واقعی؛ نبود ⇒ خالی) ───────────
router.get('/:slug/highlights', readLimit, store, wrap(async (req, res) => {
  res.json(await sf.highlights(req.store.shop_id));
}));

//  ── CHK-07/10: کدام ناحیه؟ (بی ذخیره، بی ژئوکدر؛ با خانهٔ ۳۰۰ متری) ──
router.get('/:slug/delivery', readLimit, locateLimit, store, wrap(async (req, res) => {
  res.json(await sfd.locate(req.store, req.query.lat, req.query.lng));
}));

//  ── CHK-12: بازه‌های پیشِ رو — فقط «هست/پر/کم مانده» ─────────────────
router.get('/:slug/slots', readLimit, store, wrap(async (req, res) => {
  res.json(await sfd.publicSlots(req.store, String(req.query.fulfillment || '')));
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

//  ── WEB-08: صفحهٔ کالا ───────────────────────────────────────────────
router.get('/:slug/products/:id', readLimit, store, wrap(async (req, res) => {
  res.json(await sf.productDetail(req.store.shop_id, req.params.id));
}));

/** عکس: `display` برای صفحهٔ کالا، `thumb` برای کارت (WEB-16). */
const picture = (size) => wrap(async (req, res) => {
  const p = await sf.photo(req.store.shop_id, req.params.id, size);
  if (!p) throw notFound('عکس نیست', 'photo_not_found');
  const etag = `"${p.hash}"`;
  //  پاسخ‌های JSON ِ ویترین کش نمی‌شوند؛ عکس با ETag ِ محتوا می‌شود
  res.set('Cache-Control', 'public, max-age=300');
  res.set('ETag', etag);
  if (req.get('if-none-match') === etag) return res.status(304).end();
  res.type(p.mime || sniff(p.data) || 'application/octet-stream').send(p.data);
});
router.get('/:slug/products/:id/photo', readLimit, store, picture('display'));
router.get('/:slug/products/:id/thumb', readLimit, store, picture('thumb'));

//  ── ۲۳: تأییدِ شماره پیش از ثبتِ سفارش ──────────────────────────────
router.post('/:slug/verify/start', sendCodeLimit, store, wrap(async (req, res) => {
  res.json(await sf.startVerification(req.body, { ip: clientIp(req), shopId: req.store.shop_id }));
}));

router.post('/:slug/verify/check', codeLimit, store, wrap(async (req, res) => {
  res.json(await sf.checkVerification(req.body));
}));

//  ── ثبت: فقط از راهِ `orders.create` با کلیدِ یکتایی (CHK-19) ───────
router.post('/:slug/checkout', checkoutLimit, store, wrap(async (req, res) => {
  const key = orders.clientKeyOf(req);
  let out;
  try {
    out = await sf.checkout(req.store, { key, body: req.body, ticket: ticketOf(req), session: sessionOf(req) });
  } catch (err) {
    //  RSK-07: سقفِ شماره (lib/order-rate) — کِی دوباره
    if (err && err.retryAfter) res.set('Retry-After', String(err.retryAfter));
    throw err;
  }
  if (out.replayed) {
    res.set('Idempotent-Replayed', 'true');
    return res.status(200).json({ order: out.order, replayed: true });
  }
  //  ORD-05: زمان‌سنجِ قبول — فقط اگر دکان همین لحظه می‌پذیرد (سفارشِ «بعداً» نه).
  //  شکستش سفارش را پس نمی‌گیرد؛ فقط این سفارش بی زمان‌سنج می‌ماند.
  await orderTimeout.arm(req.store, out.orderId, { at: out.order.createdAt })
    .catch((err) => console.error('[order-timeout] arm', err.message));
  await audit.log({
    shopId: req.store.shop_id, actorType: 'customer', action: 'order.create',
    targetType: 'online_order', targetId: out.orderId,
    detail: { orderNo: out.order.orderNo, source: 'storefront', totalMinor: out.order.totalMinor },
    ip: clientIp(req),
  });
  res.status(201).json({ order: out.order });
}));

router.get('/:slug/orders', readLimit, store, wrap(async (req, res) => {
  res.json({ orders: await sf.myOrders(req.store, ticketOf(req), sessionOf(req)) });
}));

router.get('/:slug/orders/:token', readLimit, store, wrap(async (req, res) => {
  res.json({ order: await sf.track(req.store, req.params.token) });
}));

//  WEB-12: «خریدِ دوباره» — همان توکنِ پیگیری؛ فقط خواندن
router.get('/:slug/orders/:token/reorder', readLimit, store, wrap(async (req, res) => {
  res.json(await sf.reorder(req.store, req.params.token));
}));

//  ── حسابِ مشتری (۲۳، CHK-01/02/09، S134/S138/S140) ───────────────────
//  ساختن یا ورود فقط با بلیتِ «شماره تأیید شد» که همین چند دقیقه پیش صادر شده
//  (نه هر بلیتِ ۱۴ روزه) — وگرنه یک بلیت هر چند نشستِ ۱۸۰ روزه می‌ساخت
router.post('/:slug/account', checkoutLimit, store, wrap(async (req, res) => {
  const phone = await sf.verifiedPhoneOfTicket(ticketOf(req), { maxAgeMs: config.storefront.accountTicketMaxMs });
  if (!phone) throw new ApiError(401, 'verification_required', 'برای ورود به حساب، همین حالا شمارهٔ خود را با کد تأیید کنید');
  const out = await customers.signIn(phone, { name: req.body && req.body.name });
  await audit.log({
    shopId: req.store.shop_id, actorType: 'customer', action: 'store_customer.sign_in',
    targetType: 'store_customer', targetId: out.customer.id, detail: {}, ip: clientIp(req),
  });
  res.status(201).json({ session: out.token, expiresAt: out.expiresAt, customer: out.customer });
}));

router.get('/:slug/account', readLimit, store, customer, wrap(async (req, res) => {
  res.json({
    customer: customers.view(req.customer), expiresAt: req.session.expiresAt,
    addresses: await customers.addresses(req.customer),
  });
}));

router.put('/:slug/account', checkoutLimit, store, customer, wrap(async (req, res) => {
  res.json({ customer: await customers.rename(req.customer, req.body) });
}));

router.delete('/:slug/account', checkoutLimit, store, customer, wrap(async (req, res) => {
  const out = await customers.remove(req.customer);
  await audit.log({
    shopId: req.store.shop_id, actorType: 'customer', action: 'store_customer.delete',
    targetType: 'store_customer', targetId: req.customer.id, detail: {}, ip: clientIp(req),
  });
  res.json(out);
}));

router.post('/:slug/account/logout', checkoutLimit, store, wrap(async (req, res) => {
  res.json(await customers.signOut(sessionOf(req)));
}));

//  «خروج از همه‌جا»: همهٔ نشست‌های حساب + همهٔ بلیت‌های این شماره
router.post('/:slug/account/logout-all', checkoutLimit, store, customer, wrap(async (req, res) => {
  const out = await customers.signOutAll(req.customer);
  await audit.log({
    shopId: req.store.shop_id, actorType: 'customer', action: 'store_customer.sign_out_all',
    targetType: 'store_customer', targetId: req.customer.id, detail: { sessions: out.signedOut }, ip: clientIp(req),
  });
  res.json(out);
}));

router.get('/:slug/account/orders', readLimit, store, customer, wrap(async (req, res) => {
  res.json(await sf.ordersOfPhone(req.store, req.customer.phone, { cursor: req.query.cursor, limit: req.query.limit }));
}));

router.get('/:slug/account/addresses', readLimit, store, customer, wrap(async (req, res) => {
  res.json({ addresses: await customers.addresses(req.customer) });
}));

router.post('/:slug/account/addresses', checkoutLimit, store, customer, wrap(async (req, res) => {
  res.status(201).json({ address: await customers.addAddress(req.customer, req.body) });
}));

router.put('/:slug/account/addresses/:id', checkoutLimit, store, customer, wrap(async (req, res) => {
  res.json({ address: await customers.updateAddress(req.customer, req.params.id, req.body) });
}));

router.delete('/:slug/account/addresses/:id', checkoutLimit, store, customer, wrap(async (req, res) => {
  res.json(await customers.removeAddress(req.customer, req.params.id));
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
