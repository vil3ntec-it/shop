'use strict';
/**
 * بازدیدکننده‌ها — کسانی که آمده‌اند، چه حساب ساخته باشند چه نه.
 *
 * ── مشکلی که این حل می‌کند ─────────────────────────────────────────
 * پنل فقط کسانی را نشان می‌داد که ثبت‌نام کرده بودند. کسی که برنامه را
 * نصب کرده و باز کرده ولی هنوز حساب نساخته — یعنی همان کسی که باید
 * دنبالش رفت — هیچ‌جا دیده نمی‌شد. نه شمارش، نه اینکه از کجاست، نه
 * اینکه چند بار برگشته.
 *
 * حالا هر بار که برنامه یا سایت باز می‌شود یک «تپش» می‌فرستد و همین‌جا
 * می‌نشیند. ردیف به دستگاه بسته است نه به کاربر، و اگر بعداً حساب
 * ساخته شد همان ردیف به حسابش وصل می‌شود — پس تاریخِ اولین باری که
 * آمده گم نمی‌شود.
 *
 * ── حریم خصوصی ─────────────────────────────────────────────────────
 * لوکیشن فقط اگر خودِ دستگاه بدهد ثبت می‌شود. IP برای همان کاری است که
 * جای دیگر هم استفاده می‌شود (محدودیت نرخ و پیگیری سوءاستفاده).
 */
const { query, one, many, newId, now } = require('../db');

const APPS = ['shop', 'admin'];

/** کوتاه کردن هر چیزی که از بیرون می‌آید، پیش از رفتن به دیتابیس. */
function cut(value, max) {
  return String(value ?? '').slice(0, max);
}

function num(value) {
  const n = Number(value);
  return Number.isFinite(n) ? n : null;
}

/**
 * ثبت یک بازدید.
 *
 * `visits` فقط وقتی یکی بالا می‌رود که از آخرین بار نیم‌ساعت گذشته
 * باشد. بدون این، برنامه‌ای که هر چند دقیقه تپش می‌فرستد عددِ بازدید را
 * بی‌معنی می‌کرد.
 */
const VISIT_GAP_MS = 30 * 60 * 1000;

async function touch({
  app = 'shop', deviceUid = '', platform = '', appVersion = '',
  userId = '', shopId = '', name = '', ip = '', userAgent = '', language = '',
  location = null,
} = {}) {
  const uid = cut(deviceUid, 64).trim();
  if (!uid) return null;
  const t = now();
  const slug = cut(app || 'shop', 40);

  const existing = await one('SELECT * FROM app_visitors WHERE app=$1 AND device_uid=$2', [slug, uid]);
  const lat = location ? num(location.lat) : null;
  const lng = location ? num(location.lng) : null;
  const hasPlace = lat !== null && lng !== null;

  if (!existing) {
    const row = await one(
      `INSERT INTO app_visitors (id, app, device_uid, platform, app_version, user_id, shop_id, name,
                                 ip, user_agent, language, lat, lng, accuracy, place,
                                 first_seen_at, last_seen_at, visits)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15,$16,$16,1) RETURNING *`,
      [newId('vis'), slug, uid, cut(platform, 20), cut(appVersion, 30), cut(userId, 40), cut(shopId, 40),
        cut(name, 80), cut(ip, 60), cut(userAgent, 300), cut(language, 20),
        hasPlace ? lat : null, hasPlace ? lng : null,
        hasPlace ? num(location.accuracy) : null, cut(location?.label, 120), t]
    );
    return shape(row);
  }

  const bump = t - Number(existing.last_seen_at) > VISIT_GAP_MS ? 1 : 0;
  const row = await one(
    `UPDATE app_visitors SET
        platform    = CASE WHEN $3 <> '' THEN $3 ELSE platform END,
        app_version = CASE WHEN $4 <> '' THEN $4 ELSE app_version END,
        user_id     = CASE WHEN $5 <> '' THEN $5 ELSE user_id END,
        shop_id     = CASE WHEN $6 <> '' THEN $6 ELSE shop_id END,
        name        = CASE WHEN $7 <> '' THEN $7 ELSE name END,
        ip          = CASE WHEN $8 <> '' THEN $8 ELSE ip END,
        user_agent  = CASE WHEN $9 <> '' THEN $9 ELSE user_agent END,
        language    = CASE WHEN $10 <> '' THEN $10 ELSE language END,
        lat         = COALESCE($11, lat),
        lng         = COALESCE($12, lng),
        accuracy    = COALESCE($13, accuracy),
        place       = CASE WHEN $14 <> '' THEN $14 ELSE place END,
        last_seen_at = $15,
        visits      = visits + $16
      WHERE id=$1 AND app=$2 RETURNING *`,
    [existing.id, slug, cut(platform, 20), cut(appVersion, 30), cut(userId, 40), cut(shopId, 40),
      cut(name, 80), cut(ip, 60), cut(userAgent, 300), cut(language, 20),
      hasPlace ? lat : null, hasPlace ? lng : null,
      hasPlace ? num(location.accuracy) : null, cut(location?.label, 120), t, bump]
  );
  return shape(row);
}

/** وقتی مهمان بالاخره حساب ساخت، ردیفش به حسابش می‌چسبد. */
async function claim(deviceUid, userId, shopId = '') {
  const uid = cut(deviceUid, 64).trim();
  if (!uid || !userId) return;
  await query(
    `UPDATE app_visitors SET user_id=$2, shop_id = CASE WHEN $3 <> '' THEN $3 ELSE shop_id END
      WHERE device_uid=$1 AND user_id=''`,
    [uid, userId, shopId]
  );
}

function shape(r) {
  return {
    id: r.id,
    app: r.app,
    deviceUid: r.device_uid,
    platform: r.platform,
    appVersion: r.app_version,
    userId: r.user_id || '',
    shopId: r.shop_id || '',
    name: r.name || '',
    ip: r.ip || '',
    language: r.language || '',
    userAgent: r.user_agent || '',
    location: r.lat === null || r.lng === null ? null : {
      lat: Number(r.lat), lng: Number(r.lng),
      accuracy: r.accuracy === null ? null : Number(r.accuracy),
      label: r.place || '',
    },
    firstSeenAt: Number(r.first_seen_at),
    lastSeenAt: Number(r.last_seen_at),
    visits: Number(r.visits),
    //  همان تفکیکی که مدیر می‌خواهد: مهمان یا حساب‌دار
    guest: !r.user_id,
  };
}

/**
 * فهرست، با گزینه‌ی «فقط مهمان‌ها».
 *
 * نام و ایمیلِ حساب هم می‌آید تا برای هر ردیف یک درخواستِ جدا لازم
 * نباشد — صفحه‌ای که برای هر سطر یک کوئری بزند، روی سیصد بازدیدکننده
 * می‌ایستد.
 */
async function list({ app = '', onlyGuests = false, q = '', limit = 100, offset = 0 } = {}) {
  const like = `%${String(q || '').toLowerCase()}%`;
  const rows = await many(
    `SELECT v.*, u.name AS account_name, u.email AS account_email, s.name AS shop_name
       FROM app_visitors v
       LEFT JOIN users u ON u.id = v.user_id
       LEFT JOIN shops s ON s.id = v.shop_id
      WHERE ($1 = '' OR v.app = $1)
        AND ($2 = false OR v.user_id = '')
        AND ($3 = '' OR lower(v.name) LIKE $4 OR lower(coalesce(u.name,'')) LIKE $4
             OR lower(coalesce(u.email,'')) LIKE $4 OR v.ip LIKE $4 OR lower(v.place) LIKE $4)
      ORDER BY v.last_seen_at DESC LIMIT $5 OFFSET $6`,
    [cut(app, 40), !!onlyGuests, String(q || ''), like, limit, offset]
  );
  return rows.map(r => ({
    ...shape(r),
    accountName: r.account_name || '',
    accountEmail: r.account_email || '',
    shopName: r.shop_name || '',
  }));
}

/** شمارش‌ها برای بالای صفحه. */
async function summary({ app = '' } = {}) {
  const t = now();
  const day = t - 24 * 3600 * 1000;
  const week = t - 7 * 24 * 3600 * 1000;
  const slug = cut(app, 40);
  const [total, guests, today, thisWeek, located] = await Promise.all([
    one(`SELECT COUNT(*)::int n FROM app_visitors WHERE ($1='' OR app=$1)`, [slug]),
    one(`SELECT COUNT(*)::int n FROM app_visitors WHERE ($1='' OR app=$1) AND user_id=''`, [slug]),
    one(`SELECT COUNT(*)::int n FROM app_visitors WHERE ($1='' OR app=$1) AND last_seen_at>$2`, [slug, day]),
    one(`SELECT COUNT(*)::int n FROM app_visitors WHERE ($1='' OR app=$1) AND last_seen_at>$2`, [slug, week]),
    one(`SELECT COUNT(*)::int n FROM app_visitors WHERE ($1='' OR app=$1) AND lat IS NOT NULL`, [slug]),
  ]);
  const platforms = await many(
    `SELECT platform, COUNT(*)::int n FROM app_visitors WHERE ($1='' OR app=$1)
      GROUP BY platform ORDER BY n DESC LIMIT 10`, [slug]
  );
  return {
    total: total.n, guests: guests.n, signedUp: total.n - guests.n,
    today: today.n, week: thisWeek.n, located: located.n,
    platforms: platforms.map(p => ({ platform: p.platform || 'نامعلوم', count: p.n })),
  };
}

module.exports = { touch, claim, list, summary, shape, APPS };
