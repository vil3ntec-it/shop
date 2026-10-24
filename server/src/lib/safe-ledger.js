'use strict';
/**
 * گاوصندوقِ دکان — PAY-18 (طرحِ SAFE-V1): سنجشِ سرور برای opهای `safeEntries`.
 *
 * مانده و تاریخچه را برنامه از روی دفتر حساب می‌کند (`core/safe/SafeCore.kt`)؛ سرور فقط
 * نمی‌گذارد ردیفی بنشیند که:
 *   • فقط‌افزودنی نیست: ویرایش یا حذف، یا درجِ دوباره روی ردیفی که هست با محتوای دیگر،
 *     یا دو درجِ یک ردیف در یک دسته ⇒ `safe_append_only`. v1 درج را روی ردیفِ موجود ادغام
 *     می‌کند، پس این‌جا `sync_rows` خوانده می‌شود؛ درجِ عیناً همان (فرستادنِ دوباره پس از
 *     بازیابی) بی‌اثر می‌گذرد — همان `inserted && same` ِ v3. v3 خودش با سیاستِ `log` می‌سنجد؛
 *   • شکلش درست نیست ⇒ `safe_invalid` — همان [check] ِ هسته (`SafeCore.check`)، با بردارِ
 *     مشترکِ `core/test-vectors/safe-v1.json`، به‌علاوهٔ نوعِ JSON و زمانی که بیش از
 *     [MAX_AHEAD_MS] از ساعتِ سرور جلوتر است. ردیفِ بدشکل خواندنِ کلِ دفتر را روی برنامه‌ها
 *     می‌شکست، و ردیفِ «آینده» هر شمارشِ بعدی را پیش از خودش می‌انداخت؛
 *   • کاربرش `safe.move` ندارد ⇒ `safe_not_allowed` (نقش + دو لایه، `staff-perms`). تنها
 *     استثنا «از دخل به گاوصندوق» است: پول در دکان می‌ماند و نوشتن بس است.
 *
 * ⛔ `sync-v1.js` (مشترک با پمپ)، `PUMP_TABLES` و `/api/pump/*` دست نمی‌خورند: v1 از درِ
 *    میان‌افزارِ فقط‌دکانِ `routes/sync-v1.js` ([v1SafeGuard]) و v3 در `authorize`ِ `sync-v3.js`.
 */
const permCore = require('./perm-core');
const { can } = require('./permissions');
const staffPerms = require('./staff-perms');
const db = require('../db');

const { many } = db;
const { rejectOps } = require('./v1-reject');

const TABLE = 'safeEntries';
const PERM = 'safe.move';

const KINDS = Object.freeze(['count', 'in', 'out', 'from_drawer', 'to_drawer', 'to_bank']);
const LINKED = Object.freeze(['from_drawer', 'to_drawer']);
/** کارهایی که پول را از دکان بیرون نمی‌برند و `safe.move` نمی‌خواهند */
const OPEN_KINDS = Object.freeze(['from_drawer']);
const OTHER = 'other';
/** همان `SafeCore.REASONS` */
const REASONS = Object.freeze({
  in: Object.freeze(['deposit', OTHER]),
  out: Object.freeze(['owner_withdrawal', 'petty_expense', OTHER]),
});
const TEXT_FIELDS = Object.freeze(['by', 'reason', 'note', 'bagNo', 'shiftId', 'auditId']);
/** ساعتِ دستگاه تا یک روز جلوتر از سرور پذیرفته است (منطقهٔ زمانیِ غلط، نه بیشتر) */
const MAX_AHEAD_MS = 24 * 60 * 60 * 1000;
const MAX_ID = 64;
const MAX_TEXT = 200;
const MAX_BAG = 40;

function isPlainObject(v) { return v !== null && typeof v === 'object' && !Array.isArray(v); }
const absent = (v) => v === undefined || v === null;
const blank = (v) => absent(v) || String(v).trim() === '';
const longer = (v, max) => typeof v === 'string' && v.length > max;

/**
 * شکلِ یک ردیف (همان نام‌های روی خط: `createdAt`) — null یعنی درست، وگرنه کدِ `SafeCore.Code`.
 * ترتیبِ سنجش همان ترتیبِ هسته است تا هر ردیف یک کد بگیرد.
 */
function check(row) {
  if (!isPlainObject(row)) return 'BAD_FIELD';
  if (typeof row.id !== 'string' || row.id.trim() === '') return 'ID_REQUIRED';
  if (typeof row.kind !== 'string' || !KINDS.includes(row.kind)) return 'BAD_KIND';
  const min = row.kind === 'count' ? 0 : 1;
  if (!Number.isSafeInteger(row.amountMinor) || row.amountMinor < min) return 'INVALID_AMOUNT';
  if (!Number.isSafeInteger(row.createdAt) || row.createdAt <= 0) return 'INVALID_TIME';
  for (const k of TEXT_FIELDS) if (!absent(row[k]) && typeof row[k] !== 'string') return 'BAD_FIELD';
  if (!absent(row.expectedMinor) && !Number.isSafeInteger(row.expectedMinor)) return 'BAD_FIELD';
  if (row.id.length > MAX_ID || longer(row.by, MAX_TEXT) || longer(row.reason, MAX_TEXT) || longer(row.note, MAX_TEXT)
    || longer(row.bagNo, MAX_BAG) || longer(row.shiftId, MAX_ID) || longer(row.auditId, MAX_ID)) return 'TEXT_TOO_LONG';
  const allowed = REASONS[row.kind];
  if (allowed) {
    if (absent(row.reason)) return 'REASON_REQUIRED';
    if (!allowed.includes(row.reason)) return 'BAD_REASON';
    if (row.reason === OTHER && blank(row.note)) return 'NOTE_REQUIRED';
  } else if (row.kind !== 'count' && !absent(row.reason)) return 'BAD_FIELD';
  if (row.kind === 'to_bank' && blank(row.bagNo)) return 'BAG_REQUIRED';
  if (row.kind === 'count' && !absent(row.bagNo)) return 'BAD_FIELD';
  if ((row.kind === 'count') === absent(row.expectedMinor)) return 'BAD_FIELD';
  if (LINKED.includes(row.kind)) {
    if (blank(row.shiftId) || blank(row.auditId)) return 'LINK_REQUIRED';
  } else if (!absent(row.shiftId) || !absent(row.auditId)) return 'BAD_FIELD';
  return null;
}

/** آیا این عضو (نقش + لایهٔ نقش + لایهٔ خودش) پولِ گاوصندوق را جابه‌جا می‌کند */
async function mayMove(ctx) {
  if (!can(ctx.role, PERM)) return false;
  return permCore.allows(ctx.role, await staffPerms.layersFor(ctx.accountId, ctx.role, ctx.userId), PERM);
}

/** شکل + زمانِ نه‌چندان آینده — null یعنی درست */
function invalid(row, nowMs) {
  return check(row) !== null || row.createdAt > nowMs + MAX_AHEAD_MS;
}

/** محتوای ردیف بی `id` و بی کلیدِ null، با ترتیبِ ثابتِ کلیدها — برای «همان ردیف است؟» */
function canonical(row) {
  const out = {};
  for (const k of Object.keys(row || {}).sort()) if (k !== 'id' && !absent(row[k])) out[k] = row[k];
  return JSON.stringify(out);
}

/**
 * داوریِ درجِ یک ردیفِ تازه (نه ردیفِ موجود) — null یعنی بنشیند.
 * @param allowed  تابعِ تنبلِ [mayMove] تا لایه‌ها یک بار در هر درخواست خوانده شوند
 */
async function judgeNew(row, allowed) {
  if (!OPEN_KINDS.includes(row.kind) && !(await allowed())) return 'safe_not_allowed';
  return null;
}

/*
 *  قفلِ هر حساب برای push ِ گاوصندوق — تا پایانِ پاسخ (و پس تراکنشِ push ِ `sync-v1.js`).
 *  سنجشِ «ردیف هست؟» پیش از تراکنشِ v1 است؛ بی قفل دو push ِ هم‌زمان با یک شناسه هر دو
 *  «نیست» می‌دیدند و هر دو می‌نشستند. push ِ دوم این‌جا می‌ایستد تا اولی پاسخ بدهد، بعد
 *  `sync_rows` را می‌خواند. دو لایه: زنجیرهٔ promise در همین فرآیند، و روی PostgreSQL قفلِ
 *  advisoryِ نشستی روی اتصالِ جدا (برای چند نمونهٔ سرور). کلیدش با قفلِ تراکنشیِ خودِ v1
 *  (`sync-v1:shop:shop:<حساب>`) فرق دارد، پس بن‌بست نمی‌سازد.
 */
const LOCK_TIMEOUT = '15s';
const chains = new Map();

function localLock(key) {
  const prev = chains.get(key) || Promise.resolve();
  let release;
  const mine = new Promise((r) => { release = r; });
  const tail = prev.then(() => mine);
  chains.set(key, tail);
  return prev.then(() => () => {
    release();
    if (chains.get(key) === tail) chains.delete(key);
  });
}

async function dbLock(key) {
  if (db.isPglite()) return async () => {};
  const client = await db.getPool().connect();
  try {
    await client.query(`SET lock_timeout = '${LOCK_TIMEOUT}'`);
    await client.query('SELECT pg_advisory_lock(hashtext($1))', [key]);
  } catch (err) {
    client.release(true);
    throw err;
  }
  return async () => {
    try {
      await client.query('SELECT pg_advisory_unlock(hashtext($1))', [key]);
      await client.query('RESET lock_timeout');
      client.release();
    } catch {
      client.release(true);                          //  اتصالِ خراب بسته می‌شود و قفل با نشست می‌رود
    }
  };
}

/** قفل تا `finish`/`close` ِ همین پاسخ؛ تابعِ برگشتی آزاد می‌کند (یک بار) */
async function lockAccount(res, accountId) {
  const key = `safe-v1:shop:${accountId}`;
  const freeLocal = await localLock(key);
  let freeDb;
  try { freeDb = await dbLock(key); } catch (err) { freeLocal(); throw err; }
  let done = false;
  const free = () => {
    if (done) return;
    done = true;
    freeDb().finally(freeLocal);
  };
  res.once('finish', free);
  res.once('close', free);
  return free;
}

function once(fn) {
  let p = null;
  return () => (p ||= fn());
}

/** میان‌افزارِ push ِ v1 — فقط دکان؛ پمپ و بقیهٔ جدول‌ها بی هیچ کاری می‌گذرند */
async function v1SafeGuard(req, res, next) {
  const ctx = req.sync;
  if (!ctx || ctx.app !== 'shop' || ctx.accountKind !== 'shop' || !ctx.accountId) return next();
  try {
    const ops = Array.isArray(req.body?.ops) ? req.body.ops : null;
    if (!ops) return next();
    const { validateOp } = require('./sync-v1');
    const mine = [];
    ops.forEach((raw, i) => {
      //  v1 نامِ جدول را از `table`/`table_name`/`tableName` می‌گیرد و یک‌دست می‌کند
      //  (`safe_entries`، `SafeEntries`، …)، پس سنجش روی همان نامِ یک‌دست‌شده است
      const v = validateOp('shop', raw);
      if (v.ok && v.op.table === TABLE) mine.push({ i, op: v.op });   //  opِ بدشکل را خودِ v1 رد می‌کند
    });
    if (!mine.length) return next();
    const free = await lockAccount(res, ctx.accountId);
    try {
      await judgeBatch(ctx, ops, mine, req, res);
    } catch (err) {
      free();
      throw err;
    }
    next();
  } catch (err) { next(err); }
}

/** داوریِ opهای گاوصندوقِ یک push، زیرِ قفلِ حساب */
async function judgeBatch(ctx, ops, mine, req, res) {
  const acc = [ctx.accountId];
  //  opِ ثبت‌شدهٔ همین حساب را خودِ v1 «تکراری» جواب می‌دهد — حتی اگر نقش از آن روز عوض شده باشد
  const seen = new Set((await many(
    `SELECT op_id FROM oplog WHERE app='shop' AND account_kind='shop' AND account_id=$1 AND op_id = ANY($2::text[])`,
    [...acc, mine.map(({ op }) => String(op.op_id))]
  )).map((r) => r.op_id));
  //  ردیف‌های موجود (حتی پاک‌شده): درجِ v1 روی آن‌ها ادغام می‌شد
  const existing = new Map((await many(
    `SELECT row_id, data, deleted_at FROM sync_rows
      WHERE app='shop' AND account_kind='shop' AND account_id=$1 AND table_name=$2 AND row_id = ANY($3::text[])`,
    [...acc, TABLE, [...new Set(mine.map(({ op }) => String(op.row_id)))]]
  )).map((r) => [r.row_id, r]));
  const allowed = once(() => mayMove(ctx));
  const nowMs = Date.now();
  const batch = new Set();
  const rejected = new Map();
  const reject = (i, op, reason) => rejected.set(i, { op_id: String(op.op_id), status: 'rejected', reason });
  for (const { i, op } of mine) {
    if (seen.has(String(op.op_id))) continue;
    if (op.type !== 'insert') { reject(i, op, 'safe_append_only'); continue; }
    const row = { ...(isPlainObject(op.fields) ? op.fields : {}), id: op.row_id };
    if (invalid(row, nowMs)) { reject(i, op, 'safe_invalid'); continue; }
    if (batch.has(op.row_id)) { reject(i, op, 'safe_append_only'); continue; }
    batch.add(op.row_id);
    const cur = existing.get(op.row_id);
    if (cur) {
      //  همان ردیف، عیناً ⇒ بی‌اثر می‌گذرد؛ هر چیزِ دیگر (یا ردیفِ پاک‌شده) ویرایش است
      if (cur.deleted_at !== null || canonical(cur.data) !== canonical(row)) reject(i, op, 'safe_append_only');
      continue;
    }
    const reason = await judgeNew(row, allowed);
    if (reason) reject(i, op, reason);
  }
  rejectOps(req, res, ops, rejected);
}

/**
 * همان داوری برای opِ بومیِ v3 (`authorize`ِ `sync-v3.js`؛ opِ رسیده از v1 آن‌جا سنجیده بود).
 * ردیفِ موجود را سیاستِ `log` ِ خودِ v3 داوری می‌کند: عیناً همان بی‌اثر، دیگری صندوقِ تعارض.
 * @param existing  ردیفِ فعلیِ v3 (یا null)
 */
async function judgeV3(ctx, op, existing) {
  if (op.kind !== 'insert') return 'safe_append_only';
  const row = { ...(isPlainObject(op.fields) ? op.fields : {}), id: op.row_id };
  if (invalid(row, Date.now())) return 'safe_invalid';
  //  عیناً همان (فرستادنِ دوباره) بی‌اثر است؛ محتوای دیگر را v3 به صندوقِ تعارض می‌برد — ولی
  //  کسی که خودش این کار را ندارد (شاگرد) تعارض هم نمی‌سازد
  if (existing && canonical(existing.data) === canonical(row)) return null;
  return judgeNew(row, () => mayMove(ctx));
}

module.exports = { TABLE, PERM, KINDS, REASONS, MAX_AHEAD_MS, check, canonical, mayMove, lockAccount, v1SafeGuard, judgeV3 };
