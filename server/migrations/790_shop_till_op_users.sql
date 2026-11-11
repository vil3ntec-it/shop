-- ============================================================
--  ۷۹۰ — «کاربرِ سنجیدهٔ صندوق» برای هر op ِ نشستهٔ دکان (S46، STF-04؛ ۱۴۰۵/۰۸/۲۰)
--
--  تا امروز سرور هر op را فقط به نامِ حسابِ دستگاه (توکن) می‌دید؛ نوبت‌های سنجیدهٔ صندوق (مهاجرتِ ۶۴۰) می‌گفتند «چه کسی
--  پشتِ کدام دستگاه بود»، ولی سند آن را نداشت. حالا برای هر op که Sync v1 یا v3 ِ دکان **نشاند** («applied») یک ردیف:
--    till_member_id  عضوی که سرور PIN اش را روی **دستگاهِ همان توکن** سنجید و نوبتش هنگامِ رسیدنِ op باز بود — یا NULL
--                    (ناشناخته: PIN ِ سرور خاموش، نوبتی نبود، op پیش از نوبت ساخته شده، یا دیر رسید)؛ هرگز ادعای برنامه
--    basis           چرا همین (session | no_session | pin_off | no_device | before_session | late)
--  ⛔ جدولِ کنار: `oplog`، `sync_rows`، `sync_v3_*`، fields و hash ِ op دست نمی‌خورند؛ موتورِ Sync v1 ِ مشترک با پمپ دست نمی‌خورد
--     (نوشتن از میان‌افزارِ فقط‌دکان پس از پاسخِ موتور — `lib/till-op-user.js`).
--  ⛔ کلیدِ (shop_id, op_id): op ِ تکراری (دوباره فرستاده) ردیفِ دوم نمی‌سازد (`ON CONFLICT DO NOTHING`).
--  ⛔ پشتِ پرچمِ خاموشِ `shop.till_op_user`: خاموش ⇒ هیچ ردیفی نوشته یا خوانده نمی‌شود.
--  ⛔ `till_member_id` کلیدِ بیگانه ندارد (همان قاعدهٔ `shop_till_sessions`): سابقه با رفتنِ عضو نمی‌رود.
--  فقط افزودنی. rollback: migrations/rollback/790_shop_till_op_users.down.sql
-- ============================================================

CREATE TABLE IF NOT EXISTS shop_till_op_users (
  shop_id         text   NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  op_id           text   NOT NULL,
  path            text   NOT NULL CHECK (path IN ('v1', 'v3')),
  table_name      text   NOT NULL,
  row_id          text   NOT NULL,
  op_type         text   NOT NULL DEFAULT '',
  --  دستگاه و کاربرِ **توکن** (حسابِ دستگاه)، نه شناسه‌ای که برنامه در بدنه می‌فرستد
  device_id       text   NOT NULL DEFAULT '',
  device_user_id  text   NOT NULL DEFAULT '',
  till_member_id  text,
  till_session_id text,
  basis           text   NOT NULL
    CHECK (basis IN ('session', 'no_session', 'pin_off', 'no_device', 'before_session', 'late')),
  --  زمانِ ساختنِ op به ادعای دستگاه (فقط برای نمایش و قاعدهٔ «پیش از نوبت/دیر»)؛ `at` زمانِ سرور هنگامِ رسیدن
  op_ts           bigint,
  at              bigint NOT NULL,
  PRIMARY KEY (shop_id, op_id),
  CHECK ((basis = 'session') = (till_member_id IS NOT NULL)),
  CHECK ((till_member_id IS NULL) = (till_session_id IS NULL))
);
CREATE INDEX IF NOT EXISTS ix_shop_till_op_users_at ON shop_till_op_users(shop_id, at);
CREATE INDEX IF NOT EXISTS ix_shop_till_op_users_row ON shop_till_op_users(shop_id, table_name, row_id);
CREATE INDEX IF NOT EXISTS ix_shop_till_op_users_member ON shop_till_op_users(shop_id, till_member_id, at)
  WHERE till_member_id IS NOT NULL;
