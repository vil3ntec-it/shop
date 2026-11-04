'use strict';
/**
 * «حذفِ حساب» به دستِ خودِ کاربر — یک روتر، دو در (همان قاعدهٔ «دو در، یک منطق» ِ `routes/portal.js`):
 *   • `/api/me/account/deletion`      نشستِ برنامهٔ دکان (`requireUser` ِ `routes/me.js`)
 *   • `/api/portal/account/deletion`  پورتالِ وب (`requireAnyUser`) — نشانیِ وبی که Google Play می‌خواهد
 *
 *   GET  ⇒ پیش‌نمایشِ بی نوشتن: `enabled`، `blocked`، `confirmWith`، `confirmValue`، دکان‌هایی که می‌روند
 *   POST {confirm} ⇒ حذف؛ پیش از آن تأییدِ دوبارهٔ هویت (۴۰۳ `reauth_required`؛ برنامه خودش پنجرهٔ رمز را باز
 *        می‌کند — `ReauthGate`) و همهٔ سدهای `lib/self-delete.js`.
 * ⛔ احراز را درِ بالایی می‌کند؛ این روتر جز زیرِ `requireUser`/`requireAnyUser` سوار نمی‌شود.
 */
const express = require('express');
const v = require('../lib/validate');
const audit = require('../lib/audit');
const reauth = require('../lib/reauth');
const selfDelete = require('../lib/self-delete');
const { rateLimit, clientIp } = require('../middleware/ratelimit');
const { badRequest, forbidden, notFound, conflict, unauthorized } = require('../middleware/errors');

const router = express.Router();
//  حذف کارِ کمیاب و سنگین است (یک تراکنشِ بزرگ) ⇒ سقفِ تنگ برای هر IP؛ پیش‌نمایش سبک‌تر
const viewLimit = rateLimit({ max: 60, keyPrefix: 'self-delete-view' });
const runLimit = rateLimit({ max: 10, keyPrefix: 'self-delete' });

const MESSAGES = {
  self_delete_disabled: 'حذفِ حساب از برنامه هنوز روشن نشده است؛ از «پشتیبانی» بخواهید تا حسابتان را پاک کنیم.',
  has_pump: 'این حساب پمپ هم دارد؛ حذفِ آن از «پشتیبانی» انجام می‌شود تا دادهٔ پمپ بی‌خبر پاک نشود.',
};

router.get('/', viewLimit, async (req, res, next) => {
  if (!req.user) return next(unauthorized());
  const out = await selfDelete.view(req.user);
  if (!out) return next(notFound('حساب پیدا نشد'));
  res.json(out);
});

router.post('/', runLimit, async (req, res, next) => {
  if (!req.user) return next(unauthorized());
  //  خودِ حساب را می‌گیرد ⇒ تازگی در هر بخش، مثلِ «گذاشتنِ نخستین رمز»
  await reauth.assertFresh(req, { allSections: true });
  const confirm = v.text(req.body?.confirm, { max: 200 });
  const out = await selfDelete.run(req.user.id, confirm);
  if (!out.ok) {
    if (out.code === 'self_delete_disabled') return next(forbidden(MESSAGES.self_delete_disabled, out.code));
    if (out.code === 'has_pump') return next(conflict(MESSAGES.has_pump, out.code));
    if (out.code === 'not_found') return next(notFound('حساب پیدا نشد'));
    const what = { email: 'ایمیلِ', phone: 'شمارهٔ', id: 'شناسهٔ' }[out.confirmWith] || 'ایمیلِ';
    return next(badRequest(`برای حذف، ${what} همین حساب را دقیق بنویسید`, 'confirm_mismatch'));
  }
  //  بی ایمیل و شماره در سابقه: حساب رفته است و سابقه می‌ماند
  await audit.log({
    actorType: 'user', userId: req.user.id, action: 'user.self_deleted', targetType: 'user', targetId: req.user.id,
    detail: { shops: out.shops, via: req.baseUrl.includes('/portal') ? 'portal' : 'app' }, ip: clientIp(req),
  });
  res.json({ ok: true, shops: out.shops });
});

module.exports = router;
