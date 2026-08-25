'use strict';
/** پلن‌ها، وضعیت دسترسی و درخواست خرید. */
const express = require('express');
const { getDb, newId, now } = require('../db');
const plansLib = require('../lib/plans');
const ent = require('../lib/entitlement');
const audit = require('../lib/audit');
const { requireUser } = require('../middleware/auth');
const { bearer } = require('../middleware/auth');
const tokens = require('../lib/tokens');
const { clientIp, rateLimit } = require('../middleware/ratelimit');
const { badRequest, notFound } = require('../middleware/errors');

const router = express.Router();

/** ساخت لینک واتساپ برای تماس با فروشنده. */
function whatsappLink(cfg, planTitle) {
  const raw = String(cfg.whatsapp_number || '').replace(/[^\d]/g, '');
  // شماره محلی افغانستان: 07xxxxxxxx → 937xxxxxxxx
  const intl = raw.startsWith('0') ? '93' + raw.slice(1) : raw;
  const text = planTitle
    ? `${cfg.whatsapp_message} پلن: ${planTitle}`
    : cfg.whatsapp_message;
  return `https://wa.me/${intl}?text=${encodeURIComponent(text)}`;
}

// ---------- پلن‌ها (عمومی؛ برای نمایش صفحه اشتراک بدون ورود) ----------
router.get('/plans', (req, res, next) => {
  try {
    const db = getDb();
    const cfg = plansLib.getConfig(db);
    const plans = plansLib.listPlans(db).map(p => ({
      ...p,
      whatsappUrl: whatsappLink(cfg, p.title),
    }));
    res.json({
      plans,
      currency: cfg.currency,
      trialDays: plansLib.trialDays(db),
      whatsapp: { number: cfg.whatsapp_number, url: whatsappLink(cfg, null) },
      serverTime: now(),
    });
  } catch (e) { next(e); }
});

/**
 * وضعیت دسترسی.
 * بدون توکن هم جواب می‌دهد (مهمان) تا برنامه بدون اجبار به ورود کار کند.
 */
router.get('/status', (req, res, next) => {
  try {
    const token = bearer(req);
    if (!token) return res.json({ entitlement: ent.guestEntitlement(now()), serverTime: now() });
    const row = tokens.verify(token, 'access');
    if (!row) return res.json({ entitlement: ent.guestEntitlement(now()), serverTime: now() });
    const user = getDb().prepare('SELECT * FROM users WHERE id=?').get(row.subject_id);
    if (!user || user.status !== 'active') {
      return res.json({ entitlement: ent.guestEntitlement(now()), serverTime: now() });
    }
    res.json({ entitlement: ent.entitlementOf(user.id, now()), serverTime: now() });
  } catch (e) { next(e); }
});

// ---------- درخواست خرید ----------
router.post('/request', requireUser, rateLimit({ max: 20, keyPrefix: 'buyreq' }), (req, res, next) => {
  try {
    const db = getDb();
    const code = String(req.body?.planCode || '').trim();
    const plan = plansLib.getPlan(db, code);
    if (!plan) throw notFound('این پلن وجود ندارد', 'plan_not_found');

    const id = newId('preq');
    db.prepare(`INSERT INTO purchase_requests (id,user_id,plan_code,note,status,created_at)
                VALUES (?,?,?,?,'pending',?)`)
      .run(id, req.user.id, plan.code, String(req.body?.note || '').slice(0, 300), now());

    audit.log({ actorType: 'user', actorId: req.user.id, action: 'billing.request',
                targetType: 'user', targetId: req.user.id,
                detail: { plan: plan.code, price: plan.price }, ip: clientIp(req) });

    const cfg = plansLib.getConfig(db);
    res.status(201).json({
      ok: true, requestId: id, plan,
      whatsappUrl: whatsappLink(cfg, plan.title),
      message: 'درخواست شما ثبت شد. برای هماهنگی پرداخت، پیام واتساپ را بفرستید.',
    });
  } catch (e) { next(e); }
});

router.get('/my-requests', requireUser, (req, res, next) => {
  try {
    const rows = getDb().prepare(
      'SELECT * FROM purchase_requests WHERE user_id=? ORDER BY created_at DESC LIMIT 20'
    ).all(req.user.id);
    res.json({ requests: rows });
  } catch (e) { next(e); }
});

module.exports = router;
module.exports.whatsappLink = whatsappLink;
