'use strict';
/**
 * اعتماد، شکایت و تخلف در بازارچه — هستهٔ سرور (v3 §۳۸.۶؛ سناریوی ABUSE-01).
 * شرحِ جدول‌ها در ‎migrations/100_market_trust_cases.sql‎ و ‎101_market_trust_sanctions.sql‎.
 *
 *   پرچم ──── `market.trust` (scope = شناسهٔ دکان یا `all`). ⛔ پیش‌فرض خاموش ⇒
 *             درهای مشتری و دکان ۴۰۴ و هیچ ردیفی ساخته نمی‌شود. درِ مدیر به پرچم
 *             بسته نیست تا برداشتنِ جریمه پس از خاموش کردنِ پرچم هم ممکن بماند.
 *   مشتری ─── شکایت از سفارش/دکان و گزارشِ کالای ویترین فقط با شمارهٔ تأییدشده
 *             (بلیتِ ویترین)؛ سفارش فقط اگر به همان شماره ثبت شده (خریدِ واقعی).
 *             پیگیری با توکنِ حدس‌نزدنی (HMAC ِ دکان+پرونده) — بی بلیت.
 *   دکان ──── پرونده‌های خودش (shop_id فقط از عضویتِ توکن)، پاسخ، اعتراض.
 *   مدیر ──── صفِ رسیدگی، مسئول، تصمیم با دلیلِ نوشته، جریمهٔ برگشت‌پذیر، رأیِ اعتراض.
 *
 * ⛔ هویتِ شاکی هرگز به دکان نمی‌رسد: نمای دکان از فهرستِ سفید ساخته می‌شود و
 *    شماره/IP اصلاً ذخیره نمی‌شوند (فقط HMAC برای سقف و تکرار). مدرکِ عکس فقط
 *    برای مدیر است؛ دکان شمارش را می‌بیند.
 * ⛔ برچسبِ خودکار (`same_text`، `same_ip`) نشانه است نه حکم (§۳۸.۶ بندِ ۲).
 * ⛔ متنِ شکایت/پاسخ در لاگ و سابقهٔ عملیات نوشته نمی‌شود.
 */
const crypto = require('crypto');
const express = require('express');
const config = require('../config');
const { one, many, newId, now, tx } = require('../db');
const chatMedia = require('./chat-media');
const { ApiError, badRequest, notFound, conflict } = require('../middleware/errors');

const FLAG_KEY = 'market.trust';
/** دسته ⇐ شدتِ پیش‌فرض (مدیر هنگامِ تصمیم می‌تواند عوض کند). */
const COMPLAINT = Object.freeze({
  not_delivered: 'medium', wrong_item: 'medium', damaged: 'medium', overcharged: 'medium',
  fraud: 'high', rude: 'low', other: 'low',
});
const REPORT = Object.freeze({
  counterfeit: 'high', prohibited: 'high', misleading: 'medium', offensive: 'medium',
  wrong_price: 'low', other: 'low',
});
const SEVERITIES = ['low', 'medium', 'high'];
const SANCTIONS = ['warning', 'hide_listing', 'suspend_storefront'];
const STATUSES = ['open', 'in_review', 'decided', 'appealed', 'closed'];
const MAX_BODY = 2000;
const MAX_RESPONSES = 20;
const SAME_TEXT_MIN = 15;
const DAY = 24 * 60 * 60 * 1000;
const SLUG_RE = /^[a-z0-9][a-z0-9-]{1,30}[a-z0-9]$/;
const CASE_RE = /^tc_[0-9a-f]{24}$/;
const EV_RE = /^tev_[0-9a-f]{24}$/;
const SAN_RE = /^tsn_[0-9a-f]{24}$/;
const TOKEN_RE = /^(tc_[0-9a-f]{24})\.([A-Za-z0-9_-]{32})$/;
const PRODUCT_ID_RE = /^[A-Za-z0-9_.:-]{1,80}$/;
const CLIENT_ID_RE = /^[A-Za-z0-9_.:-]{8,128}$/;

// ════════════════════════════════════════════════════════════════════
//  کمک‌ها
// ════════════════════════════════════════════════════════════════════

const str = (v) => (typeof v === 'string' ? v.trim() : '');
const obj = (v) => (v && typeof v === 'object' && !Array.isArray(v) ? v : {});
const n = (x) => (x == null ? null : Number(x));
const asJson = (x, def) => {
  if (x && typeof x === 'object') return x;
  try { return JSON.parse(x); } catch { return def; }
};
const sha256 = (x) => crypto.createHash('sha256').update(x).digest('hex');
const has = (o, k) => Object.prototype.hasOwnProperty.call(o, k);
const gone = () => notFound('این پرونده پیدا نشد', 'case_not_found');

/** متن: نویسه‌های کنترلی می‌روند؛ بلندتر از سقف ⇒ خطا، نه بریدنِ بی‌صدا. */
function cleanText(raw, max = MAX_BODY) {
  const t = String(raw == null ? '' : raw).replace(/[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F]/g, '').trim();
  if (t.length > max) throw badRequest(`متن خیلی بلند است — حداکثر ${max} نویسه`, 'text_too_long');
  return t;
}

/** برای تشخیصِ «متنِ یکسان»: حروفِ عربی/فارسی یکی، فاصله‌ها یکی، نشانه‌ها بی‌اثر. */
function normalize(t) {
  return t.toLowerCase()
    .replace(/[يى]/g, 'ی').replace(/ك/g, 'ک').replace(/[ۀة]/g, 'ه').replace(/[أإآ]/g, 'ا')
    .replace(/[ً-ٟـ‌‍]/g, '')
    .replace(/[^\p{L}\p{N}]+/gu, ' ').trim();
}

function maskPhone(p) {
  const s = String(p || '');
  if (s.length <= 7) return '•'.repeat(s.length);
  return s.slice(0, 4) + '•'.repeat(s.length - 7) + s.slice(-3);
}

function intIn(v, { min = 0, max = Number.MAX_SAFE_INTEGER, def = 0 } = {}) {
  if (v === undefined || v === null || v === '') return def;
  const x = Number(v);
  if (!Number.isSafeInteger(x) || x < min || x > max) throw badRequest('عدد در بازهٔ مجاز نیست', 'bad_number');
  return x;
}

// ── کلید، HMAC و توکن ─────────────────────────────────────────────

function secretKey() {
  const secret = config.secrets.api || config.secrets.jwt;
  if (!secret) throw new ApiError(503, 'trust_unavailable', 'رسیدگی به شکایت هنوز روی این سرور آماده نیست');
  return crypto.createHash('sha256').update(`market-trust\n${secret}`).digest();
}
const mac = (label, x) => crypto.createHmac('sha256', secretKey()).update(`${label}\n${x}`).digest('base64url');
const reporterKeyOf = (phone) => mac('reporter', phone);
const ipKeyOf = (ip) => (ip ? mac('ip', ip) : '');

function tokenOf(shopId, caseId) {
  return `${caseId}.${mac('case', `${shopId}\n${caseId}`).slice(0, 32)}`;
}

/** شناسهٔ پرونده از توکن — یا null (زمان‌ثابت؛ توکنِ دکانِ دیگر هم null). */
function caseIdOf(shopId, token) {
  const m = TOKEN_RE.exec(String(token || ''));
  if (!m) return null;
  const a = Buffer.from(m[2]);
  const b = Buffer.from(mac('case', `${shopId}\n${m[1]}`).slice(0, 32));
  return a.length === b.length && crypto.timingSafeEqual(a, b) ? m[1] : null;
}

// ── پرچم ─────────────────────────────────────────────────────────

async function flagOf(shopId) {
  return require('./orders').flagOf(shopId, FLAG_KEY);
}

// ════════════════════════════════════════════════════════════════════
//  تاریخچه و نماها
// ════════════════════════════════════════════════════════════════════

/**
 * زمانِ رویداد، اکیداً صعودی در این فرایند. تاریخچه با `created_at, id` خوانده می‌شود و شناسه تصادفی است؛
 * دو رویدادِ یک تراکنش (تصمیم و جریمه، رأیِ اعتراض و برداشتنِ جریمه) در یک میلی‌ثانیه جابه‌جا دیده می‌شدند.
 */
let lastEventAt = 0;
function eventAt() {
  lastEventAt = Math.max(now(), lastEventAt + 1);
  return lastEventAt;
}

async function addEvent(c, row, actor, actorId, type, body = '', data = {}) {
  await c.query(
    `INSERT INTO trust_case_events (id, case_id, shop_id, actor, actor_id, type, body, data, created_at)
     VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9)`,
    [newId('tce'), row.id, row.shop_id, actor, actorId || '', type, body || '', JSON.stringify(data || {}), eventAt()]
  );
}

const eventsOf = (caseId) => many(
  'SELECT * FROM trust_case_events WHERE case_id=$1 ORDER BY created_at, id', [caseId]
);
const evidenceOf = (caseId) => many(
  'SELECT id, mime, size, created_at FROM trust_evidence WHERE case_id=$1 ORDER BY created_at, id', [caseId]
);
const sanctionsOfCase = (caseId) => many(
  'SELECT * FROM trust_sanctions WHERE case_id=$1 ORDER BY created_at, id', [caseId]
);

function sanctionView(s, { admin = false } = {}) {
  return {
    id: s.id, caseId: s.case_id, kind: s.kind, productId: s.product_id || null, reason: s.reason,
    createdAt: n(s.created_at), active: s.lifted_at == null,
    liftedAt: n(s.lifted_at), liftReason: s.lift_reason || '',
    ...(admin ? { shopId: s.shop_id, createdBy: s.created_by, liftedBy: s.lifted_by || '' } : {}),
  };
}

function base(row) {
  return {
    kind: row.kind, subject: row.subject, orderNo: n(row.order_no), productId: row.product_id || null,
    category: row.category, status: row.status, createdAt: n(row.created_at), updatedAt: n(row.updated_at),
  };
}

/**
 * نمای دکان از همان پایه، بی `orderNo`: دکان سفارش‌هایش را با نام و شمارهٔ
 * مشتری دارد ⇒ شمارهٔ سفارش شاکی را می‌شناساند (بازبینیِ حریم). دکان فقط
 * می‌بیند «دربارهٔ یک سفارش» (`subject`).
 */
function shopBase(row) {
  const { orderNo, ...rest } = base(row);   // eslint-disable-line no-unused-vars
  return rest;
}

/** نتیجه از دیدِ بیرون: رأیِ اعتراض بر تصمیمِ نخست جلو می‌افتد. */
function verdict(row) {
  if (row.appeal_outcome === 'reversed') return 'reversed';
  return row.outcome || null;
}

const CUSTOMER_EVENTS = new Set(['filed', 'evidence_added', 'shop_response', 'assigned', 'decided', 'appealed', 'appeal_decided']);

/** نمای شاکی: نه دلیلِ داخلیِ مدیر، نه جریمه، نه برچسب؛ پاسخِ دکان آری. */
function customerView(row, events, evidence) {
  return {
    token: tokenOf(row.shop_id, row.id), ...base(row), outcome: verdict(row),
    evidence: evidence.map((e) => ({ id: e.id, mime: e.mime, size: Number(e.size), createdAt: n(e.created_at) })),
    history: events.filter((e) => CUSTOMER_EVENTS.has(e.type)).map((e) => ({
      type: e.type, at: n(e.created_at),
      ...(e.type === 'shop_response' ? { body: e.body } : {}),
      ...(e.type === 'decided' || e.type === 'appeal_decided' ? { outcome: asJson(e.data, {}).outcome || null } : {}),
    })),
  };
}

/**
 * نمای دکان — فقط از فهرستِ سفید. ⛔ هیچ چیزی از شاکی (شماره، نشانه، HMAC،
 * توکن، IP، برچسبِ خودکار، شمارهٔ سفارش) و هیچ شناسهٔ مدیری این‌جا نیست.
 */
function shopView(row, events, evidenceCount, sanctions) {
  const deadline = row.decided_at == null ? null : Number(row.decided_at) + config.trust.appealDays * DAY;
  return {
    id: row.id, ...shopBase(row), severity: row.severity, body: row.body,
    outcome: row.outcome || null, decisionReason: row.decision_reason || '', decidedAt: n(row.decided_at),
    appeal: row.appealed_at == null ? null : {
      body: row.appeal_body, at: n(row.appealed_at), outcome: row.appeal_outcome || null,
      reason: row.appeal_reason || '', decidedAt: n(row.appeal_decided_at),
    },
    canAppeal: row.status === 'decided' && row.outcome === 'upheld' && deadline !== null && now() <= deadline,
    appealDeadline: row.outcome === 'upheld' ? deadline : null,
    evidenceCount: Number(evidenceCount || 0),
    sanctions: sanctions.map((s) => sanctionView(s)),
    history: events.filter((e) => e.type !== 'flagged').map((e) => {
      const d = asJson(e.data, {});
      const out = { type: e.type, at: n(e.created_at), actor: e.actor === 'admin' ? 'platform' : e.actor };
      if (e.actor === 'shop') out.userId = e.actor_id;
      if (['shop_response', 'decided', 'appealed', 'appeal_decided', 'sanction_applied', 'sanction_lifted'].includes(e.type)) out.body = e.body;
      if (d.outcome) out.outcome = d.outcome;
      if (d.kind) out.kind = d.kind;
      if (d.productId) out.productId = d.productId;
      return out;
    }),
  };
}

function adminRow(r) {
  return {
    id: r.id, shopId: r.shop_id, shopName: r.shop_name || '', ...base(r), severity: r.severity,
    assignee: r.assignee || '', outcome: r.outcome || null, appealOutcome: r.appeal_outcome || null,
    flags: asJson(r.flags, []), duplicateOf: r.duplicate_of || null,
    evidenceCount: Number(r.evidence_count || 0),
  };
}

// ════════════════════════════════════════════════════════════════════
//  مشتری
// ════════════════════════════════════════════════════════════════════

/** ویترین از روی نشانی — باز بودن لازم نیست (از دکانِ بسته/معلق هم می‌شود شکایت کرد). */
async function storeOf(slug) {
  const s = str(slug).toLowerCase();
  const nope = () => notFound('این ویترین پیدا نشد', 'store_not_found');
  if (!SLUG_RE.test(s)) throw nope();
  const row = await one('SELECT shop_id, slug FROM storefronts WHERE slug=$1', [s]);
  //  پرچمِ خاموش و «نیست» یک جواب‌اند
  if (!row || !(await flagOf(row.shop_id))) throw nope();
  return row;
}

const OPEN_KEY_SQL = `reporter_key=$1 AND shop_id=$2 AND kind=$3 AND subject=$4 AND order_id=$5 AND product_id=$6
  AND status IN ('open','in_review')`;

/**
 * ثبتِ شکایت (`complaint`: سفارش یا دکان) یا گزارش (`report`: کالای ویترین).
 * تکرارِ همان شاکی برای همان موضوعِ باز ⇒ همان پرونده (`duplicate: true`).
 */
async function file(store, viewer, body, kind) {
  const b = obj(body);
  const shopId = store.shop_id;
  const cats = kind === 'report' ? REPORT : COMPLAINT;
  const category = str(b.category);
  if (!has(cats, category)) throw badRequest('دستهٔ شکایت نامعتبر است', 'bad_category');
  const text = cleanText(b.body);
  let subject = 'product';
  let orderId = '';
  let orderNo = null;
  let productId = '';
  if (kind === 'report') {
    productId = str(b.productId);
    if (!PRODUCT_ID_RE.test(productId)) throw badRequest('کالا نامعتبر است', 'bad_product');
    const p = await one(
      'SELECT 1 AS ok FROM storefront_products WHERE shop_id=$1 AND product_id=$2 AND published', [shopId, productId]
    );
    if (!p) throw notFound('این کالا در ویترین نیست', 'product_not_found');
  } else {
    subject = str(b.subject) || (b.orderNo != null ? 'order' : 'shop');
    if (subject !== 'order' && subject !== 'shop') throw badRequest('موضوعِ شکایت نامعتبر است', 'bad_subject');
    if (subject === 'order') {
      const no = Number(b.orderNo);
      if (!Number.isSafeInteger(no) || no <= 0) throw badRequest('شمارهٔ سفارش نامعتبر است', 'bad_order');
      //  خریدِ واقعی: سفارش باید به همین شمارهٔ تأییدشده ثبت شده باشد
      const o = await one(
        'SELECT id, order_no FROM online_orders WHERE shop_id=$1 AND order_no=$2 AND customer_phone=$3',
        [shopId, no, viewer.phone]
      );
      if (!o) throw notFound('این سفارش به نامِ شمارهٔ شما پیدا نشد', 'order_not_found');
      orderId = o.id;
      orderNo = Number(o.order_no);
    }
  }
  const rk = reporterKeyOf(viewer.phone);
  const ik = ipKeyOf(viewer.ip);
  const keyArgs = [rk, shopId, kind, subject, orderId, productId];
  const existing = await one(`SELECT * FROM trust_cases WHERE ${OPEN_KEY_SQL}`, keyArgs);
  if (existing) return { duplicate: true, case: await customerCase(existing) };

  const at = now();
  const since = at - DAY;
  const q = await one(
    `SELECT COUNT(*) FILTER (WHERE reporter_key=$1)::int AS by_phone,
            COUNT(*) FILTER (WHERE $2 <> '' AND ip_key=$2)::int AS by_ip
       FROM trust_cases WHERE created_at > $3 AND (reporter_key=$1 OR ($2 <> '' AND ip_key=$2))`,
    [rk, ik, since]
  );
  if (q.by_phone >= config.trust.dailyPerPhone || (ik && q.by_ip >= config.trust.dailyPerIp)) {
    throw new ApiError(429, 'too_many_reports', 'امروز بیش از اندازه شکایت ثبت شده است؛ فردا دوباره امتحان کنید');
  }

  //  نشانه‌های تکرار از سوی دیگران — فقط برچسب برای مدیر، نه رد یا پذیرشِ خودکار
  const flags = [];
  let duplicateOf = '';
  const norm = normalize(text);
  const bodyHash = norm.length >= SAME_TEXT_MIN ? sha256(norm) : '';
  if (bodyHash) {
    const d = await one(
      `SELECT id FROM trust_cases WHERE shop_id=$1 AND body_hash=$2 AND reporter_key<>$3 AND created_at > $4
        ORDER BY created_at, id LIMIT 1`,
      [shopId, bodyHash, rk, at - 7 * DAY]
    );
    if (d) { flags.push('same_text'); duplicateOf = d.id; }
  }
  if (ik) {
    const s = await one(
      'SELECT 1 AS ok FROM trust_cases WHERE shop_id=$1 AND ip_key=$2 AND reporter_key<>$3 AND created_at > $4 LIMIT 1',
      [shopId, ik, rk, since]
    );
    if (s) flags.push('same_ip');
  }

  const row = await tx(async (c) => {
    const r = (await c.query(
      `INSERT INTO trust_cases (id, shop_id, kind, subject, order_id, order_no, product_id, category, severity, body,
                                reporter_key, reporter_hint, ip_key, body_hash, duplicate_of, flags, created_at, updated_at)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15,$16,$17,$17)
       ON CONFLICT (reporter_key, shop_id, kind, subject, order_id, product_id) WHERE status IN ('open','in_review')
       DO NOTHING RETURNING *`,
      [newId('tc'), shopId, kind, subject, orderId, orderNo, productId, category, cats[category], text,
        rk, maskPhone(viewer.phone), ik, bodyHash, duplicateOf, JSON.stringify(flags), at]
    )).rows[0];
    if (!r) return null;
    await addEvent(c, r, 'customer', '', 'filed');
    if (flags.length) await addEvent(c, r, 'system', '', 'flagged', '', { flags, duplicateOf });
    return r;
  });
  if (!row) {
    //  درخواستِ هم‌زمان برنده شد — همان پرونده
    const won = await one(`SELECT * FROM trust_cases WHERE ${OPEN_KEY_SQL}`, keyArgs);
    if (!won) throw conflict('دوباره امتحان کنید', 'retry');
    return { duplicate: true, case: await customerCase(won) };
  }
  return { duplicate: false, case: await customerCase(row) };
}

async function customerCase(row) {
  const [events, evidence] = await Promise.all([eventsOf(row.id), evidenceOf(row.id)]);
  return customerView(row, events, evidence);
}

async function caseByToken(store, token) {
  const id = caseIdOf(store.shop_id, token);
  if (!id) throw gone();
  const row = await one('SELECT * FROM trust_cases WHERE id=$1 AND shop_id=$2', [id, store.shop_id]);
  if (!row) throw gone();
  return row;
}

async function track(store, token) {
  return customerCase(await caseByToken(store, token));
}

/** پرونده‌های همین شمارهٔ تأییدشده در همین ویترین — توکنِ گم‌شده با تأییدِ دوباره برمی‌گردد. */
async function myCases(store, viewer) {
  const rows = await many(
    `SELECT * FROM trust_cases WHERE shop_id=$1 AND reporter_key=$2 ORDER BY created_at DESC, id DESC LIMIT 20`,
    [store.shop_id, reporterKeyOf(viewer.phone)]
  );
  return rows.map((r) => ({ token: tokenOf(r.shop_id, r.id), ...base(r), outcome: verdict(r) }));
}

// ── مدرک ─────────────────────────────────────────────────────────

const kb = (bytes) => `${Math.round(bytes / 1024)} کیلوبایت`;

/** بارگذاری: نوع از روی بایت (فقط عکس)، EXIF ِ مکان پاک، همان بایت‌ها ⇒ همان ردیف. */
async function putEvidence(store, token, buf) {
  const pre = await caseByToken(store, token);
  if (pre.status !== 'open' && pre.status !== 'in_review') throw conflict('این پرونده دیگر مدرک نمی‌پذیرد', 'case_not_open');
  if (!Buffer.isBuffer(buf) || !buf.length) throw badRequest('فایل خالی است', 'bad_media');
  const s = chatMedia.sniff(buf);
  if (!s || s.kind !== 'image') throw new ApiError(415, 'unsupported_media', 'فقط عکس (JPG، PNG، WebP)');
  if (buf.length > config.trust.imageMax) throw new ApiError(413, 'too_large', `عکس بزرگ‌تر از ${kb(config.trust.imageMax)} است`);
  const data = chatMedia.stripImageMetadata(buf, s.mime);
  const hash = sha256(data);
  return tx(async (c) => {
    const row = (await c.query('SELECT * FROM trust_cases WHERE id=$1 FOR UPDATE', [pre.id])).rows[0];
    if (row.status !== 'open' && row.status !== 'in_review') throw conflict('این پرونده دیگر مدرک نمی‌پذیرد', 'case_not_open');
    const same = (await c.query('SELECT id, mime, size FROM trust_evidence WHERE case_id=$1 AND sha256=$2', [row.id, hash])).rows[0];
    if (same) return { evidenceId: same.id, mime: same.mime, size: Number(same.size), reused: true };
    const cnt = Number((await c.query('SELECT COUNT(*)::int AS n FROM trust_evidence WHERE case_id=$1', [row.id])).rows[0].n);
    if (cnt >= config.trust.evidenceMax) throw conflict(`حداکثر ${config.trust.evidenceMax} عکس برای هر پرونده`, 'evidence_limit');
    const id = newId('tev');
    await c.query(
      `INSERT INTO trust_evidence (id, case_id, shop_id, mime, size, sha256, data, created_at) VALUES ($1,$2,$3,$4,$5,$6,$7,$8)`,
      [id, row.id, row.shop_id, s.mime, data.length, hash, data, now()]
    );
    await addEvent(c, row, 'customer', '', 'evidence_added', '', { evidenceId: id });
    await c.query('UPDATE trust_cases SET updated_at=$2 WHERE id=$1', [row.id, now()]);
    return { evidenceId: id, mime: s.mime, size: data.length, reused: false };
  });
}

async function evidenceBytes(caseId, evidenceId) {
  const eid = String(evidenceId || '');
  const lost = () => notFound('این فایل روی سرور نیست', 'media_gone');
  if (!EV_RE.test(eid)) throw lost();
  const m = await one('SELECT id, mime, size, data FROM trust_evidence WHERE id=$1 AND case_id=$2', [eid, caseId]);
  if (!m) throw lost();
  return { id: m.id, mime: m.mime, size: Number(m.size), kind: 'image', data: Buffer.from(m.data) };
}

async function customerEvidence(store, token, evidenceId) {
  const row = await caseByToken(store, token);
  return evidenceBytes(row.id, evidenceId);
}

/** خوانندهٔ بدنهٔ خامِ عکس — بزرگ‌تر از سقف پیش از خواندن رد می‌شود. */
function rawEvidence(req, res, next) {
  const max = config.trust.imageMax;
  const tooBig = () => new ApiError(413, 'too_large', `عکس بزرگ‌تر از ${kb(max)} است`);
  const len = Number(req.headers['content-length']);
  if (Number.isFinite(len) && len > max) return next(tooBig());
  express.raw({ type: () => true, limit: max })(req, res, (err) => {
    if (err && err.type === 'entity.too.large') return next(tooBig());
    if (err) return next(badRequest('فایل درست نرسید', 'bad_media'));
    next();
  });
}

// ════════════════════════════════════════════════════════════════════
//  دکان
// ════════════════════════════════════════════════════════════════════

async function shopRow(shopId, id, c = null, lock = false) {
  const cid = String(id || '');
  if (!CASE_RE.test(cid)) throw gone();
  const sql = `SELECT * FROM trust_cases WHERE id=$1 AND shop_id=$2${lock ? ' FOR UPDATE' : ''}`;
  const row = c ? (await c.query(sql, [cid, shopId])).rows[0] : await one(sql, [cid, shopId]);
  if (!row) throw gone();
  return row;
}

async function shopCase(row) {
  const [events, ev, sanctions] = await Promise.all([
    eventsOf(row.id),
    one('SELECT COUNT(*)::int AS n FROM trust_evidence WHERE case_id=$1', [row.id]),
    sanctionsOfCase(row.id),
  ]);
  return shopView(row, events, ev.n, sanctions);
}

async function listForShop(shopId, { status, limit, before } = {}) {
  const lim = intIn(limit, { min: 1, max: 100, def: 50 });
  const args = [shopId];
  let where = 'shop_id=$1';
  const st = str(status);
  if (st && st !== 'all') {
    if (!STATUSES.includes(st)) throw badRequest('وضعیت نامعتبر است', 'bad_status');
    args.push(st);
    where += ` AND status=$${args.length}`;
  }
  if (before) {
    args.push(intIn(before, { min: 1 }));
    where += ` AND created_at < $${args.length}`;
  }
  args.push(lim);
  const rows = await many(
    `SELECT c.*, (SELECT COUNT(*)::int FROM trust_evidence e WHERE e.case_id = c.id) AS evidence_count
       FROM trust_cases c WHERE ${where} ORDER BY created_at DESC, id DESC LIMIT $${args.length}`,
    args
  );
  return {
    cases: rows.map((r) => ({
      id: r.id, ...shopBase(r), severity: r.severity, outcome: r.outcome || null,
      appealOutcome: r.appeal_outcome || null, evidenceCount: Number(r.evidence_count || 0),
    })),
  };
}

async function viewForShop(shopId, id) {
  return shopCase(await shopRow(shopId, id));
}

/** پاسخِ دکان — با `clientId` ِ دستگاه، تکرار پاسخِ دوم نمی‌سازد. */
async function respond(viewer, id, body) {
  const b = obj(body);
  const text = cleanText(b.body);
  if (!text) throw badRequest('متنِ پاسخ را بنویسید', 'body_required');
  const clientId = str(b.clientId);
  if (clientId && !CLIENT_ID_RE.test(clientId)) throw badRequest('شناسهٔ درخواست نامعتبر است', 'bad_client_id');
  const out = await tx(async (c) => {
    const row = await shopRow(viewer.shopId, id, c, true);
    if (clientId) {
      const prev = (await c.query(
        `SELECT 1 AS ok FROM trust_case_events WHERE case_id=$1 AND type='shop_response' AND data->>'clientId' = $2`,
        [row.id, clientId]
      )).rows[0];
      if (prev) return { row, replayed: true };
    }
    if (row.status === 'closed') throw conflict('این پرونده بسته شده است', 'case_closed');
    const cnt = Number((await c.query(
      `SELECT COUNT(*)::int AS n FROM trust_case_events WHERE case_id=$1 AND type='shop_response'`, [row.id]
    )).rows[0].n);
    if (cnt >= MAX_RESPONSES) throw conflict('پاسخِ بیشتری برای این پرونده پذیرفته نمی‌شود', 'response_limit');
    await addEvent(c, row, 'shop', viewer.userId, 'shop_response', text, clientId ? { clientId } : {});
    await c.query('UPDATE trust_cases SET updated_at=$2 WHERE id=$1', [row.id, now()]);
    return { row, replayed: false };
  });
  return { replayed: out.replayed, case: await viewForShop(viewer.shopId, out.row.id) };
}

/** اعتراضِ دکان به تصمیمِ «وارد است» — یک بار و در مهلت. تکرارِ همان متن ⇒ همان نتیجه. */
async function appeal(viewer, id, body) {
  const text = cleanText(obj(body).body);
  if (text.length < 10) throw badRequest('دلیلِ اعتراض را بنویسید (دست‌کم ۱۰ نویسه)', 'appeal_reason_required');
  const out = await tx(async (c) => {
    const row = await shopRow(viewer.shopId, id, c, true);
    if (row.appealed_at != null) {
      if (row.appeal_body === text) return { row, replayed: true };
      throw conflict('به این تصمیم یک بار اعتراض شده است', 'already_appealed');
    }
    if (row.status !== 'decided' || row.outcome !== 'upheld') throw conflict('این پرونده تصمیمِ قابلِ اعتراض ندارد', 'not_appealable');
    if (now() > Number(row.decided_at) + config.trust.appealDays * DAY) {
      throw conflict('مهلتِ اعتراض گذشته است', 'appeal_window_closed');
    }
    const at = now();
    await c.query(
      `UPDATE trust_cases SET status='appealed', appeal_body=$2, appealed_by=$3, appealed_at=$4, updated_at=$4 WHERE id=$1`,
      [row.id, text, viewer.userId, at]
    );
    await addEvent(c, row, 'shop', viewer.userId, 'appealed', text);
    return { row, replayed: false };
  });
  return { replayed: out.replayed, case: await viewForShop(viewer.shopId, out.row.id) };
}

async function sanctionsForShop(shopId) {
  const rows = await many(
    'SELECT * FROM trust_sanctions WHERE shop_id=$1 ORDER BY created_at DESC, id DESC LIMIT 200', [shopId]
  );
  return rows.map((s) => sanctionView(s));
}

// ════════════════════════════════════════════════════════════════════
//  مدیرِ سامانه
// ════════════════════════════════════════════════════════════════════

/** صفِ رسیدگی: پیش‌فرض «در انتظار» (باز، در بررسی، اعتراض‌شده)؛ اول شدیدتر، بعد قدیمی‌تر. */
async function queue({ status, shopId, limit } = {}) {
  const lim = intIn(limit, { min: 1, max: 200, def: 50 });
  const st = str(status) || 'pending';
  const args = [];
  const where = [];
  if (st === 'pending') where.push(`c.status IN ('open','in_review','appealed')`);
  else if (st !== 'all') {
    if (!STATUSES.includes(st)) throw badRequest('وضعیت نامعتبر است', 'bad_status');
    args.push(st);
    where.push(`c.status = $${args.length}`);
  }
  if (shopId) { args.push(String(shopId)); where.push(`c.shop_id = $${args.length}`); }
  args.push(lim);
  const rows = await many(
    `SELECT c.*, s.name AS shop_name, (SELECT COUNT(*)::int FROM trust_evidence e WHERE e.case_id = c.id) AS evidence_count
       FROM trust_cases c JOIN shops s ON s.id = c.shop_id
      ${where.length ? `WHERE ${where.join(' AND ')}` : ''}
      ORDER BY CASE c.status WHEN 'appealed' THEN 0 ELSE 1 END,
               CASE c.severity WHEN 'high' THEN 0 WHEN 'medium' THEN 1 ELSE 2 END, c.created_at, c.id
      LIMIT $${args.length}`,
    args
  );
  return rows.map(adminRow);
}

async function adminCaseRow(id, c = null, lock = false) {
  const cid = String(id || '');
  if (!CASE_RE.test(cid)) throw gone();
  const sql = `SELECT * FROM trust_cases WHERE id=$1${lock ? ' FOR UPDATE' : ''}`;
  const row = c ? (await c.query(sql, [cid])).rows[0] : await one(sql, [cid]);
  if (!row) throw gone();
  return row;
}

/** پروندهٔ کامل برای مدیر — شماره فقط پوشیده؛ سابقهٔ همین شاکی برای دیدنِ الگوی سوءاستفاده. */
async function adminView(id) {
  const row = await adminCaseRow(id);
  const [shop, events, evidence, sanctions, stats, related] = await Promise.all([
    one('SELECT name FROM shops WHERE id=$1', [row.shop_id]),
    eventsOf(row.id),
    evidenceOf(row.id),
    sanctionsOfCase(row.id),
    one(
      `SELECT COUNT(*)::int AS total,
              COUNT(*) FILTER (WHERE outcome='upheld')::int AS upheld,
              COUNT(*) FILTER (WHERE outcome='dismissed')::int AS dismissed,
              COUNT(DISTINCT shop_id)::int AS shops
         FROM trust_cases WHERE reporter_key=$1 AND created_at > $2`,
      [row.reporter_key, now() - 30 * DAY]
    ),
    row.body_hash ? many(
      `SELECT id FROM trust_cases WHERE shop_id=$1 AND body_hash=$2 AND id<>$3 ORDER BY created_at, id LIMIT 20`,
      [row.shop_id, row.body_hash, row.id]
    ) : [],
  ]);
  return {
    ...adminRow({ ...row, shop_name: shop ? shop.name : '', evidence_count: evidence.length }),
    body: row.body, reporterHint: row.reporter_hint, reporterStats: stats, sameText: related.map((r) => r.id),
    decision: row.decided_at == null ? null : {
      outcome: row.outcome, reason: row.decision_reason, by: row.decided_by, at: n(row.decided_at),
    },
    appeal: row.appealed_at == null ? null : {
      body: row.appeal_body, by: row.appealed_by, at: n(row.appealed_at), outcome: row.appeal_outcome || null,
      reason: row.appeal_reason, decidedBy: row.appeal_decided_by, decidedAt: n(row.appeal_decided_at),
    },
    evidence: evidence.map((e) => ({ id: e.id, mime: e.mime, size: Number(e.size), createdAt: n(e.created_at) })),
    sanctions: sanctions.map((s) => sanctionView(s, { admin: true })),
    history: events.map((e) => ({
      type: e.type, at: n(e.created_at), actor: e.actor, actorId: e.actor_id || '', body: e.body, data: asJson(e.data, {}),
    })),
  };
}

async function adminEvidence(caseId, evidenceId) {
  const row = await adminCaseRow(caseId);
  return { shopId: row.shop_id, media: await evidenceBytes(row.id, evidenceId) };
}

/** مسئولِ رسیدگی — پروندهٔ باز به «در بررسی» می‌رود. */
async function assign(adminId, id) {
  return tx(async (c) => {
    const row = await adminCaseRow(id, c, true);
    if (!['open', 'in_review', 'appealed'].includes(row.status)) throw conflict('این پرونده رسیدگی‌شده است', 'case_decided');
    const status = row.status === 'open' ? 'in_review' : row.status;
    await c.query('UPDATE trust_cases SET assignee=$2, status=$3, updated_at=$4 WHERE id=$1', [row.id, adminId, status, now()]);
    await addEvent(c, row, 'admin', adminId, 'assigned', '', { assignee: adminId });
    return { id: row.id, shopId: row.shop_id, status, assignee: adminId };
  });
}

async function productOfShop(c, shopId, productId) {
  if (!PRODUCT_ID_RE.test(productId)) throw badRequest('کالا برای پنهان کردن لازم است', 'bad_product');
  const p = (await c.query('SELECT 1 AS ok FROM storefront_products WHERE shop_id=$1 AND product_id=$2', [shopId, productId])).rows[0];
  if (!p) throw notFound('این کالا در ویترینِ این دکان نیست', 'product_not_found');
}

/**
 * تصمیم با دلیلِ نوشته (`reason` از `requireReason`). جریمه فقط با «وارد است»
 * و فقط یکی از سه نوع؛ پرونده‌ای که تصمیم دارد دوباره تصمیم نمی‌گیرد.
 */
async function decide(adminId, id, body, reason) {
  const b = obj(body);
  const outcome = str(b.outcome);
  if (outcome !== 'upheld' && outcome !== 'dismissed') throw badRequest('نتیجه باید «وارد است» یا «رد» باشد', 'bad_outcome');
  const severity = b.severity == null || b.severity === '' ? null : str(b.severity);
  if (severity !== null && !SEVERITIES.includes(severity)) throw badRequest('شدت نامعتبر است', 'bad_severity');
  const sb = b.sanction == null ? null : obj(b.sanction);
  if (sb) {
    if (!SANCTIONS.includes(str(sb.kind))) throw badRequest('نوعِ جریمه فقط هشدار، پنهان کردنِ کالا یا تعلیقِ ویترین', 'bad_sanction');
    if (outcome !== 'upheld') throw badRequest('جریمه فقط با تصمیمِ «وارد است»', 'bad_sanction');
  }
  return tx(async (c) => {
    const row = await adminCaseRow(id, c, true);
    if (row.status !== 'open' && row.status !== 'in_review') throw conflict('این پرونده پیش‌تر تصمیم گرفته است', 'already_decided');
    let sanction = null;
    if (sb) {
      const kind = str(sb.kind);
      const productId = kind === 'hide_listing' ? (str(sb.productId) || row.product_id) : '';
      if (kind === 'hide_listing') await productOfShop(c, row.shop_id, productId);
      sanction = { id: newId('tsn'), kind, productId };
    }
    const at = now();
    await c.query(
      `UPDATE trust_cases SET status='decided', outcome=$2, decision_reason=$3, decided_by=$4, decided_at=$5,
              severity=COALESCE($6, severity), assignee=CASE WHEN assignee='' THEN $4 ELSE assignee END, updated_at=$5
        WHERE id=$1`,
      [row.id, outcome, reason, adminId, at, severity]
    );
    await addEvent(c, row, 'admin', adminId, 'decided', reason, { outcome });
    if (sanction) {
      await c.query(
        `INSERT INTO trust_sanctions (id, shop_id, case_id, kind, product_id, reason, created_by, created_at)
         VALUES ($1,$2,$3,$4,$5,$6,$7,$8)`,
        [sanction.id, row.shop_id, row.id, sanction.kind, sanction.productId, reason, adminId, at]
      );
      await addEvent(c, row, 'admin', adminId, 'sanction_applied', reason,
        { sanctionId: sanction.id, kind: sanction.kind, ...(sanction.productId ? { productId: sanction.productId } : {}) });
    }
    return { id: row.id, shopId: row.shop_id, outcome, sanction };
  });
}

/** برداشتنِ جریمه داخلِ تراکنش — ردیف پاک نمی‌شود. */
async function liftIn(c, row, s, adminId, reason, at) {
  await c.query(
    'UPDATE trust_sanctions SET lifted_at=$2, lifted_by=$3, lift_reason=$4 WHERE id=$1 AND lifted_at IS NULL',
    [s.id, at, adminId, reason]
  );
  await addEvent(c, row, 'admin', adminId, 'sanction_lifted', reason,
    { sanctionId: s.id, kind: s.kind, ...(s.product_id ? { productId: s.product_id } : {}) });
}

/** رأیِ اعتراض: `reversed` همهٔ جریمه‌های فعالِ همین پرونده را برمی‌دارد. */
async function decideAppeal(adminId, id, body, reason) {
  const outcome = str(obj(body).outcome);
  if (outcome !== 'upheld' && outcome !== 'reversed') throw badRequest('رأیِ اعتراض باید «تأیید» یا «برگشت» باشد', 'bad_outcome');
  return tx(async (c) => {
    const row = await adminCaseRow(id, c, true);
    if (row.status !== 'appealed') throw conflict('این پرونده اعتراضِ در انتظار ندارد', 'no_pending_appeal');
    const at = now();
    await c.query(
      `UPDATE trust_cases SET status='closed', appeal_outcome=$2, appeal_reason=$3, appeal_decided_by=$4,
              appeal_decided_at=$5, updated_at=$5 WHERE id=$1`,
      [row.id, outcome, reason, adminId, at]
    );
    const lifted = [];
    if (outcome === 'reversed') {
      const active = (await c.query(
        'SELECT * FROM trust_sanctions WHERE case_id=$1 AND lifted_at IS NULL ORDER BY created_at, id FOR UPDATE', [row.id]
      )).rows;
      for (const s of active) { await liftIn(c, row, s, adminId, reason, at); lifted.push(s.id); }
    }
    await addEvent(c, row, 'admin', adminId, 'appeal_decided', reason, { outcome });
    return { id: row.id, shopId: row.shop_id, outcome, lifted };
  });
}

async function liftSanction(adminId, sanctionId, reason) {
  const sid = String(sanctionId || '');
  const nope = () => notFound('این جریمه پیدا نشد', 'sanction_not_found');
  if (!SAN_RE.test(sid)) throw nope();
  return tx(async (c) => {
    const s = (await c.query('SELECT * FROM trust_sanctions WHERE id=$1 FOR UPDATE', [sid])).rows[0];
    if (!s) throw nope();
    if (s.lifted_at != null) throw conflict('این جریمه پیش‌تر برداشته شده است', 'already_lifted');
    const row = (await c.query('SELECT * FROM trust_cases WHERE id=$1', [s.case_id])).rows[0];
    await liftIn(c, row, s, adminId, reason, now());
    return { id: s.id, shopId: s.shop_id, caseId: s.case_id, kind: s.kind };
  });
}

async function sanctionsForAdmin({ shopId, active, limit } = {}) {
  const lim = intIn(limit, { min: 1, max: 500, def: 100 });
  const args = [];
  const where = [];
  if (shopId) { args.push(String(shopId)); where.push(`shop_id = $${args.length}`); }
  if (active === '1' || active === 'true' || active === true) where.push('lifted_at IS NULL');
  args.push(lim);
  const rows = await many(
    `SELECT * FROM trust_sanctions ${where.length ? `WHERE ${where.join(' AND ')}` : ''}
      ORDER BY created_at DESC, id DESC LIMIT $${args.length}`,
    args
  );
  return rows.map((s) => sanctionView(s, { admin: true }));
}

module.exports = {
  FLAG_KEY, COMPLAINT, REPORT, SANCTIONS, MAX_BODY,
  flagOf, tokenOf, caseIdOf, normalize,
  storeOf, file, track, myCases, putEvidence, customerEvidence, rawEvidence,
  listForShop, viewForShop, respond, appeal, sanctionsForShop,
  queue, adminView, adminEvidence, assign, decide, decideAppeal, liftSanction, sanctionsForAdmin,
};
