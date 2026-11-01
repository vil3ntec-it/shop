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
const { rejectOps } = require('./v1-reject');
const roleV1 = require('./staff-role-v1');   //  A16: جدول/ردیفِ بیرون از نقش در v1 (شرح همان‌جا)

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
  const data = Buffer.from(text, 'utf8');
  const verify = (dsaEncoding) => {
    try { return crypto.verify('sha256', data, { key: pk.key, dsaEncoding }, sig); } catch { return false; }
  };
  //  اندروید و JVM امضای DER می‌دهند، WebCrypto امضای خامِ ۶۴ بایتی (IEEE P1363). از بایتِ اول
  //  نمی‌شود حدس زد: امضای خامی که با 0x30 آغاز می‌شود (۱ در ۲۵۶) شبیهِ DER است. پس ۶۴ بایتی اول
  //  خام، بعد DER؛ بقیه DER.
  if (sig.length === 64) return verify('ieee-p1363') || verify('der');
  return verify('der');
}

/** لایه‌ها و نقشِ یک عضوِ فعالِ دکان — یا null */
async function memberPolicy(q, shopId, userId) {
  const m = (await q.query(
    `SELECT role FROM shop_members WHERE shop_id=$1 AND user_id=$2 AND status='active' LIMIT 1`,
    [shopId, String(userId || '')]
  )).rows[0];
  if (!m) return null;
  return { role: m.role, layers: await staffPerms.layersFor(shopId, m.role, userId, (s, p) => q.query(s, p)) };
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

/**
 * لایه‌های همان کاربرِ توکن — یک بار برای هر دسته. `q` (کلاینتِ تراکنشِ v3) ⇒ با همان اتصال، نه استخر:
 * پوش‌های هم‌زمانِ پشتِ قفلِ حساب (`sync-v3`) همهٔ استخر را نگه می‌دارند و پرسشِ دوم اتصال نمی‌گیرد.
 */
async function requesterLayers(ctx, q = null) {
  if (!ctx._permLayers) {
    ctx._permLayers = staffPerms.layersFor(ctx.accountId, ctx.role, ctx.userId, q ? (s, p) => q.query(s, p) : null);
  }
  return ctx._permLayers;
}

/**
 * تصمیمِ نهایی برای یک نیاز.
 * @param opts.fallback  قاعدهٔ امروزِ «حذفِ فاکتورِ خودت» (فقط نقشی که پیش‌فرضش deny است)
 * @returns {Promise<null | {reason, box, detail}>}
 */
async function judge(q, ctx, need, slip, { opId, at, deviceId, path, fallback = false }) {
  const layers = await requesterLayers(ctx, q);
  const d = core.evaluate(ctx.role, layers, need.perm, need.req);
  if (d.decision === 'allow' || fallback) return null;
  const detail = {
    perm: need.perm, decision: d.decision, reason_core: d.reason, requester: ctx.userId || '', role: ctx.role || '',
    pct_bp: need.pct_bp ?? null, amount: need.amount ?? (need.record ? need.record.amount : null),
    ...(need.cumulative != null ? { cumulative: need.cumulative } : {}),
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

// ---------- برگشت: یک سند یک تصمیم؛ سقف تجمعی روی هر فاکتور ----------

function str(v) { return typeof v === 'string' ? v : ''; }
function fieldsOf(op) { return isPlainObject(op.fields) ? op.fields : {}; }
function atOf(op) { const v = Number(fieldsOf(op).createdAt); return Number.isFinite(v) && v > 0 ? v : 0; }
/** مهرِ «ثبت»: `ts` ِ op ِ v1 (هر دو برنامه یک ts برای کلِ یک diff می‌زنند)؛ در نمای v3 دیوارِ HLC */
function tsOf(op) { const v = Number(op.ts); return Number.isFinite(v) && v > 0 ? v : 0; }

/**
 * کلیدِ «سندِ برگشت»ِ یک ردیف — ردیف‌هایی که یک برگه و یک تصمیم دارند:
 *   `docId` (برگشتِ تازه، #318) ⇒ `tx` و همان فاکتور (v3؛ یک ثبت می‌تواند برگشتِ دو فاکتور
 *   باشد) ⇒ همان فاکتور در همان ثبت (`ts` ِ v1 — برنامهٔ وب برای هر ردیف `createdAt` ِ جدا
 *   می‌زند ولی کلِ ثبت یک ts دارد) ⇒ خودِ ردیف.
 */
function returnDocKey(op) {
  const f = fieldsOf(op);
  if (str(f.docId)) return `doc:${f.docId}`;
  if (str(op.tx)) return `tx:${op.tx}:sale:${str(f.saleId)}`;
  if (str(f.saleId) && tsOf(op)) return `sale:${f.saleId}@ts:${tsOf(op)}`;
  return `row:${op.row_id}`;
}

/**
 * ردیف‌های برگشتِ یک دسته ⇒ سندها. سندِ برگه `returns/<docId>` است (همان که #318 امضا
 * می‌خواهد)؛ برگشتِ بی docId همان `returns/<نخستین ردیف>`ِ امروز. برگه از هر ردیفِ سند.
 * @param {Array<{op, need}>} items  فقط نیازهای `returns.create`، به ترتیبِ دسته
 * @returns {Map<object, {key, docId, sale, amount, rows, need, slip}>}  op ⇒ سندش
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
    g.amount = g.rows.reduce((s, r) => s + int(r.need.amount), 0);
    g.docId = str(fieldsOf(first).docId);
    g.sale = str(fieldsOf(first).saleId);
    g.need = { perm: 'returns.create', doc: `returns/${g.docId || first.row_id}`, req: { amount: g.amount }, amount: g.amount };
    g.slip = null;
    for (const r of g.rows) { const s = slipOf(r.op); if (s) { g.slip = s; break; } }
    for (const r of g.rows) out.set(r.op, g);
  }
  return out;
}

/**
 * سقفِ برگشت **تجمعیِ هر فاکتور** است (تصمیمِ مدیر): جمعِ همهٔ برگشت‌های آن فاکتور — ثبت‌شده
 * در سرور از هر دستگاه و هر نقش (برگشتِ صاحب و برگشتِ تأییدشده هم، عمداً: وگرنه یک برگشت
 * میانِ نقش‌ها یا با یک تأییدِ کوچک تکه می‌شد)، همین دسته، و همین سند — زیرِ سقفِ کاربر.
 * برگه فقط همین سند را می‌پوشاند (`need.amount`). برگشتِ بی رسید فاکتوری ندارد ⇒ فقط همان سند.
 */
function cumulativeNeed(g, before) {
  if (!g.sale) return g.need;
  const total = before + g.amount;
  return { ...g.need, req: { amount: total }, cumulative: total };
}

/**
 * جمعِ برگشت‌های ثبت‌شدهٔ یک فاکتور، به پول — از **هر دو** دفتر (v1: `sync_rows`، افغانی؛
 * v3: `sync_v3_rows`، به پول)، هر ردیف یک بار (پلِ v3⇄v1 همان ردیف را در هر دو می‌گذارد)،
 * بی برگشتِ حذف‌شده یا لغوشده.
 * @param q  کلیدِ تراکنشِ push ِ v3 (opهای پیشینِ همین دسته هم دیده می‌شوند) یا null
 */
async function returnedTotal(q, ctx, saleId) {
  const c = q || dbq;
  const byRow = new Map();
  const v1 = (await c.query(
    `SELECT row_id, data->>'amount' AS a FROM sync_rows WHERE app='shop' AND account_kind='shop' AND account_id=$1
        AND table_name='returns' AND data->>'saleId' = $2 AND deleted_at IS NULL
        AND COALESCE(data->>'status', '') <> 'cancelled'`, [ctx.accountId, saleId]
  )).rows;
  for (const r of v1) byRow.set(r.row_id, int(Number(r.a) * 100));
  const v3 = (await c.query(
    `SELECT row_id, data->>'amountMinor' AS a FROM sync_v3_rows WHERE app='shop' AND account_kind='shop' AND account_id=$1
        AND table_name='returns' AND data->>'saleId' = $2 AND deleted_at IS NULL
        AND COALESCE(data->>'status', '') <> 'cancelled'`, [ctx.accountId, saleId]
  )).rows;
  for (const r of v3) byRow.set(r.row_id, int(r.a));
  let sum = 0;
  for (const v of byRow.values()) sum += v;
  return sum;
}

/**
 * کلیدِ قفلِ «سقفِ برگشتِ تجمعی»ِ یک دکان — فقط داخلِ تراکنشِ push ِ v3
 * (`pg_advisory_xact_lock`، بی اتصالِ جدا، با پایانِ تراکنش آزاد).
 * ⚠️ محدودیتِ شناخته: v1 قفل نمی‌گیرد (نگهبان بیرون از تراکنشِ v1 است)؛ دو دستگاهِ v1 ِ
 * هم‌زمان روی یک فاکتور ممکن است هر دو زیرِ سقف بگذرند. هیچ داده‌ای گم نمی‌شود؛ فقط سقف
 * یک بار بیش از اندازه پذیرفته می‌شود.
 */
function returnsLockKey(shopId) { return `staff-returns:${shopId}`; }

/** سندهای برگشتِ یک دستهٔ v3 (opهای سنجیده‌شده، بومی یا مبدلِ v2) — یا null */
function returnGroupsOf(ops) {
  const items = [];
  for (const op of ops) if (op.table === 'returns' && op.kind === 'insert') items.push({ op, need: needOf(op, null) });
  return items.length ? groupReturns(items) : null;
}

/**
 * داوریِ یک‌بارهٔ سندِ برگشتِ v3، با سقفِ تجمعیِ فاکتور: نخستین ردیفی که به داوری برسد، با
 * op_id ِ نخستین ردیفِ سند (تکرارِ همان دسته «تکرار» است نه بازپخش)؛ بقیه همان نتیجه.
 */
function judgeGroup(q, ctx, g, { at, deviceId, path }) {
  if (!g.decided) {
    g.decided = (async () => {
      const before = g.sale ? await returnedTotal(q, ctx, g.sale) : 0;
      return judge(q, ctx, cumulativeNeed(g, before), g.slip, { opId: g.rows[0].op.op_id, at, deviceId, path });
    })();
  }
  return g.decided;
}

// ---------- سند و همراه‌هایش ----------

/** صندوق‌هایی که «سندِ ردشده» را برای همراهِ دسته‌های بعد نگه می‌دارند */
const HOLD_KINDS = Object.freeze(['approval_required', 'approval_rejected', 'perm_denied']);
/** نگه‌داشتنِ سندِ ردشده برای دستهٔ بعد: فقط صندوقِ باز، فقط همان دستگاه، و حداکثر یک روز */
const HOLD_TTL_MS = 24 * 60 * 60 * 1000;
/** دفترِ پایه (نخستین هم‌ترازی): یک ts برای چند سند ⇒ «همان ثبت» با createdAt ِ نزدیک هم */
const BULK_NEAR_MS = 1000;
/** نمای v3 (برنامهٔ کهنه بی tx): «همان ثبت» = دیوارِ HLC ِ نزدیک */
const SAVE_HLC_MS = 1000;
const SALE_LINKED = new Set(['stockMovements', 'auditLog', 'transactions', 'warehouseEntries']);
/** جدول‌هایی که همراهِ یک سند در آن‌ها می‌نشیند */
const LINKABLE = new Set(['saleItems', 'returns', 'stockMovements', 'auditLog', 'transactions', 'warehouseEntries', 'sales']);
/** همان واژه‌های `DebtTransaction.kind` (#286) برای رسیدِ برگشت */
const RETURN_KINDS = new Set(['customer_return', 'store_credit']);

/** «سرِ» یک ثبت: فاکتور، خرید، یا سندِ برگشت — برای شناختنِ دفترِ چندسندی زیرِ یک ts */
function headKey(op) {
  if (op.table === 'sales' || op.table === 'purchases') return `${op.table}:${op.row_id}`;
  if (op.table === 'returns' && op.type === 'insert') {
    const f = fieldsOf(op);
    return str(f.saleId) ? `sales:${f.saleId}` : `returns:${str(f.docId) || op.row_id}`;
  }
  return null;
}

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
 *   sale: فاکتور؛ rows: ردیف‌های برگشت و docId؛ items: قلم‌های برگشت؛ debtor: قرض‌دارِ فاکتور؛
 *   ts: مهرِ همان ثبت؛ at: createdAt ِ سند (برای لغو، سطرِ cancel_sale ِ همان ثبت) — فقط برای دفترِ پایه.
 */
function parentOf(perm, ops, index, batch) {
  const first = ops[0];
  const f = fieldsOf(first);
  const p = { perm, index, sale: '', rows: new Set(), items: new Set(), at: 0, debtor: '', ts: tsOf(first), tol: 0, bulk: false };
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
  if (perm === 'sales.cancel' && batch) {
    const a = batch.find((o) => o.table === 'auditLog' && fieldsOf(o).type === 'cancel_sale'
      && str(fieldsOf(o).refId) === p.sale && tsOf(o) === p.ts);
    if (a) p.at = atOf(a);
  }
  return p;
}

/**
 * «همان ثبت»ِ سند؟ با مهرِ ثبت (`ts` ِ v1، برابر) **و** اگر هر دو createdAt دارند، createdAt ِ
 * نزدیک (۱ ثانیه) — یک ts می‌تواند دفترِ پایهٔ چندسندی باشد (بازگردانی، نخستین بارگذاری) که
 * بُرشِ ۲۰۰تایی فقط یک سرش را در این دسته گذاشته. بی createdAt فقط وقتی همان ثبت است که این ts
 * یک سند بیشتر ندارد. بی مهر ⇒ نه — ردیفی که معلوم نیست مالِ سند است اعمال می‌شود، رد نمی‌شود.
 */
function sameSave(p, op) {
  const t = tsOf(op);
  if (!p.ts || !t || Math.abs(t - p.ts) > p.tol) return false;
  const a = atOf(op);
  if (!p.bulk && (!a || !p.at)) return true;
  return a > 0 && p.at > 0 && Math.abs(a - p.at) <= BULK_NEAR_MS;
}

/**
 * opِ `op` همراهِ سندِ ردشدهٔ `p` است؟ v1 تراکنش ندارد: اگر فقط سرِ سند رد شود و بقیه بنشیند،
 * موجودی و بدهیِ همهٔ دستگاه‌ها بی هیچ سندی عوض می‌شود — پس ردِ سند همراه‌هایش را هم می‌گیرد:
 *   فروش (ثبت): هر چه به آن اشاره کند — قلم و برگشت (saleId)، حرکت، دفترِ کار، تراکنش و اصلاحِ
 *     انبار (refId)؛ فاکتوری که روی سرور نیست همراهش هم یتیم است، از هر ثبتی.
 *   لغو: حرکتِ sale_cancel، سطرِ cancel_sale و رسیدِ `kind=sale_cancel` — همان ثبت.
 *   برگشت: حرکت و اصلاحِ انبار و تراکنشی که به ردیف/docId ِ همین برگشت اشاره دارد؛ و در همان ثبت:
 *     رسیدِ customer_return/store_credit و سطرِ دفترِ کارِ فاکتور، returnedQty ِ قلم و debtSettled ِ فاکتور.
 *   ردیفِ بی نشانیِ برنامهٔ قدیمی (تراکنش یا اصلاحِ انبارِ بی refId): فقط همان ثبت (`looseOf`).
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
    if (!p.sale || ref !== p.sale || !sameSave(p, op)) return false;
    if (op.table === 'stockMovements') return f.type === 'sale_cancel';
    if (op.table === 'auditLog') return f.type === 'cancel_sale';
    if (op.table === 'transactions') return f.kind === 'sale_cancel';
    return false;
  }
  if (p.perm === 'returns.create') {
    const mine = ref !== '' && p.rows.has(ref);
    const ofSale = ref !== '' && ref === p.sale;
    if (op.table === 'stockMovements') return mine;
    if (op.table === 'warehouseEntries') return mine || (ofSale && sameSave(p, op));
    if (op.table === 'transactions') return mine || (ofSale && RETURN_KINDS.has(str(f.kind)) && sameSave(p, op));
    if (op.table === 'auditLog') return str(f.type).startsWith('return') && (mine || (ofSale && sameSave(p, op)));
    if (op.type !== 'update' || !sameSave(p, op)) return false;
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

/** ردیفِ بی نشانی مالِ همین سند است؟ فقط در همان ثبت، و با نوع و قرض‌دارِ درست */
function looseOf(p, op) {
  if (!isLoose(op) || !sameSave(p, op)) return false;
  const f = fieldsOf(op);
  if (op.table === 'transactions') {
    if (p.perm === 'sales.discount') {
      return f.type === 'give' && (!p.debtor || !str(f.debtorId) || str(f.debtorId) === p.debtor);
    }
    return f.type === 'receive';
  }
  return p.perm === 'returns.create';            //  اصلاحِ انبار فقط از برگشت
}

function belongs(p, op) { return companionOf(p, op) || looseOf(p, op); }

/** opی که خودِ سندِ `p` را عوض می‌کند (همان فاکتور یا همان ردیفِ برگشت) */
function touchesDoc(p, op) {
  if (p.perm === 'returns.create') return op.table === 'returns' && p.rows.has(op.row_id);
  return op.table === 'sales' && op.row_id === p.sale;
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
 * سندهای ردشدهٔ دسته‌های پیشینِ **همین دستگاه** (بُرشِ دسته سند و همراه را جدا کرد):
 * فقط صندوقِ باز (رسیدگی‌شده/کنارگذاشته آزاد است)، حداکثر یک روز، و فقط سندی که هنوز در هیچ
 * دفتری (v1 یا v3) ننشسته. نشانی‌ها از `detail` ِ صندوق؛ «همان ثبت» در v1 ts ِ برابر و در v3
 * دیوارِ HLC ِ نزدیک.
 * @param ops  opهای v1، یا نمای v1 ِ opهای v3 (`v3View`)
 */
async function heldParents(ctx, deviceId, ops, { tol = 0 } = {}) {
  if (!deviceId) return [];
  const refs = new Set();
  const tss = [];
  for (const op of ops) {
    if (!LINKABLE.has(op.table)) continue;
    const f = fieldsOf(op);
    if (str(f.refId)) refs.add(f.refId);
    if (str(f.saleId)) refs.add(f.saleId);
    if (op.table === 'sales' || op.table === 'saleItems' || op.table === 'returns') refs.add(op.row_id);
    if (tsOf(op)) tss.push(tsOf(op));
  }
  if (!refs.size && !tss.length) return [];
  const lo = tss.length ? tss.reduce((m, t) => Math.min(m, t), Infinity) - Math.max(tol, SAVE_HLC_MS) : 0;
  const hi = tss.length ? tss.reduce((m, t) => Math.max(m, t), 0) + Math.max(tol, SAVE_HLC_MS) : -1;
  const rows = await many(
    `SELECT id, table_name, row_id, detail FROM sync_v3_conflicts
      WHERE app='shop' AND account_kind='shop' AND account_id=$1 AND kind = ANY($2::text[]) AND status='open'
        AND loser_device=$3 AND at > $4 AND table_name IN ('sales','returns')
        AND jsonb_typeof(detail->'refs') = 'array'
        AND ((detail->'refs') ?| $5::text[]
          OR (CASE WHEN jsonb_typeof(detail->'ts') = 'number' THEN (detail->>'ts')::numeric END) BETWEEN $6 AND $7)
      LIMIT 200`,
    [ctx.accountId, HOLD_KINDS, deviceId, now() - HOLD_TTL_MS, [...refs], lo, hi]
  );
  if (!rows.length) return [];
  const list = (v) => (Array.isArray(v) ? v.filter((x) => typeof x === 'string') : []);
  const parents = rows.map((r) => {
    const d = isPlainObject(r.detail) ? r.detail : {};
    return { perm: str(d.perm), index: -1, held: true, boxId: r.id, sale: str(d.sale), rows: new Set(list(d.rows)),
      items: new Set(list(d.items)), at: Number(d.at) || 0, debtor: str(d.debtor), ts: Number(d.ts) || 0,
      tol: d.path === 'v1' ? 0 : SAVE_HLC_MS, bulk: d.bulk === true };
  });
  //  سندی که از آن پس نشسته (مثلاً دوباره با برگه، از هر راهی) دیگر کسی را نگه نمی‌دارد
  const ids = [...new Set(parents.flatMap((p) => (p.perm === 'returns.create' ? [...p.rows] : [p.sale])))];
  const on = new Map();
  for (const ledger of ['sync_rows', 'sync_v3_rows']) {
    const got = await many(
      `SELECT table_name, row_id, data->>'status' AS status FROM ${ledger}
        WHERE app='shop' AND account_kind='shop' AND account_id=$1 AND table_name IN ('sales','returns')
          AND row_id = ANY($2::text[]) AND deleted_at IS NULL`,
      [ctx.accountId, ids]
    );
    for (const r of got) {
      const k = `${r.table_name}/${r.row_id}`;
      if (!on.has(k) || r.status === 'cancelled') on.set(k, r.status);
    }
  }
  return parents.filter((p) => {
    if (p.perm === 'sales.discount') return !!p.sale && !on.has(`sales/${p.sale}`);
    if (p.perm === 'sales.cancel') return !!p.sale && on.get(`sales/${p.sale}`) !== 'cancelled';
    if (p.perm === 'returns.create') return ![...p.rows].some((id) => on.has(`returns/${id}`));
    return false;
  });
}

/**
 * سندِ برگشتِ این دسته دنبالهٔ سندِ برگشتِ ردشده است؟ (همان docId با ردیفِ تازه، یا همان
 * فاکتور در همان ثبت — بُرشِ دسته یا سندِ دوم در همان ثبت). آن‌گاه جدا داوری نمی‌شود — وگرنه
 * تکهٔ کوچکش زیرِ سقف می‌گذشت — و با همان رد برمی‌گردد. ردیفِ دوبارهٔ خودِ آن سند (با برگه) نه.
 */
function returnOwnerOf(g, parents) {
  const ids = g.rows.map((r) => r.op.row_id);
  return parents.find((p) => p.perm === 'returns.create' && !ids.some((id) => p.rows.has(id))
    && ((g.docId && p.rows.has(g.docId)) || (p.sale !== '' && p.sale === g.sale && sameSave(p, g.rows[0].op)))) || null;
}

/**
 * سندِ برگشتِ این دسته دنبالهٔ ثبتی است که بخشی از آن در دستهٔ پیشینِ همین دستگاه **پذیرفته**
 * شد؟ ثبتِ بزرگ‌تر از یک دسته فقط با یک ts بریده می‌شود (`OpQueue.batch`/`cut`)، پس دنباله همان
 * ts، همان دستگاه، همان فاکتور (و اگر هست همان docId) را دارد و ردیفی از آن در oplog هست.
 * تصمیمِ سند همان بخشِ اول است. (docId ِ تکراری روی فاکتورِ دیگر یا با ts ِ دیگر دنباله نیست.)
 */
async function acceptedPartOf(ctx, deviceId, g) {
  const first = g.rows[0].op;
  const ts = tsOf(first);
  if (!ts || (!g.docId && !g.sale)) return false;
  const hit = await one(
    `SELECT 1 AS x FROM oplog WHERE app='shop' AND account_kind='shop' AND account_id=$1 AND table_name='returns'
        AND op_type='insert' AND device_id=$2 AND client_ts=$3
        AND ($4::text = '' OR fields->>'docId' = $4) AND ($5::text = '' OR fields->>'saleId' = $5)
        AND NOT (row_id = ANY($6::text[])) LIMIT 1`,
    [ctx.accountId, deviceId, ts, g.docId || '', g.sale || '', g.rows.map((r) => String(r.op.row_id))]
  );
  return !!hit;
}

/** ردیف‌های سندِ پیوسته به سندِ ردشده: نشانی‌هایش به آن اضافه می‌شود (برای همراه‌های بعدی) */
function absorb(p, g) {
  for (const r of g.rows) {
    const f = fieldsOf(r.op);
    p.rows.add(r.op.row_id);
    if (str(f.docId)) p.rows.add(f.docId);
    if (str(f.saleItemId)) p.items.add(f.saleItemId);
  }
  if (p.held) p.dirty = true;
}

/** نشانی‌های تازهٔ سندِ نگه‌داشته در صندوقش */
async function rememberHeld(p) {
  const refs = [...new Set([p.sale, ...p.rows, ...p.items].filter(Boolean))].slice(0, 200);
  await query(
    `UPDATE sync_v3_conflicts SET detail = detail || $2::jsonb WHERE id=$1`,
    [p.boxId, JSON.stringify({ refs, rows: [...p.rows].slice(0, 200), items: [...p.items].slice(0, 200) })]
  );
}

/**
 * میان‌افزارِ `POST /api/sync/v1/push` — فقط دکان. پمپ (و هر حسابِ دیگر) و صاحبِ دکان
 * (همه‌چیز «مجاز»؛ هرگز سندِ ردشده ندارد) بی هیچ پرس‌وجویی رد می‌شوند.
 *
 * سندی که تصمیمش allow نیست از دسته بیرون می‌آید و پاسخش در جای خودش «rejected» است:
 *   • فاکتور (تخفیف/لغو) هر op جدا؛ برگشت هر سند یک بار، با سقفِ تجمعیِ فاکتور (بی قفل —
 *     محدودیتِ شناختهٔ دو دستگاهِ هم‌زمان: `returnsLockKey`).
 *   • همراه‌هایش (`companionOf`/`looseOf`) با `tx_rejected` — در همین دسته، یا دسته‌های بعدِ
 *     همین دستگاه تا وقتی سند ننشسته (`heldParents`). وضعیتِ تازه‌ای نیست: هر برنامه‌ای
 *     (حتی کهنه) فقط applied/duplicate/rejected می‌بیند؛ ردیفی که معلوم نیست مالِ سند است اعمال می‌شود.
 *   • نقشی که پیش‌فرضش deny است (شاگرد): همان کدِ `authorizeOp`ِ v1 (discount_not_allowed،
 *     cancel_not_allowed)، با همراه‌ها و صندوقِ perm_denied (مثلِ v3)؛ با برگه ⇒ approval_needs_v3.
 *
 * opی که op_idاش از پیش در oplog است داوری نمی‌شود: پاسخش «تکراری»ِ خودِ v1 است، حتی اگر
 * سقف از آن پس پایین آمده باشد (پاسخِ گم‌شده و تلاشِ دوباره).
 */
async function v1Guard(req, res, next) {
  const ctx = req.sync;
  if (!ctx || ctx.app !== 'shop' || ctx.accountKind !== 'shop' || !ctx.accountId) return next();
  if (ctx.role === 'owner') return next();
  try {
    const ops = Array.isArray(req.body?.ops) ? req.body.ops : null;
    if (!ops || !ops.length) return next();
    const { validateOp, tableOf } = require('./sync-v1');
    const valid = [];
    const rejected = new Map();
    ops.forEach((raw, i) => {
      const v = validateOp('shop', raw);
      if (v.ok) { valid.push({ i, op: v.op, need: needOfV1(v.op) }); return; }
      //  opِ نادرستِ جدولِ زیرِ نگهبان همین‌جا رد می‌شود، نه «خودِ v1 ردش می‌کند»: هر لایهٔ بعدی که
      //  درستش کند (مثلِ پالایشِ null ِ قدیمی) آن را بی داوری به v1 می‌رساند
      const r = isPlainObject(raw) ? raw : {};
      if (LINKABLE.has(tableOf('shop', r.table ?? r.table_name ?? r.tableName))) {
        rejected.set(i, { op_id: v.op_id, status: 'rejected', reason: v.reason });
      }
    });
    const deviceId = String(req.body?.device_id || req.body?.deviceId || '').slice(0, 64);
    const offTable = await roleV1.offRole(ctx, valid, rejected);
    const hasNeed = valid.some((x) => x.need);
    if (!hasNeed && !offTable.length && !valid.some((x) => LINKABLE.has(x.op.table))) return finish();
    const held0 = await heldParents(ctx, deviceId, valid.map((x) => x.op));
    if (!hasNeed && !held0.length && !offTable.length) return finish();
    //  op_idِ ثبت‌شده (هر حسابی — op_id در oplog یکتاست) را خودِ v1 جواب می‌دهد:
    //  «تکراری» برای همین حساب، op_id_taken برای دیگری. نه داوری، نه همراه، نه صندوق.
    const seen = await roleV1.seenOps(valid);
    const live = roleV1.dropOff(valid, offTable, seen, rejected);
    const liveOps = live.map((x) => x.op);
    //  دفترِ پایه: چند سند زیرِ یک ts
    const heads = new Map();
    for (const op of liveOps) {
      const h = headKey(op);
      const t = tsOf(op);
      if (!h || !t) continue;
      if (!heads.has(t)) heads.set(t, new Set());
      heads.get(t).add(h);
    }

    const reject = (x, reason) => rejected.set(x.i, { op_id: String(x.op.op_id || ''), status: 'rejected', reason });
    const parents = [];

    //  ۱) سرِ سندها — فاکتور هر op جدا، برگشت هر سند یک بار با سقفِ تجمعیِ فاکتور
    const groups = groupReturns(live.filter((x) => x.need && x.need.perm === 'returns.create'));
    const judged = new Set();
    const tally = new Map();                     //  فاکتور ⇒ جمعِ برگشتِ ثبت‌شده + پذیرفتهٔ همین دسته
    //  گذرِ اول: دنبالهٔ ثبتی که بخشِ اولش پذیرفته شد — تصمیمِ سند همان بخشِ اول است و مبلغش پیش از
    //  داوریِ سندهای دیگرِ همین دسته به جمعِ فاکتور می‌رود. فقط سندی که دسته با ts ِ آن شروع می‌شود
    //  می‌تواند دنباله باشد (بُرش فقط سرِ دسته را دنباله می‌کند) — بی پرس‌وجو برای بقیه.
    const lead = valid.length ? tsOf(valid[0].op) : 0;
    const continued = new Set();
    for (const g of new Set(groups.values())) {
      if (!lead || tsOf(g.rows[0].op) !== lead || returnOwnerOf(g, held0)) continue;
      if (!(await acceptedPartOf(ctx, deviceId, g))) continue;
      continued.add(g);
      if (g.sale) {
        if (!tally.has(g.sale)) tally.set(g.sale, await returnedTotal(null, ctx, g.sale));
        tally.set(g.sale, tally.get(g.sale) + g.amount);
      }
    }
    for (const x of live) {
      if (!x.need) continue;
      const g = groups.get(x.op) || null;
      if (g) {
        if (judged.has(g)) continue;
        judged.add(g);
        //  دنبالهٔ ثبتی که بخشِ اولش پذیرفته شد (گذرِ اول): وگرنه ردیف‌ها و حرکت‌های پذیرفته بی
        //  شمارنده‌ها و رسیدِ مشترکِ ثبت (پشتِ سرِ همین ردیف‌ها) می‌ماندند
        if (continued.has(g)) continue;
        const owner = returnOwnerOf(g, parents.concat(held0));
        if (owner) {
          for (const m of g.rows) reject(m, 'tx_rejected');
          absorb(owner, g);
          continue;
        }
      }
      const members = g ? g.rows : [x];
      const first = members[0];
      let need = g ? g.need : x.need;
      const slip = g ? g.slip : slipOf(x.op);
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
        if (g && g.sale) {
          if (!tally.has(g.sale)) tally.set(g.sale, await returnedTotal(null, ctx, g.sale));
          need = cumulativeNeed(g, tally.get(g.sale));
        }
        out = await judge(null, ctx, need, slip, {
          //  opِ بی زمان (برنامهٔ خیلی قدیمی) ⇒ فقط سنجشِ زمان کنار می‌رود؛ برگه هنوز یک‌بارمصرف و بسته به سند است
          opId: first.op.op_id, at: tsOf(first.op) || undefined, deviceId, path: 'v1',
        });
        if (!out) {
          if (g && g.sale) tally.set(g.sale, tally.get(g.sale) + g.amount);
          continue;
        }
      }
      for (const m of members) reject(m, out.reason);
      const p = parentOf(need.perm, members.map((m) => m.op), first.i, liveOps);
      p.bulk = (heads.get(p.ts)?.size || 0) > 1;
      p.box = { op: first.op, out };
      parents.push(p);
    }

    //  ۲) همراه‌ها. سندِ نگه‌داشته‌ای که خودش در همین دسته پذیرفته شد (مثلاً لغوِ درستِ بعدی) کسی را نگه نمی‌دارد
    const held = held0.filter((p) => !live.some((x) => !rejected.has(x.i) && touchesDoc(p, x.op)));
    const all = parents.concat(held);
    if (all.length) {
      for (const x of live) {
        if (rejected.has(x.i) || (x.need && x.op.table === 'sales')) continue;   //  فاکتورِ دیگر سندِ خودش است
        if (all.some((p) => belongs(p, x.op))) reject(x, 'tx_rejected');
      }
    }
    if (!rejected.size) return next();

    for (const p of held0) if (p.dirty) await rememberHeld(p);

    //  ۳) صندوقِ تعارض: هر سندِ ردشده یک ردیف، با نشانی‌هایش (برای همراهِ دستهٔ بعدِ همین دستگاه)
    for (const p of parents) {
      const { op, out } = p.box;
      const detail = {
        ...out.detail,
        refs: [...new Set([p.sale, ...p.rows, ...p.items].filter(Boolean))].slice(0, 200),
        sale: p.sale || undefined, rows: [...p.rows].slice(0, 200), items: [...p.items].slice(0, 200),
        at: p.at || undefined, debtor: p.debtor || undefined, ts: p.ts || undefined, bulk: p.bulk || undefined,
      };
      await boxV1(ctx, op, deviceId, { box: out.box, detail });
    }

    return finish();

    /** ردشده‌ها از دسته بیرون و پاسخشان سرِ جای خودشان (‎lib/v1-reject.js‎)؛ بی ردشده همان دسته به v1 */
    function finish() {
      rejectOps(req, res, ops, rejected);
      return next();
    }
  } catch (err) { next(err); }
}

// ---------- v3 ِ بی tx: همراه‌ها با سندشان یک واحد ----------

/** نمای v1 ِ یک op ِ v3 برای همان قاعده‌های همراه (delta ⇒ update، شمارنده‌ها با نامِ v1، مهر = دیوارِ HLC) */
function v3View(op) {
  const f = isPlainObject(op.fields) ? op.fields : {};
  const fields = { ...f };
  if ('debtSettledMinor' in f) fields.debtSettled = f.debtSettledMinor;
  if ('returnedQtyMilli' in f) fields.returnedQty = f.returnedQtyMilli;
  return { table: op.table, row_id: op.row_id, type: op.kind === 'delta' ? 'update' : op.kind, fields, ts: wallOf(op.hlc) || 0 };
}

/**
 * برنامهٔ v3 ِ کهنه برای ثبتِ بزرگ‌تر از ۵۰ op ِ سند tx نمی‌گذاشت ⇒ سرور هر op را جدا اعمال
 * می‌کرد. این‌جا هر سندِ پولی (فاکتورِ تخفیف‌دار، لغو، برگشت) با همراه‌هایش در همان دسته — با
 * همان قاعدهٔ v1، «همان ثبت» = دیوارِ HLC ِ نزدیک — یک واحد می‌شود: همه یا هیچ.
 * @param units  واحدهای `push` ِ v3 (هر واحدِ بی tx یک قلم دارد)
 * @returns واحدهای تازه (واحدهای بی‌ربط دست‌نخورده، به همان ترتیب)
 */
function bundleV3(units, groups) {
  const entries = [];
  units.forEach((u, ui) => {
    if (u.tx || u.broken) return;
    for (const it of u.items) entries.push({ ui, it, views: it.ops.map(v3View) });
  });
  if (entries.length < 2) return units;
  const parents = [];
  const seenGroup = new Set();
  entries.forEach((e, k) => e.it.ops.forEach((op, j) => {
    let perm = null;
    if (op.table === 'sales' && op.kind === 'insert' && int(op.fields?.discountMinor) > 0) perm = 'sales.discount';
    else if (op.table === 'sales' && op.kind === 'cancel') perm = 'sales.cancel';
    else if (op.table === 'returns' && op.kind === 'insert') perm = 'returns.create';
    if (!perm) return;
    let members = [k];
    let views = [e.views[j]];
    const g = perm === 'returns.create' && groups ? groups.get(op) : null;
    if (g) {
      if (seenGroup.has(g)) return;
      seenGroup.add(g);
      members = [];
      views = [];
      entries.forEach((e2, k2) => e2.it.ops.forEach((o2, j2) => {
        if (groups.get(o2) === g) { members.push(k2); views.push(e2.views[j2]); }
      }));
    }
    const p = parentOf(perm, views, k, entries.flatMap((x) => x.views));
    p.tol = SAVE_HLC_MS;
    const near = new Set();
    for (const x of entries) for (const v of x.views) {
      const h = headKey(v);
      if (h && v.ts && Math.abs(v.ts - p.ts) <= SAVE_HLC_MS) near.add(h);
    }
    p.bulk = near.size > 1;
    p.members = members;
    parents.push(p);
  }));
  if (!parents.length) return units;
  const root = entries.map((_, k) => k);
  const find = (k) => (root[k] === k ? k : (root[k] = find(root[k])));
  const join = (a, b) => { const x = find(a); const y = find(b); if (x !== y) root[Math.max(x, y)] = Math.min(x, y); };
  for (const p of parents) {
    for (const m of p.members) join(p.members[0], m);
    entries.forEach((e, k) => { if (e.views.some((v) => belongs(p, v))) join(p.members[0], k); });
  }
  const byRoot = new Map();
  entries.forEach((e, k) => {
    const r = find(k);
    if (!byRoot.has(r)) byRoot.set(r, []);
    byRoot.get(r).push(e);
  });
  const place = new Map();
  const drop = new Set();
  for (const list of byRoot.values()) {
    if (list.length < 2) continue;
    const uis = list.map((e) => e.ui);
    for (const ui of uis) drop.add(ui);
    place.set(Math.min(...uis), { tx: '', bundled: true, items: list.map((e) => e.it).sort((a, b) => a.i - b.i) });
  }
  if (!place.size) return units;
  return units.flatMap((u, ui) => (place.has(ui) ? [place.get(ui)] : drop.has(ui) ? [] : [u]));
}

/**
 * نشانی‌های سندِ ردشدهٔ v3 برای صندوقش (`detail`) — همان شکلِ v1 — تا همراهِ بی tx ِ دستهٔ
 * بعدِ همین دستگاه هم رد شود (`holdV3Units`).
 */
function holdInfoV3(op, groups) {
  let perm = null;
  if (op.table === 'sales' && op.kind === 'insert') perm = 'sales.discount';
  else if (op.table === 'sales' && op.kind === 'cancel') perm = 'sales.cancel';
  else if (op.table === 'returns' && op.kind === 'insert') perm = 'returns.create';
  if (!perm) return {};
  const g = perm === 'returns.create' && groups ? groups.get(op) : null;
  const p = parentOf(perm, g ? g.rows.map((r) => v3View(r.op)) : [v3View(op)], 0, null);
  return {
    refs: [...new Set([p.sale, ...p.rows, ...p.items].filter(Boolean))].slice(0, 200),
    sale: p.sale || undefined, rows: [...p.rows].slice(0, 200), items: [...p.items].slice(0, 200),
    at: p.at || undefined, debtor: p.debtor || undefined, ts: p.ts || undefined,
  };
}

/**
 * v3 ِ بی tx (برنامهٔ کهنه): همراهِ سندی که در دستهٔ پیشینِ همین دستگاه رد شد ⇒ واحدش «شکسته»
 * (`tx_rejected`). همان قاعدهٔ `heldParents`؛ سندی که خودش در همین دسته دوباره آمده آزاد است.
 * @returns شمارِ واحدهای شکسته
 */
async function holdV3Units(ctx, deviceId, units) {
  //  صاحبِ دکان هرگز رد نمی‌شود و صندوقِ کسِ دیگری (حتی روی همان دستگاه) نگهش نمی‌دارد
  if (ctx.role === 'owner') return 0;
  const loose = units.filter((u) => !u.tx && !u.broken && u.items.every((it) => !it.v2));
  if (!loose.length) return 0;
  const views = loose.flatMap((u) => u.items.flatMap((it) => it.ops.map(v3View)));
  const held0 = await heldParents(ctx, deviceId, views, { tol: SAVE_HLC_MS });
  const held = held0.filter((p) => !views.some((v) => touchesDoc(p, v)));
  if (!held.length) return 0;
  let n = 0;
  for (const u of loose) {
    const vs = u.items.flatMap((it) => it.ops.map(v3View));
    if (vs.some((v) => held.some((p) => belongs(p, v)))) { u.broken = true; n++; }
  }
  return n;
}

/**
 * دنبالهٔ tx ای که سرش در دستهٔ پیشینِ همین دستگاه با سقف/تأیید رد شد (tx ِ بزرگ‌تر از یک
 * دسته): فقط صندوقِ باز، حداکثر یک روز، و نه وقتی سرِ آن سند از آن پس در دفترِ v3 نشسته.
 * @returns Set<tx>
 */
async function heldTxs(q, ctx, deviceId, txs) {
  if (!txs.length) return new Map();
  const rows = (await q.query(
    `SELECT detail->>'tx' AS tx, loser_op_id, table_name, row_id, detail->>'perm' AS perm FROM sync_v3_conflicts
      WHERE app='shop' AND account_kind='shop' AND account_id=$1 AND kind = ANY($2::text[]) AND status='open'
        AND loser_device=$3 AND at > $4 AND detail->>'tx' = ANY($5::text[])`,
    [ctx.accountId, HOLD_KINDS, deviceId, now() - HOLD_TTL_MS, txs]
  )).rows;
  if (!rows.length) return new Map();
  const ids = [...new Set(rows.map((r) => r.row_id))];
  const on = new Map((await q.query(
    `SELECT table_name, row_id, data->>'status' AS status FROM sync_v3_rows
      WHERE app='shop' AND account_kind='shop' AND account_id=$1 AND table_name IN ('sales','returns')
        AND row_id = ANY($2::text[]) AND deleted_at IS NULL`,
    [ctx.accountId, ids]
  )).rows.map((r) => [`${r.table_name}/${r.row_id}`, r.status]));
  const out = new Map();
  for (const r of rows) {
    const k = `${r.table_name}/${r.row_id}`;
    const landed = r.perm === 'sales.cancel' ? on.get(k) === 'cancelled' : on.has(k);
    if (landed) continue;
    if (!out.has(r.tx)) out.set(r.tx, new Set());
    out.get(r.tx).add(r.loser_op_id);
  }
  return out;
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
  returnDocKey, groupReturns, returnGroupsOf, judgeGroup, companionOf, bundleV3, HOLD_KINDS,
  returnsLockKey, returnedTotal, holdInfoV3, holdV3Units, heldTxs,
};
