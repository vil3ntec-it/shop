'use strict';
/**
 * ══ کدِ دسترسیِ پمپ — درِ اپِ کارمندان ══════════════════════════════════
 *
 * خواستهٔ صاحب مخزن: «برای هر پمپ یک کد باشد که هر کسی برنامه را نصب
 * می‌کند همان را بزند و فقط حساب‌های همان پمپ را ببیند و با پمپ‌های دیگر
 * قاطی نشود.»
 *
 * چیزهایی که این‌جا قفل می‌شوند:
 *   ۱) برنامهٔ کامپیوتر کدش را می‌گیرد؛ دو بار پرسیدن همان کد را می‌دهد.
 *   ۲) گوشی با همان کد — بی هیچ حسابی — نشانی و رمزِ خواندنِ **همان** پمپ را
 *      می‌گیرد، نه پمپِ دیگر.
 *   ۳) کدِ غلط، کدِ بدشکل و کدِ عوض‌شده ۴۰۴ می‌گیرند.
 *   ۴) عوض کردن، کدِ قبلی را همان لحظه بی‌اثر می‌کند.
 *   ۵) ‎/live‎ عکسِ ابریِ همان پمپ را می‌دهد و پیش از اولین انتشار ۴۰۴ است.
 *   ۶) صاحبِ گوگلی هم کد را می‌بیند؛ کارمند نه.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, newId, now } = require('../src/db');

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

async function activated(uid, code) {
  const login = await h.post('/api/admin/login', { username: 'admin', password: 'Admin!12345' });
  const vip = await h.post('/api/admin/pump/vip-codes',
    { plan: 'custom', days: 60 }, { token: login.body.token });
  assert.equal(vip.status, 201, JSON.stringify(vip.body));
  const r = await h.post('/api/pump/device/activate', {
    code: vip.body.code,
    device: { uid, name: 'کامپیوترِ پمپ', platform: 'windows' },
    station: { code },
  });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  return { token: r.body.deviceToken, code: r.body.station.code, stationId: r.body.station.id };
}

const CODE_RE = /^[A-HJ-NP-Z2-9]{8}$/;

test('برنامه کدش را می‌گیرد و گوشی با همان کد فقط به همان پمپ می‌رسد', async () => {
  const a = await activated('pc-ac-1', 'ac-one');
  const b = await activated('pc-ac-2', 'ac-two');
  await h.post('/api/pump/device/home', { homeUrl: 'wss://a.example', readKey: 'read-a' }, { token: a.token });
  await h.post('/api/pump/device/home', { homeUrl: 'wss://b.example', readKey: 'read-b' }, { token: b.token });

  const first = await h.get('/api/pump/device/access-code', { token: a.token });
  assert.equal(first.status, 200, JSON.stringify(first.body));
  assert.match(first.body.code, CODE_RE);
  assert.equal(first.body.display, first.body.code.slice(0, 4) + '-' + first.body.code.slice(4));

  //  دوباره پرسیدن ⇒ همان کد، نه کدِ تازه
  const again = await h.get('/api/pump/device/access-code', { token: a.token });
  assert.equal(again.body.code, first.body.code);

  //  گوشی — بی توکن، با خطِ تیره و حرفِ کوچک
  const joined = await h.post('/api/pump/public/join', { code: first.body.display.toLowerCase() });
  assert.equal(joined.status, 200, JSON.stringify(joined.body));
  assert.equal(joined.body.station.code, 'ac-one');
  assert.equal(joined.body.home.url, 'wss://a.example');
  assert.equal(joined.body.home.readKey, 'read-a');
  assert.equal(joined.body.home.station, 'ac-one');
  assert.equal(joined.headers.get('access-control-allow-origin'), '*');
  //  هیچ چیزی از پمپِ دیگر و هیچ شناسهٔ داخلی
  const text = JSON.stringify(joined.body);
  assert.ok(!text.includes('ac-two') && !text.includes('read-b') && !text.includes(a.stationId));

  //  کدِ پمپِ دوم ⇒ پمپِ دوم
  const codeB = (await h.get('/api/pump/device/access-code', { token: b.token })).body.code;
  assert.notEqual(codeB, first.body.code);
  const joinedB = await h.post('/api/pump/public/join', { code: codeB });
  assert.equal(joinedB.body.station.code, 'ac-two');
  assert.equal(joinedB.body.home.readKey, 'read-b');
});

test('کدِ غلط، بدشکل و خالی ۴۰۴ می‌گیرند', async () => {
  assert.equal((await h.post('/api/pump/public/join', { code: 'AAAAAAAA' })).status, 404);
  assert.equal((await h.post('/api/pump/public/join', { code: 'ABC' })).status, 404);
  assert.equal((await h.post('/api/pump/public/join', { code: 'K7PM3XQ0' })).status, 404, 'صفر در الفبا نیست');
  assert.equal((await h.post('/api/pump/public/join', {})).status, 404);
});

test('عوض کردنِ کد، کدِ قبلی را همان لحظه بی‌اثر می‌کند', async () => {
  const a = await activated('pc-ac-3', 'ac-three');
  const old = (await h.get('/api/pump/device/access-code', { token: a.token })).body.code;
  const rotated = await h.post('/api/pump/device/access-code/rotate', {}, { token: a.token });
  assert.equal(rotated.status, 201);
  assert.notEqual(rotated.body.code, old);
  assert.equal((await h.post('/api/pump/public/join', { code: old })).status, 404);
  assert.equal((await h.post('/api/pump/public/join', { code: rotated.body.code })).status, 200);
  assert.equal((await h.get('/api/pump/device/access-code', { token: a.token })).body.code, rotated.body.code);
});

test('عکسِ ابری با همان کد — و پیش از اولین انتشار ۴۰۴', async () => {
  const a = await activated('pc-ac-4', 'ac-four');
  const code = (await h.get('/api/pump/device/access-code', { token: a.token })).body.code;
  const before = await h.get(`/api/pump/public/live?code=${code}`);
  assert.equal(before.status, 404);
  assert.equal(before.body.error.code, 'no_live');

  const w = await h.put('/api/pump/device/files/live.json',
    { data: { seq: 3, at: '1405/06/26', station: { name: 'پمپِ چهار' }, gate: 'pbkdf2$sha256$x' } },
    { token: a.token });
  assert.equal(w.status, 200, JSON.stringify(w.body));

  const after = await h.get(`/api/pump/public/live?code=${code}`);
  assert.equal(after.status, 200, JSON.stringify(after.body));
  assert.equal(after.body.live.station.name, 'پمپِ چهار');
  assert.ok(after.body.updatedAt > 0);
  //  و ‎join‎ حالا می‌گوید عکسِ ابری هست
  const j = await h.post('/api/pump/public/join', { code });
  assert.ok(j.body.cloudLiveAt > 0);
  //  کدِ پمپِ دیگر عکسِ این پمپ را نمی‌دهد
  const b = await activated('pc-ac-5', 'ac-five');
  const codeB = (await h.get('/api/pump/device/access-code', { token: b.token })).body.code;
  assert.equal((await h.get(`/api/pump/public/live?code=${codeB}`)).status, 404);
});

test('صاحبِ گوگلی کد را می‌بیند، کارمند نه', async () => {
  const a = await activated('pc-ac-6', 'ac-six');
  const code = (await h.get('/api/pump/device/access-code', { token: a.token })).body.code;
  const minted = await h.post('/api/pump/device/join-code', { role: 'staff' }, { token: a.token });

  const boss = await h.newUser('کارفرما', 'pump');
  assert.equal((await h.post('/api/pump/claim', { code: minted.body.code }, { token: boss.accessToken })).status, 201);
  const seen = await h.get('/api/pump/access-code', { token: boss.accessToken });
  assert.equal(seen.status, 200, JSON.stringify(seen.body));
  assert.equal(seen.body.code, code, 'همان کدِ برنامهٔ کامپیوتر');

  const staff = await h.newUser('کارمند', 'pump');
  assert.equal((await h.post('/api/pump/claim', { code: minted.body.code }, { token: staff.accessToken })).status, 201);
  assert.equal((await h.get('/api/pump/access-code', { token: staff.accessToken })).status, 403);
});
