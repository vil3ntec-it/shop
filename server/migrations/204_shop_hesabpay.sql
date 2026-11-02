-- ============================================================
--  حساب‌پیِ هر دکان (درخواستِ صاحب‌کار ۱۴۰۵/۰۸/۱۱؛ PAY-21، PAY-06) — `lib/hesabpay-account.js`.
--
--  پول مستقیم به حسابِ همان دکان می‌رود؛ کلیدِ سکو (`HESABPAY_API_KEY` ِ محیط) دیگر هرگز به کار
--  نمی‌رود، حتی به‌جای دکانی که حساب ندارد. هر دکان حداکثر یک ردیف:
--    mode = off     حساب‌پی برای این دکان خاموش (پیش‌فرض)
--    mode = simple  شمارهٔ حساب‌پیِ خودِ دکان یا QR ِ خودش (اسکن‌شده) — در صندوق نشان داده می‌شود؛ تأیید
--                   فقط با «پول رسید» ِ صندوقدار (هرگز خودکار)
--    mode = auto    کلیدهای پذیرندهٔ خودِ دکان (رمزشده) ⇒ قصدِ پرداختِ سرور + وب‌هوکِ امضاشده با رازِ همان دکان
--
--  ⛔ کلید و رازِ پذیرنده فقط رمزشده (AES-256-GCM با `HESABPAY_CRED_KEY` ِ محیط، AAD = شناسهٔ دکان +
--     نامِ ستون ⇒ رونوشتِ ستون در ردیفِ دکانِ دیگر باز نمی‌شود)؛ هرگز برنمی‌گردند (فقط چهار نویسهٔ آخر).
--  ⛔ رمزِ جدای حساب‌پی (نه رمزِ برنامه): PBKDF2-SHA256 با نمک؛ قفل پس از ۵ خطا (۱۵ دقیقه، دوبرابرشونده
--     تا ۲۴ ساعت). بازنشانیِ رمزِ فراموش‌شده حساب را هم پاک می‌کند و تا `hold_until` حسابِ تازه نمی‌پذیرد.
--  ⛔ webhook_token فقط مسیرِ وب‌هوکِ همین دکان است (`/api/v1/payments/webhook/hesabpay/<token>`)؛ اعتبار
--     همچنان فقط امضای بدنه با رازِ همین دکان.
--
--  فقط افزودنی — هیچ جدولِ موجودی دست نمی‌خورد. بازگشت: `rollback/204_shop_hesabpay.down.sql`.
-- ============================================================

CREATE TABLE IF NOT EXISTS shop_hesabpay (
  shop_id           text    PRIMARY KEY REFERENCES shops(id) ON DELETE CASCADE,
  mode              text    NOT NULL DEFAULT 'off' CHECK (mode IN ('off','simple','auto')),
  --  ساده: شمارهٔ یک‌دست (07XXXXXXXX) و/یا متنِ QR ِ خودِ دکان (حداکثر ۵۱۲ نویسه)
  account           text    NOT NULL DEFAULT '',
  qr_payload        text    NOT NULL DEFAULT '',
  --  خودکار: فقط رمزشده (v1.<iv>.<متن>.<برچسب>)؛ key_last4 تنها چیزی است که دیده می‌شود
  api_key_enc       text    NOT NULL DEFAULT '',
  secret_enc        text    NOT NULL DEFAULT '',
  merchant_enc      text    NOT NULL DEFAULT '',
  key_last4         text    NOT NULL DEFAULT '',
  webhook_token     text    UNIQUE,
  --  رمزِ حساب‌پی (جدا از رمزِ برنامه و ورود)
  pin_hash          text    NOT NULL DEFAULT '',
  pin_failures      integer NOT NULL DEFAULT 0,
  pin_locked_until  bigint  NOT NULL DEFAULT 0,
  pin_changed_at    bigint,
  hold_until        bigint  NOT NULL DEFAULT 0,
  version           integer NOT NULL DEFAULT 1,
  updated_by        text    NOT NULL DEFAULT '',
  created_at        bigint  NOT NULL,
  updated_at        bigint  NOT NULL
);
