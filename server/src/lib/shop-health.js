'use strict';
/**
 * سلامتِ یک دکان برای پنلِ مرکزی — `GET /api/admin/shops/:id/health` (فاز ۳، ماژولِ ۴.۲ ِ ممیزی).
 *
 * ⛔ **فقط دادهٔ واقعی.** هر عدد از همان جدولی می‌آید که خودِ همگام‌سازی می‌نویسد؛ هیچ چیز حدس زده یا
 *    ساخته نمی‌شود. نبودنِ داده = `null` (پنل «—» نشان می‌دهد)، نه صفر و نه «سالم».
 *    • همگام‌سازیِ v1: `sync_devices` (نسخهٔ برنامه، آخرین push/pull، صف، عقب‌ماندگی از `oplog`)
 *    • همگام‌سازیِ v3: `sync_v3_devices` (نسخه، آخرین push/pull، زمانِ آخرین خطا)
 *    • راهِ قدیم: `sync_operations` (آخرین push) و `devices.last_sync_at` ِ اعضا (نسخه ندارد ⇒ null)
 *    • خطاها: `client_errors` (شمار، نه متن — متن از `GET /api/admin/sync/errors`)
 *    • اشتراک: همان `entitlementOf` که درهای پولی می‌خوانند
 *    • حالت‌های مدیریتی: `lib/shop-holds.js`
 * ⛔ فراداده است، نه دفترِ دکان: هیچ ردیفِ فروش، مشتری، متنِ خطا یا IP بیرون نمی‌آید.
 */
const { one, many, now } = require('../db');
const holds = require('./shop-holds');

const DAY = 24 * 60 * 60 * 1000;
const n = (v) => (v === null || v === undefined || v === '' ? null : Number(v));
const maxOf = (...xs) => {
  const vals = xs.map(n).filter((x) => x !== null && Number.isFinite(x) && x > 0);
  return vals.length ? Math.max(...vals) : null;
};

async function healthOf(shopId, at = now()) {
  const shop = await one('SELECT id, name, status, created_at FROM shops WHERE id=$1', [shopId]);
  if (!shop) return null;

  const v1 = await many(
    `SELECT device_id, app_version, schema_version, cursor, last_push_at, last_pull_at, last_op_at, last_seen_at, queued_count
       FROM sync_devices WHERE app='shop' AND account_kind='shop' AND account_id=$1
      ORDER BY last_seen_at DESC LIMIT 100`, [shopId]
  );
  const head = await one(
    `SELECT COALESCE(MAX(server_seq),0) AS head FROM oplog WHERE app='shop' AND account_kind='shop' AND account_id=$1`, [shopId]
  );
  const conflicts = await one(
    `SELECT COUNT(*)::int AS n FROM sync_conflicts
      WHERE app='shop' AND account_kind='shop' AND account_id=$1 AND restored_at IS NULL`, [shopId]
  );
  const v3 = await many(
    `SELECT device_id, app_version, last_push_at, last_pull_at, last_error_at, last_seen_at, queued
       FROM sync_v3_devices WHERE app='shop' AND account_kind='shop' AND account_id=$1
      ORDER BY last_seen_at DESC LIMIT 100`, [shopId]
  );
  const legacyOps = await one('SELECT MAX(created_at) AS at FROM sync_operations WHERE shop_id=$1', [shopId]);
  const legacyDev = await one(
    `SELECT COUNT(*)::int AS n, MAX(d.last_sync_at) AS at
       FROM devices d JOIN shop_members m ON m.user_id = d.user_id
      WHERE m.shop_id=$1 AND m.status='active' AND d.status='active' AND d.last_sync_at IS NOT NULL`, [shopId]
  );
  const errs = await one(
    `SELECT COUNT(*) FILTER (WHERE at > $2)::int AS d1, COUNT(*)::int AS d7, MAX(at) AS last_at
       FROM client_errors WHERE app='shop' AND at > $3 AND (account_id=$1 OR tenant_id=$1)`,
    [shopId, at - DAY, at - 7 * DAY]
  );
  const lastErr = n(errs?.last_at) === null ? null : await one(
    `SELECT app_version, version FROM client_errors
      WHERE app='shop' AND at=$2 AND (account_id=$1 OR tenant_id=$1) LIMIT 1`, [shopId, errs.last_at]
  );

  const headSeq = Number(head?.head || 0);
  const devices = [
    ...v1.map((d) => ({
      engine: 'v1', deviceId: d.device_id, appVersion: d.app_version || null,
      schemaVersion: Number(d.schema_version) || null,
      lastPushAt: n(d.last_push_at), lastPullAt: n(d.last_pull_at), lastSeenAt: n(d.last_seen_at) || null,
      queued: Number(d.queued_count) || 0, behind: Math.max(0, headSeq - Number(d.cursor || 0)),
    })),
    ...v3.map((d) => ({
      engine: 'v3', deviceId: d.device_id, appVersion: d.app_version || null, schemaVersion: null,
      lastPushAt: n(d.last_push_at), lastPullAt: n(d.last_pull_at), lastSeenAt: n(d.last_seen_at) || null,
      queued: Number(d.queued) || 0, behind: null, lastErrorAt: n(d.last_error_at),
    })),
  ];
  const versions = {};
  for (const d of devices) {
    const k = d.appVersion || '';
    versions[k] = (versions[k] || 0) + 1;
  }

  const ent = await require('./entitlement').entitlementOf(shopId, at);
  const sub = ent.subscription || {};
  const lastV1 = maxOf(...v1.flatMap((d) => [d.last_push_at, d.last_pull_at]));
  const lastV3 = maxOf(...v3.flatMap((d) => [d.last_push_at, d.last_pull_at]));
  const lastLegacy = maxOf(legacyOps?.at, legacyDev?.at);

  return {
    shop: { id: shop.id, name: shop.name, status: shop.status, createdAt: Number(shop.created_at) },
    at,
    state: await holds.stateOf(shopId),
    subscription: {
      source: ent.source,
      status: sub.status || 'none',
      phase: sub.phase || 'none',
      plan: sub.plan || null,
      endsAt: n(sub.endsAt) || null,
      graceEndsAt: n(sub.graceEndsAt) || null,
      daysLeft: sub.status && sub.status !== 'none' ? Number(sub.daysLeft) || 0 : null,
      endedByAdmin: sub.endedByAdmin === true,
      trial: ent.trial ? { active: !!ent.trial.active, endsAt: n(ent.trial.endsAt) || null } : null,
    },
    sync: {
      lastSyncAt: maxOf(lastV1, lastV3, lastLegacy),
      v1: {
        devices: v1.length,
        lastPushAt: maxOf(...v1.map((d) => d.last_push_at)),
        lastPullAt: maxOf(...v1.map((d) => d.last_pull_at)),
        queued: v1.reduce((s, d) => s + (Number(d.queued_count) || 0), 0),
        behindMax: v1.length ? Math.max(...devices.filter((d) => d.engine === 'v1').map((d) => d.behind)) : null,
        openConflicts: Number(conflicts?.n) || 0,
      },
      v3: {
        devices: v3.length,
        lastPushAt: maxOf(...v3.map((d) => d.last_push_at)),
        lastPullAt: maxOf(...v3.map((d) => d.last_pull_at)),
        lastErrorAt: maxOf(...v3.map((d) => d.last_error_at)),
      },
      legacy: {
        lastPushAt: maxOf(legacyOps?.at),
        devices: Number(legacyDev?.n) || 0,
        lastSyncAt: maxOf(legacyDev?.at),
      },
    },
    devices,
    //  `appVersion: null` = دستگاه نسخه‌اش را نگفته (یا راهِ قدیم)، نه «نسخهٔ صفر»
    versions: Object.entries(versions)
      .map(([v, c]) => ({ appVersion: v || null, devices: c }))
      .sort((a, b) => b.devices - a.devices),
    errors: {
      last24h: Number(errs?.d1) || 0,
      last7d: Number(errs?.d7) || 0,
      lastAt: n(errs?.last_at),
      lastAppVersion: lastErr ? (lastErr.app_version || lastErr.version || null) : null,
    },
  };
}

module.exports = { healthOf };
