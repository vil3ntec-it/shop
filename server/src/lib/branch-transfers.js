'use strict';
/**
 * انتقالِ موجودی **میانِ شعبه‌های یک دکان** — INV-11 «انتقال بینِ انبار یا شعبه با در مسیر و قبول/رد در مقصد»، سناریوی S21؛
 * فازِ ۴ ِ `docs/BRANCHES-fa.md` (§۱۰)، پشتِ پرچم‌های خاموشِ `shop.branches` و `shop.catalog`؛ مهاجرتِ ۹۳۵.
 *
 *   ارسال (شعبهٔ الف) ── کالا همان لحظه از مبدأ ِ الف کم و در `in_transit` ِ **الف** دیده می‌شود (کالا تا پذیرفتن مالِ
 *                        فرستنده است؛ بی موجودیِ منفی — حالتِ `local` کمبود را ۴۰۹ `insufficient_stock` می‌دهد).
 *   دریافت (شعبهٔ ب) ─── رسیده از `in_transit` ِ الف به مکانِ مقصد در **ب** (ناقص در چند گام؛ قاعده‌ها: `branch-transfer-plan.js`).
 *   رد (شعبهٔ ب) ─────── ماندهٔ در مسیر به مبدأ ِ الف برمی‌گردد؛ آن‌چه رسیده در ب می‌ماند.
 *   پیش‌نویس (الف، ۹۴۶) ─ `draft: true` در ارسال ⇒ هیچ اثرِ موجودی؛ فقط الف می‌بیند، ویرایش (`PUT /:id`)، ارسال (`/:id/send`) یا لغو.
 *   لغو (الف، ۹۴۶) ────── پیش‌نویس ⇒ بی اثر؛ «در مسیر» ِ هنوز هیچ‌نرسیده ⇒ همه به مبدأ ِ الف (دلیل اجباری)؛ نیمه‌رسیده ⇒ نه.
 *   موجودیِ منفی ──────── فقط اگر صاحب در `shop_branch_settings.transfer_negative` اجازه داده: حرکتِ «ثبت‌شده» + وظیفهٔ
 *                        `negative_stock` (هرگز بی‌صدا)؛ وگرنه ۴۰۹ `insufficient_stock` و هیچ ردیف.
 *
 * ⛔ شعبهٔ کننده فقط از سرآیندِ سنجیدهٔ `X-Branch-Id` (`branches.restOf` در `routes/catalog.js`)، هرگز از بدنه یا پارامتر.
 *    ارسال ⇐ دسترسی به الف؛ دریافت/رد ⇐ دسترسی به ب و فقط اگر ب مقصدِ همین انتقال است (`transfer_wrong_branch`). صاحب هر
 *    شعبه را دارد ولی همیشه با سرآیندِ همان شعبه کار می‌کند. شعبهٔ مقصد در بدنه «هدف» است و با شعبه‌های فعالِ **همین دکان**
 *    سنجیده می‌شود (ناشناخته، بدشکل، بایگانی، دکانِ دیگر ⇒ یک پاسخ: `bad_destination`).
 * ⛔ جداییِ داده: هر شعبه فقط انتقال‌هایی را می‌بیند که یک سرش خودش است (بیرون ⇒ همان ۴۰۴ ِ ناموجود)؛ نما فقط همان
 *    چیزی است که انتقال با خودش دارد (نام و شناسهٔ دو سر، کالاها و عددهای همین انتقال) — نه موجودی و نه سندِ دیگرِ آن سو.
 * ⛔ یک تراکنش برای هر گام؛ قفلِ موجودیِ **هر دو بخش** به ترتیبِ ثابت (کلیدِ مرتب) ⇒ بی بن‌بست؛ ردیفِ انتقال `FOR UPDATE` و
 *    به‌روزرسانیِ نگهبان‌دار (`WHERE status = ANY(OPEN)`) + تریگرِ ۹۳۵ ⇒ دریافت و ردِ هم‌زمان فقط یک نتیجه.
 * ⛔ کلیدِ یکتایی برای هر گام (`Idempotency-Key`) در فضای شعبهٔ کننده: همان بدنه ⇒ همان نتیجه بی اثرِ دوم؛ بدنهٔ دیگر ⇒ ۴۰۹.
 * ⛔ هر گام با سابقه‌اش (`audit_logs`، `detail.branch` = شعبهٔ کننده) و ردیفِ گام (`branch_transfer_steps.branch_id`) در همان
 *    تراکنش؛ حرکت‌ها در دفترِ موجودیِ هر شعبه با `ref_type = 'branch_transfer'`.
 * ⛔ فقط از راهِ REST (برخط): گامِ بی‌اینترنتِ دستگاه (`auditLog` ِ `stock_transfer`) همان انتقالِ درونِ شعبهٔ اصلی است.
 */
const crypto = require('crypto');
const { now, tx, newId, query, one } = require('../db');
const stock = require('./stock');
const catalog = require('./catalog');
const branches = require('./branches');
const audit = require('./audit');
const P = require('./branch-transfer-plan');
const bset = require('./branch-settings');
const { stable } = require('./idempotency');
const { badRequest, notFound, conflict, forbidden } = require('../middleware/errors');

const KEY_RE = /^[A-Za-z0-9_.:-]{8,128}$/;
const MAX_LIST = 100;
const REF = 'branch_transfer';

const str = (v) => (typeof v === 'string' ? v.trim() : '');
const num = (v) => (v === null || v === undefined ? 0 : Number(v));
const hashOf = (body) => crypto.createHash('sha256').update(stable(body || {})).digest('hex').slice(0, 32);
const keyOf = (raw) => {
  const key = str(raw);
  if (!key) throw badRequest('کلیدِ یکتاییِ انتقال لازم است', 'idempotency_key_required');
  if (!KEY_RE.test(key)) throw badRequest('کلیدِ یکتایی نامعتبر است', 'bad_idempotency_key');
  return key;
};
const decode = (v) => (typeof v === 'string' ? JSON.parse(v) : v);

/** هر دو پرچم روشن؟ (خاموش ⇒ درها همان مسیرِ ناشناخته‌اند) */
async function enabled(shopId) {
  return (await branches.enabled(shopId)) && (await stock.flagOf(shopId));
}

/** نامِ شعبه‌ها (بایگانی هم: سندِ کهنه نامِ سرش را نشان می‌دهد) */
async function namesOf(shopId) {
  return new Map((await branches.list(shopId)).map((b) => [b.id, b.name]));
}
const end = (names, id) => ({ id, name: names.get(id) || '', main: id === branches.MAIN });

const nil = (v) => v === null || v === undefined;
/** فرستاده شد؟ ردیفِ پیش از ۹۴۶ (`sent_at` تهی) در دمِ ساختن فرستاده شد؛ پیش‌نویس و لغوِ پیش‌نویس نه */
const wasSent = (r) => !nil(r.sent_at) || ['in_transit', 'partial', 'received', 'rejected'].includes(r.status);

/** نمای انتقال از نگاهِ شعبهٔ `here` — فقط آن‌چه خودِ انتقال دارد */
function view(r, here, names) {
  const sent = wasSent(r);
  const lines = P.linesView(decode(r.lines), r.status, sent);
  return {
    id: r.id,
    direction: r.from_branch === here ? 'out' : 'in',
    fromBranch: end(names, r.from_branch),
    toBranch: end(names, r.to_branch),
    from: r.from_location, to: r.to_location, status: r.status, lines,
    shortMilli: lines.reduce((s, l) => s + l.shortMilli, 0),
    pendingMilli: lines.reduce((s, l) => s + l.pendingMilli, 0),
    note: r.note, reason: r.reason, createdBy: r.created_by, createdAt: num(r.created_at),
    sentBy: sent ? (r.sent_by || r.created_by) : '',
    sentAt: sent ? num(nil(r.sent_at) ? r.created_at : r.sent_at) : null,
    closedBy: r.closed_by, closedAt: nil(r.closed_at) ? null : num(r.closed_at),
  };
}

const stepView = (s, row) => ({
  kind: s.kind, side: s.branch_id === row.from_branch ? 'from' : 'to', statusFrom: s.status_from, statusTo: s.status_to,
  lines: decode(s.lines) || [], note: s.note, final: s.final, by: s.actor, at: num(s.created_at),
});

/** شعبه‌هایی که از `here` به آن‌ها می‌شود فرستاد: فعال، همین دکان، جز خودش */
async function destinations(shopId, here) {
  return (await branches.list(shopId)).filter((b) => b.status === 'active' && b.id !== here)
    .map((b) => ({ id: b.id, name: b.name, main: b.id === branches.MAIN }));
}

/** ادعای شعبهٔ مقصد ⇒ شناسهٔ شعبهٔ فعالِ همین دکان؛ هر چیزِ دیگر یک پاسخ */
async function destinationOf(shopId, here, claim) {
  const id = branches.idOf(claim);
  const bad = () => badRequest('شعبهٔ مقصد در این دکان نیست', 'bad_destination');
  if (id === null) throw bad();
  if (id === here) throw badRequest('مبدأ و مقصد یک شعبه‌اند؛ انتقالِ درونِ شعبه جداست', 'same_branch');
  if (!(await destinations(shopId, here)).some((b) => b.id === id)) throw bad();
  return id;
}

// ── گام‌ها ─────────────────────────────────────────────────────────────

async function stepOf(c, shopId, here, key) {
  return (await c.query('SELECT * FROM branch_transfer_steps WHERE shop_id=$1 AND branch_id=$2 AND step_key=$3', [shopId, here, key])).rows[0] || null;
}

async function recordStep(c, shopId, here, s, at) {
  await c.query(
    `INSERT INTO branch_transfer_steps
       (shop_id, branch_id, step_key, transfer_id, kind, body_hash, status_from, status_to, lines, note, final, actor, created_at)
     VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9::jsonb,$10,$11,$12,$13)`,
    [shopId, here, s.key, s.transferId, s.kind, s.hash, s.from, s.to, JSON.stringify(s.lines || []),
      (s.note || '').slice(0, 300), s.final !== false, String(s.actor || '').slice(0, 80), at]
  );
}

/** همان کلید در همین شعبه: همان بدنه ⇒ همان نتیجه؛ بدنهٔ دیگر ⇒ ۴۰۹ */
async function replayOf(c, shopId, here, st, hash) {
  if (st.body_hash !== hash) throw conflict('این کلیدِ یکتایی پیش‌تر برای گامِ دیگری به کار رفته است', 'idempotency_key_reused');
  const row = (await c.query('SELECT * FROM branch_transfers WHERE shop_id=$1 AND id=$2', [shopId, st.transfer_id])).rows[0];
  return { transfer: view(row, here, await namesOf(shopId)), replayed: true };
}

const base = (id, actor, at, note, mode = 'local') => ({
  mode, source: 'staff', refType: REF, refId: id, actorType: 'user', actorId: actor.id, note: note || '', at,
});

/**
 * حرکت‌ها و وظیفه‌های یک طرح در بخشِ هر سو. [negative] (فقط ارسال، با اجازهٔ صاحب) ⇒ حالتِ `recorded` ِ هسته: کمبود رد نمی‌شود
 * و وظیفهٔ `negative_stock` ِ همان شعبه باز می‌شود — هرگز بی‌صدا.
 */
async function post(c, keys, id, actor, at, note, p, negative = false) {
  const b = base(id, actor, at, note, negative ? 'recorded' : 'local');
  for (const { side, ...m } of p.moves) await stock.postMove(c, keys[side], { ...b, ...m }, at);
  for (const { side, ...t } of p.tasks || []) await stock.openTask(c, keys[side], t, at);
}

function auditEntry(shopId, here, actor, action, t, ip, extra = {}) {
  return {
    shopId, userId: actor.id, action, targetType: REF, targetId: t.id, ip,
    detail: {
      branch: here, side: here === t.fromBranch.id ? 'from' : 'to', fromBranch: t.fromBranch.id, toBranch: t.toBranch.id,
      status: t.status, lines: t.lines.map((l) => [l.productId, l.sentMilli, l.receivedMilli]), reason: t.reason, ...extra,
    },
  };
}

/** کالاهای ناشناس در کاتالوگِ دکان ⇒ ۴۰۴ با فهرستشان */
async function knownOrThrow(c, shopId, ids) {
  const known = new Set((await c.query(
    'SELECT product_id FROM catalog_products WHERE shop_id=$1 AND product_id = ANY($2::text[])', [shopId, ids]
  )).rows.map((r) => r.product_id));
  const missing = ids.filter((id) => !known.has(id));
  if (missing.length) {
    const err = notFound('کالا پیدا نشد', 'product_not_found');
    err.data = { productIds: missing };
    throw err;
  }
}

/** قفلِ موجودیِ بخش‌ها به ترتیبِ ثابت و خواندنِ دفترِ دستگاهِ هر کدام پیش از نوشتن */
async function begin(c, shopId, keys, at) {
  const settings = await stock.settingsOf(shopId, c);
  const sorted = [...new Set(keys)].sort();
  for (const k of sorted) await stock.lock(c, k);
  const ingested = [];
  for (const k of sorted) ingested.push([k, await catalog.ingestTx(c, k, at, settings)]);
  return { settings, ingested };
}

async function finish(ingested, after) {
  for (const [k, out] of ingested) await catalog.afterIngest(k, out);
  if (after) await after.afterCommit();
}

/**
 * ارسال (یا با `draft: true` پیش‌نویس) از شعبهٔ `here` ⇒ { transfer, replayed }. `places` = محدودهٔ مکانِ عضو (STF-06) یا null.
 * کالای ناشناس ⇒ ۴۰۴؛ کمبود ⇒ ۴۰۹ ِ هسته (مگر اجازهٔ صاحب) — هیچ ردیفی نمی‌ماند (یک تراکنش).
 */
async function send(shopId, here, { key, body, actor, places = null, ip = '' }) {
  const k = keyOf(key);
  const draft = !!(body && typeof body === 'object' && body.draft === true);
  const hash = hashOf({ step: 'send', body: body || {} });
  //  تکرارِ گامِ ثبت‌شده پیش از هر سنجشِ دیگر (مقصدِ بایگانی‌شده پس از ارسال هم همان پاسخ را پس می‌دهد)
  const early = await stepOf({ query }, shopId, here, k);
  if (early) return replayOf({ query }, shopId, here, early, hash);
  const req = P.parseSend(body);
  if (places && !places.includes(req.from)) throw forbidden('این مکانِ موجودی در محدودهٔ دسترسیِ شما نیست', 'location_out_of_scope');
  const toBranch = await destinationOf(shopId, here, req.toClaim);
  const A = branches.partitionOf(shopId, here);
  //  کاتالوگِ مشترک از شعبهٔ اصلی: کالای تازهٔ اصلی پیش از سنجشِ «کالا هست؟»
  if (here) await catalog.refresh(shopId, { maxAgeMs: 1, throttle: false });
  const negative = !draft && (await bset.get(shopId)).transferNegative;
  const at = now();
  let prep = null;
  let after = null;
  const out = await tx(async (c) => {
    prep = await begin(c, shopId, draft ? [] : [A], at);
    const st = await stepOf(c, shopId, here, k);
    if (st) return replayOf(c, shopId, here, st, hash);
    const ids = req.lines.map((l) => l.productId);
    await knownOrThrow(c, shopId, ids);
    const id = newId('bxf');
    if (!draft) await post(c, { from: A }, id, actor, at, req.note, { moves: P.sendMoves(id, req) }, negative);
    const row = (await c.query(
      `INSERT INTO branch_transfers (id, shop_id, from_branch, to_branch, from_location, to_location, status, lines, note, created_by, created_at,
         sent_by, sent_at)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8::jsonb,$9,$10,$11,$12,$13) RETURNING *`,
      [id, shopId, here, toBranch, req.from, req.to, draft ? 'draft' : 'in_transit',
        JSON.stringify(req.lines.map((l) => ({ ...l, receivedMilli: null }))), req.note, actor.id, at,
        draft ? '' : actor.id, draft ? null : at]
    )).rows[0];
    await recordStep(c, shopId, here, {
      key: k, transferId: id, kind: draft ? 'draft' : 'send', hash, from: '', to: draft ? 'draft' : 'in_transit', note: req.note, actor: actor.id,
      lines: req.lines.map((l) => ({ productId: l.productId, qtyMilli: l.sentMilli })),
    }, at);
    if (!draft) await stock.checkProducts(c, A, ids, prep.settings, at);
    const t = view(row, here, await namesOf(shopId));
    after = await audit.logIn(c, auditEntry(shopId, here, actor, draft ? 'stock.branch_transfer_draft' : 'stock.branch_transfer_send', t, ip,
      negative ? { negativeAllowed: true } : {}));
    return { transfer: t, replayed: false };
  });
  await finish(prep ? prep.ingested : [], after);
  return out;
}

/** ردیفِ دیدنی برای `here` (یک سرش) یا ۴۰۴ ِ یکسان */
async function visible(shopId, here, id) {
  const tid = str(id);
  const r = P.ID_RE.test(tid) ? await one('SELECT * FROM branch_transfers WHERE shop_id=$1 AND id=$2', [shopId, tid]) : null;
  //  پیش‌نویس مالِ فرستنده است: مقصد تا ارسال آن را نمی‌بیند (همان ۴۰۴ ِ ناموجود)
  if (!r || (r.from_branch !== here && (r.to_branch !== here || r.status === 'draft'))) throw notFound('انتقال پیدا نشد', 'transfer_not_found');
  return r;
}

/**
 * یک گام روی انتقالِ موجود در شعبهٔ `here`. `cfg`:
 *   side    'to' (دریافت/رد: فقط شعبهٔ مقصد) | 'from' (ارسالِ پیش‌نویس، ویرایش، لغو: فقط شعبهٔ فرستنده)
 *   from    وضعیت‌هایی که این گام از آن‌ها رواست
 *   place   مکانی از ردیف که محدودهٔ STF-06 ِ عضو باید داشته باشد (`to_location` | `from_location`)
 *   prepare (body) ⇒ (t, part, row) ⇒ plan — پیش از تراکنش (بدنهٔ بد ⇒ ۴۰۰ بی هیچ قفل)
 *   check   (async, c, row, plan) — سنجشِ تازه درونِ تراکنش (مقصدِ هنوز فعال، کالای هنوز در کاتالوگ)
 * plan: { kind, status, lines, reason, note, final, stepLines, moves, tasks, set?: {ستون: مقدار}, negative? }
 */
async function stepOn(shopId, here, id, { key, body, actor, places = null, ip = '' }, cfg) {
  const k = keyOf(key);
  const head = await visible(shopId, here, id);
  const hash = hashOf({ step: cfg.kind, id: head.id, body: body || {} });
  const early = await stepOf({ query }, shopId, here, k);
  if (early) return replayOf({ query }, shopId, here, early, hash);
  const mine = cfg.side === 'to' ? head.to_branch : head.from_branch;
  if (mine !== here) {
    throw forbidden(cfg.side === 'to' ? 'فقط شعبهٔ مقصد این انتقال را دریافت یا رد می‌کند' : 'فقط شعبهٔ فرستنده این انتقال را می‌فرستد، ویرایش یا لغو می‌کند',
      cfg.side === 'to' ? 'transfer_wrong_branch' : 'transfer_not_sender');
  }
  if (places && !places.includes(head[cfg.place])) throw forbidden('این مکانِ موجودی در محدودهٔ دسترسیِ شما نیست', 'location_out_of_scope');
  const planFn = cfg.prepare(body);
  const keys = { from: branches.partitionOf(shopId, head.from_branch), to: branches.partitionOf(shopId, head.to_branch) };
  //  دریافت/رد ⇐ قفلِ هر دو بخش؛ گامِ فرستنده ⇐ فقط بخشِ خودش (ویرایشِ پیش‌نویس هیچ موجودی‌ای را نمی‌خواند)
  const lockKeys = cfg.side === 'to' ? [keys.from, keys.to] : (cfg.kind === 'edit' ? [] : [keys.from]);
  if (cfg.kind === 'send' && head.from_branch) await catalog.refresh(shopId, { maxAgeMs: 1, throttle: false });
  const at = now();
  let prep = null;
  let after = null;
  const out = await tx(async (c) => {
    prep = await begin(c, shopId, lockKeys, at);
    const st = await stepOf(c, shopId, here, k);
    if (st) return replayOf(c, shopId, here, st, hash);
    const row = (await c.query('SELECT * FROM branch_transfers WHERE shop_id=$1 AND id=$2 FOR UPDATE', [shopId, head.id])).rows[0];
    if (!cfg.from.includes(row.status)) {
      if (row.status === 'partial' && cfg.kind === 'cancel') {
        throw conflict('بخشی از این انتقال رسیده است؛ فقط شعبهٔ مقصد دریافت یا رد می‌کند', 'transfer_partial');
      }
      if (P.LIVE.includes(row.status)) throw conflict('این گام در وضعیتِ کنونیِ این انتقال روا نیست', 'transfer_bad_state');
      throw conflict(row.status === 'received' ? 'این انتقال پیش‌تر دریافت شده است'
        : row.status === 'cancelled' ? 'این انتقال لغو شده است' : 'این انتقال پیش‌تر رد شده است', 'transfer_closed');
    }
    const names = await namesOf(shopId);
    const parts = Number((await c.query(
      `SELECT COUNT(*)::int AS n FROM branch_transfer_steps WHERE shop_id=$1 AND transfer_id=$2 AND kind='receive'`, [shopId, row.id]
    )).rows[0].n);
    const p = planFn(view(row, here, names), parts + 1, row);
    if (!P.allowed(row.status, p.status)) throw conflict('این گذار مجاز نیست', 'transfer_closed');
    if (cfg.check) await cfg.check(c, row, p);
    await post(c, keys, row.id, actor, at, row.note, p, !!p.negative);
    const closing = !P.LIVE.includes(p.status);
    const set = p.set || {};
    const cols = Object.keys(set);
    const extra = cols.map((col, i) => `, ${col}=$${i + 9}`).join('');
    const done = (await c.query(
      `UPDATE branch_transfers SET status=$3, lines=$4::jsonb, reason=$5, closed_by=$6, closed_at=$7${extra}
        WHERE shop_id=$1 AND id=$2 AND status = ANY($8::text[]) RETURNING *`,
      [shopId, row.id, p.status, JSON.stringify(p.lines), p.reason, closing ? actor.id : '', closing ? at : null, cfg.from.slice(),
        ...cols.map((col) => set[col])]
    )).rows[0];
    if (!done) throw conflict('این انتقال پیش‌تر بسته شده است', 'transfer_closed');
    await recordStep(c, shopId, here, {
      key: k, transferId: row.id, kind: p.kind, hash, from: row.status, to: p.status, note: p.note, final: p.final,
      actor: actor.id, lines: p.stepLines,
    }, at);
    if (p.moves.length) {
      const pids = p.lines.map((l) => l.productId);
      await stock.checkProducts(c, keys.from, pids, prep.settings, at);
      if (cfg.side === 'to') await stock.checkProducts(c, keys.to, pids, prep.settings, at);
    }
    const t = view(done, here, names);
    after = await audit.logIn(c, auditEntry(shopId, here, actor, `stock.branch_transfer_${p.kind}`, t, ip, p.negative ? { negativeAllowed: true } : {}));
    return { transfer: t, replayed: false };
  });
  await finish(prep ? prep.ingested : [], after);
  return out;
}

/** دریافت در مقصد: `lines: [{ productId, receivedMilli }]` (رسیده در همین گام)، `final` (پیش‌فرض true)، `reason` */
const receive = (shopId, here, id, opts) => stepOn(shopId, here, id, opts,
  { kind: 'receive', side: 'to', from: P.OPEN, place: 'to_location', prepare: P.receivePlan });
/** رد در مقصد: ماندهٔ در مسیر به مبدأ؛ دلیل اجباری */
const reject = (shopId, here, id, opts) => stepOn(shopId, here, id, opts,
  { kind: 'reject', side: 'to', from: P.OPEN, place: 'to_location', prepare: P.rejectPlan });
/** لغوِ فرستنده: پیش‌نویس (بی اثر) یا «در مسیر» ِ هنوز هیچ‌نرسیده (همه به مبدأ، دلیل اجباری) */
const cancel = (shopId, here, id, opts) => stepOn(shopId, here, id, opts,
  { kind: 'cancel', side: 'from', from: ['draft', 'in_transit'], place: 'from_location', prepare: P.cancelPlan });

/** ویرایشِ پیش‌نویس (فقط فرستنده): همان بدنهٔ ارسال (`toBranch`، مکان‌ها، `lines`، `note`) جایگزین می‌شود */
async function edit(shopId, here, id, opts) {
  //  دیدن و «فرستنده بودن» پیش از سنجشِ بدنه (شعبهٔ دیگر ۴۰۴/۴۰۳ می‌گیرد، نه خطای بدنه)
  const head = await visible(shopId, here, id);
  if (head.from_branch !== here) throw forbidden('فقط شعبهٔ فرستنده این انتقال را می‌فرستد، ویرایش یا لغو می‌کند', 'transfer_not_sender');
  const req = P.parseSend(opts.body);
  if (opts.places && !opts.places.includes(req.from)) throw forbidden('این مکانِ موجودی در محدودهٔ دسترسیِ شما نیست', 'location_out_of_scope');
  const toBranch = await destinationOf(shopId, here, req.toClaim);
  return stepOn(shopId, here, id, opts, {
    kind: 'edit', side: 'from', from: ['draft'], place: 'from_location',
    prepare: () => () => ({
      kind: 'edit', status: 'draft', reason: '', note: req.note, final: true, moves: [], tasks: [],
      lines: req.lines.map((l) => ({ ...l, receivedMilli: null })),
      stepLines: req.lines.map((l) => ({ productId: l.productId, qtyMilli: l.sentMilli })),
      set: { to_branch: toBranch, from_location: req.from, to_location: req.to, note: req.note },
    }),
    check: (c, row, p) => knownOrThrow(c, shopId, p.lines.map((l) => l.productId)),
  });
}

/** ارسالِ پیش‌نویس (فقط فرستنده): همان اثرِ ارسالِ مستقیم؛ مقصد (هنوز فعال؟) و کالاها دوباره سنجیده می‌شوند */
async function sendDraft(shopId, here, id, opts) {
  const negative = (await bset.get(shopId)).transferNegative;
  return stepOn(shopId, here, id, opts, {
    kind: 'send', side: 'from', from: ['draft'], place: 'from_location',
    prepare: () => (t, part, row) => {
      const lines = t.lines.map((l) => ({ productId: l.productId, sentMilli: l.sentMilli }));
      return {
        kind: 'send', status: 'in_transit', reason: '', note: row.note, final: true, tasks: [], negative,
        lines: lines.map((l) => ({ ...l, receivedMilli: null })),
        stepLines: lines.map((l) => ({ productId: l.productId, qtyMilli: l.sentMilli })),
        moves: P.sendMoves(row.id, { from: row.from_location, lines }),
        set: { sent_by: opts.actor.id, sent_at: now() },
      };
    },
    check: async (c, row) => {
      if (!(await destinations(shopId, here)).some((b) => b.id === row.to_branch)) {
        throw badRequest('شعبهٔ مقصد در این دکان نیست', 'bad_destination');
      }
      await knownOrThrow(c, shopId, decode(row.lines).map((l) => l.productId));
    },
  });
}

// ── خواندن ─────────────────────────────────────────────────────────────

/**
 * فهرستِ انتقال‌های همین شعبه، تازه‌ترین اول: `direction` = `in` (به این شعبه) | `out` (از این شعبه) | همه؛ `status`
 * اختیاری (`open` = در مسیر و نیمه‌رسیده). عضوِ محدود (STF-06) فقط انتقالی که سرِ همین شعبه‌اش در محدوده است.
 */
async function list(shopId, here, { direction, status, limit, places = null } = {}) {
  const lim = Math.min(Math.max(Number(limit) || 50, 1), MAX_LIST);
  const dir = str(direction);
  if (dir && dir !== 'in' && dir !== 'out') throw badRequest('جهت فقط in یا out', 'bad_direction');
  const st = str(status);
  if (st && st !== 'open' && !P.STATUSES.includes(st)) throw badRequest('وضعیتِ انتقال معتبر نیست', 'bad_status');
  const params = [shopId, here, lim];
  const outSide = places ? `(from_branch=$2 AND from_location = ANY($${params.push(places)}::text[]))` : 'from_branch=$2';
  //  پیش‌نویس فقط در فهرستِ فرستنده
  const inSide = places ? `(to_branch=$2 AND status <> 'draft' AND to_location = ANY($${params.length}::text[]))` : "(to_branch=$2 AND status <> 'draft')";
  const side = dir === 'out' ? outSide : dir === 'in' ? inSide : `(${outSide} OR ${inSide})`;
  const where = st ? ` AND status = ANY($${params.push(st === 'open' ? P.OPEN.slice() : [st])}::text[])` : '';
  const rows = (await query(
    `SELECT * FROM branch_transfers WHERE shop_id=$1 AND ${side}${where} ORDER BY created_at DESC, id DESC LIMIT $3`, params
  )).rows;
  const names = await namesOf(shopId);
  return rows.map((r) => view(r, here, names));
}

/** یک انتقال با گام‌هایش — فقط برای دو سرِ آن */
async function get(shopId, here, id) {
  const r = await visible(shopId, here, id);
  const steps = (await query(
    'SELECT * FROM branch_transfer_steps WHERE shop_id=$1 AND transfer_id=$2 ORDER BY created_at, step_key', [shopId, r.id]
  )).rows;
  return { transfer: view(r, here, await namesOf(shopId)), steps: steps.map((s) => stepView(s, r)) };
}

module.exports = { REF, enabled, destinations, send, receive, reject, cancel, edit, sendDraft, list, get, view };
