'use strict';
/* تست حساب مشترک دکان و همگام‌سازی چنددستگاهی —
   با تمرکز روی سناریوی واقعی: صاحب دکان و شاگرد، هر دو آفلاین، جدا جدا می‌فروشند. */
const test = require('node:test');
const assert = require('node:assert');
const fs = require('fs'); const os = require('os'); const path = require('path');

const TMP = fs.mkdtempSync(path.join(os.tmpdir(), 'tohid-shop-test-'));
process.env.DATA_DIR = TMP; process.env.KEYS_DIR = path.join(TMP, 'keys');
process.env.DB_PATH = path.join(TMP, 'test.db');
process.env.RATE_AUTH_MAX = '500'; process.env.RATE_GENERAL_MAX = '100000';
process.env.LOGIN_LOCKOUT_TRIES = '500';

const config = require('../src/config');
const ck = require('../src/lib/crypto-keys');

let server, base;
async function boot() {
  ck.writeKeyPair(config.keysDir, await ck.generateKeyPair());
  const { createApp } = require('../src/app');
  const app = await createApp();
  await new Promise(r => { server = app.listen(0, '127.0.0.1', r); });
  base = `http://127.0.0.1:${server.address().port}`;
}
async function req(p, { method = 'GET', body, token } = {}) {
  const headers = { 'Content-Type': 'application/json' };
  if (token) headers.Authorization = 'Bearer ' + token;
  const r = await fetch(base + p, { method, headers, body: body ? JSON.stringify(body) : undefined });
  let data = null; try { data = await r.json(); } catch {}
  return { status: r.status, data };
}
async function signup(email) {
  await req('/api/v1/auth/register', { method: 'POST', body: { email, password: 'strong-pass-123', name: email.split('@')[0] } });
  const r = await req('/api/v1/auth/login', { method: 'POST', body: { identifier: email, password: 'strong-pass-123' } });
  return r.data.accessToken;
}

test.before(async () => { await boot(); });
test.after(() => { if (server) server.close(); fs.rmSync(TMP, { recursive: true, force: true }); });

let ownerTk, staffTk, thirdTk, inviteCode;

test('صاحب دکان حساب دکان می‌سازد', async () => {
  ownerTk = await signup('owner@test.af');
  const r = await req('/api/v1/shop/create', { method: 'POST', token: ownerTk, body: { name: 'دکان توحید', maxMembers: 5 } });
  assert.equal(r.status, 201);
  assert.equal(r.data.shop.myRole, 'owner');
  assert.equal(r.data.shop.maxMembers, 5);
});

test('ساخت دکان دوم برای همان کاربر رد می‌شود', async () => {
  const r = await req('/api/v1/shop/create', { method: 'POST', token: ownerTk, body: { name: 'دکان دوم' } });
  assert.equal(r.status, 409);
});

test('شاگرد با کد دعوت وارد همان دکان می‌شود', async () => {
  const inv = await req('/api/v1/shop/invite', { method: 'POST', token: ownerTk, body: { role: 'staff' } });
  assert.equal(inv.status, 201);
  inviteCode = inv.data.code;
  assert.match(inviteCode, /^[A-Z2-9]{4}-[A-Z2-9]{4}$/);

  staffTk = await signup('staff@test.af');
  const j = await req('/api/v1/shop/join', { method: 'POST', token: staffTk, body: { code: inviteCode } });
  assert.equal(j.status, 200);
  assert.equal(j.data.shop.name, 'دکان توحید');
  assert.equal(j.data.shop.myRole, 'staff');
});

test('کد دعوت دوبار کار نمی‌کند', async () => {
  thirdTk = await signup('third@test.af');
  const j = await req('/api/v1/shop/join', { method: 'POST', token: thirdTk, body: { code: inviteCode } });
  assert.equal(j.status, 409);
});

test('کد نامعتبر رد می‌شود', async () => {
  const j = await req('/api/v1/shop/join', { method: 'POST', token: thirdTk, body: { code: 'ZZZZ-ZZZZ' } });
  assert.equal(j.status, 404);
});

test('شاگرد نمی‌تواند عضو دعوت کند', async () => {
  const r = await req('/api/v1/shop/invite', { method: 'POST', token: staffTk, body: {} });
  assert.equal(r.status, 403);
  assert.equal(r.data.error.code, 'not_owner');
});

test('سقف ۵ نفر رعایت می‌شود', async () => {
  // تا سقف پر شود: owner + staff = ۲، سه نفر دیگر اضافه می‌کنیم
  for (const email of ['m3@test.af', 'm4@test.af', 'm5@test.af']) {
    const inv = await req('/api/v1/shop/invite', { method: 'POST', token: ownerTk, body: {} });
    assert.equal(inv.status, 201, 'دعوت باید تا سقف مجاز باشد');
    const tk = await signup(email);
    const j = await req('/api/v1/shop/join', { method: 'POST', token: tk, body: { code: inv.data.code } });
    assert.equal(j.status, 200);
  }
  const over = await req('/api/v1/shop/invite', { method: 'POST', token: ownerTk, body: {} });
  assert.equal(over.status, 409);
  assert.equal(over.data.error.code, 'member_limit_reached');
});

test('کاربر بیرون از دکان به داده دسترسی ندارد', async () => {
  const outsider = await signup('outsider@test.af');
  const r = await req('/api/v1/shop/sync/pull?since=0', { token: outsider });
  assert.equal(r.status, 403);
  assert.equal(r.data.error.code, 'no_shop');
});

// ============ سناریوی واقعی ============
test('صاحب دکان کالا وارد انبار می‌کند و همگام می‌شود', async () => {
  const t = Date.now();
  const changes = [
    { collection: 'products', id: 'p1', updatedAt: t, data: { id: 'p1', name: 'روغن', unit: 'عدد', purchasePrice: 400, salePrice: 500, minStock: 5 } },
    { collection: 'warehouseEntries', id: 'w1', updatedAt: t, data: { id: 'w1', productId: 'p1', units: 10, price: 400, date: '2026-08-24' } },
  ];
  const r = await req('/api/v1/shop/sync/push', { method: 'POST', token: ownerTk, body: { deviceId: 'dev-owner', changes } });
  assert.equal(r.status, 200);
  assert.equal(r.data.applied, 2);
});

test('شاگرد همان کالا را می‌بیند', async () => {
  const r = await req('/api/v1/shop/sync/pull?since=0', { token: staffTk });
  assert.equal(r.status, 200);
  const prod = r.data.changes.find(c => c.collection === 'products' && c.id === 'p1');
  assert.ok(prod, 'شاگرد باید محصول را ببیند');
  assert.equal(prod.data.name, 'روغن');
  const wh = r.data.changes.find(c => c.collection === 'warehouseEntries');
  assert.equal(wh.data.units, 10);
});

test('هر دو آفلاین می‌فروشند و بعد همگام می‌کنند — هیچ فروشی گم نمی‌شود', async () => {
  const t = Date.now();
  // شاگرد در دکان بدون اینترنت: ۶ عدد می‌فروشد
  const staffPush = await req('/api/v1/shop/sync/push', {
    method: 'POST', token: staffTk, body: { deviceId: 'dev-staff', changes: [
      { collection: 'sales', id: 's-staff', updatedAt: t + 1, data: { id: 's-staff', invoiceNumber: 1000, finalTotal: 3000, status: 'completed', date: '2026-08-25' } },
      { collection: 'saleItems', id: 'si-staff', updatedAt: t + 1, data: { id: 'si-staff', saleId: 's-staff', productId: 'p1', quantity: 6, unitPrice: 500, purchasePrice: 400, returnedQty: 0 } },
    ] },
  });
  assert.equal(staffPush.data.applied, 2);

  // صاحب دکان، بی‌خبر از فروش شاگرد، ۷ عدد می‌فروشد
  const ownerPush = await req('/api/v1/shop/sync/push', {
    method: 'POST', token: ownerTk, body: { deviceId: 'dev-owner', changes: [
      { collection: 'sales', id: 's-owner', updatedAt: t + 2, data: { id: 's-owner', invoiceNumber: 1000, finalTotal: 3500, status: 'completed', date: '2026-08-26' } },
      { collection: 'saleItems', id: 'si-owner', updatedAt: t + 2, data: { id: 'si-owner', saleId: 's-owner', productId: 'p1', quantity: 7, unitPrice: 500, purchasePrice: 400, returnedQty: 0 } },
    ] },
  });
  assert.equal(ownerPush.data.applied, 2);

  // بعد از ادغام، هر دو فروش سر جایشان هستند
  const all = await req('/api/v1/shop/sync/pull?since=0', { token: ownerTk });
  const sales = all.data.changes.filter(c => c.collection === 'sales' && !c.deleted);
  assert.equal(sales.length, 2, 'هیچ فروشی نباید پاک شود');

  const items = all.data.changes.filter(c => c.collection === 'saleItems' && !c.deleted);
  const soldTotal = items.reduce((s, i) => s + i.data.quantity, 0);
  const inbound = all.data.changes.filter(c => c.collection === 'warehouseEntries' && !c.deleted)
    .reduce((s, w) => s + w.data.units, 0);
  assert.equal(inbound, 10);
  assert.equal(soldTotal, 13);
  // این دقیقاً حالتی است که کاربر توصیف کرد: ۱۳ فروخته شده ولی فقط ۱۰ وارد شده
  assert.equal(inbound - soldTotal, -3, 'کسری موجودی باید ۳ باشد تا برنامه بتواند توضیحش دهد');
});

test('همگام‌سازی تفاضلی است — فقط تغییرات تازه می‌آید', async () => {
  const first = await req('/api/v1/shop/sync/pull?since=0', { token: staffTk });
  const rev = first.data.rev;
  const again = await req(`/api/v1/shop/sync/pull?since=${rev}`, { token: staffTk });
  assert.equal(again.data.changes.length, 0, 'بدون تغییر جدید نباید چیزی برگردد');

  const t = Date.now();
  await req('/api/v1/shop/sync/push', { method: 'POST', token: ownerTk,
    body: { deviceId: 'dev-owner', changes: [{ collection: 'expenses', id: 'e1', updatedAt: t, data: { id: 'e1', amount: 500 } }] } });
  const delta = await req(`/api/v1/shop/sync/pull?since=${rev}`, { token: staffTk });
  assert.equal(delta.data.changes.length, 1, 'فقط رکورد تازه باید بیاید');
  assert.equal(delta.data.changes[0].id, 'e1');
});

test('ویرایش هم‌زمان: آخرین ویرایش برنده است', async () => {
  const t = Date.now();
  await req('/api/v1/shop/sync/push', { method: 'POST', token: staffTk,
    body: { deviceId: 'dev-staff', changes: [{ collection: 'products', id: 'p1', updatedAt: t + 100, data: { id: 'p1', name: 'روغن', salePrice: 520 } }] } });
  await req('/api/v1/shop/sync/push', { method: 'POST', token: ownerTk,
    body: { deviceId: 'dev-owner', changes: [{ collection: 'products', id: 'p1', updatedAt: t + 50, data: { id: 'p1', name: 'روغن', salePrice: 480 } }] } });

  const r = await req('/api/v1/shop/sync/pull?since=0', { token: ownerTk });
  const p1 = r.data.changes.filter(c => c.collection === 'products' && c.id === 'p1').pop();
  assert.equal(p1.data.salePrice, 520, 'ویرایش تازه‌تر باید برنده شود، نه آخرین درخواست');
});

test('حذف با tombstone منتقل می‌شود', async () => {
  const t = Date.now() + 1000;
  await req('/api/v1/shop/sync/push', { method: 'POST', token: ownerTk,
    body: { deviceId: 'dev-owner', changes: [{ collection: 'expenses', id: 'e1', updatedAt: t, deleted: true, data: null }] } });
  const r = await req('/api/v1/shop/sync/pull?since=0', { token: staffTk });
  const e1 = r.data.changes.filter(c => c.collection === 'expenses' && c.id === 'e1').pop();
  assert.equal(e1.deleted, true, 'حذف باید به دستگاه دیگر هم برسد');
});

test('مجموعه‌ی ناشناخته نادیده گرفته می‌شود', async () => {
  const r = await req('/api/v1/shop/sync/push', { method: 'POST', token: ownerTk,
    body: { deviceId: 'x', changes: [{ collection: 'hackers', id: 'h1', updatedAt: Date.now(), data: {} }] } });
  assert.equal(r.data.applied, 0);
  assert.equal(r.data.skipped, 1);
});

test('حذف عضو دسترسی او را می‌بندد', async () => {
  const members = await req('/api/v1/shop/members', { token: ownerTk });
  const staff = members.data.members.find(m => m.email === 'staff@test.af');
  const rm = await req(`/api/v1/shop/members/${staff.user_id}/remove`, { method: 'POST', token: ownerTk });
  assert.equal(rm.status, 200);
  const pull = await req('/api/v1/shop/sync/pull?since=0', { token: staffTk });
  assert.equal(pull.status, 403, 'عضو حذف‌شده دیگر نباید داده ببیند');
});

test('صاحب دکان قابل حذف نیست', async () => {
  const me = await req('/api/v1/shop/me', { token: ownerTk });
  const owner = me.data.members.find(m => m.role === 'owner');
  const r = await req(`/api/v1/shop/members/${owner.userId}/remove`, { method: 'POST', token: ownerTk });
  assert.equal(r.status, 400);
});
