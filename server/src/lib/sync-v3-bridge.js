'use strict';
/**
 * Sync v3 — درونِ پلِ v1 ⇄ v3 (جداشده از `sync-v3.js`، رفتار همان). درهای بیرونی (`bridge`، `catchUp`)
 * در `sync-v3.js` مانده‌اند؛ این‌جا فقط کارهای درونِ تراکنشِ قفل‌دار.
 */
const hlc = require('./hlc');
const convert = require('./sync-v3-convert');
const toV1 = require('./sync-v3-to-v1');
const store = require('./sync-v3-store');
const { validate, cmp } = require('./sync-v3-merge');
const { applyOne } = require('./sync-v3-apply');

const { ACC, acc } = store;
const BRIDGE_BATCH = 1000;

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
  const s = store.direct(c);
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
      const res = await applyOne(s, ctx, v.op, { deviceId: r.device_id || 'v1', origin: 'v1', cursor: Infinity, t });
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

module.exports = { BRIDGE_BATCH, importV1, setV1Seq, openToV1, drainToV1 };
