'use strict';
/** حساب مشترک دکان + همگام‌سازی داده‌ها. */
const express = require('express');
const { getDb, now } = require('../db');
const shops = require('../lib/shops');
const sync = require('../lib/sync');
const audit = require('../lib/audit');
const { requireUser } = require('../middleware/auth');
const { rateLimit, clientIp } = require('../middleware/ratelimit');
const { badRequest, forbidden, notFound } = require('../middleware/errors');

const router = express.Router();
const syncLimit = rateLimit({ max: 240, keyPrefix: 'sync' });

router.use(requireUser);

/** دکان کاربر را پیدا می‌کند؛ اگر عضو هیچ دکانی نیست خطا می‌دهد. */
function myShop(req) {
  const shop = shops.getUserShop(req.user.id);
  if (!shop) throw forbidden('شما عضو هیچ دکانی نیستید', 'no_shop');
  return shop;
}

function publicShop(shop, role) {
  return {
    id: shop.id, name: shop.name, ownerId: shop.owner_id,
    maxMembers: shop.max_members, myRole: role || shop.my_role,
    createdAt: shop.created_at,
  };
}

// ---------- وضعیت دکان من ----------
router.get('/me', (req, res, next) => {
  try {
    const shop = shops.getUserShop(req.user.id);
    if (!shop) return res.json({ shop: null, members: [], serverTime: now() });
    res.json({
      shop: publicShop(shop),
      members: shops.listMembers(shop.id).map(m => ({
        userId: m.user_id, name: m.name, email: m.email, phone: m.phone,
        role: m.role, joinedAt: m.joined_at, lastLoginAt: m.last_login_at,
        isMe: m.user_id === req.user.id,
      })),
      rev: sync.currentRev(shop.id),
      serverTime: now(),
    });
  } catch (e) { next(e); }
});

// ---------- ساخت دکان ----------
router.post('/create', (req, res, next) => {
  try {
    const shop = shops.createShop(req.user.id, req.body?.name, req.body?.maxMembers);
    audit.log({ actorType: 'user', actorId: req.user.id, action: 'shop.create',
                targetType: 'shop', targetId: shop.id, ip: clientIp(req) });
    res.status(201).json({ shop: publicShop(shop, 'owner') });
  } catch (e) { next(e); }
});

// ---------- تغییر نام ----------
router.post('/rename', (req, res, next) => {
  try {
    const shop = myShop(req);
    const updated = shops.renameShop(shop.id, req.user.id, req.body?.name);
    res.json({ shop: publicShop(updated, shop.my_role) });
  } catch (e) { next(e); }
});

// ---------- کد دعوت ----------
router.post('/invite', (req, res, next) => {
  try {
    const shop = myShop(req);
    const inv = shops.createInvite(shop.id, req.user.id, req.body?.role);
    audit.log({ actorType: 'user', actorId: req.user.id, action: 'shop.invite',
                targetType: 'shop', targetId: shop.id, detail: { role: inv.role }, ip: clientIp(req) });
    res.status(201).json(inv);
  } catch (e) { next(e); }
});

// ---------- پیوستن با کد ----------
router.post('/join', (req, res, next) => {
  try {
    const shop = shops.joinWithInvite(req.user.id, req.body?.code);
    audit.log({ actorType: 'user', actorId: req.user.id, action: 'shop.join',
                targetType: 'shop', targetId: shop.id, ip: clientIp(req) });
    const m = shops.getMembership(shop.id, req.user.id);
    res.json({ shop: publicShop(shop, m ? m.role : 'staff'), rev: sync.currentRev(shop.id) });
  } catch (e) { next(e); }
});

// ---------- اعضا ----------
router.get('/members', (req, res, next) => {
  try {
    const shop = myShop(req);
    res.json({ members: shops.listMembers(shop.id), maxMembers: shop.max_members });
  } catch (e) { next(e); }
});

router.post('/members/:userId/remove', (req, res, next) => {
  try {
    const shop = myShop(req);
    shops.removeMember(shop.id, req.user.id, req.params.userId);
    audit.log({ actorType: 'user', actorId: req.user.id, action: 'shop.member_remove',
                targetType: 'shop', targetId: shop.id, detail: { removed: req.params.userId }, ip: clientIp(req) });
    res.json({ ok: true });
  } catch (e) { next(e); }
});

router.post('/leave', (req, res, next) => {
  try {
    const shop = myShop(req);
    shops.leaveShop(shop.id, req.user.id);
    res.json({ ok: true });
  } catch (e) { next(e); }
});

// ---------- همگام‌سازی: فرستادن تغییرات ----------
router.post('/sync/push', syncLimit, (req, res, next) => {
  try {
    const shop = myShop(req);
    const deviceId = req.device ? req.device.id : String(req.body?.deviceId || '').slice(0, 64);
    const result = sync.pushChanges(shop.id, deviceId, req.user.id, req.body?.changes || []);

    if (req.body?.settings && typeof req.body.settings === 'object') {
      sync.putSettings(shop.id, req.body.settings.data, req.body.settings.updatedAt);
    }
    res.json({ ...result, serverTime: now() });
  } catch (e) { next(e); }
});

// ---------- همگام‌سازی: گرفتن تغییرات ----------
router.get('/sync/pull', syncLimit, (req, res, next) => {
  try {
    const shop = myShop(req);
    const out = sync.pullChanges(shop.id, req.query.since, req.query.limit);
    res.json({ ...out, settings: sync.getSettings(shop.id), serverTime: now() });
  } catch (e) { next(e); }
});

// ---------- آمار ----------
router.get('/sync/status', (req, res, next) => {
  try {
    const shop = myShop(req);
    res.json({ ...sync.shopStats(shop.id), serverTime: now() });
  } catch (e) { next(e); }
});

module.exports = router;
