'use strict';
/**
 * پلِ v3 ⇒ v1 — دکانی که دستگاه‌هایش قاطی‌اند (بعضی v1، بعضی v3).
 *
 * پلِ v1 ⇒ v3 از قبل بود (`bridge`ِ `sync-v3.js`)؛ ولی آنچه دستگاهِ v3
 * می‌فرستاد هرگز به `oplog`ِ v1 نمی‌رسید و گوشیِ v1 آن را نمی‌دید. این ماژول
 * اثرِ هر opِ پذیرفته‌شدهٔ v3 را به شکلِ opِ v1 در `oplog` + `sync_rows`
 * می‌نویسد — **همان‌طور که `push`ِ v1 می‌نویسد**، با توابعی که خودِ
 * `sync-v1.js` صادر می‌کند (`validateOp`، `applyOp`، `hashOf`).
 *
 * ── پرچم ─────────────────────────────────────────────────────────
 *   `shop.sync_v3_to_v1` در «تنظیماتِ زنده» (scope = شناسهٔ دکان، یا `all`؛
 *   مقدارِ خودِ دکان جلو می‌افتد). فقط `true` روشن است. پیش‌فرض خاموش ⇒
 *   هیچ نوشتنی در `oplog`، `sync_rows` و جدول‌های همین پل.
 *
 * ── قاعده‌ها ─────────────────────────────────────────────────────
 *   • **صندوقِ خروجیِ پایدار = خودِ `sync_v3_ops`**، با cursor در
 *     `sync_v3_v1_state`. در حالتِ عادی همان تراکنشِ push می‌نویسد؛ اگر
 *     نشد (پلِ v1 ⇒ v3 عقب بود) دفعهٔ بعد، بی آن‌که چیزی گم شود.
 *   • **تکرار بی‌اثر**: cursor و ردیفِ `sync_v3_v1_out` در همان تراکنش؛
 *     op_idِ v1 قطعی است (`v3b-` + هش) و `oplog.op_id` یکتاست.
 *   • **بی حلقه**: فقط opِ `origin <> 'v1'` بیرون می‌رود؛ و پلِ v1 ⇒ v3
 *     هر ردیفِ `oplog` را که در `sync_v3_v1_out` است برنمی‌گرداند.
 *   • **داوریِ یکسان**: v1 با ترتیبِ رسیدن داوری می‌کند و v3 با HLC. پس
 *     (۱) فقط پس از آن‌که همهٔ `oplog` به v3 آمده باشد و زیرِ قفلِ v1 نوشته
 *     می‌شود؛ (۲) از اطلاعاتِ پایه فقط فیلدی که **هنوز** در v3 برنده است؛
 *     (۳) HLCِ opِ v1ِ بعدی هرگز از HLCِ ردیف‌های پل که پیش از آن در
 *     `oplog` نشسته‌اند کوچک‌تر نیست (`floorBefore`). نتیجه: برندهٔ هر فیلد
 *     در هر دو دفتر یکی است.
 *   • **پول و مقدار**: `…Minor / 100` و `…Milli / 1000` — تقسیمِ دقیق، همان
 *     `dataToLegacy`ِ برنامهٔ v3؛ و برگشتش همان `fromLegacy` (`Math.round`)
 *     است که مبدلِ v2 به کار می‌برد ⇒ رفت و برگشت عددِ صحیح را عوض نمی‌کند.
 *   • **آنچه v1 نمی‌تواند بگوید** (جدولِ فقط-v3 مثل شمارشِ انبار، یا
 *     مقداری که `validateOp`ِ v1 رد می‌کند) دور ریخته نمی‌شود: یک ردیف
 *     در صندوقِ تعارضِ v3 با نوعِ `v1_unbridged`.
 *
 * ── ترتیبِ قفل ─────────────────────────────────────────────────
 *   همیشه اول قفلِ v3ِ حساب، بعد قفلِ v1ِ همان حساب (همان کلیدِ `push`ِ v1).
 *   `push`ِ v1 فقط قفلِ v1 را می‌گیرد ⇒ چرخهٔ انتظار ساخته نمی‌شود.
 *
 * ⛔ `sync-v1.js`، `PUMP_TABLES` و `/api/pump/*` دست نمی‌خورند؛ فقط `app=shop`.
 */
const { createHash, randomBytes } = require('crypto');
const { many, newId } = require('../db');
const { validateOp, applyOp, hashOf } = require('./sync-v1');
const v1migrations = require('./sync-v1-migrations');
const schema = require('./sync-v3-schema');
const hlc = require('./hlc');

const FLAG_KEY = 'shop.sync_v3_to_v1';
const DRAIN_BATCH = 500;
const SERVER_DEVICE = 'server';
const V1_OP_ID_RE = /^[A-Za-z0-9_-]{1,64}$/;
//  دستگاهی که «همه چیز را دیده» ⇒ نوشتنِ پل در v1 تعارض نمی‌سازد؛ داوری را v3 کرده بود
const SEEN_ALL = Number.MAX_SAFE_INTEGER;

const ACC = 'app=$1 AND account_kind=$2 AND account_id=$3';
const acc = (ctx) => [ctx.app, ctx.accountKind, ctx.accountId];

function isPlainObject(v) { return v !== null && typeof v === 'object' && !Array.isArray(v); }

// ---------- پرچم ----------

/** پرچمِ دکان: مقدارِ خودِ دکان بر `all` جلو می‌افتد؛ فقط `true` روشن است. */
async function enabled(ctx) {
  if (!ctx || ctx.app !== 'shop' || ctx.accountKind !== 'shop') return false;
  const rows = await many(
    'SELECT scope, value FROM live_config WHERE key=$1 AND scope IN ($2,$3)',
    [FLAG_KEY, 'all', String(ctx.accountId || '')]
  );
  const decode = (x) => { try { return JSON.parse(x); } catch { return null; } };
  let v;
  for (const r of rows) if (r.scope === 'all') v = decode(r.value);
  for (const r of rows) if (r.scope !== 'all') v = decode(r.value);
  return v === true;
}

/** قفلِ حسابِ v1 — همان کلیدی که `push`ِ v1 می‌گیرد. */
function lockV1(c, ctx) {
  return c.query('SELECT pg_advisory_xact_lock(hashtext($1))',
    [`sync-v1:${ctx.app}:${ctx.accountKind}:${ctx.accountId}`]);
}

// ---------- واحدها: v3 ⇒ v1 ----------

/**
 * دادهٔ v3 (`…Minor`/`…Milli`) ⇒ نام و واحدِ دفترِ v1. تقسیمِ دقیق، بی گردکردن —
 * همان `dataToLegacy`ِ برنامهٔ v3 (`license/sync-v3-engine.js`، `V3Convert.kt`).
 */
function toLegacy(spec, data) {
  const out = {};
  for (const [k, v] of Object.entries(data || {})) {
    const m = /^(.*)(Minor|Milli)$/.exec(k);
    if (m && (m[2] === 'Minor' ? spec.money : spec.qty).includes(m[1])) {
      out[m[1]] = v === null ? null : (typeof v === 'number' ? v / (m[2] === 'Minor' ? 100 : 1000) : v);
      continue;
    }
    out[k] = v;
  }
  return out;
}

/** دلتای v3 ⇒ `{"$inc": n}`ِ v1 با نام و واحدِ قدیم. */
function incToLegacy(spec, inc) {
  const out = {};
  for (const [k, n] of Object.entries(inc || {})) {
    const [name, v] = Object.entries(toLegacy(spec, { [k]: n }))[0];
    out[name] = { $inc: v };
  }
  return out;
}

/** op_idِ قطعیِ v1 برای تکهٔ `part`ِ یک opِ v3 (شکلِ مجازِ v1، یکتا میانِ حساب‌ها). */
function v1OpId(ctx, v3OpId, part) {
  const h = createHash('sha256')
    .update(`${ctx.app}|${ctx.accountKind}|${ctx.accountId}|${v3OpId}|${part}`).digest('hex');
  return `v3b-${h.slice(0, 40)}`;
}

// ---------- کفِ HLC برای opهای v1 ----------

/**
 * بزرگ‌ترین HLCِ ردیف‌هایی که پل تا `v1Seq` (شامل) در `oplog` نوشته — یا ''.
 * پلِ v1 ⇒ v3 به opِ v1ِ بعد از آن HLCی بزرگ‌تر از این می‌دهد، تا «دیرتر
 * رسیده در v1» در v3 هم دیرتر باشد.
 */
async function floorBefore(c, ctx, v1Seq) {
  const r = (await c.query(
    `SELECT hlc FROM sync_v3_v1_out WHERE ${ACC} AND v1_seq <= $4 ORDER BY hlc DESC LIMIT 1`,
    [...acc(ctx), v1Seq]
  )).rows[0];
  return r ? r.hlc : '';
}

/** HLCِ opِ v1: همان که بود، مگر به کف نرسد ⇒ یک گام بالای کف. */
function aboveFloor(stamp, floor, node) {
  if (!floor || hlc.compare(stamp, floor) > 0) return stamp;
  const f = hlc.parse(floor);
  return f.counter >= 999_999
    ? hlc.format({ wall: f.wall + 1, counter: 0, node })
    : hlc.format({ wall: f.wall, counter: f.counter + 1, node });
}

// ---------- نوشتن در v1، همان‌طور که push ِ v1 می‌نویسد ----------

/**
 * @param op خروجیِ `validateOp`ِ v1
 * @returns {Promise<number|null>} server_seq، یا null اگر op_id گرفته بود
 */
async function writeV1(c, ctx, op, { deviceId, t }) {
  const current = v1migrations.currentVersion(ctx.app);
  const ins = await c.query(
    `INSERT INTO oplog (id, app, account_kind, account_id, device_id, op_id, client_ts, table_name, row_id,
                        op_type, fields, hash, schema_version, received_at)
     VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11::jsonb,$12,$13,$14)
     ON CONFLICT (op_id) DO NOTHING RETURNING server_seq`,
    [newId('op'), ctx.app, ctx.accountKind, ctx.accountId, deviceId, op.op_id, op.ts, op.table, op.row_id,
      op.type, JSON.stringify(op.fields || {}),
      hashOf({ table: op.table, row_id: op.row_id, type: op.type, fields: op.fields }), current, t]
  );
  if (!ins.rows.length) return null;
  const seq = Number(ins.rows[0].server_seq);
  const row = (await c.query(
    `SELECT * FROM sync_rows WHERE ${ACC} AND table_name=$4 AND row_id=$5 FOR UPDATE`,
    [...acc(ctx), op.table, op.row_id]
  )).rows[0] || null;
  const out = applyOp(row, op, seq, deviceId, t, SEEN_ALL);
  await c.query(
    `INSERT INTO sync_rows (app, account_kind, account_id, table_name, row_id, data, field_seq, field_src,
                            deleted_at, updated_seq, created_at, updated_at)
     VALUES ($1,$2,$3,$4,$5,$6::jsonb,$7::jsonb,$8::jsonb,$9,$10,$11,$11)
     ON CONFLICT (app, account_kind, account_id, table_name, row_id) DO UPDATE SET
       data = excluded.data, field_seq = excluded.field_seq, field_src = excluded.field_src,
       deleted_at = excluded.deleted_at, updated_seq = excluded.updated_seq, updated_at = excluded.updated_at`,
    [...acc(ctx), op.table, op.row_id, JSON.stringify(out.data), JSON.stringify(out.field_seq),
      JSON.stringify(out.field_src), out.deleted_at, seq, t]
  );
  return seq;
}

// ---------- یک opِ v3 ⇒ opهای v1 ----------

async function v3Row(c, ctx, table, rowId) {
  return (await c.query(
    `SELECT data, field_src, deleted_at FROM sync_v3_rows WHERE ${ACC} AND table_name=$4 AND row_id=$5`,
    [...acc(ctx), table, rowId]
  )).rows[0] || null;
}

async function v1Row(c, ctx, table, rowId) {
  return (await c.query(
    `SELECT deleted_at FROM sync_rows WHERE ${ACC} AND table_name=$4 AND row_id=$5`,
    [...acc(ctx), table, rowId]
  )).rows[0] || null;
}

/**
 * @param r ردیفِ `sync_v3_ops` (با `effect`)
 * @returns {Promise<{ops:Array<{type, fields?}>} | {skip:string} | {unbridged:string}>}
 */
async function plan(c, ctx, r, spec) {
  const e = isPlainObject(r.effect) ? r.effect : {};
  if (!spec) return { unbridged: 'unknown_table' };
  if (spec.v3only) return { unbridged: 'v3_only_table' };

  if (spec.policy === 'master') {
    //  «آخرین نوشتن»: آنچه **امروز** در v3 برنده است، نه آنچه آن لحظه نشست
    const cur = await v3Row(c, ctx, r.table_name, r.row_id);
    if (e.deleted) {
      if (!cur || cur.deleted_at === null) return { skip: 'revived_later' };
      const old = await v1Row(c, ctx, r.table_name, r.row_id);
      if (!old || old.deleted_at !== null) return { skip: 'v1_already_gone' };
      return { ops: [{ type: 'delete' }] };
    }
    if (!cur || cur.deleted_at !== null) return { skip: 'deleted_later' };
    const src = isPlainObject(cur.field_src) ? cur.field_src : {};
    const keys = e.revived
      ? Object.keys(cur.data || {})
      : Object.keys(isPlainObject(e.set) ? e.set : {}).filter(k => isPlainObject(src[k]) && src[k].o === r.op_id);
    if (!keys.length) return { skip: 'superseded' };
    const data = {};
    for (const k of keys) data[k] = cur.data[k];
    return { ops: [{ type: r.kind === 'insert' && !e.revived ? 'insert' : 'update', fields: toLegacy(spec, data) }] };
  }

  //  سند، دفتر، حرکت: فقط افزودنی و جابه‌جاپذیر ⇒ خودِ اثر
  if (r.kind === 'insert' && isPlainObject(e.set) && Object.keys(e.set).length) {
    return { ops: [{ type: 'insert', fields: toLegacy(spec, e.set) }] };
  }
  if (r.kind === 'delta' && isPlainObject(e.inc) && Object.keys(e.inc).length) {
    return { ops: [{ type: 'update', fields: incToLegacy(spec, e.inc) }] };
  }
  if (r.kind === 'cancel' && isPlainObject(e.set)) {
    //  لغوِ فاکتور در v1 همان `status: cancelled` است؛ بقیهٔ اسناد در v1 «لغو» ندارند
    //  و مبدلِ v2 حذفِ v1 را لغو می‌کند ⇒ برعکسش حذفِ نرمِ v1
    if (spec.cancel === 'sales.cancel') return { ops: [{ type: 'update', fields: toLegacy(spec, e.set) }] };
    const old = await v1Row(c, ctx, r.table_name, r.row_id);
    if (old && old.deleted_at !== null) return { skip: 'v1_already_gone' };
    return { ops: [{ type: 'delete' }] };
  }
  return { unbridged: 'no_v1_equivalent' };
}

/** opی که v1 نمی‌تواند بگوید ⇒ صندوقِ تعارضِ v3، یک بار برای هر op. */
async function park(c, ctx, r, reason, t) {
  const dedupe = `v1out:${r.op_id}`;
  await c.query(
    `INSERT INTO sync_v3_conflicts (id, app, account_kind, account_id, kind, table_name, row_id, field,
                                    winner_value, loser_value, winner_hlc, loser_hlc, winner_op_id, loser_op_id,
                                    winner_device, loser_device, detail, dedupe_key, at)
     SELECT $1,$2,$3,$4,'v1_unbridged',$5,$6,'*','null'::jsonb,$7::jsonb,'',$8,'',$9,'',$10,$11::jsonb,$12,$13
      WHERE NOT EXISTS (SELECT 1 FROM sync_v3_conflicts
                         WHERE app=$2 AND account_kind=$3 AND account_id=$4 AND dedupe_key=$12)`,
    [newId('cf'), ...acc(ctx), r.table_name, r.row_id, JSON.stringify(r.effect ?? null), r.hlc || '', r.op_id,
      r.device_id || '', JSON.stringify({ reason, origin: r.origin, kind: r.kind }), dedupe, t]
  );
}

/** opِ v1ِ بومی (نه نوشتهٔ همین پل) با این op_id در همین حساب هست؟ */
async function nativeInV1(c, ctx, opId) {
  const r = (await c.query(
    `SELECT 1 AS x FROM oplog o WHERE o.app=$1 AND o.account_kind=$2 AND o.account_id=$3 AND o.op_id=$4
        AND NOT EXISTS (SELECT 1 FROM sync_v3_v1_out x WHERE x.app=$1 AND x.account_kind=$2 AND x.account_id=$3
                          AND x.v1_seq = o.server_seq)`,
    [...acc(ctx), opId]
  )).rows[0];
  return !!r;
}

// ---------- تخلیهٔ صندوق ----------

/**
 * opهای v3ِ تازه (بعد از cursor) را به v1 می‌برد. صدا‌زننده باید قفلِ v3 و
 * بعد قفلِ v1ِ حساب را گرفته و پلِ v1 ⇒ v3 را **کامل** رسانده باشد.
 *
 * @returns {{written, skipped, unbridged, behind, v3_seq}}
 */
async function drain(c, ctx, { t, max = DRAIN_BATCH } = {}) {
  const st = (await c.query(`SELECT v3_seq FROM sync_v3_v1_state WHERE ${ACC} FOR UPDATE`, acc(ctx))).rows[0];
  const from = st ? Number(st.v3_seq) : 0;
  const rows = (await c.query(
    `SELECT op_id, server_seq, device_id, origin, hlc, table_name, row_id, kind, effect
       FROM sync_v3_ops WHERE ${ACC} AND server_seq > $4 AND origin <> 'v1' AND effect IS NOT NULL
      ORDER BY server_seq ASC LIMIT $5`,
    [...acc(ctx), from, max]
  )).rows;
  let written = 0;
  let skipped = 0;
  let unbridged = 0;

  for (const r of rows) {
    const spec = schema.specOf(r.table_name);
    //  صفِ قدیمِ برنامه (`format: v2`) که همان op را از راهِ v1 هم فرستاده بود:
    //  v1 آن را دارد ⇒ دوباره نه (دلتا دو بار جمع نشود)
    const base = String(r.op_id).split('.')[0];
    if (r.origin === 'v2' && await nativeInV1(c, ctx, base)) { skipped++; continue; }

    const p = await plan(c, ctx, r, spec);
    if (p.unbridged) { await park(c, ctx, r, p.unbridged, t); unbridged++; continue; }
    if (!p.ops) { skipped++; continue; }

    const deviceId = r.origin === 'server' ? SERVER_DEVICE : (r.device_id || SERVER_DEVICE);
    const ts = (hlc.parse(r.hlc) || { wall: t }).wall;
    for (let i = 0; i < p.ops.length; i++) {
      const done = (await c.query(
        `SELECT 1 AS x FROM sync_v3_v1_out WHERE ${ACC} AND v3_op_id=$4 AND part=$5`, [...acc(ctx), r.op_id, i]
      )).rows[0];
      if (done) continue;
      const o = p.ops[i];
      //  opِ صفِ قدیم همان op_idِ v1ِ خودش را می‌گیرد ⇒ اگر بعداً از راهِ v1 هم برسد «تکراری» است.
      //  شناسهٔ قطعی را دستگاهی پیش‌دستانه گرفته بود؟ یک شناسهٔ تصادفی (تکرار را ردیفِ
      //  `sync_v3_v1_out` جلو می‌گیرد، نه خودِ op_id) ⇒ هیچ دستگاهی نمی‌تواند پل را ببندد
      const fixed = v1OpId(ctx, r.op_id, i);
      const ids = [...(r.origin === 'v2' && V1_OP_ID_RE.test(base) ? [base] : []), fixed,
        `${fixed}-${randomBytes(8).toString('hex')}`];
      let seq = null;
      let opId = '';
      let bad = '';
      for (const id of ids) {
        const val = validateOp('shop', {
          op_id: id, table: r.table_name, row_id: r.row_id, type: o.type,
          fields: o.type === 'delete' ? undefined : o.fields, ts,
        });
        if (!val.ok) { bad = val.reason; break; }
        seq = await writeV1(c, ctx, val.op, { deviceId, t });
        if (seq !== null) { opId = id; break; }
      }
      if (seq === null) {
        await park(c, ctx, r, bad || 'v1_op_id_taken', t);
        unbridged++;
        break;
      }
      await c.query(
        `INSERT INTO sync_v3_v1_out (app, account_kind, account_id, v3_op_id, part, v3_seq, v1_op_id, v1_seq, hlc, at)
         VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10)`,
        [...acc(ctx), r.op_id, i, Number(r.server_seq), opId, seq, r.hlc || '', t]
      );
      written++;
    }
  }

  let last = rows.length ? Number(rows[rows.length - 1].server_seq) : from;
  const behind = rows.length >= max;
  if (!behind) {
    //  زیرِ قفلِ v3 هیچ opِ تازه‌ای نمی‌رسد ⇒ cursor تا سرِ دفتر (opهای v1 و بی‌اثر دوباره پیمایش نشوند)
    const h = (await c.query(
      `SELECT COALESCE(MAX(server_seq),0)::bigint AS head FROM sync_v3_ops WHERE ${ACC}`, acc(ctx)
    )).rows[0];
    last = Math.max(last, Number(h.head));
  }
  if (last !== from) {
    await c.query(
      `INSERT INTO sync_v3_v1_state (app, account_kind, account_id, v3_seq, updated_at) VALUES ($1,$2,$3,$4,$5)
       ON CONFLICT (app, account_kind, account_id) DO UPDATE SET v3_seq = excluded.v3_seq, updated_at = excluded.updated_at`,
      [...acc(ctx), last, t]
    );
  }
  return { written, skipped, unbridged, behind, v3_seq: last };
}

/**
 * چیزی بعد از cursor هست؟ — پیش‌سنجِ بی قفل. عمداً opهای v1 را هم می‌شمارد:
 * تخلیه cursor را تا سرِ دفتر می‌برد، پس این پرس‌وجو هیچ‌وقت کلِ تاریخچهٔ
 * opهای v1 را برای «هیچ» پیمایش نمی‌کند.
 */
async function pending(q, ctx) {
  const r = (await q.query(
    `SELECT 1 AS x FROM sync_v3_ops WHERE ${ACC}
        AND server_seq > COALESCE((SELECT v3_seq FROM sync_v3_v1_state WHERE ${ACC}), 0) LIMIT 1`,
    acc(ctx)
  )).rows[0];
  return !!r;
}

module.exports = {
  FLAG_KEY, DRAIN_BATCH, enabled, lockV1, toLegacy, incToLegacy, v1OpId,
  floorBefore, aboveFloor, writeV1, plan, drain, pending,
};
