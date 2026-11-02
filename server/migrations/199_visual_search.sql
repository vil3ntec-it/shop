-- ============================================================
--  جست‌وجوی تصویریِ کالا بر پایهٔ شباهت — AI-11 (§۶.۲۲) — `lib/visual-search.js`.
--
--  product_visual_hashes   یک ردیف برای هر کالای عکس‌دارِ دکان: همان dHash ِ IMG-15 (۱۶ رقمِ hex) و
--                          هیستوگرامِ ۸ خانه‌ایِ روشنایی (۱۶ رقمِ hex، درصد). `src` = منبعِ همان عکسی که
--                          سنجیده شد ('p<rev>' عکسِ همگام‌شدهٔ ۰۴۵، 'b<blob_hash>' انتخابِ بانک)؛ منبعِ
--                          تازه ⇒ سنجشِ دوباره، منبعِ رفته ⇒ ردیف پاک. dhash = '-' ⇒ سنجیدنی نبود.
--
--  ⛔ عکسِ پرسش (جست‌وجو) هرگز این‌جا یا هیچ جای دیگر نمی‌نشیند؛ فقط عکس‌های خودِ کالاها.
--  ⛔ پشتِ پرچمِ خاموشِ `shop.visual_search`: تا روشن نشود هیچ ردیفی نوشته نمی‌شود.
--  فقط افزودنی. برگشت: rollback/199_visual_search.down.sql.
-- ============================================================

CREATE TABLE IF NOT EXISTS product_visual_hashes (
  shop_id     text   NOT NULL,
  product_id  text   NOT NULL,
  src         text   NOT NULL,
  dhash       text   NOT NULL,
  hist        text   NOT NULL DEFAULT '',
  updated_at  bigint NOT NULL,
  PRIMARY KEY (shop_id, product_id)
);
