'use strict';
/**
 * پنل مدیریت — کاملاً جدا از کاربران عادی.
 *
 * نشست مدیر نوع توکن جداگانه دارد (kind = admin)، پس هیچ کاربر عادی —
 * حتی با توکن معتبر خودش — نمی‌تواند به این مسیرها برسد.
 */
const express = require('express');
const { query, one, many, newId, now } = require('../db');
const config = require('../config');
const v = require('../lib/validate');
const pw = require('../lib/password');
const tokens = require('../lib/tokens');
const audit = require('../lib/audit');
const subs = require('../lib/subscriptions');
const plans = require('../lib/plans');
const smsSettings = require('../lib/sms-settings');
const otp = require('../lib/otp');
const { entitlementOf } = require('../lib/entitlement');
const { FEATURES, sanitizeFeatures } = require('../lib/features');
const { requireAdmin, requireSuperAdmin } = require('../middleware/auth');
const { rateLimit, clientIp } = require('../middleware/ratelimit');
const { badRequest, unauthorized, notFound, forbidden, tooMany } = require('../middleware/errors');

const router = express.Router();
const adminLimit = rateLimit({ max: config.rateLimit.authMax, keyPrefix: 'admin-auth' });

// ---------- ورود مدیر ----------
router.post('/login', adminLimit, async (req, res, next) => {
  const username = v.text(req.body?.username, { max: 60, required: true, field: 'نام کاربری' }).toLowerCase();
  const password = typeof req.body?.password === 'string' ? req.body.password : '';

  const since = now() - config.rateLimit.lockoutMs;
  const fails = await one(
    `SELECT COUNT(*)::int AS n FROM login_attempts WHERE scope='admin' AND identifier=$1 AND ok=false AND created_at>$2`,
    [username, since]
  );
  if (fails.n >= config.rateLimit.lockoutTries) {
    return next(tooMany('تلاش ناموفق زیاد بود، بعداً امتحان کنید', 'locked_out'));
  }

  const admin = await one('SELECT * FROM admins WHERE username=$1', [username]);
  const ok = admin && admin.status === 'active' && await pw.verifyPassword(password, admin.password_hash);
  await query(
    `INSERT INTO login_attempts (scope, identifier, ip, ok, created_at) VALUES ('admin',$1,$2,$3,$4)`,
    [username, clientIp(req), !!ok, now()]
  );
  if (!ok) return next(unauthorized('نام کاربری یا رمز درست نیست', 'bad_credentials'));

  const t = await tokens.issue({ kind: 'admin', subjectId: admin.id, ttlMs: config.tokens.adminTtlMs });
  await query('UPDATE admins SET last_login_at=$2 WHERE id=$1', [admin.id, now()]);
  await audit.log({ actorType: 'admin', userId: admin.id, action: 'admin.login', ip: clientIp(req) });
  res.json({
    token: t.token, expiresAt: t.expiresAt,
    admin: { id: admin.id, username: admin.username, name: admin.name, role: admin.role },
  });
});

router.use(requireAdmin);

router.post('/logout', async (req, res) => {
  const token = (req.headers.authorization || '').replace(/^Bearer\s+/i, '').trim();
  if (token) await tokens.revoke(token, 'admin');
  res.json({ ok: true });
});

router.get('/me', (req, res) => {
  res.json({
    admin: { id: req.admin.id, username: req.admin.username, name: req.admin.name, role: req.admin.role },
    serverTime: now(),
  });
});

router.get('/features', (req, res) => res.json({ features: FEATURES }));

// ---------- آمار ----------
router.get('/stats', async (req, res) => {
  await subs.expireDue();
  const [users, shops, members, active, expired, pending] = await Promise.all([
    one('SELECT COUNT(*)::int n FROM users'),
    one('SELECT COUNT(*)::int n FROM shops'),
    one(`SELECT COUNT(*)::int n FROM shop_members WHERE status='active'`),
    one(`SELECT COUNT(*)::int n FROM subscriptions WHERE status='active'`),
    one(`SELECT COUNT(*)::int n FROM subscriptions WHERE status='expired'`),
    one(`SELECT COUNT(*)::int n FROM purchase_requests WHERE status='pending'`),
  ]);
  res.json({
    users: users.n, shops: shops.n, members: members.n,
    activeSubscriptions: active.n, expiredSubscriptions: expired.n,
    pendingRequests: pending.n, serverTime: now(),
  });
});

// ---------- کاربران ----------
router.get('/users', async (req, res) => {
  const limit = v.integer(req.query?.limit, { min: 1, max: 200, def: 50 });
  const offset = v.integer(req.query?.offset, { min: 0, max: 1e6, def: 0 });
  const q = v.text(req.query?.q, { max: 60 });
  const like = `%${q.toLowerCase()}%`;
  const rows = await many(
    `SELECT u.id, u.name, u.email, u.phone, u.status, u.created_at, u.last_login_at,
            m.shop_id, m.role, s.name AS shop_name
       FROM users u
       LEFT JOIN shop_members m ON m.user_id = u.id AND m.status='active'
       LEFT JOIN shops s ON s.id = m.shop_id
      WHERE ($1 = '' OR lower(u.name) LIKE $2 OR lower(coalesce(u.email,'')) LIKE $2 OR coalesce(u.phone,'') LIKE $2)
      ORDER BY u.created_at DESC LIMIT $3 OFFSET $4`,
    [q, like, limit, offset]
  );
  const total = await one(
    `SELECT COUNT(*)::int n FROM users u
      WHERE ($1 = '' OR lower(u.name) LIKE $2 OR lower(coalesce(u.email,'')) LIKE $2 OR coalesce(u.phone,'') LIKE $2)`,
    [q, like]
  );
  res.json({ users: rows, total: total.n, limit, offset });
});

router.get('/users/:id', async (req, res, next) => {
  const id = v.id(req.params.id);
  const user = await one('SELECT * FROM users WHERE id=$1', [id]);
  if (!user) return next(notFound('کاربر پیدا نشد'));
  const memberships = await many(
    `SELECT m.*, s.name AS shop_name FROM shop_members m JOIN shops s ON s.id=m.shop_id WHERE m.user_id=$1`,
    [id]
  );
  const devices = await many('SELECT * FROM devices WHERE user_id=$1 ORDER BY last_seen_at DESC NULLS LAST', [id]);
  res.json({
    user: {
      id: user.id, name: user.name, email: user.email, phone: user.phone,
      status: user.status, createdAt: Number(user.created_at),
      lastLoginAt: user.last_login_at ? Number(user.last_login_at) : null,
    },
    memberships, devices,
  });
});

router.post('/users/:id/status', async (req, res, next) => {
  const id = v.id(req.params.id);
  const status = v.oneOf(req.body?.status, ['active', 'disabled'], { field: 'وضعیت' });
  const user = await one('UPDATE users SET status=$2, updated_at=$3 WHERE id=$1 RETURNING id, status', [id, status, now()]);
  if (!user) return next(notFound('کاربر پیدا نشد'));
  if (status === 'disabled') await tokens.revokeAllForSubject(id);
  await audit.log({ actorType: 'admin', userId: req.admin.id, action: 'admin.user_status', targetType: 'user', targetId: id, detail: { status } });
  res.json({ user });
});

// ---------- دکان‌ها ----------
router.get('/shops', async (req, res) => {
  await subs.expireDue();
  const limit = v.integer(req.query?.limit, { min: 1, max: 200, def: 50 });
  const offset = v.integer(req.query?.offset, { min: 0, max: 1e6, def: 0 });
  const q = v.text(req.query?.q, { max: 60 });
  const like = `%${q.toLowerCase()}%`;
  const rows = await many(
    `SELECT s.id, s.name, s.status, s.created_at, s.owner_user_id,
            u.name AS owner_name, u.phone AS owner_phone, u.email AS owner_email,
            (SELECT COUNT(*)::int FROM shop_members m WHERE m.shop_id=s.id AND m.status='active') AS members,
            sub.id AS subscription_id, sub.plan, sub.status AS sub_status, sub.starts_at, sub.ends_at
       FROM shops s
       JOIN users u ON u.id = s.owner_user_id
       LEFT JOIN LATERAL (
         SELECT * FROM subscriptions x WHERE x.shop_id = s.id
          ORDER BY (x.status IN ('active','suspended','pending')) DESC, x.created_at DESC LIMIT 1
       ) sub ON true
      WHERE ($1 = '' OR lower(s.name) LIKE $2 OR lower(u.name) LIKE $2 OR coalesce(u.phone,'') LIKE $2)
      ORDER BY s.created_at DESC LIMIT $3 OFFSET $4`,
    [q, like, limit, offset]
  );
  const total = await one('SELECT COUNT(*)::int n FROM shops');
  // دکانی که هنوز اشتراک نخریده ولی در دوره‌ی آزمایشی است، «آزمایشی» نشان داده شود
  const trialDays = Number(await plans.getConfig('trial_days', '14')) || 0;
  const t = now();
  const shops = rows.map(r => {
    if (r.sub_status) return r;
    const trialEnds = Number(r.created_at) + trialDays * 24 * 3600 * 1000;
    return trialEnds > t ? { ...r, sub_status: 'trial', ends_at: trialEnds } : r;
  });
  res.json({ shops, total: total.n, limit, offset });
});

router.get('/shops/:id', async (req, res, next) => {
  const id = v.id(req.params.id);
  const shop = await one('SELECT * FROM shops WHERE id=$1', [id]);
  if (!shop) return next(notFound('دکان پیدا نشد'));
  const members = await many(
    `SELECT m.id, m.role, m.status, m.created_at, u.id AS user_id, u.name, u.phone, u.email
       FROM shop_members m JOIN users u ON u.id=m.user_id WHERE m.shop_id=$1 ORDER BY m.created_at`,
    [id]
  );
  const ent = await entitlementOf(id);
  const history = await subs.historyOf(id);
  const counts = {};
  for (const [col, table] of Object.entries(require('../lib/sync').TABLES)) {
    const r = await one(`SELECT COUNT(*)::int n FROM ${table} WHERE shop_id=$1 AND deleted=false`, [id]);
    counts[col] = r.n;
  }
  res.json({
    shop: { id: shop.id, name: shop.name, status: shop.status, createdAt: Number(shop.created_at), ownerUserId: shop.owner_user_id },
    members, entitlement: ent, subscriptions: history, counts,
  });
});

/**
 * دفتر تغییرهای اشتراک یک دکان.
 *
 * جدول subscriptions فقط «حالا» را نشان می‌دهد؛ این می‌گوید چه کسی کِی
 * چه تمدیدی داد و تاریخ پایان از چه به چه رسید.
 */
router.get('/shops/:id/history', async (req, res) => {
  const id = v.id(req.params.id);
  const limit = v.integer(req.query?.limit, { min: 1, max: 200, def: 50 });
  res.json({ history: await subs.changeLog(id, limit) });
});

// ---------- اشتراک‌ها ----------
router.get('/subscriptions', async (req, res) => {
  await subs.expireDue();
  const limit = v.integer(req.query?.limit, { min: 1, max: 200, def: 50 });
  const status = v.text(req.query?.status, { max: 20 });
  const rows = await many(
    `SELECT sub.*, s.name AS shop_name, u.name AS owner_name, u.phone AS owner_phone
       FROM subscriptions sub
       JOIN shops s ON s.id = sub.shop_id
       JOIN users u ON u.id = s.owner_user_id
      WHERE ($1 = '' OR sub.status = $1)
      ORDER BY sub.updated_at DESC LIMIT $2`,
    [status, limit]
  );
  res.json({ subscriptions: rows.map(r => ({ ...r, state: subs.stateOf(r) })) });
});

/** صدور یا تمدید اشتراک یک دکان. */
router.post('/subscriptions', async (req, res, next) => {
  const shopId = v.id(req.body?.shopId, { field: 'شناسه دکان' });
  const plan = v.text(req.body?.plan, { max: 20 }) || 'custom';
  const days = req.body?.days === undefined || req.body?.days === null || req.body?.days === ''
    ? null : v.integer(req.body.days, { field: 'روزها', min: 1, max: 3650 });
  const features = sanitizeFeatures(req.body?.features);
  const maxDevices = v.integer(req.body?.maxDevices, { field: 'تعداد دستگاه', min: 1, max: 100, def: 10 });
  const graceDays = v.integer(req.body?.graceDays, { field: 'مهلت', min: 0, max: 90, def: 0 });
  const note = v.text(req.body?.note, { max: 300 });

  const sub = await subs.grant(shopId, {
    plan, days, features, maxDevices, graceDays, note, createdBy: req.admin.id,
    startsAt: v.timestamp(req.body?.startsAt), endsAt: v.timestamp(req.body?.endsAt),
  });
  await audit.log({ shopId, actorType: 'admin', userId: req.admin.id, action: 'admin.subscription_granted', targetType: 'subscription', targetId: sub.id, detail: { plan, days } });
  res.status(201).json({ subscription: sub, state: subs.stateOf(sub) });
});

/** ویرایش اشتراک (تاریخ پایان، قابلیت‌ها، یادداشت). */
router.put('/subscriptions/:id', async (req, res, next) => {
  const id = v.id(req.params.id);
  const current = await one('SELECT * FROM subscriptions WHERE id=$1', [id]);
  if (!current) return next(notFound('اشتراک پیدا نشد'));

  const endsAt = v.timestamp(req.body?.endsAt, { def: Number(current.ends_at) });
  const startsAt = v.timestamp(req.body?.startsAt, { def: Number(current.starts_at) });
  if (endsAt <= startsAt) return next(badRequest('تاریخ پایان باید بعد از شروع باشد'));
  const features = req.body?.features ? sanitizeFeatures(req.body.features) : current.features;
  const plan = v.text(req.body?.plan, { max: 20 }) || current.plan;
  const note = req.body?.note === undefined ? current.note : v.text(req.body.note, { max: 300 });
  const graceDays = v.integer(req.body?.graceDays, { min: 0, max: 90, def: current.grace_days });
  const maxDevices = v.integer(req.body?.maxDevices, { min: 1, max: 100, def: current.max_devices });

  const row = await one(
    `UPDATE subscriptions SET plan=$2, starts_at=$3, ends_at=$4, features=$5::jsonb,
            grace_days=$6, max_devices=$7, note=$8, updated_at=$9 WHERE id=$1 RETURNING *`,
    [id, plan, startsAt, endsAt, JSON.stringify(features), graceDays, maxDevices, note, now()]
  );
  await audit.log({ shopId: row.shop_id, actorType: 'admin', userId: req.admin.id, action: 'admin.subscription_updated', targetType: 'subscription', targetId: id });
  res.json({ subscription: row, state: subs.stateOf(row) });
});

router.post('/subscriptions/:id/status', async (req, res) => {
  const id = v.id(req.params.id);
  const status = v.oneOf(req.body?.status, ['active', 'suspended', 'cancelled', 'expired'], { field: 'وضعیت' });
  const row = await subs.setStatus(id, status, req.admin.id);
  await audit.log({ shopId: row.shop_id, actorType: 'admin', userId: req.admin.id, action: 'admin.subscription_status', targetType: 'subscription', targetId: id, detail: { status } });
  res.json({ subscription: row, state: subs.stateOf(row) });
});

// ---------- سرویس پیامک ----------
/**
 * تنظیمات سرویس پیامک، برای برنامه‌ی مدیریت.
 *
 * کلید سرویس هرگز کامل برنمی‌گردد — فقط چهار رقم آخرش. اگر کامل
 * برمی‌گشت، هر کسی که یک بار به آن گوشی دست پیدا می‌کرد کلید را داشت
 * و می‌توانست با اعتبار صاحب سامانه پیامک بفرستد.
 */
router.get('/sms', async (req, res) => {
  res.json({ sms: await smsSettings.masked() });
});

/**
 * ذخیره‌ی تنظیمات.
 *
 * `key` اگر خالی یا نیامده باشد، دست نمی‌خورد: برنامه‌ی مدیریت کلید را
 * نمی‌بیند، پس نباید بتواند ندانسته پاکش کند. برای پاک کردن عمدی،
 * `key` را با یک فاصله یا `-` بفرستید… نه: صریح‌تر، `clearKey: true`.
 */
router.put('/sms', async (req, res, next) => {
  const body = req.body || {};
  const patch = {};

  if (body.provider !== undefined) {
    patch.provider = v.oneOf(body.provider, ['log', 'sms', 'webhook', 'whatsapp'], { field: 'راه ارسال' });
  }
  if (body.method !== undefined) {
    patch.method = v.oneOf(String(body.method).toUpperCase(), ['POST', 'GET'], { field: 'روش' });
  }
  for (const name of ['url', 'sender', 'headers', 'body', 'template']) {
    if (body[name] !== undefined) patch[name] = v.text(body[name], { max: 2000, field: name });
  }

  //  کلید فقط وقتی عوض می‌شود که مدیر واقعاً چیزی نوشته باشد
  if (body.clearKey === true) patch.key = '';
  else if (typeof body.key === 'string' && body.key.trim()) patch.key = body.key.trim();

  //  JSONهای شکل درخواست پیش از ذخیره سنجیده می‌شوند، وگرنه خرابی‌اش
  //  وقتی معلوم می‌شد که کاربری منتظر کد مانده بود
  for (const name of ['headers', 'body']) {
    const value = patch[name];
    if (!value) continue;
    const isJsonish = value.trim().startsWith('{');
    if (name === 'headers' || isJsonish) {
      try { JSON.parse(value); } catch { return next(badRequest(`${name} یک JSON درست نیست`, 'bad_json')); }
    }
  }

  const saved = await smsSettings.save(patch);
  await audit.log({
    actorType: 'admin', userId: req.admin.id, action: 'admin.sms_settings',
    detail: { provider: saved.provider, url: saved.url, keyChanged: patch.key !== undefined },
  });
  res.json({ sms: await smsSettings.masked() });
});

/**
 * آزمایش واقعی: یک کد ساختگی به شماره‌ی خود مدیر.
 *
 * هیچ چیزی در otp_codes ثبت نمی‌شود و این کد جایی کار نمی‌کند — فقط
 * می‌سنجد که سرویس پیامک راه افتاده یا نه. پاسخ خام سرویس هم برمی‌گردد
 * تا اگر نرفت، دلیلش روی همان صفحه دیده شود.
 */
router.post('/sms/test', rateLimit({ max: 10, keyPrefix: 'admin-sms-test' }), async (req, res, next) => {
  const to = v.phone(req.body?.to, { required: true });
  const cfg = await smsSettings.current();
  const send = otp.senders[cfg.provider] || otp.senders.log;

  const code = String(Math.floor(100000 + Math.random() * 900000));
  const message = cfg.template ? cfg.template.replace(/\{code\}/g, code) : `کد آزمایشی توحید: ${code}`;

  try {
    const out = await send(to, code, message);
    await audit.log({ actorType: 'admin', userId: req.admin.id, action: 'admin.sms_test', detail: { to, via: cfg.provider } });
    res.json({ ok: true, via: out.via || cfg.provider, response: out.response || '' });
  } catch (err) {
    //  خطای سرویس، خطای سرور ما نیست: ۲۰۰ با ok:false تا برنامه‌ی
    //  مدیریت بتواند متنش را نشان دهد نه یک «خطای ۵۰۰».
    res.json({ ok: false, error: String(err.message || err).slice(0, 400) });
  }
});

// ---------- پلن‌ها و تنظیمات ----------
router.get('/plans', async (req, res) => {
  res.json({ plans: await plans.listPlans({ activeOnly: false }), config: await plans.allConfig() });
});

router.patch('/plans/:code', async (req, res, next) => {
  const code = v.text(req.params.code, { max: 20, required: true, field: 'کد پلن' });
  const p = await plans.getPlan(code);
  if (!p) return next(notFound('پلن پیدا نشد'));
  const row = await one(
    `UPDATE plans SET title=$2, amount=$3, unit=$4, price_afn=$5, negotiable=$6,
            badge=$7, sort_order=$8, active=$9, max_devices=$10, features=$11::jsonb, updated_at=$12
      WHERE code=$1 RETURNING *`,
    [code,
      v.text(req.body?.title, { max: 60 }) || p.title,
      req.body?.amount === undefined ? p.amount : v.integer(req.body.amount, { min: 1, max: 120 }),
      req.body?.unit === undefined ? p.unit : v.oneOf(req.body.unit, ['day', 'week', 'month', 'year'], { field: 'واحد' }),
      req.body?.price === undefined ? p.price_afn : v.integer(req.body.price, { min: 0, max: 1e7 }),
      v.bool(req.body?.negotiable, p.negotiable),
      req.body?.badge === undefined ? p.badge : v.text(req.body.badge, { max: 30 }),
      req.body?.sortOrder === undefined ? p.sort_order : v.integer(req.body.sortOrder, { min: 0, max: 999 }),
      v.bool(req.body?.active, p.active),
      req.body?.maxDevices === undefined ? p.max_devices : v.integer(req.body.maxDevices, { min: 1, max: 100 }),
      JSON.stringify(req.body?.features ? sanitizeFeatures(req.body.features) : p.features),
      now()]
  );
  res.json({ plan: row });
});

router.patch('/config', async (req, res) => {
  const allowed = ['trial_days', 'whatsapp_number', 'whatsapp_message', 'currency'];
  for (const key of allowed) {
    if (req.body?.[key] !== undefined) await plans.setConfig(key, v.text(req.body[key], { max: 300 }));
  }
  await audit.log({ actorType: 'admin', userId: req.admin.id, action: 'admin.config_updated' });
  res.json({ config: await plans.allConfig() });
});

// ---------- درخواست‌های خرید ----------
router.get('/purchase-requests', async (req, res) => {
  const status = v.text(req.query?.status, { max: 20 }) || 'pending';
  const rows = await many(
    `SELECT p.*, s.name AS shop_name, u.name AS user_name, u.phone
       FROM purchase_requests p
       JOIN shops s ON s.id=p.shop_id
       JOIN users u ON u.id=p.user_id
      WHERE p.status=$1 ORDER BY p.created_at DESC LIMIT 200`,
    [status]
  );
  res.json({ requests: rows });
});

router.post('/purchase-requests/:id/approve', async (req, res, next) => {
  const id = v.id(req.params.id);
  const reqRow = await one(`SELECT * FROM purchase_requests WHERE id=$1 AND status='pending'`, [id]);
  if (!reqRow) return next(notFound('درخواست پیدا نشد'));
  const days = req.body?.days ? v.integer(req.body.days, { min: 1, max: 3650 }) : null;
  const sub = await subs.grant(reqRow.shop_id, {
    plan: reqRow.plan_code, days, createdBy: req.admin.id, note: reqRow.note,
  });
  await query(`UPDATE purchase_requests SET status='approved', handled_at=$2, handled_by=$3 WHERE id=$1`,
    [id, now(), req.admin.id]);
  await audit.log({ shopId: reqRow.shop_id, actorType: 'admin', userId: req.admin.id, action: 'admin.request_approved', targetId: id });
  res.json({ subscription: sub, state: subs.stateOf(sub) });
});

router.post('/purchase-requests/:id/reject', async (req, res) => {
  const id = v.id(req.params.id);
  await query(`UPDATE purchase_requests SET status='rejected', handled_at=$2, handled_by=$3 WHERE id=$1 AND status='pending'`,
    [id, now(), req.admin.id]);
  res.json({ ok: true });
});

// ---------- سابقه ----------
router.get('/audit', async (req, res) => {
  const limit = v.integer(req.query?.limit, { min: 1, max: 500, def: 100 });
  const shopId = v.id(req.query?.shopId, { required: false });
  const rows = shopId
    ? await many('SELECT * FROM audit_logs WHERE shop_id=$1 ORDER BY created_at DESC LIMIT $2', [shopId, limit])
    : await many('SELECT * FROM audit_logs ORDER BY created_at DESC LIMIT $1', [limit]);
  res.json({ entries: rows });
});

// ---------- مدیران ----------
router.post('/admins', requireSuperAdmin, async (req, res, next) => {
  const username = v.text(req.body?.username, { max: 40, required: true, field: 'نام کاربری' }).toLowerCase();
  const password = typeof req.body?.password === 'string' ? req.body.password : '';
  const weak = pw.checkStrength(password);
  if (weak) return next(badRequest(weak, 'weak_password'));
  const exists = await one('SELECT 1 FROM admins WHERE username=$1', [username]);
  if (exists) return next(badRequest('این نام کاربری گرفته شده است'));
  const row = await one(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,$2,$3,$4,$5,'active',$6) RETURNING id, username, name, role`,
    [newId('adm'), username, v.text(req.body?.name, { max: 60 }), await pw.hashPassword(password),
      v.oneOf(req.body?.role, ['admin', 'superadmin'], { field: 'نقش', def: 'admin' }), now()]
  );
  res.status(201).json({ admin: row });
});

// ---------- پشتیبان‌گیری ----------
router.get('/backups', async (req, res) => {
  const backup = require('../lib/backup');
  res.json({ backups: await backup.list(), dir: config.backup.dir, enabled: config.backup.enabled });
});

router.post('/backups', requireSuperAdmin, async (req, res, next) => {
  const backup = require('../lib/backup');
  const out = await backup.run({ kind: v.oneOf(req.body?.kind, ['daily', 'weekly', 'monthly', 'manual'], { field: 'نوع', def: 'manual' }) });
  await audit.log({ actorType: 'admin', userId: req.admin.id, action: 'admin.backup_created', detail: { file: out.file } });
  res.status(201).json(out);
});

module.exports = router;
