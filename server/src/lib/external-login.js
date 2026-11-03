'use strict';
/**
 * ورود با «فراهم‌کنندهٔ بیرونی» (گوگل، اپل ِ آیفون) — قاعده و جدولِ مشترکِ مسیرهای `routes/auth.js`.
 *
 * مسیرها (`/auth/google`، `/auth/apple` و `link-code` ِ هر کدام) در `routes/auth.js` می‌مانند چون به قفلِ تلاش و
 * صدورِ نشستِ همان‌جا تکیه دارند؛ این‌جا فقط آنچه میانِ فراهم‌کننده‌ها فرق دارد یا مشترک است.
 */
const config = require('../config');
const { one } = require('../db');
const google = require('./google');
const apple = require('./apple');

/*
 *  ⛔ بندِ ۲۹.۲ قاعدهٔ ۵ و AUTH-04 — «برابر بودنِ ایمیل به‌تنهایی مجوزِ ادغام نیست».
 *
 *  تا دیروز اگر شناسهٔ گوگل (`sub`) هنوز به حسابی وصل نبود، حسابی که همان
 *  ایمیل را داشت **بی هیچ پرسشی** برداشته و گوگل به آن چسبانده می‌شد. یعنی
 *  هر کسی که یک حسابِ گوگل با نشانیِ ایمیلِ شما داشت (دامنهٔ ایمیلی که دستِ
 *  دیگری افتاده، حسابِ Workspaceِ بازیافته…) بی رمز وارد دکانِ شما می‌شد.
 *
 *  حالا وصل شدن فقط وقتی است که روشِ **قبلیِ** همان حساب هم ثابت شود: رمزِ
 *  حساب (`password`) یا کدی که به ایمیلِ حساب رفته (`code`، از
 *  `POST /google/link-code`). بی آن، پاسخ ۴۰۹ `google_link_required` است و
 *  برنامه همان‌جا رمز یا کد را می‌خواهد. کسی که گوگلش از قبل وصل است (یافتن
 *  با `sub`) هیچ تغییری نمی‌بیند.
 *
 *  بازگشت: `GOOGLE_EMAIL_AUTOLINK=1` رفتارِ قبلی را برمی‌گرداند (فقط برای
 *  اضطرار؛ هر اتصال هنوز در سابقه `auth.identity_linked` می‌نشیند).
 */
const LINK_METHODS_MESSAGE = 'این ایمیل از قبل حساب دارد. برای وصل کردنِ گوگل به همان حساب، رمزِ حساب را بزنید یا کدی را که به همین ایمیل می‌فرستیم وارد کنید.';

/*
 *  ⛔ بازبینیِ امنیتی ۱۴۰۵/۰۷/۲۷ — گوگلِ «ایمیلِ تأییدنشده».
 *
 *  گوگل اجازه می‌دهد حسابی با نشانیِ ایمیلِ کسِ دیگری ساخته شود بی آن‌که آن
 *  ایمیل تأیید شود؛ توکنش `email_verified=false` دارد و هیچ چیزی دربارهٔ
 *  مالکیتِ ایمیل ثابت نمی‌کند. تا امروز چنین توکنی (۱) برای ایمیلِ کسی که
 *  هنوز حساب نداشت **حساب می‌ساخت** — مهاجم پیش از قربانی جا را می‌گرفت و
 *  پس از «رمزِ فراموش‌شده»ی قربانی هم با همان گوگل وارد می‌شد — و (۲) برای
 *  حسابِ موجود «این ایمیل حساب دارد + روش‌هایش» را می‌گفت و کدِ وصل را به
 *  صندوقِ قربانی می‌فرستاد (بمبارانِ ایمیل).
 *
 *  حالا ساختن و وصل کردن فقط با ایمیلِ تأییدشده است. پاسخِ تأییدنشده برای
 *  ایمیلِ دارای حساب و بی‌حساب **یکی** است. شناسه‌ای که از قبل وصل است
 *  (یافتن با `sub`) همان‌طور وارد می‌شود؛ «رمزِ فراموش‌شده» شناسه‌های
 *  تأییدنشده را جدا می‌کند (`unlinkUnverifiedGoogle`).
 */
const GOOGLE_UNVERIFIED_MESSAGE = 'ایمیلِ این حسابِ گوگل نزدِ خودِ گوگل تأیید نشده است، پس با آن نمی‌شود حساب ساخت یا به حسابی وصل شد. اول ایمیل را در حسابِ گوگل تأیید کنید، یا با ایمیل و رمز/کدِ ایمیل وارد شوید.';

/** حسابِ موجودی که این ایمیل را دارد و هنوز این فراهم‌کننده (گوگل/اپل) به آن وصل نیست — فقط برای ایمیلِ تأییدشده. */
async function accountOfProviderEmail(profile) {
  if (!profile.email || !profile.emailVerified) return null;
  return one('SELECT * FROM users WHERE email=$1', [profile.email]);
}

/*
 *  ورود با «فراهم‌کنندهٔ بیرونی» — گوگل و اپل، **یک قاعده** (همان بندِ ۲۹.۲ و AUTH-04 ِ بالا):
 *  یافتن با شناسهٔ فراهم‌کننده؛ ساختن/وصل کردن فقط با ایمیلِ تأییدشده؛ ایمیلِ دارای حساب ⇐ اثباتِ
 *  روشِ قبلی (۴۰۹ `<id>_link_required` + `POST /<id>/link-code`)؛ هرگز وصلِ خودکار با ایمیل (جز کلیدِ
 *  اضطراریِ `GOOGLE_EMAIL_AUTOLINK` که فقط گوگل دارد). تفاوت‌ها فقط در همین جدول است؛ گوگل
 *  همان کد، همان پیام و همان سابقه را دارد که پیش از این جدول داشت (`google-link.test.js`).
 *  اپل: توکن با nonce ِ یک‌بارمصرفِ همین سرور (`lib/apple.js`)، و نام از بدنه (اپل در توکن نمی‌دهد).
 */
const APPLE_LINK_MESSAGE = 'این ایمیل از قبل حساب دارد. برای وصل کردنِ اپل به همان حساب، رمزِ حساب را بزنید یا کدی را که به همین ایمیل می‌فرستیم وارد کنید.';
const APPLE_UNVERIFIED_MESSAGE = 'ایمیلِ این حسابِ اپل تأییدشده نیست، پس با آن نمی‌شود حساب ساخت یا به حسابی وصل شد. با ایمیل و رمز/کدِ ایمیل وارد شوید.';

/** نامِ نمایشیِ بارِ نخستِ اپل — فقط متن، کوتاه، بی نویسهٔ کنترلی */
function bodyName(raw) {
  // eslint-disable-next-line no-control-regex
  return typeof raw === 'string' ? raw.replace(/[\u0000-\u001f\u007f]/g, ' ').trim().slice(0, 80) : '';
}

const PROVIDERS = {
  google: {
    id: 'google',
    readProfile: (req) => google.verifyIdToken(req.body?.idToken || req.body?.id_token),
    linkMessage: LINK_METHODS_MESSAGE,
    unverified: [GOOGLE_UNVERIFIED_MESSAGE, 'google_email_unverified'],
    noEmail: ['حساب گوگل ایمیل ندارد', 'google_no_email'],
    legacyEmailLink: () => config.google.legacyEmailLink,
    nameOf: (profile) => profile.name || '',
    beforeSession: async () => {},
  },
  apple: {
    id: 'apple',
    readProfile: (req) => apple.verifyIdToken(req.body?.idToken || req.body?.id_token, req.body?.nonce),
    linkMessage: APPLE_LINK_MESSAGE,
    unverified: [APPLE_UNVERIFIED_MESSAGE, 'apple_email_unverified'],
    noEmail: ['حسابِ اپل ایمیل نداد — در پنجرهٔ اپل «اشتراکِ ایمیل» را بگذارید', 'apple_no_email'],
    legacyEmailLink: () => false,
    nameOf: (profile, req) => bodyName(req.body?.name),
    //  یک‌بار مصرف: پس از تصمیمِ «چه کسی» و پیش از ساختن/وصل کردن/نشست
    beforeSession: (profile) => apple.consumeNonce(profile.nonceKey),
  },
};

function linkRequired(res, user, p) {
  return res.status(409).json({
    error: {
      code: `${p.id}_link_required`,
      message: p.linkMessage,
      //  فقط به دارندهٔ توکنِ معتبرِ همین ایمیل گفته می‌شود — کسی که
      //  ایمیل را نمی‌تواند ثابت کند به این‌جا نمی‌رسد
      link: {
        email: user.email,
        methods: [...(user.password_hash ? ['password'] : []), 'email_code'],
      },
    },
  });
}

module.exports = { PROVIDERS, accountOfProviderEmail, linkRequired, LINK_METHODS_MESSAGE, GOOGLE_UNVERIFIED_MESSAGE };
