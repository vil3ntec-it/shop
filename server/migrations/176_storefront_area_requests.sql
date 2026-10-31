-- ============================================================
--  «وقتی به محلِ شما رسیدیم خبرم کن» ِ ویترین (فاز ۴؛ CHK-10، S34)
--
--  هر (دکان، شمارهٔ تأییدشده) یک ردیف برای پینی بیرون از همهٔ ناحیه‌های فعالِ ارسال.
--  ⛔ پینِ خام ذخیره نمی‌شود: `cell_*` مرکزِ خانهٔ ۳۰۰ متریِ شبکهٔ ثابت است (`snapPin`).
--  دکان شمارِ هر خانه را می‌بیند و شماره را فقط وقتی ناحیهٔ فعالی آن خانه را بپوشاند؛
--  «خبر دادم» یا «پس گرفتنِ» مشتری ردیف را پاک می‌کند؛ ردیفِ کهنه‌تر از یک سال با
--  نوشتنِ بعدیِ همان دکان پاک می‌شود.
--
--  ⛔ پرچمِ پیش‌فرض خاموشِ `shop.storefront.notify_area` (روی `shop.storefront.zones`):
--     تا روشن نشود این جدول خالی است. پاک شدنِ دکان ⇒ ردیف‌ها هم (cascade).
--  بازگشت: DROP همین جدول — هیچ جدولِ دیگری به آن کلید ندارد.
-- ============================================================

CREATE TABLE IF NOT EXISTS storefront_area_requests (
  id             text    PRIMARY KEY,                         -- ULID
  shop_id        text    NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  phone          text    NOT NULL CHECK (phone ~ '^\+[1-9][0-9]{7,14}$'),
  cell_lat       double precision NOT NULL CHECK (cell_lat BETWEEN -90 AND 90),
  cell_lng       double precision NOT NULL CHECK (cell_lng BETWEEN -180 AND 180),
  created_at     bigint  NOT NULL,
  updated_at     bigint  NOT NULL,
  UNIQUE (shop_id, phone)
);
--  پاک کردنِ کهنه‌ها و فهرستِ دکان
CREATE INDEX IF NOT EXISTS ix_storefront_area_requests_shop ON storefront_area_requests(shop_id, updated_at);
