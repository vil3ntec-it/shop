'use strict';
/**
 * شکایت‌ها و جریمه‌های بازارچه، سمتِ دکان — `/api/shop/trust` (v3 §۳۸.۶).
 *
 * ⛔ پیش‌فرض خاموش: بی پرچمِ `market.trust` فقط `GET /config` جواب می‌دهد
 * (`enabled: false`، تا برنامه بخش را نشان ندهد)؛ بقیه ۴۰۴ و بی هیچ ردیف.
 * ⛔ shop_id فقط از عضویتِ کاربرِ توکن؛ پروندهٔ دکانِ دیگر همان ۴۰۴ ِ «نیست».
 * ⛔ دسترسی‌ها سمتِ سرور: `trust.view` (دیدن) و `trust.respond` (پاسخ، اعتراض) —
 * هر دو صاحب و مدیر. هویتِ شاکی در هیچ پاسخی نیست.
 */
const express = require('express');
const config = require('../config');
const trust = require('../lib/market-trust');
const audit = require('../lib/audit');
const { can } = require('../lib/permissions');
const { requireUser, requireShop, requirePermission } = require('../middleware/auth');
const { rateLimit } = require('../middleware/ratelimit');
const { notFound } = require('../middleware/errors');

const router = express.Router();
router.use(requireUser, requireShop, requirePermission('trust.view'));

const wrap = (fn) => (req, res, next) => Promise.resolve(fn(req, res, next)).catch(next);
const viewerOf = (req) => ({ shopId: req.shopId, userId: req.user.id, role: req.role });
const respondPerm = requirePermission('trust.respond');
const writeLimit = rateLimit({ max: config.trust.fileIpMax, keyPrefix: 'shop-trust-write', key: (req) => req.user && req.user.id });

const on = wrap(async (req, res, next) => {
  if (!(await trust.flagOf(req.shopId))) throw notFound('شکایت و تخلف برای این دکان روشن نیست', 'trust_disabled');
  res.set('Cache-Control', 'no-store');
  next();
});

router.get('/config', wrap(async (req, res) => {
  res.json({
    enabled: await trust.flagOf(req.shopId),
    appealDays: config.trust.appealDays,
    permissions: { respond: can(req.role, 'trust.respond') },
  });
}));

router.get('/cases', on, wrap(async (req, res) => {
  res.json(await trust.listForShop(req.shopId, { status: req.query.status, limit: req.query.limit, before: req.query.before }));
}));

router.get('/cases/:id', on, wrap(async (req, res) => {
  res.json({ case: await trust.viewForShop(req.shopId, req.params.id) });
}));

router.post('/cases/:id/responses', on, respondPerm, writeLimit, wrap(async (req, res) => {
  const out = await trust.respond(viewerOf(req), req.params.id, req.body);
  if (!out.replayed) {
    await audit.log({ shopId: req.shopId, userId: req.user.id, action: 'trust.case_responded', targetType: 'trust_case', targetId: out.case.id });
  }
  res.status(out.replayed ? 200 : 201).json(out);
}));

router.post('/cases/:id/appeal', on, respondPerm, writeLimit, wrap(async (req, res) => {
  const out = await trust.appeal(viewerOf(req), req.params.id, req.body);
  if (!out.replayed) {
    await audit.log({ shopId: req.shopId, userId: req.user.id, action: 'trust.case_appealed', targetType: 'trust_case', targetId: out.case.id });
  }
  res.status(out.replayed ? 200 : 201).json(out);
}));

router.get('/sanctions', on, wrap(async (req, res) => {
  res.json({ sanctions: await trust.sanctionsForShop(req.shopId) });
}));

module.exports = router;
