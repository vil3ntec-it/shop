'use strict';
/**
 * انتقالِ دومرحله‌ایِ موجودی با «در مسیر» و قبول یا رد در مقصد — INV-11، سناریوی S21 (v3 §۶.۵).
 *
 *   ارسال ─── کالا همان لحظه از مبدأ کم و در `in_transit` دیده می‌شود (دو حرکتِ `transfer_out`/
 *             `transfer_in`، بی موجودیِ منفی: حالتِ `local` کمبود را رد می‌کند).
 *   دریافت ── مقصد مقدارِ رسیده را می‌گوید: رسیده از `in_transit` به مقصد می‌رود؛ **کسری هرگز
 *             بی‌صدا گم نمی‌شود**: دلیلش اجباری است، یک حرکتِ `adjustment` ِ منفی با همان دلیل در
 *             `in_transit` می‌نشیند و یک «وظیفهٔ بررسی» (`transfer_short`) برای مدیر باز می‌شود.
 *             بیش از ارسال پذیرفته نمی‌شود (کالای اضافه حرکتِ جدای خودش را دارد).
 *   رد ─────── همه از `in_transit` به مبدأ برمی‌گردد، با دلیل.
 *
 * ⛔ همان قاعده‌های `catalog.manualMove`: پشتِ پرچمِ `shop.catalog`، دکان فقط از عضویتِ توکن، دفترِ
 *    دستگاه پیش از نوشتن خوانده می‌شود، همه زیرِ قفلِ موجودیِ دکان و در یک تراکنش.
 * ⛔ کلیدِ یکتایی برای هر سه کار: تکرارِ همان درخواست ⇒ همان نتیجه، بی اثرِ دوم؛ همان کلید با بدنهٔ
 *    دیگر ⇒ ۴۰۹. گذارِ وضعیت فقط از `in_transit`؛ انتقالِ بسته‌شده دوباره بسته نمی‌شود.
 * ⛔ هر حرکت کلیدِ `xfer:<شناسه>:<گام>:<ردیف>` دارد (UNIQUE در `stock_moves`)، پس حتی تکرارِ ناخواسته
 *    هم دو بار کم نمی‌کند. شعبه هنوز مدل ندارد (§۳۸.۳)؛ مبدأ و مقصد مکان‌های همین دکان‌اند.
 */
const crypto = require('crypto');
const { now, tx, newId } = require('../db');
const stock = require('./stock');
const catalog = require('./catalog');
const { stable } = require('./idempotency');
const { badRequest, notFound, conflict } = require('../middleware/errors');

const ENDPOINTS = Object.freeze(['shop_floor', 'warehouse']);
const STATUSES = Object.freeze(['in_transit', 'received', 'rejected']);
const MAX_LINES = 100;
const MAX_QTY_MILLI = 1_000_000_000;   // همان سقفِ حرکتِ دستی: یک میلیون واحد
const KEY_RE = /^[A-Za-z0-9_.:-]{8,128}$/;
const MAX_LIST = 100;

const str = (v) => (typeof v === 'string' ? v.trim() : '');
const num = (v) => (v === null || v === undefined ? 0 : Number(v));
function text(v, max) {
  const { stripControl } = require('./validate');
  return stripControl(str(v)).slice(0, max);
}
const hashOf = (body) => crypto.createHash('sha256').update(stable(body || {})).digest('hex').slice(0, 32);

function keyOf(raw) {
  const key = str(raw);
  if (!key) throw badRequest('کلیدِ یکتاییِ انتقال لازم است', 'idempotency_key_required');
  if (!KEY_RE.test(key)) throw badRequest('کلیدِ یکتایی نامعتبر است', 'bad_idempotency_key');
  return key;
}

function qtyOf(v, { zero = false } = {}) {
  if (typeof v !== 'number' || !Number.isSafeInteger(v) || v < (zero ? 0 : 1) || v > MAX_QTY_MILLI) {
    throw badRequest(zero ? 'مقدارِ رسیده باید عددِ صحیحِ نامنفی (هزارم) باشد' : 'مقدار باید عددِ صحیحِ مثبت (هزارم) باشد', 'bad_qty');
  }
  return v;
}

function endpointOf(v) {
  const l = str(v);
  if (!ENDPOINTS.includes(l)) throw badRequest('مبدأ/مقصدِ انتقال فقط «قفسه» یا «انبار» است', 'bad_location');
  return l;
}

/** درخواستِ ارسال ⇒ { from, to, lines: [{ productId, sentMilli }], note } */
function parseSend(body) {
  const b = body && typeof body === 'object' ? body : {};
  const from = endpointOf(b.from);
  const to = endpointOf(b.to);
  if (from === to) throw badRequest('مبدأ و مقصد یکی است', 'bad_location');
  const raw = Array.isArray(b.lines) ? b.lines : [];
  if (!raw.length) throw badRequest('دست‌کم یک کالا لازم است', 'lines_required');
  if (raw.length > MAX_LINES) throw badRequest(`حداکثر ${MAX_LINES} کالا در یک انتقال`, 'too_many_lines');
  const seen = new Set();
  const lines = raw.map((l) => {
    const productId = str(l && l.productId);
    if (!productId) throw badRequest('کالا لازم است', 'product_required');
    if (seen.has(productId)) throw badRequest('هر کالا یک بار در انتقال می‌آید', 'duplicate_product');
    seen.add(productId);
    return { productId, sentMilli: qtyOf(l.qtyMilli) };
  });
  return { from, to, lines, note: text(b.note, 300) };
}

function view(r) {
  const lines = (Array.isArray(r.lines) ? r.lines : JSON.parse(r.lines || '[]')).map((l) => ({
    productId: l.productId, sentMilli: num(l.sentMilli),
    receivedMilli: l.receivedMilli === null || l.receivedMilli === undefined ? null : num(l.receivedMilli),
    shortMilli: l.receivedMilli === null || l.receivedMilli === undefined ? 0 : num(l.sentMilli) - num(l.receivedMilli),
  }));
  return {
    id: r.id, from: r.from_location, to: r.to_location, status: r.status, lines,
    shortMilli: lines.reduce((s, l) => s + l.shortMilli, 0),
    note: r.note, reason: r.reason, createdBy: r.created_by, createdAt: num(r.created_at),
    closedBy: r.closed_by, closedAt: r.closed_at === null ? null : num(r.closed_at),
  };
}

/** همان آماده‌سازیِ `manualMove`: قفل، تنظیم و خواندنِ دفترِ دستگاه پیش از هر حرکت. */
async function begin(c, shopId, at) {
  await stock.lock(c, shopId);
  const settings = await stock.settingsOf(shopId, c);
  const ingested = await catalog.ingestTx(c, shopId, at, settings);
  return { settings, ingested };
}

const base = (t, actor, at, refNote) => ({
  mode: 'local', source: 'staff', refType: 'transfer', refId: t, actorType: 'user', actorId: actor.id, note: refNote || '', at,
});

/**
 * ارسال: ⇒ { transfer, replayed }. اجازه (`stock.move`) را مسیر می‌سنجد.
 * کالای ناشناس ⇒ ۴۰۴؛ کمبود در مبدأ ⇒ ۴۰۹ ِ هسته (هیچ ردیفی نوشته نمی‌شود: یک تراکنش).
 */
async function send(shopId, { key, body, actor }) {
  const k = keyOf(key);
  const req = parseSend(body);
  const hash = hashOf(body);
  const at = now();
  let ingested = null;
  const out = await tx(async (c) => {
    const prep = await begin(c, shopId, at);
    ingested = prep.ingested;
    const prev = (await c.query('SELECT * FROM stock_transfers WHERE shop_id=$1 AND send_key=$2', [shopId, k])).rows[0];
    if (prev) {
      if (prev.send_hash !== hash) throw conflict('این کلیدِ یکتایی پیش‌تر برای انتقالِ دیگری به کار رفته است', 'idempotency_key_reused');
      return { transfer: view(prev), replayed: true };
    }
    const ids = req.lines.map((l) => l.productId);
    const known = new Set((await c.query(
      'SELECT product_id FROM catalog_products WHERE shop_id=$1 AND product_id = ANY($2::text[])', [shopId, ids]
    )).rows.map((r) => r.product_id));
    const missing = ids.filter((id) => !known.has(id));
    if (missing.length) {
      const err = notFound('کالا پیدا نشد', 'product_not_found');
      err.data = { productIds: missing };
      throw err;
    }
    const id = newId('xfr');
    const b = base(id, actor, at, req.note);
    for (let i = 0; i < req.lines.length; i++) {
      const l = req.lines[i];
      await stock.postMove(c, shopId, { ...b, productId: l.productId, location: req.from, type: 'transfer_out', qtyMilli: l.sentMilli, reason: 'transfer_send', sourceKey: `xfer:${id}:send-out:${i + 1}` }, at);
      await stock.postMove(c, shopId, { ...b, productId: l.productId, location: 'in_transit', type: 'transfer_in', qtyMilli: l.sentMilli, reason: 'transfer_send', sourceKey: `xfer:${id}:send-in:${i + 1}` }, at);
    }
    const row = (await c.query(
      `INSERT INTO stock_transfers (id, shop_id, from_location, to_location, status, lines, note, send_key, send_hash, created_by, created_at)
       VALUES ($1,$2,$3,$4,'in_transit',$5::jsonb,$6,$7,$8,$9,$10) RETURNING *`,
      [id, shopId, req.from, req.to, JSON.stringify(req.lines.map((l) => ({ ...l, receivedMilli: null }))), req.note, k, hash, actor.id, at]
    )).rows[0];
    await stock.checkProducts(c, shopId, ids, prep.settings, at);
    return { transfer: view(row), replayed: false };
  });
  await catalog.afterIngest(shopId, ingested);
  return out;
}

/** بستنِ انتقال (دریافت یا رد) با کلیدِ یکتایی؛ `plan(row)` ⇒ { status, lines, reason, moves, tasks } */
async function close(shopId, id, { key, body, actor }, plan) {
  const k = keyOf(key);
  const hash = hashOf({ id, ...(body && typeof body === 'object' ? body : {}) });
  const at = now();
  let ingested = null;
  const out = await tx(async (c) => {
    const prep = await begin(c, shopId, at);
    ingested = prep.ingested;
    const row = (await c.query('SELECT * FROM stock_transfers WHERE shop_id=$1 AND id=$2 FOR UPDATE', [shopId, String(id || '')])).rows[0];
    if (!row) throw notFound('انتقال پیدا نشد', 'transfer_not_found');
    if (row.status !== 'in_transit') {
      if (row.close_key === k && row.close_hash === hash) return { transfer: view(row), replayed: true };
      throw conflict(row.status === 'received' ? 'این انتقال پیش‌تر دریافت شده است' : 'این انتقال پیش‌تر رد شده است', 'transfer_closed');
    }
    const p = plan(view(row));
    const b = base(row.id, actor, at, row.note);
    for (const m of p.moves) await stock.postMove(c, shopId, { ...b, ...m }, at);
    for (const t of p.tasks) await stock.openTask(c, shopId, t, at);
    const done = (await c.query(
      `UPDATE stock_transfers SET status=$3, lines=$4::jsonb, reason=$5, close_key=$6, close_hash=$7, closed_by=$8, closed_at=$9
        WHERE shop_id=$1 AND id=$2 RETURNING *`,
      [shopId, row.id, p.status, JSON.stringify(p.lines), p.reason, k, hash, actor.id, at]
    )).rows[0];
    await stock.checkProducts(c, shopId, p.lines.map((l) => l.productId), prep.settings, at);
    return { transfer: view(done), replayed: false };
  });
  await catalog.afterIngest(shopId, ingested);
  return out;
}

/**
 * دریافت در مقصد: `lines: [{ productId, receivedMilli }]`؛ کالایی که نیامده یعنی «همه رسید».
 * کسری ⇒ دلیلِ اجباری، حرکتِ `adjustment` ِ منفی در `in_transit` و وظیفهٔ `transfer_short`.
 */
function receive(shopId, id, { key, body, actor }) {
  const b = body && typeof body === 'object' ? body : {};
  const reason = text(b.reason, 120);
  const given = new Map();
  for (const l of Array.isArray(b.lines) ? b.lines : []) {
    const pid = str(l && l.productId);
    if (!pid) throw badRequest('کالا لازم است', 'product_required');
    if (given.has(pid)) throw badRequest('هر کالا یک بار می‌آید', 'duplicate_product');
    given.set(pid, qtyOf(l.receivedMilli, { zero: true }));
  }
  return close(shopId, id, { key, body, actor }, (t) => {
    const unknown = [...given.keys()].filter((pid) => !t.lines.some((l) => l.productId === pid));
    if (unknown.length) {
      const err = badRequest('این کالا در انتقال نیست', 'product_not_in_transfer');
      err.data = { productIds: unknown };
      throw err;
    }
    const lines = t.lines.map((l) => {
      const got = given.has(l.productId) ? given.get(l.productId) : l.sentMilli;
      if (got > l.sentMilli) {
        const err = badRequest('بیش از مقدارِ فرستاده نمی‌رسد؛ اضافه را جدا ثبت کنید', 'over_received');
        err.data = { productId: l.productId, sentMilli: l.sentMilli, receivedMilli: got };
        throw err;
      }
      return { productId: l.productId, sentMilli: l.sentMilli, receivedMilli: got };
    });
    const short = lines.filter((l) => l.receivedMilli < l.sentMilli);
    if (short.length && !reason) throw badRequest('برای کسری دلیل لازم است', 'reason_required');
    const moves = [];
    const tasks = [];
    lines.forEach((l, i) => {
      const n = i + 1;
      if (l.receivedMilli > 0) {
        moves.push({ productId: l.productId, location: 'in_transit', type: 'transfer_out', qtyMilli: l.receivedMilli, reason: 'transfer_receive', sourceKey: `xfer:${t.id}:recv-out:${n}` });
        moves.push({ productId: l.productId, location: t.to, type: 'transfer_in', qtyMilli: l.receivedMilli, reason: 'transfer_receive', sourceKey: `xfer:${t.id}:recv-in:${n}` });
      }
      const s = l.sentMilli - l.receivedMilli;
      if (s > 0) {
        moves.push({ productId: l.productId, location: 'in_transit', type: 'adjustment', qtyMilli: -s, reason: `کسریِ انتقال: ${reason}`.slice(0, 80), sourceKey: `xfer:${t.id}:short:${n}` });
        tasks.push({
          kind: 'transfer_short', productId: l.productId, location: 'in_transit', dedupe: `xfer-short:${t.id}:${l.productId}`,
          detail: { transferId: t.id, from: t.from, to: t.to, sentMilli: l.sentMilli, receivedMilli: l.receivedMilli, shortMilli: s, reason },
        });
      }
    });
    return { status: 'received', lines, reason: short.length ? reason : '', moves, tasks };
  });
}

/** رد در مقصد: همه از `in_transit` به مبدأ برمی‌گردد؛ دلیل اجباری. */
function reject(shopId, id, { key, body, actor }) {
  const reason = text(body && body.reason, 120);
  if (!reason) throw badRequest('دلیلِ رد لازم است', 'reason_required');
  return close(shopId, id, { key, body, actor }, (t) => ({
    status: 'rejected',
    lines: t.lines.map((l) => ({ productId: l.productId, sentMilli: l.sentMilli, receivedMilli: 0 })),
    reason,
    moves: t.lines.flatMap((l, i) => [
      { productId: l.productId, location: 'in_transit', type: 'transfer_out', qtyMilli: l.sentMilli, reason: 'transfer_reject', sourceKey: `xfer:${t.id}:back-out:${i + 1}` },
      { productId: l.productId, location: t.from, type: 'transfer_in', qtyMilli: l.sentMilli, reason: 'transfer_reject', sourceKey: `xfer:${t.id}:back-in:${i + 1}` },
    ]),
    tasks: [],
  }));
}

/** فهرست: تازه‌ترین اول؛ `status` اختیاری (`in_transit` ⇒ «در مسیر»). */
async function list(shopId, { status, limit, places = null } = {}) {
  const lim = Math.min(Math.max(Number(limit) || 50, 1), MAX_LIST);
  const st = str(status);
  if (st && !STATUSES.includes(st)) throw badRequest('وضعیتِ انتقال معتبر نیست', 'bad_status');
  const { query } = require('../db');
  const params = st ? [shopId, lim, st] : [shopId, lim];
  //  STF-06 (`staff-scope.js`): عضوِ محدود فقط انتقالی را می‌بیند که یک سرش در محدودهٔ اوست
  const mine = places ? ` AND (from_location = ANY($${params.push(places)}::text[]) OR to_location = ANY($${params.length}::text[]))` : '';
  const rows = (await query(
    `SELECT * FROM stock_transfers WHERE shop_id=$1 ${st ? 'AND status=$3' : ''}${mine} ORDER BY created_at DESC, id DESC LIMIT $2`,
    params
  )).rows;
  return rows.map(view);
}

module.exports = { ENDPOINTS, STATUSES, MAX_LINES, parseSend, send, receive, reject, list };
