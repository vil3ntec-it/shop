'use strict';
/**
 * سلامتِ سرور برای پنلِ مدیر — v3 §۳۸.۸ و سناریوی OPS-01@38.
 *
 * «مدیر وضعیتِ سلامتِ API، دیتابیس و صف را می‌بیند؛ دادهٔ محرمانه در
 * داشبورد نمایش داده نمی‌شود.» `/health` و `/ready` فقط «سرور روشن است و
 * دیتابیس جواب می‌دهد» را می‌گفتند؛ صفِ ایمیل، صفِ تلگرام، خطاهای برنامه‌ها،
 * تعارضِ همگام‌سازی، فضای دیسک و سنِ آخرین پشتیبان هیچ‌جا یک‌جا دیده نمی‌شد.
 *
 * ⛔ فقط عدد و وضعیت. نه نشانیِ دیتابیس، نه مسیرِ پوشه، نه ایمیل، نه متنِ
 *    خطا، نه هیچ کلید و رمزی — آزمون همهٔ مقدارهای رازِ `config` را در
 *    پاسخ می‌گردد.
 * ⛔ فقط‌خواندنی: هیچ چیزی نمی‌نویسد، هیچ صفی را جلو نمی‌برد.
 * ⛔ هر بخش جدا شکست می‌خورد: یک پرس‌وجوی ناموفق آن بخش را `unknown`
 *    می‌کند، نه کلِ پاسخ را ۵۰۰.
 *
 * وضعیتِ هر بخش: ok | warn | down | unknown؛ وضعیتِ کل بدترینِ آن‌هاست.
 */
const fsp = require('fs/promises');
const config = require('../config');
const { one, many, now, driverName } = require('../db');

const HOUR = 60 * 60 * 1000;
const DAY = 24 * HOUR;

/** آستانه‌ها — عدد، نه حدس: هر کدام در آزمون قفل است. */
const LIMITS = Object.freeze({
  dbSlowMs: 500,
  outboxStuckMs: 5 * 60 * 1000,
  diskWarnPct: 10,
  diskDownPct: 2,
  //  پشتیبانِ قدیمی‌تر از دو برابرِ فاصلهٔ زمان‌بندی ⇒ هشدار
  backupLateFactor: 2,
});

const RANK = { ok: 0, unknown: 1, warn: 2, down: 3 };
const worst = (list) => list.reduce((w, s) => (RANK[s] > RANK[w] ? s : w), 'ok');

async function safe(fn) {
  try { return await fn(); } catch { return { status: 'unknown' }; }
}

const ageOf = (at, t) => (at == null ? null : Math.max(0, t - Number(at)));

async function database() {
  const t0 = process.hrtime.bigint();
  let up = false;
  try { up = Number((await one('SELECT 1 AS ok'))?.ok) === 1; } catch { up = false; }
  const latencyMs = Number((process.hrtime.bigint() - t0) / 1_000_000n);
  return {
    status: !up ? 'down' : latencyMs > LIMITS.dbSlowMs ? 'warn' : 'ok',
    latencyMs,
    driver: driverName(),
  };
}

async function emailOutbox(t) {
  const r = await one(
    `SELECT COUNT(*) FILTER (WHERE status='queued')::int AS queued,
            COUNT(*) FILTER (WHERE status='sending')::int AS sending,
            COUNT(*) FILTER (WHERE status='failed' AND reason <> 'expired' AND created_at > $1)::int AS failed24h,
            MIN(created_at) FILTER (WHERE status IN ('queued','sending')) AS oldest
       FROM otp_outbox`,
    [t - DAY]
  );
  let worker = 'down';
  try { worker = require('./login-outbox').workerStatus(); } catch { worker = 'down'; }
  const oldestAgeMs = ageOf(r.oldest, t);
  const stuck = oldestAgeMs != null && oldestAgeMs > LIMITS.outboxStuckMs;
  return {
    status: stuck ? 'warn' : worker === 'up' ? 'ok' : 'warn',
    worker,
    queued: r.queued, sending: r.sending, failed24h: r.failed24h, oldestAgeMs,
  };
}

async function telegramOutbox(t) {
  const r = await one(
    `SELECT COUNT(*) FILTER (WHERE sent_at IS NULL AND failed_at IS NULL)::int AS pending,
            COUNT(*) FILTER (WHERE failed_at IS NOT NULL AND failed_at > $1)::int AS failed24h,
            MIN(created_at) FILTER (WHERE sent_at IS NULL AND failed_at IS NULL) AS oldest
       FROM telegram_outbox`,
    [t - DAY]
  );
  const oldestAgeMs = ageOf(r.oldest, t);
  return {
    status: oldestAgeMs != null && oldestAgeMs > LIMITS.outboxStuckMs ? 'warn' : 'ok',
    pending: r.pending, failed24h: r.failed24h, oldestAgeMs,
  };
}

/*
 *  ── تأخیرِ همگام‌سازی — OPS-04 (§۶.۲۴)، پشتِ پرچمِ `ops.sync_lag` ─────────
 *  live_config، scope ‏`ops`، پیش‌فرض خاموش؛ فقط `true` روشن است. خاموش ⇒ بخشِ «همگام‌سازی»
 *  دقیقاً همان پیشین (بی `lag`).
 *
 *  فقط از `sync_devices` (یک ردیف برای هر دستگاهِ Sync v1) و سرِ دفترِ هر حساب (`MAX(server_seq)`
 *  روی `idx_oplog_account_seq`، یک جست‌وجوی نمایه برای هر حساب) — هیچ پیمایشِ oplog نیست.
 *    devices         دستگاه‌های دیده‌شده در `windowDays` روزِ اخیر (حداکثر `maxDevices` تازه‌ترین)
 *    behindDevices   cursor ِ دستگاه پایین‌تر از سرِ دفترِ حسابش؛ maxBehindOps بیشینهٔ فاصله
 *    queuedDevices   صفِ گزارش‌شدهٔ خودِ دستگاه در آخرین push (`queued_count`) > ۰؛ queuedOps جمعش
 *    stale24h        آخرین push/pull ِ موفق بیش از ۲۴ ساعت پیش
 *    stuck           در `stuckMs` ِ اخیر دیده شده (push یا pull کرده)، عقب است و `stuckMs` است pull نکرده
 *    lastSyncAge*    سنِ آخرین push/pull (میانه و p95)
 *  ⚠️ کارِ آفلاین سقفِ زمانی ندارد (D-05) ⇒ عقب‌ماندگی و کهنگی فقط شمرده می‌شوند؛ تنها `stuck`
 *     (آنلاین ولی بی pull) کارت را `warn` می‌کند.
 *  ⛔ فقط عدد؛ هیچ شناسهٔ دستگاه، حساب یا دکان.
 */
const SYNC_LAG_FLAG = Object.freeze({ scope: 'ops', key: 'ops.sync_lag' });
const SYNC_LAG_LIMITS = Object.freeze({ windowDays: 30, maxDevices: 5000, stuckMs: HOUR });

async function syncLagEnabled() {
  const r = await one('SELECT value FROM live_config WHERE scope = $1 AND key = $2', [SYNC_LAG_FLAG.scope, SYNC_LAG_FLAG.key]);
  if (!r) return false;
  try { return JSON.parse(r.value) === true; } catch { return false; }
}

async function syncLag(t) {
  const L = SYNC_LAG_LIMITS;
  const r = await one(
    `WITH d AS (
       SELECT app, account_kind, account_id, cursor, queued_count, last_pull_at, last_seen_at,
              GREATEST(COALESCE(last_push_at, 0), COALESCE(last_pull_at, 0)) AS synced_at
         FROM sync_devices
        WHERE last_seen_at > $1
        ORDER BY last_seen_at DESC
        LIMIT $2
     ), heads AS MATERIALIZED (
       --  یک بار برای هر حساب؛ بی MATERIALIZED برنامه‌ریز زیرپرس‌وجو را برای هر دستگاه و هر بار
       --  که «behind» خوانده می‌شود دوباره می‌زد (۶k دستگاه، ۱k حساب: ۲۷۵ms ⇐ اندازه‌گیری در PR)
       SELECT a.app, a.account_kind, a.account_id,
              (SELECT COALESCE(MAX(o.server_seq), 0) FROM oplog o
                WHERE o.app = a.app AND o.account_kind = a.account_kind AND o.account_id = a.account_id) AS head
         FROM (SELECT DISTINCT app, account_kind, account_id FROM d) a
     ), j AS (
       SELECT d.*, GREATEST(heads.head - d.cursor, 0) AS behind
         FROM d JOIN heads USING (app, account_kind, account_id)
     )
     SELECT COUNT(*)::int AS devices,
            COUNT(*) FILTER (WHERE behind > 0)::int AS behind_devices,
            COALESCE(MAX(behind), 0)::bigint AS max_behind,
            COUNT(*) FILTER (WHERE queued_count > 0)::int AS queued_devices,
            COALESCE(SUM(queued_count), 0)::bigint AS queued_ops,
            COUNT(*) FILTER (WHERE synced_at < $3)::int AS stale,
            COUNT(*) FILTER (WHERE behind > 0 AND last_seen_at > $4 AND COALESCE(last_pull_at, 0) < $4)::int AS stuck,
            percentile_disc(0.5) WITHIN GROUP (ORDER BY $5::bigint - synced_at) AS p50,
            percentile_disc(0.95) WITHIN GROUP (ORDER BY $5::bigint - synced_at) AS p95
       FROM j`,
    [t - L.windowDays * DAY, L.maxDevices, t - DAY, t - L.stuckMs, t]
  );
  const age = (v) => (v == null ? null : Math.max(0, Number(v)));
  const stuck = Number(r.stuck);
  return {
    status: stuck > 0 ? 'warn' : 'ok',
    windowDays: L.windowDays,
    devices: Number(r.devices),
    behindDevices: Number(r.behind_devices),
    maxBehindOps: Number(r.max_behind),
    queuedDevices: Number(r.queued_devices),
    queuedOps: Number(r.queued_ops),
    stale24h: Number(r.stale),
    stuck,
    lastSyncAgeP50Ms: age(r.p50),
    lastSyncAgeP95Ms: age(r.p95),
  };
}

/** خطاهای گزارش‌شدهٔ برنامه‌ها و تعارض‌های همگام‌سازی در ۲۴ ساعت — شمرده، نه فهرست. */
async function sync(t) {
  const e = await one('SELECT COUNT(*)::int AS n FROM client_errors WHERE at > $1', [t - DAY]);
  const c = await one('SELECT COUNT(*)::int AS n FROM sync_conflicts WHERE at > $1', [t - DAY]);
  const out = { status: 'ok', clientErrors24h: e.n, conflicts24h: c.n };
  //  پرچم یا پرس‌وجوی تأخیر شکست بخورد ⇒ فقط `lag` نامعلوم، نه شمارش‌های بالا
  let on = false;
  try { on = await syncLagEnabled(); } catch { return { ...out, lag: { status: 'unknown' } }; }
  if (!on) return out;
  const lag = await safe(() => syncLag(t));
  return { ...out, status: worst([out.status, lag.status]), lag };
}

async function storage() {
  const dir = config.backup.dir;
  await fsp.mkdir(dir, { recursive: true });
  const s = await fsp.statfs(dir);
  const totalBytes = Number(s.blocks) * Number(s.bsize);
  const freeBytes = Number(s.bavail) * Number(s.bsize);
  const freePct = totalBytes > 0 ? Math.round((freeBytes / totalBytes) * 1000) / 10 : null;
  let status = 'unknown';
  if (freePct != null) status = freePct < LIMITS.diskDownPct ? 'down' : freePct < LIMITS.diskWarnPct ? 'warn' : 'ok';
  return { status, freeBytes, totalBytes, freePct };
}

async function backups(t) {
  const list = await require('./backup').list();
  const last = list[0] || null;
  const ageMs = last ? ageOf(last.createdAt, t) : null;
  const enabled = config.backup.enabled === true;
  const late = ageMs == null || ageMs > config.backup.intervalMs * LIMITS.backupLateFactor;
  return {
    status: !enabled ? 'warn' : late ? 'warn' : 'ok',
    enabled,
    //  فقط «رمز دارد یا نه» — خودِ عبارتِ عبور هرگز
    encryption: Boolean(config.backup.passphrase),
    count: list.length,
    last: last ? { at: Number(last.createdAt), kind: last.kind, bytes: last.bytes, encrypted: last.encrypted === true } : null,
    ageMs,
  };
}

/** عکسِ فوریِ سلامت. */
async function snapshot() {
  const t = now();
  const mem = process.memoryUsage();
  const api = {
    status: 'ok',
    version: require('../../package.json').version,
    uptimeSeconds: Math.round(process.uptime()),
    rssBytes: mem.rss,
  };
  const [db, email, telegram, syncState, disk, backup] = await Promise.all([
    safe(database), safe(() => emailOutbox(t)), safe(() => telegramOutbox(t)),
    safe(() => sync(t)), safe(storage), safe(() => backups(t)),
  ]);
  const parts = { api, database: db, queues: { email, telegram }, sync: syncState, storage: disk, backups: backup };
  const statuses = [api.status, db.status, email.status, telegram.status, syncState.status, disk.status, backup.status];
  return { status: worst(statuses), checkedAt: t, limits: LIMITS, ...parts };
}

/*
 *  ── سرویس‌های بیرونی — OPS-04@29 (§۲۹.۸) ────────────────────────────
 *  «provider ِ آزمایشی به‌عنوانِ سرویسِ زنده معرفی نشود.» هر راهی که سرور با
 *  آن چیزی به آدم می‌رساند یا پول می‌گیرد، با حالتِ واقعی‌اش:
 *    live        راهِ واقعی انتخاب و کامل است
 *    mock        آزمایشی: فقط لاگِ سرور (`log`) — به هیچ گوشی یا صندوقی نمی‌رسد
 *    incomplete  راهِ واقعی انتخاب شده ولی چیزی کم است ⇒ ارسال شکست می‌خورد
 *    off         مدیر خاموشش کرده
 *    none        این سرویس اصلاً provider ندارد (پرداختِ آنلاین D-08، مدل‌های عکس)
 *    unknown     خواندنِ تنظیم شکست خورد
 *  ⛔ هرگز «live» برای `log` یا راهِ ناشناخته. ⛔ فقط نام، راه و حالت؛ نه نشانی،
 *     نه کلید، نه فرستنده، نه میزبان — `missing` فقط نامِ فیلدِ کم است.
 */
const PROVIDER_STATES = Object.freeze(['live', 'mock', 'incomplete', 'off', 'none', 'unknown']);

/** کدِ ورودِ شماره — همان انتخابِ `otp.sender()`: log | sms | webhook | whatsapp. */
function smsState(s) {
  const mode = String(s.provider || 'log');
  const missing = [];
  if (mode === 'log') return { mode, state: 'mock', missing };
  if (mode === 'sms') {
    if (!s.url) missing.push('نشانیِ سرویسِ پیامک');
  } else if (mode === 'webhook') {
    if (!config.otp.webhookUrl) missing.push('OTP_WEBHOOK_URL');
  } else if (mode === 'whatsapp') {
    if (!config.whatsapp.token) missing.push('WHATSAPP_TOKEN');
    if (!config.whatsapp.phoneId) missing.push('WHATSAPP_PHONE_ID');
  } else {
    missing.push('راهِ ارسالِ شناخته‌شده (log، sms، webhook، whatsapp)');
  }
  return { mode, state: missing.length ? 'incomplete' : 'live', missing };
}

/** ایمیل (کدِ ورود، فراموشیِ رمز، اعلان) — `mailer.send()`: log | smtp | api. */
function emailState(s, readiness) {
  const mode = String(s.provider || 'log');
  if (mode === 'log') return { mode, state: 'mock', missing: [] };
  if (mode !== 'smtp' && mode !== 'api') {
    return { mode, state: 'incomplete', missing: ['راهِ ارسالِ شناخته‌شده (log، smtp، api)'] };
  }
  const r = readiness(s);
  return { mode, state: r.ok ? 'live' : 'incomplete', missing: r.missing };
}

/** پوش — `push.sendTo()` بی `enabled` یا بی حسابِ سرویس هیچ نمی‌فرستد. */
function pushState(s) {
  if (!s.enabled) return { mode: 'fcm', state: 'off', missing: [] };
  if (!s.serviceAccount) return { mode: 'fcm', state: 'incomplete', missing: ['فایلِ حسابِ سرویسِ FCM'] };
  return { mode: 'fcm', state: 'live', missing: [] };
}

async function providerSafe(id, fn) {
  try { return { id, ...(await fn()) }; } catch { return { id, mode: '', state: 'unknown', missing: [] }; }
}

/** فهرستِ همهٔ سرویس‌های بیرونی با حالتِ واقعی — فقط‌خواندنی. */
async function providers() {
  const list = await Promise.all([
    providerSafe('sms', async () => smsState(await require('./sms-settings').current())),
    providerSafe('email', async () => {
      const mailer = require('./mailer');
      return emailState(await mailer.current(), mailer.readiness);
    }),
    providerSafe('push', async () => pushState(await require('./push').current())),
    //  پرداختِ آنلاین: بی کلیدِ حساب‌پی «none» (فقط نقدِ هنگامِ تحویل، D-08)؛ کلیدها ⇒ live پشتِ پرچمِ
    //  `pay.hesabpay` ِ هر دکان؛ ساختگی (فقط بیرون از تولید) ⇒ mock — `payment-providers.providerState`
    providerSafe('payment', async () => require('./payment-providers').providerState()),
    //  مرحله‌های بیرونیِ عکس ۵۰۳ `step_unavailable` می‌دهند و هیچ چیزی نمی‌فرستند
    providerSafe('image_ai', async () => ({
      mode: '', state: 'none', missing: [],
      steps: require('./image-pipeline').STEPS.filter((x) => x.status === 'blocked_external').map((x) => x.id),
    })),
  ]);
  const counts = Object.fromEntries(PROVIDER_STATES.map((k) => [k, 0]));
  for (const p of list) counts[p.state] += 1;
  return {
    checkedAt: now(),
    //  بیرون از production کدِ راهِ `log` در خودِ پاسخ برمی‌گردد (`devCode`)
    production: config.env === 'production',
    counts,
    providers: list,
  };
}

/*
 *  ── نسخهٔ دستگاه‌ها — OPS-04 (§۶.۲۴) ───────────────────────────────
 *  «چند دستگاه روی کدام نسخه‌اند و چند تا زیرِ حداقل یا کهنه مانده‌اند.»
 *  منبع همان تعریفِ «نصب» در `admin-sync` /version-health است: دستگاه‌هایی که
 *  همگام شده‌اند (`sync_devices` و `sync_v3_devices`؛ هر دستگاه یک بار، با
 *  تازه‌ترین ردیف). ⛔ `tokens.app_version` نه: تازه‌سازیِ نشست نسخه را نمی‌برد
 *  و پس از نخستین refresh خالی می‌شد.
 *
 *  هشدار (بی جدولِ تازه — هر بار از داده حساب می‌شود):
 *    down  ⇐ دستگاهِ فعال زیرِ حداقل نسخهٔ همان برنامه (`min_app_version_<app>`)
 *    warn  ⇐ دستگاهِ فعال پایین‌تر از آخرین نسخهٔ منتشرشده (`latest_version_<app>`)
 *  آخرین نسخه ثبت نشده ⇒ «کهنه» سنجیده نمی‌شود (`outdated: null`)، نه حدس.
 *  ⛔ فقط شمار و نسخه؛ نه شناسهٔ حساب، نه دستگاه.
 */
const VERSION_LIMITS = Object.freeze({ activeDays: 30, maxVersionsPerApp: 50, maxGroups: 1000 });
const BASE_APPS = Object.freeze(['shop', 'pump']);
const APP_RE = /^[a-z0-9][a-z0-9-]{0,39}$/;

/** گروه‌های خامِ (برنامه، نسخه، شمار) ⇒ داشبوردِ هر برنامه. */
function versionReport(rows, cfg) {
  const compat = require('./app-compat');
  const apps = new Map(BASE_APPS.map((a) => [a, new Map()]));
  for (const r of rows) {
    const app = String(r.app || '');
    if (!APP_RE.test(app)) continue;
    if (!apps.has(app)) apps.set(app, new Map());
    const v = compat.valid(r.app_version) || '';
    const cur = apps.get(app).get(v) || { devices: 0, lastSeenAt: 0 };
    cur.devices += Number(r.n) || 0;
    cur.lastSeenAt = Math.max(cur.lastSeenAt, Number(r.last_seen) || 0);
    apps.get(app).set(v, cur);
  }
  const out = [];
  for (const [app, groups] of apps) {
    const latest = compat.valid(cfg[`latest_version_${app}`]);
    const minVersion = compat.minVersionOf(cfg, app);
    let devices = 0; let belowMin = 0; let outdated = 0; let unknown = 0;
    const versions = [];
    for (const [version, g] of groups) {
      let state = 'current';
      if (!version) state = 'unknown';
      else if (minVersion && compat.compare(version, minVersion) < 0) state = 'below_min';
      else if (latest && compat.compare(version, latest) < 0) state = 'outdated';
      devices += g.devices;
      if (state === 'unknown') unknown += g.devices;
      if (state === 'below_min') belowMin += g.devices;
      if (state === 'outdated') outdated += g.devices;
      versions.push({ version, devices: g.devices, lastSeenAt: g.lastSeenAt || null, state });
    }
    //  سقفِ نمایش: پرشمارترین نسخه‌ها می‌مانند (نه تازه‌ترین‌ها — وگرنه همان نسخه‌های
    //  کهنهٔ زیرِ حداقل بریده می‌شدند)، بعد از تازه به کهنه؛ شمارها از همه است
    const shown = versions.length > VERSION_LIMITS.maxVersionsPerApp
      ? [...versions].sort((a, b) => b.devices - a.devices).slice(0, VERSION_LIMITS.maxVersionsPerApp)
      : versions;
    shown.sort((a, b) => (!a.version ? 1 : !b.version ? -1 : compat.compare(b.version, a.version)));
    out.push({
      app, latest, minVersion, devices, unknown, belowMin,
      outdated: latest ? outdated : null,
      status: belowMin ? 'down' : latest && outdated ? 'warn' : 'ok',
      versions: shown,
      versionsTruncated: shown.length < versions.length,
    });
  }
  return out;
}

/** داشبوردِ نسخه‌ها: دستگاه‌های دیده‌شده در `activeDays` روزِ گذشته. */
/*
 *  هر دستگاه (برنامه، حساب، دستگاه) یک بار: نسخه از تازه‌ترین ردیفی که نسخه
 *  دارد — ردیفِ تازه‌ترِ بی‌نسخه به ردیفِ دیگرِ همان دستگاه برمی‌گردد — و
 *  «آخرین دیده‌شدن» بیشینهٔ هر دو جدول. فعال = دیده‌شده در `activeDays`.
 *  `v2.14.4` همان `2.14.4` است (در خودِ پرس‌وجو، تا گروه‌بندی و سقف درست باشد).
 *  گروه‌ها پرشمار به کم‌شمار؛ بیش از `maxGroups` ⇒ `truncated`.
 */
async function versions() {
  const t = now();
  const lim = VERSION_LIMITS.maxGroups;
  const rows = await many(
    `SELECT app, app_version, COUNT(*)::int AS n, MAX(seen) AS last_seen
       FROM (SELECT DISTINCT ON (app, account_kind, account_id, device_id)
                    app, regexp_replace(btrim(app_version), '^[vV]', '') AS app_version, MAX(last_seen_at) OVER w AS seen
               FROM (SELECT app, account_kind, account_id, device_id, app_version, last_seen_at FROM sync_devices
                     UNION ALL
                     SELECT app, account_kind, account_id, device_id, app_version, last_seen_at FROM sync_v3_devices) u
             WINDOW w AS (PARTITION BY app, account_kind, account_id, device_id)
              ORDER BY app, account_kind, account_id, device_id, (btrim(app_version) <> '') DESC, last_seen_at DESC) d
      WHERE seen > $1
      GROUP BY app, app_version
      ORDER BY n DESC, app, app_version
      LIMIT ${lim + 1}`,
    [t - VERSION_LIMITS.activeDays * DAY]
  );
  const apps = versionReport(rows.slice(0, lim), await require('./plans').allConfig());
  return {
    checkedAt: t, activeDays: VERSION_LIMITS.activeDays, truncated: rows.length > lim,
    status: worst(apps.map((a) => a.status)), apps,
  };
}

/*
 *  ── بررسیِ زنجیرهٔ هش از پنل — SEC-02 (نیمهٔ پنل) ─────────────────────
 *  همان `verifyAll`ِ `lib/hash-chain-store.js` که `scripts/verify-chain.js`
 *  صدا می‌زند — فقط‌خواندنی، بی هیچ منطقِ تازهٔ سنجش. شکسته‌ها همه می‌آیند
 *  (نخستین حلقهٔ شکسته و دلیل)؛ از سالم‌ها فقط `maxOk` تا (با «سر» برای
 *  یادداشتِ بیرونی) و بقیه فقط شمرده می‌شوند.
 */
const CHAIN_LIMITS = Object.freeze({ maxOk: 100, maxChains: 50 });

async function chainReport({ rows = true, chains = [] } = {}) {
  const store = require('./hash-chain-store');
  const names = (Array.isArray(chains) ? chains : []).map(String).filter(Boolean).slice(0, CHAIN_LIMITS.maxChains);
  const t = now();
  let rep;
  try {
    rep = await store.verifyAll({ chains: names, rows: rows !== false });
  } catch (err) {
    if (err && err.code === 'bad_chain') throw require('../middleware/errors').badRequest(err.message, 'bad_chain');
    throw err;
  }
  const enabled = await store.auditEnabled();
  const pick = (r) => (r.ok
    ? { chain: r.chain, ok: true, length: r.length, head: r.head }
    : { chain: r.chain, ok: false, length: r.length, seq: r.seq, reason: r.reason });
  const broken = rep.results.filter((r) => !r.ok).map(pick);
  const good = rep.results.filter((r) => r.ok);
  return {
    ok: rep.ok, checkedAt: t, enabled, rows: rows !== false,
    total: rep.results.length, okCount: good.length, brokenCount: broken.length,
    broken, healthy: good.slice(0, CHAIN_LIMITS.maxOk).map(pick), truncated: good.length > CHAIN_LIMITS.maxOk,
    reasons: store.REASONS,
  };
}

/*
 *  ── تطبیقِ فروشِ آنلاین برای پنلِ VILL3N — E2E-ADM-15 (§۲۴.۶) ─────────
 *  به‌ازای هر دکان:
 *    • فروش، بازپرداخت، خالص و صفِ اختلاف ── همان `order-refunds.reportMany`
 *      (همان پرس‌وجوهای گزارشِ خودِ دکان، «دادهٔ مرجع»)؛ اختلافِ سفارشِ آزمایشی بیرون.
 *    • لغو و رد ── رخدادهای rejected / auto_rejected / cancelled_by_customer / cancelled.
 *    • نقدِ پیک ── فقط `mode = 'shop_courier'` (همان cashStats ِ DLV-11): گرفته‌شده در بازه،
 *      و «تسویه‌نشده» ِ همین لحظه (بی بازه). نقدِ دریافتِ حضوری و شرکتِ بیرونی نیست.
 *    • تسویه‌های بازه ── تسویه‌ای که همهٔ مأموریت‌هایش آزمایشی است بیرون؛ آمیخته ⇐ سهمِ
 *      آزمایشی (نقد منهای کرایه اگر کرایه از نقد بود) از «باید» و «دریافت» کم می‌شود و
 *      کسری/اضافه از همان دو عدد دوباره حساب می‌شود (بی آزمایشی همان عددِ ثبت‌شده است).
 *  ⛔ نقدِ آزمایشی همه‌جا بیرون است — عمداً با DLV-11 (که آن را می‌شمارد) فرق دارد.
 *    • کارمزد ── تصمیمِ صاحبِ محصول (۱۴۰۵/۰۸): فعلاً بی کارمزد ⇒ ۰. اگر ستون‌های S10
 *      (`platform_fee_minor` / `commission_minor`) روی `online_orders` باشند، جمعِ آن‌ها روی همان
 *      سفارش‌های «فروش» ِ بازه خوانده می‌شود؛ نبودِ ستون ⇒ ۰ (پرس‌وجو نمی‌شکند).
 *  پول همه به واحدِ خُرد (AFN).
 *
 *  فهرستِ دکان‌ها از `shops` با `EXISTS` روی نمایه‌های shop_id‌دار ساخته می‌شود: کارِ بازه،
 *  نقدِ تسویه‌نشده یا اختلافِ ممکن (بی بازه) — هر کدام فقط با سفارشِ غیرِ آزمایشی. جمعِ کل
 *  از **همهٔ** دکان‌ها است؛ فهرست به اهمیت (اختلاف، تسویه‌نشده، خالص) و تا ۲۰۰ ردیف.
 *  بازهٔ روزانه (`fromDate`/`toDate`) در منطقهٔ زمانیِ پیش‌فرض (`config.defaults`).
 */
const RECON_LIMITS = Object.freeze({ defDays: 30, maxDays: 92, maxShops: 200 });
const CANCELLED = Object.freeze(['rejected', 'auto_rejected', 'cancelled_by_customer', 'cancelled']);
const SOLD = Object.freeze(['delivered', 'picked_up']);
const DATE_RE = /^\d{4}-\d{2}-\d{2}$/;
//  فقط همین نام‌ها در SQL می‌آیند (فهرستِ سفید) — نامِ ستون هرگز از ورودی نیست
const FEE_COLUMNS = Object.freeze(['platform_fee_minor', 'commission_minor']);

/** ستون‌های کارمزدِ S10 که اکنون روی `online_orders` هست (شاید هیچ). */
async function feeColumns() {
  const have = new Set((await many(
    `SELECT column_name AS c FROM information_schema.columns
      WHERE table_schema = current_schema() AND table_name = 'online_orders' AND column_name = ANY($1::text[])`,
    [[...FEE_COLUMNS]]
  )).map((r) => r.c));
  return FEE_COLUMNS.filter((c) => have.has(c));
}

/** اختلافِ ساعتِ منطقه با UTC در لحظهٔ `at` (میلی‌ثانیه). */
function tzOffsetMs(at, tz) {
  const p = Object.fromEntries(new Intl.DateTimeFormat('en-US', {
    timeZone: tz, hourCycle: 'h23', year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit',
  }).formatToParts(new Date(at)).map((x) => [x.type, x.value]));
  return Date.UTC(+p.year, +p.month - 1, +p.day, +p.hour % 24, +p.minute, +p.second) - Math.floor(at / 1000) * 1000;
}
/** آغازِ روزِ `YYYY-MM-DD` در منطقهٔ `tz` (با `addDays` برای «روزِ بعد»). */
function dayStart(ymd, tz, addDays = 0) {
  const [y, m, d] = ymd.split('-').map(Number);
  const utc = Date.UTC(y, m - 1, d + addDays);
  const guess = utc - tzOffsetMs(utc, tz);
  return utc - tzOffsetMs(guess, tz);
}
function localDate(at, tz) {
  return new Intl.DateTimeFormat('en-CA', { timeZone: tz, year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date(at));
}

function reconWindow(q = {}, at = now()) {
  const tz = config.defaults.timezone || 'Asia/Kabul';
  const bad = () => require('../middleware/errors').badRequest('بازه معتبر نیست (حداکثر ۹۲ روز)', 'bad_window');
  const num = (v) => {
    if (v === undefined || v === null || v === '') return null;
    const n = Number(v);
    if (!Number.isSafeInteger(n) || n < 0) throw bad();
    return n;
  };
  const date = (v) => {
    if (v === undefined || v === null || v === '') return null;
    //  رفت‌وبرگشت: ۲۰۲۶-۰۲-۳۱ در Date به ۰۳-۰۳ می‌لغزد ⇒ ناممکن است و ۴۰۰
    const d = DATE_RE.test(String(v)) ? new Date(`${v}T00:00:00Z`) : null;
    if (!d || Number.isNaN(d.getTime()) || d.toISOString().slice(0, 10) !== String(v)) throw bad();
    return String(v);
  };
  const fromDate = date(q.fromDate);
  const toDate = date(q.toDate);
  //  روزِ «تا» شامل است ⇒ پایان = آغازِ روزِ بعد در همان منطقه
  const end = toDate ? dayStart(toDate, tz, 1) : num(q.to) ?? at;
  const start = fromDate ? dayStart(fromDate, tz) : num(q.from) ?? end - RECON_LIMITS.defDays * DAY;
  if (start >= end || end - start > RECON_LIMITS.maxDays * DAY) throw bad();
  return { from: start, to: end, timezone: tz, fromDate: localDate(start, tz), toDate: localDate(end - 1, tz) };
}

/** دکان‌هایی که چیزی برای نشان دادن دارند — هر EXISTS روی نمایهٔ shop_id‌دارِ همان جدول. */
async function reconShops(w) {
  return many(
    `SELECT s.id AS shop_id, s.name FROM shops s
      WHERE EXISTS (SELECT 1 FROM online_order_events e JOIN online_orders o ON o.id = e.order_id
                     WHERE e.shop_id = s.id AND e.created_at >= $1 AND e.created_at < $2 AND e.kind = 'status'
                       AND e.to_status = ANY($3::text[]) AND o.is_test = false)
         OR EXISTS (SELECT 1 FROM online_order_refunds r JOIN online_orders o ON o.id = r.order_id
                     WHERE r.shop_id = s.id AND o.is_test = false)
         OR EXISTS (SELECT 1 FROM online_orders o
                     WHERE o.shop_id = s.id AND o.is_test = false
                       AND o.status IN ('return_requested','refunded_partial','refunded_full'))
         OR EXISTS (SELECT 1 FROM delivery_assignments d JOIN online_orders o ON o.id = d.order_id
                     WHERE d.shop_id = s.id AND d.mode = 'shop_courier' AND d.status = 'delivered' AND o.is_test = false
                       AND (d.settlement_id = '' OR (d.delivered_at >= $1 AND d.delivered_at < $2)))
         OR EXISTS (SELECT 1 FROM courier_settlements c
                     WHERE c.shop_id = s.id AND c.created_at >= $1 AND c.created_at < $2
                       AND (NOT EXISTS (SELECT 1 FROM delivery_assignments d WHERE d.shop_id = c.shop_id AND d.settlement_id = c.id)
                            OR EXISTS (SELECT 1 FROM delivery_assignments d JOIN online_orders o ON o.id = d.order_id
                                        WHERE d.shop_id = c.shop_id AND d.settlement_id = c.id AND o.is_test = false)))
      ORDER BY s.id`,
    [w.from, w.to, [...SOLD, ...CANCELLED]]
  );
}

/** تسویه‌های بازه به‌ازای دکان، بی سهمِ آزمایشی. */
function settledOf(rows) {
  const by = new Map();
  for (const r of rows) {
    const nAll = Number(r.n_all) || 0;
    if (nAll > 0 && Number(r.n_test) === nAll) continue;          // سراسر آزمایشی
    const testDue = (Number(r.test_cash) || 0) - (r.fare_paid_from_cash ? Number(r.test_fare) || 0 : 0);
    const due = Number(r.handover_due_minor) - testDue;
    const received = Math.max(0, Number(r.received_minor) - testDue);
    const cur = by.get(r.shop_id) || { settlements: 0, handoverDueMinor: 0, receivedMinor: 0, shortageMinor: 0, excessMinor: 0 };
    cur.settlements += 1;
    cur.handoverDueMinor += due;
    cur.receivedMinor += received;
    cur.shortageMinor += Math.max(0, due - received);
    cur.excessMinor += Math.max(0, received - due);
    by.set(r.shop_id, cur);
  }
  return by;
}

async function reconciliation(query = {}) {
  const w = reconWindow(query);
  const shops = await reconShops(w);
  const ids = shops.map((r) => r.shop_id);
  const names = new Map(shops.map((r) => [r.shop_id, r.name || '']));
  const args = [w.from, w.to, ids];
  const by = async (sql, params = args) => new Map((ids.length ? await many(sql, params) : []).map((r) => [r.shop_id, r]));
  const feeCols = await feeColumns();
  const [reports, cancels, courier, held, settleRows, fees] = await Promise.all([
    require('./order-refunds').reportMany(ids, { from: w.from, to: w.to }, { liveOnly: true }),
    by(`SELECT e.shop_id, COUNT(*)::int AS n FROM online_order_events e JOIN online_orders o ON o.id = e.order_id
         WHERE e.shop_id = ANY($3::text[]) AND e.kind = 'status' AND e.to_status = ANY($4::text[])
           AND e.created_at >= $1 AND e.created_at < $2 AND o.is_test = false
         GROUP BY e.shop_id`, [...args, [...CANCELLED]]),
    by(`SELECT d.shop_id, COUNT(*)::int AS n, COALESCE(SUM(d.cash_collected_minor), 0)::bigint AS s
          FROM delivery_assignments d JOIN online_orders o ON o.id = d.order_id
         WHERE d.shop_id = ANY($3::text[]) AND d.mode = 'shop_courier' AND d.status = 'delivered'
           AND d.delivered_at >= $1 AND d.delivered_at < $2 AND o.is_test = false
         GROUP BY d.shop_id`),
    by(`SELECT d.shop_id, COALESCE(SUM(d.cash_collected_minor), 0)::bigint AS s
          FROM delivery_assignments d JOIN online_orders o ON o.id = d.order_id
         WHERE d.shop_id = ANY($1::text[]) AND d.mode = 'shop_courier' AND d.status = 'delivered'
           AND d.settlement_id = '' AND o.is_test = false
         GROUP BY d.shop_id`, [ids]),
    ids.length ? many(
      `SELECT c.shop_id, c.handover_due_minor, c.received_minor, c.fare_paid_from_cash, t.n_all, t.n_test, t.test_cash, t.test_fare
         FROM courier_settlements c
         LEFT JOIN LATERAL (
           SELECT COUNT(*)::int AS n_all, (COUNT(*) FILTER (WHERE o.is_test))::int AS n_test,
                  COALESCE(SUM(d.cash_collected_minor) FILTER (WHERE o.is_test), 0)::bigint AS test_cash,
                  COALESCE(SUM(d.fare_minor) FILTER (WHERE o.is_test), 0)::bigint AS test_fare
             FROM delivery_assignments d JOIN online_orders o ON o.id = d.order_id
            WHERE d.shop_id = c.shop_id AND d.settlement_id = c.id) t ON true
        WHERE c.shop_id = ANY($3::text[]) AND c.created_at >= $1 AND c.created_at < $2`,
      args
    ) : [],
    //  کارمزد روی همان سفارش‌های «فروش» (همان پیوندِ collected در order-refunds)
    feeCols.length ? by(`SELECT e.shop_id, COALESCE(SUM(${feeCols.map((c) => `COALESCE(o.${c}, 0)`).join(' + ')}), 0)::bigint AS s
          FROM online_order_events e JOIN online_orders o ON o.id = e.order_id
         WHERE e.shop_id = ANY($3::text[]) AND e.kind = 'status' AND e.to_status IN ('delivered','picked_up')
           AND e.created_at >= $1 AND e.created_at < $2 AND o.is_test = false
         GROUP BY e.shop_id`) : new Map(),
  ]);
  const settled = settledOf(settleRows);
  const N = (v) => Number(v) || 0;
  const rows = [];
  for (const id of ids) {
    const rep = reports.get(id);
    const c = courier.get(id);
    const st = settled.get(id) || { settlements: 0, handoverDueMinor: 0, receivedMinor: 0, shortageMinor: 0, excessMinor: 0 };
    const issues = rep.issues.filter((i) => !i.isTest)
      .map((i) => ({ orderNo: i.orderNo, code: i.code, totalMinor: i.totalMinor, refundedMinor: i.refundedMinor }));
    const row = {
      shopId: id, shopName: names.get(id),
      sales: rep.collected, refunds: rep.refunds, netMinor: rep.netMinor,
      cancellations: N(cancels.get(id)?.n),
      courier: { deliveries: N(c?.n), collectedMinor: N(c?.s), heldMinor: N(held.get(id)?.s), ...st },
      commission: N(fees.get(id)?.s),
      issues,
    };
    //  نامزدِ «اختلافِ ممکن» که در عمل نه کاری دارد نه اختلافی ⇒ ردیفِ صفر نمی‌آید
    const empty = !row.sales.count && !row.refunds.count && !row.cancellations && !row.courier.deliveries
      && !row.courier.heldMinor && !row.courier.settlements && !issues.length;
    if (!empty) rows.push(row);
  }
  rows.sort((a, b) => b.issues.length - a.issues.length || b.courier.heldMinor - a.courier.heldMinor
    || b.netMinor - a.netMinor || (a.shopId < b.shopId ? -1 : a.shopId > b.shopId ? 1 : 0));
  const sum = (f) => rows.reduce((a, r) => a + f(r), 0);
  return {
    window: { from: w.from, to: w.to },
    local: { timezone: w.timezone, fromDate: w.fromDate, toDate: w.toDate },
    currency: 'AFN', checkedAt: now(), testExcluded: true,
    shopsTotal: rows.length, truncated: rows.length > RECON_LIMITS.maxShops,
    commission: { columns: feeCols },
    totals: {
      salesCount: sum((r) => r.sales.count), salesMinor: sum((r) => r.sales.totalMinor),
      refundsCount: sum((r) => r.refunds.count), refundsMinor: sum((r) => r.refunds.totalMinor),
      netMinor: sum((r) => r.netMinor), cancellations: sum((r) => r.cancellations),
      courierCollectedMinor: sum((r) => r.courier.collectedMinor), courierHeldMinor: sum((r) => r.courier.heldMinor),
      settlements: sum((r) => r.courier.settlements), receivedMinor: sum((r) => r.courier.receivedMinor),
      shortageMinor: sum((r) => r.courier.shortageMinor), excessMinor: sum((r) => r.courier.excessMinor),
      issues: sum((r) => r.issues.length), commissionMinor: sum((r) => r.commission),
    },
    shops: rows.slice(0, RECON_LIMITS.maxShops),
  };
}

module.exports = {
  LIMITS, PROVIDER_STATES, VERSION_LIMITS, CHAIN_LIMITS, RECON_LIMITS, worst, snapshot, providers, versions,
  versionReport, chainReport, reconciliation, reconWindow, smsState, emailState, pushState,
};
