'use strict';
/**
 * INV-21 — بسته‌بندیِ دوباره و ساختِ بسته در دفترِ موجودیِ سرور (`POST /api/shop/catalog/moves`، v3 §۶.۵).
 *
 *   repack    شکستنِ `qtyMilli` از کالای `productId` (مثلاً کارتن) به `perPackMilli` برای هر یک از کالای `toProductId`
 *             (دانه): خروجی = qtyMilli × perPackMilli ÷ ۱۰۰۰.
 *   assemble  ساختِ `qtyMilli` از کالای `productId` (بسته) با `parts` = [{productId, qtyMilli}] — مقدارِ هر جزء برای
 *             **یک** بسته، تا ۲۰ جزء (همان `Repack.MAX_PARTS` ِ برنامه)؛ مصرفِ هر جزء = مقدار × qtyMilli ÷ ۱۰۰۰.
 *
 *   گرد ───── هیچ: حاصل‌ضرب فقط وقتی پذیرفته است که دقیقاً عددِ صحیحِ هزارم باشد (BigInt، چون از ۲^۵۳ می‌گذرد)؛
 *             وگرنه ۴۰۰ `repack_inexact` و هیچ ردیف — نه نیم‌دانهٔ ساختگی، نه دانهٔ گم‌شده.
 *   سند ───── یک درخواست = یک سند: یک حرکتِ `production` ِ منفی برای هر ورودی، سپس یک `production` ِ مثبت برای خروجی، همه
 *             در یک مکان (قفسه یا انبار)، در یک تراکنش زیرِ قفلِ موجودیِ دکان (`catalog-manual.manualMove`)، با `ref_type`
 *             = repack/assembly و `ref_id` ِ یکسان (درهمِ بدنه) و کلیدهای `staff:<کلید>:<ردیف>` (UNIQUE). کمبودِ هر ورودی
 *             در همان مکان (حالتِ local) ⇒ ۴۰۹ `insufficient_stock` و **هیچ** ردیف؛ تکرارِ همان کلید ⇒ همان حرکت‌ها
 *             بی اثرِ دوم؛ همان کلید با بدنهٔ دیگر ⇒ ۴۰۹. دکان فقط از عضویتِ توکن.
 *   بها ───── دفترِ سرور فقط مقدار دارد (D-23: بها مالِ دستگاه است، `stock.postMove`) ⇒ این سند هیچ پولی نمی‌سازد و گم
 *             نمی‌کند. بهای دقیقِ تولید فقط در دفترِ برنامه است (`Repack` با «دستورِ ساخت و سریِ تولید»: `costMinor` ِ
 *             خروجی = جمعِ بهای ورودی‌ها).
 *   دستگاه ─ ردیفِ تولیدِ برنامه (`warehouseEntries` با `isAdjustment` و `adjKind = repack`، `data/Repack.kt`) با همین پرچم
 *             هنگامِ خواندنِ دفترِ دستگاه (`catalog.ingestTx`) همان `production` ِ علامت‌دار می‌شود با `ref_type = repack` و
 *             `ref_id` = کلیدِ کارِ برنامه (`refId` ِ ردیف)، پس ردیف‌های یک سند به هم می‌رسند — پیش‌تر خروجی «رسیدِ خرید»
 *             (`device_receipt`) و ورودی «اصلاح» (`device_correction`) بود، بی پیوند. جمعِ موجودی همان است.
 *
 * ⛔ پرچمِ `shop.catalog_repack` (دکان یا `all`، پیش‌فرض خاموش، با `shop.catalog`): خاموش ⇒ دو نوع ناشناخته (۴۰۰
 *    `bad_kind`) و `/config` و خواندنِ دفترِ دستگاه بایت‌به‌بایت همان. نوعِ `production` در `stock_moves` از مهاجرتِ ۷۲۰.
 * ⛔ کیت و کالای خدماتی را کاتالوگِ سرور نمی‌شناسد (فقط برنامه رد می‌کند)؛ کالای ناشناخته یا حذف‌شده ⇒ ۴۰۴.
 */
const { badRequest, notFound } = require('../middleware/errors');

const FLAG_KEY = 'shop.catalog_repack';
const KINDS = Object.freeze(['repack', 'assemble']);
/** نوعِ درخواست ⇒ `ref_type` ِ حرکت‌ها (همان نامِ سندِ برنامه: `stock_repack`/`stock_assembly`) */
const REF = Object.freeze({ repack: 'repack', assemble: 'assembly' });
/** همان اجازهٔ «اصلاحِ موجودی»؛ دلیل اختیاری (نوعِ `production` دلیل نمی‌خواهد) */
const SPEC = Object.freeze({ permission: 'stock.adjust', reason: false });
const MAX_PARTS = 20;
/** تولید فقط جایی که کالا نگه داشته می‌شود: نه خراب، نه در مسیر */
const PLACES = Object.freeze(['shop_floor', 'warehouse']);

const isKind = (k) => KINDS.includes(k);

/** پرچمِ دکان (همان `orders.flagOf`: دکان بر `all` جلو می‌افتد، فقط `true`)؛ با [c] از همان اتصالِ تراکنش */
function enabled(shopId, c = null) {
  return require('./orders').flagOf(shopId, FLAG_KEY, c ? (s, p) => c.query(s, p) : null);
}

/** a × b ÷ ۱۰۰۰ فقط وقتی عددِ صحیح و در بازهٔ ۱..max است؛ وگرنه null */
function exactTimes(aMilli, bMilli, max) {
  const n = BigInt(aMilli) * BigInt(bMilli);
  if (n % 1000n !== 0n) return null;
  const q = n / 1000n;
  return q >= 1n && q <= BigInt(max) ? Number(q) : null;
}

const inexact = () => badRequest('مقدارِ خروجی یا مصرفِ جزء دقیقاً به هزارم نمی‌رسد (یا از سقف می‌گذرد)', 'repack_inexact');

/**
 * بدنه ⇒ سند (پیش از تراکنش). [h] = سنجنده‌های `catalog-manual` (str، text، qtyOf، locOf) و سقفِ مقدار.
 * ⇒ { kind, repack: true, productId, location, qtyMilli, reason, note, inputs: [{productId, qtyMilli}], output }
 */
function parse(b, { str, text, qtyOf, locOf, max }) {
  const kind = str(b.kind);
  const productId = str(b.productId);
  if (!productId) throw badRequest('کالا لازم است', 'product_required');
  const out = {
    kind, repack: true, productId, location: locOf(b.location, PLACES, 'shop_floor'),
    qtyMilli: qtyOf(b.qtyMilli), reason: text(b.reason, 80) || REF[kind], note: text(b.note, 300),
  };
  if (kind === 'repack') {
    const to = str(b.toProductId);
    if (!to) throw badRequest('کالای خروجی (دانه) لازم است', 'product_required');
    if (to === productId) throw badRequest('کالای خروجی همان کالای کارتن است', 'same_product');
    const per = qtyOf(b.perPackMilli, 'مقدارِ هر کارتن');
    const made = exactTimes(out.qtyMilli, per, max);
    if (made === null) throw inexact();
    out.inputs = [{ productId, qtyMilli: out.qtyMilli }];
    out.output = { productId: to, qtyMilli: made };
    return out;
  }
  if (!Array.isArray(b.parts) || !b.parts.length) throw badRequest('دستِ کم یک جزء لازم است', 'parts_required');
  if (b.parts.length > MAX_PARTS) throw badRequest(`حداکثر ${MAX_PARTS} جزء`, 'too_many_parts');
  const seen = new Set();
  out.inputs = b.parts.map((p) => {
    const pid = str(p && p.productId);
    if (!pid) throw badRequest('کالای جزء لازم است', 'product_required');
    if (pid === productId) throw badRequest('بسته جزءِ خودش نمی‌شود', 'same_product');
    if (seen.has(pid)) throw badRequest('یک جزء دو بار آمده است', 'duplicate_part');
    seen.add(pid);
    const need = exactTimes(qtyOf(p.qtyMilli, 'مقدارِ جزء'), out.qtyMilli, max);
    if (need === null) throw inexact();
    return { productId: pid, qtyMilli: need };
  });
  out.output = { productId, qtyMilli: out.qtyMilli };
  return out;
}

/** همهٔ کالاهای سند، مرتب */
const ids = (m) => [...new Set([...m.inputs.map((l) => l.productId), m.output.productId])].sort();

/** هر کالای سند در کاتالوگِ همین دکان و حذف‌نشده؛ وگرنه ۴۰۴ (درونِ تراکنش، پس از خواندنِ دفترِ دستگاه) */
async function requireProducts(c, shopId, m) {
  const want = ids(m);
  const have = new Set((await c.query(
    'SELECT product_id FROM catalog_products WHERE shop_id=$1 AND product_id = ANY($2::text[]) AND deleted = false',
    [shopId, want]
  )).rows.map((r) => r.product_id));
  if (want.some((p) => !have.has(p))) throw notFound('کالا پیدا نشد', 'product_not_found');
}

/** سند ⇒ حرکت‌ها: ورودی‌ها (منفی) سپس خروجی (مثبت)، همه `production` با همان مکان، پیوند و دلیل */
function plan(m, base) {
  const doc = { ...base, refType: REF[m.kind], location: m.location, type: 'production', reason: m.reason };
  return [
    ...m.inputs.map((l) => ({ ...doc, productId: l.productId, qtyMilli: -l.qtyMilli })),
    { ...doc, productId: m.output.productId, qtyMilli: m.output.qtyMilli },
  ];
}

/**
 * ردیفِ `warehouseEntries` ِ دستگاه: ردیفِ تولیدِ برنامه (`adjKind = repack` با کلیدِ کار) ⇒ پیوندِ همان سند
 * `{ production: true, refType: 'repack', refId }`؛ ردیفِ دیگر ⇒ {} (همان امروز).
 */
function deviceRef(d) {
  const key = d && d.isAdjustment === true && d.adjKind === 'repack' && typeof d.refId === 'string' ? d.refId.trim() : '';
  return key ? { production: true, refType: 'repack', refId: key.slice(0, 128) } : {};
}

/** ⇒ async (d) ⇒ همان [deviceRef] وقتی پرچم روشن است، وگرنه {}؛ پرچم فقط با نخستین ردیفِ تولید و یک بار برای کلِ همان خواندن */
function deviceRefs(shopId, c) {
  let on = null;
  return async (d) => {
    const ref = deviceRef(d);
    if (!ref.production) return ref;
    if (on === null) on = await enabled(shopId, c);
    return on ? ref : {};
  };
}

module.exports = { FLAG_KEY, KINDS, REF, SPEC, MAX_PARTS, PLACES, isKind, enabled, exactTimes, parse, ids, requireProducts, plan, deviceRef, deviceRefs };
