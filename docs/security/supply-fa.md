# امنیتِ زنجیرهٔ تأمین — §۱۴ برنامهٔ امنیت (از ۱۴۰۵/۰۸/۰۹)

شاخه‌ها: `claude/sec-supply` (#578، وابستگی‌ها) و `claude/sec-supply-ci` (ورک‌فلوها، اسکن‌ها، همین سند).
دامنه: وابستگی‌ها و lockfileها، ورک‌فلوهای GitHub Actions، اسکنِ secret/وابستگی/SAST، SBOM، جدایی ساخت و انتشار، کانالِ
آزمایشی/پایدار. هیچ مقدارِ secretی در این سند، لاگ یا گزارش نیامده؛ فقط نام، جا و شیوهٔ نگه‌داری.

## ۱. فهرستِ اجزا (inventory)

| اکوسیستم | کجا | قفل / سنجاق | در CI |
|---|---|---|---|
| npm (production) | `server/package.json`: ۵ وابستگیِ مستقیم (`@electric-sql/pglite`، `express` 5، `pg`، `web-push`، `ws`)؛ ۹۷ بسته در قفل؛ بی devDependency | `server/package-lock.json` (v3، با integrity) | `npm ci` (حالا بی راهِ فرار)؛ `npm audit` در `security.yml` |
| npm (ابزارِ آزمون، سراسری) | `server.yml`: `playwright@1.56.1`، `axe-core@4.10.3`، `electron@38` | نسخهٔ دقیق جز electron (فقط اصلی) | فقط آزمون، توکنِ خواندنی، بی secret |
| JSِ کپی‌شده (vendored) | `license/vendor/zxing-0.21.3.min.js` + مجوزش | نسخه در نام | به مرورگرِ دکان می‌رسد |
| Gradle | `android-native`، `admin-android` (CI با `gradle` ِ نصب‌شده روی runner، wrapper 8.11.1 فقط برای IDE)، `desktop` و `android` (`gradlew` 8.14.3)، `core` (includeBuild در هر دو برنامه)، `tools/shop-migrate`، `bench/kotlin` | نسخهٔ پلاگین‌ها و کتابخانه‌ها در `build.gradle.kts`/`libs.versions.toml`؛ **بی** قفل و بی verification-metadata | مخزن‌ها: `google()`، `mavenCentral()`، `gradlePluginPortal()`؛ کامپیوتر: `TOHID_OFFLINE_M2` (اختیاری، اول) |
| Gradle wrapper | `desktop/` و `android/`: `gradle-wrapper.jar` | jarها با فهرستِ رسمیِ gradle.org یکی‌اند (محلی سنجیده شد)؛ `setup-gradle@v4` هم در CI می‌سنجد؛ حالا `distributionSha256Sum` در هر چهار `gradle-wrapper.properties` | — |
| GitHub Actions | ۱۱ action (با CodeQL)، ۸۹ بارِ `uses:` در ۱۵ ورک‌فلو (دو تا تازه) | همه با SHAِ کامل + `# vX.Y.Z` (جز `tohid-native-preview.yml`) | `workflow-guard` سد می‌کند |
| Docker | `server/Dockerfile` (`node:22-alpine`)، `docker-compose.yml` (`postgres:16-alpine`، `caddy:2-alpine`) | برچسب، نه digest | نه |
| ابزارِ بیرونیِ CI | Inno Setup با `choco` (بی نسخه)، `apt` (xvfb، قلم)، کلونِ `pump-staion-yaqobi` سرِ HEAD (آزمونِ بات) | نه | کارهای خواندنی، بی secret |

secretها و varهای ورک‌فلوها (فقط نام): `TOHID_KEYSTORE_B64`، `TOHID_KEYSTORE_PASSWORD`، `TOHID_KEY_ALIAS`، `TOHID_KEY_PASSWORD`،
`TOHID_UPDATE_SIGNING_KEY`، `ANDROID_KEYSTORE_*` (ورک‌فلوی بازنشسته)، `GITHUB_TOKEN`؛ varها (راز نیستند): `TOHID_API_BASE`،
`TOHID_UPDATE_PUBLIC_KEY`، `TOHID_LICENSE_PUBLIC_KEY`، `SHOP_SERVER_URL`.

## ۲. یافته‌ها

وضعیت‌ها همان قاعدهٔ `SECURITY-PROGRAM-fa.md` §۴. «guard» یعنی `tools/security/workflow-guard.test.mjs`.

| شناسه | شدت | مسیرِ واقعی | ریشه | اصلاح / PR | آزمون | وضعیت | خطرِ باقی‌مانده |
|---|---|---|---|---|---|---|---|
| SEC-SUP-01 | Critical | `server/package-lock.json` | proxy-addr 2.0.7 (GHSA-jqcg-44mw-7w3h، جعلِ IP)، body-parser 2.3.0 (high، GHSA-27r3-jxmh-p828، DoS)، qs 6.16.0 (moderate) — غیرمستقیم زیرِ express 5 | فقط قفل: 2.0.8 / 2.4.0 / 6.17.1 در همان بازهٔ semver — #578 | `npm-audit-gate` روی قفلِ main سرخ (۱ critical) و روی #578 سبز (۰)؛ ۱۳ فایلِ آزمونِ سرور روی PGlite ۱۶۲/۱۶۵ (سه شکستِ `backup.test.js` روی mainِ دست‌نخورده هم هست: PostgreSQLِ واقعی می‌خواهد) | FIXED-PENDING-MERGE | با `TRUST_PROXY` سرور `trust proxy = true` می‌گذارد (همهٔ پراکسی‌ها، نه زیرشبکه)؛ راهِ مشخصِ این CVE کم‌اثر بود ولی بسته شد |
| SEC-SUP-02 | High | `tohid-native.yml`، `tohid-admin.yml`، `desktop-shots.yml`، `tohid-desktop.yml` | `permissions: contents: write` در بالای فایل ⇒ کارِ ساخت (کدِ PR، Gradle و همهٔ وابستگی‌هایش) توکنِ نوشتنی داشت و checkout آن را در `.git/config` می‌گذاشت. یک وابستگیِ آلوده می‌توانست برچسبِ پایدار را جابه‌جا یا نصبیِ انتشارِ همهٔ دکان‌ها را عوض کند | بالای همهٔ فایل‌ها `contents: read`؛ کارِ تازهٔ `publish` (نوشتنی) فقط artifact را می‌گیرد و هیچ کدِ ساختی اجرا نمی‌کند؛ کامپیوتر از پیش جدا بود و فقط مجوزش به همان کار رفت | guard › «perms» و «همهٔ ورک‌فلوهای مخزن…» (سرخ روی main، سبز پس از اصلاح)؛ actionlint بی خطای تازه | FIXED-PENDING-MERGE | کارِ انتشار فقط پس از مرج روی main واقعاً می‌دود (اولین انتشارِ آزمایشی پس از مرج را ببینید) |
| SEC-SUP-03 | High | `tohid-native.yml` (گامِ «نام‌گذاری و جمع کنترلی») | کلیدِ خصوصیِ امضای فهرستِ به‌روزرسانی (`TOHID_UPDATE_SIGNING_KEY`) در کارِ ساخت و در **هر PR از همین مخزن**: APKِ بازبینی‌نشده با فهرستِ امضاشده با کلیدِ واقعی | امضای `SHA256SUMS.txt` به کارِ `publish` (فقط main) رفت؛ کارِ ساخت فقط «بودنِ» secret را می‌سنجد (`secrets.X != ''`) | guard (مجوز) + بازبینیِ ایستا | FIXED-PENDING-MERGE | artifactِ PR دیگر `.sig` ندارد (برای نصبِ دستی لازم نبود) |
| SEC-SUP-04 | High (رخداد) | `android-native/app/tohid-release.jks` و `android-native/app/build.gradle.kts` (حدودِ خطِ ۱۷۳) | کلیدِ امضای APK در مخزنِ **عمومی**، با گذرواژهٔ پیش‌فرضِ نوشته‌شده در فایلِ ساخت؛ و secretهای keystore به ساختِ هر PR داده می‌شوند | این‌جا اصلاح نشد: کارِ صاحب محصول و `claude/sec-artifacts` (SEC-ART-05، STATUS: SEC-08/F0-09). اسکنِ secret آن را تا ۱۴۰۵/۱۰/۰۱ استثنا دارد و هر keystoreِ تازه را سد می‌کند | `secret-scan --all` همین یک فایل را می‌یابد | FAIL (باز) | تا چرخاندن: هر کس مخزن را دارد می‌تواند APK با همان امضا بسازد — اگر انتشارها با همین فایل امضا شده باشند. بخشِ ۵ |
| SEC-SUP-05 | Medium | همهٔ ورک‌فلوها | `uses: …@v4`: برچسبِ جابه‌جاشدنی؛ کدِ تازهٔ action با همان توکن و secretها اجرا می‌شد | سنجاق به SHAی که همان برچسب امروز نشان می‌دهد (رفتار عوض نمی‌شود)، با `# vX.Y.Z`؛ همهٔ SHAها با `git fetch` «commit» بودنشان سنجیده شد | guard › «pin» | FIXED-PENDING-MERGE | به‌روزرسانیِ سنجاق‌ها دستی است (بخشِ ۶) |
| SEC-SUP-06 | Medium | `android-release.yml` (`github.event.inputs.version`)، `tohid-native.yml`/`tohid-desktop.yml` (`inputs.channel`، `github.ref`) | `${{ }}` ِ ورودی مستقیم داخلِ `run:` ⇒ تزریقِ فرمان (فقط با دسترسیِ نوشتن؛ `channel` از نوعِ choice بود) | همه از `env:`؛ نسخهٔ android-release فقط `^[0-9]+(\.[0-9]+){1,3}$` | guard › «inject» | FIXED-PENDING-MERGE | — |
| SEC-SUP-07 | Medium | `android-release.yml` (بازنشسته) | Run workflow از **هر شاخه‌ای** انتشارِ `v*` (غیرِ prerelease ⇒ «Latest» ِ مخزن) می‌ساخت | کار فقط از main؛ `contents: write` فقط در همان کار | guard + بازبینی | FIXED-PENDING-MERGE | اگر keystore تنظیم نباشد هنوز با امضای debug می‌سازد (بازنشسته، فقط دستی) |
| SEC-SUP-08 | Medium | ۷ جا در `server.yml`، `perf.yml`، `quality.yml` + `server/Dockerfile` | `npm ci \|\| npm install`: قفلِ ناهم‌خوان بی صدا دور زده و نسخهٔ تازهٔ سنجیده‌نشده نصب می‌شد | فقط `npm ci` (Dockerfile در #578، CI در همین PR) | CIِ «سرور» روی همین PR | FIXED-PENDING-MERGE | PRی که `package.json` را بی قفل عوض کند حالا سرخ می‌شود (عمداً) |
| SEC-SUP-09 | Medium | — | هیچ اسکنِ secret، audit، SAST یا SBOM در CI نبود | `security.yml` (هر PR و push: secret، guard، npm audit سد؛ actionlint گزارش؛ SBOM) و `codeql.yml` (گزارش) | `node --test "tools/security/*.test.mjs"` ۲۰/۲۰ | FIXED-PENDING-MERGE | CodeQL فقط JS و Actions؛ Kotlin طرح است (بخشِ ۴) |
| SEC-SUP-10 | Low | `*/gradle/wrapper/gradle-wrapper.properties` | توزیعِ Gradle بی سنجشِ hash دانلود می‌شد | `distributionSha256Sum` از صفحهٔ رسمیِ gradle.org/release-checksums (8.14.3 و 8.11.1) | jarها با فهرستِ رسمی سنجیده شدند؛ دانلودِ تازه محلی ممکن نبود (downloads.gradle.org بسته) | FIXED-PENDING-MERGE (دانلودِ تازه NOT-TESTED تا CI) | — |
| SEC-SUP-11 | Low | `perf.yml`، `android.yml`، `desktop-version` | artifact بی `retention-days` (پیش‌فرض ۹۰ روز) | ۳۰/۱۴/۳۰ روز | guard ندارد؛ بازبینی | FIXED-PENDING-MERGE | — |
| SEC-SUP-12 | Medium | همهٔ ساخت‌های Gradle | بی dependency verification و بی قفل؛ `android-native`/`admin-android` در CI با `gradle` ِ نصب‌شده روی runner (نسخه با تصویرِ runner عوض می‌شود) | طرح (بخشِ ۴) — ساختِ اندروید این‌جا ممکن نیست (dl.google.com بسته) و حالتِ سخت هر PRِ وابستگیِ تازه را می‌شکند | — | NOT-TESTED | وابستگیِ آلوده در Maven Central/Google تا آن روز دیده نمی‌شود |
| SEC-SUP-13 | Low | `server.yml`، `tohid-desktop.yml`، `Dockerfile`/compose | `electron@38` (فقط نسخهٔ اصلی)، Inno Setup با choco بی نسخه، کلونِ ریپوی پمپ سرِ HEAD، imageها با برچسب | طرح | — | NOT-TESTED | فقط کارهای خواندنی و بی secret؛ image با برچسب وصلهٔ امنیتی را خودکار می‌گیرد (عمدی) |
| SEC-SUP-14 | Info | همهٔ ورک‌فلوها | `pull_request_target`/`workflow_run` نیست؛ هیچ secretی echo نمی‌شود (keystore با `base64 -d` به فایلِ `RUNNER_TEMP`، pem پس از امضا پاک) | guard هر دو را سد می‌کند | guard › «trigger» | PASS (ایستا) | — |
| SEC-SUP-15 | — | `tohid-native-preview.yml` | سنجاق‌نشده و `contents: write` در بالای فایل | به دستورِ صاحب محصول دست نخورد؛ guard فقط هشدار می‌دهد | — | NOT-APPLICABLE (مستثنا) | همان خطرِ SEC-SUP-02/05 برای برچسبِ پیش‌نمایش |
| SEC-SUP-16 | — | `tohid-native.yml`، `tohid-desktop.yml`، `web-pages.yml`، `tohid-admin.yml` | جدایی آزمایشی/پایدار: push به main ⇐ `*-beta`؛ پایدار فقط Run workflow ⇐ main ⇐ `channel = stable`؛ آزمایشی هیچ `.apk`ی ندارد؛ مدیریت فقط از main (F0-12) | در جداسازیِ کارها همان شرط‌ها ماند (`publish` فقط `main` و نه PR؛ کانال از خروجیِ کارِ ساخت) | بازبینیِ ایستا + actionlint | PASS (ایستا) | — |
| SEC-SUP-17 | Low | `desktop/settings.gradle.kts`، `android-native`/`admin-android`/`core` | مخزنِ `TOHID_OFFLINE_M2` (اختیاری) اول می‌آید؛ مخزن‌ها فیلترِ گروه ندارند (جز `android/`) | طرح: `exclusiveContent`/`includeGroupByRegex` | — | NOT-TESTED | فقط ساختِ محلیِ آفلاین |
| SEC-SUP-18 | — | `web-pages.yml` | Pages کلِ `git archive` (سورسِ سرور و سندها) را منتشر می‌کند؛ مخزن عمومی است | مالِ `claude/sec-artifacts` (SEC-ART-03) | — | NOT-APPLICABLE (دامنهٔ دیگر) | — |

## ۳. آن‌چه حالا سد است (و چه فقط گزارش)

کارِ «اسکنِ secret، وابستگی و ورک‌فلو» در `security.yml` روی **هر PR** و هر push به main، حدودِ یک دقیقه، بی نصبِ وابستگی:

| سنجه | ابزار | رفتار |
|---|---|---|
| secret | `tools/security/secret-scan.mjs` — PR: فقط خط‌های افزوده (`HEAD^1..HEAD`)؛ main و هفتگی: همهٔ فایل‌ها | **سد** برای کلیدِ خصوصی (PEM)، AWS، GitHub، Slack، Google، Stripe live، Anthropic، OpenAI، npm، توکنِ بات تلگرام، SendGrid و فایلِ keystore/کلید/`.env`؛ هشدار برای `user:pass@` و «password = "…"». هرگز مقدار چاپ نمی‌شود (فقط ۸ نویسهٔ sha256). استثنا: `tools/security/secret-scan-allow.json` با دلیل و تاریخ |
| ورک‌فلوها | `tools/security/workflow-guard.mjs` | **سد**: actionِ سنجاق‌نشده، `permissions` نبودن یا `write` در بالای فایل، `write` در کاری بیرون از `WRITE_JOBS`، `pull_request_target`/`workflow_run`، ورودیِ نامطمئن در `run:`/`script:` |
| npm | `tools/security/npm-audit-gate.mjs` روی `server/` (`--omit=dev`) | **سد**: critical. high: هشدار تا **۱۴۰۵/۱۰/۰۱ (2026-12-22)** و از آن روز خودکار سد. قطعیِ رجیستری: سه تلاش، بعد هشدار و عبور (NOT-TESTED آن اجرا). استثنا فقط GHSA + دلیل + تاریخ در `audit-allow.json` |
| actionlint 1.7.7 | دانلود با sha256ِ سنجیده | گزارش (۱۳ یادداشتِ shellcheck از پیش، همه info) |
| SBOM | `npm sbom` (CycloneDX 1.5 و SPDX 2.3) + فهرستِ actionها | artifactِ `sbom` (۳۰ روز) |
| CodeQL | `codeql.yml`: JS/TS و Actions، `security-extended`، PRهایی که سرور/وب/license/ورک‌فلو را عوض می‌کنند + main + هفتگی | گزارش (Security ⇒ Code scanning)؛ PR را قرمز نمی‌کند |

قاعده‌ها در `docs/RULES-fa.md` بخشِ «v3 · CI و آزمون» هم آمد تا عامل‌های بعدی برنگردانند.

## ۴. طرح‌های تاریخ‌دار

1. **۱۴۰۵/۰۸/۲۴ (2026-11-15)** — نخستین نتیجه‌های CodeQL دسته‌بندی شوند (درست/کاذب)؛ هشدارهای High/Critical به PR.
2. **۱۴۰۵/۰۹/۱۵ (2026-12-06)** — ۱۳ یادداشتِ shellcheck پاک و actionlint سد شود؛ اگر صفِ Actions عادی شد، چکِ «اسکنِ secret، وابستگی و ورک‌فلو» در قاعدهٔ شاخهٔ main «required» شود.
3. **۱۴۰۵/۱۰/۰۱ (2026-12-22)** — npm high خودکار سد؛ استثنای `tohid-release.jks` منقضی ⇒ اسکنِ کاملِ main سرخ تا آن فایل برداشته شود (SEC-SUP-04).
4. **Gradle (SEC-SUP-12)**، گام‌به‌گام چون اندروید فقط در CI ساخته می‌شود:
   الف) ورک‌فلوی دستی که با `--write-verification-metadata sha256` برای `desktop` (همهٔ کدِ مشترکِ اندروید + core) و `android-native` فایلِ `gradle/verification-metadata.xml` بسازد و artifact کند؛
   ب) commit با `org.gradle.dependency.verification=lenient` (فقط گزارش) یک هفته؛
   ج) سخت (`strict`) با قاعدهٔ «وابستگیِ تازه ⇒ همان PR فایل را به‌روز کند»؛
   د) `gradle-version: 8.11.1` برای `setup-gradle` در دو ورک‌فلوی اندروید تا نسخهٔ Gradle با تصویرِ runner عوض نشود — فقط با یک اجرای سبزِ CI.
   SBOMِ Gradle (پلاگینِ CycloneDX) پس از «الف».
5. **CodeQL برای Kotlin** — `java-kotlin` با `build-mode: manual` و `./gradlew --no-build-cache compileKotlin` در `desktop/`، فقط هفتگی؛ پس از اطمینان از پشتیبانیِ CodeQL از Kotlin 2.2.20.
6. **کلیدِ امضای APK (SEC-SUP-04)** — پس از تصمیمِ F0-09: امضا از کارِ ساخت به کارِ `publish` (`apksigner` بی Gradle؛ اثرِ انگشتِ گواهی برای `SIGNING_SHA256` از `vars`)، PRها با کلیدِ آزمایشیِ جدا و دروازهٔ اثرِ انگشت فقط روی main.

## ۵. کارهای صاحب محصول (در تنظیماتِ GitHub؛ از کد شدنی نیست)

1. **Settings ⇒ Code security**: Secret scanning و **Push protection** روشن (مخزنِ عمومی، رایگان)؛ **Dependabot alerts** و **Dependabot security updates** روشن. (هشدارِ Dependabot برای actionِ سنجاق‌شده با SHA کامل نیست؛ سنجاق‌ها با بخشِ ۶ تازه می‌شوند.)
2. **CodeQL**: «Default setup» را روشن **نکنید** (با `codeql.yml` ناسازگار است و بارگذاریِ آن رد می‌شود). اگر Default setup را ترجیح می‌دهید، `codeql.yml` را بردارید.
3. **Settings ⇒ Actions ⇒ General**: «Workflow permissions» = Read repository contents؛ «Allow GitHub Actions to create and approve pull requests» خاموش. سیاستِ «سنجاقِ اجباریِ SHA» فقط پس از سنجاقِ `tohid-native-preview.yml`.
4. **نگه‌داریِ کلیدهای امضا**: یک Environment به نامِ `release` با «Deployment branches = main» (و اگر خواستید تأییدِ خودتان برای پایدار)؛ `TOHID_UPDATE_SIGNING_KEY` و `TOHID_KEYSTORE_*` به آن بروند و کارِ `publish` `environment: release` بگیرد (یک خط، پس از ساختنِ Environment). تا آن روز secretها برای هر PR از شاخه‌های همین مخزن (انسان یا عامل) در دسترس‌اند.
5. **رخدادِ SEC-SUP-04**: ببینید secretِ `TOHID_KEYSTORE_B64` تنظیم هست یا نه (در اجرای «توحید بومی»، گامِ «کلیدِ امضا» اگر نباشد هشدارِ «تنظیم نشده» می‌دهد). اگر نیست، انتشارها با فایلِ داخلِ مخزن امضا شده‌اند و کلید عملاً عمومی است. عوض کردنِ کلیدِ اندروید بی «key rotation» ِ طرحِ امضای v3 (`apksigner rotate`) به‌روزرسانیِ برنامه‌های نصب‌شده را می‌شکند — تصمیمِ F0-09، با برنامهٔ بازگشت.
6. اگر روزی قاعدهٔ شاخه گذاشتید: چکِ «اسکنِ secret، وابستگی و ورک‌فلو» required (بخشِ ۴، گامِ ۲).

## ۶. نگه‌داری

- **تازه کردنِ سنجاق‌ها** (پیشنهاد: هر سه ماه، بعدی ۱۴۰۵/۱۱/۰۱): برای هر action، SHAی که برچسبِ اصلی نشان می‌دهد:
  `git ls-remote --tags https://github.com/<owner>/<repo> 'refs/tags/v4' 'refs/tags/v4^{}'` (اگر `^{}` هست همان commit است)، و نسخهٔ
  دقیقِ هم‌SHA برای یادداشتِ `# vX.Y.Z`. نسخهٔ کم‌تر از دو هفته عمر را برندارید. پس از تغییر: `node tools/security/workflow-guard.mjs`.
- **وابستگیِ npm**: `cd server && npm audit fix --omit=dev --package-lock-only` و PRِ تنها-قفل؛ `package.json` فقط اگر لازم.
- **مثبتِ کاذب** در اسکنِ secret: سطرِ استثنا با `path`، `rule`، `fp` (همان ۸ نویسه‌ای که اسکن چاپ کرد)، دلیل و ترجیحاً `until`. رازِ واقعی را استثنا نکنید: برداشتن کافی نیست، **چرخاندن** لازم است.
- آزمونِ ابزارها: `node --test "tools/security/*.test.mjs"` (در CI هم پیش از اسکن‌ها).
