'use strict';
/**
 * ⛔ STF-03 — سقفِ فروشِ قرضیِ کارمند در سرور (پرچمِ خاموشِ `shop.credit_cap`).
 *
 * تا امروز سقفِ «به حساب» ِ فاکتور (`debtGiven`) فقط روی دستگاه سنجیده می‌شد؛ opِ مستقیم یا برنامهٔ دست‌کاری‌شده
 * آن را دور می‌زد. با پرچمِ روشن، سرور تنها داور است: مجوزِ `sales.credit` (همان هستهٔ `perm-core`، سقفِ
 * `maxAmount` به Minor) روی **جمعِ قرضِ همان فاکتور پس از همین op** سنجیده می‌شود:
 *   v3   درجِ `sales` (`debtGivenMinor` + دلتاهای پیش‌رسیده) و دلتای مثبتِ `debtGivenMinor`   (`sync-v3.authorize`)
 *   v1   درجِ `sales` (`debtGiven`) در `v1Guard`، و `update` ِ بالابرنده در `staff-edit-v1`  (⛔ `lib/sync-v1.js` دست نمی‌خورد)
 *   راهِ قدیم  هر ردیفِ `sales` که `debtGiven` را از مقدارِ سرور بالاتر ببرد (`sync.pushChanges`؛ کلِ دسته رد)
 * تصمیم همان سه‌حالتهٔ `staff-approval.judge` است:
 *   allow    ⇒ می‌نشیند (همان امروز)
 *   deny     ⇒ `credit_not_allowed` و صندوقِ تعارض (`perm_denied`)
 *   approve  ⇒ «برگهٔ امضاشده»ی مدیر/صاحب (`approval-core`، perm=`sales.credit`) یا «درخواستِ تأیید» ِ از راهِ دور
 *              (`{v:1, request}`، پرچمِ `shop.approval_requests`)؛ وگرنه `approval_required` و op در صندوق نگه داشته
 *              می‌شود (همراه‌ها با آن، مثلِ فاکتورِ تخفیف‌دار). REST برگه نمی‌برد ⇒ ۴۰۳ `approval_required`.
 * برگهٔ قرض در `creditApproval` ِ op (یا `fields.creditApproval`) می‌آید؛ اگر فاکتور تخفیفِ تأییدخواه ندارد، همان
 * `approval` هم پذیرفته است (فقط برگه‌ای با perm=`sales.credit` یا ارجاعِ درخواست).
 *
 * بی‌خطرِ تکرار: op_idِ ثبت‌شده پیش از داوری «تکراری» است؛ برگه و درخواست یک‌بارمصرف‌اند و همان op_id دوباره
 * تکرار است، نه بازپخش (`staff-approval.acceptSlip`). جدا از دکانِ دیگر: پرچم، لایه‌ها و برگه همه با `accountId`.
 * پرچمِ خاموش (پیش‌فرض) ⇒ هیچ خواندنی جز خودِ پرچم (و آن هم فقط برای فاکتورِ قرض‌دار) و پاسخ‌ها همان امروز.
 * صاحبِ دکان هرگز سنجیده نمی‌شود.
 */
//  قاعدهٔ مبلغ (جمعِ قرضِ فاکتور پس از همین درج) مشترک با صندوق: `sale-caps.creditAmount` = `core/perm/SaleCaps`
const caps = require('./sale-caps');
const FLAG = caps.CREDIT_FLAG;
const PERM = caps.CREDIT;

const isObj = (v) => v !== null && typeof v === 'object' && !Array.isArray(v);
const int = (v) => { const n = Number(v); return Number.isFinite(n) ? Math.round(n) : 0; };
/** افغانیِ اعشاریِ v1/راهِ قدیم ⇒ Minor */
const minor = (v) => int(Number(v) * 100);

/** پرچمِ دکانِ همین درخواست — یک بار برای هر دسته (`run` = اتصالِ تراکنش) */
function on(ctx, run = null) {
  if (!ctx || !ctx.accountId || ctx.role === 'owner') return Promise.resolve(false);
  if (ctx._creditCap === undefined) ctx._creditCap = require('./orders').flagOf(ctx.accountId, FLAG, run);
  return ctx._creditCap;
}

const need = (rowId, amount) => ({ perm: PERM, doc: `sales/${rowId}`, req: { amount }, amount });

/**
 * opِ v3 (پس از `validate`) و ردیفِ فعلی ⇒ نیاز، یا null اگر قرضِ تازه‌ای داده نمی‌شود.
 * درجِ دوبارهٔ سندِ نشسته قرضِ تازه نمی‌دهد (همان «تکراری» یا تعارضِ فقط‌افزودنی).
 */
function needV3(op, row) {
  if (!op || op.table !== 'sales' || !isObj(op.fields)) return null;
  const prior = row && isObj(row.data) ? int(row.data.debtGivenMinor) : 0;
  if (op.kind === 'insert') {
    if (row && row.inserted) return null;
    const a = caps.creditAmount(op.fields.debtGivenMinor, prior);
    return a > 0 ? need(op.row_id, a) : null;
  }
  if (op.kind === 'delta') {
    const d = Number(op.fields.debtGivenMinor);
    if (!Number.isFinite(d) || d <= 0) return null;
    return need(op.row_id, prior + int(d));
  }
  return null;
}

/** opِ v1 ِ سنجیده‌شده (`validateOp`) — فقط درج؛ ویرایش در `staff-edit-v1` */
function needV1(op) {
  if (!isObj(op) || op.table !== 'sales' || op.type !== 'insert' || !isObj(op.fields)) return null;
  const a = caps.creditAmount(minor(op.fields.debtGiven));
  return a > 0 ? need(op.row_id, a) : null;
}

/** این opِ v1 به قرض دست می‌زند؟ (بی پرس‌وجو — برای «پرچم را بخوانیم؟») */
function touchesV1(op) {
  return isObj(op) && op.table === 'sales' && (op.type === 'insert' || op.type === 'update')
    && isObj(op.fields) && Object.prototype.hasOwnProperty.call(op.fields, 'debtGiven');
}

/** همان، برای v3 */
function touchesV3(op) {
  return isObj(op) && op.table === 'sales' && (op.kind === 'insert' || op.kind === 'delta')
    && isObj(op.fields) && Number(op.fields.debtGivenMinor) > 0;
}

/**
 * برگهٔ قرض: `creditApproval` ِ op یا فیلدها؛ اگر سندِ تخفیفِ تأییدخواه نیست (`withDiscount` = false)، همان
 * `approval` هم — فقط برگه‌ای که خودش `sales.credit` است، یا ارجاعِ درخواستِ از راهِ دور.
 */
function slipOf(op, withDiscount = false) {
  const f = isObj(op && op.fields) ? op.fields : {};
  if (isObj(op && op.creditApproval)) return op.creditApproval;
  if (isObj(f.creditApproval)) return f.creditApproval;
  if (withDiscount) return null;
  const s = require('./staff-approval').slipOf(op);
  if (!s) return null;
  return s.perm === PERM || require('./approval-requests').isRef(s) ? s : null;
}

/**
 * v3 (`sync-v3.authorize`): پرچم، نیاز و داوری با همان `staff-approval.judge` ⇒ null یا همان شکلِ ردِ `authorize`.
 * `withDiscount` = همین op نیازِ تخفیفِ پذیرفته‌شده داشت (برگهٔ قرض فقط `creditApproval`).
 */
async function judgeV3(c, ctx, op, row, { withDiscount = false, deviceId = '', origin = 'v3' } = {}) {
  if (!touchesV3(op) || !(await on(ctx, (s, p) => c.query(s, p)))) return null;
  const n = needV3(op, row);
  if (!n) return null;
  const sa = require('./staff-approval');
  return sa.judge(c, ctx, n, slipOf(op, withDiscount), { opId: op.op_id, at: sa.wallOf(op.hlc), deviceId, path: origin });
}

/** v3: پرچم برای بسته‌بندیِ برنامهٔ کهنهٔ بی tx (`bundleV3`) — فقط وقتی opی قرض دارد */
async function onUnits(ctx, units) {
  return units.some((u) => u.items.some((it) => it.ops.some(touchesV3))) && on(ctx);
}

/**
 * v1 (`v1Guard`): قرضِ درجِ فاکتور — سندِ بی تخفیفِ تأییدخواه همان نیاز را می‌گیرد (`x.need`)، وگرنه پس از تخفیف
 * داوری می‌شود (`x.credit`). ⇒ پرچم (برای `isMoneyEdit` / `judgeEdits` ِ همان دسته)
 */
async function markV1(ctx, valid) {
  const credit = valid.some((x) => touchesV1(x.op)) && await on(ctx);
  if (!credit) return false;
  for (const x of valid) {
    const n = needV1(x.op);
    if (!n) continue;
    if (x.need) x.credit = n; else x.need = n;
  }
  return true;
}

/**
 * راهِ قدیم (`sync.pushChanges`): ردیفِ کاملِ فاکتور. بالا بردنِ `debtGiven` از مقدارِ سرور ⇒ سنجش؛
 * کاهش و بی‌تغییر آزاد. ⇒ null، یا خطای ۴۰۳ (کلِ دسته در همان تراکنش برمی‌گردد).
 */
async function judgeLegacy(ctx, rowId, data, existing, layers) {
  if (!isObj(data)) return null;
  const was = existing && !existing.deleted ? (typeof existing.data === 'string' ? JSON.parse(existing.data) : existing.data) : null;
  const a1 = minor(data.debtGiven);
  if (a1 <= 0 || a1 <= (isObj(was) ? minor(was.debtGiven) : 0)) return null;
  const d = require('./perm-core').evaluate(ctx.role, layers, PERM, { amount: a1 });
  if (d.decision === 'allow') return null;
  const { forbidden } = require('../middleware/errors');
  return d.decision === 'deny'
    ? forbidden(`فروشِ قرضی برای نقشِ شما ممنوع است (sales/${rowId})`, 'credit_not_allowed')
    : forbidden(`این مقدار قرض تأییدِ مدیر می‌خواهد؛ از صندوقِ برنامه با تأیید بفرستید (sales/${rowId})`, 'approval_required');
}

/** کلیدهای پولی‌ای که `staff-edit-v1` برای ویرایشِ v1 ِ فاکتور دنبال می‌کند */
function editNeedV1(rowId, was, now) {
  const a1 = minor(now.debtGiven);
  if (a1 <= 0 || a1 <= minor(was.debtGiven)) return null;
  return need(rowId, a1);
}

/** سندِ «درجِ فاکتور» در `staff-approval` — همراه‌ها، نگه‌داشتن و دفترِ پایه یکی (تخفیف و قرض) */
const SALE_INSERT_PERMS = new Set(['sales.discount', PERM, 'sales.price', 'sales.free_line']);   //  قیمت و قلمِ آزاد: ‎lib/price-cap.js‎

module.exports = {
  FLAG, PERM, SALE_INSERT_PERMS, on, onUnits, needV3, needV1, touchesV1, touchesV3, slipOf, judgeV3, markV1, judgeLegacy, editNeedV1, minor,
};
