'use strict';
/**
 * خبرِ بحرانی به مسئول — OPS-01@29 (§۲۹.۸: «هشدارهای بحرانی به مسئولِ مربوط برسند»).
 *
 * تا امروز هشدارِ بحرانی (`ops_alerts`، `lib/ops-alerts.js`) فقط در پنل بود؛ کسی که پنل را باز نکرده بود هیچ
 * نمی‌فهمید. این‌جا هر رخدادِ بحرانی به مسئول‌های تعیین‌شده می‌رسد:
 *
 *   ⛔ **یک خبر برای هر رخداد**: هر ردیفِ `ops_alerts` (= یک رخداد؛ تکرار همان ردیف است و چشمکِ زیرِ ۳۰ دقیقه همان
 *      ردیف را باز می‌کند) یک ردیفِ `ops_alert_pages` با کلیدِ اصلیِ همان شناسه دارد ⇒ دورِ دوم، راه‌اندازیِ دوباره
 *      و نمونهٔ دومِ سرور دوباره خبر نمی‌دهند. تصمیم زیرِ قفلِ `pg_try_advisory_xact_lock` و در یک تراکنش نوشته
 *      می‌شود و **پس از** ثبت فرستاده می‌شود (حداکثر یک بار؛ خبرِ گم‌شده را پیگیریِ بعدی جبران می‌کند).
 *   ⛔ **بی سیل**: از هر شرط (`dedup_key`) حداکثر یک خبرِ تازه در `perConditionMs`؛ بیشتر ⇒ «پس‌زده» و پس از
 *      همان مهلت، اگر هنوز باز است، خبر می‌رود. حداکثر `maxSendsPerRun` پیام در هر دور.
 *   ⛔ **پیگیری (escalation)**: «دیدم» ِ پنل (`POST /api/admin/ops/alerts/:id/ack`) نیامد ⇒ پس از N دقیقه به
 *      گیرنده‌های اصلی و پیگیری، حداکثر `maxEscalations` بار. «دیدم» یا بسته شدن پیگیری را می‌ایستاند.
 *   ⛔ **بسته شد**: بستنِ دستی ⇒ همان دم؛ بستنِ خودکار ⇒ پس از `resolveQuietMs` آرامش (چشمک «بسته شد» نمی‌فرستد).
 *   ⛔ **گیرنده فقط مدیرِ پلتفرم** (`admins`، نقشِ admin/superadmin، فعال) — هنگامِ نوشتن (۴۰۰) و هنگامِ فرستادن
 *      (مدیرِ غیرفعال/تنزل‌یافته کنار می‌رود). فهرست را فقط مدیرِ ارشد می‌نویسد (`routes/admin-ops.js`).
 *   ⛔ پرچمِ `ops.critical_alerts` (scope ‏`ops`، پیش‌فرض خاموش): خاموش ⇒ نه شرطِ تازه‌ای سنجیده می‌شود، نه شدتی
 *      عوض می‌شود، نه ردیفی نوشته و نه پیامی فرستاده می‌شود.
 *
 * کانال‌ها (D-09): پیامک/واتس‌اپ/تلگرامِ بیرونی هنوز حساب ندارند ⇒ فقط ایمیل (`mailer.js`، نشانیِ تعیین‌شده برای
 * هر مدیر) و پوش (`push.js`، دستگاه‌های ثبت‌شدهٔ همان مدیر). نتیجهٔ واقعیِ هر ارسال در `ops_alert_page_log`؛ ایمیلِ
 * راهِ «log» تحویل حساب نمی‌شود.
 */
const config = require('../config');
const db = require('../db');
const { one, many, newId, now, tx } = db;
const { badRequest } = require('../middleware/errors');

const MIN = 60_000;
const DAY = 24 * 60 * MIN;

const FLAG = Object.freeze({ scope: 'ops', key: 'ops.critical_alerts' });
const SETTING_KEYS = Object.freeze({ escalateMin: 'ops.critical_alerts.escalate_min', maxEscalations: 'ops.critical_alerts.max_escalations' });
const LOCK = 'ops:alerts:paging';

const LIMITS = Object.freeze({
  escalateMin: { def: 15, min: 5, max: 240 },
  maxEscalations: { def: 2, min: 0, max: 5 },
  perConditionMs: 60 * MIN,
  resolveQuietMs: 10 * MIN,
  maxSendsPerRun: 10,
  maxRecipients: 10,
  logKeepMs: 90 * DAY,
  tickMs: MIN,
});

/** شرط‌های بحرانیِ تازه (قاعدهٔ `critical_health` ِ ops-alerts). آستانه‌ها عددند و در آزمون قفل‌اند. */
const CRIT = Object.freeze({
  windowMs: 10 * MIN,
  dbBusyMin: 10,            //  ۵۰۳ ِ `db_busy` یا «اتصالِ استخر تمام شد» در ۱۰ دقیقه
  errors5xxMin: 100,        //  هر ۵xx ِ سرور در ۱۰ دقیقه (سقفِ ثبت ۱۲۰ در دقیقه)
  queueStuckMs: 30 * MIN,   //  کارتِ سلامت از ۵ دقیقه «هشدار» است؛ ۳۰ دقیقه ⇒ بحرانی
  reconcileFailStreak: 3,   //  سه نوبتِ پیاپیِ ناموفقِ تطبیقِ حساب‌پی (≈ ۱۵ دقیقه)
});

/** کارهای پس‌زمینه‌ای که گیر/شکستشان با پرچمِ روشن «بحرانی» است (کد، سفارش، رد ِ خودکار، خودِ هشدارها). */
const CRITICAL_JOBS = Object.freeze(['login-outbox', 'order-notify', 'order-timeout', 'ops-alerts']);
const PAGEABLE_ROLES = Object.freeze(['superadmin', 'admin']);
const TIERS = Object.freeze(['primary', 'escalation']);
const EMAIL_RE = /^[^\s@<>"']{1,64}@[^\s@<>"']{1,190}\.[a-z]{2,24}$/i;

/* ───────────────────────────── پرچم و تنظیم ───────────────────────────── */

async function opsValues() {
  const rows = await many(`SELECT key, value FROM live_config WHERE scope = $1 AND key LIKE 'ops.critical_alerts%'`, [FLAG.scope]);
  const out = {};
  for (const r of rows) { try { out[r.key] = JSON.parse(r.value); } catch { out[r.key] = null; } }
  return out;
}

async function enabled() {
  const r = await one('SELECT value FROM live_config WHERE scope = $1 AND key = $2', [FLAG.scope, FLAG.key]);
  if (!r) return false;
  try { return JSON.parse(r.value) === true; } catch { return false; }
}

const clampInt = (raw, { def, min, max }) => {
  const n = Number(raw);
  return Number.isInteger(n) && n >= min && n <= max ? n : def;
};

async function settings() {
  const vals = await opsValues();
  const escalateMin = clampInt(vals[SETTING_KEYS.escalateMin], LIMITS.escalateMin);
  return {
    enabled: vals[FLAG.key] === true,
    escalateMin, escalateAfterMs: escalateMin * MIN,
    maxEscalations: clampInt(vals[SETTING_KEYS.maxEscalations], LIMITS.maxEscalations),
  };
}

/** با پرچمِ خاموش فهرستِ خالی ⇒ شدتِ کارهای پس‌زمینه در ops-alerts همان پیشین می‌ماند. */
async function criticalJobs() { return (await enabled()) ? CRITICAL_JOBS : []; }

/* ───────────────────────────── سنجشِ شرط‌های بحرانی ───────────────────────────── */

/**
 * یافته‌های قاعدهٔ `critical_health` — فقط آنچه کارت‌های سلامت دارند ولی تا امروز «بحرانی» نمی‌شد:
 * شلوغیِ دیتابیس، جهشِ ۵xx، صفِ گیرکرده و شکستِ پیاپیِ تطبیقِ پرداخت. از کار افتادنِ دیتابیس، دیسکِ پر،
 * شکستِ پشتیبان و صفِ «down» همان `service_down` ِ بحرانی است و این‌جا تکرار نمی‌شود.
 */
async function criticalFindings(t = now()) {
  const out = [];
  const r = await one(
    `SELECT COUNT(*)::int AS n,
            COUNT(*) FILTER (WHERE code = 'db_busy' OR message LIKE 'timeout exceeded when trying to connect%')::int AS busy
       FROM server_errors WHERE at > $1`, [t - CRIT.windowMs]
  );
  const waiting = db.isPglite() ? 0 : Number(db.getPool()?.waitingCount) || 0;
  if (r.busy >= CRIT.dbBusyMin || waiting >= config.db.poolMax) {
    out.push({ target: 'db.busy', title: 'دیتابیس شلوغ است — درخواست‌ها پس زده می‌شوند (db_busy)',
      detail: { busy: r.busy, waiting, poolMax: config.db.poolMax, windowMs: CRIT.windowMs } });
  }
  if (r.n >= CRIT.errors5xxMin) {
    out.push({ target: 'errors.5xx', title: `جهشِ خطای سرور: ${r.n} پاسخِ ۵xx در ۱۰ دقیقه`, detail: { count: r.n, windowMs: CRIT.windowMs } });
  }
  const snap = await require('./system-health').snapshot();
  for (const [id, q] of [['email', snap.queues?.email], ['telegram', snap.queues?.telegram]]) {
    if (q?.oldestAgeMs != null && Number(q.oldestAgeMs) > CRIT.queueStuckMs) {
      out.push({ target: `queue.${id}`, title: `صفِ «${id}» بیش از ${CRIT.queueStuckMs / MIN} دقیقه جلو نرفته`,
        detail: { queue: id, oldestAgeMs: Number(q.oldestAgeMs) } });
    }
  }
  const rh = await require('./payment-reconciler').sharedHealth();   //  پایگاه: همهٔ نمونه‌ها، پس از راه‌اندازیِ دوباره هم
  if (rh.streak >= CRIT.reconcileFailStreak) {
    out.push({ target: 'payments.reconcile', title: `تطبیقِ پرداخت ${rh.streak} نوبتِ پیاپی شکست خورد`,
      detail: { streak: rh.streak, lastFailAt: rh.lastFailAt, lastOkAt: rh.lastOkAt, lastError: rh.lastError } });
  }
  return out.map((f) => ({ severity: 'critical', ...f }));
}

/* ───────────────────────────── گیرنده‌ها ───────────────────────────── */

async function activeRecipients(c) {
  return (await c.query(
    `SELECT r.admin_id, r.tier, r.email FROM ops_alert_recipients r JOIN admins a ON a.id = r.admin_id
      WHERE a.status = 'active' AND a.role = ANY($1::text[]) ORDER BY r.tier DESC, r.added_at, r.admin_id`, [PAGEABLE_ROLES]
  )).rows;
}

function maskEmail(e) {
  const [u, d] = String(e || '').split('@');
  return d ? `${u.slice(0, 1)}***@${d}` : '';
}

/* ───────────────────────────── تصمیم (زیرِ قفل) ───────────────────────────── */

const PHASE = { alert: 'هشدارِ بحرانی', escalation: 'پیگیری — هنوز کسی «دیدم» نزده', resolved: 'برطرف شد' };

function message(kind, a, level) {
  const id = a.alert_id || a.id;
  const head = kind === 'escalation' ? `${PHASE.escalation} (${level})` : PHASE[kind];
  const since = new Date(Number(a.live_since)).toISOString().replace('T', ' ').slice(0, 16);
  const text = [
    `${head}`, `${a.title}`, `قاعده: ${a.rule} · هدف: ${a.target}`, `از: ${since} (UTC) · تکرار: ${Number(a.occurrences) || 1}`,
    `شناسهٔ هشدار: ${id}`,
    kind === 'resolved' ? 'این رخداد بسته شد.' : 'پنلِ مرکزی ⇐ هشدارها ⇐ همین شناسه ⇐ «دیدم» (پیگیری می‌ایستد).',
  ].join('\n');
  return {
    alertId: id, kind, level, subject: `[${head}] ${a.title}`.slice(0, 200), text,
    push: { title: head, body: String(a.title).slice(0, 180), data: { kind: 'ops_alert', alertId: id, phase: kind, level }, channel: 'alerts' },
  };
}

async function nextAllowed(c, key) {
  const r = (await c.query('SELECT MAX(first_sent_at) AS last FROM ops_alert_pages WHERE dedup_key = $1', [key])).rows[0];
  return r?.last == null ? 0 : Number(r.last) + LIMITS.perConditionMs;
}

async function setPage(c, alertId, t, fields) {
  const cols = Object.keys(fields);
  const sets = cols.map((k, i) => `${k} = $${i + 3}`).join(', ');
  await c.query(`UPDATE ops_alert_pages SET ${sets}, updated_at = $2 WHERE alert_id = $1`, [alertId, t, ...cols.map((k) => fields[k])]);
}

async function plan(c, t, cfg) {
  const got = (await c.query('SELECT pg_try_advisory_xact_lock(hashtext($1)) AS ok', [LOCK])).rows[0];
  if (!got?.ok) return null;
  const rcpts = await activeRecipients(c);
  const primary = rcpts.filter((r) => r.tier === 'primary');
  const sends = [];
  let budget = LIMITS.maxSendsPerRun;
  const nextEsc = (level) => (level < cfg.maxEscalations ? t + cfg.escalateAfterMs : null);
  const fire = (kind, a, level, to) => { budget -= 1; sends.push({ ...message(kind, a, level), to }); };

  const rows = (await c.query(
    `SELECT p.*, a.status AS alert_status, a.resolution, a.resolved_at, a.rule, a.target, a.title, a.live_since, a.occurrences
       FROM ops_alert_pages p JOIN ops_alerts a ON a.id = p.alert_id
      WHERE p.state IN ('sent','suppressed','acked') OR (p.state IN ('resolved_sent','closed') AND a.status <> 'resolved')
      ORDER BY p.created_at, p.alert_id LIMIT 500`
  )).rows;
  //  «خبرِ باز» فقط در sent/acked است: فقط همان‌ها «بسته شد» می‌گیرند؛ پس‌زده بی‌صدا بسته می‌شود
  for (const r of rows) {
    const level = Number(r.level);
    if (r.alert_status === 'resolved') {
      if (r.state === 'suppressed') { await setPage(c, r.alert_id, t, { state: 'closed', next_at: null }); continue; }
      const quiet = r.resolution === 'manual' || Number(r.resolved_at) <= t - LIMITS.resolveQuietMs;
      if (!quiet || budget <= 0) continue;
      await setPage(c, r.alert_id, t, { state: 'resolved_sent', resolved_sent_at: t, last_sent_at: t, next_at: null });
      fire('resolved', r, level, level > 0 ? rcpts : primary);
      continue;
    }
    if (r.alert_status === 'acknowledged') {
      if (r.state === 'sent') await setPage(c, r.alert_id, t, { state: 'acked', next_at: null });
      else if (r.state === 'resolved_sent') await setPage(c, r.alert_id, t, { state: 'closed', next_at: null });
      continue;
    }
    //  باز
    if (r.state === 'sent') {
      if (r.next_at != null && Number(r.next_at) <= t && level < cfg.maxEscalations && budget > 0 && rcpts.length) {
        await setPage(c, r.alert_id, t, { level: level + 1, last_sent_at: t, next_at: nextEsc(level + 1) });
        fire('escalation', r, level + 1, rcpts);
      }
      continue;
    }
    if (r.state === 'acked') {   //  پس از «دیدم» بسته و در مهلتِ چشمک دوباره باز شد ⇒ پیگیری از نو، بی خبرِ تازه
      await setPage(c, r.alert_id, t, { state: 'sent', next_at: nextEsc(level) });
      continue;
    }
    //  پس‌زده، یا بسته‌شده و دوباره باز (چشمک یا پس از «بسته شد»)
    if (r.state === 'suppressed' && r.next_at != null && Number(r.next_at) > t) continue;
    const allowAt = await nextAllowed(c, r.dedup_key);
    if (allowAt > t) { await setPage(c, r.alert_id, t, { state: 'suppressed', next_at: allowAt }); continue; }
    if (!primary.length || budget <= 0) continue;
    await setPage(c, r.alert_id, t, { state: 'sent', level: 0, first_sent_at: t, last_sent_at: t, next_at: nextEsc(0), resolved_sent_at: null });
    fire('alert', r, 0, primary);
  }

  const fresh = (await c.query(
    `SELECT a.* FROM ops_alerts a
      WHERE a.status = 'open' AND a.severity = 'critical'
        AND NOT EXISTS (SELECT 1 FROM ops_alert_pages p WHERE p.alert_id = a.id)
      ORDER BY a.live_since, a.id LIMIT 100`
  )).rows;
  let waiting = 0;
  for (const a of fresh) {
    if (!primary.length || budget <= 0) { waiting += 1; continue; }
    const allowAt = await nextAllowed(c, a.dedup_key);
    const sent = allowAt <= t;
    const ins = (await c.query(
      `INSERT INTO ops_alert_pages (alert_id, dedup_key, state, level, first_sent_at, last_sent_at, next_at, created_at, updated_at)
       VALUES ($1,$2,$3,0,$4,$4,$5,$6,$6) ON CONFLICT (alert_id) DO NOTHING RETURNING alert_id`,
      [a.id, a.dedup_key, sent ? 'sent' : 'suppressed', sent ? t : null, sent ? nextEsc(0) : allowAt, t]
    )).rows[0];
    if (ins && sent) fire('alert', a, 0, primary);
  }
  return { sends, recipients: rcpts.length, primary: primary.length, waiting };
}

/* ───────────────────────────── فرستادن (پس از ثبت) ───────────────────────────── */

/** درِ آزمون — مثلِ `push.setDeliver`: در کارِ عادی هیچ‌وقت مقدار نمی‌گیرد. */
let transport = null;
function _setTransport(fn) { transport = typeof fn === 'function' ? fn : null; }

const errText = (err) => require('./ops-errors').scrub(String(err?.code || err?.message || err?.name || 'error'), 120);

async function sendEmail(r, m) {
  if (transport) return transport('email', r, m);
  const res = await require('./mailer').send({ to: r.email, subject: m.subject, text: m.text });
  return res?.via === 'log' ? { ok: false, detail: 'email_log_only' } : { ok: Boolean(res?.delivered), detail: res?.via || '' };
}

async function sendPush(r, m) {
  if (transport) return transport('push', r, m);
  const res = await require('./push').sendTo({ adminId: r.admin_id }, m.push);
  return { ok: Number(res?.sent) > 0, detail: res?.skipped || res?.error || `sent:${Number(res?.sent) || 0}` };
}

async function deliver(s, t) {
  const tries = [];
  for (const r of s.to) {
    const chans = r.email ? [['email', sendEmail], ['push', sendPush]] : [['push', sendPush]];
    for (const [channel, fn] of chans) {
      let res;
      try { res = await fn(r, s); } catch (err) { res = { ok: false, detail: errText(err) }; }
      tries.push({ adminId: r.admin_id, channel, ok: res?.ok === true, detail: String(res?.detail || '').slice(0, 120) });
    }
  }
  const delivered = tries.filter((x) => x.ok).length;
  try {
    await tx(async (c) => {
      for (const x of tries) {
        await c.query(
          `INSERT INTO ops_alert_page_log (id, alert_id, kind, level, admin_id, channel, ok, detail, at) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9)`,
          [newId('opg'), s.alertId, s.kind, s.level, x.adminId, x.channel, x.ok, x.detail, t]
        );
      }
      await c.query(
        `UPDATE ops_alert_pages SET sends = sends + 1, last_result = $2::jsonb WHERE alert_id = $1`,
        [s.alertId, JSON.stringify({ kind: s.kind, level: s.level, at: t, attempted: tries.length, delivered })]
      );
    });
  } catch (err) { console.error('[ops-paging] ثبتِ نتیجه', err.message); }
  if (!delivered) console.warn(`[ops-paging] ${s.kind} ِ ${s.alertId} به هیچ گیرنده‌ای نرسید`);
  return { alertId: s.alertId, kind: s.kind, level: s.level, attempted: tries.length, delivered };
}

let inflight = null;

/** یک دور: تصمیم زیرِ قفل ⇒ ثبت ⇒ فرستادن. پرچمِ خاموش ⇒ هیچ پرسشی جز خودِ پرچم. */
async function run({ t = now() } = {}) {
  if (!(await enabled())) return { state: 'off' };
  if (inflight) return inflight;
  inflight = (async () => {
    try {
      const cfg = await settings();
      const p = await tx((c) => plan(c, t, cfg));
      if (!p) return { state: 'busy' };
      const results = [];
      for (const s of p.sends) results.push(await deliver(s, t));
      await db.query('DELETE FROM ops_alert_page_log WHERE at < $1', [t - LIMITS.logKeepMs]).catch(() => null);
      return { state: 'ok', checkedAt: t, recipients: p.recipients, primary: p.primary, waiting: p.waiting, results };
    } finally {
      inflight = null;
    }
  })();
  return inflight;
}

/* ───────────────────────────── API ِ پنل ───────────────────────────── */

async function overview({ reveal = false } = {}) {
  const cfg = await settings();
  const recipients = (await many(
    `SELECT r.admin_id, r.tier, r.email, r.added_at, a.username, a.name, a.role, a.status FROM ops_alert_recipients r
       JOIN admins a ON a.id = r.admin_id ORDER BY r.tier DESC, r.added_at, r.admin_id`
  )).map((r) => ({
    adminId: r.admin_id, username: r.username, name: r.name, role: r.role, tier: r.tier,
    email: reveal ? r.email : maskEmail(r.email), hasEmail: Boolean(r.email),
    active: r.status === 'active' && PAGEABLE_ROLES.includes(r.role), addedAt: Number(r.added_at),
  }));
  const recent = (await many(
    `SELECT alert_id, kind, level, admin_id, channel, ok, detail, at FROM ops_alert_page_log ORDER BY at DESC, id DESC LIMIT 30`
  )).map((r) => ({ alertId: r.alert_id, kind: r.kind, level: Number(r.level), adminId: r.admin_id, channel: r.channel, ok: r.ok === true, detail: r.detail, at: Number(r.at) }));
  return {
    enabled: cfg.enabled, flag: FLAG, settings: { escalateMin: cfg.escalateMin, maxEscalations: cfg.maxEscalations },
    limits: { ...LIMITS, crit: CRIT, criticalJobs: CRITICAL_JOBS, roles: PAGEABLE_ROLES },
    channels: ['email', 'push'], recipients, recent,
  };
}

function cleanRecipients(list) {
  if (!Array.isArray(list)) throw badRequest('فهرستِ گیرنده‌ها (recipients) لازم است', 'recipients_required');
  if (list.length > LIMITS.maxRecipients) throw badRequest(`حداکثر ${LIMITS.maxRecipients} گیرنده`, 'too_many_recipients');
  const v = require('./validate');
  const seen = new Set();
  const out = list.map((it) => {
    const adminId = v.id(it?.adminId, { field: 'شناسهٔ مدیر' });
    if (seen.has(adminId)) throw badRequest('یک مدیر دو بار آمده است', 'duplicate_recipient');
    seen.add(adminId);
    const tier = it?.tier == null || it.tier === '' ? 'primary' : String(it.tier);
    if (!TIERS.includes(tier)) throw badRequest('ردهٔ گیرنده primary یا escalation است', 'bad_tier');
    const email = String(it?.email ?? '').trim().toLowerCase();
    if (email && (email.length > 254 || !EMAIL_RE.test(email))) throw badRequest('نشانیِ ایمیل درست نیست', 'bad_email');
    return { adminId, tier, email };
  });
  if (out.length && !out.some((r) => r.tier === 'primary')) throw badRequest('دست‌کم یک گیرندهٔ اصلی لازم است', 'primary_required');
  return out;
}

function cleanSetting(raw, lim, field) {
  if (raw === undefined || raw === null || raw === '') return undefined;
  const n = Number(raw);
  if (!Number.isInteger(n) || n < lim.min || n > lim.max) throw badRequest(`${field} عددِ درستِ ${lim.min} تا ${lim.max} است`, 'bad_setting');
  return n;
}

/** فهرستِ گیرنده‌ها را یک‌جا جایگزین می‌کند (فقط مدیرِ ارشد — در خودِ مسیر). */
async function configure(req, body = {}, { ip = '' } = {}) {
  const list = cleanRecipients(body.recipients);
  const escalateMin = cleanSetting(body.escalateMin, LIMITS.escalateMin, 'دقیقهٔ پیگیری');
  const maxEscalations = cleanSetting(body.maxEscalations, LIMITS.maxEscalations, 'شمارِ پیگیری');
  const by = require('./admin-service').label(req) || String(req.admin?.id || '');
  const t = now();
  await tx(async (c) => {
    const ids = list.map((r) => r.adminId);
    const found = new Map((await c.query('SELECT id, role, status FROM admins WHERE id = ANY($1::text[])', [ids])).rows.map((r) => [r.id, r]));
    for (const id of ids) {
      const a = found.get(id);
      if (!a || a.status !== 'active' || !PAGEABLE_ROLES.includes(a.role)) {
        throw badRequest('گیرنده فقط مدیرِ فعالِ پلتفرم با نقشِ admin یا superadmin است', 'recipient_not_platform_admin');
      }
    }
    await c.query('DELETE FROM ops_alert_recipients');
    for (const r of list) {
      await c.query('INSERT INTO ops_alert_recipients (admin_id, tier, email, added_by, added_at) VALUES ($1,$2,$3,$4,$5)',
        [r.adminId, r.tier, r.email, by, t]);
    }
    await require('./audit').log({
      actorType: 'admin', userId: req.admin.id, ip, action: 'admin.ops_paging_updated', targetType: 'ops_paging', targetId: 'recipients',
      detail: { recipients: list.map((r) => ({ adminId: r.adminId, tier: r.tier, email: Boolean(r.email) })),
        ...(escalateMin !== undefined ? { escalateMin } : {}), ...(maxEscalations !== undefined ? { maxEscalations } : {}) },
    }, { client: c });
  });
  const live = require('./live-config');
  if (escalateMin !== undefined) await live.set(FLAG.scope, SETTING_KEYS.escalateMin, escalateMin, by);
  if (maxEscalations !== undefined) await live.set(FLAG.scope, SETTING_KEYS.maxEscalations, maxEscalations, by);
  return overview({ reveal: true });
}

/* ───────────────────────────── زمان‌بندی ───────────────────────────── */

let timer = null;
let beat = null;
function start() {
  if (timer) return;
  timer = setInterval(async () => {
    try {
      if (!(await enabled())) return;
      //  نبضِ کار فقط با پرچمِ روشن ثبت می‌شود ⇒ پرچمِ خاموش کارتِ سلامت را عوض نمی‌کند
      if (!beat) beat = require('./job-beat').wrap('ops-paging', LIMITS.tickMs, () => run());
      await beat();
    } catch (err) { console.error('[ops-paging]', err.message); }
  }, LIMITS.tickMs);
  if (timer.unref) timer.unref();
}
function stop() { if (timer) clearInterval(timer); timer = null; }

module.exports = {
  FLAG, SETTING_KEYS, LIMITS, CRIT, CRITICAL_JOBS, PAGEABLE_ROLES,
  enabled, settings, criticalJobs, criticalFindings, run, overview, configure, start, stop, _setTransport,
};
