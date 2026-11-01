# صفحهٔ کنترلِ مدیر (ADMIN-CONTROL-PLANE)

> **سندِ مستقلِ پنلِ مدیریتِ VILL3N** — مکملِ `MASTER-SPEC` نسخهٔ ۳ (بندهای ۳۸.۱ و ۳۹.۸)، نه جایگزینِ آن.
> اگر این سند با سندِ اصلی تضاد داشت، قاعدهٔ امنیتیِ سخت‌گیرانه‌تر و تصمیمِ صریحِ صاحبِ محصول حاکم است (۳۸.۱).
>
> **پایهٔ ممیزی:** کدِ واقعیِ ریپو در commitِ `3f30017` (شاخهٔ `main`، ۱۴۰۵/۰۷/۲۵) — `server/src/routes/admin*.js`،
> `server/src/middleware/auth.js`، `server/src/lib/{audit,permissions,admin-2fa}.js`، مهاجرت‌های `001` و `055`،
> پنلِ وب `server/public/admin/**` و برنامهٔ `admin-android`. هیچ عددی در این سند از گزارشِ قدیمی نیامده؛
> فهرستِ مسیرها از خودِ سورس استخراج شده و آزمونِ `server/test/admin-control-plane.test.js` همان استخراج را
> هر بار دوباره انجام می‌دهد (بند ۹).
>
> بندهای مرتبطِ سندِ اصلی (v3): ۳۹.۸ (این قرارداد)، ۲۹.۴ (جداسازی و حریم خصوصی)، ۱۹.۱۰ (پنلِ نظارت و
> عملیاتِ حساس)، ۲۱.۳ (پشتیبانی و «ورود به‌جای کاربر»)، ۲۱.۱۴ (پنلِ مدیریتی و export)، ۲۲.۲ مرحلهٔ D بندهای ۴ و ۵.

---

## ۱. یافته‌های پیش از تغییر (ممیزی)

| موضوع | آنچه در کد هست | شاهد |
|---|---|---|
| تعدادِ APIهای مدیر | ۱۶۴ مسیر در ۱۲ فایل (۱۵۹ تعریف؛ ۵ تعریف برای دکان و پمپ دو بار سوار می‌شوند) | `server/src/routes/admin*.js` |
| نگهبانِ سمتِ سرور | همهٔ مسیرها به‌جز `POST /admin/login` پشتِ `requireAdmin` (توکنِ نوعِ `admin` + حسابِ فعال) | `routes/admin.js:78` (`router.use(requireAdmin)`)، `admin-notices.js` و `admin-sales.js` خودشان هم `requireAdmin` دارند؛ `middleware/auth.js:293` |
| نقش‌های مدیر | فقط دو نقش: `admin` و `superadmin` (قیدِ CHECK در جدول) | `migrations/001_core.sql:41`؛ `middleware/auth.js:309` |
| صاحبِ سامانه | حسابِ آغازینِ سرور همیشه `superadmin` ساخته می‌شود | `lib/admin-bootstrap.js:32` |
| نقش‌های فروشگاه | `owner/manager/staff` — ماتریسِ جدا، **ربطی به مدیرِ پلتفرم ندارد** | `lib/permissions.js` |
| نمایندهٔ فروش | مدیر نیست؛ کاربرِ عادی با ردیفِ نماینده؛ فقط `GET /api/rep/me` و فقط فروشِ کدهای خودش | `routes/rep.js:1-24` |
| تأییدِ دومرحله‌ای | TOTP برای هر مدیر **اختیاری** است (اگر فعال کرده باشد، ورود بی‌آن رد می‌شود) | `migrations/055_admin_totp.sql`، `routes/admin.js:51-64` |
| سابقه (audit) | `lib/audit.js` — کلیدهای رمز/کد/توکن را پیش از نوشتن پاک می‌کند؛ شکستِ نوشتن عملیات را نمی‌شکند | `lib/audit.js` |
| رازها | کدِ OTP، کدِ ورود و کدِ کارمند فقط با `reveal`ِ مدیرِ ارشد و با ردیفِ سابقه؛ فهرست‌ها فقط `has_code` | `admin-otp.js:47`، `admin-logins.js:73`، `admin-staff-codes.js:98`، `lib/otp.js:543` |

**شکاف‌های تأییدشده که در همین تغییر بسته شدند** (هر کدام اول با آزمونِ شکست‌خورده):

1. دانلودِ پشتیبانِ کاملِ یک حساب (`GET /admin/accounts/:app/:tenantId/backups/:id`) — یعنی «خروجیِ» همهٔ دادهٔ خصوصیِ دکان/پمپ — برای **هر** مدیر باز بود، بی‌دلیل و بی‌سابقه.
2. بازگرداندنِ تعارضِ همگام‌سازی (`POST /admin/sync/conflicts/:id/restore`) — تغییرِ دادهٔ یک حساب — برای هر مدیر، بی‌دلیل.
3. برداشتنِ افزونهٔ پولی (`DELETE …/addons/:addonId`) برای مدیرِ عادی باز بود در حالی که افزودنش فقط کارِ ارشد است؛ و **شناسهٔ اشتراکِ نشانی سنجیده نمی‌شد** (افزونهٔ اشتراکِ الف از نشانیِ اشتراکِ ب برداشته می‌شد و ردیفِ سابقه اشتراکِ غلط را ثبت می‌کرد).
4. ردیفِ سابقه نداشتند: ردِ درخواستِ خرید، تغییرِ شهرِ کاربر، تغییرِ وضعیتِ گفت‌وگوی پشتیبانی.
5. دیدنِ پروندهٔ کاربر (لوکیشنِ دقیق، دستگاه‌ها، تماس)، پروندهٔ دکان (تماسِ اعضا و جای دکان) و تعارض‌های یک حساب (مقدارِ خودِ ردیف‌ها) هیچ ردی نمی‌گذاشت.

---

## ۲. نقش‌ها

### ۲.۱ امروز (در کد)

| نقش در سند | نقش در کد | می‌بیند | می‌کند | نمی‌کند |
|---|---|---|---|---|
| **صاحبِ پلتفرم (Platform Owner)** | `admins.role = 'superadmin'` | هر چه مدیر می‌بیند + فهرستِ مدیران | همهٔ کارهای مدیر + ساختن/غیرفعال‌کردنِ مدیر، تنظیمِ پیامک/ایمیل/پوش/تلگرام، قیمتِ پلن، کدِ VIP/بی‌اینترنت، نمایشِ کدهای یک‌بارمصرف، حذفِ کاملِ کاربر، حذفِ پشتیبان، **خروجیِ پشتیبانِ یک حساب و بازگرداندنِ تعارض (با دلیل)** | ورود با توکنِ دکان؛ دیدنِ رمز؛ کنار گذاشتنِ آخرین ارشدِ فعال یا خودش (`admin.js` PATCH admins) |
| **مدیر/پشتیبانی/عملیات/فروش** (یک نقشِ مشترک) | `admins.role = 'admin'` | آمار، فهرستِ کاربران و دکان‌ها، پروندهٔ کاربر و دکان (با سابقه)، گفت‌وگوهای پشتیبانی، وضعیتِ همگام‌سازی، پرداخت‌ها و گزارشِ فروش | پاسخِ پشتیبانی، اشتراک دادن/تمدید، تأیید/ردِ درخواستِ خرید، ثبتِ پرداخت، کدِ تخفیف، نماینده، اعلانِ جمعی، غیرفعال‌کردنِ کاربر/پمپ | هر مسیرِ «ارشد» (۳۴ تعریف، در جدولِ بند ۶) — آزمون نشان می‌دهد ۴۰۳ می‌گیرد |
| **مالی** (فاز ۳، بند ۱۲) | `admins.role = 'finance'` | فراداده، دکان‌ها و سلامتشان، اشتراک، پرداخت، فروش، درخواستِ خرید، سابقه، پرچم‌ها | دادن/تمدید/وضعیتِ اشتراک، ثبتِ پرداخت، تأیید/ردِ خرید، «پایانِ اشتراکِ» دکان | پشتیبانی، کاربران، اعلانِ جمعی، تنظیم، هر درِ دیگر (پیش‌فرض‌بسته، `lib/admin-roles.js`) |
| **فقط‌خواندن** (فاز ۳) | `admins.role = 'viewer'` | هر چه مدیرِ عادی می‌خواند | هیچ نوشتنی (جز خروج و دوعاملیِ خودش) | هر نوشتن ⇒ ۴۰۳ `role_forbidden` |
| **مالکِ اصلی** (فاز ۳) | `admins.is_owner = true` (یکی از ارشدها) | همان ارشد | همان ارشد | هیچ مدیرِ دیگری غیرفعال/تنزلش نمی‌دهد (`owner_protected`)؛ کلیدِ سرویسی که ساخته را فقط خودش باطل می‌کند |
| **پلِ پنلِ خانگی** (فاز ۳) | کلیدِ سرویس (`admin_service_keys`) + «انجام‌دهنده» | به اندازهٔ کمترینِ «نقشِ انجام‌دهنده» و «سقفِ کلید» | همان | مدیران، کلیدها، دوعاملی، نشست (`service_forbidden`) |
| **نمایندهٔ فروش** | کاربرِ عادی + ردیفِ `sales_reps` | فقط کدها و فروش‌های خودش، نامِ مشتری = نامِ دکان (نه ایمیل/شماره) | — | هیچ مسیرِ `/admin` (توکنش نوعِ کاربر است ⇒ ۴۰۱) |
| **مالکِ دکان / کارمند** | `shop_members.role` | فقط دادهٔ دکانِ خودش (`permissions.js`) | — | هیچ مسیرِ `/admin` (۴۰۱) |

### ۲.۲ هدف (طرح — `finance` و `viewer` از فاز ۳ در کدند، بقیه هنوز نه؛ بند ۸.۲)

`platform_owner` (همان ارشد)، `ops_admin` (همهٔ اختیاراتِ امروزِ `admin` — برای حفظِ رفتار)، `support_agent`،
`sales_manager`، `finance`، `auditor` (فقط خواندنِ سابقه). هر نقش فقط کارهای خودش (v3 ۲۲.۲-D۴).

---

## ۳. اصولِ الزامی و وضعیتِ هر کدام

| شناسه | اصل | وضعیت | شاهد / آزمون |
|---|---|---|---|
| ACP-01 | هر API مدیر مجوزِ سمتِ سرور دارد؛ پنهان‌کردنِ دکمه امنیت نیست (۳۸.۱، ۳۹.۸) | IMPLEMENTED-VERIFIED | آزمونِ «هر API مدیر بی‌توکن و با توکنِ دکان ۴۰۱…»: همهٔ ۱۶۳ مسیرِ غیرِ ورود از سورس استخراج و بی‌توکن و با توکنِ دکان صدا زده می‌شوند؛ هر مسیرِ `requireSuperAdmin` با مدیرِ عادی ۴۰۳ |
| ACP-02 | جداسازیِ tenant **حتی برای صاحبِ پلتفرم** صریح و قابلِ ممیزی است (۳۹.۸، ۲۹.۴-۱) | PARTIAL | توکنِ مدیر توکنِ دکان نیست (آزمونِ «توکنِ مدیر توکنِ دکان نیست»)؛ مسیرهای tenantدار شناسهٔ حساب را در نشانی می‌گیرند؛ خروجی/بازگردانی/دیدنِ پرونده ردیفِ سابقه با `shop_id` می‌نویسند. **باقی:** بقیهٔ خواندن‌های شخصی سابقه ندارند (G4)؛ مجوزِ موقت نیست (G1) |
| ACP-03 | دسترسیِ پشتیبانی به دادهٔ خصوصی: حداقلِ دسترسی + دلیل + مدتِ محدود + audit (۳۹.۸، ۲۹.۴-۴، ۱۹.۱۰-۲) | PARTIAL | خروجی و بازگردانی: ارشد + دلیل + سابقه ✔؛ **مدتِ محدود و رضایتِ فروشگاه: MISSING** (G1، طرح در ۸.۱) |
| ACP-04 | «ورود به‌جای کاربر» موقت، قابلِ لغو، با دلیل، نشانگرِ روشن و audit کامل؛ دیدنِ رمز/OTP/کارت ممنوع (۲۱.۳) | MISSING (عمداً) | در کد هیچ مسیرِ impersonation نیست (جست‌وجوی `impersonat` در `server/src` خالی است). تا ساخته شود، **ممنوع** است؛ طرح در ۸.۱ |
| ACP-05 | export: مجوز، audit، حفاظتِ دادهٔ شخصی، سقفِ حجم (۲۱.۱۴) | PARTIAL | تنها خروجیِ دادهٔ حساب = دانلودِ پشتیبان ⇒ ارشد + دلیل + سابقه ✔. خروجیِ CSV/PDF از پنل وجود ندارد؛ وقتی ساخته شود همین قاعده + job پس‌زمینه و لینکِ زمان‌دار |
| ACP-06 | تغییرِ مالی (اشتراک، پرداخت، تخفیف، افزونه): حداقلِ دسترسی + دلیل + audit (۳۹.۸، ۱۹.۱۰-۳) | PARTIAL | همه سابقه دارند؛ ویرایش/حذفِ پرداخت، پلن، VIP، افزونه = ارشد ✔. **باقی:** دادن/تمدیدِ اشتراک و ثبتِ پرداخت برای مدیرِ عادی باز است (G3) و دلیل اجباری نیست (G2) |
| ACP-07 | restore: حداقلِ دسترسی + دلیل + audit | IMPLEMENTED-VERIFIED (تعارضِ همگام‌سازی) | آزمونِ «بازگرداندنِ تعارض…»؛ بازگرداندنِ پشتیبانِ حساب سمتِ خودِ کاربر است (`/api/me/backups`)، مدیر بازگردانی ندارد |
| ACP-08 | راز هرگز به پشتیبانیِ عادی نشان داده نمی‌شود؛ log رمز/OTP/توکن ندارد (۲۹.۴-۴، ۱۹.۱۰-۲) | IMPLEMENTED-VERIFIED (نگهبان) | هر سه `reveal` و تنظیمِ کلیدها فقط ارشد — آزمونِ فهرستِ کامل (مدیرِ عادی ⇒ ۴۰۳)؛ فهرستِ OTP فقط `has_code` (`lib/otp.js:543`)؛ `audit.scrub` کلیدهای راز را نمی‌نویسد |
| ACP-09 | تأییدِ دوم برای عملیاتِ پرخطر (۱۹.۱۰-۳) و TOTP برای مدیرانِ سرور (SEC-04) | PARTIAL | TOTP هست ولی اختیاری؛ تأییدِ دوم/step-up نیست (G5 — مالکِ آن عاملِ ممیزیِ ورود/نشست است) |
| ACP-10 | هر نوشتنِ مدیر ردیفِ سابقه دارد | IMPLEMENTED (با یک استثنای کم‌خطر) | جدولِ بند ۶: همهٔ نوشتن‌ها «دارد» یا «در lib» یا «در خودِ پیام»؛ استثنا: زمان‌بندیِ اعلان (G6) |

---

## ۴. قاعدهٔ جداسازیِ tenant برای مدیر (صریح)

1. **مدیر هیچ نشستِ فروشگاهی ندارد.** توکنِ `admin` روی هیچ مسیرِ کاربر/دکان پذیرفته نمی‌شود و برعکس (`tokens.verify(token, 'admin')`). آزمون: `GET /api/me` و `GET /api/me/backups` با توکنِ مدیرِ ارشد ⇒ ۴۰۱؛ هر مسیرِ `/admin` با توکنِ دکان ⇒ ۴۰۱.
2. **دیدِ پیش‌فرضِ مدیر «فراداده و شمارش» است، نه محتوا.** پروندهٔ دکان فقط شمارِ ردیف‌ها را می‌دهد (`admin.js` — `COUNT(*) … WHERE shop_id=$1`)، نه خودِ فروش/قرض/مشتری. «مدیرِ پلتفرم برای پشتیبانیِ عادی به همهٔ محتوای خصوصی دسترسیِ پیش‌فرض ندارد» (۲۱.۱۴).
3. **هر جا محتوای خصوصیِ یک حساب بیرون می‌آید، شناسهٔ همان حساب در نشانی است و در ردیفِ سابقه می‌نشیند** (`audit_logs.shop_id` برای دکان؛ `detail.tenantId` برای پمپ). پرس‌وجوها `app` و `tenant_id` را با هم شرط می‌کنند (`lib/account-backups.js:167`).
4. **حتی صاحبِ پلتفرم** برای خروجی/بازگردانی دلیل می‌نویسد و ردِ آن می‌ماند؛ «ارشد بودن» معافیت از سابقه نیست.
5. **سقفِ هدف** (طرح ۸.۱): خواندنِ محتوای خصوصی فقط با مجوزِ موقتِ گره‌خورده به یک tenant؛ مجوزِ حسابِ الف روی حسابِ ب کار نمی‌کند.

---

## ۵. دسترسی‌های حساس — قاعده و وضعیتِ امروز

| دسترسی | قاعده (این سند) | امروز |
|---|---|---|
| دیدنِ دادهٔ شخصی (تماس، لوکیشنِ دقیق، دستگاه، گفت‌وگو) | هر دیدنِ پرونده یک ردیفِ سابقه؛ فهرست‌ها کم‌ترین ستون؛ محتوای کاملِ حساب فقط با مجوزِ موقت | پروندهٔ کاربر/دکان و تعارض‌ها: سابقه ✔ (`admin.user_viewed`، `admin.shop_viewed`، `admin.sync_conflicts_viewed`). فهرست‌ها و گفت‌وگوها: بی‌سابقه (G4) |
| ورود به‌جای کاربر | فقط با مجوزِ موقت + رضایت، فقط‌خواندنی به‌طورِ پیش‌فرض، نشانگرِ دائمی، هر درخواست سابقه | **وجود ندارد و تا ساخته‌شدنِ ۸.۱ ممنوع است** |
| خروجی (export) | ارشد + دلیل + سابقه؛ بزرگ ⇒ job پس‌زمینه و لینکِ زمان‌دار | دانلودِ پشتیبانِ حساب: ارشد + دلیل + سابقه ✔ (`admin.account_backup_downloaded`، با IP و حجم) |
| تغییرِ مالی | نقشِ مالی/ارشد + دلیل + سابقه؛ پرخطر ⇒ تأییدِ دوم | سابقه ✔؛ دلیل اختیاری/نیست (G2)؛ مدیرِ عادی هم می‌تواند (G3) |
| restore | ارشد + دلیل + سابقه | تعارضِ همگام‌سازی ✔ (`sync.conflict_restored` با `reason` و `shop_id`) |
| راز (کدِ یک‌بارمصرف، کلید) | فقط ارشد، هر نمایش سابقه، هرگز در log | ✔ |

---

## ۶. فهرستِ کاملِ APIهای مدیر

پیشوندِ همه `‎/api‎` (و نامِ قدیمیِ `‎/api/v1‎`). «نگهبان»: «مدیر» = `requireAdmin`؛ «ارشد» = `requireAdmin` + `requireSuperAdmin`.
«سابقه»: «دارد» = `audit.log` در همان گرداننده؛ «در lib» = در تابعِ کتابخانه؛ «—» = خواندن بی‌سابقه.
★ = در همین تغییر اضافه/سخت‌تر شد. ستونِ «شکاف» به بند ۷ اشاره می‌کند. شاهد = `server/src/routes/<فایل>:<سطر>`.

| مسیر | نگهبان | tenant | سابقه | رده | شکاف | شاهد |
|---|---|---|---|---|---|---|
| `POST /admin/login` | عمومی (ورود) | سراسری | دارد | نشست | G5 | `admin.js:32` |
| `POST /admin/logout` | مدیر | سراسری | ندارد | نشست | G5 | `admin.js:80` |
| `GET /admin/me` | مدیر | سراسری | — | نشست | G5 | `admin.js:86` |
| `GET /admin/features` | مدیر | سراسری | — | خواندنِ سراسری | — | `admin.js:93` |
| `GET /admin/stats` | مدیر | سراسری | — | خواندنِ سراسری | — | `admin.js:99` |
| `GET /admin/users` | مدیر | سراسری | — | خواندنِ دادهٔ شخصی | G4 | `admin.js:117` |
| `GET /admin/users/:id` | مدیر | کاربر | دارد ★ | خواندنِ دادهٔ شخصی | — | `admin.js:140` |
| `POST /admin/users/:id/status` | مدیر | کاربر | دارد | عملیات | G2 | `admin.js:178` |
| `GET /admin/users/:id/delete-preview` | مدیر | کاربر | دارد ★ (`admin.user_delete_previewed`) | خواندنِ دادهٔ شخصی | — (TEN-04) | `admin.js:193` |
| `DELETE /admin/users/:id` | ارشد | کاربر | دارد | حذف | G2 | `admin.js:200` |
| `GET /admin/shops` | مدیر | سراسری | — | خواندنِ سراسری | — | `admin.js:214` |
| `GET /admin/shops/:id` | مدیر | دکان | دارد ★ | خواندنِ دادهٔ شخصی | — | `admin.js:251` |
| `GET /admin/shops/:id/history` | مدیر | دکان | — | خواندنِ سراسری | — | `admin.js:304` |
| `GET /admin/subscriptions` | مدیر | سراسری | — | خواندنِ سراسری | — | `admin.js:311` |
| `POST /admin/subscriptions` | مدیر | سراسری | دارد | مالی/اشتراک | G2, G3 | `admin.js:328` |
| `PUT /admin/subscriptions/:id` | مدیر | اشتراکِ یک حساب | دارد | مالی/اشتراک | G2, G3 | `admin.js:395` |
| `POST /admin/subscriptions/:id/status` | مدیر | اشتراکِ یک حساب | دارد | مالی/اشتراک | G2, G3 | `admin.js:418` |
| `GET /admin/sms` | مدیر | سراسری | — | خواندنِ سراسری | — | `admin.js:434` |
| `PUT /admin/sms` | ارشد | سراسری | دارد | راز/پیکربندی | — | `admin.js:448` |
| `POST /admin/sms/test` | ارشد | سراسری | دارد | عملیات | — | `admin.js:492` |
| `GET /admin/plans` | مدیر | سراسری | — | خواندنِ سراسری | — | `admin.js:523` |
| `PATCH /admin/plans/:code` | ارشد | سراسری | دارد | مالی/اشتراک | — | `admin.js:531` |
| `PATCH /admin/config` | ارشد | سراسری | دارد | راز/پیکربندی | — | `admin.js:572` |
| `GET /admin/purchase-requests` | مدیر | سراسری | — | خواندنِ دادهٔ شخصی | G4 | `admin.js:591` |
| `POST /admin/purchase-requests/:id/approve` | مدیر | درخواستِ یک دکان | دارد | مالی/اشتراک | G2, G3 | `admin.js:604` |
| `POST /admin/purchase-requests/:id/reject` | مدیر | درخواستِ یک دکان | دارد (+دلیلِ اختیاری) ★ | مالی/اشتراک | G3 | `admin.js:618` |
| `GET /admin/audit` | مدیر | سراسری | — | خواندنِ دادهٔ شخصی | G4 | `admin.js:635` |
| `POST /admin/admins` | ارشد | سراسری | دارد | عملیات | — | `admin-admins.js` |
| `GET /admin/admins` | ارشد | سراسری | — | خواندنِ سراسری | — | `admin-admins.js` |
| `PATCH /admin/admins/:id` | ارشد | سراسری | دارد | عملیات | — | `admin-admins.js` |
| `GET /admin/admins/:id/sessions` | ارشد | سراسری | — | خواندنِ سراسری (بی توکن، فقط ۱۶ نویسهٔ هش) | — | `admin-admins.js` |
| `POST /admin/admins/:id/sessions/revoke` | ارشد | سراسری | دارد (+دلیل) | عملیات | — | `admin-admins.js` |
| `GET /admin/backups` | مدیر | سراسری | — | خواندنِ سراسری | — | `admin.js:690` |
| `POST /admin/backups` | ارشد | سراسری | دارد | خروجی/بازگردانی | — | `admin.js:700` |
| `GET /admin/2fa` | مدیر | سراسری | — | نشست | G5 | `admin-2fa.js:49` |
| `POST /admin/2fa/setup` | مدیر | سراسری | ندارد | نشست | G5 | `admin-2fa.js:51` |
| `POST /admin/2fa/enable` | مدیر | سراسری | دارد | نشست | G5 | `admin-2fa.js:63` |
| `POST /admin/2fa/recovery-codes` | مدیر | سراسری | دارد | نشست | G5 | `admin-2fa.js:84` |
| `POST /admin/2fa/disable` | مدیر | سراسری | دارد | نشست | G5 | `admin-2fa.js:98` |
| `GET /admin/logins` | مدیر | سراسری | — | خواندنِ دادهٔ شخصی | G4 | `admin-logins.js:31` |
| `GET /admin/logins/stats` | مدیر | سراسری | — | خواندنِ سراسری | — | `admin-logins.js:44` |
| `POST /admin/logins/:id/resend` | مدیر | سراسری | دارد | عملیات | — | `admin-logins.js:55` |
| `POST /admin/logins/:id/reveal` | ارشد | سراسری | دارد | راز/پیکربندی | — | `admin-logins.js:73` |
| `POST /admin/logins/unlock` | مدیر | سراسری | دارد | عملیات | — | `admin-logins.js:89` |
| `GET /admin/shop-desk/overview` | مدیر | سراسری | — | خواندنِ دادهٔ شخصی | G4 | `admin-shop-desk.js:43` |
| `GET /admin/shop-desk/groups` | مدیر | سراسری | — | خواندنِ سراسری | — | `admin-shop-desk.js:112` |
| `GET /admin/shop-offline-codes` | مدیر | سراسری | — | خواندنِ سراسری | — | `admin-shop-offline.js:15` |
| `POST /admin/shop-offline-codes` | ارشد | سراسری | دارد | مالی/اشتراک | — | `admin-shop-offline.js:21` |
| `POST /admin/shop-offline-codes/:id/revoke` | مدیر | سراسری | دارد | مالی/اشتراک | G3 | `admin-shop-offline.js:41` |
| `GET /admin/shops/:id/staff-codes` | مدیر | دکان | — | راز/پیکربندی | — | `admin-staff-codes.js:58` |
| `POST /admin/shops/:id/staff-codes/reveal` | ارشد | دکان | دارد | راز/پیکربندی | — | `admin-staff-codes.js:98` |
| `GET /admin/otp` | مدیر | سراسری | — | خواندنِ دادهٔ شخصی | G4 | `admin-otp.js:34` |
| `POST /admin/otp/:id/reveal` | ارشد | سراسری | دارد | راز/پیکربندی | — | `admin-otp.js:47` |
| `POST /admin/otp/:id/resend` | مدیر | سراسری | دارد | عملیات | — | `admin-otp.js:77` |
| `POST /admin/otp/password-reset` | مدیر | سراسری | دارد | عملیات | — | `admin-otp.js:115` |
| `GET /admin/sync/status` | مدیر | سراسری | — | خواندنِ سراسری | — | `admin-sync.js:26` |
| `GET /admin/sync/conflicts` | مدیر | حساب (account=) | دارد ★ | خواندنِ دادهٔ شخصی | — | `admin-sync.js:39` |
| `POST /admin/sync/conflicts/:id/restore` | ارشد ★ + دلیل | حساب (account=) | دارد ★ | خروجی/بازگردانی | — | `admin-sync.js:64` |
| `GET /admin/sync/errors` | مدیر | سراسری | — | خواندنِ سراسری | — | `admin-sync.js:77` |
| `GET /admin/sync/version-health` | مدیر | سراسری | — | خواندنِ سراسری | — | `admin-sync.js:94` |
| `PUT /admin/plans/:code/discount` | ارشد | سراسری | دارد | مالی/اشتراک | — | `admin-platform.js:46` |
| `DELETE /admin/plans/:code/discount` | ارشد | سراسری | دارد | مالی/اشتراک | — | `admin-platform.js:83` |
| `GET /admin/vip-codes` | مدیر | سراسری | — | خواندنِ سراسری | — | `admin-platform.js:103` |
| `POST /admin/vip-codes` | ارشد | سراسری | دارد | مالی/اشتراک | — | `admin-platform.js:121` |
| `POST /admin/vip-codes/:id/revoke` | مدیر | سراسری | دارد | مالی/اشتراک | G3 | `admin-platform.js:166` |
| `GET /admin/visitors` | مدیر | سراسری | — | خواندنِ دادهٔ شخصی | G4 | `admin-platform.js:178` |
| `GET /admin/support/threads` | مدیر | سراسری | — | خواندنِ دادهٔ شخصی | G4 | `admin-platform.js:197` |
| `GET /admin/support/threads/:id` | مدیر | گفت‌وگوی یک حساب | دارد ★ (`admin.support_thread_viewed`، فقط «باز کردن»، نه `after`) | خواندنِ دادهٔ شخصی | — (TEN-04) | `admin-platform.js:214` |
| `POST /admin/support/threads/:id/messages` | مدیر | گفت‌وگوی یک حساب | در خودِ پیام (فرستنده = مدیر) | عملیات | — | `admin-platform.js:230` |
| `POST /admin/support/threads/:id/media` | مدیر | گفت‌وگوی یک حساب | در خودِ پیام (فرستنده = مدیر) | عملیات | — | `admin-platform.js:255` |
| `GET /admin/support/media/:mid` | مدیر | گفت‌وگوی یک حساب | — | خواندنِ دادهٔ شخصی | G4 | `admin-platform.js:269` |
| `POST /admin/support/threads/:id/status` | مدیر | گفت‌وگوی یک حساب | دارد ★ | عملیات | — | `admin-platform.js:277` |
| `POST /admin/support/broadcast` | مدیر | سراسری | دارد | پیامِ جمعی | G7 | `admin-platform.js:304` |
| `GET /admin/subscriptions/expiring` | مدیر | سراسری | — | خواندنِ دادهٔ شخصی | G4 | `admin-platform.js:389` |
| `POST /admin/subscriptions/notify-expiring` | مدیر | سراسری | دارد | پیامِ جمعی | G7 | `admin-platform.js:404` |
| `GET /admin/apps` | مدیر | سراسری | — | خواندنِ سراسری | — | `admin-platform.js:416` |
| `POST /admin/apps` | مدیر | سراسری | دارد | عملیات | — | `admin-platform.js:422` |
| `PUT /admin/apps/:id` | مدیر | سراسری | دارد | عملیات | — | `admin-platform.js:430` |
| `DELETE /admin/apps/:id` | مدیر | سراسری | دارد | عملیات | — | `admin-platform.js:438` |
| `POST /admin/apps/:id/key` | ارشد | سراسری | دارد | راز/پیکربندی | — | `admin-platform.js:447` |
| `POST /admin/apps/health` | مدیر | سراسری | — (خواندن) | عملیات | — | `admin-platform.js:456` |
| `GET /admin/stamps` | مدیر | سراسری | — | خواندنِ سراسری | — | `admin-platform.js:485` |
| `GET /admin/email` | مدیر | سراسری | — | خواندنِ سراسری | — | `admin-platform.js:533` |
| `PUT /admin/email` | ارشد | سراسری | دارد | راز/پیکربندی | — | `admin-platform.js:539` |
| `POST /admin/email/test` | مدیر | سراسری | دارد | عملیات | — | `admin-platform.js:577` |
| `GET /admin/accounts/:app/:tenantId/backups` | مدیر | حساب (app+tenantId) | — | خواندنِ سراسری | — | `admin-platform.js:625` |
| `GET /admin/accounts/:app/:tenantId/backups/:id` | ارشد ★ + دلیل | حساب (app+tenantId) | دارد ★ | خروجی/بازگردانی | — | `admin-platform.js:642` |
| `DELETE /admin/accounts/:app/:tenantId/backups/:id` | ارشد | حساب (app+tenantId) | دارد | حذف | — | `admin-platform.js:665` |
| `GET /admin/push` | مدیر | سراسری | — | خواندنِ سراسری | — | `admin-platform.js:682` |
| `PUT /admin/push` | ارشد | سراسری | دارد | راز/پیکربندی | — | `admin-platform.js:688` |
| `GET /admin/telegram` | مدیر | سراسری | — | خواندنِ سراسری | — | `admin-platform.js:712` |
| `PUT /admin/telegram` | ارشد | سراسری | دارد | راز/پیکربندی | — | `admin-platform.js:718` |
| `POST /admin/push/register` | مدیر | سراسری | ندارد | نشست | G5 | `admin-platform.js:743` |
| `GET /admin/overview` | مدیر | سراسری | — | خواندنِ سراسری | — | `admin-platform.js:761` |
| `GET /admin/pump/features` | مدیر | سراسری | — | خواندنِ سراسری | — | `admin-pump.js:33` |
| `GET /admin/pump/live-config` | مدیر | سراسری | — | خواندنِ سراسری | — | `admin-pump.js:42` |
| `PUT /admin/pump/live-config` | ارشد | سراسری | دارد | راز/پیکربندی | — | `admin-pump.js:48` |
| `DELETE /admin/pump/live-config` | ارشد | سراسری | دارد | راز/پیکربندی | — | `admin-pump.js:60` |
| `GET /admin/pump/stations` | مدیر | سراسری | — | خواندنِ سراسری | — | `admin-pump.js:78` |
| `GET /admin/pump/stations/:id` | مدیر | پمپ | — (`admin.station_viewed` در #615، کدِ پمپ، نیازمندِ اجازهٔ صاحب‌کار) | خواندنِ دادهٔ شخصی | G4 (`accessCode` هنوز برای مدیرِ عادی: SEC-TEN-02) | `admin-pump.js:130` |
| `GET /admin/pump/stations/:id/events` | مدیر | پمپ | — (`admin.station_events_viewed` در #615، کدِ پمپ، نیازمندِ اجازهٔ صاحب‌کار) | خواندنِ دادهٔ شخصی | G4 | `admin-pump.js:169` |
| `GET /admin/pump/stations/:id/history` | مدیر | پمپ | — | خواندنِ دادهٔ شخصی | G4 | `admin-pump.js:183` |
| `POST /admin/pump/stations/:id/devices/:deviceId/:action` | مدیر | پمپ | دارد | عملیات | G2 | `admin-pump.js:195` |
| `POST /admin/pump/stations/:id/status` | مدیر | پمپ | دارد | عملیات | G2 | `admin-pump.js:215` |
| `GET /admin/pump/subscriptions` | مدیر | سراسری | — | خواندنِ سراسری | — | `admin-pump.js:238` |
| `POST /admin/pump/subscriptions` | مدیر | سراسری | دارد | مالی/اشتراک | G2, G3 | `admin-pump.js:254` |
| `POST /admin/pump/subscriptions/:id/status` | مدیر | اشتراکِ یک حساب | دارد | مالی/اشتراک | G2, G3 | `admin-pump.js:317` |
| `GET /admin/pump/subscriptions/expiring` | مدیر | سراسری | — | خواندنِ دادهٔ شخصی | G4 | `admin-pump.js:330` |
| `GET /admin/pump/vip-codes` | مدیر | سراسری | — | خواندنِ سراسری | — | `admin-pump.js:345` |
| `POST /admin/pump/vip-codes` | ارشد | سراسری | دارد | مالی/اشتراک | — | `admin-pump.js:363` |
| `POST /admin/pump/vip-codes/:id/revoke` | مدیر | سراسری | دارد | مالی/اشتراک | G3 | `admin-pump.js:405` |
| `GET /admin/pump/offline-codes` | مدیر | سراسری | — | خواندنِ سراسری | — | `admin-pump.js:424` |
| `POST /admin/pump/offline-codes` | ارشد | سراسری | دارد | مالی/اشتراک | — | `admin-pump.js:430` |
| `POST /admin/pump/offline-codes/:id/revoke` | مدیر | سراسری | دارد | مالی/اشتراک | G3 | `admin-pump.js:451` |
| `GET /admin/pump/users` | مدیر | سراسری | — | خواندنِ دادهٔ شخصی | G4 | `admin-pump.js:472` |
| `GET /admin/pump/stats` | مدیر | سراسری | — | خواندنِ سراسری | — | `admin-pump.js:521` |
| `GET /admin/notice-templates` | مدیر | سراسری | — | خواندنِ سراسری | — | `admin-notices.js:22` |
| `PUT /admin/notice-templates/:key` | مدیر | سراسری | دارد | عملیات | — | `admin-notices.js:35` |
| `GET /admin/notices` | مدیر | سراسری | — | خواندنِ سراسری | — | `admin-notices.js:46` |
| `POST /admin/notices` | مدیر | سراسری | دارد | عملیات | — | `admin-notices.js:64` |
| `GET /admin/notices/:id` | مدیر | سراسری | — | خواندنِ سراسری | — | `admin-notices.js:82` |
| `PUT /admin/notices/:id` | مدیر | سراسری | ندارد | عملیات | — | `admin-notices.js:90` |
| `DELETE /admin/notices/:id` | مدیر | سراسری | دارد | عملیات | — | `admin-notices.js:97` |
| `POST /admin/notices/:id/preview` | مدیر | سراسری | — (خواندن) | خواندنِ دادهٔ شخصی | G4 | `admin-notices.js:106` |
| `POST /admin/notices/:id/test` | مدیر | سراسری | ندارد | عملیات | — | `admin-notices.js:115` |
| `POST /admin/notices/:id/send` | مدیر | سراسری | در lib (`notice.sent`) | پیامِ جمعی | G7 | `admin-notices.js:131` |
| `POST /admin/notices/:id/schedule` | مدیر | سراسری | ندارد | پیامِ جمعی | G7, G6 | `admin-notices.js:139` |
| `GET /admin/notices/:id/report` | مدیر | سراسری | — | خواندنِ دادهٔ شخصی | G4 | `admin-notices.js:152` |
| `POST /admin/notices/audience` | مدیر | سراسری | — (خواندن) | خواندنِ دادهٔ شخصی | G4 | `admin-notices.js:161` |
| `POST /admin/notices/run-system` | مدیر | سراسری | دارد | پیامِ جمعی | G7 | `admin-notices.js:176` |
| `GET /admin/payments` | مدیر | سراسری | — | خواندنِ دادهٔ شخصی | G4 | `admin-sales.js:75` |
| `POST /admin/payments` | مدیر | سراسری | دارد | مالی/اشتراک | G2, G3 | `admin-sales.js:114` |
| `PUT /admin/payments/:id` | ارشد | پرداختِ یک حساب | دارد | مالی/اشتراک | G2 | `admin-sales.js:137` |
| `DELETE /admin/payments/:id` | ارشد | پرداختِ یک حساب | دارد | مالی/اشتراک | G2 | `admin-sales.js:152` |
| `GET /admin/payments/:id/receipt` | مدیر | پرداختِ یک حساب | — | خواندنِ دادهٔ شخصی | G4 | `admin-sales.js:216` |
| `GET /admin/sales/summary` | مدیر | سراسری | — | خواندنِ سراسری | — | `admin-sales.js:229` |
| `GET /admin/sales/subscriptions` | مدیر | سراسری | — | خواندنِ سراسری | — | `admin-sales.js:328` |
| `GET /admin/sales/expiring` | مدیر | سراسری | — | خواندنِ دادهٔ شخصی | G4 | `admin-sales.js:354` |
| `POST /admin/sales/expiring/remind` | مدیر | سراسری | در lib (`notice.sent`) | پیامِ جمعی | G7 | `admin-sales.js:365` |
| `GET /admin/sales/debts` | مدیر | سراسری | — | خواندنِ دادهٔ شخصی | G4 | `admin-sales.js:384` |
| `POST /admin{/subscriptions|/pump/subscriptions}/:id/permanent` | ارشد | اشتراکِ یک حساب | دارد | مالی/اشتراک | — | `admin-sales.js:442` |
| `POST /admin{/subscriptions|/pump/subscriptions}/:id/discount` | ارشد | اشتراکِ یک حساب | دارد | مالی/اشتراک | — | `admin-sales.js:450` |
| `GET /admin{/subscriptions|/pump/subscriptions}/:id/addons` | مدیر | اشتراکِ یک حساب | — | خواندنِ سراسری | — | `admin-sales.js:463` |
| `POST /admin{/subscriptions|/pump/subscriptions}/:id/addons` | ارشد | اشتراکِ یک حساب | دارد | مالی/اشتراک | — | `admin-sales.js:468` |
| `DELETE /admin{/subscriptions|/pump/subscriptions}/:id/addons/:addonId` | ارشد ★ | اشتراکِ یک حساب | دارد ★ | مالی/اشتراک | — | `admin-sales.js:482` |
| `POST /admin/pump/subscriptions/:id/services` | مدیر | اشتراکِ یک حساب | دارد | مالی/اشتراک | G2, G3 | `admin-sales.js:503` |
| `GET /admin/discount-codes` | مدیر | سراسری | — | خواندنِ سراسری | — | `admin-sales.js:521` |
| `POST /admin/discount-codes` | مدیر | سراسری | دارد | مالی/اشتراک | G3 | `admin-sales.js:531` |
| `POST /admin/discount-codes/:id/revoke` | مدیر | سراسری | دارد | مالی/اشتراک | G3 | `admin-sales.js:539` |
| `GET /admin/reps` | مدیر | سراسری | — | خواندنِ سراسری | — | `admin-sales.js:555` |
| `POST /admin/reps` | مدیر | سراسری | دارد | مالی/اشتراک | G3 | `admin-sales.js:560` |
| `PATCH /admin/reps/:id` | مدیر | سراسری | دارد | مالی/اشتراک | G3 | `admin-sales.js:569` |
| `GET /admin/reps/:id/report` | مدیر | سراسری | — | خواندنِ دادهٔ شخصی | G4 | `admin-sales.js:578` |
| `POST /admin/discount-codes/quote` | مدیر | سراسری | — (خواندن) | خواندنِ سراسری | — | `admin-sales.js:587` |
| `GET /admin/plans/:code/price-history` | مدیر | سراسری | — | خواندنِ سراسری | — | `admin-sales.js:595` |
| `GET /admin/price-history` | مدیر | سراسری | — | خواندنِ سراسری | — | `admin-sales.js:601` |
| `GET /admin/campaigns` | مدیر | سراسری | — | خواندنِ سراسری | — | `admin-sales.js:611` |
| `POST /admin/campaigns` | مدیر | سراسری | دارد | مالی/اشتراک | G3 | `admin-sales.js:615` |
| `GET /admin/campaigns/:id/stats` | مدیر | سراسری | — | خواندنِ سراسری | — | `admin-sales.js:623` |
| `PUT /admin/downloads` | ارشد | سراسری | دارد | عملیات | — | `admin-sales.js:631` |
| `PUT /admin/users/:id/city` | مدیر | کاربر | دارد ★ | عملیات | — | `admin-sales.js:646` |
| `GET /admin/shops/:id/health` ◆ | مدیر (+ مالی، فقط‌خواندن) | دکان | — (فقط فراداده) | خواندنِ فراداده | — | `admin-shop-state.js` |
| `GET /admin/shops/:id/holds` ◆ | مدیر (+ مالی، فقط‌خواندن) | دکان | — (تصمیم‌های خودِ پلتفرم) | خواندنِ فراداده | — | `admin-shop-state.js` |
| `POST /admin/shops/:id/holds` ◆ | به نوعِ حالت (بند ۱۲.۱) | دکان | دارد، در همان تراکنش | عملیات / مالی | — | `admin-shop-state.js` |
| `POST /admin/shops/:id/holds/:holdId/lift` ◆ | به نوعِ حالت | دکان (همان دکان) | دارد، در همان تراکنش | عملیات / مالی | — | `admin-shop-state.js` |
| `GET /admin/flags` ◆ | مدیر (+ مالی، فقط‌خواندن) | سراسری | — | خواندنِ سراسری | — | `admin-flags.js` |
| `GET /admin/flags/history` ◆ | مدیر (+ مالی، فقط‌خواندن) | سراسری | — | خواندنِ سراسری | — | `admin-flags.js` |
| `PUT /admin/flags` ◆ | ارشد + دلیل | سراسری | دارد + تاریخچه | عملیات | — | `admin-flags.js` |
| `DELETE /admin/flags` ◆ | ارشد + دلیل | سراسری | دارد + تاریخچه | عملیات | — | `admin-flags.js` |
| `GET /admin/service-keys` ◆ | ارشدِ انسانی | سراسری | — | خواندنِ سراسری | — | `admin-service-keys.js` |
| `POST /admin/service-keys` ◆ | ارشدِ انسانی + دلیل | سراسری | دارد | راز (یک‌بار دیده) | — | `admin-service-keys.js` |
| `POST /admin/service-keys/:keyId/revoke` ◆ | ارشدِ انسانی + دلیل (کلیدِ مالک: فقط مالک) | سراسری | دارد | راز | — | `admin-service-keys.js` |
| `GET /admin/market/overview` ▲ | مدیر (+ مالی، فقط‌خواندن) | سراسری | — | خواندنِ سراسری (بی دادهٔ شخصی) | — | `admin-market.js` |
| `GET /admin/market/sellers` ▲ | مدیر | سراسری (پالایشِ دکان) | — | خواندنِ فراداده | — | `admin-market.js` |
| `GET /admin/market/orders` ▲ | مدیر (+ مالی) | سراسری (پالایشِ دکان) | — | خواندنِ سراسری (بی نام/شماره/نشانیِ مشتری) | — | `admin-market.js` |
| `GET /admin/market/commission` ▲ | مدیر (+ مالی) | سراسری | — | خواندنِ مالی | — | `admin-market.js` |
| `GET /admin/market/history` ▲ | مدیر | سراسری (پالایشِ دکان) | — | خواندنِ سابقه | — | `admin-market.js` |
| `GET /admin/delivery/overview` ▲ | مدیر (+ مالی) | سراسری | — | خواندنِ سراسری | — | `admin-delivery.js` |
| `GET /admin/delivery/queue` ▲ | مدیر | سراسری (پالایشِ دکان) | — | خواندنِ سراسری (بی دادهٔ شخصی) | — | `admin-delivery.js` |
| `GET /admin/delivery/events` ▲ | مدیر | سراسری (پالایشِ دکان) | — | خواندن (بی یادداشت و کد) | — | `admin-delivery.js` |
| `GET /admin/delivery/connectivity` ▲ | مدیر | سراسری | — | خواندنِ فراداده | — | `admin-delivery.js` |
| `GET /admin/delivery/report` ▲ | مدیر (+ مالی) | سراسری | دارد (`admin.delivery_report_viewed`) | خواندنِ مالی | — | `admin-delivery.js` |
| `GET /admin/delivery/thresholds` ▲ | مدیر | سراسری | — | خواندنِ سراسری | — | `admin-delivery.js` |
| `PUT /admin/delivery/thresholds` ▲ | ارشد + دلیل | سراسری | دارد + تاریخچه (`live_config`) | عملیات | — | `admin-delivery.js` |

◆ = فاز ۳ ِ پنلِ مرکزی (بند ۱۲). ▲ = ماژول‌های ۴.۶ و ۴.۷ (بند ۱۲.۸). `GET /admin/shops` حالا ستونِ `holds` و پالایشِ `?hold=` هم دارد؛
`PUT/DELETE /admin/pump/live-config` و `PUT /admin/release/beta` تاریخچه می‌نویسند (دلیلِ اختیاری).

---

## ۷. شکاف‌های باقی‌مانده

| شناسه | شکاف | وضعیت | قدمِ بعدیِ مشخص |
|---|---|---|---|
| G1 | مجوزِ موقتِ پشتیبانی (مدتِ محدود، رضایتِ فروشگاه، قابلِ لغو) و «ورود به‌جای کاربر» | MISSING | طرحِ ۸.۱؛ مهاجرتِ `079` برایش رزرو است و هنوز ساخته نشده |
| G2 | دلیلِ اجباری برای غیرفعال‌کردنِ کاربر/پمپ، دادن/تغییرِ اشتراک، تأییدِ خرید، ثبت/ویرایش/حذفِ پرداخت، حذفِ کاربر (۱۹.۱۰-۳) | PARTIAL (همه سابقه دارند، دلیل نه) | اول پنلِ وب و `admin-android` فیلدِ «دلیل» بگیرند و بفرستند (اختیاری، ثبت در سابقه)؛ نسخهٔ بعد سرور با `requireReason` اجباری‌اش کند — بی این ترتیب برنامهٔ مدیرِ نصب‌شده می‌شکند |
| G3 | نقشِ `admin` یکی است: پشتیبانی هم می‌تواند اشتراک بدهد، پرداخت ثبت کند، کدِ تخفیف و نماینده بسازد | PARTIAL | طرحِ ۸.۲ (ماتریسِ نقشِ مدیر)؛ `admin`ِ امروز ⇒ `ops_admin` با همان اختیارات تا رفتار عوض نشود |
| G4 | خواندن‌های شخصیِ بی‌سابقه: فهرستِ کاربران، درخواست‌های خرید (شماره)، گفت‌وگوها و رسانهٔ پشتیبانی، فهرستِ OTP/ورود (نشانیِ گیرنده)، پمپ‌ها و رویدادهایشان، پرداخت‌ها و رسید، بدهی‌ها، گزارشِ اعلان و مخاطبان، خودِ سابقه | PARTIAL | برای پرونده‌ها (یک موضوع) سابقه بنویسید، نه برای فهرست‌های پرتکرار؛ گفت‌وگوی پشتیبانی فقط هنگامِ «باز کردن» (`after` خالی) — همان الگوی `admin.user_viewed`. **پرونده‌ها انجام شد (TEN-04، `docs/security/tenant-fa.md`):** گفت‌وگوی پشتیبانی، اجازه‌ها/دستگاه‌های دکان، پیش‌نمایشِ حذف (پروندهٔ پمپ و خبرهایش: کدِ پمپ ⇐ #615، نیازمندِ اجازهٔ صاحب‌کار)؛ آزمونِ `tenant-matrix.test.js` هر درِ GETِ موضوع‌دارِ تازه را تا ردهٔ «سابقه‌دار/کنار با دلیل» سرخ می‌کند. فهرست‌ها همچنان بی‌سابقه (عمداً) |
| G5 | TOTP اختیاری است؛ step-up/تأییدِ دوم برای پرخطر نیست؛ نشست/ورود | PARTIAL — **مالک: عاملِ ممیزیِ ورود/نشست** | این سند رفتارِ ورود را عوض نکرد. پیشنهاد: TOTP برای `superadmin` اجباری؛ تأییدِ دوباره (TOTP) پیش از خروجی، بازگردانی، حذفِ کاربر و تغییرِ مدیران |
| G6 | زمان‌بندیِ اعلانِ جمعی ردیفِ سابقه ندارد (خودِ ارسال دارد: `notice.sent`) | PARTIAL (کم‌خطر) | یک `audit.log` در `admin-notices.js` (مسیرِ `schedule`) |
| G7 | اعلان و پیامِ همگانی برای هر مدیر باز است (سقفِ نرخ و سابقه دارد) | PARTIAL | در ماتریسِ ۸.۲ به `ops_admin`/`sales_manager` محدود شود |
| G8 | سابقه دستکاری‌پذیر است و هر مدیر کلِ آن را می‌خواند؛ فیلترِ «کدام مدیر» ندارد؛ سیاستِ نگهداری ندارد | PARTIAL | زنجیرهٔ هش (مهاجرت‌های ۰۷۰–۰۷۱ مالِ عاملِ دیگری است) + فیلترِ `actor`؛ نقشِ `auditor` |
| G9 | فایلِ `admin-pump.js` (`/admin/pump/*`) در این تغییر دست نخورد. سه مسیرِ مشترکی که هر دو بخش را می‌پوشانند عوض شدند: دانلودِ پشتیبان با `app=pump`، بازگرداندنِ تعارضِ پمپ، و برداشتنِ افزونهٔ `/admin/pump/subscriptions/:id/addons/:addonId` | NOT-VERIFIED برای دلیل/نقشِ بقیهٔ مسیرهای پمپ | قراردادِ پمپ مشترک است (`sync-v1.js` و `/api/pump/*` دست نخوردند)؛ هر تغییرِ بعدی با regressionِ هر دو سمت |

---

## ۸. طرح‌ها (ساخته نشده — مرزِ امنِ بعدی)

### ۸.۱ مجوزِ موقتِ پشتیبانی و «ورود به‌جای کاربر» (G1)

- **جدول** `admin_access_grants` (مهاجرتِ `079`): `id, admin_id, app, tenant_id, scope ('read_private'|'export'|'restore'|'view_as'), reason, ticket_id, consent ('tenant'|'break_glass'), granted_by, starts_at, expires_at, revoked_at, revoked_by, uses`.
- **مدت**: پیش‌فرض ۶۰ دقیقه، سقف ۲۴ ساعت؛ تمدید = مجوزِ تازه با دلیلِ تازه. زمان فقط از ساعتِ سرور.
- **رضایت**: پیش‌فرض، صاحبِ دکان در برنامهٔ خودش «اجازهٔ دسترسیِ پشتیبانی تا ساعتِ X» را می‌دهد و هر وقت بخواهد لغو می‌کند. «شکستنِ شیشه» (`break_glass`) فقط برای صاحبِ پلتفرم، با دلیلِ اجباری، و صاحبِ دکان در برنامه می‌بیند که چه کسی، کی، چرا.
- **نگهبان**: `requireGrant(scope)` روی هر مسیرِ محتوای خصوصی؛ مجوزِ حسابِ الف روی حسابِ ب ⇒ ۴۰۳؛ منقضی/لغوشده ⇒ ۴۰۳؛ هر استفاده یک ردیفِ سابقه با `grant_id`.
- **ورود به‌جای کاربر**: توکنِ نوعِ جدا (`support`) گره‌خورده به همان tenant و با انقضای ≤ مجوز؛ پیش‌فرض فقط‌خواندنی؛ نوشتن scopeِ جدا؛ نوارِ دائمیِ «پشتیبانی در حالِ مشاهده» در برنامهٔ کاربر؛ رمز، OTP، توکن و کارت هرگز در پاسخ نیست.
- **آزمون‌های لازم پیش از فعال‌شدن**: مجوزِ منقضی، لغوشده، tenantِ دیگر، نوعِ توکنِ اشتباه، شمارشِ ردیفِ سابقه، و این‌که بی‌مجوز هیچ محتوایی بیرون نمی‌آید.

### ۸.۲ ماتریسِ نقشِ مدیر (G3، G7)

- فایلِ `server/src/lib/admin-permissions.js` مثلِ `permissions.js` (یک جدول، تصمیم فقط در سرور) و `requireAdminPermission('subscriptions.grant')`.
- مهاجرت: قیدِ `CHECK (role IN ('admin','superadmin'))` در `001_core.sql:41` باید باز شود ⇒ همان `079` (یا شمارهٔ بعدیِ آزاد).
- حفظِ رفتار: `admin` ⇒ معادلِ `ops_admin` با همهٔ اختیاراتِ امروز؛ نقش‌های باریک‌تر فقط برای حساب‌های تازه.

| مجوز | owner | ops_admin | support_agent | sales_manager | finance | auditor |
|---|---|---|---|---|---|---|
| خواندنِ فراداده و آمار | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ |
| پاسخ و وضعیتِ پشتیبانی | ✔ | ✔ | ✔ | — | — | — |
| اشتراک دادن/تمدید/تأییدِ خرید | ✔ | ✔ | — | ✔ | ✔ | — |
| ثبتِ پرداخت | ✔ | ✔ | — | — | ✔ | — |
| ویرایش/حذفِ پرداخت، پلن، VIP، افزونه | ✔ | — | — | — | — | — |
| کدِ تخفیف و نماینده | ✔ | ✔ | — | ✔ | — | — |
| اعلانِ جمعی | ✔ | ✔ | — | ✔ | — | — |
| خواندنِ سابقه | ✔ | ✔ | — | — | ✔ | ✔ |
| خروجی/بازگردانی (با مجوزِ موقت) | ✔ | — | با مجوزِ ۸.۱ | — | — | — |
| مدیران، کلیدها، تنظیمِ کانال‌ها | ✔ | — | — | — | — | — |

---

## ۹. آزمون‌ها و نگهداریِ این سند

- `server/test/admin-control-plane.test.js` — اجرا: `cd server && TEST_DATABASE_URL=pglite:memory node --test --test-concurrency=1 test/admin-control-plane.test.js`
  - فهرستِ مسیرها را از سورس می‌سازد؛ **هر فایلِ تازهٔ `routes/admin*.js` که در نقشهٔ سوارشدنِ آزمون نباشد آزمون را می‌شکند**، پس مسیرِ تازهٔ بی‌نگهبان بی‌صدا اضافه نمی‌شود.
  - ضمانت‌های بند ۱ (خروجی، بازگردانی، افزونه، سابقه‌ها، جداییِ توکن) هر کدام آزمونِ جدا دارند.
- هر تغییری که مسیرِ مدیرِ تازه بسازد یا نگهبان/سابقه را عوض کند، **همین سند (جدولِ بند ۶) را در همان PR به‌روز می‌کند**.
- جدولِ بند ۶ از سورس تولید شده است؛ پس از هر تغییرِ بزرگ دوباره از سورس بسازید، نه با دست.

---

## ۱۰. تصمیم‌های باز

| شناسه | پرسش | پیشنهادِ سند | وضعیت |
|---|---|---|---|
| ACP-D1 | مدتِ پیش‌فرض/سقفِ مجوزِ موقتِ پشتیبانی | ۶۰ دقیقه / ۲۴ ساعت | باز — مانعِ G1 |
| ACP-D2 | آیا صاحبِ پلتفرم بی‌رضایتِ دکان («شکستنِ شیشه») به محتوا برسد؟ | فقط با دلیل + اطلاع به صاحبِ دکان | باز — مانعِ G1 |
| ACP-D3 | TOTP برای ارشد اجباری شود؟ | بله (SEC-04 در v3) | به عاملِ ورود/نشست سپرده شد |

---

## ۱۱. تاریخچه

- ۱۴۰۵/۰۸/۰۹ — چسبِ پل (بند ۱۲.۶): `capabilities` در `/admin/me`، ۴۰۴ ِ مسیرِ ناشناختهٔ مدیر، `total` ِ جست‌وجو.
- ۱۴۰۵/۰۸/۰۹ — فاز ۳ ِ پنلِ مرکزی (بند ۱۲): سلامتِ دکان، چهار حالت + بایگانی، نقش‌های `finance`/`viewer`، کلیدِ سرویس و
  «انجام‌دهنده» برای پل، تاریخچهٔ پرچم‌ها، حفاظتِ مالکِ اصلی. مهاجرت‌های ۱۶۵، ۱۶۸ و ۱۶۹.
- ۱۴۰۵/۰۷/۲۵ — نسخهٔ نخست: ممیزیِ کاملِ ۱۶۴ مسیر؛ خروجیِ پشتیبانِ حساب و بازگرداندنِ تعارض ⇒ ارشد + دلیل + سابقه؛ برداشتنِ افزونه ⇒ ارشد + همان اشتراک؛ سابقه برای ردِ خرید، شهرِ کاربر، وضعیتِ پشتیبانی و دیدنِ پرونده‌ها.

---

## ۱۲. فاز ۳ ِ پنلِ مرکزی — سمتِ سرورِ حساب (۱۴۰۵/۰۸/۰۹)

پایه: `docs/OWNER-CONSOLE-AUDIT-fa.md` ِ ریپوی `server` (شاخهٔ `claude/console-audit`)، §۴ (۴.۲، ۴.۳، ۴.۴، ۴.۱۲) و §۵
(S4، S10، S12، S14). آزمون: `server/test/console-phase3.test.js` + `MOUNTS` ِ `admin-control-plane.test.js`.

### ۱۲.۱ چهار حالتِ جدا و بایگانی (`lib/shop-holds.js`، مهاجرتِ ۱۶۵)

| حالت | اثر (همه در سرور) | گذاشتن/برداشتن | کلیدِ یکتایی |
|---|---|---|---|
| `access_suspended` — تعلیقِ دسترسی | هر درِ عضو ⇒ ۴۰۳ `shop_suspended` (API، Sync v1/v3، سوکتِ زنده بسته، دکانِ تازه هم نه)؛ ویترین/رسید بسته | ارشد، مدیر | اختیاری |
| `service_disabled` + `service` — غیرفعال کردنِ سرویس | فقط همان پرچم برای همان دکان خاموش (`orders.flagOf`): `shop.storefront`، `shop.orders`، `shop.delivery`، `shop.chat`، `market.directory` | ارشد، مدیر | اختیاری |
| `subscription_ended` — پایانِ اشتراک | `entitlementOf` ⇒ فقط رایگان و آزمایشیِ مصرف‌شده؛ **ردیفِ اشتراک و پول دست نمی‌خورد**؛ برداشتن روزی اضافه نمی‌کند | ارشد، **مالی** | **اجباری** (پول‌مجاور) |
| `deletion_requested` — درخواستِ حذفِ دائمی | دسترسی بسته (`shop_deletion_pending`)؛ لغوشدنی تا حذف؛ خودِ حذف فقط `DELETE /admin/users/:id` ِ ارشد با تایپِ نشانیِ حساب — برگشت ندارد | فقط ارشد | اختیاری |
| `archived` — بایگانی | دسترسی بسته (`shop_archived`)، `GET /admin/shops?hold=archived` | ارشد، مدیر | اختیاری |

- هیچ حالتی چیزی پاک نمی‌کند؛ برداشتن = پر شدنِ `lifted_*` (ردیف خودش تاریخچه است). هر (دکان، حالت، سرویس) یک ردیفِ باز.
- دلیل اجباری (۳ تا ۲۰۰)؛ ردیفِ سابقهٔ `admin.shop_hold_placed|lifted` در **همان تراکنش** (شکستش ⇒ هیچ تغییری).
- `shops.status` فقط از این‌جا: `'disabled'` ⇔ یکی از سه حالتِ بستنِ دسترسی باز است.
- حالتِ دکانِ الف از نشانیِ دکانِ ب ۴۰۴ است (TEN).

### ۱۲.۲ هویتِ سرویس برای پلِ پنل (`lib/admin-service.js`، مهاجرتِ ۱۶۸) — یافتهٔ S4

- پنل با `Authorization: Bearer vsk_…` (کلیدِ سرویس) و سرآیندهای `X-Actor-Id`، `X-Actor-Role`
  (`superadmin|admin|finance|viewer`) و `X-Actor-Name` (percent-encoded) می‌آید. بی «انجام‌دهنده» ⇒ ۴۰۰ `actor_required`.
- نقشِ مؤثر = کم‌رتبه‌ترِ نقشِ انجام‌دهنده و `role_cap` ِ کلید؛ مرز را **سرور** می‌سنجد (`lib/admin-roles.check`)، نه پل.
- هر ردیفِ سابقه در همان درخواست: `user_id` = شناسهٔ کلید (کدام ماشین)، `detail.actor` = انجام‌دهنده (کدام آدم).
- کلید فقط هنگامِ ساختن یک بار برمی‌گردد (SHA-256 در پایگاه)؛ هرگز در نشانی، لاگ یا سابقه. کلید به درهای مدیران، کلیدها،
  دوعاملی و نشست نمی‌رسد (`service_forbidden`).
- **سازگاری:** ورودِ ارشد با رمز (`HLP_ACCOUNT_ADMIN_USER/PASSWORD` ِ پنل) دست نخورده و کار می‌کند تا صاحبِ سامانه پل را عوض کند.
- **راهِ جابه‌جایی (کارِ صاحبِ سامانه / PRِ سمتِ پنل در ریپوی `server`):**
  1. ارشدِ انسانی (یا پنل با همان ورودِ امروزش، یک بار): `POST /api/admin/service-keys` با `{name: "پنلِ خانه", roleCap: "superadmin", reason}`
     ⇒ `key` را همان لحظه در محیط/گاوصندوقِ پنل بگذارید (مثلاً `HLP_ACCOUNT_SERVICE_KEY`)؛ دوباره دیده نمی‌شود.
  2. پل هر درخواست را با همان کلید و انجام‌دهندهٔ کاربرِ پنل بفرستد (مالک/admin ِ پنل ⇒ `superadmin`، operator ⇒ `admin`،
     viewer ⇒ `viewer`؛ نگاشت در پل، مرز در سرور).
  3. پس از سبز شدن، رمزِ ارشد را از پنل بردارید و TOTP ِ همان حسابِ ارشد را روشن کنید؛ `/api/account-server/status` ِ پنل
     دیگر رمزی ندارد که برگرداند.
  4. ابطال: `POST /api/admin/service-keys/:id/revoke` (همان دم ۴۰۱ `invalid_service_key`). کلیدِ ساختهٔ مالک را فقط مالک.
- هماهنگی با #588 (`claude/sec-auth`): وقتی `ADMIN_REAUTH=1` روشن شود، `assertFresh` برای `req.service` (بی `tokenRow`)
  باید «تازه» بشمارد — هر کدام دوم مرج شد همین یک خط را می‌گذارد. `enrollmentBlock` (TOTP ِ اجباری) مالِ آدم است، نه کلید.

### ۱۲.۳ پرچم‌ها و `live_config` با تاریخچه (مهاجرتِ ۱۶۹)

`live_config_history`: هر `set`/`remove`/`set_once` از هر در، در همان تراکنش — `scope`، `key`، پیشین ⇒ تازه (متنِ JSON؛ NULL = نبود)،
`changed_by`، `actor` (jsonb)، `reason`، زمان. خواندن: `GET /admin/flags` (مقدار + کاتالوگِ پرچم‌های شناخته) و
`GET /admin/flags/history?scope=&key=&limit=&before=` (صفحه‌بندیِ سمتِ سرور). نوشتن: `PUT/DELETE /admin/flags` فقط ارشد، دلیل اجباری.

### ۱۲.۴ مالکِ اصلی (`lib/admin-owner.js`، مهاجرتِ ۱۶۸) — یافتهٔ S12

- `admins.is_owner`: پیشین‌ترین ارشد (حسابِ آغازینِ سرور)، یکی در هر لحظه. هیچ مدیرِ دیگری غیرفعال/تنزلش نمی‌دهد (۴۰۳ `owner_protected`)؛
  خودش هم نقشِ خودش را عوض نمی‌کند (`admin_self`). ارشدِ دیگر کلیدِ سرویسِ مالک را باطل نمی‌کند.
- **بازیابی (فقط از پوستهٔ همان سرور):** `ADMIN_BOOTSTRAP_PASSWORD` ِ تازه در محیط ⇒ رمز برمی‌گردد، حساب فعال، TOTP خاموش؛ یا
  `node scripts/create-admin.js <نامِ مالک> <رمزِ تازه>` ⇒ همان، نقشِ مالک همیشه ارشد می‌ماند و نشست‌های پیشین بسته می‌شوند.
- جابه‌جاییِ مالکیت API ندارد (عمداً)؛ اگر لازم شد: پوسته و `UPDATE admins SET is_owner=…` در یک تراکنش، با پشتیبان.

### ۱۲.۵ رده‌بندیِ درهای تازه برای ماتریسِ جداسازی (#582، `tenant-matrix.test.js` → `ADMIN_SUBJECT`)

هر کدام از #582 و کنسولِ یکپارچه (#597 ⇐ #598 ⇐ #605) که **دوم** مرج شود **این پنج خط** را به `ADMIN_SUBJECT` می‌افزاید — همهٔ درهای
`GET /api/admin/*` ِ شناسه‌دارِ تازهٔ این سه PR (درهای بی‌شناسه، مثلِ `/admin/search` و فهرست‌های `/admin/market|delivery|ops|finance/*`، در
`ADMIN_SUBJECT` نمی‌آیند و فقط سنجشِ «دفترِ دکان و محتوای خصوصی بیرون نمی‌آید» را می‌گذرانند). وارسی‌شده (۱۴۰۵/۰۸/۱۰): `tenant-matrix.test.js` ِ
#582 روی کدِ #605 ⇒ بی این خط‌ها همین پنج در «بی‌رده»اند؛ با آن‌ها TEN-04 سبز است (بی نشتِ دفتر/محتوای خصوصی، بی ۵xx).
- `'GET /api/admin/shops/:id/health': { exempt: 'فقط فراداده (زمانِ همگام‌سازی، نسخهٔ دستگاه، شمارِ خطا، وضعِ اشتراک)؛ بی دفتر و متنِ دکان' }`
- `'GET /api/admin/shops/:id/holds': { exempt: 'تصمیم‌های خودِ پلتفرم روی دکان (حالت و دلیلِ مدیر)، نه دادهٔ دکان' }`
- `'GET /api/admin/admins/:id/sessions': { exempt: 'نشستِ مدیرِ سکو، نه دادهٔ دکان' }` (بندِ ۱۲.۷)
- `'GET /api/admin/ops/incidents/:id': { exempt: 'رخدادِ فنیِ سکو: گروهِ خطا با پیامِ پالوده، شناسهٔ دکان و چهار نویسهٔ آخرِ دستگاه؛ بی دفتر و متنِ دکان' }`
- `'GET /api/admin/finance/payments/:id': { exempt: 'پرداختِ اشتراک و بازنگری‌هایش — صورت‌حسابِ خودِ پلتفرم، نه دفترِ دکان' }` (بندِ ۱۲.۹)

### ۱۲.۶ چسبِ پل ⇄ سرورِ حساب (۱۴۰۵/۰۸/۰۹، شاخهٔ `claude/console-glue`)

- **«توانایی»ها:** `GET /api/admin/me` ⇒ `capabilities: string[]` — `shop-health.v1`، `shop-holds.v1` (چهار حالت + بایگانی؛
  نامِ `shop-suspension.v1` ِ پیش‌نویسِ فاز ۲ کنار رفت، چون درِ `/suspension` هرگز ساخته نشد)، `flags.v1`، `actor.v1`،
  `service-key.v1`. پل پیش از هر درِ تازه همین را می‌خواند؛ سرورِ کهنه این فیلد را ندارد ⇒ «داده در دسترس نیست». هر توانایی
  که گفته شود درش هست (`admin-control-plane.test.js` ⇒ `CAPABILITY_PROBES`). نامِ تازه فقط با افزودن به هر دو سو.
- **مسیرِ ناشناختهٔ مدیر ⇒ ۴۰۴ `route_not_found`** با توکنِ معتبرِ مدیر (پیش‌تر ۴۰۱ `invalid_token` از `routes/data` ⇒ پل
  «نشست تمام شد» می‌خواند). بی‌توکن و توکنِ دکان همان ۴۰۱ (وجودِ مسیر گفته نمی‌شود). خطِ آخرِ درهای `/admin` در `app.js`.
- **`GET /admin/shops`:** `total` همان `q` و `hold` را می‌شمارد (یک WHERE برای ردیف‌ها و شمار).

### ۱۲.۷ پوستهٔ کنسولِ «فروشگاه» ِ پنل (۱۴۰۵/۰۸/۱۰، شاخهٔ `claude/console-shell-api`)

دو «توانایی» ِ تازه برای ماژول‌های ۴.۳ و ۴.۱۲ و جست‌وجوی سراسریِ پنل (ریپوی `server`، شاخهٔ `claude/console-shell`):

- **`admin-sessions.v1`:** `GET /api/admin/admins/:id/sessions` (نشست‌های باز: ۱۶ نویسهٔ آغازِ SHA-256 ِ توکن، `issuedAt`،
  `expiresAt`، `current` — نه توکن، نه هشِ کامل) و `POST …/sessions/revoke` `{sessionId?, reason}` (یکی یا همه؛ دلیل اجباری؛
  نشستِ پیدانشده ⇒ ۴۰۴ `session_not_found`). فقط ارشد؛ کلیدِ سرویس هرگز (`SERVICE_DENY`). نشست‌های **مالکِ اصلی** را فقط خودش
  می‌بندد (`owner_protected`). سابقه: `admin.admin_sessions_revoked` با شمار و دلیل.
- **`search.v1`:** `GET /api/admin/search?q=&limit=` (`routes/admin-search.js`) — دکان، کاربر، پرداخت، سفارش (فقط شناسه/شمارهٔ
  سفارش؛ نام و تلفنِ خریدار جست‌وجو نمی‌شود)، خطا، و مدیر فقط برای ارشدِ **انسانی**. نوع‌ها به نقش (`KINDS_BY_ROLE`): مالی
  فقط دکان و پرداخت (`FINANCE` ِ `lib/admin-roles.js`). `%`/`_` ِ ورودی حرفِ معمولی است؛ کوتاه‌تر از ۲ نویسه ⇒ ۴۰۰.
- **سابقهٔ نقش و تنظیم:** `PATCH /admin/admins/:id` ⇒ `detail.from` (نقش/وضعیتِ پیشین) و `reason`؛ `POST /admin/admins` بی نقش
  ⇒ `viewer` (کمینهٔ دسترسی، پیش‌تر `admin`)؛ `PATCH /admin/config` ⇒ `detail.changes` («پیشین ⇒ تازه») و `reason`.
- **پرچمِ شناخته نوع‌دار:** `PUT /admin/flags` برای کلیدِ کاتالوگ فقط مقدارِ هم‌نوعِ پیش‌فرض (بولی، یا فهرستِ شناسه برای
  `beta.*`) و فقط در scope ِ خودش (`shop` ⇒ `all` یا دکانِ موجود؛ `all` ⇒ `all`؛ `security`/`release` ⇒ همان) — وگرنه
  `bad_flag_value` / `bad_flag_scope`. کلیدِ ناشناخته همان رفتارِ پیشین.
- **برای #582 (ماتریسِ جداسازی)،** هر کدام که دوم مرج شود: `'GET /api/admin/search': { exempt: 'جست‌وجوی سکوی مدیر به نقش؛ فقط
  شناسه و نامِ نمایشی، بی دفتر یا متنِ دکان' }` و `'GET /api/admin/admins/:id/sessions': { exempt: 'نشستِ مدیرِ سکو، نه دادهٔ دکان' }`.

آزمون: `test/console-shell.test.js` (۶ بند، سرخ روی پایه ⇐ سبز) و `CAPABILITY_PROBES` ِ `admin-control-plane.test.js`.
### ۱۲.۸ بازار (۴.۶) و نظارت بر پیک (۴.۷) — سمتِ مدیر (۱۴۰۵/۰۸/۱۰، شاخهٔ `claude/console-market-api`)

- **توانایی‌ها:** `market-admin.v1` (`/admin/market/*`) و `delivery-watch.v1` (`/admin/delivery/*`). همه خواندنی‌اند جز
  `PUT /admin/delivery/thresholds` (فقط ارشد + دلیل؛ تکرارِ همان مقدار ⇒ `unchanged` بی ردیفِ دوم در تاریخچه).
- **نوشتنِ بازار درِ تازه ندارد:** تعلیقِ فروشنده = حالتِ `service_disabled` با سرویسِ `market.directory` (یا `shop.storefront`)
  از همان `/admin/shops/:id/holds` (بند ۱۲.۱)؛ تصمیمِ پرونده و برداشتنِ جریمه همان `/admin/trust/*` (فقط ارشد، دلیل).
- **مدل همان است که هست:** فروشنده = دکانِ VILL3N (فروشندهٔ مستقل در مدل نیست ⇒ `null`)؛ «تأییدِ فروشنده» نیست (بودن در فهرست
  را صاحبِ دکان روشن می‌کند و سامانه با پرچم/حالت/جریمه می‌بندد ⇒ `gates` و `blockedBy`)؛ سفارشِ بازار تک‌دکانی؛ کمیسیون و
  سبدِ چنددکانی فقط با ستون‌های مهاجرتِ ۱۳۰ (#432) ⇒ تا آن‌وقت `inModel: false`، نه صفر.
- **«گیر کرده / ناهم‌خوان / اختلاف»** فقط از `lib/order-watch.js` (خالص؛ همان دو ماشینِ وضعیت). آستانه‌ها در `live_config`
  (`console.delivery_thresholds`، scope ِ `all`)؛ مقدارِ نامعتبر ⇒ پیش‌فرض و `source: 'default'`.
- **بی دادهٔ شخصی:** هیچ پاسخی نام، شماره، نشانی یا یادداشتِ مشتری و هیچ کد/هشِ تحویل ندارد (آزمونِ `admin-market-delivery`).
- **گزارشِ کارکرد** با تعریف‌های RPT-15 (#554) و تطبیقِ مدیر (#411): «گرفته» از `delivered_at`، «تسویه» از `created_at` ِ سند،
  «در دست» = تحویل‌شدهٔ تسویه‌نشده؛ جمعِ ردیف‌ها = جمعِ کل؛ دیدنش یک ردیفِ سابقه.
- **مهاجرتِ ۱۷۲:** فقط نمایه برای پرسش‌های بی‌دکانِ مدیر، با خط‌های `rollback:` ِ آزموده.

### ۱۲.۹ مالیِ مرکزی — ماژولِ ۴.۵ (۱۴۰۵/۰۸/۱۰، شاخهٔ `claude/console-finance-api`، مهاجرتِ ۱۷۱)

پولِ اشتراکِ **خودِ پلتفرم**، جدا از حساب‌داریِ داخلیِ دکان‌ها. توانایی: `finance.v1`. همه زیرِ `requireAdmin` و مرزِ
`lib/admin-roles.js` (finance: همهٔ این درها؛ viewer: فقط GET)؛ نوشتن و خروجی در خودِ در به `superadmin`/`finance`
بسته است (`requireFinanceWriter`) — مدیرِ عادی (پشتیبانی) نه.

| در | شرح |
|---|---|
| `GET /api/admin/finance/summary` | `group=day\|week\|month`، `calendar=persian\|gregorian`، `fromDate`/`toDate` (یا `from`/`to`)، `app` ⇒ جمعِ هر ارز (پرداخت، اصلاح، بازپرداخت، ابطال، خالص)، سطل‌ها به وقتِ `DEFAULT_TIMEZONE` (هفته از شنبه، ماهِ خورشیدی)، به تفکیکِ بخش و روش. حداکثر ۷۳۲ روز و ۴۰۰ سطل |
| `GET …/breakdown?by=shop\|plan` | تفکیکِ بازه، صفحه‌بندیِ سمتِ سرور (`limit`/`offset`، `total`) |
| `GET …/payments` · `…/payments/:id` | دفترِ پرداخت با نشانگر (`paid_at`، `id`) و پالایش؛ یک پرداخت + سندها + `sub_payment_revisions` + سابقه |
| `GET …/adjustments` · `POST …/adjustments` | سندها؛ ساختنِ سند `{paymentId, kind: correction\|refund\|reversal\|reference, amount?, reference?, reason, expectedNet?}` + `Idempotency-Key` |
| `GET …/receivables` | بدهی = قیمتِ اشتراک (یا قیمتِ پلن در روزِ آغاز، همان قاعدهٔ `/sales/debts`) − (پرداخت + سند) **به همان ارز**؛ فعال/معلق |
| `GET …/pending` · `…/gateway` | درخواست‌های خرید با قیمتِ برآوردی؛ درگاهِ حساب‌پی (`payment_intents` ِ #518/#529): بی جدول یا بی کلید ⇒ `state: blocked_external` با پیامِ «BLOCKED-EXTERNAL: کلیدِ حساب‌پی نیامده»، وگرنه شمارِ هر وضعیت، «بی شمارهٔ تراکنش» و «نیازمندِ بررسی» |
| `GET …/discrepancies` | پرداختِ بی اشتراک، اشتراک/دکانِ ناپیدا، ارزِ ناهمسان، رسیدِ تکراری، ویرایش/حذفِ راهِ پیشین، سندِ پرداختِ حذف‌شده، شمارهٔ تراکنشِ تکراری، بیش‌پرداخت، درگاه (اگر زنده) — هر یافته با شمارِ دقیق و تا ۵۰ نمونه |
| `GET …/history` | ردیف‌های سابقهٔ کارهای مالی با نشانگر |
| `GET …/export?dataset=payments\|adjustments` | `{csv, rows, nextCursor, filename}` — حداکثر ۵۰۰۰ ردیف در هر صفحه، BOM، خنثیِ فرمول، یک ردیفِ سابقه برای هر صفحه |

- ⛔ **احرازِ دوباره برای سند:** نشستی که در `REAUTH_WINDOW_MIN` ساخته شده، یا `password` ِ همان مدیر (۵ اشتباه در ربع ساعت ⇒ ۴۲۹،
  هر اشتباه یک ردیفِ `admin.finance_reauth_failed`)؛ پلِ پنل با کلیدِ سرویس ⇒ `X-Actor-Reauth-At` (زمانِ رمزِ پنل، تازه‌تر از همان
  پنجره) — به همان اندازه معتبر که کلیدِ سرویس. رمز هرگز در هشِ کلیدِ یکتایی یا هیچ ردیفی نمی‌نشیند.
- ⛔ **جدول‌ها فقط افزودنی** (تریگرِ `finance_refuse_change`)؛ `sub_payments_revision` هر UPDATE/DELETE ِ `sub_payments` را با ردیفِ
  پیش و پس نگه می‌دارد. **خالص هرگز منفی** (قید + قفلِ ردیفِ پرداخت)؛ ابطال = همهٔ خالصِ همان لحظه.
- ⚠️ وقتی #582 (`tenant-matrix.test.js`) مرج شد، `GET /api/admin/finance/payments/:id` (تنها درِ شناسه‌دارِ مالی) در `ADMIN_SUBJECT`
  رده می‌گیرد — خطِ آماده در بندِ ۱۲.۵؛ بقیهٔ `GET /api/admin/finance/*` بی‌شناسه‌اند (دادهٔ پولیِ پلتفرم به‌ازای دکان؛ فقط مدیر).

