-- ============================================================
--  ۴۷۰ — پوشِ وب برای مشتریِ ویترین (§۳.۱۲ «مشتری: لینکِ پیگیری و پوشِ PWA …»؛ CHK-20، NTF-01، ORD-16؛ ۱۴۰۵/۰۸/۱۹)
--
--  `store_push_subs`: اشتراکِ پوشِ مرورگرِ مشتری برای **یک سفارش** (نه حساب، نه شماره): مشتری در صفحهٔ پیگیری «خبرم کن»
--  را می‌زند و کانالِ `push` ِ `lib/customer-notify.js` پیامِ همان سفارش را رمزشده (RFC 8291) به همان مرورگر می‌فرستد.
--  `endpoint` فقط نشانیِ سرویسِ پوشِ مرورگر (فهرستِ سفیدِ `lib/store-push.js`)؛ `p256dh`/`auth` کلیدهای رمزِ همان مرورگر.
--  `page` همان نشانیِ صفحهٔ ویترینِ مشتری (`/` یا `/store/<slug>`) برای بازکردنِ پیگیری با لمسِ اعلان.
--  `link_gen` نسلِ لینکِ پیگیریِ اشتراک (TEN-03): لینکِ باطل‌شده (`track_gen` بالا رفت) ⇒ اشتراکِ آن لینک دیگر پیامی نمی‌گیرد
--  و پاک می‌شود — کسی که لینکِ لورفته را داشت با «باطل کردن» بیرون می‌ماند.
--  حداکثر ۳ اشتراک برای هر سفارش (lib)؛ اشتراکِ مرده (۴۰۴/۴۱۰ ِ سرویس) پاک؛ پاک شدنِ سفارش یا دکان ⇒ cascade؛
--  حذفِ دادهٔ مشتری (SEC-10) ⇒ اشتراک‌های همان سفارش‌ها.
--
--  `customer_notify_outbox.only_channel`: فرستادنِ دوباره فقط از یک کانالِ **رایگان** — وقتی مشتری پس از «ثبت شد» تازه
--  پوش را روشن می‌کند، آخرین پیامِ نرفتهٔ همان سفارش یک بار دیگر و فقط از `push` می‌رود (کارِ مشتری هرگز پیامکِ پولی
--  برنمی‌انگیزد). خالی = همان زنجیرهٔ کاملِ `MATRIX` (رفتارِ پیشین).
--
--  ⛔ پیش‌فرض خاموش: بی پرچمِ `shop.storefront.web_push` (و `shop.customer_notify`) هیچ ردیفی نوشته نمی‌شود.
--  فقط افزودنی. rollback: migrations/rollback/470_store_web_push.down.sql
-- ============================================================

CREATE TABLE IF NOT EXISTS store_push_subs (
  id          text    PRIMARY KEY,                                                    -- ULID
  shop_id     text    NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  order_id    text    NOT NULL REFERENCES online_orders(id) ON DELETE CASCADE,
  endpoint    text    NOT NULL CHECK (length(endpoint) BETWEEN 12 AND 1024),
  p256dh      text    NOT NULL CHECK (length(p256dh) BETWEEN 80 AND 100),
  auth        text    NOT NULL CHECK (length(auth) BETWEEN 16 AND 32),
  page        text    NOT NULL DEFAULT '' CHECK (length(page) <= 120),
  link_gen    integer NOT NULL DEFAULT 0 CHECK (link_gen >= 0),                       -- TEN-03: نسلِ لینکِ پیگیری هنگامِ اشتراک
  created_at  bigint  NOT NULL,
  last_ok_at  bigint,
  UNIQUE (order_id, endpoint)
);
--  کانالِ `push`: اشتراک‌های همین سفارش
CREATE INDEX IF NOT EXISTS ix_store_push_subs_order ON store_push_subs(shop_id, order_id);
--  پاک‌سازیِ اشتراکِ کهنه (۳۰ روز) بی پیمایشِ کلِ جدول
CREATE INDEX IF NOT EXISTS ix_store_push_subs_created ON store_push_subs(created_at);

ALTER TABLE customer_notify_outbox ADD COLUMN IF NOT EXISTS only_channel text NOT NULL DEFAULT ''
  CHECK (only_channel IN ('', 'push'));
