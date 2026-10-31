'use strict';
/**
 * RSK-07 — شکل‌های یک شماره یک سقف دارند. validate.phone ‎+930…‎، ‎+9300…‎ و ‎+93000…‎ را هم
 * می‌پذیرد و همان را ذخیره می‌کند؛ پیش از این `formsOf` فقط یک صفر برمی‌داشت و هر شکلِ دیگر سقفِ
 * روزانه، فاصلهٔ ارسالِ دوباره، دستهٔ هزارتایی و سطلِ درِ کد را جدا داشت (بازبینِ مستقل:
 * با OTP_DAILY_MAX=2 شش کد رفت). اکنون همه روی `phoneKey`/`phoneForms` ِ validate.
 */
Object.assign(process.env, {
  OTP_DAILY_MAX: '2', OTP_RESEND_SECONDS: '0',
  OTP_SMS_IP_PHONES: '3', OTP_SMS_BLOCK_PHONES: '3', OTP_SMS_DAILY_MAX: '1000',
  RATE_OTP_MAX: '3',
});
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const v = require('../src/lib/validate');
const otp = require('../src/lib/otp');
const rate = require('../src/lib/order-rate');
const config = require('../src/config');
const settings = require('../src/lib/sms-settings');

const realWebhook = otp.senders.webhook;
test.before(async () => {
  await h.start();
  //  پیامکِ «واقعی» بی شبکه تا سنجه‌های SEC-06 هم اجرا شوند
  otp.senders.webhook = async () => {};
  await settings.save({ provider: 'webhook' });
});
test.after(async () => {
  otp.senders.webhook = realWebhook;
  await h.stop();
});

const code = (p) => p.then(() => 'ok', (e) => e.code || e.message);

test('کلیدِ یکتا: همهٔ صفرهای پس از ‎+93‎ برداشته می‌شود؛ شکل‌ها همه شمرده می‌شوند؛ validate.phone دست نخورده', () => {
  const forms = ['+93700111222', '+930700111222', '+9300700111222', '+93000700111222', '+930000700111222'];
  for (const f of forms) {
    assert.equal(v.phoneKey(f), '+93700111222', f);
    assert.deepEqual(v.phoneForms(f), forms, f);
    assert.equal(rate.canon(f), '+93700111222', f);
    assert.deepEqual(rate.formsOf(rate.canon(f)), forms, f);
  }
  //  هر شکلی که validate.phone می‌پذیرد در فهرست است (تا سقفِ ۱۵ رقم)
  for (const raw of ['0700111222', '+930700111222', '00930700111222', '+9300700111222', '+930000700111222']) {
    assert.ok(v.phoneForms(v.phone(raw)).includes(v.phone(raw)), raw);
  }
  //  ذخیره همان شکلِ نوشته‌شده می‌ماند (حسابِ موجود دو تا نمی‌شود)
  assert.equal(v.phone('+9300700111222'), '+9300700111222');
  //  ایمیل و کدِ کشورِ دیگر همان
  assert.deepEqual(v.phoneForms('a@b.co'), ['a@b.co']);
  assert.deepEqual(v.phoneForms('+447700900123'), ['+447700900123']);
});

//  وارونهٔ zz-rev-otp-forms ِ بازبین
test('سقفِ روزانه: ‎+93…‎، ‎+9300…‎ و ‎+930000…‎ با هم همان سقفِ ۲ را دارند، نه سه برابر', async () => {
  const sent = [];
  for (const raw of ['+93700111222', '+93700111222', '+93700111222', '+9300700111222', '+9300700111222',
    '+9300700111222', '+930000700111222', '+930000700111222']) {
    sent.push(`${raw}:${await code(otp.request(v.phone(raw), { purpose: 'store_verify', ip: '1.1.1.1' }))}`);
  }
  assert.equal(sent.filter((x) => x.endsWith(':ok')).length, 2, JSON.stringify(sent));
  assert.ok(sent.slice(2).every((x) => x.endsWith(':otp_daily_limit')), JSON.stringify(sent));
  //  شکلِ غیرِ یکتا نخست: هیچ شکلی «اصلی» نیست، باز همان دو
  const other = [];
  for (const raw of ['+9300700111333', '+9300700111333', '+930000700111333', '+93000700111333', '+93700111333']) {
    other.push(`${raw}:${await code(otp.request(v.phone(raw), { purpose: 'store_verify', ip: '1.1.1.1' }))}`);
  }
  assert.equal(other.filter((x) => x.endsWith(':ok')).length, 2, JSON.stringify(other));
});

test('فاصلهٔ ارسالِ دوباره روی همهٔ شکل‌ها', async () => {
  const prev = config.otp.resendMs;
  config.otp.resendMs = 120_000;
  try {
    assert.equal(await code(otp.request('+93700222333', { purpose: 'login', ip: '1.1.1.2' })), 'ok');
    for (const alt of ['+930700222333', '+9300700222333', '+93000700222333']) {
      assert.equal(await code(otp.request(alt, { purpose: 'login', ip: '1.1.1.2' })), 'otp_resend_wait', alt);
    }
  } finally { config.otp.resendMs = prev; }
});

test('SEC-06: دستهٔ هزارتایی و شمارِ شماره‌های جدا روی کلیدِ یکتا', async () => {
  //  سه شمارهٔ تازه در دستهٔ ‎+93795000…‎ ⇒ چهارمی با هر شکلی بسته است
  for (let i = 1; i <= 3; i++) {
    assert.equal(await code(otp.request(`+93795000${String(i).padStart(3, '0')}`, { ip: `203.0.113.${10 + i}` })), 'ok');
  }
  for (const alt of ['+930795000999', '+9300795000999', '+93000795000998']) {
    assert.equal(await code(otp.request(alt, { ip: '203.0.113.20' })), 'sms_prefix_limit', alt);
  }
  //  دو شکلِ یک گوشی از یک شبکه یک شماره‌اند ⇒ سقفِ IP ِ ۳ زودتر پر نمی‌شود
  const ip = '198.51.100.40';
  assert.equal(await code(otp.request('+93796100001', { ip })), 'ok');
  assert.equal(await code(otp.request('+930796100001', { ip })), 'ok');
  assert.equal(await code(otp.request('+93796200002', { ip })), 'ok');
  assert.equal(await code(otp.request('+93796300003', { ip })), 'ok', 'دو شمارهٔ جدای دیگر هنوز زیرِ سقف‌اند');
  assert.equal(await code(otp.request('+93796400004', { ip })), 'sms_ip_limit');
});

test('سطلِ درِ کد (otpLimit): ‎0700…‎، ‎+93700…‎، ‎0093700…‎ و ‎+9300700…‎ یک سطل‌اند', async () => {
  require('../src/middleware/ratelimit').resetAll();
  const forms = ['0700333444', '+93700333444', '0093700333444', '+9300700333444'];
  const got = [];
  for (const phone of forms) {
    const r = await h.post('/api/auth/otp/request', { phone });
    got.push(r.status === 200 ? 'ok' : r.body.error.code);
  }
  //  دو کد (سقفِ روزانه)، سومی سقفِ روزانهٔ کد، چهارمی خودِ سطل (RATE_OTP_MAX=3)
  assert.deepEqual(got, ['ok', 'ok', 'otp_daily_limit', 'rate_limited'], JSON.stringify(got));
});
