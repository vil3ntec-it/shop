# راهنمای نگه‌دارنده — برای هر برنامه‌نویس یا سیزنِ تازهٔ هوشِ مصنوعی (۱۴۰۵/۰۸/۱۶)

این سند برای کسی است که از امروز، بی ما، کارِ «فروشگاه من / توحید» را ادامه می‌دهد. هر چه این‌جا آمده از خودِ مخزن
(کد، ورک‌فلو، `docs/spec/STATUS.md`) در `main` ِ `0c6992420` (#941) خوانده و سنجیده شده است. هر جا چیزی سنجیده نشده،
همان‌جا با «⚠️ نسنجیده» آمده و فهرستش در §۱۰ است. کارهای فقط-صاحب‌کار در [`OWNER-TODO-fa.md`](OWNER-TODO-fa.md) و
پاسخ به پرسش‌های بزرگ (ظرفیت، امنیت، از دست رفتنِ داده…) در [`QA-BIG-QUESTIONS-fa.md`](QA-BIG-QUESTIONS-fa.md) است.

## ۰. سه قاعده‌ای که هرگز نشکنید

1. **کدِ مشترک با پروژهٔ پمپ دست نمی‌خورد:** `server/src/lib/sync-v1.js`، ثابتِ `PUMP_TABLES` (در همان پرونده)،
   مسیرهای `/api/pump/*` (`server/src/routes/pump*.js`) و `server/src/routes/admin-pump.js`. برنامهٔ پمپ (ریپوی جدا) با
   همین‌ها همگام می‌شود. `tools/claude-ops/finish-pr.sh` با `PUMP-SHARED FILE TOUCHED` / `PUMP_TABLES TOUCHED` جلویش را
   می‌گیرد. تنها راهِ استثنا: اجازهٔ صریحِ صاحب‌کار (مثلاً PR ‏#615، تصمیمِ O-19).
2. **نسخهٔ پایدار فقط با صاحب‌کار.** هر مرج به `main` خودکار فقط «آزمایشی» (beta) و «پیش‌نمایش» می‌سازد؛ برچسب‌های
   پایدار (`tohid-native`، `tohid-desktop`، `tohid-web`) فقط با Run workflow ⇐ `channel = stable` جابه‌جا می‌شوند (§۵).
3. **قابلیتِ پرخطر پشتِ پرچمِ خاموش.** هر رفتارِ تازه‌ای که پول، دسترسی یا داده را عوض می‌کند پشتِ کلیدِ `live_config`
   (پیش‌فرض خاموش) یا تنظیمِ خاموشِ دستگاه ساخته می‌شود؛ هیچ داده‌ای پاک نمی‌شود و صاحبِ دکان هرگز قفل نمی‌شود
   (`docs/OWNER-DECISIONS-fa.md`، سرِ سند).

قاعده‌های کامل: `docs/RULES-fa.md` (۳۴۶۶ خط — **کامل نخوانید**؛ با `grep -n '^## \|^### ' docs/RULES-fa.md` سرخطِ بخشِ
همان کار را پیدا کنید). پیش از هر کامیت: `node docs/spec/tools/spec.mjs check` باید «سالم» بدهد.

---

## ۱. محصول در یک نگاه

| بخش | پوشه | چیست | وضع |
|---|---|---|---|
| برنامهٔ اندروید (گوشی و تبلت) | `android-native/` | Kotlin + Jetpack Compose؛ رابطِ نسخهٔ ۳ در `ui/v3`، صفحه‌های بزرگ در `ui/screens` | فعال؛ بستهٔ `ir.vil3ntec.tohid` |
| برنامهٔ کامپیوتر (ویندوز؛ مک و لینوکس هم ساخته می‌شود) | `desktop/` | **همان** سورسِ `android-native/app/src/main/java` روی Compose Desktop؛ نام‌های `android.*` با shim در `desktop/src/shim` | فعال؛ نصاب در CI روی `windows-latest` |
| برنامهٔ آیفون | `ios/` | همان سورسِ اندروید، کپی‌شده هنگامِ ساخت (`ios/shared`، Kotlin/Native + Compose Multiplatform) + پوستهٔ SwiftUI (`ios/iosApp`) | فقط شبیه‌ساز؛ بی امضا (§۶، `docs/IOS-PLAN-fa.md`) |
| منطقِ مشترک | `core/` | Kotlin Multiplatform (`commonMain`، `jvmMain` فقط برای JDK)؛ includeBuild در هر سه برنامه | منطقِ تازه **فقط** این‌جا |
| سرور | `server/` | Node 22 + Express روی PostgreSQL یا PGlite (`DATABASE_URL=pglite:<پوشه>`)؛ ۱۱۳ پروندهٔ مسیر، ۲۸۱ پروندهٔ `lib`، ۱۵۴ مهاجرت | فعال؛ `api.vill3n.top` (سرورِ خانگی پشتِ تونل) |
| ویترینِ وب (خریدِ مشتری) | `server/public/store` + `server/public/kit` | JS ِ بی‌چارچوب، CSP ِ سخت | فعال |
| بازارچه | `server/public/market` | فهرستِ دکان‌ها (`market.js`) | فعال |
| برنامهٔ خریدِ مشتری (اندروید؛ آیفون از همان کد در مرحلهٔ ۲) | `customer-android/` + `core/src/commonMain/.../core/shopper` | Compose؛ کارخواهِ تایپ‌شدهٔ همان درهای ویترین/بازارچه، سبدِ روی دستگاه، ثبت با کلیدِ یکتایی، پیگیری (O-04 «ب»، مرحلهٔ ۱) | فقط ساختِ debug در CI (کارِ «برنامهٔ خریدِ مشتری (گزارش)»)؛ بستهٔ `ir.vil3ntec.tohid.customer`؛ بی امضا و بی انتشار |
| پورتال، رسید، SDK | `server/public/portal`، `receipt`، `sdk` | حذفِ حساب، رسیدِ عمومی، API برای دیگران | فعال |
| پنلِ وبِ مدیرِ سکو | `server/public/admin` + برنامهٔ `admin-android/` | پرچم‌ها، انتشار، عملیات، پشتیبانی | نگه‌داری؛ پنلِ مرکزیِ تازهٔ مالک در ریپوی جدای `vil3ntec-it/server` (`homelab-panel`) |
| وبِ مدیریتِ دکان (PWA ِ قدیم) | `index.html`، `license/*`، `sw.js` | — | **منجمد** از ۱۴۰۵/۰۷/۲۷ (STATUS، یادداشت‌ها): نه حذف، نه قابلیتِ تازه؛ سقفِ اندازه‌اش در `tools/quality` |
| پوستهٔ WebView ِ قدیم | `android/` | — | فقط ساختِ دستی (`android.yml`) |
| مجوز و اشتراک | `server/src/lib/license.js`، `offline-codes.js`، `entitlement.js`، `subscriptions.js` | مجوزِ امضاشدهٔ ES256 برای هر دستگاه، کدِ آفلاین | کلیدِ خصوصی: O-18 در `OWNER-TODO` |
| پمپ (پروژهٔ دیگر) | ریپوی `pump-staion-yaqobi` | روی **همین** سرور با Sync v1 | فقط مسیرهای مشترکِ §۰ |

سندهای پایه: `docs/ARCHITECTURE-fa.md`، `docs/CLIENT-ARCHITECTURE-fa.md`، `docs/MODULES-fa.md`، `server/README.md`،
`android-native/README-fa.md`، `docs/DESKTOP-fa.md`، `docs/SYNC-v1-fa.md`، `docs/PUMP-fa.md`.

---

## ۲. نقشهٔ معماری

### ۲.۱ داده و همگام‌سازی

- **آفلاین‌اول:** هر دستگاه دفترِ کاملِ دکان را دارد (فایل‌های JSON و `SharedPreferences`؛ پایگاه‌دادهٔ SQL روی دستگاه
  نیست — `docs/IOS-PLAN-fa.md` §۱). فروش، برگشت، انبار و قرض بی اینترنت کار می‌کنند (SYN-01).
- **Sync v1 (پیش‌فرضِ همهٔ دکان‌ها، مشترک با پمپ):** صفِ op با `op_id` ِ یکتا (تکراری دوباره اعمال نمی‌شود، SYN-03)،
  push/pull با cursor. سرور: `server/src/lib/sync-v1.js` (⛔ دست نزنید) و پیرامونش که مالِ دکان است و دست‌زدنی:
  `routes/sync-v1.js`، `lib/sync-v1-own-newer.js` (پرچمِ `sync.pull_own_newer`)، `sync-v1-ack.js`، `sync-v1-live.js`
  (سوکتِ زنده، بستن هنگامِ لغوِ دسترسی)، `sync-v1-keep.js`. برنامه: `SyncV1Engine` در `android-native/.../sync`،
  منطقِ خالص در `core/.../core/sync` (مثلاً `PushSince.kt`). قاعده‌های نشکستنی: `docs/RULES-fa.md` سرخطِ «v3 · همگام‌سازی»
  و «Sync v1».
- **Sync v3 (تازه، خاموش):** `server/src/lib/sync-v3*.js` و `routes/sync-v3.js`؛ فقط با `SYNC_V3_ENABLED` (پیش‌فرض خاموش ⇒
  `/api/sync/v3/*` ۴۰۴) و فقط برای دکان. مبلغ `<نام>Minor` و مقدار `<نام>Milli` (عددِ صحیح). HLC، سیاستِ ادغامِ هر
  جدول، صندوقِ تعارض در سرور (صفحهٔ برنامه‌اش هنوز نیست، SYN-07). پلِ v3 ⇐ v1 پشتِ `shop.sync_v3_to_v1`. دروازهٔ push
  (`lib/sync-v3-gate.js`، `SYNC_V3_GATE` پیش‌فرض روشن) سقفِ push ِ هم‌زمان را پیش از گرفتنِ اتصال می‌گذارد. روشن کردنش
  برای دکانِ واقعی تصمیمِ D-17 است و منتظرِ کپیِ بی‌نامِ دادهٔ واقعی (O-15).
- **پول:** همیشه عددِ صحیح به کوچک‌ترین واحد (`*_minor`)؛ در `commonMain` گرد کردن فقط با `mathRound` (نه `roundToLong`
  — روی آیفون فرق می‌کند؛ RULES «هسته‌های مشترک»). هر منطقِ مشترکِ Kotlin و JS یک بردارِ مرجع دارد
  (`core/test-vectors/*`، `license/test-vectors/*`): یکی را بی دیگری و بردار عوض نکنید.

### ۲.۲ زنجیرهٔ هش و سابقه (audit)

- قالبِ `tohid-hashchain-v1`: `SHA-256(prevHash ‖ canonicalJson)`، genesis ِ ۶۴ صفر، سه پیاده‌سازی با بردارِ
  `license/test-vectors/hashchain-v1.json`. جدولِ `hash_chain` فقط‌افزودنی است (تریگر UPDATE/DELETE را رد می‌کند).
- سابقهٔ سرور: `lib/audit.js` (رمز/کد/توکن پیش از نوشتن پاک می‌شود). زنجیرهٔ سابقه پشتِ `hashchain.audit` (scope ِ
  `security`)؛ سنجش: `cd server && npm run verify-chain` (خروجِ ۰ سالم، ۲ شکسته).
- **زنجیرهٔ اسنادِ دستگاه (SEC-02)** پشتِ `shop.doc_chain`: منطق فقط در `core/src/commonMain/.../core/audit`
  (`HashChain`، `PostedDocument`، `DocChain`، `InvoiceVoid`)؛ سرور `lib/doc-chain.js` و `GET/POST /api/sync/v1/chain`
  (گزارش، هرگز ردِ فروش). سندِ «posted» ویرایش نمی‌شود؛ اصلاح فقط با سندِ معکوس (SEC-03).
- **doc-roles:** `lib/doc-roles.js` پشتِ `shop.sync.doc_roles` نقشِ کاربر را برای درجِ سندهای سابقه‌سوار در **Sync v3**
  (کارتِ هدیه، امتیاز، امانی، …) از `authorize` ِ `sync-v3.js` داوری می‌کند.

### ۲.۳ پرچم‌ها و دروازهٔ قابلیت

- جدولِ `live_config` با `scope` = شناسهٔ دکان یا `all` (یا scope ِ ویژه: `security`، `ops`، `release`). مقدارِ خودِ دکان
  بر `all` جلو می‌افتد و فقط `true` روشن است (`orders.flagOf`). حالتِ «غیرفعال کردنِ سرویس» ِ دکان (`shop_holds`) همهٔ این
  پرچم‌ها را برای همان دکان می‌بُرد.
- نوشتن: `PUT /api/admin/flags` (مدیرِ ارشد، دلیلِ اجباری) یا `PUT /api/admin/pump/live-config`؛ تاریخچه در
  `live_config_history` (`GET /api/admin/flags/history`). پنل: زبانهٔ «پرچم‌ها».
- **نقشهٔ کامل** (۱۱۱ ردیف: کلید، scope، پیش‌فرض، چه را باز می‌کند، PR): `docs/RULES-fa.md` سرخطِ «v3 · نقشهٔ پرچم‌ها».
  **هر پرچمِ تازه یک ردیف همان‌جا می‌خواهد.** دروازهٔ دوم کلیدِ اشتراک است (`features`؛ فهرستِ خالی = پلنِ کامل؛
  `lib/features.js`، `PAID_KEYS`).
- برنامه پرچم را از درِ `…/config` ِ همان بخش می‌خواند (مثلاً `GET /api/shop/ledger-merge/config`)؛ خاموش ⇒ رفتارِ پیشین.

### ۲.۴ تنظیمات: ردهٔ A/B/C و جست‌وجو

- `android-native/.../ui/screens/SettingsTiers.kt`: هر بخشِ `SettingsLayout.GROUPS` دقیقاً در یکی از سه رده است —
  **A** (`ALWAYS_ON`: فقط نمایش، پیش‌فرض روشن و پنهان، هیچ پول/دسترسی را عوض نمی‌کند)، **C** (`ESSENTIAL`: یک بار تنظیم)،
  **B** (بقیه: پولی/سیاستی/دسترسی/آزمایشی؛ پیش‌فرض خاموش). آزمون: `SettingsTiersTest`.
- تصمیمِ صاحب‌کار (۱۴۰۵/۰۸/۱۱): هیچ گزینه‌ای حذف نشود؛ «جست‌وجو + شش دسته + صفحهٔ قابلیت‌ها»؛ هیچ سرخطی دو بار نیاید
  (`tools/claude-ops/briefs/brief-settings-v2.md`).
- جست‌وجو: `ui/screens/SettingsSearchIndex.kt` (همهٔ بخش‌ها، هر سه زبان و دری؛ نرمال‌سازیِ `TextNormalizer`)؛ آزمون
  `SettingsHomeTest` (سرِ پرونده `SettingsSearchTest` نوشته که چنین پرونده‌ای نیست). هر نویسندهٔ تنظیم صفحهٔ v3 می‌خواهد (`SettingsPortTest.EXCEPTIONS` خالی است).

### ۲.۵ زبان‌ها: fa، en، ps و دری

- جدول‌های متن: `android-native/.../core/i18n/Str*.kt`؛ هر `Str*` تازه در `I18nTables.kt` ِ همان پوشه ثبت می‌شود.
  نگهبان‌ها: `I18nCoverageTest` (سقفِ جاافتاده ۰)، `ScreenTextTest` (صفحهٔ سه‌زبانه متنِ ثابتِ فارسی ندارد).
- **دری (`prs`) = متنِ فارسی + لایهٔ واژه:** `core/src/commonMain/.../core/text/DariText.kt` (`PHRASES`، `WORDS`، `KEEP`).
  متنِ فارسی هرگز برای دری عوض نمی‌شود. **هر مقدارِ درونِ متنِ فارسیِ `Str*` باید از `asIs(…)` بگذرد**
  (`android-native/.../core/i18n/I18n.kt`) تا نامِ کالا/مشتری دست نخورد؛ نگهبانِ سراسری `DariWordsTest`. واژهٔ تازه فقط با `DariTextTest`.
  ابزار: `python3 tools/claude-ops/asis-wrap.py <Str*.kt>`. ۱۵ واژهٔ نامطمئن فارسی می‌مانند تا صاحب‌کار بگوید.
- ویترینِ وب: واژه‌نامه‌های `server/public/kit/i18n/*.fa.json` و لایهٔ `kit/dari.js` (هرگز `prs.json` نسازید؛ لایه هرگز
  در بارِ نخست). واژه‌نامهٔ بارِ نخست زیرِ سقفِ `web-i18n.test.js`.
- تاریخِ ذخیره‌شده همیشه ASCII ‏`YYYY-MM-DD`؛ رقمِ فارسی فقط از فونت؛ تلفن `+93`.

### ۲.۶ ویترین: بودجهٔ بار و CSP

- بارِ نخست = `store/store.js` + `kit/kit.js` + `kit/boot.js` زیرِ سقفِ چرخ‌دنده‌ایِ `server/test/storefront-budget.js`
  (`FIRST_LOAD`، امروز ۷۰KB) و هرگز بالای سقفِ سند (`SPEC` ۷۵KB). هر چیزِ دیگر (چت، پرداخت، حساب…) دیربار با
  `K.part(name)` و واژه‌نامهٔ خودش.
- CSP ِ سخت: `script-src 'self'`، بی `unsafe-inline`/`unsafe-eval`، بی `blob:` (SEC-09، `storefront.test.js`).
  هیچ اسکریپتِ درون‌خطی و هیچ کتابخانهٔ بیرونی.

### ۲.۷ مهاجرت‌های پایگاه‌داده

- `server/migrations/NNN_<نام>.sql`، اجرا با `server/src/migrate.js` (`npm run migrate`)؛ **نامِ پرونده هویتِ مهاجرت است**
  ⇒ مهاجرتِ رسیده به main را هرگز تغییرِ نام ندهید (اجرای دوباره در تولید). اثرِ انگشت به پایانِ خط بی‌اعتناست.
- روی main آخرین **۲۱۴** است (`214_shop_trial_14_days.sql`)؛ ۲۱۲ خالی/سپرده مانده. پیش از گرفتنِ شماره: `ls server/migrations`
  روی `origin/main` **و** شاخه‌های باز (`git diff --name-only origin/main...<شاخه> -- server/migrations`).
- برگشت: `server/migrations/rollback/NNN_<نام>.down.sql` (امروز ۲۶ پرونده؛ برای هر مهاجرتِ تازه‌ای که جدول/ستون می‌سازد
  بنویسید). سهم‌بندیِ شماره‌ها: RULES سرخطِ «v3 · سهم‌بندیِ شماره‌های مهاجرت».
- سد: `node tools/quality/quality.mjs --check migrations` (شمارهٔ تکراری ⇒ CI ِ «کیفیتِ کد» قرمز). ارتقای واقعی را
  ورک‌فلوِ «تمرینِ ارتقا (staging)» روی دادهٔ واقعی‌نما می‌سنجد.

### ۲.۸ سقف‌های کیفیت (فقط پایین می‌آیند)

- `tools/quality/quality.mjs` با قاعده‌های `rules.mjs` و سقف‌های `tools/quality/baseline.json`: اندازهٔ فایل (۸۰۰ خط)،
  کمکی‌های تکراری، رشته‌های `'pump'`/`'shop'` در `server/src`، اندازهٔ `index.html` ِ منجمد، وابستگیِ پلتفرمی (راهِ آیفون)،
  ESLint ِ درستی و ES5 ِ هسته‌های `license`. شرح: `docs/QUALITY-fa.md`.
- پایین آوردن: `node tools/quality/quality.mjs --lower-baseline` و کامیتِ `baseline.json`. بالا بردن فقط دستی و با دلیل در PR.
  تعارضِ `baseline.json` هنگامِ ادغام: `python3 tools/claude-ops/baseline-max.py tools/quality/baseline.json`.
- در CI فقط «شمارهٔ مهاجرت» و «قاعده‌های دسترسیِ عامل‌ها» سد است؛ ولی `finish-pr.sh` سقف‌ها را با شبیه‌سازیِ squash سد
  می‌کند (`QUALITY FAIL`؛ با `BUMP=1` فقط اگر بیش‌شدن فقط در `android-native/` باشد سقف را بالا می‌برد).

### ۲.۹ مشخصات و STATUS

- سندِ اصلی `docs/spec/MASTER-SPEC.md` (SHA-256 ‏`dfa09307…`) در ۷۲ تکه (`docs/spec/00-*.md` تا `40-*.md`).
- `docs/spec/STATUS.md`: ۷۶۴ ردیف، هر شناسه یک ردیف با وضعیت (`IMPLEMENTED-VERIFIED`، `PARTIAL`، `MISSING`، `BROKEN`،
  `NOT-VERIFIED`، `BLOCKED-EXTERNAL`)، «تأیید: دستور و نتیجه»، «مانع» و «commit/PR». تصمیم‌ها (D-*) در `docs/spec/BLOCKERS.md`.
- `node docs/spec/tools/spec.mjs status` جمع‌ها و ساختار را از نو می‌سازد (ستون‌های دستی می‌مانند)؛ `check` باید «سالم» بدهد.
  `IMPLEMENTED-VERIFIED` بی دستورِ آزمون و commit/PR، و `BLOCKED-EXTERNAL` بی «مانع» رد می‌شود.
- **STATUS را کامل نخوانید** (۱۰۰۰+ خط، ردیف‌های بلند). جدولِ «خلاصه به تفکیکِ خانواده» (`sed -n 11,72p`) و برای ردیف‌ها:
  `grep -n '^| POS-37 ' docs/spec/STATUS.md` یا یک اسکریپتِ کوتاهِ python که ستون‌ها را با `|` می‌بُرد.
- ادغامِ ردیف‌به‌ردیفِ STATUS در تعارض: `python3 tools/claude-ops/batch-status.py`.

### ۲.۱۰ «تازه‌ها» (WHATS-NEW)

- `docs/WHATS-NEW-fa.md` تنها منبعِ برگهٔ «تازه‌ها» ی برنامه است (همراهِ برنامه ساخته می‌شود؛ `core/guide/WhatsNew.kt`).
- هر PR که کدِ دیدنیِ برنامه را عوض کند (`android-native/app/src/main`، `core/src/{commonMain,jvmMain,iosMain}`،
  `desktop/src/main`، `ios/iosApp`، `ios/shared/src/{commonMain,iosMain}`) دست‌کم یک سطرِ «- تازه:/- تغییر:/- رفع:/
  - برداشته: … ⇐ به‌جایش: …» به زبانِ کاربر می‌افزاید؛ بخشِ رسیده به main بسته است ⇒ بخشِ تازه بالا. سنجه:
  `node tools/whatsnew/check.mjs --base origin/main`؛ تغییرِ نادیدنی: `WHATSNEW_OK=<دلیل>`. آزمونِ قالب: `WhatsNewFileTest`.

---

## ۳. ساخت و آزمونِ محلی

پیش‌نیاز: JDK 17، Node 22، Gradle ِ سیستم (`gradle`)، و برای آزمونِ مرورگر Playwright
(`npm i -g playwright@1.56.1 && npx playwright install chromium`).

```bash
# ── سرور ─────────────────────────────────────────────
cd server && npm ci
TEST_DATABASE_URL=pglite:memory node --test --test-concurrency=1 test/<نام>.test.js   # یک آزمون، بی PostgreSQL
npm run test:pglite                       # همه روی PGlite (backup و pump-isolation رد می‌شوند: pg_dump می‌خواهند)
TEST_DATABASE_URL=postgres://<کاربر>:<رمز>@127.0.0.1:5432/shop_test npm test   # PostgreSQL ِ واقعی (مثلِ CI)
node -e "require('./src/app')"            # سرور بار می‌شود؟ (همان سنجشِ finish-pr)
DATABASE_URL=pglite:./data/pg NODE_ENV=development npm start   # سرورِ محلی بی PostgreSQL

# ── مرورگر (ویترین، پنل، وب) ─────────────────────────
node test/web-store.mjs                   # یا همهٔ ویترین: npm run test:web-store
npm run test:web-market ; npm run test:panel ; npm run test:e2e
SKIP_BROWSER_TESTS=1 …                    # فقط روی کامپیوترِ خودتان، اگر Playwright ندارید؛ «سبز» حساب نکنید

# ── مشخصات، کیفیت، قراردادِ API ──────────────────────
node docs/spec/tools/spec.mjs check       # «سالم»
node tools/quality/quality.mjs ; node tools/quality/quality.mjs --check migrations
node tools/api-contract/generate.cjs      # docs/api/routes.json را از نو؛ --check فقط می‌سنجد

# ── Kotlin: هسته و کامپیوتر ───────────────────────────
gradle -p core jvmTest --no-daemon
gradle -p desktop test --no-daemon -Pdev=true -Pkotlin.compiler.execution.strategy=in-process
cd android-native && gradle --no-daemon testReleaseUnitTest assembleRelease   # همان CI؛ اینترنت (dl.google.com) می‌خواهد

# ── برنامهٔ خریدِ مشتری ───────────────────────────────
gradle -p core jvmTest --tests 'ir.vil3ntec.tohid.core.shopper.*'   # کارخواه، سبد، ثبت، پیگیری، نوشته‌ها (بی سرور)
(cd server && TEST_DATABASE_URL=pglite:memory node test/shopper-live.mjs)  # همان کارخواه روی سرورِ واقعی (ShopperLiveTest)
cd customer-android && gradle --no-daemon testDebugUnitTest assembleDebug  # اینترنت (dl.google.com) می‌خواهد؛ فقط debug

# ── آیفون (لینوکس، فقط klib) ─────────────────────────
desktop/gradlew -p ios/shared -Pios=true compileKotlinIosSimulatorArm64   # پیوند و Xcode فقط روی macOS (CI)
```

**نکته‌های محیطِ بی‌اینترنت (کانتینرِ سیزن‌های Claude):**

- `dl.google.com` در شبکهٔ سیزن‌ها ۴۰۳ می‌داد (HANDOFF، چند سیزن) ⇒ برنامهٔ اندروید این‌جا ساخته و آزموده نمی‌شود؛ فقط CI.
  هسته و کامپیوتر با init-script ِ بی‌اینترنت آزموده می‌شوند:
  ```bash
  OPS=$PWD/tools/claude-ops
  flock $S/gradle.lock gradle -p core jvmTest --offline --no-daemon --init-script $OPS/v3f/offline.init.gradle
  flock $S/gradle.lock gradle -p desktop test --offline --no-daemon -Pdev=true \
        -Pkotlin.compiler.execution.strategy=in-process --init-script $OPS/v3f/offline.init.gradle
  bash $OPS/desk-check.sh <worktree>      # هر دو، با خلاصهٔ شکست‌ها (S را بدهید)
  ```
  init-script گروه‌های `androidx.*` را بیرون می‌گذارد و Compose runtime ‏۱.۷.۳ را از کشِ `/root/.gradle` و stubهای
  `v3f/stubs` می‌گیرد ⇒ **فقط روی ماشینی کار می‌کند که همان کش را دارد.** برنامه‌نویسِ بااینترنت آن را لازم ندارد.
  (سنجیده در همین سیزن: `DariTextTest` ‏۱۶/۱۶ با همین فرمان، ۱ دقیقه و ۴۴ ثانیه.)
- `flock` روی یک پروندهٔ قفل فقط برای این است که چند عامل/پنجره هم‌زمان Gradle نزنند (حافظهٔ ~۱۵ گیگ)؛ `--no-daemon` و
  کامپایلِ درون‌فرآیندیِ Kotlin هم برای همین است.
- **۱۳ آزمونِ `ui.theme` ِ کامپیوتر فقط محلی سرخ‌اند** (`ConnectorKt`، نبودِ `androidx.collection` زیرِ init-script) و در CI سبز؛
  **۵ آزمونِ وابسته به `pg_dump` روی PGlite همیشه سرخ‌اند** (`tools/claude-ops/README-fa.md` §۴). سرخِ دیگر یعنی مشکلِ واقعی.
- دیسکِ کانتینر تنگ است (~۲ گیگ آزاد)؛ پوشه‌های `build/` و worktree ِ کارهای مرج‌شده را پاک کنید.
- PostgreSQL ِ محلیِ کانتینرهای پیشین روی پورتِ 55432 بود (README ِ claude-ops) — ⚠️ در هر کانتینر نسنجیده.

**آزمون‌های Kotlin که بیشتر می‌شکنند:** نامِ آزمونِ backtick بی `: . ; [ ] / < > \` (`python3 tools/claude-ops/kt-names.py <wt>`)؛
وابستگیِ پلتفرم فقط در `*Android.kt`؛ پروندهٔ تازهٔ اندروید برای آیفون shim می‌گیرد، نه سطرِ `ios/shared/ios-exclude.txt`
(امروز ۱۰ سطر)؛ shim ِ هم‌نامِ رده‌های Kotlin/Native (`java.lang.Double`…) باید `internal` باشد.

---

## ۴. نقشهٔ CI (`.github/workflows/`)

| ورک‌فلو (نامِ دیده‌شده) | کی | چه می‌سنجد | سد یا گزارش |
|---|---|---|---|
| `server.yml` «سرور» | push/PR | کارهای `pg` (PostgreSQL)، `pglite`، `electron` (سرور زیرِ Electron)، `browser` (پنل، وب، ویترین، بازارچه)، `e2e` (سناریوهای سرتاسری)؛ کارِ جمع «آزمون‌های سرور» فقط وقتی همه سبزند سبز است | **سد** |
| `quality.yml` «کیفیتِ کد» | هر PR و push به main | سقف‌ها، ESLint، «تازه‌ها»، پوشش ⇐ گزارش؛ **شمارهٔ مهاجرت** و **قاعده‌های دسترسیِ عامل‌ها** (`tools/ai-access`) | دو گامِ سد، بقیه گزارش |
| `spec.yml` «مشخصاتِ جامع» | تغییرِ `docs/spec/**` | `spec.mjs check` | **سد** |
| `tohid-native.yml` «توحید بومی» | تغییرِ `android-native/**`، `core/**`، `docs/WHATS-NEW-fa.md` (PR)؛ `customer-android/**` فقط در PR | آزمون‌های واحد + `:core:jvmTest`، APK، دروازه‌های APK و `artifact-gate`، AAB ِ Play (`tools/play-check.py`)؛ روی main انتشارِ beta. کارِ جدای «برنامهٔ خریدِ مشتری (گزارش)»: `testDebugUnitTest assembleDebug` ِ `customer-android` و `server/test/shopper-live.mjs` | **سد** (ساخت)؛ کارِ مشتری **گزارش** (`continue-on-error`) |
| `tohid-native-preview.yml` | push به main + PR | APK ِ بستهٔ `ir.vil3ntec.tohid.preview` ⇐ برچسبِ `tohid-native-preview` | **سد** |
| `tohid-desktop.yml` «فروشگاه من — کامپیوتر» | `desktop/**`، `android-native/app/src/**`، `core/**`، `install.ps1` | کامپایل و آزمون، دیسکِ ویندوز، نصاب‌ها، انتشارِ beta | **سد** |
| `tohid-admin.yml` «توحید مدیریت» | `admin-android` | ساخت و انتشار ⇐ برچسبِ `tohid-admin` | سد |
| `ios-spike.yml` | `core/**`، `ios/**` | `core-native` (linuxX64)، `ios` (core روی شبیه‌ساز)، «رابطِ مشترکِ آیفون — klib»، «برنامهٔ آیفون — Xcode» با آزمونِ سرتاسریِ ۱۱ گام (مصنوعِ `ios-e2e-shots`) | سه کار `continue-on-error` (**گزارش**) |
| `codeql.yml` «CodeQL (گزارش)» | `server/src/**`، `server/public/**`، `license/**`، ورک‌فلوها؛ هفتگی | تحلیلِ JS | گزارش |
| `security.yml` «امنیت — زنجیرهٔ تأمین» | push/PR/هفتگی | اسکنِ secret، `npm audit` (critical ⇐ سد)، actionlint (گزارش) | بخشی سد |
| `perf.yml` «سنجشِ کارایی» | PR ِ مسیرهای مرتبط، هفتگی | بنچِ سرور و بانکِ تصاویر روی هر دو پایگاه | ⚠️ سد/گزارش بودنِ هر گام نسنجیده |
| `staging-rehearsal.yml` «تمرینِ ارتقا» | `server/migrations/**`، `migrate.js`، `db.js` | نسخهٔ پیش ⇐ تازه روی دادهٔ واقعی‌نما | سد |
| `shop-migrate.yml` | `tools/shop-migrate/**` و چند پروندهٔ مدل | ابزارِ مهاجرتِ داده | سد |
| `desktop-shots.yml` | PR ِ رابط | عکس از همهٔ بخش‌ها ⇐ شاخهٔ `desktop-screenshots` | مصنوع |
| `web-pages.yml` «وب — GitHub Pages» | `index.html`، `license/**`… | سایت (پایدار + `/beta/`) | سد؛ انتشار فقط پس از O-17 |
| `android.yml`، `android-release.yml` | فقط دستی | پوستهٔ WebView ِ قدیم | — |

⚠️ کدام چک‌ها در «branch protection» ِ main اجباری‌اند از مخزن دیده نمی‌شود (تنظیمِ GitHub). قاعدهٔ کار: squash-merge فقط
وقتی همهٔ چک‌ها سبزند؛ چک‌های «(گزارش)» ِ آیفون سد نیستند.

**سرخ‌های رایج و درمان:**

| نشانه | ریشه | درمان |
|---|---|---|
| `paid-routes-entitlement` / `paid-routes-public-entitlement` (SUB-10) | مسیرِ نوشتنیِ تازه در `/api/shop` یا ویترین دسته ندارد | مسیر را در **هر دو** پرونده در یکی از دسته‌های PAID/JOIN/STAFF/FREE بگذارید (FREE با دلیل)؛ ویترینِ پولی ⇒ ۴۰۴ `store_not_found` با `sf.open` |
| `shop-route-inventory`، `route-authz-inventory` | مسیرِ تازه بی ردیف؛ برای `:id` ردیفِ واقعیِ کاشته‌شده لازم است | ردیف و دادهٔ کاشت را در همان آزمون بیفزایید |
| `tenant-matrix` | مسیرِ تازهٔ `/api` در هیچ خانواده‌ای نیست، یا درِ مدیر بی سابقه | خانواده‌اش را نام ببرید؛ درِ GET ِ موضوع‌دارِ مدیر سابقه بنویسد (TEN-04) |
| `api-contract.test.js` | `docs/api/routes.json` کهنه است | `node tools/api-contract/generate.cjs` و کامیتِ `docs/api/routes.json` |
| CodeQL `js/missing-rate-limiting` | مسیرِ بی سقف | `router.use(rateLimit(…))` **پیش از** احراز؛ model pack ِ `.github/codeql/extensions/shop-javascript` همین را می‌شناسد (`node --test tools/security/codeql-models.test.mjs`). ساکت کردن ممنوع |
| `storefront-budget` / `web-i18n` | بارِ نخستِ ویترین یا واژه‌نامه بزرگ شد | کد را دیربار کنید (`K.part`)؛ سقف را بالا نبرید |
| آیفون klib سرخ (گزارش) | پروندهٔ تازهٔ اندروید به JVM تکیه دارد | shim در `ios/shared/src/iosMain`، نه exclude؛ بعد `CoreFlowsE2EIosTest` اگر برچسبِ صفحه‌های ورود/صندوق/کالا/«بیشتر»/زبان عوض شد |
| «زیرِ Electron» گیر | پیش‌تر ۴۵ دقیقه گیر می‌کرد (رفع در #838: نگهبانِ پرونده) | دوباره اجرا فقط با اجازه؛ ریشه را در لاگ ببینید |
| آزمونِ «دست‌خورده» گاه‌به‌گاه سبز/سرخ | بلیتِ دست‌خورده با پسوندِ ثابت یک بار در ۴۰۹۶ همان توکنِ درست است | فقط `h.tampered(token)` ِ `server/test/helpers.js` |
| `WhatsNewFileTest` | قالبِ `docs/WHATS-NEW-fa.md` | §۲.۱۰ |

---

## ۵. انتشار

| کانال | چه می‌سازد | برچسب | کی |
|---|---|---|---|
| اندروید، آزمایشی | `MyShop-Android.apk.beta` و نسخهٔ بازار | `tohid-native-beta` | خودکار با هر push به main که اندروید/core را عوض کند |
| اندروید، **پایدار** | همان، برای همهٔ دکان‌ها | `tohid-native` | **فقط صاحب‌کار:** Actions ⇐ «توحید بومی» ⇐ Run workflow ⇐ `main` ⇐ `channel = stable` |
| پیش‌نمایشِ گوشیِ صاحب‌کار | بستهٔ جدای `ir.vil3ntec.tohid.preview` (`tohid-preview.zip`) | `tohid-native-preview` | خودکار با هر push به main؛ اگر صاحب‌کار گفت «نسخه تازه نشد» اول همین ورک‌فلو |
| کامپیوتر، آزمایشی / پایدار | `MyShop-Windows-Setup.exe`، `…-Offline.exe` و مک/لینوکس | `tohid-desktop-beta` / `tohid-desktop` | همان قاعده: پایدار با «فروشگاه من — کامپیوتر» ⇐ `channel = stable` |
| وب | `/shop/beta/` و ریشهٔ سایت | `tohid-web` | فقط پس از تنظیمِ Pages = GitHub Actions (O-17)؛ پایدار با «وب — GitHub Pages» ⇐ `channel = stable` |
| برنامهٔ مدیر | APK ِ `admin-android` | `tohid-admin` | با push به main |
| Google Play | `MyShop-Play.aab` (مصنوعِ هر اجرا) | — | دستی در Play Console؛ با کلیدِ بارگذاریِ تازه روی کامپیوترِ صاحب‌کار (`docs/GOOGLE-PLAY-fa.md` §۳) |

- **کدام دکان آزمایشی می‌گیرد:** `live_config` با scope ِ `release` و کلیدهای `beta.shops` / `beta.devices` (پنل:
  `/api/admin/release/beta`). ⚠️ پیش از پایدار، سرور به‌روز شود (`docs/spec/OWNER-ACTIONS-fa.md` §۵).
- **secretها و متغیرهای GitHub:** امضای APK با `TOHID_KEYSTORE_B64`، `TOHID_KEYSTORE_PASSWORD`، `TOHID_KEY_ALIAS`،
  `TOHID_KEY_PASSWORD` (بی secret ⇒ هشدار و **کلیدِ داخلِ مخزن**، که لو رفته است — D-02)؛ امضای فهرستِ به‌روزرسانی
  `TOHID_UPDATE_SIGNING_KEY` (اگر متغیرِ `TOHID_UPDATE_PUBLIC_KEY` سنجاق شده و secret نباشد ⇒ خطا)؛ Firebase
  `GOOGLE_SERVICES_JSON` (اختیاری؛ نبودش ساخت را نمی‌شکند)؛ متغیرهای `TOHID_API_BASE`، `TOHID_LICENSE_PUBLIC_KEY`
  (سنجاقِ کلیدِ عمومیِ مجوز؛ `artifact-gate` آن را در APK می‌سنجد). پوستهٔ قدیم: `ANDROID_KEYSTORE_BASE64` و همتاها.
- نسخه: `versionBase` در `android-native/app/build.gradle.kts` (امروز ۳٫۲) و `-PbuildNumber` = شمارهٔ اجرای CI؛
  نسخهٔ کاری در کامیت‌ها `(2.14.5)`.

---

## ۶. ادامهٔ کار به تفکیکِ خانواده

شمارها از جدولِ «خلاصه به تفکیکِ خانواده» ِ `docs/spec/STATUS.md` روی main ِ امروز (V = IMPLEMENTED-VERIFIED،
P = PARTIAL، M = MISSING، NV = NOT-VERIFIED، B = BLOCKED-EXTERNAL). **کل: ۷۶۴ ردیف — V ۳۹۴، P ۲۹۹، M ۳۳، NV ۱۲، B ۲۶،
BROKEN ۰ ⇒ حدودِ ۷۱٪** (V = ۱، P = ۰٫۵). PRهای باز (§۶.۲) هنوز در این شمارها نیستند.

| خانواده | شمار | V / P / M / NV / B | بزرگ‌ترین کمبود | کجای کد |
|---|---|---|---|---|
| POS صندوق | ۴۲ | ۱۹ / ۲۱ / ۱ / ۱ / ۰ | POS-37 مالیات (پشتِ D-11)؛ POS-42 سرعتِ اسکن روی دستگاهِ واقعی (NV)؛ ۱۷ PARTIAL ِ P0/P1 (POS-01…06، 10، 12، 15، 18…) | `core/.../core/{pos,sale}`، `ui/v3/sales`، `ui/screens`؛ سرور `lib/till-guard.js`، `finalized-sales.js` |
| INV انبار | ۳۹ | ۲۷ / ۱۲ / ۰ / ۰ / ۰ | INV-01، 03، 09، 10، 11، 13، 16 (شمارش حینِ فروش، پیشنهادِ خرید) | `core/.../core/stock`، `ui/v3/stock`؛ `lib/stock*.js`، `stock-transfers.js` |
| PAY پرداخت | ۲۴ | ۱۱ / ۱۲ / ۱ / ۰ / ۰ | PAY-22 (QR ِ ملیِ APS/AfPay، مشخصاتِ بیرونی)؛ PAY-06/08/21 پشتِ D-08 (حساب‌پیِ واقعی)؛ PAY-07 | `core/.../core/{pay,gateway,safe}`، `android-native/.../pay`؛ `lib/payment-*.js`، `hesabpay-*.js`، `safe-ledger.js`؛ `docs/PAYMENTS-HESABPAY-fa.md` |
| PUR خرید | ۱۴ | ۶ / ۶ / ۲ / ۰ / ۰ | PUR-11 ارزِ دیگر (⛔ D-15: تا دکانِ واقعی نخواهد نسازید؛ #784 پیش‌نویس)؛ PUR-12 عکسِ فاکتور (همراهِ AI-10) | `core/.../core/purchase`؛ `lib/purchase-order*.js`؛ PR ‏#950 باز |
| RPT گزارش | ۱۸ | ۸ / ۱۰ / ۰ / ۰ / ۰ | RPT-10 فاکتورِ قانونی (D-11 + D-25)؛ RPT-03/04/11/13/14/17 | `core/.../core/report`، `ui/screens/ReportFresh*.kt`، `ui/v3/analytics`؛ `lib/report-core.js`، `finance-reports.js`، `owner-report.js` |
| STF کارمند | ۱۱ | ۳ / ۸ / ۰ / ۰ / ۰ | نقش‌های بیشتر (STF-01)، سه‌حالتهٔ مجوز و برگهٔ تأیید (STF-02/03)، PIN ِ شخصی (STF-04)؛ S144 (D-26 گرفته، ساخته نشده) | `core/.../core/{staff,perm}`؛ `lib/staff-*.js`، `permissions.js`، `perm-core.js` |
| DLV پیک و تحویل | ۲۰ | ۱۴ / ۶ / ۰ / ۰ / ۰ | DLV-01، 02، 05، 06، 16 | `core/.../core/{delivery,courier}`، `ui/courier`؛ `lib/delivery-*.js`، `courier-*.js` |
| ORD سفارش | ۲۳ | ۱۸ / ۳ / ۱ / ۰ / ۱ | ORD-20 کسرِ قطعیِ موجودی هنگامِ بسته‌بندی/تحویل؛ ORD-02 زنگِ پس‌زمینه (O-01) | `core/.../core/orders`، `ui/OrdersLive.kt`، `ui/v3/online`؛ `lib/order-*.js`، `orders.js` |
| PCK جمع‌آوری | ۱۴ | ۱۱ / ۳ / ۰ / ۰ / ۰ | PCK-05، 07، 11 | `core/.../core/pick`؛ `lib/pick-*.js`، `order-picking.js` |
| CHK ثبتِ سفارشِ ویترین | ۲۴ | ۱۲ / ۱۱ / ۰ / ۰ / ۱ | CHK-02 کدِ ورودِ واقعی (D-09)؛ CHK-15/16 (PR ‏#955 باز)؛ CHK-01، 03، 08، 09، 10، 14، 18، 20 | `server/public/store/{checkout,pay-step,store}.js`؛ `lib/storefront-*.js`، `store-cart.js` |
| WEB ویترین | ۲۵ | ۱۲ / ۱۱ / ۰ / ۰ / ۲ | WEB-21 تلگرام و WEB-22 واتس‌اپ (حسابِ بیرونی، D-09)؛ WEB-09، 15، 16، 17 | `server/public/{store,kit}`؛ `lib/storefront*.js`، `store-seo.js` |
| MKT بازاریابی | ۱۲ | ۳ / ۲ / ۶ / ۰ / ۱ | MKT-03…08 ساخته نشده‌اند (سبدِ رهاشده، برگرداندنِ مشتری، گروه‌بندی، خریدِ دوباره، معرفی، سفارشِ تکرارشونده)؛ O-04 «ب» (۱۴۰۵/۰۸/۱۶): پایهٔ برنامهٔ خرید (مرحلهٔ ۱، `customer-android`) هست، این ردیف‌ها روی حساب و Push ِ مرحلهٔ ۲ | `core/.../core/{marketing,market}`؛ `lib/market-*.js`، `campaigns.js`، `storefront-promos.js`؛ `server/public/market` |
| CHAT گفت‌وگو | ۱۵ | ۴ / ۱۱ / ۰ / ۰ / ۰ | همه PARTIAL ِ P2: رابطِ نسخهٔ ۳ و شاهدِ CI | `core/.../core/chat`؛ `lib/shop-chat*.js`، `chat-*.js`؛ `store/chat.js` |
| HW سخت‌افزار | ۱۳ | ۲ / ۱۱ / ۰ / ۰ / ۰ | چاپِ حرارتی، صفِ چاپ، کشو (HW-01…03)، ترازو و دستگاه‌ها روی دستگاهِ واقعی؛ HW-08؛ PR ‏#952 باز | `core/.../core/{hw,terminal,nfc}`، `android-native/.../{hw,print,scan}`؛ `docs/RULES-fa.md` «چاپگر و بارکدخوان» |
| SEC امنیت | ۱۱ | ۶ / ۴ / ۱ / ۰ / ۰ | SEC-08 کلیدِ امضای تازه (D-02، کارِ صاحب‌کار)؛ SEC-01 رمزِ دفترِ دستگاه پیش‌فرض خاموش؛ SEC-10 حذفِ دادهٔ مشتری پشتِ پرچم | `core/.../core/{audit,keys}`؛ `lib/{hash-chain,doc-chain,totp,reauth}.js`؛ `docs/security/*.md` |
| OPS عملیات | ۱۴ | ۶ / ۸ / ۰ / ۰ / ۰ | OPS-01 هشدارِ بحرانی (پرچمِ `ops.critical_alerts`)، OPS-02 تمرینِ ماهانهٔ بازیابی، OPS-06 متنِ راهنما (O-14)؛ PR ‏#953 باز | `core/.../core/{ops,guide}`؛ `lib/ops-*.js`، `system-health.js`، `restore-drill.js` |
| AI هوشِ مصنوعی | ۱۲ | ۲ / ۳ / ۷ / ۰ / ۰ | AI-01/02/03/05 پردازشِ عکس (D-16: سرویسِ پولیِ بیرونی خاموش)؛ AI-09/10/12 تصمیمِ صاحب‌کار (مدل) | `core/.../core/image`؛ `lib/image-*.js`، `visual-search.js` |
| آیفون | — (شناسهٔ STATUS ندارد) | گامِ ۱۰ از طرحِ `docs/IOS-PLAN-fa.md` | امضا، حسابِ اپل، APNs و App Store (O-02، §۰.۶)؛ از فهرستِ کارهای اندروید فقط تشخیصِ گفتار مانده؛ چاپِ بلوتوثِ کلاسیک روی iOS شدنی نیست | `ios/shared/src/iosMain` (shim و `*Ios.kt`)، `ios/iosApp`، `ios/e2e/backend.mjs`؛ CI ِ «برنامهٔ آیفون — Xcode» |

### ۶.۱ چطور یک کمبود را ببندید (الگوی همهٔ دورهای «کمبودهای PARTIAL»)

1. ردیف را بخوانید (فقط همان: `grep -n '^| DLV-05 ' docs/spec/STATUS.md`) و بخشِ سند (`grep -rn 'DLV-05' docs/spec/06-DLV.md`).
   ستونِ «تأیید» می‌گوید چه ساخته شده و چه مانده؛ ستونِ «مانع» اگر D-* دارد، اول `docs/spec/BLOCKERS.md` و
   `docs/OWNER-DECISIONS-fa.md` (گروهِ «الف» گرفته‌شده‌اند و ساختنی‌اند؛ «ب» و «ج» منتظرِ صاحب‌کارند).
2. منطق را در `core/src/commonMain` با آزمونِ `commonTest` (و بردارِ مرجع اگر JS هم دارد)، سرور را با آزمونِ `server/test`
   روی PGlite، و رابط را در `ui/v3` بسازید. رفتارِ پرخطر پشتِ پرچم + ردیف در نقشهٔ پرچم‌ها.
3. ستونِ «تأیید» را با **دستورِ واقعیِ آزمون و نتیجه** (مثلاً `TEST_DATABASE_URL=pglite:memory node --test test/x.test.js ⇒ ۸/۸`)
   و commit/PR پر کنید؛ وضعیت را فقط وقتی `IMPLEMENTED-VERIFIED` کنید که هر بندِ ردیف شاهد دارد (ممیزی‌های «شاهدِ CI»
   دقیقاً همین را می‌سنجند). سپس `spec.mjs status` و `check`.
4. سطرِ `WHATS-NEW` اگر کاربر چیزی می‌بیند.

### ۶.۲ صفِ کارِ امروز (از `tools/claude-ops/README-fa.md` §۷، HANDOFF و PRهای باز)

- **PRهای باز (۱۴۰۵/۰۸/۱۶):** #946 دستهٔ ۲۴؛ #949 و #943 دریِ دورِ ۵ و ۴؛ #942 و #944 سقفِ دستگاه که صاحب را نمی‌بندد
  (O-09)؛ #945 انبار؛ #947 امنیت؛ #948 CRM/RET؛ #950 خرید؛ #951 PRES/DATA؛ #952 HW؛ #953 عملیات (بازیابیِ «همه یا هیچ»)؛
  #954 صندوق؛ #955 CHK-15/16؛ #956 امنیتِ ویترین و پرچمِ `market.slug_hold`. شاخهٔ `ccr-3a0d593c-ac0jpj-batch-25` هم روی origin
  است (⚠️ PR اش دیده نشد). هر کدام را با CI ِ سبز squash کنید یا در دسته‌ای (§۷) بچینید.
- **بعد:** PAY-07؛ پاک کردنِ کامل کدِ رابطِ قدیم (legacy) پس از اطمینان که هیچ صفحه به آن تکیه ندارد؛ کمبودهای PARTIAL
  (اول مدیریت و پیک، بعد ویترین)؛ MISSING ِ بی مانع (ORD-20، MKT-03…08، HW-08، PUR-12 با AI-10 پس از تصمیمِ مدل، S121،
  S122)؛ دورِ تازهٔ «شاهدِ CI» پس از هر دسته؛ آیفون در هر دسته یک گام (تصمیمِ صاحب‌کار).
- **نسازید:** تصمیم‌های به‌تعویق‌افتاده (PUR-11 با D-15؛ S47 با D-05 تعارض دارد)؛ هر چیزی که حساب/کلیدِ بیرونی می‌خواهد
  تا صاحب‌کار بدهد (§ `OWNER-TODO`). ترتیبِ صاحب‌کار: مدیریت + پیک ⇐ برنامهٔ خرید (ویترین) ⇐ بقیه.

---

## ۷. روشِ کار: شاخه، PR، دسته (خلاصهٔ `tools/claude-ops/README-fa.md` برای برنامه‌نویسِ انسانی)

1. **هر کار یک شاخه و یک PR:** `git worktree add -b claude/<نام> <پوشه> origin/main` (شاخهٔ کلونِ اصلی را عوض نکنید؛
   در سیزن‌های Claude کلون روی `fix-report-channel` است و دست نمی‌خورد). `server/node_modules` را symlink کنید.
2. **کامیت:** عنوان فارسی، با `(2.14.5)` در پایان. در سیزن‌های Claude دو سطرِ پایانی (`Co-Authored-By`، `Claude-Session`) از
   `CLAUDE_FOOTER` ِ همان سیزن (`tools/claude-ops/footer.sh`)؛ برنامه‌نویسِ انسانی لازم ندارد.
3. **پیش از push:** در سیزنِ Claude: `WT=<worktree> S=<پوشهٔ موقت> bash tools/claude-ops/finish-pr.sh 0 <شاخه>` — تعارضِ
   حل‌نشده، STATUS از نو، unwrap ِ Express 5، نگهبانِ پمپ، «تازه‌ها»، `spec check`، بارگذاریِ سرور، شمارهٔ مهاجرت، کیفیت با
   شبیه‌سازیِ squash، ESLint، ادغامِ main، و push. این ابزار بی `CLAUDE_FOOTER` ِ معتبر (دقیقاً دو سطرِ `Co-Authored-By: Claude …`
   و `Claude-Session: …`، `footer.sh`) همان اول می‌ایستد؛ پس **برنامه‌نویسِ انسانی همان سنجه‌ها را دستی می‌زند:**
   ```bash
   git merge origin/main                                   # یا rebase؛ بی تعارض
   node docs/spec/tools/spec.mjs status && node docs/spec/tools/spec.mjs check    # «سالم»
   git diff --name-only origin/main | grep -E 'server/src/lib/sync-v1\.js|routes/(admin-pump|pump)'  # باید خالی باشد
   node tools/whatsnew/check.mjs --base origin/main        # ۰ یا تغییرِ نادیدنی
   (cd server && node -e "require('./src/app')")
   node tools/quality/quality.mjs && node tools/quality/quality.mjs --check migrations && node tools/quality/quality.mjs --check eslint
   ```
4. **PR به `main`، صبر برای CI** (`bash tools/claude-ops/wprs.sh <pr>`)، **squash-merge وقتی همه سبزند** (چک‌های گزارشیِ آیفون
   سد نیستند). مرج پرسیدن نمی‌خواهد؛ انتشارِ پایدار می‌خواهد.
5. **دسته (batch):** چند PR ِ آماده را در شاخهٔ `ccr-3a0d593c-ac0jpj-batch-N` ادغام کنید: `bash tools/claude-ops/bmerge.sh <wt> claude/a claude/b …`
   (STATUS خودکار؛ تعارضِ دیگر ⇒ می‌ایستد). ابزارِ تعارض: `cres.py <file> o|t|ot|to`، `baseline-max.py`، `batch-status.py`،
   `list-union.py`، `i18n-union.py`، `onew.py`. سپس `desk-check.sh` و `a3-test.sh <wt> test/<file>…`، و `finish-pr.sh` با `BUMP=1`.
   پس از squash ِ دسته، PRهای درونش را با کامنتِ «با دستهٔ #… در main آمد» ببندید.
6. **دستهٔ بعد روی دستهٔ قبل:** پس از squash ِ قبلی، ادغامِ main تعارض می‌دهد؛ سمتِ دسته را نگه دارید (`git checkout --ours`)
   و STATUS را با `batch-status.py`. PR ِ پشته‌ای که پایه‌اش squash شد: فقط کامیت‌های خودش را cherry-pick کنید.
7. **دفترِ تحویل:** پس از هر دسته یک بند به «گزارشِ سیزن‌ها» ی `docs/HANDOFF-fa.md` (چه شد، چه ماند، قدمِ بعد) و کامنت در
   issue ‏#606. هم‌زمان حداکثر ۳ کارِ موازی.

---

## ۸. پیش از دست زدن به هر بخش، این‌ها را بخوانید (فقط بخشِ لازم)

| کار | بخوانید |
|---|---|
| قاعده‌های یک بخش | `grep -n '^### v3 · ' docs/RULES-fa.md` ⇐ همان سرخط |
| پرداخت | `docs/PAYMENTS-HESABPAY-fa.md`، RULES «حساب‌پی در صندوق» |
| همگام‌سازی | `docs/SYNC-v1-fa.md`، `docs/SYNC-CLIENT-fa.md`، RULES «v3 · همگام‌سازی» |
| امنیت | `docs/security/{auth,tenant,data,client,supply,ai-access,artifacts}-fa.md`، `docs/SECURITY-PROGRAM-fa.md` |
| بازیابی | `docs/DISASTER-RECOVERY-fa.md`، `server/scripts/restore-drill.js` |
| کارایی | `docs/PERF-LOAD-fa.md`، `docs/PERF-QUERY-PLANS-fa.md`؛ مسیرِ مقیاس: شاخهٔ `claude/scale-1` ⇐ `docs/SCALING-fa.md` (⚠️ هنگامِ نوشتن روی origin نبود) |
| آیفون | `docs/IOS-PLAN-fa.md` §۰ (به‌ویژه ۰.۵، ۰.۶، ۰.۱۳، ۰.۱۵) |
| Google Play / Firebase | `docs/GOOGLE-PLAY-fa.md`، `docs/FIREBASE-SETUP-fa.md` |
| پنلِ مدیر | `docs/spec/ADMIN-CONTROL-PLANE.md`، `docs/ADMIN-PLATFORM-fa.md` |
| تصمیم‌ها | `docs/OWNER-DECISIONS-fa.md`، `docs/spec/BLOCKERS.md`، `docs/spec/40-owner-decisions.md` |
| تاریخچهٔ سیزن‌ها | `docs/HANDOFF-fa.md` (فقط بندهای بالایی) |

---

## ۹. دام‌هایی که گران تمام شد (دوباره نیاورید)

- `API_SECRET` پس از پُر شدن **هرگز** عوض نمی‌شود: کلیدِ پشتیبانِ پمپ‌ها (`lib/backup-key.js`)، کدِ ثابتِ شاگرد
  (`lib/staff-codes.js`)، کدِ تحویل و VIP از آن ساخته می‌شوند. کلیدِ خصوصیِ مجوز هم هرگز تازه ساخته نمی‌شود
  (`scripts/license-key.js` خودش نمی‌گذارد؛ کلیدِ تازه مجوزِ همهٔ نصب‌ها را رد می‌کند).
- «رفت» با «در لاگ چاپ شد» یکی نیست (ایمیل/پیامک/پوش را با پاسخِ سرویس بسنجید).
- نصبیِ آزمایشی هرگز به `.apk` ختم نمی‌شود (`….apk.beta`): برنامه‌های قدیمی اولین `.apk` ِ فهرستِ انتشارها را برمی‌دارند.
- `shop_id` هرگز از بدنه خوانده نمی‌شود؛ از عضویتِ توکن. مجوز را سرور می‌سنجد، نه پنهان کردنِ دکمه.
- خروج از حساب/حذفِ دستگاه با دادهٔ نرفته فقط از `LogoutGuard.decide` (SYN-10).
- پس از اعمالِ opها، سایه = «سایهٔ پیشین + همین opها»، هرگز «کلِ دفترِ امروز» (SYN-01).

---

## ۱۰. آنچه این سند نسنجیده

- کدام چک‌ها در branch protection ِ main اجباری‌اند (تنظیمِ GitHub، از مخزن دیده نمی‌شود).
- اینکه `secrets.TOHID_KEYSTORE_B64` امروز در GitHub پُر است یا CI هنوز با کلیدِ داخلِ مخزن امضا می‌کند (فقط لاگِ گامِ
  «کلیدِ امضا» می‌گوید).
- ساختِ اندروید و آزمونِ کاملِ کامپیوتر در همین سیزن (فقط `DariTextTest` ِ هسته و `api-contract.test.js` ِ سرور اجرا شد،
  هر دو سبز). فهرستِ دستیِ §۷ گامِ ۳ از خواندنِ `finish-pr.sh` آمده، نه از اجرای آن بیرون از سیزن.
- سد یا گزارش بودنِ تک‌تکِ گام‌های `perf.yml`؛ وضعِ شاخهٔ `claude/scale-1` و `docs/SCALING-fa.md`.
- اینکه پنلِ وبِ مدیر (`server/public/admin`) رسماً «منجمد» است: سندی نیافتم که بگوید؛ هنوز تغییر می‌گیرد (آخرین ۱۴۰۵/۰۸/۱۲).
  منجمدِ رسمی فقط وبِ مدیریتِ دکان (`index.html` + `license/*`) است.
- مقدارهای واقعیِ `.env` ِ سرورِ خانگی (فقط صاحب‌کار می‌بیند).
