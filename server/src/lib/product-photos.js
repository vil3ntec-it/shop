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
const { one, many } = require('../db');

const MAX_BYTES = 600 * 1024;
const MAX_PHOTOS = 20000;

function badRequest(message) { const e = new Error(message); e.status = 400; return e; }

function sniff(buf) {
  if (buf.length >= 3 && buf[0] === 0xff && buf[1] === 0xd8 && buf[2] === 0xff) return 'image/jpeg';
  if (buf.length >= 8 && buf.slice(0, 8).equals(Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]))) return 'image/png';
  if (buf.length >= 12 && buf.slice(0, 4).toString('latin1') === 'RIFF' && buf.slice(8, 12).toString('latin1') === 'WEBP') return 'image/webp';
  return '';
}

function checkId(productId) {
  const id = String(productId || '');
  if (!/^[A-Za-z0-9_.:-]{1,64}$/.test(id)) throw badRequest('شناسهٔ کالا درست نیست');
  return id;
}

async function list(ctx, since = 0, limit = 500) {
  const rows = await many(
    `SELECT product_id, hash, size, rev, updated_at, (data IS NULL) AS deleted
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
    'SELECT data, hash FROM product_photos WHERE app=$1 AND account_id=$2 AND product_id=$3 AND data IS NOT NULL',
    [ctx.app, ctx.accountId, checkId(productId)],
  );
  return r ? { data: Buffer.from(r.data), hash: r.hash } : null;
}

async function put(ctx, productId, buf, deviceId = '') {
  const id = checkId(productId);
  if (!Buffer.isBuffer(buf) || buf.length < 100) throw badRequest('عکس خالی است');
  if (buf.length > MAX_BYTES) throw badRequest('عکس بزرگ‌تر از ۶۰۰ کیلوبایت است');
  if (!sniff(buf)) throw badRequest('فقط عکسِ JPG، PNG یا WebP');
  const count = await one(
    'SELECT COUNT(*)::int AS n FROM product_photos WHERE app=$1 AND account_id=$2 AND data IS NOT NULL',
    [ctx.app, ctx.accountId],
  );
  if (Number(count?.n || 0) >= MAX_PHOTOS) {
    const exists = await one('SELECT 1 FROM product_photos WHERE app=$1 AND account_id=$2 AND product_id=$3 AND data IS NOT NULL',
      [ctx.app, ctx.accountId, id]);
    if (!exists) throw badRequest('سقفِ عکس‌های این دکان پر است');
  }
  const hash = crypto.createHash('sha256').update(buf).digest('hex').slice(0, 32);
  const row = await one(
    `INSERT INTO product_photos (app, account_id, product_id, data, hash, size, rev, updated_at, device_id)
     VALUES ($1,$2,$3,$4,$5,$6,nextval('product_photo_rev'),$7,$8)
     ON CONFLICT (app, account_id, product_id) DO UPDATE
       SET data=EXCLUDED.data, hash=EXCLUDED.hash, size=EXCLUDED.size, rev=EXCLUDED.rev,
           updated_at=EXCLUDED.updated_at, device_id=EXCLUDED.device_id
     RETURNING rev`,
    [ctx.app, ctx.accountId, id, buf, hash, buf.length, Date.now(), String(deviceId || '').slice(0, 64)],
  );
  return { product_id: id, hash, size: buf.length, rev: Number(row.rev) };
}

async function remove(ctx, productId, deviceId = '') {
  const id = checkId(productId);
  const row = await one(
    `UPDATE product_photos SET data=NULL, hash='', size=0, rev=nextval('product_photo_rev'), updated_at=$4, device_id=$5
      WHERE app=$1 AND account_id=$2 AND product_id=$3 AND data IS NOT NULL RETURNING rev`,
    [ctx.app, ctx.accountId, id, Date.now(), String(deviceId || '').slice(0, 64)],
  );
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
  await many('DELETE FROM photo_inbox WHERE created_at < $1 RETURNING id', [now - INBOX_TTL_MS]);
  const count = await one('SELECT COUNT(*)::int AS n FROM photo_inbox WHERE app=$1 AND account_id=$2', [ctx.app, ctx.accountId]);
  if (Number(count?.n || 0) >= INBOX_MAX) throw badRequest('صندوقِ عکس پر است — عکس‌های رسیده را در کامپیوتر بردارید');
  const id = 'in_' + crypto.randomBytes(12).toString('hex');
  await one(
    `INSERT INTO photo_inbox (id, app, account_id, pair, product_id, data, size, created_at, device_id)
     VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9) RETURNING id`,
    [id, ctx.app, ctx.accountId, p, pid, buf, buf.length, now, String(deviceId || '').slice(0, 64)],
  );
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
  const r = await one('SELECT data, product_id FROM photo_inbox WHERE app=$1 AND account_id=$2 AND id=$3',
    [ctx.app, ctx.accountId, String(id || '').slice(0, 64)]);
  return r ? { data: Buffer.from(r.data), product_id: r.product_id } : null;
}

async function inboxDelete(ctx, id) {
  const r = await one('DELETE FROM photo_inbox WHERE app=$1 AND account_id=$2 AND id=$3 RETURNING id',
    [ctx.app, ctx.accountId, String(id || '').slice(0, 64)]);
  return !!r;
}

module.exports = {
  list, get, put, remove, sniff, MAX_BYTES, MAX_PHOTOS,
  inboxPut, inboxList, inboxGet, inboxDelete, INBOX_MAX_BYTES,
};
