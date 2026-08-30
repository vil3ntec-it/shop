'use strict';
/**
 * سرویس پیامک — شکل درخواست از تنظیمات می‌آید، نه از کد.
 *
 * این تست یک سرویس ساختگی بالا می‌آورد و می‌سنجد که دقیقاً همان چیزی
 * فرستاده شود که در .env نوشته شده. اگر روزی جاگذاری‌ها بشکنند، اینجا
 * معلوم می‌شود نه روی گوشی کاربر.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const http = require('node:http');

process.env.NODE_ENV = 'test';
const config = require('../src/config');
const { senders } = require('../src/lib/otp');

let server;
let seen = [];
let base = '';

test.before(async () => {
  server = http.createServer((req, res) => {
    let body = '';
    req.on('data', c => { body += c; });
    req.on('end', () => {
      seen.push({ method: req.method, url: req.url, headers: req.headers, body });
      if (req.url.includes('/fail')) {
        res.writeHead(401, { 'Content-Type': 'application/json' });
        res.end('{"error":"bad key"}');
        return;
      }
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end('{"status":"ok"}');
    });
  });
  await new Promise(r => server.listen(0, '127.0.0.1', r));
  base = `http://127.0.0.1:${server.address().port}`;
});

test.after(async () => { await new Promise(r => server.close(r)); });

function reset() { seen = []; }

test('POST: کلید در سرآیند و پارامترها در بدنه', async () => {
  reset();
  Object.assign(config.sms, {
    url: `${base}/sendsms`,
    method: 'POST',
    key: 'KEY-123',
    sender: '50004000',
    headers: '{"Authorization":"Bearer {key}"}',
    body: '{"receptor":"{to}","sender":"{sender}","message":"{message}"}',
  });

  const out = await senders.sms('0790000001', '123456', 'کد ورود شما: 123456');
  assert.equal(out.delivered, true);

  const got = seen[0];
  assert.equal(got.method, 'POST');
  assert.equal(got.headers.authorization, 'Bearer KEY-123');
  assert.equal(got.headers['content-type'], 'application/json');

  const sent = JSON.parse(got.body);
  assert.deepEqual(sent, {
    receptor: '0790000001',
    sender: '50004000',
    message: 'کد ورود شما: 123456',
  });
  //  کلید نباید در بدنه لو برود وقتی جایش سرآیند است
  assert.ok(!got.body.includes('KEY-123'));
});

test('GET: کلید داخل آدرس و پارامترها در query', async () => {
  reset();
  Object.assign(config.sms, {
    url: `${base}/v1/{key}/send.json`,
    method: 'GET',
    key: 'KEY-456',
    sender: '30001111',
    headers: '',
    body: 'receptor={to}&sender={sender}&message={message}',
  });

  await senders.sms('0790000002', '654321', 'کد: 654321');

  const got = seen[0];
  assert.equal(got.method, 'GET');
  assert.ok(got.url.startsWith('/v1/KEY-456/send.json?'), got.url);
  const q = new URLSearchParams(got.url.split('?')[1]);
  assert.equal(q.get('receptor'), '0790000002');
  assert.equal(q.get('sender'), '30001111');
  //  متن فارسی باید encode شده باشد وگرنه آدرس می‌شکند
  assert.equal(q.get('message'), 'کد: 654321');
});

test('کلید می‌تواند داخل خودِ بدنه هم برود', async () => {
  reset();
  Object.assign(config.sms, {
    url: `${base}/sendsms`,
    method: 'POST',
    key: 'KEY-789',
    sender: '2000',
    headers: '',
    body: '{"apikey":"{key}","to":"{to}","text":"{message}"}',
  });

  await senders.sms('0790000003', '111222', 'متن');
  assert.deepEqual(JSON.parse(seen[0].body), {
    apikey: 'KEY-789', to: '0790000003', text: 'متن',
  });
});

test('پاسخ خطا با متنِ خودِ سرویس برمی‌گردد، نه فقط یک شماره', async () => {
  reset();
  Object.assign(config.sms, {
    url: `${base}/fail`, method: 'POST', key: 'x', sender: '1',
    headers: '', body: '{"to":"{to}"}',
  });

  await assert.rejects(
    () => senders.sms('0790000004', '1', 'x'),
    (err) => {
      assert.match(err.message, /401/);
      assert.match(err.message, /bad key/);
      return true;
    }
  );
});

test('بدون SMS_API_URL، با پیام روشن رد می‌شود', async () => {
  Object.assign(config.sms, { url: '' });
  await assert.rejects(() => senders.sms('079', '1', 'x'), /SMS_API_URL/);
});

test('JSON خرابِ تنظیمات، با نامِ همان متغیر گفته می‌شود', async () => {
  Object.assign(config.sms, {
    url: `${base}/sendsms`, method: 'POST', key: 'x', sender: '1',
    headers: '{این JSON نیست}', body: '',
  });
  await assert.rejects(() => senders.sms('079', '1', 'x'), /SMS_API_HEADERS/);
});
