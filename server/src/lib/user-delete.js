'use strict';
/**
 * حذفِ **کاملِ** یک حساب — «از ریشه».
 *
 * ⛔ گزارشِ صاحب سامانه (۱۴۰۵/۰۷/۱۳): «نمی‌تونم حسابِ کاربری رو حذف کنم از
 * ریشه.» تا امروز فقط «تعلیق» بود (`POST /users/:id/status`).
 *
 * چه می‌رود — همه در **یک** تراکنش (یا همه، یا هیچ):
 *   • خودِ حساب، نشست‌ها و توکن‌هایش، راه‌های ورودش (`user_identities`)،
 *     دستگاه‌ها، عضویت‌ها، پیوندهای تلگرام، کدهای ورودِ همان ایمیل و همان شماره؛
 *   • **پمپ‌ها و دکان‌هایی که مالِ خودِ همین حساب‌اند** (صاحبشان است) با
 *     همهٔ دادهٔ ابری‌شان — اشتراک، دستگاه، رویداد، گفت‌وگو، دفترِ همگام‌سازی؛
 *   • ردیف‌هایی که کلیدِ خارجی ندارند و فقط شناسه را نگه داشته‌اند (پشتیبان،
 *     پوش، پشتیبانی، خطا، بازدید…) — وگرنه دادهٔ شخصی بی‌صاحب می‌ماند.
 * بعد از تراکنش: پوشهٔ پشتیبان‌های ابریِ همان پمپ‌ها/دکان‌ها از دیسک.
 *
 * Sync v3 (`sync_v3_*`): همان قاعدهٔ دفترِ v1 — دفترِ دکانی که مالِ خودِ حساب
 * است کامل می‌رود (op، ردیف، تعارض، دستگاه و حالِ پل‌ها)؛ دکانِ کسِ دیگری که
 * این حساب فقط عضوش بوده دست نمی‌خورد (دادهٔ مشترکِ دکان است، و بریدنِ نیمه‌اش
 * cursorِ پل‌ها و نشانِ «بی حلقه» را می‌شکست) — جز شناسهٔ خودِ این کاربر در
 * `sync_v3_conflicts.resolved_by` که خالی می‌شود، و شناسهٔ دستگاه‌هایش در
 * `sync_v3_rows.created_by` که به `DELETED_CREATOR` می‌رود (پایین: چرا خالی نه).
 *
 * چه **نمی‌رود**، عمداً:
 *   • `sub_payments` — دفترِ پولِ صاحبِ سامانه است (درآمد)، نه دادهٔ کاربر.
 *   • `audit_logs` — خودِ این حذف همان‌جا ثبت می‌شود.
 *   • پمپ یا دکانِ **کسِ دیگری** که این حساب فقط عضوش بوده — فقط عضویت می‌رود.
 *
 * ⚠️ `preview` هیچ چیزی نمی‌نویسد: همان شمارش‌ها را می‌دهد تا مدیر پیش از
 * زدنِ دکمه ببیند چه چیزی می‌رود.
 */
const fsp = require('node:fs/promises');
const path = require('node:path');
const { one, many, tx } = require('../db');
const config = require('../config');
const backups = require('./account-backups');
const v = require('./validate');

/**
 * همهٔ جدول‌های `sync_v3_*` که `account_id` دارند — از کاتالوگ، نه فهرستِ دستی:
 * جدولِ تازه (مثلاً پلِ v3 ⇒ v1ِ مهاجرتِ ۰۹۳) خودبه‌خود پاک می‌شود و جدولی که
 * هنوز ساخته نشده لازم نیست. نام فقط از کاتالوگ می‌آید و با الگو سنجیده می‌شود.
 */
async function syncV3Tables(c) {
  const r = await c.query(
    `SELECT DISTINCT table_name AS t FROM information_schema.columns
      WHERE table_schema = current_schema() AND column_name = 'account_id' AND table_name LIKE 'sync\\_v3\\_%'
      ORDER BY 1`);
  return r.rows.map((x) => x.t).filter((t) => /^sync_v3_[a-z0-9_]+$/.test(t));
}

/*
 * ── تأیید با چه چیزی ─────────────────────────────────────────────────
 * دکمه‌ای که با یک کلیکِ اشتباه کلِ پمپِ کسی را می‌برد نباید بی تایپِ نشانیِ
 * **همان** حساب کار کند. تا دیروز آن نشانی فقط ایمیل بود؛ پس شاگردِ ورود‌با‌کد
 * (نه ایمیل، نه شماره) و حسابِ فقط‌شماره هرگز حذف نمی‌شدند. حالا همان قاعده
 * با نشانیِ واقعیِ هر حساب، به همین ترتیب:
 *   ایمیل (بی حساسیت به حروف) ⇐ شماره (در هر قالبی که `validate.phone` همان
 *   شماره بخواند) ⇐ شناسهٔ خودِ حساب (`usr_…`، دقیق).
 * حسابِ ایمیل‌دار فقط با ایمیل؛ شناسه یا شماره جای آن را نمی‌گیرد.
 */
function confirmWith(user) {
  if (String(user.email || '').trim()) return 'email';
  if (String(user.phone || '').trim()) return 'phone';
  return 'id';
}

function confirmed(user, typed) {
  const t = String(typed || '').trim();
  if (!t) return false;
  switch (confirmWith(user)) {
    case 'email': return t.toLowerCase() === String(user.email).trim().toLowerCase();
    case 'phone': {
      let p = null;
      try { p = v.phone(t); } catch { return false; }
      return p === String(user.phone).trim();
    }
    default: return t === user.id;
  }
}

/*
 * ── `sync_v3_rows.created_by` در دکانِ دیگران ──────────────────────────
 * شناسهٔ **دستگاهی** است که ردیف را ساخت (نه شناسهٔ کاربر) و فقط یک‌جا خوانده
 * می‌شود: «شاگرد فقط ردیفی را حذف کند که خودش ساخته» (`sync-v3.authorize`).
 * ⚠️ خالی‌کردنش اجازه را **باز** می‌کرد (ردیفِ بی‌سازنده را هر شاگردی حذف
 * می‌کند). پس به نشانی می‌رود که هیچ دستگاهی نمی‌تواند داشته باشد — `#` در
 * شناسهٔ دستگاه (`validate.id`) پذیرفته نیست — و همان رفتارِ امروز می‌ماند:
 * صاحب/مدیر حذف می‌کنند، شاگرد نه.
 */
const DELETED_CREATOR = '#deleted-user';

async function scope(userId) {
  const user = await one('SELECT id, email, phone, name, status FROM users WHERE id=$1', [userId]);
  if (!user) return null;
  const stations = await many(
    `SELECT s.id, s.name,
            (SELECT COUNT(*)::int FROM station_members m WHERE m.station_id=s.id AND m.status='active' AND m.user_id<>$1) AS others
       FROM stations s WHERE s.owner_user_id=$1`, [userId]);
  const shops = await many(
    `SELECT s.id, s.name,
            (SELECT COUNT(*)::int FROM shop_members m WHERE m.shop_id=s.id AND m.status='active' AND m.user_id<>$1) AS others
       FROM shops s WHERE s.owner_user_id=$1`, [userId]);
  const memberOf = await one(
    `SELECT
       (SELECT COUNT(*)::int FROM station_members m JOIN stations s ON s.id=m.station_id
         WHERE m.user_id=$1 AND s.owner_user_id<>$1) AS stations,
       (SELECT COUNT(*)::int FROM shop_members m JOIN shops s ON s.id=m.shop_id
         WHERE m.user_id=$1 AND s.owner_user_id<>$1) AS shops`, [userId]);
  return { user, stations, shops, memberOf };
}

/** چه چیزی خواهد رفت — بی نوشتن. */
async function preview(userId) {
  const s = await scope(userId);
  if (!s) return null;
  return {
    user: { id: s.user.id, email: s.user.email, name: s.user.name, status: s.user.status },
    //  پنل همین را از مدیر می‌خواهد: ایمیل، شماره یا شناسهٔ حساب
    confirmWith: confirmWith(s.user),
    stations: s.stations.map((x) => ({ id: x.id, name: x.name, otherMembers: x.others })),
    shops: s.shops.map((x) => ({ id: x.id, name: x.name, otherMembers: x.others })),
    memberOf: s.memberOf,
  };
}

/**
 * حذف. ⚠️ `confirm` باید نشانیِ همان حساب باشد (بالا: ایمیل ⇐ شماره ⇐ شناسه).
 * `confirmEmail` نامِ دیروزِ همین فیلد است و هنوز پذیرفته می‌شود.
 */
async function remove(userId, { confirm = '', confirmEmail = '' } = {}) {
  const s = await scope(userId);
  if (!s) return { ok: false, code: 'not_found' };
  if (!confirmed(s.user, confirm || confirmEmail)) {
    return { ok: false, code: 'confirm_mismatch', confirmWith: confirmWith(s.user) };
  }
  const email = String(s.user.email || '').trim().toLowerCase();
  const phone = String(s.user.phone || '').trim();
  const stationIds = s.stations.map((x) => x.id);
  const shopIds = s.shops.map((x) => x.id);
  const tenants = [...stationIds, ...shopIds];
  const accounts = [userId, ...tenants];

  const counts = {};
  await tx(async (c) => {
    const q = async (label, sql, params) => {
      const r = await c.query(sql, params);
      counts[label] = (counts[label] || 0) + (r.rowCount || 0);
    };
    //  ۰) دستگاه‌های همین حساب — ثبتِ ورود و opهای v1ش — پیش از هر حذفی؛
    //     برای `sync_v3_rows.created_by` در دکانِ دیگران (پایین)
    const uids = new Set();
    const seen = await c.query(
      `SELECT device_uid AS d FROM devices WHERE user_id=$1
       UNION SELECT device_id AS d FROM sync_operations WHERE user_id=$1`, [userId]);
    for (const { d } of seen.rows) {
      const raw = String(d || '').trim();
      if (!raw) continue;
      uids.add(raw);
      uids.add(raw.replace(/[^A-Za-z0-9_.-]/g, '-').slice(0, 64));   //  همان پاک‌سازیِ موتورِ v3ِ برنامه
    }

    //  ۱) ردیف‌های بی‌کلیدِ خارجی که به همین حساب یا پمپ/دکان‌هایش اشاره دارند
    const devIds = (await c.query(
      'SELECT id FROM station_devices WHERE station_id = ANY($1::text[])', [stationIds])).rows.map((r) => r.id);
    await q('tokens', 'DELETE FROM tokens WHERE subject_id = ANY($1::text[])', [[userId, ...devIds]]);
    await q('support', `DELETE FROM support_messages WHERE thread_id IN (
        SELECT id FROM support_threads WHERE user_id=$1 OR station_id = ANY($2::text[]) OR shop_id = ANY($3::text[]))`,
      [userId, stationIds, shopIds]);
    await q('support', 'DELETE FROM support_threads WHERE user_id=$1 OR station_id = ANY($2::text[]) OR shop_id = ANY($3::text[])',
      [userId, stationIds, shopIds]);
    await q('push', 'DELETE FROM push_tokens WHERE user_id=$1 OR station_id = ANY($2::text[]) OR shop_id = ANY($3::text[])',
      [userId, stationIds, shopIds]);
    await q('backups', 'DELETE FROM account_backups WHERE user_id=$1 OR tenant_id = ANY($2::text[])', [userId, tenants]);
    await q('sync', 'DELETE FROM sync_rows WHERE account_id = ANY($1::text[])', [accounts]);
    await q('sync', 'DELETE FROM oplog WHERE account_id = ANY($1::text[])', [accounts]);
    //  A16: نویسندهٔ درجِ برگشت (مهاجرتِ ۱۲۳) — دفترِ حساب‌های خودش، و نامِ خودش در دکانِ دیگران
    await q('sync', 'DELETE FROM sync_op_authors WHERE account_id = ANY($1::text[]) OR user_id = $2', [accounts, userId]);
    await q('sync', 'DELETE FROM sync_devices WHERE account_id = ANY($1::text[])', [accounts]);
    await q('sync', 'DELETE FROM sync_conflicts WHERE account_id = ANY($1::text[])', [accounts]);
    await q('sync', 'DELETE FROM sync_operations WHERE user_id=$1 OR shop_id = ANY($2::text[])', [userId, shopIds]);
    //  Sync v3: دفترِ دکان‌های خودش؛ در دکانِ دیگران فقط نامِ خودش از «بستنِ تعارض»
    const v3 = await syncV3Tables(c);
    for (const t of v3) await q('sync_v3', `DELETE FROM "${t}" WHERE account_id = ANY($1::text[])`, [accounts]);
    if (v3.includes('sync_v3_conflicts')) {
      await q('sync_v3_anonymized', "UPDATE sync_v3_conflicts SET resolved_by='' WHERE resolved_by=$1", [userId]);
    }
    if (v3.includes('sync_v3_rows')) {
      //  فقط دکان‌هایی که عضوش بود؛ دستگاهی که عضوِ دیگرِ همان دکان هم با آن وارد
      //  شده (تبلتِ مشترکِ صندوق) مالِ اوست و دست نمی‌خورد
      await q('sync_v3_created_by', `UPDATE sync_v3_rows r SET created_by=$3
         WHERE r.app='shop' AND r.created_by = ANY($2::text[])
           AND r.account_id IN (SELECT shop_id FROM shop_members WHERE user_id=$1)
           AND NOT EXISTS (SELECT 1 FROM devices d
                 WHERE d.user_id<>$1
                   AND (d.device_uid=r.created_by
                        OR left(regexp_replace(d.device_uid, '[^A-Za-z0-9_.-]', '-', 'g'), 64)=r.created_by)
                   AND (d.user_id IN (SELECT m.user_id FROM shop_members m WHERE m.shop_id=r.account_id)
                        OR d.user_id = (SELECT s.owner_user_id FROM shops s WHERE s.id=r.account_id)))`,
      [userId, [...uids], DELETED_CREATOR]);
    }
    await q('codes', 'DELETE FROM station_vip_codes WHERE station_id = ANY($1::text[])', [stationIds]);
    await q('codes', 'DELETE FROM vip_codes WHERE shop_id = ANY($1::text[])', [shopIds]);
    //  کدهای ورود فقط با نشانیِ واقعیِ حساب — نشانیِ خالی هیچ ردیفی را نمی‌گیرد
    if (email) {
      await q('codes', 'DELETE FROM otp_codes WHERE lower(destination)=$1', [email]);
      await q('codes', 'DELETE FROM otp_outbox WHERE lower(email)=$1', [email]);
      await q('codes', 'DELETE FROM login_requests WHERE lower(email)=$1', [email]);
      await q('codes', 'DELETE FROM login_locks WHERE lower(email)=$1', [email]);
    }
    if (phone) await q('codes', 'DELETE FROM otp_codes WHERE destination=$1', [phone]);
    await q('other', 'DELETE FROM subscription_addons WHERE tenant_id = ANY($1::text[])', [tenants]);
    //  F0-14: پاسخِ ذخیره‌شدهٔ کلیدِ یکتایی رونوشتِ اشتراک است — با حساب می‌رود
    await q('other', 'DELETE FROM idempotency_keys WHERE tenant_id = ANY($1::text[])', [tenants]);
    await q('other', 'DELETE FROM notice_deliveries WHERE user_id=$1 OR tenant_id = ANY($2::text[])', [userId, tenants]);
    await q('other', 'DELETE FROM discount_uses WHERE user_id=$1', [userId]);
    await q('other', 'DELETE FROM client_errors WHERE user_id=$1 OR account_id = ANY($2::text[]) OR tenant_id = ANY($2::text[])', [userId, accounts]);
    await q('other', 'DELETE FROM app_visitors WHERE user_id=$1 OR station_id = ANY($2::text[]) OR shop_id = ANY($3::text[])', [userId, stationIds, shopIds]);
    await q('other', 'DELETE FROM device_locations WHERE user_id=$1', [userId]);
    //  ۲) پمپ‌ها و دکان‌های خودش — بقیه با ON DELETE CASCADE می‌روند
    await q('stations', 'DELETE FROM stations WHERE id = ANY($1::text[])', [stationIds]);
    await q('shops', 'DELETE FROM shops WHERE id = ANY($1::text[])', [shopIds]);
    //  ۳) خودِ حساب — دستگاه، عضویت، راهِ ورود، تلگرام… با CASCADE
    await q('users', 'DELETE FROM users WHERE id=$1', [userId]);
  });

  //  ⛔ S78: پس از تراکنش — سوکت‌های زندهٔ خودش و همهٔ دستگاه‌های دکان‌ها/پمپ‌های رفته همان لحظه بسته
  const live = require('./sync-v1-live');
  await live.accessChanged({ userId });
  for (const id of shopIds) await live.accessChanged({ accountKind: 'shop', accountId: id });
  for (const id of stationIds) await live.accessChanged({ accountKind: 'station', accountId: id });

  //  ۴) پوشهٔ پشتیبان‌های ابری روی دیسک — پس از تراکنش، و نرفتنش حذف را برنمی‌گرداند
  let files = 0;
  for (const [app, ids] of [['pump', stationIds], ['shop', shopIds]]) {
    for (const id of ids) {
      const dir = path.join(config.backup.dir, 'accounts', app, backups.safeTenant(id));
      try { await fsp.rm(dir, { recursive: true, force: true }); files++; } catch { /* نبود */ }
    }
  }
  return {
    ok: true,
    user: { id: s.user.id, email: s.user.email },
    stations: stationIds.length, shops: shopIds.length, backupFolders: files, rows: counts,
  };
}

module.exports = { preview, remove, syncV3Tables };
