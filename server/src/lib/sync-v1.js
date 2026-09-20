'use strict';
/**
 * VILL3N Sync v1 — سمتِ سرور (بخشِ ۲۰ی پرامپت).
 *
 * ── یک نگاه ─────────────────────────────────────────────────────────
 *   push      دسته‌ای از opها (حداکثر ۲۰۰ یا ۲۵۶KB)، یک تراکنش، پاسخِ
 *             هر op جدا: applied | duplicate | rejected(reason)
 *   pull      opهای **دستگاه‌های دیگر** بعد از cursor
 *   snapshot  همهٔ ردیف‌های زنده + cursor — فقط برای دستگاهِ تازه
 *   conflicts نسخهٔ بازندهٔ هر تعارضِ فیلدی، با بازگردانی
 *   deleted   ردیف‌های حذف‌شده (نرم)، با بازگردانی
 *
 * ── قاعده‌هایی که این‌جا زنده‌اند ──────────────────────────────────
 *   • **حساب از توکن** — `ctx` را نگهبانِ `lib/sync-v1-auth.js` می‌سازد،
 *     و هیچ تابعی این‌جا `account` را از بدنهٔ درخواست نمی‌خواند.
 *   • **هر پرس‌وجو `app` + `account_kind` + `account_id` را شرط می‌کند.**
 *   • **تعارض سطحِ فیلد است**، نه رکورد: دو دستگاه که دو فیلدِ یک ردیف
 *     را عوض کنند، هر دو می‌مانند. یک فیلد ⇒ ترتیبِ رسیدن به سرور
 *     برنده، بازنده در `sync_conflicts`.
 *   • **`{"$inc": n}` دلتا است** و جمع می‌شود، تا دو فروشِ هم‌زمان از
 *     دو دستگاه هر دو از موجودی کم شوند.
 *   • **حذف نرم است** (`deleted_at`)، و ویرایشِ بعد از حذف ردیف را
 *     زنده می‌کند.
 *   • **`op_id` یکتاست** ⇒ فرستادنِ دوباره «تکراری» است، نه دو بار.
 *
 * ── «تعارض» دقیقاً کِی است ───────────────────────────────────────
 * هر ویرایشِ پشتِ سرِ همِ یک فیلد تعارض نیست — کاربر همان اسم را دو
 * بار عوض کرده. تعارض وقتی است که فیلد را **دستگاهِ دیگری** نوشته و
 * این دستگاه آن نوشته را **ندیده** بود: یعنی `server_seq`ِ نوشتهٔ قبلی
 * از cursorِ این دستگاه (آخرین pullش) بزرگ‌تر است. پس دستگاهی که اول
 * pull کرده و بعد آگاهانه همان فیلد را عوض می‌کند، تعارض نمی‌سازد.
 */
const { createHash } = require('crypto');
const zlib = require('zlib');
const { one, many, tx, newId, now } = require('../db');
const { ApiError, badRequest, notFound } = require('../middleware/errors');
const migrations = require('./sync-v1-migrations');
const { TABLES: SHOP_TABLES } = require('./sync');

// ---------- جدول‌های مجاز ----------
//
//  نامِ جدول از درخواست می‌آید و هر نامِ بیرونِ این فهرست رد می‌شود.
//  دکان: همان مجموعه‌های `lib/sync.js`. پمپ: موجودیت‌های دفترِ برنامهٔ
//  پمپ (`native/PumpYaqobi.Domain/Entities/*.cs`).
//
//  ⚠️ نام‌ها با هر شکلِ نوشتن پذیرفته می‌شوند (`DebtRow`، `debt_row`،
//  `debtrow`) و به نامِ **قانونی** برمی‌گردند؛ برنامهٔ اندروید و
//  برنامهٔ کامپیوتر لازم نیست سرِ حروفِ بزرگ و کوچک با هم قرار بگذارند.

const PUMP_TABLES = [
  //  گاوصندوق، صرافی، مصارف، پرچون
  'SafeEntry', 'ExchangeRow', 'Expense', 'RetailRow',
  //  قرض‌داران
  'Debtor', 'DebtAccount', 'RasidEntry', 'DebtRow', 'DebtQuickReceipt', 'DebtTableArchive',
  //  شیفت، پارچه، مخزن
  'ShiftData', 'ParchaReport', 'ParchaReceipt', 'FuelPurchase', 'TankDip', 'TankerUnload',
  //  شرکت‌ها و امانت
  'TilCompany', 'CompanyTableArchive', 'CompanyRow', 'AmanatAccount', 'AmanatRow',
  //  ورق‌ها
  'WaraqEntry', 'WaraqShift', 'WaraqPump', 'WaraqTransaction',
  //  فاکتور، کارمندان
  'Invoice', 'StaffMember', 'AttendanceRow', 'SalaryPayment', 'StaffShortage', 'StaffShortSettle',
  //  بقیه
  'VoiceTemplate', 'Camera', 'ExtraIncome', 'RateHistoryEntry', 'SectionNote', 'SectionNoteDraft',
  //  ⚠️ `AppUser` (رمزِ محلی)، `Setting`، `TrashItem` و `AuditEntry`
  //  عمداً نیستند: مالِ همان کامپیوترند، نه دفترِ مشترک.
];

function normName(s) { return String(s || '').toLowerCase().replace(/[^a-z0-9]/g, ''); }
function index(names) {
  const m = new Map();
  for (const n of names) m.set(normName(n), n);
  return m;
}
const WHITELIST = Object.freeze({
  shop: index(Object.keys(SHOP_TABLES)),
  pump: index(PUMP_TABLES),
});

/** نامِ قانونیِ جدول، یا `''` اگر مجاز نباشد. */
function tableOf(app, raw) {
  const m = WHITELIST[app];
  if (!m) return '';
  return m.get(normName(raw)) || '';
}

/** فهرستِ جدول‌های مجازِ یک بخش — برای `/status` و مستندات. */
function tablesOf(app) {
  return WHITELIST[app] ? [...WHITELIST[app].values()] : [];
}

// ---------- سقف‌ها ----------
const MAX_OPS = 200;
const MAX_BATCH_BYTES = 256 * 1024;
const MAX_FIELDS_BYTES = 64 * 1024;
const MAX_PULL = 1000;
const DEFAULT_PULL = 500;
const SERVER_DEVICE = 'server';

// ---------- اثرِ انگشتِ op ----------

/** JSON با کلیدهای مرتب — تا اثرِ انگشت روی هر زبان و هر کتابخانه‌ای یکی باشد. */
function canonical(value) {
  if (value === null || typeof value !== 'object') return JSON.stringify(value);
  if (Array.isArray(value)) return `[${value.map(canonical).join(',')}]`;
  const keys = Object.keys(value).sort();
  return `{${keys.map(k => `${JSON.stringify(k)}:${canonical(value[k])}`).join(',')}}`;
}

/**
 * اثرِ انگشتِ یک op — `sha256("<table>|<row_id>|<type>|<fields canonical>")`.
 *
 * اختیاری است: برنامه‌ای که می‌فرستد، سرور می‌سنجد و ناجورش رد می‌شود.
 * نامِ جدول همان است که برنامه فرستاده (پیش از قانونی شدن).
 */
function hashOf({ table, row_id, type, fields }) {
  return createHash('sha256')
    .update(`${table}|${row_id}|${type}|${canonical(fields || {})}`)
    .digest('hex');
}

// ---------- سنجشِ یک op ----------

function isPlainObject(v) {
  return v !== null && typeof v === 'object' && !Array.isArray(v);
}
function isInc(v) {
  return isPlainObject(v) && Object.keys(v).length === 1 && Number.isFinite(v.$inc);
}
function deepEqual(a, b) { return canonical(a) === canonical(b); }

const OP_ID_RE = /^[A-Za-z0-9_-]{1,64}$/;
const TYPES = ['insert', 'update', 'delete'];

/**
 * نام‌های هر دو سبک پذیرفته می‌شوند (`op_id` و `opId`، `table` و
 * `table_name`، …). خروجی همیشه یک شکل دارد.
 */
function normalize(raw) {
  const r = isPlainObject(raw) ? raw : {};
  return {
    op_id: r.op_id ?? r.opId ?? '',
    table: r.table ?? r.table_name ?? r.tableName ?? '',
    row_id: r.row_id ?? r.rowId ?? r.id ?? '',
    type: r.type ?? r.op_type ?? r.opType ?? '',
    fields: r.fields,
    ts: Number(r.ts ?? r.client_ts ?? r.clientTs ?? 0) || 0,
    hash: r.hash ? String(r.hash) : '',
  };
}

/** @returns {{ok:true, op:object} | {ok:false, reason:string, op_id:string}} */
function validateOp(app, raw) {
  const op = normalize(raw);
  const bad = (reason) => ({ ok: false, reason, op_id: typeof op.op_id === 'string' ? op.op_id.slice(0, 64) : '' });

  if (typeof op.op_id !== 'string' || !OP_ID_RE.test(op.op_id)) return bad('bad_op_id');
  const table = tableOf(app, op.table);
  if (!table) return bad('unknown_table');
  if (typeof op.row_id !== 'string' || !op.row_id || op.row_id.length > 80) return bad('bad_row_id');
  if (!TYPES.includes(op.type)) return bad('bad_type');

  let fields = {};
  if (op.type === 'delete') {
    //  حذف فیلد ندارد — چند صد بایت، همین
    if (op.fields !== undefined && op.fields !== null && !(isPlainObject(op.fields) && !Object.keys(op.fields).length)) {
      return bad('delete_has_fields');
    }
  } else {
    if (!isPlainObject(op.fields)) return bad('bad_fields');
    if (op.type === 'insert' && !Object.keys(op.fields).length) return bad('empty_insert');
    for (const [k, v] of Object.entries(op.fields)) {
      if (!k || k.length > 64 || k.startsWith('$')) return bad('bad_field_name');
      if (isPlainObject(v) && Object.keys(v).some(x => x.startsWith('$')) && !(op.type === 'update' && isInc(v))) {
        return bad('bad_delta');
      }
    }
    const json = JSON.stringify(op.fields);
    if (Buffer.byteLength(json, 'utf8') > MAX_FIELDS_BYTES) return bad('fields_too_large');
    fields = op.fields;
  }

  //  اثرِ انگشت روی همان چیزی سنجیده می‌شود که برنامه فرستاده
  if (op.hash && op.hash !== hashOf({ table: op.table, row_id: op.row_id, type: op.type, fields })) {
    return bad('hash_mismatch');
  }

  return { ok: true, op: { ...op, table, fields, hash: op.hash } };
}

// ---------- اعمالِ یک op روی حالِ یک ردیف ----------

/**
 * @param row ردیفِ فعلیِ `sync_rows` یا null
 * @param deviceCursor آخرین cursorی که این دستگاه pull کرده
 * @returns {{data, field_seq, field_src, deleted_at, conflicts:Array}}
 */
function applyOp(row, op, seq, deviceId, t, deviceCursor) {
  const data = row ? { ...row.data } : {};
  const fseq = row ? { ...row.field_seq } : {};
  const fsrc = row ? { ...row.field_src } : {};
  const conflicts = [];

  if (op.type === 'delete') {
    return {
      data, field_seq: fseq, field_src: fsrc,
      deleted_at: row && row.deleted_at ? Number(row.deleted_at) : t,
      conflicts,
    };
  }

  for (const [f, v] of Object.entries(op.fields)) {
    const prevSeq = Number(fseq[f] || 0);
    const prev = isPlainObject(fsrc[f]) ? fsrc[f] : { d: '', o: '' };

    if (isInc(v)) {
      //  دلتا جمع می‌شود — همین است که دو فروشِ هم‌زمان هر دو حساب می‌شوند
      data[f] = (Number(data[f]) || 0) + v.$inc;
    } else {
      const unseen = row && prevSeq > 0 && prev.d !== deviceId && prev.d !== SERVER_DEVICE
        && prevSeq > deviceCursor && Object.prototype.hasOwnProperty.call(data, f);
      if (unseen && !deepEqual(data[f], v)) {
        conflicts.push({
          field: f, loser_value: data[f], winner_value: v,
          loser_op_id: prev.o || '', loser_device: prev.d || '',
        });
      }
      data[f] = v;
    }
    fseq[f] = seq;
    fsrc[f] = { d: deviceId, o: op.op_id };
  }

  //  ویرایش یا درجِ دوباره بعد از حذف ⇒ ردیف زنده می‌شود
  return { data, field_seq: fseq, field_src: fsrc, deleted_at: null, conflicts };
}

// ---------- پرس‌وجوهای مشترک ----------

const ACC = 'app=$1 AND account_kind=$2 AND account_id=$3';
const accArgs = (ctx) => [ctx.app, ctx.accountKind, ctx.accountId];

async function headSeq(q, ctx) {
  const r = (await q.query(
    `SELECT COALESCE(MAX(server_seq),0)::bigint AS head FROM oplog WHERE ${ACC}`, accArgs(ctx)
  )).rows[0];
  return Number(r.head);
}

async function deviceRow(q, ctx, deviceId) {
  return (await q.query(
    `SELECT * FROM sync_devices WHERE ${ACC} AND device_id=$4`, [...accArgs(ctx), deviceId]
  )).rows[0] || null;
}

async function touchDevice(q, ctx, deviceId, patch) {
  const t = now();
  const cols = {
    cursor: patch.cursor, last_push_at: patch.lastPushAt, last_pull_at: patch.lastPullAt,
    last_op_at: patch.lastOpAt, queued_count: patch.queued, app_version: patch.appVersion,
    schema_version: patch.schemaVersion,
  };
  await q.query(
    `INSERT INTO sync_devices (app, account_kind, account_id, device_id, cursor, last_push_at, last_pull_at,
                               last_op_at, queued_count, app_version, schema_version, last_seen_at)
     VALUES ($1,$2,$3,$4, COALESCE($5,0), $6, $7, $8, COALESCE($9,0), COALESCE($10,''), COALESCE($11,0), $12)
     ON CONFLICT (app, account_kind, account_id, device_id) DO UPDATE SET
       cursor         = GREATEST(sync_devices.cursor, COALESCE(excluded.cursor, 0)),
       last_push_at   = COALESCE(excluded.last_push_at, sync_devices.last_push_at),
       last_pull_at   = COALESCE(excluded.last_pull_at, sync_devices.last_pull_at),
       last_op_at     = COALESCE(excluded.last_op_at, sync_devices.last_op_at),
       queued_count   = CASE WHEN $9::int IS NULL THEN sync_devices.queued_count ELSE excluded.queued_count END,
       app_version    = CASE WHEN $10::text IS NULL OR $10 = '' THEN sync_devices.app_version ELSE excluded.app_version END,
       schema_version = CASE WHEN $11::int IS NULL THEN sync_devices.schema_version ELSE excluded.schema_version END,
       last_seen_at   = excluded.last_seen_at`,
    [...accArgs(ctx), deviceId, cols.cursor ?? null, cols.last_push_at ?? null, cols.last_pull_at ?? null,
      cols.last_op_at ?? null, cols.queued_count ?? null, cols.app_version ?? null, cols.schema_version ?? null, t]
  );
}

// ---------- Push ----------

/**
 * @param ctx {app, accountKind, accountId}
 * @returns {{results:Array, head:number, applied:number, schema_version:number, upgrade_available:boolean}}
 * @throws 426 اگر برنامه از سرور جدیدتر باشد — و هیچ چیزی اعمال نمی‌شود
 */
async function push(ctx, { deviceId, schemaVersion, ops, appVersion = '', queued = null }) {
  if (!Array.isArray(ops)) throw badRequest('فهرستِ opها درست نیست', 'bad_ops');
  if (ops.length > MAX_OPS) throw badRequest(`حداکثر ${MAX_OPS} op در هر دسته`, 'batch_too_large');

  const current = migrations.currentVersion(ctx.app);
  if (!current) throw badRequest('این بخش همگام‌سازی ندارد', 'no_schema');
  const clientSchema = Number(schemaVersion);
  if (!Number.isInteger(clientSchema) || clientSchema < 1) throw badRequest('schema_version لازم است', 'bad_schema_version');
  if (clientSchema > current) {
    //  برنامه جلوتر از سرور است. opها روی خودِ برنامه می‌مانند تا سرور
    //  به‌روز شود — این‌جا هیچ چیزی نوشته نمی‌شود.
    throw new ApiError(426, 'UPGRADE_REQUIRED',
      `سرور schema نسخهٔ ${current} را می‌شناسد و برنامه نسخهٔ ${clientSchema} می‌فرستد؛ سرور باید به‌روز شود`);
  }
  const upgradeAvailable = clientSchema < current;
  const t = now();

  return tx(async (c) => {
    const dev = await deviceRow(c, ctx, deviceId);
    const deviceCursor = dev ? Number(dev.cursor) : 0;
    const results = [];
    let applied = 0;
    let lastOpAt = null;

    for (const raw of ops) {
      const val = validateOp(ctx.app, raw);
      if (!val.ok) { results.push({ op_id: val.op_id, status: 'rejected', reason: val.reason }); continue; }
      let op = val.op;
      if (upgradeAvailable) op = { ...migrations.upgrade(ctx.app, clientSchema, op), hash: '' };

      //  تکراری؟ همان op_id، همان حساب ⇒ «تکراری»؛ حسابِ دیگر ⇒ رد.
      const dup = (await c.query(
        'SELECT app, account_kind, account_id, server_seq FROM oplog WHERE op_id=$1', [op.op_id]
      )).rows[0];
      if (dup) {
        const mine = dup.app === ctx.app && dup.account_kind === ctx.accountKind && dup.account_id === ctx.accountId;
        results.push(mine
          ? { op_id: op.op_id, status: 'duplicate', server_seq: Number(dup.server_seq) }
          : { op_id: op.op_id, status: 'rejected', reason: 'op_id_taken' });
        continue;
      }

      //  ردیف را قفل می‌کنیم تا دو دستگاه هم‌زمان روی هم ننویسند
      const row = (await c.query(
        `SELECT * FROM sync_rows WHERE ${ACC} AND table_name=$4 AND row_id=$5 FOR UPDATE`,
        [...accArgs(ctx), op.table, op.row_id]
      )).rows[0] || null;

      const ins = await c.query(
        `INSERT INTO oplog (id, app, account_kind, account_id, device_id, op_id, client_ts, table_name, row_id,
                            op_type, fields, hash, schema_version, received_at)
         VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11::jsonb,$12,$13,$14) RETURNING server_seq`,
        [newId('op'), ctx.app, ctx.accountKind, ctx.accountId, deviceId, op.op_id, op.ts, op.table, op.row_id,
          op.type, JSON.stringify(op.fields || {}), op.hash || '', current, t]
      );
      const seq = Number(ins.rows[0].server_seq);

      const out = applyOp(row, op, seq, deviceId, t, deviceCursor);
      await c.query(
        `INSERT INTO sync_rows (app, account_kind, account_id, table_name, row_id, data, field_seq, field_src,
                                deleted_at, updated_seq, created_at, updated_at)
         VALUES ($1,$2,$3,$4,$5,$6::jsonb,$7::jsonb,$8::jsonb,$9,$10,$11,$11)
         ON CONFLICT (app, account_kind, account_id, table_name, row_id) DO UPDATE SET
           data = excluded.data, field_seq = excluded.field_seq, field_src = excluded.field_src,
           deleted_at = excluded.deleted_at, updated_seq = excluded.updated_seq, updated_at = excluded.updated_at`,
        [...accArgs(ctx), op.table, op.row_id, JSON.stringify(out.data), JSON.stringify(out.field_seq),
          JSON.stringify(out.field_src), out.deleted_at, seq, t]
      );
      for (const cf of out.conflicts) {
        await c.query(
          `INSERT INTO sync_conflicts (app, account_kind, account_id, table_name, row_id, field, loser_value,
                                       winner_value, loser_op_id, winner_op_id, loser_device, winner_device, at)
           VALUES ($1,$2,$3,$4,$5,$6,$7::jsonb,$8::jsonb,$9,$10,$11,$12,$13)`,
          [...accArgs(ctx), op.table, op.row_id, cf.field, JSON.stringify(cf.loser_value ?? null),
            JSON.stringify(cf.winner_value ?? null), cf.loser_op_id, op.op_id, cf.loser_device, deviceId, t]
        );
      }
      results.push({ op_id: op.op_id, status: 'applied', server_seq: seq, conflicts: out.conflicts.length });
      applied++;
      lastOpAt = t;
    }

    await touchDevice(c, ctx, deviceId, {
      lastPushAt: t, lastOpAt, queued, appVersion, schemaVersion: clientSchema,
    });
    return {
      results, applied, head: await headSeq(c, ctx),
      schema_version: current, upgrade_available: upgradeAvailable,
    };
  });
}

// ---------- Pull ----------

async function pull(ctx, { since = 0, deviceId, limit = DEFAULT_PULL }) {
  const from = Math.max(0, Number(since) || 0);
  const cap = Math.min(Math.max(1, Number(limit) || DEFAULT_PULL), MAX_PULL);
  const rows = await many(
    `SELECT op_id, device_id, server_seq, client_ts, table_name, row_id, op_type, fields, schema_version, received_at
       FROM oplog WHERE ${ACC} AND server_seq > $4 AND device_id <> $5
      ORDER BY server_seq ASC LIMIT $6`,
    [...accArgs(ctx), from, deviceId, cap + 1]
  );
  const hasMore = rows.length > cap;
  const page = hasMore ? rows.slice(0, cap) : rows;
  const head = await headSeq({ query: (s, p) => require('../db').query(s, p) }, ctx);
  //  اگر همه آمد، cursor سرِ دفتر است — opهای خودِ همین دستگاه هم پشتِ
  //  سرش می‌مانند و دیگر لازم نیست دوباره از رویشان رد شود.
  const cursor = hasMore ? Number(page[page.length - 1].server_seq) : Math.max(head, from);
  await touchDevice({ query: (s, p) => require('../db').query(s, p) }, ctx, deviceId, {
    cursor, lastPullAt: now(),
  });
  return {
    ops: page.map(r => ({
      op_id: r.op_id, device_id: r.device_id, server_seq: Number(r.server_seq), ts: Number(r.client_ts),
      table: r.table_name, row_id: r.row_id, type: r.op_type, fields: r.fields,
      schema_version: r.schema_version, received_at: Number(r.received_at),
    })),
    cursor, has_more: hasMore, head,
    schema_version: migrations.currentVersion(ctx.app),
  };
}

// ---------- Snapshot ----------

/**
 * همهٔ ردیف‌های زنده + cursor، **در یک تراکنشِ REPEATABLE READ** — تا
 * cursor و ردیف‌ها از یک لحظه باشند. بی این، opی که وسطِ خواندن می‌رسید
 * یا دو بار روی برنامه می‌نشست (بد برای `$inc`) یا اصلاً نمی‌رسید.
 */
async function snapshot(ctx) {
  return tx(async (c) => {
    try { await c.query('SET TRANSACTION ISOLATION LEVEL REPEATABLE READ'); } catch { /* راه‌اندازی بی این هم می‌خواند */ }
    const head = await headSeq(c, ctx);
    const rows = (await c.query(
      `SELECT table_name, row_id, data FROM sync_rows WHERE ${ACC} AND deleted_at IS NULL
        ORDER BY table_name, row_id`, accArgs(ctx)
    )).rows;
    const tables = {};
    for (const r of rows) {
      (tables[r.table_name] ||= []).push({ id: r.row_id, data: r.data });
    }
    return {
      app: ctx.app, account: { kind: ctx.accountKind, id: ctx.accountId },
      cursor: head, schema_version: migrations.currentVersion(ctx.app),
      rows: rows.length, tables, serverTime: now(),
    };
  });
}

/** همان snapshot، فشرده — برای فرستادن روی خط. */
async function snapshotGzip(ctx) {
  const json = JSON.stringify(await snapshot(ctx));
  return zlib.gzipSync(Buffer.from(json, 'utf8'));
}

// ---------- وضعیت ----------

async function status(ctx, deviceId = '') {
  const db = require('../db');
  const q = { query: db.query };
  const head = await headSeq(q, ctx);
  const dev = deviceId ? await deviceRow(q, ctx, deviceId) : null;
  const counts = await one(
    `SELECT
       (SELECT COUNT(*)::int FROM sync_conflicts WHERE ${ACC} AND restored_at IS NULL) AS conflicts,
       (SELECT COUNT(*)::int FROM sync_rows WHERE ${ACC} AND deleted_at IS NOT NULL) AS deleted,
       (SELECT COUNT(*)::int FROM sync_rows WHERE ${ACC} AND deleted_at IS NULL) AS live,
       (SELECT COUNT(*)::int FROM sync_devices WHERE ${ACC}) AS devices`,
    accArgs(ctx)
  );
  return {
    app: ctx.app, account: { kind: ctx.accountKind, id: ctx.accountId },
    head, schema_version: migrations.currentVersion(ctx.app),
    tables: tablesOf(ctx.app),
    device: dev ? shapeDevice(dev) : null,
    conflicts: counts.conflicts, deleted: counts.deleted, rows: counts.live, devices: counts.devices,
    limits: { max_ops: MAX_OPS, max_batch_bytes: MAX_BATCH_BYTES, max_fields_bytes: MAX_FIELDS_BYTES, max_pull: MAX_PULL },
  };
}

function shapeDevice(d) {
  return {
    device_id: d.device_id, cursor: Number(d.cursor),
    last_push_at: d.last_push_at ? Number(d.last_push_at) : null,
    last_pull_at: d.last_pull_at ? Number(d.last_pull_at) : null,
    last_op_at: d.last_op_at ? Number(d.last_op_at) : null,
    queued_count: Number(d.queued_count), app_version: d.app_version,
    schema_version: Number(d.schema_version), last_seen_at: Number(d.last_seen_at),
  };
}

// ---------- پنل: وضعیت، تعارض‌ها، حذف‌شده‌ها ----------

/** وضعیتِ هر حساب و دستگاه در یک بخش — برای پنلِ مدیریت. */
async function adminStatus(app, { accountId = '', limit = 100 } = {}) {
  const kind = app === 'pump' ? 'station' : 'shop';
  const args = [app, kind];
  let where = 'd.app=$1 AND d.account_kind=$2';
  if (accountId) { args.push(accountId); where += ` AND d.account_id=$${args.length}`; }
  args.push(Math.min(Math.max(1, Number(limit) || 100), 500));
  const rows = await many(
    `SELECT d.*,
            (SELECT COALESCE(MAX(server_seq),0) FROM oplog o
              WHERE o.app=d.app AND o.account_kind=d.account_kind AND o.account_id=d.account_id) AS head,
            (SELECT COUNT(*)::int FROM sync_conflicts c
              WHERE c.app=d.app AND c.account_kind=d.account_kind AND c.account_id=d.account_id
                AND c.restored_at IS NULL) AS conflicts,
            (SELECT COUNT(*)::int FROM sync_rows r
              WHERE r.app=d.app AND r.account_kind=d.account_kind AND r.account_id=d.account_id
                AND r.deleted_at IS NOT NULL) AS deleted
       FROM sync_devices d WHERE ${where}
      ORDER BY d.last_seen_at DESC LIMIT $${args.length}`,
    args
  );
  return rows.map(r => ({
    account: { kind: r.account_kind, id: r.account_id },
    ...shapeDevice(r),
    head: Number(r.head), behind: Math.max(0, Number(r.head) - Number(r.cursor)),
    conflicts: r.conflicts, deleted: r.deleted,
  }));
}

function shapeConflict(r) {
  return {
    id: Number(r.id), app: r.app, account: { kind: r.account_kind, id: r.account_id },
    table: r.table_name, row_id: r.row_id, field: r.field,
    loser_value: r.loser_value, winner_value: r.winner_value,
    loser_op_id: r.loser_op_id, winner_op_id: r.winner_op_id,
    loser_device: r.loser_device, winner_device: r.winner_device,
    at: Number(r.at), restored_at: r.restored_at ? Number(r.restored_at) : null,
  };
}

async function listConflicts(ctx, { limit = 100, includeRestored = false } = {}) {
  const rows = await many(
    `SELECT * FROM sync_conflicts WHERE ${ACC} ${includeRestored ? '' : 'AND restored_at IS NULL'}
      ORDER BY at DESC, id DESC LIMIT $4`,
    [...accArgs(ctx), Math.min(Math.max(1, Number(limit) || 100), 500)]
  );
  return rows.map(shapeConflict);
}

/**
 * بازگردانیِ نسخهٔ بازنده — به شکلِ یک opِ تازه از طرفِ **سرور**.
 *
 * چیزی «برگردانده» نمی‌شود؛ یک ویرایشِ تازه در دفتر می‌نشیند که مقدارِ
 * بازنده را دوباره می‌نویسد. پس همهٔ دستگاه‌ها در pullِ بعدی همان را
 * می‌گیرند، درست مثلِ هر تغییرِ دیگری، و تاریخچه هم دست نمی‌خورد.
 */
async function restoreConflict(id) {
  const cf = await one('SELECT * FROM sync_conflicts WHERE id=$1', [Number(id) || 0]);
  if (!cf) throw notFound('این تعارض پیدا نشد', 'conflict_not_found');
  const ctx = { app: cf.app, accountKind: cf.account_kind, accountId: cf.account_id };
  const op = {
    op_id: `srv_${newId('restore').replace(/[^A-Za-z0-9_-]/g, '')}`.slice(0, 64),
    table: cf.table_name, row_id: cf.row_id, type: 'update', fields: { [cf.field]: cf.loser_value },
  };
  const out = await push(ctx, {
    deviceId: SERVER_DEVICE, schemaVersion: migrations.currentVersion(cf.app), ops: [op],
  });
  await require('../db').query('UPDATE sync_conflicts SET restored_at=$2 WHERE id=$1', [cf.id, now()]);
  return { ctx, op, head: out.head, result: out.results[0] };
}

async function listDeleted(ctx, { limit = 100 } = {}) {
  const rows = await many(
    `SELECT table_name, row_id, data, deleted_at, updated_seq FROM sync_rows
      WHERE ${ACC} AND deleted_at IS NOT NULL ORDER BY deleted_at DESC LIMIT $4`,
    [...accArgs(ctx), Math.min(Math.max(1, Number(limit) || 100), 500)]
  );
  return rows.map(r => ({
    table: r.table_name, row_id: r.row_id, data: r.data,
    deleted_at: Number(r.deleted_at), updated_seq: Number(r.updated_seq),
  }));
}

/** زنده کردنِ ردیفِ حذف‌شده — باز هم یک opِ تازه از طرفِ سرور. */
async function restoreDeleted(ctx, { table, rowId }) {
  const canon = tableOf(ctx.app, table);
  if (!canon) throw badRequest('جدول شناخته نشد', 'unknown_table');
  const row = await one(
    `SELECT * FROM sync_rows WHERE ${ACC} AND table_name=$4 AND row_id=$5`, [...accArgs(ctx), canon, rowId]
  );
  if (!row) throw notFound('این ردیف پیدا نشد', 'row_not_found');
  if (!row.deleted_at) return { restored: false, head: await headSeq({ query: require('../db').query }, ctx) };
  const op = {
    op_id: `srv_${newId('undelete').replace(/[^A-Za-z0-9_-]/g, '')}`.slice(0, 64),
    table: canon, row_id: rowId, type: 'update', fields: {},
  };
  const out = await push(ctx, {
    deviceId: SERVER_DEVICE, schemaVersion: migrations.currentVersion(ctx.app), ops: [op],
  });
  return { restored: true, op, head: out.head, result: out.results[0] };
}

/** `app_schema` را با آن‌چه در کد است هم‌قد می‌کند — سرِ بالا آمدن. */
async function syncSchemaTable() {
  const db = require('../db');
  for (const [app, version] of Object.entries(migrations.CURRENT)) {
    await db.query(
      `INSERT INTO app_schema (app, schema_version, updated_at) VALUES ($1,$2,$3)
       ON CONFLICT (app) DO UPDATE SET schema_version=excluded.schema_version, updated_at=excluded.updated_at
       WHERE app_schema.schema_version <> excluded.schema_version`,
      [app, version, now()]
    );
  }
}

module.exports = {
  MAX_OPS, MAX_BATCH_BYTES, MAX_FIELDS_BYTES, MAX_PULL, SERVER_DEVICE, PUMP_TABLES,
  tableOf, tablesOf, hashOf, canonical, validateOp, applyOp,
  push, pull, snapshot, snapshotGzip, status,
  adminStatus, listConflicts, restoreConflict, listDeleted, restoreDeleted, syncSchemaTable,
};
