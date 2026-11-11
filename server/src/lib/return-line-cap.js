'use strict';
/**
 * ⛔ هیچ‌کس — صاحبِ دکان هم — بیش از آنچه روی همان قلمِ فاکتور فروخته شد برنمی‌گرداند؛ نه مقدار، نه پول (DATA-04،
 * سفرِ JB5 ِ `docs/JOURNEYS-fa.md`). این درستی است، نه اجازه: هیچ نقش و هیچ برگه‌ای از آن نمی‌گذرد.
 *
 * ── اشکال ───────────────────────────────────────────────────────────────
 * دو دستگاهِ صاحب، هر دو بی اینترنت، همان کالای همان فاکتور را کامل برمی‌گرداندند. هر دستگاه با شمارندهٔ خودش
 * (`returnedQty`) «جا» می‌دید و سرور هر دو را می‌نشاند ⇒ پولِ برگشت و موجودی دو بار. `shop.returns.sale_cap`
 * (`return-sale-cap.js`) فقط کارمند و فقط جمعِ فاکتور را می‌سنجید، و `staff-approval.v1Guard` صاحب را اصلاً نمی‌دید.
 *
 * ── حالا ────────────────────────────────────────────────────────────────
 * برگشته‌ی هر قلم (مقدار و پول) از خودِ دفتر خوانده می‌شود — ردیف‌های زندهٔ `returns` با همان `saleItemId` از هر سه دفتر
 * (v3 `sync_v3_rows`، v1 `sync_rows`، راهِ قدیم/REST `sale_returns`)، هر شناسه یک بار — فقط با شناسهٔ دکانِ همان
 * درخواست، **درونِ همان تراکنش و زیرِ همان قفلِ** `staff-returns:<دکان>` (`staff-approval.returnsLockKey`) که برگشت
 * می‌نشیند؛ مهاجرتِ ۳۰۵ نمایهٔ همین خواندن است. دفترِ شمارندهٔ دوم نیست، چون هر نسخهٔ دوم از دفتر جدا می‌افتد (پلِ v1⇄v3
 * که همان ردیف را در هر دو می‌گذارد، حذف و لغوِ برگشت، بازیابی، نمونهٔ کهنهٔ سرور هنگامِ استقرار).
 *   v3/v2  `staff-approval.judgeGroup` — پیش از نقش و پولِ فاکتور، برای همه (`sync-v3.push` قفل را می‌گیرد)
 *   v1     نگهبانِ درونِ تراکنشِ push (`guard` ِ `sync-v1.push`، از `routes/sync-v1.js`؛ `lib/sync-v1.js` دست نمی‌خورد،
 *          پمپ همان `guard` ِ پیشین را می‌گیرد): نخستین `check` کلِ دسته را یک‌جا داوری می‌کند
 *   REST   `lib/sync.js` (`/api/data/returns`، `/api/data/sales/full` و راهِ قدیم): همه یا هیچ ⇒ ۴۰۹
 * بیش از ماندهٔ قلم ⇒ `return_exceeds_sold`، هرگز نمی‌نشیند؛ همراه‌های همان ثبت (حرکتِ انبار، شمارندهٔ قلم، رسید، سابقه)
 * `tx_rejected` (v3: همهٔ واحد)؛ صندوقِ تعارضِ `perm_denied` با همهٔ opها (`conflict-payload`) برای صاحب. همان ردیف
 * دوباره — op ِ تکراری، یا همان شناسه با op ِ تازه — یک بار شمرده می‌شود.
 *
 * ماندهٔ قلم همان `ReturnCap` ِ core است (`core/.../money/ReturnCap.kt`؛ بردار: `license/test-vectors/return-cap-v1.json`):
 *   مقدار  فروخته − برگشته
 *   پول    round(جمعِ قلم × سهمِ پرداخت (+ سهمِ مالیاتِ فاکتور، POS-37) × ۱۰۰) یک‌صدم − پس‌داده — دقیق تا پول (O-22 ب ِ صاحب‌کار:
 *          آخرین برگشت دقیقاً باقی‌مانده)، به‌اضافهٔ نیم افغانی برای هر ردیف (برنامهٔ پیش از #586 هر برگشت را جدا گرد می‌کرد و
 *          پیش از O-22 ب نیمه را رو به بالا) و گردِ نقدیِ مثبتِ فاکتور (`roundingMinor`، PAY-09) — مالیات و گرد فقط تا آنجا
 *          که در نهاییِ فاکتور آمده‌اند (`extrasOf`، SR48-1/2)
 * فاکتورِ لغوشده چیزی برای برگشت ندارد (لغو همه را برگرداند). برگشتِ بی قلم (بی رسید)، و قلمی که سرور ندارد (نه در
 * دفتر، نه در همین دسته) سنجیده نمی‌شود. ردیفِ برگشتی که در یکی از دفترها حذف یا لغو شده شمرده نمی‌شود.
 *
 * پرچمِ `shop.returns.line_cap` فقط خاموش‌کنِ اضطراری است: ⛔ پیش‌فرض **روشن**؛ تنها `false` ِ صریح (دکان یا all) خاموش
 * می‌کند — برای دکانی که برنامهٔ کهنه‌اش شکلِ ناشناخته‌ای از قلم دارد، بی استقرارِ تازه. آزمون: `test/return-line-cap.test.js`.
 */
const { query, now } = require('../db');

const FLAG = 'shop.returns.line_cap';
const REASON = 'return_exceeds_sold';
/** گردِ قدیمِ هر ردیفِ برگشت (برنامهٔ پیش از #586 و پیش از O-22 ب): نیم افغانی، به پول */
const TOL_PER_ROW = 50;
const MAX_LINES = 50;
const APP = 'shop';
const ACC = `app='shop' AND account_kind='shop' AND account_id=$1`;

const isObj = (v) => v !== null && typeof v === 'object' && !Array.isArray(v);
const str = (v) => (typeof v === 'string' ? v : '');
const num = (v) => {
  const n = typeof v === 'string' && v.trim() !== '' ? Number(v) : v;
  return typeof n === 'number' && Number.isFinite(n) ? n : null;
};
const int = (v) => { const n = num(v); return n === null ? 0 : Math.round(n); };
const fieldsOf = (op) => (isObj(op && op.fields) ? op.fields : {});
const sum = (list, k) => list.reduce((s, x) => s + x[k], 0);
const isShop = (ctx) => !!ctx && ctx.app === APP && ctx.accountKind === APP && !!ctx.accountId;

// ---------- حساب (هم‌زادِ `ReturnCap.kt`) ----------

/** مقدارِ دفترِ v1 (اعشاری) ⇒ هزارم */
const milli = (q) => { const n = num(q); return n === null ? 0 : Math.round(n * 1000); };
/** پولِ دفترِ v1 (افغانیِ اعشاری) ⇒ یک‌صدم */
const minor = (a) => { const n = num(a); return n === null ? 0 : Math.round(n * 100); };

/** سقفِ پولِ یک قلم به یک‌صدم: round(جمعِ قلم × سهمِ پرداخت × ۱۰۰)، دقیق تا پول (O-22 ب)؛ نامعتبر یا منفی ⇒ ۰ */
function capMinor(lineTotal, paidRatio) {
  if (!Number.isFinite(lineTotal) || !Number.isFinite(paidRatio)) return 0;
  return Math.max(0, Math.round(lineTotal * paidRatio * 100));
}

/**
 * همان `SalesEngine.paidRatio` ِ برنامه. POS-37: مالیاتِ فاکتور (`taxMinor`، همان واحدِ `total`) هم سهمی از هر قلم است ⇒
 * سهم بیش از ۱؛ فاکتورِ بی مالیات بیت‌به‌بیت همان.
 */
function paidRatio(total, discount, tax = 0) {
  const base = total > 0 && discount > 0 ? Math.min(1, Math.max(0, (total - discount) / total)) : 1;
  return total > 0 && tax > 0 ? base + tax / total : base;
}

/**
 * ⛔ SR48-1/2: مالیات (`taxMinor`) و گردِ نقدیِ مثبت (`roundingMinor`) ادعای خودِ دستگاه‌اند ⇒ فقط تا آنجا که در نهاییِ
 * خودِ فاکتور آمده‌اند (مشتری پرداخته) سقفِ پول را بالا می‌برند: جا = نهایی − بیشینهٔ (۰، جمع − تخفیف)؛ گرد تا
 * `sale-caps.ROUND_MAX_MINOR` و تا همان جا، مالیات تا باقیِ جا. بی نهایی ⇒ بی مالیات و فقط گردِ تا سقف. فاکتورِ درستِ
 * برنامه (نهایی = جمع − تخفیف + مالیات + گرد) بیت‌به‌بیت همان. به پول.
 */
function extrasOf(s, v3) {
  const m = v3 ? int : minor;
  const round = Math.min(require('./sale-caps').ROUND_MAX_MINOR, Math.max(0, int(s.roundingMinor)));
  const tax = Math.max(0, int(s.taxMinor));
  const fin = v3 ? s.finalTotalMinor : s.finalTotal;
  if (num(fin) === null) return { taxMinor: 0, roundMinor: round };
  const room = Math.max(0, m(fin) - Math.max(0, m(v3 ? s.totalMinor : s.total) - m(v3 ? s.discountMinor : s.discount)));
  const r = Math.min(round, room);
  return { taxMinor: Math.min(tax, room - r), roundMinor: r };
}

/** prior: [{qtyMilli, amountMinor}] */
function state(soldMilli, cap, prior, counterMilli = 0) {
  const returned = Math.max(counterMilli, sum(prior, 'qtyMilli'));
  const paid = sum(prior, 'amountMinor');
  return {
    soldMilli, capMinor: cap, returnedMilli: returned, returnedMinor: paid,
    leftMilli: Math.max(0, soldMilli - returned), leftMinor: Math.max(0, cap - paid),
  };
}

/** @returns {'ok'|'qty'|'amount'} */
function check(soldMilli, cap, prior, req, { tolMinorPerRow = 0, counterMilli = 0, extraMinor = 0 } = {}) {
  const s = state(soldMilli, cap, prior, counterMilli);
  if (req.qtyMilli > s.leftMilli) return 'qty';
  const room = cap + Math.max(0, extraMinor) + Math.max(0, tolMinorPerRow) * (prior.length + 1) - s.returnedMinor;
  return req.amountMinor > room ? 'amount' : 'ok';
}

// ---------- دفتر ----------

/** خاموش‌کنِ اضطراری: فقط `false` ِ صریح (دکان مقدم بر all) */
async function enabled(c, shopId) {
  const rows = (await c.query(`SELECT scope, value FROM live_config WHERE key=$1 AND scope IN ('all', $2)`, [FLAG, String(shopId)])).rows;
  const decode = (t) => { try { return JSON.parse(t); } catch { return null; } };
  let v;
  for (const r of rows) if (r.scope === 'all') v = decode(r.value);
  for (const r of rows) if (r.scope !== 'all') v = decode(r.value);
  return v !== false;
}

/** ردیفِ برگشت با نام‌های راهِ خودش (v3/v2: `…Milli`/`…Minor`؛ v1 و REST: افغانی و مقدارِ اعشاری) */
function rowOf(op, path) {
  const f = fieldsOf(op);
  const v3 = path === 'v3' || path === 'v2';
  return {
    id: str(op && op.row_id), sale: str(f.saleId), item: str(f.saleItemId),
    qtyMilli: Math.max(0, v3 ? int(f.quantityMilli) : milli(f.quantity)),
    amountMinor: Math.max(0, v3 ? int(f.amountMinor) : minor(f.amount)),
  };
}

/**
 * قلم ⇒ فروخته، سقفِ پول، گردِ نقدیِ فاکتور و لغو — از ردیفِ قلم و فاکتورش. قلمِ بی مقدار ⇒ ناشناخته (سنجیده
 * نمی‌شود)؛ قلمِ بی جمع (`totalPrice`) ⇒ فقط مقدار سنجیده می‌شود (`capMinor` = null)، نه پول.
 */
function factsOf(item, sale, v3) {
  if (!isObj(item)) return null;
  const s = isObj(sale) ? sale : {};
  const sold = v3 ? num(item.quantityMilli) : num(item.quantity);
  if (sold === null) return null;
  const total = v3 ? num(item.totalPriceMinor) : num(item.totalPrice);
  const x = extrasOf(s, v3);   //  POS-37: به پول در هر دو راه (مثلِ گرد)؛ فقط آنچه در نهایی آمده (SR48-1/2)
  const ratio = v3 ? paidRatio(int(s.totalMinor), int(s.discountMinor), x.taxMinor) : paidRatio(num(s.total) || 0, num(s.discount) || 0, x.taxMinor / 100);
  return {
    sale: str(item.saleId), soldMilli: v3 ? Math.round(sold) : milli(sold),
    capMinor: total === null ? null : capMinor(v3 ? total / 100 : total, ratio),
    extraMinor: x.roundMinor, cancelled: s.status === 'cancelled',
  };
}

/** قلم‌ها از دفتر (v3 مقدم، بعد v1، بعد راهِ قدیم)، وگرنه از درج‌های همین دسته (نام‌های v1) */
async function linesOf(c, shopId, items, batch) {
  const out = new Map();
  const put = (id, item, sale, v3) => { if (!out.has(id)) { const f = factsOf(item, sale, v3); if (f) out.set(id, f); } };
  for (const [ledger, v3] of [['sync_v3_rows', true], ['sync_rows', false]]) {
    const rows = (await c.query(
      `SELECT i.row_id, i.data AS item, s.data AS sale FROM ${ledger} i
         LEFT JOIN ${ledger} s ON s.app = i.app AND s.account_kind = i.account_kind AND s.account_id = i.account_id
              AND s.table_name = 'sales' AND s.row_id = i.data->>'saleId' AND s.deleted_at IS NULL
        WHERE i.app='shop' AND i.account_kind='shop' AND i.account_id=$1 AND i.table_name='saleItems'
          AND i.row_id = ANY($2::text[]) AND i.deleted_at IS NULL`, [shopId, items]
    )).rows;
    for (const r of rows) put(r.row_id, r.item, r.sale, v3);
  }
  const legacy = (await c.query(
    `SELECT i.id AS row_id, i.data AS item, s.data AS sale FROM sale_items i
       LEFT JOIN sales s ON s.shop_id = i.shop_id AND s.id = i.data->>'saleId' AND NOT s.deleted
      WHERE i.shop_id=$1 AND i.id = ANY($2::text[]) AND NOT i.deleted`, [shopId, items]
  )).rows;
  for (const r of legacy) put(r.row_id, r.item, r.sale, false);
  for (const op of batch || []) {
    if (op.table !== 'saleItems' || op.type !== 'insert' || !items.includes(op.row_id)) continue;
    const f = fieldsOf(op);
    const sale = (batch.find((o) => o.table === 'sales' && o.type !== 'delete' && o.row_id === str(f.saleId)) || {}).fields;
    put(op.row_id, f, sale, false);
  }
  return out;
}

/**
 * برگشت‌های پیشینِ زندهٔ هر قلم از هر سه دفتر، هر شناسه یک بار (v3 مقدم: پل همان ردیف را در هر دو می‌گذارد). ردیفی که در
 * یکی از دفترها حذف یا لغو شده شمرده نمی‌شود. `skip` = ردیف‌های همین سند ⇒ تکرارِ همان برگشت دوباره شمرده نمی‌شود.
 */
async function priorOf(c, shopId, items, skip) {
  const seen = new Map();
  const take = (id, data, alive, v3) => {
    if (skip.has(id)) return;
    const d = isObj(data) ? data : {};
    const live = alive && d.status !== 'cancelled';
    const cur = seen.get(id);
    if (cur) { cur.dead = cur.dead || !live; return; }
    seen.set(id, { id, item: str(d.saleItemId), dead: !live, ...rowOf({ row_id: id, fields: d }, v3 ? 'v3' : 'v1') });
  };
  for (const [ledger, v3] of [['sync_v3_rows', true], ['sync_rows', false]]) {
    const rows = (await c.query(
      `SELECT row_id, data, deleted_at IS NULL AS alive FROM ${ledger}
        WHERE ${ACC} AND table_name='returns' AND data->>'saleItemId' = ANY($2::text[])`, [shopId, items]
    )).rows;
    for (const r of rows) take(r.row_id, r.data, r.alive, v3);
  }
  const legacy = (await c.query(
    `SELECT id AS row_id, data, NOT deleted AS alive FROM sale_returns WHERE shop_id=$1 AND data->>'saleItemId' = ANY($2::text[])`,
    [shopId, items]
  )).rows;
  for (const r of legacy) take(r.row_id, r.data, r.alive, false);
  const out = new Map();
  for (const r of seen.values()) {
    if (r.dead || !r.item) continue;
    if (!out.has(r.item)) out.set(r.item, []);
    out.get(r.item).push(r);
  }
  return out;
}

/**
 * یک سندِ برگشت (ردیف‌های `rowOf`) پس از برگشت‌های دفتر و `pending` (پذیرفته‌های همین دسته که هنوز در دفتر نیستند،
 * قلم ⇒ ردیف‌ها) جا دارد؟ صدازننده قفلِ برگشت را دارد.
 * @returns {Promise<null | {reason, box, detail}>} همان شکلِ ردِ `staff-approval.judge`
 */
async function judgeDoc(c, ctx, rows, { batch = [], pending = null, path = 'v3' } = {}) {
  const lined = rows.filter((r) => r.item);
  if (!lined.length || !isShop(ctx) || !(await enabled(c, ctx.accountId))) return null;
  const facts = await linesOf(c, ctx.accountId, [...new Set(lined.map((r) => r.item))], batch);
  if (!facts.size) return null;
  const skip = new Set(rows.map((r) => r.id));
  const prior = await priorOf(c, ctx.accountId, [...facts.keys()], skip);
  const lines = [];
  for (const [item, f] of facts) {
    const mine = lined.filter((r) => r.item === item);
    const before = new Map();
    for (const p of [...(prior.get(item) || []), ...((pending && pending.get(item)) || [])]) if (!skip.has(p.id)) before.set(p.id, p);
    const list = [...before.values()];
    const req = { qtyMilli: sum(mine, 'qtyMilli'), amountMinor: sum(mine, 'amountMinor') };
    const capV = f.capMinor === null ? Number.MAX_SAFE_INTEGER : f.capMinor;   //  بی جمعِ قلم ⇒ فقط مقدار
    const s = state(f.soldMilli, capV, list);
    const verdict = f.cancelled ? (req.qtyMilli > 0 || req.amountMinor > 0 ? 'cancelled' : 'ok')
      : check(f.soldMilli, capV, list, req, { tolMinorPerRow: TOL_PER_ROW, extraMinor: f.extraMinor + TOL_PER_ROW * (mine.length - 1) });
    lines.push({
      item, sold_milli: f.soldMilli, returned_milli: s.returnedMilli, left_milli: s.leftMilli, qty_milli: req.qtyMilli,
      cap_minor: f.capMinor, returned_minor: s.returnedMinor, left_minor: f.capMinor === null ? null : s.leftMinor,
      amount_minor: req.amountMinor, verdict,
    });
  }
  if (lines.every((l) => l.verdict === 'ok')) return null;
  return {
    reason: REASON, box: 'perm_denied',
    detail: {
      perm: 'returns.create', decision: 'deny', reason_core: 'exceeds_sold', requester: ctx.userId || '', role: ctx.role || '',
      sale: lined[0].sale || [...facts.values()][0].sale, amount: sum(rows, 'amountMinor'),
      lines: lines.filter((l) => l.verdict !== 'ok').concat(lines.filter((l) => l.verdict === 'ok')).slice(0, MAX_LINES), path,
    },
  };
}

const addPending = (pending, rows) => {
  for (const r of rows) {
    if (!r.item) continue;
    if (!pending.has(r.item)) pending.set(r.item, []);
    pending.get(r.item).push(r);
  }
};

/** ردیفِ صندوقِ تعارض با همهٔ opهای سند (`conflict-payload`)؛ همان op دوباره ⇒ همان ردیف (`perm:<op_id>`) */
async function box(c, ctx, { deviceId = '', path, op, out, ops, extra = {} }) {
  const opId = str(op.op_id).slice(0, 80);
  await require('./sync-v3-store').direct(c).conflict(
    { app: APP, accountKind: APP, accountId: ctx.accountId },
    { table: str(op.table).slice(0, 80), row_id: str(op.row_id).slice(0, 80), loser_op_id: opId,
      loser_device: str(deviceId).slice(0, 64), loser_value: require('./conflict-payload').payload(path, ops) },
    out.box, now(), { dedupe: `perm:${opId}`, detail: { ...out.detail, ...extra } }
  );
}

// ---------- v3/v2 ----------

/** سندِ برگشتِ `staff-approval.groupReturns` (`g.rows` ⇒ {op}) — درونِ تراکنشِ push و زیرِ `returnsLockKey` */
function judgeGroup(q, ctx, g, { path = 'v3' } = {}) {
  if (!q || !g || !Array.isArray(g.rows)) return Promise.resolve(null);
  return judgeDoc(q, ctx, g.rows.map((r) => rowOf(r.op, path)), { path });
}

// ---------- v1 ----------

const keyOf = (op) => `${op.table}\u0000${op.rowId ?? op.row_id}\u0000${op.type}\u0000${op.ts}`;

/** کلِ دستهٔ v1 یک‌جا، درونِ تراکنش و زیرِ قفل (نخستین `check`): op ⇒ دلیلِ رد یا null، به ترتیبِ دسته */
async function planV1(c, ctx, deviceId, valid) {
  const plan = new Map();
  if (!(await enabled(c, ctx.accountId))) return plan;
  const approvals = require('./staff-approval');
  await c.query('SELECT pg_advisory_xact_lock(hashtext($1))', [approvals.returnsLockKey(ctx.accountId)]);
  //  op_id ِ ثبت‌شده را خودِ v1 «تکراری» جواب می‌دهد: نه داوری، نه همراه
  const seen = new Set((await c.query('SELECT op_id FROM oplog WHERE op_id = ANY($1::text[])',
    [valid.map((x) => String(x.op.op_id || ''))])).rows.map((r) => r.op_id));
  const live = valid.filter((x) => !seen.has(String(x.op.op_id || '')));
  const liveOps = live.map((x) => x.op);
  const groups = approvals.groupReturns(live.filter((x) => x.need && x.need.perm === 'returns.create'));
  const why = new Map();
  const pending = new Map();
  for (const g of new Set(groups.values())) {
    const rows = g.rows.map((m) => rowOf(m.op, 'v1'));
    const out = await judgeDoc(c, ctx, rows, { batch: liveOps, pending, path: 'v1' });
    if (!out) { addPending(pending, rows); continue; }
    for (const m of g.rows) why.set(m.op, REASON);
    //  همراه‌های همان ثبت — همان قاعدهٔ `staff-approval` (حرکت، اصلاحِ انبار، رسید، سابقه، شمارنده‌ها)
    const p = approvals.parentOf('returns.create', g.rows.map((m) => m.op), -1, liveOps);
    p.bulk = new Set(liveOps.filter((o) => Number(o.ts) === p.ts).map(approvals.headKey).filter(Boolean)).size > 1;
    const kin = [];
    for (const x of live) {
      if (why.has(x.op) || (x.need && x.op.table === 'sales')) continue;
      if (approvals.belongs(p, x.op)) { why.set(x.op, 'tx_rejected'); kin.push(x.op); }
    }
    await box(c, ctx, { deviceId, path: 'v1', op: g.rows[0].op, out, ops: [...g.rows.map((m) => m.op), ...kin], extra: {
      refs: [...new Set([p.sale, ...p.rows, ...p.items].filter(Boolean))].slice(0, 200), sale: p.sale || out.detail.sale,
      rows: [...p.rows].slice(0, 200), items: [...p.items].slice(0, 200), at: p.at || undefined, ts: p.ts || undefined,
    } });
  }
  for (const x of live) {
    const k = keyOf(x.op);
    if (!plan.has(k)) plan.set(k, []);
    plan.get(k).push(why.get(x.op) || null);
  }
  return plan;
}

/**
 * `guard` ِ `sync-v1.push` برای دکان (همراهِ `inner` = نگهبانِ فاکتورِ ثبت‌شده، `finalized-sales.v1Guard`). دستهٔ بی
 * برگشتِ قلم‌دار، پمپ و هر حسابِ دیگر ⇒ همان `inner` (بی هیچ پرسشی).
 */
function v1Guard(ctx, deviceId, ops, inner = null) {
  if (!isShop(ctx) || !Array.isArray(ops) || !ops.length) return inner;
  const { validateOp } = require('./sync-v1');
  const { needOfV1 } = require('./staff-approval');
  const valid = [];
  for (const raw of ops) {
    const v = validateOp(APP, raw);
    if (v.ok) valid.push({ op: v.op, need: needOfV1(v.op) });
  }
  if (!valid.some((x) => x.op.table === 'returns' && x.op.type === 'insert' && str(fieldsOf(x.op).saleItemId))) return inner;
  let plan = null;
  return {
    async check(c, op, row) {
      if (!plan) plan = await planV1(c, ctx, deviceId, valid);
      const q = plan.get(keyOf(op));
      const why = q && q.length ? q.shift() : null;
      const g = inner ? await inner.check(c, op, row) : null;
      if (g && (g.reject || g.replay)) return g;
      return why ? { reject: why } : g;
    },
    finish: () => (inner ? inner.finish() : undefined),
  };
}

// ---------- REST / راهِ قدیم (`lib/sync.js`) ----------

/**
 * درونِ تراکنشِ `pushChanges` (پس از `reserveRevs`): هر نوشتنِ ردیفِ برگشتِ قلم‌دار (درج یا ویرایش) با مقدار و پولِ تازه‌اش.
 * بیش از ماندهٔ قلم ⇒ خطای ۴۰۹ `return_exceeds_sold` (کلِ دسته برمی‌گردد) با `returnBox` برای صندوقِ پس از برگشت (`boxRest`).
 */
async function legacyCheck(c, ctx, changes) {
  const batch = [];
  const docs = new Map();
  for (const ch of changes || []) {
    const data = isObj(ch && ch.data) ? ch.data : null;
    if (!data || ch.deleted === true) continue;
    const op = { op_id: `rest:${str(ch.id)}`.slice(0, 80), table: str(ch.collection), row_id: str(ch.id), type: 'insert', fields: data };
    batch.push(op);
    if (op.table !== 'returns' || !str(data.saleItemId)) continue;
    const k = str(data.docId) || `sale:${str(data.saleId)}`;
    if (!docs.has(k)) docs.set(k, []);
    docs.get(k).push(op);
  }
  if (!docs.size) return;
  const c3 = { app: APP, accountKind: APP, accountId: ctx.shopId, userId: ctx.userId, role: ctx.role };
  if (!(await enabled(c, ctx.shopId))) return;
  await c.query('SELECT pg_advisory_xact_lock(hashtext($1))', [require('./staff-approval').returnsLockKey(ctx.shopId)]);
  const pending = new Map();
  for (const ops of docs.values()) {
    const rows = ops.map((op) => rowOf(op, 'rest'));
    const out = await judgeDoc(c, c3, rows, { batch, pending, path: 'rest' });
    if (!out) { addPending(pending, rows); continue; }
    const err = require('../middleware/errors').conflict('این برگشت از آنچه روی همان قلمِ فاکتور فروخته شد بیشتر است', REASON);
    err.data = { lines: out.detail.lines };   //  `errorHandler`: جزئیاتِ نمایش‌دادنی کنارِ کد و پیام
    Object.defineProperty(err, 'returnBox', { enumerable: false, value: { ctx: c3, deviceId: ctx.deviceId || '', op: ops[0], out, ops: batch } });
    throw err;
  }
}

/** صندوقِ تعارضِ ردِ REST — پس از برگشتِ تراکنش (بیرون از آن) */
async function boxRest(err) {
  const b = err && err.returnBox;
  if (!b) return;
  await box({ query: (s, p) => query(s, p) }, b.ctx, { deviceId: b.deviceId, path: 'rest', op: b.op, out: b.out, ops: b.ops });
}

module.exports = {
  FLAG, REASON, TOL_PER_ROW, milli, minor, capMinor, paidRatio, state, check, enabled, rowOf, judgeDoc, judgeGroup,
  v1Guard, legacyCheck, boxRest,
};
