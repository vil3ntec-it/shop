'use strict';
/**
 * دسترسیِ حساسِ مدیر — `docs/spec/ADMIN-CONTROL-PLANE.md`
 * (سندِ v3: ۳۹.۸، ۲۹.۴ بندِ ۴، ۱۹.۱۰ بندِ ۳).
 *
 * «خروجی»ِ دادهٔ خصوصیِ یک حساب و «بازگرداندن» (restore) بی‌دلیلِ نوشته
 * انجام نمی‌شوند؛ دلیل در ردیفِ سابقه می‌نشیند تا بعداً بشود پرسید «چرا».
 *
 * ⚠️ دلیل از بدنه یا querystring (`reason`) می‌آید، نه از سرآیند: مرورگر
 *    نویسهٔ فارسی را در سرآیند نمی‌فرستد (fetch خطا می‌دهد).
 */
const v = require('./validate');
const { badRequest } = require('../middleware/errors');

const REASON_MIN = 3;
const REASON_MAX = 200;

/** دلیلِ نوشته‌شده، یا رشتهٔ خالی. */
function reasonOf(req) {
  return v.text(req.body?.reason ?? req.query?.reason, { max: REASON_MAX }).trim();
}

/** دلیل لازم است؛ نبودش ⇒ ۴۰۰ِ `reason_required`. */
function requireReason(req) {
  const reason = reasonOf(req);
  if (reason.length < REASON_MIN) {
    throw badRequest('دلیلِ این دسترسی را بنویسید (دست‌کم ۳ نویسه)', 'reason_required');
  }
  return reason;
}

module.exports = { reasonOf, requireReason, REASON_MIN, REASON_MAX };
