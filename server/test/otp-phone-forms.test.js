'use strict';
/**
 * RSK-07 — شکل‌های یک شماره یک کلید دارند. validate.phone ‎+930…‎، ‎+9300…‎ و ‎+93000…‎ را هم
 * می‌پذیرد و همان را ذخیره می‌کند؛ هر شمردن/قفلی که شماره (دادهٔ تماس: فهرستِ سیاه، چت، …) کلیدش است
 * روی `phoneKey`/`phoneForms` ِ validate. «فقط ایمیل» (۱۴۰۵/۰۸/۲۳): شماره دیگر هرگز کد نمی‌گیرد —
 * حتی با سرویسِ پیامکِ «واقعیِ» تنظیم‌شده.
 */
Object.assign(process.env, { OTP_RESEND_SECONDS: '0' });
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const v = require('../src/lib/validate');
const otp = require('../src/lib/otp');
const settings = require('../src/lib/sms-settings');

const realWebhook = otp.senders.webhook;
test.before(async () => {
  await h.start();
  //  سرویسِ پیامکِ «واقعی» تنظیم‌شده (بی شبکه): باز هم هیچ کدی به شماره نمی‌رود
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

//  «فقط ایمیل» (۱۴۰۵/۰۸/۲۳): سقفِ روزانه، فاصله، دستهٔ هزارتایی (SEC-06) و سطلِ درِ کدِ **شماره** با خودِ کدِ پیامکی رفتند
//  ⇐ به‌جایش: هیچ شکلی از شماره کد نمی‌گیرد (پیش از هر ردیف و هر سطل‌شماری) — `email-only-auth.test.js`
test('هیچ شکلی از یک شماره کد نمی‌گیرد: ۴۰۰ ِ email_required و `otp.request` ِ email_only، بی ردیف', async () => {
  require('../src/middleware/ratelimit').resetAll();
  for (const phone of ['0700333444', '+93700333444', '0093700333444', '+9300700333444']) {
    const r = await h.post('/api/auth/otp/request', { phone });
    assert.equal(r.status, 400, phone);
    assert.equal(r.body.error.code, 'email_required');
    assert.equal(await code(otp.request(v.phone(phone), { purpose: 'store_verify', ip: '1.1.1.1' })), 'email_only');
  }
  const { many } = require('../src/db');
  assert.deepEqual(await many("SELECT 1 FROM otp_codes WHERE destination LIKE '+93%'"), []);
});
