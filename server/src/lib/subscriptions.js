'use strict';
/**
 * اشتراک — به دکان (یا پمپ) وصل است، نه به یک نفر.
 *
 * همه‌ی اعضای دکان از همان یک اشتراک استفاده می‌کنند؛ لازم نیست برای
 * هر شاگرد جدا اشتراک خرید.
 *
 * زمان همیشه از ساعت سرور خوانده می‌شود. عوض کردن تاریخ گوشی هیچ اثری
 * روی تمدید یا پایان اشتراک ندارد.
 *
 * ── یک منطق، دو دفتر ───────────────────────────────────────────────
 * بخشِ پمپ‌بنزین جدول‌های خودش را دارد ولی حسابش را همین‌جا می‌کند.
 * `build(T)` همین توابع را روی جدول‌های آن بخش می‌بندد؛ `T` از
 * `lib/tenancy.js` می‌آید و جای دیگری ساخته نمی‌شود.
 *
 * صادراتِ پیش‌فرضِ این پرونده همان بخشِ دکان است، پس هر کدی که از قبل
 * `subs.grant(shopId, …)` می‌نوشت دست‌نخورده کار می‌کند.
 */
const { query, one, many, newId, now } = require('../db');
const { badRequest, notFound } = require('../middleware/errors');
const { sanitizeFeatures } = require('./features');
const plans = require('./plans');
const tenancy = require('./tenancy');

const DAY = 24 * 60 * 60 * 1000;

/** وضعیت اشتراک با ساعت سرور. برای هر دو بخش یکی است، پس بیرونِ build. */
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

/**
 * تاریخ شروعی که باید نگه داشته شود.
 *
 * تمدیدِ اشتراکِ زنده، شروع را دست نمی‌زند. ولی اگر اشتراک تمام شده باشد
 * این یک اشتراکِ تازه است و از همین حالا شروع می‌شود.
 */
function if_live(existing, at) {
  if (existing && Number(existing.ends_at) > at) return Number(existing.starts_at);
  return null;
}

/**
 * بستنِ همین منطق روی جدول‌های یک بخش.
 * @param {object} T یکی از ثابت‌های lib/tenancy.js
 */
function build(T) {
  const TBL = T.subsTable;
  const HIST = T.historyTable;
  const TEN = T.tenantTable;
  const KEY = T.tenantKey;

  /** اشتراک زنده‌ی دکان (active/suspended/pending) — یا null. */
  async function liveOf(tenantId) {
    return one(
      `SELECT * FROM ${TBL}
        WHERE ${KEY}=$1 AND status IN ('active','suspended','pending')
        ORDER BY created_at DESC LIMIT 1`,
      [tenantId]
    );
  }

  /**
   * تازه‌ترین اشتراک دکان، حتی اگر تمام شده باشد.
   * برای نمایش وضعیت لازم است: کاربر باید «اشتراک تمام شد» ببیند،
   * نه «اشتراکی وجود ندارد».
   */
  async function latestOf(tenantId) {
    return one(
      `SELECT * FROM ${TBL} WHERE ${KEY}=$1
        ORDER BY (status IN ('active','suspended','pending')) DESC, ends_at DESC, created_at DESC
        LIMIT 1`,
      [tenantId]
    );
  }

  async function historyOf(tenantId) {
    return many(`SELECT * FROM ${TBL} WHERE ${KEY}=$1 ORDER BY created_at DESC LIMIT 50`, [tenantId]);
  }

  /** بستن اشتراک‌هایی که مهلتشان تمام شده — با ساعت سرور. */
  async function expireDue(at = now()) {
    const r = await query(
      `UPDATE ${TBL}
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
        `INSERT INTO ${HIST}
           (id, subscription_id, ${KEY}, action, plan, prev_status, new_status,
            prev_ends_at, new_ends_at, actor, note, created_at)
         VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12)`,
        [
          newId('sbh'), row.subscriptionId, row.tenantId, row.action, row.plan || '',
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
  async function changeLog(tenantId, limit = 50) {
    return many(
      `SELECT * FROM ${HIST} WHERE ${KEY}=$1
        ORDER BY created_at DESC LIMIT $2`,
      [tenantId, Math.min(Number(limit) || 50, 200)]
    );
  }

  /**
   * صدور یا تمدید اشتراک برای یک دکان.
   * اگر اشتراک زنده‌ای باشد، از پایان همان ادامه پیدا می‌کند تا روزهای
   * باقی‌مانده از بین نرود.
   */
  async function grant(tenantId, { plan = 'custom', days = null, startsAt = null, endsAt = null,
    features = [], maxDevices = 10, graceDays = 0, note = '', createdBy = '' } = {}) {

    const tenant = await one(`SELECT id FROM ${TEN} WHERE id=$1`, [tenantId]);
    if (!tenant) throw notFound(T.notFoundMessage, T.notFoundCode);

    const t = now();
    const existing = await liveOf(tenantId);
    const base = existing && Number(existing.ends_at) > t ? Number(existing.ends_at) : t;

    let start = startsAt ? Number(startsAt) : t;
    let end;
    if (endsAt) {
      end = Number(endsAt);
    } else if (days) {
      end = base + Number(days) * DAY;
      //  اشتراکِ زنده: شروع همان شروعِ قبلی می‌ماند و فقط پایان جلو می‌رود.
      //  اشتراکِ تمام‌شده: از همین حالا شروع می‌شود، نه از تاریخِ قدیمی —
      //  وگرنه در گزارش‌ها اشتراکی دیده می‌شد که ماه‌ها پیش شروع شده.
      start = if_live(existing, t) ?? start;
    } else {
      const p = await plans.getPlan(plan, T.app);
      if (!p || !p.amount || !p.unit) throw badRequest('مدت اشتراک مشخص نیست', 'missing_duration');
      end = plans.endOfPeriod(base, p.amount, p.unit);
      start = if_live(existing, t) ?? start;
      if (!features.length && Array.isArray(p.features) && p.features.length) features = p.features;
      maxDevices = maxDevices || p.max_devices;
    }
    if (!Number.isFinite(end) || end <= t) throw badRequest('تاریخ پایان اشتراک معتبر نیست');

    const clean = sanitizeFeatures(features, T.app);

    if (existing) {
      const row = await one(
        `UPDATE ${TBL}
            SET plan=$2, status='active', starts_at=$3, ends_at=$4, features=$5::jsonb,
                max_devices=$6, grace_days=$7, note=$8, updated_at=$9, created_by=$10
          WHERE id=$1 RETURNING *`,
        [existing.id, plan, start, end, JSON.stringify(clean), maxDevices, graceDays, note, t, createdBy]
      );
      await logChange({
        subscriptionId: row.id, tenantId, action: 'renew', plan,
        prevStatus: existing.status, newStatus: row.status,
        prevEndsAt: Number(existing.ends_at), newEndsAt: Number(row.ends_at),
        actor: createdBy, note,
      });
      return row;
    }
    const row = await one(
      `INSERT INTO ${TBL} (id, ${KEY}, plan, status, starts_at, ends_at, features, max_devices, grace_days, note, created_at, updated_at, created_by)
       VALUES ($1,$2,$3,'active',$4,$5,$6::jsonb,$7,$8,$9,$10,$10,$11) RETURNING *`,
      [newId('sub'), tenantId, plan, start, end, JSON.stringify(clean), maxDevices, graceDays, note, t, createdBy]
    );
    await logChange({
      subscriptionId: row.id, tenantId, action: 'grant', plan,
      newStatus: row.status, newEndsAt: Number(row.ends_at),
      actor: createdBy, note,
    });
    return row;
  }

  async function setStatus(subscriptionId, status, by = '') {
    if (!['active', 'suspended', 'cancelled', 'expired', 'pending'].includes(status)) {
      throw badRequest('وضعیت اشتراک معتبر نیست');
    }
    const before = await one(`SELECT * FROM ${TBL} WHERE id=$1`, [subscriptionId]);
    const row = await one(
      `UPDATE ${TBL} SET status=$2, updated_at=$3, created_by=COALESCE(NULLIF($4,''), created_by) WHERE id=$1 RETURNING *`,
      [subscriptionId, status, now(), by]
    );
    if (!row) throw notFound('اشتراک پیدا نشد', 'subscription_not_found');
    await logChange({
      subscriptionId: row.id, tenantId: row[KEY], action: 'status', plan: row.plan,
      prevStatus: before ? before.status : '', newStatus: row.status,
      prevEndsAt: before ? Number(before.ends_at) : null, newEndsAt: Number(row.ends_at),
      actor: by,
    });
    return row;
  }

  /**
   * اشتراک‌هایی که دارند تمام می‌شوند.
   *
   * ── چرا این لازم شد ────────────────────────────────────────────────
   * برنامه‌ی مدیریت می‌گفت کدام اشتراک فعال است و کدام تمام شده، ولی
   * نمی‌گفت کدام **دارد** تمام می‌شود. یعنی صاحب سامانه همیشه دیر
   * می‌فهمید: وقتی که دکان‌دار قفل شده بود و زنگ می‌زد.
   *
   * `daysLeft` منفی هم می‌تواند باشد (تازه‌تمام‌شده‌ها) تا همان فهرست
   * «تازه از دست رفتند» را هم بدهد — کسانی که هنوز می‌شود برشان گرداند.
   */
  async function expiringSoon({ withinDays = 7, includeExpired = 3, limit = 100 } = {}) {
    const at = now();
    const until = at + withinDays * DAY;
    const from = at - includeExpired * DAY;
    const rows = await many(
      `SELECT sub.*, s.name AS tenant_name, s.owner_user_id,
              u.name AS owner_name, u.email AS owner_email, u.phone AS owner_phone
         FROM ${TBL} sub
         JOIN ${TEN} s ON s.id = sub.${KEY}
         JOIN users u ON u.id = s.owner_user_id
        WHERE sub.status IN ('active','suspended','expired')
          AND (sub.ends_at + (sub.grace_days * $1::bigint)) BETWEEN $2 AND $3
        ORDER BY (sub.ends_at + (sub.grace_days * $1::bigint)) ASC
        LIMIT $4`,
      [DAY, from, until, limit]
    );
    return rows.map(r => {
      const state = stateOf(r, at);
      return {
        subscriptionId: r.id,
        app: T.app,
        tenantId: r[KEY],
        tenantName: r.tenant_name,
        //  نام‌های قدیمی، تا پنل و برنامه‌ی مدیریت نشکنند
        shopId: r[KEY],
        shopName: r.tenant_name,
        ownerUserId: r.owner_user_id,
        ownerName: r.owner_name,
        ownerEmail: r.owner_email || '',
        ownerPhone: r.owner_phone || '',
        plan: r.plan,
        status: state.status,
        endsAt: state.endsAt,
        graceEndsAt: state.graceEndsAt,
        //  روزهای مانده؛ منفی یعنی همین‌قدر روز است که تمام شده
        daysLeft: Math.ceil((state.graceEndsAt - at) / DAY),
        note: r.note || '',
      };
    });
  }

  /**
   * خبر دادن به دکان‌دارهایی که اشتراکشان نزدیک پایان است.
   *
   * پیام در همان چت پشتیبانیِ خودشان می‌نشیند (پس در برنامه و سایت هر دو
   * دیده می‌شود) و اگر پوش تنظیم باشد، گوشیِ بسته را هم بیدار می‌کند.
   *
   * برای هر اشتراک فقط یک بار در هر «آستانه» فرستاده می‌شود — با یک کلید
   * در app_config. بدون این، هر بار که سرور این را صدا می‌زد یک پیام
   * تکراری می‌رفت و کاربر پشتیبانی را می‌بست.
   */
  async function notifyExpiring({ thresholds = [7, 3, 1] } = {}) {
    const support = require('./support');
    const rows = await expiringSoon({ withinDays: Math.max(...thresholds), includeExpired: 0, limit: 500 });
    let sent = 0;
    for (const row of rows) {
      //  نزدیک‌ترین آستانه‌ای که رد شده
      const hit = thresholds.filter(d => row.daysLeft <= d).sort((a, b) => a - b)[0];
      if (hit === undefined || row.daysLeft < 0) continue;
      const key = `subnotice_${row.subscriptionId}_${hit}`;
      const already = await plans.getConfig(key, '');
      if (already) continue;
      try {
        await support.systemMessage({
          userId: row.ownerUserId,
          //  چتِ پشتیبانی به دکان بسته است؛ برای پمپ فقط به خودِ کاربر
          //  می‌رسد و همان کافی است — صاحبِ پمپ همان کسی است که باید بداند.
          shopId: T === tenancy.SHOP ? row.tenantId : '',
          who: row.ownerName,
          body: row.daysLeft <= 0
            ? `اشتراک ${T.expiryNoun} امروز تمام می‌شود. برای اینکه قابلیت‌ها بسته نشوند، تمدیدش کنید.`
            : `اشتراک ${T.expiryNoun} ${row.daysLeft} روز دیگر تمام می‌شود. اگر بخواهید، همین‌جا بگویید تا تمدید شود.`,
        });
        await plans.setConfig(key, String(now()));
        sent++;
      } catch (err) {
        console.error('[subscriptions:notify]', err.message);
      }
    }
    return { sent, checked: rows.length };
  }

  return {
    tenancy: T,
    liveOf, latestOf, historyOf, changeLog, stateOf, expireDue, grant, setStatus,
    expiringSoon, notifyExpiring, DAY,
  };
}

const shop = build(tenancy.SHOP);
const pump = build(tenancy.PUMP);

module.exports = {
  //  بخشِ دکان، همان‌جا که همیشه بود
  ...shop,
  //  و بخش‌های دیگر، با نام
  build,
  shop,
  pump,
  forApp: (app) => (app === 'pump' ? pump : shop),
  stateOf,
  DAY,
};
