'use strict';
/**
 * وب‌هوکِ ارائه‌دهندهٔ پرداخت — `/api/v1/payments/webhook/<نام>` (PAY-21، S35).
 *
 *   POST /webhook/hesabpay   حساب‌پی — همین نشانی در داشبوردِ حساب‌پی ثبت می‌شود
 *   POST /webhook/fake       ارائه‌دهندهٔ ساختگی؛ فقط بیرون از تولید (وگرنه ۴۰۱)
 *
 * ⛔ بی توکنِ کاربر: اعتبار فقط امضای بدنهٔ **خام** است (`app.js` بدنه را پیش از پارسرِ JSON
 *    خام نگه می‌دارد)؛ امضای بد ⇒ ۴۰۱ و هیچ تغییری. منطق و پاسخ‌ها: `lib/payment-intents.handleWebhook`.
 * ⛔ عمداً بیرون از `/shop`: درهای `/shop` همه درِ کاربرِ دکان‌اند (courier_scope، SUB-10).
 * پیشوندِ تازهٔ `apiRouter` است ⇒ `ACCOUNT_PREFIXES` ِ پنلِ خانگی باید `/payments` را هم بگیرد
 * (ریپوی دیگر؛ کارِ صاحب محصول — `docs/PAYMENTS-HESABPAY-fa.md`).
 */
const express = require('express');
const pay = require('../lib/payment-intents');

const router = express.Router();

const webhook = (name) => (req, res, next) => {
  const raw = Buffer.isBuffer(req.body) ? req.body : Buffer.alloc(0);
  pay.handleWebhook(name, raw, req.headers).then((out) => res.status(out.status).json(out.body)).catch(next);
};
router.post('/webhook/hesabpay', webhook('hesabpay'));
router.post('/webhook/fake', webhook('fake'));

module.exports = router;
