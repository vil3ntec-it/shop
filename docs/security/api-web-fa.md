# امنیتِ API و وب — یافته‌ها و وضعیت (`claude/sec-api-web`، ۱۴۰۵/۰۸/۰۹)

> برنامهٔ امنیت: `docs/SECURITY-PROGRAM-fa.md` (شاخهٔ `claude/security-program`)، بخش‌های §۲.۳، §۱۰ و §۱۱ (وب).
> دامنه: همهٔ مسیرهای `server/src` (API، پنلِ مدیر، پورتال، صفحه‌های ایستا) و نسخهٔ وبِ دکان (`index.html`، `license/*.js`، `sw.js`).
> بیرون از دامنه (فقط ثبت): هر چه مالِ فروشگاهِ آنلاینِ **مشتری** است (ویترین/بازارچه از سمتِ خریدار، حسابِ مشتری، پرداختِ مشتری).
> هیچ کاری روی سرورِ زنده یا دادهٔ واقعی نشد؛ همهٔ حمله‌ها روی PGlite ِ حافظه و مرورگرِ آزمون با دادهٔ ساختگی.

| PR | موضوع |
|---|---|
| #589 | SEC-API-01 و SEC-API-08 — XSS ِ ذخیره‌شده در نسخهٔ وب (`index.html`، `license/license-client.js`) |
| `claude/sec-api-web` | SEC-API-02..07 و SEC-API-12 — سرور: بدنه پس از توکن، SSRF، `devCode`، CSP ِ API و پورتال، همگام‌سازیِ قدیم، نگهبانِ SQL، دستگیرهٔ تکراری؛ و همین سند |

## ۱. فهرستِ واقعیِ مسیرها (از کد، نه از سند)

روش: سوار شدن‌های `api.use('/…', require('./routes/…'))` در `src/app.js` + روترهای تودرتوی `router.use('/…', require('./…'))`
(و `makeRouter`) + `api.get`/`app.get` ِ خودِ `app.js`. هر مسیر هم زیرِ `/api` و هم زیرِ `/api/v1` است.
**۶۲۴ مسیر** در ۶۰ پرونده (۱۴۰۵/۰۸/۰۹، main ‏a43a41b). خلاصه (نگهبانِ سطحِ روتر؛ «—» یعنی هر مسیر نگهبانِ خودش را دارد یا عمداً باز است):

| پیشوند | پرونده | شمار | نگهبانِ سطحِ روتر |
|---|---|---|---|
| `/auth`، `/auth/:app` | auth، app-auth | ۲۰ + ۳ | — (ورود؛ سقفِ نرخِ جدا روی هر در) |
| `/sync/v1`، `/sync/v3` | sync-v1، sync-v3 | ۱۵ + ۸ | سقفِ نرخِ هر حساب + `requireSyncAccount` |
| `/sync`، `/shop/sync` (قدیم) | sync | ۱۰ | `requireUser, requireShop` |
| `/me`، `/me/backups` | me، account-backups | ۱۷ + ۴ | `requireUser, optionalShop` |
| `/shop` و زیرشاخه‌ها | shop، shop-staff، shop-chat، shop-trust، catalog، order-notify، orders، storefront-admin، shop-receipt، market-admin، shop-images، delivery، shop-promos، data | ۲۵+۱۰+۱۶+۶+۱۱+۳+۱۰+۷+۶+۲+۱۲+۱۹+۴+۶ | `requireUser` + `requireShop` (+ `requirePermission`) |
| `/courier` | courier | ۴ | نشستِ پیک (هر مسیر) |
| `/pump`، `/pump/device`، `/pump/public` | pump، pump-device، pump-public (+ pump-support، pump-events، account-backups) | ۳۱ + ۳۰ + ۱۱ (+۱۴، ۶، ۸) | `requirePumpUser`+`requireStation` / `requireDevice` / کدِ کیو‌آر |
| `/admin` و زیرشاخه‌ها | admin، admin-2fa، admin-logins، admin-otp، admin-sync، admin-system، admin-release، admin-platform، admin-pump، admin-sales، admin-notices، admin-image-bank، admin-trust، admin-shop-chat، admin-shop-desk، admin-shop-offline، admin-staff-codes، admin-staff-perms | ۲۰۸ | `requireAdmin` (و `requireSuperAdmin` روی کارهای حساس) |
| `/portal`، `/rep` | portal، rep | ۱۶ + ۱ | `requireAnyUser` |
| `/store`، `/market`، `/receipt` | storefront، shop-chat-store، trust-store، market، receipt-public | ۲۶+۱۴+۷+۷+۱ | سمتِ خریدار — **بیرون از دامنه** (فقط سرآیندها دیده شد) |
| `/support`، `/visit`، `/vip`، `/errors`، `/location`، `/events`، `/license`، `/releases` | … | ۵+۱+۲+۱+۲+۳+۲+۲ | عمداً بی توکنِ اجباری (پشتیبانیِ مهمان، تپش، انتشار) یا `requireUser` |
| `/health`، `/ready`، `/config`، `/plans`، `/terms`، `/downloads` | app.js | ۱۲ | باز، فقط‌خواندنی، بی راز |

پوشش‌های موجود که همین فهرست را می‌سنجند (کارِ `sec-auth`/`sec-tenant`): `admin-control-plane.test.js` (هر درِ مدیر بی توکن ۴۰۱)،
`shop-route-inventory.test.js` و `role-route-isolation.test.js` (هر درِ `/shop` میانِ دکانِ A و B).

## ۲. یافته‌ها

| شناسه | شدت | مسیرِ واقعیِ کد | ریشه | اصلاح / PR | آزمون (پرونده › نام) | وضعیت | ریسکِ باقی | کارِ صاحب محصول |
|---|---|---|---|---|---|---|---|---|
| SEC-API-01 | **بالا** | `index.html` (‎~۴۷ جا: ‎`data-…-id="${p.id}"`‎، ‎`class="tx-item ${t.type}"`‎، `formatDate`، `formatCartQty`، برچسبِ نوعِ جابه‌جاییِ انبار و سابقه) | مقدارِ دفتر (شناسه تا ۸۰ نویسهٔ دلخواه، نوع، تاریخ، مقدار) خام در `innerHTML`؛ `formatDate` تاریخِ ناجور را خام برمی‌گرداند؛ CSP ِ وب `'unsafe-inline'` دارد. شاگردی با «ثبتِ اطلاعات» (یا پشتیبانِ دست‌کاری‌شده) ردیفی می‌فرستد و اسکریپتش در مرورگرِ **صاحبِ دکان** با توکنِ او اجرا می‌شود | `escAttr`/`plainText` و رمزِ همهٔ این جاها — #589، بی پرچم (جلوی نشتِ توکن و داده را می‌گیرد؛ خروجیِ دادهٔ سالم بایت‌به‌بایت همان) | `server/test/web-xss-sync.mjs` (۲۳ گام: ۱۹ صفحه + حساب، خرید، سبد/فروشِ سریع، جزئیات) — پیش از اصلاح ۲۳/۲۳ سرخ (۲۱ بارِ جدا اجرا شد)، پس از آن ۲۳/۲۳ سبز؛ جهش: `escAttr` بی رمز ⇒ سرخ، `formatDate` ِ خام ⇒ سرخ | FIXED-PENDING-MERGE | CSP ِ وب هنوز `'unsafe-inline'` دارد (کارِ فاز ۲، `web-csp.test.js`)؛ جای تازه‌ای که بی `escAttr` ساخته شود همان حفره است — این آزمون همهٔ صفحه‌ها را با دادهٔ آلوده می‌گردد | — |
| SEC-API-02 | میانه | `src/app.js` (پارسرهای بدنه) | پارسرها بالای همه‌چیز بودند: بدنهٔ هر درخواست پیش از سقفِ نرخ خوانده و parse می‌شد. **درهای پمپ:** ‎`/pump/device/state`‎ ِ ۴mb بی هیچ توکن و پیش از سقفِ نرخ خوانده می‌شود و سقفِ ۳۴mb ِ ‎`/report/`‎ مرده است (RegExp اش در Express 5 جور نمی‌شود؛ گزارشِ بالای ۲mb ِ برنامهٔ پمپ ۴۱۳ می‌گیرد) | **درهای غیرِ پمپ:** سقفِ نرخ ⇐ پارسر — `claude/sec-api-web`. **درهای پمپ (‎`/api/pump/*`‎، ‎`/api/admin/pump/*`‎) همان main** (کدِ مشترکِ پمپ بی اجازه دست نمی‌خورد)؛ درمانِ پمپیِ (سقفِ نرخ ⇐ `requireDevice` ⇐ پارسرِ بزرگ، نشانیِ رشته‌ای) در #615 (`claude/sec-pump-pending`، پیش‌نویس) | `api-web-hardening.test.js` › «SEC-API-02: درهای غیرِ پمپ …» (روی main سرخ) و «کدِ پمپ: …» (۲ آزمون: ترتیبِ لایه‌ها و رفتارِ درِ پمپ همان main؛ روی نسخهٔ پیشینِ این شاخه سرخ)؛ درمانِ پمپ: `sec-pump-pending.test.js` در #615 | غیرِ پمپ: FIXED-PENDING-MERGE · پمپ: BLOCKED — نیازمندِ اجازهٔ صاحب‌کار (کدِ پمپ) | پارسرِ همگانیِ ۲mb هنوز پیش از توکن است (روالِ عادیِ Express؛ پس از سقفِ نرخ) | — |
| SEC-API-03 | میانه | `routes/admin-platform.js` (‎POST/PUT /admin/apps‎)، `lib/managed-apps.js` (`checkHealth`) | هر مدیرِ معمولی (نه فقط ارشد) `healthUrl` ِ دلخواه می‌گذاشت و «سنجیدنِ سلامت» از خودِ سرور به آن وصل می‌شد و وضعیت/زمان/خطا را پس می‌داد ⇒ شمارشِ درگاه‌های شبکهٔ خانگی (SSRF)؛ طرحِ نشانی هم سنجیده نمی‌شد | ساختن/ویرایش فقط `requireSuperAdmin` (مثلِ چرخشِ کلید)، `cleanUrl`: فقط http/https، بی user:pass؛ ردیفِ کهنهٔ ناروا fetch نمی‌شود — `claude/sec-api-web` | `api-web-hardening.test.js` › «SEC-API-03: …» (۳ آزمون؛ ۳/۳ سرخ پیش از اصلاح)؛ جهش: برداشتنِ `requireSuperAdmin` ⇒ سرخ؛ `platform.test.js` و `admin-panel.mjs` سبز | FIXED-PENDING-MERGE | مدیرِ ارشد همچنان می‌تواند نشانیِ داخلی بگذارد (اعتمادِ کامل؛ عمدی). ریدایرکت دنبال می‌شود | مدیرِ معمولی‌ای که برنامه می‌ساخت حالا «فقط مدیر ارشد» می‌بیند |
| SEC-API-04 | میانه (بالا اگر `NODE_ENV` در سرورِ واقعی نباشد) | `lib/otp.js` (`devCode`)، `config.js` | کدِ ورود/ثبت‌نام در خودِ پاسخ برمی‌گشت هر وقت `NODE_ENV ≠ production` و راهِ ارسال `log`؛ `NODE_ENV` ِ نیامده = development. سرورِ واقعی‌ای که پنلِ خانگی بی `NODE_ENV` بالا بیاورد و پیامک/ایمیلش تنظیم نباشد، کدِ هر شماره/ایمیلی را به هر کسی می‌داد ⇒ گرفتنِ حساب | `config.otp.devEcho`: فقط `NODE_ENV=test` یا `OTP_DEV_ECHO=true`، و در production هرگز؛ `system-health` ‏`devCodeEcho` را نشان می‌دهد — `claude/sec-api-web` | `api-web-hardening.test.js` › «SEC-API-04: …» (۲ آزمون؛ ۲/۲ سرخ پیش از اصلاح)؛ جهش: نادیده گرفتنِ `devEcho` ⇒ سرخ | FIXED-PENDING-MERGE | سرورِ بی `NODE_ENV` هنوز `API_SECRET`/`OTP_SECRET` را اجباری نمی‌کند (`config.validate` فقط در production) و `staff-codes`/`delivery` به رازِ ثابت برمی‌گردند — به `sec-auth` سپرده شد | در پنلِ مدیر ⇐ «سرویس‌ها»: `production` باید `true` باشد؛ وگرنه `NODE_ENV=production` در محیطِ همان راه‌انداز |
| SEC-API-05 | کم | `src/app.js` (سرآیندها، `/portal`)، `public/admin/app.js` و `public/portal/portal.js` (`openReceipt`) | پاسخ‌های `/api` CSP نداشتند (فقط رسانهٔ چت داشت)؛ پورتال (توکن در sessionStorage) هیچ CSP ای نداشت؛ دکمهٔ چاپِ رسید زیرِ CSP ِ به‌ارث‌رسیده با `onclick` ِ درون‌خطی کار نمی‌کرد | CSP ِ پیش‌فرضِ `/api` (جز درهای پمپ): ‎`default-src 'none'; frame-ancestors 'none'; sandbox`‎؛ CSP و Permissions-Policy ِ پنل برای پورتال؛ دستگیرهٔ چاپ از پنجرهٔ مادر — `claude/sec-api-web`. CSP ِ ‎`/api/pump/*`‎ و ‎`/api/admin/pump/*`‎ در #615 (نیازمندِ اجازهٔ صاحب‌کار) | `api-web-hardening.test.js` › «SEC-API-05: …» (۲/۲ سرخ پیش از اصلاح) و «کدِ پمپ: …» (درِ پمپ CSP ِ تازه نمی‌گیرد)؛ `portal-rep.mjs` ۷/۷ و `admin-panel.mjs` سبز | FIXED-PENDING-MERGE (پمپ: BLOCKED، #615) | — | — |
| SEC-API-06 | کم | `lib/sync.js` (`pushChanges`) | ‎`TABLES[collection]`‎ با ‎`constructor`/`__proto__`‎ تابع/شیءِ JS برمی‌گرداند و به‌جای نامِ جدول در SQL می‌نشست ⇒ ۵۰۰ و افتادنِ کلِ بسته (تزریق نه: متنِ ثابتِ JS) | `Object.hasOwn` — `claude/sec-api-web` | `api-web-hardening.test.js` › «SEC-API-06: …» (سرخ پیش از اصلاح) | FIXED-PENDING-MERGE | — | — |
| SEC-API-07 | — (نگهبان) | همهٔ `src/**/*.js` | تزریقِ SQL: همهٔ مقدارها پارامتر (`$n`)؛ هر نامِ جدول/ستونِ درون‌متن از فهرستِ ثابت (‎`TABLES`‎، ‎`ORDER_BY[tab]`‎، ‎`col` ِ دوحالته‌…)؛ `LIMIT` ِ درون‌متن همیشه `clamp`/`parseInt` | نگهبانِ ایستا: هیچ template literal ِ SQL ای `req.*`/`body.`/`query.x`/`params.x` را مستقیم ندارد — `claude/sec-api-web` | `api-web-hardening.test.js` › «SEC-API-07: …» (جهش: ‎`${req.query.sort}`‎ در `image-bank.js` ⇒ سرخ) | PASS | جست‌وجوی مدیر (`LIKE %q%`) نویسه‌های `%`/`_` را رمز نمی‌کند — فقط کارایی، فقط مدیر | — |
| SEC-API-08 | کم | `license/license-client.js` (پنلِ «اشتراک») | نام/ایمیلِ حساب، شناسهٔ مجوز و منطقهٔ زمانی خام در `innerHTML` (XSS ِ خود-کاربر) | `esc` — #589 | با همان PR؛ آزمونِ جدا ندارد (دادهٔ خودِ کاربر) | FIXED-PENDING-MERGE | — | — |
| SEC-API-09 | میانه | `routes/admin-sales.js` (‎POST /admin/payments‎) | بی کلیدِ یکتایی؛ دو کلیک ⇒ دو پرداخت؛ `receipt_no` = ‎`COUNT(*)+1`‎ بی قفل و بی UNIQUE ⇒ شمارهٔ رسیدِ تکراری | — (کارِ `claude/sec-data`: idempotency و پول) | — | NOT-TESTED | ثبتِ دوبارهٔ پرداخت در دفترِ فروشِ مدیر | — |
| SEC-API-10 | — | `.github/workflows/web-pages.yml` | خروجیِ وب = `git archive` ِ **کلِ** مخزن (‎`server/`، `docs/`…) و Pages هنوز «از شاخه» منتشر می‌شود | — (کارِ `claude/sec-artifacts`، SEC-ART-03/04) | — | NOT-TESTED | سورسِ بک‌اند روی سایتِ عمومی | Settings ⇒ Pages ⇒ Source = GitHub Actions (همان گامِ نوشته‌شده در ورک‌فلو) |
| SEC-API-11 | کم | `license/shop-sync.js` (`alignLedgerToAccount`)، `license/shop-ui.js` (`doLogout`) | پس از خروج، دفتر روی مرورگر می‌ماند (عمدی: «اطلاعات دکان روی این گوشی دست‌نخورده است»)؛ با ورودِ حسابِ دیگر دفترِ قبلی به `VAULT` می‌رود و قاطی نمی‌شود — ولی روی مرورگرِ مشترک خواندنی می‌ماند | — (تصمیمِ محصول: پاک کردن = خطرِ از دست رفتنِ کارِ آفلاین) | تعویضِ حساب: `web-session.mjs` (موجود) | PASS (تعویضِ حساب) / تصمیمِ باز (مرورگرِ مشترک) | دادهٔ حسابِ قبلی روی مرورگرِ مشترک | تصمیم: گزینهٔ «خروج و پاک کردنِ این دستگاه» بخواهید یا نه |
| SEC-API-12 | کم | `routes/me.js` | دو ‎`GET /heartbeat`‎ در یک روتر؛ دومی (‎`server_time`، `days_left`‎) هرگز نمی‌رسید چون `portal.heartbeatHandler` همیشه پاسخ می‌دهد — دستگیرهٔ سایه‌خورده یعنی هر اصلاحی در آن هرگز اجرا نمی‌شود. ‎`/pump/backups`‎ و ‎`/pump/events`‎ (دروازهٔ `requirePumpServices` سپس سوار شدن، زیرِ دو پیشوندِ کاربر/دستگاه) و دو ‎`GET /`‎ ِ `app.js` (اولی بیرون از دامنهٔ ویترین `next()`) تکرار نیستند، عمدی‌اند | دستگیرهٔ مرده برداشته شد (رفتار همان؛ همهٔ کلاینت‌ها و آزمون‌ها شکلِ پورتال را می‌خوانند) — `claude/sec-api-web` | `api-web-hardening.test.js` › «SEC-API-12: …» (نگهبانِ ایستا سرخ پیش از اصلاح)؛ `sync-client.test.js` و `login-contract.test.js` سبز | FIXED-PENDING-MERGE | — | — |

## ۳. فهرستِ بازرسی (هر ردهٔ حمله)

| رده | وضعیت | شاهد / دلیل |
|---|---|---|
| ورودی، شِما و سقفِ بدنه | PASS (+SEC-API-02) | JSON ِ همگانی ۲mb پس از سقفِ نرخ (درهای پمپ همان main: پیش از آن؛ ‎/state‎ ۴mb و ‎/report/‎ ۳۴mb پس از توکنِ دستگاه ⇐ #615)؛ بارگذاری‌های خام (`express.raw`) سقفِ خودشان را دارند و پس از احراز/کدِ کیو‌آر سوار می‌شوند؛ خطای بدنه پیامِ تمیزِ فارسی، بی متنِ Node (`bad_json`/`body_too_large`) |
| تزریقِ SQL | PASS | SEC-API-07 |
| تزریقِ فرمان | NOT-APPLICABLE | تنها `spawn` در `lib/backup.js` (`pg_dump`/`psql`) با آرایهٔ آرگومان (بی shell) و مقدارِ پیکربندی؛ هیچ ورودیِ درخواستی به آن نمی‌رسد. `eval`/`new Function`/`vm` در سرور نیست |
| پیمایشِ مسیر | PASS | نامِ هر فایلِ نوشتنی را سرور می‌سازد (پشتیبانِ حساب: ‎`app-stamp-random.ext`‎ زیرِ `safeTenant`)؛ انتشارها: برچسب از فهرستِ ثابت، نام با `NAME` و فقط دارایی‌های همان انتشار؛ `sendFile` فقط `index.html` با `root` |
| SSRF | FIXED (SEC-API-03) | دیگر نشانی‌های بیرونی (پیامک، ایمیلِ API، FCM، گوگل، GitHub) فقط از پیکربندیِ مدیرِ ارشد/محیط، با مهلت |
| بارگذاری | PASS | عکس: نوع از بایت‌های آغاز (`image-bytes.inspect`)، سقفِ ابعاد ‎۱۰۰۰۰‎ و ‎۴۰M‎ پیکسل از سرآیند (بی decode ⇒ بی بمبِ فشرده)، حذفِ متادیتا؛ رسانهٔ چت/پشتیبانی: SVG/XML/HTML رد، CSP ‏`sandbox` و `nosniff`، فایل در دیتابیس (نه زیرِ ریشهٔ وب)، هیچ اجرایی؛ پشتیبانِ حساب کدرِ ذخیره، باز نمی‌شود. هیچ zip/gzip ِ کاربر روی سرور باز نمی‌شود ⇒ بمبِ فشرده NOT-APPLICABLE |
| مجوزِ دانلود و لینکِ امضاشده | PASS (نمونه‌برداری) | رسیدِ پورتال فقط برای حسابِ خودِ آدم (`membership`)؛ فاکتورِ عمومی با توکنِ HMAC و ۴۰۴ ِ یکسان (`receipt-public`)؛ لینکِ پیگیری باطل‌شدنی (#507)؛ ماتریسِ کاملِ A/B کارِ `sec-tenant` |
| ریدایرکتِ باز | PASS | تنها ریدایرکتِ سرور ‎`/` ⇒ `/admin/`‎ (ثابت)؛ در وب هیچ `?next=`/`return` ای نیست |
| خطا (stack/مسیر/پرس‌وجو/راز) | PASS | `errorHandler`: ۵xx فقط «خطای داخلی سرور (کدِ پیگیری)» و جزئیات فقط در لاگ؛ ۴xx فقط پیامِ `ApiError`؛ پاسخِ `/config` و `/plans` از فهرستِ سفید (‎`PUBLIC_CONFIG_KEYS`‎). یادداشت: `/admin/backups` پوشهٔ پشتیبان را به **مدیر** نشان می‌دهد (عمدی) |
| debug/آزمون در production | FIXED (SEC-API-04) | هیچ مسیرِ ‎`/debug`‎ یا ‎`/test`‎ نیست؛ تنها رفتارِ وابسته به محیط `devCode` بود |
| سقفِ نرخ | PASS | سطلِ همگانیِ هر IP (۶۰۰/۱۵ دقیقه، IP ِ راست‌ترین `X-Forwarded-For` فقط با `TRUST_PROXY`)، سطلِ جدای ورود/کد/زدنِ کد/پیامک/همگام‌سازی/ارسالِ همگانی؛ حالا پیش از خواندنِ بدنه. ⚠️ سطل‌ها در حافظهٔ همان فرآیندند (یک فرآیند ⇒ درست؛ چند فرآیند ⇒ هر کدام جدا) |
| idempotency ِ پول و سفارش | جزئی ⇒ `sec-data` | سفارش و بازپرداختِ سفارش کلیدِ یکتایی دارند (`clientKeyOf`، `Idempotent-Replayed`)؛ پرداختِ مدیر نه (SEC-API-09) |
| مهلت‌ها | PASS | همهٔ fetch های بیرونی `AbortSignal.timeout` (۸ تا ۲۰ ثانیه؛ دارایی‌های انتشار ۱۰ دقیقه)، SMTP ‏۳۰ ثانیه؛ `headersTimeout` ‏۷۰ ثانیه |
| سرآیندها | PASS (+SEC-API-05) | همه: `nosniff`، ‎`X-Frame-Options: DENY`‎، ‎`Referrer-Policy: no-referrer`‎، COOP، HSTS پشتِ TLS؛ `/api` (جز درهای پمپ ⇐ #615): CSP ِ «صفحه نیست»؛ پنل، پورتال، ویترین، بازارچه، فاکتور: CSP بی `unsafe-inline` در `script-src` و `frame-ancestors 'none'` و Permissions-Policy. نسخهٔ وبِ دکان (GitHub Pages): CSP با meta و `connect-src` ِ بسته (F0-22)، ولی `script-src 'unsafe-inline'` (فاز ۲)؛ meta نمی‌تواند `frame-ancestors` بگذارد و Pages سرآیند نمی‌پذیرد ⇒ ضدِ قاب برای نسخهٔ وب NOT-APPLICABLE تا میزبانِ دیگر |
| CORS | PASS | فقط `CORS_ORIGIN` ِ محیط؛ ناشناس ⇒ پیش‌پرواز ۴۰۳؛ `*` هرگز با `Allow-Credentials` نیست (اصلاً فرستاده نمی‌شود) |
| CSRF | NOT-APPLICABLE | هیچ کوکی‌ای در سرور یا وب نیست (‎`Set-Cookie`/`res.cookie`/`document.cookie`‎ صفر)؛ همهٔ نشست‌ها ‎`Authorization: Bearer`‎ یا سرآیندِ صریح (‎`X-Store-Session`‎) که مرورگر خودش نمی‌فرستد |
| XSS (پنل، پورتال، ویترین، بازارچه، فاکتور) | PASS | فقط DOM API (`textContent`/`el()`)؛ تنها `document.write` رسیدِ HTML ِ سرور است که همهٔ فیلدهایش `escapeHtml` می‌شود |
| XSS (نسخهٔ وبِ دکان) | FIXED (SEC-API-01، SEC-API-08) | `license/orders-ui.js`، `shop-ui.js`، `support.js` از پیش `esc` داشتند |
| سرویس‌ورکر و دادهٔ خصوصی | PASS | `sw.js` فقط پوستهٔ ایستای همان مبدأ را کش می‌کند؛ API روی مبدأِ دیگر (‎`api.vill3n.top`‎) هرگز از SW رد نمی‌شود؛ دفتر در localStorage است، نه در کش |
| دادهٔ حسابِ قبلی پس از خروج | جزئی | SEC-API-11 |
| source map | PASS | هیچ ‎`.map`‎ یا ‎`sourceMappingURL`‎ در وب و `server/public` نیست (بی bundler) |
| فایلِ بک‌اند در خروجیِ وب | FAIL ⇒ `sec-artifacts` | SEC-API-10 |

## ۴. هماهنگی با کارهای دیگر

- **#534** (TEN-02، باز): `Cache-Control: private, no-cache` برای هر درخواستِ دارای اعتبارنامه زیرِ `/api`. این شاخه روی main است و
  همان لایه را دست نمی‌زند؛ میان‌افزارِ #534 پیش از سقفِ نرخ می‌نشیند و پارسرهای این PR پس از آن — دو تکهٔ جدا از `app.js`.
- `sec-auth`: سرورِ بی `NODE_ENV` رازها را اجباری نمی‌کند (SEC-API-04، ریسکِ باقی). `sec-data`: SEC-API-09. `sec-artifacts`: SEC-API-10.
- بیرون از دامنه (فقط ثبت): درهای خریدار (`/store/:slug/*`، `/market/*`، `/receipt/*`) فقط از نظرِ سرآیند دیده شدند؛ `verificationAvailable()` ِ ویترین
  بیرون از production «در دسترس» می‌گوید (بی `devCode` حالا بی‌خطر).

## ۵. آزمون‌های اجراشده (۱۴۰۵/۰۸/۰۹، محلی، PGlite ِ حافظه + کرومیوم ِ پلی‌رایت)

- `server/test/api-web-hardening.test.js`: ۱۵/۱۵ سبز؛ پیش از اصلاح ۱۳ سرخ + ۲ نگهبان/شاهد (SQL، شکلِ تپش). پنج جهش، هر پنج سرخ.
- `sync-client.test.js` + `login-contract.test.js` (مصرف‌کننده‌های تپش) با همان پرونده: ۶۷/۶۷.
- بازگشت (۲۱ پرونده، ۲۴۲ آزمون، همه سبز): admin-roles، discounts، login-hardening، market-web، platform، portal، pump-chat-media-relay،
  pump-services، pump-support-media، reauth، receipt-public، sales، shop-chat، station-reports، storefront، pump-device، sync-legacy-warn،
  admin-control-plane، admin-ops-providers، security-hardening. مرورگر: `admin-panel.mjs` (۰ ❌)، `portal-rep.mjs` ۷/۷.
- `server/test/web-xss-sync.mjs` (در `npm run test:web`): ۲۳/۲۳ سبز؛ پیش از اصلاح ۲۳/۲۳ سرخ؛ دو جهش سرخ.
  آزمون‌های وبِ دیگر با همان `index.html`: web-pos-draft ۷/۷، web-digits ۶/۶، web-locale ۶/۶، web-report-fresh ۶/۶،
  web-owner-export ۴/۴، web-session ۱۰/۱۰، web-debtors-ledger ۴/۵ و web-ui ۶/۷ — تنها گامِ سرخِ هر دو «axe-core پیدا نشد» است
  (axe روی این ماشین نصب نیست؛ BLOCKED محلی، CI نصبش می‌کند).
- NOT-TESTED: همزمانیِ واقعی روی PostgreSQL (PGlite تک‌اتصالی است)؛ مجموعهٔ کاملِ `npm test` (ماشینِ مشترک؛ CI می‌دواند).

## ۶. کارهای صاحب محصول

1. پنلِ مدیر ⇐ سلامت/سرویس‌ها: `production: true` و `devCodeEcho: false`. اگر `production: false` است، `NODE_ENV=production` را در محیطِ راه‌اندازِ پنلِ خانگی بگذارید (و `API_SECRET`/`OTP_SECRET` ِ ۲۴+ نویسه).
2. GitHub ⇐ Settings ⇐ Pages ⇐ Source = GitHub Actions (SEC-API-10؛ جزئیاتش در `sec-artifacts`).
3. تصمیم دربارهٔ «خروج و پاک کردنِ دادهٔ این مرورگر» برای مرورگرهای مشترک (SEC-API-11).
4. اگر مدیرِ غیرِارشدی «برنامه‌ها» را می‌ساخت، از این پس مدیرِ ارشد (SEC-API-03).
