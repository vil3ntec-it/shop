'use strict';
/**
 * مدیرانِ سکو — ساختن، فهرست، نقش/وضعیت و نشست‌ها (زیرِ ‎/api/admin/admins‎؛ سوار در `routes/admin.js` پشتِ `requireAdmin`).
 *
 * از `routes/admin.js` جدا شد (کنسولِ یکپارچه، ۱۴۰۵/۰۸/۱۰) تا آن فایل زیرِ سقفِ ۸۰۰ خطِ `tools/quality` بماند؛ رفتار،
 * مسیرها و ترتیبِ سوار شدن همان است. کلیدِ سرویس هرگز به این‌جا نمی‌رسد (`SERVICE_DENY` ِ `lib/admin-service.js`).
 */
const express = require('express');
const { query, one, many, newId, now } = require('../db');
const v = require('../lib/validate');
const pw = require('../lib/password');
const tokens = require('../lib/tokens');
const audit = require('../lib/audit');
const { requireSuperAdmin } = require('../middleware/auth');
const { reasonOf, requireReason } = require('../lib/admin-access');
const { ROLES } = require('../lib/admin-roles');
const { badRequest, notFound, forbidden } = require('../middleware/errors');

const router = express.Router();

router.post('/', requireSuperAdmin, async (req, res, next) => {
  const username = v.text(req.body?.username, { max: 40, required: true, field: 'نام کاربری' }).toLowerCase();
  const password = typeof req.body?.password === 'string' ? req.body.password : '';
  const weak = pw.checkStrength(password);
  if (weak) return next(badRequest(weak, 'weak_password'));
  const exists = await one('SELECT 1 FROM admins WHERE username=$1', [username]);
  if (exists) return next(badRequest('این نام کاربری گرفته شده است'));
  const row = await one(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,$2,$3,$4,$5,'active',$6) RETURNING id, username, name, role`,
    //  ⛔ کمینهٔ دسترسی (پوستهٔ کنسول، ۴.۳): نقشِ نگفته «فقط‌خواندن» است، نه «مدیر» — پیش‌فرضِ ناامن نه
    [newId('adm'), username, v.text(req.body?.name, { max: 60 }), await pw.hashPassword(password),
      v.oneOf(req.body?.role, ROLES, { field: 'نقش', def: 'viewer' }), now()]
  );
  const reason = reasonOf(req);
  await audit.log({ actorType: 'admin', userId: req.admin.id, action: 'admin.admin_created', targetType: 'admin', targetId: row.id, detail: { username, role: row.role, ...(reason ? { reason } : {}) } });
  res.status(201).json({ admin: row });
});

router.get('/', requireSuperAdmin, async (req, res) => {
  await require('../lib/admin-owner').ownerId();
  res.json({ admins: await many('SELECT id, username, name, role, status, is_owner, created_at FROM admins ORDER BY created_at') });
});

/**
 *  غیرفعال کردن یا عوض کردنِ نقشِ یک مدیر — فقط مدیرِ ارشد.
 *  ⚠️ نه خودش (قفل شدنِ ناخواسته) و نه آخرین مدیرِ ارشدِ فعال (پنلِ بی‌صاحب).
 *  غیرفعال ⇒ همهٔ نشست‌هایش همان دم بسته می‌شود.
 */
router.patch('/:id', requireSuperAdmin, async (req, res, next) => {
  const id = String(req.params.id);
  if (id === req.admin.id) return next(badRequest('نقش یا وضعیتِ خودتان را نمی‌توانید عوض کنید', 'admin_self'));
  const target = await one('SELECT id, role, status FROM admins WHERE id=$1', [id]);
  if (!target) return next(notFound('مدیر پیدا نشد'));
  /*
   *  ⛔ فاز ۳ (یافتهٔ S12): **مالکِ اصلی** را هیچ مدیرِ دیگری — حتی ارشد — غیرفعال یا تنزل نمی‌دهد؛
   *  وگرنه ارشدِ دوم صاحبِ سامانه (و پلِ پنلش) را بیرون می‌گذاشت. راهِ بازیابی فقط از پوستهٔ سرور
   *  (`lib/admin-owner.js`). خودِ مالک هم نقشِ خودش را عوض نمی‌کند (`admin_self` ِ بالا).
   */
  if (id === await require('../lib/admin-owner').ownerId()) {
    return next(forbidden('مالکِ اصلیِ سامانه را هیچ مدیرِ دیگری غیرفعال یا تنزل نمی‌دهد', 'owner_protected'));
  }
  const status = req.body?.status === undefined ? target.status : v.oneOf(req.body.status, ['active', 'disabled'], { field: 'وضعیت' });
  const role = req.body?.role === undefined ? target.role : v.oneOf(req.body.role, ROLES, { field: 'نقش' });
  const losesSuper = target.role === 'superadmin' && target.status === 'active' && (role !== 'superadmin' || status !== 'active');
  if (losesSuper) {
    const left = await one(`SELECT COUNT(*)::int AS n FROM admins WHERE role='superadmin' AND status='active' AND id<>$1`, [id]);
    if (!left || left.n < 1) return next(badRequest('آخرین مدیرِ ارشدِ فعال نمی‌تواند کنار برود', 'last_superadmin'));
  }
  const row = await one('UPDATE admins SET status=$2, role=$3 WHERE id=$1 RETURNING id, username, name, role, status', [id, status, role]);
  if (status === 'disabled') await tokens.revokeAllForSubject(id);
  //  پوستهٔ کنسول (۴.۳): «پیشین ⇒ تازه» و دلیل در سابقه — فیلدهای پیشینِ `status`/`role` همان‌جا ماندند
  const reason = reasonOf(req);
  await audit.log({
    actorType: 'admin', userId: req.admin.id, action: 'admin.admin_updated', targetType: 'admin', targetId: id,
    detail: { status, role, from: { status: target.status, role: target.role }, ...(reason ? { reason } : {}) },
  });
  res.json({ admin: row });
});

/*
 *  ── نشست‌های یک مدیر (پوستهٔ کنسول، ماژولِ ۴.۳ ِ پنل؛ توانایی `admin-sessions.v1`) ──
 *
 *  فقط ارشد (و کلیدِ سرویس هرگز — `SERVICE_DENY` همهٔ ‎/admin/admins‎ را می‌بندد). شناسهٔ نشست ۱۶ نویسهٔ
 *  آغازِ SHA-256 ِ توکن است: خودِ توکن هرگز این‌جا نیست و از هش برنمی‌گردد.
 *  ⛔ نشست‌های **مالکِ اصلی** را فقط خودش می‌بندد — وگرنه ارشدِ دوم بارها بیرونش می‌انداخت (همان S12).
 *  ⛔ دلیل اجباری و ردیفِ سابقه با شمار؛ نشستِ پیدانشده ⇒ ۴۰۴، نه «انجام شد» ِ دروغ.
 */
const SESSION_ID_RE = /^[0-9a-f]{16}$/;

router.get('/:id/sessions', requireSuperAdmin, async (req, res, next) => {
  const id = String(req.params.id);
  const target = await one('SELECT id FROM admins WHERE id=$1', [id]);
  if (!target) return next(notFound('مدیر پیدا نشد'));
  const bearer = (req.headers.authorization || '').replace(/^Bearer\s+/i, '').trim();
  const mine = bearer ? tokens.hashToken(bearer) : '';
  const rows = await many(
    `SELECT token_hash, issued_at, expires_at FROM tokens
      WHERE kind='admin' AND subject_id=$1 AND revoked_at IS NULL AND expires_at>$2
      ORDER BY issued_at DESC LIMIT 100`, [id, now()]);
  res.json({
    sessions: rows.map((r) => ({
      id: String(r.token_hash).slice(0, 16), issuedAt: Number(r.issued_at), expiresAt: Number(r.expires_at),
      current: Boolean(mine) && r.token_hash === mine,
    })),
    serverTime: now(),
  });
});

router.post('/:id/sessions/revoke', requireSuperAdmin, async (req, res, next) => {
  const id = String(req.params.id);
  const reason = requireReason(req);
  const target = await one('SELECT id, username FROM admins WHERE id=$1', [id]);
  if (!target) return next(notFound('مدیر پیدا نشد'));
  if (id !== req.admin.id && id === await require('../lib/admin-owner').ownerId()) {
    return next(forbidden('نشست‌های مالکِ اصلی را فقط خودش می‌بندد', 'owner_protected'));
  }
  const sessionId = req.body?.sessionId === undefined || req.body?.sessionId === '' ? '' : String(req.body.sessionId);
  if (sessionId && !SESSION_ID_RE.test(sessionId)) return next(badRequest('شناسهٔ نشست درست نیست', 'bad_session_id'));
  let revoked;
  if (sessionId) {
    const r = await query(
      `UPDATE tokens SET revoked_at=$1 WHERE kind='admin' AND subject_id=$2 AND revoked_at IS NULL AND left(token_hash, 16)=$3`,
      [now(), id, sessionId]);
    revoked = r.rowCount;
    if (!revoked) return next(notFound('این نشست باز نیست یا مالِ این مدیر نیست', 'session_not_found'));
  } else {
    revoked = await tokens.revokeAllForSubject(id, 'admin');
  }
  await audit.log({
    actorType: 'admin', userId: req.admin.id, action: 'admin.admin_sessions_revoked', targetType: 'admin', targetId: id,
    detail: { session: sessionId || 'all', revoked, reason },
  });
  res.json({ ok: true, revoked, serverTime: now() });
});

module.exports = router;
