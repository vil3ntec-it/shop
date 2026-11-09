'use strict';
/**
 * CRM-03 — سقفِ اعتبارِ مشتری در سرور: «بررسی، نه رد» (پرچمِ خاموشِ `shop.credit_limit_review`).
 *
 * سقفِ اعتبار (`Debtor.creditLimit`) تا امروز فقط روی دستگاه سنجیده می‌شد (`core/credit/CreditGate`، صندوقِ S07 پشتِ
 * `ShopPolicy.creditGate`)؛ برنامهٔ کهنه، تنظیمِ خاموشِ دستگاه یا opِ مستقیم آن را دور می‌زد و سرور خبر نداشت.
 *
 * ⛔ تصمیمِ صاحب‌کار هنوز باز است (STATUS، CRM-03): سرور فروشِ بیش از سقفِ بی تأیید را **رد کند** (فروشِ آفلاینِ
 *    انجام‌شده برمی‌گردد) یا فقط **به بررسی بفرستد**. این پرونده فقط راهِ دوم است و هرگز رد نمی‌کند، هیچ ردیفی از
 *    دفتر نمی‌نویسد و هیچ پاسخی را عوض نمی‌کند:
 *   - پس از نشستنِ push (v1: `credit-debtor.flagV1`، بی دست زدن به `sync-v1`؛ راهِ قدیم و REST: `credit-debtor.flagLegacy`؛
 *     v3: `routes/sync-v3.js`) برای هر فاکتورِ **تازهٔ** قرض‌دار، ماندهٔ دفترِ **سرور** ِ همان مشتری تا لحظهٔ همان فاکتور
 *     + بردِ همین فاکتور با سقف سنجیده می‌شود (همان `CreditGate.check`).
 *   - بیش از سقف و بی تأییدِ دستگاه (ردِ `credit_over_limit` ِ همان فاکتور با نام و دلیل) ⇒ سابقهٔ
 *     `sale.credit_over_limit` برای صاحب (یک بار برای هر فاکتور، `audit.log` با `once`) در «سابقهٔ عملیاتِ دکان»
 *     (`GET /api/shop/audit`، فقط `audit.view`).
 *   - تأییدِ دستگاه امضا ندارد (PIN روی همان دستگاه)، پس «با تأیید» یعنی ادعای دستگاه — نه برگهٔ سنجیده.
 *   - صاحبِ دکان سنجیده نمی‌شود (خودش بررسی‌کننده است).
 *   - ماندهٔ سرور = همان `DebtBook.creditCheck`: «give» مثبت، بقیه منفی؛ همهٔ تراکنش‌های نشستهٔ آن مشتری جز ردیف‌های
 *     همین فاکتور و جز آنچه پس از آن ساخته شد (`createdAt`؛ دستهٔ آفلاینِ چند فاکتوره فاکتورِ پیشین را به‌خاطرِ
 *     بعدی نشان نمی‌زند). ردیفی که هنوز نرسیده دیده نمی‌شود — بررسی است، نه داوری.
 *   - عددِ بیرون از ±(2^53−1) یا سقفِ منفی «بیش از سقف» است، نه «آزاد» (همان بسته‌به‌خطای `CreditGate`).
 *   - پرچمِ خاموش (پیش‌فرض)، صاحب یا دسته‌ای بی فاکتورِ قرض‌دار ⇒ هیچ پرسشی. شکستِ بررسی هرگز push را نمی‌خواباند.
 * ⛔ فقط ردیف‌های همین دکان (`account_id`/`shop_id` ِ عضویت). بی دادهٔ شخصی در سابقه (فقط شناسهٔ مشتری، نه نام و شماره).
 */
const { many } = require('../db');
const audit = require('./audit');

const FLAG = 'shop.credit_limit_review';
const ACTION = 'sale.credit_over_limit';
const APPROVAL_TYPE = 'credit_over_limit';
const MAX_SALES = 200;
const MAX_EVENTS = 20;

const isObj = (v) => v !== null && typeof v === 'object' && !Array.isArray(v);
const str = (v) => (typeof v === 'string' ? v : v === null || v === undefined ? '' : String(v));
const nonBlank = (v) => typeof v === 'string' && v.trim() !== '';

/* ---------------------------------------------------------------- قاعده */

/**
 * همتای دقیقِ `CreditGate.check` (`core/credit/CreditGate.kt`): همه به پول (Minor)، بی گردکردن.
 * @param limitMinor سقف؛ null ⇒ بی سقف
 * @returns null اگر از سقف نمی‌گذرد، وگرنه {limitMinor, balanceMinor, addMinor, afterMinor, excessMinor} (عددِ ناامن ⇒ دو آخری null)
 */
function check(limitMinor, balanceMinor, addMinor) {
  if (limitMinor === null || limitMinor === undefined) return null;
  //  بردِ نادرست (NaN) ناامن است، نه «بی برد» — Kotlin آن را هرگز نمی‌گیرد
  if (!Number.isNaN(addMinor) && !(addMinor > 0)) return null;
  if (!Number.isSafeInteger(limitMinor) || limitMinor < 0 || !Number.isSafeInteger(balanceMinor) || !Number.isSafeInteger(addMinor)) {
    return { limitMinor: Number.isFinite(limitMinor) ? limitMinor : null, balanceMinor: Number.isFinite(balanceMinor) ? balanceMinor : null,
      addMinor: Number.isFinite(addMinor) ? addMinor : null, afterMinor: null, excessMinor: null };
  }
  //  هر دو در ±(2^53−1)‌اند؛ جمع تا ۲^۵۴ است و عددِ زوجِ آن بازه دقیق می‌ماند — و مقایسه با سقفِ امن درست است
  const after = balanceMinor + addMinor;
  return after > limitMinor ? { limitMinor, balanceMinor, addMinor, afterMinor: after, excessMinor: after - limitMinor } : null;
}

/** عدد ⇒ Minor؛ `afn` = افغانیِ اعشاری (v1/راهِ قدیم). نبودن ⇒ ۰؛ نادرست ⇒ NaN (ناامن، بسته‌به‌خطا) */
function minorOf(v, afn) {
  if (v === null || v === undefined || v === '') return 0;
  const n = typeof v === 'number' ? v : typeof v === 'string' ? Number(v) : NaN;
  if (!Number.isFinite(n)) return NaN;
  return Math.round(afn ? n * 100 : n);
}

/** سقفِ مشتری ⇒ Minor یا null (بی سقف) */
function limitOf(d, shape) {
  if (!isObj(d)) return null;
  if (shape === 'v3' && d.creditLimitMinor !== undefined && d.creditLimitMinor !== null) return minorOf(d.creditLimitMinor, false);
  if (d.creditLimit === undefined || d.creditLimit === null) return null;
  return minorOf(d.creditLimit, true);
}

const amountOf = (t, shape) => (shape === 'v3' && t.amountMinor !== undefined ? minorOf(t.amountMinor, false) : minorOf(t.amount, true));
const debtOf = (s, shape) => (shape === 'v3' ? minorOf(s.debtGivenMinor, false) : minorOf(s.debtGiven, true));
const time = (v) => (Number.isFinite(Number(v)) && v !== null && v !== '' ? Number(v) : null);

/** فاکتورِ قرض‌دارِ نامزد؟ (بی پرسش — برای «پرچم را بخوانیم؟») */
function candidate(s, shape) {
  if (!isObj(s) || s.status === 'cancelled' || !nonBlank(s.debtorId)) return false;
  const d = debtOf(s, shape);
  return d > 0 || Number.isNaN(d);
}

/** تأییدِ دستگاه برای همین فاکتور: ردِ `credit_over_limit` با نامِ تأییدکننده و دلیل */
function approvedOn(audits, saleId) {
  return audits.some((a) => isObj(a) && a.type === APPROVAL_TYPE && str(a.refId) === saleId && nonBlank(a.approvedBy) && nonBlank(a.reason));
}

/**
 * داوریِ خالص: فاکتورها + دفترِ خوانده‌شده ⇒ فاکتورهای بیش از سقفِ بی تأیید.
 * @param sales [{id, data}]؛ ledger {debtors: Map(id ⇒ data), txs: [data], audits: [data]}
 */
function overLimit(sales, ledger, shape) {
  const out = [];
  for (const { id, data: s } of sales) {
    if (!candidate(s, shape)) continue;
    const debtorId = s.debtorId;
    const limit = limitOf(ledger.debtors.get(debtorId), shape);
    if (limit === null) continue;
    const at = time(s.createdAt);
    let balance = 0;
    let own = 0;
    for (const t of ledger.txs) {
      if (!isObj(t) || t.debtorId !== debtorId) continue;
      const a = amountOf(t, shape);
      if (str(t.refId) === id) { if (t.type === 'give') own += a; continue; }
      const tt = time(t.createdAt);
      if (at !== null && tt !== null && tt > at) continue;
      balance += t.type === 'give' ? a : -a;
    }
    const debt = debtOf(s, shape);
    const add = own > 0 || Number.isNaN(own) ? own : debt;
    const over = check(limit, balance, add);
    if (!over || approvedOn(ledger.audits, id)) continue;
    out.push({ id, data: s, debtorId, over });
  }
  return out;
}

/* --------------------------------------------------------------- خواندن */

/**
 * دفترِ v1 (`sync_rows`) یا v3 (`sync_v3_rows`) ِ همین حساب — سه پرسشِ جدا، هر کدام روی پیشوندِ کلیدِ
 * (حساب، جدول)، نه یک OR که کلِ ردیف‌های دکان را بگردد.
 */
async function loadRows(table, ctx, debtorIds, saleIds) {
  const base = `SELECT row_id, data FROM ${table} WHERE app=$1 AND account_kind=$2 AND account_id=$3 AND deleted_at IS NULL`;
  const acc = [ctx.app, ctx.accountKind, ctx.accountId];
  const [debtors, txs, audits] = await Promise.all([
    many(`${base} AND table_name='debtors' AND row_id = ANY($4::text[])`, [...acc, debtorIds]),
    many(`${base} AND table_name='transactions' AND data->>'debtorId' = ANY($4::text[])`, [...acc, debtorIds]),
    many(`${base} AND table_name='auditLog' AND data->>'type' = $5 AND data->>'refId' = ANY($4::text[])`, [...acc, saleIds, APPROVAL_TYPE]),
  ]);
  return { debtors: new Map(debtors.map((r) => [r.row_id, r.data])), txs: txs.map((r) => r.data), audits: audits.map((r) => r.data) };
}

/** دفترِ راهِ قدیم (جدول‌های جدای `debtors`، `payments`، `shop_audit_log`) */
async function loadLegacy(shopId, debtorIds, saleIds) {
  const [debtors, txs, audits] = await Promise.all([
    many(`SELECT id, data FROM debtors WHERE shop_id=$1 AND deleted=false AND id = ANY($2::text[])`, [shopId, debtorIds]),
    many(`SELECT data FROM payments WHERE shop_id=$1 AND deleted=false AND data->>'debtorId' = ANY($2::text[])`, [shopId, debtorIds]),
    many(`SELECT data FROM shop_audit_log WHERE shop_id=$1 AND deleted=false AND data->>'type' = $3 AND data->>'refId' = ANY($2::text[])`,
      [shopId, saleIds, APPROVAL_TYPE]),
  ]);
  return { debtors: new Map(debtors.map((r) => [r.id, r.data])), txs: txs.map((r) => r.data), audits: audits.map((r) => r.data) };
}

/* ---------------------------------------------------------------- اجرا */

/**
 * @param who {shopId, role, userId, deviceId, ip, path}
 * @param sales [{id, data}] — فقط فاکتورهای تازهٔ همین دسته
 * @param load (debtorIds, saleIds) ⇒ ledger
 * @returns شمارِ سابقه‌های تازه
 */
async function review(who, sales, shape, load) {
  if (!who || !who.shopId || who.role === 'owner' || !Array.isArray(sales)) return 0;
  const cands = sales.filter((s) => s && typeof s.id === 'string' && candidate(s.data, shape)).slice(0, MAX_SALES);
  if (!cands.length || !(await require('./orders').flagOf(who.shopId, FLAG))) return 0;
  const ledger = await load([...new Set(cands.map((s) => s.data.debtorId))], cands.map((s) => s.id));
  let n = 0;
  for (const x of overLimit(cands, ledger, shape).slice(0, MAX_EVENTS)) {
    const o = x.over;
    const r = await audit.log({
      shopId: who.shopId, actorType: 'user', userId: who.userId || '', ip: who.ip || '', once: true,
      action: ACTION, targetType: 'sale', targetId: x.id.slice(0, 128),
      detail: {
        problem: 'credit_over_limit', review: true, path: who.path, deviceId: str(who.deviceId).slice(0, 64), role: str(who.role).slice(0, 24),
        invoice: x.data.invoiceNumber ?? null, debtorId: str(x.debtorId).slice(0, 128),
        limitMinor: o.limitMinor, balanceMinor: o.balanceMinor, addMinor: o.addMinor, afterMinor: o.afterMinor, excessMinor: o.excessMinor,
        text: 'فروشِ نسیه بیش از سقفِ اعتبارِ مشتری، بی تأییدِ مدیر روی دستگاه — فروش ثبت شد؛ بررسی کنید',
      },
    });
    if (r) n++;
  }
  return n;
}

/** شکستِ بررسی هرگز push را نمی‌خواباند */
async function safely(label, fn) {
  try { return await fn(); } catch (err) {
    console.error(`[credit-limit-review] بررسیِ ${label} نشد:`, err.message);
    return 0;
  }
}

/**
 * v1 (از `credit-debtor.flagV1` پس از push): فاکتورهای نشستهٔ همین دسته (`rows` از `sync_rows`) که op ِ درج داشتند.
 * @param inserted Set ِ شناسهٔ فاکتورهایی که در همین دسته درج شدند
 */
function v1(ctx, deviceId, rows, inserted, { ip = '' } = {}) {
  return safely('v1', () => review(
    { shopId: ctx.accountId, role: ctx.role, userId: ctx.userId, deviceId, ip, path: 'v1' },
    rows.filter((r) => inserted.has(String(r.id))), 'v1',
    (debtorIds, saleIds) => loadRows('sync_rows', ctx, debtorIds, saleIds),
  ));
}

/** راهِ قدیم و REST (از `credit-debtor.flagLegacy`): ردیف‌های نشسته؛ فقط درجِ تازه (`version = 1`) */
function legacy({ shopId, role, userId = '', deviceId = '', ip = '', path = 'legacy' }, rows) {
  return safely('legacy', async () => {
    if (!shopId || role === 'owner' || !rows.some((r) => candidate(r.data, 'v1'))) return 0;
    const fresh = new Set((await many(`SELECT id FROM sales WHERE shop_id=$1 AND version=1 AND id = ANY($2::text[])`,
      [shopId, rows.map((r) => r.id)])).map((r) => r.id));
    return review({ shopId, role, userId, deviceId, ip, path }, rows.filter((r) => fresh.has(r.id)), 'v1',
      (debtorIds, saleIds) => loadLegacy(shopId, debtorIds, saleIds));
  });
}

/**
 * v3 (`routes/sync-v3.js` پس از push): درجِ نشستهٔ فاکتور در همین دسته؛ `ops`/`results` هم‌ترتیب (پیش از `mergeResults`).
 * شکلِ v2 (opِ v1 از درِ v3) با همان `table`/`row_id`/`type`.
 */
function v3(ctx, deviceId, ops, results, { ip = '' } = {}) {
  return safely('v3', async () => {
    const { SHOP } = require('./tenancy');
    if (!ctx || ctx.app !== SHOP.app || ctx.accountKind !== SHOP.app || !ctx.accountId || ctx.role === 'owner') return 0;
    if (!Array.isArray(ops) || !Array.isArray(results)) return 0;
    const ids = new Set();
    ops.forEach((op, i) => {
      const r = results[i];
      if (!isObj(op) || op.table !== 'sales' || !(op.kind === 'insert' || op.type === 'insert')) return;
      if (!r || (r.status !== 'applied' && r.status !== 'partial')) return;
      if (typeof op.row_id === 'string' && op.row_id && ids.size < MAX_SALES) ids.add(op.row_id);
    });
    if (!ids.size) return 0;
    const sales = (await many(
      `SELECT row_id AS id, data FROM sync_v3_rows
        WHERE app=$1 AND account_kind=$2 AND account_id=$3 AND table_name='sales' AND row_id = ANY($4::text[]) AND deleted_at IS NULL`,
      [ctx.app, ctx.accountKind, ctx.accountId, [...ids]]
    ));
    return review({ shopId: ctx.accountId, role: ctx.role, userId: ctx.userId, deviceId, ip, path: 'v3' }, sales, 'v3',
      (debtorIds, saleIds) => loadRows('sync_v3_rows', ctx, debtorIds, saleIds));
  });
}

module.exports = { FLAG, ACTION, check, minorOf, limitOf, overLimit, approvedOn, v1, v3, legacy };
