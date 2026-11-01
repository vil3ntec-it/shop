'use strict';
/**
 * Pull با نوشته‌های تازه‌ترِ خودِ دستگاه — پرچمِ دکانِ `sync.pull_own_newer` (پیش‌فرض خاموش؛ #501).
 *
 * ── چه چیزی نبود ─────────────────────────────────────────────────────
 * داوری «آخری که رسید» است، ولی pull همهٔ opهای دیگران پس از cursor را **به ترتیبِ سرور** می‌داد،
 * بی‌توجه به اینکه همین دستگاه پس از آن‌ها چه نوشته. دستگاه opهای خودش را پیش‌تر روی دفترش
 * نشانده (و pull آن‌ها را نمی‌آورد)، پس opِ کهنه‌ترِ دیگری **روی** نوشتهٔ تازه‌ترِ خودش می‌نشست:
 *   (۱) هر دو یک فیلد را عوض کنند و A دیرتر برسد ⇒ سرور و B مقدارِ A را دارند، A مقدارِ B را.
 *   (۲) B حذف کند و A پس از آن ویرایش (ردیف روی سرور زنده می‌شود) ⇒ ردیف از دفترِ A می‌رود.
 *   (۳) B ویرایش کند و A پس از آن حذف ⇒ ردیفِ نیمه‌ای («شبح») در دفترِ A می‌ماند.
 * هیچ‌کدام خودبه‌خود درست نمی‌شود: opِ خودِ دستگاه هرگز به خودش برنمی‌گردد.
 * ──────────────────────────────────────────────────────────────────────
 *
 * با پرچم، پاسخِ pull همان opها را **برای همین دستگاه** چنین می‌دهد:
 *   • فیلدِ غیرِ `$inc` که آخرین نویسنده‌اش روی سرور همین دستگاه است با شماره‌ای بزرگ‌تر ⇒ برداشته
 *     می‌شود. `$inc` هرگز (دلتا جمع می‌شود). اگر آخرین نویسنده دستگاهِ سومی پس از آن باشد، opِ او
 *     پس از این می‌رسد و همان برنده است — دست نمی‌خورد.
 *   • op ای که همهٔ فیلدهایش برداشته شد ⇒ فرستاده نمی‌شود (op ِ تهی روی دستگاهی بی آن ردیف «شبح» می‌ساخت).
 *   • حذفِ دیگری روی ردیفی که حالا زنده است و همین دستگاه پس از آن رویش نوشته ⇒ فرستاده نمی‌شود.
 *   • درج/ویرایشِ دیگری روی ردیفی که حالا حذف است و همین دستگاه پس از آن حذفش کرده ⇒ فرستاده نمی‌شود.
 * cursor، `has_more`، `head`، شمارهٔ opها و شکلِ پاسخ همان است؛ فقط فیلد یا opی کم می‌شود.
 * مانده (جدا): ردیفی که دستگاهِ **دیگری** پس از حذف زنده کند، روی دستگاهی که حذف را دیده نیمه‌ای
 * می‌نشیند — opِ ویرایش فقط فیلدهای خودش را دارد. این پرچم آن را نه بدتر می‌کند نه درست.
 *
 * ⛔ `lib/sync-v1.js` مشترک با پمپ است و دست نمی‌خورد: این لایه فقط پاسخِ آمادهٔ `sync.pull` را در
 * `routes/sync-v1.js` می‌پالاید، فقط برای حسابِ دکان. پمپ (و دکانِ پرچم‌خاموش) همان آرایهٔ main را
 * می‌گیرد — همان شیء، بی هیچ پرس‌وجوی دیگری؛ پمپ حتی پرچم را نمی‌خواند.
 */
const { many } = require('../db');
const { SHOP } = require('./tenancy');

const FLAG = 'sync.pull_own_newer';

const ACC = 'app=$1 AND account_kind=$2 AND account_id=$3';
const accArgs = (ctx) => [ctx.app, ctx.accountKind, ctx.accountId];

//  همان دو کمکیِ `lib/sync-v1.js` (آن‌جا صادر نمی‌شوند)
const isPlainObject = (v) => v !== null && typeof v === 'object' && !Array.isArray(v);
const isInc = (v) => isPlainObject(v) && Object.keys(v).length === 1 && Number.isFinite(v.$inc);

/** حسابِ دکان (حسابِ پمپ `station` است) */
const isShop = (ctx) => Boolean(ctx && ctx.app === SHOP.app && ctx.accountKind === SHOP.app && ctx.accountId);

async function on(ctx) {
  if (!isShop(ctx)) return false;
  return require('./orders').flagOf(ctx.accountId, FLAG);
}

/**
 * opهای پاسخِ pull برای همین دستگاه — شرح سرِ همین پرونده. بی پرچم (یا پمپ) همان آرایه (همان شیء).
 * @param ops `ops` ِ پاسخِ `sync.pull` (دیگران، به ترتیبِ `server_seq`): `{server_seq, table, row_id, type, fields, …}`
 * @param since همان `since` ِ درخواست (همان `from` ِ `sync.pull`)
 */
async function view(ctx, deviceId, since, ops) {
  if (!Array.isArray(ops) || !ops.length || !(await on(ctx))) return ops;
  const from = Math.max(0, Number(since) || 0);
  const keyOf = (t, id) => `${t}\u0000${id}`;
  const tables = [];
  const ids = [];
  const seenKey = new Set();
  for (const r of ops) {
    const k = keyOf(r.table, r.row_id);
    if (seenKey.has(k)) continue;
    seenKey.add(k);
    tables.push(r.table);
    ids.push(r.row_id);
  }
  const state = new Map();
  for (const s of await many(
    `SELECT table_name, row_id, field_seq, field_src, deleted_at FROM sync_rows
      WHERE ${ACC} AND (table_name, row_id) IN (SELECT * FROM unnest($4::text[], $5::text[]))`,
    [...accArgs(ctx), tables, ids]
  )) state.set(keyOf(s.table_name, s.row_id), s);

  //  حذف‌های خودِ همین دستگاه پس از `from`، فقط برای ردیف‌هایی که حالا حذف‌اند و opِ غیرِ حذف دارند
  const gone = { t: [], id: [] };
  for (const r of ops) {
    const s = state.get(keyOf(r.table, r.row_id));
    if (r.type !== 'delete' && s && s.deleted_at !== null && s.deleted_at !== undefined) {
      gone.t.push(r.table);
      gone.id.push(r.row_id);
    }
  }
  const ownDelete = new Map();
  if (gone.t.length) {
    for (const d of await many(
      `SELECT table_name, row_id, MAX(server_seq) AS seq FROM oplog
        WHERE ${ACC} AND device_id=$4 AND op_type='delete' AND server_seq > $5
          AND (table_name, row_id) IN (SELECT * FROM unnest($6::text[], $7::text[]))
        GROUP BY table_name, row_id`,
      [...accArgs(ctx), deviceId, from, gone.t, gone.id]
    )) ownDelete.set(keyOf(d.table_name, d.row_id), Number(d.seq));
  }

  const out = [];
  for (const r of ops) {
    const k = keyOf(r.table, r.row_id);
    const s = state.get(k);
    const seq = Number(r.server_seq);
    if (!s) { out.push(r); continue; }
    const fseq = isPlainObject(s.field_seq) ? s.field_seq : {};
    const fsrc = isPlainObject(s.field_src) ? s.field_src : {};
    const mineAfter = (f) => isPlainObject(fsrc[f]) && fsrc[f].d === deviceId && Number(fseq[f] || 0) > seq;
    const alive = s.deleted_at === null || s.deleted_at === undefined;
    if (r.type === 'delete') {
      if (alive && Object.keys(fsrc).some(mineAfter)) continue;
      out.push(r);
      continue;
    }
    if (!alive && (ownDelete.get(k) || 0) > seq) continue;
    const fields = isPlainObject(r.fields) ? r.fields : {};
    let cut = false;
    const kept = {};
    for (const [f, v] of Object.entries(fields)) {
      if (!isInc(v) && mineAfter(f)) { cut = true; continue; }
      kept[f] = v;
    }
    if (!cut) { out.push(r); continue; }
    if (!Object.keys(kept).length) continue;
    out.push({ ...r, fields: kept });
  }
  return out;
}

module.exports = { FLAG, isShop, on, view };
