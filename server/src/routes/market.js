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
 */
const express = require('express');
const config = require('../config');
const market = require('../lib/market');
const sf = require('../lib/storefront');
const { rateLimit, clientIp } = require('../middleware/ratelimit');

const router = express.Router();
const wrap = (fn) => (req, res, next) => Promise.resolve(fn(req, res, next)).catch(next);

const readLimit = rateLimit({ max: config.rateLimit.generalMax, keyPrefix: 'market-read' });
const searchLimit = rateLimit({ max: config.rateLimit.marketSearchMax, keyPrefix: 'market-search' });
//  سقفِ ضربه‌ای: چند جست‌وجو در دقیقه از یک IP، جدا از سقفِ ربع‌ساعته
const searchBurst = rateLimit({ max: config.rateLimit.marketSearchBurstMax, windowMs: 60 * 1000, keyPrefix: 'market-search-burst' });
//  همان کلیدهای `routes/storefront.js` ⇒ همان سطل
const sendCodeLimit = rateLimit({ max: config.rateLimit.storeCheckoutMax, keyPrefix: 'store-verify' });
const codeLimit = rateLimit({ max: config.rateLimit.verifyMax, keyPrefix: 'store-verify-code' });

const on = wrap(async (req, res, next) => {
  await market.requireOn();
  res.set('Cache-Control', 'no-store');
  next();
});

router.get('/', readLimit, on, wrap(async (req, res) => {
  res.json(await market.overview());
}));

router.get('/shops', readLimit, on, wrap(async (req, res) => {
  const { q, category, lat, lng, limit, offset } = req.query;
  res.json(await market.shops({ q, category, lat, lng, limit, offset }));
}));

router.get('/search', searchBurst, searchLimit, on, wrap(async (req, res) => {
  const { q, lat, lng, limit } = req.query;
  res.json(await market.search({ q, lat, lng, limit }));
}));

//  ── ۲۳: تأییدِ شماره بی دکان (برای «سفارش‌های من») — همان بلیتِ ویترین ──
router.post('/verify/start', sendCodeLimit, on, wrap(async (req, res) => {
  res.json(await sf.startVerification(req.body, { ip: clientIp(req) }));
}));

router.post('/verify/check', codeLimit, on, wrap(async (req, res) => {
  res.json(await sf.checkVerification(req.body));
}));

router.get('/orders', readLimit, on, wrap(async (req, res) => {
  res.json({ orders: await market.myOrders(req.get('x-store-ticket') || '') });
}));

module.exports = router;
