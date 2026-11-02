'use strict';
/**
 * تخفیفِ ویترین — MKT-01 (کدِ تخفیف و تخفیفِ خودکار) و MKT-09 (تخفیفِ زمان‌دار با «پایان در …»)،
 * پشتِ پرچمِ خاموشِ `shop.storefront.promos`.
 *
 *   قاعده ──── همان قاعده‌های تخفیفِ دکان (`shop_promotions`، ویرایشگرِ برنامهٔ مدیریت، PRM-11) با کانالِ
 *              «اینترنتی» (`channel: 'online'`)؛ حساب فقط `promo-core.evaluate` (همان هستهٔ صندوق و Kotlin)، با
 *              ساعت و روزِ محلیِ خودِ دکان (`localParts`) برای بازهٔ زمانی.
 *   ثبت ────── درونِ همان تراکنشِ `orders.create` (پیچیده دورِ `pricer` ِ ویترین): تخفیف در `discountMinor` ِ
 *              سفارش و جمعِ تازه؛ کدِ واردشده‌ای که اعمال نشود ⇒ ۴۰۹ `promo_code_rejected` (با `promo.status`)، نه
 *              سفارشِ بی‌صدا با قیمتِ دیگر. «جمعِ دیده‌شده» (CHK-17) پس از تخفیف سنجیده می‌شود.
 *   سقف ────── کدِ سقف‌دار زیرِ قفلِ ردیفِ همان قاعده (`FOR UPDATE`) شمرده و در همان تراکنش در دفترِ مصرف
 *              (`shop_promo_redemptions`) با `sale_id = sf:<کلیدِ یکتایی>` نوشته می‌شود — دو سفارشِ هم‌زمان آخرین
 *              ظرفیت را دو بار نمی‌گیرند و تکرارِ همان درخواست مصرفِ دوم نمی‌سازد.
 *   عمومی ──── `GET /api/store/:slug/promos`: فقط تخفیف‌های خودکارِ زنده (نام، نوع، `endsAt`) و «کد پذیرفته
 *              می‌شود؟» — ⛔ هیچ کدی فهرست نمی‌شود. `POST /api/store/:slug/promos/quote`: پیش‌نمایشِ همان حساب.
 *
 * ⛔ پرچمِ خاموش ⇒ ثبتِ ویترین همان پیشین (کلیدِ `promoCode` نادیده) و درهای عمومی ۴۰۴.
 * جدا از `storefront.js`، تا آن فایل از سقفِ اندازه‌اش بزرگ‌تر نشود.
 */
const db = require('../db');
const P = require('./promo-core');
const orders = require('./orders');
const S = require('./order-states');
const { localParts } = require('./storefront-hours');
const { badRequest, conflict, notFound } = require('../middleware/errors');

const FLAG = 'shop.storefront.promos';
const STEP_MINOR = 100;          // پلهٔ افغانی، همان صندوق
const MAX_LINES = 100;
const MAX_QTY_MILLI = 1_000_000_000;
const PRODUCT_RE = /^[A-Za-z0-9_.:-]{1,80}$/;

const asJson = (v) => {
  if (v && typeof v === 'object') return v;
  try { return JSON.parse(v) || {}; } catch { return {}; }
};
const str = (v) => (typeof v === 'string' ? v.trim() : '');

/** کدِ مشتری (حداکثر یکی) — همان نرمال‌سازیِ صندوق؛ نبود ⇒ '' */
const codeOf = (body) => P.normalizeCode(str(body && body.promoCode).slice(0, 64));

const flagOn = (shopId) => orders.flagOf(shopId, FLAG);

/** ساعتِ محلیِ دکان برای بازهٔ قاعده‌ها */
function atOf(sf, at) {
  const l = localParts(at, sf.timezone || 'Asia/Kabul');
  return { date: l.date, minute: l.minutes };
}

/** قاعده‌های زندهٔ دکان با مصرفِ دفتر؛ `exceptSale` ⇒ مصرفِ خودِ همان سفارش شمرده نمی‌شود (تکرارِ درخواست) */
async function rulesOf(run, shopId, { exceptSale = '' } = {}) {
  const rows = (await run('SELECT id, data FROM shop_promotions WHERE shop_id=$1 AND deleted=false ORDER BY id', [shopId])).rows;
  const uses = new Map((await run(
    `SELECT promo_id, count(*)::int AS n FROM shop_promo_redemptions
      WHERE shop_id=$1 AND released_at IS NULL AND sale_id <> $2 GROUP BY promo_id`, [shopId, exceptSale]
  )).rows.map((r) => [r.promo_id, Number(r.n)]));
  return rows.map((r) => ({ ...asJson(r.data), id: r.id, uses: uses.get(r.id) || 0 }));
}

/** دسته، برند، تأمین‌کننده و بهای خرید از دفترِ کالا (کفِ «زیرِ بهای خرید نه» ِ هسته) */
async function factsOf(run, shopId, ids) {
  if (!ids.length) return new Map();
  const { PRODUCT_FROM } = require('./storefront');
  const rows = (await run(
    `SELECT r.row_id AS id, r.data FROM ${PRODUCT_FROM} WHERE sp.shop_id=$1 AND sp.product_id = ANY($2::text[])`, [shopId, ids]
  )).rows;
  return new Map(rows.map((r) => {
    const d = asJson(r.data);
    const cost = Number.isSafeInteger(d.purchasePriceMinor) ? d.purchasePriceMinor
      : (typeof d.purchasePrice === 'number' && d.purchasePrice > 0 ? Math.round(d.purchasePrice * 100) : 0);
    return [r.id, { category: str(d.category), brand: str(d.brand), supplierId: str(d.supplierId), costMinor: cost > 0 ? cost : 0 }];
  }));
}

/**
 * قلم‌های قیمت‌خورده (`{lineNo, productId, qtyMilli, unitPriceMinor, lineTotalMinor}`) ⇒ نتیجهٔ هسته.
 * ⇒ `{discountMinor, lines:[{lineNo, discountMinor}], applied:[{id, name, discountMinor}], code:{code,status}|null, codeRuleIds}`
 */
async function evaluate(run, sf, lines, code, at, { exceptSale = '' } = {}) {
  const rules = await rulesOf(run, sf.shop_id, { exceptSale });
  const facts = await factsOf(run, sf.shop_id, [...new Set(lines.map((l) => l.productId))]);
  const r = P.evaluate({
    stepMinor: STEP_MINOR,
    channel: 'online',
    at: atOf(sf, at),
    coupons: code ? [code] : [],
    lines: lines.map((l) => ({
      key: String(l.lineNo), productId: l.productId, qtyMilli: l.qtyMilli, unitMinor: l.unitPriceMinor, amountMinor: l.lineTotalMinor,
      ...(facts.get(l.productId) || {}),
    })),
  }, rules);
  const byId = new Map(rules.map((x) => [x.id, x]));
  return {
    discountMinor: r.discountMinor,
    lines: r.lines.map((x) => ({ lineNo: Number(x.key), discountMinor: x.discountMinor })),
    applied: r.applied.map((a) => ({ id: a.id, name: a.name, discountMinor: a.discountMinor })),
    code: code ? { code, status: (r.codes[0] && r.codes[0].status) || 'unknown' } : null,
    codeRuleIds: r.applied.map((a) => a.id).filter((id) => str((byId.get(id) || {}).code) !== ''),
  };
}

const REJECT = {
  unknown: 'این کدِ تخفیف در این ویترین نیست',
  inactive: 'این کدِ تخفیف الان فعال نیست',
  used_up: 'ظرفیتِ این کدِ تخفیف پر شده است',
  no_effect: 'این کدِ تخفیف روی این سبد اثری ندارد',
};

/**
 * دورِ `pricer` ِ ثبتِ ویترین (`storefront.checkout`). پرچمِ خاموش ⇒ همان `price` بی تغییر.
 * @param {{key:string, body:object, input:{expectedTotalMinor:number|null}}} ctx
 */
async function wrap(price, sf, { key, body, input }) {
  if (!(await flagOn(sf.shop_id))) return price;
  const code = codeOf(body);
  const sale = `sf:${key}`;
  return async (run, at) => {
    //  «جمعِ دیده‌شده» پس از تخفیف: همین‌جا سنجیده می‌شود، نه درونِ `pricer`
    const expected = input.expectedTotalMinor;
    input.expectedTotalMinor = null;
    let out;
    try { out = await price(run, at); } finally { input.expectedTotalMinor = expected; }
    //  کدِ سقف‌دار: قفلِ ردیفِ همان قاعده پیش از شمارش (دو سفارشِ هم‌زمان پشتِ سرِ هم)
    if (code) await run('SELECT id FROM shop_promotions WHERE shop_id=$1 AND code=$2 AND deleted=false FOR UPDATE', [sf.shop_id, code]);
    const e = await evaluate(run, sf, out.lines, code, at, { exceptSale: sale });
    if (e.code && e.code.status !== 'applied') {
      throw Object.assign(conflict(REJECT[e.code.status] || REJECT.unknown, 'promo_code_rejected'), { data: { promo: e.code } });
    }
    const totalMinor = out.subtotalMinor - e.discountMinor + out.deliveryFeeMinor;
    if (expected !== null && expected !== totalMinor) {
      throw Object.assign(conflict('قیمت‌ها عوض شده است؛ سبد را دوباره ببینید', 'price_changed'), {
        data: { quote: { subtotalMinor: out.subtotalMinor, discountMinor: e.discountMinor, deliveryFeeMinor: out.deliveryFeeMinor, totalMinor }, changes: [] },
      });
    }
    for (const id of e.codeRuleIds) {
      await run(
        `INSERT INTO shop_promo_redemptions (shop_id, promo_id, sale_id, code, user_id, device_id, created_at, updated_at)
         VALUES ($1,$2,$3,$4,'','storefront',$5,$5) ON CONFLICT (shop_id, promo_id, sale_id) DO NOTHING`,
        [sf.shop_id, id, sale, code, at]
      );
    }
    return { ...out, discountMinor: e.discountMinor, totalMinor };
  };
}

// ════════════════════════════════════════════════════════════════════
//  درهای عمومی
// ════════════════════════════════════════════════════════════════════

/** تخفیف‌های خودکارِ زندهٔ «اینترنتی» (بی کد) با پایانِ بازه — و آیا کدی پذیرفته می‌شود؛ هیچ کدی فهرست نمی‌شود */
async function publicList(sf, at = db.now()) {
  if (!(await flagOn(sf.shop_id))) throw notFound('تخفیفی در این ویترین نیست', 'promos_disabled');
  const run = (t, p) => db.query(t, p);
  const rules = (await rulesOf(run, sf.shop_id)).map((r) => P.ruleOf(r));
  const when = atOf(sf, at);
  //  «زنده» = همان شرطِ هسته بی کد و بی سبد: یک سبدِ تهی با همین ساعت
  const live = (r) => r.active && P.validate(r, STEP_MINOR).length === 0 && (r.channels.length === 0 || r.channels.includes('online'))
    && r.groups.length === 0 && P.inWindow(r.window, when) && !(r.maxUses > 0 && r.uses >= r.maxUses)
    //  PRM-10: «نزدیکِ انقضا» محموله می‌خواهد و سبدِ ویترین ندارد ⇒ این‌جا هرگز اعمال نمی‌شود، پس فهرست هم نه
    && r.kind !== 'expiry';
  const auto = rules.filter((r) => r.code === '' && live(r));
  return {
    promos: auto.map((r) => ({ id: r.id, name: r.name, kind: r.kind, endsOn: r.window.to || null })),
    codes: rules.some((r) => r.code !== '' && live(r)),
    serverTime: at,
  };
}

function parseQuote(body) {
  const b = body && typeof body === 'object' ? body : {};
  if (!Array.isArray(b.lines) || !b.lines.length || b.lines.length > MAX_LINES) throw badRequest('سبد معتبر نیست', 'bad_lines');
  const lines = b.lines.map((l, i) => {
    const x = l && typeof l === 'object' ? l : {};
    const productId = str(x.productId);
    if (!PRODUCT_RE.test(productId)) throw badRequest('کالا معتبر نیست', 'bad_lines');
    if (!Number.isSafeInteger(x.qtyMilli) || x.qtyMilli <= 0 || x.qtyMilli > MAX_QTY_MILLI) throw badRequest('مقدار معتبر نیست', 'bad_lines');
    return { lineNo: i + 1, productId, qtyMilli: x.qtyMilli };
  });
  return { lines, code: codeOf(b) };
}

/** پیش‌نمایش: همان قیمتِ ویترین و همان حساب، بی ثبت و بی مصرف. کالای نبوده ⇒ `unavailable` */
async function quote(sf, body, at = db.now()) {
  if (!(await flagOn(sf.shop_id))) throw notFound('تخفیفی در این ویترین نیست', 'promos_disabled');
  const q = parseQuote(body);
  const { PRODUCT_FROM, PRODUCT_COLS, LIVE_PRODUCT, productView } = require('./storefront');
  const run = (t, p) => db.query(t, p);
  const rows = (await run(
    `SELECT ${PRODUCT_COLS} FROM ${PRODUCT_FROM}
      WHERE sp.shop_id=$1 AND sp.product_id = ANY($2::text[]) AND sp.published AND ${LIVE_PRODUCT}`,
    [sf.shop_id, [...new Set(q.lines.map((l) => l.productId))]]
  )).rows;
  const byId = new Map(rows.map((r) => [r.id, productView(r)]));
  const unavailable = [];
  const lines = [];
  for (const l of q.lines) {
    const p = byId.get(l.productId);
    if (!p || p.priceMinor === null) { unavailable.push(l.productId); continue; }
    lines.push({ ...l, unitPriceMinor: p.priceMinor, lineTotalMinor: S.lineTotalMinor(l.qtyMilli, p.priceMinor) });
  }
  const subtotalMinor = lines.reduce((s, l) => s + l.lineTotalMinor, 0);
  const e = await evaluate(run, sf, lines, q.code, at);
  return {
    subtotalMinor, discountMinor: e.discountMinor, totalMinor: subtotalMinor - e.discountMinor,
    lines: e.lines, applied: e.applied.map(({ name, discountMinor }) => ({ name, discountMinor })),
    code: e.code, unavailable,
  };
}

module.exports = { FLAG, codeOf, evaluate, wrap, publicList, quote, parseQuote };
