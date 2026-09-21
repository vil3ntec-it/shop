'use strict';
/**
 * پنلِ مدیریت — بخشِ پمپ‌بنزین.
 *
 * خواستهٔ صاحب مخزن: «تو بخشِ پمپ‌بنزین هم میخام جوری باشه که به هر
 * حساب اشتراک بدم یا که ندم، مثل بخشِ شاپ.»
 *
 * پس همان چند کاری که در بخشِ شاپ هست، این‌جا هم هست و بس:
 *   فهرستِ پمپ‌ها · یکِ پمپ با دفترش · اشتراک دادن و گرفتن · کدِ شش‌رقمی
 *
 * زیرِ `/api/admin/pump/…` می‌نشیند و همان نگهبانِ `requireAdmin` را
 * دارد که بقیهٔ مسیرهای مدیریت دارند — این فایل خودش دری باز نمی‌کند.
 */
const express = require('express');
const { one, many, query, now } = require('../db');
const v = require('../lib/validate');
const stations = require('../lib/stations');
const subs = require('../lib/subscriptions').pump;
const vip = require('../lib/vip-codes').pump;
const plans = require('../lib/plans');
const audit = require('../lib/audit');
const { catalogOf, PUMP_FEATURES } = require('../lib/features');
const { entitlementOf } = require('../lib/entitlement').pump;
const { badRequest, notFound } = require('../middleware/errors');

const router = express.Router();
const PUMP = catalogOf('pump');

/** کاتالوگِ قابلیت‌های پمپ — پنل از این چک‌باکس‌هایش را می‌سازد. */
router.get('/features', (req, res) => res.json({ features: PUMP_FEATURES }));

/* ==========================================================
   پمپ‌ها
   ========================================================== */

router.get('/stations', async (req, res) => {
  await subs.expireDue();
  const limit = v.integer(req.query?.limit, { min: 1, max: 200, def: 50 });
  const offset = v.integer(req.query?.offset, { min: 0, max: 1e6, def: 0 });
  const q = v.text(req.query?.q, { max: 60 });
  const like = `%${q.toLowerCase()}%`;
  const rows = await many(
    `SELECT s.id, s.code, s.name, s.status, s.created_at, s.owner_user_id,
            s.home_url, s.home_seen_at,
            u.name AS owner_name, u.phone AS owner_phone, u.email AS owner_email,
            (SELECT COUNT(*)::int FROM station_members m
              WHERE m.station_id=s.id AND m.status='active') AS members,
            (SELECT COUNT(*)::int FROM station_files f WHERE f.station_id=s.id) AS files,
            sub.id AS subscription_id, sub.plan, sub.status AS sub_status,
            sub.starts_at, sub.ends_at
       FROM stations s
       --  LEFT، نه JOIN. پمپی که با کدِ شش‌رقمی فعال شده صاحب ندارد
       --  (owner_user_id خالی). با JOINِ ساده چنین پمپی از فهرست
       --  می‌افتاد — و چون همهٔ پمپ‌های فعال‌شده با کد همین‌طورند،
       --  کلِ صفحه خالی می‌ماند در حالی که total عددِ درست را
       --  می‌گفت. همان «بخشِ پمپ هیچی نداره».
       LEFT JOIN users u ON u.id = s.owner_user_id
       LEFT JOIN LATERAL (
         SELECT * FROM station_subscriptions x WHERE x.station_id = s.id
          ORDER BY (x.status IN ('active','suspended','pending')) DESC, x.created_at DESC LIMIT 1
       ) sub ON true
      --  ⛔ **ایمیل هم گشته می‌شود.** خواستهٔ صریحِ صاحب سامانه
      --  (۱۴۰۵/۰۷/۰۸): «به حسابِ مورد نظر یا ایمیلِ مد نظر اشتراک بدم».
      --  بی این، تنها راهِ پیدا کردنِ یک پمپ نامِ پمپ یا نامِ صاحبش بود —
      --  و صاحبِ سامانه معمولاً فقط ایمیلِ طرف را دارد.
      WHERE ($1 = '' OR lower(s.name) LIKE $2 OR lower(s.code) LIKE $2
             OR lower(coalesce(u.name,'')) LIKE $2
             OR lower(coalesce(u.email,'')) LIKE $2
             OR coalesce(u.phone,'') LIKE $2)
      ORDER BY s.created_at DESC LIMIT $3 OFFSET $4`,
    [q, like, limit, offset]
  );
  const total = await one('SELECT COUNT(*)::int n FROM stations');
  //  پمپی که هنوز اشتراک نخریده ولی در دورهٔ آزمایشی است، «آزمایشی»
  //  نشان داده شود — وگرنه در فهرست بی‌اشتراک به نظر می‌رسد و مدیر
  //  بی‌دلیل سراغش می‌رود.
  const trialDays = await plans.trialDaysOf('pump');
  const t = now();
  const list = rows.map(r => {
    if (r.sub_status) return r;
    const trialEnds = Number(r.created_at) + trialDays * 24 * 3600 * 1000;
    return trialEnds > t ? { ...r, sub_status: 'trial', ends_at: trialEnds } : r;
  });
  res.json({ stations: list, total: total.n, limit, offset });
});

router.get('/stations/:id', async (req, res, next) => {
  try {
    const id = v.id(req.params.id);
    const st = await one('SELECT * FROM stations WHERE id=$1', [id]);
    if (!st) return next(notFound('پمپ پیدا نشد', 'station_not_found'));
    const ent = await entitlementOf(id);
    const files = await many(
      `SELECT path, rev, size, updated_at FROM station_files
        WHERE station_id=$1 ORDER BY path`, [id]
    );
    res.json({
      station: stations.shape(st),
      //  کدِ اپِ کارمندان — تا مدیر بتواند به صاحبِ پمپی که کدش را گم کرده بگوید
      accessCode: st.access_code ? require('../lib/station-access').format(st.access_code) : '',
      owner: await one('SELECT id, name, email, phone, status FROM users WHERE id=$1',
        [st.owner_user_id]),
      members: await stations.members(id),
      entitlement: ent,
      subscription: ent.subscription,
      //  پوشهٔ همین پمپ — فقط فهرست و اندازه. خودِ داده مالِ صاحبش
      //  است و در پنل باز نمی‌شود.
      files: files.map(f => ({
        path: f.path, rev: Number(f.rev), size: Number(f.size), updatedAt: Number(f.updated_at),
      })),
      serverTime: now(),
    });
  } catch (err) { next(err); }
});

/**
 * دفترِ خبرِ همین پمپ — همان چیزی که برنامه فرستاده.
 *
 * ⚠️ فقط **دیدن**. مدیر این‌جا خبری نمی‌سازد و چیزی پاک نمی‌کند؛ این
 * پنجره برای وقتی است که صاحبِ پمپ می‌گوید «خبر نگرفتم» و باید معلوم
 * شود خبر به ابر رسیده بود یا نه.
 */
router.get('/stations/:id/events', async (req, res, next) => {
  try {
    const id = v.id(req.params.id);
    const st = await one('SELECT id FROM stations WHERE id=$1', [id]);
    if (!st) return next(notFound('پمپ پیدا نشد', 'station_not_found'));
    res.json({
      events: await require('../lib/events').pump.list(id, {
        limit: v.integer(req.query?.limit, { min: 1, max: 200, def: 50 }),
      }),
      serverTime: now(),
    });
  } catch (err) { next(err); }
});

router.get('/stations/:id/history', async (req, res, next) => {
  try {
    res.json({ history: await subs.changeLog(v.id(req.params.id), 100) });
  } catch (err) { next(err); }
});

/** روشن و خاموش کردنِ یک پمپ. */
router.post('/stations/:id/status', async (req, res, next) => {
  try {
    const id = v.id(req.params.id);
    const status = String(req.body?.status || '');
    if (!['active', 'disabled'].includes(status)) {
      return next(badRequest('وضعیت معتبر نیست', 'bad_status'));
    }
    const row = await one(
      'UPDATE stations SET status=$2, updated_at=$3 WHERE id=$1 RETURNING *', [id, status, now()]
    );
    if (!row) return next(notFound('پمپ پیدا نشد', 'station_not_found'));
    await audit.log({
      actorType: 'admin', userId: req.admin.id, action: 'admin.station_status',
      targetType: 'station', targetId: id, detail: { status },
    });
    res.json({ station: stations.shape(row) });
  } catch (err) { next(err); }
});

/* ==========================================================
   اشتراک
   ========================================================== */

router.get('/subscriptions', async (req, res) => {
  await subs.expireDue();
  const limit = v.integer(req.query?.limit, { min: 1, max: 200, def: 50 });
  const status = v.text(req.query?.status, { max: 20 });
  const rows = await many(
    `SELECT sub.*, s.name AS station_name, s.code AS station_code
       FROM station_subscriptions sub
       JOIN stations s ON s.id = sub.station_id
      WHERE ($1 = '' OR sub.status = $1)
      ORDER BY sub.created_at DESC LIMIT $2`,
    [status, limit]
  );
  res.json({ subscriptions: rows.map(r => ({ ...r, state: subs.stateOf(r) })) });
});

/** اشتراک دادن یا تمدید کردن — همان کاری که در بخشِ شاپ می‌شود. */
router.post('/subscriptions', async (req, res, next) => {
  try {
    const stationId = v.id(req.body?.stationId, { field: 'شناسه‌ی پمپ' });
    const row = await subs.grant(stationId, {
      plan: v.text(req.body?.plan, { max: 20 }) || 'custom',
      days: req.body?.days === undefined || req.body?.days === null || req.body?.days === ''
        ? null : v.integer(req.body.days, { field: 'روزها', min: 1, max: 3650 }),
      endsAt: req.body?.endsAt ? Number(req.body.endsAt) : null,
      features: Array.isArray(req.body?.features) ? req.body.features : [],
      maxDevices: v.integer(req.body?.maxDevices, { field: 'تعداد دستگاه', min: 1, max: 100, def: 10 }),
      graceDays: v.integer(req.body?.graceDays, { field: 'مهلت', min: 0, max: 90, def: 0 }),
      note: v.text(req.body?.note, { max: 300 }),
      createdBy: req.admin.id,
    });
    await audit.log({
      actorType: 'admin', userId: req.admin.id, action: 'admin.pump_subscription_granted',
      targetType: 'station', targetId: stationId,
      detail: { plan: row.plan, endsAt: Number(row.ends_at) },
    });
    res.status(201).json({ subscription: row, state: subs.stateOf(row) });
  } catch (err) { next(err); }
});

/** گرفتنِ اشتراک — تعلیق یا لغو. */
router.post('/subscriptions/:id/status', async (req, res, next) => {
  try {
    const row = await subs.setStatus(v.id(req.params.id),
      String(req.body?.status || ''), req.admin.id);
    await audit.log({
      actorType: 'admin', userId: req.admin.id, action: 'admin.pump_subscription_status',
      targetType: 'subscription', targetId: row.id, detail: { status: row.status },
    });
    res.json({ subscription: row, state: subs.stateOf(row) });
  } catch (err) { next(err); }
});

/** اشتراک‌هایی که دارند تمام می‌شوند. */
router.get('/subscriptions/expiring', async (req, res, next) => {
  try {
    res.json({
      subscriptions: await subs.expiringSoon({
        withinDays: v.integer(req.query?.days, { min: 1, max: 90, def: 7 }),
        includeExpired: v.integer(req.query?.expired, { min: 0, max: 30, def: 3 }),
      }),
    });
  } catch (err) { next(err); }
});

/* ==========================================================
   کدِ شش‌رقمیِ پمپ
   ========================================================== */

router.get('/vip-codes', async (req, res, next) => {
  try {
    res.json({
      codes: await vip.list({
        status: v.text(req.query?.status, { max: 20 }),
        limit: v.integer(req.query?.limit, { min: 1, max: 300, def: 100 }),
      }),
    });
  } catch (err) { next(err); }
});

/**
 * ساخت کد و — اگر ایمیل داده شده باشد — فرستادنش.
 *
 * کدِ خام فقط همین یک بار در پاسخ می‌آید. بعد از این، حتی خودِ سرور هم
 * نمی‌تواند نشانش بدهد؛ پس مدیر یا همان لحظه برش می‌دارد یا می‌گذارد
 * ایمیل کارش را بکند.
 */
router.post('/vip-codes', async (req, res, next) => {
  try {
    const email = v.text(req.body?.email, { max: 160 });
    if (email && !email.includes('@')) return next(badRequest('نشانی ایمیل درست نیست', 'bad_email'));
    //  شمارهٔ موبایل ⇒ کد همان لحظه پیامک می‌شود (همان سرویسِ کدِ ورود)
    const phone = v.text(req.body?.phone, { max: 30 }).replace(/[\s-]/g, '');
    if (phone && !/^\+?\d{7,15}$/.test(phone)) return next(badRequest('شمارهٔ موبایل درست نیست', 'bad_phone'));

    const { code, row } = await vip.create({
      plan: v.text(req.body?.plan, { max: 20 }) || 'custom',
      days: req.body?.days === undefined || req.body?.days === null || req.body?.days === ''
        ? null : v.integer(req.body.days, { field: 'روزها', min: 1, max: 3650 }),
      features: Array.isArray(req.body?.features) ? req.body.features : [],
      maxDevices: v.integer(req.body?.maxDevices, { field: 'تعداد دستگاه', min: 1, max: 100, def: 10 }),
      note: v.text(req.body?.note, { max: 300 }),
      email: email ? email.toLowerCase() : '',
      phone,
      tenantId: req.body?.stationId ? v.id(req.body.stationId) : null,
      expiresInDays: v.integer(req.body?.expiresInDays, { field: 'مهلت', min: 0, max: 365, def: 30 }),
      createdBy: req.admin.id,
    });

    let finalRow = email ? await vip.mail(row.id, code, { appName: 'پمپ' }) : vip.shape(row);
    if (phone) finalRow = await vip.sms(row.id, code, { appName: 'پمپ' });

    await audit.log({
      actorType: 'admin', userId: req.admin.id, action: 'admin.pump_vip_code_created',
      targetType: 'vip_code', targetId: row.id,
      detail: { plan: row.plan, days: row.days, email: email ? 'yes' : 'no', sms: phone ? 'yes' : 'no' },
    });

    res.status(201).json({
      code,                       // فقط همین یک بار
      vipCode: finalRow,
      emailStatus: finalRow.emailStatus,
      emailError: finalRow.emailError,
      smsStatus: finalRow.smsStatus,
      smsError: finalRow.smsError,
    });
  } catch (err) { next(err); }
});

router.post('/vip-codes/:id/revoke', async (req, res, next) => {
  try {
    const row = await vip.revoke(v.id(req.params.id));
    await audit.log({
      actorType: 'admin', userId: req.admin.id,
      action: 'admin.pump_vip_code_revoked', targetId: row.id,
    });
    res.json({ vipCode: row });
  } catch (err) { next(err); }
});

/* ==========================================================
   افراد — چه کسانی به پمپ‌ها وصل‌اند و حالِ اشتراکشان چیست

   خواستهٔ صاحب مخزن: «ببینم افراد رو، اشتراک‌هاشون و غیره؛ بخشِ
   فروشگاه خیلی تکمیل است، شبیه همون باشه.»

   ⚠️ همتای ‎/admin/users‎ی بخشِ دکان است، ولی از درِ ‎station_members‎
   می‌آید نه ‎shop_members‎ — دو دفترِ جدا، همان‌طور که باید بماند.
   کسی که فقط دکان دارد این‌جا پیدا نمی‌شود.
   ========================================================== */
router.get('/users', async (req, res, next) => {
  try {
    const limit = v.integer(req.query?.limit, { min: 1, max: 200, def: 50 });
    const offset = v.integer(req.query?.offset, { min: 0, max: 1e6, def: 0 });
    const q = v.text(req.query?.q, { max: 60 });
    const like = `%${q.toLowerCase()}%`;

    //  یک ردیف به ازای هر عضویت: یک نفر می‌تواند چند پمپ داشته باشد.
    const rows = await many(
      `SELECT u.id, u.name, u.email, u.phone, u.status, u.created_at, u.last_login_at,
              m.station_id, m.role, m.status AS member_status, m.created_at AS joined_at,
              st.name AS station_name, st.code AS station_code,
              sub.status AS sub_status, sub.plan AS sub_plan, sub.ends_at AS sub_ends_at
         FROM users u
         JOIN station_members m ON m.user_id = u.id
         JOIN stations st ON st.id = m.station_id
         LEFT JOIN LATERAL (
           SELECT status, plan, ends_at FROM station_subscriptions
            WHERE station_id = st.id ORDER BY ends_at DESC LIMIT 1
         ) sub ON true
        WHERE ($1 = '' OR lower(u.name) LIKE $2
                      OR lower(coalesce(u.email,'')) LIKE $2
                      OR coalesce(u.phone,'') LIKE $2
                      OR lower(st.name) LIKE $2
                      OR lower(st.code) LIKE $2)
        ORDER BY u.created_at DESC
        LIMIT $3 OFFSET $4`,
      [q, like, limit, offset]
    );

    const total = await one(
      `SELECT COUNT(*)::int n
         FROM users u
         JOIN station_members m ON m.user_id = u.id
         JOIN stations st ON st.id = m.station_id
        WHERE ($1 = '' OR lower(u.name) LIKE $2
                      OR lower(coalesce(u.email,'')) LIKE $2
                      OR coalesce(u.phone,'') LIKE $2
                      OR lower(st.name) LIKE $2
                      OR lower(st.code) LIKE $2)`,
      [q, like]
    );

    res.json({ users: rows, total: total.n, limit, offset });
  } catch (err) { next(err); }
});

/* ==========================================================
   یک نگاه کلی — همان کارتِ بالای صفحه
   ========================================================== */
router.get('/stats', async (req, res, next) => {
  try {
    const s = await one(
      `SELECT
         (SELECT COUNT(*)::int FROM stations) AS stations,
         (SELECT COUNT(*)::int FROM stations WHERE status='active') AS active_stations,
         (SELECT COUNT(*)::int FROM station_subscriptions
           WHERE status='active' AND ends_at > $1) AS active_subs,
         (SELECT COUNT(*)::int FROM station_vip_codes WHERE status='active') AS open_codes,
         (SELECT COUNT(*)::int FROM station_files) AS files,
         (SELECT COUNT(*)::int FROM station_events) AS events`,
      [now()]
    );
    res.json({ stats: s, serverTime: now() });
  } catch (err) { next(err); }
});

module.exports = router;
