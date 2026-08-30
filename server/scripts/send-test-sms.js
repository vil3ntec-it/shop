#!/usr/bin/env node
'use strict';
/**
 * آزمایش سرویس پیامک، بدون دست زدن به برنامه و دیتابیس.
 *
 *   node scripts/send-test-sms.js 0790000000
 *
 * یک کد ساختگی به همان شماره می‌فرستد و دقیقاً می‌گوید چه چیزی به
 * سرویس فرستاده شد و چه پاسخی آمد. تا این کار نکند، ورود با شماره هم
 * روی گوشی کار نمی‌کند.
 */
const config = require('../src/config');
const { senders } = require('../src/lib/otp');

const to = process.argv[2];
if (!to) {
  console.error('شماره را بدهید:  node scripts/send-test-sms.js 0790000000');
  process.exit(1);
}

const provider = config.otp.provider;
const code = String(Math.floor(100000 + Math.random() * 900000));
const message = config.sms.template
  ? config.sms.template.replace(/\{code\}/g, code)
  : `کد ورود شما: ${code}`;

console.log('راه ارسال :', provider);
if (provider === 'sms') {
  console.log('آدرس      :', config.sms.url || '(تنظیم نشده)');
  console.log('روش       :', config.sms.method);
  console.log('فرستنده   :', config.sms.sender || '(تنظیم نشده)');
  console.log('کلید      :', config.sms.key ? `تنظیم شده (${config.sms.key.length} نویسه)` : '(تنظیم نشده)');
}
console.log('گیرنده    :', to);
console.log('متن       :', message);
console.log('');

const send = senders[provider];
if (!send) {
  console.error(`راه ارسالی به نام «${provider}» نداریم. OTP_PROVIDER را درست کنید.`);
  console.error('راه‌های موجود:', Object.keys(senders).join('، '));
  process.exit(1);
}

send(to, code, message)
  .then((out) => {
    console.log('✅ فرستاده شد:', JSON.stringify(out));
    if (provider === 'log') {
      console.log('');
      console.log('توجه: OTP_PROVIDER روی «log» است، یعنی کد فقط همین‌جا چاپ شد');
      console.log('و به هیچ گوشی‌ای نرفت. برای ارسال واقعی OTP_PROVIDER=sms بگذارید.');
    }
  })
  .catch((err) => {
    console.error('❌ نرفت:', err.message);
    console.error('');
    console.error('اگر پاسخ سرویس را بالا می‌بینید، معمولاً یکی از اینهاست:');
    console.error('  • نام پارامترها در SMS_API_BODY با چیزی که سرویس می‌خواهد نمی‌خواند');
    console.error('  • کلید در جای درست فرستاده نشده (سرآیند یا بدنه)');
    console.error('  • خط فرستنده (SMS_SENDER) به این حساب تعلق ندارد');
    console.error('  • اعتبار حساب تمام شده');
    process.exit(1);
  });
