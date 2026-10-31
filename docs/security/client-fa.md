# امنیتِ کلاینت و اشتراک — شاخهٔ `claude/sec-client` (۱۴۰۵/۰۸/۰۹)

دامنه: برنامهٔ برنامهٔ امنیت §۶ و §۱۱ — اشتراک و ضدکرک **در سرور**، برنامهٔ اندرویدِ `ir.vil3ntec.tohid`
(`android-native`)، برنامهٔ مدیر (`admin-android`)، برنامهٔ کامپیوتر (`desktop`)؛ برنامهٔ WebViewِ قدیمی
(`android/`) فقط بازرسی. آزمون‌ها: SEC-SUB-01..05. هیچ ادعای «۱۰۰٪ غیرقابل‌کرک» نیست؛ هدف این است که
دست‌کاریِ برنامه، فایلِ محلی، ساعت یا بازپخشِ درخواست **هیچ مجوزِ سروریِ تازه‌ای نسازد**.

PRها: همین شاخه (سرور + این سند) · #580 (`claude/sec-client-hardening`: کامپیوتر و R8).
هیچ مقدارِ کلید/رمز/توکنی خوانده یا این‌جا نوشته نشده؛ فقط جا و شیوهٔ نگهداری.

## ۱. جدولِ یافته‌ها

| شناسه | شدت | مسیرِ واقعی | ریشه | اصلاح / PR | آزمون (پرونده › نام) | وضعیت | خطرِ باقی‌مانده | کارِ صاحب محصول |
|---|---|---|---|---|---|---|---|---|
| SEC-CLI-01 | High | `server/src/routes/license.js` › `POST /api/license/sync` | شناسهٔ دستگاهِ بدنه فقط **شمرده** می‌شد، هرگز **نشانده** نه: یک توکن (یا توکنِ کپی‌شده) با شناسه‌های پیاپی همیشه «n < سقف» می‌دید ⇒ بی‌شمار مجوزِ امضاشده؛ شناسهٔ باطل‌شده (`revoked`) چون شمرده نمی‌شد دوباره مجوز می‌گرفت؛ بی قفل، درخواستِ هم‌زمان هم رد می‌شد؛ و دستگاهی که با «ورود» ثبت شده بود همیشه «شناخته» بود، پس سقف عملاً برای هیچ دستگاهِ واردشده‌ای نبود | `claimSlot`: شناسهٔ تازه فقط با جای خالی، زیرِ `pg_advisory_xact_lock('shop-devices:<دکان>')`، در `devices` ثبت می‌شود (در «دستگاه‌های من» دیده و باطل‌شدنی)؛ باطل‌شده ⇒ `device_revoked`؛ دستگاهِ واردشدهٔ بیرون از n ِ اول ⇒ `device_limit` فقط با پرچمِ `shop.device_cap` (پیش‌فرض خاموش) — همین PR | `server/test/sec-sub-device-cap.test.js` › هر چهار آزمون (پیش از اصلاح ۴/۴ سرخ؛ پس از آن ۴/۴ سبز روی PGlite و PostgreSQL ۱۶). جهش روی PostgreSQL: بی قفل ⇒ «هم‌زمان» سرخ؛ بی ثبت ⇒ ۲ سرخ؛ بی سنجشِ باطل ⇒ «باطل‌شده» سرخ؛ پرچمِ نادیده ⇒ «پرچم» سرخ | FIXED-PENDING-MERGE | تا `shop.device_cap` خاموش است، هر گوشی‌ای که با گذرواژهٔ همان حساب **وارد** شود مجوز می‌گیرد (رفتارِ نسخه‌های نصب‌شده). مجوز فقط قابلیتِ آفلاینِ همان گوشی است؛ درهای سرور (`requireFeature`) جدا و درست‌اند | پس از دیدنِ «دستگاه‌های من»ِ دکان‌های بزرگ، `PUT /api/admin/pump/live-config {scope:'all', key:'shop.device_cap', value:true}` |
| SEC-CLI-02 | **Critical — رخداد** | `android-native/app/tohid-release.jks` (کلیدِ امضای انتشار) در مخزنِ **عمومی**؛ رمزِ پیش‌فرضش در `android-native/KEYSTORE.md` و پیش‌فرضِ `android-native/app/build.gradle.kts` و `admin-android/app/build.gradle.kts` | هر کسی می‌تواند APKی بسازد که اندروید آن را «به‌روزرسانیِ همان برنامه» بداند (برنامهٔ دکان و برنامهٔ مدیر — هر دو همین فایل را پیش‌فرض دارند؛ WebViewِ قدیمی `release.keystore`ِ جدا و بیرون از مخزن دارد)؛ سنجشِ امضای خودِ برنامه (`SIGNING_SHA256`) هم بی‌معنا می‌شود | — (کارِ کلید: فقط صاحب محصول؛ بندِ ۶ِ قاعده‌ها) | — | FAIL | تا چرخش: برنامهٔ تروجانیِ هم‌امضا از هر کانالی (پیام‌رسان، سرورِ دست‌کاری‌شده) روی نصبِ کاربر می‌نشیند | ۱) کلیدِ تازه و چرخش با APK Signature Scheme v3 (`apksigner rotate` ⇒ اندروید ۹+ بی حذف ارتقا می‌گیرد؛ ۷ و ۸ باید یک بار حذف و نصب کنند)؛ ۲) کلید فقط در secretِ `TOHID_KEYSTORE_B64` و رمز فقط در secret؛ ۳) `git rm` فایل و پاک کردنِ رمزِ پیش‌فرض از سه پرونده؛ ۴) بازنویسیِ تاریخچه اختیاری (کلون‌ها می‌مانند). هم‌پوشانی با `claude/sec-artifacts` (SEC-ART-05) |
| SEC-CLI-03 | High | انتشارهای `tohid-desktop`، `tohid-native`، `tohid-admin` (و `-beta`) فقط `SHA256SUMS.txt` دارند، **بی `.sig`** ⇒ `TOHID_UPDATE_PUBLIC_KEY` خالی ⇒ `UpdaterDesk.verify` و `Updater.verify`ِ اندروید در حالتِ نرم | فهرستِ جمعِ کنترلی از همان کانالِ فایل می‌آید (سرورِ خانگی `/api/releases` یا GitHub)؛ کسی که آن کانال را بگیرد نصب‌کنندهٔ دلخواه با جمعِ درست می‌دهد. نصب‌کنندهٔ ویندوز امضای Authenticode ندارد. (اندروید خودش امضای APK را می‌سنجد — ولی SEC-CLI-02) | — (کد آماده است: `ReleaseSignature`، گام‌های امضای سه ورک‌فلو) | از روی کد: `UpdaterDesk.verify` با کلیدِ سنجاق‌شده فهرستِ بی‌امضا/نادرست را رد می‌کند؛ آزمونِ واحدی برای `ReleaseSignature` پیدا نشد (NOT-TESTED) | FAIL | تا کلید گذاشته نشود، به‌روزرسانیِ کامپیوتر فقط به TLSِ کانال تکیه دارد | کلیدِ P-256: secretِ `TOHID_UPDATE_SIGNING_KEY` + متغیرِ `TOHID_UPDATE_PUBLIC_KEY`؛ نگه داشتنِ کلیدِ خصوصی بیرون از سرور. چرخش: نسخهٔ بعدی با کلیدِ تازه فقط با برنامه‌ای که کلیدِ تازه را سنجاق کرده؛ گم شدنِ کلید ⇒ یک بار دانلودِ دستی. گواهیِ Authenticode و notarizeِ اپل هم کارِ صاحب محصول |
| SEC-CLI-04 | Medium | `desktop/.../SecretBox.kt` | کلیدِ AES-GCMِ نشست در `%LOCALAPPDATA%\Tohid\keys\session.key` کنارِ خودِ رمزشده؛ روی ویندوز `setPosixFilePermissions` بی‌صدا شکست می‌خورد ⇒ فقط ACLِ ارثیِ پوشهٔ کاربر؛ DPAPI/Credential Manager نیست | — (پیشنهاد: کلید با `CryptProtectData`ِ CurrentUser (DPAPI) از راهِ JNA، با مهاجرت و برگشت به همین فایل؛ این‌جا ساخت و آزمونِ ویندوز ممکن نبود) | — | NOT-TESTED | کپیِ آفلاینِ دیسک، مدیرِ دیگرِ همان کامپیوتر یا ابزارِ همگام‌سازیِ پوشهٔ کاربر نشست (توکنِ تازه‌سازی) را می‌برد. در برابرِ بدافزارِ همان کاربر DPAPI هم کمکی نمی‌کند | تصمیم برای کارِ DPAPI (PRِ جدا با آزمونِ ویندوز) |
| SEC-CLI-05 | Low | `desktop/.../DevShot.kt` | ابزارِ عکس/اسکریپتِ آزمون (`typecmd` = فرمانِ پوسته، `click`، `type`) در نسخهٔ انتشار هم با `-Dtohid.shot…` روشن می‌شد | فقط `BuildConfig.DEBUG` — #580 | `desktop/src/test/.../DevShotReleaseGateTest.kt` › «در ساختِ انتشار ویژگیِ tohid-shot هیچ کاری نمی‌کند» (پیش از اصلاح پوشه ساخته می‌شد) | FIXED-PENDING-MERGE (CI؛ Gradle این‌جا نمی‌سازد) | — | — |
| SEC-CLI-06 | Info | `android-native/app/proguard-rules.pro` | R8 و کوچک‌سازی روشن بود، ولی `-keep class ir.vil3ntec.tohid.**` کدِ برنامه را مبهم نمی‌کرد (عمداً، برای دفترِ kotlinx.serialization) | پیش‌فرض بی‌تغییر؛ `-Ptohid.obfuscate=true` ⇒ `proguard-obfuscate.pro` — #580 | — | NOT-TESTED (مسیرِ مبهم) | مبهم‌سازی کنترلِ امنیتی نیست؛ تصمیم‌ها در سرورند | پیش از روشن کردن: preview، نصب روی نسخهٔ پیشین، بازیابیِ پشتیبانِ کهنه، Sync، چاپ، اسکن، ورود؛ نگه داشتنِ `mapping.txt` |
| SEC-CLI-07 | Low | `android-native/.../CourierStore.kt` (`tohid-courier.xml`، `files/courier-photos/`) | کارت‌های مأموریتِ باز (نشانیِ مشتری) و عکس‌های تحویل در پشتیبانِ ابری و انتقالِ دستگاهِ اندروید می‌روند | — عمداً دست نخورد: بیرون گذاشتن، رویدادهای ارسال‌نشدهٔ **پول** (`cashCollectedMinor`) را در انتقالِ گوشی گم می‌کند ⇒ رفتارِ پول/همگام‌سازی ⇒ فقط پشتِ پرچم و با تصمیم | — | NOT-TESTED | دادهٔ شخصیِ مشتری در پشتیبانِ گوگلِ خودِ پیک (رمزشده با قفلِ صفحه در اندروید ۹+) | تصمیم: بیرون گذاشتنِ عکس‌ها و کارت‌های **پایان‌یافته** بی صف |
| SEC-CLI-08 | Low | `admin-android/.../net/Session.kt` | اگر `EncryptedSharedPreferences` ساخته نشود، توکنِ مدیر در `SharedPreferences`ِ ساده (خصوصیِ برنامه) | — | — | NOT-TESTED | فقط روی گوشی‌ای که Keystoreش خراب است؛ `allowBackup=false` و `FLAG_SECURE` هست | — |
| SEC-CLI-09 | Medium (فقط بازرسی) | `android/` (`af.tohid.shop`، WebView) | انتشار: `cleartextTrafficPermitted="true"` و اعتماد به CAِ کاربر؛ `allowBackup="true"` (دادهٔ WebView با نشست می‌تواند به پشتیبان برود)؛ پلِ JS ِ `ShopAndroid` (فقط روی صفحه‌های خودِ برنامه، `allowFileAccess=false`، اشکال‌زداییِ WebView فقط در debug) | — (صاحب محصول فعلاً نگهش داشته) | — | NOT-APPLICABLE (تصمیمِ صاحب محصول) | MITM روی وای‌فای و پروکسیِ CAِ کاربر | برداشتنِ انتشارش یا دست‌کم بستنِ cleartext و CAِ کاربر |
| SEC-CLI-10 | Info | `network_security.xml`ِ انتشار | سنجاقِ گواهی نیست | عمداً اضافه نشد: گواهیِ دامنه (تونل/Let's Encrypt) خودکار عوض می‌شود و برنامهٔ نصب‌شده با سنجاقِ کهنه **همهٔ دکان‌ها** را از سرور (و از به‌روزرسانیِ همان سرور) می‌بُرد؛ تا برنامهٔ چرخش و بازیابی (دو کلیدِ پشتیبان، کانالِ به‌روزرسانیِ جدا) نباشد سنجاق نمی‌شود. جایش: فقط CAِ سیستم، بی cleartext، و کلیدِ عمومیِ **مجوز** و **به‌روزرسانی** سنجاق‌شده در ساخت | — | NOT-APPLICABLE (با دلیل) | CAِ دولتی/دزدیده | گذاشتنِ `TOHID_LICENSE_PUBLIC_KEY` در متغیرهای GitHub (بی آن، برنامه کلیدِ مجوز را از سرور می‌گیرد و ساخت هشدار می‌دهد) |

## ۲. آزمون‌های SEC-SUB

| شناسه | معنا | آزمون | نتیجه | وضعیت |
|---|---|---|---|---|
| SEC-SUB-01 | اشتراکِ منقضی/معلق/لغوشده API را باز نکند | `paid-routes-entitlement.test.js` (همهٔ نوشتنی‌های `/api/shop` از سورس، چهار حالت) · `license-format.test.js` › «اشتراک که تمام شود، مجوز تازه صادر نمی‌شود» · `sec-sub-trial.test.js` › دومی (پس از پایان: `staff-code` ⇒ ۴۰۳ `subscription_*`، مجوز `no_subscription`) | سبز روی PGlite (این شاخه) | PASS |
| SEC-SUB-02 | ساعت، نصبِ دوباره، پاک کردنِ داده و بازیابی آزمایشی را از نو نکند | `sec-sub-trial.test.js` (۲ آزمون): `X-Client-Time`/`Date` جلو و عقب، ورودِ دوباره با دستگاهِ تازه، `POST /shop/create` دوم ⇒ ۴۰۹ `already_member`، بدنهٔ ساختگیِ `trial_started_at/created_at/subscription`، پشتیبانِ ساختگی (`/api/me/backups`) و Syncِ ساختگی ⇒ همان پایان، `free` می‌ماند. جهش: بی `already_member` ⇒ سرخ؛ آغاز از `updated_at` ⇒ ۲ سرخ · `trial-single-source.test.js` | ۲/۲ سبز | PASS |
| SEC-SUB-03 | جعلِ پاسخ/فایل/درخواستِ کلاینت مجوز نسازد | `subscription-tamper.test.js` (۵/۵: `vip=true`، پلن، تاریخ، درِ مدیر، ساعت) · `sec-sub-device-cap.test.js` (شناسهٔ ساختگی/باطل‌شده) · `sec-sub-trial.test.js` (پشتیبان و Syncِ ساختگی) | پیش از SEC-CLI-01 سرخ؛ پس از آن سبز | FIXED-PENDING-MERGE |
| SEC-SUB-04 | replay و هم‌زمانی تمدید/فعال‌سازی را تکرار نکنند | `sec-sub-replay.test.js` (۳ آزمون، PGlite و PostgreSQL ۱۶): یک کدِ وی‌آی‌پی × ۴ هم‌زمان + بازپخش ⇒ یک ۲۰۱ و یک دوره؛ یک کد، دو دکانِ هم‌زمان ⇒ یکی؛ کدِ آفلاین × ۴ + بازپخش ⇒ یک `granted`. جهش: بی `FOR UPDATE` در وی‌آی‌پی و کدِ آفلاین ⇒ ۲ سرخ روی PostgreSQL · `sec-sub-device-cap` › «هم‌زمان» (۸ درخواست، ۲ جا) · `admin-idempotency.test.js` (تمدیدِ مدیر با `Idempotency-Key`) | سبز | PASS (دستگاه: FIXED-PENDING-MERGE) |
| SEC-SUB-05 | همهٔ APIهای پولی مستقیم آزموده شوند | `paid-routes-entitlement.test.js` (فهرست از سورس: هر نوشتنیِ `/api/shop` دقیقاً یک دسته؛ مسیرِ تازهٔ بی‌دسته ⇒ سرخ) + راه‌های نوشتنِ داده (`/sync`، `/sync/v1/push`، `/sync/v3/push`، `/products`) + `license/sync`، `vip/redeem`، `vip/offline-code` در همین شاخه | سبز | PASS؛ `/api/courier/*` NOT-TESTED (پایین) |

**`/api/courier/*` (پیک):** فقط پرچم می‌سنجد، نه اشتراک — عمداً (§۲۸.۶): رویدادِ مأموریتی که **پیش از پایانِ اشتراک**
سپرده شده (پولِ گرفته‌شده) دور ریخته نمی‌شود. سپردنِ مأموریتِ تازه (`POST /api/shop/delivery/orders/:id/assign`) PAID است و
در `paid-routes-entitlement` بسته سنجیده می‌شود؛ پس پیک ارزشِ تازه نمی‌سازد. آزمونِ مستقیمِ «رویدادِ پیک پس از پایانِ اشتراک»
نوشته نشد ⇒ NOT-TESTED.

## ۳. سیاستِ اشتراک در سرور (همان که آزمون‌ها می‌سنجند)

- **منبعِ حقیقت:** `lib/entitlement.js` با ساعتِ سرور؛ هیچ مسیری از کلاینت تاریخ، پلن، شناسهٔ دکان یا اشتراک نمی‌پذیرد
  (دکان از ردیفِ توکن و عضویت — `requireShop`/`optionalShop`؛ #273).
- **دفترِ آزمایشی:** دکان = `shops.created_at`، پمپ = `stations.trial_started_at`؛ هیچ مسیری از بیرون نمی‌نویسد؛ «هر حساب یک
  دکانِ خودش» (`createShop`) و «هر حساب یک پمپ»؛ هر اشتراکِ غیرِ `pending` آزمایشی را مصرف می‌کند. حذفِ حساب فقط از
  پنلِ سوپرادمین است. **باقی‌مانده:** حسابِ تازه با ایمیلِ تازه آزمایشیِ تازه می‌گیرد (ایمیل تأیید می‌خواهد). دفترِ «اثرِ انگشتِ
  دستگاه» ساخته نشد: شناسهٔ دستگاه از خودِ برنامه می‌آید و با پاک کردنِ داده عوض می‌شود، و بستنِ آزمایشیِ حسابِ دوم روی یک
  گوشیِ مشترک (شاگردی که دکانِ خودش را می‌سازد) تصمیمِ تجاری است؛ اگر خواسته شد: مهاجرتِ ۱۶۲ (سهمِ همین شاخه) پشتِ پرچمِ خاموش.
- **سقفِ دستگاه:** SEC-CLI-01 بالا.
- **مهلتِ آفلاین:** مجوزِ ES256 بسته به `duid`، حساب و دکان؛ `exp = min(iat + 10 روز, graceEndsAt)` (سرور؛ آزمون‌های `license-format` و
  `subscription-tamper`)؛ برنامه ساعتی را که فقط جلو می‌رود نگه می‌دارد (`LicenseGuard.trustedNow`، ارفاقِ یک روزه برای منطقهٔ
  زمانی). مجوز، کلید و کفِ ساعت در `tohid-sync` / `tohid-session*` اند که با پشتیبانِ اندروید نمی‌روند. کدِ آفلاینِ مدیر بسته به
  کامپیوتر و حساب و یک‌بار برای یک دکان است (`shop-offline-code.test.js`).
- **بازسنجی پس از اتصال:** سیاستِ D-05 — نوشتنِ صاحب همیشه پذیرفته؛ نوشتنِ شاگرد بی `multi_device` با `subscription_required`
  رد و روی گوشی می‌ماند تا تمدید (`requireDataWrite`، Sync v1/v3؛ `paid-routes-entitlement`).
- **پایانِ اشتراک داده را پاک نمی‌کند:** خواندن هرگز بسته نیست و خروجیِ صاحب پشتِ اشتراک نیست (SUB-07: `owner-export`،
  `entitlement-enforcement`).

## ۴. بازرسیِ کلاینت — آن‌چه درست بود (با شاهدِ کد)

**اندروید (`android-native`):** انتشار با R8 و `shrinkResources`، بی `debuggable`؛ `network_security.xml`ِ انتشار بی cleartext و فقط
CAِ سیستم (نسخهٔ http و CAِ کاربر فقط در `src/debug`)؛ نشانیِ سرور فقط از ساخت (`AppConfig.allowInsecure = BuildConfig.DEBUG`، کادرِ
نشانی در انتشار نیست)؛ یک Activityِ صادرشده (فقط LAUNCHER، بی deep link)، `FileProvider` صادرنشده با سه پوشهٔ کَش؛ توکن در
`EncryptedSharedPreferences`/Keystore (`tohid-session-secure`) و همراهِ PINِ شاگرد، شناسهٔ دستگاه و سریِ سند از پشتیبانِ ابری و انتقال بیرون
(`backup_rules*.xml`، `TohidBackupAgent`، `DeviceOnlyPrefsTest`)؛ `SavedLogins` بی توکن؛ در کلِ برنامه یک `Log.d` (خطای فریمِ
بارکد، بی داده)؛ سنجشِ امضای خودِ برنامه (`Integrity`) — که با SEC-CLI-02 ارزشش رفته. اجازه‌ها هر کدام با کارِ نوشته‌شده (دوربین،
میکروفونِ پیامِ صوتی، مکان یک‌باره، بلوتوث/USBِ چاپگر، نصبِ به‌روزرسانی). `FLAG_SECURE` در برنامهٔ دکان نیست (سیاستی نخواسته؛
برنامهٔ مدیر دارد).

**برنامهٔ مدیر:** `allowBackup="false"`، `FLAG_SECURE`، بی cleartext، توکن در `EncryptedSharedPreferences` (SEC-CLI-08)، R8.

**کامپیوتر:** نصب بی مدیر (`PrivilegesRequired=lowest`، `%LOCALAPPDATA%\Tohid`)؛ حذف فقط `app\` و `runtime\` (داده می‌ماند)؛ نصب‌کنندهٔ آنلاین
SHA-256ِ بستهٔ دانلودی را که هنگامِ ساخت در خودش نوشته شده می‌سنجد؛ IPCِ تک‌نمونه فقط روی loopback و فقط فرمانِ `show`؛ به‌روزرسان
آرگومان‌های ثابت و سنجشِ جمعِ کنترلی (+ امضا وقتی کلید سنجاق شود). ترتیبِ جست‌وجوی DLL: لانچرِ jpackage با runtimeِ همراه در پوشهٔ
خودِ کاربر — پوشه برای همان کاربر نوشتنی است ولی هیچ بالا رفتنِ دسترسی‌ای در کار نیست، پس کاشتنِ DLL چیزی بیش از دسترسیِ خودِ
کاربر نمی‌دهد. امضای کد (Authenticode، notarizeِ اپل): گواهی نیست ⇒ کارِ صاحب محصول.

## ۵. اجرا شده (۱۴۰۵/۰۸/۰۹، روی همین شاخه)

- PGlite: `sec-sub-device-cap` ۴/۴، `sec-sub-trial` ۲/۲، `sec-sub-replay` ۳/۳، `license-format` ۵/۵، `subscription-tamper` ۵/۵؛
  رگرسیونِ `license`، `pump-isolation`، `sync-client`، `sync-live-ticket`، `sync-setting-lists`، `sync-v3-client`، `sync-v3-to-v1`،
  `security-hardening`، `paid-routes-entitlement` ⇒ ۱۲۷ سبز، ۱ سرخِ محیطی (`pump-isolation` › «سرور را به کامپیوترِ دیگر ببر»: `pg_dump`ِ
  این ماشین با نقشِ `root` وصل نمی‌شود — بی‌ربط به این تغییر).
- PostgreSQL ۱۶ (پایگاهِ آزمایشیِ جدا): `sec-sub-device-cap` ۴/۴ و `sec-sub-replay` ۳/۳، به‌علاوهٔ شش جهشِ بالا (همه سرخ شدند).
- اندروید و کامپیوتر: ساخت این‌جا ممکن نیست (Gradle) ⇒ CIِ #580.
