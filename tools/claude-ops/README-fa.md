# راهنمای ادامهٔ کار برای Claude (حسابِ دوم) — از ۱۴۰۵/۰۸/۱۱

این پوشه همهٔ ابزار و شرحِ کارهایی است که سیزنِ اول با آن‌ها کار کرد. **پیش از هر کار این را کامل بخوان**؛
صاحب‌کار نباید چیزی را از اول بگوید. به‌روزرسانی‌های تازه‌تر در issue **#606** است (آخرین کامنت‌ها را بخوان).

> **از ۱۴۰۵/۰۸/۱۶:** راهنمای کاملِ نگه‌دارنده (معماری، ساخت، CI، انتشار، ادامهٔ هر خانواده) در
> [`docs/MAINTAINER-fa.md`](../../docs/MAINTAINER-fa.md)؛ کارهای فقط-صاحب‌کار در [`docs/OWNER-TODO-fa.md`](../../docs/OWNER-TODO-fa.md).

---

## ۰. تازه‌ترین — ۱۴۰۵/۰۸/۲۲ (دستورهای تازهٔ صاحب‌کار)

بندِ بالای «گزارشِ سیزن‌ها» در [`docs/HANDOFF-fa.md`](../../docs/HANDOFF-fa.md) را اول بخوان. کوتاه:
- ⛔ بی‌توقف تا آخر؛ عامل‌های هم‌زمان (Gradle با `--max-workers=2 -Xmx2g`).
- کارهای صاحب‌کار (کلید روی سرورش، حساب‌ها، تصمیمِ باز، دستگاهِ واقعی) «بررسی‌نشده» بمانند و کار جلو برود.
- همه چیز روی همین کانتینر آزموده شود، با و بی اینترنت؛ آن‌چه نشد صریح «سنجیده نشد».
- Gradle ِ محلی: `export LC_ALL=C.UTF-8 JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS -Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8"`
  (نامِ فارسیِ آزمون بی آن «Malformed input» می‌دهد)؛ `./desktop/gradlew -p desktop test` آزمون‌های android-native را هم می‌دواند.
- نسخهٔ بستهٔ سرورِ حساب از ساعتِ کامیتِ shop می‌آید (server #140)؛ بی آن هیچ دسته‌ای به سرورِ صاحب‌کار نمی‌رسید.
- جدولِ `Str*`: یک `t()` برای هر تابع/ویژگی؛ «ایستا…» و واژه‌های فقط‌فارسی در دری رد می‌شوند.
- `server/node_modules` ِ پیوندی در worktree‌ها در `.git/info/exclude` است (کامیت نشود).
- چند عاملِ Gradle ِ هم‌زمان قفلِ کش را نگه می‌دارند ⇒ هر عامل `GRADLE_USER_HOME` ِ جدا با `GRADLE_RO_DEP_CACHE=/root/.gradle/caches`
  و `-Pkotlin.daemon.jvmargs=-Xmx2560m`؛ `verify-all.sh` فقط وقتی هیچ عاملی Gradle نمی‌دواند.
- لاگِ کارِ آیفون پر از عکسِ base64 است و `get_job_logs` فقط ۵۰۰۰ خطِ آخر را می‌دهد ⇒ گیر را با `summary.md` و `e2e.log` بخوان، نه با
  tail ِ بزرگ در گفت‌وگو (برای خواندنِ کاملش یک عاملِ جدا بفرست). blob ِ لاگ از کانتینر ۴۰۳ است.
- پس از squash-merge ِ یک PR، شاخه‌ای که روی شاخهٔ همان PR ساخته شده هنگامِ ادغامِ main ناسازگاری می‌گیرد ولی درختِ دو سو یکی است ⇒
  سمتِ شاخه را بگیر و بستهٔ وب را **از نو بساز**: نشانهٔ `<<<<<<<` در `server/public/assets/*.js` می‌ماند (server #144).

## ۰. تازه‌ترین — ۱۴۰۵/۰۸/۲۱ (پایانِ سیزنِ هماهنگ‌کننده با سقفِ هفتگی)

وضعیت و قدمِ بعد: بالاترین بندِ «گزارشِ سیزن‌ها» در [`docs/HANDOFF-fa.md`](../../docs/HANDOFF-fa.md) و آخرین پیامِ issue #606.

**قاعده‌های تازهٔ صاحب‌کار (بر بندهای پایین مقدم‌اند):**
- ⛔ **وبِ مدیریتِ فروشگاه وجود ندارد** (برداشته شد، دستهٔ ۴۸). وب فقط ویترین و بازارچه. هیچ قابلیتِ مدیریتی در `index.html`/`license/`.
- ⛔ **کدِ پمپ** (ریپوی پمپ و پرونده‌های مشترک در shop) دست نمی‌خورد. صاحب‌کار واژهٔ «پمپ» را نمی‌شناسد؛ ساده بگو: «پروژهٔ پمپ استیشنِ یعقوبی».
- همهٔ تصمیم‌های `OWNER-PENDING` با پیشنهادِ ثبت‌شده پذیرفته شده‌اند (`docs/OWNER-DECISIONS-fa.md`)؛ «نسخهٔ ۱» در `docs/spec/RELEASE-1-fa.md`.
- همهٔ پیام‌ها و **پرسش‌ها (AskUserQuestion) فارسی** — یک پرسشِ انگلیسی صاحب‌کار را عصبانی کرد. کوتاه و بی اصطلاح؛ اگر چیزی را نمی‌شناسد، خودت بهترین راه را برگزین و ساده بگو.
- «بی‌توقف تا آخر». ولی سقفِ هفتگی واقعی است: سه عاملِ هم‌زمان (هر کدام ۳۰۰ تا ۷۰۰ هزار توکن) سقف را در حدودِ یک روز تمام کرد.

**ابزارهای تازهٔ همین پوشه:**
- `offline-run.sh <فرمان…>` — همان فرمان در فضای شبکهٔ تازه: **بی اینترنت واقعی** (نه DNS، نه پراکسی)، فقط 127.0.0.1؛ پیش از اجرا خودش می‌سنجد. نمونه: `web-store-zones.mjs` بی اینترنت ۱۱۵ ✅ (۱۴۰۵/۰۸/۲۲).
- `verify-all.sh <worktree> [--offline]` — همه چیز پشتِ هم بی ایست (سرور PGlite، E2E، همهٔ زنجیره‌های مرورگر، core، desktop/android) با خلاصه در `verify-<نام>.log`؛ تنها اجرا شود.
- `ci-status.sh shop|server <pr>…` — خلاصهٔ چک‌های head ِ PR و id ِ هر کارِ سرخ (لاگ: `gh api repos/vil3ntec-it/<repo>/actions/jobs/<id>/logs`).
- `precheck.sh <worktree>` — همهٔ سنجه‌های ایستا پیش از push (api-contract، spec، whatsnew، quality، secret-scan، dari-words، kt-names، ثبتِ `Str*`). spec «سالم» نیست ⇒ `node docs/spec/tools/spec.mjs status`.
- `bmerge-docs.sh <worktree> <branch>…` — ادغامِ پشتِ هم با حلِ خودکارِ HANDOFF/WHATS-NEW/STATUS؛ روی هر تعارضِ دیگر می‌ایستد.
- `mapmerge.py <wt> <branch>` سپس `mapfix.py <wt>` — ادغامِ `docs/spec/PARTIAL-MAP-fa.md` ردیف‌به‌ردیف: از HEAD شروع، فقط ردیف‌هایی که شاخه نسبت به merge-base عوض کرده (متن یا دسته) برداشته می‌شوند؛ سپس ردیف‌های غیرِ PARTIAL ِ STATUS کنار و شمارها از نو. ⚠️ حلِ دستیِ تعارضِ نقشه با «اجتماعِ خط‌ها» جابه‌جایی�‌های شاخهٔ دیگر را برمی‌گرداند (دستهٔ ۵۴، e-rows-13u).
- `pkg-union.py <package.json>` — اجتماعِ زنجیره‌های `&&` ِ اسکریپت‌ها در تعارضِ `package.json`.

**درس‌های همین سیزن (دوباره نخور):**
1. کارِ «آزمون‌های مرورگر» ِ CI روی نخستین گامِ سرخ می‌ایستد و بقیهٔ زنجیره‌ها اجرا نمی‌شوند ⇒ پیش از push همهٔ زنجیره‌ها را محلی بی ایست اجرا کن (`test:panel`، `test:web`، `test:web-store`، `test:web-vitals`، `test:web-market`). یک push در هر دورِ CI.
2. PGlite ِ کامل (`node scripts/test-pglite.js`) با چند عاملِ هم‌زمان از ۵۰ دقیقه می‌گذرد ⇒ تنها اجرا کن، یا شمارِ سبزِ تا آن‌جا را گزارش کن و CI داور باشد.
3. ری‌استارتِ کانتینر: PostgreSQL ِ محلی را دوباره روشن کن (دستورش در بندِ HANDOFF ِ ۱۴۰۵/۰۸/۲۱)، عامل‌ها را با SendMessage از همان‌جا ادامه بده؛ منتظرهای پس‌زمینه گم می‌شوند ⇒ همیشه یک `send_later` ِ ایمنی برای PR ِ باز.
4. سنجهٔ کیفیت: در `server/src` رشتهٔ `'shop'`/`'pump'` نه (از `SHOP.app` ِ `lib/tenancy` بخوان)؛ سقفِ ۸۰۰ خط برای پروندهٔ تازه و سقف‌های خط‌مبنا ⇒ جدا کن، سقف را بالا نبر؛ واژه‌نامهٔ ویترین ۴KB برای هر بخش و ۲۱KB اصلی ⇒ بخشِ تازه بساز.
5. شاخه‌های موازی: شمارهٔ بخشِ `FLAGS-ON` و شمارهٔ مهاجرت تصادم می‌کنند ⇒ پس از ادغام بشمار (`grep '^## ' docs/FLAGS-ON-fa.md`، `ls server/migrations`)؛ به هر عامل بازهٔ شماره بده.
6. آزمون‌های «متنیِ» Kotlin (`ScenarioS46Test`، `LineSellerFlowTest` …) متنِ دقیقِ کد را می‌خواهند ⇒ وقتی رفتار عمداً عوض شد، سنجه را به متنِ تازه برسان و سخت‌تر کن، نه سست‌تر.
7. فیلدِ تازهٔ مدل ⇒ `core/test-vectors/ledger-sync-v1.json` و `resend-dropped-v1.json` مقدارِ ناتهی می‌خواهند و hash ِ هر op با روشِ خودش (سرور `syncV1.hashOf`، جاوای قدیم، ART) از نو.
8. CodeQL ِ remote-property-injection: پس از `await` هرگز `req.body.ops = …` ننویس؛ همان آرایه را در جا کوتاه کن (`lib/v1-reject.js`).
9. «یک بار» ِ نوشتن‌های مدیر: پایانِ کار زیرِ همان قفلِ کلید (R24، `lib/admin-write-once.js`)؛ آزمونِ هم‌زمانی فقط روی PostgreSQL معنا دارد.
10. پروژهٔ Gradle ِ تازه (مثلِ `bench/kotlin`) بی `gradle.properties` با حافظهٔ پیش‌فرض OutOfMemory می‌دهد ⇒ همان `-Xmx4g` ِ `desktop/gradle.properties`.
11. کارهای GitHub (بستن/ادغامِ PR) با ابزارهای MCP ِ github؛ `gh pr close` از bash را حالتِ خودکار رد می‌کند.
12. آزمونِ «۳۰ روز آفلاین» (SYN-01) در CI رد می‌شود چون کارِ `tohid-desktop.yml` ‏`npm ci` ِ سرور ندارد؛ آن workflow انتشار است ⇒ workflow ِ آزمونیِ جدا (`offline30.yml` در شاخهٔ نیمه‌کارهٔ `claude/e-rows-7`).
13. زیرِ `offline-run.sh` کرومیوم `navigator.onLine === false` می‌گوید (فقط loopback) ⇒ کدی که با آن صبر می‌کند (`store/cart-sync.js`) چیزی نمی‌فرستد و `web-store-cart-sync.mjs` آن‌جا سرخ است ولی با اینترنت سبز — رفتارِ درستِ «بی شبکه»، نه باگ؛ گوشیِ روی وای‌فایِ بی اینترنت `onLine` ِ true دارد (`claude/verify-rows-j`). PostgreSQL ِ محلی در همان فضا فقط از سوکت: `postgres://postgres@%2Ftmp:55432/<db>`.

## ۱. قاعده‌های صاحب‌کار (همیشه)

- **زبان:** هر پیام به صاحب‌کار فقط فارسی، کوتاه. **اول جوابِ پرسشِ او، بعد کار.** کامیت و عنوانِ PR فارسی با `(2.14.5)`.
- **نپرس، نایست:** سؤالی که کار را متوقف کند نپرس. با بهترین پیش‌فرضِ امن جلو برو و بگو چه کردی.
- **مرج:** هر کارِ تمام‌شده مرج می‌شود (squash، وقتی همهٔ چک‌ها سبزند؛ چک‌های «(گزارش)» ِ آیفون سد نیستند). برای مرج نپرس.
- **انتشار:** هر مرج به main خودکار **بتا** و **پیش‌نمایش** می‌سازد. **نسخهٔ پایدار فقط با اجازهٔ صریحِ صاحب‌کار.**
- **«تازه‌ها» (۱۴۰۵/۰۸/۱۲):** هر PR که کدِ برنامه را عوض می‌کند دست‌کم یک سطرِ ساده از چشمِ کاربر در `docs/WHATS-NEW-fa.md`
  می‌افزاید (قالب و قاعده‌ها سرِ همان پرونده؛ بخشِ رسیده به main بسته است ⇒ بخشِ تازه بالا). `finish-pr.sh` بی آن با
  `WHATS-NEW MISSING` می‌ایستد (تغییرِ نادیدنی: `WHATSNEW_OK=<دلیل>`)؛ CI ِ «کیفیتِ کد» هشدار می‌دهد؛ `bmerge.sh` تعارضِ آن پرونده را با هر دو سمت حل می‌کند.
- **دستیارها:** هم‌زمان **حداکثر ۳** (نه ۱۰–۲۰). بیشتر از این نه.
- **ترتیبِ بخش‌ها:** مدیریت + پیک ⇐ برنامهٔ خرید (ویترین) ⇐ بقیه.
- **اسکیل‌ها لازم‌اند:** پیش از اولین کارِ کدی در هر سیزن `smart-coding-core`، `exact-implementation-pro-max`، `concise-engineering-response` (و برای پول/ورود/داده/سرور `elite-product-engineering-ultimate-memory`).
- **آیفون همیشه:** برنامهٔ آیفون از همان کدِ مشترک (Compose Multiplatform) ساخته می‌شود و **در هر دسته جلو می‌رود**. کارهای فقط-صاحب‌کار (حسابِ Apple Developer، Team ID، bundle id، امضا، App Store Connect، کلیدِ APNs) در `docs/IOS-PLAN-fa.md` §۰.۶.
- **دست نزن:** `server/src/lib/sync-v1.js`، `PUMP_TABLES`، `/api/pump/*`، `admin-pump` (مشترک با پروژهٔ پمپ). `finish-pr.sh` جلویش را می‌گیرد.
- **شاخهٔ کلون:** شاخهٔ `/home/user/shop` (`fix-report-channel`) را **هرگز** عوض نکن؛ همهٔ کار در worktree زیرِ scratchpad.
- **تصمیم‌های به‌تعویق‌افتاده را نساز:** مثلاً PUR-11 (#784، پیش‌نویس) با D-15 تا دکانِ واقعی لازم نشود ساخته نمی‌شود. S47 با D-05 تعارض دارد (تصمیمِ صاحب‌کار).
- **دسته‌ی کار (issue #606)** دفترِ تحویل است: پس از هر دسته به‌روزش کن.

## ۲. تصمیم‌های تازهٔ صاحب‌کار (۱۴۰۵/۰۸/۱۱)

1. **تنظیماتِ ساده:** گزینه‌ها کامل بمانند، **هیچ چیز حذف نشود**، ولی هر چه تصمیم نمی‌خواهد **روشن و پنهان** باشد؛ فقط آنچه واقعاً تأیید/انتخابِ صاحب‌کار می‌خواهد دیده شود. «پیشرفته» با فهرستِ بلند **بد بود** (صاحب‌کار گفت بدتر شد). طرحِ نهایی: **جست‌وجو + شش دسته** (دکان و امنیت · فروش و پرداخت · کالا و گدام · دستگاه‌ها · داده و پشتیبان · ظاهر و زبان) + صفحهٔ **«قابلیت‌ها»** با کارت برای ماژول‌های اختیاری؛ «(آزمایشی)»ها دیده نمی‌شوند؛ هیچ سرخطی دو بار نیاید. شرح: `briefs/brief-settings-v2.md`.
2. **فارسی و دری هر دو:** فارسی دست نمی‌خورد؛ «دری» زبانِ چهارم (`prs`) است = متنِ فارسی + لایهٔ واژه (`core/.../text/DariText.kt`). **هر مقدارِ درونِ متنِ فارسی در `Str*` باید از `asIs(…)` بگذرد** (آزمونِ سراسریِ `DariWordsTest`). واژه‌های نامطمئن (انبارگردانی، گاوصندوق، اسکناس، پیامک، تلفن، کالا، سود، چاپگر، تراکنش، کیسه، دوربین…) فارسی می‌مانند تا صاحب‌کار بگوید. «پول میده» غلط بود.
3. **شکلِ قدیم هرگز:** رابطِ قدیم (legacy) نباید دیگر بیاید؛ کلیدِ «رابطِ تازه (نسخهٔ ۳)» برداشته و `UiV3` همیشه روشن (شاخهٔ `claude/ui-v3-guard`، شرح: `briefs/brief-ui-v3-always.md`). پاک‌کردنِ کاملِ کدِ legacy مرحلهٔ بعد است، پس از اطمینان که هیچ صفحه‌ای به آن تکیه ندارد.
4. **«دستگاه‌ها»:** یک بلوتوث برای همهٔ دستگاه‌ها؛ اسکن وقتی برنامه بسته/گوشی خاموش (حافظهٔ اسکنر)؛ مبایل و کمپیوتر؛ پرداخت پس از اسکن: پیش‌فرض فاکتورِ نگه‌داشته + یک لمس؛ «کارتِ فرمان» (فقط نقد، کلاسِ B، پیش‌فرض خاموش). شرح: `briefs/brief-devices.md`.
5. **حساب‌پیِ هر دکان:** پول مستقیم به حسابِ همان دکان، **کلیدِ سکو هرگز جایگزین**؛ حالتِ «ساده» (شماره/QR، تأیید با دستِ صندوقدار) و «خودکار» (کلیدِ فروشنده، وب‌هوکِ امضاشده، تأیید فقط با کد + مبلغ)؛ **رمزِ جدا** برای بخشِ حساب‌پی. پیشنهادِ «تأییدِ خودکار با هم‌مبلغ بودنِ پیامک» **رد شد** (جعلِ پیامک، مبلغِ یکسان، سیاستِ Play). شرح: `briefs/brief-hesabpay-per-shop.md`.
6. **پیش‌نمایشِ گوشیِ صاحب‌کار** (`tohid-native-preview`، بستهٔ `ir.vil3ntec.tohid.preview`) حالا با هر push به main ساخته می‌شود. اگر صاحب‌کار گفت «نسخه تازه نشد» اول همین ورک‌فلو را ببین.

## ۳. نقشهٔ پروژه

- `android-native/` برنامهٔ اندروید (رابطِ نسخهٔ ۳ در `ui/v3`، قدیم در `ui/legacy`)؛ `desktop/` کامپیوتر (همان کد، تولیدشده)؛ `core/` منطقِ مشترک (commonMain)؛ `ios/` آیفون (شیم‌ها + `ios-exclude.txt`).
- `server/` Node/Express روی PostgreSQL و PGlite؛ ویترین در `server/public` (بودجهٔ بارِ نخست: store.js + kit.js + boot.js < 75KB؛ واژه‌نامهٔ هر بخش ≤ 4KB).
- **مشخصات:** `docs/spec/STATUS.md` (۷۶۴ ردیف؛ `node docs/spec/tools/spec.mjs status` جمع‌ها را می‌سازد و `check` باید «سالم» بدهد). قاعده‌ها: `docs/RULES-fa.md` (کامل نخوان؛ با grep سرخطِ بخش را پیدا کن).
- **پیشرفت:** پس از #835: VERIFIED ۳۵۹، PARTIAL ۳۲۲، MISSING ۴۵، NOT-VERIFIED ۱۲، BLOCKED ۲۶ ⇒ حدودِ ۶۸٪ (VERIFIED=۱، PARTIAL=۰٫۵).
- **مهاجرت‌ها:** تا ۲۰۴ گرفته شده (۲۰۴ = `shop_hesabpay` در دستهٔ ۱۱). بعدی ۲۰۵ (پیش از کامیت `ls server/migrations` را ببین).

## ۴. روشِ کار: دسته (batch)

1. هر کار در یک worktree و شاخهٔ جدا (`claude/<نام>`) ⇐ یک PR.
2. چند PR ِ آماده را در `ccr-3a0d593c-ac0jpj-batch-N` ادغام کن:
   ```
   export S=<scratchpad ِ همین سیزن>   # مثلاً /tmp/claude-0/<...>/scratchpad
   OPS=/home/user/shop/tools/claude-ops   # یا همین پوشه در worktree
   git -C /home/user/shop worktree add -b ccr-3a0d593c-ac0jpj-batch-N $S/wt-batchN origin/main
   ln -s /home/user/shop/server/node_modules $S/wt-batchN/server/node_modules
   bash $OPS/bmerge.sh $S/wt-batchN claude/a claude/b ...   # STATUS خودکار؛ تعارضِ دیگر ⇒ STOP
   ```
   تعارض‌ها: `python3 $OPS/cres.py <file> o|t|ot|to`، `baseline.json` با `python3 $OPS/baseline-max.py tools/quality/baseline.json`، STATUS با `python3 $OPS/batch-status.py`.
3. **آزمونِ محلی:** `bash $OPS/desk-check.sh $S/wt-batchN` (هسته + کامپیوتر؛ ۱۳ آزمونِ `ui.theme` فقط محلی سرخ‌اند) و سرور روی PGlite: `bash $OPS/a3-test.sh $S/wt-batchN test/<file>...` (۵ آزمونِ `pg_dump` روی PGlite همیشه سرخ‌اند). PostgreSQL ِ محلی: پورت 55432، کاربر shop/shoppass، `TEST_DATABASE_URL=postgres://shop:shoppass@127.0.0.1:55432/shop_test`.
4. **push:** `WT=$S/wt-batchN BUMP=1 bash $OPS/finish-pr.sh 0 ccr-3a0d593c-ac0jpj-batch-N` (spec، بارگذاریِ برنامه، شمارهٔ مهاجرت، کیفیت با شبیه‌سازیِ squash، eslint، نگهبانِ پمپ، push).
   **پیش از آن پایانِ کامیتِ همین سیزن را بده** (کامیت‌هایی که خودِ ابزار می‌سازد — unwrap و سقفِ کیفیت — همین را می‌گیرند؛ سیزنِ کهنه هرگز):
   ```
   export CLAUDE_FOOTER='Co-Authored-By: Claude … <noreply@anthropic.com>
   Claude-Session: https://claude.ai/code/session_…'   # دقیقاً همان دو سطرِ «Attribution» ِ همین سیزن
   ```
   نبودن، بدشکل بودن یا سیزنِ دیگر (در سیزنِ ابری با `CLAUDE_CODE_REMOTE_SESSION_ID` سنجیده می‌شود) ⇒ `FOOTER MISSING/BAD/STALE` و
   خروج پیش از هر کار (`footer.sh`). سپس PR، صبر برای CI (`bash $OPS/wprs.sh <pr>`)، squash-merge، و بستنِ PRهای درونش با کامنتِ «با دستهٔ #… در main آمد».
5. **دستهٔ بعدی روی سرِ دستهٔ قبلی** چیده می‌شود. پس از squash ِ قبلی، ادغامِ main تعارض می‌دهد؛ چون درختِ main همان دستهٔ قبلی است، **سمتِ دسته را نگه دار** (`git checkout --ours`)، و STATUS را با `batch-status.py`.
6. PR ِ پشته‌ای که پایه‌اش squash شد: فقط کامیت‌های خودش را cherry-pick کن (`git log --no-merges <br> ^<base-br> ^<old-main>`).

## ۵. دام‌های شناخته‌شدهٔ CI

- **SUB-10:** هر مسیرِ تازه در هر دو `paid-routes*.test.js` دسته می‌خواهد (STORE_PAID ⇒ ۴۰۴ `store_not_found` با `sf.open`).
- `shop-route-inventory` برای مسیرهای `:id` ردیفِ واقعیِ کاشته‌شده؛ `route-authz-inventory`.
- **CodeQL `js/missing-rate-limiting`:** از `(2.14.5)` model pack ِ `.github/codeql/extensions/shop-javascript` `rateLimit` ِ مخزن را می‌شناساند؛ سقفِ IP با `rateLimit` **پیش از** احراز (`router.use(rateLimit(…))`) کافی است. هشدارِ تازه یعنی مسیرِ واقعاً بی سقف (یا model کهنه: `node --test tools/security/codeql-models.test.mjs`). ساکت کردن ممنوع.
- **Kotlin:** نامِ آزمونِ backtick بی `: . ; [ ] / < > \` (`python3 $OPS/kt-names.py <wt>`)؛ وابستگیِ پلتفرم فقط در `*Android.kt`؛ هر `Str*` تازه در `core/i18n/I18nTables.kt`؛ هر نویسندهٔ تنظیم صفحهٔ v3 می‌خواهد (`SettingsPortTest.EXCEPTIONS` خالی است).
- **حافظه:** کامپایلِ Kotlin با `-Pkotlin.compiler.execution.strategy=in-process` و `--no-daemon` (desk-check همین را دارد).
- **رفع‌شده‌ها (دوباره نیاورید):** `tenant-matrix` (ردپای «الف» پس از آرام شدنِ نوشتن‌های بی‌await)، `web-store-zones.mjs` (`addRice` با کلیکِ DOM).
  بلیت/توکنِ «دست‌خورده» با پسوندِ ثابت (`slice(0, -2)}xx`) یک بار در ۴۰۹۶ خودِ توکنِ درست است ⇒ فقط `h.tampered(token)` ِ `server/test/helpers.js`
  (سرخیِ «زیرِ Electron» ِ `shop-chat` روی 83dcd9f2، run 41585590213).
- **«آزمون‌های سرور زیرِ Electron»** گاه ۴۵ دقیقه گیر می‌کرد؛ PR ‏#838 (نگهبانِ پرونده + ساختنِ PGlite با حلقهٔ رویدادِ زنده) — باید CI ِ آن سبز و مرج شود.
- **چک‌های آیفون** («رابطِ مشترکِ آیفون — klib» و «برنامهٔ آیفون — Xcode») `continue-on-error`اند؛ فایل‌های تازهٔ اندروید باید برای iOS شیم بگیرند، نه exclude.
  shim ِ هم‌نامِ ردهٔ زمانِ اجرای Kotlin/Native (`java.lang.Double`، `Long`، `Number`…) **`internal`** باشد، وگرنه پیوندِ چارچوب با
  `OBJC_CLASS_$_TohidShared<نام>: symbol multiply defined` می‌افتد (`ObjcNamesJvmTest`؛ `docs/IOS-PLAN-fa.md` پایانِ §۰.۱۲).
  از گامِ ۸ کارِ «برنامهٔ آیفون» جریان‌های اصلی را سرتاسری روی شبیه‌ساز با سرورِ واقعی (PGlite، `ios/e2e/backend.mjs`) می‌زند؛
  عکسِ هر گام و `summary.md` در مصنوعِ `ios-e2e-shots` (`docs/IOS-PLAN-fa.md` §۰.۱۳). برچسبِ تازهٔ صفحه‌های ورود، صندوق، کالا،
  «بیشتر» و زبان ⇐ همان آزمون (`CoreFlowsE2EIosTest`) را ببین.

## ۶. محیط

- **دیسک تنگ** (~۱٫۵ گیگ آزاد). `/tmp/e2e-*` حدودِ ۶ گیگ است ولی پاک‌کردنش با sandbox رد می‌شود؛ worktree ِ کارهای مرج‌شده و پوشه‌های build را پاک کن.
- **حافظه** ~۱۵ گیگ؛ دیمن‌های بیکارِ gradle را ببند.
- `$OPS/v3f/offline.init.gradle` برای آزمونِ کامپیوترِ بی‌اینترنت لازم است (desk-check خودش می‌دهد).

## ۷. وضعِ لحظهٔ تحویل (برای ادامه)

| چیز | شاخه / PR | وضع |
|---|---|---|
| دستهٔ ۱۱ | #837 `ccr-3a0d593c-ac0jpj-batch-11` | #832 دستگاه‌ها، #833 حساب‌پی، #834/#836 آیفون ۲/۳؛ دو رفع (CodeQL ِ `shop-hesabpay.js`، `addRice`) push شد؛ CI را ببین و مرج کن؛ بعد PRهای درونش را ببند |
| گیرِ Electron | #838 `claude/electron-hang` | کامیت‌ها push شد؛ CI و مرج |
| شکلِ قدیم هرگز | `claude/ui-v3-guard` | `briefs/brief-ui-v3-always.md` |
| تنظیماتِ نسخهٔ دوم | `claude/settings-v2` | `briefs/brief-settings-v2.md` |
| کارتِ فرمان | `claude/command-card` | `briefs/brief-devices.md` §۹ب |
| آیفون گامِ ۴ | `claude/ios-app-phase4` | `docs/IOS-PLAN-fa.md` §۰.۸ |

اگر شاخه‌ای روی origin نبود یعنی کارش push نشده؛ از روی brief از نو بساز.

**صف پس از این‌ها:** PAY-07، دریِ ویترینِ وب، پاک‌کردنِ کدِ legacy پس از v3 ِ همیشه، دورِ تازهٔ شاهدِ CI پس از هر دسته، کمبودهای PARTIAL (اول مدیریت و پیک)، MISSINGها (POS-16/RPT-18 فازِ ۶، HW-08، AI-06/09/10/12، PUR-12، S70، S109، S121) — بی تصمیم‌های به‌تعویق‌افتاده.

## ۸. ابزارها

| ابزار | کار |
|---|---|
| `finish-pr.sh <n> <branch>` (`WT=`، `BUMP=1`، `CLAUDE_FOOTER` لازم؛ `WHATSNEW_OK=<دلیل>` برای تغییرِ نادیدنی) | بررسیِ کامل («تازه‌ها» هم) و push |
| `footer.sh` (`. $OPS/footer.sh; claude_footer`) | پایانِ کامیتِ ابزارها از `CLAUDE_FOOTER`، سنجیده با سیزنِ کنونی |
| `bmerge.sh <wt> <branch…>` | ادغامِ پیاپی در دسته |
| `batch-status.py` | ادغامِ ردیف‌به‌ردیفِ STATUS |
| `cres.py <file> o\|t\|ot\|to` | حلِ تکه‌های تعارض |
| `onew.py`، `list-union.py`، `i18n-union.py` | تعارض‌های فهرستی |
| `baseline-max.py <file>` | بیشینهٔ سقف‌های کیفیت |
| `a3-test.sh <wt> <files>` | آزمونِ سرور روی PGlite |
| `desk-check.sh <wt>` | هسته + کامپیوتر |
| `wprs.sh <pr>…`، `wbr.sh main <sha8> <label>` | صبر برای CI / ساختِ بتا |
| `kt-names.py <wt>` | نامِ آزمون‌های Kotlin |
| `asis-wrap.py <Str*.kt>` | پیچیدنِ جاهای خالیِ متنِ فارسی در `asIs` |
| `unwrap.py` | برداشتنِ wrap ِ محلی در مسیرهای Express 5 |
