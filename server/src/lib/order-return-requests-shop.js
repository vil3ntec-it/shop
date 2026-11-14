'use strict';
/**
 * RET-11 — صندوقِ «درخواست‌های برگشت» ِ دکان (`routes/shop-return-requests.js`)؛ سمتِ مشتری و قاعده‌ها در
 * `order-return-requests.js`.
 *
 *   فهرست ──── `orders.view`: در انتظار (پیش‌فرض) | پذیرفته | ثبت‌شده | ردشده | همه، با نامِ قلم‌ها و فرادادهٔ عکس.
 *   پذیرفتن ── pending ⇒ accepted (دستگاهِ دوم ⇒ همان «پذیرفته»، بی دوگانه). برنامه سپس همان برگهٔ برگشتِ موجود را
 *              با اقلامِ پرشده باز می‌کند (`ReturnFlowSheet` ⇐ `ReturnFlow.post`، سندِ `o-<سفارش>-rq-<درخواست>`).
 *   ثبت‌شد ─── accepted ⇒ completed با شناسهٔ **همان** سند (هر شناسهٔ دیگر ۴۰۰)؛ تکرار بی‌اثر.
 *   رد ─────── pending/accepted ⇒ rejected با دلیلِ ۲ تا ۱۰۰۰ نویسه (مشتری می‌بیند)؛ همان دلیل دوباره بی‌اثر.
 * ⛔ نوشتن: `orders.manage` + مجوزِ برگشتِ موجود (`returns.create` ِ نقش و لایه‌ها، نه «ممنوع») + اشتراک؛ پرچمِ خاموش ⇒ ۴۰۳.
 * ⛔ پول و موجودی این‌جا عوض نمی‌شوند؛ فقط وضعیت و شناسهٔ سند. بازپرداختِ آنلاین منتظرِ حساب‌پی است (نقد یا اعتبار از
 *    همان برگهٔ برگشت).
 * ⛔ اعلان: دکان ⇐ همان پوشِ گیرنده‌های اعلانِ سفارش (هیچ کانالِ پولی)؛ مشتری ⇐ صفِ `customer-notify` (یک پیام برای هر
 *    تصمیمِ هر درخواست، فقط کانال‌های رایگان).
 */
const db = require('../db');
const { now } = db;
const R = require('./order-return-requests');
const imgStore = require('./image-store');
const { badRequest, notFound, conflict } = require('../middleware/errors');

const FILTERS = Object.freeze(['pending', 'accepted', 'completed', 'rejected', 'all']);
const NOTE = Object.freeze({ min: 2, max: 1000 });
const DOC_RE = /^o-[0-9A-Za-z_-]{1,64}-rq-[0-9A-Z]{26}$/;

const photoView = (p) => ({ id: p.id, mime: p.mime, size: Number(p.size), width: Number(p.width), height: Number(p.height), createdAt: Number(p.created_at) });

/** نمای دکان: درخواست + شمارهٔ سفارش، نام و واحدِ هر قلم، شناسهٔ سندِ برگشت و فرادادهٔ عکس‌ها (بی بایت) */
function shopView(r, lines, photos) {
  const v = R.requestView(r, photos.length);
  return {
    ...v, orderId: r.order_id, orderNo: Number(r.order_no),
    lines: v.lines.map((l) => {
      const ol = lines.find((x) => x.order_id === r.order_id && Number(x.line_no) === l.lineNo) || {};
      return { ...l, productId: String(ol.product_id || ''), name: String(ol.name || ''), unit: String(ol.unit || ''), boughtMilli: Number(ol.qty_milli || 0) };
    }),
    photos: photos.map(photoView), decidedBy: r.decided_by || null,
    returnDocId: r.return_doc_id || null, docId: R.docIdOf(r.order_id, r.id),
  };
}

async function withDetails(run, shopId, rows) {
  if (!rows.length) return [];
  const orderIds = [...new Set(rows.map((r) => r.order_id))];
  const lines = (await run(
    `SELECT order_id, line_no, product_id, name, unit, qty_milli FROM online_order_lines WHERE shop_id=$1 AND order_id = ANY($2::text[])`,
    [shopId, orderIds])).rows;
  const photos = (await run(
    `SELECT id, request_id, mime, size, width, height, created_at FROM order_return_request_photos
      WHERE shop_id=$1 AND request_id = ANY($2::text[]) ORDER BY created_at, id`, [shopId, rows.map((r) => r.id)])).rows;
  return rows.map((r) => shopView(r, lines, photos.filter((p) => p.request_id === r.id)));
}

const pool = (sql, args) => db.query(sql, args);
const SELECT = `SELECT r.*, o.order_no FROM order_return_requests r JOIN online_orders o ON o.id = r.order_id`;

/** فهرست (بی بایت): تازه‌ترین اول، حداکثر ۲۰۰؛ `waiting` = شمارِ «در انتظار» برای نشانِ دکمه */
async function forShop(shopId, q = {}) {
  await R.needFlag(shopId);
  const status = q.status === undefined || q.status === '' ? 'pending' : String(q.status);
  if (!FILTERS.includes(status)) throw badRequest('وضعیت معتبر نیست', 'bad_status');
  const rows = (await pool(`${SELECT} WHERE r.shop_id=$1 AND ($2 = 'all' OR r.status = $2) ORDER BY r.created_at DESC, r.id DESC LIMIT 200`,
    [shopId, status])).rows;
  const waiting = (await pool(`SELECT COUNT(*)::int AS n FROM order_return_requests WHERE shop_id=$1 AND status='pending'`, [shopId])).rows[0].n;
  return { requests: await withDetails(pool, shopId, rows), waiting: Number(waiting) };
}

/** بایت‌های یک عکس — فقط همان دکان و همان درخواست */
async function photoOf(shopId, requestId, photoId) {
  await R.needFlag(shopId);
  const rid = String(requestId || ''); const pid = String(photoId || '');
  if (!R.ID_RE.test(rid) || !R.ID_RE.test(pid)) throw notFound('عکس پیدا نشد', 'photo_not_found');
  const r = await db.one('SELECT id, mime, data, store_key FROM order_return_request_photos WHERE shop_id=$1 AND request_id=$2 AND id=$3', [shopId, rid, pid]);
  const data = r && await imgStore.bytes(imgStore.shopTenant(shopId), r);
  if (!data) throw notFound('عکس پیدا نشد', 'photo_not_found');
  return { id: r.id, kind: 'image', mime: r.mime, data };
}

/** گذارِ یک درخواست زیرِ قفلِ خودش ⇒ `{request, unchanged, from}`؛ `step(row, at)` ⇒ `null` (بی‌اثر) یا ستون‌های تازه */
async function move(shopId, requestId, step, kind) {
  await R.needFlag(shopId);
  const rid = String(requestId || '');
  if (!R.ID_RE.test(rid)) throw notFound('درخواست پیدا نشد', 'request_not_found');
  const out = await db.tx(async (c) => {
    const run = (sql, args) => c.query(sql, args);
    const r = (await run(`${SELECT} WHERE r.shop_id=$1 AND r.id=$2 FOR UPDATE OF r`, [shopId, rid])).rows[0];
    if (!r) throw notFound('درخواست پیدا نشد', 'request_not_found');
    const at = now();
    const set = step(r, at);
    if (!set) return { request: (await withDetails(run, shopId, [r]))[0], unchanged: true, from: r.status };
    const cols = Object.keys(set);
    const u = (await run(
      `UPDATE order_return_requests SET ${cols.map((k, i) => `${k}=$${i + 3}`).join(', ')}, updated_at=$${cols.length + 3}
        WHERE id=$1 AND shop_id=$2 RETURNING *`, [rid, shopId, ...cols.map((k) => set[k]), at])).rows[0];
    const order = (await run('SELECT * FROM online_orders WHERE id=$1 AND shop_id=$2', [u.order_id, shopId])).rows[0];
    await require('./customer-notify').onReturnIn(run, { shopId, order, requestId: rid, kind, at });
    return { request: (await withDetails(run, shopId, [{ ...u, order_no: r.order_no }]))[0], unchanged: false, from: r.status };
  });
  return out;
}

const by = (actor) => String((actor && actor.id) || '');

/** «پذیرفتن» — فقط از «در انتظار»؛ پذیرفته ⇒ بی‌اثر (دستگاهِ دوم همان را می‌بیند)؛ بسته ⇒ ۴۰۹ */
function accept(shopId, requestId, actor) {
  return move(shopId, requestId, (r, at) => {
    if (r.status === 'accepted') return null;
    if (r.status !== 'pending') throw conflict('این درخواست پیش‌تر بسته شده است', 'request_closed');
    return { status: 'accepted', decided_by: by(actor), decided_at: at };
  }, 'order_return_accepted');
}

/** «رد با دلیل» — از «در انتظار» یا «پذیرفته» (کالا نرسید)؛ همان دلیل دوباره بی‌اثر */
function reject(shopId, requestId, body, actor) {
  const note = require('./order-issues').cleanText(body && body.note, NOTE, 'bad_note');
  return move(shopId, requestId, (r, at) => {
    if (r.status === 'rejected') {
      if (r.decision_note !== note) throw conflict('این درخواست پیش‌تر با دلیلِ دیگری رد شده است', 'already_rejected');
      return null;
    }
    if (!R.OPEN.includes(r.status)) throw conflict('این درخواست پیش‌تر بسته شده است', 'request_closed');
    return { status: 'rejected', decision_note: note, decided_by: by(actor), decided_at: at };
  }, 'order_return_rejected');
}

/** «برگشت ثبت شد» — فقط پس از پذیرفتن و فقط با شناسهٔ سندِ همین درخواست؛ تکرارِ همان سند بی‌اثر */
function complete(shopId, requestId, body, actor) {
  const doc = typeof (body && body.returnDocId) === 'string' ? body.returnDocId.trim() : '';
  if (!DOC_RE.test(doc)) throw badRequest('شناسهٔ سندِ برگشت لازم است', 'bad_return_doc');
  return move(shopId, requestId, (r, at) => {
    if (doc !== R.docIdOf(r.order_id, r.id)) throw badRequest('این سند مالِ همین درخواست نیست', 'bad_return_doc');
    if (r.status === 'completed') return null;
    if (r.status !== 'accepted') throw conflict('نخست درخواست را بپذیرید', 'not_accepted');
    return { status: 'completed', return_doc_id: doc, completed_at: at, decided_by: r.decided_by || by(actor) };
  }, 'order_return_completed');
}

/** اعلانِ «درخواستِ برگشتِ تازه» به کارمندان — همان پوشِ اعلانِ سفارش؛ شکستش درخواست را پس نمی‌گیرد */
async function notifyShop(shopId, orderId, orderNo) {
  try {
    const on = require('./order-notify');
    if (!(await on.flagOf(shopId))) return { sent: 0, skipped: 'flag_off' };
    const title = `درخواستِ برگشت برای سفارشِ ${String(orderNo).replace(/[0-9]/g, (d) => '۰۱۲۳۴۵۶۷۸۹'[Number(d)])}`;
    const body = 'مشتری از ویترین درخواستِ برگشت فرستاد؛ در «درخواست‌های برگشت» ببینید و بپذیرید یا رد کنید.';
    if (require('./notify-cost').costMicros('push', title + body) > 0) return { sent: 0, skipped: 'paid_channel' };
    const onlyUserIds = await on.recipientIds(shopId);
    return (await require('./push').sendTo({ shopId, app: 'shop', onlyUserIds }, {
      title, body, channel: on.CHANNEL,
      data: { type: 'order_return_request', orderId, orderNo: String(orderNo), shopId },
    })) || {};
  } catch (err) {
    console.error('[order-return-requests] اعلان', err && err.message);
    return { sent: 0, error: String((err && err.message) || err) };
  }
}

module.exports = { FILTERS, NOTE, forShop, photoOf, accept, reject, complete, notifyShop };
