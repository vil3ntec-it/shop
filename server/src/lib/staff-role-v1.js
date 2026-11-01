'use strict';
/**
 * ── جدولِ نوشتنیِ هر نقش در Sync v1 (STF-01، A16) ───────────────────────
 * `v1Guard` ِ `staff-approval.js` پیش از همهٔ داوری‌ها این‌جا را می‌پرسد: opی که جدولش
 * در حدِ نقش نیست `table_not_allowed` می‌گیرد و هر opِ همین دسته که با
 * `refId`/`saleId`/`purchaseId`/`returnId` به آن بسته است `tx_rejected`.
 * سه نقشِ قدیم «*»اند، پس این شاخه برایشان هرگز کاری نمی‌کند. از `staff-approval.js`
 * جدا شد تا آن فایل از سقفِ اندازه نگذرد؛ رفتار همان است.
 */
const core = require('./perm-core');
const roleRows = require('./role-rows');
const { many, query } = require('../db');

function isPlainObject(v) { return v !== null && typeof v === 'object' && !Array.isArray(v); }

/** فیلدهایی که opِ همراه با آن به سندِ پدر اشاره می‌کند */
const REF_FIELDS = Object.freeze(['refId', 'saleId', 'purchaseId', 'returnId']);

/** opِ `op` به ردیفِ `parent` بسته است؟ (برای ردِ جدولیِ A16) */
function refersTo(parent, op) {
  if (op.table === parent.table && op.row_id === parent.row_id) return true;
  const f = isPlainObject(op.fields) ? op.fields : {};
  return REF_FIELDS.some((k) => typeof f[k] === 'string' && f[k] !== '' && f[k] === parent.row_id);
}

/**
 * opهای v1ِ بیرون از حدِ نقش: جدولی که نقش نمی‌نویسد، یا ردیفی از جدولِ قاعده‌دار که قاعده را
 * نمی‌گذراند (صندوق‌دار: رسیدِ انبار فقط اصلاحِ قلمِ برگشتِ خودش، حرکتِ انبار فقط از فاکتور و
 * برگشت؛ `role-rows.js`). برگشت و فاکتورِ همین دسته فقط اگر واقعاً می‌نشیند: نه op_id ِ ثبت‌شده
 * (پاسخش «تکراری» است و ردیفی نمی‌سازد)، نه opِ نادرستِ ردشده. برگشتی که پس از این با داوریِ
 * تأیید رد شود همراه‌هایش را با خودش می‌برد (`companionOf`).
 */
async function offRoleV1(ctx, valid, rejected) {
  const off = [];
  let ruled = false;
  for (const x of valid) {
    if (!core.canWrite(ctx.role, x.op.table)) off.push(x);
    else if (core.rowRule(ctx.role, x.op.table)) ruled = true;
  }
  if (!ruled) return off;
  const seen = await seenOps(valid);
  const items = valid.map((x) => ({
    x, table: x.op.table, rowId: String(x.op.row_id || ''), insert: x.op.type === 'insert',
    fresh: x.op.type === 'insert' && !seen.has(String(x.op.op_id)) && !rejected.has(x.i),
    live: !seen.has(String(x.op.op_id)) && !rejected.has(x.i), fields: x.op.fields, unit: 0,
  }));
  //  «برگشتِ خودی» = ساختهٔ همین کاربر، نه شناسهٔ دستگاهِ بدنه (بازبینیِ دومِ #336، ج)
  const res = await roleRows.judge(ctx.role, items, roleRows.v1Db(many, ctx, ctx.userId));
  for (const it of items) if (res.has(it) && !res.get(it).ok) off.push(it.x);
  return off;
}

/**
 * ج ِ بازبینیِ دومِ #336: نویسندهٔ درجِ برگشتِ صندوق‌دار — تا «برگشتِ خودی» ِ دستهٔ بعد (حتی از
 * دستگاهِ دیگرِ همین کاربر) به کاربر بسته باشد، نه به `device_id` ِ بدنه. پیش از اعمال: opی که
 * ننشیند (ردِ این نگهبان یا خودِ v1) در oplog نیست و این ثبت اثری ندارد؛ op_id ِ ثبت‌شده در
 * oplog (مثلاً برگشتِ صاحب دکان که دوباره فرستاده شود) هرگز (`roleRows.noteAuthors`).
 */
async function rememberAuthorsV1(ctx, valid, rejected, off) {
  if (!roleRows.authorRole(ctx.role)) return;
  const offSet = new Set(off);
  const ids = valid
    .filter((x) => x.op.table === 'returns' && x.op.type === 'insert' && !rejected.has(x.i) && !offSet.has(x))
    .map((x) => String(x.op.op_id || ''));
  await roleRows.noteAuthors(query, ctx, ids);
}

/**
 * opی که جدولش (یا ردیفش) در حدِ نقش نیست (صندوق‌دار خرید، انباردار فروش، حسابدار هیچ)، و ثبتِ
 * نویسندهٔ درجِ برگشت. روی همان opِ یک‌دست‌شده‌ای سنجیده می‌شود که به v1 می‌رسد (پس از
 * shopNewerFields). سه نقشِ قدیم قاعدهٔ ردیف ندارند، پس برایشان فهرست همیشه خالی است.
 */
async function offRole(ctx, valid, rejected) {
  const off = await offRoleV1(ctx, valid, rejected);
  await rememberAuthorsV1(ctx, valid, rejected, off);
  return off;
}

/** op_idهای ثبت‌شده در oplog (هر حسابی — op_id یکتاست): پاسخشان را خودِ v1 می‌دهد */
async function seenOps(valid) {
  return new Set((await many(
    'SELECT op_id FROM oplog WHERE op_id = ANY($1::text[])', [valid.map((x) => String(x.op.op_id))]
  )).map((r) => r.op_id));
}

/**
 * پیش از هر داوری: opِ بیرون از نقش `table_not_allowed` و همراه‌هایش در همین دسته (`refersTo`)
 * `tx_rejected` — تا برگه‌ای برای opی که به‌هرحال نمی‌نشیند خرج نشود و صندوقِ تعارضی برایش ساخته
 * نشود. opِ ثبت‌شده را باز خودِ v1 «تکراری» جواب می‌دهد. ⇐ opهای زنده (نه ثبت‌شده، نه ردشده).
 */
function dropOff(valid, offTable, seen, rejected) {
  const offLive = offTable.filter((x) => !seen.has(String(x.op.op_id)));
  const drop = (x, reason) => rejected.set(x.i, { op_id: String(x.op.op_id || ''), status: 'rejected', reason });
  for (const x of offLive) drop(x, 'table_not_allowed');
  for (const x of valid) {
    if (rejected.has(x.i) || seen.has(String(x.op.op_id))) continue;
    if (offLive.some((p) => refersTo(p.op, x.op))) drop(x, 'tx_rejected');
  }
  return valid.filter((x) => !seen.has(String(x.op.op_id)) && !rejected.has(x.i));
}

module.exports = { offRole, seenOps, dropOff, refersTo };
