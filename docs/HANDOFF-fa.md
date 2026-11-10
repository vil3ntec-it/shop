# دفترِ تحویل بین حساب‌ها (HANDOFF)

صاحب ریپو با دو حسابِ Claude کار می‌کند و هر وقت یکی به سقفِ مصرف برسد، با حسابِ دیگر ادامه می‌دهد.
**هر سیزن، پیش از کار روی پروژه، این فایل خوانده می‌شود و پیش از پایانِ سیزن یک بند به «گزارشِ سیزن‌ها» اضافه می‌شود**: چه
کاری انجام شد، چه کاری نیمه‌کاره ماند و قدمِ بعدی چیست. وضعیتِ تک‌تکِ شناسه‌ها همچنان در `docs/spec/STATUS.md` است.
این فایل فقط تصویرِ کلی، تصمیم‌های صاحب محصول و کارهای در جریان را نگه می‌دارد.
**تصمیم‌های مانده با صاحب‌کار (فهرستِ یکتا، از ۱۴۰۵/۰۸/۱۹):** [`OWNER-PENDING-fa.md`](OWNER-PENDING-fa.md).

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
### ۱۴۰۵/۰۸/۱۹ — شاخهٔ `claude/dari-mirror` (آینهٔ JS ِ DariCoverageTest برای جدول‌های `Str*`؛ روی main ‏99036497؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: روی `ccr-…-batch-43` ‏f067bf9c فقط CI ِ اندروید سرخ شد (`StrProductGallery.notSent`، `StrPromoHeld.releaseFailed`: «دوباره بزنید») و همهٔ JS سبز بود،
  چون آینهٔ `dari-text.test.js` فقط خرید + وب را می‌سنجید. حالا `KT.appTexts` (`server/test/kt-source.js`) همان `rows(I18nTables.all)` را از خودِ Str*.kt می‌خواند
  (۱۲۵ جدول، ~۹۳۷۰ فارسی؛ قالب ⇒ protect، `+`، چندسطری، `fa =`، `td`، `if … else`، `asIs(…)`، ثابتِ پروندهٔ دیگر) و آزمونِ تازهٔ آینه همان `of` + `farsiOnlyLeft` را می‌گذراند؛
  خوانده‌نشده ۲ (`StrOnline.coded`، `StrOnline.deskActionLabel`؛ سقفِ `APP_UNREAD_MAX`). روی main سبز، روی f067bf9c دقیقاً همان دو سرخ، با دو جملهٔ PHRASES سبز. فقط آزمون (`WHATSNEW_OK=test-only`).
- نیمه‌کاره: هیچ. یادداشت برای رفعِ دستهٔ ۴۳: جز دو سطرِ PHRASES و `dari-words.js --write`، دو ردیف در `docs/DARI-REVIEW-fa.md` هم لازم است (وگرنه آزمونِ «فهرستِ بازبینی» ِ همان پرونده سرخ).
- قدمِ بعدی: هماهنگ‌کننده شاخه را در دسته بگذارد؛ پس از ادغامِ رفعِ دستهٔ ۴۳ همین آینه سبز می‌ماند.
### ۱۴۰۵/۰۸/۱۹ — شاخهٔ `claude/rsk-platform` (تمیزکاری: بی سقفِ پلتفرمیِ تازه برای `OrderRiskListsV3.kt`؛ روی `ccr-…-batch-42` ‏1879be76؛ سیزنِ `session_01BwqpPgixfqMFfpZzgeTkyJ`)
- انجام شد: `090a2373` (از `claude/rsk-app`) برای یک `LocalContext` در صفحهٔ v3 سقفِ تازهٔ ۱ در `platformDeps` ِ `tools/quality/baseline.json` گذاشته بود.
  پوستهٔ مسیرِ `RiskListsRoute` (مخزن از `Context`) به `OrderRiskListsAndroid.kt` رفت (مرزِ `*Android.kt` در `PLATFORM_DEPS.boundary`)؛ بدنه `RiskListsContent(repo)`
  در `OrderRiskListsV3.kt` بی‌پلتفرم ماند و سطرِ سقف برداشته شد. بقیهٔ سقف‌های همان کامیت فقط پایین آمده بودند و دست نخوردند. quality، eslint، spec «سالم»، آینه‌های Kotlin و dari ۲۱/۲۱.
- نیمه‌کاره: کامپایلِ اندروید/کامپیوتر و `V3ReachabilityTest` (`RiskListsRoute` ⇐ `RiskListsContent` ⇐ `RiskEntriesList`) فقط در CI.
- قدمِ بعدی: هماهنگ‌کننده شاخه را در دسته بگذارد. `finish-pr.sh` با `[ -n "$BUMP" ]` می‌سنجد ⇒ `BUMP=0` هم سقف می‌افزاید؛ برای «بی سقفِ تازه» `BUMP` را خالی بگذارید.
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
