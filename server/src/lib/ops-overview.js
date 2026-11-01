'use strict';
/**
 * نماهای خواندنیِ پنلِ مرکزیِ مالک — ۴.۸ نسخه‌ها و انتشار، ۴.۹ سلامتِ سیستم، ۴.۱۳ یکپارچه‌سازی‌ها.
 *
 * ⛔ فقط‌خواندنی و فقط عدد/وضعیت/نام؛ هیچ راز، کلید، نشانیِ کامل، ایمیل یا شمارهٔ کسی. «اعتبارنامه»
 *    فقط «تنظیم شده / نشده» است.
 * ⛔ هر بخش جدا شکست می‌خورد (`safe`): یک پرس‌وجوی ناموفق آن بخش را `unknown` می‌کند، نه کلِ پاسخ را.
 * ⛔ «نسخهٔ برنامه»، «نسخهٔ API» و «نسخهٔ طرحِ پایگاه» سه چیزِ جدا‌ند و جدا گزارش می‌شوند:
 *      app     نسخهٔ برنامهٔ نصبی روی دستگاه (انتشارِ GitHub، `sync_*_devices.app_version`)
 *      api     نسخهٔ همین سرورِ حساب (`package.json`) + «توانایی»های قرارداد با پنل
 *      schema  آخرین مهاجرتِ اجراشدهٔ پایگاه (`schema_migrations`) و نسخهٔ طرحِ دادهٔ همگام‌سازیِ هر
 *              برنامه (`app_schema`)
 */
const config = require('../config');
const { one, many, now } = require('../db');

const HOUR = 3600_000;
const DAY = 24 * HOUR;

async function safe(fn, fallback = { status: 'unknown' }) {
  try { return await fn(); } catch (err) { return { ...fallback, error: String(err?.code || err?.name || 'error').slice(0, 60) }; }
}

function withTimeout(promise, ms, code = 'timeout') {
  let timer;
  return Promise.race([
    promise.finally(() => clearTimeout(timer)),
    new Promise((_, rej) => { timer = setTimeout(() => rej(Object.assign(new Error(code), { code })), ms); }),
  ]);
}

const apiVersion = () => require('../../package.json').version;

/* ───────────────────────────── ۴.۸ نسخه‌ها و انتشار ───────────────────────────── */

/** برچسب‌های انتشار ⇒ برنامه و سکو (همان `routes/releases.js`). */
const PLATFORMS = Object.freeze({
  'tohid-native': { app: 'shop', platform: 'android' },
  'tohid-desktop': { app: 'shop', platform: 'desktop' },
});

async function publishedOf(tag) {
  const rel = require('../routes/releases');
  if (typeof rel._release !== 'function') return { tag, state: 'unknown', error: 'release_reader_missing' };
  try {
    const data = await withTimeout(rel._release(tag), 8000);
    const version = await withTimeout(rel._versionOf(data), 8000).catch(() => '');
    return {
      tag, state: 'published', version: version || '', publishedAt: data.publishedAt ? Date.parse(data.publishedAt) || null : null,
      //  یادداشتِ انتشار همان متنِ عمومیِ GitHub است؛ بریده تا صفحه سنگین نشود
      notes: String(data.notes || '').slice(0, 1500), notesTruncated: String(data.notes || '').length > 1500,
      assets: (data.assets || []).length,
    };
  } catch (err) {
    const code = String(err?.code || err?.message || 'error');
    //  «github 404» ⇒ این برچسب هنوز منتشر نشده — نه خطا
    if (/github 404/.test(code)) return { tag, state: 'none' };
    return { tag, state: 'unknown', error: code.slice(0, 60) };
  }
}

async function stageInfo() {
  let lib;
  try { lib = require('./release-stage'); } catch { lib = null; }
  const base = {
    //  ⛔ «برگشت» یعنی پایین آوردنِ نسخهٔ نصب‌شده — نیست: درصدِ ۰ فقط می‌ایستاند و پایانِ مرحله فقط برچسبِ
    //     روان را برای بقیه برمی‌گرداند؛ دستگاهی که نسخهٔ تازه را گرفته پایین نمی‌آید (OPS-01).
    rollback: { supported: false, reason: 'downgrade_not_supported' },
    scheduled: { supported: false, reason: 'time_based_rollout_not_supported' },
  };
  if (!lib) return { supported: false, reason: 'ops01_missing', needs: 'OPS-01 (#559): lib/release-stage.js · /api/admin/release/stage', ...base };
  const { items } = await require('./live-config').list('release');
  const stages = {};
  for (const tag of Object.keys(PLATFORMS)) {
    const it = items.find((i) => i.key === `${lib.KEY_PREFIX}${tag}`);
    const s = it ? lib.parseStage(it.value) : null;
    stages[tag] = s ? { ...s, updatedAt: it.updatedAt || null, updatedBy: it.updatedBy || '' } : null;
  }
  return { supported: true, controls: ['start', 'percent', 'pause', 'end'], stages, ...base };
}

async function errorsByVersion(app, t) {
  const rows = await many(
    `SELECT COALESCE(NULLIF(regexp_replace(btrim(app_version), '^[vV]', ''), ''), NULLIF(version, ''), '') AS v,
            COUNT(*)::int AS n, COUNT(DISTINCT device_id) FILTER (WHERE device_id <> '')::int AS devices,
            COUNT(DISTINCT account_id) FILTER (WHERE account_kind='shop' AND account_id <> '')::int AS shops,
            COUNT(DISTINCT fingerprint) FILTER (WHERE fingerprint <> '')::int AS incidents, MAX(at) AS last
       FROM client_errors WHERE app=$1 AND at > $2 GROUP BY 1 ORDER BY n DESC LIMIT 30`,
    [app, t - 7 * DAY]
  );
  return rows.map((r) => ({
    version: r.v || '', errors: Number(r.n), devices: Number(r.devices), shops: Number(r.shops),
    incidents: Number(r.incidents), lastAt: Number(r.last) || null,
  }));
}

/** خطای «پس از انتشار»: ردیف‌های همان نسخهٔ پایدار از لحظهٔ انتشار به بعد. */
async function errorsSince(app, version, since) {
  if (!version || !since) return null;
  const r = await one(
    `SELECT COUNT(*)::int AS n, COUNT(DISTINCT device_id) FILTER (WHERE device_id <> '')::int AS devices
       FROM client_errors WHERE app=$1 AND regexp_replace(btrim(app_version), '^[vV]', '') = $2 AND at >= $3`,
    [app, version, since]
  );
  return { version, since, errors: Number(r.n), devices: Number(r.devices) };
}

async function schemaInfo() {
  const mig = await one('SELECT MAX(version) AS v, COUNT(*)::int AS n, MAX(applied_at) AS at FROM schema_migrations');
  const apps = await many('SELECT app, schema_version, updated_at FROM app_schema ORDER BY app').catch(() => []);
  return {
    migration: mig?.v ? String(mig.v) : '', migrations: Number(mig?.n) || 0, appliedAt: mig?.at == null ? null : Number(mig.at),
    appSchema: apps.map((a) => ({ app: a.app, schemaVersion: Number(a.schema_version), updatedAt: Number(a.updated_at) || null })),
  };
}

async function releases() {
  const t = now();
  const cfg = await safe(() => require('./plans').allConfig(), {});
  const compat = require('./app-compat');
  const tags = Object.keys(PLATFORMS);
  const channels = await Promise.all(tags.map(async (tag) => {
    const [stable, beta] = await Promise.all([publishedOf(tag), publishedOf(`${tag}-beta`)]);
    return { tag, ...PLATFORMS[tag], stable, beta };
  }));
  const versions = await safe(() => require('./system-health').versions());
  const apps = {};
  for (const app of ['shop', 'pump']) {
    const devicesOf = (versions.apps || []).find((a) => a.app === app) || null;
    const stable = channels.find((c) => c.app === app && c.stable.state === 'published');
    apps[app] = {
      app,
      minVersion: compat.minVersionOf(cfg, app) || '',
      latestDeclared: compat.valid(cfg[`latest_version_${app}`]) || '',
      devices: devicesOf ? {
        total: devicesOf.devices, belowMin: devicesOf.belowMin, outdated: devicesOf.outdated, unknown: devicesOf.unknown,
        status: devicesOf.status, versions: (devicesOf.versions || []).slice(0, 15),
      } : null,
      errorsByVersion: await safe(() => errorsByVersion(app, t), []),
      afterRelease: stable ? await safe(() => errorsSince(app, stable.stable.version, stable.stable.publishedAt), null) : null,
    };
  }
  return {
    checkedAt: t,
    api: { version: apiVersion(), capabilitiesNote: 'GET /api/admin/me ⇒ capabilities' },
    schema: await safe(schemaInfo),
    channels,
    apps,
    stage: await safe(stageInfo, { supported: false, reason: 'error' }),
    activeDays: versions.activeDays ?? null,
  };
}

/* ───────────────────────────── ۴.۹ سلامتِ سیستم ───────────────────────────── */

/** تعریفِ صریحِ هر حالت — همان که پنل کنارِ هر چراغ می‌گوید. */
const DEFINITIONS = Object.freeze({
  ok: 'سالم: آخرین سنجش پاسخِ درست داد و از آستانه‌ها رد نشد',
  warn: 'هشدار: کار می‌کند ولی از آستانه گذشته (کند، صفِ مانده، کارِ دیرکرده، پشتیبانِ کهنه، دیسکِ کم)',
  down: 'از کار افتاده: سنجش شکست خورد یا مقدار از حدِ بحرانی گذشت',
  unknown: 'نامعلوم: سنجش ممکن نشد یا این بخش در این پروسه راه نیفتاده — نه سبز، نه سرخ',
});

async function syncHealth(t) {
  const v1 = await many(
    `SELECT app, MAX(GREATEST(COALESCE(last_push_at,0), COALESCE(last_pull_at,0))) AS last_ok, MAX(last_seen_at) AS last_seen,
            COUNT(*) FILTER (WHERE last_seen_at > $1)::int AS active
       FROM sync_devices GROUP BY app`, [t - DAY]
  );
  const v3 = await many(
    `SELECT app, MAX(last_ok_at) AS last_ok, COUNT(*) FILTER (WHERE last_error_at > $1)::int AS errors24h,
            COUNT(*) FILTER (WHERE COALESCE(last_ok_at,0) > $1)::int AS active
       FROM sync_v3_devices GROUP BY app`, [t - DAY]
  );
  const apps = {};
  for (const r of v1) apps[r.app] = { app: r.app, lastOkAt: Number(r.last_ok) || null, activeDevices24h: Number(r.active), errors24h: null };
  for (const r of v3) {
    const cur = apps[r.app] || { app: r.app, lastOkAt: null, activeDevices24h: 0, errors24h: 0 };
    cur.lastOkAt = Math.max(cur.lastOkAt || 0, Number(r.last_ok) || 0) || null;
    cur.activeDevices24h += Number(r.active);
    cur.errors24h = (cur.errors24h || 0) + Number(r.errors24h);
    apps[r.app] = cur;
  }
  return { checkedAt: t, apps: Object.values(apps) };
}

let loopDelay = null;
function eventLoop() {
  try {
    if (!loopDelay) {
      loopDelay = require('perf_hooks').monitorEventLoopDelay({ resolution: 20 });
      loopDelay.enable();
      return { status: 'unknown', reason: 'warming_up' };
    }
    const p99 = Math.round(loopDelay.percentile(99) / 1e6);
    const mean = Math.round(loopDelay.mean / 1e6);
    loopDelay.reset();
    return { status: p99 > 1000 ? 'down' : p99 > 200 ? 'warn' : 'ok', p99Ms: p99, meanMs: mean, limits: { warnMs: 200, downMs: 1000 } };
  } catch { return { status: 'unknown' }; }
}

async function health() {
  const t = now();
  const hs = require('./system-health');
  const errs = require('./ops-errors');
  const [snap, sync, serverErrors, schema] = await Promise.all([
    safe(() => hs.snapshot()), safe(() => syncHealth(t)), safe(() => errs.serverErrorCounts(t)), safe(schemaInfo),
  ]);
  const jobs = require('./job-beat').report(t);
  const mem = process.memoryUsage();
  const loop = eventLoop();
  const jobsStatus = jobs.some((j) => j.status === 'warn') ? 'warn' : jobs.every((j) => j.status === 'unknown') ? 'unknown' : 'ok';
  const serverErrStatus = serverErrors.lastHour == null ? 'unknown' : serverErrors.lastHour >= 20 ? 'warn' : 'ok';
  const parts = { snapshot: snap.status || 'unknown', jobs: jobsStatus, serverErrors: serverErrStatus, eventLoop: loop.status };
  return {
    checkedAt: t,
    status: hs.worst(Object.values(parts).map((s) => (['ok', 'warn', 'down', 'unknown'].includes(s) ? s : 'unknown'))),
    parts,
    server: { version: apiVersion(), node: process.version, uptimeSeconds: Math.round(process.uptime()), rssBytes: mem.rss, heapUsedBytes: mem.heapUsed, driver: snap.database?.driver || null },
    snapshot: snap,
    jobs,
    sync,
    serverErrors: { ...serverErrors, status: serverErrStatus, limits: { warnPerHour: 20 } },
    eventLoop: loop,
    schema,
    definitions: DEFINITIONS,
  };
}

/* ───────────────────────────── ۴.۱۳ یکپارچه‌سازی‌ها ───────────────────────────── */

async function emailActivity(t) {
  const r = await one(
    `SELECT MAX(sent_at) AS last_ok, MAX(failed_at) FILTER (WHERE reason <> 'expired') AS last_fail,
            COUNT(*) FILTER (WHERE status='sent' AND sent_at > $1)::int AS ok24h,
            COUNT(*) FILTER (WHERE status='failed' AND reason <> 'expired' AND created_at > $1)::int AS fail24h
       FROM otp_outbox`, [t - DAY]
  );
  const last = await one(
    `SELECT reason FROM otp_outbox WHERE status='failed' AND reason <> 'expired' ORDER BY failed_at DESC NULLS LAST LIMIT 1`
  ).catch(() => null);
  return { lastOkAt: Number(r.last_ok) || null, lastFailAt: Number(r.last_fail) || null, ok24h: r.ok24h, fail24h: r.fail24h, lastErrorCode: last?.reason || '' };
}

async function smsActivity(t) {
  const r = await one(
    `SELECT MAX(at) FILTER (WHERE ok) AS last_ok, MAX(at) FILTER (WHERE NOT ok) AS last_fail,
            COUNT(*) FILTER (WHERE ok AND at > $1)::int AS ok24h, COUNT(*) FILTER (WHERE NOT ok AND at > $1)::int AS fail24h
       FROM sms_usage`, [t - DAY]
  );
  return { lastOkAt: Number(r.last_ok) || null, lastFailAt: Number(r.last_fail) || null, ok24h: r.ok24h, fail24h: r.fail24h, lastErrorCode: '' };
}

async function telegramActivity(t) {
  const r = await one(
    `SELECT MAX(sent_at) AS last_ok, MAX(failed_at) AS last_fail,
            COUNT(*) FILTER (WHERE sent_at > $1)::int AS ok24h, COUNT(*) FILTER (WHERE failed_at > $1)::int AS fail24h
       FROM telegram_outbox`, [t - DAY]
  );
  return { lastOkAt: Number(r.last_ok) || null, lastFailAt: Number(r.last_fail) || null, ok24h: r.ok24h, fail24h: r.fail24h, lastErrorCode: '' };
}

/** فقط «هست / نیست» — هرگز مقدار، حتی چهار نویسهٔ آخر. */
function credentials() {
  const set = (v) => Boolean(v && String(v).length);
  const list = [
    { id: 'API_SECRET', service: 'server', set: set(config.secrets.api), rotate: 'env' },
    { id: 'OTP_SECRET', service: 'server', set: set(config.secrets.otp), rotate: 'env' },
    { id: 'RELEASES_TOKEN', service: 'releases', set: set(config.releases?.token), rotate: 'env' },
    { id: 'BACKUP_PASSPHRASE', service: 'backups', set: set(config.backup?.passphrase), rotate: 'env' },
    { id: 'WHATSAPP_TOKEN', service: 'sms', set: set(config.whatsapp?.token), rotate: 'env' },
    { id: 'HESABPAY_API_KEY', service: 'payment.hesabpay', set: set(process.env.HESABPAY_API_KEY), rotate: 'env' },
    { id: 'HESABPAY_WEBHOOK_SECRET', service: 'payment.hesabpay', set: set(process.env.HESABPAY_WEBHOOK_SECRET), rotate: 'env' },
    { id: 'HESABPAY_MERCHANT_ID', service: 'payment.hesabpay', set: set(process.env.HESABPAY_MERCHANT_ID), rotate: 'env' },
  ];
  return list;
}

async function serviceKeys() {
  if (!(await one("SELECT to_regclass('admin_service_keys') IS NOT NULL AS ok")).ok) return { available: false };
  const r = await one(
    `SELECT COUNT(*) FILTER (WHERE status='active')::int AS active, COUNT(*) FILTER (WHERE status='revoked')::int AS revoked,
            MAX(created_at) FILTER (WHERE status='active') AS newest, MAX(last_used_at) AS last_used
       FROM admin_service_keys`
  );
  return { available: true, active: r.active, revoked: r.revoked, newestCreatedAt: Number(r.newest) || null, lastUsedAt: Number(r.last_used) || null };
}

async function hesabpayWebhook(t) {
  let routePresent = false;
  try { require.resolve('../routes/payments-webhook'); routePresent = true; } catch { routePresent = false; }
  const tables = (await one("SELECT to_regclass('payment_events') IS NOT NULL AS ok")).ok;
  const base = {
    id: 'hesabpay', path: '/api/v1/payments/webhook/hesabpay', routePresent, needs: 'PAY-21 (#518)',
    signatureSecretSet: Boolean(process.env.HESABPAY_WEBHOOK_SECRET),
  };
  if (!routePresent || !tables) return { ...base, state: 'blocked', reason: routePresent ? 'tables_missing' : 'route_missing' };
  const r = await one(
    `SELECT MAX(received_at) AS last, COUNT(*) FILTER (WHERE received_at > $1)::int AS n24h,
            COUNT(*) FILTER (WHERE received_at > $1 AND outcome IN ('mismatch','unconfirmed','unknown_intent'))::int AS bad24h
       FROM payment_events WHERE provider='hesabpay'`, [t - DAY]
  );
  return {
    ...base, state: !base.signatureSecretSet ? 'blocked' : 'live', reason: base.signatureSecretSet ? '' : 'secret_missing',
    lastReceivedAt: Number(r.last) || null, received24h: r.n24h, problems24h: r.bad24h,
  };
}

function policies() {
  const rl = config.rateLimit || {};
  let outbox = null;
  try {
    const o = require('./login-outbox');
    outbox = { maxAttempts: o.MAX_ATTEMPTS, backoffMs: o.BACKOFF_MS, timeoutMs: o.TIMEOUT_MS, breakerFails: o.BREAKER_FAILS, breakerResetMs: o.BREAKER_RESET };
  } catch { outbox = null; }
  return {
    rateLimit: {
      windowMs: rl.windowMs ?? null, authMax: rl.authMax ?? null, otpMax: rl.otpMax ?? null, probeIpMax: rl.probeIpMax ?? null,
      lockoutTries: rl.lockoutTries ?? null, lockoutMs: rl.lockoutMs ?? null,
      clientErrorsPerWindow: Number(process.env.RATE_ERRORS_MAX || 60),
    },
    emailOutbox: outbox,
    serverErrorsPerMinute: require('./ops-errors').LIMITS.serverPerMinute,
    alerts: { evaluateEveryMs: require('./ops-alerts').LIMITS.evaluateEveryMs, maxNewPerRun: require('./ops-alerts').LIMITS.maxNewPerRun, maxPerRule: require('./ops-alerts').LIMITS.maxPerRule },
  };
}

async function integrations() {
  const t = now();
  const hs = require('./system-health');
  const prov = await safe(() => hs.providers(), { providers: [] });
  const activity = {
    email: await safe(() => emailActivity(t), null),
    sms: await safe(() => smsActivity(t), null),
    telegram: await safe(() => telegramActivity(t), null),
  };
  const providers = (prov.providers || []).map((p) => ({ ...p, activity: activity[p.id] || null }));
  providers.push({ id: 'telegram', mode: 'bot', state: 'unknown', missing: [], activity: activity.telegram, note: 'telegram_outbox' });
  return {
    checkedAt: t,
    production: prov.production ?? null,
    providers,
    credentials: credentials(),
    serviceKeys: await safe(serviceKeys, { available: false }),
    webhooks: [await safe(() => hesabpayWebhook(t), { id: 'hesabpay', state: 'unknown' })],
    contracts: {
      api: apiVersion(),
      adminBasePaths: ['/api/admin', '/api/v1/admin'],
      sync: ['v1', 'v3'],
      schema: await safe(schemaInfo),
    },
    policies: policies(),
  };
}

/*
 *  «توانایی»های این ماژول برای `GET /api/admin/me` (`routes/admin.js`): `ops.v1` = درهای `routes/admin-ops.js`؛
 *  `release-stage.v1` فقط وقتی انتشارِ مرحله‌ایِ OPS-01 (`lib/release-stage.js` و `PUT|DELETE /admin/release/stage`)
 *  در همین نسخه هست — وگرنه پنل «پشتیبانی نمی‌شود» می‌گوید، نه دکمه‌ای که جایی نمی‌رود.
 */
const HAS_RELEASE_STAGE = (() => { try { require.resolve('./release-stage'); return true; } catch { return false; } })();
const CAPABILITIES = Object.freeze(['ops.v1', ...(HAS_RELEASE_STAGE ? ['release-stage.v1'] : [])]);

module.exports = { CAPABILITIES, PLATFORMS, DEFINITIONS, releases, health, integrations, stageInfo, credentials, policies, _withTimeout: withTimeout };
