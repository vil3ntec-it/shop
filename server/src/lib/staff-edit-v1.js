'use strict';
/**
 * ⛔ نقش و سقف روی **ویرایشِ** سندِ پولیِ Sync v1 — STF-03 (نگهبانِ `staff-approval.v1Guard`).
 *
 * `authorizeOp` ِ v1 (`sync-v1.js`، مشترک با پمپ، دست نمی‌خورد) فقط `Number(discount) > 0` را می‌بیند و
 * نگهبان فقط **درجِ** فاکتور و برگشت را می‌سنجید؛ ولی v1 هر ستون را با `update` یا `{"$inc": n}` عوض
 * می‌کند. پس (پیش از این پرونده):
 *   • شاگردِ بی تخفیف با `discount: {"$inc": 70}` روی فاکتورِ ثبت‌شده تخفیف می‌داد (Number(شیء) = NaN)؛
 *   • مدیرِ با سقف یا «ممنوع» فاکتور را بی تخفیف ثبت می‌کرد و بعد تخفیف را بالا یا جمع را پایین می‌برد؛
 *   • سقفِ مبلغِ برگشت (تجمعیِ فاکتور) با بالا بردنِ `amount` ِ برگشتِ ثبت‌شده دور زده می‌شد.
 * این‌جا هر `update` ِ v1 که تخفیف یا درصدِ تخفیفِ فاکتور، یا مبلغِ برگشت را **بالا می‌برد** همان
 * نیازِ درج را می‌گیرد، با مقدارِ پس از اعمال (ردیفِ v1 ِ سرور + opهای پیشینِ همین دسته که رد نشدند).
 * کاهش و ویرایشِ بی‌پول آزاد است. ویرایش برگه نمی‌برد (هیچ برنامه‌ای با ویرایش برگه نمی‌فرستد):
 * «نیازمند تأیید» ⇒ approval_required؛ «ممنوع» ⇒ همان کدِ درج. فقط همان op رد می‌شود (بی همراه) و یک
 * ردیف در صندوقِ تعارض (`detail.edit`). صاحبِ دکان هرگز به این‌جا نمی‌رسد (`v1Guard`). Sync v3 نیازی
 * ندارد: سندِ v3 فقط‌افزودنی است و تخفیف، جمع و مبلغِ برگشت شمارنده نیستند.
 * STF-03 (پرچمِ خاموشِ `shop.credit_cap`): بالا بردنِ `debtGiven` ِ فاکتور هم همان سقفِ `sales.credit` ِ درج را
 * می‌گیرد (مقدارِ پس از اعمال)؛ در v3 قرض شمارنده است و دلتایش در `credit-cap.needV3` سنجیده می‌شود.
 */
const core = require('./perm-core');
const { many } = require('../db');

/** فقط همین کلیدها پیگیری می‌شوند — هرگز شیء از کلیدهای درخواست ساخته نمی‌شود */
const KEYS = ['discount', 'total', 'amount', 'saleId', 'status', 'debtGiven'];

const isObj = (v) => v !== null && typeof v === 'object' && !Array.isArray(v);
const has = (o, k) => isObj(o) && Object.prototype.hasOwnProperty.call(o, k);
const isInc = (v) => isObj(v) && Object.keys(v).length === 1 && Number.isFinite(v.$inc);
const minor = (v) => { const n = Math.round(Number(v) * 100); return Number.isFinite(n) ? n : 0; };

/**
 * ویرایشِ پولی؟ (بی پرس‌وجو — برای «چیزی برای سنجیدن هست؟» ِ نگهبان)
 * @param credit  STF-03: پرچمِ `shop.credit_cap` روشن ⇒ `debtGiven` ِ فاکتور هم (‎lib/credit-cap.js‎)
 */
function isMoneyEdit(op, credit = false) {
  if (!op || op.type !== 'update' || !isObj(op.fields)) return false;
  if (op.table === 'sales') return has(op.fields, 'discount') || has(op.fields, 'total') || (credit && has(op.fields, 'debtGiven'));
  return op.table === 'returns' && has(op.fields, 'amount');
}

/** همان رفتارِ v1: عدد جایگزین، `$inc` جمع؛ درج از صفر */
function applied(cur, op) {
  const next = op.type === 'insert' ? {} : { ...cur };
  for (const k of KEYS) {
    if (!has(op.fields, k)) continue;
    const v = op.fields[k];
    next[k] = isInc(v) ? (Number(next[k]) || 0) + v.$inc : v;
  }
  return next;
}

function pick(data) {
  const o = {};
  for (const k of KEYS) if (has(data, k)) o[k] = data[k];
  return o;
}

/**
 * `authorizeOp` ِ v1 این opِ فروش را حتماً با نقش رد می‌کند؟ همان دو شرطِ خودش (با همان
 * `can`) و همان کد؛ حذف راهِ «حذفِ مالِ خودت» را دارد که به دفتر بسته است، پس این‌جا نه.
 */
function v1RoleReason(role, op) {
  if (op.table !== 'sales' || op.type === 'delete' || !isObj(op.fields)) return null;
  const { can } = require('./permissions');
  if (Number(op.fields.discount) > 0 && !can(role, 'sales.discount')) return 'discount_not_allowed';
  if (op.fields.status === 'cancelled' && !can(role, 'sales.cancel')) return 'cancel_not_allowed';
  return null;
}

/**
 * نیازهای ویرایشِ یک op از «پیش» به «پس» (به ترتیب؛ نخستینِ ردشده تصمیم است)، یا [] اگر چیزی بالا نرفت.
 * فاکتور: تخفیف، سپس (STF-03، با `credit`) قرضِ همان فاکتور (`debtGiven`).
 */
async function needsOf(ctx, op, was, now, db, credit) {
  if (op.table === 'sales') {
    const out = [];
    const d0 = minor(was.discount);
    const d1 = minor(now.discount);
    const bp0 = core.discountBp(d0, minor(was.total));
    const bp1 = core.discountBp(d1, minor(now.total));
    if (d1 > 0 && (d1 > d0 || bp1 > bp0)) {
      out.push({ perm: 'sales.discount', doc: `sales/${op.row_id}`, req: { pct: bp1 / 100, amount: d1 }, pct_bp: bp1, amount: d1 });
    }
    const c = credit ? require('./credit-cap').editNeedV1(op.row_id, was, now) : null;
    if (c) out.push(c);
    return out;
  }
  const one = await returnNeedOf(ctx, op, was, now, db);
  return one ? [one] : [];
}

async function returnNeedOf(ctx, op, was, now, db) {
  const a1 = minor(now.amount);
  if (a1 <= minor(was.amount) || now.status === 'cancelled') return null;
  const need = { perm: 'returns.create', doc: `returns/${op.row_id}`, req: { amount: a1 }, amount: a1 };
  const sale = typeof now.saleId === 'string' ? now.saleId : '';
  if (!sale) return need;
  //  سقفِ تجمعیِ فاکتور (همان `cumulativeNeed`): ثبت‌شده‌ها بی سهمِ پیشینِ همین ردیف، به‌علاوهٔ مقدارِ تازه
  const counted = db && !db.deleted && db.saleId === sale && db.status !== 'cancelled' ? minor(db.amount) : 0;
  const total = (await require('./staff-approval').returnedTotal(null, ctx, sale)) - counted + a1;
  return { ...need, req: { amount: total }, cumulative: total };
}

/**
 * ویرایش‌های پولیِ ردشدهٔ یک دسته (به ترتیب؛ opِ ردشده پایهٔ opِ بعدی نمی‌شود).
 * @param live [{i, op, need}] — opهای زندهٔ نگهبان (بی تکراری و بی ردشده)
 * @returns {Promise<Array<{x, out: {reason, box, detail}}>>}
 */
async function judgeEdits(ctx, live, deviceId, { credit = false } = {}) {
  const edits = live.filter((x) => !x.need && isMoneyEdit(x.op, credit));
  if (!edits.length) return [];
  const keyOf = (op) => `${op.table}/${op.row_id}`;
  const keys = new Set(edits.map((x) => keyOf(x.op)));
  const state = new Map();
  const dbRows = new Map();
  for (const r of await many(
    `SELECT table_name, row_id, data, deleted_at FROM sync_rows
      WHERE app='shop' AND account_kind='shop' AND account_id=$1 AND table_name IN ('sales','returns') AND row_id = ANY($2::text[])`,
    [ctx.accountId, [...new Set(edits.map((x) => String(x.op.row_id)))]]
  )) {
    const k = `${r.table_name}/${r.row_id}`;
    if (!keys.has(k)) continue;
    const data = pick(typeof r.data === 'string' ? JSON.parse(r.data) : r.data);
    state.set(k, data);
    dbRows.set(k, { ...data, deleted: r.deleted_at !== null && r.deleted_at !== undefined });
  }
  const sa = require('./staff-approval');
  const out = [];
  for (const x of live) {
    const k = keyOf(x.op);
    if (!keys.has(k) || (x.op.type !== 'insert' && x.op.type !== 'update') || !isObj(x.op.fields)) continue;
    const was = state.get(k) || {};
    const now = applied(was, x.op);
    const needs = !x.need && isMoneyEdit(x.op, credit) ? await needsOf(ctx, x.op, was, now, dbRows.get(k), credit) : [];
    let verdict = null;
    for (const need of needs) {
      if (core.defaultMode(ctx.role, need.perm) === 'deny') {
        //  نقشی که این کار را ندارد (شاگرد): v1 برگه نمی‌پذیرد — همان کدِ درج
        verdict = { reason: sa.DENY_CODE[need.perm], box: 'perm_denied',
          detail: { perm: need.perm, decision: 'deny', requester: ctx.userId || '', role: ctx.role || '', path: 'v1' } };
      } else {
        verdict = await sa.judge(null, ctx, need, null, { opId: x.op.op_id, deviceId, path: 'v1' });
      }
      if (verdict) break;
    }
    if (verdict) out.push({ x, out: { ...verdict, detail: { ...verdict.detail, edit: true } } });
    else state.set(k, now);
  }
  return out;
}

module.exports = { isMoneyEdit, v1RoleReason, judgeEdits };
