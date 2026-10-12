'use strict';
/**
 * عکسِ کالاها روی حساب — همان عکس روی همهٔ دستگاه‌های یک دکان، و فقط همان دکان.
 */
process.env.RATE_LOGIN_EDGE_MAX = process.env.RATE_LOGIN_EDGE_MAX || '100000';

const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');

const JPEG = Buffer.concat([Buffer.from([0xff, 0xd8, 0xff, 0xe0]), Buffer.alloc(400, 7)]);

async function shopOwner(name, deviceId) {
  let u = await h.newUser(name, 'shop');
  await h.api('POST', '/api/shop', { token: u.accessToken, body: { name: 'دکانِ ' + name } });
  const login = await h.api('POST', '/api/auth/login', {
    body: { identifier: u.email, password: u.password, app: 'shop', device: { deviceId, name: deviceId, platform: 'test' } },
  });
  return { ...u, ...login.body };
}
const call = (method, path, token, body) => h.api(method, path, { token, body, headers: { 'X-App': 'shop', 'X-Device': 'dev-a' } });

let a; let b;
test.before(async () => {
  await h.start();
  a = await shopOwner('عکس‌دار', 'dev-a');
  b = await shopOwner('همسایه', 'dev-b');
});
test.after(async () => { await h.stop(); });

test('فرستادن، فهرست، خواندن و حذفِ عکسِ کالا', async () => {
  const put = await call('PUT', '/api/sync/v1/photos/p-1', a.accessToken, { data: JPEG.toString('base64'), device_id: 'dev-a' });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  assert.ok(put.body.rev > 0);

  const list = await call('GET', '/api/sync/v1/photos?since=0', a.accessToken);
  assert.equal(list.status, 200);
  assert.deepEqual(list.body.items.map(i => [i.product_id, i.deleted]), [['p-1', false]]);

  const got = await call('GET', '/api/sync/v1/photos/p-1', a.accessToken);
  assert.equal(got.status, 200);
  assert.ok(Buffer.from(got.body.data, 'base64').equals(JPEG));
  assert.equal(got.body.type, 'image/jpeg');

  //  پس از rev ِ فعلی چیزی نیست
  const after = await call('GET', `/api/sync/v1/photos?since=${list.body.head}`, a.accessToken);
  assert.equal(after.body.items.length, 0);

  const del = await call('DELETE', '/api/sync/v1/photos/p-1', a.accessToken);
  assert.equal(del.status, 200);
  const list2 = await call('GET', `/api/sync/v1/photos?since=${list.body.head}`, a.accessToken);
  assert.deepEqual(list2.body.items.map(i => [i.product_id, i.deleted]), [['p-1', true]]);
  assert.equal((await call('GET', '/api/sync/v1/photos/p-1', a.accessToken)).status, 404);
});

test('دکانِ دیگر عکسِ این دکان را نمی‌بیند', async () => {
  await call('PUT', '/api/sync/v1/photos/p-2', a.accessToken, { data: JPEG.toString('base64') });
  const other = await call('GET', '/api/sync/v1/photos/p-2', b.accessToken);
  assert.equal(other.status, 404);
  const list = await call('GET', '/api/sync/v1/photos?since=0', b.accessToken);
  assert.equal(list.body.items.length, 0);
});

test('فایلِ غیرِ عکس و شناسهٔ نادرست پذیرفته نمی‌شوند', async () => {
  const txt = await call('PUT', '/api/sync/v1/photos/p-3', a.accessToken, { data: Buffer.alloc(500, 65).toString('base64') });
  assert.equal(txt.status, 400);
  const bad = await call('PUT', '/api/sync/v1/photos/..%2Fx', a.accessToken, { data: JPEG.toString('base64') });
  assert.ok(bad.status === 400 || bad.status === 404);
  const none = await call('PUT', '/api/sync/v1/photos/p-4', null, { data: JPEG.toString('base64') });
  assert.equal(none.status, 401);
});

test('سوکتِ زنده: عکسِ تازه و بارکدِ گوشی به دستگاهِ دیگرِ همان حساب می‌رسد، نه به حسابِ دیگر', async () => {
  const { WebSocket } = require('ws');
  const base = await h.start();
  require('../src/lib/sync-v1-live').attach(h.server());
  const url = (token, dev) => base.replace('http', 'ws') + `/api/sync/v1/live?token=${encodeURIComponent(token)}&device_id=${dev}&app=shop`;
  const open = (token, dev) => new Promise((resolve, reject) => {
    const ws = new WebSocket(url(token, dev));
    ws.msgs = [];
    ws.on('message', m => ws.msgs.push(JSON.parse(String(m))));
    ws.on('open', () => resolve(ws));
    ws.on('error', reject);
  });
  const desk = await open(a.accessToken, 'desk');
  const phone = await open(a.accessToken, 'phone');
  const stranger = await open(b.accessToken, 'other');
  await new Promise(r => setTimeout(r, 150));

  phone.send(JSON.stringify({ type: 'relay', event: 'scan', code: '6261234567890', pair: '123456' }));
  //  رخدادِ ناشناخته جایی نمی‌رود
  phone.send(JSON.stringify({ type: 'relay', event: 'changed', cursor: 999 }));
  await call('PUT', '/api/sync/v1/photos/p-9', a.accessToken, { data: JPEG.toString('base64'), device_id: 'phone' });
  await new Promise(r => setTimeout(r, 300));

  const got = desk.msgs.filter(m => m.event !== 'hello');
  assert.ok(got.some(m => m.event === 'scan' && m.code === '6261234567890' && m.pair === '123456'), JSON.stringify(got));
  assert.ok(got.some(m => m.event === 'photos' && m.rev > 0), JSON.stringify(got));
  assert.ok(!got.some(m => m.event === 'changed'), 'رخدادِ ناشناخته نباید برسد');
  assert.ok(!phone.msgs.some(m => m.event === 'scan'), 'فرستنده خبرِ کارِ خودش را نمی‌گیرد');
  assert.equal(stranger.msgs.filter(m => m.event !== 'hello').length, 0, 'حسابِ دیگر چیزی نمی‌گیرد');
  [desk, phone, stranger].forEach(ws => ws.close());
});
