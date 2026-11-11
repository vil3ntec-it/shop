'use strict';
/**
 * «درخواستِ تأیید» از راهِ دور — STF-02 (بخشِ ۶.۹ و ۱۰.۱).
 *
 * برگهٔ امضاشده (`approval-core.js`) فقط وقتی کار می‌کند که مدیر کنارِ صندوق باشد و PINش را روی **همان** دستگاه
 * بزند. این‌جا شاگرد از صندوق می‌پرسد و صاحب یا مدیر روی **دستگاهِ خودش** تأیید/رد می‌کند؛ تصمیم فقط در سرور است:
 *
 *   pending ──approve──▶ approved ──opِ همگام‌سازی──▶ used
 *      │  └──deny─────▶ denied
 *      └──cancel (درخواست‌کننده، از pending یا approved)──▶ cancelled
 *   pending/approved ِ گذشته از `expires_at` ⇒ «منقضی» (ستون نیست؛ `stateOf`).
 *
 * ⛔ قاعده‌ها:
 *   • بسته به دکان + کار (`perm`، همان PERMS ِ برگه) + سند (`doc` = جدول/شناسهٔ ردیف) + سقفِ درصد و مبلغ؛ opی که
 *     مقدارِ بیشتر یا سندِ دیگر بخواهد همان کدهای برگه را می‌گیرد (`approval_too_small` / `approval_mismatch`).
 *   • درخواست فقط وقتی ساخته می‌شود که تصمیمِ خودِ درخواست‌کننده برای همان مقدار «نیازمندِ تأیید» باشد:
 *     «مجاز» ⇒ ۴۰۹ `approval_not_needed`، «ممنوع» ⇒ ۴۰۳ با همان کدِ رد (`discount_not_allowed`…).
 *   • تأییدکننده عضوِ فعالِ صاحب/مدیر است، خودش را تأیید نمی‌کند (`approval_self`) و **خودش** همان کار را با همان
 *     مقدار «مجاز» دارد (`approver_not_allowed`) — هنگامِ تأیید و دوباره هنگامِ مصرف (مدیرِ برداشته‌شده یا کم‌اختیارشده).
 *   • عمر حداکثر ۱۵ دقیقه (`approval-core.TTL_MAX_MS`)؛ تأییدِ دیرتر از `expires_at` ⇒ ۴۰۹ `request_expired`.
 *   • یک‌بارمصرف: opی که `approval: {v:1, request:<id>}` بیاورد آن را `used` می‌کند (UPDATE ِ شرطی ⇒ دو op ِ هم‌زمان
 *     یکی می‌برد) و ردیفِ `shop_approvals` (nonce = `req_<id>`) سابقهٔ نهایی است؛ همان op_id ِ دوباره ⇒ تکرار، نه بازپخش.
 *   • بی‌خطرِ تکرار: (دکان، درخواست‌کننده، `key`) یکتاست؛ همان کلید با همان محتوا همان ردیف، با محتوای دیگر ۴۰۹.
 *     درخواستِ تازه برای همان کار و سند درخواستِ بازِ پیشینِ همان کاربر را «جایگزین‌شده» لغو می‌کند.
 *   • هر تغییرِ حال در همان تراکنش یک ردیفِ `audit_log` دارد (`approval_request.*`).
 *   • ⛔ کاری که پیش‌فرضِ نقشِ درخواست‌کننده برایش «ممنوع» است (تخفیف/لغوِ شاگرد و صندوق‌دار) فقط از Sync v3 می‌نشیند
 *     (`staff-approval.v1Guard` ⇒ `approval_needs_v3`: v1 ِ مشترک با پمپ خودش نقش را می‌سنجد). سرورِ بی Sync v3 هیچ راهی
 *     برای مصرفِ آن ندارد ⇒ ۴۰۹ `approval_needs_v3` همان دمِ پرسیدن، نه پس از تأییدِ بیهودهٔ مدیر و ردِ فاکتور.
 *   • درخواستِ تازه (نه تکرار) پس از commit یک پوش به صاحبان/مدیرانِ فعال (جز خودش) می‌فرستد ([notifyApprovers])؛
 *     بی‌انتظار، و شکستش کار را برنمی‌گرداند.
 *
 * ⛔ پیش‌فرض خاموش: بی پرچمِ `shop.approval_requests` همهٔ درها جز `/config` ۴۰۳ و opِ با `request` همان
 *    `approval_invalid` ِ امروز (برگهٔ بدشکل) را می‌گیرد — رفتارِ امروز بایت‌به‌بایت همان.
 */
const config = require('../config');
const core = require('./perm-core');
const slipCore = require('./approval-core');
const staffPerms = require('./staff-perms');
const audit = require('./audit');
const { flagOf } = require('./orders');
const { many, one, query, tx, newId, now } = require('../db');
const { badRequest, conflict, forbidden, notFound, tooMany } = require('../middleware/errors');
const maskCards = (t) => require('./ops-errors').maskCards(t);   //  S117: شمارهٔ کاملِ کارت ⇒ «[کارت]» پیش از دیتابیس و هشِ تکرار

const FLAG = 'shop.approval_requests';
const APPROVER_ROLES = Object.freeze(['owner', 'manager']);
const TTL_DEFAULT_MS = 10 * 60 * 1000;
const TTL_MIN_MS = 60 * 1000;
const TTL_MAX_MS = slipCore.TTL_MAX_MS;
/** درخواستِ بازِ هم‌زمانِ هر کاربر */
const MAX_PENDING = 10;
const NOTE_MAX = 200;
const KEY_RE = /^[A-Za-z0-9_-]{8,64}$/;
const DOC_RE = /^[A-Za-z][A-Za-z0-9_]{0,40}\/[A-Za-z0-9_.:-]{1,120}$/;
const ID_RE = /^apr_[0-9a-f]{24}$/;
const LINE_BREAK = /[\r\n]/;
/** کارهایی که بی مقدار معنا ندارند */
const NEEDS_VALUE = new Set(['sales.discount', 'returns.create', 'sales.credit', 'sales.price', 'sales.free_line']);   //  قلمِ آزاد: تصمیمِ ۱-۱۱
const STATUSES = Object.freeze(['open', 'pending', 'approved', 'denied', 'cancelled', 'used', 'expired', 'all']);

const enabled = (shopId) => flagOf(shopId, FLAG);

/** تراکنش + سابقه در همان تراکنش؛ زنجیرهٔ هشِ سابقه (STF-09) پس از commit */
async function inTx(fn) {
  const done = [];
  const out = await tx((c) => fn(c, async (entry) => { done.push(await audit.logIn(c, entry)); }));
  for (const d of done) await d.afterCommit();
  return out;
}
const isApprover = (role) => APPROVER_ROLES.includes(role);
const optInt = (v) => (v === null || v === undefined ? null : Number(v));

/** حالِ دیده‌شده: pending/approved ِ گذشته از زمان ⇒ expired */
function stateOf(r, t = now()) {
  if ((r.status === 'pending' || r.status === 'approved') && t > Number(r.expires_at)) return 'expired';
  return r.status;
}

function shape(r, t = now()) {
  return {
    id: r.id, perm: r.perm, doc: r.doc, pctBp: optInt(r.pct_bp), amount: optInt(r.amount), reason: r.reason,
    status: stateOf(r, t), requesterId: r.requester_id, requesterName: r.requester_name || undefined,
    createdAt: Number(r.created_at), expiresAt: Number(r.expires_at),
    decidedBy: r.decided_by || null, decidedAt: optInt(r.decided_at), note: r.decision_note || '',
    //  نام و نقشِ تأییدکننده برای صندوق (برگشت نقشِ تأییدکننده را می‌سنجد، `ReturnFlow.canApprove`) — فقط در get/list
    deciderName: r.decider_name || undefined, deciderRole: r.decider_role || undefined,
    usedOpId: r.used_op_id || null, usedAt: optInt(r.used_at),
  };
}

const DECIDER_COLS = 'd.name AS decider_name, dm.role AS decider_role';
const DECIDER_JOIN = `LEFT JOIN users d ON d.id = r.decided_by
      LEFT JOIN shop_members dm ON dm.shop_id = r.shop_id AND dm.user_id = r.decided_by AND dm.status = 'active'`;

/** درخواستِ عددی برای `PermCore.evaluate` — همان شکلِ `approval-core.evalReq` */
function evalReq(r) {
  const out = {};
  const bp = optInt(r.pct_bp);
  const amount = optInt(r.amount);
  if (bp !== null) out.pct = bp / 100;
  if (amount !== null) out.amount = amount;
  return out;
}

/** نقش و لایه‌های یک عضوِ فعال — یا null */
async function policyOf(c, shopId, userId) {
  const m = (await c.query(
    `SELECT role FROM shop_members WHERE shop_id=$1 AND user_id=$2 AND status='active' LIMIT 1`, [shopId, String(userId || '')]
  )).rows[0];
  if (!m) return null;
  return { role: m.role, layers: await staffPerms.layersFor(shopId, m.role, userId, (s, p) => c.query(s, p)) };
}

function intIn(v, lo, hi, code) {
  if (v === undefined || v === null) return null;
  if (!Number.isSafeInteger(v) || v < lo || v > hi) throw badRequest('مقدار نادرست است', code);
  return v;
}

function parseBody(body) {
  const b = body && typeof body === 'object' && !Array.isArray(body) ? body : {};
  const key = typeof b.key === 'string' ? b.key : '';
  if (!KEY_RE.test(key)) throw badRequest('کلیدِ درخواست نادرست است', 'request_invalid');
  if (!slipCore.PERMS.includes(b.perm)) throw badRequest('این کار تأییدِ از راهِ دور ندارد', 'bad_perm');
  if (typeof b.doc !== 'string' || !DOC_RE.test(b.doc)) throw badRequest('سند نادرست است', 'request_invalid');
  const pctBp = intIn(b.pctBp, 0, 10000, 'bad_value');
  const amount = intIn(b.amount, 0, 1e15, 'bad_value');
  if (NEEDS_VALUE.has(b.perm) && pctBp === null && amount === null) throw badRequest('مقدارِ این کار لازم است', 'bad_value');
  const reason = typeof b.reason === 'string' ? b.reason.trim() : '';
  if (!reason || reason.length > slipCore.MAX_REASON || LINE_BREAK.test(reason)) throw badRequest('دلیل لازم است', 'bad_reason');
  const ttl = b.ttlMs === undefined ? TTL_DEFAULT_MS : intIn(b.ttlMs, TTL_MIN_MS, TTL_MAX_MS, 'bad_time');
  return { key, perm: b.perm, doc: b.doc, pctBp, amount, reason, ttl };
}

const same = (r, p) => r.perm === p.perm && r.doc === p.doc && optInt(r.pct_bp) === p.pctBp
  && optInt(r.amount) === p.amount && r.reason === p.reason;

/**
 * شاگرد می‌پرسد. `actor` = { userId, role }. ⇒ { request, duplicate }
 */
async function create(shopId, actor, body, { deviceId = '' } = {}) {
  const p = parseBody(body);
  const layers = await staffPerms.layersFor(shopId, actor.role, actor.userId);
  const d = core.evaluate(actor.role, layers, p.perm, evalReq({ pct_bp: p.pctBp, amount: p.amount }));
  if (d.decision === 'allow') throw conflict('این کار برای شما تأیید نمی‌خواهد', 'approval_not_needed');
  if (d.decision === 'deny') {
    const code = require('./staff-approval').DENY_CODE[p.perm] || 'perm_denied';
    throw forbidden('این کار برای نقشِ شما ممنوع است', code);
  }
  if (!config.syncV3.enabled && core.defaultMode(actor.role, p.perm) === 'deny') {
    throw conflict('تأییدِ این کار برای نقشِ شما فقط با همگام‌سازیِ نسخهٔ ۳ می‌نشیند و این سرور آن را ندارد؛ از مدیر بخواهید خودش ثبت کند', 'approval_needs_v3');
  }
  const out = await inTx(async (c, log) => {
    await c.query('SELECT pg_advisory_xact_lock(hashtext($1))', [`approval-requests:${shopId}:${actor.userId}`]);
    const prev = (await c.query(
      'SELECT * FROM shop_approval_requests WHERE shop_id=$1 AND requester_id=$2 AND request_key=$3', [shopId, actor.userId, p.key]
    )).rows[0];
    if (prev) {
      if (!same(prev, p)) throw conflict('این کلید برای درخواستِ دیگری به کار رفته', 'request_key_reused');
      return { request: shape(prev), duplicate: true };
    }
    const t = now();
    const open = (await c.query(
      `SELECT COUNT(*)::int AS n FROM shop_approval_requests
        WHERE shop_id=$1 AND requester_id=$2 AND status='pending' AND expires_at >= $3`, [shopId, actor.userId, t]
    )).rows[0].n;
    if (open >= MAX_PENDING) throw tooMany(`حداکثر ${MAX_PENDING} درخواستِ باز؛ منتظرِ پاسخ بمانید`, 'too_many_pending');
    //  همان کار روی همان سند: درخواستِ بازِ پیشینِ همین کاربر جایگزین می‌شود (مثلاً تخفیفِ بیشتر)
    await c.query(
      `UPDATE shop_approval_requests SET status='cancelled', decided_at=$5, decision_note='superseded'
        WHERE shop_id=$1 AND requester_id=$2 AND perm=$3 AND doc=$4 AND status='pending'`,
      [shopId, actor.userId, p.perm, p.doc, t]
    );
    const row = (await c.query(
      `INSERT INTO shop_approval_requests (id, shop_id, request_key, requester_id, requester_device, perm, doc, pct_bp, amount,
                                           reason, status, created_at, expires_at)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,'pending',$11,$12) RETURNING *`,
      [newId('apr'), shopId, p.key, actor.userId, String(deviceId || '').slice(0, 64), p.perm, p.doc, p.pctBp, p.amount,
        p.reason, t, t + p.ttl]
    )).rows[0];
    await log({
      shopId, userId: actor.userId, action: 'approval_request.create', targetType: 'approval_request', targetId: row.id,
      detail: { perm: p.perm, doc: p.doc, pctBp: p.pctBp, amount: p.amount, expiresAt: t + p.ttl },
    });
    return { request: shape(row, t), duplicate: false };
  });
  if (!out.duplicate) notifyApprovers(shopId, actor.userId, out.request);
  return out;
}

const PERM_LABEL = Object.freeze({
  'sales.discount': 'تخفیف', 'sales.cancel': 'لغوِ فاکتور', 'returns.create': 'برگشت',
  'sales.credit': 'فروشِ قرضی', 'sales.price': 'تغییرِ قیمت',   //  STF-03
  'sales.free_line': 'قلمِ آزاد',   //  POS-10، تصمیمِ ۱-۱۱ (۲)
});

/**
 * خبرِ پوش به صاحبان و مدیرانِ فعالِ همین دکان (جز خودِ درخواست‌کننده) — همان `push.sendTo` ِ هشدارِ حساب‌پی.
 * ⛔ بی‌انتظار و بی‌اثر بر کار: شکستِ پوش (یا خاموش بودنش) درخواست را برنمی‌گرداند؛ صندوقِ تأییدِ برنامه هم می‌پرسد.
 * ⛔ فقط آگاهی است: تأیید هنوز با `POST /:id/approve` ِ دستگاهِ خودِ مدیر و سنجشِ اختیارش در سرور است.
 */
function notifyApprovers(shopId, requesterId, request) {
  const T = require('./text-core');
  const fa = (n) => T.displayDigits(String(n), 'fa');
  return (async () => {
    const ids = (await query(
      `SELECT user_id FROM shop_members WHERE shop_id=$1 AND status='active' AND role = ANY($2::text[]) AND user_id <> $3`,
      [shopId, APPROVER_ROLES, String(requesterId || '')]
    )).rows.map((r) => r.user_id);
    if (!ids.length) return { sent: 0 };
    const who = (await one('SELECT name FROM users WHERE id=$1', [requesterId]))?.name || 'کارمند';
    const money = request.amount === null ? '' : `${fa((request.amount / 100).toLocaleString('en-US')).split(',').join('٬')} افغانی`;
    const pct = request.pctBp === null ? '' : `${fa(request.pctBp / 100)}٪`;
    const what = [PERM_LABEL[request.perm] || request.perm, [pct, money].filter(Boolean).join(' — ')].filter(Boolean).join(' ');
    return require('./push').sendTo({ shopId, app: 'shop', onlyUserIds: ids }, {
      title: 'درخواستِ تأیید', body: `${who}: ${what}. پاسخ در «درخواست‌های تأیید» ِ برنامه.`,
      data: { type: 'approval_request', id: request.id }, tag: `apr:${request.id}`,
      ttlSeconds: Math.max(60, Math.round((request.expiresAt - now()) / 1000)),
    });
  })().catch((err) => { console.warn('[approval-requests] خبر به مدیر نرسید:', err.message); return null; });
}

/** صاحب و مدیر همه را می‌بینند؛ بقیه فقط درخواست‌های خودشان. `status=open` (پیش‌فرض) = در انتظار و نگذشته. */
async function list(shopId, actor, { status = 'open', limit = 100 } = {}) {
  const want = STATUSES.includes(status) ? status : 'open';
  const cap = Math.min(Math.max(1, Number(limit) || 100), 200);
  const all = isApprover(actor.role);
  const t = now();
  const rows = await many(
    `SELECT r.*, u.name AS requester_name, ${DECIDER_COLS} FROM shop_approval_requests r LEFT JOIN users u ON u.id = r.requester_id
      ${DECIDER_JOIN}
      WHERE r.shop_id=$1 AND ($2::boolean OR r.requester_id=$3)
        AND ($4 <> 'open' OR (r.status='pending' AND r.expires_at >= $5))
      ORDER BY r.created_at DESC LIMIT $6`,
    [shopId, all, String(actor.userId || ''), want, t, want === 'open' || want === 'all' ? cap : 500]
  );
  const out = rows.map((r) => shape(r, t));
  return { requests: (want === 'open' || want === 'all' ? out : out.filter((r) => r.status === want)).slice(0, cap) };
}

/** فقط درخواست‌کننده یا صاحب/مدیرِ همان دکان؛ بقیه همان ۴۰۴ (بودنِ درخواست هم نشت نمی‌کند) */
async function get(shopId, actor, id) {
  const r = ID_RE.test(String(id || '')) ? await one(
    `SELECT r.*, u.name AS requester_name, ${DECIDER_COLS} FROM shop_approval_requests r LEFT JOIN users u ON u.id = r.requester_id
      ${DECIDER_JOIN}
      WHERE r.id=$1 AND r.shop_id=$2`, [id, shopId]
  ) : null;
  if (!r || (r.requester_id !== actor.userId && !isApprover(actor.role))) throw notFound('درخواست پیدا نشد', 'request_not_found');
  return { request: shape(r) };
}

async function lockRow(c, shopId, id) {
  if (!ID_RE.test(String(id || ''))) return null;
  return (await c.query('SELECT * FROM shop_approval_requests WHERE id=$1 AND shop_id=$2 FOR UPDATE', [id, shopId])).rows[0] || null;
}

/**
 * صاحب یا مدیر روی دستگاهِ خودش تأیید/رد می‌کند. ⇒ { request, duplicate }
 * @param decision 'approve' | 'deny'
 */
async function decide(shopId, actor, id, decision, { note = '' } = {}) {
  if (!isApprover(actor.role)) throw forbidden('فقط صاحب دکان یا مدیر تأیید می‌کند', 'not_approver');
  const text = maskCards(typeof note === 'string' ? note.trim() : '');
  if (text.length > NOTE_MAX || LINE_BREAK.test(text)) throw badRequest('یادداشت نادرست است', 'bad_reason');
  const target = decision === 'approve' ? 'approved' : 'denied';
  return inTx(async (c, log) => {
    const r = await lockRow(c, shopId, id);
    if (!r) throw notFound('درخواست پیدا نشد', 'request_not_found');
    if (r.requester_id === actor.userId) throw forbidden('کسی کارِ خودش را تأیید نمی‌کند', 'approval_self');
    const t = now();
    if (r.status !== 'pending') {
      //  همان تصمیمِ همان آدم دوباره (پاسخ گم شد) ⇒ همان ردیف
      const again = r.decided_by === actor.userId && (r.status === target || (target === 'approved' && r.status === 'used'));
      if (again) return { request: shape(r, t), duplicate: true };
      throw conflict('این درخواست بسته شده است', 'request_closed');
    }
    if (t > Number(r.expires_at)) throw conflict('زمانِ این درخواست گذشته است', 'request_expired');
    if (target === 'approved') {
      const me = await policyOf(c, shopId, actor.userId);
      const d = me && isApprover(me.role) ? core.evaluate(me.role, me.layers, r.perm, evalReq(r)) : { decision: 'deny' };
      if (d.decision !== 'allow') throw forbidden('خودِ شما این کار را با این مقدار مجاز ندارید', 'approver_not_allowed');
    }
    const row = (await c.query(
      `UPDATE shop_approval_requests SET status=$3, decided_by=$4, decided_at=$5, decision_note=$6
        WHERE id=$1 AND shop_id=$2 AND status='pending' RETURNING *`,
      [r.id, shopId, target, actor.userId, t, text]
    )).rows[0];
    await log({
      shopId, userId: actor.userId, action: `approval_request.${decision === 'approve' ? 'approve' : 'deny'}`,
      targetType: 'approval_request', targetId: r.id,
      detail: { perm: r.perm, doc: r.doc, pctBp: optInt(r.pct_bp), amount: optInt(r.amount), requester: r.requester_id },
    });
    return { request: shape(row, t), duplicate: false };
  });
}

/** درخواست‌کننده پس می‌گیرد (در انتظار یا تأییدشدهٔ مصرف‌نشده). ⇒ { request, duplicate } */
async function cancel(shopId, actor, id) {
  return inTx(async (c, log) => {
    const r = await lockRow(c, shopId, id);
    if (!r || r.requester_id !== actor.userId) throw notFound('درخواست پیدا نشد', 'request_not_found');
    const t = now();
    if (r.status === 'cancelled') return { request: shape(r, t), duplicate: true };
    if (r.status !== 'pending' && r.status !== 'approved') throw conflict('این درخواست بسته شده است', 'request_closed');
    const row = (await c.query(
      `UPDATE shop_approval_requests SET status='cancelled', decision_note=CASE WHEN status='pending' THEN 'withdrawn' ELSE decision_note END,
              decided_at=COALESCE(decided_at, $3)
        WHERE id=$1 AND shop_id=$2 RETURNING *`, [r.id, shopId, t]
    )).rows[0];
    await log({
      shopId, userId: actor.userId, action: 'approval_request.cancel', targetType: 'approval_request', targetId: r.id,
      detail: { perm: r.perm, doc: r.doc, was: r.status },
    });
    return { request: shape(row, t), duplicate: false };
  });
}

/** ارجاعِ op به درخواست: `{v:1, request:'apr_…'}` */
function isRef(slip) {
  return slip !== null && typeof slip === 'object' && !Array.isArray(slip) && typeof slip.request === 'string';
}

const dbq = { query: (s, p) => query(s, p) };

/**
 * opِ همگام‌سازی که به درخواستِ تأییدشده ارجاع می‌دهد — همان قراردادِ `staff-approval.acceptSlip`:
 * null یعنی پذیرفته و مصرف شد؛ وگرنه کدِ خطا. `q` = کلاینتِ تراکنشِ v3 (مصرف درونِ همان savepoint) یا null (v1).
 */
async function accept(q, ctx, need, ref, { opId, at, deviceId, path }) {
  const c = q || dbq;
  if (!(await enabled(ctx.accountId))) return 'approval_invalid';
  if (ref.v !== 1 || !ID_RE.test(ref.request)) return 'approval_invalid';
  const r = (await c.query('SELECT * FROM shop_approval_requests WHERE id=$1 AND shop_id=$2', [ref.request, ctx.accountId])).rows[0];
  if (!r) return 'approval_invalid';
  if (r.requester_id !== String(ctx.userId || '')) return 'approval_mismatch';
  const op = String(opId || '');
  if (r.status === 'used') return r.used_op_id === op ? null : 'approval_replayed';
  if (r.status === 'pending') return 'approval_pending';
  if (r.status !== 'approved') return 'approval_not_approved';
  const when = Number.isSafeInteger(at) && at > 0 ? at : now();
  const cover = slipCore.covers(
    { perm: r.perm, doc: r.doc, pct_bp: optInt(r.pct_bp), amount: optInt(r.amount), iat: Number(r.created_at), exp: Number(r.expires_at) },
    { perm: need.perm, doc: need.doc, pct_bp: need.pct_bp, amount: need.amount, at: when }
  );
  if (cover) return cover;
  //  تأییدکننده هنوز عضوِ فعالِ صاحب/مدیر است و همین مقدار را مجاز دارد
  const approver = await policyOf(c, ctx.accountId, r.decided_by);
  if (!approver || !isApprover(approver.role)
    || core.evaluate(approver.role, approver.layers, r.perm, evalReq(r)).decision !== 'allow') return 'approver_not_allowed';
  const t = now();
  const won = (await c.query(
    `UPDATE shop_approval_requests SET status='used', used_op_id=$3, used_at=$4
      WHERE id=$1 AND shop_id=$2 AND status='approved' RETURNING id`, [r.id, ctx.accountId, op, t]
  )).rows[0];
  if (!won) {
    const now2 = (await c.query('SELECT status, used_op_id FROM shop_approval_requests WHERE id=$1', [r.id])).rows[0];
    return now2 && now2.status === 'used' && now2.used_op_id === op ? null : 'approval_replayed';
  }
  await c.query(
    `INSERT INTO shop_approvals (shop_id, nonce, op_id, doc, perm, pct_bp, amount, approver_id, requester_id,
                                 key_id, device_id, path, reason, issued_at, used_at)
     VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,'remote',$10,$11,$12,$13,$14) ON CONFLICT (shop_id, nonce) DO NOTHING`,
    [ctx.accountId, `req_${r.id}`, op, need.doc, need.perm, optInt(r.pct_bp),
      optInt(r.amount) ?? (need.record ? need.record.amount : null), r.decided_by, r.requester_id,
      String(deviceId || '').slice(0, 64), ['v1', 'v2', 'v3'].includes(path) ? path : 'v3', r.reason, Number(r.decided_at), t]
  );
  return null;
}

module.exports = {
  FLAG, APPROVER_ROLES, TTL_DEFAULT_MS, TTL_MIN_MS, TTL_MAX_MS, MAX_PENDING,
  enabled, stateOf, create, list, get, decide, cancel, isRef, accept, notifyApprovers,
};
