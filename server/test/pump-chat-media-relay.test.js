'use strict';
/**
 * ══ رسانهٔ چتِ مشتری ↔ صاحبِ پمپ فقط رله است ══════════════════════════════
 *   «صدا، عکس و ویدیو از طریقِ سرور به یارو برود، نه این‌که روی سرور بماند.»
 *   ۱) عکسِ مشتری که صاحبِ پمپ گرفت ⇒ از سرور پاک شد
 *   ۲) خودِ فرستنده گرفتن را پاک نمی‌کند (پیش‌نمایشِ خودش)
 *   ۳) صدای صاحبِ پمپ که مشتری گرفت ⇒ پاک، و بارِ دوم ۴۰۴
 *   ۴) ردیفِ کهنه (بی فرستنده) دست نمی‌خورد — تا ۱۵ روزِ همیشگی
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { one, query, newId, now } = require('../src/db');

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]
  );
});
test.after(async () => { await h.stop(); });

const KEY = 'abcdef0123456789abcd';
async function pump(uid, code) {
  const login = await h.post('/api/admin/login', { username: 'admin', password: 'Admin!12345' });
  const vip = await h.post('/api/admin/pump/vip-codes', { plan: 'custom', days: 60 }, { token: login.body.token });
  const r = await h.post('/api/pump/device/activate', {
    code: vip.body.code, device: { uid, name: 'pc', platform: 'windows' }, station: { code },
  });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  const token = r.body.deviceToken;
  await h.put('/api/pump/device/files/acct-d7', { data: { v: 1, k: KEY, at: 1, d: { n: 'هارون' } } }, { token });
  return { token, code: r.body.station.code };
}
const pub = (code, path) => `/api/pump/public/${code}/acct/d7/chat${path}?k=${KEY}`;
async function raw(path, mime, buf, token) {
  const res = await fetch(`${h.base()}${path}`, {
    method: 'POST', headers: { 'Content-Type': mime, ...(token ? { Authorization: `Bearer ${token}` } : {}) }, body: buf,
  });
  return { status: res.status, body: await res.json().catch(() => null) };
}
const row = (id) => one('SELECT id, uploader FROM station_chat_media WHERE id=$1', [id]);
async function gone(id) {
  for (let i = 0; i < 50; i++) { if (!(await row(id))) return true; await new Promise(r => setTimeout(r, 20)); }
  return false;
}

test('عکسِ مشتری پس از رسیدن به صاحبِ پمپ از سرور پاک می‌شود؛ خودِ مشتری پاکش نمی‌کند', async () => {
  const p = await pump('pc-relay-1', 'relay-one');
  const png = Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47]), Buffer.alloc(1500, 3)]);
  const up = await raw(pub(p.code, '/media'), 'image/png', png);
  assert.equal(up.status, 201, JSON.stringify(up.body));
  assert.equal((await row(up.body.mediaId)).uploader, 'c');

  const mine = await fetch(`${h.base()}${pub(p.code, '/media/' + up.body.mediaId)}`);
  assert.equal(mine.status, 200);
  assert.equal(mine.headers.get('cache-control'), 'no-store');
  assert.equal(mine.headers.get('x-content-type-options'), 'nosniff');
  assert.match(mine.headers.get('content-security-policy') || '', /sandbox/);
  await mine.arrayBuffer();
  await new Promise(r => setTimeout(r, 150));
  assert.ok(await row(up.body.mediaId), 'گرفتنِ خودِ فرستنده نباید پاک کند');

  const got = await fetch(`${h.base()}/api/pump/device/chat/media/${up.body.mediaId}`, { headers: { Authorization: `Bearer ${p.token}` } });
  assert.equal(got.status, 200);
  assert.equal((await got.arrayBuffer()).byteLength, png.length);
  assert.ok(await gone(up.body.mediaId), 'پس از رسیدن باید از سرور پاک شود');
});

test('صدای صاحبِ پمپ پس از رسیدن به مشتری پاک می‌شود و بارِ دوم ۴۰۴ است', async () => {
  const p = await pump('pc-relay-2', 'relay-two');
  const v = await raw('/api/pump/device/chat/d7/media', 'audio/webm', Buffer.alloc(700, 1), p.token);
  assert.equal(v.status, 201, JSON.stringify(v.body));
  assert.equal((await row(v.body.mediaId)).uploader, 'o');

  const back = await fetch(`${h.base()}${pub(p.code, '/media/' + v.body.mediaId)}`);
  assert.equal(back.status, 200);
  await back.arrayBuffer();
  assert.ok(await gone(v.body.mediaId));
  const again = await fetch(`${h.base()}${pub(p.code, '/media/' + v.body.mediaId)}`);
  assert.equal(again.status, 404);
});

test('ردیفِ کهنهٔ بی‌فرستنده با گرفتن پاک نمی‌شود', async () => {
  const p = await pump('pc-relay-3', 'relay-three');
  const v = await raw('/api/pump/device/chat/d7/media', 'audio/webm', Buffer.alloc(300, 2), p.token);
  await query('UPDATE station_chat_media SET uploader=NULL WHERE id=$1', [v.body.mediaId]);
  const r = await fetch(`${h.base()}${pub(p.code, '/media/' + v.body.mediaId)}`);
  assert.equal(r.status, 200);
  await r.arrayBuffer();
  await new Promise(r2 => setTimeout(r2, 150));
  assert.ok(await row(v.body.mediaId));
});

test('⛔ SVG (اسکریپت‌دار) عکس شمرده نمی‌شود — نه از مشتری، نه از صاحبِ پمپ', async () => {
  const p = await pump('pc-relay-4', 'relay-four');
  const svg = Buffer.from('<svg xmlns="http://www.w3.org/2000/svg"><script>alert(1)</script></svg>');
  const c = await raw(pub(p.code, '/media'), 'image/svg+xml', svg);
  assert.equal(c.status, 400, JSON.stringify(c.body));
  const o = await raw('/api/pump/device/chat/d7/media', 'image/svg+xml; charset=utf-8', svg, p.token);
  assert.equal(o.status, 400, JSON.stringify(o.body));
});
