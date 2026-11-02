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
const sfm = require('../lib/storefront-media');
const customers = require('../lib/store-customers');
const extras = require('../lib/storefront-extras');
const finder = require('../lib/storefront-filters');
const cart = require('../lib/store-cart');
const storeSeo = require('../lib/store-seo');
const views = require('../lib/public-views');
const orders = require('../lib/orders');
const orderTimeout = require('../lib/order-timeout');
const campaigns = require('../lib/campaigns');
const areas = require('../lib/storefront-area-requests');
const onlineReport = require('../lib/order-online-report');
const orderDesk = require('../lib/order-desk');
const audit = require('../lib/audit');
const pay = require('../lib/payment-intents');
const { sniff } = require('../lib/product-photos');
const { rateLimit, clientIp } = require('../middleware/ratelimit');
const { notFound, ApiError } = require('../middleware/errors');

const router = express.Router();
//  S2: پرچمِ `market.public_views` پیش از هر پاسخ (خاموش ⇒ پاسخِ بی بُرش، همان main)
router.use(views.gate);
const RECENT_FLAG = 'shop.storefront.recent';
/** E2E-MKT-02: ۴۰۹ ِ `out_of_stock` در ویترین با فهرستِ همهٔ قلم‌های کم و «کم کن به …» (پیش‌فرض خاموش) */
const STOCK_LIST_FLAG = 'shop.storefront.stock_list';
//  CHK-08: شمارهٔ دوم و Plus Code در فرمِ نشانیِ وب (پیش‌فرض خاموش ⇒ همان فرم)
const ADDRESS_EXTRA_FLAG = 'shop.storefront.address_extra';

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
const store = async (req, res, next) => {
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
};

const ticketOf = (req) => req.get('x-store-ticket') || '';
const sessionOf = (req) => req.get('x-store-session') || '';

/** حسابِ مشتری (نشستِ `X-Store-Session`) — یا ۴۰۱. */
const customer = async (req, res, next) => {
  const a = await customers.auth(sessionOf(req));
  if (!a) throw new ApiError(401, 'session_required', 'برای این کار وارد حسابِ خود شوید');
  req.customer = a.customer;
  req.session = a;
  next();
};

//  ── S3: ویترین با شناسهٔ عمومیِ پایدار — `/api/store?id=<ULID>` ⇒ نشانیِ امروز ──
//  (پرچمِ خاموش، ویترینِ بسته یا شناسهٔ ناموجود ⇒ همان ۴۰۴ ِ امروزِ این نشانی)
router.get('/', (req, res, next) => next(typeof req.query.id === 'string' ? undefined : 'route'), readLimit, async (req, res, next) => {
  const hit = await sf.byPublicId(req.query.id);
  if (!hit) return next();
  res.set('Cache-Control', 'no-store').json(hit);
});

router.get('/:slug', readLimit, store, async (req, res) => {
  const [cats, status, delivery, seo, media, pid] = await Promise.all([
    sf.categories(req.store.shop_id), sf.statusOf(req.store).then((st) => orderDesk.overlay(req.store.shop_id, st)), sfd.publicInfo(req.store),
    storeSeo.publicInfo(req.store), sfm.publicView(req.store), sf.publicIdOn(req.store.shop_id),
  ]);
  res.json({
    //  `payment`: «حساب‌پی» فقط با کلید + پرچمِ `pay.hesabpay` + اشتراکِ فعال (PAY-21)؛ وگرنه نقدِ هنگامِ تحویل
    store: views.pub('shop', {
      ...sf.profile(req.store, cats, status), payment: await pay.storefrontPayment(req.store.shop_id), delivery, media,
      ...(pid ? { publicId: req.store.public_id } : {}),
      //  shop.orders.subscribers_only روشن ⇒ `ordering` («سفارش می‌پذیرد؟»)؛ ثبت را `orders.create` می‌بندد. خاموش ⇒ کلید نیست (پاسخِ main)
      ...require('../lib/storefront-intake').orderingOf(req.store),
    }),
    verification: { available: await sf.verificationAvailable() },
    //  پیوندِ «بازارچه» فقط وقتی بازارچه باز است (پرچمِ `market.directory`)
    market: { available: await require('../lib/market').directoryOn() },
    //  WEB-13: «دیده‌شده‌های اخیر» (`recent.js`، فقط روی دستگاه) با پرچمِ `shop.storefront.recent`؛ خاموش ⇒ این کلید نیست
    ...((await orders.flagOf(req.store.shop_id, RECENT_FLAG)) ? { recent: true } : {}),
    //  فقط با پرچمِ روشن (E2E-MKT-02)؛ خاموش ⇒ همان پاسخِ پیشین، بی این کلید
    ...await featuresOf(req.store.shop_id),
    //  WEB-20: پیوندِ قابلِ پیش‌نمایشِ کالا فقط با پرچمِ `shop.storefront.seo`؛ خاموش ⇒ این کلید نیست
    ...(seo ? { seo } : {}),
  });
});

/** `features` فقط با پرچم‌های روشن؛ هیچ‌کدام ⇒ کلید نیست (پاسخِ پیشین) */
async function featuresOf(shopId) {
  const f = {
    ...((await orders.flagOf(shopId, STOCK_LIST_FLAG)) ? { stockList: true } : {}),
    ...((await orders.flagOf(shopId, ADDRESS_EXTRA_FLAG)) ? { addressExtra: true } : {}),
    //  WEB-11: ♥ و «فهرستِ خرید» ِ وب (`store/catalog.js`، روی همین دستگاه) با پرچمِ `shop.storefront.favorites`
    ...((await extras.favoritesOn(shopId)) ? { favorites: true } : {}),
    //  CHK-14: «با اسکناسِ … پرداخت می‌کنم» در فرمِ ثبتِ وب با پرچمِ `shop.orders.delivery_prefs` (همان درِ `delivery.cashTenderMinor`)
    ...((await require('../lib/delivery-prefs').flagOn(shopId)) ? { deliveryPrefs: true } : {}),
    //  WEB-24: نمایشِ بنر در وب (`GET /:slug/banners`) با پرچمِ `shop.storefront.banners`
    ...((await extras.bannersOn(shopId)) ? { banners: true } : {}),
    //  WEB-07/10: فیلترِ کاتالوگ و «خبرم کن» ِ وب (`store/filters.js` ِ دیربار) با `shop.storefront.filters`/`.alerts`
    ...((await finder.flagOn(shopId)) ? { filters: true } : {}),
    ...((await require('../lib/storefront-alerts').flagOn(shopId)) ? { alerts: true } : {}),
  };
  return Object.keys(f).length ? { features: f } : {};
}

//  ── WEB-02: پرفروش‌ها و تازه‌ها (دادهٔ واقعی؛ نبود ⇒ خالی) ───────────
router.get('/:slug/highlights', readLimit, store, async (req, res) => {
  res.json(await sf.highlights(req.store.shop_id));
});

//  ── CHK-07/10: کدام ناحیه؟ (بی ذخیره، بی ژئوکدر؛ با خانهٔ ۳۰۰ متری) ──
router.get('/:slug/delivery', readLimit, locateLimit, store, async (req, res) => {
  res.json(await sfd.locate(req.store, req.query.lat, req.query.lng));
});

//  ── CHK-12: بازه‌های پیشِ رو — فقط «هست/پر/کم مانده» ─────────────────
router.get('/:slug/slots', readLimit, store, async (req, res) => {
  res.json(await sfd.publicSlots(req.store, String(req.query.fulfillment || '')));
});

//  ── CHK-10/S34: «وقتی به محلِ شما رسیدیم خبرم کن» ─────────────────────
//  ⛔ پرچمِ خاموشِ `shop.storefront.notify_area` (یا ناحیهٔ خاموش، یا ویترینِ بسته) ⇒
//  `next('route')` پیش از هر سقف و شماره: همان پاسخِ مسیرِ ناشناختهٔ main، بیت‌به‌بیت.
const areaStore = async (req, res, next) => {
  //  ویترینِ نبوده یا بسته (اشتراکِ منقضی/معلق/لغو) ⇒ همان ۴۰۴ `store_not_found` ِ درهای دیگرِ ویترین (SUB-10)،
  //  پیش از سنجشِ شماره؛ پرچمِ خاموش ⇒ این در نیست.
  const s = await sf.open(req.params.slug);
  if (!(await areas.flagOn(s.shop_id))) return next('route');
  req.store = s;
  res.set('Cache-Control', 'no-store');
  next();
};
//  شمارهٔ تأییدشده: بلیتِ همین چند روز یا نشستِ حساب — همان که دکان با آن خبر می‌دهد
const verified = async (req, res, next) => {
  const phone = await sf.verifiedPhone(ticketOf(req), sessionOf(req));
  if (!phone) throw new ApiError(401, 'verification_required', 'برای «خبرم کن»، شمارهٔ خود را با کد تأیید کنید');
  req.verifiedPhone = phone;
  next();
};

router.post('/:slug/area-request', areaStore, checkoutLimit, verified, async (req, res) => {
  const out = await areas.request(req.store, req.verifiedPhone, req.body);
  res.status(out.created ? 201 : 200).json({ request: out.request });
});

//  پس گرفتنِ رضایت — تکرارش همان پاسخ
router.delete('/:slug/area-request', areaStore, checkoutLimit, verified, async (req, res) => {
  res.json(await areas.withdraw(req.store, req.verifiedPhone));
});

router.get('/:slug/products', readLimit, store, async (req, res) => {
  const out = req.query.ids !== undefined
    ? await sf.catalogByIds(req.store.shop_id, req.query.ids)
    : await sf.catalog(req.store.shop_id, {
      q: req.query.q, category: req.query.category, cursor: req.query.cursor, limit: req.query.limit,
      contactPhone: req.store.contact_phone,
      //  WEB-07: برند، قیمت، موجود بودن و مشخصه (پرچمِ خاموشِ `shop.storefront.filters` ⇒ null و همان پاسخ)
      filters: await finder.parse(req.store.shop_id, req.query),
    });
  //  RPT-14: صفحهٔ نخستِ جست‌وجوی بی‌نتیجه شمرده می‌شود (فقط با پرچمِ گزارش؛ خطا جواب را خراب نمی‌کند)
  if (req.query.ids === undefined && !req.query.cursor && req.query.q && !out.items.length) {
    await onlineReport.noteMiss(req.store.shop_id, req.query.q);
  }
  //  ۳۲.۳: با پرچمِ `shop.catalog` وضعیتِ موجودی از دفترِ سرور (#263)؛ خاموش ⇒ null
  //  و همان «در حال بررسی». موجودیِ نامعلوم/کهنه هرگز «موجود» نیست (`badgeOf`).
  const stock = out.items.length
    ? await require('../lib/catalog').publicStock(req.store.shop_id, out.items.map((p) => p.id)) : null;
  if (stock) out.items = out.items.map((p) => ({ ...p, stock: stock.get(p.id) || p.stock }));
  res.json({ ...out, items: views.pubList('product', out.items) });
});

//  ── WEB-08: صفحهٔ کالا ───────────────────────────────────────────────
router.get('/:slug/products/:id', readLimit, store, async (req, res) => {
  res.json(await sf.productDetail(req.store.shop_id, req.params.id));
});

/** عکس: `display` برای صفحهٔ کالا، `thumb` برای کارت (WEB-16). */
const picture = (size) => async (req, res) => {
  const p = await sf.photo(req.store.shop_id, req.params.id, size);
  if (!p) throw notFound('عکس نیست', 'photo_not_found');
  const etag = `"${p.hash}"`;
  //  پاسخ‌های JSON ِ ویترین کش نمی‌شوند؛ عکس با ETag ِ محتوا می‌شود
  res.set('Cache-Control', 'public, max-age=300');
  res.set('ETag', etag);
  if (req.get('if-none-match') === etag) return res.status(304).end();
  res.type(p.mime || sniff(p.data) || 'application/octet-stream').send(p.data);
};
//  ── M3: لوگو و تصویرِ سر — فقط نسخهٔ جاری. نشانی نسخه‌دار است ولی کشِ عمومی
//  یک روز می‌ماند (نه immutable) تا «برداشتنِ» عکس دیر یا زود به همه برسد ──
router.get('/:slug/media/:kind/:version', readLimit, store, async (req, res) => {
  const kind = String(req.params.kind);
  const f = sfm.KINDS.includes(kind) && await sfm.flagOn(req.store.shop_id)
    ? await sfm.file(req.store.shop_id, kind, req.params.version, { onlyCurrent: true }) : null;
  if (!f) throw notFound('عکس نیست', 'media_not_found');
  res.set('Cache-Control', 'public, max-age=86400');
  res.set('ETag', `"${f.hash}"`);
  res.set('X-Content-Type-Options', 'nosniff');
  if (req.get('if-none-match') === `"${f.hash}"`) return res.status(304).end();
  res.type(f.mime).send(f.data);
});
router.get('/:slug/products/:id/photo', readLimit, store, picture('display'));
router.get('/:slug/products/:id/thumb', readLimit, store, picture('thumb'));

//  ── ۲۳: تأییدِ شماره پیش از ثبتِ سفارش ──────────────────────────────
router.post('/:slug/verify/start', sendCodeLimit, store, async (req, res) => {
  res.json(await sf.startVerification(req.body, { ip: clientIp(req), shopId: req.store.shop_id }));
});

router.post('/:slug/verify/check', codeLimit, store, async (req, res) => {
  res.json(await sf.checkVerification(req.body));
});

//  ── ثبت: فقط از راهِ `orders.create` با کلیدِ یکتایی (CHK-19) ───────
router.post('/:slug/checkout', checkoutLimit, store, async (req, res) => {
  const key = orders.clientKeyOf(req);
  //  ORD-08/09: توقفِ موقتِ سفارش‌گیری ⇒ ۴۰۹ `store_paused` (تکرارِ سفارشِ پیشین آزاد)
  await orderDesk.guardCheckout(req.store.shop_id, key);
  let out;
  try {
    out = await sf.checkout(req.store, { key, body: req.body, ticket: ticketOf(req), session: sessionOf(req) });
  } catch (err) {
    //  RSK-07: سقفِ شماره (lib/order-rate) — کِی دوباره
    if (err && err.retryAfter) res.set('Retry-After', String(err.retryAfter));
    throw err;
  }
  //  MKT-11: سفارش ⇐ پیوندِ کمپین (`campaign` ِ بدنه، همان `?c=` ِ پیوند). بی‌صدا و
  //  پس از ثبت: کدِ نادرست یا شکستِ این‌جا سفارش را نه می‌بندد و نه عوض می‌کند.
  await campaigns.attribute(req.store, out.orderId, req.body && req.body.campaign)
    .catch((err) => console.error('[campaigns] attribute', err.message));
  if (out.replayed) {
    res.set('Idempotent-Replayed', 'true');
    return res.status(200).json({ order: out.order, replayed: true });
  }
  //  ORD-05: زمان‌سنجِ قبول — فقط اگر دکان همین لحظه می‌پذیرد (سفارشِ «بعداً» نه).
  //  شکستش سفارش را پس نمی‌گیرد؛ فقط این سفارش بی زمان‌سنج می‌ماند.
  await orderTimeout.arm(req.store, out.orderId, { at: out.order.createdAt })
    .catch((err) => console.error('[order-timeout] arm', err.message));
  //  ORD-04 قبولِ خودکار و ORD-09 سنجشِ توقف (پرچمِ `shop.orders.desk`)؛ شکست سفارش را پس نمی‌گیرد
  await orderDesk.afterOrder(req.store, out.orderId).catch((err) => console.error('[order-desk] after', err.message));
  await audit.log({
    shopId: req.store.shop_id, actorType: 'customer', action: 'order.create',
    targetType: 'online_order', targetId: out.orderId,
    detail: { orderNo: out.order.orderNo, source: 'storefront', totalMinor: out.order.totalMinor },
    ip: clientIp(req),
  });
  res.status(201).json({ order: out.order });
});

router.get('/:slug/orders', readLimit, store, async (req, res) => {
  res.json({ orders: await sf.myOrders(req.store, ticketOf(req), sessionOf(req)) });
});

router.get('/:slug/orders/:token', readLimit, store, async (req, res) => {
  //  PAY-21: قصدِ گذشته از زمان پیش از خواندن بسته می‌شود؛ دکمه فقط وقتی همین حالا پرداختنی است
  const orderId = sf.orderIdOf(req.store.shop_id, req.params.token);
  await pay.refreshOrder(req.store.shop_id, orderId);
  const order = await sf.track(req.store, req.params.token);
  order.onlinePay = { available: await pay.orderPayable(req.store.shop_id, orderId) };
  //  NTF-08: پیگیریِ بازپرداخت — فقط با پرچمِ `shop.orders.refund_tracking` (خاموش ⇒ کلید نیست)
  const refund = await require('../lib/order-refund-view').viewOf(req.store.shop_id, orderId);
  if (refund !== undefined) order.refund = refund;
  //  CHK-24: «ویرایشِ دستورِ تحویل» فقط با پرچمِ `shop.orders.delivery_prefs` (خاموش ⇒ کلید نیست)
  const prefs = await require('../lib/delivery-prefs').customerPrefs(req.store.shop_id, orderId);
  if (prefs !== undefined) order.deliveryPrefs = prefs;
  res.json({ order });
});

/**
 * PAY-21: پرداختِ سفارشِ ویترین با حساب‌پی — توکنِ پیگیری همان اجازه است؛ مبلغ و نشانیِ بازگشت را
 * سرور می‌سازد. پاسخ فقط نشانیِ صفحهٔ حساب‌پی است؛ «پرداخت شد» فقط با وب‌هوک و تأییدِ دوم، هرگز با
 * برگشتِ مرورگر (صفحهٔ پیگیری وضعیت را از همین سرور می‌خواند).
 */
router.post('/:slug/orders/:token/pay', checkoutLimit, store, async (req, res) => {
  await sf.track(req.store, req.params.token);   // توکنِ درست و سفارشِ همین ویترین — وگرنه ۴۰۴
  const orderId = sf.orderIdOf(req.store.shop_id, req.params.token);
  const base = String(config.serverUrl || '').trim().replace(/\/+$/, '');
  const page = base ? `${base}/store/${encodeURIComponent(req.store.slug)}`
    : `https://${req.store.slug}.${config.storefront.domain}/`;
  let out;
  try {
    out = await pay.createForStorefront(req.store.shop_id, orderId, {
      key: req.get('idempotency-key') || '', returnUrl: `${page}#/o/${encodeURIComponent(req.params.token)}`,
      description: String(req.store.title || '').slice(0, 100),
    });
  } catch (err) {
    if (err && err.code === 'provider_unavailable') {
      return res.status(502).json({ error: { code: err.code, message: err.message } });
    }
    throw err;
  }
  const p = out.intent;
  res.status(out.created ? 201 : 200).json({
    payment: { status: p.status, checkoutUrl: p.checkoutUrl, expiresAt: p.expiresAt, amountMinor: p.amountMinor, currency: p.currency },
  });
});

//  WEB-12: «خریدِ دوباره» — همان توکنِ پیگیری؛ فقط خواندن
router.get('/:slug/orders/:token/reorder', readLimit, store, async (req, res) => {
  res.json(await sf.reorder(req.store, req.params.token));
});

//  ── حسابِ مشتری (۲۳، CHK-01/02/09، S134/S138/S140) ───────────────────
//  ساختن یا ورود فقط با بلیتِ «شماره تأیید شد» که همین چند دقیقه پیش صادر شده
//  (نه هر بلیتِ ۱۴ روزه) — وگرنه یک بلیت هر چند نشستِ ۱۸۰ روزه می‌ساخت
router.post('/:slug/account', checkoutLimit, store, async (req, res) => {
  const phone = await sf.verifiedPhoneOfTicket(ticketOf(req), { maxAgeMs: config.storefront.accountTicketMaxMs });
  if (!phone) throw new ApiError(401, 'verification_required', 'برای ورود به حساب، همین حالا شمارهٔ خود را با کد تأیید کنید');
  const out = await customers.signIn(phone, { name: req.body && req.body.name });
  await audit.log({
    shopId: req.store.shop_id, actorType: 'customer', action: 'store_customer.sign_in',
    targetType: 'store_customer', targetId: out.customer.id, detail: {}, ip: clientIp(req),
  });
  res.status(201).json({ session: out.token, expiresAt: out.expiresAt, customer: out.customer });
});

router.get('/:slug/account', readLimit, store, customer, async (req, res) => {
  res.json({
    customer: customers.view(req.customer), expiresAt: req.session.expiresAt,
    addresses: await customers.addresses(req.customer),
  });
});

router.put('/:slug/account', checkoutLimit, store, customer, async (req, res) => {
  res.json({ customer: await customers.rename(req.customer, req.body) });
});

router.delete('/:slug/account', checkoutLimit, store, customer, async (req, res) => {
  const out = await customers.remove(req.customer);
  await audit.log({
    shopId: req.store.shop_id, actorType: 'customer', action: 'store_customer.delete',
    targetType: 'store_customer', targetId: req.customer.id, detail: {}, ip: clientIp(req),
  });
  res.json(out);
});

router.post('/:slug/account/logout', checkoutLimit, store, async (req, res) => {
  res.json(await customers.signOut(sessionOf(req)));
});

//  «خروج از همه‌جا»: همهٔ نشست‌های حساب + همهٔ بلیت‌های این شماره
router.post('/:slug/account/logout-all', checkoutLimit, store, customer, async (req, res) => {
  const out = await customers.signOutAll(req.customer);
  await audit.log({
    shopId: req.store.shop_id, actorType: 'customer', action: 'store_customer.sign_out_all',
    targetType: 'store_customer', targetId: req.customer.id, detail: { sessions: out.signedOut }, ip: clientIp(req),
  });
  res.json(out);
});

router.get('/:slug/account/orders', readLimit, store, customer, async (req, res) => {
  res.json(await sf.ordersOfPhone(req.store, req.customer.phone, { cursor: req.query.cursor, limit: req.query.limit }));
});

router.get('/:slug/account/addresses', readLimit, store, customer, async (req, res) => {
  res.json({ addresses: await customers.addresses(req.customer) });
});

router.post('/:slug/account/addresses', checkoutLimit, store, customer, async (req, res) => {
  res.status(201).json({ address: await customers.addAddress(req.customer, req.body) });
});

router.put('/:slug/account/addresses/:id', checkoutLimit, store, customer, async (req, res) => {
  res.json({ address: await customers.updateAddress(req.customer, req.params.id, req.body) });
});

router.delete('/:slug/account/addresses/:id', checkoutLimit, store, customer, async (req, res) => {
  res.json(await customers.removeAddress(req.customer, req.params.id));
});

//  ── WEB-11: علاقه‌مندی‌های حسابِ مشتری (پرچمِ خاموشِ `shop.storefront.favorites` ⇒ ۴۰۴) ──
const favoritesOn = async (req, res, next) => {
  if (!(await extras.favoritesOn(req.store.shop_id))) throw notFound('این بخش در این ویترین نیست', 'favorites_disabled');
  next();
};

router.get('/:slug/account/favorites', readLimit, store, customer, favoritesOn, async (req, res) => {
  res.json(await extras.favorites(req.store.shop_id, req.customer));
});

router.put('/:slug/account/favorites/:productId', checkoutLimit, store, customer, favoritesOn, async (req, res) => {
  res.json(await extras.addFavorite(req.store.shop_id, req.customer, req.params.productId));
});

router.delete('/:slug/account/favorites/:productId', checkoutLimit, store, customer, favoritesOn, async (req, res) => {
  res.json(await extras.removeFavorite(req.store.shop_id, req.customer, req.params.productId));
});

//  ── WEB-24: بنرهای همین لحظه برای ناحیهٔ مشتری (پرچمِ خاموشِ `shop.storefront.banners` ⇒ ۴۰۴) ──
router.get('/:slug/banners', readLimit, store, async (req, res) => {
  if (!(await extras.bannersOn(req.store.shop_id))) throw notFound('این بخش در این ویترین نیست', 'banners_disabled');
  res.json({ banners: await extras.activeBanners(req.store.shop_id, { zone: req.query.zone }) });
});

//  ── CHK-03: سبدِ ماندگارِ حساب روی سرور (S140) ─────────────────────
//  ⛔ ویترینِ نبوده یا بسته (اشتراکِ منقضی/معلق/لغو) ⇒ همان ۴۰۴ `store_not_found` ِ درهای دیگرِ ویترین
//  (SUB-10، مثلِ #684)، پیش از نشست؛ پرچمِ خاموشِ `shop.storefront.cart` ⇒ `next('route')`: این در نیست.
const cartStore = async (req, res, next) => {
  const s = await sf.open(req.params.slug);
  if (!(await cart.flagOn(s.shop_id))) return next('route');
  req.store = s;
  res.set('Cache-Control', 'no-store');
  next();
};

router.get('/:slug/account/cart', cartStore, readLimit, customer, async (req, res) => {
  res.json({ cart: await cart.get(req.store, req.customer) });
});

//  جایگزینیِ کامل با نسخهٔ دیده‌شده؛ فهرستِ خالی = خالی کردن (پس از ثبتِ سفارش هم)
router.put('/:slug/account/cart', cartStore, checkoutLimit, customer, async (req, res) => {
  res.json({ cart: await cart.replace(req.store, req.customer, req.body) });
});

//  سبدِ مهمانِ همین مرورگر پس از ورود — تکرارش اثرِ دوم ندارد
router.post('/:slug/account/cart/merge', cartStore, checkoutLimit, customer, async (req, res) => {
  res.json({ cart: await cart.merge(req.store, req.customer, req.body) });
});

router.post('/:slug/orders/:token/cancel', checkoutLimit, store, async (req, res) => {
  res.json(await sf.cancel(req.store, req.params.token));
});

//  CHK-24: ویرایشِ دستورِ تحویل تا پیش از ارسال (پرچمِ خاموشِ `shop.orders.delivery_prefs`)
router.post('/:slug/orders/:token/delivery', checkoutLimit, store, async (req, res) => {
  const P = require('../lib/delivery-prefs');
  const out = await P.editByToken(req.store, req.params.token, req.body);
  const prefs = await P.customerPrefs(req.store.shop_id, sf.orderIdOf(req.store.shop_id, req.params.token));
  if (prefs !== undefined) out.order.deliveryPrefs = prefs;
  res.json(out);
});

/** خطایی که دادهٔ همراه دارد (برگهٔ قیمتِ تازه، کالای ناموجود، …) — همه‌اش برود. */
// eslint-disable-next-line no-unused-vars
router.use((err, req, res, next) => {
  if (!err || !err.data || err.expose !== true) return next(err);
  res.status(err.status).json({ error: { code: err.code, message: err.message, ...err.data } });
});

module.exports = router;
