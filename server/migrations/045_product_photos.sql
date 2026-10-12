-- ============================================================
--  عکسِ کالاها روی حساب — هر دستگاهِ همان دکان همان عکس را می‌بیند
--
--  عکس داخلِ دفتر (sync_rows) نمی‌رود: دفتر با هر تغییر جابه‌جا می‌شود و
--  چند عکسِ ۶۰ کیلوبایتی آن را کند می‌کرد. این‌جا هر کالا یک ردیف دارد
--  و `rev` (یکتا و فقط رو به بالا) به دستگاه می‌گوید از آخرین بار چه
--  عوض شده. حذف نرم است: `data` تهی می‌شود ولی ردیف می‌ماند تا
--  دستگاه‌های دیگر هم بفهمند.
--
--  ⛔ هر پرس‌وجو `app` و `account_id` را شرط می‌کند — مثلِ sync_rows.
-- ============================================================

CREATE SEQUENCE IF NOT EXISTS product_photo_rev;

CREATE TABLE IF NOT EXISTS product_photos (
  app         text   NOT NULL,
  account_id  text   NOT NULL,
  product_id  text   NOT NULL,
  data        bytea,
  hash        text   NOT NULL DEFAULT '',
  size        integer NOT NULL DEFAULT 0,
  rev         bigint NOT NULL,
  updated_at  bigint NOT NULL,
  device_id   text   NOT NULL DEFAULT '',
  PRIMARY KEY (app, account_id, product_id)
);

CREATE INDEX IF NOT EXISTS product_photos_rev ON product_photos (app, account_id, rev);
