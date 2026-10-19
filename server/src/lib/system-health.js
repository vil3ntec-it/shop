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
const { one, now, driverName } = require('../db');

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

/** خطاهای گزارش‌شدهٔ برنامه‌ها و تعارض‌های همگام‌سازی در ۲۴ ساعت — شمرده، نه فهرست. */
async function sync(t) {
  const e = await one('SELECT COUNT(*)::int AS n FROM client_errors WHERE at > $1', [t - DAY]);
  const c = await one('SELECT COUNT(*)::int AS n FROM sync_conflicts WHERE at > $1', [t - DAY]);
  return { status: 'ok', clientErrors24h: e.n, conflicts24h: c.n };
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

module.exports = { LIMITS, worst, snapshot };
