'use strict';
/**
 * 🔐 دسترسیِ پشتیبانی به دادهٔ خصوصیِ **یک** دکان — G1 ِ `docs/spec/ADMIN-CONTROL-PLANE.md` §۸.۱ (TEN-04، ACP-03؛ توانایی
 * ‎support-access.v1‎ ِ پلِ پنل، `console-support-access.js` ِ ریپوی server ۱.۵۰.۹۰). جدول: `admin_access_grants` (مهاجرتِ ۸۱۰).
 *
 * تصمیمِ ۲-۹ ِ صاحب‌کار (۱۴۰۵/۰۸/۲۰):
 *   ACP-D1 الف — مدت عددِ صحیحِ ۵ تا ۱۴۴۰ دقیقه (پیش‌فرض ۶۰)؛ بیشتر ⇒ ‎400 bad_minutes‎، هرگز بریدنِ بی‌صدا. تمدید = اجازهٔ تازه.
 *   ACP-D2 الف — «شکستنِ شیشه» (بی رضایتِ دکان) فقط مالکِ پلتفرم، با دلیلِ نوشتهٔ ۱۰ تا ۳۰۰ نویسه، احرازِ دوباره و خبرِ همان دم
 *                به صاحبِ دکان (`notify_owner: true` لازم است؛ `notified` همان است که واقعاً نشست).
 *
 * چرخه (زمان فقط از ساعتِ **سرور**):
 *   درخواست (consent=tenant) ⇒ pending ── صاحب در برنامه می‌پذیرد ⇒ active تا `minutes` ── گذشت ⇒ expired
 *                                     └─ رد ⇒ denied؛ ۲۴ ساعت بی پاسخ ⇒ expired
 *   شکستنِ شیشه ⇒ همان دم active (+ خبرِ صاحب)؛ لغو (مدیرِ ارشد؛ صاحب فقط اجازه‌ای که خودش داده) ⇒ revoked
 *
 * ⛔ اجازه مالِ **همان** درخواست‌کننده است (مدیر یا کلیدِ سرویس + «انجام‌دهنده»ٔ پنل)، نه هر مدیری؛ `useGrant` (همان
 *    `requireGrant(scope)` ِ طرح) فقط اجازهٔ فعالِ همان کس، همان دکان و همان دامنه را می‌پذیرد، `uses` را می‌شمارد و هر استفاده
 *    یک ردیفِ سابقه با شناسهٔ اجازه دارد. ⚠️ هنوز روی هیچ درِ محتوای خصوصی سوار نیست (درهای امروز همان سابقهٔ TEN-04 را دارند).
 * ⛔ پاسخ فقط فراداده: هرگز رمز، توکن یا محتوای دکان. رمزِ احرازِ دوباره در هیچ ردیف، سابقه یا هشِ کلیدِ یکتایی نمی‌نشیند.
 */
const config = require('../config');
const { one, many, tx, newId, now } = require('../db');
const v = require('./validate');
const audit = require('./audit');
const { SHOP } = require('./tenancy');
const { badRequest, forbidden, notFound, conflict, tooMany } = require('../middleware/errors');
const { clientIp } = require('../middleware/ratelimit');

const SCOPES = Object.freeze(['read_private', 'export', 'restore', 'view_as']);
const DEFAULT_MINUTES = 60;
const MIN_MINUTES = 5;
const MAX_MINUTES = 24 * 60;
const PENDING_TTL = 24 * 60 * 60 * 1000;
const PENDING_MAX = 5;                      //  درخواستِ بی‌پاسخِ هم‌زمانِ یک دکان (سیلِ خبر به صاحب نه)
const REASON = Object.freeze({ request: [3, 200], breakGlass: [10, 300], revoke: [3, 200] });
const REAUTH_FAIL_MAX = 5;
const REAUTH_FAIL_WINDOW = 15 * 60 * 1000;
const COLS = `id, scope, consent, status, reason, ticket_id, minutes, requested_by, granted_by, revoked_by, revoke_reason,
              created_at, decided_at, starts_at, expires_at, revoked_at, notified_at, uses`;

const N = (x) => (x === null || x === undefined ? null : Number(x));

/* ── سنجشِ ورودی ───────────────────────────────────────────────────── */

function minutesOf(x) {
  if (x === undefined || x === null || x === '') return DEFAULT_MINUTES;
  if (!Number.isInteger(x) || x < MIN_MINUTES || x > MAX_MINUTES) {
    throw badRequest(`مدت باید عددِ صحیحِ ${MIN_MINUTES} تا ${MAX_MINUTES} دقیقه باشد (سقفِ ۲۴ ساعت؛ تمدید = اجازهٔ تازه با دلیلِ تازه)`, 'bad_minutes');
  }
  return x;
}

function scopeOf(x) {
  if (x === undefined || x === null || x === '') return 'read_private';
  if (!SCOPES.includes(x)) throw badRequest('دامنهٔ دسترسی درست نیست', 'bad_scope');
  return x;
}

/** دلیلِ نوشته، بی بریدن: کوتاه‌تر یا بلندتر ⇒ ‎400 reason_required‎. */
function reasonOf(body, [min, max]) {
  const s = typeof body?.reason === 'string' ? v.stripControl(body.reason).trim() : '';
  if (s.length < min || s.length > max) {
    throw badRequest(`دلیل را بنویسید (${min} تا ${max} نویسه) — صاحبِ دکان هم آن را می‌بیند`, 'reason_required');
  }
  return s;
}

/** «انجام‌دهنده»: آدم، نه کارِ خودکارِ پنل (‎panel:system‎). */
function requester(req) {
  if (req.service && (!req.actor || !req.actor.id || req.actor.id === 'panel:system')) {
    throw forbidden('دسترسیِ پشتیبانی فقط به نامِ یک آدم داده می‌شود، نه کارِ خودکارِ پنل', 'human_actor_required');
  }
  return {
    adminId: String(req.admin.id),
    actorId: req.actor?.id ? String(req.actor.id) : '',
    name: String(req.actor?.name || req.actor?.id || req.admin.name || req.admin.username || '').slice(0, 120),
  };
}

async function shopOf(id) {
  const shopId = v.id(id, { field: 'شناسهٔ دکان' });
  const row = await one('SELECT id, name FROM shops WHERE id=$1', [shopId]);
  if (!row) throw notFound('دکان پیدا نشد', 'shop_not_found');
  return row;
}

/* ── شکلِ پاسخ ─────────────────────────────────────────────────────── */

/** حالِ امروزِ اجازه از ساعتِ سرور: pending/active که گذشته‌اند ⇒ expired. */
function statusOf(row, t = now()) {
  if (row.status === 'pending' && t >= Number(row.created_at) + PENDING_TTL) return 'expired';
  if (row.status === 'active' && t >= Number(row.expires_at)) return 'expired';
  return row.status;
}

function shape(row, t = now()) {
  const status = statusOf(row, t);
  return {
    id: row.id, scope: row.scope, consent: row.consent, status, reason: row.reason, ticketId: row.ticket_id || '',
    minutes: Number(row.minutes), requestedBy: row.requested_by || '', grantedBy: row.granted_by || '',
    revokedBy: row.revoked_by || '', revokeReason: row.revoke_reason || '',
    createdAt: N(row.created_at), decidedAt: N(row.decided_at), startsAt: N(row.starts_at), expiresAt: N(row.expires_at),
    revokedAt: N(row.revoked_at), notifiedAt: N(row.notified_at), uses: Number(row.uses) || 0,
    ...(row.status === 'pending' ? { requestExpiresAt: Number(row.created_at) + PENDING_TTL } : {}),
  };
}

const POLICY = Object.freeze({
  defaultMinutes: DEFAULT_MINUTES, minMinutes: MIN_MINUTES, maxMinutes: MAX_MINUTES, scopes: SCOPES,
  pendingHours: PENDING_TTL / 3_600_000,
});

async function list(shopId, { limit = 100 } = {}) {
  const rows = await many(
    `SELECT ${COLS} FROM admin_access_grants WHERE app=$1 AND tenant_id=$2 ORDER BY created_at DESC, id DESC LIMIT $3`,
    [SHOP.app, shopId, Math.min(200, Math.max(1, Number(limit) || 100))]
  );
  const t = now();
  return rows.map((r) => shape(r, t));
}

const grantOf = (c, shopId, gid) => c.query(
  `SELECT ${COLS}, admin_id, actor_id FROM admin_access_grants WHERE id=$1 AND app=$2 AND tenant_id=$3 FOR UPDATE`, [gid, SHOP.app, shopId]
).then((r) => r.rows[0] || null);

/* ── خبرِ صاحبِ دکان ───────────────────────────────────────────────── */

const when = (ms) => `${new Date(ms).toISOString().replace('T', ' ').slice(0, 16)} (UTC)`;

function noticeText(kind, g) {
  const who = g.requested_by || 'پشتیبانی';
  if (kind === 'break_glass') {
    return ['پشتیبانی بی اجازهٔ شما به دادهٔ دکان دسترسی گرفت',
      `«${who}» برای «${g.reason}» تا ${when(Number(g.expires_at))} به دادهٔ دکانِ شما دسترسی دارد (دامنه: ${g.scope}). این دسترسی بی رضایتِ شما و فقط از سوی مالکِ پلتفرم باز می‌شود و خودش بسته می‌شود؛ اگر از آن خبر ندارید، همین حالا به پشتیبانی بنویسید.`];
  }
  return ['پشتیبانی اجازهٔ دسترسی به دادهٔ دکان را می‌خواهد',
    `«${who}» برای «${g.reason}» ${g.minutes} دقیقه دسترسی به دادهٔ دکانِ شما (دامنه: ${g.scope}) می‌خواهد. تا شما در برنامه نپذیرید هیچ دسترسی‌ای باز نمی‌شود؛ درخواست ۲۴ ساعتِ دیگر خودش می‌گذرد.`];
}

/**
 * خبرِ داخلِ برنامه (صندوقِ `GET /api/me/notices`) برای هر صاحبِ فعالِ دکان + پوشِ بهترین‌کوشش.
 * ⇒ { inapp, push } — شکستش کار را برنمی‌گرداند (اجازه و سابقه‌اش پیش‌تر نشسته‌اند).
 */
async function notifyOwners(shopId, kind, g) {
  const out = { inapp: 0, push: 0 };
  try {
    const owners = await many(
      `SELECT u.id, u.name FROM shop_members m JOIN users u ON u.id = m.user_id
        WHERE m.shop_id=$1 AND m.role='owner' AND m.status='active'`, [shopId]);
    if (!owners.length) return out;
    const notices = require('./notices');
    const [title, body] = noticeText(kind, g);
    for (const o of owners) {
      const n = await notices.create({
        app: SHOP.app, audience: { kind: 'user', user_id: o.id }, channels: ['inapp', 'push'], title, body,
        templateKey: 'support_access', variables: { kind, grant: g.id },
      }, { createdBy: 'system', system: true });
      const t = now();
      await one(
        `INSERT INTO notice_deliveries (id, notice_id, app, user_id, tenant_id, channel, status, who, address, title, body, run, created_at, sent_at)
         VALUES ($1,$2,$3,$4,'','inapp','sent',$5,'',$6,$7,1,$8,$8) RETURNING id`,
        [newId('ndv'), n.id, SHOP.app, o.id, o.name || '', title, body, t]);
      await one(`UPDATE notices SET status='sent', sent_at=$2, runs=1, counts=$3::jsonb, updated_at=$2 WHERE id=$1 RETURNING id`,
        [n.id, t, JSON.stringify({ recipients: 1, sent: 1, error: 0, byChannel: { inapp: { sent: 1, error: 0 } } })]);
      out.inapp++;
    }
    const p = await require('./push').sendTo({ shopId, app: SHOP.app, onlyUserIds: owners.map((o) => o.id) },
      { title, body, data: { type: 'support_access', kind, grant: g.id } }).catch(() => null);
    out.push = Number(p?.sent) || 0;
  } catch (err) {
    console.warn('[support-access] خبر به صاحبِ دکان نرسید:', err.message);
  }
  return out;
}

/* ── احرازِ دوبارهٔ مالکِ پلتفرم (شکستنِ شیشه) ─────────────────────── */

/**
 * ⇒ 'login' | 'reauth' | 'password' | 'panel' | 'panel_reauth'
 *   • آدم با توکنِ خودش: نشستی که در پنجرهٔ `REAUTH_WINDOW_MIN` ساخته شده یا با ‎/admin/reauth‎ تازه شده، یا `password` ِ خودش
 *     همراهِ همین درخواست (پنج اشتباه در ربع ساعت ⇒ ‎429 reauth_locked‎؛ هر اشتباه یک سابقه).
 *   • پلِ پنل با کلیدِ سرویس: پنل پیش از پل «فقط مالکِ پنل» و رمزِ همان درخواست (با دوعاملی) را سنجیده است؛ اگر
 *     ‎X-Actor-Reauth-At‎ بیاید باید تازه باشد (کهنه یا آینده ⇒ رد).
 */
async function assertReauth(req) {
  const win = Math.max(60_000, Number(config.tokens.reauthWindowMs) || 600_000);
  const t = now();
  if (req.service) {
    const raw = String(req.headers['x-actor-reauth-at'] ?? '').trim();
    if (!raw) return 'panel';
    const at = Number(raw);
    if (!Number.isSafeInteger(at) || at > t + 60_000 || t - at > win) {
      throw forbidden('«شکستنِ شیشه» احرازِ دوبارهٔ همین چند دقیقه را می‌خواهد (رمزِ پنل)', 'reauth_required');
    }
    return 'panel_reauth';
  }
  const issued = Number(req.tokenRow?.issued_at) || 0;
  if (issued && t - issued <= win) return 'login';
  if (req.tokenRow?.token_hash && await require('./reauth').freshUntil(req.tokenRow.token_hash) > t) return 'reauth';
  const password = typeof req.body?.password === 'string' ? req.body.password : '';
  if (!password) throw forbidden('برای «شکستنِ شیشه» رمزِ خودتان را دوباره بزنید (یا دوباره وارد شوید)', 'reauth_required');
  const fails = await one(
    `SELECT COUNT(*)::int AS n FROM audit_logs WHERE action='admin.support_access_reauth_failed' AND user_id=$1 AND created_at > $2`,
    [req.admin.id, t - REAUTH_FAIL_WINDOW]);
  if (Number(fails?.n) >= REAUTH_FAIL_MAX) throw tooMany('رمز چند بار اشتباه بود؛ ربعِ ساعتِ دیگر دوباره امتحان کنید', 'reauth_locked');
  const row = await one('SELECT password_hash FROM admins WHERE id=$1', [req.admin.id]);
  if (!row || !(await require('./password').verifyPassword(password, row.password_hash))) {
    await audit.log({ actorType: 'admin', userId: req.admin.id, action: 'admin.support_access_reauth_failed', targetType: 'admin', targetId: req.admin.id });
    throw forbidden('رمز درست نیست', 'reauth_failed');
  }
  return 'password';
}

/**
 * ⛔ شکستنِ شیشه فقط مالکِ پلتفرم: آدم ⇒ `admins.is_owner` (`lib/admin-owner`)؛ پل ⇒ نقشِ مؤثرِ ارشد (مالکِ **پنل** را خودِ پنل
 * پیش از پل می‌سنجد — مدیرِ دومِ پنل ‎403 owner_only‎ پیش از رمز). ردِ مدیرِ دیگر یک سابقهٔ `admin.support_access_denied` دارد.
 */
async function assertPlatformOwner(req, shopId) {
  if (req.service) return;
  if (await require('./admin-owner').ownerId() === req.admin.id) return;
  await audit.log({ actorType: 'admin', userId: req.admin.id, shopId, action: 'admin.support_access_denied', targetType: 'shop', targetId: shopId,
    detail: { why: 'not_owner', what: 'break_glass' } });
  throw forbidden('«شکستنِ شیشه» (دسترسی بی رضایتِ دکان) فقط کارِ مالکِ پلتفرم است', 'owner_only');
}

/* ── کارهای مدیر ───────────────────────────────────────────────────── */

const logGrant = (c, req, shopId, action, detail) => audit.log({
  actorType: 'admin', userId: req.admin.id, shopId, action, targetType: 'shop', targetId: shopId, detail, ip: clientIp(req),
}, { client: c });

/** درخواست با رضایتِ دکان ⇒ { grant } (pending) */
async function request(req, shopId, input) {
  const who = requester(req);
  const t = now();
  const g = await tx(async (c) => {
    await c.query('SELECT pg_advisory_xact_lock(hashtext($1))', [`access-grants:${shopId}`]);
    const live = (await c.query(
      `SELECT COUNT(*)::int AS n FROM admin_access_grants WHERE app=$1 AND tenant_id=$2 AND status='pending' AND created_at > $3`,
      [SHOP.app, shopId, t - PENDING_TTL])).rows[0];
    if (Number(live?.n) >= PENDING_MAX) {
      throw conflict(`این دکان ${PENDING_MAX} درخواستِ بی‌پاسخ دارد؛ پیش از درخواستِ تازه یکی را لغو کنید`, 'too_many_pending');
    }
    const row = (await c.query(
      `INSERT INTO admin_access_grants (id, app, tenant_id, admin_id, actor_id, requested_by, scope, consent, status, reason, ticket_id,
                                        minutes, created_at)
       VALUES ($1,$2,$3,$4,$5,$6,$7,'tenant','pending',$8,$9,$10,$11) RETURNING ${COLS}`,
      [newId('agr'), SHOP.app, shopId, who.adminId, who.actorId, who.name, input.scope, input.reason, input.ticketId, input.minutes, t])).rows[0];
    await logGrant(c, req, shopId, 'admin.support_access_requested',
      { grant: row.id, scope: row.scope, minutes: row.minutes, reason: row.reason, ticket: row.ticket_id });
    return row;
  });
  await notifyOwners(shopId, 'request', g);
  return { grant: shape(g) };
}

/** شکستنِ شیشه ⇒ { grant, notified } (همان دم active) */
async function breakGlass(req, shopId, input, reauth) {
  const who = requester(req);
  const t = now();
  const g = await tx(async (c) => {
    const row = (await c.query(
      `INSERT INTO admin_access_grants (id, app, tenant_id, admin_id, actor_id, requested_by, scope, consent, status, reason, minutes,
                                        granted_by, created_at, decided_at, starts_at, expires_at)
       VALUES ($1,$2,$3,$4,$5,$6,$7,'break_glass','active',$8,$9,$6,$10,$10,$10,$11) RETURNING ${COLS}`,
      [newId('agr'), SHOP.app, shopId, who.adminId, who.actorId, who.name, input.scope, input.reason, input.minutes, t,
        t + input.minutes * 60_000])).rows[0];
    await logGrant(c, req, shopId, 'admin.support_access_break_glass',
      { grant: row.id, scope: row.scope, minutes: row.minutes, reason: row.reason, reauth });
    return row;
  });
  const sent = await notifyOwners(shopId, 'break_glass', g);
  const notified = sent.inapp > 0;
  let row = g;
  if (notified) {
    row = await one(`UPDATE admin_access_grants SET notified_at=$2 WHERE id=$1 RETURNING ${COLS}`, [g.id, now()]) || g;
  }
  await audit.log({ actorType: 'admin', userId: req.admin.id, shopId, action: 'admin.support_access_owner_notified', targetType: 'shop',
    targetId: shopId, detail: { grant: g.id, notified, inapp: sent.inapp, push: sent.push } });
  return { grant: shape(row), notified };
}

/** لغو از سوی مدیرِ ارشد (هر اجازهٔ زندهٔ همین دکان). گذشته/ردشده/لغوشده ⇒ همان اجازه با `unchanged`. */
async function revoke(req, shopId, gid, reason) {
  const who = requester(req);
  return tx(async (c) => {
    const row = await grantOf(c, shopId, gid);
    if (!row) throw notFound('اجازه پیدا نشد', 'grant_not_found');
    const t = now();
    if (!['pending', 'active'].includes(statusOf(row, t))) return { grant: shape(row, t), unchanged: true };
    const out = (await c.query(
      `UPDATE admin_access_grants SET status='revoked', revoked_at=$2, revoked_by=$3, revoke_reason=$4 WHERE id=$1 RETURNING ${COLS}`,
      [gid, t, who.name, reason])).rows[0];
    await logGrant(c, req, shopId, 'admin.support_access_revoked', { grant: gid, reason, was: row.status });
    return { grant: shape(out, t), unchanged: false };
  });
}

/**
 * `requireGrant(scope)` ِ طرح: اجازهٔ فعالِ **همین** درخواست‌کننده روی همین دکان و دامنه ⇒ شمارِ استفاده + سابقهٔ
 * `admin.support_access_used` با شناسهٔ اجازه؛ وگرنه ‎403 grant_required‎ (گذشته، لغوشده، دکانِ دیگر، مدیرِ دیگر یا دامنهٔ دیگر).
 */
async function useGrant(req, shopId, scope, { what = '' } = {}) {
  const who = requester(req);
  const t = now();
  return tx(async (c) => {
    const row = (await c.query(
      `SELECT id FROM admin_access_grants
        WHERE app=$1 AND tenant_id=$2 AND admin_id=$3 AND actor_id=$4 AND scope=$5 AND status='active'
          AND starts_at <= $6 AND expires_at > $6
        ORDER BY expires_at DESC LIMIT 1 FOR UPDATE`,
      [SHOP.app, String(shopId), who.adminId, who.actorId, scope, t])).rows[0];
    if (!row) throw forbidden('برای این کار اجازهٔ زندهٔ دسترسیِ پشتیبانی به همین دکان لازم است', 'grant_required');
    await c.query('UPDATE admin_access_grants SET uses = uses + 1 WHERE id=$1', [row.id]);
    await logGrant(c, req, String(shopId), 'admin.support_access_used', { grant: row.id, scope, what: String(what).slice(0, 120) });
    return row.id;
  });
}

function requireGrant(scope) {
  if (!SCOPES.includes(scope)) throw new Error(`requireGrant: دامنهٔ ناشناختهٔ ${scope}`);
  return (req, res, next) => {
    useGrant(req, req.params.id, scope, { what: `${req.method} ${req.baseUrl || ''}${req.path || ''}` })
      .then((id) => { req.accessGrant = id; next(); }, next);
  };
}

/* ── کارهای صاحبِ دکان (برنامه) ────────────────────────────────────── */

const logOwner = (c, req, action, detail) => audit.log({
  shopId: req.shopId, userId: req.user.id, action, targetType: 'access_grant', targetId: detail.grant, detail, ip: clientIp(req),
}, { client: c });

/** پذیرفتن / رد کردنِ درخواستِ بی‌پاسخ؛ لغوِ اجازه‌ای که خودِ دکان داده. ⇒ { grant } */
async function ownerDecide(req, gid, action) {
  return tx(async (c) => {
    const row = await grantOf(c, req.shopId, gid);
    if (!row) throw notFound('اجازه پیدا نشد', 'grant_not_found');
    const t = now();
    const status = statusOf(row, t);
    if (action === 'revoke') {
      if (row.consent !== 'tenant') {
        throw conflict('این دسترسی بی رضایتِ دکان و با دلیلِ نوشتهٔ مالکِ پلتفرم باز شده و سرِ وقتش خودش بسته می‌شود؛ اگر از آن خبر ندارید به پشتیبانی بنویسید', 'break_glass_owner');
      }
      if (!['pending', 'active'].includes(status)) return { grant: shape(row, t), unchanged: true };
      const out = (await c.query(
        `UPDATE admin_access_grants SET status='revoked', revoked_at=$2, revoked_by=$3, revoke_reason='owner' WHERE id=$1 RETURNING ${COLS}`,
        [gid, t, `owner:${req.user.id}`])).rows[0];
      await logOwner(c, req, 'shop.support_access_revoked', { grant: gid, was: row.status });
      return { grant: shape(out, t), unchanged: false };
    }
    if (status === 'expired') throw conflict('این درخواست گذشته است', 'grant_expired');
    if (status !== 'pending') throw conflict('این درخواست دیگر در انتظارِ پاسخ نیست', 'grant_not_pending');
    const out = action === 'accept'
      ? (await c.query(
        `UPDATE admin_access_grants SET status='active', granted_by=$2, decided_at=$3, starts_at=$3, expires_at=$4
          WHERE id=$1 AND status='pending' RETURNING ${COLS}`, [gid, `owner:${req.user.id}`, t, t + Number(row.minutes) * 60_000])).rows[0]
      : (await c.query(
        `UPDATE admin_access_grants SET status='denied', granted_by=$2, decided_at=$3 WHERE id=$1 AND status='pending' RETURNING ${COLS}`,
        [gid, `owner:${req.user.id}`, t])).rows[0];
    await logOwner(c, req, action === 'accept' ? 'shop.support_access_accepted' : 'shop.support_access_denied',
      { grant: gid, scope: out.scope, minutes: out.minutes, consent: out.consent });
    return { grant: shape(out, t), unchanged: false };
  });
}

module.exports = {
  SCOPES, DEFAULT_MINUTES, MIN_MINUTES, MAX_MINUTES, PENDING_TTL, PENDING_MAX, REASON, POLICY,
  minutesOf, scopeOf, reasonOf, requester, shopOf, statusOf, shape, list,
  assertReauth, assertPlatformOwner, request, breakGlass, revoke, useGrant, requireGrant, ownerDecide, notifyOwners,
};
