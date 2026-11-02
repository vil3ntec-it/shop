'use strict';
/**
 * NTF-07 — گزارشِ مشکلِ سفارش با عکس (v3 §۶.۱۹)، پشتِ پرچمِ خاموشِ `shop.orders.issue_reports`، مهاجرتِ ۱۹۱.
 *
 *   مشتری ── با همان توکنِ پیگیریِ ویترین (`routes/order-feedback-store.js`): گزارش (نوع + شرح) و سپس عکس، یکی‌یکی.
 *   دکان ─── `routes/order-feedback.js`: فهرست، بایت‌های عکس (هر دیدن در سابقه) و «رسیدگی شد» با پاسخ.
 *   پیگیری ── کلیدِ `issueReports` در `GET /api/store/:slug/orders/:token` (فقط با پرچم؛ خاموش ⇒ کلید نیست).
 *
 * ⛔ پرچمِ خاموش ⇒ هر درِ مشتری همان ۴۰۴ «سفارش پیدا نشد» و هیچ ردیفی نوشته نمی‌شود؛ درِ دکان ۴۰۳.
 * ⛔ سفارش فقط از توکن (همان قاعدهٔ انقضا و باطل‌شدنِ لینک) و دکان فقط از `slug`، هرگز از بدنه.
 * ⛔ حداکثر ۳ گزارش برای هر سفارش و ۳ عکس برای هر گزارش؛ شرح ۳ تا ۱۰۰۰ نویسه، پاسخ ۲ تا ۱۰۰۰؛
 *    کلیدِ یکتای درخواست: ارسالِ دوباره ⇒ همان گزارش، همان کلید با بدنهٔ دیگر ⇒ ۴۰۹ `request_key_reused`.
 * ⛔ عکس: نوع فقط از محتوا (`image-bytes.inspect`: JPEG/PNG/WebP، ساختار، سقفِ ابعاد و پیکسل)، همان سقفِ
 *    اندازهٔ عکسِ کالا (`product-photos.MAX_BYTES`)، و پیش از نوشتن بی EXIF/GPS/XMP (`chat-media`؛ جهت می‌ماند).
 *    بایت‌ها فقط در پایگاه و فقط برای دکانِ همان سفارش — هیچ نشانیِ عمومی؛ مشتری فقط شمارِ عکس‌ها را می‌بیند.
 * ⛔ اعلانِ دکان فقط پوشِ کارمندان (همان پرچم و گیرنده‌های اعلانِ سفارش)؛ اگر پوش روزی قیمت بگیرد
 *    (`notify-cost`) این اعلان نمی‌رود — هیچ کانالِ پولی.
 */
const crypto = require('crypto');
const db = require('../db');
const { now } = db;
const orders = require('./orders');
const { ulid } = require('./ulid');
const bytes = require('./image-bytes');
const chatMedia = require('./chat-media');
const { MAX_BYTES } = require('./product-photos');
const { badRequest, notFound, conflict, forbidden } = require('../middleware/errors');

const FLAG = 'shop.orders.issue_reports';
const KINDS = Object.freeze(['missing_item', 'wrong_item', 'damaged', 'quality', 'late', 'other']);
const MAX_PER_ORDER = 3;
const MAX_PHOTOS = 3;
const TEXT = Object.freeze({ min: 3, max: 1000 });
const REPLY = Object.freeze({ min: 2, max: 1000 });
const KEY_RE = /^[A-Za-z0-9_.:-]{8,64}$/;
const ID_RE = /^[0-9A-Z]{26}$/;
const B64_RE = /^[A-Za-z0-9+/]+={0,2}$/;

const flagOn = (shopId) => orders.flagOf(shopId, FLAG);

/** متنِ آزاد: بی نویسهٔ کنترلی (خطِ تازه می‌ماند)، بی فاصلهٔ دو سر؛ طول به نویسه، نه بایت. */
function cleanText(v, { min, max }, code) {
  // eslint-disable-next-line no-control-regex
  const s =(typeof v === 'string' ? v : '').replace(/[\u0000-\u0009\u000B-\u001F\u007F‎‏‪-‮⁦-⁩]/g, ' ').trim();
  const n = [...s].length;
  if (n < min || n > max) throw badRequest(`متن باید ${min} تا ${max} نویسه باشد`, code);
  return s;
}

/** بدنهٔ گزارش ⇒ `{kind, text, key, hash}` — همهٔ سنجش‌ها پیش از هر نوشتن. */
function parseReport(body) {
  const b = body && typeof body === 'object' ? body : {};
  const kind = typeof b.kind === 'string' ? b.kind : '';
  if (!KINDS.includes(kind)) throw badRequest('نوعِ مشکل معتبر نیست', 'bad_kind');
  const text = cleanText(b.text, TEXT, 'bad_text');
  const key = typeof b.requestKey === 'string' ? b.requestKey.trim() : '';
  if (!KEY_RE.test(key)) throw badRequest('کلیدِ یکتای درخواست لازم است', 'bad_request_key');
  return { kind, text, key, hash: crypto.createHash('sha256').update(JSON.stringify([kind, text])).digest('hex') };
}

/** بدنهٔ عکس ⇒ `{data, mime, width, height, hash}` — نوع از محتوا، سقفِ اندازه و ابعاد، بی فراداده. */
function parsePhoto(body) {
  const b = body && typeof body === 'object' ? body : {};
  const b64 = typeof b.imageBase64 === 'string' ? b.imageBase64 : '';
  if (!b64) throw badRequest('عکس لازم است', 'photo_required');
  if (b64.length > Math.ceil(MAX_BYTES * 4 / 3) + 8) throw badRequest('عکس بزرگ‌تر از حدِ مجاز است', 'photo_too_large');
  if (!B64_RE.test(b64)) throw badRequest('فقط عکسِ JPG، PNG یا WebP', 'image_type');
  const raw = Buffer.from(b64, 'base64');
  if (raw.length > MAX_BYTES) throw badRequest('عکس بزرگ‌تر از حدِ مجاز است', 'photo_too_large');
  const info = bytes.inspect(raw);   // image_empty / image_type / image_corrupt / image_too_large_dimensions
  const data = chatMedia.stripImageMetadata(raw, info.mime);
  return {
    data, mime: info.mime, width: info.width, height: info.height,
    hash: crypto.createHash('sha256').update(raw).digest('hex'),
  };
}

const num = (v) => (v === null || v === undefined ? null : Number(v));
const photoView = (p) => ({ id: p.id, mime: p.mime, size: Number(p.size), width: Number(p.width), height: Number(p.height), createdAt: Number(p.created_at) });

/** نمای مشتری: بی کارمند و بی بایت. */
const customerIssue = (r, photos) => ({
  id: r.id, kind: r.kind, text: r.body, status: r.status, reply: r.reply,
  photos: photos, createdAt: Number(r.created_at), resolvedAt: num(r.resolved_at),
});

const photoCount = async (run, issueId) => Number((await run(
  'SELECT COUNT(*)::int AS n FROM order_issue_photos WHERE issue_id=$1', [issueId])).rows[0].n);

/** مشتری: سفارش از توکن (همان قاعدهٔ لینکِ پیگیری). پرچمِ خاموش ⇒ همان «پیدا نشد». */
async function orderOfCustomer(store, token) {
  if (!(await flagOn(store.shop_id))) throw notFound('سفارش پیدا نشد', 'order_not_found');
  const id = await require('./storefront').orderIdOfLink(store, token);
  const o = await db.one('SELECT id, source FROM online_orders WHERE shop_id=$1 AND id=$2', [store.shop_id, id]);
  if (!o || o.source !== 'storefront') throw notFound('سفارش پیدا نشد', 'order_not_found');
  return o.id;
}

/** کلیدِ `issueReports` ِ پیگیری — بی پرچم `undefined` (کلید نیاید). */
async function customerView(shopId, orderId) {
  if (!(await flagOn(shopId))) return undefined;
  const rows = await db.many(
    `SELECT i.*, (SELECT COUNT(*)::int FROM order_issue_photos p WHERE p.issue_id = i.id) AS photos
       FROM order_issues i WHERE i.shop_id=$1 AND i.order_id=$2 ORDER BY i.created_at, i.id`, [shopId, String(orderId || '')]);
  return {
    canReport: rows.length < MAX_PER_ORDER,
    limits: { issues: MAX_PER_ORDER, photos: MAX_PHOTOS, textMax: TEXT.max, photoBytes: MAX_BYTES },
    kinds: KINDS,
    issues: rows.map((r) => customerIssue(r, Number(r.photos))),
  };
}

/** اعلانِ «گزارشِ تازه» به کارمندان — شکستش گزارش را پس نمی‌گیرد. */
async function notifyShop(shopId, orderId, orderNo) {
  try {
    const on = require('./order-notify');
    if (!(await on.flagOf(shopId))) return { sent: 0, skipped: 'flag_off' };
    const title = `گزارشِ مشکل برای سفارشِ ${String(orderNo).replace(/[0-9]/g, (d) => '۰۱۲۳۴۵۶۷۸۹'[Number(d)])}`;
    const body = 'مشتری از صفحهٔ پیگیری مشکلی گزارش کرد؛ در «گزارش‌های مشکل» ببینید و پاسخ دهید.';
    if (require('./notify-cost').costMicros('push', title + body) > 0) return { sent: 0, skipped: 'paid_channel' };
    const onlyUserIds = await on.recipientIds(shopId);
    return (await require('./push').sendTo({ shopId, app: 'shop', onlyUserIds }, {
      title, body, channel: on.CHANNEL,
      data: { type: 'order_issue', orderId, orderNo: String(orderNo), shopId },
    })) || {};
  } catch (err) {
    console.error('[order-issues] اعلان', err && err.message);
    return { sent: 0, error: String((err && err.message) || err) };
  }
}

/** مشتری: گزارشِ تازه ⇒ `{issue, replayed}` */
async function report(store, token, body) {
  const orderId = await orderOfCustomer(store, token);
  const p = parseReport(body);
  const out = await db.tx(async (c) => {
    const run = (sql, args) => c.query(sql, args);
    const o = (await run('SELECT id, order_no FROM online_orders WHERE shop_id=$1 AND id=$2 FOR UPDATE', [store.shop_id, orderId])).rows[0];
    const prev = (await run('SELECT * FROM order_issues WHERE order_id=$1 AND request_key=$2', [orderId, p.key])).rows[0];
    if (prev) {
      if (prev.request_hash !== p.hash) throw conflict('این کلید پیش‌تر برای گزارشِ دیگری به کار رفته است', 'request_key_reused');
      return { issue: customerIssue(prev, await photoCount(run, prev.id)), replayed: true };
    }
    const n = Number((await run('SELECT COUNT(*)::int AS n FROM order_issues WHERE order_id=$1', [orderId])).rows[0].n);
    if (n >= MAX_PER_ORDER) throw conflict(`حداکثر ${MAX_PER_ORDER} گزارش برای هر سفارش`, 'too_many_issues');
    const at = now();
    const row = (await run(
      `INSERT INTO order_issues (id, shop_id, order_id, kind, body, request_key, request_hash, created_at)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8) RETURNING *`,
      [ulid(at), store.shop_id, orderId, p.kind, p.text, p.key, p.hash, at])).rows[0];
    return { issue: customerIssue(row, 0), replayed: false, orderNo: Number(o.order_no) };
  });
  if (!out.replayed) await notifyShop(store.shop_id, orderId, out.orderNo);
  return { issue: out.issue, replayed: out.replayed };
}

/** مشتری: یک عکس برای گزارشِ بازِ خودش ⇒ `{photo, replayed}` */
async function addPhoto(store, token, issueId, body) {
  const orderId = await orderOfCustomer(store, token);
  const iid = String(issueId || '');
  if (!ID_RE.test(iid)) throw notFound('گزارش پیدا نشد', 'issue_not_found');
  const p = parsePhoto(body);
  return db.tx(async (c) => {
    const run = (sql, args) => c.query(sql, args);
    const issue = (await run('SELECT id, status FROM order_issues WHERE id=$1 AND order_id=$2 AND shop_id=$3 FOR UPDATE',
      [iid, orderId, store.shop_id])).rows[0];
    if (!issue) throw notFound('گزارش پیدا نشد', 'issue_not_found');
    const prev = (await run('SELECT * FROM order_issue_photos WHERE issue_id=$1 AND hash=$2', [iid, p.hash])).rows[0];
    if (prev) return { photo: photoView(prev), replayed: true };
    if (issue.status !== 'open') throw conflict('این گزارش بسته شده است', 'issue_closed');
    if ((await photoCount(run, iid)) >= MAX_PHOTOS) throw conflict(`حداکثر ${MAX_PHOTOS} عکس برای هر گزارش`, 'too_many_photos');
    const at = now();
    const row = (await run(
      `INSERT INTO order_issue_photos (id, shop_id, issue_id, mime, size, width, height, hash, data, created_at)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10) RETURNING id, mime, size, width, height, created_at`,
      [ulid(at), store.shop_id, iid, p.mime, p.data.length, p.width, p.height, p.hash, p.data, at])).rows[0];
    return { photo: photoView(row), replayed: false };
  });
}

// ════════════════════════════════════════════════════════════════════
//  دکان
// ════════════════════════════════════════════════════════════════════

async function needFlag(shopId) {
  if (!(await flagOn(shopId))) throw forbidden('گزارشِ مشکلِ سفارش برای این دکان روشن نیست', 'issue_reports_disabled');
}

const shopIssue = (r, photos) => ({
  ...customerIssue(r, photos.length), orderId: r.order_id, orderNo: Number(r.order_no),
  photos: photos.map(photoView), resolvedBy: r.resolved_by || null,
});

/** فهرستِ گزارش‌ها (بی بایت): `status` = open (پیش‌فرض) | resolved | all؛ تازه‌ترین اول، حداکثر ۲۰۰. */
async function forShop(shopId, q = {}) {
  await needFlag(shopId);
  const status = q.status === undefined || q.status === '' ? 'open' : String(q.status);
  if (!['open', 'resolved', 'all'].includes(status)) throw badRequest('وضعیت معتبر نیست', 'bad_status');
  const rows = await db.many(
    `SELECT i.*, o.order_no FROM order_issues i JOIN online_orders o ON o.id = i.order_id
      WHERE i.shop_id=$1 AND ($2 = 'all' OR i.status = $2) ORDER BY i.created_at DESC, i.id DESC LIMIT 200`, [shopId, status]);
  const ids = rows.map((r) => r.id);
  const photos = ids.length ? await db.many(
    `SELECT id, issue_id, mime, size, width, height, created_at FROM order_issue_photos
      WHERE shop_id=$1 AND issue_id = ANY($2::text[]) ORDER BY created_at, id`, [shopId, ids]) : [];
  return { issues: rows.map((r) => shopIssue(r, photos.filter((p) => p.issue_id === r.id))) };
}

/** بایت‌های یک عکس — فقط همان دکان و همان گزارش. */
async function photoOf(shopId, issueId, photoId) {
  await needFlag(shopId);
  const iid = String(issueId || '');
  const pid = String(photoId || '');
  if (!ID_RE.test(iid) || !ID_RE.test(pid)) throw notFound('عکس پیدا نشد', 'photo_not_found');
  const r = await db.one('SELECT id, mime, data FROM order_issue_photos WHERE shop_id=$1 AND issue_id=$2 AND id=$3', [shopId, iid, pid]);
  if (!r) throw notFound('عکس پیدا نشد', 'photo_not_found');
  return { id: r.id, kind: 'image', mime: r.mime, data: Buffer.from(r.data) };
}

/** «رسیدگی شد» با پاسخ ⇒ `{issue, unchanged}`؛ همان پاسخ دوباره بی‌اثر، پاسخِ دیگر ⇒ ۴۰۹. */
async function resolve(shopId, issueId, body, actor) {
  await needFlag(shopId);
  const iid = String(issueId || '');
  if (!ID_RE.test(iid)) throw notFound('گزارش پیدا نشد', 'issue_not_found');
  const reply = cleanText(body && body.reply, REPLY, 'bad_reply');
  return db.tx(async (c) => {
    const run = (sql, args) => c.query(sql, args);
    const r = (await run(
      `SELECT i.*, o.order_no FROM order_issues i JOIN online_orders o ON o.id = i.order_id
        WHERE i.shop_id=$1 AND i.id=$2 FOR UPDATE OF i`, [shopId, iid])).rows[0];
    if (!r) throw notFound('گزارش پیدا نشد', 'issue_not_found');
    const photos = (await run('SELECT id, mime, size, width, height, created_at FROM order_issue_photos WHERE issue_id=$1 ORDER BY created_at, id', [iid])).rows;
    if (r.status === 'resolved') {
      if (r.reply !== reply) throw conflict('این گزارش پیش‌تر با پاسخِ دیگری بسته شده است', 'already_resolved');
      return { issue: shopIssue(r, photos), unchanged: true };
    }
    const at = now();
    const u = (await run(
      `UPDATE order_issues SET status='resolved', reply=$2, resolved_by=$3, resolved_at=$4 WHERE id=$1 RETURNING *`,
      [iid, reply, String((actor && actor.id) || ''), at])).rows[0];
    return { issue: shopIssue({ ...u, order_no: r.order_no }, photos), unchanged: false };
  });
}

module.exports = {
  FLAG, KINDS, MAX_PER_ORDER, MAX_PHOTOS, TEXT, REPLY,
  parseReport, parsePhoto, customerView, report, addPhoto, forShop, photoOf, resolve,
};
