'use strict';
/**
 * SEC-06 — سقفِ هزینهٔ پیامک و ضدِ SMS pumping در lib/otp.js.
 *
 *   - یک IP: یازدهمین شمارهٔ جدا در ساعت ⇒ ۴۲۹ sms_ip_limit؛ همان شماره و IP ِ دیگر آزاد.
 *   - یک بلوکِ هزارتایی: یازدهمین شمارهٔ جدا ⇒ sms_prefix_limit؛ بلوکِ دیگر آزاد.
 *   - شمارهٔ خارجی: بیست‌ویکمین شمارهٔ جدا در ساعت ⇒ sms_foreign_limit؛ شمارهٔ داخلی آزاد.
 *   - سقفِ روزانهٔ سراسری از دفترِ sms_usage ⇒ sms_daily_ceiling؛ ردیفِ کهنه‌تر از ۲۴ ساعت نه.
 *   - راهِ `log` و ایمیل بی‌هزینه‌اند: هیچ سقفی نمی‌گیرند (آزمون‌های دیگر و سرورِ بی پیامک).
 *   - هر تلاشِ پیامک (موفق/ناموفق) در sms_usage با دکان می‌نشیند؛ ایمیل نه.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');
const otp = require('../src/lib/otp');
const settings = require('../src/lib/sms-settings');

const G = otp.SMS_GUARD;
const realWebhook = otp.senders.webhook;
let sent = [];
let failNext = false;
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
  //  پیامکِ «واقعی» بی شبکه: راهِ webhook با فرستندهٔ ساختگی
  otp.senders.webhook = async (to) => {
    if (failNext) { failNext = false; throw new Error('provider down'); }
    sent.push(to);
  };
  await settings.save({ provider: 'webhook' });
});
test.after(async () => {
  otp.senders.webhook = realWebhook;
  await h.stop();
});
test.beforeEach(() => { sent = []; });

const code = (p) => p.then(() => 'ok', (e) => e.code || e.message);
const pad = (i, n = 3) => String(i).padStart(n, '0');

test('IP: یازدهمین شمارهٔ جدا از یک IP در ساعت ⇒ sms_ip_limit؛ همان شماره و IP ِ دیگر آزاد', async () => {
  assert.equal(G.ipPhones, 10);
  const ip = '198.51.100.7';
  //  هر شماره در بلوکِ خودش، تا فقط سنجهٔ IP بگیرد
  for (let i = 0; i < G.ipPhones; i++) assert.equal(await code(otp.request(`+93791${pad(i)}123`, { ip })), 'ok');
  assert.equal(sent.length, G.ipPhones);
  assert.equal(await code(otp.request('+93791999123', { ip })), 'sms_ip_limit');
  assert.equal(sent.length, G.ipPhones, 'پیامکی نرفت');
  assert.equal((await one(`SELECT COUNT(*)::int AS n FROM otp_codes WHERE destination='+93791999123'`)).n, 0, 'ردیفی ساخته نشد');
  //  همان شمارهٔ پیشین (هدفِ دیگر) به شمارِ «جدا» نمی‌افزاید
  assert.equal(await code(otp.request('+93791000123', { ip, purpose: 'reauth' })), 'ok');
  assert.equal(await code(otp.request('+93791999123', { ip: '198.51.100.8' })), 'ok');
});

test('بلوک: یازدهمین شمارهٔ جدا در یک هزارتایی ⇒ sms_prefix_limit؛ بلوکِ دیگر آزاد', async () => {
  for (let i = 0; i < G.blockPhones; i++) {
    assert.equal(await code(otp.request(`+93792000${pad(i)}`, { ip: `203.0.113.${i + 1}` })), 'ok');
  }
  assert.equal(await code(otp.request('+93792000999', { ip: '203.0.113.200' })), 'sms_prefix_limit');
  assert.equal(await code(otp.request('+93792001999', { ip: '203.0.113.201' })), 'ok');
});

test('خارجی: بیست‌ویکمین شمارهٔ خارجیِ جدا در ساعت ⇒ sms_foreign_limit؛ شمارهٔ داخلی آزاد', async () => {
  for (let i = 0; i < G.foreignPhones; i++) {
    assert.equal(await code(otp.request(`+4477${pad(i)}00123`, { ip: `192.0.2.${i + 1}` })), 'ok');
  }
  assert.equal(await code(otp.request('+4477999001234', { ip: '192.0.2.200' })), 'sms_foreign_limit');
  assert.equal(await code(otp.request('+93793000123', { ip: '192.0.2.201' })), 'ok');
});

test('سقفِ روزانهٔ سراسری از sms_usage؛ کهنه‌تر از ۲۴ ساعت شمرده نمی‌شود؛ log و ایمیل بی‌سقف', async () => {
  const t = now();
  const fill = async (n, at, ok = true) => query(
    `INSERT INTO sms_usage (id, shop_id, app, purpose, via, ok, parts, cost_minor, at)
     SELECT 'smsu_seed_' || g || '_' || $3::text || '_' || $4::text, '', 'shop', 'login', 'webhook', $4::boolean, 1, 0, $2::bigint
       FROM generate_series(1, $1::int) AS g`,
    [n, at, String(at), ok]
  );
  await fill(G.dailyMax + 5, t - 25 * 3600_000);
  await fill(G.dailyMax, t - 3600_000, false);
  assert.equal(await code(otp.request('+93794000123', { ip: '192.0.2.50' })), 'ok', 'کهنه و ناموفق شمرده نمی‌شوند');
  await fill(G.dailyMax, t - 3600_000);
  assert.equal(await code(otp.request('+93794001123', { ip: '192.0.2.51' })), 'sms_daily_ceiling');
  //  ایمیل و راهِ log هزینه ندارند ⇒ همان سقف نمی‌گیرد
  assert.equal(await code(otp.request('ceiling@test.local', { ip: '192.0.2.52' })), 'ok');
  await settings.save({ provider: 'log' });
  try {
    assert.equal(await code(otp.request('+93794002123', { ip: '192.0.2.53' })), 'ok');
  } finally {
    await settings.save({ provider: 'webhook' });
  }
  await query(`DELETE FROM sms_usage WHERE id LIKE 'smsu_seed_%'`);
});

test('دفتر: هر تلاشِ پیامک با دکان، هدف و راه می‌نشیند؛ ناموفق ok=false؛ ایمیل نه؛ ویترین دکانش را می‌دهد', async () => {
  const before = (await one('SELECT COUNT(*)::int AS n FROM sms_usage')).n;
  await otp.request('+93795000123', { ip: '192.0.2.60', purpose: 'login', app: 'shop', shopId: 'shp_x' });
  let r = await one(`SELECT * FROM sms_usage ORDER BY at DESC, id DESC LIMIT 1`);
  assert.deepEqual([r.shop_id, r.app, r.purpose, r.via, r.ok], ['shp_x', 'shop', 'login', 'webhook', true]);
  failNext = true;
  assert.equal(await code(otp.request('+93795001123', { ip: '192.0.2.61' })), 'delivery_failed');
  r = await one(`SELECT * FROM sms_usage ORDER BY at DESC, id DESC LIMIT 1`);
  assert.equal(r.ok, false);
  await otp.request('ledger@test.local', { ip: '192.0.2.62' });
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM sms_usage')).n, before + 2, 'ایمیل در دفترِ پیامک نیست');
  //  هیچ ستونی شماره یا کد ندارد
  assert.ok(!JSON.stringify(r).includes('95001123'));

  //  ویترین: کدِ تأییدِ مشتری با شناسهٔ همان دکان
  const u = await h.newUser('دکانِ پیامک', 'shop');
  const shop = (await h.post('/api/shop', { name: 'دکانِ پیامک' }, { token: u.accessToken })).body.shop;
  for (const key of ['shop.orders', 'shop.storefront']) {
    const f = await h.put('/api/admin/pump/live-config', { scope: shop.id, key, value: true }, { token: admin });
    assert.equal(f.status, 200, JSON.stringify(f.body));
  }
  const slug = `sms-${Date.now().toString(36)}`;
  const put = await h.put('/api/shop/storefront', { slug, enabled: true, title: 'ویترین', contactPhone: '0700000001' },
    { token: u.accessToken });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  const v = await h.post(`/api/store/${slug}/verify/start`, { phone: '0796000123' });
  assert.equal(v.status, 200, JSON.stringify(v.body));
  r = await one(`SELECT * FROM sms_usage WHERE purpose = 'store_order' ORDER BY at DESC LIMIT 1`);
  assert.equal(r.shop_id, shop.id);
});
