'use strict';
/**
 * پنلِ مدیرِ سامانه: مجوزها و دستگاه‌های یک دکان (STF-01..03، STF-10).
 *
 * زیرِ `/api/admin/shops/:id/staff-perms` و پشتِ `requireAdmin`ِ `routes/admin.js`.
 * خواندن برای هر مدیر؛ نوشتن فقط مدیرِ ارشد (همان قاعدهٔ بقیهٔ پنل) و
 * همان سنجشِ هسته که صاحب دکان دارد — پنل هم بیش از نقش نمی‌دهد.
 */
const express = require('express');
const { one } = require('../db');
const v = require('../lib/validate');
const audit = require('../lib/audit');
const perms = require('../lib/staff-perms');
const deviceBlock = require('../lib/device-block');
const { requireSuperAdmin } = require('../middleware/auth');
const { notFound } = require('../middleware/errors');
const { clientIp } = require('../middleware/ratelimit');

const router = express.Router({ mergeParams: true });

async function shopOf(req) {
  const id = v.id(req.params.id);
  const shop = await one('SELECT id, name FROM shops WHERE id=$1', [id]);
  if (!shop) throw notFound('دکان پیدا نشد');
  return shop;
}

router.get('/', async (req, res) => {
  const shop = await shopOf(req);
  //  TEN-04: اجازه‌های اعضا و دستگاه‌هایشان مالِ یک دکان است ⇒ دیدنش سابقه دارد، مثلِ پروندهٔ دکان
  await audit.log({
    shopId: shop.id, actorType: 'admin', userId: req.admin.id, action: 'admin.shop_staff_perms_viewed',
    targetType: 'shop', targetId: shop.id, ip: clientIp(req),
  });
  res.json({
    shop: { id: shop.id, name: shop.name },
    perms: await perms.overview(shop.id),
    devices: await deviceBlock.listForShop(shop.id),
  });
});

router.put('/roles/:role', requireSuperAdmin, async (req, res) => {
  const shop = await shopOf(req);
  const role = v.text(req.params.role, { max: 20, required: true, field: 'نقش' });
  const layer = await perms.setRoleLayer(shop.id, role, req.body?.perms, `admin:${req.admin.id}`);
  await audit.log({
    shopId: shop.id, actorType: 'admin', action: 'perms.role_updated', targetType: 'role', targetId: role,
    detail: { perms: layer, admin: req.admin.id }, ip: clientIp(req),
  });
  res.json({ role, perms: layer });
});

router.put('/members/:uid', requireSuperAdmin, async (req, res) => {
  const shop = await shopOf(req);
  const uid = v.id(req.params.uid, { field: 'شناسه عضو' });
  const { member, layer } = await perms.setMemberLayer(shop.id, uid, req.body?.perms, `admin:${req.admin.id}`);
  await audit.log({
    shopId: shop.id, actorType: 'admin', action: 'perms.member_updated', targetType: 'member', targetId: member.user_id,
    detail: { perms: layer, admin: req.admin.id }, ip: clientIp(req),
  });
  res.json({ userId: member.user_id, perms: layer });
});

router.post('/devices/:did/disable', requireSuperAdmin, async (req, res) => {
  const shop = await shopOf(req);
  const did = v.id(req.params.did, { field: 'شناسه دستگاه' });
  const out = await deviceBlock.disable(shop.id, did, {
    byId: `admin:${req.admin.id}`, actorType: 'admin', reason: v.text(req.body?.reason, { max: 200 }), ip: clientIp(req),
  });
  res.json({ ok: true, ...out });
});

module.exports = router;
