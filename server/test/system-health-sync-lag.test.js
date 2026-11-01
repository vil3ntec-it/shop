'use strict';
/**
 * OPS-04 — «پایشِ سرور: … تأخیرِ همگام‌سازی …» (§۶.۲۴)، بخشِ «همگام‌سازی» ِ `GET /api/admin/system/health`.
 *
 * پیش از این، کارتِ همگام‌سازی فقط «خطای برنامه» و «تعارض» ِ ۲۴ ساعت را می‌شمرد؛ هیچ‌جا دیده نمی‌شد
 * دستگاه‌ها چقدر از سرِ دفتر عقب‌اند، صفِ گوشی‌ها چقدر است یا دستگاهی که آنلاین است ولی pull نمی‌کند.
 *
 *   ۱) پرچمِ `ops.sync_lag` (scope ‏`ops`، پیش‌فرض خاموش) ⇒ پاسخ و وضعیتِ کارت همان پیشین، بی `lag`.
 *   ۲) روشن ⇒ فقط عدد: دستگاهِ فعال (۳۰ روز)، عقب از سرِ دفتر و بیشینهٔ عقب‌ماندگی، صفِ گزارش‌شدهٔ
 *      دستگاه‌ها، بی‌همگام‌سازی بیش از ۲۴ ساعت، و سنِ آخرین همگام‌سازی (میانه و p95).
 *   ۳) «گیرکرده» = در یک ساعتِ اخیر دیده شده، عقب است و بیش از یک ساعت pull نکرده ⇒ کارت `warn`.
 *      کارِ آفلاین مجاز است (D-05) ⇒ دستگاهِ آفلاین یا کهنه هرگز به‌تنهایی هشدار نمی‌شود.
 *   ۴) ⛔ هیچ شناسهٔ دستگاه، حساب یا دکانی در پاسخ نیست.
 *   ۵) دستگاهِ بیرونِ پنجرهٔ ۳۰ روز شمرده نمی‌شود.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, newId, now } = require('../src/db');
const liveConfig = require('../src/lib/live-config');

const HOUR = 60 * 60 * 1000;
const DAY = 24 * HOUR;
let admin = '';
let shopA = '';
let shopB = '';

const get = () => h.get('/api/admin/system/health', { token: admin });

async function device(shop, id, { cursor = 0, pushAt = null, pullAt = null, queued = 0, seen }) {
  await query(
    `INSERT INTO sync_devices (app, account_kind, account_id, device_id, cursor, last_push_at, last_pull_at,
                               queued_count, app_version, schema_version, last_seen_at)
     VALUES ('shop','shop',$1,$2,$3,$4,$5,$6,'2.14.4',2,$7)`,
    [shop, id, cursor, pushAt, pullAt, queued, seen]
  );
}
/** `n` op در دفترِ دکان؛ سرِ دفترِ همان دکان را برمی‌گرداند */
async function ops(shop, n) {
  const t = now();
  for (let i = 0; i < n; i++) {
    await query(
      `INSERT INTO oplog (id, op_id, app, account_kind, account_id, device_id, client_ts, table_name, row_id, op_type, fields, schema_version, received_at)
       VALUES ($1,$1,'shop','shop',$2,'w-1',$3,'products',$4,'insert','{"name":"x"}'::jsonb,2,$3)`,
      [newId('op'), shop, t, `r-${shop}-${i}`]
    );
  }
  return Number((await query(`SELECT MAX(server_seq) AS m FROM oplog WHERE account_id=$1`, [shop])).rows[0].m);
}

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
  shopA = `shop-lag-a-${Date.now()}`;
  shopB = `shop-lag-b-${Date.now()}`;
});
test.after(async () => { await h.stop(); });

test('OPS-04: پرچم خاموش (پیش‌فرض) ⇒ بخشِ همگام‌سازی همان پیشین، بی lag', async () => {
  const headA = await ops(shopA, 3);
  const t = now();
  //  دستگاهی که اگر پرچم روشن بود «گیرکرده» می‌شد
  await device(shopA, 'dev-stuck-off', { cursor: headA - 3, pushAt: t - 60 * 1000, pullAt: t - 3 * HOUR, seen: t - 60 * 1000 });
  const r = await get();
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.body.sync.status, 'ok');
  assert.equal('lag' in r.body.sync, false, JSON.stringify(r.body.sync));
  assert.deepEqual(Object.keys(r.body.sync).sort(), ['clientErrors24h', 'conflicts24h', 'status']);
  await query(`DELETE FROM sync_devices WHERE device_id='dev-stuck-off'`);
});

test('OPS-04: روشن ⇒ عقب‌ماندگی، صف، کهنگی و سنِ آخرین همگام‌سازی؛ آفلاین هشدار نیست', async () => {
  await liveConfig.set('ops', 'ops.sync_lag', true, 'test');
  const t = now();
  const headA = Number((await query(`SELECT MAX(server_seq) AS m FROM oplog WHERE account_id=$1`, [shopA])).rows[0].m);
  const headB = await ops(shopB, 5);
  //  به‌روز، همین حالا
  await device(shopA, 'dev-fresh', { cursor: headA, pushAt: t - 30 * 1000, pullAt: t - 20 * 1000, seen: t - 20 * 1000 });
  //  آفلاین از دو روز پیش، ۵ op عقب، ۴ op در صفِ خودش — مجاز (D-05) ⇒ فقط شمرده می‌شود
  await device(shopB, 'dev-offline', { cursor: headB - 5, pushAt: t - 2 * DAY, pullAt: t - 2 * DAY, queued: 4, seen: t - 2 * DAY });
  //  بیرونِ پنجرهٔ ۳۰ روز ⇒ شمرده نمی‌شود
  await device(shopB, 'dev-gone', { cursor: 0, pushAt: t - 40 * DAY, pullAt: t - 40 * DAY, queued: 9, seen: t - 40 * DAY });

  const s = (await get()).body.sync;
  assert.equal(s.status, 'ok', JSON.stringify(s));
  assert.equal(typeof s.clientErrors24h, 'number');
  assert.deepEqual(s.lag, {
    status: 'ok',
    windowDays: 30,
    devices: 2,
    behindDevices: 1,
    maxBehindOps: 5,
    queuedDevices: 1,
    queuedOps: 4,
    stale24h: 1,
    stuck: 0,
    lastSyncAgeP50Ms: s.lag.lastSyncAgeP50Ms,
    lastSyncAgeP95Ms: s.lag.lastSyncAgeP95Ms,
  });
  //  میانه و p95 از همان دو دستگاه: یکی ~۲۰ ثانیه، دیگری ~۲ روز
  assert.ok(s.lag.lastSyncAgeP50Ms >= 20 * 1000 && s.lag.lastSyncAgeP50Ms < 2 * DAY, String(s.lag.lastSyncAgeP50Ms));
  assert.ok(s.lag.lastSyncAgeP95Ms >= 2 * DAY - 5000, String(s.lag.lastSyncAgeP95Ms));
});

test('OPS-04: آنلاین ولی بی pull و عقب ⇒ «گیرکرده» و کارت warn؛ وضعیتِ کل هم', async () => {
  const t = now();
  const headA = Number((await query(`SELECT MAX(server_seq) AS m FROM oplog WHERE account_id=$1`, [shopA])).rows[0].m);
  //  push می‌کند (همین حالا دیده شد) ولی ۳ ساعت است pull ِ موفق ندارد و ۲ op عقب است
  await device(shopA, 'dev-stuck', { cursor: headA - 2, pushAt: t - 60 * 1000, pullAt: t - 3 * HOUR, seen: t - 60 * 1000 });
  const b = (await get()).body;
  assert.equal(b.sync.lag.stuck, 1);
  assert.equal(b.sync.lag.status, 'warn');
  assert.equal(b.sync.status, 'warn');
  assert.notEqual(b.status, 'ok');
  //  pull کرد ⇒ دیگر گیر نیست
  await query(`UPDATE sync_devices SET cursor=$1, last_pull_at=$2 WHERE device_id='dev-stuck'`, [headA, now()]);
  const after = (await get()).body.sync;
  assert.equal(after.lag.stuck, 0);
  assert.equal(after.status, 'ok');
});

test('OPS-04: ⛔ هیچ شناسهٔ دستگاه، حساب یا دکانی در پاسخ نیست', async () => {
  const text = JSON.stringify((await get()).body);
  for (const secret of ['dev-fresh', 'dev-offline', 'dev-stuck', 'dev-gone', shopA, shopB, 'w-1']) {
    assert.ok(!text.includes(secret), secret);
  }
});

test('OPS-04: پرچم دوباره خاموش ⇒ همان پاسخِ پیشین', async () => {
  await liveConfig.set('ops', 'ops.sync_lag', false, 'test');
  const s = (await get()).body.sync;
  assert.equal('lag' in s, false);
  assert.equal(s.status, 'ok');
});
