'use strict';
/**
 * گاوصندوقِ دکان — PAY-18 (طرحِ SAFE-V1): سنجشِ سرور برای opهای `safeEntries`.
 *
 * مانده و تاریخچه را برنامه از روی دفتر حساب می‌کند (`core/safe/SafeCore.kt`)؛ سرور فقط
 * نمی‌گذارد ردیفی بنشیند که:
 *   • فقط‌افزودنی نیست: ویرایش یا حذف، یا درجِ دوباره روی ردیفی که هست با محتوای دیگر،
 *     یا دو درجِ یک ردیف در یک دسته ⇒ `safe_append_only`. v1 درج را روی ردیفِ موجود ادغام
 *     می‌کند، پس این‌جا `sync_rows` خوانده می‌شود؛ درجِ عیناً همان یا بخشی از همان (فرستادنِ
 *     دوباره پس از بازیابی) بی‌اثر می‌گذرد ([within]). v3 خودش با سیاستِ `log` می‌سنجد؛
 *   • شکلش درست نیست ⇒ `safe_invalid` — همان [check] ِ هسته (`SafeCore.check`)، با بردارِ
 *     مشترکِ `core/test-vectors/safe-v1.json`، به‌علاوهٔ نوعِ JSON و زمانی که بیش از
 *     [MAX_AHEAD_MS] از ساعتِ سرور جلوتر است. ردیفِ بدشکل خواندنِ کلِ دفتر را روی برنامه‌ها
 *     می‌شکست، و ردیفِ «آینده» هر شمارشِ بعدی را پیش از خودش می‌انداخت. opِ گاوصندوقی که خودِ
 *     `validateOp` ِ v1 نمی‌پذیرد هم همین‌جا `safe_invalid` است، نه «v1 خودش رد می‌کند»؛
 *   • کاربرش `safe.move` ندارد ⇒ `safe_not_allowed` (نقش + دو لایه، `staff-perms`). تنها
 *     استثنا درجِ تازهٔ «از دخل به گاوصندوق» است: پول در دکان می‌ماند و نوشتن بس است.
 *
 * ── جایِ نگهبان ─────────────────────────────────────────────────────
 *   v1: **پس از** `newer-fields` (که کلیدِ null ِ ساختِ قدیمی را برمی‌دارد و اثرِ انگشت را از نو
 *   می‌سازد) — نگهبان همان opی را می‌بیند که v1 می‌نشاند. هر push ِ گاوصندوقی (v1 و v3) قفلِ
 *   حساب را تا پایانِ پاسخ نگه می‌دارد ([hold]، `account-lock.js`)، تا «ردیف هست؟» تا نشستن درست بماند.
 *
 * ⛔ `sync-v1.js` (مشترک با پمپ)، `PUMP_TABLES` و `/api/pump/*` دست نمی‌خورند: v1 از درِ
 *    میان‌افزارِ فقط‌دکانِ `routes/sync-v1.js` ([v1SafeGuard]) و v3 در `authorize`ِ `sync-v3.js`.
 */
const permCore = require('./perm-core');
const { can } = require('./permissions');
const staffPerms = require('./staff-perms');
const { many } = require('../db');
const { rejectOps } = require('./v1-reject');
const { holdFor } = require('./account-lock');
const { SHOP } = require('./tenancy');
const safeFlag = require('./safe-flag');

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
/** همان `SafeCore.MAX_AMOUNT`: یک میلیارد افغانی در هر ردیف */
const MAX_AMOUNT = 100_000_000_000;
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
  if (!Number.isSafeInteger(row.amountMinor) || row.amountMinor < min || row.amountMinor > MAX_AMOUNT) return 'INVALID_AMOUNT';
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

/**
 * آیا [fields] عینِ ردیفِ موجود یا بخشی از آن است — یعنی ادغامِ درجِ v1 روی [data] هیچ مقداری را
 * عوض نمی‌کند. کلیدِ null فقط جایی که ردیف هم آن را ندارد؛ `id` اگر باشد همان شناسهٔ ردیف.
 */
function within(fields, data, rowId) {
  if (!isPlainObject(fields) || !isPlainObject(data)) return false;
  const { canonical } = require('./sync-v1');
  return Object.keys(fields).every((k) => {
    if (k === 'id') return fields.id === rowId;
    if (absent(fields[k])) return absent(data[k]);
    return !absent(data[k]) && canonical(fields[k]) === canonical(data[k]);
  });
}

/**
 * داوریِ درجِ یک ردیفِ تازه (نه ردیفِ موجود) — null یعنی بنشیند.
 * @param allowed  تابعِ تنبلِ [mayMove] تا لایه‌ها یک بار در هر درخواست خوانده شوند
 */
async function judgeNew(row, allowed) {
  if (!OPEN_KINDS.includes(row.kind) && !(await allowed())) return 'safe_not_allowed';
  return null;
}

/** ردیفِ op: فیلدها با شناسهٔ ردیف؛ `id` ِ دیگری در فیلدها شکلِ نادرست است */
function rowOf(op) {
  const fields = isPlainObject(op.fields) ? op.fields : {};
  if (!absent(fields.id) && fields.id !== op.row_id) return null;
  return { ...fields, id: op.row_id };
}

/**
 * آیا opِ خامِ push (هر شکلِ نامِ جدول: `table`/`table_name`/`tableName`، `safe_entries`، …)
 * مالِ گاوصندوق است — همان یک‌دست‌سازیِ `validateOp`، حتی برای opی که `validateOp` نمی‌پذیرد.
 */
function isSafeOp(raw) {
  if (!isPlainObject(raw)) return false;
  const { tableOf } = require('./sync-v1');
  return tableOf('shop', raw.table ?? raw.table_name ?? raw.tableName) === TABLE;
}

/** آیا این دستهٔ push opِ گاوصندوق دارد (برای قفلِ push ِ v3) */
function touches(ops) {
  return Array.isArray(ops) && ops.some(isSafeOp);
}

/**
 * قفلِ گاوصندوقِ حساب تا پایانِ همین پاسخ (`account-lock.js`) — یک کلید برای v1 و v3، چون
 * پلِ v3⇒v1 همان `sync_rows` را می‌نویسد که نگهبانِ v1 می‌خواند.
 * @returns تابعِ آزادکننده، یا null یعنی اتصال قطع شد و گرداننده کاری نکند
 */
function hold(res, ctx) {
  return holdFor(res, `safe:shop:${ctx.accountId}`);
}

/** بخشِ دکان با حسابِ دکان (حسابِ پمپ `station` است؛ `accountKind` ِ دکان همان نامِ بخش است) */
function isShopSync(ctx) {
  return Boolean(ctx && ctx.app === SHOP.app && ctx.accountKind === SHOP.app && ctx.accountId);
}

function once(fn) {
  let p = null;
  return () => (p ||= fn());
}

/** میان‌افزارِ push ِ v1 — فقط دکان؛ پمپ و بقیهٔ جدول‌ها بی هیچ کاری می‌گذرند */
async function v1SafeGuard(req, res, next) {
  const ctx = req.sync;
  if (!isShopSync(ctx)) return next();
  try {
    const ops = Array.isArray(req.body?.ops) ? req.body.ops : null;
    if (!ops || !ops.some(isSafeOp)) return next();
    //  پرچمِ خاموش ⇒ همان main: جدولِ ناشناخته، بی قفل و بی داوری (‎lib/safe-flag.js‎)
    if (!safeFlag.on(ctx)) {
      const rejected = new Map();
      const { validateOp } = require('./sync-v1');
      ops.forEach((raw, i) => {
        if (!isSafeOp(raw)) return;
        const v = validateOp('shop', raw);
        //  همان دلیلی که main می‌داد: شناسهٔ op پیش از جدول سنجیده می‌شود
        const reason = !v.ok && v.reason === 'bad_op_id' ? 'bad_op_id' : 'unknown_table';
        rejected.set(i, { op_id: v.ok ? v.op.op_id : v.op_id, status: 'rejected', reason });
      });
      rejectOps(req, res, ops, rejected);
      return next();
    }
    const free = await hold(res, ctx);
    if (!free) return;                               //  در صف قطع شد: نه داوری، نه v1
    try {
      await judgeBatch(ctx, ops, req, res);
    } catch (err) {
      free();
      throw err;
    }
    next();
  } catch (err) { next(err); }
}

/** داوریِ opهای گاوصندوقِ یک push، زیرِ قفلِ حساب */
async function judgeBatch(ctx, ops, req, res) {
  const { validateOp } = require('./sync-v1');
  const rejected = new Map();
  const reject = (i, opId, reason) => rejected.set(i, { op_id: String(opId ?? ''), status: 'rejected', reason });
  const mine = [];
  ops.forEach((raw, i) => {
    if (!isSafeOp(raw)) return;
    const v = validateOp('shop', raw);
    if (v.ok) mine.push({ i, op: v.op });
    else reject(i, v.op_id, 'safe_invalid');
  });
  if (mine.length) {
    const acc = [ctx.accountId];
    //  opِ ثبت‌شدهٔ همین حساب را خودِ v1 «تکراری» جواب می‌دهد — حتی اگر نقش از آن روز عوض شده باشد
    const seen = new Set((await many(
      `SELECT op_id FROM oplog WHERE app='shop' AND account_kind='shop' AND account_id=$1 AND op_id = ANY($2::text[])`,
      [...acc, mine.map(({ op }) => String(op.op_id))]
    )).map((r) => r.op_id));
    //  ردیف‌های موجود (حتی پاک‌شده): درجِ v1 روی آن‌ها ادغام می‌شد
    const ids = [...new Set(mine.map(({ op }) => String(op.row_id)))];
    const existing = new Map((await many(
      `SELECT row_id, data, deleted_at FROM sync_rows
        WHERE app='shop' AND account_kind='shop' AND account_id=$1 AND table_name=$2 AND row_id = ANY($3::text[])`,
      [...acc, TABLE, ids]
    )).map((r) => [r.row_id, r]));
    //  ردیفی که فقط در v3 است هم «هست»: پلِ v3⇒v1 (`catchUp` ِ `sync-v3.js`، بی این قفل) آن را بعداً
    //  به `sync_rows` می‌برد. هر دو راهِ ساختنِ ردیفِ تازه (push ِ v1 و v3) زیرِ همین قفل‌اند، پس
    //  «نیست ⇐ هست» هرگز بیرون از قفل رخ نمی‌دهد. v1 آن را هنوز ندارد، پس فقط عینِ همان می‌گذرد
    //  (بخشی از آن، ردیفِ ناقص در v1 می‌ساخت).
    const v3only = ids.filter((id) => !existing.has(id));
    if (v3only.length) {
      for (const r of await many(
        `SELECT row_id, data, deleted_at FROM sync_v3_rows
          WHERE app='shop' AND account_kind='shop' AND account_id=$1 AND table_name=$2 AND row_id = ANY($3::text[])`,
        [...acc, TABLE, v3only]
      )) existing.set(r.row_id, { ...r, v3: true });
    }
    const allowed = once(() => mayMove(ctx));
    const nowMs = Date.now();
    const batch = new Set();
    for (const { i, op } of mine) {
      if (seen.has(String(op.op_id))) continue;
      if (op.type !== 'insert' || batch.has(op.row_id)) { reject(i, op.op_id, 'safe_append_only'); continue; }
      batch.add(op.row_id);
      const cur = existing.get(op.row_id);
      if (cur) {
        //  عینِ همان ردیف یا بخشی از آن ⇒ بی‌اثر می‌گذرد؛ هر چیزِ دیگر (یا ردیفِ پاک‌شده) ویرایش است
        const same = within(op.fields, cur.data, op.row_id) && (!cur.v3 || within(cur.data, op.fields, op.row_id));
        if (cur.deleted_at !== null || !same) reject(i, op.op_id, 'safe_append_only');
        continue;
      }
      const row = rowOf(op);
      if (!row || invalid(row, nowMs)) { reject(i, op.op_id, 'safe_invalid'); continue; }
      const reason = await judgeNew(row, allowed);
      if (reason) reject(i, op.op_id, reason);
    }
  }
  rejectOps(req, res, ops, rejected);
}

/**
 * همان داوری برای opِ بومیِ v3 (`authorize`ِ `sync-v3.js`؛ opِ رسیده از v1 آن‌جا سنجیده بود).
 * ردیفِ موجود را سیاستِ `log` ِ خودِ v3 داوری می‌کند: عینِ همان یا بخشی از آن بی‌اثر، دیگری
 * صندوقِ تعارض — و تعارض ساختن کارِ کسی است که `safe.move` دارد، حتی با محتوای «از دخل».
 * @param existing  ردیفِ فعلیِ v3 (یا null)
 */
async function judgeV3(ctx, op, existing) {
  if (op.kind !== 'insert') return 'safe_append_only';
  if (existing && within(op.fields, existing.data, op.row_id)) return null;
  const row = rowOf(op);
  if (!row || invalid(row, Date.now())) return 'safe_invalid';
  if (existing) return (await mayMove(ctx)) ? null : 'safe_not_allowed';
  return judgeNew(row, () => mayMove(ctx));
}

module.exports = {
  TABLE, PERM, KINDS, REASONS, MAX_AHEAD_MS, MAX_AMOUNT,
  check, within, mayMove, isSafeOp, touches, hold, v1SafeGuard, judgeV3,
};
