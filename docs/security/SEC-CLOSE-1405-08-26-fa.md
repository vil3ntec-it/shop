# بستنِ مانده‌های امنیتی — «e-sec-close» (۱۴۰۵/۰۸/۲۶)

عاملِ `e-sec-close`، شاخهٔ `claude/e-sec-close` (از `claude/e-devver`) و در مخزنِ مرکزِ فرمان `claude/e-sec-close-panel` (۱.۵۳.۲).
دنبالهٔ دو ممیزیِ تیمِ قرمزِ همان روز (`REDTEAM-CRACK-1405-08-26-fa.md`): ممیزیِ رازهای در حالِ سکون در هر دو مخزن، بستنِ
«سنجیده‌نشده»ها با شواهدِ اجراشده، آماده کردنِ چرخشِ کلیدِ امضا برای صاحبِ مخزن، و گیرِ گاه‌به‌گاهِ کارِ گزارشِ کشیدنِ صفحه‌ها.
همهٔ کار روی نمونهٔ محلی (PGlite، PostgreSQL ِ ۱۶ ِ محلی، SQLite ِ واقعیِ پنل) و خروجی‌های **عمومیِ** انتشار؛ سرورِ واقعی لمس نشد.

> این سند فقط موارد **اصلاح‌شده** و شمارِ کلیِ موارد باز را دارد؛ جزئیاتِ موارد باز بیرون از هر مخزن نگه‌داری می‌شود.

## موارد اصلاح‌شده

### C-01 — رازِ TOTP ِ مدیرِ سرورِ حساب در دیتابیس خام بود (متوسط)
- **مسیر و ریشه**: `routes/admin-2fa.js` (setup/enable) و `lib/admin-2fa.js` (`checkSecondFactor`) رازِ base32 را همان‌طور در
  `admins.totp_secret` / `totp_pending` می‌نوشتند و می‌خواندند. راز برای سنجیدنِ کد لازم است (هش‌شدنی نیست) ⇒ باید رمز شود.
- **رفع**: `lib/admin-totp-seal.js` — پاکتِ `ats1.` با AES-256-GCM، کلید با HKDF از رازِ سرور (`API_SECRET`)، AAD = شناسهٔ مدیر
  (پاکتِ یک مدیر روی ردیفِ دیگری باز نمی‌شود). پاکتِ ناخوانا ⇒ هیچ کدی پذیرفته نیست (کدِ بازیابی و `ADMIN_BOOTSTRAP_PASSWORD`
  راهِ نجات می‌مانند). ردیفِ خامِ نسخه‌های پیشین در هر راه‌اندازی پیش از نخستین درخواست رمز می‌شود (کلید فقط در محیط است، پس
  مهاجرتِ SQL نمی‌تواند)؛ برگشت: `node scripts/admin-totp-seal.js unseal`.
- **آزمون**: `server/test/admin-totp-at-rest.test.js` ردیفِ واقعیِ دیتابیس را می‌خواند — پیش از رفع ۴ از ۵ قرمز (رازِ خام در ردیف)،
  پس از رفع ۵/۵. مجموعه‌های مرتبط سبز: admin-totp ۱۰/۱۰، route-authz-inventory ۴/۴، shop-route-inventory ۲/۲، tenant-matrix ۵/۵،
  tenant-matrix-branches ۱/۱، paid-routes-entitlement ۶/۶، login-alerts ۸/۸، login-contract ۱۹/۱۹، login-hardening ۳۶/۳۶،
  security-hardening ۱۳/۱۳، api-contract ۲/۲، sec-auth ۱۲/۱۲، server-secret ۲۴/۲۴، log-secrets ۲/۲، backup-secrets ۱۱/۱۱.

### C-02 — رازِ TOTP ِ کاربرانِ پنلِ مرکزی در `panel.db` خام بود (متوسط) — مخزنِ `server`، ۱.۵۳.۲
- **ریشه**: `src/routes/auth.js` (setup/enable) و `src/auth.js` (`consumeTotp`) رازِ base32 را خام نگه می‌داشتند، در حالی که رازهای
  جدولِ `settings` از پیش با جعبهٔ رازِ `data/vault.key` رمز بودند.
- **رفع**: `src/lib/totp-seal.js` — همان جعبهٔ راز (`enc1:`)، شناسهٔ کاربر درونِ پاکت؛ مهاجرتِ ۰۱۵ ردیف‌های کهنه را رمز می‌کند؛
  برگشت: `node scripts/totp-unseal.mjs`.
- **آزمون**: `test/totp-at-rest.mjs` روی ردیفِ واقعیِ `panel.db` — پیش از رفع ۶ قرمز، پس از رفع ۲۱/۲۱؛ totp ۵۲/۵۲ و admin-gate،
  support-telegram، owner-recovery، auth-e2e، roles-logs، backup-restore، move-server، settings-secrets، jwt-alg-pin، security،
  auth-abuse، refresh-token، app-auth، remake-spec، module-boundary، smoke، data-perms همه سبز.

### B — «سنجیده‌نشده»های پیشین، حالا با شواهدِ اجراشده
- **خروجی‌های ویندوز و مک** (`tohid-desktop-beta`): MSI، EXE ِ آفلاین (Inno Setup 6.7؛ بستهٔ LZMA2 مستقیم باز شد)، EXE ِ آنلاین،
  7z و هر دو DMG — هر کدام در پوشهٔ تازهٔ خودش باز شد؛ `artifact-gate --profile desktop` روی هر پنج: ۰ یافتهٔ سد، ۰ هشدار؛ جست‌وجوی
  دستیِ کلید/رمز/‎.env‎/سورسِ سرور/پرچمِ اشکال‌زدایی: ۰؛ `BuildConfig.DEBUG = false` و نشانیِ API همان دامنهٔ عمومی.
- **هم‌زمانیِ واقعی**: کارِ CI «آزمون‌های سرور روی PostgreSQL» (postgres:16، همهٔ پرونده‌ها) همین رقابت‌ها را دارد و سبز است
  (تمدیدِ هم‌زمانِ اشتراک F0-14، سقفِ دستگاه SEC-SUB-04، آخرین عددِ موجودی INV-05/06، سفارشِ تکراری ORD-02/RSK-05، …).
  روی PostgreSQL ِ ۱۶ ِ محلی هم اجرا شد (admin-idempotency، sec-sub-device-cap، pg-concurrency، pg-concurrency-2، catalog-stock،
  online-orders، order-risk، order-ledger، catalog-transfers — همه سبز).
- **آزمونِ تازه**: `server/test/pg-race-renewal.test.js` — دو و پنج تمدیدِ هم‌زمان با کلیدهای **جدا** ⇒ دقیقاً همان شمار دوره (هیچ‌کدام
  گم نمی‌شود). سنجشِ جهش: با برداشتنِ قفلِ `sub-grant` ِ `grantOnce` روی PostgreSQL هر دو قرمز شدند، سپس قفل برگشت.

### C — چرخشِ کلیدِ امضای اندروید با یک فرمان (F0-09/SEC-08)
- کارِ صاحبِ مخزن روی کامپیوترِ خودش: `android-native/tools/rotate-signing-key.sh` و `.ps1` ⇐ گام‌به‌گام در `docs/OWNER-ACTIONS-fa.md`.
- CI: `android-native/tools/sign-rotated.sh` (v2 با کلیدِ پیشین برای اندروید ۷–۸، v3 با کلیدِ تازه + دودمان برای ۹+)، گامِ پیش‌فرض‌خاموش
  در `tohid-native.yml` و `tohid-native-preview.yml`، و `apk-gates.sh` ِ آگاه از چرخش. بی secret ِ دودمان هیچ گامی اجرا نمی‌شود.
- سنجیده با کلیدِ **دورریختنی** روی یک APK ِ منتشرشدهٔ واقعی و دو نسخهٔ apksigner (کلید سپس پاک شد). نسخهٔ PowerShell اجرا نشد.

### D — گیرِ گاه‌به‌گاهِ `NewScreensRenderTest` (کارِ «(گزارش)»)
- **ریشه (از کد؛ روی کامپیوترِ خودی بازتولید نشد)**: `ScreenRender` صحنه را با `Dispatchers.Unconfined` می‌ساخت. ادامهٔ
  `withContext(IO)` یا `delay` ِ اثرهای صفحه (سرورِ ساختگیِ سفارشِ خرید، حلقهٔ صندوقِ سندهای ردشده) روی نخِ IO یا `DefaultExecutor`
  هم‌زمان با `scene.render` ِ نخِ آزمون اجرا می‌شد — همان بن‌بستِ `runLock` ِ Compose با قفلِ دفتر که `perf/ScreenFrame.kt` و آیفون
  (#1037، #1039) پیش‌تر دیده بودند.
- **رفع**: `SceneDispatcher` — همهٔ کارهای صحنه در صفی که روی همان نخِ کشیدن پیش و پس از هر قاب خالی می‌شود؛ نخِ دیگری واردِ صحنه نمی‌شود.
- **شکستِ زود به جای ۴۰ دقیقهٔ بی‌صدا**: `RenderWatchdog` (قاعدهٔ JUnit) — مهلتِ ۴ دقیقه برای هر آزمون (کلِ کلاس ~۴۰ ثانیه است)،
  رونوشتِ همهٔ نخ‌ها با قفل‌ها و بن‌بست‌یاب در `thread-dump-*.txt` ِ artifact و پیامِ شکست، و کنار رفتنِ بقیهٔ همان کلاس؛ گامِ
  «کشیدن و سنجیدن» مهلتِ ۲۵ دقیقه و خلاصه رونوشت را چاپ می‌کند. `RenderWatchdogTest` خودِ نگهبان را می‌سنجد (۲/۲).
- **سنجش** (`gradlew -p desktop -Pdev=true test --tests …NewScreensRenderTest`، lane A): پیش و پس از تغییر ۵/۵ سبز و گزارشِ کشیدن
  یکی — ۵۲ حالت، همان شمارِ هر نوع یافته، هیچ `error`؛ تفاوتِ ۸ عکس فقط ساعتِ «نسبت به اکنون». `SafeCurrencyRenderTest` و
  `NewProductFormRenderTest` (همان ابزار) هم سبز.

## موارد باز (شمارِ کلی، بی جزئیات)
- **۶ مورد** باز ماند: ۱ متوسط (نیازمندِ یک تنظیمِ صاحبِ مخزن، بی تغییرِ کد) و ۵ کم (دو در مرکزِ فرمان، یکی طراحیِ پیکربندیِ سرور،
  دو در کدِ مشترک با پمپ که این عامل اجازهٔ تغییرش را نداشت). هیچ‌کدام دسترسیِ پولیِ سمتِ سرور یا جداییِ دکان‌ها را نمی‌شکند.
  جزئیات، مسیرِ کد و پیشنهادِ رفع بیرون از هر مخزن سپرده شد.
