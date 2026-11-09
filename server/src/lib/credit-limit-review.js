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
 *   - پیگیری‌ها: دلتای مثبتِ `debtGivenMinor` ِ v3 و `update` ِ `debtGiven` ِ v1 همان فاکتورِ نشسته را با ماندهٔ تا لحظهٔ
 *     همان op (`raised`) می‌سنجند؛ ردیفِ «give» ِ تازه (تراکنشِ دیررسیده) فاکتورهای نشستهٔ همان مشتری از زمانِ خودش به بعد و
 *     فاکتورِ `refId` ِ خودش را دوباره می‌سنجد (`late`). بردِ فاکتور = بزرگ‌ترِ «give» های خودش و قرضِ سرش. هنوز یک سابقه
 *     برای هر فاکتور (`once`). ⚠️ راهِ قدیم ویرایشِ قرض را جدا نمی‌کند (ردیفِ کامل) — فقط درجِ تازه و تراکنشِ دیررسیده.
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
/** فاکتورهای نشستهٔ یک دسته از نشانه‌های «تراکنشِ دیررسیده» */
const MAX_LATE = 1000;

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
 * @param asOf Map(شناسهٔ فاکتور ⇒ لحظهٔ دلتا/ویرایشِ قرض) یا null
 */
function overLimit(sales, ledger, shape, asOf = null) {
  const out = [];
  for (const { id, data: s } of sales) {
    if (!candidate(s, shape)) continue;
    const debtorId = s.debtorId;
    const limit = limitOf(ledger.debtors.get(debtorId), shape);
    if (limit === null) continue;
    //  دلتا/ویرایشِ بالابرندهٔ قرض: مانده تا لحظهٔ همان op (پس از فاکتور)، نه فقط تا خودِ فاکتور
    const created = time(s.createdAt);
    const later = asOf && asOf.has(id) ? asOf.get(id) : null;
    const at = later !== null && (created === null || later > created) ? later : created;
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
    //  ردیف‌های «give» ِ خودِ فاکتور یا جمعِ قرضِ سرش، هر کدام بزرگ‌تر (دلتای سر بی ردیفِ تازه هم دیده می‌شود)
    const add = own > 0 || Number.isNaN(own) ? Math.max(own, debt) : debt;
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
 * ردیفِ «give» ِ تازهٔ همین دسته ⇒ نشانه‌ای برای بازبینیِ فاکتورهای **نشستهٔ** همان مشتری که پس از آن ساخته شدند (تراکنشِ
 * دیررسیده: ماندهٔ آن‌ها هنگامِ بررسیِ خودشان این ردیف را نداشت) و فاکتورِ همان `refId` (سرش پیش‌تر رسید).
 * @param txs دادهٔ ردیف‌های تراکنش (`{debtorId, type, createdAt, refId}`) ⇒ [{debtorId, at, refId}]
 */
function triggersOf(txs) {
  const out = [];
  for (const t of Array.isArray(txs) ? txs : []) {
    if (!isObj(t) || t.type !== 'give' || !nonBlank(t.debtorId) || out.length >= MAX_SALES) continue;
    out.push({ debtorId: t.debtorId, at: time(t.createdAt), refId: str(t.refId) });
  }
  return out;
}

/** فاکتورِ نشسته‌ای که این نشانه‌ها ماندهٔ لحظه‌اش را عوض می‌کنند؟ (زمانِ نامعلوم ⇒ آری، بسته‌به‌خطا) */
function lateHit(sale, trig) {
  const at = time(sale.data && sale.data.createdAt);
  return trig.some((t) => t.refId === sale.id
    || (t.debtorId === sale.data.debtorId && (t.at === null || at === null || t.at <= at)));
}

/**
 * @param who {shopId, role, userId, deviceId, ip, path}
 * @param sales [{id, data}] — فاکتورهای تازه یا قرض‌افزودهٔ همین دسته
 * @param load (debtorIds, saleIds) ⇒ ledger
 * @param opts.asOf Map(شناسه ⇒ لحظهٔ دلتای قرض)؛ opts.triggers از `triggersOf`؛ opts.lateOf (debtorIds, refIds) ⇒ [{id, data}] ِ نشسته
 * @returns شمارِ سابقه‌های تازه
 */
async function review(who, sales, shape, load, { asOf = null, triggers = [], lateOf = null } = {}) {
  if (!who || !who.shopId || who.role === 'owner' || !Array.isArray(sales)) return 0;
  const cands = sales.filter((s) => s && typeof s.id === 'string' && candidate(s.data, shape)).slice(0, MAX_SALES);
  const trig = lateOf ? triggers : [];
  if ((!cands.length && !trig.length) || !(await require('./orders').flagOf(who.shopId, FLAG))) return 0;
  const late = new Set();
  if (trig.length) {
    const seen = new Set(cands.map((s) => s.id));
    const rows = await lateOf([...new Set(trig.map((t) => t.debtorId))], [...new Set(trig.map((t) => t.refId).filter(Boolean))]);
    for (const r of rows) {
      if (cands.length >= MAX_SALES) break;
      if (!r || seen.has(r.id) || !candidate(r.data, shape) || !lateHit(r, trig)) continue;
      seen.add(r.id);
      late.add(r.id);
      cands.push(r);
    }
  }
  if (!cands.length) return 0;
  const ledger = await load([...new Set(cands.map((s) => s.data.debtorId))], cands.map((s) => s.id));
  let n = 0;
  for (const x of overLimit(cands, ledger, shape, asOf).slice(0, MAX_EVENTS)) {
    const o = x.over;
    const r = await audit.log({
      shopId: who.shopId, actorType: 'user', userId: who.userId || '', ip: who.ip || '', once: true,
      action: ACTION, targetType: 'sale', targetId: x.id.slice(0, 128),
      detail: {
        problem: 'credit_over_limit', review: true, path: who.path, deviceId: str(who.deviceId).slice(0, 64), role: str(who.role).slice(0, 24),
        invoice: x.data.invoiceNumber ?? null, debtorId: str(x.debtorId).slice(0, 128),
        limitMinor: o.limitMinor, balanceMinor: o.balanceMinor, addMinor: o.addMinor, afterMinor: o.afterMinor, excessMinor: o.excessMinor,
        //  تراکنشِ دیررسیده یا دلتا/ویرایشِ قرض این فاکتور را از سقف گذراند (فاکتور خودش پیش‌تر نشسته بود)
        ...(late.has(x.id) ? { late: true } : {}), ...(asOf && asOf.has(x.id) ? { raised: true } : {}),
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

/** فاکتورهای نشستهٔ مشتری‌های نشانه‌ها (یا همان `refId`) در `sync_rows`/`sync_v3_rows` ِ همین حساب — سقفِ [MAX_LATE] */
function lateRows(table, ctx) {
  return (debtorIds, refIds) => many(
    `SELECT row_id AS id, data FROM ${table}
      WHERE app=$1 AND account_kind=$2 AND account_id=$3 AND table_name='sales' AND deleted_at IS NULL
        AND (data->>'debtorId' = ANY($4::text[]) OR row_id = ANY($5::text[])) LIMIT ${MAX_LATE}`,
    [ctx.app, ctx.accountKind, ctx.accountId, debtorIds, refIds]
  );
}

const applied = (r) => r && (r.status === 'applied' || r.status === 'partial');
const opTable = (op) => op.table ?? op.table_name ?? op.tableName;
const opType = (op) => op.kind ?? op.type ?? op.op_type ?? op.opType;
const hasField = (op, k) => isObj(op.fields) && Object.prototype.hasOwnProperty.call(op.fields, k);

/**
 * v1 (از `credit-debtor.flagV1` پس از push): فاکتورهای نشستهٔ همین دسته (`rows` از `sync_rows`) که op ِ درج داشتند، یا
 * `update` ِ `debtGiven` (عدد یا `$inc`) — این یکی با ماندهٔ تا لحظهٔ همان op (`ts`)؛ و ردیفِ «give» ِ تازه ⇒ فاکتورهای
 * نشستهٔ پس از آن (تراکنشِ دیررسیده). `ops` = همان دستهٔ push (پیش از اعمال؛ داوری فقط از دفترِ نشسته).
 * @param inserted Set ِ شناسهٔ فاکتورهایی که در همین دسته درج شدند
 */
function v1(ctx, deviceId, rows, inserted, { ip = '', ops = [] } = {}) {
  return safely('v1', () => {
    const asOf = new Map();
    const txs = [];
    const { tableOf } = require('./sync-v1');
    for (const op of Array.isArray(ops) ? ops : []) {
      if (!isObj(op)) continue;
      const table = tableOf('shop', opTable(op));
      if (table === 'sales' && opType(op) === 'update' && hasField(op, 'debtGiven') && asOf.size < MAX_SALES) {
        asOf.set(String(op.row_id ?? op.rowId ?? op.id ?? ''), time(op.ts));
      } else if (table === 'transactions' && opType(op) === 'insert' && isObj(op.fields)) txs.push(op.fields);
    }
    return review(
      { shopId: ctx.accountId, role: ctx.role, userId: ctx.userId, deviceId, ip, path: 'v1' },
      rows.filter((r) => inserted.has(String(r.id)) || asOf.has(String(r.id))), 'v1',
      (debtorIds, saleIds) => loadRows('sync_rows', ctx, debtorIds, saleIds),
      { asOf, triggers: triggersOf(txs), lateOf: lateRows('sync_rows', ctx) },
    );
  });
}

/**
 * راهِ قدیم و REST (از `credit-debtor.flagLegacy`): ردیف‌های نشسته؛ فقط درجِ تازه (`version = 1`)، و ردیفِ «give» ِ تازهٔ
 * `transactions` ⇒ فاکتورهای نشستهٔ پس از آن (تراکنشِ دیررسیده).
 */
function legacy({ shopId, role, userId = '', deviceId = '', ip = '', path = 'legacy' }, rows, { txs = [] } = {}) {
  return safely('legacy', async () => {
    const triggers = triggersOf(txs);
    if (!shopId || role === 'owner' || (!rows.some((r) => candidate(r.data, 'v1')) && !triggers.length)) return 0;
    const fresh = rows.length ? new Set((await many(`SELECT id FROM sales WHERE shop_id=$1 AND version=1 AND id = ANY($2::text[])`,
      [shopId, rows.map((r) => r.id)])).map((r) => r.id)) : new Set();
    return review({ shopId, role, userId, deviceId, ip, path }, rows.filter((r) => fresh.has(r.id)), 'v1',
      (debtorIds, saleIds) => loadLegacy(shopId, debtorIds, saleIds),
      {
        triggers,
        lateOf: (debtorIds, refIds) => many(
          `SELECT id, data FROM sales WHERE shop_id=$1 AND deleted=false AND (data->>'debtorId' = ANY($2::text[]) OR id = ANY($3::text[]))
            LIMIT ${MAX_LATE}`, [shopId, debtorIds, refIds]),
      });
  });
}

/**
 * v3 (`routes/sync-v3.js` پس از push): درجِ نشستهٔ فاکتور در همین دسته، دلتای مثبتِ `debtGivenMinor` (ماندهٔ تا دیوارِ HLC ِ
 * همان op) و ردیفِ «give» ِ تازه ⇒ فاکتورهای نشستهٔ پس از آن؛ `ops`/`results` هم‌ترتیب (پیش از `mergeResults`).
 * شکلِ v2 (opِ v1 از درِ v3) با همان `table`/`row_id`/`type`؛ `update` ِ `debtGiven` ِ آن همان دلتاست.
 */
function v3(ctx, deviceId, ops, results, { ip = '' } = {}) {
  return safely('v3', async () => {
    const { SHOP } = require('./tenancy');
    if (!ctx || ctx.app !== SHOP.app || ctx.accountKind !== SHOP.app || !ctx.accountId || ctx.role === 'owner') return 0;
    if (!Array.isArray(ops) || !Array.isArray(results)) return 0;
    const ids = new Set();
    const asOf = new Map();
    const txs = [];
    ops.forEach((op, i) => {
      if (!isObj(op) || !applied(results[i]) || typeof op.row_id !== 'string' || !op.row_id) return;
      const kind = opType(op);
      if (op.table === 'transactions' && kind === 'insert' && isObj(op.fields)) { txs.push(op.fields); return; }
      if (op.table !== 'sales' || ids.size >= MAX_SALES) return;
      if (kind === 'insert') ids.add(op.row_id);
      else if ((kind === 'delta' && Number(op.fields && op.fields.debtGivenMinor) > 0) || (kind === 'update' && hasField(op, 'debtGiven'))) {
        ids.add(op.row_id);
        asOf.set(op.row_id, kind === 'delta' ? (require('./staff-approval').wallOf(op.hlc) ?? null) : time(op.ts));
      }
    });
    const triggers = triggersOf(txs);
    if (!ids.size && !triggers.length) return 0;
    const sales = ids.size ? (await many(
      `SELECT row_id AS id, data FROM sync_v3_rows
        WHERE app=$1 AND account_kind=$2 AND account_id=$3 AND table_name='sales' AND row_id = ANY($4::text[]) AND deleted_at IS NULL`,
      [ctx.app, ctx.accountKind, ctx.accountId, [...ids]]
    )) : [];
    return review({ shopId: ctx.accountId, role: ctx.role, userId: ctx.userId, deviceId, ip, path: 'v3' }, sales, 'v3',
      (debtorIds, saleIds) => loadRows('sync_v3_rows', ctx, debtorIds, saleIds),
      { asOf, triggers, lateOf: lateRows('sync_v3_rows', ctx) });
  });
}

module.exports = { FLAG, ACTION, check, minorOf, limitOf, overLimit, approvedOn, triggersOf, lateHit, v1, v3, legacy };
