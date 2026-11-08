'use strict';
/**
 * نسخهٔ فقط-خواندنیِ پایگاه (read replica) برای خواندن‌های کهنگی‌پذیر — docs/SCALING-fa.md («نسخهٔ خواندنی»).
 *
 * پیش‌فرض خاموش: بی `DATABASE_REPLICA_URL` هیچ استخری ساخته نمی‌شود، `reads`/`writes` فقط `next()` اند و
 * `many`/`one` همان `db.many`/`db.one` ِ پایگاهِ اصلی — بیت‌به‌بیت رفتارِ امروز. روی PGlite هرگز.
 *
 * خواندن فقط وقتی به نسخه می‌رود که **همهٔ** این‌ها راست باشد (وگرنه همان پایگاهِ اصلی):
 *   ۱) مسیر داوطلب است (`reads` روی چند GET ِ عمومیِ ویترین و بازار و گزارشِ شعبه‌ها) **و** همان پرسش داوطلب
 *      است (`many`/`one` ِ همین ماژول، نه `db.many`) — دو در، تا کدِ مشترکی که جای دیگر صدا زده می‌شود
 *      ناخواسته از نسخه نخواند؛
 *   ۲) درونِ تراکنش نیست (`db.inTransaction()`)؛
 *   ۳) همان درخواست پیش از آن چیزی ننوشته (`db.readScope.wrote`: هر `tx` یا پرسشِ غیرِ SELECT ِ ساده)؛
 *   ۴) همان مشتری/کاربر در `REPLICA_STICKY_MS` ِ گذشته درخواستِ نوشتنی (هر روشِ جز GET/HEAD/OPTIONS) نفرستاده —
 *      کلید: `Authorization`، `X-Store-Session`، `X-Store-Ticket` و IP ِ واقعی (`clientIp`؛ قاعدهٔ دقیق در `keysOf`)، فقط
 *      به شکلِ HMAC ِ کوتاه؛ با `CLUSTER_ENABLED=1` همان کلیدها با گذرگاه به نمونه‌های دیگر هم می‌رسند (پیش از پاسخ، در
 *      ورودِ نوشتن)؛
 *   ۵) نسخه زنده است و تأخیرِ اندازه‌گرفته‌اش ≤ `REPLICA_MAX_LAG_MS` (سنجش هر `REPLICA_CHECK_MS`؛ سنجشِ کهنه‌تر از
 *      سه دور یعنی «نامعلوم» ⇒ اصلی). خطای پرسش روی نسخه ⇒ همان پرسش روی اصلی؛ خطای اتصال ⇒ نسخه تا سنجشِ
 *      موفقِ بعدی کنار.
 *
 * تأخیر: روی standby ِ واقعی «اگر گیرندهٔ WAL زنده است و هرچه رسیده بازپخش شده ⇒ ۰، وگرنه فاصلهٔ اکنون تا
 * آخرین تراکنشِ بازپخش‌شده» (پس standby ِ بریده از اصلی با گذشتِ زمان «عقب» می‌شود و کنار می‌رود). پایگاهی که
 * standby نیست (مثلاً نشانیِ همان اصلی) تأخیرِ ۰ دارد. ساعتِ دو ماشین باید هم‌زمان باشد (NTP).
 *
 * ⛔ هیچ راز، توکن یا IP ِ خامی در حافظه یا گذرگاه نیست — فقط HMAC ِ ۱۶ نویسه‌ای با کلیدِ برگرفته از `API_SECRET`.
 */
const crypto = require('node:crypto');
const config = require('../config');
const db = require('../db');

const SAFE = new Set(['GET', 'HEAD', 'OPTIONS']);
const MAX_STICKY = 100_000;
const TOPIC = 'replica.wrote';

const state = {
  up: false, standby: null, lagMs: null, checkedAt: 0, lastError: '', probes: 0,
  reads: 0, fallbacks: 0, primary: Object.create(null),
};
const sticky = new Map();   //  کلید ⇒ تا کی فقط اصلی
let pool = null;
let timer = null;
let probing = null;
let unsubscribe = null;
let pending = null;         //  کلیدهای در صفِ گذرگاه

function configured() { return Boolean(config.db.replicaUrl) && !db.isPglite(); }

function replicaPool() {
  if (!pool) pool = db.extraPool(config.db.replicaPoolMax, config.db.replicaUrl);
  return pool;
}

// ── سنجشِ نسخه ───────────────────────────────────────────────────────────────

const PROBE_SQL = `SELECT pg_is_in_recovery() AS standby,
  (CASE WHEN NOT pg_is_in_recovery() THEN 0
        WHEN EXISTS (SELECT 1 FROM pg_stat_wal_receiver) AND pg_last_wal_receive_lsn() = pg_last_wal_replay_lsn() THEN 0
        ELSE COALESCE(EXTRACT(EPOCH FROM (clock_timestamp() - pg_last_xact_replay_timestamp())) * 1000, 1e12)
   END)::float8 AS lag_ms`;

/** نتیجهٔ یک سنجش (یا خطای آن) ⇒ وضعیت. */
function applyProbe(row, t = Date.now()) {
  state.probes++;
  state.checkedAt = t;
  if (!row) { state.up = false; return state; }
  state.up = true;
  state.standby = Boolean(row.standby);
  state.lagMs = Math.max(0, Math.round(Number(row.lag_ms)));
  if (!Number.isFinite(state.lagMs)) state.lagMs = Number.MAX_SAFE_INTEGER;
  state.lastError = '';
  return state;
}

function errLabel(err) { return `${String(err?.name || 'Error').slice(0, 40)}${err?.code ? `:${String(err.code).slice(0, 20)}` : ''}`; }

function markDown(err) {
  state.up = false;
  state.lastError = errLabel(err);
}

/** یک سنجش با مهلت؛ هم‌زمان فقط یکی. */
function probe() {
  if (!configured()) return Promise.resolve(state);
  if (probing) return probing;
  const limit = Math.max(1000, config.db.replicaCheckMs * 2);
  let t = null;
  probing = Promise.race([
    replicaPool().query(PROBE_SQL).then((r) => applyProbe(r.rows[0])),
    new Promise((_, reject) => { t = setTimeout(() => reject(Object.assign(new Error('probe timeout'), { code: 'ETIMEDOUT' })), limit); }),
  ]).catch((err) => { applyProbe(null); markDown(err); return state; })
    .finally(() => { clearTimeout(t); probing = null; });
  return probing;
}

/** تازه، زنده و کم‌تأخیر؟ */
function usable(t = Date.now()) {
  if (!state.up || state.lagMs === null) return false;
  if (t - state.checkedAt > Math.max(3 * config.db.replicaCheckMs, 3000)) return false;
  return state.lagMs <= config.db.replicaMaxLagMs;
}

// ── «همین حالا نوشت» ───────────────────────────────────────────────────────

const hmacKey = () => crypto.createHash('sha256').update(`read-replica:${config.secrets.api || 'dev'}`).digest();
let keyCache = null;
function tag(kind, value) {
  if (!keyCache) keyCache = hmacKey();
  return crypto.createHmac('sha256', keyCache).update(`${kind}:${value}`).digest('base64url').slice(0, 16);
}

/**
 * کلیدهای «همین مشتری/کاربر». خواندن: IP ِ واقعی و هر نشانِ همراه (`Authorization`، `X-Store-Session`، `X-Store-Ticket` —
 * ویترین بلیت را با **هر** درخواست می‌فرستد، `kit.js`). نوشتن: همان نشان‌ها؛ IP فقط وقتی نوشتن نشان ندارد (ورود، ثبت‌نام) یا
 * با `Authorization` است (صاحب/کارمند که پس از انتشار ویترینِ خودش را بی ورود در مرورگر می‌بیند). نوشتنِ مشتری با بلیت/نشست
 * IP را قفل نمی‌کند: پشتِ NAT ِ مشترکِ اپراتور (CGNAT) صدها مشتریِ دیگر همان IP را دارند و خودِ او با همان بلیت شناخته می‌شود.
 */
function keysOf(req, { write = false } = {}) {
  const { clientIp } = require('../middleware/ratelimit');
  const ids = [];
  const auth = req.get('authorization');
  if (auth) ids.push(tag('a', auth));
  const session = req.get('x-store-session');
  if (session) ids.push(tag('s', session));
  const ticket = req.get('x-store-ticket');
  if (ticket) ids.push(tag('t', ticket));
  if (write && ids.length && !auth) return ids;
  return [tag('ip', clientIp(req)), ...ids];
}

function remember(keys, t = Date.now()) {
  const until = t + config.db.replicaStickyMs;
  for (const k of keys) {
    sticky.delete(k);
    sticky.set(k, until);
  }
  if (sticky.size > MAX_STICKY) {
    for (const [k, u] of sticky) { if (u <= t) sticky.delete(k); }
    for (const k of sticky.keys()) { if (sticky.size <= MAX_STICKY) break; sticky.delete(k); }
  }
}

function isSticky(keys, t = Date.now()) {
  for (const k of keys) {
    const u = sticky.get(k);
    if (u === undefined) continue;
    if (u > t) return true;
    sticky.delete(k);
  }
  return false;
}

/** کلیدها به نمونه‌های دیگر — یک خبر برای همهٔ نوشتن‌های همان دورِ رویداد. */
function broadcast(keys) {
  const cluster = require('./cluster');
  if (!cluster.enabled()) return;
  if (!pending) {
    pending = new Set();
    setImmediate(() => {
      const list = [...pending];
      pending = null;
      for (let i = 0; i < list.length; i += 300) cluster.publish(TOPIC, { k: list.slice(i, i + 300) });
    });
  }
  for (const k of keys) pending.add(k);
}

// ── میان‌افزار ─────────────────────────────────────────────────────────────

/** سراسری (پیش از همهٔ مسیرهای API): هر درخواستِ نوشتنی ⇒ خواندن‌های همان مشتری تا مدتی از اصلی. */
function writes(req, res, next) {
  if (!configured() || SAFE.has(req.method) || config.db.replicaStickyMs <= 0) return next();
  const keys = keysOf(req, { write: true });
  remember(keys);
  broadcast(keys);
  //  مهلت از پایانِ نوشتن (نه آغازش) شمرده شود
  res.once('finish', () => remember(keys));
  return next();
}

/** داوطلبِ مسیر (فقط GET/HEAD): بقیهٔ همین درخواست در دامنهٔ «خواندن از نسخه مجاز است». */
function reads(req, res, next) {
  if (!configured() || (req.method !== 'GET' && req.method !== 'HEAD')) return next();
  if (!timer) start();
  const ctx = { wrote: false, sticky: config.db.replicaStickyMs > 0 && isSticky(keysOf(req)) };
  return db.readScope.run(ctx, next);
}

/** چرا این خواندن به نسخه نمی‌رود ('' ⇒ می‌رود). */
function reason(t = Date.now()) {
  if (!configured()) return 'off';
  const s = db.readScope.getStore();
  if (!s) return 'unscoped';
  if (db.inTransaction()) return 'tx';
  if (s.wrote) return 'wrote';
  if (s.sticky) return 'sticky';
  if (!usable(t)) return state.up ? 'lag' : 'down';
  return '';
}

function count(why) { state.primary[why] = (state.primary[why] || 0) + 1; }

/** پرسشِ داوطلب: نسخه اگر مجاز است، وگرنه (یا با هر خطا) همان پایگاهِ اصلی. */
async function query(text, params = []) {
  const why = reason();
  if (why) {
    if (why !== 'off') count(why);
    return db.query(text, params);
  }
  const p = replicaPool();
  if (config.db.maxWaiting > 0 && p.waitingCount >= config.db.maxWaiting) {
    count('busy');
    return db.query(text, params);
  }
  try {
    const r = await p.query(text, params);
    state.reads++;
    return r;
  } catch (err) {
    state.fallbacks++;
    //  اتصال/خاموشی/تعارضِ بازپخش ⇒ نسخه تا سنجشِ موفقِ بعدی کنار؛ خطای خودِ پرسش روی اصلی هم همان خطا را می‌دهد
    if (!err || !err.code || /^(08|57|53|58|40001)/.test(String(err.code)) || /^E[A-Z]+$/.test(String(err.code))) markDown(err);
    return db.query(text, params);
  }
}
async function many(text, params = []) { return (await query(text, params)).rows; }
async function one(text, params = []) { return (await query(text, params)).rows[0] || null; }

// ── چرخه ───────────────────────────────────────────────────────────────────

/** سنجشِ دوره‌ای و گیرندهٔ گذرگاه؛ بی پیکربندی هیچ. */
function start() {
  if (!configured() || timer) return false;
  timer = setInterval(() => { probe(); }, config.db.replicaCheckMs);
  if (timer.unref) timer.unref();
  probe();
  if (!unsubscribe) {
    unsubscribe = require('./cluster').subscribe(TOPIC, (d) => {
      if (Array.isArray(d.k)) remember(d.k.filter((k) => typeof k === 'string' && k.length <= 32).slice(0, 300));
    });
  }
  return true;
}

async function stop() {
  clearInterval(timer);
  timer = null;
  if (unsubscribe) { unsubscribe(); unsubscribe = null; }
  const p = pool;
  pool = null;
  if (p) await p.end().catch(() => {});
}

function stats(t = Date.now()) {
  if (!configured()) return { configured: false };
  return {
    configured: true, usable: usable(t), up: state.up, standby: state.standby, lagMs: state.lagMs,
    maxLagMs: config.db.replicaMaxLagMs, checkedAt: state.checkedAt || null, lastError: state.lastError,
    reads: state.reads, fallbacks: state.fallbacks, primary: { ...state.primary }, sticky: sticky.size,
  };
}

/** فقط آزمون: وضعیت از صفر (کلیدِ HMAC هم دوباره از پیکربندی). */
async function _reset() {
  await stop();
  Object.assign(state, { up: false, standby: null, lagMs: null, checkedAt: 0, lastError: '', probes: 0, reads: 0, fallbacks: 0, primary: Object.create(null) });
  sticky.clear();
  keyCache = null;
}

module.exports = {
  configured, writes, reads, query, many, one, reason, usable, probe, applyProbe, start, stop, stats,
  keysOf, isSticky, remember, PROBE_SQL, _reset,
};
