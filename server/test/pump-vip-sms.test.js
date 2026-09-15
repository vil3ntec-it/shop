'use strict';
/**
 * ══ کدِ شش‌رقمی با پیامک ═══════════════════════════════════════════════
 * «باتِ ارسالِ خودکارِ کدِ شش‌رقمی داره یا نداره؟» — حالا دارد: شمارهٔ موبایل
 * بده، کد همان لحظه با همان سرویسِ پیامکِ کدِ ورود می‌رود.
 *   ۱) پیامک با همان کدی می‌رود که به مدیر نشان داده شد.
 *   ۲) کد در دیتابیس ذخیره نمی‌شود — فقط شماره و «رفت».
 *   ۳) سرویسِ خراب ⇒ کد ساخته می‌شود ولی smsStatus=failed با دلیل.
 *   ۴) شمارهٔ بی‌ربط رد می‌شود.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const http = require('node:http');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');
const settings = require('../src/lib/sms-settings');

let smsServer; let smsBase = ''; let seen = [];

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]
  );
  smsServer = http.createServer((req, res) => {
    let body = '';
    req.on('data', c => { body += c; });
    req.on('end', () => {
      seen.push({ url: req.url, body });
      if (req.url.includes('/fail')) { res.writeHead(500); res.end('{"error":"down"}'); return; }
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end('{"status":"ok"}');
    });
  });
  await new Promise(r => smsServer.listen(0, '127.0.0.1', r));
  smsBase = `http://127.0.0.1:${smsServer.address().port}`;
});
test.after(async () => { await new Promise(r => smsServer.close(r)); await h.stop(); });

function smsAt(path) {
  settings.current = async () => ({
    provider: 'sms', url: smsBase + path, method: 'POST', key: 'K', sender: '5000',
    headers: '', body: '{"to":"{to}","text":"{message}"}', template: '',
  });
}

async function adminToken() {
  const r = await h.post('/api/admin/login', { username: 'admin', password: 'Admin!12345' });
  return r.body.token;
}

test('شمارهٔ موبایل ⇒ کد همان لحظه پیامک می‌شود، با همان کد', async () => {
  seen = []; smsAt('/send');
  const t = await adminToken();
  const r = await h.post('/api/admin/pump/vip-codes',
    { plan: 'custom', days: 30, phone: '+93 790 000 000' }, { token: t });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  assert.equal(r.body.smsStatus, 'sent');
  assert.match(r.body.code, /^\d{6}$/);

  assert.equal(seen.length, 1);
  const sent = JSON.parse(seen[0].body);
  assert.equal(sent.to, '+93790000000');
  assert.ok(sent.text.includes(r.body.code), 'همان کد در پیامک است');

  const row = await one('SELECT * FROM station_vip_codes WHERE id=$1', [r.body.vipCode.id]);
  assert.equal(row.phone, '+93790000000');
  assert.equal(row.sms_status, 'sent');
  assert.ok(!JSON.stringify(row).includes(r.body.code), 'کدِ خام در دیتابیس نیست');
});

test('سرویسِ پیامک خراب ⇒ کد ساخته می‌شود ولی می‌گوید نرفت', async () => {
  seen = []; smsAt('/fail');
  const t = await adminToken();
  const r = await h.post('/api/admin/pump/vip-codes', { plan: 'custom', days: 30, phone: '0790000001' }, { token: t });
  assert.equal(r.status, 201);
  assert.equal(r.body.smsStatus, 'failed');
  assert.ok(r.body.smsError.length > 0);
  assert.match(r.body.code, /^\d{6}$/);
});

test('بی شماره هیچ پیامکی نمی‌رود؛ شمارهٔ بی‌ربط رد می‌شود', async () => {
  seen = []; smsAt('/send');
  const t = await adminToken();
  const none = await h.post('/api/admin/pump/vip-codes', { plan: 'custom', days: 30 }, { token: t });
  assert.equal(none.status, 201);
  assert.equal(none.body.smsStatus, 'none');
  assert.equal(seen.length, 0);

  const bad = await h.post('/api/admin/pump/vip-codes', { plan: 'custom', days: 30, phone: 'abc' }, { token: t });
  assert.equal(bad.status, 400);
  assert.equal(bad.body.error.code, 'bad_phone');
});
