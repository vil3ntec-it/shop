-- ============================================================
--  ۴۱۰ — لنگرِ سرورِ زنجیرهٔ سابقهٔ عملیاتِ دستگاه (STF-09)
--
--  دستگاه سرِ زنجیرهٔ هشِ `auditLog` ِ خودش (`core/audit/AuditChain`: `chain` = `<deviceId>/<دوره>`،
--  `chainSeq`، `hash`) را پس از هر دورِ همگام‌سازی، هر بار که عوض شده، می‌فرستد
--  (`POST /api/sync/v1/audit-anchor`، `lib/device-audit-anchor.js`). خودِ ردیف‌ها همان راهِ همیشگیِ
--  `sync_rows` را می‌روند؛ این جدول فقط «دستگاه تا کجا نوشته بود» است — همان کارِ `device_chain_heads` و
--  `hash_chain` برای زنجیرهٔ اسناد — تا سنجشِ سرور (`npm run verify-chain -- --device-audit`) بریدنِ دُمی را
--  هم ببیند که هنوز همگام نشده بود.
--
--  یک ردیف برای هر سری که دستگاه گزارش کرد (دکان، زنجیره، شماره):
--    hash        هشِ حلقهٔ همان شماره به گفتهٔ دستگاه
--    problem     '' | 'fork' (همان شماره با هشِ دیگر، `other_hash`) | 'rollback' (سرِ عقب‌تر در همان دوره، یا
--                ادامهٔ دوره‌ای کهنه پس از دورهٔ تازه‌تر)
--  ردیفِ تکراری (همان شماره و همان هش) هیچ نمی‌نویسد. دورهٔ تازه (بازیابی، نصبِ دوباره) ردیفِ تازه است و
--  دورهٔ کهنه شاهد می‌ماند (همان قاعدهٔ `AuditChain.resume`).
--
--  ⛔ پشتِ پرچمِ خاموشِ `shop.doc_chain`: خاموش ⇒ هیچ ردیفی.
--  ⛔ هیچ دادهٔ شخصی: فقط شناسه‌ها و هش. فقط افزودنی.
--  rollback: migrations/rollback/410_device_audit_anchors.down.sql
-- ============================================================

CREATE TABLE IF NOT EXISTS device_audit_anchors (
  shop_id      text    NOT NULL,
  chain        text    NOT NULL,
  seq          bigint  NOT NULL CHECK (seq >= 1),
  hash         text    NOT NULL CHECK (hash ~ '^[0-9a-f]{64}$'),
  device_id    text    NOT NULL,
  epoch        bigint  NOT NULL CHECK (epoch >= 1),
  --  کاربرِ نشستی که گزارش داد (سابقه؛ تصمیمی به آن بسته نیست)
  user_id      text    NOT NULL DEFAULT '',
  problem      text    NOT NULL DEFAULT '' CHECK (problem IN ('', 'fork', 'rollback')),
  other_hash   text    NOT NULL DEFAULT '',
  reported_at  bigint  NOT NULL,
  PRIMARY KEY (shop_id, chain, seq)
);
CREATE INDEX IF NOT EXISTS ix_device_audit_anchors_device ON device_audit_anchors (shop_id, device_id, epoch);
