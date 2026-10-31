'use strict';
/**
 * پرداختِ حساب‌پی برای برنامهٔ دکان — `/api/v1/shop/payments` (همان `/api/shop/payments`)؛
 * PAY-21، PAY-08، S35. قرارداد و شرح: `docs/PAYMENTS-HESABPAY-fa.md`.
 *
 *   GET  /config               { hesabpay: { enabled } } — همیشه جواب می‌دهد
 *   POST /intents              ساختنِ قصدِ پرداخت (پرچمِ `pay.hesabpay` + کلیدهای HESABPAY_*)
 *   GET  /intents/:id[?wait=25] وضعیت؛ با wait تا ۲۵ ثانیه منتظرِ تغییر می‌ماند
 *   POST /intents/:id/cancel   فقط از created/pending
 *
 * ⛔ shop_id فقط از نشستِ کاربر (`requireShop`)، هرگز از بدنه.
 * ⛔ کارمندی که فروش ثبت می‌کند (`data.write`: صاحب، مدیر، شاگرد).
 * ⛔ SUB-10: دستهٔ FREE (کلیدِ اشتراکی ندارد؛ فقط پرچم). پرداختِ سفارشِ آنلاین اشتراکِ فعال
 *    می‌خواهد و ردش `online_payment_unavailable` است (`lib/payment-intents.js`).
 * وب‌هوکِ بی‌توکنِ ارائه‌دهنده جای دیگری است: `routes/payments-webhook.js` (`/api/v1/payments`).
 */
const express = require('express');
const pay = require('../lib/payment-intents');
const { requireUser, requireShop, requirePermission } = require('../middleware/auth');
const { rateLimit, clientIp } = require('../middleware/ratelimit');
const { forbidden } = require('../middleware/errors');

const router = express.Router();
const wrap = (fn) => (req, res, next) => Promise.resolve(fn(req, res, next)).catch(next);

router.use(requireUser, requireShop, requirePermission('data.write'));

const perUser = (req) => `${req.shopId}:${req.user.id}`;
const createLimit = rateLimit({
  max: Number(process.env.RATE_PAY_CREATE_MAX) || 30, windowMs: 60 * 1000, keyPrefix: 'pay-create', key: perUser,
});
const pollLimit = rateLimit({
  max: Number(process.env.RATE_PAY_POLL_MAX) || 240, windowMs: 60 * 1000, keyPrefix: 'pay-poll', key: perUser,
});

const needEnabled = wrap(async (req, res, next) => {
  if (!(await pay.enabledFor(req.shopId))) {
    return next(forbidden('پرداخت با حساب‌پی برای این دکان روشن نیست', 'hesabpay_disabled'));
  }
  next();
});

router.get('/config', wrap(async (req, res) => {
  res.json(await pay.configOf(req.shopId));
}));

router.post('/intents', createLimit, needEnabled, wrap(async (req, res) => {
  let out;
  try {
    out = await pay.create(req.shopId, {
      body: req.body, headerKey: req.get('idempotency-key') || '',
      actor: { id: req.user.id, ip: clientIp(req) },
    });
  } catch (err) {
    //  ۵۰۲ با شناسهٔ قصد (errorHandler دادهٔ خطای ۵xx را نمی‌فرستد)؛ کلیدِ تازه ⇒ تلاشِ تازه
    if (err && err.code === 'provider_unavailable') {
      return res.status(502).json({ error: { code: err.code, message: err.message, ...(err.data || {}) } });
    }
    throw err;
  }
  if (!out.created) res.set('Idempotent-Replayed', 'true');
  res.status(out.created ? 201 : 200).json(out.intent);
}));

router.get('/intents/:id', pollLimit, wrap(async (req, res) => {
  const ctl = new AbortController();
  res.on('close', () => ctl.abort());
  res.json(await pay.get(req.shopId, req.params.id, { wait: req.query.wait, signal: ctl.signal }));
}));

router.post('/intents/:id/cancel', createLimit, wrap(async (req, res) => {
  res.json(await pay.cancel(req.shopId, req.params.id, { id: req.user.id, ip: clientIp(req) }));
}));

module.exports = router;
