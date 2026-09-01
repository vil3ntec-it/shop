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

/**
 * ویرایش حساب — نام، و یک‌بار ثبت شماره.
 *
 * ── چه چیزی عوض می‌شود و چه چیزی نه ──────────────────────────────────
 * نام هر وقت خواست عوض می‌شود؛ اسم آدم نشانه‌ی هویت حساب نیست.
 *
 * ایمیل و شماره **راه ورود**اند. عوض کردنشان یعنی حساب از دست صاحبش
 * در می‌آید: کسی که یک بار توکن گرفته باشد می‌توانست ایمیل را عوض کند
 * و از آن به بعد بازیابی رمز به ایمیل خودش برود. پس هیچ‌کدام از این
 * مسیر عوض نمی‌شوند.
 *
 * ولی یک حالت مانده بود: کسی که با ایمیل ثبت‌نام کرده و شماره ندارد.
 * برای او شماره «عوض کردن» نیست، «افزودن» است — و همان یک بار.
 * ثبتش که شد، دیگر از اینجا دست نمی‌خورد.
 * ────────────────────────────────────────────────────────────────────
 */
router.put('/', async (req, res) => {
  const name = v.text(req.body?.name, { max: 80 });
  const phone = v.phone(req.body?.phone);

  //  شماره‌ای که آمده و کاربر از قبل شماره داشته: بی‌صدا نادیده گرفته
  //  نمی‌شود، وگرنه برنامه فکر می‌کند ثبت شد
  if (phone && req.user.phone && phone !== req.user.phone) {
    return res.status(409).json({
      error: { code: 'phone_locked', message: 'شماره ثبت‌شده عوض نمی‌شود — با پشتیبانی تماس بگیرید' },
    });
  }

  const claiming = phone && !req.user.phone;
  if (claiming) {
    const taken = await one('SELECT id FROM users WHERE phone=$1 AND id<>$2', [phone, req.user.id]);
    if (taken) {
      return res.status(409).json({
        error: { code: 'phone_taken', message: 'این شماره روی حساب دیگری ثبت شده' },
      });
    }
  }

  const user = await one(
    `UPDATE users SET name=$2, phone=COALESCE($3, phone), updated_at=$4
     WHERE id=$1 RETURNING *`,
    [req.user.id, name, claiming ? phone : null, now()]
  );
  if (claiming) {
    await audit.log({
      shopId: req.shopId, userId: req.user.id, action: 'account.phone_added', detail: {},
    });
  }
  res.json({ user: publicUser(user) });
});

module.exports = router;
module.exports.subscriptionHandler = subscriptionHandler;
module.exports.plansHandler = plansHandler;
