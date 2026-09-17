-- ============================================================
--  پوش برای دستگاهی که حساب ندارد
-- ------------------------------------------------------------
--  ⛔ سوراخی که این می‌بندد:
--
--  `push_tokens` فقط `user_id` و `admin_id` داشت، و `sendTo` هم فقط
--  با همان دو می‌گشت. یعنی برنامهٔ کامپیوترِ پمپ — که **حساب ندارد** و
--  با کدِ شش‌رقمی فعال می‌شود — می‌توانست توکنش را ثبت کند ولی
--  هیچ‌وقت هیچ پیامی نمی‌گرفت: ردیفش با `user_id=''` می‌نشست و هیچ
--  پرس‌وجویی به آن نمی‌رسید. ثبتِ بی‌فایده، که بدتر از نبودن است چون
--  به‌نظر می‌رسد کار می‌کند.
--
--  با `station_id`، جوابِ مدیر و خبرِ پایانِ اشتراک به کامپیوترِ پمپ
--  هم می‌رسد — همان‌جا که صاحبِ پمپ واقعاً می‌نشیند.
-- ============================================================

ALTER TABLE push_tokens ADD COLUMN IF NOT EXISTS station_id text NOT NULL DEFAULT '';
--  و دکان، تا خبرِ «کالا تمام شد» به همهٔ گوشی‌های همان دکان برسد،
--  نه فقط به صاحبش
ALTER TABLE push_tokens ADD COLUMN IF NOT EXISTS shop_id text NOT NULL DEFAULT '';

CREATE INDEX IF NOT EXISTS idx_push_station ON push_tokens(station_id) WHERE station_id <> '';
CREATE INDEX IF NOT EXISTS idx_push_shop    ON push_tokens(shop_id)    WHERE shop_id    <> '';
