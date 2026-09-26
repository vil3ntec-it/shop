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
    assert.ok(fs.existsSync(path.join(t.DIR, t.EMAIL_FILES[app])), 'نسخهٔ فرستادنی');
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

test('۲) ⛔ ایمیلِ کدِ ورود با نسخهٔ فرستادنی فقط در شش رقم فرق دارد — و یک خطِ پیش‌نمایشِ نامرئی', () => {
  const pre = t.preheader(t.PREVIEW.code);
  for (const app of ['pump', 'shop']) {
    const raw = fs.readFileSync(path.join(t.DIR, t.EMAIL_FILES[app]), 'utf8');
    const html = t.codeHtml({ app, code: '735102' });
    assert.ok(html, 'رندر شد');
    //  ⛔ خطِ پیش‌نمایش (۱۴۰۵/۰۷/۱۳، «توی اعلانات کدی نباشد») درست پس از <body>
    const at = html.indexOf(pre);
    assert.ok(at > html.indexOf('<body') && at < html.indexOf('<table'), 'خطِ پیش‌نمایش سرِ بدنه');
    assert.ok(/display:none/.test(pre) && !pre.includes('735102'), 'پنهان و بی کد');
    const body = html.slice(0, at) + html.slice(at + pre.length + 1);
    const expected = app === 'pump'
      ? raw.replace(`">${t.SAMPLE}</div>`, '">735102</div>')
      : raw.replace(/<!--digits-->[^]*?<!--\/digits-->/, (b) => { let k = 0; return b.replace(/>(\d)<\/td>/g, () => `>${'735102'[k++]}</td>`); });
    assert.notEqual(expected, raw, 'رقمِ نمونه پیدا شد');
    assert.equal(body, expected, `${app}: فقط رقم‌ها عوض شده‌اند`);
  }
});

/**
 * ⛔ گزارشِ صاحبِ سامانه با عکسِ جیمیل (۱۴۰۵/۰۷/۱۳): «اصلاً ظاهرش رو دیدی؟»
 * نامه متنِ خام می‌رسید، چون فایلِ او صفحهٔ وب است و جیمیل <style>، متغیرِ
 * CSS، flex/grid، SVG و اسکریپت را دور می‌ریزد. نسخهٔ فرستادنی هیچ‌کدام را
 * ندارد — و **نوشته‌اش واژه‌به‌واژه همان فایلِ اوست**.
 */
test('۲الف) ⛔ نسخهٔ فرستادنی: همان نوشته‌ها، بی چیزی که جیمیل دور می‌ریزد', () => {
  const text = (h) => h.replace(/<!--[^]*?-->/g, '').replace(/<(script|style|title|head)[^]*?<\/\1>/gi, '')
    .replace(/<[^>]+>/g, ' ').replace(/&nbsp;/g, ' ').replace(/\s+/g, ' ').trim();
  for (const app of ['pump', 'shop']) {
    const orig = fs.readFileSync(path.join(t.DIR, t.FILES[app]), 'utf8');
    const mail = fs.readFileSync(path.join(t.DIR, t.EMAIL_FILES[app]), 'utf8');
    assert.equal(text(mail), text(orig), `${app}: نوشته‌ها همان فایلِ صاحبِ سامانه`);
    for (const bad of ['<style', '<script', 'var(--', '<svg', 'display:flex', 'display:grid', 'fonts.googleapis', 'class="']) {
      assert.ok(!mail.includes(bad), `${app}: «${bad}» در نامه نیست`);
    }
    //  هر تصویر پیوستِ درون‌خطی است و فایلش هست
    const cids = [...mail.matchAll(/src="([^"]+)"/g)].map((m) => m[1]);
    assert.ok(cids.length > 10);
    for (const c of cids) assert.ok(/^cid:(pump|shop)-[a-z0-9]+@vill3n$/.test(c), c);
    const parts = t.inlineParts(mail);
    assert.equal(parts.length, new Set(cids).size, `${app}: هر cid یک PNG`);
    for (const p of parts) assert.equal(p.data.subarray(1, 4).toString(), 'PNG');
  }
});

test('۲ج) نامهٔ دارای تصویر multipart/related است و هر تصویر Content-IDِ خودش را دارد', () => {
  const { buildMessage } = require('../src/lib/mailer');
  const html = t.codeHtml({ app: 'pump', code: '735102' });
  const msg = buildMessage({ from: 'a@b.c', fromName: 'ویلن' }, { to: 'x@y.z', subject: 'کد ورود ویلن', text: '735102', html });
  assert.match(msg, /Content-Type: multipart\/related; type="multipart\/alternative"; boundary="/);
  assert.match(msg, /Content-Type: multipart\/alternative; boundary="a/);
  for (const p of t.inlineParts(html)) assert.ok(msg.includes(`Content-ID: <${p.cid}>`), p.cid);
  //  نامهٔ بی تصویر همان alternativeِ ساده می‌ماند
  const plain = buildMessage({ from: 'a@b.c' }, { to: 'x@y.z', subject: 's', text: 't', html: '<p>t</p>' });
  assert.match(plain, /Content-Type: multipart\/alternative; boundary="b/);
  assert.ok(!plain.includes('Content-ID'));
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
    assert.ok(/">اشتراکِ شما رو به پایان است<\/h[23]>/.test(html), 'عنوان');
    assert.ok(html.includes('">سلام،<br>هفت روز مانده.</p>'), 'خطِ تازه ⇒ <br>');
    assert.ok(html.includes('">برای تمدید با ما تماس بگیرید.</p>'), 'خطِ خالی ⇒ پاراگرافِ تازه');
    assert.ok(!html.includes(t.SAMPLE), 'هیچ کدی در پیام نیست');
    assert.ok(!html.includes('copyBtn') && !html.includes('<script'), 'دکمهٔ کپی و اسکریپتش رفته');
    assert.ok(!html.includes('کد ورود شما'), 'عنوانِ کارتِ کد رفته');
    assert.ok(html.includes('چرا ویلن') && html.includes('مشخصات برنامه'), 'بقیهٔ صفحه سرِ جایش');
    assert.ok(html.includes('cid:') && !html.includes('<style'), 'ظاهر همان نسخهٔ فرستادنی است');
  }
  //  پمپ: کادرِ کد، دو نشانِ «اعتبار» و هشدارِ سرخ هم مالِ کد بودند
  const pump = t.messageHtml({ app: 'pump', title: 'x', body: 'y' });
  assert.ok(!pump.includes('<!--code-box-->') && !pump.includes('<!--meta-->') && !pump.includes('<!--warn-->'));
  assert.ok(!pump.includes('cid:pump-copy@') && !pump.includes('اعتبار: ۵ دقیقه'));
  //  دکان: شش خانهٔ رقم
  const shop = t.messageHtml({ app: 'shop', title: 'x', body: 'y' });
  assert.ok(!shop.includes('<!--digits-->') && !shop.includes('کپی کردن کد'));
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
