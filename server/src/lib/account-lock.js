'use strict';
/**
 * قفلِ یک حساب تا پایانِ کارِ گردانندهٔ همین درخواست — برای سنجشی که پیش از تراکنشِ دیگری
 * (push ِ `sync-v1.js` یا `sync-v3.js`) می‌خواند و نتیجه‌اش تا پایانِ آن تراکنش باید درست بماند
 * (گاوصندوق: «این ردیف هست؟»، `safe-ledger.js`).
 *
 * ── دو لایه ─────────────────────────────────────────────────────────
 *   • صفِ همین فرآیند (زنجیرهٔ promise) — روی یک نمونهٔ سرور همین بس است و PGlite فقط همین را دارد؛
 *   • روی PostgreSQL، قفلِ advisoryِ نشستی با `pg_try_advisory_lock` و تلاشِ دوباره، روی اتصالی از
 *     **استخرِ کوچکِ جدا** (`db.extraPool`) — اتصالِ استخرِ اصلی هرگز گرفته نمی‌ماند، و چون صفِ
 *     فرآیند جلوتر است هر حساب در هر نمونه حداکثر یک اتصالِ قفل دارد. کلید با قفلِ تراکنشیِ v1
 *     (`sync-v1:shop:shop:<حساب>`) فرق دارد، پس چرخهٔ انتظار ساخته نمی‌شود.
 *
 * ── آزاد شدن ────────────────────────────────────────────────────────
 *   با `res.end` (هر پاسخی: موفق، رد، خطای `next(err)`) — **نه** با `close`: اتصالی که وسطِ کار
 *   قطع شود تراکنش را نمی‌ایستاند، پس قفل هم تا پایانِ کار می‌ماند. سقفِ سختِ [HOLD_MS] هر قفلِ
 *   گیرکرده را آزاد می‌کند؛ انتظار در صف هم سقف دارد ([WAIT_MS]) و بعد `503 SAFE_BUSY`. درخواستی
 *   که در صف قطع شده باشد، پس از رسیدنِ نوبت بی‌درنگ آزاد می‌کند و کاری نمی‌کند.
 */
const db = require('../db');
const { ApiError } = require('../middleware/errors');

/** سقف‌ها — آزمون کوچکشان می‌کند */
const limits = { WAIT_MS: 15_000, HOLD_MS: 60_000, RETRY_MS: 25, POOL_MAX: 4 };

const chains = new Map();
let lockPool;

function busy() {
  return new ApiError(503, 'SAFE_BUSY', 'گاوصندوقِ این دکان همین حالا در حالِ ثبت است؛ چند لحظهٔ دیگر دوباره');
}

const sleep = (ms) => new Promise((r) => { setTimeout(r, ms).unref?.(); });

/** نوبت در صفِ همین فرآیند؛ برگشتی آزاد می‌کند. انتظارِ رهاشده نوبتش را هم پس می‌دهد. */
function localTurn(key, waitMs) {
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
    const t = setTimeout(() => { late = true; reject(busy()); }, waitMs);
    t.unref?.();
    prev.then(() => {
      clearTimeout(t);
      if (late) free(); else resolve(free);
    });
  });
}

/** اتصالی از استخرِ قفل تا [deadline]؛ اتصالی که دیر برسد پس داده می‌شود */
async function connectBy(deadline) {
  lockPool ||= db.extraPool(limits.POOL_MAX);
  const pending = lockPool.connect();
  let timer;
  const late = new Promise((_, reject) => {
    timer = setTimeout(() => reject(busy()), Math.max(0, deadline - Date.now()));
    timer.unref?.();
  });
  try {
    return await Promise.race([pending, late]);
  } catch (err) {
    pending.then((c) => c.release(), () => {});
    throw err;
  } finally {
    clearTimeout(timer);
  }
}

/** قفلِ نشستیِ PostgreSQL؛ روی PGlite هیچ */
async function dbTurn(key, deadline) {
  if (db.isPglite()) return async () => {};
  const client = await connectBy(deadline);
  try {
    for (;;) {
      const r = await client.query('SELECT pg_try_advisory_lock(hashtext($1)) AS ok', [key]);
      if (r.rows[0].ok) break;
      if (Date.now() > deadline) throw busy();
      await sleep(limits.RETRY_MS);
    }
  } catch (err) {
    client.release(true);
    throw err;
  }
  return async () => {
    try {
      await client.query('SELECT pg_advisory_unlock(hashtext($1))', [key]);
      client.release();
    } catch {
      client.release(true);                          //  اتصال بسته می‌شود و قفل با نشست می‌رود
    }
  };
}

/**
 * قفلِ [key] برای همین درخواست، تا `res.end`.
 * @returns {Promise<Function|null>} تابعِ آزادکننده (یک‌باره؛ برای خطای خودِ گرداننده پیش از
 *          پاسخ)، یا null یعنی اتصال در صف قطع شد یا پاسخ داده شد: قفل آزاد است و گرداننده کاری نکند
 * @throws ApiError 503 `SAFE_BUSY` اگر انتظار از سقف گذشت
 */
async function holdFor(res, key) {
  //  شنونده پیش از صف: قطعِ اتصال در صف هم دیده می‌شود
  let gone = false;
  const onClose = () => { gone = true; };
  res.once('close', onClose);
  const deadline = Date.now() + limits.WAIT_MS;
  let freeLocal;
  let freeDb;
  try {
    freeLocal = await localTurn(key, limits.WAIT_MS);
    freeDb = await dbTurn(key, deadline);
  } catch (err) {
    freeLocal?.();
    throw err;
  } finally {
    res.removeListener('close', onClose);
  }
  let done = false;
  let cap = null;
  const free = () => {
    if (done) return;
    done = true;
    clearTimeout(cap);
    freeDb().finally(freeLocal);
  };
  if (gone || res.destroyed || res.writableEnded) {
    free();
    return null;
  }
  cap = setTimeout(() => {
    console.warn(`[account-lock] ${key} پس از ${limits.HOLD_MS}ms هنوز پاسخ نداده بود؛ قفل آزاد شد`);
    free();
  }, limits.HOLD_MS);
  cap.unref?.();
  //  پایانِ کارِ گرداننده: هر پاسخی (`res.json`، `res.send`، `send` ِ v3، و خطای `next(err)`) به
  //  `res.end` می‌رسد — حتی وقتی اتصال وسطِ کار قطع شده باشد
  const end = res.end;
  res.end = function endAndFree(...args) {
    try { return end.apply(this, args); } finally { free(); }
  };
  return free;
}

module.exports = { holdFor, limits };
