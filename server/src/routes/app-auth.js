'use strict';
/**
 * ورود با کدِ شش‌رقمیِ ایمیلی — قراردادِ «پرامپتِ ورودِ بی‌نقص».
 *
 *   POST /api/auth/:app/request-code      ⇒ {request_id, expires_in, resend_after, masked_email}
 *   GET  /api/auth/:app/request-status    ⇒ {state, sent_at, attempts, can_resend_in}
 *   POST /api/auth/:app/verify            ⇒ توکن‌ها + user + subscription
 *
 * ── چرا این مسیرها جدا از `routes/auth.js`اند ───────────────────────────
 * آن یکی ورودِ امروزِ کاربرانِ دکان است (رمز، گوگل، ثبت‌نامِ سه‌پله‌ای) و
 * شکلِ پاسخش را برنامه‌های نصب‌شده می‌خوانند. این یکی قراردادِ **ثابتِ**
 * پرامپت است که هر سه برنامه با همان نوشته می‌شوند. دست زدن به آن یکی
 * یعنی بیرون افتادنِ مشتری‌های امروز؛ پس کنارِ هم می‌مانند و هر دو یک
 * دفترِ حساب (`users`) دارند.
 *
 * ── سه قاعده که نباید بشکنند ────────────────────────────────────────────
 *  ۱) پاسخِ `request-code` **همیشه** ۲۰۰ است مگر سقفِ نرخ — تا کسی نتواند
 *     با آن بفهمد کدام ایمیل حساب دارد.
 *  ۲) کاربر هیچ‌وقت منتظرِ ایمیل نمی‌ماند: ردیف در صف می‌نشیند و پاسخ
 *     همان لحظه می‌رود. صف نپذیرفت ⇒ ۵۰۳ صریح، نه سکوت.
 *  ۳) هر خطا یک `error` انگلیسی برای برنامه و یک `message` فارسی برای
 *     آدم دارد؛ «هیچی نشد» وجود ندارد.
 */
const express = require('express');
const { query, one, now, newId } = require('../db');
const config = require('../config');
const codes = require('../lib/login-codes');
const outbox = require('../lib/login-outbox');
const tokens = require('../lib/tokens');
const audit = require('../lib/audit');
const { clientIp, rateLimit } = require('../middleware/ratelimit');
const entitlement = require('../lib/entitlement');
const { sectionOf } = require('../lib/tenancy');

const router = express.Router({ mergeParams: true });

/*
 *  سقفِ نرخِ لبه — فقط برای اینکه سیلِ درخواست پیش از رسیدن به دیتابیس
 *  بایستد. سقف‌های **واقعی** (بیست در ده دقیقه برای هر IP، پنج در ساعت
 *  برای هر ایمیل) در `login-codes` با شمارشِ دیتابیسی‌اند و پس از
 *  ری‌استارت هم یادشان می‌ماند.
 *
 *  ⚠️ عددش عمداً بزرگ است: پشتِ تونل و بی `TRUST_PROXY`، همهٔ مشتری‌ها
 *  یک IP دیده می‌شوند. سقفِ کوچک این‌جا یعنی نفرِ صدویکم بی آن‌که کاری
 *  کرده باشد «درخواست زیاد است» می‌گیرد — و آن پیام هیچ راهی هم پیشِ
 *  پایش نمی‌گذارد.
 */
const edgeLimit = rateLimit({ max: Number(process.env.RATE_LOGIN_EDGE_MAX || 600), keyPrefix: 'login-edge' });

/**
 * پاسخِ خطا دقیقاً به شکلِ بندِ ۲ پرامپت.
 *
 * ⚠️ `codes.fail()` فیلدهای اضافه (`attempts_left`، `devices`) را **در
 * سطحِ بالا** می‌گذارد، نه داخلِ یک `extra`. پس همه‌چیز جز `status` و
 * `ok` عیناً منتقل می‌شود — یک بار این را زیرِ `f.extra` خواندم و
 * `attempts_left` هیچ‌وقت به برنامه نرسید (سنجه‌اش گرفت).
 */
function sendFail(res, f) {
  const { status, ok, retry_after: retryAfter, ...rest } = f;
  const body = { ok: false, ...rest };
  if (retryAfter) body.retry_after = retryAfter;
  return res.status(status).json(body);
}

const headerApp = (req) => String(req.headers['x-app'] || req.headers['x-app-id'] || '').trim();
const deviceOf = (req) => String(req.headers['x-device'] || req.body?.device_id || '').slice(0, 64);
const versionOf = (req) => String(req.headers['x-app-version'] || '').slice(0, 32);

/* ---------------------------------------------------------------- کد بگیر */

router.post('/request-code', edgeLimit, async (req, res, next) => {
  try {
    const app = codes.appOf(req.params.app);
    const result = await codes.requestCode({
      app,
      email: String(req.body?.email || ''),
      ip: clientIp(req),
      deviceId: deviceOf(req),
      deviceName: String(req.body?.device_name || '').slice(0, 80),
      appVersion: versionOf(req),
      clientRequestId: String(req.headers['x-request-id'] || '').slice(0, 64),
    });
    if (!result.ok) return sendFail(res, result);

    //  به صف، نه ارسالِ مستقیم. نپذیرفت ⇒ ردیف پس گرفته می‌شود تا کاربر
    //  شصت ثانیه پشتِ کدی که هیچ‌وقت نرفته گیر نکند.
    try {
      await outbox.enqueue({ id: result.request_id, app, email: result.email });
    } catch (err) {
      await codes.discard(result.request_id);
      return sendFail(res, codes.fail(
        503, 'EMAIL_SERVICE_DOWN', 'ارسال ایمیل موقتاً ممکن نیست. چند لحظه بعد تلاش کنید.', 15
      ));
    }

    await audit.log({
      actorType: 'system', action: 'login.code_requested', ip: clientIp(req),
      detail: { app, request_id: result.request_id, email: codes.mask(result.email) },
    }).catch(() => {});

    res.json({
      ok: true,
      request_id: result.request_id,
      expires_in: result.expires_in,
      resend_after: result.resend_after,
      masked_email: result.masked_email,
    });
  } catch (err) { next(err); }
});

/* ------------------------------------------------- ایمیل رفت یا نه؟ */

router.get('/request-status', async (req, res, next) => {
  try {
    const app = codes.appOf(req.params.app);
    const id = String(req.query?.request_id || '').slice(0, 80);
    const rec = await codes.requestById(id);
    if (!rec || rec.app !== app) {
      return sendFail(res, codes.fail(400, 'REQUEST_NOT_FOUND', 'درخواست نامعتبر است.'));
    }
    const st = (await outbox.statusOf(id)) || { state: 'queued', attempts: 0 };
    const elapsed = Math.floor((now() - Number(rec.created_at)) / 1000);
    res.json({
      ok: true,
      state: st.state,
      reason: st.reason,
      sent_at: st.sent_at,
      attempts: st.attempts,
      can_resend_in: Math.max(0, codes.RESEND_S - elapsed),
      expires_in: Math.max(0, Math.ceil((Number(rec.expires_at) - now()) / 1000)),
    });
  } catch (err) { next(err); }
});

/* ------------------------------------------------------------ کد را بسنج */

/** حسابِ این ایمیل در این برنامه؛ نبود، همین‌جا ساخته می‌شود. */
async function findOrCreateUser(email, ip) {
  const found = await one('SELECT * FROM users WHERE email=$1', [email]);
  if (found) return { user: found, created: false };
  const t = now();
  const row = await one(
    `INSERT INTO users (id, name, email, status, created_at, updated_at)
     VALUES ($1,$2,$3,'active',$4,$4) RETURNING *`,
    [newId('usr'), email.split('@')[0].slice(0, 60), email, t]
  );
  await audit.log({ actorType: 'system', userId: row.id, action: 'auth.register', ip, detail: { method: 'email_code' } }).catch(() => {});
  return { user: row, created: true };
}

/** عضویتِ این کاربر در بخشِ خواسته‌شده — برای `subscription` پاسخ. */
const MEMBERS = {
  shop: { table: 'shop_members', key: 'shop_id' },
  pump: { table: 'station_members', key: 'station_id' },
};

async function subscriptionOf(app, userId) {
  const section = sectionOf(app) || 'shop';
  const m = MEMBERS[section];
  if (!m) return { status: 'none', plan: null, ends_at: null, days_left: 0 };
  const member = await one(
    `SELECT ${m.key} AS tenant_id FROM ${m.table} WHERE user_id=$1 AND status='active' ORDER BY created_at ASC LIMIT 1`,
    [userId]
  ).catch(() => null);
  if (!member) return { status: 'none', plan: null, ends_at: null, days_left: 0 };

  const ent = await entitlement.forApp(section).entitlementOf(member.tenant_id);
  const sub = ent.subscription || {};
  const endsMs = Number(sub.endsAt || 0);
  return {
    status: ent.active ? 'active' : (sub.status || 'none'),
    plan: sub.plan || null,
    ends_at: endsMs ? Math.floor(endsMs / 1000) : null,
    days_left: endsMs ? Math.max(0, Math.ceil((endsMs - now()) / 86400000)) : 0,
    permanent: Boolean(sub.permanent) || (ent.active && !endsMs),
    source: ent.source || '',
    features: ent.features || [],
    tenant_id: member.tenant_id,
  };
}

async function upsertDevice(userId, deviceId, deviceName, ip) {
  if (!deviceId) return null;
  const t = now();
  return one(
    `INSERT INTO devices (id, user_id, device_uid, name, platform, status, created_at, last_seen_at, last_ip)
     VALUES ($1,$2,$3,$4,'','active',$5,$5,$6)
     ON CONFLICT (user_id, device_uid) DO UPDATE SET
       name = COALESCE(NULLIF(excluded.name,''), devices.name),
       last_seen_at = excluded.last_seen_at,
       last_ip = excluded.last_ip
     RETURNING *`,
    [newId('dev'), userId, deviceId, String(deviceName || '').slice(0, 80), t, String(ip || '').slice(0, 64)]
  ).catch(() => null);
}

router.post('/verify', edgeLimit, async (req, res, next) => {
  try {
    const app = codes.appOf(req.params.app);
    const result = await codes.verifyCode({
      app, requestId: req.body?.request_id, code: req.body?.code,
    });
    if (!result.ok) {
      await audit.log({
        actorType: 'system', action: 'login.code_failed', ip: clientIp(req),
        detail: { app, error: result.error, request_id: String(req.body?.request_id || '').slice(0, 80) },
      }).catch(() => {});
      return sendFail(res, result);
    }

    const { user, created } = await findOrCreateUser(result.email, clientIp(req));
    if (user.status !== 'active') {
      return sendFail(res, codes.fail(403, 'ACCOUNT_DISABLED', 'این حساب غیرفعال است.'));
    }

    const deviceId = deviceOf(req) || result.request.device_id;
    const dev = await upsertDevice(user.id, deviceId, req.body?.device_name || result.request.device_name, clientIp(req));

    const section = sectionOf(app) || 'shop';
    const access = await tokens.issue({
      kind: 'access', subjectId: user.id, deviceId: dev?.id || null,
      ttlMs: config.tokens.accessTtlMs, app: section, appVersion: versionOf(req),
    });
    const refresh = await tokens.issue({
      kind: 'refresh', subjectId: user.id, deviceId: dev?.id || null,
      ttlMs: config.tokens.refreshTtlMs, app: section, appVersion: versionOf(req),
    });
    await query('UPDATE users SET last_login_at=$2 WHERE id=$1', [user.id, now()]).catch(() => {});

    await audit.log({
      actorType: 'user', userId: user.id, action: 'auth.login', ip: clientIp(req),
      detail: { method: 'email_code', app, device: deviceId || null },
    }).catch(() => {});

    res.json({
      ok: true,
      access_token: access.token,
      access_expires_in: Math.round(config.tokens.accessTtlMs / 1000),
      refresh_token: refresh.token,
      refresh_expires_in: Math.round(config.tokens.refreshTtlMs / 1000),
      //  نام‌های امروزِ برنامه‌ها هم می‌آیند تا نسخه‌های نصب‌شده نشکنند
      accessToken: access.token,
      accessExpiresAt: access.expiresAt,
      refreshToken: refresh.token,
      refreshExpiresAt: refresh.expiresAt,
      user: { id: user.id, email: user.email, name: user.name || '', app, created },
      subscription: await subscriptionOf(app, user.id),
      server_time: now(),
    });
  } catch (err) { next(err); }
});

module.exports = router;
