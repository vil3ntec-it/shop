'use strict';
/**
 * سرویس‌های بیرونی در پنلِ مدیر — OPS-04@29 (§۲۹.۸).
 *
 *   ۱) فقط مدیر؛ بی توکن ۴۰۱، کاربرِ دکان راه ندارد.
 *   ۲) پیامک، ایمیل، پوش، پرداخت و عکس — هر پنج با حالتِ واقعی.
 *   ۳) ⛔ راهِ `log` همیشه «mock» است، هرگز «live»؛ راهِ ناشناخته هم «live» نیست.
 *   ۴) راهِ واقعیِ ناقص «incomplete» با نامِ فیلدِ کم؛ کامل ⇒ «live».
 *   ۵) پرداختِ آنلاین «none» (D-08)، نه «mock».
 *   ۶) ⛔ هیچ نشانی، کلید، رمز یا میزبانی در پاسخ نیست.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const config = require('../src/config');
const { query, newId, now } = require('../src/db');
const health = require('../src/lib/system-health');
const sms = require('../src/lib/sms-settings');
const mailer = require('../src/lib/mailer');
const push = require('../src/lib/push');
const pipeline = require('../src/lib/image-pipeline');

let admin = '';
const get = (token) => h.get('/api/admin/system/providers', { token });
const byId = (b) => Object.fromEntries(b.providers.map((p) => [p.id, p]));

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

test('فقط مدیر: بی توکن ۴۰۱، کاربرِ دکان راه ندارد', async () => {
  assert.equal((await get(null)).status, 401);
  const u = await h.newUser('دکان‌دارِ کنجکاو', 'shop');
  const r = await get(u.accessToken);
  assert.ok([401, 403].includes(r.status), String(r.status));
});

test('OPS-04@29: پنج سرویس، پیش‌فرضِ آزمون «آزمایشی» است نه «زنده»', async () => {
  const r = await get(admin);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.headers.get('cache-control'), 'no-store');
  const b = r.body;
  assert.deepEqual(b.providers.map((p) => p.id), ['sms', 'email', 'push', 'payment', 'image_ai']);
  for (const p of b.providers) assert.ok(health.PROVIDER_STATES.includes(p.state), `${p.id}: ${p.state}`);
  assert.equal(b.production, false);
  const p = byId(b);
  assert.deepEqual([p.sms.mode, p.sms.state], ['log', 'mock']);
  assert.deepEqual([p.email.mode, p.email.state], ['log', 'mock']);
  assert.equal(p.push.state, 'off');
  assert.deepEqual([p.payment.mode, p.payment.state, p.payment.decision], ['cod', 'none', 'D-08']);
  assert.equal(p.image_ai.state, 'none');
  assert.deepEqual(p.image_ai.steps,
    pipeline.STEPS.filter((s) => s.status === 'blocked_external').map((s) => s.id));
  assert.ok(p.image_ai.steps.length >= 1);
  //  شمارش با فهرست یکی است
  const sum = Object.values(b.counts).reduce((a, n) => a + n, 0);
  assert.equal(sum, b.providers.length);
  assert.equal(b.counts.mock, 2);
  assert.equal(b.counts.live, 0);
});

test('⛔ `log` و راهِ ناشناخته هرگز «live» نیستند', () => {
  assert.equal(health.smsState({ provider: 'log', url: 'https://sms.example/x' }).state, 'mock');
  assert.equal(health.smsState({ provider: '' }).state, 'mock');
  assert.equal(health.smsState({ provider: 'carrier-pigeon', url: 'https://x' }).state, 'incomplete');
  const ready = () => ({ ok: true, missing: [] });
  assert.equal(health.emailState({ provider: 'log' }, ready).state, 'mock');
  assert.equal(health.emailState({ provider: 'fax' }, ready).state, 'incomplete');
});

test('راهِ واقعیِ ناقص «incomplete» با نامِ فیلد؛ کامل ⇒ «live»', async () => {
  try {
    //  پیامک: sms بی نشانی ⇒ ناقص؛ با نشانی ⇒ زنده
    await sms.save({ provider: 'sms', url: '' });
    let p = byId((await get(admin)).body);
    assert.equal(p.sms.state, 'incomplete');
    assert.deepEqual(p.sms.missing, ['نشانیِ سرویسِ پیامک']);
    await sms.save({ provider: 'sms', url: 'https://sms.example.test/send?to={to}', key: 'Sms-Key-Never-Shown-8812' });
    //  ایمیل: smtp بی رمز ⇒ ناقص
    await mailer.save({ provider: 'smtp', host: 'smtp.never-shown.test', user: 'u@x.test', pass: '', from: 'from@x.test' });
    p = byId((await get(admin)).body);
    assert.equal(p.sms.state, 'live');
    assert.equal(p.email.state, 'incomplete');
    assert.ok(p.email.missing.length === 1 && /رمز/.test(p.email.missing[0]), JSON.stringify(p.email.missing));
    await mailer.save({ pass: 'Smtp-Pass-Never-Shown-5531' });
    //  پوش: روشن بی حسابِ سرویس ⇒ ناقص
    await push.save({ enabled: true });
    p = byId((await get(admin)).body);
    assert.equal(p.email.state, 'live');
    assert.equal(p.push.state, 'incomplete');
    //  واتس‌اپ بی توکن ⇒ ناقص با نامِ هر دو متغیر
    await sms.save({ provider: 'whatsapp' });
    p = byId((await get(admin)).body);
    assert.equal(p.sms.state, 'incomplete');
    assert.deepEqual(p.sms.missing, ['WHATSAPP_TOKEN', 'WHATSAPP_PHONE_ID']);
  } finally {
    await sms.save({ provider: 'log', url: '', key: '' });
    await mailer.save({ provider: 'log', host: '', pass: '' });
    await push.save({ enabled: false });
  }
});

test('⛔ هیچ نشانی، کلید، رمز یا میزبانی در پاسخ نیست', async () => {
  try {
    await sms.save({ provider: 'sms', url: 'https://sms.never-shown.test/api', key: 'Sms-Key-Never-Shown-8812', sender: '+93700111222' });
    await mailer.save({ provider: 'smtp', host: 'smtp.never-shown.test', user: 'smtp-user@never-shown.test', pass: 'Smtp-Pass-Never-Shown-5531', from: 'from@never-shown.test' });
    const text = JSON.stringify((await get(admin)).body);
    for (const s of ['never-shown', 'Sms-Key', 'Smtp-Pass', '+93700111222', 'https://', '@']) {
      assert.ok(!text.includes(s), `در پاسخ آمد: ${s}`);
    }
    //  و هیچ مقدارِ رشته‌ایِ رازگونهٔ config
    const secrets = [];
    const walk = (o, key = '') => {
      if (!o || typeof o !== 'object') return;
      for (const [k, v] of Object.entries(o)) {
        if (typeof v === 'string' && v.length >= 6 && /secret|pass|token|key|url|dsn|smtp|dir|path/i.test(`${key}.${k}`)) secrets.push(v);
        else walk(v, k);
      }
    };
    walk(config);
    for (const s of secrets) assert.ok(!text.includes(s), `مقدارِ config در پاسخ آمد (${s.length} نویسه)`);
  } finally {
    await sms.save({ provider: 'log', url: '', key: '', sender: '' });
    await mailer.save({ provider: 'log', host: '', user: '', pass: '', from: '' });
  }
});
