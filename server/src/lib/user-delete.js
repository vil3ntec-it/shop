'use strict';
/**
 * حذفِ **کاملِ** یک حساب — «از ریشه».
 *
 * ⛔ گزارشِ صاحب سامانه (۱۴۰۵/۰۷/۱۳): «نمی‌تونم حسابِ کاربری رو حذف کنم از
 * ریشه.» تا امروز فقط «تعلیق» بود (`POST /users/:id/status`).
 *
 * چه می‌رود — همه در **یک** تراکنش (یا همه، یا هیچ):
 *   • خودِ حساب، نشست‌ها و توکن‌هایش، راه‌های ورودش (`user_identities`)،
 *     دستگاه‌ها، عضویت‌ها، پیوندهای تلگرام، کدهای ورودِ همان ایمیل؛
 *   • **پمپ‌ها و دکان‌هایی که مالِ خودِ همین حساب‌اند** (صاحبشان است) با
 *     همهٔ دادهٔ ابری‌شان — اشتراک، دستگاه، رویداد، گفت‌وگو، دفترِ همگام‌سازی؛
 *   • ردیف‌هایی که کلیدِ خارجی ندارند و فقط شناسه را نگه داشته‌اند (پشتیبان،
 *     پوش، پشتیبانی، خطا، بازدید…) — وگرنه دادهٔ شخصی بی‌صاحب می‌ماند.
 * بعد از تراکنش: پوشهٔ پشتیبان‌های ابریِ همان پمپ‌ها/دکان‌ها از دیسک.
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

async function scope(userId) {
  const user = await one('SELECT id, email, name, status FROM users WHERE id=$1', [userId]);
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
    stations: s.stations.map((x) => ({ id: x.id, name: x.name, otherMembers: x.others })),
    shops: s.shops.map((x) => ({ id: x.id, name: x.name, otherMembers: x.others })),
    memberOf: s.memberOf,
  };
}

/**
 * حذف. ⚠️ `confirmEmail` باید دقیقاً ایمیلِ همان حساب باشد — دکمه‌ای که با
 * یک کلیکِ اشتباه کلِ پمپِ کسی را می‌برد نباید بی تایپِ ایمیل کار کند.
 */
async function remove(userId, { confirmEmail = '' } = {}) {
  const s = await scope(userId);
  if (!s) return { ok: false, code: 'not_found' };
  const email = String(s.user.email || '').trim().toLowerCase();
  if (!email || String(confirmEmail || '').trim().toLowerCase() !== email) {
    return { ok: false, code: 'confirm_mismatch' };
  }
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
    await q('sync', 'DELETE FROM sync_devices WHERE account_id = ANY($1::text[])', [accounts]);
    await q('sync', 'DELETE FROM sync_conflicts WHERE account_id = ANY($1::text[])', [accounts]);
    await q('sync', 'DELETE FROM sync_operations WHERE user_id=$1 OR shop_id = ANY($2::text[])', [userId, shopIds]);
    await q('codes', 'DELETE FROM station_vip_codes WHERE station_id = ANY($1::text[])', [stationIds]);
    await q('codes', 'DELETE FROM vip_codes WHERE shop_id = ANY($1::text[])', [shopIds]);
    await q('codes', 'DELETE FROM otp_codes WHERE lower(destination)=$1', [email]);
    await q('codes', 'DELETE FROM otp_outbox WHERE lower(email)=$1', [email]);
    await q('codes', 'DELETE FROM login_requests WHERE lower(email)=$1', [email]);
    await q('codes', 'DELETE FROM login_locks WHERE lower(email)=$1', [email]);
    await q('other', 'DELETE FROM subscription_addons WHERE tenant_id = ANY($1::text[])', [tenants]);
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

module.exports = { preview, remove };
