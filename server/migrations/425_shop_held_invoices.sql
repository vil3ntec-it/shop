-- ============================================================
--  ۴۲۵ — پشتیبانِ سرورِ فاکتورهای نگه‌داشته (POS-12، S09؛ ۱۴۰۵/۰۸/۱۸)
--
--  فاکتورِ نگه‌داشته (`HeldInvoiceStore` ِ برنامه) تا امروز فقط در فایلِ همان دستگاه بود: پاک کردنِ برنامه، دستگاهِ
--  تازه یا گم شدنِ گوشی ⇒ همهٔ نگه‌داشته‌ها رفته بودند و دستگاهِ دیگرِ دکان هم نمی‌دیدشان. حالا هر نگه‌داشته یک
--  ردیف است (کلید: دکان + شناسهٔ فاکتور = `SaleDraft.key`، همان شناسهٔ فروشِ بعدی):
--    body        خودِ نگه‌داشته همان‌طور که برنامه می‌نویسد (`HeldInvoice`)؛ سرور فقط شکل و اندازه را می‌سنجد
--    version     نسخهٔ دستگاه (ساعتِ ویرایشِ همان فاکتور، همیشه بیش از نسخهٔ پیشینی که دیده بود). نوشتنِ کهنه‌تر یا
--                برابر هرگز روی تازه‌تر نمی‌نشیند (۴۰۹ `held_stale` با ردیفِ کنونی)
--    deleted     سنگِ قبر: فاکتور برگشت (`resumed`)، فروش رفت (`sold`) یا دور انداخته شد (`deleted`) — تا نوشتنِ کهنهٔ
--                دستگاهی دیگر آن را زنده نکند. سنگِ قبرِ کهنه‌تر از ۳۰ روز با نوشتنِ بعدیِ همان دکان پاک می‌شود
--    rev         شمارهٔ پیاپیِ همان دکان (زیرِ قفلِ دکان) برای «از کجا به بعد» ِ `GET ?since=`
--  ⛔ پشتِ پرچمِ خاموشِ `shop.held_sync`: خاموش ⇒ هیچ ردیفی نوشته نمی‌شود.
--  ⛔ shop_id فقط از عضویتِ توکن؛ دور انداختنِ نگه‌داشتهٔ کسِ دیگر `sales.cancel` می‌خواهد.
--  ⛔ سقف‌ها: ۶۰ نگه‌داشتهٔ زنده در هر دکان، ۳۲ کیلوبایت و ۲۰۰ ردیف در هر فاکتور.
--  فقط افزودنی. rollback: migrations/rollback/425_shop_held_invoices.down.sql
-- ============================================================

CREATE TABLE IF NOT EXISTS shop_held_invoices (
  shop_id     text     NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  id          text     NOT NULL CHECK (id ~ '^[0-9A-Za-z_-]{1,64}$'),
  body        jsonb,
  bytes       integer  NOT NULL DEFAULT 0,
  version     bigint   NOT NULL,
  deleted     boolean  NOT NULL DEFAULT false,
  reason      text     NOT NULL DEFAULT '' CHECK (reason IN ('', 'resumed', 'sold', 'deleted')),
  rev         bigint   NOT NULL,
  --  نخستین نگه‌دارنده و آخرین نگه‌دارنده (همان «فاکتورِ چه کسی») — شناسهٔ کاربر
  created_by  text     NOT NULL,
  held_by     text     NOT NULL,
  updated_by  text     NOT NULL,
  device_id   text     NOT NULL DEFAULT '',
  --  ساعتِ سرور، نه دستگاه
  created_at  bigint   NOT NULL,
  updated_at  bigint   NOT NULL,
  PRIMARY KEY (shop_id, id),
  CHECK (deleted OR body IS NOT NULL)
);
CREATE INDEX IF NOT EXISTS ix_shop_held_invoices_rev ON shop_held_invoices(shop_id, rev);
CREATE INDEX IF NOT EXISTS ix_shop_held_invoices_live ON shop_held_invoices(shop_id) WHERE NOT deleted;
