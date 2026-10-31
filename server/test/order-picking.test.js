'use strict';
/**
 * جمع‌آوری و بسته‌بندی (PCK، v3 §۶.۱۷) — پرچمِ خاموشِ `shop.orders.picking`.
 *
 *   PCK-11 (گامِ ۱): برنامهٔ مدیریت QRِ کدِ تحویل/دریافت را فقط وقتی نشان می‌دهد که
 *   `picking` ِ `/config` روشن باشد. این‌جا قفل می‌شود که:
 *     ۱) پیش‌فرض خاموش است (هر دو `/config`)
 *     ۲) مقدارِ `all` روشن می‌کند و مقدارِ خودِ دکان بر `all` جلو می‌افتد
 *     ۳) فقط `true` روشن است (رشتهٔ "true" یا ۱ نه)
 *     ۴) پرچمِ یک دکان دکانِ دیگر را روشن نمی‌کند
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, newId, now } = require('../src/db');

let admin = '';

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

async function shopOwner(name) {
  const u = await h.newUser(name, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${name}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  return { ...u, token: u.accessToken, shopId: made.body.shop.id };
}

async function setFlag(scope, key, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}

async function pickingOf(owner) {
  const o = await h.get('/api/shop/orders/config', { token: owner.token });
  assert.equal(o.status, 200, JSON.stringify(o.body));
  const d = await h.get('/api/shop/delivery/config', { token: owner.token });
  assert.equal(d.status, 200, JSON.stringify(d.body));
  assert.equal(typeof o.body.picking, 'boolean', 'orders/config.picking بولی است');
  assert.equal(o.body.picking, d.body.picking, 'دو /config یک جواب دارند');
  return o.body.picking;
}

test('PCK-11: پرچمِ جمع‌آوری پیش‌فرض خاموش است؛ all روشن می‌کند و مقدارِ خودِ دکان جلو می‌افتد', async () => {
  const a = await shopOwner('جمع‌آوری-الف');
  const b = await shopOwner('جمع‌آوری-ب');
  assert.equal(await pickingOf(a), false, 'بی پرچم خاموش');

  await setFlag(a.shopId, 'shop.orders.picking', true);
  assert.equal(await pickingOf(a), true, 'پرچمِ خودِ دکان');
  assert.equal(await pickingOf(b), false, 'پرچمِ «الف» دکانِ «ب» را روشن نمی‌کند');

  await setFlag('all', 'shop.orders.picking', true);
  assert.equal(await pickingOf(b), true, 'all روشن ⇒ همه');
  await setFlag(b.shopId, 'shop.orders.picking', false);
  assert.equal(await pickingOf(b), false, 'مقدارِ خودِ دکان بر all جلو می‌افتد');
  await setFlag('all', 'shop.orders.picking', false);
});

test('PCK-11: فقط true روشن است — رشته، عدد و null خاموش‌اند', async () => {
  const a = await shopOwner('جمع‌آوری-نوع');
  for (const v of ['true', 1, 'on', null]) {
    await setFlag(a.shopId, 'shop.orders.picking', v);
    assert.equal(await pickingOf(a), false, `مقدارِ ${JSON.stringify(v)} نباید روشن کند`);
  }
  await setFlag(a.shopId, 'shop.orders.picking', true);
  assert.equal(await pickingOf(a), true);
});
