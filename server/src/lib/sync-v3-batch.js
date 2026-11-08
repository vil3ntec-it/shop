'use strict';
/**
 * Sync v3 — اعمالِ دسته‌ایِ push با رفت‌وبرگشتِ کمتر (`SYNC_V3_BATCH`، ⛔ پیش‌فرض خاموش؛ docs/SCALING-fa.md §۳).
 *
 * همان `applyOne`/`after` ِ `sync-v3-apply.js`، همان تراکنش، همان قفل و همان ترتیب؛ فقط «انبار» فرق دارد
 * (`sync-v3-store.direct` را ببینید):
 *   • یک بار برای همهٔ دسته: تکراری‌ها (`op_id = ANY`)، ردیف‌ها (`FOR UPDATE`)، و برای حرکت/شمارشِ کالا جمعِ
 *     حرکت‌ها و بودنِ شمارش. شمارهٔ دفتر (`nextval`) یک‌جا برای opهای مانده.
 *   • درجِ op، نوشتنِ ردیف و تعارضِ ساده در حافظه، و یک‌جا در **یک** دستور (`jsonb_to_recordset`) — پیش از هر
 *     خواندنی که دفتر را می‌بیند، و در پایان.
 *   • savepoint ِ هر واحد فقط وقتی واحد به دیتابیس دست بزند؛ واحدِ ردشده‌ای که دست نزده فقط در حافظه برمی‌گردد.
 *
 * ⛔ برابری با راهِ تک‌تک (سنجه: `test/sync-v3-batch.test.js`):
 *   • هر پرس‌وجوی داورهای بیرونی (نقش، تأیید، قرض، سند، صندوق، گاوصندوق) همان دم اجرا می‌شود؛ اگر متنش به
 *     `sync_v3_ops|rows|conflicts` اشاره کند، نوشته‌های مانده پیش از آن نوشته می‌شوند، و اگر فقط‌خواندنی نباشد
 *     savepoint ِ واحد پیش از آن باز است (همان برگشتِ واحد). هیچ view/تابع/تریگری روی این جدول‌ها نیست.
 *   • حالِ ردیف در حافظه همان است که دیتابیس برمی‌گرداند (ترتیبِ کلیدِ jsonb، همان تبدیل‌های `saveRow`).
 *   • هر چیزِ ناآشنا (نوعِ غیرِ رشته در ستونِ متنی، عددِ ناصحیح، کالای بی شناسهٔ رشته‌ای) ⇒ همان پرس‌وجوی تک‌تک.
 */
const config = require('../config');
const { newId } = require('../db');
const store = require('./sync-v3-store');

const { ACC, acc } = store;
const on = () => !!(config.syncV3 && config.syncV3.batch);

const LEDGER = /\bsync_v3_(?:ops|rows|conflicts)\b/i;
const SELECT = /^\s*select\b/i;
const EFFECTFUL = /\bfor\s+(?:update|share|no\s+key|key)\b|\b(?:nextval|setval|pg_advisory\w*|pg_notify|set_config|lo_\w+|txid_current\w*|pg_current_xact_id\w*)\s*\(|\b(?:insert|update|delete|merge)\b/i;
/** فقط‌خواندنیِ مطمئن؛ هر شکِ دیگر ⇒ نوشتن (savepoint) */
const readOnly = (sql) => typeof sql === 'string' && SELECT.test(sql) && !EFFECTFUL.test(sql);
const seesLedger = (sql) => typeof sql !== 'string' || LEDGER.test(sql);
const INT_TEXT = /^-?[0-9]{1,15}$/;
const ODD = Symbol('odd');
const MAX_SEQ_BLOCK = 1000;

const keyOf = (table, rowId) => `${table}\u0000${rowId}`;
//  رشتهٔ خوش‌ساخت (بی نیمه‌جانشینِ تنها): در JSON ِ دسته همان بایتی می‌شود که پارامترِ متنیِ راهِ تک‌تک
const isText = (v) => typeof v === 'string' && v.isWellFormed();
const isInt = (v) => Number.isSafeInteger(v);

/** ترتیبِ کلیدِ jsonb: کوتاه‌تر (بایت) اول، سپس بایت‌به‌بایت — همان که PostgreSQL برمی‌گرداند */
function keyCmp(a, b) {
  const x = Buffer.from(a);
  const y = Buffer.from(b);
  return x.length - y.length || Buffer.compare(x, y);
}
function jsonbOrder(v) {
  if (Array.isArray(v)) return v.map(jsonbOrder);
  if (v === null || typeof v !== 'object') return v;
  const out = {};
  for (const k of Object.keys(v).sort(keyCmp)) out[k] = jsonbOrder(v[k]);
  return out;
}
const asJsonb = (text) => jsonbOrder(JSON.parse(text));
const clone = (st) => (st ? { ...st, data: JSON.parse(JSON.stringify(st.data)),
  field_hlc: JSON.parse(JSON.stringify(st.field_hlc)), field_src: JSON.parse(JSON.stringify(st.field_src)) } : null);

/** حالِ ردیف همان شکلِ `loadRow` */
function fromDb(r) {
  return {
    data: r.data, field_hlc: r.field_hlc, field_src: r.field_src, row_hlc: r.row_hlc, inserted: r.inserted,
    created_by: r.created_by, deleted_hlc: r.deleted_hlc, deleted_at: r.deleted_at === null ? null : Number(r.deleted_at),
  };
}

/** [p] = `store.rowParams` ⇒ رکوردِ درج، یا null اگر شکلی ناآشنا دارد (⇒ همان `saveRow`) */
function rowRecord(p) {
  const [table, rowId, data, fh, fs, rowHlc, inserted, createdBy, deletedHlc, deletedAt, matchKey, seq, t] = p;
  if (![table, rowId, data, fh, fs, rowHlc, createdBy, matchKey].every(isText)) return null;
  if (!(deletedHlc === null || isText(deletedHlc)) || !(deletedAt === null || isInt(deletedAt))) return null;
  if (typeof inserted !== 'boolean' || !isInt(seq) || !isInt(t)) return null;
  return { table_name: table, row_id: rowId, data, field_hlc: fh, field_src: fs, row_hlc: rowHlc, inserted,
    created_by: createdBy, deleted_hlc: deletedHlc, deleted_at: deletedAt, match_key: matchKey, updated_seq: seq, t };
}
const stateOf = (w) => ({
  data: asJsonb(w.data), field_hlc: asJsonb(w.field_hlc), field_src: asJsonb(w.field_src), row_hlc: w.row_hlc,
  inserted: w.inserted, created_by: w.created_by, deleted_hlc: w.deleted_hlc, deleted_at: w.deleted_at,
});

function opRecord(r) {
  const texts = [r.op_id, r.device_id, r.origin, r.tx_id, r.hlc, r.client_hlc, r.table, r.row_id, r.kind, r.fields, r.status];
  if (!texts.every(isText) || !(r.effect === null || isText(r.effect)) || typeof r.skewed !== 'boolean'
    || !isInt(r.seq) || !isInt(r.t)) return null;
  return { op_id: r.op_id, server_seq: r.seq, device_id: r.device_id, origin: r.origin, tx_id: r.tx_id, hlc: r.hlc,
    client_hlc: r.client_hlc, skewed: r.skewed, table_name: r.table, row_id: r.row_id, kind: r.kind, fields: r.fields,
    effect: r.effect, status: r.status, received_at: r.t };
}

const CF_COLS = ['kind', 'table_name', 'row_id', 'field', 'winner_value', 'loser_value', 'winner_hlc', 'loser_hlc',
  'winner_op_id', 'loser_op_id', 'winner_device', 'loser_device', 'detail', 'dedupe_key'];
function conflictRecord(id, p) {
  const at = p[p.length - 1];
  const vals = p.slice(0, -1);
  if (!vals.every(isText) || !isInt(at)) return null;
  const out = { id };
  CF_COLS.forEach((k, i) => { out[k] = vals[i]; });
  out.at = at;
  return out;
}

//  یک دستور برای همه: opها، ردیف‌ها (همان ON CONFLICT ِ `saveRow`)، `detail` ِ وظیفهٔ بازِ دیتابیس و تعارض‌های
//  تازه. jsonb همه از متن (`::jsonb`)، همان راهِ پارامترِ `$n::jsonb` ِ راهِ تک‌تک (null ِ JSON در `winner_value`
//  همان jsonb ِ null می‌ماند). به‌روزرسانی هیچ‌گاه به ردیفی نمی‌خورد که در همین دستور درج می‌شود (`writeOut`).
const FLUSH_SQL = `WITH o AS (
  INSERT INTO sync_v3_ops (app, account_kind, account_id, op_id, server_seq, device_id, origin, tx_id, hlc,
                           client_hlc, skewed, table_name, row_id, kind, fields, effect, status, received_at)
  SELECT $1, $2, $3, x.op_id, x.server_seq, x.device_id, x.origin, x.tx_id, x.hlc, x.client_hlc, x.skewed, x.table_name,
         x.row_id, x.kind, x.fields::jsonb, x.effect::jsonb, x.status, x.received_at
    FROM jsonb_to_recordset($4::jsonb) AS x(op_id text, server_seq bigint, device_id text, origin text, tx_id text,
         hlc text, client_hlc text, skewed boolean, table_name text, row_id text, kind text, fields text, effect text,
         status text, received_at bigint)
), r AS (
  INSERT INTO sync_v3_rows (app, account_kind, account_id, table_name, row_id, data, field_hlc, field_src,
                            row_hlc, inserted, created_by, deleted_hlc, deleted_at, match_key, updated_seq,
                            created_at, updated_at)
  SELECT $1, $2, $3, x.table_name, x.row_id, x.data::jsonb, x.field_hlc::jsonb, x.field_src::jsonb, x.row_hlc,
         x.inserted, x.created_by, x.deleted_hlc, x.deleted_at, x.match_key, x.updated_seq, x.t, x.t
    FROM jsonb_to_recordset($5::jsonb) AS x(table_name text, row_id text, data text, field_hlc text, field_src text,
         row_hlc text, inserted boolean, created_by text, deleted_hlc text, deleted_at bigint, match_key text,
         updated_seq bigint, t bigint)
  ON CONFLICT (app, account_kind, account_id, table_name, row_id) DO UPDATE SET
    data = excluded.data, field_hlc = excluded.field_hlc, field_src = excluded.field_src,
    row_hlc = excluded.row_hlc, inserted = excluded.inserted, created_by = excluded.created_by,
    deleted_hlc = excluded.deleted_hlc, deleted_at = excluded.deleted_at, match_key = excluded.match_key,
    updated_seq = excluded.updated_seq, updated_at = excluded.updated_at
), u AS (
  UPDATE sync_v3_conflicts AS c SET detail = x.detail::jsonb
    FROM jsonb_to_recordset($7::jsonb) AS x(id text, detail text) WHERE c.id = x.id
)
INSERT INTO sync_v3_conflicts (id, app, account_kind, account_id, kind, table_name, row_id, field,
                               winner_value, loser_value, winner_hlc, loser_hlc, winner_op_id, loser_op_id,
                               winner_device, loser_device, detail, dedupe_key, at)
SELECT x.id, $1, $2, $3, x.kind, x.table_name, x.row_id, x.field, x.winner_value::jsonb, x.loser_value::jsonb,
       x.winner_hlc, x.loser_hlc, x.winner_op_id, x.loser_op_id, x.winner_device, x.loser_device, x.detail::jsonb,
       x.dedupe_key, x.at
  FROM jsonb_to_recordset($6::jsonb) AS x(id text, kind text, table_name text, row_id text, field text,
       winner_value text, loser_value text, winner_hlc text, loser_hlc text, winner_op_id text, loser_op_id text,
       winner_device text, loser_device text, detail text, dedupe_key text, at bigint)`;

const ROWS_SQL = `SELECT table_name, row_id, data, field_hlc, field_src, row_hlc, inserted, created_by, deleted_hlc, deleted_at
    FROM sync_v3_rows JOIN unnest($4::text[], $5::text[]) AS k(kt, kid) ON table_name = k.kt AND row_id = k.kid
   WHERE ${ACC}
   ORDER BY table_name, row_id FOR UPDATE OF sync_v3_rows`;

//  جمعِ حرکت‌ها (فقط مقدارِ صحیحِ ساده؛ بقیه `odd` ⇒ همان پرس‌وجوی تک‌تک) و بودنِ شمارش برای کالاهای همین دسته
const STOCK_SQL = `SELECT 'm' AS k, data->>'productId' AS p,
         COALESCE(SUM(CASE WHEN (data->>'qtyMilli') ~ '^-?[0-9]{1,15}$' THEN (data->>'qtyMilli')::bigint END), 0)::text AS s,
         COUNT(*) FILTER (WHERE data->>'qtyMilli' IS NOT NULL AND (data->>'qtyMilli') !~ '^-?[0-9]{1,15}$')::int AS odd
    FROM sync_v3_rows WHERE ${ACC} AND table_name = 'stockMovements' AND inserted AND data->>'productId' = ANY($4::text[])
   GROUP BY 2
  UNION ALL
  SELECT 'c', data->>'productId', '0', 0
    FROM sync_v3_rows WHERE ${ACC} AND table_name = 'stockCounts' AND inserted AND data->>'productId' = ANY($4::text[])
   GROUP BY 2`;

/** سهمِ یک ردیف در `sumMoves(p)` همان‌طور که SQL می‌شمارد؛ ODD ⇒ نامطمئن */
function moveOf(st, p) {
  if (!st || st.inserted !== true) return 0n;
  const pid = st.data ? st.data.productId : undefined;
  if (pid === undefined || pid === null) return 0n;
  if (typeof pid !== 'string') return ODD;
  if (pid !== p) return 0n;
  const q = st.data.qtyMilli;
  if (q === undefined || q === null) return 0n;
  const txt = typeof q === 'string' ? q : (isInt(q) && Math.abs(q) < 1e15 ? String(q) : null);
  return txt !== null && INT_TEXT.test(txt) ? BigInt(txt) : ODD;
}

const emptyBuf = () => ({ ops: [], rows: new Map(), conflicts: [], details: new Map() });
const isEmpty = (b) => !b.ops.length && !b.rows.size && !b.conflicts.length && !b.details.size;

/**
 * انبارِ دسته‌ای برای یک push، روی اتصالِ تراکنشِ [c] (قفلِ حساب گرفته شده). [plan] واحدهای همان push.
 * @returns همان شکلِ `store.direct(c)`
 */
async function open(c, ctx, plan) {
  const ops = plan.filter((u) => !u.broken).flatMap((u) => u.items.flatMap((it) => it.ops));
  const clientIds = new Set(ops.map((o) => o.op_id));
  let left = ops.length;

  const known = new Map();       //  op_id ⇒ server_seq (در دیتابیس یا نوشتهٔ همین دسته)
  const asked = new Set(clientIds);
  const cache = new Map();       //  ردیف ⇒ حالِ کنونی (null = نیست)
  const base = new Map();        //  ردیف ⇒ حالِ پیش از همین دسته
  const sums = new Map();        //  کالا ⇒ {s, odd} جمعِ حرکت‌ها پیش از همین دسته
  const counted = new Set();     //  کالاهایی که شمارش دارند
  const stockAsked = new Set();
  const movesTouched = new Set();
  const countsTouched = new Set();
  let countsOdd = false;
  //  وظیفه/صندوقِ dedupe: کلید ⇒ {id} ِ ردیفِ باز (دیتابیس یا درجِ ماندهٔ همین دسته) یا null؛ چند ردیفِ باز ⇒ odd
  const tasks = new Map();
  const oddTasks = new Set();
  let pool = [];
  let committed = emptyBuf();
  let pending = emptyBuf();
  let unit = null;               //  {name, sp, undo: []}
  //  داورِ بیرونی‌ای که خودش دفتر را بنویسد (امروز هیچ‌کدام) ⇒ از آن پس هر خواندن از دیتابیس، پس از نوشتنِ مانده‌ها
  let tainted = false;

  //  همان پرسشِ `addConflict` (ردیفِ باز با همین کلید) برای چند کلید با هم؛ دو ردیفِ باز ⇒ همان راهِ تک‌تک
  async function resolveTasks(keys) {
    const need = keys.filter((k) => !tasks.has(k) && !oddTasks.has(k));
    if (!need.length) return;
    const found = new Map();
    for (const r of (await c.query(
      `SELECT dedupe_key, id FROM sync_v3_conflicts WHERE ${ACC} AND dedupe_key = ANY($4::text[]) AND status='open'`,
      [...acc(ctx), need])).rows) (found.get(r.dedupe_key) || found.set(r.dedupe_key, []).get(r.dedupe_key)).push(r.id);
    for (const k of need) {
      const ids = found.get(k) || [];
      if (ids.length > 1) oddTasks.add(k);
      else tasks.set(k, ids.length ? { id: ids[0] } : null);
    }
  }

  // ---------- پیش‌خوانی ----------
  if (clientIds.size) {
    const dup = (await c.query(`SELECT op_id, server_seq FROM sync_v3_ops WHERE ${ACC} AND op_id = ANY($4::text[])`,
      [...acc(ctx), [...clientIds]])).rows;
    for (const r of dup) known.set(r.op_id, Number(r.server_seq));
    const keys = new Map();
    for (const o of ops) keys.set(keyOf(o.table, o.row_id), [o.table, o.row_id]);
    const list = [...keys.values()];
    for (const k of keys.keys()) { cache.set(k, null); base.set(k, null); }
    const rows = (await c.query(ROWS_SQL, [...acc(ctx), list.map((x) => x[0]), list.map((x) => x[1])])).rows;
    for (const r of rows) {
      const st = fromDb(r);
      cache.set(keyOf(r.table_name, r.row_id), st);
      base.set(keyOf(r.table_name, r.row_id), st);
    }
    const stock = new Set();
    const STOCK = new Set(['stockMovements', 'stockCounts']);
    for (const o of ops) if (STOCK.has(o.table) && o.fields && isText(o.fields.productId)) stock.add(o.fields.productId);
    for (const r of rows) if (STOCK.has(r.table_name) && r.data && isText(r.data.productId)) stock.add(r.data.productId);
    if (stock.size) {
      for (const p of stock) { stockAsked.add(p); sums.set(p, { s: 0n, odd: 0 }); }
      for (const r of (await c.query(STOCK_SQL, [...acc(ctx), [...stock]])).rows) {
        if (r.k === 'm') sums.set(r.p, { s: BigInt(r.s), odd: Number(r.odd) });
        else counted.add(r.p);
      }
      await resolveTasks([...stock].map((p) => `neg:${p}`));
    }
  }

  // ---------- نوشتن ----------
  const writeOut = async (b) => {
    if (isEmpty(b)) return;
    //  به‌روزرسانیِ detail ِ درجی که در همین بافر است روی خودِ درج (UPDATE ِ همان دستور درجِ همان دستور را نمی‌بیند)
    for (const r of b.conflicts) if (b.details.has(r.id)) { r.detail = b.details.get(r.id); b.details.delete(r.id); }
    await c.query(FLUSH_SQL, [...acc(ctx), JSON.stringify(b.ops), JSON.stringify([...b.rows.values()]),
      JSON.stringify(b.conflicts), JSON.stringify([...b.details].map(([id, detail]) => ({ id, detail })))]);
    b.ops = []; b.rows = new Map(); b.conflicts = []; b.details = new Map();
  };
  //  savepoint ِ واحد پیش از نخستین کارِ دیتابیسیِ آن؛ نوشته‌های واحدهای پیشین بیرون از آن
  const ensureSp = async () => {
    if (!unit || unit.sp) return;
    await writeOut(committed);
    await c.query(`SAVEPOINT ${unit.name}`);
    unit.sp = true;
  };
  //  هر نوشتهٔ مانده پیش از خواندنی که دفتر را می‌بیند
  const visible = async () => {
    await writeOut(committed);
    if (unit && !isEmpty(pending)) { await ensureSp(); await writeOut(pending); }
  };
  const visibleFor = async (tables) => {
    const hit = (b) => [...b.rows.values()].some((w) => tables.includes(w.table_name));
    if (hit(committed) || (unit && hit(pending))) await visible();
  };
  const buf = () => (unit ? pending : committed);
  //  اتصال برای داورهای بیرونی: همان پرس‌وجو، همان دم
  const outside = {
    query: async (sql, params) => {
      if (seesLedger(sql)) await visible();
      if (!readOnly(sql)) {
        await ensureSp();
        if (seesLedger(sql)) tainted = true;
      }
      return c.query(sql, params);
    },
  };
  //  تعارضِ dedupe (`openTask`، صندوقِ ردِ پولی): خواندنش فقط `dedupe_key` ِ ناتهی است که هیچ‌کدامِ ماندهٔ
  //  حافظه ندارد ⇒ بی نوشتن، فقط savepoint برای نوشتنش
  const direct = { query: async (sql, params) => { if (!readOnly(sql)) await ensureSp(); return c.query(sql, params); } };

  const remember = (key) => {
    if (unit) unit.undo.push({ key, had: cache.has(key), prev: cache.get(key) });
  };
  const touch = (op, key, st) => {
    if (op.table === 'stockMovements') movesTouched.add(key);
    if (op.table !== 'stockCounts') return;
    for (const x of [st, base.get(key)]) {
      const pid = x && x.data ? x.data.productId : undefined;
      if (isText(pid)) countsTouched.add(pid);
      else if (pid !== undefined && pid !== null) countsOdd = true;
    }
  };

  async function sumMoves(cx, productId, upTo) {
    const s0 = upTo === '' && !tainted ? sums.get(productId) : null;
    let total = s0 && !s0.odd ? s0.s : null;
    for (const k of total === null ? [] : movesTouched) {
      const now = moveOf(cache.get(k), productId);
      const was = moveOf(base.get(k), productId);
      if (now === ODD || was === ODD) { total = null; break; }
      total += now - was;
    }
    if (total !== null && total <= BigInt(Number.MAX_SAFE_INTEGER) && total >= -BigInt(Number.MAX_SAFE_INTEGER)) {
      return Number(total);
    }
    await visibleFor(['stockMovements']);
    return store.sumMoves(c, cx, productId, upTo);
  }

  return {
    c: outside,
    async dup(cx, opId) {
      if (clientIds.has(opId) && left > 0) left--;
      if (tainted) { await visible(); return store.findDup(c, cx, opId); }
      if (known.has(opId)) return { server_seq: known.get(opId) };
      if (asked.has(opId)) return undefined;
      const r = await store.findDup(c, cx, opId);
      asked.add(opId);
      if (r) known.set(opId, Number(r.server_seq));
      return r;
    },
    async load(cx, table, rowId) {
      const key = keyOf(table, rowId);
      if (tainted) await visible();
      if (!cache.has(key) || tainted) {
        await ensureSp();   //  FOR UPDATE ِ ردیفِ تازه (opِ خودِ سرور)، مثلِ راهِ تک‌تک درونِ واحد
        const st = await store.loadRow(c, cx, table, rowId);
        if (tainted) remember(key);
        cache.set(key, st);
        if (!base.has(key)) base.set(key, st);
      }
      return clone(cache.get(key));
    },
    async seq() {
      if (!pool.length) {
        const n = Math.min(MAX_SEQ_BLOCK, Math.max(1, left + 1));
        pool = (await c.query(
          `SELECT nextval(pg_get_serial_sequence('sync_v3_ops','server_seq')) AS s FROM generate_series(1, $1::int)`, [n]
        )).rows.map((r) => Number(r.s)).sort((a, b) => a - b);
      }
      return pool.shift();
    },
    async insertOp(cx, r) {
      const rec = opRecord(r);
      if (rec) buf().ops.push(rec);
      else { await ensureSp(); await store.insertOp(c, cx, r); }
      known.set(r.op_id, r.seq);
      if (unit) unit.undo.push({ op: r.op_id });
    },
    async save(cx, op, row, seq, t) {
      const key = keyOf(op.table, op.row_id);
      const rec = rowRecord(store.rowParams(op, row, seq, t));
      remember(key);
      let st;
      if (rec) {
        buf().rows.set(key, rec);
        st = stateOf(rec);
      } else {
        await visible();
        await ensureSp();
        await store.saveRow(c, cx, op, row, seq, t);
        st = await store.loadRow(c, cx, op.table, op.row_id);
      }
      cache.set(key, st);
      touch(op, key, st);
    },
    async conflict(cx, cf, kind, t, opts = {}) {
      const { dedupe = '', detail = {} } = opts;
      if (dedupe) {
        if (tainted) { await visible(); return store.addConflict(direct, cx, cf, kind, t, { dedupe, detail }); }
        if (isText(dedupe)) await resolveTasks([dedupe]);
        if (!isText(dedupe) || oddTasks.has(dedupe)) return store.addConflict(direct, cx, cf, kind, t, { dedupe, detail });
        const text = JSON.stringify(detail);
        const open = tasks.get(dedupe);
        if (open && isText(text)) {
          buf().details.set(open.id, text);   //  همان UPDATE ِ detail؛ آخری می‌ماند (`writeOut`)
          return open.id;
        }
        if (!open) {
          const id = newId('cf');
          const rec = conflictRecord(id, store.conflictParams(cf, kind, t, dedupe, detail));
          if (rec) {
            buf().conflicts.push(rec);
            if (unit) unit.undo.push({ task: dedupe, prev: null });
            tasks.set(dedupe, { id });
            return id;
          }
        }
        //  شکلِ ناآشنا ⇒ همان SQL، و کلید از این پس همان راه
        oddTasks.add(dedupe);
        await visible();
        return store.addConflict(direct, cx, cf, kind, t, { dedupe, detail });
      }
      const id = newId('cf');
      const rec = conflictRecord(id, store.conflictParams(cf, kind, t, '', detail));
      if (rec) { buf().conflicts.push(rec); return id; }
      await ensureSp();
      return store.addConflict(c, cx, cf, kind, t, { dedupe, detail });
    },
    sumMoves,
    async lateCount(cx, productId, eff, countId) {
      if (!tainted && stockAsked.has(productId) && !counted.has(productId) && !countsTouched.has(productId) && !countsOdd) return undefined;
      await visibleFor(['stockCounts']);
      return store.lateCount(c, cx, productId, eff, countId);
    },
    async barcodeOthers(cx, table, rowId, code) {
      await visibleFor([table]);
      return store.barcodeOthers(c, cx, table, rowId, code);
    },
    async phoneOthers(cx, table, key, rowId) {
      await visibleFor([table]);
      return store.phoneOthers(c, cx, table, key, rowId);
    },
    async begin(name) { unit = { name, sp: false, undo: [] }; pending = emptyBuf(); },
    async commit(name) {
      const u = unit;
      unit = null;
      if (u.sp) await c.query(`RELEASE SAVEPOINT ${name}`);
      committed.ops.push(...pending.ops);
      for (const [k, w] of pending.rows) { committed.rows.delete(k); committed.rows.set(k, w); }
      committed.conflicts.push(...pending.conflicts);
      for (const [id, d] of pending.details) committed.details.set(id, d);
      pending = emptyBuf();
    },
    async undo(name) {
      const u = unit;
      unit = null;
      if (u.sp) await c.query(`ROLLBACK TO SAVEPOINT ${name}`);
      pending = emptyBuf();
      for (const x of u.undo.reverse()) {
        if (x.op) known.delete(x.op);
        else if (x.task) tasks.set(x.task, x.prev);
        else if (x.had) cache.set(x.key, x.prev);
        else cache.delete(x.key);
      }
    },
    flush: () => writeOut(committed),
  };
}

module.exports = { on, open, jsonbOrder, readOnly, seesLedger };
