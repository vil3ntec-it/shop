# امنیتِ خروجی‌ها و رازها — SEC-ART (برنامهٔ امنیت §۲.۲، §۵)

> شاخه‌ها: `claude/sec-artifacts` (دروازه‌ها، همین سند)، `claude/sec-artifacts-license` (#593)،
> `claude/sec-artifacts-signing` (#594، پیش‌نویس) و در ریپوی `server`: `claude/sec-artifacts-admin-key` (vil3ntec-it/server#112، پیش‌نویس).
> ⛔ در این کار هیچ مقدارِ رازی خوانده، چاپ یا در گزارش گذاشته نشد — فقط جا، نوع و (برای گواهی‌ها) اثرِ انگشتِ عمومی.

## ⛔ پیش از هر چیز: هر دو مخزن عمومی‌اند

`vil3ntec-it/shop` و `vil3ntec-it/server` در گیت‌هاب **public**اند (`private: false`). پس **هر چه یک بار در
تاریخچهٔ هر شاخه یا برچسبی آمده، لورفته حساب می‌شود** — برداشتن از HEAD یا حتی بازنویسیِ تاریخچه چیزی را که
کلون شده برنمی‌گرداند؛ فقط چرخش. سورسِ کاملِ بک‌اند، مهاجرت‌ها، آزمون‌ها و سندهای درونیِ امنیتی (مثلِ همین سند و
`SECURITY-AUDIT-fa.md`) هم عمومی‌اند — برخلافِ قاعدهٔ ۲ِ برنامه («سورسِ بک‌اند … هرگز در مخزن [عمومی] نباشد»).

**تصمیمِ صاحب محصول (OA-1):** خصوصی کردنِ مخزن‌ها. پیش از آن بدانید: ۱) به‌روزرسانیِ درونِ برنامه‌ها APK و نصاب را از
انتشارهای گیت‌هابِ **همین مخزن** می‌گیرد (`api.github.com` و `releases/download`) — مخزنِ خصوصی یعنی نصب‌های امروز دیگر به‌روز
نمی‌شوند، مگر انتشارها اول به جای عمومیِ دیگری (مخزنِ عمومیِ فقط-انتشار یا سرورِ خودتان) بروند؛ ۲) Pages روی مخزنِ خصوصی
پلنِ پولی می‌خواهد؛ ۳) دقیقه‌های Actions برای مخزنِ خصوصی سهمیه دارد.

## وضعیتِ SEC-ART

| شناسه | وضعیت | شاهد |
|---|---|---|
| SEC-ART-01 APK بی کلیدِ خصوصیِ مجوز | **PASS** (محتوای خروجی‌های منتشرشده) · دروازهٔ دائمی **FIXED-PENDING-MERGE** | دروازه روی خودِ فایل‌های انتشار، بازشده تا ته (dex، jar، gz): پایدار 3.2.337، آزمایشی 3.2.1159، مدیر 1.0.37 ⇒ ۰ یافته. گامِ «دروازهٔ محتوای APK» در `tohid-native.yml` و `tohid-admin.yml` |
| SEC-ART-02 بستهٔ ویندوز بی credentialِ سرور | **FIXED-PENDING-MERGE** | deb ِ لینوکسِ منتشرشده (همان jarها، ۲۶٬۸۵۰ فایل، ۵۷ بایگانیِ بازشده) ⇒ ۰ یافته. MSI، Setup.exe و DMG این‌جا باز نمی‌شوند ⇒ شاهد، اجرای سبزِ «دروازهٔ محتوای نصاب» در `tohid-desktop.yml` روی هر پنج نصاب |
| SEC-ART-03 خروجیِ وب بی سورسِ بک‌اند/تنظیمِ خصوصی | **FAIL** روی main و سایتِ زنده · اصلاح **FIXED-PENDING-MERGE** | `web-pages.yml` کلِ مخزن را (`git archive`) منتشر می‌کرد: دروازه روی همان خروجی ⇒ ۲٬۶۶۱ یافتهٔ سد (۲٬۰۱۴ سورسِ بک‌اند، کلیدِ امضا، رمزِ پیش‌فرضش…). با فهرستِ سفید ⇒ ۱۱۸ فایل، ۰ یافته. تا Pages روی «Deploy from a branch» است، سایتِ زنده همچنان کلِ main است (OA-2) |
| SEC-ART-04 پشتیبان و Git از وبِ عمومی گرفتنی نیست | **PASS** (سرور + مخزن) · سایتِ زنده **NOT-TESTED** | `server/test/static-exposure.test.js` ⇒ ۴/۴ (dotfile، پیمایشِ مسیر، فهرستِ پوشه؛ دو جهش سرخ)؛ هیچ پشتیبان/dump در تاریخچهٔ مخزن نیست؛ `.git` نه در `git archive` است نه در Pages. github.io از این محیط در دسترس نیست |
| SEC-ART-05 کلیدِ خصوصی در مخزن و artifact نیست | **FAIL** — رخداد · اصلاح‌ها **FIXED-PENDING-MERGE** | کلیدِ امضای APK و برنامهٔ ادمینِ پنل در مخزن‌های عمومی (پایین). کلیدِ خصوصیِ **مجوز** نه در مخزن است، نه در تاریخچه، نه در هیچ خروجی — ولی در `app_config` و پشتیبان‌ها (#593) |

## یافته‌ها

| ID | شدت | جا | ریشه | اصلاح | آزمون | وضعیت | ریسکِ مانده / کارِ صاحب محصول |
|---|---|---|---|---|---|---|---|
| ART-F1 | **بحرانی (رخداد)** | `android-native/app/tohid-release.jks` + رمزِ پیش‌فرض در دو `build.gradle.kts`، `KEYSTORE.md`، `OWNER-ACTIONS`؛ کپی در تاریخچهٔ ریپوی server | fallback «تا secret نیامده، کلیدِ مخزن» در ساخت و CI؛ مخزن عمومی | #594 (پیش‌نویس): بی پیش‌فرض، فایل پاک، بی secret قرمز | گواهیِ v2/v3 ِ APKهای انتشار = گواهیِ فایلِ مخزن (`E6F4DF44B118356E…`) | FAIL / FIXED-PENDING-MERGE | OA-3: secretها، «مرج کن»، `Integrity.kt` با دو اثرِ انگشت، چرخش با زنجیرهٔ v3 (`KEYSTORE.md`)، تصمیمِ بازنویسیِ تاریخچه. اندروید ۷–۸ پس از چرخش هم کلیدِ قدیم را می‌پذیرند |
| ART-F2 | **بالا (رخداد)** | ریپوی server: `homelab-panel/admin-android/app/villain-admin.jks` + رمزهای ثابت در `build.gradle.kts` | «این کلید راز نیست» — در مخزنِ عمومی | server#112 (پیش‌نویس) | `history-scan` روی HEAD ِ شاخه ⇒ بی کلید و رمز | FAIL / FIXED-PENDING-MERGE | OA-4: secretهای `VILLAIN_ADMIN_*`، «مرج کن»، چرخش |
| ART-F3 | بالا | `.github/workflows/web-pages.yml` | `git archive` ِ کلِ مخزن برای Pages (ریشه و `/beta/`) | فهرستِ سفید + «دروازهٔ محتوای سایت» (با آزمونِ خودِ دروازه) | دروازه روی خروجیِ قدیم ⇒ سرخ (۲٬۶۶۱)؛ روی خروجیِ تازه ⇒ سبز | FIXED-PENDING-MERGE | OA-2: Settings ⇒ Pages ⇒ Source = GitHub Actions؛ تا آن روز سایت = کلِ main |
| ART-F4 | بالا (تصمیم) | هر دو مخزن | مخزنِ عمومی | — | `gh api` ⇒ `private: false` | FAIL | OA-1 (بالا) |
| ART-F5 | متوسط | APKِ پایدار و آزمایشی، نصابِ کامپیوتر | `vars.TOHID_LICENSE_PUBLIC_KEY` خالی ⇒ کلیدِ عمومیِ مجوز سنجاق نیست و برنامه کلیدِ ذخیره‌شده روی دستگاه را می‌پذیرد (TOFU) | دروازه: اگر var پر است، کلید **باید** در APK/نصاب باشد؛ خالی ⇒ هشدار | در dexِ هر دو APK و jarهای deb فقط پیشوندِ SPKI است، نه کلید | FAIL (تا OA-5) | دست‌کاریِ دستگاه ⇒ مجوزِ آفلاینِ جعلی برای قفل‌های **محلی**؛ تصمیم‌های سرور دست نمی‌خورند. OA-5: مقدارِ `GET /api/license/public-key` در `vars.TOHID_LICENSE_PUBLIC_KEY` |
| ART-F6 | متوسط | `server/src/lib/license.js` | کلید در `app_config` ⇒ در هر پشتیبان؛ گم شدن ⇒ کلیدِ تازهٔ بی‌صدا | #593: `LICENSE_KEY_MODE=env`، فایلِ ‎0600‎، `LICENSE_REVOKED_KIDS`، `scripts/license-key.js` | `license-key-mode.test.js` ⇒ ۸/۸ (پیش از اصلاح ۴ سرخ) | FIXED-PENDING-MERGE | OA-6: جابه‌جاییِ سرورِ زنده (OWNER-ACTIONS بخشِ ۳)؛ پشتیبان‌های پیشین کلید را دارند ⇒ چرخش اگر بیرون رفته‌اند |
| ART-F7 | متوسط | `android-native/app/build.gradle.kts` | خواندنِ نشدنِ کلید ⇒ `SIGNING_SHA256=""` ⇒ `isGenuine()` همیشه true | #594: شکستِ ساخت | NOT-TESTED (Gradleِ اندروید این‌جا نمی‌سازد) | FIXED-PENDING-MERGE | — |
| ART-F8 | پایین | `.gitignore`، `server/.dockerignore` | الگوهای راز/پشتیبان نبود | `.env.*`، `*.pem`، `*.key`، `*.jks`، `*.p12`، `local.properties`، dumpها | — | FIXED-PENDING-MERGE | — |
| ART-F9 | بررسی | ریپوی server: ثابتِ `APP_BUILTIN_TOKEN` (۳۶ نویسه) در `homelab-panel/server/src/sitesync/index.js` (دست‌کم یک شاخه) | توکنِ ثابت در سورسِ عمومی | — | کاربردش خوانده نشد (نگهبانِ ابزار خواندنِ پیرامونش را بست) | NOT-TESTED | OA-7: اگر چیزی را باز می‌کند، لورفته ⇒ به env و چرخش |
| ART-F10 | PASS | لاگِ CI و سرور | — | — | هیچ ورک‌فلو secret را echo/printenv/`set -x` نمی‌کند (همه از env)؛ سرور: کدِ OTP در production لاگ نمی‌شود (#493)، `redactPath` توکن‌های مسیر را می‌پوشاند؛ `create-admin.js` رمزِ ساخته را فقط در ترمینالِ اپراتور می‌گوید (عمدی) | PASS | `desktop-shots.yml`: GITHUB_TOKEN در نشانیِ git (پوشیده با Actions) — مالِ §۱۴ |

### اسکنِ تاریخچه (همهٔ شاخه‌ها و برچسب‌ها؛ فقط نوع و مسیر)

- **shop** (۲٬۵۳۹ کامیت، ۴۶٬۳۰۵ blob): رازِ واقعی فقط ART-F1. بقیه ساختگی: رمز/توکنِ آزمون‌ها (`server/test/**`)، بردارهای
  مرجع (`core/test-vectors/**`)، PostgreSQLِ سرویسِ CI روی 127.0.0.1. `tohid-android.zip` ِ قدیم فقط سورس است. هیچ `.env`، dump،
  پشتیبان یا کلیدِ PEM هرگز commit نشده.
- **server** (۳۰۵ کامیت، ۵٬۴۴۸ blob): ART-F2، کپیِ کلیدِ ART-F1 در `homelab-panel/android/app/` و `homelab-panel/tohid-native/app/`
  (تاریخچه)، ART-F9؛ بی‌خطر: کلیدِ نمونهٔ مستنداتِ AWS در یک آزمون، رمزهای ساختگیِ آزمون، `push_auth` ِ SQL.

## چه ساخته شد (دائمی، نه یک‌بار)

- `tools/security/artifact-gate.mjs` — خودِ محتوای خروجی را باز می‌کند (zip/apk/jar/aar تا ۴ لایه، gz، tar) و سد می‌کند:
  کلیدِ PEM با بدنه، انبارِ کلید، کلیدِ خصوصی، `.env`، پشتیبان/dump، `.git` و فایل‌های درونیِ مخزن، سورسِ بک‌اند (مسیر و
  محتوا)، source map، توکن‌های شناخته، رمز در نشانی و فرمانِ امضا، پیوندِ نمادینِ بیرون‌زن، و هر بایگانیِ ناخوانا (7z، msi،
  dmg…) — چیزی که دیده نشود پاس نمی‌شود. خروجی فقط «قاعده، مسیر، خط». محلی:
  `node tools/security/artifact-gate.mjs --profile apk|desktop|web [--license-pin <SPKI>] <پوشه یا فایل>`.
- در CI پس از ساخت و پیش از upload/انتشار: APK (`tohid-native.yml`، هر `.apk` و `.apk.beta`)، برنامهٔ مدیر (`tohid-admin.yml`)،
  هر پنج نصابِ کامپیوتر (`tohid-desktop.yml`: پوشهٔ createDistributable برای Inno و DMG، `msiexec /a` برای MSI، `dpkg-deb -x` برای deb)
  و سایت (`web-pages.yml`، در PR هم؛ پیش از آن آزمونِ خودِ دروازه).
- `tools/security/history-scan.mjs` — همهٔ blobهای رسیدنی از هر شاخه/برچسب (`--history`)، هر مخزنی (`--repo`)؛ فهرستِ مجازِ
  دلیل‌دار. سدِ CIِ «راز در مخزن» مالِ `secret-scan.mjs` ِ §۱۴ (#585) است؛ این یکی برای تاریخچه و مخزن‌های دیگر.
- آزمون: `node --test tools/security/artifact-gate.test.mjs` ⇒ ۱۲/۱۲ با fixtureهای ساخته در زمانِ آزمون (کلید با `crypto`)؛
  جهش‌ها (بی بازکردنِ zip، PEMِ فقط-سرآیند، بی قاعدهٔ `.git`، بی source map، تاریخچهٔ نادیده، بی سنجشِ سنجاق) همه سرخ.

## کارهای صاحب محصول

| # | کار | اثر |
|---|---|---|
| OA-1 | تصمیم دربارهٔ عمومی ماندنِ دو مخزن (با جابه‌جاییِ انتشارها پیش از خصوصی کردن) | بدونِ آن سورس و تاریخچه عمومی می‌ماند |
| OA-2 | Settings ⇒ Pages ⇒ Source = **GitHub Actions** | سایت فقط فهرستِ سفید و پس از دروازه |
| OA-3 | secretهای `TOHID_KEYSTORE_*` ⇒ «مرج کن» ِ #594 ⇒ `Integrity.kt` با دو اثرِ انگشت ⇒ چرخشِ کلیدِ APK | پایانِ ART-F1 |
| OA-4 | secretهای `VILLAIN_ADMIN_*` ⇒ «مرج کن» ِ server#112 ⇒ چرخش | پایانِ ART-F2 |
| OA-5 | `vars.TOHID_LICENSE_PUBLIC_KEY` = کلیدِ عمومیِ امروزِ سرور | پایانِ ART-F5؛ دروازه از آن پس نبودنش را سد می‌کند |
| OA-6 | جابه‌جاییِ کلیدِ مجوز به فایل (#593، OWNER-ACTIONS بخشِ ۳) | پایانِ ART-F6 |
| OA-7 | بررسیِ `APP_BUILTIN_TOKEN` ِ ریپوی server | ART-F9 |
