'use strict';
/**
 * نگهبانِ پولِ صندوق در Sync v3 — دو داوری، هر کدام پشتِ پرچمِ خاموشِ خودش (`live_config`، دکان یا `all`، فقط `true`؛
 * همان `orders.flagOf` با «غیرفعال کردنِ سرویس»). صدا از سرِ `doc-roles.judgeV3` (همان جای `authorize` ِ `sync-v3.js`)،
 * فقط برای `insert` ِ v3/v2 روی ردیفی که هنوز ننشسته. ⛔ خاموش ⇒ پاسخ همان امروز؛ تنها کار خواندنِ پرچم است، آن هم فقط برای
 * opی از همین نوع‌ها. ⛔ `sync-v1.js` ِ مشترک با پمپ دست نمی‌خورد: ② در v1 نیست (همان مرزِ `doc-roles.js`)؛ ① در v1 از سرِ
 * میان‌افزارِ فقط‌دکانِ `staff-approval.v1Guard` (`v1Guard` ِ همین‌جا، ①′ پایین) — برنامه امروز پیش‌فرض با v1 همگام می‌شود.
 * همان در «کاربرِ سنجیدهٔ صندوق» ِ S46 را هم برای هر op ِ v1 می‌نشاند (`till-op-user.v1Hook`، پرچمِ خاموشِ خودش).
 * پولِ هر دو دفتر خوانده می‌شود (v3 مقدم، ردیفِ v1 ِ هم‌شناسه یک بار)، همان راهِ `return-sale-cap.js`.
 *
 * ① `shop.sync.pay_fix` — POS-40، «اصلاحِ روشِ پرداخت» (`auditLog` با `type = pay_fix`، `PayFixFlow` ِ برنامه):
 *    pay_fix_invalid     شکل (برای همه، صاحب هم): فاکتور (`refId`)، دو روشِ جدای شمردنی (نه `account`، اعتبار و کارتِ
 *                        هدیه)، مبلغِ صحیحِ مثبت، دلیلِ یک‌خطیِ ۳ تا ۱۲۰ نویسه؛ و فاکتورِ لغوشده، اینترنتی یا برگشت‌خورده
 *    pay_fix_exceeds     مبلغ از ماندهٔ «روشِ پیشین» پس از اصلاح‌های ثبت‌شدهٔ سرور بیشتر است — همان `payFixApply` ِ
 *                        `license/pay-core.js`؛ دو دستگاه یک پول را دو بار جابه‌جا نمی‌کنند (صندوقِ تعارض `perm_denied`)
 *    pay_fix_closed      روزِ فاکتور بسته است (`dayClosed` = `PayFix.closed`، از `shift_open`/`shift_close` ِ سرور): برای همه،
 *                        صاحب هم (صندوقِ تعارض `perm_denied`) — Z ِ بسته با اصلاحِ دیررسِ دستگاهِ دیگر عوض نمی‌شود
 *    مجوز                `sales.pay_fix` با `staff-approval.judge` (نقش + لایه + «برگهٔ امضاشده»ی `fields.approval` برای
 *                        `auditLog/<ردیف>` تا همان مبلغ): ممنوع ⇒ `pay_fix_not_allowed`؛ «نیازمند تأیید» بی برگه ⇒
 *                        `approval_required`؛ برگهٔ نادرست ⇒ کدِ همان نگهبان. برنامهٔ پیش از (2.14.5) برگه را نمی‌فرستاد ⇒
 *                        پرچم پس از همه‌گیر شدنِ نسخهٔ تازه روشن شود. فاکتوری که سرور ندارد فقط شکل و مجوز می‌گیرد.
 * ② `shop.sync.refund_cap` — POS-26/POS-27: بردِ بازپرداختِ سفارشِ ویژه (`special_order_refund`) و امانی
 *    (`layaway_refund`) از پولِ نگه‌داشتهٔ همان سند (رسیدهای پیش‌پرداخت/قسط − بازپرداخت‌های پیشین) بیشتر نمی‌شود —
 *    همان `held` ِ برنامه (`SpecialOrder.held`، `Layaways.heldMinor`). لغوِ هم‌زمان روی دو دستگاه دیگر دو بار نقد از دخل
 *    نمی‌بَرد: `refund_exceeds_held` (همهٔ واحدِ tx، صندوقِ تعارض `perm_denied`)، برای همه، صاحب هم.
 * آزمون: `test/till-guard.test.js`.
 */
const PAY_FIX_FLAG = 'shop.sync.pay_fix';
const REFUND_FLAG = 'shop.sync.refund_cap';
const PERM = 'sales.pay_fix';
/** همان `ACCOUNT` و روش‌های ناشمردنیِ پیش‌فرضِ pay-core */
const NOT_MOVABLE = new Set(['account', 'store_credit', 'gift_card']);
const REASON_MIN = 3;
const REASON_MAX = 120;
/** خانوادهٔ بازپرداخت ⇒ رسیدهای همان سند */
const REFUNDS = Object.freeze({
  special_order_refund: ['special_order_deposit'],
  layaway_refund: ['layaway_deposit', 'layaway_installment'],
});

const isObj = (v) => v !== null && typeof v === 'object' && !Array.isArray(v);
const text = (v) => (typeof v === 'string' ? v.trim() : '');
const posInt = (v) => Number.isSafeInteger(v) && v > 0;
const pul = (v) => { const n = Number(v); return Number.isFinite(n) ? Math.round(n) : 0; };
const afnToPul = (v) => pul(Number(v) * 100);
const ACC = `app='shop' AND account_kind='shop' AND account_id=$1`;

/** ردیف‌های هر دو دفتر برای یک شرط؛ v3 مقدم و ردیفِ v1 ِ هم‌شناسه فقط یک بار. `money` ⇒ مبلغِ ردیف به پول */
async function rowsOf(c, ctx, table, where, params) {
  const out = new Map();
  const v3 = (await c.query(
    `SELECT row_id, data FROM sync_v3_rows WHERE ${ACC} AND table_name=$2 AND inserted AND deleted_at IS NULL AND ${where}`,
    [ctx.accountId, table, ...params]
  )).rows;
  for (const r of v3) out.set(r.row_id, { data: r.data || {}, v1: false });
  const v1 = (await c.query(
    `SELECT row_id, data FROM sync_rows WHERE ${ACC} AND table_name=$2 AND deleted_at IS NULL AND ${where}`,
    [ctx.accountId, table, ...params]
  )).rows;
  for (const r of v1) if (!out.has(r.row_id)) out.set(r.row_id, { data: r.data || {}, v1: true });
  return [...out.entries()].map(([id, r]) => ({ id, ...r }));
}
const moneyOf = (r, field) => (r.v1 ? afnToPul(r.data[field]) : pul(r.data[`${field}Minor`]));

// ---------- ① اصلاحِ روشِ پرداخت ----------

/** شکلِ ردیفِ برنامه (`PayFixFlow.row`)؛ null یعنی درست */
function payFixShape(f) {
  const from = text(f.fromMethod);
  const to = text(f.toMethod);
  const reason = text(f.reason);
  if (!text(f.refId) || !from || !to || from === to || NOT_MOVABLE.has(from) || NOT_MOVABLE.has(to)) return 'pay_fix_invalid';
  if (!posInt(f.amountMinor)) return 'pay_fix_invalid';
  if (reason.length < REASON_MIN || reason.length > REASON_MAX || /[\r\n]/.test(reason)) return 'pay_fix_invalid';
  return null;
}

/** ردیف‌های پرداختِ فاکتور همان‌طور که ثبت شد — همان `PayFlow.saleRowsAsPosted` (پول) */
function postedRows(sale) {
  const d = sale.data;
  if (Array.isArray(d.payments) && d.payments.length) {
    return d.payments.filter((p) => isObj(p) && !p.custody)
      .map((p) => ({ method: text(p.method), amount: pul(p.amount ?? p.amountMinor) }))
      .filter((p) => p.amount > 0);
  }
  const debt = Math.max(0, moneyOf(sale, 'debtGiven'));
  const cash = Math.max(0, moneyOf(sale, 'finalTotal') - debt);
  return [{ method: 'cash', amount: cash }, { method: 'account', amount: debt }].filter((r) => r.amount > 0);
}

/** ماندهٔ هر روش پس از اصلاح‌ها به ترتیبِ (زمان، شناسه)؛ اصلاحِ بی‌جا کنار می‌رود — همان `payFixApply` */
function applyFixes(rows, fixes) {
  const sums = new Map();
  for (const r of rows) sums.set(r.method, (sums.get(r.method) || 0) + r.amount);
  const list = [...fixes].sort((a, b) => (a.at !== b.at ? a.at - b.at : a.id < b.id ? -1 : a.id > b.id ? 1 : 0));
  for (const f of list) {
    const have = sums.get(f.from) || 0;
    if (!posInt(f.amount) || f.from === f.to || f.from === 'account' || f.to === 'account' || f.amount > have) continue;
    sums.set(f.from, have - f.amount);
    sums.set(f.to, (sums.get(f.to) || 0) + f.amount);
  }
  return sums;
}

/**
 * روزِ فاکتور بسته است؟ — همان `PayFix.closed` (core) و `payFixClosed` (pay-core.js)، بردارِ `payFix.closed` ِ
 * `pay-v1.json`: فاکتور در پنجرهٔ صندوقِ بسته‌ای است، یا پیش از آخرین بستنِ (Z) صندوق‌هایی که همان روز باز شدند.
 * @param shifts [{ openedAt, closedAt (null ⇒ باز), day }]
 */
function dayClosed(saleAt, saleDay, shifts) {
  const day = text(saleDay);
  let last = null;
  for (const w of shifts || []) {
    if (w.closedAt === null || w.closedAt === undefined) continue;
    if (saleAt >= w.openedAt && saleAt <= w.closedAt) return true;
    if (day && text(w.day) === day && (last === null || w.closedAt > last)) last = w.closedAt;
  }
  return last !== null && saleAt <= last;
}

const at = (v) => (typeof v === 'number' && Number.isFinite(v) ? v : null);

/**
 * پنجره‌های صندوق از سابقهٔ هر دو دفتر — همان `CashShift.list`: `shift_open` و `shift_close` با `refId` = شناسهٔ
 * بازکردن (همهٔ دستگاه‌ها و کشوها؛ opهای پیشینِ همین دسته هم). دو بستن برای یک صندوق ⇒ دیرترین (سخت‌گیرانه). زمانِ
 * بدشکل ⇒ آن ردیف کنار می‌رود.
 */
function windowsOf(rows) {
  const closes = new Map();
  for (const r of rows) {
    const t = at(r.data.createdAt);
    const ref = text(r.data.refId);
    if (r.data.type !== 'shift_close' || t === null || !ref) continue;
    if (!closes.has(ref) || t > closes.get(ref)) closes.set(ref, t);
  }
  return rows.filter((r) => r.data.type === 'shift_open' && at(r.data.createdAt) !== null)
    .map((r) => ({ openedAt: r.data.createdAt, closedAt: closes.has(r.id) ? closes.get(r.id) : null, day: text(r.data.date) }));
}

/** آنچه داوریِ اصلاح از دفتر می‌خواند — پایگاه‌داده (هر دو دفتر)؛ v1 روی آن opهای پیشینِ همان دسته را می‌گذارد (`batchSource`) */
function dbSource(c, ctx) {
  return {
    sale: async (id) => (await rowsOf(c, ctx, 'sales', 'row_id=$3', [id]))[0] || null,
    returns: (saleId) => rowsOf(c, ctx, 'returns', `data->>'saleId'=$3`, [saleId]),
    shifts: () => rowsOf(c, ctx, 'auditLog', `data->>'type' IN ('shift_open','shift_close')`, []),
    fixes: (saleId) => rowsOf(c, ctx, 'auditLog', `data->>'type'='pay_fix' AND data->>'refId'=$3`, [saleId]),
  };
}

/**
 * @param src  همان `dbSource` (v3)؛ v1 ⇒ `batchSource`
 * @param when زمانِ op برای برگه — v3 دیوارِ HLC، v1 همان `ts`
 */
async function judgePayFix(c, ctx, op, { deviceId, origin, src = dbSource(c, ctx), when = undefined }) {
  const f = op.fields;
  const bad = payFixShape(f);
  if (bad) return bad;
  const saleId = text(f.refId);
  const sale = await src.sale(saleId);
  const approvals = require('./staff-approval');
  if (sale) {
    const d = sale.data;
    if (d.status === 'cancelled' || d.channel === 'online' || text(d.series) === 'W') return 'pay_fix_invalid';
    if ((await src.returns(saleId)).length) return 'pay_fix_invalid';
    //  روزِ بسته: Z ثابت می‌ماند — دستگاهِ بی‌اینترنت یا کهنه پس از Z ِ دستگاهِ دیگر پول را میانِ روش‌ها جابه‌جا نمی‌کند
    const saleAt = at(d.createdAt);
    if (saleAt !== null && dayClosed(saleAt, d.date, windowsOf(await src.shifts()))) {
      return {
        reason: 'pay_fix_closed', box: 'perm_denied',
        detail: { perm: PERM, decision: 'deny', reason_core: 'day_closed', requester: ctx.userId || '', role: ctx.role || '',
          amount: f.amountMinor, sale: saleId, day: text(d.date), path: origin || 'v3' },
      };
    }
    const prior = (await src.fixes(saleId))
      .filter((r) => r.id !== op.row_id)
      .map((r) => ({ id: r.id, at: pul(r.data.createdAt), from: text(r.data.fromMethod), to: text(r.data.toMethod), amount: moneyOf(r, 'amount') }));
    const have = applyFixes(postedRows(sale), prior).get(text(f.fromMethod)) || 0;
    if (f.amountMinor > have) {
      return {
        reason: 'pay_fix_exceeds', box: 'perm_denied',
        detail: { perm: PERM, decision: 'deny', reason_core: 'exceeds', requester: ctx.userId || '', role: ctx.role || '',
          amount: f.amountMinor, available: have, method: text(f.fromMethod), sale: saleId, path: origin || 'v3' },
      };
    }
  }
  const need = { perm: PERM, doc: `auditLog/${op.row_id}`, req: { amount: f.amountMinor }, amount: f.amountMinor };
  return approvals.judge(c, ctx, need, approvals.slipOf(op), {
    opId: op.op_id, at: when === undefined ? approvals.wallOf(op.hlc) : when, deviceId, path: origin || 'v3',
  });
}

// ---------- ①′ همان، در Sync v1 ----------

/**
 * فیلدهای سندِ اصلاحِ ثبت‌شده — سرِ جا عوض نمی‌شوند (v3 خودش `auditLog` را فقط‌افزودنی می‌داند: `log`). مُهر و حلقهٔ
 * زنجیرهٔ سابقه (STF-09) و `notes` بیرونِ فهرست‌اند: برنامه بعدتر روی همان ردیف می‌نویسدشان.
 */
const FIX_LOCKED = Object.freeze(['type', 'refId', 'amount', 'amountMinor', 'fromMethod', 'toMethod', 'payRef', 'by', 'approvedBy',
  'approval', 'reason', 'date', 'createdAt']);
const dbq = { query: (s, p) => require('../db').query(s, p) };
const isInc = (v) => isObj(v) && Object.keys(v).length === 1 && typeof v.$inc === 'number';
const norm = (v) => (v === undefined ? null : JSON.stringify(v));

/** ردیفِ v1 ⇐ نامِ v3: `amount` ِ افغانیِ `AuditEntry` ⇐ `amountMinor` (فقط عدد؛ رشته ⇒ بی مبلغ = شکلِ بد) */
function v1Fix(f) {
  return { ...f, amountMinor: typeof f.amount === 'number' ? afnToPul(f.amount) : undefined };
}

/** روی ردیفِ [cur] (`{data, v1}`) فیلدهای op می‌نشیند — همان ادغامِ فیلدبه‌فیلدِ v1 (`$inc` جمع) */
function merged(cur, fields) {
  const data = { ...(cur ? cur.data : {}) };
  for (const [k, v] of Object.entries(fields || {})) data[k] = isInc(v) ? (Number(data[k]) || 0) + v.$inc : v;
  return { data, v1: cur ? cur.v1 : true };
}

/** opهای پیاپیِ یک ردیف در یک دسته: آخرین مقدار، `$inc` ِ پشتِ هم جمع */
function accumulate(prev, fields) {
  const out = { ...prev };
  for (const [k, v] of Object.entries(fields || {})) {
    const p = out[k];
    out[k] = isInc(v) && isInc(p) ? { $inc: p.$inc + v.$inc } : isInc(v) && p !== undefined ? (Number(p) || 0) + v.$inc : v;
  }
  return out;
}

/** ویرایش یا حذفِ سرِ جای ردیفی که اصلاح است یا اصلاح می‌شود ⇒ `pay_fix_invalid`؛ همان محتوا (بازپخش، مُهر) ⇒ null */
function editOf(cur, op) {
  if (op.type === 'delete') return 'pay_fix_invalid';
  const f = op.fields || {};
  if (cur.data.type !== 'pay_fix') return 'pay_fix_invalid';
  for (const k of FIX_LOCKED) {
    if (!Object.prototype.hasOwnProperty.call(f, k)) continue;
    if (k === 'amount' || k === 'amountMinor') {
      const want = k === 'amount' ? (typeof f.amount === 'number' ? afnToPul(f.amount) : NaN) : f.amountMinor;
      if (want !== moneyOf(cur, 'amount')) return 'pay_fix_invalid';
    } else if (norm(f[k]) !== norm(cur.data[k])) return 'pay_fix_invalid';
  }
  return null;
}

/**
 * پایگاه‌داده + opهای پیشینِ همین دستهٔ v1 (هنوز ننشسته‌اند؛ v3 آن‌ها را در همان تراکنش می‌بیند): فاکتور، برگشت،
 * صندوق و اصلاح‌های پذیرفته. [over] ⇐ `جدول\u0000شناسه` ⇒ `{fields, deleted}`.
 */
function batchSource(db, over) {
  const mine = (table) => [...over.entries()].filter(([k]) => k.startsWith(`${table}\u0000`))
    .map(([k, v]) => ({ id: k.slice(table.length + 1), ...v }));
  /** ردیف‌های پایگاه‌داده با جای‌گذاریِ همان شناسه از دسته؛ [keep] روی دادهٔ ادغام‌شده */
  const blend = (table, rows, keep) => {
    const out = new Map(rows.map((r) => [r.id, r]));
    for (const o of mine(table)) {
      if (o.deleted) { out.delete(o.id); continue; }
      out.set(o.id, { id: o.id, ...merged(out.get(o.id), o.fields) });
    }
    return [...out.values()].filter((r) => keep(r.data));
  };
  return {
    sale: async (id) => {
      const o = over.get(`sales\u0000${id}`);
      const base = await db.sale(id);
      if (!o) return base;
      return o.deleted ? null : { id, ...merged(base, o.fields) };
    },
    returns: async (saleId) => blend('returns', await db.returns(saleId), (d) => text(d.saleId) === saleId),
    shifts: async () => blend('auditLog', await db.shifts(), (d) => d.type === 'shift_open' || d.type === 'shift_close'),
    fixes: async (saleId) => blend('auditLog', await db.fixes(saleId), (d) => d.type === 'pay_fix' && text(d.refId) === saleId),
  };
}

/**
 * POS-40 در Sync v1 — همان داوریِ ① برای ردیفی که از راهِ v1 می‌رسد. برنامه امروز پیش‌فرض با v1 همگام می‌شود
 * (`SyncChoice`)، پس بی این، پرچمِ روشن روی دستگاهِ واقعی چیزی نمی‌سنجید. صدا از سرِ `staff-approval.v1Guard` (میان‌افزارِ
 * فقط‌دکانِ `routes/sync-v1.js`، پیش از `sync-v1.js` ِ مشترک با پمپ که دست نمی‌خورد)، برای همه، صاحب هم.
 * ⛔ خاموش ⇒ تنها کار خواندنِ پرچم، آن هم فقط برای دسته‌ای که op ِ `auditLog` دارد.
 *   درجِ ردیفِ تازهٔ `pay_fix`   ⇐ همان `judgePayFix` (شکل، فاکتور، روزِ بسته، مانده، مجوز و برگه) روی پایگاه‌داده + opهای
 *                               پیشینِ همین دسته؛ مبلغ از `amount` ِ افغانی
 *   ردیفِ موجودِ اصلاح        ⇐ فقط‌افزودنی: حذف یا تغییرِ `FIX_LOCKED` ⇒ `pay_fix_invalid`؛ همان محتوا آزاد
 *   ردیفِ دیگرِ سابقه ⇐ اصلاح  ⇒ `pay_fix_invalid`
 * op_id ِ ثبت‌شده را خودِ v1 «تکراری» جواب می‌دهد (داوری نمی‌شود). ردشده‌ها با `rejectOps` از دسته بیرون؛ ردِ مجوز، مانده و
 * روزِ بسته ردیفی در صندوقِ تعارض (`boxV1`).
 */
async function v1Guard(req, res) {
  //  S46 (`shop.till_op_user`، صاحب هم): کاربرِ سنجیدهٔ صندوق برای هر op ِ نشسته — نوبت همین حالا، ردیف پس از پاسخِ موتور
  //  (‎lib/till-op-user.js‎). ⛔ پس از پالایش‌های `newer-fields`/`contact` (پاسخِ ساختگیِ op ِ برداشته را نمی‌بیند) و پیش از هر `rejectOps`
  await require('./till-op-user').v1Hook(req, res);
  const ctx = req.sync;
  const ops = Array.isArray(req.body?.ops) ? req.body.ops : null;
  if (!ops || !ops.length) return;
  const { validateOp, tableOf } = require('./sync-v1');
  //  بی op ِ سابقه هیچ کار (نه سنجشِ op، نه پرچم)؛ خاموش ⇒ فقط خواندنِ پرچم
  if (!ops.some((r) => isObj(r) && tableOf('shop', r.table ?? r.table_name ?? r.tableName ?? '') === 'auditLog')) return;
  if (!(await require('./orders').flagOf(ctx.accountId, PAY_FIX_FLAG))) return;
  const valid = [];
  ops.forEach((raw, i) => { const v = validateOp('shop', raw); if (v.ok) valid.push({ i, op: v.op }); });
  const audit = valid.filter((x) => x.op.table === 'auditLog');
  if (!audit.length) return;
  const db = dbSource(dbq, ctx);
  const ids = [...new Set(audit.map((x) => x.op.row_id))];
  const base = new Map((await rowsOf(dbq, ctx, 'auditLog', 'row_id = ANY($3::text[])', [ids])).map((r) => [r.id, r]));
  //  سنگِ قبرِ v1 با محتوا: نوشتن رویش ردیف را با همان داده زنده می‌کند (`applyOp`)
  for (const r of (await dbq.query(
    `SELECT row_id, data FROM sync_rows WHERE ${ACC} AND table_name='auditLog' AND deleted_at IS NOT NULL AND row_id = ANY($2::text[])`,
    [ctx.accountId, ids])).rows) {
    if (!base.has(r.row_id)) base.set(r.row_id, { id: r.row_id, data: r.data || {}, v1: true, dead: true });
  }
  const touches = (x, cur) => (cur && cur.data.type === 'pay_fix') || (x.op.type !== 'delete' && isObj(x.op.fields) && x.op.fields.type === 'pay_fix');
  if (!audit.some((x) => touches(x, base.get(x.op.row_id)))) return;
  const seen = new Set((await dbq.query('SELECT op_id FROM oplog WHERE op_id = ANY($1::text[])',
    [audit.map((x) => String(x.op.op_id))])).rows.map((r) => r.op_id));

  const deviceId = String(req.body?.device_id || req.body?.deviceId || '').slice(0, 64);
  const over = new Map();
  const src = batchSource(db, over);
  const rejected = new Map();
  const boxes = [];
  for (const x of valid) {
    const { op } = x;
    const key = `${op.table}\u0000${op.row_id}`;
    const prev = over.get(key);
    if (op.table === 'auditLog') {
      const b = base.get(op.row_id) || null;
      const cur = prev ? { id: op.row_id, ...merged(b, prev.fields), dead: prev.deleted } : b;
      if (touches(x, cur) && !seen.has(String(op.op_id))) {
        let out;
        if (cur && cur.dead) out = op.type === 'delete' ? null : 'pay_fix_invalid';   //  اصلاحِ حذف‌شده سرِ جا زنده نمی‌شود
        else if (cur) out = editOf(cur, op);
        else {
          out = await judgePayFix(dbq, ctx, { ...op, fields: v1Fix(op.fields) },
            { deviceId, origin: 'v1', src, when: Number(op.ts) > 0 ? Number(op.ts) : undefined });
        }
        if (out) {
          rejected.set(x.i, { op_id: String(op.op_id || ''), status: 'rejected', reason: typeof out === 'string' ? out : out.reason });
          if (typeof out !== 'string') boxes.push({ op, out });
          continue;
        }
      }
    }
    over.set(key, { fields: accumulate(prev ? prev.fields : {}, op.type === 'delete' ? {} : op.fields), deleted: op.type === 'delete' });
  }
  if (!rejected.size) return;
  const { boxV1 } = require('./staff-approval');
  const cpay = require('./conflict-payload');
  for (const { op, out } of boxes) await boxV1(ctx, op, deviceId, { box: out.box, detail: out.detail, payload: cpay.payload('v1', [op]) });
  require('./v1-reject').rejectOps(req, res, ops, rejected);
}

// ---------- ② سقفِ بازپرداختِ سفارشِ ویژه و امانی ----------

async function judgeRefund(c, ctx, op, { origin }) {
  const f = op.fields;
  const receipts = REFUNDS[f.kind];
  const ref = text(f.refId);
  if (f.type !== 'give' || !posInt(f.amountMinor) || !ref) return null;   //  شکل مالِ `doc-roles` است
  const kinds = [...receipts, f.kind];
  const rows = (await rowsOf(c, ctx, 'transactions', `data->>'refId'=$3 AND data->>'kind' IN (${kinds.map((_, i) => `$${i + 4}`).join(',')})`, [ref, ...kinds]))
    .filter((r) => r.id !== op.row_id);
  let held = 0;
  for (const r of rows) {
    const a = moneyOf(r, 'amount');
    if (r.data.type === 'receive' && receipts.includes(r.data.kind)) held += a;
    if (r.data.type === 'give' && r.data.kind === f.kind) held -= a;
  }
  held = Math.max(0, held);
  if (f.amountMinor <= held) return null;
  return {
    reason: 'refund_exceeds_held', box: 'perm_denied',
    detail: { perm: 'sales.cancel', decision: 'deny', reason_core: 'exceeds_held', requester: ctx.userId || '', role: ctx.role || '',
      amount: f.amountMinor, held, doc: ref, kind: f.kind, path: origin || 'v3' },
  };
}

// ---------- ورودی ----------

/**
 * @param c  اتصالِ تراکنشِ push (opهای پیشینِ همین دسته دیده می‌شوند)
 * @returns {Promise<undefined | null | string | {reason, box, detail}>} undefined ⇒ مالِ این نگهبان نیست
 *          (صدازننده ادامه می‌دهد)؛ null ⇒ همین‌جا پذیرفته؛ وگرنه همان شکلِ ردِ `authorize`
 */
async function judgeV3(c, ctx, op, { deviceId = '', origin = 'v3' } = {}) {
  const f = isObj(op.fields) ? op.fields : {};
  const payFix = op.table === 'auditLog' && f.type === 'pay_fix';
  const refund = op.table === 'transactions' && typeof f.kind === 'string' && Object.prototype.hasOwnProperty.call(REFUNDS, f.kind);
  if (!payFix && !refund) return undefined;
  const flag = payFix ? PAY_FIX_FLAG : REFUND_FLAG;
  if (!(await require('./orders').flagOf(ctx.accountId, flag, (s, p) => c.query(s, p)))) return undefined;
  if (payFix) return judgePayFix(c, ctx, { ...op, fields: f }, { deviceId, origin });
  return (await judgeRefund(c, ctx, { ...op, fields: f }, { origin })) || undefined;
}

module.exports = { PAY_FIX_FLAG, REFUND_FLAG, FIX_LOCKED, payFixShape, postedRows, applyFixes, dayClosed, judgeV3, v1Guard };
