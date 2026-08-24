'use strict';
/** API پنل مدیریت — همه‌ی مسیرها پشت requireAdmin هستند. */
const express = require('express');
const { getDb, newId, now } = require('../db');
const config = require('../config');
const pw = require('../lib/password');
const tokens = require('../lib/tokens');
const subs = require('../lib/subscriptions');
const audit = require('../lib/audit');
const time = require('../lib/time');
const { FEATURES, GRANTABLE_KEYS, CORE_KEYS } = require('../lib/features');
const { requireAdmin } = require('../middleware/auth');
const { rateLimit, clientIp } = require('../middleware/ratelimit');
const { badRequest, unauthorized, forbidden, notFound, tooMany } = require('../middleware/errors');
const { checkLockout, recordAttempt, publicUser } = require('./auth');

const router = express.Router();
const authLimit = rateLimit({ max: config.rateLimit.authMax, keyPrefix: 'adminauth' });

// ---------- ورود مدیر ----------
router.post('/login', authLimit, async (req, res, next) => {
  try {
    const username = String(req.body?.username || '').trim().toLowerCase();
    const password = req.body?.password;
    if (!username || typeof password !== 'string') throw badRequest('نام کاربری و رمز عبور لازم است');

    const ip = clientIp(req);
    if (checkLockout('admin', username)) {
      throw tooMany('به دلیل تلاش‌های ناموفق، ورود موقتاً قفل شده است');
    }

    const admin = getDb().prepare('SELECT * FROM admins WHERE username=?').get(username);
    const ok = admin ? await pw.verifyPassword(password, admin.password_hash) : false;
    if (!ok) {
      recordAttempt('admin', username, ip, false);
      throw unauthorized('نام کاربری یا رمز عبور اشتباه است', 'invalid_credentials');
    }
    if (admin.status !== 'active') throw forbidden('حساب مدیر غیرفعال است', 'admin_disabled');

    recordAttempt('admin', username, ip, true);
    getDb().prepare('UPDATE admins SET last_login_at=? WHERE id=?').run(now(), admin.id);
    const t = tokens.issue({ kind: 'admin', subjectId: admin.id, ttlMs: config.tokens.adminTtlMs });

    audit.log({ actorType: 'admin', actorId: admin.id, action: 'admin.login',
                targetType: 'admin', targetId: admin.id, ip });

    res.json({
      admin: { id: admin.id, username: admin.username, name: admin.name, role: admin.role },
      token: t.token, expiresAt: t.expiresAt,
    });
  } catch (e) { next(e); }
});

router.post('/logout', requireAdmin, (req, res) => {
  tokens.revoke(require('../middleware/auth').bearer(req), 'admin');
  res.json({ ok: true });
});

router.use(requireAdmin);   // ← از این پس همه چیز نیازمند مدیر است

router.get('/me', (req, res) => {
  res.json({
    admin: { id: req.admin.id, username: req.admin.username, name: req.admin.name, role: req.admin.role },
    serverTime: now(), defaultTimezone: config.defaults.timezone,
  });
});

// ---------- کاتالوگ قابلیت‌ها ----------
router.get('/features', (req, res) => {
  res.json({ features: FEATURES, grantable: GRANTABLE_KEYS, core: CORE_KEYS });
});

// ---------- آمار کلی ----------
router.get('/stats', (req, res, next) => {
  try {
    const db = getDb();
    const t = now();
    const users = db.prepare('SELECT COUNT(*) n FROM users').get().n;
    const devices = db.prepare("SELECT COUNT(*) n FROM devices WHERE status='active'").get().n;
    const live = db.prepare("SELECT * FROM subscriptions WHERE status IN ('active','suspended')").all();
    let active = 0, expired = 0, suspended = 0, pending = 0;
    for (const s of live) {
      const st = subs.evaluate(s, t).state;
      if (st === 'active' || st === 'grace') active++;
      else if (st === 'expired') expired++;
      else if (st === 'suspended') suspended++;
      else if (st === 'pending') pending++;
    }
    res.json({ users, devices, subscriptions: { total: live.length, active, expired, suspended, pending },
               serverTime: t, timezone: config.defaults.timezone });
  } catch (e) { next(e); }
});

// ---------- فهرست کاربران ----------
router.get('/users', (req, res, next) => {
  try {
    const db = getDb();
    const q = String(req.query.q || '').trim();
    const limit = Math.min(200, Math.max(1, Number(req.query.limit) || 50));
    const offset = Math.max(0, Number(req.query.offset) || 0);

    const rows = q
      ? db.prepare(`SELECT * FROM users
                    WHERE name LIKE @q OR email LIKE @q OR phone LIKE @q OR id = @exact
                    ORDER BY created_at DESC LIMIT @limit OFFSET @offset`)
          .all({ q: `%${q}%`, exact: q, limit, offset })
      : db.prepare('SELECT * FROM users ORDER BY created_at DESC LIMIT ? OFFSET ?').all(limit, offset);

    const t = now();
    const users = rows.map(u => {
      const state = subs.evaluateUser(u.id, t);
      const dev = db.prepare("SELECT COUNT(*) n FROM devices WHERE user_id=? AND status='active'").get(u.id).n;
      return { ...publicUser(u), lastLoginAt: u.last_login_at, deviceCount: dev, subscription: state };
    });
    const total = db.prepare('SELECT COUNT(*) n FROM users').get().n;
    res.json({ users, total, limit, offset });
  } catch (e) { next(e); }
});

// ---------- جزئیات یک کاربر ----------
router.get('/users/:id', (req, res, next) => {
  try {
    const db = getDb();
    const u = db.prepare('SELECT * FROM users WHERE id=?').get(req.params.id);
    if (!u) throw notFound('کاربر پیدا نشد');
    const t = now();
    res.json({
      user: { ...publicUser(u), lastLoginAt: u.last_login_at },
      subscription: subs.evaluateUser(u.id, t),
      subscriptions: subs.listSubscriptions(u.id).map(s => ({ ...s, features: subs.parseFeatures(s.features),
                                                              evaluated: subs.evaluate(s, t) })),
      devices: db.prepare('SELECT * FROM devices WHERE user_id=? ORDER BY created_at').all(u.id),
      licenses: db.prepare(`SELECT l.*, d.name AS device_name FROM licenses l
                            LEFT JOIN devices d ON d.id = l.device_id
                            WHERE l.user_id=? ORDER BY l.issued_at DESC LIMIT 50`).all(u.id)
                   .map(l => ({ ...l, features: subs.parseFeatures(l.features) })),
      audit: audit.list({ targetType: 'user', targetId: u.id, limit: 30 }),
      serverTime: t,
    });
  } catch (e) { next(e); }
});

// ---------- فعال/غیرفعال کردن کاربر ----------
router.post('/users/:id/status', (req, res, next) => {
  try {
    const status = req.body?.status;
    if (!['active', 'disabled'].includes(status)) throw badRequest('وضعیت نامعتبر است');
    const db = getDb();
    const u = db.prepare('SELECT * FROM users WHERE id=?').get(req.params.id);
    if (!u) throw notFound('کاربر پیدا نشد');

    db.prepare('UPDATE users SET status=?, updated_at=? WHERE id=?').run(status, now(), u.id);
    if (status === 'disabled') tokens.revokeAllForSubject(u.id);

    audit.log({ actorType: 'admin', actorId: req.admin.id, action: 'user.status',
                targetType: 'user', targetId: u.id, detail: { status }, ip: clientIp(req) });
    res.json({ ok: true, status });
  } catch (e) { next(e); }
});

// ---------- ساخت/جایگزینی اشتراک ----------
router.post('/users/:id/subscription', (req, res, next) => {
  try {
    const u = getDb().prepare('SELECT * FROM users WHERE id=?').get(req.params.id);
    if (!u) throw notFound('کاربر پیدا نشد');

    const sub = subs.createSubscription(u.id, req.body || {}, req.admin.id);
    audit.log({ actorType: 'admin', actorId: req.admin.id, action: 'subscription.create',
                targetType: 'user', targetId: u.id,
                detail: { subscriptionId: sub.id, startsAt: sub.starts_at, endsAt: sub.ends_at,
                          features: subs.parseFeatures(sub.features), tz: sub.timezone },
                ip: clientIp(req) });

    res.status(201).json({ subscription: { ...sub, features: subs.parseFeatures(sub.features) },
                           evaluated: subs.evaluate(sub, now()) });
  } catch (e) { next(e); }
});

// ---------- ویرایش اشتراک ----------
router.patch('/subscriptions/:id', (req, res, next) => {
  try {
    const before = subs.getSubscriptionById(req.params.id);
    if (!before) throw notFound('اشتراک پیدا نشد');
    const sub = subs.updateSubscription(req.params.id, req.body || {});

    audit.log({ actorType: 'admin', actorId: req.admin.id, action: 'subscription.update',
                targetType: 'user', targetId: sub.user_id,
                detail: { subscriptionId: sub.id, changes: Object.keys(req.body || {}) }, ip: clientIp(req) });
    res.json({ subscription: { ...sub, features: subs.parseFeatures(sub.features) },
               evaluated: subs.evaluate(sub, now()) });
  } catch (e) { next(e); }
});

// ---------- تمدید ----------
router.post('/subscriptions/:id/renew', (req, res, next) => {
  try {
    const { amount, unit } = req.body || {};
    if (amount === undefined || !unit) throw badRequest('مقدار و واحد تمدید لازم است (amount + unit)');
    const sub = subs.renewSubscription(req.params.id, amount, unit);

    audit.log({ actorType: 'admin', actorId: req.admin.id, action: 'subscription.renew',
                targetType: 'user', targetId: sub.user_id,
                detail: { subscriptionId: sub.id, amount, unit, newEnd: sub.ends_at }, ip: clientIp(req) });
    res.json({ subscription: { ...sub, features: subs.parseFeatures(sub.features) },
               evaluated: subs.evaluate(sub, now()) });
  } catch (e) { next(e); }
});

// ---------- تغییر وضعیت اشتراک (فعال/معلق/لغو) ----------
router.post('/subscriptions/:id/status', (req, res, next) => {
  try {
    const status = req.body?.status;
    if (!['active', 'suspended', 'cancelled'].includes(status)) throw badRequest('وضعیت نامعتبر است');
    const sub = subs.updateSubscription(req.params.id, { status });

    audit.log({ actorType: 'admin', actorId: req.admin.id, action: 'subscription.status',
                targetType: 'user', targetId: sub.user_id,
                detail: { subscriptionId: sub.id, status }, ip: clientIp(req) });
    res.json({ subscription: { ...sub, features: subs.parseFeatures(sub.features) },
               evaluated: subs.evaluate(sub, now()) });
  } catch (e) { next(e); }
});

// ---------- دستگاه‌ها ----------
router.get('/devices', (req, res, next) => {
  try {
    const db = getDb();
    const limit = Math.min(200, Math.max(1, Number(req.query.limit) || 100));
    const rows = db.prepare(`
      SELECT d.*, u.name AS user_name, u.email AS user_email, u.phone AS user_phone
      FROM devices d JOIN users u ON u.id = d.user_id
      ORDER BY d.last_seen_at DESC NULLS LAST, d.created_at DESC LIMIT ?
    `).all(limit);
    res.json({ devices: rows });
  } catch (e) { next(e); }
});

router.post('/devices/:id/revoke', (req, res, next) => {
  try {
    const db = getDb();
    const dev = db.prepare('SELECT * FROM devices WHERE id=?').get(req.params.id);
    if (!dev) throw notFound('دستگاه پیدا نشد');

    db.transaction(() => {
      db.prepare("UPDATE devices SET status='revoked' WHERE id=?").run(dev.id);
      db.prepare("UPDATE licenses SET status='revoked', revoked_at=?, revoked_reason=? WHERE device_id=? AND status='active'")
        .run(now(), 'device revoked', dev.id);
      tokens.revokeAllForDevice(dev.id);
    })();

    audit.log({ actorType: 'admin', actorId: req.admin.id, action: 'device.revoke',
                targetType: 'device', targetId: dev.id, detail: { userId: dev.user_id }, ip: clientIp(req) });
    res.json({ ok: true });
  } catch (e) { next(e); }
});

router.post('/devices/:id/restore', (req, res, next) => {
  try {
    const db = getDb();
    const dev = db.prepare('SELECT * FROM devices WHERE id=?').get(req.params.id);
    if (!dev) throw notFound('دستگاه پیدا نشد');
    db.prepare("UPDATE devices SET status='active' WHERE id=?").run(dev.id);
    audit.log({ actorType: 'admin', actorId: req.admin.id, action: 'device.restore',
                targetType: 'device', targetId: dev.id, ip: clientIp(req) });
    res.json({ ok: true });
  } catch (e) { next(e); }
});

router.delete('/devices/:id', (req, res, next) => {
  try {
    const db = getDb();
    const dev = db.prepare('SELECT * FROM devices WHERE id=?').get(req.params.id);
    if (!dev) throw notFound('دستگاه پیدا نشد');
    db.transaction(() => {
      tokens.revokeAllForDevice(dev.id);
      db.prepare('DELETE FROM devices WHERE id=?').run(dev.id);
    })();
    audit.log({ actorType: 'admin', actorId: req.admin.id, action: 'device.delete',
                targetType: 'device', targetId: dev.id, ip: clientIp(req) });
    res.json({ ok: true });
  } catch (e) { next(e); }
});

// ---------- ابطال License ----------
router.post('/licenses/:id/revoke', (req, res, next) => {
  try {
    const db = getDb();
    const lic = db.prepare('SELECT * FROM licenses WHERE id=?').get(req.params.id);
    if (!lic) throw notFound('License پیدا نشد');
    db.prepare("UPDATE licenses SET status='revoked', revoked_at=?, revoked_reason=? WHERE id=?")
      .run(now(), String(req.body?.reason || 'admin revoke').slice(0, 200), lic.id);
    audit.log({ actorType: 'admin', actorId: req.admin.id, action: 'license.revoke',
                targetType: 'user', targetId: lic.user_id, detail: { licenseId: lic.id }, ip: clientIp(req) });
    res.json({ ok: true });
  } catch (e) { next(e); }
});

// ---------- سابقه عملیات ----------
router.get('/audit', (req, res, next) => {
  try {
    res.json({ entries: audit.list({
      limit: Math.min(500, Math.max(1, Number(req.query.limit) || 100)),
      offset: Math.max(0, Number(req.query.offset) || 0),
    }) });
  } catch (e) { next(e); }
});

// ---------- منطقه‌های زمانی پرکاربرد ----------
router.get('/timezones', (req, res) => {
  const common = ['Asia/Kabul', 'Asia/Tehran', 'Asia/Dubai', 'Asia/Karachi', 'Asia/Kolkata',
                  'Asia/Tashkent', 'Europe/Istanbul', 'Europe/London', 'UTC'];
  const t = now();
  res.json({
    timezones: common.filter(time.isValidTimeZone).map(tz => ({
      id: tz, offsetMinutes: time.tzOffsetMs(t, tz) / 60000, localTime: time.formatInZone(t, tz),
    })),
    default: config.defaults.timezone,
  });
});

module.exports = router;
