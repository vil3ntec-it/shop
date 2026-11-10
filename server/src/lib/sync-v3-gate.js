'use strict';
/**
 * دروازهٔ تراکنش‌های قفل‌دارِ Sync v3 (PERF-02/PERF-04) — صف در همین فرآیند **پیش از** گرفتنِ
 * اتصالِ استخر.
 *
 * ── چرا ─────────────────────────────────────────────────────────────
 *   push ِ بزرگ (۲۰۰ تا ۵۰۰ op) ۱ تا ۲ ثانیه یک اتصال را نگه می‌دارد و قفلِ حساب
 *   (`pg_advisory_xact_lock` در `sync-v3.js`) درونِ تراکنش گرفته می‌شود؛ پس دستگاهِ دومِ همان دکان
 *   اتصالش را بیکار، پشتِ همان قفل، نگه می‌داشت (burst ِ `docs/PERF-LOAD-fa.md`: تا ۶ از ۱۰). حالا:
 *     ۱. صفِ هر حساب (زنجیرهٔ promise، کلید همان کلیدِ قفل): نوبت پیش از اتصال؛
 *     ۲. سقفِ push ِ هم‌زمان در کلِ فرآیند ([slots]، bulkhead) تا خواندنِ ویترین و میز همیشه اتصال
 *        داشته باشد؛ و پس از هر دو، همان تراکنش با همان قفلِ advisory (درستیِ چند نمونهٔ سرور روی یک
 *        پایگاه: میانِ دو نمونه انتظار هنوز پشتِ قفلِ پایگاه است، با اتصال — مثلِ پیش).
 *   ترتیب، اتمی بودنِ واحدها، تکراری‌ها و ردها همان است: همان تراکنش و همان قفل، فقط دیرتر باز می‌شود.
 *
 * ── پس‌زدن ──────────────────────────────────────────────────────────
 *   انتظار سقف دارد ([waitMs]، هر دو مرحله با هم) و شمارِ منتظرها هم ([queueMax])؛ بیرون از آن‌ها
 *   `503 db_busy` با `Retry-After` — همان پاسخِ استخرِ پر (`middleware/errors.js`). هیچ چیزی اعمال
 *   نشده است و برنامه همان دسته را دوباره می‌فرستد (opها با op_id تکراری‌اند).
 *
 * ── PGlite ──────────────────────────────────────────────────────────
 *   خاموش: یک اتصال دارد و خودِ `db.tx` همهٔ تراکنش‌ها را پشتِ سرِ هم می‌گذارد؛ اتصالی نیست که
 *   بیکار بماند. رفتار همان پیشین است.
 */
const config = require('../config');
const db = require('../db');
const errors = require('../middleware/errors');

const RETRY_AFTER_S = 2;

const chains = new Map();          //  کلید ⇒ دمِ زنجیره
const slotQueue = [];              //  منتظرانِ جای push، به ترتیبِ رسیدن
let inflight = 0;                  //  push هایی که جا دارند (تراکنش باز یا در راه)
let waiting = 0;                   //  منتظرانِ هر دو مرحله
const counters = { waitingMax: 0, inflightMax: 0, rejected: 0, timedOut: 0 };

const busy = () => errors.busy(RETRY_AFTER_S);

function on() { return !db.isPglite() && config.syncV3.gate.enabled; }

/** نوبتِ [key] در صفِ همین فرآیند تا [deadline]؛ برگشتی آزاد می‌کند. انتظارِ رهاشده نوبتش را پس می‌دهد. */
function turn(key, deadline) {
  const prev = chains.get(key) || Promise.resolve();
  let release;
  const mine = new Promise((r) => { release = r; });
  const tail = prev.then(() => mine);
  chains.set(key, tail);
  const free = () => {
    release();
    if (chains.get(key) === tail) chains.delete(key);
  };
  return new Promise((resolve, reject) => {
    let late = false;
    const t = setTimeout(() => { late = true; counters.timedOut++; reject(busy()); }, Math.max(0, deadline - Date.now()));
    t.unref?.();
    prev.then(() => {
      clearTimeout(t);
      if (late) free(); else resolve(free);
    });
  });
}

/** یک جا از [slots] تا [deadline]؛ برگشتی پس می‌دهد. */
function slot(deadline) {
  const max = config.syncV3.gate.slots;
  let done = false;
  const free = () => {
    if (done) return;
    done = true;
    const next = slotQueue.shift();
    if (next) next.grant(); else inflight--;   //  جا مستقیم به نفرِ بعد می‌رسد
  };
  if (!max || inflight < max) {
    inflight++;
    counters.inflightMax = Math.max(counters.inflightMax, inflight);
    return Promise.resolve(free);
  }
  return new Promise((resolve, reject) => {
    const item = {
      grant() { clearTimeout(t); resolve(free); },
    };
    const t = setTimeout(() => {
      const i = slotQueue.indexOf(item);
      if (i >= 0) slotQueue.splice(i, 1);
      counters.timedOut++;
      reject(busy());
    }, Math.max(0, deadline - Date.now()));
    t.unref?.();
    slotQueue.push(item);
  });
}

/**
 * نوبتِ حسابِ [key] (و برای push جا: [heavy]) پیش از گرفتنِ اتصال.
 * @returns {Promise<Function>} آزادکننده (یک‌باره)
 * @throws ApiError 503 `db_busy` اگر صف پر بود یا انتظار از [waitMs] گذشت
 */
async function enter(key, { heavy = false } = {}) {
  if (!on()) return () => {};
  const g = config.syncV3.gate;
  if (g.queueMax > 0 && waiting >= g.queueMax) { counters.rejected++; throw busy(); }
  const deadline = Date.now() + g.waitMs;
  waiting++;
  counters.waitingMax = Math.max(counters.waitingMax, waiting);
  let freeTurn = null;
  let freeSlot = null;
  try {
    freeTurn = await turn(key, deadline);
    if (heavy) freeSlot = await slot(deadline);
  } catch (err) {
    freeTurn?.();
    throw err;
  } finally {
    waiting--;
  }
  let done = false;
  const free = () => {
    if (done) return;
    done = true;
    freeSlot?.();
    freeTurn();
  };
  free.deadline = deadline;
  return free;
}

/**
 * [fn] پس از نوبتِ [key] (و جای push با [heavy])؛ آزاد با پایانِ آن، موفق یا ناموفق. با `poolDeadline` (پیش‌فرض خاموش)
 * push ِ [heavy] `{ acquireBy }` می‌گیرد: گرفتنِ اتصالِ تراکنش هم تا همان سقفِ انتظار (`db.tx`)، نه جدا تا `PG_CONNECT_TIMEOUT_MS`.
 */
async function run(key, heavy, fn) {
  const free = await enter(key, { heavy });
  try {
    return await fn(heavy && free.deadline && config.syncV3.gate.poolDeadline ? { acquireBy: free.deadline } : undefined);
  } finally {
    free();
  }
}

/** نمای همین لحظه (سنجشِ بار و آزمون)؛ `reset` بیشینه‌ها را صفر می‌کند */
function stats({ reset = false } = {}) {
  const out = { on: on(), inflight, waiting, slotQueue: slotQueue.length, keys: chains.size, ...counters };
  if (reset) Object.assign(counters, { waitingMax: waiting, inflightMax: inflight, rejected: 0, timedOut: 0 });
  return out;
}

module.exports = { enter, run, stats, RETRY_AFTER_S };
