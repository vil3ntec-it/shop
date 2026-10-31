'use strict';
/**
 * پنلِ مدیر، «پرداخت‌های درگاهی» (حساب‌پی) — `/api/admin/payments` (PAY-21، S35).
 *
 *   GET  /intents?status=&review=1&shopId=&limit=&before=   فهرست (هر مدیر؛ بی نشانیِ پرداخت)
 *   POST /reconcile                                          تطبیقِ ۴۸ ساعتِ اخیر با حساب‌پی (مدیرِ ارشد)
 *   POST /intents/:id/refund   { note }                      «پول در حساب‌پی برگشت» ⇒ refunded (مدیرِ ارشد)
 *
 * زمان‌بندِ خودکارِ تطبیق همان `housekeeping` ِ `app.js` است (هر ۶ ساعت، ۴۸ ساعتِ اخیر).
 */
const express = require('express');
const pay = require('../lib/payment-intents');
const audit = require('../lib/audit');
const { requireAdmin, requireSuperAdmin } = require('../middleware/auth');
const { clientIp } = require('../middleware/ratelimit');

const router = express.Router();
router.use(requireAdmin);
const wrap = (fn) => (req, res, next) => Promise.resolve(fn(req, res, next)).catch(next);

router.get('/intents', wrap(async (req, res) => {
  res.json(await pay.list({
    status: String(req.query.status || ''), review: req.query.review === '1' || req.query.review === 'true',
    shopId: String(req.query.shopId || ''), limit: req.query.limit, before: String(req.query.before || ''),
  }));
}));

router.post('/reconcile', requireSuperAdmin, wrap(async (req, res) => {
  const out = await pay.reconcile();
  await audit.log({ actorType: 'admin', userId: req.admin.id, action: 'admin.payments_reconcile', detail: out, ip: clientIp(req) });
  res.json(out);
}));

router.post('/intents/:id/refund', requireSuperAdmin, wrap(async (req, res) => {
  res.json(await pay.markRefunded(req.params.id, { adminId: req.admin.id, note: req.body?.note }));
}));

module.exports = router;
