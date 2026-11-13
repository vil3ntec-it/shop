'use strict';
/**
 * جابه‌جاییِ بایت‌های عکس میانِ پایگاه و انبارِ شیء (`lib/image-store.js`) — دلِ `scripts/image-store-migrate.js`.
 *
 *   status    شمارِ هر جدول: فقط پایگاه / هر دو (رونوشت) / فقط انبار، و حجمِ بایتِ درونِ پایگاه.
 *   copy      رونوشتِ هر `data` ِ بی‌کلید به انبار ⇒ `store_key`. ⛔ `data` می‌ماند (تا `purge`). ادامه‌پذیر و
 *             بی‌اثر در تکرار: ردیفِ کلیددار رد می‌شود؛ کلید از HMAC ِ (جدول، کلیدِ ردیف، md5 ِ بایت) با رازِ سرور
 *             است، پس اجرای بریده و دوباره همان شیء را بازمی‌نویسد، نه شیءِ یتیم. هر شیء پس از نوشتن بازخوانی و
 *             بایت‌به‌بایت سنجیده می‌شود؛ ردیفی که در این میان عوض شد (md5 ِ دیگر) دست نمی‌خورد و شیءش پاک می‌شود.
 *   verify    هر ردیفِ کلیددار: شیء هست، مالِ همان tenant است، sha256 ِ درونِ کلید را دارد و (اگر رونوشت) با `data` یکی.
 *   purge     فقط با `confirm` و فقط در حالتِ شیء: `data` ِ ردیف‌های رونوشت ⇒ null، هر کدام پس از سنجشِ دوبارهٔ شیء.
 *   rollback  فقط در حالتِ `db` با `IMAGE_STORE_READ`: بایتِ هر ردیفِ فقط-انبار به `data`، و `store_key` = ''.
 *             شیءها می‌مانند (پاکشان با `gc --apply`).
 *   gc        شیءهای بی ردیف که از `olderThanMs` کهنه‌ترند (شیءِ تراکنشِ برگشته، پاکیِ آبشاریِ دکان)؛ پیش‌فرض فقط گزارش.
 *
 * رسانهٔ چتِ دکان، پشتیبانی و مدرکِ پروندهٔ اعتماد (مهاجرتِ ۲۷۵، `lib/media-store.js`) هم این‌جایند، هر کدام با سنجهٔ
 * نوعِ خودش (`check`)؛ ردیفی که بایتش آن نوع نیست جابه‌جا نمی‌شود (`skipped`، در پایگاه می‌ماند).
 * ⛔ کدِ پمپ (`station_chat_media`) این‌جا نیست (docs/SCALING-fa.md §۵).
 */
const crypto = require('crypto');
const db = require('../db');
const store = require('./image-store');
const media = require('./media-store');
const { serverSecret } = require('./server-secret');

const shop = (r) => store.shopTenant(r.shop_id);
const account = (r) => store.accountTenant(r.app, r.account_id);
/** هر جدولِ بایت (مهاجرتِ ۲۵۵ و ۲۷۵): کلیدِ ردیف، ستون‌های tenant، tenant ِ انبار و سنجهٔ نوع (پیش‌فرض عکس). */
const TABLES = [
  { table: 'product_photos', pk: ['app', 'account_id', 'product_id'], extra: [], tenant: account },
  //  نسخهٔ کارتِ همان عکس (مهاجرتِ ۳۵۰) — بی این `gc` شیءِ کارت را «بی ردیف» می‌دید و پاک می‌کرد
  { table: 'product_photo_thumbs', pk: ['app', 'account_id', 'product_id'], extra: [], tenant: account },
  { table: 'photo_inbox', pk: ['id'], extra: ['app', 'account_id'], tenant: account },
  { table: 'storefront_media', pk: ['shop_id', 'kind', 'version'], extra: [], tenant: shop },
  { table: 'image_blobs', pk: ['hash'], extra: [], tenant: () => store.BLOBS },
  { table: 'order_issue_photos', pk: ['id'], extra: ['shop_id'], tenant: shop },
  //  RET-11: عکسِ درخواستِ برگشتِ ویترین (مهاجرتِ ۸۶۶) — همان راهِ عکسِ گزارشِ مشکل
  { table: 'order_return_request_photos', pk: ['id'], extra: ['shop_id'], tenant: shop },
  { table: 'order_pick_photos', pk: ['id'], extra: ['shop_id'], tenant: shop },
  { table: 'delivery_photos', pk: ['id'], extra: ['shop_id'], tenant: shop },
  { table: 'shop_chat_media', pk: ['id'], extra: ['shop_id', 'mime'], tenant: shop, check: (r) => media.chatCheck(r.mime) },
  { table: 'support_media', pk: ['id'], extra: ['thread_id'], tenant: (r) => media.supportTenant(r.thread_id), check: () => media.supportCheck },
  { table: 'trust_evidence', pk: ['id'], extra: ['shop_id', 'mime'], tenant: shop, check: (r) => media.chatCheck(r.mime) },
  //  رسانهٔ نشانی (`lib/address-media.js`، `media-store.address`): حساب (۸۳۶) در `customer:<حساب>`، سفارش (۸۴۷) در `shop:<دکان>` —
  //  بی این دو `gc` شیءشان را «بی ردیف» می‌دید
  { table: 'store_address_media', pk: ['id'], extra: ['customer_id', 'mime'], tenant: media.addressTenant, check: (r) => media.chatCheck(r.mime) },
  { table: 'order_address_media', pk: ['id'], extra: ['shop_id', 'mime'], tenant: media.addressTenant, check: (r) => media.chatCheck(r.mime) },
];
const checkOf = (t, r) => (t.check ? t.check(r) : store.sniff);

function pick(names) {
  if (!names || !names.length) return TABLES;
  const out = TABLES.filter((t) => names.includes(t.table));
  const unknown = names.filter((n) => !TABLES.some((t) => t.table === n));
  if (unknown.length) throw new Error(`جدولِ ناشناخته: ${unknown.join(', ')}`);
  return out;
}

const md5 = (b) => crypto.createHash('md5').update(b).digest('hex');
const seedSecret = () => crypto.createHmac('sha256', serverSecret(['api', 'jwt'], 'image-store-move')).update('image-store-move-v1').digest();

/**
 * ردیف‌های `where` به ترتیبِ کلیدِ ردیف، دسته‌دسته با keyset (ردیفِ ردشده دوباره خوانده نمی‌شود). `fn(row)` برای هر ردیف.
 */
async function eachRow(t, where, cols, fn, { batch = 100, limit = Infinity } = {}) {
  const pk = t.pk.join(', ');
  const size = Math.max(1, Math.min(1000, Math.floor(Number(batch) || 100)));
  let last = null;
  let seen = 0;
  for (;;) {
    const args = [];
    let cond = where;
    if (last) {
      args.push(...t.pk.map((k) => last[k]));
      cond += ` AND (${pk}) > (${t.pk.map((_, i) => `$${i + 1}`).join(', ')})`;
    }
    args.push(size);
    const rows = await db.many(
      `SELECT ${[...t.pk, ...t.extra].join(', ')}, ${cols} FROM ${t.table} WHERE ${cond} ORDER BY ${pk} LIMIT $${args.length}`, args);
    for (const r of rows) {
      if (seen++ >= limit) return;
      await fn(r);
    }
    if (rows.length < size) return;
    last = rows[rows.length - 1];
  }
}

const pkWhere = (t, r, from = 1) => ({
  sql: t.pk.map((k, i) => `${k}=$${i + from}`).join(' AND '),
  args: t.pk.map((k) => r[k]),
});

function needBackend() {
  const a = store.backend();
  if (!a) throw new Error('انبارِ شیء پیکربندی نشده است: IMAGE_STORE=disk|s3 (یا IMAGE_STORE_READ با IMAGE_STORE=db)');
  return a;
}

async function status({ tables } = {}) {
  const out = {};
  for (const t of pick(tables)) {
    const r = await db.one(
      `SELECT COUNT(*) FILTER (WHERE data IS NOT NULL AND store_key = '')::int AS db_only,
              COUNT(*) FILTER (WHERE data IS NOT NULL AND store_key <> '')::int AS copied,
              COUNT(*) FILTER (WHERE data IS NULL AND store_key <> '')::int AS store_only,
              COALESCE(SUM(octet_length(data)), 0)::bigint AS db_bytes
         FROM ${t.table}`);
    out[t.table] = { dbOnly: r.db_only, copied: r.copied, storeOnly: r.store_only, dbBytes: Number(r.db_bytes) };
  }
  return { mode: store.mode(), backend: store.backend() ? store.backend().kind : null, tables: out };
}

async function copy({ tables, batch, limit, log = () => {} } = {}) {
  const a = needBackend();
  const secret = seedSecret();
  const out = {};
  for (const t of pick(tables)) {
    const c = { copied: 0, changed: 0, skipped: 0, failed: 0 };
    await eachRow(t, `data IS NOT NULL AND store_key = ''`, 'data', async (r) => {
      const buf = Buffer.from(r.data);
      const mime = checkOf(t, r)(buf);
      //  ⛔ همان سنجشِ سختِ نوع (عکس: JPEG/PNG/WebP؛ رسانه: `lib/media-store.js`): ردیفِ دیگر جابه‌جا نمی‌شود (در پایگاه می‌ماند و گزارش می‌شود)
      if (!mime) { c.skipped++; return; }
      const sum = md5(buf);
      const tenant = t.tenant(r);
      const key = store.keyFor(tenant, buf, { secret, id: `${t.table}:${JSON.stringify(t.pk.map((k) => r[k]))}:${sum}` });
      try {
        await a.put(key, buf, mime);
        const back = await a.get(key);
        if (!back || !back.equals(buf)) throw new Error('بازخوانی با نوشته یکی نیست');
      } catch (e) {
        c.failed++;
        log(`✖ ${t.table} ${JSON.stringify(t.pk.map((k) => r[k]))}: ${e.message}`);
        return;
      }
      const w = pkWhere(t, r, 3);
      const done = await db.one(
        `UPDATE ${t.table} SET store_key=$1 WHERE ${w.sql} AND store_key = '' AND data IS NOT NULL AND md5(data) = $2 RETURNING 1 AS ok`,
        [key, sum, ...w.args]);
      if (done) { c.copied++; return; }
      c.changed++;
      await store.drop(tenant, [key]);
    }, { batch, limit });
    out[t.table] = c;
    log(`${t.table}: ${JSON.stringify(c)}`);
  }
  return out;
}

async function verify({ tables, batch, log = () => {} } = {}) {
  needBackend();
  const out = {};
  for (const t of pick(tables)) {
    const c = { ok: 0, missing: 0, mismatch: 0 };
    await eachRow(t, `store_key <> ''`, 'store_key, data', async (r) => {
      const b = await store.fetchObject(t.tenant(r), r.store_key, checkOf(t, r));
      if (!b) { c.missing++; log(`✖ ${t.table} ${JSON.stringify(t.pk.map((k) => r[k]))}: شیء نیست یا سالم نیست`); return; }
      if (r.data !== null && !b.equals(Buffer.from(r.data))) { c.mismatch++; return; }
      c.ok++;
    }, { batch });
    out[t.table] = c;
  }
  return out;
}

async function purge({ tables, batch, confirm = false, log = () => {} } = {}) {
  if (!store.objectMode()) throw new Error('purge فقط وقتی IMAGE_STORE=disk|s3 است (خواندن از انبار)');
  needBackend();
  const out = {};
  for (const t of pick(tables)) {
    const c = { purged: 0, wouldPurge: 0, kept: 0 };
    await eachRow(t, `data IS NOT NULL AND store_key <> ''`, 'store_key, data', async (r) => {
      const buf = Buffer.from(r.data);
      const b = await store.fetchObject(t.tenant(r), r.store_key, checkOf(t, r));
      //  ⛔ رونوشتِ پایگاه فقط وقتی برداشته می‌شود که شیء همین دم بایت‌به‌بایت همان باشد
      if (!b || !b.equals(buf)) { c.kept++; log(`✖ ${t.table} ${JSON.stringify(t.pk.map((k) => r[k]))}: شیء با پایگاه یکی نیست`); return; }
      if (!confirm) { c.wouldPurge++; return; }
      const w = pkWhere(t, r, 3);
      const done = await db.one(
        `UPDATE ${t.table} SET data = NULL WHERE ${w.sql} AND store_key = $1 AND md5(data) = $2 RETURNING 1 AS ok`,
        [r.store_key, md5(buf), ...w.args]);
      if (done) c.purged++; else c.kept++;
    }, { batch });
    out[t.table] = c;
    log(`${t.table}: ${JSON.stringify(c)}`);
  }
  return out;
}

async function rollback({ tables, batch, log = () => {} } = {}) {
  if (store.objectMode()) {
    throw new Error('rollback فقط با IMAGE_STORE=db و IMAGE_STORE_READ=disk|s3 (وگرنه نوشته‌های تازه باز به انبار می‌روند)');
  }
  needBackend();
  const out = {};
  for (const t of pick(tables)) {
    const c = { restored: 0, unlinked: 0, missing: 0 };
    await eachRow(t, `store_key <> ''`, 'store_key, (data IS NOT NULL) AS in_db', async (r) => {
      const w = pkWhere(t, r, 2);
      if (r.in_db) {
        const done = await db.one(`UPDATE ${t.table} SET store_key = '' WHERE ${w.sql} AND store_key = $1 RETURNING 1 AS ok`,
          [r.store_key, ...w.args]);
        if (done) c.unlinked++;
        return;
      }
      const b = await store.fetchObject(t.tenant(r), r.store_key, checkOf(t, r));
      if (!b) { c.missing++; log(`✖ ${t.table} ${JSON.stringify(t.pk.map((k) => r[k]))}: شیء نیست — ردیف دست نخورد`); return; }
      const done = await db.one(
        `UPDATE ${t.table} SET data = $${t.pk.length + 2}, store_key = '' WHERE ${w.sql} AND store_key = $1 AND data IS NULL RETURNING 1 AS ok`,
        [r.store_key, ...w.args, b]);
      if (done) c.restored++;
    }, { batch });
    out[t.table] = c;
    log(`${t.table}: ${JSON.stringify(c)}`);
  }
  return out;
}

async function gc({ olderThanMs = 24 * 3600 * 1000, apply = false, now = Date.now(), log = () => {} } = {}) {
  const a = needBackend();
  const old = (await a.list()).filter((o) => o.mtime < now - Math.max(0, olderThanMs));
  const c = { listed: 0, referenced: 0, orphans: 0, deleted: 0 };
  c.listed = old.length;
  for (let i = 0; i < old.length; i += 500) {
    const keys = old.slice(i, i + 500).map((o) => o.key);
    const used = new Set();
    for (const t of TABLES) {
      for (const r of await db.many(`SELECT store_key FROM ${t.table} WHERE store_key = ANY($1::text[])`, [keys])) used.add(r.store_key);
    }
    for (const k of keys) {
      if (used.has(k)) { c.referenced++; continue; }
      c.orphans++;
      if (!apply) continue;
      try { await a.del(k); c.deleted++; } catch (e) { log(`✖ ${k}: ${e.message}`); }
    }
  }
  return c;
}

module.exports = { TABLES, status, copy, verify, purge, rollback, gc };
