-- ============================================================
--  صندوقِ عکس: عکسی که دوربینِ گوشی برای کامپیوترِ همان حساب می‌فرستد
--
--  کامپیوتر «عکسِ این کالا را بگیر» می‌خواهد (یا گوشی خودش می‌فرستد)؛ عکس
--  این‌جا می‌نشیند و کامپیوتری که کدِ جفت‌شدنش `pair` است آن را برمی‌دارد،
--  در استودیو ویرایش می‌کند و روی کالا می‌گذارد. ⛔ موقت است: بیش از ۲۴
--  ساعت نمی‌ماند و با برداشتن پاک می‌شود — انبارِ عکس نیست.
-- ============================================================

CREATE TABLE IF NOT EXISTS photo_inbox (
  id          text   PRIMARY KEY,
  app         text   NOT NULL,
  account_id  text   NOT NULL,
  pair        text   NOT NULL DEFAULT '',
  product_id  text   NOT NULL DEFAULT '',
  data        bytea  NOT NULL,
  size        integer NOT NULL DEFAULT 0,
  created_at  bigint NOT NULL,
  device_id   text   NOT NULL DEFAULT ''
);

CREATE INDEX IF NOT EXISTS photo_inbox_account ON photo_inbox (app, account_id, created_at);
