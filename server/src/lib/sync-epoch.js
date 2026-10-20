'use strict';
/**
 * «دورانِ بازیابی» ِ همگام‌سازی (restore epoch) — §۹.۵ «بازیابی تا آخرین پشتیبان و سپس
 * اعمالِ opهای صف»، سناریوی E2E-OPS-14.replay.
 *
 * ── مشکل ───────────────────────────────────────────────────────────────────
 * سرور از پشتیبان برمی‌گردد ⇒ `oplog` و دنبالهٔ `server_seq` به لحظهٔ پشتیبان برمی‌گردند.
 * دستگاهی که پس از پشتیبان فروخته بود، opهایش را «اعمال‌شده» گرفته و از صف بیرون کرده،
 * و cursorش از سرِ دفترِ سرور جلوتر است ⇒ فروش گم می‌شود و دفترها از هم جدا می‌شوند.
 * «head < cursor» بس نیست: دستگاهِ دیگری که زودتر برسد head را دوباره بالا می‌برد.
 *
 * ── راه ────────────────────────────────────────────────────────────────────
 * در `app_config` (`sync.epoch`):
 *   { id: شناسهٔ بازیابی — فقط با بازیابی عوض می‌شود، boot: شناسهٔ هر بالا آمدن،
 *     seq / seq_v3: بیشینهٔ server_seq ِ oplog / sync_v3_ops در لحظهٔ آخرین بازیابی,
 *     at, restored_at, reason }
 * بالا آمدنِ عادی (`createApp` ⇒ `rotate('boot')`): همان id و seq، فقط boot ِ تازه ⇒ برنامه
 * هیچ کاری نمی‌کند. بازیابی: id ِ تازه و seq = سرِ دفترِ همان لحظه. بازیابی از دو راه شناخته
 * می‌شود:
 *   • نشانِ پشتیبان: `backup.run` پیش از گرفتنِ پشتیبان ردیفِ `sync.backup_marker.<id>` را
 *     می‌نشاند و پس از آن پاک می‌کند ⇒ فقط دیتابیسی که از پشتیبان آمده (یا سروری که وسطِ
 *     پشتیبان افتاد — بی‌ضرر) آن را دارد؛ نخستین بالا آمدن همان را بازیابی می‌شمارد.
 *   • راهِ صریح: `backup.restore` (`scripts/restore.js`) پس از بازگرداندن `rotate('restore')`.
 * «پایهٔ» هر حساب = بیشینهٔ server_seq ِ همان حساب که ≤ seq است، یعنی سرِ دفترِ حساب در
 * لحظهٔ بازیابی. پوش‌های یک حساب پشتِ قفلِ همان حساب‌اند (sync-v1 / sync-v3 / order-ledger)
 * ⇒ هر opِ این حساب با شمارهٔ ≤ پایه در دیتابیس هست.
 *
 * دستگاه (SyncV1Engine / SyncV3Engine / وب / NativeDevice ِ آزمونِ سرتاسری) id را با آخرین
 * idی که دیده مقایسه می‌کند؛ عوض شده ⇒ opهای پذیرفته‌شدهٔ خودش و (v1) opهای دیگران که گرفته
 * بود با شمارهٔ > پایه را به ترتیبِ سرور و با همان op_id دوباره می‌فرستد و دفترش را «نگه‌دارِ
 * محلی» از نو می‌سازد: Snapshot + ردیف‌های تنهای خودش، جز آنچه این‌جا حذف‌شده است
 * (`deletedRows`، `POST /api/shop/sync-epoch/deleted` و `/api/sync/v3/deleted`) —
 * `core/sync/RestoreReplay.kt` و `sync/v1/V1Restore.kt`.
 *
 * ⛔ `lib/sync-v1.js` و مسیرهای پمپ دست نمی‌خورند؛ دورانِ v1 از درِ جدای
 *    `GET /api/shop/sync-epoch` و دورانِ v3 در پاسخِ pull ِ خودِ v3 می‌رود.
 * ⛔ خاموش‌کن: `SYNC_EPOCH_ENABLED=false` ⇒ در ۴۰۴ و v3 بی دوران ⇒ برنامه همان رفتارِ پیشین.
 *    نوشتنِ دوران در بالا آمدن همیشه هست (بی‌ضرر) تا روشن کردنِ دوباره درست کار کند.
 * ⛔ هر خطای دیتابیس (جز «جدول نیست») پرتاب می‌شود و دورانِ پیشین سرِ جایش می‌ماند —
 *    پایهٔ صفر یعنی «همه‌چیز دوباره» برای همهٔ دستگاه‌ها.
 */
const crypto = require('crypto');
const { one, query, now } = require('../db');

const KEY = 'sync.epoch';
const MARKER = 'sync.backup_marker.';

function newId(prefix) {
  return `${prefix}_${Date.now().toString(36)}${crypto.randomBytes(6).toString('hex')}`;
}

/** بیشینهٔ server_seq ِ یک جدول؛ «جدول نیست» (دیتابیسِ آزمونیِ ناقص) ⇒ ۰، هر خطای دیگر پرتاب */
async function maxSeq(table, q = one) {
  try {
    const r = await q(`SELECT COALESCE(MAX(server_seq),0)::bigint AS m FROM ${table}`);
    return Number(r && r.m) || 0;
  } catch (e) {
    if (e && e.code === '42P01') return 0;
    throw e;
  }
}

function parse(raw) {
  try {
    const e = JSON.parse(raw);
    if (e && typeof e.id === 'string' && e.id && Number.isFinite(Number(e.seq)) && Number(e.seq) >= 0) return e;
  } catch { /* خراب */ }
  return null;
}

async function save(epoch) {
  await query(
    `INSERT INTO app_config (key, value, updated_at) VALUES ($1,$2,$3)
     ON CONFLICT (key) DO UPDATE SET value = excluded.value, updated_at = excluded.updated_at`,
    [KEY, JSON.stringify(epoch), epoch.at]
  );
}

/** نامِ نشستِ pg_dump ِ پشتیبان (`PGAPPNAME` در `backup.dump`) — تا بالا آمدن آن را بشناسد */
const BACKUP_APP = 'vil3n-sync-backup';

/**
 * پشتیبانی همین حالا در جریان است؟ (`scripts/backup.js` در فرایندِ جدا، هم‌زمان با بالا آمدنِ
 * سرور) — نشانِ آن «در پشتیبان» است، نه «از پشتیبان برگشته». PGlite یا بی دسترسی ⇒ نه.
 */
async function backupRunning() {
  try {
    const r = await one(
      'SELECT 1 AS x FROM pg_stat_activity WHERE application_name = $1 AND pid <> pg_backend_pid() LIMIT 1', [BACKUP_APP]
    );
    return !!r;
  } catch { return false; }
}

/**
 * در بالا آمدنِ سرور (`'boot'`) یا پس از بازگرداندنِ پشتیبان (`'restore'`).
 *   • نبودِ دوران ⇒ نخستین دوران (برنامه‌ای که هیچ idی ندیده فقط آن را نگه می‌دارد).
 *   • `'restore'`، نشانِ پشتیبان در دیتابیس، یا ردیفِ خراب ⇒ id ِ تازه و seq = سرِ دفتر.
 *   • وگرنه (بالا آمدنِ عادی) ⇒ همان id و seq.
 *
 * چرخهٔ نشان:
 *   • پشتیبانی که هم‌زمان در فرایندِ جدا در جریان است (`busy`) ⇒ نشانش نه شمرده و نه پاک
 *     می‌شود (وگرنه فایلش شاید بی نشان می‌ماند).
 *   • نشانِ پشتیبانِ کشته‌شده (فرایند وسطِ کار افتاد و پاکش نکرد) ⇒ «بازیابی» شمرده می‌شود و
 *     بی‌ضرر است: پایه = سرِ دفترِ همین لحظه، و برنامه‌ای که بالای پایه هیچ ندیده و نفرستاده
 *     (`RestoreReplay.plan`) فقط id را نگه می‌دارد — نه دوباره‌فرستادن، نه ساختنِ دوباره.
 * @returns {Promise<object>} دورانِ نشسته
 */
async function rotate(reason = 'boot', { seqOf = maxSeq, busy = backupRunning } = {}) {
  const t = now();
  const row = await one('SELECT value FROM app_config WHERE key = $1', [KEY]);
  const prev = row ? parse(row.value) : null;
  const markers = reason === 'restore' || !(await busy())
    ? await query("SELECT key FROM app_config WHERE key LIKE 'sync.backup_marker.%'")
    : { rows: [] };
  const restored = reason === 'restore' || markers.rows.length > 0 || (row && !prev);
  let epoch;
  if (prev && !restored) {
    epoch = { ...prev, boot: newId('bt'), at: t, reason: String(reason).slice(0, 32) };
  } else {
    epoch = {
      id: newId('ep'),
      boot: newId('bt'),
      seq: await seqOf('oplog'),
      seq_v3: await seqOf('sync_v3_ops'),
      at: t,
      restored_at: prev || row ? t : 0,
      reason: prev || row ? (reason === 'restore' ? 'restore' : (markers.rows.length ? 'backup_marker' : 'corrupt')) : 'genesis',
    };
  }
  await save(epoch);
  for (const m of markers.rows) await query('DELETE FROM app_config WHERE key = $1', [m.key]);
  return epoch;
}

/** دورانِ همین حالا. نبود (بالا آمدنی که نتوانست بنویسد) ⇒ همین‌جا ساخته می‌شود؛ خراب ⇒ خطا. */
async function current() {
  const row = await one('SELECT value FROM app_config WHERE key = $1', [KEY]);
  if (!row) return rotate('missing');
  const e = parse(row.value);
  if (!e) throw new Error('دورانِ همگام‌سازی در app_config خراب است');
  return e;
}

/**
 * دورانِ یک حساب: پایه (سرِ دفترِ حساب در لحظهٔ بازیابی) و سرِ دفترِ همین حالا.
 * @param ctx  `req.sync` ِ `requireSyncAccount` — {app, accountKind, accountId}
 * @param {'v1'|'v3'} line
 * @returns {Promise<{id:string, at:number, base:number, head:number}>}
 */
async function forAccount(ctx, line = 'v1') {
  const e = await current();
  const table = line === 'v3' ? 'sync_v3_ops' : 'oplog';
  const raw = line === 'v3' ? (e.seq_v3 ?? 0) : e.seq;
  const bound = Number(raw);
  if (!Number.isFinite(bound) || bound < 0) throw new Error(`پایهٔ دورانِ همگام‌سازی (${line}) نامعتبر است`);
  const args = [ctx.app, ctx.accountKind, ctx.accountId];
  const acc = 'app=$1 AND account_kind=$2 AND account_id=$3';
  const base = await one(`SELECT COALESCE(MAX(server_seq),0)::bigint AS m FROM ${table} WHERE ${acc} AND server_seq <= $4`, [...args, bound]);
  const head = await one(`SELECT COALESCE(MAX(server_seq),0)::bigint AS m FROM ${table} WHERE ${acc}`, args);
  return { id: e.id, at: Number(e.restored_at || e.at) || 0, base: Number(base.m) || 0, head: Number(head.m) || 0 };
}

/** خبرِ سامانه‌ای در «سابقه»ی پنل (همان راهِ `backup.unencrypted`)؛ دیتابیسِ خاموش ⇒ فقط لاگ */
function alert(action, detail) {
  console.warn(`[sync-epoch] ${action}: ${detail.error || ''}`);
  return require('./audit').log({ actorType: 'system', action, detail })
    .catch((e) => console.error(`[sync-epoch] ${action} (سابقه هم ننشست):`, e.message));
}

/**
 * نشانِ «این دیتابیس در پشتیبان است» — پیش از گرفتنِ پشتیبان می‌نشیند (پس در فایل هست) و
 * پس از آن از دیتابیسِ زنده پاک می‌شود. دیتابیسی که از آن فایل برگردد، در نخستین بالا آمدن
 * دورانِ بازیابیِ تازه می‌گیرد. شکست ⇒ پشتیبان بی نشان (همان رفتارِ پیشین)، نه بی پشتیبان —
 * ولی دیده می‌شود: خبرِ `backup.sync_marker_failed` در «سابقه»ی پنل (بازیابی از چنین فایلی را
 * `scripts/restore.js` با `rotate('restore')` جبران می‌کند؛ بالا آمدنِ سادهٔ سرور نه).
 * @returns {Promise<() => Promise<void>>} پاک‌کننده
 */
async function markBackup({ q = query } = {}) {
  const key = MARKER + newId('bk');
  try {
    await q('INSERT INTO app_config (key, value, updated_at) VALUES ($1,$2,$3)', [key, JSON.stringify({ at: now() }), now()]);
  } catch (e) {
    await alert('backup.sync_marker_failed', { error: String((e && e.message) || e).slice(0, 300) });
    return async () => {};
  }
  return async () => {
    try {
      await q('DELETE FROM app_config WHERE key = $1', [key]);
    } catch (e) {
      //  نشانِ مانده در دیتابیسِ زنده ⇒ بالا آمدنِ بعدی «بازیابی» می‌شمارد — بی‌ضرر (شرحِ `rotate`)
      await alert('backup.sync_marker_left', { key, error: String((e && e.message) || e).slice(0, 300) });
    }
  };
}

/** بیشترین ردیف در یک پرسشِ `deletedRows` */
const MAX_ASK = 500;

/**
 * از ردیف‌هایی که فقط روی یک دستگاه مانده‌اند (نه در Snapshot)، کدام روی سرور «حذف‌شده» است؟
 * — پیش از ساختنِ دوبارهٔ دفتر پس از بازیابی (`V1Restore.rebuild`، `SyncV3Engine.rebuild`، وب):
 * ردیفِ تنهای دستگاه می‌ماند و «درج» می‌رود، **مگر** این‌جا حذف‌شده باشد (درجِ دوباره زنده‌اش
 * می‌کرد). Snapshot ردیفِ حذف‌شده ندارد و `listDeleted` ِ v1 سقفِ ۵۰۰ دارد.
 * فقط خواندن؛ فقط ردیف‌های همین حساب. جدولِ ناشناخته و شناسهٔ بد نادیده.
 * @param ctx  {app, accountKind, accountId}
 * @param {'v1'|'v3'} line
 * @param {Array<{table:string,row_id:string}>} rows حداکثر `MAX_ASK`
 * @returns {Promise<Array<{table:string,row_id:string}>>} با همان نامِ جدولی که دستگاه فرستاد
 */
async function deletedRows(ctx, line, rows) {
  const v3 = line === 'v3';
  const canon = v3
    ? (t) => (require('./sync-v3-schema').specOf(t) ? t : '')
    : (t) => require('./sync-v1').tableOf(ctx.app, t);
  const byTable = new Map();
  for (const r of Array.isArray(rows) ? rows.slice(0, MAX_ASK) : []) {
    const name = typeof r?.table === 'string' ? r.table : '';
    const id = typeof r?.row_id === 'string' ? r.row_id : '';
    const t = name && canon(name);
    if (!t || !id || id.length > 128) continue;
    if (!byTable.has(t)) byTable.set(t, new Map());
    const g = byTable.get(t);
    if (!g.has(id)) g.set(id, new Set());
    g.get(id).add(name);
  }
  const table = v3 ? 'sync_v3_rows' : 'sync_rows';
  const out = [];
  for (const [t, g] of byTable) {
    const res = await query(
      `SELECT row_id FROM ${table} WHERE app=$1 AND account_kind=$2 AND account_id=$3 AND table_name=$4
         AND row_id = ANY($5::text[]) AND deleted_at IS NOT NULL`,
      [ctx.app, ctx.accountKind, ctx.accountId, t, [...g.keys()]]
    );
    for (const x of res.rows) for (const name of g.get(x.row_id) || []) out.push({ table: name, row_id: x.row_id });
  }
  return out;
}

module.exports = {
  rotate, current, forAccount, markBackup, deletedRows, backupRunning, maxSeq, KEY, MARKER, BACKUP_APP, MAX_ASK,
};
