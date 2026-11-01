'use strict';
/**
 * ORD-14 — ویرایشِ سفارش پس از ثبت، با رضایتِ ثبت‌شدهٔ مشتری و تسویهٔ تفاوت.
 *
 *   پرچم ── `shop.orders.amend` (همان قاعدهٔ `orders.flagOf`؛ ⛔ پیش‌فرض خاموش) روی `shop.orders`؛
 *           نوشتنِ کارمند کلیدِ اشتراکِ `online_orders` را هم می‌خواهد (SUB-10).
 *   کی ──── فقط [AMEND_STATUSES] (تازه، قبول‌شده) — پیش از «شروعِ جمع‌آوری» (برگهٔ جمع‌آوری
 *           هنوز ساخته نشده و پیکی سپرده نشده: سپردن پس از «بسته‌بندی» است). فقط سفارشِ
 *           **پرداخت‌نشده** (نقد هنگامِ تحویل)؛ پرداخت‌شده ⇒ ۴۰۹ `amend_paid_order`.
 *   چه ──── مقدارِ تازهٔ قلم‌های موجود؛ ۰ = برداشتنِ قلم؛ دست‌کم یک قلم می‌ماند (همه را برداشتن =
 *           لغو). افزودنِ قلم CHK-21 است (فاز ۵). قیمتِ واحد همان قیمتِ ثبت است — هرگز از کلاینت.
 *           مبلغ: همان `S.lineTotalMinor` ِ ساختِ سفارش؛ تخفیف و هزینهٔ ارسال دست نمی‌خورند؛
 *           تخفیفِ بیش از جمعِ تازهٔ اقلام ⇒ ۴۰۹ `amend_discount_exceeds`.
 *   رضایت ─ سفارشِ **ویترین**: فقط خودِ مشتری با توکنِ پیگیری می‌پذیرد یا رد می‌کند، تا [TTL_MS]؛
 *           پس از مهلت «منقضی» و هیچ تغییری. سفارشِ **کارمند** (تلفنی/تلگرام/واتس‌اپ/دستی):
 *           کارمند راهِ رضایت (`consent.via` ∈ [CONSENT_VIA]) و توضیح را ثبت می‌کند و همان دم اعمال
 *           می‌شود — بی رضایتِ ثبت‌شده ۴۰۰ `consent_required`.
 *   تسویه ─ نقد هنگامِ تحویل: مبلغِ دریافتی همان جمعِ تازه است؛ تفاوت (`differenceMinor`، مثبت =
 *           مشتری بیشتر می‌دهد) در رویداد می‌ماند.
 *   ایمنی ─ ردیفِ سفارش `FOR UPDATE`؛ کلیدِ یکتایی در رویداد (تکرار ⇒ همان نتیجه؛ همان کلید با بدنهٔ
 *           دیگر ⇒ ۴۰۹ `idempotency_key_reused`)؛ یک پیشنهادِ باز برای هر سفارش؛ اثرِ انگشتِ قلم‌ها
 *           در پیشنهاد ⇒ اگر قلم یا مبلغ از آن پس عوض شد، پذیرفتن «کهنه» است و هیچ تغییری.
 *   سابقه ─ رویدادهای `note` (نوعِ موجود، مهاجرت نمی‌خواهد) با `detail.type` ∈ [TYPES] (ORD-16).
 *           مشتری فقط نام، مقدار و مبلغ را می‌بیند — نه کارمند، نه توضیحِ داخلی.
 *   پرداخت‌شده (پرچمِ جدای `shop.orders.amend_paid`؛ ⛔ پیش‌فرض خاموش ⇒ همان ۴۰۹ `amend_paid_order`) ── فقط
 *           `payment_status = paid` (در جریان یا بخشی‌بازپرداخت‌شده نه). اعمالِ ویرایش تفاوت را **ثبت** می‌کند
 *           (`settlement`: `refund` = دکان به مشتری بدهکار، `charge` = مشتری بدهکار، با مبلغ؛ بی درگاه، بی تغییرِ
 *           وضعیتِ پرداخت یا نقدِ پیک). تسویه با `settle` (`POST …/amendments/:amendId/settlement`): فقط نقد (D-08)،
 *           یک بار برای هر ویرایش، با کلیدِ یکتایی و `orders.refund`؛ رویدادِ `amend_settled` و سابقه.
 */
const crypto = require('crypto');
const db = require('../db');
const { now, tx } = db;
const { ulid } = require('./ulid');
const S = require('./order-states');
const orders = require('./orders');
const { stable } = require('./idempotency');
const { stripControl } = require('./validate');
const { badRequest, forbidden, notFound, conflict } = require('../middleware/errors');

const FLAG_KEY = 'shop.orders.amend';
const AMEND_STATUSES = Object.freeze(['new', 'accepted']);
const REASONS = Object.freeze(['out_of_stock', 'customer_request', 'price_error', 'other']);
const CONSENT_VIA = Object.freeze(['phone', 'in_person', 'telegram', 'whatsapp']);
const TTL_MS = 30 * 60_000;
const MAX_QTY_MILLI = 10_000_000;
const MAX_CHANGES = orders.MAX_LINES;
const TYPES = Object.freeze({ proposed: 'amend_proposed', applied: 'amend_applied', closed: 'amend_closed', settled: 'amend_settled' });
const PAID_FLAG_KEY = 'shop.orders.amend_paid';
const SETTLE_METHODS = Object.freeze(['cash']);
const AMEND_ID_RE = /^am_[0-9A-Z]{26}$/;

const flagOf = (shopId) => orders.flagOf(shopId, FLAG_KEY);
const paidFlagOf = (shopId) => orders.flagOf(shopId, PAID_FLAG_KEY);
/** آنچه برنامه برای «ویرایشِ سفارش» لازم دارد (`GET /orders/config`، فقط با پرچم). */
const configOf = () => ({
  statuses: [...AMEND_STATUSES], reasons: [...REASONS], consentVia: [...CONSENT_VIA], ttlMinutes: TTL_MS / 60_000,
});
const str = (v) => (typeof v === 'string' ? v.trim() : '');
const clean = (v, max) => stripControl(str(v)).slice(0, max);
const asJson = (x) => (x && typeof x === 'object' ? x : (() => { try { return JSON.parse(x); } catch { return {}; } })());

function parse(body) {
  const b = body && typeof body === 'object' ? body : {};
  if (!Array.isArray(b.changes) || b.changes.length === 0) throw badRequest('تغییری نیامده است', 'bad_changes');
  if (b.changes.length > MAX_CHANGES) throw badRequest(`حداکثر ${MAX_CHANGES} تغییر`, 'bad_changes');
  const seen = new Set();
  const changes = b.changes.map((x) => {
    const c = x && typeof x === 'object' ? x : {};
    const lineNo = c.lineNo;
    const qtyMilli = c.qtyMilli;
    if (!Number.isSafeInteger(lineNo) || lineNo < 1) throw badRequest('شمارهٔ قلم معتبر نیست', 'bad_changes');
    if (!Number.isSafeInteger(qtyMilli) || qtyMilli < 0 || qtyMilli > MAX_QTY_MILLI) {
      throw badRequest('مقدارِ تازه معتبر نیست', 'bad_changes');
    }
    if (seen.has(lineNo)) throw badRequest('هر قلم یک بار', 'bad_changes');
    seen.add(lineNo);
    return { lineNo, qtyMilli };
  });
  const reason = str(b.reason);
  if (reason && !REASONS.includes(reason)) throw badRequest('دلیل معتبر نیست', 'bad_reason');
  const note = clean(b.note, 300);
  if (reason === 'other' && !note) throw badRequest('برای «دلیلِ دیگر» توضیح لازم است', 'note_required');
  let consent = null;
  if (b.consent !== undefined && b.consent !== null) {
    const k = b.consent && typeof b.consent === 'object' ? b.consent : {};
    const via = str(k.via);
    if (!CONSENT_VIA.includes(via)) throw badRequest('راهِ رضایتِ مشتری معتبر نیست', 'bad_consent');
    consent = { via, note: clean(k.note, 300) };
  }
  return { changes, reason, note, consent };
}

/** اثرِ انگشتِ قلم‌ها و مبلغ‌ها — پیشنهادِ روی سفارشِ عوض‌شده پذیرفته نمی‌شود. */
function fingerprint(row, lines) {
  const base = {
    lines: lines.map((l) => [Number(l.line_no), Number(l.qty_milli), Number(l.unit_price_minor)]),
    discount: Number(row.discount_minor), fee: Number(row.delivery_fee_minor),
  };
  return crypto.createHash('sha256').update(stable(base)).digest('hex');
}

/** قلم‌ها و جمعِ تازه از تغییرها — همان گردکردنِ ساختِ سفارش. */
function plan(row, lines, changes) {
  const byNo = new Map(lines.map((l) => [Number(l.line_no), l]));
  const out = [];
  for (const ch of changes) {
    const l = byNo.get(ch.lineNo);
    if (!l) throw badRequest(`قلمِ ${ch.lineNo} در این سفارش نیست`, 'bad_changes');
    const from = Number(l.qty_milli);
    if (from === ch.qtyMilli) throw badRequest(`مقدارِ قلمِ ${ch.lineNo} همان پیشین است`, 'bad_changes');
    const unit = Number(l.unit_price_minor);
    out.push({
      lineNo: ch.lineNo, name: l.name, unit: l.unit, unitPriceMinor: unit,
      fromQtyMilli: from, toQtyMilli: ch.qtyMilli,
      fromLineTotalMinor: Number(l.line_total_minor),
      toLineTotalMinor: ch.qtyMilli === 0 ? 0 : S.lineTotalMinor(ch.qtyMilli, unit),
    });
  }
  const changed = new Map(out.map((c) => [c.lineNo, c]));
  const kept = lines.filter((l) => !changed.has(Number(l.line_no)) || changed.get(Number(l.line_no)).toQtyMilli > 0);
  if (kept.length === 0) throw conflict('همهٔ قلم‌ها برداشته می‌شوند — سفارش را لغو کنید', 'amend_empty');
  const subtotalMinor = lines.reduce((s, l) => {
    const c = changed.get(Number(l.line_no));
    return s + (c ? c.toLineTotalMinor : Number(l.line_total_minor));
  }, 0);
  const discountMinor = Number(row.discount_minor);
  if (discountMinor > subtotalMinor) throw conflict('تخفیفِ سفارش از جمعِ تازهٔ اقلام بیشتر می‌شود', 'amend_discount_exceeds');
  const totalMinor = subtotalMinor - discountMinor + Number(row.delivery_fee_minor);
  const prevTotalMinor = Number(row.total_minor);
  return {
    changes: out, itemsCount: kept.length, prevSubtotalMinor: Number(row.subtotal_minor), subtotalMinor,
    prevTotalMinor, totalMinor, differenceMinor: totalMinor - prevTotalMinor,
  };
}

/** وضعیتِ پیشنهادها از رویدادهای سفارش (به ترتیب). */
function ledgerOf(events, at) {
  const items = new Map();
  for (const e of events) {
    if (e.kind !== 'note') continue;
    const d = asJson(e.detail);
    if (d.type === TYPES.proposed || d.type === TYPES.applied) {
      items.set(d.amendId, { ...d, status: d.type === TYPES.applied ? 'applied' : 'pending', createdAt: Number(e.created_at) });
    } else if (d.type === TYPES.closed && items.has(d.amendId)) {
      const it = items.get(d.amendId);
      it.status = d.outcome;
      it.closedAt = Number(e.created_at);
      if (d.settlement) it.settlement = d.settlement;
    } else if (d.type === TYPES.settled && items.has(d.amendId)) {
      items.get(d.amendId).settled = { at: Number(e.created_at), method: d.method, amountMinor: d.amountMinor, key: d.key, hash: d.hash };
    }
  }
  for (const it of items.values()) if (it.status === 'pending' && at >= it.expiresAt) it.status = 'expired';
  const list = [...items.values()];
  return { list, pending: list.find((x) => x.status === 'pending') || null, last: list[list.length - 1] || null };
}

async function lockOrder(c, shopId, id) {
  const row = (await c.query('SELECT * FROM online_orders WHERE shop_id=$1 AND id=$2 FOR UPDATE', [shopId, String(id || '')])).rows[0];
  if (!row) throw notFound('سفارش پیدا نشد', 'order_not_found');
  const lines = (await c.query('SELECT * FROM online_order_lines WHERE order_id=$1 ORDER BY line_no', [row.id])).rows;
  const events = (await c.query('SELECT * FROM online_order_events WHERE order_id=$1 ORDER BY seq', [row.id])).rows;
  return { row, lines, events };
}

async function event(c, shopId, row, actor, detail, { reason = '', note = '' } = {}, at = now()) {
  const seq = Number((await c.query(
    'SELECT COALESCE(MAX(seq), 0) + 1 AS n FROM online_order_events WHERE order_id=$1', [row.id]
  )).rows[0].n);
  await c.query(
    `INSERT INTO online_order_events (id, order_id, shop_id, seq, kind, from_status, to_status,
       actor_type, actor_id, reason, note, detail, created_at)
     VALUES ($1,$2,$3,$4,'note',$5,$5,$6,$7,$8,$9,$10::jsonb,$11)`,
    [ulid(at), row.id, shopId, seq, row.status, actor.type, actor.id || '', reason, note, JSON.stringify(detail), at]
  );
}

/** دلیلِ «دیگر نمی‌شود» برای سفارشِ همین حالا؛ `null` = ویرایش‌پذیر. */
function blockerOf(row, paidOn = false) {
  if (!AMEND_STATUSES.includes(row.status)) return 'amend_not_allowed';
  if (row.payment_status !== 'unpaid' && !(paidOn && row.payment_status === 'paid')) return 'amend_paid_order';
  return null;
}

/** سفارشِ پرداخت‌شده ⇒ تفاوتی که باید تسویه شود (فقط ثبت)؛ پرداخت‌نشده ⇒ `undefined` (همان رویدادِ پیشین) */
function settlementOf(row, p) {
  if (row.payment_status !== 'paid') return undefined;
  const d = Number(p.differenceMinor);
  return { kind: d < 0 ? 'refund' : d > 0 ? 'charge' : 'none', amountMinor: Math.abs(d), paidTotalMinor: Number(p.prevTotalMinor) };
}
const BLOCK_MESSAGE = {
  amend_not_allowed: 'این سفارش دیگر ویرایش‌پذیر نیست (جمع‌آوری شروع شده یا پایان یافته)',
  amend_paid_order: 'سفارشِ پرداخت‌شده این‌جا ویرایش نمی‌شود',
};

/** قلم‌ها و مبلغِ سفارش را به جمعِ تازه می‌برد — درونِ تراکنشِ قفل‌شده. */
async function applyIn(c, shopId, row, p, at) {
  for (const ch of p.changes) {
    if (ch.toQtyMilli === 0) {
      await c.query('DELETE FROM online_order_lines WHERE order_id=$1 AND line_no=$2', [row.id, ch.lineNo]);
    } else {
      await c.query(
        'UPDATE online_order_lines SET qty_milli=$3, line_total_minor=$4 WHERE order_id=$1 AND line_no=$2',
        [row.id, ch.lineNo, ch.toQtyMilli, ch.toLineTotalMinor]
      );
    }
  }
  await c.query(
    `UPDATE online_orders SET subtotal_minor=$3, total_minor=$4, items_count=$5, version=version+1, updated_at=$6
      WHERE shop_id=$1 AND id=$2`,
    [shopId, row.id, p.subtotalMinor, p.totalMinor, p.itemsCount, at]
  );
}

const amounts = (p) => ({
  changes: p.changes, itemsCount: p.itemsCount, prevSubtotalMinor: p.prevSubtotalMinor, subtotalMinor: p.subtotalMinor,
  prevTotalMinor: p.prevTotalMinor, totalMinor: p.totalMinor, differenceMinor: p.differenceMinor,
});

/** نمای کارمند: همه‌چیز جز کلید و اثرِ انگشت. */
function staffView(it) {
  if (!it) return null;
  return {
    id: it.amendId, status: it.status, reason: it.reason || '', consent: it.consent || null,
    changes: it.changes, prevTotalMinor: it.prevTotalMinor, totalMinor: it.totalMinor, differenceMinor: it.differenceMinor,
    expiresAt: it.expiresAt ?? null, createdAt: it.createdAt, closedAt: it.closedAt ?? null,
    //  ویرایشِ سفارشِ پرداخت‌شده (پرچمِ `shop.orders.amend_paid`) — بی آن کلید نیست (همان پاسخِ پیشین)
    ...(it.settlement ? { settlement: settlementView(it) } : {}),
  };
}

function settlementView(it) {
  const s = it.settlement;
  return {
    kind: s.kind, amountMinor: s.amountMinor, paidTotalMinor: s.paidTotalMinor,
    settledAt: it.settled ? it.settled.at : null, method: it.settled ? it.settled.method : null,
  };
}

/** نمای مشتری: نام، مقدار و مبلغ — نه کارمند، نه توضیحِ داخلی، نه راهِ رضایت. */
function customerView(it) {
  if (!it) return null;
  return {
    id: it.amendId, status: it.status, reason: it.reason || '',
    changes: it.changes.map((c) => ({
      name: c.name, unit: c.unit, fromQtyMilli: c.fromQtyMilli, toQtyMilli: c.toQtyMilli,
      fromLineTotalMinor: c.fromLineTotalMinor, toLineTotalMinor: c.toLineTotalMinor,
    })),
    prevTotalMinor: it.prevTotalMinor, totalMinor: it.totalMinor, differenceMinor: it.differenceMinor,
    expiresAt: it.expiresAt ?? null, createdAt: it.createdAt,
    ...(it.settlement ? { settlement: { kind: it.settlement.kind, amountMinor: it.settlement.amountMinor, settled: !!it.settled } } : {}),
  };
}

/**
 * پیشنهاد (ویترین) یا ویرایشِ همان‌دمِ با رضایتِ ثبت‌شده (کارمند).
 * @returns {Promise<{replayed:boolean, amendment:object, order:object}>}
 */
async function propose(shopId, id, { key, body, actor }) {
  const p = parse(body);
  const hash = orders.hashOf(body);
  const oid = String(id || '');
  const paidOn = await paidFlagOf(shopId);
  const out = await tx(async (c) => {
    const { row, lines, events } = await lockOrder(c, shopId, oid);
    const at = now();
    //  تکرارِ همان درخواست ⇒ همان نتیجه (پیش از هر سنجشِ دیگر)
    for (const e of events) {
      const d = e.kind === 'note' ? asJson(e.detail) : {};
      if ((d.type === TYPES.proposed || d.type === TYPES.applied) && d.key === key) {
        if (d.hash !== hash) throw conflict('این کلیدِ یکتایی پیش‌تر برای درخواستِ دیگری به کار رفته است', 'idempotency_key_reused');
        return { replayed: true, amendId: d.amendId };
      }
    }
    const blocked = blockerOf(row, paidOn);
    if (blocked) {
      const err = conflict(BLOCK_MESSAGE[blocked], blocked);
      err.current = row.status;
      throw err;
    }
    if (ledgerOf(events, at).pending) throw conflict('برای این سفارش پیشنهادِ بازی منتظرِ مشتری است', 'amend_pending');
    const storefront = row.source === 'storefront';
    if (storefront && p.consent) throw badRequest('مشتریِ ویترین خودش با پیوندِ پیگیری می‌پذیرد', 'consent_via_link');
    if (!storefront && !p.consent) throw badRequest('رضایتِ مشتری (راه و توضیح) را ثبت کنید', 'consent_required');
    const pl = plan(row, lines, p.changes);
    const amendId = `am_${ulid(at)}`;
    const common = { amendId, key, hash, reason: p.reason, ...amounts(pl) };
    if (storefront) {
      //  نسخه بالا می‌رود تا صفِ زندهٔ دستگاه‌ها و صفحهٔ پیگیری تازه شوند؛ اثرِ انگشت پس از آن هم همان است
      await c.query('UPDATE online_orders SET version=version+1, updated_at=$3 WHERE shop_id=$1 AND id=$2', [shopId, row.id, at]);
      await event(c, shopId, row, actor, {
        type: TYPES.proposed, ...common, base: fingerprint(row, lines), expiresAt: at + TTL_MS,
      }, p, at);
    } else {
      await applyIn(c, shopId, row, pl, at);
      await event(c, shopId, row, actor, {
        type: TYPES.applied, ...common, consent: { ...p.consent, by: 'staff' }, settlement: settlementOf(row, pl),
      }, p, at);
    }
    return { replayed: false, amendId };
  });
  return { ...out, ...(await viewOf(shopId, oid, out.amendId, actor)) };
}

async function viewOf(shopId, oid, amendId, actor) {
  const order = await orders.get(shopId, oid, actor);
  const evs = (await db.query('SELECT kind, detail, created_at FROM online_order_events WHERE order_id=$1 ORDER BY seq', [oid])).rows;
  const it = ledgerOf(evs, now()).list.find((x) => x.amendId === amendId);
  return { amendment: staffView(it), order };
}

/**
 * بستنِ پیشنهادِ باز: `accept` / `reject` (مشتری، با توکن) یا `withdraw` (کارمند).
 * تکرارِ همان تصمیم ⇒ همان نتیجه، بی رویدادِ دوم. پیشنهادِ گذشته از مهلت یا روی سفارشِ
 * عوض‌شده **ثبت** می‌شود («منقضی»/«کهنه») و پاسخش ۴۰۹ است، نه خطای بی‌اثر.
 * @returns {Promise<{status:string, unchanged:boolean, code?:string}>}
 */
async function decide(shopId, id, amendId, decision, actor) {
  if (!AMEND_ID_RE.test(String(amendId || ''))) throw notFound('پیشنهاد پیدا نشد', 'amend_not_found');
  if (!['accept', 'reject', 'withdraw'].includes(decision)) throw badRequest('تصمیم معتبر نیست', 'bad_decision');
  const paidOn = await paidFlagOf(shopId);
  return tx(async (c) => {
    const { row, lines, events } = await lockOrder(c, shopId, id);
    const at = now();
    const led = ledgerOf(events, at);
    const it = led.list.find((x) => x.amendId === amendId);
    if (!it || it.type === TYPES.applied) throw notFound('پیشنهاد پیدا نشد', 'amend_not_found');
    const wanted = decision === 'accept' ? 'applied' : decision === 'reject' ? 'rejected' : 'withdrawn';
    if (it.status === wanted) return { status: it.status, unchanged: true };
    const close = async (outcome) => event(c, shopId, row, actor, { type: TYPES.closed, amendId, outcome }, {}, at);
    if (it.status === 'expired') {
      //  مهلت گذشته: «منقضی» یک بار ثبت می‌شود و هیچ تغییری در سفارش نیست
      if (it.closedAt == null) await close('expired');
      return { status: 'expired', unchanged: false, code: 'amend_expired' };
    }
    if (it.status !== 'pending') return { status: it.status, unchanged: false, code: 'amend_closed' };
    if (decision !== 'accept') {
      await close(wanted);
      return { status: wanted, unchanged: false };
    }
    if (blockerOf(row, paidOn) || fingerprint(row, lines) !== it.base) {
      await close('stale');
      return { status: 'stale', unchanged: false, code: 'order_changed' };
    }
    await applyIn(c, shopId, row, it, at);
    await event(c, shopId, row, actor, {
      type: TYPES.closed, amendId, outcome: 'applied', consent: { via: 'link', by: 'customer' }, settlement: settlementOf(row, it),
    }, {}, at);
    return { status: 'applied', unchanged: false };
  });
}

/** آخرین پیشنهادِ سفارش برای کارمند (`GET /shop/orders/:id`). */
async function staffOf(shopId, orderId) {
  const evs = (await db.query(
    'SELECT kind, detail, created_at FROM online_order_events WHERE shop_id=$1 AND order_id=$2 ORDER BY seq',
    [shopId, String(orderId || '')]
  )).rows;
  return staffView(ledgerOf(evs, now()).last);
}

/**
 * نمای پیگیریِ مشتری با کلیدِ `amendment` (آخرین پیشنهاد) — فقط با پرچم؛ خاموش ⇒ همان شیءِ
 * پیشین، بیت‌به‌بیت. ویرایشِ همان‌دمِ کارمند (سفارشِ کارمند) در ویترین نیست.
 */
async function withAmendment(shopId, orderId, view) {
  if (!(await flagOf(shopId))) return view;
  const evs = (await db.query(
    'SELECT kind, detail, created_at FROM online_order_events WHERE shop_id=$1 AND order_id=$2 ORDER BY seq',
    [shopId, String(orderId || '')]
  )).rows;
  const last = ledgerOf(evs, now()).list.filter((x) => x.type === TYPES.proposed).pop() || null;
  return { ...view, amendment: customerView(last) };
}

function parseSettle(body) {
  const b = body && typeof body === 'object' ? body : {};
  const method = str(b.method);
  if (!SETTLE_METHODS.includes(method)) throw badRequest('روشِ تسویه فقط نقد است (بی درگاه)', 'bad_method');
  return { method, note: clean(b.note, 300) };
}

/**
 * تسویهٔ تفاوتِ ویرایشِ سفارشِ پرداخت‌شده — فقط **ثبت** (نقد؛ نه درگاه، نه وضعیتِ پرداخت، نه نقدِ پیک):
 * `refund` = دکان به مشتری پس داد، `charge` = از مشتری گرفت. یک بار برای هر ویرایش؛ تکرارِ همان درخواست ⇒ همان
 * نتیجه؛ همان کلید با بدنهٔ دیگر یا برای ویرایشِ دیگر ⇒ ۴۰۹ `idempotency_key_reused`.
 * @returns {Promise<{replayed:boolean, kind?:string, amountMinor?:number, orderNo?:number, amendment:object, order:object}>}
 */
async function settle(shopId, id, amendId, { key, body, actor }) {
  if (!(await paidFlagOf(shopId))) throw forbidden('ویرایشِ سفارشِ پرداخت‌شده برای این دکان روشن نیست', 'amend_paid_disabled');
  if (!AMEND_ID_RE.test(String(amendId || ''))) throw notFound('ویرایش پیدا نشد', 'amend_not_found');
  const p = parseSettle(body);
  const hash = orders.hashOf(body);
  const oid = String(id || '');
  const out = await tx(async (c) => {
    const { row, events } = await lockOrder(c, shopId, oid);
    for (const e of events) {
      const d = e.kind === 'note' ? asJson(e.detail) : {};
      if (d.type !== TYPES.settled || d.key !== key) continue;
      if (d.amendId !== amendId || d.hash !== hash) throw conflict('این کلیدِ یکتایی پیش‌تر برای درخواستِ دیگری به کار رفته است', 'idempotency_key_reused');
      return { replayed: true };
    }
    const it = ledgerOf(events, now()).list.find((x) => x.amendId === amendId);
    if (!it || it.status !== 'applied' || !it.settlement) throw notFound('ویرایشِ پرداخت‌شده‌ای با این شناسه نیست', 'amend_not_found');
    if (it.settled) throw conflict('تفاوتِ این ویرایش پیش‌تر تسویه شده است', 'already_settled');
    if (it.settlement.kind === 'none' || !(it.settlement.amountMinor > 0)) throw conflict('این ویرایش تفاوتی ندارد', 'nothing_to_settle');
    const at = now();
    await c.query('UPDATE online_orders SET version=version+1, updated_at=$3 WHERE shop_id=$1 AND id=$2', [shopId, row.id, at]);
    await event(c, shopId, row, actor, {
      type: TYPES.settled, amendId, kind: it.settlement.kind, amountMinor: it.settlement.amountMinor, method: p.method, key, hash,
    }, { note: p.note }, at);
    return { replayed: false, orderNo: Number(row.order_no), kind: it.settlement.kind, amountMinor: it.settlement.amountMinor };
  });
  return { ...out, ...(await viewOf(shopId, oid, amendId, actor)) };
}

const MAX_OPEN_SETTLEMENTS = 100;

/**
 * حسابِ تسویهٔ ویرایشِ سفارش‌های پرداخت‌شدهٔ دکان (برای «تطبیقِ دریافت و بازپرداخت»): `open` ── تفاوت‌هایی که هنوز
 * تسویه نشده‌اند (همهٔ زمان‌ها، کهنه‌ترین اول، حداکثر ۱۰۰)، `settled` ── جمعِ تسویه‌های بازهٔ [w] به تفکیکِ نوع.
 * سفارشِ آزمایشی شمرده نمی‌شود.
 */
async function settlementsOf(shopId, w) {
  const evs = (await db.query(
    `SELECT e.order_id, o.order_no, e.detail, e.created_at
       FROM online_order_events e JOIN online_orders o ON o.id = e.order_id AND o.shop_id = e.shop_id
      WHERE e.shop_id = $1 AND e.kind = 'note' AND o.is_test = false
        AND (e.detail->>'type' = $2 OR (e.detail->>'type' = ANY($3::text[]) AND e.detail->'settlement' IS NOT NULL))
      ORDER BY e.created_at, e.seq`,
    [String(shopId), TYPES.settled, [TYPES.applied, TYPES.closed]]
  )).rows;
  const due = new Map();
  const settled = { refundMinor: 0, chargeMinor: 0, count: 0 };
  for (const e of evs) {
    const d = asJson(e.detail);
    if (d.type === TYPES.settled) {
      due.delete(d.amendId);
      const at = Number(e.created_at);
      if (at >= w.from && at < w.to) {
        settled.count++;
        if (d.kind === 'refund') settled.refundMinor += Number(d.amountMinor); else settled.chargeMinor += Number(d.amountMinor);
      }
    } else if ((d.type === TYPES.applied || d.outcome === 'applied') && d.settlement.kind !== 'none' && d.settlement.amountMinor > 0) {
      due.set(d.amendId, {
        orderId: e.order_id, orderNo: Number(e.order_no), amendId: d.amendId, kind: d.settlement.kind,
        amountMinor: Number(d.settlement.amountMinor), appliedAt: Number(e.created_at),
      });
    }
  }
  const open = [...due.values()];
  return {
    open: open.slice(0, MAX_OPEN_SETTLEMENTS),
    openRefundMinor: open.filter((x) => x.kind === 'refund').reduce((a, x) => a + x.amountMinor, 0),
    openChargeMinor: open.filter((x) => x.kind === 'charge').reduce((a, x) => a + x.amountMinor, 0),
    settled,
  };
}

module.exports = {
  FLAG_KEY, PAID_FLAG_KEY, AMEND_STATUSES, REASONS, CONSENT_VIA, SETTLE_METHODS, TTL_MS, TYPES,
  flagOf, paidFlagOf, configOf, parse, plan, ledgerOf, propose, decide, settle, settlementsOf, staffOf, withAmendment,
};
