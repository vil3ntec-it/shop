'use strict';
/**
 * عکسِ کالاها روی حساب — شرح در ‎migrations/045_product_photos.sql‎.
 *
 *   list(ctx, since)        ⇒ تغییرهای پس از rev (فقط نشانه، نه خودِ عکس)
 *   get(ctx, productId)     ⇒ { data, hash } یا null
 *   put(ctx, productId, buf, deviceId)
 *   remove(ctx, productId, deviceId)
 *
 * ⛔ فقط JPEG/PNG/WebP و حداکثر ۶۰۰ کیلوبایت — برنامه پیش از فرستادن عکس را
 *    ۸۰۰ پیکسلی و فشرده می‌کند (حدودِ ۴۰ تا ۸۰ کیلوبایت).
 * ⛔ سقفِ ۲۰٬۰۰۰ عکس برای هر دکان: جلوی پر کردنِ دیسکِ سرور.
 */
const crypto = require('crypto');
const { one, many, tx } = require('../db');
const store = require('./image-store');

const MAX_BYTES = 600 * 1024;
const MAX_PHOTOS = 20000;

function badRequest(message) { const e = new Error(message); e.status = 400; return e; }

//  نوع فقط از محتوا — یک نسخه برای همه، در `lib/image-store.js` (خواندنِ انبار هم همان را می‌سنجد)
const { sniff } = store;
//  بایتِ عکس در `data` یا در انبارِ شیء (`store_key`، مهاجرتِ ۲۵۵) — همیشه از `image-store`
const tenantOf = (ctx) => store.accountTenant(ctx.app, ctx.accountId);
const HAS = store.has();

function checkId(productId) {
  const id = String(productId || '');
  if (!/^[A-Za-z0-9_.:-]{1,64}$/.test(id)) throw badRequest('شناسهٔ کالا درست نیست');
  return id;
}

async function list(ctx, since = 0, limit = 500) {
  const rows = await many(
    `SELECT product_id, hash, size, rev, updated_at, (data IS NULL AND store_key = '') AS deleted
       FROM product_photos WHERE app=$1 AND account_id=$2 AND rev > $3
      ORDER BY rev ASC LIMIT $4`,
    [ctx.app, ctx.accountId, Number(since) || 0, Math.min(1000, Math.max(1, Number(limit) || 500))],
  );
  const items = rows.map(r => ({
    product_id: r.product_id, hash: r.hash, size: Number(r.size), rev: Number(r.rev),
    updated_at: Number(r.updated_at), deleted: !!r.deleted,
  }));
  return { items, head: items.length ? items[items.length - 1].rev : Number(since) || 0, has_more: items.length >= limit };
}

async function get(ctx, productId) {
  const r = await one(
    `SELECT data, store_key, hash FROM product_photos WHERE app=$1 AND account_id=$2 AND product_id=$3 AND ${HAS}`,
    [ctx.app, ctx.accountId, checkId(productId)],
  );
  const data = await store.bytes(tenantOf(ctx), r);
  return data ? { data, hash: r.hash } : null;
}

async function put(ctx, productId, buf, deviceId = '') {
  const id = checkId(productId);
  if (!Buffer.isBuffer(buf) || buf.length < 100) throw badRequest('عکس خالی است');
  if (buf.length > MAX_BYTES) throw badRequest('عکس بزرگ‌تر از ۶۰۰ کیلوبایت است');
  if (!sniff(buf)) throw badRequest('فقط عکسِ JPG، PNG یا WebP');
  const count = await one(
    `SELECT COUNT(*)::int AS n FROM product_photos WHERE app=$1 AND account_id=$2 AND ${HAS}`,
    [ctx.app, ctx.accountId],
  );
  if (Number(count?.n || 0) >= MAX_PHOTOS) {
    const exists = await one(`SELECT 1 FROM product_photos WHERE app=$1 AND account_id=$2 AND product_id=$3 AND ${HAS}`,
      [ctx.app, ctx.accountId, id]);
    if (!exists) throw badRequest('سقفِ عکس‌های این دکان پر است');
  }
  const hash = crypto.createHash('sha256').update(buf).digest('hex').slice(0, 32);
  //  شیءِ عکسِ پیشین (اگر بود) پس از نوشتنِ ردیف پاک می‌شود؛ کلیدش در همان تراکنش و با قفلِ ردیف خوانده می‌شود
  const row = await store.withStaged(tenantOf(ctx), buf, (s) => tx(async (c) => {
    const old = (await c.query('SELECT store_key FROM product_photos WHERE app=$1 AND account_id=$2 AND product_id=$3 FOR UPDATE',
      [ctx.app, ctx.accountId, id])).rows[0];
    const r = (await c.query(
      `INSERT INTO product_photos (app, account_id, product_id, data, store_key, hash, size, rev, updated_at, device_id)
       VALUES ($1,$2,$3,$4,$9,$5,$6,nextval('product_photo_rev'),$7,$8)
       ON CONFLICT (app, account_id, product_id) DO UPDATE
         SET data=EXCLUDED.data, store_key=EXCLUDED.store_key, hash=EXCLUDED.hash, size=EXCLUDED.size, rev=EXCLUDED.rev,
             updated_at=EXCLUDED.updated_at, device_id=EXCLUDED.device_id
       RETURNING rev`,
      [ctx.app, ctx.accountId, id, s.data, hash, buf.length, Date.now(), String(deviceId || '').slice(0, 64), s.key],
    )).rows[0];
    s.used = true;
    return { rev: r.rev, old_key: old ? old.store_key : '' };
  }));
  if (row.old_key) await store.drop(tenantOf(ctx), [row.old_key]);
  return { product_id: id, hash, size: buf.length, rev: Number(row.rev) };
}

async function remove(ctx, productId, deviceId = '') {
  const id = checkId(productId);
  const row = await tx(async (c) => {
    const old = (await c.query(`SELECT store_key FROM product_photos WHERE app=$1 AND account_id=$2 AND product_id=$3 AND ${HAS} FOR UPDATE`,
      [ctx.app, ctx.accountId, id])).rows[0];
    if (!old) return null;
    const r = (await c.query(
      `UPDATE product_photos SET data=NULL, store_key='', hash='', size=0, rev=nextval('product_photo_rev'), updated_at=$4, device_id=$5
        WHERE app=$1 AND account_id=$2 AND product_id=$3 RETURNING rev`,
      [ctx.app, ctx.accountId, id, Date.now(), String(deviceId || '').slice(0, 64)],
    )).rows[0];
    return { rev: r.rev, old_key: old.store_key };
  });
  if (row && row.old_key) await store.drop(tenantOf(ctx), [row.old_key]);
  return { product_id: id, rev: row ? Number(row.rev) : 0, removed: !!row };
}

/* ------------------------------ صندوقِ عکس ------------------------------ */
//  شرح: ‎migrations/046_photo_inbox.sql‎. عکسِ دوربینِ گوشی تا ۲ مگابایت
//  (کامپیوتر خودش در استودیو کوچکش می‌کند)، حداکثر ۵۰ عکسِ منتظر برای هر دکان.
const INBOX_MAX_BYTES = 2 * 1024 * 1024;
const INBOX_MAX = 50;
const INBOX_TTL_MS = 24 * 3600 * 1000;

async function inboxPut(ctx, buf, { pair = '', productId = '', deviceId = '' } = {}) {
  if (!Buffer.isBuffer(buf) || buf.length < 100) throw badRequest('عکس خالی است');
  if (buf.length > INBOX_MAX_BYTES) throw badRequest('عکس بزرگ‌تر از ۲ مگابایت است');
  if (!sniff(buf)) throw badRequest('فقط عکسِ JPG، PNG یا WebP');
  const p = String(pair || '').replace(/\D/g, '').slice(0, 6);
  if (p.length !== 6) throw badRequest('کدِ جفت‌شدن درست نیست');
  const pid = productId ? checkId(productId) : '';
  const now = Date.now();
  for (const r of await many('DELETE FROM photo_inbox WHERE created_at < $1 RETURNING app, account_id, store_key', [now - INBOX_TTL_MS])) {
    if (r.store_key) await store.drop(store.accountTenant(r.app, r.account_id), [r.store_key]);
  }
  const count = await one('SELECT COUNT(*)::int AS n FROM photo_inbox WHERE app=$1 AND account_id=$2', [ctx.app, ctx.accountId]);
  if (Number(count?.n || 0) >= INBOX_MAX) throw badRequest('صندوقِ عکس پر است — عکس‌های رسیده را در کامپیوتر بردارید');
  const id = 'in_' + crypto.randomBytes(12).toString('hex');
  await store.withStaged(tenantOf(ctx), buf, async (s) => {
    await one(
      `INSERT INTO photo_inbox (id, app, account_id, pair, product_id, data, store_key, size, created_at, device_id)
       VALUES ($1,$2,$3,$4,$5,$6,$10,$7,$8,$9) RETURNING id`,
      [id, ctx.app, ctx.accountId, p, pid, s.data, buf.length, now, String(deviceId || '').slice(0, 64), s.key],
    );
    s.used = true;
  });
  return { id, pair: p, product_id: pid, size: buf.length, created_at: now };
}

async function inboxList(ctx, pair) {
  const p = String(pair || '').replace(/\D/g, '').slice(0, 6);
  const rows = await many(
    `SELECT id, pair, product_id, size, created_at FROM photo_inbox
      WHERE app=$1 AND account_id=$2 AND pair=$3 AND created_at >= $4 ORDER BY created_at ASC LIMIT 50`,
    [ctx.app, ctx.accountId, p, Date.now() - INBOX_TTL_MS],
  );
  return rows.map(r => ({ id: r.id, pair: r.pair, product_id: r.product_id, size: Number(r.size), created_at: Number(r.created_at) }));
}

async function inboxGet(ctx, id) {
  const r = await one('SELECT data, store_key, product_id FROM photo_inbox WHERE app=$1 AND account_id=$2 AND id=$3',
    [ctx.app, ctx.accountId, String(id || '').slice(0, 64)]);
  const data = await store.bytes(tenantOf(ctx), r);
  return data ? { data, product_id: r.product_id } : null;
}

async function inboxDelete(ctx, id) {
  const r = await one('DELETE FROM photo_inbox WHERE app=$1 AND account_id=$2 AND id=$3 RETURNING store_key',
    [ctx.app, ctx.accountId, String(id || '').slice(0, 64)]);
  if (r && r.store_key) await store.drop(tenantOf(ctx), [r.store_key]);
  return !!r;
}

module.exports = {
  list, get, put, remove, sniff, checkId, MAX_BYTES, MAX_PHOTOS, HAS,
  inboxPut, inboxList, inboxGet, inboxDelete, INBOX_MAX_BYTES,
};
