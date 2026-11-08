'use strict';
/**
 * Sync v3 — دسترسیِ دیتابیسِ اعمالِ op (جداشده از `sync-v3.js`، همان SQL و همان ترتیب).
 *
 * `direct(c)` «انبارِ» اعمالِ op است روی اتصالِ تراکنشِ [c]: هر خواندن و نوشتنِ `applyOne`/`after`
 * از این‌جا می‌گذرد، تا راهِ دسته‌ای (`sync-v3-batch.js`، پرچمِ `SYNC_V3_BATCH`) همان کار را با
 * رفت‌وبرگشتِ کمتر بکند و بقیهٔ منطق یک نسخه بماند. هر تابعِ این‌جا دقیقاً همان پرس‌وجوی پیشین است.
 */
const { newId, now } = require('../db');

const ACC = 'app=$1 AND account_kind=$2 AND account_id=$3';
const acc = (ctx) => [ctx.app, ctx.accountKind, ctx.accountId];

/** شمارهٔ تلفن ⇒ کلیدِ هم‌سنجی؛ همان `normPhone` ِ ادغام (بی وابستگیِ دوری) */
const { normPhone } = require('./sync-v3-merge');

// ---------- ردیف، op، تعارض ----------

async function loadRow(c, ctx, table, rowId) {
  const r = (await c.query(
    `SELECT data, field_hlc, field_src, row_hlc, inserted, created_by, deleted_hlc, deleted_at
       FROM sync_v3_rows WHERE ${ACC} AND table_name=$4 AND row_id=$5 FOR UPDATE`,
    [...acc(ctx), table, rowId]
  )).rows[0];
  if (!r) return null;
  return { ...r, deleted_at: r.deleted_at === null ? null : Number(r.deleted_at) };
}

/** پارامترهای `saveRow` (جز سه‌تای حساب) — راهِ دسته‌ای همین را برای درجِ چندتایی می‌سازد */
function rowParams(op, row, seq, t) {
  const matchKey = op.spec.duplicate === 'phone' ? normPhone(row.data.phone) : '';
  return [op.table, op.row_id, JSON.stringify(row.data), JSON.stringify(row.field_hlc || {}),
    JSON.stringify(row.field_src || {}), row.row_hlc || '', !!row.inserted, row.created_by || '',
    row.deleted_hlc || null, row.deleted_at ?? null, matchKey, seq, t];
}

async function saveRow(c, ctx, op, row, seq, t) {
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
    [...acc(ctx), ...rowParams(op, row, seq, t)]
  );
}

/** پارامترهای درجِ تعارض (جز سه‌تای حساب، و شناسه پیش از آن‌ها) */
function conflictParams(cf, kind, t, dedupe, detail) {
  return [kind || cf.kind, cf.table, cf.row_id, cf.field || '',
    JSON.stringify(cf.winner_value ?? null), JSON.stringify(cf.loser_value ?? null),
    cf.winner_hlc || '', cf.loser_hlc || '', cf.winner_op_id || '', cf.loser_op_id || '',
    cf.winner_device || '', cf.loser_device || '', JSON.stringify(detail), dedupe, t];
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
    [id, ...acc(ctx), ...conflictParams(cf, kind, t, dedupe, detail)]
  );
  return id;
}

async function nextSeq(c) {
  const r = (await c.query(`SELECT nextval(pg_get_serial_sequence('sync_v3_ops','server_seq')) AS s`)).rows[0];
  return Number(r.s);
}

async function findDup(c, ctx, opId) {
  return (await c.query(
    `SELECT server_seq FROM sync_v3_ops WHERE ${ACC} AND op_id=$4`, [...acc(ctx), opId]
  )).rows[0];
}

/** پارامترهای درجِ op (جز سه‌تای حساب)؛ [r] همان شکلِ `applyOne` */
function opParams(r) {
  return [r.op_id, r.seq, r.device_id, r.origin, r.tx_id, r.hlc, r.client_hlc, r.skewed, r.table, r.row_id, r.kind,
    r.fields, r.effect, r.status, r.t];
}

function insertOp(c, ctx, r) {
  return c.query(
    `INSERT INTO sync_v3_ops (app, account_kind, account_id, op_id, server_seq, device_id, origin, tx_id, hlc,
                              client_hlc, skewed, table_name, row_id, kind, fields, effect, status, received_at)
     VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15::jsonb,$16::jsonb,$17,$18)`,
    [...acc(ctx), ...opParams(r)]
  );
}

// ---------- پیامدها (`after`) ----------

async function sumMoves(c, ctx, productId, upTo = '') {
  const r = (await c.query(
    `SELECT COALESCE(SUM((data->>'qtyMilli')::bigint), 0)::bigint AS s FROM sync_v3_rows
      WHERE ${ACC} AND table_name='stockMovements' AND inserted AND data->>'productId' = $4
        AND ($5 = '' OR row_hlc <= $5)`,
    [...acc(ctx), productId, upTo]
  )).rows[0];
  return Number(r.s);
}

/** تازه‌ترین شمارشِ همین کالا با HLC ≥ [eff] (حرکتِ دیررسیده) */
async function lateCount(c, ctx, productId, eff, countId) {
  return (await c.query(
    `SELECT row_id, row_hlc FROM sync_v3_rows
      WHERE ${ACC} AND table_name='stockCounts' AND inserted AND data->>'productId' = $4
        AND row_hlc >= $5 AND row_id <> $6
      ORDER BY row_hlc DESC LIMIT 1`,
    [...acc(ctx), productId, eff, countId]
  )).rows[0];
}

async function barcodeOthers(c, ctx, table, rowId, code) {
  return (await c.query(
    `SELECT row_id FROM sync_v3_rows WHERE ${ACC} AND table_name=$4 AND row_id <> $5 AND deleted_at IS NULL
        AND data->'barcodes' @> $6::jsonb ORDER BY row_id`,
    [...acc(ctx), table, rowId, JSON.stringify([code])]
  )).rows.map(r => r.row_id);
}

async function phoneOthers(c, ctx, table, key, rowId) {
  return (await c.query(
    `SELECT row_id FROM sync_v3_rows WHERE ${ACC} AND table_name=$4 AND match_key=$5 AND row_id <> $6
        AND deleted_at IS NULL ORDER BY row_id`,
    [...acc(ctx), table, key, rowId]
  )).rows.map(r => r.row_id);
}

/**
 * انبارِ مستقیم: هر کار همان پرس‌وجو روی [c]، همان دم. `begin`/`commit`/`undo` همان savepoint ِ هر واحد.
 * `c` به داورهای بیرونی (نقش، تأیید، قرض، سند) همان اتصال را می‌دهد.
 */
function direct(c) {
  return {
    c,
    dup: (ctx, opId) => findDup(c, ctx, opId),
    load: (ctx, table, rowId) => loadRow(c, ctx, table, rowId),
    seq: () => nextSeq(c),
    insertOp: (ctx, r) => insertOp(c, ctx, r),
    save: (ctx, op, row, seq, t) => saveRow(c, ctx, op, row, seq, t),
    conflict: (ctx, cf, kind, t, opts) => addConflict(c, ctx, cf, kind, t, opts),
    sumMoves: (ctx, productId, upTo) => sumMoves(c, ctx, productId, upTo),
    lateCount: (ctx, productId, eff, countId) => lateCount(c, ctx, productId, eff, countId),
    barcodeOthers: (ctx, table, rowId, code) => barcodeOthers(c, ctx, table, rowId, code),
    phoneOthers: (ctx, table, key, rowId) => phoneOthers(c, ctx, table, key, rowId),
    begin: (name) => c.query(`SAVEPOINT ${name}`),
    commit: (name) => c.query(`RELEASE SAVEPOINT ${name}`),
    undo: (name) => c.query(`ROLLBACK TO SAVEPOINT ${name}`),
    flush: async () => {},
  };
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

module.exports = {
  ACC, acc, loadRow, saveRow, addConflict, nextSeq, findDup, insertOp, sumMoves, lateCount, barcodeOthers, phoneOthers,
  rowParams, opParams, conflictParams, direct, touchDevice, headSeq,
};
