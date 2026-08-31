'use strict';
/**
 * ورود و ثبت‌نام.
 *
 * سه راه ورود پشتیبانی می‌شود و هر سه به یک user_id می‌رسند:
 *   ۱) شماره + کد یک‌بارمصرف
 *   ۲) حساب گوگل
 *   ۳) ایمیل یا شماره + رمز عبور
 *
 * ایمیل و شماره فقط «راه ورود»اند؛ شناسه‌ی اصلی همیشه user_id است.
 * حساب‌ها هرگز خودبه‌خود در هم ادغام نمی‌شوند.
 */
const express = require('express');
const { query, one, tx, newId, now } = require('../db');
const config = require('../config');
const v = require('../lib/validate');
const pw = require('../lib/password');
const tokens = require('../lib/tokens');
const otp = require('../lib/otp');
const google = require('../lib/google');
const audit = require('../lib/audit');
const { membershipOf } = require('../lib/shops');
const staffCodes = require('../lib/staff-codes');
const { rateLimit, clientIp } = require('../middleware/ratelimit');
const { requireUser } = require('../middleware/auth');
const { badRequest, unauthorized, forbidden, conflict, tooMany, notFound } = require('../middleware/errors');

const router = express.Router();

const authLimit = rateLimit({ max: config.rateLimit.authMax, keyPrefix: 'auth' });
//  کد شاگرد خودش رمز است، پس مثل رمز محدود می‌شود
const joinLimit = rateLimit({ max: config.rateLimit.joinMax, keyPrefix: 'staff-login' });
const otpLimit = rateLimit({
  max: config.rateLimit.otpMax, keyPrefix: 'otp',
  key: (req) => String(req.body?.phone || '').replace(/\D/g, '') || null,
});

// ---------- کمکی‌ها ----------

/** ثبت تلاش ورود — پایه‌ی قفل موقت حساب. */
async function noteAttempt(scope, identifier, ip, ok) {
  await query(
    'INSERT INTO login_attempts (scope, identifier, ip, ok, created_at) VALUES ($1,$2,$3,$4,$5)',
    [scope, identifier, ip, ok, now()]
  );
}

/** اگر پشت سر هم اشتباه زده شده، چند دقیقه صبر لازم است. */
async function assertNotLocked(scope, identifier) {
  const since = now() - config.rateLimit.lockoutMs;
  const r = await one(
    `SELECT COUNT(*)::int AS n FROM login_attempts
      WHERE scope=$1 AND identifier=$2 AND ok=false AND created_at > $3`,
    [scope, identifier, since]
  );
  if (r.n >= config.rateLimit.lockoutTries) {
    throw tooMany('تلاش ناموفق زیاد بود، چند دقیقه بعد دوباره امتحان کنید', 'locked_out');
  }
}

/** ثبت یا به‌روزرسانی دستگاه. */
async function upsertDevice(userId, device, ip) {
  const uid = v.id(device?.deviceId || device?.uid || '', { field: 'شناسه دستگاه', required: false, max: 64 });
  if (!uid) return null;
  const t = now();
  const row = await one(
    `INSERT INTO devices (id, user_id, device_uid, name, platform, status, created_at, last_seen_at, last_ip)
     VALUES ($1,$2,$3,$4,$5,'active',$6,$6,$7)
     ON CONFLICT (user_id, device_uid) DO UPDATE SET
       name = COALESCE(NULLIF(excluded.name,''), devices.name),
       platform = COALESCE(NULLIF(excluded.platform,''), devices.platform),
       last_seen_at = excluded.last_seen_at,
       last_ip = excluded.last_ip
     RETURNING *`,
    [newId('dev'), userId, uid,
      v.text(device?.name, { max: 80 }), v.text(device?.platform, { max: 40 }), t, ip]
  );
  return row;
}

/** ساخت نشست تازه (توکن دسترسی + توکن تازه‌سازی). */
async function issueSession(user, device, ip) {
  const dev = await upsertDevice(user.id, device, ip);
  const access = await tokens.issue({
    kind: 'access', subjectId: user.id, deviceId: dev?.id || null, ttlMs: config.tokens.accessTtlMs,
  });
  const refresh = await tokens.issue({
    kind: 'refresh', subjectId: user.id, deviceId: dev?.id || null, ttlMs: config.tokens.refreshTtlMs,
  });
  await query('UPDATE users SET last_login_at=$2 WHERE id=$1', [user.id, now()]);
  return {
    accessToken: access.token,
    accessExpiresAt: access.expiresAt,
    refreshToken: refresh.token,
    refreshExpiresAt: refresh.expiresAt,
    deviceId: dev?.id || null,
  };
}

function publicUser(u) {
  return {
    id: u.id, name: u.name || '', email: u.email || null, phone: u.phone || null,
    createdAt: Number(u.created_at), hasPassword: !!u.password_hash,
  };
}

/** پاسخ استاندارد ورود — همراه دکان و نقش، تا برنامه بداند کجاست. */
async function loginPayload(user, session) {
  const member = await membershipOf(user.id);
  return {
    user: publicUser(user),
    shop: member ? {
      id: member.shop_id, name: member.shop_name || '', role: member.role,
    } : null,
    ...session,
  };
}

// ---------- ورود شاگرد، فقط با کد ----------
/**
 * صاحب دکان کد را به شاگردش می‌دهد؛ شاگرد همان کد را در صفحه‌ی ورود
 * می‌زند و داخل است. نه ایمیل، نه شماره، نه رمز.
 *
 * ── چرا این‌طور ────────────────────────────────────────────────────
 * تا امروز شاگرد باید اول خودش حساب می‌ساخت (ایمیل یا شماره) و بعد کد
 * را می‌زد. یعنی صاحب دکان باید یک مرحله‌ی اضافه هم توضیح می‌داد، و
 * شاگردی که ایمیل ندارد اصلاً وارد نمی‌شد.
 *
 * ── همان دستگاه، همان حساب ─────────────────────────────────────────
 * شناسه‌ی دستگاه با شناسه‌ی دکان یک هویت پایدار می‌سازد
 * (`user_identities` با provider='staff'). پس شاگردی که فردا دوباره
 * وارد می‌شود همان حساب قبلی را می‌گیرد، نه یک عضو تازه — وگرنه هر
 * ورود یک صندلی از ظرفیت دکان می‌خورد و کد هم زودتر تمام می‌شد.
 *
 * ── امنیت ──────────────────────────────────────────────────────────
 * کد خودش رمز است، پس مثل رمز با آن رفتار می‌شود: محدودیت نرخ روی
 * همین مسیر، و صاحب دکان هر وقت بخواهد کد را باطل یا عوض می‌کند و
 * دسترسی همان لحظه بسته می‌شود.
 */
router.post(['/staff', '/staff-login'], joinLimit, async (req, res, next) => {
  try {
    const rawCode = v.text(req.body?.code, { max: 40, required: true, field: 'کد شاگرد' });
    const name = v.text(req.body?.name, { max: 80 });
    const device = req.body?.device || {};
    const deviceUid = v.id(device?.uid || device?.deviceId || '', {
      field: 'شناسه دستگاه', required: false, max: 64,
    });
    const ip = clientIp(req);

    const shopId = await staffCodes.shopIdOf(rawCode);
    const subject = deviceUid ? `${shopId}:${deviceUid}` : '';

    //  همان دستگاه پیش از این آمده؟ همان حساب را برمی‌گردانیم
    let user = null;
    if (subject) {
      const found = await one(
        `SELECT u.* FROM user_identities i JOIN users u ON u.id = i.user_id
          WHERE i.provider='staff' AND i.subject=$1`, [subject]
      );
      if (found) user = found;
    }

    const t = now();
    if (!user) {
      user = await one(
        `INSERT INTO users (id, name, kind, status, created_at, updated_at)
         VALUES ($1,$2,'staff','active',$3,$3) RETURNING *`,
        [newId('usr'), name || 'شاگرد', t]
      );
      if (subject) {
        await query(
          `INSERT INTO user_identities (id, user_id, provider, subject, created_at)
           VALUES ($1,$2,'staff',$3,$4) ON CONFLICT (provider, subject) DO NOTHING`,
          [newId('idn'), user.id, subject, t]
        );
      }
    } else if (name && name !== user.name) {
      await query('UPDATE users SET name=$2, updated_at=$3 WHERE id=$1', [user.id, name, t]);
      user.name = name;
    }

    if (user.status !== 'active') {
      return next(forbidden('این حساب غیرفعال شده است', 'user_disabled'));
    }

    /*
     *  عضوِ فعالِ همین دکان است؟ کد دوباره خرج نمی‌شود.
     *
     *  بدون این، هر بار باز کردن برنامه یک استفاده از کد می‌خورد و
     *  کدی که «یک بار مصرف» ساخته شده، دفعه‌ی دوم کار نمی‌کرد.
     */
    const member = await membershipOf(user.id);
    if (!member || member.shop_id !== shopId) {
      await staffCodes.redeem(rawCode, user.id, ip);
    }

    const session = await issueSession(user, { ...device, uid: deviceUid }, ip);
    res.status(201).json(await loginPayload(user, session));
  } catch (err) { next(err); }
});

// ---------- ثبت‌نام با رمز ----------
router.post('/register', authLimit, async (req, res, next) => {
  if (!config.allowRegistration) return next(forbidden('ثبت‌نام روی این سرور بسته است', 'registration_closed'));

  const name = v.text(req.body?.name, { max: 80 });
  const email = v.email(req.body?.email);
  const phone = v.phone(req.body?.phone);
  const password = typeof req.body?.password === 'string' ? req.body.password : '';

  // یکی از این دو کافی است — نه هر دو
  if (!email && !phone) return next(badRequest('ایمیل یا شماره موبایل لازم است', 'identifier_required'));
  const weak = pw.checkStrength(password);
  if (weak) return next(badRequest(weak, 'weak_password'));

  const clash = await one(
    'SELECT id FROM users WHERE (email IS NOT NULL AND email=$1) OR (phone IS NOT NULL AND phone=$2) LIMIT 1',
    [email, phone]
  );
  if (clash) return next(conflict('این ایمیل یا شماره از قبل ثبت شده است', 'already_registered'));

  const hash = await pw.hashPassword(password);
  const t = now();
  const user = await one(
    `INSERT INTO users (id, name, email, phone, password_hash, status, created_at, updated_at)
     VALUES ($1,$2,$3,$4,$5,'active',$6,$6) RETURNING *`,
    [newId('usr'), name, email, phone, hash, t]
  );

  const session = await issueSession(user, req.body?.device, clientIp(req));
  await audit.log({ actorType: 'user', userId: user.id, action: 'auth.register', ip: clientIp(req) });
  res.status(201).json(await loginPayload(user, session));
});

// ---------- ورود با رمز ----------
router.post('/login', authLimit, async (req, res, next) => {
  const raw = String(req.body?.identifier || req.body?.email || req.body?.phone || '').trim();
  if (!raw) return next(badRequest('ایمیل یا شماره موبایل لازم است', 'identifier_required'));
  const password = typeof req.body?.password === 'string' ? req.body.password : '';

  const isEmail = raw.includes('@');
  const identifier = isEmail ? v.email(raw, { required: true }) : v.phone(raw, { required: true });
  await assertNotLocked('user', identifier);

  const user = await one(
    isEmail ? 'SELECT * FROM users WHERE email=$1' : 'SELECT * FROM users WHERE phone=$1',
    [identifier]
  );
  const ok = user && user.password_hash && await pw.verifyPassword(password, user.password_hash);
  await noteAttempt('user', identifier, clientIp(req), !!ok);
  if (!ok) return next(unauthorized('ایمیل/شماره یا رمز درست نیست', 'bad_credentials'));
  if (user.status !== 'active') return next(forbidden('این حساب غیرفعال است', 'account_disabled'));

  const session = await issueSession(user, req.body?.device, clientIp(req));
  await audit.log({ actorType: 'user', userId: user.id, action: 'auth.login', detail: { method: 'password' }, ip: clientIp(req) });
  res.json(await loginPayload(user, session));
});

// ---------- کد یک‌بارمصرف ----------
/**
 * مقصد کد: شماره یا ایمیل.
 *
 * هر دو یک راه دارند — کاربر چیزی را که بلد است می‌زند و کد به همان
 * می‌رود. برای شماره پیامک یا واتساپ، برای ایمیل سرویس ایمیل؛ کدام
 * کدام است را خود سرور تصمیم می‌گیرد، نه برنامه.
 */
function destinationOf(body) {
  const raw = String(body?.phone || body?.email || body?.destination || '').trim();
  if (!raw) throw badRequest('شماره یا ایمیل لازم است', 'destination_required');
  return raw.includes('@')
    ? { kind: 'email', value: v.email(raw, { required: true }) }
    : { kind: 'phone', value: v.phone(raw, { required: true }) };
}

router.post('/otp/request', otpLimit, async (req, res, next) => {
  const to = destinationOf(req.body);
  const out = await otp.request(to.value, { purpose: 'login', ip: clientIp(req) });
  res.json({ ok: true, [to.kind]: to.value, destination: to.value, ...out });
});

router.post('/otp/verify', otpLimit, async (req, res, next) => {
  const to = destinationOf(req.body);
  await assertNotLocked('otp', to.value);

  try {
    await otp.verify(to.value, req.body?.code, { purpose: 'login' });
  } catch (err) {
    await noteAttempt('otp', to.value, clientIp(req), false);
    return next(err);
  }
  await noteAttempt('otp', to.value, clientIp(req), true);

  let user = await one(
    to.kind === 'email' ? 'SELECT * FROM users WHERE email=$1' : 'SELECT * FROM users WHERE phone=$1',
    [to.value]
  );
  let created = false;
  if (!user) {
    if (!config.allowRegistration) return next(forbidden('ثبت‌نام روی این سرور بسته است', 'registration_closed'));
    const t = now();
    user = await one(
      to.kind === 'email'
        ? `INSERT INTO users (id, name, email, status, created_at, updated_at)
           VALUES ($1,$2,$3,'active',$4,$4) RETURNING *`
        : `INSERT INTO users (id, name, phone, status, created_at, updated_at)
           VALUES ($1,$2,$3,'active',$4,$4) RETURNING *`,
      [newId('usr'), v.text(req.body?.name, { max: 80 }), to.value, t]
    );
    created = true;
  }
  if (user.status !== 'active') return next(forbidden('این حساب غیرفعال است', 'account_disabled'));

  const session = await issueSession(user, req.body?.device, clientIp(req));
  await audit.log({ actorType: 'user', userId: user.id, action: created ? 'auth.register' : 'auth.login', detail: { method: 'otp' }, ip: clientIp(req) });
  res.status(created ? 201 : 200).json({ created, ...(await loginPayload(user, session)) });
});

// ---------- ورود با گوگل ----------
router.post('/google', authLimit, async (req, res, next) => {
  const idToken = req.body?.idToken || req.body?.id_token;
  const profile = await google.verifyIdToken(idToken);

  // اول با شناسه‌ی گوگل، بعد با ایمیلِ تأییدشده
  let user = null;
  const identity = await one(
    `SELECT u.* FROM user_identities i JOIN users u ON u.id = i.user_id
      WHERE i.provider='google' AND i.subject=$1`,
    [profile.sub]
  );
  if (identity) user = identity;

  if (!user && profile.email && profile.emailVerified) {
    user = await one('SELECT * FROM users WHERE email=$1', [profile.email]);
  }

  let created = false;
  if (!user) {
    if (!config.allowRegistration) return next(forbidden('ثبت‌نام روی این سرور بسته است', 'registration_closed'));
    if (!profile.email) return next(badRequest('حساب گوگل ایمیل ندارد', 'google_no_email'));
    const t = now();
    user = await one(
      `INSERT INTO users (id, name, email, status, created_at, updated_at)
       VALUES ($1,$2,$3,'active',$4,$4) RETURNING *`,
      [newId('usr'), profile.name || '', profile.email, t]
    );
    created = true;
  }
  if (user.status !== 'active') return next(forbidden('این حساب غیرفعال است', 'account_disabled'));

  await query(
    `INSERT INTO user_identities (id, user_id, provider, subject, email, created_at)
     VALUES ($1,$2,'google',$3,$4,$5) ON CONFLICT (provider, subject) DO NOTHING`,
    [newId('idn'), user.id, profile.sub, profile.email || '', now()]
  );

  const session = await issueSession(user, req.body?.device, clientIp(req));
  await audit.log({ actorType: 'user', userId: user.id, action: created ? 'auth.register' : 'auth.login', detail: { method: 'google' }, ip: clientIp(req) });
  res.status(created ? 201 : 200).json({ created, ...(await loginPayload(user, session)) });
});

// ---------- تازه‌سازی نشست ----------
router.post('/refresh', async (req, res, next) => {
  const token = String(req.body?.refreshToken || req.body?.refresh_token || '');
  const row = await tokens.verify(token, 'refresh');
  if (!row) return next(unauthorized('نشست منقضی شده است، دوباره وارد شوید', 'invalid_token'));

  const user = await one('SELECT * FROM users WHERE id=$1', [row.subject_id]);
  if (!user || user.status !== 'active') return next(unauthorized('حساب در دسترس نیست', 'invalid_token'));

  const access = await tokens.issue({
    kind: 'access', subjectId: user.id, deviceId: row.device_id, ttlMs: config.tokens.accessTtlMs,
  });
  res.json({ accessToken: access.token, accessExpiresAt: access.expiresAt });
});

// ---------- خروج ----------
router.post('/logout', async (req, res) => {
  const refresh = String(req.body?.refreshToken || req.body?.refresh_token || '');
  const access = (req.headers.authorization || '').replace(/^Bearer\s+/i, '').trim();
  if (refresh) await tokens.revoke(refresh, 'refresh');
  if (access) await tokens.revoke(access, 'access');
  res.json({ ok: true });
});

/** خروج از همه‌ی دستگاه‌ها. */
router.post('/logout-all', requireUser, async (req, res) => {
  const n = await tokens.revokeAllForSubject(req.user.id);
  await audit.log({ actorType: 'user', userId: req.user.id, action: 'auth.logout_all', detail: { sessions: n } });
  res.json({ ok: true, sessions: n });
});

/** گذاشتن یا عوض کردن رمز عبور (برای کسی که با کد وارد شده). */
router.post('/password', requireUser, async (req, res, next) => {
  const next_ = typeof req.body?.newPassword === 'string' ? req.body.newPassword : '';
  const weak = pw.checkStrength(next_);
  if (weak) return next(badRequest(weak, 'weak_password'));

  if (req.user.password_hash) {
    const current = typeof req.body?.currentPassword === 'string' ? req.body.currentPassword : '';
    const ok = await pw.verifyPassword(current, req.user.password_hash);
    if (!ok) return next(unauthorized('رمز فعلی درست نیست', 'bad_credentials'));
  }
  const hash = await pw.hashPassword(next_);
  await query('UPDATE users SET password_hash=$2, updated_at=$3 WHERE id=$1', [req.user.id, hash, now()]);
  await audit.log({ actorType: 'user', userId: req.user.id, action: 'auth.password_changed' });
  res.json({ ok: true });
});

/* ---------- بازیابی رمز فراموش‌شده ---------- */

/**
 * درخواست کد بازیابی.
 *
 * کد به همان ایمیلی می‌رود که کاربر زده — نه جای دیگر. اگر آن ایمیل
 * حسابی نداشته باشد، باز هم همان پاسخ موفق برمی‌گردد و کدی فرستاده
 * نمی‌شود.
 *
 * چرا این‌طور: اگر می‌گفتیم «این ایمیل حساب ندارد»، هر کسی می‌توانست
 * با امتحان کردن نشانی‌ها بفهمد چه کسانی روی این سرور حساب دارند.
 *
 * `purpose = 'reset'` جداست از کد ورود، پس کدی که برای ورود صادر شده
 * به درد عوض کردن رمز نمی‌خورد و برعکس.
 */
router.post('/password/forgot', otpLimit, async (req, res, next) => {
  const raw = String(req.body?.email || req.body?.identifier || '').trim();
  if (!raw) return next(badRequest('ایمیل لازم است', 'email_required'));
  const email = v.email(raw, { required: true });

  const user = await one('SELECT id FROM users WHERE email=$1', [email]);
  let out = {};
  if (user) {
    out = await otp.request(email, { purpose: 'reset', ip: clientIp(req) });
    await audit.log({ actorType: 'user', userId: user.id, action: 'auth.password_reset_requested', ip: clientIp(req) });
  }

  //  پاسخ برای حسابِ موجود و ناموجود یکی است. `resendSeconds` هم می‌رود
  //  تا برنامه بتواند شمارش را نشان دهد، چه حساب باشد چه نباشد.
  res.json({
    ok: true,
    sent: true,
    email,
    resendSeconds: out.resendSeconds || Math.ceil(config.otp.resendMs / 1000),
    ...(out.devCode ? { devCode: out.devCode } : {}),
  });
});

/**
 * گذاشتن رمز تازه با کدی که به ایمیل رفته.
 *
 * بعد از عوض شدن رمز، همه‌ی نشست‌های باز بسته می‌شوند. اگر کسی رمز را
 * فراموش کرده چون گوشی‌اش دست دیگری افتاده، آن نشست هم باید برود.
 */
router.post('/password/reset', otpLimit, async (req, res, next) => {
  const email = v.email(req.body?.email, { required: true });
  const password = typeof req.body?.password === 'string' ? req.body.password : '';
  const weak = pw.checkStrength(password);
  if (weak) return next(badRequest(weak, 'weak_password'));

  await assertNotLocked('otp', email);
  try {
    await otp.verify(email, req.body?.code, { purpose: 'reset' });
  } catch (err) {
    await noteAttempt('otp', email, clientIp(req), false);
    return next(err);
  }
  await noteAttempt('otp', email, clientIp(req), true);

  const user = await one('SELECT * FROM users WHERE email=$1', [email]);
  if (!user) return next(notFound('حسابی با این ایمیل نیست', 'user_not_found'));
  if (user.status !== 'active') return next(forbidden('این حساب غیرفعال است', 'account_disabled'));

  const hash = await pw.hashPassword(password);
  await query('UPDATE users SET password_hash=$2, updated_at=$3 WHERE id=$1', [user.id, hash, now()]);
  await tokens.revokeAllForSubject(user.id);
  await audit.log({ actorType: 'user', userId: user.id, action: 'auth.password_reset', ip: clientIp(req) });

  //  و همان‌جا واردش می‌کنیم؛ کسی که تازه رمز گذاشته نباید دوباره بزندش
  const session = await issueSession(user, req.body?.device, clientIp(req));
  res.json(await loginPayload(user, session));
});

module.exports = router;
module.exports.issueSession = issueSession;
module.exports.publicUser = publicUser;
module.exports.loginPayload = loginPayload;
