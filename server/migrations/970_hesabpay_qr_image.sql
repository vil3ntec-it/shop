-- ============================================================
--  970 — عکسِ QR ِ حساب‌پیِ دکان (تصمیمِ صاحب‌کار ۱۴۰۵/۰۸/۲۶، `claude/e-paystep`؛ `lib/hesabpay-qr-image.js`)
--  «عکسِ QR ِ حساب‌پیم را بار می‌کنم و صندوق همان عکس را بزرگ نشان می‌دهد» — کنارِ متنِ QR ِ اسکن‌شده، نه به جایش.
--
--  shop_hesabpay.qr_image — هشِ sha256 ِ عکسِ فعال ('' = بی عکس)؛ عکسِ حسابِ در انتظار درونِ `pending` (jsonb) می‌ماند
--    و با همان فعال شدنِ ۲۴ ساعته (`hesabpay-guard.activateDue`) جای این می‌نشیند.
--  hesabpay_qr_images — بایت‌های عکس، نشانی از محتوا (shop_id، hash)؛ سرور دوباره رمزگذاری کرده (JPEG ِ بی EXIF،
--    ضلعِ بلند ≤ ۱۰۲۴) یا اگر رمزگشودنی نبود همان عکس بی فراداده؛ ≤ ۲ مگابایت. فقط عکسِ فعال به صندوق می‌رود
--    (`GET /shop/payments/qr-image/:hash`)؛ عکسی که نه فعال است نه در انتظار، با هر ذخیره/برداشتن پاک می‌شود.
--  ⛔ عکس هرگز گیرندهٔ پول نیست: فقط همراهِ متنِ QR ای پذیرفته می‌شود که شمارهٔ درونش همان شمارهٔ حساب است (همان
--    `parseAccount`)، و صندوق پیش از نشان دادن عکس را دوباره می‌خواند و فقط اگر همان متن بود نشان می‌دهد.
--  دکان پاک شود ⇒ عکس‌ها هم (ON DELETE CASCADE). بازگشت: `rollback/970_hesabpay_qr_image.down.sql`.
-- ============================================================
ALTER TABLE shop_hesabpay ADD COLUMN IF NOT EXISTS qr_image text NOT NULL DEFAULT '';

CREATE TABLE IF NOT EXISTS hesabpay_qr_images (
  shop_id     text    NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  hash        text    NOT NULL CHECK (hash ~ '^[0-9a-f]{64}$'),
  mime        text    NOT NULL CHECK (mime IN ('image/jpeg', 'image/png')),
  width       integer NOT NULL CHECK (width BETWEEN 1 AND 4096),
  height      integer NOT NULL CHECK (height BETWEEN 1 AND 4096),
  bytes       bytea   NOT NULL CHECK (octet_length(bytes) BETWEEN 1 AND 2097152),
  created_at  bigint  NOT NULL,
  created_by  text    NOT NULL DEFAULT '',
  PRIMARY KEY (shop_id, hash)
);
