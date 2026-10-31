# امنیتِ ورود، نشست و مجوز — SEC-AUTH (از ۱۴۰۵/۰۸/۰۹)

دامنه: §۷ و §۱۳ِ `docs/SECURITY-PROGRAM-fa.md` (شاخهٔ `claude/sec-auth`) — کاربرانِ دکان و پمپ، کارمندان، دستگاه‌ها و
پنلِ مدیر. **بیرون از دامنه** (تا ساختنِ همان بخش): حساب و ورودِ مشتریِ فروشگاهِ آنلاین (`/api/store/*`، `/api/market/*`).

⛔ هیچ بندی «۱۰۰٪ امن» نیست. هر وضعیت با آزمونِ اجراشده است؛ آن‌چه اجرا نشد `NOT-TESTED` یا `BLOCKED` است.
آزمون‌ها روی PGlite (`TEST_DATABASE_URL=pglite:memory`) و دکان/پمپ/مدیرِ ساختگی، روی همین ماشین؛ هیچ سرور یا دادهٔ زنده‌ای لمس نشد.

PRها: **A** = `claude/sec-auth` (همین سند) · **B** = #587 `claude/sec-auth-device`.

## ۱. نقشهٔ واقعیِ مسیرها (آن‌چه دیده شد)

| کار | در | نگهبان |
|---|---|---|
| ثبت‌نام (سه‌پله)، ورود با رمز، کدِ یک‌بارمصرف، گوگل، کدِ ایمیلیِ هر برنامه | `routes/auth.js`، `routes/app-auth.js` | سقفِ IP و مقصد، قفلِ «شناسه + IP» و سراسری، `burnTime` |
| تازه‌سازی (چرخشی)، خروج، خروج از همه، رمز، بازیابی | `routes/auth.js`، `lib/tokens.js` | ردِ بازپخش ⇒ بستنِ خانواده؛ عوض شدنِ رمز ⇒ بستنِ نشست‌ها |
| تأییدِ دوباره (کارِ حساسِ دکان) | `lib/reauth.js` | پنجرهٔ ده‌دقیقه‌ای بسته به هشِ توکن |
| ورود با کدِ شاگرد، پیوستن، نقش، برداشتن/تعلیقِ عضو | `routes/auth.js` (`/staff-login`)، `routes/shop.js`، `routes/pump.js` | `members.role`/`members.manage` فقط صاحب، صاحب تغییرناپذیر |
| دستگاه: ثبت (هنگامِ ورود)، باطل (`/me/devices`، `/shop/devices/*/disable`، پمپ `revoke/restore`) | `routes/me.js`، `routes/shop-staff.js`، `routes/pump*.js` | مالکیت در سرور |
| ورودِ مدیر، TOTP، مدیرها | `routes/admin.js`، `routes/admin-2fa.js`، `lib/admin-2fa.js` | توکنِ `kind=admin` جدا |

توکن‌ها **مات‌اند، نه JWT** (۳۲ بایت تصادفی، فقط SHA-256 در `tokens`)؛ پس کلیدِ امضای JWT و حمله‌های `alg` روی نشستِ خودمان
موضوع ندارد. تنها JWTی که سرور می‌خواند ID Tokenِ گوگل است (SEC-AUTH-07). `JWT_SECRET` فقط به‌عنوانِ رازِ پشتیبانِ HMAC به کار می‌رود.

## ۲. یافته‌ها

| شناسه | شدت | مسیرِ واقعی | ریشه | اصلاح / PR | آزمون (پرونده › نام) | وضعیت | خطرِ مانده | کارِ صاحب محصول |
|---|---|---|---|---|---|---|---|---|
| SEC-AUTH-01 | Low (S20) | `lib/password.js` | scrypt با `N=2^14, r=8, p=1` — یک‌پنجمِ کفِ OWASP | `p=5` (هم‌ارزِ رسمیِ OWASP، همان ۱۶MB، ~۲۰۰ms)؛ هشِ کهنه پس از ورودِ درستِ کاربر و مدیر شرطی بازنویسی می‌شود؛ `PASSWORD_SCRYPT_P` فقط در test/dev پایین می‌رود — A | `sec-auth.test.js` › «SEC-AUTH-01: …» ۲/۲ | FIXED-PENDING-MERGE | رمزِ غلط روی هشِ کهنه ~۵۰ms کندتر از «حساب نیست» (۲۵۰ در برابرِ ۲۰۰ms) تا همهٔ هش‌ها نو شوند | — |
| SEC-AUTH-02 | — | `lib/otp.js`، `lib/login-codes.js` | (بازبینی) کد: `randomInt`، فقط HMAC، ۱۰ دقیقه، یک‌بارمصرف (UPDATE شرطی)، بسته به مقصد + `purpose`، ۵ تلاشِ اتمی روی تازه‌ترین ردیف، ۲۰ کد در روز، پاسخ و زمانِ یکسان | — | `login-hardening` ۳۶/۳۶، `password-reset`، `account-enumeration`، `otp-late-mail`، `otp-desk`، `otp-cost` (محلی) | PASS | سقفِ حدس: ۲۰ کد × ۵ تلاش در روز برای هر مقصد (حداکثر ۳ کدِ زنده) | — |
| SEC-AUTH-03 | Medium (S17، نیمهٔ «قفلِ سراسری») | `routes/admin.js` › `/login` | هر ۸ رمزِ غلط برای یک نامِ مدیر، **از هر IP**، همان مدیر را ۱۵ دقیقه می‌بست ⇒ هر کس نامِ مدیرِ ارشد را می‌دانست صاحب را از پنل بیرون نگه می‌داشت | همان دو شمارندهٔ کاربران: «نام + IP» (۸) و «نام، از همه‌جا» (۴۰) — A | `sec-auth.test.js` › «SEC-AUTH-03: …» | FIXED-PENDING-MERGE | حملهٔ واقعاً پخش‌شده (۴۰ شکست از چند IP در ربع ساعت) هنوز قفل می‌کند؛ راهِ نجات: صبر یا `ADMIN_BOOTSTRAP_PASSWORD` | `TRUST_PROXY=true` روی سرورِ پشتِ پنل (وگرنه همه یک IPاند) |
| SEC-AUTH-04 | Low | `routes/admin.js` › `/login` | نامِ مدیرِ ناموجود یا غیرفعال scrypt اجرا نمی‌کرد ⇒ نامِ مدیر از زمانِ پاسخ پیدا می‌شد | `burnTime` مثلِ ورودِ کاربر — A | `sec-auth.test.js` › «SEC-AUTH-04: …» | FIXED-PENDING-MERGE | — | — |
| SEC-AUTH-05 | Medium (شرطی) | `middleware/ratelimit.js` › `clientIp`، `app.js` | با `TRUST_PROXY=true`، `X-Forwarded-For` از **هر** همسایه‌ای خوانده می‌شد؛ سرور پیش‌فرض روی `0.0.0.0` است ⇒ هر کس مستقیم به آن می‌رسید با سرآیندِ تازه سقفِ ورود/کد و قفلِ «شناسه + IP» را دور می‌زد | سرآیند فقط از همسایهٔ loopback/خصوصی (یا `TRUST_PROXY_FROM`)، فقط راست‌ترین؛ همان تابع برای `trust proxy`ِ Express — A | `sec-auth.test.js` › «SEC-AUTH-05: …» ۲/۲ | FIXED-PENDING-MERGE | پروکسیِ شبکهٔ محلی (۱۹۲.۱۶۸…) پیش‌فرض مطمئن است | پشتِ پنلِ خانگی `TRUST_PROXY_FROM=127.0.0.1` بگذارید |
| SEC-AUTH-06 | **High (شرطی)** | `lib/otp.js` (`devCode` و لاگِ `log`) | «هر چه production نیست» کد را در **پاسخ** پس می‌داد؛ سروری که بی `NODE_ENV` بالا بیاید و راهِ پیامک/ایمیل `log` باشد ⇒ ورود با شماره/ایمیلِ هر کس | فقط با `NODE_ENV`ِ صریحِ `test`/`development` (`config.devCodes`)؛ هشدارِ راه‌اندازی — A | `sec-auth.test.js` › «SEC-AUTH-06: …»؛ `login-hardening` (لاگِ production) | FIXED-PENDING-MERGE | — | روی سرورِ واقعی `NODE_ENV=production` را **بسنجید** (پنلِ سلامت: `production`) |
| SEC-AUTH-07 | Low | `lib/google.js` › `verifyIdToken` | `exp`/`iat`ِ نبوده یا نادرست (NaN) از هر دو مقایسه رد می‌شد ⇒ توکنِ بی‌پایان (با امضای گوگل) | عددِ متناهی اجباری — A. سنجاقِ `RS256`، `kid`، امضا، `iss`، `aud` (مقایسهٔ زمان‌ثابت) از پیش بود | `sec-auth.test.js` › «SEC-AUTH-07: …» (HS256، none، iss/aud دیگر هم) | FIXED-PENDING-MERGE | با گوگلِ زنده آزموده نشده | — |
| SEC-AUTH-08 | — | `lib/tokens.js`، `routes/auth.js` | (بازبینی) چرخشِ تازه‌سازی، بازپخش پس از ۳۰ثانیه ⇒ بستنِ کلِ خانواده، `markRotated` شرطی؛ خروج، خروج از همه، عوض شدنِ رمز (نشست‌های دیگر) و بازیابی (همه + جدا کردنِ گوگلِ تأییدنشده)؛ تعلیق/برداشتنِ عضو همهٔ توکن‌هایش را می‌بندد | — | `login-contract`، `login-hardening`، `password-reset`، `security-hardening` (محلی) | PASS | — | — |
| SEC-AUTH-09 | Medium | `routes/pump.js` › `PATCH /members/:id`، `PATCH /` | نقش و وضعیتِ عضوِ پمپ و تنظیمِ پمپ **هیچ ردیفِ سابقه‌ای** ندارند (دکان دارد) | `pump.member_updated` و `pump.station_updated` با کنشگر، زمانِ سرور، هدف و نتیجه — **در #588 نیست**: کدِ پمپ بی اجازه دست نمی‌خورد ⇒ #615 (`claude/sec-pump-pending`، پیش‌نویس) | `sec-pump-pending.test.js` › «SEC-AUTH-09: …» (در #615) | BLOCKED — نیازمندِ اجازهٔ صاحب‌کار (کدِ پمپ) | سابقه فقط کارِ موفق را می‌نویسد؛ ردِ مجوز (۴۰۳) سابقه ندارد | — |
| SEC-AUTH-10 | — (کنترلِ تازه) | همهٔ `/api` | دری که تازه ساخته شود و احراز/حسابِ توکن نداشته باشد آزمونی سرخ نمی‌کرد (فهرست‌ها فقط `/api/shop`، `/api/admin`، پیک و مشتری بودند) | `route-authz-inventory.test.js`: هر درِ نوشتنی (بیش از ۳۶۰) PUBLIC/SELF با دلیل یا زنجیرهٔ احراز + حسابِ توکن؛ بی‌توکن ۴۰۱؛ **توکنِ مدیر روی هیچ درِ غیرِ مدیر کار نمی‌کند**. فهرستِ زنده به `route-inventory.live` رفت و E2E-SEC-13 هم همان را می‌خواند — A | `route-authz-inventory.test.js` ۳ آزمون | PASS (هیچ درِ بی‌نگهبان پیدا نشد) | GETها در این فهرست نیستند (خواندنِ عمومی بسیار است) | — |
| SEC-AUTH-11 | — | `POST /api/shop`، `PUT /api/me`، `PATCH /api/shop/members/:id`، `POST /api/shop/invite`، `POST /api/admin/admins`، `POST /api/pump` | (بازبینی) mass assignment: همهٔ دستگیره‌ها فیلد به فیلد می‌خوانند؛ `role/isAdmin/ownerId/shopId/accountId/userId` نادیده یا ۴۰۰ | — | `route-authz-inventory.test.js` › «SEC-AUTH-11: …» | PASS | — | — |
| SEC-AUTH-12 | Medium (S17، F0-15) | `middleware/auth.js` › `requireAdmin`، `lib/admin-guard.js` | MFAِ مدیر فقط اختیاری بود و راهی برای اجباری کردن نبود | پرچمِ **خاموشِ** `ADMIN_2FA_REQUIRED`: مدیرِ بی TOTP با رمزِ درست فقط به «روشن کردن» می‌رسد (در هر درخواست از ردیفِ زنده؛ نشستِ کهنه هم)؛ پنل خودش پنجرهٔ روشن کردن را باز می‌کند. راهِ نجات (`ADMIN_BOOTSTRAP_PASSWORD`/`create-admin.js`) TOTP را خاموش و «روشن کردن» را باز می‌کند ⇒ صاحب قفل نمی‌شود — A | `sec-auth.test.js` › «SEC-AUTH-12: …» ۲/۲ | FIXED-PENDING-MERGE (پرچم خاموش) | مدیری که هنوز TOTP نساخته، با رمزِ دزدیده‌شده می‌تواند TOTPِ خودِ دزد را بسازد (ذاتیِ «ثبت‌نام در نخستین ورود») | خودتان TOTP بسازید، بعد `ADMIN_2FA_REQUIRED=1`؛ کدهای بازیابی را بیرون از کامپیوتر نگه دارید |
| SEC-AUTH-13 | Medium | `requireSuperAdmin`، `POST /api/admin/reauth`، دانلودِ پشتیبانِ حساب | توکنِ ۱۲ساعتهٔ مدیر بی هیچ پرسشی مدیرِ ارشدِ تازه می‌ساخت، رازها و پلن‌ها را عوض و داده را بیرون می‌برد | پرچمِ **خاموشِ** `ADMIN_REAUTH`: هر نوشتنِ پشتِ `requireSuperAdmin` و دانلودِ پشتیبانِ حساب فقط تا `REAUTH_WINDOW_MIN` پس از ورود یا `/admin/reauth` (رمز + TOTP اگر روشن است؛ ۴۰۳ نه ۴۰۱)؛ پنل پنجرهٔ «تأییدِ دوباره» دارد و همان درخواست را دوباره می‌فرستد — A | `sec-auth.test.js` › «SEC-AUTH-13: …» ۲/۲ | FIXED-PENDING-MERGE (پرچم خاموش) | دادنِ اشتراک از مدیرِ عادی پشتِ آن نیست (کارِ روزانهٔ پشتیبانی)؛ `admin-android` هنوز `admin_reauth_required` را نمی‌شناسد | پیش از روشن کردن، `admin-android` را به‌روز یا فقط از پنلِ وب کار کنید؛ بعد `ADMIN_REAUTH=1` |
| SEC-AUTH-14 | **High** | `lib/sync-v1-auth.js` (همهٔ درهای Sync v1/v3) | `device_id`ِ کلاینت بی سنجش؛ شاگرد با شناسهٔ دستگاهِ صاحب (از `/status`) «حذفِ ردیفِ خودم» را دور می‌زد و ردیف‌های صاحب را پاک می‌کرد؛ سرِ زنجیرهٔ هرِ دستگاه (#541) هم جعل‌پذیر | شناسه باید دستگاهِ همان توکن باشد؛ پرچمِ `sync.device_bind` روشن ⇒ ۴۰۳، خاموش (پیش‌فرض) ⇒ فقط سابقهٔ `sync.device_mismatch` — B (#587) | `sync-device-bind.test.js` ۴/۴ (پیش از اصلاح ۰/۴) | FIXED-PENDING-MERGE (پرچم خاموش) | تا روشن شدنِ پرچم، باز؛ دستگاهِ تازه با ورودِ دوباره ساختنی است (رمز/کد می‌خواهد و در فهرستِ دستگاه‌ها دیده می‌شود) | چند روز سابقهٔ `sync.device_mismatch` را ببینید، بعد `sync.device_bind=true` روی `all` |
| SEC-AUTH-15 | High (شرطی) | `lib/staff-codes.js`، `lib/server-secret.js` و هر `pepper()` | بی `API_SECRET`/`OTP_SECRET`، کدِ ثابتِ شاگرد HMAC با **رشتهٔ ثابتِ سورس** است ⇒ از روی شناسهٔ دکان ساختنی؛ `validate()` فقط با `NODE_ENV=production` رازها را می‌خواهد | هشدارِ راه‌اندازی (نه توقف — سرورِ نصب‌شده بیرون نیفتد) — A | — (فقط پیام) | NOT-TESTED (رفتار عوض نشد؛ وضعیتِ سرورِ واقعی را نمی‌دانیم) | اگر سرور بی راز است، همهٔ کدهای شاگرد ساختنی‌اند | روی سرورِ خانگی بسنجید: `NODE_ENV=production`، `API_SECRET` و `OTP_SECRET` (۲۴+ نویسه). گذاشتنِ رازِ تازه کدهای ثابتِ شاگرد را عوض می‌کند (شاگردهای فعلی می‌مانند) |
| SEC-AUTH-16 | Low | `routes/support.js` › `who`، `lib/support.threadFor` | رشتهٔ پشتیبانیِ **مهمان** فقط با شناسهٔ دستگاه پیدا می‌شود؛ کسی که شناسهٔ دستگاهِ مهمانی را بداند آن رشته را می‌خواند، و با توکنِ خودش آن را به حسابِ خودش می‌چسباند | — (ثبت) | — | FAIL (Low، اصلاح نشد) | شناسهٔ دستگاه تصادفی است و فقط تا پیش از ثبت‌نام؛ ولی صاحب/مدیرِ دکان شناسهٔ دستگاهِ کارمندانش را می‌بیند | PRِ بعدی: چسباندن فقط وقتی شناسه همان دستگاهِ توکن است |
| SEC-AUTH-17 | — | `src/**` | (بازبینی) در پشتی، رمزِ ثابت، درِ پنهان/debug، جعلِ کاربر (impersonate) | — | grep روی `src` (رمز/کلیدِ ثابت، `/debug`، `/_`، `impersonat`، سرآیندِ ویژه) | PASS (چیزی پیدا نشد) | `ADMIN_BOOTSTRAP_PASSWORD` در محیط عمداً راهِ نجات است | — |
| SEC-AUTH-18 | — | E2E-SEC-13، `shop-route-inventory`، `pump-isolation`، `staff-join-role` | (بازبینی) IDOR/BOLA و ارتقای نقش: شناسهٔ دکان/پمپ فقط از توکن و عضویت؛ نقش را فقط صاحب عوض می‌کند و «صاحب» از هیچ دری ساخته نمی‌شود؛ دعوتِ مدیر تأییدِ دوباره می‌خواهد | — | `shop-route-inventory` + `security-hardening` + `staff-join-role` ۱۸/۱۸، `role-route-isolation` ۳/۳، `pump-isolation` (محلی؛ جز «به کامپیوترِ دیگر ببر» که `pg_dump` می‌خواهد) | PASS | کاربرِ چنددکانی: #488 (AUTH-09) | — |

## ۳. هماهنگی با PRهای باز

- **#498** (AUTH-08، افزودنِ شماره فقط با کدِ همان شماره، پرچمِ `auth.phone_add_code`): همان یافتهٔ S19 است؛ این‌جا دست نخورد (`routes/me.js` مالِ آن است).
- **#493** (AUTH-10، لاگِ بی راز): `lib/mailer.js` مالِ آن است. SEC-AUTH-06 فقط `lib/otp.js` را عوض کرد (کدِ خام در لاگ هم فقط با `config.devCodes`)؛ هم‌پوشانی ندارد.
- **#488** (AUTH-09) فقط `shop-route-inventory.test.js` را عوض می‌کند؛ این‌جا آن پرونده دست نخورد و آزمونِ تازه پروندهٔ جداست.
- **#534** (TEN-02) هم `app.js` را عوض می‌کند (جای دیگر)؛ این‌جا فقط خطِ `trust proxy`.
- **#541/#547/#571** (زنجیرهٔ اسناد، S89): درِ زنجیره زیرِ `requireSyncAccount` است، پس پس از مرج خودبه‌خود پشتِ SEC-AUTH-14 می‌نشیند. هیچ پرونده‌ای مشترک نیست.
- مهاجرت لازم نشد (۱۶۰ استفاده نشد).

## ۴. کارهای صاحب محصول (به ترتیب)

1. روی سرورِ خانگی بسنجید: `NODE_ENV=production`، `API_SECRET` و `OTP_SECRET` پُر (SEC-AUTH-06، ۱۵) و `TRUST_PROXY=true` با `TRUST_PROXY_FROM=127.0.0.1` (SEC-AUTH-03، ۰۵).
2. TOTPِ خودتان را در پنل روشن کنید و کدهای بازیابی را بیرون از کامپیوتر نگه دارید؛ بعد `ADMIN_2FA_REQUIRED=1` (SEC-AUTH-12).
3. پس از به‌روز شدنِ `admin-android` (یا کار فقط از پنلِ وب): `ADMIN_REAUTH=1` (SEC-AUTH-13).
4. پس از مرجِ #587 و چند روز سابقهٔ `sync.device_mismatch`: `sync.device_bind=true` روی `all` (SEC-AUTH-14).

## ۵. آن‌چه آزموده نشد

- CIِ GitHub برای این PRها هنوز ندویده (صفِ Actions)؛ همهٔ اعداد بالا اجرای محلی روی PGlite است. آزمون‌های هم‌زمانی روی PostgreSQLِ واقعی این‌جا اجرا نشد (`pg_dump`/PostgreSQL روی این ماشین نیست).
- ورود با گوگلِ واقعی، پیامک/ایمیلِ واقعی و `admin-android` (Gradle این‌جا ساخته نمی‌شود).
- رفتارِ برنامهٔ پمپ (ریپوی دیگر) با SEC-AUTH-14 — همان دلیلِ «فقط ثبت» در پیش‌فرض.
