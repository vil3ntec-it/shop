-- ============================================================
--  SEC-02 — «سرِ ثبت‌شده» ِ زنجیرهٔ اسنادِ مالیِ هر دستگاه (بخشِ ۱۰.۳)
--
--  هر دستگاه برای سریِ خودش (F0-03، `shop_doc_series`) زنجیرهٔ هشِ
--  اسنادِ مالی می‌سازد و در هر دورِ همگام‌سازی سر و حلقه‌های تازه را
--  می‌فرستد (`POST /api/sync/v1/chain`، `lib/doc-chain.js`). خودِ حلقه‌ها
--  در `hash_chain` ِ فقط‌افزودنی (۰۷۰) با نامِ `doc/<دکان>/<سری>/<دوره>`
--  می‌نشینند — همان‌جا که ابزارِ «بررسیِ زنجیره» ِ پنل و
--  `npm run verify-chain` می‌سنجند.
--
--  این جدول فقط «آخرین گزارشِ دستگاه» است، یک ردیف برای هر (دکان، سری، دوره):
--    device_id      دستگاهی که این دوره را آغاز کرد
--    reported_*     سری که دستگاه در آخرین گزارش گفت (ممکن است جلوترِ
--                   حلقه‌های رسیده باشد)
--    problem*       آخرین ناهمخوانی (انشعاب، عقب‌گرد، پیوندِ شکسته، …)؛
--                   '' یعنی تا امروز هیچ. رویدادِ امنیتی فقط وقتی ثبت
--                   می‌شود که (problem، problem_seq) تازه باشد — هر دورِ
--                   همگام‌سازی سابقه را پر نمی‌کند.
--  «دوره» (epoch): زنجیره‌ای که شکست ادامه نمی‌یابد؛ دستگاه دورهٔ تازه
--  می‌سازد و دورهٔ کهنه شاهد می‌ماند.
--
--  ⛔ پشتِ پرچمِ خاموشِ `shop.doc_chain` (live_config): خاموش ⇒ هیچ ردیفی.
--  ⛔ هیچ دادهٔ شخصی: فقط شناسه‌ها و هش.
--  برگشت: DROP TABLE device_chain_heads; (حلقه‌های `hash_chain` به قاعدهٔ
--  ۰۷۰ پاک نمی‌شوند.)
-- ============================================================
CREATE TABLE IF NOT EXISTS device_chain_heads (
  shop_id        text    NOT NULL,
  series         text    NOT NULL,
  epoch          integer NOT NULL CHECK (epoch >= 1),
  device_id      text    NOT NULL,
  reported_seq   bigint  NOT NULL DEFAULT 0 CHECK (reported_seq >= 0),
  reported_hash  text    NOT NULL DEFAULT '',
  reported_at    bigint  NOT NULL,
  problem        text    NOT NULL DEFAULT '',
  problem_seq    bigint  NOT NULL DEFAULT 0,
  problem_at     bigint  NOT NULL DEFAULT 0,
  created_at     bigint  NOT NULL,
  PRIMARY KEY (shop_id, series, epoch)
);
