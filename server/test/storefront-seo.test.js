'use strict';
/**
 * ویترین — پیش‌نمایشِ پیوندِ کالا (WEB-20) و سئو (WEB-17) پشتِ پرچمِ خاموشِ
 * `shop.storefront.seo` (`lib/store-seo.js`).
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) ⛔ خاموش ⇒ همان رفتارِ امروز: `/store/<slug>` همان پروندهٔ ایستا (بایت‌به‌بایت)،
 *      `GET /api/store/:slug` بی کلیدِ `seo`، نشانی‌های تازه (صفحهٔ کالا، نقشهٔ سایت،
 *      robots) همان ۴۰۴.
 *   ۲) روشن ⇒ صفحهٔ کالا با Open Graph (نام، توضیح، عکسِ مطلق، قیمتِ «120.50 AFN»)،
 *      canonical و hreflang ِ سه زبان، و JSON-LD ِ Product/Offer.
 *   ۳) ⛔ متنِ دکان گریزانده می‌شود: نامی با `"><script>` یا `$&` نه HTML می‌شود نه الگو؛
 *      JSON-LD هیچ `<` ندارد.
 *   ۴) فقط کالای فروختنیِ ویترین: منتشرنشده یا ناموجود ⇒ ۴۰۴ با برچسب‌های خودِ دکان.
 *   ۵) نقشهٔ سایت فقط کالاهای فروختنی را با سه زبان دارد؛ robots ِ میزبانِ دکان به آن اشاره می‌کند.
 *   ۶) نشانیِ مطلق از دامنهٔ پیکربندی است، نه از سرآیندِ Host.
 *   ۷) دکمهٔ «فرستادنِ نشانی» در `store/share.js` ِ دیربار است، نه در بارِ نخست (WEB-15).
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const http = require('node:http');
const h = require('./helpers');
const { query, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');
const seo = require('../src/lib/store-seo');

const SHELL = fs.readFileSync(path.join(__dirname, '..', 'public', 'store', 'index.html'), 'utf8');
let admin = '';
let seq = 0;
const uniq = () => `${Date.now().toString(36)}${++seq}`;

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]
  );
  admin = (await h.post('/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
  assert.ok(admin);
});
test.after(async () => { await h.stop(); });
test.beforeEach(() => resetAll());

async function setFlag(scope, key, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}

const push = (token, ops, device = 'dev-seo') => h.api('POST', '/api/sync/v1/push', {
  token, headers: { 'X-App': 'shop', 'X-Device': device },
  body: { device_id: device, schema_version: 2, ops },
});
const op = (rowId, fields) => ({ op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: rowId, type: 'insert', fields });

const EVIL = 'برنج "><script>alert(1)</script> $& $1';

/** دکانِ روشن با ویترین؛ برنج و روغن منتشرشده، صابون نه. */
async function liveStore(label) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const o = { token: u.accessToken, shopId: made.body.shop.id };
  await setFlag(o.shopId, 'shop.orders', true);
  await setFlag(o.shopId, 'shop.storefront', true);
  const slug = `seo-${uniq()}`;
  const put = await h.put('/api/shop/storefront', {
    slug, enabled: true, title: `ویترینِ ${label}`, tagline: 'تازه هر روز', contactPhone: '0700000001',
  }, { token: o.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  const p = await push(o.token, [
    op('rice', { name: EVIL, unit: 'کیلو', salePrice: 120.5, category: 'خوراکی' }),
    op('oil', { name: 'روغن', unit: 'بوتل', salePrice: 1200, category: 'خوراکی' }),
    op('soap', { name: 'صابون', unit: 'دانه', salePrice: 40, category: 'بهداشتی' }),
  ]);
  assert.equal(p.status, 200, JSON.stringify(p.body));
  const pub = await h.put('/api/shop/storefront/products/rice', {
    published: true, description: 'برنجِ دانه‌بلندِ <b>پیشاور</b>',
  }, { token: o.token });
  assert.equal(pub.status, 200, JSON.stringify(pub.body));
  assert.equal((await h.put('/api/shop/storefront/products/oil', { published: true }, { token: o.token })).status, 200);
  return { ...o, slug };
}

function rawGet(p, host) {
  const u = new URL(h.base());
  return new Promise((resolve, reject) => {
    http.get({ hostname: u.hostname, port: u.port, path: p, headers: host ? { Host: host } : {} }, (res) => {
      let body = '';
      res.setEncoding('utf8');
      res.on('data', (c) => { body += c; });
      res.on('end', () => resolve({ status: res.statusCode, headers: res.headers, body }));
    }).on('error', reject);
  });
}

const metaOf = (html, prop) => {
  const m = new RegExp(`<meta property="${prop}" content="([^"]*)">`).exec(html);
  return m ? m[1] : null;
};
const ldOf = (html) => {
  const m = /<script type="application\/ld\+json">([^<]*)<\/script>/.exec(html);
  return m ? JSON.parse(m[1]) : null;
};

test('خاموش: همان پروندهٔ ایستا، بی کلیدِ seo، نشانی‌های تازه ۴۰۴', async () => {
  const s = await liveStore('خاموش');
  const page = await rawGet(`/store/${s.slug}`);
  assert.equal(page.status, 200);
  assert.equal(page.body, SHELL, 'بایت‌به‌بایت همان index.html');
  const host = await rawGet('/', `${s.slug}.vill3n.top`);
  assert.equal(host.body, SHELL);
  const api = await h.get(`/api/store/${s.slug}`);
  assert.equal(api.status, 200);
  assert.equal('seo' in api.body, false, 'خاموش ⇒ کلیدِ تازه‌ای نیست');
  //  هر نشانیِ تازه درست همان پاسخِ یک نشانیِ ناشناختهٔ هم‌شکل را می‌گیرد (۴۰۴ ِ امروز)
  for (const [p, twin, hostName] of [
    [`/store/${s.slug}/p/rice`, `/store/${s.slug}/q/rice`], [`/store/${s.slug}/sitemap.xml`, `/store/${s.slug}/sitemap.xmz`],
    ['/p/rice', '/q/rice', `${s.slug}.vill3n.top`], ['/sitemap.xml', '/sitemap.xmz', `${s.slug}.vill3n.top`],
    ['/robots.txt', '/robots.txz', `${s.slug}.vill3n.top`],
  ]) {
    const [r, w] = [await rawGet(p, hostName), await rawGet(twin, hostName)];
    assert.equal(r.status, 404, `${hostName || ''}${p}`);
    assert.equal(r.headers['content-security-policy'], w.headers['content-security-policy'], `${p}: سرآیندِ همان ۴۰۴`);
    assert.equal(r.headers['content-type'], w.headers['content-type'], p);
    assert.equal(r.body.replace(p, '·'), w.body.replace(twin, '·'), `${p}: بدنهٔ همان ۴۰۴`);
  }
  //  پرچمِ سئو بی ویترینِ روشن هیچ چیزی را باز نمی‌کند
  await setFlag(s.shopId, 'shop.storefront.seo', true);
  await setFlag(s.shopId, 'shop.storefront', false);
  assert.equal((await rawGet(`/store/${s.slug}/p/rice`)).status, 404);
  assert.equal((await rawGet(`/store/${s.slug}/sitemap.xml`)).status, 404);
});

test('روشن: صفحهٔ کالا با Open Graph، hreflang و JSON-LD؛ متنِ دکان گریزانده', async () => {
  const s = await liveStore('روشن');
  await setFlag(s.shopId, 'shop.storefront.seo', true);
  const origin = `https://${s.slug}.vill3n.top`;
  const r = await rawGet(`/store/${s.slug}/p/rice`);
  assert.equal(r.status, 200);
  assert.match(r.headers['content-type'], /text\/html/);
  assert.match(r.headers['content-security-policy'] || '', /script-src 'self';/, 'همان CSP ِ سختِ ویترین');
  assert.doesNotMatch(r.headers['content-security-policy'] || '', /unsafe-inline/);
  const html = r.body;
  //  بقیهٔ پوسته همان است
  assert.ok(html.includes('<script src="/store/store.js" defer></script>'));
  assert.ok(html.includes('<div id="product-sheet"'));
  assert.equal(metaOf(html, 'og:type'), 'product');
  assert.equal(metaOf(html, 'og:url'), `${origin}/p/rice`);
  assert.equal(metaOf(html, 'og:title'), 'برنج &quot;&gt;&lt;script&gt;alert(1)&lt;/script&gt; $&amp; $1');
  assert.equal(metaOf(html, 'og:description'), 'برنجِ دانه‌بلندِ &lt;b&gt;پیشاور&lt;/b&gt;');
  assert.equal(metaOf(html, 'og:site_name'), 'ویترینِ روشن');
  assert.equal(metaOf(html, 'og:locale'), 'fa_AF');
  assert.equal(metaOf(html, 'product:price:amount'), '120.50');
  assert.equal(metaOf(html, 'product:price:currency'), 'AFN');
  assert.equal(metaOf(html, 'og:image'), null, 'بی عکس ⇒ بی og:image ِ ساختگی');
  assert.match(html, /<meta name="twitter:card" content="summary">/);
  assert.match(html, new RegExp(`<link rel="canonical" href="${origin}/p/rice">`));
  for (const [l, href] of [['fa', `${origin}/p/rice`], ['en', `${origin}/p/rice?lang=en`], ['ps', `${origin}/p/rice?lang=ps`], ['x-default', `${origin}/p/rice`]]) {
    assert.ok(html.includes(`<link rel="alternate" hreflang="${l}" href="${href}">`), l);
  }
  //  ⛔ نامِ بد نه تگ شد، نه الگوی جای‌گذاری
  assert.doesNotMatch(html, /<script>alert/);
  assert.equal((html.match(/<title>/g) || []).length, 1, 'یک <title>');
  assert.ok(html.includes('<title>برنج &quot;&gt;&lt;script&gt;alert(1)&lt;/script&gt; $&amp; $1 — ویترینِ روشن</title>'));
  const data = ldOf(html);
  assert.ok(data, 'JSON-LD هست و بی `<` (بیرون‌پریدن از script ممکن نیست)');
  assert.equal(data['@type'], 'Product');
  assert.equal(data.name, EVIL);
  assert.equal(data.category, 'خوراکی');
  assert.equal(data.offers.price, '120.50');
  assert.equal(data.offers.priceCurrency, 'AFN');
  assert.equal(data.offers.availability, undefined, 'موجودیِ «در حال بررسی» ادعای «موجود» نمی‌شود');
  assert.equal(data.offers.url, `${origin}/p/rice`);
  //  میزبانِ دکان همان صفحه
  const onHost = await rawGet('/p/rice', `${s.slug}.vill3n.top`);
  assert.equal(onHost.status, 200);
  assert.equal(onHost.body, html);
  //  API ِ ویترین پایهٔ پیوند را می‌گوید
  const api = await h.get(`/api/store/${s.slug}`);
  assert.deepEqual(api.body.seo, { productBase: `${origin}/p/` });
  //  WEB-15: «فرستادنِ نشانی» (share.js) از همان مبدأ و جدا از بارِ نخست
  const js = await fetch(`${h.base()}/store/share.js`);
  assert.equal(js.status, 200);
  assert.match(js.headers.get('content-type') || '', /javascript/);
  assert.doesNotMatch(SHELL, /share\.js/, 'share.js در بارِ نخست نیست');
});

test('روشن: زبان، عکس، کالای منتشرنشده و صفحهٔ دکان', async () => {
  const s = await liveStore('زبان');
  await setFlag(s.shopId, 'shop.storefront.seo', true);
  const origin = `https://${s.slug}.vill3n.top`;
  const en = await rawGet(`/store/${s.slug}/p/oil?lang=en`);
  assert.equal(en.status, 200);
  assert.match(en.body, /<html lang="en" dir="ltr">/);
  assert.equal(metaOf(en.body, 'og:locale'), 'en_US');
  assert.equal(metaOf(en.body, 'og:url'), `${origin}/p/oil?lang=en`);
  assert.equal(metaOf(en.body, 'og:description'), `Price: AFN 1,200 / بوتل — at ویترینِ زبان`);
  assert.equal(metaOf(en.body, 'product:price:amount'), '1200.00');
  const fa = await rawGet(`/store/${s.slug}/p/oil?lang=xx`);
  assert.match(fa.body, /<html lang="fa" dir="rtl">/, 'زبانِ ناشناخته ⇒ fa');
  assert.equal(metaOf(fa.body, 'og:description'), 'قیمت: ۱٬۲۰۰ افغانی / بوتل — در ویترینِ زبان');
  //  عکس ⇒ نشانیِ مطلقِ عکسِ همان ویترین
  await query(
    `INSERT INTO product_photos (app, account_id, product_id, data, hash, size, rev, updated_at)
     VALUES ('shop', $1, 'oil', $2, 'h-oil', 4, 1, $3)`,
    [s.shopId, Buffer.from([0x89, 0x50, 0x4e, 0x47]), now()]
  );
  const withPhoto = await rawGet(`/store/${s.slug}/p/oil`);
  const img = metaOf(withPhoto.body, 'og:image');
  assert.equal(img, `${origin}/api/store/${s.slug}/products/oil/photo`);
  assert.equal(metaOf(withPhoto.body, 'og:image:alt'), 'روغن');
  assert.match(withPhoto.body, /<meta name="twitter:card" content="summary_large_image">/);
  assert.deepEqual(ldOf(withPhoto.body).image, [img]);
  //  منتشرنشده، ناموجود و شناسهٔ بد ⇒ ۴۰۴ با برچسبِ دکان (نه کالا)
  for (const id of ['soap', 'nothing', 'bad%20id']) {
    const r = await rawGet(`/store/${s.slug}/p/${id}`);
    assert.equal(r.status, 404, id);
    assert.equal(metaOf(r.body, 'og:type'), 'website', id);
    assert.doesNotMatch(r.body, /صابون/, id);
  }
  //  صفحهٔ خودِ دکان
  const home = await rawGet(`/store/${s.slug}`);
  assert.equal(home.status, 200);
  assert.equal(metaOf(home.body, 'og:type'), 'website');
  assert.equal(metaOf(home.body, 'og:url'), `${origin}/`);
  assert.equal(metaOf(home.body, 'og:description'), 'تازه هر روز');
  assert.match(home.body, /<title>ویترینِ زبان<\/title>/);
  assert.equal(ldOf(home.body)['@type'], 'Store');
  //  Host ِ دیگر نشانیِ مطلق را عوض نمی‌کند
  const spoof = await rawGet(`/store/${s.slug}/p/oil`, 'evil.example');
  assert.equal(metaOf(spoof.body, 'og:url'), `${origin}/p/oil`);
});

test('نقشهٔ سایت و robots فقط کالاهای فروختنی را با سه زبان دارند', async () => {
  const s = await liveStore('نقشه');
  await setFlag(s.shopId, 'shop.storefront.seo', true);
  const origin = `https://${s.slug}.vill3n.top`;
  //  منتشرشده ولی بی‌قیمت ⇒ فروختنی نیست: نه صفحه، نه نقشه
  assert.equal((await push(s.token, [op('free', { name: 'رایگان', unit: 'دانه', salePrice: 0 })])).status, 200);
  assert.equal((await h.put('/api/shop/storefront/products/free', { published: true }, { token: s.token })).status, 200);
  assert.equal((await rawGet(`/store/${s.slug}/p/free`)).status, 404);
  const r = await rawGet(`/store/${s.slug}/sitemap.xml`);
  assert.equal(r.status, 200);
  assert.match(r.headers['content-type'], /application\/xml/);
  const xml = r.body;
  assert.match(xml, /^<\?xml version="1.0" encoding="UTF-8"\?>\n<urlset xmlns="http:\/\/www.sitemaps.org\/schemas\/sitemap\/0.9" xmlns:xhtml="http:\/\/www.w3.org\/1999\/xhtml">/);
  const locs = [...xml.matchAll(/<loc>([^<]+)<\/loc>/g)].map((m) => m[1]);
  assert.deepEqual(locs.sort(), [`${origin}/`, `${origin}/p/oil`, `${origin}/p/rice`].sort());
  assert.ok(xml.includes(`<xhtml:link rel="alternate" hreflang="ps" href="${origin}/p/oil?lang=ps"/>`));
  assert.ok(xml.includes(`<xhtml:link rel="alternate" hreflang="x-default" href="${origin}/p/rice"/>`));
  assert.doesNotMatch(xml, /soap|free/, 'منتشرنشده و بی‌قیمت در نقشه نیستند');
  const host = await rawGet('/sitemap.xml', `${s.slug}.vill3n.top`);
  assert.equal(host.body, xml);
  const robots = await rawGet('/robots.txt', `${s.slug}.vill3n.top`);
  assert.equal(robots.status, 200);
  assert.equal(robots.body, `User-agent: *\nAllow: /\nSitemap: ${origin}/sitemap.xml\n`);
  //  میزبانِ پنل همان ۴۰۴ ِ امروز
  assert.equal((await rawGet('/robots.txt', 'api.vill3n.top')).status, 404);
  assert.equal((await rawGet('/sitemap.xml')).status, 404);
});

test('پول و گریز: تقسیمِ صحیح، رقمِ فارسی، JSON-LD بی `<`', () => {
  assert.equal(seo.amountOf(12050), '120.50');
  assert.equal(seo.amountOf(5), '0.05');
  assert.equal(seo.amountOf(0), '0.00');
  assert.equal(seo.amountOf(100_000_000_000), '1000000000.00');
  assert.equal(seo.amountOf(-1), null);
  assert.equal(seo.amountOf(1.5), null);
  assert.equal(seo.priceText(123456789, 'fa'), '۱٬۲۳۴٬۵۶۷٫۸۹ افغانی');
  assert.equal(seo.priceText(120000, 'en'), 'AFN 1,200');
  assert.equal(seo.priceText(120000, 'ps'), '۱٬۲۰۰ افغانۍ');
  const out = seo.ld({ name: '</script><script>x()</script>&\u2028' });
  assert.doesNotMatch(out, /[<>&\u2028]/);
  assert.equal(JSON.parse(out).name, '</script><script>x()</script>&\u2028');
  assert.equal(seo.esc(`"'<>&`), '&quot;&#39;&lt;&gt;&amp;');
});
