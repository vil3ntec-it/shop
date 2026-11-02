'use strict';
/**
 * «حسابِ من» ِ ویترین — `/api/store/:slug/account/history…` (CRM-13؛ `lib/store-account-history.js`).
 *
 * ⛔ ویترینِ نبوده یا بسته (اشتراکِ منقضی/معلق/لغو) ⇒ همان ۴۰۴ `store_not_found` ِ درهای دیگرِ ویترین (SUB-10)،
 *    پیش از پرچم و هویت؛ پرچمِ خاموشِ `shop.storefront.account_history` ⇒ `next('route')`: این در نیست.
 * ⛔ فقط با شمارهٔ تأییدشده (`X-Store-Ticket` یا `X-Store-Session`)؛ نبود ⇒ ۴۰۱ `verification_required`.
 * ⛔ سقف: سطلِ IP ِ خودش پیش از هر کار (`router.use`)، سپس همان سطلِ خواندنِ ویترین.
 *
 *   GET /:slug/account/history          `{linked, balanceMinor, online, purchases, ledger}` (صفحهٔ نخستِ هر بخش)
 *   GET /:slug/account/history/:part    part = online | purchases | ledger، با `?cursor=&limit=`
 */
const express = require('express');
const config = require('../config');
const sf = require('../lib/storefront');
const H = require('../lib/store-account-history');
const views = require('../lib/public-views');
const { rateLimit } = require('../middleware/ratelimit');
const { notFound, ApiError } = require('../middleware/errors');

const router = express.Router();

//  مسیرها با رشتهٔ صریح (نه ثابت) تا فهرستِ درها (`test/route-inventory.js`) آن‌ها را ببیند
router.use('/:slug/account/history', rateLimit({ max: config.rateLimit.generalMax, keyPrefix: 'store-history-ip' }));
const readLimit = rateLimit({ max: config.rateLimit.generalMax, keyPrefix: 'store-history-read' });

const gate = async (req, res, next) => {
  const s = await sf.open(req.params.slug);
  if (!(await H.flagOn(s.shop_id))) return next('route');
  req.store = s;
  res.set('Cache-Control', 'no-store');
  next();
};

const verified = async (req, res, next) => {
  const phone = await sf.verifiedPhone(req.get('x-store-ticket') || '', req.get('x-store-session') || '');
  if (!phone) throw new ApiError(401, 'verification_required', 'برای دیدنِ حساب، شمارهٔ خود را تأیید کنید');
  req.customerPhone = phone;
  next();
};

router.get('/:slug/account/history', gate, readLimit, views.gate, verified, async (req, res) => {
  res.json(await H.summary(req.store, req.customerPhone));
});

router.get('/:slug/account/history/:part', gate, readLimit, views.gate, verified, async (req, res) => {
  if (!H.PARTS.includes(req.params.part)) throw notFound('این بخش نیست', 'not_found');
  res.json(await H.part(req.store, req.customerPhone, req.params.part, { cursor: req.query.cursor, limit: req.query.limit }));
});

module.exports = router;
