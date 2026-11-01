'use strict';
/**
 * Sync v1 ِ دکان: «تغییرِ کاربر روی دستگاه گم نشود» — دو پرچمِ `live_config` (پیش‌فرض خاموش) که فقط
 * به پاسخِ pull چیزی **می‌افزایند**؛ `lib/sync-v1.js` (مسیرِ مشترک با پمپ) دست نمی‌خورد و پمپ حتی
 * پرچم را نمی‌خواند. قاعدهٔ پرچم همان `orders.flagOf`: مقدارِ خودِ دکان بر `all` جلو می‌افتد و فقط
 * `true` روشن است. pull ِ بی op هیچ پرس‌وجوی اضافه ندارد.
 *
 *  • `sync.keep_pending` ⇒ `keep_pending: true` در پاسخ. برنامه (اندروید/کامپیوتر `LedgerDiff.keepPending`،
 *    وب `core.keepPending`) پیش از نشاندنِ opهای رسیده، تغییرِ محلی‌ای را که هنوز در صف است (مثلاً
 *    ثبت‌شده وسطِ همین pull) بازنویسی نمی‌کند — قاعده: `core/…/core/sync/KeepPending.kt`.
 *    بی پرچم: دستگاه opِ رسیده را روی ویرایشِ در صفش می‌نشاند و پس از رسیدنِ صف با سرور واگرا می‌ماند.
 *
 *  • `sync.revive_full_row` ⇒ op ِ غیرِ حذفی که ردیفِ **حذف‌شده** را زنده کرد (قاعدهٔ v1: «ویرایش یا درجِ
 *    پس از حذف ردیف را زنده می‌کند»، سرِ `lib/sync-v1.js`) کلیدِ `row` می‌گیرد: دادهٔ کاملِ ردیف **درست پس از
 *    همان op** (بازپخشِ `applyOp` روی opهای همان ردیف تا همان شماره). دستگاهی که حذف را دیده و ردیف را
 *    ندارد آن را کامل می‌سازد؛ بی پرچم فقط با فیلدهای همان op («نیمه‌ای»، بقیه پیش‌فرضِ مدل). داده
 *    «پس از همان op» است نه «امروز»، تا opهای بعدی (و `$inc`) دو بار ننشینند. دستگاهی که ردیف را دارد
 *    `row` را نادیده می‌گیرد؛ برنامهٔ کهنه کلید را نمی‌شناسد.
 *
 *  • `sync.gzip_pull` ⇒ پاسخِ pull ِ بزرگ‌تر از ۱ کیلوبایت، اگر درخواست `Accept-Encoding: gzip` داشت، فشرده
 *    می‌رود (همان سازوکارِ Snapshot: اندروید و مرورگر خودشان باز می‌کنند؛ JVM ِ کامپیوتر این سرآیند را
 *    نمی‌فرستد ⇒ همان پاسخِ ساده). push فشرده نشد: برنامه باید بدنه را خودش فشرده کند و از سرور بداند که
 *    می‌پذیرد — جدا، پس از این.
 */
const { many } = require('../db');
const { applyOp } = require('./sync-v1');
const { SHOP } = require('./tenancy');

const KEEP_PENDING = 'sync.keep_pending';
const REVIVE_ROW = 'sync.revive_full_row';
const GZIP_PULL = 'sync.gzip_pull';

const ACC = 'app=$1 AND account_kind=$2 AND account_id=$3';
const accArgs = (ctx) => [ctx.app, ctx.accountKind, ctx.accountId];
const keyOf = (table, rowId) => `${table}\u0000${rowId}`;

function decode(text) {
  try { return JSON.parse(text); } catch { return null; }
}

const isShop = (ctx) => !!ctx && ctx.app === SHOP.app && ctx.accountKind === SHOP.app && !!ctx.accountId;

/** هر سه پرچم با یک پرس‌وجو — همان قاعدهٔ `orders.flagOf` */
async function flags(ctx) {
  const rows = await many(
    'SELECT scope, key, value FROM live_config WHERE key = ANY($1::text[]) AND scope IN ($2,$3)',
    [[KEEP_PENDING, REVIVE_ROW, GZIP_PULL], 'all', String(ctx.accountId)]
  );
  const v = {};
  for (const r of rows) if (r.scope === 'all') v[r.key] = decode(r.value);
  for (const r of rows) if (r.scope !== 'all') v[r.key] = decode(r.value);
  return { keepPending: v[KEEP_PENDING] === true, reviveRow: v[REVIVE_ROW] === true, gzip: v[GZIP_PULL] === true };
}

/**
 * opهای صفحه، و برای هر op ِ غیرِ حذفی که ردیفِ حذف‌شده را زنده کرد `row` = دادهٔ ردیف پس از همان op.
 * ردیف‌هایی که هرگز حذف نشده‌اند (تقریباً همه) فقط یک پرس‌وجوی نمایه‌دار (`idx_oplog_row`) دارند.
 */
async function reviveRows(ctx, ops) {
  const live = ops.filter((o) => o.type !== 'delete');
  if (!live.length) return ops;
  const top = Math.max(...live.map((o) => Number(o.server_seq) || 0));
  const seen = new Set();
  const t = [];
  const r = [];
  for (const o of live) {
    const k = keyOf(o.table, o.row_id);
    if (seen.has(k)) continue;
    seen.add(k);
    t.push(o.table);
    r.push(o.row_id);
  }
  const tombs = await many(
    `SELECT table_name, row_id FROM oplog
      WHERE ${ACC} AND op_type='delete' AND server_seq < $4
        AND (table_name, row_id) IN (SELECT * FROM unnest($5::text[], $6::text[]))
      GROUP BY table_name, row_id`,
    [...accArgs(ctx), top, t, r]
  );
  if (!tombs.length) return ops;
  const history = await many(
    `SELECT op_id, device_id, server_seq, table_name, row_id, op_type, fields, received_at FROM oplog
      WHERE ${ACC} AND server_seq <= $4
        AND (table_name, row_id) IN (SELECT * FROM unnest($5::text[], $6::text[]))
      ORDER BY server_seq ASC`,
    [...accArgs(ctx), top, tombs.map((x) => x.table_name), tombs.map((x) => x.row_id)]
  );
  const state = new Map();
  const after = new Map();
  for (const h of history) {
    const k = keyOf(h.table_name, h.row_id);
    const prev = state.get(k) || null;
    const fields = typeof h.fields === 'string' ? decode(h.fields) || {} : h.fields || {};
    const out = applyOp(prev, { op_id: h.op_id, type: h.op_type, fields }, Number(h.server_seq), h.device_id,
      Number(h.received_at) || 0, Number.MAX_SAFE_INTEGER);
    if (h.op_type !== 'delete' && prev && prev.deleted_at) after.set(h.op_id, out.data);
    state.set(k, out);
  }
  if (!after.size) return ops;
  return ops.map((o) => (after.has(o.op_id) ? { ...o, row: after.get(o.op_id) } : o));
}

/**
 * افزوده‌های پاسخِ pull (`routes/sync-v1.js`) — شرح سرِ همین فایل.
 * @returns {Promise<null | {body:{ops?:Array, keep_pending?:true}, gzip:boolean}>} null ⇒ پاسخ همان پیشین
 */
async function pullExtras(ctx, ops) {
  if (!Array.isArray(ops) || !ops.length || !isShop(ctx)) return null;
  try {
    const f = await flags(ctx);
    if (!f.keepPending && !f.reviveRow && !f.gzip) return null;
    const body = {};
    if (f.reviveRow) body.ops = await reviveRows(ctx, ops);
    if (f.keepPending) body.keep_pending = true;
    return { body, gzip: f.gzip };
  } catch (err) {
    //  افزوده است، نه شرطِ pull: شکست ⇒ همان پاسخِ پیشین (رفتارِ بی پرچم)
    console.error('[sync-v1-keep] افزوده‌های pull خوانده نشد:', err.message);
    return null;
  }
}

module.exports = { KEEP_PENDING, REVIVE_ROW, GZIP_PULL, GZIP_MIN_BYTES: 1024, flags, reviveRows, pullExtras };
