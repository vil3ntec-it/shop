'use strict';
/**
 * نقشِ سفارشی — `/api/shop/custom-roles` (STF-01؛ منطق در `lib/custom-roles.js`).
 *
 *   GET    /config                 { enabled, max, baseRoles }
 *   GET    /                       نقش‌ها با اعضایشان (صاحب و مدیر: members.view)
 *   POST   /                       فقط صاحب { name, baseRole, perms }        ⇒ ۲۰۱
 *   PUT    /:id                    فقط صاحب { name?, perms? }  (نقشِ پایه ثابت)
 *   DELETE /:id                    فقط صاحب — عضوها روی نقشِ پایه می‌مانند
 *   PUT    /members/:id            فقط صاحب { roleId | null } — دادن یا برداشتن (نقشِ عضو ⇐ نقشِ پایه)
 * اجرا همان راه‌های امروز است: `staff-perms.layersFor` لایهٔ نقشِ سفارشی را میانِ نقش و کاربر می‌گذارد.
 *
 * ⛔ پیش‌فرض خاموش: بی `shop.custom_roles` همه جز `/config` ⇒ ۴۰۳ `custom_roles_disabled`.
 * ⛔ shop_id فقط از عضویتِ توکن؛ تغییر فقط از نشستِ تازه احراز‌شده (`requireRecentAuth`، همان درِ مجوزها).
 * ⛔ سقف: سطلِ IP پیش از احراز، سپس سطلِ هر کاربر.
 * ⛔ کلیدِ اشتراک ندارد (SUB-10: FREE): مثلِ لایهٔ نقش و کاربر (`/shop/permissions/*`) بخشی از مجوزهای هستهٔ دکان است.
 */
const express = require('express');
const config = require('../config');
const v = require('../lib/validate');
const audit = require('../lib/audit');
const roles = require('../lib/custom-roles');
const { requireUser, requireShop, requirePermission } = require('../middleware/auth');
const { rateLimit, clientIp } = require('../middleware/ratelimit');
const { forbidden } = require('../middleware/errors');
const { requireRecentAuth } = require('../lib/reauth');

const router = express.Router();
router.use(rateLimit({ max: config.rateLimit.generalMax, keyPrefix: 'custom-roles-ip' }));
const limit = rateLimit({ max: 120, windowMs: 60 * 1000, keyPrefix: 'custom-roles', key: (req) => req.user && req.user.id });
router.use(requireUser, requireShop, limit, requirePermission('members.view'));

//  Express 5 خودش promiseِ ردشده را به next(err) می‌دهد
async function needFlag(req, res, next) {
  if (!(await roles.enabled(req.shopId))) return next(forbidden('نقشِ سفارشی برای این دکان روشن نیست', 'custom_roles_disabled'));
  next();
}
const owner = [needFlag, requirePermission('members.role'), requireRecentAuth];
const log = (req, action, targetId, detail) => audit.log({
  shopId: req.shopId, userId: req.user.id, action, targetType: 'custom_role', targetId, detail, ip: clientIp(req),
});

router.get('/config', async (req, res) => {
  res.json({ enabled: await roles.enabled(req.shopId), max: roles.MAX_ROLES, baseRoles: roles.BASE_ROLES });
});

router.get('/', needFlag, async (req, res) => {
  res.json({ roles: await roles.list(req.shopId) });
});

router.post('/', ...owner, async (req, res) => {
  const role = await roles.create(req.shopId, req.body, req.user.id);
  await log(req, 'custom_role.created', role.id, { name: role.name, baseRole: role.baseRole, perms: role.perms });
  res.status(201).json({ role });
});

router.put('/members/:id', ...owner, async (req, res) => {
  const id = v.id(req.params.id, { field: 'شناسه عضو' });
  const out = await roles.assign(req.shopId, id, req.body?.roleId ?? null, req.user.id);
  await log(req, out.role ? 'custom_role.assigned' : 'custom_role.unassigned', out.role ? out.role.id : '',
    { member: out.member.user_id, baseRole: out.baseRole });
  res.json({ userId: out.member.user_id, role: out.role, baseRole: out.baseRole });
});

router.put('/:id', ...owner, async (req, res) => {
  const role = await roles.update(req.shopId, v.id(req.params.id, { field: 'شناسه نقش' }), req.body, req.user.id);
  await log(req, 'custom_role.updated', role.id, { name: role.name, perms: role.perms });
  res.json({ role });
});

router.delete('/:id', ...owner, async (req, res) => {
  const out = await roles.remove(req.shopId, v.id(req.params.id, { field: 'شناسه نقش' }));
  await log(req, 'custom_role.deleted', out.id, { name: out.name });
  res.json({ ok: true, id: out.id });
});

module.exports = router;
