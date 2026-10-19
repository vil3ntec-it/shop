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

// ---------- راهِ v1 ----------

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
 * میان‌افزارِ `POST /api/sync/v1/push` — فقط دکان. پمپ (و هر حسابِ دیگر)
 * بی هیچ کاری رد می‌شود.
 *
 * opی که تصمیمش allow نیست از دسته بیرون می‌آید و پاسخش در جای خودش
 * «rejected» است. نقشی که پیش‌فرضش deny است (شاگرد برای تخفیف و لغو) به
 * همان `authorizeOp`ِ امروز سپرده می‌شود — رفتار و کدِ خطایش عوض نمی‌شود.
 */
async function v1Guard(req, res, next) {
  const ctx = req.sync;
  if (!ctx || ctx.app !== 'shop' || ctx.accountKind !== 'shop' || !ctx.accountId) return next();
  try {
    const ops = Array.isArray(req.body?.ops) ? req.body.ops : null;
    if (!ops || !ops.length) return next();
    const { validateOp } = require('./sync-v1');
    const cands = [];
    ops.forEach((raw, i) => {
      const v = validateOp('shop', raw);
      if (!v.ok) return;                       //  خودِ v1 ردش می‌کند، با دلیلِ خودش
      const need = needOfV1(v.op);
      if (need && core.defaultMode(ctx.role, need.perm) !== 'deny') cands.push({ i, op: v.op, need });
    });
    if (!cands.length) return next();
    const deviceId = String(req.body?.device_id || req.body?.deviceId || '').slice(0, 64);
    const rejected = new Map();
    for (const { i, op, need } of cands) {
      const slip = isPlainObject(op.fields) && isPlainObject(op.fields.approval) ? op.fields.approval : null;
      const out = await judge(null, ctx, need, slip, {
        //  opِ بی زمان (برنامهٔ خیلی قدیمی) ⇒ فقط سنجشِ زمان کنار می‌رود؛ برگه هنوز یک‌بارمصرف و بسته به سند است
        opId: op.op_id, at: Number(op.ts) > 0 ? Number(op.ts) : undefined, deviceId, path: 'v1',
      });
      if (!out) continue;
      rejected.set(i, { op_id: String(op.op_id || ''), status: 'rejected', reason: out.reason });
      await boxV1(ctx, op, deviceId, out);
    }
    if (!rejected.size) return next();
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
};
