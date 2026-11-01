'use strict';
/**
 * بازارچهٔ عمومی — `/api/market/…` (WEB، §۲۳، §۳۱.۱؛ `lib/market.js`).
 *
 * ⛔ بی‌توکن و عمومی، ولی پیش‌فرض خاموش: بی پرچمِ `market.directory` (scope
 * `all`) همه‌چیز ۴۰۴ `market_not_found` است و ویترینِ هر دکان همان رفتارِ امروز.
 * ⛔ سقفِ درخواست به‌ازای IP: خواندن سطلِ خودش را با `generalMax`، جست‌وجوی
 * کالا `marketSearchBurstMax` در دقیقه و `marketSearchMax` در ربع ساعت، و
 * تأییدِ شماره **همان سطل‌های ویترین** (یک IP با دو نشانی دو برابر تلاش نمی‌گیرد).
 * پاسخ‌ها در مرورگر کش نمی‌شوند؛ سرور «برشِ» دکان‌ها را `eligibleCacheMs` نگه
 * می‌دارد (lib/market.js).
 *
 * حسابِ مشتری بی دکان — `/api/market/account/…` (S9): همان `store_customers`،
 * همان نشستِ `X-Store-Session` و همان قاعده‌های `/api/store/:slug/account`
 * (`lib/store-customers.js`)، به‌علاوهٔ تغییرِ شماره. ⛔ پیش‌فرض خاموش: افزون بر
 * `market.directory` پرچمِ سراسریِ `market.account` هم لازم است؛ نباشد ⇒ ۴۰۴.
 */
const express = require('express');
const config = require('../config');
const market = require('../lib/market');
const sf = require('../lib/storefront');
const customers = require('../lib/store-customers');
const audit = require('../lib/audit');
const { rateLimit, clientIp } = require('../middleware/ratelimit');
const { notFound, ApiError } = require('../middleware/errors');

const router = express.Router();

const readLimit = rateLimit({ max: config.rateLimit.generalMax, keyPrefix: 'market-read' });
const searchLimit = rateLimit({ max: config.rateLimit.marketSearchMax, keyPrefix: 'market-search' });
//  سقفِ ضربه‌ای: چند جست‌وجو در دقیقه از یک IP، جدا از سقفِ ربع‌ساعته
const searchBurst = rateLimit({ max: config.rateLimit.marketSearchBurstMax, windowMs: 60 * 1000, keyPrefix: 'market-search-burst' });
//  خانه (قفسه‌ها و دو فهرست) همان سقفِ دقیقه‌ای را دارد، با سطلِ خودش
const feedBurst = rateLimit({ max: config.rateLimit.marketFeedBurstMax, windowMs: 60 * 1000, keyPrefix: 'market-feed-burst' });
//  همان کلیدهای `routes/storefront.js` ⇒ همان سطل
const sendCodeLimit = rateLimit({ max: config.rateLimit.storeCheckoutMax, keyPrefix: 'store-verify' });
const codeLimit = rateLimit({ max: config.rateLimit.verifyMax, keyPrefix: 'store-verify-code' });
const writeLimit = rateLimit({ max: config.rateLimit.storeCheckoutMax, keyPrefix: 'store-checkout' });

const on = async (req, res, next) => {
  await market.requireOn();
  res.set('Cache-Control', 'no-store');
  next();
};

router.get('/', readLimit, on, async (req, res) => {
  res.json(await market.overview());
});

router.get('/shops', readLimit, on, async (req, res) => {
  const { q, category, lat, lng, limit, offset, first } = req.query;
  res.json(await market.shops({ q, category, lat, lng, limit, offset, first }));
});

//  خانه: قفسهٔ دکان‌ها، «پرفروش» و «ارزان‌تر» ِ واقعی؛ `only=lists` ⇒ فقط دو فهرست
router.get('/products', feedBurst, readLimit, on, async (req, res) => {
  const { category, lat, lng, limit, only } = req.query;
  res.json(await market.feed({ category, lat, lng, limit, only }));
});

router.get('/search', searchBurst, searchLimit, on, async (req, res) => {
  const { q, lat, lng, limit } = req.query;
  res.json(await market.search({ q, lat, lng, limit }));
});

//  M4: «همین کالا در دکان‌های دیگر» با شناسهٔ کالای بانک (پرچمِ `market.product_details`)
router.get('/same/:bankProductId', searchBurst, readLimit, on, async (req, res) => {
  const { lat, lng, limit, exclude } = req.query;
  res.json(await market.sameProduct(req.params.bankProductId, { lat, lng, limit, exclude }));
});

//  ── ۲۳: تأییدِ شماره بی دکان (برای «سفارش‌های من») — همان بلیتِ ویترین ──
router.post('/verify/start', sendCodeLimit, on, async (req, res) => {
  res.json(await sf.startVerification(req.body, { ip: clientIp(req) }));
});

router.post('/verify/check', codeLimit, on, async (req, res) => {
  res.json(await sf.checkVerification(req.body));
});

router.get('/orders', readLimit, on, async (req, res) => {
  res.json({ orders: await market.myOrders(req.get('x-store-ticket') || '') });
});

//  ── حسابِ مشتری بی دکان (S9) ─────────────────────────────────────────
const accountOn = async (req, res, next) => {
  if (!(await market.accountOn())) throw notFound('بازارچه هنوز باز نیست', 'market_not_found');
  next();
};
const ticketOf = (req) => req.get('x-store-ticket') || '';
const sessionOf = (req) => req.get('x-store-session') || '';
/** بلیتِ «شماره تأیید شد» که همین چند دقیقه پیش صادر شده — همان سقفِ ویترین. */
const freshPhone = (req) => sf.verifiedPhoneOfTicket(ticketOf(req), { maxAgeMs: config.storefront.accountTicketMaxMs });
const customer = async (req, res, next) => {
  const a = await customers.auth(sessionOf(req));
  if (!a) throw new ApiError(401, 'session_required', 'برای این کار وارد حسابِ خود شوید');
  req.customer = a.customer;
  req.session = a;
  next();
};
const acct = [on, accountOn];
const log = (req, action, targetId, detail = {}) => audit.log({
  actorType: 'customer', action, targetType: 'store_customer', targetId, detail, ip: clientIp(req),
});

router.post('/account', writeLimit, ...acct, async (req, res) => {
  const phone = await freshPhone(req);
  if (!phone) throw new ApiError(401, 'verification_required', 'برای ورود به حساب، همین حالا شمارهٔ خود را با کد تأیید کنید');
  const out = await customers.signIn(phone, { name: req.body && req.body.name });
  await log(req, 'store_customer.sign_in', out.customer.id, { source: 'market' });
  res.status(201).json({ session: out.token, expiresAt: out.expiresAt, customer: out.customer });
});

router.get('/account', readLimit, ...acct, customer, async (req, res) => {
  res.json({
    customer: customers.view(req.customer), expiresAt: req.session.expiresAt,
    addresses: await customers.addresses(req.customer),
  });
});

router.put('/account', writeLimit, ...acct, customer, async (req, res) => {
  res.json({ customer: await customers.rename(req.customer, req.body) });
});

router.delete('/account', writeLimit, ...acct, customer, async (req, res) => {
  const out = await customers.remove(req.customer);
  await log(req, 'store_customer.delete', req.customer.id, { source: 'market' });
  res.json(out);
});

router.post('/account/logout', writeLimit, ...acct, async (req, res) => {
  res.json(await customers.signOut(sessionOf(req)));
});

router.post('/account/logout-all', writeLimit, ...acct, customer, async (req, res) => {
  const out = await customers.signOutAll(req.customer);
  await log(req, 'store_customer.sign_out_all', req.customer.id, { sessions: out.signedOut, source: 'market' });
  res.json(out);
});

//  تغییرِ شماره: نشستِ همین حساب + بلیتِ تازهٔ شمارهٔ نو (`X-Store-Ticket`)
router.post('/account/phone', writeLimit, ...acct, customer, async (req, res) => {
  const phone = await freshPhone(req);
  if (!phone) throw new ApiError(401, 'verification_required', 'شمارهٔ تازه را همین حالا با کد تأیید کنید');
  const out = await customers.changePhone(req.customer, req.session.sessionId, phone);
  await log(req, 'store_customer.phone_change', req.customer.id, {
    conversations: out.conversations, sessionsRevoked: out.sessionsRevoked,
  });
  res.json({ customer: out.customer, sessionsRevoked: out.sessionsRevoked });
});

router.get('/account/orders', readLimit, ...acct, customer, async (req, res) => {
  res.json({ orders: await market.ordersOfPhone(req.customer.phone) });
});

router.get('/account/addresses', readLimit, ...acct, customer, async (req, res) => {
  res.json({ addresses: await customers.addresses(req.customer) });
});

router.post('/account/addresses', writeLimit, ...acct, customer, async (req, res) => {
  res.status(201).json({ address: await customers.addAddress(req.customer, req.body) });
});

router.put('/account/addresses/:id', writeLimit, ...acct, customer, async (req, res) => {
  res.json({ address: await customers.updateAddress(req.customer, req.params.id, req.body) });
});

router.delete('/account/addresses/:id', writeLimit, ...acct, customer, async (req, res) => {
  res.json(await customers.removeAddress(req.customer, req.params.id));
});

module.exports = router;
