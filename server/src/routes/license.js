'use strict';
/**
 * فعال‌سازی و همگام‌سازی License.
 *
 * جریان: برنامه → درخواست امن → سرور → بررسی حساب → بررسی اشتراک →
 *        تولید License امضاشده → بازگشت به برنامه.
 * سرور هرگز خودش به دستگاه کاربر وصل نمی‌شود؛ همیشه برنامه آغازکننده است.
 */
const express = require('express');
const { getDb, newId, now } = require('../db');
const config = require('../config');
const subs = require('../lib/subscriptions');
const licenseLib = require('../lib/license');
const audit = require('../lib/audit');
const tokens = require('../lib/tokens');
const { requireUser } = require('../middleware/auth');
const { clientIp, rateLimit } = require('../middleware/ratelimit');
const { badRequest, forbidden, notFound, conflict } = require('../middleware/errors');

const router = express.Router();
const syncLimit = rateLimit({ max: 60, keyPrefix: 'license' });

/** keys از app.locals می‌آید (در app.js بارگذاری شده). */
function keys(req) { return req.app.locals.keys; }

function normalizeDeviceInput(body) {
  const d = body?.device || {};
  const uid = String(d.uid || '').trim();
  if (!/^[A-Za-z0-9_-]{8,128}$/.test(uid)) {
    throw badRequest('شناسه دستگاه نامعتبر است', 'bad_device_uid');
  }
  return {
    uid,
    name: String(d.name || '').trim().slice(0, 80) || 'دستگاه بدون نام',
    platform: String(d.platform || '').trim().slice(0, 120),
    fingerprint: String(d.fingerprint || '').slice(0, 512),
  };
}

/** ثبت یا به‌روزرسانی دستگاه، با رعایت سقف تعداد دستگاه اشتراک. */
function upsertDevice(userId, dev, maxDevices, ip) {
  const db = getDb();
  const t = now();
  const existing = db.prepare('SELECT * FROM devices WHERE user_id=? AND device_uid=?').get(userId, dev.uid);

  if (existing) {
    if (existing.status === 'revoked') {
      throw forbidden('دسترسی این دستگاه توسط مدیر لغو شده است', 'device_revoked');
    }
    db.prepare(`UPDATE devices SET name=?, platform=?, fingerprint_hash=?, last_seen_at=?, last_ip=? WHERE id=?`)
      .run(dev.name, dev.platform, licenseLib.fingerprintHash(dev.fingerprint), t, ip, existing.id);
    return db.prepare('SELECT * FROM devices WHERE id=?').get(existing.id);
  }

  const activeCount = db.prepare(
    "SELECT COUNT(*) AS n FROM devices WHERE user_id=? AND status='active'"
  ).get(userId).n;
  if (activeCount >= maxDevices) {
    const err = conflict(
      `سقف دستگاه‌های مجاز این اشتراک ${maxDevices} دستگاه است. برای افزودن دستگاه جدید، یکی از دستگاه‌های قبلی را حذف کنید.`,
      'device_limit_reached'
    );
    err.devices = db.prepare(
      "SELECT id,name,platform,created_at,last_seen_at FROM devices WHERE user_id=? AND status='active'"
    ).all(userId);
    throw err;
  }

  const row = {
    id: newId('dev'), user_id: userId, device_uid: dev.uid,
    name: dev.name, platform: dev.platform,
    fingerprint_hash: licenseLib.fingerprintHash(dev.fingerprint),
    status: 'active', created_at: t, last_seen_at: t, last_sync_at: null, last_ip: ip,
  };
  db.prepare(`INSERT INTO devices (id,user_id,device_uid,name,platform,fingerprint_hash,status,created_at,last_seen_at,last_sync_at,last_ip)
              VALUES (@id,@user_id,@device_uid,@name,@platform,@fingerprint_hash,@status,@created_at,@last_seen_at,@last_sync_at,@last_ip)`).run(row);
  return row;
}

/** صدور License تازه برای یک دستگاه. Licenseهای قبلی همان دستگاه superseded می‌شوند. */
async function issueLicense({ req, user, device, state, sub }) {
  const db = getDb();
  const t = now();
  const k = keys(req);

  const licenseEndsAt = licenseLib.computeLicenseEnd({
    nowMs: t,
    subEndsAt: state.endsAt,
    graceDays: state.graceDays || 0,
    licenseTtlDays: state.licenseTtlDays,
  });

  const licenseId = newId('lic');
  const payload = licenseLib.buildPayload({
    licenseId,
    version: config.license.version,
    userId: user.id,
    deviceId: device.id,
    deviceUid: device.device_uid,
    subscriptionId: sub ? sub.id : null,
    keyId: k.keyId,
    issuer: config.license.issuer,
    audience: config.license.audience,
    nowMs: t,
    startsAt: state.startsAt,
    licenseEndsAt,
    subEndsAt: state.endsAt,
    graceDays: state.graceDays || 0,
    timezone: state.timezone,
    plan: state.plan,
    features: state.features,
    maxDevices: state.maxDevices,
    deviceFingerprint: device.fingerprint_hash,
  });

  const token = await licenseLib.signLicense(k.privateKey, payload);

  const tx = db.transaction(() => {
    db.prepare("UPDATE licenses SET status='superseded' WHERE device_id=? AND status='active'").run(device.id);
    db.prepare(`INSERT INTO licenses
      (id,user_id,device_id,subscription_id,version,key_id,features,starts_at,ends_at,sub_ends_at,issued_at,status)
      VALUES (?,?,?,?,?,?,?,?,?,?,?, 'active')`)
      .run(licenseId, user.id, device.id, sub ? sub.id : null, config.license.version, k.keyId,
           JSON.stringify(state.features), state.startsAt, licenseEndsAt, state.endsAt, t);
    db.prepare('UPDATE devices SET last_sync_at=?, last_seen_at=? WHERE id=?').run(t, t, device.id);
  });
  tx();

  return { licenseId, token, payload, expiresAt: licenseEndsAt };
}

// ---------- کلید عمومی (برای بررسی امضا در برنامه) ----------
router.get('/public-key', (req, res) => {
  const k = keys(req);
  res.json({ keyId: k.keyId, algorithm: 'ES256', format: 'spki', publicKey: k.publicKeyB64 });
});

// ---------- زمان سرور (برای محاسبه‌ی اختلاف ساعت) ----------
router.get('/time', (req, res) => {
  res.json({ serverTime: now(), timezone: config.defaults.timezone });
});

// ---------- فعال‌سازی اولیه ----------
router.post('/activate', requireUser, syncLimit, async (req, res, next) => {
  try {
    const dev = normalizeDeviceInput(req.body);
    const state = subs.evaluateUser(req.user.id, now());

    if (state.state === 'none') {
      throw forbidden('برای این حساب اشتراکی ثبت نشده است. با مدیر تماس بگیرید.', 'no_subscription');
    }
    if (!state.active) {
      // دستگاه ثبت می‌شود ولی License صادر نمی‌شود — کاربر بخش‌های رایگان را می‌بیند
      const device = upsertDevice(req.user.id, dev, Math.max(1, state.maxDevices || 1), clientIp(req));
      return res.status(403).json({
        error: { code: 'subscription_inactive', message: subscriptionMessage(state) },
        subscription: state, device: { id: device.id, name: device.name }, serverTime: now(),
      });
    }

    const sub = subs.getLiveSubscription(req.user.id);
    const device = upsertDevice(req.user.id, dev, state.maxDevices, clientIp(req));
    const issued = await issueLicense({ req, user: req.user, device, state, sub });

    // توکن دسترسی وابسته به دستگاه، تا لغو دستگاه نشست را هم ببندد
    const access = tokens.issue({
      kind: 'access', subjectId: req.user.id, deviceId: device.id, ttlMs: config.tokens.accessTtlMs,
    });

    audit.log({ actorType: 'user', actorId: req.user.id, action: 'license.activate',
                targetType: 'device', targetId: device.id,
                detail: { licenseId: issued.licenseId }, ip: clientIp(req) });

    res.json({
      license: issued.token,
      licenseId: issued.licenseId,
      expiresAt: issued.expiresAt,
      subscription: state,
      device: { id: device.id, uid: device.device_uid, name: device.name },
      accessToken: access.token, accessExpiresAt: access.expiresAt,
      serverTime: now(),
    });
  } catch (e) { next(e); }
});

// ---------- همگام‌سازی (تمدید/تغییر اشتراک) ----------
router.post('/sync', requireUser, syncLimit, async (req, res, next) => {
  try {
    const dev = normalizeDeviceInput(req.body);
    const db = getDb();
    const device = db.prepare('SELECT * FROM devices WHERE user_id=? AND device_uid=?')
      .get(req.user.id, dev.uid);
    if (!device) throw notFound('این دستگاه ثبت نشده است. دوباره فعال‌سازی کنید.', 'device_not_found');
    if (device.status !== 'active') throw forbidden('دسترسی این دستگاه لغو شده است', 'device_revoked');

    db.prepare('UPDATE devices SET last_seen_at=?, last_ip=? WHERE id=?').run(now(), clientIp(req), device.id);

    const state = subs.evaluateUser(req.user.id, now());
    if (!state.active) {
      return res.status(403).json({
        error: { code: 'subscription_inactive', message: subscriptionMessage(state) },
        subscription: state, serverTime: now(),
      });
    }

    const sub = subs.getLiveSubscription(req.user.id);
    const issued = await issueLicense({ req, user: req.user, device, state, sub });

    res.json({
      license: issued.token, licenseId: issued.licenseId, expiresAt: issued.expiresAt,
      subscription: state, serverTime: now(),
    });
  } catch (e) { next(e); }
});

// ---------- وضعیت (بدون صدور License) ----------
router.get('/status', requireUser, (req, res, next) => {
  try {
    const state = subs.evaluateUser(req.user.id, now());
    res.json({ subscription: state, serverTime: now() });
  } catch (e) { next(e); }
});

// ---------- دستگاه‌های خود کاربر ----------
router.get('/devices', requireUser, (req, res, next) => {
  try {
    const rows = getDb().prepare(`SELECT id,device_uid,name,platform,status,created_at,last_seen_at,last_sync_at
                                  FROM devices WHERE user_id=? ORDER BY created_at`).all(req.user.id);
    res.json({ devices: rows });
  } catch (e) { next(e); }
});

/** کاربر می‌تواند دستگاه خودش را حذف کند تا جا برای دستگاه جدید باز شود. */
router.delete('/devices/:id', requireUser, (req, res, next) => {
  try {
    const db = getDb();
    // شرط user_id در همین کوئری است: کاربر نمی‌تواند دستگاه دیگری را حذف کند
    const dev = db.prepare('SELECT * FROM devices WHERE id=? AND user_id=?').get(req.params.id, req.user.id);
    if (!dev) throw notFound('دستگاه پیدا نشد', 'device_not_found');

    db.transaction(() => {
      db.prepare('DELETE FROM devices WHERE id=?').run(dev.id);
      tokens.revokeAllForDevice(dev.id);
    })();

    audit.log({ actorType: 'user', actorId: req.user.id, action: 'device.remove',
                targetType: 'device', targetId: dev.id, ip: clientIp(req) });
    res.json({ ok: true });
  } catch (e) { next(e); }
});

function subscriptionMessage(state) {
  switch (state.state) {
    case 'none':      return 'برای این حساب اشتراکی ثبت نشده است';
    case 'pending':   return 'اشتراک شما هنوز شروع نشده است';
    case 'suspended': return 'اشتراک شما موقتاً معلق شده است';
    case 'cancelled': return 'اشتراک شما لغو شده است';
    case 'expired':   return 'اشتراک شما به پایان رسیده است';
    default:          return 'اشتراک شما فعال نیست';
  }
}

module.exports = router;
module.exports.issueLicense = issueLicense;
