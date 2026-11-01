'use strict';
/**
 * ── جدولِ نوشتنیِ هر نقش در Sync v3 (STF-01، A16) ───────────────────────
 * `applyOne` ِ `sync-v3.js` پیش از `authorize` این‌جا را می‌پرسد. از آن فایل جدا شد تا از سقفِ
 * اندازه نگذرد؛ رفتار همان است:
 *  - جدولی که این نقش نمی‌نویسد (صندوق‌دار خرید، انباردار فروش، حسابدار هیچ) ⇒ `table_not_allowed`.
 *    ردِ یک op کلِ واحدِ tx را برمی‌گرداند، پس قلم و حرکتِ انبارِ همان سند هم نمی‌نشیند.
 *  - قاعدهٔ ردیف (صندوق‌دار: اصلاحِ قلمِ برگشتِ خودش، حرکتِ فاکتور و برگشت) — داوریِ کلِ دسته پیش از
 *    اعمال (`role-rows.judge`)؛ opی که داوری ندارد بسته است.
 *  سه نقشِ قدیم «*»اند و قاعدهٔ ردیف ندارند، پس برای آن‌ها هیچ چیز عوض نمی‌شود و هیچ پرس‌وجویی نیست.
 */
const permCore = require('./perm-core');
const roleRows = require('./role-rows');

const ACC = 'app=$1 AND account_kind=$2 AND account_id=$3';
const acc = (ctx) => [ctx.app, ctx.accountKind, ctx.accountId];

/**
 * داوریِ ردیف‌های قاعده‌دارِ دسته ⇒ Map(op ⇒ {ok, reason})؛ null وقتی نقش قاعدهٔ ردیف ندارد یا چنین
 * opی نیست. ⛔ درونِ تراکنشِ push و پس از قفلِ حساب (`c`): وگرنه دو Pushِ هم‌زمان هر دو «جا» می‌دیدند
 * و «یک اصلاح برای هر قلم» / سقفِ حرکت دو بار خرج می‌شد (بازبینیِ دومِ #336، د). «برگشتِ خودی» =
 * ساختهٔ همین کاربر، نه `device_id` ِ بدنه (همان، ج). برگشتِ همین دسته فقط از همان واحد (tx یا
 * بستهٔ `bundleV3`) و فقط اگر op_idاش تازه است و واحدش نشکسته.
 */
async function judgeRows(c, ctx, plan) {
  const all = [];
  plan.forEach((u, ui) => u.items.forEach((it) => it.ops.forEach((op) => all.push({ op, ui, broken: !!u.broken }))));
  if (!all.some(({ op }) => permCore.rowRule(ctx.role, op.table))) return null;
  const rows = async (sql, params) => (await c.query(sql, params)).rows;
  const dup = new Set((await rows(
    `SELECT op_id FROM sync_v3_ops WHERE ${ACC} AND op_id = ANY($4::text[])`, [...acc(ctx), all.map(({ op }) => op.op_id)]
  )).map((r) => r.op_id));
  const items = all.map(({ op, ui, broken }) => ({
    op, table: op.table, rowId: String(op.row_id || ''), insert: op.kind === 'insert',
    fresh: op.kind === 'insert' && !broken && !dup.has(op.op_id), live: !broken && !dup.has(op.op_id),
    cancel: op.kind === 'cancel', fields: op.fields, unit: ui,
  }));
  const res = await roleRows.judge(ctx.role, items, roleRows.v3Db(rows, ctx, ctx.userId), { v3: true, perUnit: true });
  const out = new Map();
  for (const it of items) if (res.has(it)) out.set(it.op, res.get(it));
  return out;
}

/** داوری + نویسندهٔ درجِ برگشتِ صندوق‌دار (همان تراکنش؛ opی که ننشیند در sync_v3_ops نیست) */
async function prepare(c, ctx, plan) {
  const rowChecks = await judgeRows(c, ctx, plan);
  if (roleRows.authorRole(ctx.role)) {
    await roleRows.noteAuthors((sql, p) => c.query(sql, p), ctx, plan.filter((u) => !u.broken)
      .flatMap((u) => u.items.flatMap((it) => it.ops))
      .filter((op) => op.table === 'returns' && op.kind === 'insert').map((op) => op.op_id));
  }
  return rowChecks;
}

//  یک بار برای هر دستهٔ push: نخستین `applyOne` (پیش از نشستنِ هیچ opی، پس از شکستنِ واحدهای
//  ردشده و درونِ تراکنش پس از قفل) داوری را می‌سازد؛ بقیهٔ opهای همان دسته همان را می‌خوانند
const prepared = new WeakMap();

/** دلیلِ ردِ نقشی، یا null. v1 و سرور خودشان سنجیده بودند (`v1Guard`) */
async function denied(c, ctx, op, origin, plan) {
  if (origin === 'v1' || origin === 'server') return null;
  const role = ctx.role;
  if (!permCore.canWrite(role, op.table)) return 'table_not_allowed';
  if (!permCore.rowRule(role, op.table) && !(plan && roleRows.authorRole(role))) return null;
  let rowChecks = null;
  if (plan) {
    if (!prepared.has(plan)) prepared.set(plan, prepare(c, ctx, plan));
    rowChecks = await prepared.get(plan);
  }
  if (!permCore.rowRule(role, op.table)) return null;
  const r = rowChecks ? rowChecks.get(op) : null;
  return !r || !r.ok ? 'table_not_allowed' : null;
}

module.exports = { denied, judgeRows, prepare };
