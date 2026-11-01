'use strict';
/**
 * هشدارهای مرکزی — پنلِ مرکزیِ مالک ۴.۱۱.
 *
 * هر «قاعده» یک پرس‌وجوی فقط‌خواندنی روی دادهٔ واقعی است و یافته‌هایش را با «هدف» (دکان، برنامه،
 * سرویس، رخداد، نامِ کاربریِ مدیر…) برمی‌گرداند. `evaluate()` همه را اجرا و نتیجه را در `ops_alerts`
 * می‌نشاند:
 *
 *   ⛔ **یکی کردنِ تکرار**: از هر (قاعده، هدف) در هر لحظه فقط یک هشدارِ زنده (`ux_ops_alerts_live`)؛
 *      دیدنِ دوباره فقط `last_seen_at` و شمارنده را بالا می‌برد.
 *   ⛔ **بی سیلِ اعلان**: حداکثر `maxNewPerRun` هشدارِ تازه در هر دور و `maxPerRule` برای هر قاعده؛
 *      بیشتر ⇒ یک هشدارِ «و N مورد دیگر» برای همان قاعده (`<rule>:overflow`)، نه صد ردیف.
 *   ⛔ **بی چشمک**: هشداری که کمتر از `flapMs` پیش «خودکار بسته شد» و دوباره دیده شد، همان ردیف
 *      دوباره باز می‌شود (نه ردیف و اعلانِ تازه)؛ `live_since` فقط برای هشدارِ واقعاً تازه جلو می‌رود.
 *   ⛔ **بسته شدنِ خودکار** فقط وقتی قاعده **با موفقیت** اجرا شد و آن هدف را دیگر ندید؛ قاعده‌ای که
 *      شکست خورد یا جدولش نیست (`unavailable`) هیچ هشداری را نمی‌بندد — نبودِ داده «درست شد» نیست.
 *   ⛔ هر هشدار سطحِ اهمیت دارد: critical · high · medium · low.
 *
 * دیدن/تأیید/بستن: `routes/admin-ops.js` (تأیید و بستنِ دستی با سابقه).
 * ارسالِ بیرونی (پیامک، ایمیل): نیست — پنلِ خانگی هشدارهای برگزیده را با رباتِ تلگرامِ مالک می‌فرستد.
 */
const { one, many, newId, now, tx } = require('../db');
const audit = require('./audit');
const { badRequest, notFound, conflict, tooMany } = require('../middleware/errors');

const HOUR = 3600_000;
const DAY = 24 * HOUR;

const LIMITS = Object.freeze({
  evaluateEveryMs: 60_000,       //  ارزیابیِ خودکار پیش از فهرست، حداکثر هر دقیقه
  forceMinMs: 15_000,            //  «همین حالا بسنج» حداکثر هر ۱۵ ثانیه
  maxNewPerRun: 50,
  maxPerRule: 20,
  flapMs: 30 * 60_000,
  scheduleMs: 5 * 60_000,
});

/**
 * تعریفِ صریحِ هر قاعده (همان که پنل نشان می‌دهد). آستانه‌ها عدد‌ند و در آزمون قفل‌اند.
 */
const RULES = Object.freeze({
  subscription_expiring: { severity: 'medium', window: 3 * DAY, what: 'اشتراکِ فعالِ دکان که تا ۳ روزِ دیگر تمام می‌شود (یک هشدارِ جمعی)' },
  shop_disconnected: { severity: 'medium', window: 48 * HOUR, what: 'دکانِ با اشتراکِ فعال که در ۳۰ روزِ گذشته وصل بوده ولی ۴۸ ساعت هیچ دستگاهش همگام نشده (دکانِ معلق/بایگانی بیرون)' },
  repeated_errors: { severity: 'high', window: HOUR, min: 20, what: 'رخدادِ بازی که در یک ساعتِ گذشته ۲۰ بار یا بیشتر تکرار شده' },
  service_down: { severity: 'critical', what: 'بخشی از سلامتِ سرورِ حساب «از کار افتاده» (دیتابیس، دیسک)، سرویسِ بیرونیِ نیمه‌تنظیم (ارسال شکست می‌خورد)، یا کارِ پس‌زمینه‌ای که از مهلتش گذشته یا شکست خورده' },
  payment_failed: { severity: 'high', window: DAY, what: 'پرداختِ ناموفق در ۲۴ ساعتِ گذشته (سفارشِ آنلاین با payment_status=failed، و قصدِ پرداختِ حساب‌پی اگر جدولش هست)' },
  order_stuck: { severity: 'medium', window: DAY, what: 'سفارشِ آنلاینِ واقعی (نه آزمایشی) که ۲۴ ساعت در یک وضعیتِ میانی مانده، یا «تازه»ای که ۱۵ دقیقه از مهلتِ قبولش گذشته و خودکار رد نشده' },
  settlement_mismatch: { severity: 'high', window: 7 * DAY, what: 'تسویهٔ نقدِ پیک در ۷ روزِ گذشته با کسری یا اضافه' },
  version_incompatible: { severity: 'high', what: 'دستگاهِ فعال (۳۰ روز) زیرِ حداقل نسخهٔ پشتیبانی‌شدهٔ همان برنامه' },
  admin_suspicious: { severity: 'high', window: HOUR, min: 10, what: 'ورودِ ناموفقِ مدیر: ۱۰ بار برای یک نامِ کاربری یا ۳۰ بار از یک IP در یک ساعت؛ یا ۱۰ حالتِ دکان (تعلیق/بایگانی…) از یک مدیر در ۱۰ دقیقه' },
});
const SEVERITIES = Object.freeze(['critical', 'high', 'medium', 'low']);
const STATUSES = Object.freeze(['open', 'acknowledged', 'resolved']);

async function tableExists(name) {
  const r = await one('SELECT to_regclass($1) IS NOT NULL AS ok', [name]);
  return r?.ok === true;
}

/** «داده در دسترس نیست» برای یک قاعده — نه صفر، نه سبز. */
class Unavailable extends Error {
  constructor(reason) { super(reason); this.unavailable = true; }
}

/* ───────────────────────────── قاعده‌ها ───────────────────────────── */

const ACTIVE_SUB = `s.status='active' AND s.ends_at + s.grace_days * ${DAY}::bigint > $1`;

const finders = {
  async subscription_expiring(t) {
    const rows = await many(
      `SELECT s.shop_id, sh.name, s.ends_at FROM subscriptions s JOIN shops sh ON sh.id = s.shop_id
        WHERE s.status='active' AND s.ends_at > $1 AND s.ends_at <= $2 ORDER BY s.ends_at LIMIT 500`,
      [t, t + RULES.subscription_expiring.window]
    );
    if (!rows.length) return [];
    const soonest = rows[0];
    return [{
      target: 'all',
      severity: rows.some((r) => Number(r.ends_at) - t <= DAY) ? 'high' : 'medium',
      title: `${rows.length} اشتراکِ دکان تا ۳ روزِ دیگر تمام می‌شود`,
      detail: {
        count: rows.length, soonestShopId: soonest.shop_id, soonestEndsAt: Number(soonest.ends_at),
        shops: rows.slice(0, 20).map((r) => ({ id: r.shop_id, name: r.name, endsAt: Number(r.ends_at) })),
      },
    }];
  },

  async shop_disconnected(t) {
    const holdFilter = (await tableExists('shop_holds'))
      ? `AND NOT EXISTS (SELECT 1 FROM shop_holds hd WHERE hd.shop_id = sh.id AND hd.lifted_at IS NULL
                          AND hd.kind IN ('access_suspended','archived','deletion_requested'))`
      : '';
    const rows = await many(
      `SELECT sh.id, sh.name, d.last_seen, d.devices FROM shops sh
         JOIN subscriptions s ON s.shop_id = sh.id AND ${ACTIVE_SUB}
         JOIN (SELECT account_id, MAX(last_seen_at) AS last_seen, COUNT(DISTINCT device_id)::int AS devices
                 FROM (SELECT account_id, device_id, last_seen_at FROM sync_devices WHERE app='shop' AND account_kind='shop'
                       UNION ALL
                       SELECT account_id, device_id, last_seen_at FROM sync_v3_devices WHERE app='shop' AND account_kind='shop') u
                GROUP BY account_id) d ON d.account_id = sh.id
        WHERE d.last_seen > $2 AND d.last_seen < $3 ${holdFilter}
        ORDER BY d.last_seen LIMIT 200`,
      [t, t - 30 * DAY, t - RULES.shop_disconnected.window]
    );
    return rows.map((r) => ({
      target: r.id,
      title: `دکانِ «${r.name || r.id}» ${Math.floor((t - Number(r.last_seen)) / HOUR)} ساعت است همگام نشده`,
      detail: { shopId: r.id, lastSeenAt: Number(r.last_seen), devices: Number(r.devices) },
    }));
  },

  async repeated_errors() {
    const rule = RULES.repeated_errors;
    const rows = await require('./ops-errors').burst(rule.window, rule.min);
    return rows.map((r) => ({
      target: r.id,
      severity: r.source === 'server' || Number(r.shops) >= 5 ? 'high' : 'medium',
      title: `خطای تکراری: ${r.n} بار در یک ساعت — ${String(r.title).slice(0, 100)}`,
      detail: { incidentId: r.id, count: Number(r.n), shops: Number(r.shops) || 0, source: r.source, app: r.app },
    }));
  },

  async service_down(t) {
    const health = require('./system-health');
    const out = [];
    const snap = await health.snapshot();
    const parts = [
      ['database', snap.database], ['storage', snap.storage], ['backups', snap.backups],
      ['queue.email', snap.queues?.email], ['queue.telegram', snap.queues?.telegram],
    ];
    for (const [id, p] of parts) {
      if (p?.status === 'down') out.push({ target: id, severity: 'critical', title: `سرویسِ «${id}» از کار افتاده`, detail: { part: id, status: 'down' } });
    }
    const prov = await health.providers();
    for (const p of prov.providers || []) {
      if (p.state === 'incomplete') {
        out.push({
          target: `provider.${p.id}`, severity: 'high', title: `سرویسِ بیرونیِ «${p.id}» نیمه‌تنظیم است — ارسال شکست می‌خورد`,
          //  ⛔ فقط نامِ فیلدِ کم، هرگز مقدار
          detail: { provider: p.id, mode: p.mode, missing: (p.missing || []).join('، ') },
        });
      }
    }
    for (const j of require('./job-beat').snapshot(t)) {
      if (j.status === 'warn') {
        out.push({
          target: `job.${j.name}`, severity: 'high', title: `کارِ پس‌زمینهٔ «${j.name}» ${j.lastError ? 'شکست خورد' : 'از مهلتش گذشته'}`,
          detail: { job: j.name, lastOkAt: j.lastOkAt, lastFailAt: j.lastFailAt, lastError: j.lastError },
        });
      }
    }
    return out;
  },

  async payment_failed(t) {
    const since = t - RULES.payment_failed.window;
    const rows = await many(
      `SELECT o.shop_id, sh.name, COUNT(*)::int AS n, MAX(o.updated_at) AS last FROM online_orders o JOIN shops sh ON sh.id = o.shop_id
        WHERE o.payment_status='failed' AND o.is_test=false AND o.updated_at > $1 GROUP BY o.shop_id, sh.name ORDER BY n DESC LIMIT 100`,
      [since]
    );
    const byShop = new Map(rows.map((r) => [r.shop_id, { name: r.name, orders: Number(r.n), intents: 0, last: Number(r.last) }]));
    if (await tableExists('payment_intents')) {
      for (const r of await many(
        `SELECT p.shop_id, sh.name, COUNT(*)::int AS n, MAX(p.updated_at) AS last FROM payment_intents p JOIN shops sh ON sh.id = p.shop_id
          WHERE p.status='failed' AND p.updated_at > $1 GROUP BY p.shop_id, sh.name LIMIT 100`, [since])) {
        const cur = byShop.get(r.shop_id) || { name: r.name, orders: 0, intents: 0, last: 0 };
        cur.intents = Number(r.n);
        cur.last = Math.max(cur.last, Number(r.last));
        byShop.set(r.shop_id, cur);
      }
    }
    return [...byShop.entries()].map(([shopId, v]) => ({
      target: shopId,
      title: `پرداختِ ناموفق در دکانِ «${v.name || shopId}»: ${v.orders + v.intents} مورد در ۲۴ ساعت`,
      detail: { shopId, orders: v.orders, intents: v.intents, lastAt: v.last },
    }));
  },

  async order_stuck(t) {
    const rows = await many(
      `SELECT o.shop_id, sh.name,
              COUNT(*) FILTER (WHERE o.status='new' AND o.accept_deadline_at IS NOT NULL AND o.accept_deadline_at < $2)::int AS overdue_new,
              COUNT(*) FILTER (WHERE o.status IN ('accepted','picking','packed','ready_for_pickup','out_for_delivery',
                                                  'delivery_failed','retry_scheduled','return_requested') AND o.updated_at < $1)::int AS stale,
              MIN(o.updated_at) AS oldest
         FROM online_orders o JOIN shops sh ON sh.id = o.shop_id
        WHERE o.is_test=false AND o.status IN ('new','accepted','picking','packed','ready_for_pickup','out_for_delivery',
                                               'delivery_failed','retry_scheduled','return_requested')
        GROUP BY o.shop_id, sh.name
       HAVING COUNT(*) FILTER (WHERE o.status='new' AND o.accept_deadline_at IS NOT NULL AND o.accept_deadline_at < $2) > 0
           OR COUNT(*) FILTER (WHERE o.status IN ('accepted','picking','packed','ready_for_pickup','out_for_delivery',
                                                  'delivery_failed','retry_scheduled','return_requested') AND o.updated_at < $1) > 0
        ORDER BY oldest LIMIT 100`,
      [t - RULES.order_stuck.window, t - 15 * 60_000]
    );
    return rows.map((r) => ({
      target: r.shop_id,
      //  «تازه»ی ردنشده پس از مهلت یعنی زمان‌سنجِ قبول کار نکرده ⇒ مهم‌تر
      severity: Number(r.overdue_new) > 0 ? 'high' : 'medium',
      title: `سفارشِ گیرکرده در دکانِ «${r.name || r.shop_id}»: ${Number(r.stale) + Number(r.overdue_new)} سفارش`,
      detail: { shopId: r.shop_id, stale: Number(r.stale), overdueNew: Number(r.overdue_new), oldestUpdatedAt: Number(r.oldest) },
    }));
  },

  async settlement_mismatch(t) {
    if (!(await tableExists('courier_settlements'))) throw new Unavailable('courier_settlements');
    const rows = await many(
      `SELECT c.shop_id, sh.name, COUNT(*)::int AS n, SUM(c.shortage_minor)::bigint AS shortage, SUM(c.excess_minor)::bigint AS excess,
              MAX(c.created_at) AS last
         FROM courier_settlements c JOIN shops sh ON sh.id = c.shop_id
        WHERE c.created_at > $1 AND (c.shortage_minor > 0 OR c.excess_minor > 0)
        GROUP BY c.shop_id, sh.name ORDER BY shortage DESC LIMIT 100`,
      [t - RULES.settlement_mismatch.window]
    );
    return rows.map((r) => ({
      target: r.shop_id,
      severity: Number(r.shortage) > 0 ? 'high' : 'medium',
      title: `مغایرتِ تسویهٔ پیک در دکانِ «${r.name || r.shop_id}»: ${r.n} تسویه`,
      //  پول به واحدِ خُرد (AFN × ۱۰۰) — همان ستون‌ها، بی گرد کردن
      detail: { shopId: r.shop_id, settlements: Number(r.n), shortageMinor: Number(r.shortage), excessMinor: Number(r.excess), lastAt: Number(r.last) },
    }));
  },

  async version_incompatible() {
    const v = await require('./system-health').versions();
    return (v.apps || []).filter((a) => Number(a.belowMin) > 0).map((a) => ({
      target: a.app,
      title: `${a.belowMin} دستگاهِ «${a.app}» زیرِ حداقل نسخهٔ ${a.minVersion}`,
      detail: { app: a.app, belowMin: Number(a.belowMin), minVersion: a.minVersion, devices: Number(a.devices) },
    }));
  },

  async admin_suspicious(t) {
    const rule = RULES.admin_suspicious;
    const since = t - rule.window;
    const out = [];
    for (const r of await many(
      `SELECT identifier, COUNT(*)::int AS n, COUNT(DISTINCT ip)::int AS ips, MAX(created_at) AS last FROM login_attempts
        WHERE scope='admin' AND ok=false AND created_at > $1 GROUP BY identifier HAVING COUNT(*) >= $2 ORDER BY n DESC LIMIT 20`,
      [since, rule.min])) {
      out.push({
        target: `user:${String(r.identifier).slice(0, 60)}`,
        title: `${r.n} ورودِ ناموفقِ مدیر برای «${String(r.identifier).slice(0, 60)}» در یک ساعت`,
        detail: { username: String(r.identifier).slice(0, 60), failures: Number(r.n), ips: Number(r.ips), lastAt: Number(r.last) },
      });
    }
    for (const r of await many(
      `SELECT ip, COUNT(*)::int AS n, COUNT(DISTINCT identifier)::int AS users, MAX(created_at) AS last FROM login_attempts
        WHERE scope='admin' AND ok=false AND created_at > $1 AND ip <> '' GROUP BY ip HAVING COUNT(*) >= $2 ORDER BY n DESC LIMIT 20`,
      [since, rule.min * 3])) {
      out.push({
        target: `ip:${r.ip}`,
        title: `${r.n} ورودِ ناموفقِ مدیر از یک IP در یک ساعت (${r.users} نامِ کاربری)`,
        detail: { ip: r.ip, failures: Number(r.n), usernames: Number(r.users), lastAt: Number(r.last) },
      });
    }
    for (const r of await many(
      `SELECT user_id, COUNT(*)::int AS n, MAX(created_at) AS last FROM audit_logs
        WHERE action='admin.shop_hold_placed' AND created_at > $1 GROUP BY user_id HAVING COUNT(*) >= $2 LIMIT 20`,
      [t - 10 * 60_000, rule.min])) {
      out.push({
        target: `holds:${r.user_id}`,
        title: `${r.n} حالتِ دکان (تعلیق/بایگانی…) از یک مدیر در ۱۰ دقیقه`,
        detail: { adminId: r.user_id, holds: Number(r.n), lastAt: Number(r.last) },
      });
    }
    return out;
  },
};

/* ───────────────────────────── ارزیابی ───────────────────────────── */

let running = null;
let lastEvalAt = 0;
let lastForceAt = 0;
let lastReport = null;

function dedupKey(rule, target) { return `${rule}|${String(target || '').slice(0, 120)}`; }

function shape(r) {
  return {
    id: r.id, rule: r.rule, target: r.target, severity: r.severity, status: r.status, title: r.title,
    detail: r.detail || {}, firstSeenAt: Number(r.first_seen_at), lastSeenAt: Number(r.last_seen_at),
    liveSince: Number(r.live_since), occurrences: Number(r.occurrences),
    acknowledgedAt: r.acknowledged_at == null ? null : Number(r.acknowledged_at), acknowledgedBy: r.acknowledged_by || '',
    ackNote: r.ack_note || '', resolvedAt: r.resolved_at == null ? null : Number(r.resolved_at), resolvedBy: r.resolved_by || '',
    resolution: r.resolution || '', resolveReason: r.resolve_reason || '', version: Number(r.version),
  };
}

/** نتیجهٔ یک قاعده ⇒ ردیف‌ها. `budget` سقفِ هشدارِ تازهٔ همین دور است (مشترک بینِ قاعده‌ها). */
async function apply(c, rule, findings, t, budget) {
  const def = RULES[rule];
  let created = 0; let updated = 0; let reopened = 0; let suppressed = 0;
  const seenKeys = new Set();
  let perRule = 0;
  for (const f of findings) {
    const key = dedupKey(rule, f.target);
    seenKeys.add(key);
    const severity = SEVERITIES.includes(f.severity) ? f.severity : def.severity;
    const live = (await c.query(`SELECT * FROM ops_alerts WHERE dedup_key=$1 AND status <> 'resolved' FOR UPDATE`, [key])).rows[0];
    if (live) {
      await c.query(
        `UPDATE ops_alerts SET last_seen_at=$2, occurrences=occurrences+1, title=$3, detail=$4::jsonb,
                severity=$5, version=version+1 WHERE id=$1`,
        [live.id, t, f.title, JSON.stringify(f.detail || {}), severity]
      );
      updated += 1;
      continue;
    }
    //  چشمک: همین هدف کمتر از flapMs پیش خودکار بسته شد ⇒ همان ردیف دوباره باز (بی اعلانِ تازه)
    const recent = (await c.query(
      `SELECT * FROM ops_alerts WHERE dedup_key=$1 AND status='resolved' AND resolution='auto' AND resolved_at > $2
        ORDER BY resolved_at DESC LIMIT 1 FOR UPDATE`, [key, t - LIMITS.flapMs])).rows[0];
    if (recent) {
      await c.query(
        `UPDATE ops_alerts SET status='open', resolved_at=NULL, resolved_by='', resolution='', last_seen_at=$2,
                occurrences=occurrences+1, title=$3, detail=$4::jsonb, severity=$5, version=version+1 WHERE id=$1`,
        [recent.id, t, f.title, JSON.stringify(f.detail || {}), severity]
      );
      reopened += 1;
      continue;
    }
    if (perRule >= LIMITS.maxPerRule || budget.left <= 0) { suppressed += 1; continue; }
    perRule += 1;
    budget.left -= 1;
    await c.query(
      `INSERT INTO ops_alerts (id, rule, target, dedup_key, severity, status, title, detail, first_seen_at, last_seen_at, live_since)
       VALUES ($1,$2,$3,$4,$5,'open',$6,$7::jsonb,$8,$8,$8)`,
      [newId('alr'), rule, String(f.target || '').slice(0, 120), key, severity, f.title, JSON.stringify(f.detail || {}), t]
    );
    created += 1;
  }
  //  سرریز ⇒ یک هشدارِ جمعی برای همان قاعده (خودش هم یکی‌شده و خودکار بسته‌شدنی)
  const overflowKey = dedupKey(rule, '*overflow');
  if (suppressed > 0) {
    seenKeys.add(overflowKey);
    const title = `و ${suppressed} مورد دیگر از «${rule}» (برای جلوگیری از سیلِ هشدار جدا ساخته نشد)`;
    const live = (await c.query(`SELECT id FROM ops_alerts WHERE dedup_key=$1 AND status <> 'resolved' FOR UPDATE`, [overflowKey])).rows[0];
    if (live) {
      await c.query(`UPDATE ops_alerts SET last_seen_at=$2, occurrences=occurrences+1, title=$3, detail=$4::jsonb, version=version+1 WHERE id=$1`,
        [live.id, t, title, JSON.stringify({ suppressed })]);
    } else {
      await c.query(
        `INSERT INTO ops_alerts (id, rule, target, dedup_key, severity, status, title, detail, first_seen_at, last_seen_at, live_since)
         VALUES ($1,$2,'*overflow',$3,$4,'open',$5,$6::jsonb,$7,$7,$7)`,
        [newId('alr'), rule, overflowKey, def.severity, title, JSON.stringify({ suppressed }), t]
      );
    }
  }
  //  هدف‌هایی که دیگر دیده نشدند ⇒ خودکار بسته (فقط چون قاعده با موفقیت اجرا شد)
  const stale = (await c.query(`SELECT id, dedup_key FROM ops_alerts WHERE rule=$1 AND status <> 'resolved' FOR UPDATE`, [rule])).rows
    .filter((r) => !seenKeys.has(r.dedup_key));
  for (const r of stale) {
    await c.query(
      `UPDATE ops_alerts SET status='resolved', resolved_at=$2, resolved_by='system', resolution='auto', version=version+1 WHERE id=$1`,
      [r.id, t]
    );
  }
  return { created, updated, reopened, suppressed, autoResolved: stale.length };
}

/** همهٔ قاعده‌ها؛ هر کدام جدا شکست می‌خورد. تک‌پرواز در همین پروسه و زیرِ قفلِ تراکنشی. */
async function evaluate({ force = false } = {}) {
  if (running) return running;
  if (!force && lastReport && Date.now() - lastEvalAt < LIMITS.evaluateEveryMs) return lastReport;
  running = (async () => {
    try {
      //  رخدادها پیش از قاعدهٔ «خطای تکراری» تازه شوند
      await require('./ops-errors').ingest({ force: true }).catch(() => null);
      const t = now();
      const budget = { left: LIMITS.maxNewPerRun };
      const rules = [];
      for (const id of Object.keys(RULES)) {
        const def = RULES[id];
        const base = { id, severity: def.severity, what: def.what, windowMs: def.window || null, min: def.min || null };
        let findings;
        try {
          findings = await finders[id](t);
        } catch (err) {
          rules.push({ ...base, state: err.unavailable ? 'unavailable' : 'error', reason: err.unavailable ? `نیاز به جدولِ ${err.message}` : String(err.code || err.name || 'error').slice(0, 60) });
          continue;
        }
        try {
          const res = await tx(async (c) => {
            await c.query('SELECT pg_advisory_xact_lock(hashtext($1))', [`ops:alerts:${id}`]);
            return apply(c, id, findings, t, budget);
          });
          rules.push({ ...base, state: 'ok', findings: findings.length, ...res });
        } catch (err) {
          rules.push({ ...base, state: 'error', reason: String(err.code || err.name || 'error').slice(0, 60) });
        }
      }
      lastEvalAt = Date.now();
      lastReport = { evaluatedAt: t, rules };
      return lastReport;
    } finally {
      running = null;
    }
  })();
  return running;
}

async function evaluateNow() {
  if (Date.now() - lastForceAt < LIMITS.forceMinMs) throw tooMany('ارزیابی همین حالا انجام شد؛ چند ثانیهٔ دیگر', 'evaluate_too_soon');
  lastForceAt = Date.now();
  return evaluate({ force: true });
}

/* ───────────────────────────── فهرست و کارِ مدیر ───────────────────────────── */

async function list(f = {}) {
  const where = [];
  const args = [];
  const add = (sql, v) => { args.push(v); where.push(sql.replace('$?', `$${args.length}`)); };
  if (f.status === 'live' || !f.status) where.push("status <> 'resolved'");
  else if (STATUSES.includes(f.status)) add('status = $?', f.status);
  if (SEVERITIES.includes(f.severity)) add('severity = $?', f.severity);
  if (RULES[f.rule]) add('rule = $?', f.rule);
  if (Number(f.since) > 0) add('live_since > $?', Math.floor(Number(f.since)));
  const sql = where.length ? `WHERE ${where.join(' AND ')}` : '';
  const limit = Math.min(200, Math.max(1, Number(f.limit) || 50));
  const offset = Math.max(0, Math.min(100_000, Number(f.offset) || 0));
  const rows = await many(
    `SELECT * FROM ops_alerts ${sql}
      ORDER BY CASE severity WHEN 'critical' THEN 0 WHEN 'high' THEN 1 WHEN 'medium' THEN 2 ELSE 3 END, last_seen_at DESC, id
      LIMIT ${limit} OFFSET ${offset}`, args
  );
  const total = (await one(`SELECT COUNT(*)::int AS n FROM ops_alerts ${sql}`, args)).n;
  const counts = Object.fromEntries((await many(
    `SELECT status, COUNT(*)::int AS n FROM ops_alerts WHERE status <> 'resolved' OR resolved_at > $1 GROUP BY status`, [now() - 7 * DAY]
  )).map((r) => [r.status, r.n]));
  return { alerts: rows.map(shape), total, limit, offset, counts };
}

function actorName(req) { return require('./admin-service').label(req) || String(req.admin?.id || ''); }

async function acknowledge(id, req, { note = '', ip = '' }) {
  const clean = require('./ops-errors').scrub(note, 300);
  return tx(async (c) => {
    const r = (await c.query('SELECT * FROM ops_alerts WHERE id=$1 FOR UPDATE', [String(id)])).rows[0];
    if (!r) throw notFound('هشدار پیدا نشد', 'alert_not_found');
    if (r.status === 'resolved') throw conflict('این هشدار بسته شده است', 'alert_resolved');
    if (r.status === 'acknowledged') return { alert: shape(r), changed: false };
    const upd = (await c.query(
      `UPDATE ops_alerts SET status='acknowledged', acknowledged_at=$2, acknowledged_by=$3, ack_note=$4, version=version+1 WHERE id=$1 RETURNING *`,
      [r.id, now(), actorName(req), clean]
    )).rows[0];
    await audit.log({
      actorType: 'admin', userId: req.admin.id, ip, action: 'admin.ops_alert_ack', targetType: 'ops_alert', targetId: r.id,
      detail: { rule: r.rule, target: r.target, ...(clean ? { note: clean } : {}) },
    }, { client: c });
    return { alert: shape(upd), changed: true };
  });
}

async function resolve(id, req, { reason, ip = '' }) {
  const clean = require('./ops-errors').scrub(reason, 200);
  if (clean.length < 3) throw badRequest('دلیلِ بستن را بنویسید (دست‌کم ۳ نویسه)', 'reason_required');
  return tx(async (c) => {
    const r = (await c.query('SELECT * FROM ops_alerts WHERE id=$1 FOR UPDATE', [String(id)])).rows[0];
    if (!r) throw notFound('هشدار پیدا نشد', 'alert_not_found');
    if (r.status === 'resolved') return { alert: shape(r), changed: false };
    const upd = (await c.query(
      `UPDATE ops_alerts SET status='resolved', resolved_at=$2, resolved_by=$3, resolution='manual', resolve_reason=$4, version=version+1
        WHERE id=$1 RETURNING *`,
      [r.id, now(), actorName(req), clean]
    )).rows[0];
    await audit.log({
      actorType: 'admin', userId: req.admin.id, ip, action: 'admin.ops_alert_resolved', targetType: 'ops_alert', targetId: r.id,
      detail: { rule: r.rule, target: r.target, reason: clean },
    }, { client: c });
    return { alert: shape(upd), changed: true };
  });
}

function definitions() {
  return Object.entries(RULES).map(([id, d]) => ({ id, severity: d.severity, what: d.what, windowMs: d.window || null, min: d.min || null }));
}

/* ───────────────────────────── زمان‌بندی ───────────────────────────── */

let timer = null;
function start() {
  if (timer) return;
  const run = require('./job-beat').wrap('ops-alerts', LIMITS.scheduleMs, () => evaluate({ force: true }));
  timer = setInterval(() => run().catch((err) => console.error('[ops-alerts]', err.message)), LIMITS.scheduleMs);
  if (timer.unref) timer.unref();
}
function stop() { if (timer) clearInterval(timer); timer = null; }

function _reset() { lastEvalAt = 0; lastForceAt = 0; lastReport = null; }

module.exports = {
  LIMITS, RULES, SEVERITIES, STATUSES, evaluate, evaluateNow, list, acknowledge, resolve, definitions,
  lastReport: () => lastReport, start, stop, dedupKey, _reset, _finders: finders,
};
