'use strict';
/**
 * اجرای سقف‌ها و «نیازمند تأیید» برای opهای همگام‌سازی — STF-02، STF-03
 * (بخشِ ۱۰.۱، سناریوهای S07 و S08).
 *
 * ── چه چیزی سنجیده می‌شود ────────────────────────────────────────────
 *   sales.discount   درجِ فاکتور با تخفیف (درصد و مبلغ)
 *   sales.cancel     لغوِ فاکتور
 *   returns.create   درجِ برگشت/بازپرداخت (مبلغ)
 * تصمیم با همان هستهٔ مشترک (`perm-core.js`) و دو لایهٔ `staff-perms.js` است:
 *   allow    ⇒ می‌نشیند (همان امروز)
 *   deny     ⇒ رد (همان کدهای امروز)
 *   approve  ⇒ فقط با «برگهٔ امضاشده»ی معتبر؛ وگرنه رد
 * opِ ردشده **هرگز بی‌صدا نمی‌نشیند**: دستگاه دلیل را می‌گیرد و یک ردیف در
 * صندوقِ تعارض (`sync_v3_conflicts`) برای صاحب دکان باز می‌شود.
 *
 * ── برگه ─────────────────────────────────────────────────────────────
 * شکل و متنِ امضاشده در `approval-core.js` است (مشترک با وب و Kotlin). این‌جا:
 *   • کلیدِ عمومیِ تأییدکننده از `shop_approver_keys` (ES256، SPKI)؛
 *   • تأییدکننده هنوز عضوِ فعالِ همین دکان است و **خودش** همان کار را با
 *     همان مقدار «مجاز» دارد — مدیرِ با سقفِ ۱۰٪ تأییدِ ۲۰٪ نمی‌دهد؛
 *   • کسی خودش را تأیید نمی‌کند؛
 *   • هر nonce یک بار: تکرارِ همان op (همان op_id) «تکرار» است، opِ دیگر
 *     «بازپخش» و رد می‌شود.
 *
 * ── دو راه ───────────────────────────────────────────────────────────
 *   v3/v2 (`sync-v3.js`): برگه در `op.approval` یا `fields.approval`؛ مصرفِ
 *     nonce داخلِ همان savepoint است، پس اگر واحد رد شود برگه مصرف نشده.
 *   v1 (`v1Guard`، میان‌افزارِ فقط‌دکانِ `routes/sync-v1.js`): `sync-v1.js`
 *     (مشترک با پمپ) دست نمی‌خورد؛ opِ ناروا پیش از آن از دسته بیرون می‌آید و
 *     پاسخش «rejected» با همان دلیل در جای خودش برمی‌گردد. پمپ از این
 *     میان‌افزار بی هیچ کاری رد می‌شود.
 */
const crypto = require('crypto');
const core = require('./perm-core');
const slipCore = require('./approval-core');
const staffPerms = require('./staff-perms');
const { one, many, query, newId, now } = require('../db');
const { badRequest, forbidden, notFound, conflict } = require('../middleware/errors');
const hlc = require('./hlc');

const ALG = 'ES256';
const MAX_KEYS_PER_USER = 5;
/** فقط این نقش‌ها چیزی را «مجاز» دارند که تأییدش معنا داشته باشد */
const APPROVER_ROLES = Object.freeze(['owner', 'manager']);

const DENY_CODE = Object.freeze({
  'sales.discount': 'discount_not_allowed',
  'sales.cancel': 'cancel_not_allowed',
  'returns.create': 'return_not_allowed',
});

const MESSAGES = {
  bad_public_key: 'کلیدِ عمومی باید ECDSA P-256 (SPKI، base64) باشد',
  not_approver: 'فقط صاحب دکان یا مدیر کلیدِ تأیید می‌سازد',
  too_many_keys: `هر کاربر حداکثر ${MAX_KEYS_PER_USER} کلیدِ فعال دارد؛ کلیدِ دستگاهِ قدیمی را باطل کنید`,
  key_taken: 'این کلید مالِ کاربرِ دیگری است',
  key_not_found: 'این کلید پیدا نشد',
};

function isPlainObject(v) { return v !== null && typeof v === 'object' && !Array.isArray(v); }
function int(v) { const n = Number(v); return Number.isFinite(n) ? Math.round(n) : 0; }

// ---------- کلیدها ----------

function keyIdOf(spkiDer) {
  return 'ak_' + crypto.createHash('sha256').update(spkiDer).digest('hex').slice(0, 24);
}

/** base64ِ SPKI ⇒ کلیدِ Node؛ فقط P-256 */
function parsePublicKey(b64) {
  const s = typeof b64 === 'string' ? b64.trim() : '';
  if (!s || s.length > 400 || !/^[A-Za-z0-9+/=_-]+$/.test(s)) return null;
  try {
    const der = Buffer.from(s.replace(/-/g, '+').replace(/_/g, '/'), 'base64');
    const key = crypto.createPublicKey({ key: der, format: 'der', type: 'spki' });
    if (key.asymmetricKeyType !== 'ec' || key.asymmetricKeyDetails?.namedCurve !== 'prime256v1') return null;
    return { der, key, b64: der.toString('base64') };
  } catch { return null; }
}

function shapeKey(r) {
  return {
    id: r.id, userId: r.user_id, alg: r.alg, label: r.label || '', deviceId: r.device_id || '',
    createdAt: Number(r.created_at), revokedAt: r.revoked_at === null ? null : Number(r.revoked_at),
  };
}

async function registerKey(shopId, member, { publicKey, label = '', deviceId = '' } = {}) {
  if (!member || !APPROVER_ROLES.includes(member.role)) throw forbidden(MESSAGES.not_approver, 'not_approver');
  const pk = parsePublicKey(publicKey);
  if (!pk) throw badRequest(MESSAGES.bad_public_key, 'bad_public_key');
  const id = keyIdOf(pk.der);
  const userId = member.user_id;
  const existing = await one('SELECT * FROM shop_approver_keys WHERE id=$1', [id]);
  if (existing) {
    if (existing.shop_id !== shopId || existing.user_id !== userId) throw conflict(MESSAGES.key_taken, 'key_taken');
    if (existing.revoked_at === null) return shapeKey(existing);
  }
  const active = await one(
    'SELECT COUNT(*)::int AS n FROM shop_approver_keys WHERE shop_id=$1 AND user_id=$2 AND revoked_at IS NULL',
    [shopId, userId]
  );
  if (active.n >= MAX_KEYS_PER_USER) throw badRequest(MESSAGES.too_many_keys, 'too_many_keys');
  const t = now();
  const row = await one(
    `INSERT INTO shop_approver_keys (id, shop_id, user_id, alg, public_key, label, device_id, created_at)
     VALUES ($1,$2,$3,$4,$5,$6,$7,$8)
     ON CONFLICT (id) DO UPDATE SET revoked_at = NULL, revoked_by = '', label = excluded.label,
       device_id = excluded.device_id, created_at = excluded.created_at
     RETURNING *`,
    [id, shopId, userId, ALG, pk.b64, String(label || '').slice(0, 60), String(deviceId || '').slice(0, 64), t]
  );
  return shapeKey(row);
}

/** صاحب دکان همه را می‌بیند؛ بقیه فقط کلیدهای خودشان */
async function listKeys(shopId, member) {
  const all = member?.role === 'owner';
  const rows = await many(
    `SELECT * FROM shop_approver_keys WHERE shop_id=$1 AND ($2::boolean OR user_id=$3)
      ORDER BY created_at DESC LIMIT 200`,
    [shopId, all, String(member?.user_id || '')]
  );
  return rows.map(shapeKey);
}

async function revokeKey(shopId, member, id) {
  const row = await one('SELECT * FROM shop_approver_keys WHERE id=$1 AND shop_id=$2', [String(id || ''), shopId]);
  if (!row || (row.user_id !== member?.user_id && member?.role !== 'owner')) {
    throw notFound(MESSAGES.key_not_found, 'key_not_found');
  }
  if (row.revoked_at !== null) return shapeKey(row);
  const upd = await one(
    'UPDATE shop_approver_keys SET revoked_at=$2, revoked_by=$3 WHERE id=$1 RETURNING *',
    [row.id, now(), String(member.user_id || '')]
  );
  return shapeKey(upd);
}

// ---------- op ⇒ نیاز ----------

/**
 * opِ v3 (یا مبدلِ v2) و ردیفِ فعلی ⇒ نیاز، یا null اگر هیچ مجوزِ پولیِ
 * لایه‌پذیری درگیر نیست.
 * @returns {null | {perm, doc, req, pct_bp?, amount?, record}}
 */
function needOf(op, row) {
  const doc = `${op.table}/${op.row_id}`;
  if (op.table === 'sales' && op.kind === 'insert') {
    const d = int(op.fields.discountMinor);
    if (d <= 0) return null;
    const bp = core.discountBp(d, int(op.fields.totalMinor));
    return { perm: 'sales.discount', doc, req: { pct: bp / 100, amount: d }, pct_bp: bp, amount: d };
  }
  if (op.table === 'sales' && op.kind === 'cancel') {
    const total = int(row?.data?.finalTotalMinor);
    //  لغو سقف ندارد؛ مبلغ فقط برای سابقه است، نه شرطِ پوشش
    return { perm: 'sales.cancel', doc, req: { amount: total }, record: { amount: total } };
  }
  if (op.table === 'returns' && op.kind === 'insert') {
    const a = int(op.fields.amountMinor);
    return { perm: 'returns.create', doc, req: { amount: a }, amount: a };
  }
  return null;
}

/**
 * همان، برای opِ v1 (نام‌های قدیم، افغانیِ اعشاری). ورودی opِ سنجیده‌شده با
 * `validateOp`ِ خودِ v1 است (فقط خوانده می‌شود)، پس نامِ مستعارِ جدول و
 * ستون‌ها همان است که v1 می‌فهمد.
 */
function needOfV1(op) {
  if (!isPlainObject(op) || typeof op.table !== 'string' || typeof op.row_id !== 'string') return null;
  const f = isPlainObject(op.fields) ? op.fields : {};
  const doc = `${op.table}/${op.row_id}`;
  const minor = (v) => int(Number(v) * 100);
  if (op.table === 'sales' && op.type === 'insert' && Number(f.discount) > 0) {
    const d = minor(f.discount);
    const bp = core.discountBp(d, minor(f.total));
    return { perm: 'sales.discount', doc, req: { pct: bp / 100, amount: d }, pct_bp: bp, amount: d };
  }
  if (op.table === 'sales' && (op.type === 'delete' || (op.type === 'update' && f.status === 'cancelled'))) {
    const total = minor(f.finalTotal);
    return { perm: 'sales.cancel', doc, req: { amount: total }, record: { amount: total } };
  }
  if (op.table === 'returns' && op.type === 'insert') {
    const a = minor(f.amount);
    return { perm: 'returns.create', doc, req: { amount: a }, amount: a };
  }
  return null;
}

// ---------- سنجشِ برگه ----------

function verifySignature(publicKeyB64, text, sigB64) {
  let sig;
  try { sig = Buffer.from(String(sigB64).replace(/-/g, '+').replace(/_/g, '/'), 'base64'); } catch { return false; }
  if (!sig.length || sig.length > 80) return false;
  const pk = parsePublicKey(publicKeyB64);
  if (!pk) return false;
  //  اندروید و JVM امضای DER می‌دهند، WebCrypto امضای خامِ ۶۴ بایتی
  const dsaEncoding = sig.length === 64 && sig[0] !== 0x30 ? 'ieee-p1363' : 'der';
  try {
    return crypto.verify('sha256', Buffer.from(text, 'utf8'), { key: pk.key, dsaEncoding }, sig);
  } catch { return false; }
}

/** لایه‌ها و نقشِ یک عضوِ فعالِ دکان — یا null */
async function memberPolicy(q, shopId, userId) {
  const m = (await q.query(
    `SELECT role FROM shop_members WHERE shop_id=$1 AND user_id=$2 AND status='active' LIMIT 1`,
    [shopId, String(userId || '')]
  )).rows[0];
  if (!m) return null;
  return { role: m.role, layers: await staffPerms.layersFor(shopId, m.role, userId) };
}

const dbq = { query: (s, p) => query(s, p) };

/**
 * برگه‌ای که op آورده برای این نیاز معتبر است؟ اگر بله، nonce مصرف می‌شود.
 * @param q  کلاینتِ تراکنش (v3) یا null (v1)
 * @returns {Promise<null | string>} کدِ خطا یا null
 */
async function acceptSlip(q, ctx, need, slip, { opId, at, deviceId, path }) {
  const c = q || dbq;
  const shape = slipCore.check(slip);
  if (shape) return 'approval_invalid';
  if (slip.shop !== ctx.accountId) return 'approval_mismatch';
  const cover = slipCore.covers(slip, { perm: need.perm, doc: need.doc, pct_bp: need.pct_bp, amount: need.amount, at });
  if (cover) return cover;
  if (slip.by === ctx.userId) return 'approval_self';

  const key = (await c.query(
    'SELECT * FROM shop_approver_keys WHERE id=$1 AND shop_id=$2', [slip.key, ctx.accountId]
  )).rows[0];
  if (!key || key.revoked_at !== null || key.user_id !== slip.by) return 'approval_key_unknown';

  const approver = await memberPolicy(c, ctx.accountId, slip.by);
  if (!approver || !APPROVER_ROLES.includes(approver.role)) return 'approver_not_allowed';
  const d = core.evaluate(approver.role, approver.layers, need.perm, slipCore.evalReq(slip));
  if (d.decision !== 'allow') return 'approver_not_allowed';

  if (!verifySignature(key.public_key, slipCore.payload(slip), slip.sig)) return 'approval_bad_signature';

  const used = (await c.query(
    `INSERT INTO shop_approvals (shop_id, nonce, op_id, doc, perm, pct_bp, amount, approver_id, requester_id,
                                 key_id, device_id, path, reason, issued_at, used_at)
     VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15)
     ON CONFLICT (shop_id, nonce) DO NOTHING RETURNING op_id`,
    [ctx.accountId, slip.nonce, String(opId || ''), need.doc, need.perm,
      Number.isInteger(slip.pct_bp) ? slip.pct_bp : null,
      Number.isInteger(slip.amount) ? slip.amount : (need.record ? need.record.amount : null),
      slip.by, String(ctx.userId || ''), key.id, String(deviceId || '').slice(0, 64), path || 'v3',
      slip.reason.trim(), slip.iat, now()]
  )).rows[0];
  if (used) return null;
  //  همان op که دوباره فرستاده شد (پاسخ گم شد) تکرار است، نه بازپخش
  const prev = (await c.query(
    'SELECT op_id FROM shop_approvals WHERE shop_id=$1 AND nonce=$2', [ctx.accountId, slip.nonce]
  )).rows[0];
  return prev && prev.op_id === String(opId || '') ? null : 'approval_replayed';
}

/** لایه‌های همان کاربرِ توکن — یک بار برای هر دسته */
async function requesterLayers(ctx) {
  if (!ctx._permLayers) {
    ctx._permLayers = staffPerms.layersFor(ctx.accountId, ctx.role, ctx.userId);
  }
  return ctx._permLayers;
}

/**
 * تصمیمِ نهایی برای یک نیاز.
 * @param opts.fallback  قاعدهٔ امروزِ «حذفِ فاکتورِ خودت» (فقط نقشی که پیش‌فرضش deny است)
 * @returns {Promise<null | {reason, box, detail}>}
 */
async function judge(q, ctx, need, slip, { opId, at, deviceId, path, fallback = false }) {
  const layers = await requesterLayers(ctx);
  const d = core.evaluate(ctx.role, layers, need.perm, need.req);
  if (d.decision === 'allow' || fallback) return null;
  const detail = {
    perm: need.perm, decision: d.decision, reason_core: d.reason, requester: ctx.userId || '', role: ctx.role || '',
    pct_bp: need.pct_bp ?? null, amount: need.amount ?? (need.record ? need.record.amount : null),
    ...(d.cap ? { cap: d.cap, limit: d.limit } : {}), path: path || 'v3',
  };
  if (d.decision === 'deny') return { reason: DENY_CODE[need.perm], box: 'perm_denied', detail };
  if (!isPlainObject(slip)) return { reason: 'approval_required', box: 'approval_required', detail };
  const bad = await acceptSlip(q, ctx, need, slip, { opId, at, deviceId, path });
  if (!bad) return null;
  return {
    reason: bad, box: 'approval_rejected',
    detail: { ...detail, approver: typeof slip.by === 'string' ? slip.by.slice(0, 80) : '', slip_error: bad },
  };
}

/** زمانِ op از HLCِ خودِ دستگاه (میلی‌ثانیه) */
function wallOf(stamp) {
  const p = hlc.parse(stamp);
  return p && p.wall > 0 ? p.wall : undefined;
}

/** برگه‌ای که op آورده: `op.approval`، وگرنه `fields.approval` */
function slipOf(op) {
  if (isPlainObject(op.approval)) return op.approval;
  if (isPlainObject(op.fields) && isPlainObject(op.fields.approval)) return op.fields.approval;
  return null;
}

// ---------- برگشت: یک سند، یک سقف ----------

function str(v) { return typeof v === 'string' ? v : ''; }
function fieldsOf(op) { return isPlainObject(op.fields) ? op.fields : {}; }
function atOf(op) { const v = Number(fieldsOf(op).createdAt); return Number.isFinite(v) && v > 0 ? v : 0; }

/**
 * کلیدِ «سندِ برگشت»ِ یک ردیف. سقفِ برگشت روی جمعِ سند است، نه هر ردیف — وگرنه
 * دو ردیفِ ۳۰۰ زیرِ سقفِ ۵۰۰ هر دو می‌گذشتند:
 *   `docId` (برگشتِ تازه، #318) ⇒ `tx` (v3) ⇒ همان فاکتور و همان لحظهٔ ثبت
 *   (برگشتِ قدیمی برای هر قلم یک ردیف با یک createdAt می‌سازد) ⇒ خودِ ردیف.
 */
function returnDocKey(op) {
  const f = fieldsOf(op);
  if (str(f.docId)) return `doc:${f.docId}`;
  if (str(op.tx)) return `tx:${op.tx}`;
  if (str(f.saleId) && atOf(op)) return `sale:${f.saleId}@${atOf(op)}`;
  return `row:${op.row_id}`;
}

/**
 * ردیف‌های برگشتِ یک دسته ⇒ گروه‌ها با نیازِ جمع‌شده. سندِ برگه `returns/<docId>`
 * است (همان که #318 امضا می‌خواهد)؛ برگشتِ بی docId همان `returns/<نخستین ردیف>`ِ
 * امروز (یک ردیف ⇒ همان رفتار). برگه از هر ردیفِ سند که آن را آورده باشد.
 * @param {Array<{op, need}>} items  فقط نیازهای `returns.create`، به ترتیبِ دسته
 * @returns {Map<object, {key, docId, rows, need, slip}>}  op ⇒ گروهش
 */
function groupReturns(items) {
  const byKey = new Map();
  for (const it of items) {
    const k = returnDocKey(it.op);
    if (!byKey.has(k)) byKey.set(k, { key: k, rows: [] });
    byKey.get(k).rows.push(it);
  }
  const out = new Map();
  for (const g of byKey.values()) {
    const first = g.rows[0].op;
    const amount = g.rows.reduce((s, r) => s + int(r.need.amount), 0);
    g.docId = str(fieldsOf(first).docId);
    g.need = { perm: 'returns.create', doc: `returns/${g.docId || first.row_id}`, req: { amount }, amount };
    g.slip = null;
    for (const r of g.rows) { const s = slipOf(r.op); if (s) { g.slip = s; break; } }
    for (const r of g.rows) out.set(r.op, g);
  }
  return out;
}

/** گروه‌های برگشتِ یک دستهٔ v3 (opهای سنجیده‌شده، بومی یا مبدلِ v2) — یا null */
function returnGroupsOf(ops) {
  const items = [];
  for (const op of ops) if (op.table === 'returns' && op.kind === 'insert') items.push({ op, need: needOf(op, null) });
  return items.length ? groupReturns(items) : null;
}

/**
 * داوریِ یک‌بارهٔ سندِ برگشت: نخستین ردیفی که به داوری برسد، با op_idِ نخستین ردیفِ
 * سند (تکرارِ همان دسته «تکرار» است نه بازپخش)؛ بقیهٔ ردیف‌ها همان نتیجه را می‌گیرند.
 */
function judgeGroup(q, ctx, g, { at, deviceId, path }) {
  if (!g.decided) g.decided = judge(q, ctx, g.need, g.slip, { opId: g.rows[0].op.op_id, at, deviceId, path });
  return g.decided;
}

// ---------- راهِ v1: سند و همراه‌هایش ----------

/** صندوق‌هایی که «سندِ ردشده» را برای همراهِ دسته‌های بعد نگه می‌دارند */
const HOLD_KINDS = Object.freeze(['approval_required', 'approval_rejected', 'perm_denied']);
/** «همان ثبت» برای ردیفِ بی نشانی — برنامهٔ وب هر ردیف را جدا `Date.now()` می‌زند */
const SAME_SAVE_MS = 5000;
const MAX_HELD_IN_BOX = 20;
const SALE_LINKED = new Set(['stockMovements', 'auditLog', 'transactions', 'warehouseEntries']);
/** همان واژه‌های `DebtTransaction.kind` (#286) برای رسیدِ برگشت */
const RETURN_KINDS = new Set(['customer_return', 'store_credit']);

function nearAt(a, b) { return a > 0 && b > 0 && Math.abs(a - b) <= SAME_SAVE_MS; }

/** ردیفِ صندوقِ تعارض برای opِ ردشدهٔ v1 (بیرون از تراکنشِ v1) */
async function boxV1(ctx, op, deviceId, out) {
  const id = newId('cf');
  const dedupe = `perm:${String(op.op_id || '').slice(0, 80)}`;
  const open = await one(
    `SELECT id FROM sync_v3_conflicts WHERE app=$1 AND account_kind=$2 AND account_id=$3 AND dedupe_key=$4 AND status='open' LIMIT 1`,
    ['shop', 'shop', ctx.accountId, dedupe]
  );
  if (open) {
    await query('UPDATE sync_v3_conflicts SET detail=$2::jsonb WHERE id=$1', [open.id, JSON.stringify(out.detail)]);
    return;
  }
  await query(
    `INSERT INTO sync_v3_conflicts (id, app, account_kind, account_id, kind, table_name, row_id, field,
                                    loser_op_id, loser_device, detail, dedupe_key, at)
     VALUES ($1,'shop','shop',$2,$3,$4,$5,'',$6,$7,$8::jsonb,$9,$10)`,
    [id, ctx.accountId, out.box, String(op.table || '').slice(0, 80), String(op.row_id || '').slice(0, 80),
      String(op.op_id || '').slice(0, 80), String(deviceId || '').slice(0, 64), JSON.stringify(out.detail), dedupe, now()]
  );
}

/**
 * سندِ ردشده ⇒ نشانی‌هایی که همراه‌هایش به آن اشاره می‌کنند.
 *   sale: شناسهٔ فاکتور؛ rows: شناسهٔ ردیف‌های برگشت و docId؛ items: قلم‌های برگشت؛
 *   at: لحظهٔ ثبت (createdAt؛ برای لغو از سطرِ cancel_sale ِ همان دسته)؛ debtor: قرض‌دارِ فاکتور.
 */
function parentOf(perm, ops, index, batch) {
  const first = ops[0];
  const f = fieldsOf(first);
  const p = { perm, index, sale: '', rows: new Set(), items: new Set(), at: 0, debtor: '' };
  if (perm === 'returns.create') {
    p.sale = str(f.saleId);
    for (const o of ops) {
      const of = fieldsOf(o);
      p.rows.add(o.row_id);
      if (str(of.docId)) p.rows.add(of.docId);
      if (str(of.saleItemId)) p.items.add(of.saleItemId);
    }
    p.at = atOf(first);
    return p;
  }
  p.sale = first.row_id;
  p.debtor = str(f.debtorId);
  if (perm === 'sales.discount') p.at = atOf(first);
  if (perm === 'sales.cancel') {
    const a = batch.find((x) => x.op.table === 'auditLog' && fieldsOf(x.op).type === 'cancel_sale'
      && str(fieldsOf(x.op).refId) === p.sale);
    if (a) p.at = atOf(a.op);
  }
  return p;
}

/**
 * opِ `op` همراهِ سندِ ردشدهٔ `p` است؟ (با نشانی: saleId/refId/ردیف)
 *
 * v1 تراکنش ندارد: اگر فقط سرِ سند رد شود و بقیه بنشیند، موجودی و بدهیِ همهٔ
 * دستگاه‌ها بی هیچ سندی عوض می‌شود. پس ردِ سند همراه‌هایش را هم می‌گیرد — همان «کلِ tx»ِ v3:
 *   فروش (ثبت): قلم‌ها و برگشت‌هایش (saleId)، حرکتِ انبار، دفترِ کار، تراکنش و اصلاحِ انبار (refId)؛
 *   لغو: حرکتِ sale_cancel، سطرِ cancel_sale و رسیدِ `kind=sale_cancel` (refId = فاکتور)؛
 *   برگشت: حرکتِ انبار و اصلاحِ انبار (refId = ردیف یا docId)، رسیدِ customer_return/store_credit
 *     (refId = فاکتور و همان لحظه)، و شمارنده‌های همان برگشت: returnedQty ِ قلم و debtSettled ِ فاکتور.
 *     شمارنده‌ها در ترتیبِ `LedgerDiff` پیش از برگشت‌اند، پس فقط در همان دسته (نه سندِ دستهٔ پیشین).
 */
function companionOf(p, op) {
  const f = fieldsOf(op);
  const ref = str(f.refId);
  if (p.perm === 'sales.discount') {
    if (!p.sale) return false;
    if (op.table === 'saleItems' || op.table === 'returns') return op.type === 'insert' && str(f.saleId) === p.sale;
    return SALE_LINKED.has(op.table) && ref === p.sale;
  }
  if (p.perm === 'sales.cancel') {
    if (!p.sale || ref !== p.sale) return false;
    if (op.table === 'stockMovements') return f.type === 'sale_cancel';
    if (op.table === 'auditLog') return f.type === 'cancel_sale';
    if (op.table === 'transactions') return f.kind === 'sale_cancel';
    return false;
  }
  if (p.perm === 'returns.create') {
    const mine = ref !== '' && p.rows.has(ref);
    const sameSale = ref !== '' && ref === p.sale && (!atOf(op) || !p.at || nearAt(atOf(op), p.at));
    if (op.table === 'stockMovements') return mine;
    if (op.table === 'warehouseEntries') return mine || sameSale;
    if (op.table === 'transactions') return mine || (sameSale && RETURN_KINDS.has(str(f.kind)));
    if (op.table === 'auditLog') return (mine || sameSale) && str(f.type).startsWith('return');
    if (op.type !== 'update' || p.held) return false;
    if (op.table === 'saleItems') return p.items.has(op.row_id) && 'returnedQty' in f;
    if (op.table === 'sales') return p.sale !== '' && op.row_id === p.sale && 'debtSettled' in f && !('status' in f);
  }
  return false;
}

/** ردیفِ پولیِ «بی نشانی» (برنامهٔ پیش از refId): تراکنش، یا اصلاحِ انبار */
function isLoose(op) {
  const f = fieldsOf(op);
  if (op.type !== 'insert' || str(f.refId)) return false;
  return op.table === 'transactions' || (op.table === 'warehouseEntries' && f.isAdjustment === true);
}

/**
 * ردیفِ بی نشانی مالِ همین سند است؟ با «همان ثبت» (createdAt) و قرض‌دار شناخته می‌شود.
 * @returns {true|false|null}  null = نمی‌دانیم (زمان ندارد)
 */
function looseOf(p, op) {
  const f = fieldsOf(op);
  if (op.table === 'transactions') {
    if (p.perm === 'sales.discount') {
      if (f.type !== 'give') return false;
      if (p.debtor && str(f.debtorId) && str(f.debtorId) !== p.debtor) return false;
    } else if (f.type !== 'receive') return false;
  } else if (p.perm !== 'returns.create') return false;
  const at = atOf(op);
  return at && p.at ? nearAt(at, p.at) : null;
}

/**
 * `authorizeOp`ِ v1 این opِ فروش را حتماً با نقش رد می‌کند؟ همان دو شرطِ خودش (با همان
 * `can`) و همان کد؛ حذف راهِ «حذفِ مالِ خودت» را دارد که به دفتر بسته است، پس این‌جا نه.
 */
function v1RoleReason(role, op) {
  if (op.table !== 'sales' || op.type === 'delete' || !isPlainObject(op.fields)) return null;
  const { can } = require('./permissions');
  if (Number(op.fields.discount) > 0 && !can(role, 'sales.discount')) return 'discount_not_allowed';
  if (op.fields.status === 'cancelled' && !can(role, 'sales.cancel')) return 'cancel_not_allowed';
  return null;
}

/**
 * سندهای ردشدهٔ دسته‌های پیشین (صندوقِ تعارض) که شاید همراهی در این دسته دارند و هنوز
 * ننشسته‌اند — برای سند و همراهی که بُرشِ ۲۰۰تاییِ دسته از هم جدا کرده.
 */
async function heldParents(ctx, deviceId, live) {
  const refs = new Set();
  const ats = [];
  for (const { op } of live) {
    const f = fieldsOf(op);
    if (str(f.refId)) refs.add(f.refId);
    if (str(f.saleId)) refs.add(f.saleId);
    if (isLoose(op) && atOf(op)) ats.push(atOf(op));
  }
  if (!refs.size && !ats.length) return [];
  const lo = ats.length ? Math.min(...ats) - SAME_SAVE_MS : 0;
  const hi = ats.length ? Math.max(...ats) + SAME_SAVE_MS : -1;
  const rows = await many(
    `SELECT table_name, row_id, loser_device, detail FROM sync_v3_conflicts
      WHERE app='shop' AND account_kind='shop' AND account_id=$1 AND kind = ANY($2::text[])
        AND table_name IN ('sales','returns') AND jsonb_typeof(detail->'refs') = 'array'
        AND ((detail->'refs') ?| $3::text[]
          OR (loser_device = $4 AND jsonb_typeof(detail->'at') = 'number'
              AND (detail->>'at')::numeric BETWEEN $5 AND $6))
      LIMIT 500`,
    [ctx.accountId, HOLD_KINDS, [...refs], deviceId, lo, hi]
  );
  if (!rows.length) return [];
  const list = (v) => (Array.isArray(v) ? v.filter((x) => typeof x === 'string') : []);
  const parents = rows.map((r) => {
    const d = isPlainObject(r.detail) ? r.detail : {};
    return { perm: str(d.perm), index: -1, held: true, device: r.loser_device, sale: str(d.sale),
      rows: new Set(list(d.rows)), items: new Set(list(d.items)), at: Number(d.at) || 0, debtor: str(d.debtor) };
  });
  //  سندی که از آن پس نشسته (مثلاً دوباره با برگه) دیگر کسی را نگه نمی‌دارد
  const ids = [...new Set(parents.flatMap((p) => (p.perm === 'returns.create' ? [...p.rows] : [p.sale])))];
  const live2 = await many(
    `SELECT table_name, row_id, data->>'status' AS status FROM sync_rows
      WHERE app='shop' AND account_kind='shop' AND account_id=$1 AND table_name IN ('sales','returns')
        AND row_id = ANY($2::text[]) AND deleted_at IS NULL`,
    [ctx.accountId, ids]
  );
  const on = new Map(live2.map((r) => [`${r.table_name}/${r.row_id}`, r.status]));
  return parents.filter((p) => {
    if (p.perm === 'sales.discount') return !!p.sale && !on.has(`sales/${p.sale}`);
    if (p.perm === 'sales.cancel') return !!p.sale && on.get(`sales/${p.sale}`) !== 'cancelled';
    if (p.perm === 'returns.create') return ![...p.rows].some((id) => on.has(`returns/${id}`));
    return false;
  });
}

/**
 * میان‌افزارِ `POST /api/sync/v1/push` — فقط دکان. پمپ (و هر حسابِ دیگر)
 * بی هیچ کاری رد می‌شود.
 *
 * سندی که تصمیمش allow نیست از دسته بیرون می‌آید و پاسخش در جای خودش «rejected» است:
 *   • فاکتور (تخفیف/لغو) هر op جدا؛ برگشت هر سند یک بار با جمعِ ردیف‌ها (`groupReturns`).
 *   • همراه‌هایش (`companionOf`) با `tx_rejected` — در همین دسته، یا دسته‌های بعد تا وقتی
 *     سند ننشسته (`heldParents`، از صندوقِ تعارض).
 *   • ردیفِ پولیِ بی نشانیِ برنامهٔ قدیمی (`isLoose`): با «همان ثبت» ⇒ `tx_rejected`؛ اگر
 *     زمان ندارد و پس از سند آمده ⇒ `deferred` (اعمال نمی‌شود؛ برنامهٔ تازه آن را در صف نگه
 *     می‌دارد و دورِ بعد می‌فرستد، برنامهٔ قدیمی در «ردشده‌ها»ی حالِ همگام‌سازی نشانش می‌دهد؛
 *     شناسه‌اش در صندوقِ تعارضِ سند هم هست).
 *   • نقشی که پیش‌فرضش deny است (شاگرد): همان کدِ `authorizeOp`ِ v1 (discount_not_allowed،
 *     cancel_not_allowed)، حالا با همراه‌ها و صندوقِ perm_denied (مثلِ v3)؛ با برگه ⇒ approval_needs_v3.
 *
 * opی که op_idاش از پیش در oplog است داوری نمی‌شود: پاسخش «تکراری»ِ خودِ v1 است، حتی اگر
 * سقف از آن پس پایین آمده باشد (پاسخِ گم‌شده و تلاشِ دوباره).
 */
async function v1Guard(req, res, next) {
  const ctx = req.sync;
  if (!ctx || ctx.app !== 'shop' || ctx.accountKind !== 'shop' || !ctx.accountId) return next();
  try {
    const ops = Array.isArray(req.body?.ops) ? req.body.ops : null;
    if (!ops || !ops.length) return next();
    const { validateOp } = require('./sync-v1');
    const valid = [];
    ops.forEach((raw, i) => {
      const v = validateOp('shop', raw);
      if (v.ok) valid.push({ i, op: v.op, need: needOfV1(v.op) });   //  نادرست را خودِ v1 رد می‌کند
    });
    if (!valid.length) return next();
    const deviceId = String(req.body?.device_id || req.body?.deviceId || '').slice(0, 64);
    //  op_idِ ثبت‌شده (هر حسابی — op_id در oplog یکتاست) را خودِ v1 جواب می‌دهد:
    //  «تکراری» برای همین حساب، op_id_taken برای دیگری. نه داوری، نه همراه، نه صندوق.
    const seen = new Set((await many(
      'SELECT op_id FROM oplog WHERE op_id = ANY($1::text[])', [valid.map((x) => String(x.op.op_id))]
    )).map((r) => r.op_id));
    const live = valid.filter((x) => !seen.has(String(x.op.op_id)));
    const held = await heldParents(ctx, deviceId, live);
    if (!live.some((x) => x.need) && !held.length) return next();

    const rejected = new Map();
    const reject = (x, reason, status = 'rejected') => rejected.set(x.i, { op_id: String(x.op.op_id || ''), status, reason });
    const parents = [];

    //  ۱) سرِ سندها
    const groups = groupReturns(live.filter((x) => x.need && x.need.perm === 'returns.create'));
    const judged = new Set();
    for (const x of live) {
      if (!x.need) continue;
      const g = groups.get(x.op) || null;
      if (g) { if (judged.has(g)) continue; judged.add(g); }
      const members = g ? g.rows : [x];
      const need = g ? g.need : x.need;
      const slip = g ? g.slip : slipOf(x.op);
      const first = members[0];
      let out;
      if (core.defaultMode(ctx.role, need.perm) === 'deny') {
        if (slip) {
          //  تأییدِ شاگرد فقط از راهِ Sync v3 می‌نشیند (v1ِ پمپ‌مشترک نقش را می‌سنجد)
          out = { reason: 'approval_needs_v3', box: 'approval_rejected',
            detail: { perm: need.perm, requester: ctx.userId || '', role: ctx.role || '', path: 'v1', slip_error: 'approval_needs_v3',
              approver: typeof slip.by === 'string' ? slip.by.slice(0, 80) : '' } };
        } else {
          const why = v1RoleReason(ctx.role, first.op);
          if (!why) continue;                  //  همان امروز: خودِ v1 می‌سنجد (حذفِ مالِ خودت، برگشت)
          out = { reason: why, box: 'perm_denied',
            detail: { perm: need.perm, decision: 'deny', requester: ctx.userId || '', role: ctx.role || '', path: 'v1' } };
        }
      } else {
        out = await judge(null, ctx, need, slip, {
          //  opِ بی زمان (برنامهٔ خیلی قدیمی) ⇒ فقط سنجشِ زمان کنار می‌رود؛ برگه هنوز یک‌بارمصرف و بسته به سند است
          opId: first.op.op_id, at: Number(first.op.ts) > 0 ? Number(first.op.ts) : undefined, deviceId, path: 'v1',
        });
        if (!out) continue;
      }
      for (const m of members) reject(m, out.reason);
      const p = parentOf(need.perm, members.map((m) => m.op), first.i, live);
      p.box = { op: first.op, out };
      p.deferred = [];
      parents.push(p);
    }

    //  ۲) همراه‌ها و ردیف‌های بی نشانی
    const all = parents.concat(held);
    for (const x of live) {
      if (rejected.has(x.i) || (x.need && x.op.table === 'sales')) continue;   //  فاکتورِ دیگر سندِ خودش است
      let verdict = null;
      for (const p of all) {
        if (companionOf(p, x.op)) { verdict = 'tx_rejected'; break; }
        if (!isLoose(x.op) || (p.held && p.device !== deviceId)) continue;
        const v = looseOf(p, x.op);
        if (v === true) { verdict = 'tx_rejected'; break; }
        if (v === null && !p.held && x.i > p.index && !verdict) verdict = p;
      }
      if (verdict === 'tx_rejected') reject(x, 'tx_rejected');
      else if (verdict) { reject(x, 'held_after_reject', 'deferred'); verdict.deferred.push(String(x.op.op_id)); }
    }
    if (!rejected.size) return next();

    //  ۳) صندوقِ تعارض: هر سندِ ردشده یک ردیف، با نشانی‌هایش (برای همراهِ دستهٔ بعد) و ردیف‌های نگه‌داشته
    for (const p of parents) {
      const { op, out } = p.box;
      const refs = [...new Set([p.sale, ...p.rows, ...p.items].filter(Boolean))].slice(0, 200);
      const detail = {
        ...out.detail, refs, sale: p.sale || undefined, rows: [...p.rows].slice(0, 200), items: [...p.items].slice(0, 200),
        at: p.at || undefined, debtor: p.debtor || undefined,
        ...(p.deferred.length ? { held: p.deferred.slice(0, MAX_HELD_IN_BOX) } : {}),
      };
      await boxV1(ctx, op, deviceId, { box: out.box, detail });
    }

    req.body.ops = ops.filter((_, i) => !rejected.has(i));
    const send = res.json.bind(res);
    res.json = (body) => {
      if (body && body.ok && Array.isArray(body.results)) {
        const rest = body.results.slice();
        const merged = [];
        for (let i = 0; i < ops.length; i++) merged.push(rejected.has(i) ? rejected.get(i) : rest.shift());
        //  اگر شمارِ پاسخ‌ها نخواند، چیزی گم نشود
        body.results = merged.filter(Boolean).concat(rest);
      }
      return send(body);
    };
    next();
  } catch (err) { next(err); }
}

// ---------- سابقه ----------

async function listApprovals(shopId, { limit = 100 } = {}) {
  const cap = Math.min(Math.max(1, Number(limit) || 100), 500);
  const rows = await many(
    `SELECT a.*, ua.name AS approver_name, ur.name AS requester_name
       FROM shop_approvals a
       LEFT JOIN users ua ON ua.id = a.approver_id
       LEFT JOIN users ur ON ur.id = a.requester_id
      WHERE a.shop_id=$1 ORDER BY a.used_at DESC LIMIT $2`,
    [shopId, cap]
  );
  return rows.map((r) => ({
    nonce: r.nonce, opId: r.op_id, doc: r.doc, perm: r.perm,
    pctBp: r.pct_bp === null ? null : Number(r.pct_bp), amount: r.amount === null ? null : Number(r.amount),
    approverId: r.approver_id, approverName: r.approver_name || '',
    requesterId: r.requester_id, requesterName: r.requester_name || '',
    keyId: r.key_id, deviceId: r.device_id, path: r.path, reason: r.reason,
    issuedAt: Number(r.issued_at), usedAt: Number(r.used_at),
  }));
}

module.exports = {
  ALG, MAX_KEYS_PER_USER, APPROVER_ROLES, DENY_CODE, MESSAGES,
  keyIdOf, parsePublicKey, registerKey, listKeys, revokeKey,
  needOf, needOfV1, verifySignature, acceptSlip, judge, wallOf, slipOf, v1Guard, listApprovals,
  returnDocKey, groupReturns, returnGroupsOf, judgeGroup, companionOf, HOLD_KINDS,
};
