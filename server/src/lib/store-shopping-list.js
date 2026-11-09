'use strict';
/**
 * «فهرستِ خرید» ِ حسابِ مشتری روی سرور (WEB-11). شرحِ جدول: ‎migrations/432_store_customer_lists.sql‎.
 *
 *   ذخیره ─── هر (مشتری، ویترین) یک فهرست: فقط شناسهٔ کالا و مقدار (هزارم)، به ترتیبِ مشتری، حداکثر همان
 *             ۱۰۰ قلمِ سبد و ثبت. قیمت، نام و موجودی هرگز ذخیره نمی‌شود: هر خواندن کالاهای **همین حالا**
 *             در ویترین را از همان دروازهٔ کاتالوگ می‌دهد (`items`) و «همه به سبد» قیمت را از نو می‌خواند.
 *             کالای بیرون‌رفته یا پنهان پاک نمی‌شود؛ فقط `available: false` (همان پاسخِ کالای نبوده ⇒ بی نشت).
 *   نوشتن ─── جایگزینیِ کامل با نسخهٔ دیده‌شده (CAS): نسخهٔ کهنه ⇒ ۴۰۹ `list_changed` با فهرستِ تازه.
 *             همان محتوا (تلاشِ دوباره پس از قطع، دو زدن) ⇒ ۲۰۰ با همان فهرست و بی بالا رفتنِ نسخه، با هر نسخه‌ای.
 *   ادغام ─── فهرستِ همین دستگاه پس از ورود: کالای تازه پشتِ فهرستِ حساب، و برای کالای مشترک بیشینهٔ دو مقدار
 *             (نه جمع) ⇒ تکرارِ همان ادغام اثرِ دوم ندارد و نسخه را بالا نمی‌برد. بیش از سقف ⇒ ۴۰۹ `list_full`
 *             و هیچ چیز عوض نمی‌شود.
 *   سقف ──── ۱۰۰ قلم در هر فهرست و حداکثر `MAX_LISTS` فهرستِ پُر برای هر مشتری در همهٔ ویترین‌ها (۴۰۹ `too_many_lists`).
 *
 * ⛔ همان پرچمِ پیش‌فرض خاموشِ ♥ (`shop.storefront.favorites`؛ درها در `routes/storefront.js`).
 * ⛔ customer_id فقط از نشستِ مشتری و shop_id فقط از ویترینِ روشنِ `slug`؛ هیچ‌کدام از بدنه نمی‌آید.
 */
const db = require('../db');
const { tx, now } = db;
const sf = require('./storefront');
const { badRequest, conflict } = require('../middleware/errors');

const MAX_LINES = sf.MAX_CHECKOUT_LINES;
const MAX_LISTS = 50;

const obj = (v) => (v && typeof v === 'object' && !Array.isArray(v) ? v : {});
const str = (v) => (typeof v === 'string' ? v.trim() : '');

/** قلم‌های فهرست — همان قاعدهٔ شناسه و مقدارِ قلمِ ثبت. فهرستِ خالی درست است. */
function parseItems(raw) {
  if (!Array.isArray(raw)) throw badRequest('فهرستِ قلم‌ها لازم است', 'bad_lines');
  if (raw.length > MAX_LINES) throw badRequest(`حداکثر ${MAX_LINES} قلم`, 'bad_lines');
  const seen = new Set();
  return raw.map((l, i) => {
    const x = obj(l);
    const productId = str(x.productId);
    if (!sf.PRODUCT_ID_RE.test(productId)) throw badRequest(`کالای قلمِ ${i + 1} معتبر نیست`, 'bad_lines');
    if (seen.has(productId)) throw badRequest('یک کالا دو بار در فهرست آمده است', 'bad_lines');
    seen.add(productId);
    const q = x.qtyMilli;
    if (typeof q !== 'number' || !Number.isSafeInteger(q) || q < 1 || q > sf.MAX_QTY_MILLI) {
      throw badRequest(`مقدارِ قلمِ ${i + 1} در بازهٔ مجاز نیست`, 'bad_number');
    }
    return { productId, qtyMilli: q };
  });
}

function parseVersion(v) {
  if (typeof v !== 'number' || !Number.isSafeInteger(v) || v < 0) {
    throw badRequest('نسخهٔ فهرستی که دیده‌اید لازم است', 'bad_version');
  }
  return v;
}

const itemsOf = (row) => (row ? (typeof row.items === 'string' ? JSON.parse(row.items) : row.items) : []);
const same = (a, b) => JSON.stringify(a.map((i) => [i.productId, i.qtyMilli])) === JSON.stringify(b.map((i) => [i.productId, i.qtyMilli]));

/** ردیفِ فهرست، قفل‌شده تا پایانِ تراکنش (نبود ⇒ ردیفِ خالیِ نسخهٔ ۰ همین‌جا، تا دو نوشتنِ نخست هر دو «۰» را نپذیرند). */
async function locked(c, customerId, shopId, at) {
  await c.query(
    `INSERT INTO store_customer_lists (customer_id, shop_id, items, version, updated_at)
     VALUES ($1,$2,'[]'::jsonb,0,$3) ON CONFLICT (customer_id, shop_id) DO NOTHING`,
    [customerId, shopId, at]
  );
  return (await c.query(
    'SELECT items, version, updated_at FROM store_customer_lists WHERE customer_id = $1 AND shop_id = $2 FOR UPDATE',
    [customerId, shopId]
  )).rows[0];
}

/** فهرستِ خالی ⇒ پُر شدنش سقفِ فهرست‌های پُرِ همین مشتری را می‌سنجد (زیرِ قفلِ ردیفِ مشتری). */
async function write(c, customerId, shopId, cur, items, at) {
  if (!itemsOf(cur).length && items.length) {
    await c.query('SELECT id FROM store_customers WHERE id = $1 FOR UPDATE', [customerId]);
    const n = Number((await c.query(
      `SELECT COUNT(*)::int AS n FROM store_customer_lists
        WHERE customer_id = $1 AND shop_id <> $2 AND jsonb_array_length(items) > 0`,
      [customerId, shopId]
    )).rows[0].n);
    if (n >= MAX_LISTS) return { many: true };
  }
  return {
    row: (await c.query(
      `UPDATE store_customer_lists SET items = $3::jsonb, version = $4, updated_at = $5
        WHERE customer_id = $1 AND shop_id = $2 RETURNING items, version, updated_at`,
      [customerId, shopId, JSON.stringify(items), Number(cur.version) + 1, at]
    )).rows[0],
  };
}

/** نمای فهرست: قلم‌ها (شناسه، مقدار، «هست؟») و کالاهای همین حالا در ویترین با قیمتِ امروز. */
async function view(store, row) {
  const items = itemsOf(row);
  const live = items.length ? (await sf.catalogByIds(store.shop_id, items.map((i) => i.productId).join(','))).items : [];
  const byId = new Map(live.map((p) => [p.id, p]));
  return {
    version: row ? Number(row.version) : 0,
    updatedAt: row && Number(row.version) > 0 ? Number(row.updated_at) : null,
    maxLines: MAX_LINES,
    lines: items.map((i) => ({ productId: i.productId, qtyMilli: i.qtyMilli, available: byId.has(i.productId) })),
    items: items.map((i) => byId.get(i.productId)).filter(Boolean),
  };
}

const withData = (err, data) => Object.assign(err, { data });
const tooMany = () => conflict(`حداکثر ${MAX_LISTS} فهرستِ خرید در ویترین‌های مختلف`, 'too_many_lists');

async function get(store, customer) {
  const row = await db.one(
    'SELECT items, version, updated_at FROM store_customer_lists WHERE customer_id = $1 AND shop_id = $2',
    [customer.id, store.shop_id]
  );
  return view(store, row);
}

/** جایگزینیِ کامل: `{ version, items }`. همان محتوا ⇒ بی‌اثر (تکرارِ امن)؛ نسخهٔ کهنه ⇒ ۴۰۹ با فهرستِ تازه. */
async function replace(store, customer, body) {
  const b = obj(body);
  const version = parseVersion(b.version);
  const items = parseItems(b.items);
  const out = await tx(async (c) => {
    const at = now();
    const cur = await locked(c, customer.id, store.shop_id, at);
    if (same(itemsOf(cur), items)) return { row: cur };
    if (Number(cur.version) !== version) return { stale: cur };
    return write(c, customer.id, store.shop_id, cur, items, at);
  });
  if (out.many) throw tooMany();
  if (out.stale) {
    throw withData(conflict('فهرستِ خرید در دستگاهِ دیگری عوض شده است؛ فهرستِ تازه را ببینید', 'list_changed'),
      { list: await view(store, out.stale) });
  }
  return view(store, out.row);
}

/** ادغامِ فهرستِ دستگاه: `{ items }`. کالای تازه پشتِ فهرستِ حساب؛ کالای مشترک بیشینهٔ دو مقدار. */
async function merge(store, customer, body) {
  const guest = parseItems(obj(body).items);
  const out = await tx(async (c) => {
    const at = now();
    const cur = await locked(c, customer.id, store.shop_id, at);
    const merged = itemsOf(cur).map((i) => ({ productId: i.productId, qtyMilli: i.qtyMilli }));
    const byId = new Map(merged.map((i) => [i.productId, i]));
    let changed = false;
    for (const g of guest) {
      const m = byId.get(g.productId);
      if (!m) {
        const add = { ...g };
        merged.push(add);
        byId.set(add.productId, add);
        changed = true;
      } else if (g.qtyMilli > m.qtyMilli) {
        m.qtyMilli = g.qtyMilli;
        changed = true;
      }
    }
    if (merged.length > MAX_LINES) return { full: cur };
    if (!changed) return { row: cur };
    return write(c, customer.id, store.shop_id, cur, merged, at);
  });
  if (out.many) throw tooMany();
  if (out.full) {
    throw withData(conflict(`فهرست بیش از ${MAX_LINES} قلم می‌شود؛ چیزی ادغام نشد`, 'list_full'),
      { list: await view(store, out.full) });
  }
  return view(store, out.row);
}

module.exports = { MAX_LINES, MAX_LISTS, parseItems, get, replace, merge };
