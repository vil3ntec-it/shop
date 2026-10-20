'use strict';
/**
 * کارمندان، مجوزها و دستگاه‌ها — STF-01..03 و STF-10 (بخشِ ۱۰.۱، سناریوهای S44 و S45).
 *
 * زیرِ `routes/shop.js` سوار است (همان `requireUser`). shop_id همیشه از
 * عضویتِ توکن می‌آید، نه از بدنه (بخشِ ۲۹.۲ بندِ ۲).
 *
 *   GET  /api/shop/permissions                 مجوزهای خودم؛ مدیر/صاحب: همهٔ لایه‌ها
 *   PUT  /api/shop/permissions/roles/:role     لایهٔ نقش (فقط صاحب: members.role)
 *   PUT  /api/shop/permissions/members/:id     لایهٔ یک کاربر (فقط صاحب)
 *   GET  /api/shop/devices                     دستگاه‌های اعضا (members.view)
 *   POST /api/shop/devices/:id/disable         از کار انداختنِ دستگاهِ گم‌شده (فقط صاحب)
 *   POST /api/shop/devices/:id/enable          برگرداندنِ دستگاهِ پیداشده (فقط صاحب)
 *
 *   «برگهٔ امضاشده» (STF-02، بخشِ ۱۰.۱ — `lib/staff-approval.js`):
 *   POST   /api/shop/approver-keys             ثبتِ کلیدِ عمومیِ همین دستگاه (صاحب/مدیر)
 *   GET    /api/shop/approver-keys             کلیدهای خودم؛ صاحب: همه
 *   DELETE /api/shop/approver-keys/:id         باطل کردن (خودم یا صاحب)
 *   GET    /api/shop/approvals                 سابقهٔ تأییدهای پذیرفته (audit.view)
 */
const express = require('express');
const v = require('../lib/validate');
const audit = require('../lib/audit');
const perms = require('../lib/staff-perms');
const deviceBlock = require('../lib/device-block');
const approvals = require('../lib/staff-approval');
const { can } = require('../lib/permissions');
const { requireShop, requirePermission } = require('../middleware/auth');
const { clientIp } = require('../middleware/ratelimit');
const { requireRecentAuth } = require('../lib/reauth');

const router = express.Router();

router.get('/permissions', requireShop, async (req, res) => {
  const { permissions, policy } = await perms.policyOf(req.shopId, req.member);
  const out = { role: req.member.role, permissions, policy };
  if (can(req.member.role, 'members.view')) out.shop = await perms.overview(req.shopId);
  res.json(out);
});

//  ⛔ تغییرِ مجوزها فقط از نشستِ تازه احراز‌شده (بندِ ۲۱.۱، `lib/reauth`)
router.put('/permissions/roles/:role', requireShop, requirePermission('members.role'), requireRecentAuth, async (req, res) => {
  const role = v.text(req.params.role, { max: 20, required: true, field: 'نقش' });
  const layer = await perms.setRoleLayer(req.shopId, role, req.body?.perms, req.user.id);
  await audit.log({
    shopId: req.shopId, userId: req.user.id, action: 'perms.role_updated',
    targetType: 'role', targetId: role, detail: { perms: layer }, ip: clientIp(req),
  });
  res.json({ role, perms: layer, shop: await perms.overview(req.shopId) });
});

router.put('/permissions/members/:id', requireShop, requirePermission('members.role'), requireRecentAuth, async (req, res) => {
  const id = v.id(req.params.id, { field: 'شناسه عضو' });
  const { member, layer } = await perms.setMemberLayer(req.shopId, id, req.body?.perms, req.user.id);
  await audit.log({
    shopId: req.shopId, userId: req.user.id, action: 'perms.member_updated',
    targetType: 'member', targetId: member.user_id, detail: { perms: layer }, ip: clientIp(req),
  });
  res.json({ userId: member.user_id, perms: layer, shop: await perms.overview(req.shopId) });
});

router.get('/devices', requireShop, requirePermission('members.view'), async (req, res) => {
  const devices = await deviceBlock.listForShop(req.shopId);
  const current = req.device?.id || null;
  res.json({ devices: devices.map((d) => ({ ...d, current: d.id === current })) });
});

router.post('/devices/:id/disable', requireShop, requirePermission('members.manage'), async (req, res) => {
  const id = v.id(req.params.id, { field: 'شناسه دستگاه' });
  const reason = v.text(req.body?.reason, { max: 200 });
  const out = await deviceBlock.disable(req.shopId, id, {
    byId: req.user.id, currentDeviceId: req.device?.id || null, reason, ip: clientIp(req),
  });
  res.json({ ok: true, ...out });
});

router.post('/devices/:id/enable', requireShop, requirePermission('members.manage'), async (req, res) => {
  const id = v.id(req.params.id, { field: 'شناسه دستگاه' });
  const out = await deviceBlock.enable(req.shopId, id, { byId: req.user.id, ip: clientIp(req) });
  res.json({ ok: true, ...out });
});

router.post('/approver-keys', requireShop, async (req, res) => {
  const b = req.body || {};
  const key = await approvals.registerKey(req.shopId, req.member, {
    publicKey: b.public_key ?? b.publicKey,
    label: v.text(b.label, { max: 60 }) || '',
    deviceId: v.text(b.device_id ?? b.deviceId, { max: 64 }) || '',
  });
  await audit.log({
    shopId: req.shopId, userId: req.user.id, action: 'approver_key.registered',
    targetType: 'approver_key', targetId: key.id, detail: { label: key.label }, ip: clientIp(req),
  });
  res.status(201).json({ key });
});

router.get('/approver-keys', requireShop, async (req, res) => {
  res.json({ keys: await approvals.listKeys(req.shopId, req.member) });
});

router.delete('/approver-keys/:id', requireShop, async (req, res) => {
  const id = v.text(req.params.id, { max: 80, required: true, field: 'شناسه کلید' });
  const key = await approvals.revokeKey(req.shopId, req.member, id);
  await audit.log({
    shopId: req.shopId, userId: req.user.id, action: 'approver_key.revoked',
    targetType: 'approver_key', targetId: key.id, detail: { owner: key.userId }, ip: clientIp(req),
  });
  res.json({ key });
});

router.get('/approvals', requireShop, requirePermission('audit.view'), async (req, res) => {
  res.json({ approvals: await approvals.listApprovals(req.shopId, { limit: Number(req.query?.limit || 100) }) });
});

module.exports = router;
