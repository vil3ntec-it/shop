-- ============================================================
--  اعلانِ سفارش حتی وقتی برنامهٔ گوشی بسته است — v3 §۳۰ (NOTIF)
--
--  صفِ پایدارِ (outbox) هر دکان: یک ردیف برای هر سفارشِ تازهٔ مشتری،
--  نوشته‌شده در **همان تراکنشِ** ساختِ سفارش (`orders.create`). فرستادن
--  جداست و بعداً است؛ خطای فرستادن هرگز سفارشِ ثبت‌شده را پس نمی‌گیرد
--  (§۳۰.۱ بندِ ۶).
--
--  ⛔ پیش‌فرض خاموش: تا کلیدِ `shop.order_notify` در «تنظیماتِ زنده»
--  روشن نشود هیچ ردیفی در این جدول نوشته نمی‌شود.
--  ⛔ یکتایی با شناسهٔ سفارش: (order_id, event) — تکرارِ همان رویداد
--  ردیفِ دوم نمی‌سازد (NOTIF-09).
--  ⛔ سه حالِ جدا (§۳۰.۱ بندِ ۷): ردیف هست = «در سرور ثبت شد»؛
--  `provider_accepted_at` = «سرویسِ پوش پذیرفت» (اثباتِ دیده‌شدن نیست)؛
--  `acked_at` = «کسی باز کرد یا سفارش را رسیدگی کرد».
--  ⛔ تلاش محدود است (NOTIF-11): `attempts` و `reminders` سقف دارند و
--  کانالِ پشتیبان حداکثر یک بار صدا زده می‌شود (`fallback_*`).
--
--  بازگشت: این جدول را هیچ جای دیگری نمی‌خواند؛ پاک کردنش (یا خاموش
--  ماندنِ پرچم) هیچ رفتاری را عوض نمی‌کند.
-- ============================================================

CREATE TABLE IF NOT EXISTS order_notify_outbox (
  id                    text    PRIMARY KEY,                 -- ULID
  shop_id               text    NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  order_id              text    NOT NULL REFERENCES online_orders(id) ON DELETE CASCADE,
  order_no              bigint  NOT NULL,
  event                 text    NOT NULL DEFAULT 'order_created' CHECK (event IN ('order_created')),
  status                text    NOT NULL DEFAULT 'pending' CHECK (status IN (
                          'pending',            -- منتظرِ تلاشِ (دوبارهٔ) فرستادن
                          'sending',            -- در دستِ کارگر (locked_at)
                          'provider_accepted',  -- پوش دست‌کم برای یک دستگاه پذیرفته شد؛ منتظرِ باز شدن
                          'acked',              -- کسی باز کرد یا سفارش از «تازه» بیرون رفت
                          'failed',             -- تلاش‌ها تمام شد و هیچ دستگاهی نپذیرفت
                          'unacked',            -- پذیرفته شد، یادآوری‌ها تمام شد، کسی باز نکرد
                          'cancelled')),        -- پرچم پیش از فرستادن خاموش شد
  attempts              integer NOT NULL DEFAULT 0,
  reminders             integer NOT NULL DEFAULT 0,
  next_attempt_at       bigint  NOT NULL,
  locked_at             bigint,
  devices_total         integer NOT NULL DEFAULT 0,
  devices_sent          integer NOT NULL DEFAULT 0,
  --  ok · push_off · no_devices · bad_service_account · provider_error · flag_off
  last_result           text    NOT NULL DEFAULT '',
  last_error            text    NOT NULL DEFAULT '',
  provider_accepted_at  bigint,
  first_received_at     bigint,
  acked_at              bigint,
  ack_source            text    NOT NULL DEFAULT '' CHECK (ack_source IN ('', 'device', 'order_status')),
  acked_by              text    NOT NULL DEFAULT '',
  acked_device          text    NOT NULL DEFAULT '',
  --  کانالِ پشتیبان (§۳۰.۴): پیامکِ هشدار به مالک — جدا از پیامکِ OTP
  fallback_channel      text    NOT NULL DEFAULT '',
  fallback_state        text    NOT NULL DEFAULT '' CHECK (fallback_state IN (
                          '', 'not_configured', 'provider_accepted', 'provider_error')),
  fallback_at           bigint,
  created_at            bigint  NOT NULL,
  updated_at            bigint  NOT NULL,
  UNIQUE (order_id, event)
);

--  کارگر فقط ردیف‌های زنده را می‌خواهد
CREATE INDEX IF NOT EXISTS ix_order_notify_due
  ON order_notify_outbox(next_attempt_at)
  WHERE status IN ('pending', 'sending', 'provider_accepted');
CREATE INDEX IF NOT EXISTS ix_order_notify_shop
  ON order_notify_outbox(shop_id, created_at);
