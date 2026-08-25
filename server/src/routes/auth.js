'use strict';
/** ثبت‌نام، ورود و مدیریت نشست کاربر. */
const express = require('express');
const { getDb, newId, now } = require('../db');
const pw = require('../lib/password');
const tokens = require('../lib/tokens');
const audit = require('../lib/audit');
const config = require('../config');
const { rateLimit, clientIp } = require('../middleware/ratelimit');
const { requireUser } = require('../middleware/auth');
const { badRequest, unauthorized, forbidden, conflict, tooMany } = require('../middleware/errors');
const subs = require('../lib/subscriptions');

const router = express.Router();
const authLimit = rateLimit({ max: config.rateLimit.authMax, keyPrefix: 'auth' });

function normEmail(v) {
  if (typeof v !== 'string') return null;
  const s = v.trim().toLowerCase();
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(s) ? s : null;
}
function normPhone(v) {
  if (typeof v !== 'string') return null;
  const s = v.replace(/[\s-()]/g, '');
  return /^\+?\d{9,15}$/.test(s) ? s : null;
}

/** قفل موقت پس از چند تلاش ناموفق — جلوی حدس رمز را می‌گیرد. */
function checkLockout(scope, identifier) {
  const since = now() - config.rateLimit.lockoutMs;
  const row = getDb().prepare(`
    SELECT COUNT(*) AS n FROM login_attempts
    WHERE scope=? AND identifier=? AND ok=0 AND created_at > ?
  `).get(scope, identifier, since);
  return row.n >= config.rateLimit.lockoutTries;
}
function recordAttempt(scope, identifier, ip, ok) {
  getDb().prepare('INSERT INTO login_attempts (scope,identifier,ip,ok,created_at) VALUES (?,?,?,?,?)')
    .run(scope, identifier, ip, ok ? 1 : 0, now());
}

function publicUser(u) {
  return { id: u.id, email: u.email, phone: u.phone, name: u.name, status: u.status, createdAt: u.created_at };
}

// ---------- ثبت‌نام ----------
router.post('/register', authLimit, async (req, res, next) => {
  try {
    if (!config.allowRegistration) throw forbidden('ثبت‌نام جدید غیرفعال است', 'registration_disabled');
    const { password, name } = req.body || {};
    const email = normEmail(req.body?.email);
    const phone = normPhone(req.body?.phone);
    if (!email && !phone) throw badRequest('ایمیل یا شماره موبایل معتبر لازم است');

    const weak = pw.checkStrength(password);
    if (weak) throw badRequest(weak, 'weak_password');

    const db = getDb();
    const dup = db.prepare('SELECT id FROM users WHERE (email IS NOT NULL AND email=?) OR (phone IS NOT NULL AND phone=?)')
      .get(email, phone);
    if (dup) throw conflict('این ایمیل یا شماره قبلاً ثبت شده است', 'already_registered');

    const t = now();
    const user = {
      id: newId('usr'), email, phone,
      name: String(name || '').trim().slice(0, 80),
      password_hash: await pw.hashPassword(password),
      created_at: t, updated_at: t,
    };
    db.prepare(`INSERT INTO users (id,email,phone,name,password_hash,created_at,updated_at)
                VALUES (@id,@email,@phone,@name,@password_hash,@created_at,@updated_at)`).run(user);

    // دوره آزمایشی از همین لحظه شروع می‌شود — تاریخ‌ها با ساعت سرور
    const trial = require('../lib/entitlement').startTrialIfEligible(user.id, t);

    audit.log({ actorType: 'user', actorId: user.id, action: 'user.register',
                targetType: 'user', targetId: user.id,
                detail: { trialEndsAt: trial && trial.endsAt }, ip: clientIp(req) });

    res.status(201).json({ user: publicUser(user), trial });
  } catch (e) { next(e); }
});

// ---------- ورود ----------
router.post('/login', authLimit, async (req, res, next) => {
  try {
    const identifier = String(req.body?.identifier || '').trim();
    const password = req.body?.password;
    if (!identifier || typeof password !== 'string') throw badRequest('نام کاربری و رمز عبور لازم است');

    const ip = clientIp(req);
    if (checkLockout('user', identifier.toLowerCase())) {
      throw tooMany('به دلیل تلاش‌های ناموفق، ورود موقتاً قفل شده است. کمی بعد دوباره تلاش کنید.');
    }

    const email = normEmail(identifier);
    const phone = normPhone(identifier);
    const user = getDb().prepare(
      'SELECT * FROM users WHERE (email IS NOT NULL AND email=?) OR (phone IS NOT NULL AND phone=?)'
    ).get(email, phone);

    // پیام یکسان برای «کاربر نیست» و «رمز غلط» تا وجود حساب لو نرود
    const ok = user ? await pw.verifyPassword(password, user.password_hash) : false;
    if (!ok) {
      recordAttempt('user', identifier.toLowerCase(), ip, false);
      throw unauthorized('نام کاربری یا رمز عبور اشتباه است', 'invalid_credentials');
    }
    if (user.status !== 'active') throw forbidden('حساب کاربری غیرفعال است', 'account_disabled');

    recordAttempt('user', identifier.toLowerCase(), ip, true);
    getDb().prepare('UPDATE users SET last_login_at=? WHERE id=?').run(now(), user.id);

    const access = tokens.issue({ kind: 'access', subjectId: user.id, ttlMs: config.tokens.accessTtlMs });
    const refresh = tokens.issue({ kind: 'refresh', subjectId: user.id, ttlMs: config.tokens.refreshTtlMs });

    audit.log({ actorType: 'user', actorId: user.id, action: 'user.login',
                targetType: 'user', targetId: user.id, ip });

    res.json({
      user: publicUser(user),
      accessToken: access.token, accessExpiresAt: access.expiresAt,
      refreshToken: refresh.token, refreshExpiresAt: refresh.expiresAt,
    });
  } catch (e) { next(e); }
});

// ---------- تازه‌سازی توکن ----------
router.post('/refresh', authLimit, (req, res, next) => {
  try {
    const row = tokens.verify(req.body?.refreshToken, 'refresh');
    if (!row) throw unauthorized('توکن تازه‌سازی نامعتبر است', 'invalid_token');
    const user = getDb().prepare('SELECT * FROM users WHERE id=?').get(row.subject_id);
    if (!user || user.status !== 'active') throw forbidden('حساب کاربری غیرفعال است', 'account_disabled');

    const access = tokens.issue({ kind: 'access', subjectId: user.id, ttlMs: config.tokens.accessTtlMs });
    res.json({ accessToken: access.token, accessExpiresAt: access.expiresAt });
  } catch (e) { next(e); }
});

// ---------- خروج ----------
router.post('/logout', requireUser, (req, res, next) => {
  try {
    if (req.body?.refreshToken) tokens.revoke(req.body.refreshToken, 'refresh');
    if (req.body?.allDevices) tokens.revokeAllForSubject(req.user.id);
    else tokens.revoke(require('../middleware/auth').bearer(req), 'access');
    res.json({ ok: true });
  } catch (e) { next(e); }
});

// ---------- تغییر رمز ----------
router.post('/change-password', requireUser, authLimit, async (req, res, next) => {
  try {
    const { currentPassword, newPassword } = req.body || {};
    const ok = await pw.verifyPassword(currentPassword, req.user.password_hash);
    if (!ok) throw unauthorized('رمز فعلی اشتباه است', 'invalid_credentials');
    const weak = pw.checkStrength(newPassword);
    if (weak) throw badRequest(weak, 'weak_password');

    getDb().prepare('UPDATE users SET password_hash=?, updated_at=? WHERE id=?')
      .run(await pw.hashPassword(newPassword), now(), req.user.id);
    // با تغییر رمز، همه‌ی نشست‌های قبلی باطل می‌شوند
    tokens.revokeAllForSubject(req.user.id);
    audit.log({ actorType: 'user', actorId: req.user.id, action: 'user.change_password',
                targetType: 'user', targetId: req.user.id, ip: clientIp(req) });
    res.json({ ok: true, message: 'رمز عبور تغییر کرد؛ لطفاً دوباره وارد شوید' });
  } catch (e) { next(e); }
});

// ---------- پروفایل + وضعیت اشتراک ----------
router.get('/me', requireUser, (req, res, next) => {
  try {
    const ent = require('../lib/entitlement').entitlementOf(req.user.id, now());
    const devices = getDb().prepare(
      'SELECT id,device_uid,name,platform,status,created_at,last_seen_at,last_sync_at FROM devices WHERE user_id=? ORDER BY created_at'
    ).all(req.user.id);
    res.json({
      user: publicUser(req.user),
      entitlement: ent,
      subscription: ent.subscription,
      trial: ent.trial,
      devices,
      serverTime: now(),
    });
  } catch (e) { next(e); }
});

module.exports = router;
module.exports.publicUser = publicUser;
module.exports.checkLockout = checkLockout;
module.exports.recordAttempt = recordAttempt;
