'use strict';
/**
 * دکانِ آزمونیِ `storefront-filters.test.js` (WEB-07) و `storefront-alerts.test.js` (WEB-10): ویترینِ روشن،
 * کالاهای همگام‌شده با برند، مشخصهٔ عمومیِ ویترین و (با `catalog`) موجودیِ کاتالوگ.
 */
process.env.RATE_STORE_CHECKOUT_MAX = process.env.RATE_STORE_CHECKOUT_MAX || '1000';
process.env.RATE_LOGIN_EDGE_MAX = process.env.RATE_LOGIN_EDGE_MAX || '100000';

const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');

let admin = '';
let seq = 0;
const uniq = () => `${Date.now().toString(36)}${++seq}`;

function boot() {
  test.before(async () => {
    await h.start();
    const pw = require('../src/lib/password');
    await query(
      `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
       VALUES ($1,'admin','مدیر',$2,'superadmin','active',$3)`,
      [newId('adm'), await pw.hashPassword('Admin!12345'), now()]
    );
    admin = (await h.post('/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
    assert.ok(admin, 'ورودِ مدیر');
  });
  test.after(async () => { await h.stop(); });
  test.beforeEach(() => resetAll());
}

async function flag(scope, key, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}

let opN = 0;
const opId = () => `01JWF${String(Date.now()).slice(-8)}${String(++opN).padStart(8, '0')}`;
const op = (id, fields) => ({
  op_id: opId(), ts: Date.now(), table: 'products', row_id: id, type: 'insert',
  fields: { unit: 'دانه', purchasePrice: 1, active: true, barcodes: [], createdAt: Date.now(), ...fields },
});

/** کالاها: نام، قیمت (افغانی)، دسته، برند؛ «مشخصه» فقط برای ویترین (WEB-08) */
const PRODUCTS = [
  { id: 'tea-a', name: 'چای الف', salePrice: 120, category: 'نوشیدنی', brand: 'Ahmad', specs: [{ name: 'وزن', value: '۵۰۰ گرم' }] },
  { id: 'tea-b', name: 'چای ب', salePrice: 80, category: 'نوشیدنی', brand: 'ahmad ', specs: [{ name: 'وزن', value: '۲۵۰ گرم' }] },
  { id: 'tea-c', name: 'چای پ', salePrice: 300, category: 'نوشیدنی', brand: 'Lipton', specs: [{ name: 'وزن', value: '۵۰۰ گرم' }] },
  { id: 'oil-a', name: 'روغن الف', salePrice: 450, category: 'خوراکی', brand: 'Lipton', specs: [] },
  { id: 'soap-a', name: 'صابون الف', salePrice: 40, category: 'بهداشتی', specs: [{ name: 'رنگ', value: 'سبز' }] },
];

/** دکانِ روشن با ویترین؛ `catalog` ⇒ پرچمِ `shop.catalog` هم (موجودیِ واقعی). */
async function liveStore(label, { catalog = false } = {}) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const s = { token: u.accessToken, shopId: made.body.shop.id, device: `dev-f-${uniq()}` };
  for (const key of ['shop.orders', 'shop.storefront', ...(catalog ? ['shop.catalog'] : [])]) await flag(s.shopId, key, true);
  s.slug = `fx-${uniq()}`;
  const put = await h.put('/api/shop/storefront', {
    slug: s.slug, enabled: true, title: `ویترینِ ${label}`, contactPhone: '0700000001', acceptPickup: true, minOrderMinor: 0,
  }, { token: s.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  const pushed = await h.api('POST', '/api/sync/v1/push', {
    token: s.token, headers: { 'X-App': 'shop', 'X-Device': s.device },
    body: { device_id: s.device, schema_version: 2, ops: PRODUCTS.map(({ id, specs, ...f }) => op(id, f)) },
  });
  assert.equal(pushed.status, 200, JSON.stringify(pushed.body));
  for (const p of PRODUCTS) {
    const r = await h.put(`/api/shop/storefront/products/${p.id}`, { published: true, specs: p.specs }, { token: s.token });
    assert.equal(r.status, 200, JSON.stringify(r.body));
  }
  return s;
}

/** مانده در قفسه (`adjust` ِ صاحب دکان، با دلیل) */
async function setStock(s, productId, qtyMilli) {
  const r = await h.post('/api/shop/catalog/moves', {
    kind: 'adjust', productId, location: 'shop_floor', qtyMilli, reason: 'شمارشِ آزمون',
  }, { token: s.token, headers: { 'Idempotency-Key': `fx-mv-${uniq()}` } });
  assert.equal(r.status, 201, JSON.stringify(r.body));
}

const setPrice = (s, productId, onlinePriceMinor) => h.put(`/api/shop/storefront/products/${productId}`,
  { published: true, onlinePriceMinor }, { token: s.token });

async function ticketFor(slug, phone) {
  const st = await h.post(`/api/store/${slug}/verify/start`, { phone });
  assert.equal(st.status, 200, JSON.stringify(st.body));
  const c = await h.post(`/api/store/${slug}/verify/check`, { phone, code: st.body.devCode });
  assert.equal(c.status, 200, JSON.stringify(c.body));
  return { headers: { 'X-Store-Ticket': c.body.ticket } };
}

module.exports = { h, boot, flag, liveStore, setStock, setPrice, ticketFor, uniq, PRODUCTS };
