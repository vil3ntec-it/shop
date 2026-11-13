## جای کار
- worktree: `/home/user/wt-<X>` روی شاخهٔ `claude/e-rows-<X>` (از `claude/e-rows-11p`). فقط همین‌جا، فقط به همین شاخه push (`git push -u origin claude/e-rows-<X>`)؛ PR نساز، مرج نکن. `server/node_modules` پیوندی و در exclude است. زود و چند بار push کن تا کار گم نشود (کانتینر ممکن است دوباره راه بیفتد).
- اول `CLAUDE.md`، بندِ بالای «گزارشِ سیزن‌ها» در `docs/HANDOFF-fa.md`، «۰. تازه‌ترین» در `tools/claude-ops/README-fa.md` و `docs/spec/PARTIAL-MAP-fa.md` (نقشهٔ ردیف‌ها؛ تو بخشِ «کد» را می‌سازی). از `docs/RULES-fa.md` فقط بخشِ مربوط با grep. اسکیل‌های `anthropic-skills:smart-coding-core`، `anthropic-skills:exact-implementation-pro-max`، `anthropic-skills:elite-product-engineering-ultimate-memory` را یک بار پیش از نخستین کارِ کدی با ابزارِ Skill بخوان.

## قاعده‌ها
- ردیف فقط وقتی IMPLEMENTED-VERIFIED که هیچ ساختنی نمانده (پرچمِ پیش‌فرض‌خاموش مانع نیست). پس از هر تغییرِ STATUS: `node docs/spec/tools/spec.mjs status` و `check` ⇒ «سالم». شمارهای سرِ `PARTIAL-MAP-fa.md` را هم به‌روز کن.
- کدِ پمپ، همگام‌سازی (`sync/`) و workflowهای انتشار دست نمی‌خورند. وبِ مدیریتِ فروشگاه نداریم (وب فقط ویترینِ آنلاین). امنیت: هر درِ تازهٔ سرور نقش/مالکیت/سقفِ نرخ و آزمونِ ۴۰۳ دارد.
- `Str*`: یک `t()` برای هر تابع/ویژگی؛ واژهٔ فقط‌فارسی در دری رد؛ عدد با `asIs()`. هر چیزِ دیدنیِ تازه ⇒ سطر در `docs/WHATS-NEW-fa.md` (قاعده‌ها سرِ همان پرونده). پروندهٔ Kotlin با `Context` فقط با نامِ `…Android.kt`.
- Gradle: `export LC_ALL=C.UTF-8 JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS -Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8" GRADLE_USER_HOME=/home/user/.gradle-<X> GRADLE_RO_DEP_CACHE=/root/.gradle/caches` و `-Pkotlin.compiler.execution.strategy=in-process -Dorg.gradle.jvmargs=-Xmx2g --max-workers=2`. ⚠️ چند عامل هم‌زمان Gradle می‌دوانند و حافظه ۱۵ گیگ است: آزمون‌های هدف‌دار (`--tests '…'`) آزادند، ولی **اجرای کاملِ** `./desktop/gradlew --no-daemon -p desktop test` را فقط با `flock /tmp/gradle-full.lock …` بزن (یکی‌یکی). سرور: `cd server && TEST_DATABASE_URL=pglite:memory node --test --test-concurrency=1 test/<file>`.
- پیش از push ِ پایانی: `bash tools/claude-ops/precheck.sh /home/user/wt-<X>` ⇒ `fail=0`؛ آزمون‌های مربوط؛ اگر Kotlin عوض شد یک اجرای کاملِ desktop (۰ شکست).
- یک بند به بالای «گزارشِ سیزن‌ها»ی `docs/HANDOFF-fa.md`. `git stash` ِ بی‌نام نه.

## گزارشِ پایانی (فارسی، کوتاه)
هر ردیف: وضعیتِ تازه یا دلیلِ دست‌نزدن؛ آزمون‌ها (سرخ پیش/سبز پس)؛ باگ‌های واقعیِ یافته؛ اجرای کامل؛ sha ِ نهایی.

- ⚠️ حالا شش عامل هم‌زمان کار می‌کنند و ۴ هسته داریم: daemon نگذار (`--no-daemon`)، پس از هر اجرای Gradle پردازهٔ جامانده نباشد.
- ⚠️ پیش از push ِ پایانی جدا هم `node tools/quality/quality.mjs` بزن: هیچ ❌ (اندازهٔ فایل زیرِ سقفِ خودش، وابستگیِ پلتفرمیِ Kotlin مثلِ `System.currentTimeMillis`/`Context` زیرِ سقف). سقف را بالا نبر؛ کدِ تازه را در پروندهٔ جدا بگذار.
- ⚠️ نامِ آزمونِ Kotlin (تابعِ `…`) نویسهٔ `* ? < > | : " \ /` نداشته باشد (ویندوز)؛ در `core/src/commonTest` و `ios/shared` (Kotlin/Native) همچنین `( ) [ ] ; .` نه. نگهبان: `server/test/kotlin-test-names-windows.test.js`.
- ⚠️ هر مهاجرتِ تازه: rollback ِ آن در `migrations/rollback/<n>.down.sql` باید `DELETE FROM schema_migrations WHERE version = '<n>.sql';` هم داشته باشد (`upgrade-rehearsal.test.js`).
