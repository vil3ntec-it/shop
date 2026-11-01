'use strict';
/**
 * ویترین — پیش‌نمایشِ پیوند (WEB-20) و سئو (WEB-17)، پشتِ پرچمِ خاموشِ
 * `shop.storefront.seo` (دکان / `all`؛ فقط `true` روشن است).
 *
 *   چرا سرور ── پوستهٔ ویترین ایستاست و مسیرِ کالا `#/p/<id>` است؛ خزندهٔ
 *              واتس‌اپ و فیسبوک نه JS اجرا می‌کند و نه `#…` را می‌فرستد. پس
 *              کالا یک نشانیِ واقعی می‌گیرد — `<slug>.<دامنه>/p/<id>` (و همان
 *              `/store/<slug>/p/<id>`) — و سرور همان پوسته را با برچسب‌های
 *              `<head>` ِ همان کالا می‌دهد. بقیهٔ صفحه همان است؛ JS ِ ویترین
 *              مسیرِ `/p/<id>` را به `#/p/<id>` برمی‌گرداند.
 *   برچسب‌ها ── Open Graph (`og:*`، `product:price:*`)، کارتِ توییتر، `canonical`،
 *              `hreflang` برای fa/en/ps (+ `x-default`) با `?lang=` ِ همان
 *              `kit/boot.js`، و دادهٔ ساختاریافتهٔ `Product`/`Offer` (JSON-LD).
 *   نقشهٔ سایت ─ `/sitemap.xml` روی میزبانِ دکان (و `/store/<slug>/sitemap.xml`)
 *              با همهٔ کالاهای فروختنیِ ویترین (حداکثر `MAX_SITEMAP`) و
 *              `xhtml:link` ِ سه زبان؛ `robots.txt` ِ همان میزبان به آن اشاره می‌کند.
 *
 *   ⛔ خاموش ⇒ هیچ رفتاری عوض نمی‌شود: `/store/<slug>` همان پرونده را می‌فرستد،
 *     نشانی‌های تازه همان ۴۰۴ ِ امروز را دارند و `GET /api/store/:slug` کلیدِ
 *     تازه‌ای ندارد.
 *   ⛔ فقط همان چیزی که ویترینِ عمومی نشان می‌دهد (نام، توضیح، قیمت، عکس، موجودیِ
 *     واقعی)؛ کالای پنهان، بی‌قیمت یا بی‌نام نه صفحه دارد نه در نقشه می‌آید.
 *   ⛔ هر متنِ دکان پیش از نشستن در HTML گریزانده می‌شود و JSON-LD هیچ `<`
 *     ندارد (بیرون‌پریدن از `<script>` ممکن نیست). جای‌گذاری با تابع است، نه
 *     رشته، تا `$&` ِ نامِ کالا الگو نشود.
 *   ⛔ نشانیِ مطلق فقط از `config.storefront.domain` ساخته می‌شود، نه از سرآیندِ
 *     `Host` ِ درخواست (بی مسمومیتِ کش). دامنه خالی ⇒ این قابلیت خاموش می‌ماند.
 *   ⛔ پول: فقط عددِ صحیحِ یک‌صدمِ افغانی؛ «۱۲۰٫۵۰» با تقسیمِ صحیح ساخته می‌شود.
 */
const fs = require('fs');
const path = require('path');
const { one, many } = require('../db');
const config = require('../config');
const orders = require('./orders');
const sf = require('./storefront');
const shelf = require('./storefront-shelf');

const FLAG_KEY = 'shop.storefront.seo';
const LANGS = ['fa', 'en', 'ps'];
const DIR = { fa: 'rtl', en: 'ltr', ps: 'rtl' };
const LOCALE = { fa: 'fa_AF', en: 'en_US', ps: 'ps_AF' };
const MAX_SITEMAP = 5000;
const MAX_DESCRIPTION = 200;

//  متنِ کوتاهِ برچسب‌ها (فقط وقتی دکان توضیحی ننوشته) — سه زبان، مثلِ کیتِ وب
const WORDS = {
  fa: { price: 'قیمت', currency: 'افغانی', in: 'در', shop: 'ویترینِ آنلاین' },
  en: { price: 'Price', currency: 'AFN', in: 'at', shop: 'Online shop' },
  ps: { price: 'بیه', currency: 'افغانۍ', in: 'په', shop: 'آنلاین دوکان' },
};

const TEMPLATE = path.join(__dirname, '..', '..', 'public', 'store', 'index.html');
let template = null;
function shell() {
  if (template === null) template = fs.readFileSync(TEMPLATE, 'utf8');
  return template;
}

const langOf = (v) => (LANGS.includes(String(v || '')) ? String(v) : 'fa');

/** پرچمِ همین دکان (و دامنهٔ پیکربندی‌شده) — وگرنه هیچ. */
async function on(store) {
  if (!config.storefront.domain || !store) return false;
  return orders.flagOf(store.shop_id, FLAG_KEY);
}

/** نشانیِ اصلیِ دکان (D-06) — بی `/` ِ پایانی. */
function originOf(store) {
  return `https://${store.slug}.${config.storefront.domain}`;
}
const pageUrl = (store, productId) => `${originOf(store)}/${productId ? `p/${encodeURIComponent(productId)}` : ''}`;
const withLang = (url, lang) => (lang === 'fa' ? url : `${url}?lang=${lang}`);

/** برای `GET /api/store/:slug` — فقط وقتی روشن است؛ خاموش ⇒ null (کلید نمی‌آید). */
async function publicInfo(store) {
  if (!(await on(store))) return null;
  return { productBase: `${originOf(store)}/p/` };
}

// ── گریزاندن ──────────────────────────────────────────────────────

const ESC = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' };
const esc = (s) => String(s).replace(/[&<>"']/g, (c) => ESC[c]);
/** JSON ِ درونِ `<script type="application/ld+json">` — بی `<`، `>`، `&` و جداکنندهٔ خطِ JS. */
const ld = (o) => JSON.stringify(o).replace(/[<>&\u2028\u2029]/g, (c) => `\\u${c.charCodeAt(0).toString(16).padStart(4, '0')}`);
const oneLine = (s, max) => {
  const t = String(s || '').replace(/\s+/g, ' ').trim();
  return t.length > max ? `${t.slice(0, max - 1).trimEnd()}…` : t;
};

// ── پول ───────────────────────────────────────────────────────────

/** «120.50» از ۱۲۰۵۰ — فقط با عددِ صحیح. */
function amountOf(minor) {
  const m = Number(minor);
  if (!Number.isSafeInteger(m) || m < 0) return null;
  const cents = m % 100;
  return `${(m - cents) / 100}.${String(cents).padStart(2, '0')}`;
}
const FA_DIGITS = '۰۱۲۳۴۵۶۷۸۹';
/** متنِ قیمت برای خواننده: «۱٬۲۰۰ افغانی» / «AFN 1,200» (اعشار فقط اگر هست). */
function priceText(minor, lang) {
  const a = amountOf(minor);
  if (a === null) return '';
  const [int, frac] = a.split('.');
  const grouped = int.replace(/\B(?=(\d{3})+(?!\d))/g, ',');
  const plain = frac === '00' ? grouped : `${grouped}.${frac}`;
  if (lang === 'en') return `AFN ${plain}`;
  const local = plain.replace(/\d/g, (d) => FA_DIGITS[Number(d)]).replace(/,/g, '٬').replace(/\./g, '٫');
  return `${local} ${WORDS[lang].currency}`;
}

// ── کالا ──────────────────────────────────────────────────────────

/** کالای فروختنیِ همین ویترین (همان دروازهٔ کاتالوگ) — یا null. */
async function productOf(store, productId) {
  if (!sf.PRODUCT_ID_RE.test(String(productId || ''))) return null;
  const row = await one(
    `SELECT ${sf.PRODUCT_COLS}, sp.description FROM ${sf.PRODUCT_FROM}
      WHERE sp.shop_id = $1 AND sp.product_id = $2 AND sp.published AND ${sf.LIVE_PRODUCT}`,
    [store.shop_id, String(productId)]
  );
  if (!row) return null;
  const p = sf.productView(row);
  if (p.priceMinor === null || !p.name) return null;
  p.description = oneLine(row.description, 2000);
  if (!p.photo && (await shelf.bankIds(store.shop_id, [p.id])).has(p.id)) p.photo = true;
  await shelf.applyStock(store.shop_id, [p]);
  return p;
}

const AVAILABILITY = { out_of_stock: 'OutOfStock', unavailable: 'OutOfStock' };
/** موجودی فقط اگر واقعی است؛ «در حال بررسی» ⇒ بی ادعا. */
function availabilityOf(p) {
  const s = p.stock && p.stock.status;
  if (!s || s === 'unverified') return null;
  return `https://schema.org/${AVAILABILITY[s] || 'InStock'}`;
}

// ── برچسب‌ها ──────────────────────────────────────────────────────

function alternates(url) {
  return [
    ...LANGS.map((l) => `<link rel="alternate" hreflang="${l}" href="${esc(withLang(url, l))}">`),
    `<link rel="alternate" hreflang="x-default" href="${esc(url)}">`,
  ];
}

function headOf(store, product, lang) {
  const w = WORDS[lang];
  const url = pageUrl(store, product && product.id);
  const canonical = withLang(url, lang);
  const meta = (k, v) => (v ? `<meta property="${k}" content="${esc(v)}">` : '');
  const tags = [];
  if (product) {
    const price = priceText(product.priceMinor, lang);
    const title = `${product.name} — ${store.title}`;
    const description = oneLine(product.description, MAX_DESCRIPTION)
      || oneLine(`${w.price}: ${price}${product.unit ? ` / ${product.unit}` : ''} — ${w.in} ${store.title}`, MAX_DESCRIPTION);
    const image = product.photo ? `${originOf(store)}/api/store/${encodeURIComponent(store.slug)}/products/${encodeURIComponent(product.id)}/photo` : '';
    const offer = {
      '@type': 'Offer', price: amountOf(product.priceMinor), priceCurrency: 'AFN', url: canonical,
      seller: { '@type': 'Organization', name: store.title },
    };
    const availability = availabilityOf(product);
    if (availability) offer.availability = availability;
    const data = { '@context': 'https://schema.org', '@type': 'Product', name: product.name, url: canonical, offers: offer };
    if (product.description) data.description = oneLine(product.description, 1000);
    if (product.category) data.category = product.category;
    if (image) data.image = [image];
    tags.push(
      `<title>${esc(title)}</title>`,
      `<meta name="description" content="${esc(description)}">`,
      `<link rel="canonical" href="${esc(canonical)}">`,
      ...alternates(url),
      meta('og:type', 'product'), meta('og:site_name', store.title), meta('og:title', product.name),
      meta('og:description', description), meta('og:url', canonical), meta('og:locale', LOCALE[lang]),
      ...LANGS.filter((l) => l !== lang).map((l) => meta('og:locale:alternate', LOCALE[l])),
      meta('og:image', image), image ? meta('og:image:alt', product.name) : '',
      meta('product:price:amount', amountOf(product.priceMinor)), meta('product:price:currency', 'AFN'),
      `<meta name="twitter:card" content="${image ? 'summary_large_image' : 'summary'}">`,
      `<script type="application/ld+json">${ld(data)}</script>`,
    );
  } else {
    const description = oneLine(store.tagline, MAX_DESCRIPTION) || `${w.shop} — ${store.title}`;
    const data = { '@context': 'https://schema.org', '@type': 'Store', name: store.title, url: canonical };
    if (store.tagline) data.description = oneLine(store.tagline, 1000);
    tags.push(
      `<title>${esc(store.title)}</title>`,
      `<meta name="description" content="${esc(description)}">`,
      `<link rel="canonical" href="${esc(canonical)}">`,
      ...alternates(url),
      meta('og:type', 'website'), meta('og:site_name', store.title), meta('og:title', store.title),
      meta('og:description', description), meta('og:url', canonical), meta('og:locale', LOCALE[lang]),
      ...LANGS.filter((l) => l !== lang).map((l) => meta('og:locale:alternate', LOCALE[l])),
      `<meta name="twitter:card" content="summary">`,
      `<script type="application/ld+json">${ld(data)}</script>`,
    );
  }
  return tags.filter(Boolean).join('\n');
}

/**
 * پوستهٔ ویترین با برچسب‌های همین صفحه — یا null اگر خاموش است (صدا زننده
 * همان پروندهٔ امروز را می‌فرستد). `productId` ِ نامعتبر یا نافروختنی ⇒ ۴۰۴
 * با برچسب‌های خودِ دکان (JS ِ صفحه «این کالا دیگر در ویترین نیست» را می‌گوید).
 */
async function page(store, { productId = null, lang } = {}) {
  if (!(await on(store))) return null;
  const l = langOf(lang);
  const product = productId === null ? null : await productOf(store, productId);
  const head = headOf(store, product, l);
  let html = shell().replace(/<html lang="fa" dir="rtl">/, () => `<html lang="${l}" dir="${DIR[l]}">`);
  html = /<title>[^<]*<\/title>/.test(html)
    ? html.replace(/<title>[^<]*<\/title>/, () => head)
    : html.replace('</head>', () => `${head}\n</head>`);
  return { status: productId !== null && !product ? 404 : 200, body: html };
}

/** نقشهٔ سایتِ همین دکان — یا null اگر خاموش است. */
async function sitemap(store) {
  if (!(await on(store))) return null;
  const rows = await many(
    `SELECT r.row_id AS id, r.data, sp.online_price_minor, sp.max_qty_milli, false AS has_photo FROM ${sf.PRODUCT_FROM}
      WHERE sp.shop_id = $1 AND sp.published AND ${sf.LIVE_PRODUCT}
      ORDER BY COALESCE(r.data->>'name', ''), r.row_id LIMIT $2`,
    [store.shop_id, MAX_SITEMAP]
  );
  const ids = rows.map(sf.productView).filter((p) => p.priceMinor !== null && p.name).map((p) => p.id);
  const entry = (url) => [
    `<url><loc>${esc(url)}</loc>`,
    ...LANGS.map((l) => `<xhtml:link rel="alternate" hreflang="${l}" href="${esc(withLang(url, l))}"/>`),
    `<xhtml:link rel="alternate" hreflang="x-default" href="${esc(url)}"/></url>`,
  ].join('');
  return [
    '<?xml version="1.0" encoding="UTF-8"?>',
    '<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9" xmlns:xhtml="http://www.w3.org/1999/xhtml">',
    entry(pageUrl(store, null)),
    ...ids.map((id) => entry(pageUrl(store, id))),
    '</urlset>',
    '',
  ].join('\n');
}

/** `robots.txt` ِ میزبانِ دکان — یا null اگر خاموش است. */
async function robots(store) {
  if (!(await on(store))) return null;
  return `User-agent: *\nAllow: /\nSitemap: ${originOf(store)}/sitemap.xml\n`;
}

module.exports = {
  FLAG_KEY, LANGS, MAX_SITEMAP,
  on, publicInfo, page, sitemap, robots,
  //  برای آزمون
  amountOf, priceText, headOf, esc, ld,
};
