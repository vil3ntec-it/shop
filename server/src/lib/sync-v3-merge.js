'use strict';
/**
 * Sync v3 — سنجش و ادغامِ یک op، **خالص** و بی دیتابیس (جداشده از `sync-v3.js`، رفتار همان).
 *
 *   validate  شکلِ یک op (جدول، نوع، HLC، فیلدها، برگهٔ تأیید)
 *   merge     اثرِ یک op روی حالِ یک ردیف (HLC، تعارض، حذف، دلتا، لغو)
 */
const { createHash } = require('crypto');
const { canonical } = require('./sync-v1');
const hlc = require('./hlc');
const schema = require('./sync-v3-schema');

const MAX_FIELDS_BYTES = 64 * 1024;
const KINDS = ['insert', 'update', 'delete', 'delta', 'cancel'];
const OP_ID_RE = /^[A-Za-z0-9_.-]{1,80}$/;
const MAX_APPROVAL_BYTES = 2048;
const SERVER_DEVICE = 'server';

function isPlainObject(v) { return v !== null && typeof v === 'object' && !Array.isArray(v); }
function deepEqual(a, b) { return canonical(a === undefined ? null : a) === canonical(b === undefined ? null : b); }
const cmp = hlc.compare;

// ---------- سنجشِ یک op ----------

/**
 * @param origin v3 | v2 | v1 | server — فقط opِ مبدل (v2/v1) و سرور اجازهٔ
 *        «ویرایشِ قدیمی» روی سندِ فقط‌افزودنی دارند، که به صندوق می‌رود.
 * @returns {{ok:true, op} | {ok:false, reason, op_id}}
 */
function validate(raw, origin = 'v3', ctx = undefined) {
  const r = isPlainObject(raw) ? raw : {};
  const opId = typeof r.op_id === 'string' ? r.op_id : '';
  const bad = (reason) => ({ ok: false, reason, op_id: opId.slice(0, 80) });
  const relaxed = origin !== 'v3';

  if (!OP_ID_RE.test(opId)) return bad('bad_op_id');
  const spec = schema.specOf(r.table, ctx);   // درِ دستگاه (ctx): جدولِ پشتِ پرچمِ خاموش (گاوصندوق) همان ناشناختهٔ main
  if (!spec) return bad('unknown_table');
  if (typeof r.row_id !== 'string' || !r.row_id || r.row_id.length > 80) return bad('bad_row_id');
  //  `srv.` مالِ opها و ردیف‌های خودِ سرور است (تعدیلِ شمارش، برگرداندن): دستگاهی
  //  که پیش‌دستی کند می‌توانست تعدیل را «تکراری» کند و بی‌صدا از کار بیندازد
  if (origin !== 'server' && (opId.startsWith('srv.') || r.row_id.startsWith('srv.'))) return bad('reserved_id');
  if (!KINDS.includes(r.kind)) return bad('bad_kind');
  if (!spec.kinds.includes(r.kind) && !(relaxed && (r.kind === 'update' || r.kind === 'delete'))) {
    return bad(spec.policy === 'master' ? 'bad_kind' : 'append_only');
  }
  const stamp = hlc.canonical(r.hlc);
  if (!stamp) return bad('bad_hlc');

  let fields = {};
  if (r.kind === 'delete' || r.kind === 'cancel') {
    if (r.fields !== undefined && r.fields !== null && !(isPlainObject(r.fields) && !Object.keys(r.fields).length)) {
      return bad('unexpected_fields');
    }
  } else {
    if (!isPlainObject(r.fields)) return bad('bad_fields');
    if (!Object.keys(r.fields).length) return bad('empty_fields');
    for (const [k, v] of Object.entries(r.fields)) {
      if (!k || k.length > 64 || k.startsWith('$') || k === 'id') return bad('bad_field_name');
      if (r.kind === 'delta') {
        if (!spec.counters.includes(k)) return bad('not_a_counter');
        if (!Number.isSafeInteger(v)) return bad('not_integer');
        continue;
      }
      if (relaxed) continue;
      if (spec.legacyNames.has(k)) return bad('legacy_field');
      if (spec.derived.includes(k)) return bad('derived_field');
      if (isPlainObject(v) && Object.keys(v).some(x => x.startsWith('$'))) return bad('bad_value');
      if (spec.intFields.has(k) && v !== null && !Number.isSafeInteger(v)) return bad('not_integer');
    }
    if (Buffer.byteLength(JSON.stringify(r.fields), 'utf8') > MAX_FIELDS_BYTES) return bad('fields_too_large');
    //  PRD-07: شکلِ واحدهای خریدِ کالا (v3 و v2 ِ مبدل) — ‎lib/purchase-units.js‎
    if (spec.name === 'products' && r.kind !== 'delta') {
      const why = require('./purchase-units').reasonOf(r.fields);
      if (why) return bad(why);
    }
    fields = r.fields;
    if (r.kind === 'insert' && !relaxed) {
      if (spec.policy === 'moves' && (typeof fields.productId !== 'string' || !fields.productId
        || !Number.isSafeInteger(fields.qtyMilli))) return bad('bad_move');
      if (spec.policy === 'count' && (typeof fields.productId !== 'string' || !fields.productId
        || !Number.isSafeInteger(fields.countedQtyMilli))) return bad('bad_count');
    }
  }
  let reason = '';
  if (r.kind === 'cancel') {
    reason = typeof r.reason === 'string' ? r.reason.trim().slice(0, 200) : '';
    if (!reason) return bad('reason_required');
  }
  const txId = r.tx === undefined || r.tx === null ? '' : String(r.tx);
  if (txId.length > 80) return bad('bad_tx');
  //  «برگهٔ امضاشده»ی تأیید (بخشِ ۱۰.۱) و برگهٔ جدای قرض (`creditApproval`، STF-03) — فقط همراه‌اند؛ سنجش در `authorize`
  const slips = { approval: null };
  for (const k of ['approval', 'creditApproval', 'priceApproval', 'freeLineApproval']) {   //  STF-03: برگه‌های جدای قرض و قیمت (#988)؛ قلمِ آزاد: ۱-۱۱
    if (r[k] === undefined || r[k] === null) continue;
    if (!isPlainObject(r[k]) || Buffer.byteLength(JSON.stringify(r[k]), 'utf8') > MAX_APPROVAL_BYTES) return bad('bad_approval');
    slips[k] = r[k];
  }
  return {
    ok: true,
    op: { op_id: opId, table: spec.name, spec, row_id: r.row_id, kind: r.kind, hlc: stamp, fields, reason, tx: txId,
      fromDelete: relaxed && r.fromDelete === true, ...slips },
  };
}

// ---------- ادغام: خالص، بی دیتابیس ----------

function emptyRow() {
  return {
    data: {}, field_hlc: {}, field_src: {}, row_hlc: '', inserted: false, created_by: '',
    deleted_hlc: null, deleted_at: null,
  };
}

/**
 * یک op را روی حالِ یک ردیف اعمال می‌کند — **خالص**؛ دیتابیس را صدا‌زننده می‌نویسد.
 *
 * @param row   حالِ فعلیِ ردیف یا null
 * @param op    opِ سنجیده؛ `hlc` همان HLCِ مؤثر است و `client_hlc` آنچه آمد
 * @param meta  {seq, deviceId, cursor, t, skewed}
 * @returns {{row:object|null, status:'applied'|'partial'|'conflict', effect:object|null, conflicts:Array}}
 *          `row: null` یعنی ردیف عوض نشد.
 */
function merge(row, op, { seq, deviceId, cursor = 0, t, skewed = false }) {
  const cur = row ? { ...emptyRow(), ...row } : emptyRow();
  const conflicts = [];
  const cf = (o) => conflicts.push({
    table: op.table, row_id: op.row_id, field: '', winner_value: null, loser_value: null,
    winner_hlc: '', loser_hlc: '', winner_op_id: '', loser_op_id: '', winner_device: '', loser_device: '', ...o,
  });
  const loser = { loser_hlc: op.client_hlc || op.hlc, loser_op_id: op.op_id, loser_device: deviceId };
  const spec = op.spec;

  if (op.kind === 'delta') {
    const data = { ...cur.data };
    const inc = {};
    for (const [k, n] of Object.entries(op.fields)) { data[k] = (Number(data[k]) || 0) + n; inc[k] = n; }
    return { row: { ...cur, data }, status: 'applied', effect: { inc }, conflicts };
  }

  /* ---------------- فقط‌افزودنی: سند، دفتر، حرکت، شمارش ---------------- */
  if (spec.policy !== 'master') {
    if (op.kind === 'update' || op.kind === 'delete') {
      //  فقط opِ مبدل به این‌جا می‌رسد: سندِ مالی عوض نمی‌شود، ولی ویرایش گم هم نمی‌شود
      cf({
        kind: 'append_only_edit', field: op.kind === 'delete' ? '*delete*' : '*',
        loser_value: op.kind === 'delete' ? null : op.fields, winner_value: row ? cur.data : null, ...loser,
      });
      return { row: null, status: 'conflict', effect: null, conflicts };
    }
    if (op.kind === 'cancel') {
      if (cur.data.status === 'cancelled') return { row: null, status: 'applied', effect: null, conflicts };
      const set = { status: 'cancelled', cancelReason: op.reason, cancelledAt: hlc.parse(op.hlc).wall };
      return {
        row: { ...cur, data: { ...cur.data, ...set }, created_by: cur.created_by || '' },
        status: 'applied', effect: { set }, conflicts,
      };
    }
    //  insert
    if (cur.inserted) {
      const same = Object.entries(op.fields).every(([k, v]) => spec.counters.includes(k) || deepEqual(cur.data[k], v));
      if (same) return { row: null, status: 'applied', effect: null, conflicts };
      cf({ kind: 'append_only_edit', field: '*', loser_value: op.fields, winner_value: cur.data, ...loser });
      return { row: null, status: 'conflict', effect: null, conflicts };
    }
    const data = require('./sync-v3-time').corrected({ ...op.fields }, op.hlc); //  S28b: «تاریخِ تصحیح‌شده» فقط از سرور
    if (row) {
      //  دلتا یا لغوی که پیش از خودِ سند رسیده بود
      for (const k of spec.counters) if (k in cur.data) data[k] = (Number(data[k]) || 0) + Number(cur.data[k]);
      if (cur.data.status === 'cancelled') {
        for (const k of ['status', 'cancelReason', 'cancelledAt']) if (k in cur.data) data[k] = cur.data[k];
      }
    }
    const fh = {};
    for (const k of Object.keys(data)) fh[k] = op.hlc;
    return {
      row: { ...cur, data, field_hlc: fh, row_hlc: op.hlc, inserted: true, created_by: deviceId },
      status: 'applied', effect: { set: data }, conflicts,
    };
  }

  /* ---------------- اطلاعاتِ پایه: «آخرین نوشتن» با HLC ---------------- */
  if (skewed) {
    cf({
      kind: 'clock_skew', field: op.kind === 'delete' ? '*delete*' : '*',
      loser_value: op.kind === 'delete' ? null : op.fields,
      winner_value: op.kind === 'delete' ? null : pick(cur.data, Object.keys(op.fields)), ...loser,
    });
    return { row: null, status: 'conflict', effect: null, conflicts };
  }

  /*
   *  همگرایی: نتیجه به ترتیبِ رسیدن بستگی ندارد.
   *    • هر فیلد = نوشتهٔ با بزرگ‌ترین HLC (حتی روی ردیفِ حذف‌شده، پنهان)
   *    • HLCِ حذف = بزرگ‌ترین حذف
   *    • ردیف زنده است اگر تازه‌ترین فیلدش از حذف تازه‌تر باشد
   *  پس «ویرایشِ قدیمی‌تر از حذف» ردیف را زنده نمی‌کند و به صندوق می‌رود،
   *  ولی اگر ردیف بعداً زنده شود، همان مقدار را دارد که اگر به‌موقع رسیده بود.
   */
  const wasAlive = !!row && !cur.deleted_at;

  if (op.kind === 'delete') {
    const newest = hlc.newestOf(cur.field_hlc);
    const deletedHlc = !cur.deleted_hlc || cmp(op.hlc, cur.deleted_hlc) > 0 ? op.hlc : cur.deleted_hlc;
    if (newest && cmp(newest, deletedHlc) > 0) {
      //  ویرایشی تازه‌تر از این حذف هست ⇒ ردیف می‌ماند
      cf({ kind: 'stale_delete', field: '*delete*', winner_hlc: newest, ...loser });
      return { row: { ...cur, deleted_hlc: deletedHlc }, status: 'conflict', effect: null, conflicts };
    }
    return {
      row: { ...cur, deleted_hlc: deletedHlc, deleted_at: cur.deleted_at || t },
      status: 'applied', effect: wasAlive ? { deleted: true } : null, conflicts,
    };
  }

  //  insert / update
  const stale = !!cur.deleted_hlc && cmp(op.hlc, cur.deleted_hlc) <= 0;
  const data = { ...cur.data };
  const fh = { ...cur.field_hlc };
  const fs = { ...cur.field_src };
  const set = {};
  const fieldConflicts = [];
  let lost = 0;
  for (const [f, v] of Object.entries(op.fields)) {
    const prevH = fh[f];
    if (!prevH || cmp(op.hlc, prevH) > 0) {
      const prev = fs[f];
      //  نوشتهٔ دستگاهِ دیگری که این دستگاه **ندیده بود** ⇒ بازنده به صندوق
      if (prevH && prev && prev.d !== deviceId && prev.d !== SERVER_DEVICE && Number(prev.s) > cursor
        && Object.prototype.hasOwnProperty.call(data, f) && !deepEqual(data[f], v)) {
        fieldConflicts.push({
          kind: 'lww', field: f, loser_value: data[f], winner_value: v, loser_hlc: prevH, winner_hlc: op.hlc,
          loser_op_id: prev.o || '', winner_op_id: op.op_id, loser_device: prev.d || '', winner_device: deviceId,
        });
      }
      data[f] = v; fh[f] = op.hlc; fs[f] = { d: deviceId, s: seq, o: op.op_id };
      set[f] = v;
    } else if (!deepEqual(data[f], v)) {
      lost++;
      const prev = fs[f] || {};
      fieldConflicts.push({
        kind: 'lww', field: f, loser_value: v, winner_value: data[f], winner_hlc: prevH,
        winner_op_id: prev.o || '', winner_device: prev.d || '', ...loser,
      });
    }
  }
  const anySet = Object.keys(set).length > 0;
  const next = anySet ? {
    ...cur, data, field_hlc: fh, field_src: fs, inserted: true,
    created_by: cur.created_by || deviceId, row_hlc: cur.row_hlc || op.hlc,
  } : null;

  if (stale) {
    //  ویرایشِ قدیمی‌تر از حذف — حتی اگر دیرتر رسیده باشد: پنهان می‌نشیند، زنده نمی‌کند
    //  (ردیفی که ویرایشِ تازه‌تری زنده‌اش کرده، همان فیلدها را آشکارا می‌گیرد)
    cf({ kind: 'stale_after_delete', field: '*', loser_value: op.fields, winner_hlc: cur.deleted_hlc, ...loser });
    return { row: next, status: 'conflict', effect: wasAlive && anySet ? { set } : null, conflicts };
  }
  for (const x of fieldConflicts) cf(x);
  const status = lost === 0 ? 'applied' : anySet ? 'partial' : 'conflict';
  if (!next) return { row: null, status, effect: null, conflicts };
  next.deleted_at = null;
  const revived = !!row && !!cur.deleted_at;
  return {
    row: next,
    status,
    //  ردیفِ زنده‌شده کامل می‌رود: دستگاهی که پاکش کرده بود همه‌اش را لازم دارد
    effect: revived ? { set: data, revived: true } : { set },
    conflicts,
  };
}

/** شناسهٔ کوتاه و پایدار برای ردیف‌های سرور (سقفِ ۸۰ نویسه، بی برخورد). */
function shortId(id) {
  return id.length <= 60 ? id : createHash('sha256').update(id).digest('hex').slice(0, 40);
}

function pick(obj, keys) {
  const out = {};
  for (const k of keys) if (Object.prototype.hasOwnProperty.call(obj, k)) out[k] = obj[k];
  return out;
}

/** شمارهٔ تلفن ⇒ ۹ رقمِ آخر (ارقامِ فارسی و عربی هم)؛ کوتاه‌تر ⇒ ''. */
function normPhone(s) {
  const d = String(s || '')
    .replace(/[۰-۹]/g, (ch) => String(ch.charCodeAt(0) - 0x06F0))
    .replace(/[٠-٩]/g, (ch) => String(ch.charCodeAt(0) - 0x0660))
    .replace(/\D/g, '');
  return d.length >= 9 ? d.slice(-9) : '';
}

module.exports = {
  MAX_FIELDS_BYTES, KINDS, SERVER_DEVICE, isPlainObject, deepEqual, cmp, validate, emptyRow, merge, shortId, pick, normPhone,
};
