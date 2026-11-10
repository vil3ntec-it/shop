'use strict';
/**
 * تصمیمِ ۱-۱ (D-09 ب، ۱۴۰۵/۰۸/۱۹): «پیامک فقط برای کدِ ورود و خبرِ فوری، با سقفِ ۱۰۰ پیامک در روز برای کلِ سرور».
 * سقفِ روزانهٔ سراسری (`lib/otp.js` ‹dailyCeiling›) برای شمارهٔ آشنا `dailyMax + knownExtra` و برای ناآشنا `dailyMax` است؛
 * پیش‌فرضِ بی متغیرِ محیطی پس روی‌هم ۱۰۰ (۸۰ + ۲۰) است — پیش از تصمیم ۲۰۰۰ + ۵۰۰. متغیرِ صریح همان را جا می‌اندازد.
 */
const test = require('node:test');
const assert = require('node:assert/strict');

test('پیش‌فرضِ سقفِ روزانهٔ پیامکِ سرور ۱۰۰ است (۸۰ ناآشنا + ۲۰ سهمِ آشنا)؛ متغیرِ محیطی جا می‌اندازد', () => {
  const keep = { max: process.env.OTP_SMS_DAILY_MAX, extra: process.env.OTP_SMS_KNOWN_EXTRA };
  const fresh = () => { delete require.cache[require.resolve('../src/config')]; return require('../src/config').otp.smsGuard; };
  try {
    delete process.env.OTP_SMS_DAILY_MAX;
    delete process.env.OTP_SMS_KNOWN_EXTRA;
    const g = fresh();
    assert.deepEqual([g.dailyMax, g.knownExtra, g.dailyMax + g.knownExtra], [80, 20, 100]);
    process.env.OTP_SMS_DAILY_MAX = '300';
    process.env.OTP_SMS_KNOWN_EXTRA = '0';
    assert.deepEqual([fresh().dailyMax, fresh().knownExtra], [300, 0]);
  } finally {
    for (const [k, v] of [['OTP_SMS_DAILY_MAX', keep.max], ['OTP_SMS_KNOWN_EXTRA', keep.extra]]) {
      if (v === undefined) delete process.env[k]; else process.env[k] = v;
    }
    delete require.cache[require.resolve('../src/config')];
  }
});
