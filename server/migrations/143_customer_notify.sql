-- ============================================================
--  اعلانِ وضعیتِ سفارش به مشتری — NTF-01، NTF-03، NTF-04 (§۶.۱۹، §۳.۱۲)
--
--  یک ردیف برای هر «رویدادِ مشتری» ِ هر سفارشِ ویترین (ثبت، قبول، رد،
--  آماده، در راه، تحویل‌نشده، تحویلِ دوباره، تحویل، لغو)، نوشته‌شده در
--  **همان تراکنشِ** ساخت یا گذارِ سفارش. فرستادن جداست و بعداً است؛ خطای
--  فرستادن هرگز سفارش یا گذار را پس نمی‌گیرد.
--
--  ⛔ پیش‌فرض خاموش: تا کلیدِ `shop.customer_notify` در «تنظیماتِ زنده»
--  روشن نشود هیچ ردیفی در این جدول نوشته نمی‌شود.
--  ⛔ یکتایی (NTF-04): (order_id, event) — هر رویداد حداکثر یک پیام؛ زنجیرهٔ
--  کانال‌ها در اولین «پذیرفت» می‌ایستد.
--  ⛔ بی دادهٔ حساس: نه شماره، نه نشانی، نه مبلغ. شماره هنگامِ فرستادن از خودِ
--  سفارش خوانده می‌شود؛ `phone_key` فقط HMAC ِ شماره است، برای سقفِ هر مشتری.
--  ⛔ تلاش محدود: `attempts` سقف دارد و هنگامِ «گرفتن» بالا می‌رود.
--
--  `online_orders.customer_lang`: زبانِ مشتری هنگامِ ثبت (fa، ps، en؛ خالی =
--  نامعلوم ⇒ fa) — قالبِ پیام (NTF-03). فقط ستونِ تازه با پیش‌فرضِ ثابت.
--
--  بازگشت: این جدول و ستون را هیچ جای دیگری نمی‌خواند؛ DROP TABLE
--  customer_notify_outbox و DROP COLUMN customer_lang (یا خاموش ماندنِ پرچم)
--  هیچ رفتارِ دیگری را عوض نمی‌کند.
-- ============================================================

ALTER TABLE online_orders ADD COLUMN IF NOT EXISTS customer_lang text NOT NULL DEFAULT ''
  CHECK (customer_lang IN ('', 'fa', 'ps', 'en'));

CREATE TABLE IF NOT EXISTS customer_notify_outbox (
  id               text    PRIMARY KEY,                 -- ULID
  shop_id          text    NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  order_id         text    NOT NULL REFERENCES online_orders(id) ON DELETE CASCADE,
  event            text    NOT NULL CHECK (event IN (
                     'order_received', 'order_accepted', 'order_rejected', 'order_ready',
                     'order_on_the_way', 'order_delivery_failed', 'order_delivery_retry',
                     'order_completed', 'order_cancelled')),
  lang             text    NOT NULL DEFAULT 'fa' CHECK (lang IN ('fa', 'ps', 'en')),
  phone_key        text    NOT NULL,                     -- HMAC ِ شماره؛ فقط برای سقفِ هر مشتری
  status           text    NOT NULL DEFAULT 'pending' CHECK (status IN (
                     'pending',       -- منتظرِ تلاشِ (دوبارهٔ) فرستادن
                     'sending',       -- در دستِ کارگر (locked_at)
                     'sent',          -- یک کانال پذیرفت (channel، sent_at)
                     'no_channel',    -- هیچ کانالِ مجازی وصل نبود یا همه رد کردند
                     'failed',        -- تلاش‌ها تمام شد
                     'rate_limited',  -- سقفِ پیامِ این مشتری پر بود
                     'superseded',    -- رویدادِ تازه‌ترِ همین سفارش پیش از فرستادن رسید
                     'expired',       -- دیرتر از آن‌که هنوز به کار آید
                     'cancelled')),   -- پرچم خاموش شد یا سفارش دیگر از ویترین نیست
  channel          text    NOT NULL DEFAULT '',          -- کانالی که پذیرفت
  attempts         integer NOT NULL DEFAULT 0,
  next_attempt_at  bigint  NOT NULL,
  locked_at        bigint,
  tried            jsonb   NOT NULL DEFAULT '[]'::jsonb, -- [{channel, result}] ِ آخرین تلاش
  last_error       text    NOT NULL DEFAULT '',
  provider_ref     text    NOT NULL DEFAULT '',
  sent_at          bigint,
  created_at       bigint  NOT NULL,
  updated_at       bigint  NOT NULL,
  UNIQUE (order_id, event)
);

CREATE INDEX IF NOT EXISTS ix_customer_notify_due
  ON customer_notify_outbox(next_attempt_at)
  WHERE status IN ('pending', 'sending');
CREATE INDEX IF NOT EXISTS ix_customer_notify_phone
  ON customer_notify_outbox(phone_key, sent_at)
  WHERE status = 'sent';
CREATE INDEX IF NOT EXISTS ix_customer_notify_shop
  ON customer_notify_outbox(shop_id, created_at);
