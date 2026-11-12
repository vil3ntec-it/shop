'use strict';
/**
 * CHK-08، تصمیمِ ۴-۳ «الف» — نقشهٔ پینِ نشانی در ثبتِ ویترین (`lib/store-map.js`).
 *
 *   ۱) الگوی `STORE_MAP_TILES`: فقط https ِ میزبانِ ساده یا مسیرِ همین مبدأ، با هر سه جای‌نگهدار و بی نویسه‌ای که CSP یا HTML را
 *      بشکند؛ مبدأ ِ `img-src` فقط از الگوی درست.
 *   ۲) پیش‌فرض بسته از دو سو: بی پرچمِ دکان یا بی الگوی سرور ⇒ `features.mapPin` نیست؛ هر دو ⇒ الگو و «©» ِ OSM.
 *   ۳) CSP ِ ویترین: همان یک مبدأ در `img-src`، نه در `script-src`/`connect-src`.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const config = require('../src/config');
const TILES = 'https://tiles.example.org/{z}/{x}/{y}.png';
config.storefront.mapTiles = TILES;                     //  پیش از ساختِ برنامه (h.start): CSP یک بار ساخته می‌شود
const M = require('../src/lib/store-map');
const { query, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');

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
test.after(async () => { config.storefront.mapTiles = ''; await h.stop(); });
test.beforeEach(() => resetAll());

async function setFlag(scope, key, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}
async function liveStore(label) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const o = { shopId: made.body.shop.id, slug: `map-${uniq()}` };
  for (const k of ['shop.orders', 'shop.storefront']) await setFlag(o.shopId, k, true);
  //  پین پیش‌فرض‌روشن است (`lib/flag-defaults.js`، ۱۴۰۵/۰۸/۲۲) ⇒ «پرچمِ خاموش» ِ این پرونده `false` ِ صریح است
  await setFlag(o.shopId, M.FLAG, false);
  const put = await h.put('/api/shop/storefront', { slug: o.slug, enabled: true, title: `ویترینِ ${label}`, deliveryFeeMinor: 0, minOrderMinor: 0 },
    { token: u.accessToken });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  return o;
}
const features = async (slug) => {
  const r = await h.get(`/api/store/${slug}`);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  return r.body.features || {};
};

test('الگوی تکه‌ها: فقط https یا مسیرِ همین مبدأ، با {z}/{x}/{y}، بی نویسهٔ شکننده', () => {
  for (const ok of [TILES, 'https://a.tile.example.org:8443/osm/{z}/{x}/{y}.png', '/tiles/{z}/{x}/{y}.png']) {
    assert.equal(M.valid(ok), true, ok);
  }
  for (const bad of ['', 'http://tiles.example.org/{z}/{x}/{y}.png', 'https://tiles.example.org/{x}/{y}.png',
    "https://evil.org/{z}/{x}/{y}.png'; script-src *", 'https://a b.org/{z}/{x}/{y}.png', '//tiles.example.org/{z}/{x}/{y}.png',
    'javascript:{z}{x}{y}', 'https://tiles.example.org/{z}/{x}/{y}.png"><script>', `https://x.org/${'a'.repeat(300)}/{z}/{x}/{y}`, null, 42]) {
    assert.equal(M.valid(bad), false, String(bad));
  }
  assert.equal(M.origin(TILES), 'https://tiles.example.org');
  assert.equal(M.origin('https://A.Tile.Example.org:8443/{z}/{x}/{y}.png'), 'https://a.tile.example.org:8443');
  assert.equal(M.origin('/tiles/{z}/{x}/{y}.png'), '', 'همین مبدأ ⇒ همان \'self\'');
  assert.equal(M.origin(''), '');
});

test('پیش‌فرض بسته: بی پرچمِ دکان یا بی الگوی سرور ⇒ بی mapPin؛ هر دو ⇒ الگو و «©»', async () => {
  const A = await liveStore('نقشه');
  assert.equal((await features(A.slug)).mapPin, undefined, 'پرچم خاموش');
  await setFlag(A.shopId, M.FLAG, true);
  assert.deepEqual((await features(A.slug)).mapPin, { tiles: TILES, attribution: 'OpenStreetMap', copyright: 'https://www.openstreetmap.org/copyright' });
  config.storefront.mapTiles = 'http://insecure.example.org/{z}/{x}/{y}.png';
  try {
    assert.equal((await features(A.slug)).mapPin, undefined, 'الگوی نادرست ⇒ هیچ');
    config.storefront.mapTiles = '';
    assert.equal((await features(A.slug)).mapPin, undefined, 'بی الگو ⇒ هیچ');
  } finally { config.storefront.mapTiles = TILES; }
  const B = await liveStore('بی‌نقشه');
  assert.equal((await features(B.slug)).mapPin, undefined, 'دکانِ دیگر با پرچمِ خاموش');
});

test('CSP ِ ویترین: مبدأِ تکه‌ها فقط در img-src', async () => {
  const A = await liveStore('سی‌اس‌پی');
  const r = await fetch(`${h.base()}/store/${A.slug}`);
  const csp = r.headers.get('content-security-policy') || '';
  assert.ok(csp, 'سرآیندِ CSP');
  const dir = Object.fromEntries(csp.split(';').map((d) => d.trim().split(/\s+/)).map(([k, ...v]) => [k, v]));
  assert.deepEqual(dir['img-src'], ["'self'", 'data:', 'https://tiles.example.org']);
  for (const k of ['script-src', 'connect-src', 'default-src', 'style-src']) assert.deepEqual(dir[k], ["'self'"], k);
});
