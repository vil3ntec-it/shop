'use strict';
/**
 * وب‌هوکِ ارائه‌دهندهٔ پرداخت — `/api/v1/payments/webhook/<نام>` (PAY-21، S35).
 *
 *   POST /webhook/hesabpay/:token  حساب‌پیِ **یک دکان** — همین نشانی (از «حساب‌پیِ دکان») در داشبوردِ حساب‌پیِ همان
 *                                  دکان ثبت می‌شود و `callback_url` ِ هر قصد هم همین است؛ امضا با رازِ همان دکان
 *   POST /webhook/hesabpay         درِ کهنهٔ بی‌دکان: دکان از قصدِ نام‌برده در بدنه، امضا باز هم با رازِ همان دکان
 *   POST /webhook/fake             ارائه‌دهندهٔ ساختگی؛ فقط بیرون از تولید (وگرنه ۴۰۱)
 *
 * ⛔ بی توکنِ کاربر: اعتبار فقط امضای بدنهٔ **خام** است (`app.js` بدنه را پیش از پارسرِ JSON
 *    خام نگه می‌دارد)؛ امضای بد ⇒ ۴۰۱ و هیچ تغییری. منطق و پاسخ‌ها: `lib/payment-intents.handleWebhook`.
 * ⛔ عمداً بیرون از `/shop`: درهای `/shop` همه درِ کاربرِ دکان‌اند (courier_scope، SUB-10).
 * پیشوندِ تازهٔ `apiRouter` است ⇒ `ACCOUNT_PREFIXES` ِ پنلِ خانگی باید `/payments` را هم بگیرد
 * (ریپوی دیگر؛ کارِ صاحب محصول — `docs/PAYMENTS-HESABPAY-fa.md`).
 */
const express = require('express');
const pay = require('../lib/payment-intents');
const { rateLimit } = require('../middleware/ratelimit');
const { rawBody } = require('../lib/raw-body');   //  نگهبانِ نوعِ بدنهٔ خام (CodeQL: type confusion)

const router = express.Router();
//  سقفِ نرخِ هر IP پیش از هر کار (بی توکنِ کاربر)؛ گشاده تا تکرارِ مشروعِ ارائه‌دهنده رد نشود
router.use(rateLimit({ max: Number(process.env.RATE_PAY_WEBHOOK_MAX) || 600, windowMs: 60 * 1000, keyPrefix: 'pay-webhook-ip' }));

const webhook = (name) => (req, res, next) => {
  const raw = rawBody(req);
  const opts = req.params.token === undefined ? {} : { token: String(req.params.token) };
  pay.handleWebhook(name, raw, req.headers, opts).then((out) => res.status(out.status).json(out.body)).catch(next);
};
router.post('/webhook/hesabpay/:token', webhook('hesabpay'));
router.post('/webhook/hesabpay', webhook('hesabpay'));
router.post('/webhook/fake', webhook('fake'));

module.exports = router;
