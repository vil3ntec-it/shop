'use strict';
/**
 * RET-11 — «درخواستِ برگشت» ِ مشتری از ویترین (تصمیمِ ۱۳ «ب» ِ صاحب‌کار، ۱۴۰۵/۰۸/۲۳)، پشتِ پرچمِ خاموشِ
 * `shop.storefront.return_requests`، مهاجرتِ ۸۶۶. این پرونده: سمتِ مشتری و قاعده‌های مشترک؛ دکان در
 * `order-return-requests-shop.js`.
 *
 *   مشتری ── در جزئیاتِ سفارشِ **تحویل‌شده**: قلم‌ها و تعداد، دلیل (فهرستِ کوتاه + متن)، روشِ رساندن (آوردن به دکان،
 *            یا پیک اگر پیکِ دکان روشن است) و سپس عکسِ اختیاری یکی‌یکی (`routes/store-return-requests.js`).
 *   پیگیری ── کلیدِ `returnRequest` در `GET /api/store/:slug/orders/:token` (فقط با پرچم؛ خاموش ⇒ کلید نیست).
 *   «سفارش‌های من» ── `GET /:slug/account/return-requests`: درخواست‌های همین حساب در همین دکان با وضعیت.
 *
 * ⛔ پرچم پیش از بدنه: خاموش ⇒ هر درِ مشتری همان درِ ناشناخته (`next('route')`) پیش از خواندنِ بدنه، و هیچ ردیفی.
 * ⛔ هویت = حسابِ ویترین (نشستِ `X-Store-Session`)، نه شماره و نه بلیتِ OTP: درخواست به `customer_id` گره می‌خورد.
 *    سفارش فقط از توکنِ پیگیری (همان قاعدهٔ انقضا/باطل‌شدن) و دکان فقط از `slug`، هرگز از بدنه. «صاحبِ سفارش» =
 *    همان قاعدهٔ «سفارش‌های من» ِ حساب ([ownsOrder] ⇐ `GET /:slug/account/orders`)؛ هر تغییرِ آن قاعده (ایمیل) همین‌جا هم.
 * ⛔ فقط سفارشِ تحویل‌شده ([ELIGIBLE])؛ مهلت و اقلامِ غیرقابلِ برگشت از سیاستِ **همان دکان** (`tohid-policy` ِ همگام‌شده:
 *    `return_window_days`، `return_blocked` — همان `ReturnPolicy` ِ برنامه)؛ بی همگام‌سازیِ تنظیمات ⇒ بی سیاست و تصمیم با دکان.
 * ⛔ تعداد ≤ خریده‌شده منهای درخواست‌های بازِ/پذیرفته/ثبت‌شدهٔ پیشین؛ یک درخواستِ باز در هر لحظه و حداکثر [MAX_PER_ORDER]؛
 *    همه زیرِ قفلِ ردیفِ سفارش (هم‌زمانی ⇒ یکی) و کلیدِ یکتای درخواست (تکرار ⇒ همان؛ همان کلید با بدنهٔ دیگر ⇒ ۴۰۹).
 * ⛔ عکس: همان راهِ امنِ گزارشِ مشکل (`order-issues.parsePhoto`: نوع از محتوا، سقفِ اندازه و ابعاد، بی EXIF/GPS) و
 *    بایت فقط برای دکانِ همان سفارش؛ مشتری فقط شمار را می‌بیند.
 * ⛔ پول و موجودی هرگز این‌جا: پذیرفتن در برنامهٔ دکان همان سندِ برگشتِ موجود است (`ReturnFlow.post`).
 */
const crypto = require('crypto');
const db = require('../db');
const { now } = db;
const orders = require('./orders');
const { ulid } = require('./ulid');
const imgStore = require('./image-store');
const T = require('./text-core');
const fw = require('./finance-window');
const { badRequest, notFound, conflict, forbidden } = require('../middleware/errors');

const FLAG = 'shop.storefront.return_requests';
const REASONS = Object.freeze(['damaged', 'wrong_item', 'expired', 'not_as_described', 'changed_mind', 'other']);
const METHODS = Object.freeze(['drop_off', 'courier']);
const STATUSES = Object.freeze(['pending', 'accepted', 'completed', 'rejected']);
const OPEN = Object.freeze(['pending', 'accepted']);
/** وضعیت‌هایی که «تحویل‌شده»اند و هنوز چیزی برای برگشت دارند (بازپرداختِ کامل ⇒ نه) */
const ELIGIBLE = Object.freeze(['delivered', 'picked_up', 'return_requested', 'refunded_partial']);
const MAX_PER_ORDER = 3;
const MAX_PHOTOS = 3;
const MAX_LINES = 50;
const TEXT = Object.freeze({ min: 0, max: 1000 });
const KEY_RE = /^[A-Za-z0-9_.:-]{8,64}$/;
const ID_RE = /^[0-9A-Z]{26}$/;
const MAX_DAYS = 3650;

const flagOn = (shopId, run) => orders.flagOf(shopId, FLAG, run);
const num = (v) => (v === null || v === undefined ? null : Number(v));
const hash = (x) => crypto.createHash('sha256').update(JSON.stringify(x)).digest('hex');

/** سندِ برگشتی که برنامهٔ دکان برای این درخواست می‌نویسد — همان `OrderReturnRequests.docIdOf` ِ هسته */
const docIdOf = (orderId, requestId) => `o-${orderId}-rq-${requestId}`;

/**
 * «صاحبِ سفارش» = همان قاعدهٔ «سفارش‌های من» ِ حساب (`sf.ordersOfPhone` ِ `GET /:slug/account/orders`). امروز پیوندِ
 * حساب ⇄ سفارش شمارهٔ حساب است؛ آزمونِ «مالکیت = سفارش‌های من» این دو را به هم می‌بندد تا با هم عوض شوند.
 */
function ownsOrder(customer, order) {
  return Boolean(customer && order && order.source === 'storefront' && customer.phone && order.customer_phone === customer.phone);
}

// ════════════════════════════════════════════════════════════════════
//  سیاستِ دکان (`tohid-policy` ِ همگام‌شده)
// ════════════════════════════════════════════════════════════════════

/** سیاستِ برگشتِ دکان ⇒ `{days, blocked[], known}`؛ همگام‌سازیِ تنظیمات خاموش ⇒ `known: false` و بی سیاست. */
async function policyOf(shopId, run) {
  if (!(await orders.flagOf(shopId, require('./shop-settings-sync').FLAG, run))) return { days: 0, blocked: [], known: false };
  const sql = `SELECT key, type, value FROM shop_setting_keys
    WHERE shop_id=$1 AND store='tohid-policy' AND key IN ('return_window_days','return_blocked')`;
  const rows = run ? (await run(sql, [String(shopId)])).rows : await db.many(sql, [String(shopId)]);
  const by = Object.fromEntries(rows.filter((r) => r.type !== 'del').map((r) => [r.key, typeof r.value === 'string' && r.type !== 's' ? JSON.parse(r.value) : r.value]));
  const d = Number(by.return_window_days);
  const days = Number.isInteger(d) && d > 0 ? Math.min(d, MAX_DAYS) : 0;
  return { days, blocked: parseRules(by.return_blocked), known: true };
}

/** همان `ReturnPolicy.parseRules`: هر خط یکی، بی خالی و تکراری (کلیدِ مقایسه)، حداکثر ۱۰۰ و هر خط ≤ ۶۰ */
function parseRules(text) {
  const out = []; const seen = new Set();
  for (const raw of String(typeof text === 'string' ? text : '').split(/[\r\n]/)) {
    const t = raw.trim().replace(/\s+/g, ' ');
    if (!t || t.length > 60) continue;
    const k = ruleKey(t);
    if (seen.has(k)) continue;
    seen.add(k); out.push(t);
    if (out.length === 100) break;
  }
  return out;
}
const ruleKey = (s) => T.compareKey(T.asciiDigits(s));

/** همان `ReturnPolicy.matches`: نامِ دسته، زیردسته، نامِ کالا یا بارکد */
function blockedBy(rules, item) {
  for (const rule of rules) {
    const k = ruleKey(rule);
    if (!k) continue;
    if ([item.category, item.subcategory, item.name].some((v) => v && ruleKey(v) === k)) return true;
    const code = T.asciiDigits(rule).trim();
    if (item.barcodes.some((b) => T.asciiDigits(String(b)).trim() === code)) return true;
  }
  return false;
}

/** دادهٔ کالاهای قلم‌ها از دفترِ همگام‌شدهٔ دکان (دسته، زیردسته، بارکد) — نبود ⇒ فقط نامِ قلم */
async function itemsOf(shopId, lines, run) {
  const ids = [...new Set(lines.map((l) => l.product_id).filter(Boolean))];
  const byId = new Map();
  if (ids.length) {
    const src = await require('./storefront').ledgerSource(shopId, { strict: false });
    const sql = `SELECT r.row_id AS id, r.data FROM ${src.from} WHERE ${src.where} AND r.row_id = ANY($2::text[])`;
    const rows = run ? (await run(sql, [shopId, ids])).rows : await db.many(sql, [shopId, ids]);
    for (const r of rows) byId.set(r.id, typeof r.data === 'string' ? JSON.parse(r.data) : r.data || {});
  }
  return lines.map((l) => {
    const d = byId.get(l.product_id) || {};
    const codes = [].concat(Array.isArray(d.barcodes) ? d.barcodes : [], typeof d.barcode === 'string' && d.barcode ? [d.barcode] : []);
    return { lineNo: Number(l.line_no), name: String(d.name || l.name || ''), category: String(d.category || ''), subcategory: String(d.subcategory || ''), barcodes: codes };
  });
}

// ════════════════════════════════════════════════════════════════════
//  حالِ یک سفارش (پیگیری و ساختن، یکی)
// ════════════════════════════════════════════════════════════════════

/** روزِ تحویل (نخستین گذار به تحویل/دریافت) به وقتِ ویترین */
async function deliveredAt(run, shopId, order) {
  const r = (await run(
    `SELECT MIN(created_at) AS at FROM online_order_events
      WHERE shop_id=$1 AND order_id=$2 AND kind='status' AND to_status IN ('delivered','picked_up')`, [shopId, order.id])).rows[0];
  return r && r.at !== null && r.at !== undefined ? Number(r.at) : Number(order.updated_at);
}

const requestView = (r, photos) => ({
  id: r.id, status: r.status, reason: r.reason, text: r.body, method: r.method,
  lines: (Array.isArray(r.lines) ? r.lines : JSON.parse(r.lines || '[]')).map((l) => ({ lineNo: Number(l.lineNo), qtyMilli: Number(l.qtyMilli) })),
  photos: Number(photos) || 0, decisionNote: r.decision_note || '', createdAt: Number(r.created_at),
  decidedAt: num(r.decided_at), completedAt: num(r.completed_at),
});

/**
 * همهٔ آن‌چه پیگیری و ساختن لازم دارند، با `run` ِ تراکنش (ساختن) یا استخر (پیگیری). `order` ردیفِ `online_orders`.
 * ⇒ `{lines: [{lineNo, name, unit, qtyMilli, leftMilli, blocked}], requests, why, days, lastDay, deadlineAt, methods}`
 */
async function stateOf(run, store, order, at = now()) {
  const shopId = store.shop_id;
  const lines = (await run(
    `SELECT line_no, product_id, name, unit, qty_milli FROM online_order_lines WHERE shop_id=$1 AND order_id=$2 ORDER BY line_no`,
    [shopId, order.id])).rows;
  const reqs = (await run(
    `SELECT r.*, (SELECT COUNT(*)::int FROM order_return_request_photos p WHERE p.request_id = r.id) AS photos
       FROM order_return_requests r WHERE r.shop_id=$1 AND r.order_id=$2 ORDER BY r.created_at, r.id`, [shopId, order.id])).rows;
  const used = new Map();
  for (const r of reqs) {
    if (r.status === 'rejected') continue;
    for (const l of requestView(r, 0).lines) used.set(l.lineNo, (used.get(l.lineNo) || 0) + l.qtyMilli);
  }
  const policy = await policyOf(shopId, run);
  const items = policy.blocked.length ? await itemsOf(shopId, lines, run) : [];
  const zone = store.timezone || 'Asia/Kabul';
  let days = 0; let lastDay = null; let deadlineAt = null; let late = false;
  const delivered = ELIGIBLE.includes(order.status);
  if (delivered && policy.days > 0) {
    const from = fw.localYmd(await deliveredAt(run, shopId, order), zone);
    days = policy.days;
    lastDay = fw.ymdAdd(from, days);
    deadlineAt = fw.dayStart(lastDay, zone, 1) - 1;
    late = fw.localYmd(at, zone) > lastDay;
  }
  const view = lines.map((l) => {
    const n = Number(l.line_no);
    const item = items.find((x) => x.lineNo === n);
    return {
      lineNo: n, name: String(l.name || ''), unit: String(l.unit || ''), qtyMilli: Number(l.qty_milli),
      leftMilli: Math.max(0, Number(l.qty_milli) - (used.get(n) || 0)),
      blocked: Boolean(item && blockedBy(policy.blocked, item)),
    };
  });
  const methods = ['drop_off'];
  if ((await require('./delivery').stateOf(shopId)).enabled) methods.push('courier');
  const open = reqs.some((r) => OPEN.includes(r.status));
  const why = !delivered ? 'not_delivered'
    : late ? 'late'
      : open ? 'open'
        : reqs.length >= MAX_PER_ORDER ? 'max'
          : !view.some((l) => l.leftMilli > 0 && !l.blocked) ? 'none_left' : '';
  return { lines: view, requests: reqs.map((r) => requestView(r, r.photos)), why, days, lastDay, deadlineAt, methods, policyKnown: policy.known };
}

/** کلیدِ `returnRequest` ِ پیگیری — بی پرچم یا سفارشِ ناتحویل بی درخواست ⇒ `undefined` (کلید نیاید). */
async function customerView(store, orderId) {
  if (!(await flagOn(store.shop_id))) return undefined;
  const order = await db.one('SELECT * FROM online_orders WHERE shop_id=$1 AND id=$2', [store.shop_id, String(orderId || '')]);
  if (!order || order.source !== 'storefront' || order.is_test === true) return undefined;
  const s = await stateOf((sql, args) => db.query(sql, args), store, order);
  if (s.why === 'not_delivered' && !s.requests.length) return undefined;
  return {
    canRequest: s.why === '', why: s.why, days: s.days, lastDay: s.lastDay, deadlineAt: s.deadlineAt,
    methods: s.methods, reasons: REASONS, lines: s.lines, requests: s.requests,
    limits: { photos: MAX_PHOTOS, textMax: TEXT.max, photoBytes: require('./product-photos').MAX_BYTES, requests: MAX_PER_ORDER },
  };
}

// ════════════════════════════════════════════════════════════════════
//  مشتری: ساختن و عکس
// ════════════════════════════════════════════════════════════════════

/** بدنهٔ درخواست ⇒ شکلِ پاک — همهٔ سنجش‌های بی‌پایگاه پیش از هر نوشتن */
function parseRequest(body) {
  const b = body && typeof body === 'object' && !Array.isArray(body) ? body : {};
  const reason = typeof b.reason === 'string' ? b.reason : '';
  if (!REASONS.includes(reason)) throw badRequest('دلیلِ برگشت معتبر نیست', 'bad_reason');
  const method = typeof b.method === 'string' ? b.method : '';
  if (!METHODS.includes(method)) throw badRequest('روشِ رساندنِ کالا معتبر نیست', 'bad_method');
  const raw = Array.isArray(b.lines) ? b.lines : [];
  if (raw.length < 1 || raw.length > MAX_LINES) throw badRequest('دست‌کم یک قلم را برگزینید', 'bad_lines');
  const seen = new Set();
  const lines = raw.map((l) => {
    const lineNo = l && Number(l.lineNo); const qtyMilli = l && Number(l.qtyMilli);
    if (!Number.isInteger(lineNo) || lineNo < 0 || lineNo > 100000 || seen.has(lineNo)) throw badRequest('قلمِ نادرست', 'bad_lines');
    if (!Number.isSafeInteger(qtyMilli) || qtyMilli <= 0) throw badRequest('تعدادِ نادرست', 'bad_qty');
    seen.add(lineNo);
    return { lineNo, qtyMilli };
  }).sort((x, y) => x.lineNo - y.lineNo);
  const text = require('./order-issues').cleanText(b.text === undefined ? '' : b.text, { min: reason === 'other' ? 3 : TEXT.min, max: TEXT.max }, 'bad_text');
  const key = typeof b.requestKey === 'string' ? b.requestKey.trim() : '';
  if (!KEY_RE.test(key)) throw badRequest('کلیدِ یکتای درخواست لازم است', 'bad_request_key');
  return { reason, method, lines, text, key, hash: hash([reason, method, lines, text]) };
}

/** سفارشِ همین توکن و همین حساب — نبودِ مالکیت ⇒ همان ۴۰۴ ِ «سفارش پیدا نشد» (بی نشتِ بودنِ سفارشِ دیگری) */
async function orderOf(run, store, customer, token, lock = false) {
  const id = await require('./storefront').orderIdOfLink(store, token);
  const o = (await run(`SELECT * FROM online_orders WHERE shop_id=$1 AND id=$2${lock ? ' FOR UPDATE' : ''}`, [store.shop_id, id])).rows[0];
  if (!o || o.is_test === true || !ownsOrder(customer, o)) throw notFound('سفارش پیدا نشد', 'order_not_found');
  return o;
}

/** مشتری: درخواستِ تازه ⇒ `{request, replayed}` */
async function create(store, customer, token, body) {
  const p = parseRequest(body);
  const out = await db.tx(async (c) => {
    const run = (sql, args) => c.query(sql, args);
    const o = await orderOf(run, store, customer, token, true);
    const prev = (await run('SELECT * FROM order_return_requests WHERE order_id=$1 AND request_key=$2', [o.id, p.key])).rows[0];
    if (prev) {
      if (prev.request_hash !== p.hash) throw conflict('این کلید پیش‌تر برای درخواستِ دیگری به کار رفته است', 'request_key_reused');
      const n = (await run('SELECT COUNT(*)::int AS n FROM order_return_request_photos WHERE request_id=$1', [prev.id])).rows[0].n;
      return { request: requestView(prev, n), replayed: true };
    }
    const s = await stateOf(run, store, o);
    if (s.why === 'not_delivered') throw conflict('فقط سفارشِ تحویل‌شده برگشت می‌پذیرد', 'not_delivered');
    if (s.why === 'late') throw conflict('مهلتِ برگشتِ این سفارش گذشته است', 'return_window_passed');
    if (s.why === 'open') throw conflict('برای این سفارش یک درخواستِ برگشتِ باز هست', 'return_open');
    if (s.why === 'max') throw conflict(`حداکثر ${MAX_PER_ORDER} درخواستِ برگشت برای هر سفارش`, 'too_many_requests');
    if (!s.methods.includes(p.method)) throw badRequest('پیکِ دکان برای برگشت در دسترس نیست', 'bad_method');
    for (const l of p.lines) {
      const line = s.lines.find((x) => x.lineNo === l.lineNo);
      if (!line) throw badRequest('قلمِ نادرست', 'bad_lines');
      if (line.blocked) throw conflict(`«${line.name}» برگشت‌پذیر نیست`, 'item_not_returnable');
      if (l.qtyMilli > line.leftMilli) throw conflict(`تعدادِ «${line.name}» بیش از خریده‌شده است`, 'qty_over');
    }
    const at = now();
    const row = (await run(
      `INSERT INTO order_return_requests (id, shop_id, order_id, customer_id, reason, body, method, lines, request_key, request_hash, created_at, updated_at)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8::jsonb,$9,$10,$11,$11) RETURNING *`,
      [ulid(at), store.shop_id, o.id, customer.id, p.reason, p.text, p.method, JSON.stringify(p.lines), p.key, p.hash, at])).rows[0];
    return { request: requestView(row, 0), replayed: false, orderNo: Number(o.order_no), orderId: o.id };
  });
  if (!out.replayed) await require('./order-return-requests-shop').notifyShop(store.shop_id, out.orderId, out.orderNo);
  return { request: out.request, replayed: out.replayed };
}

/** مشتری: یک عکس برای درخواستِ هنوز در انتظارِ خودش ⇒ `{photo, replayed}` */
async function addPhoto(store, customer, token, requestId, body) {
  const rid = String(requestId || '');
  if (!ID_RE.test(rid)) throw notFound('درخواست پیدا نشد', 'request_not_found');
  const p = require('./order-issues').parsePhoto(body);
  return imgStore.withStaged(imgStore.shopTenant(store.shop_id), p.data, (s) => db.tx(async (c) => {
    const run = (sql, args) => c.query(sql, args);
    const o = await orderOf(run, store, customer, token);
    const r = (await run('SELECT id, status FROM order_return_requests WHERE id=$1 AND order_id=$2 AND shop_id=$3 FOR UPDATE',
      [rid, o.id, store.shop_id])).rows[0];
    if (!r) throw notFound('درخواست پیدا نشد', 'request_not_found');
    const view = (x) => ({ id: x.id, mime: x.mime, size: Number(x.size), width: Number(x.width), height: Number(x.height), createdAt: Number(x.created_at) });
    const prev = (await run('SELECT * FROM order_return_request_photos WHERE request_id=$1 AND hash=$2', [rid, p.hash])).rows[0];
    if (prev) return { photo: view(prev), replayed: true };
    if (r.status !== 'pending') throw conflict('این درخواست دیگر عکسِ تازه نمی‌پذیرد', 'request_closed');
    const n = (await run('SELECT COUNT(*)::int AS n FROM order_return_request_photos WHERE request_id=$1', [rid])).rows[0].n;
    if (n >= MAX_PHOTOS) throw conflict(`حداکثر ${MAX_PHOTOS} عکس برای هر درخواست`, 'too_many_photos');
    const at = now();
    s.used = true;
    const row = (await run(
      `INSERT INTO order_return_request_photos (id, shop_id, request_id, mime, size, width, height, hash, data, store_key, created_at)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11) RETURNING id, mime, size, width, height, created_at`,
      [ulid(at), store.shop_id, rid, p.mime, p.data.length, p.width, p.height, p.hash, s.data, s.key, at])).rows[0];
    return { photo: view(row), replayed: false };
  }));
}

/** «سفارش‌های من»: درخواست‌های همین حساب در همین دکان (تازه‌ترین اول، ۲۰ تا) با لینکِ پیگیریِ هر سفارش */
async function forCustomer(store, customer) {
  const sf = require('./storefront');
  const rows = await db.many(
    `SELECT r.id, r.status, r.reason, r.lines, r.decision_note, r.created_at, r.decided_at, r.completed_at,
            o.id AS order_id, o.order_no, o.track_gen
       FROM order_return_requests r JOIN online_orders o ON o.id = r.order_id
      WHERE r.shop_id=$1 AND r.customer_id=$2 ORDER BY r.created_at DESC, r.id DESC LIMIT 20`, [store.shop_id, customer.id]);
  const on = await sf.linkPolicyOn(store.shop_id);
  return {
    requests: rows.map((r) => ({
      id: r.id, status: r.status, reason: r.reason, orderNo: Number(r.order_no),
      items: (Array.isArray(r.lines) ? r.lines : JSON.parse(r.lines || '[]')).length,
      decisionNote: r.decision_note || '', createdAt: Number(r.created_at), decidedAt: num(r.decided_at), completedAt: num(r.completed_at),
      token: sf.listTokenOf(store.shop_id, { id: r.order_id, track_gen: r.track_gen }, on),
    })),
  };
}

/** دکان: پرچمِ خاموش ⇒ ۴۰۳ ِ همان قابلیت */
async function needFlag(shopId) {
  if (!(await flagOn(shopId))) throw forbidden('درخواستِ برگشت از ویترین برای این دکان روشن نیست', 'return_requests_disabled');
}

module.exports = {
  FLAG, REASONS, METHODS, STATUSES, OPEN, ELIGIBLE, MAX_PER_ORDER, MAX_PHOTOS, TEXT, ID_RE,
  flagOn, needFlag, docIdOf, ownsOrder, policyOf, parseRules, blockedBy, parseRequest, requestView, stateOf,
  customerView, create, addPhoto, forCustomer,
};
