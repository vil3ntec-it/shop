'use strict';
/**
 * تنظیم سرویس پیامک از برنامه‌ی مدیریت.
 *
 * چیزی که باید ثابت شود: مدیر می‌تواند تنظیم کند، کاربر عادی نمی‌تواند،
 * و کلید سرویس هرگز کامل بیرون نمی‌رود.
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

async function token() {
  const r = await h.post('/api/admin/login', { username: 'admin', password: 'Admin!12345' });
  return r.body.token;
}

test('مدیر تنظیمات پیامک را می‌گذارد و همان برمی‌گردد', async () => {
  const t = await token();
  const put = await h.put('/api/admin/sms', {
    provider: 'sms',
    url: 'https://restapi.easysendsms.app/v1/rest/sms/send',
    method: 'POST',
    key: 'SECRET-KEY-9876',
    sender: 'Tohid',
    headers: '{"apikey":"{key}","Accept":"application/json"}',
    body: '{"from":"{sender}","to":"{to_plain}","text":"{message}","type":"1"}',
  }, { token: t });

  assert.equal(put.status, 200);
  assert.equal(put.body.sms.provider, 'sms');
  assert.equal(put.body.sms.sender, 'Tohid');

  const get = await h.get('/api/admin/sms', { token: t });
  assert.equal(get.body.sms.url, 'https://restapi.easysendsms.app/v1/rest/sms/send');
});

test('کلید سرویس هرگز کامل برنمی‌گردد', async () => {
  const t = await token();
  await h.put('/api/admin/sms', { key: 'SECRET-KEY-9876' }, { token: t });

  const get = await h.get('/api/admin/sms', { token: t });
  const raw = JSON.stringify(get.body);
  assert.ok(!raw.includes('SECRET-KEY-9876'), 'کلید نباید در پاسخ باشد');
  assert.equal(get.body.sms.keySet, true);
  assert.equal(get.body.sms.keyHint, '••••9876');
});

test('فرستادن بدون کلید، کلیدِ ذخیره‌شده را پاک نمی‌کند', async () => {
  const t = await token();
  await h.put('/api/admin/sms', { key: 'KEEP-ME-4321' }, { token: t });

  //  برنامه‌ی مدیریت کلید را نمی‌بیند، پس وقتی مدیر فقط نامِ فرستنده را
  //  عوض می‌کند نباید ندانسته کلید را پاک کند
  await h.put('/api/admin/sms', { sender: 'TohidNew' }, { token: t });

  const get = await h.get('/api/admin/sms', { token: t });
  assert.equal(get.body.sms.keySet, true);
  assert.equal(get.body.sms.keyHint, '••••4321');
  assert.equal(get.body.sms.sender, 'TohidNew');
});

test('پاک کردن کلید فقط با درخواست صریح', async () => {
  const t = await token();
  await h.put('/api/admin/sms', { key: 'GOES-AWAY-1111' }, { token: t });
  await h.put('/api/admin/sms', { clearKey: true }, { token: t });

  const get = await h.get('/api/admin/sms', { token: t });
  assert.equal(get.body.sms.keySet, false);
});

test('JSON خراب پیش از ذخیره رد می‌شود', async () => {
  const t = await token();
  const bad = await h.put('/api/admin/sms', { headers: '{این JSON نیست}' }, { token: t });
  assert.equal(bad.status, 400);
});

test('کاربر عادی به تنظیمات پیامک نمی‌رسد', async () => {
  const user = await h.newUser('کاربر ساده');

  const get = await h.get('/api/admin/sms', { token: user.accessToken });
  assert.equal(get.status, 401);

  const put = await h.put('/api/admin/sms', { url: 'https://evil.example' }, { token: user.accessToken });
  assert.equal(put.status, 401);

  const anon = await h.get('/api/admin/sms');
  assert.equal(anon.status, 401);
});

test('آزمایشِ ارسال، خطای سرویس را با متن خودش برمی‌گرداند', async () => {
  const t = await token();
  //  نشانی‌ای که وجود ندارد: باید ok:false بدهد نه خطای ۵۰۰
  await h.put('/api/admin/sms', {
    provider: 'sms',
    url: 'http://127.0.0.1:9/none',
    key: 'x', sender: 's',
    headers: '{}', body: '{"to":"{to}"}',
  }, { token: t });

  const out = await h.post('/api/admin/sms/test', { to: '0790000000' }, { token: t });
  assert.equal(out.status, 200);
  assert.equal(out.body.ok, false);
  assert.ok(out.body.error, 'دلیلش باید گفته شود');
});
