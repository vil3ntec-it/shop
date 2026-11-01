'use strict';
/**
 * پنلِ مرکزی، فاز ۳ — کلیدهای سرویسِ پلِ پنل (یافتهٔ S4: «یک هویتِ مشترکِ superadmin برای پل»).
 *
 *   GET  /api/admin/service-keys                  فهرست (بی خودِ کلید؛ فقط هشت نویسهٔ آغاز)
 *   POST /api/admin/service-keys                  {name, roleCap, reason} ⇒ کلید **فقط همین یک بار**
 *   POST /api/admin/service-keys/:keyId/revoke    {reason} — همان دم بی‌اثر
 *
 * ⛔ فقط مدیرِ ارشدِ انسانی (کلیدِ سرویس به این درها نمی‌رسد: `lib/admin-roles.SERVICE_DENY`).
 * ⛔ کلیدِ ساختهٔ مالکِ اصلی را فقط خودِ مالک باطل می‌کند (همان قاعدهٔ «مالک بیرون نمی‌ماند»: ارشدِ دوم
 *    پلِ مالک را خاموش نمی‌کند). سازندهٔ هر کلید هم کلیدِ خودش را باطل می‌کند.
 * ⛔ کلید هرگز در لاگ، سابقه یا پاسخِ دیگری نمی‌آید؛ اگر گم شد، کلیدِ تازه بسازید و کهنه را باطل کنید.
 */
const express = require('express');
const svc = require('../lib/admin-service');
const audit = require('../lib/audit');
const roles = require('../lib/admin-roles');
const v = require('../lib/validate');
const { requireSuperAdmin } = require('../middleware/auth');
const { requireReason } = require('../lib/admin-access');
const { clientIp } = require('../middleware/ratelimit');
const { notFound, forbidden, conflict } = require('../middleware/errors');

const router = express.Router();

router.get('/', requireSuperAdmin, async (req, res) => {
  res.json({ keys: await svc.list() });
});

router.post('/', requireSuperAdmin, async (req, res) => {
  const reason = requireReason(req);
  const name = v.text(req.body?.name, { max: 60, required: true, field: 'نام' });
  const roleCap = v.oneOf(req.body?.roleCap, roles.ROLES, { field: 'سقفِ نقش', def: 'admin' });
  const { key, row } = await svc.create({ name, roleCap, createdBy: req.admin.id });
  await audit.log({
    actorType: 'admin', userId: req.admin.id, action: 'admin.service_key_created', ip: clientIp(req),
    targetType: 'service_key', targetId: row.id, detail: { name, roleCap, reason },
  });
  res.set('Cache-Control', 'no-store');
  res.status(201).json({ key, serviceKey: row, shownOnce: true });
});

router.post('/:keyId/revoke', requireSuperAdmin, async (req, res, next) => {
  const reason = requireReason(req);
  const id = v.id(req.params.keyId);
  const cur = await svc.get(id);
  if (!cur) return next(notFound('کلید پیدا نشد'));
  if (cur.status !== 'active') return next(conflict('این کلید پیش‌تر باطل شده است', 'service_key_revoked'));
  const owner = await require('../lib/admin-owner').ownerId();
  if (cur.createdBy === owner && req.admin.id !== owner) {
    return next(forbidden('کلیدی که مالکِ اصلی ساخته را فقط خودش باطل می‌کند', 'owner_protected'));
  }
  const row = await svc.revoke(id, req.admin.id);
  if (!row) return next(conflict('این کلید پیش‌تر باطل شده است', 'service_key_revoked'));
  await audit.log({
    actorType: 'admin', userId: req.admin.id, action: 'admin.service_key_revoked', ip: clientIp(req),
    targetType: 'service_key', targetId: id, detail: { name: row.name, reason },
  });
  res.json({ serviceKey: row });
});

module.exports = router;
