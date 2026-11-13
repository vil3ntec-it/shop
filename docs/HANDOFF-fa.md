# دفترِ تحویل بین حساب‌ها (HANDOFF)

صاحب ریپو با دو حسابِ Claude کار می‌کند و هر وقت یکی به سقفِ مصرف برسد، با حسابِ دیگر ادامه می‌دهد.
**هر سیزن، پیش از کار روی پروژه، این فایل خوانده می‌شود و پیش از پایانِ سیزن یک بند به «گزارشِ سیزن‌ها» اضافه می‌شود**: چه
کاری انجام شد، چه کاری نیمه‌کاره ماند و قدمِ بعدی چیست. وضعیتِ تک‌تکِ شناسه‌ها همچنان در `docs/spec/STATUS.md` است.
این فایل فقط تصویرِ کلی، تصمیم‌های صاحب محصول و کارهای در جریان را نگه می‌دارد.
**تصمیم‌های مانده با صاحب‌کار (فهرستِ یکتا، از ۱۴۰۵/۰۸/۱۹):** [`OWNER-PENDING-fa.md`](OWNER-PENDING-fa.md).
**⚡ بخشِ زندهٔ هماهنگ‌کننده برای حسابِ دیگر (از ۲۰۲۶-۱۱-۱۳):** [`tools/claude-ops/LIVE-fa.md`](../tools/claude-ops/LIVE-fa.md) — پس از هر شروع/پایانِ کار به‌روز
می‌شود؛ تازه‌ترینش همیشه روی شاخهٔ `claude/live-handoff` است: `git fetch origin claude/live-handoff && git show origin/claude/live-handoff:tools/claude-ops/LIVE-fa.md`.
**اگر سیزنِ قبلی به سقفِ مصرف خورد، اول همین را بخوان** (گام‌به‌گام: عامل‌های نیمه‌کاره، دستهٔ بعدی، صف).

## تصمیم‌های صاحب محصول (۱۴۰۵/۰۸/۰۸) — در سند: `docs/spec/40-owner-decisions.md` (§۴۰)

1. **دو برنامهٔ جدا**: مدیریتِ فروشگاه (با حالتِ پیک) و خریدِ مشتری (برنامه و سایت). حالتِ «خرید» در برنامهٔ مدیریت ساخته نمی‌شود؛ PRِ #359 باید با این بخواند.
2. **پیک**: حالتی در برنامهٔ مدیریت ولی بخشِ مستقل که فقط با سرور کار می‌کند، تا بعداً بی بازنویسی برنامهٔ جدا شود.
3. **برنامهٔ بومیِ آیفون** با KMP و Compose Multiplatform. از همین حالا منطقِ تازه در `core` و بی وابستگیِ تازهٔ `android.*`/`java.*`. تا امروز فقط `ios-spike` (کامپایلِ `core` برای iOS) هست.
4. **بازارِ بزرگ** با تبلیغات (دکان، کالا، خودِ صاحب، سایت‌های دیگر) و API برای سیستم‌های دیگر.
5. **ترتیب**: اول مدیریت و پیک (هم‌زمان کارهای لازمِ بازار در سرور و مدیریت)، بعد برنامهٔ خرید، بعد API و تبلیغات.

## تصویرِ کلی (از `docs/spec/STATUS.md`، ۱۴۰۵/۰۸/۰۸)

سندِ بارگذاری‌شده با نامِ `MASTER-SPEC-VILL3N-REVISED-v32.md` همان `docs/spec/MASTER-SPEC.md` است (SHA-256 `9e4a59f3…`، یکسان). از ۱۴۰۵/۰۸/۰۸ بخشِ ۴۰ به سند افزوده شد (SHA-256 تازه `dfa09307…`).
`spec.mjs check` سبز است.

| وضعیت | شمار از ۷۶۴ |
|---|---|
| IMPLEMENTED-VERIFIED (پیاده‌سازی‌شده و تأییدشده) | ۱۱۳ |
| PARTIAL (نیمه‌کاره) | ۳۰۷ |
| MISSING (انجام‌نشده) | ۲۳۱ |
| NOT-VERIFIED (هنوز ممیزی‌نشده) | ۹۰ (۸۷ تا سناریوهای S) |
| BLOCKED-EXTERNAL (منتظرِ بیرون) | ۲۳ |
| BROKEN (خراب) | ۰ |

- قوی‌ترین بخش‌ها: F0 (۲۲ از ۲۸)، AUTH (۷ از ۱۰)، PRM (۹ از ۱۸)، INV (۱۷ از ۳۹).
- تقریباً شروع‌نشده: PCK (۰ از ۱۴)، MKT (۰ از ۱۲)، AI (۰ از ۱۲)، PUR (۱ از ۱۴)، NTF (۰ از ۱۰).
- کارِ جاری در «WP-0 تا WP-2» است (آزمون‌ها، امنیت، پنلِ مدیر/عملیات، بانکِ تصاویر).

## PRهای باز = کارهای نیمه‌کاره (۱۴۰۵/۰۸/۰۸)

| PR | موضوع |
|---|---|
| #412 | نسخهٔ ۳، حالتِ پیک روی کیت |
| #411 | پنلِ مدیر: تطبیقِ فروشِ آنلاین و تسویه — E2E-ADM-15 |
| #409 | سرور: برداشتنِ wrap() زائد (Express 5) |
| #407 | ذخیرهٔ کهنه پس از Pull — ادغامِ سه‌طرفه، پشتِ پرچمِ خاموشِ `shop.ledger_merge` |
| #400 | نسخهٔ ۳، گروهِ آنلاین (سفارش، ویترین، گفت‌وگو، میزِ تحویل) |
| #393 (پیش‌نویس) | پول روی کیتِ نسخهٔ ۳ |
| #391 (پیش‌نویس) | فروش (G5) روی کیتِ نسخهٔ ۳ |
| #390 | ورود و دروازه‌ها روی کیتِ نسخهٔ ۳ (G1) |
| #364 | گاوصندوق PAY-18، گامِ ۱ |
| #359 (پیش‌نویس) | پایهٔ بازسازیِ VILL3N: کیتِ v3، سه حالتِ دکان/پیک/خرید — **به تصمیمِ جداسازی برخورد دارد** |
| #357 | پیک: امروز/هفته به وقتِ دکان |
| #336 | نقش‌های تازهٔ کارمند — STF-01 |
| #335 | همگام‌سازی پس از بازیابیِ سرور |
| #323 | خرید: برگشت به تأمین‌کننده (وابسته به #315) |
| #315 | موتورِ تازهٔ موجودی و بها پشتِ تنظیمِ خاموش |
| #273 | دکانِ فعالِ نشست — مهاجرتِ ۰۹۷ (منتظرِ «مرج کن» صاحب محصول) |

## گزارشِ سیزن‌ها (تازه‌ترین بالا)
### ۱۴۰۵/۰۸/۲۳ — عاملِ e-rows-45v (د۸) زیرِ هماهنگ‌کننده، شاخهٔ `claude/e-rows-45v` (از `claude/batch-60`، a3e53fd57)

- **NOTIF-06 (برنامهٔ ویندوز در سینی؛ اعلان/صدا تا تأیید):** یادداشتِ STATUS («بخشِ سفارش/اعلانِ سفارش ندارد») نادرست بود؛ کامپیوتر همان
  صفحهٔ «سفارش‌ها» و `OrdersLive`/`OrderNotify` ِ اندروید را دارد. کارِ پنهانِ واقعی: پرسشِ سفارش به چرخهٔ پنجره بسته بود (پنجرهٔ کوچک‌شده در
  Compose ِ کامپیوتر `CREATED` ⇒ هیچ پرسش؛ پنهان در سینی `STARTED` ولی هر ۲۰ ثانیه)، اعلانِ سیستم یک بالنِ یک‌باره، کلیکش فقط پنجره را جلو
  می‌آورد (`PendingIntent` در shim هیچ ⇒ نه سفارش باز می‌شد، نه رسیدِ «باز شد»)، و صدای تکراری تا «دیدم» نبود.
- **ساخته:** `core/orders/OrderRing.kt` (منطقِ خالص) و `desktop/…/desktop/OrderTrayAlarmDesktop.kt`: حلقهٔ جدا از پنجره هر ۸ ثانیه
  `GET /api/shop/order-notify/pending` پشتِ `shop.orders` (نقشِ بی `orders.view` ⇒ خاموش) و `shop.order_notify`؛ بالنِ سیستم فقط با شمارهٔ سفارش،
  یک بار برای هر اعلان (حافظهٔ «نشان‌داده‌شده» ِ `OrderNotify`)؛ سه نتِ زنگ هر ۱۵ ثانیه تا «دیدم»؛ نشانِ سینیِ چشمک‌زن، راهنما و منوی سینی
  («سفارشِ تازه #n»، «دیدم»)؛ نوارِ بالای پنجره با «دیدم» و «باز کردن»؛ کلیک ⇒ رسیدِ «باز شد» (فقط `orders.manage`) و همان سفارش تازه از سرور
  (`OrdersLive.openFromNotice`؛ چند سفارش ⇒ فهرست)؛ بی اینترنت بی خطا (فقط سفارش‌های شناخته تا «دیدم»). سیم‌کشی در `Main.kt` (۴ جا)؛
  `ScanFeedback.play` ِ کامپیوتر `internal` شد. سرور دست نخورد (درها از #258 بودند).
- **آزمون‌ها (سرخ پیش/سبز پس):** `OrderRingTest` ۶/۶ (core jvmTest؛ پیش از کد کامپایل‌نشدنی)، `OrderTrayAlarmTest` ۴/۴ (پیش از کد:
  `Unresolved reference 'OrderTrayAlarm'`)، `OrderAlarmBannerUiTest` ۲/۲ (نوارِ پنجره روی Compose ِ کامپیوتر؛ پس از کد نوشته شد)؛ اجرای کاملِ `./desktop/gradlew -p desktop test` (flock، `desktop/build/test-data` ِ پاک) ⇒ ۴۱۳۱ آزمون، **۰ شکست**، ۴ SKIP ِ پیشین (اجرای کاملِ نخست `HardcodedColorsTest` را برای رنگِ ثابتِ نقطهٔ سینی سرخ کرد ⇒ رنگ از تم، `Shop.colors.danger`)؛ core `jvmTest` کامل ۱۹۱۳ (۰ شکست، ۲ SKIP)؛ نگهبانِ `kotlin-test-names-windows` ۳/۳؛ precheck `fail=0`، `quality` بی ❌، `spec check` سالم، `whatsnew` سبز.
  ⚠️ کامپایلِ کاملِ desktop با `-Dorg.gradle.jvmargs=-Xmx2g` و `in-process` OOM داد (`VariableLivenessFrame`، `PurchaseSuggestView`) ⇒ `-Xmx3g`.
- **STATUS:** NOTIF-06 همان BLOCKED-EXTERNAL (§۳۰.۶) با یادداشتِ تازه و مانعِ درست («دستگاهِ واقعیِ ویندوز»، نه Firebase)؛ یادداشتِ ORD-02 و S36
  هم‌راه شد؛ PARTIAL-MAP: شمارها همان.
- **نیمه‌کاره/قدمِ بعد:** فقط کارِ صاحب‌کار: روی ویندوزِ واقعی با دو پرچمِ روشن، برنامه در سینی ⇒ سفارشِ ویترین ⇒ بالن و زنگ ≤ ۱۰ ثانیه،
  تکرار تا «دیدم»، کلیکِ بالن ⇒ همان سفارش (بررسی‌نشده). اندروید (ORD-02/S36) همچنان FCM می‌خواهد.

### ۱۴۰۵/۰۸/۲۳ — عاملِ e-rows-46w (د۹) زیرِ هماهنگ‌کننده، شاخهٔ `claude/e-rows-46w` (از `claude/batch-60`، a3e53fd57)

- **§۲۳.۴ «تغییرِ شمارهٔ تلفن» ِ حسابِ مشتری (ماندهٔ `e-rows-44u`):** منطق در `lib/store-customers.changePhone` بود و درش با
  `/api/market/account/**` رفته بود. حالا `POST /api/store/:slug/account/phone` (نشستِ همین حساب + بلیتِ تازهٔ شمارهٔ نو از همان
  `verify/start` و `verify/check` و `lib/otp`، ≤ `accountTicketMaxMs`، هم‌شماره با بدنه) پشتِ `shop.storefront.web_account` (خاموش ⇒ همان
  درِ ناشناخته پیش از سقف و نشست)، و رابط در «حسابِ من» ِ ویترین (`store/phone.js` ِ دیربار از `acct.js`؛ `kit/i18n/phone.*.json`).
  **باگِ واقعی:** `changePhone` «حسابِ دیگری دارد» را فقط با همان نوشته می‌سنجید ⇒ ‎+930…‎ ِ شمارهٔ حسابِ دیگر پذیرفته می‌شد (یک شماره،
  دو حساب)؛ حالا هر شکل (`phoneForms`). تکرار/هم‌زمانی ⇒ ۲۰۰ ِ `unchanged` و یک سابقه؛ سابقه بی شماره. شاهد در AUTH-08 (همان PARTIAL ِ پرچم).
- **آزمون‌ها (سرخ پیش/سبز پس):** `storefront-account-phone.test.js` پیش از در ۵/۶ سرخ، پس ۶/۶ (جهش: سنجشِ تک‌شکل ⇒ سرخ)؛
  `web-store-phone.mjs` پیش از رابط سرخ (بخش نیست)، پس ۳۴ ✅. اجرای کاملِ سرور (PGlite، دو تکهٔ هم‌زمان): ۴۲۵۴ آزمون،
  ۴۲۱۲ قبول، **۰ شکست**، ۴۰ SKIP و ۲ TODO ِ پیشین؛ همهٔ ۳۵ پروندهٔ `web-store*.mjs` ⇒ ۱۷۱۳ ✅ — تنها ❌ ِ `web-store-vitals` (INP ِ 2G
  میانه ۲۵۶ms زیرِ بارِ ۹ ِ ماشین) با اجرای دوباره در بارِ کمتر سبز (۱۶۸ms، ۲۳ ✅)؛ بارِ نخستِ JS دست نخورد. `precheck.sh` ⇒ `fail=0`.
- **تصمیمِ صاحب‌کار (ساخته نشد؛ `OWNER-PENDING-fa.md` ۲-۲):** «بازیابیِ حساب» ِ مشتری امروز همان ورودِ دوباره با کد به همان شماره از هر دستگاه است (S138)؛ برای
  **سیم‌کارتِ گم‌شده** راهی نیست. §۲۱ («مسیرِ بازیابی تعریف شود؛ نه با دانستنِ شماره»، نمونه: رمز/کدِ بازیابی) و §۲۹.۲ بندِ ۱۰ روش را
  روشن نمی‌گویند ⇒ گزینه‌ها: کدهای بازیابیِ یک‌بارمصرف هنگامِ ساختنِ حساب، یا فقط «حسابِ تازه با شمارهٔ تازه» (سفارش‌ها سندِ دکان‌اند).
  هم‌چنین سفارش‌های پیش از تغییرِ شماره در «سفارش‌های من» ِ حساب دیگر دیده نمی‌شوند (به شماره بسته‌اند) — اگر باید بیایند، تصمیم.
- **قدمِ بعد:** روشن کردنِ `shop.storefront.web_account` برای یک دکانِ آزمایشی؛ برنامهٔ خریدِ جدا (اگر آمد) همین در را صدا بزند.

### ۱۴۰۵/۰۸/۲۲ — عاملِ e-rows-44u (د۷) زیرِ هماهنگ‌کننده، شاخهٔ `claude/e-rows-44u` (از `main`، 64c931c3d)

- **دنبالهٔ B15 (`SECURITY-AUDIT-fa.md`، جدولِ «دنبالهٔ B15»):** برای هر درِ «نامطمئن» ِ بازرسی یک تصمیم:
  - **ساخته (رابطِ تازه):** «خروج از همهٔ دستگاه‌ها» در «حساب من» (AUTH-06، `POST /api/auth/logout-all` پس از همان برگهٔ خروجِ امن)؛
    «باطل کردنِ لینکِ پیگیری» در جزئیاتِ سفارشِ ویترین (TEN-03؛ کلیدِ تازهٔ `trackLinkRevoke` ِ `/config`، پاسخِ سرور حالا `link`/`whatsappUrl`)؛
    «PIN ِ صندوق» در صفحهٔ کارمند (STF-04؛ حال، باز کردنِ قفل، برداشتن با برگهٔ تأیید؛ `till-pin.mayManage`)؛ «زمانِ رسیدن برای مشتری» روی
    مأموریتِ «در راه» ِ پیک (DLV-06؛ کلیدِ تازهٔ `etaEnabled` ِ `/courier/me`، `core/courier/CourierEta`)؛ «گزارشِ عکسِ نادرست» برای عکسِ مرکزی
    (§۳۷.۷؛ `core/image/BankPhotoReport`). `GET …/trust/cases/:token/evidence/:evidenceId` از پیش زنده بود (`e-rows-42w`).
  - **پیش‌ساخته (درِ سرور هست، رابط نه؛ پرچمِ خاموش = همان درِ ناشناخته با `next('router')`):** ۱۳ درِ کاتالوگِ سرور (INV-01/INV-11/CHK-18 —
    برداشتنشان ۴۶ پروندهٔ آزمون را جابه‌جا می‌کرد، بیش از ۳۰ دقیقه)، `PUT /till-pin/me`، `GET /current|sessions|ops|ops/row` (STF-04/S46)،
    `GET /api/courier/map(/:id)` (DLV-05). قاعدهٔ RULES («درِ بی صدازننده…») همین را حالا صریح می‌گوید.
  - **برداشته (`dead-doors.test.js` ‹`GONE_2`›، ۱۹ در + `GET /api/courier/route`):** `/api/market/account/**` (نامِ دومِ حسابِ ویترین)،
    `POST /shop/images/:id/versions|process`، `GET /shop/delivery/settlements/:id`، `GET /shop/trust/sanctions`، `GET /api/terms`، `GET /api/store?id=`،
    `GET /api/courier/route` (برنامه خودش می‌سازد؛ `lib/courier-route.js` فقط پرچم). کتابخانه‌های مرده هم: `image-bank.shopVersion`،
    `delivery-settle.getSettlement`، `market-trust.sanctionsForShop`، `courier-route.plan/routeOf`.
  - **INV-21:** ادعای `POST /catalog/moves` درست شد — برنامه repack را محلی می‌کند و موجودیِ سرور همان سندِ تولید را از همگام‌سازی می‌گیرد.
- **آزمون‌ها (سرخ پیش/سبز پس):** `dead-doors` ۳/۳ روی 64c931c3d سرخ؛ `till-pin`/`till-op-user` (پرچمِ خاموش ⇒ درِ ناشناخته) ۲ سرخ روی روترِ پیشین؛
  `catalog-stock`/`catalog-transfers` ۲ سرخ؛ `storefront-link-expiry` ۱ سرخ؛ `delivery-customer-view` ۲ سرخ؛ Kotlin: `SignOutEverywhereTest`،
  `OrderTrackLinkTest`، `TillPinMemberModelTest`، `CourierEtaRepositoryTest` (+ core: `BankPhotoReportTest`، `CourierEtaTest`) پیش از کد کامپایل‌نشدنی.
  `market-account.test.js` حساب را از درِ ویترین می‌سازد و تغییرِ شماره را مستقیم روی `store-customers.changePhone` می‌سنجد.
- **نتیجه:** همهٔ آزمون‌های سرور روی PGlite (دو تکه، `scripts/test-pglite.js`): ۴۲۴۷ آزمون، ۴۲۰۵ قبول، **۰ شکست**، ۴۰ SKIP و ۲ TODO ِ پیشین؛
  e2e ِ `delivery` و `flags-on` ۱۹/۱۹ گام؛ اجرای کاملِ `./desktop/gradlew -p desktop test` (با `desktop/build/test-data` ِ پاک) ⇒ ۴۱۲۱ PASS،
  ۰ FAIL، ۴ SKIP ِ پیشین؛ core: `BankPhotoReportTest` ۲/۲، `CourierEtaTest` ۲/۲، `BankPhotoCardTest` ۱۱/۱۱. کامپایلِ اندروید/آیفون فقط در CI.
- **نیمه‌کاره/قدمِ بعد:** §۲۳.۴ «تغییرِ شمارهٔ مشتری» حالا در و رابط ندارد (منطق و آزمونش ماند؛ ردیفِ STATUS ندارد) — اگر خواسته شد،
  `POST /api/store/:slug/account/phone` با رابطِ «حسابِ من» ِ ویترین. `GET /api/auth/terms` هم صدازنندهٔ برنامه ندارد (عمومی، مستند در
  `server/README.md`؛ ماند). درهای پیش‌ساختهٔ کاتالوگ با مدلِ شعبه (فازِ ۶) رابط می‌گیرند یا می‌روند.

### ۱۴۰۵/۰۸/۲۲ — عاملِ e-rows-41s (س۶) زیرِ هماهنگ‌کننده، شاخهٔ `claude/e-rows-41s` (از `main`، 62a6bac71)

- **سطحِ حمله (B15 ِ `SECURITY-AUDIT-fa.md`):** ۶۳ درِ بی صدازننده برداشته شد — کلِ `routes/data.js` (۵۱ در: `/api/products|inventory|sales|
  sale-items|suppliers|purchases|returns|debtors|expenses|payments` و `POST /api/sales/full`)، `GET /api/shop/memberships`، `POST /api/shop/leave`،
  `GET`/`PUT /api/shop/settings`، `PUT /api/shop/permissions/roles/:role`، `GET /api/shop/approvals` (+ `lib/approval-log.js`)، `GET`/`PUT
  /api/me/settings` (شاگرد «همگام‌سازیِ ابری» ِ کلِ دکان را عوض می‌کرد)، `POST /api/me/redeem|errors`، `GET /api/portal/subscriptions` و نامِ دومِ
  `POST /api/auth/staff-login`؛ `customer-access.note` ِ مرده هم. ⚠️ دروازهٔ پایانیِ `routes/data.js` (`requireUser, requireShop` روی هر
  مسیرِ ناشناختهٔ `/api`) در `app.js` **ماند**: برنامهٔ خرید «درِ پرچمِ خاموش = ۴۰۱ ِ درِ ناشناخته» را قرارداد کرده (`account-cart-v1.json`،
  `ShopperCartSync.doorClosed`، `ShopperAddressSync.transient`) و اجرای کاملِ نخست همین را با `storefront-cart`/`security-review-38` نشان داد.
  شاهد: grep ِ همهٔ برنامه‌ها، `server/public/**`، وبِ دکانِ برداشته‌ای که درونِ APK ِ پوستهٔ WebView است (`git show b187c45be^`)، نسخهٔ بومیِ
  بایگانیِ `android/native-ui-archive`، بات‌ها، `scripts`، `tools`، `bench` و قرارداد. کدِ پمپ دست نخورد (دستگیره‌های مشترکِ `portal.*` همان).
- **ماندند با دلیل:** `/api/billing/status|plans|request` و `POST /api/shop/invite` (وبِ درونِ APK ِ WebView)؛ `GET`/`POST`/`PUT /api/shop` و
  `/api/shop/staff/join` (نسخهٔ بومیِ بایگانی، `server/README.md`)؛ `POST /api/shop/active` (RULES، TEN-01)؛ `GET /api/shop/audit` (RULES ِ
  `security.doc_chain` و `credit-limit-review`). همه در `KEPT` ِ `dead-doors.test.js`. بندهای نامطمئن/پیش‌ساخته ِ گزارش دست نخوردند.
- **آزمون‌ها:** یاورهای `h.seed`/`legacyPush`/`legacyRows`/`legacyRow`/`roleLayer` (e2e: `E.roleLayer`/`E.approvals`)؛ ۷۰ پرونده جابه‌جا شد؛
  نگهبان‌های S89/B7، SR43-2، لغو و سقف‌ها از درِ مستقیم به راهِ قدیمِ **زنجیره‌دار** رفتند (همان `pushChanges`)؛ CRM-15 ِ سابقهٔ دسترسی روی
  درِ `app` و «سفارش‌های یک مشتری». `dead-doors.test.js` ِ تازه روی 62a6bac71 ۳/۳ سرخ، پس از تغییر ۳/۳ سبز. آستانهٔ ماتریسِ نشست‌دارِ
  `tenant-matrix` ۱۲۰ ⇐ ۸۵ (۵۶ درِ برداشته؛ امروز ۸۷). SR46-3 ِ `customer-access` (کلاس‌بندیِ پین در درِ `debtor`) با همان در رفت.
- **نتیجه:** همهٔ آزمون‌های سرور روی PGlite (دو تکه، `scripts/test-pglite.js`): ۴۲۴۲ آزمون، ۴۲۰۰ قبول، **۰ شکست**، ۴۰ SKIP و ۲ TODO ِ پیشین؛
  e2e (`flags-on`، `journeys-a/b`، `scenario-s45`، `roles`، `access`) ۵۴ گامِ سبز؛ مرورگر: `web-store`، `web-store-cart-sync`، `web-store-acct`،
  `portal-rep`، `web-shop-gone` سبز؛ precheck `fail=0`، `quality.mjs` بی ❌ (سقفِ `staff-approval.js` ۱۰۵۴ ⇐ ۱۰۵۲). Kotlin دست نخورد.
- **نیمه‌کاره/قدمِ بعدی:** برداشتنِ درهای «ماندند» پس از داشبوردِ نسخه‌ها (یا ۴۱۰ ِ روشن). `ACCOUNT_PREFIXES` ِ پنلِ خانگی (ریپوی `server`)
  خوانده نشد؛ اگر `/products`… را دارد فقط پیکربندی است. بررسی‌نشده: `backup.test.js` و آزمونِ هم‌زمانیِ PostgreSQL ِ `sale-void-chain`
  (Postgres ِ محلی بالا نبود).
### ۱۴۰۵/۰۸/۲۳ — عاملِ e-rows-43f (ز۶) زیرِ هماهنگ‌کننده، شاخهٔ `claude/e-rows-43f` (از `claude/batch-59` ‏6216d2739)

- **WEB-25 (پیامِ ۴۰۹ ِ «ثبت» در هر زبان):** `err.store_paused`/`err.store_offline` در هیچ واژه‌نامه نبود (fa.json ۲۱٬۴۷۷ از
  ۲۱٬۵۰۴ و checkout.fa.json ۴٬۰۸۳ از ۴٬۰۹۶ پر بودند) ⇒ `K.api` برای en، ps و دری (`prs` ≠ `fa`) «خطای عمومی» می‌داد. اکنون
  واژه‌نامهٔ دیربارِ تازهٔ `kit/i18n/ckform.<زبان>.json` (۴۵ کلیدِ فرمِ ثبت: نشانی، ناحیه، پین، تأییدِ شماره، سبدِ برگهٔ ثبت + این
  دو پیام؛ فارسی = همان متنِ سرور) که store.js در همان `Promise.all` ِ `loadCheckout` با checkout.js و `checkout` می‌گیرد
  (هم‌زمان، نه پشتِ سرِ اسکریپت). fa.json ⇐ ۱۸٬۳۴۴، ps ⇐ ۱۸٬۲۳۶، en ⇐ ۱۳٬۷۷۳ ⇒ سقفِ اصلی ۲۱KB ⇐ ۲۰KB؛ ckform ۳٬۵۹۹/۳٬۴۱۰/۲٬۶۱۷
  زیرِ ۴KB. JS ِ بارِ اول ۷۲٬۶۳۶ ⇐ ۷۲٬۶۳۴ (سقف دست نخورد).
- **نگهبان:** `web-i18n.test.js` سه آزمونِ تازه: کلیدِ بارِ اول (boot/kit/store.js + HTML ِ بیرون از بخش‌ها) و هر پروندهٔ بی بخش
  فقط در اصلی؛ کلیدِ پروندهٔ دیربار در اصلی یا بخش(های) خودش؛ دو واژه‌نامهٔ ثبت پیش از `StoreCheckout` (سه جهش ⇒ سرخ).
  مرورگر `web-store-paused-i18n.mjs` (در `test:web-store`): پیش ۱۱ ❌، اکنون ۳۵ ✅.
- **قدمِ بعد:** کلیدِ تازهٔ برگهٔ ثبت به `checkout` یا `ckform` (هر کدام جا دارد)، نه اصلی. شاید `pausedUntil` ِ پاسخ («تا ساعتِ …»)
  هم در پیام بیاید — ساخته نشد (خواسته نبود).

### ۱۴۰۵/۰۸/۲۳ — عاملِ e-rows-42w (و۶) زیرِ هماهنگ‌کننده، شاخهٔ `claude/e-rows-42w` (از `main` ‏62a6bac71)

- **CHK-13 (پایین):** `POST /api/store/:slug/orders/:token/pay-choice` روش را پیش از تنظیمِ «پرداخت پس از پذیرش» می‌سنجید ⇒
  خاموش + بدنهٔ بد ⇒ ۴۰۰. اکنون `order-flow.assertChoiceOpen` پیش از توکن و بدنه. نگهبانِ تازهٔ `store-flag-off-gate.test.js`
  همهٔ درهای `/api/store/:slug/…` را با پرچم‌های خاموش و بدنهٔ بد/تهی می‌زند (۴۰۴ یا پاسخِ درِ ناشناخته؛ ۲۴ درِ بی‌پرچم با دلیل) —
  فقط pay-choice سرخ بود. ⚠️ درِ ناشناختهٔ `/api/store/*` ۴۰۱ `unauthorized` است (نه ۴۰۴)؛ `next('route')` همان را می‌دهد.
  CHK-24 ِ مشتری با پرچمِ خاموش ۴۰۳ `delivery_prefs_disabled` ِ مستند می‌دهد (پرچم پیش از بدنه) — دست نخورد.
- **ABUSE-01 (درِ مدرک):** spec §۳۸.۶ «پرونده با شواهد» ⇒ رابط ساخته شد: «افزودنِ عکس»/«دیدنِ عکس‌ها» در «گزارش‌های من» ِ
  `store/trust.js` (دیربار، بی innerHTML). باگِ سرور: مدرک فقط metadata پاک می‌کرد ⇒ بمبِ پیکسلِ ۱۲۰۰۰×۱۲۰۰۰ پذیرفته می‌شد؛
  اکنون `chatMedia.cleanImage` ⇒ ۴۱۳. سقف‌ها سنجیده: حجم ۱٫۵MB پیش از خواندن، نوع از بایت، `evidenceMax` ۵، سطلِ IP ۳۰.
- **ردیف‌های بی‌آزمون:** WEB-25، CHK-19، CHK-23، ORD-21 شاهدِ نام‌به‌نام در STATUS و آزمونِ تازه (`web-store-checkout-rows.mjs` در
  `test:web-store`؛ `storefront.test.js` «ORD-21»). باگِ یافته: `accept.paused` در واژه‌نامه نبود ⇒ سرِ ویترین هنگامِ توقفِ میز خالی.
  ⚠️ `fa.json` ۲۱٬۴۷۷ از ۲۱٬۵۰۴ بایت و `checkout.fa.json` ۴٬۰۸۳ از ۴٬۰۹۶: جا برای کلیدِ تازه نیست (پیامِ ۴۰۹ `store_offline`/`store_paused`
  در en/ps هنوز «خطای عمومی» است؛ fa متنِ سرور را نشان می‌دهد).
- **بند ۴ (ب):** فقط سدِ پرچمِ همهٔ درها، درِ مدرک و نمونهٔ CHK-21/CHK-22 در کد؛ سنجشِ ردیف‌به‌ردیفِ ۷۶ ردیفِ `webrows.json` نماند.
- **قدمِ بعدی:** جدا کردنِ کلیدهای فرمِ ثبت از `fa.json` تا جای ترجمهٔ `err.store_offline`/`err.store_paused` باز شود؛ ادامهٔ بند ۴.

### ۱۴۰۵/۰۸/۲۲ — عاملِ e-rows-40k (ی۵) زیرِ هماهنگ‌کننده، شاخهٔ `claude/e-rows-40k` (از سرِ `claude/batch-57`، ccfbef65e)

- **کلیدِ مجوزِ آیفون (میانه، SUB-02):** `ios/shared/build.gradle.kts` ِ `generateConfig` `LICENSE_PUBLIC_KEY = ""` را ثابت می‌گذاشت ⇒
  `SyncStore.PINNED_PUBLIC_KEY` تهی و کلید از NSUserDefaults (ویرایش‌پذیر، در پشتیبان) ⇒ `LicenseGuard`/`OfflineCode.verify` با کلیدِ
  دست‌کاری‌پذیر. اکنون همان منبعِ کامپیوتر (`env("TOHID_LICENSE_PUBLIC_KEY") ?: prop("tohid.licenseKey")`، همان `dev`)، کلید ورودیِ کار، و
  هشدارِ ساخت وقتی تهی است (فقط `-Pios=true`). `IosLicensePinTest` (desktop، دو پروندهٔ Gradle را می‌خواند) روی پروندهٔ پیشین ۳/۳ سرخ،
  پس از تغییر سبز؛ اجرای واقعیِ `-p ios/shared generateConfig`: کلیدِ محیط ⇒ همان، `-Pdev=true` ⇒ «»، بی کلید ⇒ «» و هشدار. ورک‌فلوها دست
  نخوردند: `ios-spike.yml` کلید را نمی‌دهد ⇒ سطرِ تازه در `OWNER-NEXT-fa.md` §۳ برای ساختِ انتشاریِ آیفون. `SIGNING_SHA256`/`UPDATE_PUBLIC_KEY`
  عمداً خالی (توضیح دقیق شد).
- **کدِ مرده:** `ShopperApi.chats` و سطرِ آزمونش؛ `ShopperText.appName` (سنجهٔ زبانِ `CustomerModelTest` ⇐ `ShopperText.back`)؛ `crmTabs`
  (+ `CustomerReportsPhoneTest`؛ دو سنجهٔ `DariCoverageTest` مستقیم روی `StrReach.tabAging/tabRfm`) و `SERVERS_SECTION_TITLE` — گراف
  (`39m/graph.py` روی همین شاخه) و grep ⇒ بی‌راه. ⚠️ کفِ «پوششِ دریِ دورِ ۵» ِ `DariCoverageTest` ۱۹ ⇐ ۱۸ پایین آمد (۱۱ از ۶۱): همان دو کلید
  با `crmTabs` از پرونده‌های دورِ ۵ رفتند و در نسخهٔ ۳ زنده‌اند؛ دلیل در توضیحِ همان کف.
- **اسناد:** DATA-06 — «آیفون `sync-v3/` را از پشتیبانِ iCloud بیرون نمی‌گذارد» کهنه بود (`NoBackupDirs`، b41c514b9؛ `NoBackupDirsJvmTest` ۷/۷
  محلی)؛ ردیف همان PARTIAL در «دستگاه» (دو گوشیِ واقعی). شاهد در SUB-02، CRM-04، CRM-14؛ نقشهٔ PARTIAL (شمارها همان).
- **سقفِ اندازه:** `DeskSale.kt` ۱۰۸۶ ⇐ ۱۰۷۹ و نُه پروندهٔ دیگر به اندازهٔ امروز (`ReportFresh.kt` زیرِ ۸۰۰ از فهرست رفت)؛ فقط پایین.
- **آزمون‌ها:** `core` jvmTest ۱۹۰۳ (۰ شکست، ۲ SKIP)؛ نگهبان‌های سرور ۲۴/۲۴؛ precheck `fail=0`، `quality.mjs` بی ❌؛ اجرای کاملِ
  `flock /tmp/gradle-full.lock ./desktop/gradlew --no-daemon -p desktop test` ⇒ ۴۱۱۲ آزمون، ۰ شکست، ۴ SKIP ِ پیشین. `customer-android` فقط CI.
- **نیمه‌کاره/قدمِ بعدی:** هیچ. بررسی‌نشده: `NoBackupDirsIosTest` و آزمونِ سرتاسریِ آیفون با این شاخه (فقط CI ِ macOS)؛ کامپایلِ `customer-android`.

### ۱۴۰۵/۰۸/۲۲ (شب) — هماهنگ‌کننده: دسته‌های ۵۵ و ۵۶ در main، دستهٔ ۵۷ آماده

- **در main:** دستهٔ ۵۵ (#1041؛ پس از رفعِ گیرِ `sharp` زیرِ Electron در `jpeg-trailer.test.js`)، server #147 (پنل 1.50.98) و دستهٔ ۵۶
  (#1042؛ سیم‌های گم‌شدهٔ نسخهٔ ۳). پس از هر مرج `account-server.yml` ِ server روی main اجرا شد.
- **بازرسیِ دسترس‌پذیری** (گرافِ نام‌ها از ریشه‌های v3/کامپیوتر/پیش‌نمایش؛ اسکریپت در scratchpad ِ همان سیزن، `audit-v3/graph2.py` و
  `39m/graph.py`) ۱۴ کمبودِ واقعی یافت که همه ساخته شد: POS-24، RET-08/09/10، S22، POS-28/22/11، S49، INV-05، CRM-14، PAY-19، OPS-02، S45،
  AI-11، AUTH-08؛ بازرسیِ دوم (هم‌سانیِ کامپیوتر) ۴ کمبود: S22 پیش از پولِ حساب‌پی، PAY-19 در `ShiftV2`، PRM-02 در `SalesHistory.voidSale`، STF-02.
  بازبینیِ مستقلِ ایمنیِ دستهٔ ۵۶: یافتهٔ بالا/میانه نداشت؛ دورزدنِ پیشینِ «تعویض» (بی سدِ منقضی/قاعده/مالیات/گرد) در دستهٔ ۵۷ بسته شد.
- **دستهٔ ۵۷ (`claude/batch-57`):** تعویض = فاکتورِ صندوق (`data/TillGuards.kt`، `data/ExchangeTill.kt`)، چهار سدِ کامپیوتر، برداشتنِ کدِ
  مردهٔ آزمون‌شده (۵ پرونده، ۴۶ بخش). هر عامل اجرای کاملِ ۰ شکست داشت؛ اجرای کاملِ خودِ دسته پیش از PR.
- **پرسش‌های تازه برای صاحب‌کار** (`docs/OWNER-NEXT-fa.md` ۲-الف): SYN-08 (نشانهٔ همیشه‌پیدای همگام‌سازی در خانهٔ گوشی) و مبنای قاعدهٔ
  جهتِ قیمت برای بسته.
- **مانده (کد):** `crmTabs` ِ مرده در `CustomerReportsPhone.kt` هنوز آزمون دارد؛ `CheckoutDialog` ِ قدیم (فقط پیش‌نمایشِ گوشیِ کامپیوتر)
  مالیات/پله را جدا می‌خواند؛ سقفِ `DeskSale.kt` در `baseline.json` را می‌شود ۱۰۸۶ ⇐ ۱۰۷۹ کرد. **قدمِ بعد:** PR ِ دستهٔ ۵۷ و مرج؛ سپس
  همان بازرسیِ دسترس‌پذیری برای برنامهٔ خریدِ مشتری (`customer-android`) و آیفون.
- **دام‌های تازه:** scratchpad میانِ عامل‌ها مشترک است ⇒ لاگِ هر عامل در زیرپوشهٔ خودش؛ دیسک پر شد (Gradle-home ِ عامل‌های تمام‌شده را
  پاک کن)؛ timeout ِ اجرای پس‌زمینه کمتر از زمانِ اجرای کامل نباشد.

### ۱۴۰۵/۰۸/۲۲ — عاملِ e-rows-38d (ک۴) زیرِ هماهنگ‌کننده، شاخهٔ `claude/e-rows-38d` (از سرِ `claude/batch-57`، e69b30114؛ ادغامِ e98f1e282)

- **S22 در درگاه‌های صندوقِ کامپیوتر (میانه):** `DeskSale` پیش‌سنجیِ حساب‌پی را بی `expiryGate` می‌زد و تأییدِ منقضی را فقط پس از تسویه
  می‌پرسید؛ «انصراف» تسویهٔ گرفته‌شده را دور می‌انداخت. **سرنوشتِ پول در مسیرِ پیشین:** درگاهِ خودکار ⇒ قصدِ PAID زیرِ کلیدِ فاکتور در
  `HesabPayAttempts` می‌ماند؛ برگه (بی پنلِ ترکیبی) در «پرداخت شد» با «ثبت» می‌ماند و بازکردنِ همان فاکتور با همان مبلغ همان تسویه را دوباره
  پیشنهاد می‌کرد (فقط با تأیید ثبت می‌شد)؛ ولی سبدِ عوض‌شده (مثلاً برداشتنِ کالای منقضی) ⇒ `replace` ⇒ قصدِ پرداخت‌شده لغو نمی‌شود ⇒
  `amountMismatch` و **پول بی فاکتور** (بازپرداختِ دستی). حالتِ ساده («پول رسید») هیچ ردی نگه نمی‌داشت. با پنلِ ترکیبی `payOpen = false`
  برگه را هم می‌بست. حالا `ui/screens/DeskExpiryPayAndroid.kt` (`DeskExpiryPay`): `GatewayHook.ready` (تازه، پیش‌فرض `true`) پیش از باز
  شدنِ برگهٔ پول — حساب‌پیِ خودکار و ساده و کارت‌خوانِ پنل — `CheckoutExpiry.uncovered` را می‌سنجد ⇒ همان پنجرهٔ PIN؛ پیش‌سنجی
  `HesabTillModel.precheck` با همان `expiryGate` و تأیید؛ تأیید به کلیدِ فاکتور بسته و `finish` همان را می‌خواند. ادامهٔ خودکارِ قصدِ باز
  (`resumable`) از `ready` نمی‌گذرد (پول شاید گرفته شده).
- **PAY-19 (میانه):** «کشو بی فروش» ِ `ShiftPanelV2` ⇒ `DrawerNoSale.phoneAction` با `ShopRole.canOpenDrawer`. نکته: `drawer.open`
  «restrict» است ⇒ شاگرد/صندوق‌دار هرگز «مجاز» نمی‌شوند؛ فرقِ واقعی مدیری است که دکان کشو را برایش «ممنوع» یا «نیازمندِ تأیید» کرده.
- **PRM-02 (میانه):** `SalesHistory.voidSale` حالا همان `SalesVoid.run` ِ نسخهٔ ۳ (کپیِ دومِ `InvoiceVoid.check`+`SalesEngine.cancel` برداشته شد)
  و پس از لغو/اصلاحِ موفق `PromoLive.releaseOnVoid`.
- **STF-02 (پایین، PARTIAL می‌ماند):** خوانندهٔ یگانهٔ `data/CancelAskAndroid.kt` (`CancelAsk.offered`، `need`): گوشیِ v3 «لغو» را در صفحهٔ
  فاکتور، کشیدنِ ردیف و برگهٔ کارها برای نقشِ «نیازمندِ تأیید» روی Sync v3 نشان می‌دهد («اصلاح» همان `canCancel`)؛ جدولِ کامپیوتر ⇒
  `CancelApprovalDialog` (`SalesVoidDialogs.kt`، همان `ApprovalDialog`) ⇒ `voidSale(..., slip)` ⇒ `Sale.cancelApproval`. سمتِ سرور ساختنی
  نماند؛ ماندهٔ ردیف همان D-17، پرچمِ `shop.approval_requests` و دیدن روی دستگاه.
- **آزمون‌ها (پنجرهٔ واقعیِ Compose):** `DeskExpiryPayUiTest` ۱، `ShiftNoSaleDeskUiTest` ۳، `DeskInvoiceVoidUiTest` ۳، `CancelAskPhoneUiTest` ۲ —
  روی e69b30114 (worktree ِ جدا با همین آزمون‌ها) سرخ به دلیلِ درست (جز دو نگهبانِ «مدیرِ بی محدودیت» و «v1 بی ورود» که پیش و پس سبزند)، پس از
  درمان سبز. اجرای کاملِ `flock /tmp/gradle-full.lock ./desktop/gradlew --no-daemon -p desktop test` روی `033d9dec8` (پس از ادغامِ `claude/batch-57` ‏e98f1e282، محلی): ۴۱۱۹ آزمون — ۴۱۱۵ سبز، ۰ شکست، ۴ SKIP ِ پیشین (BUILD SUCCESSFUL).
- **نیمه‌کاره/قدمِ بعدی:** هیچ کدِ نیمه‌کاره‌ای نیست. بررسی‌نشده: آیا پنلِ ترکیبیِ گوشیِ v3 (کارت‌خوان، اگر دارد) هم مثلِ دکمهٔ حساب‌پی
  پیش از پول تأییدِ منقضی می‌پرسد؛ و سقف‌های `baseline.json` ِ `DeskSale.kt` (۱۰۸۶ ⇐ ۱۰۷۹) را می‌شود پایین آورد (`--lower-baseline`) — دست نزدم تا با
  شاخه‌های هم‌زمان ناسازگار نشود.
### ۱۴۰۵/۰۸/۲۲ — عاملِ e-rows-39m (پ۴) زیرِ هماهنگ‌کننده، شاخهٔ `claude/e-rows-39m` (از سرِ `claude/batch-57`، e69b30114)

- **هدف: هیچ آزمونی روی کدِ مرده سبزِ دروغ نشان ندهد.** گرافِ دسترسی‌پذیریِ نام‌محور روی همین worktree از نو ساخته شد با ریشه‌های
  محافظه‌کار: `ui/v3/**`، `AppRoot`/`MainActivity`/`AppShellAndroid`، **کلِ** `desktop/src/main/kotlin/**` (پیش‌نمایش‌های گوشیِ `PhonePreview*`
  و `shots` هم)، `ios/shared/src/iosMain/**`، کلاس‌های مانیفست (با `backupAgent`) و `SyncV1Worker`. نتیجه: ۷ پروندهٔ کامل‌مرده (نه ۲۹ ِ
  بازرسی) — بیشترِ «مرده»های بازرسی را `PhonePreview` (`SettingsScreen`، `SaleScreen`، `QuickSaleScreen`، `DebtorsScreen`…) هنوز صدا می‌زند و
  طبقِ دستور زنده‌اند؛ `TohidBackupAgent` از مانیفست زنده است.
- **برداشته شد (۵ پروندهٔ برنامه + ۱ آزمون، ۴۶ بخشِ سطحِ بالا + `MissionView.tableLines` ِ core)، هر کدام بی هیچ صدازننده در برنامه، کامپیوتر،
  پیش‌نمایش و آیفون (grep + گراف):**
  - میزِ سفارشِ رابطِ امروز: `OrderDeskPanel.kt` و `OrderAlertsUi.kt` (کامل)، `OrderDelayEntry`/`OrderDelaySheet`، نوار/برگهٔ گروهی
    (`OrderBulkToggle`/`Row`/`Bar`/`Confirm`)، دکمه و برگهٔ «پروندهٔ مدرک» (`OrderEvidenceEntry` و یاورهای خصوصی‌اش). حالت‌های مشترک
    (`OrderDeskState`، `OrderDelayForm`، `OrderBulkState`، `evidenceMoney`) ماندند. آزمون‌ها: `OrderDeskV3Test` (به‌جای `rememberOrderDesk(api)` ِ
    پنل اکنون همان در `OrdersRouteContent` ِ نسخهٔ ۳)، `ScenarioS42KeyTest` و `ServerFeatureScreensTest` فقط نسخهٔ ۳ را می‌خوانند.
  - ثابت‌های عنوانِ مرده (`RETURN_SETTINGS_TITLE` در `ReturnsV2` — فقط همین یک سطر —، `PRICE_LIST_SETTINGS_TITLE`، `STOCK_PLACES_TITLE`،
    `SMART_FORECAST_TITLE`، `ANOMALY_TITLE`، `OWNER_REPORT_TITLE`، `STOREFRONT_ETA_TITLE`، `PROMO_GUARD_TITLE`، `EASY_SETUP_TITLE`،
    `TRAINING_SETTINGS_TITLE`، `TILL_GOODS_TITLE`، و `ScanCashSettingsAndroid.kt` که فقط `SCAN_CASH_TITLE` بود). `SettingsPortTest` به‌جای برابری با
    ثابتِ مرده، مقدارِ کلیدِ `SettingsLayout` را میخ می‌کند (سنجه سست نشد).
  - `ui/theme/PhoneSheet.kt` + `PhoneSheetContrastTest` (جانشین: `PhoneV3`)، `ui/screens/Sparks.kt` (بی هیچ ارجاع).
  - `HomeSections` و `StatsBuckets` (`PhoneHomeModel.kt`) با ۵ آزمونشان ⇒ `PhoneHomeModelTest` ۲۱؛ شاهدِ S96 و `ShellTargetsTest` به درهای
    نسخهٔ ۳ (`NavCatalog.allowed`/`BUILT_IN` و مسیرِ `V3Modules`).
  - `CourierCall.tableLines` و `MissionView.tableLines` (core): ردیفِ تبلت `rowLines` است و نشانیِ کنارِ نشانه را عمداً نمی‌گذارد، پس آزمونِ core
    رفتاری را می‌سنجید که صفحه ندارد ⇒ آن آزمون رفت (`MissionAddressTest` ۵)، شاهدِ DLV-02 فقط `CourierCallV3Test`.
- **عمداً نماند/ماند (با دلیل):** `CheckoutDialog` (`SaleDialogs.kt`، که پس از ادغامِ `claude/batch-57` ‏e98f1e282 هنوز مالیات و پله را جدا می‌خواند) مرده نیست ⇒ برداشته نشد؛ `GiftCardWiringTest:43,45` (`CheckoutDialog`) و `CustomerPricesWiringTest:79` (`PriceListSettingsWired`،
  `Settings.kt:689`) دست نخوردند: `PhoneV3CartPreview` ⇐ `SaleScreen`/`QuickSaleScreen` ⇐ `CheckoutDialog` و `PhonePreview` ⇐ `SettingsScreen` ⇐
  همان سطر هنوز صدا می‌زنند. `QuickSaleSearch` (+ آزمونش) هم از همین راه زنده است. `pay/DevicePaySettle` پلِ پیش‌ساختهٔ PAY-08 تا D-08 است، نه
  رابطِ کهنه ⇒ ماند و kdoc ِ آزمون و STATUS صریح شدند «هنوز هیچ صفحه‌ای صدا نمی‌زند». `core/i18n/I18nTables` فهرستِ جدول‌ها برای آزمون‌های پوشش
  است (و `server/test/kt-source.js` آن را می‌خواند) ⇒ ماند.
- **STATUS:** یادداشتِ «بازرسیِ کدِ مرده» در ORD-03/04/07/08/09/19، S42، S96، PAY-08، DLV-02؛ **هیچ وضعیتی عوض نشد**؛ PARTIAL-MAP: یک بند و متنِ PAY-08.
- **سنجه‌ها:** `compileTestKotlin` ِ desktop دو بار سبز (پس از بخشِ سفارش؛ پس از همه و ادغامِ `origin/claude/batch-57` ‏e98f1e282، تعارض فقط در دو ردیفِ
  STATUS: سمتِ batch-57 + یادداشتِ همین دور)؛ `(cd core && ../desktop/gradlew --no-daemon jvmTest)` زیرِ flock: ۱۹۰۳ آزمون، ۰ شکست (۲ SKIP ِ سرورِ
  زنده)، `MissionAddressTest` ۵/۵؛ نگهبان‌های سرور `dari-text`، `kotlin-file-mirrors`، `kotlin-test-names-windows` ۲۴/۲۴؛ `spec.mjs check` سالم؛
  `quality.mjs` بی ❌؛ precheck fail=0. اجرای کاملِ `rm -rf desktop/build/test-data && flock /tmp/gradle-full.lock ./desktop/gradlew --no-daemon -p desktop test`
  روی f47a07491: ۴۱۰۳ آزمون، ۰ شکست، ۴ SKIP ِ پیشین (ACL ِ ویندوزِ `AtRestDiskTest`، دو سنجهٔ perf، `NewScreensRenderTest`)؛ هیچ skip ِ تازه‌ای نیست.
  سنجه‌های جابه‌جاشده روی کدِ زنده سبزند: `OrderDeskV3Test` ۵، `ScenarioS42KeyTest` ۳، `SettingsPortTest` ۸، `ScenarioS96Test` ۱، `ShellTargetsTest` ۵.
- **نیمه‌کاره / قدمِ بعد:** نیمه‌مرده‌های درونِ پرونده‌های زنده (۲۵ پروندهٔ نیمه‌مرده در کلِ برنامه؛ ۴۲ بخشِ مرده در `ui/screens`/`ui/components`؛ گرافِ نام‌محورِ
  این دور در scratchpad ِ سیزن ماند، در ریپو نیست) دست نخوردند؛ تنها موردی که هنوز آزمون دارد `crmTabs` (`CustomerReportsPhone.kt`؛ `CustomerReportsPhoneTest` و
  دو سنجهٔ `DariCoverageTest`). `SERVERS_SECTION_TITLE` (`Servers.kt`) هم مرده ولی بی آزمون.

### ۱۴۰۵/۰۸/۲۲ — عاملِ e-rows-37e (ت۵) زیرِ هماهنگ‌کننده، شاخهٔ `claude/e-rows-37e` (از سرِ `claude/batch-57`، e69b30114)

- **فاکتورِ «تعویض» = فاکتورِ صندوقِ همین دستگاه (RET-05):** سه کمبودی که e-rows-36t دید سنجیده شد. (۱) **مالیات (POS-37) — کمبودِ واقعی، ساخته شد:**
  برگشتِ فاکتورِ مالیات‌دار سهمِ مالیات را پس می‌دهد (`paidRatio`) ولی فروشِ تعویض مالیات نمی‌گرفت ⇒ تعویضِ همان کالا با ۱۰٪ ِ روشن ۱۰۰ از ۱۱۰۰ را به
  مشتری می‌داد. (۲) **گردِ نقدی (PAY-09) — کمبودِ واقعی، ساخته شد:** گرد فقط روی نقدی که مشتری پس از نقدِ برگشتِ همان سند می‌دهد (همان «سهمِ نقدی که
  فاکتور را می‌بندد» ِ `Pay.settle`؛ `Checkout.cashCovered` ⇐ `ExchangeTill.sale`، تنها حساب `SalesEngine.cashRoundingMinor`)؛ پس‌دادنی هرگز.
  (۳) **فروشندهٔ ردیف (POS-23) — کمبود نبود (شاهد):** سبدِ تعویض ردیفِ سپرده‌شده ندارد ⇒ قلم‌ها بی `seller` و به نامِ فروشندهٔ فاکتور؛ ولی فروشندهٔ
  **فاکتور** کمبود داشت (S46): هر دو برگه `SyncStore.accountName` می‌دادند ⇒ حالا `TillUser.me` (خاموش ⇒ همان نامِ حساب).
- **یک خواننده:** `TillGuards` حالا `tax` و `cashRounding` هم دارد (`TillGuardsPref` ⇐ `SaleTaxPref.active`، `CashRoundPref.step`)؛ `DeskSale` و
  `CheckoutRoute` همان را می‌خوانند و `TillGuards.exchange` همان را به فروشِ تعویض می‌دهد. پیش‌نمایشِ هر دو برگه `ExchangeTill.preview` (ردیف‌های
  «مالیات» و «گردکردنِ نقدی»، همان اعدادِ ثبت). همه پیش‌فرض خاموش ⇒ بیت‌به‌بیت همان امروز (آزمونِ «خاموش»).
- **آزمون (سرخ پیش/سبز پس):** روی e69b30114 پنج سرخ — `ExchangeTillPricingTest` ۳ (`expected:<10000> but was:<null>`، `expected:<-100>`/`<200> but was:<null>`)،
  `ExchangeGuardsDeskUiTest.theExchangePreviewAndInvoiceCarryTheTillTax` («ردیفِ مالیات در پیش‌نمایشِ تعویض نیامد»)، `ScenarioS46Test` («ReturnFlowSheet.kt
  هنوز نامِ حساب را مستقیم می‌گیرد»)؛ پس از رفع ۱۸۲ آزمونِ هدف‌دار سبز (`*Exchange*`، `*Return*`، `*Checkout*`، `*SaleTax*`، `*Rounding*`، `*LineSeller*`،
  `*ScenarioS46*`، `PosLogicTest`…). اجرای کاملِ `rm -rf desktop/build/test-data && flock /tmp/gradle-full.lock ./desktop/gradlew --no-daemon -p desktop test` روی `ad418ea14`: ۴۱۱۰ آزمون، ۰ شکست، ۴ SKIP ِ پیشین (ACL ِ ویندوز، دو بنچ، عکسِ صفحه‌ها؛ BUILD SUCCESSFUL، ۱۰ دقیقه).
- **نیمه‌کاره / قدمِ بعد:** هیچ. دیده‌شده و دست‌نخورده: صندوقِ رابطِ قدیمِ گوشی (`CheckoutDialog` در `SaleDialogs.kt`) هنوز مالیات و پله را جدا می‌خواند و از `TillGuards` نمی‌گیرد؛
  تعویض برگهٔ STF-03 (سقف‌های سرور) و «فروشندهٔ ردیف» را در رابط ندارد (همان پیش از این).

### ۱۴۰۵/۰۸/۲۲ — عاملِ e-rows-36t (ت۴) زیرِ هماهنگ‌کننده، شاخهٔ `claude/e-rows-36t` (از سرِ `claude/batch-56`، 0d06aa8d5)

- **دورزدنِ S22 و POS-24 از راهِ «تعویض» ِ برگشت (شدتِ میانه/بالا) بسته شد:** فروشِ تعویض در گوشیِ v3 (`ReturnFlowSheet`) و کامپیوتر
  (`ReturnsV2`) با `SalesEngine.Checkout(seller = me)` ِ خام ثبت می‌شد ⇒ کالای منقضی بی تأییدِ مدیر، کالای منقضی با «فروخته نشود»، فاکتورِ صفر و
  فاکتورِ بی مشتری از راهِ تعویض. حالا `data/TillGuards` (خالص) + `TillGuardsPref` (Android) یک خوانندهٔ سدهای فروشِ همین دستگاه است که
  `DeskSale`، `CheckoutRoute` و هر دو برگهٔ برگشت می‌خوانند؛ `TillGuards.exchange` فروشِ تعویض را می‌سازد و `ReturnFlow.exchangeExpiry` نیازِ منقضی
  را روی دفترِ پس از برگشت می‌سنجد ⇒ همان برگهٔ PIN ِ صندوق (گوشی `CreditApprovalSheet`، کامپیوتر `CreditApprovalDialog`)؛ تأیید ⇒ همان ثبت، بستن ⇒
  پیامِ موتور. **تصمیمِ POS-24:** قاعده‌ها روی خودِ فاکتورِ تعویض (نه تفاوت)؛ مشتریِ فاکتورِ تعویض = مشتریِ برگشت (فقط موجود و فعال)؛ بی مشتری ⇒
  پیامِ صندوق (برگشت و فروشِ جدا). جملهٔ RULES «تعویض همیشه `CheckoutRules.OFF`» عوض شد.
- **فروشِ سریعِ پیشین (`QuickSale`، فقط «پیش‌نمایشِ گوشی»):** صافیِ کاشی‌ها به `QuickSaleSearch` با `CatalogModel.query(search, d).term` (همان v3)؛
  «1*3x3 کابل» ِ `VisualSearch.tillText` دیگر فهرست را خالی نمی‌کند.
- **آزمون (سرخ پیش/سبز پس):** `ExchangeTillGuardsTest` ۵/۵، `ExchangeGuardsDeskUiTest` ۲/۲، `ExchangeGuardsPhoneUiTest` ۱/۱ (پنجرهٔ واقعیِ Compose)،
  `QuickSaleSearchTest` ۲/۲، نگهبانِ تنگ‌شدهٔ `CheckoutRulesWiringTest` — پیش از رفع ۶ از ۱۹ سرخ (تعویضِ منقضی/رایگان با «فروش‌ها: 2» بی پرسش ثبت شد،
  `[]` به جای `[cable]`)؛ پس از رفع ۶۰۲ آزمونِ هدف‌دار سبز (جز یک انتظارِ نادرستِ خودِ آزمونِ تازه که اصلاح شد). اجرای کاملِ `rm -rf desktop/build/test-data && flock /tmp/gradle-full.lock ./desktop/gradlew --no-daemon -p desktop test` روی `1dc9916b5`: ۴۱۰۱ آزمون، ۰ شکست، ۴ SKIP ِ پیشین (BUILD SUCCESSFUL، ۱۱ دقیقه).
- **نیمه‌کاره / قدمِ بعد:** هیچ. دیده‌شده و دست‌نخورده: فروشِ تعویض مالیاتِ فاکتور (POS-37)، گردکردنِ نقدی و فروشندهٔ ردیف را ندارد (بیرون از این کار).

### ۱۴۰۵/۰۸/۲۲ — عاملِ e-rows-31w زیرِ هماهنگ‌کننده، شاخهٔ `claude/e-rows-31w` (از سرِ `claude/batch-56`)

- **OPS-02 — «پاک‌سازیِ کاملِ اطلاعات» ِ نسخهٔ ۳ درست شد (باگِ پرخطرِ 28l):** قاعده در `core/backup/WipeAll` (commonMain): ردیف فقط برای
  **صاحب** (`visible`؛ مدیر با `settings.write` دیگر نمی‌بیند)، دو مرحله در یک برگه (`WipeAllSheet`: هشدار ⇐ «ادامه» ⇐ نوشتنِ دقیقِ نامِ دکان؛
  «پاک کن» تا نامِ درست بسته)، پیش از نوشتن نقش و نام دوباره (`BackupController.wipe(typed)` ⇐ `decide`) و «پاک شد» فقط با `true` ِ `replaceAll`
  (`outcome`؛ وگرنه «اطلاعات پاک نشد؛ هیچ چیزی تغییر نکرد»). رابطِ پیشین دکمهٔ پشتیبانِ پیش از پاک‌سازی نداشت (فقط هشدار) ⇒ همان هشدار.
  `WipeAllName` ⇐ `WipeAll.matches` (پیش‌نمایشِ `WipeAllDialog` هم). `WipeAllNameTest` حالا صفحهٔ زنده را می‌سنجد. ردیف PARTIAL می‌ماند (USB/دستگاه).
- **S45 — گوشیِ نسخهٔ ۳:** `LineModel.check`/`apply` با همان `LinePolicy` (یک درِ تازهٔ `LinePolicy.checked` برای هر دو صندوق)، پیامِ رد زیرِ
  «قیمت هر واحد»، «درخواستِ تأیید» ⇐ `PinApprovalSheet` (بدنهٔ `CreditApprovalSheet`، حالا عمومی) ⇐ «تأیید شد: …» ⇐ «ثبت» و ردیفِ سابقهٔ
  `price_override` (اسکوپِ IO بیرون از برگه، فقط اگر سبد نشست)؛ `apply` بی تأییدِ همان قیمت سبد را دست نمی‌زند؛ قلمِ آزادِ گوشی هم سقفِ POS-10.
- **باگِ واقعیِ تازه (کامپیوتر):** `CartLineDialog` متنِ رد (`Dialog.problem`) را جای کدِ رد به `PriceApproval.askable` می‌داد ⇒ «درخواستِ تأیید» ِ
  S45 **هرگز** دیده نمی‌شد و `lifted` بی‌اثر بود؛ «۳/۳» ِ `ScenarioS45PriceTest` فقط متنِ کد را می‌خواند. رفع: `LinePolicy.Dialog.code`.
- **کارِ افزودهٔ هماهنگ‌کننده (بازرسیِ v3، بندهای ۴، ۷، ۸)، سه کامیتِ جدا:** برگهٔ ردیفِ گوشیِ نسخهٔ ۳ همان کامپوننت‌های `CartLineDialog` را
  گرفت (کپیِ دوم نه): POS-28 سریال (`posLineSerials` ⇒ `PosSerialField` ⇒ `CartSerials.set`؛ بی آن کالای سریال‌دار در گوشی فروخته نمی‌شد)،
  POS-22 «چرا این قیمت؟» (`priceWhyOn` ⇒ `PriceWhyLine.rows` با `PromoLive` ⇒ `PriceWhyPanel`)، POS-11 یادداشتِ ردیف (`posLineNote` ⇒
  `PosNoteField` ⇒ `CartNotes.set`).
- **آزمون (پنجرهٔ واقعیِ Compose روی کامپیوتر، سرخ پیش/سبز پس):** `WipeAllUiTest` ۴/۴ (پیش ۴/۴ سرخ)، `WipeAllNameTest` ۴/۴،
  core `WipeAllTest` ۶/۶؛ `ScenarioS45DeskUiTest` ۱/۱ و `ScenarioS45PhoneUiTest` ۲/۲ (پیش هر سه سرخ)، `ScenarioS45PhoneTest` ۷/۷، `ScenarioS45PriceTest` ۴/۴،
  `LinePolicyTest` ۹/۹، `LineUnitsTest` ۷/۷، `CartSheetsModelTest` ۶/۶، core `PriceApprovalTest` ۳/۳، `LinePricingTest` ۲۰/۲۰، `WhatsNewFileTest` ۴/۴؛
  `LineSerialsUiTest`، `LinePriceWhyUiTest`، `LineNoteUiTest` هر کدام ۲/۲ (پیش: آزمونِ «روشن» ِ هر سه سرخ، درست سرِ کادرِ نبوده).
- **جهش‌آزمایی:** پاک‌سازی ۴ جهش (مدیر در `visible`، نادیده گرفتنِ نتیجهٔ `replaceAll`، دکمهٔ بازِ مرحلهٔ دوم، پاک کردن روی نامِ نادرست) و S45 ۳ جهش
  (متنِ رد جای کد، `apply` بی سنجش، سقفِ قلمِ آزاد نادیده) — همه کشته (WipeAllUiTest ۳، WipeAllNameTest ۲، WipeAllTest ۲، S45: ۱۰ آزمونِ سرخ).
- **اجرای کامل:** `./desktop/gradlew -p desktop test` (با `flock`، `test-data` ِ پاک) **۴٬۰۵۵ آزمون: ۴٬۰۵۰ PASS، ۱ FAIL، ۴ SKIP ِ پیشین** —
  تنها شکست `LineSerialsUiTest` ِ تازه بود از **مسابقه در خودِ آزمون**: `CartStore.set` پیش‌نویس را ناهم‌زمان می‌نویسد و صفحه گاهی سبدِ خالی می‌خواند
  (برگهٔ ردیف «ردیف دیگر نیست» و قفلِ «فروش» زیرش). `LineSheetHarness.seed` تا نشستنِ پیش‌نویس می‌ایستد (`ScenarioS45PhoneUiTest` هم)؛ پس از آن
  چهار کلاسِ پنجره دوباره سبز (بالا). کدِ برنامه پس از اجرای کامل عوض نشد. precheck ‏fail=0؛ `quality.mjs` بی ❌؛ spec «سالم»؛ `dari-text`/نامِ آزمون سبز.
- **قدمِ بعدی:** AI-11 («جست‌وجو با عکس» در گوشی) و AUTH-08 (گامِ کدِ شمارهٔ تازه) هنوز در نسخهٔ ۳ نیستند؛ بازرسیِ v3 (بندهای ۱–۳، ۵، ۶، ۹، ۱۰).
  پیش‌نمایشِ قدیمِ `ui/screens/Settings.kt` («پاک‌سازی» با پیامِ شکستِ پوسته) دست نخورد — فقط DevShot است.
### ۱۴۰۵/۰۸/۲۲ — عاملِ e-rows-34b (ب۳) زیرِ هماهنگ‌کننده، شاخهٔ `claude/e-rows-34b` (از سرِ `claude/batch-56`)

- **ساخته شد (دو باگِ برگهٔ برگشتِ نسخهٔ ۳، `ui/v3/sales/returns/ReturnFlowSheet.kt`):** سدِ تأییدِ برگشت یک‌جا در `data/ReturnDecision.kt` —
  `of` را هر دو فرم (`ReturnFlowDialog` ِ کامپیوتر و برگهٔ نسخهٔ ۳) صدا می‌زنند و `ReturnFlow.post` همان `gate` را می‌سنجد (برابری در ۱۸۰ حالت آزموده).
  **RET-09:** برگه سیاستِ همین دستگاه (`ReturnPolicyPrefs.of`) را به سد و `Request(policy)` می‌دهد و هشدارِ «بیرون از سیاست» (`ReturnFlowModel.policyNotice`)
  نشان می‌دهد؛ `WHY_POLICY` دیگر «مجوز» خوانده نمی‌شد. **RET-10:** سقفِ دستگاه در برگه هم تجمعیِ فاکتور و تأیید با `remember(minor(total), gate)`
  (بن‌بستِ «مجاز در صفحه، APPROVAL_REQUIRED در ثبت» رفت). **یافتهٔ هم‌دسته، ساخته شد:** RET-08 — برگه دلیل‌ها را از فهرستِ ثابتِ `ReturnFlow.REASONS`
  نشان می‌داد و `reasons` را به ثبت نمی‌داد ⇒ حالا `ReturnSettings.reasons` (یادداشت در STATUS ِ RET-08؛ ردیف همان IMPLEMENTED-VERIFIED).
  هر دو ردیف IMPLEMENTED-VERIFIED ماندند با شاهدِ تازه؛ قاعدهٔ «سدِ دستی در صفحه نه» در RULES (بخشِ فروش). «تازه‌ها»: سه سطرِ «رفع».
- **آزمون‌ها (سرخ پیش / سبز پس):** `ReturnDecisionTest` ۵ (تازه؛ روی مدلِ سدِ پیشینِ برگه ۴ سرخ، با جهشِ «بی پیشین و بی سیاست» دوباره سرخ (RET-10: «approve» ⇐ «allow»))، `ReturnFlowModelTest` ۲ (تازه؛
  `whyText` سرخ بود)، `ReturnSettingsGateTest` (+۱: هر دو فرم)، `ApprovalScopeWiringTest` (برگشت: هر دو فرم)، `SalesFlowModelTest` (سیمِ برگه به `ReturnDecision`) — همه پیش
  از رفع سرخ؛ پس از رفع ۱۰ کلاسِ برگشت ۹۴/۹۴ (با `ReturnFlowTest` ۲۱، `SalesReturnTest` ۱۵، `ReturnPolicyFlowTest` ۶، `ReturnApprovalSlipTest` ۵، `ReturnLeftTest` ۴).
  آزمون‌های امروزِ «دروغ‌سبز»: `ReturnPolicyFlowTest` فقط `post` و `ReturnSettingsGateTest`/`ApprovalScopeWiringTest` فقط `ReturnsV2` را می‌خواندند.
- **اجرای کامل:** __FULL__؛ `quality.mjs` بی ❌؛ precheck ‏fail=0؛ spec «سالم».
- **دام:** دیسکِ کانتینر میانِ کار پر شد (۱۰۰ مگ آزاد ⇒ «Failed to create Jar file … gradle-api»)؛ هماهنگ‌کننده Gradle-home و `build` ِ کهنه را پاک کرد.
  `GRADLE_USER_HOME` ِ تازه را با `cp -al /root/.gradle/wrapper` بسازید (بی جای اضافه).
- **قدمِ بعدی:** دیدنِ برگه روی گوشیِ واقعی با «برگشت و تعویض» ِ روشن و مهلت/سقفِ پر (سنجیده نشد: فقط آزمونِ JVM و متن).
### ۱۴۰۵/۰۸/۲۲ — عاملِ e-rows-35h (ه۳) زیرِ هماهنگ‌کننده، شاخهٔ `claude/e-rows-35h` (از سرِ `claude/batch-56`، a5bd4667f)

- **بازرسیِ «IMPLEMENTED-VERIFIED ولی بی‌راه از نسخهٔ ۳»، بندهای ۶، ۹، ۱۰ و «نامطمئن»:** (۱) **S49** یادآورِ سررسیدِ مالیات حالا هشدارِ خانهٔ نسخهٔ ۳
  است (`HomeAlerts.Kind.TAX` + `HomeAlerts.taxDue`: تنظیمِ روشن، فقط `canSeeMoney`، درونِ `QuarterTax.reminder`؛ کارتِ کهربایی روی خانهٔ گوشی و
  کامپیوتر و ردیفِ «هشدارها»، لمس ⇐ `settings/data/tax-quarter`)؛ `ui/screens/TaxQuarterAndroid.kt` ِ مرده برداشته و یادداشتِ LOC-06 درست شد.
  (۲) **INV-05** «انبارگردانی» ِ سادهٔ نسخهٔ ۳ (`stock/count`) تراشه‌های دسته/تأمین‌کننده/برند/قفسه گرفت (`CountScope`، `CountState.options`، همان
  `CountFilter`؛ برچسبِ قفسه `CountFilter.shelfLabel`)؛ `CountFilterTest` حالا پرونده‌های نسخهٔ ۳ را می‌خواند. (۳) **CRM-14** شاهد به `ReportsModelTest`
  (RFM فقط با `canSeeMoney`)؛ «گزارش‌ها»ی کامپیوتر عمداً سدِ پول ندارد و ردیف آن را ادعا نکرده بود ⇒ فقط یادداشت. (۴) **PAY-19** «کشو بی فروش» ِ
  گوشیِ نسخهٔ ۳ کشو را **باز نمی‌کرد** (فقط ردیف، با سدِ «شاگرد نیست»): حالا همان `drawer.open` و با چاپگرِ پیش‌فرضِ کشودار همان
  `PrintSpooler.openDrawer` (`DrawerNoSale.phoneAction`)؛ در «دستگاه» می‌ماند. (۵) **SYN-08** ⇐ PARTIAL/«تصمیم»: گوشیِ نسخهٔ ۳ نشانهٔ همیشه‌پیدای
  همگام‌سازی ندارد (فقط کادرِ پنهانِ خانه، کارتِ خطا/صفِ بی‌نت، صفحهٔ همگام‌سازی)؛ سرخطِ خانه انتخابِ صاحب‌کار است ⇒ پرسش در PARTIAL-MAP.
- **آزمون:** سرخ روی a5bd4667f (کامپایل: `HomeAlerts.taxDue`/`Kind.TAX`/`ShellTargets.TAX_QUARTER`، `CountScope`/`CountState.options`،
  `DrawerNoSale.phoneAction`)؛ سبز: TaxDueAlertTest ۵، StockCountScopeTest ۴، CountFilterTest ۵، DrawerNoSaleTest ۷، ReportsModelTest (+۱ ِ RFM، با
  `RFM.money = false` سرخ — جهش)؛ بی `canSeeMoney` در `HomeAlerts.taxDue` هم TaxDueAlertTest سرخ (جهش). اجرای کاملِ `flock … ./desktop/gradlew --no-daemon -p desktop test`: BUILD SUCCESSFUL، ۶۳۵ مجموعه، ۴۰۴۵ آزمون، ۰ شکست (۴ رد‌شده).
- **درس:** پوشهٔ scratchpad میانِ عامل‌های هم‌زمانِ یک هماهنگ‌کننده مشترک است — نامِ لاگ را یکتا کنید (`scratchpad/<X>/…`)؛ `red1.log` ِ این عامل
  را عاملِ دیگری با همان نام بازنویسی کرد.
- **قدمِ بعدی:** پاسخِ صاحب‌کار به SYN-08 (الف/ب)؛ PAY-19 و «کشو بی فروش» ِ گوشی روی کشوی واقعی؛ شاهدِ CI پس از مرج.
### ۱۴۰۵/۰۸/۲۲ — عاملِ ک۳ (e-rows-33k) زیرِ هماهنگ‌کننده، شاخهٔ `claude/e-rows-33k` (از سرِ `claude/batch-56`، a5bd4667f)

- **دو باگِ واقعیِ صندوقِ گوشیِ نسخهٔ ۳ بسته شد (فروشِ سریع هم، چون همان برگهٔ `sale/checkout` است):** (۱) **POS-24** — `CheckoutModel.checkout`
  فیلدِ `rules` نداشت ⇒ «مشتری لازم از مبلغِ X» و «فاکتورِ صفر رد» ِ `CheckoutRulesPref` در گوشی هرگز سنجیده نمی‌شد؛ حالا همان `Checkout` ِ برگه
  (جمع، پیش‌سنجیِ حساب‌پی و ثبت) قاعده‌ها را دارد و پیامِ رد بالای برگه می‌آید. (۲) **S22** — برگه فقط `blockExpired` را می‌گذاشت ⇒ با «با مجوزِ مدیر»
  فروشِ منقضی بی تأیید ثبت می‌شد؛ حالا `ExpiryPref.gate` ⇐ `Checkout.expiryGate`، پیش از ثبت و پیش از گرفتنِ پولِ حساب‌پی `CheckoutExpiry.uncovered`
  ⇐ برگهٔ `sale/checkout/expiry` (همان `CreditApprovalSheet` با پارامترِ تازهٔ `title`) ⇐ `CheckoutSession.expiryApproval`؛ تأیید فقط همان کالاها و
  تا همان مقدار (`ExpiryGate.covers`)، سبدِ بزرگ‌تر دوباره می‌پرسد و با هر پرداخت پاک می‌شود. کنارش: ردیفِ «کالای منقضی» ِ فهرستِ تنظیمات
  «با مجوزِ مدیر» را «فقط هشدار» می‌نوشت (`SettingsIndexModel`).
- **آزمون:** `CheckoutRulesWiringTest` (۴) و `CheckoutExpiryWiringTest` (۵) — سیم‌کشی روی کدِ پیشین ۴ از ۵ سرخ (پنجمی نگهبانِ «فقط `CheckoutModel`
  در صندوقِ v3 `Checkout` می‌سازد»)، رفتار با امضای تازه؛ `CheckoutGuardsPrefDesktopTest` (۳، مقدارِ واقعیِ `tohid-policy` روی `DesktopContext`)؛
  `SettingsModelsTest`؛ دو ردیفِ `V3ReachabilityTest`. اجرای کاملِ `./desktop/gradlew -p desktop test` (۴۰۴۵ آزمون، ۰ شکست، ۴ SKIP ِ پیشین)؛
  اجرای کاملِ اول یک شکست داشت: `LineSellerFlowTest` متنِ `.copy(lineSellers = lineSellers, tax = taxConfig)` را می‌پاید ⇒ تأییدِ منقضی در `copy` ِ جدا.
  STATUS ِ POS-24 و S22 به‌روز (IMPLEMENTED-VERIFIED می‌مانند)؛ قاعدهٔ S22 در RULES «فروش و صندوق».
- **قدمِ بعدی:** دیدنِ برگهٔ تأییدِ منقضی روی گوشیِ واقعی (سنجیده نشد: فقط آزمونِ JVM)؛ `ScenarioS22Test` همچنان خودش `Checkout(expiryGate = true)`
  می‌سازد — سیم‌کشی را آزمون‌های تازه می‌پایند.
### ۱۴۰۵/۰۸/۲۲ — عاملِ e-rows-32a زیرِ هماهنگ‌کننده، شاخهٔ `claude/e-rows-32a` (از سرِ `claude/batch-56`)

- **ساخته شد — AI-11 «جست‌وجو با عکس» در گوشیِ نسخهٔ ۳** (یافتهٔ ۲۸l): `VisualSearchEntry` به `rememberVisualSearch` (null با پرچمِ خاموش
  یا بی حساب) + `VisualSearchHost` شکست (کامپیوتر همان دکمه). صندوقِ گوشی: ردیف در «…» (`SaleMenuSheet`، `SaleRoute` ⇐ `SaleScreen`)؛
  فروشِ سریع: دکمهٔ دوربینِ سربرگ؛ «کالاها»: «…» ِ سربرگ (`V3Scaffold` فقط دو دکمه می‌گیرد). «مشابه» در صندوق فقط کادرِ جست‌وجو با دستهٔ
  «همه»، در «کالاها» بازکردنِ کالا. همان درها (`/api/shop/visual-search`) و همان پرچم. **باگِ واقعیِ تازه (کامپیوتر هم):** نامِ آغاز با رقم
  («3x3 کابل») در کادرِ صندوق پیشوندِ شمارِ POS-03 خوانده می‌شد ⇒ لمسِ بعدی سه تا؛ `VisualSearch.tillText` «1*» ِ صریح می‌گذارد.
  ردیف در «دستگاه» می‌ماند (آستانه روی عکسِ واقعی).
- **ساخته شد — AUTH-08 گامِ کدِ شمارهٔ تازه در نسخهٔ ۳:** «ویرایش حساب و دکان» (`ProfileEditScreen`) کادرِ کد زیرِ شماره، «ارسال دوبارهٔ کد» با
  شمارشِ `resendSeconds` ِ پاسخِ سرور و خطای درجا (ناقص/نادرست/کهنه)؛ حالِ کادر در `core/account/PhoneClaimForm` (commonMain، کد بسته به شماره،
  کدِ ناقص ⇒ هیچ درخواست، سقف‌ها فقط سرور)؛ `requestPhoneCode` ⇐ `ApiResult<Int>`؛ `V3TextButton(enabled)`. درِ سرور بی تغییر (فقط توضیحِ
  `me.js`). `ui/screens/PhoneClaimStep.kt` هیچ صدازننده‌ای نداشت (`Profile.kt` فقط دو state ِ بی‌استفاده) ⇒ برداشته؛ استنادِ STATUS/`StrShellPages`
  به پروندهٔ تازه. ردیف در «پرچم» با ماندهٔ تازه: `auth.phone_add_code` (scope `security`، سراسری) پس از رسیدنِ این نسخه به همهٔ دستگاه‌ها.
- **آزمون:** سرخ پیش: `VisualSearchTest` ۶/۲ سرخ روی کدِ پیشین («3x3 کابل expected 1.0 but was 3.0» و سیم)؛ `PhoneClaimFormTest` سرخِ کامپایل.
  سبز پس: `PhoneClaimFormTest` ۷/۷، `PhoneClaimTest` ۵/۵؛ ۱۲ کلاسِ هدف‌دار ۹۱ آزمون (`VisualSearchTest` ۶، `ProfileEditModelTest` ۵، `DariCoverageTest` ۲۳،
  `PreviewPagesTextTest` ۴، `I18nCoverageTest` ۸، `DariWordsTest` ۱۷، `NoLegacyUiTest` ۵، `V3ReachabilityTest` ۹، `PhoneTillWiringTest` ۴ …) با یک سرخ:
  «مهلت» ِ فقط‌فارسی در دری ⇐ «وقت». جهش: هسته ۴ از ۴ (`phone`، `codeToSend`، کدِ مرده، `phone_code_disabled`)، صفحه/مدل ۴ از ۴ (بی «1*»،
  بی `picked = null`، پیامِ جابه‌جا، بی `form.phone(it)`). سرور `phone-add-code` ۷/۷، `visual-search` ۹/۹،
  `dari-text` + نامِ آزمون ۲۰/۲۰ (PGlite محلی). اجرای کاملِ `./desktop/gradlew -p desktop test` (با `flock`) **۴٬۰۳۳ PASS، ۰ FAIL، ۴ SKIP ِ پیشین** (۶۳۳ کلاس). `quality.mjs` بی ❌، precheck ‏fail=0، spec «سالم»، `generate.cjs` تازه.
- **قدمِ بعدی:** دو بی‌سیمِ دیگرِ ۲۸l (OPS-02 «پاک‌سازی کامل»، S45 قاعده/تأییدِ قیمتِ ردیف)؛ برگه‌های عکس و «مشابه» هنوز شکلِ کیتِ قدیم
  (`ParcelPhotoSource`/`TohidBottomSheet`) — بازسازی با `V3Sheet` اختیاری؛ گوشیِ واقعی برای هر دو سنجیده نشد.

### ۱۴۰۵/۰۸/۲۲ — عاملِ e-rows-28l زیرِ هماهنگ‌کننده، شاخهٔ `claude/e-rows-28l` (از سرِ `claude/batch-55`)

- **ساخته شد:** LOC-06 دورِ ۳ — متنِ خامِ `ui/screens` **۱۷۸ ⇐ ۷۳** (`DariCoverageTest.maxRawOldScreens`). (ب) ۷۴ متنِ صفحه‌های فقط‌پیش‌نمایشِ گوشی
  (`Sale`، `QuickSale`، `Profile`، `ProfileModel`، `Settings`، `Debtors`، `DebtorDetail`، `DebtorTxSheet`، `NavEditor`، `PhoneHomeModel`، `IdleLockChoice`،
  `VoiceSearch`) به `Str*` — اول همان جملهٔ موجودِ جدول (با آرگومان و `td` هم؛ 22n فقط بی‌آرگومان/`t` را گرفته بود)، بقیه ویژگیِ تازه در `StrSalePages`/
  `StrDebtPages`/`StrShellPages`؛ (ج) `ui/screens/ProductDetail.kt` (+ `movementLabelOf`) و `ui/screens/Receipts.kt` برداشته شدند (جانشینِ نسخهٔ ۳
  دارند؛ استنادِ IMG-PRO-11 به `ui/v3/products/ProductDetailScreen` برگشت؛ «`Receipts` (PRM-13)» ِ 13t اشتباه بود — آن `print/Receipts.kt` است)؛
  `WarehouseDialogs` و `PhoneClaimStep` جانشینِ کامل ندارند ⇒ ۲۷ متنشان به `StrStockPages`/`StrShellPages`. نگهبانِ تازه `PreviewPagesTextTest` (۴).
  (الف) ۷۳ ِ `DayEndModel`/`HeatmapModel` دست نخورد ⇒ پرسشِ `OWNER-PENDING-fa.md` ۴-۳ (پیشنهادِ پیش‌فرض الف)؛ LOC-06 در نقشه از «کد» به «تصمیم».
- **آزمون‌های سیمِ فقط‌پیش‌نمایش ⇐ نسخهٔ ۳:** Gift/Layaway/Quote/SpecialOrder/TillReceivePhone ⇐ `PhoneTillTools`+`SaleRoute`؛ Loyalty/SaleNotes/
  ApprovalScope/PhoneTill ⇐ `CheckoutRoute`/`CheckoutSession`/`QuickSaleRoute`؛ PromoLive ⇐ `SafeSignOut`؛ LegacySyncCall ⇐ `SyncScreen`؛ S18 ⇐
  `CustomerPickerSheet`؛ PromoGroup/SafePanel: سطرِ پیش‌نمایش برداشته. جهش: بی `GiftCardBar`/`TillReceivePhone` در `PhoneTillTools` ⇐ دو آزمون سرخ.
- **باگ‌های واقعی (در نسخهٔ ۳ سیم ندارد؛ یادداشت در STATUS ِ همان ردیف، ساخته نشد):** (۱) **OPS-02** «پاک‌سازی کامل اطلاعات» در
  `v3/settings/sections/BackupScreen`: برای هر کسی با `settings.write` (مدیر هم)، یک برگهٔ تأییدِ ساده (نه دو مرحله با نامِ دکان) و «پاک شد» بی نگاه به
  نتیجهٔ `replaceAll` — رابطِ پیشین فقط صاحب بود؛ پرخطرترین. (۲) **S45** کادرِ ردیفِ گوشیِ نسخهٔ ۳ (`LineModel.apply`) قاعدهٔ «قیمت و تخفیفِ ردیف» و
  «درخواستِ تأیید» ندارد (ردیف IMPLEMENTED-VERIFIED است؛ وضعیتش بازبینی شود). (۳) **AI-11** «جست‌وجو با عکس» در گوشیِ نسخهٔ ۳ نیست. (۴) **AUTH-08**
  گامِ کدِ شمارهٔ تازه در نسخهٔ ۳ نیست (پیش از روشن کردنِ `auth.phone_add_code`).
- **قدمِ بعدی:** ساختنِ چهار سیمِ بالا در نسخهٔ ۳ (اول OPS-02)؛ پاسخِ ۴-۳ ⇐ ۷۳ ِ مانده؛ سپس پیش‌نمایشِ DevShot به نسخهٔ ۳ و برداشتنِ صفحه‌های قدیمی.
  «تازه‌ها» سطر نمی‌خواهد (هیچ چیزِ دیدنی برای کاربر: پیش‌نمایش، مرده و آزمون؛ `WHATSNEW_OK`).
- **آزمون:** اجرای کاملِ `./desktop/gradlew -p desktop test` (با `flock`، `test-data` ِ پاک) **۴٬۰۲۹ PASS، ۰ FAIL، ۴ SKIP ِ پیشین** (۶۳۳ کلاس)؛ هدف‌دار: ۲۳ کلاس سبز (`DariCoverageTest` ۲۳، `I18nCoverageTest` ۸، `DariWordsTest` ۱۷، `DesktopPhrasesTest` ۴، همهٔ
  آزمون‌های سیمِ جابه‌جاشده، `V3ReachabilityTest` ۹، `NoLegacyUiTest` ۵)؛ `dari-text.test.js` ۱۷/۱۷؛ نگهبانِ نامِ آزمون ۳/۳؛ `quality.mjs` بی ❌ (فقط
  سطرِ `ProductDetail.kt` ِ برداشته از خط‌مبنا)؛ precheck ‏fail=0؛ spec «سالم».

### ۱۴۰۵/۰۸/۲۲ — عاملِ e-rows-30r زیرِ هماهنگ‌کننده، شاخهٔ `claude/e-rows-30r` (از سرِ `claude/batch-55-m`) — اصلاحِ بازبینیِ امنیتیِ دستهٔ ۵۵

- **شد:** (۱) `else` ِ سرگردانِ `store/checkout.js`: نشانیِ ذخیره‌شدهٔ بی پین در دکانِ ناحیه‌دار ۴۰۰ `zone_required` می‌گرفت؛ (۲) رسانهٔ
  نشانیِ ذخیره‌شده فقط برای سفارشِ باز (`address-media.FINISHED` از ماشینِ وضعیت) و `CLOSED` ِ مشتری کامل؛ (۳) حذفِ نشانی/حساب شیءِ
  انبار را هم برمی‌دارد و PUT پشتِ قفلِ نشانی؛ (۴) JPEG تا EOI؛ (۵-۷) نمای کم‌ترِ اعتماد در پرونده و صف بی هم‌متن/مدرک/تصمیم‌گیرنده؛
  (۸) کلیدِ تکراریِ پرچم (CI ِ #1041)؛ (۹) نامِ پنل با نیم‌فاصله، بی Tags/Zalgo؛ (۱۰) یادداشتِ صوتی فقط صدا و بی اتمِ مکانِ M4A؛
  توکن پیش از بدنه؛ کرانِ پایگاه در ۸۴۷/۸۵۵. هر کدام آزمونِ سرخ‌پیش/سبزپس؛ جهش‌آزمایی روی (۱) و (۲).
- **نیمه‌کاره/تصمیم:** برچسب‌های دیگرِ صدا (`©nam`، ID3) پاک نمی‌شوند (STATUS ِ CHK-08)؛ سه «پیش‌فرض؛ صاحب‌کار تأیید کند» در STATUS:
  متنِ شاکی برای بیننده (ABUSE-01)، پیوندِ «دریافت از دکان» (CRM-09)، `grantForDossier` (TEN-04).
- **قدمِ بعدی:** مرجِ همین شاخه در دسته؛ تصمیمِ صاحب‌کار روی سه پیش‌فرض.
### ۱۴۰۵/۰۸/۲۲ — عاملِ e-rows-26c زیرِ هماهنگ‌کننده، شاخهٔ `claude/e-rows-26c` (از سرِ `claude/batch-55`)

- **CRM-09 (افزودن از برنامهٔ خریدِ بومی) ⇐ «پرچم»:** برنامهٔ خرید حسابِ مشتری دارد ⇒ راهِ حساب، نه درِ سفارش. `core/shopper/ShopperAddressSync.kt`:
  دفترِ نشانیِ گوشی ⇄ دفترِ حساب با یک درخواست در هر گام (DELETE ِ پاک‌شده‌ها ⇐ PUT ِ ویرایش‌های نرفته ⇐ GET ⇐ POST ِ فقط‌دستگاهی‌ها)، هر پاسخ
  روی حالِ **اکنون** (ویرایشِ میانهٔ دور گم نمی‌شود)، همانند دوباره فرستاده نمی‌شود، سرور مرجع، خروج ⇒ نشانی‌های حساب از گوشی. شناسهٔ
  `adr-…` یعنی فقط‌دستگاهی (حالتِ تازه فقط `ShopperState.addressGone` و `SavedAddress.dirty/media`). رسانه با همان درهای حساب
  (`/account/addresses/:id/media/:kind`) در `AddressMediaCard.kt`/`ModelAddressSync.kt`؛ ثبت با نشانیِ حساب ⇒ `savedId` + نشست ⇒
  `linkOrder` ِ سرور. **سرورِ تازه‌ای لازم نشد.**
- **CHK-08 (نقشه در برنامهٔ خرید) ⇐ «پرچم»:** MapLibre در کش نیست ⇒ «کاشیِ ثابت + پین» ِ ویترین در Compose (`ShopperPinMap.kt` + `PinMapView.kt`،
  `Platform.tile`: `MapTilesAndroid.kt` و `ImagesIos.Tile`)؛ بردارِ مشترکِ `core/test-vectors/pin-map-v1.json` از خودِ `store/pin-map.js`
  (`server/test/store-pin-map.test.js` ۳/۳ و `PinMapVectorsTest` ۳/۳).
- **آزمون‌ها:** core ‏`ir.vil3ntec.tohid.core.shopper.*` ۱۵۳ PASS (تازه `ShopperAddressSyncTest` ۱۲، `PinMapVectorsTest` ۳، `PlaceTextCoverageTest` ۲؛
  جهش‌آزمایی ۲ از ۲ سرخ)؛ `customer-android` (صفحه‌ها و مدلِ مشترک) روی Compose Desktop ِ محلی کامپایل و ۳۳/۳۳ (تازه `CustomerAddressSyncTest`)؛
  سرور `store-pin-map` ۳، `store-map` ۳، `store-pin-text` ۳، `web-i18n` ۳۵، `dari-text` ۱۷، `address-media-door` ۲، `store-address-media` ۵،
  `kotlin-test-names-windows` ۲؛ اجرای کاملِ `flock /tmp/gradle-full.lock ./desktop/gradlew -p desktop test` (‎-Xmx3g، `desktop/build/test-data` ِ پاک) ⇒
  ۴۰۲۵ PASS، ۰ FAIL، ۴ SKIP ِ پیشین؛ precheck ⇒ `fail=0`؛ `quality.mjs` بی ❌. کامپایلِ اندروید (`MapTilesAndroid.kt`، `MainActivity.kt`) و آیفون فقط CI.
- **دام:** (۱) `CustomerModel.kt` درست ۸۰۰ خط است ⇒ هر سیمِ تازه در پروندهٔ جدا (`ModelAddressSync.kt`) و در خودِ مدل فقط جایگزینیِ هم‌خط با نامِ
  کامل (`ir.vil3ntec…AddressSync`). (۲) `ShopperText.kt` ۷۹۹ خط ⇒ نوشته‌های تازه در `PlaceText` (`ShopperPlaceText.kt`) با آزمونِ پوششِ خودش.
  (۳) قابِ نقشه در فارسی آینه می‌شود ⇒ `LayoutDirection.Ltr` و `absoluteOffset` (جهت‌های نقشه فیزیکی‌اند). (۴) `ensureStore` همان دکان را دوباره
  وارد نمی‌شود ⇒ تازه کردنِ دفترِ حساب با باز شدنِ «نشانی‌های من» هم.
- **قدمِ بعد:** دیدنِ CI ِ اندروید/آیفون برای پرونده‌های تازه؛ روشن کردنِ `shop.storefront.address_media` و `shop.storefront.map_pin` + `STORE_MAP_TILES`
  با صاحب‌کار (FLAGS-ON §۲۳، §۳۱؛ هر دو حالا بخشِ «برنامهٔ خرید» دارند) و گوشیِ واقعی.

### ۱۴۰۵/۰۸/۲۲ — عاملِ e-rows-29o زیرِ هماهنگ‌کننده، شاخهٔ `claude/e-rows-29o` (از سرِ `claude/batch-55`) — فقط سند

- **شد:** سندِ کوتاهِ صاحب‌کار [`OWNER-NEXT-fa.md`](OWNER-NEXT-fa.md) از نقشهٔ ۱۴۰۵/۰۸/۲۲ (۲۲۷ ردیف): ۱. کلیدها ۵۶ (۲۴ کم‌خطرِ «امروز،
  ۱۵ دقیقه»، ۱۸ با هشدارِ دیسک/پول/داده، ۴ تنظیمِ سرور، ۱۰ «هنوز نه»)؛ ۲. تصمیم‌ها ۵۱ (۳۱ پرسشِ باز با پیشنهادِ Claude و اثر، S89 در جدولِ
  هفت پیش‌فرضِ این دور، ۱۹ گرفته‌شده و منتظرِ زمان)؛ ۳. حساب‌ها ۳۹ (پیامک ۱۲، حساب‌پی ۱۲، Firebase ۵، سرورِ خودتان ۶، …)؛ ۴. دستگاه ۵۹ در
  ۹ جلسه؛ ۵. همگام/پمپ ۱۸؛ CI ۱ در سرِ سند. هر شناسهٔ غیرِ «کد» ِ نقشه دقیقاً یک بار در سند است و هر بخش دقیقاً همان دستهٔ نقشه را دارد
  (سنجیده با اسکریپتِ کوچکِ بیرون از ریپو). کدی عوض نشد؛ spec «سالم».
- **دام:** شناسه‌ها را در متنِ توضیحی تکرار نکنید (مثال‌ها با «‹ردیف›»)؛ `OPS-01`/`OPS-01@29` و `OPS-02`/`OPS-02@29` دو ردیفِ جدا در دو بخش‌اند.
- **قدمِ بعد:** پس از هر جابه‌جاییِ نقشه این سند هم به‌روز شود (یا کهنه اعلام شود)؛ پاسخ‌های صاحب‌کار به بخشِ ۲ ⇐ OWNER-DECISIONS و STATUS.
### ۱۴۰۵/۰۸/۲۲ — عاملِ e-rows-24u زیرِ هماهنگ‌کننده، شاخهٔ `claude/e-rows-24u` (از سرِ `claude/batch-55`)

- **ادغامِ `origin/claude/e-rows-18q` و یکی‌سازیِ رسانهٔ نشانی (CRM-09 + CHK-08):** دو عامل هم‌زمان عکسِ دروازه/یادداشتِ صوتی را
  ساخته بودند (نشانیِ ذخیره‌شدهٔ حساب، ۸۳۶؛ نشانیِ یک سفارش، ۸۴۷) با دو کتابخانه، دو سقف، دو انبار و **دو درِ هم‌مسیر با پاسخِ
  متفاوت** برای دکان و پیک (`courier.js` حتی `const addrMedia` ِ تکراری داشت). حالا `lib/address-media.js`: یک پرچم، یک سقف
  (`config.addressMedia`)، یک سنجشِ بایت (`accept`؛ EXIF/مکان برای هر دو پاک)، یک انبار (`media-store.address`، tenant از ردیف) و
  **یک در و یک نما**: `GET /api/shop/orders/:id/address-media[/:kind]` و `GET /api/courier/missions/:id/address-media[/:kind]` — برای
  هر نوع رسانهٔ سفارش اگر هست، وگرنه نشانیِ ذخیره‌شده (`source`). نقش: `orders.contact` + `requireOrdersContact` (نه `orders.view`؛
  دلیل سرِ همان پرونده). هر دو جدول و هر دو مهاجرت ماندند (۸۴۷ فقط توضیح). erasure: `addressMediaLinks` ِ تازه + آینهٔ Kotlin و برچسب.
  ویترین: `store/addr-media.js` (نشانی‌های من) و `store/order-addr-media.js` (پیگیری، `oam.*`، واژه‌نامهٔ `orderaddr` — کلیدهای `am.*`
  ِ دو پرونده در یک صفحه روی هم می‌افتادند). POS-05/INV-03 ِ همان شاخه دست نخورد.
- **CodeQL (هشدارِ PR #1041):** نگهبانِ نوعِ صریح (`typeof`/`Array.isArray`/`Buffer.isBuffer`) در `accept` و `chat-media.sniff`؛ بدنهٔ
  JSON/آرایه ⇒ ۴۰۰ `bad_media` (رشتهٔ تنها را parser ِ strict ِ برنامه ۴۰۰ `bad_json` می‌دهد)، پرسشِ تکراری بی‌اثر — هر دو آزمون دارند.
- **آزمون‌ها:** تازه `address-media-door.test.js` ۲/۲ (فقط نشانی، هر دو، فقط سفارش، هیچ؛ صاحب/مدیر بله، صندوق‌دار/شاگرد/مدیرِ
  بسته‌شده ۴۰۳، دکانِ دیگر ۴۰۴، پیکِ دیگر ۴۰۴)؛ `store-address-media` ۵/۵، `order-address-media` ۴/۴، `catalog-reserved` ۴/۴،
  `customer-erasure` ۶/۶، `tenant-matrix` ۴/۴، `card-data` ۱۱/۱۱، `api-contract` ۲/۲، `web-i18n` ۳۵/۳۵، `dari-text` ۱۷/۱۷،
  `shop-route-inventory` ۲/۲، `image-store-migrate` ۵/۵، `media-store` ۵/۵، `shop-chat` ۱۷/۱۷، `courier-scope` ۱۲/۱۲؛ کرومیوم
  `web-store-addr-media` ۲۸ ✅، `web-store-acct` ۲۷ ✅، `web-store` ۱۳۵ ✅ (بارِ نخست ۷۲٬۶۳۶ بایت زیرِ سقفِ ۷۲٬۷۰۴، دست نخورد)؛ core
  `OrderAddressMediaTest` ۴/۴ و `OnlineHoldsTest` ۹/۹. Kotlin ِ هدف‌دار (`./desktop/gradlew -p desktop test --tests …`): `HeldStockTest` ۵، `HeldSyncTest` ۷،
  `PosSellableTileUiTest` ۲، `ServerFeatureScreensTest` ۱۱ (آینهٔ `COUNT_KEYS`)، `DariCoverageTest` ۲۳ (برچسبِ تازهٔ `StrErasure`)،
  `DataSafetyGuardTest` ۱۰ — ۵۸ PASS، ۰ FAIL. اجرای کاملِ `flock /tmp/gradle-full.lock ./desktop/gradlew -p desktop test`
  (‎-Xmx3g، `desktop/build/test-data` ِ پاک) ⇒ ۴۰۱۶ PASS، ۰ FAIL، ۴ SKIP ِ پیشین؛ precheck ⇒ `fail=0`؛ `quality.mjs` بی ❌.
- **سرخیِ CI ِ #1041 (`web-store-acct.mjs`):** پیش‌نمایشِ عکسِ «نشانی‌های من» ناهمگام می‌آید (GET ⇐ FileReader) ⇒ آزمون حالا منتظرِ
  دیده شدن می‌ماند؛ «پاک کردن» ِ عکس ⇒ `#am-thumb[hidden]` با `display:none` (آزمونِ تازه) و پاسخِ دیرِ پیش‌نمایش پس از پاک کردن عکس را
  برنمی‌گرداند. `.am-thumb[hidden]` در `store.css` افزوده شد، ولی خودِ `kit.css` از پیش `[hidden] { display:none !important }` دارد ⇒
  بی آن هم پنهان بود (آزمون روی پایه سبز ماند؛ فقط زمان‌بندی سرخ بود). کرومیوم `web-store-acct.mjs` ۲۹ ✅.
- **دام:** (۱) `K.part` کلیدِ موجود را بازنویسی نمی‌کند ⇒ دو واژه‌نامهٔ دیربار با پیشوندِ یکسان در یک صفحه بی‌صدا متنِ اولی را
  نشان می‌دهند؛ پیشوندِ هر بخش یکتا باشد. (۲) حسابِ ویترین سراسری است (یک شماره ⇒ یک حساب در همهٔ آزمون‌های یک پرونده)؛
  شمارشِ ردیف‌های حساب را نسبی بسنجید. (۳) JPEG ِ ساختگیِ `ff d8 ff e0 + پُرکننده` از `cleanImage` ۴۰۰ می‌گیرد؛ از `image-fixtures.jpeg`.
- **قدمِ بعد:** CHK-08 فقط نقشه در برنامهٔ خرید؛ CRM-09 افزودن از برنامهٔ خریدِ بومی (دفترِ نشانیِ حساب در برنامه لازم است)؛
  سپس پرچمِ `shop.storefront.address_media` با صاحب‌کار (FLAGS-ON §۳۱).
### ۱۴۰۵/۰۸/۲۲ — عاملِ e-rows-18q، شاخهٔ `claude/e-rows-18q` (از سرِ `claude/batch-54`، سپس ادغامِ سرِ تازهٔ آن)

- **POS-05 و INV-03 ⇐ IMPLEMENTED-VERIFIED:** درِ سرورِ `GET /api/shop/catalog/reserved` (`stock-reserve.posView`: نگه‌داشتهٔ نرمِ فعال
  با کلیدِ فروشِ تحویلِ هر قلم `o-<سفارش>-<قلم>`؛ `stock.view` + `orders.view`، سقفِ ۳۰/دقیقه هر کاربر؛ پرچمِ خاموشِ
  `shop.stock_reserve` ⇒ `enabled:false`) — `catalog-reserved.test.js` ۴/۴ (پایه ۰/۴). صندوق: `core/stock/OnlineHolds.kt` (مهلت با
  ساعتِ سرور، فروشِ تحویلِ رسیده دو بار نه، نمای کهنه‌تر از یک روز یا ساعتِ برگشته ⇒ هیچ) + `OnlineHoldsStore`
  (`data/OnlineHoldsAndroid.kt`) + `rememberSellableHeld` (`ui/components/pos/OnlineHoldsAndroid.kt`) در `DeskSale` و `SaleRoute`
  ⇒ `HeldStock.sellable`. `OnlineHoldsTest` ۹/۹ (core)، `HeldStockTest` ۵/۵، `HeldSyncTest` ۷/۷، `PosSellableTileUiTest` ۲/۲ (صفحهٔ خودِ کاشی، محلی)؛ اجرای کاملِ desktop ۴۰۰۳ آزمون، ۰ شکست، ۴ SKIP ِ پیشینِ بنچ/عکس. نگه‌داشته‌های دستگاهِ دیگر = `shop.held_sync` (سنجشِ تازه در `HeldSyncTest`).
- **CHK-08 (PARTIAL، کد):** سرور (مهاجرتِ ۸۴۷، `lib/order-address-media.js`، درهای مشتری/کارمند/پیک، حذفِ دادهٔ مشتری،
  `image-store-move.TABLES`؛ پرچمِ `shop.storefront.address_media`) — `order-address-media.test.js` ۴/۴؛ فرمِ ویترین روی صفحهٔ پیگیری
  (`store/addr-media.js` ِ دیربار، واژه‌نامهٔ `addrmedia`) — `web-store-addr-media.mjs` ۲۲ ✅ (در زنجیرهٔ `test:web-store`).
- **قدمِ بعد:** به گفتهٔ هماهنگ‌کننده CHK-08 این‌جا گسترش نیافت: عاملِ «ت» (`claude/e-rows-13t`، CRM-09) همین را برای نشانیِ
  ذخیره‌شدهٔ حساب ساخته (`store_address_media`، مهاجرتِ ۸۳۶، پیک هم می‌بیند) ⇒ پس از ادغام دو جدول/در یکی شوند (هر دو `chat-media.sniff`).
  سپس نمایشِ عکس/صدای نشانیِ سفارش در جزئیاتِ سفارشِ برنامهٔ دکان و مأموریتِ پیک (درها آماده‌اند) و نقشه در برنامهٔ خرید (منطق در core،
  اندروید فقط CI).
- **درس:** هر انبارِ `getSharedPreferences` ِ تازه باید در `DATA-SAFETY-fa.md` §۶ و `DataSafetyGuardTest.CLASSIFIED` رده بگیرد
  (`tohid-online-holds` ⇐ «نسخهٔ سرور»)؛ کلیدِ تازهٔ `COUNT_KEYS` ِ حذفِ داده هم در `CustomerErasure.kt` و `StrErasure.kt`.
### ۱۴۰۵/۰۸/۲۲ — عاملِ e-rows-21m زیرِ هماهنگ‌کننده، شاخهٔ `claude/e-rows-21m` (از `claude/batch-55`)

- **TEN-04 (همچنان PARTIAL، «کد»):** درهای خصوصیِ دیگرِ مدیر زیرِ نگهبانِ اجازه، پشتِ همان پرچمِ خاموشِ `security / admin.support_grant`:
  نقش و دستگاهِ کارکنان (`staff-perms`) و تعارض‌های همگام‌سازیِ دکان بی اجازهٔ زندهٔ `read_private` ⇒ ‎403 grant_required‎؛ بازگرداندنِ تعارضِ
  دکان ⇒ اجازهٔ `restore`؛ پرونده‌های دکان و کاربرِ عضوِ دکان بی اجازه فقط فراداده با `restricted` (`lib/admin-dossier.js`؛ ۴۰۳ نه، چون مالی
  اجازه نمی‌گیرد و پرونده جای درخواستِ اجازه است). کنار با دلیل: گفت‌وگوی پشتیبانی، پیش‌نمایشِ حذف، فهرستِ دسته‌های عکس (تصمیمِ آزموده‌شدهٔ
  `admin-image-changes.test.js` — نخست بستمش و همان آزمون سرخ شد، برگرداندم). ماتریسِ `tenant-matrix` حالا ردهٔ اجازهٔ هر درِ سابقه‌دار را
  می‌خواهد و «TEN-04 (اجازه)» با پرچمِ روشن همهٔ درها را می‌سنجد (روی کدِ پیشین ۸ خطا)؛ `support-grant-read` ۵/۵ (PGlite و PostgreSQL ۱۶)، جهش ۱۱ از ۱۱.
  **مانده:** پنلِ مرکزی (ریپوی server، توانایی `support-grant-read.v1`) پیش از روشن کردنِ پرچم؛ پروندهٔ پمپ (#615)؛ دامنهٔ اجازه برای نوشتنِ
  نقش/دستگاهِ کارکنان از پنل (هیچ‌کدام از چهار دامنه نیست ⇐ تصمیم).
- **GATE-NEW-02 ⇐ IMPLEMENTED-VERIFIED:** `server/test/catalog-stock-page.test.js` سناریوهای INV را از صفحهٔ کالا، ویترینِ عمومی و سفارشِ مشتری
  می‌برد و هر کدام با تطبیقِ دفتر تمام می‌شود (۱۴/۱۴، PostgreSQL ۱۶ هم؛ جهش ۳ از ۳)؛ پاسخِ زندهٔ صفحهٔ کالا در
  `core/test-vectors/product-page-inv-v1.json` و `ProductStorefrontInvVectorsTest` (۱/۱، جهشِ زیرنویسِ «غیرفعال» کشته) همان را با کلاینتِ واقعی
  می‌خواند. یافته: نشانِ ویترین پس از فروشِ دستگاه حداکثر یک `REFRESH_THROTTLE_MS` دیر است ولی سفارش همان دم رد می‌شود (طراحی، نه باگ).
  اجرای کاملِ `./desktop/gradlew -p desktop test` (با `flock`): ۴۰۰۵ گذشت، ۰ شکست (۴ کنار ِ همیشگی)؛ ماتریسِ TEN-04 روی PostgreSQL ۱۶ هم سبز.
- **قدمِ بعد:** TEN-04 در ریپوی server (نمایشِ ۴۰۳ و `restricted`، درخواستِ دامنه‌های `read_private`/`restore`)؛ تصمیمِ دامنهٔ نوشتن‌های کارکنان.

### ۱۴۰۵/۰۸/۲۲ — عاملِ e-rows-22n زیرِ هماهنگ‌کننده، شاخهٔ `claude/e-rows-22n` (از `claude/batch-55`)

- **ساخته شد:** LOC-06 — متنِ خامِ `ui/screens` **۱۴۷۰ ⇐ ۱۷۸** (`DariCoverageTest.maxRawOldScreens`). سنجشِ اعلان‌به‌اعلانِ «زنده» (ریشه: هر
  پروندهٔ بیرون از `ui/screens` در اندروید، کامپیوتر، آیفون، هسته و آزمون؛ نامِ هم‌نامِ نسخهٔ ۳ فقط با همان بسته یا import) نشان داد «۱۴۷۰ متنِ
  زنده» درست نبود: ~۴۲۰ زنده (۲۹۳ تا داده)، ~۲۳۰ فقط در پیش‌نمایشِ گوشیِ DevShot و ~۸۱۵ در کدِ مرده. (۱) ۳۵۴ داده «// داده:» گرفت (کلیدهای بخشِ
  تنظیمات، واژه‌های سه‌زبانهٔ `SettingsSearchIndex.KEYWORDS`)؛ (۲) ۶۸۹ با کدِ مرده برداشته شد (`DeliveryCash.kt`، `Devices.kt` و بخشِ مردهٔ `Orders`،
  `Reports`، `Vip`، `DeliveryDesk`، `DeliveryDialogs`، `StorefrontOwnerSettings`، `OwnerReportSettings`، `StockBook`، `SettingsParts`؛ همه جانشینِ نسخهٔ
  ۳ دارند) و پنج آزمونِ سیم‌کشی که متنِ همان صفحه‌های مرده را می‌سنجیدند به پرونده‌های نسخهٔ ۳ رفتند؛ (۳) ۲۴۹ متن که عیناً فارسیِ ویژگیِ بی‌آرگومانِ
  جدولی بود به همان ویژگی رفت (فارسی بایت‌به‌بایت همان؛ `const`، enum و سرستونِ دوپهلو نه).
- **نیمه‌کاره:** ۱۷۸ مانده: ستون‌های `DayEndModel`/`HeatmapModel` (۷۳؛ صفحه و خروجیِ اکسل با هم ⇐ تصمیمِ صاحب‌کار)، صفحه‌های فقط‌پیش‌نمایش
  (`Sale`، `QuickSale`، `Profile`، `Settings`، `Debtors`… ۷۴، با `ProfileModel`/`PhoneHomeModel` ِ فقط‌آزمون) و مرده‌های نگه‌داشته برای نسخهٔ ۳
  (`WarehouseDialogs`، `PhoneClaimStep`، `ProductDetail`، `Receipts`؛ ۳۱) — ردیف PARTIAL.
- **باگ/دام:** (۱) `ui/screens` و `ui/v3` نام‌های هم‌نام دارند (`CouriersTab`، `OrdersScreen`، `ReportsScreen`، `SaleScreen`، `QuickSaleScreen`…) ⇒ grep ِ
  نام «زنده» نشان می‌دهد؛ بسته/import را ببینید. (۲) پیش‌نمایشِ گوشیِ `DevShot` (`PhonePreview`، `PhoneV3CartPreview`) هنوز صفحه‌های قدیمیِ بی‌راه را
  عکس می‌گیرد و چند آزمونِ سیم‌کشی (`GiftCardWiringTest`، `LayawayWiringTest`، `QuoteWiringTest`، `SpecialOrderWiringTest`، `LoyaltyTillWiringTest`…)
  سیمِ همان `Sale.kt`/`QuickSale.kt`/`Settings.kt` را می‌سنجند — سبزیِ آن‌ها دربارهٔ برنامهٔ واقعی چیزی نمی‌گوید. (۳) `ProductDetail.kt` ِ مرده
  `movementLabelOf` ِ `Reports.kt` را می‌خواند (با آن ماند).
- **قدمِ بعدی:** تصمیمِ صاحب‌کار: سرستون‌های گزارشِ پایانِ روز و نقشهٔ ساعت در خروجیِ اکسل هم به زبانِ صفحه بروند؟ سپس جدا کردنِ برچسبِ صفحه از
  خروجی؛ پیش‌نمایشِ DevShot به صفحه‌های نسخهٔ ۳ و برداشتنِ صفحه‌های قدیمی (و آزمون‌های سیمِ آن‌ها)؛ مرده‌های نگه‌داشته پس از جای‌گیریِ نسخهٔ ۳.
- **آزمون:** اجرای کاملِ کامپیوتر ۴٬۰۰۴ PASS، ۰ FAIL، ۴ SKIP؛ `dari-text.test.js` ۱۷/۱۷؛ `quality.mjs` بی ❌ (سقف‌ها فقط برای پرونده‌های همین کار
  پایین آمد)؛ precheck ‏fail=0؛ spec «سالم».

### ۱۴۰۵/۰۸/۲۲ — عاملِ e-rows-25s زیرِ هماهنگ‌کننده، شاخهٔ `claude/e-rows-25s` (از `claude/batch-55`) + پنل `claude/panel-rows-23p` (server#147)

- **ABUSE-01 — پنل (server، 1.50.97):** «فروشگاه ← مدیریتِ بازار ← گزارش‌ها و جریمه‌ها» کامل شد: صف با پالایش، «مسئولش من»، تصمیم/رأی/برداشتن
  با دلیل و رمز، کلیدِ پنجره، سقفِ نرخ، ردیفِ دفتر؛ بیننده/اپراتور با `trust-read.v1` فقط می‌خوانند (پنل خودش هم نمای کم‌تر می‌بُرد، چون
  راهِ پیشینِ پل نمای کامل می‌گیرد). **باگِ واقعی:** دوکلیکِ «مسئولش من» پنجرهٔ «تصمیم» را باز می‌کرد (برچسبِ دکمه کوتاه می‌شد).
- **ABUSE-01 — سرورِ حساب (این شاخه):** از راهِ کلیدِ سرویس «چه کسی» = `X-Actor-Id` (پیش‌تر شناسهٔ مشترکِ کلید برای همه)، `assigneeName`
  (مهاجرتِ ۸۵۵)؛ `trust-read.v1` با نمای کم‌تر (`lib/trust-admin-view.js`)؛ `Idempotency-Key` روی چهار نوشتن. `admin-trust-roles.test.js`
  ۵/۵ (۴ سرخ روی پایه، ۹ جهش گرفته شد). ردیف PARTIAL در «کد» می‌ماند.
- **قدمِ بعد:** دیدنِ مدرکِ پیوست در پنل (پلِ بایتِ عکسِ `GET /api/admin/trust/cases/:id/evidence/:eid`، فقط admin ِ پنل) ⇒ ردیف به «پرچم»
  (`market.trust`). اختیاری: پنل کلیدِ پنجره را با `trust-read.v1` به خودِ سرورِ حساب هم بفرستد (`makeUpstreamOnce`)؛ امروز در حافظهٔ پنل است.
### ۱۴۰۵/۰۸/۲۲ — عاملِ e-rows-20f زیرِ هماهنگ‌کننده، شاخهٔ `claude/e-rows-20f` (از `claude/batch-55`) — بازبینی و رفعِ OPS-02/PAY-08

- **شد:** بازبینیِ مستقلِ `claude/e-rows-15w` و رفعِ همهٔ یافته‌ها. PAY-08 (`core/terminal/DevicePay`): کلیدِ تازه فقط پس از
  روشن شدنِ تلاشِ باز (`Next.Run`/`Resolve`، `resolve` فقط `lookup`)، تلاشِ ردشده/لغوشده دوباره به کار نمی‌رود (`close`/`done`)،
  «پاسخ نداد» جدا از «ندیده» (`DeviceLookup`؛ پس از مهلت ⇒ بررسی، نه لغو)، یک پرداخت یک ردیف (`Recorded.Replaced`)، فقط چهار
  رقمِ کارت؛ `DevicePaySettle` کلید و ارز را می‌سنجد؛ `withRow` فقط `confirmed` با سطحِ غیرِدستی؛ `PayLevel` با `Pay.ACCOUNT`/`FX`.
  OPS-02: «فقط دفتر» ِ رمزدار (`BackupRestore.writeLedger`، `phrase-backup.js` می‌شناسد)، سنجشِ کلید و حافظه پیش از انتخابگر
  (`BackupSeal.blocked`)، سنجشِ حافظه پیش از رمز (۳×) و بازیابی (۴×) چون Conscrypt ِ اندروید کلِ ورودیِ GCM را نگه می‌دارد،
  پرچمِ `backup_seal` در `tohid-backup-device` (بیرون از پشتیبان/انتقالِ اندروید، با مهاجرت)، شکستِ پشتیبانِ پوشه دیدنی
  (`BackupDevice.mirrorError`)؛ shim ِ `Runtime` برای آیفون.
- **آزمون:** همه سرخ پیش (جهش یا اجرای کدِ پیشین) و سبز پس — `DevicePayTest` ۱۳/۱۳، `BackupSealDrillTest` ۱۰/۱۰،
  `DevicePaySettleTest` ۵/۵، `DeviceOnlyPrefsTest` ۵/۵، `phrase-backup-tool.test.js` ۵/۵؛ اجرای کاملِ desktop ‏۴۰۱۶ آزمون، ۰ شکست
  (۴ ردشده)؛ precheck `fail=0`، `quality.mjs` بی ❌، `spec.mjs check` سالم.
- **نیمه‌کاره/قدمِ بعدی:** پاکتِ تکه‌ایِ نسخهٔ ۲ (STREAM) عمداً ساخته نشد (STATUS ِ OPS-02)؛ ساختِ klib ِ آیفون این‌جا سنجیده نشد
  (shim ِ تازهٔ `RuntimeShim.kt` با `NSProcessInfo.physicalMemory`) ⇒ CI ِ آیفون ببیند. ردیف‌ها همان PARTIAL و همان دسته.

### ۱۴۰۵/۰۸/۲۲ — عاملِ e-rows-19s زیرِ هماهنگ‌کننده، شاخهٔ `claude/e-rows-19s` (از `claude/batch-54`)

- **S18 ⇐ دستگاه:** گامِ ۴ در پنجرهٔ واقعیِ Compose (`TillNewCustomerUiTest`، `ShopStore` ِ واقعی، زبانِ دری): کادرِ نامِ گزینشِ مشتری
  با فوکوس صفحه‌کلیدِ حروف را باز می‌کند؛ **باگِ واقعی** (یافتهٔ بازبین): دو زدنِ تندِ «مشتریِ تازه» دو مشتریِ هم‌نام می‌ساخت ⇒ `onAdd`
  حالا `suspend` و دکمه `adding` دارد (آزاد در `finally`، جز پس از ثبتِ موفق که برگه بسته می‌شود)؛ خطای ثبت زیرِ دکمه، نه toast.
- **S01 ⇐ پرچم:** `ScenarioS01DesktopTest` نخستین آزمونی است که خودِ `DeskSaleScreen` را در پنجرهٔ Compose می‌کشد (۲۰ هزار کالا، چاپگرِ
  ساختگیِ `net:127.0.0.1`). **دام‌ها:** (۱) توزیع‌گرِ آزمون بی‌صف است ⇒ دو منتظرِ نخستین چیدمانِ یک `LazyGridState` در `AwaitFirstLayoutModifier`
  ِ Compose 1.9.3 ‏IOOBE می‌دهند؛ `DeskSale` دیگر در بارِ نخست (و برای فهرستِ پنهان) `scrollToItem(0)` را منتظر نمی‌ماند — از خواندنِ کد،
  نمای جدولی پیش‌تر با عوض کردنِ دسته به سر نمی‌رفت (بی آزمون). (۲) پنجرهٔ آزمون فوکوسِ آغازین (کادرِ اسکن، کادرِ نقدِ پنل) را تا نخستین زدن
  نمی‌دهد ⇒ آزمون یک بار می‌زند؛ بازگشتِ فوکوس پس از فروش همان `refocus()` است. (۳) نوشتنِ فروش روی دفترِ ۲۰ هزار کالایی زیرِ بارِ این
  کانتینر ۴٫۶ تا ۱۵ ثانیه تا پاک شدنِ سبد طول کشید (همان `docs/PERF-LEDGER-fa.md`؛ کدِ همگام‌سازی دست نخورد).
- **S96 ⇐ دستگاه:** `ScenarioS96Test` یک روزِ کاملِ دکانِ بی کامپیوتر را روی همان موتورهای صفحه‌های گوشی پشتِ سرِ هم می‌گرداند (کالا و انبار،
  فروشِ نقد با دوربین، مشتریِ تازه و قرض، سفارشِ آنلاینِ «مشتری گرفت» یک بار، رسیدِ بخشی، گزارشِ روز و موجودی)؛ سمتِ سرورِ سفارش با
  `node test/e2e/run.mjs stock` (PGlite) ۸/۸. بی تغییرِ کد. مانده فقط گوشیِ واقعی (دوربین، چاپگرِ بلوتوث، صفحهٔ کوچک).
- **اجرای کامل:** `flock /tmp/gradle-full.lock ./desktop/gradlew -p desktop test` روی `08b187964` ⇒ ۳۹۸۱ PASS، ۰ FAIL، ۴ SKIP ِ شناخته
  (ACL ِ ویندوز، دو بنچِ دستی، عکس‌ها)؛ precheck ⇒ `fail=0`؛ `quality.mjs` بی ❌ (`SaleDialogs.kt` با یک خطِ ادغام‌شده زیرِ سقفِ ۱۲۲۱).
- **نیمه‌کاره / قدمِ بعد:** هر سه ردیف فقط صاحب‌کار: S18 و S96 دستگاه، S01 پرچمِ `payV2` + کامپیوترِ مرجع. پیشنهاد: آزمون‌های تازهٔ صندوق
  `ScenarioS01DesktopTest` را الگو بگیرند (`DeskSaleScreen` در پنجرهٔ Compose با دیسکِ آزمون و چاپگرِ `net:127.0.0.1`).
### ۱۴۰۵/۰۸/۲۲ — عاملِ e-rows-13t زیرِ هماهنگ‌کننده، شاخهٔ `claude/e-rows-13t` (از `claude/e-rows-11p`)

- **ساخته شد:** RPT-14 — تپشِ بالای قیف از برنامهٔ خرید (`core/shopper/ShopperFunnel` + `customer-android/…/ModelFunnel.kt`؛
  `ShopperFunnelTest` ۴/۴، جهش ۴ از ۴؛ `CustomerFunnelTest` فقط CI) ⇒ «دستگاه». CRM-09 — عکسِ دروازه و یادداشتِ صوتیِ نشانیِ
  حسابِ مشتری (مهاجرتِ ۸۳۶، `lib/store-address-media.js`، `store/addr-media.js`، `OrderAddressMediaAndroid.kt`؛ پرچمِ خاموشِ
  `shop.storefront.address_media`؛ `store-address-media` ۴/۴ با جهش ۶ از ۶، کرومیوم `web-store-acct.mjs` ۲۷ ✅؛ پیکِ همان مأموریت هم می‌بیند) — مانده:
  افزودن از برنامهٔ خریدِ بومی (همان ماندهٔ CHK-08؛ یک کار). LOC-06 — ۱۷ صفحهٔ تمام‌مرده و بخشِ مردهٔ `Storefront.kt` برداشته شد، سقفِ متنِ خامِ
  `ui/screens` ۱۸۳۱ ⇐ ۱۴۷۰. CRM-06 — بی کدِ تازه: قالبِ یادآوری از پیش با `ShopSettingsSync` همگام می‌شد (کلیدِ مالِ دکان)؛ آزمونش
  افزوده شد؛ دسته‌اش با عاملِ «ق».
- **بی کد (دسته درست شد):** HW-07 ⇐ «تصمیم» (۴-۲۴ ج)، DLV-05 ⇐ «تصمیم» (نقشه‌کشِ برداری در کش نیست)، SEC-02 ⇐ «همگام/پمپ»
  (دیسک و دورِ زنجیره در `sync/platform/DocChainSync.kt`). RSK-04 به عاملِ «و» رفت (کدی از این شاخه ندارد).
- **باگ/دام:** (۱) CSP ِ ویترین `img-src` ِ blob: ندارد ⇒ هر کدِ وب که `URL.createObjectURL` را به `<img>`/`Image` می‌دهد بی‌صدا
  می‌شکند؛ از data: بخوانید. (۲) `customers.auth` ِ ویترین `{customer}` برمی‌گرداند نه خودِ مشتری. (۳) «پرونده‌ای مرده» یعنی هیچ
  پروندهٔ ماندهٔ دیگری (حتی مرده) از آن نخواند — `Sparks.kt` را `Vip.kt` ِ نیمه‌مرده می‌خواند. (۴) `git rm` ِ زودهنگام در کامیتِ
  بعدی (RPT-14 ‏629a30100) نشست؛ کامیتِ 82fb81946 درستش کرد — `git rm` را تا کامیتِ همان ردیف نزنید. (۵) `CustomerModel.kt` روی
  سقفِ ۸۰۰ خط است: سیمِ تازه در پروندهٔ کناری (`Model*.kt`) و جایگزینیِ خط، نه افزودن.
- **اجرای کامل:** `./desktop/gradlew -p desktop test` (‎-Xmx3g، زیرِ `flock`) با `desktop/build/test-data` ِ پاک ⇒ ۳٬۹۲۷ PASS، ۰ FAIL،
  ۴ SKIP ِ پیشین. دام: (۶) با `-Xmx2g` کامپایلِ کاملِ کامپیوتر OutOfMemory داد ⇒ اجرای کامل با `-Xmx3g`؛ (۷) `desktop/build/test-data`
  (`TOHID_DATA_DIR` ِ آزمون) میانِ اجراها می‌ماند و وضعِ کهنه‌اش `SignOutNoLossDesktopTest` را سرخ کرد ⇒ پیش از اجرای کامل پاکش کنید.
  سرور: `store-address-media` ۵/۵، `shop-route-inventory` ۲/۲، `customer-erasure` ۶/۶، `api-contract` ۲/۲، `web-i18n` ۳۲/۳۲؛ کرومیوم
  `web-store-acct` ۲۷ ✅، `web-store-p1` ۱۸ ✅، `web-store-erase` ۳۲ ✅، `web-store-cart-sync` ۴۶ ✅؛ precheck ⇒ `fail=0`؛ quality بی ❌.
- **یافته برای ردیف‌های دیگر:** AUTH-08 — `PhoneClaimStep.kt` در نسخهٔ ۳ صدازننده ندارد (گامِ کدِ شمارهٔ تازه دیده نمی‌شود)؛ نوارِ
  یادآورِ مالیاتِ فصل (`TaxQuarterAndroid.kt`) هم در نسخهٔ ۳ راه ندارد؛ GATE-NEW-01 ‹صفحهٔ کهنهٔ `Storefront.kt`› حالا برداشته شده.
- **قدمِ بعد:** CRM-09/CHK-08: افزودنِ عکس/صدا از `customer-android` (همان درهای `/:slug/account/addresses/:id/media/:kind`). LOC-06: بخش‌های مردهٔ `Vip.kt`، `Orders.kt`، `DeliveryDialogs.kt`… و سپس جدول‌ها.
### ۱۴۰۵/۰۸/۲۲ — عاملِ e-rows-15w زیرِ هماهنگ‌کننده، شاخهٔ `claude/e-rows-15w` (از `claude/batch-54`)

- **OPS-02 (رمزِ پشتیبانِ دستی و پوشه درونِ برنامه):** `BackupSeal` (`data/BackupSealAndroid.kt`، تنظیمِ دستگاهِ `backup_seal`، پیش‌فرض
  خاموش، فقط با کلیدِ عبارتِ `SnapshotSeal`) ⇐ کلِ ZIP در پاکتِ `rp1` با `PhraseBackup.sealing` ِ جریانی (بایت‌به‌بایت همان `seal`،
  قراردادِ `phrase-backup.js`)؛ پوشه `….zip.sealed`؛ کلید باز نشد ⇒ هیچ فایلی، هرگز ساده. بازیابی با کلیدِ همین دستگاه یا برگهٔ عبارت
  (`BackupController.unlockFile`)؛ عبارتِ اشتباه/فایلِ دست‌خورده ⇒ رد پیش از هر نوشتن. `BackupSealDrillTest` ۵/۵ (ده راهِ ردِ امن)، دو جهش سرخ؛ همسایه‌ها سبز. مانده: USB با یک لمس (دستگاه).
- **PAY-08:** `core/pay/PayLevel` (سه سطح، `success`)، `core/terminal/DevicePay` (`VerifyingTerminal`، کلیدِ یکتایی، ازسرگیری با
  `lookup`، `confirmed`+`device` فقط با پاسخِ کاملِ برابر، ناجوری/دیر ⇒ `needs_review`، یک ردیف) و `pay/DevicePaySettle` (همان
  `HesabPaySettle.withRow`). `DevicePayTest` ۷/۷ (core، سه جهش سرخ)، `DevicePaySettleTest` ۳/۳. مانده: دستگاهِ واقعی با سندِ یکپارچه‌سازی و D-08 («بیرونی»).
- **دام:** `quality.mjs` وابستگیِ پلتفرمیِ `AtRestEnvelope.kt` را سقف دارد ⇒ رمزِ جریانی در `core/atrest/platform/PhraseBackup.kt`
  (پوشهٔ مرزی). `javax.crypto.CipherOutputStream` خطای `flush` ِ پس از برچسب را نادیده می‌گیرد ⇒ جریانِ خودمان. کامپایلِ کامپیوتر
  با `-Xmx2g` ِ in-process «Java heap space» داد و با ۳g زیرِ بارِ عامل‌های دیگر کشته شد ⇒ `-Xmx3g -XX:+UseSerialGC --max-workers=1`.
- **قدمِ بعد:** اجرای کاملِ کامپیوتر (`flock`، ۱۴:۰۲) ⇒ ۳۹۷۰ PASS، ۰ FAIL، ۴ SKIP ِ پیشینِ بنچ/عکس؛ precheck ⇒ `fail=0`؛ `quality.mjs` بی ❌. OPS-02: USB با یک لمس و برگهٔ عبارت روی گوشیِ واقعی؛
  PAY-08: نخستین `VerifyingTerminal` ِ واقعی فقط با سندِ یکپارچه‌سازی (`VendorAppTerminal`)، سپس دکمه در `CardTerminalEntry`. ادغامِ
  `claude/e-rows-15w` در دسته.

### ۱۴۰۵/۰۸/۲۲ — عاملِ e-rows-13u زیرِ هماهنگ‌کننده، شاخهٔ `claude/e-rows-13u` (از `claude/e-rows-11p`)

- **ABUSE-01 (هنوز PARTIAL، فقط پنل):** ویترینِ وب «گزارشِ مشکل با این دکان» و «گزارشِ این کالا» دارد (`store/trust.js` ِ دیربار
  زیرِ `features.trust` ⇐ پرچمِ `market.trust`؛ تأییدِ شماره همان‌جا، «گزارش‌های من» با توکنِ روی دستگاه، سقفِ نرخِ سرور)؛ برنامهٔ دکان
  «ویترین ← … ← شکایت و گزارشِ خریداران» با پاسخ (کلیدِ یک‌بار) و اعتراض. آزمون‌ها: `storefront-trust` ۵/۵، `trust.e2e.mjs` (کرومیوم)
  ۵ گام، `TrustModelTest`/`TrustCasesUiTest`. مانده: صفِ رسیدگی و تصمیم در پنلِ مدیر (ریپوی server).
- **FIN-01 ⇐ IMPLEMENTED-VERIFIED:** `OrderRefundServerDesktopTest` همان کدِ برنامه را با سرورِ واقعیِ محلی (`offline30-backend.mjs`)
  می‌سنجد: بخشی، کامل، کلیدِ تکراری، هم‌زمان، بیش از مانده، و جمعِ دفتر = گزارشِ تطبیق.
- **TEN-04 (هنوز PARTIAL):** نوارِ «پشتیبانی در حالِ مشاهده» در پوستهٔ برنامهٔ صاحب (`SupportAccessNotice`): چه کسی/چرا/تا کی، بستن،
  پذیرفتن (با `ReauthHost`) و رد. مانده: نگهبانِ اجازه روی درهای خصوصیِ دیگرِ مدیر.
- **S89 ⇐ دستهٔ «تصمیم»:** سندِ معکوس به‌جای حذفِ قرض معنای دادهٔ همگام را عوض می‌کند (دخلِ نسخه‌های پیشین، FIFO ِ کهنگی، امتیاز)؛
  پرسش در `PARTIAL-MAP-fa.md` بخشِ «تصمیم». کدی زده نشد.
- **دام:** Gradle ِ `--no-daemon` که با `timeout` کشته شود دیمنِ تک‌بارش را زنده و ۱۰۰٪ CPU جا می‌گذارد ⇒ پس از هر قطع `ps | grep gradle-<X>`.
  با شش عامل `-Xmx3g` را OOM-killer می‌کشد و `-Xmx2g` کامپایلِ in-process را OOM می‌کند؛ `-Xmx2560m` کامپایل شد و آزمونِ هدف‌دار با
  `-Xmx1536m` (کامپایلِ آماده) سبز. سرور: مسیرِ تازهٔ ویترین فقط یک کلید در `features` (بی خطِ تازه در `app.js`).
- **اجرای کامل:** `flock /tmp/gradle-full.lock ./desktop/gradlew -p desktop test` ⇒ ۳۹۴۵ PASS، ۱ FAIL، ۴ SKIP ِ پیشینِ بنچ/عکس (۱۳:۲۷)؛
  تنها شکست `DariCoverageTest` بود: واژهٔ فقط‌فارسیِ «خیلی» در `kit/i18n/trust.fa.json` (واژه‌نامهٔ وب هم از همان سدِ دری می‌گذرد) ⇒
  «بیش از اندازه» و همان آزمون ۲۳/۲۳. جهش‌آزمایی (۴ جهش، همه سرخ) زیرِ همان قفل. precheck ⇒ `fail=0`؛ `quality.mjs` بی ❌.
- **نیمه‌کاره / قدمِ بعد:** دستهٔ «کد» ِ نقشه (۴۲)؛ پنلِ ABUSE-01 و درهای TEN-04 در ریپوی server/همین سرور؛ پاسخِ صاحب‌کار به S89.
### ۱۴۰۵/۰۸/۲۲ — عاملِ e-rows-17v زیرِ هماهنگ‌کننده، شاخهٔ `claude/e-rows-17v` (از `claude/batch-54`)

- **RSK-04 «دستگاه» (PARTIAL ⇒ «پرچم»):** شناسه همان رشتهٔ تصادفیِ مرورگر/نصبِ برنامهٔ خرید (MKT-02)، نه سخت‌افزار؛ ویترین و برنامهٔ خرید
  در هر ثبت می‌فرستند، سرور فقط چکیدهٔ «دکان + شناسه» را با پرچمِ روشن نگه می‌دارد (`lib/risk-device.js`، مهاجرتِ **۸۴۰** —
  ۸۲۱ تا ۸۳۹ خالی ماند؛ هماهنگ‌کننده شماره را تأیید کند). دکان فقط از راهِ سفارشِ خودش می‌بندد/مطمئن می‌کند؛ حذفِ دادهٔ مشتری
  دستگاه را پاک می‌کند. `risk-device.test.js` ۱۰/۱۰ (چهار جهش سرخ)؛ برنامهٔ دکان: دو دکمه در کارتِ سابقه — `OrderRiskCardUiTest` ۸/۸، `OrderRiskTest` ۱۳/۱۳، `ShopperCheckoutTest` ۱۳/۱۳ (desktop با `-Xmx3g`؛ ۲g «Java heap space» و دو بار OOM-kill زیرِ بارِ عامل‌های دیگر).
- **درس:** خانهٔ جدولِ STATUS هرگز `|` ندارد (`true|false` ردیف را شکست و `status` آن را NOT-VERIFIED کرد؛ از نو نوشته شد).
- **نیمه‌کاره:** هیچ. **قدمِ بعدی:** روشن کردنِ `shop.order_risk` روی دکانِ آزمایشی؛ ثبت از برنامهٔ خریدِ واقعی و دیدنِ دکمه روی گوشی
  (`customer-android` و `android-native` فقط در CI کامپایل می‌شوند).
### ۱۴۰۵/۰۸/۲۲ — عاملِ e-rows-12r زیرِ هماهنگ‌کننده، شاخهٔ `claude/e-rows-12r` (از `claude/e-rows-11p`)

- **بسته شد (IMPLEMENTED-VERIFIED):** S47 (نوارِ «حساب من» روزِ دقیقِ پایان، بی اینترنت از مجوزِ گوشی)، OPS-09 (کلیدِ ویترینِ هر دکان در
  پنل = server#141 ِ مرج‌شده)، PRM-11 (`POST /promos/redeem` کدِ قاعدهٔ گروه‌دار را با گروهِ قرض‌دارِ دفترِ دکان می‌سنجد ⇒
  `wrong_group`؛ برنامه `debtorId` ِ همان سبد را همیشه می‌فرستد، بی کلید = برنامهٔ پیشین)، INV-04@32 (ویترین «… — N مانده» از
  `sellableMilli` و سبد بیش از آن نمی‌پذیرد؛ بارِ نخستِ JS زیرِ سقف با کوتاه کردنِ دو توضیحِ `kit.js`).
- **کدِ کامل، فقط دستگاه:** PRES-08 (`seenAt` = وضعِ دیده‌شدهٔ لحظهٔ تصمیم ⇒ اعلامِ صفِ آفلاین اعلامِ تازه‌ترِ دستگاهِ دیگر را برنمی‌گرداند)،
  S110 (پیش‌فرضِ `Motion` = تنظیمِ گوشی؛ `V3Sheet` زیرِ کاهشِ حرکت بی انیمیشن)، S125 (نمایهٔ بارکد/شناسه برای همان `d.products`
  یک بار، محموله‌های انقضا برای همان دفتر و روز یک بار ⇒ اسکنِ کامپیوتر با ۵۰ هزار کالا p95 ‏۱۴۳٫۷ ⇐ ۱۹٫۴ms؛ آزمونِ سقفِ صفحهٔ ۱۰۰).
- **دسته عوض شد (نقشه):** CHAT-02/05/15 و S46 ⇐ دستگاه (گفت‌وگوی برنامهٔ خرید در #1027 ساخته است؛ F6 ِ مشترک خواستهٔ S46 نیست)،
  IMG-04 ⇐ پرچم (قبل/بعد کنارِ هم هست؛ اسلایدر خواستهٔ سند نیست). GATE-NEW-02: هر سناریوی INV با `reconciled` و نگهبانِ همان پرونده؛
  مانده رابطِ INV-01@32 (عاملِ دیگر). TEN-04، FIN-01، ABUSE-01، S89 و IMG-PRO-17 به عامل‌های دیگر رسید ⇒ سطرهایشان دست‌نخورده.
- **دام:** پوشهٔ scratchpad میانِ عامل‌ها **مشترک** است — `st.py` ِ من را عاملِ دیگری با نسخهٔ خودش (نوشتن در `wt-12q`) جایگزین کرد و یک بار
  سطرِ PRM-11 ِ `wt-12q` را خراب کردم (همان دم از HEAD ِ همان شاخه برگردانده شد). ابزارهای هر عامل در زیرپوشهٔ نام‌دارِ خودش (`r12/`).
  `server/node_modules` ِ پیوندیِ این worktree به `wt-webp` ِ پاک‌شده اشاره می‌کرد ⇒ به `scratchpad/srvdeps/node_modules`.
  PostgreSQL ِ محلی (پورت 55432، `/home/user/pgdata`) خاموش بود و نقشِ `shop` نداشت ⇒ روشن و `shop`/`shop_test` ساخته شد.
  سنجهٔ ۵۰ هزار کالا در کنارِ عامل‌های دیگر دو بار با کمبودِ حافظه کشته شد (اندازهٔ نهاییِ مسیرِ «نزدیکِ انقضا» سنجیده نشد).
- **اجرای کامل:** `flock /tmp/gradle-full.lock ./desktop/gradlew -p desktop test` ⇒ ۳۹۳۷ PASS، ۰ FAIL، ۴ SKIP ِ شناخته (اجرای نخست با
  کشته شدنِ daemon از کمبودِ حافظه پس از ۲۴۱۴ PASS بی هیچ FAIL ماند؛ دومی با `-Xmx1536m`)؛ precheck ⇒ `fail=0`؛ `quality.mjs` بی ❌.
- **نیمه‌کاره / قدمِ بعد:** S01 (آزمونِ Compose ِ `DeskSaleScreen` فقط با کلید: اسکن، `3*`، «نوشا»، F9، ۱۰۰۰، چاپ و فوکوس)،
  S18 (برگهٔ «مشتریِ تازه» ِ صندوقِ کامپیوتر با صفحه‌کلیدِ لمسیِ حروف)، S96 (آزمونِ یک روزِ کامل فقط با گوشی) — هر سه کد، ساخته نشد.
### ۱۴۰۵/۰۸/۲۲ — عاملِ e-rows-14q، شاخهٔ `claude/e-rows-14q` (از `claude/e-rows-12q` + ادغامِ `claude/batch-54`)

- **quality ِ دستهٔ ۵۴:** «مشتریِ تازه» به `ui/screens/TillNewCustomer.kt` (ساعتِ `ReportBridge.nowMs`)؛ DeskSale/SaleDialogs/ShopStore
  دوباره زیرِ سقف، `CustomerMerge` بی `currentTimeMillis` ِ چهارم (`589566c79`، ادغام‌شده).
- **WEB-16 ⇐ IMPLEMENTED-VERIFIED:** srcset با `STORE_SIZES` روی بازبینیِ ۵۴ب ِ `store-webp.js` (`WIDTHS`، `widthOf`، `?w=`؛ سرِ
  ویترین `imageWidths` فقط وقتی روشن)؛ بودجهٔ بارِ نخست ۷۲٬۷۰۳ از ۷۲٬۷۰۴ (یک بایت جا مانده — شاخهٔ بعدیِ ویترین باید جا باز کند).
  `docs/api/routes.json` پارامترِ `w` را دارد.
- **قدمِ بعد:** CHK-08 (عکسِ دروازه و یادداشتِ صوتیِ اختیاری در فرمِ نشانی: جدولِ رسانهٔ نشانیِ سفارش + درِ بارگذاری با توکنِ
  سفارش و سقفِ اندازه/نرخ، خواندن فقط برای `orders.view`/پیکِ همان سفارش با آزمونِ ۴۰۳، فرمِ ویترین، نمایش در برنامه؛ نقشه در
  برنامهٔ خرید فقط CI)، POS-05/INV-03 (درِ سرورِ مقدارِ رزروشدهٔ سفارش‌های اینترنتی + خواندن در `HeldStock.sellable`) — شروع نشد.

### ۱۴۰۵/۰۸/۲۲ — عاملِ e-rows-13x زیرِ هماهنگ‌کننده، شاخهٔ `claude/e-rows-13x` (از `claude/e-rows-11p`) + server `claude/panel-rows-13v`

- **LOC-04 ⇐ IMPLEMENTED-VERIFIED:** ویرایشگرِ جدولِ تعطیلات از 1.50.78 (server #130، `788f371`) در پنل بود («فروشگاه‌ها ← تنظیماتِ مرکزی و
  پرچم‌ها ← تعطیلاتِ رسمی»)؛ STATUS کهنه بود. شاهد: `console-holidays.mjs` ۱۶/۱۶ و `holidays-ui.mjs` (کرومیوم) ۱۴/۱۴ روی main ِ server.
- **IMG-PRO-17 (PARTIAL ⇒ «دستگاه»):** درِ مدیرِ `routes/admin-image-changes.js` (`image-changes.v1`): فهرست/جزئیات/برگرداندن؛ جزئیات با
  پرچمِ `admin.support_grant` اجازهٔ `read_private` (پیش‌فرض؛ صاحب‌کار می‌تواند عوض کند)؛ برگرداندن ارشد + دلیل + همیشه اجازهٔ `restore` +
  Idempotency-Key. `admin-image-changes.test.js` ۹/۹ (پایه ۸/۹ سرخ، شش جهش سرخ). پنل (server 1.50.96، `2011903`): پل + کارت در «بانکِ عکس»،
  `console-image-changes.mjs` ۲۱/۲۱، `image-changes-ui.mjs` ۱۳/۱۳؛ `bridge-roles.mjs` حالا همهٔ `require` های یک `router.use` را می‌خواند.
- **نیمه‌کاره:** هیچ. **قدمِ بعدی:** PR ِ هر دو شاخه (shop پیش از server تا `bridge-roles` روی main ِ shop هم درها را ببیند)؛ آزمونِ
  دستیِ IMG-PRO-17 روی گوشی (PARTIAL-MAP «دستگاه»). `staff-bank-ui.mjs` ِ server روی main هم یک سرخِ «404 در کنسول» دارد (جدا از این کار).
### ۱۴۰۵/۰۸/۲۲ — عاملِ e-rows-14w زیرِ هماهنگ‌کننده، شاخهٔ `claude/e-rows-14w` (از `claude/e-rows-13w`)

- **GATE-NEW-05 ساخته شد (PARTIAL تا شاهد):** ورک‌فلوی آزمونیِ `.github/workflows/gradle-evidence.yml` (بی انتشار، `contents: read`،
  actionهای سنجاق، بی `workflow_run`؛ push ِ main با تغییرِ Kotlin، PR ِ خودش، دستی) `desktop test` (با `android-native/app/src/test`؛
  همهٔ شناسه‌های کارِ بومی را دارد) و core را می‌دواند و `tools/test-evidence/gradle-evidence.mjs` JUnit XML را به JSONL ِ هم‌شکلِ
  `server/scripts/test-evidence.js` در artifact ِ `test-evidence-gradle` می‌برد. آزمون ۹/۹، شش جهش سرخ؛ ۵۶۸۶ خط روی گزارش‌های واقعی؛
  `repro` ِ یک آزمونِ فارسیِ core با Gradle ِ محلی دقیقاً همان یک آزمون را دواند. workflow-guard ۰ خطا، ۴۰/۴۰. قاعده در RULES «CI و آزمون».
- **دام:** `java -version` خطِ «Picked up JAVA_TOOL_OPTIONS: …» (پراکسی، truststore و رمزش) را چاپ می‌کند ⇒ از شواهد کنار می‌رود
  (`javaLine`). KMP نامِ آزمونِ core را در XML با `[jvm]` می‌نویسد ⇒ `--tests` با آن چیزی پیدا نمی‌کند؛ برداشته می‌شود.
- **قدمِ بعد:** پس از مرج، artifact ِ `test-evidence-gradle` ِ نخستین اجرای «شواهدِ آزمون‌های Gradle» روی main را ببین (شمارِ خط‌ها ≈
  شمارِ KTEST ِ «کامپایل و آزمون») و GATE-NEW-05 ⇐ IMPLEMENTED-VERIFIED؛ دستهٔ CI ِ نقشه دوباره خالی می‌شود.

### ۱۴۰۵/۰۸/۲۲ — عاملِ e-rows-13w زیرِ هماهنگ‌کننده، شاخهٔ `claude/e-rows-13w` (از `claude/e-rows-11p`)

- **دستهٔ «CI» ِ نقشهٔ PARTIAL (۶ ردیف)، شاهد از CI ِ push ِ main ‏719bbcf63 (#1038):** INV-16 (`StockValueBridgeTest` ۳/۳)، SYN-12
  (`SyncRetryAfterTest` ۴/۴، `ReconnectGateTest` ۵/۵)، S117 (`card-data` ۱۱/۱۱ در PostgreSQL، PGlite و Electron)، S124 (server#141
  مرج شد ⇒ CI ِ main ِ server ‏f80640b86: `account-finance` ۴۱ سبز با دو بررسیِ S124؛ `admin-money` ۳/۳ در shop) و GATE-NEW-01
  (`PresenceUiTest`، `StatusPaneUiTest`، `StorefrontModelTest`، `PresenceSenderDesktopTest` و PRES ِ سرور) ⇐ IMPLEMENTED-VERIFIED.
  GATE-NEW-05 ⇐ «کد»: CI هیچ artifact ِ آزمونِ Gradle ندارد (فقط `test-evidence-*` ِ سرور). S94 (پرچم): شاهدِ CI ِ برگهٔ پیک
  نوشته شد، فقط پرچم مانده. نقشه: ۲۴۰ ردیف، CI ۰، کد ۴۵. هیچ کدی عوض نشد؛ precheck ⇒ `fail=0`.
- **روش (برای دورِ بعد):** `gh api repos/vil3ntec-it/shop/actions/jobs/<job>/logs > x.log` **کلِ** لاگ را از کانتینر می‌دهد (برخلافِ
  `get_job_logs` با سقفِ ۵۰۰۰ خط)؛ سپس `grep -c 'KTEST PASS <کلاسِ کامل> >'`. zip ِ artifact هنوز رد می‌شود (blob در پراکسی).
- **دام:** صفحهٔ کهنهٔ `ui/screens/Storefront.kt` (`StorefrontScreen(snackbar)`، `PresencePane`، `PresenceBadge`، `OutboxBanner`) هیچ
  صدازننده‌ای ندارد؛ فقط `StorefrontGate` و `SwitchRow` ِ آن زنده‌اند. `DashboardEngine.board(...).stockValue` هم خوانده نمی‌شود.
- **قدمِ بعد:** GATE-NEW-05 — ورک‌فلوی آزمونیِ جدا (مثلِ `offline30.yml`؛ `tohid-desktop.yml`/`tohid-native.yml` انتشاری‌اند و
  `workflow_run` در workflow-guard ممنوع) با JUnit XML ⇐ JSONL ِ هم‌شکلِ `server/scripts/test-evidence.js`. پاک کردنِ کدِ مردهٔ بالا
  (با یک اجرای کاملِ desktop). RPT-14 و CHAT-02 («کامپایلِ اندروید فقط CI») در دستهٔ «کد»اند و دست نخوردند. ادغامِ این شاخه در دسته.
### ۱۴۰۵/۰۸/۲۲ — عاملِ e-rows-12q زیرِ هماهنگ‌کننده، شاخهٔ `claude/e-rows-12q` (از `claude/e-rows-11p`)

- **ساخته شد (۶ ردیف از دستهٔ «کد» ِ نقشه):** CRM-03 ⇐ IMPLEMENTED-VERIFIED (ادغامِ مشتری پیش از نوشتن «تغییرِ سقفِ اعتبار» را
  می‌پرسد: `CustomerMerge.merge(…, limitRules)` با همان `CreditLimitGate`؛ هر دو درِ ادغام). PAY-10 ⇐ IMPLEMENTED-VERIFIED (کادرِ
  ارزِ نقد در پنلِ F9 ِ کامپیوتر، `FxTenderBox` با `FxTillPref.tillRates`). POS-31: تشخیصِ صفحهٔ لمسی از سیستم‌عامل (هستهٔ
  `TouchScreen` + `TouchScreenProbeDesk.kt` با JNA؛ فقط پیش‌فرضِ کلیدِ نوار، `TouchMode.choice`)، صفحه‌کلیدِ حروف با فوکوسِ نامِ
  قلمِ آزاد و یادداشت‌ها (`TouchTextKeys`)، «مشتریِ تازه» در گزینشِ مشتریِ صندوقِ لمسی ⇒ فقط دستگاه. POS-41: Tab/Shift+Tab ِ
  پنلِ پرداخت از keymap (partial) ⇒ فقط دستگاه. PAY-06: «روش‌های پرداختِ این دکان» (`tohid-policy:pay_methods_off`، کلیدِ دکان) در
  هر سه صندوق ⇒ فقط بیرونی (D-08).
- **بازبینیِ بی کد:** CRM-15 (صفِ دستگاه و فرمانِ حذف = همگام‌سازی) ⇐ همگام/پمپ. نقشه: ۲۴۳ ردیف، کد ۳۸. یافته برای عاملِ «ت»
  (CRM-06 و HW-07 به او سپرده شد؛ سطرهایشان دست نخورد): `debt_reminder_template` از پیش کلیدِ دکان است (`ShopSettingsKeys`) و با
  `shop.settings_sync` همگام می‌شود؛ HW-07 تصمیمِ ۴-۲۴ (۱) ج «فعلاً نه» دارد.
- **دام:** `CustomerMerge.merge` با `newId` ِ آخر را بسیاری با lambda ِ پسین صدا می‌زنند ⇒ پارامترِ تازه با **هم‌بار**، نه
  افزودن به آخر. `LocalFocusManager` ِ بیرون از `Dialog` مالِ پنجرهٔ صندوق است ⇒ Tab ِ پنل باید درونِ `Dialog` بگیردش.
  `PosLogicTest` شمارِ «بازگشت به پنل پس از گزینش» را می‌شمارد (حالا ۳ با «مشتریِ تازه»).
- **اجرای کامل:** `flock /tmp/gradle-full.lock ./desktop/gradlew -p desktop test` روی کدِ Kotlin ِ `db2f33b04` (پس از آن فقط سرور و
  سند) ⇒ ۳۹۵۰ PASS، ۰ FAIL، ۴ SKIP ِ پیشینِ بنچ/عکس (۱۷:۳۹). سرور: `store-webp` ۴ + ۱ skip ِ Electron، `storefront-v2` ۱۹/۱۹،
  `keymap` ۸/۸ (PGlite). precheck ⇒ `fail=0`.
- **WEB-16 (سرور):** AVIF با `STORE_AVIF=1` ِ جدا در همان `lib/store-webp.js` (`pick`/`encodedOf`)؛ گالری از پیش با PRD-17.
- **نیمه‌کاره / قدمِ بعد:** WEB-16: AVIF ساخته شد (پایین)؛ مانده srcset (اندازه‌های پاسخ‌گوی بیشتر). ماندهٔ همین عامل: POS-05/INV-03
  (رزروِ سفارشِ اینترنتی در صندوق: درِ سرور برای مقدارِ رزروشده + خواندن در صندوق)، PAY-08، CHK-08، OPS-02 — شروع نشد.
  ⚠️ `server/node_modules` ِ مشترک (`/home/user/wt-webp`) در میانهٔ کار پاک شد؛ این worktree به نصبِ خصوصیِ scratchpad پیوند دارد.

### ۱۴۰۵/۰۸/۲۲ — عاملِ e-rows-11p زیرِ هماهنگ‌کننده، شاخهٔ `claude/e-rows-11p` (از `claude/batch-53`)

- **نقشهٔ PARTIAL:** `docs/spec/PARTIAL-MAP-fa.md` — هر ردیفِ PARTIAL در یک دسته با یک عبارت از مانده و، برای دسته‌های صاحب‌کار،
  «دقیقاً چه کند» (پیوند به FLAGS-ON و OWNER-ACTIONS، بی تکرار). پیش از این دور ۲۴۶ ردیف: کد ۴۷، پرچم ۵۱، تصمیم ۴۷، بیرونی ۳۶،
  دستگاه ۴۴، همگام/پمپ ۱۵، CI ۶؛ پس از آن ۲۴۵ (کد ۴۴). سازنده: اسکریپتِ کوچکِ بیرون از ریپو؛ `spec.mjs` پرونده را در فهرستِ مجاز
  می‌شناسد (بیرون از تکه‌ها، check «سالم»). با هر تغییرِ STATUS کهنه می‌شود — مرجع همان STATUS.
- **ساخته شد:** S90 ⇐ IMPLEMENTED-VERIFIED (پنج گامِ «پشتیبانی» در `roles.e2e.mjs`: چهار نقشِ مدیرِ پنل، دکمه‌های «فقط ارشد» ⇔
  سرور، پنل در کرومیوم، `support-access.v1` و اجازهٔ زنده فقط برای درخواست‌کننده؛ ۱۰/۱۰ گام، چهار جهش سرخ). CHK-18: «افزودن به
  همان سفارش» هم سقفِ نگه‌داشتنِ هر شماره را دارد (`syncOrderIn` با `phoneCap`، بازسازیِ S86 همان قاعده) — فقط گوشیِ واقعی مانده.
  POS-04: درِ همگانیِ `GET /api/search/synonyms` و `TillSynonyms` (روزی یک بار، بی‌شبکه نسخهٔ ذخیره‌شده) ⇐ جست‌وجوی صندوق — فقط
  فهرستِ صاحب‌کار مانده.
- **دام:** دو آزمونِ کهنهٔ «افزودن» (`stock-reserve`، SR42-1) بی سقفِ شماره نوشته شده بودند ⇒ با موجودیِ بیشتر همان قصد را می‌سنجند
  و حالتِ «موجودیِ کهنه» حالا محافظت را می‌سنجد. `server/src/app.js` روی سقفِ ۸۰۰ خط است ⇒ سوار کردنِ مسیرِ تازه بی خطِ تازه.
  `remember(...)` ِ کاشی‌های صندوق کلیدِ `TillSynonyms.search` را دارد، وگرنه فهرستِ تازه تا جست‌وجوی بعد دیده نمی‌شد.
- **اجرای کامل:** `./desktop/gradlew -p desktop test` ⇒ ۳۹۲۶ PASS، ۰ FAIL، ۴ SKIP ِ پیشینِ بنچ/عکس (۲۱:۴۷)؛ اجرای نخست انبارِ
  تازهٔ `tohid-synonyms` ِ بی دسته را گرفت (`DataSafetyGuardTest`) ⇒ DATA-SAFETY §۶. سرتاسری با `E2E_FLAGS_ON=1` ⇒ ۲۷۳ گام سبز؛
  precheck ⇒ `fail=0` (پروندهٔ تازه با `Context` فقط با نامِ مرزیِ `…Android.kt`).
- **نیمه‌کاره / قدمِ بعد:** دستهٔ «کد» ِ نقشه به ترتیب (P0: POS-31، POS-41، PAY-06، CRM-03، CRM-15، CHK-08، OPS-02؛ سپس P1)؛
  S47 (تاریخِ پایان در نوارِ «حساب من») کوچک ولی امضای `ProfileHomeModel.plan` را عوض می‌کند. ادغامِ `claude/e-rows-11p` در دسته.
### ۱۴۰۵/۰۸/۲۲ — عاملِ e-rows-11o زیرِ هماهنگ‌کننده، شاخهٔ `claude/e-rows-11o` (از `claude/batch-53`)

- **INV-01@32 ⇒ IMPLEMENTED-VERIFIED («تعریف و انتشار از یک صفحه»):** صفحهٔ هر کالا (`products/{id}`، گوشی و کامپیوتر) بخشِ «ویترینِ
  اینترنتی» دارد (`ui/v3/online/storefront/ProductStorefront.kt` منطقِ خالص، `ProductStorefrontAndroid.kt` مرزِ پلتفرم و `ProductStorefrontSlot`).
  سرور: `GET /api/shop/storefront/products/:productId` (`staffProduct` در `storefront-owner.js`، همان شکلِ فهرست). **۴۰۴ ِ «کالای
  همگام‌نشده» درست حل شد:** خواندنِ ۴۰۴ `product_not_found` ⇒ «هنوز به سرور نرسیده»، کلیدِ خاموش و هیچ PUT؛ «همین حالا همگام کن»
  (`AutoSync.now`) و پایانِ هر همگام‌سازیِ موفق (`rememberSyncV1Status().lastOkAt`) فقط کالای نرسیده را دوباره می‌پرسد — بی دست زدن به `sync/`.
- **IMG-11 (PARTIAL ِ روشن‌تر):** zoom ِ ۲۴۰۰px روی سرور (`lib/image-zoom.js`، پرچمِ خاموشِ `image_bank.zoom` در کاتالوگ و RULES، `sharp` ِ
  اختیاری با بارکنندهٔ مشترکِ `lib/sharp-lib.js`)، یک‌بارنویس در `image_derivatives`؛ درهای ویترین/گالری/برنامه و `urls.zoom` ِ «کدام عکس»
  (`withUrl` در درِ `shop-images`، نه در `image-bank.js` که سقفِ اندازه دارد). وب: `store/zoom.js` + واژه‌نامهٔ جدای `zoom` (≥۱۰ کلید —
  قاعدهٔ `web-i18n`؛ با واژه‌نامهٔ `gallery` نشد چون `web-store-gallery.mjs` «پرچمِ گالری خاموش ⇒ هیچ `i18n/gallery`» را می‌سنجد). برنامه:
  بخشِ «نسخهٔ بزرگ‌نمایی» در «نسخه‌های عکس» با `zoomFits` و `ZoomFrame`/`ZoomMath`. مانده فقط گوشیِ اندروید ِ واقعی و تصمیمِ روشن کردنِ پرچم.
- **آزمون‌ها (سرخ پیش/سبز پس):** `storefront-product-one` ۰/۲ ⇒ ۲/۲، `image-zoom` ۰/۳ ⇒ ۳/۳، `web-store-zoom.mjs` خروجِ ۱ ⇒ ۴۱ ✅؛ desktop
  `ProductStorefrontUiTest` ۶/۶، `ZoomFrameUiTest` ۲/۲، `PhotoVersionsPageTest` ۸/۸؛ جهش‌ها (۳ سرور، ۳ کاتلین) همه کشته. بی‌تغییر سبز: `storefront`
  ۱۹، `storefront-bulk` ۵، `catalog-stock` ۲۶، `image-bank` ۲۰، `storefront-gallery` ۳، `store-webp` ۳، `admin-flags` ۲، `console-shell` ۸، `web-i18n` ۳۳،
  `web-store-gallery.mjs` ۳۵، `web-store.mjs` ۱۳۲، `admin-panel.mjs` ۴۳/۴۳. `./desktop/gradlew -p desktop test` کامل ⇒ ۳۹۳۵ آزمون (۳۹۳۱ PASS،
  ۴ SKIP ِ پیشین)، ۰ شکست، ۱۵:۵۲. precheck `fail=0`.
- **یافته در اجرای کامل:** `V3A11yGuardTest` قابِ بزرگ‌نماییِ تازه (`detectTransformGestures`) را بی جایگزینِ صفحه‌خوان گرفت ⇒ `customActions`
  (بزرگ‌تر، کوچک‌تر و دیدنِ چهار سو) روی همان قاب، نه ردیفِ `ALTERNATIVES`. Gradle ِ نخست را OOM ِ cgroup کشت (عامل‌های هم‌زمان) ⇒ `-Xmx2g`.
- **دامِ آزمونِ Compose:** یک بار `aSuccessfulSyncRechecksOnlyAProductThatHadNotArrived` در اجرای کاملِ کلاس سرخ شد (دو دورِ پرسشِ هم‌پوشان؛ تکرار
  نشد) ⇒ پیش از «همگام‌سازیِ بعدی» ۵۰۰ms صبر و چاپِ درختِ معنایی هنگامِ شکست.
- **قدمِ بعد:** ادغامِ `claude/e-rows-11o` در دسته؛ IMG-11 روی گوشیِ اندروید (CI ِ اندروید یا دستگاه) و تصمیمِ صاحب‌کار برای `image_bank.zoom`.

### ۱۴۰۵/۰۸/۲۲ — عاملِ e-rows-10m زیرِ هماهنگ‌کننده، شاخهٔ `claude/e-rows-10m` (از `claude/batch-52`)

- **انجام شد:** IMG-BANK-10، STF-07، PUR-02 و PUR-14 ⇒ IMPLEMENTED-VERIFIED؛ S90 با آزمونِ سرتاسریِ تازه (PARTIAL ِ روشن‌تر).
  دو باگِ واقعی: (۱) «انتخاب از بانکِ تصاویر» — جست‌وجوی نخست بیرون از برگه شروع می‌شد و پاسخِ بی‌درنگ پنجره را روی «در حالِ
  جست‌وجو…» نگه می‌داشت (کار تمام و حالت نوشته شده بود؛ با پاسخِ ۱٫۵ ثانیه‌ای سبز) ⇒ `LaunchedEffect` درونِ `AdaptiveSheet`؛
  (۲) «ورود و خروج» — زدنِ تازه پس از برگشتِ شبکه پیش از صف می‌رفت و سرور زدنِ صف‌شده را `punch_before_last` رد می‌کرد (نوبت گم)
  ⇒ صف نخست و `flush` زیرِ قفل. ابزارِ آزمونیِ تازه در `desktop/src/test`: `TestBackend` (`Backend.api` ⇐ سرورِ ساختگی، با
  `online`)، `RouteUiHarness` (هر مسیرِ نسخهٔ ۳ با `ShellContext` ِ واقعی از راهِ `V3RoutePreview`)، `FakePurchaseOrders` (سرورِ
  سفارشِ خریدِ با حالت). آزمون‌ها: `ImageBankPickerUiTest` ۴، `StaffTimeHubTest` ۳، `PurchaseOrderRoutesUiTest` ۷،
  `SupplierBillRoutesUiTest` ۶؛ `server/test/e2e/roles.e2e.mjs` ۵ گام (هفت نقش: `/api/shop`، درهای مدیریت، سفارشِ خرید، ورود و
  خروج، Sync v1 ⇔ `PermCore.canWrite`).
- **دامِ محیط:** Kotlin daemon میانِ عامل‌ها مشترک است (همان `-Xmx2560m` ⇒ همان daemon) و با دو کامپایلِ هم‌زمان در GC گیر می‌کند
  (کامپایلِ desktop از ۳۰ دقیقه گذشت). `-Pkotlin.compiler.execution.strategy=in-process -Dorg.gradle.jvmargs=-Xmx4600m` ⇒ هر
  کامپایلِ کامل ۵ تا ۷ دقیقه، بی اثر روی عاملِ دیگر.
- **نیمه‌کاره:** S90 — فقط «پشتیبانی» (اجازهٔ موقتِ دسترسی `support-access.v1` و نقش‌های مدیرانِ پنل) در آزمونِ سازگاریِ رابط
  نیست؛ پیک را `courier.e2e.mjs` (گذارهای برنامهٔ پیک ⇔ `canTransition` ِ سرور) از پیش دارد.
- **اجرای کامل:** `./desktop/gradlew -p desktop test` ⇒ ۳۹۲۳ آزمون (۳۹۱۹ PASS، ۴ SKIP ِ پیشینِ بنچ/عکس)، ۰ شکست، ۱۴:۳۹؛
  precheck ⇒ `fail=0`.
- **قدمِ بعد:** ادغامِ `claude/e-rows-10m` در دسته؛ برای S90 یک گامِ پشتیبانی (پنلِ مدیر در کرومیوم: دکمه‌های فقط‌ارشد برای مدیرِ
  عادی پنهان ⇔ همان درها ۴۰۳) در همان `roles.e2e.mjs`.
### ۱۴۰۵/۰۸/۲۲ (د) — عامل `claude/e-rows-10n` (از `claude/batch-52`): قراردادِ عمومیِ v2، پشتهٔ گیرِ آیفون، IMG-01

- **قراردادِ نمای عمومی v2:** `refund` ِ پیش‌فرض‌روشن (NTF-08) و `receipts` ِ درونش (E2E-RET-11) فیلدِ اختیاریِ نسخهٔ ۲ شد:
  `SCHEMA_VERSION = 2` (`server/src/lib/public-views.js`: `order.refund?`، `orderRefund`، `refundEntry`، `refundReceipt`)،
  `core/test-vectors/market-public-v2.json` (v1 دست‌نخورده)، سازندهٔ خالصِ `order-refund-view.shapeOf` (نمونه از همان، با
  دلیل/کنندهٔ کارمند در ورودی) و بُرشِ `pub('orderRefund')` با `market.public_views`. `public-views.test.js` بی `defaultOnOff`:
  پاسخ‌های واقعی دو بار (پیش‌فرض‌ها و همه `false` ِ صریح) + آزمونِ پیگیریِ واقعی با دو بازپرداخت و رسید (کلیدها به ترتیب، بی
  دلیل/کننده، بُرش همان پاسخ، رسید خاموش ⇒ بی `receipts`، پیگیری خاموش ⇒ بی کلید) + «نسخهٔ تازه هیچ فیلدِ پیشین را برنمی‌دارد».
  core: `PublicRefund`/`PublicRefundEntry`/`PublicRefundReceipt` در `MarketContract.kt` (`RefundView`/`RefundEntry` ِ برنامهٔ خرید
  حالا typealias ِ همان‌ها؛ `trackPage` هنوز `refund` را جدا از `PublicOrder` می‌خواند). `ignoreUnknownKeys` ِ `MarketContract.json`
  و `ShopperJson` سنجیده شد (`toleratesNewerServer`، `refundV2ReceiptsAndKeysFromANewerServerAreTolerated`).
- **آیفون (الف):** گامِ `if: always()` ِ «پشتهٔ رشته‌های گیرکرده (hang-sample، کوتاه)» **پس از** THUMB در `ios-spike.yml` با
  `ios/e2e/hang-threads.py` (رشتهٔ اصلی + رشته‌های ایستاده روی NSRecursiveLock/semaphore_wait/dispatch_sync، هر کدام ≤۸۰ خط: ریشه
  و تهِ پشته؛ همان در `ios-e2e/shots/hang-threads.txt`). `python3 ios/e2e/hang-threads.py --self-test` ⇒ ۷/۷.
- **آیفون (ب) — رفع نشد (شاهد کافی نیست، فقط CI ِ مک داور است):** `synchronized` ِ iOS یک `NSRecursiveLock` ِ سراسری است و
  `@Synchronized` هم به `synchronized(Unit)` برمی‌گردد (`unsynchronize` در `ios/shared/build.gradle.kts`) ⇒ همهٔ قفل‌های برنامه یکی‌اند.
  چرخه‌های ممکن (فقط استدلالِ کد): ① رشته‌ای با قفل ⇐ HTTP (`Http.kt:174` FOREVER) و پاسخش روی صفِ سریالِ نمایندهٔ همان
  NSURLSession است که `didReceiveChallenge`/`willPerformHTTPRedirection` در آن `ShimHttp.entry` ⇐ همان قفل را می‌خواهد (https یا ۳xx)؛
  ② قفل ⇐ `IosLocation.lastNow` (`dispatch_sync(main)`) و رشتهٔ اصلی پشتِ قفل؛ ③ قفل ⇐ `Future.get` (`Concurrent.kt:56`) و کارِ صف
  قفل بخواهد. پویشِ ایستای بدنهٔ همهٔ `synchronized`/`@Synchronized` های android-native هیچ HTTP/`get()`/`dispatch_sync` ِ مستقیمی
  نیافت (فراخوانِ تودرتو سنجیده نشد) و آزمونِ سرتاسری http ِ ساده به 127.0.0.1 است (بی challenge) ⇒ ریشهٔ #1037 روشن نیست.
  core هدفِ `linuxX64` دارد ولی این shim ها (Foundation/libdispatch) فقط در `ios/shared/iosMain` اند ⇒ بازتولید روی لینوکس نشد.
  ⛔ قفلِ هر شیء به‌تنهایی **خطرناک** است: `SyncV1Engine` هم `@Synchronized` دارد هم `synchronized(this)` و `ShopStore`
  `synchronized(SyncV1Engine.of(context))` ⇒ با `@Synchronized → synchronized(Unit)` دو قفلِ جدا و بی‌انحصاری روی دفتر؛ باید
  `unsynchronize` ‏`synchronized(this)` (یا `this@Class` برای تابعِ extension) بنویسد — ۶۹ جا، فقط با کامپایلِ مک سنجیدنی.
- **IMG-01 ⇒ IMPLEMENTED-VERIFIED:** «مانده» ِ STATUS کهنه بود — پنلِ مدیر (#382، `server/public/admin/image-bank.js`: پوشه + CSV،
  پیشرفتِ «N از M فایل»، گزارشِ ردیف و فایلِ خراب) و ورودِ گروهیِ برنامه (`ImageBulkImport.kt`، #291) روی main‌اند؛ آزمون‌ها محلی سبز.
  INV-01@32 («تعریف و انتشار از یک صفحه») و IMG-11 (نسخهٔ zoom در کلاینت‌ها) دست نخوردند: هر دو کارِ رابطِ برنامه با وابستگیِ
  همگام‌سازی/دستگاه‌اند (انتشارِ کالای هنوز همگام‌نشده ۴۰۴ می‌گیرد ⇒ نوعِ تازه در `StorefrontOutbox` پس از push؛ zoom ⇒ ساختِ
  ۲۴۰۰px در `ProductGalleryRepository` + نمایشگرِ بزرگ‌نمایی) — بزرگ‌تر از یک عامل، نیمه‌کاره نماندند.
- **آزمون‌ها:** سرور (`TEST_DATABASE_URL=pglite:memory node --test --test-concurrency=1`): `public-views` ۹/۹ (با `public-views.js` ِ پیشین ۵ سرخ:
  snapshot، آشکارساز، سازنده، پاسخ‌های واقعی با پیش‌فرض‌ها، v2 ِ بازپرداخت)؛ بی‌تغییر سبز: `order-refund-view` ۳/۳، `order-refund-receipt` ۳/۳،
  `customer-notify-refund` ۱۵/۱۵، `flag-defaults` ۳/۳، `admin-flags` ۲/۲، `order-flow` ۱۶/۱۶، `order-amend` ۹/۹، `order-add-items` ۷/۷،
  `storefront` ۱۹/۱۹، `storefront-consumption` ۲/۲، `storefront-photo-version` ۲/۲، `image-bank` ۲۰/۲۰، `admin-ops-image-bank` ۳/۳؛
  `node test/admin-panel.mjs` ۴۳/۴۳. core: `jvmTest --tests 'core.market.*' --tests 'core.shopper.*'` ۱۴۱ (۰ شکست، ۲ رد ِ پیشین؛
  `MarketContractTest` ۸/۸). `./desktop/gradlew -p desktop test` کامل ۳۸۹۹ PASS، ۰ FAIL، ۴ SKIP ِ پیشین (`BulkImportTest` ۱۱/۱۱).
  precheck `fail=0`. فقط CI: کامپایلِ `customer-android` (بی Android SDK ِ محلی؛ `RefundView` حالا typealias) و آزمونِ iOS ِ
  `ShopperChatActsTest` روی شبیه‌ساز.
- **قدمِ بعد:** (۱) اجرای بعدیِ «برنامهٔ آیفون — شبیه‌ساز» که گیر کند ⇒ `hang-threads.txt`/پایانِ لاگ نشان می‌دهد کدام چرخه؛ آن‌گاه
  کوچک‌ترین رفع (برای ①: قفلِ خصوصیِ `ShimHttp`/`IosWebSocket` به‌جای `synchronized` ِ سراسری؛ برای ②: `lastNow` بی `dispatch_sync`
  — آخرین موقعیت را رشتهٔ اصلی در `@Volatile` نگه دارد؛ برای ③: `get` با مهلت). (۲) INV-01@32 و IMG-11 با نقشهٔ بالا.

### ۱۴۰۵/۰۸/۲۲ (ج) — هماهنگ‌کننده: دسته‌های ۵۰ تا ۵۲ و «چند سرور» (سیزنِ `session_01BwqpPed1SJuwB7Q4nZUbg9`) — **از این‌جا ادامه بده**

- **انجام شد (shop):** #1035 (دستهٔ ۵۰) مرج. #1037 (دستهٔ ۵۱) باز: همهٔ کارها سبز جز «برنامهٔ آیفون — شبیه‌ساز» که در
  `10-online-order` پس از «ثبتِ فاکتور» ۲۰ دقیقه گیر کرد (نه شکستِ آزمون؛ سرور فاکتور را ساخت و پس از آن هیچ درخواستی نیامد)؛
  یک بار دوباره اجرا شد. ریشهٔ محتمل، پیشین و بیرون از این دسته: `ios/shared/src/iosMain/.../jvm/Jvm.kt:194-205` همهٔ
  `synchronized` را روی یک `NSRecursiveLock` ِ سراسری می‌برد و انتظارِ بی‌پایان زیرِ آن (`IosLocation.kt:58` ‏`dispatch_sync`، 
  `java/net/Http.kt:174` با `DISPATCH_TIME_FOREVER`) همهٔ رشته‌ها را می‌خواباند — با `hang-sample` تأیید نشده (پشته از ۵۰۰۰ خطِ
  آخرِ لاگ بیرون می‌افتد چون پس از آن عکس‌های THUMB چاپ می‌شوند).
- **دستهٔ ۵۲ (`claude/batch-52`):** e-rows-9d، e-rows-9e، verify-rows-i، verify-rows-j و safe-flags-on (۱۷ کلیدِ کم‌خطر پیش‌فرض
  روشن؛ تنها فهرست `server/src/lib/flag-defaults.js`، فقط `false` ِ صریح یا نگه‌داشتِ سرویس خاموش می‌کند). `verify-all.sh` ِ
  محلی پس از ادغام: سرور PGlite ۴۱۲۰ (۰ fail، ۲ TODO ِ پیشینِ همگام‌سازی)، e2e ۳۷، همهٔ مرورگرها، core ۱۸۲۳، desktop ۳۸۹۹ — ۰ bad.
- **انجام شد (server):** #139، #140 (نسخهٔ بستهٔ سرورِ حساب با مُهرِ زمان ⇒ پنل به‌روزرسانی و پرچم‌های تازه را می‌بیند)، #142 طرحِ
  `docs/MULTI-SERVER-fa.md`، #143 «جابه‌جایی به کامپیوترِ دیگر» (1.50.93)، #144 «سرورهای من» (1.50.94؛ کارتِ جابه‌جایی هم
  همین‌جا چون «تنظیمات» در `web/src/features.ts` خاموش است)، #145 جابه‌جاییِ تک‌برنامه از راهِ مرکز و `move-server --only`
  (1.50.95). همه مرج؛ برنامهٔ ویندوز از کانالِ `windows-preview` به پنل می‌رسد.
- **قدمِ بعد:** (۱) اجرای دوبارهٔ آیفونِ #1037: سبز ⇒ مرج و main در batch-52؛ دوباره گیر ⇒ گامِ `if: always()` پس از THUMB در
  `ios-spike.yml` که فقط پشتهٔ رشته‌های ایستاده روی قفل را از `hang-sample-*.txt` چاپ کند، سپس رفعِ قفل. (۲) PR ِ دستهٔ ۵۲.
  (۳) قراردادِ نمای عمومی: `refund` ِ پیش‌فرض‌روشن بیرون از v1 است ⇒ نسخهٔ تازهٔ قرارداد (`public-views.test.js`). (۴) S125 و
  PERF-02 روی کانتینر از بودجه رد شدند (۱۹۲ms در برابرِ ۱۰۰؛ ورود ۳۶۸ در برابرِ ۳۰۰) ⇒ سنجش روی سخت‌افزارِ مرجع.
- **کارِ صاحب‌کار (بررسی‌نشده، تا آخر کنار):** روشن کردنِ پرچم‌های غیرِ کم‌خطر (FLAGS-ON)، حساب‌ها (SMS ‏D-09، پرداخت D-08 و
  HesabPay، Firebase، Apple)، گامِ ۴ ِ کلیدِ امضای تازه روی کامپیوترِ خودش، آزمونِ دستگاهِ واقعی (چاپگر، کشو، بارکدخوان، ترازو،
  نمایشگرِ قطبی، NFC، اندروید ۱۷، دو گوشی، پوشِ گوشی)، و کلیدِ API ِ Cloudflare فقط برای رکوردِ A ِ خودکارِ VPS ِ بی تونل.

### ۱۴۰۵/۰۸/۲۲ — عاملِ E (موجِ ۲) زیرِ هماهنگ‌کننده، شاخهٔ `claude/e-rows-9e` (از `claude/batch-51`)

- **انجام شد:** LOC-03 (سرور: هفتهٔ خلاصهٔ پیک از تنظیمِ دکان پشتِ `shop.courier_week_rule`، FLAGS-ON §۳۰؛ برنامه: تنظیمِ هفتهٔ رسیده
  بی بازکردنِ دوباره)، IMG-PRO-13 (`core/image/BankThumbCache`)، IMG-BANK-18 (سنجش روی PostgreSQL، هر دو هدف پاس)، S143 (`S143.series`)،
  S10 (کشتنِ واقعیِ فرایند، `S10KillDesktopTest`).
- **نیمه‌کاره:** IMG-BANK-10 — آزمونِ Compose ِ `ImageBankPicker` با سرورِ ساختگی (`TestHttpServer` + سیم‌کشیِ `Backend` مثلِ
  `NewScreensRenderTest`) ساخته شد ولی نخستین جست‌وجوی هر JVM پس از رسیدنِ درخواست به سرور در «در حالِ جست‌وجو…» می‌ماند
  (۹۰ ثانیه؛ آزمونِ سوم سبز) ⇒ کامیت نشد؛ علت را در `ImageBank.search`/`HttpEngine` زیرِ ساعتِ آزمونِ Compose بجو.
  IMG-11 (نسخهٔ zoom در هیچ کلاینتی) و IMG-01 (رابطِ بانک) کارِ بزرگِ جدا؛ S10 قطعِ برقِ واقعی می‌خواهد.
- **قدمِ بعد:** ادغامِ `claude/e-rows-9e` در دسته؛ شمارهٔ FLAGS-ON ۳۰ و PARITY ۵۸ را پس از ادغام دوباره بشمار.
### ۱۴۰۵/۰۸/۲۲ — شاخهٔ `claude/verify-rows-i` (سنجشِ خودکارِ ردیف‌های «فقط روی دستگاه/شاهدِ CI»؛ روی `claude/batch-51` ‏396171d0a؛ سیزنِ `session_01BwqpPed1SJuwB7Q4nZUbg9`)

- **انجام شد:** هشت آزمونِ تازهٔ صفحهٔ Compose در `desktop/src/test` (`CountReviewUiTest`، `PoSuggestUiTest`، `WholesaleFieldUiTest`، `PromoAppliedUiTest`، `StorefrontSettingsPaneUiTest`، `StaffTimeCardUiTest`، `HeldClaimSheetsUiTest`، `HeatmapPhoneUiTest`) — ۲۶ آزمون سبز؛ تنها تغییرِ کد `CheckoutSheet.PromoApplied` ِ `private` ⇐ `internal` برای آزمون. اجرای کاملِ کامپیوتر ۳۸۸۶ آزمون بی شکست و core ِ هدف‌دار ۲۷۴/۲۷۴. IMPLEMENTED-VERIFIED: INV-09، INV-13، PRD-12، PRM-18، POS-03، RPT-08؛ بقیهٔ ۵۴ ردیف با شاهدِ محلی و «مانده»ِ روشن (سخت‌افزار: «فقط دستگاهِ واقعی — …»).
- **نیمه‌کاره:** صفحه‌های مسیرِ نسخهٔ ۳ (`RouteScope`/`ShellContext`، مثلِ سفارش‌های خرید PUR-02/14) و `StaffTimeHub` (STF-07) هنوز آزمونِ صفحه ندارند — نیازمندِ ساختِ ShellContext و `Backend` ِ آزمونی.
- **قدمِ بعد:** PR و CI؛ با قفلِ کشِ Gradle ِ عامل‌های دیگر، `GRADLE_USER_HOME` ِ جدا (کپیِ `modules-2`/`jars-9`/`wrapper`) و `-Pkotlin.daemon.jvmargs=-Xmx2560m` کار کرد (Kotlin daemon ِ ۴g با OOM کشته شد).
### ۱۴۰۵/۰۸/۲۲ — هماهنگ‌کننده (سیزنِ `session_01BwqpPed1SJuwB7Q4nZUbg9`) — **دستورهای تازهٔ صاحب‌کار؛ از این‌جا ادامه بده**

**دستورهای صاحب‌کار (همه برای هر دو حساب؛ بر بندهای پایین مقدم):**
1. ⛔ **بی‌توقف تا آخر.** «حق نداری پروژه را استاپ کنی.» هر کاری که کد است ساخته و آزموده شود؛ پیام‌ها کوتاه و فارسی.
2. **عامل‌ها:** صاحب‌کار عامل‌های هم‌زمان می‌خواهد («کو ایجنت‌ها؟»). هر عامل: worktree و شاخهٔ جدا، بازهٔ جدای شمارهٔ مهاجرت و بخشِ
   FLAGS-ON، Gradle با `--max-workers=2 -Xmx2g` (کانتینر ۴ هسته/۱۵ گیگ؛ سه Gradle ِ هم‌زمان OOM شد).
3. **هر چه به صاحب‌کار بند است (کلید روشن کردن روی سرورِ خودش، حساب‌های پیامک/حساب‌پی/Firebase/Apple، تصمیمِ باز، آزمون روی
   دستگاهِ واقعی) «بررسی‌نشده — منتظرِ صاحب‌کار» بماند** و کار جلو برود؛ آخرِ کار یک‌جا و ساده از او پرسیده می‌شود.
   به او نگو «برو سرورت را روشن کن/روی سرورت آزمون بزن».
4. **همه چیز روی سرورِ Claude (همین کانتینر) آزموده شود، بی حدس:** سرور (PGlite و PostgreSQL 16)، سناریوهای سرتاسری،
   همهٔ زنجیره‌های مرورگر، آزمون‌های برنامهٔ دکان/کامپیوتر/core (`./desktop/gradlew -p desktop test` همهٔ آزمون‌های
   android-native را هم می‌دواند؛ `dl.google.com` برای همین به شبکهٔ محیط افزوده شد)، پنل (ریپوی server)، و **با و بی
   اینترنت** (`Offline30DaysDesktopTest` با سرورِ واقعی، `SubscriptionOfflineTest`، …). هر چه این‌جا آزمودنی نیست
   (Android emulator: بی KVM؛ آیفون: بی macOS) صریح «سنجیده نشد» گفته شود.
5. اگر صاحب‌کار چیزی را جا انداخته، با جست‌وجو در منابعِ معتبر پیدا و درست شود.
6. **کلیدهای کم‌خطر پیش‌فرض روشن** («کلیدهای کم‌خطر را روشن کن»): ۱۷ کلیدِ نمایشی/راحتی، فقط `false` ِ صریح خاموش — شاخهٔ
   `claude/safe-flags-on` (عاملِ ز).
7. **برنامهٔ سرور:** (الف) پشتیبانِ کامل با دکمه برای بردنِ همه چیز با داده به کامپیوترِ دیگر — `claude/move-ui` ِ ریپوی server
   (عاملِ ح، روی `move-server.mjs`)؛ (ب) چند سرور: خانگی/VPS/خارجی روشن‌وخاموش و «هر برنامه/سایت روی کدام سرور» (مثلاً تانک‌تیل
   روی خانه) — گامِ ۲ «سرورهای من» و گامِ ۳ «جابه‌جاییِ تک‌برنامه + DNS/تونل»، پس از (الف). (ج) Node بماند (گلوگاه دیتابیس است؛
   مقیاس با چند نسخه + PostgreSQL)؛ فقط بخشِ سنگینِ خاص به Go.

**ریشه‌های پیدا‌شدهٔ این سیزن (دوباره نخور):**
- **«هیچ به‌روزرسانی نمی‌آید و کلیدِ تازه در پنل نیست»:** `account-server.yml` ِ ریپوی server نسخهٔ بسته را فقط از
  `server/package.json` ِ shop می‌گرفت و shop روی `2.14.5` ماند ⇒ پنل هیچ بسته‌ای را تازه‌تر ندید. رفع: server **#140**
  (نسخه = `<shop>.<YYYYMMDDHHMM ِ کامیتِ main>`). تا مرجِ آن هیچ دسته‌ای به سرورِ صاحب‌کار نرسیده است.
- **«اشتراک تکون نمی‌خورد؛ بی اینترنت تا ابد کار می‌کند»:** روزِ مانده از تپشِ کش‌شده و قفلِ نرم فقط با تپشِ سرور ⇒ رفع در
  دستهٔ ۵۰ (`SubscriptionClock`، `SoftLock.decide`، RULES «بی اینترنت تا ابد نه»).

**وضع:** دستهٔ ۴۹ ⇒ main (#1034). دستهٔ ۵۰ (#1035: کلیدِ امضا گامِ ۳، WebP، استودیوی عکس، اشتراکِ آفلاین) در CI. دستهٔ ۵۱
(`claude/batch-51`: e-rows-8a/b/c) محلی کامل سبز (کاتلین ۳۸۶۰، مرورگر ۱۷۵۰ ✅)، PR پس از ۵۰. در کار: عامل‌های د/ه
(`claude/e-rows-9d`/`9e`، ۵۵ ردیفِ «کد مانده»)، و (`claude/panel-rows-1` ِ server)، ز، ح. server #139 مرج شد.
**شمار (نسخهٔ ۱):** ۷۴۳ ردیف؛ پس از ۵۱: ۴۵۰ تأییدشده، ۲۵۸ نیمه‌کاره (≈۵۵ کد، ≈۵۶ دستگاهِ واقعی، باقی کلید/حساب/تصمیمِ
صاحب‌کار)، ۵ ساخته‌نشده، ۳۰ بسته به بیرون.

### ۱۴۰۵/۰۸/۲۱ (ب) — هماهنگ‌کننده، دستهٔ ۴۹ (سیزنِ `session_01BwqpPed1SJuwB7Q4nZUbg9`) — **از این‌جا ادامه بده**

- **انجام شد:** #1033 مرج (`21b802fe6`). در `ccr-3a0d593c-ac0jpj-batch-49`: `b8fa597cf` و main ِ تازه، سپس سه نیمه‌کاره:
  - `claude/panel-doors`: بی تغییر؛ ۷ پروندهٔ آزمون ۵۰/۵۰ روی PGlite و PostgreSQL 16؛ با RULES ِ server «۱.۵۰.۸۹/۹۰» می‌خواند.
  - `claude/owner-yes-4` (`3b144409d`): ۴-۳ «الف» تمام — «انتخاب روی نقشه» (`store/pin-map.js`، واژه‌نامهٔ `map`، `shop.storefront.map_pin` + `STORE_MAP_TILES`،
    FLAGS-ON §۲۳، OWNER-ACTIONS §۹ الف ۱۰ و ب ۸؛ `store-map.test.js` ۳/۳، `web-store-zones.mjs` ۱۱۵ ✅)؛ ۳-۱۱ «الف» — ۱۷ مسیرِ دقیق در `server.yml`.
    **مانده از owner-yes-4:** ۴-۲ (libvips/WebP، وابستگیِ تازهٔ سرور) و ۴-۲۳ پرسشِ ۱ (صفحهٔ استودیوی عکس در برنامه) — ساخته نشد.
  - `claude/e-rows-7`: بازبینی و آزمون (سرور ۲۰۹/۰ سرخ، `subwait` ۴۲، `vsearch` ۲۹، `push` ۳۹ ✅)؛ `CourierLabels.clock` بی `java.util` (سنجهٔ راهِ آیفون).
  - پس از ادغام: `card-data` روی «pan» ِ نقشه و توضیحِ `orders.js` سرخ شد ⇒ نام‌ها درست شد (سنجه سست نشد).
- **آزمون روی دسته:** هستهٔ کاتلین **محلی** `gradle -p core jvmTest` ⇒ ۱۸۰۱ آزمون، ۰ شکست (با `LC_ALL=C.UTF-8` و `-Dsun.jnu.encoding=UTF-8`؛ بی آن نامِ
  فارسیِ آزمون «Malformed input» می‌دهد). desktop/اندروید محلی به ۴۲۹ ِ Maven خورد ⇒ فقط CI. همهٔ زنجیره‌های مرورگر محلی (۲۷ اسکریپت) بی ایست سبز.
- **SR49** (بازبینِ مستقل، `SECURITY-AUDIT-fa.md` B14): SR49-1 Medium (شکستنِ شیشهٔ پل بی `X-Actor-Reauth-At`) بسته + همتای پنل server **#139**
  (نسخه 1.50.91)؛ SR49-3 و SR49-4 بسته؛ SR49-2 پذیرفته (قراردادِ پنل).
- **قدمِ بعد:** PGlite ِ کامل ⇒ PR ِ دسته ⇒ CI ⇒ squash؛ server #139 پس از CI. سپس دورِ سومِ شاهدِ CI، ۴-۲ و ۴-۲۳ (۱)، کلیدِ امضا (پایین).
- **کلیدِ امضای تازه:** کلید فقط روی کامپیوترِ صاحب‌کار ساخته می‌شود (`OWNER-ACTIONS-fa.md` §۲ گامِ ۴)؛ پیش از آن گامِ ۳ (`Integrity.kt` با فهرستِ
  اثرِ انگشتِ قدیم و جدید) کارِ Claude است و هنوز ساخته نشده.

### ۱۴۰۵/۰۸/۲۰ — شاخهٔ `claude/panel-doors` (سمتِ سرورِ حسابِ دو درِ پنل: ۴-۱ «ب» مترادف و ۲-۹ دسترسیِ پشتیبانی؛ روی `ccr-…-batch-49` ‏e260b8d74؛ پنل: `vil3ntec-it/server#138` ‏c375c3d، ۱.۵۰.۸۹ و ۱.۵۰.۹۰؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: **`synonyms.v1`** — `GET|PUT /api/admin/search/synonyms` (`routes/admin-synonyms.js`، `lib/search-synonyms.js`؛ سندِ `search_synonyms` ِ `app_config`، بی مهاجرت): سنجشِ همان پنل، ارشد + دلیل، قفلِ نسخه (‎409 synonyms_version‎)، `unchanged`، `Idempotency-Key`، سابقهٔ `admin.synonyms_updated`؛ ویترین پشتِ پرچمِ خاموشِ تازهٔ `shop.storefront.synonyms` (روی `search_core`) همان `text-core.matches` با گروه‌ها. **`support-access.v1`** — مهاجرتِ **۸۱۰** `admin_access_grants` (۰۷۹ ِ طرح گرفته شده بود)، `lib/support-access.js`، `routes/admin-access-grants.js` (`GET|POST /api/admin/shops/:id/access-grants`، `…/break-glass`، `…/:gid/revoke`) و سمتِ صاحب `routes/shop-support-access.js` (`/api/shop/support-access` + `accept` با تأییدِ دوباره، `deny`، `revoke`)؛ خبرِ داخلِ برنامهٔ صاحب، `notified` ِ راست، `useGrant`/`requireGrant(scope)`. سقفِ IP پیش از احراز در `routes/admin.js` (همان ۸۰۰ خط)؛ رده‌ها در `admin-control-plane`، `tenant-matrix`، `paid-routes-entitlement`، `shop-route-inventory`؛ `docs/api/routes.json` تازه.
- آزمون: `search-synonyms` ۸/۸ و `support-access` ۱۴/۱۴ روی PGlite و PostgreSQL 16 (پیش از ساخت: بی کتابخانه هر دو پرونده سرخ؛ بی درها ۶/۸ و ۱۲ از ۱۳ آزمونِ نخست سرخ)؛ جهش‌ها همه سرخ (شرح در ردیف‌های TEN-04، POS-04، WEB-05).
- نیمه‌کاره / تصمیم: `requireGrant` روی هیچ درِ محتوای خصوصی سوار نیست (درهای امروز همان سابقهٔ TEN-04 را دارند)؛ صاحب «شکستنِ شیشه» را خودش نمی‌بندد (‎409 break_glass_owner‎)؛ پلِ پنل برای شکستنِ شیشه `X-Actor-Reauth-At` نمی‌فرستد ⇒ سرورِ حساب به سنجشِ مالک/رمزِ خودِ پنل تکیه دارد. ⚠️ پنل: `synonymKey` ِ پنل رقمِ عربی (٠–٩) را پیش از تبدیل پاک می‌کند (بازهٔ اعراب ‎U+064B–U+0670‎)؛ این‌جا تبدیل می‌شود.
- قدمِ بعدی: PR ِ همین شاخه؛ پنل `X-Actor-Reauth-At` را روی شکستنِ شیشه بفرستد و ترتیبِ `synonymKey` را درست کند؛ درِ خواندنِ مترادف برای برنامه‌ها (POS-04) و رابطِ پذیرفتنِ دسترسی در برنامهٔ دکان؛ سوار کردنِ `requireGrant` با تصمیمِ صاحب‌کار.
### ۱۴۰۵/۰۸/۲۱ — هماهنگ‌کننده (سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)، پایان با سقفِ هفتگی — **از این‌جا ادامه بده**

**در main ِ shop رفت (همه با CI ِ سبز):** دستهٔ ۴۵ (#1029 ⇒ `5ef491d`)، ۴۶ (#1030 ⇒ `4c889d1`)، ۴۷ (#1031 ⇒ `02d3097`)، ۴۸ (#1032 ⇒ `b187c45`).
**در main ِ server رفت:** #138 ⇒ `d8566fb` (نسخهٔ 1.50.90؛ ۸ تصمیمِ صاحب‌کار). ۱۵ PR ِ جایگزین‌شدهٔ server بسته شد (۳-۷ الف: #106–#111، #113–#121). #104 (تغییرِ `CLAUDE.md` و هوکِ `.claude/settings.json`) و #112 (برنامهٔ اندرویدِ مدیر، ۲-۱۴ بی پاسخ) عمداً باز ماندند.

**قاعده‌ها و تصمیم‌های تازهٔ صاحب‌کار (همه در ریپو ثبت است):**
- ⛔ **وبِ مدیریتِ فروشگاه وجود ندارد** — وب فقط ویترین و بازارچه (بالای `docs/RULES-fa.md`). نسخهٔ وبِ دکان در دستهٔ ۴۸ برداشته شد (صفحهٔ «برنامه را نصب کنید» با پشتیبانِ دادهٔ مرورگر؛ `sw.js` خودش را برمی‌دارد و باید تا ۱۴۰۶/۰۲/۰۱ بماند).
- ⛔ **کدِ پمپ دست نمی‌خورد** (ریپوی `pump-staion-yaqobi` و پرونده‌های مشترکِ پمپ در shop: `sync-v1.js`، `routes/sync-v1.js`، `tenancy.js`، `pump-device.js`، …). در این سیزن یک خط هم عوض نشد. صاحب‌کار «پمپ» را نمی‌شناسد؛ هر توضیح ساده باشد.
- ۷۳ تصمیمِ ماندهٔ `OWNER-PENDING` با پیشنهادِ ثبت‌شده پذیرفته شد (`docs/OWNER-DECISIONS-fa.md` بخشِ «گرفته‌شده — ۱۴۰۵/۰۸/۲۰»). ۴ تا هنوز پرسش دارند و با گزینهٔ محتاط جلو رفتیم: ۲-۱۴ (#112 همان بماند)، ۴-۱۲ (Google Play فعلاً نه)، ۴-۲۴ پرسشِ ۲ (دکمهٔ ترازو پس از انتشار)، ۳-۱۰ پرسشِ ۲.
- **«نسخهٔ ۱»** تعریف شد (`docs/spec/RELEASE-1-fa.md`): بازاریابی (MKT-*)، هوشِ مصنوعیِ محلی (AI-09/10/12، S122 و ۴ ردیفِ وابسته) و شارژِ سیم (S121) «پس از انتشار». شمار در `STATUS.md` «خلاصهٔ نسخهٔ ۱».
- صاحب‌کار انتخابِ دو کار را به Claude سپرد: (۱) **کلیدِ امضای تازه: بله**، با چرخشِ کلیدِ APK Signature v3 (lineage) تا اندروید ۹+ بی نصبِ دوباره به‌روز شود؛ یک نسخهٔ کلید باید نزدِ صاحب‌کار بماند — **هنوز ساخته نشده** (کارِ حساس: workflowهای انتشار، رازها). (۲) S46 **بی کدِ پمپ** ساخته شد (پایین).
- PUR-11 ساخته نشد چون D-15 = «الف» (تا دکانِ واقعی نخواهد؛ PR ِ پیش‌نویسِ #784).
- صاحب‌کار از سقف ناراحت است: کم بخوان، کم عامل. خواستِ آخرش: «بدون استاپ پروژه را تمام کن» و سه عامل هم‌زمان.

**دستهٔ ۴۹ — شاخهٔ `ccr-3a0d593c-ac0jpj-batch-49` (هنوز PR ندارد):** S46 (`shop_till_op_users`، مهاجرتِ ۷۹۰، پرچمِ `shop.till_op_user`، فقط مسیرِ دکان)، شاهدِ CI ِ دورِ ۱۷ (`claude/ci-evidence-13`)، تصمیم‌های دورِ ۳ (`claude/owner-yes-3`: ۲-۲۳، ۲-۲۱، ۲-۸، ۳-۱۳، ۴-۲۱، ۳-۱۲، ۳-۱۷، ۳-۱۶، ۲-۱۶، ۴-۲۰ و R25)، جدا کردنِ `order-phone-mask.js` از `orders.js` (زیرِ سقفِ ۸۰۰). precheck روی `e260b8d74` سالم.
- **قدمِ بعدِ ۴۹:** `git merge b8fa597cf` (دو رفعِ پایانیِ ۴۸)، سپس `git diff --quiet b8fa597cf origin/main && git merge -s ours origin/main`؛ ادغامِ شاخه‌های نیمه‌کارهٔ پایین پس از تمام شدنشان؛ PGlite ِ کامل (با سه عاملِ هم‌زمان از ۵۰ دقیقه می‌گذرد — تنها اجرا کن)، زنجیره‌های مرورگر **همه** محلی (در CI پس از نخستین سرخ بقیه اجرا نمی‌شوند)، بازبینِ امنیتیِ مستقلِ SR49، PR، CI، squash-merge.

**نیمه‌کاره (سقفِ هفتگی وسطِ کار؛ هر سه push شده، آزموده نشده):**
- `claude/e-rows-7` (`cbacf5214`، روی `3851fde35`): S117 (پوشاندنِ کارت در یادداشتِ کارمند/پنل)، S93 (حالتِ «منتظرِ پاسخِ جایگزین» در پیگیری)، S126 (جایگزینِ `visual-search.js`)، RPT-13 (کلیدهای −/+، مقایسه و ریز در گزارشِ کامپیوتر — آخرین کار: `FreshPeriodBar` در `ReportKeys.kt`)، S94 (کلیدِ رضایتِ موقعیت در برنامهٔ پیک)، SYN-01 (workflow ِ آزمونیِ تازهٔ `offline30.yml`؛ ⛔ نه `tohid-desktop.yml` که انتشار است). همه یک کامیتِ «نیمه‌کاره»: بازبینی، آزمون، سپس کامیت‌های تمیز.
- `claude/owner-yes-4` (`fef6ad0fb`، روی `e260b8d74`): **انجام**: O-22 ب (۱-۲، برگشتِ تکه‌تکه رو به پایین تا افغانیِ کامل)، ۳-۴ ابزارِ بی‌نام‌کننده (`npm run anonymize`)، نامِ `ConvertFidelityTest`. **نیمه**: ۴-۳ نقشهٔ OSM (بخشِ سرور). **مانده**: ۳-۱۱ (`server.yml`)، ۴-۲۳ پرسشِ ۱ (صفحهٔ استودیوی عکس در برنامه)، ۴-۲ (کتابخانهٔ عکس).
- `claude/panel-doors` (`e8109c594`، روی `e260b8d74`): درهای سرورِ حساب برای پنلِ #138 — مترادف‌ها (`synonyms.v1`، ۴-۱ ب) و دسترسیِ پشتیبانی (`support-access.v1`، ۲-۹). کامیت شده ولی اجرای پایانیِ آزمون‌ها با سقف قطع شد ⇒ دوباره بیازما (PGlite و PG)، قرارداد را با `docs/RULES-fa.md` ِ server بخش‌های «۱.۵۰.۸۹» و «۱.۵۰.۹۰» بسنج.

**کارهای ماندهٔ دیگر (به ترتیب):**
1. کلیدِ امضای تازه با چرخشِ v3 (بالا) — پیش از آن به صاحب‌کار ساده بگو چه گامی با اوست (نگه‌داشتنِ نسخهٔ کلید).
2. دورِ سومِ شاهدِ CI پس از ادغامِ ۴۸ و ۴۹ (روشِ `claude/ci-evidence-1` و `-13`؛ آزمون‌های وبِ دکانِ برداشته شاهد نیستند).
3. کدِ تصمیم‌های مانده و ردیف‌های PARTIAL ِ «کد مانده» (فهرست در گزارش‌های همین بخش و STATUS).
4. کارهای صاحب‌کار (فقط او): پرچم‌ها و خط‌های `.env` در `docs/spec/OWNER-ACTIONS-fa.md` §۸ و §۹، حساب‌های پیامک/حساب‌پی/Firebase/Apple.

**شمار (نسخهٔ ۱، روی شاخهٔ ۴۹ پیش از ادغامِ نیمه‌کاره‌ها):** ۷۴۳ ردیف؛ تأییدشده ۴۴۴، نیمه‌کاره ۲۶۴، ساخته‌نشده ۵، بسته به بیرون ۳۰. بازبینی‌های امنیتیِ این سیزن: SR46-1..3، SR47-1..2، SR48-1..3، R20، R24، R25، چهار هشدارِ CodeQL — همه بسته.

**یادِ کار:** کامیت و عنوانِ PR فارسی با ` (2.14.5)` (server: ` (نسخه 1.50.N)`) و دو خطِ `Co-Authored-By`/`Claude-Session`؛ worktree برای هر کار (شاخهٔ checkout‌شده را دست نزن)؛ `tools/claude-ops/cres.py` برای حلِ تعارضِ HANDOFF/STATUS؛ `node docs/spec/tools/spec.mjs status` و `check`؛ PostgreSQL ِ محلی پس از ری‌استارت: `su postgres -c "/usr/lib/postgresql/16/bin/pg_ctl -D /home/user/pgdata -o '-p 55432 -k /tmp' -l /home/user/pgdata/log.txt -w start"`. هر سرخیِ CI ریشه‌یابی، یک push در هر دور؛ هیچ آزمونی کنار نمی‌رود.
### ۱۴۰۵/۰۸/۲۰ — شاخهٔ `claude/owner-yes-3` (دورِ سومِ تصمیم‌های پذیرفتهٔ صاحب‌کار، «همه شونو تموم کن»؛ روی `ccr-…-batch-48` ‏17f89bc25؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد (سرور، PGlite و PostgreSQL 16، هر کدام پیش از ساخت سرخ): **۲-۲۳** R7/R8 ب — `finalized-sales` ‹`WATCH`›: برداشتنِ ادغام (`merge_undone`)
  و بردنِ قرض/رسید به شیفتِ دیگر (`shift_moved`) آزاد با ردیفِ `security.doc_chain` (فقط نوشتنِ نشسته؛ دستهٔ فقط‌مشتری هم نگهبان می‌گیرد) —
  `doc-chain-watch` ۴/۴. **۲-۲۱** R5 الف — `stock-reserve.phoneCapOk`: هر شماره تا نیمِ آزادِ هر کالا (دست‌کم یک دانه)، بیشتر ⇒ کلِ سفارش بی
  نگه‌داشتن — `stock-reserve-phone-cap` ۳/۳. **۲-۸** CRM-15 الف — `orders.maskerOf` پشتِ پرچمِ خاموشِ تازهٔ `shop.orders.phone_mask` (FLAGS-ON §۲۲):
  بی «دیدنِ تماس» فقط دو رقمِ آخر — `order-phone-mask` ۱/۱. **R25** — `verify-data` ‏`sum.sale_final` گرد را به ±۵ افغانی می‌بندد. **۲-۱۶**
  R2 الف — `product-unit-rules` (و همتای برنامه `ProductDraftRules`): واحدِ خالی روی کالای گردش‌دار قفل، ضریبِ خوابیده با روشن شدنِ واحدِ فروش
  سنجیده — `product-unit-rules` ۱۲/۱۲، `security-review-40` ۵/۵ (سه ادعای کهنهٔ «خالی آزاد» به قاعدهٔ تازه).
- انجام شد (برنامه، Kotlin فقط CI): **۳-۱۳** (۱) `core/sync/SyncRetryAfter` + `ApiFailure.retryAfterMs` + `ReconnectGate.serverAsked` (سقفِ ۵ دقیقه؛
  «همگام‌سازی حالا» همان دم). **۴-۲۱** `PromoHeld.release` (نارسیده، فقط صاحب، ۷ روز، با هشدار). **۳-۱۲** نسخهٔ ۲ ِ `QtyExact` و هم‌زادِ
  `license/money.js` و بردارِ `qty-exact-v1.json` (مقدارِ درست هم؛ `qty-exact.test.js` ۶/۶). **۳-۱۷** `PurchaseSuggest.forSupplier` در هر دو فرمِ
  سفارش (همان پیشنهادِ صفحه، دلیل و اطمینان در گوشی). **۳-۱۶ (۲)** `ReportEngine.stockValue` (دوقلوی `fromLedger`) در گزارشِ محصولات، گزارشِ
  کامپیوتر و مرکزِ مدیریت. **۴-۲۰ (۲)** متنِ «پایانِ روزِ کاری». «تازه‌ها»: یک بخشِ تازه با ۸ سطر.
- نیمه‌کاره / برای CI: آزمون‌های Kotlin ِ تازه یا عوض‌شده اجرا نشده‌اند — `SyncRetryAfterTest`، `ReconnectGateTest`، `PromoHeldTest`،
  `QtyExactTest`/`QtyExactVectorsTest`/`QtyExactWiringTest` (ادعاهای «مقدارِ درست همان امروز» به قاعدهٔ تازه)، `PurchaseSuggestTest`،
  `StockValueBridgeTest`، `SaleUnitExactTest`. نمانده‌ها به ترتیب: **۱-۲** (O-22 ب — هم‌زادِ `ReturnShare`/`returnShare`، بردارِ دستیِ
  `return-share-v1.json` ⇒ v2، پیکرهٔ `core-fuzz` و ادعای «همیشه افغانیِ کامل»، `ReturnCap.capMinor` و ۳۸ آزمونِ برگشتِ Kotlin؛ شاخهٔ جدا)،
  سپس ۳-۴، ۴-۳، ۳-۱۱، ۴-۲۳ (۱)، ۴-۲. «ارزش انبار» ِ کارتِ خانه (`WarehouseEngine.summary.value` = جمعِ ورودی‌ها) دست نخورد.
- قدمِ بعدی: مرجِ این شاخه با دسته؛ شاهدِ CI ِ Kotlin ِ بالا؛ سپس ۱-۲ در شاخهٔ خودش.
### ۱۴۰۵/۰۸/۲۰ — شاخهٔ `claude/ci-evidence-13` (دورِ دومِ شاهدِ CI پس از مرجِ دسته‌های ۴۶/۴۷، و شکافِ SYN-01؛ روی `ccr-…-batch-49` ‏bf70ca7f7؛ فقط سند؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- نامِ شاخه: `claude/ci-evidence-2` روی origin از دورِ قدیم (دستهٔ ۴۱، a5f08332a) هست و STATUS به آن ارجاع می‌دهد ⇒ بی force، نامِ آزادِ `-13`.
- انجام شد: لاگِ push ِ سبزِ main ‏02d309771 (#1031؛ دستهٔ ۴۶ را هم دارد — push ِ #1030 در «سرور» سرخ بود، IMG-PRO-14 ِ PGlite) خوانده شد: «سرور» 42937274724
  (PostgreSQL ۴۱۳۱/۴۱۱۴ سبز، PGlite و Electron هر کدام ۴۱۰۱، همه ۰ شکست؛ مرورگر ۱۸۸۴ ✅ / ۰ ❌؛ E2E همه ✅)، «کامپایل و آزمون» 130250330261 (۵۵۹۶ PASS)،
  «ساخت نسخهٔ بومی» 130250329728 (۷۰۳۴ PASS). ۲۳ ردیف ویرایش شد (STATUS «یادداشت‌ها»، «دورِ هفدهم»): **S67، S68، S86، S118 ⇐ IMPLEMENTED-VERIFIED**؛ ۱۸ ردیف شاهد
  گرفتند و PARTIAL ماندند با «مانده» ِ فقط غیرِ CI (S56: «مانع» ⇐ `shop.orders.weight_hold`)؛ آزمون‌های وبِ مدیریتِ فروشگاه شاهد شمرده نشدند. نسخهٔ ۱: ۴۴۴ IV (از ۴۴۰)، ۲۶۴ PARTIAL (از ۲۶۸).
- SYN-01: SKIP از `OfflineBackend.available` است (`server/node_modules` در کارِ `check` ِ `tohid-desktop.yml` نیست)، نه env/ویژگی؛ آن ورک‌فلو انتشار است و سناریو ≈ ۵٫۵ دقیقه ⇒
  **تغییری داده نشد**؛ نیاز (setup-node + `npm ci` ِ سرور پیش از «آزمون»، یا کارِ جدا در `perf.yml`) در ردیف.
- نیمه‌کاره: هیچ.
- قدمِ بعدی: صاحب‌کار بگوید SYN-01 در کدام ورک‌فلو اجرا شود؛ پس از مرجِ دسته‌های ۴۸/۴۹ همین دور برای ردیف‌هایی که آزمونشان فقط آن‌جاست.

### ۱۴۰۵/۰۸/۲۰ — شاخهٔ `claude/s46-till-user` (S46: کاربرِ سنجیدهٔ صندوق روی سندِ سرور، **بی کدِ پمپ**؛ روی `ccr-…-batch-48` ‏17f89bc25؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: هر op که Sync v1 یا v3 ِ دکان می‌نشاند «کاربرِ سنجیدهٔ صندوق» را در جدولِ کنارِ `shop_till_op_users` می‌گیرد — مهاجرتِ **۷۹۰**
  (+ rollback؛ ۷۷۲–۷۸۹ برای شاخه‌های هم‌زمان خالی ماند)، پرچمِ خاموشِ **`shop.till_op_user`** (کاتالوگِ پرچم‌ها، `FLAGS-ON-fa.md` §۲۱)،
  `lib/till-op-user.js`. نوبتِ `shop.till_pin` ِ **دستگاهِ توکن** در لحظهٔ رسیدن (`till-pin.sessionAt`، تازه؛ `whoAt` همان)، ردیف پس از پاسخِ
  موتور: v1 از سرِ `till-guard.v1Guard` در میان‌افزارِ فقط‌دکانِ `staff-approval.v1Guard` (پیچشِ `res.json` پیش از هر `rejectOps` و پس از
  پالایش‌های `newer-fields`/`contact` — پاسخِ ساختگیِ op ِ برداشته را نمی‌بیند)، v3 در `routes/sync-v3.js` ِ فقط‌دکان. فقط op ِ نشسته؛
  عضو فقط با نوبتِ باز، op ِ نه پیش از آغازِ نوبت و نه دورتر از ۳۰ دقیقه، وگرنه NULL با `basis`. fields/hash ِ op، `oplog`، `sync_rows` و
  پاسخِ push دست‌نخورده؛ خطای نوشتن push را نمی‌شکند. خواندن: `GET /api/shop/till-pin/ops` و `/ops/row` (صاحب/مدیر همه، بقیه خودشان).
  ⛔ `git diff --stat 17f89bc25 HEAD -- server/src/lib/sync-v1.js server/src/routes/sync-v1.js server/src/lib/tenancy.js …` خالی؛ ۲-۶/O-19 لازم نشد
  (یادداشت در `OWNER-DECISIONS-fa.md`). آزمون: `till-op-user.test.js` ۹/۹ روی PGlite و PostgreSQL 16 (پیش از کار ۰/۹ هر دو)؛ ۶۰ پروندهٔ
  هم‌سایه روی PGlite (sync-v1*، sync-v3*، pump*، till-guard، staff-approval، till-pin، security-review-45، فهرست‌های مسیر، api-contract…):
  ۴۹۲ سبز، تنها سرخ `pump-isolation` › «سرور را به کامپیوترِ دیگر ببر» که روی PGlite به `pg_dump` ِ واقعی می‌رسد (محیط؛ روی PostgreSQL
  سبز، پایین)؛ روی PostgreSQL 16 ‏(`oys46_test`، `--test-concurrency=1`) ۲۱ پرونده (till-op-user، till-pin، security-review-45، till-guard، staff-approval، sync-v1/v3 ِ اصلی، pump-isolation، pump، pump-device، فهرست‌های مسیر…): ۲۵۰ سبز، ۰ سرخ، ۱ TODO ِ پیشین. `spec.mjs check` «سالم»، `precheck.sh` ‏fail=0.
- نیمه‌کاره: هیچ. برنامه (Kotlin) دست نخورد: «به دستِ چه کسی» را امروز از سرور نشان نمی‌دهد؛ راهِ قدیمِ `/api/sync` (کهنه، `warnLegacy`) ستون نمی‌گیرد.
- قدمِ بعدی: صاحب‌کار `shop.till_op_user` را برای همان دکانِ آزمایشیِ `shop.till_pin` روشن کند (§۲۱)؛ اگر برنامه روزی «فروشنده (سنجیده)» را
  نشان دهد، از `/api/shop/till-pin/ops/row` بخواند. F6 (فاکتورِ نگه‌داشتهٔ مشترک میانِ کاربرانِ صندوق) هنوز کارِ برنامه است.

### ۱۴۰۵/۰۸/۲۰ — شاخهٔ `claude/sec-review-48` (بازبینیِ امنیتیِ دومِ دستهٔ ۴۸؛ روی `ccr-…-batch-48` ‏a57dae088؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: حملهٔ خصمانه روی `8c493593c..a57dae088` (مالیاتِ POS-37، گالریِ ویترین، دلیلِ «از کار انداختن» + R24، برداشتنِ وبِ دکان، `spec.mjs`) —
  `docs/SECURITY-AUDIT-fa.md` بخشِ **B13**. بسته: **SR48-1 High** — `return-line-cap` مالیاتِ ادعاشده را بی سنجش با نهایی و بی پرچم به سقفِ برگشت می‌افزود
  (فاکتورِ ۱۰۰۰ با «مالیاتِ» ۱۰۰۰ ⇒ برگشتِ ۲۰۰۰، پرچمِ خاموش، همهٔ راه‌ها)؛ **SR48-2 Medium** (پیشین، PAY-09) — گردِ مثبتِ بی سقف همان‌جا؛ هر دو با `extrasOf`
  (فقط آنچه در نهایی آمده، گرد تا ۵ افغانی). **SR48-3 Low** — `verify-data` مالیاتِ منفی را می‌پذیرفت (تخفیفِ پنهان در گزارش پنهان) ⇒ `greatest(0, …)`.
  آزمون‌ها پیش از رفع سرخ، پس از رفع روی PGlite و PostgreSQL 16 سبز.
- نیمه‌کاره: هیچ. باز (فقط گزارش): R25 (گردِ منفیِ بی سقف در گزارشِ `sum.sale_final`، پیشین)، R26 (پیش‌نمایشِ برگشتِ برنامه و `saleHash`، Info).
- قدمِ بعدی: مرجِ این شاخه با دستهٔ ۴۸؛ اگر روزی `sum.sale_final` گرد را هم به `ROUND_MAX_MINOR` ببندد، R25 بسته می‌شود.

### ۱۴۰۵/۰۸/۲۰ — شاخهٔ `claude/web-shop-removal` (برداشتنِ وبِ مدیریتِ فروشگاه، تصمیمِ صاحب‌کار؛ روی `ccr-…-batch-48` ‏64aa440e9؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: رابطِ وبِ دکان رفت — `index.html` ِ ۸۵۴KB، ۲۷ پروندهٔ رابطِ `license/` (`*-ui.js/css`، `shop-sync`، `license-client`،
  `vip`، `support`، `photo-studio`، `ledger-store`، `auto-backup`، `thermal-print`، `stock-transfers`، `orders-desk/risk`،
  `customer-access`، `area-requests`، `web-i18n`، `api-config`)، `license/i18n/` (۱۴ واژه‌نامه)، `sounds/`، `bench/web.mjs`،
  `scripts/receipt-labels.js`. `index.html` = «برنامه را نصب کنید» (fa/دری/پښتو/English): اگر روی مرورگر دفتری مانده (localStorage،
  IndexedDB ِ `tohid-ledger`، دفترِ حساب‌های دیگر، عکس‌های `tohid-photos`) پیش از هر چیز پشتیبانِ قابلِ بازیابی در برنامه می‌دهد
  (قالبِ «دانلود نسخه پشتیبان» ِ وب ⇐ `BackupRestore.decode`؛ با عکس ZIP ِ `shop-data.json` + `photos/<id>.jpg` ⇐ `BackupBundle.read`)
  و فایلِ خامِ بی رمز؛ هیچ چیزی نمی‌نویسد یا پاک نمی‌کند. `sw.js` = کارگرِ خداحافظی (فقط کشِ همان کانال، unregister، بارِ دوباره).
  نگه داشته (هم‌زاد/آزمونِ سرور): هسته‌های با بردار، `dari.js`، `owner-export.js`، `csv-cell.js`، کلاینت‌های همگام‌سازی
  (`sync-core/engine/v3-engine`، `account-code`)، `keymap.js`، `vendor/zxing`. آزمون‌ها: ۲۳ آزمونِ مرورگرِ وبِ دکان و ۹ آزمونِ
  node ِ رابط و `WebPaletteParityTest.kt` رفتند؛ بخش‌های سیم‌کشیِ وب از ۱۳ آزمونِ هسته بریده شد (هسته‌ها همه سنجیده می‌مانند)؛
  تازه: `server/test/web-shop-gone.mjs` ۱۱/۱۱ (`test:web` = web-sync-v3 + همین). `server.yml`: سه گامِ web-ui/web-idb/web-export
  رفت. «تازه‌ها»: ۲۳ سطرِ وب و ۷ بخشِ خالی‌شده رفت، یک «برداشته» بالا. STATUS: ۶۲ ردیف یادداشت (وضعیت دست نخورد)، BLOCKERS «هـ».
  `download.html` (آیفون «به‌زودی»، ۳۲بیتی ⇐ اندروید) و پیامِ ۳۲بیتیِ `install.ps1` و `desktop/setup-all`.
- نیمه‌کاره / برای صاحب‌کار: ⚠️ برنامهٔ آیفون هنوز در App Store نیست (IOS-PLAN §۰.۶) — کاربرِ آیفون تا آن روز فقط اندروید/کامپیوتر دارد.
  ورک‌فلوی انتشارِ Pages (`web-pages.yml`) دست نخورد: `WEB_PATHS` هنوز `license` و `sounds` را دارد (بی‌ضرر) و اگر Source ِ Pages
  «GitHub Actions» است ریشه فقط با Run workflow ⇐ stable عوض می‌شود. سطرهای مشترکِ «برنامه و نسخهٔ وب» در «تازه‌ها» (۴ سطر) و
  متنِ `StrSettings.ledgerOnlyDesc` («همان فایلی که نسخهٔ وب می‌خواند») دست نخوردند. `android/` (پوستهٔ WebViewِ بازنشسته)
  نسخهٔ خودش را در بسته دارد و نصب‌های پیشینش دست نخوردند؛ ساختِ دوباره‌اش فقط صفحهٔ نصب را می‌آورد (پلِ دانلودش پشتیبان را می‌گیرد). `CORS_ORIGIN` ِ سرور (اگر github.io را دارد) را صاحب‌کار می‌تواند بعداً تنگ کند.
- قدمِ بعدی: `sw.js` ِ خداحافظی تا دست‌کم ۱۴۰۶/۰۲/۰۱ بماند؛ پس از آن (و پس از انتشارِ آیفون) می‌شود صفحه را ساده‌تر کرد. کلاینت‌های
  مرجعِ همگام‌سازی را می‌شود روزی به `server/test/` برد.

### ۱۴۰۵/۰۸/۲۰ — شاخهٔ `claude/owner-yes-2` («همه با پیشنهادِ خودت» ِ صاحب‌کار و دامنهٔ نسخهٔ ۱؛ روی `ccr-…-batch-48` ‏fe84f4d5b؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد (ثبت): OWNER-PENDING ۷۳ ⇐ ۴ (۲-۱۴، ۴-۱۲ و پرسشِ ۲ ِ ۳-۱۰ و ۴-۲۴ ماند — پیشنهادشان به واقعیتی بسته است که فقط صاحب‌کار
  می‌داند)؛ ۶۹ بند + پرسشِ ۱ ِ دو بند با متنِ کامل و «پیشنهاد پذیرفته شد» در OWNER-DECISIONS «گرفته‌شده — ۱۴۰۵/۰۸/۲۰» با یادداشتِ
  وضعیتِ هر بند (کد لازم نیست / کارِ شما / کدِ Claude / ریپوی server / ⚠️ استثنا)؛ `spec/OWNER-ACTIONS-fa.md` §۹؛ BLOCKERS (D-01، D-02،
  D-06، D-10، D-11، D-25 و «د) دامنهٔ نسخهٔ ۱»)؛ STATUS ۵۸ ردیف یادداشت، NTF-08 ⇐ IMPLEMENTED-VERIFIED. نسخهٔ ۱: `spec/RELEASE-1-fa.md`
  (۲۱ ردیفِ «پس از انتشار»: MKT-01..12، AI-01، AI-06، AI-09، AI-10، AI-12، PUR-12، RPT-16، S121، S122)، نشان در ستونِ «مانع»، جدولِ
  خودکارِ «خلاصهٔ نسخهٔ ۱» در `spec.mjs status` و سنجشِ «فهرست = نشان» در `check` (`spec.test.mjs` ۱۰/۱۰). قاعدهٔ تازهٔ همان روزِ
  صاحب‌کار: نسخهٔ وبِ دکان (`index.html`، `license/`) قابلیتِ تازه نمی‌گیرد؛ تنها سطحِ وب ویترین و بازارچه است.
- انجام شد (کد): (۴-۲۲) گالریِ ویترین — `lib/storefront-gallery.js`، دو درِ `GET /api/store/:slug/products/:id/gallery[/:tag/:size]`،
  `features.gallery`، `store/gallery.js` + `gallery.{fa,en,ps}.json` ِ دیربار، پرچمِ خاموشِ `shop.storefront.gallery` (+ `shop.image_bank`)،
  FLAGS-ON §۲۰؛ PRD-17 و PRD-27 ⇐ IMPLEMENTED-VERIFIED. (۲-۲۰) درِ مدیرِ «از کار انداختنِ دستگاه» دلیلِ نوشته می‌خواهد. آزمون‌ها:
  `storefront-gallery` ۰/۳ ⇐ ۳/۳ و `admin-console-writes` (آزمونِ تازه ۲۰۰ ⇐ ۴۰۰) روی PGlite و PostgreSQL 16؛ `web-store-gallery.mjs`
  بی ❌ روی هر دو؛ `web-store-allflags`، `web-store-same`، `web-store`، `web-i18n` ۳۰/۳۰، `market-web`، `paid-routes-public-entitlement`،
  `admin-flags`، `console-shell`، `storefront-photo-version`، `admin-control-plane` سبز.
- نیمه‌کاره: ⚠️ `admin-console-writes` «هم‌زمان: چهار ازکارانداختن با یک کلید» روی PostgreSQL گاهی دو پاسخِ تازه می‌دهد — روی پایهٔ
  همین دسته هم (۱ از ۵) سرخ شد، پس از پیش بوده (کلیدِ یکتاییِ `lib/admin-write-once.js` زیرِ هم‌زمانی)؛ دست نخورد. گروه‌های کدِ مانده
  به ترتیب: ۲-۲۳ (R7/R8 سابقه، `lib/finalized-sales.js`)، ۲-۲۱ (سقفِ نگه‌داشتِ هر شماره)، ۲-۸ (پوشاندنِ شماره در سفارش و تحویل)،
  ۳-۱۳ (سه عددِ SYN-12 فقط برنامه)، ۴-۲۱ (PRM-02 ب)، ۳-۱۲ (PRD-08 الف)، ۳-۱۷ (PUR-03)، ۳-۱۶ (۲) ارزشِ موجودیِ برنامه، ۲-۱۶ (PRD-07)،
  ۱-۲ (O-22 ب، هسته و بردار)، ۴-۲۰ (۲) متنِ «پایانِ روزِ کاری»، ۳-۴ (ابزارِ بی‌نام‌کننده)، ۴-۳ (نقشهٔ OSM)، ۳-۱۱ (`server.yml`)، ۴-۲۳ (۱)
  صفحهٔ استودیو، ۴-۲ (وابستگیِ تازهٔ عکس). ریپوی `server`: ۲-۹، ۲-۱۱، ۲-۱۸، ۲-۲۶، ۳-۷، ۳-۹، ۳-۱۵، ۴-۱، ۴-۱۶.
- قدمِ بعدی: هماهنگ‌کننده استثناها را صریح از صاحب‌کار بپرسد (۱-۱، ۲-۱، ۲-۶، ۲-۲۲ (۳)، ۳-۸ (۲)، ۳-۱۴، ۴-۱۳، ۴-۲۳ (۲))؛ سپس همان
  ترتیبِ گروه‌های بالا.

### ۱۴۰۵/۰۸/۲۰ — شاخهٔ `claude/missing-1` (ردیف‌های MISSING: POS-37؛ روی `ccr-…-batch-47` ‏96053218a؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: POS-37 ⇐ PARTIAL — مالیاتِ فاکتور پشتِ پرچمِ خاموشِ `shop.sale_tax` و تنظیمِ دکانِ `tohid-policy:sale_tax*` (بی نرخِ پیش‌فرض، D-11):
  هستهٔ `core/sale/SaleTax` + هم‌زادِ `license/sale-tax.js` + بردارِ `sale-tax-v1.json` («فاکتور» یا «ردیف» با نرخِ هر کالا)، `Sale.taxMinor`،
  `SalesEngine.totals` ⇐ `SaleTaxWiring.totals`، ردیفِ «مالیات» در برگهٔ پرداخت/خلاصهٔ صندوق/رسید (`StrSaleTax`، `ReceiptLayout`)، برگشت
  با سهمِ مالیات (`paidRatio` = `return-line-cap.paidRatio`)، بخشِ تنظیم در صفحهٔ «هزینهٔ خودکار»؛ سرور: `SALE_FIXED`، `verify-data`، پایهٔ
  تخفیفِ `sale-caps`/`SaleCaps` فقط با پرچم (+ بردار)، فاکتورِ عمومی، `saleTax` در `/shop/stock-ledger/config`؛ ردیفِ `s-tax` در
  `ledger-sync-v1.json` (s-full دست نخورد ⇒ `resend-dropped-v1.json` همان). ۱۳ مجموعه ۱۲۶/۱۲۶ روی PGlite و PostgreSQL ۱۶ (تازه‌ها پیش از کد
  سرخ)؛ FLAGS-ON §۱۹، OWNER-PENDING ۴-۶. PUR-11 ساخته نشد: D-15 «الف» ِ گرفته‌شده (تا دکانِ واقعی نخواهد؛ PR ‏#784 پیش‌نویس).
- نیمه‌کاره: Kotlin فقط CI (`SaleTaxVectorsTest`، `SaleTaxFlowTest`، `SaleCapsVectorsTest`، `LedgerSyncCoverageTest`، کامپایلِ
  `SaleTaxSectionAndroid`/`CheckoutRoute`/`DeskSale`)؛ اگر سرخ شد همان پرونده‌ها. گزارش‌ها (سود، X/Z، `QuarterTax`) مالیات را جدا نمی‌کنند.
- قدمِ بعدی: CI ِ این شاخه؛ جدا کردنِ مالیات در `report-core` (سه هم‌زاد + `report-v1.json`)؛ PUR-11 فقط اگر صاحب‌کار D-15 را «ب» کند (#784).
### ۱۴۰۵/۰۸/۲۰ — شاخهٔ `claude/ci-evidence-1` (شاهدِ CI ِ ردیف‌های ردهٔ D؛ روی `ccr-…-batch-47` ‏96053218؛ فقط سند؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: لاگِ کاملِ push ِ سبزِ main ‏5ef491d97 (#1029) خوانده شد — «سرور» 42838745057 (PostgreSQL ۴۰۰۳ سبز، PGlite و Electron هر کدام ۳۹۹۰ آزمون، همه ۰ شکست؛
  مرورگر ۱۷۸۲ ✅ / ۰ ❌؛ E2E همه ✅)، «کامپایل و آزمون» 129907899978 (۵۵۵۲ PASS)، «ساخت نسخهٔ بومی» 129907898914 (۶۹۷۴ PASS)، برنامهٔ خرید ۳۰ PASS؛ هیچ FAIL.
  ۶۱ ردیف ویرایش شد (روش و فهرست در «یادداشت‌ها» ِ STATUS، «دورِ شانزدهم»): **S123 و DLV-06 ⇐ IMPLEMENTED-VERIFIED** (DLV-06 با O-07 = ب ِ گرفته‌شده:
  تماس = گفت‌وگوی سفارش)؛ ۴۱ ردیف «شاهدِ CI» را از «مانده» برداشتند یا شاهدِ «فقط CI» گرفتند و PARTIAL ماندند (کمبودِ دیگرشان سرِ جایش)؛ ۱۸ ردیف یادداشتِ
  «شاهدِ CI روی main نیست» گرفتند. همه: ۴۴۱ IV (از ۴۳۹)، ۲۷۶ PARTIAL (از ۲۷۸)، MISSING ۱۶، BLOCKED-EXTERNAL ۳۱.
- یافته: دسته‌های ۴۶ و ۴۷ هنوز در main نیستند ⇒ آزمون‌های `claude/e-rows-1..6`، `owner-yes-1`، `rpt-04`، `rpt-3`، `chk-addr`، `pos-4`، `nv-audit` در CI ِ main
  اجرا نشده‌اند (S67، S68، S73، S86، S118، S41، S56، S79، S93، S94، S117، S124، S126، PRM-18، RPT-04، POS-40، CHK-08، بخشی از S33/S70/CRM-09/RPT-13/PRD-12).
  شکافِ واقعی: `Offline30DaysDesktopTest` (SYN-01) در CI ِ main هم SKIP؛ آیفون در push ِ main هیچ کاری ندارد (CHAT-06، PRD-07).
- نیمه‌کاره: هیچ.
- قدمِ بعدی: پس از مرجِ دسته‌های ۴۶/۴۷ همین دور برای ۱۸ ردیفِ بالا (همان ابزار: لاگِ کارها + سنجشِ نام‌به‌نامِ نسخهٔ main)؛ STF-07 و ردیف‌هایی که فقط
  «دیدن روی دستگاه» دارند با قاعدهٔ یکسان (ناهمسانیِ دورِ پانزدهم) تعیین‌تکلیف شوند.
### ۱۴۰۵/۰۸/۲۰ — شاخهٔ `claude/e-rows-6` (دورِ ششمِ ردیف‌های PARTIAL ِ ردهٔ E؛ روی `ccr-…-batch-47` ‏d30302a7؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: ORD-15 — بازپرداختِ باقی پس از رفتنِ پیک با تصمیمِ ۱-۶ ب: دفترِ بازپرداخت روشِ `hesabpay` ِ دستی را فقط با
  `shop.orders.manual_refund` و `orders.refund` می‌پذیرد (`order-refunds.methodsOf`، مهاجرتِ ۷۷۰ + rollback)، `/orders/config` ⇒
  `refundMethods`، برنامه تراشهٔ «نقد پس دادم / با حساب‌پی پس دادم» در برگهٔ بازپرداخت (Kotlin، **کامپایل‌نشده**؛ `OrderRefundModelTest`)؛
  `order-refund-manual-method` ۴/۴ (پیش از کد ۳ سرخ)؛ FLAGS-ON §۱۶ ⇐ بخشِ «پس از رفتنِ پیک». S47 — هر دو یادآورِ سرور روزِ دقیقِ پایان
  را دارند (`subscriptions.endDayOf`، متغیرِ `{تاریخ-پایان}`، مهاجرتِ ۷۷۱ فقط برای متنِ پیش‌فرضِ دست‌نخورده)؛ `subscription-end-day` ۴/۴
  (پیش از کد ۴ سرخ)، گامِ «S47» ِ `entitle.e2e.mjs` روز را می‌سنجد. S46 و STF-06: ممیزی، بی کد (STATUS): ستونِ کاربرِ صندوقِ سرور مسیرِ
  push ِ Sync v1 (کدِ پمپ) را می‌خواهد؛ صفِ پس از خروج در برنامه پیش‌تر با SYN-10 بسته است. ۲۲ مجموعه ۱۸۷/۱۸۷ روی PGlite و PostgreSQL ۱۶.
- نیمه‌کاره: شاهدِ CI ِ کاتلین (`OrderRefundModelTest`، کامپایلِ `OrderActionSheet`/`OrdersRouteContent`)؛ اگر سرخ شد همان پرونده‌ها.
  شمارهٔ مهاجرت‌های ۷۷۰ و ۷۷۱ را پیش از مرج با شاخه‌های هم‌زمان بسنجید (یکتا باشد).
- قدمِ بعدی: CI ِ این شاخه؛ ردِ قلم با دستِ پیک در برنامهٔ پیک (ORD-15، کارِ بزرگ)؛ S46 پس از اجازهٔ O-19 (مهرِ `whoAt(req.device)`
  هنگامِ push)؛ STF-06: امضای هر گام با کلیدِ هر کارمند (طراحی).
### ۱۴۰۵/۰۸/۲۰ — شاخهٔ `claude/sec-review-47` (بازبینیِ امنیتیِ دومِ دستهٔ ۴۷؛ روی `ccr-…-batch-47` ‏d30302a7f؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: `SECURITY-AUDIT-fa.md` §B12. SR47-1 (Medium) — `stock-reserve.rebuild` ردیفِ «نگه‌داشته» ای را که دفتر بسته بود داور می‌گرفت ⇒
  بستنِ دوم در دفترِ فقط‌افزودنی (INV-16 برای همیشه ناهمخوان) یا نگه‌داشتهٔ شبح که نگه‌داشتنِ بازِ واقعی را آزاد می‌کرد؛ حالا گونهٔ `stale`
  بی حرکتِ تازه (`reserve-rebuild` ۴/۴، آزمونِ تازه پیش از رفع سرخ). SR47-2 (Low) — سقفِ پولِ بسته‌بندی هر قلم را هم می‌سنجد (`overCap`)؛
  پیش‌تر قلمِ حذف‌شده بیشیِ بی‌رضایتِ قلمِ دیگر را می‌پوشاند. `sr47-weight-hold-race.test.js` ۶/۶ (هم‌زمانیِ واقعی روی PostgreSQL 16: مشتری ×
  کارمند، × بسته‌بندی، × وزنِ تازه، دو پذیرش، مهلت × بسته؛ IDOR ِ هم‌دکان؛ سقفِ هر قلم). جمع‌آوری، جایگزین، رزرو، بازسازی‌ها و R20 بی‌پسرفت
  (PGlite و PostgreSQL 16)؛ `web-store-weigh.mjs` سبز.
- نیمه‌کاره: R21–R23 (Info، فقط گزارش؛ §B12).
- قدمِ بعدی: CI ِ این شاخه و مرج؛ اگر R22 مهم شد، `decide` ِ جایگزین و وزن وضعیتِ سفارش را هم بسنجد.
### ۱۴۰۵/۰۸/۲۰ — شاخهٔ `claude/e-rows-5` (دورِ پنجمِ ردیف‌های PARTIAL ِ ردهٔ E؛ روی `claude/e-rows-4` ‏a64466b6؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: S56 — کارتِ وب: پیگیری کلیدِ `weightHold` (`pick-weight-hold.trackView`؛ فقط هر دو پرچم و نگه‌داشتِ باز)، `GET …/weight-holds`
  با `now` ِ سرور، `store/weight-hold.js` ِ دیربار + واژه‌نامهٔ تازهٔ `kit/i18n/weigh.*` (۱۵ کلید؛ `order` پر ماند)، گامِ مرورگرِ
  `web-store-weigh.mjs` (axe؛ روشن/خاموش، پذیرش، رد، سر رسیدنِ مهلت) در `test:web-store`؛ `pick-weight-hold` ۹/۹ (پایه ۱ سرخ). برنامهٔ
  جمع‌آور (Kotlin، **کامپایل‌نشده**): `PickWeightHoldDto`، `decideWeight`/`holdJson`، `PickWeightHoldUi.kt`، `PickSheet` و `StrPick.hold*`،
  آزمونِ `PickingWireTest`؛ FLAGS-ON §۱۸ ⇐ آن‌چه درست است. S86 — `stock-reserve.rebuild` و `stock-rebuild --reserve` (`reserve-rebuild` ۳/۳،
  پایه ۳ سرخ) و آزمونِ منبعِ v3 ِ بازسازیِ کاتالوگ (`storefront-catalog-source` ۵/۵). S113 ⇐ ردهٔ A (مدلِ پشتیبانی). OWNER-PENDING ۱-۵
  (تفاوتِ وزنِ سفارشِ پرداخت‌شده) و ۴-۲۵ (S113). همه روی PGlite و PostgreSQL ۱۶ ِ محلی؛ `web-store*` بی‌پسرفت.
- نیمه‌کاره: شاهدِ CI ِ کاتلین (`PickingWireTest`، کامپایلِ `PickSheet`/`PickWeightHoldUi`) — اگر سرخ شد، همان پرونده‌ها؛ روشن کردنِ
  `shop.orders.weight_hold` (A)؛ دو پرسشِ OWNER-PENDING.
- قدمِ بعدی: CI ِ این شاخه؛ نامزدها: ORD-15، و هر ردیفِ E ِ دیگر در STATUS.
### ۱۴۰۵/۰۸/۲۰ — شاخهٔ `claude/e-rows-4` (دورِ چهارمِ ردیف‌های PARTIAL ِ ردهٔ E؛ روی `ccr-…-batch-46` ‏4e2a7459؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: S56 — اول `order-picking.js` (۷۹۳/۸۰۰) جدا شد: «جای قفسه» و «وظیفهٔ پیدا نکردم» ⇐ `lib/pick-issues.js` (همان نام‌ها دوباره
  صادر می‌شوند)؛ سپس پرچمِ خاموشِ `shop.orders.weight_hold` (`lib/pick-weight-hold.js`، مهاجرتِ ۷۶۰ + rollback): وزنِ بیش از حدِ سفارشِ
  پرداخت‌نشده ⇒ نگه‌داشت با مهلتِ ۱۰ دقیقه، بسته‌بندی ۴۰۹ `weight_hold_pending`، پذیرش ⇒ همان وزن، رد/مهلت ⇒ `removed` و کسر، سقفِ پولِ
  بسته‌بندی `capOf` ⇒ ۴۰۹ `over_cap`؛ درِ مشتری در `pick-substitutes-store.js` و درِ کارمند `…/pick/weight-holds/decide`؛ `/orders/config`
  ⇒ `weightHold`؛ تأخیرِ چشم‌به‌راهِ مشتری در `order-late-apology` هم شمرده می‌شود. `pick-weight-hold` ۸/۸ (پایه ۷ سرخ). S86 — کاتالوگ:
  `catalog-products.rebuildProducts` و `stock-rebuild --catalog` (SAVEPOINT: پاک و از نو با `upsertProduct`، برگشت، فقط تفاوت)؛
  `catalog-rebuild` ۲/۲ (پایه ۲ سرخ). S118 و S37: کدی نماند (آزمونِ مسیرِ «S118.customer» از e-rows-2 هست و سبز؛ S37 ⇒ C/A) — فقط متنِ STATUS.
  همه روی PGlite و PostgreSQL ۱۶ ِ محلی؛ فهرست‌های مسیر، `upgrade-rehearsal`، `storefront*` بی‌پسرفت.
- نیمه‌کاره: S56 کارتِ «وزنِ بیشتر» در صفحهٔ پیگیریِ ویترین (واژه‌نامهٔ `order` پر است ⇒ بخشِ تازهٔ ≥۱۰ کلید با اسکریپتِ دیربار) و برنامهٔ
  جمع‌آور (Kotlin) ساخته نشد ⇒ پرچم «هنوز روشن نکنید» (FLAGS-ON §۱۸)؛ گرفتنِ تفاوتِ سفارشِ پرداخت‌شده تصمیمِ صاحب‌کار. S86: بازسازیِ
  `stock_reservations` از سفارش‌های باز و آزمونِ منبعِ v3.
- قدمِ بعدی: کارتِ وب ِ S56 (`store/weight-hold.js` + `kit/i18n/weigh.*.json` + گامِ مرورگر با axe) و دکمهٔ برنامهٔ جمع‌آور؛ پرسشِ A
  برای صاحب‌کار: تفاوتِ وزنِ بیشترِ سفارشِ پرداخت‌شده دمِ در نقد گرفته شود یا نه؛ نامزدهای دیگر: ORD-15، S113.
### ۱۴۰۵/۰۸/۱۹ — شاخهٔ `claude/stuck-alert-pages` (R20 ِ بازبینیِ دستهٔ ۴۶؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: R20 بسته — جاروبِ «سفارشِ گیرکرده» (`lib/order-stuck-alert.js`) نامزدها را دسته‌دسته تا آخر می‌خواند، نه فقط ۳۰۰ ِ نخست؛
  `order-stuck-alert.test.js` ۴/۴ روی PGlite و PostgreSQL 16 (آزمونِ «S79 (R20)» پیش از رفع سرخ).
- نیمه‌کاره: R18 و R19 همچنان تصمیمِ صاحب‌کار.
- قدمِ بعدی: ادغام در دستهٔ ۴۷.
### ۱۴۰۵/۰۸/۱۹ — شاخهٔ `claude/sec-review-46` (بازبینیِ امنیتیِ دومِ دستهٔ ۴۶، روی 4e2a7459؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: سه یافته بسته (`docs/SECURITY-AUDIT-fa.md` بخشِ B11): SR46-1 Medium — با `shop.price_cap` عمده کردنِ مشتری (`debtors.priceList`) فقط با
  `sales.discount` ِ بی سقف (v3 و راهِ قدیم، `lib/price-cap.js`)؛ SR46-2 Low — `FOR SHARE` در سنجشِ رضایتِ موقعیتِ پیک تا نقطه پس از «نه» نماند؛
  SR46-3 Info — «سابقهٔ دسترسی» پینِ تحویلِ قرض‌دار را «تماس» می‌شمارد. هر سه پیش از رفع سرخ (`price-list-grant.test.js`، «SR46-2» و «SR46-3»).
- نیمه‌کاره: R18 (سقفِ قیمت در v1)، R19 (سقفِ ۱۰۰ پیامکِ روزانه) و R20 (جاروبِ «سفارشِ گیرکرده» به ۳۰۰ ِ کهنه‌ترِ همهٔ دکان‌ها بسته بود؛ پس از آن در `claude/stuck-alert-pages` بسته شد) فقط گزارش شدند.
- قدمِ بعدی: ادغامِ این شاخه در دسته؛ تصمیمِ صاحب‌کار دربارهٔ R18–R20.
### ۱۴۰۵/۰۸/۱۹ — شاخهٔ `claude/chk-addr` (CHK-08، CHK-10 و پینِ CRM-09؛ روی main ‏cceb5930 (#1027)؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: CHK-10 — سرور تنها داور با یک خانه برای «کجا؟»، ثبت و «خبرم کن» (`storefront-delivery.pinCell`: گردِ شش‌رقمیِ `orders.coord` سپس
  شبکه؛ پیش‌تر پینِ رقمِ هفتمِ کنارِ خطِ شبکه پیش از ثبت «بیرون» و در ثبت «درون» بود)؛ لبه و رأسِ چندضلعی درون؛ پاپیون (دو نقطهٔ جابه‌جا) ⇒ ۴۰۰ `bad_zone` با
  شمارهٔ نقطه‌ها (`crossing`، BigInt)، چندضلعیِ ذخیره‌شدهٔ پیشین بی تغییر می‌گذرد؛ همان سنجش کنارِ کادرِ برنامه (`StorefrontDelivery.crossing` + `savedPolygon`)؛
  بردارِ `core/test-vectors/delivery-zone-v1.json` (فاصله و خانه با پایتونِ جدا). CHK-08 — وب: «پین از برنامهٔ نقشه» پشتِ `shop.storefront.address_extra`
  (`store/pin-text.js` ِ دیربار، همزادِ `PinText` با `pin-text-v1.json`)؛ `AddressRules.round6` ِ هسته نیمه را زوج می‌کرد ⇒ همان `Math.round` ِ سرور.
  CRM-09 — پینِ قرض‌دار: فیلدِ **جدای** `Debtor.deliveryPin` (نه درونِ `DeliveryAddress`: RULES ِ همان بند)، برگهٔ ویرایش، سطرِ پرونده، ادغام، خروجیِ صاحب، و
  فیلدِ تماس در `lib/customer-contact.js`. `Models.kt` با یک‌خطی کردنِ حاشیه‌نویسیِ `cardCode` زیرِ سقفِ ۹۲۵ ماند.
- نیمه‌کاره: آزمون‌های Kotlin (`StorefrontDeliveryVectorsTest`، `AddressRoundVectorsTest`، `PinTextVectorsTest`، چهار آزمونِ تازهٔ
  `CustomerDeliveryAddressTest`) فقط در CI؛ محلی فقط با kotlinc ِ درونِ Gradle (بی دانلود، با جانشینِ JUnit) سبز و روی main سرخ — جز `CustomerDeliveryAddressTest`
  که بی افزونهٔ serialization محلی ساخته نمی‌شود. `routes/data.js` (ثبتِ «چه کسی پرونده را دید») `deliveryPin` را در `full` نمی‌شمارد — بیرون از دامنهٔ این شاخه.
- قدمِ بعدی: CI ِ PR را ببینید (Kotlin)؛ تصمیمِ صاحب‌کار: نقشه روی صفحه، کدِ پستیِ اختیاری (LOC-10)، Plus Code ِ کوتاه بی پین برای «داخلِ محدوده»، و
  مرزِ دقتِ موقعیتِ مرورگر (پینِ ±چند کیلومتریِ کامپیوتر امروز پذیرفته است).
### ۱۴۰۵/۰۸/۱۹ — شاخهٔ `claude/rpt-3` (RPT-13 کلیدهای گزارش و RPT-08 نقشهٔ ساعت × روز، بخشِ وب و keymap؛ روی `ccr-…-batch-44` ‏040ae52c؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: (۱) **keymap** با کد هم‌خوان: `reports.prevPeriod`/`nextPeriod`/`compare` «planned» ⇐ «partial» (در «گزارش‌ها»ی کامپیوتر با `ReportKeysPref` ِ پیش‌فرض‌روشن ساخته بودند و F1 نشانشان نمی‌داد؛ «گزارشِ تازه» ِ کامپیوتر ندارد)، `reports.drill` همان «partial» (Enter فقط در جدول‌های «گزارشِ تازه»)؛ کلیدِ وب: Enter، − / +، **Alt+C** (Ctrl+Shift+C ِ مرورگر بازرسیِ عنصر است) و `webStatus` ِ partial برای این چهار و Ctrl+P / Ctrl+E؛ `KeyMap.kt` و `license/keymap.js` دوباره ساخته شدند.
  (۲) **وب** (`license/report-ui.js`، فقط با «گزارشِ تازه»): − / + دوره (`license/report-period-core.js` = دوقلوی `ReportPeriod`، بردارِ `report-period-v1.json`؛ `ReportPeriod.MAX_DIFF` ِ Kotlin به ⌊(2^53−1)/10⁴⌋ آمد تا دو سو یک عدد بدهند)، Alt+C مقایسه (ستون‌های «دورهٔ قبل»/«تغییر» + خطِ «نسبت به …»)، Ctrl+P / Ctrl+E، Enter ِ ریز (`invoicesOf`/`dayInvoices` در `report-core.js` و رونوشتِ سرور، دوقلوی `ReportCore`، بردارِ `report-drill-v1.json`)، Esc. (۳) **RPT-08 وب**: زبانهٔ «ساعت و روز» (`license/sales-heatmap-core.js` = دوقلوی `SalesHeatmap`، بردارِ `sales-heatmap-v1.json`)، جدولِ واقعیِ ۷ × ۲۴ با عدد و سرخط؛ `sw.js` ‏v75.
  آزمون‌ها (همه روی پایه سرخ): `report-period-vectors` ۴/۴، `report-heatmap-vectors` ۴/۴، `report-drill-vectors` ۲/۲، `keymap.test.js` ۸/۸، `web-shop-i18n` (۲۸ کلیدِ تازه در سه زبان)، کرومیومِ `web-report-fresh.mjs` ۱۷/۱۷ با axe (روشن و تیرهٔ واقعیِ `data-theme`).
- نیمه‌کاره: `ReportPeriodVectorsTest`، `SalesHeatmapVectorsTest` (core jvmTest)، `ReportDrillVectorsTest` (app) و KeyMapTest/ScenarioS50Test با keymap ِ تازه **فقط در CI**. ریزِ «سنِ بدهی»/«ضدتقلب» در وب نیست (`openDebt`/`fraudEvents` دوقلوی JS ندارند)؛ «گزارشِ تازه» ِ کامپیوتر − / + و مقایسه ندارد؛ وب پایانِ روزِ کاری و آغازِ هفتهٔ دکان را ندارد (نقشه با نیمه‌شب و شنبه).
- قدمِ بعدی: شاهدِ CI ِ سه آزمونِ Kotlin در STATUS ِ RPT-13/RPT-08؛ دوقلوی `openDebt`/`fraudEvents` برای ریزِ بدهی و ضدتقلبِ وب؛ − / + در `DeskFreshReports`؛ پشتو/انگلیسیِ ۲۸ کلیدِ تازه را صاحب‌کار ببیند.
### ۱۴۰۵/۰۸/۱۹ — شاخهٔ `claude/inv-3` (INV-21 و INV-20؛ روی `ccr-…-batch-44` ‏040ae52c؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: **INV-21 سرور** پشتِ پرچمِ خاموشِ `shop.catalog_repack` (با `shop.catalog`؛ کاتالوگِ پرچم‌ها، RULES، FLAGS-ON §۸ک): `lib/catalog-repack.js` +
  نوع‌های `repack`/`assemble` ِ `POST /api/shop/catalog/moves` (بی مسیرِ تازه؛ `stock.adjust`) — یک سند = `production` ِ منفی برای هر ورودی و مثبت برای
  خروجی، یک تراکنش زیرِ قفلِ دکان، همان `ref_type`/`ref_id`، دقیق به هزارم با BigInt (نادقیق ⇒ ۴۰۰ `repack_inexact`)، کمبود ⇒ ۴۰۹ بی ردیف، تکرار ⇒ همان
  حرکت‌ها؛ ردیفِ تولیدِ برنامه (`adjKind = repack`) در خواندنِ دفترِ دستگاه `production` ِ پیوسته با کلیدِ کار (نه «رسید + اصلاح»)؛ مهاجرتِ ۷۲۰ (CHECK ِ
  `stock_moves.type` + `production`، rollback با `NOT VALID`). `catalog-repack.test.js` ۸/۸ و ۲۳ پروندهٔ بی‌پسرفت (کاتالوگ، موجودی، قفلِ سند، مسیرها،
  `upgrade-rehearsal`) روی PGlite و PostgreSQL 16 سبز؛ روی پایه ۷ از ۸ سرخ. **INV-21 برنامه:** یک ردیفِ سندِ تولید دیگر تنها پاک/عوض نمی‌شود
  (`Repack.entryFixed` (⇐ `isDocRow`) در `WarehouseEngine.deleteEntry`/`editEntry`، `StrGoods.repackEntryFixed`، یک سطرِ «تازه‌ها») — `RepackTest` ِ تازه **فقط CI**.
  **INV-20:** بی کد — محلِ درونِ انبار در سرور همان پرسشِ ۳ ِ ۳-۲۶ است (`claude/owner-decisions-4`)؛ ماندهٔ کهنهٔ «سریِ دانه‌ها» در STATUS راست شد. `finalized-sales.js` دست نخورد.
- نیمه‌کاره: دکمهٔ وب برای بسته‌بندیِ سرور (فقط API)؛ کیت/خدماتی را کاتالوگِ سرور نمی‌شناسد (فقط برنامه رد می‌کند)؛ `warehouseEntries` در `DOC_FIXED` ِ
  قفلِ سند نیست ⇒ ردیفِ تولید از راهِ v1 با `shop.doc_chain` هنوز پاک‌شدنی است (v3: فقط «لغو» با `data.delete.any`) — عمداً دست نخورد (شاخهٔ دیگری همان پرونده را دارد).
- قدمِ بعدی: CI (`RepackTest`)؛ تصمیمِ صاحب‌کار: (۱) بهای دقیقِ تولید (`stock_bom`) پیش‌فرض شود؟ بی آن ۱۰۰۰ ÷ ۲۴ ⇒ ارزشِ انبار ۱۰۰۰٫۰۸؛ (۲) ۳-۲۶ پرسشِ ۳ برای INV-20؛
  (۳) ردیفِ تولید در قفلِ سندِ v1.
### ۱۴۰۵/۰۸/۲۰ — شاخهٔ `claude/nv-audit` (ممیزیِ ۱۲ ردیفِ NOT-VERIFIED: ۱۱ از S و POS-42؛ روی `ccr-…-batch-44` ‏040ae52c؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: هر ۱۲ ردیف وضعیتِ واقعی گرفت (NOT-VERIFIED ۱۲ ⇐ ۰). قاعده: نرم‌افزارِ کامل که فقط سخت‌افزار/آدم/production کم دارد ⇐ BLOCKED-EXTERNAL با
  نامِ مانع؛ کارِ نرم‌افزاریِ مانده ⇐ PARTIAL. شاهدِ Kotlin فقط خطِ `KTEST` ِ CI ِ 866af600. ردیف‌ها (پیشین ⇐ تازه، چرا):
  POS-42 ⇐ BLOCKED-EXTERNAL (`PosSpeedBenchTest` ِ سقفِ ۵× در CI سبز؛ فقط سخت‌افزارِ مرجع مانده)؛ S24 ⇐ BLOCKED-EXTERNAL (`ScenarioS24Test` ۲/۲ در CI؛
  چاپگرِ برچسب، A4 و بارکدخوان)؛ S54 ⇐ PARTIAL (تمرینِ `restore-drill --migrate` آماده؛ ابزارِ ناشناس‌سازی نیست و کپیِ واقعی production می‌خواهد)؛
  S63 ⇐ BLOCKED-EXTERNAL (`web-idb.mjs` ۷/۷ در CI؛ iPhone ِ واقعی)؛ S82 ⇐ IMPLEMENTED-VERIFIED (سه آزمونِ تازهٔ «S82» در `stock-reserve.test.js`:
  «worker» ِ تکراری، پیامِ دیررسیده، مسابقهٔ قبول/جارو/ردِ خودکار؛ ۲۰/۲۰ روی PGlite و دو بار روی PostgreSQL 16؛ جهشِ کد سرخشان کرد)؛ S91 ⇐
  BLOCKED-EXTERNAL (آزمونِ کاربری؛ نرم‌افزارِ مسیر در CI سبز)؛ S92 ⇐ PARTIAL و S110 ⇐ PARTIAL (`server/test/web-store-weak.mjs` ِ تازه، در
  `test:web-vitals`: 2G ⇒ ۲۵۱KB روی سیم، فقط `/thumb`، heap ~۲٫۳MB، خطای عکس، برگشت از پس‌زمینه، شکستِ بار بی گم شدنِ سبد، و کاهشِ حرکت بی هیچ
  حرکت با کنترلِ «بی تنظیم» ⇒ ۶ حرکت؛ مانده: برنامهٔ خرید، صندوقِ بومی، دستگاهِ واقعی)؛ S125 ⇐ PARTIAL (۵۰ هزار قلم اجرا نشده)؛ S127 ⇐
  BLOCKED-EXTERNAL (`restore-drill.test.js` ۱۳/۱۳؛ رونوشتِ برون‌سرور = production)؛ S128 ⇐ IMPLEMENTED-VERIFIED (آزمونِ بارِ بازتولیدپذیر و
  گزارش‌شدهٔ PERF-02؛ `load-smoke`/`load-correctness` PGlite ۳/۳ و «ok» در کارِ PostgreSQL ِ CI؛ بی ادعای میلیون)؛ S144 ⇐ PARTIAL (لغو و
  `device_revoked` آزموده؛ D-26 ساخته نشده).
  کدِ تازه: `K.photo` (`server/public/kit/kit.js`؛ کارتِ ویترین/بازارچه، نوارِ سبد، برگهٔ کالا) — عکسِ ۵۰۴/بریده/خراب ⇒ حرفِ نخستِ نام، پیش از
  تغییر ۶ سرخ (S126 هم؛ کمبودش در STATUS نوشته شد). دو خطِ RULES («v3 · ویترین»، «v3 · موجودی»)، یک سطرِ WHATS-NEW. شمار: IMPLEMENTED-VERIFIED
  ۴۳۷، PARTIAL ۲۸۰، MISSING ۱۶، NOT-VERIFIED ۰، BLOCKED-EXTERNAL ۳۱.
- نیمه‌کاره: S144/D-26 (کارِ بزرگِ سرور + Kotlin؛ مسیرِ فعالِ برنامه Sync v1 ِ مشترک با پمپ است ⇒ O-19 بندِ ۶) ساخته نشد؛ ابزارِ ناشناس‌سازیِ S54 (منتظرِ O-15)؛ ۵۰ هزار قلمِ
  S125؛ جای‌گزینِ عکس در `visual-search.js`؛ `web-store-weak.mjs` هنوز بی شاهدِ CI. اجرای محلیِ PostgreSQL ِ `load-*.test.js` با `nvaudit_db` را
  خودِ ابزار رد کرد (نامِ پایگاه test/bench می‌خواهد) — شاهدِ PostgreSQL همان CI است.
- قدمِ بعدی: هماهنگ‌کننده شاخه را در دسته بگذارد و پس از CI خطِ «web-store-weak» را به S92/S110/S126 بیفزاید؛ ساختِ D-26 روی Sync v3 (S144) به‌عنوانِ کارِ جدا.
### ۱۴۰۵/۰۸/۱۹ — شاخهٔ `claude/pos-4` (ردیف‌های PARTIAL ِ صندوق: POS-40 در Sync v1، Alt+P ِ POS-41، حروفِ لمسیِ POS-31؛ روی `ccr-…-batch-44` ‏391148a8؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: **POS-40** — یافته: برنامه پیش‌فرض با Sync v1 همگام می‌شود (`SyncChoice`؛ هیچ دکمه‌ای v3 را روشن نمی‌کند)، پس `till-guard` ِ v3 روی دستگاهِ واقعی
  هیچ اصلاحی را نمی‌سنجید. حالا همان پرچمِ `shop.sync.pay_fix` در v1 هم (`till-guard.v1Guard` از سرِ `staff-approval.v1Guard`، صاحب هم؛ `sync-v1.js` دست نخورد):
  شکل، فاکتور، روزِ بسته، مانده (پایگاه‌داده + opهای پیشینِ همان دسته، `batchSource`) و مجوز/برگه؛ سندِ اصلاحِ ثبت‌شده فقط‌افزودنی (`FIX_LOCKED`، سنگِ قبر)؛
  لایهٔ نقش/کاربرِ `sales.pay_fix` با همان پرچم ویرایش‌پذیر (`staff-perms.FLAGGED`) و نامش در فرمِ مجوزِ برنامه. `finalized-sales.js` دست نخورد (`claude/rpt-04`).
  **POS-41** — Alt+P ِ پنلِ پرداخت: `core/pos/PrintToggle`، `CartStore.print`/`togglePrint`/`printShown`، `PrintSpooler.afterSale(print)`، دکمه در `PayPanelDialog`؛
  keymap ⇒ partial (وب planned). **POS-31** — کلیدِ «حروف» در `DebtorPicker(touch)` ِ صندوقِ کامپیوترِ لمسی (F2 و «دریافت»).
  آزمون: `pay-fix-v1.test.js` ۷/۷ (۶ سرخ روی پایه؛ ۹ جهش کشته)، `till-guard` ۱۲/۱۲، `keymap.test.js` ۸/۸ (تازه سرخ روی پایه)؛ Kotlin (`PrintToggleTest`، `PosLogicTest`،
  `CartStorePrintDesktopTest`، `CustomRoleModelTest`، `ScenarioS18Test`) فقط CI. یک WHATS-NEW، چهار خطِ RULES، STATUS ِ سه ردیف (همه PARTIAL).
- نیمه‌کاره: CI ِ Kotlin. POS-41 Tab/Shift+Tab (کامپیوترِ واقعی)؛ POS-31 تشخیصِ خودکار، کادرهای دیگر، «مشتریِ تازه» و آزمونِ دستگاه؛ POS-32 فقط تصمیمِ صاحب‌کار
  (فرستادنِ مستقیم از کامپیوتر) — کدی ننوشتم. پس از مرجِ `claude/rpt-04`: نوعِ `pay_fix` را هم به `SHIFT_DOCS` ِ `finalized-sales.js` بیفزایید تا راهِ قدیم (`/api/sync`) و `shop.doc_chain` هم سندِ اصلاح را قفل کنند.
- قدمِ بعدی: هماهنگ‌کننده شاخه را در دسته بگذارد؛ پس از CI ِ سبز و همه‌گیر شدنِ (2.14.5) تصمیمِ صاحب‌کار دربارهٔ روشن کردنِ `shop.sync.pay_fix`.
### ۱۴۰۵/۰۸/۱۹ — شاخهٔ `claude/rpt-04` (RPT-04: گزارشِ X و Z؛ روی `ccr-…-batch-44` ‏391148a8 که خطِ ارزِ PAY-10 را دارد؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: (۱) **Z ِ بسته در سرور ثابت**: ردیف‌های صندوقِ سابقه (`shift_open`، `shift_close` با `z`، `cash_in`/`cash_out`/`no_sale`، `shift_review`، `shift_resume`) در نگهبانِ S89
  (`lib/finalized-sales.js`، همان پرچمِ خاموشِ `shop.doc_chain`، `DOC_FIXED.auditLog` + `SHIFT_DOCS`): v1، راهِ قدیم و بازگرداندنِ تعارض دیگر Z را سرِ جا عوض/پاک/زنده نمی‌کنند
  (`server/test/z-report-fixed.test.js` ۶/۶، PGlite و PostgreSQL؛ روی پایه ۴ سرخ). (۲) **شمارهٔ Z** در سریِ سندِ دستگاه (`core/pay/ZSeq`، `PayFlow.closeRow(…, series)`،
  `ShiftReport.no/series`، ردیفِ `ShiftSlip.Kind.NUMBER`) و پیوستگی (`DrawerModel.zLines` در «سابقهٔ صندوق» و برگهٔ Z). (۳) **چاپِ X/Z در نسخهٔ ۳** (`ShiftPrintAndroid.kt`،
  مجوزِ `shift.report`) و «چاپِ دوباره» برای Z ِ سابقه (هر دو صفحه). (۴) خط‌های ارز و حساب‌پی ِ X/Z ِ کشوی نام‌دار با مهرِ همان کشو (`CashShift.ownSale`) — همان یادداشتِ `claude/pay-10`.
- نیمه‌کاره: همهٔ آزمون‌های Kotlin (`ZSeqTest`، `ShiftSlipTest`، `PayFlowTest`، `FxTillFlowTest`، `SalesFlowModelTest`) و کامپایلِ خودِ کدِ Kotlin فقط در CI دیده می‌شوند (Gradle این‌جا نیست)؛
  RPT-04 PARTIAL ماند. نسخهٔ ۳ X/Z را بی مجوزِ `shift.report` هم نشان می‌دهد (فقط «چاپ» پشتِ مجوز است) — همان رفتارِ پیشین، دست نخورد.
- قدمِ بعدی: اگر CI ِ Kotlin سرخ شد همان‌جا درست شود؛ تصمیم‌های صاحب‌کار: روشن کردنِ `shop.doc_chain` (حالا Z را هم می‌بندد)، شمارهٔ Z «هر دستگاه» (پیشنهاد، مثلِ فاکتور) یا
  «یکتای دکان» (سرور باید بدهد)، و این‌که نسخهٔ ۳ دیدنِ X/Z را هم پشتِ `shift.report` ببرد. گزارشِ Z برای صاحب در سرور (درِ جدا یا گزارشِ خودکار) ساخته نشد.

### ۱۴۰۵/۰۸/۱۹ — شاخهٔ `claude/e-rows-3` (دورِ سومِ ردیف‌های PARTIAL ِ ردهٔ E؛ روی `claude/e-rows-2`؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: S33 (`public/store/plus-code.js` یک منبع برای ویترین و سرور + `lib/plus-code.js`، پرچمِ خاموشِ `shop.orders.plus_code`:
  `address.pinCode` ِ ساختهٔ سرور در ثبت، اصلاحِ نشانی و تغییرِ روش؛ نشانیِ ذخیره‌شده از پینِ خودش؛ زیرِ پینِ برگهٔ ثبت؛ کارتِ فهرستِ
  مأموریتِ پیک؛ بردارِ مشترکِ `core/test-vectors/plus-code-v1.json` با `PlusCodeVectorsTest.kt`؛ `plus-code` ۴/۴)؛ E2E-RET-11
  (`lib/order-refund-receipt.js`، پرچمِ خاموشِ `shop.orders.refund_receipt`: رسیدِ اصلاحیِ `<سفارش>-R<seq>` برای کارمند و مشتری؛
  `order-refund-receipt` ۳/۳ و گامِ `web-store-changes`). شاهد (روی کدِ پایه هم سبز): S67 (ردِ مجوزِ موقعیت؛ گامِ `ok(true)` ِ بی‌شرطِ
  `web-market.mjs` واقعی شد)، S68 (برچسبِ «≈»)، S73 (برگهٔ سبدها «هر دکان سفارشِ جدا دارد»).
- رفعِ سرخِ پایه: `web-i18n.test.js` روی `claude/e-rows-1`/`-2` دو سرخ داشت (fa.json ۲۱٬۵۱۲ > ۲۱KB با `ck.dup.*` ِ S41؛ order.fa.json
  ۴٬۱۹۲ > ۴KB با `od.courier.none` ِ S93) ⇒ `ck.dup.*` به `checkout.*.json`، `od.courier.none` به اصلی؛ `web-store-dup.mjs` سبز.
- بازطبقه‌بندی (فقط متنِ STATUS): S73 «ثبتِ یک‌جای چند دکان» ⇒ A (تصمیمِ محصول).
- نیمه‌کاره: S33 و E2E-RET-11 پرچمِ خاموش؛ چاپِ رسیدِ اصلاحی در برنامهٔ دکان؛ آزمون‌های کاتلین فقط CI.
- قدمِ بعدی: S56 (وزنِ بیش از سقف ⇒ نگه‌داشتن تا تأییدِ مشتری؛ اول `order-picking.js` ۷۹۳/۸۰۰ را جدا کنید — بخش‌های «جای قفسه» و
  «وظیفهٔ بررسی» (خطِ ۶۴۳ تا ۷۵۱) جدا می‌شوند؛ الگوی «در انتظارِ مشتری» همان `pick-substitutes.pendingOf` و ۴۰۹ ِ بسته‌بندی است)،
  ORD-15 (ردِ قلم دمِ در پس از رفتنِ پیک)، S113.
### ۱۴۰۵/۰۸/۱۹ — شاخهٔ `claude/e-rows-2` (دورِ دومِ ردیف‌های PARTIAL ِ ردهٔ E؛ روی `claude/e-rows-1`؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: S124 (`lib/admin-money.js` + `reconciliation?money=1`: GMV، صندوق، آنلاین، بازپرداخت، وصول، تسویه‌نشده و درآمدِ پلتفرم؛
  فاکتورِ تحویلِ سفارشِ آنلاین `o-…`/`w-…` از صندوق بیرون تا دو بار شمرده نشود؛ `owner-report.salesLedgerOf` جدا شد؛ `admin-money` ۳/۳)؛
  S94 (`lib/courier-location-consent.js`، پرچمِ خاموشِ `shop.delivery_live_location.consent`، مهاجرتِ ۷۵۰ + rollback،
  `PUT /api/courier/location-consent`؛ «نه» نقطه‌ها را همان لحظه پاک می‌کند؛ `courier-location-consent` ۴/۴). شاهد (روی کدِ پایه هم سبز):
  S118 درِ مشتری با بلیتِ منقضی/دست‌خورده/لغوشده (`scenario-s118` ۳/۳)، S39 و S43 پیامِ مشتری از راهِ واقعیِ پیک و دریافتِ حضوری
  (`scenario-s39-s43` ۲/۲)؛ S123 و S37 با آزمون‌های موجود. آزمون‌های تازهٔ کد پیش از رفع سرخ؛ ۲۷ مجموعهٔ مرتبط روی PGlite و PostgreSQL ۱۶.
- بازطبقه‌بندی (فقط متنِ STATUS): S130 (اجبارِ TOTP/احرازِ دوباره env ِ خاموش ⇒ A)، S95 (C/A)، S109 و RPT-18 (پنلِ ریپوی server)،
  E2E-ADM-15 (پنل + کمیسیونِ #432).
- نیمه‌کاره: S124 و E2E-ADM-15 نمایش در پنل (ریپوی server)؛ S94 کلیدِ «اشتراکِ موقعیت» در برنامهٔ پیک (کاتلین) پیش از روشن کردنِ پرچم.
- قدمِ بعدی: شاهدِ CI ِ همین شاخه؛ نامزدها: S33 (Plus Code ِ ساخته از پین در سفارش/نشانی و سایتِ مشتری)، E2E-RET-11 (رسیدِ اصلاحیِ
  بازپرداخت)، S56 (وزنِ بیش از سقف ⇒ نگه‌داشتن تا تأییدِ مشتری؛ `order-picking.js` ۷۹۳/۸۰۰ ⇒ اول جدا کردن)، ORD-15 (تحویلِ بخشی دمِ در).
### ۱۴۰۵/۰۸/۱۹ — شاخهٔ `claude/e-rows-1` (ردیف‌های PARTIAL ِ ردهٔ E «کدِ ساخته‌نشده»؛ روی `claude/owner-yes-1`؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: S117 (`maskCards` در گفت‌وگوی مشتری ↔ دکان، یادداشتِ گزارش، یادداشتِ سفارشِ ویترین/کارمند/قلم/گذار و نظرِ مشتری؛
  `card-data` ۷/۷)؛ S79 (`lib/order-stuck-alert.js` + پرچمِ خاموشِ `shop.orders.stuck_alert` در کاتالوگ و `workers.js`؛ رویدادِ `note`،
  سابقه، پوش؛ وضعیت دست نمی‌خورد؛ `order-stuck-alert` ۳/۳)؛ S41 (`secondOrder` در `parseCheckout` ⇒ `order-rate.guard` ⇒
  `order-risk.checkIn`، `ask: 'track_or_second'`، پرسشِ ویترین با `K.confirm(text, ok, no)`؛ `order-risk` ۱۰/۱۰ و مرورگرِ تازهٔ
  `web-store-dup.mjs` در زنجیرِ `test:web-store`)؛ S93 («پیک تعیین نشده» در پیگیری؛ گامِ تازه در `track.e2e.mjs`)؛ S47 (تاریخِ دقیق در
  بنرِ وب؛ `web-backup.mjs`). هر آزمونِ تازه پیش از رفع سرخ؛ ۳۵ مجموعهٔ مرتبط ۳۰۹/۳۰۹ روی PGlite و PostgreSQL ۱۶ ِ محلی؛
  `web-store`، `web-store-allflags`، `web-store-changes`، `web-store-method` و سرتاسریِ `lifecycle`/`track` سبز.
- بازطبقه‌بندی (نه کد؛ فقط متنِ STATUS): SYN-01، SYN-06، PAY-16، PRD-08، PRM-02، RPT-13 (تصمیم/دستگاه/شاهدِ CI)، OPS-09 (رابطِ پنلِ
  ریپوی دیگر/وبِ منجمد)، CRM-10 و STF-06 (طراحیِ همگام‌سازی، نه کدِ کوچک).
- نیمه‌کاره: S117 (یادداشت‌های کارمند و پنل)، S41 (همین پرسش در `customer-android`؛ پیش‌پرداختِ مشتریِ تازه ⇐ D-08)، S93 («جایگزینی در انتظارِ پاسخ»)، S47 (بنرِ کاتلین
  و قالبِ اعلانِ سرور)؛ SYN-09 و LOC-06 کدِ بزرگ‌اند و شروع نشدند.
- قدمِ بعدی: شاهدِ CI ِ همین شاخه؛ S124 (گزارشِ GMV/صندوق/آنلاین/وصول/تسویه‌نشده/بازپرداخت در پنل) و S118 (آزمونِ درِ مشتری با
  بلیتِ منقضی) نامزدهای دورِ بعد.

### ۱۴۰۵/۰۸/۱۹ — شاخهٔ `claude/owner-yes-1` («همه بله» ِ صاحب‌کار به ۱۹ بندِ `OWNER-PENDING`؛ روی دستهٔ ۴۵ + `claude/owner-decisions-4`؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: (۱-۱۱) مجوزِ هسته‌ایِ `sales.free_line` (perm-core JS×۲ و Kotlin، perm-v1، approval-core×۲، `RemoteApproval`، staff-perms FLAGGED)
  و قاعدهٔ «قلمِ آزاد» + «عمده فقط برای مشتریِ لیستِ عمده» در `sale-caps.js` = `SaleCaps.kt` (بردارِ تازهٔ sale-caps-v1)، داوریِ
  `price-cap.js` (v3، قلمِ دیرآمده، REST؛ برگهٔ `freeLineApproval`)، پیش‌سنجیِ `SaleCapGate`، فیلدِ `Sale.freeLineApproval` (+ بردارهای
  ledger-sync و resend-dropped با هشِ کاتلین بازسازی‌شده، core-fuzz نو)؛ (۱-۶) `lib/order-pack-refund.js` پشتِ `shop.orders.manual_refund`
  + دکمه‌های `PickSheet` و حساب‌پیِ دستی در تسویهٔ ویرایش؛ (۱-۱) پیش‌فرضِ سقفِ پیامک ۸۰ + ۲۰ = ۱۰۰؛ (۳-۲۲) «گزارشِ تازه» پیش‌فرض
  روشن (وب و برنامه، خاموشِ کاربر می‌ماند) و `market.same_in_store`؛ (۴-۲۵ (۳)) `compose.desktop.uiTestJUnit4` + `PromoLinesUiTest`.
  سند: OWNER-PENDING ۹۰ ⇐ ۷۱ (شماره‌ها پیوسته)، OWNER-DECISIONS «گرفته‌شده — ۱۴۰۵/۰۸/۱۹»، `spec/OWNER-ACTIONS-fa.md` §۸ (کلیک‌ها و سه
  حساب)، FLAGS-ON §۱۵–۱۷، STATUS (۱۲ ردیف؛ POS-02 ⇐ IMPLEMENTED-VERIFIED)، RULES، WHATS-NEW. آزمون‌ها: price-cap ۱۰/۱۰، order-picking
  ۱۵/۱۵، order-amend-paid ۴/۴، sale-caps ۷/۷، sms-daily-cap، core-fuzz، sync-resend-dropped و ledger-coverage روی PGlite و PostgreSQL ۱۶؛
  `web-report-fresh.mjs`، `web-store-same.mjs` و `web-pos-qty.mjs` با axe سبز؛ تازه‌ها پیش از کد سرخ.
- نیمه‌کاره: همهٔ Kotlin فقط CI (`SaleCapsVectorsTest`، `SaleCapGateTest`، `ReportFreshDefaultTest`، `PromoLinesUiTest` و وابستگیِ
  آزمونِ Compose، `LedgerSyncCoverageTest`/`DroppedResendTest` با بردارِ دستی‌ساز)؛ همهٔ کلیدها خاموش‌اند تا صاحب‌کار در پنل بزند.
  تسویهٔ ویرایش در برنامه هنوز فقط «نقد» می‌فرستد (سرور حساب‌پیِ دستی را می‌پذیرد). Sync v1 قلمِ آزاد را نمی‌سنجد (همان سقفِ قیمت).
- قدمِ بعدی: CI ِ سبز (اگر `compose.desktop.uiTestJUnit4` در CI حل نشد، همان خط و آزمون را بردارید و در PR بگویید)؛ پس از
  کلیک‌های §۸ ِ OWNER-ACTIONS هر ردیف را با شاهدِ دکانِ آزمایشی بالا ببرید.

### ۱۴۰۵/۰۸/۱۹ — شاخهٔ `claude/sec-fix-44` (پیگیریِ بازبینیِ دومِ دستهٔ ۴۴: R10، R12، R11؛ روی `claude/sec-review-44` ‏671931d3؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: (۱) R10: `license/csv-cell.js` (بارکننده `shop-sync.js`، `sw.js` ‏v72) `csvCell` ِ index.html ِ یخ‌زده را با همان `finance-reports.cell` ِ سرور عوض می‌کند ⇒
  هر چهار CSV ِ صفحه (گزارش‌ها، صورت‌حساب، انبارگردانی، «گزارشِ تازه») بی فرمول، عددِ منفی عدد. `web-csv-cell.test.js` ۵/۵، `web-csv-export.mjs` ۶/۶ (روی پایه ۵ سرخ؛
  در `test:web`). (۲) R12: `sweep` در هر `claim` کارِ «در صف»/«در کار با اجارهٔ تمام‌شده» ِ دستهٔ لغوشده را می‌بندد. (۳) R11: بودجهٔ `IMAGE_STUDIO_DECODE_MB` میانِ
  کارگرها (پیش‌فرض بی تغییر). `security-review-44.test.js` ۶/۶ و `image-studio-jobs.test.js` ۱۱/۱۱ روی PGlite و PostgreSQL ‏16 (R12 و R11 پیش از رفع سرخ).
  پیگیریِ B9 ِ ممیزی، دو خطِ RULES. (۴) به خواستِ هماهنگ‌کننده: متنِ فقط-عدد `^-?[0-9]+(\.[0-9]+)?$` (همان خانهٔ `g` ِ xlsx) در `finance-reports.cell`
  و `csv-cell.js` با هم بی «'» ⇒ پولِ منفیِ «گزارشِ تازه» عدد؛ `-2+3`، `-1e5`، `+500`، `-.5`، `- 500`، `-150.00%` همان «'». سنجه: `web-csv-cell.test.js` ۵/۵،
  R10 ِ `security-review-44.test.js` (خروجیِ مالیِ پنل)، گامِ «گزارشِ تازه» ِ `web-csv-export.mjs` — هر سه پیش از استثنا سرخ.
  (۵) R18 (نامِ R13 به خاطرِ B10 ِ `claude/sec-review-45` عوض شد): `image-bank.reportCsv` هر خانه را از `finance-reports.cell` می‌گذراند (پیش‌گیرانه؛ امروز هیچ
  ورودیِ کاربر به آن گزارش نمی‌رسد)؛ فهرستِ نویسنده‌های CSV (سرور/وب/پنل) در `web-csv-cell.test.js` ۶/۶. `security-review-44.test.js` ۷/۷ (PGlite و PostgreSQL).
- نیمه‌کاره: هیچ. پیش‌فرضِ کم‌ترِ `IMAGE_STUDIO_DECODE_MB` برای سرورِ خانگیِ کم‌حافظه — تصمیمِ صاحب‌کار.
- قدمِ بعدی: هماهنگ‌کننده شاخه را پس از `claude/sec-review-44` در دسته بگذارد؛ `VERSION` ِ `sw.js` با شاخه‌های دیگرِ دسته هماهنگ شود.
### ۱۴۰۵/۰۸/۱۹ — شاخهٔ `claude/ci-evidence-3` (شاهدِ CI ِ دستهٔ ۴۳ برای ردیف‌هایی که آزمونِ Kotlin شان «فقط در CI» بود؛ روی main ‏cceb5930 (#1027)؛ فقط سند؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: لاگِ سرِ سبزِ #1027 ‏866af600 خوانده شد — KTEST ِ «کامپایل و آزمون» 129710301971 (۵۴۹۰ PASS، ۷ SKIP)، «ساخت نسخهٔ بومی» 129710297176
  (۶۸۷۹ PASS، ۴ SKIP)، «برنامهٔ خریدِ مشتری (گزارش)» 129710297127 (۳۰ PASS)، دیسکِ ویندوز ۱۴۱ و مک ۱۴۰ PASS (+۱ SKIP ِ ACL ِ ویندوز)، iOS ِ core
  129712432526 (۲۳۱ `<testsuite>` بی شکست)، سرور (run 42780861831) و مرورگر 129710301956؛ **هیچ FAIL**؛ SKIPها همه بی‌محیط (`ShopperLiveTest`/`ShopperStage2LiveTest`
  بی سرور — در کارِ برنامهٔ خرید PASS —، `AtRestDiskTest` ِ ویندوز، دو سنجهٔ کارایی، `NewScreensRenderTest`، `Offline30DaysDesktopTest`). ۲۵ ردیف شاهدِ کلاس‌به‌کلاس
  گرفتند (PASS = `@Test`) و «فقط در CI/بی شاهد» از آن‌ها برداشته شد؛ فقط **CHAT-14** جز شاهد چیزی نداشت ⇒ IMPLEMENTED-VERIFIED. همه: ۴۳۶ IV (از ۴۳۵)، ۲۶۱ PARTIAL (از ۲۶۲).
- PARTIAL ماندند: تصمیمِ صاحب‌کار (`OWNER-PENDING-fa.md`) — NTF-08 (۱-۹)، PRD-17 و PRD-27 (۴-۲۲)، PRM-02 (۴-۲۱)، CRM-03 و STF-03 (۱-۸)، SYN-12 (۳-۲۱)، LOC-03 (۴-۲۰)،
  LOC-04 (۴-۱۹)، PRD-08 (۳-۲۰)، RPT-14 (۲-۱۷)، POS-10، OPS-06، CHAT-07 (۳-۷ + آیفون)؛ دستگاه — CHK-18 و ORD-20 (+۳-۲۳)، CHAT-02، RSK-03، RSK-04، MKT-06 (+O-01)؛
  دیگر — CHK-03 (D-09)، LOC-06، SYN-01 (`Offline30DaysDesktopTest` در CI هم SKIP)، RPT-13 (`keymap.json` هنوز `reports.drill` «partial» و سه کلیدِ دوره «planned»).
- یافته: در «ساخت نسخهٔ بومی» هر کلاسِ `core` دو بار می‌آید (`:core:jvmTest` و `:jvmTest` ِ جدا) ⇒ PASS = ۲ × `@Test`؛ `ScreenGuidesTest` ۵ `@Test` دارد، نه ۶ ِ STATUS.
- نیمه‌کاره: هیچ.
- قدمِ بعدی: ردیف‌های فقط-JS با «شاهدِ CI هنوز نه» (مثلاً DLV-06 که O-07 اش گرفته شد) با همین لاگ‌ها؛ RPT-13: هم‌خوان کردنِ `keymap.json` با آنچه ساخته شد.
### ۱۴۰۵/۰۸/۱۹ — شاخهٔ `claude/owner-decisions-4` (فهرستِ یکتای تصمیم‌های مانده، دورِ چهارم؛ فقط سند؛ روی main ‏cceb5930 (#1027)؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: `docs/OWNER-PENDING-fa.md` ۷۹ ⇐ ۹۰ (۱۱ / ۲۶ / ۲۸ / ۲۵؛ شماره‌ها با اسکریپت: بی تکرار، پیوسته، هر ارجاعِ «گ-ش» موجود)، هر بند با متنِ منبع سنجیده:
  ۱-۱۰ PAY-10 (یک روز / ۱۰ دقیقه، نیمه رو به بالا، `FX_EXCESS`، پولِ پسِ بی سقف، IRR بی اعشار و واحدهای ۱ / ۱٬۰۰۰ / ۱٬۰۰۰٬۰۰۰ از `license/fx-till.js`)،
  ۱-۱۱ POS-10/21 + PRD-12 (عمده بی مجوز)، ۲-۲۳ R7/R8 (B8، در main)، ۲-۲۴ R10 (B9)، ۲-۲۵ `shop.till_pin` + STF-07، ۲-۲۶ `panel.db`، به‌روزرسان، مخزن، امضا و
  مقدارِ env ِ سایت برای viewer (ریپوی `server`: «۱.۵۰.۸۲» در main، «۱.۵۰.۸۳» در `claude/server-48` تا f09672b)، ۳-۲۶ `shop.stock_v2` / ارزشِ موجودیِ D-23 /
  انبار و محل، ۳-۲۷ PUR-03، ۴-۲۳ استودیوی عکس (+ R11/R12)، ۴-۲۴ HW-07/09، ۴-۲۵ POS-02 / PRD-12 ِ نام‌دار / PRM-18. یکی‌شده: ۲-۸ (۴+۳)، ۲-۱۰، ۲-۱۱ (کاربرِ
  غیرِ root)، ۲-۱۳، ۲-۱۸ (چهار از هفت در ۱.۵۰.۸۰؛ دانلودِ پشتیبان)، ۳-۳، ۳-۱۲، ۳-۲۲ (+ `shop.owner_report.profit`)، ۴-۱، ۴-۲، ۴-۷ (AI-01/09/12)، ۴-۱۰ (en/ps ِ
  `claude/rpt-2`)، ۴-۱۱ (مجوزِ چندپخشِ HW-12). «گرفته‌شده»: R9، نوشتنِ انتشار فقط مالک، کلیدهای پرخطرِ پنل.
- نیمه‌کاره: بی منبعِ «تصمیم» و نیامد: هشدار و «فقط کپی» ِ نشانیِ پیگیری (CHK-20 فقط D-09 ِ ۱-۱)، کلیدهای گزارشِ وب (RPT-13 دست نخورد)، جای «موجودیِ هر مکان».
  R10/R11/R12 در `claude/sec-fix-44` (push‌نشده) در دستِ رفع‌اند؛ با مرجش ۲-۲۴ و یادداشتِ ۴-۲۳ به «گرفته‌شده». تاریخ: ساعتِ سیستم ۲۰۲۶/۱۱/۱۰ = ۱۴۰۵/۰۸/۱۹.
- قدمِ بعدی: پاسخِ صاحب‌کار؛ با مرجِ هر شاخهٔ منبع «هنوز در main نیست» ِ بندها برداشته شود. ⚠️ `pay-10` (دستهٔ ۴۴) و `stf-2` هر دو بخشِ «۸ط» ِ `FLAGS-ON-fa.md` را
  دارند؛ هنگامِ ادغام یکی «۸ی» شود (OWNER-PENDING به نامِ بخش ارجاع می‌دهد، نه شماره).
### ۱۴۰۵/۰۸/۱۹ — شاخهٔ `claude/till-pin-2` (پیگیریِ B10: R14 و R15 ِ PIN ِ صندوق، روی `claude/sec-review-45` ‏2151eff7؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: R14 — `till-pin.unlock` فقط قفلِ کنونی را برمی‌دارد و شمار می‌ماند (۵ خطای بعدی ۳۰ دقیقه)؛ `VOID_AFTER` = ۱۰ خطا از آخرین درستی ⇒ PIN باطل
  (`pin_hash=''`، سابقهٔ `till_pin.invalidated`)، فقط PIN ِ تازه (خودِ عضو `PUT /me` یا صاحب/مدیر) برمی‌گرداند؛ صاحب با تأییدِ دوباره همیشه برمی‌گردد. R15 —
  «ندارد/باطل» ِ `/verify` فقط برای خودِ عضو و `members.view`؛ بقیه همان ۴۰۳/۴۲۳ ِ PIN ِ غلط با ردیفِ جانشین و PBKDF2 ِ ساختگی. `/config` و `/members`
  حالا `invalidated` دارند. آزمون: سه آزمونِ تازه در `security-review-45.test.js` (پیش از رفع سرخ، پس از رفع سبز؛ PGlite و PostgreSQL 16) و یک قید در
  `till-pin.test.js` (بازکردن دیگر شمار را صفر نمی‌کند). برنامهٔ اندروید پاسخِ خطای `/verify` را نمی‌خواند ⇒ بی تغییر. B10، FLAGS-ON §۸ی، RULES.
- نیمه‌کاره: هیچ. R13 (قفل برای هر هدف، نه هر (هدف، حسابِ دستگاه))، R16 و R17 هنوز تصمیمِ صاحب‌کار.
- قدمِ بعدی: هماهنگ‌کننده شاخه را در دسته بگذارد.
### ۱۴۰۵/۰۸/۱۹ — شاخهٔ `claude/sec-review-45` (بازبینیِ امنیتیِ دومِ دستهٔ ۴۵، روی `ccr-…-batch-44` ‏391148a8 + هشت شاخهٔ دسته؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: هشت شاخه (`stf-2`، `crm-2`، `web-2`، `pos-3`، `pur-2`، `prd-prm`، `syn-3`، `hw-2`) یکی‌یکی ادغام (ادغامِ آخر df7a6ac0) و حمله شدند؛ سه یافتهٔ Low،
  هر سه با آزمونِ سرخ‌پیش‌از‌رفع (`server/test/security-review-45.test.js`، ۳ از ۳ روی PGlite و PostgreSQL 16): SR45-1 ردیف‌های `*` ِ «خروجیِ همه» دیدنِ
  واقعیِ مشتری را از ۲۰۰ ردیفِ فهرستِ صاحب بیرون نمی‌رانند (`customer-access.list`، `ALL_SHARE`)، SR45-2 PBKDF2 ِ گذاشتنِ PIN پس از سنجشِ عضو و اختیار و
  سطلِ `till-pin-set` ِ ۲۰ در دقیقه، SR45-3 تعلیق/برداشتنِ عضو PIN ِ صندوق و نوبتِ بازش را پاک/بسته می‌کند (`till-pin.memberGone` در `shops.updateMember`).
  B10 ِ `SECURITY-AUDIT-fa.md` (شش شاخهٔ دیگر سالم)، یک خطِ RULES کنارِ STF-04. ۱۸ پروندهٔ آزمونِ مرتبط (۱۴۹ آزمون) روی PGlite و PostgreSQL سبز.
- نیمه‌کاره: هیچ. R13 (قفلِ PIN ِ هدف با حدسِ عضوِ دیگر)، R14 (مدیر با `unlock` حدس را از سر می‌گیرد)، R15–R17 (Info) تصمیمِ صاحب‌کار.
- قدمِ بعدی: هماهنگ‌کننده شاخه را در دسته بگذارد.
### ۱۴۰۵/۰۸/۱۹ — شاخهٔ `claude/syn-3` (ردیف‌های P0 ِ SYN-04/05/06: پاسخِ push ِ v3 روی خودِ دستگاه و push ِ فشردهٔ v3؛ روی main ‏cceb5930 = دستهٔ ۴۳ (#1027)؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: (۱) SYN-05/06: سرور `current` را برای درجِ سندِ ساعت‌کج (`correctedAt`) و برای «تکراری» ِ پاسخِ گم‌شده هم می‌دهد
  (`lib/sync-v3-apply.js`، `amendOf` ِ `lib/sync-v3-time.js`)؛ وب `currentFixes` و کاتلین `V3Current` آن را پیش از بیرون رفتنِ ورودی از صف
  روی دفتر و سایه می‌نشانند (بردارِ `core/test-vectors/v3-current-v1.json`). سه شکافِ داده بسته شد: دستگاهِ ساعت‌کج با قیمتِ بازنده می‌ماند،
  حذف‌کنندهٔ کهنه با Pull ردیفِ نیمه می‌ساخت، `correctedAt` به دستگاهِ سازنده نمی‌رسید (سطرِ PENDING ِ `scenario-s28b` ⇒ assert).
  (۲) SYN-04: push ِ v3 ِ فشرده پشتِ همان `sync.gzip_push` (وب و کاتلین). ⛔ `sync-v1.js`، `finalized-sales.js`، `sync.js`، `fromV2`/`unitClash`
  و `convertFields` دست نخوردند؛ `sync-v3-engine.js` با فشرده‌نویسی در همان سقفِ ۱۲۵۸ ماند و `SyncV3Engine.kt` ۷۹۸ خط؛ بی مهاجرت، بی مسیرِ تازه.
- نیمه‌کاره: کاتلین (`V3Current`، `V3Gzip`، `V3CurrentVectorsTest`، `V3GzipTest`، آزمونِ تازهٔ `SyncV3EngineTest`) فقط در CI؛ v3 هنوز پیش‌فرض
  خاموش و بی دکمه (تصمیمِ صاحب‌کار)؛ push ِ v1 ِ کاتلین هنوز ساده؛ v3 روی 2G سنجیده نشد.
- قدمِ بعدی: شاهدِ CI ِ همین آزمون‌ها و `sync-v3-current`/`sync-v3-gzip-push`/`web-sync-v3`/`scenario-s28b` ⇒ STATUS؛ سپس push ِ فشردهٔ v1 ِ
  کاتلین با همان `V3Gzip` (پس از `gzip_push` ِ pull ِ v1).
### ۱۴۰۵/۰۸/۱۹ — شاخهٔ `claude/prd-prm` (ردیف‌های PARTIAL ِ PRD-12، PRM-11، PRM-18؛ روی main ‏cceb5930 (#1027)؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: **PRM-11، کانال** — ویرایشگر هر قاعدهٔ تازه را «فقط صندوق» می‌ساخت و کانال را هرگز عوض نمی‌کرد ⇒ تخفیفِ ویترین (MKT-01) ساختنی نبود.
  `GET /api/shop/promos` با پرچمِ `shop.storefront.promos` کلیدِ `online` دارد ⇒ فرم «کجا اعمال شود» (صندوق/ویترین/هر دو؛ `PromoForm.channel`، دست‌نخورده ⇒ همان فهرست) و
  متنِ دامنه؛ `POST /redeem` کدِ «فقط ویترین» را `wrong_channel` رد می‌کند (همه یا هیچ). `shop-promos` ۱۰/۱۰، `promo-redemptions` ۹/۹ (PGlite و PostgreSQL ۱۶؛ پیش از رفع ۲ سرخ)؛
  `PromoChannelTest` فقط CI. شاهدِ CI ِ `storefront-promo-groups` (main ‏99036497) در STATUS. PRD-12 و PRM-18 ممیزی شدند: کدی نماند (STATUS).
- نیمه‌کاره: کامپایل و `PromoChannelTest` فقط CI. تصمیمِ صاحب‌کار: لیستِ قیمتِ نام‌دار (PRD-12)؛ فروشِ خرده به قیمتِ عمده بی سقف/مجوز (`sale-caps.refUnits` + چیپِ بی مجوزِ صندوق)؛
  چارچوبِ آزمونِ Compose برای PRM-18 (وابستگیِ تازه). وبِ دکان (`index.html` ِ یخ‌زده) نه تخفیفِ خودکار دارد نه عمده.
- قدمِ بعدی: هماهنگ‌کننده شاخه را در دسته بگذارد؛ پس از CI ِ سبز شاهدِ PRM-11 و ارتقا اگر چیزی جز گروه در `redeem` نماند؛ دو تصمیمِ بالا برای `OWNER-PENDING-fa.md`.
### ۱۴۰۵/۰۸/۱۹ — شاخهٔ `claude/hw-2` (خانوادهٔ HW، دورِ دوم: شکاف‌های کدیِ بی‌نیاز به دستگاه؛ روی main ‏cceb5930 (#1027)؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: HW-12 — پیدا کردنِ چاپگرِ خامِ شبکه با mDNS/DNS-SD (`_pdl-datastream._tcp`): `core/hw/Mdns.kt` (پرسش، خواندنِ بسته با فشرده‌سازیِ نام،
  PTR ⇐ SRV ⇐ A، فقط RFC1918)، `MdnsProbe` در core/jvmMain (پرسشِ یک‌باره و پاسخِ تک‌پخشِ RFC 6762 §6.7، بسته به نشانیِ کارت)،
  `PrinterFinderPlatform.mdnsPrinters` (اندروید پس از `LocalNetworkAccess.blocked` ِ HW-13؛ کامپیوتر؛ آیفون فهرستِ خالی) و هم‌زمان با جست‌وجوی ۹۱۰۰
  در «پیدا کردنِ خودکارِ چاپگر» (پشتِ همان `easy_setup` ِ خاموش). بردارهای `core/test-vectors/mdns-v1.json` را dnspython ساخته؛
  `hw-mdns.test.js` ۵/۵ محلی (پیش از `Mdns.kt` سرخ)؛ MdnsVectorsTest، MdnsProbeTest (پاسخ‌دهندهٔ ساختگی روی loopback) و یک آزمونِ EasySetupWiringTest فقط CI.
- نیمه‌کاره: کامپایل و آزمونِ Kotlin فقط در CI (Gradle این‌جا نیست؛ kotlinc ِ جدا هم اجرا نشد). هیچ ردیفی بالا نرفت (سخت‌افزارِ واقعی، MKT-10).
  بررسی شد و ساخته نشد: HW-01/02/03/08/11/13 جز دستگاهِ واقعی کمبودی ندارند؛ HW-07 (اسکنِ گوشی ⇐ هاب ⇐ صندوق) روی مرزِ امنیتیِ هابِ SYN-09 است
  (توکن بی اینترنت سنجیده نمی‌شود) و طرح می‌خواهد؛ HW-10 (سبد روی صفحهٔ دومِ اندروید) با `Presentation` ِ حساب‌پی در `pay/platform` یکی می‌شود؛
  HW-09 ِ گوشی (ترازوی بلوتوثی در صندوق): `ScaleWeighButton` فقط در `CartLineDialog` ِ صندوقِ پیشین و `PickSheet` است و صندوقِ v3 دکمهٔ وزن ندارد؛ BLE ِ GATT ِ چاپگر بی دستگاه آزمودنی نیست.
- قدمِ بعدی: هماهنگ‌کننده شاخه را در دسته بگذارد و KTEST ِ `:core:jvmTest` (MdnsVectorsTest ۵، MdnsProbeTest ۵) و EasySetupWiringTest ۷ را در STATUS ِ HW-12 بنویسد.
### ۱۴۰۵/۰۸/۱۹ — شاخهٔ `claude/pur-2` (خانوادهٔ PUR: PUR-02/03/04/13/14؛ روی `origin/main` ‏99036497 = #1026، سپس ادغامِ #1027؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- ممیزی: هر پنج ردیف جز آزمونِ **دستگاه** کد داشتند؛ سه کاستیِ واقعی پیدا شد. (۱) سرور «بخشی/کامل» را نمی‌پالایید و برنامه فقط صفحهٔ
  نخستِ ۵۰تاییِ `state=sent` را می‌خواند ⇒ پالایه‌های گوشی ناقص و «در راه» ِ پیشنهادِ سفارش (PUR-03) بی سفارشِ بازِ کهنه. (۲) `onOrderMilli`
  ضریبِ واحدِ خرید (PRD-07) را نمی‌دید: ۲ کارتنِ ۲۴تایی = ۲ ⇒ پیشنهادِ بیش از نیاز. (۳) پنجرهٔ دریافتِ کامپیوتر «دریافتِ همهٔ باقی‌مانده» نداشت.
- انجام شد: سرور `GET /api/shop/purchase-orders?status=…` (draft/sent/partial/complete/cancelled/open) با `core.status`، صفحه‌بندیِ پیموده و
  `SCAN_MAX` ِ ۵۰۰ (`lib/purchase-orders.js` ‹`list`›)، بی مهاجرت و بی پرچمِ تازه — `purchase-orders-status.test.js` ۳/۳ (پیش از تغییر ۰/۳) روی PGlite و
  PostgreSQL 16؛ برنامه: `PurchaseOrderBook.planningOrders` (همهٔ بازها + صفحهٔ نخستِ فرستاده‌ها) در هر سه جای پیشنهاد، `onOrderMilli` به واحدِ پایه،
  `PoForms.serverStatus`/`openOnly`، و `DeskReceiveAll` در `PoReceive`؛ `PurchaseOrderPlanningTest` (۴) و کارِ تازهٔ `V3ReachabilityTest`.
- نیمه‌کاره: کامپایل و آزمون‌های Kotlin فقط در CI (Gradle این‌جا نیست). PUR-13/14 کدِ تازه نخواستند؛ مانده همان چاپگر/اسکنر و گوشی/کامپیوترِ واقعی.
  PUR-03: فصل و درجهٔ اطمینانِ INV-13 در پیشنهادِ فرم همان **تصمیمِ صاحب‌کار**. پیشنهادِ اختیاری: `match` ِ سه‌طرفه در `GET /:id` ِ سرور (امروز فقط برنامه).
- قدمِ بعدی: هماهنگ‌کننده شاخه را در دسته بگذارد و نتیجهٔ `PurchaseOrderPlanningTest` و `V3ReachabilityTest` را از CI در STATUS بنویسد.
### ۱۴۰۵/۰۸/۱۹ — شاخهٔ `claude/crm-2` (CRM-15، P0: سابقهٔ دسترسیِ وب و حسابدار، شمارهٔ پوشیدهٔ وب؛ روی `origin/main` ‏99036497 = دستهٔ ۴۲؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: (۱) سرور `POST /api/shop/customer-access` (درِ `app`، مهاجرتِ ۶۱۰ با rollback، `lib/customer-access.noteApp`): بیننده/نقش/دستگاه از توکن، `phone` ِ بی مجوز ⇐ `detail`، `*` فقط `export` (`all: true` در فهرست)، سطلِ IP و هر کاربر، FREE ِ SUB-10؛
  (۲) حسابدار روی دستگاه (`CustomerAccessNote`) به همین در؛ (۳) وب `license/customer-access.js` (بارکننده و `policy` در `shop-sync.js`، پیش‌کشِ `sw.js` v71): شمارهٔ پوشیده، کادرِ شمارهٔ پنهان، ثبتِ دیدن‌ها و «چه کسی این پرونده را دید».
  آزمون: `customer-access-app` ۸/۸ (PGlite و PostgreSQL ۱۶، پیش از کد ۰/۸)، `web-customer-access.test.js` ۵/۵، کرومیوم `web-customer-access.mjs` ۱۰/۱۰ (سه بار PGlite، یک بار PostgreSQL؛ بی کد سرخ)، و فهرست‌های در/مجوز/دکان سبز.
- نیمه‌کاره: Kotlin (`CustomerAccessTest`، `CustomerAccessLogTest`) فقط CI؛ حسابدارِ بی‌اینترنت ثبت نمی‌شود؛ CRM-15 PARTIAL می‌ماند (تصمیمِ صاحب: `phoneMasked` ۴+۳، نشانیِ سفارش/تحویل؛ SEC-10 روی دفترِ دستگاه).
- قدمِ بعدی: هماهنگ‌کننده شاخه را در دسته بگذارد؛ شاهدِ CI ِ همین آزمون‌ها در STATUS. CRM-06: قالبِ یادآوری حالا با `shop.settings_sync` (خاموش) همگام می‌شود — «مانده» ِ STATUS کهنه است.
### ۱۴۰۵/۰۸/۱۹ — شاخهٔ `claude/pos-3` (دو پیگیریِ `claude/pos-2`: بارکدِ کامل پیش از پیشوند در کامپیوتر و گوشی، و POS-04 = نامِ کالا در کادرِ صندوقِ وب؛ روی `claude/pos-2` ‏63a0bbfc؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: **«12x34»** — `PosSearch.resolve` (و `DeskSale`: بسته و شبکهٔ پیشنهاد؛ `CatalogModel.query` ِ گوشی؛ شبیه‌سازِ `PosSpeed` ِ کامپیوتر) پیشوند را پیش از
  بارکدِ کامل می‌خواند ⇒ ۱۲ تا از «34». حالا `CartQuery.parse(query, isCode)` ⇐ `PosSearch.parse`/`isFullCode` (بارکدِ کاملِ کالا یا بسته) همان `decide` ِ وب است؛ بخشِ
  `fullCode` ِ `cart-query-v1.json` (۱۵ مورد) را `CartQueryVectorsTest` و `web-cart-query.test.js` می‌خوانند؛ JS خودش درست بود (فقط بردار تازه). **POS-04 وب** —
  `license/catalog-match.js` همزادِ `CatalogMatch` با `text-core.js` و بردارِ `core/test-vectors/catalog-match-v1.json` (۵۳ جست‌وجو، ۲۶ تا از `CatalogMatchTest`؛ ۴۳۲ فاصله)
  و `CatalogMatchVectorsTest` (jvmTest)؛ پشتِ تنظیمِ خاموشِ همین مرورگر `tohid-pos-name-search-v1` («تنظیمات» ⇐ «دوربین بارکدخوان»): فهرستِ شناورِ ARIA زیرِ کادر، ↑ ↓/Enter/لمس،
  بارکدِ کامل اول، «۳*نام» با پیشوندِ روشن، افزودن با `addToCart` ِ صفحه. بارکننده `shop-sync.js`، `sw.js` v72، چهار کلیدِ تازهٔ واژه‌نامه (fa/en/ps)، `test:web` + `web-pos-name.mjs`.
  آزمون (محلی): `web-catalog-match.test.js` ۹/۹، `web-cart-query.test.js` ۹/۹، `web-shop-i18n.test.js` ۱۷/۱۷ (PGlite)؛ `web-pos-name.mjs` ۱۲/۱۲ با axe (روی `claude/pos-2` ۱۱ سرخ)، `web-pos-qty` ۹/۹،
  `web-shop-i18n.mjs` ۱۸/۱۸، `web-digits`/`web-pos-draft`/`web-beta-channel`/`web-vip-gate`/`web-product-cycle` سبز؛ بردارها جدا با برنامهٔ یک‌بارِ Java ِ JDK 21 هم‌خوان (۲٬۴۰۰ پرس‌وجوی تصادفی هم).
- نیمه‌کاره: آزمون‌های Kotlin (`CartQueryVectorsTest`، `PosLogicTest`، `CartModelTest`، `CatalogMatchVectorsTest`) فقط CI؛ STATUS: POS-03 و POS-04 همان PARTIAL (گوشیِ واقعی؛ فرهنگِ مترادف OWNER-PENDING 4-1).
- قدمِ بعدی: CI؛ سنجشِ وب روی گوشیِ واقعی (صفحه‌کلیدِ حرف و لمسِ فهرست)؛ اگر `claude/pos-2` جدا مرج شد، این شاخه روی main بازپایه شود (بخشِ «تازه‌ها» ی جدا دارد).
### ۱۴۰۵/۰۸/۱۹ — شاخهٔ `claude/stf-2` (خانوادهٔ STF: STF-04 هویتِ سروریِ کاربرِ صندوق، STF-07 «دیر ثبت‌شده»؛ روی main ‏99036497 (#1026)؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: **STF-04/S46** PIN ِ صندوق در سرور پشتِ پرچمِ خاموشِ `shop.till_pin` (مهاجرتِ ۶۴۰ ِ `shop_till_pins`/`shop_till_sessions` + rollback؛
  `lib/till-pin.js`، `routes/shop-till-pin.js`): فقط اثرِ PBKDF2، سنجش زیرِ `FOR UPDATE`، هر ۵ خطا قفلِ زمان‌دار، هر نوشتن با تأییدِ دوبارهٔ ورود، صاحب هرگز
  بیرون نمی‌ماند؛ PIN ِ درست روی دستگاهِ توکن نوبتِ صندوق باز می‌کند (`whoAt`). برنامه (فقط CI): `TillPinRepository`، `TillUser.serverEnroll/serverVerify/serverEnd`.
  **STF-07** هر زدن `recordedAt` و `late` (ساعتِ سرور، >۳۰ دقیقه) دارد؛ کارتِ «۷ روز و این ماه» نشانش را برای صاحب و مدیر دارد. `till-pin.test.js` ۹/۹ و
  `staff-time.test.js` ۹/۹ روی PGlite و PostgreSQL 16 (پیش از کار سرخ)، درهای SUB-10/route-authz/tenant/inventory/admin-flags سبز. STATUS ِ STF-04، STF-07 و S46 (PARTIAL).
- نیمه‌کاره: کامپایل و آزمونِ برنامه فقط CI؛ سندهای همگام‌سازی ستونِ کاربرِ صندوق ندارند (نسبت با دستگاه + زمان)؛ STF-06 دست نخورد (مانده‌اش امضای هر کارمند، فاز ۶ یا `routes/sync-v1.js` ِ ممنوع است).
- قدمِ بعدی: هماهنگ‌کننده شاخه را در دسته بگذارد؛ تصمیمِ صاحب‌کار: روشن کردنِ `shop.till_pin` (کاربرانِ پیشین یک بار دوباره افزوده شوند — FLAGS-ON §۸ی؛ PAY-10 هم §۸ط گرفته بود و در دستهٔ ۴۵ این بند ۸ی شد).
### ۱۴۰۵/۰۸/۱۹ — شاخهٔ `claude/web-2` (دورِ دوم: ردیف‌های PARTIAL ِ WEB و CHK؛ روی main ‏99036497 = دستهٔ ۴۲؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: (۱) CHK-20 در برابرِ §۱۹.۲ بندِ ۸: رسید دکانِ مسئول، ناحیهٔ ارسال، بازهٔ برگزیده (`scheduledFor` در پاسخ بود و دیده نمی‌شد) و «کپیِ نشانیِ پیگیری» با هشدارِ لغو را
  نداشت ⇒ `store/order-facts.js` ِ دیربار + واژه‌نامهٔ `facts` (واژه‌نامهٔ `order` در ۴KB پر است)؛ `web-store-zones.mjs` (کدِ پیشین ۶ ❌). رفعِ همراه: `K.part(…, $('view-order'))`
  همهٔ `data-t` ِ صفحه را از نو می‌نوشت ⇒ با پوشِ وب ِ روشن عنوانِ رسید «سفارش» می‌شد؛ ریشه فقط گرهٔ خودِ بخش (`web-store-push.mjs`، کدِ پیشین ۱ ❌). (۲) WEB-17: نقشهٔ سایت در ۵۰۰۰ کالا
  بی‌صدا بریده می‌شد ⇒ `sitemapindex` + `?page=<n>` (بی نشانیِ تازه؛ `STORE_SITEMAP_PAGE`)؛ `storefront-seo.test.js` ۶/۶ روی PGlite و PostgreSQL (تازه روی کدِ پیشین سرخ).
  بقیهٔ نامزدها جز کارِ بیرونی/تصمیم چیزی نداشتند: WEB-01 (صفحهٔ وبِ مدیریت، `market.slug_hold`)، WEB-05 (فرهنگِ مترادف ۴-۱)، WEB-20 (Sharing Debugger)، CHK-03 (D-09 و CI).
- نیمه‌کاره: هیچ. ردیف‌ها PARTIAL ماندند (CHK-20: D-09 و CI؛ WEB-17: Search Console). شاخهٔ کهنهٔ `claude/web-2` (ac8bfe21، squash‌شده) روی origin بود.
- قدمِ بعدی: هماهنگ‌کننده شاخه را در دسته بگذارد؛ شاهدِ CI ِ `web-store-zones.mjs` و `storefront-seo.test.js`.
### ۱۴۰۵/۰۸/۱۹ — شاخهٔ `claude/sec-review-44` (بازبینیِ امنیتیِ دومِ دستهٔ ۴۴، روی `ccr-…-batch-44` ‏6c73e73c؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: سه یافتهٔ Low در استودیوی عکس، هر سه با آزمونِ سرخ‌پیش‌از‌رفع (`server/test/security-review-44.test.js`، ۳ آزمون): SR44-1 PNG ِ دو-IHDR
  (`inspectPng` ⇒ `png-multi-ihdr`؛ `image-codec.decodePng` و `image-quality.pngLuma` IHDR ِ دوم را نمی‌گیرند)، SR44-2 سقفِ روزانه/صفِ `createBatch` زیرِ قفلِ دکان
  (فقط PostgreSQL سرخ بود: ۲۸۰۰ کار با سقفِ ۲۰۰۰)، SR44-3 کارِ هم‌digest ِ هم‌زمان ⇒ `doneOf` در `complete` زیرِ قفلِ عکس. B9 ِ ممیزی، یک خطِ RULES.
  شش شاخهٔ دیگر حمله شدند و سالم ماندند (B9). ۲۵ پروندهٔ آزمونِ عکس/مسیر روی PGlite و PostgreSQL سبز.
- نیمه‌کاره: هیچ. R10 (فرمول در CSV ِ `index.html` ِ یخ‌زده، پیش از این دسته)، R11 (سقفِ heap ِ رشته ArrayBuffer را نمی‌بندد) و R12 (کارِ «در کار» ِ دستهٔ لغوشده پس از افتادنِ فرآیند) تصمیمِ صاحب‌کارند.
- قدمِ بعدی: هماهنگ‌کننده شاخه را در دسته بگذارد.
### ۱۴۰۵/۰۸/۱۹ — شاخهٔ `claude/dari-mirror` (آینهٔ JS ِ DariCoverageTest برای جدول‌های `Str*`؛ روی main ‏99036497؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: روی `ccr-…-batch-43` ‏f067bf9c فقط CI ِ اندروید سرخ شد (`StrProductGallery.notSent`، `StrPromoHeld.releaseFailed`: «دوباره بزنید») و همهٔ JS سبز بود،
  چون آینهٔ `dari-text.test.js` فقط خرید + وب را می‌سنجید. حالا `KT.appTexts` (`server/test/kt-source.js`) همان `rows(I18nTables.all)` را از خودِ Str*.kt می‌خواند
  (۱۲۵ جدول، ~۹۳۷۰ فارسی؛ قالب ⇒ protect، `+`، چندسطری، `fa =`، `td`، `if … else`، `asIs(…)`، ثابتِ پروندهٔ دیگر) و آزمونِ تازهٔ آینه همان `of` + `farsiOnlyLeft` را می‌گذراند؛
  خوانده‌نشده ۲ (`StrOnline.coded`، `StrOnline.deskActionLabel`؛ سقفِ `APP_UNREAD_MAX`). روی main سبز، روی f067bf9c دقیقاً همان دو سرخ، با دو جملهٔ PHRASES سبز. فقط آزمون (`WHATSNEW_OK=test-only`).
- نیمه‌کاره: هیچ. یادداشت برای رفعِ دستهٔ ۴۳: جز دو سطرِ PHRASES و `dari-words.js --write`، دو ردیف در `docs/DARI-REVIEW-fa.md` هم لازم است (وگرنه آزمونِ «فهرستِ بازبینی» ِ همان پرونده سرخ).
- قدمِ بعدی: هماهنگ‌کننده شاخه را در دسته بگذارد؛ پس از ادغامِ رفعِ دستهٔ ۴۳ همین آینه سبز می‌ماند.
### ۱۴۰۵/۰۸/۱۹ — شاخهٔ `claude/pos-2` (ردیف‌های P0 ِ صندوق: POS-03 در نسخهٔ وب، شاهدِ وب برای POS-02، و راست کردنِ POS-04/10/21؛ روی main ‏990364974؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: **POS-03 وب** (سند: «در گوشی و وب اضافه شود») — `license/cart-query.js` همزادِ `CartQuery.parse` با `\s`/`.`/`$` ِ java.util.regex و بردارِ مشترکِ
  `license/test-vectors/cart-query-v1.json` (۵۳ مورد؛ جدا با JDK 21 ِ همین ماشین ۵۳/۵۳ و همهٔ BMP هم‌خوان؛ `CartQueryVectorsTest` ِ Kotlin فقط CI)؛ پشتِ
  تنظیمِ خاموشِ همین مرورگر `tohid-pos-qty-prefix-v1` («تنظیمات» ⇐ «دوربین بارکدخوان»)؛ «۳*کد» در کادرِ دستی و «۳*» ِ تنها برای اسکنِ بعدی، فقط از
  `handleBarcodeScanned` ِ صفحه؛ بارکدِ کامل بر پیشوند پیش است؛ کادر روی گوشی `tel`. بارکننده `shop-sync.js`، `sw.js` v71، چهار کلیدِ واژه‌نامه (fa/en/ps).
  آزمون: `web-cart-query.test.js` ۸/۸ (PGlite و PostgreSQL)، `web-pos-qty.mjs` ۹/۹ (روی origin/main ۹ سرخ)، بی‌پسرفتِ پنج آزمونِ مرورگرِ وب. **STATUS:** POS-02 شاهدِ وب؛
  POS-04 «#359 باز» کهنه بود و وب جست‌وجوی نام ندارد؛ POS-21 «سرور نمی‌سنجد» کهنه بود (`shop.price_cap` هست؛ price-cap ۸/۸، sale-caps ۶/۶، staff-approval ۲۱/۲۱ روی هر دو)؛
  POS-10 یافته: قلمِ آزاد (`productId` خالی) در سرور از سنجشِ قیمت بیرون است. هیچ ردیفی وضعیت عوض نکرد (همه PARTIAL). یک خطِ RULES («v3 · فروش و صندوق») و یک خطِ نقشهٔ پرچم‌ها.
- نیمه‌کاره: POS-41 (Alt+P در پنلِ پرداخت = پرونده‌های پرداخت، بیرون از قلمرو)؛ POS-02 «ردیفِ جدا» و POS-10/21 تصمیمِ صاحب‌کار؛ `PosSearch.resolve` ِ کامپیوتر پیشوند را پیش از
  بارکدِ کامل می‌خواند (یافته، دست نخورد)؛ دوربینی که پیش از بارِ `cart-query.js` روشن شد «۳*» ِ تنها را تا روشن شدنِ دوباره نمی‌بیند.
- قدمِ بعدی: CI (`CartQueryVectorsTest`)؛ سنجشِ وب روی آیفونِ واقعی (صفحه‌کلیدِ `tel`)؛ تصمیمِ صاحب‌کار دربارهٔ مجوزِ «قلمِ آزاد» در سرور و روشن کردنِ `shop.price_cap`.
### ۱۴۰۵/۰۸/۱۹ — شاخهٔ `claude/inv-2` (ردیف‌های PARTIAL ِ INV: INV-16 و INV-01، بخشِ وب؛ روی main ‏99036497 = دستهٔ ۴۲، #1026؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: **INV-16** زبانهٔ «ارزش موجودی» در «گزارشِ تازه» ِ وب (`license/stock-value-core.js` = دوقلوی `StockAnalysis.valuation`، بردارِ تازهٔ `core/test-vectors/stock-value-v1.json` ۱۷ مورد؛ بها = بازپخشِ میانگینِ موزونِ `report-core.stockCost`، ارزش = جمعِ دفتر؛ کم‌بودها زیرِ جدول؛ فقط با `money`) — `stock-value-core.test.js` ۶/۶ و `web-report-fresh.mjs` ۹/۹ (هر دو پیش از تغییر سرخ). **INV-01** زبانهٔ «موجودیِ هر مکان» در پنجرهٔ «انتقالِ موجودی» ِ وب (موجودیِ سرور در قفسه/انبار/خراب/در مسیر و جمع، محدودهٔ STF-06) — `web-transfers.mjs` ۱۱/۱۱ روی PGlite و PostgreSQL 16 و `web-transfers-core.test.js` ۸/۸. بی مسیرِ سرورِ تازه، بی مهاجرت، بی Kotlin ِ برنامه؛ `sw.js` ‏v71. هر دو ردیف PARTIAL ماندند.
- نیمه‌کاره: `StockValueVectorsTest` (core ‏jvmTest) فقط در CI. INV-09/11/13 دست نخوردند (ماندهٔ هر سه دستگاهِ واقعی است). INV-03 و INV-04@32 (رزرو) عمداً بیرون.
- قدمِ بعدی: شاهدِ CI (خطِ `KTEST PASS ir.vil3ntec.tohid.core.stock.StockValueVectorsTest`) در STATUS ِ INV-16؛ تصمیمِ صاحب‌کار برای «ارزش موجودی (به بهای خرید)» ِ `ReportEngine.products`/`DashboardEngine` (هنوز `purchasePrice` با کیت/منفی) و `shop.stock_v2`؛ مدلِ انبار جدا از محل و شعبه (INV-01).
### ۱۴۰۵/۰۸/۱۹ — شاخهٔ `claude/sec-fix-43` (پیگیریِ بازبینیِ دومِ دستهٔ ۴۳: R9 و صفحه‌های `/images/mine`؛ روی `claude/sec-review-43` ‏041e8995 + main؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: (۱) R9: نامِ قدیم و واحددارِ یک فیلد در op ِ v1/v2 (`amount`+`amountMinor`) دیگر به ترتیبِ کلیدها بسته نیست — `unitClash` در `fromV2` و همزادهای
  وب/Kotlin؛ یکی ⇒ همان مقدار، ناجور ⇒ `unit_conflict` (راهِ op ِ نادرست). بردارِ `rejects`؛ `sync-v3-unit-pair.test.js` ۴/۴ (پیش از رفع ۴ سرخ، PGlite و PostgreSQL)؛
  همهٔ `sync-v3*` روی هر دو سبز. (۲) صفحهٔ «عکس‌های بیشتر و ویترین»: `ProductGallery.collect` همهٔ صفحه‌های `/mine` را می‌خواند (فهرست و نگه داشتنِ عکسِ ویترین
  در `ImageBank.uploadOwn`)؛ بی درِ تازه. پیگیریِ B8 ِ ممیزی، دو خطِ RULES، WHATS-NEW.
- نیمه‌کاره: Kotlin (`V3ConvertVectorsTest.unitClashGoesToServer`، `ProductGalleryTest`، `ProductGalleryRepositoryTest`) فقط در CI. `ImageBank.source` (کارتِ منبعِ عکس،
  وضعِ پیشنهاد به بانک) هنوز صفحهٔ نخستِ `/mine` را می‌خواند — فقط نمایش. سندِ ناجورِ v1 در v1 می‌ماند و در v3 نیست (`sync-v1.js` ِ مشترک دست نمی‌خورد).
- قدمِ بعدی: هماهنگ‌کننده شاخه را پس از `claude/sec-review-43` در دسته بگذارد؛ R7 و R8 تصمیمِ صاحب‌کار.
### ۱۴۰۵/۰۸/۲۰ — شاخهٔ `claude/missing-rows` (دسته‌بندیِ ۳۰ ردیفِ MISSING و استودیوی عکسِ محلی؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: هر ۳۰ ردیف دسته‌بندی شد (یادداشتِ همان روز در پایانِ `docs/spec/STATUS.md`). ۱۳ ردیفِ تصویر (AI-01/02/03/05، IMG-04/05/06،
  IMG-PRO-01/02/03/04/08/14) ⇐ PARTIAL: استودیوی عکس روی سرورِ خودمان پشتِ پرچمِ خاموشِ `shop.image_studio` — رمزگشا/رمزگذارِ JPEG/PNG ِ بی
  کتابخانه (`lib/image-codec.js`)، زنجیرهٔ قطعی (`lib/image-studio.js`)، صف و کارگر در رشتهٔ جدا (`lib/image-studio-jobs.js`)، درهای
  `/api/shop/images/studio/*`، مهاجرتِ ۵۹۰. درِ انتشارِ `image-bank.selectIn` حالا نسخهٔ تأییدنشده و تصویرِ تبلیغ را نمی‌پذیرد (پیش‌تر هر نسخه).
  آزمون‌ها: `image-studio.test.js` ۱۳/۱۳، `image-studio-jobs.test.js` ۱۱/۱۱ (PGlite و PostgreSQL ِ محلی).
- نیمه‌کاره: صفحهٔ «استودیوی عکسِ محصول» در برنامه‌ها (فقط API هست)؛ مدلِ حذفِ پس‌زمینه (AI-01) نیست؛ آستانه‌ها فقط روی عکسِ مصنوعی.
- قدمِ بعدی: صفحهٔ استودیو در برنامه (core + Compose) روی همین API؛ تصمیمِ صاحب‌کار دربارهٔ مدلِ خودمیزبان (AI-01) و نگه‌داشتنِ نسخه‌های ردشده.
### ۱۴۰۵/۰۸/۱۹ — شاخهٔ `claude/rpt-2` (خانوادهٔ RPT: سود و خروجیِ «گزارشِ تازه» ِ وب، RPT-03 و RPT-11؛ روی main ‏28f6acc2 + ادغامِ main ‏99036497 = دستهٔ ۴۲؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: (۱) RPT-03 — «گزارشِ تازه» ِ وب و کارتِ سودِ خانه هنوز همیشه با قیمتِ روی قلم و «تقریبی» بودند (متنشان «نه روشِ مصوب (D-23)» می‌گفت، با این‌که D-23 گرفته شده)؛
  حالا `COST` ِ `report-board.js` (دوقلوی `CostPolicy`، بردارِ `cost-policy-v1.json`) با میانگینِ موزون + `costApproved`، گزینهٔ «روش بها» (همین مرورگر) و نشانِ «سود قطعی/تقریبی».
  (۲) RPT-11 — وب فقط CSV و چاپ داشت: «خروجی اکسل» = ‎.xlsx‎ ِ واقعی (`TohidOwnerExport.report`) و «خروجی PDF» = `license/report-pdf.js` (دوقلوی `ImagePdf`، بردارِ `image-pdf-v1.json`؛
  canvas، A4، سقفِ ۵۰۰ صفحه)، `sw.js` v71. آزمون: `cost-policy-vectors` ۴/۴ و `report-pdf-vectors` ۴/۴ (روی پایه سرخ)، کرومیومِ `web-report-fresh.mjs` ۱۰/۱۰ با axe (چهار سنجه روی پایه سرخ)،
  `owner-export`، `web-shop-i18n` (۱۳ کلیدِ تازه در سه زبان، ۵ کلیدِ کهنه برداشته)، `web-owner-export.mjs`، `web-beta-channel.mjs` سبز.
- نیمه‌کاره: `CostPolicyVectorsTest` و `ImagePdfVectorsTest` (core jvmTest) فقط در CI دیده می‌شوند (Gradle این‌جا نیست)؛ هر دو ردیف PARTIAL می‌مانند: RPT-03 با روشن کردنِ «گزارشِ تازه»/پرچمِ سود
  به دستِ صاحب‌کار، RPT-11 با دستگاه و چاپگرِ واقعی. RPT-13 (کلیدهای گزارش در وب: Ctrl+P/E و −/+) و RPT-14 (تپشِ برنامهٔ خریدِ بومی) دست نخوردند؛ RPT-04 بیرون از دامنه (`claude/pay-10`).
- قدمِ بعدی: شاهدِ CI ِ دو آزمونِ Kotlin؛ اگر شاخهٔ دیگری `sw.js` را به v71 برد، یکی‌شان v72 شود. انگلیسی/پشتوی تازه‌ها را صاحب‌کار ببیند (CostPolicy خودش در برنامه فقط فارسی است).
### ۱۴۰۵/۰۸/۲۰ — شاخهٔ `claude/pay-10` (ارزِ نقد در صندوق، PAY-10؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: دالر، کلدار و ریال در صندوقِ «ترکیبی و شیفت» پشتِ تنظیمِ خاموشِ **دکان** `FxTillPref` (`fx_tender` + جدولِ نرخِ `fx_rates`، هر دو `ShopSettingsKeys` ِ دکان؛ D-15 رعایت شد). هسته: `core/pay/FxTill.kt` (جدولِ نرخ، پرداختِ ارز، متن ⇐⇒ واحد)، `Pay.settle`/`exactCash` با `SettleInput.fx` (ردیفِ `fx`، ارز اول، پولِ پس افغانی، `FX_INVALID`/`FX_EXCESS`)، `exchange` با `per`، ارزهای PKR/IRR؛ هم‌زاد `license/fx-till.js` + `pay-core.js`؛ بردار `core/test-vectors/fx-till-v1.json` و موردهای تازهٔ `pay-v1.json`. برنامه: `FxTillFlow`، کادرِ `CheckoutFxBox`، تنظیمِ `FxTillSettings`، رسید، X/Z (`DrawerModel.fxLines`)، شیفت (`cash_out` ِ پولِ پسِ ارز) و `CashShift.saleCash`، برگشت به افغانی، کشو. سرور دست نخورد (ردیفِ پرداخت سنجیده نمی‌شود؛ `sync-v1.js` نه).
- نیمه‌کاره: آزمون‌های Kotlin (`FxTillVectorsTest`، `FxTillFlowTest`، `PayCoreTest.exchange`، `PrinterDrawerTest`) فقط در CI؛ STATUS ِ PAY-10 تا شاهدِ CI همان PARTIAL. صندوقِ کامپیوترِ قدیم (`DeskSale`) و برنامهٔ آیفون کادرِ ارز ندارند. گزارشِ خودکارِ صاحب در سرور ارز را در «نقد» می‌شمارد (`paymentMethod = cash`).
- قدمِ بعدی: پس از CI ِ سبز ردیف را IMPLEMENTED-VERIFIED کن؛ تصمیم‌های صاحب‌کار: سقفِ کهنگیِ نرخ (یک روز)، گردِ نیمه رو به بالا به یک افغانی، و این‌که «پولِ پسِ ارز» سقف بخواهد یا نه.
### ۱۴۰۵/۰۸/۱۹ — شاخهٔ `claude/owner-decisions-4` (فهرستِ یکتای تصمیم‌های مانده، دورِ چهارم؛ فقط سند؛ روی main ‏cceb5930 (#1027)؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: `docs/OWNER-PENDING-fa.md` ۷۹ ⇐ ۹۰ (۱۱ / ۲۶ / ۲۸ / ۲۵؛ شماره‌ها با اسکریپت: بی تکرار، پیوسته، هر ارجاعِ «گ-ش» موجود)، هر بند با متنِ منبع سنجیده:
  ۱-۱۰ PAY-10 (یک روز / ۱۰ دقیقه، نیمه رو به بالا، `FX_EXCESS`، پولِ پسِ بی سقف، IRR بی اعشار و واحدهای ۱ / ۱٬۰۰۰ / ۱٬۰۰۰٬۰۰۰ از `license/fx-till.js`)،
  ۱-۱۱ POS-10/21 + PRD-12 (عمده بی مجوز)، ۲-۲۳ R7/R8 (B8، در main)، ۲-۲۴ R10 (B9)، ۲-۲۵ `shop.till_pin` + STF-07، ۲-۲۶ `panel.db`، به‌روزرسان، مخزن، امضا و
  مقدارِ env ِ سایت برای viewer (ریپوی `server`: «۱.۵۰.۸۲» در main، «۱.۵۰.۸۳» در `claude/server-48` تا f09672b)، ۳-۲۶ `shop.stock_v2` / ارزشِ موجودیِ D-23 /
  انبار و محل، ۳-۲۷ PUR-03، ۴-۲۳ استودیوی عکس (+ R11/R12)، ۴-۲۴ HW-07/09، ۴-۲۵ POS-02 / PRD-12 ِ نام‌دار / PRM-18. یکی‌شده: ۲-۸ (۴+۳)، ۲-۱۰، ۲-۱۱ (کاربرِ
  غیرِ root)، ۲-۱۳، ۲-۱۸ (چهار از هفت در ۱.۵۰.۸۰؛ دانلودِ پشتیبان)، ۳-۳، ۳-۱۲، ۳-۲۲ (+ `shop.owner_report.profit`)، ۴-۱، ۴-۲، ۴-۷ (AI-01/09/12)، ۴-۱۰ (en/ps ِ
  `claude/rpt-2`)، ۴-۱۱ (مجوزِ چندپخشِ HW-12). «گرفته‌شده»: R9، نوشتنِ انتشار فقط مالک، کلیدهای پرخطرِ پنل.
- نیمه‌کاره: بی منبعِ «تصمیم» و نیامد: هشدار و «فقط کپی» ِ نشانیِ پیگیری (CHK-20 فقط D-09 ِ ۱-۱)، کلیدهای گزارشِ وب (RPT-13 دست نخورد)، جای «موجودیِ هر مکان».
  R10/R11/R12 در `claude/sec-fix-44` (push‌نشده) در دستِ رفع‌اند؛ با مرجش ۲-۲۴ و یادداشتِ ۴-۲۳ به «گرفته‌شده». تاریخ: ساعتِ سیستم ۲۰۲۶/۱۱/۱۰ = ۱۴۰۵/۰۸/۱۹.
- قدمِ بعدی: پاسخِ صاحب‌کار؛ با مرجِ هر شاخهٔ منبع «هنوز در main نیست» ِ بندها برداشته شود. ⚠️ `pay-10` (دستهٔ ۴۴) و `stf-2` هر دو بخشِ «۸ط» ِ `FLAGS-ON-fa.md` را
  دارند؛ هنگامِ ادغام یکی «۸ی» شود (OWNER-PENDING به نامِ بخش ارجاع می‌دهد، نه شماره).
### ۱۴۰۵/۰۸/۲۰ — شاخهٔ `claude/sec-review-43` (بازبینیِ امنیتیِ دومِ دستهٔ ۴۳، ادغامِ `reserve-off`، `syn-12`، `posted-docs` و `app-gaps-4` روی `ccr-…-batch-42`؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: دو یافتهٔ Low در نگهبانِ S89 (`lib/finalized-sales.js`، پرچمِ خاموشِ `shop.doc_chain`)، هر دو با آزمونِ سرخ‌پیش‌از‌رفع
  (`server/test/security-review-43.test.js`، ۳ آزمون، PGlite و PostgreSQL): SR43-1 ادغامِ «خیالی» (op ِ ردشده/تکراری/کهنه) قرض یا فاکتورِ نسیهٔ ثبت‌شده را
  جابه‌جا می‌کرد ⇒ `v1Merges` + `claimOf` + `settle` (یک خط در `lib/sync.js`)؛ SR43-2 `$inc` ِ لفظی در کلِ ردیف «همان» دیده می‌شد ⇒ `after(…, full)`.
  B8 ِ ممیزی (و B6 ِ تکراریِ `posted-docs` ⇐ B7)، یک خطِ RULES. سه شاخهٔ دیگر حمله شدند و سالم ماندند (B8).
- نیمه‌کاره: هیچ. R7 (ادغام + جدا کردن در دو دسته)، R8 (`shiftId` ِ آزاد) و R9 (`amountMinor` در پلِ v3) تصمیمِ صاحب‌کارند.
- قدمِ بعدی: هماهنگ‌کننده شاخه را در دسته بگذارد.
### ۱۴۰۵/۰۸/۲۰ — شاخهٔ `claude/owner-decisions-3` (فهرستِ یکتای تصمیم‌های مانده به‌روز شد؛ فقط سند؛ روی `ccr-…-batch-42` ‏bd6f03a8 (#1026، squash‌شده در main ‏99036497)؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: `docs/OWNER-PENDING-fa.md` ۶۷ ⇐ ۷۹ (۹ / ۲۲ / ۲۶ / ۲۲؛ ۳-۱۴ب پیش‌تر شمرده نشده بود)، هر بند با متنِ شاخهٔ منبع سنجیده: ۲-۲۱ R5 (`SECURITY-AUDIT` B6 ِ
  دستهٔ ۴۲؛ R6 در `claude/reserve-off` بسته)، ۲-۲۲ SEC-03 سه پرسش (`code-gaps`، `posted-docs` R2)، ۳-۲۰ مقدارِ درستِ PRD-08، ۳-۲۱ SYN-12 (`Retry-After`،
  ۲ شکست/۶۰ ثانیه، پخشِ ۱۵ ثانیه)، ۳-۲۲ پیش‌فرضِ `market.directory`/`market.product_details`/`report_fresh`/`shop.owner_report`، ۳-۲۳ آزمایشِ
  `shop.stock_reserve` + `shop.order_notify` و «یک پوش برای هر سفارش» (`stock-ui`)، ۳-۲۴ سقفِ ۳۰ دقیقهٔ «ساخت نسخهٔ بومی» (`tohid-native.yml`؛ main
  ۲۸:۴۹–۲۹:۳۰، لغوِ ۳۰:۱۵ در تلاشِ نخستِ #1025 — از `gh run`)، ۳-۲۵ دو رفتارِ پلِ پنل (ریپوی `server` ممیزی §۹، فقط خواندن)، ۴-۲۰ LOC-03، ۴-۲۱ PRM-02، ۴-۲۲
  گالریِ ویترین. ۲-۱۸ (S11) با بازسنجیِ ۱.۵۰.۷۹ درست شد (هفت درِ `/admin/` هنوز در پنل نیست) و پرسشِ حذفِ پشتیبان گرفت؛ ۴-۱۹ فقط «روزی یک بار یا هر ۶ ساعت»
  ماند (ویرایشگرِ تعطیلات در پنلِ ۱.۵۰.۷۸ مرج شده). ۴-۱۸ «برگشت‌پذیر» ِ جاافتاده گرفت؛ «هنوز مرج نشده» ِ
  دسته‌های ۴۱ و ۴۲ در ۱-۹، ۲-۱۶، ۲-۲۱، ۳-۱۴ب و ۳-۱۶ به «مرج‌شده» درست شد.
- نیمه‌کاره: هیچ. شاخه‌های `posted-docs`، `app-gaps-4`، `syn-12`، `syn-12b` و `reserve-off` هنوز در دسته نیستند؛ با مرجشان «هنوز در دسته نیست» ِ بندها را بردارید.
  ⚠️ `posted-docs` بندِ «B6» ِ `SECURITY-AUDIT-fa.md` را دارد و دستهٔ ۴۲ هم B6 ِ دیگری (بازبینیِ ۴۱–۴۲)؛ هنگامِ ادغام یکی B7 شود.
- قدمِ بعدی: پاسخِ صاحب‌کار؛ هر بندِ گرفته‌شده به «گرفته‌شده» ِ `OWNER-DECISIONS-fa.md`.
### ۱۴۰۵/۰۸/۱۹ — شاخهٔ `claude/rsk-platform` (تمیزکاری: بی سقفِ پلتفرمیِ تازه برای `OrderRiskListsV3.kt`؛ روی `ccr-…-batch-42` ‏1879be76؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: `090a2373` (از `claude/rsk-app`) برای یک `LocalContext` در صفحهٔ v3 سقفِ تازهٔ ۱ در `platformDeps` ِ `tools/quality/baseline.json` گذاشته بود.
  پوستهٔ مسیرِ `RiskListsRoute` (مخزن از `Context`) به `OrderRiskListsAndroid.kt` رفت (مرزِ `*Android.kt` در `PLATFORM_DEPS.boundary`)؛ بدنه `RiskListsContent(repo)`
  در `OrderRiskListsV3.kt` بی‌پلتفرم ماند و سطرِ سقف برداشته شد. بقیهٔ سقف‌های همان کامیت فقط پایین آمده بودند و دست نخوردند. quality، eslint، spec «سالم»، آینه‌های Kotlin و dari ۲۱/۲۱.
- نیمه‌کاره: کامپایلِ اندروید/کامپیوتر و `V3ReachabilityTest` (`RiskListsRoute` ⇐ `RiskListsContent` ⇐ `RiskEntriesList`) فقط در CI.
- قدمِ بعدی: هماهنگ‌کننده شاخه را در دسته بگذارد. `finish-pr.sh` با `[ -n "$BUMP" ]` می‌سنجد ⇒ `BUMP=0` هم سقف می‌افزاید؛ برای «بی سقفِ تازه» `BUMP` را خالی بگذارید.
### ۱۴۰۵/۰۸/۲۰ — شاخهٔ `claude/reserve-off` (R6 ِ بازبینیِ ۴۲: پرچمِ خاموشِ نگه‌داشتن، روی `ccr-…-batch-42` ‏1879be76؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: `shop.stock_reserve` خاموش ⇒ نگه‌داشتهٔ باز هیچ‌جا اثر ندارد (ویترین، کاتالوگ، ثبت، افزودن، قبول، جزئیات/کارتِ سفارش، وظیفه و خبر)؛ ردیف‌ها
  می‌مانند و فقط بسته می‌شوند ⇒ روشنِ دوباره: نگذشته می‌شمارد، گذشته نه. `lib/stock-reserve.js` (`heldOf` پرچم را می‌سنجد، `sumHeld` ِ بی پرچم برای
  `planIn`/`reholdIn`)؛ آزمون‌ها سرخ‌پیش‌از‌رفع: R6 ×۳ در `security-review-42.test.js` (PGlite و PostgreSQL) و `web-store-stock.mjs`؛ یک بندِ
  `stock-reserve-review-notify.test.js` که همان رفتارِ نادرست را می‌خواست (`stockHold.review` با پرچمِ خاموش) عوض شد. B6 ِ ممیزی، FLAGS-ON §۴ب، یک خطِ RULES.
- نیمه‌کاره: هیچ. کمبودی که در دورانِ خاموش رخ داد پس از روشنِ دوباره نشان دارد ولی پوشِ «به بررسی رفت» و وظیفه‌اش تا حرکتِ بعدی نمی‌آیند.
- قدمِ بعدی: هماهنگ‌کننده شاخه را در دسته بگذارد.
### ۱۴۰۵/۰۸/۲۰ — شاخهٔ `claude/sec-review-42` (بازبینیِ امنیتیِ دومِ دسته‌های ۴۱–۴۲، روی `ccr-…-batch-42` ‏a8704c12؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: دو یافتهٔ Low در نگه‌داشتنِ موجودی (O-06، پرچم‌های خاموش)، هر دو با آزمونِ سرخ‌پیش‌از‌رفع (`server/test/security-review-42.test.js`، ۴ آزمون):
  SR42-1 قلمِ بزرگ‌شده (افزودنِ مشتری) جای صفِ پیشین را نگه می‌داشت ⇒ سفارشِ دیگری به بررسی می‌رفت ⇒ بزرگ شدن ته صف؛ SR42-2 بن‌بستِ گذارِ کارمند و
  فروشِ حضوری روی PostgreSQL (`40P01`) ⇒ `FOR KEY SHARE NOWAIT` در خبرِ بررسی. شرح، «حمله شد و سالم ماند» و R5–R7 در `SECURITY-AUDIT-fa.md` B6؛ یک خطِ RULES.
- نیمه‌کاره: هیچ. R5 (سقفِ احتکار برای هر شماره) و R6 (خاموش کردنِ پرچم نگه‌داشته‌ها را آزاد نمی‌کند) برای `OWNER-PENDING-fa.md` (هماهنگ‌کننده).
- قدمِ بعدی: هماهنگ‌کننده شاخه را در دسته بگذارد؛ پیش از روشن کردنِ `shop.stock_reserve` تصمیمِ R5.
### ۱۴۰۵/۰۸/۲۰ — شاخهٔ `claude/ci-evidence-2` (ردیف‌هایی که فقط شاهدِ CI کم داشتند، با خطِ KTEST؛ روی `ccr-…-batch-41` ‏86102aed؛ فقط سند؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: از ۲۷۸ ردیفِ PARTIAL/NOT-VERIFIED، ۱۸۸ از CI نام می‌برند؛ پنج ردیف جز شاهدِ CI کمبودی نداشتند و هر آزمونِ نام‌برده‌شان در لاگِ سرِ #1025 ‏75b13cee PASS بود ⇒
  IMPLEMENTED-VERIFIED: POS-01، WEB-11، WEB-13، CHK-14، MKT-02 (KTEST ِ «کامپایل و آزمون» 129620741220، «ساخت نسخهٔ بومی» 129620745030، «برنامهٔ خریدِ مشتری (گزارش)» 129620744584؛
  PostgreSQL 129620741156 و مرورگر 129620741305). همه: ۴۳۵ IV، ۲۶۱ PARTIAL. یک خطِ RULES («v3 · CI و آزمون»): کلاسِ بی متد ⇒ PASS = شمارِ `@Test`.
- نیمه‌کاره: NTF-08، CHAT-07، CHAT-14 (برنامهٔ خرید)؛ CHAT-02، CHAT-06، POS-12، SYN-14 (دستگاه)؛ CHK-03، CHK-20، ORD-16، WEB-10 (D-09)؛ POS-10، PRD-12، STF-03، WEB-01 (صاحب‌کار)؛ CRM-03، S70، SEC-03، AI-06 (کد).
- قدمِ بعدی: همین روش برای ردیفی که کمبودِ دیگرش بسته شد؛ لاگ با `gh api repos/vil3ntec-it/shop/actions/jobs/<id>/logs`.
### ۱۴۰۵/۰۸/۲۰ — شاخهٔ `claude/syn-12b` (SYN-12، دنبالهٔ `claude/syn-12` ‏54e2b31c: هجومِ پس از وصلِ دوباره، نوارِ کامپیوتر، رشتهٔ شکست با ورود/دکانِ دیگر؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: قاعدهٔ یگانهٔ `core/…/core/sync/SyncReconnect.kt` ⇔ `reconnectDelayMs`/`reconnectHold` ِ `license/sync-core.js` (بخش‌های تازهٔ
  `sync-outage-v1.json`): دورِ نخستِ پس از `online` ِ وب (v1 و v3) یا پس از دیدنِ بی‌نتی در برنامه (`sync/v1/ReconnectGate` در `SyncV1Worker`)
  درنگِ یکنواختِ ۰ تا ۱۵ ثانیه؛ تیک/Pull ِ دوره‌ای صبر، «همگام‌سازی حالا»، تغییرِ وب و تلاشِ پس از شکست همان دم؛ فاصلهٔ تلاش، صف و بدنه دست‌نخورده.
  رشتهٔ شکست با `onSignedIn` ِ وب و `SyncV1Engine.rebind` (⇒ `SyncV3Runner.forgetOutage`) پاک؛ نوارِ وضعیتِ کامپیوتر `SyncModel.quietHead`.
  آزمون: `sync-outage.test.js` ۱۸/۱۸ (۱۰ جهش ⇒ سرخ)، `web-sync-ux.mjs` ۸ ✅، `web-sync-v3.mjs` ۷ ✅، ۱۵ پروندهٔ موتورِ همگام‌سازی ۱۶۲ سبز.
- نیمه‌کاره: Kotlin فقط در CI (`SyncReconnectTest`، `SyncOutageVectorsTest.reconnect*`، `ReconnectGateTest`، `SyncV3EngineTest.forgetOutage…`،
  `SettingsModelsTest` ِ نوارِ کامپیوتر)؛ STATUS همان PARTIAL. در برنامه دورِ پس از تغییر در پنجرهٔ درنگ صبر می‌کند (همان کارِ WorkManager).
  `web-idb.mjs` «سهمیهٔ IndexedDB» روی پایهٔ syn-12 هم سرخ است (به این کار ربطی ندارد).
- قدمِ بعدی: شاهدِ CI ِ آزمون‌های Kotlin ِ syn-12 و syn-12b؛ اگر صاحب‌کار خواست `Retry-After` (با سقف) و نشانِ تغییرِ دورِ برنامه (`change`).
### ۱۴۰۵/۰۸/۲۰ — شاخهٔ `claude/syn-12` (SYN-12 ِ سمتِ دستگاه: قطعیِ شبکهٔ دستگاه، سرور، قطعیِ سراسری یا نشست؛ روی main ‏b6419a69 = دستهٔ ۴۰؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: تصمیمِ یگانه در `core/…/core/sync/SyncOutage.kt` و همزادِ `license/sync-core.js` با بردارِ `core/test-vectors/sync-outage-v1.json`
  (بی‌نت ⇒ آفلاین؛ شکستِ شبکه/سرورِ کوتاه ⇒ «دوباره می‌کوشد» بی هشدار؛ ≥ ۲ شکست و ۶۰ ثانیه ⇒ «به سرور نمی‌رسیم» یا «سرور» با اعلانِ آرام؛
  نشست و خطای واقعی همان سرخ). موتورهای v1/v3 ِ وب و برنامه رشتهٔ شکست را در حافظه نگه می‌دارند و حکم و چراغ را در حال می‌گذارند؛ نوارِ
  `#sync-outage` و پنجرهٔ همگام‌سازیِ وب، کارتِ آرامِ خانه و تشخیصِ صفحهٔ همگام‌سازیِ نسخهٔ ۳. صف، بدنهٔ push، فاصلهٔ تلاش و `Retry-After` دست نخوردند.
  آزمون: `sync-outage.test.js` ۱۰/۱۰، `web-sync-ux.mjs` ۸ ✅ (axe روز و شب؛ گامِ «پهن» حالا زرد به‌جای سرخ برای نخستین شکستِ شبکه — عمدی).
- نیمه‌کاره: Kotlin فقط در CI (`SyncOutageTest`، `SyncOutageVectorsTest`، `SyncV3EngineTest`، `SyncV1CoreTest`، `PhoneHomeModelTest`، `SettingsModelsTest`)؛
  STATUS همان PARTIAL. «کنترلِ هجوم پس از وصلِ دوباره» در دستگاه نیست (رویدادِ `online` ِ وب همان دم یک دور می‌زند)؛ `Retry-After` نادیده (تصمیمِ صاحب‌کار).
- قدمِ بعدی: شاهدِ CI ِ آزمون‌های Kotlin؛ اگر صاحب‌کار خواست، پراکندگیِ دورِ نخست پس از `online`/بازگشتِ شبکه و پذیرفتنِ `Retry-After` (با سقف).
### ۱۴۰۵/۰۸/۲۰ — شاخهٔ `claude/posted-docs` (SEC-03/S89: سندهای مالیِ دیگر و «زنده کردن»؛ روی `claude/code-gaps` ‏99220d4b؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: (۱) **S89، سندهای دیگر:** برگشت، قرض/رسید، خرید و حرکتِ انبار با همان پرچمِ `shop.doc_chain` و همان `enforce`/`log` ِ فاکتور (`lib/finalized-sales.js` `DOC_FIXED`/`checkDoc`،
  `doc_posted_edit_forbidden`؛ آزاد: فیلدِ غیرمالی، دلتای `paidAmount`/`debt` ِ خرید، ادغامِ مشتری). (۲) **زنده کردن:** ردیفِ حذف‌شدهٔ با محتوا (`deadOf`) دیگر «ثبتِ تازه» نیست ⇒ v1،
  راهِ قدیم، API و بازگرداندنِ تعارضِ مدیر روی فاکتور/سندِ حذف‌شده `*revive*` (زنجیره‌دار رد). `sync-v1.js` ِ مشترک دست نخورد؛ `lib/sync.js` فقط کدِ رد را می‌رساند.
  آزمون: `posted-docs.test.js` ۸/۸ روی PGlite و PostgreSQL (پیش از رفع ۷ سرخ). شرح در RULES «v3 · سابقهٔ عملیات و زنجیرهٔ هش» و SECURITY-AUDIT بندِ B7.
- نیمه‌کاره/باز: `restoreDeleted` ِ `lib/sync-v1.js` (مشترک با پمپ) بی نگهبان ولی بی درِ HTTP؛ برنامهٔ زنجیره‌دار قرض و حرکت را سرِ جا پاک می‌کند (`DebtorEngine.deleteTransaction`،
  حذفِ مشتری/کالا/ورودی) ⇒ با پرچمِ روشن «rejected» — باید سندِ معکوس بسازد؛ شاهدِ CI نیست.
- تصمیمِ صاحب‌کار: حذفِ v1 ِ برگشت/قرض/خرید از دستگاهِ زنجیره‌دار رد بماند (SEC-03) یا مثلِ v3 «لغو» شمرده شود؛ پیش‌فرضِ `shop.doc_chain` همان تصمیمِ پیشین.
- قدمِ بعدی: شاهدِ CI برای `posted-docs`؛ در برنامه به‌جای «حذفِ قرض» رسیدِ قرینه (سندِ معکوس) وقتی زنجیره روشن است.
### ۱۴۰۵/۰۸/۲۰ — شاخهٔ `claude/code-gaps` (شکاف‌های کدیِ S70، SEC-03 و S48؛ روی main ‏b6419a69 = دستهٔ ۴۰؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: (۱) **SEC-03، بازبینیِ خصمانه:** نگهبانِ S89 حالتِ `enforce` را فقط از `device_id` ِ بدنه می‌گرفت ⇒ نشستِ دستگاهِ زنجیره‌دار با شناسهٔ تازه (v1) یا از `/api/sync`
  مبلغِ فاکتورِ ثبت‌شده را سرِ جا عوض می‌کرد. حالا «زنجیره‌دار» = شناسهٔ ادعاشده یا دستگاهِ نشست (`lib/finalized-sales.js` `chainDevice`/`boundOf`؛ `lib/sync.js` و
  `routes/sync.js` `boundDevice`)؛ راهِ قدیم با نشست/شناسهٔ زنجیره‌دار ۴۰۹. `sync-v1.js` ِ مشترک دست نخورد. آزمون: `finalized-sales-bound.test.js` ۵/۵ (PGlite و PostgreSQL، پیش از رفع ۳ سرخ).
  (۲) **S70:** `market-units.sizeOfItem` (اندازهٔ واحدِ بی عدد از `saleUnit` و `packs`، فقط نامِ روشن و نسبتِ دقیق) در `sameProduct`؛ رابطِ وب «همین کالا در دکان‌های دیگر» روی برگهٔ
  کالای ویترین (`store/same.js` ِ دیربار + واژه‌نامهٔ `same`، فقط با `bankProductId`)؛ آزمون‌ها: `market-units` ۶/۶، `market-same-units` ۲/۲ (هر دو پایگاه)، `web-store-same.mjs` ۲۹ ✅ با axe.
  (۳) **S48:** شکافِ کدی نماند؛ PENDING ِ کهنهٔ `scenario-s48` درست شد و `S48.keys` حالا Enter ِ `reports.drill` را هم می‌سنجد.
- نیمه‌کاره/باز: سندهای برگشت/قرض/خرید/حرکت نگهبانِ «ثبت‌شده» ندارند؛ ویرایشِ فاکتورِ حذف‌شده آن را با محتوای تازه زنده می‌کند (حذف فقط از راهِ `log`)؛ برنامهٔ خریدِ مشتری
  «همین کالا» را ندارد؛ نشستِ بی زنجیره همچنان `log` (F0-02).
- تصمیمِ صاحب‌کار: SEC-03 — رد (نه فقط رویداد) برای دستگاهِ بی زنجیره و `/api/sync` وقتی `shop.doc_chain` روشن است (برنامهٔ کهنه ویرایشش رد می‌شود)، و پیش‌فرضِ خودِ `shop.doc_chain`؛
  S48 — پیش‌فرضِ «گزارشِ تازه» (`report_fresh`) و `shop.owner_report`؛ S70 — `market.directory` و `market.product_details`.
- قدمِ بعدی: شاهدِ CI ِ دسته (نام‌به‌نام) برای `finalized-sales-bound`، `market-units`، `market-same-units`، `web-store-same` و `DayEndTest`؛ نگهبانِ سندهای برگشت/قرض/خرید (S89).
### ۱۴۰۵/۰۸/۲۰ — شاخهٔ `claude/app-gaps-4` (سه کمبودِ برنامه با سرورِ موجود: PRM-02، PRD-17، PRD-27؛ روی main ‏b6419a69 = دستهٔ ۴۰؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: (۱) PRM-02: صاحب/مدیر نمی‌دانست کدام فاکتور کد را نگه داشته ⇒ درِ تازهٔ `GET /api/shop/promos/redemptions` (فقط `mayCancel`، پرچمِ `shop.promo_uses`،
  سقفِ IP پیش از احراز، ۲۰۰ ردیف + `more`، بی شناسهٔ کاربر/دستگاه) و صفحهٔ «تنظیمات ← فروش ← کدهای تخفیفِ نگه‌داشته» (`core/promo/PromoHeld`) که فقط فاکتورِ
  لغوشدهٔ دفترِ همان دستگاه را با همان `POST /redeem/release` آزاد می‌کند. (۲) PRD-17/27: صفحهٔ «عکس‌های بیشتر و ویترین» (`products/{id}/photo/gallery`،
  `core/image/ProductGallery`): تکمیلی با نقش روی همان `POST /api/shop/images` بی `select`؛ عکسِ جدای ویترین با نقشِ `promo` + `select`؛ `ImageBank.uploadOwn` آن را با
  عکسِ تازهٔ دستگاه کنار نمی‌زند؛ «برداشتن از کالا» با درِ تازهٔ `POST /api/shop/images/:id/detach` (اصل می‌ماند). آزمون: `promo-held` ۵/۵ و `image-bank-extras` ۳/۳
  روی PGlite و PostgreSQL ِ محلی (پیش از دو در سرخ)؛ `promo-redemptions`، `shop-promos`، `image-bank`، `shop-route-inventory`، `tenant-matrix`، `route-authz-inventory`، `paid-routes-entitlement` سبز.
- نیمه‌کاره: Kotlin (`PromoHeldTest`، `ProductGalleryTest`، `PromoHeldStateTest`، `ProductGalleryStateTest`، دو `Feature` ِ `V3ReachabilityTest`) فقط در CI.
  گالریِ چندعکسی در خودِ ویترین سمتِ سرور ندارد (تصمیمِ صاحب‌کار)؛ فاکتورِ «نارسیده» (سبدِ ثبت‌نشده) در فهرست دکمه ندارد (تصمیمِ صاحب‌کار).
- قدمِ بعدی: شاهدِ CI ِ چهار آزمونِ Kotlin و سپس STATUS ِ سه ردیف.
### ۱۴۰۵/۰۸/۱۹ — شاخهٔ `claude/crm-03-app` (CRM-03، سمتِ برنامه؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: برنامه پیش از ذخیره «تغییرِ سقفِ اعتبار» (`customers.credit_limit`) را می‌پرسد: هستهٔ `core/perm/CreditLimitPerm` (همان قاعدهٔ `credit-limit-perm.js`)، `data/CreditLimitGate` با پیام‌های `StrSaleCaps`، برگهٔ «ویرایش قرض‌دار» ِ نسخهٔ ۳ (کادرِ بسته با دلیل یا «تا X»، خطای درجا، سدِ آخر در `DebtorFormsModel.save`) و پروندهٔ کامپیوتر (`DeskCreditLimitDialog`، جدا از `DeskCustomers.kt` تا سقفِ اندازه بماند)؛ قاعده از `rememberCreditLimitRules` (مرزِ سکو). پرچمِ خاموش/صاحب ⇒ همان امروز. نامِ اجازه در صفحهٔ نقش‌ها.
- نیمه‌کاره: `CreditLimitPermTest` و `CreditLimitGateTest` فقط در CI (Gradle این‌جا نیست)؛ ادغامِ مشتری و صفحه‌های قدیمِ گوشی نمی‌پرسند. «رد یا بررسی» (OWNER-PENDING ۱-۸) دست نخورد؛ این کار به آن وابسته نیست.
- قدمِ بعدی: پس از CI ِ سبز، شاهد در STATUS ِ CRM-03.
### ۱۴۰۵/۰۸/۱۹ — شاخهٔ `claude/shopper-chat` (برنامهٔ خرید: CHAT-07، CHAT-14 و کارتِ بازپرداختِ NTF-08؛ روی main ِ 28f6acc2؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: گفت‌وگوی برنامهٔ خرید همتای ویترین (`store/chat-acts.js`، `store/chat-voice.js` و دستهٔ ۴۲ ِ `store/chat.js`): «کارها» ⇐ پاسخ (`replyTo` از همان صف)، ویرایش (فقط پیامِ متنیِ زندهٔ خودم تا `editUntil`، بی صف)، سنجاق (نوار از `conversation.pinned`) و جست‌وجو؛ صدا پشتِ `limits.voice` با `VoiceDevice` (اندروید: `VoiceAndroid.kt`، اجازهٔ `RECORD_AUDIO` فقط با زدنِ میکروفون؛ آیفون null) و پخشِ حباب بی بایت پیش از «پخش»؛ کارتِ «بازپرداخت» در صفحهٔ سفارش. ⛔ پیوندِ رشته: هر نوشته فقط به رشتهٔ خودش (`ChatCompose`، `ChatOpening`)؛ رفعِ واقعیِ برنامه: گشودنِ پشتِ‌سرِ‌همِ دو سفارشِ یک دکان پاسخِ دیرِ اولی را می‌نشاند، متنِ کادر و پاسخِ «مشکل دارم» به رشتهٔ دیگر می‌رفتند. سرور: `POST /api/store/:slug/chat/conversations/:id/messages/:mid/edit` (همان PATCH؛ HttpURLConnection PATCH نمی‌فرستد) با آزمونِ سرخ‌پیش‌از‌در در `shop-chat-web-acts.test.js` (۷/۷ روی PGlite و PostgreSQL ِ محلی).
- نیمه‌کاره: آزمون‌های Kotlin فقط در CI (`ShopperChatActsTest`، `ShopperVoiceTest`، `CustomerChatActsTest`، `CustomerChatVoiceTest` و بخشِ CHAT-14 ِ `ShopperStage2LiveTest`)؛ STATUS بی ارتقا تا شاهدِ CI. ضبط/پخشِ آیفون (`VoiceDevice` ِ iOS) نیست.
- قدمِ بعدی: پس از CI ِ دسته، خطِ `KTEST PASS` ِ همین چهار کلاس را در ردیف‌های CHAT-07/14 و NTF-08 بگذار؛ سپس `VoiceDevice` ِ آیفون (AVAudioRecorder/AVAudioPlayer در `*Ios.kt`).
### ۱۴۰۵/۰۸/۲۰ — دستهٔ ۴۱ (`ccr-3a0d593c-ac0jpj-batch-41`، #1025): کارهای PGlite و Electron دو تکه شدند
- انجام شد: «آزمون‌های سرور زیرِ Electron» در ۴۵:۱۵ لغو شد (سقفِ کار؛ همهٔ آزمون‌ها تا آن لحظه سبز؛ همان روی main ِ f8134445؛ دستهٔ ۴۰: ۳۹ دقیقه، main ِ b6419a69: ۴۲). PGlite هم از ۲۸ به ۳۵+ دقیقه رسیده بود. ریشه: همهٔ ۴۷۸ پرونده پشتِ‌سرِ‌هم در یک کار. رفع: `TEST_SHARD=k/n` در `scripts/test-pglite.js` (`scripts/test-shard.js`) و ماتریسِ دو تکه برای هر دو کار در `server.yml`؛ «آزمون‌های سرور» منتظرِ همهٔ تکه‌هاست. `test-shard.test.js` ۴/۴ (پیش از سیم‌کشی ۳/۴: آزمونِ سیم‌کشی سرخ).
- قدمِ بعدی: اگر تکه‌ای باز به ~۴۰ دقیقه رسید ⇒ n=3؛ سقف بالا نمی‌رود.
### ۱۴۰۵/۰۸/۲۰ — شاخهٔ `claude/sec-review-40` (بازبینیِ امنیتیِ دومِ دستهٔ ۴۰، PR #1024؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: سه یافتهٔ Low، هر سه با آزمونِ سرخ‌پیش‌از‌رفع روی PGlite و PostgreSQL ِ محلی (`server/test/security-review-40.test.js`، ۵ آزمون):
  SR40-1 واحدِ نامتنی (عدد/آرایه/`$inc`) قفلِ واحدِ PRD-07 را در یک op دور می‌زد (v1 و v3) ⇒ `oddUnit`؛ SR40-2 NUL در پرسشِ `sync/v3` ⇒ ۵۰۰
  (استثنای `RAW_SYNC` بیش از اندازه، `V3` ِ بزرگ هم) ⇒ v3 بیرون از استثنا؛ SR40-3 تپشِ قیف از سایتِ دیگر (sendBeacon) ⇒ `Sec-Fetch-Site: cross-site` شمرده نمی‌شود.
  شرح و «حمله شد و سالم ماند» در `docs/SECURITY-AUDIT-fa.md` B5؛ سه خطِ RULES.
- نیمه‌کاره: هیچ. باز برای صاحب‌کار (B5): R2 (PRD-07 در دو op با «واحدِ خالی»، همان قاعدهٔ برنامه)، R3 (چرخاندنِ User-Agent از یک IP در قیف؛ ترازو با CGNAT)، R4 Info ِ مشترکِ پمپ.
- قدمِ بعدی: هماهنگ‌کننده شاخه را در دسته بگذارد؛ اگر صاحب‌کار R2 را بخواهد، «واحدِ خالی روی کالای گردش‌دار = قفل» در `ProductDraftRules` و سرور با هم.
### ۱۴۰۵/۰۸/۲۰ — شاخهٔ `claude/acct-console-2` (درهای خواستهٔ پنلِ مرکزی ۱.۵۰.۷۵ برای کارکنان، بانکِ عکس و پشتیبان؛ روی `ccr-…-batch-40` ‏76aa1657؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: (۱) `POST /api/admin/shops/:id/staff-perms/devices/:did/enable` (ارشد + دلیل، همان `deviceOfShop` و سابقه، سقفِ IP پیش از احراز)؛
  (۲) `Idempotency-Key` روی disable/enable ِ دستگاه، بازبینیِ بانکِ عکس (approve/reject/archive/search) و `POST /admin/backups` با
  `lib/admin-write-once.js` (نشانِ پایگاه `admin_write_claims`، مهاجرتِ ۵۰۰؛ هم‌زمان ⇒ ۴۰۹ `idempotency_in_progress`)؛ (۳) توانایی‌های
  `staff-perms.v1`، `image-bank.v1`، `idempotent-writes.v1`؛ (۴) `?limit=&offset=` روی `GET …/staff-perms` (بی پارامتر همان پاسخ)؛ (۵) یافتهٔ
  امنیتی: دکانِ «الف» دستگاهِ انداختهٔ دکانِ «ب» (کاربرِ هر دو) را برمی‌گرداند یا با «انداختنِ دوباره» نشانش را می‌دزدید ⇒ ۴۰۹
  `disabled_by_other_shop`؛ دو برگرداندنِ هم‌زمان دو سابقه می‌ساخت ⇒ نوشتنِ شرطی. آزمون: `admin-console-writes` ۱۶ (PGlite و PostgreSQL ۱۶،
  پیش از تغییر همه سرخ)، شرح در بندِ ۱۲.۱۱ ِ ADMIN-CONTROL-PLANE و RULES «v3 · پنلِ مدیر».
- نیمه‌کاره: سمتِ پنل (ریپوی server) — پشتِ `idempotent-writes.v1` کلیدِ پنجره را به بالادست بفرستد (مثلِ `sync-restore-once.v1`)، دکمهٔ
  «برگرداندن» پشتِ `staff-perms.v1`، و ۴۰۹ ِ `idempotency_in_progress`/`disabled_by_other_shop` را بشناسد.
- قدمِ بعدی: شاهدِ CI ِ آزمونِ تازه؛ تصمیمِ صاحب‌کار دربارهٔ اجباری کردنِ دلیل روی disable ِ مدیر (امروز اختیاری، پل همیشه می‌فرستد).
### ۱۴۰۵/۰۸/۱۹ — شاخهٔ `claude/stock-flake` (ریشهٔ سرخیِ گاه‌به‌گاهِ `web-store-stock.mjs:154`؛ روی `claude/stock-ui` ‏1c1c3f6c؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: ریشه = پنجرهٔ پرسشِ ۵۰۰ms ِ `catalog.refresh` (`CATALOG_REFRESH_THROTTLE_MS`) — push ِ روغنِ O-06 درونِ پنجرهٔ پرسشِ `look()` ِ پیشین
  می‌افتاد، خواندنِ ویترین بی پرسش همان دفترِ پیش از push را می‌داد و صفحه دوباره نمی‌پرسد ⇒ دکمه تا ۱۰ ثانیه بسته. کهنگیِ تا ۵۰۰ms طراحیِ سرور است (ساختِ سفارش
  بی مهلت می‌خواند) ⇒ رفعِ آزمون: پس از push خودِ رسیدن در دفترِ سرور با `GET /api/shop/catalog/products/oil` (قفسه ۱۰۰۰، فروختنی ۱۰۰۰، `in_stock`) و سپس ویترین.
  شاهد: شیمِ ثبتِ پرسش‌ها ⇒ اجرای سرخ بی هیچ `hasNewer` پس از push و `oil = out_of_stock`؛ اجرای عادیِ کدِ پیشین ۲ از ۴ سرخ؛ بازتولیدِ قطعی (پاسخِ
  `PUT /api/shop/storefront` ۶۰۰ms دیر، یا `CATALOG_REFRESH_THROTTLE_MS=5000`): کدِ پیشین PGlite ۵/۵ و ۳/۳، PostgreSQL ۲/۲ سرخ؛ کدِ تازه ۵/۵، ۵/۵ و ۲/۲ سبز؛
  اجرای عادی ۳/۳ PGlite و ۳/۳ PostgreSQL، هر بار ۳۰ ✅. قاعده در `RULES-fa.md` «v3 · CI و آزمون».
- نیمه‌کاره: هیچ.
- قدمِ بعدی: ادغام در دستهٔ بعد؛ آزمونِ مرورگرِ تازه‌ای که پس از push ویترین را می‌خواند همین مانع را بگذارد.
### ۱۴۰۵/۰۸/۱۹ — شاخهٔ `claude/stock-ui` (سمتِ برنامهٔ O-06 = ب و پوشِ «سفارش به بررسی رفت»؛ روی `claude/p0-stock` ‏4cd1002c؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: (۱) برنامهٔ مدیریت (v3): `OrderDto.stockHold`/`stockReview` (پیش‌فرض‌دار؛ نبود ⇒ هیچ)، `ui/v3/online/orders/OrderStockHold` (نشان و پیام)، نشانِ
  «موجودیِ رزروشده کم آمد — بررسی کنید» نخستین برچسبِ `OrderTags` (کارت و سرِ جزئیات) و پیامِ حالِ نگه‌داشتن زیرِ سرِ جزئیات؛ متن‌ها در `StrOrdersHub`
  (fa/en/ps، دری از لایه). (۲) سرور: `stock-reserve.recheckShort` ⇒ `order-notify.enqueueReviewIn` در همان تراکنش زیرِ SAVEPOINT، پشتِ `shop.stock_reserve` +
  `shop.order_notify`، هر سفارش یک بار (یکتاییِ صف)، رویدادِ یک‌باره، متن فقط شماره؛ بررسیِ بسته پیش از فرستادن ⇒ بی پوش؛ مهاجرتِ ۵۲۰ (CHECK) + rollback.
  آزمون: `stock-reserve-review-notify.test.js` ۶/۶ (پیش از کد ۵ سرخ) روی PGlite و PostgreSQL ۱۶؛ `stock-reserve` ۱۷، `order-notify` ۱۳، `push-fcm` ۶ روی هر دو سبز.
- نیمه‌کاره: `OrderStockHoldTest` (۶ آزمون) و ساختِ Kotlin فقط در CI — هنوز دیده نشده. فهرستِ «در انتظار» ِ اعلان در برنامه (`/pending`) همان سفارشِ تازه می‌ماند.
- قدمِ بعدی: شاهدِ CI (اندروید/کامپیوتر/آیفون برای `OrderStockHoldTest`)؛ روشن کردنِ `shop.stock_reserve` + `shop.order_notify` برای دکانِ آزمایشی و دیدنِ پوش روی
  گوشیِ واقعی؛ ریسکِ مانده: یک فروشِ بزرگِ حضوری می‌تواند هم‌زمان چند سفارش را به بررسی ببرد ⇒ چند پوش (هر سفارش یکی، کارگر ۲۰ در هر دور).
### ۱۴۰۵/۰۸/۲۰ — شاخهٔ `claude/p0-stock` (O-06 = ب: نگه‌داشتنِ موجودیِ سفارشِ آنلاین، ORD-20 و CHK-18؛ روی main ‏f81344458 ِ دستهٔ ۳۹؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: `lib/stock-reserve.js` + مهاجرتِ ۴۳۵ پشتِ پرچمِ تازهٔ خاموشِ `shop.stock_reserve` (در کاتالوگِ پرچم‌ها، RULES «نقشهٔ پرچم‌ها»،
  `docs/FLAGS-ON-fa.md` §۴ب): ساختِ سفارش با موجودیِ تازه ⇒ نگه‌داشتن زیرِ قفلِ `catalog:<دکان>` (دو سفارشِ هم‌زمانِ آخرین عدد ⇒ دقیقاً یکی)،
  مهلت `reserveTtlSec` (تنبل + جاروی دقیقه‌ای در `workers.js`)، قبول ⇒ بی‌مهلت یا دوباره یا ۴۰۹، تحویل ⇒ تا رسیدنِ فروشِ `o-<سفارش>-<قلم>`
  (مهلتِ پشتیبانِ ۲۴ساعته) که در **همان تراکنشِ** کسرِ دستگاه «انجام‌شده» می‌شود (سرور هرگز کم نمی‌کند)، پایان ⇒ آزاد، INV-19 ⇒ بررسی؛
  ویرایش/افزودن/بسته‌بندی ⇒ هم‌اندازه (`syncOrderIn`)؛ رفعِ ۴۰۹ ِ نادرستِ افزودنِ قلم وقتی نگه‌داشتهٔ خودِ سفارش شمرده می‌شد. ویترین:
  کالای نگه‌داشته «ناموجود» و پیامِ «برای سفارشِ دیگری نگه داشته شده». آزمون: `stock-reserve.test.js` ۱۷ (PGlite و PostgreSQL؛ آزمونِ مسابقه
  بی قفل روی PostgreSQL سرخ)، `web-store-stock.mjs`. STATUS: ORD-20 ⇒ PARTIAL، CHK-18 «مانع» ⇒ —؛ AI-04 (۳) همان `select` با `version` است.
- نیمه‌کاره: نشانِ «در بررسی» و `stockHold` در برنامهٔ دکان (Kotlin) ساخته نشد؛ اعلانِ «سفارش به بررسی رفت» (#429 داشت) نه. شاهدِ CI.
- قدمِ بعدی: شاهدِ CI؛ روشن کردنِ `shop.stock_reserve` برای یک دکانِ آزمایشی؛ ریسکِ مانده: یک شمارهٔ تأییدشده تا سقفِ RSK-07 (۱۰ سفارش در ساعت)
  و `maxQtyMilli` می‌تواند موجودی را ۳۰ دقیقه نگه دارد — مهلت را کوتاه‌تر کنید یا سقفِ نگه‌داشتنِ هر شماره را بسازید.
### ۱۴۰۵/۰۸/۱۹ — شاخهٔ `claude/chat-thread` (برگهٔ گفت‌وگو تا پاسخِ POST ِ رشتهٔ تازه رشتهٔ پیشین و کادرش را داشت؛ روی `claude/chat-flake-2`؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: یافتهٔ شاخهٔ پیشین باگِ محصول بود: «گفت‌وگو با دکان» ِ سفارشِ دیگر تا پاسخِ POST ِ `/chat/conversations` عنوان، پیام‌ها و
  کادرِ رشتهٔ پیشین را داشت و `send()` با `conv` ِ پیشین در صف می‌گذاشت ⇒ پیامِ o4 در رشتهٔ o3. رفع در `store/chat.js`: `hold(body)` (از
  `openWith` و `start`) برگه را خالی می‌کند (`conv` ِ تهی، `loop++`، فهرست‌ها، سنجاق، عکس و صدای پیشین، کادر پنهان، عنوان از درخواست،
  «در حالِ باز کردن…» + `aria-busy`)؛ `opening` پاسخِ دیرِ گشودنِ پیشین را می‌اندازد؛ `prev`/`reload`/`issue`/خطای حذف و ویرایش (`chat-acts.js`) فقط برای همان رشته؛
  `draftOf` متنِ نیمه‌نوشته را به رشتهٔ دیگر نمی‌برد. `chat-out.js` دست نخورد (هر پیام `convId` ِ همان دمِ نوشتن را دارد). سرور پیامِ
  رشتهٔ دیگرِ همان مشتری را بی سنجشِ سفارش می‌پذیرد (`rowOf` فقط دکان + شماره) — درست است، مرز برگه است.
  بازتولید: `--require` ِ `repro-thread.cjs` (فقط `page.route`؛ POST ِ گشودنِ o4 نگه داشته، مشتری می‌نویسد و می‌فرستد، پایگاه می‌گوید
  کدام رشته): کدِ پیشین ۷/۷ سرخ (پیام در رشتهٔ #۳؛ ۵ PGlite + ۲ PostgreSQL)، تازه ۷/۷ سبز (کادر در نگه‌داشت نیست، پیام در رشتهٔ #۴).
  آزمون: گامِ تازهٔ `web-store-chat.mjs` (گشودنِ o3 و o4 نگه‌داشته، پاسخِ دیرِ o3 پس از o4) روی محصولِ پیشین ۴/۴ سرخ (۶ ❌)؛ کلِ پرونده ۱۸۹ ✅
  (PGlite ۲ بار، PostgreSQL ۲ بار)؛ `web-store.mjs` ۱۳۲ و `web-store-allflags.mjs` ۳۳۵ سبز؛ `web-chat-out` ۱۱/۱۱.
- نیمه‌کاره: هیچ.
- قدمِ بعدی: اگر CI سرخی دیگری از همین پرونده نشان داد، با همان شیم (`page.route`) بازتولید شود.
### ۱۴۰۵/۰۸/۱۹ — شاخهٔ `claude/chat-flake-2` (ریشهٔ دو سرخیِ `web-store-chat.mjs`: تمرکز پس از «حذف برای من» ِ خطِ ۵۰۰ و عکسِ خطِ ۹۴۴؛ روی `claude/chat-old`؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: هر دو باگِ محصول بودند، نه بخت. (۱) دکمهٔ خاموشِ «بله» تمرکز را به body می‌دهد و فقط پاسخِ DELETE آن را برمی‌گرداند؛ زیرِ بار
  long-poll ِ همان «پنهان» زودتر می‌رسید (لاگِ سرور: هر دو long-poll پیش از DELETE تمام شدند) و حباب با تمرکزِ body می‌رفت ⇒ `put` ِ
  `store/chat.js` «کارِ busy ِ همین پیام + body» را تمرکزِ درونِ حباب می‌شمارد. (۲) پیگیریِ سفارشِ دیگر در همان سند (`#/o/…` ِ دیگر) تا پاسخ
  سفارشِ پیشین را با کارهایش نشان می‌داد؛ آزمون (فقط `hidden` ِ خودِ کارت) کارتِ «گفت‌وگو» ِ o3 را زد و عکس به رشتهٔ o3 رفت ⇒ `wait`/`want`
  در `store/order.js` (پیشین پنهان + «در حالِ باز کردن…»، پاسخِ دیرِ سفارشِ دیگر نمی‌نشیند) و `orderTok` در `store.js`؛ آزمون با
  `checkVisibility()` و عنوانِ همین شماره. سه گامِ تازهٔ قطعی در آزمون (پاسخِ DELETE نگه‌داشته، GET ِ o4 نگه‌داشته، تمرکزِ رشتهٔ بسته).
  بازتولید: `--require` ِ `repro-net.cjs` (فقط `page.route`): کدِ پیشین هر کدام ۷/۷ سرخ با همان نشانه (۵ PGlite + ۲ PostgreSQL؛ عکس: POST ِ
  گفت‌وگو با `orderNo:3`)، تازه ۷/۷ سبز (`orderNo:4`، تمرکز روی `#ct-text`)؛ دو گامِ نگه‌داشتهٔ تازهٔ آزمون روی محصولِ پیشین ۵/۵ سرخ. کلِ پرونده: ۱۸۳ ✅
  (PGlite ۲ بار، PostgreSQL ۲ بار)؛ ۱۲ آزمونِ مرورگرِ دیگرِ پیگیری (web-store*، web-market) سبز.
- نیمه‌کاره: هیچ. بررسی‌نشده: `fail()` ِ `order.js` (سفارشِ دیگرِ ناموفق) فقط بخشی از کارت‌ها را پنهان می‌کند؛ کارت‌های دیربارِ سفارشِ پیشین (امتیاز، جایگزین، روش…) شاید بمانند (پیش از این شاخه هم).
- یافتهٔ تازه (رفع‌نشده، بیرون از این دو گام): برگهٔ گفت‌وگو با «گفت‌وگو با دکان» ِ سفارشِ دیگر همان دم باز می‌شود و تا پاسخِ
  POST ِ `/chat/conversations` عنوان، پیام‌ها و کادرِ نوشتنِ رشتهٔ پیشین را دارد و `send()` هنوز به `conv` ِ پیشین می‌فرستد (روی PostgreSQL ِ
  محلی ۴/۴ عنوانِ «#۳» درست پس از پیدا شدنِ `#ct-form`)؛ آزمون اکنون عنوانِ همین رشته را منتظر می‌ماند.
- قدمِ بعدی: همان برگه در `openWith` ِ `store/chat.js` تا `show()` ِ رشتهٔ تازه بدنه و فرمِ پیشین را پنهان کند (آزمون: POST ِ همان رشته
  با `page.route` نگه داشته شود). اگر CI سرخی دیگری از همین پرونده نشان داد، با همان شیم بازتولید شود.
### ۱۴۰۵/۰۸/۱۹ — شاخهٔ `claude/chat-old` (ریشهٔ سرخیِ `web-store-chat.mjs:691` در CI ِ #1024؛ روی `origin/main`؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: ریشه در آزمون بود، نه محصول. گامِ «شمارهٔ دیگر» (۰۷۹۱۱۱۲۲۵۵) در همان دکان گفت‌وگوی عمومیِ دومی می‌سازد و آزمون رشتهٔ عمومی را
  با «دکان + `order_id=''`» و `one()` ِ بی `ORDER BY` می‌گرفت. بی آمار برنامه‌ریز Index Scan ِ `idx_shop_chat_conv_inbox` (`updated_at DESC` ⇒
  رشتهٔ تازه، سبز)؛ پس از autoanalyze ِ PostgreSQL ِ CI Seq Scan به ترتیبِ heap ⇒ رشتهٔ شمارهٔ دیگر ۸ روز کهنه شد، رشتهٔ «آرد دارید؟» باز ماند،
  `restart` چیزی نکرد و `#ct-old` نیامد (همان لاگِ CI). اکنون با کلیدِ یکتا (دکان، شماره، '') + سنجشِ همان پیام (۱۷۶ ✅).
  بازتولید: شیمِ `--require` که پیش از SELECT ِ آزمون `ANALYZE` می‌زند (یا از چند ردیف آخری را می‌دهد): کدِ پیشین ۱۰/۱۰ سرخ در `691:12`، تازه ۱۰/۱۰ سبز.
- نیمه‌کاره: هیچ. زیرِ بارِ محلیِ سه مرورگرِ هم‌زمان یک بار دو گامِ دیگر (تمرکزِ پس از «حذف برای من» ِ خطِ ۵۰۰، عکسِ خطِ ۹۴۴) سرخ شد؛ تنها سبز — بررسی نشد.
- قدمِ بعدی: اگر CI همان دو گام را سرخ نشان داد، با `Animation`/CPU-throttle ِ همان صفحه بازتولید شود.
### ۱۴۰۵/۰۸/۲۰ — شاخهٔ `claude/owner-decisions-2` (فهرستِ یکتای تصمیم‌های مانده به‌روز شد؛ فقط سند؛ روی `ccr-3a0d593c-ac0jpj-batch-40` = main ‏b6419a69c (#1024)؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: `docs/OWNER-PENDING-fa.md` ۵۴ ⇐ ۶۶ تصمیم (۹ / ۲۰ / ۱۹ / ۱۸)، هر کدام با متنِ همان شاخه و دستهٔ ۴۱ سنجیده: ۱-۹ پیامِ بازپرداخت با مبلغ و بی پیامک
  (`claude/ntf-refund`)، ۲-۱۶ و ۲-۱۷ R2/R3 ِ `SECURITY-AUDIT-fa.md` B5 (`claude/sec-review-40`)، ۲-۱۸ S11، ۲-۱۹ ویرایشِ مجوزِ کارکنان، ۲-۲۰ دلیلِ اجباریِ disable ِ مدیر (`acct-console-2`)، ۳-۱۷ بارگذاریِ
  بانکِ عکس، ۳-۱۸ سقفِ ~۱۰۰ ثانیهٔ Cloudflare و مهلتِ رسانهٔ پشتیبانی در درِ مدیر (ریپوی `server`، ممیزی §۹ و RULES «۱.۵۰.۷۵»/«۱.۵۰.۷۶»)،
  ۳-۱۶ تنظیمِ تولید پس از آزمونِ بار و برداشتنِ `SYNC_V3_GATE_POOL_DEADLINE` (`PERF-LOAD-fa.md` «(سوم)» §۴، `claude/perf-2`)، ۳-۱۹ مسیرهای
  `server.yml`، ۴-۱۷ کدِ پستیِ CRM-09، ۴-۱۸ سقفِ ۲۰ دکانِ «دیده‌شده‌ها» (`claude/shopper-gaps`). ۳-۲ با آزمونِ سوم هم‌راستا شد (مهلتِ کلِ
  درخواست خاموش، سقفِ صف به ۳-۱۶)؛ «اسکناس» ِ برنامهٔ خرید در ۴-۱۰ (همان ۱۵ واژه)؛ دو یادداشتِ کهنه (#1023 مرج شد، `product_unit_rules`
  با #1024 مرج شد). «مانع» ِ DLV-06 در STATUS: O-07 گرفته‌شده (ب).
- نیمه‌کاره: هیچ. شاخه‌های منبع (`perf-2`، `ntf-refund`، `sec-review-40`، `shopper-gaps`، `acct-console-2`) در دستهٔ ۴۱‌اند و هنوز مرج نشده؛
  اگر یکی کنار رفت، بندش را هم بردار. «مانع» ِ CHK-18/ORD-20 عمداً دست نخورد (عاملِ دیگر).
- قدمِ بعدی: با هر جوابِ صاحب‌کار بندِ همان تصمیم به «گرفته‌شده» ِ `OWNER-DECISIONS-fa.md`.
### ۱۴۰۵/۰۸/۱۹ — شاخهٔ `claude/tender-stale` (اسکناسِ مانده پس از تغییرِ جمع، CHK-24؛ روی `claude/web-chk24` ‏b3f1b377؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: دو «نیمه‌کاره» ِ بندِ `claude/web-chk24`. سرور — یک قاعده (`delivery-prefs.dropStaleTenderIn`): هر نویسندهٔ جمع/روشِ پرداخت/روشِ تحویل (ویرایشِ
  ORD-14 در `propose` و `decide`، افزودنِ قلم، `pack` ِ جمع‌آوری با جایگزین/«پیدا نشد»، روشِ تحویل، پرداختِ آنلاین) پس از نوشتن و زیرِ همان قفل
  اسکناسی را که از `checkTender` نمی‌گذرد برمی‌دارد، با رویدادِ `delivery_edit` ِ `system` و یادداشتِ فارسی در خطِ زمانیِ کارمند؛ `edit` بی کلیدِ اسکناس هم
  همین را برای ردیفِ کهنه می‌کند ⇒ ویرایشِ وب، برنامهٔ خرید و کارتِ کارمند دیگر `cash_tender_*` نمی‌گیرد؛ مبلغِ فرستاده همان ۴۰۰. بی مهاجرت، بی در و بی پرچمِ تازه.
  `payment-intents.js` در سقفِ ۸۵۵ ماند (یک UPDATE دو سطری شد). برنامهٔ خرید — `PrefsRules.body`: نقد نیست و اسکناس مانده ⇒ `cashTenderMinor: null` ِ صریح، مثلِ وب.
  آزمون‌ها: `order-tender-stale.test.js` ۸/۸ روی PGlite و PostgreSQL ِ محلی (پیش از رفع ۰/۸ روی هر دو؛ هم‌زمانی پنج بار روی PostgreSQL، هر دو ترتیب دیده شد)،
  و `order-delivery-prefs`، `order-amend`، `order-amend-paid`، `order-add-items`، `order-delivery-method`، `order-picking`، `payment-intents`، `pick-substitutes`،
  `order-substitution`، `cash-tender-vectors`، `hesabpay-guard` سبز روی هر دو.
- نیمه‌کاره: `ShopperPrefsTest.nonCashOrderWithAStoredBanknoteSendsAnExplicitNull` (و یک سطرِ عوض‌شدهٔ `bodyCarriesTheSeenVersion…` که همان باگ را قفل کرده بود) فقط CI.
  کارتِ کارمند (`OrderDeliveryPrefsUi`) دست نخورد: اسکناسِ نشان‌داده اکنون همیشه درست یا خالی است.
- قدمِ بعدی: پس از CI ِ سبز شاهد در ردیفِ CHK-24.
### ۱۴۰۵/۰۸/۱۹ — شاخهٔ `claude/web-chk24` (CHK-24 در وبِ پیگیری: «با کدام اسکناس…» و رفعِ کارتِ ردشده/لغوشده؛ روی `claude/shopper-gaps-2` ‏921c168f؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: باگ — کارتِ «دستورِ تحویل» ِ `store/order.js` برای سفارشِ ردشده/لغوشده «از دکان بیرون رفته» نشان می‌داد؛ حالا مثلِ `PrefsRules.shown`
  پنهان است (`K.orderKind` = err). همزادی — «با کدام اسکناس…» در همان کارت فقط برای «نقد هنگامِ تحویل»: یک تابعِ `K.tenders` در `kit/order-kit.js`
  برای فرمِ ثبت و پیگیری (نسخهٔ checkout.js برداشته شد)، اسکناسِ کنونیِ درست می‌ماند، null ِ صریح، `expectedVersion`، یک درخواست در هر لحظه، ۴xx ⇒
  پیگیری از نو و «ثبت شد» اگر همان نشست؛ کادرها با `توکن:نسخه`. جا در `order.*.json` بی بالا بردنِ سقف: `od.prefs.locked` (همان متنِ
  `err.delivery_locked`) برداشته، نُه کلید کوتاه (`od.pf.*`، `od.pay.go/wait/fail`، `od.sub.ok/rm`)، متنِ `err.version_conflict` کوتاه و عام —
  fa ۴۰۲۶⇐۴۰۸۳، ps ۴۰۴۲⇐۴۰۸۸، en ۲۹۱۵⇐۲۹۶۷ از ۴۰۹۶. آزمون‌ها: `web-store-zones.mjs` ۹۰ ✅ روی PGlite و PostgreSQL (پیش از رفع ۱۶ ❌، `cash-tender-vectors` ۳ از ۴ سرخ)،
  `cash-tender-vectors` ۴/۴، `web-i18n` + `dari-text` ۴۰/۴۰، web-store/allflags/dari/paystep سبز. `server/src/app.js` ِ main ۸۰۱ خط بود (سقفِ
  ۸۰۰، کیفیتِ main سرخ): یک توضیح کنارِ همان سطر رفت.
- نیمه‌کاره: سرور — اسکناسی که جمعِ امروز دیگر نمی‌پذیرد (جایگزین/برداشتنِ قلم پس از ثبت) هر ویرایشِ دیگرِ دستورِ تحویل را، از کارمند هم، رد می‌کند
  (`cash_tender_*`) تا کسی صریحاً پاکش کند؛ وب حالا null می‌فرستد، برنامهٔ خرید هم (`PrefsRules.formOf`)، ولی کارتِ کارمند نه. برنامهٔ خرید برای
  سفارشی که پس از ثبت با حساب‌پی پرداخت شده کلیدِ اسکناس را نمی‌فرستد ⇒ اسکناسِ مانده `bad_cash_tender` می‌گیرد (وب null می‌فرستد).
- قدمِ بعدی: پس از CI ِ سبز شاهد در ردیفِ CHK-24؛ تصمیم دربارهٔ دو موردِ «نیمه‌کاره» (درست‌کردن در سرور یا در کارتِ کارمند و `PrefsRules.body`).
### ۱۴۰۵/۰۸/۲۰ — شاخهٔ `claude/shopper-gaps-2` (CHK-24 در برنامهٔ خرید: «دستورِ تحویل» و اسکناس پس از ثبت؛ روی `claude/shopper-gaps` ‏cf438f31؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: کارتِ «دستورِ تحویل» در پیگیریِ برنامهٔ خرید، همزادِ `#o-prefs` ِ وب (`core/.../shopper/ShopperPrefs.kt` ِ `PrefsRules`،
  `ShopperApi.editDelivery`، `OrderExtras.deliveryPrefs`؛ `customer/ui/ModelPrefs.kt` ِ `PrefsModel`، `OrderCards.DeliveryPrefsCard`):
  «زنگ»، «تحویل به»، نشانهٔ تازه و «با کدام اسکناس…» (فقط نقد) با همان در و `expectedVersion`؛ یک درخواست در هر لحظه، بی تکرارِ خودکار،
  ۴۰۹ + نمای تازهٔ همان ⇒ «ثبت شد». سرور: `customerPrefs` کلیدِ `cashTenderMinor` گرفت (بی آن برنامه اسکناسِ کنونی را نمی‌دانست و
  ذخیرهٔ کادرِ دیگر پاکش می‌کرد)؛ `order-delivery-prefs.test.js` (فهرستِ کلیدها عمداً + ویرایشِ اسکناس با توکن، کدهای خطا، تکرار) ۵/۵
  روی PGlite و PostgreSQL، پیش از رفع سرخ. بی مهاجرت، بی درِ تازه، بی پرچمِ تازه.
- نیمه‌کاره: آزمون‌های کاتلین (`ShopperPrefsTest`، `ShopperTenderVectorsTest.afterPlacingOptionsAreTheSameVectorAndEachIsServerValid`،
  `CustomerDeliveryPrefsTest`) فقط CI. ⚠️ وبِ پیگیری اسکناس را پس از ثبت نمی‌پرسد: `order.fa.json` ۴۰۲۶ از ۴۰۹۶ بایت و واژه‌نامهٔ اصلی
  ۲۱۱۵۸ از ۲۱۵۰۴ — جا نیست مگر صاحب‌کار سقف یا جای واژه‌ها را بگوید.
- قدمِ بعدی: پس از CI ِ سبز شاهد در ردیفِ CHK-24 ِ STATUS؛ تصمیمِ صاحب‌کار دربارهٔ اسکناس در وبِ پیگیری.
### ۱۴۰۵/۰۸/۲۰ — شاخهٔ `claude/shopper-gaps` (کاستی‌های برنامهٔ خرید در برابرِ ویترین: WEB-13 و CHK-14؛ روی `origin/main` ‏f81344458؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: (۱) WEB-13 — ردیفِ «دیده‌شده‌های اخیر» در صفحهٔ دکانِ برنامهٔ خرید با همان کلیدِ `recent` ِ `GET /api/store/:slug` و همان قاعدهٔ
  `RecentlyViewed` (core/shopper `RecentProducts`، `ShopperState.seen`؛ هر دکان ۱۲ کالا، ۳۰ روز، حداکثر ۲۰ دکان)، فقط روی دستگاه، `?ids=` با هر ورود
  (نیامده ⇒ بیرون)، «پاک کردن»؛ `ui/ModelRecent.kt` + `ShopScreen.RecentRow`. (۲) CHK-14 — «با کدام اسکناس…» در برگهٔ ثبت فقط با
  `features.deliveryPrefs` و ارسال (`CheckoutRules.tenderOptions`/`tenders`/`tenderOf`، `CheckoutDelivery.cashTenderMinor`؛ `CheckoutScreen.Tender`)؛
  بردارِ مشترکِ `core/test-vectors/cash-tender-v1.json` با `server/test/cash-tender-vectors.test.js` (تابعِ `tenders` ِ `checkout.js` در vm و `checkTender` ِ سرور).
  هیچ درِ سرور، مهاجرت یا پرچمِ تازه‌ای نیست؛ پرچمِ خاموش ⇒ همان بدنه و همان صفحه.
- نیمه‌کاره: آزمون‌های کاتلین (`ShopperRecentTest`، `ShopperCheckoutTest.tender…`، `ShopperApiTest.recentAndDeliveryPrefs…`، `ShopperTenderVectorsTest`،
  `CustomerRecentTenderTest`) نوشته شد ولی محلی اجرا نشد (بی Gradle) — شاهدِ CI. ویرایشِ اسکناس پس از ثبت (CHK-24) در برنامهٔ خرید هنوز نیست.
- قدمِ بعدی: پس از CI ِ سبز، ردیف‌های WEB-13 و CHK-14 ِ STATUS با شاهدِ CI؛ سپس CHK-24 (ویرایشِ دستورِ تحویل) در برگهٔ پیگیریِ برنامهٔ خرید.
### ۱۴۰۵/۰۸/۲۰ — شاخهٔ `claude/safety-kt-2` (شکافِ ماندهٔ POS-12 از شاخهٔ کهنهٔ `claude/safety-followups-kt`؛ روی main ‏b6419a69 = دستهٔ ۴۰؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: سنجشِ فایل‌به‌فایلِ شاخهٔ کهنه (دو کامیت روی دستهٔ ۳۳، ۳۰ فایل) با main: هر ۲۴ فایلِ کد و آزمون یا عیناً در main است یا main
  از آن جلوتر است (`HeldInvoices.kt` فروشنده، `BackupRestoreDrillTest` همان، `desktop/build.gradle.kts` گزارشِ کاملِ شکست) و هر سطرِ افزودهٔ
  سندهایش هم در main هست — همه با دستهٔ ۳۴ (#1018) آمد ⇒ **هیچ کدی آورده نشد** (دوباره‌کاری می‌شد). شاخهٔ کهنه جانشین دارد (همین main) و دست نخورد.
  شاهدِ CI دیده‌شده: main ‏f8134445 بومی run 42742083103 (`testReleaseUnitTest` تازه، ۳۱/۳۱ کار) و کامپیوتر run 42742083227 (`./gradlew test`)
  سبز ⇒ `HeldClaimTest`/`HeldSyncTest`/`RecoveryPhraseSecureWiringTest`/`RecoveryPhraseFlowTest`/`BackupRestoreDrillTest`/`DataSafetyGuardTest`
  در STATUS (POS-12، S09، OPS-02) و ردیفِ پرچمِ `shop.held_sync` ِ RULES («باز» ⇐ «main»). محلی: `shop-held-invoices` ۱۱/۱۱ (PGlite و PostgreSQL)،
  `dari-text` ۱۵، `kotlin-file-mirrors` ۶، `core-vectors-common-mirror` ۶، `keymap` ۷.
- نیمه‌کاره: POS-12 همچنان PARTIAL — آزمون با دو دستگاهِ واقعی و آزمونِ تصویریِ برگه‌های «در دستِ دستگاه» و «دوتایی» نیست.
- قدمِ بعدی: آزمونِ دستیِ دو گوشی با `shop.held_sync` روشن روی دکانِ آزمایشی؛ سپس تصمیمِ ارتقای POS-12.
### ۱۴۰۵/۰۸/۲۰ — شاخهٔ `claude/p1-native` (دو ردیفِ P1 ِ فقط‌برنامه: LOC-06 پوستهٔ کامپیوتر و RPT-13 ستونِ مقایسهٔ برگهٔ فهرستی؛ روی main ‏b6419a69 = دستهٔ ۴۰؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: (۱) RPT-13: «مقایسه» در «گزارش‌ها» ِ کامپیوتر برای فاکتور/خرید/مصرف — گروه با روشِ پرداخت/تأمین‌کننده/دسته و جمعِ همان ستون (فاکتورِ
  لغوشده بیرون، جمع به صدم با عددِ صحیح، گروهِ فقط-دورهٔ-قبل در انتها) در `core/report/ReportCompare.group`/`grouped` (commonMain) و
  `ReportCsv.compareGroup`/`comparedList`، صفحهٔ `ReportsDesk` پشتِ همان `ReportKeysPref`؛ `ReportCompare.format` دیگر «1.2E7» نمی‌نویسد.
  آزمون: ۷ آزمونِ تازهٔ `ReportCompareTest` و یکی در `ReportEngineTest`. (۲) LOC-06: ۲۰ متنِ خامِ پوستهٔ کامپیوتر (سینی، عنوانِ پنجره‌ها، پنجرهٔ
  فایل، ۱۲ ردیفِ میان‌برهای قدیم) از جدول — جملهٔ عیناً موجود یا `StrDesktop` با رونوشتِ ترجمهٔ همان جمله، بی پشتوی تازه؛ نگهبانِ
  `DesktopTextTest` (سقفِ ۵۹). فهرستِ بی‌ترجمه‌ها برای صاحب‌کار در `docs/i18n-glossary.md` («پوستهٔ کامپیوتر»).
- نیمه‌کاره: هیچ آزمونِ کاتلینی این‌جا اجرا نشد (بی Gradle) ⇒ شاهدِ CI ِ دسته؛ STATUS ارتقا نیافت.
- قدمِ بعدی: ترجمهٔ صاحب‌کار برای فهرستِ glossary؛ تصمیمِ `en`/`ps` در `keymap.json` برای پنجرهٔ F1؛ پس از CI ِ سبز، RPT-13 فقط با شاهد ارتقا.
### ۱۴۰۵/۰۸/۲۰ — شاخهٔ `claude/loc-04` (ردیفِ P1 ِ LOC-04: تعطیلاتِ رسمی از سرور؛ روی `claude/loc-03` ‏a0f77ca9؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: سرور از پیش در داشت (`routes/calendar.js`، `routes/admin-calendar.js`، `lib/holidays.js`)؛ کمبودها بسته شد: دلیلِ اجباری (۴۰۰ `reason_required`)،
  سابقه با دلیل و افزوده/برداشته **در همان تراکنش**، جدولِ یکسان ⇒ `unchanged` (تکرارِ پاسخِ گم‌شده ۴۰۹ ِ دروغ نمی‌گیرد)، قفلِ تراکنشیِ کلید
  (نخستین نوشتنِ هم‌زمان روی PostgreSQL هر دو «نسخهٔ ۱» می‌شد)، سقفِ IP ِ `calendar-ip` و `admin-calendar-ip` (در `routes/admin.js` پیش از
  `requireAdmin`)، `from>to` ⇒ ۴۰۰، `If-None-Match` ِ چندتایی، ردیفِ خرابِ پایگاه کنار (نه ۵۰۰)، «توانایی» ِ `holidays.v1` و دو ردیفِ بندِ ۶ ِ
  ADMIN-CONTROL-PLANE. برنامه‌ها: `core/calendar/Holidays` (commonMain؛ همان `parse` ِ سرور با بردارِ `core/test-vectors/calendar-holidays-v1.json`)،
  `ShopDayChoice.holidays` در `rules` (نسخه در `tohid-holidays`، DATA-SAFETY §۶)، `keepHolidaysFresh` در `AppShell` (اندروید و آیفون) و `Main.kt` ِ
  کامپیوتر — روزی یک بار، بی‌توکن و بی شناسهٔ دستگاه؛ بی جدول همان رفتارِ پیشین. `holidays.test.js` ۱۴/۱۴ روی PGlite و PostgreSQL ِ محلی.
- نیمه‌کاره: آزمون‌های کاتلین فقط در CI (`HolidaysTest`، `HolidayVectorsTest`، `ShopWeekWiringTest`، `HolidaysPrefDesktopTest`، `DataSafetyGuardTest`)؛
  صفحهٔ ویرایشِ جدول ساخته نشد: `server/public/admin` فقط نگه‌داری است و پنلِ مرکزی در ریپوی server (این سیزن خاموش) ⇒ `OWNER-PENDING-fa.md` ۴-۱۷.
- قدمِ بعدی: شاهدِ CI ِ آزمون‌های بالا؛ پاسخِ صاحب‌کار به ۴-۱۷ (پیشنهاد: ویرایشگر در پنلِ مرکزی پشتِ `holidays.v1`)؛ گذاشتنِ نخستین جدول.
### ۱۴۰۵/۰۸/۲۰ — شاخهٔ `claude/loc-03` (ردیفِ P0 ِ LOC-03: هفتهٔ دکان؛ روی main ‏b6419a69 = دستهٔ ۴۰؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: منطقِ هفته از پیش در `BusinessDay`/`WeekRules` بود ولی هیچ‌جا تنظیم نمی‌شد و خانه شنبه را ثابت داشت. تازه: `core/calendar/ShopWeek`
  (commonMain: آغازِ شنبه/یکشنبه/دوشنبه، متنِ روزهای تعطیل، `toggle` با نگه‌داشتنِ آخرین روزِ کاری، `order`/`indexOf`)؛ `ShopDayChoice.weekStart`/
  `closedDays`/`rules` با کلیدهای «مالِ دکان» ِ `week_start`/`closed_days` (ShopSettingsKeys و DATA-SAFETY §۶)؛ تنظیم در «تاریخ و رقم» ِ نسخهٔ ۳؛
  آمار، نمودارِ «هفته» ِ خانه، نقشهٔ «ساعت و روز» و روزهای تخفیف همان قاعده را می‌گیرند؛ «دیروز» ِ کادرِ تاریخِ نسخهٔ ۳ (`V3Inputs`) روزِ کاری شد.
  بردارهای `calendar.json` (weekStart +۴ با آینهٔ CoreVectorsCommonTest، dayKind +۶)؛ JS ِ locale-vectors و common-mirror ۱۳/۱۳ محلی.
- نیمه‌کاره: آزمون‌های کاتلین فقط در CI (ShopWeekTest، StatsModelTest، HomeHeroTest، HeatmapModelTest، PromoTextTest، ShopWeekWiringTest،
  ShopWeekPrefDesktopTest)؛ LOC-04 در برنامه (گرفتنِ `/api/calendar/holidays` و پرکردنِ `WeekRules.holidays`) و خواندنِ تنظیمِ رسیده از دستگاهِ دیگر بی بازکردنِ
  دوباره (`ShopSettingsSync` پرونده را می‌نویسد، `ShopDayChoice` با `load` ِ بعدی می‌خواند — همان رفتارِ «پایانِ روز»)؛ خلاصهٔ پیکِ سرور هنوز شنبهٔ ثابت.
- قدمِ بعدی: شاهدِ CI ِ آزمون‌های بالا ⇐ LOC-03 به IMPLEMENTED-VERIFIED فقط اگر صاحب‌کار «تعطیلاتِ رسمی» را جدا (LOC-04) بپذیرد.
### ۱۴۰۵/۰۸/۲۰ — شاخهٔ `claude/p2-close` (ردیف‌های P2 ِ PARTIAL/MISSING؛ روی main ‏b6419a69 = دستهٔ ۴۰؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: ممیزیِ ۳۹ ردیفِ P2 با سنجشِ کد؛ دو کمبودِ کدیِ سرور/وب بی مانع ساخته شد، هر دو پشتِ پرچمِ خاموش:
  (۱) PRM-11: قاعدهٔ تخفیفِ گروه‌دار در ویترین — `shop.storefront.promo_groups`، `lib/storefront-promo-groups.js`؛ گروه فقط از قرض‌دارِ همین دکان با
  همان شمارهٔ تأییدشده (`store-account-history.debtorRowsOf`)، دو گروهِ ناهم‌سان ⇒ بی گروه، هرگز از بدنه؛ ثبت، پیش‌نمایش و افزودنِ قلم (CHK-21) یکی.
  (۲) SEC-10: «پاک کردنِ داده‌های من در این دکان» در «حسابِ من» ِ وب (`store/erase.js`، `features.erasure`، واژه‌نامهٔ `erase.*.json`).
  آزمون: `storefront-promo-groups` ۵/۵ و `customer-erasure` ۶/۶ (PGlite و PostgreSQL ۱۶)، کرومیومِ `web-store-erase.mjs` ۳۱ ✅ با axe (هر دو پایگاه)؛
  `web-store-allflags.mjs` حالا `shop.customer_erasure` را هم روشن می‌کند (۵۸ پرچم، سبز). ردیف‌های کهنه درست شد: PRM-11 («ویترین هسته را نمی‌خواند»)،
  NTF-10 («امروز no_channel» — پوشِ وب از دستهٔ ۳۹ کانالِ `push` است)؛ شاهدِ CI ِ کادرِ وب ِ MKT-06 (main ‏b6419a69، اجرای 42752721248) ثبت شد.
- نیمه‌کاره: هیچ‌کدام بی شاهدِ CI ارتقا نیافت. ORD-15 (کسرِ سفارشِ پرداخت‌شدهٔ آنلاین) دفترِ بازپرداختِ «معلق» با روشِ غیرنقد می‌خواهد ⇐ تصمیمِ صاحب‌کار
  دربارهٔ روشِ بازپرداختِ پیش‌پرداخت؛ AI-03/05 رمزگشا/رمزگذارِ تصویر روی سرور می‌خواهند (وابستگیِ تازه ⇐ تصمیم)؛ RPT-18 «صفحه در پنل» روشن نیست کدام پنل.
- قدمِ بعدی: شاهدِ CI ِ دو آزمونِ تازه و `web-store-erase.mjs`؛ آزمونِ رسیدنِ پوزشِ NTF-10 از پوشِ وب؛ متنِ ۱۶ راهنمای OPS-06 (O-14 گرفته شد، فقط Kotlin)؛
  آزمونِ خودکارِ صفحهٔ PRM-18 (Kotlin).
### ۱۴۰۵/۰۸/۲۰ — شاخهٔ `claude/ops-06` (OPS-06: متنِ راهنمای ۱۶ صفحه با F1، O-14 ب؛ روی main ‏b6419a69 = دستهٔ ۴۰؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: هر ۱۶ ردیفِ `core/guide/ScreenGuides.kt` READY با ۵ تا ۷ گامِ فارسی و انگلیسی (۸۹ گام) — هر گام پس از خواندنِ صفحهٔ **کامپیوترِ**
  همان مسیر (`DeskHome`، `DeskSale`، `DeskCustomers`، `DeskProducts`، `DeskPurchasing`، `ReportsDesk`، `StockRoutes`، `OrdersRouteContent`،
  ویترین، `Promos`، `TeamRoute`، `DevicesScreen`، تنظیماتِ نسخهٔ دوم)؛ قابلیتِ پشتِ پرچمِ خاموش (پرداختِ ترکیبی، برگشتِ v2، گاوصندوقِ «به‌زودی») نیامد.
  عنوان = سرخطِ `V3Route` با فارسی/انگلیسی/پشتوی همان جدول؛ پشتوی گام‌ها ساخته نشد (⇐ فارسی). رفعِ همراه: `AppRoot` مسیرِ `stock` را
  `warehouse` نمی‌کرد و راهنمای انبار هرگز نمی‌آمد ⇐ `ScreenGuides.screenOf`. آزمون‌ها (فقط CI): `ScreenGuidesTest` (۶؛ دو آزمونِ «همه جای خالی»
  عمداً به «همه READY» عوض شد)، `ScreenGuidesTextTest` ِ تازه (۴: عنوان، هر «…»/“…” = نوشتهٔ `Str*` با کلیدِ فهرست، دریِ نام = دریِ دکمه،
  مسیرِ واقعیِ نسخهٔ ۳)، `DariWordsTest` (+۱). محلی: آینهٔ همان قاعده‌ها با `kit/dari.js` (۱۶ راهنما، ۸۹ گام، ۰ ایراد)، `dari-words.js`،
  `dari-text` + `kotlin-file-mirrors` ۲۱/۲۱.
- نیمه‌کاره: ویدیوها؛ پشتوی ۸۹ گام (بازبینی‌شده نداریم)؛ دکمهٔ «راهنمای این صفحه» روی گوشی (تصمیمِ صاحب‌کار، OWNER-ACTIONS بخشِ ۷). STATUS همان PARTIAL.
- قدمِ بعدی: شاهدِ CI ِ سه آزمونِ Kotlin؛ اگر صاحب‌کار نامِ دکمه‌ای را عوض کرد، `ScreenGuidesTextTest` همان راهنما را نشان می‌دهد.
### ۱۴۰۵/۰۸/۲۰ — شاخهٔ `claude/rsk-app` (RSK-03 «کارت در صف» و RSK-04 پین و صفحهٔ جدای فهرست، سمتِ برنامه؛ روی main ‏b6419a69 = دستهٔ ۴۰؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: (۱) سرور: `GET /api/shop/orders/risk/queue` (`order-risk.queueOf`، `routes/order-risk-queue.js`، سوار پیش از `/shop/orders`؛ سطلِ IP و سقفِ هر کاربر)
  فقط سفارش‌های پیش از ارسال با هشدار، نشانه‌ها همان `riskOf`؛ `order-risk-queue` ۳/۳ (اول ۰/۳ سرخ) روی PGlite و PostgreSQL ِ محلی، `docs/api/routes.json` تازه؛
  `app.js` با فشرده کردنِ دو توضیح ۸۰۰ خط ماند. (۲) هسته: `OrderRisk.queue/queueCard/cardFlags/orderPin/pinEntryError/pinEntryBody/typedPhone`. (۳) برنامه:
  کارتِ بالای صف و نشانِ کارتِ هر سفارش (`OrderRiskQueueCardV3`، `OrderCard.risk`)، «بستنِ این جا» در کارتِ سابقه، صفحهٔ `orders/risk-lists` (`OrderRiskListsV3.kt`) از «…» ِ صف، `OrdersConfigDto.risk`.
- نیمه‌کاره: آزمون‌های Kotlin (`OrderRiskTest` +۶، `OnlineRoutesTest` +۱، `V3ReachabilityTest` +۳ ردیف) این‌جا ساخته نشدند — فقط CI؛ کارتِ وب (وبِ یخ‌زده)؛ «دستگاه» ِ RSK-04.
- قدمِ بعدی: شاهدِ CI ِ همین آزمون‌ها روی PR ِ دسته؛ دیدنِ کارت و صفحه روی دستگاه با پرچمِ `shop.order_risk` روشن.
### ۱۴۰۵/۰۸/۱۹ — شاخهٔ `claude/prd-08` (PRD-08: کالای وزنی با ۳ رقمِ اعشار، سرتاسری؛ روی main ‏b6419a69 = دستهٔ ۴۰؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: دروازهٔ یگانه `core/money/QtyExact` (+ هم‌زادِ `qtyExact` در `license/money.js`، بردارِ `core/test-vectors/qty-exact-v1.json` با سازندهٔ
  `server/scripts/qty-exact-vectors.js`) و در برنامه `data/QtyExactWiring` پشتِ پرچمِ خاموشِ `shop.qty_exact` (کلیدِ `qtyExact` در
  `GET /api/shop/stock-ledger/config` ⇐ `StockFlag`). روشن ⇒ مبلغِ ردیفِ مقدارِ اعشاری با قاعدهٔ ۳.۴ (= سفارشِ آنلاین)، همهٔ ۳۸ صدای
  `SalesEngine.lineTotal` از همان؛ جمعِ سبد، جای خالی تا موجودی، قلم و حرکتِ انبار به هزارمِ دقیق؛ پیامِ کمبود با اعشار؛ کادرِ مقدارِ برگهٔ ردیف
  (نسخهٔ ۳: پیام؛ کامپیوتر: کادر رقمِ چهارم نمی‌گیرد) و پیش‌نمایشِ مبلغِ `CartLineDialog`. مقدارِ درست و پرچمِ خاموش بیت‌به‌بیت همان. بی مهاجرت.
- آزموده: `qty-exact.test.js` ۶/۶ (جاروب ۸۹۹٬۱۰۰ ⇒ ۱۳۱ فرق، همه +۱ افغانی؛ دو جهش کشته)، `shop-stock-ledger.test.js` ۲/۲ روی PGlite و
  PostgreSQL. ⚠️ فقط CI: `QtyExactTest`، `QtyExactVectorsTest`، `QtyExactWiringTest` (Kotlin اینجا ساخته نمی‌شود).
- نیمه‌کاره/کنار: گرد کردنِ دقیقِ ردیفِ **مقدارِ درست** با قیمتِ پول‌دار (۵۰ × ۰٫۲۹: امروز ۱۴، دقیق ۱۵؛ ۴۲ از ۹۰٬۰۰۰) تصمیمِ جدای صاحب‌کار
  است و عمداً وصل نشد؛ `index.html` ِ یخ‌زده همان قاعدهٔ امروز؛ برگشتِ بخشی همان `ReturnShare`.
- قدمِ بعدی: CI ِ سبزِ سه آزمونِ Kotlin، سپس ارتقای PRD-08 و روشن کردنِ `shop.qty_exact` برای یک دکانِ آزمایشی (`OWNER-PENDING` ۳-۱۴ب، `FLAGS-ON` §۱۴).
### ۱۴۰۵/۰۸/۲۰ — شاخهٔ `claude/code-gaps` (شکاف‌های کدیِ S70، SEC-03 و S48؛ روی main ‏b6419a69 = دستهٔ ۴۰؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: (۱) **SEC-03، بازبینیِ خصمانه:** نگهبانِ S89 حالتِ `enforce` را فقط از `device_id` ِ بدنه می‌گرفت ⇒ نشستِ دستگاهِ زنجیره‌دار با شناسهٔ تازه (v1) یا از `/api/sync`
  مبلغِ فاکتورِ ثبت‌شده را سرِ جا عوض می‌کرد. حالا «زنجیره‌دار» = شناسهٔ ادعاشده یا دستگاهِ نشست (`lib/finalized-sales.js` `chainDevice`/`boundOf`؛ `lib/sync.js` و
  `routes/sync.js` `boundDevice`)؛ راهِ قدیم با نشست/شناسهٔ زنجیره‌دار ۴۰۹. `sync-v1.js` ِ مشترک دست نخورد. آزمون: `finalized-sales-bound.test.js` ۵/۵ (PGlite و PostgreSQL، پیش از رفع ۳ سرخ).
  (۲) **S70:** `market-units.sizeOfItem` (اندازهٔ واحدِ بی عدد از `saleUnit` و `packs`، فقط نامِ روشن و نسبتِ دقیق) در `sameProduct`؛ رابطِ وب «همین کالا در دکان‌های دیگر» روی برگهٔ
  کالای ویترین (`store/same.js` ِ دیربار + واژه‌نامهٔ `same`، فقط با `bankProductId`)؛ آزمون‌ها: `market-units` ۶/۶، `market-same-units` ۲/۲ (هر دو پایگاه)، `web-store-same.mjs` ۲۹ ✅ با axe.
  (۳) **S48:** شکافِ کدی نماند؛ PENDING ِ کهنهٔ `scenario-s48` درست شد و `S48.keys` حالا Enter ِ `reports.drill` را هم می‌سنجد.
- نیمه‌کاره/باز: سندهای برگشت/قرض/خرید/حرکت نگهبانِ «ثبت‌شده» ندارند؛ ویرایشِ فاکتورِ حذف‌شده آن را با محتوای تازه زنده می‌کند (حذف فقط از راهِ `log`)؛ برنامهٔ خریدِ مشتری
  «همین کالا» را ندارد؛ نشستِ بی زنجیره همچنان `log` (F0-02).
- تصمیمِ صاحب‌کار: SEC-03 — رد (نه فقط رویداد) برای دستگاهِ بی زنجیره و `/api/sync` وقتی `shop.doc_chain` روشن است (برنامهٔ کهنه ویرایشش رد می‌شود)، و پیش‌فرضِ خودِ `shop.doc_chain`؛
  S48 — پیش‌فرضِ «گزارشِ تازه» (`report_fresh`) و `shop.owner_report`؛ S70 — `market.directory` و `market.product_details`.
- قدمِ بعدی: شاهدِ CI ِ دسته (نام‌به‌نام) برای `finalized-sales-bound`، `market-units`، `market-same-units`، `web-store-same` و `DayEndTest`؛ نگهبانِ سندهای برگشت/قرض/خرید (S89).
### ۱۴۰۵/۰۸/۲۰ — شاخهٔ `claude/acct-console` (بندهای سمتِ سرورِ حسابِ ممیزیِ پنلِ مرکزی §۹: بازگرداندنِ یک‌باره، S12، S15، S18؛ روی main ‏ed8009d7 = دستهٔ ۳۸؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: (۱) `POST /admin/sync/conflicts/:id/restore` یک بار — شناسهٔ یک‌شکل (۴۰۰ `bad_id`)، ۴۰۹ `already_restored`/`restore_in_progress` با نشانِ پایگاه
  (`sync_conflict_restore_claims`، مهاجرتِ ۴۹۵)، `Idempotency-Key` (`lib/sync-restore-once.js`؛ `sync-v1.js` دست نخورد). (۲) S12: نگهبانِ مالک از پیش بود
  (بستن/تنزل/نشست/کلیدِ سرویس)؛ شکافِ مانده «مالک = پیشین‌ترین ارشد، فعال یا نه» بسته شد (`lib/admin-owner.js`: فقط ارشدِ فعال، جابه‌جاییِ نشانِ
  مانده با سابقه). (۳) S15: `GET /admin/subscriptions/period-end` با همان `subscriptions.periodEnd` ِ اعطا. (۴) S18: `X-Admin-API-Version` و
  `GET /admin/contract` از `server/src/lib/admin-contract.json` (ساختهٔ `generate.cjs` از `docs/api/routes.json`). آزمون‌ها: `admin-sync-restore-once` ۸،
  `admin-owner-active` ۷، `admin-bridge-contract` ۸ (پیش از تغییر ۱۹ از ۲۳ سرخ؛ چهار سبزِ ماندنی نگهبانِ پسرفت‌اند)، روی PGlite و PostgreSQL ِ محلی؛ شرح در RULES «v3 · پنلِ مدیر» و بندِ ۱۲.۱۰ ِ ADMIN-CONTROL-PLANE.
- نیمه‌کاره: سمتِ پنل (ریپوی server) — فرستادنِ `Idempotency-Key` به بالادست، جایگزینیِ `addPeriod` با `period-end`، خواندنِ `/admin/contract`؛ S14 فقط پیشنهاد (تصمیمِ صاحب محصول).
- قدمِ بعدی: شاهدِ CI ِ سه آزمونِ تازه؛ تغییرِ پنل پشتِ توانایی‌های `sync-restore-once.v1`، `subscription-period.v1` و `admin-contract.v1`.
### ۱۴۰۵/۰۸/۲۰ — شاخهٔ `claude/p1-close` (ردیف‌های P1 ِ همهٔ خانواده‌ها؛ روی main ‏ed8009d7 = دستهٔ ۳۸، سپس ادغامِ main ‏f8134445 = دستهٔ ۳۹؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: ممیزیِ ۶۱ ردیفِ P1 (PARTIAL/MISSING) با سنجشِ کد؛ فقط دو کمبودِ کدیِ سرور/وب بی مانع ماند و هر دو ساخته شد:
  (۱) CRM-09: دفترِ نشانیِ حساب (ویترین و بازارچه) ناحیه، دروازه، ولایت، گذر، منزل/طبقه، شمارهٔ دوم و Plus Code را نگه می‌دارد — مهاجرتِ ۴۹۰،
  `orders.addressExtras` ِ مشترک با ثبت، `PUT` ِ برنامهٔ پیشین پاکشان نمی‌کند؛ `acct.js` کادرها را پر و «جزئیاتِ بیشتر» را باز می‌کند.
  (۲) RPT-14: بالای قیفِ ویترینِ وب (بازدید ⇐ دیدنِ کالا ⇐ سبد) — مهاجرتِ ۴۹۱، `lib/storefront-funnel.js`، `POST /api/store/:slug/funnel/:step`،
  تپش در `store.js` (+۴۴۳ بایت؛ بارِ نخست پس از ادغامِ دستهٔ ۳۹ ۷۰٬۹۴۹ از ۷۲٬۷۰۴)، `visits`/`top` در گزارش؛ هم‌زادِ کوچکِ Kotlin (`OnlineReport.Top` و بخشِ «بالای قیف» در صفحهٔ گزارش).
  آزمون: `store-address-extra` ۴/۴، `storefront-funnel` ۶/۶ (PGlite و PostgreSQL ۱۶)، کرومیومِ `web-store-p1.mjs` ۱۷ ✅ با axe (هر دو پایگاه).
- نیمه‌کاره: `OnlineReportTest` (+۱) و کامپایلِ `OnlineReportPage`/`StrOnlineReport` فقط در CI. ردهٔ بقیه در گزارشِ شاخه: شاهدِ CI (WEB-11، MKT-02، …)،
  تصمیمِ صاحب‌کار (کدِ پستیِ اختیاری در ویترین که نگهبانِ LOC-10 می‌بندد، POS-06/32/40، PAY-16، PUR-03، SEC-01، SYN-07، CHK-18، DLV-06، OPS-01/09، LOC-04، CRM-10)،
  دستگاه/سرویسِ بیرونی (D-08، D-09، ترازو، کشو، چاپگر، AI-01/02 که رمزگشای تصویرِ بومی روی سرور می‌خواهد) و فقط Kotlin.
- قدمِ بعدی: شاهدِ CI ِ دو آزمون و `web-store-p1.mjs`؛ تپشِ همان قیف در برنامهٔ خریدِ بومی؛ NTF-08 با پوشِ وبِ مشتریِ دستهٔ ۳۹ حالا ساختنی است:
  رویدادِ «پول برگشت» برای **هر** ردیفِ `online_order_refunds` در صفِ `customer_notify` (یکتایی امروز (سفارش، رویداد) است ⇒ مهاجرت برای (سفارش، رویداد، شناسهٔ بازپرداخت) و CHECK ِ `event`).
### ۱۴۰۵/۰۸/۱۹ — شاخهٔ `claude/ci-evidence` (ردیف‌های STATUS که فقط شاهدِ CI کم داشتند؛ روی main ‏f8134445 = دستهٔ ۳۹؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: از ۱۴۱ ردیفِ PARTIAL/NOT-VERIFIED که از CI نام می‌برند فقط POS-01، WEB-11 و MKT-02 جز شاهدِ CI کمبودی نداشتند. لاگِ کاملِ کارهای main ‏f8134445
  (PostgreSQL 129585341948، PGlite 129585342405، مرورگر 129585342400، «برنامهٔ خریدِ مشتری (گزارش)» 129585337686، «کامپایل و آزمون» 129585337797) و iOS ِ
  سرِ PR ِ #1023 (129573624196) خوانده شد. هیچ ردیفی ارتقا نیافت: لاگِ Gradle ِ اندروید، کامپیوتر و برنامهٔ خرید فقط `> Task :test` و BUILD SUCCESSFUL
  می‌نویسد (`testLogging` بی `events`) ⇒ `CatalogCountsTest`/`CartQueryTest` (POS-01)، `CustomerAccountTest` (WEB-11) و `CustomerStage2Test` (MKT-02)
  نام‌به‌نام دیده نمی‌شوند. شاهدِ دیده‌شده در WEB-11 و MKT-02 ثبت شد (JS، مرورگر و `<testsuite>` ِ iOS برای `ShopperAccountTest` ۸ و `ShopperStage2Test` ۷).
- نیمه‌کاره: سه ردیفِ بالا تا شاهدِ نام‌به‌نامِ Kotlin.
- قدمِ بعدی: گامِ `grep -ho '<testsuite [^>]*'` (مثلِ `ios-spike.yml`) پس از آزمونِ اندروید/کامپیوتر/برنامهٔ خرید، یا `events` در `testLogging`؛ سپس همین سه ردیف.
### ۱۴۰۵/۰۸/۲۰ — شاخهٔ `claude/ntf-refund` (NTF-08: پیامِ هر بازپرداخت به مشتری؛ روی main ‏f8134445 = دستهٔ ۳۹؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: هر ردیفِ تازهٔ دفترِ بازپرداخت (بخشی/پایانی/کامل) در همان تراکنش یک ردیفِ `customer_notify_outbox` می‌سازد (`onRefundIn`)؛ مهاجرتِ ۴۹۶ (+rollback):
  ستونِ `refund_id`، رویدادِ `order_refund:<refund_id>` و CHECK ِ تازهٔ `event` که آن را به `refund_id` می‌بندد، FK به بازپرداختِ همان سفارش؛
  UNIQUE (order_id, event) ِ ۱۴۳ عمداً ماند (نسخهٔ پیش با `ON CONFLICT (order_id, event)` باید بنویسد، OPS-03). متن با مبلغِ همان بازپرداخت
  (`store-alert-push.money`) و شمارهٔ سفارش، fa/ps/en؛ پشتِ `shop.customer_notify` + `shop.orders.refund_tracking` (بی پرچمِ تازه)؛ فقط کانالِ رایگان.
  پوشِ وب: `store-push.quietOf` ⇒ با این پرچم‌ها اشتراک پس از تحویل تا بسته شدنِ بازپرداخت می‌ماند، کارت با شرحِ `np.hint.rf`، برچسبِ جدای هر
  بازپرداخت. برنامهٔ دکان: نامِ `order_refund` و `order_late_apology` در `OrderNoticesUi.kt`. رفعِ کوچک: جایگزینیِ `render` با تابع (نامِ ویترینِ دارای `$&`).
  آزمون: `customer-notify-refund.test.js` ۱۵/۱۵ (PGlite و PostgreSQL ِ محلی؛ پیش از کد ۱۲ سرخ)، `web-store-refund-push.mjs` ۲۶ ✅ (هر دو پایگاه؛ پیش از کد سرخ).
- نیمه‌کاره: شاهدِ CI؛ Kotlin فقط در CI (دو رشته و دو شاخهٔ `when`، بی آزمونِ تازهٔ Kotlin — هم‌سانی با آزمونِ سرور قفل است)؛ نمای برنامهٔ خرید (native).
- تصمیمِ صاحب‌کار: پیامِ بازپرداخت تنها پیامی است که مبلغ دارد (روی صفحهٔ قفلِ گوشی هم دیده می‌شود؛ همان مبلغی که صفحهٔ پیگیری با همین پرچم نشان می‌دهد)؛ پیامک برای آن نیست.
- قدمِ بعدی: پس از CI ِ سبز شاهد در ردیفِ NTF-08 (هنوز PARTIAL به‌خاطرِ برنامهٔ خرید).
### ۱۴۰۵/۰۸/۱۹ — شاخهٔ `claude/kt-testlog` (نامِ آزمون‌های Kotlin در لاگِ CI برای شاهدِ STATUS؛ روی `origin/main` f81344458؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: بلوکِ `tasks.withType<Test>()` ِ `desktop`، `core`، `android-native/app` و (تازه) `customer-android/app` برای هر آزمون یک خطِ
  `KTEST PASS|FAIL|SKIP <کلاسِ کامل> > <نامِ آزمون>` چاپ می‌کند (`addTestListener`) و `events(FAILED, SKIPPED)` با `exceptionFormat = FULL`؛ PASSED ِ خودِ
  Gradle نه (دو خط برای هر آزمون). بی افزونه/وابستگیِ تازه؛ ورک‌فلو دست نخورد. خطِ تازه در RULES «CI و آزمون».
- برآوردِ خطِ افزوده (شمارِ `@Test`): «کامپایل و آزمون» ~۵٬۳۴۰ (desktop ۳٬۷۰۲ + core ۱٬۶۳۸)، «ساخت نسخهٔ بومی» ~۵٬۰۰۰ تا ~۶٬۶۰۰
  (app ۳٬۳۵۸ + core ۱٬۶۳۸، و گامِ دومِ «آزمون‌های core» اگر دوباره بدود)، «برنامهٔ خریدِ مشتری (گزارش)» ۸.
- نیمه‌کاره: Gradle محلی نیست؛ کامپایلِ اسکریپت‌ها و شکلِ خط فقط در CI ِ PR ِ دسته دیده می‌شود.
- قدمِ بعدی: پس از سبز شدنِ دسته، ردیف‌های STATUS ِ Kotlin فقط با خطِ `KTEST PASS` ِ دیده‌شده + شناسهٔ run/job بالا بروند.
### ۱۴۰۵/۰۸/۱۹ — شاخهٔ `claude/perf-2` (PERF-02/04: شمارش زیرِ بار، «قطعِ سراسری» با بارسازِ بی‌اشباع، دروازه در برابرِ مهلتِ برنامه‌ها؛ روی `origin/main` ِ دستهٔ ۳۹؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: (۱) مهاجرتِ ۴۶۵ (نمایهٔ جزئیِ «شمارشِ دیررسیده»: `v3.lateCount` و شاخهٔ شمارشِ `STOCK_SQL` همهٔ شمارش‌های دکان را می‌خواندند؛ ۱۰ هزار ردیف
  ۴۴۴ ⇐ ۲ بافر)، آزمونِ `sync-v3-late-count.test.js` (پیش از رفع سرخ) و `scripts/late-count-bench.js`. (۲) ابزارِ بار: انبارگردانی در آمیزه
  (`sync3.count`، `--count-history`)، سنجهٔ `counts`، `--plan` (چند پیکربندیِ سرور روی یک پایگاهِ کاشته)، `--check-each`، یورشِ دستگاه‌ها بیرون از سقف
  (`--max-inflight 4096`)، درنگِ برنامه پس از شکست و زمانِ تخلیه، زمانِ دروازه/اتصال/اجرا، تازه‌سازیِ نشست روی ۴۰۱. (۳) `SYNC_V3_GATE_POOL_DEADLINE`
  (پیش‌فرض خاموش) با آزمونِ پیش از رفع سرخ. (۴) چهار اجرای ۲۰۰ × ۵ (ماشینِ مشترک، بارِ ۴ تا ۲۰؛ اجرای ۱ با راه‌اندازیِ دوبارهٔ ماشین نیمه ماند).
- یافته: مهلتِ واقعیِ برنامه‌ها ۱۵ ثانیه (وب) و ۱۵ + ۳۰ (اندروید)، نه ۱۰؛ با پیش‌فرض‌ها ۰ مهلت ولی دیرترین push ‏۱۴٫۴ ثانیه و ویترین p95 تا ۸٫۷ ثانیه
  در «قطعِ سراسری»؛ ترکیبِ `SYNC_V3_BATCH=1`+`PG_POOL_MAX_WAITING=100`+`HOT_CACHE_MS=2000` تخلیه را ~۲ برابر تند و ویترین را ≤ ۱٫۲ ثانیه کرد
  (جفت‌های هم‌ترتیبِ اجرای ۴)؛ `SYNC_V3_GATE_POOL_DEADLINE` به‌تنهایی زیرِ اشباع بدتر بود ⇒ خاموش بماند (`docs/PERF-LOAD-fa.md` «(سوم)»، جدولِ پیشنهاد).
- نیمه‌کاره: هیچ کدِ نیمه‌کاره. PERF-02/04 همان PARTIAL، PERF-03 همان (شاهدِ CI برای ۴۶۵ هنوز نیست). `request-deadline.test.js` روی پایگاهِ پرِ ماشینِ
  پربار یک بار سرخ شد (`DROP SCHEMA` زیرِ سقفِ ۴۰۰ms ِ خودِ آزمون)؛ روی پایگاهِ تازه و ماشینِ آرام ۴/۴ — دست نخورد.
- قدمِ بعدی (صاحب محصول): روشن کردنِ آن سه تنظیم در تولید؛ سپس همین `--plan` روی سخت‌افزارِ جدا. اگر صاحب‌کار پرچمِ آزمودهٔ
  `SYNC_V3_GATE_POOL_DEADLINE` را نمی‌خواهد، برداشتنش (سه پرونده + یک آزمون) بی‌خطر است.
### ۱۴۰۵/۰۸/۲۰ — شاخهٔ `claude/shopper-gaps` (کاستی‌های برنامهٔ خرید در برابرِ ویترین: WEB-13 و CHK-14؛ روی `origin/main` ‏f81344458؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: (۱) WEB-13 — ردیفِ «دیده‌شده‌های اخیر» در صفحهٔ دکانِ برنامهٔ خرید با همان کلیدِ `recent` ِ `GET /api/store/:slug` و همان قاعدهٔ
  `RecentlyViewed` (core/shopper `RecentProducts`، `ShopperState.seen`؛ هر دکان ۱۲ کالا، ۳۰ روز، حداکثر ۲۰ دکان)، فقط روی دستگاه، `?ids=` با هر ورود
  (نیامده ⇒ بیرون)، «پاک کردن»؛ `ui/ModelRecent.kt` + `ShopScreen.RecentRow`. (۲) CHK-14 — «با کدام اسکناس…» در برگهٔ ثبت فقط با
  `features.deliveryPrefs` و ارسال (`CheckoutRules.tenderOptions`/`tenders`/`tenderOf`، `CheckoutDelivery.cashTenderMinor`؛ `CheckoutScreen.Tender`)؛
  بردارِ مشترکِ `core/test-vectors/cash-tender-v1.json` با `server/test/cash-tender-vectors.test.js` (تابعِ `tenders` ِ `checkout.js` در vm و `checkTender` ِ سرور).
  هیچ درِ سرور، مهاجرت یا پرچمِ تازه‌ای نیست؛ پرچمِ خاموش ⇒ همان بدنه و همان صفحه.
- نیمه‌کاره: آزمون‌های کاتلین (`ShopperRecentTest`، `ShopperCheckoutTest.tender…`، `ShopperApiTest.recentAndDeliveryPrefs…`، `ShopperTenderVectorsTest`،
  `CustomerRecentTenderTest`) نوشته شد ولی محلی اجرا نشد (بی Gradle) — شاهدِ CI. ویرایشِ اسکناس پس از ثبت (CHK-24) در برنامهٔ خرید هنوز نیست.
- قدمِ بعدی: پس از CI ِ سبز، ردیف‌های WEB-13 و CHK-14 ِ STATUS با شاهدِ CI؛ سپس CHK-24 (ویرایشِ دستورِ تحویل) در برگهٔ پیگیریِ برنامهٔ خرید.
### ۱۴۰۵/۰۸/۱۹ — شاخهٔ `claude/sec-review-39` (بازبینیِ امنیتیِ مستقلِ دستهٔ ۳۹؛ روی `ccr-3a0d593c-ac0jpj-batch-39`؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: تفاضلِ `server/src`، `server/public` و `license` ِ دستهٔ ۳۸..۳۹ خوانده و به آن حمله شد (پوشِ وب ِ سفارش و «خبرم کن»، SSRF، رمز، کاتالوگِ پرچم‌ها،
  `vip.js`، ۴۰۱ ِ پنل، بانکِ تصویر، تمرکزِ `kit`/`chat`، `upgrade-rehearsal`، `verification()`). دو یافتهٔ Low بسته شد (`docs/SECURITY-AUDIT-fa.md` B4):
  SR39-1 «باطل کردنِ لینک» وسطِ اشتراک یا وسطِ فرستادن لینکِ تازه را به دارندهٔ لینکِ باطل می‌داد؛ SR39-2 خطای خودِ سرور (`VAPID_SUBJECT` ِ نادرست)
  همهٔ اشتراک‌ها را پاک می‌کرد. `store-push.js` + یک واژه در صادراتِ `storefront.js` (`parseToken`)؛ آزمون `security-review-39.test.js` پیش از رفع سرخ روی هر دو پایگاه.
- نیمه‌کاره: هیچ. باز (فقط گزارش): `PUT /api/admin/pump/live-config` (کدِ پمپ) سنجشِ کاتالوگِ `admin-flags` را دور می‌زند — همان مدیرِ ارشد.
- قدمِ بعدی: اگر صاحب محصول بخواهد، درِ پمپ هم از `checkKnown` بگذرد (کارِ کدِ پمپ، نه این شاخه).
### ۱۴۰۵/۰۸/۱۹ — شاخهٔ `claude/e2e-pg` (سناریوهای سرتاسری روی PostgreSQL؛ روی `claude/flag-catalog` ‏4da5ef2e؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: هر ۳۷ سناریو روی PostgreSQL ِ محلی (`E2E_DATABASE_URL`) — پایه: ۲۴۳ سبز، ۵ سرخ، ۱۵ اجرانشده. هیچ‌کدام باگِ محصول نبود:
  (۱) notify، ops، recovery: هارنس SQL ِ pg_dump را gzip کرده به `loadDataDir` ِ PGlite می‌داد (خطای gunzip) و ops قالبِ PGlite را
  می‌خواست ⇒ `test/e2e/lib/restore-copy.mjs`: قالب از نامِ فایل؛ PostgreSQL ⇒ دیتابیسِ تازهٔ `…_rtest` و `scripts/restore.js` (راهِ
  واقعیِ صاحب‌کار، روی PostgreSQL سالم دیده شد)، `control-sums.mjs sums` نشانیِ PostgreSQL هم می‌گیرد، `cutPower` برای «برق رفت».
  (۲) DOM ِ کهنه: E2E-ADM.counts (شمارِ بازکردنِ پیشین)، E2E-ADM.user (`/فعال$/` «غیرفعال» را هم می‌گرفت)، E2E-PCK-06.4 (`goto` با
  همان سند ⇒ جایگزینِ سفارشِ اول؛ رفعش `about:blank` در دستهٔ ۳۹، #1023، نه این شاخه)، S132 ِ customer با `all` (نوارِ سبد پیش از `route` ِ ویترین ⇒ `booted`).
  (۳) خاموشیِ ظرف میانِ `DROP SCHEMA public` و `CREATE` دیتابیسِ مشترک را بی public گذاشت ⇒ ۳۷/۳۷ سرخ ⇒ `IF EXISTS` در `harness.mjs`.
  شاهد (هر حالت سه اجرای پیاپی، با رفعِ pick ِ دستهٔ ۳۹): PostgreSQL پیش‌فرض و `all` هر بار ۲۶۳/۲۶۳، PGlite پیش‌فرض و `all` هر بار ۲۶۲/۲۶۲.
- نیمه‌کاره: E2E روی PostgreSQL در CI نیست (فقط پیشنهاد در گزارش؛ ورک‌فلو دست نخورد).
- قدمِ بعدی: کارِ `e2e-pg` در `server.yml` (سرویسِ postgres:16 و `E2E_DATABASE_URL`، ubuntu-24.04 برای psql/pg_dump ِ ۱۶).
### ۱۴۰۵/۰۸/۱۹ — شاخهٔ `claude/flag-catalog` (کاتالوگِ کاملِ پرچم‌ها و «همه روشن» ِ سرتاسری؛ روی `ccr-3a0d593c-ac0jpj-batch-38`؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: (۱) ۵۵ کلیدِ `live_config` که کدِ غیرِ پمپ می‌خواند و در `KNOWN` نبود (خانوادهٔ `shop.delivery_*`، `shop.visual_search`، `pay.hesabpay`،
  `shop.promo_*`، `shop.owner_report`، همگام‌سازیِ دکان، بازار و کارمزدها، ops/security و تنظیم‌های عددی/شیء) با پیش‌فرضِ خودِ کد ⇒ ۱۴۲ ردیف.
  (۲) `server/test/flag-scan.js` + سنجهٔ `console-shell.test.js`: سرور، `public/*`، `license/` و کاتلینِ برنامه‌ها؛ روی پایه سرخ با فهرستِ همان ۵۵؛
  بیرون فقط با دلیل (`NOT_CATALOG`). (۳) `admin-flags.test.js`: برای هر ۵۵ ردیف بی ردیف = پیش‌فرضِ کاتالوگ، نوشتنِ همان پیش‌فرض بی‌اثر، مقدارِ
  دیگر اثردار (جهشِ پیش‌فرض سرخ می‌کند). (۴) `E2E_FLAGS_ON=all` (۱۱۸ پرچم): پیش از کار ۲۲ سناریو سرخ — همه از `subscribers_only` ِ بی اشتراک ⇒
  `harness.mjs` با آن پرچم اشتراکِ فعال می‌دهد (`subscribe: false` برای admin/entitle)؛ سپس ۱۶ گامِ سرخ، هیچ‌کدام باگِ محصول نبود:
  `shop.delivery_code_rule` (پیک کدِ برگشته از سپردن را می‌فرستد)، `shop.delivery_near_pin` (S31 موقعیتِ پین با `nearPin`)، `shop.order_risk`
  («بی پرچمِ ریسک» صریح در شش سناریوی سبدِ تکراری)، `shop.delivery_live_location` (فازِ «بی پرچم» ِ DLV-16 صریح)، `link_expiry` (`sameOrderLink`
  در pick). نتیجه: ۲۶۲/۲۶۲ گام با `all`.
- نیمه‌کاره: شاهدِ CI. تصمیمِ صاحب‌کار: `shop.orders.ledger` (هنوز `order-ledger.LEGACY_FLAGS` هنگامِ راه‌اندازی)، دو پرچمِ مشترک با پمپ
  (`sync.device_bind`، `sync.live_expiry_close`) و `stage.*` ِ پویا بیرون از کاتالوگ‌اند. PostgreSQL ِ `E2E_DATABASE_URL`: notify، ops، recovery و pick
  بی پرچم هم سرخ‌اند (قالبِ پشتیبانِ PGlite، DOM ِ کهنهٔ پیگیری) و `E2E-ADM.counts` با فهرستِ پیشین هم ناپایدار است (DOM ِ کهنه) — نه از این کار.
- قدمِ بعدی: پرچمِ تازه ⇐ ردیفِ `KNOWN` با پیش‌فرضِ کد (وگرنه `console-shell.test.js` سرخ)؛ پس از CI ِ سبز همین `E2E_FLAGS_ON=all` در CI.
### ۱۴۰۵/۰۸/۱۹ — شاخهٔ `claude/web10-push` (WEB-10: «خبرم کن» ِ کالا روی پوشِ وب؛ `CustomerErasure.kt`؛ روی `claude/p0-close` ‏2f502da1؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: (۱) «خبرم کن وقتی موجود/ارزان شد» حالا کانالِ رایگانِ پوشِ وب دارد (مهاجرتِ ۴۷۵ `store_alert_push`، `lib/store-alert-push.js`، درِ
  `POST/DELETE /api/store/:slug/alerts/push`، `store/alert-push.js` + `store/push-client.js` ِ مشترک با پیگیریِ سفارش): اشتراک برای (دکان، شمارهٔ
  تأییدشده، مرورگر) با اثباتِ همان تأیید (`storefront.verification`، تنها منبعِ `verifiedPhone`)؛ بلیتِ باطل/منقضی، نشستِ بسته یا شمارهٔ عوض‌شده ⇒
  هیچ پیام؛ ۳ مرورگر، سقفِ ۲۴ ساعتهٔ دکان (`STORE_ALERTS_SHOP_PER_DAY`)، SEC-10 `alertPushSubs`، پاک کردنِ حساب؛ پیام فقط نام و قیمت. رمز/VAPID/SSRF
  فقط از `store-push.fanOut` (کپی نشد). (۲) `CustomerErasure.kt` کلیدهای `lists`، `pushSubs`، `alertPushSubs` (+ متن‌های `StrErasure`) و آزمونِ هم‌سانی
  با `COUNT_KEYS` ِ سرور در `ServerFeatureScreensTest`. آزمون: `store-alert-push.test.js` ۱۱/۱۱ (PGlite و PostgreSQL، پیش از کد ۱۱ سرخ)، جهش ۱۳/۱۳،
  `web-store-alert-push.mjs` ۳۷ ✅، `web-store-push.mjs` ۳۸ ✅، `web-store-finder.mjs` ۱۷ ✅.
- نیمه‌کاره: شاهدِ CI؛ Kotlin فقط در CI (`ServerFeatureScreensTest`)؛ آزمون با سرویسِ پوشِ واقعیِ مرورگر روی گوشی (این ماشین به FCM راه ندارد).
- قدمِ بعدی: پس از CI ِ سبز شاهد در ردیفِ WEB-10 (هنوز PARTIAL به‌خاطرِ D-09 برای مشتریِ بی پوش)؛ اگر صاحب‌کار بخواهد، «فرستادنِ دوباره»ی خبرِ
  `no_channel` ِ تازه پس از روشن کردنِ پوش (امروز عمداً نیست: کارِ مشتری هیچ پیامی برنمی‌انگیزد).
### ۱۴۰۵/۰۸/۱۹ — شاخهٔ `claude/p0-close` (ردیف‌های P0 ِ ویترین و مشتری: دسته‌بندی و پوشِ وب ِ مشتری؛ روی `ccr-3a0d593c-ac0jpj-batch-38`؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: (۱) دسته‌بندیِ ۲۱ ردیفِ P0 ِ نیمه‌کارهٔ WEB/CHK/ORD/DLV/PCK/CRM/NTF/LOC (+ PAY-07/23 ِ سفارشِ آنلاین): تقریباً همه پشتِ D-09، O-01، D-08،
  تصمیمِ صاحب‌کار (نقشه، مترادف، CDN، وبِ پیک، رد یا بررسیِ سقفِ اعتبار) یا کارِ Kotlin‌اند. تنها شکافِ کدیِ ساختنی «پیامِ تأیید» بود:
  §۳.۱۲ برای مشتری «پوشِ PWA» را نخستین کانال می‌داند و هیچ حسابِ بیرونی نمی‌خواهد. (۲) پوشِ وب ِ مشتریِ ویترین (CHK-20، NTF-01، ORD-16؛
  مهاجرتِ ۴۷۰؛ پرچمِ خاموشِ `shop.storefront.web_push` + `shop.customer_notify`): `lib/store-push.js` (VAPID ِ خودِ سرور، رمزِ aes128gcm،
  فهرستِ سفیدِ SSRF، حداکثر ۳ مرورگر با `FOR UPDATE`، لینکِ باطل ⇒ اشتراک پاک، SEC-10 `pushSubs`)، `customer-notify.builtin/catchUp`
  («ثبت شد» ِ ماندهٔ پیش از «خبرم کن» فقط از پوش، `only_channel`)، درِ `POST/DELETE /api/store/:slug/orders/:token/push`، کارتِ
  `store/order-push.js` و `store/sw.js`. `store-push.test.js` ۱۱/۱۱ (PGlite و PostgreSQL ِ محلی)، جهش ۱۲/۱۲، `web-store-push.mjs` ۳۸/۳۸
  (کرومیومِ کامل؛ PGlite و PostgreSQL)، `web-store-allflags.mjs` ۳۳۵ ✅ با پرچمِ تازه.
- نیمه‌کاره: شاهدِ CI؛ `CustomerErasure.kt` کلیدِ `pushSubs` را نشان نمی‌دهد (مثلِ `lists`)؛ `storefront-alerts` (WEB-10) هنوز پوشِ وب ندارد؛
  آزمون با سرویسِ پوشِ واقعیِ مرورگر روی گوشی (این ماشین به FCM راه ندارد). یافتهٔ امنیتیِ بیرون از دامنه: `station-chat.js` (پمپ،
  دست‌نخورده) endpoint ِ پوشِ مشتری را بی فهرستِ سفید POST می‌کند (SSRF) — رفعش اجازهٔ کدِ پمپ می‌خواهد.
- قدمِ بعدی: پس از CI ِ سبز، ارتقای ردیف‌های «فقط شاهدِ CI»؛ «خبرم کن ِ موجود شد» (WEB-10) روی همین کانال؛ جوابِ D-09 برای مشتریِ بی پوش.
### ۱۴۰۵/۰۸/۱۹ — شاخهٔ `claude/p0-shop` (ممیزیِ P0 ِ خانواده‌های پشتِ صحنه: POS، PAY ِ دکان، PRD، PRM، INV، PUR، RET، STF، RPT، HW، SYN، SEC، F0، OPS؛ روی `ccr-3a0d593c-ac0jpj-batch-38`؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: ۳۸ ردیفِ P0 ِ نیمه‌کاره رده‌بندی شد؛ تنها کارِ کدیِ سرور/وبِ مانده PRD-07 بود: `server/src/lib/product-unit-rules.js` پشتِ پرچمِ خاموشِ
  `shop.product_unit_rules` (تغییرِ واحدِ پایهٔ کالای گردش‌دار ⇒ `unit_locked`، ضریبِ تازهٔ ناجور ⇒ `unit_factor_inexact`؛ v3 از `authorize`، v1 از
  `newer-fields.v1NewerFields` با گردشِ همان دسته؛ ⛔ `sync-v1.js` دست نخورد) — `product-unit-rules.test.js` ۸/۸ روی PGlite و PostgreSQL (و `SYNC_V3_BATCH=1`).
  STATUS ِ PRD-07، PRD-12 (کادرِ عمده در main با #1021) و POS-12 (قفلِ «در دستِ دستگاهِ X» در main با #1018) به‌روز شد.
- نیمه‌کاره: هیچ. Kotlin دست نخورد. راهِ قدیمِ `/api/sync` این قاعده را ندارد (برنامهٔ بسیار کهنه).
- قدمِ بعدی: شاهدِ CI ِ `product-unit-rules`؛ بقیهٔ P0ها شاهدِ CI، دستگاه/سخت‌افزار، حسابِ بیرونی (D-08، D-02) یا تصمیمِ صاحب‌کار‌اند (جدولِ گزارشِ همین شاخه).
### ۱۴۰۵/۰۸/۱۹ — شاخهٔ `claude/owner-decisions` (فهرستِ یکتای تصمیم‌های مانده؛ فقط سند؛ روی main ‏ed8009d7 = دستهٔ ۳۸؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: `docs/OWNER-PENDING-fa.md` ِ تازه: ۵۴ تصمیمِ مانده از STATUS، BLOCKERS، OWNER-DECISIONS/TODO/ACTIONS، FLAGS-ON، HANDOFF، PRهای
  #1019 تا #1023 ِ shop و #104، #112، #122 تا #125 ِ server؛ هر کدام با چیستی، ردیف‌های بسته، گزینه، پیشنهاد، خطر، راهِ اجرا و برگشت‌پذیری، در
  چهار گروه (مشتری/پول ۸، امنیت ۱۵، سرعت و کار ۱۵، خوب‌است ۱۶). هشت موردِ «تصمیمِ صاحب‌کار» ِ کهنه که پیش‌تر گرفته شده بودند جدا آمدند
  (D-15، D-16، O-06 ⇐ CHK-18/ORD-20، O-07 ⇐ DLV-06، O-09، subscribers_only، D-03، اجازهٔ push در server). قاعده در RULES («مشخصات جامع»).
- نیمه‌کاره: هیچ. قدمِ بعدی: با هر جوابِ صاحب‌کار بندِ همان تصمیم از OWNER-PENDING به «گرفته‌شده» ِ OWNER-DECISIONS برود؛ ستونِ «مانع» ِ
  CHK-18، ORD-20 و DLV-06 در STATUS هنوز «تصمیمِ صاحب‌کار» می‌گوید (O-06 و O-07 گرفته شده‌اند).
### ۱۴۰۵/۰۸/۱۹ — شاخهٔ `claude/guard-mirror` (آینهٔ JS ِ آزمون‌های کاتلینِ پرونده‌خوان؛ روی `main` ِ دستهٔ ۳۸؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: فهرستِ آزمون‌های کاتلینی که پروندهٔ سرور/وب/license/docs/بردار می‌خوانند (RULES-fa «v3 · CI و آزمون»). آینهٔ `node --test` در `server/test`،
  با فهرست‌های خوانده از خودِ کاتلین (`kt-source.js`، تجزیه‌گرِ سخت): `dari-text.test.js` حالا `farsiOnlyForms`/`farsiOnlyAllowed` را از DariCoverageTest.kt
  می‌خواند (پیش‌تر رونوشت) و همان گذرِ کاتلین را روی ۳۴۹ متنِ ShopperText + ۷۵۹ متنِ وب می‌زند (خرید پیش‌تر نبود)؛ `kotlin-file-mirrors.test.js`
  (WhatsNewFileTest، بخشِ پرونده‌ایِ WhatsNewTest، WebPaletteParityTest، CustomerOnlineOrdersTest)؛ `core-vectors-common-mirror.test.js`
  (CoreVectorsCommonParityTest: ۲۹ جدول، ۲٬۰۳۴ سطر، و فهرستِ بخش‌ها). `tools/whatsnew/check.mjs` بخش‌ها را هم برمی‌گرداند و سطرِ هم‌کلید و shipped=0 را
  مثلِ `WhatsNew.parse` مشکل می‌داند. پیش‌سرخ: حالتِ `e7bbb0e0a` ِ دستهٔ ۳۹ (np.fail بی PHRASES) سرخ، با `5f8fdfa60` سبز؛ هر آینه با تغییرِ موقت سرخ شد.
- نیمه‌کاره: هیچ. CI ِ سرور روی PR ای که فقط `docs/WHATS-NEW-fa.md`، `docs/DARI-REVIEW-fa.md` یا پروندهٔ کاتلینِ خوانده‌شده را عوض کند نمی‌دود (pathهای `server.yml`) — تصمیمِ هماهنگ‌کننده.
- قدمِ بعدی: آزمونِ کاتلینِ پرونده‌خوانِ تازه ⇒ آینه در همان PR؛ برای بردارهای فقط‌کاتلین (customer-price، gift، layaway، price-list، returns، special-order،
  stock-ledger) همتای JS نیست.
### ۱۴۰۵/۰۸/۱۹ — شاخهٔ `claude/sec-left` (بندهای بازِ ممیزیِ ۳۵: X1–X4 و لرزانیِ push-fcm؛ روی `main` + `claude/sec-audit-35`؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد (`server/test/security-audit-35b.test.js`، ردیف‌های X1–X4 در `docs/SECURITY-AUDIT-fa.md`): X1 `admin/sync/errors?limit=-1`؛ X2 NUL در
  پرسش **سراسری** ⇒ ۴۰۰ `bad_query` (جز `sync`/`shop/sync`/`pump`؛ دو سنجهٔ کهنه با دلیل درست شد: `customer-access` ِ `routes-idor-31` و
  `bad_product_id` ِ B4)؛ X3 کلیدِ تأیید زیرِ قفلِ دکان+کاربر و رونویسیِ کلیدِ کاربرِ دیگر بسته (روی PostgreSQL پیش از رفع هفت/هشت کلید)؛
  X4 سطلِ «ناموفق» ِ وی‌آی‌پی به‌ازای حساب و دکان (صاحب جدا، موقت، فقط همین در)؛ لرزانیِ `push-fcm` («250» در شناسهٔ تصادفی) با دانهٔ
  ثابت بازتولید و رفع، سنجه سخت‌تر (رقمِ فارسی و جداکنندهٔ هزارگان).
- نیمه‌کاره/تصمیم: P4 (کدِ وی‌آی‌پیِ پمپ فقط IP)، P5 (`sync/v1/status?device_id=%00` ⇒ ۵۰۰ در راهِ مشترک)، X5 (`sectionOf` و پروتوتایپ) فقط
  گزارش؛ P1–P3 همان. کاتلینی عوض نشد.
- قدمِ بعدی: P1/P4/P5 در کارِ پمپ با اجازهٔ صاحب‌کار؛ شاهدِ CI.
### ۱۴۰۵/۰۸/۱۸ — شاخهٔ `claude/sec-review-38` (بازبینیِ امنیتیِ دومِ دسته‌های ۳۷–۳۸؛ روی `ccr-3a0d593c-ac0jpj-batch-38`؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: سه یافته با آزمونِ پیش‌سرخ و رفعِ کوچک (`server/test/security-review-38.test.js`، شرح در `docs/SECURITY-AUDIT-fa.md` «B3»): SR38-1 (High) پشتیبانِ
  مهرشدهٔ PGlite رازها را در `pg_statistic` ِ ANALYZE ِ دوره‌ای خام داشت ⇒ `sealPglite` آمار را هم می‌بَرد؛ SR38-2 (Medium) reviver ِ NUL پارسِ هر بدنه را پیش از
  سقفِ نرخ ۱۰–۲۵ برابر کند می‌کرد ⇒ `middleware/json-body.js` بی reviver، همان رفتارِ B4؛ SR38-3 (Medium) `openSnapshot` با `PG_IDLE_TX_TIMEOUT_MS` و dump ِ بلند
  سرور را با uncaughtException می‌خواباند ⇒ شنونده + `release(err)` و شمارِ seal از استخر. سطلِ مدیر، پاکتِ رازها، نمای سفارش، صدا، سبدِ حساب، کدِ خطا و مهلت سالم ماندند.
- نیمه‌کاره: هیچ. قدمِ بعدی: پیش از ارتقای PostgreSQL ِ تولید به ۱۸، `backup-secrets.test.js` روی همان pg_dump (R1)؛ محدود کردنِ نامِ `unseal` به `SECRET_KEYS` (R2، لایهٔ دوم).
### ۱۴۰۵/۰۸/۱۹ — شاخهٔ `claude/store-allflags` (ویترین با همهٔ پرچم‌های مشتری‌رو روشن، سرتاسر در مرورگر؛ روی `ccr-3a0d593c-ac0jpj-batch-37`؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: (۱) `server/test/web-store-allflags.mjs` (در `test:web-store`): ۵۲ پرچمِ خانوادهٔ ویترین/گفت‌وگو/سفارش + catalog/image_bank/customer_notify/
  delivery_customer_view از `KNOWN`، با پیش‌نیازِ shop.delivery، shop.visual_search و pay.hesabpay؛ سفرِ کامل در ۳۶۰·fa، ۷۶۸·en، ۱۲۸۰·ps و همان صفحه‌ها در
  شش ترکیبِ دیگر؛ هر صفحه axe ِ WCAG 2.1 A/AA پس از گذارها، بیرون‌زدگی، JS/CSP، ۴xx/۵xx و بارِ نخست؛ سنجه‌ها خودشان هم سنجیده می‌شوند.
  محلی پنج بارِ پیاپی سبز (چهار PGlite + یک PostgreSQL ِ محلی؛ ۱۴۷/۱۴۷ صفحه، ۳۳۴/۳۳۴ سنجه، ~۳ دقیقه). (۲) چهار رفع با آزمونِ پیش‌سرخ: ۳۹ پرچمِ خوانده‌شده بیرون از `KNOWN`
  (`console-shell.test.js`)؛ پاسخِ pay-choice/افزودن/ویرایش/لغو فقط `sf.track` بود و کارتِ گفت‌وگو، دستورِ تحویل، زمان و روش را می‌برد
  (`lib/store-order-view.js`؛ order-flow، order-add-items، order-amend، storefront) — **برهم‌کنشِ پرچم‌ها**؛ ۴۰۴ ِ `…/rating` بی
  `shop.delivery_ratings` (کلیدِ `deliveryRating`، delivery-ratings)؛ ردیفِ `#store-meta` ِ گوشی بی دسترسیِ صفحه‌کلید (web-store.mjs، axe).
  (۳) سرتاسری با `E2E_FLAGS_ON` (۵۱ پرچمِ خانواده + shop.delivery): هفت گامِ سرخ بی ایرادِ محصول ⇒ گزینندهٔ «+» جدا از ♥، `sameOrderLink`
  (TEN-03)، «بی پرچمِ نمای پیک» صریح و سه مقدارِ شمارشیِ عمومی؛ customer/order/track/notify/delivery/courier/lifecycle ۶۶/۶۶ با و بی آن.
- نیمه‌کاره: شاهدِ CI ِ `web-store-allflags.mjs` (شغلِ ۴۵ دقیقه‌ایِ «آزمونِ ویترین» ~۳ دقیقه بیشتر). خانوادهٔ `shop.delivery_*` و پرچم‌های دیگرِ
  بیرون از کاتالوگ (shop.visual_search، pay.hesabpay، shop.promo_uses، shop.owner_report، …) هنوز در `KNOWN` نیستند. `shop.orders.ledger` بازنشسته
  است ولی هنوز در `order-ledger.js` خوانده می‌شود. `E2E_FLAGS_ON=all` حالا `shop.orders.subscribers_only` را هم روشن می‌کند (دکانِ بی اشتراک ⇒ بی سفارش).
- قدمِ بعدی: شاهدِ CI؛ تصمیمِ صاحب‌کار دربارهٔ subscribers_only در «همه روشن»؛ کاتالوگِ پیک و پرداخت با همان سنجهٔ `console-shell.test.js`.
### ۱۴۰۵/۰۸/۱۹ — شاخهٔ `claude/backup-secrets` (F0-28: رازهای app_config در پشتیبانِ سرور؛ روی `ccr-3a0d593c-ac0jpj-batch-37`؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: `server/src/lib/backup-secrets.js` پشتِ `BACKUP_SECRETS=seal` (پیش‌فرض plain = همان امروز، بی هیچ تبدیل): هفت رازِ app_config
  (کلیدِ مجوز، vapid، رمز و کلیدِ ایمیل، کلیدِ پیامک، حسابِ سرویسِ پوش، `station_read_key`) در خودِ پشتیبان `sealed:<نام>` با AES-256-GCM و
  `BACKUP_SECRETS_KEY[_FILE]`؛ pg_dump با تبدیلِ جریانیِ فقط بلوکِ `COPY public.app_config` (و سنجشِ شمار در همان snapshot)، PGlite با رونوشت +
  `VACUUM FULL` + چرخاندنِ WAL و سنجشِ نشانیِ هر صفحهٔ WAL (بی آن مقدارِ خام در WAL می‌ماند — با جهشِ آزمون دیده شد). باز شدن (restore،
  تمرین، راه‌اندازی پیش از نخستین درخواست) همه یا هیچ؛ بی کلید ۵۰۳ ِ روشن و هیچ کلیدِ تازه (`license.js`، و ردیفِ خالیِ `station_read_key`؛
  کدِ پمپ دست نخورد). seal بی کلید یا حالتِ ناشناخته ⇒ ۴۰۹ و هیچ فایل. `omit` عمداً ساخته نشد (کلیدِ پمپ و vapid برنمی‌گشتند ⇒ قفلِ کارمندان).
- آزمون: `test/backup-secrets.test.js` ⇒ PGlite ۱۱/۱۱ (+۱ فقط PostgreSQL)، PostgreSQL ۱۶ ۱۲/۱۲؛ خانوادهٔ پشتیبان/مجوز/امنیت سبز (گزارشِ شاخه).
- نیمه‌کاره: CI ندیده (F0-28 = PARTIAL)؛ روشن کردن با صاحب محصول (OWNER-ACTIONS-fa.md §۳ گامِ ۸). قدمِ بعدی: پس از CI ِ سبز، روی سرورِ
  خانگی گامِ ۸ و یک `npm run restore-drill` (باید «کلیدِ مجوز: همان کلیدِ پیشین» بگوید)؛ نمایشِ «رازِ مهر مانده» در «سلامت» ِ پنل هنوز نیست.
### ۱۴۰۵/۰۸/۱۹ — شاخهٔ `claude/ios-keychain` (سه آزمونِ سرخِ کارِ `ios-app`؛ روی `ccr-3a0d593c-ac0jpj-batch-37`؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- ریشه (از لاگِ کارهای 128869959125 تا 129427623969): `test.kexe` کلیددانِ iOS ندارد (OSStatus -25291، همان که کاوشِ آزمونِ سرتاسری
  در همان فرآیند می‌نویسد). `DeviceIdentityIosTest` در `keepingRealState` (`Keychain.read`، سطرِ ۱۰۷) KeychainException می‌داد؛
  `DataSafetyIosTest.signOutVault…`: KEK ِ `AtRestKeysIos` ⇐ `KEY_UNAVAILABLE` ⇐ `SignOutVault.seal` = false (سطرِ ۱۹۱). نخستین سرخ: اجرای
  ‎#716‎ (دستهٔ ۳۱، ‏b9cfbc736)؛ پیشینِ سبز ‎#713‎ (دستهٔ ۳۰، ‏83c283fa6) این دو کلاس را نداشت (db573a6bc و 5c019a53f؛ اجرای خودِ شاخه‌ها لغو شده بود).
- انجام شد: هر دو آزمون روی `Keychain.memory` (برگردانده پس از خود؛ کلیددانِ دفتری که آزمونِ گاوصندوق ساخت برداشته می‌شود)؛ ردهٔ
  «فقط همین دستگاه» با `Keychain.addRequestIs` (همان واژه‌نامهٔ `SecItemAdd` ِ `add`، `addQuery` ِ تازه) به‌جای `storedAs`. هیچ assertی کم نشد.
- نیمه‌کاره / قدمِ بعد: فقط CI ِ macOS (کارِ `ios-app`) سه آزمون را می‌سنجد؛ کامپایل را کارِ `ios-ui-klib` ِ لینوکس. کلیددانِ واقعی در
  `test.kexe` (امضای ad-hoc با entitlements ِ DER، `__ents_der`) آزموده نشده — اگر خواستند، آزمونی جدا و گزارشی.
### ۱۴۰۵/۰۸/۱۹ — شاخهٔ `claude/perf-load` (PERF-02: نخستین اجرای واقعیِ ۲۰۰ دکان × ۵ دستگاه و درستی زیرِ بار؛ روی `ccr-3a0d593c-ac0jpj-batch-37`؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: (۱) آمیزهٔ `scale` و `scale-burst` ِ ابزارِ بار (`server/scripts/load-scale.mjs`: فروش، ویرایشِ بها از چند دستگاه، حرکت، مشتری،
  نگه‌داشته، ویترین روی زیرمجموعهٔ دکان‌ها، اشتراکِ صاحب، پنلِ مدیر، کاوشِ ویترینِ دکانِ دیگر) با دفترِ درستی و بازفرستِ op ِ بی‌پاسخ؛
  سنجهٔ `server/scripts/load-check.js` (۱۲ سنجه، درونِ سرورِ آزمون یا جدا)؛ `--query-stats` به‌جای `pg_stat_statements`. (۲) سه اجرای ۲۰۰ × ۵
  روی PostgreSQL ِ محلی (۱۰ دقیقه پایدار + «قطعِ سراسری»؛ عددها و بارِ ماشین در `docs/PERF-LOAD-fa.md` «۱۴۰۵/۰۸/۱۹ (دوم)»): پایدار هر بار
  ۲۳٬۹۹۹ درخواست و ۰ خطا؛ داده بی گم/دوتایی و دکان‌ها جدا. (۳) رفع: مهاجرتِ ۴۵۰ (نمایهٔ جزئیِ وظیفهٔ بازِ تکراری‌نشو؛ آزمونِ
  `sync-v3-task-dedupe.test.js` پیش از رفع سرخ). (۴) آزمونِ پایدارِ `load-correctness.test.js` (بی سنجهٔ زمانی، ~۱ دقیقه، ۳/۳ روی هر دو).
  (۵) کاشتنِ ابزارِ بار دیگر درِ پمپ (`/api/admin/pump/live-config`) را نمی‌زند؛ `/api/admin/flags`.
- نیمه‌کاره: هیچ کدِ نیمه‌کاره. PERF-02 همان PARTIAL (شاهدِ CI نیست). یافتهٔ بی‌ربط: `upgrade-rehearsal.test.js` روی PGlite ِ ماشینِ شلوغ گاهی
  `fetch failed` (ECONNRESET ِ اتصالِ keep-alive ِ کهنه) می‌گیرد؛ در CI سبز؛ دست نخورد.
- قدمِ بعدی (صاحب محصول): روشن کردنِ `SYNC_V3_BATCH` و `HOT_CACHE_MS` در تولید (اجرای سوم همین سند)؛ بودجهٔ ورود؛ رفتارِ «قطعِ سراسری»
  (مهلتِ کلاینتِ ۱۰ ثانیه در برابرِ ۸ ثانیه انتظارِ دروازه، `REQUEST_TIMEOUT_MS`/`PG_POOL_MAX_WAITING`)؛ همین اجرا روی سخت‌افزارِ جدا.
### ۱۴۰۵/۰۸/۱۹ — شاخهٔ `claude/flake-2` (دنبالهٔ شکارِ لرزش؛ روی `claude/flake-hunt` ‏b05891a2؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد (هر ردیف: زمانِ به‌زور کدِ پیشین را با همان خطای CI سرخ کرد؛ کدِ تازه زیرِ همان زور بارها سبز):
  (۱) Kotlin: `testLogging` (`exceptionFormat = FULL`، علت، stack) در `android-native/app`، `core`، `desktop`. `ResumableDownloadServerTest:113`
  (run 41645079134، فقط «AssertionError»): سرورِ آزمایشی `sent` را پس از flush می‌شمرد و پاسخِ کامل برای کلاینت بی صبر برای آن ریسه تمام است ⇒
  شمردن پیش از نوشتن. بازسازیِ JDK ِ همان سرور/کلاینت: پیشین بی زور ۳۸ از ۲۰۰ و با ۵ms مکث ۴۸ از ۵۰ سرخ (`sent` = ۲٬۸۰۰٬۰۰۰)، تازه ۰ از ۴۰۰.
  (۲) `hesabpay-account.test.js`: «9999» به `device.trustedAfter` ِ خودِ «ب» می‌خورد ⇒ آن عدد با مقدارِ دقیق (session_at + TRUST_MS)، جاهای
  کلید/حساب تهی، بقیهٔ متن بی کلید/راز/وب‌هوک/شناسهٔ «الف». ساعتِ به‌زور: پیشین ۲ از ۲ سرخ، تازه ۱۰ از ۱۰؛ کلِ فایل PGlite و PG سبز.
  `push-fcm.test.js` را `claude/sec-left` درست کرده (دست نخورد).
  (۳) زیرِ ۹۰۰ms ِ GET سه باگِ واقعی: `license/vip.js` (ورود به پرسشِ کندِ مهمان می‌پیوست ⇒ صندوق تا بارِ دوباره قفل؛ پیشین ۳ از ۳ سرخ)،
  `admin/app.js` (۴۰۱ ِ GET ِ دیرِ نشستِ پیشین نشستِ تازه را می‌بست ⇒ گامِ ۲FA) و `admin/image-bank.js` (بارهای هم‌پوشان ⇒ ردیفِ دوتایی)؛
  پنل پیشین ۱ از ۲ سرخ؛ تازه vip ۷ و پنل ۶ اجرا (بی زور، زیرِ تأخیر، PG) سبز. گام‌های نخست-سرخ همان درخواست را نگه می‌دارند (با کدِ پیشین سرخ). `web-sync-ux.mjs`: تأخیر فقط ۵ GET ِ سندِ پیش از
  `reload` را کند می‌کند (route ِ صفحه جلوتر است) ⇒ سرخِ پیشین زادهٔ بارِ میزبان؛ ۷ اجرا زیرِ تأخیر، CPU×6 و هر دو سبز.
  (۴) `upgrade-rehearsal`: مکثِ همگامِ ۵٫۵s پیش از `/api/ready` ⇒ پیشین ۲ از ۲ «fetch failed ← ECONNRESET»؛ `Connection: close` در `http()`،
  تازه ۵ از ۵ (و PG)؛ آزمونِ تازه: هر درخواست سوکتِ تازه و بستنِ واقعیِ اتصال هنوز خطاست.
  (۵) `web-store-chat.mjs` reverify (CI ِ #1022: run 42716236303 و 42721354292): تایمرِ ۸۰ms ِ `verify()` میانِ fill ِ کد ⇒ کد در کادرِ
  شماره ⇒ `verify/check` ۴۰۰؛ تایمرِ ۶۰ms ِ `K.openSheet` هم. `latefill`: پیشین همان ۴۰۰ و همان Timeout (322/341) با لاگِ «phone = شماره+کد، code تهی ⇒ bad_request»، تازه همه سبز (PGlite و PG)؛
  گامِ `refocus` با هر کدام از کدهای پیشین سرخ. تشخیصِ batch-38 همان شد (ادغامِ بی‌برخورد)؛ از 303629cd9 در CI دیگر reverify نیفتاده
  (42723742564 در web-store-acct افتاد، 42725165760 سبز) ⇒ خطِ «reverify … sent» هنوز نیامده.
- نیمه‌کاره: آزمون‌های Kotlin (`ResumableDownloadServerTest` و گزارشِ FULL) فقط در CI سنجیده می‌شوند.
- قدمِ بعدی: لرزشِ بعدیِ Kotlin پیام و stack دارد — از همان شروع شود. ابزارهای زور (`clock9999`، `stallfetch`، `latefill`، `slownet-log`،
  probe ِ ناوبری/نقشِ برگه) در scratchpad ِ همین سیزن‌اند، نه در مخزن.

### ۱۴۰۵/۰۸/۱۹ — شاخهٔ `claude/flake-hunt` (شکارِ آزمون‌های لرزان از CI ِ ۸ روزِ اخیر؛ روی main ‏98dece3a = دستهٔ ۳۷، #1021؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: (۱) ۷۶۷ اجرای کامل‌شدهٔ بی‌لغو (سرور، توحید بومی، کامپیوتر و…؛ ۱۱/۰۱ تا ۱۱/۰۹) و همهٔ تلاش‌هایشان، به‌علاوهٔ کارهای سرخِ اجراهای
  لغوشده جدا: هر لرزشِ دیده‌شده بیرون از Kotlin پیش‌تر ریشه‌دار رفع شده بود (ترتیبِ سابقهٔ IMG-PRO-17، بلیتِ «دست‌خورده»، ۳۷۰۳۵ ِ مُهرِ زمان،
  انقضای سوکت، بذرِ هم‌زمانِ پلن، کلیکِ ناحیه، صبرِ کاتالوگ، سه موردِ دستهٔ ۳۷)؛ باقی قطعی بودند یا زیرساخت (ubuntu-26.04). (۲) همان روشِ
  «زمان را به زور» روی همزادهای همان رده‌ها: پخشِ کندِ انیمیشن (CDP) ⇒ axe ِ نیمه‌راه در `web-debtors-ledger`، `web-orders`، `web-report-fresh`،
  `web-ui` ⇒ `animationsSettled` ِ `test/browser-guard.mjs`؛ ۹۰۰ms تأخیرِ GET ⇒ چهار گامِ `admin-panel.mjs` (کد، صافیِ پشتیبانی، فروش، سلامت)
  خوابِ ثابت داشتند ⇒ صبر برای پاسخِ تازه؛ دیرکردِ `onload` ⇒ شمارشِ RSK-03 ِ `web-orders` فقط برای همان سفارش. (۳) باگِ واقعی: رسیدِ
  CHAT-04 که میانِ دو long-poll می‌نشست تا ۲۵ ثانیه نمی‌رسید ⇒ `pd`/`pr` ِ `messages?wait` (`chat-wait.js`، هر دو درِ چت، `store/chat.js`)؛
  آزمونِ نخست-سرخِ `shop-chat.test.js` (کدِ پیشین ۳۰۴۳ms منتظر ماند). (۴) `web-store-chat.mjs`: long-poll ِ دکان برای سنگِ قبر با هر تغییرِ
  گفت‌وگو تهی برمی‌گشت (یک بار سرخ روی PostgreSQL ِ پربارِ محلی) ⇒ پرسشِ دوباره از cursor تا رسیدنِ همان؛ با رسیدِ تزریق‌شده کدِ پیشین سرخ، تازه سبز.
- نیمه‌کاره: `ResumableDownloadServerTest` (Kotlin، یک بار در `claude/gaps15-partial`، همان sha سبز) — لاگِ Gradle فقط «AssertionError at :113»
  دارد، ریشه قطعی نیست، دست نخورد. iOS (`DataSafetyIosTest`/`DeviceIdentityIosTest`، کارِ «گزارش») قطعی سرخ است: `KeychainException` روی شبیه‌ساز.
- قدمِ بعدی: `testLogging.exceptionFormat = FULL` برای Gradle تا لرزشِ بعدیِ Kotlin پیام داشته باشد (تصمیمِ صاحب‌کار)؛ ابزارهای بازتولید
  (کندیِ انیمیشن، تأخیرِ GET، دیرکردِ onload) در گزارشِ همین شاخه‌اند، نه در مخزن.

### ۱۴۰۵/۰۸/۱۹ — شاخهٔ `claude/shopper-cart` (CHK-03 در برنامهٔ خرید: سبدِ حساب؛ روی `ccr-3a0d593c-ac0jpj-batch-36` = #1020؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: (۱) core: ماشینِ کوچکِ `CartSync` (`ShopperCartSync.kt`) زیرِ گام‌های سبدِ `AccountRules`: ادغامِ سبدِ مهمان یک بار و فقط اگر هست
  (پاسخش همان خواندن)، آینهٔ نشستِ پیشین ادغام نمی‌شود؛ ۴۰۹ ⇒ همان تغییرِ دستگاه روی سبدِ تازه (`rebase`، یک بار پشتِ‌هم، سپس درنگ) به‌جای
  جای‌گزینیِ کل که کارِ دستگاهِ دیگر را گم می‌کرد؛ تغییرِ میانهٔ ادغام/نوشتن هم می‌ماند؛ بی اینترنت/۵xx/۴۲۹ ⇒ درنگِ ۲ تا ۱۲۰ ثانیه
  (`Retry-After`)؛ ۴۰۱ ِ بی `session_required` (پرچمِ خاموش) ⇒ فقط سبد خاموش، بی خروج؛ قیمت/موجودی/سقف از سرور با شمارِ پیام (`cartFresh`)؛
  خروج/۴۰۱/نشستِ سرآمده ⇒ سبدِ حساب از گوشی پاک. (۲) اندروید (`ModelAccount.kt`): سکوتِ ۸۰۰ms پیش از نوشتن و بیدار شدن پس از درنگ
  (`CustomerModel.later`)، پیامِ قیمت/«دیگر نیست» فقط روی سبد/ثبت. (۳) بردارِ مشترکِ `core/test-vectors/account-cart-v1.json` (`storefront-cart.test.js`
  ۷/۷ روی PGlite و PostgreSQL ِ محلی، سه جهش ⇒ سرخ) و `CartServer` ِ آزمونِ Kotlin که `ShopperCartVectorsTest` با همان بردار نگه می‌دارد.
- نیمه‌کاره: Kotlin این‌جا ساخته نمی‌شود ⇒ `ShopperCartSyncTest`، `ShopperCartVectorsTest`، `ShopperAccountTest` (یک بندِ عمداً عوض‌شده: ۴۰۹ دیگر
  روغنِ دستگاهِ دیگر را پاک نمی‌کند) و `CustomerAccountTest` فقط در CI. وب (`acct.js`) هنوز «دستگاه برنده» است (سیزنِ دیگر روی ویترین کار دارد).
- قدمِ بعدی: شاهدِ CI ِ آزمون‌های Kotlin ⇒ بازنگریِ متنِ CHK-03؛ هم‌قاعده کردنِ ۴۰۹ ِ `acct.js` با همان `rebase` و بردار؛ D-09 برای ورود در تولید.
### ۱۴۰۵/۰۸/۱۸ — شاخهٔ `claude/acct-bucket` (سطلِ نرخِ جدای پلِ پنل و مدیر؛ روی main ‏0e2e7b36 = دستهٔ ۳۶؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: ریشهٔ «سقفِ نرخِ سرورِ حساب پر شده» (۱۴۰۵/۰۷/۱۰): پلِ پنل از 127.0.0.1 و بی XFF همان سطلِ عمومیِ ۶۰۰ ِ یک IP را
  می‌خورد. حالا `middleware/admin-bucket.js` (پیچیده دورِ همان `general` ِ `app.js`): فقط `/api[/v1]/admin/*` (نه login/reauth/2fa/pump)
  با کلیدِ سرویسِ فعال یا نشستِ زندهٔ مدیرِ فعال ⇒ سطلِ همان کلید/مدیر (`RATE_ADMIN_PROVEN_MAX` ۶٬۰۰۰)؛ هر چه اثبات نشد همان سطلِ IP.
  پرسشِ پایگاه برای اعتبارنامهٔ ناشناخته سطلِ IP ِ خودش (`RATE_ADMIN_PROBE_MAX` ۶۰، فقط شکست‌ها)؛ `requireAdmin` شکست ⇒ `forget`.
  پیش‌فرض روشن، `RATE_ADMIN_BUCKET=0` ⇒ رفتارِ پیشین. `admin-bucket.test.js` (۱۲ آزمون؛ روی کدِ پیشین ۵ سرخ، هشت جهش سرخ)، PGlite و
  PostgreSQL ِ محلی، هر دو انبار (حافظه و pg).
- نیمه‌کاره: هیچ. ماندهٔ آگاهانه: سقف‌های تنگِ هر درِ مدیر (`admin-census`، `admin-notify-usage-ip`، آزمونِ پیامک/ایمیل…) هنوز به‌ازای
  IP‌اند و برای پل همه 127.0.0.1 — عمداً دست نخورد. بی `TRUST_PROXY=true` همهٔ ترافیکِ درگاه یک IP است (همان قاعدهٔ پیشین).
- قدمِ بعدی: شاهدِ CI ِ `admin-bucket.test.js`؛ اگر پل هنوز با رمزِ ارشد می‌آید، کلیدِ سرویس (بندِ ۱۲.۲ ِ ADMIN-CONTROL-PLANE) — هر دو راه سطلِ خودشان را دارند.
### ۱۴۰۵/۰۸/۱۸ — شاخهٔ `claude/mkt-1` (ردیف‌های MKT ِ بازِ بی‌مانع؛ روی `ccr-3a0d593c-ac0jpj-batch-35` = #1019؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: MKT-06 در نسخهٔ وبِ دکان — کادرِ «چرخهٔ مصرف (روز)» در ویرایشِ کالا (`license/product-cycle.js`، بی هیچ خطی در index.html)،
  بردارِ مشترکِ `license/test-vectors/product-cycle-v1.json` با `ProductCycleVectorsTest` (Kotlin، فقط CI)، `web-product-cycle.test.js` ۸/۸ و
  `web-product-cycle.mjs` ۱۰/۱۰ (PGlite و PostgreSQL ِ محلی؛ از صفحه تا `?ids=` ِ ویترین؛ بی بارکننده و سه جهش سرخ).
- نیمه‌کاره: هیچ کدِ نیمه‌کاره. بقیهٔ MKT بسته به بیرون/تصمیم: MKT-02 (برنامهٔ خرید)، MKT-03/04/05/07/08 (O-01، D-09، رضایت و
  قاعدهٔ پاداش = تصمیمِ صاحب‌کار)، MKT-10 (چاپگرِ واقعی)، MKT-12 (D-09).
- قدمِ بعدی: شاهدِ CI ِ `web-product-cycle.mjs` (گامِ «آزمونِ نسخهٔ وب») و `ProductCycleVectorsTest` ⇒ بازنگریِ متنِ MKT-06.
### ۱۴۰۵/۰۸/۱۹ — شاخهٔ `claude/shopper-app` (برنامهٔ خرید و ویترین: «فهرستِ خرید»، ♥ و سبد همراهِ حساب؛ روی `ccr-3a0d593c-ac0jpj-batch-35`؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: (۱) سرور: «فهرستِ خرید» ِ حساب (`lib/store-shopping-list.js`، مهاجرتِ ۴۳۲ + rollback؛ `GET/PUT /api/store/:slug/account/shopping-list` و
  `POST …/merge`، پرچمِ ♥، سطلِ `store-acct-sync` پیش از نشست): نسخهٔ دیده‌شده (۴۰۹)، همان محتوا ⇒ تکرارِ بی‌اثر، ادغامِ بیشینه، ۱۰۰ قلم و
  ۵۰ فهرستِ پُر برای هر مشتری، پنهان = نبوده (بی شمارش)؛ حذفِ دادهٔ مشتری (SEC-10) و privacy.html هم. ممیزیِ مهاجم: IDOR (مشتری/دکانِ دیگر،
  شناسهٔ بدنه)، تکرار، شمارش، نشستِ ساختگی، ویترینِ بسته و هم‌زمانی (PostgreSQL ِ محلی) ⇒ `storefront-shopping-list.test.js`؛ ثبت در
  `role-route-isolation` و `routes.json` (SUB-10، route-authz و tenant-matrix از روی سورس خودکار، اجرا شدند). (۲) وب: `catalog.js` ِ دیربار
  (بیرونِ ۷۱KB) فهرست را مثلِ ♥ با حساب هم‌گام می‌کند (صفِ `store-listsync:`). (۳) برنامهٔ خرید: `AccountRules` (core) با همان درها و ادغام برای
  سبد، ♥ و فهرست؛ ورود با کدِ واقعیِ سرور (`ModelAccount.kt`)، نشست فقط در Keystore (`SecretsAndroid.kt`)؛ ♥ در برگهٔ کالا، «دلخواه‌ها» در دکان،
  «فهرستِ خرید» و «حسابِ من» زیرِ سبد، خروج در تنظیمات. (۴) STATUS: متنِ «مانده» ِ WEB-13، CHK-14، MKT-02 و WEB-11 بی ارتقا.
- نیمه‌کاره: Kotlin این‌جا ساخته نمی‌شود ⇒ `ShopperAccountTest` (core) و `CustomerAccountTest` (اندروید) فقط در CI؛ آیفون نشست را فقط در حافظه
  نگه می‌دارد (Keychain نیست). پیامکِ واقعیِ تولید همان D-09 است (بی آن ورود در تولید «تأیید راه‌اندازی نشده» می‌گوید).
- قدمِ بعدی: شاهدِ CI ِ این‌ها و `web-store-web.mjs`؛ Keychain ِ آیفون؛ «کالاهای دیده‌شده» و انتخابِ اسکناس در برنامهٔ خرید (WEB-13، CHK-14).
### ۱۴۰۵/۰۸/۱۹ — شاخهٔ `claude/p0-syn` (ردیف‌های P0 ِ SEC-03، SYN-01/04/05/06 و OPS-02، بخشِ سرور/JS؛ روی main ‏37f8fa4c = دستهٔ ۳۵، #1019؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: (۱) SEC-03 پشتِ `shop.doc_chain`: هر لغوی که S89 می‌نشاند (v1 ِ زنجیره‌دار و بی زنجیره، وب، راهِ قدیم، API) در همان
  تراکنش حلقهٔ `hash_chain` ِ `void/<دکان>` می‌شود، یک بار برای هر فاکتور (نمایهٔ `sale_void_index`، **مهاجرتِ ۳۲۶**)، بی متنِ دلیل،
  مبلغ با متنِ عددِ JS؛ شکستِ حلقه لغو را نمی‌بندد (SAVEPOINT ⇒ `void_unrecorded`)؛ بازگرداندنِ تعارضِ مدیرِ ارشد روی فیلدِ مالی/وضعیتِ
  فاکتورِ ثبت‌شده ۴۰۹ (`adminRestoreCheck`). (۲) SYN-04: push ِ فشرده — پرچمِ خاموشِ `sync.gzip_push` در pull، وب با `CompressionStream`
  بالای ۱KB، پاسخِ تمیزِ بمب/gzip ِ خراب/کدگذاریِ ناشناخته، CORS. (۳) OPS-02: `server/scripts/phrase-backup.js` (رمز و بازکردنِ پشتیبانِ
  دستی با عبارت، همان پاکتِ برنامه با بردارِ Python). (۴) شاهدِ PostgreSQL ِ محلی برای آزمون‌های SYN (پیش‌تر فقط PGlite).
- نیمه‌کاره: هیچ کدِ نیمه‌کاره. کاتلین دست نخورد (push ِ فشرده و رمزِ پشتیبانِ درونِ برنامه هنوز نه). SYN-05/06: کارِ بازِ سرور/JS
  نماند؛ مانده‌ها پرچم و v3 (تصمیمِ صاحب‌کار) و «۵-ب» در `lib/sync-v1.js` ِ مشترک با پمپ.
- قدمِ بعدی: شاهدِ CI ِ `sale-void-chain`، `sync-gzip-push` و `phrase-backup-tool`؛ کاتلین: `GZIPOutputStream` برای push پس از
  `gzip_push`، و «پشتیبان روی USB با عبارت» در برنامه با همان پاکت.
### ۱۴۰۵/۰۸/۱۹ — شاخهٔ `claude/srv-authority` (اختیارِ سرور: STF-03 تخفیف/امتیاز، پیگیری‌های CRM-03، مجوزِ «تغییرِ سقفِ اعتبار»؛ آغاز روی `ccr-3a0d593c-ac0jpj-batch-35`، سپس روی main پس از #1019 (همان درخت)؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد (همه پشتِ پرچمِ خاموش، بی مسیرِ تازه، بی مهاجرت، ⛔ `sync-v1.js` دست نخورد): (۱) **STF-03** `lib/promo-loyalty-review.js` (`shop.promo_loyalty_review`): `promo` ِ قلم با `promo-core.evaluate` و قاعده‌های دکان، `loyalty_spend` با `Loyalty.spend` (همتای JS ِ تازه در `loyalty-balance.js` + بردارِ `core/test-vectors/loyalty-spend-v1.json` + `LoyaltySpendVectorsTest.kt`) ⇒ فقط سابقهٔ `sale.promo_review` (یک بار، هرگز رد، شکستش push را نمی‌خواباند). (۲) **CRM-03** بررسیِ سقفِ اعتبار حالا دلتای `debtGivenMinor` ِ v3، ویرایشِ قرضِ v1 و تراکنشِ دیررسیده را هم می‌بیند. (۳) مجوزِ هسته‌ایِ `customers.credit_limit` (§۱۰.۱؛ JS/Kotlin/`perm-v1.json`) و اجرا در v1، v3 و REST پشتِ `shop.credit_limit_perm` (`lib/credit-limit-perm.js`). آزمون‌ها روی PGlite و PostgreSQL ِ محلی.
- نیمه‌کاره: برنامه مجوزِ تازه را پیش از ذخیره نمی‌پرسد و برچسبِ فارسی ندارد (صفحهٔ کارمند/مشتری، بیرون از این کار)؛ Kotlin (`PermCore`، `LoyaltySpendVectorsTest`) فقط در CI.
- تصمیمِ صاحب‌کار: STF-03/CRM-03 «رد یا بررسی»؛ پیش‌فرضِ `customers.credit_limit` برای شاگرد/صندوق‌دار (امروز «مجاز» = رفتارِ امروز).
- قدمِ بعدی: شاهدِ CI برای پنج آزمونِ تازه/گسترده و `PermCoreTest`/`LoyaltySpendVectorsTest`؛ سپس پرسیدنِ مجوز در فرمِ مشتریِ برنامه.
### ۱۴۰۵/۰۸/۱۹ — شاخهٔ `claude/chat-web` (گفت‌وگوی ویترین: CHAT-02 و CHAT-14 در وب، `editUntil`، شاهدِ CI ِ CHAT-04/08؛ آغاز روی `ccr-3a0d593c-ac0jpj-batch-35`، سپس روی main ‏37f8fa4c پس از #1019 با همان درخت؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: (۱) سرور: `editUntil` ِ ساعتِ سرور (`editWindowMs`) فقط روی پیامِ زندهٔ خودِ مشتری، کنارِ `deleteUntil` (`lib/shop-chat.js` `messageView`، بی خطِ تازه). `shop-chat-web-acts.test.js` (۶ آزمون از راهِ درهای ویترین: editUntil، `not_author`/`edit_window_passed`، ۴۰۴ ِ گفت‌وگوی دیگری، `bad_reply`، جست‌وجوی محدود، سنجاقِ دیگری ۴۰۴ و سنجاقِ بی متن). (۲) CHAT-02 در وب: `store/chat-out.js` (منطقِ خالصِ صف، همزادِ `ChatOutbox`؛ `web-chat-out.test.js`) و سیم‌کشی در `store/chat.js`: حبابِ «در صف» با «دوباره»/«لغو»، localStorage ِ `store-chat-out`، از سر گرفتن با گشودنِ دوباره، پاک شدن با شمارهٔ دیگر، خواندن⇐تغییر⇐نوشتن میانِ برگه‌ها. (۳) CHAT-14 در وب: `store/chat-acts.js` (پاسخ، ویرایش تا `editUntil`، سنجاق، جست‌وجو) با واژه‌نامه‌های جدای `chatq`/`chatacts` (بخشِ `chat` ِ ۴۰۵۳ بایتی جا نداشت). همه دیربار، بارِ نخست دست‌نخورده. (۴) CHAT-04 و CHAT-08 ⇐ IMPLEMENTED-VERIFIED با شاهدِ دیده‌شدهٔ CI ِ main bd6cb560 (شرح در STATUS).
- دو گامِ `web-store-chat.mjs` عمداً عوض شد: بی‌اینترنت متن دیگر در کادر نمی‌ماند (به حبابِ «در صف» می‌رود)؛ «زدنِ دوباره» پس از جوابِ گم‌شده حالا «دوباره» ِ همان حباب است (و همان `clientId` در دو POST سنجیده می‌شود).
- نیمه‌کاره: CHAT-07 (ضبطِ صدا در وب) انجام نشد. CHAT-02 و CHAT-14 PARTIAL می‌مانند تا شاهدِ CI ِ همین شاخه.
- قدمِ بعدی: پس از CI ِ دسته: `web-store-chat.mjs` و `web-chat-out`/`shop-chat-web-acts` ⇒ بازنگریِ CHAT-02 و CHAT-14؛ سپس CHAT-07 ِ وب.
### ۱۴۰۵/۰۸/۱۹ — شاخهٔ `claude/ops-perf` (ایمنیِ ساعت برای اشتراکِ دکان؛ ردیف‌های PARTIAL ِ OPS و PERF؛ روی main ‏37f8fa4c + `claude/sub-ten-rsk`؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: (۱) **ساعتِ سرور** (فقط دکان، مهاجرتِ ۳۳۳؛ پمپ بیت‌به‌بیت همان): جهشِ رو به جلو `expired` ِ همیشگی می‌نوشت ⇒ حالا با شاهدِ
  `auto_expired_at` برمی‌گردد (همان دم در `stateOf`، ستون در خانه‌تکانیِ بعدی زیرِ قفلِ اعطا)؛ اعطای «همین لحظه» با عقب رفتنِ ساعت
  «شروع‌نشده» نمی‌شود (`updated_at ≥ starts_at`). `entitlement-clock.test.js` ۵/۵ با ساعتِ ساختگی (پیش از رفع ۳ سرخ). خانه‌تکانیِ
  `GET /api/me/subscription` روی نمایه: p95 ‏۴٫۷۱ ⇐ ۱٫۰۰ms (۶۰ هزار ردیف). سرریزِ «۳۱ ژانویه + ۱ ماه = ۳ مارس» دست نخورد؛ برای صاحب
  محصول در `docs/SUB-STATES-fa.md` نوشته شد. (۲) OPS-01@29: شمارِ شکستِ تطبیق در پایگاه (راه‌اندازیِ دوباره/نمونهٔ دیگر صفرش نمی‌کند).
  (۳) OPS-09: هزینهٔ پیامکِ کدها (`codes`) در گزارشِ «مصرف و هزینهٔ پیام». (۴) PERF-04: `REQUEST_TIMEOUT_MS`، `PG_STATEMENT_TIMEOUT_MS`،
  `PG_IDLE_TX_TIMEOUT_MS` (همه پیش‌فرض خاموش) و رفعِ اتصالِ افتاده در `tx()`؛ overload پس از دروازه: ۲۲۶ مهلتِ کنترل‌نشده ⇐ ۰ با مهلت‌ها.
- نیمه‌کاره: هیچ کدِ نیمه‌کاره. ردیف‌ها PARTIAL ماندند (شاهدِ CI نیست). OPS-01/OPS-02/OPS-02@29/OPS-03@29/OPS-06 بیرونی، تصمیم یا Kotlin‌اند.
  یافتهٔ پمپ (دست‌نخورده): `app.use(<regex>, express.json({limit:'34mb'}))` ِ گزارشِ پمپ در Express 5 بدنه را نمی‌خواند ⇒ سقفِ ۲MB ِ همگانی.
- قدمِ بعدی (صاحب محصول): عددِ تولیدِ سه مهلت (پیشنهاد: ۱۵ ثانیه، ۱۵ ثانیه، ۶۰ ثانیه)؛ قاعدهٔ سرریزِ پایانِ ماه؛ CI برای `entitlement-clock`،
  `request-deadline`، `ops-paging`، `admin-notify-usage`.
### ۱۴۰۵/۰۸/۱۹ — شاخهٔ `claude/sub-ten-rsk` (ردیف‌های PARTIAL ِ SUB، TEN، RSK، DATA و PRES؛ روی main ‏37f8fa4c = دستهٔ ۳۵؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
### ۱۴۰۵/۰۸/۱۹ — شاخهٔ `claude/sub-ten-rsk` (ردیف‌های PARTIAL ِ SUB، TEN، RSK، DATA و PRES؛ روی main ‏0e2e7b36 = دستهٔ ۳۶؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: (۱) دو باگِ مرزِ اشتراکِ دکان (`entitlement-boundaries.test.js`، پیش از رفع ۲ سرخ): تمدیدِ ماهانه/ساله از بامدادِ یکمِ ماه ِ کابل با UTC ِ خام
  تا ۲۹ ِ همان ماه می‌رسید (سه روز کم) ⇐ `plans.endOfPeriodIn` و `max` با حسابِ پیشین؛ اشتراکِ شروع‌نشده دورهٔ آزمایشیِ دکان را می‌سوزاند ⇐ نه.
  پمپ عمداً دست نخورد (همان دو باگ را دارد؛ تصمیم با صاحب‌کار). (۲) RSK-03 ِ وب: کارتِ «سابقهٔ این مشتری» در جزئیاتِ سفارشِ وب
  (`license/orders-risk.js`، پرچمِ `shop.order_risk` ⇒ `risk` ِ `/config`)؛ `web-orders.mjs` ۲۵/۲۵ (پیش از سیم‌کشی ۲ سرخ). (۳) SUB-02 با شاهدِ CI ِ #1019
  (سرور 42668579333) IMPLEMENTED-VERIFIED شد. (۴) جداسازی روی PostgreSQL ِ محلی: `tenant-matrix`، `role-route-isolation`، `tenant-surfaces` ۱۱/۱۱؛
  ریسک/حضور/لینک ۳۷/۳۷؛ اشتراک ۸۳/۸۳.
- نیمه‌کاره: هیچ کدِ نیمه‌کاره. SUB-08/S144 (D-26) ساخته نشد: راهِ push ِ همگام‌سازی (v1 مشترکِ پمپ) را می‌خواهد. TEN-04 بسته به ACP-D1/D2.
- قدمِ بعدی: شاهدِ CI برای `entitlement-boundaries` و سه گامِ RSK-03 ِ `web-orders.mjs`؛ کارتِ ریسک در فهرستِ سفارش (برنامه و وب)؛ D-26 با صاحبِ همگام‌سازی.
### ۱۴۰۵/۰۸/۱۸ — شاخهٔ `claude/pos-inv-2` (پی‌گیری‌های Kotlin ِ صندوق و انبار؛ روی main ‏37f8fa4c = دستهٔ ۳۵؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: (۱) کادرِ ردیفِ صندوق (`CartLineDialog`) با `UnitSale.typedToBase` ِ هسته (= `toBaseQty`؛ ۱٫۵ × ۰٫۰۰۹ = ۰٫۰۱۴). (۲) `HeldInvoice.seller`
  (خالی نوشته نمی‌شود) و برگهٔ صندوقِ سبدِ جاری در `HeldBackupRules.of`. (۳) گیرندهٔ `ScanRouter` در «انبارگردانی»: «ابزارهای تازهٔ دستگاه» ⇒ +۱،
  خاموش ⇒ فقط پیام (امن‌تر از امروز: نه بستنِ صفحه، نه سبدِ صندوق). (۴) کادرِ عمده در «کالای تازه» ِ نسخهٔ ۳ و `DeskProducts` (ردیفِ تازه و
  پنلِ ویرایش؛ دو تابعِ حدها به `DeskProductParts.kt` رفت تا سقف بالا نرود) و ردِ سابقهٔ تغییرِ عمده (`productChanges`). (۵) برچسبِ
  `customers.credit_limit` انجام نشد: دستهٔ ۳۶ هنوز روی main نیست.
- نیمه‌کاره: هیچ آزمونِ Kotlin این‌جا اجرا نشد (Gradle نیست)؛ همه با CI ِ دسته.
- قدمِ بعدی: پس از مرجِ دستهٔ ۳۶ برچسبِ فارسیِ `customers.credit_limit`؛ تصمیمِ صاحب‌کار: اسکن در انبارگردانی با تنظیمِ خاموش هم +۱ بزند یا نه.
### ۱۴۰۵/۰۸/۱۸ — شاخهٔ `claude/sec-audit-35` (ممیزیِ امنیتیِ سرور با سنجه؛ روی `ccr-3a0d593c-ac0jpj-batch-35` = #1019؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: سه دورِ فاز روی ۹۹۸ درِ زنده + خواندنِ هدف‌دار؛ هشت یافته رفع شد، هر کدام با آزمونی که پیش از رفع سرخ بود
  (`server/test/security-audit-35.test.js`، جدولِ B2 در `docs/SECURITY-AUDIT-fa.md`): پشتیبانِ کامل و CRM-15 (پرچمِ خاموشِ
  `shop.backup_contact_guard`)، بدنهٔ ۶۵MB پیش از سنجشِ نقش، خبرِ آینده‌دار که خبرهای صاحب را خاموش می‌کرد، «هر کاربر یک دکان»
  (رقابت روی PostgreSQL: هشت دکان)، NUL ⇒ ۵۰۰ در ۳۵+ در، کدِ SQLSTATE در ۵xx، `/api/errors` و دو درِ مدیر. S17/S18/S19 با
  سنجه بسته شدند (S18 تکهٔ مانده: سطلِ IP ِ `otp/request`).
- نیمه‌کاره/تصمیم: روشن کردنِ `shop.backup_contact_guard` و `auth.phone_add_code` با صاحب محصول. باز و بیرون از دامنه (فقط گزارش):
  P1 پارسرِ ۳۴MB ِ پمپ پیش از احراز (High)، P2/P3 همان رقابت و زمانِ آینده در پمپ، X1 درهای مدیرِ همگام‌سازی، X2–X4.
- قدمِ بعدی: P1 در کارِ پمپ (با اجازهٔ صاحب‌کار)؛ شاهدِ CI ِ `security-audit-35`.
### ۱۴۰۵/۰۸/۱۸ — شاخهٔ `claude/web-flake-voice` (ریشهٔ لرزانیِ `web-session.mjs` و CHAT-07 ِ ویترین؛ روی main ‏37f8fa4c = دستهٔ ۳۵؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: (۱) «لرزانی» باگِ واقعی بود، نه زمان‌بندیِ آزمون: `refreshCloudBackups` ِ `index.html` (setTimeout ۲٫۵ ثانیه) با توکنِ کهنه
  `backups.list` می‌زد و ۴۰۱ اش پس از پایانِ تازه‌سازیِ گام می‌رسید ⇒ تک‌پروازِ `TohidAccount.refresh` بسته بود و توکنِ همان لحظه ذخیره دوباره
  می‌چرخید (ردگیریِ fetch با پشته در اجرای سرخ). حالا هر سه لایه توکنی را که فرستاده‌اند می‌دهند (`refresh(stale)`). ۴۶ اجرای پیشین ⇒ ۴ سرخ
  (همه «دورِ ۱/۲: توکنِ چرخیده»)، ۲۵ اجرای پسین ⇒ ۲۵/۲۵ و هر بار دقیقاً ۱۴ تازه‌سازی (پیشین ۱۱ تا ۱۳ با گامِ کمتر)؛ سه گامِ قطعیِ `page.route`
  (پیش از رفع ۳/۳ سرخ). (۲) CHAT-07: ضبطِ صدای مشتری در ویترین پشتِ پرچمِ خاموشِ `shop.chat.voice` (`store/chat-voice.js` ِ دیربار، واژه‌نامهٔ
  `voice`، `microphone=(self)`)؛ `store-chat-voice.test.js` ۷/۷ (PGlite و PostgreSQL ِ محلی، چهار جهش) و گام‌های میکروفونِ ساختگی در
  `web-store-chat.mjs`.
- نیمه‌کاره: هیچ کدِ نیمه‌کاره؛ شاهدِ CI ِ هر دو. صدای Opus/WebM ِ کروم در برنامهٔ آیفونِ دکان شاید پخش نشود (کروم AAC ندارد؛ سافاری MP4/AAC).
- قدمِ بعدی: شاهدِ CI ⇒ CHAT-07؛ روشن کردنِ `shop.chat.voice` برای یک دکانِ آزمایشی و شنیدنِ صدای کروم روی آیفونِ دکان.
### ۱۴۰۵/۰۸/۱۹ — شاخهٔ `claude/store-cart` (CHK-03: سبدِ حساب در ویترینِ وب، سرتاسر؛ روی main ‏37f8fa4c = دستهٔ ۳۵؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: هم‌گامیِ سبدِ حساب از acct.js به `store/cart-sync.js` ِ دیربار رفت (فقط با `accountCart` و نشست؛ بارِ نخست ۷۰٬۴۶۳ بایت،
  سقف ۷۱KB). سه باگِ کلاینت، هر سه با آزمونِ پیش از رفع سرخ: ۴۰۹ قلمِ دستگاهِ دیگر را پاک می‌کرد (حالا سه‌طرفه، یک بار دوباره، سپس درنگ)؛
  خاموش کردنِ پرچمِ سبد مشتری را بیرون می‌انداخت (فقط `session_required` خروج است)؛ سبدِ حساب پس از خروج/پاک شدنِ حساب/نشستِ منقضی روی
  دستگاه می‌ماند. ۵۰۳/۴۲۹/بی اینترنت ⇒ درنگِ دوبرابرشونده بی پیامِ خطا؛ «خروج» اول صفِ سبد را می‌نویسد؛ در حالتِ حساب قیمت و «دیگر نیست»/
  «ناموجود» از پاسخِ سرور با پیامِ ساده (`ac.cart.out` ِ تازه). آزمونِ تازهٔ `web-store-cart-sync.mjs` (۴۶ گام، axe).
- نیمه‌کاره: کدِ نیمه‌کاره نیست؛ سرور دست نخورد (ممیزیِ امنیتی بی یافتهٔ سرور). CHK-03 PARTIAL می‌ماند: برنامهٔ خریدِ بومی هنوز سبدِ سرور را
  نمی‌خواند (D-09). برچسبِ سطرِ خط‌خوردهٔ «ناموجود» در سبد هنوز همان «دیگر در ویترین نیست — بردارید» ِ امروز است (فقط پیام دقیق است).
- قدمِ بعدی: شاهدِ CI ِ `web-store-cart-sync.mjs` ⇒ بازنگریِ CHK-03 برای وب؛ هم‌گامیِ سبد در برنامهٔ خرید پس از D-09.
### ۱۴۰۵/۰۸/۱۹ — شاخهٔ `claude/web-2` (ردیف‌های غیرِ P0 ِ ویترین، سفارشِ آنلاین و پیک؛ بارِ نخست؛ روی `ccr-3a0d593c-ac0jpj-batch-34` = #1018؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: (۱) باگِ واقعیِ مشتری: `PUT`/`merge` ِ سبدِ سرور و ♥ همان سطلِ `store-checkout` ِ ثبت را (۲۰ در ربع ساعت به‌ازای IP) می‌خوردند ⇒
  مشتریِ واردشده پس از ~۲۰ تغییرِ سبد سفارش را با ۴۲۹ نمی‌توانست ثبت کند؛ حالا سطلِ `store-acct-sync` (`RATE_STORE_SYNC_MAX` ۲۴۰)،
  `storefront-sync-limit.test.js` (پیش از رفع سرخ، سه جهش سرخ). (۲) WEB-11: ♥ ِ وب با نشستِ حساب هم‌گام (`catalog.js` ِ دیربار: ادغامِ
  نخستینِ هر نشست، سپس حساب حرفِ آخر، صفِ بی اینترنت `store-favsync:<slug>`)، `web-store-web.mjs` با ۹ گامِ تازه (با catalog.js ِ پیشین ❌).
  (۳) بارِ نخست ۷۳٬۱۹۰ ⇐ ۷۰٬۲۵۸ بایت فقط با کوتاه کردنِ توضیح‌های store.js؛ سقف ۷۲KB ماند. (۴) متنِ کهنهٔ CHK-18 (رزرو از #340 نیست ⇒
  تصمیمِ صاحب‌کار) و DLV-06 (گفت‌وگوی سفارش در پیگیری هست؛ ماندهٔ تنها: تماسِ مستقیم با پیک) درست شد.
- نیمه‌کاره: هیچ کدِ نیمه‌کاره؛ PostgreSQL ِ محلی نبود (فقط PGlite). ماندهٔ بیرونی/تصمیمی: WEB-10 و MKT-08 (D-09)، CHK-13 (D-08)،
  WEB-17 (Search Console)، DLV-03 و CHK-18 و DLV-06 (تصمیمِ صاحب‌کار)، ORD-15 (بازپرداختِ آنلاین D-08 و ردِ قلم دمِ در در برنامهٔ پیک).
- قدمِ بعدی: شاهدِ CI ِ `storefront-sync-limit` و `web-store-web.mjs` ⇒ بازنگریِ WEB-11؛ پس از مرجِ دسته سقفِ `FIRST_LOAD` به ۷۱KB.
### ۱۴۰۵/۰۸/۱۸ — شاخهٔ `claude/pur-phone` (سفارشِ خرید روی گوشیِ نسخهٔ ۳، ممیزی و دریافتِ کامل با یک لمس؛ روی دستهٔ ۳۴ = #1018؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- ممیزی روی پایهٔ تازه: «گوشی راهِ سفارشِ خرید ندارد» و «فرمِ ثبت خرید ِ گوشی هزینهٔ جانبی و سررسید ندارد» دیگر درست نیست — هر دو پیش‌تر
  آمده‌اند (دستهٔ ۳۰ `claude/po-phone-v3` و دورِ ۳۸ `claude/gaps38-mgmt`، #958): فهرست، ساختن/ویرایش، فرستادن، لغو، دریافتِ کامل/جزئی با اسکن،
  ثبت در دفتر، تطبیقِ سه‌طرفه و برچسب؛ همهٔ کلیدهای یک‌باره سر جایشان. آزمون‌های خرید روی همین پایه سبز (۸۴ آزمون). ردیف‌های PUR-06/07 که هنوز
  «گوشی ندارد» می‌گفتند با یادداشتِ «بسته شد» درست شدند.
- انجام شد: «دریافتِ همهٔ باقی‌مانده» در صفحهٔ دریافتِ گوشی (`PoForms.fillRemaining/hasRemaining`، جزءِ `PoReceiveAll`) — فقط کادرها را پر می‌کند.
- کارِ نیمه‌کارهٔ `/home/user/wt/gaps37` (۱۴۰۵/۰۸/۱۴) کهنه و جایگزین‌شده است؛ چیزی از آن برداشته نشد.
- قدمِ بعدی: همان دکمه در پنجرهٔ دریافتِ کامپیوتر (منطق آماده)؛ آزمون روی گوشی و بارکدخوانِ واقعی (**دستگاه**).
### ۱۴۰۵/۰۸/۱۹ — شاخهٔ `claude/resend-dropped` (بازفرستِ `hash_mismatch` ِ عددی، پایان؛ روی دستهٔ ۳۳ = #1017؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: ادغامِ دستهٔ ۳۳ (`JsNumber.shortest`) و بازفرست با همان `JsNumber` برای هر دو علت (نمای جاوا و رقمِ اضافهٔ جاوا ۱۷/ART)؛
  نگهبانِ تازهٔ `DroppedResend.Settled`: ردیفِ سندِ کنارگذاشته/اصلاح‌شدهٔ «سندهای ردشده» هرگز دوباره نمی‌رود (بایگانی‌شده هم)؛ فیکسچرِ
  `DroppedResendTest` به جاوای اجرا بسته نیست (CI ِ جاوا ۱۷)؛ دو ردیفِ ART (`0.0020` بی نما، `5.6843418860808015E-14`)؛ بردارِ
  `core/test-vectors/resend-dropped-v1.json` ساخته شد؛ آزمونِ سرور پرچمِ `sync.safe_entries` (schema ۴) را می‌گذارد. شرح: DATA-SAFETY §۸ بندِ ۳.
- نیمه‌کاره: PostgreSQL ِ محلی نبود (فقط PGlite)؛ رفتار پشتِ پرچم نیست (همان تصمیمِ شاخه) — تصمیمِ صاحب‌کار در گزارش.
- قدمِ بعدی: CI ِ جاوا ۱۷ و PostgreSQL ِ همین بردار؛ پیش از روشن کردنِ v3 روی دستگاهِ v1 یک دورِ v1 (یافتهٔ `initialize(keepLocalOnly = false)`).
### ۱۴۰۵/۰۸/۱۸ — شاخهٔ `claude/safety-followups-kt` (بخشِ Kotlin ِ `docs/wip/safety-followups-kotlin.patch`؛ روی main پس از #1017؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد (آزمون‌ها اول سرخ با تابع‌های خالی، بعد سبز): (الف) نگه‌داشته‌ها و سبدِ جاری در پشتیبانِ کامل (`device/held-invoices.json`)
  و بازیابی فقط پس از دفتر و حصارِ بازیابی، فقط دستگاهِ بی فاکتورِ باز، همان دکان، نه فروخته؛ **افزوده بر وصله:** راهِ خودکارِ
  پس از ورود فقط پشتیبانِ همین دستگاه (`HeldBackup.from`) تا پشتیبانِ ابریِ گوشیِ دیگر نگه‌داشته‌های بازِ آن را روی کامپیوترِ تازه
  دوتایی نکند. (ب) «در دستِ دستگاهِ X» و «یک فاکتور در دو دستگاه» در برنامه (پشتِ همان `shop.held_sync`) با کلیدِ تازهٔ فروش؛
  **رفع بر وصله:** سقفِ ۳ ثانیه واقعی شد (درخواست در پس‌زمینه؛ `withTimeoutOrNull` دورِ `HttpURLConnection` ِ مسدودکننده
  برنمی‌گشت)، پرسش روی `Dispatchers.IO` و یک پرسش در هر لحظه. (ج) `FLAG_SECURE` ِ برگهٔ عبارت روی اندروید؛ کامپیوتر و آیفون بی‌اثر
  (مستند در DATA-SAFETY §۲ بندِ ۱۲-ج و FLAGS-ON §۸و). همهٔ تکه‌های Kotlin ِ وصله نشست (سرور در #1017 بود)؛ خودِ پرونده فقط در شاخهٔ `claude/safety-followups-wip` است، نه این‌جا —
  آن شاخه دیگر لازم نیست.
- نیمه‌کاره: آزمونِ اندرویدیِ واقعیِ عکسِ صفحهٔ سیاه، برگه‌های «در دستِ» و «دوتایی» (آزمونِ تصویری ندارند)، دو دستگاهِ واقعی؛
  کامپایلِ آیفون این‌جا اجرا نشد (فقط `SecureWindow` ِ بی‌اثر افزوده شد).
- قدمِ بعد: روی دکانِ آزمایشی `shop.held_sync` و برگه‌ها روی دو دستگاه (FLAGS-ON §۸ز «بعد از روشن کردن ببینید»)؛ تصمیمِ صاحب:
  نگه‌داشته‌های پشتیبانِ دکان روی دفترِ بی‌صاحب (پیش از ورود) هم بنشینند یا نه (امروز نه).

### ۱۴۰۵/۰۸/۱۸ — شاخهٔ `claude/p0-web` (ردیف‌های P0 ِ نیمه‌کارهٔ ویترین و سفارشِ آنلاین؛ روی `ccr-3a0d593c-ac0jpj-batch-33`؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: پیش‌نمایشِ پیوند (WEB-20 × WEB-01، `lib/store-seo.js`): og:image ِ کالا با `?v=` ِ نسخهٔ عکس (بانک پیش از همگام‌شده) و لوگوی دکان
  برای پیوندِ دکان و کالای بی‌عکس با ابعاد و نوع (`storefront-seo-media.test.js` ۲/۲، پیش از تغییر سرخ)؛ آزمونِ رنگِ ویترین در
  `storefront.test.js`؛ PCK-11 با شاهدِ main ‏5c375ada به IMPLEMENTED-VERIFIED؛ متنِ «مانده» ِ WEB-01/05/16/20، CHK-03/08/10/20،
  ORD-16، PCK-07 و DLV-01 کوتاه و درست شد (لوگوی WEB-01 در وب هست؛ «لوگو نیست» ِ کهنه برداشته شد).
- نیمه‌کاره: هیچ کدِ نیمه‌کاره‌ای نیست؛ همهٔ ماندهٔ این ردیف‌ها بیرونی یا تصمیمِ صاحب‌کار است: D-09 (پیامِ تأیید، «رسیدیم»،
  فرستادنِ واقعیِ ORD-16، ورودِ مشتری برای سبدِ سروریِ برنامه)، D-08 + PAY-23/ORD-15 (بازپرداختِ سفارشِ حساب‌پی)، فرهنگِ
  مترادف (POS-04)، CDN (AVIF/WebP)، نقشه، وبِ پیک، `market.slug_hold`، و سنجشِ واقعیِ پیش‌نمایش در واتس‌اپ/فیسبوک.
- قدمِ بعدی: پس از مرج، شاهدِ CI ِ `storefront-seo-media` و آزمونِ رنگ ⇒ بازنگریِ WEB-01؛ با پرچمِ سئو روی یک دکانِ واقعی،
  Sharing Debugger ِ فیسبوک و فرستادن در واتس‌اپ ⇒ WEB-20.
### ۱۴۰۵/۰۸/۱۸ — شاخهٔ `claude/p0-pay` (ردیف‌های نیمه‌کارهٔ P0 ِ پرداخت و گزارش؛ روی `ccr-3a0d593c-ac0jpj-batch-33`؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: **PAY-23** — برگشت فقط از پولِ رسیده: `PayStatus.forRefund` و `PayStatus.refundRoom` (core) در `ReturnFlow.paidRows`/`split`؛
  همان قاعدهٔ X/Z: «در انتظار» ِ واقعی، «ناموفق»، «لغو» و «نیازمند بررسی» نه به روشِ خودش و نه با نقدِ دخل پس داده می‌شوند و با ردیفِ
  نارسیده جمعِ برگشت‌ها از پولِ رسیده بیشتر نمی‌شود (پیش‌تر ۴۰۰ به حساب‌پیِ «در انتظار» و ۲۵۰ + ۲۵۰ نقد از ۴۰۰ ِ رسیده)؛ پیامِ
  `StrLedger.refundAwaitsPayment`؛ فاکتورِ سفارشِ اینترنتی عمداً همان امروز (سرور مرجع، ORD-15). **RPT-03**: `mavgReplay` ِ `report-core.js` به `stock-core.js` (`reportReplay`) رفت، رفتار بایت‌به‌بایت
  (core-fuzz و cost-v1)، سقفِ ۸۱۶ ِ هر دو رونوشت از خط‌مبنا برداشته شد. **O-22** (گردِ نیم‌افغانیِ `ReturnShare`) با سه گزینه و نمونهٔ دقیق
  در `docs/OWNER-DECISIONS-fa.md` (رفتارِ امروز با `return-share-v1.json` قفل). **PAY-13** با شاهدِ CI ِ دیده‌شدهٔ main ‏2cf59860 ⇒ VERIFIED.
- نیمه‌کاره: PAY-07 (آینهٔ `needs_review`/`refunded` ِ قصدِ سرور روی ردیفِ فاکتور: هستهٔ همگام‌سازی یا خواندنِ قصد؛ محیطِ آزمایشیِ D-08)،
  PAY-06 (روشن/خاموشِ روش‌ها در پنلِ صندوق: شاخهٔ صندوق؛ کیف‌پول‌ها D-08)، PAY-15/RPT-04/RPT-11 فقط دستگاه و چاپگر، RPT-03 تا روشن
  شدنِ «گزارشِ تازه». هیچ‌کدام بی CI ِ همین شاخه VERIFIED نشد.
- قدمِ بعدی: CI ِ دستهٔ همین شاخه؛ جوابِ O-22 (اگر «ب»: `ReturnShare` و `returnShare` و بردار با هم)؛ RET-06 (روشن کردنِ برگشتِ تازه).

### ۱۴۰۵/۰۸/۱۸ — شاخهٔ `claude/p0-crm` (ردیف‌های P0 ِ CRM-03، CRM-15، RET-12، STF-02/03/04؛ روی main ‏bd6cb560 = دستهٔ ۳۳؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: **CRM-03** راهِ «بررسی» در سرور پشتِ پرچمِ خاموشِ `shop.credit_limit_review` (`lib/credit-limit-review.js`): فروشِ نسیهٔ تازهٔ بیش از سقفِ اعتبار بی تأییدِ
  دستگاه در v1، v3 و راهِ قدیم/REST فقط سابقهٔ `sale.credit_over_limit` می‌گیرد، هرگز رد (بی دست زدن به `sync-v1`). **CRM-15** «خبرم کن» و سدِ تخفیفِ ویترین برای
  مدیرِ بی `customers.contact` شمارهٔ پوشیده می‌دهند و «زنگ» روی شمارهٔ پوشیده نیست. بی مسیرِ تازه، بی مهاجرت. همه PARTIAL ماندند (شاهدِ CI هنوز نیست).
- تصمیمِ صاحب‌کار لازم: CRM-03 (رد یا فقط بررسی)، CRM-15 (`phoneMasked` ِ ۴+۳)، STF-04 (هویتِ سروریِ کاربرِ صندوق). سخت‌افزار: RET-12 (چاپِ ۵۸/۸۰ میلی‌متری).
- قدمِ بعدی: STF-03 سنجشِ سروریِ `promo` ِ قلم و `loyalty_spend` با تعریف‌های دکان (پشتِ پرچمِ تازه)، مجوزِ «تغییرِ سقفِ اعتبار» (§۱۰.۱) در هسته، و شاهدِ CI برای دو آزمونِ تازه.
### ۱۴۰۵/۰۸/۱۸ — شاخهٔ `claude/p0-pos` (ردیف‌های P0 ِ صندوق و چهار باگِ صندوق؛ روی `ccr-3a0d593c-ac0jpj-batch-33`؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: (۱) **پیش‌نویس** تخفیفِ کل، روشِ پرداخت، دریافتی و فروشندهٔ فاکتورِ اصلاح‌شده را هم نگه می‌دارد (`SaleDraft.page`،
  `CartStore.setPage`؛ `DeskSale` با هر تغییر می‌نویسد و از آن آغاز می‌کند؛ «اصلاح»، «اصلاح و ثبت» و بازگشتِ نگه‌داشته ⇐
  `HeldSession.keep`؛ بازیابی ⇐ `DraftPrompt`)؛ تا پاسخِ پرسشِ بازیابی نوشتنِ صفحه رد. (۲) ردیفِ فیِ ثابتِ برابر با خرده دیگر «زنده»
  نمی‌شود (`PriceList.cartUnit`؛ یک ادعای `PriceListTest` عمداً عوض شد). (۳) فاکتورِ اصلاح‌شده به نامِ فروشندهٔ همان فاکتور
  (`HeldExtras.seller`). (۴) POS-42: جدولِ موجودی و شمارِ فروشِ کاتالوگ پس از فروش افزایشی (`core/ledger/AppendTail`،
  `StockIndex.extend`، `CatalogSearch.counts`؛ هم‌ارزیِ بیت‌به‌بیت). POS-01: کدِ کالا در کادرِ جست‌وجوی گوشی (پشتِ `product_info`).
  STATUS: POS-01/10/12/41 متنِ «مانده» کوتاه و راست شد؛ یافته‌ها در POS-13/18/23/42. بی مسیر و مهاجرتِ تازه.
- نیمه‌کاره: POS-41 (Alt+P کوچک نبود)؛ نگه‌داشتنِ فاکتورِ اصلاح‌شده فروشنده را نمی‌برد (`HeldInvoice` فیلدش را ندارد — قلمروی شاخهٔ
  نگه‌داشته‌ها)؛ میانِ نوشتنِ ردیف‌های نگه‌داشتهٔ برگشته و صفحه‌اش چند میلی‌ثانیه است. شاهدِ CI ِ همه.
- قدمِ بعدی: CI و مرجِ همین شاخه؛ سنجشِ POS-42 روی گوشیِ واقعی؛ تصمیم‌های صاحب‌کار POS-02/04/10/21 و دستگاهِ POS-03/31.
### ۱۴۰۵/۰۸/۱۸ — شاخهٔ `claude/p0-inv` (ردیف‌های P0 ِ کالا و انبار: PRD-07، PRD-12، INV-01، INV-16؛ روی main پس از #1017 (دستهٔ ۳۳)؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: **PRD-07** ضریبِ دقیقِ واحدِ فروشِ دوم با هزارمِ صحیح مثلِ سرور و برگهٔ ردیف (`UnitSale.toBaseQty`؛ ضریبِ کهنهٔ ناجور همان ضربِ پیشین)، ضریبِ تازهٔ ناجور با هزارم رد (`data/ProductDraftRules`)، کادرِ ضریب از هزارم، و واحدِ پایهٔ کالای گردش‌دار
  بی‌صدا عوض نمی‌شود. **PRD-12** کادرِ «قیمتِ عمده» در «ویرایش کالا» ِ نسخهٔ ۳ پشتِ «لیستِ قیمت در صندوق» (پیش‌تر هیچ فرمی عمده نمی‌نوشت ⇒ لیستِ عمده
  همیشه خرده بود). **INV-16** ارزشِ «تحلیلِ موجودی» با موتورِ تازه = خودِ جمعِ دفتر (`ValueLine.costValueMinor`). **INV-01** تراشهٔ هر محل در فهرستِ
  انبارِ نسخهٔ ۳ (`StockListModel.atPlace`، پشتِ «محل‌های نگه‌داری»). `StockLedger.kt` شکسته شد (`StockLedgerEvents.kt`، بی تغییرِ رفتار) و سقفش پایین آمد.
- نیمه‌کاره: CI و مرج. `SaleDialogs` ِ صندوق هنوز ضربِ ممیزِ شناورِ خودش را دارد (شاخهٔ صندوق، نیمهٔ هزارم)؛ سرور ضریب و تغییرِ واحد را نمی‌سنجد؛ «ارزش موجودی (به بهای خرید)» ِ
  گزارش و خانه همان `purchasePrice` (تصمیمِ صاحب‌کار)؛ موتورِ تازه پشتِ `shop.stock_v2`؛ شعبه فازِ ۶.
- قدمِ بعدی: پس از CI ِ سبز، شاهدِ CI در STATUS؛ تراشهٔ محل در کامپیوتر و گوشی روی دستگاه؛ سنجشِ ضریب و تغییرِ واحد در سرور (`sync-v3-merge.validate`).
### ۱۴۰۵/۰۸/۱۸ — شاخهٔ `claude/p0-hw` (ردیف‌های P0 ِ دستگاه، AI-04 و LOC-03؛ روی main ‏bd6cb560؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: HW-05 — کادرِ کوچکِ مقدارِ شمارشِ انبار و انتقال (`DraftCountField`) نگهبانِ اسکن گرفت، بی رفتن به صندوق
  (`toCart = false`؛ شمارشِ نوشته‌نشده در `remember` است)، و `V3ScanGuardTest` هر کادرِ عددیِ بی‌نگهبانِ `ui/v3` را سرخ می‌کند.
  HW-01 — نشانهٔ «−» ِ تخفیف/گردکردن/ماندهٔ منفی روی کاغذِ راست‌به‌چپ «۵۰−» بود: `core/receipt/PaperBidi` (LRM) در
  `Receipt.layout`. LOC-03 — «جمعهٔ تعطیل» نخستین مصرف‌کننده را در «روزهای فروشِ این هفته» ِ آمار گرفت. شاهدِ CI ِ main
  ‏bd6cb560 (42596663179) برای کارهای `claude/gaps34-hw` که با دسته‌ها رسیده بودند (HW-05، HW-13) در STATUS نوشته شد؛
  `EasySetupWiringTest` همان‌جا سبز است، پس چیزی از #952 دوباره ساخته نشد. همهٔ آزمون‌های تازه پیش از رفع سرخ بودند.
- نیمه‌کاره: هیچ ردیفی بالا نرفت (سخت‌افزارِ واقعی، دستگاهِ اندروید ۱۷). AI-04 فقط ممیزی: پردازشِ مدل‌دار `blocked_external`
  و بی ارسال؛ صفحهٔ مقایسه و «برگشت به اصل» ِ دکان نیست. یافته (مالِ `claude/p0-inv`، دست نخورد): در کامپیوتر بارکدخوان وقتی
  هیچ کادری فوکوس ندارد در «شمارشِ انبار» به صندوق می‌رود (`Main.kt` ⇐ `ScanRouter.dispatch` ⇐ `AppNav.go("sale")`؛
  شمارش گوش‌دهنده ندارد) و شمارشِ نوشته‌نشده گم می‌شود.
- قدمِ بعدی: تصمیمِ صاحب‌کار D-16 (مدلِ خودمیزبان) پیش از AI-04؛ تنظیمِ «روزهای کاری و تعطیلِ دکان» با همگام‌سازی (LOC-03)؛
  گوش‌دهندهٔ `ScanRouter` در «شمارشِ انبار» (اسکن ⇒ +۱) با `claude/p0-inv`.
### ۱۴۰۵/۰۸/۱۷ — شاخهٔ `claude/customer-app-2` (برنامهٔ خریدِ مشتری، مرحلهٔ ۲؛ روی `claude/customer-app-1` = #964؛ سیزنِ `session_01GcPTALbaroEADJph5to8Qj`)
- انجام شد (همه روی درهای امروزِ سرور، بی درِ تازه و بی مهاجرت): نشانی‌های من با پین (CHK-08/09/10: «موقعیتِ من» یا پیوند/مختصاتِ
  برنامهٔ نقشه با `PinText`، «نمایش روی نقشه» با `geo:`، ناحیه از پین، ۱۰ نشانی روی دستگاه با قاعدهٔ `parseAddress`؛ کارخواهِ
  `/:slug/account/addresses` آماده و روی سرورِ واقعی آزموده)، گفت‌وگو با دکان (CHAT-02/06: صفِ دیسکیِ `ChatOutbox` با یک clientId،
  long-poll، رسید، عکس با جهتِ EXIF و بی EXIF از `ShopperPhoto`، «مشکل دارم»/«گفت‌وگوی تازه» از `life` ِ سرور)، نظر و امتیاز
  (NTF-09، DLV-18)، کدِ تخفیف و تخفیفِ خودکار (MKT-01/09؛ عددِ سرور در `expectedTotalMinor`)، سفارشِ تکرارشونده روی دستگاه
  (MKT-08 ⇒ PARTIAL؛ ثبت همیشه با تأییدِ مشتری)، پیوندِ عمیق (`tohidshop://`، `vill3n.top/store/…`، `<slug>.vill3n.top`، QR ِ
  کمپین با `campaign` در ثبت)، و پوستهٔ آیفون از همان صفحه‌ها (`customer-ios/shared` + `customer-ios/iosApp`؛ CI: `customer-ios.yml`
  دو کارِ «(گزارش)»). آزمون: `gradle -p core jvmTest --tests 'ir.vil3ntec.tohid.core.shopper.*'`، فاز ۲ ِ `server/test/shopper-live.mjs`
  (`ShopperStage2LiveTest`)، و صفحه‌ها + `CustomerModelTest`/`CustomerStage2Test` روی Compose Desktop.
- نیمه‌کاره: کامپایلِ اندروید و iOS فقط در CI (این‌جا `dl.google.com` ۴۰۳ و Kotlin/Native نیست). MKT-03/04/05/06/07 ساخته نشد:
  Push (O-01)، رضایت و حسابِ مشتری (D-09) و قاعدهٔ پاداش تصمیمِ صاحب‌کارند (شرح در STATUS).
- قدمِ بعدی: ورود و نشستِ بلندِ مشتری پس از D-09 (همگام‌سازیِ همین نشانی‌ها با حساب، Keystore/Keychain)، Push (O-01)، امضا و
  انتشارِ برنامهٔ خرید (اجازه، `OWNER-TODO` §۱۰-ب)، نقشهٔ درون‌برنامه (تصمیمِ صاحب‌کار)، و پس از چند دورِ سبز «(گزارش)» ⇐ سد.
### ۱۴۰۵/۰۸/۱۷ — شاخهٔ `claude/customer-app-1` (برنامهٔ خریدِ مشتری، مرحلهٔ ۱؛ O-04 «ب»؛ روی main ‏8c22f3f4؛ سیزنِ `session_01GcPTALbaroEADJph5to8Qj`)
- انجام شد: کارخواهِ تایپ‌شدهٔ ویترین و بازارچه در `core/src/commonMain/.../core/shopper` (همان درهای عمومی، بی درِ تازه در سرور)،
  قاعده‌های سبد و ثبت (همان `store.js`/`checkout.js` ِ وب و `forCheckout` ِ سرور)، کلیدِ یکتاییِ ثبت، پیگیری، نوشته‌های
  fa/en/ps + دری با `asIs`؛ برنامهٔ جدای `customer-android` (بستهٔ `ir.vil3ntec.tohid.customer`، فقط debug): خانه/بازارچه،
  دکان با جست‌وجو و دسته، کالا، سبد، ثبت (ارسال/دریافت، ناحیه، بازه، تأییدِ شماره)، ثبت‌شد، پیگیری و لغو؛ راست‌چین و تیره.
  آزمون: `gradle -p core jvmTest --tests 'ir.vil3ntec.tohid.core.shopper.*'` و `server/test/shopper-live.mjs` (کارخواه روی سرورِ
  واقعیِ PGlite). CI: کارِ گزارشیِ «برنامهٔ خریدِ مشتری (گزارش)» در `tohid-native.yml`. تصمیم در `OWNER-DECISIONS-fa.md` (O-04).
- نیمه‌کاره: کامپایلِ اندرویدِ `customer-android` فقط در CI (این‌جا `dl.google.com` ۴۰۳)؛ صفحه‌ها روی Compose Desktop کامپایل و
  `CustomerModelTest` روی JVM سنجیده شد.
- قدمِ بعدی (مرحلهٔ ۲): حساب/ورودِ مشتری و نشستِ بلند (CHK-02، D-09)، نشانی‌های ذخیره‌شده با پین، Push (FCM پس از O-01)،
  گفت‌وگو (CHAT-02/06)، نظر، پوستهٔ آیفون از همین کد، و MKT-03…08 روی همین پایه؛ پس از چند دورِ سبز، کارِ CI سد شود.
### ۱۴۰۵/۰۸/۱۴ — شاخهٔ `claude/web-device-id` (O-09 در وب؛ روی `claude/owner-device-cap` = #942؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: ورودِ با رمزِ وب (`login`، `resetPassword` ِ `license/license-client.js` و فرمِ `shop-ui.js`) حالا `device` =
  `{uid, name, platform:'web'}` می‌فرستد؛ `uid` همان شناسهٔ تصادفیِ `tohid-device-uid-v1` است (ورودِ کدی هم همان را
  می‌فرستاد). پس سرور مرورگر را مثلِ برنامه‌ها ثبت می‌کند و مسیرِ صاحب در `claimSlot` کار می‌کند. `warning` ِ `license/sync` ⇒
  `deviceCapOver()` ⇒ نوارِ زردِ `#vip-device-bar` (`vip.js`) با دکمهٔ «دستگاه‌ها». نشستِ تازه `notify()` می‌کند تا قفل و نوار
  بی بارِ دوباره بیایند. سرور دست نخورد. آزمون‌ها: `sec-sub-device-cap-web.test.js` (vm + سرورِ واقعی؛ پیش ۰/۴، پس ۴/۴؛
  جهش ⇒ ۱ سرخ) و `web-device-cap.mjs` (کرومیوم؛ پیش ۳ سرخ، پس ۴/۴، در `test:web`).
- مانده: #942 باید پیش از این مرج شود. نشستِ وبی که پیش از این نسخه باز شده تا ورودِ بعدی دستگاه ندارد (پیامِ «خارج و دوباره
  وارد شوید» حالا کار می‌کند). دستگاهی که از «دستگاه‌های من» بسته شده با ورودِ دوباره فعال نمی‌شود (برنامه‌ها و ورودِ کدیِ وب
  هم همین‌اند) — تصمیمِ جدا.
- قدمِ بعدی: CI ِ همین PR؛ پس از #942 مرج.
### ۱۴۰۵/۰۸/۱۳ — شاخهٔ `claude/owner-device-cap` (O-09 / SUB-09: صاحبِ دکان هرگز از دستگاهِ خودش بیرون نمی‌ماند؛ روی main پس از #923؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: با کلیدِ `shop.device_cap` دستگاهِ سومِ خودِ صاحب (سقفِ ۲) رد می‌شد و آزمونِ قدیم همین را می‌خواست. با کلیدِ خاموش هم
  دستگاهِ ثبت‌شدهٔ صاحب که ۹۰ روز دیده نشده بود، وقتی جاها پر بود، رد می‌شد. حالا `claimSlot` (`server/src/routes/license.js`)
  دستگاهی را که ردیفِ فعالِ حسابِ خودِ صاحب است بیرون از سقف هم می‌پذیرد و `warning` (`device_over_cap`، شمار، سقف، `manage: 'devices'`)
  می‌دهد. کارمندِ بیرون از سقف رد می‌شود و پیامِ «از صاحبِ دکان بخواهید…» می‌گیرد. شناسهٔ ثبت‌نشده با توکنِ صاحب همچنان رد است
  (SEC-CLI-01). آزمون‌های نخست-سرخ: `sec-sub-device-cap` پیش از رفع ۴ سرخ از ۷ بود و پس از آن ۷/۷ سبز شد. جهشِ «صاحب بی شرطِ
  ردیف» ۴ آزمون را سرخ کرد. در برنامه، `core/…/account/DeviceCap.kt` (`DeviceCapTest` ۴/۴) نوارِ زردِ پوسته را در همهٔ صفحه‌ها
  (صندوق هم) نشان می‌دهد و به «دستگاه‌های وصل» می‌برد. صفحهٔ قفلِ `device_limit` هم دیگر «قیمت‌ها» را نشان نمی‌دهد.
- مانده: نسخهٔ وب (`license/license-client.js`) در ورود شناسهٔ دستگاه نمی‌فرستد. پس مرورگرِ تازهٔ صاحب، وقتی جاها پر است، هنوز
  `device_limit` می‌گیرد و صفحهٔ قدیمیِ وب را می‌بیند. پرسشِ کوچکِ O-09 (صاحبی که رمزش را به کارمندان می‌دهد) در `OWNER-DECISIONS-fa.md` ثبت شد.
- قدمِ بعدی: شاهدِ CI برای SUB-09؛ خبر به دکان‌ها و بعد روشن کردنِ `shop.device_cap` (اول یک دکانِ آزمایشی). ورودِ وب `device` را
  بفرستد تا مرورگرِ صاحب هم «دستگاهِ ثبت‌شده» شود.
### ۱۴۰۵/۰۸/۱۴ — شاخهٔ `claude/gaps29-inv` (کمبودهای PARTIAL ِ انبار، دورِ ۲۹؛ روی main پس از #923؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: **INV-10** «شمارش» ِ دفترِ موجودی لحظهٔ هر کالا را در پیش‌نویس نگه می‌دارد (`StockActions.stamp`، `CountDraft.countedAt`؛ `StockLedger.countReview` ⇐ موجودیِ امروز − گردشِ پس از لحظه):
  پیش‌تر کالای شمرده پس از فروشِ وسطِ شمارش آن فروش را دو بار کم می‌کرد (کسریِ دروغ تا موجودیِ منفی) و فروشِ آفلاینِ دیررسیده کسریِ دروغ می‌ساخت؛ حالا نه (`StockCountMomentTest`)؛ سرور: شمارشِ مکان با `countedAt` ِ اختیاری
  (`catalog-manual.js`، `catalog-count-moment.test.js`). **INV-16** D-23 گرفته شد (الف) ⇒ «تحلیلِ موجودی» با موتورِ تازه بهای میانگین، و کیت دوباره ارزش‌گذاری نمی‌شود (`StockValuationCostTest`).
  ردیف‌های دستگاهی/تصمیمی نشان خوردند (INV-09/13 دستگاه؛ INV-01، INV-20 فروش از محل، INV-02@38 تصمیمِ صاحب‌کار؛ INV-03 صندوق با #939؛ INV-04@32 ویترینِ وب). بی مسیرِ تازه، بی مهاجرت، بی رشتهٔ تازه. همه PARTIAL ماندند.
- نیمه‌کاره: CI و مرجِ همین PR. قدمِ بعدی: «ارزش موجودی (به بهای خرید)» ِ `ReportEngine.products` هنوز کیت و موجودیِ منفی را ضرب می‌کند (عددِ دیدنی ⇒ تصمیمِ صاحب‌کار)؛
  فروشِ آفلاینِ پیش از شمردن که پس از ثبتِ شمارش به سرور می‌رسد (بازبینیِ دیررس)؛ فرستادنِ `countedAt` از صفحه‌ای که روزی شمارش را به سرور ببرد.
### ۱۴۰۵/۰۸/۱۳ — شاخهٔ `claude/dari-real-5` (دریِ واقعی، دورِ ۵؛ روی `claude/dari-real-4` (#943)؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: ۶۸ نوشتهٔ خامِ شش صفحهٔ زندهٔ کوچکِ `ui/screens` به دو جدولِ تازه رفت (فارسی بایت‌به‌بایت همان — بازسازیِ متن از جدول و
  سنجش با نسخهٔ پیشین؛ انگلیسی و پشتو تازه): `ScanSheet`/`PhoneLinkHost`/`PackEditor` (`StrScanPages`)، `StorefrontMediaPanel`/
  `HeatmapPanel`/`CustomerReportsPhone` (`StrPanelPages`)؛ ۳۹ نوشتهٔ تازه، ۲۴ کلیدِ جدول‌های پیشین. «بارکد را جلوی دوربین بگیرید…» با
  `td(…)`؛ «گوشی/کامپیوتر» ِ `PhoneLinkHost` نامِ دستگاهِ آن سرِ کار است ⇒ `t(…)`. نامِ نوعِ لوگو/تصویرِ سر از جدول (`mediaKindName`، فارسی
  همان `Kind.label`). آزمون: `DariCoverageTest` (سقفِ متنِ خامِ `ui/screens` ۲۰۵۲ ⇐ ۱۹۸۴، نگهبانِ ۶ پروندهٔ دورِ ۵ — پیش از جابه‌جایی
  سرخ —، کفِ پوشش ۱۹٪، نمونه‌های بایت‌به‌بایت و عوض شدن با زبان و کامپیوتر). واژهٔ تازهٔ دری نه (سقفِ ۶۴).
- نیمه‌کاره: #943 باید پیش از این PR مرج شود (پایهٔ همین شاخه).
- قدمِ بعدی: تصمیمِ صاحب‌کار برای ستون‌های `DayEndModel`/`HeatmapModel` و برچسبِ `CustomerInsight` (صفحه و خروجیِ اکسل با هم)؛ سپس
  `ReportFresh*` پس از #924/#936؛ بقیهٔ `ui/screens` بیشتر کلید و داده یا صفحه‌های نادیدنی است.

### ۱۴۰۵/۰۸/۱۳ — شاخهٔ `claude/dari-real-4` (دریِ واقعی، دورِ ۴؛ آغاز روی `ccr-3a0d593c-ac0jpj-batch-22` چون #934 تا ۲۰ دقیقه مرج نشد، سپس روی main پس از #934 با همان درخت؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: ۵۵۰ نوشتهٔ خامِ زندهٔ `ui/screens` در ۱۴ پرونده به چهار جدولِ تازه رفت (فارسی بایت‌به‌بایت همان — بازسازیِ متن از جدول و
  سنجش با نسخهٔ پیشین؛ انگلیسی و پشتو تازه): `Servers`/`TeamStaff`/`EasySetupAndroid` (`StrSetupPages`)، `ImageBankPicker`/
  `BankPhotoSource`/`ImageBulkImport` (`StrImagePages`)، `PromoText`/`StorefrontCampaigns`/`CrmProfileUi` (`StrMarketPages`)، `BulkSheets`/
  `BulkPriceDialog`/`ProductListModel`/`ProductImportDialog`/`SupplierExtras` (`StrCatalogPages`)؛ ۳۶۱ نوشتهٔ تازه، ۱۷۷ جا جملهٔ جدول‌های
  پیشین. جدول‌های `PromoText` و برچسبِ `enum` ِ وضعیت/زبانه هر بار خوانده می‌شوند؛ روزهای هفته از `StrReports.weekdaysShort`؛ کارتِ
  «زمان‌دار» ِ `Promos` «همیشه» را با جدول می‌سنجد؛ عنوانِ «سرورها» از `StrReach.servers` (کلیدِ `SERVERS_SECTION_TITLE` داده ماند).
  داده با «// داده:»: نامِ کلیدِ تأیید روی سرور، نامِ دو پیوندِ ثابتِ QR، واحدِ پیش‌فرض، دلیلِ حذف در سابقه، نامِ پروندهٔ ردیف‌های رد.
  آزمون: `DariCoverageTest` (سقفِ متنِ خامِ `ui/screens` ۲۶۰۲ ⇐ ۲۰۵۲، نگهبانِ ۱۴ پروندهٔ دورِ ۴ — پیش از جابه‌جایی سرخ با ۵۵۰ نوشته —،
  کفِ پوشش، نمونه‌های بایت‌به‌بایت و عوض شدنِ برچسب با زبان). واژهٔ تازهٔ دری نه (سقفِ ۶۴).
- نیمه‌کاره: #924 (۳۲ متنِ خامِ `ReportFresh*`) با سقفِ تازه هم جور نیست ⇒ متن‌هایش پیش از دسته به جدول بروند.
- قدمِ بعدی: دورِ ۵ برای صفحه‌های زندهٔ کوچک (`StorefrontMediaPanel`، `HeatmapPanel`، `PhoneLinkHost`، `PackEditor`، `CustomerReportsPhone`،
  `ScanSheet`؛ دو تا `td(…)` می‌خواهند)؛ تصمیمِ صاحب‌کار برای `DayEndModel`/`HeatmapModel` (ستونِ خروجی) و سقفِ واژه‌های دری.
### ۱۴۰۵/۰۸/۱۴ — شاخهٔ `claude/gaps33-sec` (کمبودهای SEC، دورِ ۳۳؛ روی main پس از #934؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: SEC-10 — بازگردانیِ پشتیبانِ چتِ پیش از حذف (`scripts/chat-backup.js restore-*`) رسانه و پیامِ هرس‌شدهٔ گفت‌وگوی
  حذف‌شده را زنده می‌کرد (عکس و متنِ مشتری پس از «حذفِ دادهٔ من» برمی‌گشت) ⇒ حالا گفت‌وگوی `erased:` ِ مقصد از فایلِ پیش از حذف
  هیچ پیام/«حذف برای من»/گزارش/سنجاق و رسانهٔ بی‌اشاره نمی‌گیرد (`erased` در گزارش)؛ گفت‌وگوی دیگر و پشتیبانِ پس از حذف کامل.
  آزمون پیش از رفع سرخ، جهش ۲/۲. ردیف‌های SEC-01/02/03/08/10 کوتاه و صادق شد (هیچ ارتقایی بی شاهدِ CI).
- تصمیمِ صاحب‌کار: SEC-08/F0-09 (D-02؛ کلیدِ امضا هنوز در مخزن، ساختِ بی secret به آن برمی‌گردد)؛ SEC-01 روشن کردنِ پیش‌فرض؛
  SEC-10 دفترِ حذف (HMAC ِ شماره) تا بازگردانیِ کلِ سرور (pg_dump) حذف‌ها را برنگرداند.
- نیمه‌کاره: سهمِ دستگاهِ SEC-02/03 (برگشت/قرض/خرید در زنجیره، Sync v3، صفحهٔ بررسی) با تاریخچهٔ دستهٔ #941 هم‌پوشان است ⇒ پس از آن.
- قدمِ بعدی: CI و مرجِ همین PR؛ شاهدِ CI ِ `privacy-erasure-text` و آزمونِ «SEC-10» ِ `chat-backup` در ردیفِ SEC-10.

### ۱۴۰۵/۰۸/۱۳ — شاخهٔ `claude/gaps30-crm` (کمبودهای CRM و RET، دورِ ۳۰؛ روی main پس از #923؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: **CRM-06** دکمه‌های «پیامک/واتس‌اپ» ِ حسابِ مشتریِ نسخهٔ ۳ متنِ همین دستگاه را با همهٔ جای‌نگهدارها می‌فرستند (`DebtReminder.forDebtor`) و پس از باز شدنِ
  برنامهٔ پیام ردِ `debt_reminder` می‌گذارند (`DebtReminder.sent`: یک بار در روز برای هر راه)؛ ویرایشگرِ متن در «گزارش‌های مشتری» ِ گوشی هم. **CRM-07** ردیفِ سفارشِ
  باز/لغوشده در پروندهٔ مشتری (گوشی و کامپیوتر) همان `orders/{id}` را باز می‌کند. شاهدِ CI ِ main ‏62b5ba40 برای CRM-07، CRM-09، CRM-10، CRM-13 (امتیاز)، CRM-16، RET-11
  (متنِ کهنهٔ «order-ledger ۸/۸» درست شد) و RET-12. بی مسیرِ تازه، بی مهاجرت، بی تنظیمِ تازه. همه PARTIAL ماندند.
- تصمیمِ صاحب‌کار لازم: CRM-03 (سرور نسیهٔ بیش از سقفِ بی برگه را رد کند یا به صندوقِ بررسی بفرستد)، CRM-13 («حسابِ من» ِ وب بس است یا تا برنامهٔ خرید، O-04)،
  RET-11 (برگشتِ خریدِ حضوری از راهِ ویترین). سخت‌افزار (کارِ صاحب‌کار): CRM-16 و RET-12.
- **CRM-07 ⇐ IMPLEMENTED-VERIFIED** با CI ِ کامپیوترِ همین PR (#948، 41793429498 سبز). نیمه‌کاره: بقیهٔ CI و مرجِ #948. قدمِ بعدی: CRM-09 (ولایت/طبقه/Plus Code در دفترِ نشانیِ ویترین = مهاجرت + ویترینِ وب)، CRM-10 (راهِ سرورِ v3 برای ادغام).
### ۱۴۰۵/۰۸/۱۳ — شاخهٔ `claude/gaps31-pur` (خانوادهٔ PUR: بدهی و پرداختِ تأمین‌کننده، جمع و ورودِ انبارِ فاکتورِ خرید؛ روی main پس از #934؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: **PUR-05** فاکتور = (شماره، تأمین‌کننده) در `PurchaseBook.invoices` (دو دستگاهِ آفلاین با «#۱» از دو تأمین‌کننده دیگر یکی نمی‌شوند و یادآورِ
  سررسید درست است)؛ کلیدِ فرم در `PurchaseBook.record(key)`/`keyedIds` و نگهبانِ شناسهٔ تکراری در `addPurchase` (دو لمس/F9 یک فاکتور؛ گوشیِ v3 و کامپیوتر)؛
  `PurchaseBook.fixedEntry` — ورودِ انبارِ خرید و سندِ کسر حذف یا کم‌وزیاد نمی‌شود؛ سرور: چهار ناوردای خرید در `verify-data`. **PUR-07** `paySupplier(key)`.
  STATUS ِ هر هشت ردیفِ PARTIAL/MISSING بازنویسی شد؛ هیچ ردیفی بالا نرفت. آزمون: `PurchaseBookTest` ۷/۷، `verify-data.test.js` ۳۳/۳۳.
- ⚠️ یافته: گوشیِ نسخهٔ ۳ درِ «سفارش‌های خرید» ندارد (`PurchasingScreen` ِ پیشین بی فراخواننده؛ فقط `DeskPurchasingScreen`) ⇒ PUR-02/03/04/13 و
  طرفِ دومِ PUR-14 در دکانِ فقط‌گوشی نیست. فرمِ «ثبت خرید» ِ گوشیِ v3 کادرِ «هزینهٔ جانبی» و «سررسید» ندارد (PUR-05/06/07).
- نیمه‌کاره: CI و مرجِ همین PR. قدمِ بعدی: صفحه‌های v3 ِ گوشی برای سفارشِ خرید (فهرست، ساختن، فرستادن، دریافت با اسکن، برچسب)؛ دو کادرِ
  هزینهٔ جانبی/سررسید در `PurchaseFormModel`. تصمیمِ صاحب‌کار: PUR-03 (فصل/اطمینان)، PUR-11 (D-15)، PUR-12 (OCR، D-16)، و اینکه انباردار
  (`stock.adjust`) فاکتورِ تأمین‌کننده ثبت/باطل کند یا فقط مدیر.
### ۱۴۰۵/۰۸/۱۴ — شاخهٔ `claude/gaps32-pres-data` (کمبودهای PARTIAL ِ PRES و DATA، دورِ ۳۲؛ روی main پس از #934؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: دو باگِ واقعی در اعلامِ حضورِ ویترین. (۱) برگهٔ «وضعیت» ِ نسخهٔ ۳ (`PresenceSender`) اعلامِ نرسیده را در خانهٔ بی‌دکانِ
  `pending-presence` نگه می‌داشت ⇒ پس از عوض شدنِ حساب روی همان دستگاه، با باز شدنِ صفحه برای دکانِ دیگر فرستاده می‌شد؛ نشستِ تمام‌شده
  و ۴۲۹ هم اعلام را دور می‌ریختند. حالا همان صفِ پایدارِ `StorefrontQueue` که ردیفِ هر دکان را جدا دارد (`enqueue`/`pending`؛ core:
  `StorefrontOutbox.presenceKept`؛ `PresenceSenderDesktopTest` ۵/۵، پیش از درمان ۳ سرخ). (۲) سرور ترتیبِ اعلام‌ها را با ساعتِ خامِ دستگاه
  می‌سنجید ⇒ دستگاهی با ساعتِ «فردا» تا فردا هر اعلامِ دیگر را «کهنه» می‌کرد؛ حالا `LEAST(زمانِ دستگاه، زمانِ رسیدن)`
  (`storefront-presence-order.test.js` ۳/۳، پیش از درمان ۱ سرخ). ردیف‌ها: PRES-02/08/09 و PRES-01 کوتاه شدند (هیچ ارتقایی، شاهدِ CI
  هنوز نیست)؛ DATA-05 و DATA-06 «تصمیم/کارِ دستیِ صاحب محصول» نشان خوردند.
- نیمه‌کاره: CI و مرجِ همین PR. DATA-04 (قفلِ دو push ِ v1 در `sync-v1.js` ِ پمپ — دست‌نخوردنی)، DATA-07/10 (پشتیبانِ واقعی،
  decided-rows) و DATA-08 (صندوق، دستهٔ دیگر) دست نخوردند.
- قدمِ بعدی: پس از مرج، دورِ شاهدِ CI برای PRES-02/08/09؛ آزمونِ رابطِ Compose برای `StatusPane` (وابستگیِ `compose.uiTest` در
  `desktop` لازم است) تا «آزمونِ رابط» ِ PRES-01/02/04/05/09 بسته شود.

### ۱۴۰۵/۰۸/۱۴ — شاخهٔ `claude/gaps34-hw` (کمبودهای PARTIAL ِ خانوادهٔ HW، دورِ ۳۴؛ روی main ‏b786cb72؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: **HW-05** رفع: `AppRoot` (گوشی و کامپیوتر) و پنجرهٔ کامپیوتر `ScanPrefs.load` را پیش از نخستین کلید صدا می‌زنند (آستانه/پیشوند/پسوند پس از هر
  بار باز کردنِ برنامه تا «دستگاه‌ها» گم بود)؛ نگهبانِ کادرِ `NUMBER`/`MONEY` ِ `V3TextField` (`ui/v3/V3ScanGuard.kt`)؛ «بارکدخوانِ لرزان»
  (`ScanConfig.steady` در `core/hw/KeyScan.kt`: پلِ وقفهٔ بلوتوث، تهِ اسکنِ شکسته هرگز کد نیست، `ScanEcho`) — هر سه پشتِ `hw_tools_v2` (خاموش).
  **HW-13** رلهٔ هابِ محلی (`HubRelayAndroid`) پیش از هر تماس `LocalNetworkAccess.blocked` را می‌پرسد (هدفِ ۳۵ ⇒ بی اثر). ۱۱ ردیفِ PARTIAL با
  شاهدِ CI ِ main ‏b786cb72 کوتاه شدند؛ همه PARTIAL ماندند (سخت‌افزارِ واقعی، قاعدهٔ MKT-10).
- نیمه‌کاره: CI و مرجِ همین PR. قدمِ بعدی: راهِ «اسکنِ گوشی ⇐ هاب ⇐ صندوقِ کامپیوتر» (HW-07 روی هابِ SYN-09)؛ جمعِ سبد روی صفحهٔ دومِ اندروید
  (HW-10)؛ وزن در صندوقِ گوشی (HW-09)؛ تصمیمِ صاحب محصول: `DrawerRule` ِ کامل بی `hw_tools_v2` (HW-03) و منبعِ کدهای تنظیمِ سازنده‌ها (HW-12).
### ۱۴۰۵/۰۸/۱۳ — شاخهٔ `claude/gaps35-ops` (کمبودهای OPS: پشتیبان، بازگردانی، برگشتِ به‌روزرسانی؛ روی main پس از #934؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: (۱) **بازیابیِ سرور «همه یا هیچ»** (`psql --single-transaction`): پیش از این بازیابی روی دیتابیسِ ناخالی ۱۴۹ جدول را نیمه‌کاره
  می‌نشاند (`backup.test.js` ۹/۹ روی PostgreSQL ِ محلی، دو آزمونِ تازه پیش از رفع سرخ). (۲) **ثبتِ تمرینِ بازیابی** (OPS-02@29، از MISSING به
  PARTIAL): هر `npm run restore-drill` (موفق/ناموفق) با RTO/RPO در `<BACKUP_PATH>/drills.jsonl` (فقط عدد و گونهٔ شکست)، در «سلامت» و
  `GET /api/admin/backups` (`drill`)؛ ناموفق یا کهنه‌تر از ۳۱ روز (D-24، `BACKUP_DRILL_MAX_DAYS`) ⇒ زرد. (۳) **گامِ «برگشت» در تمرینِ ارتقا**
  (OPS-03@29): سرورِ نسخهٔ پیش روی پایگاهِ مهاجرت‌شده می‌خواند و می‌نویسد؛ مهاجرتِ ساختگیِ NOT NULL که «تمرینِ ارتقا» را سبز می‌گذاشت حالا
  کدِ ۲ می‌دهد. (۴) شاهدِ CI ِ main ‏b786cb72: OPS-03 ⇐ IMPLEMENTED-VERIFIED؛ OPS-01@29 و OPS-09 شاهد گرفتند و PARTIAL ماندند.
- نیمه‌کاره/برخورد: `claude/decided-rows-1` (در دسته‌های ۲۳/۲۴) هم `restore-drill.js` (هدفِ D-24) و ردیف‌های OPS-02@29/OPS-03@29 را عوض
  کرده؛ تغییرِ این شاخه کنارِ آن است (ثبت، نه سنجشِ هدف) و `withinTargets` ِ آن را اگر باشد ثبت می‌کند ⇒ در ادغام هر دو بمانند.
- قدمِ بعدی (صاحب محصول): نخستین `npm run restore-drill -- --migrate` روی سرورِ واقعی؛ OPS-02 (عبارت/USB/رمزِ نسخه‌های خودکار) کارِ Kotlin
  است؛ OPS-09 (کلیدِ ویترین از پنل، هزینهٔ OTP) و OPS-01 (گامِ انتشارِ مرحله‌ای با اجازهٔ صاحب‌کار).
### ۱۴۰۵/۰۸/۱۴ — شاخهٔ `claude/gaps36-pos` (کمبودهای صندوق، دورِ ۳۶؛ روی دستهٔ ۲۳ ‏#941؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: **POS-40** سرور «روزِ بسته» را هم می‌سنجد (همان پرچمِ خاموشِ `shop.sync.pay_fix`، `till-guard.dayClosed` = `PayFix.closed`):
  اصلاحِ فاکتوری که در صندوقِ بسته است یا پیش از آخرین Z ِ همان روز ⇒ `pay_fix_closed` (صاحب هم)؛ دو بردارِ تازهٔ «صندوقِ شبانه» در `pay-v1.json`
  که هر سه هم‌زاد را می‌سنجد. **POS-06** سنجاقِ کاشی در صندوقِ کامپیوتر (برگهٔ ردیفِ F3، `TillPins.arrangeBy`) و پیامِ درستِ «صفحهٔ اول پُر است»
  برای گوشی و کامپیوتر (`TillPins.change`). ستونِ «مانع» ِ POS-06/15/32/40/41 روشن شد (تصمیمِ صاحب‌کار یا دستگاه). بی مسیر و مهاجرتِ تازه.
- نیمه‌کاره: CI و مرجِ همین PR. نیست: POS-12 بازیابی روی دستگاهِ دیگر (جدولِ همگامِ فاکتورِ نگه‌داشته + قفل؛ کارِ بزرگ)، POS-23 فروشندهٔ ردیف (هم‌پای STF)،
  POS-25 PDF ِ پیش‌فاکتور. قدمِ بعدی: روشن کردنِ `shop.sync.pay_fix` پس از همه‌گیر شدنِ (2.14.5) و `shop.sync.refund_cap`.
### ۱۴۰۵/۰۸/۱۴ — هماهنگ‌کنندهٔ حسابِ دوم (دسته‌های ۲۰ تا ۲۲؛ حساب به ۸۵٪ ِ سقف رسید)
- انجام شد: مرجِ دستهٔ ۲۰ (#918)، #927، #932، دستهٔ ۲۱ (#923: #917/#919/#920/#921 + رفعِ امنیتیِ حذفِ حساب از نشستِ
  دکانِ دیگر ⇒ ۴۰۳ `owner_session_required`، ثبتِ مسیرها در `tenant-matrix`) و دستهٔ ۲۲ (#922 Firebase پشتِ پرچم،
  #925 دری دورِ ۳، #928 وارسیِ سرتاسریِ سفارشِ آنلاین، #930 کاشیِ «سفارش‌های آنلاین» همیشه سرِ خانه).
  گلهٔ صاحب‌کار («فروشِ تازه را سفارش‌های آنلاین کن»/«تاریخچه») ریشه داشت: پایدار از ۲۱ مهر ساخته نشده و کاشی شرطِ
  `OrdersLive.enabled` داشت؛ عکسِ واقعی از CI (شاخهٔ `desktop-screenshots`) برایش فرستاده شد.
- نیمه‌کاره: #924 (کمبودهای ۲۴) از دستهٔ ۲۲ بیرون ماند: ۳۲ متنِ خامِ `ui/screens/ReportFresh*.kt` با سقفِ تازهٔ #925
  (۲۶۰۲) جور نیست ⇒ پیش از دستهٔ بعد متن‌ها به `StrReportPages` بروند. PRهای باز: #926 چت، #929 دسترس‌پذیری،
  #931 پیک (رفعِ نقدِ دوباره)، #933 تاریخچه/چاپ؛ عامل‌ها روی کارمند (`claude/gaps26-staff`)، ردیف‌های تصمیم‌گرفته
  (`claude/decided-rows-1`)، کلیدِ مجوز (`claude/license-key-guard`) و دسترس‌پذیریِ دورِ ۲ (`claude/a11y-v3-round2`).
- قدمِ بعدی: دستهٔ ۲۳ از PRهای بالا (#929 و #930 هر دو `HomeShortcuts.kt`؛ #933 و #929 هر دو `ui/v3` ِ تاریخچه)؛
  دورِ ۱۴ ِ شاهدِ CI؛ هفتهٔ بعد حسابِ اول: فروشگاهِ آنلاین و برنامهٔ خریدِ مشتری (برنامه در #606).

### ۱۴۰۵/۰۸/۱۳ — شاخهٔ `claude/gaps25-chat`، PR #926 (خانوادهٔ CHAT، دورِ ۲۵؛ روی main پس از #918؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد (هر کدام با آزمونِ سرخِ پیش از رفع): (۱) **CHAT-08 حریم**: پیامِ «حذف برای من» در صندوق می‌ماند (`lastMessage.preview` ِ فهرست/نما/همگام و شمارِ
  نخوانده) ⇒ `viewSql(viewer, args)` پنهانِ همان بیننده را کنار می‌گذارد (`shop-chat-scope.test.js`). (۲) **CHAT-11 سقف**: ویرایشِ دکان (PATCH و
  POST …/edit) و حذف/بلاکِ مشتری فقط سطلِ IP داشتند ⇒ حالا `sendMax` ِ فرستنده (`shop-chat-quota.test.js`). (۳) **ویترین** (`store/chat.js`): جوابِ گم‌شده +
  زدنِ دوباره پیامِ دوم می‌ساخت ⇒ همان متن = همان `clientId`؛ بی‌اینترنت ⇒ «در صف» و با وصل شدن خودش؛ برگهٔ پنهانِ مرورگر «خواندم» می‌فرستاد ⇒ فقط «رسید»
  (`web-store-chat.mjs` ۳۷ سنجه). ممیزیِ CHAT-09 (همهٔ درهای دیگر) و درِ بارگذاریِ مشتری (CHAT-10) رخنه‌ای نیافت و آزمونش ماند. بی مسیرِ تازه، بی مهاجرت، بی پرچم.
- یافته‌های باز (در STATUS): ویترین پیامِ صوتیِ دکان را پخش نمی‌کند (حبابِ خالی؛ `media-src` ِ CSP)، دکمهٔ حذف/پاسخ/ویرایش/سنجاق و فرستادنِ عکس برای مشتری
  ندارد، وضعیتِ «رسید/خوانده شد» ِ پیامِ مشتری را نشان نمی‌دهد؛ صفِ ویترین یک‌پیامه و با بستنِ برگه می‌رود.
- تصمیمِ صاحب: CHAT-12 (زمان‌بندی و جای جدای پشتیبانِ رسانه)، CHAT-13 (هدفِ حجمیِ انتشار). بیرونی: FCM (CHAT-11)، دستگاه/شبکهٔ هدف (GATE-NEW-03).
- CI ِ PR سبز روی db83c9bb («سرور» 41760347845 با هر پنج کار، «توحید بومی» 41760348013، کیفیت، مشخصات، CodeQL، امنیت) ⇒ CHAT-01، CHAT-03،
  CHAT-09 و CHAT-10 IMPLEMENTED-VERIFIED (CHAT: ۴ از ۱۵؛ پیش‌تر ۰). برنامهٔ بومیِ خرید (گامِ ۳ ِ §۴۰.۵) و GATE-NEW-03 جدا پیگیری می‌شوند.
- نیمه‌کاره: مرجِ همین PR (هماهنگ‌کننده). قدمِ بعدی: پخشِ صدا در ویترین (`media-src` ِ CSP، پشتِ پرچمِ خاموش)، سپس کارهای مشتری در ویترین (حذف، عکس، وضعیتِ پیام).
### ۱۴۰۵/۰۸/۱۳ — شاخهٔ `claude/a11y-v3-round2` (دسترس‌پذیری دورِ ۲: دو جای خالیِ #929؛ پشته‌ای روی `claude/a11y-v3` ‏#929 ِ هنوز باز؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: ضبطِ صدای گفت‌وگو (`ui/v3/online/chat/VoiceBar.kt`) جز نگه‌داشتن با زدن هم: ماشینِ حالتِ خالصِ `core/chat/VoiceFlow.kt`
  (نگه/زدن/آماده؛ رها کردن فقط ضبطِ نگه‌داشتنی را می‌بندد؛ سقف هر دو را؛ بیرون رفتن از ضبط همیشه میکروفون را می‌بندد)، روی دکمه
  `onClick` و کارهای سفارشیِ «شروعِ ضبط» / «پایانِ ضبط و فرستادن» / «لغو»، «فرستادنِ صدا» ِ دیدنی در ضبطِ زدنی، ناحیهٔ زندهٔ همیشه‌حاضر
  درونِ دکمه (فقط با گذرِ حالت؛ «فرستاده/لغو شد» پس از ۴ ثانیه پاک). ردیفِ «نوار پایین» (`NavSlotList`) جایش را در `stateDescription`
  می‌گوید و «یک جا بالاتر/پایین‌تر» با `NavLayouts.step` (همان `move` ِ کشیدن؛ بی‌اثر/ردشده پیشنهاد نمی‌شود)؛ ردیفِ جابه‌جاشده زنده است.
  نگهبان: `NavSlotList` از `ALTERNATIVES` ِ `V3A11yGuardTest` برداشته شد و `tryAwaitRelease` (نگه‌داشتن) هم کشیدن به حساب می‌آید.
  آزمون: `VoiceFlowTest` (هسته) و دو آزمونِ تازهٔ `NavLayoutTest`.
- نیمه‌کاره: مرجِ #929 و سپس همین PR. آزمونِ Compose ِ معناها (خواندنِ واقعیِ TalkBack) هنوز نیست — همان مانعِ `ui-test` ِ آفلاین.
  قدمِ بعدی: روی گوشیِ واقعی با TalkBack ضبطِ زدنی و «یک جا بالاتر» را بشنوید؛ `VoiceBar` ِ رابطِ قدیم (`ui/screens/ShopChat.kt`) دست نخورد.
### ۱۴۰۵/۰۸/۱۳ — شاخهٔ `claude/gaps27-delivery` (پیک و تحویل، DLV — «پیک … دقیق بچین»؛ روی main پس از #918؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: امانتِ نقدِ پیک — «نقدِ موردِ انتظار» ِ مأموریتِ زنده حالا بدهیِ امروزِ سفارش است (`server/src/lib/delivery-due.js`، صدا از
  `assign`، `order-amend.applyIn`، `order-add-items` و «paid» ِ `payment-intents`): پرداختِ حساب‌پی پس از سپردن ⇒ پیک نقدی نمی‌خواهد (پیش‌تر
  همان جمع را دوباره می‌گرفت)، ویرایش/افزودنِ قلم ⇒ جمعِ تازه. آزمون: `courier-cash-custody.test.js` ۵/۵ (بی پیاده‌سازی ۳ سرخ) + دو بار زدنِ
  «تحویل شد» و تسویهٔ هم‌زمان (یک سند، نقد یک بار به دکان)؛ پرونده‌های پیک/تحویل/سفارش/پرداخت ۲۲۰/۲۲۰ (PGlite محلی). ممیزیِ ۱۲ ردیفِ PARTIAL ِ DLV با
  CI ِ main ‏e90cc55d: DLV-11، ۱۲، ۱۳، ۱۴، ۱۵، ۱۹ ⇐ IMPLEMENTED-VERIFIED؛ «مانده» ِ بقیه کوتاه و صادق (STATUS، یادداشتِ «دورِ ۲۷»). بی مسیرِ تازه، بی مهاجرت.
- مانده: DLV-01 (وبِ پشتیبانِ پیک — تصمیمِ صاحب‌کار پس از §۴۰.۲)، DLV-02 (دستهٔ باز #923)، DLV-03 (موقعیتِ پیکِ آزاد — تصمیم + DLV-16)،
  DLV-05 (نقشه‌کشِ PMTiles در برنامه + گوشی؛ عصارهٔ شهر روی سرور)، DLV-06 (تماس با خودِ پیک — تصمیم)، DLV-16 (سرویسِ پیش‌زمینهٔ موقعیت — Play Console + گوشی).
- قدمِ بعدی: مرجِ #931 (`courier-cash-custody` ۵/۵ نام‌به‌نام در CI ِ همین PR، سرور 41762955559، هر دو پایگاه)؛ تصمیم‌های صاحب‌کار بالا.
### ۱۴۰۵/۰۸/۱۳ — شاخهٔ `claude/history-print-fixes` (کمبودهای بازبینیِ «تاریخچه» و چاپِ دوره؛ روی main پس از #927؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: (۱) برگهٔ «چاپ» پیش‌فرض «همهٔ فاکتورها» (اول در فهرست؛ «خلاصه» می‌ماند). (۲) کادرِ بالای تاریخچه با
  `V3HeroCard(wrapChips = true)`: تراشه‌ها با FlowRow به ردیفِ بعد، نوشتهٔ بلند چندخطی و کادر به‌جای بریدن بلندتر؛ ترتیب
  چاپ ⇐ بررسی ⇐ صندوق. اندازه‌گیری با وزیرمتن: «صندوق باز — … · نقدِ مورد انتظار: …» به‌تنهایی ۲۷۶dp در ×۱ و ۳۴۵dp در ×۱٫۳ است
  و ردیفِ گوشیِ ۳۶۰ فقط ۲۹۲dp، پس تنها کوچک‌کردنِ «چاپ» بس نبود. خانه و بقیهٔ کادرها همان یک ردیفِ ثابت‌اند (پیش‌فرضِ false).
  (۳) شمارهٔ حرف‌دار روی کاغذ: `PeriodSlip.numberTag` = LRM + «#شماره» + RLM فقط در راست‌به‌چپ (ایزوله نه: وزیرمتن
  LRI/PDI ندارد ⇒ جعبه در Java2D ِ ۱۷). (۴) عکس‌ها در `SalesShots`: `history-month`، `history-year`، `history-print`
  (چاپگرِ نمایشیِ ۸۰ با `demoPrinter`؛ برگه `internal` شد)، `history-360-large-text`؛ همهٔ عکس‌های تاریخچه تراشهٔ «چاپ» دارند.
  (۵) با فیلتر یک سطر در برگه: فیلتر در چاپ نیست و چند فاکتور چاپ می‌شود (`StrHistory.printIgnoresFilter`)؛ چیزِ چاپ‌شده همان.
  آزمون‌ها: `PeriodSlipTest` (چیدنِ دوجهتهٔ سطر با `java.text.Bidi`)، `HistoryPrintWiringTest` ِ تازه. `desk-check`: هسته ۱۳۰۹/۰ سرخ؛
  کامپیوتر ۳۰۴۶ با همان ۱۳ سرخِ محلیِ `ui.theme` (`ConnectorKt`، نبودِ androidx.collection).
- نیمه‌کاره: CI و عکس‌های «عکس از همهٔ بخش‌ها» (برنامهٔ کامپیوتر این‌جا اجرا نمی‌شود؛ ۳۶۰/×۱٫۳ را فقط آن عکس نشان می‌دهد). مرج نشد.
- قدمِ بعدی: دیدنِ چهار عکسِ تازه (روشن/تیره × فارسی/انگلیسی)؛ چاپِ واقعیِ یک سفارشِ اینترنتی روی ۵۸ و ۸۰.
### ۱۴۰۵/۰۸/۱۳ — شاخهٔ `claude/gaps26-staff` (خانوادهٔ STF؛ روی main ‏e90cc55d پس از #918؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: دو یافتهٔ امنیتی بسته شد. (۱) **ویرایشِ v1 سقف را دور می‌زد** (STF-03): شاگردِ بی تخفیف با `update` ِ `discount: {"$inc": n}`
  روی فاکتورِ ثبت‌شده تخفیف می‌داد و مدیرِ سقف‌دار/«ممنوع» تخفیف را پس از ثبت بالا یا جمع را پایین می‌برد، یا مبلغِ برگشت را بالا ⇐
  `server/src/lib/staff-edit-v1.js` در `v1Guard` (`v1RoleReason` هم به آن‌جا رفت تا `staff-approval.js` زیرِ سقفِ ۱۰۷۰ خط بماند)؛
  `staff-edit-v1.test.js` ۴/۴ (پیش از درمان ۳ سرخ). (۲) **مدیر پورسانتِ خودش را می‌ساخت** (STF-08): `PUT …/staff-time/commission-plan`
  فقط صاحب (`members.role`)، برنامه ویرایشگر را فقط به صاحب نشان می‌دهد؛ `staff-time.test.js` ۸/۸. و «نرخِ دسته» ِ پورسانت
  (`Commission.saleLines`، `Returned.category`، کادرِ نرخِ هر دسته) و تصمیمِ O-05 (ب، #932): پورسانتِ دوره زیرِ صفر نمی‌رود —
  CommissionTest ۱۰/۱۰، StaffReportTest ۳/۳. شاهدِ CI ِ main برای STF-01 و STF-07 در STATUS.
- نیمه‌کاره/مانده (در STATUS): STF-03 سقفِ فروشِ قرضی و قاعدهٔ قیمت در سرور (طرح: مجوزِ `sales.credit` با `maxAmount` روی `debtGiven` در درج،
  دلتای v3 و update ِ v1 — همین `staff-edit-v1.js` — همراه‌ها و پیش‌سنجیِ صندوق)؛ STF-01 نقشِ سفارشی؛ STF-06 و STF-09 دست نخوردند.
- تصمیم‌های صاحب‌کار: D-17 (STF-02، گرفته‌شده: پس از O-15)؛ نامِ کاربرِ صندوق در سرور (STF-04)؛ زدنِ خودِ کارمند تا ۷ روز عقب (STF-07).
- قدمِ بعدی: CI و مرجِ همین PR؛ سپس `sales.credit` (P0) یا نقشِ سفارشی.
### ۱۴۰۵/۰۸/۱۳ — شاخهٔ `claude/decided-rows-1` (ردیف‌های گروهِ «الف» ِ تصمیم‌ها؛ روی #932؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: در `STATUS.md` ستونِ «مانع» و متنِ «منتظرِ تصمیم» ِ ۴۲ ردیفِ گروهِ الف به «گرفته‌شده ۱۴۰۵/۰۸/۱۳» با شناسهٔ تصمیم برگشت؛
  وضعیتِ هیچ ردیفی با تصمیمِ تنها بالا نرفت. IMG-08 و IMG-PRO-10 بسته ماندند (فقط گزینهٔ ب ِ D-16). شش ردیفِ مالِ دستیارهای دیگر
  دست نخوردند: CHAT-02، CHAT-06، CHAT-12، CHAT-13 (`claude/gaps25-chat`)، DLV-06 (`claude/gaps27-delivery`) و STF-08 (`claude/gaps26-staff`).
  `BLOCKERS.md`: وضعِ D-03، D-05، D-15، D-16، D-17، D-23، D-24، D-26 و پنج ردیفِ «موانعِ دیگر». ساخته شد (آزمونِ سرخ پیش از کد):
  **D-03** دورهٔ آزمایشیِ دکان ۱۴ روز (`TRIAL_DEFAULT`، مهاجرتِ **۲۱۴** فقط ۷ ِ کاشته ⇒ ۱۴، `PlansModel`)؛ **D-23** `CostPolicy` پیش‌فرض و
  مصوب = میانگینِ موزون با `costApproved` (RPT-03)؛ **O-08** QR ِ فاکتور و پیگیریِ شکایت یک سال، پشتِ همان پرچمِ خاموشِ
  `shop.storefront.link_expiry` (`lib/paper-links.js`، TEN-03)؛ **D-24** `restore-drill` با هدفِ RPO ۲۴ و RTO ۱۲ ساعت (کدِ خروجِ ۲، OPS-02@29 ⇒ PARTIAL).
- مانده: D-16 (پردازشِ محلیِ عکس)، D-26 (۲۴ ساعتِ کارهای حساسِ کارمندِ برداشته‌شده؛ با `claude/gaps26-staff` هماهنگ شود)، O-06 (رزروِ #429)،
  O-09 (سنجهٔ «دستگاهِ صاحب هرگز بسته نمی‌شود» هنوز نیست؛ آزمونِ امروز سومین دستگاهِ خودِ صاحب را می‌بندد)، O-10/O-09 روشن کردنِ پرچم
  پس از خبر به دکان‌ها، O-14 متنِ ۱۶ صفحهٔ راهنما (فارسی، انگلیسی، پشتو). اجرای مهاجرتِ ۲۱۴ روی سرورِ زنده همراهِ استقرار.
- قدمِ بعدی: پس از مرجِ #932 همین PR؛ سپس D-16 پشتِ کلیدِ خاموش (زنجیرهٔ محلی) و D-26 با دستیارِ کارمندان.
### ۱۴۰۵/۰۸/۱۳ — شاخهٔ `claude/license-key-guard` (O-18، پیش‌نیاز: کلیدِ مجوز بی‌صدا ساخته نمی‌شود؛ روی main پس از #932؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: `server/src/lib/license.js` در production (و بی NODE_ENV) دیگر کلیدِ تازه نمی‌سازد. بی کلید ⇒ ۵۰۳ ِ `license_key_missing` روی
  `/api/license/*`، مجوزِ پمپ و کدِ آفلاین، و یک خطِ `[license]` در راه‌اندازی (`index.js`) با منبع و kid؛ `/health` و `/ready` سالم می‌مانند.
  ترتیب: `LICENSE_PRIVATE_KEY` (یک‌خطیِ `\n` هم) ⇐ `LICENSE_PRIVATE_KEY_FILE` ⇐ `app_config`. کلیدِ خراب ⇒ `license_key_invalid`، بی برگشت.
  کلیدِ محیط که با کلیدِ پیشینِ پایگاه‌داده نمی‌خواند ⇒ یک ⚠️. دو بارِ نخستِ هم‌زمان دیگر دو کلید نمی‌سازند. ابزارِ
  `server/scripts/license-key.js` (`export` به فایلِ ۶۰۰ و `status`؛ هرگز چاپ و هرگز ساختن). `docker-compose.yml` و `.env.example` متغیر را دارند.
  سه گامِ صاحب‌کار در O-18؛ RULES «کلیدها»، OWNER-ACTIONS §۳ و DISASTER-RECOVERY هم.
  دورِ دوم (خواستِ هماهنگ‌کننده): سنجش فقط کلیدِ عمومی می‌خواهد (بی کلیدِ خصوصی از ردیفِ `license_public_key`؛ کدهای فروشگاه، پمپ ۱/۲ و
  مجوز)، و `license-key.js generate <فایل>` برای سرورِ کاملاً نو (هر ردِ کلید یا کد/مجوزِ پیشین ⇒ نمی‌سازد). آزمون: `license-key-guard.test.js`
  (۱۱) و آزمون‌های مجوز، اشتراک، کدِ آفلاین و پمپ سبز.
- مانده: جابه‌جاییِ واقعی کارِ صاحب‌کار است. پاک کردنِ ردیفِ `license_private_key` روی PGlite ابزار ندارد. F0-28 و SUB-04 تا جابه‌جایی همان‌اند.
- قدمِ بعدی: پس از «O-18 انجام شد» ِ صاحب‌کار، F0-28 و چک‌لیستِ `SECURITY-AUDIT-fa.md` (بخشِ H) تیک بخورند. پیش از هر چرخاندن، برنامه‌ها
  `LICENSE_PUBLIC_KEYS` (چند کلید) بگیرند.
### ۱۴۰۵/۰۸/۱۳ — شاخهٔ `claude/gaps28-pos` (کمبودهای PARTIAL ِ صندوق، دورِ ۲۸؛ روی main پس از #923؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: **POS-40** سرور «اصلاحِ روشِ پرداخت» را می‌سنجد (`lib/till-guard.js` از سرِ `doc-roles.judgeV3`، پرچمِ خاموشِ `shop.sync.pay_fix`): شکل،
  فاکتورِ لغوشده/اینترنتی/برگشت‌خورده، بیش از ماندهٔ روش پس از اصلاح‌های سرور (`pay_fix_exceeds`) و مجوزِ `sales.pay_fix` با برگهٔ امضاشده؛ برنامه برگهٔ
  `ApprovalDialog` را در `AuditEntry.approval` می‌فرستد. **POS-26/27** بازپرداختِ سفارشِ ویژه/امانی بیش از پولِ نگه‌داشته ⇒ `refund_exceeds_held` (پرچمِ خاموشِ
  `shop.sync.refund_cap`). آزمون: `till-guard.test.js` (PGlite ۱۰/۱۰؛ بی قلاب ۶ سرخ)، `PayFixFlowTest`. ردیف‌های نیازمندِ دستگاه (POS-03، ۲۹، ۳۰، ۳۱، ۴۲) و
  تصمیمِ صاحب‌کار (POS-01، ۰۲، ۰۴، ۱۶ شعبه، ۱۸، ۲۱) در ستونِ «مانع» نشان شدند؛ POS-16 «دیدن پیش از افزودن» از راهِ قیمت‌پرسی (S17) بود. بی مسیر و مهاجرتِ تازه.
- نیمه‌کاره: CI و مرجِ همین PR. قدمِ بعدی: روشن کردنِ `shop.sync.refund_cap` (بی پیش‌نیاز در برنامه) و پس از همه‌گیر شدنِ (2.14.5) `shop.sync.pay_fix`؛
  «روزِ بسته» ِ اصلاح در سرور؛ تصمیمِ صاحب‌کار دربارهٔ سنجشِ قیمتِ قلم در سرور (POS-21).
### ۱۴۰۵/۰۸/۱۴ — شاخهٔ `claude/evidence-14` (ممیزیِ شاهدِ CI، دورِ ۱۴؛ روی main پس از #923؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: ردیف‌هایی که دستهٔ ۲۱ (#923، 62b5ba40) دست زد (DLV-02، S70) و دو ردیفی که آزمونشان را عوض کرد (LOC-06، HW-13) با CI ِ main سنجیده شدند:
  «سرور» 41770438952، «فروشگاه من — کامپیوتر» 41770438997 و «توحید بومی» 41770438991، همه سبز. `CourierCallV3Test`، `MissionAddressTest`،
  `LocalNetworkManifestTest`، `DariCoverageTest` و `DariTextTest` در هر دو کارِ Gradle اجرا شدند (بی `@Ignore`) ⇐ شاهد در ردیف نوشته شد. **هیچ ارتقایی نشد**:
  هر چهار بازماندهٔ دیگر دارند (دستگاهِ واقعی و نمای جدولیِ تبلت؛ رابطِ S70؛ اندروید ۱۷؛ صفحه‌های بی دری). هیچ ردیفِ IMPLEMENTED-VERIFIED نقض نشد
  (از e90cc55d هیچ آزمونی پاک، جابه‌جا یا skip نشد). جدول بی تغییر: ۳۸۴ / ۳۰۸ / ۳۴ / ۱۲ / ۲۶. فقط STATUS و HANDOFF.
- نیمه‌کاره: نامِ آزمون‌های سرورِ `market-units`، `market-same-units` و `self-delete` در CI دیده نشد: `get_job_logs` فقط ۵۰۰۰ سطرِ پایانی می‌دهد و
  `productionresultssa30.blob.core.windows.net` (گزارشِ کامل و مصنوعِ `test-evidence-*`) در این سیزن ۴۰۳ ِ پراکسی گرفت؛ فقط شمار (+۱۲، ۰ شکست) ثبت شد.
  دستهٔ ۲۲ (#934) هنوز باز است و در این دور نیست.
- قدمِ بعدی: پس از مرجِ #934، دورِ ۱۵ برای ردیف‌های #928 (وارسیِ سرتاسریِ سفارشِ آنلاین) و #930 (`HomeTilesTest`)؛ اگر میزبانِ گزارش باز شد، نام‌به‌نامِ S70 هم.
### ۱۴۰۵/۰۸/۱۳ — شاخهٔ `claude/owner-decisions-bigtech` (سند: تصمیم‌های صاحب‌کار به روشِ شرکت‌های بزرگ؛ روی main پس از #918؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: صاحب‌کار گفت «ببین شرکت‌های بزرگ فناوری چه می‌کنند». `docs/OWNER-DECISIONS-fa.md` از نو چیده شد و هیچ شناسه یا متنی
  پاک نشد. ۳۹ مورد داشت: ۳۱ تا، ۶ موردِ «بازِ دیگر» و دو موردِ تازهٔ Play (O-20 حساب و کشور؛ O-21 فروشِ اشتراک در Play). این‌ها در سه گروه رفتند.
  **الف (۲۲، گرفته‌شده و قابلِ برگشت، با دلیلِ Shopify/Square/Stripe/Amazon/Google/Apple):** D-16، D-24، D-23، D-26، D-05، D-17، D-15،
  D-03 (۱۴ روز)، O-03 تا O-16 به جز O-15، و O-21. **ب (۱۳، فقط صاحب‌کار، با گام‌های ساده):** D-09 (حسابِ پیامک و سقف؛ راه = گزینهٔ ب)،
  O-01، D-08، O-15، D-02 (Play App Signing + کلیدِ بارگذاریِ تازه)، O-18، O-02، O-17، O-19، پایدار، O-20، D-01 و D-06.
  **ج (۴):** D-11، D-25، D-10 و واژه‌های دری.
  بخشِ تازهٔ «خلاصه برای صاحب‌کار» بالای سند فقط ب و ج را دارد، به ترتیبِ اینکه چقدر کار را باز می‌کنند. ۴۹ ردیف دیگر منتظرِ تصمیم
  نیستند و ۴۶ ردیف پشتِ ب و ج مانده‌اند. هشت تصمیمِ اخیرِ Claude هم ثبت شد: پرداخت پس از پذیرش برای دکانِ تازه، شمارهٔ مشتری در
  سرور، لمسِ UIKit، FCM پشتِ کلید، بتا خودکار و پایدار با اجازه، کاشیِ اولِ «سفارش‌های آنلاین»، هشدارِ فاکتور (#928) و
  `shop.orders.subscribers_only` خاموش.
- مانده: `STATUS.md` و `BLOCKERS.md` هنوز ردیف‌های گروهِ الف را «منتظرِ تصمیم» می‌نویسند. پیوندِ `FIREBASE-SETUP-fa.md` (#922) و
  `GOOGLE-PLAY-fa.md` (#921) تا مرجِ همان PRها روی main باز نمی‌شود.
- قدمِ بعدی: یک PR ِ ممیزی ستونِ «مانع» ِ ردیف‌های الف را به «گرفته‌شده ۱۴۰۵/۰۸/۱۳» برگرداند. ساختنِ الف‌ها از پرردیف‌ترین (D-16، D-24،
  D-23) شروع شود؛ O-04 طبقِ همان تصمیم پس از پایداریِ برنامهٔ مدیریت می‌آید و کلیدهای پرخطر پیش‌فرض خاموش بمانند. جوابِ صاحب‌کار به ب و ج در همین سند ثبت شود.
### ۱۴۰۵/۰۸/۱۳ — شاخهٔ `claude/evidence-13` (ممیزیِ شاهدِ CI، دورِ ۱۳؛ روی main پس از #918؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: ردیف‌هایی که مرج‌های پس از دورِ ۱۲ (#911) دست زدند با CI ِ main سنجیده شدند. #912/#916 (آیفون) هیچ ردیفی را دست نزدند و آزمونِ
  سرتاسریِ آیفون فقط در `ios-spike` ِ PR اجرا می‌شود (شاهدِ main ندارد)؛ دستهٔ #918 (e90cc55d) فقط PRM-15، CRM-15، S86 و DATA-04 را دست زد.
  آزمون‌های هر چهار در «سرور» 41754869669 (PostgreSQL، PGlite، «زیرِ Electron»؛ نام‌به‌نام «ok»)، «کامپیوتر» 41754869731 و «توحید بومی»
  41754869575 سبزند ⇐ شاهد در ردیف نوشته و «شاهدِ CI» از «مانده» برداشته شد، ولی **هیچ ارتقایی نشد**: هر چهار بازماندهٔ دیگر دارند.
  ردیفِ کهنه درست شد: DATA-04 و DATA-08 «#586 (منتظرِ مرج)» و «FIXED-PENDING-MERGE» داشتند؛ #586 در 8cbf3b8e مرج شده است.
  هیچ ردیفِ IMPLEMENTED-VERIFIED نقض نشد (از 9e28d1eb هیچ آزمونی پاک، جابه‌جا یا skip نشد). جدول بی تغییر: ۳۸۴ / ۳۰۷ / ۳۵ / ۱۲ / ۲۶. فقط STATUS و HANDOFF.
- نیمه‌کاره: CI و مرجِ همین PR. دستهٔ ۲۱ (#923: DLV-02 و S70) هنوز باز است و در این دور نیست.
- قدمِ بعدی: پس از مرجِ #923 و سبزیِ CI ِ main ِ آن، دورِ ۱۴ برای DLV-02 (دکمهٔ تماسِ پیک، #917) و S70.
### ۱۴۰۵/۰۸/۱۳ — شاخهٔ `claude/push-fcm` (اعلانِ سفارش با برنامهٔ بسته از راهِ Firebase؛ تصمیمِ O-01؛ روی main پس از #911؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد (همه آماده و **خاموش**؛ بی کلید هیچ رفتاری عوض نمی‌شود): **سرور** — `lib/push.js` کلید را از `FCM_SERVICE_ACCOUNT_FILE`/
  `FCM_SERVICE_ACCOUNT` هم می‌خواند، بی کلید یک بار در لاگ می‌گوید و هیچ نمی‌فرستد، توکنِ مرده را با دلیل کهنه می‌کند (`stale_reason`)؛
  `routes/shop-push.js` (`/api/shop/push/config|devices`، پرچمِ تازهٔ `shop.device_push`، سطلِ IP پیش از احراز، SUB-10: FREE)؛ برداشتنِ
  عضو، خروج، «خروج از همه» و از کار انداختنِ دستگاه ⇒ توکن کهنه؛ صفِ `order-notify` دو رویدادِ یک‌بارهٔ «پرداخت شد» و «مشتری لغو کرد»
  گرفت؛ پیام بی دادهٔ مشتری (شماره و شمار). مهاجرتِ ۲۱۳ + rollback. آزمون: `push-fcm` ۶/۶ (FCM ِ ساختگی)، `order-notify`، `push-delivery`،
  `paid-routes-entitlement`، `route-authz-inventory`، `shop-route-inventory` سبز. **اندروید** — `firebase-messaging` (BoM 33.7.0)؛ افزونهٔ
  google-services فقط با `app/google-services.json`؛ `data/push/DevicePushAndroid.kt` (مشترک) + `FcmPushAndroid.kt` (فقط اندروید)، `core/push/PushPolicy`
  (+ آزمون)؛ «تنظیمات ← سفارشِ آنلاین» حالِ پوش و دکمهٔ «اجازهٔ اعلان». CI: `android-native/tools/google-services.sh` با secret ِ
  `GOOGLE_SERVICES_JSON` (نبودش ساخت را نمی‌شکند). راهنمای صاحب‌کار: `docs/FIREBASE-SETUP-fa.md`؛ O-01 «تصمیم گرفته شد».
- نیمه‌کاره: ساختِ APK ِ اندروید این‌جا ممکن نبود (بی Android SDK) — کامپایلِ Firebase و دروازهٔ حجمِ APK (±۵٪، PR فایلِ ساخت را عوض کرده)
  فقط در CI ِ «توحید بومی» سنجیده می‌شود. آیفون فقط درز دارد (`PushPlatform`؛ APNs پس از O-02).
- قدمِ بعدی: صاحب‌کار گام‌های `docs/FIREBASE-SETUP-fa.md` را برود؛ سپس آزمون با گوشیِ واقعی و ثبتِ NOTIF-02 تا ۰۵، NOTIF-12، NTF-05، ORD-02 و S36.
### ۱۴۰۵/۰۸/۱۳ — شاخهٔ `claude/dari-real-3` (دریِ واقعی، دورِ ۳؛ پشته‌ای روی `claude/dari-real-2` ‏#919 ِ هنوز باز؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: ۱۰۱۲ نوشتهٔ خامِ زندهٔ `ui/screens` به هشت جدولِ تازه رفت (فارسی بایت‌به‌بایت همان — هر پرونده با بازسازیِ متن از جدول
  با نسخهٔ پیشین سنجیده شد؛ انگلیسی و پشتو تازه): `DeskCustomers` (`StrCustomerPages`)، `StockBook` (`StrStockPages`)، `DevicesHw`
  (`StrDevicePages`)، `DeskProducts` (`StrProductPages`)، خرید: `PurchaseOrders`/`PurchaseBills`/`PurchaseSuggestView`/`DeskPurchasing`
  (`StrPurchasePages`)، `Promos` (`StrPromoPages`)، `HesabPayAccountSettings` (`StrPayPages`) و `ReportFresh` (`StrReportPages`)؛ ۳۵۲ جا
  جملهٔ جدول‌های پیشین دوباره به کار رفت. ۱۹ نوشته داده است («// داده:»: برگهٔ اکسل و نامِ پروندهٔ خروجی، واحدِ پیش‌فرض، یادداشتِ
  ذخیره‌شده، دلیلِ پیش‌فرضِ سابقه، برچسبِ نمونهٔ چاپی، سنجیدنِ «همیشه»). برچسبِ زبانه‌ها و صافی‌ها (`enum`) هر بار خوانده می‌شوند.
  دری: واژهٔ تازه نه — فهرست به سقفِ ۶۴ ِ `DariTextTest` رسیده؛ «میانگین ⇐ اوسط» و «بیشینه ⇐ حداکثر» پیشنهاد برای صاحب‌کار
  (`DARI-REVIEW-fa.md` §۶). آزمون: `DariCoverageTest` (سقفِ
  متنِ خامِ `ui/screens` ۳۶۳۳ ⇐ ۲۶۰۲، نگهبانِ ۱۰ پروندهٔ دورِ ۳، کفِ پوششِ تازه، نمونه‌های بایت‌به‌بایت).
- نیمه‌کاره: بقیهٔ `ui/screens` (۲۶۰۲): نمایهٔ جست‌وجو و کلیدهای `SettingsLayout` (۲۸۷، داده)، صفحه‌های مرده (`Storefront`، `DeliveryCash`،
  `Orders`، `Reports`…)، و از صفحه‌های زنده `PromoText`، `Servers`، `TeamStaff`، `ImageBankPicker`، `EasySetupAndroid`، `ImageBulkImport`؛
  ستون‌های `DayEndModel` (صفحه و خروجی) ⇐ تصمیمِ صاحب‌کار؛ `StockModeSection` ِ صدا‌زده‌نشده دست نخورد.
- قدمِ بعدی: دورِ ۴ با همان ابزار (spec ⇐ جدول، بازسازی و سنجشِ فارسی، «// داده:»)؛ بازبینیِ صاحب‌کار روی فهرستِ دری و تصمیمِ سقفِ واژه‌ها.
### ۱۴۰۵/۰۸/۱۳ — شاخهٔ `claude/dari-real-2` (دریِ واقعی، دورِ ۲؛ روی main پس از #910؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: متنِ خامی که دورِ ۱ بیرون گذاشته بود به جدول رفت (فارسی بایت‌به‌بایت همان، انگلیسی و پشتو تازه): همهٔ `ui/components`
  (۷۶)، صفحه‌های قدیمیِ `ui/screens` که نسخهٔ ۳ صدا می‌زند (فروش و چاپ، دریافتِ قرض، برگشت، دفترِ قرض، دستورِ تحویل، جمع‌آوری،
  نقدِ پیک‌ها، پایانِ روز؛ جدول‌های `StrSalePages`/`StrReturnPages`/`StrDebtPages`/`StrOrderPages`/`StrShellPages`، ۲۴۲ جا جملهٔ
  همان جدول‌های نسخهٔ ۳)، یاورهای `OwnerReportPlan`/`StorefrontEtaApi`/`PromoGuardApi` (۴۹، در `StrSettingsPages`)، نامِ ۹ بخشِ
  تنظیمات (`SettingsLayout.labelOf`؛ کلیدِ فارسی همان) و ۱۳ خطای ورودیِ `CourierQueue` (ثابتِ `CourierInputText` ِ هسته ⇐
  `StrCourierApp`). دری: ۹ واژه (آدرس، قبل از، کوشش، ختم …) و ۵ جملهٔ کامل، همه «نیاز به تأییدِ صاحب‌کار» در `DARI-REVIEW-fa.md` §۲ و §۵.
  آزمون: `DariCoverageTest` (نگهبانِ متنِ خام روی `ui/components`، پرونده‌های جابه‌جاشده و یاورها، سقفِ چرخ‌دنده‌ایِ متنِ خامِ
  `ui/screens`، کفِ پوششِ نوشته‌های جابه‌جاشده، خطای صفِ پیک)، `DariTextTest`، `dari-text.test.js`. پوشش: کارهای اصلی ۱۵٫۵٪ ⇐ ۱۶٫۵٪ (کف ۱۶)،
  همه ۱۳٫۸٪ ⇐ ۱۵٫۰٪، نوشته‌های جابه‌جاشده ۰ ⇐ ۱۴٫۸٪ (کف ۱۴)، متنِ خامِ `ui/screens` ۴۰۳۲ ⇐ ۳۶۳۳. `desk-check`: هسته ۱۳۰۶/۰؛ کامپیوتر ۳۰۴۲ آزمون، جز ۱۳ ِ `ui.theme`
  دو سرخ (`DariWordsTest`: «نشانی‌هاست»؛ `DesktopPhrasesTest`: عبارتِ «سیم» ⇐ «چاپگر سیستم») که رفع و با ۱۲۵ آزمونِ مربوط دوباره سبز شد.
- نیمه‌کاره: بقیهٔ `ui/screens` (سقف ۳۶۳۳ نوشته: `DeskCustomers`، `DeskProducts`، `StockBook`، `DevicesHw`، خرید، تخفیف‌ها، گزارش‌ها؛
  نمایهٔ جست‌وجوی تنظیمات و کلیدهای `SettingsLayout` داده‌اند)؛ ستون‌های `DayEndModel` به خروجی هم می‌روند ⇐ تصمیم لازم؛
  «ناحیه/دروازه» ِ `MissionView` در هسته.
- قدمِ بعدی: دورِ ۳ همان روش (سطرِ داده با «// داده: دلیل»، پرونده به `movedScreens`، سقف پایین)؛ بازبینیِ صاحب‌کار روی فهرست.
### ۱۴۰۵/۰۸/۱۳ — شاخهٔ `claude/order-flow-e2e` (سنجشِ سرتاسریِ جریانِ سفارشِ آنلاینِ #910 با شاهد؛ روی main پس از #918؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: `server/test/order-flow-e2e.test.js` (۵ آزمون، درهای واقعی پشتِ هم): ویترین ⇐ صفِ فروشنده (`summary` + برگهٔ «تازه»)
  ⇐ «قبول» ِ دوضربهٔ هم‌زمان (یک گذار) ⇐ کارتِ «پرداخت» (نقد + حساب‌پی، با خواندنِ دوباره) ⇐ «نقد» ِ دوضربه (یک رویداد) ⇐ فاکتورِ
  یک‌ضرب ⇐ پیک (سپردن، تحویل با نقد ⇒ `cash_collected`، تسویه ⇒ `cash_settled`)؛ تغییرِ قیمت میانِ ثبت و قبول (پرداخت، قصدِ حساب‌پی و
  قلم‌های فاکتور همان قیمتِ سفارش)؛ ردِ فروشنده (کارتِ بسته؛ `pay-choice`، `/pay` و فاکتور ۴۰۹)؛ حساب‌پیِ دوضربه با دو کلید (یک قصد)؛
  فروشندهٔ آفلاین (سفارشِ «با تأخیر» بی مهلتِ قبول، خودکار رد نمی‌شود؛ برخطِ بی‌پاسخ ⇒ `auto_rejected`). کاتلین: `OrderFlowTest`
  (ردشده/لغوِ مشتری بی دکمه؛ `closesOnlineChoice`)، `OrderFulfillmentTest` (قیمتِ دفترِ عوض‌شده ⇒ فاکتور با قیمتِ سفارش).
- رفعِ کوچک: «بسته شد و فاکتور» درست پس از «قبول» (پیش از انتخابِ مشتری، `awaiting_choice`) بی‌صدا حساب‌پیِ مشتری را می‌بست
  (`order_invoiced`)؛ حالا بخشِ «فاکتور» هشدار می‌دهد (`StrOrderFlow.invoiceBeforeChoice`، `OrderFlow.closesOnlineChoice`). رفتار همان.
- تصمیمِ صاحب: (۱) دکمهٔ فاکتور تا انتخابِ مشتری بسته بماند؟ (۲) `shop.orders.subscribers_only` هنوز خاموش ⇒ دکانِ آزمایشی
  سفارش می‌گیرد (حساب‌پی فقط مشترک).
- نیمه‌کاره: آزمونِ سرتاسریِ دستگاهِ اندروید نیست (فقط `CoreFlowsE2EIosTest` ِ CI ِ macOS)؛ FCM در شاخهٔ دیگر. قدمِ بعدی: CI و مرج.
### ۱۴۰۵/۰۸/۱۳ — شاخهٔ `claude/a11y-v3` (ممیزیِ دسترس‌پذیریِ رابطِ نسخهٔ ۳، WCAG 2.1 AA؛ روی main پس از #916؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد (کدِ مشترک، پس آیفون و کامپیوتر هم): دکمه‌های تک‌آیکنیِ بی نام («+» ِ کادرِ صندوق، پاک کردنِ رقمِ صفحه‌کلیدِ صندوق،
  بستنِ اسکن) و QR ِ حساب‌پی نام گرفتند؛ دستگیرهٔ کادرِ صندوق «صندوق، باز/بسته»؛ `V3Sheet` با `paneTitle`، عنوانِ سرخط و دستگیرهٔ
  «بستن» برای صفحه‌خوان؛ خطای `V3Message` ناحیهٔ زنده (WCAG 4.1.3)؛ `V3ListRow` کارهای پنهانِ زیرِ ردیف را تا کشیده نشده از
  صفحه‌خوان و Tab پنهان می‌کند و نگه‌داشتن «کارهای بیشتر» نام دارد؛ `V3ToggleRow` یک کلیدِ با حالت (نه دکمه + کلیدِ دوم)؛
  «حذف» ِ ردیفِ حسابِ مشتری کارِ سفارشی (کشیدنش جایگزینی نداشت)؛ باز/بستهٔ «تاریخچه» و پروندهٔ مشتری؛ «انتخاب‌شده» ِ کارتِ
  سفارش؛ «نیاز به توجه» ِ تبِ «جدید»؛ `selectableGroup` در تب‌ها. بریدن با نوشتهٔ درشت: عددِ کادرِ صندوق (×۱٫۳)، سرخطِ خانه
  (×۲)، نشانِ کاشیِ سفارش‌ها و تاریخِ ردیفِ حساب (`heightIn`/`widthIn` به‌جای اندازهٔ ثابت). واژه‌ها: `StrA11y`.
- نگهبان: `V3A11yGuardTest` (روی متنِ کد، بی صفحه): دکمهٔ تک‌آیکنیِ بی نام در `ui/v3` و `ui/components`، کشیدنِ بی کارِ سفارشی
  (فهرستِ جایگزین‌ها فقط کوتاه می‌شود)، نوشتهٔ یک‌خطی با پهنای ثابت، و سیم‌کشیِ اجزای مشترک. آزمونِ Compose ِ واقعی نشد:
  `ui-test` ِ ۱.۹.۳ با ساختِ آفلاینِ محلی (runtime ِ ۱.۷.۳) نمی‌خواند.
- نیمه‌کاره: مرجِ همین PR. قدمِ بعدی: «نگه دار و حرف بزن» ِ گفت‌وگو (`VoiceBar`) جایگزینِ صفحه‌خوان ندارد؛ کششِ ردیف‌های
  «نوارِ پایین» در تنظیمات فقط با برگهٔ «جای X»؛ آزمونِ Compose ِ معناها روی CI ِ کامپیوتر وقتی وابستگیِ `ui-test` آفلاین هم برسد.
### ۱۴۰۵/۰۸/۱۳ — شاخهٔ `claude/gaps24-partial` (کمبودهای PARTIAL، دورِ ۲۴؛ روی main پس از #918؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: **PRD-04/S05** — صندوقِ گوشیِ نسخهٔ ۳ بارکدِ کارتن را نمی‌شناخت (پس از #848 فقط `barcodeIndex`)؛ `data/PackScan` در `CartModel.scan`:
  کارتن ⇒ اندازهٔ بسته، با «واحدِ بزرگ در صندوق» قیمتِ لیستِ کارتن (کامپیوتر هم)، ردیفِ با قیمتِ دیگر نه جمع نه بی‌صدا عوض؛ قیمتِ لیستِ بسته کاهشِ
  ردیف نیست (`DiscountGate.reductionMinor`، POS-21). **INV-21** — سری و انقضای کارتن به دانه‌ها (FEFO، `ExpiryEngine.take`) و زودترین
  انقضای اجزا به بسته؛ خروجیِ بسته‌بندی محمولهٔ `ExpiryEngine.lots`. **RPT-13** — Enter در «امروز»، «بدهی‌ها» و «ضدتقلب» ِ «گزارشِ تازه»
  با جمعِ برابرِ ردیف (`ReportRowDrillTest`) و رفعِ ستونِ «فاکتور» ِ خالی در ریزِ «فروش». بی مسیرِ سرور، بی مهاجرت، بی تنظیمِ تازه.
  محلی: desk-check ِ کامل — core ۱۳۰۵/۱۳۰۵، کامپیوتر ۳۰۵۵ با همان ۱۳ سرخِ شناختهٔ `ui.theme`؛ جهش‌ها ۱۲ آزمون را سرخ کردند.
- `DeskSale` (کامپیوتر) هم همان `PackScan` را در اسکن و Enter دارد (`addPack`)؛ هانک‌ها دور از تغییرِ هم‌زمانِ دری (۲۸۶ و ۹۱۲).
- بیرون ماند از دستهٔ ۲۲ (#934) چون با #925 (سقفِ متنِ خامِ `ui/screens` ۲۶۰۲) ۲۶۳۴ می‌شد ⇐ دستهٔ ۲۲ ادغام شد و همهٔ نوشته‌های
  ریزِ گزارش (و ریزِ «فروش» و «بله/نه» ِ پیشین) به `StrReportPages` رفت؛ متنِ خامِ `ui/screens` ۲۵۹۳.
- نیمه‌کاره: CI و مرجِ همین PR #924. قدمِ بعدی: BOM ِ INV-21؛
  سری در سندِ `production` ِ `StockLedger`؛ ستونِ مقایسه برای برگه‌های فهرستیِ سند (RPT-13).
### ۱۴۰۵/۰۸/۱۳ — شاخهٔ `claude/crm15-server` (اجرای سرورِ `customers.contact`، CRM-15؛ روی main پس از #911؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: `server/src/lib/customer-contact.js` — بی مجوز (انباردار/جمع‌آور، یا لایهٔ نقش/کاربرِ صاحب) `GET /api/debtors[/:id]` شمارهٔ
  پوشیده (همان `ContactMask`) و بی نشانی؛ pull/Snapshot/حذف‌شده‌های راهِ قدیم، v1 (فقط در `routes/sync-v1.js`؛ `lib/sync-v1.js` دست نخورد)
  و v3 بی `phone`/`phone2`/`deliveryAddress`؛ صندوقِ تعارض پوشیده؛ push ِ همان اصل (v1، v3، راهِ قدیم، PUT ِ REST) فیلدِ تماسِ ردیفِ موجود را
  هرگز عوض/پاک نمی‌کند (`ignored`). شمارهٔ کاملِ سفارش/گفت‌وگو/واتس‌اپ و درهای فهرستِ سیاه و «سفارش‌های یک مشتری» =
  `orders.contact` **و** `customers.contact` (پیش‌فرضِ صاحب و مدیر همان). آزمون: `customer-contact.test.js` ۱۰/۱۰ (پیش از تغییر سرخ). CodeQL (`js/remote-property-injection`، ۴ هشدار در
  `lib/sync-v1.js` از راهِ `v1Guard`) ⇐ نگهبان‌ها دیگر opِ تازه از کلیدهای درخواست نمی‌سازند (در جا، کلیدِ ثابت) و نامِ ناامن رد.
- مانده (در STATUS ِ CRM-15): `phoneMasked` ِ ۴+۳ و نشانیِ سفارش/تحویل برای همهٔ `orders.view` (تصمیمِ صاحب، چون نمای جمع‌آور عوض
  می‌شود)، فهرست‌های پشتِ `settings.write` برای مدیرِ بسته، شمارهٔ ازپیش‌همگام‌شده روی دستگاه تا بازسازی، سابقهٔ دسترسی، چاپی، وب.
- قدمِ بعدی: CI و مرجِ همین PR؛ تصمیمِ صاحب دربارهٔ `phoneMasked` ِ سفارش برای نقشِ بی مجوز.
### ۱۴۰۵/۰۸/۱۳ — شاخهٔ `claude/dlv02-courier-call` (پسرفتِ DLV-02 در پیکِ نسخهٔ ۳؛ روی main پس از #910؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- تشخیص: خطِ «ناحیه · دروازهٔ رنگ» و دکمهٔ «تماس با مشتری» فقط در `CourierHomeLegacy.kt` بودند؛ دستهٔ ۱۳ (#855، 0a68b851) آن را
  بی جانشین پاک کرد و `landmarkFirst`/`callNumber`/`openDialer` بی صدازننده ماندند (CI سبز، چون هیچ آزمونی صدازننده نمی‌سنجید).
- انجام شد: کارتِ «مسیر» (به `CourierMissionCard.kt` جدا شد تا `CourierHome.kt` زیرِ سقف بماند؛ سقف ۹۵۹ ⇐ ۸۶۶) و جزئیاتِ
  مأموریت هر دو را دارند؛ دروازه همان پیشین (`CourierExtras.canCall` = پرچمِ `shop.delivery_courier_call` + `CourierFlow.canCall`)؛
  `landmarkFirst` برچسبِ ناحیه/دروازه را از `StrCourierApp` می‌گیرد (پیش‌فرض همان فارسی)؛ یک تماس در هر لحظه. آزمون: `CourierCallV3Test` ⇒ ۷/۷
  (صدازننده از `AppRoot` با `SourceGraph`، دروازه، رفتار)، `MissionAddressTest` ⇒ ۵/۵؛ desk-check: هسته ۱۳۰۶/۱۳۰۶، کامپیوتر ۳۰۴۴ با همان ۱۳ ِ `ui.theme`؛ عکسِ `phone-v3-courier-call*`.
  ⚠️ دیمنِ مشترکِ کاتلین زیرِ بارِ چند سیزن یک ساعت کامپایلِ core را نگه داشت؛ با `-Pkotlin.compiler.execution.strategy=in-process` ۱٫۵ دقیقه شد.
- نیمه‌کاره: گوشیِ واقعی، آیفونِ بی سیم‌کارت و شماره‌گیرِ کامپیوتر آزموده نشد؛ نمای جدولیِ تبلت فقط نشانه دارد (جزئیات همه را).
- قدمِ بعدی: در `ui/courier` چهار تابعِ دیگر هم بی صدازننده‌اند (`BagScanDialog`، `NearPinFlow`، `RouteSheet`، `RateDialog` در
  `CourierChecksUi.kt`)؛ کارشان در نسخهٔ ۳ با برگه‌های `CourierChecksV3.kt` هست، پس کدِ مرده‌اند نه پسرفت — پاک کردن یا نگه داشتن با صاحب‌کار.
### ۱۴۰۵/۰۸/۱۳ — شاخهٔ `claude/dari-real-2` (دریِ واقعی، دورِ ۲؛ روی main پس از #910؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: متنِ خامی که دورِ ۱ بیرون گذاشته بود به جدول رفت (فارسی بایت‌به‌بایت همان، انگلیسی و پشتو تازه): همهٔ `ui/components`
  (۷۶)، صفحه‌های قدیمیِ `ui/screens` که نسخهٔ ۳ صدا می‌زند (فروش و چاپ، دریافتِ قرض، برگشت، دفترِ قرض، دستورِ تحویل، جمع‌آوری،
  نقدِ پیک‌ها، پایانِ روز؛ جدول‌های `StrSalePages`/`StrReturnPages`/`StrDebtPages`/`StrOrderPages`/`StrShellPages`، ۲۴۲ جا جملهٔ
  همان جدول‌های نسخهٔ ۳)، یاورهای `OwnerReportPlan`/`StorefrontEtaApi`/`PromoGuardApi` (۴۹، در `StrSettingsPages`)، نامِ ۹ بخشِ
  تنظیمات (`SettingsLayout.labelOf`؛ کلیدِ فارسی همان) و ۱۳ خطای ورودیِ `CourierQueue` (ثابتِ `CourierInputText` ِ هسته ⇐
  `StrCourierApp`). دری: ۹ واژه (آدرس، قبل از، کوشش، ختم …) و ۵ جملهٔ کامل، همه «نیاز به تأییدِ صاحب‌کار» در `DARI-REVIEW-fa.md` §۲ و §۵.
  آزمون: `DariCoverageTest` (نگهبانِ متنِ خام روی `ui/components`، پرونده‌های جابه‌جاشده و یاورها، سقفِ چرخ‌دنده‌ایِ متنِ خامِ
  `ui/screens`، کفِ پوششِ نوشته‌های جابه‌جاشده، خطای صفِ پیک)، `DariTextTest`، `dari-text.test.js`. پوشش: کارهای اصلی ۱۵٫۵٪ ⇐ ۱۶٫۵٪ (کف ۱۶)،
  همه ۱۳٫۸٪ ⇐ ۱۵٫۰٪، نوشته‌های جابه‌جاشده ۰ ⇐ ۱۴٫۸٪ (کف ۱۴)، متنِ خامِ `ui/screens` ۴۰۳۲ ⇐ ۳۶۳۳. `desk-check`: هسته ۱۳۰۶/۰؛ کامپیوتر ۳۰۴۲ آزمون، جز ۱۳ ِ `ui.theme`
  دو سرخ (`DariWordsTest`: «نشانی‌هاست»؛ `DesktopPhrasesTest`: عبارتِ «سیم» ⇐ «چاپگر سیستم») که رفع و با ۱۲۵ آزمونِ مربوط دوباره سبز شد.
- نیمه‌کاره: بقیهٔ `ui/screens` (سقف ۳۶۳۳ نوشته: `DeskCustomers`، `DeskProducts`، `StockBook`، `DevicesHw`، خرید، تخفیف‌ها، گزارش‌ها؛
  نمایهٔ جست‌وجوی تنظیمات و کلیدهای `SettingsLayout` داده‌اند)؛ ستون‌های `DayEndModel` به خروجی هم می‌روند ⇐ تصمیم لازم؛
  «ناحیه/دروازه» ِ `MissionView` در هسته.
- قدمِ بعدی: دورِ ۳ همان روش (سطرِ داده با «// داده: دلیل»، پرونده به `movedScreens`، سقف پایین)؛ بازبینیِ صاحب‌کار روی فهرست.
### ۱۴۰۵/۰۸/۱۳ — شاخهٔ `claude/missing-rows-1b` (کمبودهای MISSING ِ بی مانع؛ روی main پس از #916؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: **S70** (MISSING ⇐ PARTIAL) — «همین کالا در دکان‌های دیگر» (`/api/market/same/:id`، پشتِ `market.product_details`) فقط با واحدِ هم‌سنج
  مقایسه می‌کند: `lib/market-units.js` (اندازهٔ بی حدس: پایه‌ها یا بسته با یک عدد؛ «کارتن» ِ بی عدد، دو عدد، «1,000»، سیر/پاو/من ⇒ null)،
  `perUnit`، `cheapest` (همان پایه، دست‌کم دو، روی همهٔ پیدا‌شده‌ها) و `compare`؛ ترتیبِ درونِ دکان با قیمتِ یک پایه. آزمون‌ها پیش از ساخت سرخ:
  `market-units.test.js` ۵/۵، `market-same-units.test.js` ۱/۱؛ همهٔ `market-*` و public-views ۸۰/۸۰ روی PGlite. بی مهاجرت، بی مسیرِ تازه، بی کدِ برنامه.
- ساخته نشد (دلیل): از ۳۵ ردیفِ MISSING فقط S70 بی تصمیم و بی وابستگیِ بیرونی بود. PAY-22 (QR ِ ملی): APS هنوز مشخصاتی منتشر نکرده و
  ساختن یعنی پروتکلِ حدسی (همان قاعدهٔ کارت‌خوان در RULES) — بهتر است BLOCKED-EXTERNAL شود. S121 (شارژِ سیم‌کارت) قراردادِ اپراتور می‌خواهد؛
  S122 و AI-09 مدلِ زبانی (O-16/D-16)؛ PUR-12/AI-10 موتورِ OCR (D-16)؛ GATE-NEW-03 دستگاه/شبکهٔ واقعی؛ بقیه در `OWNER-DECISIONS-fa.md`.
- نیمه‌کاره: رابطِ «همین کالا» (وبِ بازارچه یا برنامهٔ خرید) هنوز نیست؛ «واحدِ فروشِ دوم» و «بسته‌ها» ِ کالا در اندازه به کار نمی‌روند
  (ضریبِ نیامده در `detailsOf` پیش‌فرضِ ۱ می‌گیرد و «روشن» نیست). قدمِ بعدی: مرجِ همین PR؛ با رابطِ «همین کالا» همین `perUnit` نشان داده شود.
### ۱۴۰۵/۰۸/۱۳ — شاخهٔ `claude/play-readiness` (آمادگیِ Google Play؛ روی main پس از #916؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: ممیزیِ Play با منبعِ رسمی (هدفِ API 36 از ۳۱ اوت ۲۰۲۶، ۱۶KB، حذفِ حساب، اجازه‌های محدود، تأییدِ توسعه‌دهنده…) و
  چک‌لیستِ فارسیِ صاحب‌کار `docs/GOOGLE-PLAY-fa.md` (جوابِ فرمِ ایمنیِ داده، متن‌ها، نشانی‌ها، ریسک‌ها). `-Pstore=play`: قاعدهٔ بازار
  (`StoreChannel.storeManaged` ِ core به‌جای `== "bazaar"`) + `compileSdk/targetSdk` ِ ۳۶ فقط برای Play؛ کارِ CI ِ «نسخهٔ Google Play
  (AAB)» با `tools/play-check.py`. «حذفِ حساب»: برنامه (`profile/delete`، `DeleteAccountSheet`)، پورتالِ وب (`#delete-account`)،
  سرور `routes/account-deletion.js` + `lib/self-delete.js` روی `lib/user-delete.js`، پرچمِ `account.self_delete` پیش‌فرض خاموش
  (خاموش ⇒ درخواست به پشتیبانی؛ پمپ‌دار ⇒ ۴۰۹)، `privacy.html#delete-account` و پیوندِ «حریمِ خصوصی» در برنامه.
- ⚠️ یافته: کلیدِ `tohid-release.jks` در مخزنِ عمومی است (برای Play کلیدِ بارگذاریِ تازه)؛ تحریم و حسابِ Play، Play Billing برای اشتراک.
- نیمه‌کاره: CI و مرجِ همین PR (ساختِ AAB با compileSdk 36 روی AGP 8.7.3 فقط در CI سنجیده می‌شود). قدمِ بعدی: تصمیم‌های بخشِ ۱ ِ
  `GOOGLE-PLAY-fa.md`، روشن کردنِ `account.self_delete` پیش از Production.
### ۱۴۰۵/۰۸/۱۳ — آیفون گامِ ۱۰، شاخهٔ `claude/ios-app-phase10` (روی main پس از #910؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: `CoreFlowsE2EIosTest` ۷ ⇐ ۱۱ گام، همه با زدن روی صفحه در برابرِ سرورِ واقعی: **نسیه** (#906) — «نسیه» بی قرض‌دار «ثبت فروش»
  خاموش با پیام و زدنِ دکمهٔ خاموش هیچ نمی‌نویسد، «انتخابِ مشتری» ⇐ «قرض‌دارِ تازه» ⇐ نسیه با همان قرض‌دار (دفتر، بدهی، سرور)؛
  **کادرِ صندوقِ خانه** — کشیدنِ انگشت (لمسِ واقعی) ⇐ کادر با موجودیِ `CashShift`، «+» ⇐ باز کردنِ صندوق ⇐ ورودِ نقد ⇐ موجودی بالا رفت
  (دفتر و سرور)؛ **سفارشِ آنلاین** (#910) — `GET /order/new` ِ `ios/e2e/backend.mjs` (بخشِ سفارش و ویترین برای همان دکان، کالا، تأییدِ
  شماره، `checkout` ِ ویترین) ⇐ کاشی ⇐ صف ⇐ قبول ⇐ «بسته شد و فاکتور» ⇐ یک «فاکتور شد» ِ سرور و یک فروشِ `o-<سفارش>`. دری آخر رفت.
  بازخوردِ لمسیِ آیفون با تصمیمِ صاحب‌کار (HIG، فقط مولدهای UIKit): `ios/IosHaptics.kt` — موفق/خطا/تأیید/انتخاب/آستانه، همان معنای
  اندروید، هم برای `Haptics` هم `LocalHapticFeedback` ِ ریشه؛ «لرزش‌های سیستم» را UIKit رعایت می‌کند. `Phase10IosTest` (۳). شرح: §۰.۱۵.
- `ios-exclude.txt` همان ۱۰: `Haptics.kt` ِ اندروید زمان‌بندیِ خامِ `Vibrator` است و shim ِ هم‌نامش یا بی‌لرزش بود یا معنا را حدس می‌زد.
- اندروید، کامپیوتر و `core` دست نخوردند. محلی: klib و klib ِ آزمونِ iOS، `ios/shared` jvmTest، `backend.mjs` با جریانِ سفارش تا فاکتور.
- CI ِ macOS (PR #912، کارِ 126255834388): سبز در اجرای نخست — شبیه‌ساز ۳۶/۳۶، `CoreFlowsE2EIosTest` با هر ۱۱ گام، `Phase10IosTest` ۳/۳ (§۰.۱۵).
- نیمه‌کاره: مرجِ همین PR. قدمِ بعدی: «دریافت از دکان» و پرداختِ آنلاینِ سفارش (درگاهِ آزمایشیِ سرور)، حالتِ پیک.
### ۱۴۰۵/۰۸/۱۳ — شاخهٔ `claude/sec-order-flow` (بازبینیِ امنیتیِ جریانِ سفارشِ آنلاینِ #910 و پیش‌فرضِ «پرداخت پس از پذیرش»؛ روی main پس از #910؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد (هر کدام با آزمونِ سرخِ پیش از رفع در `order-flow.test.js`): (۱) «به فاکتور» فقط `orders.manage` می‌خواست و دکمهٔ برنامه
  را فقط برنامه با اجازهٔ ثبتِ فروش پنهان می‌کرد ⇒ جمع‌آور با تماسِ مستقیم «فاکتور شد» می‌نشاند و حساب‌پیِ مشتری را می‌بست؛ حالا
  `order-ledger.judgeOf().recordable` در سرور (۴۰۳ `permission_denied`). (۲) مسابقهٔ فاکتورِ نقدی و `/pay`: سنجشِ `/pay` بیرونِ قفل بود و
  سفارش فقط پس از پاسخِ درگاه «در انتظار» می‌شد ⇒ فاکتورِ نقدی + پرداختِ حساب‌پی روی یک سفارش؛ حالا قصدِ زنده فاکتور را می‌بندد و
  `/pay` درونِ تراکنشِ ساختنِ قصد زیرِ قفلِ ردیفِ سفارش دوباره می‌سنجد (`guardOnlinePayIn`). (۳) مهاجرتِ ۲۱۱ (سپرده از هماهنگ‌کننده):
  «پرداخت پس از پذیرش» برای دکانِ تازه روشن، موجود خاموش؛ آزمون‌های رفتارِ خاموش حالا `payAfterAccept: false` را صریح می‌گذارند؛
  `paymentStep` ِ پیگیری (حالا پیش‌فرضِ دکانِ تازه) به قراردادِ عمومیِ v1 رفت (`public-views.js`، `market-public-v1.json`، `PublicOrder.paymentStep`).
- سالم دیده شد: جداسازیِ دکان/سفارش در `pay-choice` (توکنِ HMAC ِ دکان+سفارش)، `/pay` پیش از قبول، دو فاکتورِ هم‌زمان (قفلِ ردیف)،
  دست‌کاریِ مبلغ، ماشینِ وضعیت (لغو فقط پیش از بیرون رفتن ⇒ برگشتِ دوباره نیست)، نقدِ پیک (`custody = courier`)، سقفِ IP پیش از
  احراز، XSS ِ `pay-step.js`.
- (۴) «دریافت از دکان» ِ پرداخت‌نشده: سرور هم مثلِ برنامه فاکتور را فقط همراهِ «مشتری گرفت» می‌پذیرد (از «بسته‌بندی‌شده» ۴۰۹
  `invoice_at_handover`؛ از «آمادهٔ دریافت» تحویل در همان تراکنش؛ زمانِ فاکتور = زمانِ تحویل) — خواستِ هماهنگ‌کننده، با آزمونِ سرخ.
- نیمه‌کاره: آزمون‌های مرورگر (`test:web-store`، `test:e2e`) و Gradle ِ `:core:jvmTest` این‌جا اجرا نشد (مرورگر نیست، صفِ Gradle) ⇒ CI.
- قدمِ بعدی: مرجِ همین PR پس از CI ِ سبز.
### ۱۴۰۵/۰۸/۱۳ — شاخهٔ `claude/gaps23-partial`، PR #915 (کمبودهای PARTIAL، دورِ ۲۳؛ روی main پس از #911؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: **DATA-04 / SEC-DATA-07** سرور جمعِ برگشت‌های هر فاکتور را با `finalTotal` + ۵۰ پول برای هر قلم می‌سنجد (`lib/return-sale-cap.js` از نگهبانِ `staff-approval.js`، v3 و v1، فاکتورِ همین دستهٔ v1 هم)،
  پشتِ پرچمِ خاموشِ `shop.returns.sale_cap` (`return-sale-cap.test.js`)؛ **S86** بازسازیِ مانده‌های موجودیِ سرور از دفتر (`stock.rebuild`، `npm run stock-rebuild`،
  پیش‌فرض فقط نمایش؛ `stock-rebuild.test.js`)؛ **PRM-15** «دادنِ اعتبارِ هدیه» در حسابِ مشتریِ گوشی (نسخهٔ ۳)، پشتِ `GiftCardPref` و تخفیفِ «مجاز» ِ همان مبلغ
  (`StoreCreditGrantTest`، `StoreCreditGrantFlowTest`). بی مسیرِ تازه، بی مهاجرت، بی تنظیمِ تازه. همه PARTIAL ماندند.
- نیمه‌کاره: CI و مرجِ همین PR. قدمِ بعدی: روشن کردنِ `shop.returns.sale_cap` پس از همه‌گیر شدنِ نسخهٔ دارای `ReturnShare` (#586)؛ بازسازیِ projection ِ کاتالوگ و سفارش (S86)؛
  همان دکمه در پروندهٔ مشتریِ کامپیوتر (`DeskCustomers` به سقفِ اندازه رسیده — اول تکه کردنش)؛ «اعتبارِ هدیه» با «درخواستِ تأیید» (PIN ِ مدیر).
### ۱۴۰۵/۰۸/۱۳ — شاخهٔ `claude/ci-evidence-12` (ممیزیِ شاهدِ CI، دورِ ۱۲؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: ردیف‌هایی که دسته‌های #900 (1eecbdaa) و #909 (9e28d1eb) دست زدند با CI ِ main سنجیده شدند (#910 ردیفی را دست نزد).
  **PRM-08** (`PromoBrandTest`، `PromoCoreTest`، `promo-core` ۷۲/۷۲: 41701433394، 41701433313، «سرور» 41701433334)، **PRM-13**، **DLV-09** و **MKT-11**
  (آزمون‌های نام‌برده: کامپیوتر 41714074283، «توحید بومی» 41714074719، «سرور» 41714074577 و `web-store-account`) ⇐ IMPLEMENTED-VERIFIED.
  جدول: ۳۸۴ / ۳۰۷ / ۳۵ / ۱۲ / ۲۶ (حدودِ ۷۰٫۴٪). فقط STATUS و HANDOFF.
- ⚠️ یافته: DLV-02 PARTIAL ماند — ناحیه/رنگِ دروازه در کارتِ پیک و دکمهٔ «تماس با مشتری» با `CourierHomeLegacy.kt` در دستهٔ سیزدهم
  (0a68b851) رفتند و به `ui/courier` ِ نسخهٔ ۳ نیامدند (`landmarkFirst`، `callNumber`، `openDialer` بی فراخواننده؛ CI سبز است).
- نیمه‌کاره: CI و مرجِ همین PR. قدمِ بعدی: آوردنِ ناحیه/دروازه و «تماس با مشتری» به کارتِ پیکِ نسخهٔ ۳ (DLV-02)؛ دورِ ۱۳ پس از مرج‌های بعدی.
### ۱۴۰۵/۰۸/۱۳ — شاخهٔ `claude/online-order-flow`، PR #910 (جریانِ کاملِ سفارشِ آنلاین: «چرا نمی‌رسد»، فاکتورِ یک‌ضرب، پرداخت پس از پذیرش؛ روی دستهٔ ۱۸ (#900)؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- تشخیص: زنجیره ویترین/بازارچه ⇒ `POST /store/:slug/checkout` ⇒ `online_orders` ⇒ برنامه (`/shop/orders/config` ⇒ `summary` هر ۲۰ثانیه فقط
  جلوی چشم؛ FCM در برنامه نیست). سرور هیچ سفارشِ ثبت‌شده‌ای را گم نمی‌کرد؛ باگ «خاموشیِ بی‌صدا» بود: پرچمِ `shop.orders`، کلیدِ
  اشتراک، پرچمِ `shop.storefront`، ویترینِ ساخته‌نشده/خاموش، بی کالای منتشر، `subscribers_only` ِ دورهٔ آزمایشی، تأییدِ شمارهٔ
  بی پیامک، و `shop.order_notify` ِ خاموش — هیچ‌کدام به صاحب گفته نمی‌شد (بخش کامل پنهان). حالا `arrival` ِ `/config`
  (`lib/order-arrival.js`) + کارتِ سرِ صف + «تنظیمات ← سفارشِ آنلاین»؛ هیچ دروازه‌ای باز نشد.
- انجام شد: `POST /shop/orders/:id/invoice` (یک بار زیرِ قفل، از «بسته‌بندی‌شده») + «به سبد / فاکتور» در جزئیات (`OrderFlowAndroid.kt`،
  همان `OrderFulfillment.fulfill` با زمانِ «فاکتور شد»؛ دریافتِ نقدی ⇒ همراهِ تحویل)؛ لغوِ سفارشِ فاکتورشده ⇒ لغوِ فروش
  (`cancelled` در `BACK_IN_SHOP`)؛ «پرداخت پس از پذیرش» (مهاجرتِ ۲۱۰، پیش‌فرض خاموش) با `pay-choice` و `store/pay-step.js` ِ دیربار.
  آزمون: `order-flow.test.js` (۷ از ۷ پیش از تغییر سرخ)، `web-store-paystep.mjs`، `OrderFlowTest`، `OrderFulfillmentTest`.
- نیمه‌کاره/تصمیم: پیش‌فرضِ «پرداخت پس از پذیرش» با صاحب محصول (خاموش ماند تا `payment-storefront.test.js` ِ PAY-21 دست نخورد)؛
  حساب‌پیِ واقعی (D-08) و دستگاهِ واقعی آزموده نشد؛ پیامِ سیستمی در گفت‌وگوی سفارش ساخته نشد (چت نوعِ پیامِ سیستمی ندارد).
- قدمِ بعدی: FCM در برنامه تا سفارش با برنامهٔ بسته هم زنگ بزند؛ کارتِ `arrival` روی صفحهٔ «ویترین» هم.
### ۱۴۰۵/۰۸/۱۲ — شاخهٔ `claude/sec-review-new` (بازبینیِ امنیتیِ #900/#901، #906 و #904؛ پشته‌ای روی `ccr-3a0d593c-ac0jpj-batch-18` ‏#900 ِ هنوز باز؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: رشتهٔ بستهٔ چت «حذف برای همه» را از هر دو طرف می‌پذیرفت (قاعدهٔ ۲۰۹ فقط «حذف برای من» را باز می‌گذارد) ⇒ حالا ۴۰۹
  `chat_closed` زیرِ همان قفل (`remove` ِ `lib/shop-chat.js`)؛ سنجه در `shop-chat-life.test.js`. سالم دیده شد: جداسازیِ دکان/مشتری،
  پنجرهٔ ۷ روزهٔ «مشکل دارم» (ساعتِ سرور، قفلِ ردیف)، سقفِ IP پیش از احراز، XSS ِ ویترین، رسانهٔ چت؛ `HesabTill.verdict`، برچسبِ NFC و
  قاعدهٔ قرض‌دار در `SalesEngine.record`.
- تصمیمِ صاحب: `customers.contact` (#904) فقط در صفحه است؛ `GET /api/debtors` و همگام‌سازی شمارهٔ کامل را به نقشِ «ممنوع» (انباردار،
  جمع‌آور، یا کاربری که صاحب بسته) می‌دهند و شمارهٔ سفارش/چت هنوز فقط با `orders.contact` ِ نقش است. پوشاندن در pull بی محافظتِ push
  شماره‌های پوشیده را به دفتر برمی‌گرداند؛ کارِ جدا.
- نیمه‌کاره: CI و مرجِ همین PR (پس از #900). قدمِ بعدی: تصمیمِ صاحب دربارهٔ اجرای سرورِ `customers.contact`.
### ۱۴۰۵/۰۸/۱۲ — شاخهٔ `claude/gaps19-partial` (کمبودهای PARTIAL، دورِ ۱۹؛ روی main پس از #879؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد (همه PARTIAL، شاهدِ CI): **PRM-08** ویرایشگرِ تخفیف تأمین‌کنندهٔ شامل/مستثنا و دستهٔ مستثنا را گرفت (هسته از پیش داشت؛
  `PromoForm.suppliers/exSuppliers/exCategories`، `PromoText.supplierName`). **PUR-05** انقضای هر ردیف در «ثبت خرید» ِ گوشی (همان
  کادرِ «ورودِ کالا» ِ انبار، بی رشتهٔ تازه) روی ورودِ انبارِ همان محموله. **PRM-15** آزمونِ سر‌تا‌ته‌ِ اعتبارِ فروشگاه
  (`StoreCreditLifecycleTest`): برگشت به اعتبار، خرج تا صفر، خرجِ دوم رد، برگشتِ دوباره و دخل — باگی پیدا نشد. بی مسیرِ سرور، بی مهاجرت،
  بی تنظیمِ تازه، بی رشتهٔ تازه در جدول‌های زبان، بی پروندهٔ پمپ.
- رد شد: PAY-23 (پرداختِ «در انتظار» ِ درگاه — همان جریانِ حساب‌پی که دستِ دیگری است و D-08)، INV-10 («پیش‌نویسِ بازگردانده» در
  انبارگردانیِ تازه نیست؛ پیش‌نویسِ `StockBook` با `snapshot` درست است)، SYN-12 (فاصلهٔ وصلِ دوبارهٔ سوکت در #897 است)، CRM-15 و STF-01 (بزرگ).
- نیمه‌کاره: CI و مرجِ همین PR (هماهنگ‌کننده مرج می‌کند). قدمِ بعدی: هزینهٔ جانبیِ فاکتور در گوشی (با موتورِ تازهٔ موجودی)؛ اعطای دستیِ اعتبار.
### ۱۴۰۵/۰۸/۱۳ — شاخهٔ `claude/till-credit-hesab` (نسیه فقط با مشتری + حساب‌پی با NFC/QR در صندوقِ گوشیِ نسخهٔ ۳؛ روی main پس از #891؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد — **نسیهٔ بی صاحب**: بازسازی نشان داد موتور نسیهٔ «بی مشتری» را از پیش رد می‌کرد، ولی (۱) برگهٔ «انتخابِ مشتری» ِ گوشی
  مشتریانِ **ادغام‌شده** را هم نشان می‌داد و نسیه (کامل، بخشی، «به حسابِ» ترکیبی) به مشتری‌ای می‌رفت که در هیچ فهرستی نیست،
  (۲) «ثبت» هیچ‌جا به‌خاطرِ مشتری خاموش نبود و ردِ موتور بالای برگه پنهان می‌ماند، (۳) سرور ردیفِ نسیهٔ بی‌مشتریِ برنامهٔ کهنه را
  بی‌صدا می‌پذیرفت. رفع در قاعدهٔ مشترکِ `data/CreditDebtor` روی `SalesEngine.record` (همهٔ صندوق‌ها و آیفون)، «ثبت» ِ خاموش با
  پیام و «انتخابِ مشتری» در گوشی/فروشِ سریع/کامپیوتر (F9 و پنلِ پرداخت)، و در سرور فقط نشان (سابقهٔ `sale.credit_no_debtor`
  پس از push ِ v1 و `/api/sync`، بررسیِ `orphan.credit_no_debtor` در verify-data) — رد نه، دادهٔ کهنه دست‌نخورده.
- انجام شد — **حساب‌پی NFC/QR**: در رابطِ قدیم (`CheckoutDialog`) بود و پس از دستهٔ ۱۳ در صندوقِ گوشی **بی‌راه** بود (کامپیوتر
  داشت). مسیرِ `sale/checkout/hesabpay` (`HesabTillRoute`)، برچسبِ HCE با URI + متنِ مبلغ/کد/گیرنده (`core/nfc/PayTag`)، حالتِ
  تأیید `core/gateway/HesabTill` (خودکار فقط با مرجع + مبلغ + اثرِ انگشتِ گیرنده؛ ساده فقط «پول رسید»)، `payeeFp` در نمای قصد،
  `flag` در `/config`؛ منطقِ قصد از `HesabPaySheet` به `pay/HesabPayIntentFlow` رفت (کامپیوتر هم حالا گیرنده را می‌سنجد).
- نیمه‌کاره: CI ِ همین PR (به خواستِ هماهنگ‌کننده مرج نشد). دو گوشیِ واقعی (NFC، برنامهٔ حساب‌پی) آزموده نشده‌اند.
- قدمِ بعدی: حسابِ پذیرندهٔ حساب‌پی (D-08) برای «خودکار»؛ شکلِ پیوندِ حساب‌پی با مبلغ و یادداشت (`HesabPayShop.qrWithAmount`) تا
  «ساده» هم در برچسب نشانیِ پرداخت داشته باشد؛ آزمونِ دستی روی دو گوشیِ اندروید و یک آیفون (فقط QR).
### ۱۴۰۵/۰۸/۱۲ — شاخهٔ `claude/gaps21-partial` (کمبودهای PARTIAL، دورِ ۲۱؛ روی main پس از #892؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- ممیزیِ «(باز)» ِ کهنه (یافتهٔ دورِ یازدهمِ شاهد، #903): S27، AUTH-08، AUTH-09، LOC-06، DLV-02، DLV-09، GATE-NEW-05 و MKT-11 به PRهای مرج‌شده
  اشاره داشتند — هر کدام روی main دوباره سنجیده شد (آزمون‌های سرورِ همان PRها روی PGlite ۷۷/۷۷ و آزمون‌های کاتلینِ نام‌برده) و «مانده» درست شد.
  S39 (#357) و ORD-13 (#723) بی مرج بسته شده بودند ولی **کارشان با دستهٔ #359 (3b3520fa) رسیده بود** (`courier-summary.js` ِ «ناموفق از
  خودِ پیک»؛ `PickSubstituteSheet`)؛ نشان داده شد، چیزی ساخته نشد. همه PARTIAL ماندند (AUTH-09 همان IMPLEMENTED-VERIFIED).
- انجام شد: **RPT-13** ریزِ هر ردیفِ تفکیکِ «فروش» و «سود و حاشیه» با کلیک یا Enter (`ReportCore.invoicesOf`، `ReportFreshModel.drill`؛
  `ReportDrillTest` — جمعِ ریز = ستونِ «فروش» ِ همان ردیف برای همهٔ ردیف‌ها). ممیزیِ S48 و PRM-13 (فهرستِ «مانده» ِ کهنه). بی مسیرِ سرور، بی مهاجرت.
- نیمه‌کاره: CI و مرجِ همین PR. قدمِ بعدی: Enter در «امروز»، «بدهی‌ها» و «تقلب»؛ کسبِ امتیاز روی پرداختِ بعدیِ نسیه (PRM-13).
### ۱۴۰۵/۰۸/۱۲ — شاخهٔ `claude/gaps22-partial` (کمبودهای PARTIAL، دورِ ۲۲؛ روی main پس از #903؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: **PRM-13 — کسب روی پرداختِ بعدیِ نسیه** (`LoyaltyPoints.afterPayment` در `DebtEntry.change` و فرمِ رسیدِ گوشی؛ حذفِ رسید
  ⇐ `afterPaymentDeleted`): FIFO ِ زمان روی همهٔ بدهی‌های مشتری (نسیهٔ باز + بردِ دستی)، هدفِ تجمعیِ هر فاکتور و فقط افزایش، ردیف با
  `refId` ِ فاکتور تا برگشت/لغو آن را هم پس بگیرند؛ فقط فاکتورهای پس از نخستین ردیفِ امتیازِ دکان. `LoyaltyCreditPaymentTest` ۶/۶.
  بی مسیرِ سرور (سرور ردیفِ `loyalty_earn` را با همان `points(SELL)` ِ `doc-roles.js` می‌پذیرد)، بی مهاجرت، بی تنظیمِ تازه.
- ⚠️ ردیفِ PRM-13 در STATUS با #905 هم دست خورده؛ متنِ این شاخه ممیزیِ #905 را هم دارد (هنگامِ ادغام همین را نگه دارید).
- نیمه‌کاره: CI و مرجِ همین PR. قدمِ بعدی: امتیازِ رسیدِ «صندوق ← دریافت از مشتری» در پنلِ مدیریتِ وب (اگر روزی ثبتِ رسید آن‌جا بیاید).
### ۱۴۰۵/۰۸/۱۲ — آیفون گامِ ۹، شاخهٔ `claude/ios-app-phase9` (روی main پس از #879؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: نتیجهٔ گامِ ۸ در `docs/IOS-PLAN-fa.md` §۰.۱۳ (اجرای هشتم، کارِ 125974915281: هر هفت گام سبز). `ios-exclude.txt` ۱۲ ⇐ ۱۰:
  `data/Migration.kt` و `pay/platform/PayDevice.kt` ِ اندروید خودشان روی آیفون با shimهای بی‌رفتار (WebView، NFC، نمایشگرِ دوم، `Presentation`،
  `ComposeView`، `ComponentActivity`)؛ `MigrationIos`/`PayDeviceIos` پاک شدند؛ آزمونِ شبیه‌ساز `Phase9IosTest` (۳). اندروید و کامپیوتر دست نخوردند.
  ۱۰ سطرِ مانده و دلیلِ هرکدام: §۰.۱۴ (۲ فقط-اندروید، ۸ همتا که shim ِ هم‌نامشان دروغ می‌گفت).
- محلی: klib ِ iOS و klib ِ آزمون سبز (۱۱۹۳ از ۱۲۰۳ فایل)، `jvmTest` ِ `ios/shared` ۲۰/۲۰.
- ⚠️ ساختِ Gradle روی این ماشین: هیچ عاملی `gradlew --stop` نزند (همهٔ daemonهای همان نسخه، از جمله ساختِ عامل‌های دیگر، را می‌کشد).
- CI ِ macOS (کارِ 126091370476): سبز، شبیه‌ساز ۳۳/۳۳ با `CoreFlowsE2EIosTest` ۱/۱ و `Phase9IosTest` ۳/۳ (در §۰.۱۴).
- نیمه‌کاره: مرجِ همین PR. قدمِ بعدی: از ۸ همتا شاید `DiskWrite` با
  `SDK_INT` ِ جدا برای همان فایل، و `Haptics` پس از تصمیمِ صاحب‌کار دربارهٔ Core Haptics.
### ۱۴۰۵/۰۸/۱۲ — شاخهٔ `claude/ci-evidence-11` (ممیزیِ شاهدِ CI، دورِ ۱۱؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: ردیف‌هایی که #889، #891، #893 و #892 دست زدند با CI ِ main سنجیده شدند (#894 و #879 ردیفی را دست نزدند؛ دستهٔ #900 باز بود و بیرون ماند).
  **TEN-02** (دو آزمونِ «TEN-02: دو دکان» ِ `order-timeout`/`order-late-apology` «ok» در هر سه کارِ «سرور» 41654110608)، **POS-09** (`LineUnitsTest`،
  `ScenarioS05Test`، `ProductRulesTest`: کامپیوتر 41654110717، «توحید بومی» 41654110812) و **S14** (`ScenarioS14Test`، `ReturnFlowTest`: 41666727425،
  41666727891؛ `staff-approval` ۲۱/۲۱ در «سرور» 41666727611) ⇐ IMPLEMENTED-VERIFIED. بقیه «مانده»ی غیرِ CI دارند (S04: نیمه‌های Double ِ `Rounding`).
  جدول: ۳۸۰ / ۳۱۱ / ۳۵ / ۱۲ / ۲۶ (حدودِ ۷۰٫۱٪). فقط STATUS و HANDOFF.
- نیمه‌کاره: CI و مرجِ همین PR. قدمِ بعدی: دورِ ۱۲ پس از مرجِ #900؛ ردیف‌های کهنهٔ «(باز)» برای PRهای بسته (S27، S39، AUTH-08، AUTH-09، LOC-06، DLV-02،
  DLV-09، GATE-NEW-05، MKT-11، ORD-13 — فهرست در یادداشتِ دورِ ۱۱ ِ STATUS) از نو نوشته شوند.
### ۱۴۰۵/۰۸/۱۲ — شاخهٔ `claude/home-orders` («سفارش‌های آنلاین» در خانه و یک گفت‌وگو برای هر سفارش؛ روی main پس از #891؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: کاشیِ «سفارش‌های آنلاین» جای «فروشِ تازه» در خانهٔ نسخهٔ ۳ (فقط با `OrdersLive.enabled`؛ نشانِ زنده، نقطهٔ تپنده،
  رنگِ مرجانی؛ `HomeTiles`)؛ صندوق بی‌راه نمی‌ماند: نوارِ بی «فروش»/«اسکن» ⇒ دکمهٔ شناورِ «فروشِ تازه» روی خانه
  (`V3ReachabilityTest`، `HomeTilesTest`). صف: برگه‌ها به نامِ صاحب‌کار (`StrOrdersHub.tab`، «زمان‌بندی‌شده» فقط با سفارش)،
  کارت با «سفارشِ آنلاین»، نامِ اقلام (`itemsPreview` ِ `orders.list`)، «با پیک»/«خودش می‌آید»؛ جزئیات: «آماده‌سازی و تحویل»
  (`OrderPrepCardV3`: عکس برای مشتری از چتِ همان سفارش، «بسته شد»، پیک/حضوری). سرور: چرخهٔ عمرِ رشته (`lib/shop-chat-life.js`،
  مهاجرتِ ۲۰۹ با rollback): «بسته شد» پس از تحویل+پرداخت یا پایانِ بی‌تحویل، «مشکل دارم» ِ مشتری تا ۷ روز (همان رشته)، عمومیِ
  بی‌کار ۷ روز ⇒ بسته و دورِ تازه در همان رشته، ۴۰۹ ِ نوشتن در رشتهٔ بسته از هر دو طرف، `GET …/history` و `POST …/issue`
  (سطلِ IP پیش از احراز؛ routes.json از نو). ویترین: `store/chat.js` ِ دیربار («پرسش از دکان» و کارتِ پیگیری؛ بارِ نخست
  ۷۰٬۳۷۳ بایت). آزمون: `shop-chat-life.test.js` (۷ از ۸ پیش از تغییر سرخ، پس از آن سبز)، `web-store-chat.mjs` (کرومیوم)، کاتلین.
- نیمه‌کاره: CI و بررسیِ صاحب‌کار (به خواستِ هماهنگ‌کننده مرج نشد). دکان روشِ تحویل را عوض نمی‌کند (S72 کارِ مشتری است).
- قدمِ بعدی: فرستادنِ عکس از سمتِ مشتری در ویترین (فعلاً فقط متن)، نشانِ «بسته/مشکل دارم» در صندوقِ گفت‌وگوها، و پوشِ
  web-push برای مشتریِ ویترین.
### ۱۴۰۵/۰۸/۱۲ — شاخهٔ `claude/gaps20-partial` (کمبودهای PARTIAL، دورِ ۲۰؛ روی main پس از #892؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد (PARTIAL، شاهدِ CI): **CRM-15** بخشِ «دسترسیِ محدود»: مجوزِ تازهٔ `customers.contact` (فقط نمایشی، `full`) در هر سه هسته
  و بردارهای `perm-v1.json` و `SERVER_EDITABLE`؛ پیش‌فرض همه جز انباردار و جمع‌آور. بی مجوز شمارهٔ مشتری پوشیده (`ContactMask`) در
  فهرست/پروندهٔ مشتریِ گوشی، برگه‌های گزینشِ مشتری، مشتریانِ کامپیوتر و شمارهٔ دوم؛ تماس/پیامک/واتس‌اپ ِ پرونده برداشته. برچسب در
  صفحهٔ کارمند (`StrAccount.permContacts`). **STF-01** فقط ممیزیِ شاهد: چهار نقشِ تازه هستند (`staff-roles*.test.js` ۲۲/۲۲)؛ متنِ «نیست» کهنه بود.
  بی مسیرِ تازه (`routes.json` دست نخورد)، بی مهاجرت، بی پروندهٔ پمپ.
- نیمه‌کاره: CI و مرجِ همین PR. قدمِ بعدی: سابقهٔ دسترسیِ CRM-15 (چه کسی پرونده را دید)، وب، نشانیِ تحویل؛ نقشِ سفارشیِ STF-01.
### ۱۴۰۵/۰۸/۱۲ — شاخهٔ `claude/gaps16-partial` (کمبودهای PARTIAL، دورِ ۱۶؛ روی main پس از #890، main ِ پس از #889 در آن ادغام شد؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- شمارِ PARTIAL ِ هر خانواده از STATUS: S ۷۰، POS ۲۲، CHAT ۱۵، DLV ۱۳، PAY ۱۲ (INV ۱۲) ⇒ ردیف‌ها از سه خانوادهٔ نخست.
- انجام شد (همه PARTIAL، شاهدِ CI): **CHAT-14** گفت‌وگوی نسخهٔ ۳ ِ مدیریت — «کارها» زیرِ هر حباب (پاسخ با نقل، ویرایشِ پیامِ خود در مهلت،
  سنجاق تا ۵ و نوارِ «سنجاق‌شده»)، «در پیام‌ها» در جست‌وجوی صندوق؛ سرور `POST …/messages/:mid/edit` (کامپیوتر PATCH نمی‌فرستد) با سطلِ IP پیش از
  احراز. **S117** پیامِ پشتیبانیِ دکان و یادداشتِ درخواستِ خرید بی شمارهٔ کاملِ کارت (رشتهٔ پمپ دست نخورد، O-19). **POS-31/S18** صفحه‌کلیدِ حروفِ
  روی‌صفحه (فا/دری، پښتو، لاتین) برای کادرِ اسکنِ کامپیوترِ لمسی (`TouchKeyboard`؛ `DeskSale.kt` بی خطِ تازه). **POS-06** سنجاقِ کاشی در صندوقِ گوشی،
  فقط همین دستگاه (`TillPins`). بی مهاجرت، بی تنظیمِ تازه، بی پروندهٔ پمپ؛ پیش‌فرض‌ها همان.
- رد شد: S79 (کارگرِ هشدار همان `ops-alerts.order_stuck` است؛ فقط آزمون می‌ماند)، S19 (کرایه با PUR-06 آمده؛ «مانده» کهنه — برای ممیزی)، S83 (محدودهٔ
  پیک جدول و مهاجرت می‌خواهد)، S124 (گزارشِ مالیِ تازهٔ پنل)، ردیف‌های مشتری/ویترین (S41، S43، S56، S126) و DLV-16.
- یافته: `docs/api/routes.json` روی main هم کهنه است (`node tools/api-contract/generate.cjs --check`).
- نیمه‌کاره: CI و مرجِ همین PR. قدمِ بعدی: شاهدِ CI برای این پنج ردیف؛ گامِ ۴ ِ S18 (برگهٔ «مشتریِ تازه» در صندوقِ کامپیوتر)؛ چیدمانِ کاشی در کامپیوتر.
### ۱۴۰۵/۰۸/۱۲ — شاخهٔ `claude/history-print` («تاریخچه» ِ روز/ماه/سال و چاپِ گزارشِ هر دوره؛ روی main پس از #891؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد (خواستِ صاحب‌کار): «تاریخچه» ِ نسخهٔ ۳ از نو — پیش‌فرض امروز با جمعِ امروز (مبلغ، شمار، نقد/نسیه/دیگر، برگشت، خالص)،
  کلیدِ «روز / ماه / سال» با بخشِ باز/بستهٔ هر دوره (تقویمِ خورشیدی یا میلادی از تنظیمات)، جستجو و فیلتر در سربرگ (فیلتر جمع را
  عوض نمی‌کند)، گوشی یک ستون و تبلت/کامپیوتر دو ستون؛ کامپیوتر جدولِ پیشین را با `sales/table` نگه داشت. جمع‌ها بی قاعدهٔ تازه:
  `data/SalesPeriods` همان `DayEnd.events` + `DayClose.of` (روزِ دارای سندِ دیررسِ POS-40/PAY-07 با پنجرهٔ خودِ پایانِ روز؛ مهرِ
  کشوی نام‌دار برداشته). «چاپ» ِ هر دوره: خلاصه یا همهٔ فاکتورها (`PeriodSlip`، ۵۸ و ۸۰)، برگهٔ تأیید با شمار و کاغذِ تخمینی
  (`core/receipt/PaperLength`)، از صفِ چاپ چندتکه با «لغو» در میانه (`core/hw/PrintBatch`، `print/PeriodPrintAndroid`،
  `PrintSpooler.submit(onDone)`). شمارشِ کور ⇒ بی جمعِ پولی. آزمون‌ها: `SalesPeriodsTest` (برابری با پایانِ روز و Z، مرزِ
  ماه/سالِ خورشیدی و میلادی، ده هزار فاکتور ≈ ۱۰۰ms)، `PeriodSlipTest`، `PrintBatchTest`، `PaperLengthTest`، `V3ReachabilityTest`.
- نیمه‌کاره: CI ِ همین PR (به خواستِ هماهنگ‌کننده مرج نشد). عکسِ صفحه روی این ماشین ساخته نشد (`gradle run` ِ آفلاین
  `androidx.collection` ندارد) — عکس‌های CI («history» در `SalesShots`) را ببینید.
- قدمِ بعدی: آزمایشِ چاپِ واقعی روی چاپگرِ ۵۸ و ۸۰ (تخمینِ کاغذ عمداً کمی بیشتر است)؛ اگر صاحب‌کار خواست، `DayEnd` هم کشوهای
  نام‌دار را در «دفتر» بشمارد (امروز فقط کشوی اصلی؛ تاریخچه همهٔ کشوها).
### ۱۴۰۵/۰۸/۱۳ — شاخهٔ `claude/dari-real` (دریِ واقعی؛ گلهٔ صاحب‌کار «لفظ‌های دری به کار نرفته»؛ روی main پس از #893؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- تشخیص: لایهٔ دری (۱۸ واژه، ۲ جمله) فقط ۳٫۵٪ ِ نوشته‌ها و ۲٫۶٪ ِ کارهای اصلی را عوض می‌کرد، چون فارسیِ برنامه خودش از پیش
  افغانی است (دکان، قرض‌دار، برد/رسید، الباقی)؛ و حالتِ پیک (~۱۸۰ نوشته) و پنج صفحهٔ تنظیماتِ نسخهٔ ۳ (~۶۰) متنِ خام بودند.
- انجام شد: `DariText.PHRASES` با جای خالی (`{}` = مقدارِ asIs، کلید‌به‌کلید، `DariText.shape`)؛ ۳۷ واژه/عبارتِ تازه و ۳۰ جملهٔ
  کامل (همه «نیاز به تأییدِ صاحب‌کار»، فهرست در `docs/DARI-REVIEW-fa.md`)؛ `StrCourierApp` و `StrSettingsPages` (فارسی همان،
  انگلیسی و پشتو تازه)؛ پیامِ محلیِ `ApiFailure` در دری از لایه (EN/PS همان)؛ «… گردید» ِ دری سبز؛ «انترنت» در `SyncModel`؛
  وب با `dari-words.js` (جملهٔ دوسطری، کلیدِ تکراری ⇒ خطا). آزمون: `DariCoverageTest` (کفِ پوشش، اندازهٔ هر صفحه، نگهبانِ
  متنِ خام در `ui/v3` و `ui/courier`)، `DariWordsTest`، `DariTextTest`، `dari-text.test.js`، `web-store-dari.mjs`. پوشش: همه ۳٫۵٪ ⇐ ۱۳٫۸٪،
  کارهای اصلی ۲٫۶٪ ⇐ ۱۵٫۵٪ (کفِ آزمون ۱۵٪)، صفحه‌های نسخهٔ ۳ ۴٫۵٪ ⇐ ۱۴٫۱٪، وب ۱۴ ⇐ ۵۷ از ۶۰۸.
- نیمه‌کاره: CI ِ همین PR (به خواستِ هماهنگ‌کننده مرج نشد)؛ بازبینیِ سطر‌به‌سطرِ صاحب‌کار.
- قدمِ بعدی: پس از ✗ های صاحب‌کار فهرست را کوتاه/درست کن (هر دو: `DariText.kt` و `DARI-REVIEW-fa.md`، بعد
  `node scripts/dari-words.js --write`)؛ واژه‌های «منتظرِ تصمیم» (تیلفون، فرمایش، جنس…) فقط با گفتنِ او؛ متنِ خامِ یاورهای
  `OwnerReportPlan`/`StorefrontEtaApi`/`PromoGuardApi` و صفحه‌های قدیمیِ `ui/screens` که نسخهٔ ۳ صدا می‌زند.
### ۱۴۰۵/۰۸/۱۲ — شاخهٔ `claude/gaps18-partial` (کمبودهای PARTIAL، دورِ ۱۸؛ روی main پس از #879/#893/#894؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد (همه PARTIAL، شاهدِ CI): **S76/S77** `LiveSocket.kt` آزمودنی شد با کم‌ترین بازآرایی — هر وابستگیِ بیرونی (راهِ سوکت،
  بلیت، ساعت، زمان‌بند، نشانِ زنده، کارهای سکو) از `LivePort`؛ سازندهٔ عمومی همان `AppLivePort` ِ واقعی (کدِ پیشین بی تغییر)؛
  `ScenarioS76Test` ۴/۴ و `ScenarioS77Test` ۷/۷ با سرورِ جعلی (`scenario/LiveSocketFakes.kt`). **باگِ واقعی:** `disconnect()` وسطِ صبرِ
  وصلِ دوباره پایان نبود — کارِ زمان‌بندی‌شده سوکتی بی مالک می‌ساخت (هر خبر دو بار، `current` به نمونهٔ رهاشده)؛ رفع در ریشه
  (`later { if (!closedByUs) connect() }`)، آزمون پیش از رفع سرخ. **S11** Enter ِ بی اسکن در صندوقِ گوشی با صفحه‌کلیدِ بیرونی
  (`bareEnterPays`، فقط با `payV2`). **POS-05** موجودیِ «قابل‌فروش» در کاشیِ کامپیوتر هم (`HeldStock.sellable`). **POS-41** کلیدهای
  پنلِ پرداخت در keymap ⇒ partial و F2 ِ تازه (`payPanelKey`). بی مسیرِ سرور، بی مهاجرت، بی تنظیمِ تازه، بی پروندهٔ پمپ.
- رد شد: S05 (بارکدِ کارتن با قیمتِ لیست) — سبد هر کالا را یک ردیف نگه می‌دارد، پس قیمتِ کارتن روی دانهٔ بعدیِ همان کالا هم
  می‌نشست؛ مدلِ ردیفِ جدا برای واحد لازم است. S94 (رضایتِ پیک) — مهاجرت و تغییرِ رفتارِ دکان‌هایی که پرچمِ موقعیت را روشن کرده‌اند.
- نیمه‌کاره: CI و مرجِ همین PR (هماهنگ‌کننده مرج می‌کند). قدمِ بعدی: Alt+P ِ پنلِ پرداخت؛ شاهدِ CI ِ این ردیف‌ها؛ S05 با ردیفِ واحد.
### ۱۴۰۵/۰۸/۱۲ — شاخهٔ `claude/gaps17-partial` (کمبودهای PARTIAL، دورِ ۱۷ — سناریوها؛ روی main پس از #891؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد (همه PARTIAL، شاهدِ CI): آزمونِ سناریوی واقعی برای **S04** (`ScenarioS04Test`: برچسبِ ترازو و «۰٫۷۵*» تا فاکتور و موجودی، گردکردن
  برابرِ هسته)، **S14** (`ScenarioS14Test`: برگشتِ بی رسید با تأیید و سقف تا زبانهٔ «ضدتقلب»)، **S15** (`ScenarioS15Test`: Shift+F9، تعویض و
  رسیدِ دوبخشی از سندِ ثبت‌شده؛ `Receipts.returnInputOf` = نگاشتِ بی Context)، **S95** (`scenario-s95.test.js`: خطای دیتابیس در راه‌انداز و
  دیسکِ پُر ⇒ هشدارِ critical ⇒ ایمیل و پوش ⇒ بستنِ خودکار)، **S118** (`scenario-s118.test.js`: تکرارِ هم‌زمانِ فایل، نشانیِ خصوصی با توکنِ
  منقضی/باطل و پس از نگهداری) و **S64** (قاعدهٔ `release` در `tools/security/workflow-guard.mjs`).
- **باگِ واقعی (S64):** `disk-windows` ِ `tohid-desktop.yml` (آزمونِ ذخیرهٔ دفتر روی ویندوز، F0-01) در زنجیرهٔ انتشار نبود ⇒ نصاب با آن
  آزمونِ سرخ هم منتشر می‌شد. رفع: `publish.needs: [package, disk-windows]`؛ نگهبان همین را برای هر کارِ آزمونِ بیرون از زنجیره سد می‌کند.
- **قراردادِ API:** `docs/api/routes.json` از #605 کهنه بود (۲۰۶ مسیر بیرون). چرا گرفته نشد: تنها آزمونِ CI پارسر بود و `generate.cjs --check`
  (که `server/node_modules` می‌خواهد) در هیچ کاری اجرا نمی‌شد. حالا `server/test/api-contract.test.js` در کارِ سرور سد است (envِ آلوده +
  جهش)، `--check` مسیرهای افزوده/افتاده را نام می‌برد و `--out` دارد؛ `server.yml` با تغییرِ قرارداد/مولد هم می‌دود. ⚠️ #892 (مسیرِ تازهٔ
  گفت‌وگو) پس از مرجِ این PR باید `node tools/api-contract/generate.cjs` بزند (مسیرِ #891 همین‌جا آمد).
- رد شد: S37 (پیش‌پرداختِ آنلاین ⇐ D-08)، S76/S77 (`LiveSocket.kt` بی جداسازیِ کلاینتِ OkHttp و `Handler` آزمون‌پذیر نیست؛ کارِ جدا)،
  S08 (برگهٔ تأییدِ سرور)، S96 (روزِ کاملِ گوشی — کارِ بزرگ‌تر). بی مهاجرت، بی مسیرِ تازه، بی پروندهٔ پمپ، بی «تازه‌ها» (تغییرِ دیدنی نیست).
- نیمه‌کاره: CI و مرجِ همین PR. قدمِ بعدی: شاهدِ CI برای این شش ردیف؛ آزمون‌پذیر کردنِ `LiveSocket` (S76/S77)؛ گامِ آزمون برای پوستهٔ
  بازنشستهٔ `android-release.yml` یا برداشتنش (تصمیمِ صاحب‌کار).
### ۱۴۰۵/۰۸/۱۲ — شاخهٔ `claude/gaps15-partial` (کمبودهای PARTIAL، دورِ ۱۵؛ روی main پس از #890؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد (همه PARTIAL، شاهدِ CI): **TEN-02** آزمونِ دوتاییِ دکان برای دو کارگرِ `order-timeout` و `order-late-apology` (PGlite و
  PostgreSQL ِ محلی ۱۵/۱۵؛ ۴ جهشِ نشت همه سرخ؛ نشتی پیدا نشد، کدِ کارگرها دست نخورد). **OPS-09** «مصرف و هزینهٔ پیامِ مشتری» در
  زبانهٔ «عملیات» ِ پنلِ مدیر (`GET /api/admin/system/notify-usage`، `lib/notify-usage.js`؛ هر دکان و کانال، سقف‌ها و نشانه‌های
  سوءاستفاده؛ سطلِ IP پیش از احراز). **POS-09/S05** همهٔ واحدها و بسته‌ها با قیمتِ لیست در برگهٔ ردیفِ صندوقِ گوشیِ نسخهٔ ۳ (پشتِ
  همان `TillGoodsPref.units`؛ `LineUnitsTest`). **S11** Enter در کادرِ خالیِ صندوقِ کامپیوتر ⇒ پنلِ پرداخت فقط ≥ ۵۰۰ میلی‌ثانیه پس
  از آخرین اسکن/Enter و فقط با `payV2` (`CashKeys.emptyEnterOpensPay`، `EmptyEnterGate`). بی مهاجرت، بی تنظیمِ تازه، بی پروندهٔ پمپ.
- رد شد: **DLV-16 / «نزدیک‌ترین پیک» ِ DLV-03** — نامزدِ سپردن پیکِ آزاد است، موقعیت عمداً فقط «در راه» نگه داشته می‌شود و سرویسِ
  پیش‌زمینهٔ موقعیت نیست ⇒ سرویسِ زندهٔ موقعیت لازم است (دلیل در ستونِ DLV-03 ِ STATUS).
- نیمه‌کاره: CI ِ همین PR (به خواستِ هماهنگ‌کننده مرج نشد).
- قدمِ بعدی: شاهدِ CI برای این ردیف‌ها؛ قیمتِ لیستِ بسته در اسکنِ بارکدِ کارتن (S05)؛ همان Enter در صندوقِ گوشی با صفحه‌کلیدِ بیرونی؛
  هزینهٔ پیامکِ کدِ ورود در گزارشِ مصرف (OPS-09).
### ۱۴۰۵/۰۸/۱۳ — شاخهٔ `claude/gaps14-partial` (کمبودهای PARTIAL، دورِ ۱۴؛ روی main پس از مرجِ #887؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- رفعِ «بی‌راه» (اولویتِ بالا): کلیدِ «گزارش‌های مشتری» (`ShopPolicy.crmReports`، ردهٔ B، پیش‌فرض خاموش) روی گوشی ردیف نداشت
  (`SettingsLayout.PHONE_HIDDEN`) ولی ادغامِ تکراری‌ها (CRM-10)، تصویرِ صورت‌حساب (CRM-05)، «گروه، لیستِ قیمت و رضایت»
  (CRM-08/12) و سنِ بدهی/RFM ِ گوشی پشتِ آن بودند ⇒ روی گوشی هرگز روشن نمی‌شدند. حالا ردیف و خطِ ساده در «داده و پشتیبان» ِ
  گوشی، توضیحِ همان سکو (`crmNotePhone`)، و جست‌وجوی «ادغام/تکراری/صورت‌حساب». نگهبانِ همیشگی: `V3ReachabilityTest` هر
  بخشِ `PHONE_HIDDEN` را با خواننده‌اش (`GATES`) در صفحه‌های گوشیِ `ui/v3` می‌گردد؛ `SettingsPortTest.DESK_ONLY` دلیلِ هر
  بخشِ فقط‌کامپیوتر را دارد. پویشِ بقیه: POS_SEARCH، POS_TOUCH، POS_MORE_KEYS، KEY_HELP، TILL_SWITCH، REPORT_KEYS، WEBCAM
  عمداً فقط کامپیوترند (هیچ صفحهٔ گوشی پشتشان نیست؛ `TillUser.me` خاموش ⇒ نامِ حساب)؛ DESK_HIDDEN (دوربین، نوارِ پایین) هیچ
  کلیدی ندارند.
- انجام شد (همه PARTIAL ماندند، شاهدِ CI): **PRM-11** گروهِ مشتری در تخفیفِ خودکار (`PromoEngine.groupOf`، «برای کدام
  مشتریان» در ویرایشگر)؛ **POS-32** «ذخیرهٔ تصویر/PDF» ِ فاکتور در کامپیوتر (`InvoiceFile.bytes/save`، `InvoiceSaveRow`)؛ **POS-26**
  رسیدِ چاپیِ پیش‌پرداختِ سفارشِ ویژه (`SpecialOrders.depositTx` ⇐ `DebtReceiptDialog`)؛ **SEC-10** متنِ privacy.html
  (`#customer-erasure`) گره‌خورده به `KEPT` با `privacy-erasure-text.test.js`. بی مهاجرت، بی مسیرِ تازه، بی پروندهٔ پمپ.
- رد شد: PRM-18 («مانده» فقط آزمونِ صفحه است)، INV-04@32 (به O-06 وابسته)، SYN-12/S30 (با #421 هم‌پوشانی)، HW-09 ِ گوشی
  (ترازوی بلوتوثی بی درگاهِ صندوق)، TEN-02/DLV-03 («مانده» ِ STATUS کهنه است: #515 و #537 مرج شده‌اند — برای ممیزیِ بعد).
- نیمه‌کاره: CI و مرجِ همین PR. قدمِ بعدی: شاهدِ CI ِ همین ردیف‌ها؛ رابطِ وبِ SEC-10.
### ۱۴۰۵/۰۸/۱۲ — شاخهٔ `claude/evidence10` (ممیزیِ شاهدِ CI، دورِ ۱۰؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: ردیف‌هایی که #881، #883، #885، #886، #887 و #888 (میانِ کار مرج شد) دست زدند با CI ِ main سنجیده شدند (#889 باز است). **IMG-12** ⇐
  IMPLEMENTED-VERIFIED (73275c2e: «سرور» 41612493503، «توحید بومی» 41612493045، کامپیوتر 41612494191) و **OPS-05** ⇐ IMPLEMENTED-VERIFIED
  (`check.test.mjs` ۵/۵ در «کیفیتِ کد» ِ main از #885: 41613706064؛ آزمون‌های Kotlin روی 647abc8e: 41632748448، 41632748440). ردیف‌های کهنه: TEN-02
  (#534 با #515 آمد؛ مانده آزمونِ دوتاییِ دکان برای `order-timeout` و `order-late-apology`) و DLV-03 (#537 مرج شده؛ مانده «نزدیک‌ترین پیک»، DLV-16).
  بقیه «مانده» دارند. جدول: ۳۷۷ / ۳۱۴ / ۳۵ / ۱۲ / ۲۶ (حدودِ ۶۹٫۹٪). فقط STATUS و HANDOFF.
- نیمه‌کاره: CI و مرجِ همین PR. قدمِ بعدی: دورِ ۱۱ ِ شاهدِ CI پس از مرجِ #889 و پایانِ CI ِ 42763ce4؛ آزمونِ دوتاییِ دکان برای دو کارگرِ TEN-02.
### ۱۴۰۵/۰۸/۱۲ — شاخهٔ `claude/gaps13-partial` (کمبودهای PARTIAL، دورِ ۱۳؛ پشته‌ای روی `claude/gaps12-partial` ‏#887 ِ هنوز باز؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد (همه PARTIAL، شاهدِ CI): **PUR-14** روی گوشیِ نسخهٔ ۳ — «خرید و تأمین‌کننده» ⇐ «فاکتورهای تأمین‌کننده» (`purchasing/bills`، `…/{id}`، `…/{id}/new`؛
  فقط با پرچمِ `shop.purchase_orders` و برای مدیر/صاحب)؛ فرم، «باطل» و تطبیق همان اجزای کامپیوتر (`PoBill`، `PoBillsList`، `PoBillVoidDialog`، `PoMatchLines`)
  و `PurchaseOrderBook.canBill/canVoid/billable`؛ `V3ReachabilityTest` چهار کارِ تازه. **FIN-01** «بازپرداختِ بخشی/کامل» با مبلغ و دلیل از `POST /:id/refunds`
  وقتی `refundLedger` روشن است (پیش‌تر ۴۰۹ `refund_via_ledger` و بی راه؛ `OrderRefundModel`). **S17** موجودیِ هر محل در قیمت‌پرسی (همان `PlaceStock.of`،
  فقط با «محل‌های نگه‌داری»). **INV-03** موجودیِ کاشیِ صندوقِ گوشی منهای فاکتورهای نگه‌داشته (`HeldStock.reserved`). **S33** Plus Code از پینِ مقصد در
  جزئیاتِ مأموریتِ پیک (`core/courier/PlusCode`، بردارهای مرجعِ OLC). بی مهاجرت، بی تغییرِ سرور، بی پروندهٔ پمپ.
- رد شد: CRM-06 (ردیفِ D-09 در `OWNER-DECISIONS-fa.md`) و STF-08 (ردیفِ O-05) — هر دو ساخته و پیش از کامیت برگردانده شدند. یافته برای بعد: کلیدِ «گزارش‌های
  مشتری» در گوشی پنهان است (`SettingsLayout.PHONE_HIDDEN`) ولی ادغامِ تکراری‌ها (CRM-10) و تصویرِ صورت‌حساب (CRM-05) ِ گوشی پشتِ همان‌اند ⇒ روی گوشی هرگز روشن
  نمی‌شوند. PUR-03 (فصل و اطمینان در فرمِ سفارش) رفتارِ PUR-10 ِ دورِ ۵ را عوض می‌کرد ⇒ نه.
- نیمه‌کاره: CI و مرجِ #887 و سپس همین PR (پس از squash ِ #887 فقط کامیت‌های خودش، README §۴.۶).
- قدمِ بعدی: شاهدِ CI برای همین شش ردیف؛ ساختن/دریافتِ سفارشِ خرید در گوشیِ نسخهٔ ۳؛ تصمیمِ صاحب‌کار دربارهٔ «گزارش‌های مشتری» در گوشی.
### ۱۴۰۵/۰۸/۱۳ — شاخهٔ `claude/gaps12-partial` (کمبودهای PARTIAL، دورِ ۱۲؛ روی main پس از مرجِ #886؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد (همه PARTIAL، شاهدِ CI): **PUR-14** فاکتورِ جدای تأمین‌کننده — مهاجرتِ **۲۰۸** `purchase_bills` (+ rollback، آزموده روی PGlite)،
  `POST …/purchase-orders/:id/bills` و `…/bills/:billId/void` (فقط مدیر، کلیدِ یکتا، شمارهٔ فعالِ یکتا، باطل با دلیل، سقفِ IP پیش از احراز؛
  SUB-10، `shop-route-inventory`، `route-authz`، `tenant-matrix` سبز)؛ برنامه: تطبیق با فاکتورِ فعالِ تأمین‌کننده، فرم و «باطل» در جزئیاتِ
  سفارش (`PurchaseBillTest`). **CRM-09** `Debtor.deliveryAddress` (فیلدِ v1 بی migration، الگوی `phone2`؛ برگهٔ ویرایش و پروندهٔ نسخهٔ ۳،
  ادغام، خروجی؛ `CustomerDeliveryAddressTest`). **STF-02** تأییدِ لغو فقط روی Sync v3 (`CancelApproval`، `Sale.cancelApproval` ⇐ `approval` ِ
  op ِ «c» در `V3Convert`؛ دکانِ v1 همان امروز). **RSK-03** کارتِ «سابقهٔ این مشتری» در جزئیاتِ سفارش و **RSK-04** «بستن/مطمئن کردنِ این شماره»
  و فهرست با برداشتن (`core/orders/OrderRisk`). بی پروندهٔ پمپ.
- رد شد: **SYN-07** — روشن کردنِ v3 برای دکانِ واقعی = جابه‌جاییِ دادهٔ زنده + `SYNC_V3_ENABLED` ِ سرورِ زنده ⇒ D-17 (در `OWNER-DECISIONS-fa.md`
  و ستونِ «مانع» ِ SYN-07 ثبت شد). یافته: لغوِ برگه‌دارِ **مدیرِ** «نیازمند تأیید» در v1 پذیرفته می‌شود ولی برگه در `approval` با `shop.doc_chain`
  «ویرایشِ فاکتورِ قطعی» است ⇒ ساخته نشد.
- نیمه‌کاره: CI و مرجِ همین PR. قدمِ بعدی: شاهدِ CI برای این شش ردیف؛ راهِ «سفارش‌های خرید» در گوشیِ نسخهٔ ۳؛ افزودنِ پین به فهرستِ بسته.
### ۱۴۰۵/۰۸/۱۳ — شاخهٔ `claude/gaps11-partial` (کمبودهای PARTIAL، دورِ ۱۱؛ روی main پس از مرجِ #883؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد (همه PARTIAL ماندند، شاهدِ CI): **POS-03** «۳*نام» در صندوق و «فروشِ سریع» ِ گوشیِ نسخهٔ ۳ (`CatalogModel.query`، `CartModel.addCount`)؛
  **POS-15** «قیمت‌پرسی» در منوی «…» ِ صندوقِ گوشی (`sale/price-check`، همان `PosPriceCheckPrefs` ِ ردهٔ A که از `PHONE_HIDDEN` بیرون آمد؛
  سطرها مشترک در `PriceCheckData.lines`)؛ **POS-25** «ارسال» ِ متنِ پیش‌فاکتور (`Quotes.shareText`؛ کامپیوتر کپی)؛ **POS-40** نشانِ
  «اصلاح‌شده» در ستونِ «نوع» (`PayFixFlow.kindLabel`)؛ **INV-09** «مستثنا» و «شمارشِ دوبارهٔ مغایرها» (`CountReview`). مانده‌های کوچک:
  **PAY-17** «همهٔ کشوها با هم» (`Drawers.combine`، `sales/drawer/all`، نه در نمای کور)؛ **RPT-13** ستونِ «دورهٔ قبل/تغییر» در خودِ جدولِ
  «سود و زیان» و «فروشِ هر کاربر» (`core/report/ReportCompare`). بی مهاجرت، بی تغییرِ سرور، بی پروندهٔ پمپ.
- رد شد: STF-02 ِ لغو (برگهٔ تأییدِ لغو در v1 ⇒ `approval_needs_v3` ِ سرور؛ بی موتورِ v3 ِ روشن بی‌اثر)، SYN-07 (صندوقِ تعارضِ v3 هنوز هیچ
  دکمه‌ای ندارد)، PUR-14 (فاکتورِ جدای تأمین‌کننده جدول و مهاجرت می‌خواهد)، CRM-09 (نشانیِ قرض‌دار فیلدِ همگام‌شوندهٔ v1 می‌خواهد).
- نیمه‌کاره: CI و مرجِ همین PR. قدمِ بعدی: شاهدِ CI برای همین هفت ردیف؛ ستونِ مقایسه برای برگه‌های فهرستی فقط با برگهٔ جمع‌بندیِ تازه.
### ۱۴۰۵/۰۸/۱۲ — نگهبانِ مرجِ #877، #878، #880، #882، #883، #885 و #881 (سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: #877 (`013f90e4`)، #878 (`83dcd9f2`، پس از ادغامِ main؛ STATUS با `batch-status.py`، HANDOFF و «تازه‌ها» هر دو
  سمت)، #880 (`9e3f39d0`)، #882 (`eac92fff`) و #883 (`1fcf35b4`) squash شدند؛ `claude/img12` (#881) از نو روی main ساخته
  شد (فقط cherry-pick ِ `f21e3b206`، README §۴.۶) و PR به main برگشت. رفعِ سرخی‌های CI بی سست کردنِ آزمون: axe ِ «سبد
  با عکسِ نسخه‌دار» برگهٔ نیمه‌پیدا را می‌سنجید (`cart-open` در requestAnimationFrame)؛ `PhotoVersionsPageTest` در
  اندروید کامپایل نمی‌شد (java.awt ⇒ بازتاب)؛ سرآیندِ «کشو» در `license/owner-export.js`؛ «روی گوشی» در `versionsNote`؛
  ترتیبِ تصادفیِ سابقه در آزمونِ IMG-PRO-17 (`ORDER BY created_at, target_id, id`، در #881، #883 و #885).
  پس از آن #881 (`73275c2e`)، #885 (`1d2d81e2`) و #886 (`915ff8fb`) هم squash شدند. در #887 سرخیِ «ابزارِ مهاجرتِ داده»
  رفع شد (`DeliveryAddress.kt` ِ CRM-09 در `legacyFiles` ِ `tools/shop-migrate`)؛ نگهبانِ LOC-10 (`loc-no-postal.test.js`)
  کادرِ **اختیاریِ** کدِ پستیِ §۴.۴ را فقط با سطرهای دقیقِ بازبینی‌شده (`REVIEWED_OPTIONAL`) می‌پذیرد؛ و «بی 37035» در
  `customer-notify.test.js` با زمانِ میلی‌ثانیه‌ایِ 17937035xxxxx برخورد می‌کرد ⇒ مرزِ رقم. نیمه‌کاره: CI و مرجِ #887 و
  #879 (آیفون گامِ ۸، فقط پس از گزارشِ «تمام» از نویسنده و سبز شدنِ چکِ آیفون).
  قدمِ بعدی: دورِ تازهٔ شاهدِ CI برای دورِ ۹ و ۱۰، WEB-15، IMG-12 و دورهای ۲ و ۳ ِ MISSING.
### ۱۴۰۵/۰۸/۱۳ — شاخهٔ `claude/img12` (نسخهٔ عکسِ ویترین، IMG-12؛ پس از #880؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: هر کالای عکس‌دار در نمای عمومی `photoVersion` (۱۲ hex) دارد — از (عکس، نسخه)ِ بانک با پرچمِ دکان (`withBank`، حالا
  `?ids=` ِ سبد هم)، وگرنه hash ِ عکسِ همگام‌شده؛ ویترین (کارت، برگهٔ کالا، سبد) و بازارچه `?v=` می‌گذارند. `/photo` و `/thumb` فقط
  وقتی `v` با عکسِ سروشده جور است یک روز کش می‌شوند؛ بی نسخه یا کهنه همان عکسِ امروز با ۵ دقیقه (پیوند/سبد/SEO ِ پیشین نمی‌شکند).
  فیلدِ تازه به v1 ِ قرارداد افزوده شد (`public-views.js`، `market-public-v1.json`، `MarketContract.kt`، مثلِ #467). بارِ نخست
  ۶۹٬۹۹۴ بایت (۲۴۲ بیشتر، سقف دست نخورد). آزمون: `storefront-photo-version.test.js` (۲)، `web-store.mjs` (۱۴ سنجهٔ IMG-12، پیش از
  تغییر ۹ ❌)، ۱۲ آزمونِ مرورگرِ ویترین/بازارچه سبز، `MarketContractTest` ۷/۷.
- نیمه‌کاره: هیچ؛ PR باز است (مرج نشد، به خواستِ صاحب‌کار).
- قدمِ بعدی: CI و مرج پس از #880. IMG-PRO-13 هنوز آزمونِ خودکارِ کشِ Android/Windows می‌خواهد.
### ۱۴۰۵/۰۸/۱۳ — شاخهٔ `claude/missing-rows-3` (کمبودهای MISSING، دورِ ۳؛ روی main پس از مرجِ #882؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: دو ردیفِ بی مانعِ باقی‌مانده ساخته شد و PARTIAL است: **PAY-17** «چند کشو و شیفتِ شناور» (تنظیمِ خاموشِ همین دستگاه،
  ردهٔ B؛ کشوی نام‌دار، مهرِ شیفت روی فروش/برگشت/رسید، «ادامه روی این دستگاه» با `shift_resume`؛ قاعده `core/pay/Drawers`) و
  **IMG-PRO-16** «نسخه‌های عکس» (کوچک/بزرگ در قابِ گوشی و صفحهٔ بزرگ و ۱:۱، بی بزرگ‌نمایی؛ سنجهٔ فشرده‌سازی از سرآیند،
  `core/image/ImageQuality`). صفحهٔ برنامه برای دو درِ سرورِ #882: **IMG-PRO-17** (`stock/images/changes`، پشتِ `shop.image_bank`) و
  **RPT-18** (`reports/branches`، پشتِ `shop.branches_report` و فقط صاحب). بی مهاجرت و بی تغییرِ سرور؛ هیچ پروندهٔ پمپ.
  نیمه‌کاره: CI و مرجِ همین PR (نخست روی `claude/missing-rows-2` ساخته شد؛ #882 میانِ کار squash شد ⇒ فقط سه کامیتِ خودش روی main
  نشست، README §۴.۶).
  قدمِ بعدی: آزمونِ دو دستگاهِ واقعی برای کشوی شناور؛ X/Z ِ همهٔ کشوها با هم؛ صفحهٔ پنل برای RPT-18 و IMG-PRO-17.
### ۱۴۰۵/۰۸/۱۳ — شاخهٔ `claude/ci-flaky-tamper` (ناپایداریِ آزمونِ چتِ ویترین و شاهدِ main برای OPS-05؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: ریشهٔ سرخیِ «زیرِ Electron» روی 83dcd9f2 (run 41585590213، «درِ مشتری /api/store/:slug/chat با بلیتِ ویترین»): بلیتِ «دست‌خورده»
  = دو نویسهٔ آخر ⇒ `xx`؛ HMAC ِ بلیتِ صادرشده در 06:03:43.249Z خودش به `xx` ختم می‌شد (`…WWQxx`) ⇒ همان بلیتِ درست، سرور درست ۲۰۱ داد
  (یک بار در ۴۰۹۶؛ ربطی به Electron ندارد). `h.tampered` در `server/test/helpers.js` و همان رفع در پنج جای هم‌خانواده (market-trust،
  order-add-items، apple-signin، login-hardening، e2e ِ security). بازتولید: ساعتِ ثابت روی ۵۰ لحظهٔ برخورد ⇒ پیش ۵۰/۵۰ سرخ، پس ۰/۵۰؛
  ۲۰۰ بار با ساعتِ واقعی ۰ شکست. OPS-05: `check.test.mjs` گامِ جدا در «کیفیتِ کد»، در push به main هم.
  نیمه‌کاره: CI و مرجِ همین PR. قدمِ بعدی: پس از مرج، در دورِ تازهٔ شاهدِ CI، OPS-05 را با اجرای main ِ همان گام بسنج.
### ۱۴۰۵/۰۸/۱۲ — شاخهٔ `claude/evidence-9` (ممیزیِ شاهدِ CI، دورِ ۹؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: ردیف‌هایی که مرج‌های پس از دورِ هشتم (#877، #878، #880، #882) دست زدند با CI ِ main سنجیده شدند (#875 و #876 ردیفی
  نداشتند؛ #881 و #883 باز‌اند). INV-05 ⇒ IMPLEMENTED-VERIFIED (013f90e4: «توحید بومی» 41581868063، «فروشگاه من — کامپیوتر» 41581868144).
  OPS-05 PARTIAL ماند (`check.test.mjs` هنوز فقط در PR). ۲۱ ردیفِ دیگر «مانده» یا «نیست» دارند. «سرور» ِ main روی 83dcd9f2 (#878) با یک
  شکستِ «زیرِ Electron» («درِ مشتری /api/store/:slug/chat با بلیتِ ویترین») سرخ شد و روی 9e3f39d0 سبز. جدول: ۳۷۵ / ۳۱۴ / ۳۷ / ۱۲ / ۲۶.
  نیمه‌کاره: CI و مرجِ همین PR. قدمِ بعدی: دورِ دهم پس از مرجِ #881 و #883 و پایانِ CI ِ eac92fff؛ نگاه به ناپایداریِ همان آزمونِ چت.
### ۱۴۰۵/۰۸/۱۳ — شاخهٔ `claude/missing-rows-2` (کمبودهای MISSING ِ بی مانع، دورِ ۲؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: هر ۴۲ ردیفِ MISSING با مانعش (جدول در PR): ۲۵ تصمیمِ صاحب‌کار، ۷ تعویق (§۴۰.۵ / D-13)، ۳ بیرونی، ۷ بی مانع.
  سه ردیفِ بی مانع ساخته شد و PARTIAL است: POS-16 (موجودیِ هر محل در برگهٔ ردیفِ سبدِ صندوق، پشتِ «محل‌های نگه‌داری»)،
  IMG-PRO-17 (تغییرِ دسته‌ایِ عکسِ کالا و برگشتِ یک‌جا با سابقهٔ کامل، مهاجرتِ ۲۰۷) و RPT-18 (گزارشِ همهٔ شعبه‌های یک صاحب،
  پرچمِ خاموشِ `shop.branches_report`). IMG-BANK-18 (#463) و IMG-PRO-11 (`GuidedCapture`) پیش‌تر ساخته شده بودند و STATUS جا
  مانده بود ⇒ PARTIAL با نامِ آزمون. نیمه‌کاره: CI و مرجِ همین PR؛ صفحهٔ برنامه برای IMG-PRO-17 و RPT-18 نیست.
  قدمِ بعدی: آن دو صفحه، و PAY-17 و IMG-PRO-16 (بی مانع ولی بزرگ‌تر).
### ۱۴۰۵/۰۸/۱۲ — شاخهٔ `claude/sf-budget` (بودجهٔ بارِ نخستِ ویترین، WEB-15؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: JS ِ بارِ نخستِ ویترین (store.js + kit.js + boot.js) ۷۶٬۷۳۳ ⇐ ۶۹٬۷۵۲ بایت (۶٬۹۸۱ کمتر؛ بازارچه ۶۵٬۸۹۸ ⇐ ۶۰٬۴۶۵) بی
  بالا بردنِ سقف: پنجرهٔ تنظیمات ⇐ `kit/settings.js` (با زدنِ دکمه)، `K.normPhone/keyFor/dropKey/confirm/reason/orderSteps` ⇐
  `kit/order-kit.js` (همراهِ checkout.js و order.js)، پیگیریِ زنده و برگهٔ خطای سفارش ⇐ order.js (`open/fail/stop`)، تنظیمِ
  روش‌های دریافت ⇐ checkout.js، نشانه‌های فقط-بازارچه و `K.photo` ⇐ market.js، نشانهٔ بی‌کارِ `bag` برداشته، `K.js` هر نشانی
  یک بار. سقفِ آزمون‌ها یک‌جا در `server/test/storefront-budget.js`: چرخ‌دنده‌ای ۷۰KB (سند ۷۵KB هرگز بالا نمی‌رود). سبد و
  برگهٔ سبد عمداً در بارِ نخست ماندند (بی اینترنت کار می‌کنند). ۱۲ آزمونِ مرورگرِ ویترین/بازارچه سبز (۵۱۶ ✅، ۸ تازه)، ۲۰۸ آزمونِ سرور.
- نیمه‌کاره: هیچ؛ PR باز است (مرج نشد، به خواستِ صاحب‌کار).
- قدمِ بعدی: CI ِ PR و مرج. IMG-12 و هر کارِ تازهٔ ویترین: کدِ نخستین نقاشی فقط تا سقفِ چرخ‌دنده‌ای؛ بیشتر ⇒ دلیل در PR و بالا
  بردنِ `FIRST_LOAD` (تا ۷۵KB)، وگرنه پروندهٔ دیربار با `K.js`.
### ۱۴۰۵/۰۸/۱۳ — شاخهٔ `claude/gaps10-partial` (کمبودهای PARTIAL، دورِ ۱۰؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: **SEC-03** لغو و «اصلاح» ِ تاریخچهٔ نسخهٔ ۳ (`CancelRoute`/`FixRoute`) از `SalesVoid.run` (= `InvoiceVoid.check` ⇐
  `SalesEngine.cancel`)، لغوکننده با `FraudTrailPref.by` و `DocChainSync.recordVoid` فقط پس از ذخیرهٔ موفق؛ **RPT-04** (S48) کلیک یا
  Enter روی ردیفِ روشِ «پایان روز» ⇒ ریزِ تراکنش‌ها (`DayEnd.lines`، `DayEndModel.details`، جمع = «خالص دفتر»)، و `reports.drill` ِ
  keymap «partial» (**RPT-13**)؛ **RPT-08** «ساعت و روز» در «گزارشِ تازه» ِ گوشی (`HeatmapPhone`، فقط با دیدنِ پول)؛ **RPT-14** صفحهٔ
  «گزارشِ فروشِ اینترنتی» (`reports/online`، `core/report/OnlineReport`، راهش فقط با پاسخِ روشنِ سرور). همه PARTIAL ماندند (شاهدِ CI).
  بی مهاجرت و بی تغییرِ سرور.
- STATUS ِ کهنه (یافتهٔ دورِ ۹) با نامِ آزمون‌ها بازنویسی شد: NTF-10 (مانده فقط D-09)، CRM-03 (مانده سنجشِ سرور)، CRM-13 (مانده شاهدِ CI
  و برنامهٔ خرید O-04)، INV-20/21 (صفحهٔ نسخهٔ ۳ هست؛ مانده سری/انقضا، فروش از محل، BOM).
- رد شد: IMG-12 (بودجهٔ بارِ نخستِ ویترین فقط ۶۷ بایت جا دارد؛ نشانیِ نسخه‌دارِ عکس بی کوچک کردنِ store.js/kit.js جا نمی‌شود)،
  ستونِ مقایسهٔ RPT-13 (برگه‌های `ReportCsv` متن‌اند و ردیف‌به‌ردیف جفت نمی‌شوند).
- قدمِ بعدی: CI ِ همین PR و ادغام در دسته (کنارِ `claude/gaps9-partial`؛ `WHATS-NEW-fa.md` و `HANDOFF-fa.md` هر دو سمت).
### ۱۴۰۵/۰۸/۱۳ — شاخهٔ `claude/gaps9-partial` (کمبودهای PARTIAL، دورِ ۹؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: **CRM-16** دکمهٔ «کارتِ مشتری» در پروندهٔ مشتریِ گوشیِ نسخهٔ ۳ (`DebtorFileExtras`، همان `CustomerCardButton`)؛ **POS-18** Ctrl+R
  ِ صندوقِ کامپیوتر خرده ⇄ عمده (`PosKeys.priceList`، keymap «partial»، فقط با `PriceListPrefs` روشن)؛ **PUR-05** فاکتورِ خریدِ چندردیفه
  در «ثبت خرید» ِ گوشی (`PurchaseFormModel.more` ⇐ `PurchaseBook.record`؛ تک‌ردیفه همان `addPurchase`)؛ **CRM-07** سفارش‌های باز/لغوشدهٔ
  مشتری در پروندهٔ گوشی و کامپیوتر (`GET /api/shop/orders/customer`، `orders.contact`، سطلِ IP پیش از احراز؛ `CustomerOnlineOrdersPanel`)؛
  **INV-05** صافیِ برند و قفسه در هر دو پنجرهٔ انبارگردانی (`CountFilter.brands/shelves`)؛ **PRM-08** برندِ کالا به `PromoCore` و برندِ
  شامل/مستثنا در ویرایشگرِ تخفیف. همه PARTIAL ماندند (شاهدِ CI). بی مهاجرت.
- یافته: «مانده» ِ چند ردیف کهنه است و STATUS ِ آن‌ها دست نخورد (برای ممیزیِ بعد): NTF-10 صفحهٔ سیاست در برنامه هست
  (`LateApologyScreen`)، CRM-03 صندوقِ نسخهٔ ۳ تأییدِ سقف دارد (`CheckoutRoute`/`CreditApprovalRoute`)، CRM-13 امتیاز در حسابِ وب هست
  (`store-account-history.js`)، INV-20/21 صفحهٔ نسخهٔ ۳ دارند (`StockPlacesRoute`).
- رد شد: INV-10 (پیش‌نویسِ `StockBook` با برگهٔ لحظهٔ آغاز کار می‌کند ⇒ D-19)، LOC-04 (هیچ صفحه‌ای `isWorkingDay` نمی‌خواند؛ گرفتنِ جدول اثری
  نمی‌داشت)، POS-09 ِ نسخهٔ ۳ (بسته‌ها و قیمتِ بسته، بزرگ).
- قدمِ بعدی: CI ِ همین PR و ادغام در دسته.
### ۱۴۰۵/۰۸/۱۲ — آیفون گامِ ۸، شاخهٔ `claude/ios-app-phase8` (روی main پس از #875؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: آزمونِ سرتاسریِ جریان‌های اصلی روی شبیه‌ساز در کارِ `ios-app` (`iosSimulatorArm64Test`): ریشهٔ برنامه (`IosRoot`) روی
  Skia ِ بی‌صفحه با زدن و نوشتن روی صفحه — بالا آمدن تا ورود، ورود با ایمیل و رمز، فروشِ نقد در صندوق (`TillSale.record`)،
  کالای تازه، «تازه‌ها»، زبانِ دری — در برابرِ سرورِ واقعیِ مخزن روی PGlite (`ios/e2e/backend.mjs`)، با سنجشِ دفتر و `sync_rows` ِ
  سرور و عکسِ هر گام (مصنوعِ `ios-e2e-shots`). لایهٔ iOS: `IosNetRoute` (فقط آزمون)، `IosHost` (اعلان و لرزش بیرون از `.app`
  بی‌صدا). شرح: `docs/IOS-PLAN-fa.md` §۰.۱۳.
- آزمون: klib و klib ِ آزمونِ iOS سبز (Compose 1.7.3 ِ محلی)، `ios/shared` jvmTest ۲۰/۲۰؛ خودِ آزمونِ سرتاسری فقط روی macOS.
- نیمه‌کاره: نتیجهٔ نخستین اجرای CI ِ macOS (در §۰.۱۳ نوشته می‌شود).
- قدمِ بعدی: سبز کردنِ گام‌های سرخ (اگر بود) از روی عکس و درختِ `ios-e2e-shots`؛ سپس همین راه برای حالتِ پیک و برگشتِ فروش.

### ۱۴۰۵/۰۸/۱۲ — شاخهٔ `claude/split-big-files` («شکستنِ فایل‌های بزرگ»، پیگیریِ سقف‌های موقتِ #690؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: بازسازیِ خالص، هر پرونده یک کامیت، همان خط‌ها جابه‌جا: `storefront.js` ۱۱۴۲⇐۷۸۰ (`storefront-ticket`، `storefront-owner`)،
  `delivery.js` ۱۱۷۰⇐۷۳۵ (`delivery-views/recall/call/photos/settle`)، `catalog.js` ۹۱۸⇐۷۴۷ (`catalog-manual`)، `market.js` ۸۱۱⇐۷۳۸
  (`market-cards`)، `otp.js` ۸۷۷⇐۷۲۸ (`otp-desk`)، `public/admin/app.js` ۲۴۳۶⇐۶۸۰ (`shops/platform/support/commerce/start.js`). شش سقفِ
  موقت از baseline.json برداشته شد؛ هیچ سقفی بالا نرفت. مسیرها و فهرست‌هایشان دست نخورد؛ خروجیِ هر ماژول با main یکی.
- نیمه‌کاره: `admin/index.html` (۱۳۸۷) شکسته نشد — HTML ِ ایستا بی بار کردنِ پویا شکستنی نیست. زبانهٔ پمپ در `admin/app.js` عمداً ماند.
- قدمِ بعدی: CI ِ PR و مرج. پایگاهِ محلیِ 55432 (`shop_test`) SQL_ASCII است ⇒ آزمون‌های بازارچه رویش سرخ‌اند؛ برای PostgreSQL ِ محلی
  پایگاهِ UTF8 با «test» در نام بسازید (ابزارِ بار بی آن رد می‌کند).
### ۱۴۰۵/۰۸/۱۳ — شاخهٔ `claude/gaps8-partial` (کمبودهای PARTIAL، دورِ ۸، و فهرستِ تصمیم‌های صاحب‌کار؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: **PAY-19** Shift+F8 ِ صندوقِ کامپیوتر ⇐ پنجرهٔ دلیلِ «کشو بی فروش» (`PosKeys.noSaleDrawer`، `DrawerNoSale.keyGate`؛ keymap «partial»)؛
  **PRM-02** آزادِ خودکارِ کدهای سقف‌دار با لغو/اصلاحِ فاکتورِ نسخهٔ ۳ (`PromoLive.releaseOnVoid` + صفِ بی‌اینترنت، `PromoRedeem.release*`)؛
  **PAY-13** «شمارشِ کور» تنظیمِ دکان شد (`ShopPolicy.blindCount`، ردیف در `settings/sales/payments`، `ShiftSlip.blind`)؛ **SYN-14** عکس‌ها
  قابلِ ادامه و پس از دفتر (`core/sync/PhotoSyncPlan`، `SyncV1Engine.ledgerBusy`). هر چهار PARTIAL ماندند (شاهدِ CI). بی مهاجرت.
- **`docs/OWNER-DECISIONS-fa.md`**: ۳۰ تصمیمِ باز (D-xx و O-01..O-19) با پرسشِ ساده، گزینه‌ها، پیشنهاد و ردیف‌هایی که باز می‌کنند (۹۴ ردیف).
- نیمه‌کاره / یافته: صندوقِ پرداختِ گوشیِ نسخهٔ ۳ (`CheckoutRoute`) کدِ سقف‌دار را پیش از ثبت از سرور نمی‌گیرد (`redeemGate` فقط در صندوقِ
  کامپیوتر) — در «مانده» ِ PRM-02 آمد؛ چون #862 همان فایل را داشت در این PR دست نخورد.
- قدمِ بعدی: CI ِ همین PR؛ `gate` ِ `redeemGate` در `CheckoutRoute` (#862 حالا مرج شده)؛ جوابِ صاحب‌کار به `OWNER-DECISIONS-fa.md`.

### ۱۴۰۵/۰۸/۱۲ — آیفون گامِ ۷، شاخهٔ `claude/ios-app-phase7` (روی main پس از #861 و دستهٔ ۱۵؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: `ios-exclude.txt` ۱۳ ⇐ ۱۲ و هیچ سطرِ بی‌دلیل (۴ فقط-اندروید، ۸ همتای iOS؛ دلیلِ هر سطر در خودِ فایل و جدولِ
  `docs/IOS-PLAN-fa.md` §۰.۱۲). `data/DeviceLocation.kt` ِ اندروید با shim ِ `android.location` (CoreLocation) روی آیفون و
  `DeviceLocationIos.kt` پاک شد. بدنهٔ `setContent`، `CrashNotice`، پیوند، لمسِ اعلان و همگام‌سازیِ `MainActivity` به
  `AppShellAndroid.kt` (`AppShell`، `AppStart`) رفت و ریشهٔ آیفون همان را صدا می‌زند (رونوشت‌ها پاک شد). `ScanGate` ⇐ `core`
  (یک نسخه به‌جای سه)، `versionOf`/`isNewer` ⇐ `update/ReleaseVersion.kt` (یک نسخه به‌جای سه). iOS: ۱۱۵۱ ⇐ ۱۱۵۴ از ۱۱۶۶ فایل.
- آزمون: klib و klib ِ آزمونِ iOS سبز (Compose 1.7.3 ِ محلی)، `ios/shared` jvmTest ۱۸/۱۸، `Phase7IosTest` (۶؛ فقط شبیه‌ساز) با
  حساب‌پیِ سادهٔ دستهٔ ۱۴، دری و `TillSale.record` ِ دستهٔ ۱۵؛ هسته ۱۱۷۲/۱۱۷۲، کامپیوتر ۲۷۸۷ با همان ۱۳ ِ شناختهٔ `ui.theme`.
- نیمه‌کاره: اندروید این‌جا ساخته نمی‌شود (SDK نیست)؛ `MainActivity` را فقط CI می‌سازد. `desktop/…/Main.kt` هنوز رونوشتِ پوسته را دارد.
- قدمِ بعدی: CI ِ macOS (`ios-app`) برای `Phase7IosTest`؛ تصمیمِ صاحب‌کار دربارهٔ لرزشِ یکسان (Core Haptics)؛ بردنِ کامپیوتر به `AppShell`.

### ۱۴۰۵/۰۸/۱۲ — حسابِ دوم: سقفِ کدِ تخفیف در صندوقِ گوشیِ نسخهٔ ۳ (شاخهٔ `claude/promo-cap-phone`، سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد (PRM-02؛ یافتهٔ دورِ ۸ ِ کمبودها، #870): `CheckoutRoute` (`sale/checkout`) فاکتور را بی گرفتنِ کدِ سقف‌دار از سرور ثبت
  می‌کرد ⇒ سقف از گوشی دور زده می‌شد. حالا همان `gate = { key -> PromoLive.redeemGate(context, key, promo) }` ِ کامپیوتر را
  به `cart.post` می‌دهد، و قاعده (کدام کد، بی اینترنت ⇒ «فقط با اینترنت» و هیچ ثبتی، رد ⇒ پیامِ خودِ سرور و هیچ فروش و تخفیفی،
  پرچمِ خاموش یا کدِ بی سقف ⇒ بی پرسش) از `PromoLive` به `TillSale.redeem` رفت تا یک جا و آزمون‌پذیر باشد؛ رفتارِ کامپیوتر،
  `Sale` و `QuickSale` همان است. سازگار با آزادِ #870: کلیدی که سرور می‌شمارد همان شناسهٔ فاکتور است.
- آزمون: `PhoneTillWiringTest` ِ تازه (هر `.post(store` ِ هر صندوق با دروازه) روی کدِ main سرخ بود و حالا سبز؛
  `TillSaleRedeemTest` ۴/۴ (پرشده، گرفت، آفلاین/بی پاسخ، بی پرسش). desk-check: هسته ۱۱۹۲/۱۱۹۲، کامپیوتر ۲۷۹۹ (فقط ۱۳ ِ
  `ui.theme`)؛ سرور روی PGlite: `promo-redemptions`، `shop-promos`، `promo-core` ۸۹/۸۹.
- نیمه‌کاره: هیچ؛ PR باز و مرج‌نشده (به خواستِ هماهنگ‌کننده). قدمِ بعدی: CI ِ همین PR، سپس دسته با #870 (هر دو `PromoLive.kt`
  را عوض می‌کنند، بخش‌های جدا؛ ادغامِ دستی اگر تعارض داد).

### ۱۴۰۵/۰۸/۱۲ — «تازه‌ها» از پروندهٔ فارسی، شاخهٔ `claude/whats-new-changelog` (سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- خواستِ صاحب‌کار: پیش‌نمایش را از ۶۰ به ۶۴ برد و نفهمید چه عوض شد. **ریشه:** یادداشت‌های OPS-05 نسخهٔ «2.14.5» داشتند و برنامه
  «3.2.N» است ⇒ پنجرهٔ پس از به‌روزرسانی هرگز باز نمی‌شد؛ پیش‌نمایش (۳٫۲٫۶۶) و آزمایشی (۳٫۲٫۱۹۲۰) هم از یک کد شمارهٔ جدا دارند.
- انجام شد: تنها منبع `docs/WHATS-NEW-fa.md` (قاعده‌ها سرِ همان پرونده)، همراهِ برنامه با `generateWhatsNew` ِ core؛ `core/guide/WhatsNew`
  («دیده‌شده» با کلیدِ هر سطر؛ گذر از نسخهٔ دیده‌شدهٔ پیشین با `shipped` ِ هر کانال)؛ برگهٔ یک‌باره، صفحهٔ «بیشتر» ← «تازه‌ها» (`whats-new`،
  کامپیوتر هم)، کلیدِ ردهٔ A `SettingsLayout.WHATS_NEW`؛ `WhatsNew.ALL` ِ درونِ کد برداشته شد (یادداشتِ #865 در بخشِ ۶۱ است). سطرهای
  ۶۱ تا ۶۶ با main سنجیده شد؛ «ورودِ گوگل با رمز/کدِ ایمیل» ِ فهرستِ هماهنگ‌کننده برداشته شد (پیش از ۶۱ بود؛ #861 فقط کدش را جابه‌جا کرد).
  CI: `tools/whatsnew/check.mjs` (هشدارِ «کیفیتِ کد»)، سدِ `finish-pr.sh` (`WHATSNEW_OK=<دلیل>`)، `bmerge.sh` تعارضِ پرونده را دوسویه حل می‌کند.
- نیمه‌کاره: بخش‌های تازه شمارهٔ ساخت ندارند (عنوانِ برگه تاریخ و موضوع است)؛ شاهدِ CI.
- قدمِ بعدی: مرج؛ از این پس هر PR ِ کدِ برنامه سطرِ خودش را می‌آورد و هماهنگ‌کننده سرخطِ بخشِ هر دسته را می‌نویسد.

### ۱۴۰۵/۰۸/۱۲–۱۳، نیمه‌شب — حسابِ دوم: #861، دسته‌های ۱۵، ۱۶ و ۱۷ (سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: #861 (آیفون گامِ ۶) main را گرفت (تعارض فقط در این فایل و `docs/IOS-PLAN-fa.md`؛ هر دو سمت ماند، §۰.۱۰ گامِ ۵ و
  §۰.۱۱ گامِ ۶) و با ۳۱ چکِ سبز مرج شد (`3fccb68e`). دستهٔ ۱۵ به این ترتیب: #862 (راهِ نسخهٔ ۳ برای کارهای بی‌راه، با PRM-13 و
  POS-11 در صندوقِ گوشی) ⇐ #860 (پاک‌سازی، گامِ ۳؛ تنها هم‌پوشانی یادداشتِ تهِ STATUS، هر دو ماند) ⇐ main (#861) ⇐ #864 (HW-08
  وب‌کمِ کامپیوتر، S109 شمارشِ منطقه‌ای، مهاجرتِ ۲۰۶؛ به خواستِ هماهنگ‌کننده در همین دسته). تعارضِ #864 فقط در فهرست‌ها بود
  (`I18nTables`: `StrReach` و `StrMarketRegion`؛ `SettingsHelp`: `SERVERS` و `WEBCAM`)، هر دو ماند. دو سرخِ CI ِ #864 رفع شد، بی
  سست کردنِ آزمون: `I18nCoverageTest` (یادداشتِ فقط-گوشیِ وب‌کم «روی گوشی…خودِ گوشی» عبارتِ جدولِ کامپیوتر داشت ⇒ متن بی آن
  عبارت‌ها) و S117 ِ `card-data.test.js` (کدِ ISO 3166-2:AF ِ پنجشیر `'PAN'` در `af-provinces.js` ⇒ استثنای دقیقِ پرونده + همهٔ
  سطر، با سنجشِ «استثنای بی‌کار»؛ افزودنِ `pan` به همان سطر هنوز سرخ است).
- آزمونِ محلی (درختِ همین دسته): هسته ۱۱۶۹/۱۱۶۹؛ کامپیوتر ۲۷۸۷، فقط ۱۳ ِ شناختهٔ `ui.theme` (`V3ReachabilityTest` ۶/۶،
  `NoLegacyUiTest` ۵/۵، `PhoneTillWiringTest` ۳/۳، `TillSaleTest` ۵/۵، `I18nCoverageTest` ۸/۸، `HubServerTest` ۱۱/۱۱)؛ سرور روی
  PGlite: ۳۷ پرونده‌ای که منبعِ برنامه را می‌خوانند ۳۳۹/۳۳۹ (پیش از #861/#864)، `card-data` ۳/۳.
- نیمه‌کاره: KPIهای زبانه‌های تازهٔ گزارشِ کامپیوتر (فقط ضایعات و شماره‌های جاافتاده)؛ `Purchasing.kt` ِ گوشی و ۱۱
  `…SettingsWired` ِ `ui/screens/Settings.kt` (فقط `DevShot`) هنوز هستند.
- دستهٔ ۱۵ با ۳۶ چکِ سبز مرج شد (#866 ⇐ `bb9a4332`؛ #860 #862 #864 بسته شدند). کلِ سرور روی PGlite روی همان درخت: ۲۸۳۰/۲۸۵۲
  (۵ ِ همیشگیِ `pg_dump` و S117 ِ پیش از رفع). دستهٔ ۱۶ روی آن: #865 (کمبودهای دورِ ۷: WEB-24 بنرها، CHK-10 «خبرم کن»،
  CRM-02/11 و OPS-05 با نسخهٔ ۳) ⇐ #867 (AI-06 پیشنهادِ دسته و نام، بی شبکه و بی مهاجرت). تعارض فقط HANDOFF، STATUS
  (`batch-status.py`) و `I18nTables` (`StrProductSuggest` کنارِ `StrReach`/`StrMarketRegion`). آزمونِ محلیِ دستهٔ ۱۶: هسته ۱۱۹۲/۱۱۹۲،
  کامپیوتر ۲۷۹۴ (فقط ۱۳ ِ `ui.theme`؛ `V3ReachabilityTest` ۶/۶، `NoLegacyUiTest` ۵/۵)، سرور ۴۲ پرونده ۳۵۹/۳۵۹.
- دستهٔ ۱۶ با ۳۱ چکِ سبز مرج شد (#868 ⇐ `5d8f72bc`؛ #865 #867 بسته شدند). دستهٔ ۱۷ روی آن: #869 (آیفون گامِ ۷) ⇐ #870 (کمبودهای
  دورِ ۸: PAY-19، PRM-02، PAY-13، SYN-14 و `docs/OWNER-DECISIONS-fa.md`). تعارض فقط HANDOFF و `tools/quality/baseline.json`: سقف‌های
  main به‌علاوهٔ پایین آمدنِ `BarcodeScanner.kt` در #869، `StrOnline` همان ۹۱۸ ِ دستهٔ ۱۶، سطرِ `TopBar.kt` ِ پاک‌شده رفت. آزمونِ
  محلیِ دستهٔ ۱۷: هسته ۱۲۰۰/۱۲۰۰، کامپیوتر ۲۸۰۰ (فقط ۱۳ ِ `ui.theme`؛ `V3ReachabilityTest` ۶/۶، `NoLegacyUiTest` ۵/۵، `KeyMapTest`
  ۹/۹)، سرور ۳۷ پرونده ۳۳۹/۳۳۹. یک بار ظرف از نو راه افتاد (صبرهای پس‌زمینه کشته شد؛ چیزی از دست نرفت).
- #872 (رفعِ امنیتیِ سقفِ کدِ تخفیف در صندوقِ گوشی) با CI ِ سبز جدا و پیش از دسته مرج شد (`6d4158d9`)؛ دستهٔ ۱۷ main را گرفت
  (HANDOFF و ردیفِ PRM-02 ِ STATUS: یافتهٔ «صندوقِ گوشی بی دروازه» ِ #870 برداشته شد، «مانده»‌ها یکی شد) و #873 («تازه‌ها» از
  `docs/WHATS-NEW-fa.md`) هم در آن آمد، با سطرهای کاربرپسندِ #870 و #872 (کشو با Shift+F8، آزاد شدنِ کدِ محدود با لغو/اصلاح،
  «شمارشِ کور» در تنظیمات، ادامهٔ عکس‌ها، سنجشِ کدِ محدود در صندوقِ گوشی) زیرِ بخشِ بالا. از این پس هر دستهٔ کدِ برنامه سطرِ «تازه‌ها» می‌خواهد.
- قدمِ بعدی: مرجِ دستهٔ ۱۷ و بستنِ #869 #870 #873؛ دورِ هشتمِ شاهدِ CI (PRM-13، POS-11، RPT-17، HW-08، S109، WEB-24، CHK-10، AI-06،
  PAY-19، PRM-02، PAY-13، SYN-14، OPS-05)؛ چکِ «Xcode (گزارش)» (سد نیست) روی خودِ #869 هم سرخ است: پیوندِ
  `linkDebugFrameworkIosSimulatorArm64` با «symbol multiply defined: `OBJC_CLASS_$_TohidSharedDouble`» — کارِ گامِ بعدیِ آیفون؛ گامِ ۴ ِ پاک‌سازی.

### ۱۴۰۵/۰۸/۱۲ — حسابِ دوم: راهِ نسخهٔ ۳ برای کارهای بی‌راه (شاخهٔ `claude/v3-reach`، سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: پس از دستهٔ ۱۳ حدودِ ۴۰ کارِ موجود (محل‌ها، انبارگردانیِ تازه، دفترِ موجودی، برچسب، ورودِ گروهی، PRD-20، بانکِ تصاویر،
  بخش‌های STF و ساعتِ کار، زبانه‌های اختیاریِ گزارش و پرسشِ ساده، S25 در گوشی، ابزارهای سبدِ صندوقِ گوشی، کارهای سفارش و میزِ
  تحویل، کارزار و رسانهٔ ویترین، ابزارهای سخت‌افزار، «سرورها») هیچ راهی در نسخهٔ ۳ نداشتند؛ هر کدام به منو/«…»/دستهٔ تنظیمات/صفحهٔ
  کارمندان وصل شد (فهرستِ کامل در «یادداشت‌ها»ی STATUS، ۱۴۰۵/۰۸/۱۲). نگهبان: `V3ReachabilityTest` (نقشهٔ ایستای صدا زدن از
  `AppRoot` + مسیر در جدولِ نسخهٔ ۳؛ روی main سرخ). با #860 (پاک‌سازی، گامِ ۳) هیچ پروندهٔ مشترکی جز STATUS ندارد.
- همان PR: امتیازِ وفاداری (PRM-13) و یادداشتِ فاکتور (POS-11) در صندوقِ گوشیِ نسخهٔ ۳ تا ته — ثبت با `TillSale.record` (همان
  زنجیرهٔ کامپیوتر)، `TillSaleTest` و `PhoneTillWiringTest`. نیمه‌کاره: KPIهای زبانه‌های تازهٔ گزارشِ کامپیوتر فقط برای ضایعات و
  شماره‌های جاافتاده (بقیه جدولِ برگه).
- قدمِ بعدی: بازبینی و CI ِ سبزِ همین PR ⇐ مرج (این PR خودکار مرج نشد)؛ پس از مرجِ هر دو، `Purchasing.kt` ِ گوشی هم
  بی‌مرجع است و در گامِ بعدیِ پاک‌سازی برود.

### ۱۴۰۵/۰۸/۱۲ — شاخهٔ `claude/gaps7-web-crm` (کمبودهای PARTIAL، دورِ ۷ — ویترین/مدیریتِ وب و سنجشِ CRM/OPS)
- انجام شد: **WEB-24** برگهٔ «بنرها» در صفحهٔ ویترینِ نسخهٔ ۳ (گوشی و کامپیوتر) فقط با کلیدِ تازهٔ `banners` ِ `/storefront/config`
  (پرچمِ `shop.storefront.banners` + اجازهٔ مدیریت)؛ سنجش در `core/storefront/StorefrontBanners` (همان قاعده‌های سرور، روز به وقتِ
  دکان، بنری که فقط ناحیهٔ برداشته‌شده دارد ⇒ خطا نه «همه‌جا»). **CHK-10** فهرستِ «خبرم کن» ِ بیرونِ محدوده زیرِ «ارسال و زمان»
  (`AreaRequestsSection`، کلیدِ `notifyArea` ِ `/storefront/config`؛ زنگ با شماره‌گیر، «خبر دادم»). **OPS-05** یادداشتِ «برداشته‌شده» برای
  شکلِ قدیم و کلیدِ «رابطِ تازه (نسخهٔ ۳)» (دستهٔ ۱۳ بی‌صدا برداشته بود). CRM-02 و CRM-11 با جای نسخهٔ ۳ سنجیده و بازنوشته شدند.
- رد شد (دلیل در PR): WEB-10/CHK-20 (D-09)، WEB-11/WEB-14 (وب نشستِ حساب ندارد ⇐ §۴۰.۵ گامِ ۳)، WEB-13/CHK-14 (برنامهٔ خرید)،
  ORD-15 (کسرِ سفارشِ پرداخت‌شده تا دفترِ بازپرداخت حالتِ «معلق» بگیرد عمداً ۴۰۹ است — RULES «v3 · سفارش»؛ ردِ قلم دمِ در بزرگ است).
- بی مهاجرت. نیمه‌کاره: شاهدِ CI ِ این ردیف‌ها؛ ORD-15 (ردِ قلم دمِ در).
- قدمِ بعدی: CI ِ PR و ادغام در دسته؛ سپس دورِ تازهٔ شاهدِ CI.

### ۱۴۰۵/۰۸/۱۲ — شاخهٔ `claude/ai-rows` (ردیف‌های MISSING ِ هوشِ مصنوعی: AI-06/09/10/12)
- انجام شد: **AI-06** فقط بخشِ قطعی و بی هوشِ مصنوعی (⇒ PARTIAL): `core/product/ProductSuggest` (دستهٔ پیشنهادی فقط از دسته‌های موجود با
  واژه‌های مشترکِ نام؛ «افزودن از متن» که از هر سطر فقط نام برمی‌دارد و قیمت/بارکد را کنار می‌گذارد و هیچ‌جا نمی‌نویسد)، تراشه‌های «دستهٔ
  پیشنهادی» در فرمِ کالای تازه و ویرایشِ نسخهٔ ۳ و برگهٔ «افزودن از متن»؛ تنظیمِ ردهٔ A «پیشنهادِ دسته و نام» (`ProductSuggestPref`).
- ساخته نشد (با دلیل در PR و ستونِ «مانع» ِ STATUS): AI-09 (مدلِ زبانیِ خودمیزبان روی سرور = تصمیمِ صاحب‌کار)، AI-10 (OCR، همان PUR-12)،
  AI-12 (ویترینِ مشتری + خواندنِ دست‌نوشته).
- نیمه‌کاره: «توضیح» و «از عکس» ِ AI-06 (جورِ دسته از «مشابه» ِ AI-11 وقتی AI-11 به رابطِ نسخهٔ ۳ آمد)؛ شاهدِ CI.
- قدمِ بعدی: CI ِ PR را ببین و مرج کن؛ برای AI-09/10/12 تصمیمِ صاحب‌کار دربارهٔ مدلِ خودمیزبان (سخت‌افزار، مجوز، نماهای امن).

### ۱۴۰۵/۰۸/۱۲ — آیفون گامِ ۶، شاخهٔ `claude/ios-app-phase6` (روی #852؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: ورود با اپل — سرور (`lib/apple.js`، `/auth/apple/nonce`، `/auth/apple`، `/auth/apple/link-code`؛ جدولِ مشترکِ `PROVIDERS` با گوگل،
  nonce ِ HMAC ِ یک‌بارمصرف؛ بی `APPLE_CLIENT_IDS` خاموش)، کدِ مشترک (`data/ExternalSignIn.kt`، `AuthController.apple`، `AppleButton`؛ اندروید و
  کامپیوتر دکمه ندارند) و پنجرهٔ iOS (`ios/AppleSignInIos.kt`). `ios-exclude.txt` ۱۹ ⇐ ۱۳ (TokenStore، FileName، Fingerprint، Integrity،
  TohidBackupAgent، HesabPayHceService)؛ ۱۱۴۲ از ۱۱۵۵ فایل برای iOS (`docs/IOS-PLAN-fa.md` §۰.۱۱). دستهٔ ۱۳ (#855) در شاخه ادغام شد.
- نیمه‌کاره: آزمونِ شبیه‌ساز (`Phase6IosTest`) و xcodebuild فقط در CI ِ macOS؛ ورودِ اپل روی گوشیِ واقعی منتظرِ حسابِ اپل (§۰.۶ بندِ ۵).
- قدمِ بعدی: مرجِ #852 و سپس همین PR؛ اگر صاحب‌کار حسابِ اپل داد: entitlements ِ `applesignin` + `aps-environment` و `APPLE_CLIENT_IDS`.

### ۱۴۰۵/۰۸/۱۲ — شاخهٔ `claude/gaps6-dlv-admin` (کمبودهای PARTIAL، دورِ ۶ — اول پیک و مدیریت)
- انجام شد: **RSK-08** مسئول و مهلتِ اختلاف (§۱۹.۹ بندِ ۵): `POST /api/shop/orders/evidence/:id/disputes/assign` (فقط‌افزودنی در
  `audit_logs`، مسئول = صاحب یا عضوِ فعالِ دارای `orders.manage`، مهلت تا ۹۰ روز، `overdue` فقط نما)، فهرستِ `assignees` در پرونده، چاپ،
  و «سپردن به مسئول» در هر دو برگهٔ برنامه؛ **DLV-10** کارت‌های پیک با رمزِ دفترِ دستگاه (`CourierSeal` ⇐ `AtRest`، پشتِ همان
  `atrest_encrypt`)؛ **OPS-07** مهرِ «آموزشی» روی برگهٔ سفارش و برچسبِ کیسه؛ **RPT-17** «شماره‌های جاافتاده» در «گزارشِ تازه»؛
  **PAY-07** وضعیتِ پرداخت در فاکتورهای کامپیوتر (`DeskInvoices`: ستونِ وضعیت و بخشِ «وضعیتِ پرداخت» با همان `PayStatusFlow`).
- بی مهاجرت. DLV-14 دست نخورد: «زمانِ هر ناحیه» و «ناحیه در سایت» را WEB-04 (`zoneTravel`) و CHK-07 دارند — کارِ ممیزیِ بعدی.
- نیمه‌کاره: شاهدِ CI ِ این ردیف‌ها پس از مرج؛ آینهٔ `needs_review`/`refunded` ِ حساب‌پیِ خودکار روی ردیفِ فاکتور (PAY-07).
- قدمِ بعدی: CI ِ PR، سپس دورِ تازهٔ شاهدِ CI برای همین ردیف‌ها.

### ۱۴۰۵/۰۸/۱۱، شب — حسابِ دوم: #850 و دستهٔ ۱۳ (سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: #850 (شاهدِ CI، دورِ ششم) پس از ادغامِ main مرج شد (`ceaefb22`)؛ دو سطرِ کهنهٔ یادداشتِ «دستگاه‌ها» که
  `batch-status.py` تکراری نگه داشته بود با دست برداشته شد. دستهٔ ۱۳ (#851، #848، #846) دو بار main را گرفت (دستهٔ ۱۲ و #850).
  دو سرخِ واقعی رفع شد: `ScenarioS46Test` حالا Ctrl+L را در پوستهٔ نسخهٔ ۳ (`AppLockKey`، هر سه شاخه) می‌سنجد، و سرآیندِ
  `leadDays` ِ PUR-10 به `license/owner-export.js` آمد (`owner-export.test.js`). ۱۲۰ worktree ِ پاک و push‌شده از `/home/user/wt` پاک شد.
- محیط: کشِ gradle دو jar ِ `runtime-desktop`/`runtime-saveable-desktop` ِ 1.7.3 را که `v3f/offline.init.gradle` می‌خواهد نداشت ⇒ کامپایلرِ
  K2 روی `OrderDelayUi.kt` با «property+invoke» می‌ترکید (main هم). از Maven Central با همان SHA-1 ِ نامِ پوشه برگردانده شد؛ چند jar ِ
  دیگر (`desktop-jvm`، skiko، …) هم یک بار بی `--offline` گرفته شد (Maven Central گاه ۴۲۹ می‌دهد؛ مخزنِ تازه در `TOHID_OFFLINE_M2` نزن).
  آزمونِ محلی: هسته ۱۱۵۱/۱۱۵۱، کامپیوتر ۲۷۴۷ (فقط ۱۳ ِ `ui.theme`)، سرور (PGlite) ۶۵ پرونده که منبعِ برنامه یا `license` را می‌خوانند: ۶۳۳/۶۳۵، ۰ شکست (یک skip و یک TODO ِ شناخته).
- دستهٔ ۱۳ مرج شد (#855 ⇐ `0a68b851`؛ #851 #848 #846 بسته شدند). دستهٔ ۱۴ روی سرِ آن: #852 (آیفون ۵)، #854 (دریِ ویترین)،
  #853 (امنیتِ حساب‌های پرداخت، مهاجرتِ ۲۰۵)، #857 (کمبودها دورِ ۶: RSK-08، DLV-10، OPS-07، RPT-17، DeskInvoices). رفعِ خودِ دسته: «اسکنِ secret» ِ #852 سرخ بود چون `pemOf` در `apns.js` سرخطِ
  PEM می‌سازد ⇒ استثنای دقیق (پرونده + قاعده + fp) در `secret-scan-allow.json`، نه تغییرِ کد برای گول زدنِ اسکنر.
- قدمِ بعدی: مرجِ PR ِ دستهٔ ۱۴ با CI ِ سبز و بستنِ #852 #853 #854 #857؛ سپس صفِ README §۷ (پاک‌سازیِ `ui/screens` ِ بی‌مرجع، DeskInvoices، …).

### ۱۴۰۵/۰۸/۱۱ — شاخهٔ `claude/web-dari` («دریِ ویترینِ وب»)
- انجام شد: «دری» (`prs`) زبانِ چهارمِ وبِ خریدار (ویترین و بازارچه): گزینش‌گرِ فارسی · دری · English · پښتو، ماندگار در
  همان مرورگر، `lang="prs"`/`dir="rtl"`. دری = واژه‌نامهٔ فارسی + لایهٔ `server/public/kit/dari.js` که فقط با دری و از `kit.js`
  بار می‌شود (بیرون از بودجهٔ ۷۵KB) و فقط روی قالبِ `K.t` پیش از پر شدنِ `{…}` (نامِ کالا/دکان دست‌نخورده). فهرست همان
  `DariText.kt` است (`server/scripts/dari-words.js --write`، آزمونِ `dari-text.test.js`)؛ `KEYS` ِ dari.js دو کلید (`zone.small`،
  `dm.send`) را فارسی نگه می‌دارد چون «هزینه» آن‌جا هزینهٔ مشتری است نه «مصارف». برای جا: بارگذارِ مشترکِ `K.js` جای پنج
  بارگذارِ اسکریپتِ store.js؛ و رفعِ `K.setLang` (با #مسیر بارِ دوباره نمی‌شد، برای همهٔ زبان‌ها). STATUS: LOC-06 (PARTIAL).
- نیمه‌کاره: پیامِ وضعیتِ سفارش (پیامک/اعلان) برای مشتریِ دری فارسی است (`customer-notify` فقط fa/ps/en)؛ پیامِ خطای خامِ سرور
  برای دری به «خطا؛ دوباره بزنید» می‌رود. شاهدِ CI پس از ادغام.
- قدمِ بعدی: بازبینیِ واژه‌ها به دستِ صاحب‌کار (جدولِ PR)؛ در صورتِ خواست، `prs` در `customer-notify`.
### ۱۴۰۵/۰۸/۱۱ — شاخهٔ `claude/pay-account-security` (امنیتِ خیلی بالای حساب‌پی و حساب‌های پرداخت؛ حسابِ دوم)
- انجام شد: نیمه‌کارِ `dd4026d47` تمام شد. نگهبانِ حسابِ گیرنده (`server/src/lib/hesabpay-guard.js`، مهاجرتِ ۲۰۵): نخستین حساب
  فوری و هر حسابِ بعدی ۲۴ ساعت «در انتظار» با پوش و «این من نبودم — لغو کن» (`POST /shop/hesabpay/pending/cancel`، قفلِ ۲۴ ساعته)؛
  هر تغییر (حساب، برداشتن، رمز، بازنشانی) فقط از دستگاهِ مطمئن + رمزِ حساب‌پی + تأییدِ دوبارهٔ ورود؛ QR فقط متن و فقط با همان
  شمارهٔ حساب؛ نام و شناسهٔ پذیرنده اجباری؛ «پرداخت شد» ِ خودکار فقط با پذیرندهٔ همین دکان + مرجع + اثرِ انگشتِ حسابِ فعال
  (`payment-gateway.payeeHeld`، وب‌هوک و تطبیق) وگرنه «نیازمندِ بررسی» و هشدار؛ گزارشِ «پول به کدام حساب رفت» (`GET /payees` و
  گزارشِ خودکارِ صاحب). برنامه: «پول به حسابِ <نام> — <۴ رقم>» کنارِ QR ِ صندوق، `payee` روی ردیفِ «پول رسید»، کارت و نوارِ
  پوستهٔ «این من نبودم» (`ui/v3/shell/HesabPayChangeNotice.kt`). آزمون: `hesabpay-guard.test.js` ۹ (۸ روی main سرخ)،
  `hesabpay-account.test.js` ۱۲، HesabPayShopTest/HesabPaySettleTest.
- دورِ دوم (همان PR #853): «دستگاهِ مطمئن» حالا زنجیرهٔ توکنِ سرور است (`tokens.session_at`، مهاجرتِ ۲۰۵)، نه `device_uid` —
  رمزِ دزدیده + شناسهٔ دستگاهِ دانسته رد می‌شود؛ X/Z ِ صندوق به تفکیکِ حسابِ گیرنده (`DrawerModel.hesabPayLines`)؛ هشدار و پوشِ
  فوری برای «پول رسید» ِ صندوق با حسابِ بیگانه (`hesabpay-guard.tillSweep`، هر ۳۰ ثانیه، v1 و v3).
- نیمه‌کاره: نامِ فیلدِ شناسهٔ پذیرنده در وب‌هوک/درِ وضعیتِ حساب‌پی تأییدنشده (`TODO(hesabpay-sandbox)`)؛ پس از استقرار،
  نشست‌های پیشین ۷ روز نامطمئن‌اند (بسته در برابرِ خطا).
- قدمِ بعدی: PR در دستهٔ بعدی (عاملِ ادغام)؛ با sandbox ِ حساب‌پی نامِ فیلدِ پذیرنده را درست کنید.
### ۱۴۰۵/۰۸/۰۸ — شاخه‌های `claude/any-server` و `claude/any-server-app` («برنامه‌ها به هر سرور وصل شوند»)
- انجام شد (PRِ اول): `core/server` (پروفایلِ سرور، کیو‌آرِ `tohid-server:`، قاعدهٔ نشانی، سنجاقِ هویت، جابه‌جاییِ نشانی، نامِ
  بایگانیِ جدا برای هر سرور؛ `ServerProfilesTest` ۱۶)، `GET /api/v1/config/server` با `serverId` ِ پایدار (`server-info.test.js` ۶)،
  HTTPS ِ مستقیمِ اختیاری (`TLS_*`)، `PUBLIC_URL`/`SERVER_NAME`، `docs/SERVERS-fa.md` و بخشِ «🌐 هر سرور» در `RULES-fa.md`.
  PRِ دوم (روی اولی): بخشِ «سرورها» در تنظیماتِ گوشی و کامپیوتر پشتِ پرچمِ خاموشِ `app.multi_server` — افزودن با نشانی
  یا کیو‌آر، تأییدِ اثرِ انگشت، نشانیِ دومِ همان سرور با جابه‌جاییِ خودکار، دفتر/عکس/نشستِ جدا برای هر سرور، و عوض شدن
  فقط در شروعِ برنامه (`platform/ServerSwitchAndroid.kt`).
- تصمیمِ فنی: `http` ِ شبکهٔ دکان پذیرفته **نشد** (همان شنودِ توکن)؛ به‌جایش https با گواهیِ خودامضای سنجاق‌شده.
- نیمه‌کاره: نسخهٔ وب هنوز فقط نشانیِ زمانِ ساخت را دارد؛ پنلِ مدیریتِ سرورها؛ تصمیمِ مجوزِ اشتراک روی سرورِ دیگر (`SERVERS-fa.md` §۲.۱).
- قدمِ بعدی: سبز شدنِ CIِ اندروید/کامپیوترِ PRِ دوم (این‌جا کامپایل نشد)، سپس پنلِ سرورها که همان کیو‌آر را بسازد.

### ۱۴۰۵/۰۸/۰۹ (۳۱ اکتبر ۲۰۲۶، عصر) — حسابِ دوم: ممیزی، دستهٔ ۱ و ۲ ِ ادغام، موجِ ۲ (سیزنِ `session_01GcPTALbaroEADJph5to8Qj`)
**از این‌جا شروع کن:**
1. همین بند را بخوان.
2. کامنتِ آخرِ issue #606 را بخوان؛ وضعِ زنده آن‌جاست.
3. هر PR را از آخرین کامیت و بدنهٔ خودش ادامه بده.

**ممیزیِ کارِ حسابِ قبلی (انجام شد):**
- هیچ بازنویسیِ تاریخچه‌ای روی main رخ نداده است.
- این‌ها دست نخورده‌اند: امضا و کلید، applicationId و versionCode، AppId و upgradeUuid ِ نصاب، انتشارِ پایدار.
- آزمون‌های سرور روی main محلی ۱۷۵۱ آزمون، ۰ شکست.
- **قانون‌شکنی‌های یافته‌شده که درست شد:** این PRها به کدِ مشترکِ پمپ دست زده بودند: #364 (`lib/sync-v1.js`)، #590، #515، #588 و #582 (`admin-pump.js`).
  - همه به main برگشتند.
  - حفره‌های امنیتیِ سمتِ پمپ در پیش‌نویسِ **#615** جمع شد و فقط با اجازهٔ صاحب‌کار ادغام می‌شود.
- **یافتهٔ خطرناکِ قدیمی:** دکمهٔ «پاک کردنِ همه» می‌گفت «این دستگاه» ولی از همهٔ دستگاه‌ها و سرور پاک می‌کرد.
  - #614 متن را راست کرد و دکمه را فقط برای صاحب و دو مرحله‌ای کرد.
  - گزینهٔ «فقط همین دستگاه» منتظرِ تصمیمِ صاحب‌کار است.

**ادغام‌شده در این سیزن:**
- دستهٔ ۱: #493، #498، #499، #508، #524، #584، #587، #589. CI ِ main روی 57913cbd سبز شد؛ تنها استثنا مکِ arm64 است، به‌خاطرِ brownout، که پایین‌تر آمده.
- دستهٔ ۲: #563، #564.
- ⚠️ ادغامِ پشتِ‌همِ این دو، خلاصهٔ `docs/spec/STATUS.md` را ناهم‌خوان کرد. درمانش #618 است.
  - **قاعده:** پس از هر ادغامی که STATUS را عوض می‌کند، پیش از ادغامِ بعدی `node docs/spec/tools/spec.mjs check` را روی main ِ تازه اجرا کن.

**چهار PR ِ سلامتِ CI؛ پیش از همه ادغام شوند:**
- **#617** آزمونِ بازار:
  - مشکل: سنجهٔ نشتِ مختصات «3453» را در کلِ JSON می‌جست. `Date.now` (۱۷۹۳۴۵۳…، ۱۳:۲۳ تا ۱۳:۴۰ UTC ِ ۳۱ اکتبر) و نشانِ `f-<base36><seq>` هم آن را داشتند.
  - پیامد: #607، #578، #597، #598 و #613 سرخ شدند.
  - درمان: `h.withoutEpochs`. شکست با ساعتِ جابه‌جا بازسازی شد؛ پس از درمان ۱۵ از ۱۵؛ ۴ جهش گرفته شد.
- **#607**: آزمونِ ناپایدارِ `staff-approval:488` (ts ِ مشترک) و برداشتنِ wrap.
- **#608**: پاکیزه‌کاریِ CI، و **macos-14 ⇐ macos-15**. GitHub آن را **۲ نوامبر ۲۰۲۶ بازنشسته می‌کند** و تا آن روز brownout دارد؛ کارِ «نصاب مک (Apple Silicon)» بی runner لغو می‌شود.
- **#618**: STATUS.

پس از ادغامِ #617 و #607، کارهای سرخِ #598، #335، #613، #578 و #597 را یک بار دوباره اجرا کن. سرخی‌شان از این دو آزمونِ ناپایدار بود.

**ترتیبِ ادغام‌های بعدی** (هر کدام فقط با CI ِ سبز، و پس از هر کدام spec check):
- #534 و #515 **پشتِ هم و فقط هر دو با هم**. #534 تنها، با `dropRevoked` سوکتِ کاربرِ پمپ را می‌بندد؛ #515 مرزِ `s78` را می‌گذارد.
- #529 ⇐ #560 ⇐ #562
- #597 ⇐ #598 ⇐ #605:
  - پس از ادغام #600 تا #603 را ببند.
  - اگر #582 زودتر ادغام شد، #605 باید ۵ درِ مدیر را دسته‌بندی کند: `shops/:id/health`، `shops/:id/holds`، `admins/:id/sessions`، `ops/incidents/:id`، `finance/payments/:id`.
- #576 ⇐ #577
- #335 ⇐ #579
- #578 ⇐ #585 (پس از ادغام، انتشارِ beta را بسنج)
- #546، #612، **#610**، **#609**، #611، #613، #614:
  - **#610**: ماندهٔ صاف به پول، پشتِ پرچمِ خاموشِ `shop.debt_minor`. نیم‌پول با قاعدهٔ DebtBook درست شد و بازبینیِ مستقلِ پول انجام شد.
  - **#609**: حساب‌پی. paid ِ تأییدنشده ⇐ ۵۰۳ و deferred؛ reconciler با قفلِ اجاره؛ needs_review برای paid ِ بی‌تأیید و متناقض؛ سقفِ ۳۰ ثانیه برای مهلت. دو دور بازبینیِ مستقل شد: «سالم».
  - #611: یک‌شکل کردنِ شماره برای همهٔ سقف‌ها. #613: دو برگهٔ وب با Web Locks.
- #582: گروهِ امنیت در دورِ ۴ admin-pump.js را به #615 می‌برد و main را در آن می‌آورد.
- #565، #561، #533، #596، #588، #590، #593، #595
  - #533: اگر #467 زودتر ادغام شد، `existingOrder` روی `POST …/delay`.
  - #595: کارِ «ساختِ سایت» سرخ است و هنوز بررسی نشده.
- #407. هر کدام از #407 و #614 دیرتر ادغام شد، `replace=true` و «پاک شد پس از نوشتنِ موفق» را در `onConfirm` ِ `WipeAllDialog` بنشاند.
- #364 (گاوصندوق). بی دست زدن به `sync-v1.js`: ظرفِ `AsyncLocalStorage` در `lib/safe-flag.js` فقط برای دکان.
- #315 ⇐ #323
- #359 (پایهٔ v3، a5665a9d):
  - ایمنیِ مانیفست انجام شد: `enableOnBackInvokedCallback` فقط در preview، `vill3n://` در alias ِ خاموش، `DateField` پشتِ پرچم.
  - bench/kotlin به commonMain رفت؛ کارِ PERF روی main هم سرخ است و با همین درست می‌شود.
  - پس از آن #390، #391 (creditGate)، #400، #558 و #616 (تنظیماتِ v3).
  - در #616 عکس‌ها مانده‌اند، و پس از آمدنِ پایه `LinePolicyPrefs`/`PosSearchPrefs` صفحهٔ v3 می‌خواهند.

**موجِ ۲ (در جریان؛ ۶ گروه روی ۹۳ PR ِ قدیمی).** دستورِ مشترک در پیامِ گروه‌ها: هر PR به «آمادهٔ ادغام» برسد یا با دلیل پیشنهادِ بستن بگیرد. در هر دور حداکثر ۸ push. نتیجهٔ هر PR در آخرین کامیت و بدنهٔ خودش است.
- **الف۱ (سرور، اشتراک، پشتیبان):** #586، #581، #442، #446، #592، #440، #591، #453، #460، #507، #510، #520، #541، #542
- **الف۲ (صندوق، کالا، گزارش):** #336 (نقش‌ها، چهار موردِ «اول درست کن»)، #434، #436، #457، #459، #466، #469، #473، #516، #527، #551، #553، #556
- **الف۳ (پیک، سفارش، چت):** #491، #519، #525، #526، #538، #543، #419، #517، #357، #490، #502، #537، #545، #554
- **ب۱ (بازار، ویترین):** #417، #418، #420، #421، #422، #425، #427، #428، #430، #432، #439، #454، #461، #494، #549، #572
- **ب۲ (همگام‌سازی، پشتیبان، پول):** #481 (معنای cursor؛ PERF-05 ⇐ تصمیمِ صاحب‌کار)، #501، #528، #485، #455، #496، #465، #429، #438، #464، #448، #547، #574، #441
- **ب۳ (تصاویر، خرید، کالا، چت، عملیات):** #443، #458، #463، #474، #480، #471، #478، #482، #521، #522، #489، #504، #523، #511، #548، #559، #568
- **فقط با صاحب‌کار:** #594 (امضای اندروید فقط از secret)، #477 (کلیدِ امضای مجوز)، #615 (حفره‌های پمپ)، #273 (مهاجرتِ ۰۹۷).

**CI:**
- حدودِ ۸ کار هم‌زمان اجرا می‌شود و بیش از ۱۵۰ اجرا در صف است.
- اجرای در صفی که کامیتش کهنه شده لغو شود؛ اجرای کهنهٔ main لغو نشود، چون ورکفلوهای مسیری فقط روی همان کامیت اجرا شده‌اند.
- هر PR در هر دور فقط یک push.

**منتظرِ صاحب‌کار (افزون بر فهرستِ بندِ پیشین):**
1. اجازه برای #615.
2. «پاک کردنِ همه» فقط برای همین دستگاه؟
3. اجازهٔ صریح برای #506 و #567 ِ بی‌پرچم.
4. اجازهٔ push در ریپوی `server`: پیشوندِ `/payments` در `ACCOUNT_PREFIXES` ِ پنل، و main ِ تازه در #107 تا #110.
5. اجازهٔ انتشارِ پایدار.
6. پیش از روشن کردنِ `pay.hesabpay`: سقفِ پرسشِ حساب‌پی را بسنج؛ reconciler تا ۵۰۰ پرسش در هر نوبت می‌کند.

**شمارهٔ مهاجرت:** ۱۷۴ (#628)، ۱۷۵ (#640)، ۱۷۶ (#644)، ۱۷۷–۱۷۹ (#648، #655، #660) گرفته شد ⇒ **آزادِ بعدی ۱۸۰** (پیش از گرفتن بسنج).

**به‌روزرسانیِ ۳۱ اکتبر، ۱۸:۴۵ UTC:**
- ادغام‌های بعد از این بند: #529، #614، #585 (+ بستنِ #578)، #611، #596، #442، #618، #543، #533، #612. #577 درونِ #576 و #579 درونِ #335 squash شد. #357 بسته شد (کارِ یکتایش در #667).
- **⚠️ CI:** ساعتِ ۱۶:۳۶ و ۱۶:۵۲ صدها اجرا «canceled by @vil3ntec-it» شد (کارِ هیچ ایجنتِ این سیزن نبود). ~۶۰۰ اجرا در صف و ۶ هم‌زمان ⇒ **یخ‌بندانِ ادغام** تا یک دورِ کاملِ «سرور» روی HEAD ِ main سبز شود؛ پیشنهاد به صاحب‌کار: self-hosted runner روی سرورِ خانه. گروه‌ها حق ندارند اجرا را لغو/rerun کنند.
- **اول ادغام شوند (سلامتِ CI):** #617، #607، #608، #662 (--test-force-exit)، #665 (سابقهٔ پرداخت در تراکنش)، #668 (wrap)، #670 (ردیف‌های کهنهٔ STATUS)، #627 (ترتیبِ رویدادهای market-trust)، #649 (شکستنِ lib/delivery.js؛ پیش از #519، #526، #620).
- **بازبینیِ پول:** سالم ⇐ #609، #610، #586، #592، #485 (پس از درمان)، #448، زنجیرهٔ #457⇐#471⇐#478⇐#482، #466، #574، #549. **درمان در جریان (پیش از ادغام):** #517 (بسته‌بندیِ پرداخت‌شده بی بازپرداخت)، #434 (DrawerRule بی پرچم)، #522 (بارکدِ مبهم).
- **تیمِ قرمز (دو دور):** بحرانی/بالا نیست. اپِ قدیمیِ WebView ⇐ #637. امضای به‌روزرسانیِ کامپیوتر ⇐ صاحب‌کار `TOHID_UPDATE_*` را بگذارد.
- **PRهای تازهٔ موجِ ۳/۴ (همه پشتِ پرچمِ خاموش، منتظرِ CI):** #620–#670 (فهرست در کامنتِ آخرِ #606). زنجیره‌ها: #490⇐#517⇐(#648، #655، #660)؛ #649⇐(#519، #526، #620)؛ #359⇐#667.
- **پیش‌نویس، فقط صاحب‌کار:** #615، #594، #477، #593.
- **پرسش‌های باز از صاحب‌کار:** لغوِ اجراها؛ برگرداندنِ بخشِ وبِ #533 (قابلیتِ تازه در `license/`)؛ رزرو/کسرِ موجودی برای سفارشِ آنلاین (سند «بله»، تصمیمِ ۰۷/۲۷ «نه»؛ #429 پشتِ پرچم)؛ آستانهٔ «گران‌قیمت» (#620)؛ مهلتِ جایگزین (#648)؛ پیش‌پرداختِ «تحویلِ بعدی» (#663)؛ کدِ کارتِ مشتری (CRM-16)؛ ایمیل برای گزارشِ روزانه (RPT-12).

### ۱۴۰۵/۰۸/۱۰ — تحویل به حسابِ دوم (سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`، سقفِ مصرف)
**صفحهٔ پیشرفتِ زنده (از این‌جا شروع کن):** issue #606 — پس از هر دستهٔ ادغام به‌روز می‌شود؛ گیرها و کارهای صاحب محصول هم آن‌جاست.

**قاعده‌ها (همچنان):** همهٔ کار با زیرعامل‌ها در یک سیزن (هرگز create_session)؛ هر PR پس از CIِ سبز squash؛ پرچمِ خاموش برای رفتارِ پرخطر؛
صاحب محصول گفته «همه را ادغام و منتشر کن» (شاملِ مهاجرت‌ها و رفع‌های بی‌پرچمِ نشتِ داده/پول). ریپوی `server` با دستورِ ۱۴۰۵/۰۸/۰۹ مجاز
است؛ `pump-staion-yaqobi` نه. **برنامهٔ ادمینِ اندروید کنار گذاشته شده؛ راهِ موبایلِ مالک رباتِ تلگرام است.**

**صفِ CI گرهِ اصلی است** (~۶۰۰ اجرا، ۱۱ هم‌زمان، ~۴۰ در ساعت). قاعدهٔ سرپرست: PRی که آخرین CIاش سبز بوده و پس از آن فقط ادغامِ main با تعارضِ
سند/`baseline.json` گرفته، با ابزارِ پیش‌مرج (wrap()، شمارهٔ مهاجرت، ratchet، دسته‌بندیِ مسیرها در #582/#588، فایل‌های تغییرکرده در main، spec check)
بی دورِ تازهٔ CI ادغام می‌شود. سقفِ اندازهٔ فایل برای storefront/delivery/catalog/market/otp/admin.js موقتاً بالا رفت ⇐ کارِ پیگیریِ شکستنِ فایل.

**ترتیبِ ادغام‌های باز:**
- shop: #597 ⇐ #598 ⇐ #605 (یکپارچهٔ کنسول، جای #600/#601/#602/#603 ⇐ پس از ادغام آن‌ها را ببند؛ دسته‌بندیِ مسیرهای تازهٔ مدیر در #582 مانده) ·
  #534 ⇐ #515 · #335 ⇐ #579 · #541 ⇐ #547 ⇐ #571 · #518 (ادغام شد) ⇐ #529 (به main برگشت) ⇐ #560 ⇐ #562 · #576 ⇐ #577 · #578 ⇐ #585
  (پس از ادغام، انتشارِ beta را بسنج) · #417 ⇐ #425 ⇐ #549 · #490 ⇐ #517 ⇐ #540 ⇐ #550 · #473 ⇐ #521 ⇐ #522 · #391 (پیگیریِ priorMinor
  و نشتِ شمارشِ کور push شد، head `ecc2a3c`، منتظرِ CI؛ پس از ادغامِ #536، `DrawerModel.forViewer` ⇐ `ShiftSlip.forViewer`).
  **سرخ و بی‌صاحب (ایجنت‌هایشان بسته شدند؛ حسابِ دوم درستشان کند):** #407، #508، #510، #520، #521، #522، #560، #562، #577، #585.
  **push‌شده و منتظرِ CI ِ تازه:** #529، #452، #454، #442، #446، #460، #419، #503، #516، #524، #527، #551، #556، #563، #564، #567، #584،
  #490، #504، #507، #519، #323، #599؛ در #503/#504/#499/#516/#524/#527/#551/#556/#563/#529 تعارضِ کد را مدیر حل کرد ⇐ فقط با CI ِ سبز.
  #536: تعارضِ `ShiftSlip.kt`. #533: اگر #467 زودتر ادغام شد، `existingOrder` باید درِ `POST …/delay` را هم بپاید.
  **پیش‌نویس‌ها، فقط با «مرج کن» و secretهای صاحب محصول:** #594 (امضای اندروید فقط از CI)، server#112.
- server: #105 ادغام شد؛ #107 ⇐ #108 ⇐ #109 ⇐ #111 ⇐ #113 ⇐ #114 ⇐ #115 ⇐ #116 ⇐ #121 (یکپارچه، نسخهٔ 1.50.69، جای #117–#120 ⇐ پس از ادغام آن‌ها را ببند؛ هم‌زمان با shop #605 ادغام شود).
  **گیر:** دستهٔ اجازه push ِ ادغامِ main در شاخه‌های ریپوی server را رد کرد؛ #107–#110 فقط به‌خاطرِ main ِ کهنه (پیشوندِ receipt) سرخ‌اند ⇐ اجازهٔ صاحب
  محصول یا ادغامِ main به‌دستِ خودِ او.
- پس از #552 در #391: `permDecision(priorMinor)`. پس از #567 در #546: `TillReceivePref.record` ⇐ `DebtEntry.record`. پیش از روشن کردنِ
  `shop.ledger_merge` (#407): کارِ `save(base, next)`. #503/#551/#556/#504/#499 تعارضِ کدی را مدیر حل کرد و منتظرِ CIاند.

**شمارهٔ مهاجرت‌ها:** ۱۳۸ chk-1 · ۱۳۹ #518 · ۱۴۰ #507 · ۱۴۱ #523 · ۱۴۲ #517 · ۱۴۳ #511 · ۱۴۴ #525 · ۱۴۶ #545 · ۱۴۸ #537 · ۱۵۱ #541 ·
۱۵۳ #572 · ۱۵۷ #561 · ۱۶۵/۱۶۸/۱۶۹ #597 · ۱۶۶ #548 · ۱۶۷ #592 · ۱۷۱ مالی · ۱۷۲ بازار · ۱۷۳ عملیات · **آزادِ بعدی ۱۷۴**.

**برنامه‌های بزرگِ این سیزن (سندها):** امنیت: `docs/SECURITY-PROGRAM-fa.md` (#570) و `docs/security/*-fa.md` · پنلِ مرکزی در بخشِ «فروشگاه»ِ
برنامهٔ سرور: ریپوی server `docs/OWNER-CONSOLE-PROGRAM-fa.md` (#106) و `docs/OWNER-CONSOLE-AUDIT-fa.md` (#110)؛ فاز ۱ تا ۳، پوسته و ماژول‌های
مالی/بازار و پیک/نسخه و سلامت و خطا و هشدار ساخته شده‌اند · حساب‌پی: `docs/PAYMENTS-HESABPAY-fa.md` · هر سرور: `docs/SERVERS-fa.md` و server
`docs/DEPLOY-ANYWHERE-fa.md`.

**منتظرِ صاحب محصول:** (۱) **کلیدِ امضای اندروید لو رفته** (ریپوها عمومی‌اند) ⇐ کلیدِ تازه + secretهای `TOHID_KEYSTORE_*` + چرخشِ v3؛
(۲) عمومی/خصوصی بودنِ ریپوها (پیش از خصوصی شدن، انتشارها و سایت به ریپوی جدا)؛ (۳) لغوِ اجراهای کهنهٔ CI؛ (۴) اجازهٔ به‌روز کردنِ شاخه‌های
server؛ (۵) Pages ⇐ GitHub Actions، اسکنِ secret، Dependabot، مجوزِ Actions = Read؛ (۶) روی سرورِ خانه: `NODE_ENV=production`،
`API_SECRET`، `OTP_SECRET`، `TRUST_PROXY_FROM`، `BACKUP_PASSPHRASE`، TOTP برای خودش؛ (۷) کلید و حسابِ حساب‌پی؛ (۸) پرسش‌های محصولیِ جمع‌شده
(پیشنهادِ پیش‌فرض کنارِ هرکدام در گفت‌وگو داده شد): پایانِ روزِ کاری برای کلِ دکان، روشِ بها (میانگینِ موزون)، مهلتِ لینکِ پیگیری ۳۰ روز، …
### ۱۴۰۵/۰۸/۰۹ — برنامهٔ امنیت، شاخهٔ `claude/sec-client` (اشتراک در سرور، اندروید، کامپیوتر)
- انجام شد: SEC-CLI-01 (High) — `POST /api/license/sync` شناسهٔ دستگاهِ ساختگی را فقط می‌شمرد، نمی‌نشاند ⇒ سقفِ دستگاه دور
  می‌خورد و دستگاهِ باطل‌شده دوباره مجوز می‌گرفت؛ حالا جا زیرِ قفلِ دکان ثبت می‌شود، باطل ⇒ `device_revoked`، سقفِ دستگاه‌های
  واردشده پشتِ پرچمِ خاموشِ `shop.device_cap`. آزمون‌های `sec-sub-device-cap`، `sec-sub-trial`، `sec-sub-replay` (روی
  PostgreSQL هم). سند: `docs/security/client-fa.md`. PRِ جدا #580: `DevShot` فقط در ساختِ dev، مبهم‌سازیِ R8 پشتِ
  `-Ptohid.obfuscate=true`.
- نیمه‌کاره / کارِ صاحب محصول: SEC-CLI-02 (Critical) کلیدِ امضای انتشار و رمزش در مخزنِ عمومی ⇒ چرخش؛ SEC-CLI-03 کلیدِ امضای
  به‌روزرسانی گذاشته نشده؛ SEC-CLI-04 DPAPI برای نشستِ کامپیوتر؛ روشن کردنِ `shop.device_cap`.
- قدمِ بعدی: پس از مرج، ردیفِ SEC-SUB در `SECURITY-PROGRAM-fa.md` (کارِ هماهنگ‌کننده)؛ مهاجرتِ ۱۶۲ مصرف نشد.


### ۱۴۰۵/۰۸/۰۹ — شاخهٔ `claude/portable-account-server` (جابه‌جاییِ سرورِ حساب؛ همراهِ `claude/portable-server` در ریپوی server)
- انجام شد: `server/scripts/move-db.js` (inspect · copy · compare) بینِ PGlite و PostgreSQL در هر دو جهت؛ مبدأ هرگز نوشته
  نمی‌شود، مقصدِ تازه با Migrationهای همین کد، یک تراکنش با ماشه‌های خاموش، و تطبیقِ شمار/هشِ ردیف/جمعِ مبلغ/sequence.
  سند: `DISASTER-RECOVERY-fa.md` §۳.۱. آزمون: `test/move-db.test.js` ۸/۸ (PGlite؛ و PGlite ⇐ PostgreSQL ⇐ PGlite روی PostgreSQL 16ِ محلی).
- نیمه‌کاره: پیوند از `docs/SERVERS-fa.md` (هنوز در PRِ باز #576) به همین ابزار، پس از مرجِ آن.
- قدمِ بعدی: پس از مرج، پنلِ خانگی `move-server.mjs` را با همین ابزار برای تطبیقِ سرورِ حساب صدا می‌زند (کدش آماده است).
### ۱۴۰۵/۰۸/۰۹ — شاخهٔ `claude/console-phase3` (پنلِ مرکزی، فاز ۳ — سمتِ سرورِ حساب)
- انجام شد: `GET /api/admin/shops/:id/health` (فقط دادهٔ واقعی؛ نبود ⇒ null)؛ چهار حالتِ جدای دکان + بایگانی
  (`lib/shop-holds.js`، مهاجرتِ ۱۶۵) — تعلیقِ دسترسی، غیرفعال کردنِ سرویس، پایانِ اشتراک (فقط مالی/ارشد، با Idempotency-Key)،
  درخواستِ حذف (حذفِ واقعی همان راهِ تأییدشدهٔ پیشین) و بایگانی؛ هیچ‌کدام چیزی پاک نمی‌کند، دلیل اجباری، سابقه در همان تراکنش.
  نقش‌های `finance` و `viewer` (پیش‌فرض‌بسته، `lib/admin-roles.js`)؛ کلیدِ سرویس + «انجام‌دهنده» برای پلِ پنل (راهِ ارشدِ پیشین
  هنوز کار می‌کند)؛ تاریخچهٔ `live_config` و `GET/PUT/DELETE /api/admin/flags`؛ مالکِ اصلی (`is_owner`) را ارشدِ دیگر نمی‌بندد
  (مهاجرت‌های ۱۶۸–۱۶۹). شرح: بندِ ۱۲ ِ `docs/spec/ADMIN-CONTROL-PLANE.md`. آزمون: `test/console-phase3.test.js`.
- نیمه‌کاره: سمتِ پنل (ریپوی `server`) هنوز با رمزِ ارشد می‌آید — جابه‌جایی به کلیدِ سرویس کارِ PRِ بعدیِ همان ریپو است
  (راهش در بندِ ۱۲.۲). رده‌بندیِ دو درِ تازه در `tenant-matrix.test.js` (#582) و «تازه» شمردنِ کلیدِ سرویس در `assertFresh` (#588)
  با هر کدام که دوم مرج شود (بندِ ۱۲.۲ و ۱۲.۵).
- قدمِ بعدی: PRِ پل در ریپوی `server`؛ رابطِ «حالت‌ها»، «سلامت» و «پرچم‌ها» در `ShopDesk`.


### ۱۴۰۵/۰۸/۱۰ — شاخهٔ `claude/console-integrated-api` (کنسولِ یکپارچهٔ «فروشگاه» — سمتِ سرورِ حساب؛ روی `claude/console-glue`)
- انجام شد: چهار شاخهٔ هم‌زمان در یک مسیرِ مرج: پوسته (#601، بی مهاجرت) + بازار/پیک (#600، ۱۷۲) + عملیات (#602، ۱۷۳) + مالی (#603، ۱۷۱).
  تعارض‌ها: `CAPABILITIES` ِ `routes/admin.js` (همه، به همان نام‌های پل؛ `ops.v1`/`release-stage.v1` از `lib/ops-overview`)، `MOUNTS` و
  `CAPABILITY_PROBES` ِ `admin-control-plane.test.js`، `lib/admin-roles.js` (درهای مالیِ بازار/پیک + مالیِ مرکزی)، `app.js` (`/admin/finance`
  و نبضِ خانه‌تکانی)، بندهای ۱۲.۷ پوسته · ۱۲.۸ بازار/پیک · ۱۲.۹ مالی ِ `ADMIN-CONTROL-PLANE.md` (هر سه ۱۲.۷ بودند)، «سهم‌بندی» ِ `RULES`
  (۱۷۰ بی‌مصرف، ۱۷۱ مالی، ۱۷۲ بازار/پیک، ۱۷۳ عملیات؛ آزادِ بعدی ۱۷۴). `routes/admin.js` از ۹۰۱ خط به ۷۸۶: بخشِ «مدیران» (ساختن،
  نقش/وضعیت، نشست‌ها) بی تغییرِ رفتار به `routes/admin-admins.js` (سوار روی `/admins`؛ `MOUNTS` و جدولِ بندِ ۶). `STATUS.md` با
  `spec.mjs status` بی تغییر ماند. جانشینِ #600، #601، #602، #603؛ سمتِ پنل: ریپوی `server`، شاخهٔ `claude/console-integrated` (server#121).
  آزمون: کلِ سرور روی PGlite ۱۷۳۵/۱۷۳۶ (۱ رد) و PG ۱۷۵۶/۱۷۶۲ (۶ رد) + auto-backup ۹/۹، بی شکست؛ quality (ratchet، eslint، migrations) و `spec.mjs check` سبز.
- نیمه‌کاره: #582 (`ADMIN_SUBJECT`) هنوز روی main نیست ⇒ رده‌بندیِ درهای تازه (`/admin/search`، `/admin/admins/:id/sessions`،
  `/admin/market/*`، `/admin/delivery/*`، `/admin/ops/*`، `/admin/finance/*`) همان روزِ مرجِ #582؛ متنِ پیشنهادی در بندهای ۱۲.۷ و ۱۲.۹.
- قدمِ بعدی: مرجِ #597 ⇐ #598 ⇐ همین (به‌جای چهار PR)، هم‌زمان با PRِ یکپارچهٔ ریپوی `server`؛ بعد بستنِ #600–#603.

### ۱۴۰۵/۰۸/۱۰ — شاخهٔ `claude/console-shell-api` (پوستهٔ کنسولِ «فروشگاه»؛ روی `claude/console-glue`)
- انجام شد: توانایی‌های `admin-sessions.v1` (نشست‌های یک مدیر: فهرست و بستن، مالکِ اصلی فقط خودش) و `search.v1`
  (`GET /api/admin/search`، به نقش)؛ مدیرِ تازه بی نقش ⇒ `viewer`؛ «پیشین ⇒ تازه» و دلیل در سابقهٔ نقش و `PATCH /config`؛ پرچمِ
  کاتالوگ نوع‌دار و در scope ِ خودش. بندِ ۱۲.۷ ِ `ADMIN-CONTROL-PLANE.md`؛ آزمون `test/console-shell.test.js`. بی مهاجرت (۱۷۰ آزاد ماند).
- نیمه‌کاره: هیچ. سمتِ پنل: ریپوی `server`، شاخهٔ `claude/console-shell` (روی #116).
- قدمِ بعدی: مرج پس از #597 و #598؛ اگر #582 دوم مرج شد، دو خطِ بندِ ۱۲.۷ را به `ADMIN_SUBJECT` بیفزایید.

### ۱۴۰۵/۰۸/۱۰ — شاخهٔ `claude/console-market-api` (پنلِ مرکزی ۴.۶ بازار و ۴.۷ پیک — سمتِ سرورِ حساب؛ روی `claude/console-glue`)
- انجام شد: `/api/admin/market/*` (نمای کلی، فروشنده‌ها با دروازه‌ها، سفارش‌های ویترین بی دادهٔ شخصی، کمیسیون «در مدل نیست» تا #432،
  سابقهٔ کارهای حساس) و `/api/admin/delivery/*` (صف‌ها، گیرکرده/ناهم‌خوان/اختلاف از `lib/order-watch.js`، رویدادها، اتصالِ پیک،
  گزارشِ کارکرد با تعریف‌های RPT-15، آستانه‌ها در live_config — `PUT` فقط ارشد + دلیل)؛ توانایی‌های `market-admin.v1` و
  `delivery-watch.v1`؛ finance فقط درهای پولی؛ مهاجرتِ ۱۷۲ فقط نمایه. بندِ ۱۲.۸ ِ `ADMIN-CONTROL-PLANE.md`.
- نیمه‌کاره: کمیسیون و سبدِ چنددکانی تا مرجِ #432 «در مدل نیست»؛ مدرکِ پرونده (عکس) به پل نرسیده (فقط کنسولِ خودِ سرورِ حساب).
- قدمِ بعدی: مرجِ #597 ⇐ #598 ⇐ همین؛ سمتِ پنل: ریپوی `server`، شاخهٔ `claude/console-market` (روی #116).

### ۱۴۰۵/۰۸/۱۰ — شاخهٔ `claude/console-ops-api` (پنلِ مرکزی ۴.۸–۴.۱۱، ۴.۱۳ — سمتِ سرورِ حساب؛ روی `claude/console-glue`)
- انجام شد: درهای `/api/admin/ops/*` (`routes/admin-ops.js`، توانایی `ops.v1`): نسخه‌ها و انتشار (پایدار/آزمایشیِ هر سکو، حداقل نسخه،
  نسخهٔ API و طرحِ پایگاه جدا، خطای پس از انتشار، انتشارِ مرحله‌ای فقط اگر OPS-01 ‎#559‎ هست ⇒ `release-stage.v1`)، سلامت (نبضِ واقعیِ
  کارهای پس‌زمینه `lib/job-beat.js`، آخرین همگام‌سازی، ۵xx، حلقهٔ رویداد)، مرکزِ رخداد (۵xx ِ سرور حالا در `server_errors` با همان
  کدِ پیگیری؛ خطای تکراری = یک رخداد؛ وضعیت/اولویت/یادداشت/پیوندِ پشتیبانی با سابقه)، هشدارها (۹ قاعده روی دادهٔ واقعی، یکی شدنِ
  تکرار، سقفِ سیل، بی چشمک)، یکپارچه‌سازی (اعتبارنامه فقط هست/نیست، وب‌هوکِ حساب‌پی «مسدود» تا #518). مهاجرتِ **۱۷۳**.
- نیمه‌کاره: هیچ. سمتِ پنل: ریپوی `server`، شاخهٔ `claude/console-ops` (روی `claude/console-glue-panel`).
- قدمِ بعدی: مرجِ #597 ⇐ #598 ⇐ همین؛ پس از #559 «انتشارِ مرحله‌ای» خودش روشن می‌شود. هشدارِ مالی (پرداخت/تسویه) برای نقشِ
  finance هنوز باز نیست (درهای `ops` برای finance پیش‌فرض‌بسته‌اند).


### ۱۴۰۵/۰۸/۰۹ — شاخهٔ `claude/console-glue` (چسبِ فاز ۳ ⇄ پنل؛ روی `claude/console-phase3`)
- انجام شد: `GET /api/admin/me` ⇒ `capabilities` (`shop-health.v1`، `shop-holds.v1`، `flags.v1`، `actor.v1`، `service-key.v1` —
  همان نام‌های پل در ریپوی `server`)؛ مسیرِ ناشناختهٔ `/api/admin/*` با توکنِ معتبرِ مدیر ۴۰۴ `route_not_found` (بی‌توکن همان ۴۰۱)؛
  `total` ِ `GET /api/admin/shops` همان `q` و `hold` را می‌شمارد. بندِ ۱۲.۶ ِ `ADMIN-CONTROL-PLANE.md`؛ آزمون در `admin-control-plane.test.js`.
- نیمه‌کاره: هیچ. سمتِ پنل: ریپوی `server`، شاخهٔ `claude/console-glue-panel` (روی #115).
- قدمِ بعدی: مرجِ #597 و همین، بعد #115 و PRِ پل؛ صاحبِ سامانه از «فروشگاه ← یکپارچه‌سازی» کلیدِ سرویس را می‌سازد.

### ۱۴۰۵/۰۸/۰۹ — شاخهٔ `claude/console-phase3` (پنلِ مرکزی، فاز ۳ — سمتِ سرورِ حساب)
- انجام شد: `GET /api/admin/shops/:id/health` (فقط دادهٔ واقعی؛ نبود ⇒ null)؛ چهار حالتِ جدای دکان + بایگانی
  (`lib/shop-holds.js`، مهاجرتِ ۱۶۵) — تعلیقِ دسترسی، غیرفعال کردنِ سرویس، پایانِ اشتراک (فقط مالی/ارشد، با Idempotency-Key)،
  درخواستِ حذف (حذفِ واقعی همان راهِ تأییدشدهٔ پیشین) و بایگانی؛ هیچ‌کدام چیزی پاک نمی‌کند، دلیل اجباری، سابقه در همان تراکنش.
  نقش‌های `finance` و `viewer` (پیش‌فرض‌بسته، `lib/admin-roles.js`)؛ کلیدِ سرویس + «انجام‌دهنده» برای پلِ پنل (راهِ ارشدِ پیشین
  هنوز کار می‌کند)؛ تاریخچهٔ `live_config` و `GET/PUT/DELETE /api/admin/flags`؛ مالکِ اصلی (`is_owner`) را ارشدِ دیگر نمی‌بندد
  (مهاجرت‌های ۱۶۸–۱۶۹). شرح: بندِ ۱۲ ِ `docs/spec/ADMIN-CONTROL-PLANE.md`. آزمون: `test/console-phase3.test.js`.
- نیمه‌کاره: سمتِ پنل (ریپوی `server`) هنوز با رمزِ ارشد می‌آید — جابه‌جایی به کلیدِ سرویس کارِ PRِ بعدیِ همان ریپو است
  (راهش در بندِ ۱۲.۲). رده‌بندیِ دو درِ تازه در `tenant-matrix.test.js` (#582) و «تازه» شمردنِ کلیدِ سرویس در `assertFresh` (#588)
  با هر کدام که دوم مرج شود (بندِ ۱۲.۲ و ۱۲.۵).
- قدمِ بعدی: PRِ پل در ریپوی `server`؛ رابطِ «حالت‌ها»، «سلامت» و «پرچم‌ها» در `ShopDesk`.


### ۱۴۰۵/۰۸/۰۸ — خانوادهٔ ORD (سفارشِ آنلاین، سمتِ مدیریت و سرور)؛ شاخه‌های `claude/spec-ord-1` تا `-4`
- انجام شد: #533 (ORD-07 «عقب انداختن» و ORD-03 «باز / بسته»، سرور + وب پشتِ `shop.orders.delay`/`shop.orders.presence`)؛
  #535 (آزمونِ پذیرشِ ORD-01@38 و ORD-02@38، و ORD-05 ⇐ IMPLEMENTED-VERIFIED)؛ #561 (میزِ سفارش: ORD-08 «شلوغم» و توقف،
  ORD-09 توقفِ خودکار، ORD-04 قبولِ خودکار؛ مهاجرتِ ۱۵۷، پرچمِ `shop.orders.desk`)؛ و همین PR (ORD-18 هشدارِ شروعِ
  زمان‌بندی‌شده، ORD-19 تغییرِ وضعیتِ گروهی؛ `shop.orders.start_alert`/`shop.orders.bulk`). همه پرچمِ خاموش، با جهش‌آزمایی.
- نیمه‌کاره: صفحه‌های مدیریت برای ORD-04/08/09/18/19 (وب روی افزونهٔ `license/orders-desk.js` ِ #533؛ اندروید/کامپیوتر
  پس از #400)؛ ORD-10 (چاپِ خودکار)، ORD-13/14/15 (جایگزین/ویرایش/تحویلِ بخشی با رضایتِ مشتری) شروع نشده؛ ORD-16 و ORD-20
  به #400 سپرده شد.
- قدمِ بعدی: پس از ادغامِ #533 دکمه‌های میز و گروهی در وب؛ پس از #467، `existingOrder` روی `POST …/delay`.

### ۱۴۰۵/۰۸/۰۹ — پنلِ مرکزیِ مالک در ریپوی `server` و جابه‌جاییِ سرور
- صاحب محصول: پنلِ مدیریت **همراهِ ساختِ برنامه** ساخته شود، روی «برنامهٔ سرور» (`vil3ntec-it/server`، `homelab-panel`) در **بخشِ
  فروشگاه** (`/shop`)؛ بخشِ جدای «اشتراک‌ها» حذف و اشتراکِ هر برنامه (تانک‌تیل/پمپ، فروشگاه) در بخشِ خودش؛ برنامهٔ سرور قابلِ بردن به
  VPS، هاستِ اشتراکی و مدل‌های دیگر. از این تاریخ کار روی ریپوی `server` **با دستورِ صاحب محصول مجاز است** (پیش‌تر فقط shop).
- برنامه، قاعده‌ها و شاخه‌ها: `docs/OWNER-CONSOLE-PROGRAM-fa.md` در ریپوی `server` (شاخهٔ `claude/owner-console-program`). سمتِ برنامه‌ها
  برای «هر سرور»: شاخهٔ `claude/any-server` در همین ریپو.

### ۱۴۰۵/۰۸/۰۹ — برنامهٔ جامعِ امنیت و ضدکرک (شاخه‌های `claude/sec-*`)
- صاحب محصول خواست امنیتِ **همهٔ بخش‌ها** طبقِ «VILL3N — ULTIMATE SECURITY & ANTI-CRACK MASTER PROMPT» پیاده و هر حفره پیدا و
  درست شود؛ **هر چه مالِ فروشگاهِ آنلاینِ مشتری است کنار می‌ماند تا هنگامِ ساختش**. خلاصهٔ کاملِ متن، قاعده‌ها، دامنه، شاخهٔ هر بخش،
  شناسه‌های آزمون (SEC-AI، SEC-ART، SEC-SUB، TEN، DATA) و جدولِ وضعیت: `docs/SECURITY-PROGRAM-fa.md`.
- هشت شاخهٔ کار: `claude/sec-artifacts`، `sec-auth`، `sec-tenant`، `sec-client`، `sec-api-web`، `sec-data`، `sec-supply`، `sec-ai-access`؛
  پس از اصلاح‌های بحرانی یک بازبینیِ مستقل. سرورِ خانگی و ریپوی `server` بیرون از این مخزن‌اند و فقط به‌صورتِ کارِ صاحب محصول ثبت می‌شوند.
- **قدمِ بعدی برای هر حساب:** جدولِ §۴ِ همان فایل را ببین؛ هر ردیفِ `NOT-TESTED` کارِ باقی است. هیچ ادعای «۱۰۰٪ امن» نکن.

### ۱۴۰۵/۰۸/۰۸ — خانوادهٔ SYN (شاخه‌های `claude/spec-syn-1` تا `claude/spec-syn-4`)
- انجام شد: #501 pull ِ هم‌گرا پشتِ پرچمِ خاموشِ `sync.pull_own_newer` (SYN-06)؛ #506 وب: تغییرِ ثبت‌نشده (حافظهٔ پر) با pull گم
  نمی‌شود (SYN-01)؛ #528 آزمونِ S26 (سه دستگاه ۳۰ روز آفلاین، قطع و ادامه، 2G؛ SYN-01/04)؛ PRِ `claude/spec-syn-4` دنبالهٔ #481:
  هر دو برنامه `since` ِ محتاط در push می‌فرستند (`core/…/sync/PushSince.kt`). هیچ‌کدام مرج نشده‌اند.
- یافته‌ها: (۱) v1 روی main: دستگاهِ برنده با pull ِ بعدی مقدارِ بازنده را می‌نشاند، «شبح» یا ردیفِ رفته می‌ماند (درمان #501)؛
  (۲) وب تغییرِ ثبت‌نشده را با pull گم می‌کرد (#506)؛ (۳) push/pull ِ v1 فشرده نیستند، فقط Snapshot؛ (۴) صفِ هر دو برنامه در ۵۰٬۰۰۰ op
  کهنه‌ترین را کنار می‌گذارد (`queue_overflow`) — به سرور نمی‌رسد؛ (۵) ثبتِ وسطِ pull روی خودِ دستگاه با op ِ رسیده بازنویسی
  می‌شود (تعارض حالا ثبت می‌شود ولی دستگاه تا بازسازی واگرا می‌ماند)؛ (۶) ردیفی که دستگاهِ دیگر پس از حذف زنده کند نیمه‌ای می‌نشیند.
- نیمه‌کاره/نکرده: SYN-07 (صفحهٔ صندوقِ تعارض؛ سرور فهرست دارد، بازگرداندن فقط مدیرِ ارشد)، SYN-09 هاب (فاز ۳)، SYN-12، SYN-14،
  SYN-05/06 (v3 پیش‌فرض خاموش و بی دکمه). اجرای کاملِ ۱۰۰٬۰۰۰ op ِ S26 روی ماشینِ مشترک (فشارِ حافظه) نشد؛ عدد برون‌یابی است.
- قدمِ بعدی: پس از مرجِ #335 و #364: یافتهٔ ۴ (نگه داشتن در دفتر به‌جای انداختن) و ۵ (فیلدِ در صف بر op ِ رسیده می‌ماند) در
  `SyncCommit.absorb`/`applyRemote`؛ gzip ِ pull (و push)؛ پس از مرجِ #481، شاخهٔ syn-4 با main به‌روز شود تا آزمونِ ۵ اجرا شود.
### ۱۴۰۵/۰۸/۰۸ — سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`، ایجنتِ خانوادهٔ PUR (`/home/user/wt/fam-pur`)

- **انجام شد** (چهار PRِ پشتِ هم، هیچ‌کدام مرج نشده): #457 هستهٔ سفارشِ خرید در `core` و همتای JS با بردارهای مشترک ⇐
  #471 سرور `/api/shop/purchase-orders` و مهاجرتِ **۱۳۴** پشتِ پرچمِ خاموشِ `shop.purchase_orders` ⇐ #478 صفحه‌های گوشی و
  کامپیوتر (رسید ⇒ فاکتورِ خریدِ دفتر با شناسهٔ قطعی) ⇐ #482 تاریخچهٔ قیمت/Ctrl+I، زمانِ تأمینِ سنجیده از سفارش‌ها، برچسبِ رسید.
  PUR-02/03/04/09/10/13/14 ⇐ PARTIAL. به #315/#323 وابسته نیست و چیزی از آن‌ها تکرار نشد.
- **نیمه‌کاره**: Kotlinِ برنامه (کامپایل و `PurchaseOrderBookTest`) فقط در CI سنجیده می‌شود؛ قفلِ `FOR UPDATE` فقط روی PostgreSQL ِ CI.
  Ctrl+I در فرمِ فاکتورِ خریدِ دستی منتظرِ #323؛ PUR-11 (D-15) و PUR-12 (AI-10) عمداً ساخته نشدند.
- **قدمِ بعدی**: دیدنِ CIِ چهار PR، سپس مرج به ترتیبِ #457 ⇐ #471 ⇐ #478 ⇐ #482 (پایهٔ هر کدام پس از مرجِ قبلی به main).

### ۱۴۰۵/۰۸/۰۸ — خانواده‌های NTF و RSK (اعلانِ مشتری، ریسکِ سفارش)؛ شاخه‌های `claude/spec-ntf-1`، `claude/spec-rsk-1..3`
- انجام شد (همه پشتِ پرچمِ خاموش، هیچ پیامِ واقعی): #487 RSK-07 (سقفِ درخواستِ کد به‌ازای IP و شماره؛ شکلِ ‎+930…‎ دیگر سقفِ کد را دور نمی‌زند)؛ #511 NTF-01/03/04 (صفِ اعلانِ وضعیت به مشتری، جدولِ رویداد/کانال، قالبِ fa/ps/en، یک پیام برای هر رویداد؛ مهاجرتِ ۱۴۳)؛ #530 RSK-02/03/05 (تکراری، سقفِ مشتریِ تازه، سابقهٔ مشتری برای دکان؛ بی مهاجرت)؛ RSK-04 (فهرستِ سیاه و سفید، روی #530، مهاجرتِ ۱۶۶).
- نیمه‌کاره: نمایشِ سابقه، فهرست و اعلانِ مشتری در برنامهٔ مدیریت (APIها آماده‌اند)؛ «دستگاه» در RSK-04؛ سرویس‌دهندهٔ واقعیِ کانال‌ها (D-09)؛ NTF-02/05/06/07/08/09/10 و RSK-06/08/09/10 دست نخوردند.
- قدمِ بعدی: پس از مرجِ #530، شاخهٔ RSK-04 با main هم‌روز شود؛ سپس NTF-08 (پیگیریِ بازپرداخت در صفحهٔ «سفارشِ من» و رویدادِ `order_refunded` از دفترِ بازپرداخت).


### ۱۴۰۵/۰۸/۰۸ — پرداخت با حساب‌پی (PAY-21، PAY-08، S35)؛ شاخه‌های `claude/payments-hesabpay-1` (#518) و `claude/payments-hesabpay-1-web`
- انجام شد: سرورِ پرداختِ حساب‌پی پشتِ پرچمِ خاموشِ `pay.hesabpay` (مهاجرتِ ۱۳۹، قصدِ پرداخت، وب‌هوکِ امضاشده با تأییدِ دوم،
  S35)، APIِ دکان `/api/v1/shop/payments` برای QR/NFC ِ صندوق؛ سپس دکمهٔ ویترین، تطبیقِ هر ۶ ساعت و فهرست/بازپرداختِ پنل.
  قرارداد و کارهای صاحب محصول: `docs/PAYMENTS-HESABPAY-fa.md`.
- نیمه‌کاره: فیلدهای API ِ حساب‌پی «تأییدنشده»اند (سندِ رسمی در دسترس نبود)؛ آزمون با کلیدِ واقعی/sandbox نشده؛ صفحه‌های
  اندروید/کامپیوتر و NFC کارِ عاملِ دیگر است؛ آزمونِ مرورگرِ دکمهٔ ویترین نوشته نشد (کرومیوم در این محیط نبود).
- قدمِ بعدی: کلیدها و ثبتِ وب‌هوک، افزودنِ `/payments` به `ACCOUNT_PREFIXES` ِ پنلِ خانگی، یک پرداختِ آزمایشیِ کوچک و
  درست کردنِ `server/src/lib/payment-hesabpay.js` با سندِ رسمی.

### ۱۴۰۵/۰۸/۰۸ — شاخهٔ `claude/spec-ops2-2` (OPS-03: گزارشِ از کار افتادنِ برنامهٔ نصبی)
- انجام شد: منطق در `core/.../core/ops/CrashReports.kt` (پوشاندنِ ایمیل/شماره با هر سه خطِ رقم/توکن/عددِ ۷+ رقمی پیش از دیسک،
  کدِ `cer_<ULID>`، یکی کردنِ حلقهٔ خطا، صفِ ۲۰تایی ۷روزه)؛ `data/platform/CrashReporter.kt` + `CrashStore.kt` (گیرندهٔ خطا، صفِ اتمی روی
  دیسک، فرستادنِ بی توکن در اجرای بعد) در `MainActivity` و `desktop/Main.kt`؛ پنجرهٔ یک‌بارهٔ «برنامه ناگهان بسته شد» با کد
  (اندروید)؛ سرور `report_id` را شناسهٔ ردیف می‌کند و فرستادنِ دوباره بی‌اثر است.
  آزمون: `CrashReportsTest` ۸/۸ (Gradle ِ core)، `CrashStoreTest` ۴/۴ (kotlinc ِ محلی)، `client-errors-report-id.test.js` ۲/۲.
- نیمه‌کاره: ساختِ اندروید/کامپیوتر این‌جا ممکن نبود (dl.google.com) ⇒ CI؛ پنجرهٔ کد روی کامپیوتر نیست؛ گزینهٔ خاموش کردن در تنظیمات
  (پرسش از صاحب محصول).
- قدمِ بعدی: سبز شدنِ CI ِ «توحید بومی» و «فروشگاه من — کامپیوتر»؛ آزمونِ روی گوشیِ واقعی (یک کرشِ عمدی در ساختِ dev).
### ۱۴۰۵/۰۸/۰۸ — شاخهٔ `claude/spec-ops2-1` (OPS-01: آماده‌سازیِ انتشارِ مرحله‌ای)
- انجام شد: برچسبِ نسخه‌دارِ ثابت `<برچسب>-v<نسخه>` و کلیدِ `stage.<برچسب>` (`{version, percent}`) پشتِ پیکربندی
  (`lib/release-stage.js`، `routes/releases.js`)؛ پنلِ `GET|PUT|DELETE /api/admin/release/stage` (فقط مدیرِ ارشد، با سابقه)؛
  برنامه `staged=1` می‌فرستد و برچسبِ نسخه‌دارِ همان نسخه را می‌پذیرد (`ReleaseFeed.kt`). بی کلید ⇒ رفتارِ پیشین.
  آزمون: `release-stage.test.js` ۱۰/۱۰ (جهش ۱۰ از ۱۰)، `ReleaseFeedTest` ۱۷/۱۷ با kotlinc ِ محلی.
- نیمه‌کاره (عمداً): ورک‌فلو و امضا دست نخورد — گامِ `channel = staged` در `docs/RULES-fa.md` (بخشِ F0-27) نوشته شده؛ دلتا نیست.
- قدمِ بعدی: PRِ جدای ورک‌فلو با تأییدِ صاحب محصول؛ تا آن‌وقت کلیدِ `stage.*` زده نشود.

### ۱۴۰۵/۰۸/۰۸ — شاخهٔ `ccr-3a0d593c-ac0jpj-stale-save` (PR #407، ذخیرهٔ کهنه)
- انجام شد: بازبینیِ سرپرست، دورِ ۱. (۱) دلتای برابرِ شمارنده جمع می‌شود (دو پرداختِ ۱۰۰ ⇒ ۲۰۰)؛ (۴) همهٔ رفتارِ تازه پشتِ
  پرچمِ خاموشِ `shop.ledger_merge` (`LedgerMergeFlagAndroid.kt`، `GET /api/shop/ledger-merge/config`، ردیفِ نقشهٔ پرچم‌ها)، خاموش ⇒
  همان `main`؛ (۲) پایه‌ای که دیگر نگه داشته نشده ⇒ ذخیره رد با پیامِ `STALE_BASE`؛ (۳) ویرایشِ ردیفِ حذف‌شده در دستگاهِ دیگر ⇒
  پیامِ `editsDropped` (`ShopStore.notice`)؛ «پاک کردنِ همه» با پرچمِ روشن هم دفتر را خالی می‌کند؛ ادغامِ main.
- ریسکِ باز (در بدنهٔ PR): پایه از روی شیءهای مشترک حدس زده می‌شود؛ صفحه‌ای که از نتیجهٔ ذخیرهٔ قبلیِ خودش (نه از `data`) دوباره
  بسازد، وقتی دیسک جلوتر بود، شمارندهٔ همان ذخیره را دو بار جمع می‌کند. راهِ ریشه‌ای: `save(base, next)` با پایهٔ صریح.
- قدمِ بعدی: سبز شدنِ CI و بازبینیِ دورِ ۲؛ پس از مرج، روشن کردنِ پرچم فقط برای یک دکانِ آزمایشی (F0-27).

### ۱۴۰۵/۰۸/۰۸ — شاخه‌های `claude/spec-prd2-*` (باقی‌ماندهٔ PRD، روی زنجیرهٔ ابزارهای کالا #459⇐#466⇐#473)
- انجام شد: PRD-20 (`claude/spec-prd2-1`): ورود از xlsx/CSV با پیش‌نمایشِ تازه/بی تغییر/در دکان/به‌روز/رد، به‌روزرسانیِ کالای موجود
  فقط با تیک و بی پاک کردن، «فایلِ ردیف‌های رد» برای اصلاح و تلاشِ دوباره و «سابقهٔ ورود از فایل»؛ منطق در core
  (`ImportTable`، `ProductImportPlan`، `DecimalText`)، پشتِ `ShopPolicy.productTools`؛ بی مهاجرت.
- انجام شد: PRD-04 (`claude/spec-prd2-2`، روی `claude/spec-prd2-1`): بسته‌ها با بارکدِ هر واحد (کارتنِ ۲۴ دانه‌ای) در فرمِ کالا، اسکنِ
  کارتن ⇒ ۲۴ دانه در صندوقِ گوشی و کامپیوتر، یکتاییِ بارکد بین کالا و بسته‌ها (ثبت، ویرایش، ورود از فایل)؛ `PackBarcodes` در core.
- نیمه‌کاره/کنار: PRD-08 (گردکردنِ دقیقِ §۳.۴ برای کالای وزنی تغییرِ رفتار است و تصمیمِ صاحب محصول می‌خواهد)، PRD-12/07
  (قیمتِ عمده و واحدهای چندگانه در صندوق، هم‌پوشان با #391/#315)، PRD-17/27 (عکس‌ها در زنجیرهٔ بانکِ تصاویر).
- قدمِ بعدی: CIِ اندروید/کامپیوتر (`ProductImportFileTest`، `ProductPacksTest`)؛ پس از مرجِ #473، پایهٔ PRها را main کن.


### ۱۴۰۵/۰۸/۰۸ — شاخهٔ `claude/spec-ops2-4` (OPS-02: عبارتِ بازیابیِ فروشگاه — منطق و رمز)
- انجام شد: `core/.../core/backup/RecoveryPhrase.kt` (ساخت، خواندن با هر شکلِ نوشتن و رقمِ فارسی، وارسیِ غلطِ تایپی، KID ِ
  `rp1.<تکرار>.<نمک>` با بازهٔ امن) و `android-native/.../core/atrest/platform/PhraseBackup.kt` (PBKDF2-HMAC-SHA256 ۶۰۰ هزار + همان
  پاکتِ AES-GCM ِ `AtRestEnvelope`). بردارِ ثابتِ کلید و پاکت با Python (`hashlib` + `cryptography`) ساخته و در هر دو آزمون قفل شد.
- نیمه‌کاره (عمداً، برشِ بعد): صفحهٔ «عبارتِ بازیابی» (یک بار نشان دادن و چاپ)، نگه داشتنِ کلیدِ ساخته‌شده زیرِ Keystore/DPAPI،
  رمز کردنِ `auto-*.json`، خروجیِ USB/SD با یک لمس (SAF) و بازیابی با عبارت روی دستگاهِ تازه.
- قدمِ بعدی: پاسخِ صاحب محصول به شکلِ عبارت (۲۵ نویسهٔ لاتین یا واژه‌های فارسی) و چاپ؛ سپس صفحه و سیم‌کشی.

### ۱۴۰۵/۰۸/۰۸ — شاخهٔ `claude/spec-ops2-3` (OPS-06: ساختارِ راهنمای هر صفحه)
- انجام شد: `core/.../core/guide/ScreenGuides.kt` — راهنمای چندگامی و ویدیوی کوتاه برای ۱۶ صفحه، همه جای خالیِ پنهان؛
  `AppNav.screen` (از `AppRoot`) و بخشِ «راهنمای این صفحه» بالای F1 ِ کامپیوتر فقط برای ردیفِ READY ِ بی‌ایراد.
  آزمون: `ScreenGuidesTest` ۴/۴ (Gradle ِ core روی JVM). فهرستِ محتوای لازم: `docs/spec/OWNER-ACTIONS-fa.md` بخشِ ۷.
- نیمه‌کاره (عمداً): متن و ویدیو (کارِ صاحب محصول)؛ مسیرِ سروِ `/guides/*.mp4` پس از نخستین ویدیو؛ دکمهٔ راهنما روی گوشی (تصمیم).
- قدمِ بعدی: گرفتنِ متنِ صفحه‌ها از صاحب محصول و READY کردنِ ردیف‌ها.

### ۱۴۰۵/۰۸/۰۸ — خانوادهٔ MKT (بازاریابی): فقط آماده‌سازیِ مدیریت و سرور — شاخه‌های `claude/spec-mkt-1..3`
- انجام شد: MKT-10 و MKT-11 ⇐ PARTIAL. سرور (#572): پیوندِ کمپین `/store/go/<کد>` با منبع، بازدیدِ یکتای روز (HMAC، بی IP)،
  سفارشِ ویترین ⇐ پیوند، شمار بی دادهٔ مشتری؛ مهاجرتِ ۱۵۳ (با rollback)، پرچمِ خاموشِ `shop.storefront.campaigns`؛ آزمون ۹/۹ (روی main ۰/۹)،
  جهش ۱۰/۱۰، کلِ سرور روی PGlite ۱۶۸۴ سبز، ۰ سرخ. برنامهٔ مدیریت (#566): برگهٔ «پیوند و QR» با QR ِ دیوار/رسید، چاپ روی رسید و A4؛
  منطق در `core/marketing` (commonMain) ⇒ ۷/۷.
- تعویق (STATUS، وضعیت همان MISSING با یادداشت): MKT-03، ۰۷، ۰۸ مالِ برنامهٔ خرید (§۴۰.۵)؛ MKT-01، ۰۲، ۰۹ پس از مرجِ #417، #450/#472،
  #494/#525/#543 (همان pricer و دفترِ کد؛ سمتِ مدیریت هست: PRM-11)؛ MKT-04، ۰۵، ۰۶ رضایت و کانالِ اعلان (#511) می‌خواهند.
- نیمه‌کاره: آزمونِ برنامهٔ اندروید/کامپیوتر این‌جا اجرا نشد (dl.google.com ۴۰۳) ⇒ CI ِ #566؛ QR ِ دکان زیرِ **هر** فاکتورِ فروش هنوز نیست.
- قدمِ بعدی: سبز شدنِ CI ِ #572 و #566؛ سپس QR ِ دکان در `ReceiptLayout` (پرچمِ shop.receipt_v2) یا پیوندِ «سفارشِ اینترنتی» در صفحهٔ `/r/…`.

### ۱۴۰۵/۰۸/۰۸–۰۹ — شاخه‌های `claude/spec-crm2-1..4` (باقی‌ماندهٔ CRM/RET/STF)
- انجام شد: crm2-1 = تأییدِ برگشت با «برگهٔ امضاشده» و PINِ مدیر/صاحب، سقفِ تجمعیِ فاکتور مثلِ سرور (RET-04، RET-10، بخشِ برگشتِ
  STF-02؛ `core/perm/ReturnApprovals`)؛ crm2-2 = شمارهٔ دوم، برچسب و صافی، دکمهٔ ادغامِ تکراری‌ها در صفحه‌های قدیمِ گوشی و کامپیوتر
  (CRM-02، CRM-10، CRM-11)؛ crm2-3 = تصویرِ صورت‌حساب (CRM-05؛ `core/receipt/StatementLayout`)؛ crm2-4 = همان «تصویرِ صورت‌حساب» در کشوی
  حسابِ نسخهٔ ۳ (PR روی `ccr-3a0d593c-ac0jpj-v3-foundation`، فایل‌های تازه بایت‌به‌بایت همان). همه پشتِ تنظیم‌های خاموشِ موجود
  (`ReturnSettings`، `ShopPolicy.crmReports`). مهاجرت ندارند.
- روشِ آزمون روی ماشینِ مشترک: دیمنِ Gradle زیرِ بار کشته می‌شد؛ آزمون‌های data با کامپایلرِ Kotlin ِ کشِ Gradle (2.2.20 + serialization)
  مستقیماً روی بستهٔ واقعیِ data و `core` اجرا شد (فقط `ShopStore.stock/debt` و `LocaleChoice` شبیه‌سازی). صفحه‌ها فقط در CI.
- نیمه‌کاره: CRM-07 (پیوندِ سفارشِ اینترنتی به مشتری با کلیدِ شمارهٔ نرمال و هر دو شماره در `OrderFulfillment`) — به‌خاطرِ کارِ هم‌زمانِ
  `claude/online-refund-ledger` روی همان مسیر دست نخورد؛ CRM-15 (کلیدِ مجوزِ دیدنِ تماسِ مشتری روی طرحِ ۷نقشیِ #336 و سابقهٔ دسترسی)؛
  STF-01/04 منتظرِ #336، STF-02 برای لغو (برگهٔ لغو با برگهٔ تخفیف در یک `Sale.approval` جا نمی‌شود)، STF-03 (سقفِ تغییرِ قیمت)، STF-09 (زنجیرهٔ سابقهٔ دستگاه).
- قدمِ بعدی: CI و بازبینیِ چهار PR؛ پس از مرج، یک خط در برگهٔ برگشتِ نسخهٔ ۳ (#391): `permDecision(..., priorMinor)`.

### ۱۴۰۵/۰۸/۰۸ — شاخه‌های `claude/spec-invrpt-1` تا `-4` (باقی‌ماندهٔ INV و RPT پس از #438 و #464)
- انجام شد: #495 RPT-04 «پایانِ روز» ِ چندشیفتی (`core/pay/DayClose`، تطبیق با دفتر و «بیرون از شیفت‌ها»)؛ #551 RPT-03 راهبردِ روشِ بها
  (`core/cost/CostPolicy`، پیش‌فرض همان قیمتِ روی قلم، میانگینِ موزون فقط پیش‌نمایش)؛ #554 RPT-15 گزارشِ نقدِ پیک‌ها (سرور + برگه در میزِ تحویل)؛
  همین PR (STATUS): RPT-01، RPT-02 و RPT-06 ⇐ IMPLEMENTED-VERIFIED پشتِ `report_fresh`، RPT-09 با شکاف‌های دقیق. هیچ‌کدام مرج نشده و مهاجرتی ندارند.
- کنار ماند: INV-01 (شعبه) — سند چندشعبه را به فاز ۶ و «موج ۶» برده؛ INV-02@32 و INV-04@32 سمتِ مدیریت — هم‌پوشان با #429 (M5) و
  #464/#315؛ RPT-09 — برنامه کاربرِ لغو و برگشت و رویدادِ «حذفِ ردیف» را نمی‌نویسد (کارِ صندوق، برخورد با #391).
- قدمِ بعدی: پس از سبز شدنِ CIِ اندروید/کامپیوتر برای #495، #551 و #554، ردیف‌های RPT-04/03/15 با شمارهٔ اجرای CI؛ پرسش‌های صاحب محصول:
  D-23 (روش و مهاجرتِ بها) و مرزِ روزِ شیفتِ شبانه («روزِ باز شدن» پیش‌فرض است).

### ۱۴۰۵/۰۸/۰۸ — شاخه‌های `claude/spec-auth-1..7` (خانواده‌های AUTH، TEN، GATE-NEW)
- انجام شد (هر کدام یک PR از main، هیچ‌کدام مرج نشده، CI در صف): #488 آزمونِ کاربرِ چنددکانی روی همهٔ درهای شناسه‌دار
  (AUTH-09)؛ #493 لاگِ بی راز — راهِ ایمیلِ `log` در production متنِ نامه (کدِ ورودِ برنامه، کدِ VIP) را چاپ می‌کرد + آزمونِ سراسری
  (AUTH-10)؛ #498 افزودنِ شماره با کدِ همان شماره پشتِ پرچمِ خاموشِ `auth.phone_add_code` (AUTH-08)؛ #507 لینکِ پیگیریِ سفارش
  زمان‌دار و باطل‌شدنی پشتِ پرچمِ خاموشِ `shop.storefront.link_expiry`، مهاجرتِ ۱۴۰ (TEN-03)؛ #534 `Cache-Control: private` پیش‌فرضِ
  پاسخِ دارای اعتبارنامه و بستنِ سوکتِ زندهٔ نشستِ باطل + آزمونِ جست‌وجو/کش/سوکت (TEN-02)؛ #542 پروندهٔ شواهدِ هر آزمونِ سرور در CI
  (GATE-NEW-05)؛ این PR: STATUS (AUTH-09 ⇐ IMPLEMENTED-VERIFIED؛ بقیه با ارجاع به PRِ باز) و همین بند.
- نیمه‌کاره: GATE-NEW-01/02/03 دست نخورد (رابطِ صاحب دکان، رابطِ موجودی و دستگاه/شبکهٔ هدف می‌خواهند)؛ لینکِ QR ِ فاکتور و پیگیریِ
  شکایت هنوز بی‌انقضا؛ گامِ کدِ شماره در برنامه‌ها نیست (پرچمِ AUTH-08 تا آن خاموش بماند)؛ شواهدِ Gradle.
- قدمِ بعدی: پس از سبز شدنِ CI و مرجِ #493/#534/#507/#498، ردیف‌های AUTH-10، TEN-02، TEN-03 و AUTH-08 در STATUS بالا بروند؛
  مهاجرتِ ۱۴۰ «مرج کن» ِ صاحب محصول می‌خواهد.
### ۱۴۰۵/۰۸/۰۸ — شاخهٔ `claude/market-s6-changes` (S6 ِ قراردادِ بازار)
- انجام شد: جدولِ `market_changes` (مهاجرتِ ۱۲۶)، `lib/market-changes.js` (ثبت در همان تراکنش با امضای حالت ⇒ تکرار بی ردیف،
  پاک‌سازیِ کهنه‌ها در کارِ دوره‌ای) و نقطه‌های نوشتن در ویترین (پروفایل، حضور، کالا)، فهرستِ بازارچه، کاتالوگ و جریمه؛
  بازار کشِ هر دکان و برش/نمایهٔ جست‌وجو را با `seq` باطل می‌کند. پشتِ پرچمِ خاموشِ `market.changes`.
- نیمه‌کاره: تغییرِ قیمت از راهِ همگام‌سازیِ خامِ دستگاه (بی کاتالوگ) ردیف نمی‌سازد؛ وب‌هوک و API هنوز نیست (عمداً).
- قدمِ بعدی: بازبینیِ مدیر و مرجِ PR؛ بعد S1 (کاتالوگِ همیشه‌پر) تا قیمتِ دفتر هم همیشه از راهِ کاتالوگ ردیف بسازد.


### ۱۴۰۵/۰۸/۰۸، ساعتِ ۱۷ — سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ` (یکپارچه‌سازی؛ شاخهٔ `ccr-b7ea1bd9-ov9cut`)

**قاعدهٔ تازهٔ صاحب ریپو: هیچ‌وقت برای هر کار سیزنِ ابریِ جدا ساخته نمی‌شود** (`create_session` ممنوع). همهٔ کارها در یک
سیزن با ایجنت‌های داخلی (ابزارِ `Agent`، هر کدام worktreeِ خودش زیرِ `/home/user/wt/<نام>`) انجام می‌شوند و در فهرستِ تسک‌های
همان سیزن دیده می‌شوند. ۳۲ سیزنِ ابری‌ای که سیزنِ `claude/great-ptolemy-ptt10v` ساخته بود (برچسبِ `vill3n-prs`) متوقف و
بایگانی شدند؛ کدشان روی شاخه‌ها و PRها سالم است. **اگر در حسابِ دیگر ادامه می‌دهی:** شاید ایجنت‌های این سیزن هنوز روی این
شاخه‌ها کامیت بزنند؛ پیش از دست زدن، آخرین کامیتِ شاخه را بخوان.

**گرهِ اصلی:** main در CIِ «سرور» از ۵ روز پیش سرخ است: ۵ آزمونِ SUB-10 در `paid-routes-entitlement.test.js`، چون
`POST /shop/orders/:id/review-ack` گاردِ `needWrite` ندارد. **PR #416** (یک خط) درمانش است. تا #416 مرج نشود، CIِ سرورِ همهٔ
PRها سرخ می‌ماند. ترتیبِ پیشنهادیِ مرج: #416 ⇐ #409 ⇐ #414 ⇐ بازار (S1، S2، S6، S7، S9، S3، S5، S10، M4).

**سهم‌بندیِ شمارهٔ مهاجرت** (main تا ۱۲۱): ۰۹۷ #273، ۱۲۲ M4 (`claude/market-m4-m6`)، ۱۲۳ #336، ۱۲۴ S7+S8، ۱۲۶ S6،
۱۲۹ S9، ۱۳۰ S10، ۱۳۱ S3 (از ۱۲۲ آمد)، ۱۳۲ S5 (از ۱۲۴ آمد)، ۱۳۳ برای M2 کنار گذاشته شد. ۱۲۵، ۱۲۷ و ۱۲۸ آزادند.

| کار | شاخه / PR | وضعیت |
|---|---|---|
| درمانِ main | #416 | ایجنت در حالِ سبز کردن |
| S1 کاتالوگِ واحد | #417 (`claude/market-s1-catalog`) | درست؛ آزمون‌ها سبز؛ فقط ۵ سرخِ مشترک با main |
| S2+S4 نمای عمومی | #418 | **ریسک:** فهرستِ سفیدِ فیلدها بی پرچم روی API زنده؛ ایجنت آن را پشتِ پرچم می‌برد |
| S3 شناسهٔ عمومی | #422 | مهاجرت به ۱۳۱ منتقل می‌شود |
| S5 نمای پیک برای مشتری | `claude/market-s5-courier-view` | مهاجرت به ۱۳۲ منتقل می‌شود؛ PR در راه |
| S6 جریانِ تغییر | #419 | درست؛ منتظرِ CI و #416 |
| S7+S8 بارِ بازار | #421 | درست؛ ضربانِ ویترین `needWrite` گرفت |
| S9 حسابِ مشتری | #420 | درست؛ تصمیمِ صاحب: سفارش‌ها هم به `customer_id` گره بخورند؟ |
| S10 فیلدهای آیندهٔ سفارش | `claude/market-s10-order-fields` | در حالِ کار |
| M2 انتشارِ گروهی | `claude/market-m2-bulk-publish` | کارِ قبلی کامل push نشده بود؛ از نو |
| M4+M6 | `claude/market-m4-m6`، `claude/market-m6-ack` | در حالِ کار |
| core برای آیفون | `claude/core-common-ios` | **کارِ قبلی هرگز push نشده بود و از دست رفت**؛ از نو |
| #407 ادغامِ سه‌طرفه | — | **باگِ پول:** `LedgerMerge.fields()` دو پرداختِ برابر را یکی می‌کند؛ در حالِ رفع |
| #411 تطبیقِ فروش | — | سبز بوده؛ پس از #416 هم‌روز شود |
| #315 موتورِ موجودی | b149bd9 | بازبینیِ ۱ تا ۴ رفع شد؛ آزمون‌های Kotlin فقط در CI (پروکسی `dl.google.com` را می‌بندد) |
| #323، #335، #336، #364 | — | در حالِ کار؛ #335: درخواستِ ۴۰۴ در هر چرخه وقتی پرچم خاموش است |
| زنجیرهٔ v3: #359، #390، #391، #393، #400، #412 | — | پایهٔ #359 شاخهٔ `phone-v3` است که با main تاریخچهٔ مشترک ندارد؛ CIِ بومی و کامپایل سرخ؛ در حالِ رفع |
| #357 | — | تعارض با `phone-v3`؛ بررسیِ هم‌پوشانی با #412 |
| server: `receipt` | ریپوی server | فهرستِ درگاه `/api/receipt` را ندارد ⇐ CIِ «پنل و مرکز فرمان» سرخ؛ در حالِ رفع |

**تصمیم‌هایی که با صاحب محصول است:**
1. زنجیرهٔ v3: پایهٔ #359 به main عوض شود؟ رابطِ تازه بی پرچم جای قبلی را بگیرد؟ #357 بسته شود؟
2. #407 پس از رفعِ باگِ پول بی پرچم روی همگام‌سازیِ نصب‌شده برود؟
3. مهاجرت‌های ۰۹۷ (#273) و ۱۲۳ (#336).
4. پرچم‌های #364 (پلهٔ ۴ِ schema)، #315/#323 (تنظیمِ دکان یا `live_config`) و #335 (پرچمِ محیطی).
5. #418: فهرستِ سفید روی API زنده؛ S1: کوچ به v3 منبعِ کاتالوگ را صریح بنویسد؟
6. پاک کردنِ ۲۱۸ شاخهٔ بی‌ربط به main (تاریخچهٔ main حدودِ ۶ روز پیش بازنویسی شده) و دو شاخهٔ `research-*` که در #414 تکراری‌اند.
7. `review-ack` پولی باشد یا رایگان.

**به‌روزرسانی، ساعتِ ۲۰:۵۰:** #416، #414، #415، #431 و #456 مرج شدند و آزمون‌های سرورِ main دوباره سبزند. پاسخ‌های صاحب
محصول: `review-ack` پولی می‌ماند؛ سفارشِ آنلاین فقط برای دکانِ مشترک (#467، پرچمِ خاموش)؛ کارمزدِ فروشِ آنلاین ساخته شد ولی ۰ است
(#432 و #411)؛ رزروِ موجودی (#429)؛ پایهٔ v3 به main و پشتِ پرچمِ خاموش، و رشدِ حجمِ APK فقط یک بار پذیرفته شد؛ برنامهٔ خریدِ مشتری
فعلاً ساخته نمی‌شود و مدیریت باید همهٔ نیازهای فروشِ آنلاین را داشته باشد. ده‌ها PRِ تازهٔ تکمیلِ PARTIALها (#425 تا #482) باز است.
باگ‌های زنده که رفع شده و منتظرِ مرج‌اند: #476 (پاک شدنِ بارکدها)، #470 (گردِ تسویه)، #442 و #468 (اشتراکِ چنددوره)، #481
(cursor ِ همگام‌سازی، پرچمِ خاموش؛ پیش از روشن کردن، برنامه‌ها باید `since` را در push هم بفرستند). گلوگاه: صفِ GitHub Actions
(حدودِ ۳۶۰ اجرا). اگر سیزن به سقف خورد، ایجنت‌ها با پیامِ «ادامه بده» از همان‌جا ادامه می‌دهند.

**قدمِ بعدی:** مرجِ PRها به ترتیبِ نگهبان پس از «مرج کن» صاحب محصول برای PRهای مهاجرت‌دار و درمان‌های بی پرچم.

### ۱۴۰۵/۰۸/۰۸ — خانوادهٔ DLV (پیک و تحویل)، شاخه‌های `claude/spec-dlv-1..4`
- انجام شد (همه باز، مرج‌نشده):
  - #519: DLV-02، ناحیه و رنگِ دروازه در نشانی و کارتِ «اول نشانه». DLV-09، «اصلاحِ نشانی» پس از ناموفق (سرور، میز و ویترین).
  - #526: DLV-02، «تماس با مشتری» پشتِ پرچمِ خاموشِ `shop.delivery_courier_call`.
  - #537: DLV-03، سقفِ کارِ هم‌زمانِ هر پیک در سرور (مهاجرتِ ۱۴۸).
  - PRِ STATUS: ردیف‌های DLV. #278 روی main است، پس ارجاع‌های «(باز)» درست شد. DLV-14 و DLV-20 ⇐ PARTIAL.
- کنار گذاشته، چون PRِ باز دارند:
  - DLV-06 و سرورِ موقعیتِ DLV-16 در #423.
  - «نقدِ من» و آمارِ پیک (DLV-11/12/13) در #359 و #357.
  - S40 و سقفِ نقد در #448.
- قدمِ بعدی:
  - DLV-01: نسخهٔ وبِ پشتیبانِ پیک.
  - DLV-08: قاعدهٔ «نقد یا گران‌قیمت ⇒ کدِ اجباری»، پس از #423 چون همان `assign` را عوض می‌کند.
  - DLV-03: «نزدیک‌ترین پیک»، پس از موقعیت.
  - DLV-16: سرویسِ پیش‌زمینهٔ موقعیت در اپ.
  - MISSINGهای فاز ۴ و ۵: DLV-04، 05، 07، 17، 18.
- پرسش برای صاحب محصول:
  - پیکِ سپرده‌شده شمارهٔ کامل را برای تماس ببیند، یا شمارهٔ واسط (سرویسِ بیرونی) لازم است؟
  - آستانهٔ «گران‌قیمت» برای کدِ اجباری چند افغانی است؟

### ۱۴۰۵/۰۸/۰۸ — شاخه‌های `claude/spec-e2e-1` تا `-5` (خانواده‌های کوچکِ STATUS: E2E-*، DEL، FIN، SRCH، REC، REL، ABUSE، DATA)
- انجام شد (هیچ‌کدام مرج نشده؛ این بند در PR ِ جدا): #496 سرتاسریِ `recovery.e2e.mjs` — پشتیبان در محیطِ جدا با شمارش و جمعِ کنترلِ هر جدول و هر مجموعهٔ دفتر (REC-01)، مهاجرتِ خطادار/کشته‌شده بی اثر، برگشت به نسخهٔ پیش و نسخهٔ اصلاح‌شده (REL-01)؛ هر دو PARTIAL ماندند (D-24، نصابِ کامپیوتر).
  #502 برنامهٔ پیک از صفِ گوشی (`lib/courier.mjs` = CourierQueue.kt): DEL-01، DEL-02، E2E-DLV-07، E2E-SYNC-12 ⇐ IMPLEMENTED-VERIFIED؛ #512 (سوار بر #502، پس از مرجِ آن پایه ⇐ main): E2E-DLV-08.
  #505 «یک سفارش ⇒ یک فروشِ تحویل» (E2E-ORD-03.effect) و سنجشِ E2E-STK-05، DATA-01، DATA-02، DATA-09 روی مسیرِ پیش‌فرض ⇐ IMPLEMENTED-VERIFIED.
- یافته: بازپرداختِ سفارشِ اینترنتیِ تحویل‌شده هیچ اثری در دفترِ دکان ندارد — فروشِ `o-…` کامل می‌ماند و `handover` فقط برگشت به دکان را لغو می‌کند ⇒ فروش و دخلِ گزارشِ دکان پس از بازپرداخت بیش از واقع است (E2E-RET-11، FIN-01، DATA-04 همچنان PARTIAL). DATA-05 روی v1 همان «برندهٔ فیلد با ترتیبِ رسیدن» است.
- نیمه‌کاره/رهاشده: E2E-PCK-06 (جمع‌آوری و جایگزینی؛ فاز ۴، طرحِ «ترجیحِ خالی» و حذفِ قلم با ناوردایی‌های verify-data لازم)، SRCH-01 (صندوق؛ `ShopPolicy.kt`/`Settings.kt` نقطهٔ برخوردِ هشت PR ِ باز؛ ویترین در #454)، E2E-MKT-02 (موجودی، هم‌پوشان با #429)، E2E-OPS-14 (#335)، E2E-ADM-15 (#411)، ABUSE-01 (رابط)، DATA-08 (پشتِ payV2).
- قدمِ بعدی: ثبتِ بازپرداختِ سفارش در دفترِ دکان به شکلِ مرجوعیِ `channel = online` (سرور: فهرستِ بازپرداخت‌ها در `handover`؛ برنامه: `OrderFulfillment`) پشتِ پرچمِ خاموش، با تصمیمِ صاحب محصول دربارهٔ اثرِ موجودیِ `refunded_full`.

### ۱۴۰۵/۰۸/۰۸ — خانواده‌های IMG و IMG-PRO (شاخه‌های `claude/spec-img*`، `claude/spec-imgpro-*`)
- انجام شد (هر کدام PRِ جدا، مرج‌نشده): #458 بمبِ فشرده و سرآیندِ دوگانه (IMG-PRO-09، IMG-03؛ با IMG-01 و IMG-02)؛
  #474 sha256 ِ کلاینت و ادامهٔ ورودِ گروهی در پنل پشتِ پرچمِ خاموشِ `image_bank.resume` (IMG-14، IMG-PRO-06؛ با IMG-PRO-07)؛
  #480 مقایسهٔ اصل و نسخه با ۱:۱ ِ هم‌گام و پذیرش/ردِ نسخه (IMG-PRO-05، IMG-07)؛ همین PR ردیف‌های IMG-09، IMG-10،
  IMG-PRO-12 و IMG-PRO-18 (رابطِ پنل و برنامه روی main بود و یادداشتِ «روی main نیست» کهنه).
- نیمه‌کاره: IMG-11، IMG-12، IMG-13 و IMG-PRO-13 (نمایش و cache در برنامه) کنار گذاشته شد چون همان فایل‌ها زیرِ #433 است؛
  جدول‌های خلاصهٔ STATUS عمداً دست نخوردند، پس `spec.mjs check` تا تازه شدنشان قرمز است. #474 و #480 در ردیفِ همسایهٔ
  IMG-PRO-05/06 تعارض می‌دهند: هر دو خط بماند. ساختِ Gradle ِ اندروید/کامپیوتر فقط در CI.
- قدمِ بعدی: پس از #433، فرستادنِ `X-Content-SHA256` از `ImageBank.uploadOwn` ِ برنامه.

### ۱۴۰۵/۰۸/۰۸ — تکمیلِ PARTIALهای CRM و RET (سه برش)
- انجام شد: #435 (RET-08 فهرستِ قابلِ تنظیمِ دلیل، RET-12 رسیدِ برگشت از `core/receipt/ReturnReceiptLayout` پشتِ `shop.receipt_v2`)؛
  #436 (سرتاسریِ `S13.waste` و `S13.credit.v2`، شکافِ شناختهٔ RET-07 بسته شد)؛ #451 (CRM-03/S07: سقفِ اعتبار در صندوق با تأییدِ
  مدیر، `core/credit/CreditGate`، پشتِ `ShopPolicy.creditGate`). منطقِ تازه در `core` commonMain با `commonTest` (محلی `jvmTest` سبز).
- نیمه‌کاره: کامپایل و آزمون‌های اندروید/کامپیوتر فقط در CI (این‌جا Gradle ِ اندروید اجرا نمی‌شود). کنار ماند: CRM-02/05/07/10/11
  (رابطشان در #393)، CRM-15 (کلیدِ مجوزِ تازه؛ برخورد با #336 و تصمیمِ صاحب محصول)، RET-01/04/05/10 (فقط روشن شدنِ تنظیم مانده).
- قدمِ بعدی: سبز شدنِ CI ِ سه PR؛ پس از #391 همان `CreditGate` و فهرستِ دلیل در صندوق و برگشتِ v3.
### ۱۴۰۵/۰۸/۰۸ — شاخهٔ `claude/fix-s1-m2-catalog` (S1 × M2: انتشارِ گروهی با کاتالوگِ واحد)
- انجام شد: شاخهٔ S1 (#417) در شاخهٔ M2 (#425) ادغام شد. ریشهٔ «کالا پیدا نشد»: `setProducts` ِ M2 کالا را همیشه در `sync_rows`
  می‌جست، ولی با `storefront.catalog_source` ویترین از `catalog_products` می‌خواند ⇒ دکانِ v3 همه `product_not_found`.
  درمان: `ledgerSource` ِ مشترک در `lib/storefront.js` برای انتشارِ تکی و گروهی (کاتالوگ بی مهلت و بیرون از تراکنش تازه می‌شود).
  آزمون: `server/test/storefront-bulk-catalog-source.test.js` (۳ آزمون؛ دکانِ v1 و v3 با هر دو پرچم)، جهش‌آزمایی ۵ از ۵ کشته.
- نیمه‌کاره: هیچ. هیچ پرچمی روشن نشد.
- قدمِ بعدی: پس از مرجِ #417 و #425 این PR فقط درمان را دارد؛ پیش از آن، ترتیبِ مرج: #417 ⇐ #425 ⇐ همین.

### ۱۴۰۵/۰۸/۰۸ — شاخهٔ `claude/market-m2-bulk-publish` (M2 ِ قراردادِ بازار)
- انجام شد: `PUT /api/shop/storefront/products` (گروهی، تا ۵۰۰ کالا، `Idempotency-Key` اجباری، پاسخِ `notLive` برای هر کالا)
  پشتِ پرچمِ تازهٔ `shop.storefront.bulk` (پیش‌فرض خاموش؛ روشن نشد)؛ `notLive` در فهرستِ کالای کارمند فقط با همان پرچم؛
  منطقِ خالص در `core/src/commonMain/.../core/storefront/BulkPublish.kt`؛ صفحهٔ «کالاها»ی ویترین (`Storefront.kt`) با پرچم:
  یک درخواست برای همه، «قیمت و سقف» برای انتخاب‌شده‌ها، «دوباره بفرست» با همان کلید، و خطِ «در بازار نمی‌آید: …».
- نیمه‌کاره: «عکس از بانک» در انتشارِ گروهی ساخته نشد (بانکِ عکس مسیرِ جدای خودش را دارد)؛ صفِ گروهی ماندگار نیست (در حافظهٔ صفحه).
  آزمون‌های کاتلین این‌جا اجرا نشد (`dl.google.com` در شبکهٔ سیزن بسته بود) — فقط CI.
- قدمِ بعدی: روشن کردنِ آزمایشیِ `shop.storefront.bulk` برای یک دکان به تصمیمِ صاحب محصول؛ همان صفحه در کیتِ v3 (پس از #400).

### ۱۴۰۵/۰۸/۰۸ — شاخهٔ `claude/market-s1-catalog` (S1 ِ قراردادِ بازار)
- انجام شد: ویترین، فهرستِ صاحب دکان، انتشارِ کالا، بازار و ثبتِ سفارش پشتِ پرچمِ خاموشِ `storefront.catalog_source` (فقط `all`)
  از `catalog_products` می‌خوانند؛ `catalog.refreshProducts` فقط بخشِ کالا را از v1 یا v3 پر می‌کند، مستقل از `shop.catalog`
  و بی مهاجرت. آزمون: `server/test/storefront-catalog-source.test.js` (دکانِ v1 و v3 با یک فهرست و قیمت).
- نیمه‌کاره: هیچ. پرچم روشن نشده است. سیزنِ پیگیری (همان روز): درستیِ کار در برابرِ قرارداد سنجیده شد، آزمونِ «`active` ِ
  رشته‌ای» و جهش‌آزمایی افزوده شد. سرخیِ CI (`paid-routes-entitlement`، ۵ آزمون) روی main هم هست و کارِ PR #416 است، نه S1.
- قدمِ بعدی: پس از مرجِ #416 شاخه را به‌روز کن تا CI سبز شود؛ سپس S2 (`lib/public-views.js`)؛ تصمیمِ این‌که کوچِ دکان به v3 `catalog_settings.sync_source` را هم بنویسد (امروز حدسِ «کالای v3 دارد»).

### ۱۴۰۵/۰۸/۰۸ — شاخهٔ `claude/spec-s-wave2-4` (S78، PR #515، روی #534)
- انجام شد: سوکتِ زندهٔ عضوِ برداشته‌شده دیگر باز نمی‌ماند. سه لایه در `server/src/lib/sync-v1-live.js`: `accessChanged` (تنها درِ
  «دسترسی عوض شد»، از همهٔ راه‌های لغو صدا زده می‌شود و سوکت را در همان درخواست می‌بندد)، سنجشِ دوباره پیش از هر «scan»/«changed»
  با حافظهٔ ≤ ۵ ثانیه، و بستن سرِ انقضای توکن (4403)؛ تپشِ `dropRevoked` ِ #534 تورِ ایمنی ماند. برنامهٔ اندروید/کامپیوتر
  (`LiveSocket.kt`) بلیت را از `ApiClient` می‌گیرد و پس از 4403 یک ثانیه بعد برمی‌گردد؛ وب و برنامه با `hello` یک Pull می‌زنند.
- نیمه‌کاره: `LiveSocket.kt` فقط با کامپایلِ جدا (stub) سنجیده شد؛ CIِ اندروید/کامپیوتر و آزمونِ روی دستگاه مانده. #534 باید پیش از #515 یا با آن مرج شود.
- قدمِ بعدی: پس از سبز شدنِ CI، روی گوشی و کامپیوتر: «گوشی به‌عنوانِ بارکدخوان» را بیش از یک ساعت باز بگذار و ببین پس از 4403 کامپیوتر زود به اتاق برمی‌گردد.
- دنباله (به خواستِ هماهنگ‌کننده): بستن سرِ انقضا فقط برای کلاینتِ `caps=expiry` (برنامه و وبِ تازه) یا با پرچمِ `sync.live_expiry_close`
  (پیش‌فرض خاموش)؛ برنامهٔ کهنه پس از پیری باز می‌ماند ولی لغو همچنان می‌بندد. پرچم را پس از رفتنِ نسخه‌های پیش از S78 روشن کن.

### ۱۴۰۵/۰۸/۰۸ — شاخهٔ `claude/core-common-ios` (core کامل برای آیفون؛ بازسازی پس از گم شدنِ شاخهٔ push‌نشده)
- انجام شد: ۱۳ از ۱۵ فایلِ `core/jvmMain` به commonMain رفت (Money، Rounding، Ulid، ReceiptTokens، تقویم‌ها، متن، Payment، Shift،
  BackupRetention)؛ مرزِ سکو فقط `core/platform/Platform.kt` (JVM همان JDK؛ iOS: SecRandomCopyBytes/CC_SHA256/NSDate/NSLock؛
  linuxX64 فقط برای نگهبانِ #415)؛ `mathRound` = برگردانِ بیت‌به‌بیتِ `Math.round`. `PinCrypto` و `ApproverVault` ماندند.
  آزمون: `core` allTests روی JVM سبز (۱۳)، کامپایلِ klib ِ iOS (main و test) روی لینوکس سبز، سنجهٔ platform-deps ِ #415 سبز.
- اجرانشده این‌جا: آزمونِ desktop/android (dl.google.com بسته، Maven Central 429)، `iosSimulatorArm64Test` (فقط macOS)، کامپایلِ linuxX64
  (download.jetbrains.com بسته) ⇒ به CI سپرده شد.
- همچنین: `tools/shop-migrate` (کپیِ فایل از مسیرِ jvmMain) به commonMain و یک جانشینِ JVM ِ `core/platform` رفت (آزمون: ۲۶ سبز)؛
  main (#414، #415) ادغام شد و جدولِ §۲.۳ ِ `docs/IOS-PLAN-fa.md` به‌روز شد. PR #426.
- قدمِ بعدی: سبز شدنِ CI ِ #426 و بازبینیِ مدیر؛ سپس برشِ جدا برای `PinCrypto`/`ApproverVault` روی iOS (پلِ Swift/CryptoKit با بردار).
### ۱۴۰۵/۰۸/۰۸ — خانوادهٔ S (سناریوهای سرتاسری)، شاخه‌های `claude/spec-s-*`
- انجام شد: #444 سناریوی تازهٔ `customer.e2e.mjs` (کرومیوم) برای S131 تا S140 و S32؛ #448 گام‌های `S141` (سه جوابِ
  گم‌شده و دو تلاشِ هم‌زمان) و `S40` (عددهای سند و سقفِ نقدِ پیک)، S12b از `PayFlowTest`؛ ردیف‌ها با شاهد. جدول‌های خلاصهٔ
  STATUS عمداً دست نخوردند (دستورِ هماهنگ‌کننده) ⇒ `spec.mjs check` تا `status` زدن «با سند نمی‌خواند» می‌گوید.
- یافتهٔ BROKEN: #455 (پیش‌نویس، عمداً قرمز) — بازیابیِ پشتیبانِ کهنه روی دستگاهِ دوم فروش‌های تازهٔ دستگاهِ اول را از سرور و
  همان دستگاه پاک می‌کند (S143). کمبودِ محصول: S43 (کدِ دریافت به مشتری فرستاده نمی‌شود)، S41 (پرسشِ ادغام و سقفِ مشتریِ تازه).
- نیمه‌کاره: S01/S04/S06/S08/S12/S14/S15/S16 (رابط و سخت‌افزارِ برنامه، بی SDKِ اندروید)، S26 (۱۰۰ هزار op و 2G هارنسِ
  تازه می‌خواهد)، S27 و S39 (PRهای باز #315 و #357)، S144 (D-26 باز).
- قدمِ بعدی: تصمیمِ صاحب محصول دربارهٔ معنای «بازیابی» روی دستگاهِ همگام (#455)، سپس `spec.mjs status` پس از مرجِ #444 و #448.

### ۱۴۰۵/۰۸/۰۸ — شاخهٔ `claude/online-refund-ledger` (بازپرداختِ سفارشِ آنلاین در دفترِ دکان)
- انجام شد: بازپرداختِ سفارشِ تحویل‌شده (کامل، ردیفِ دفترِ بازپرداخت، پولِ برگشتهٔ درگاهِ #529) حالا سندِ برگشتِ همان فروشِ `o-…`
  در دفترِ دکان است (مدلِ RET، شناسهٔ ثابت، یک بار، روزِ بازپرداخت؛ پولِ آنلاین بیرونِ دخل) و کالا فقط با «کالا به انبار برگشت؟»
  (پیش‌فرض نه). سرور فقط `refunds` ِ `GET /shop/orders/handover` را می‌دهد، آن هم فقط با پرچمِ خاموشِ
  `shop.orders.refunds` (خاموش ⇒ همیشه خالی، حتی برای گذارِ ساده و درگاه)؛ بی مهاجرت و بی پرچمِ تازه. DATA-04 سبز شد.
- نیمه‌کاره: پنجرهٔ مبلغِ جزئی در برنامه (`POST …/refunds`)، چاپِ رسیدِ سندِ بازپرداخت، قرینهٔ `native.mjs` برای E2E-RET-11،
  و «برعکس» ِ RET-11 (مرجوعیِ صندوق ⇐ دفترِ بازپرداختِ سفارش). Android/کامپیوتر فقط در CI ساخته می‌شوند.
- قدمِ بعدی: پس از سبز شدنِ CI ِ «توحید بومی» و کامپیوتر، همان چهار کار.
### ۱۴۰۵/۰۸/۰۸ — خانوادهٔ PCK (جمع‌آوری و بسته‌بندی)، شاخه‌های `claude/spec-pck-1..4`
- انجام شد (همه پشتِ پرچمِ خاموشِ `shop.orders.picking`): #490 QRِ کدِ تحویل و اسکن در میزِ تحویل (PCK-11)؛ #517 مدل و APIِ سرور، مهاجرتِ **۱۴۲**
  (برگه به ترتیبِ قفسه، اسکنِ بارکد، «پیدا نکردم» با وظیفهٔ مدیر و «تمام شده» در ویترین، وزن، کسرِ خودکار هنگامِ بسته‌بندی، کیسه، شاخص‌ها)؛
  #540 دوقلوی Kotlin ِ `pick-core` با بردارِ `core/test-vectors/pick-v1.json`؛ برشِ ۴ صفحهٔ جمع‌آوری در برنامهٔ مدیریت (`PickSheet`). PRها روی هم چیده‌اند.
- نیمه‌کاره: PCK-05/06 (پیشنهادِ جایگزین با تأییدِ مشتری و قاعدهٔ حلال/دسته — دادهٔ «حلال» در کالا نیست)، PCK-08 (عکسِ بسته)، PCK-10 (قفسهٔ انتظار؛
  ستونش در ۱۴۲ هست)، PCK-12 (گروهی؛ ستونِ `batch_id` هست)، چاپِ برچسبِ کیسه، صفحهٔ وظیفه‌ها و شاخص‌ها در برنامه، ترازو در برگه.
- قدمِ بعدی: صفحهٔ «وظیفه‌های جمع‌آوری» و شاخص‌ها برای مدیر، سپس PCK-05/06 پس از تصمیمِ صاحب محصول دربارهٔ برچسبِ حلال.
### ۱۴۰۵/۰۸/۰۸ — خانوادهٔ CHK (سبد و ثبتِ سفارش)، شاخه‌های `claude/spec-chk-1..3`
- انجام شد (هر کدام PRِ جدا، پشتِ پرچمِ خاموش، با آزمونِ سرخ‌سپس‌سبز و جهش‌آزمایی): #494 هزینهٔ سبدِ کوچک و هزینهٔ بازهٔ ارسال
  (CHK-06/07، مهاجرتِ ۱۳۸)؛ #525 ترجیحِ جایگزینیِ کلی و هر قلم با «کالای مشخص» (CHK-15/16، مهاجرتِ ۱۴۴ پس از سهم‌بندیِ هماهنگ‌کننده)؛
  #545 «با اسکناسِ … پرداخت می‌کنم» و ویرایشِ دستورِ تحویل تا پیش از ارسال (CHK-14/24، مهاجرتِ ۱۴۶).
- نیمه‌کاره: سمتِ مشتریِ همین ردیف‌ها (نمایشِ ناحیه/بازه، انتخابِ اسکناس، ترجیحِ جایگزینی فقط برای کم‌موجودها) کارِ برنامهٔ خریدِ جداست (§۴۰.۵)؛
  CHK-21 (افزودنِ قلم پیش از جمع‌آوری) و CHK-17 (فهرستِ روشنِ «قیمتِ X از … به …») شروع نشده‌اند.
- قدمِ بعدی: پس از سبز شدنِ CI، بازبینی و مرجِ این سه PR؛ سپس CHK-21 در سرور و کارتِ سفارشِ برنامه.


### ۱۴۰۵/۰۸/۰۸ — خانوادهٔ POS، نیمهٔ دوم (POS-22 به بعد)، شاخه‌های `claude/spec-pos-b-1…4`
- انجام شد (هر کدام PRِ جدا از main، منطق در `core` با آزمونِ `:core:jvmTest` و جهش‌آزمایی، رفتارِ تازه پشتِ تنظیمِ خاموش):
  POS-35 تعدادِ نسخهٔ چاپِ خودکار (#486)؛ POS-32 پیوندِ فاکتورِ آنلاین در «فرستادن» و «کپیِ پیوند» ِ کامپیوتر پشتِ `shop.receipt_v2`
  و POS-33 ⇐ IMPLEMENTED-VERIFIED با شاهدِ #379 (#492)؛ POS-24 قاعده‌های پیش از پایانِ فاکتور (#503)؛ POS-39 دریافت از مشتری با F7 در
  صندوقِ کامپیوتر (همین شاخه).
- نیمه‌کاره: POS-39 در صندوقِ گوشی دکمه ندارد؛ POS-32 فرستادنِ مستقیمِ تصویر/PDF از کامپیوتر ندارد؛ قاعده‌های POS-24 مالِ هر دستگاه‌اند.
  کنار ماند: POS-41 و POS-31 (صفحه‌های فروشِ v3 در #391 بازنویسی می‌شوند)، POS-23/26/27/28/30/34/36/37 (فاز ۵)، POS-25 و POS-40 (مدلِ داده و
  پولِ شیفتِ بسته؛ تصمیم لازم)، POS-29/38 (فاز ۳/۴؛ ترازو در #445).
- قدمِ بعدی: پس از سبز شدنِ CI ِ چهار PR، دکمهٔ دریافت در صندوقِ گوشی، و تصمیمِ صاحب محصول دربارهٔ همگام شدنِ سیاست‌های صندوق برای کلِ دکان.

### ۱۴۰۵/۰۸/۰۸ — شاخهٔ `claude/market-s10-order-fields` (S10)
- **چه شد:** مهاجرتِ ۱۳۰ روی `online_orders`: `market_order_group_id` (NULL)، `platform_fee_minor` و `commission_minor` (۰، نامنفی)، `channel` ('' | storefront | market_app | api:<appId>). فقط جای خالی، **بی منطق**؛ سفارشِ تازهٔ ویترین را سرور `channel = storefront` می‌گذارد، بدنه نمی‌تواند. پاسخ‌ها و گزارش‌ها دست نخورده؛ برگشت با خط‌های `rollback:` ِ خودِ فایل (آزمونِ `order-market-fields`).
- **نیمه‌کاره:** سیزنِ نخست پیش از push بسته شد؛ از نو روی main ساخته شد.
- **سیاستِ کارمزد (تصمیمِ صاحب محصول):** فعلاً صفر، ولی کد هست: `lib/market-fees.js` نرخ‌های `market.*` ِ live_config (پیش‌فرض ۰) را هنگامِ ساختِ سفارشِ ویترین در `platform_fee_minor`/`commission_minor` می‌نویسد؛ قاعدهٔ گرد کردن و سقف در `RULES-fa.md` بخشِ «v3 · سفارش».
- **قدمِ بعد:** منطقِ سبدِ چنددکانی وقتی بازار ساخته شد؛ روشن کردنِ کارمزد فقط با نوشتنِ نرخ در live_config، و گزارشِ کارمزد هنوز ساخته نشده است.

### ۱۴۰۵/۰۸/۰۸ — شاخهٔ `claude/market-m1-storefront-screen` (PR #427)
- انجام شد: M1 ِ قراردادِ بازار. قاعده‌های فرم و صفِ آفلاینِ ویترین/بازار در `core` (commonMain) با ۲۱ آزمون (`jvmTest` سبز)؛
  همهٔ ذخیره‌های صفحهٔ ویترین از صف؛ «ثبت شد» فقط پس از ۲xx. روی صفحهٔ موجود در main ساخته شد، نه کیتِ #359.
- نیمه‌کاره: ساختِ اندروید/کامپیوتر محلی ممکن نبود (پراکسی)؛ منتظرِ CI و آزمونِ دستی روی دستگاه.
- قدمِ بعدی: اگر CI خطای کامپایل داد همان را درست کن؛ بعد آزمونِ دستیِ بی‌اینترنت و دو دستگاه.

### ۱۴۰۵/۰۸/۰۸ — خانواده‌های PRM، STF و NOTIF (شاخه‌های `claude/spec-prm-partial`، `claude/spec-prm-redeem-app`، `claude/spec-notif-partial`)
- انجام شد: PRM-02 در سرور (#450: دفترِ مصرفِ کد، مهاجرتِ ۱۳۶، پرچمِ خاموشِ `shop.promo_uses`) و در برنامه (#472 روی #450:
  گرفتنِ کدِ سقف‌دار پیش از ثبت و «سقفِ مصرف» در ویرایشگر). NOTIF-07 و NOTIF-08 با آزمونِ سرتاسریِ کرومیوم و بازیابیِ واقعیِ پشتیبان
  (#462، بی تغییرِ کد). هیچ‌کدام مرج نشده است.
- نیمه‌کاره: PRM-02 تا سبزیِ CIِ اندروید/کامپیوتر PARTIAL می‌ماند. آزاد کردنِ خودکارِ مصرف با لغوِ فاکتور هم هنوز نیست.
  NOTIF-01/07/08 به حکمِ §۳۰.۶ تا آزمونِ دستگاهِ واقعی PARTIAL می‌مانند.
- کنار گذاشته (با دلیل):
  - STF-01 و STF-04: #336.
  - STF-02 (تأییدِ لغو در برنامه): #391.
  - STF-03: perm-core در #336 و #364 بازنویسی می‌شود و نسیه در #451 است.
  - STF-09: زنجیرهٔ سریِ دستگاه و فرستادنِ «سر» در همگام‌سازی (§۱۰.۳، SEC-02).
  - NOTIF-09: #431. NOTIF-11: D-09.
  - PRM-08: وابسته به PRD-06. PRM-11: وابسته به CRM-08.
  - PRM-15: تصمیمِ مالک. PRM-18: فقط آزمونِ صفحه مانده است.
- قدمِ بعدی: پس از مرجِ #336، STF-03 (کلیدهای `sales.credit` و `sales.price` با سقف در perm-core و سرور).


### ۱۴۰۵/۰۸/۰۸ — PR #323 (دورِ CI)، شاخهٔ `ccr-3a0d593c-ac0jpj-stock-purchasing`
- انجام شد: سرِ تازهٔ #315 (همراهِ main و ترتیبِ تاریخچهٔ پرونده‌های اعتماد) ادغام شد ⇒ پنج سرخیِ SUB-10 و سرخیِ گاه‌به‌گاهِ
  ABUSE-01 می‌روند. سرورِ این شاخه جز همان #315 تغییری ندارد؛ آزمون‌های سرخ روی PGlite ِ محلی سبز.
- قدمِ بعدی: CI؛ پس از مرجِ #315، main را دوباره ادغام کن.

### ۱۴۰۵/۰۸/۰۸ — PR #323، شاخهٔ `ccr-3a0d593c-ac0jpj-stock-purchasing`
- انجام شد: سرِ تازهٔ #315 (b149bd9، پرچمِ `shop.stock_v2`) ادغام شد و تعارضی نداشت. کلیدِ `remember` ِ `stockOn` در صفحه‌های خرید `serverFlag` را هم می‌گیرد. جهش‌آزماییِ سقفِ برگشت از راهِ CI انجام شد (کامیتِ 88061fe ⇐ همین بازگردان). بدنهٔ PR هم به قالبِ #315 درآمد.
- نیمه‌کاره: آزمون‌های Kotlin این‌جا اجرا نمی‌شوند، چون `dl.google.com` بسته است. نتیجهٔ سرخِ جهش و سبزِ پس از بازگردان را باید در CI دید.
- قدمِ بعدی: CI را ببین. پس از مرجِ #416 و #315، main را دوباره ادغام کن.

### ۱۴۰۵/۰۸/۰۸ — PR #315 (دورِ CI)، شاخهٔ `ccr-3a0d593c-ac0jpj-stock-wiring`
- انجام شد: ادغامِ main (پس از #416، #470 و #273) ⇒ پنج سرخیِ SUB-10ِ review-ack رفت. ریشهٔ سرخیِ گاه‌به‌گاهِ ABUSE-01 (در CI ِ #323
  دیده شد): «تصمیم» و «جریمه»ی یک پرونده در یک میلی‌ثانیه با id ِ تصادفی جابه‌جا می‌شدند؛ `addEvent` ِ `lib/market-trust.js` حالا زمانِ
  هر رویداد را دست‌کم یکی بیش از آخرین رویدادِ همان پرونده می‌گذارد (بی مهاجرت)؛ آزمونِ ساعتِ ایستاده. کلِ سرور روی PGlite ِ محلی سبز.
- قدمِ بعدی: CI؛ سپس #323 (روی همین شاخه).

### ۱۴۰۵/۰۸/۰۸ — PR #315، شاخهٔ `ccr-3a0d593c-ac0jpj-stock-wiring`
- انجام شد: بازبینیِ دوبارهٔ کامیتِ fff48dc (موردهای ۱ تا ۴ِ بازبین: دروازهٔ `shop.stock_v2` در `live_config` AND تنظیمِ دکان،
  Dispatcher فقط وقتی روشن، `kotlin.math`، آزمونِ دفترِ بزرگ با شمارِ کار)؛ ادغامِ main؛ ردیفِ `shop.stock_v2` در «نقشهٔ پرچم‌ها»ی RULES؛
  بدنهٔ PR به قالبِ «🧩 روشِ کار». آزمونِ سرورِ پرچم روی PGlite ۱/۱ و جهش‌آزمایی (سرخ ⇐ برگشت ⇐ سبز).
- نیمه‌کاره: آزمون‌های Kotlin این‌جا اجرا نمی‌شوند (`dl.google.com` و دانلودِ بستهٔ آفلاین بسته‌اند) ⇒ فقط CI. سرورِ main تا مرجِ #416 سرخ است.
- قدمِ بعدی: پس از مرجِ #416، main را دوباره ادغام کن و CI را ببین؛ بازبین (`claude/review-lead`) دورِ ۲ را ثبت کند. #323 پس از #315.

### ۱۴۰۵/۰۸/۰۸ — شاخهٔ `claude/spec-s89-finalized` (S89، نیمهٔ سرورِ SEC-03؛ روی `claude/spec-sec2-2`)
- انجام شد: `server/src/lib/finalized-sales.js` پشتِ همان پرچمِ `shop.doc_chain`: ویرایش/حذفِ سرِ جای فاکتور و قلمِ ثبت‌شده برای
  دستگاهِ زنجیره‌دار `rejected` (`sale_finalized_edit_forbidden`) و در API ِ مستقیم ۴۰۹؛ لغو و برگشت آزاد؛ بازپخشِ یکسان پذیرفته و
  بی بازنویسی؛ برنامهٔ بی زنجیره و `/api/sync` فقط رویدادِ `security.doc_chain` (`finalized_edit`). آزمون: `finalized-sales.test.js` ۷/۷.
- نیمه‌کاره: سندهای برگشت/قرض/خرید و `transactions`؛ نمایشِ رویدادِ `finalized_edit` در پنل؛ برنامه هنوز دلیلِ ردِ تازه را فقط در دفترِ کنار نشان می‌دهد.
- قدمِ بعدی: پس از مرجِ #541 و #547 و این، پرچم برای یک دکانِ آزمایشی؛ یک هفته رویدادهای `enforced:false` پاییده شود تا معلوم شود
  کدام برنامهٔ کهنه هنوز فاکتور را سرِ جا عوض می‌کند، بعد تصمیمِ «رد برای همه».

### ۱۴۰۵/۰۸/۰۸ — شاخه‌های `claude/spec-sec2-1` و `claude/spec-sec2-2` (SEC-02، SEC-03، STF-09؛ مهاجرتِ ۱۵۱)
- انجام شد: زنجیرهٔ هشِ اسنادِ مالیِ هر سریِ دستگاه در core ِ خالص (`Sha256` با NIST، `HashChain`/`PostedDocument` از
  android-native به commonMain، `DocChain`، `DocChainCycle`)، گزارش در هر دورِ `SyncV1Worker` به `POST /api/sync/v1/chain`،
  سنجش و لنگر در `hash_chain` و رویدادِ `security.doc_chain` بی بستنِ فروش (پرچمِ خاموشِ `shop.doc_chain`)؛ کنترلِ یگانهٔ
  لغو/اصلاح (`InvoiceVoid`) و سندِ معکوس در زنجیره، وصل به تاریخچهٔ فروشِ امروز. PR دوم روی PR اول است.
- نیمه‌کاره: سیم‌کشیِ `CancelSheet` ِ نسخهٔ ۳ (#391)، دستگاهِ Sync v3 و وب؛ ردِ ویرایشِ مستقیمِ فاکتور در سرور (S89)؛
  سندِ برگشت/قرض/خرید در زنجیره؛ صفحهٔ «بررسیِ یکپارچگی» روی دستگاه؛ اجرای اندروید/کامپیوتر فقط در CI دیده شد.
- قدمِ بعدی: پس از سبز شدنِ CI و مرجِ هر دو، پرچم برای یک دکانِ آزمایشی روشن و پنل («بررسیِ زنجیره») و سابقهٔ
  `security.doc_chain` یک هفته پاییده شود.

||||||| eb152359

### ۱۴۰۵/۰۸/۰۸ — PR #364 (دورِ CI)، شاخهٔ `ccr-3a0d593c-ac0jpj-safe-core`
- انجام شد: ادغامِ main (تعارضِ فقط‌توضیحِ review-ack). ریشهٔ سرخیِ E2E: دستگاهِ آزمون (`test/e2e/lib/native.mjs` و دو push ِ خامِ
  fields/sync) پلهٔ ۴ِ ثابت می‌فرستاد، نه `wireSchema` ِ برنامه ⇒ ۴۲۶ روی دکانِ پرچم‌خاموش و همهٔ سناریوها از گامِ صفر سرخ. حالا مثلِ
  برنامه؛ E2E ِ محلی ۷۶ سبز. کارِ Electron ِ CI پس از `account-backup` ۴۵ دقیقه ایستاد و لغو شد؛ کلِ سرور زیرِ Electron 38 (PGlite) و روی PostgreSQL ِ محلی سبز (گاه‌به‌گاه؟).
- قدمِ بعدی: CI؛ اگر Electron باز ایستاد، فایلِ پس از `account-backup` را بکاو.
### ۱۴۰۵/۰۸/۰۸ — شاخهٔ `claude/sync-no-data-loss` (روی #335؛ سه یافتهٔ داده‌ازدست‌دادنِ ممیزیِ SYN + gzip ِ pull)
- انجام شد: (۱) صفِ v1 (اندروید/کامپیوتر و وب) دیگر بالای ۵۰٬۰۰۰ op کهنه‌ترین‌ها را دور نمی‌ریزد؛ از ۱۰٬۰۰۰ پیامِ «X تغییر هنوز به
  سرور نرسیده» (`core/…/sync/OpBacklog.kt`)؛ صفِ ننشسته در اندروید پیامِ روشن می‌دهد — بی پرچم (تصمیمِ سرپرست). (۲) تغییرِ در صف
  (ثبتِ وسطِ pull) با op ِ رسیده بازنویسی نمی‌شود — پرچمِ خاموشِ `sync.keep_pending` (`core/…/sync/KeepPending.kt`). (۳) ردیفی که
  دستگاهِ دیگر پس از حذف زنده کرد کامل می‌رسد — پرچمِ خاموشِ `sync.revive_full_row` (`server/src/lib/sync-v1-keep.js`). (۴) pull ِ بزرگ
  فشرده پشتِ `sync.gzip_pull`؛ push نه.
- آزمون: `sync-keep-local.test.js` ۱۳/۱۳ (پیش از درمان ۱۰ سرخ)، `:core:jvmTest` (KeepPending/OpBacklog + بردارها)، KeepLocalTest ۸/۸ و
  OpQueueBacklogDesktopTest (۶۰٬۰۰۰ op) در پروژهٔ JVM ِ جدا؛ ۱۰ جهش سرخ. Gradle ِ اندروید/کامپیوتر فقط CI.
- نیمه‌کاره: push ِ فشرده (برنامه باید بدنه را فشرده کند و از سرور بداند)؛ صفِ خیلی بزرگ کند است (هر batch/ack کلِ پرونده را
  می‌خواند)؛ v3 دست نخورد. قدمِ بعدی: پس از مرجِ #335 همین PR به main؛ روشن کردنِ سه پرچم دکان‌به‌دکان با صاحب محصول؛ با #501/#506/#553
  فقط ردیف‌های STATUS/RULES/HANDOFF برخورد دارند.

### ۱۴۰۵/۰۸/۰۸ — PR #335 (دورِ CI)، شاخهٔ `ccr-3a0d593c-ac0jpj-restore-repush`
- انجام شد: ادغامِ main (تعارضِ FREE ِ `paid-routes-entitlement`: هر دو ردیف). ریشهٔ سرخیِ PostgreSQL (`sync-epoch.test:182`):
  با pg_dump ِ ناموجود Promise ِ `closed` در `lib/backup.js` پیش از `await` بی‌شنونده رد می‌شد ⇒ unhandledRejection (در تولید یعنی
  خوابیدنِ سرور هنگامِ پشتیبانِ زمان‌بندی‌شده). درست شد؛ psql ِ بازیابی هم شنوندهٔ error گرفت؛ آزمونِ برگشت در `backup.test` (فقط PostgreSQL).
- قدمِ بعدی: CI را ببین.


### ۱۴۰۵/۰۸/۰۸ — شاخهٔ `claude/ios-step0-guard` (گامِ ۰ِ آیفون)
- انجام شد: قاعدهٔ کدِ تازه برای آیفون در `RULES-fa.md` (بخشِ «v3 · هسته‌های مشترک»)؛ سنجهٔ `tools/quality/checks/platform-deps.mjs`
  (فایلِ تازه صفر، موجود فقط کاهشی، سقفِ ۲۱۴ فایل در `baseline.json`، فعلاً گزارش)؛ هدفِ `linuxX64` پشتِ `-Pnative=true` در `core`
  و کارِ `core-native` در `ios-spike.yml` (فعلاً گزارش).
- قدمِ بعدی: پس از یک هفته بی‌خطا، سنجه برای فایلِ تازه سد شود (برداشتنِ continue-on-error) و `core-native` هم.
### ۱۴۰۵/۰۸/۰۸ — بازار M4 و M6 (شاخه‌های `claude/market-m4-m6` و `claude/market-m6-ack`)
- انجام شد: M4 (برند، بارکد، واحد، variant و `bankProductId` در نمای عمومی، جست‌وجو با بارکد/برند، «همین کالا در دکان‌های دیگر»
  و نگاشتِ دستهٔ آزاد به فهرستِ ثابت، پشتِ پرچمِ خاموشِ `market.product_details`) و M6 (رسیدِ «باز شد» ِ اعلانِ سفارش از برنامهٔ مدیریت
  با صفِ آفلاینِ `core/commonMain` ⇐ `ack_source='device'`). شمارهٔ مهاجرتِ M4 همان ۱۲۲ ماند (S3 به ۱۳۱ رفت؛ ۱۳۰ از آنِ S10 است).
- نیمه‌کاره: آزمونِ کاتلینِ کاملِ M6 این‌جا اجرا نشد (`dl.google.com` بسته بود)؛ فقط `OrderNotifyAcksTest` جدا اجرا شد. داوری با CI.
- قدمِ بعدی: بازبینی و مرجِ دو PR به دستِ سرپرست؛ PRِ M4 روی #414 (سندها) سوار است.
### ۱۴۰۵/۰۸/۰۸ — شاخهٔ `claude/market-s9-account` (S9 از `docs/MARKETPLACE-CONTRACT-fa.md` §۳)
- انجام شد: مسیرهای `/api/market/account/*` (ورود با بلیتِ تازه، خواندن/نام/پاک کردن، خروج و خروج از همه‌جا، نشانی‌ها،
  سفارش‌ها و **تغییرِ شماره** با نشست + بلیتِ شمارهٔ نو) روی همان `store_customers`؛ مهاجرتِ ۱۲۹ (`shop_chat_conversations.customer_id`)؛
  چتِ ویترین نشستِ حساب را هم می‌پذیرد. همه پشتِ پرچمِ سراسریِ `market.account` (خاموش). آزمون: `server/test/market-account.test.js`.
- نیمه‌کاره/عمداً بیرون: سفارش‌ها هنوز با `customer_phone` پیدا می‌شوند ⇒ پس از تغییرِ شماره، سفارش‌های شمارهٔ پیشین در «سفارش‌های من» نمی‌آیند
  و بازکردنِ گفت‌وگوی تازه دربارهٔ سفارشِ کهنه «پیدا نشد» می‌دهد (گفت‌وگوهای موجود می‌مانند). دکان شمارهٔ تماسِ گفت‌وگوی کهنه را همان شمارهٔ پیشین می‌بیند.
- شمارهٔ مهاجرتِ ۱۲۹ در «سهم‌بندی»ِ `docs/RULES-fa.md` ثبت شد. SUB-10 (`review-ack` در `paid-routes-entitlement.test.js`) روی main هم قرمز است و از این شاخه نیست.
- قدمِ بعدی: گره زدنِ سفارش به `customer_id` اگر صاحب محصول خواست.

### ۱۴۰۵/۰۸/۰۸ — شاخهٔ `claude/market-m3-media` (بازار M3)
- انجام شد: لوگو و تصویرِ سرِ ویترین — مهاجرتِ ۱۳۳ (`storefront_media`)، `/api/shop/storefront/media` پشتِ پرچمِ خاموشِ
  `shop.storefront.media`، سنجشِ محتوا با `image-pipeline`، نسخه‌دار؛ نمای عمومی `store.media`. در برنامهٔ مدیریت قابِ جدای
  `StorefrontMediaPanel.kt` در برگهٔ «تنظیمات»ِ ویترین (یک خط در `Storefront.kt` تا با صفحهٔ M1 بی برخورد ادغام شود).
- نیمه‌کاره: ساختِ اندروید (AGP از dl.google.com) این‌جا اجرا نشد؛ ویترینِ وب (`server/public/store`) هنوز لوگو را نشان نمی‌دهد.
- قدمِ بعدی: سبز شدنِ CI ِ اندروید و نمایشِ `store.media` در ویترین/بازار.

### ۱۴۰۵/۰۸/۰۸ — شاخهٔ `claude/market-s3-public-id` (S3 ِ قراردادِ بازار)
- انجام شد: مهاجرتِ ۱۳۱ (`storefronts.public_id` ULID ِ تغییرناپذیر با پرکردنِ ردیف‌های موجود، و `storefront_slug_redirects`)
  با rollback در `server/migrations/rollback/`؛ خواندن پشتِ پرچمِ خاموشِ `market.public_id` (۳۰۸/۳۰۱ از نشانیِ کهنه، `GET /api/store?id=`،
  `publicId` در پروفایل). آزمون: `server/test/storefront-public-id.test.js`.
- نیمه‌کاره: مسیرهای گفت‌وگو و اعتمادِ ویترین (`shop-chat-store`، `trust-store`) و `manifest` نشانیِ کهنه را دنبال نمی‌کنند؛ بازارچه هنوز
  `publicId` نمی‌دهد (جایش ماژولِ نمای عمومیِ S2 است).
- ۱۴۰۵/۰۸/۰۹: شمارهٔ مهاجرت از ۱۲۲ به ۱۳۱ رفت (۱۲۲ سهمِ `claude/market-m4-m6`)؛ PR #422 باز شد.
- قدمِ بعدی: بازبینی و مرجِ PR #422 به دستِ سرپرست؛ تصمیمِ صاحب محصول دربارهٔ نگه‌داشتنِ نشانیِ رهاشده برای همان دکان (امروز نشانیِ زنده مقدم است).


### ۱۴۰۵/۰۸/۰۸ — شاخهٔ `claude/market-s2-public-views` (S2 و S4 ِ قراردادِ بازار)
- انجام شد: `server/src/lib/public-views.js` (فهرستِ سفیدِ همهٔ شکل‌های عمومی + `SCHEMA_VERSION = 1`)؛ سازنده‌های امروزِ
  `/store` و `/market` (`profile`، `productView`، `publicOrder`، `cardOf`، `feedItem`، جست‌وجو، «سفارش‌های من»، خریدِ دوباره)
  از آن می‌گذرند، با ترتیبِ کلیدِ همان ورودی ⇒ پاسخ بی تغییر. نمونهٔ مشترک `core/test-vectors/market-public-v1.json`؛
  آزمون‌ها `server/test/public-views.test.js` و `core/src/jvmTest/.../market/MarketContractTest.kt`؛ مدل‌ها در
  `core/src/commonMain/.../market/MarketContract.kt` (kotlinx.serialization 1.7.3، همان نسخهٔ اندروید). کارِ `test` در core
  = `jvmTest`، و گامِ «آزمونِ core» در `tohid-desktop.yml`.
- دنباله (همان روز): بُرشِ پاسخِ زنده پشتِ پرچمِ `market.public_views` (scope `all`، **پیش‌فرض خاموش**) رفت تا فیلدِ تازهٔ
  M4/S3/S5/S7 پیش از رسیدن به فهرست بی‌صدا بریده نشود: سازنده‌ها `pub`/`pubList` را صدا می‌زنند (خاموش ⇒ همان شیء، پاسخِ main)،
  `view`/`list` همیشه می‌بُرند، و `router.use(views.gate)` در `/store` و `/market` پرچم را می‌خواند. روشن کردنِ پرچم
  فقط پس از هم‌گام شدنِ فهرست با فیلدهای شاخه‌های بالا (نسخهٔ ۲).
- نیمه‌کاره/بیرون از کار: `store.delivery` (ناحیه‌ها، §۱.۵) و پاسخ‌های بازه/ناحیه/گفت‌وگو/حساب هنوز از ماژول نمی‌گذرند؛
  `delivery` (پیک) شکلش ثابت شده ولی در هیچ پاسخی نیست (S5)؛ `schemaVersion` فقط در کد و پرونده است، نه در پاسخ.
- قدمِ بعدی: S1 (خواندن از کاتالوگِ واحد) یا S5 (پیک برای مشتری) — هر فیلدِ تازه ⇒ نسخهٔ ۲ و پروندهٔ `market-public-v2.json`.
- ادغامِ main (۱۴۰۵/۰۸/۱۰، تصمیمِ صاحب محصول): فیلدهایی که main همین حالا عمومی می‌فرستد، **بی v2** و با همان شکلِ main
  به v1 افزوده شد (فهرستِ سفید، `market-public-v1.json` ← `_v1Additions`، `MarketContract.kt`، §۳ ِ `MARKETPLACE-CONTRACT-fa.md`):
  `substitution`/`substituteName` (#525)، `smallBasketFeeMinor`/`slotFeeMinor` (#494)، `onlinePay` ِ پیگیری (#529)،
  `highlights.deals` با `regularPriceMinor` (#626)، `store.media` (#428). آزمون حالا همهٔ نشانی‌ها را با پرچمِ خاموش/روشن به شکل
  می‌سنجد. هنوز بیرون (پشتِ پرچم‌های خاموشِ دیگر): `order.delivery` (S5)، `order.refund` (NTF-08)، `store.publicId` (S3)، جزئیاتِ M4.

### ۱۴۰۵/۰۸/۰۸ — شاخهٔ `claude/great-ptolemy-ptt10v`
- انجام شد: بندِ ۴ِ `CLAUDE.md` (هر سه اسکیل در هر سیزن) و بندِ ۷ (چتِ معمولی بی دست‌زدن به پروژه) و هوکِ
  `SessionStart` (PR #413)؛ ممیزیِ وضعیتِ پروژه در برابرِ سندِ v32 (همین فایل).
- یافته: دو تصمیمِ «برنامهٔ اپل» و «جداسازیِ فروشگاهِ آنلاین از مدیریت» هیچ‌جا نوشته نشده بودند. حالا این‌جا ثبت شده‌اند،
  ولی هنوز به سند نرفته‌اند.
- قدمِ بعدی: ثبتِ دو تصمیم در اصلِ سند و طرحِ ماژول‌ها، و تعیینِ تکلیفِ #359.
- ۱۴۰۵/۰۸/۰۸، دنباله: بخشِ ۴۰ به سند افزوده شد. برای ۱۶ PRِ باز، هر کدام یک سیزنِ ابریِ جدا و هم‌زمان (برچسبِ `vill3n-prs`)
  باز شد که همان شاخهٔ PR را از جایی که حسابِ قبلی ماند ادامه می‌دهد: هم‌روزسازی با main، سبز کردنِ CI، رسیدگی به کامنت‌ها و کاملِ بخشِ
  ناتمام. هیچ‌کدام مرج نمی‌کنند. دو سیزنِ بررسی هم باز شد: `claude/research-ios-readiness` ⇐ `docs/IOS-PLAN-fa.md` و
  `claude/research-marketplace-contract` ⇐ `docs/MARKETPLACE-CONTRACT-fa.md`. پس از پایانِ آن‌ها، سرپرست (سیزنِ اصلی) هر PR را
  بازبینی می‌کند، `STATUS.md` و همین فایل را به‌روز می‌کند و درستی‌ها را می‌فرستد. **اگر در حسابِ دیگر ادامه می‌دهی:** پیش از دست زدن به
  شاخه‌ای از این ۱۶ PR، آخرین کامیت‌های آن را بخوان؛ ممکن است سیزنی هنوز رویش کار کند.

### ۱۴۰۵/۰۸/۰۸ — شاخهٔ `claude/market-s5-courier-view` (S5)
- انجام شد: نمای پیک برای مشتری در پیگیریِ سفارشِ ویترین (وضعیت، نامِ کوچک، تماس از راهِ گفت‌وگوی سفارش، کدِ تحویل، زمانِ تقریبی) پشتِ
  `shop.delivery_customer_view`؛ موقعیتِ تقریبی فقط «در راه» پشتِ `shop.delivery_live_location`؛ مهاجرتِ ۱۳۲ با ROLLBACK؛ آزمونِ
  `test/delivery-customer-view.test.js` (۵ از ۵ سبز). هر دو پرچم خاموش.
- نیمه‌کاره: نتیجهٔ کاملِ `npm test` و PR (به درخواستِ سیزنِ یکپارچه‌سازی متوقف شد). `paid-routes-entitlement` روی پایه هم سرخ است.
- قدمِ بعدی: PR به main، و تماسِ مستقیمِ پیک در گفت‌وگو (پرسشِ ۲ِ بخشِ ۶).
- ۱۴۰۵/۰۸/۰۸، موجِ دوم: PR #413 مرج شد. صاحب محصول گفت «همه را وقتی کامل شد مرج کن»؛ اختیارِ مرج به سیزنِ مدیر
  (`session_01BwqpPh9JtuEMvTX2hDzmsG`، گزارش در شاخهٔ `claude/review-lead` ⇐ `docs/REVIEW-LOG-fa.md`) داده شد: فقط پس از بازبینیِ او،
  CIِ سبز، بی تعارض و رفتارِ پرخطر پشتِ پرچمِ خاموش، با squash. دو گزارشِ بررسی به `docs/IOS-PLAN-fa.md` و
  `docs/MARKETPLACE-CONTRACT-fa.md` رفتند (PR #414). ۱۲ سیزنِ تازه (برچسبِ `vill3n-wave2`)، هر کدام یک PRِ تازه: نگهبانِ آیفون (گامِ ۰)،
  core کامل برای آیفون، و کارهای S1، S2+S4، S3، S5، S6، S7+S8، S9، S10، M2، M4، M6 از قراردادِ بازار. کارهای M1 (هم‌پوشان با #400)،
  M3 و M5 (منتظرِ تصمیمِ رزرو) هنوز شروع نشده‌اند. پرسش‌های باز برای صاحب محصول: رزروِ موجودی، موقعیتِ زندهٔ پیک، دستهٔ سراسری،
  حسابِ اپل و راهِ انتشار در آیفون، صندوقِ «سبکِ» آیفون.
