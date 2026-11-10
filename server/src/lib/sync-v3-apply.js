'use strict';
/**
 * Sync v3 — اعمالِ یک op (جداشده از `sync-v3.js`، رفتار همان): ساعتِ سرور، داوریِ نقش و پول،
 * تکراری، ادغام، نوشتن، و پیامدها (بارکد/مشتریِ تکراری، موجودیِ منفی، تعدیلِ شمارش).
 *
 * هر خواندن و نوشتنِ دفتر از «انبار» ([s]) می‌گذرد: `sync-v3-store.direct(c)` (همان SQL ِ همیشه) یا
 * `sync-v3-batch.open(…)` (پرچمِ `SYNC_V3_BATCH`). داورهای بیرونی همیشه `s.c` را می‌گیرند.
 */
const { one } = require('../db');
const { can } = require('./permissions');
const hlc = require('./hlc');
const approvals = require('./staff-approval');
const permCore = require('./perm-core');
const { SERVER_DEVICE, validate, merge, emptyRow, pick, shortId, normPhone } = require('./sync-v3-merge');

const clock = hlc.createClock({ node: SERVER_DEVICE });
let clockReady = null;

/** ساعتِ سرور پس از راه‌اندازی از بزرگ‌ترین HLCِ ثبت‌شده عقب‌تر نمی‌دهد. */
function ready() {
  if (!clockReady) {
    //  بزرگ‌ترین HLC همیشه میانِ opهای تازه است (HLC با زمان جلو می‌رود)؛
    //  پس به جای پیمایشِ کلِ دفتر، چند هزار ردیفِ آخر بس است
    clockReady = one('SELECT MAX(hlc) AS m FROM (SELECT hlc FROM sync_v3_ops ORDER BY server_seq DESC LIMIT 5000) x')
      .then((r) => { if (r && r.m) clock.observe(r.m); })
      .catch((err) => { clockReady = null; throw err; });
  }
  return clockReady;
}

/** وظیفهٔ بررسی (موجودیِ منفی، بارکد یا مشتریِ تکراری) — برای هر کلید یکی باز. */
function openTask(s, ctx, { kind, table, rowId, dedupe, detail, t }) {
  return s.conflict(ctx, { table, row_id: rowId }, kind, t, { dedupe, detail });
}

// ---------- دسترسیِ هر op ----------

/**
 * @returns {Promise<null | string | {reason, box, detail}>} دلیلِ رد؛ با `box`
 *          یعنی ردیفی هم در صندوقِ تعارض لازم است (opِ پولیِ ردشده).
 */
async function authorize(c, ctx, op, row, deviceId, origin, groups = null, plan = null) {
  if (origin === 'v1' || origin === 'server') return null;      //  v1 خودش سنجیده بود (و `v1Guard`)
  { const pu = await require('./product-unit-rules').judgeV3(c, ctx, op, row); if (pu) return pu; }   //  PRD-07: واحدِ گردش‌دار و ضریبِ ناجور (صاحب هم)
  const role = ctx.role;
  const own = !!row && row.created_by === deviceId;
  if (op.kind === 'delete' && op.spec.policy === 'master') {
    if (can(role, 'data.delete.any')) return null;
    if (!can(role, 'data.delete.own')) return 'delete_not_allowed';
    return !row || !row.created_by || own ? null : 'delete_not_allowed';
  }
  if (op.table === 'safeEntries') return require('./safe-ledger').judgeV3(ctx, op, row, (s, p) => c.query(s, p));   // گاوصندوق (PAY-18): فقط درج، شکل و safe.move
  { const pc = await require('./price-cap').judgeV3(c, ctx, op, row, { plan, deviceId, origin }); if (pc !== undefined) return pc; }   //  STF-03 قیمت/تخفیفِ پنهان (‎lib/price-cap.js‎، #988)
  //  تخفیف، لغوِ فاکتور و برگشت: نقش + دو لایه + «برگهٔ امضاشده» (STF-02/03)
  const need = approvals.needOf(op, row);
  if (need) {
    //  برگشت: سقف روی جمعِ سند (docId یا tx)، یک داوری برای همهٔ ردیف‌هایش
    const g = need.perm === 'returns.create' && groups ? groups.get(op) : null;
    if (g) return approvals.judgeGroup(c, ctx, g, { at: approvals.wallOf(g.rows[0].op.hlc), deviceId, path: origin });
    //  قاعدهٔ امروزِ v1 برای نقشی که لغو ندارد (شاگرد): «حذفِ فاکتورِ خودت» از راهِ حذفِ v2
    const fallback = need.perm === 'sales.cancel' && op.fromDelete && own && can(role, 'data.delete.own')
      && permCore.defaultMode(role, 'sales.cancel') === 'deny';
    return (await approvals.judge(c, ctx, need, approvals.slipOf(op), {
      opId: op.op_id, at: approvals.wallOf(op.hlc), deviceId, path: origin, fallback,
    })) || require('./credit-cap').judgeV3(c, ctx, op, row, { withDiscount: true, deviceId, origin });   //  STF-03: قرضِ همان فاکتور
  }
  { const cr = await require('./credit-cap').judgeV3(c, ctx, op, row, { deviceId, origin }); if (cr) return cr; }   //  STF-03: قرض/دلتای قرض (‎lib/credit-cap.js‎)
  { const cl = await require('./credit-limit-perm').judgeV3(c, ctx, op, row, { origin }); if (cl) return cl; }   //  CRM-03 §۱۰.۱: سقفِ اعتبارِ مشتری (‎lib/credit-limit-perm.js‎)
  if (op.kind === 'cancel') {
    if (can(role, op.spec.cancel)) return null;
    //  همان قاعدهٔ v1: «حذفِ ردیفِ خودت» — برای سندِ غیرِ فاکتور
    if (can(role, 'data.delete.own') && own) return null;
    return 'cancel_not_allowed';
  }
  return require('./transfer-step-scope').markV3(c, ctx, op, row, await require('./doc-roles').judgeV3(c, ctx, op, row, { deviceId, origin }));   // کارت، امتیاز، امانی، سفارش، محل؛ پولِ صندوق (`till-guard`)؛ پذیرفته ⇒ STF-06 ِ گامِ انتقال (۲۸۰)
}

// ---------- اعمالِ یک op ----------

/**
 * @returns {Promise<object>} نتیجهٔ همین op برای پاسخ:
 *          applied | partial | conflict | duplicate | rejected
 */
async function applyOne(s, ctx, op, { deviceId, origin, cursor = 0, t, groups = null, plan = null, hideContact = false }) {
  const c = s.c;
  const dup = await s.dup(ctx, op.op_id);
  if (dup) return { op_id: op.op_id, status: 'duplicate', server_seq: Number(dup.server_seq) };

  const row = await s.load(ctx, op.table, op.row_id);
  const denied = await require('./sync-v3-roles').denied(c, ctx, op, origin, plan) || await authorize(c, ctx, op, row, deviceId, origin, groups, plan);
  if (denied) {
    const out = { op_id: op.op_id, status: 'rejected', reason: typeof denied === 'string' ? denied : denied.reason };
    //  ردیفِ صندوق بعد از برگشتِ savepoint نوشته می‌شود (`push`)؛ در پاسخ نمی‌آید
    if (denied.box) {
      Object.defineProperty(out, 'box', {
        enumerable: false,
        value: { kind: denied.box, detail: denied.detail, table: op.table, row_id: op.row_id, op_id: op.op_id },
      });
    }
    return out;
  }
  const cc = hideContact ? require('./customer-contact').guardV3(op, row, origin) : null;   //  ⛔ CRM-15: تماسِ ردیفِ موجود دست نمی‌خورد
  if (cc) { if (cc.noop) return cc.noop; op = cc.op; }

  //  نگهبانِ ساعت: سرور با HLCِ کج جلو نمی‌رود و آن را «مؤثر» نمی‌کند
  let eff = op.hlc;
  let skewed = false;
  if (origin !== 'server') {
    const r = clock.recv(op.hlc);
    if (r.skewed) { skewed = true; eff = clock.tick(); }
  } else {
    clock.observe(op.hlc);
  }

  const seq = await s.seq();
  const res = merge(row, { ...op, hlc: eff, client_hlc: op.hlc }, { seq, deviceId, cursor, t, skewed });

  await s.insertOp(ctx, {
    op_id: op.op_id, seq, device_id: deviceId, origin, tx_id: op.tx || '', hlc: eff, client_hlc: op.hlc, skewed,
    table: op.table, row_id: op.row_id, kind: op.kind,
    fields: JSON.stringify(op.kind === 'cancel' ? { reason: op.reason } : op.fields || {}),
    effect: res.effect ? JSON.stringify(res.effect) : null, status: res.status, t,
  });
  if (res.row) await s.save(ctx, op, res.row, seq, t);
  for (const cf of res.conflicts) await s.conflict(ctx, cf, cf.kind, t);

  if (res.effect && res.row) await after(s, ctx, op, res, { eff, origin, t });

  const out = { op_id: op.op_id, status: res.status, server_seq: seq, hlc: eff, ...(cc ? { ignored: cc.ignored } : {}) };
  if (skewed) out.skewed = true;
  if (res.conflicts.length) out.conflicts = res.conflicts.length;
  if (res.status !== 'applied') {
    //  حالِ واقعیِ همین فیلدها، تا دستگاه بی pull خودش را درست کند
    const now = res.row || row || emptyRow();
    const data = op.kind === 'delete' || op.kind === 'cancel' ? now.data : pick(now.data, Object.keys(op.fields || {}));
    out.current = { deleted: !!now.deleted_at, data: hideContact ? require('./customer-contact').omit(op.table, data) : data };
  }
  return out;
}

/** opِ خودِ سرور (تعدیلِ شمارش، برگرداندن) — همان مسیر، بی سنجشِ نقش. */
async function applyServerOp(s, ctx, raw, t) {
  const val = validate(raw, 'server');
  if (!val.ok) throw new Error(`server op invalid: ${val.reason}`);
  return applyOne(s, ctx, val.op, { deviceId: SERVER_DEVICE, origin: 'server', cursor: Infinity, t });
}

async function after(s, ctx, op, res, { eff, origin, t }) {
  const data = res.row.data;
  const set = (res.effect && res.effect.set) || {};

  if (op.spec.duplicate === 'barcodes' && 'barcodes' in set && Array.isArray(data.barcodes) && !res.row.deleted_at) {
    const codes = [...new Set(data.barcodes.filter(b => typeof b === 'string' && b.trim()).map(b => b.trim()))];
    for (const code of codes) {
      const others = await s.barcodeOthers(ctx, op.table, op.row_id, code);
      if (others.length) {
        await openTask(s, ctx, {
          kind: 'duplicate_barcode', table: op.table, rowId: op.row_id, dedupe: `barcode:${code}`, t,
          detail: { barcode: code, rows: [op.row_id, ...others].sort() },
        });
      }
    }
  }

  if (op.spec.duplicate === 'phone' && 'phone' in set && !res.row.deleted_at) {
    const key = normPhone(data.phone);
    if (key) {
      const others = await s.phoneOthers(ctx, op.table, key, op.row_id);
      if (others.length) {
        await openTask(s, ctx, {
          kind: 'duplicate_customer', table: op.table, rowId: op.row_id, dedupe: `phone:${key}`, t,
          detail: { phone: key, rows: [op.row_id, ...others].sort() },
        });
      }
    }
  }

  if (op.kind !== 'insert' || res.status !== 'applied') return;

  if (op.spec.policy === 'moves' && typeof data.productId === 'string' && data.productId) {
    const q = Number(data.qtyMilli) || 0;
    if (data.type === 'count_adjust_late') return;
    //  حرکتی که دیر رسیده ولی HLCش پیش از یک شمارش است: آن شمارش آن را
    //  ندیده بود ⇒ یک «تعدیلِ شمارش»ِ تازه (فقط افزودنی) اثرش را برمی‌دارد
    if (q !== 0) {
      const cnt = await s.lateCount(ctx, data.productId, eff, String(data.countId || ''));
      if (cnt) {
        await applyServerOp(s, ctx, {
          op_id: `srv.late.${shortId(op.row_id)}`, table: 'stockMovements', row_id: `srv.late.${shortId(op.row_id)}`,
          kind: 'insert', hlc: cnt.row_hlc,
          fields: { productId: data.productId, qtyMilli: -q, type: 'count_adjust_late', refId: op.row_id, countId: cnt.row_id },
        }, t);
      }
    }
    if (origin !== 'server' && q < 0) {
      const stock = await s.sumMoves(ctx, data.productId, '');
      if (stock < 0) {
        await openTask(s, ctx, {
          kind: 'negative_stock', table: 'products', rowId: data.productId, dedupe: `neg:${data.productId}`, t,
          detail: { productId: data.productId, stockMilli: stock },
        });
      }
    }
  }

  if (op.spec.policy === 'count' && typeof data.productId === 'string' && Number.isSafeInteger(data.countedQtyMilli)) {
    //  اصلاح = شمرده‌شده − جمعِ حرکت‌های همین کالا با HLC ≤ T از همهٔ دستگاه‌ها.
    //  کالای شمرده‌نشده دست نمی‌خورد (قاعدهٔ RULES).
    const before = await s.sumMoves(ctx, data.productId, eff);
    const adj = data.countedQtyMilli - before;
    if (adj !== 0) {
      await applyServerOp(s, ctx, {
        op_id: `srv.count.${shortId(op.row_id)}`, table: 'stockMovements', row_id: `srv.cadj.${shortId(op.row_id)}`,
        kind: 'insert', hlc: eff,
        fields: { productId: data.productId, qtyMilli: adj, type: 'count_adjust', refId: op.row_id, countId: op.row_id },
      }, t);
    }
  }
}

function resetClock() { clockReady = null; }

module.exports = { SERVER_DEVICE, clock, ready, resetClock, authorize, applyOne, applyServerOp, openTask };
