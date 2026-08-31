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
const settings = require('../src/lib/sms-settings');

let server;
let seen = [];
let base = '';

test.before(async () => {
  server = http.createServer((req, res) => {
    let body = '';
    req.on('data', c => { body += c; });
    req.on('end', () => {
      seen.push({ method: req.method, url: req.url, headers: req.headers, body });
      if (req.url.includes('/soft-fail')) {
        // ۲۰۰ ولی خطا در بدنه — رفتار چند سرویس واقعی
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end('{"error":4015,"description":"Insufficient credits."}');
        return;
      }
      if (req.url.includes('/fail')) {
        res.writeHead(401, { 'Content-Type': 'application/json' });
        res.end('{"error":4002,"description":"No API key found in request."}');
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

/**
 * تنظیمات را مستقیم می‌نشانیم، بدون دیتابیس.
 *
 * این تست‌ها فقط شکلِ درخواست را می‌سنجند؛ خواندن از دیتابیس جای
 * دیگری سنجیده می‌شود و اینجا فقط تست را کند و شکننده می‌کرد.
 */
function set(patch) {
  const base = {
    provider: 'sms', url: '', method: 'POST', key: '', sender: '',
    headers: '', body: '', template: '',
  };
  const value = { ...base, ...patch };
  value.method = String(value.method).toUpperCase();
  settings.current = async () => value;
}

test('POST: کلید در سرآیند و پارامترها در بدنه', async () => {
  reset();
  set({
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
  set({
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
  set({
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
  set({
    url: `${base}/fail`, method: 'POST', key: 'x', sender: '1',
    headers: '', body: '{"to":"{to}"}',
  });

  await assert.rejects(
    () => senders.sms('0790000004', '1', 'x'),
    (err) => {
      assert.match(err.message, /401/);
      assert.match(err.message, /No API key/);
      return true;
    }
  );
});

test('پاسخ ۲۰۰ ولی با خطا در بدنه، «فرستاده شد» حساب نمی‌شود', async () => {
  reset();
  //  EasySendSMS و چند سرویس دیگر گاهی ۲۰۰ می‌دهند و خطا را داخل بدنه
  //  می‌گذارند. اگر این را نمی‌سنجیدیم، به کاربر می‌گفتیم کد رفت و نرفته بود.
  set({
    url: `${base}/soft-fail`, method: 'POST', key: 'k', sender: 's',
    headers: '', body: '{"to":"{to}"}',
  });

  await assert.rejects(
    () => senders.sms('0790000005', '1', 'x'),
    /Insufficient credits/
  );
});

test('شکل واقعی EasySendSMS همان‌طور که هست فرستاده می‌شود', async () => {
  reset();
  set({
    url: `${base}/v1/rest/sms/send`,
    method: 'POST',
    key: 'APIKEY-XYZ',
    sender: 'Tohid',
    headers: '{"apikey":"{key}","Accept":"application/json"}',
    body: '{"from":"{sender}","to":"{to_plain}","text":"{message}","type":"1"}',
  });

  //  سرور شماره را با + نگه می‌دارد؛ EasySendSMS آن را قبول نمی‌کند
  await senders.sms('+93790000000', '445566', 'کد ورود توحید: 445566');

  const got = seen[0];
  assert.equal(got.headers.apikey, 'APIKEY-XYZ');
  assert.equal(got.headers.accept, 'application/json');
  assert.deepEqual(JSON.parse(got.body), {
    from: 'Tohid',
    to: '93790000000',
    text: 'کد ورود توحید: 445566',
    //  «۱» یعنی یونیکد. با «۰» متن فارسی به هم می‌ریزد.
    type: '1',
  });
});

test('بدون نشانی سرویس، با پیام روشن رد می‌شود', async () => {
  set({ url: '' });
  await assert.rejects(() => senders.sms('079', '1', 'x'), /نشانی سرویس پیامک تنظیم نشده/);
});

test('JSON خرابِ تنظیمات، با نامِ همان متغیر گفته می‌شود', async () => {
  set({
    url: `${base}/sendsms`, method: 'POST', key: 'x', sender: '1',
    headers: '{این JSON نیست}', body: '',
  });
  await assert.rejects(() => senders.sms('079', '1', 'x'), /SMS_API_HEADERS/);
});
