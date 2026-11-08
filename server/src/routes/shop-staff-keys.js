'use strict';
/**
 * دستگاه‌های شناختهٔ کارمندان (رازِ دستگاهِ ورود با کد) — `/api/shop/staff-keys` (منطق در `lib/staff-device-keys.js`).
 *
 *   GET  /                          رازهای زندهٔ اعضا (برچسب، شناسهٔ کوتاهِ دستگاه، آخرین استفاده، ساخت) و نشانهٔ
 *                                   «شاید همان <نام> است با دستگاهِ تازه» (صاحب و مدیر: members.view)
 *   POST /:id/revoke                «باطل کردنِ این دستگاه»: همان راز + نشست‌های همان دستگاه (members.manage)
 *   POST /members/:id/logout-all    «خروج از همهٔ دستگاه‌ها» برای یک عضو: نشست‌های بخشِ دکان + همهٔ رازهایش (members.manage)
 *
 * ⛔ shop_id فقط از عضویتِ توکن (رازِ دکانِ دیگر ⇒ ۴۰۴)؛ سقف: سطلِ IP پیش از احراز، سپس سطلِ هر کاربر.
 * ⛔ هیچ پاسخی راز، HMAC یا شناسهٔ کاملِ دستگاه را نمی‌دهد. باطل کردن دوباره‌زدنی است (بار دوم `revoked: 0`).
 * ⛔ کلیدِ اشتراک ندارد (SUB-10: FREE): بستنِ دسترسیِ کارمند بخشی از مجوزهای هستهٔ دکان است و پس از پایانِ اشتراک
 *    هم باید شدنی بماند. پرچم ندارد: فقط دسترسی را کم می‌کند.
 * ⛔ نشانهٔ «عضوِ کهنه» هرگز حساب‌ها را یکی نمی‌کند (ربودنِ حساب)؛ برداشتنِ عضوِ کهنه همان `DELETE /api/shop/members/:id`
 *    است که فروش و سابقه را نگه می‌دارد.
 */
const express = require('express');
const config = require('../config');
const v = require('../lib/validate');
const audit = require('../lib/audit');
const deviceKeys = require('../lib/staff-device-keys');
const { can } = require('../lib/permissions');
const { requireUser, requireShop, requirePermission } = require('../middleware/auth');
const { rateLimit, clientIp } = require('../middleware/ratelimit');

const router = express.Router();
router.use(rateLimit({ max: config.rateLimit.generalMax, keyPrefix: 'staff-keys-ip' }));
const limit = rateLimit({ max: 60, windowMs: 60 * 1000, keyPrefix: 'staff-keys', key: (req) => req.user && req.user.id });
router.use(requireUser, requireShop, limit, requirePermission('members.view'));
const manage = requirePermission('members.manage');

/** همان سنجشِ `requirePermission('members.manage')` برای دیدنِ دکمه — سرور باز هم خودش می‌سنجد */
async function canManage(req) {
  if (!can(req.member.role, 'members.manage')) return false;
  const perms = require('../lib/staff-perms');
  return !perms.isLayered('members.manage') || perms.memberAllows(req, 'members.manage');
}

router.get('/', async (req, res) => {
  res.json({ ...(await deviceKeys.overview(req.shopId)), canRevoke: Boolean(await canManage(req)) });
});

router.post('/:id/revoke', manage, async (req, res) => {
  const id = v.id(req.params.id, { field: 'شناسهٔ دستگاه' });
  const out = await deviceKeys.revokeDevice({ shopId: req.shopId, keyId: id, by: req.user.id, ip: clientIp(req) });
  await audit.log({
    shopId: req.shopId, userId: req.user.id, action: 'member.device_key_revoked', targetType: 'user', targetId: out.userId,
    detail: { key: id, revoked: out.revoked, sessions: out.sessions }, ip: clientIp(req),
  });
  res.json({ ok: true, revoked: out.revoked, sessions: out.sessions });
});

router.post('/members/:id/logout-all', manage, async (req, res) => {
  const id = v.id(req.params.id, { field: 'شناسه عضو' });
  const out = await deviceKeys.logoutMember({ shopId: req.shopId, memberId: id, by: req.user.id, ip: clientIp(req) });
  await audit.log({
    shopId: req.shopId, userId: req.user.id, action: 'member.logout_all', targetType: 'member', targetId: out.memberId,
    detail: { revoked: out.revoked, sessions: out.sessions }, ip: clientIp(req),
  });
  res.json({ ok: true, revoked: out.revoked, sessions: out.sessions });
});

module.exports = router;
