'use strict';
/**
 * «من» — حساب، دکان، نقش، دسترسی‌ها و اشتراک.
 *
 * برنامه بعد از هر ورود و هر همگام‌سازی همین را می‌خواند و تصمیم قفل/باز
 * بودن قابلیت‌ها را از روی همین می‌گیرد؛ نه از روی ساعت گوشی.
 */
const express = require('express');
const { one, many, query, now } = require('../db');
const v = require('../lib/validate');
const { requireUser, optionalShop } = require('../middleware/auth');
const { entitlementOf } = require('../lib/entitlement');
const { permissionsOf } = require('../lib/permissions');
const { FEATURES } = require('../lib/features');
const subs = require('../lib/subscriptions');
const plans = require('../lib/plans');
const { publicUser } = require('./auth');
const audit = require('../lib/audit');

const router = express.Router();

router.use(requireUser, optionalShop);

router.get('/', async (req, res) => {
  const ent = req.shopId ? await entitlementOf(req.shopId) : null;
  res.json({
    user: publicUser(req.user),
    shop: req.member ? {
      id: req.member.shop_id,
      name: req.member.shop_name || '',
      role: req.member.role,
      isOwner: req.member.role === 'owner',
    } : null,
    permissions: req.member ? permissionsOf(req.member.role) : [],
    entitlement: ent ? {
      source: ent.source, features: ent.features,
      subscription: ent.subscription, trial: ent.trial,
    } : null,
    serverTime: now(),
  });
});

/** وضعیت اشتراک — همیشه با ساعت سرور. */
async function subscriptionHandler(req, res) {
  if (!req.shopId) {
    return res.json({
      status: 'none', active: false, source: 'none',
      features: [], serverTime: now(), shop: null,
    });
  }
  await subs.expireDue();
  const ent = await entitlementOf(req.shopId);
  res.json({
    shop: { id: req.shopId, name: req.member.shop_name || '' },
    status: ent.subscription.status,
    active: ent.subscription.active,
    source: ent.source,
    plan: ent.subscription.plan || '',
    startsAt: ent.subscription.startsAt,
    endsAt: ent.subscription.endsAt,
    daysLeft: ent.subscription.daysLeft,
    trial: ent.trial,
    features: ent.features,
    catalog: FEATURES,
    serverTime: now(),
  });
}

/** پلن‌ها و شماره‌ی واتساپ — برای صفحه‌ی اشتراک. */
async function plansHandler(req, res) {
  const cfg = await plans.allConfig();
  res.json({
    plans: await plans.listPlans(),
    whatsapp: { number: cfg.whatsapp_number || '', message: cfg.whatsapp_message || '' },
    currency: cfg.currency || 'افغانی',
    trialDays: Number(cfg.trial_days || 0),
  });
}

router.get('/subscription', subscriptionHandler);
router.get('/plans', plansHandler);

/** ثبت درخواست خرید — مدیر بعد از دریافت پول فعالش می‌کند. */
router.post('/purchase-request', async (req, res) => {
  if (!req.shopId) return res.status(403).json({ error: { code: 'no_shop', message: 'اول دکان بسازید' } });
  const planCode = v.text(req.body?.plan, { max: 20, required: true, field: 'پلن' });
  const note = v.text(req.body?.note, { max: 300 });
  const { newId } = require('../db');
  const row = await one(
    `INSERT INTO purchase_requests (id, shop_id, user_id, plan_code, note, status, created_at)
     VALUES ($1,$2,$3,$4,$5,'pending',$6) RETURNING *`,
    [newId('preq'), req.shopId, req.user.id, planCode, note, now()]
  );
  await audit.log({ shopId: req.shopId, userId: req.user.id, action: 'subscription.requested', detail: { plan: planCode } });
  res.status(201).json({ request: { id: row.id, plan: row.plan_code, status: row.status } });
});

/** دستگاه‌های این حساب. */
router.get('/devices', async (req, res) => {
  const rows = await many(
    'SELECT id, device_uid, name, platform, status, created_at, last_seen_at FROM devices WHERE user_id=$1 ORDER BY last_seen_at DESC NULLS LAST',
    [req.user.id]
  );
  res.json({ devices: rows });
});

router.delete('/devices/:id', async (req, res) => {
  const id = v.id(req.params.id, { field: 'شناسه دستگاه' });
  await query(`UPDATE devices SET status='revoked' WHERE id=$1 AND user_id=$2`, [id, req.user.id]);
  await query('UPDATE tokens SET revoked_at=$1 WHERE device_id=$2 AND revoked_at IS NULL', [now(), id]);
  res.json({ ok: true });
});

/** ویرایش نام حساب. */
router.put('/', async (req, res) => {
  const name = v.text(req.body?.name, { max: 80 });
  const user = await one('UPDATE users SET name=$2, updated_at=$3 WHERE id=$1 RETURNING *',
    [req.user.id, name, now()]);
  res.json({ user: publicUser(user) });
});

module.exports = router;
module.exports.subscriptionHandler = subscriptionHandler;
module.exports.plansHandler = plansHandler;
