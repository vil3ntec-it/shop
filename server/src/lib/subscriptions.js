'use strict';
/**
 * اشتراک — به دکان وصل است، نه به یک نفر.
 *
 * همه‌ی اعضای دکان از همان یک اشتراک استفاده می‌کنند؛ لازم نیست برای
 * هر شاگرد جدا اشتراک خرید.
 *
 * زمان همیشه از ساعت سرور خوانده می‌شود. عوض کردن تاریخ گوشی هیچ اثری
 * روی تمدید یا پایان اشتراک ندارد.
 */
const { query, one, many, newId, now } = require('../db');
const { badRequest, notFound } = require('../middleware/errors');
const { sanitizeFeatures } = require('./features');
const plans = require('./plans');

const DAY = 24 * 60 * 60 * 1000;

/** اشتراک زنده‌ی دکان (active/suspended/pending) — یا null. */
async function liveOf(shopId) {
  return one(
    `SELECT * FROM subscriptions
      WHERE shop_id=$1 AND status IN ('active','suspended','pending')
      ORDER BY created_at DESC LIMIT 1`,
    [shopId]
  );
}

/**
 * تازه‌ترین اشتراک دکان، حتی اگر تمام شده باشد.
 * برای نمایش وضعیت لازم است: کاربر باید «اشتراک تمام شد» ببیند،
 * نه «اشتراکی وجود ندارد».
 */
async function latestOf(shopId) {
  return one(
    `SELECT * FROM subscriptions WHERE shop_id=$1
      ORDER BY (status IN ('active','suspended','pending')) DESC, ends_at DESC, created_at DESC
      LIMIT 1`,
    [shopId]
  );
}

async function historyOf(shopId) {
  return many('SELECT * FROM subscriptions WHERE shop_id=$1 ORDER BY created_at DESC LIMIT 50', [shopId]);
}

/** وضعیت اشتراک با ساعت سرور. */
function stateOf(sub, at = now()) {
  if (!sub) return { status: 'none', active: false, endsAt: 0, startsAt: 0, daysLeft: 0, plan: '' };
  const endsAt = Number(sub.ends_at);
  const graceEnd = endsAt + (sub.grace_days || 0) * DAY;
  let status = sub.status;
  if (status === 'active' && at > graceEnd) status = 'expired';
  const active = status === 'active' && at >= Number(sub.starts_at) && at <= graceEnd;
  return {
    id: sub.id,
    plan: sub.plan,
    status,
    active,
    startsAt: Number(sub.starts_at),
    endsAt,
    graceEndsAt: graceEnd,
    daysLeft: Math.max(0, Math.ceil((graceEnd - at) / DAY)),
    features: Array.isArray(sub.features) ? sub.features : [],
    maxDevices: sub.max_devices,
    note: sub.note || '',
  };
}

/** بستن اشتراک‌هایی که مهلتشان تمام شده — با ساعت سرور. */
async function expireDue(at = now()) {
  const r = await query(
    `UPDATE subscriptions
        SET status='expired', updated_at=$1
      WHERE status='active'
        AND (ends_at + (grace_days * $2::bigint)) < $1`,
    [at, DAY]
  );
  return r.rowCount;
}

/**
 * ثبت یک سطر در تاریخچه.
 *
 * هرگز جلوی کار اصلی را نمی‌گیرد: اگر نوشتن تاریخچه شکست بخورد،
 * اشتراک کاربر نباید صادر نشود. خطا در لاگ می‌ماند.
 */
async function logChange(row) {
  try {
    await query(
      `INSERT INTO subscription_history
         (id, subscription_id, shop_id, action, plan, prev_status, new_status,
          prev_ends_at, new_ends_at, actor, note, created_at)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12)`,
      [
        newId('sbh'), row.subscriptionId, row.shopId, row.action, row.plan || '',
        row.prevStatus || '', row.newStatus || '',
        row.prevEndsAt ?? null, row.newEndsAt ?? null,
        row.actor || '', row.note || '', now(),
      ]
    );
  } catch (err) {
    console.error('[subscriptions] تاریخچه ثبت نشد:', err.message);
  }
}

/** دفتر تغییرهای اشتراک یک دکان، تازه‌ترین اول */
async function changeLog(shopId, limit = 50) {
  return many(
    `SELECT * FROM subscription_history WHERE shop_id=$1
      ORDER BY created_at DESC LIMIT $2`,
    [shopId, Math.min(Number(limit) || 50, 200)]
  );
}

/**
 * صدور یا تمدید اشتراک برای یک دکان.
 * اگر اشتراک زنده‌ای باشد، از پایان همان ادامه پیدا می‌کند تا روزهای
 * باقی‌مانده از بین نرود.
 */
async function grant(shopId, { plan = 'custom', days = null, startsAt = null, endsAt = null,
  features = [], maxDevices = 10, graceDays = 0, note = '', createdBy = '' } = {}) {

  const shop = await one('SELECT id FROM shops WHERE id=$1', [shopId]);
  if (!shop) throw notFound('دکان پیدا نشد', 'shop_not_found');

  const t = now();
  const existing = await liveOf(shopId);
  const base = existing && Number(existing.ends_at) > t ? Number(existing.ends_at) : t;

  let start = startsAt ? Number(startsAt) : t;
  let end;
  if (endsAt) {
    end = Number(endsAt);
  } else if (days) {
    end = base + Number(days) * DAY;
    start = existing ? Number(existing.starts_at) : start;
  } else {
    const p = await plans.getPlan(plan);
    if (!p || !p.amount || !p.unit) throw badRequest('مدت اشتراک مشخص نیست', 'missing_duration');
    end = plans.endOfPeriod(base, p.amount, p.unit);
    start = existing ? Number(existing.starts_at) : start;
    if (!features.length && Array.isArray(p.features) && p.features.length) features = p.features;
    maxDevices = maxDevices || p.max_devices;
  }
  if (!Number.isFinite(end) || end <= t) throw badRequest('تاریخ پایان اشتراک معتبر نیست');

  const clean = sanitizeFeatures(features);

  if (existing) {
    const row = await one(
      `UPDATE subscriptions
          SET plan=$2, status='active', starts_at=$3, ends_at=$4, features=$5::jsonb,
              max_devices=$6, grace_days=$7, note=$8, updated_at=$9, created_by=$10
        WHERE id=$1 RETURNING *`,
      [existing.id, plan, start, end, JSON.stringify(clean), maxDevices, graceDays, note, t, createdBy]
    );
    await logChange({
      subscriptionId: row.id, shopId, action: 'renew', plan,
      prevStatus: existing.status, newStatus: row.status,
      prevEndsAt: Number(existing.ends_at), newEndsAt: Number(row.ends_at),
      actor: createdBy, note,
    });
    return row;
  }
  const row = await one(
    `INSERT INTO subscriptions (id, shop_id, plan, status, starts_at, ends_at, features, max_devices, grace_days, note, created_at, updated_at, created_by)
     VALUES ($1,$2,$3,'active',$4,$5,$6::jsonb,$7,$8,$9,$10,$10,$11) RETURNING *`,
    [newId('sub'), shopId, plan, start, end, JSON.stringify(clean), maxDevices, graceDays, note, t, createdBy]
  );
  await logChange({
    subscriptionId: row.id, shopId, action: 'grant', plan,
    newStatus: row.status, newEndsAt: Number(row.ends_at),
    actor: createdBy, note,
  });
  return row;
}

async function setStatus(subscriptionId, status, by = '') {
  if (!['active', 'suspended', 'cancelled', 'expired', 'pending'].includes(status)) {
    throw badRequest('وضعیت اشتراک معتبر نیست');
  }
  const before = await one('SELECT * FROM subscriptions WHERE id=$1', [subscriptionId]);
  const row = await one(
    'UPDATE subscriptions SET status=$2, updated_at=$3, created_by=COALESCE(NULLIF($4,\'\'), created_by) WHERE id=$1 RETURNING *',
    [subscriptionId, status, now(), by]
  );
  if (!row) throw notFound('اشتراک پیدا نشد', 'subscription_not_found');
  await logChange({
    subscriptionId: row.id, shopId: row.shop_id, action: 'status', plan: row.plan,
    prevStatus: before ? before.status : '', newStatus: row.status,
    prevEndsAt: before ? Number(before.ends_at) : null, newEndsAt: Number(row.ends_at),
    actor: by,
  });
  return row;
}

module.exports = { liveOf, latestOf, historyOf, changeLog, stateOf, expireDue, grant, setStatus, DAY };
