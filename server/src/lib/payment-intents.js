'use strict';
/**
 * قصدِ پرداختِ درگاهی (حساب‌پی) — PAY-21، PAY-08، سناریوی S35.
 *
 *   صندوق (pos_sale)      صندوقدار QR ِ مبلغِ دقیق را نشان می‌دهد ⇒ مشتری با برنامهٔ حساب‌پی
 *                         می‌پردازد ⇒ وب‌هوک ⇒ «paid» ⇒ برنامهٔ دکان (long-poll) فروش را می‌بندد.
 *   سفارش (online_order)  مبلغ همیشه همان `online_orders.total_minor` ِ سرور است، نه عددِ کاربر.
 *
 * ── ماشینِ وضعیت ─────────────────────────────────────────────────────────
 *   created → pending → paid | failed | expired | cancelled ؛ paid → refunded (فقط مدیر)
 *   created → failed | expired | cancelled
 *   ⛔ هر گذارِ دیگر رد می‌شود و وضعیتِ پایانی برنمی‌گردد. تنها استثنا S35 است:
 *      expired | cancelled | failed → paid فقط وقتی خودِ ارائه‌دهنده (fetchStatus) پرداخت را
 *      تأیید کند؛ همان دم `needs_review` و سابقهٔ `payment.late_paid` (هشدار برای تطبیق/بازپرداخت).
 *   هر گذار در تراکنش و با compare-and-set روی status (`UPDATE … WHERE status = قبلی`)؛ ترتیبِ
 *   رسیدنِ وب‌هوک‌ها (حتی هم‌میلی‌ثانیه) مهم نیست — «paid» هرگز با رویدادِ بعدی پایین نمی‌آید.
 *
 * ── یکتایی ───────────────────────────────────────────────────────────────
 *   (shop_id, idempotency_key) ⇒ همان کلید همان قصد؛ هر فروش/سفارش حداکثر یک قصدِ زنده؛
 *   ref_id عوض نمی‌شود ⇒ یک قصد فقط یک فروش را می‌پردازد؛ فروشِ پرداخت‌شده قصدِ تازه نمی‌گیرد.
 *
 * ⛔ shop_id فقط از نشستِ کاربر می‌آید (routes/shop-payments.js)، هرگز از بدنه.
 * ⛔ «پرداخت شد» فقط از وب‌هوکِ امضاشده + تأییدِ دومِ ارائه‌دهنده (پیش‌فرض روشن)؛ هرگز از
 *    برگشتِ مرورگر یا گفتهٔ برنامه.
 */
const crypto = require('crypto');
const { EventEmitter } = require('events');
const { one, tx, newId, now, query } = require('../db');
const config = require('../config');
const P = require('./payment-providers');
const S = require('./order-states');
const { ulid } = require('./ulid');
const { stable } = require('./idempotency');
const audit = require('./audit');
const { badRequest, forbidden, notFound, conflict, ApiError } = require('../middleware/errors');

const FLAG_KEY = 'pay.hesabpay';
const METHOD_ID = 'hesabpay';            // همان شناسهٔ PAY-06 در license/pay-core.js
const METHOD_KIND = 'wallet';
const KINDS = ['pos_sale', 'online_order'];
const LIVE = ['created', 'pending'];
const KEY_RE = /^[A-Za-z0-9_.:-]{8,128}$/;
const REF_RE = /^[A-Za-z0-9_.:-]{1,128}$/;
const MAX_AMOUNT_MINOR = 100_000_000_000;   // یک میلیارد افغانی — همان سقفِ قیمتِ سفارش
const MAX_WAIT_S = 25;
/** سفارشِ بسته‌ای که پولش دیر رسید ⇒ بررسی و بازپرداخت */
const CLOSED_ORDER = ['rejected', 'auto_rejected', 'cancelled', 'cancelled_by_customer', 'returned_to_store',
  'restocked', 'refunded_partial', 'refunded_full'];

const TRANSITIONS = Object.freeze({
  created: ['pending', 'failed', 'expired', 'cancelled'],
  pending: ['paid', 'failed', 'expired', 'cancelled'],
  paid: ['refunded'],
  failed: [], expired: [], cancelled: [], refunded: [],
});
/** S35: فقط با تأییدِ صریحِ ارائه‌دهنده. */
const LATE_PAID_FROM = Object.freeze(['expired', 'cancelled', 'failed']);

function canTransition(from, to, { confirmedLate = false } = {}) {
  if (TRANSITIONS[from] && TRANSITIONS[from].includes(to)) return true;
  return to === 'paid' && confirmedLate && LATE_PAID_FROM.includes(from);
}

const bus = new EventEmitter();
bus.setMaxListeners(0);

// ════════════════════════════════════════════════════════════════════
//  پرچم و پیکربندی
// ════════════════════════════════════════════════════════════════════

/** تأییدِ دوم پیش از «paid» — پیش‌فرض روشن؛ فقط `PAYMENTS_CONFIRM_WEBHOOK=0` خاموشش می‌کند. */
const confirmEnabled = () => String(process.env.PAYMENTS_CONFIRM_WEBHOOK ?? '1').trim() !== '0';

function provider() { return P.adapter(P.activeName()); }

const flagOf = (shopId) => require('./orders').flagOf(shopId, FLAG_KEY);

/** «حساب‌پی» برای این دکان روشن است؟ = کلیدها داده شده + پرچمِ دکان. */
async function enabledFor(shopId) {
  const ad = provider();
  if (!ad || !ad.configured()) return false;
  return flagOf(shopId);
}

/** پرداختِ سفارشِ آنلاین فقط برای دکانِ **مشترک** (منبعِ «اشتراک» با کلیدِ online_orders؛ آزمایشی نه). */
async function subscribedFor(shopId) {
  const { entitlementOf } = require('./entitlement');
  const ent = await entitlementOf(shopId, now());
  return ent.source === 'subscription' && ent.features.includes('online_orders');
}

async function configOf(shopId) {
  return { hesabpay: { enabled: await enabledFor(shopId) } };
}

/** نشانیِ وب‌هوک برای ارائه‌دهنده: `HESABPAY_CALLBACK_URL`، وگرنه از `SERVER_URL`؛ هیچ ⇒ همانی که در داشبوردِ حساب‌پی ثبت شده. */
function callbackUrl(name) {
  if (name === 'hesabpay' && process.env.HESABPAY_CALLBACK_URL) return String(process.env.HESABPAY_CALLBACK_URL).trim();
  const base = String(config.serverUrl || '').trim().replace(/\/+$/, '');
  return base ? `${base}/api/v1/payments/webhook/${name}` : '';
}

// ════════════════════════════════════════════════════════════════════
//  نما
// ════════════════════════════════════════════════════════════════════

const num = (v) => (v === null || v === undefined ? null : Number(v));

/** ردیفِ پرداختِ فروش، به همان شکلِ ردیف‌های pay-core (`amount` = پول) — برنامه همین را به فروش می‌افزاید. */
function paymentRow(r) {
  return {
    method: METHOD_ID, kind: METHOD_KIND, amount: Number(r.amount_minor),
    ref: r.provider_ref || r.id, status: 'confirmed', verification: 'gateway', intentId: r.id,
  };
}

function view(r) {
  const out = {
    id: r.id,
    kind: r.kind,
    refId: r.ref_id,
    status: r.status,
    //  ⛔ مبلغ همیشه عددِ صحیح به «پول» (یک‌صدمِ افغانی) — ۱۴۵۰ افغانی = 145000
    amount: Number(r.amount_minor),
    amountMinor: Number(r.amount_minor),
    currency: r.currency,
    checkoutUrl: r.checkout_url || '',
    qrPayload: r.checkout_url || '',
    expiresAt: num(r.expires_at),
    paidAt: num(r.paid_at),
    providerRef: r.provider_ref || '',
    needsReview: r.needs_review === true,
    createdAt: num(r.created_at),
  };
  if ((r.status === 'paid' || r.status === 'refunded') && r.kind === 'pos_sale') out.payment = paymentRow(r);
  return out;
}

// ════════════════════════════════════════════════════════════════════
//  گذار (compare-and-set) و اثرش روی سفارش
// ════════════════════════════════════════════════════════════════════

const nudge = (id) => setImmediate(() => bus.emit(id));

/**
 * گذارِ قصد درونِ تراکنشِ `c`. `from` همان وضعیتی است که خوانده شد؛ اگر دیگر نیست null برمی‌گردد
 * (فراخواننده دوباره می‌خواند). `patch`: ستون‌های دیگرِ همین UPDATE.
 */
async function casIn(c, row, to, patch = {}, opts = {}) {
  if (!canTransition(row.status, to, opts)) {
    throw conflict('این تغییرِ وضعیتِ پرداخت مجاز نیست', 'invalid_payment_transition');
  }
  const cols = Object.keys(patch);
  const sets = cols.map((k, i) => `${k}=$${i + 5}`);
  const r = await c.query(
    `UPDATE payment_intents SET status=$3, ${sets.length ? `${sets.join(', ')}, ` : ''}version=version+1, updated_at=$4
      WHERE id=$1 AND status=$2 RETURNING *`,
    [row.id, row.status, to, now(), ...cols.map((k) => patch[k])]
  );
  return r.rows[0] || null;
}

/**
 * پرداختِ سفارشِ آنلاین هم‌گام با قصد — `order-states.PAYMENT_TRANSITIONS` و رویدادِ `payment`.
 * @returns {string} '' = بی‌مشکل؛ وگرنه دلیلِ بررسی (پرداختِ دوباره، نقدِ گرفته‌شده، …)
 */
async function orderPaymentIn(c, intent, to, detail = {}) {
  if (intent.kind !== 'online_order') return '';
  const o = (await c.query(
    'SELECT id, status, payment_status, cod_status, total_minor FROM online_orders WHERE shop_id=$1 AND id=$2 FOR UPDATE',
    [intent.shop_id, intent.ref_id]
  )).rows[0];
  if (!o) return to === 'paid' ? 'order_missing' : '';
  let steps;
  if (to === 'paid') {
    if (o.payment_status === 'paid' || !['', 'to_collect'].includes(o.cod_status)) return 'order_already_paid';
    steps = o.payment_status === 'pending' ? ['paid'] : S.canPayment(o.payment_status, 'pending') ? ['pending', 'paid'] : null;
    if (!steps) return 'order_payment_blocked';
  } else if (to === 'pending') {
    if (!S.canPayment(o.payment_status, 'pending')) return '';
    steps = ['pending'];
  } else {
    if (o.payment_status !== 'pending') return '';
    //  قصدِ زندهٔ دیگری برای همین سفارش؟ ⇒ همچنان «در انتظار»
    const other = (await c.query(
      `SELECT 1 AS x FROM payment_intents WHERE shop_id=$1 AND kind='online_order' AND ref_id=$2 AND id<>$3
          AND status IN ('created','pending') LIMIT 1`, [intent.shop_id, intent.ref_id, intent.id]
    )).rows[0];
    if (other) return '';
    steps = ['failed'];
  }
  const at = now();
  let seq = Number((await c.query(
    'SELECT COALESCE(MAX(seq), 0) AS n FROM online_order_events WHERE order_id=$1', [o.id]
  )).rows[0].n);
  let from = o.payment_status;
  for (const step of steps) {
    await c.query(
      `INSERT INTO online_order_events (id, order_id, shop_id, seq, kind, from_status, to_status,
         actor_type, actor_id, reason, note, detail, created_at)
       VALUES ($1,$2,$3,$4,'payment',$5,$6,'system','',$7,'',$8::jsonb,$9)`,
      [ulid(at), o.id, intent.shop_id, ++seq, from, step, detail.reason || '',
        JSON.stringify({ intentId: intent.id, method: METHOD_ID, ...detail }), at]
    );
    from = step;
  }
  if (to === 'paid') {
    await c.query(
      `UPDATE online_orders SET payment_status='paid', payment_method='online',
              cod_status=CASE WHEN cod_status='to_collect' THEN '' ELSE cod_status END,
              version=version+1, updated_at=$3 WHERE shop_id=$1 AND id=$2`,
      [intent.shop_id, o.id, at]
    );
    if (CLOSED_ORDER.includes(o.status)) return 'order_closed_paid';
    if (Number(o.total_minor) !== Number(intent.amount_minor)) return 'order_total_changed';
  } else {
    await c.query('UPDATE online_orders SET payment_status=$3, version=version+1, updated_at=$4 WHERE shop_id=$1 AND id=$2',
      [intent.shop_id, o.id, steps[steps.length - 1], at]);
  }
  return '';
}

/** هشدارِ تطبیق (S35) — سابقه برای صاحب و پنل؛ شکستش کار را نمی‌خواباند. */
function alert(intent, reason, detail = {}) {
  console.warn(`[payments] بررسی لازم: ${intent.id} (${intent.shop_id}) — ${reason}`);
  return audit.log({
    shopId: intent.shop_id, actorType: 'system', action: 'payment.review',
    targetType: 'payment_intent', targetId: intent.id,
    detail: { reason, kind: intent.kind, refId: intent.ref_id, amountMinor: Number(intent.amount_minor), ...detail },
  });
}

/**
 * needs_review و هشدار. هشدار فقط وقتی نشان تازه خورد: تکرارِ همان رویداد طوفانِ هشدار نمی‌سازد و
 * قصدی که از پیش زیرِ بررسی است همان یک ردیفِ سابقه را دارد. @returns {Promise<boolean>} نشانِ تازه خورد؟
 */
async function flagReview(intent, reason, detail = {}) {
  const r = await one(
    'UPDATE payment_intents SET needs_review=true, updated_at=$2 WHERE id=$1 AND needs_review=false RETURNING *',
    [intent.id, now()]
  );
  if (r) await alert(r, reason, detail);
  return Boolean(r);
}

/**
 * «paid» ِ امضاشده‌ای که هرگز تأیید نشد (رویدادِ `deferred`) روی قصدی که دیگر زنده نیست: پول شاید نزدِ
 * ارائه‌دهنده است و دکان باید ببیند ⇒ needs_review و هشدارِ `paid_unconfirmed`. ⛔ هرگز خودکار پرداخت‌شده
 * نمی‌شود؛ تأییدِ بعدیِ ارائه‌دهنده (ارسالِ دوباره یا تطبیق) همان مسیرِ S35 است.
 */
async function flagDeferredPaid(intent, source) {
  if (!intent || intent.needs_review === true || ['paid', 'refunded'].includes(intent.status)) return false;
  const ev = await one(
    `SELECT provider_event_id, received_at FROM payment_events
      WHERE intent_id=$1 AND type='paid' AND outcome='deferred' ORDER BY received_at LIMIT 1`, [intent.id]
  );
  if (!ev) return false;
  return flagReview(intent, 'paid_unconfirmed', {
    cause: 'never_confirmed', source, from: intent.status, eventId: ev.provider_event_id, receivedAt: Number(ev.received_at),
  });
}

/** گذار به یکی از وضعیت‌های بسته‌ی بی‌پول (failed/expired/cancelled) با اثرش روی سفارش. */
async function close(row, to, reason) {
  const out = await tx(async (c) => {
    const next = await casIn(c, row, to, { status_reason: reason });
    if (next) await orderPaymentIn(c, next, 'failed', { reason });
    return next;
  });
  if (out) {
    nudge(out.id);
    //  بسته شد در حالی که «paid» ِ امضاشده‌اش هنوز «بعداً» است ⇒ بی‌صدا منقضی نشود
    await flagDeferredPaid(out, reason).catch((err) => console.error('[payments] نشانِ بررسی', out.id, err.message));
  }
  return out;
}

/** قصدِ زنده‌ای که زمانش گذشته ⇒ «expired» (تنبل، با هر خواندن؛ پرداختِ دیرش مسیرِ S35 است). */
async function expireIfDue(row) {
  if (!row || !LIVE.includes(row.status) || Number(row.expires_at) > now()) return row;
  return (await close(row, 'expired', 'timeout')) || one('SELECT * FROM payment_intents WHERE id=$1', [row.id]);
}

/**
 * «پرداخت شد» — فقط پس از تأییدِ مبلغ و ارز (و تأییدِ دومِ ارائه‌دهنده در فراخواننده).
 * @returns {Promise<'applied'|'duplicate_state'>}
 */
async function markPaid(id, { providerRef = '', confirmed = false, source = 'webhook' } = {}) {
  for (let i = 0; i < 3; i++) {
    const res = await tx(async (c) => {
      const row = (await c.query('SELECT * FROM payment_intents WHERE id=$1 FOR UPDATE', [id])).rows[0];
      if (!row) return { outcome: 'ignored' };
      if (row.status === 'paid' || row.status === 'refunded') return { outcome: 'duplicate_state', row };
      //  دیر = بسته‌شده، یا هنوز «زنده» ولی زمانش گذشته (پیش از آن‌که کسی بخواندش)
      const late = !LIVE.includes(row.status) || Number(row.expires_at) <= now();
      if (late && !confirmed) return { outcome: 'unconfirmed', row, late };
      const at = now();
      const next = await casIn(c, row, 'paid', {
        paid_at: at, provider_ref: String(providerRef || '').slice(0, 128),
        needs_review: late || row.needs_review === true,
        status_reason: late ? `late_payment_after_${LIVE.includes(row.status) ? 'expired' : row.status}` : '',
      }, { confirmedLate: late && confirmed });
      if (!next) return { retry: true };
      const problem = await orderPaymentIn(c, next, 'paid', { source, providerRef: next.provider_ref });
      if (problem && !next.needs_review) {
        await c.query('UPDATE payment_intents SET needs_review=true WHERE id=$1', [id]);
        next.needs_review = true;
      }
      return { outcome: 'applied', row: next, late, problem, from: row.status };
    });
    if (res.retry) continue;
    if (res.outcome === 'applied') {
      nudge(id);
      if (res.late || res.problem) await alert(res.row, res.late ? 'late_payment' : res.problem, { from: res.from, source, problem: res.problem || '' });
    }
    if (res.outcome === 'unconfirmed' && res.row) await flagReview(res.row, 'late_payment_unconfirmed', { from: res.row.status, source });
    return res.outcome;
  }
  return 'duplicate_state';
}

// ════════════════════════════════════════════════════════════════════
//  ساختن
// ════════════════════════════════════════════════════════════════════

function parseCreate(body, headerKey) {
  const b = body && typeof body === 'object' ? body : {};
  const kind = String(b.kind || '');
  if (!KINDS.includes(kind)) throw badRequest('نوعِ پرداخت نامعتبر است (pos_sale یا online_order)', 'bad_kind');
  const refId = typeof b.refId === 'string' ? b.refId.trim() : '';
  if (!REF_RE.test(refId)) throw badRequest('شناسهٔ فروش یا سفارش نامعتبر است', 'bad_ref');
  const amount = b.amount;
  if (!Number.isSafeInteger(amount) || amount <= 0 || amount > MAX_AMOUNT_MINOR) {
    throw badRequest('مبلغ باید عددِ صحیحِ مثبت به «پول» (یک‌صدمِ افغانی) باشد', 'bad_amount');
  }
  if (b.currency !== 'AFN') throw badRequest('فقط افغانی (AFN) پذیرفته می‌شود', 'bad_currency');
  const key = String(headerKey || b.idempotencyKey || '').trim();
  if (!key) throw badRequest('کلیدِ یکتایی (idempotencyKey) لازم است', 'idempotency_key_required');
  if (!KEY_RE.test(key)) throw badRequest('کلیدِ یکتایی نامعتبر است', 'bad_idempotency_key');
  if (b.idempotencyKey && headerKey && String(b.idempotencyKey) !== String(headerKey)) {
    throw badRequest('کلیدِ یکتاییِ سرآیند و بدنه یکی نیستند', 'bad_idempotency_key');
  }
  const description = typeof b.description === 'string' ? b.description.trim().slice(0, 120) : '';
  return { kind, refId, amount, currency: 'AFN', key, description };
}

const hashOf = (i) => crypto.createHash('sha256')
  .update(stable({ kind: i.kind, refId: i.refId, amount: i.amount, currency: i.currency })).digest('hex');

/** سفارش: مبلغ از سرور؛ فقط سفارشِ واقعیِ باز با پرداختِ نشده. */
async function orderFor(shopId, input) {
  const o = await one(
    `SELECT id, status, is_test, payment_status, cod_status, currency, total_minor
       FROM online_orders WHERE shop_id=$1 AND id=$2`, [shopId, input.refId]
  );
  if (!o) throw notFound('سفارش پیدا نشد', 'order_not_found');
  if (!(await subscribedFor(shopId))) {
    throw forbidden('پرداختِ آنلاینِ سفارش فقط برای دکانِ دارای اشتراکِ فعال است', 'online_payment_unavailable');
  }
  if (o.is_test || S.isTerminal(o.status) || o.payment_status === 'paid' || !['', 'to_collect'].includes(o.cod_status)
      || !['unpaid', 'pending', 'failed'].includes(o.payment_status)) {
    throw conflict('این سفارش پرداختِ آنلاین نمی‌پذیرد', 'order_not_payable');
  }
  if (o.currency !== input.currency || Number(o.total_minor) !== input.amount) {
    const err = conflict('مبلغ با جمعِ سفارش نمی‌خواند', 'amount_mismatch');
    err.data = { totalMinor: Number(o.total_minor) };
    throw err;
  }
  return o;
}

/**
 * @param {string} shopId از نشست
 * @param {{body:object, headerKey?:string, actor:{id:string}, returnUrl?:string}} p
 *   `returnUrl` فقط از کدِ سرور (ویترین)، هرگز از کاربر.
 * @returns {Promise<{intent:object, created:boolean}>}
 */
async function create(shopId, { body, headerKey = '', actor = {}, returnUrl = '' }) {
  const input = parseCreate(body, headerKey);
  if (input.kind === 'online_order') await orderFor(shopId, input);
  const name = P.activeName();
  const ad = P.adapter(name);
  if (!ad || !ad.configured()) throw forbidden('پرداختِ حساب‌پی پیکربندی نشده است', 'hesabpay_disabled');
  const hash = hashOf(input);
  const at = now();

  const pre = await tx(async (c) => {
    //  ⛔ اول کلید، بعد فروش — هر دو درخواستِ هم‌زمان با یکی از این دو پشتِ هم می‌مانند (بی بن‌بست)
    await c.query('SELECT pg_advisory_xact_lock(hashtext($1))', [`pay-key:${shopId}:${input.key}`]);
    await c.query('SELECT pg_advisory_xact_lock(hashtext($1))', [`pay-ref:${shopId}:${input.kind}:${input.refId}`]);
    const prev = (await c.query('SELECT * FROM payment_intents WHERE shop_id=$1 AND idempotency_key=$2', [shopId, input.key])).rows[0];
    if (prev) {
      if (prev.request_hash !== hash) throw conflict('این کلیدِ یکتایی برای پرداختِ دیگری به کار رفته است', 'idempotency_key_reused');
      return { row: prev, replayed: true };
    }
    const rows = (await c.query(
      `SELECT * FROM payment_intents WHERE shop_id=$1 AND kind=$2 AND ref_id=$3 AND status IN ('created','pending','paid','refunded')`,
      [shopId, input.kind, input.refId]
    )).rows;
    const paid = rows.find((r) => r.status === 'paid' || r.status === 'refunded');
    if (paid) {
      const err = conflict('این فروش پیش‌تر با حساب‌پی پرداخت شده است', 'already_paid');
      err.data = { intentId: paid.id };
      throw err;
    }
    const live = rows.find((r) => LIVE.includes(r.status));
    if (live && Number(live.expires_at) > at) {
      if (Number(live.amount_minor) === input.amount) return { row: live, replayed: true };
      const err = conflict('برای این فروش یک پرداختِ باز با مبلغِ دیگر هست؛ اول لغوش کنید', 'intent_active');
      err.data = { intentId: live.id };
      throw err;
    }
    if (live) {
      const gone = await casIn(c, live, 'expired', { status_reason: 'timeout' });
      if (gone) await orderPaymentIn(c, gone, 'failed', { reason: 'timeout' });
    }
    const id = newId('pi');
    const r = await c.query(
      `INSERT INTO payment_intents (id, shop_id, kind, ref_id, amount_minor, currency, status, provider,
         idempotency_key, request_hash, expires_at, created_by, created_at, updated_at)
       VALUES ($1,$2,$3,$4,$5,$6,'created',$7,$8,$9,$10,$11,$12,$12) RETURNING *`,
      [id, shopId, input.kind, input.refId, input.amount, input.currency, name, input.key, hash,
        at + 15 * 60 * 1000, String(actor.id || ''), at]
    );
    return { row: r.rows[0], replayed: false };
  });
  if (pre.replayed) return { intent: view(await expireIfDue(pre.row)), created: false };

  let session;
  try {
    session = await ad.createSession({
      intentId: pre.row.id, amountMinor: input.amount, currency: input.currency,
      description: input.description || (input.kind === 'pos_sale' ? 'خرید از فروشگاه' : 'سفارشِ آنلاین'),
      returnUrl, callbackUrl: callbackUrl(name),
    });
  } catch (err) {
    console.error(`[payments] ساختنِ جلسه در ${name} نشد (${pre.row.id}): ${err.message}`);
    await close(pre.row, 'failed', 'provider_error');
    const e = new ApiError(502, 'provider_unavailable', 'درگاهِ حساب‌پی پاسخ نداد؛ دوباره تلاش کنید');
    e.data = { intentId: pre.row.id };
    throw e;
  }
  const row = await tx(async (c) => {
    const next = await casIn(c, pre.row, 'pending', {
      provider_session_id: String(session.providerSessionId).slice(0, 200),
      checkout_url: String(session.checkoutUrl).slice(0, 2000),
      expires_at: Math.floor(Number(session.expiresAt)) || at + 15 * 60 * 1000,
    });
    if (next) await orderPaymentIn(c, next, 'pending', {});
    return next;
  });
  const final = row || await one('SELECT * FROM payment_intents WHERE id=$1', [pre.row.id]);
  nudge(final.id);
  return { intent: view(final), created: true };
}

// ════════════════════════════════════════════════════════════════════
//  خواندن (با انتظارِ بلند)، لغو
// ════════════════════════════════════════════════════════════════════

const rowOf = async (shopId, id) => {
  const r = await one('SELECT * FROM payment_intents WHERE shop_id=$1 AND id=$2', [shopId, String(id || '')]);
  if (!r) throw notFound('پرداخت پیدا نشد', 'payment_not_found');
  return r;
};

let waiting = 0;
const waitingByShop = new Map();
const MAX_WAITERS = 500;
const MAX_WAITERS_PER_SHOP = 30;

/**
 * وضعیت؛ با `wait` (ثانیه، تا ۲۵) تا تغییرِ وضعیتِ زنده صبر می‌کند — برنامه پشتِ سرِ هم نمی‌کوبد.
 * `signal` (بسته شدنِ درخواست) انتظار را زود تمام می‌کند. بیش از سقفِ انتظارها ⇒ بی انتظار.
 */
async function get(shopId, id, { wait = 0, signal = null } = {}) {
  let row = await expireIfDue(await rowOf(shopId, id));
  const seconds = Math.min(MAX_WAIT_S, Math.max(0, Math.floor(Number(wait) || 0)));
  const perShop = waitingByShop.get(shopId) || 0;
  if (!seconds || !LIVE.includes(row.status) || waiting >= MAX_WAITERS || perShop >= MAX_WAITERS_PER_SHOP) return view(row);
  const start = row.status;
  waiting++;
  waitingByShop.set(shopId, perShop + 1);
  try {
    const deadline = Date.now() + seconds * 1000;
    while (Date.now() < deadline && !(signal && signal.aborted)) {
      await new Promise((resolve) => {
        const left = Math.min(3000, deadline - Date.now());
        const done = () => { clearTimeout(t); bus.off(row.id, done); if (signal) signal.removeEventListener?.('abort', done); resolve(); };
        const t = setTimeout(done, Math.max(0, left));
        bus.on(row.id, done);
        if (signal) signal.addEventListener?.('abort', done, { once: true });
      });
      row = await expireIfDue(await rowOf(shopId, id));
      if (row.status !== start) break;
    }
  } finally {
    waiting--;
    const n = (waitingByShop.get(shopId) || 1) - 1;
    if (n > 0) waitingByShop.set(shopId, n); else waitingByShop.delete(shopId);
  }
  return view(row);
}

async function cancel(shopId, id, actor = {}) {
  const row = await expireIfDue(await rowOf(shopId, id));
  if (row.status === 'cancelled') return view(row);
  if (!LIVE.includes(row.status)) {
    const err = conflict('این پرداخت دیگر لغوشدنی نیست', 'not_cancellable');
    err.data = { status: row.status };
    throw err;
  }
  const next = await close(row, 'cancelled', 'cancelled_by_staff');
  const final = next || await rowOf(shopId, id);
  if (next) {
    await audit.log({ shopId, userId: actor.id || '', action: 'payment.cancel', targetType: 'payment_intent', targetId: row.id,
      detail: { kind: row.kind, refId: row.ref_id, amountMinor: Number(row.amount_minor) }, ip: actor.ip || '' });
  } else if (final.status !== 'cancelled') {
    const err = conflict('این پرداخت دیگر لغوشدنی نیست', 'not_cancellable');
    err.data = { status: final.status };
    throw err;
  }
  return view(final);
}

// ════════════════════════════════════════════════════════════════════
//  وب‌هوک
// ════════════════════════════════════════════════════════════════════

/** پاسخِ قطعیِ «پرداخت نشد» ِ ارائه‌دهنده در تأییدِ دوم/تطبیق. */
const PROVIDER_NO = Object.freeze(['failed', 'expired', 'cancelled']);

/** تأییدِ دومِ «paid» از خودِ ارائه‌دهنده. */
async function confirmWith(ad, intent) {
  if (typeof ad.fetchStatus !== 'function') return { ok: false, reason: 'no_status_api' };
  let st;
  try { st = await ad.fetchStatus(intent.provider_session_id); } catch (err) {
    console.warn(`[payments] تأییدِ دوم از ${ad.name} نشد (${intent.id}): ${err.message}`);
    return { ok: false, reason: 'status_unreachable', retry: true };
  }
  //  فقط «نه» ِ قطعی پایانی است. pending/unknown/… یعنی درِ وضعیت هنوز هم‌گام نشده (پول شاید رفته)
  //  ⇒ «بعداً» (۵۰۳) تا ارائه‌دهنده همان رویداد را دوباره بفرستد؛ رویداد نهایی نمی‌شود.
  if (st.status !== 'paid') return { ok: false, reason: `provider_says_${st.status}`, retry: !PROVIDER_NO.includes(st.status) };
  if (st.amountMinor !== Number(intent.amount_minor) || st.currency !== intent.currency) {
    return { ok: false, reason: 'amount_mismatch', mismatch: true, got: { amountMinor: st.amountMinor, currency: st.currency } };
  }
  return { ok: true, providerRef: st.providerRef };
}

async function applyEvent(ad, found, ev) {
  const intent = await expireIfDue(found);
  if (ev.status === 'paid') {
    if (intent.status === 'paid' || intent.status === 'refunded') return 'duplicate_state';
    //  وب‌هوک پیش از ثبتِ جلسه رسید (پیدا شده با `reference`) ⇒ ارائه‌دهنده دوباره بفرستد
    if (intent.status === 'created') return 'deferred';
    if (ev.amountMinor !== Number(intent.amount_minor) || ev.currency !== intent.currency) {
      await query('UPDATE payment_intents SET needs_review=true, updated_at=$2 WHERE id=$1', [intent.id, now()]);
      await alert(intent, 'amount_mismatch', { got: { amountMinor: ev.amountMinor, currency: ev.currency } });
      return 'mismatch';
    }
    const late = !LIVE.includes(intent.status) || Number(intent.expires_at) <= now();
    let providerRef = ev.providerRef;
    let confirmed = false;
    if (confirmEnabled() || late) {
      const c = await confirmWith(ad, intent);
      if (c.retry) {
        if (c.reason !== 'status_unreachable') console.warn(`[payments] «paid» ِ ${intent.id} هنوز تأیید نشد (${c.reason}) ⇒ بعداً`);
        return 'deferred';
      }
      if (c.mismatch) {
        await query('UPDATE payment_intents SET needs_review=true, updated_at=$2 WHERE id=$1', [intent.id, now()]);
        await alert(intent, 'amount_mismatch', { got: c.got, source: 'status' });
        return 'mismatch';
      }
      if (!c.ok) {
        //  امضاشده می‌گوید «paid» و درِ وضعیت قطعاً «نه» (یا درِ وضعیت نیست): تناقض ⇒ بررسیِ دستی، هرگز پرداخت‌شده نه
        console.warn(`[payments] «paid» ِ ${intent.id} تأیید نشد: ${c.reason}`);
        await flagReview(intent, late ? 'late_payment_unconfirmed' : 'paid_unconfirmed',
          { cause: c.reason, eventId: ev.eventId || '' });
        return 'unconfirmed';
      }
      confirmed = true;
      providerRef = c.providerRef || providerRef;
    }
    return markPaid(intent.id, { providerRef, confirmed, source: 'webhook' });
  }
  if (['failed', 'expired', 'cancelled'].includes(ev.status)) {
    if (!LIVE.includes(intent.status)) return 'duplicate_state';
    return (await close(intent, ev.status, `provider_${ev.status}`)) ? 'applied' : 'duplicate_state';
  }
  return 'ignored';
}

/*
 *  رویدادِ در حالِ پردازش: `outcome = 'processing:<پایانِ اجاره>'` (ردیفِ پیش از این نسخه: '' با
 *  `received_at`). ارسالِ دوبارهٔ رویدادی که هنوز در کار است ⇒ ۵۰۳ (نه ۲۰۰ ِ «تکراری» که ارائه‌دهنده را از
 *  ارسالِ دوباره بازمی‌داشت)؛ اجارهٔ گذشته (فرآیندی که وسطِ کار مُرد) ⇒ همین بار دوباره. گرفتنِ ردیف
 *  compare-and-set روی همان مقدار است، پس دو پردازشِ هم‌زمانِ یک رویداد پدید نمی‌آید (و markPaid/close هم CAS‌اند).
 */
const EVENT_LEASE_MS = 2 * 60 * 1000;   // یک پردازش (تأییدِ دوم با مهلتِ ۱۰ ثانیه) هرگز این‌قدر نمی‌کشد
const leaseOf = (at) => `processing:${at + EVENT_LEASE_MS}`;
function processingState(prev, at) {
  if (prev.outcome === '') return Number(prev.received_at) + EVENT_LEASE_MS <= at ? 'stale' : 'busy';
  const m = /^processing:(\d+)$/.exec(String(prev.outcome || ''));
  if (!m) return '';
  return Number(m[1]) <= at ? 'stale' : 'busy';
}
const retryLater = (message = 'تأییدِ پرداخت هنوز ممکن نشد؛ دوباره بفرستید') =>
  ({ status: 503, body: { error: { code: 'retry_later', message } } });

/**
 * وب‌هوکِ ارائه‌دهنده — همیشه سریع و یک‌باره. @returns {{status:number, body:object}}
 *   امضای بد ⇒ ۴۰۱؛ رویدادِ تکراری ⇒ ۲۰۰؛ قصدِ ناشناخته ⇒ ۲۰۰ و لاگ (بی طوفانِ تکرار)؛
 *   اختلافِ مبلغ/ارز ⇒ ۲۰۰ و هشدار، بی «paid»؛ تأییدِ دومِ دست‌نیافتنی یا هنوز نامعلوم (pending/unknown)
 *   ⇒ ۵۰۳ و رویدادِ «deferred» تا ارائه‌دهنده دوباره بفرستد؛ فقط «نه» ِ قطعی ⇒ ۲۰۰ `unconfirmed` و بررسی.
 *   همان رویداد هنوز در کار ⇒ ۵۰۳؛ «paid» ِ deferred روی قصدی که دیگر زنده نیست ⇒ needs_review و هشدار.
 */
async function handleWebhook(name, raw, headers) {
  const ad = P.webhookAdapter(name);
  if (!ad || !ad.configured()) return { status: 401, body: { error: { code: 'bad_signature', message: 'امضای درخواست درست نیست' } } };
  const v = await ad.verifyWebhook(raw, headers || {});
  if (!v.ok) {
    console.warn(`[payments] وب‌هوکِ ${name} رد شد: ${v.reason}`);
    return { status: 401, body: { error: { code: 'bad_signature', message: 'امضای درخواست درست نیست' } } };
  }
  const ev = v.event;
  const eventKey = `${name}:${ev.eventId || `sha256:${crypto.createHash('sha256').update(raw).digest('hex')}`}`.slice(0, 300);
  const at = now();
  const ins = await one(
    `INSERT INTO payment_events (id, provider, provider_event_id, type, outcome, raw, received_at)
     VALUES ($1,$2,$3,$4,$7,$5::jsonb,$6) ON CONFLICT (provider_event_id) DO NOTHING RETURNING id`,
    [newId('pe'), name, eventKey, String(ev.status || '').slice(0, 40), JSON.stringify(ev.raw || {}), at, leaseOf(at)]
  );
  let eventRowId = ins && ins.id;
  if (!eventRowId) {
    const prev = await one('SELECT id, outcome, received_at FROM payment_events WHERE provider_event_id=$1', [eventKey]);
    if (!prev) return { status: 200, body: { ok: true, duplicate: true } };
    const busy = processingState(prev, at);
    if (prev.outcome !== 'deferred' && !busy) return { status: 200, body: { ok: true, duplicate: true } };
    if (busy === 'busy') return retryLater('همین رویداد در حالِ پردازش است؛ کمی بعد دوباره بفرستید');
    //  پیش‌تر «بعداً» شد (تأییدِ دوم نرسید) یا پردازشش وسطِ کار ماند ⇒ همین بار دوباره؛ فقط یکی برنده
    const claimed = await one('UPDATE payment_events SET outcome=$3 WHERE id=$1 AND outcome=$2 RETURNING id',
      [prev.id, prev.outcome, leaseOf(at)]);
    if (!claimed) return retryLater('همین رویداد در حالِ پردازش است؛ کمی بعد دوباره بفرستید');
    eventRowId = prev.id;
  }
  const setOutcome = (outcome, intentId = null) => query(
    'UPDATE payment_events SET outcome=$2, intent_id=COALESCE($3, intent_id) WHERE id=$1', [eventRowId, outcome, intentId]
  );
  let intent = ev.sessionId
    ? await one('SELECT * FROM payment_intents WHERE provider=$1 AND provider_session_id=$2', [name, ev.sessionId])
    : null;
  if (!intent && ev.reference) intent = await one('SELECT * FROM payment_intents WHERE provider=$1 AND id=$2', [name, ev.reference]);
  if (!intent) {
    console.warn(`[payments] وب‌هوکِ ${name} برای قصدِ ناشناخته (session=${String(ev.sessionId).slice(0, 40)})`);
    await setOutcome('unknown_intent');
    return { status: 200, body: { ok: true, ignored: 'unknown_intent' } };
  }
  let outcome;
  try {
    outcome = await applyEvent(ad, intent, ev);
  } catch (err) {
    await setOutcome('deferred', intent.id);
    throw err;
  }
  await setOutcome(outcome, intent.id);
  if (outcome === 'deferred') {
    //  «paid» ِ تأییدنشده روی قصدی که دیگر زنده نیست (دیر رسید، یا در همین پردازش منقضی شد) ⇒ دکان ببیند
    if (ev.status === 'paid') {
      const cur = await one('SELECT * FROM payment_intents WHERE id=$1', [intent.id]);
      if (cur && (!LIVE.includes(cur.status) || Number(cur.expires_at) <= now())) {
        await flagDeferredPaid(cur, 'webhook').catch((err) => console.error('[payments] نشانِ بررسی', cur.id, err.message));
      }
    }
    return retryLater();
  }
  return { status: 200, body: { ok: true, outcome } };
}

// ════════════════════════════════════════════════════════════════════
//  تطبیق با ارائه‌دهنده (PAY-21) — قصدهای زنده/منقضیِ ۴۸ ساعتِ اخیر
// ════════════════════════════════════════════════════════════════════

/**
 * برای هر قصدِ created/pending/expired ِ بازهٔ `sinceMs` (پیش‌فرض ۴۸ ساعت) وضعیت را از ارائه‌دهنده
 * می‌پرسد: «paid» ِ تأییدشده ⇒ markPaid (دیر ⇒ هشدار)؛ «failed/expired/cancelled» ⇒ بستنِ زنده؛
 * اختلاف ⇒ هشدار. زنده پیش از منقضی و تازه پیش از کهنه؛ `budgetMs` ⇒ پس از آن زمان بقیه به نوبتِ بعد
 * (`truncated`). قصدِ بسته با «paid» ِ deferred ⇒ needs_review و هشدار (`flagged`).
 * @returns {{checked, paid, closed, mismatched, errors, truncated, flagged}}
 */
async function reconcile({ sinceMs = 48 * 60 * 60 * 1000, limit = 500, budgetMs = 0 } = {}) {
  const out = { checked: 0, paid: 0, closed: 0, mismatched: 0, errors: 0, truncated: false, flagged: 0 };
  const t0 = Date.now();
  const rows = (await query(
    `SELECT * FROM payment_intents WHERE status IN ('created','pending','expired') AND created_at >= $1
        AND provider_session_id IS NOT NULL
      ORDER BY CASE WHEN status = 'expired' THEN 1 ELSE 0 END, created_at DESC, id LIMIT $2`,
    [now() - sinceMs, Math.min(5000, Math.max(1, limit))]
  )).rows;
  for (const r of rows) {
    if (budgetMs > 0 && Date.now() - t0 >= budgetMs) { out.truncated = true; break; }
    //  ساختگی فقط بیرون از تولید (`webhookAdapter`)؛ بی کلید ⇒ رد
    const ad = P.webhookAdapter(r.provider);
    if (!ad || !ad.configured() || typeof ad.fetchStatus !== 'function') continue;
    out.checked++;
    let st;
    try { st = await ad.fetchStatus(r.provider_session_id); } catch { out.errors++; continue; }
    if (st.status === 'paid') {
      if (st.amountMinor !== Number(r.amount_minor) || st.currency !== r.currency) {
        out.mismatched++;
        await query('UPDATE payment_intents SET needs_review=true, updated_at=$2 WHERE id=$1', [r.id, now()]);
        await alert(r, 'amount_mismatch', { got: { amountMinor: st.amountMinor, currency: st.currency }, source: 'reconcile' });
        continue;
      }
      if ((await markPaid(r.id, { providerRef: st.providerRef, confirmed: true, source: 'reconcile' })) === 'applied') out.paid++;
    } else if (['failed', 'expired', 'cancelled'].includes(st.status) && LIVE.includes(r.status)) {
      if (await close(r, st.status, `provider_${st.status}`)) out.closed++;
    } else if (LIVE.includes(r.status)) {
      if (await expireIfDue(r).then((x) => x && x.status === 'expired')) out.closed++;
    }
  }
  //  تورِ ایمنی: قصدِ بسته‌ای که «paid» ِ deferred دارد و هنوز نشانِ بررسی ندارد (مثلاً فرآیند میانِ بستن و
  //  نشان مُرد) ⇒ needs_review و هشدار. قصدِ زنده‌ای که زمانش گذشته در حلقهٔ بالا بسته و همان‌جا نشان می‌خورد.
  const stuck = (await query(
    `SELECT i.* FROM payment_intents i
      WHERE i.status IN ('expired','failed','cancelled') AND i.needs_review = false AND i.created_at >= $1
        AND EXISTS (SELECT 1 FROM payment_events e WHERE e.intent_id = i.id AND e.type = 'paid' AND e.outcome = 'deferred')
      ORDER BY i.created_at DESC LIMIT $2`,
    [now() - sinceMs, Math.min(5000, Math.max(1, limit))]
  )).rows;
  for (const r of stuck) {
    if (await flagDeferredPaid(r, 'reconcile')) out.flagged++;
  }
  return out;
}

// ════════════════════════════════════════════════════════════════════
//  زمان‌بندِ تطبیق — تورِ ایمنیِ وب‌هوکِ گم‌شده یا تأییدِ دومِ دیرکرده
// ════════════════════════════════════════════════════════════════════

/*
 *  هر ۵ دقیقه قصدهای زنده و تازه‌منقضی (۲ ساعتِ اخیر؛ مهلتِ قصد ۱۵ دقیقه است) و هر ۱۲ نوبت (≈ ساعتی،
 *  و نخستین نوبت پس از راه‌اندازی) همان ۴۸ ساعتِ پیش‌فرض. فقط وقتی ارائه‌دهندهٔ فعال پیکربندی شده و درِ
 *  وضعیت دارد؛ بی کلید هیچ پرسشی نمی‌رود. دو نمونهٔ سرور هم‌زمان اجرا نمی‌کنند: قفلِ اجاره‌ای در
 *  `app_config` (`updated_at` = پایانِ اجاره؛ نمونه‌ای که وسطِ کار بمیرد، پس از LEASE_MS آزاد می‌شود).
 *  این کلید در فهرستِ سفیدِ درِ مدیریت (`plans.publicConfig`) نیست و بیرون نمی‌رود.
 */
const RECONCILE_TICK_MS = 5 * 60 * 1000;
const RECONCILE_RECENT_MS = 2 * 60 * 60 * 1000;
const RECONCILE_FULL_EVERY = 12;
const RECONCILE_BUDGET_MS = 4 * 60 * 1000;
const RECONCILE_LEASE_KEY = 'lease:payments.reconcile';
const RECONCILE_LEASE_MS = 10 * 60 * 1000;
const INSTANCE = `${process.pid}.${crypto.randomBytes(6).toString('hex')}`;

/** تطبیق کاری دارد؟ = ارائه‌دهندهٔ فعال پیکربندی شده و درِ وضعیت دارد. */
function reconcileReady() {
  const ad = provider();
  return Boolean(ad && ad.configured() && typeof ad.fetchStatus === 'function');
}

/** اجارهٔ میانِ نمونه‌ها: فقط اگر آزاد، منقضی یا مالِ همین نمونه باشد. */
async function takeReconcileLease(at = now()) {
  const r = await one(
    `INSERT INTO app_config (key, value, updated_at) VALUES ($1, $2, $3)
     ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value, updated_at = EXCLUDED.updated_at
      WHERE app_config.updated_at <= $4 OR app_config.value = $2
     RETURNING key`,
    [RECONCILE_LEASE_KEY, INSTANCE, at + RECONCILE_LEASE_MS, at]
  );
  return Boolean(r);
}
const dropReconcileLease = () => query(
  'UPDATE app_config SET updated_at = 0 WHERE key = $1 AND value = $2', [RECONCILE_LEASE_KEY, INSTANCE]
);

let reconTimer = null;
let reconRunning = false;
let reconTicks = 0;
/**
 * یک نوبتِ زمان‌بند. null ⇒ اجرا نشد (پیکربندی نشده، نوبتِ قبلی هنوز در کار، یا نمونهٔ دیگری قفل دارد).
 * @returns {Promise<null|{checked, paid, closed, mismatched, errors, truncated, wide}>}
 */
async function reconcileTick({ wide } = {}) {
  if (reconRunning || !reconcileReady()) return null;
  reconRunning = true;
  try {
    if (!(await takeReconcileLease())) return null;
    try {
      const full = wide ?? (reconTicks++ % RECONCILE_FULL_EVERY === 0);
      const out = await reconcile({ ...(full ? {} : { sinceMs: RECONCILE_RECENT_MS }), budgetMs: RECONCILE_BUDGET_MS });
      if (out.paid || out.closed || out.mismatched || out.errors || out.truncated || out.flagged) {
        console.log(`[payments] تطبیق: ${JSON.stringify(out)}`);
      }
      return { ...out, wide: full };
    } finally {
      await dropReconcileLease().catch(() => {});
    }
  } finally {
    reconRunning = false;
  }
}
function startReconciler() {
  if (reconTimer) return;
  reconTimer = setInterval(() => reconcileTick().catch((err) => console.error('[payments] تطبیق', err.message)), RECONCILE_TICK_MS);
  if (reconTimer.unref) reconTimer.unref();
}
function stopReconciler() {
  if (reconTimer) clearInterval(reconTimer);
  reconTimer = null;
}

module.exports = {
  FLAG_KEY, METHOD_ID, TRANSITIONS, LATE_PAID_FROM, MAX_WAIT_S,
  canTransition, enabledFor, subscribedFor, configOf, create, get, cancel, handleWebhook, reconcile, markPaid,
  reconcileTick, startReconciler, stopReconciler, RECONCILE_LEASE_KEY,
  view, paymentRow, _bus: bus,
};
