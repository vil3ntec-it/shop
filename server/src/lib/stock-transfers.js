'use strict';
/**
 * انتقالِ موجودی با «در مسیر»، دریافتِ چندباره و قبول یا رد در مقصد — INV-11، سناریوی S21 (v3 §۶.۵).
 *
 *   ارسال ─── کالا همان لحظه از مبدأ کم و در `in_transit` دیده می‌شود (دو حرکتِ `transfer_out`/
 *             `transfer_in`، بی موجودیِ منفی: حالتِ `local` کمبود را رد می‌کند).
 *   دریافت ── مقصد مقدارِ رسیده را می‌گوید: رسیده از `in_transit` به مقصد می‌رود.
 *             • `final: false` (دریافتِ ناقص، مهاجرتِ ۲۶۵): فقط همین رسیده‌ها؛ بقیه در `in_transit` می‌ماند و
 *               وضعیت `partial` می‌شود. کالایی که نیامده یعنی «در این گام چیزی نرسید». همه رسید ⇒ `received`.
 *             • `final` (پیش‌فرض، همان رفتارِ پیشین): کالایی که نیامده یعنی «بقیه‌اش رسید»؛ **کسری هرگز
 *               بی‌صدا گم نمی‌شود**: دلیلش اجباری است، یک حرکتِ `adjustment` ِ منفی با همان دلیل در
 *               `in_transit` می‌نشیند و یک «وظیفهٔ بررسی» (`transfer_short`) برای مدیر باز می‌شود.
 *             بیش از ماندهٔ «در مسیر» پذیرفته نمی‌شود (کالای اضافه حرکتِ جدای خودش را دارد).
 *   رد ─────── ماندهٔ `in_transit` به مبدأ برمی‌گردد، با دلیل (آنچه پیش‌تر رسیده، رسیده می‌ماند).
 *
 * ⛔ همان قاعده‌های `catalog.manualMove`: پشتِ پرچمِ `shop.catalog`، دکان فقط از عضویتِ توکن، دفترِ
 *    دستگاه پیش از نوشتن خوانده می‌شود، همه زیرِ قفلِ موجودیِ دکان و در یک تراکنش.
 * ⛔ کلیدِ یکتایی برای هر گام (`stock_transfer_steps`): تکرارِ همان درخواست ⇒ همان نتیجه، بی اثرِ دوم؛
 *    همان کلید با بدنهٔ دیگر ⇒ ۴۰۹. گذارِ وضعیت فقط از `in_transit`/`partial`؛ انتقالِ بسته دوباره بسته نمی‌شود.
 * ⛔ هر حرکت کلیدِ `xfer:<شناسه>:<گام>:<ردیف>` دارد (UNIQUE در `stock_moves`)، پس حتی تکرارِ ناخواسته
 *    هم دو بار کم نمی‌کند. شعبه هنوز مدل ندارد (§۳۸.۳)؛ مبدأ و مقصد مکان‌های همین دکان‌اند.
 *
 * ── بی‌اینترنت (دفترِ دستگاه، [ingestDevice]) ──────────────────────────────────────────────
 * برنامه هر گام را یک ردیفِ `auditLog` با `type = stock_transfer` و شیءِ `xfer` می‌نویسد؛ همان صفِ همگام‌سازی
 * (v1 یا v3) آن را می‌برد و `catalog.ingestTx` هنگامِ خواندنِ دفترِ دستگاه اینجا می‌دهد. کلیدِ گام = شناسهٔ همان
 * ردیف ⇒ فرستادنِ دوباره پس از قطعِ شبکه، خواندنِ کاملِ دوبارهٔ دفتر یا دو دستگاه هیچ‌کدام دو بار نمی‌شمارند.
 * سرور داور است: گامی که نمی‌پذیرد (کمبود، کالای ناشناس، انتقالِ بسته، …) **ثبت می‌شود** — نتیجه با کد
 * (`GET /transfers?keys=`) و وظیفهٔ `transfer_refused` برای مدیر؛ هیچ حرکتی از آن نمی‌ماند (SAVEPOINT).
 * دکان همان حسابِ همگام‌سازی است ⇒ ردیفِ دکانِ دیگر هرگز به انتقالِ این دکان نمی‌رسد.
 */
const crypto = require('crypto');
const { now, tx, newId } = require('../db');
const stock = require('./stock');
const catalog = require('./catalog');
const { stable } = require('./idempotency');
const { ApiError, badRequest, notFound, conflict, forbidden } = require('../middleware/errors');

const ENDPOINTS = Object.freeze(['shop_floor', 'warehouse']);
const STATUSES = Object.freeze(['in_transit', 'partial', 'received', 'rejected']);
/** وضعیت‌هایی که هنوز چیزی «در مسیر» دارند */
const OPEN = Object.freeze(['in_transit', 'partial']);
const MAX_LINES = 100;
const MAX_QTY_MILLI = 1_000_000_000;   // همان سقفِ حرکتِ دستی: یک میلیون واحد
const KEY_RE = /^[A-Za-z0-9_.:-]{8,128}$/;
const MAX_LIST = 100;
const MAX_KEYS = 50;
/** نوعِ ردیفِ `auditLog` ِ دستگاه */
const DEVICE_TYPE = 'stock_transfer';
const STEPS = Object.freeze(['send', 'receive', 'reject']);

const str = (v) => (typeof v === 'string' ? v.trim() : '');
const num = (v) => (v === null || v === undefined ? 0 : Number(v));
const isObj = (v) => v !== null && typeof v === 'object' && !Array.isArray(v);
function text(v, max) {
  const { stripControl } = require('./validate');
  return stripControl(str(v)).slice(0, max);
}
/** S117 — یادداشت و دلیلِ آزادِ انتقال بی شمارهٔ کاملِ کارت (`ops-errors.maskCards`)، پیش از دیتابیس و هشِ تکرار. */
const noteText = (v, max) => require('./ops-errors').maskCards(text(v, max));
const hashOf = (body) => crypto.createHash('sha256').update(stable(body || {})).digest('hex').slice(0, 32);
function decode(v) {
  if (v === null || v === undefined) return null;
  if (typeof v !== 'string') return v;
  try { return JSON.parse(v); } catch { return null; }
}

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
  const b = isObj(body) ? body : {};
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
  return { from, to, lines, note: noteText(b.note, 300) };
}

/** درخواستِ دریافت ⇒ { given: Map<pid, milli>, reason, final } */
function parseReceive(body) {
  const b = isObj(body) ? body : {};
  if (b.final !== undefined && typeof b.final !== 'boolean') throw badRequest('final باید true یا false باشد', 'bad_final');
  const given = new Map();
  const raw = Array.isArray(b.lines) ? b.lines : [];
  if (raw.length > MAX_LINES) throw badRequest(`حداکثر ${MAX_LINES} کالا در یک انتقال`, 'too_many_lines');
  for (const l of raw) {
    const pid = str(l && l.productId);
    if (!pid) throw badRequest('کالا لازم است', 'product_required');
    if (given.has(pid)) throw badRequest('هر کالا یک بار می‌آید', 'duplicate_product');
    given.set(pid, qtyOf(l.receivedMilli, { zero: true }));
  }
  return { given, reason: noteText(b.reason, 120), final: b.final !== false };
}

const received = (l) => (l.receivedMilli === null || l.receivedMilli === undefined ? 0 : num(l.receivedMilli));

function view(r) {
  const open = OPEN.includes(r.status);
  const lines = (Array.isArray(r.lines) ? r.lines : JSON.parse(r.lines || '[]')).map((l) => {
    const got = l.receivedMilli === null || l.receivedMilli === undefined ? null : num(l.receivedMilli);
    return {
      productId: l.productId, sentMilli: num(l.sentMilli), receivedMilli: got,
      //  کسری فقط پس از بستن معنا دارد؛ تا باز است همان «هنوز در مسیر» (`pendingMilli`)
      shortMilli: got === null || open ? 0 : num(l.sentMilli) - got,
      pendingMilli: open ? num(l.sentMilli) - (got || 0) : 0,
    };
  });
  return {
    id: r.id, from: r.from_location, to: r.to_location, status: r.status, lines,
    shortMilli: lines.reduce((s, l) => s + l.shortMilli, 0),
    pendingMilli: lines.reduce((s, l) => s + l.pendingMilli, 0),
    note: r.note, reason: r.reason, sendKey: r.send_key, createdBy: r.created_by, createdAt: num(r.created_at),
    closedBy: r.closed_by, closedAt: r.closed_at === null || r.closed_at === undefined ? null : num(r.closed_at),
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

// ── گام‌ها ─────────────────────────────────────────────────────────────

async function stepOf(c, shopId, key) {
  return (await c.query('SELECT * FROM stock_transfer_steps WHERE shop_id=$1 AND step_key=$2', [shopId, key])).rows[0] || null;
}

async function recordStep(c, shopId, s, at) {
  await c.query(
    `INSERT INTO stock_transfer_steps
       (shop_id, step_key, transfer_id, kind, body_hash, outcome, code, message, note, lines, final, origin, actor, created_at)
     VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10::jsonb,$11,$12,$13,$14)`,
    [shopId, s.key, s.transferId || '', s.kind, s.hash, s.outcome, s.code || '', (s.message || '').slice(0, 300),
      (s.note || '').slice(0, 300), JSON.stringify(s.lines || []), s.final !== false, s.origin, (s.actor || '').slice(0, 80), at]
  );
}

function stepView(s) {
  return {
    key: s.step_key, kind: s.kind, outcome: s.outcome, code: s.code, message: s.message, note: s.note,
    transferId: s.transfer_id, final: s.final, origin: s.origin, lines: decode(s.lines) || [], at: num(s.created_at),
  };
}

/** گامِ پیش‌تر ثبت‌شده با همین کلید: همان بدنه ⇒ همان نتیجه؛ بدنهٔ دیگر ⇒ ۴۰۹ */
async function replayOf(c, shopId, st, hash) {
  if (st.body_hash !== hash) throw conflict('این کلیدِ یکتایی پیش‌تر برای گامِ دیگری به کار رفته است', 'idempotency_key_reused');
  if (st.outcome !== 'applied') throw new ApiError(409, st.code || 'step_refused', st.message || 'این گام پیش‌تر رد شده است');
  const row = (await c.query('SELECT * FROM stock_transfers WHERE shop_id=$1 AND id=$2', [shopId, st.transfer_id])).rows[0];
  return { transfer: view(row), replayed: true };
}

/**
 * STF-06 برای گامِ دستگاه (`transfer-step-scope.js`، مهاجرتِ ۲۸۰): `places` = محدودهٔ عضوی که ردیف را push کرد، یا null.
 * همان قاعدهٔ REST ِ `routes/catalog.js`: ارسال از مبدأِ محدوده، دریافت و رد در مقصدِ محدوده.
 */
function inScope(places, loc) {
  if (places && !places.includes(loc)) throw forbidden('این مکانِ موجودی در محدودهٔ دسترسیِ فرستندهٔ گام نیست', 'location_out_of_scope');
}

/** ارسال درونِ تراکنشِ گیرنده (قفل و دفترِ دستگاه پیش‌تر خوانده شده) */
async function sendIn(c, shopId, { key, req, hash, actor, origin, by = '', places = null }, settings, at) {
  const st = await stepOf(c, shopId, key);
  if (st) return replayOf(c, shopId, st, hash);
  inScope(places, req.from);
  //  انتقالِ پیش از ۲۶۵ گامی ندارد؛ کلیدش همان `send_key` است
  const prev = (await c.query('SELECT * FROM stock_transfers WHERE shop_id=$1 AND send_key=$2', [shopId, key])).rows[0];
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
    [id, shopId, req.from, req.to, JSON.stringify(req.lines.map((l) => ({ ...l, receivedMilli: null }))), req.note, key, hash, actor.id, at]
  )).rows[0];
  await recordStep(c, shopId, {
    key, transferId: id, kind: 'send', hash, outcome: 'applied', note: req.note, final: true, origin, actor: by || actor.id,
    lines: req.lines.map((l) => ({ productId: l.productId, qtyMilli: l.sentMilli })),
  }, at);
  await stock.checkProducts(c, shopId, ids, settings, at);
  return { transfer: view(row), replayed: false };
}

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
    return sendIn(c, shopId, { key: k, req, hash, actor, origin: 'api' }, prep.settings, at);
  });
  await catalog.afterIngest(shopId, ingested);
  return out;
}

/**
 * بستن یا پیش بردنِ انتقال (دریافت یا رد) درونِ تراکنشِ گیرنده. `ref` = { id } یا { sendKey } (گامِ دستگاه که شناسهٔ
 * سرور را هنوز نمی‌داند). `plan(view, part)` ⇒ { kind, status, lines, reason, moves, tasks, stepLines, final, note }
 */
async function closeIn(c, shopId, ref, { key, hash, actor, origin, by = '', places = null }, settings, at, plan) {
  const st = await stepOf(c, shopId, key);
  if (st) return replayOf(c, shopId, st, hash);
  const id = str(ref.id);
  const sendKey = str(ref.sendKey);
  const row = (await c.query(
    `SELECT * FROM stock_transfers WHERE shop_id=$1 AND ${id ? 'id=$2' : 'send_key=$2'} FOR UPDATE`, [shopId, id || sendKey]
  )).rows[0];
  if (!row || (!id && !sendKey)) throw notFound('انتقال پیدا نشد', 'transfer_not_found');
  inScope(places, row.to_location);   //  دریافت و رد در مقصد
  if (!OPEN.includes(row.status)) {
    if (row.close_key === key && row.close_hash === hash) return { transfer: view(row), replayed: true };
    throw conflict(row.status === 'received' ? 'این انتقال پیش‌تر دریافت شده است' : 'این انتقال پیش‌تر رد شده است', 'transfer_closed');
  }
  const parts = Number((await c.query(
    `SELECT COUNT(*)::int AS n FROM stock_transfer_steps
      WHERE shop_id=$1 AND transfer_id=$2 AND kind='receive' AND outcome='applied'`, [shopId, row.id]
  )).rows[0].n);
  const p = plan(view(row), parts + 1);
  const b = base(row.id, actor, at, row.note);
  for (const m of p.moves) await stock.postMove(c, shopId, { ...b, ...m }, at);
  for (const t of p.tasks) await stock.openTask(c, shopId, t, at);
  const closing = !OPEN.includes(p.status);
  const done = (await c.query(
    `UPDATE stock_transfers SET status=$3, lines=$4::jsonb, reason=$5, close_key=$6, close_hash=$7, closed_by=$8, closed_at=$9
      WHERE shop_id=$1 AND id=$2 RETURNING *`,
    [shopId, row.id, p.status, JSON.stringify(p.lines), p.reason,
      closing ? key : '', closing ? hash : '', closing ? actor.id : '', closing ? at : null]
  )).rows[0];
  await recordStep(c, shopId, {
    key, transferId: row.id, kind: p.kind, hash, outcome: 'applied', note: p.note, final: p.final, origin, actor: by || actor.id,
    lines: p.stepLines,
  }, at);
  await stock.checkProducts(c, shopId, p.lines.map((l) => l.productId), settings, at);
  return { transfer: view(done), replayed: false };
}

async function closeApi(shopId, id, { key, body, actor }, plan) {
  const k = keyOf(key);
  const hash = hashOf({ id, ...(isObj(body) ? body : {}) });
  const at = now();
  let ingested = null;
  const out = await tx(async (c) => {
    const prep = await begin(c, shopId, at);
    ingested = prep.ingested;
    return closeIn(c, shopId, { id: String(id || '') || '-' }, { key: k, hash, actor, origin: 'api' }, prep.settings, at, plan);
  });
  await catalog.afterIngest(shopId, ingested);
  return out;
}

/**
 * طرحِ دریافت: هر عدد = رسیده در **همین گام** (برای انتقالِ تازه همان «رسیدهٔ کل» ِ پیشین).
 * `final` ⇒ کالایی که نیامده = همهٔ ماندهٔ «در مسیر»ش رسید؛ کسری ⇒ دلیلِ اجباری، حرکتِ `adjustment` ِ منفی در
 * `in_transit` و وظیفهٔ `transfer_short`. ناقص ⇒ کالایی که نیامده = هیچ؛ بقیه در مسیر می‌ماند.
 */
function receivePlan({ given, reason, final }) {
  return (t, part) => {
    const unknown = [...given.keys()].filter((pid) => !t.lines.some((l) => l.productId === pid));
    if (unknown.length) {
      const err = badRequest('این کالا در انتقال نیست', 'product_not_in_transfer');
      err.data = { productIds: unknown };
      throw err;
    }
    const step = t.lines.map((l) => {
      const before = received(l);
      const left = l.sentMilli - before;
      const got = given.has(l.productId) ? given.get(l.productId) : (final ? left : 0);
      if (got > left) {
        const err = badRequest('بیش از ماندهٔ «در مسیر» نمی‌رسد؛ اضافه را جدا ثبت کنید', 'over_received');
        err.data = { productId: l.productId, sentMilli: l.sentMilli, receivedMilli: before + got, pendingMilli: left };
        throw err;
      }
      return { productId: l.productId, sentMilli: l.sentMilli, before, got, after: before + got };
    });
    if (!final && step.every((s) => s.got === 0)) throw badRequest('در این گام چیزی نرسیده است', 'nothing_received');
    const whole = step.every((s) => s.after === s.sentMilli);
    const closing = final || whole;
    const short = closing ? step.filter((s) => s.after < s.sentMilli) : [];
    if (short.length && !reason) throw badRequest('برای کسری دلیل لازم است', 'reason_required');
    const moves = [];
    const tasks = [];
    //  گامِ ناقص کلیدِ «part<n>» دارد؛ بستن همان کلیدهای پیشین (یک بار برای هر انتقال)
    const tag = final ? 'recv' : `part${part}`;
    step.forEach((s, i) => {
      const n = i + 1;
      if (s.got > 0) {
        moves.push({ productId: s.productId, location: 'in_transit', type: 'transfer_out', qtyMilli: s.got, reason: 'transfer_receive', sourceKey: `xfer:${t.id}:${tag}-out:${n}` });
        moves.push({ productId: s.productId, location: t.to, type: 'transfer_in', qtyMilli: s.got, reason: 'transfer_receive', sourceKey: `xfer:${t.id}:${tag}-in:${n}` });
      }
      const gap = closing ? s.sentMilli - s.after : 0;
      if (gap > 0) {
        moves.push({ productId: s.productId, location: 'in_transit', type: 'adjustment', qtyMilli: -gap, reason: `کسریِ انتقال: ${reason}`.slice(0, 80), sourceKey: `xfer:${t.id}:short:${n}` });
        tasks.push({
          kind: 'transfer_short', productId: s.productId, location: 'in_transit', dedupe: `xfer-short:${t.id}:${s.productId}`,
          detail: { transferId: t.id, from: t.from, to: t.to, sentMilli: s.sentMilli, receivedMilli: s.after, shortMilli: gap, reason },
        });
      }
    });
    return {
      kind: 'receive',
      status: closing ? 'received' : 'partial',
      //  بازِ ناقص: رسیدهٔ تا اینجا؛ بسته: رسیدهٔ کل (همان شکلِ پیشین)
      lines: step.map((s) => ({ productId: s.productId, sentMilli: s.sentMilli, receivedMilli: s.after })),
      reason: short.length ? reason : '',
      note: reason,
      final: closing,
      stepLines: step.filter((s) => s.got > 0).map((s) => ({ productId: s.productId, qtyMilli: s.got })),
      moves,
      tasks,
    };
  };
}

/** طرحِ رد: ماندهٔ «در مسیر» از `in_transit` به مبدأ برمی‌گردد؛ دلیل اجباری */
function rejectPlan(reason) {
  if (!reason) throw badRequest('دلیلِ رد لازم است', 'reason_required');
  return (t) => {
    const back = t.lines.map((l) => ({ productId: l.productId, sentMilli: l.sentMilli, got: received(l), left: l.sentMilli - received(l) }));
    return {
      kind: 'reject',
      status: 'rejected',
      lines: back.map((l) => ({ productId: l.productId, sentMilli: l.sentMilli, receivedMilli: l.got })),
      reason,
      note: reason,
      final: true,
      stepLines: back.filter((l) => l.left > 0).map((l) => ({ productId: l.productId, qtyMilli: l.left })),
      moves: back.flatMap((l, i) => (l.left > 0 ? [
        { productId: l.productId, location: 'in_transit', type: 'transfer_out', qtyMilli: l.left, reason: 'transfer_reject', sourceKey: `xfer:${t.id}:back-out:${i + 1}` },
        { productId: l.productId, location: t.from, type: 'transfer_in', qtyMilli: l.left, reason: 'transfer_reject', sourceKey: `xfer:${t.id}:back-in:${i + 1}` },
      ] : [])),
      tasks: [],
    };
  };
}

/**
 * دریافت در مقصد: `lines: [{ productId, receivedMilli }]` (رسیده در همین گام)، `final` (پیش‌فرض true)، `reason`.
 */
function receive(shopId, id, { key, body, actor }) {
  const r = parseReceive(body);
  return closeApi(shopId, id, { key, body, actor }, receivePlan(r));
}

/** رد در مقصد: ماندهٔ در مسیر به مبدأ برمی‌گردد؛ دلیل اجباری. */
function reject(shopId, id, { key, body, actor }) {
  const plan = rejectPlan(noteText(body && body.reason, 120));
  return closeApi(shopId, id, { key, body, actor }, plan);
}

// ── دفترِ دستگاه ───────────────────────────────────────────────────────

/** ردیفِ `auditLog` ِ گامِ انتقال؟ (فیلترِ همان در `catalog.ingestTx`: `data->>'type'`) */
const isDeviceRow = (r) => r.table_name === 'auditLog' && (decode(r.data) || {}).type === DEVICE_TYPE;

/** شکلِ گامِ دستگاه ⇒ { kind, run(c), lines, note, ref } — پرتابِ ApiError یعنی «رد» */
function deviceStep(x) {
  const step = STEPS.includes(x.step) ? x.step : 'other';
  const lines = (Array.isArray(x.lines) ? x.lines : []).slice(0, MAX_LINES)
    .map((l) => ({ productId: str(l && l.productId).slice(0, 128), qtyMilli: Number.isSafeInteger(l && l.qtyMilli) ? l.qtyMilli : 0 }));
  const ref = { id: str(x.transferId).slice(0, 128), sendKey: str(x.sendKey).slice(0, 128) };
  return { step, lines, ref, note: noteText(x.step === 'send' ? x.note : x.reason, 300) };
}

/**
 * گام‌های انتقالِ دفترِ دستگاه، به ترتیبِ `updated_seq` (`catalog.ingestTx`، همان تراکنش و قفل).
 * هر گام یک بار: کلید = شناسهٔ ردیف. ⛔ خطای پایگاه (نه ApiError) پرتاب می‌شود ⇒ کلِ خواندن برمی‌گردد و
 * دفعهٔ بعد دوباره؛ ردِ قاعده ثبت می‌شود و خواندن ادامه می‌یابد (یک گامِ بد صف را نمی‌بندد).
 */
async function ingestDevice(c, shopId, rows, settings, at) {
  let applied = 0;
  let refused = 0;
  for (const r of rows) {
    if (r.deleted_at !== null && r.deleted_at !== undefined) continue;
    const d = decode(r.data) || {};
    if (d.type !== DEVICE_TYPE) continue;
    const key = str(r.row_id).slice(0, 128);
    if (!key || await stepOf(c, shopId, key)) continue;
    const x = isObj(d.xfer) ? d.xfer : {};
    const hash = hashOf(x);
    const s = deviceStep(x);
    const actor = { id: 'device' };
    const by = text(d.by, 80);
    const places = await require('./transfer-step-scope').placesFor(c, shopId, key);   //  STF-06: فقط عضوِ محدود
    const opts = { key, hash, actor, origin: 'device', by, places };
    await c.query('SAVEPOINT xfer_device');
    try {
      if (s.step === 'send') {
        await sendIn(c, shopId, { ...opts, req: parseSend({ from: x.from, to: x.to, lines: x.lines, note: x.note }) }, settings, at);
      } else if (s.step === 'receive') {
        const lines = (Array.isArray(x.lines) ? x.lines : []).map((l) => ({ productId: l && l.productId, receivedMilli: l && l.qtyMilli }));
        await closeIn(c, shopId, s.ref, opts, settings, at, receivePlan(parseReceive({ lines, reason: x.reason, final: x.final })));
      } else if (s.step === 'reject') {
        await closeIn(c, shopId, s.ref, opts, settings, at, rejectPlan(text(x.reason, 120)));
      } else {
        throw badRequest('گامِ انتقال ناشناخته است', 'bad_step');
      }
      await c.query('RELEASE SAVEPOINT xfer_device');
      applied += 1;
    } catch (e) {
      if (!(e instanceof ApiError)) throw e;
      await c.query('ROLLBACK TO SAVEPOINT xfer_device');
      await c.query('RELEASE SAVEPOINT xfer_device');
      const transferId = s.ref.id || (s.ref.sendKey
        ? ((await c.query('SELECT id FROM stock_transfers WHERE shop_id=$1 AND send_key=$2', [shopId, s.ref.sendKey])).rows[0] || {}).id || ''
        : '');
      await recordStep(c, shopId, {
        key, transferId, kind: s.step, hash, outcome: 'refused', code: e.code, message: e.message, note: s.note,
        lines: s.lines, final: s.step !== 'receive' || x.final !== false, origin: 'device', actor: by,
      }, at);
      await stock.openTask(c, shopId, {
        kind: 'transfer_refused', productId: (s.lines[0] && s.lines[0].productId) || '',
        location: s.step === 'send' && ENDPOINTS.includes(x.from) ? x.from : '',
        dedupe: `xfer-refused:${key}`,
        detail: { stepKey: key, step: s.step, code: e.code, message: e.message, transferId, lines: s.lines, by },
      }, at);
      refused += 1;
    }
  }
  return { applied, refused };
}

// ── خواندن ─────────────────────────────────────────────────────────────

/** فهرست: تازه‌ترین اول؛ `status` اختیاری (`in_transit` ⇒ «در مسیر»، `open` ⇒ در مسیر و نیمه‌رسیده). */
async function list(shopId, { status, limit, places = null } = {}) {
  const lim = Math.min(Math.max(Number(limit) || 50, 1), MAX_LIST);
  const st = str(status);
  if (st && st !== 'open' && !STATUSES.includes(st)) throw badRequest('وضعیتِ انتقال معتبر نیست', 'bad_status');
  const { query } = require('../db');
  const params = [shopId, lim];
  const where = st ? ` AND status = ANY($${params.push(st === 'open' ? OPEN.slice() : [st])}::text[])` : '';
  //  STF-06 (`staff-scope.js`): عضوِ محدود فقط انتقالی را می‌بیند که یک سرش در محدودهٔ اوست
  const mine = places ? ` AND (from_location = ANY($${params.push(places)}::text[]) OR to_location = ANY($${params.length}::text[]))` : '';
  const rows = (await query(
    `SELECT * FROM stock_transfers WHERE shop_id=$1${where}${mine} ORDER BY created_at DESC, id DESC LIMIT $2`,
    params
  )).rows;
  return rows.map(view);
}

/** نتیجهٔ گام‌ها با کلید، به ترتیبِ پرسش (برنامه: «در صف» ⇒ ثبت شد / رد شد). کلیدِ ناشناخته یعنی هنوز نرسیده. */
async function steps(shopId, keys) {
  const ks = [...new Set((Array.isArray(keys) ? keys : []).map(str).filter(Boolean))].slice(0, MAX_KEYS);
  if (!ks.length) return [];
  const { query } = require('../db');
  const rows = (await query('SELECT * FROM stock_transfer_steps WHERE shop_id=$1 AND step_key = ANY($2::text[])', [shopId, ks])).rows;
  //  به ترتیبِ همان کلیدهای پرسیده
  const by = new Map(rows.map((r) => [r.step_key, r]));
  return ks.filter((k) => by.has(k)).map((k) => stepView(by.get(k)));
}

module.exports = {
  ENDPOINTS, STATUSES, OPEN, MAX_LINES, MAX_KEYS, DEVICE_TYPE,
  parseSend, parseReceive, deviceStep, send, receive, reject, list, steps, ingestDevice, isDeviceRow,
};
