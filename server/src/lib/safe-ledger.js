'use strict';
/**
 * گاوصندوقِ دکان — PAY-18 (طرحِ SAFE-V1): سنجشِ سرور برای opهای `safeEntries`.
 *
 * مانده و تاریخچه را برنامه از روی دفتر حساب می‌کند (`core/safe/SafeCore.kt`)؛ سرور فقط
 * نمی‌گذارد ردیفی بنشیند که:
 *   • فقط‌افزودنی نیست: ویرایش یا حذف ⇒ `safe_append_only` (v3: سیاستِ `log` خودش)؛
 *   • شکلش درست نیست ⇒ `safe_invalid` — همان [check] ِ هسته (`SafeCore.check`)، با بردارِ
 *     مشترکِ `core/test-vectors/safe-v1.json`، به‌علاوهٔ نوعِ JSON. ردیفِ بدشکل خواندنِ کلِ
 *     دفتر را روی برنامه‌ها می‌شکست؛
 *   • کاربرش `safe.move` ندارد ⇒ `safe_not_allowed` (نقش + دو لایه، `staff-perms`). تنها
 *     استثنا «از دخل به گاوصندوق» است: پول در دکان می‌ماند و نوشتن بس است.
 *
 * ⛔ `sync-v1.js` (مشترک با پمپ)، `PUMP_TABLES` و `/api/pump/*` دست نمی‌خورند: v1 از درِ
 *    میان‌افزارِ فقط‌دکانِ `routes/sync-v1.js` ([v1SafeGuard]) و v3 در `authorize`ِ `sync-v3.js`.
 */
const permCore = require('./perm-core');
const { can } = require('./permissions');
const staffPerms = require('./staff-perms');
const { many } = require('../db');
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

/**
 * داوریِ یک ردیف — null یعنی بنشیند.
 * @param kind  `insert` یا هر کارِ دیگر (v1 `type`، v3 `kind`)
 * @param allowed  تابعِ تنبلِ [mayMove] تا لایه‌ها یک بار در هر درخواست خوانده شوند
 */
async function judge(kind, row, allowed) {
  if (kind !== 'insert') return 'safe_append_only';
  if (check(row) !== null) return 'safe_invalid';
  if (!OPEN_KINDS.includes(row.kind) && !(await allowed())) return 'safe_not_allowed';
  return null;
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
      if (!isPlainObject(raw) || raw.table !== TABLE) return;
      const v = validateOp('shop', raw);
      if (v.ok) mine.push({ i, op: v.op });           //  opِ بدشکل را خودِ v1 با دلیلِ خودش رد می‌کند
    });
    if (!mine.length) return next();
    //  opِ ثبت‌شده را خودِ v1 «تکراری» جواب می‌دهد — حتی اگر نقش از آن روز عوض شده باشد
    const seen = new Set((await many(
      'SELECT op_id FROM oplog WHERE op_id = ANY($1::text[])', [mine.map(({ op }) => String(op.op_id))]
    )).map((r) => r.op_id));
    const allowed = once(() => mayMove(ctx));
    const rejected = new Map();
    for (const { i, op } of mine) {
      if (seen.has(String(op.op_id))) continue;
      const row = { ...(isPlainObject(op.fields) ? op.fields : {}), id: op.row_id };
      const reason = await judge(op.type, row, allowed);
      if (reason) rejected.set(i, { op_id: String(op.op_id), status: 'rejected', reason });
    }
    rejectOps(req, res, ops, rejected);
    next();
  } catch (err) { next(err); }
}

/** همان داوری برای opِ بومیِ v3 (`authorize`ِ `sync-v3.js`؛ opِ رسیده از v1 آن‌جا سنجیده بود) */
async function judgeV3(ctx, op) {
  const row = { ...(isPlainObject(op.fields) ? op.fields : {}), id: op.row_id };
  return judge(op.kind, row, () => mayMove(ctx));
}

module.exports = { TABLE, PERM, KINDS, REASONS, check, mayMove, v1SafeGuard, judgeV3 };
