-- فاز ۳ ِ پنلِ مرکزی (console-phase3): «حالت‌های مدیریتیِ دکان» — چهار حالتِ جدا + بایگانی.
--
--   access_suspended    تعلیقِ دسترسی: هیچ عضوی به دکان نمی‌رسد (API ⇒ ۴۰۳ shop_suspended)
--   service_disabled    غیرفعال کردنِ یک سرویس (`service` = کلیدِ پرچمِ همان سرویس، مثلِ shop.storefront)
--   subscription_ended  پایانِ اشتراک: قابلیت‌های پولی بسته، ردیفِ اشتراک و پول دست نمی‌خورد
--   deletion_requested  درخواستِ حذفِ دائمی: دسترسی بسته؛ خودِ حذف فقط از راهِ تأییدشدهٔ موجود
--                       (`DELETE /api/admin/users/:id` با تایپِ نشانیِ همان حساب)
--   archived            بایگانی: دسترسی بسته، داده سرِ جایش
--
-- ⛔ هیچ‌کدام چیزی پاک نمی‌کند: نه دفترِ دکان، نه اشتراک، نه پرداخت. برداشتن = پر شدنِ ستون‌های
-- `lifted_*` (ردیف می‌ماند و خودش تاریخچه است). در هر لحظه از هر (دکان، حالت، سرویس) فقط یک ردیفِ باز.
-- ⛔ `shops.status` از همین جدول ساخته می‌شود (`lib/shop-holds.js`): 'disabled' ⇔ یکی از سه حالتِ
-- بستنِ دسترسی باز است. جای دیگری آن را نمی‌نویسد.
--
-- فقط افزودنی. برگشت: DROP TABLE shop_holds (و `UPDATE shops SET status='active'`).
CREATE TABLE IF NOT EXISTS shop_holds (
  id            text    PRIMARY KEY,
  shop_id       text    NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  kind          text    NOT NULL CHECK (kind IN
                  ('access_suspended','service_disabled','subscription_ended','deletion_requested','archived')),
  service       text    NOT NULL DEFAULT '',
  reason        text    NOT NULL,
  placed_by     text    NOT NULL,
  placed_actor  jsonb   NOT NULL DEFAULT '{}'::jsonb,
  placed_at     bigint  NOT NULL,
  lifted_at     bigint,
  lifted_by     text,
  lifted_actor  jsonb,
  lift_reason   text,
  CHECK ((kind = 'service_disabled') = (service <> ''))
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_shop_holds_open ON shop_holds(shop_id, kind, service) WHERE lifted_at IS NULL;
CREATE INDEX IF NOT EXISTS ix_shop_holds_shop ON shop_holds(shop_id, placed_at DESC);
