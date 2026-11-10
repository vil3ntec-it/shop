'use strict';
/**
 * CHK-21 — افزودنِ قلم به همان سفارشِ ویترین تا پیش از «شروعِ جمع‌آوری» (S38).
 *
 *   پرچم ── `shop.orders.add_items` (همان قاعدهٔ `orders.flagOf`؛ ⛔ پیش‌فرض خاموش). خاموش ⇒ درِ مشتری ۴۰۴ و
 *           پیگیری بی کلیدِ `addItems` (بیت‌به‌بیت همان پیشین).
 *   کی ──── همان دروازهٔ ORD-14 (`order-amend.blockerOf`): تازه یا قبول‌شده و **پرداخت‌نشده**. وضعیت درونِ
 *           تراکنش و زیرِ قفلِ ردیفِ سفارش (`order-amend.lockOrder`، `FOR UPDATE`) دوباره سنجیده می‌شود؛
 *           «شروعِ جمع‌آوری» (`order-picking.start`) و هر گذارِ دیگر همان ردیف را قفل می‌کنند ⇒ یکی پس از
 *           دیگری، هرگز قلمِ تازه روی برگهٔ ساخته‌شده. برگهٔ جمع‌آوریِ موجود یا پیشنهادِ بازِ ORD-14 ⇒ نه.
 *           پس از آن ۴۰۹ `order_preparing` («در حال آماده‌سازی است»؛ قلمِ تازه با سفارشِ تازه).
 *   کیست ── فقط صاحبِ سفارش: توکنِ پیگیری (`orderIdOfLink`، سفارشِ همین ویترین) **و** شمارهٔ تأییدشده (بلیت
 *           یا نشستِ حساب) برابرِ شمارهٔ همان سفارش — بی آن ۴۰۱ `verification_required`، شمارهٔ دیگر ۴۰۳
 *           `not_your_order`. دکان و سفارش فقط از نشانی و توکن، نه از بدنه.
 *   قیمت ── مثلِ ثبت (`storefront.pricer`): قیمت و «هنوز در ویترین» همان لحظه از دفتر (`LIVE_PRODUCT`)، کالای
 *           «پیدا نشد» ِ PCK-14، سقفِ هر سفارش (روی مقدارِ کلِ همان کالا در سفارش) و موجودیِ کاتالوگ
 *           (`catalog.checkOrder`، پرچمِ `shop.catalog`)؛ مبلغِ ردیف `S.lineTotalMinor`. کالای موجود با همان
 *           قیمتِ واحد ⇒ مقدار روی همان قلم؛ با قیمتِ دیگر ⇒ قلمِ تازه (قیمتِ ثبت‌شده هرگز عوض نمی‌شود).
 *   تخفیف ─ تخفیفِ ثبت‌شده می‌ماند و کدِ تخفیف (یک‌بارمصرف یا سقف‌دار) دوباره به کار نمی‌رود و مصرفِ تازه
 *           نمی‌سازد. با پرچمِ تخفیفِ ویترین فقط قاعده‌های **خودکار** با همان هسته (`storefront-promos.evaluate`)
 *           و همان لحظه: اثرِ سبدِ تازه منهای اثرِ سبدِ پیشین (هرگز منفی؛ تخفیف هرگز بیش از جمعِ اقلام).
 *           هزینهٔ ارسال و زمانِ تحویل همان می‌مانند (S38).
 *   دیده ── `expectedTotalMinor` (جمعِ تازه‌ای که مشتری دید) ⇒ اگر فرق کند ۴۰۹ `price_changed` با برگهٔ تازه.
 *   ایمنی ─ کلیدِ یکتایی (`Idempotency-Key`) در رویداد: تکرار ⇒ همان نتیجه بی اثرِ دوم (حتی پس از شروعِ
 *           جمع‌آوری)؛ همان کلید با بدنهٔ دیگر ⇒ ۴۰۹ `idempotency_key_reused`.
 *   سابقه ─ رویدادِ `note` (نوعِ موجود، بی مهاجرت) با `detail.type = 'items_added'` (ORD-16)، `version` بالا
 *           (صفِ زندهٔ دستگاه‌ها و صفحهٔ پیگیری)، سطرِ سابقهٔ عملیات و پوشِ اعلانِ سفارش به اعضای دکان
 *           (همان پرچم و گیرنده‌های `shop.order_notify`؛ خاموش ⇒ فقط رویداد و سابقه).
 */
const db = require('../db');
const { now, tx } = db;
const { ulid } = require('./ulid');
const S = require('./order-states');
const orders = require('./orders');
const amend = require('./order-amend');
const { stripControl } = require('./validate');
const { ApiError, badRequest, forbidden, notFound, conflict } = require('../middleware/errors');

const FLAG_KEY = 'shop.orders.add_items';
const EVENT_TYPE = 'items_added';
const MAX_ADD_LINES = 20;
const CUSTOMER = Object.freeze({ type: 'customer', id: '' });
const PREPARING = Object.freeze(['picking', 'packed']);
const FA_DIGITS = '۰۱۲۳۴۵۶۷۸۹';
const faNum = (n) => String(n).replace(/[0-9]/g, (d) => FA_DIGITS[Number(d)]);

const flagOf = (shopId) => orders.flagOf(shopId, FLAG_KEY);
const str = (v) => (typeof v === 'string' ? v.trim() : '');
const obj = (v) => (v && typeof v === 'object' && !Array.isArray(v) ? v : {});
const asJson = (x) => (x && typeof x === 'object' ? x : (() => { try { return JSON.parse(x); } catch { return {}; } })());
const withData = (err, data) => Object.assign(err, { data });

function parse(body) {
  const { PRODUCT_ID_RE, MAX_QTY_MILLI } = require('./storefront');
  const b = obj(body);
  if (!Array.isArray(b.lines) || b.lines.length === 0) throw badRequest('قلمی نیامده است', 'bad_lines');
  if (b.lines.length > MAX_ADD_LINES) throw badRequest(`حداکثر ${MAX_ADD_LINES} قلم در هر بار`, 'bad_lines');
  const seen = new Set();
  const lines = b.lines.map((l, i) => {
    const x = obj(l);
    const productId = str(x.productId);
    if (!PRODUCT_ID_RE.test(productId)) throw badRequest(`کالای قلمِ ${i + 1} معتبر نیست`, 'bad_lines');
    if (seen.has(productId)) throw badRequest('یک کالا دو بار آمده است', 'bad_lines');
    seen.add(productId);
    if (!Number.isSafeInteger(x.qtyMilli) || x.qtyMilli < 1 || x.qtyMilli > MAX_QTY_MILLI) {
      throw badRequest(`مقدارِ قلمِ ${i + 1} معتبر نیست`, 'bad_lines');
    }
    return { productId, qtyMilli: x.qtyMilli, note: stripControl(str(x.note)).slice(0, 200) };
  });
  let expectedTotalMinor = null;
  if (b.expectedTotalMinor !== undefined && b.expectedTotalMinor !== null) {
    if (!Number.isSafeInteger(b.expectedTotalMinor) || b.expectedTotalMinor < 0) throw badRequest('جمعِ دیده‌شده معتبر نیست', 'bad_expected_total');
    expectedTotalMinor = b.expectedTotalMinor;
  }
  return { lines, expectedTotalMinor };
}

const pricedOf = (l) => ({
  lineNo: Number(l.line_no), productId: l.product_id, qtyMilli: Number(l.qty_milli),
  unitPriceMinor: Number(l.unit_price_minor), lineTotalMinor: Number(l.line_total_minor),
});

/** قلم‌های تازه با قیمتِ همین لحظه ⇒ تغییرها و قلم‌های پس از افزودن (همان سنجش‌های ثبت). */
async function planIn(c, shopId, from, input, lines) {
  const sf = require('./storefront');
  const ids = input.lines.map((l) => l.productId);
  const rows = (await c.query(
    `SELECT ${sf.PRODUCT_COLS} FROM ${from}
      WHERE sp.shop_id = $1 AND sp.product_id = ANY($2::text[]) AND sp.published AND ${sf.LIVE_PRODUCT}`,
    [shopId, ids]
  )).rows;
  const byId = new Map(rows.map((r) => [r.id, sf.productView(r)]));
  const run = (t, p) => c.query(t, p);
  const unavailable = await require('./order-picking').unorderable(run, shopId, ids,
    (id) => !byId.has(id) || byId.get(id).priceMinor === null || !byId.get(id).name);
  if (unavailable.length) throw withData(conflict('برخی کالاها دیگر در ویترین نیستند', 'product_unavailable'), { unavailable });

  const after = lines.map(pricedOf);
  let nextNo = after.reduce((m, l) => Math.max(m, l.lineNo), 0) + 1;
  const changes = [];
  for (const l of input.lines) {
    const p = byId.get(l.productId);
    const inOrder = after.filter((x) => x.productId === p.id).reduce((s, x) => s + x.qtyMilli, 0);
    if (p.maxQtyMilli !== null && inOrder + l.qtyMilli > p.maxQtyMilli) {
      throw withData(badRequest(`حداکثرِ «${p.name}» در هر سفارش بیشتر نمی‌شود`, 'qty_over_limit'),
        { productId: p.id, maxQtyMilli: p.maxQtyMilli, inOrderQtyMilli: inOrder });
    }
    const same = after.find((x) => x.productId === p.id && x.unitPriceMinor === p.priceMinor);
    if (same) {
      const to = same.qtyMilli + l.qtyMilli;
      changes.push({
        lineNo: same.lineNo, productId: p.id, name: p.name, unit: p.unit, unitPriceMinor: p.priceMinor, merged: true,
        addedQtyMilli: l.qtyMilli, fromQtyMilli: same.qtyMilli, toQtyMilli: to,
        fromLineTotalMinor: same.lineTotalMinor, toLineTotalMinor: S.lineTotalMinor(to, p.priceMinor),
      });
      same.qtyMilli = to;
      same.lineTotalMinor = S.lineTotalMinor(to, p.priceMinor);
    } else {
      const total = S.lineTotalMinor(l.qtyMilli, p.priceMinor);
      changes.push({
        lineNo: nextNo, productId: p.id, name: p.name, unit: p.unit, unitPriceMinor: p.priceMinor, merged: false,
        addedQtyMilli: l.qtyMilli, fromQtyMilli: 0, toQtyMilli: l.qtyMilli, fromLineTotalMinor: 0, toLineTotalMinor: total,
        note: l.note,
      });
      after.push({ lineNo: nextNo, productId: p.id, qtyMilli: l.qtyMilli, unitPriceMinor: p.priceMinor, lineTotalMinor: total });
      nextNo++;
    }
  }
  if (after.length > sf.MAX_CHECKOUT_LINES) throw badRequest(`حداکثر ${sf.MAX_CHECKOUT_LINES} قلم در هر سفارش`, 'too_many_lines');
  //  موجودیِ کاتالوگ (پرچمِ `shop.catalog`): مقدارِ **کلِ** هر کالای دست‌خورده در سفارش، مثلِ ثبت ⛔ بی رزرو
  const touched = [...new Set(changes.map((x) => x.productId))].map((id) => ({
    productId: id, name: byId.get(id).name, unit: byId.get(id).unit,
    qtyMilli: after.filter((x) => x.productId === id).reduce((s, x) => s + x.qtyMilli, 0),
  }));
  await require('./catalog').checkOrder(c, shopId, { lines: touched, isTest: false });
  return { changes, after, before: lines.map(pricedOf) };
}

/** اثرِ قاعده‌های خودکارِ ویترین بر قلم‌های افزوده: سبدِ تازه منهای سبدِ پیشین، همین لحظه (کد هرگز). */
async function autoExtraIn(c, store, before, after, at, promosOn, group = '') {
  if (!promosOn) return 0;
  const promos = require('./storefront-promos');
  const run = (t, p) => c.query(t, p);
  //  PRM-11: همان گروهِ ثبت (شمارهٔ تأییدشدهٔ همان سفارش؛ پرچمِ `shop.storefront.promo_groups` خاموش ⇒ '')
  const a = await promos.evaluate(run, store, after, '', at, { group });
  const b = await promos.evaluate(run, store, before, '', at, { group });
  return Math.max(0, a.discountMinor - b.discountMinor);
}

const summaryOf = (d) => ({
  id: d.addId, lines: d.lines.map(({ lineNo, name, unit, unitPriceMinor, merged, addedQtyMilli, toQtyMilli, toLineTotalMinor }) => ({
    lineNo, name, unit, unitPriceMinor, merged, addedQtyMilli, toQtyMilli, toLineTotalMinor,
  })),
  prevTotalMinor: d.prevTotalMinor, totalMinor: d.totalMinor, differenceMinor: d.differenceMinor,
});

/**
 * افزودنِ قلم به سفارشِ همین ویترین با توکنِ پیگیری.
 * @returns {Promise<{replayed:boolean, added:object, orderId:string}>}
 */
async function add(store, token, { key, body, ticket = '', session = '' }) {
  const sf = require('./storefront');
  const shopId = store.shop_id;
  const input = parse(body);
  const hash = orders.hashOf(body);
  const orderId = await sf.orderIdOfLink(store, token);   // توکنِ نادرست/باطل ⇒ ۴۰۴
  const phone = await sf.verifiedPhone(ticket, session);
  if (!phone) throw new ApiError(401, 'verification_required', 'برای افزودن به سفارش، شمارهٔ خود را تأیید کنید');
  //  پرچم‌ها و منبعِ کاتالوگ پیش از تراکنش (درونِ قفل فقط اتصالِ همان تراکنش)
  const [from, promosOn] = await Promise.all([
    sf.productFrom(shopId, { strict: true }),   //  S1: storefront-catalog از راهِ storefront
    orders.flagOf(shopId, require('./storefront-promos').FLAG),
  ]);
  await require('./catalog').prepare(shopId, { at: now() });
  const group = promosOn ? await require('./storefront-promo-groups').prepare(shopId, phone) : '';

  const out = await tx(async (c) => {
    const { row, lines, events } = await amend.lockOrder(c, shopId, orderId);
    if (row.source !== 'storefront' || row.is_test === true) throw notFound('سفارش پیدا نشد', 'order_not_found');
    if (row.customer_phone !== phone) throw forbidden('این سفارش با شمارهٔ تأییدشدهٔ شما ثبت نشده است', 'not_your_order');
    for (const e of events) {
      const d = e.kind === 'note' ? asJson(e.detail) : {};
      if (d.type === EVENT_TYPE && d.key === key) {
        if (d.hash !== hash) throw conflict('این کلیدِ یکتایی پیش‌تر برای درخواستِ دیگری به کار رفته است', 'idempotency_key_reused');
        return { replayed: true, detail: d, row };
      }
    }
    const blocked = amend.blockerOf(row, false);
    const picked = (await c.query('SELECT 1 FROM order_picks WHERE order_id=$1', [row.id])).rows.length > 0;
    if (blocked === 'amend_not_allowed' || picked) {
      throw withData(conflict('سفارش در حال آماده‌سازی است؛ قلمِ تازه را با سفارشِ تازه بخرید', 'order_preparing'),
        { current: row.status, newOrder: true });
    }
    if (blocked) throw withData(conflict(amend.BLOCK_MESSAGE[blocked], blocked), { current: row.status });
    if (amend.ledgerOf(events, now()).pending) {
      throw conflict('پیشنهادِ دکان برای ویرایشِ این سفارش منتظرِ پاسخِ شماست؛ نخست پاسخ دهید', 'amend_pending');
    }
    const at = now();
    const pl = await planIn(c, shopId, from, input, lines);
    const subtotalMinor = pl.after.reduce((s, l) => s + l.lineTotalMinor, 0);
    const prevDiscountMinor = Number(row.discount_minor);
    const extra = await autoExtraIn(c, store, pl.before, pl.after, at, promosOn, group);
    const discountMinor = Math.min(subtotalMinor, prevDiscountMinor + extra);
    const deliveryFeeMinor = Number(row.delivery_fee_minor);
    const totalMinor = subtotalMinor - discountMinor + deliveryFeeMinor;
    if (!Number.isSafeInteger(totalMinor)) throw badRequest('جمعِ سفارش بیش از حد بزرگ است', 'bad_lines');
    if (input.expectedTotalMinor !== null && input.expectedTotalMinor !== totalMinor) {
      throw withData(conflict('قیمت‌ها عوض شده است؛ دوباره ببینید', 'price_changed'), {
        quote: {
          lines: pl.changes.map((x) => ({ productId: x.productId, name: x.name, unit: x.unit, qtyMilli: x.addedQtyMilli, unitPriceMinor: x.unitPriceMinor })),
          subtotalMinor, discountMinor, deliveryFeeMinor, totalMinor,
        },
      });
    }
    for (const ch of pl.changes) {
      if (ch.merged) {
        await c.query('UPDATE online_order_lines SET qty_milli=$3, line_total_minor=$4 WHERE order_id=$1 AND line_no=$2',
          [row.id, ch.lineNo, ch.toQtyMilli, ch.toLineTotalMinor]);
      } else {
        await c.query(
          `INSERT INTO online_order_lines (id, order_id, shop_id, line_no, product_id, name, unit, qty_milli,
             unit_price_minor, line_total_minor, note)
           VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11)`,
          [ulid(at), row.id, shopId, ch.lineNo, ch.productId, ch.name, ch.unit, ch.toQtyMilli, ch.unitPriceMinor, ch.toLineTotalMinor, ch.note || '']
        );
      }
    }
    await c.query(
      `UPDATE online_orders SET subtotal_minor=$3, discount_minor=$4, total_minor=$5, items_count=$6, version=version+1, updated_at=$7
        WHERE shop_id=$1 AND id=$2`,
      [shopId, row.id, subtotalMinor, discountMinor, totalMinor, pl.after.length, at]
    );
    await require('./delivery-due').syncIn(c, shopId, row.id, at);   //  DLV-11: پیکِ سپرده‌شده جمعِ تازه را می‌گیرد
    const detail = {
      type: EVENT_TYPE, addId: `ad_${ulid(at)}`, key, hash,
      lines: pl.changes.map(({ note: _n, ...x }) => x),
      prevSubtotalMinor: Number(row.subtotal_minor), subtotalMinor, prevDiscountMinor, discountMinor,
      prevTotalMinor: Number(row.total_minor), totalMinor, differenceMinor: totalMinor - Number(row.total_minor),
    };
    await amend.event(c, shopId, row, CUSTOMER, detail, {}, at);
    return { replayed: false, detail, row };
  });
  if (!out.replayed) await afterAdd(shopId, out.row, out.detail);
  return { replayed: out.replayed, added: summaryOf(out.detail), orderId };
}

/** سابقه و پوشِ «قلمِ تازه» به اعضای دکان — پس از تراکنش؛ خطایش افزودن را پس نمی‌گیرد. */
async function afterAdd(shopId, row, d) {
  const no = Number(row.order_no);
  await require('./audit').log({
    shopId, actorType: 'customer', action: 'order.items_added', targetType: 'online_order', targetId: row.id,
    detail: { orderNo: no, lines: d.lines.length, prevTotalMinor: d.prevTotalMinor, totalMinor: d.totalMinor },
  }).catch(() => {});
  try {
    const on = require('./order-notify');
    if (!(await on.flagOf(shopId))) return;
    await require('./push').sendTo({ shopId, app: 'shop', onlyUserIds: await on.recipientIds(shopId) }, {
      title: `قلمِ تازه — سفارشِ شمارهٔ ${faNum(no)}`,
      body: 'مشتری پیش از جمع‌آوری قلم افزود؛ سفارش را دوباره ببینید.',
      channel: on.CHANNEL,
      data: { type: 'order_items_added', orderId: row.id, orderNo: String(no), shopId },
    });
  } catch (err) {
    console.error('[order-add-items] اعلان', err && err.message);
  }
}

/**
 * پیگیریِ مشتری با کلیدِ `addItems` — فقط با پرچم؛ خاموش ⇒ همان شیءِ پیشین. `allowed` فقط راهنمای
 * دکمه است؛ تصمیمِ آخر درونِ تراکنشِ `add` است. `preparing` ⇒ پیامِ «در حال آماده‌سازی» (S38).
 */
async function withAddItems(shopId, o, view) {
  if (!(await flagOf(shopId))) return view;
  const pending = Boolean(view.amendment && view.amendment.status === 'pending');
  const allowed = amend.AMEND_STATUSES.includes(o.status) && o.paymentStatus === 'unpaid' && !pending;
  return { ...view, addItems: { allowed, preparing: PREPARING.includes(o.status) } };
}

module.exports = { FLAG_KEY, EVENT_TYPE, MAX_ADD_LINES, flagOf, parse, add, withAddItems };
