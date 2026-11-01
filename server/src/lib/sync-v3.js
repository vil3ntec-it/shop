'use strict';
/**
 * VILL3N Sync v3 — سمتِ سرور، فازِ ۱ (بخشِ ۳.۸؛ SYN-01..08، 10، 11، 13).
 *
 * ── یک نگاه ─────────────────────────────────────────────────────────
 *   push      opهای v3 (یا صفِ v2ِ همان برنامه با `format: "v2"`)، یک تراکنش؛
 *             opهای یک `tx` (سند + حرکتِ کالا + پرداخت) همه یا هیچ (SYN-02)
 *   pull      اثرِ opهای دستگاه‌های دیگر، با سقفِ بایت (۶۴ تا ۲۵۶KB) و ادامه
 *             از cursor (SYN-04)
 *   snapshot  ردیف‌های زنده + cursor، برای دستگاهِ تازه
 *   status    نقطهٔ وضعیت: منتظر، آخرین همگام‌سازیِ موفق، آخرین خطا (SYN-08)
 *   conflicts صندوقِ تعارض: دیدن، نگه داشتن، برگرداندن (SYN-07)
 *
 * ── قاعده‌هایی که این‌جا زنده‌اند ──────────────────────────────────
 *   • **فقط `app=shop`.** v1 و پمپ دست نمی‌خورند؛ از `sync-v1.js` فقط
 *     `canonical` و (در مبدل) `validateOp` خوانده می‌شود.
 *   • **حساب از توکن** (`ctx` را `sync-v1-auth.js` می‌سازد) و هر پرس‌وجو
 *     `app + account_kind + account_id` را شرط می‌کند.
 *   • **op_id یکتاست** ⇒ تکرار «تکراری» است، نه دو بار.
 *   • **داوری با HLC است، نه ترتیبِ رسیدن** ⇒ opی که دیر برسد ولی قدیمی‌تر
 *     باشد نمی‌برد، و نتیجه به ترتیبِ رسیدن بستگی ندارد.
 *   • **ساعتِ کج** (بیش از ۵ دقیقه جلوتر از سرور): ثبت می‌شود، ساعتِ سرور را
 *     جلو نمی‌برد و در «آخرین نوشتن» نمی‌برد ⇒ صندوقِ تعارض.
 *   • **سیاستِ هر جدول** در `sync-v3-schema.js` است.
 */
const { createHash } = require('crypto');
const { one, many, tx, newId, now, query } = require('../db');
const { ApiError, badRequest, notFound, conflict: conflictError, forbidden } = require('../middleware/errors');
const { can } = require('./permissions');
const { canonical } = require('./sync-v1');
const v1migrations = require('./sync-v1-migrations');
const hlc = require('./hlc');
const schema = require('./sync-v3-schema');
const convert = require('./sync-v3-convert');
const toV1 = require('./sync-v3-to-v1');
const approvals = require('./staff-approval');
const safeLedger = require('./safe-ledger');
const safeFlag = require('./safe-flag');
const permCore = require('./perm-core');

const PROTOCOL = 3;
const MAX_OPS = 500;
const MAX_BATCH_BYTES = 256 * 1024;
const MAX_FIELDS_BYTES = 64 * 1024;
const PULL_MIN_BYTES = 64 * 1024;
const PULL_MAX_BYTES = 256 * 1024;
const PULL_MAX_OPS = 2000;
const BRIDGE_BATCH = 1000;
const SERVER_DEVICE = 'server';
const KINDS = ['insert', 'update', 'delete', 'delta', 'cancel'];
const OP_ID_RE = /^[A-Za-z0-9_.-]{1,80}$/;
//  برگرداندنِ مقدارِ بازنده همان اختیارِ «حذف و اصلاحِ دادهٔ دیگران» است (مالک و مدیر)
const RESOLVE_PERMISSION = 'data.delete.any';
const REVERTIBLE = new Set(['lww', 'clock_skew', 'stale_after_delete', 'stale_delete']);
//  وظیفه‌های بررسی؛ سه‌تای آخر opِ پولیِ ردشده‌اند (STF-02/03، `staff-approval.js`)
const TASKS = new Set(['negative_stock', 'duplicate_barcode', 'duplicate_customer',
  'approval_required', 'approval_rejected', 'perm_denied']);
const MAX_APPROVAL_BYTES = 2048;

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

const ACC = 'app=$1 AND account_kind=$2 AND account_id=$3';
const acc = (ctx) => [ctx.app, ctx.accountKind, ctx.accountId];

function isPlainObject(v) { return v !== null && typeof v === 'object' && !Array.isArray(v); }
function deepEqual(a, b) { return canonical(a === undefined ? null : a) === canonical(b === undefined ? null : b); }
const cmp = hlc.compare;

function lock(c, ctx) {
  return c.query('SELECT pg_advisory_xact_lock(hashtext($1))', [`sync-v3:${ctx.app}:${ctx.accountKind}:${ctx.accountId}`]);
}

function requireShop(ctx) {
  if (!ctx || ctx.app !== 'shop' || ctx.accountKind !== 'shop') {
    throw forbidden('همگام‌سازیِ v3 فقط برای دکان است', 'v3_shop_only');
  }
}

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
  const spec = schema.specOf(r.table);
  //  جدولِ پشتِ پرچمِ خاموش (گاوصندوق، `safe-flag.js`) همان ناشناختهٔ main است — فقط درِ دستگاه (ctx دارد)
  if (!spec || (ctx !== undefined && !safeFlag.allowed(spec.name, ctx))) return bad('unknown_table');
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
  //  «برگهٔ امضاشده»ی تأیید (بخشِ ۱۰.۱) — فقط همراه است؛ سنجشش در `authorize`
  let approval = null;
  if (r.approval !== undefined && r.approval !== null) {
    if (!isPlainObject(r.approval) || Buffer.byteLength(JSON.stringify(r.approval), 'utf8') > MAX_APPROVAL_BYTES) {
      return bad('bad_approval');
    }
    approval = r.approval;
  }
  return {
    ok: true,
    op: { op_id: opId, table: spec.name, spec, row_id: r.row_id, kind: r.kind, hlc: stamp, fields, reason, tx: txId,
      fromDelete: relaxed && r.fromDelete === true, approval },
  };
}

// ---------- ادغام: خالص، بی دیتابیس ----------

function emptyRow() {
  return {
    data: {}, field_hlc: {}, field_src: {}, row_hlc: '', inserted: false, created_by: '',
    deleted_hlc: null, deleted_at: null,
  };
}

function maxHlc(map) {
  let best = '';
  for (const h of Object.values(map || {})) if (h && (!best || cmp(h, best) > 0)) best = h;
  return best;
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
    const data = { ...op.fields };
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
    const newest = maxHlc(cur.field_hlc);
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

// ---------- دیتابیس ----------

async function loadRow(c, ctx, table, rowId) {
  const r = (await c.query(
    `SELECT data, field_hlc, field_src, row_hlc, inserted, created_by, deleted_hlc, deleted_at
       FROM sync_v3_rows WHERE ${ACC} AND table_name=$4 AND row_id=$5 FOR UPDATE`,
    [...acc(ctx), table, rowId]
  )).rows[0];
  if (!r) return null;
  return { ...r, deleted_at: r.deleted_at === null ? null : Number(r.deleted_at) };
}

async function saveRow(c, ctx, op, row, seq, t) {
  const matchKey = op.spec.duplicate === 'phone' ? normPhone(row.data.phone) : '';
  await c.query(
    `INSERT INTO sync_v3_rows (app, account_kind, account_id, table_name, row_id, data, field_hlc, field_src,
                               row_hlc, inserted, created_by, deleted_hlc, deleted_at, match_key, updated_seq,
                               created_at, updated_at)
     VALUES ($1,$2,$3,$4,$5,$6::jsonb,$7::jsonb,$8::jsonb,$9,$10,$11,$12,$13,$14,$15,$16,$16)
     ON CONFLICT (app, account_kind, account_id, table_name, row_id) DO UPDATE SET
       data = excluded.data, field_hlc = excluded.field_hlc, field_src = excluded.field_src,
       row_hlc = excluded.row_hlc, inserted = excluded.inserted, created_by = excluded.created_by,
       deleted_hlc = excluded.deleted_hlc, deleted_at = excluded.deleted_at, match_key = excluded.match_key,
       updated_seq = excluded.updated_seq, updated_at = excluded.updated_at`,
    [...acc(ctx), op.table, op.row_id, JSON.stringify(row.data), JSON.stringify(row.field_hlc || {}),
      JSON.stringify(row.field_src || {}), row.row_hlc || '', !!row.inserted, row.created_by || '',
      row.deleted_hlc || null, row.deleted_at ?? null, matchKey, seq, t]
  );
}

async function addConflict(c, ctx, cf, kind, t, { dedupe = '', detail = {} } = {}) {
  if (dedupe) {
    const open = (await c.query(
      `SELECT id FROM sync_v3_conflicts WHERE ${ACC} AND dedupe_key=$4 AND status='open' LIMIT 1`,
      [...acc(ctx), dedupe]
    )).rows[0];
    if (open) {
      await c.query('UPDATE sync_v3_conflicts SET detail=$2::jsonb WHERE id=$1', [open.id, JSON.stringify(detail)]);
      return open.id;
    }
  }
  const id = newId('cf');
  await c.query(
    `INSERT INTO sync_v3_conflicts (id, app, account_kind, account_id, kind, table_name, row_id, field,
                                    winner_value, loser_value, winner_hlc, loser_hlc, winner_op_id, loser_op_id,
                                    winner_device, loser_device, detail, dedupe_key, at)
     VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9::jsonb,$10::jsonb,$11,$12,$13,$14,$15,$16,$17::jsonb,$18,$19)`,
    [id, ...acc(ctx), kind || cf.kind, cf.table, cf.row_id, cf.field || '',
      JSON.stringify(cf.winner_value ?? null), JSON.stringify(cf.loser_value ?? null),
      cf.winner_hlc || '', cf.loser_hlc || '', cf.winner_op_id || '', cf.loser_op_id || '',
      cf.winner_device || '', cf.loser_device || '', JSON.stringify(detail), dedupe, t]
  );
  return id;
}

/** وظیفهٔ بررسی (موجودیِ منفی، بارکد یا مشتریِ تکراری) — برای هر کلید یکی باز. */
function openTask(c, ctx, { kind, table, rowId, dedupe, detail, t }) {
  return addConflict(c, ctx, { table, row_id: rowId }, kind, t, { dedupe, detail });
}

// ---------- دسترسیِ هر op ----------

/**
 * @returns {Promise<null | string | {reason, box, detail}>} دلیلِ رد؛ با `box`
 *          یعنی ردیفی هم در صندوقِ تعارض لازم است (opِ پولیِ ردشده).
 */
async function authorize(c, ctx, op, row, deviceId, origin, groups = null) {
  if (origin === 'v1' || origin === 'server') return null;      //  v1 خودش سنجیده بود (و `v1Guard`)
  const role = ctx.role;
  const own = !!row && row.created_by === deviceId;
  if (op.kind === 'delete' && op.spec.policy === 'master') {
    if (can(role, 'data.delete.any')) return null;
    if (!can(role, 'data.delete.own')) return 'delete_not_allowed';
    return !row || !row.created_by || own ? null : 'delete_not_allowed';
  }
  //  گاوصندوق (PAY-18): فقط درج، شکلِ درست و `safe.move` (جز «از دخل») — `safe-ledger.js`
  if (op.table === safeLedger.TABLE) return safeLedger.judgeV3(ctx, op, row);
  //  تخفیف، لغوِ فاکتور و برگشت: نقش + دو لایه + «برگهٔ امضاشده» (STF-02/03)
  const need = approvals.needOf(op, row);
  if (need) {
    //  برگشت: سقف روی جمعِ سند (docId یا tx)، یک داوری برای همهٔ ردیف‌هایش
    const g = need.perm === 'returns.create' && groups ? groups.get(op) : null;
    if (g) return approvals.judgeGroup(c, ctx, g, { at: approvals.wallOf(g.rows[0].op.hlc), deviceId, path: origin });
    //  قاعدهٔ امروزِ v1 برای نقشی که لغو ندارد (شاگرد): «حذفِ فاکتورِ خودت» از راهِ حذفِ v2
    const fallback = need.perm === 'sales.cancel' && op.fromDelete && own && can(role, 'data.delete.own')
      && permCore.defaultMode(role, 'sales.cancel') === 'deny';
    return approvals.judge(c, ctx, need, approvals.slipOf(op), {
      opId: op.op_id, at: approvals.wallOf(op.hlc), deviceId, path: origin, fallback,
    });
  }
  if (op.kind === 'cancel') {
    if (can(role, op.spec.cancel)) return null;
    //  همان قاعدهٔ v1: «حذفِ ردیفِ خودت» — برای سندِ غیرِ فاکتور
    if (can(role, 'data.delete.own') && own) return null;
    return 'cancel_not_allowed';
  }
  return null;
}

// ---------- اعمالِ یک op ----------

async function nextSeq(c) {
  const r = (await c.query(`SELECT nextval(pg_get_serial_sequence('sync_v3_ops','server_seq')) AS s`)).rows[0];
  return Number(r.s);
}

/**
 * @returns {Promise<object>} نتیجهٔ همین op برای پاسخ:
 *          applied | partial | conflict | duplicate | rejected
 */
async function applyOne(c, ctx, op, { deviceId, origin, cursor = 0, t, groups = null, plan = null }) {
  const dup = (await c.query(
    `SELECT server_seq FROM sync_v3_ops WHERE ${ACC} AND op_id=$4`, [...acc(ctx), op.op_id]
  )).rows[0];
  if (dup) return { op_id: op.op_id, status: 'duplicate', server_seq: Number(dup.server_seq) };

  const row = await loadRow(c, ctx, op.table, op.row_id);
  const denied = await require('./sync-v3-roles').denied(c, ctx, op, origin, plan) || await authorize(c, ctx, op, row, deviceId, origin, groups);
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

  //  نگهبانِ ساعت: سرور با HLCِ کج جلو نمی‌رود و آن را «مؤثر» نمی‌کند
  let eff = op.hlc;
  let skewed = false;
  if (origin !== 'server') {
    const r = clock.recv(op.hlc);
    if (r.skewed) { skewed = true; eff = clock.tick(); }
  } else {
    clock.observe(op.hlc);
  }

  const seq = await nextSeq(c);
  const res = merge(row, { ...op, hlc: eff, client_hlc: op.hlc }, { seq, deviceId, cursor, t, skewed });

  await c.query(
    `INSERT INTO sync_v3_ops (app, account_kind, account_id, op_id, server_seq, device_id, origin, tx_id, hlc,
                              client_hlc, skewed, table_name, row_id, kind, fields, effect, status, received_at)
     VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15::jsonb,$16::jsonb,$17,$18)`,
    [...acc(ctx), op.op_id, seq, deviceId, origin, op.tx || '', eff, op.hlc, skewed, op.table, op.row_id, op.kind,
      JSON.stringify(op.kind === 'cancel' ? { reason: op.reason } : op.fields || {}),
      res.effect ? JSON.stringify(res.effect) : null, res.status, t]
  );
  if (res.row) await saveRow(c, ctx, op, res.row, seq, t);
  for (const cf of res.conflicts) await addConflict(c, ctx, cf, cf.kind, t);

  if (res.effect && res.row) await after(c, ctx, op, res, { eff, origin, t });

  const out = { op_id: op.op_id, status: res.status, server_seq: seq, hlc: eff };
  if (skewed) out.skewed = true;
  if (res.conflicts.length) out.conflicts = res.conflicts.length;
  if (res.status !== 'applied') {
    //  حالِ واقعیِ همین فیلدها، تا دستگاه بی pull خودش را درست کند
    const now = res.row || row || emptyRow();
    out.current = {
      deleted: !!now.deleted_at,
      data: op.kind === 'delete' || op.kind === 'cancel' ? now.data : pick(now.data, Object.keys(op.fields || {})),
    };
  }
  return out;
}

/** opِ خودِ سرور (تعدیلِ شمارش، برگرداندن) — همان مسیر، بی سنجشِ نقش. */
async function applyServerOp(c, ctx, raw, t) {
  const val = validate(raw, 'server');
  if (!val.ok) throw new Error(`server op invalid: ${val.reason}`);
  return applyOne(c, ctx, val.op, { deviceId: SERVER_DEVICE, origin: 'server', cursor: Infinity, t });
}

async function sumMoves(c, ctx, productId, upTo = '') {
  const r = (await c.query(
    `SELECT COALESCE(SUM((data->>'qtyMilli')::bigint), 0)::bigint AS s FROM sync_v3_rows
      WHERE ${ACC} AND table_name='stockMovements' AND inserted AND data->>'productId' = $4
        AND ($5 = '' OR row_hlc <= $5)`,
    [...acc(ctx), productId, upTo]
  )).rows[0];
  return Number(r.s);
}

/** پیامدهای یک op: تکراری‌ها، موجودیِ منفی، تعدیلِ شمارش. */
async function after(c, ctx, op, res, { eff, origin, t }) {
  const data = res.row.data;
  const set = (res.effect && res.effect.set) || {};

  if (op.spec.duplicate === 'barcodes' && 'barcodes' in set && Array.isArray(data.barcodes) && !res.row.deleted_at) {
    const codes = [...new Set(data.barcodes.filter(b => typeof b === 'string' && b.trim()).map(b => b.trim()))];
    for (const code of codes) {
      const others = (await c.query(
        `SELECT row_id FROM sync_v3_rows WHERE ${ACC} AND table_name=$4 AND row_id <> $5 AND deleted_at IS NULL
            AND data->'barcodes' @> $6::jsonb ORDER BY row_id`,
        [...acc(ctx), op.table, op.row_id, JSON.stringify([code])]
      )).rows.map(r => r.row_id);
      if (others.length) {
        await openTask(c, ctx, {
          kind: 'duplicate_barcode', table: op.table, rowId: op.row_id, dedupe: `barcode:${code}`, t,
          detail: { barcode: code, rows: [op.row_id, ...others].sort() },
        });
      }
    }
  }

  if (op.spec.duplicate === 'phone' && 'phone' in set && !res.row.deleted_at) {
    const key = normPhone(data.phone);
    if (key) {
      const others = (await c.query(
        `SELECT row_id FROM sync_v3_rows WHERE ${ACC} AND table_name=$4 AND match_key=$5 AND row_id <> $6
            AND deleted_at IS NULL ORDER BY row_id`,
        [...acc(ctx), op.table, key, op.row_id]
      )).rows.map(r => r.row_id);
      if (others.length) {
        await openTask(c, ctx, {
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
      const cnt = (await c.query(
        `SELECT row_id, row_hlc FROM sync_v3_rows
          WHERE ${ACC} AND table_name='stockCounts' AND inserted AND data->>'productId' = $4
            AND row_hlc >= $5 AND row_id <> $6
          ORDER BY row_hlc DESC LIMIT 1`,
        [...acc(ctx), data.productId, eff, String(data.countId || '')]
      )).rows[0];
      if (cnt) {
        await applyServerOp(c, ctx, {
          op_id: `srv.late.${shortId(op.row_id)}`, table: 'stockMovements', row_id: `srv.late.${shortId(op.row_id)}`,
          kind: 'insert', hlc: cnt.row_hlc,
          fields: { productId: data.productId, qtyMilli: -q, type: 'count_adjust_late', refId: op.row_id, countId: cnt.row_id },
        }, t);
      }
    }
    if (origin !== 'server' && q < 0) {
      const stock = await sumMoves(c, ctx, data.productId);
      if (stock < 0) {
        await openTask(c, ctx, {
          kind: 'negative_stock', table: 'products', rowId: data.productId, dedupe: `neg:${data.productId}`, t,
          detail: { productId: data.productId, stockMilli: stock },
        });
      }
    }
  }

  if (op.spec.policy === 'count' && typeof data.productId === 'string' && Number.isSafeInteger(data.countedQtyMilli)) {
    //  اصلاح = شمرده‌شده − جمعِ حرکت‌های همین کالا با HLC ≤ T از همهٔ دستگاه‌ها.
    //  کالای شمرده‌نشده دست نمی‌خورد (قاعدهٔ RULES).
    const before = await sumMoves(c, ctx, data.productId, eff);
    const adj = data.countedQtyMilli - before;
    if (adj !== 0) {
      await applyServerOp(c, ctx, {
        op_id: `srv.count.${shortId(op.row_id)}`, table: 'stockMovements', row_id: `srv.cadj.${shortId(op.row_id)}`,
        kind: 'insert', hlc: eff,
        fields: { productId: data.productId, qtyMilli: adj, type: 'count_adjust', refId: op.row_id, countId: op.row_id },
      }, t);
    }
  }
}

// ---------- دستگاه ----------

async function touchDevice(q, ctx, deviceId, p) {
  const t = now();
  await q.query(
    `INSERT INTO sync_v3_devices (app, account_kind, account_id, device_id, cursor, last_push_at, last_pull_at,
                                  last_ok_at, last_error, last_error_at, queued, clock_offset_ms, app_version, last_seen_at)
     VALUES ($1,$2,$3,$4, COALESCE($5,0), $6, $7, $8, COALESCE($9,''), $10, COALESCE($11,0), COALESCE($12,0),
             COALESCE($13,''), $14)
     ON CONFLICT (app, account_kind, account_id, device_id) DO UPDATE SET
       cursor          = GREATEST(sync_v3_devices.cursor, COALESCE(excluded.cursor, 0)),
       last_push_at    = COALESCE($6, sync_v3_devices.last_push_at),
       last_pull_at    = COALESCE($7, sync_v3_devices.last_pull_at),
       last_ok_at      = COALESCE($8, sync_v3_devices.last_ok_at),
       last_error      = CASE WHEN $9::text IS NULL THEN sync_v3_devices.last_error ELSE $9 END,
       last_error_at   = CASE WHEN $9::text IS NULL THEN sync_v3_devices.last_error_at ELSE $10 END,
       queued          = CASE WHEN $11::int IS NULL THEN sync_v3_devices.queued ELSE $11 END,
       clock_offset_ms = CASE WHEN $12::bigint IS NULL THEN sync_v3_devices.clock_offset_ms ELSE $12 END,
       app_version     = CASE WHEN $13::text IS NULL OR $13 = '' THEN sync_v3_devices.app_version ELSE $13 END,
       last_seen_at    = excluded.last_seen_at`,
    [...acc(ctx), deviceId, p.cursor ?? null, p.lastPushAt ?? null, p.lastPullAt ?? null, p.lastOkAt ?? null,
      p.lastError ?? null, p.lastError === undefined || p.lastError === null ? null : (p.lastError ? t : null),
      p.queued ?? null, p.clockOffsetMs ?? null, p.appVersion ?? null, t]
  );
}

async function headSeq(q, ctx) {
  const r = (await q.query(
    `SELECT COALESCE(MAX(server_seq),0)::bigint AS head FROM sync_v3_ops WHERE ${ACC}`, acc(ctx)
  )).rows[0];
  return Number(r.head);
}

const dbq = { query: (s, p) => query(s, p) };

function intOrNull(v, min, max) {
  if (v === undefined || v === null || v === '') return null;
  const n = Number(v);
  if (!Number.isFinite(n)) return null;
  return Math.min(max, Math.max(min, Math.trunc(n)));
}

function rawId(raw) {
  return isPlainObject(raw) ? String(raw.op_id ?? raw.opId ?? '').slice(0, 80) : '';
}

function errorText(v) {
  if (v === undefined || v === null) return null;
  return String(v).replace(/[\u0000-\u001f\u007f]/g, ' ').slice(0, 300);
}

// ---------- Push ----------

/**
 * @param body {device_id, protocol?, format?: 'v3'|'v2', schema_version?, ops, queued?, last_error?, clock_offset_ms?}
 * @throws 426 اگر برنامه از سرور جلوتر باشد — و هیچ چیزی اعمال نمی‌شود (opها روی برنامه می‌مانند)
 */
async function push(ctx, { deviceId, protocol = PROTOCOL, format = 'v3', schemaVersion = 0, ops, appVersion = '',
  queued = null, lastError, clockOffsetMs = null }) {
  requireShop(ctx);
  if (deviceId === SERVER_DEVICE) throw badRequest('این شناسهٔ دستگاه رزرو است', 'bad_device');
  const proto = Number(protocol) || PROTOCOL;
  if (proto > PROTOCOL) {
    throw new ApiError(426, 'UPGRADE_REQUIRED', `سرور پروتکلِ ${PROTOCOL} را می‌شناسد و برنامه ${proto} می‌فرستد؛ سرور باید به‌روز شود`);
  }
  if (!Array.isArray(ops)) throw badRequest('فهرستِ opها درست نیست', 'bad_ops');
  if (ops.length > MAX_OPS) throw badRequest(`حداکثر ${MAX_OPS} op در هر دسته`, 'batch_too_large');
  if (Buffer.byteLength(JSON.stringify(ops), 'utf8') > MAX_BATCH_BYTES) {
    throw badRequest('دسته بزرگ‌تر از ۲۵۶ کیلوبایت است', 'batch_too_large');
  }

  //  ورودی ⇒ فهرستِ واحدهای اعمال: هر واحد چند op که همه یا هیچ
  const results = new Array(ops.length);
  const units = [];                    //  [{tx, items: [{i, op}]}]
  const byTx = new Map();
  const origin = format === 'v2' ? 'v2' : 'v3';
  if (origin === 'v2') {
    const sv = Number(schemaVersion);
    const cur = v1migrations.currentVersion('shop', ctx);
    if (!Number.isInteger(sv) || sv < 1) throw badRequest('schema_version لازم است', 'bad_schema_version');
    if (sv > cur) {
      throw new ApiError(426, 'UPGRADE_REQUIRED', `سرور schema نسخهٔ ${cur} را می‌شناسد و برنامه نسخهٔ ${sv} می‌فرستد`);
    }
  }
  ops.forEach((raw, i) => {
    let list;
    if (origin === 'v2') {
      const conv = convert.fromV2(raw, { schemaVersion, deviceId, index: i });
      if (!conv.ok) { results[i] = { op_id: conv.op_id, status: 'rejected', reason: conv.reason }; return; }
      if (!conv.ops.length) {
        //  فقط مانده یا نشانِ محلی داشت — چیزی برای دفتر نیست
        results[i] = { op_id: rawId(raw), status: 'applied', converted: [], dropped: conv.dropped };
        return;
      }
      list = conv.ops.map((o) => validate(o, 'v2', ctx));
      const bad = list.find(v => !v.ok);
      if (bad) { results[i] = { op_id: rawId(raw), status: 'rejected', reason: bad.reason }; return; }
      units.push({ tx: '', items: [{ i, ops: list.map(v => v.op), v2: true, dropped: conv.dropped }] });
      return;
    }
    const v = validate(raw, 'v3', ctx);
    if (!v.ok) {
      results[i] = { op_id: v.op_id, status: 'rejected', reason: v.reason };
      //  یک op ِ نادرست کلِ tx را رد می‌کند
      const t = isPlainObject(raw) && raw.tx ? String(raw.tx) : '';
      if (t) {
        const u = byTx.get(t) || { tx: t, items: [], broken: true };
        u.broken = true;
        if (!byTx.has(t)) { byTx.set(t, u); units.push(u); }
      }
      return;
    }
    if (v.op.tx) {
      let u = byTx.get(v.op.tx);
      if (!u) { u = { tx: v.op.tx, items: [] }; byTx.set(v.op.tx, u); units.push(u); }
      u.items.push({ i, ops: [v.op] });
    } else {
      units.push({ tx: '', items: [{ i, ops: [v.op] }] });
    }
  });

  //  سندهای برگشتِ این دسته (یک برگه و یک تصمیم؛ سقفِ تجمعیِ فاکتور)، و برنامهٔ کهنهٔ بی tx:
  //  هر سندِ پولی با همراه‌هایش یک واحد (همه یا هیچ)
  const groups = approvals.returnGroupsOf(units.flatMap((u) => u.items.flatMap((it) => it.ops)));
  const plan = approvals.bundleV3(units, groups);
  //  همراهِ بی tx ِ سندی که در دستهٔ پیشینِ همین دستگاه رد شد ⇒ همان رد
  await approvals.holdV3Units(ctx, deviceId, plan);

  //  پلِ v3 ⇒ v1 (پرچمِ دکان): بیشترِ عقب‌ماندگیِ v1 ⇒ v3 بیرونِ تراکنشِ push
  const toV1On = await toV1.enabled(ctx);
  if (toV1On) await catchUp(ctx, 3, true);
  await ready();
  const t = now();

  const out = await tx(async (c) => {
    await lock(c, ctx);
    //  سقفِ تجمعیِ برگشت: از خواندنِ جمع تا نوشتن در همین تراکنش (با پایانش آزاد)
    if (groups) await c.query('SELECT pg_advisory_xact_lock(hashtext($1))', [approvals.returnsLockKey(ctx.accountId)]);
    //  روشن: قفلِ v1 هم، و v1 ⇒ v3 کامل پیش از اعمال ⇒ داوریِ این opها همهٔ v1 را دیده است
    const v1 = toV1On ? await openToV1(c, ctx, t) : null;
    const dev = (await c.query(
      `SELECT cursor FROM sync_v3_devices WHERE ${ACC} AND device_id=$4`, [...acc(ctx), deviceId]
    )).rows[0];
    const cursor = dev ? Number(dev.cursor) : 0;
    let applied = 0;
    let sp = 0;

    //  دنبالهٔ tx ای که سرش در دستهٔ پیشینِ همین دستگاه با سقف/تأیید رد شد (tx ِ بزرگ‌تر از یک
    //  دسته) ⇒ همان رد — فقط صندوقِ باز، تا یک روز، و تا وقتی سرش ننشسته
    const txs = ctx.role === 'owner' ? [] : plan.filter((u) => u.tx && !u.broken).map((u) => u.tx);
    if (txs.length) {
      const held = await approvals.heldTxs(c, ctx, deviceId, txs);
      for (const u of plan) {
        const heads = u.tx ? held.get(u.tx) : null;
        if (!heads) continue;
        //  همان tx دوباره (پاسخ گم شد) ⇒ دوباره داوری می‌شود، نه «دنباله»
        const again = u.items.some((it) => it.ops.some((o) => heads.has(o.op_id)));
        if (!again) u.broken = true;
      }
    }

    for (const unit of plan) {
      if (unit.broken) {
        for (const it of unit.items) results[it.i] = { op_id: it.ops[0].op_id, status: 'rejected', reason: 'tx_rejected' };
        continue;
      }
      const name = `u${++sp}`;
      await c.query(`SAVEPOINT ${name}`);
      const local = [];
      let failed = null;
      for (const it of unit.items) {
        const parts = [];
        for (const op of it.ops) {
          const r = await applyOne(c, ctx, op, { deviceId, origin, cursor, t, groups, plan });
          parts.push(r);
          if (r.status === 'rejected') { failed = r; break; }
        }
        local.push({ it, parts });
        if (failed) break;
      }
      if (failed) {
        await c.query(`ROLLBACK TO SAVEPOINT ${name}`);
        //  opِ پولیِ ردشده هرگز بی‌صدا نمی‌رود: صندوقِ تعارض برای صاحب دکان
        if (failed.box) {
          const b = failed.box;
          await addConflict(c, ctx, { table: b.table, row_id: b.row_id, loser_op_id: b.op_id, loser_device: deviceId },
            b.kind, t, { dedupe: `perm:${b.op_id}`, detail: {
              ...b.detail,
              //  نشانی‌های سند برای همراهِ دستهٔ بعد (v3 ِ بی tx، `holdV3Units`) و دنبالهٔ همان tx
              ...approvals.holdInfoV3(unit.items.flatMap((it) => it.ops).find((o) => o.op_id === b.op_id) || {}, groups),
              ...(unit.tx ? { tx: unit.tx } : {}),
            } });
        }
        for (const it of unit.items) {
          const mine = local.find(l => l.it === it);
          const culprit = mine && mine.parts.find(p => p.status === 'rejected');
          const opId = it.v2 ? rawId(ops[it.i]) : it.ops[0].op_id;
          results[it.i] = { op_id: opId, status: 'rejected', reason: culprit ? culprit.reason : 'tx_rejected' };
        }
        continue;
      }
      await c.query(`RELEASE SAVEPOINT ${name}`);
      for (const { it, parts } of local) {
        if (!it.v2) {
          results[it.i] = parts[0];
        } else {
          //  یک op v2 ⇒ یک پاسخ، با جزئیاتِ opهای v3ی که از آن ساخته شد
          const statuses = parts.map(p => p.status);
          const status = statuses.every(s => s === 'duplicate') ? 'duplicate'
            : statuses.every(s => s === 'applied' || s === 'duplicate') ? 'applied'
              : statuses.every(s => s === 'conflict') ? 'conflict' : 'partial';
          results[it.i] = { op_id: rawId(ops[it.i]), status, converted: parts };
          if (it.dropped.length) results[it.i].dropped = it.dropped;
        }
        for (const p of parts) if (p.status === 'applied' || p.status === 'partial') applied++;
      }
    }

    //  همان تراکنش: اثرِ همین opها در `oplog`/`sync_rows`ِ v1 (گوشی‌های v1 در pullِ بعدی می‌بینند)
    if (v1 && v1.ready) await drainToV1(c, ctx, t);

    await touchDevice(c, ctx, deviceId, {
      lastPushAt: t, lastOkAt: t, queued: intOrNull(queued, 0, 10_000_000), appVersion,
      lastError: errorText(lastError), clockOffsetMs: intOrNull(clockOffsetMs, -1e12, 1e12),
    });
    return { results, applied, head: await headSeq(c, ctx) };
  });
  return { ...out, protocol: PROTOCOL, server_time: now(), hlc: clock.peek() };
}

// ---------- پلِ v1 ⇒ v3 ----------

/**
 * opهای تازهٔ `oplog`ِ v1ِ همین دکان را به v3 می‌آورد — **فقط می‌خواند**.
 * HLC از زمانِ رسیدن به سرورِ v1 ساخته می‌شود، پس برندهٔ هر فیلد همان
 * برندهٔ v1 است. op_id همان است ⇒ آنچه برنامه از راهِ v3 هم فرستاده تکراری است.
 *
 * پلِ v3 ⇒ v1 (`sync-v3-to-v1.js`): ردیفی از `oplog` که خودِ آن پل نوشته
 * برنمی‌گردد (بی حلقه)، و HLCِ opِ v1ِ بعد از آن از HLCِ آن ردیف کوچک‌تر
 * نیست (تا «دیرتر رسیده در v1» در v3 هم دیرتر باشد). بی ردیفِ پل، هر دو بی‌اثرند.
 *
 * صدا‌زننده قفلِ حساب را گرفته است.
 */
async function importV1(c, ctx, { max = BRIDGE_BATCH, t }) {
  const b = (await c.query(`SELECT v1_seq FROM sync_v3_bridge WHERE ${ACC}`, acc(ctx))).rows[0];
  const from = b ? Number(b.v1_seq) : 0;
  const rows = (await c.query(
    `SELECT o.op_id, o.device_id, o.server_seq, o.client_ts, o.table_name, o.row_id, o.op_type, o.fields,
            o.schema_version, o.received_at, x.hlc AS out_hlc
       FROM oplog o
       LEFT JOIN sync_v3_v1_out x ON x.app=o.app AND x.account_kind=o.account_kind AND x.account_id=o.account_id
                                 AND x.v1_seq=o.server_seq
      WHERE o.app=$1 AND o.account_kind=$2 AND o.account_id=$3 AND o.server_seq > $4
      ORDER BY o.server_seq ASC LIMIT $5`,
    [...acc(ctx), from, max]
  )).rows;
  if (!rows.length) return { imported: 0, behind: false, v1_seq: from };
  let floor = await toV1.floorBefore(c, ctx, from);
  let imported = 0;
  for (const r of rows) {
    const seq = Number(r.server_seq);
    if (r.out_hlc !== null && r.out_hlc !== undefined) {
      //  نوشتهٔ خودِ پلِ v3 ⇒ v1: برنمی‌گردد، فقط کفِ HLC را بالا می‌برد
      if (r.out_hlc && (!floor || cmp(r.out_hlc, floor) > 0)) floor = r.out_hlc;
      continue;
    }
    const node = `v1.${String(seq).padStart(15, '0')}`;
    const conv = convert.fromV2({
      op_id: r.op_id, table: r.table_name, row_id: r.row_id, type: r.op_type,
      fields: r.op_type === 'delete' ? undefined : r.fields, ts: Number(r.client_ts),
    }, {
      schemaVersion: r.schema_version, deviceId: r.device_id,
      hlc: toV1.aboveFloor(hlc.format({ wall: Number(r.received_at), counter: 0, node }), floor, node),
    });
    if (!conv.ok) continue;
    for (const o of conv.ops) {
      const v = validate(o, 'v1');
      if (!v.ok) continue;
      const res = await applyOne(c, ctx, v.op, { deviceId: r.device_id || 'v1', origin: 'v1', cursor: Infinity, t });
      if (res.status !== 'duplicate' && res.status !== 'rejected') imported++;
    }
  }
  const last = Number(rows[rows.length - 1].server_seq);
  await setV1Seq(c, ctx, last, t);
  return { imported, behind: rows.length >= max, v1_seq: last };
}

function setV1Seq(c, ctx, seq, t) {
  return c.query(
    `INSERT INTO sync_v3_bridge (app, account_kind, account_id, v1_seq, updated_at) VALUES ($1,$2,$3,$4,$5)
     ON CONFLICT (app, account_kind, account_id) DO UPDATE SET v1_seq = excluded.v1_seq, updated_at = excluded.updated_at`,
    [...acc(ctx), seq, t]
  );
}

/**
 * پلِ v3 ⇒ v1 در همین تراکنش (پرچمِ دکان روشن)، گامِ اول: قفلِ v1 (بعد از قفلِ
 * v3) و رساندنِ **کاملِ** پلِ v1 ⇒ v3. اگر کامل نرسید، این بار چیزی به v1 نمی‌رود
 * (داوری نادرست می‌شد) — صندوقِ خروجی پایدار است و دفعهٔ بعد می‌رود.
 * @returns {{imported:number, ready:boolean}}
 */
async function openToV1(c, ctx, t, { rounds = 10, max = BRIDGE_BATCH } = {}) {
  await toV1.lockV1(c, ctx);
  let imported = 0;
  for (let i = 0; i < rounds; i++) {
    const r = await importV1(c, ctx, { t, max });
    imported += r.imported;
    if (!r.behind) return { imported, ready: true };
  }
  return { imported, ready: false };
}

/** گامِ دوم: opهای v3 به v1؛ پلِ v1 ⇒ v3 از سرِ دفتر ادامه می‌دهد (ردیف‌های تازه همه از همین پل‌اند). */
async function drainToV1(c, ctx, t) {
  const drained = await toV1.drain(c, ctx, { t });
  if (drained.written) {
    const h = (await c.query(`SELECT COALESCE(MAX(server_seq),0)::bigint AS head FROM oplog WHERE ${ACC}`, acc(ctx))).rows[0];
    await setV1Seq(c, ctx, Number(h.head), t);
  }
  return drained;
}

async function bridge(ctx, { max = BRIDGE_BATCH, toV1On } = {}) {
  requireShop(ctx);
  const out = toV1On === undefined ? await toV1.enabled(ctx) : toV1On;
  //  پیش‌سنجِ بی قفل: بیشترِ وقت‌ها چیزی تازه نیست و قفلِ حساب لازم نیست
  const fresh = await one(
    `SELECT 1 AS x FROM oplog WHERE ${ACC} AND server_seq > COALESCE(
       (SELECT v1_seq FROM sync_v3_bridge WHERE ${ACC}), 0) LIMIT 1`, acc(ctx)
  );
  if (!fresh && !(out && await toV1.pending(dbq, ctx))) return { imported: 0, behind: false };
  await ready();
  const t = now();
  return tx(async (c) => {
    await lock(c, ctx);
    if (!out) return importV1(c, ctx, { max, t });
    const o = await openToV1(c, ctx, t, { rounds: 1, max });
    if (!o.ready) return { imported: o.imported, behind: true };
    return { imported: o.imported, behind: false, to_v1: await drainToV1(c, ctx, t) };
  });
}

async function catchUp(ctx, rounds = 3, toV1On) {
  let r = { behind: false };
  if (toV1On === undefined) toV1On = await toV1.enabled(ctx);
  for (let i = 0; i < rounds; i++) {
    r = await bridge(ctx, { toV1On });
    if (!r.behind) break;
  }
  return r;
}

// ---------- Pull ----------

async function pull(ctx, { deviceId, since = 0, maxBytes = PULL_MIN_BYTES, limit = PULL_MAX_OPS }) {
  requireShop(ctx);
  const bridged = await catchUp(ctx);
  const from = Math.max(0, Number(since) || 0);
  const budget = Math.min(Math.max(Number(maxBytes) || PULL_MIN_BYTES, PULL_MIN_BYTES), PULL_MAX_BYTES);
  const cap = Math.min(Math.max(1, Number(limit) || PULL_MAX_OPS), PULL_MAX_OPS);
  //  سرِ دفتر **پیش از** خواندن (همان درسِ v1): opی که وسطِ خواندن برسد دفعهٔ بعد می‌آید
  const head = await headSeq(dbq, ctx);
  const rows = await many(
    `SELECT op_id, server_seq, device_id, origin, hlc, table_name, row_id, kind, effect
       FROM sync_v3_ops WHERE ${ACC} AND server_seq > $4 AND server_seq <= $5 AND device_id <> $6
        AND effect IS NOT NULL
      ORDER BY server_seq ASC LIMIT $7`,
    [...acc(ctx), from, head, deviceId, cap + 1]
  );
  const page = [];
  let bytes = 0;
  let hasMore = false;
  for (const r of rows) {
    if (page.length >= cap) { hasMore = true; break; }
    const item = {
      op_id: r.op_id, seq: Number(r.server_seq), device_id: r.device_id, origin: r.origin, hlc: r.hlc,
      table: r.table_name, row_id: r.row_id, kind: r.kind, effect: r.effect,
    };
    const size = Buffer.byteLength(JSON.stringify(item), 'utf8');
    if (page.length && bytes + size > budget) { hasMore = true; break; }
    page.push(item);
    bytes += size;
  }
  const cursor = hasMore ? page[page.length - 1].seq : Math.max(head, from);
  const t = now();
  await touchDevice(dbq, ctx, deviceId, { cursor, lastPullAt: t, lastOkAt: t });
  return {
    ops: page, cursor, has_more: hasMore || !!bridged.behind, head, bytes,
    protocol: PROTOCOL, server_time: now(), hlc: clock.peek(),
  };
}

// ---------- Snapshot ----------

async function snapshot(ctx) {
  requireShop(ctx);
  const bridged = await catchUp(ctx, 10);
  return tx(async (c) => {
    try { await c.query('SET TRANSACTION ISOLATION LEVEL REPEATABLE READ'); } catch { /* بی این هم می‌خواند */ }
    const head = await headSeq(c, ctx);
    const rows = (await c.query(
      `SELECT table_name, row_id, data FROM sync_v3_rows WHERE ${ACC} AND deleted_at IS NULL AND inserted
        ORDER BY table_name, row_id`, acc(ctx)
    )).rows;
    const tables = {};
    for (const r of rows) (tables[r.table_name] ||= []).push({ id: r.row_id, data: r.data });
    return {
      protocol: PROTOCOL, cursor: head, rows: rows.length, tables,
      complete: !bridged.behind, server_time: now(), hlc: clock.peek(),
    };
  });
}

// ---------- وضعیت (SYN-08، SYN-10) ----------

function shapeDevice(d, head) {
  return {
    device_id: d.device_id, cursor: Number(d.cursor),
    behind: Math.max(0, head - Number(d.cursor)),
    last_push_at: d.last_push_at === null ? null : Number(d.last_push_at),
    last_pull_at: d.last_pull_at === null ? null : Number(d.last_pull_at),
    last_ok_at: d.last_ok_at === null ? null : Number(d.last_ok_at),
    last_error: d.last_error || '',
    last_error_at: d.last_error_at === null ? null : Number(d.last_error_at),
    queued: Number(d.queued),
    //  SYN-10: تا صفر نشده، خروج و حذفِ دستگاه فقط با خروجیِ اجباری
    unsynced: Number(d.queued) > 0,
    clock_offset_ms: Number(d.clock_offset_ms),
    app_version: d.app_version || '',
    last_seen_at: Number(d.last_seen_at),
  };
}

async function status(ctx, deviceId = '') {
  requireShop(ctx);
  const toV1On = await toV1.enabled(ctx);
  await bridge(ctx, { toV1On });
  const head = await headSeq(dbq, ctx);
  const devs = await many(`SELECT * FROM sync_v3_devices WHERE ${ACC} ORDER BY last_seen_at DESC LIMIT 200`, acc(ctx));
  const mine = devs.find(d => d.device_id === deviceId) || null;
  const cursor = mine ? Number(mine.cursor) : 0;
  const counts = await one(
    `SELECT
       (SELECT COUNT(*)::int FROM sync_v3_ops WHERE ${ACC} AND server_seq > $4 AND device_id <> $5
          AND effect IS NOT NULL) AS pending,
       (SELECT COUNT(*)::int FROM sync_v3_conflicts WHERE ${ACC} AND status='open') AS open,
       (SELECT COALESCE(MAX(server_seq),0)::bigint FROM oplog WHERE ${ACC}) AS v1_head,
       (SELECT COALESCE(MAX(v1_seq),0)::bigint FROM sync_v3_bridge WHERE ${ACC}) AS v1_seq`,
    [...acc(ctx), cursor, deviceId]
  );
  const seeAll = can(ctx.role, 'members.view');
  return {
    protocol: PROTOCOL, head, server_time: now(), hlc: clock.peek(), skew_limit_ms: hlc.SKEW_MS,
    device: mine ? { ...shapeDevice(mine, head), pending: counts.pending } : { device_id: deviceId, pending: counts.pending },
    conflicts_open: counts.open,
    bridge: {
      v1_head: Number(counts.v1_head), v1_seq: Number(counts.v1_seq), caught_up: Number(counts.v1_seq) >= Number(counts.v1_head),
      //  پلِ v3 ⇒ v1 (پرچمِ دکانیِ `shop.sync_v3_to_v1`)
      to_v1: toV1On ? { enabled: true, caught_up: !(await toV1.pending(dbq, ctx)) } : { enabled: false },
    },
    devices: (seeAll ? devs : devs.filter(d => d.device_id === deviceId)).map(d => shapeDevice(d, head)),
  };
}

/** گزارشِ برنامه: تعدادِ منتظر، خطای آخر، اختلافِ ساعت — بی op. */
async function report(ctx, deviceId, { queued, lastError, clockOffsetMs, appVersion = '' } = {}) {
  requireShop(ctx);
  await touchDevice(dbq, ctx, deviceId, {
    queued: intOrNull(queued, 0, 10_000_000), lastError: errorText(lastError),
    clockOffsetMs: intOrNull(clockOffsetMs, -1e12, 1e12), appVersion,
  });
  return { server_time: now(), hlc: clock.peek() };
}

// ---------- صندوقِ تعارض (SYN-07) ----------

function shapeConflict(r) {
  return {
    id: r.id, kind: r.kind, task: TASKS.has(r.kind), revertible: REVERTIBLE.has(r.kind) && r.status === 'open',
    table: r.table_name, row_id: r.row_id, field: r.field,
    winner_value: r.winner_value, loser_value: r.loser_value,
    winner_hlc: r.winner_hlc, loser_hlc: r.loser_hlc,
    winner_op_id: r.winner_op_id, loser_op_id: r.loser_op_id,
    winner_device: r.winner_device, loser_device: r.loser_device,
    detail: r.detail || {}, status: r.status, at: Number(r.at),
    resolved_at: r.resolved_at === null ? null : Number(r.resolved_at),
    resolved_by: r.resolved_by || '', resolution_op_id: r.resolution_op_id || '',
  };
}

async function listConflicts(ctx, { status: st = 'open', limit = 100 } = {}) {
  requireShop(ctx);
  const cap = Math.min(Math.max(1, Number(limit) || 100), 500);
  const all = st === 'all';
  const rows = await many(
    `SELECT * FROM sync_v3_conflicts WHERE ${ACC} AND ($4::boolean OR status='open')
      ORDER BY at DESC, id DESC LIMIT $5`,
    [...acc(ctx), all, cap]
  );
  return rows.map(shapeConflict);
}

/** opِ برگرداندن: مقدارِ بازنده با HLCِ تازهٔ سرور، که از همه بزرگ‌تر است. */
function revertOp(cf, stamp) {
  const base = { op_id: `srv.rv.${cf.id}`.slice(0, 80), table: cf.table_name, row_id: cf.row_id, hlc: stamp };
  if (cf.field === '*delete*' || cf.kind === 'stale_delete') return { ...base, kind: 'delete' };
  if (cf.field === '*') return { ...base, kind: 'update', fields: cf.loser_value || {} };
  return { ...base, kind: 'update', fields: { [cf.field]: cf.loser_value } };
}

async function resolveConflict(ctx, id, action, { userId = '' } = {}) {
  requireShop(ctx);
  if (!['keep', 'revert', 'dismiss'].includes(action)) throw badRequest('کار باید keep یا revert باشد', 'bad_action');
  if (!can(ctx.role, RESOLVE_PERMISSION)) throw forbidden('فقط مالک یا مدیر تعارض را می‌بندد', 'resolve_not_allowed');
  await ready();
  const t = now();
  const toV1On = await toV1.enabled(ctx);
  return tx(async (c) => {
    await lock(c, ctx);
    const v1 = toV1On ? await openToV1(c, ctx, t) : null;
    const cf = (await c.query(
      `SELECT * FROM sync_v3_conflicts WHERE ${ACC} AND id=$4 FOR UPDATE`, [...acc(ctx), String(id || '')]
    )).rows[0];
    if (!cf) throw notFound('این مورد پیدا نشد', 'conflict_not_found');
    if (cf.status !== 'open') throw conflictError('این مورد پیش‌تر بسته شده است', 'already_resolved');

    let op = null;
    let final = TASKS.has(cf.kind) || action === 'dismiss' ? 'dismissed' : 'kept';
    if (action === 'revert') {
      if (!REVERTIBLE.has(cf.kind)) throw badRequest('برای این مورد برگرداندن معنا ندارد', 'not_revertible');
      op = await applyServerOp(c, ctx, revertOp(cf, clock.tick()), t);
      final = 'reverted';
    }
    const upd = (await c.query(
      `UPDATE sync_v3_conflicts SET status=$2, resolved_at=$3, resolved_by=$4, resolution_op_id=$5
        WHERE id=$1 RETURNING *`,
      [cf.id, final, t, String(userId || ctx.userId || ''), op ? op.op_id : '']
    )).rows[0];
    if (v1 && v1.ready && op) await drainToV1(c, ctx, t);
    return { conflict: shapeConflict(upd), op };
  });
}

/** آنچه برنامه برای راه افتادن لازم دارد — `/hello` (جدولِ پشتِ پرچمِ خاموش نه، `safe-flag.js`). */
function describe(ctx = null) {
  const tables = schema.describe();
  for (const t of safeFlag.GATED) if (!safeFlag.allowed(t, ctx)) delete tables[t];
  return {
    protocol: PROTOCOL,
    accepts: { v3: true, v2_schema_max: v1migrations.currentVersion('shop', ctx) },
    skew_limit_ms: hlc.SKEW_MS,
    max_ops: MAX_OPS, max_batch_bytes: MAX_BATCH_BYTES,
    pull_bytes: { min: PULL_MIN_BYTES, max: PULL_MAX_BYTES },
    units: { money: 'Minor (1/100 AFN, integer)', qty: 'Milli (1/1000, integer)' },
    tables,
    server_time: now(), hlc: clock.peek(),
  };
}

function _resetClockForTests() { clockReady = null; }

module.exports = {
  PROTOCOL, MAX_OPS, MAX_BATCH_BYTES, PULL_MIN_BYTES, PULL_MAX_BYTES, SERVER_DEVICE,
  validate, merge, normPhone, push, pull, snapshot, status, report, bridge,
  listConflicts, resolveConflict, describe, clock, _resetClockForTests,
};
