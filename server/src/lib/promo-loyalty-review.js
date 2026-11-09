'use strict';
/**
 * STF-03 — تخفیفِ خودکارِ قلم (`promo`) و مصرفِ امتیاز (`loyalty_spend`) ِ فاکتورِ کارمند در برابرِ **تعریف‌های خودِ دکان**:
 * «بررسی، نه رد» (پرچمِ خاموشِ `shop.promo_loyalty_review`؛ الگوی `lib/credit-limit-review.js`).
 *
 * تا امروز سرور اعلامِ صندوق را می‌پذیرفت: `price-cap` (`sale-caps.line`) تخفیفِ خودکارِ هر قلم و سهمِ امتیاز را پیش از
 * سنجشِ قیمت «برمی‌گرداند»، پس کارمند با `promo` ِ ساختگی یا امتیازی که مشتری ندارد قیمت را پایین می‌آورد و هیچ سقفی
 * نمی‌دید. این پرونده **هیچ ردی نمی‌کند، هیچ ردیفی از دفتر نمی‌نویسد و هیچ پاسخی را عوض نمی‌کند**؛ پس از نشستنِ push
 * (v1: `credit-debtor.flagV1`، ⛔ بی دست زدن به `sync-v1`؛ راهِ قدیم و REST: `credit-debtor.flagLegacy`؛ v3: `routes/sync-v3.js`)
 * برای هر فاکتورِ تازه (یا فاکتورِ نشسته‌ای که قلم یا مصرفِ امتیازش دیر رسید) با همان قاعدهٔ صندوق می‌سنجد:
 *
 *   تخفیف  همان `promo-core.evaluate` (= `PromoCore.kt`، بردارِ `promo-v1.json`) با ورودیِ `PromoEngine.lines` ِ صندوق:
 *          کانالِ `pos`، پلهٔ ۱۰۰، کدهای فاکتور (`coupons`)، گروهِ مشتری (`groupOf`، همان `compareKey`)، ساعتِ محلیِ دکان،
 *          قاعده‌های زندهٔ دکان (`shop_promotions` + کلیدِ `shop_promo_settings.enabled`، مصرفِ دفتر بی خودِ همین فاکتور) و
 *          هر قلم = {قیمتِ فهرست (`listPriceMinor`، وگرنه کالای سرور)، مقدار، مبلغِ پیش از تخفیف = خالصِ قلم + اعلامِ همان قلم،
 *          بها/دسته/تأمین‌کننده/برندِ کالای سرور}. جمعِ اعلام‌شده > جمعِ مجاز ⇒ `promo_over`؛ شناسهٔ قاعده‌ای که دکان هرگز
 *          نداشت ⇒ `promo_unknown_rule`؛ کدِ سقف‌دار (PRM-02، با `shop.promo_uses`) بی مصرفِ گرفته‌شده برای همین فاکتور ⇒
 *          `promo_unredeemed`. «نزدیکِ انقضا» محموله می‌خواهد که روی سرور نیست ⇒ بیشترینِ دو حالت (بی محموله /
 *          همهٔ واحدها نزدیکِ انقضا) — سقفِ بالا، نه برآوردِ پایین.
 *   امتیاز همان `Loyalty.spend` (`loyalty-balance.spend`، بردارِ `loyalty-spend-v1.json`) با قاعدهٔ یکسانِ دکان (`loyalty-rule`) و
 *          ماندهٔ دفترِ سرور تا `createdAt` ِ فاکتور (بی ردیف‌های خودش، با انقضای FIFO؛ مشتری با ادغام‌ها). فاکتورِ پیش از
 *          امتیاز = جمعِ فاکتور + ارزشِ اعلام‌شده (`LoyaltyPoints.post`). ⇒ `loyalty_over_balance`، `loyalty_over_share`،
 *          `loyalty_bad_points`، `loyalty_value` (ارزشِ امتیاز با قاعده نمی‌خواند) یا `loyalty_customer` (مشتریِ دیگر/بی مشتری).
 *          بی قاعدهٔ سرور فقط مانده (بی انقضا = سقفِ بالا) سنجیده می‌شود (`ruleOnServer: false`).
 * ⇒ یک سابقهٔ `sale.promo_review` برای هر فاکتور (`audit.log` با `once`) در «سابقهٔ عملیاتِ دکان» (`audit.view`)، با همهٔ
 *    مشکل‌ها؛ قاعده‌ای که پس از فاکتور عوض شد (`ruleChanged`) یا نسخهٔ کهنه‌اش روی فاکتور است (`staleRev`) نشان می‌خورد —
 *    بررسی است، نه داوری. بی نام و شمارهٔ مشتری.
 * صاحبِ دکان سنجیده نمی‌شود. پرچمِ خاموش (پیش‌فرض) یا دستهٔ بی فاکتور ⇒ هیچ پرسشی جز خودِ پرچم. شکستِ بررسی هرگز push را
 * نمی‌خواباند. ⛔ فقط ردیف‌های همین دکان (`account_id`/`shop_id` ِ عضویت).
 */
const { many } = require('../db');
const audit = require('./audit');
const P = require('./promo-core');
const caps = require('./sale-caps');
const loyalty = require('./loyalty-balance');

const FLAG = 'shop.promo_loyalty_review';
const ACTION = 'sale.promo_review';
const MAX_SALES = 50;
const MAX_EVENTS = 20;
const MAX_ROWS = 5000;
const STEP_MINOR = 100;
const SPEND = caps.LOYALTY_SPEND;
const LOYALTY_TYPES = Object.keys(loyalty.TYPES);

const isObj = (v) => v !== null && typeof v === 'object' && !Array.isArray(v);
const str = (v) => (typeof v === 'string' ? v : '');
const nonBlank = (v) => typeof v === 'string' && v.trim() !== '';
const num = (v) => (typeof v === 'number' ? v : typeof v === 'string' && v.trim() !== '' ? Number(v) : NaN);
/** عددِ صحیحِ امن یا null */
const safeInt = (v) => { const n = Math.round(num(v)); return Number.isSafeInteger(n) ? n : null; };
const time = (v) => { const n = num(v); return Number.isFinite(n) ? n : null; };

/* ---------------------------------------------------------------- شکل‌ها */

/** v1/راهِ قدیم: افغانی و مقدارِ اعشاری؛ v3: `<نام>Minor`/`<نام>Milli` */
function moneyOf(d, key, shape) {
  if (!isObj(d)) return null;
  if (shape === 'v3' && d[`${key}Minor`] !== undefined && d[`${key}Minor`] !== null) return safeInt(d[`${key}Minor`]);
  return d[key] === undefined || d[key] === null ? null : safeInt(num(d[key]) * 100);
}
function qtyOf(d, shape) {
  if (shape === 'v3' && d.quantityMilli !== undefined && d.quantityMilli !== null) return safeInt(d.quantityMilli);
  return d.quantity === undefined || d.quantity === null ? null : safeInt(num(d.quantity) * 1000);
}

/** اعلامِ تخفیفِ خودکارِ یک قلم: [{id, rev, minor}] (فقط مثبت) */
function claimsOf(item) {
  const out = [];
  for (const p of Array.isArray(item && item.promo) ? item.promo : []) {
    if (!isObj(p)) continue;
    const m = safeInt(p.discountMinor);
    if (m === null || m <= 0) continue;
    out.push({ id: str(p.id).slice(0, 64), rev: safeInt(p.rev), minor: m });
  }
  return out;
}

/* ---------------------------------------------------------------- تخفیف */

/** PRM-11 — همان `PromoEngine.groupOf`: نامِ گروهِ قاعده با همان `compareKey`، وگرنه همان گروهِ مشتری */
function groupOf(debtor, rules) {
  const raw = isObj(debtor) ? str(debtor.group).trim() : '';
  if (!raw) return '';
  const { compareKey } = require('./text-core');
  const key = compareKey(raw);
  for (const r of rules) for (const g of Array.isArray(r.groups) ? r.groups : []) if (typeof g === 'string' && compareKey(g) === key) return g;
  return raw;
}

/** قیمتِ فهرستِ کالای سرور در روزِ فاکتور (بازهٔ فعال، وگرنه قیمتِ فروش) — همان پایهٔ `ProductPlansMap.price` */
function listOf(product, date, shape) {
  const ref = caps.refOf(product, { legacy: shape !== 'v3' });
  if (!ref) return null;
  const w = ref.windows.find((x) => typeof date === 'string' && date !== '' && date >= x.from && date <= x.to);
  return w ? w.priceMinor : ref.saleMinor;
}

/**
 * ورودیِ هسته برای همان سبدِ صندوق (`PromoEngine.lines`). قلمِ آزاد (بی کالا) همان‌جا هم تخفیف نمی‌گیرد.
 * @returns {{lines, claimed, ids: Set}}
 */
function cartOf(items, products, date, shape) {
  const lines = [];
  let claimed = 0;
  const ids = new Set();
  for (const { id, data: it } of items) {
    if (!isObj(it)) continue;
    const qty = qtyOf(it, shape);
    const net = moneyOf(it, 'totalPrice', shape);
    const mine = claimsOf(it);
    const c = mine.reduce((a, x) => a + x.minor, 0);
    claimed += c;
    for (const x of mine) if (x.id) ids.add(x.id);
    if (qty === null || net === null) continue;
    const productId = str(it.productId);
    const p = productId ? products.get(productId) : null;
    const listed = safeInt(it.listPriceMinor);
    const unit = listed !== null && listed >= 0 ? listed : (p ? listOf(p, date, shape) : moneyOf(it, 'unitPrice', shape));
    const cost = p ? moneyOf(p, 'purchasePrice', shape) : null;
    lines.push({
      key: String(id), productId: p ? productId : '', qtyMilli: qty, unitMinor: unit === null ? 0 : unit, amountMinor: net + c,
      costMinor: cost !== null && cost > 0 ? cost : 0,
      category: p ? str(p.category) : '', supplierId: p ? str(p.supplierId) : '', brand: p ? str(p.brand).trim() : '',
    });
  }
  return { lines, claimed, ids };
}

/** جمعِ تخفیفی که هسته با قاعده‌های دکان می‌دهد؛ «نزدیکِ انقضا» ⇒ بیشترینِ بی‌محموله و همه‌نزدیک. عددِ ناامن ⇒ ۰ (همان صندوق) */
function allowedOf(cart, rules, ctxCart) {
  const run = (lines) => {
    try { return P.evaluate({ ...ctxCart, stepMinor: STEP_MINOR, channel: 'pos', lines }, rules).discountMinor; } catch { return 0; }
  };
  let best = run(cart.lines);
  if (rules.some((r) => r && r.kind === 'expiry' && r.active !== false)) {
    best = Math.max(best, run(cart.lines.map((l) => (l.productId ? { ...l, lots: [{ daysLeft: 0, qtyMilli: l.qtyMilli }] } : l))));
  }
  return best;
}

/**
 * قاعده‌ها و کلیدِ تخفیفِ دکان برای یک دسته؛ `liveFor(فاکتور)` = قاعده‌های زنده با مصرفِ دفتر **بی خودِ همان فاکتور**
 * (همان `storefront-promos.rulesOf`: کدِ سقف‌داری که صندوق پیش از ثبت گرفت ظرفیتِ خودش را نمی‌خورد)
 */
async function promoState(shopId, saleIds) {
  const [settings, rows, uses, own] = await Promise.all([
    many('SELECT enabled, updated_at FROM shop_promo_settings WHERE shop_id=$1', [shopId]),
    many('SELECT id, data, rev, updated_at, deleted FROM shop_promotions WHERE shop_id=$1 ORDER BY id', [shopId]),
    many(`SELECT promo_id, count(*)::int AS n FROM shop_promo_redemptions WHERE shop_id=$1 AND released_at IS NULL GROUP BY promo_id`, [shopId]),
    many(`SELECT promo_id, sale_id FROM shop_promo_redemptions WHERE shop_id=$1 AND released_at IS NULL AND sale_id = ANY($2::text[])`,
      [shopId, saleIds]),
  ]);
  const used = new Map(uses.map((r) => [r.promo_id, Number(r.n)]));
  const held = new Set(own.map((r) => `${r.sale_id}\u0000${r.promo_id}`));
  const asJson = (v) => (isObj(v) ? v : (() => { try { return JSON.parse(v) || {}; } catch { return {}; } })());
  const live = rows.filter((r) => r.deleted !== true).map((r) => ({ ...asJson(r.data), id: r.id }));
  return {
    enabled: !!(settings[0] && settings[0].enabled), settingsAt: settings[0] ? Number(settings[0].updated_at) : 0,
    all: rows.map((r) => ({ id: r.id, rev: Number(r.rev), at: Number(r.updated_at), deleted: r.deleted === true })),
    liveFor: (saleId) => live.map((r) => ({ ...r, uses: Math.max(0, (used.get(r.id) || 0) - (held.has(`${saleId}\u0000${r.id}`) ? 1 : 0)) })),
    /** PRM-02: کدِ سقف‌داری که روی فاکتور است ولی ظرفیتش برای همین فاکتور گرفته نشد */
    unredeemed: (saleId, ids) => live.filter((r) => ids.has(r.id) && str(r.code) !== '' && Number(r.maxUses) > 0
      && !held.has(`${saleId}\u0000${r.id}`)).map((r) => r.id),
  };
}

/** ساعتِ محلیِ دکان (ویترین اگر هست، وگرنه پیش‌فرضِ سرور) — همان «امروز و دقیقه» ِ صندوق */
async function tzOf(shopId) {
  const r = await many('SELECT timezone FROM storefronts WHERE shop_id=$1 LIMIT 1', [shopId]);
  return (r[0] && r[0].timezone) || require('../config').defaults.timezone || 'Asia/Kabul';
}

/* ---------------------------------------------------------------- امتیاز */

/** مشتریِ ماندگار (CRM-10، `mergedInto` تا سه لایه) — همان `CustomerCrm.resolve` */
function rootOf(debtors, id) {
  let cur = id;
  for (let i = 0; i < 4; i++) {
    const d = debtors.get(cur);
    const next = isObj(d) ? str(d.mergedInto) : '';
    if (!next || next === cur) break;
    cur = next;
  }
  return cur;
}

/**
 * داوریِ خالصِ مصرفِ امتیاز. @param spends دادهٔ ردیف‌های `loyalty_spend` ِ فاکتور؛ @param events رویدادهای خانوادهٔ مشتری
 * ⇒ null (سالم) یا {problems, detail}
 */
function judgeLoyalty({ saleId, sale, spends, events, rule, root, family, shape }) {
  const points = spends.reduce((a, s) => a + (safeInt(s.points) || 0), 0);
  const value = spends.reduce((a, s) => a + (moneyOf(s, 'amount', shape) || 0), 0);
  const problems = [];
  const payRefs = [...new Set(spends.map((s) => str(s.payRef)))];
  if (!nonBlank(sale.debtorId) || payRefs.some((p) => p !== root && p !== sale.debtorId)) problems.push('loyalty_customer');
  const at = time(sale.createdAt);
  const mine = events.filter((e) => e.saleId !== saleId && family.has(e.customer) && (at === null || e.at <= at));
  const st = loyalty.state(mine, rule || { expiryMonths: 0 }, at === null ? Number.MAX_SAFE_INTEGER : at);
  const total = moneyOf(sale, 'total', shape);
  const base = total === null ? null : total + value;
  const detail = { points, valueMinor: value, balance: st.balance, ruleOnServer: !!rule, invoiceMinor: base };
  if (rule) {
    const s = base === null ? { ok: false, code: 'BAD_POINTS' } : loyalty.spend(rule, st.balance, base, points);
    detail.capPoints = base === null ? 0 : loyalty.cap(rule, st.balance, base);
    if (!s.ok) {
      problems.push({ OVER_BALANCE: 'loyalty_over_balance', OVER_SHARE: 'loyalty_over_share', BAD_RULE: 'loyalty_bad_rule' }[s.code] || 'loyalty_bad_points');
    } else if (s.valueMinor !== value) {
      detail.expectedValueMinor = s.valueMinor;
      problems.push('loyalty_value');
    }
  } else if (!(points > 0)) problems.push('loyalty_bad_points');
  else if (points > st.balance) problems.push('loyalty_over_balance');
  return problems.length ? { problems, detail } : null;
}

/* ---------------------------------------------------------------- خواندن */

/** انبارِ v1 (`sync_rows`) یا v3 (`sync_v3_rows`) ِ همین حساب */
function syncStore(table, ctx) {
  const acc = [ctx.app, ctx.accountKind, ctx.accountId];
  const rows = (name, where, params) => many(
    `SELECT row_id AS id, data FROM ${table} WHERE app=$1 AND account_kind=$2 AND account_id=$3 AND table_name='${name}'
        AND deleted_at IS NULL AND ${where} LIMIT ${MAX_ROWS}`, [...acc, ...params]);
  return {
    heads: (ids) => rows('sales', 'row_id = ANY($4::text[])', [ids]),
    items: (ids) => rows('saleItems', `data->>'saleId' = ANY($4::text[])`, [ids]),
    spends: (ids) => rows('auditLog', `data->>'type' = $4 AND data->>'refId' = ANY($5::text[])`, [SPEND, ids]),
    products: (ids) => rows('products', 'row_id = ANY($4::text[])', [ids]),
    debtors: (ids) => rows('debtors', 'row_id = ANY($4::text[])', [ids]),
    mergedInto: (ids) => rows('debtors', `data->>'mergedInto' = ANY($4::text[])`, [ids]),
    events: (refs) => rows('auditLog', `data->>'payRef' = ANY($4::text[]) AND data->>'type' = ANY($5::text[])`, [refs, LOYALTY_TYPES]),
  };
}

/** انبارِ راهِ قدیم (جدول‌های جدا) */
function legacyStore(shopId) {
  const rows = (table, where, params) => many(
    `SELECT id, data FROM ${table} WHERE shop_id=$1 AND deleted=false AND ${where} LIMIT ${MAX_ROWS}`, [shopId, ...params]);
  return {
    heads: (ids) => rows('sales', 'id = ANY($2::text[])', [ids]),
    items: (ids) => rows('sale_items', `data->>'saleId' = ANY($2::text[])`, [ids]),
    spends: (ids) => rows('shop_audit_log', `data->>'type' = $2 AND data->>'refId' = ANY($3::text[])`, [SPEND, ids]),
    products: (ids) => rows('products', 'id = ANY($2::text[])', [ids]),
    debtors: (ids) => rows('debtors', 'id = ANY($2::text[])', [ids]),
    mergedInto: (ids) => rows('debtors', `data->>'mergedInto' = ANY($2::text[])`, [ids]),
    events: (refs) => rows('shop_audit_log', `data->>'payRef' = ANY($2::text[]) AND data->>'type' = ANY($3::text[])`, [refs, LOYALTY_TYPES]),
  };
}

/** خانوادهٔ مشتری (ریشه + ادغام‌شده‌ها، تا سه لایه) */
async function familyOf(store, root) {
  const all = new Set([root]);
  let frontier = [root];
  for (let i = 0; i < 3 && frontier.length; i++) {
    frontier = (await store.mergedInto(frontier)).map((r) => r.id).filter((id) => !all.has(id));
    for (const id of frontier) all.add(id);
  }
  return all;
}

/* ---------------------------------------------------------------- اجرا */

/**
 * @param who {shopId, role, userId, deviceId, ip, path}
 * @param saleIds فاکتورهای تازه یا فاکتورهایی که قلم/مصرفِ امتیازِ تازه گرفتند (همین دسته)
 * @returns شمارِ سابقه‌های تازه
 */
async function review(who, saleIds, shape, store) {
  if (!who || !who.shopId || who.role === 'owner') return 0;
  const ids = [...new Set(saleIds.filter((x) => typeof x === 'string' && x))].slice(0, MAX_SALES);
  if (!ids.length || !(await require('./orders').flagOf(who.shopId, FLAG))) return 0;
  const [heads, items, spends] = await Promise.all([store.heads(ids), store.items(ids), store.spends(ids)]);
  const live = heads.filter((h) => isObj(h.data) && h.data.status !== 'cancelled');
  if (!live.length) return 0;
  const itemsOf = new Map();
  for (const it of items) {
    if (!isObj(it.data)) continue;
    const k = str(it.data.saleId);
    if (!itemsOf.has(k)) itemsOf.set(k, []);
    itemsOf.get(k).push(it);
  }
  const spendsOf = new Map();
  for (const s of spends) {
    if (!isObj(s.data)) continue;
    const k = str(s.data.refId);
    if (!spendsOf.has(k)) spendsOf.set(k, []);
    spendsOf.get(k).push(s.data);
  }
  const claimedAny = live.some((h) => (itemsOf.get(h.id) || []).some((it) => claimsOf(it.data).length));
  const spentAny = live.some((h) => spendsOf.has(h.id));
  if (!claimedAny && !spentAny) return 0;

  const productIds = [...new Set(items.map((it) => (isObj(it.data) ? str(it.data.productId) : '')).filter(Boolean))];
  const debtorIds = [...new Set(live.map((h) => str(h.data.debtorId)).filter(Boolean))];
  const [products, debtorRows, tz, rule, promos] = await Promise.all([
    claimedAny && productIds.length ? store.products(productIds) : [],
    debtorIds.length ? store.debtors(debtorIds) : [],
    claimedAny ? tzOf(who.shopId) : null,
    spentAny ? require('./loyalty-rule').activeRule(who.shopId) : null,
    claimedAny ? promoState(who.shopId, live.map((h) => h.id)) : null,
  ]);
  //  PRM-02: سقفِ کد فقط با دفترِ مصرفِ روشن شمرده می‌شود (`shop.promo_uses`)
  const usesOn = claimedAny && await require('./orders').flagOf(who.shopId, 'shop.promo_uses');
  const families = new Map();
  const productMap = new Map(products.map((r) => [r.id, r.data]));
  const debtors = new Map(debtorRows.map((r) => [r.id, r.data]));
  const { localParts } = require('./storefront-hours');

  let n = 0;
  for (const h of live) {
    if (n >= MAX_EVENTS) break;
    const s = h.data;
    const problems = [];
    const detail = {};
    const mine = itemsOf.get(h.id) || [];
    const cart = cartOf(mine, productMap, '', shape);
    if (cart.claimed > 0) {
      const at = time(s.createdAt);
      const local = at === null ? null : localParts(at, tz);
      const date = local ? local.date : str(s.date);
      const priced = cartOf(mine, productMap, date, shape);
      const st = promos;
      const rules = st.enabled ? st.liveFor(h.id) : [];
      const allowed = allowedOf(priced, rules, {
        coupons: (Array.isArray(s.coupons) ? s.coupons : []).filter((c) => typeof c === 'string').slice(0, 10),
        group: groupOf(debtors.get(str(s.debtorId)), rules),
        at: { date, minute: local ? local.minutes : -1 },
      });
      const known = new Map(st.all.map((r) => [r.id, r]));
      const unknown = [...priced.ids].filter((id) => !known.has(id));
      const revs = mine.flatMap((it) => claimsOf(it.data)).filter((x) => x.id && known.has(x.id));
      const changed = (at !== null && (st.settingsAt > at || [...priced.ids].some((id) => known.has(id) && known.get(id).at > at)));
      if (priced.claimed > allowed) problems.push('promo_over');
      if (unknown.length) problems.push('promo_unknown_rule');
      const unredeemed = usesOn && st.enabled ? st.unredeemed(h.id, priced.ids) : [];
      if (unredeemed.length) problems.push('promo_unredeemed');
      if (problems.length) {
        detail.promo = {
          claimedMinor: priced.claimed, allowedMinor: allowed, excessMinor: Math.max(0, priced.claimed - allowed),
          //  ⛔ `audit.scrub` آرایه را شیء می‌کند ⇒ فهرست‌ها رشتهٔ «،» ‌دار
          rules: [...priced.ids].slice(0, 20).join(','), ...(unknown.length ? { unknown: unknown.slice(0, 20).join(',') } : {}),
          ...(unredeemed.length ? { unredeemed: unredeemed.slice(0, 20).join(',') } : {}),
          ...(st.enabled ? {} : { off: true }), ...(changed ? { ruleChanged: true } : {}),
          ...(revs.some((x) => x.rev !== null && x.rev < known.get(x.id).rev) ? { staleRev: true } : {}),
        };
      }
    }
    const sp = spendsOf.get(h.id);
    if (sp && sp.length) {
      const root = rootOf(debtors, str(s.debtorId));
      //  یک بار برای هر مشتری در همین دسته
      if (!families.has(root)) {
        const fam = nonBlank(root) ? await familyOf(store, root) : new Set();
        families.set(root, { family: fam, rows: fam.size ? await store.events([...fam]) : [] });
      }
      const { family, rows: evRows } = families.get(root);
      const events = evRows.filter((r) => isObj(r.data) && loyalty.TYPES[r.data.type]).map((r) => ({
        id: r.id, kind: loyalty.TYPES[r.data.type], points: safeInt(r.data.points) || 0,
        saleId: str(r.data.refId), at: time(r.data.createdAt) ?? 0, customer: str(r.data.payRef),
      }));
      const j = judgeLoyalty({ saleId: h.id, sale: s, spends: sp, events, rule, root, family, shape });
      if (j) { problems.push(...j.problems); detail.loyalty = j.detail; }
    }
    if (!problems.length) continue;
    const r = await audit.log({
      shopId: who.shopId, actorType: 'user', userId: who.userId || '', ip: who.ip || '', once: true,
      action: ACTION, targetType: 'sale', targetId: h.id.slice(0, 128),
      detail: {
        problem: 'promo_loyalty_review', review: true, problems: problems.join(','), path: who.path, deviceId: str(who.deviceId).slice(0, 64),
        role: str(who.role).slice(0, 24), invoice: s.invoiceNumber ?? null, ...detail,
        text: 'تخفیفِ خودکار یا مصرفِ امتیازِ این فاکتور با تعریف‌های دکان جور نیست — فروش ثبت شد؛ بررسی کنید',
      },
    });
    if (r) n++;
  }
  return n;
}

/** شکستِ بررسی هرگز push را نمی‌خواباند */
async function safely(label, fn) {
  try { return await fn(); } catch (err) {
    console.error(`[promo-loyalty-review] بررسیِ ${label} نشد:`, err.message);
    return 0;
  }
}

/** شناسهٔ فاکتورهای یک op: درجِ فاکتور، قلمِ تازه (`saleId`) یا ردیفِ `loyalty_spend` (`refId`) */
function saleIdOf(table, kind, rowId, fields) {
  if (kind !== 'insert') return '';
  if (table === 'sales') return String(rowId || '');
  if (!isObj(fields)) return '';
  if (table === 'saleItems') return str(fields.saleId);
  if (table === 'auditLog' && fields.type === SPEND) return str(fields.refId);
  return '';
}

/** v1 (از `credit-debtor.flagV1` پس از push): op ها همان دسته؛ داوری فقط از دفترِ نشسته */
function v1(ctx, deviceId, ops, { ip = '' } = {}) {
  return safely('v1', () => {
    const { tableOf } = require('./sync-v1');
    const ids = (Array.isArray(ops) ? ops : []).filter(isObj).map((op) => saleIdOf(
      tableOf('shop', op.table ?? op.table_name ?? op.tableName), op.type ?? op.op_type ?? op.opType, op.row_id ?? op.rowId ?? op.id, op.fields));
    return review({ shopId: ctx.accountId, role: ctx.role, userId: ctx.userId, deviceId, ip, path: 'v1' }, ids, 'v1',
      syncStore('sync_rows', { app: 'shop', accountKind: 'shop', accountId: ctx.accountId }));
  });
}

/** v3 (`routes/sync-v3.js` پس از push): فقط opهای نشسته؛ `ops`/`results` هم‌ترتیب (پیش از `mergeResults`) */
function v3(ctx, deviceId, ops, results, { ip = '' } = {}) {
  return safely('v3', async () => {
    const { SHOP } = require('./tenancy');
    if (!ctx || ctx.app !== SHOP.app || ctx.accountKind !== SHOP.app || !ctx.accountId || ctx.role === 'owner') return 0;
    if (!Array.isArray(ops) || !Array.isArray(results)) return 0;
    const ids = [];
    ops.forEach((op, i) => {
      const r = results[i];
      if (!isObj(op) || !r || (r.status !== 'applied' && r.status !== 'partial')) return;
      ids.push(saleIdOf(op.table, op.kind ?? op.type, op.row_id, op.fields));
    });
    return review({ shopId: ctx.accountId, role: ctx.role, userId: ctx.userId, deviceId, ip, path: 'v3' }, ids, 'v3',
      syncStore('sync_v3_rows', ctx));
  });
}

/** راهِ قدیم و REST (از `credit-debtor.flagLegacy`): فاکتورِ تازه (`version = 1`)، قلم یا مصرفِ امتیازِ تازه */
function legacy({ shopId, role, userId = '', deviceId = '', ip = '', path = 'legacy' }, changes, refused = new Set()) {
  return safely('legacy', async () => {
    if (!shopId || role === 'owner' || !Array.isArray(changes)) return 0;
    const live = changes.filter((ch) => isObj(ch) && ch.deleted !== true && isObj(ch.data) && !refused.has(`${ch.collection}/${ch.id}`));
    const heads = live.filter((ch) => ch.collection === 'sales').map((ch) => String(ch.id));
    const fresh = heads.length ? new Set((await many(`SELECT id FROM sales WHERE shop_id=$1 AND version=1 AND id = ANY($2::text[])`,
      [shopId, heads])).map((r) => r.id)) : new Set();
    const ids = [
      ...heads.filter((id) => fresh.has(id)),
      ...live.map((ch) => (ch.collection === 'sales' ? '' : saleIdOf(ch.collection, 'insert', ch.id, ch.data))),
    ];
    return review({ shopId, role, userId, deviceId, ip, path }, ids, 'v1', legacyStore(shopId));
  });
}

module.exports = { FLAG, ACTION, claimsOf, groupOf, cartOf, allowedOf, judgeLoyalty, rootOf, saleIdOf, v1, v3, legacy };
