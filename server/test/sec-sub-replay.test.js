'use strict';
/**
 * SEC-SUB-04 — بازپخش و درخواست‌های هم‌زمان تمدید/فعال‌سازی را دوبار نمی‌کنند.
 *
 * درهای کاربر برای باز کردنِ اشتراک: کدِ وی‌آی‌پی (`POST /api/vip/redeem`) و کدِ آفلاین
 * (`POST /api/vip/offline-code`). هر دو با چند درخواستِ هم‌زمانِ یک کد، و با بازپخشِ همان درخواست
 * پس از موفقیت، سنجیده می‌شوند: فقط یک دوره، فقط یک دکان. (تمدیدِ مدیر با `Idempotency-Key`:
 * `admin-idempotency.test.js`؛ سقفِ دستگاهِ هم‌زمان: `sec-sub-device-cap.test.js`.)
 * روی PostgreSQLِ CI قفل‌ها واقعاً هم‌زمان می‌دوند؛ روی PGlite پشتِ هم.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');
const offline = require('../src/lib/shop-offline-codes');

const DAY = 24 * 60 * 60 * 1000;
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
  assert.ok(admin);
});
test.after(async () => { await h.stop(); });

async function expiredOwner(label) {
  const u = await h.newUser(label);
  const shopId = (await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken })).body.shop.id;
  await query('UPDATE shops SET created_at=$2 WHERE id=$1', [shopId, now() - 400 * DAY]);
  return { ...u, shopId };
}
const endsOf = async (shopId) => {
  const rows = await query('SELECT ends_at FROM subscriptions WHERE shop_id=$1', [shopId]);
  return rows.rows.map((r) => Number(r.ends_at));
};

test('SEC-SUB-04: یک کدِ وی‌آی‌پی، چهار درخواستِ هم‌زمان و یک بازپخش ⇒ یک دوره', async () => {
  const o = await expiredOwner('وی‌آی‌پی-هم‌زمان');
  const made = await h.post('/api/admin/vip-codes', { plan: 'm1', days: 30 }, { token: admin });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const code = made.body.code;
  const out = await Promise.all([1, 2, 3, 4].map(() => h.post('/api/vip/redeem', { code }, { token: o.accessToken })));
  const ok = out.filter((r) => r.status === 201).length;
  assert.equal(ok, 1, `فقط یکی باید بگیرد: ${out.map((r) => `${r.status}:${r.body.error?.code || ''}`).join(',')}`);
  assert.ok(out.filter((r) => r.status !== 201).every((r) => r.status === 403 && r.body.error?.code === 'code_used'));
  const replay = await h.post('/api/vip/redeem', { code }, { token: o.accessToken });
  assert.equal(replay.status, 403);
  assert.equal(replay.body.error?.code, 'code_used');
  const ends = await endsOf(o.shopId);
  assert.equal(ends.length, 1, 'یک ردیفِ اشتراک');
  assert.ok(ends[0] - now() <= 31 * DAY, `یک دورهٔ سی‌روزه، نه بیشتر: ${Math.round((ends[0] - now()) / DAY)} روز`);
});

test('SEC-SUB-04: یک کدِ وی‌آی‌پی، دو دکانِ هم‌زمان ⇒ فقط یکی', async () => {
  const a = await expiredOwner('دکان-A');
  const b = await expiredOwner('دکان-B');
  const code = (await h.post('/api/admin/vip-codes', { plan: 'm1', days: 30 }, { token: admin })).body.code;
  const [ra, rb] = await Promise.all([a, b].map((o) => h.post('/api/vip/redeem', { code }, { token: o.accessToken })));
  assert.equal([ra, rb].filter((r) => r.status === 201).length, 1, `${ra.status} / ${rb.status}`);
  const n = (await endsOf(a.shopId)).length + (await endsOf(b.shopId)).length;
  assert.equal(n, 1, 'فقط یک دکان اشتراک گرفت');
});

test('SEC-SUB-04: یک کدِ آفلاین، چهار درخواستِ هم‌زمان و بازپخش ⇒ یک ثبت، یک دوره', async () => {
  const o = await expiredOwner('آفلاین-هم‌زمان');
  const pc = offline.computerOfFingerprint(`win-${'c'.repeat(32)}`);
  const made = await h.post('/api/admin/shop-offline-codes', { plan: 'sub', computer: pc, days: 30 }, { token: admin });
  assert.ok(made.status < 300, JSON.stringify(made.body));
  const code = made.body.code;
  const call = () => h.post('/api/vip/offline-code', { code, computer: pc }, { token: o.accessToken });
  const out = await Promise.all([1, 2, 3, 4].map(call));
  const statuses = out.map((r) => `${r.status}:${r.body.status || r.body.error?.code || ''}`);
  assert.equal(out.filter((r) => r.body.status === 'granted').length, 1, statuses.join(','));
  assert.ok(out.every((r) => r.status === 200 && ['granted', 'already'].includes(r.body.status)), statuses.join(','));
  assert.equal((await call()).body.status, 'already', 'بازپخش پس از موفقیت');
  const ends = await endsOf(o.shopId);
  assert.equal(ends.length, 1);
  assert.ok(ends[0] - now() <= 31 * DAY);
  const row = await one('SELECT redeemed_shop_id FROM shop_offline_codes WHERE redeemed_shop_id=$1', [o.shopId]);
  assert.ok(row);
});
