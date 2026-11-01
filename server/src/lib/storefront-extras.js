'use strict';
/**
 * ویترین — علاقه‌مندی‌های حسابِ مشتری (WEB-11) و بنرهای زمان‌بندی‌شدهٔ هر ناحیه (WEB-24).
 *
 *   علاقه‌مندی ── پرچمِ خاموشِ `shop.storefront.favorites`. فهرستِ هر مشتری در هر ویترین
 *                (حسابِ مشتری سراسری است). افزودن فقط کالای فروختنیِ همین ویترین؛ تکرار
 *                بی‌اثر؛ حداکثر `MAX_FAVORITES`. خواندن فقط کالاهایی را می‌دهد که **همین حالا**
 *                در ویترین‌اند (همان `catalogByIds`)؛ ردیفِ کالای پنهان‌شده می‌ماند تا اگر
 *                برگشت، دوباره دیده شود. پاک کردنِ حساب ⇒ پاک (cascade).
 *   بنر ──────── پرچمِ خاموشِ `shop.storefront.banners`. صاحب/مدیر کلِ فهرست را یک‌جا می‌نویسد
 *                (حداکثر `MAX_BANNERS`): عنوان، متن، پیوند (کالا/دسته/هیچ)، بازهٔ نمایش
 *                (ms، حداکثر ۹۲ روز) و ناحیه‌ها (خالی = همه‌جا). نمای عمومی فقط بنرِ **اکنونِ
 *                ساعتِ سرور** را می‌دهد، فقط برای همان ناحیه (یا همه‌جا)، و بنری که به کالای
 *                نافروختنی پیوند دارد پنهان است.
 *
 *   ⛔ shop_id فقط از نشست یا ویترینِ روشن؛ customer_id فقط از نشستِ مشتری.
 *   ⛔ خاموش ⇒ هیچ خواندن یا نوشتنی (درِ عمومی ۴۰۴ و درِ دکان ۴۰۳، مثلِ بقیهٔ ویترین).
 */
const { many, query, tx, now } = require('../db');
const orders = require('./orders');
const sf = require('./storefront');
const sfd = require('./storefront-delivery');
const { ulid } = require('./ulid');
const { stripControl } = require('./validate');
const { badRequest, notFound, conflict } = require('../middleware/errors');

const FAVORITES_FLAG = 'shop.storefront.favorites';
const BANNERS_FLAG = 'shop.storefront.banners';
const MAX_FAVORITES = 200;
const MAX_BANNERS = 20;
const MAX_PUBLIC_BANNERS = 5;
const MAX_ZONES = 20;
const MAX_SPAN_MS = 92 * 24 * 60 * 60 * 1000;

const str = (v) => (typeof v === 'string' ? v.trim() : '');
const text = (v, max) => stripControl(str(v)).slice(0, max);
const obj = (v) => (v && typeof v === 'object' && !Array.isArray(v) ? v : {});

const favoritesOn = (shopId) => orders.flagOf(shopId, FAVORITES_FLAG);
const bannersOn = (shopId) => orders.flagOf(shopId, BANNERS_FLAG);

// ── علاقه‌مندی (WEB-11) ───────────────────────────────────────────

async function favorites(shopId, customer) {
  const rows = await many(
    `SELECT product_id FROM store_customer_favorites WHERE customer_id = $1 AND shop_id = $2
      ORDER BY created_at DESC, product_id LIMIT $3`,
    [customer.id, String(shopId), MAX_FAVORITES]
  );
  const ids = rows.map((r) => r.product_id);
  const live = new Map((await sf.catalogByIds(shopId, ids.join(','))).items.map((p) => [p.id, p]));
  return { items: ids.filter((id) => live.has(id)).map((id) => live.get(id)), max: MAX_FAVORITES };
}

async function addFavorite(shopId, customer, productId) {
  const id = String(productId || '');
  if (!sf.PRODUCT_ID_RE.test(id) || !(await sf.catalogByIds(shopId, id)).items.length) {
    throw notFound('این کالا در ویترین نیست', 'product_not_found');
  }
  return tx(async (c) => {
    await c.query('SELECT id FROM store_customers WHERE id = $1 FOR UPDATE', [customer.id]);
    const has = (await c.query(
      'SELECT 1 FROM store_customer_favorites WHERE customer_id = $1 AND shop_id = $2 AND product_id = $3',
      [customer.id, String(shopId), id]
    )).rows.length > 0;
    if (has) return { productId: id, added: false };
    const n = Number((await c.query(
      'SELECT COUNT(*)::int AS n FROM store_customer_favorites WHERE customer_id = $1 AND shop_id = $2',
      [customer.id, String(shopId)]
    )).rows[0].n);
    if (n >= MAX_FAVORITES) throw conflict(`حداکثر ${MAX_FAVORITES} کالا در علاقه‌مندی‌ها`, 'too_many_favorites');
    await c.query(
      'INSERT INTO store_customer_favorites (customer_id, shop_id, product_id, created_at) VALUES ($1,$2,$3,$4)',
      [customer.id, String(shopId), id, now()]
    );
    return { productId: id, added: true };
  });
}

async function removeFavorite(shopId, customer, productId) {
  const r = await query(
    'DELETE FROM store_customer_favorites WHERE customer_id = $1 AND shop_id = $2 AND product_id = $3',
    [customer.id, String(shopId), String(productId || '')]
  );
  return { deleted: r.rowCount > 0 };
}

// ── بنر (WEB-24) ──────────────────────────────────────────────────

const LINKS = ['none', 'product', 'category'];

function parseBanner(raw, i) {
  const b = obj(raw);
  const at = (k) => {
    const n = Number(b[k]);
    if (!Number.isSafeInteger(n) || n < 0) throw badRequest(`بنرِ ${i + 1}: زمانِ ${k === 'startAt' ? 'آغاز' : 'پایان'} معتبر نیست`, 'bad_banner');
    return n;
  };
  const title = text(b.title, 80);
  if (!title) throw badRequest(`بنرِ ${i + 1}: عنوان لازم است`, 'bad_banner');
  const start = at('startAt');
  const end = at('endAt');
  if (end <= start) throw badRequest(`بنرِ ${i + 1}: پایان باید پس از آغاز باشد`, 'bad_banner');
  if (end - start > MAX_SPAN_MS) throw badRequest(`بنرِ ${i + 1}: حداکثر ۹۲ روز`, 'bad_banner');
  const link = obj(b.link);
  const type = LINKS.includes(link.type) ? link.type : 'none';
  const value = type === 'product' ? str(link.value) : type === 'category' ? text(link.value, 80) : '';
  if (type === 'product' && !sf.PRODUCT_ID_RE.test(value)) throw badRequest(`بنرِ ${i + 1}: کالای پیوند معتبر نیست`, 'bad_banner');
  if (type === 'category' && !value) throw badRequest(`بنرِ ${i + 1}: دستهٔ پیوند لازم است`, 'bad_banner');
  const zones = b.zoneIds === undefined || b.zoneIds === null ? [] : b.zoneIds;
  if (!Array.isArray(zones) || zones.length > MAX_ZONES || !zones.every((z) => typeof z === 'string' && sfd.ID_RE.test(z))) {
    throw badRequest(`بنرِ ${i + 1}: ناحیه‌ها معتبر نیستند`, 'bad_banner');
  }
  const sort = b.sort === undefined ? 0 : Number(b.sort);
  if (!Number.isInteger(sort) || sort < 0 || sort > 100) throw badRequest(`بنرِ ${i + 1}: اولویت ۰ تا ۱۰۰`, 'bad_banner');
  return { title, body: text(b.body, 200), type, value, zones: [...new Set(zones)], start, end, sort };
}

function bannerView(r) {
  return {
    id: r.id, title: r.title, body: r.body,
    link: { type: r.link_type, value: r.link_value },
    zoneIds: Array.isArray(r.zone_ids) ? r.zone_ids : JSON.parse(r.zone_ids || '[]'),
    startAt: Number(r.start_at), endAt: Number(r.end_at), sort: Number(r.sort),
  };
}

async function banners(shopId) {
  return (await many('SELECT * FROM storefront_banners WHERE shop_id = $1 ORDER BY sort DESC, start_at, id', [String(shopId)]))
    .map(bannerView);
}

/** کلِ فهرست یک‌جا (هر بنر شناسهٔ تازه می‌گیرد) — در یک تراکنش. */
async function saveBanners(shopId, body, userId) {
  const list = obj(body).banners;
  if (!Array.isArray(list) || list.length > MAX_BANNERS) throw badRequest(`فهرستِ بنرها (حداکثر ${MAX_BANNERS})`, 'bad_banner');
  const parsed = list.map(parseBanner);
  await tx(async (c) => {
    await c.query('DELETE FROM storefront_banners WHERE shop_id = $1', [String(shopId)]);
    const at = now();
    for (const b of parsed) {
      await c.query(
        `INSERT INTO storefront_banners (id, shop_id, title, body, link_type, link_value, zone_ids, start_at, end_at, sort, updated_by, updated_at)
         VALUES ($1,$2,$3,$4,$5,$6,$7::jsonb,$8,$9,$10,$11,$12)`,
        [ulid(at), String(shopId), b.title, b.body, b.type, b.value, JSON.stringify(b.zones), b.start, b.end, b.sort, String(userId || ''), at]
      );
    }
  });
  return banners(shopId);
}

/** نمای عمومی: بنرهای همین لحظه برای این ناحیه (یا همه‌جا)؛ پیوندِ کالای نافروختنی ⇒ پنهان. */
async function activeBanners(shopId, { zone } = {}, at = now()) {
  const z = typeof zone === 'string' && sfd.ID_RE.test(zone) ? zone : '';
  const rows = (await many(
    `SELECT * FROM storefront_banners WHERE shop_id = $1 AND start_at <= $2 AND end_at > $2
      ORDER BY sort DESC, start_at, id`,
    [String(shopId), at]
  )).map(bannerView).filter((b) => !b.zoneIds.length || (z && b.zoneIds.includes(z)));
  const productIds = rows.filter((b) => b.link.type === 'product').map((b) => b.link.value);
  const live = productIds.length
    ? new Set((await sf.catalogByIds(shopId, productIds.join(','))).items.map((p) => p.id)) : new Set();
  return rows.filter((b) => b.link.type !== 'product' || live.has(b.link.value)).slice(0, MAX_PUBLIC_BANNERS)
    .map((b) => ({ id: b.id, title: b.title, body: b.body, link: b.link, startAt: b.startAt, endAt: b.endAt }));
}

module.exports = {
  FAVORITES_FLAG, BANNERS_FLAG, MAX_FAVORITES, MAX_BANNERS,
  favoritesOn, bannersOn, favorites, addFavorite, removeFavorite,
  banners, saveBanners, activeBanners,
};
