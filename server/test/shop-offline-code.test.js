'use strict';
/**
 * ══ کدِ اشتراکِ آفلاینِ فروشگاه — بسته به یک کامپیوتر، یک بار ═══════════
 *
 * «برای هر کامپیوتر یک ایدی… از سرور به اون ادرس کد داده میشه… روی همون
 * کد فقط کار کنه و یک بار بیشتر هم استفاده نشه.»
 *
 * هر بند از درهای واقعی: پنلِ مدیر (‎/api/admin/shop-offline-codes‎) و
 * برنامهٔ واردشده (‎/api/vip/offline-code‎).
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('crypto');
const h = require('./helpers');
const { query, newId, now } = require('../src/db');
const offline = require('../src/lib/shop-offline-codes');
const pumpOffline = require('../src/lib/offline-codes');
const license = require('../src/lib/license');

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

const DAY = 86400000;
const PC_A = offline.computerOfFingerprint('win-' + 'a'.repeat(32));
const PC_B = offline.computerOfFingerprint('win-' + 'b'.repeat(32));

async function admin() {
  return (await h.post('/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
}
async function issue(body) {
  return h.post('/api/admin/shop-offline-codes', body, { token: await admin() });
}
async function shopOwner(name) {
  const u = await h.newUser(name);
  const made = await h.post('/api/shop', { name: `دکانِ ${name}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  return { ...u, shopId: made.body.shop.id };
}
const redeem = (o, code, computer) => h.post('/api/vip/offline-code', { code, computer }, { token: o.accessToken });

test('صدور: ۱۳۸ نویسه، امضای درست، بسته به همان کامپیوتر', async () => {
  const r = await issue({ plan: 'sub', computer: PC_A, days: 30 });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  assert.equal(r.body.code.replace(/-/g, '').length, 138);
  const p = await offline.verify(r.body.code);
  assert.ok(p, 'امضا با کلیدِ همین سرور');
  assert.equal(p.plan, 'sub');
  assert.ok(p.endsAt - p.issuedAt >= 30 * DAY);
  assert.equal(r.body.offline.computer, PC_A);
  //  خودِ کد در جدول نیست
  const rows = (await query('SELECT * FROM shop_offline_codes WHERE id=$1', [r.body.offline.id])).rows;
  assert.ok(!JSON.stringify(rows).includes(r.body.code.replace(/-/g, '').slice(0, 40)));
});

test('شناسهٔ کامپیوترِ غلط یا نوعِ ناشناس ⇒ ۴۰۰', async () => {
  assert.equal((await issue({ plan: 'sub', computer: 'ABCD-EFGH' })).status, 400);
  assert.equal((await issue({ plan: 'vip', computer: PC_A })).status, 400);
});

test('کدِ پمپ در فروشگاه پذیرفته نیست (دامنهٔ امضا جدا)', async () => {
  const pc = pumpOffline.parseComputer(PC_A);
  const b = pumpOffline.body2({ plan: 'vip', machine: pc.machine, account: Buffer.alloc(4),
    serial: crypto.randomBytes(4), issuedDay: 1000, endDay: 5000 });
  const pumpCode = await pumpOffline.encode2(b, license.signBytes);
  assert.equal(await offline.verify(pumpCode), null);
});

test('ثبتِ آنلاین ⇒ اشتراکِ دکان؛ دوباره ⇒ روز اضافه نمی‌شود؛ دکانِ دیگر ⇒ ۴۰۹', async () => {
  const o = await shopOwner('آفلاین');
  const made = (await issue({ plan: 'sub', computer: PC_A, days: 400 })).body;
  const r = await redeem(o, made.code, PC_A);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.body.status, 'granted');
  assert.equal(r.body.entitlement.source, 'subscription');
  const ends1 = Number((await query(`SELECT ends_at FROM subscriptions WHERE shop_id=$1 AND status='active'`, [o.shopId])).rows[0].ends_at);
  assert.ok(ends1 >= now() + 399 * DAY);

  const again = await redeem(o, made.code, PC_A);
  assert.equal(again.status, 200);
  assert.equal(again.body.status, 'already');
  const ends2 = Number((await query(`SELECT ends_at FROM subscriptions WHERE shop_id=$1 AND status='active'`, [o.shopId])).rows[0].ends_at);
  assert.equal(ends2, ends1, 'یک بار، نه دو بار');

  const other = await shopOwner('دیگری');
  const r2 = await redeem(other, made.code, PC_A);
  assert.equal(r2.status, 409);
  assert.equal(r2.body.error.code, 'code_used_elsewhere');
});

test('کامپیوترِ دیگر ⇒ computer_mismatch', async () => {
  const o = await shopOwner('کامپیوترِ دیگر');
  const made = (await issue({ plan: 'sub', computer: PC_A, days: 30 })).body;
  const r = await redeem(o, made.code, PC_B);
  assert.equal(r.status, 400);
  assert.equal(r.body.error.code, 'computer_mismatch');
});

test('بسته به حساب: حسابِ دیگر ⇒ account_mismatch؛ همان حساب ⇒ پذیرفته', async () => {
  const mine = await shopOwner('صاحبِ کد');
  const made = (await issue({ plan: 'perm', computer: PC_A, account: mine.email })).body;
  assert.equal(made.offline.permanent, true);
  const thief = await shopOwner('دزد');
  const bad = await redeem(thief, made.code, PC_A);
  assert.equal(bad.status, 409);
  assert.equal(bad.body.error.code, 'account_mismatch');
  const ok = await redeem(mine, made.code, PC_A);
  assert.equal(ok.status, 200, JSON.stringify(ok.body));
  assert.equal(ok.body.status, 'granted');
});

test('باطل‌شده ⇒ ۴۱۰', async () => {
  const o = await shopOwner('باطل');
  const made = (await issue({ plan: 'sub', computer: PC_A, days: 30 })).body;
  const t = await admin();
  const rv = await h.post(`/api/admin/shop-offline-codes/${made.offline.id}/revoke`, {}, { token: t });
  assert.equal(rv.status, 200);
  const r = await redeem(o, made.code, PC_A);
  assert.equal(r.status, 410);
  assert.equal(r.body.error.code, 'code_revoked');
  const ls = await h.get('/api/admin/shop-offline-codes', { token: t });
  assert.ok(ls.body.codes.some(c => c.id === made.offline.id && c.status === 'revoked'));
});

test('کدِ دست‌کاری‌شده ⇒ bad_offline_code', async () => {
  const o = await shopOwner('دست‌کاری');
  const made = (await issue({ plan: 'sub', computer: PC_A, days: 30 })).body;
  const raw = made.code.replace(/-/g, '');
  const flipped = raw.slice(0, 5) + (raw[5] === 'A' ? 'B' : 'A') + raw.slice(6);
  const r = await redeem(o, flipped, PC_A);
  assert.equal(r.status, 400);
  assert.equal(r.body.error.code, 'bad_offline_code');
});
