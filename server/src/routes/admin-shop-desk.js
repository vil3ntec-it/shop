'use strict';
/**
 * میزِ «فروشگاه» — یک تماس، همان شش عددی که صاحبِ سامانه خواست.
 * بندِ ۴.۲ سندِ ریمیکِ ریپوی `server`.
 *
 *   GET /api/admin/shop-desk/overview?days=7
 *
 * ── خواستهٔ صاحب سامانه ────────────────────────────────────────────────
 *   «اول داشبورد… تعدادِ مشتری‌ها، اشتراک‌دارها، آنلاین‌ها، کسانی که
 *    اشتراکشان رو به پایان است با ایمیل و وقتِ مانده، و پیام‌های
 *    پشتیبانی.»
 *
 * ── چرا یک مسیر، نه چهار ───────────────────────────────────────────────
 *  ⛔ **پنل هیچ عددی حساب نمی‌کند** — قاعدهٔ «یک دفترِ حساب». پس یا این
 *     شش عدد این‌جا جمع می‌شوند یا صفحهٔ پنل باید خودش جمع بزند، که
 *     همان دفترِ دوم است. و چهار رفت‌وبرگشت از تونل برای یک ردیف کارت
 *     دقیقاً همان فشاری است که گذرگاهِ زنده برداشت.
 *
 *  ⛔ **و این‌جا هیچ چیزی ذخیره نمی‌شود.** فقط `SELECT`. عددها از همان
 *     جدول‌هایی می‌آیند که بقیهٔ سامانه می‌خواند، پس هیچ‌وقت با آن‌ها
 *     دو صدا نمی‌شوند.
 *
 * ── و «آنلاین» یعنی چه ─────────────────────────────────────────────────
 *  ⚠️ `devices.last_seen_at` با **هر** درخواستِ احرازشده مهر می‌خورد
 *     (`middleware/auth.js`). پس «آنلاین» یعنی «دستگاهی از این دکان در
 *     `ONLINE_MS` دقیقهٔ گذشته با سرور حرف زده» — نه «برنامه‌اش باز
 *     است»، که سرور اصلاً نمی‌تواند بداند. خودِ پاسخ همین را می‌گوید
 *     (`onlineWithinMs`) تا کسی عددِ دیگری از آن نفهمد.
 */
const express = require('express');
const { one, many, now } = require('../db');
const support = require('../lib/support');
const subs = require('../lib/subscriptions');
const v = require('../lib/validate');
const { requireAdmin } = require('../middleware/auth');

const router = express.Router();
router.use(requireAdmin);

/** پنجرهٔ «آنلاین». ⚠️ عددش این‌جا نوشته شده و در پاسخ هم می‌آید. */
const ONLINE_MS = 10 * 60 * 1000;

router.get('/overview', async (req, res, next) => {
  try {
    const days = v.integer(req.query?.days, { field: 'روزها', min: 1, max: 90, def: 7 });
    const at = now();

    const shops = await one(`SELECT COUNT(*)::int n FROM shops WHERE status <> 'deleted'`);
    const owners = await one(
      `SELECT COUNT(DISTINCT owner_user_id)::int n FROM shops
        WHERE status <> 'deleted' AND owner_user_id IS NOT NULL AND owner_user_id <> ''`
    );
    const subscribed = await one(
      `SELECT COUNT(DISTINCT shop_id)::int n FROM subscriptions
        WHERE status='active' AND ends_at > $1`, [at]
    );

    /*
     *  «آنلاین» = دکانی که دستگاهِ یکی از اعضایش تازه دیده شده.
     *  ⚠️ `DISTINCT m.shop_id` لازم است: یک نفر با سه دستگاه یک دکان است،
     *  نه سه تا — وگرنه عددِ آنلاین از شمارِ کلِ دکان‌ها بزرگ‌تر می‌شد.
     */
    const online = await one(
      `SELECT COUNT(DISTINCT m.shop_id)::int n
         FROM devices d
         JOIN shop_members m ON m.user_id = d.user_id AND m.status='active'
        WHERE d.status='active' AND d.last_seen_at IS NOT NULL AND d.last_seen_at >= $1`,
      [at - ONLINE_MS]
    );

    //  رو به پایان — با ایمیل و روزِ مانده، همان چیزی که خواسته شد
    const expiring = await subs.forApp('shop').expiringSoon({ withinDays: days, includeExpired: 0, limit: 200 });

    //  پیام‌های پشتیبانی: نخوانده‌ها و رشته‌های باز
    const openThreads = await one(
      `SELECT COUNT(*)::int n FROM support_threads WHERE status <> 'closed' AND app='shop'`
    );
    const threads = await support.list({ app: 'shop', status: 'open', limit: 10 });

    res.json({
      app: 'shop',
      serverTime: at,
      onlineWithinMs: ONLINE_MS,
      counts: {
        shops: shops.n,
        customers: owners.n,
        subscribed: subscribed.n,
        online: online.n,
        expiring: expiring.length,
        supportOpen: openThreads.n,
        supportUnread: await support.unreadForAdmin(),
      },
      expiring: expiring.map((r) => ({
        subscriptionId: r.subscriptionId, tenantId: r.tenantId, tenantName: r.tenantName,
        ownerName: r.ownerName || '', ownerEmail: r.ownerEmail || '',
        plan: r.plan || '', endsAt: Number(r.endsAt) || 0, daysLeft: Number(r.daysLeft),
      })),
      support: threads.threads || threads,
      days,
    });
  } catch (err) { next(err); }
});

/**
 * اشتراک‌های دکان‌ها در **سه گروه** — بندِ ۴.۳.
 *
 * ⛔ گروه‌بندی این‌جا می‌شود، نه در مرورگر: «دارد / ندارد / تمام‌شده»
 * از حالِ واقعیِ اشتراک درمی‌آید (`subs.stateOf`) و همان قاعده‌ای است
 * که خودِ برنامه با آن قفل باز می‌کند. دو جای تصمیم یعنی روزی پنل
 * «دارد» می‌گوید و برنامه «تمام شده».
 */
router.get('/groups', async (req, res, next) => {
  try {
    const at = now();
    const q = String(req.query?.q || '').trim().toLowerCase().slice(0, 60);
    /*
     *  ⚠️ همهٔ ستون‌هایی که `subs.stateOf` می‌خواند باید این‌جا باشند —
     *  `starts_at` و `grace_days` هم. بارِ اول جا افتاده بودند و
     *  `stateOf` برای هر دکان `active:false` می‌داد، یعنی هر اشتراکِ
     *  زنده در گروهِ «تمام‌شده» می‌نشست. سنجهٔ ۲ همین را گرفت.
     */
    const rows = await many(
      `SELECT s.id, s.name, s.created_at, u.name AS owner_name, u.email AS owner_email, u.phone AS owner_phone,
              sub.id AS sub_id, sub.plan, sub.status AS sub_status,
              sub.starts_at, sub.ends_at, sub.grace_days, sub.features, sub.max_devices, sub.note
         FROM shops s
         LEFT JOIN users u ON u.id = s.owner_user_id
         LEFT JOIN LATERAL (
           SELECT * FROM subscriptions x WHERE x.shop_id = s.id ORDER BY x.created_at DESC LIMIT 1
         ) sub ON true
        WHERE s.status <> 'deleted'
          AND ($1 = '' OR lower(s.name) LIKE '%' || $1 || '%'
               OR lower(coalesce(u.email,'')) LIKE '%' || $1 || '%'
               OR lower(coalesce(u.name,''))  LIKE '%' || $1 || '%')
        ORDER BY s.created_at DESC LIMIT 500`,
      [q]
    );

    const groups = { has: [], none: [], expired: [] };
    for (const r of rows) {
      //  ⛔ شیءِ صریح، نه خودِ ردیف: نامِ ستونِ `status` در ردیف مالِ
      //  **دکان** است و دادنِ ردیفِ خام به `stateOf` یعنی حالِ دکان به
      //  جای حالِ اشتراک خوانده می‌شود.
      const state = r.sub_id
        ? subs.stateOf({
          id: r.sub_id, plan: r.plan, status: r.sub_status,
          starts_at: r.starts_at, ends_at: r.ends_at, grace_days: r.grace_days,
          features: r.features, max_devices: r.max_devices, note: r.note,
        }, at)
        : null;
      const item = {
        tenantId: r.id, tenantName: r.name || '', createdAt: Number(r.created_at) || 0,
        ownerName: r.owner_name || '', ownerEmail: r.owner_email || '', ownerPhone: r.owner_phone || '',
        subscriptionId: r.sub_id || '', plan: r.plan || '',
        status: state ? state.status : 'none',
        endsAt: Number(r.ends_at) || 0,
        daysLeft: state ? Number(state.daysLeft) : 0,
      };
      //  ⚠️ «هیچ‌وقت اشتراکی نداشته» با «داشت و تمام شد» یکی نیست، و
      //  صاحبِ سامانه صریح سه گروه خواست.
      if (!r.sub_id) groups.none.push(item);
      else if (state && state.active) groups.has.push(item);
      else groups.expired.push(item);
    }

    res.json({ app: 'shop', serverTime: at, groups,
      counts: { has: groups.has.length, none: groups.none.length, expired: groups.expired.length } });
  } catch (err) { next(err); }
});

module.exports = router;
