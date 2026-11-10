-- ============================================================
--  ۴۷۵ — «خبرم کن وقتی موجود/ارزان شد» (WEB-10) روی پوشِ وب ِ همین مرورگر (۱۴۰۵/۰۸/۱۹)
--
--  `store_alert_push`: اشتراکِ پوشِ مرورگرِ مشتری برای «خبرم کن» ِ کالا — بسته به (دکان، شمارهٔ تأییدشده، مرورگر)، نه به
--  سفارش (`store_push_subs` ِ ۴۷۰ برای صفحهٔ پیگیری است). همان فهرستِ سفیدِ SSRF، رمز و VAPID ِ `lib/store-push.js`.
--  `proof` همان اثباتی که مشتری با آن «روی همین دستگاه» را روشن کرد:
--    ticket  — بلیتِ «شماره تأیید شد»: `ticket_gen` نسلِ همان بلیت؛ «خروج از همه‌جا»/پاک کردنِ حساب نسل را بالا می‌برد
--    session — نشستِ حسابِ ویترین: `session_id` (پاک شدنِ نشست ⇒ cascade)؛ نشستِ بسته، منقضی، حسابِ مسدود یا شمارهٔ عوض‌شده
--  `expires_at` = انقضای همان اثبات (و حداکثر ۹۰ روز، عمرِ هشدار). ⛔ هر پیام پیش از فرستادن اثبات را دوباره می‌سنجد:
--  باطل/منقضی ⇒ اشتراک پاک و هیچ پیامی (`lib/store-alert-push.js`).
--  حداکثر ۳ مرورگر برای هر (دکان، شماره)؛ اشتراکِ مرده (۴۰۴/۴۱۰ ِ سرویس) پاک؛ پاک شدنِ دکان ⇒ cascade؛
--  حذفِ دادهٔ مشتری (SEC-10) ⇒ اشتراک‌های همان شماره در همان دکان (`alertPushSubs`)؛ پاک کردنِ حساب ⇒ همهٔ دکان‌ها.
--
--  ⛔ پیش‌فرض خاموش: بی پرچم‌های `shop.storefront.alerts` + `shop.storefront.web_push` + `shop.customer_notify` هیچ ردیفی
--  نوشته نمی‌شود. فقط افزودنی. rollback: migrations/rollback/475_store_alert_push.down.sql
-- ============================================================

CREATE TABLE IF NOT EXISTS store_alert_push (
  id          text    PRIMARY KEY,                                                    -- ULID
  shop_id     text    NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  phone       text    NOT NULL CHECK (phone ~ '^\+[1-9][0-9]{7,14}$'),
  endpoint    text    NOT NULL CHECK (length(endpoint) BETWEEN 12 AND 1024),
  p256dh      text    NOT NULL CHECK (length(p256dh) BETWEEN 80 AND 100),
  auth        text    NOT NULL CHECK (length(auth) BETWEEN 16 AND 32),
  page        text    NOT NULL DEFAULT '' CHECK (length(page) <= 120),
  proof       text    NOT NULL CHECK (proof IN ('ticket', 'session')),
  ticket_gen  integer NOT NULL DEFAULT 0 CHECK (ticket_gen >= 0),
  session_id  text    REFERENCES store_customer_sessions(id) ON DELETE CASCADE,
  expires_at  bigint  NOT NULL,
  created_at  bigint  NOT NULL,
  updated_at  bigint  NOT NULL,
  last_ok_at  bigint,
  UNIQUE (shop_id, phone, endpoint),
  CHECK ((proof = 'session') = (session_id IS NOT NULL))
);
--  فرستادن و سقفِ هر شماره: اشتراک‌های همین (دکان، شماره)؛ پاک کردنِ حساب: همین شماره در همهٔ دکان‌ها
CREATE INDEX IF NOT EXISTS ix_store_alert_push_phone ON store_alert_push(phone, shop_id);
--  پاک‌سازیِ اشتراکِ منقضی بی پیمایشِ کلِ جدول
CREATE INDEX IF NOT EXISTS ix_store_alert_push_expires ON store_alert_push(expires_at);
