'use strict';
/**
 * PRD-17 / PRD-27 — عکس‌های تکمیلیِ کالا و عکسِ جدای ویترین روی بانکِ تصاویرِ دکان (پرچمِ `shop.image_bank`).
 *
 * قراردادِ موجود (برنامه همین را می‌زند): `POST /api/shop/images?product_id=&role=` (نقشِ back/label/variant/detail
 * برای تکمیلی، promo برای ویترین)، نسخهٔ کوچک با `PUT /:id/v/:v/:size`، فهرست با `GET /mine?product_id=`، و
 * `POST /select {source:'shop'}` برای عکسِ ویترین. درِ تازه فقط `POST /:id/detach`: عکسِ تکمیلی از کالا برداشته
 * می‌شود ولی اصل و نسخه‌ها می‌مانند (۳۷: اصل هرگز پاک نمی‌شود)؛ عکسی که همین حالا عکسِ کالاست ۴۰۹ `image_selected`؛
 * دکانِ دیگر ۴۰۴؛ پرچم خاموش ۴۰۳؛ تکرار بی اثر.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const F = require('./image-fixtures');
const { query, one } = require('../src/db');

test.before(async () => { await h.start(); });
test.after(async () => { await h.stop(); });

let seed = 7000;
const pic = (w = 800, hh = 600) => F.jpeg(w, hh, { seed: ++seed });

async function shopOwner(name, { flag = true } = {}) {
  const u = await h.newUser(name, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${name}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const shopId = made.body.shop.id;
  if (flag) {
    await query(
      `INSERT INTO live_config (scope, key, value, updated_at) VALUES ($1,'shop.image_bank','true',1)
       ON CONFLICT (scope, key) DO UPDATE SET value=excluded.value`, [shopId]);
  }
  return { token: u.accessToken, shopId };
}

const up = (who, q, buf = pic()) => h.raw('POST', `/api/shop/images?${q}`, buf, { token: who.token });
const mine = (who, pid) => h.get(`/api/shop/images/mine?product_id=${pid}`, { token: who.token });
const detach = (who, id) => h.post(`/api/shop/images/${id}/detach`, {}, { token: who.token });

test('تکمیلی با نقش در فهرستِ همان کالا؛ برداشتن از کالا اصل را نگه می‌دارد و تکرارش بی اثر است', async () => {
  const A = await shopOwner('تکمیلی');
  const back = await up(A, 'product_id=p1&role=back');
  assert.equal(back.status, 201, JSON.stringify(back.body));
  const detail = await up(A, 'product_id=p1&role=detail');
  assert.equal(detail.status, 201);
  const t = await h.raw('PUT', `/api/shop/images/${detail.body.id}/v/1/thumb`, F.jpeg(400, 300, { seed: ++seed }), { token: A.token });
  assert.equal(t.status, 200, JSON.stringify(t.body));
  const before = await mine(A, 'p1');
  assert.deepEqual(before.body.items.map((i) => [i.id, i.role]).sort(), [[back.body.id, 'back'], [detail.body.id, 'detail']].sort());
  assert.ok(before.body.items.find((i) => i.id === detail.body.id).urls.thumb, 'نشانیِ نسخهٔ کوچک');

  const d = await detach(A, detail.body.id);
  assert.equal(d.status, 200, JSON.stringify(d.body));
  assert.deepEqual(d.body, { image_id: detail.body.id, product_id: 'p1', detached: true });
  assert.deepEqual((await mine(A, 'p1')).body.items.map((i) => i.id), [back.body.id]);
  //  اصل و نسخهٔ کوچک سرِ جایشان؛ صاحب هنوز می‌خواند
  const row = await one('SELECT shop_product_id, current_version FROM images WHERE id=$1', [detail.body.id]);
  assert.deepEqual(row, { shop_product_id: '', current_version: 1 });
  assert.equal((await h.raw('GET', `/api/shop/images/file/${detail.body.id}/1/original`, null, { token: A.token })).status, 200);
  const again = await detach(A, detail.body.id);
  assert.equal(again.status, 200);
  assert.deepEqual(again.body, { image_id: detail.body.id, product_id: '', detached: false });
});

test('عکسِ ویترین (promo) که انتخاب شده برداشته نمی‌شود: ۴۰۹ image_selected', async () => {
  const A = await shopOwner('ویترین');
  const promo = await up(A, 'product_id=p2&role=promo');
  assert.equal(promo.status, 201);
  const sel = await h.post('/api/shop/images/select', { product_id: 'p2', source: 'shop', image_id: promo.body.id, version: 1 }, { token: A.token });
  assert.equal(sel.status, 200, JSON.stringify(sel.body));
  const shown = await h.get('/api/shop/images/products/p2', { token: A.token });
  assert.equal(shown.body.source, 'shop');
  assert.equal(shown.body.image_id, promo.body.id);
  const d = await detach(A, promo.body.id);
  assert.equal(d.status, 409, JSON.stringify(d.body));
  assert.equal(d.body.error.code, 'image_selected');
  assert.equal((await one('SELECT shop_product_id FROM images WHERE id=$1', [promo.body.id])).shop_product_id, 'p2');
});

test('دکانِ دیگر ۴۰۴ و هیچ تغییری؛ پرچم خاموش ۴۰۳؛ بی توکن ۴۰۱', async () => {
  const A = await shopOwner('الف');
  const B = await shopOwner('ب');
  const off = await shopOwner('خاموش', { flag: false });
  const x = await up(A, 'product_id=p3&role=label');
  assert.equal(x.status, 201);
  const other = await detach(B, x.body.id);
  assert.equal(other.status, 404, JSON.stringify(other.body));
  assert.equal((await one('SELECT shop_product_id FROM images WHERE id=$1', [x.body.id])).shop_product_id, 'p3');
  const o = await detach(off, x.body.id);
  assert.equal(o.status, 403);
  assert.equal(o.body.error.code, 'image_bank_disabled');
  assert.equal((await h.post(`/api/shop/images/${x.body.id}/detach`, {})).status, 401);
});
