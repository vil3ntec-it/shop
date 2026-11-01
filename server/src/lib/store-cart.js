'use strict';
/**
 * سبدِ ماندگارِ حسابِ مشتری روی سرور (فاز ۴؛ CHK-03، ۲۳ S140).
 * شرحِ جدول: ‎migrations/174_store_customer_carts.sql‎.
 *
 *   ذخیره ─── هر (مشتری، ویترین) یک سبد: فقط شناسهٔ کالا، مقدار (هزارم) و یادداشتِ
 *             قلم، حداکثر همان ۱۰۰ قلمِ ثبت. قیمت هرگز ذخیره نمی‌شود: هر خواندن قیمت و
 *             «هنوز در ویترین» را از همان دروازهٔ کالای ویترین می‌خواند و ثبت دوباره
 *             می‌سنجد (CHK-17). کالای بیرون‌رفته پاک نمی‌شود؛ «نیست» نشان داده می‌شود.
 *   نوشتن ─── جایگزینیِ کامل با نسخهٔ دیده‌شده (CAS): نسخهٔ کهنه ⇒ ۴۰۹ `cart_changed`
 *             با سبدِ تازه — دو دستگاهِ یک حساب هم را بی‌صدا پاک نمی‌کنند. خالی کردن همان
 *             نوشتنِ فهرستِ خالی است و ردیف می‌ماند تا نسخه به صفر برنگردد (بی ABA).
 *   ادغام ─── سبدِ مهمانِ همین مرورگر پس از ورود (S140): برای هر کالا بیشینهٔ دو مقدار،
 *             نه جمع ⇒ تکرارِ همان ادغام (دو زدن، تلاشِ دوباره پس از قطع) اثرِ دوم ندارد و
 *             نسخه را هم بالا نمی‌برد. بیش از سقفِ قلم ⇒ ۴۰۹ `cart_full` و هیچ چیز عوض نمی‌شود.
 *
 * ⛔ پرچمِ پیش‌فرض خاموشِ `shop.storefront.cart` (`live_config`، دکان یا all): خاموش ⇒
 *    درها همان پاسخِ مسیرِ ناشناختهٔ main‌اند (بیت‌به‌بیت) و این جدول خالی می‌ماند.
 * ⛔ هر خواندن و نوشتن به شناسهٔ مشتریِ نشست و دکانِ `slug` بسته است؛ از بدنه نمی‌آید.
 * ⛔ پاک کردنِ حساب یا دکان ⇒ سبد هم (cascade). سبدِ مهمان در مرورگر می‌ماند.
 */
const db = require('../db');
const { tx, now } = db;
const orders = require('./orders');
const sf = require('./storefront');
const S = require('./order-states');
const { stripControl } = require('./validate');
const { badRequest, conflict } = require('../middleware/errors');

const FLAG_KEY = 'shop.storefront.cart';
//  همان سقفِ ثبت؛ `catalogByIds` هم تا همین ۱۰۰ شناسه را یک‌جا می‌خواند
const MAX_LINES = sf.MAX_CHECKOUT_LINES;
const MAX_NOTE = 200;

const str = (v) => (typeof v === 'string' ? v.trim() : '');
const obj = (v) => (v && typeof v === 'object' && !Array.isArray(v) ? v : {});

const flagOn = (shopId) => orders.flagOf(shopId, FLAG_KEY);

/** قلم‌های سبد — همان قاعدهٔ قلمِ ثبتِ ویترین (`parseCheckout`). فهرستِ خالی درست است. */
function parseItems(raw) {
  if (!Array.isArray(raw)) throw badRequest('فهرستِ قلم‌های سبد لازم است', 'bad_lines');
  if (raw.length > MAX_LINES) throw badRequest(`حداکثر ${MAX_LINES} قلم`, 'bad_lines');
  const seen = new Set();
  return raw.map((l, i) => {
    const x = obj(l);
    const productId = str(x.productId);
    if (!sf.PRODUCT_ID_RE.test(productId)) throw badRequest(`کالای قلمِ ${i + 1} معتبر نیست`, 'bad_lines');
    if (seen.has(productId)) throw badRequest('یک کالا دو بار در سبد آمده است', 'bad_lines');
    seen.add(productId);
    const q = x.qtyMilli;
    if (typeof q !== 'number' || !Number.isSafeInteger(q) || q < 1 || q > sf.MAX_QTY_MILLI) {
      throw badRequest(`مقدارِ قلمِ ${i + 1} در بازهٔ مجاز نیست`, 'bad_number');
    }
    return { productId, qtyMilli: q, note: stripControl(str(x.note)).slice(0, MAX_NOTE) };
  });
}

function parseVersion(v) {
  if (typeof v !== 'number' || !Number.isSafeInteger(v) || v < 0) {
    throw badRequest('نسخهٔ سبدی که دیده‌اید لازم است', 'bad_version');
  }
  return v;
}

const itemsOf = (row) => (row ? (typeof row.items === 'string' ? JSON.parse(row.items) : row.items) : []);

/**
 * ردیفِ سبد، قفل‌شده تا پایانِ تراکنش. نبود ⇒ ردیفِ خالیِ نسخهٔ ۰ همین‌جا ساخته
 * می‌شود تا دو نوشتنِ هم‌زمانِ نخست هر دو «۰» را نپذیرند.
 */
async function locked(c, customerId, shopId, at) {
  await c.query(
    `INSERT INTO store_customer_carts (customer_id, shop_id, items, version, updated_at)
     VALUES ($1,$2,'[]'::jsonb,0,$3) ON CONFLICT (customer_id, shop_id) DO NOTHING`,
    [customerId, shopId, at]
  );
  return (await c.query(
    'SELECT items, version, updated_at FROM store_customer_carts WHERE customer_id = $1 AND shop_id = $2 FOR UPDATE',
    [customerId, shopId]
  )).rows[0];
}

async function write(c, customerId, shopId, items, version, at) {
  return (await c.query(
    `UPDATE store_customer_carts SET items = $3::jsonb, version = $4, updated_at = $5
      WHERE customer_id = $1 AND shop_id = $2 RETURNING items, version, updated_at`,
    [customerId, shopId, JSON.stringify(items), version, at]
  )).rows[0];
}

/**
 * نمای سبد با قیمت و وضعیتِ امروز. `subtotalMinor` فقط جمعِ قلم‌های در دسترس است و
 * وعده نیست: هزینهٔ ارسال، حداقل و قیمتِ نهایی را ثبت می‌گوید.
 */
async function view(store, row) {
  const items = itemsOf(row);
  const ids = items.map((i) => i.productId);
  const live = ids.length ? (await sf.catalogByIds(store.shop_id, ids.join(','))).items : [];
  const byId = new Map(live.map((p) => [p.id, p]));
  //  ۳۲.۳: با پرچمِ `shop.catalog` وضعیتِ موجودی از دفترِ سرور؛ خاموش ⇒ «در حال بررسی»
  const stock = live.length ? await require('./catalog').publicStock(store.shop_id, live.map((p) => p.id)) : null;
  let subtotalMinor = 0;
  const lines = items.map((i) => {
    const p = byId.get(i.productId);
    if (!p) {
      return {
        productId: i.productId, qtyMilli: i.qtyMilli, note: i.note, available: false, reason: 'not_in_store',
        name: null, unit: null, priceMinor: null, lineTotalMinor: null, maxQtyMilli: null, overLimit: false, stock: null,
      };
    }
    const lineTotalMinor = S.lineTotalMinor(i.qtyMilli, p.priceMinor);
    subtotalMinor += lineTotalMinor;
    return {
      productId: i.productId, qtyMilli: i.qtyMilli, note: i.note, available: true, reason: '',
      name: p.name, unit: p.unit, priceMinor: p.priceMinor, lineTotalMinor, maxQtyMilli: p.maxQtyMilli,
      overLimit: p.maxQtyMilli !== null && i.qtyMilli > p.maxQtyMilli,
      stock: (stock && stock.get(p.id)) || p.stock,
    };
  });
  return {
    version: row ? Number(row.version) : 0,
    updatedAt: row && Number(row.version) > 0 ? Number(row.updated_at) : null,
    maxLines: MAX_LINES,
    lines,
    subtotalMinor,
  };
}

function withData(err, data) {
  return Object.assign(err, { data });
}

async function get(store, customer) {
  const row = await db.one(
    'SELECT items, version, updated_at FROM store_customer_carts WHERE customer_id = $1 AND shop_id = $2',
    [customer.id, store.shop_id]
  );
  return view(store, row);
}

/** جایگزینیِ کامل: `{ version, items }` — نسخه همان که مشتری دیده. */
async function replace(store, customer, body) {
  const b = obj(body);
  const version = parseVersion(b.version);
  const items = parseItems(b.items);
  const out = await tx(async (c) => {
    const at = now();
    const cur = await locked(c, customer.id, store.shop_id, at);
    if (Number(cur.version) !== version) return { stale: cur };
    return { row: await write(c, customer.id, store.shop_id, items, Number(cur.version) + 1, at) };
  });
  if (out.stale) {
    throw withData(conflict('سبد در دستگاهِ دیگری عوض شده است؛ سبدِ تازه را ببینید', 'cart_changed'),
      { cart: await view(store, out.stale) });
  }
  return view(store, out.row);
}

/** ادغامِ سبدِ مهمان: `{ items }`. بیشینهٔ مقدار؛ یادداشتِ سبدِ حساب بر یادداشتِ مهمان مقدم است. */
async function merge(store, customer, body) {
  const guest = parseItems(obj(body).items);
  const out = await tx(async (c) => {
    const at = now();
    const cur = await locked(c, customer.id, store.shop_id, at);
    const merged = itemsOf(cur).map((i) => ({ ...i }));
    const byId = new Map(merged.map((i) => [i.productId, i]));
    let changed = false;
    for (const g of guest) {
      const m = byId.get(g.productId);
      if (!m) {
        const add = { ...g };
        merged.push(add);
        byId.set(add.productId, add);
        changed = true;
        continue;
      }
      if (g.qtyMilli > m.qtyMilli) { m.qtyMilli = g.qtyMilli; changed = true; }
      if (!m.note && g.note) { m.note = g.note; changed = true; }
    }
    if (merged.length > MAX_LINES) return { full: cur };
    if (!changed) return { row: cur };
    return { row: await write(c, customer.id, store.shop_id, merged, Number(cur.version) + 1, at) };
  });
  if (out.full) {
    throw withData(conflict(`سبد بیش از ${MAX_LINES} قلم می‌شود؛ چیزی ادغام نشد`, 'cart_full'),
      { cart: await view(store, out.full) });
  }
  return view(store, out.row);
}

module.exports = { FLAG_KEY, MAX_LINES, flagOn, parseItems, get, replace, merge };
