'use strict';
/**
 * ══ کیو‌آرِ زندهٔ مشتری — /api/pump/public ═════════════════════════════
 *
 * چیزهایی که این‌جا قفل می‌شوند:
 *   ۱) برنامهٔ کامپیوتر فایلِ ‎acct-<شناسه>‎ را می‌نویسد و مشتری با رمزِ همان
 *      یک حساب (‎k‎) می‌خواندش — بی هیچ توکنی.
 *   ۲) رمزِ حساب هرگز در پاسخ برنمی‌گردد.
 *   ۳) رمزِ غلط، حسابِ نبوده و پمپِ نبوده همه یک ۴۰۴ می‌گیرند.
 *   ۴) CORS باز است — صفحهٔ مشتری روی دامنهٔ پمپ است.
 *   ۵) فایل‌های دیگرِ پمپ (‎live.json‎ …) از این در بیرون نمی‌روند.
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
  return { token: r.body.deviceToken, code: r.body.station.code };
}

const KEY = 'abcdef0123456789abcd';
const env = (d, at = 1700000000000) => ({ v: 1, k: KEY, at, d });

test('برنامه می‌نویسد، مشتری با رمزِ همان حساب می‌خواند — بی توکن', async () => {
  const a = await activated('pc-pub-1', 'pub-one');
  const w = await h.put('/api/pump/device/files/acct-d12',
    { data: env({ n: 'محمد', s: [['الباقی', '640']] }) }, { token: a.token });
  assert.equal(w.status, 200, JSON.stringify(w.body));

  const r = await h.get(`/api/pump/public/${a.code}/acct/d12?k=${KEY}`);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.body.d.n, 'محمد');
  assert.equal(r.body.at, 1700000000000);
  assert.ok(!JSON.stringify(r.body).includes(KEY), 'رمزِ حساب نباید در پاسخ باشد');
  assert.equal(r.headers.get('access-control-allow-origin'), '*');
  assert.equal(r.headers.get('cache-control'), 'no-store');
});

test('عکسِ تازه‌تر جای قبلی را می‌گیرد — همان چیزی که «هر دقیقه چک کند» می‌خواهد', async () => {
  const a = await activated('pc-pub-2', 'pub-two');
  await h.put('/api/pump/device/files/acct-d5', { data: env({ n: 'اول' }, 100) }, { token: a.token });
  await h.put('/api/pump/device/files/acct-d5', { data: env({ n: 'دوم' }, 200) }, { token: a.token });
  const r = await h.get(`/api/pump/public/${a.code}/acct/d5?k=${KEY}`);
  assert.equal(r.body.d.n, 'دوم');
  assert.equal(r.body.at, 200);
});

test('رمزِ غلط، حسابِ نبوده، پمپِ نبوده و بی‌رمز — همه یک ۴۰۴', async () => {
  const a = await activated('pc-pub-3', 'pub-three');
  await h.put('/api/pump/device/files/acct-c9', { data: env({ n: 'شرکت' }) }, { token: a.token });

  const wrong = await h.get(`/api/pump/public/${a.code}/acct/c9?k=abcdef0123456789abce`);
  assert.equal(wrong.status, 404);
  const none = await h.get(`/api/pump/public/${a.code}/acct/c8?k=${KEY}`);
  assert.equal(none.status, 404);
  const noPump = await h.get(`/api/pump/public/no-such-pump/acct/c9?k=${KEY}`);
  assert.equal(noPump.status, 404);
  const noKey = await h.get(`/api/pump/public/${a.code}/acct/c9`);
  assert.equal(noKey.status, 404);
  const short = await h.get(`/api/pump/public/${a.code}/acct/c9?k=abc`);
  assert.equal(short.status, 404);
});

test('فایل‌های دیگرِ پمپ از این در بیرون نمی‌روند', async () => {
  const a = await activated('pc-pub-4', 'pub-four');
  await h.put('/api/pump/device/files/live.json', { data: { k: KEY, safe: 500 } }, { token: a.token });
  //  شناسه باید «حرف + عدد» باشد؛ ‎live.json‎ اصلاً به این مسیر نمی‌رسد،
  //  و ‎acct-‎ همیشه جلوی نام می‌نشیند — پس فایلِ ‎live.json‎ دست‌نیافتنی است.
  const r = await h.get(`/api/pump/public/${a.code}/acct/live.json?k=${KEY}`);
  assert.equal(r.status, 404);
});

test('این در به توکن کاری ندارد — توکنِ پمپِ دیگر هم فرقی نمی‌کند', async () => {
  const a = await activated('pc-pub-5', 'pub-five');
  const b = await activated('pc-pub-6', 'pub-six');
  await h.put('/api/pump/device/files/acct-d1', { data: env({ n: 'الف' }) }, { token: a.token });
  //  پمپِ ب همان شناسه و همان رمز را ندارد ⇒ ۴۰۴، حتی با توکنِ خودش
  const r = await h.get(`/api/pump/public/${b.code}/acct/d1?k=${KEY}`, { token: b.token });
  assert.equal(r.status, 404);
});
