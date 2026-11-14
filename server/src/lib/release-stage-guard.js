'use strict';
/**
 * OPS-01 — ایستِ خودکارِ انتشارِ مرحله‌ای وقتی خطا بالا رفت (تصمیمِ صاحب‌کار ۱۴۰۵/۰۸/۲۳، OA §۹د؛ spec §۱۵.۳ F0-27:
 * «توقفِ خودکار در افزایشِ crash/error و rollback»).
 *
 * ── چه می‌سنجد ──────────────────────────────────────────────────────
 * برای هر مرحلهٔ زنده (`stage.<برچسب>`، نایستاده، با درصد یا فهرستِ دکان)، از لحظهٔ شروعِ همان نسخه (`since`):
 *   • نسخهٔ مرحله: چند دستگاه این نسخه را دارند (`sync_devices` ∪ `sync_v3_devices`، دیده‌شده از `since`) و چند
 *     دستگاهِ این نسخه گزارشِ خطا/ازکارافتادن فرستادند (`client_errors`، همان `POST /api/errors` ِ برنامه).
 *   • پایه: همان دو عدد برای نسخهٔ برچسبِ روان (پایدارِ امروز) در همان بازه — اگر خواندنی است.
 * ⇒ ایست اگر هر دو:
 *   ۱) دست‌کم `minErrorDevices` دستگاهِ نسخهٔ تازه خطا فرستادند (یک گوشیِ خراب پخش را نمی‌ایستاند)، و
 *   ۲) سهمِ دستگاه‌های خطادار ≥ max(`floorPercent`٪، `factor` × سهمِ پایه). دستگاهی که خطا فرستاده حتماً آن نسخه را
 *      دارد ⇒ مخرج هرگز کمتر از شمارِ خطادارها نیست (جمعیتِ کوچکِ همگام‌نشده هم سنجیده می‌شود).
 *
 * ── «ایست و برگشت» یعنی چه ─────────────────────────────────────────
 * `halted` روی همان کلید می‌نشیند (`lib/release-stage.js`): از همان دم **هیچ** دستگاهی (نه فهرست، نه درصد) نسخهٔ
 * تازه نمی‌گیرد و همه برچسبِ روان (= پایدارِ پیشین) را می‌گیرند. ⛔ پایین آوردنِ نسخهٔ نصب‌شده نیست: اندروید نسخهٔ
 * پایین‌تر را روی نصب نمی‌نشاند (`docs/RELEASE-STABLE-fa.md` §۳)؛ رفع همیشه رو به جلو است (نسخهٔ تازه‌تر).
 * فقط تصمیمِ تازهٔ مدیر (`PUT /api/admin/release/stage`) ایست را برمی‌دارد؛ نگهبان هرگز خودش دوباره راه نمی‌اندازد.
 *
 * ── پرچم (پیش‌فرض خاموش) ────────────────────────────────────────────
 *   live_config، scope `release`، کلیدِ `auto_halt`: `true` یا `{ "enabled": true, "minErrorDevices": 2,
 *   "factor": 2, "floorPercent": 20 }`. نبود/ناجور ⇒ خاموش (عددِ بیرون از بازه ⇒ پیش‌فرضِ همان عدد).
 *   خاموش ⇒ کارگر فقط پرچم را می‌پرسد؛ «همین حالا بسنج» ِ پنل فقط گزارش می‌دهد و نمی‌ایستاند.
 * ⛔ نوشتنِ ایست compare-and-set است (`liveConfig.setIf`): اگر مدیر میانِ سنجش و نوشتن مرحله را عوض کرد، نگهبان
 *    روی تصمیمِ او نمی‌نشیند. هر ایست یک سابقه (`admin.release_stage_halt`، بازیگر system) و هشدارِ پنل
 *    (`release_stage_halted` ِ `lib/ops-alerts.js`).
 */
const { one, now } = require('../db');
const liveConfig = require('./live-config');
const audit = require('./audit');
const stage = require('./release-stage');

const SCOPE = 'release';
const FLAG = Object.freeze({ scope: SCOPE, key: 'auto_halt' });
const DEFAULTS = Object.freeze({ minErrorDevices: 2, factor: 2, floorPercent: 20 });
const BOUNDS = Object.freeze({ minErrorDevices: [1, 1000], factor: [1, 100], floorPercent: [1, 100] });
const DAY = 24 * 3600_000;
const LIMITS = Object.freeze({
  tickMs: 5 * 60_000,
  //  مرحلهٔ کهنهٔ بی `since` (پیش از این تغییر): فقط هفت روزِ گذشته
  lookbackMs: 7 * DAY,
  //  خواندنِ نسخهٔ برچسبِ روان از GitHub (کش‌دار) — کند/قطع ⇒ «پایه نامعلوم»، نه گیر کردنِ کارگر
  stableReadMs: 8000,
});

const num = (raw, [min, max], def) => (Number.isFinite(raw) && raw >= min && raw <= max ? raw : def);

/** مقدارِ ذخیره‌شده ⇒ تنظیم؛ فقط `true` یا `{enabled:true}` روشن است */
function parseSettings(value) {
  let v = value;
  if (typeof v === 'string') { try { v = JSON.parse(v); } catch { v = null; } }
  const obj = v && typeof v === 'object' && !Array.isArray(v) ? v : {};
  return {
    enabled: v === true || obj.enabled === true,
    minErrorDevices: Math.round(num(obj.minErrorDevices, BOUNDS.minErrorDevices, DEFAULTS.minErrorDevices)),
    factor: num(obj.factor, BOUNDS.factor, DEFAULTS.factor),
    floorPercent: num(obj.floorPercent, BOUNDS.floorPercent, DEFAULTS.floorPercent),
  };
}

async function settings() {
  const r = await one('SELECT value, updated_at, updated_by FROM live_config WHERE scope = $1 AND key = $2', [FLAG.scope, FLAG.key]);
  return { ...parseSettings(r ? r.value : null), updatedAt: r ? Number(r.updated_at) : 0, updatedBy: r ? r.updated_by : '' };
}

const VERSION_OF_ERROR = "regexp_replace(btrim(COALESCE(NULLIF(app_version, ''), version)), '^[vV]', '')";

/** یک نسخه از `since`: دستگاه‌های دارای آن (همگام‌سازی) و دستگاه‌ها/گزارش‌های خطادار */
async function versionStats(version, since) {
  const e = await one(
    `SELECT COUNT(*)::int AS n, COUNT(DISTINCT device_id) FILTER (WHERE device_id <> '')::int AS devices,
            COUNT(*) FILTER (WHERE device_id = '')::int AS anonymous
       FROM client_errors WHERE app = 'shop' AND ${VERSION_OF_ERROR} = $1 AND at >= $2`,
    [version, since]
  );
  const d = await one(
    `SELECT COUNT(*)::int AS n FROM (
       SELECT DISTINCT account_kind, account_id, device_id FROM (
         SELECT app, account_kind, account_id, device_id, app_version, last_seen_at FROM sync_devices
         UNION ALL
         SELECT app, account_kind, account_id, device_id, app_version, last_seen_at FROM sync_v3_devices) u
        WHERE app = 'shop' AND regexp_replace(btrim(app_version), '^[vV]', '') = $1 AND last_seen_at >= $2) x`,
    [version, since]
  );
  //  گزارشِ بی شناسهٔ دستگاه هم دست‌کم یک دستگاه است (برنامه‌ای که پیش از شناسه شکست)
  const errorDevices = Number(e.devices) + (Number(e.anonymous) > 0 ? 1 : 0);
  return { version, devices: Number(d.n), errorDevices, errors: Number(e.n) };
}

const share = (s) => {
  const exposed = Math.max(s.devices, s.errorDevices);
  return exposed > 0 ? s.errorDevices / exposed : 0;
};
const pct = (x) => Math.round(x * 1000) / 10;

/**
 * تصمیمِ خالص (آزموده در `release-stage-guard.test.js`): `staged` و `baseline` (یا null) ⇒ ایست یا نه.
 */
function decide(m, cfg) {
  const ratio = share(m.staged);
  const base = m.baseline && Math.max(m.baseline.devices, m.baseline.errorDevices) > 0 ? share(m.baseline) : null;
  const limit = Math.min(1, Math.max(cfg.floorPercent / 100, base == null ? 0 : cfg.factor * base));
  const halt = m.staged.errorDevices >= cfg.minErrorDevices && ratio >= limit;
  const exposed = Math.max(m.staged.devices, m.staged.errorDevices);
  return {
    halt,
    ratioPercent: pct(ratio), limitPercent: pct(limit), baselinePercent: base == null ? null : pct(base),
    reason: halt
      ? `${m.staged.errorDevices} دستگاه از ${exposed} دستگاهِ نسخهٔ ${m.staged.version} خطا فرستادند (${pct(ratio)}٪؛ ` +
        `آستانه ${pct(limit)}٪${base == null ? '' : `، پایدار ${pct(base)}٪`})`
      : '',
  };
}

/** نسخهٔ برچسبِ روان (پایدارِ امروز)؛ نامعلوم ⇒ '' */
async function stableVersionOf(tag) {
  try {
    const rel = require('../routes/releases');
    const read = (async () => rel._versionOf(await rel._release(tag)))();
    let timer;
    const late = new Promise((resolve) => { timer = setTimeout(() => resolve(''), LIMITS.stableReadMs); });
    const v = await Promise.race([read.catch(() => ''), late]);
    clearTimeout(timer);
    return String(v || '');
  } catch {
    return '';
  }
}

/** مرحله‌های جاری با متنِ خامِ هر کلید (برای compare-and-set) */
async function currentStages() {
  const out = [];
  for (const tag of require('../routes/releases').STABLE) {
    const r = await one('SELECT value FROM live_config WHERE scope = $1 AND key = $2', [SCOPE, `${stage.KEY_PREFIX}${tag}`]);
    if (!r) continue;
    const s = stage.parseStage(r.value);
    if (s) out.push({ tag, stage: s, raw: r.value });
  }
  return out;
}

/** سنجشِ یک مرحله */
async function measure(tag, s, cfg, t = now()) {
  const since = s.since || t - LIMITS.lookbackMs;
  const staged = await versionStats(s.version, since);
  const stableVersion = await stableVersionOf(tag);
  const baseline = stableVersion && stableVersion !== s.version ? await versionStats(stableVersion, since) : null;
  return { tag, since, staged, baseline, decision: decide({ staged, baseline }, cfg) };
}

/** ایستاندنِ یک مرحله (compare-and-set). ⇐ true اگر همین فراخوان ایستاند. */
async function halt(tag, raw, s, reason, metrics) {
  const next = { ...s, halted: { at: now(), reason: reason.slice(0, 300), by: 'auto' } };
  const out = await liveConfig.setIf(SCOPE, `${stage.KEY_PREFIX}${tag}`, raw, next, 'auto-halt', { reason: 'ایستِ خودکار: خطای بالا' });
  if (!out.changed) return false;
  await audit.log({
    actorType: 'system', action: 'admin.release_stage_halt', targetType: 'live_config', targetId: `${SCOPE}:${stage.KEY_PREFIX}${tag}`,
    detail: {
      tag, version: s.version, percent: s.percent, shops: (s.shops || []).length, reason: reason.slice(0, 300),
      errorDevices: metrics.staged.errorDevices, devices: metrics.staged.devices, errors: metrics.staged.errors,
      baselineVersion: metrics.baseline ? metrics.baseline.version : '',
    },
  });
  try { require('../routes/releases')._forgetBeta(); } catch { /* فقط کش */ }
  console.warn(`[release-guard] ${tag} ${s.version}: ایست — ${reason}`);
  return true;
}

/**
 * یک دور: همهٔ مرحله‌های زنده. `act` = اجازهٔ ایستاندن (فقط با پرچمِ روشن). ⇐ گزارشِ هر برچسب.
 */
async function run({ act = true } = {}) {
  const cfg = await settings();
  const t = now();
  const results = {};
  for (const { tag, stage: s, raw } of await currentStages()) {
    if (!stage.isActive(s)) { results[tag] = { tag, version: s.version, active: false, halted: s.halted || null }; continue; }
    const m = await measure(tag, s, cfg, t);
    let halted = false;
    if (m.decision.halt && act && cfg.enabled) halted = await halt(tag, raw, s, m.decision.reason, m);
    results[tag] = { ...m, version: s.version, active: true, halted };
  }
  return { checkedAt: t, enabled: cfg.enabled, settings: cfg, results };
}

/** تنظیم از پنل (فقط مدیرِ ارشد؛ سنجشِ ورودی در `routes/admin-release.js`) */
async function configure(input, by) {
  const cur = await settings();
  const next = {
    enabled: input.enabled === undefined ? cur.enabled : input.enabled === true,
    minErrorDevices: input.minErrorDevices ?? cur.minErrorDevices,
    factor: input.factor ?? cur.factor,
    floorPercent: input.floorPercent ?? cur.floorPercent,
  };
  await liveConfig.set(FLAG.scope, FLAG.key, next, by);
  return { before: cur, after: await settings() };
}

/* ───────────────────────────── زمان‌بندی ───────────────────────────── */

let timer = null;
let beat = null;
async function tick() {
  const cfg = await settings();
  if (!cfg.enabled) return;
  //  نبضِ کار فقط با پرچمِ روشن ⇒ پرچمِ خاموش کارتِ سلامت را عوض نمی‌کند
  if (!beat) beat = require('./job-beat').wrap('release-stage-guard', LIMITS.tickMs, () => run({ act: true }));
  await beat();
}
function start() {
  if (timer) return;
  timer = setInterval(() => { tick().catch((err) => console.error('[release-guard]', err.message)); }, LIMITS.tickMs);
  if (timer.unref) timer.unref();
}
function stop() { if (timer) clearInterval(timer); timer = null; }

module.exports = {
  FLAG, DEFAULTS, BOUNDS, LIMITS, parseSettings, settings, versionStats, decide, measure, run, configure, start, stop,
};
