'use strict';
/**
 * S71 و S72 (§۱۹.۱۲، §۲۰.۶) — روش و مسئولِ تحویل پیش از پرداخت و در پیگیری؛ تغییرِ روشِ تحویل تا پیش از جمع‌آوری.
 *
 *   پرچم ── `shop.storefront.delivery_method` (همان قاعدهٔ `orders.flagOf`؛ ⛔ پیش‌فرض خاموش). خاموش ⇒ ویترین بی
 *           `features.deliveryMethod`، پیگیری بی کلیدِ `deliveryMethod` و درِ تغییر ۴۰۴ (بیت‌به‌بیت همان پیشین).
 *   S71 ─── فقط دادهٔ واقعی: «دریافت از دکان» ⇒ مسئولِ بردن خودِ مشتری؛ «ارسال» ⇒ مسئولِ رساندن خودِ دکان، و پس از
 *           سپردن همان روشِ مأموریتِ دکان (`delivery_assignments.mode`): پیکِ خودِ دکان یا شرکتِ بیرونی با نامش.
 *           ⛔ هر حالتِ دیگر (بی مأموریت، `platform` که پذیرفته نمی‌شود، دادهٔ ناشناخته) ⇒ «ارسال به دستِ دکان» —
 *           هرگز پیکِ شبکهٔ VILL3N.
 *   S72 ─── مشتری روشِ تحویل (ارسال ⇄ دریافت، یا ناحیهٔ ارسال) را عوض می‌کند. همان دروازه و قفلِ ORD-14 و CHK-21
 *           (`order-amend.lockOrder`/`blockerOf`): فقط تازه/قبول‌شده، **پرداخت‌نشده**، بی برگهٔ جمع‌آوری، بی پیشنهادِ
 *           بازِ ORD-14 و بی مأموریتِ زنده؛ وضعیت درونِ تراکنش زیرِ قفلِ ردیف دوباره سنجیده می‌شود (شروعِ جمع‌آوری
 *           و سپردن همان ردیف را قفل می‌کنند ⇒ یکی پس از دیگری). هزینه، حداقل، ناحیه و بازه با همان
 *           `storefront-delivery.forCheckout` ِ ثبت؛ زمانِ تقریبی با همان `storefront-eta` (پیش‌نمایش = آن‌چه برگهٔ ثبت
 *           نشان می‌دهد؛ پس از تغییر، پیگیریِ زندهٔ همان سفارش). اقلام و تخفیف دست نمی‌خورند.
 *   رضایت ─ تغییرِ مبلغ بی تأییدِ مشتری ثبت نمی‌شود: بی `expectedTotalMinor` ⇒ ۴۰۹ `confirm_required` با برگهٔ تازه
 *           (هزینه، جمع، زمان) و هیچ تغییری؛ جمعِ دیگر ⇒ ۴۰۹ `price_changed`.
 *   ایمنی ─ فقط صاحبِ سفارش (توکنِ پیگیری + شمارهٔ تأییدشدهٔ همان سفارش)؛ کلیدِ یکتایی در رویداد (تکرار ⇒ همان نتیجه،
 *           همان کلید با بدنهٔ دیگر ⇒ ۴۰۹)؛ رویدادِ `note` با `detail.type = 'fulfillment_changed'` (بی مهاجرت)، `version`
 *           بالا، سابقهٔ عملیات و پوشِ دکان (همان پرچم و گیرنده‌های `shop.order_notify`).
 */
const db = require('../db');
const { one, now, tx } = db;
const { ulid } = require('./ulid');
const orders = require('./orders');
const amend = require('./order-amend');
const sfd = require('./storefront-delivery');
const eta = require('./storefront-eta');
const { LIVE } = require('./delivery-states');
const { ApiError, badRequest, forbidden, notFound, conflict } = require('../middleware/errors');

const FLAG_KEY = 'shop.storefront.delivery_method';
const EVENT_TYPE = 'fulfillment_changed';
const CUSTOMER = Object.freeze({ type: 'customer', id: '' });
const PREPARING = Object.freeze(['picking', 'packed']);
/** مأموریتی که دیگر روشِ این سفارش نیست (پیک نپذیرفت / دکان پس گرفت) */
const DROPPED = Object.freeze(['rejected', 'withdrawn']);
const FA_DIGITS = '۰۱۲۳۴۵۶۷۸۹';
const faNum = (n) => String(n).replace(/[0-9]/g, (d) => FA_DIGITS[Number(d)]);

const flagOf = (shopId) => orders.flagOf(String(shopId), FLAG_KEY);
const str = (v) => (typeof v === 'string' ? v.trim() : '');
const obj = (v) => (v && typeof v === 'object' && !Array.isArray(v) ? v : {});
const asJson = (x) => (x && typeof x === 'object' ? x : (() => { try { return JSON.parse(x); } catch { return {}; } })());
const withData = (err, data) => Object.assign(err, { data });
const zoneIdOf = (address) => obj(obj(asJson(address)).zone).id || null;

/** روش و مسئول از نوعِ سفارش و آخرین مأموریتِ برجا — هرگز پیکِ VILL3N. */
function methodOf(fulfillment, a) {
  if (fulfillment === 'pickup') return { method: 'pickup', responsible: 'customer' };
  if (a && a.mode === 'shop_courier') return { method: 'shop_courier', responsible: 'shop' };
  if (a && a.mode === 'external') {
    return { method: 'external', responsible: 'shop', ...(str(a.external_name) ? { company: str(a.external_name) } : {}) };
  }
  return { method: 'shop_delivery', responsible: 'shop' };
}

/** برگهٔ ثبت: روش‌هایی که این ویترین واقعاً دارد (`features.deliveryMethod`)؛ خاموش ⇒ `undefined`. */
async function storeMethods(sf) {
  if (!(await flagOf(sf.shop_id))) return undefined;
  return {
    ...(sf.accept_delivery === true ? { delivery: methodOf('delivery', null) } : {}),
    ...(sf.accept_pickup === true ? { pickup: methodOf('pickup', null) } : {}),
  };
}

const assignmentOf = (run, shopId, orderId) => run(
  `SELECT mode, external_name, status FROM delivery_assignments
    WHERE shop_id=$1 AND order_id=$2 AND status <> ALL($3::text[]) ORDER BY attempt_no DESC LIMIT 1`,
  [shopId, orderId, DROPPED]
).then((r) => r.rows[0] || null);
const pooled = (t, p) => db.many(t, p).then((rows) => ({ rows }));

/**
 * پیگیری: `{method, responsible, company?, change}`؛ `undefined` = پرچم خاموش. `change.allowed` فقط راهنمای دکمه
 * است — تصمیمِ آخر درونِ تراکنشِ `change`.
 */
async function customerView(sf, orderId) {
  const shopId = sf.shop_id;
  if (!(await flagOf(shopId))) return undefined;
  const row = await one('SELECT id, status, payment_status, fulfillment, address FROM online_orders WHERE shop_id=$1 AND id=$2',
    [shopId, String(orderId || '')]);
  if (!row) return undefined;
  const a = await assignmentOf(pooled, shopId, row.id);
  const picked = Boolean(await one('SELECT 1 AS x FROM order_picks WHERE order_id=$1', [row.id]));
  const events = await db.many(`SELECT kind, detail, created_at FROM online_order_events WHERE order_id=$1 AND kind='note' ORDER BY seq`, [row.id]);
  const pending = Boolean(amend.ledgerOf(events, now()).pending);
  const allowed = !amend.blockerOf(row, false) && !picked && !pending && !(a && LIVE.includes(a.status));
  const view = { ...methodOf(row.fulfillment, a) };
  if (!allowed) return { ...view, change: { allowed: false, preparing: picked || PREPARING.includes(row.status) } };
  const zones = sf.accept_delivery === true && (await sfd.flagOn(shopId))
    ? (await sfd.zonesOf(shopId)).filter((z) => z.active) : [];
  const current = row.fulfillment === 'delivery' ? zoneIdOf(row.address) : null;
  const pin = obj(asJson(row.address)).lat !== undefined;
  const others = zones.filter((z) => z.id !== current).map((z) => ({ id: z.id, name: z.name, feeMinor: z.feeMinor }));
  return {
    ...view,
    change: {
      allowed: true, preparing: false,
      pickup: sf.accept_pickup === true && row.fulfillment === 'delivery',
      delivery: sf.accept_delivery === true && (row.fulfillment === 'pickup' || (!pin && others.length > 0)),
      needsAddress: row.fulfillment === 'pickup',
      zones: row.fulfillment === 'pickup' || !pin ? others : [],
    },
  };
}

function parse(body, sf) {
  const b = obj(body);
  const fulfillment = str(b.fulfillment);
  if (!['delivery', 'pickup'].includes(fulfillment)) throw badRequest('ارسال یا دریافت از دکان؟', 'bad_fulfillment');
  if ((fulfillment === 'delivery' && !sf.accept_delivery) || (fulfillment === 'pickup' && !sf.accept_pickup)) {
    throw badRequest('این روشِ تحویل در این ویترین نیست', 'fulfillment_unavailable');
  }
  const a = obj(b.address);
  const hasAddress = ['text', 'landmark', 'lat', 'lng'].some((k) => a[k] !== undefined && a[k] !== null && a[k] !== '');
  const address = fulfillment === 'delivery' && hasAddress ? orders.parseAddress(a) : null;
  const zoneId = fulfillment === 'delivery' ? str(a.zoneId) : '';
  if (zoneId && !sfd.ID_RE.test(zoneId)) throw badRequest('ناحیه معتبر نیست', 'bad_zone');
  const opt = (v, field) => {
    if (v === undefined || v === null || v === '') return null;
    if (!Number.isSafeInteger(v) || v < 0) throw badRequest(`${field} معتبر نیست`, field === 'بازهٔ زمانی' ? 'bad_slot' : 'bad_expected_total');
    return v;
  };
  return {
    fulfillment, address, zoneId,
    slotStartsAt: opt(b.slotStartsAt, 'بازهٔ زمانی'), expectedTotalMinor: opt(b.expectedTotalMinor, 'جمعِ دیده‌شده'),
  };
}

/** زمانِ تقریبی همان‌طور که برگهٔ ثبت نشان می‌دهد (`GET /:slug/eta`)؛ پرچمِ خاموش، بازه یا بی عدد ⇒ `null`. */
async function quoteEta(sf, fulfillment, zoneId, scheduled) {
  if (scheduled) return null;
  const e = await eta.cachedPublic(sf);
  if (!e || !e.available) return null;
  if (fulfillment === 'pickup') return e.pickup || null;
  if (!zoneId) return e.delivery || null;
  const z = (e.zones || []).find((x) => x.id === zoneId);
  return z ? { minMinutes: z.minMinutes, maxMinutes: z.maxMinutes } : null;
}

const summaryOf = (d) => ({
  id: d.changeId, from: d.from, to: d.to,
  prevTotalMinor: d.prevTotalMinor, totalMinor: d.totalMinor, differenceMinor: d.differenceMinor,
});

/**
 * تغییرِ روشِ تحویلِ سفارشِ همین ویترین با توکنِ پیگیری.
 * @returns {Promise<{replayed:boolean, changed:object, orderId:string}>}
 */
async function change(sf, token, { key, body, ticket = '', session = '' }) {
  const store = require('./storefront');
  const shopId = sf.shop_id;
  const input = parse(body, sf);
  const hash = orders.hashOf(body);
  const orderId = await store.orderIdOfLink(sf, token);   // توکنِ نادرست/باطل ⇒ ۴۰۴
  const who = await store.verification(ticket, session);
  if (!who) throw new ApiError(401, 'verification_required', 'برای تغییرِ روشِ تحویل، ایمیلِ خود را تأیید کنید');
  const zonesOn = await sfd.flagOn(shopId);   // پرچم پیش از تراکنش (درونِ قفل فقط اتصالِ همان تراکنش)
  const pinOn = await require('./plus-code').flagOn(shopId);   // S33: نشانیِ تازه ⇒ Plus Code ِ همان پین

  let out;
  try {
    out = await tx(async (c) => {
      const run = (t, p) => c.query(t, p);
      const { row, events } = await amend.lockOrder(c, shopId, orderId);
      if (row.source !== 'storefront' || row.is_test === true) throw notFound('سفارش پیدا نشد', 'order_not_found');
      if (!store.ownsOrder(row, who)) throw forbidden('این سفارش با ایمیلِ تأییدشدهٔ شما ثبت نشده است', 'not_your_order');
      for (const e of events) {
        const d = e.kind === 'note' ? asJson(e.detail) : {};
        if (d.type === EVENT_TYPE && d.key === key) {
          if (d.hash !== hash) throw conflict('این کلیدِ یکتایی پیش‌تر برای درخواستِ دیگری به کار رفته است', 'idempotency_key_reused');
          return { replayed: true, detail: d, row };
        }
      }
      const blocked = amend.blockerOf(row, false);
      const picked = (await run('SELECT 1 FROM order_picks WHERE order_id=$1', [row.id])).rows.length > 0;
      if (blocked === 'amend_not_allowed' || picked) {
        throw withData(conflict('سفارش در حال آماده‌سازی است؛ روشِ تحویل دیگر عوض نمی‌شود', 'order_preparing'), { current: row.status });
      }
      if (blocked) throw withData(conflict(amend.BLOCK_MESSAGE[blocked], blocked), { current: row.status });
      if (amend.ledgerOf(events, now()).pending) {
        throw conflict('پیشنهادِ دکان برای ویرایشِ این سفارش منتظرِ پاسخِ شماست؛ نخست پاسخ دهید', 'amend_pending');
      }
      const a = await assignmentOf(run, shopId, row.id);
      if (a && LIVE.includes(a.status)) throw conflict('دکان تحویلِ این سفارش را سپرده است؛ با دکان تماس بگیرید', 'delivery_assigned');

      const at = now();
      const prevAddress = obj(asJson(row.address));
      const { zone: _z, ...kept } = prevAddress;
      const prevDelivery = obj(asJson(row.delivery));
      const { slot: _s, ...deliveryKept } = prevDelivery;
      let address = {};
      if (input.fulfillment === 'delivery') {
        address = input.address || (row.fulfillment === 'delivery' ? kept : null);
        if (!address) throw badRequest('نشانی یا پینِ تحویل لازم است', 'bad_address');
      }
      //  بازهٔ خودِ همین سفارش در شمارِ پرشدگی نیاید (تراکنش با هر خطا برمی‌گردد)
      await run('UPDATE online_orders SET scheduled_for=NULL WHERE id=$1', [row.id]);
      const subtotalMinor = Number(row.subtotal_minor);
      const d = await sfd.forCheckout(run, sf, {
        fulfillment: input.fulfillment, address, delivery: deliveryKept, zoneId: input.zoneId, slotStartsAt: input.slotStartsAt,
      }, subtotalMinor, at, { zonesOn });
      const toZone = input.fulfillment === 'delivery' ? zoneIdOf(d.address) : null;
      const fromZone = row.fulfillment === 'delivery' ? zoneIdOf(prevAddress) : null;
      if (input.fulfillment === row.fulfillment && toZone === fromZone) {
        throw conflict('روشِ تحویل همان است که بود', 'method_unchanged');
      }
      const discountMinor = Number(row.discount_minor);
      const deliveryFeeMinor = d.deliveryFeeMinor;
      const totalMinor = subtotalMinor - discountMinor + deliveryFeeMinor;
      const quote = {
        fulfillment: input.fulfillment, zoneName: obj(d.address.zone).name || '',
        subtotalMinor, discountMinor, deliveryFeeMinor, ...sfd.feeParts(d), totalMinor,
        prevDeliveryFeeMinor: Number(row.delivery_fee_minor), prevTotalMinor: Number(row.total_minor),
        minOrderMinor: d.minOrderMinor, scheduledFor: d.scheduledFor,
      };
      if (sfd.belowMin(subtotalMinor, d)) {
        throw withData(badRequest('سبد از حداقلِ سفارشِ این روش کمتر است', 'below_min_order'), { minOrderMinor: d.minOrderMinor, quote });
      }
      if (input.expectedTotalMinor === null) {
        throw withData(conflict('هزینه و جمعِ تازه را ببینید و بپذیرید', 'confirm_required'), { quote, toZone });
      }
      if (input.expectedTotalMinor !== totalMinor) {
        throw withData(conflict('هزینه یا جمع عوض شده است؛ دوباره ببینید', 'price_changed'), { quote, toZone });
      }
      await run(
        `UPDATE online_orders SET fulfillment=$3, address=$4::jsonb, delivery=$5::jsonb, delivery_fee_minor=$6, total_minor=$7,
                scheduled_for=$8, version=version+1, updated_at=$9
          WHERE shop_id=$1 AND id=$2`,
        [shopId, row.id, input.fulfillment, JSON.stringify(require('./plus-code').stamp(d.address || {}, pinOn)), JSON.stringify(d.delivery || {}),
          deliveryFeeMinor, totalMinor, d.scheduledFor, at]
      );
      const side = (f, z, fee) => ({ fulfillment: f, zoneName: z, deliveryFeeMinor: fee });
      const detail = {
        type: EVENT_TYPE, changeId: `fc_${ulid(at)}`, key, hash,
        from: side(row.fulfillment, obj(prevAddress.zone).name || '', Number(row.delivery_fee_minor)),
        to: side(input.fulfillment, quote.zoneName, deliveryFeeMinor),
        prevTotalMinor: Number(row.total_minor), totalMinor, differenceMinor: totalMinor - Number(row.total_minor),
        prevScheduledFor: row.scheduled_for === null || row.scheduled_for === undefined ? null : Number(row.scheduled_for),
        scheduledFor: d.scheduledFor,
      };
      await amend.event(c, shopId, row, CUSTOMER, detail, {}, at);
      await require('./delivery-prefs').dropStaleTenderIn(c, shopId, row.id, at);   //  CHK-24: دریافت از دکان یا هزینهٔ تازه
      return { replayed: false, detail, row };
    });
  } catch (err) {
    //  برگهٔ تازه با همان زمانِ تقریبیِ برگهٔ ثبت — پس از برگشتِ تراکنش (کش و استخر، نه اتصالِ قفل‌شده)
    if (err && err.data && err.data.quote && (err.code === 'confirm_required' || err.code === 'price_changed')) {
      const q = err.data.quote;
      q.eta = await quoteEta(sf, q.fulfillment, err.data.toZone, q.scheduledFor).catch(() => null);
      delete err.data.toZone;
    }
    throw err;
  }
  if (!out.replayed) await afterChange(shopId, out.row, out.detail);
  return { replayed: out.replayed, changed: summaryOf(out.detail), orderId };
}

/** سابقه و پوشِ «روشِ تحویل عوض شد» به اعضای دکان — پس از تراکنش؛ خطایش تغییر را پس نمی‌گیرد. */
async function afterChange(shopId, row, d) {
  const no = Number(row.order_no);
  await require('./audit').log({
    shopId, actorType: 'customer', action: 'order.fulfillment_changed', targetType: 'online_order', targetId: row.id,
    detail: { orderNo: no, from: d.from.fulfillment, to: d.to.fulfillment, prevTotalMinor: d.prevTotalMinor, totalMinor: d.totalMinor },
  }).catch(() => {});
  try {
    const on = require('./order-notify');
    if (!(await on.flagOf(shopId))) return;
    await require('./push').sendTo({ shopId, app: 'shop', onlyUserIds: await on.recipientIds(shopId) }, {
      title: `روشِ تحویل عوض شد — سفارشِ شمارهٔ ${faNum(no)}`,
      body: d.to.fulfillment === 'pickup' ? 'مشتری «دریافت از دکان» را برگزید.' : 'مشتری «ارسال» را برگزید؛ نشانی و هزینه را ببینید.',
      channel: on.CHANNEL,
      data: { type: 'order_fulfillment_changed', orderId: row.id, orderNo: String(no), shopId },
    });
  } catch (err) {
    console.error('[order-delivery-method] اعلان', err && err.message);
  }
}

module.exports = { FLAG_KEY, EVENT_TYPE, flagOf, methodOf, storeMethods, customerView, parse, quoteEta, change };
