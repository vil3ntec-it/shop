'use strict';
/**
 * پنلِ مدیرِ سامانه: مجوزها و دستگاه‌های یک دکان (STF-01..03، STF-10).
 *
 * زیرِ `/api/admin/shops/:id/staff-perms` و پشتِ `requireAdmin`ِ `routes/admin.js`.
 * خواندن برای هر مدیر؛ نوشتن فقط مدیرِ ارشد (همان قاعدهٔ بقیهٔ پنل) و
 * همان سنجشِ هسته که صاحب دکان دارد — پنل هم بیش از نقش نمی‌دهد.
 *
 * پنلِ مرکزی (۱.۵۰.۷۵، توانایی‌های `staff-perms.v1` و `idempotent-writes.v1`): `?limit=&offset=` (سقفِ ۲۰۰؛ بی آن همان
 * پاسخِ امروز)، برگرداندنِ دستگاه برای مدیر (ارشد + دلیلِ ۳ تا ۲۰۰، همان `deviceOfShop` و سابقهٔ `device.enabled`؛ از کار انداختن
 * هم با همان دلیلِ اجباری از ۱۴۰۵/۰۸/۲۰) و
 * `Idempotency-Key` روی هر دو کارِ دستگاه (`lib/admin-write-once.js`).
 */
const express = require('express');
const { one } = require('../db');
const v = require('../lib/validate');
const audit = require('../lib/audit');
const perms = require('../lib/staff-perms');
const deviceBlock = require('../lib/device-block');
const once = require('../lib/admin-write-once');
const { requireReason } = require('../lib/admin-access');
const { requireSuperAdmin } = require('../middleware/auth');
const { notFound, badRequest } = require('../middleware/errors');
const { clientIp } = require('../middleware/ratelimit');

const router = express.Router({ mergeParams: true });

async function shopOf(req) {
  const id = v.id(req.params.id);
  const shop = await one('SELECT id, name FROM shops WHERE id=$1', [id]);
  if (!shop) throw notFound('دکان پیدا نشد');
  return shop;
}

/** `?limit=&offset=` ⇒ صفحه (هر دو فهرست با همان دو عدد)؛ هیچ‌کدام ⇒ null (همان پاسخِ امروز). */
function pageOf(req) {
  const q = req.query || {};
  if (q.limit === undefined && q.offset === undefined) return null;
  //  فقط رقمِ ساده (نه «1e2»، «1.5»، خالی یا دو بار) — بیرونِ بازه ⇒ ۴۰۰، نه بریدنِ بی‌صدا
  const int = (x, field, min, max) => {
    const n = typeof x === 'string' && /^\d{1,7}$/.test(x) ? Number(x) : NaN;
    if (!(n >= min && n <= max)) throw badRequest(`${field} باید عددی از ${min} تا ${max} باشد`, 'bad_page');
    return n;
  };
  return {
    limit: q.limit === undefined ? 50 : int(q.limit, 'limit', 1, 200),
    offset: q.offset === undefined ? 0 : int(q.offset, 'offset', 0, 1e6),
  };
}

router.get('/', async (req, res) => {
  const shop = await shopOf(req);
  const page = pageOf(req);
  //  TEN-04: اجازه‌های اعضا و دستگاه‌هایشان مالِ یک دکان است ⇒ دیدنش سابقه دارد، مثلِ پروندهٔ دکان
  await audit.log({
    shopId: shop.id, actorType: 'admin', userId: req.admin.id, action: 'admin.shop_staff_perms_viewed',
    targetType: 'shop', targetId: shop.id, ip: clientIp(req),
  });
  const out = {
    shop: { id: shop.id, name: shop.name },
    perms: await perms.overview(shop.id, page),
    devices: await deviceBlock.listForShop(shop.id, page),
  };
  if (page) {
    const membersTotal = await perms.memberCount(shop.id);
    const devicesTotal = await deviceBlock.countForShop(shop.id);
    const next = page.offset + page.limit;
    out.page = { ...page, membersTotal, devicesTotal, nextOffset: next < Math.max(membersTotal, devicesTotal) ? next : null };
  }
  res.json(out);
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

//  تصمیمِ ۲-۲۰ (۱۴۰۵/۰۸/۲۰): از کار انداختن هم مثلِ برگرداندن دلیلِ نوشته می‌خواهد (۳ تا ۲۰۰؛ پنلِ مرکزی همیشه می‌فرستد)
router.post('/devices/:did/disable', requireSuperAdmin, async (req, res) => {
  const shop = await shopOf(req);
  const did = v.id(req.params.did, { field: 'شناسه دستگاه' });
  const reason = requireReason(req);
  once.send(res, await once.writeOnce(req, { tenantId: shop.id, resultId: did }, async () => ({
    ok: true,
    ...await deviceBlock.disable(shop.id, did, { byId: `admin:${req.admin.id}`, actorType: 'admin', reason, ip: clientIp(req) }),
  })));
});

//  همتای مدیرِ `POST /api/shop/devices/:id/enable`: همان مرزِ دکان (`deviceOfShop` ⇒ ۴۰۴)، همان سابقه + دلیلِ نوشته
router.post('/devices/:did/enable', requireSuperAdmin, async (req, res) => {
  const shop = await shopOf(req);
  const did = v.id(req.params.did, { field: 'شناسه دستگاه' });
  const reason = requireReason(req);
  once.send(res, await once.writeOnce(req, { tenantId: shop.id, resultId: did }, async () => ({
    ok: true,
    ...await deviceBlock.enable(shop.id, did, { byId: `admin:${req.admin.id}`, actorType: 'admin', reason, ip: clientIp(req) }),
  })));
});

module.exports = router;
