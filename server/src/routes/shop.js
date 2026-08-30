'use strict';
/**
 * دکان، اعضا و کدهای شاگرد.
 *
 * shop_id هیچ‌وقت از بدنه‌ی درخواست خوانده نمی‌شود؛ همیشه از عضویت
 * کاربرِ توکن پیدا می‌شود.
 */
const express = require('express');
const { one, many, now, newId } = require('../db');
const config = require('../config');
const v = require('../lib/validate');
const shops = require('../lib/shops');
const staffCodes = require('../lib/staff-codes');
const audit = require('../lib/audit');
const subsLib = require('../lib/subscriptions');
const { entitlementOf } = require('../lib/entitlement');
const { permissionsOf } = require('../lib/permissions');
const { requireUser, requireShop, optionalShop, requirePermission, requireFeature } = require('../middleware/auth');
const { rateLimit, clientIp } = require('../middleware/ratelimit');
const { badRequest, forbidden, notFound } = require('../middleware/errors');

const router = express.Router();
router.use(requireUser);

const joinLimit = rateLimit({ max: config.rateLimit.joinMax, keyPrefix: 'join' });

function shopPayload(shop, member, extra = {}) {
  return {
    shop: {
      id: shop.id, name: shop.name, status: shop.status,
      ownerUserId: shop.owner_user_id, createdAt: Number(shop.created_at),
      maxMembers: shop.max_members,
    },
    role: member.role,
    isOwner: member.role === 'owner',
    permissions: permissionsOf(member.role),
    ...extra,
  };
}

// ---------- دکان من ----------
// «/me» نام قدیمی همین مسیر است و برای نسخه‌های قبلی برنامه باز مانده.
router.get(['/', '/me'], optionalShop, async (req, res) => {
  if (!req.member) return res.json({ shop: null, role: null, permissions: [] });
  const shop = await shops.getShop(req.shopId);
  const ent = await entitlementOf(req.shopId);
  res.json(shopPayload(shop, req.member, {
    memberCount: await shops.memberCount(req.shopId),
    entitlement: { source: ent.source, subscription: ent.subscription, trial: ent.trial },
    serverTime: now(),
  }));
});

// ---------- ساخت دکان ----------
router.post(['/', '/create'], async (req, res) => {
  const name = v.text(req.body?.name, { max: 80 }) || 'دکان من';
  const shop = await shops.createShop(req.user.id, name);
  const member = await shops.membershipOf(req.user.id);
  await audit.log({ shopId: shop.id, userId: req.user.id, action: 'shop.created', detail: { name }, ip: clientIp(req) });
  res.status(201).json(shopPayload(shop, member, { memberCount: 1 }));
});

// ---------- ویرایش دکان ----------
router.put('/', requireShop, requirePermission('shop.update'), async (req, res) => {
  const name = v.text(req.body?.name, { max: 80, required: true, field: 'نام دکان' });
  const shop = await shops.updateShop(req.shopId, { name });
  await audit.log({ shopId: shop.id, userId: req.user.id, action: 'shop.updated', detail: { name } });
  res.json(shopPayload(shop, req.member));
});

// ---------- اعضا ----------
router.get('/members', requireShop, requirePermission('members.view'), async (req, res) => {
  const rows = await shops.members(req.shopId);
  res.json({
    members: rows.map(m => ({
      id: m.id, userId: m.user_id, name: m.name || '',
      phone: m.phone || '', email: m.email || '',
      role: m.role, status: m.status,
      joinedAt: Number(m.created_at), lastLoginAt: m.last_login_at ? Number(m.last_login_at) : null,
    })),
    maxMembers: (await shops.getShop(req.shopId)).max_members,
  });
});

router.patch('/members/:id', requireShop, requirePermission('members.role'), async (req, res) => {
  const id = v.id(req.params.id, { field: 'شناسه عضو' });
  const patch = {
    role: req.body?.role ? v.oneOf(req.body.role, ['manager', 'staff'], { field: 'نقش' }) : undefined,
    status: req.body?.status ? v.oneOf(req.body.status, ['active', 'suspended'], { field: 'وضعیت' }) : undefined,
  };
  const row = await shops.updateMember(req.shopId, id, patch);
  await audit.log({ shopId: req.shopId, userId: req.user.id, action: 'member.updated', targetType: 'member', targetId: id, detail: patch });
  res.json({ member: { id: row.id, role: row.role, status: row.status } });
});

router.delete('/members/:id', requireShop, requirePermission('members.manage'), async (req, res) => {
  const id = v.id(req.params.id, { field: 'شناسه عضو' });
  await shops.updateMember(req.shopId, id, { status: 'removed' });
  await audit.log({ shopId: req.shopId, userId: req.user.id, action: 'member.removed', targetType: 'member', targetId: id });
  res.json({ ok: true });
});

/** خروج داوطلبانه‌ی شاگرد از دکان. */
router.post('/leave', requireShop, async (req, res, next) => {
  if (req.member.role === 'owner') return next(badRequest('صاحب دکان نمی‌تواند از دکان خودش بیرون برود', 'owner_cannot_leave'));
  await shops.updateMember(req.shopId, req.member.id, { status: 'removed' });
  await audit.log({ shopId: req.shopId, userId: req.user.id, action: 'member.left' });
  res.json({ ok: true });
});

// ---------- کدهای شاگرد ----------
router.get('/staff-codes', requireShop, requirePermission('staffcode.view'), async (req, res) => {
  const rows = await staffCodes.list(req.shopId);
  res.json({
    codes: rows.map(r => ({
      id: r.id, hint: r.code_hint, role: r.role, status: r.status,
      createdAt: Number(r.created_at),
      expiresAt: r.expires_at ? Number(r.expires_at) : null,
      maxUses: r.max_uses, usedCount: r.used_count,
    })),
  });
});

/**
 * کد ثابت دکان.
 *
 * یک کد، برای همه‌ی شاگردها، که تا وقتی خودِ صاحب دکان عوضش نکند همان
 * می‌ماند. برخلاف کدهای یک‌بارمصرف، این هر بار قابل دیدن است چون از
 * shop_id ساخته می‌شود نه از دیتابیس خوانده.
 */
router.get('/staff-code', requireShop, requirePermission('staffcode.view'), async (req, res) => {
  const out = await staffCodes.standing(req.shopId, req.user.id);
  res.json({ code: out.code, id: out.id, role: out.role, generation: out.generation, standing: true });
});

/** عوض کردن کد ثابت — برای وقتی که لو رفته باشد. شاگردهای فعلی می‌مانند. */
router.post('/staff-code/rotate', requireShop, requirePermission('staffcode.create'), requireFeature('multi_device'), async (req, res) => {
  const out = await staffCodes.rotateStanding(req.shopId, req.user.id);
  await audit.log({ shopId: req.shopId, userId: req.user.id, action: 'staff_code.rotated', targetType: 'staff_code', targetId: out.id, detail: { generation: out.generation } });
  res.json({ code: out.code, id: out.id, role: out.role, generation: out.generation, standing: true });
});

// «چند کاربر روی یک دکان» قابلیت اشتراکی است؛ بررسی‌اش اینجا سمت سرور
// انجام می‌شود، نه با پنهان کردن دکمه در گوشی.
router.post(['/staff-code', '/invite'], requireShop, requirePermission('staffcode.create'), requireFeature('multi_device'), async (req, res) => {
  const role = req.body?.role ? v.oneOf(req.body.role, ['manager', 'staff'], { field: 'نقش' }) : 'staff';
  const maxUses = v.integer(req.body?.maxUses, { field: 'تعداد استفاده', min: 0, max: 500, def: 1 });
  const days = v.integer(req.body?.expiresInDays, { field: 'مهلت', min: 0, max: 3650, def: 0 });
  const expiresAt = days ? now() + days * 24 * 3600 * 1000 : null;

  const out = await staffCodes.create(req.shopId, req.user.id, { role, expiresAt, maxUses });
  await audit.log({ shopId: req.shopId, userId: req.user.id, action: 'staff_code.created', targetType: 'staff_code', targetId: out.id, detail: { role, maxUses, expiresAt } });
  // کد فقط همین یک بار دیده می‌شود
  res.status(201).json({ code: out.code, id: out.id, role, maxUses, expiresAt });
});

router.delete('/staff-codes/:id', requireShop, requirePermission('staffcode.revoke'), async (req, res) => {
  const id = v.id(req.params.id, { field: 'شناسه کد' });
  await staffCodes.revoke(req.shopId, id);
  await audit.log({ shopId: req.shopId, userId: req.user.id, action: 'staff_code.revoked', targetType: 'staff_code', targetId: id });
  res.json({ ok: true });
});

// ---------- ورود شاگرد به دکان ----------
router.post(['/staff/join', '/join'], joinLimit, async (req, res) => {
  const code = v.text(req.body?.code, { max: 40, required: true, field: 'کد شاگرد' });
  const { shop, role } = await staffCodes.redeem(code, req.user.id, clientIp(req));
  const member = await shops.membershipOf(req.user.id);
  const ent = await entitlementOf(shop.id);
  await audit.log({ shopId: shop.id, userId: req.user.id, action: 'staff.joined', detail: { role }, ip: clientIp(req) });
  res.status(201).json(shopPayload(shop, member, {
    entitlement: { source: ent.source, subscription: ent.subscription, trial: ent.trial },
  }));
});

// ---------- تنظیمات مشترک دکان ----------
router.get('/settings', requireShop, async (req, res) => {
  const row = await one('SELECT data, rev, updated_at FROM shop_settings WHERE shop_id=$1', [req.shopId]);
  res.json({ settings: row?.data || {}, rev: Number(row?.rev || 0), updatedAt: Number(row?.updated_at || 0) });
});

router.put('/settings', requireShop, requirePermission('settings.write'), async (req, res) => {
  const data = v.payload(req.body?.settings, { max: 128 * 1024, field: 'تنظیمات' });
  const row = await one(
    `INSERT INTO shop_settings (shop_id, data, rev, updated_at) VALUES ($1,$2::jsonb,1,$3)
     ON CONFLICT (shop_id) DO UPDATE SET data=excluded.data, rev=shop_settings.rev+1, updated_at=excluded.updated_at
     RETURNING data, rev`,
    [req.shopId, JSON.stringify(data), now()]
  );
  res.json({ settings: row.data, rev: Number(row.rev) });
});

// ---------- سابقه‌ی عملیات دکان ----------
router.get('/audit', requireShop, requirePermission('audit.view'), async (req, res) => {
  const limit = v.integer(req.query?.limit, { min: 1, max: 500, def: 100 });
  const rows = await many(
    `SELECT id, user_id, action, target_type, target_id, detail, created_at
       FROM audit_logs WHERE shop_id=$1 ORDER BY created_at DESC LIMIT $2`,
    [req.shopId, limit]
  );
  res.json({ entries: rows });
});

module.exports = router;
