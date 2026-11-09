'use strict';
/**
 *   POST /api/sync/v1/audit-anchor   سرِ زنجیرهٔ سابقهٔ عملیاتِ همین دستگاه (STF-09، مهاجرتِ ۴۱۰)
 *        { device_id, format: 'tohid-audit-chain-v1', heads: [{chain, seq, hash}] }
 *        ⇐ { ok, enabled, acks, problems, last_epoch }
 *   GET  /api/sync/v1/audit-anchor   سنجشِ سرور برای صفحهٔ «سابقهٔ عملیات» ِ صاحب (فقط `audit.view`)
 *        ⇐ { ok, enabled, valid, broken, chains, pending, legacy }
 *
 * شرح و قاعده‌ها: `lib/device-audit-anchor.js`؛ سنجش: `lib/device-audit-chain.js` (`verify-chain --device-audit`).
 *
 * ⛔ جدا از `routes/sync-v1.js` (مشترک با پمپ) و فقط دکان — همان الگوی درِ کارتِ ویترین (`sync-photo-thumbs.js`):
 *    پیش از `sync-v1` سوار می‌شود ولی فقط همین درها را دارد و همهٔ زنجیره روی خودِ در است، نه `router.use` ⇒ هیچ
 *    درخواستِ دیگرِ `/sync/v1` از این پرونده نمی‌گذرد و سقف یا احرازش دو بار شمرده نمی‌شود. زنجیره: سقفِ آی‌پی پیش از
 *    احراز (سطلِ `syncip`)، `requireSyncAccount` (حساب و دستگاه از توکن)، فقط دکان (پمپ ⇒ ۴۰۳ `shop_only`)، سقفِ
 *    حساب (سطلِ `sync`). دکان همیشه از نشست (`req.sync.accountId`) است، نه از بدنه یا سرآیند.
 * ⛔ `device_id` ِ گزارش همیشه همان دستگاهِ نشست (`anchors.ownDevice`؛ دیگری ⇒ ۴۰۳ `device_mismatch`، نشستِ بی دستگاه ⇒
 *    ۴۰۳ `device_unbound`) — نه فقط با پرچمِ `sync.device_bind` که خاموشش در `requireSyncAccount` فقط ثبت می‌کند.
 * ⛔ GET همان `requirePermission('audit.view')` ِ `/api/shop/audit`: سقفِ نقش و سپس لایه‌های دکان/کاربر (STF-01) —
 *    مدیر یا حسابداری که صاحب «سابقهٔ عملیات» را برایش بسته، حکم و شناسهٔ دستگاه‌ها را هم نمی‌بیند.
 * ⛔ رایگان (SUB-10): بی `requireSyncWrite` — پاسبانیِ یکپارچگی پس از پایانِ اشتراک هم باید کار کند؛ هر عضو فقط سرِ
 *    زنجیرهٔ دستگاهِ خودش را می‌فرستد (`<device_id>/<دوره>`).
 */
const express = require('express');
const config = require('../config');
const { rateLimit, clientIp } = require('../middleware/ratelimit');
const { ApiError } = require('../middleware/errors');
const { requireSyncAccount } = require('../lib/sync-v1-auth');
const { SHOP } = require('../lib/tenancy');
const { can } = require('../lib/permissions');
const permCore = require('../lib/perm-core');
const staffPerms = require('../lib/staff-perms');
const v = require('../lib/validate');
const anchors = require('../lib/device-audit-anchor');

const router = express.Router();

const ipLimit = rateLimit({ max: config.rateLimit.syncIpMax, keyPrefix: 'syncip' });
const accountLimit = rateLimit({
  max: config.rateLimit.syncMax,
  keyPrefix: 'sync',
  key: (req) => (req.sync ? req.sync.app + ':' + req.sync.accountId : ''),
});

/** فقط حسابِ دکان (`app` و `accountKind` هر دو `tenancy.SHOP.app`)؛ پمپ و هر بخشِ دیگر ⇒ ۴۰۳ */
function shopOnly(req, res, next) {
  const s = req.sync;
  if (s && s.app === SHOP.app && s.accountKind === SHOP.app && s.accountId) return next();
  next(new ApiError(403, 'shop_only', 'زنجیرهٔ سابقهٔ عملیات فقط برای دکان است'));
}

/** سنجشِ سرور همان صفحهٔ «سابقهٔ عملیات» است ⇒ همان اجازه، با لایه‌های دکان/کاربر (فقط می‌بندند) */
async function auditView(req, res, next) {
  try {
    const s = req.sync;
    if (!can(s.role, 'audit.view')) return next(new ApiError(403, 'permission_denied', 'دیدنِ سابقهٔ عملیات در حدِ نقشِ شما نیست'));
    const layers = await staffPerms.layersFor(s.accountId, s.role, s.userId);
    if (permCore.allows(s.role, layers, 'audit.view')) return next();
    next(new ApiError(403, 'permission_denied', 'صاحب دکان دیدنِ سابقهٔ عملیات را برای شما بسته است'));
  } catch (err) { next(err); }
}

router.post('/audit-anchor', ipLimit, requireSyncAccount, shopOnly, accountLimit, async (req, res, next) => {
  try {
    const deviceId = v.id(req.body?.device_id, { field: 'شناسه دستگاه', required: true, max: 64 });
    res.set('Cache-Control', 'no-store');
    res.json({ ok: true, ...(await anchors.report(req.sync, deviceId, req.body, { ip: clientIp(req) })) });
  } catch (err) { next(err); }
});

router.get('/audit-anchor', ipLimit, requireSyncAccount, shopOnly, accountLimit, auditView, async (req, res, next) => {
  try {
    res.set('Cache-Control', 'no-store');
    res.json({ ok: true, ...(await anchors.status(req.sync)) });
  } catch (err) { next(err); }
});

module.exports = router;
