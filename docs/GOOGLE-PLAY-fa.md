# انتشار در Google Play — چک‌لیستِ صاحب‌کار

آخرین بازبینی: ۱۴۰۵/۰۸/۱۳ (۴ نوامبر ۲۰۲۶)، نسخهٔ 2.14.5. هر قاعده از صفحهٔ رسمیِ Google در همین روز خوانده شد
(فهرستِ منبع‌ها پایانِ همین سند). قاعده‌های Play زود عوض می‌شوند؛ پیش از فرستادن، صفحه‌های بخشِ «منبع‌ها» را یک بار دیگر ببینید.

نشانه‌ها: ✅ انجام شده · 🟡 کارِ شما در Play Console · ⚠️ نامطمئن یا نیازمندِ تصمیمِ شما.

---

## ۰. خلاصه در پنج خط

1. برنامه از نظرِ کد آمادهٔ Play است: نسخهٔ جدای Play (`-Pstore=play`) با هدفِ Android 16، بی اجازهٔ نصبِ بسته، بی به‌روزرسانیِ
   داخلی و بی خرید با واتساپ ساخته می‌شود و CI هر بار آن را می‌سازد و می‌سنجد.
2. «حذفِ حساب» حالا هم در برنامه هست، هم در وب (پورتال)، هم در صفحهٔ حریمِ خصوصی. حذفِ خودکار **پیش‌فرض خاموش** است؛ تا
   روشنش نکنید، همان دکمه درخواست را به پشتیبانی می‌فرستد.
3. ⚠️ سه تصمیمِ بزرگ با شماست (بخشِ ۱): حسابِ توسعه‌دهنده از کدام کشور، کلیدِ امضا، و فروشِ اشتراک داخلِ برنامهٔ Play.
4. 🟡 در Play Console حدودِ ده فرم پر می‌شود (بخشِ ۴)؛ جوابِ پیشنهادیِ فرمِ «ایمنیِ داده» در بخشِ ۵ است.
5. 🟡 برای حسابِ شخصیِ تازه، پیش از انتشارِ همگانی ۱۴ روز آزمونِ بسته با دست‌کم ۱۲ نفر لازم است.

---

## ۱. پیش از هر کاری — سه تصمیم (⚠️)

### ۱.۱ حسابِ توسعه‌دهنده و کشور

- ساختنِ حسابِ Play Console ۲۵ دلار (یک بار) است و هویتِ شما (شخص یا شرکت) تأیید می‌شود. شرکت شمارهٔ D-U-N-S می‌خواهد (رایگان،
  تا ۲۸ روز طول می‌کشد).
- ⚠️ **کشورِ تحریم:** Google نوشته است توسعه‌دهندگانی که در کشورهای زیرِ تحریمِ کامل هستند نمی‌توانند تأییدِ هویتِ توسعه‌دهنده را
  انجام دهند. اگر حساب را از چنین کشوری (مثلاً ایران) بسازید، احتمالاً ساختن یا پرداختِ حساب نمی‌شود. اگر در افغانستان یا کشورِ
  دیگری هستید، همان صفحهٔ ثبت‌نام می‌گوید پذیرفته است یا نه — من نتوانستم فهرستِ کشورها را قطعی ببینم.
- حسابِ **شخصیِ** تازه (ساخته‌شده پس از ۱۳ نوامبر ۲۰۲۳) پیش از انتشارِ همگانی باید یک «آزمونِ بسته» با دست‌کم **۱۲ آزمایش‌کننده**
  به مدتِ **۱۴ روزِ پیاپی** داشته باشد. حسابِ شرکت (با D-U-N-S) این را ندارد.

### ۱.۲ کلیدِ امضا (مهم)

- ⚠️ فایلِ کلیدِ امضای امروزِ برنامه (`android-native/app/tohid-release.jks`) و رمزِ پیش‌فرضش **در مخزنِ عمومیِ گیت‌هاب** است.
  هر کسی می‌تواند با آن برنامه‌ای بسازد که اندروید «همان برنامه» بداند. این کلید را به Play ندهید.
- پیشنهاد: در Play Console **Play App Signing** را با کلیدی که خودِ Google می‌سازد بپذیرید و یک **کلیدِ بارگذاریِ تازه**
  (upload key) فقط برای Play بسازید و جایی امن (نه در مخزن) نگه دارید. برنامه‌های تازه در Play از اول با Play App Signing و
  فایلِ AAB منتشر می‌شوند.
- پیامدِ این انتخاب: نسخهٔ Play و APK ِ سایت امضای جدا دارند؛ گوشی‌ای که APK ِ سایت را دارد نمی‌تواند از Play به‌روز شود و باید
  یک بار برنامه را پاک و از Play نصب کند. **پیش از پاک کردن، همگام‌سازی باید کامل باشد**؛ وگرنه دادهٔ فقط‌روی‌گوشی می‌رود.
- ⚠️ «تأییدِ توسعه‌دهندهٔ اندروید» (Android developer verification): Google از ۳۰ سپتامبر ۲۰۲۶ در چهار کشور (برزیل،
  اندونزی، سنگاپور، تایلند) و از ۲۰۲۷ در همهٔ دنیا فقط برنامه‌ای را روی گوشیِ دارای Google نصب می‌کند که نامِ بسته‌اش
  (`ir.vil3ntec.tohid`) به نامِ یک توسعه‌دهندهٔ تأییدشده و کلیدِ امضایش ثبت شده باشد — **APK ِ سایت و بازار هم**. برنامه‌های
  Play خودکار ثبت می‌شوند. چون این نامِ بسته پیش از Play روی گوشی‌ها بوده، Google ممکن است اثباتِ مالکیتِ کلیدِ امروز را
  بخواهد؛ یک بسته می‌تواند چند کلیدِ ثبت‌شده داشته باشد. ⚠️ اگر کشورِ شما زیرِ تحریمِ کامل باشد این ثبت ممکن نیست، ولی
  Google می‌گوید این سنجش روی گوشی‌هایی که در همان کشورها هستند اجرا نمی‌شود.
- این PR به کلید و رمزها و secretهای گیت‌هاب **دست نزد**.

### ۱.۳ فروشِ اشتراک در نسخهٔ Play

- قاعدهٔ پرداختِ Play: هر چیزِ دیجیتالی که داخلِ برنامه فروخته شود (اشتراک هم) باید با **Google Play Billing** باشد و برنامه نباید
  کاربر را به پرداختِ بیرونی بفرستد (بیرون از آمریکا).
- ✅ نسخهٔ Play مثلِ نسخهٔ بازار دکمهٔ «خرید با واتساپ» و «پرداخت از راهِ واتساپ» ندارد.
- ⚠️ «کد اشتراک دارم» (واردکردنِ کدی که بیرون خریده شده) و دیدنِ اشتراکِ حسابی که بیرون خریده شده می‌ماند. قاعدهٔ Play دیدنِ
  خریدِ بیرونی را منع نمی‌کند ولی نمی‌دانم بازبین «وارد کردنِ کد» را چطور می‌بیند. اگر رد شد، راه‌ها: پنهان کردنِ همان ردیف در
  نسخهٔ Play، یا افزودنِ Play Billing (کارِ جدا).
- ⚠️ صفحهٔ «پلن‌ها» قیمت‌ها را نشان می‌دهد ولی دکمهٔ خرید ندارد؛ معمولاً مشکلی نیست، ولی قطعی نیست.

---

## ۲. آنچه در این PR انجام شد ✅

| کار | کجا |
|---|---|
| نسخهٔ Play: `gradle bundleRelease -Pstore=play` — هدفِ API 36 (لازم از ۳۱ اوت ۲۰۲۶؛ مهلتِ تمدید تا ۱ نوامبر ۲۰۲۶ هم گذشته)، بی `REQUEST_INSTALL_PACKAGES`، بی به‌روزرسانیِ داخلی، بی خرید با واتساپ | `android-native/app/build.gradle.kts`، `core/…/platform/StoreChannel.kt` |
| APK ِ سایت و بازار بیت‌به‌بیت همان می‌مانند (هدفِ ۳۵) تا رفتارِ گوشیِ دکان‌ها عوض نشود | همان |
| CI: کارِ تازهٔ «نسخهٔ Google Play (AAB)» هر بار AAB را می‌سازد و می‌سنجد (هدفِ ۳۶، نبودنِ اجازهٔ نصب، هم‌ترازیِ ۱۶KB) و در artifact ِ `MyShop-Play` می‌گذارد | `.github/workflows/tohid-native.yml`، `android-native/tools/play-check.py` |
| «حذفِ حساب» در برنامه: «حساب من ← ویرایش حساب ← حذفِ حساب» با پیش‌نمایشِ «چه پاک می‌شود، چه می‌ماند»، تأییدِ دوبارهٔ رمز و نوشتنِ ایمیل/شمارهٔ حساب | `ui/v3/account/profile/DeleteAccountSheet.kt` |
| «حذفِ حساب» در وب بی برنامه: پورتال ← برگهٔ «حذفِ حساب» (`https://api.vill3n.top/portal/#delete-account`) | `server/public/portal/` |
| سرور: همان حذفِ کاملِ پنلِ مدیر (`lib/user-delete.js`) برای خودِ کاربر؛ پرچمِ `account.self_delete` **پیش‌فرض خاموش**؛ حسابِ پمپ‌دار فقط از پشتیبانی | `server/src/routes/account-deletion.js`، `server/src/lib/self-delete.js` |
| خاموش بودنِ پرچم ⇒ همان دکمه یک پیامِ «درخواستِ حذف» به پشتیبانی می‌فرستد (Play درخواستِ حذف را هم می‌پذیرد) | همان |
| صفحهٔ حریمِ خصوصی: بخشِ `#delete-account` (راهِ برنامه، راهِ وب، چه می‌ماند) و اجازه‌ها و داده‌های جاافتاده (میکروفون، NFC، دسترسی به اعلان، گزارشِ خطا، گفت‌وگو، ML Kit) | `privacy.html` |
| پیوندِ «حریمِ خصوصی» داخلِ برنامه (قاعدهٔ Play): «درباره برنامه» و «ویرایش حساب»؛ جست‌وجوی تنظیمات «حذف حساب» و «حریم خصوصی» را پیدا می‌کند | `AboutScreen.kt`، `ProfileEditScreen.kt`، `SettingsSearchIndex.kt` |

### آنچه سنجیده شد و درست بود (بی تغییر)

| موضوع | نتیجه |
|---|---|
| اجازه‌های مانیفست | همه به کار می‌روند: دوربین (بارکد)، میکروفون (پیامِ صوتیِ گفت‌وگو، پشتِ پرچم)، مکانِ تقریبی/دقیق (یک بار هنگامِ ثبت‌نام، و بلوتوثِ اندروید ۱۱ و پایین‌تر)، اعلان، بلوتوث (چاپگر و بارکدخوان)، NFC (برچسبِ حساب‌پی)، سرویسِ پیش‌رو + روشن شدنِ گوشی (بارکدخوانِ پس‌زمینه). تنها اجازهٔ نامجاز برای Play، `REQUEST_INSTALL_PACKAGES`، در نسخهٔ Play برداشته می‌شود. |
| اجازه‌های محدودِ Play | پیامک و تماس، `QUERY_ALL_PACKAGES`، هشدارِ دقیق (`SCHEDULE_/USE_EXACT_ALARM`)، مکانِ پس‌زمینه، `READ_MEDIA_*`، مخاطبان و اعلانِ تمام‌صفحه **هیچ‌کدام در برنامه نیستند** ⇒ فرمِ اعلامِ اجازه برای آن‌ها لازم نیست. |
| سرویسِ پیش‌رو | دو سرویس، هر کدام نوعش را هم در مانیفست و هم در `startForeground` دارد: `ScanCaptureService` ‏`connectedDevice` (بارکدخوانِ پس‌زمینه) و از DLV-16 ‏`CourierTrackService` ‏`location` (موقعیتِ پیک فقط «در راه»، با اجازهٔ مکانِ «هنگامِ استفاده»؛ مکانِ پس‌زمینه خواسته نمی‌شود)؛ کارهای پس‌زمینهٔ WorkManager سرویسِ پیش‌رو نمی‌خواهند. |
| اجزای بیرونی (`exported`) | فقط صفحهٔ اصلی، پیوندِ `vill3n://` (پیش‌فرض خاموش)، سرویسِ NFC (فقط سیستم با `BIND_NFC_SERVICE`) و سرویسِ اعلان (فقط سیستم با `BIND_NOTIFICATION_LISTENER_SERVICE`). بقیه `exported=false`. |
| پشتیبانِ اندروید | توکنِ ورود، PIN کارمندان و سریِ سند بیرون از پشتیبان؛ با «رمزگذاریِ دفتر» دفترِ رمزدار هم نمی‌رود. |
| ترافیکِ بی‌رمز (HTTP) | در نسخهٔ انتشار بسته است (`cleartextTrafficPermitted="false"`). |
| صفحه‌های ۱۶ کیلوبایتی | در آخرین APK ِ منتشرشده (3.2.337) هر چهار کتابخانهٔ بومیِ ۶۴بیتی (`libbarhopper_v3`، `libimage_processing_util_jni`، `libsurface_util_jni`، `libandroidx.graphics.path`) هم در ELF و هم در zip ۱۶KB-هم‌ترازند؛ AGP 8.7.3 (بالاتر از 8.5.1). کتابخانهٔ ۳۲بیتیِ `libbarhopper_v3` ۴KB است که برای قاعدهٔ Play (فقط ۶۴بیتی) مهم نیست. CI از این پس هر AAB ِ Play را می‌سنجد. |

### کارهایی که عمداً نکردم

- کلید، رمز و secretهای امضا و تنظیمِ انتشار دست نخورد.
- هدفِ APK ِ سایت و بازار ۳۵ ماند؛ اگر خواستید آن‌ها هم ۳۶ شوند، کارِ جدایی است (رفتارِ برگشت و صفحه‌های بزرگِ اندروید ۱۶ آزموده شود).
- نسخهٔ Play هنوز روی سرورِ انتشار (Release) نمی‌نشیند؛ فقط artifact ِ CI است.

---

## ۳. ساختنِ فایلِ AAB برای Play

- **راهِ ساده (برای آزمونِ داخلی):** هر اجرای CI ِ «توحید بومی» artifact ِ `MyShop-Play` (فایلِ `MyShop-Play.aab`) دارد. این فایل
  با همان کلیدِ امروز امضا شده؛ اگر در Play Console کلیدِ بارگذاریِ تازه گذاشتید، Play آن را **نمی‌پذیرد**.
- **با کلیدِ بارگذاریِ تازه (پیشنهادی):** روی کامپیوترِ خودتان در پوشهٔ `android-native`:
  ```
  TOHID_KEYSTORE=/مسیر/upload.jks TOHID_KEYSTORE_PASSWORD=… TOHID_KEY_ALIAS=upload TOHID_KEY_PASSWORD=… \
  TOHID_API_BASE=https://api.vill3n.top TOHID_LICENSE_PUBLIC_KEY=<همان کلیدِ عمومیِ مجوز> \
  gradle bundleRelease -Pstore=play -PbuildNumber=<عددی بزرگ‌تر از بارِ پیش>
  ```
  فایل: `app/build/outputs/bundle/release/app-release.aab`. شمارهٔ نسخه (versionCode) = ۱۰۰ + `buildNumber` و هر بارگذاری باید
  از قبلی بزرگ‌تر باشد. ⚠️ secretهای گیت‌هاب را به کلیدِ بارگذاری عوض نکنید: همان‌ها APK ِ سایت را هم امضا می‌کنند.
- پیش از بارگذاری: `python3 tools/play-check.py app/build/outputs/bundle/release/app-release.aab <مانیفستِ ساخته‌شده>` (همان سنجشِ CI).

---

## ۴. کارهای Play Console، گام‌به‌گام 🟡

1. **ساختِ حساب** و تأییدِ هویت (بخشِ ۱.۱). در «Android developer verification» ببینید بسته ثبت شده است.
2. **Create app:** نام «فروشگاه من» (یا نامِ دلخواه)، زبانِ پیش‌فرض «فارسی»، نوع App، رایگان. ⚠️ اگر اشتراک را در Play
   می‌فروشید «رایگان با خریدِ درون‌برنامه»؛ فعلاً خرید ندارد.
3. **Play App Signing:** «Let Google create and manage my app signing key» و بارگذاریِ کلیدِ بارگذاری (بخشِ ۱.۲).
4. **App content** (Policy and programs ← App content):
   - **Privacy policy:** `https://vil3ntec-it.github.io/shop/privacy.html`
   - **App access:** «All or some functionality is restricted» ⇐ یک حسابِ آزمایشی (ایمیل و رمز) با دکانِ نمونه برای بازبین
     بسازید و همین‌جا بنویسید؛ بگویید ورود با «ایمیل و رمز» است و اشتراکِ آزمایشیِ هفت‌روزه همه‌چیز را باز می‌کند.
   - **Ads:** No.
   - **Content rating** (پرسش‌نامهٔ IARC): دستهٔ «Utility, Productivity, Communication, or Other». خشونت، جنسی، مواد، قمار: همه No.
     «کاربران با هم ارتباط دارند یا محتوا می‌فرستند؟» ⇐ ⚠️ **Yes** اگر «گفت‌وگو با مشتری» را برای دکان‌ها روشن می‌کنید (پیام،
     عکس، صدا میانِ دکان و مشتری)، وگرنه No. «مکانِ کاربر با دیگران» ⇐ No. «خریدِ دیجیتال» ⇐ No. نتیجهٔ احتمالی: همه/۳+.
   - **Target audience:** فقط «۱۸ سال و بالاتر» (برنامهٔ کار و کسب)؛ «برای کودکان جذاب است؟» No.
   - **News app:** No. **Government app:** No. **Health:** No.
   - **Financial features:** ⚠️ برنامه دفترِ فروش، قرضِ مشتری (نسیهٔ خودِ دکان)، دریافت و حساب‌پی (پول مستقیم از مشتری به
     حسابِ حساب‌پیِ دکان) دارد، ولی خودش نه پول نگه می‌دارد، نه جابه‌جا می‌کند، نه وام یا اعتبار می‌دهد. پیشنهادِ من: اگر گزینهٔ
     «Other» هست، همان را با این شرح: «Point-of-sale and bookkeeping for small shops: records sales, store-credit tabs and
     expenses, and shows a HesabPay payment link/NFC tag so customers pay the shop directly. The app does not hold, transfer or
     lend money.» اگر نبود: «My app doesn't provide any financial features». ⚠️ این تفسیرِ من است؛ اگر Google سند خواست، همین شرح را بدهید.
   - **Data safety:** بخشِ ۵.
   - **Account deletion** (درونِ Data safety): «Yes, users can delete their account» و نشانی:
     `https://vil3ntec-it.github.io/shop/privacy.html#delete-account`. «امکانِ حذفِ بخشی از داده بی حذفِ حساب» ⇐ ⚠️ Yes
     (کالا، مشتری و پشتیبان‌های ابری را خودِ کاربر در برنامه پاک می‌کند؛ فروشِ ثبت‌شده فقط لغو می‌شود — اگر مطمئن نیستید No بزنید).
   - **Foreground service permissions:** نوعِ `connectedDevice` ⇐ کاربردِ «Connect to a Bluetooth barcode scanner the user
     paired, while the app is closed». ویدیوی کوتاه (مثلاً در یوتیوبِ unlisted): «دستگاه‌ها» ⇐ افزودنِ بارکدخوانِ بلوتوثی ⇐
     بستنِ برنامه ⇐ اعلانِ همیشگی ⇐ اسکن ⇐ کالا در برنامه.
     نوعِ `location` (از DLV-16) ⇐ کاربردِ «Share the delivery courier's approximate location with the shop and the
     customer of the order being delivered, only between pick-up and arrival». ویدیو: حالتِ پیک ⇐ «بسته را گرفتم» ⇐
     اعلانِ همیشگیِ «موقعیتِ شما برای همین سفارش» ⇐ صفحهٔ پیگیریِ مشتری ⇐ «رسیدم» ⇐ اعلان می‌رود. ⚠️ تا این فرم پر
     نشده، نسخهٔ Play با این سرویس پذیرفته نمی‌شود (پیش‌فرضِ دکان خاموش است، ولی اعلامیه به مانیفست بسته است).
   - **Advertising ID:** No (برنامه شناسهٔ تبلیغاتی نمی‌خواند).
5. **Store listing:** نام (تا ۳۰ نویسه)، شرحِ کوتاه (تا ۸۰)، شرحِ کامل (تا ۴۰۰۰)، آیکونِ ۵۱۲×۵۱۲، تصویرِ شاخص ۱۰۲۴×۵۰۰،
   دست‌کم ۲ عکسِ صفحهٔ گوشی، ایمیلِ تماس (اجباری)، دسته‌بندی «Business». متنِ پیشنهادی در بخشِ ۶.
6. **کشورها:** فقط کشورهایی که خدمت می‌دهید.
7. **انتشار:** Internal testing (برای خودتان) ⇐ Closed testing (۱۲ نفر، ۱۴ روز، برای حسابِ شخصی) ⇐ Production.
8. **پیش از Production:** پرچمِ سرورِ `account.self_delete` را در پنلِ مدیر («پرچم‌ها»، scope `all`، مقدار `true`) روشن کنید و
   یک بار با حسابِ آزمایشی حذف را از برنامه و از پورتال امتحان کنید. ⚠️ ببینید `https://api.vill3n.top/portal/` باز می‌شود و
   ورود با کدِ ایمیل کار می‌کند (فرستادنِ ایمیل روی سرورِ اصلی باید روشن باشد). اگر پرچم خاموش بماند هم پذیرفتنی است، به شرطِ
   این‌که درخواست‌های حذف از پشتیبانی واقعاً و زود انجام شوند.

---

## ۵. فرمِ «ایمنیِ داده» (Data safety) — جواب‌های پیشنهادی

بر پایهٔ کدِ امروز. «جمع‌آوری» یعنی داده از گوشی به سرورِ «فروشگاه من» یا به Google می‌رود. چیزی که فقط روی گوشی می‌ماند
(دفترِ بی‌حساب، متنِ اعلانِ حساب‌پی، عکسِ دوربینِ بارکد، پشتیبانِ خودکارِ اندروید در Google Drive ِ خودِ کاربر) جمع‌آوری نیست.

**پرسش‌های کلی**

| پرسش | جواب |
|---|---|
| Does your app collect or share any of the required user data types? | Yes |
| Is all of the user data collected by your app encrypted in transit? | Yes (فقط HTTPS؛ HTTP در نسخهٔ انتشار بسته است) |
| Do you provide a way for users to request that their data is deleted? | Yes (برنامه، پورتال، پشتیبانی) |
| داده با شخصِ ثالث «Share» می‌شود؟ | No — ⚠️ ML Kit ِ Google به‌عنوانِ ارائه‌دهندهٔ سرویس حساب می‌شود و حساب‌پی را خودِ کاربر (مشتری) برای پرداخت باز می‌کند؛ هر دو طبقِ تعریفِ Google «اشتراک» نیستند. اگر حالتِ «خودکارِ» حساب‌پی (کلیدِ فروشنده روی سرور) را روشن کردید، ⚠️ دوباره بسنجید. |

**نوع‌های داده** (همه: Collected؛ Shared: No؛ پردازش: Not ephemeral)

| دسته ← نوع | چرا (Purpose) | اختیاری؟ |
|---|---|---|
| Personal info ← Name | App functionality، Account management | اختیاری (بی حساب هم برنامه کار می‌کند) |
| Personal info ← Email address | Account management، App functionality | اختیاری |
| Personal info ← Phone number | Account management (و شمارهٔ مشتری‌هایی که دکان ثبت می‌کند) | اختیاری |
| Personal info ← Address | App functionality (نشانیِ تحویلِ سفارشِ آنلاین، اگر دکان روشن کند) ⚠️ | اختیاری |
| Location ← Approximate location | App functionality، Fraud prevention, security (یک بار هنگامِ ثبت‌نام، با اجازه) | اختیاری |
| Location ← Precise location | App functionality (اگر کاربر «دقیق» بدهد؛ تأییدِ تحویلِ پیک نزدیکِ نشانی) | اختیاری |
| Financial info ← Purchase history | App functionality (فروش‌های دکان، خریدِ اشتراک) | اختیاری |
| Financial info ← Other financial info | App functionality (قرضِ مشتری، دریافت‌ها، شمارهٔ پیگیریِ حساب‌پی) | اختیاری |
| Messages ← Other in-app messages | App functionality (گفت‌وگو با مشتری و پشتیبانی) | اختیاری |
| Photos and videos ← Photos | App functionality (عکسِ کالا و ویترین، عکسِ گفت‌وگو) | اختیاری |
| Audio ← Voice or sound recordings | App functionality (پیامِ صوتیِ گفت‌وگو) | اختیاری |
| App activity ← Other user-generated content | App functionality (دفترِ دکان: کالا، فروش، مصارف، پشتیبانِ ابری) | اختیاری |
| App info and performance ← Crash logs | App functionality (گزارشِ از کار افتادن، بی دادهٔ شخصی) | **الزامی** (خودکار) |
| App info and performance ← Diagnostics | Analytics (ML Kit: مدل و نسخهٔ دستگاه، نامِ برنامه، به Google) | **الزامی** |
| Device or other IDs ← Device or other IDs | App functionality، Analytics، Fraud prevention, security (شناسهٔ دستگاهِ خودِ برنامه: سقفِ دستگاه و «بازدید» ِ هر شش ساعت، حتی بی حساب) | **الزامی** |

«نه» برای: Contacts، Calendar، Web browsing، Health and fitness، Files and docs، Installed apps، SMS/Call log، Race/Religion و …،
Financial ← Credit score / User payment info (شمارهٔ کارت خوانده نمی‌شود)، Advertising ID.

⚠️ اگر بعداً اعلانِ گوشی با Firebase (کارِ در جریانِ `push-fcm`) یا هر SDK ِ تحلیل اضافه شد، همین فرم باید دوباره دیده شود.

---

## ۶. متن‌هایی که لازم است

- **نامِ برنامه (≤ ۳۰):** فروشگاه من — صندوق و حساب دکان
- **شرحِ کوتاه (≤ ۸۰):** صندوقِ فروش، قرضِ مشتری، انبار و گزارش برای دکان — بی اینترنت هم کار می‌کند
- **شرحِ کامل (پیش‌نویس):**
  > «فروشگاه من» صندوق و دفترِ حسابِ دکان‌های کوچک است. فروش را با بارکد یا جست‌وجو ثبت کنید، فاکتور چاپ کنید یا برای مشتری
  > بفرستید، قرضِ هر مشتری و دریافت‌ها را نگه دارید، موجودیِ انبار و تاریخِ انقضا را ببینید و گزارشِ روز و ماه بگیرید.
  > بی اینترنت هم کار می‌کند؛ با حساب، دفتر میانِ گوشی‌ها و کامپیوترهای دکان همگام می‌شود و پشتیبان می‌گیرد.
  > چاپگرِ حرارتی و بارکدخوانِ بلوتوثی، کارمند با نقش و اجازهٔ جدا، و پرداختِ حساب‌پی با QR یا NFC.
  > تبلیغ ندارد و داده‌های دکانِ شما فروخته نمی‌شود. حریمِ خصوصی: https://vil3ntec-it.github.io/shop/privacy.html
- **نشانی‌ها:** حریمِ خصوصی `https://vil3ntec-it.github.io/shop/privacy.html` · حذفِ حساب
  `https://vil3ntec-it.github.io/shop/privacy.html#delete-account` · حذف از وب `https://api.vill3n.top/portal/#delete-account`
  · وب‌سایت `https://vil3ntec-it.github.io/shop/`
- **ایمیلِ تماس:** ⚠️ یک ایمیلِ واقعی که می‌خوانید (در Play عمومی دیده می‌شود).
- **شرحِ حسابِ آزمایشی برای بازبین** (App access): «Sign in with email and password on the first screen. The account has a
  sample shop with products and sales; the 7-day trial unlocks all features.»

---

## ۷. ریسک‌های بازبینی که از پیش بدانید (⚠️)

- **دسترسی به اعلان** (`NotificationListenerService` برای پیشنهادِ «پول رسید» از اعلانِ حساب‌پی): Play فرمِ جدا برایش ندارد،
  ولی بازبین ممکن است بپرسد. پیش‌فرض خاموش است و متنِ اعلان از گوشی بیرون نمی‌رود؛ در `privacy.html` نوشته شد. اگر سخت گرفتند،
  همین قابلیت را در نسخهٔ Play برمی‌داریم.
- **مکانِ دقیق:** قاعدهٔ تازهٔ مکانِ Play (اعلام‌شده ۱۵ آوریل ۲۰۲۶، اجرا از ۲۷/۲۸ اکتبر ۲۰۲۶) برای مکانِ دقیقِ **همیشگی** اعلامیه
  می‌خواهد؛ برنامه فقط یک بار و در پیش‌زمینه می‌گیرد، پس نباید لازم شود. برای برنامه‌های هدفِ Android 17 «دکمهٔ مکان» خواسته
  می‌شود — نسخهٔ Play امروز ۱۶ است.
- **کد اشتراک و قیمت‌ها:** بخشِ ۱.۳.
- **مهلتِ بعدی:** هر سال اوت Google هدفِ API را یک پله بالا می‌برد (برای ۲۰۲۷ احتمالاً API 37 — هنوز اعلام نشده).

---

## ۸. منبع‌ها (خوانده‌شده در ۴ نوامبر ۲۰۲۶)

- هدفِ API: https://developer.android.com/google/play/requirements/target-sdk و https://support.google.com/googleplay/android-developer/answer/11926878
- صفحه‌های ۱۶KB: https://developer.android.com/guide/practices/page-sizes
- تأییدِ توسعه‌دهنده: https://developer.android.com/developer-verification و https://developer.android.com/developer-verification/guides/faq
- حذفِ حساب و فرمِ ایمنیِ داده: https://support.google.com/googleplay/android-developer/answer/13327111
- سرویسِ پیش‌رو در Play: https://support.google.com/googleplay/android-developer/answer/13392821 و https://developer.android.com/guide/components/fg-service-types
- فرمِ ویژگی‌های مالی: https://support.google.com/googleplay/android-developer/answer/13849271
- پیامک و تماس: https://support.google.com/googleplay/android-developer/answer/16558241
- `QUERY_ALL_PACKAGES`: https://support.google.com/googleplay/android-developer/answer/10158779 · `REQUEST_INSTALL_PACKAGES`: https://support.google.com/googleplay/android-developer/answer/12085295
- هشدارِ دقیق: https://developer.android.com/training/scheduling/alarms
- عکس و ویدیو: https://support.google.com/googleplay/android-developer/answer/14115180 · مخاطبان و مکان (۲۰۲۶): https://support.google.com/googleplay/android-developer/answer/16935362 و https://support.google.com/googleplay/android-developer/answer/16909972
- پرداخت: https://support.google.com/googleplay/android-developer/answer/9858738 و https://play.google.com/about/monetization-ads/payments
- آزمونِ بسته ۱۲ نفر/۱۴ روز: https://support.google.com/googleplay/android-developer/answer/14151465
- دادهٔ ML Kit: https://developers.google.com/ml-kit/android-data-disclosure
- ⚠️ صفحه‌های `support.google.com` از این محیط مستقیم باز نشدند؛ متنشان از نتیجهٔ جست‌وجو خوانده شد. صفحه‌های `developer.android.com` کامل خوانده شدند.
