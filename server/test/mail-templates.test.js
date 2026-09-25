'use strict';
/**
 * قالب‌های ایمیلِ هر برنامه — همان دو فایلی که صاحبِ سامانه داد (۱۴۰۵/۰۷/۱۳).
 *
 * ⛔ جانِ این آزمون بندِ ۲ است: خروجیِ ایمیلِ کد با خودِ فایل **فقط در شش
 * رقم** فرق دارد. هر «بهبود»ی روی قالب همین‌جا سرخ می‌شود — و باید بشود،
 * چون اجازه‌اش داده نشده.
 *
 * بی سرور و بی دیتابیس: ماژول خالص است.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const t = require('../src/lib/mail-templates');

const SPANS = (s) => s.split('').map((d) => `<span>${d}</span>`).join('');

test('۱) هر دو قالب هستند، و همان فایل‌های صاحبِ سامانه‌اند (نه بازنویسی)', () => {
  for (const app of ['pump', 'shop']) {
    assert.ok(t.available(app), `قالبِ ${app}`);
    const raw = fs.readFileSync(path.join(t.DIR, t.FILES[app]), 'utf8');
    //  نشانه‌های خودِ فایل‌ها — اگر کسی فایل را «تمیز» کند، این‌جا می‌افتد
    assert.ok(raw.includes('<title>کد ورود'), 'عنوانِ خودِ فایل');
    assert.ok(raw.includes('fonts.googleapis.com'), 'قلمِ بیرونی دست نخورده');
    assert.ok(raw.includes('<script>'), 'اسکریپتِ کپی دست نخورده');
    assert.ok(raw.includes('چرا ویلن'), 'بخشِ «چرا ویلن»');
    assert.ok(raw.includes('کد ورود شما'), 'عنوانِ کارتِ کد');
  }
  assert.ok(fs.readFileSync(path.join(t.DIR, t.FILES.pump), 'utf8').includes(`id="code" dir="ltr">${t.SAMPLE}<`));
  assert.ok(fs.readFileSync(path.join(t.DIR, t.FILES.shop), 'utf8').includes(SPANS(t.SAMPLE)));
});

test('۲) ⛔ ایمیلِ کدِ ورود با فایل فقط در شش رقم فرق دارد — و یک خطِ پیش‌نمایشِ نامرئی', () => {
  const pre = t.preheader(t.PREVIEW.code);
  for (const app of ['pump', 'shop']) {
    const raw = fs.readFileSync(path.join(t.DIR, t.FILES[app]), 'utf8');
    const html = t.codeHtml({ app, code: '735102' });
    assert.ok(html, 'رندر شد');
    assert.ok(!html.includes(t.SAMPLE), 'رقمِ نمونه نمانده');
    //  ⛔ خطِ پیش‌نمایش (۱۴۰۵/۰۷/۱۳، «توی اعلانات کدی نباشد») فقط در سرِ فایل
    //  است، پنهان است، و **کد در آن نیست**
    assert.ok(html.startsWith(pre), 'خطِ پیش‌نمایش سرِ نامه');
    assert.ok(/display:none/.test(pre) && !pre.includes('735102'), 'پنهان و بی کد');
    //  بقیه همان فایل، با رقمِ تازه به‌جای نمونه — و **هیچ** فرقِ دیگری
    const expected = app === 'pump'
      ? raw.replace(`id="code" dir="ltr">${t.SAMPLE}<`, 'id="code" dir="ltr">735102<')
      : raw.replace(SPANS(t.SAMPLE), SPANS('735102'));
    const body = html.slice(pre.length);
    assert.equal(body, expected, `${app}: فقط رقم‌ها عوض شده‌اند`);
    assert.equal(body.length, raw.length, 'حتی یک بایت از خودِ فایل کم و زیاد نشده');
  }
});

test('۲ب) ⛔ عنوانِ ایمیل همان <title>ِ فایل است و کد ندارد؛ فرستنده نامِ برنامه', () => {
  assert.equal(t.titleOf('pump'), 'کد ورود ویلن');
  assert.equal(t.titleOf('shop'), 'کد ورود VILL3N Shop');
  assert.equal(t.brandOf('pump'), 'ویلن');
  assert.equal(t.brandOf('shop'), 'VILL3N Shop');
});

test('۳) کدِ اشتراک و بازیابیِ رمز همان مدل با عنوانِ خودشان — بی دست زدن به بقیه', () => {
  for (const app of ['pump', 'shop']) {
    const html = t.codeHtml({ app, code: '000042', title: 'کد اشتراک شما', lead: 'برنامه را باز کنید.' });
    assert.ok(html.includes('کد اشتراک شما') && !html.includes('کد ورود شما'), 'عنوان عوض شد');
    assert.ok(html.includes('برنامه را باز کنید.'), 'توضیح عوض شد');
    assert.ok(html.includes('چرا ویلن'), 'بقیهٔ صفحه سرِ جایش');
    //  متنِ کاربر خام در HTML نمی‌نشیند
    const bad = t.codeHtml({ app, code: '000042', title: '<img src=x onerror=1>' });
    assert.ok(!bad.includes('<img src=x'), 'عنوان امن می‌شود');
  }
});

test('۴) ایمیلِ پیام: همان مدل، کارتِ کد جایش پیام — کپی و اسکریپت رفته', () => {
  for (const app of ['pump', 'shop']) {
    const html = t.messageHtml({ app, title: 'اشتراکِ شما رو به پایان است', body: 'سلام،\nهفت روز مانده.\n\nبرای تمدید با ما تماس بگیرید.' });
    assert.ok(html, 'رندر شد');
    assert.ok(html.includes('<h2>اشتراکِ شما رو به پایان است</h2>') || html.includes('<h3>اشتراکِ شما رو به پایان است</h3>'), 'عنوان');
    assert.ok(html.includes('<p>سلام،<br>هفت روز مانده.</p>'), 'خطِ تازه ⇒ <br>');
    assert.ok(html.includes('<p>برای تمدید با ما تماس بگیرید.</p>'), 'خطِ خالی ⇒ پاراگرافِ تازه');
    assert.ok(!html.includes(t.SAMPLE), 'هیچ کدی در پیام نیست');
    assert.ok(!html.includes('copyBtn') && !html.includes('<script'), 'دکمهٔ کپی و اسکریپتش رفته');
    assert.ok(!html.includes('کد ورود شما'), 'عنوانِ کارتِ کد رفته');
    assert.ok(html.includes('چرا ویلن') && html.includes('مشخصات برنامه'), 'بقیهٔ صفحه سرِ جایش');
    assert.ok(html.includes('<style>') && html.includes('fonts.googleapis.com'), 'ظاهر همان است');
  }
  //  پمپ: کادرِ کد، دو نشانِ «اعتبار» و هشدارِ سرخ هم مالِ کد بودند
  const pump = t.messageHtml({ app: 'pump', title: 'x', body: 'y' });
  assert.ok(!pump.includes('class="code-box"') && !pump.includes('class="meta"') && !pump.includes('class="warn"'));
  //  دکان: شش خانهٔ رقم
  const shop = t.messageHtml({ app: 'shop', title: 'x', body: 'y' });
  assert.ok(!shop.includes('id="digits"'));
});

test('۵) ورودیِ بد ⇒ null، نه ایمیلِ نصفه', () => {
  assert.equal(t.codeHtml({ app: 'pump', code: '12' }), null, 'کدِ کوتاه');
  assert.equal(t.codeHtml({ app: 'pump', code: 'abcdef' }), null, 'کدِ بی‌رقم');
  assert.equal(t.appOf('dukan'), 'shop', 'هر چیزی جز pump دکان است');
  assert.equal(t.appOf('PUMP'), 'pump');
});

test('۶) ⛔ همهٔ فرستنده‌ها از همین قالب می‌گذرند — و کارتِ ساده فقط پشتیبان است', () => {
  const read = (p) => fs.readFileSync(path.join(__dirname, '..', 'src', p), 'utf8');
  const otp = read('lib/otp.js');
  assert.ok(/mail-templates'\)\.codeHtml|templates\.codeHtml\(/.test(otp), 'کدِ ثبت‌نام/ورود/بازیابی');
  assert.ok(/purpose === 'reset'/.test(otp), 'بازیابیِ رمز عنوانِ خودش را دارد');
  assert.ok(/\{ app: sectionOf\(app\) \|\| '', purpose \}/.test(otp), 'برنامه و منظور به فرستنده می‌رسد');
  const outbox = read('lib/login-outbox.js');
  assert.ok(/mail-templates'\)\.codeHtml\(\{ app, code \}\)/.test(outbox), 'کدِ ورودِ برنامه‌ها — بی هیچ عنوانِ تازه');
  const vip = read('lib/vip-codes.js');
  assert.ok(/mail-templates'\)\.codeHtml\(\{[\s\S]{0,80}app: T\.app, code,[\s\S]{0,80}title: 'کد اشتراک شما'/.test(vip), 'کدِ اشتراک');
  const notices = read('lib/notices.js');
  assert.ok(/messageHtml\(\{ app, title, body \}\)/.test(notices), 'اعلان‌ها با قالبِ پیام');
  assert.ok(/emailHtml\(\{ title, body, brand: b\.brand, logoUrl: b\.logoUrl, app: r\.app \}\)/.test(notices), 'برنامهٔ گیرنده به قالب می‌رسد');
  const platform = read('routes/admin-platform.js');
  assert.ok(/mail-templates'\)\.messageHtml\(/.test(platform), 'ایمیلِ آزمایشیِ مدیر همان مدل است');
});
