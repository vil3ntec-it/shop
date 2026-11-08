'use strict';
/**
 * رازِ دستگاهِ شاگرد — اثباتِ «همان دستگاه» برای ورودِ دوباره با کدِ شاگرد (مهاجرتِ ۳۲۰).
 *
 * ⛔ شناسهٔ دستگاه (`device.uid`، `X-Device`) **راز نیست**: همکار در Pull و هر درخواستِ همگام‌سازی آن را می‌بیند.
 * پس `/auth/staff` عضوِ موجود را دیگر با «کد + شناسهٔ دستگاه» برنمی‌گرداند؛ دستگاه باید رازی را نشان دهد که در
 * نخستین پیوستن (یا از نشستِ بازِ خودش، `POST /api/auth/staff/device-key`) گرفته و در حافظهٔ امنِ خودش نگه داشته.
 *
 * - راز ۳۲ بایتِ تصادفی (`randomBytes`) است و فقط **یک بار** در پاسخ می‌آید؛ سرور فقط HMAC ِ آن را با رازِ سرور
 *   نگه می‌دارد (`key_hash`) — نه خودش، نه چیزی که از آن برگردد. هرگز در لاگ یا سابقه نمی‌نشیند.
 * - راز به «دکان + کاربر + شناسهٔ دستگاه» بسته است: رازِ دکان یا دستگاهِ دیگر چیزی را باز نمی‌کند.
 * - یک رازِ زنده برای هر سه‌تایی؛ رازِ تازه پیشین را باطل می‌کند (درونِ یک تراکنش).
 * - ⛔ عمرِ راز (مهاجرتِ ۳۴۵): وقتی اعتماد تمام شد سرور راز را **درونِ همان تراکنشِ کارِ اصلی** باطل می‌کند
 *   ([revokeIn]): «خروج از همه» (خودِ شاگرد یا صاحب)، برداشتن/تعلیقِ عضو، باطل کردنِ کدِ شماره‌دارِ خودِ او، حذفِ
 *   حساب، «باطل کردنِ این دستگاه» ِ صاحب. هر بار یک ردیفِ سابقه (`staff_device_key.revoked`) با دلیل و شمار —
 *   هرگز راز یا HMAC. رازِ باطل برنمی‌گردد (نگهبانِ پایگاه + `verify`/`issue`)؛ هر راه دوباره‌زدنی است (بار دوم هیچ).
 */
const { createHmac, randomBytes } = require('crypto');
const { one, many, tx, newId, now } = require('../db');

/** پرچمِ کلیدِ اضطراری — پیش‌فرض **روشن**؛ فقط `false` ِ صریح (دکان یا all) رفتارِ پیشین را برمی‌گرداند. */
const FLAG_KEY = 'auth.staff_session_guard';

/** بلندترین رازی که پذیرفته می‌شود (رازِ ما ۴۳ نویسه است) — ورودیِ بلندتر بی پرسشِ پایگاه رد */
const MAX_LEN = 128;

/** همان رازِ سرورِ کدِ شاگرد (`staff-codes.pepper`)، با پیشوندِ جدا تا HMAC ِ راز و کد هرگز هم‌دامنه نباشند */
function hashKey(secret) {
  return createHmac('sha256', require('./staff-codes').pepper()).update(`staff-device:${secret}`).digest('hex');
}

/** رازِ فرستاده از بدنه — فقط رشتهٔ ناتهیِ کوتاه؛ هر چیزِ دیگر یعنی «رازی نیامد» */
function presented(device) {
  const raw = device && typeof device === 'object' && !Array.isArray(device) ? device.key : undefined;
  if (typeof raw !== 'string') return '';
  const s = raw.trim();
  return s.length > 0 && s.length <= MAX_LEN ? s : '';
}

/** برنامه رازِ دستگاه را می‌شناسد؟ (`device.keyVersion` ≥ ۱ یا رازی فرستاده) — نسخهٔ کهنه راز نمی‌گیرد که دور بریزد */
function capable(device) {
  if (presented(device)) return true;
  const v = device && typeof device === 'object' && !Array.isArray(device) ? Number(device.keyVersion) : 0;
  return Number.isFinite(v) && v >= 1;
}

async function guardOn(shopId) {
  return require('./default-on-flag').defaultOn(shopId, FLAG_KEY);
}

/**
 * رازِ نشان‌داده درست است؟ ⇒ ردیفِ زنده (`user_id`)، وگرنه `null`. فقط با همان دکان و همان شناسهٔ دستگاه.
 */
async function verify({ shopId, deviceUid, secret }) {
  if (!shopId || !deviceUid || !secret) return null;
  const row = await one(
    `UPDATE staff_device_keys SET last_used_at=$4
      WHERE key_hash=$1 AND shop_id=$2 AND device_uid=$3 AND revoked_at IS NULL
      RETURNING id, user_id`,
    [hashKey(secret), shopId, deviceUid, now()]
  );
  return row || null;
}

/** این کاربر روی این دستگاهِ این دکان رازِ زنده دارد؟ */
async function hasLive({ shopId, userId, deviceUid }) {
  if (!shopId || !userId || !deviceUid) return false;
  const row = await one(
    `SELECT 1 AS ok FROM staff_device_keys
      WHERE shop_id=$1 AND user_id=$2 AND device_uid=$3 AND revoked_at IS NULL`,
    [shopId, userId, deviceUid]
  );
  return Boolean(row);
}

/**
 * رازِ تازه برای «دکان + کاربر + دستگاه»؛ رازِ زندهٔ پیشینِ همان سه‌تایی باطل می‌شود. خودِ راز فقط همین‌جا برمی‌گردد.
 * `onlyIfNone` ⇒ اگر رازِ زنده‌ای هست هیچ کاری نمی‌شود و `null` برمی‌گردد (برای گرفتن از نشستِ باز — هرگز چرخاندن).
 */
async function issue({ shopId, userId, deviceUid, onlyIfNone = false }) {
  if (!shopId || !userId || !deviceUid) return null;
  const secret = randomBytes(32).toString('base64url');
  const t = now();
  return tx(async (c) => {
    //  دو درخواستِ هم‌زمانِ همان دستگاه پشتِ هم، نه کنارِ هم (نمایهٔ یکتای جزئی سدِّ دوم است)
    await c.query('SELECT pg_advisory_xact_lock(hashtext($1))', [`staff-device-key:${shopId}:${userId}:${deviceUid}`]);
    const live = (await c.query(
      `SELECT id FROM staff_device_keys WHERE shop_id=$1 AND user_id=$2 AND device_uid=$3 AND revoked_at IS NULL`,
      [shopId, userId, deviceUid]
    )).rows[0];
    if (live && onlyIfNone) return null;
    //  ⛔ از نشستِ باز فقط برای دستگاهی که هرگز راز نداشته (مهاجرتِ آرام) — رازِ باطل‌شده با توکنِ باز برنمی‌گردد
    if (onlyIfNone && (await c.query(
      'SELECT 1 FROM staff_device_keys WHERE shop_id=$1 AND user_id=$2 AND device_uid=$3 LIMIT 1',
      [shopId, userId, deviceUid])).rows.length) return null;
    if (live) {
      await c.query(`UPDATE staff_device_keys SET revoked_at=$2, revoked_reason='rotated' WHERE id=$1 AND revoked_at IS NULL`,
        [live.id, t]);
    }
    await c.query(
      `INSERT INTO staff_device_keys (id, shop_id, user_id, device_uid, key_hash, created_at)
       VALUES ($1,$2,$3,$4,$5,$6)`,
      [newId('sdk'), shopId, userId, deviceUid, hashKey(secret), t]
    );
    return secret;
  });
}

/* ------------------------------------------------------------------ عمرِ راز (۳۴۵) */

/** دلیل‌های باطل شدن — فقط همین‌ها در `revoked_reason` و سابقه */
const REASONS = Object.freeze([
  'rotated', 'logout_all', 'owner_logout_all', 'member_removed', 'member_suspended',
  'code_revoked', 'account_deleted', 'owner_revoked',
]);

/** «هیچ رازی باطل نشد» — برای راه‌هایی که چیزی برای باطل کردن ندارند */
const NONE = Object.freeze({ revoked: 0, afterCommit: async () => {} });

/**
 * ⛔ باطل کردنِ رازهای زندهٔ دستگاه **درونِ تراکنشِ کارِ اصلی** (`c` ِ `db.tx`) — شکستش کار را برمی‌گرداند.
 *
 * دست‌کم یکی از `shopId`، `userIds`، `shopIds` یا `keyId` لازم است (هرگز «همه»). فقط ردیف‌های زنده عوض می‌شوند ⇒
 * دوباره‌زدنی: بار دوم `revoked: 0` و هیچ سابقه‌ای. برای هر «دکان + کاربر» یک ردیفِ `staff_device_key.revoked` با
 * دلیل، شمار و شناسهٔ دستگاه‌ها — هرگز راز یا `key_hash`. زنجیرهٔ سابقه (STF-09) با `afterCommit()` پس از COMMIT.
 *
 * @param {object} c client ِ تراکنش
 * @param {{shopId?: string, userIds?: string[], shopIds?: string[], keyId?: string, reason: string,
 *   by?: string, actorType?: string, ip?: string, detail?: object}} o `userIds`+`shopIds` با هم ⇒ «کاربر یا دکان»
 * @returns {Promise<{revoked: number, afterCommit: () => Promise<void>}>}
 */
async function revokeIn(c, o) {
  const { shopId = null, keyId = null, reason, by = '', actorType = 'user', ip = '', detail = {} } = o || {};
  if (!REASONS.includes(reason)) throw new Error(`staff-device-keys: دلیلِ ناشناخته ${reason}`);
  const userIds = Array.isArray(o.userIds) ? o.userIds.filter((x) => typeof x === 'string' && x) : null;
  const shopIds = Array.isArray(o.shopIds) ? o.shopIds.filter((x) => typeof x === 'string' && x) : null;
  if (!shopId && !keyId && !(userIds && userIds.length) && !(shopIds && shopIds.length)) return NONE;
  //  فهرستِ کاربرِ خالی (کدی که کسی خرجش نکرده) یعنی هیچ — نه «همهٔ دکان»
  if (userIds && !userIds.length && !(shopIds && shopIds.length)) return NONE;
  const r = await c.query(
    `UPDATE staff_device_keys SET revoked_at=$1, revoked_reason=$2, revoked_by=$3
      WHERE revoked_at IS NULL
        AND ($4::text IS NULL OR shop_id = $4::text)
        AND ($5::text IS NULL OR id = $5::text)
        AND (($6::text[] IS NULL AND $7::text[] IS NULL)
             OR user_id = ANY(COALESCE($6::text[], '{}'::text[])) OR shop_id = ANY(COALESCE($7::text[], '{}'::text[])))
      RETURNING id, shop_id, user_id, device_uid`,
    [now(), reason, String(by || ''), shopId, keyId, userIds, shopIds]
  );
  if (!r.rows.length) return NONE;
  const groups = new Map();
  for (const row of r.rows) {
    const k = `${row.shop_id}\u0000${row.user_id}`;
    if (!groups.has(k)) groups.set(k, { shopId: row.shop_id, userId: row.user_id, devices: [] });
    groups.get(k).devices.push(row.device_uid);
  }
  const after = [];
  for (const g of groups.values()) {
    after.push(await require('./audit').logIn(c, {
      shopId: g.shopId, actorType, userId: String(by || ''), action: 'staff_device_key.revoked',
      targetType: 'user', targetId: g.userId,
      detail: { ...detail, reason, keys: g.devices.length, devices: g.devices.slice(0, 20) }, ip,
    }));
  }
  return {
    revoked: r.rows.length,
    async afterCommit() { for (const a of after) await a.afterCommit(); },
  };
}

/** شناسهٔ دستگاه کوتاه برای چشمِ صاحب — «abcd…wxyz»؛ کوتاه‌تر از ۱۱ نویسه همان */
function shortUid(uid) {
  const s = String(uid || '');
  return s.length > 10 ? `${s.slice(0, 4)}…${s.slice(-4)}` : s;
}

/** نامِ یکسان‌شده برای «همان نام» — فاصله‌ها، کشیده و حرفِ کوچک */
function sameName(a, b) {
  const n = (x) => String(x || '').replace(/ـ/g, '').replace(/\s+/g, ' ').trim().toLowerCase();
  return Boolean(n(a)) && n(a) === n(b);
}

/**
 * فهرستِ صاحب/مدیر: رازهای زندهٔ اعضای همین دکان (برچسب از `devices` ِ همان کاربر و همان شناسه) و «شاید همان
 * عضوِ کهنه» — عضوِ تازه‌ای که شناسهٔ دستگاهش پیش‌تر مالِ عضوِ کهنه‌ترِ همین دکان بوده (نسخهٔ کهنهٔ برنامه یا رازِ
 * باطل‌شده ⇒ کدِ تازه ⇒ حسابِ تازه). ⛔ فقط نشانه است: هرگز حساب‌ها یکی نمی‌شوند. همه چیز فقط با `shop_id` ِ عضویتِ توکن.
 */
async function overview(shopId) {
  const devices = await many(
    `SELECT k.id, k.user_id, m.id AS member_id, k.device_uid, k.created_at, k.last_used_at,
            COALESCE(d.name, '') AS label, COALESCE(d.platform, '') AS platform
       FROM staff_device_keys k
       JOIN shop_members m ON m.shop_id = k.shop_id AND m.user_id = k.user_id AND m.status <> 'removed'
       LEFT JOIN devices d ON d.user_id = k.user_id AND d.device_uid = k.device_uid
      WHERE k.shop_id = $1 AND k.revoked_at IS NULL
      ORDER BY k.created_at DESC
      LIMIT 500`,
    [shopId]
  );
  const hints = await many(
    `WITH m AS (
       SELECT sm.id, sm.user_id, sm.created_at, u.name
         FROM shop_members sm JOIN users u ON u.id = sm.user_id
        WHERE sm.shop_id = $1 AND sm.status <> 'removed' AND sm.role <> 'owner'
     ), d AS (
       SELECT user_id, device_uid FROM devices WHERE user_id IN (SELECT user_id FROM m)
       UNION
       SELECT user_id, device_uid FROM staff_device_keys WHERE shop_id = $1 AND user_id IN (SELECT user_id FROM m)
     )
     SELECT DISTINCT ON (n.id, o.id)
            n.id AS member_id, n.user_id, n.name, o.id AS old_member_id, o.user_id AS old_user_id,
            o.name AS old_name, dn.device_uid
       FROM m n
       JOIN d dn ON dn.user_id = n.user_id
       JOIN d od ON od.device_uid = dn.device_uid AND od.user_id <> n.user_id
       JOIN m o ON o.user_id = od.user_id
      WHERE o.created_at < n.created_at
      ORDER BY n.id, o.id, dn.device_uid
      LIMIT 200`,
    [shopId]
  );
  return {
    devices: devices.map((k) => ({
      id: k.id, userId: k.user_id, memberId: k.member_id, label: k.label, platform: k.platform,
      device: shortUid(k.device_uid), createdAt: Number(k.created_at),
      lastUsedAt: k.last_used_at === null || k.last_used_at === undefined ? null : Number(k.last_used_at),
    })),
    hints: hints.map((x) => ({
      memberId: x.member_id, userId: x.user_id, oldMemberId: x.old_member_id, oldUserId: x.old_user_id,
      oldName: x.old_name || '', sameName: sameName(x.name, x.old_name), device: shortUid(x.device_uid),
    })),
  };
}

/** پس از COMMIT: سوکتِ زندهٔ همان کس دوباره سنجیده می‌شود (S78) و پوشِ دستگاه‌های بسته می‌رود */
async function afterSessions(userId, deviceIds, shopId, reason) {
  await require('./sync-v1-live').accessChanged({ userId: String(userId) });
  const push = require('./push');
  if (deviceIds === null) await push.revokeMember(shopId, userId, reason);
  else for (const id of deviceIds) await push.revokeDevice({ deviceId: id, userId, app: 'shop', reason });
}

/**
 * «باطل کردنِ این دستگاه» ِ صاحب: همان راز باطل و نشست‌های همان دستگاهِ همان کاربر (بخشِ دکان، نشستِ بی‌دکان یا
 * بسته به همین دکان) بسته — هر دو در یک تراکنش، با سابقه. رازِ دکانِ دیگر ⇒ ۴۰۴ (همان پاسخِ «نیست»)؛ رازِ از پیش
 * باطل ⇒ ۲۰۰ با `revoked: 0` (دوباره‌زدنی).
 */
async function revokeDevice({ shopId, keyId, by = '', ip = '' }) {
  const { notFound } = require('../middleware/errors');
  const row = await one('SELECT id, user_id, device_uid FROM staff_device_keys WHERE id=$1 AND shop_id=$2', [keyId, shopId]);
  if (!row) throw notFound('این دستگاه در دکانِ شما نیست', 'device_key_not_found');
  const out = await tx(async (c) => {
    const keys = await revokeIn(c, { shopId, keyId: row.id, reason: 'owner_revoked', by, ip });
    const s = await c.query(
      `UPDATE tokens SET revoked_at=$1
        WHERE subject_id=$2 AND revoked_at IS NULL AND COALESCE(app,'shop')='shop' AND (shop_id IS NULL OR shop_id=$3)
          AND device_id IN (SELECT id FROM devices WHERE user_id=$2 AND device_uid=$4)
        RETURNING device_id`,
      [now(), row.user_id, shopId, row.device_uid]
    );
    return { keys, sessions: s.rows.length, deviceIds: [...new Set(s.rows.map((x) => x.device_id))] };
  });
  await out.keys.afterCommit();
  await afterSessions(row.user_id, out.deviceIds, shopId, 'device_key_revoked');
  return { revoked: out.keys.revoked, sessions: out.sessions, userId: row.user_id };
}

/**
 * «خروج از همهٔ دستگاه‌ها» برای یک عضو، به دستِ صاحب: همهٔ نشست‌های بخشِ دکانِ او (بی‌دکان یا بسته به همین دکان) و
 * همهٔ رازهای دستگاهش در همین دکان — یک تراکنش، با سابقه. عضوِ دکانِ دیگر ⇒ ۴۰۴؛ صاحب ⇒ ۴۰۰.
 */
async function logoutMember({ shopId, memberId, by = '', ip = '' }) {
  const { notFound, badRequest } = require('../middleware/errors');
  const m = await require('./shops').findMember(shopId, memberId);
  if (!m || m.status === 'removed') throw notFound('این عضو در دکان شما نیست', 'member_not_found');
  if (m.role === 'owner') throw badRequest('صاحب دکان را نمی‌توان تغییر داد', 'owner_immutable');
  const out = await tx(async (c) => {
    const s = await c.query(
      `UPDATE tokens SET revoked_at=$1
        WHERE subject_id=$2 AND revoked_at IS NULL AND COALESCE(app,'shop')='shop' AND (shop_id IS NULL OR shop_id=$3)`,
      [now(), m.user_id, shopId]
    );
    const keys = await revokeIn(c, { shopId, userIds: [m.user_id], reason: 'owner_logout_all', by, ip });
    return { keys, sessions: s.rowCount || 0 };
  });
  await out.keys.afterCommit();
  await afterSessions(m.user_id, null, shopId, 'member_logout_all');
  return { revoked: out.keys.revoked, sessions: out.sessions, userId: m.user_id, memberId: m.id };
}

module.exports = {
  FLAG_KEY, MAX_LEN, REASONS, NONE, revokeDevice, logoutMember, presented, capable, guardOn, verify, hasLive, issue, hashKey,
  revokeIn, overview, shortUid, sameName,
};
