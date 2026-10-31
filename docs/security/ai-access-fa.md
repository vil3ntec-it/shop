# دسترسیِ عامل‌های هوشِ مصنوعی به پروژه — SEC-AI-01..05 (برنامهٔ امنیت §۳)

> شاخهٔ `claude/sec-ai-access` · ۱۴۰۵/۰۸/۰۹ · Claude Code 2.1.314
> برنامهٔ مادر: `docs/SECURITY-PROGRAM-fa.md` (شاخهٔ `claude/security-program`). جدولِ وضعیتِ آن‌جا را سرپرست به‌روز می‌کند.
> قاعده: هیچ مقدارِ secretی در این سند، در لاگ یا در خروجیِ ابزارها نیامده — فقط نوع، جا و شیوهٔ نگهداری.

## ۰. خلاصهٔ وضعیت

| شناسه | شدت | وضعیت | یک خط |
|---|---|---|---|
| SEC-AI-01 | متوسط | `BLOCKED` — **دسترسیِ محدودشده اثبات نشده** | هر عامل کلِ مخزن (با `server/`) را دارد؛ جداسازیِ واقعی تصمیمِ صاحب محصول است (§۴). پایه‌اش ساخته شد: قراردادِ API بی سورسِ بک‌اند (§۵، #583). |
| SEC-AI-02a | بالا | `FIXED-PENDING-MERGE` | قاعده‌های `deny` ِ اجرایی در `.claude/settings.json` برای فایل‌های secret؛ شاهد: آزمونِ واقعی با خودِ Claude Code (§۲.۳) و سدِ CI. |
| SEC-AI-02b | متوسط | `BLOCKED` — **دسترسیِ محدودشده اثبات نشده** | `grep -r`، `node`/`python`، `keytool`، Gradle و هر زیرفرایندی که خودش فایل باز کند را قاعدهٔ deny نمی‌گیرد؛ مرزِ سیستم‌عامل (sandbox) در محیطِ ابری نیست. |
| SEC-AI-03 | بالا | `FAIL` | کلیدِ امضای اندروید (`android-native/app/tohid-release.jks`) در هر workspace و در مخزنِ **عمومی** است (F0-09، کارِ صاحب محصول). جز آن: نه `.env` ِ production، نه کلید، نه dump در workspace. |
| SEC-AI-04 | متوسط | `NOT-TESTED` (مالِ `claude/sec-artifacts`) | یافتهٔ هم‌پوشان: GitHub Pages **کلِ مخزن** را منتشر می‌کند (§۶). |
| SEC-AI-05a | متوسط | `FIXED-PENDING-MERGE` | ریختنِ کلِ env (`env`، `printenv`، `export -p`) و `create-admin` در ترنسکریپتِ عامل بسته شد؛ شاهد: همان آزمونِ واقعی. |
| SEC-AI-05b | پایین | `NOT-TESTED` | بازبینیِ ایستای اسکریپت‌ها و ورک‌فلوها: هیچ گامی secret یا env چاپ نمی‌کند (§۳)؛ آزمونِ خودکارِ لاگِ CI مالِ `claude/sec-supply` است. |

## ۱. واقعیت‌ها — دسترسیِ امروزِ عامل‌ها (گامِ ۱؛ همه سنجیده، نه حدس)

**مخزن.** `vil3ntec-it/shop` **عمومی** است (`gh api repos/vil3ntec-it/shop` ⇒ `private: false`). یک monorepo با ۱۷۴۹ فایلِ ردیاب‌شده:
`server` ۶۰۰، `android-native` ۵۵۴، `docs` ۱۱۵، `android` ۱۰۲، `desktop` ۹۷، `core` ۸۶، `tools` ۵۴، `admin-android` ۴۶، `license` ۴۴، و پوستهٔ وب در ریشه.
پس سورسِ بک‌اند همین امروز و در کلِ تاریخچهٔ Git برای همه خواناست؛ امنیت نباید به پنهان ماندنِ کد تکیه کند (قاعدهٔ ۳ و ۴ِ برنامه).

**سیزنِ ابری.** مخزن کامل clone می‌شود (بی sparse checkout)؛ کارگاه‌های `git worktree` در `/home/user/wt/*` همه همان درختِ کامل‌اند.
عامل shell، فایل‌سیستم، شبکه از راهِ proxy ِ خروجی (`/root/.ccr/README.md`)، ابزارهای GitHub (MCP و `gh`) و push دارد.
متغیرهای محیطیِ `GH_TOKEN`، `GITHUB_TOKEN`، `AWS_*` و `CLOUDSDK_AUTH_ACCESS_TOKEN` تنظیم‌اند ولی **قالبِ توکنِ واقعی ندارند** (۱۰ تا ۱۹ نویسه؛
فقط با الگو سنجیده شد، مقدار خوانده نشد) ⇒ جای‌نگهدار؛ اعتبارِ واقعی را proxy می‌گذارد (استنتاج). `~/.claude/.credentials.json` هست؛ `~/.ssh` خالی؛ `~/.aws` فقط `config`.

**فایل‌های secret‌مانند در مخزن و workspace** (`git ls-files` + ردیاب‌نشده + ignoreشده؛ فقط نام):

| مسیر | نوع | حکم |
|---|---|---|
| `android-native/app/tohid-release.jks` | Java keystore ِ امضای انتشار؛ `admin-android` هم همان را برمی‌دارد (`build.gradle.kts:34`) | ⛔ **رخداد** — F0-09 در `docs/spec/OWNER-ACTIONS-fa.md`؛ `android-native/.gitignore` می‌گوید `*.jks` هرگز نباید در مخزن باشد |
| `server/.env.example` | الگوی env، مقدارها خالی | بی‌خطر؛ عامل‌ها باید بخوانند و ویرایش کنند |
| `server/test/fixtures/server-secret-golden.json` | بردارِ آزمون؛ سه مقدارِ `secretValues` نشانِ ساختگی دارند (با الگو سنجیده شد) | بی‌خطر |
| `.env`، `*.pem`، `*.key`، dump، `*.db`، `server/data/` | — | **نیست** (نه ردیاب‌شده، نه محلی) |

جست‌وجوی متنی در فایل‌های ردیاب‌شده (فقط نامِ فایل): هیچ بلوکِ `BEGIN … PRIVATE KEY` و هیچ رشته با قالبِ توکنِ GitHub/AWS/Slack/OpenAI/Google/باتِ تلگرام نیست.
secretهای production جای دیگرند: envِ سرور و `secrets.json` ِ پنلِ خانگی (ریپوی `server`)، و secretهای GitHub Actions
(`TOHID_KEYSTORE_B64`، `TOHID_KEYSTORE_PASSWORD`، `TOHID_KEY_ALIAS`، `TOHID_KEY_PASSWORD`، `TOHID_UPDATE_SIGNING_KEY`، `ANDROID_KEYSTORE_*`).

**لاگ و ترنسکریپت.** هر چه عامل بخواند یا چاپ کند در ترنسکریپتِ سیزن می‌ماند (`~/.claude/projects/**.jsonl` و تاریخچهٔ سیزن در claude.ai).
پس «خواندنِ secret» یعنی «secret در لاگ». هیچ پاک‌سازیِ خودکاری روی ترنسکریپت نیست.

## ۲. محدودیتِ اجرایی (گامِ ۲، SEC-AI-02)

### ۲.۱ چه اضافه شد — `.claude/settings.json` ⇒ `permissions.deny`

هوکِ `SessionStart` ِ اسکیل‌ها دست نخورده (آزمون می‌سنجد). برای **هر** الگوی زیر هم `Read(...)` و هم `Edit(...)`:

- `**/.env`، `**/.env.*` و پس از آن استثنای `!**/.env.example`
- کلید: `**/*.pem`، `*.key`، `*.jks`، `*.keystore`، `*.p12`، `*.pfx`، `*.p8`، `id_rsa*`، `id_ecdsa*`، `id_ed25519*`
- `**/secrets/**`، `**/secrets.json` (همان نامِ فایلِ پنلِ خانگی)
- پشتیبان و داده: `/server/data/**` (پوشهٔ پیش‌فرضِ `BACKUP_PATH`)، `*.sql.gz(.enc)`، `*.pglite.tar.gz(.enc)` (نامِ واقعیِ پشتیبان در `server/src/lib/backup.js`)، `*.dump`، `*.pgdump`، `*.db`، `*.sqlite(3)`
- خانهٔ کاربر: `~/.ssh/**`، `~/.aws/**`، `~/.netrc`، `~/.git-credentials`، `~/.config/gh/**`، `~/.claude/.credentials.json`

و Bash: `env`، `printenv`، `export -p` (فقط شکلِ بی‌آرگومان — `printenv HOME` باز است)، و سه شکلِ اجرای `create-admin.js` (§۳).

نحو و معنا از مستنداتِ رسمیِ Claude Code (`code.claude.com/docs/en/permissions`) سنجیده شد: deny پیش از ask و allow ارزیابی می‌شود و
allow نمی‌تواند از آن استثنا بسازد؛ `Read` ِ deny نوشتن و ساختنِ همان مسیر با Edit/Write را هم می‌بندد؛ `Write(path)` و `Glob(path)` هرگز
تطبیق نمی‌خورند (پس به‌کار نرفتند و آزمون جلوی آمدنشان را می‌گیرد)؛ استثنای `!` فقط قاعده‌های **پیش از خودش** در همان فایل را می‌شکافد.

### ۲.۲ سدِ CI — تا قاعده بی‌صدا ضعیف نشود

`tools/ai-access/check-settings.mjs` (و آزمونش) در گامِ «قاعده‌های دسترسیِ عامل‌ها به فایل‌های secret (سد)» ِ `quality.yml` روی **هر** PR:
JSON درست، هوک سرِ جا، هر `Read`/`Edit` ِ لازم، استثنا فقط `.env.example` و پس از `.env.*`، هیچ قاعدهٔ بی‌اثر، و — مهم‌تر — هر فایلِ
secret‌مانندِ واقعیِ workspace (امروز: `tohid-release.jks`) با همین قاعده‌ها بسته و هر فایلِ لازم (کدِ پشتیبان، fixture، `.env.example`) باز.
فقط نامِ مسیر سنجیده می‌شود؛ هیچ محتوایی خوانده نمی‌شود. اگر روزی fixture ِ آزمون با کلیدِ **ساختگی** لازم شد، زیرِ مسیرِ روشنی مثلِ
`server/test/fixtures/fake-keys/` بگذارید و استثنایش را هم در `settings.json` و هم در `CARVE_OUTS` ِ سنجه، در یک PR ِ بازبینی‌شده، بیفزایید.

### ۲.۳ شاهدِ اجرای واقعی (نه گفتهٔ مدل)

`node tools/ai-access/probe-enforcement.mjs` در پوشهٔ موقت با فایل‌های **ساختگی** و همین قاعده‌ها، `claude -p` (Claude Code 2.1.314، مدلِ haiku)
را اجرا می‌کند و حکم را از نتیجهٔ ابزار در رویدادهای خودِ harness می‌خواند. اجرای ۱۴۰۵/۰۸/۰۹:

| ابزار | هدف | حکم |
|---|---|---|
| Read | `.env`، `keys/probe.pem`، `secrets/probe.txt`، `android-native/app/probe.jks` | BLOCKED ✓ |
| Read | `server/.env.example`، `README.txt` | RAN ✓ |
| Bash | `cat .env`، `cat keys/probe.pem`، `env`، `printenv`، `export -p`، `node server/scripts/create-admin.js probe-user` | BLOCKED ✓ |
| Bash | `printenv HOME` | RAN ✓ |
| Write / Edit | `.env.production` / `server/.env.example` | BLOCKED ✓ / RAN ✓ |
| Bash | `grep -rl SYNTHETIC_PROBE .`، `node -e "…readFileSync('.env')"` | **RAN — شکافِ شناخته (SEC-AI-02b)** |

### ۲.۴ آنچه اثبات **نشده** (SEC-AI-02b) — «دسترسیِ محدودشده اثبات نشده»

- قاعدهٔ deny فقط ابزارهای خودِ Claude و فرمان‌های فایلیِ شناخته‌شدهٔ Bash (`cat`، `head`، `tail`، `sed`، `tee`، تغییرِ مسیرِ `<`/`>`) را می‌گیرد.
  `grep -r` بی نامِ فایل، `node`/`python`، `keytool`، `gradlew` و هر زیرفرایند آزاد است (شاهد بالا). `echo "$VAR"` هم.
- مرزِ سیستم‌عامل (`sandbox.enabled`) روشن **نشد**: در کانتینرِ ابری `bwrap` نیست ⇒ sandbox بی‌صدا بی‌اثر می‌شود؛ و sandbox شبکه را به‌طورِ
  پیش‌فرض می‌بندد (فهرستِ دامنهٔ مجاز خالی) ⇒ git/npm/gh از راهِ proxy می‌شکست. برای کامپیوترِ خودِ صاحب محصول پیشنهاد است (§۷).
- قاعده‌ها فقط وقتی بار می‌شوند که پروژهٔ سیزن همین مخزن باشد. ابزارهای GitHub (MCP، `gh api`) هر فایلِ ردیاب‌شدهٔ مخزنِ عمومی را می‌خوانند —
  یعنی `tohid-release.jks` برای هر کس، با یا بی این قاعده‌ها، گرفتنی است (پس SEC-AI-03 `FAIL` می‌ماند).
- `.claude/` مسیرِ محافظت‌شده است (نوشتنش تأیید می‌خواهد) و سدِ CI جلوی مرجِ نسخهٔ ضعیف‌شده را می‌گیرد؛ ولی کسی که در حالتِ
  `bypassPermissions` یا با `--settings` ِ دیگر کار کند، این را جدا نسنجیده‌ایم.

## ۳. لاگ، ترنسکریپت و گزارش (SEC-AI-05)

| یافته | شدت | مسیر | حکم |
|---|---|---|---|
| ریختنِ کلِ env در ترنسکریپتِ عامل | متوسط | Bash | بسته (`env`، `printenv`، `export -p`) — 05a |
| `create-admin.js` رمزِ ساخته‌شده را چاپ می‌کند وقتی رمز داده نشود | پایین | `server/scripts/create-admin.js:44` | عمداً برای اپراتور (RULES، «سرور: پشتیبانِ بی‌رمز و ورودِ دومرحله‌ایِ مدیر»)؛ رفتار عوض نشد. اجرا به‌دستِ عامل بسته شد (سه شکلِ رایج؛ شکل‌های دیگر را نمی‌گیرد) — 05a |
| `send-test-sms.js` | اطلاع | `server/scripts/send-test-sms.js:29-35` | کلید را فقط با طول نشان می‌دهد؛ آدرس را **الگو** (`{key}`) چاپ می‌کند — اگر اپراتور کلید را خامِ درونِ `SMS_API_URL` بگذارد چاپ می‌شود. با `OTP_PROVIDER=log` کد را چاپ می‌کند (ابزارِ آزمون) |
| لاگِ درخواستِ سرور | — | `server/src/lib/log-redact.js` | توکنِ مسیر `:tok` می‌شود و querystring هرگز؛ کدِ ورود از لاگِ production در #493 رفت (حوزهٔ `sec-api-web`) |
| ورک‌فلوهای CI | — | `.github/workflows/*.yml` | secretها فقط از env به فایلِ `$RUNNER_TEMP` می‌روند (`base64 -d >`، `printf >`)؛ نه `set -x`، نه `printenv`، نه چاپِ env؛ `GITHUB_TOKEN` ِ `desktop-shots.yml` درونِ URL ِ git است و Actions پنهانش می‌کند. `android-release.yml` کلید را در `android/app/release.keystore` ِ کارگاه باز می‌کند ولی فقط `out/*.apk` بالا می‌رود — 05b |
| ابزارهای این شاخه | — | `tools/ai-access/*`، `tools/api-contract/*` | فقط نامِ مسیر، دستور و حکم چاپ می‌کنند؛ مولدِ قرارداد secretِ ساختگی و envِ پاک دارد |

خطرِ باقی‌مانده: عامل هنوز می‌تواند secretی را که در دسترسِ زیرفرایند است چاپ کند (§۲.۴)؛ ترنسکریپت پاک‌سازیِ خودکار ندارد. قاعدهٔ رفتاری همان
قاعدهٔ ۵ِ برنامه است؛ مرزِ واقعی‌اش این است که secret اصلاً در workspace نباشد (SEC-AI-03).

## ۴. درخواستِ تصمیم — جداسازیِ واقعی برای «عاملِ فرانت‌اند» (گامِ ۳؛ اجرا نشده)

### ۴.۱ گره‌های واقعیِ امروز میانِ فرانت‌اند و بک‌اند

1. **هستهٔ مشترکِ JS:** شش فایل بایت‌به‌بایت یکی‌اند میانِ `license/` (وب) و `server/src/lib/` — `approval-core`، `calendar-core`، `perm-core`، `promo-core`، `stock-core`، `text-core` (آزمون‌های سرور برابری را می‌سنجند).
2. **آزمون‌های سرور به کدِ فرانت‌اند تکیه دارند:** دست‌کم ۲۶ فایلِ `server/test/*.test.js` کد یا بردارِ `license/` را بار می‌کنند و ۱۵ از ۲۰ آزمونِ مرورگرِ `server/test/*.mjs` به `index.html`/`license/` وابسته‌اند.
3. **بردارهای آزمون:** `core/test-vectors/` (۱۸) و `license/test-vectors/` (۴) را Kotlin، وب و سرور با هم می‌خوانند؛ چند بردار را `server/scripts/*-vectors.js` می‌سازد.
4. **فرانت‌اندهای درونِ سرور:** `server/public/` (پنلِ مدیر، ویترین، پورتال، SDK، kit، رسید، بازار).
5. **فیلترِ مسیرِ CI:** `server.yml` با `index.html`، `sw.js`، `license/**`، `core/test-vectors/**` و `OwnerExport.kt` روشن می‌شود؛ `tohid-native`/`tohid-desktop` با `license/test-vectors/**`.
6. **GitHub Pages** آرشیوِ کلِ مخزن را منتشر می‌کند (`web-pages.yml:126-131`).
7. **مخزن عمومی است** ⇒ هر جداسازی در *همین* مخزن فقط کمینه‌دسترسی است، نه محرمانگی.

### ۴.۲ گزینه‌ها

| | (الف) بک‌اند در مخزنِ خصوصیِ جدا + قراردادِ منتشرشده | (ب) workspaceِ جدا برای هر نوع عامل + مرزِ سیستم‌عامل | (پ) همین monorepo؛ عاملِ فرانت‌اند با sparse checkout و profile ِ deny |
|---|---|---|---|
| محرمانگیِ سورسِ بک‌اند | **فقط برای کدِ پس از جدایی**؛ تاریخچهٔ امروز عمومی می‌ماند | ندارد (`git show HEAD:server/…` و GitHub MCP) | ندارد (همان) |
| کمینه‌دسترسیِ عامل | کامل (مخزن را ندارد) | کامل اگر sandbox باشد | در ابزارهای Claude؛ زیرفرایند آزاد (مثلِ §۲.۴) |
| CI | `server.yml` و سدِ مهاجرت‌ها می‌روند؛ آزمون‌های وبِ سرور باید فرانت‌اند را در commitِ سنجاق‌شده checkout کنند؛ تغییرِ هسته/بردار ⇒ `repository_dispatch` به هر دو | بی تغییر | بی تغییر |
| استقرار | سرورِ خانگی از مخزنِ خصوصی می‌کشد ⇒ deploy key؛ پنلِ خانگی (ریپوی `server`) عوض می‌شود | بی تغییر | بی تغییر |
| import‌ها | `require('../../license/x.js')` ِ آزمون‌ها ⇒ بستهٔ نسخه‌دارِ «هسته‌ها و بردارها» یا vendor با سنجشِ بایت | بی تغییر | بی تغییر |
| آزمون | قراردادِ `docs/api/routes.json` + بردارها مرزِ رسمی؛ CIِ فرانت‌اند می‌سنجد کلاینت فقط مسیرِ موجود در قرارداد را صدا بزند | بی تغییر | بی تغییر |
| هزینه/خطر | چند PR و گامِ صاحب محصول؛ خطرِ بالا برای صفِ CI و آزمون‌های سرور | به imageِ ابری با `bwrap` و فهرستِ دامنهٔ مجاز وابسته است — امروز در اختیارِ ما نیست | کم؛ یک PR؛ برگشت‌پذیر |

خصوصی کردنِ **کلِ** مخزن هم گزینه است ولی پیامدِ مستقیم دارد: Pages روی مخزنِ خصوصی طرحِ پولی می‌خواهد، دقیقه‌های Actions محدود می‌شود
(صفِ امروز ~۶۰۰ اجرا)، و به‌روزرسانیِ برنامه از releaseهای همین مخزن (`server/src/routes/releases.js`) بی توکن نمی‌شود.

### ۴.۳ پیشنهاد — فازبندی

- **فاز ۰ (همین PR و #583):** قاعده‌های deny + سدِ CI + آزمونِ واقعی؛ قراردادِ API ِ ماشین‌خوان.
- **فاز ۱ (پیشنهادِ اجرا؛ یک PR):** گزینهٔ (پ): profile ِ «عاملِ فرانت‌اند» — settings ِ جدا با `Read/Edit(/server/**)` در deny، برای
  `claude --settings` روی کامپیوتر و سیزنِ ابری با `sparse_checkout_paths` = `android-native`، `desktop`، `core`، `license`، `admin-android`، `docs`،
  `fonts`، `icons`، `sounds` و فایل‌های ریشه. صادقانه: این کمینه‌دسترسی و کمتر خواندن است، **نه** پنهان کردنِ بک‌اند.
- **فاز ۲ (فقط با تصمیمِ صاحب محصول):** اگر سورسِ بک‌اند باید محرمانه شود ⇒ (الف)، با ترتیب: ساختِ مخزنِ خصوصی ⇐ بستهٔ هسته‌ها/بردارها/قرارداد ⇐
  دوگانه‌اجرای CI ⇐ برداشتنِ `server/` از این مخزن ⇐ deploy key ِ سرورِ خانگی. هر secretی که روزی در این مخزن بوده باید چرخانده شود.
- **فاز ۳:** مرزِ سیستم‌عامل هر جا شدنی است (§۷ بندِ ۴).

**پرسش‌ها از صاحب محصول:** (۱) آیا سورسِ بک‌اند باید محرمانه شود، با علم به اینکه تاریخچهٔ تا امروز عمومی می‌ماند؟ (۲) فاز ۱ اجرا شود؟
(۳) اگر (الف): نامِ مخزنِ خصوصی و اینکه پنلِ خانگی از کجا بکشد.

## ۵. قراردادِ API برای کارِ بی سورسِ بک‌اند (گامِ ۴، پایهٔ SEC-AI-01) — #583

- `node tools/api-contract/generate.cjs` ⇒ `docs/api/routes.json` (`--check` ⇒ کد ۱ اگر کهنه است). پیش‌نیاز: `server/node_modules`.
- درستیِ فهرست: سرورِ **واقعی** (`createApp()`) با PGlite ِ درونِ حافظه و env ِ **پاک** (DATABASE_URL ِ محیط هرگز به کار نمی‌رود؛ secretِ ساختگی)
  بالا می‌آید و ثبتِ هر مسیر در router ِ Express پاییده می‌شود ⇒ همان مسیرهایی که سرور سوار می‌کند، با فایلِ منبع.
- هر مسیر: `method`، `path`، `area`، `auth` (`admin`/`user`/`device`/`optional`/`inline`/`none-detected`)، `guards`، `params`، `query`، `body`،
  `response.statuses`/`keys`، `errors` (وضعیت + کد) و `shape`. پوششِ نسخهٔ اول: ۶۸۲ مسیر؛ شکل: ۵۳۴ `extracted`، ۱۴۴ `empty`، ۴ `unresolved`.
  شکلِ درخواست/پاسخ **تقریبی** است (فیلدی که handler به تابعِ کمکی می‌دهد دیده نمی‌شود) و `none-detected` یعنی «نگهبانِ نام‌دار دیده نشد»،
  نه «عمومی است». `/api/v1/…` همان `/api/…` است (`aliases`).
- در CI سد نیست: هر PR ِ سرور باید این فایل را بسازد و ده‌ها PR ِ هم‌زمان بر سرِ آن برخورد می‌کردند. پس از تغییرِ مسیرهای سرور دوباره بسازید.
- برای `sec-api-web`: ثبتِ تکراری (دومی هرگز نمی‌رسد) — `GET /api/me/heartbeat` (`me.js:62` و `:111`)، `POST /api/pump/backups`،
  `POST /api/pump/events` و دو همتای `device`ِ آن‌ها، `GET /` و `GET /market` در `app.js` (`duplicateOfEarlier` در فایل).

## ۶. هماهنگی با شاخه‌های دیگر

- **`claude/sec-artifacts`** (درِ artifact مالِ آن‌جاست؛ این‌جا دست نخورد): `web-pages.yml:126-131` با `git archive` کلِ مخزن — `server/`، `docs/` و
  `android-native/app/tohid-release.jks` — را روی Pages می‌گذارد (SEC-ART-03/04/05). کلید هم ردیاب‌شده است (SEC-ART-05).
- **`claude/sec-supply`:** بازبینیِ ایستای لاگِ CI در §۳ آمد؛ lint ِ خودکارِ «لاگِ بی secret» مالِ آن‌جاست.
- **`claude/sec-api-web`:** `docs/api/routes.json` فهرستِ مسیرهای ساخته از کد است و می‌تواند پایهٔ فهرستِ آن‌جا باشد.
- بیرون از دامنه (فروشگاهِ آنلاینِ مشتری): مسیرهای `/api/store/**` و `/api/market/**` فقط فهرست شدند، بازبینی نشدند.

## ۷. کارهای صاحب محصول

1. **تصمیمِ §۴.۳** (سه پرسش).
2. **کلیدِ امضا (F0-09):** همان گام‌های `docs/spec/OWNER-ACTIONS-fa.md` — چرخاندنِ کلید و برداشتن از مخزن. تا آن روز SEC-AI-03 `FAIL` است.
3. `create-admin.js` روی سرورِ زنده را خودتان اجرا کنید، نه عامل (رمز در ترنسکریپت می‌ماند).
4. روی کامپیوترِ خودتان (مک/لینوکس با `bwrap`) در تنظیماتِ **کاربر** `sandbox.enabled` را با `filesystem.denyRead` برای همان الگوهای §۲.۱ و
   فهرستِ دامنهٔ مجاز (github.com، registry.npmjs.org، …) روشن کنید تا شکافِ زیرفرایند (§۲.۴) هم بسته شود.
5. هر تغییرِ `.claude/settings.json` را در PR ببینید؛ سدِ CI ضعیف شدن را رد می‌کند ولی افزودنِ allow ِ تازه را نه.

## ۸. دفترِ آزمون‌ها (اجراشده در همین شاخه)

| آزمون | نتیجه |
|---|---|
| `node --test tools/ai-access/check-settings.test.mjs` | ۱۱/۱۱ سبز. سرخ پیش از اصلاح: تنظیماتِ main ⇒ «permissions.deny نیست». ۴ جهش در خودِ سنجه (تطبیق، قاعدهٔ لازم، ترتیبِ استثنا، قاعدهٔ بی‌اثر) ⇒ هر ۴ گرفته شد |
| `node tools/ai-access/check-settings.mjs` | سبز؛ پوشش: `android-native/app/tohid-release.jks` |
| `node tools/ai-access/probe-enforcement.mjs` | سبز (جدولِ §۲.۳)؛ Claude Code 2.1.314، haiku؛ دستی، نه CI |
| `node --test tools/api-contract/extract.test.cjs` (#583) | ۵/۵ سبز؛ ۳ جهش (regex، نامِ مستعار، وضعیتِ پاسخ) ⇒ گرفته شد |
| `node tools/api-contract/generate.cjs --check` (#583) | تازه؛ سه اجرای پیاپی هشِ یکسان |
