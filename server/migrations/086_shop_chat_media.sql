-- ============================================================
--  چتِ مشتری ↔ دکان — رسانه و گزارشِ سوءاستفاده (v3 §۳۳.۲، §۳۳.۳)
--
--  ⛔ رسانه جدای پیام است (CHAT-12: پیام و رسانه جدا پشتیبان/بازیابی
--  می‌شوند) و فقط به گفت‌وگوی خودش بسته است: شناسهٔ حدس‌زده‌شده به‌تنهایی
--  هیچ دری باز نمی‌کند (CHAT-09).
--  ⛔ نوع از روی **بایت‌ها** شناخته می‌شود، نه از سرآیندِ کلاینت؛ SVG/HTML/
--  فایلِ اجرایی هرگز پذیرفته نمی‌شود (CHAT-10). EXIF ِ مکان پیش از ذخیره
--  پاک می‌شود و فقط جهتِ عکس می‌ماند (§۳۳.۲ بندِ ۱).
--  ⛔ همان بایت‌ها از همان طرف در همان گفت‌وگو یک ردیف‌اند (sha256) — تکرارِ
--  بارگذاری دیسک را دوباره پر نمی‌کند و وجودِ فایلِ کسِ دیگری را لو نمی‌دهد.
--  ⛔ بارگذاریِ بی‌پیام پس از یک ساعت با `shop-chat.sweep()` می‌رود.
-- ============================================================

CREATE TABLE IF NOT EXISTS shop_chat_media (
  id                text    PRIMARY KEY,
  shop_id           text    NOT NULL,
  conversation_id   text    NOT NULL REFERENCES shop_chat_conversations(id) ON DELETE CASCADE,
  uploader          text    NOT NULL CHECK (uploader IN ('customer','shop')),
  uploader_user_id  text    NOT NULL DEFAULT '',
  role              text    NOT NULL CHECK (role IN ('main','thumb')),
  kind              text    NOT NULL CHECK (kind IN ('image','audio','file')),
  mime              text    NOT NULL,
  size              integer NOT NULL,
  sha256            text    NOT NULL,
  data              bytea   NOT NULL,
  created_at        bigint  NOT NULL,
  UNIQUE (conversation_id, uploader, role, sha256)
);
CREATE INDEX IF NOT EXISTS idx_shop_chat_media_created ON shop_chat_media(created_at);

--  گزارشِ سوءاستفاده — رونوشتِ همان پیام در لحظهٔ گزارش، تا «حذف برای همه»
--  مدرک را از بین نبرد. یک گزارش برای هر (پیام، طرف) — تکرار ردیفِ دوم نمی‌سازد.
CREATE TABLE IF NOT EXISTS shop_chat_reports (
  id                text    PRIMARY KEY,
  shop_id           text    NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  conversation_id   text    NOT NULL REFERENCES shop_chat_conversations(id) ON DELETE CASCADE,
  message_id        text    NOT NULL DEFAULT '',
  reporter          text    NOT NULL CHECK (reporter IN ('customer','shop')),
  reporter_user_id  text    NOT NULL DEFAULT '',
  reason            text    NOT NULL CHECK (reason IN ('spam','abuse','fraud','other')),
  note              text    NOT NULL DEFAULT '',
  snapshot          jsonb   NOT NULL DEFAULT '{}'::jsonb,
  status            text    NOT NULL DEFAULT 'open' CHECK (status IN ('open','reviewed')),
  created_at        bigint  NOT NULL,
  UNIQUE (conversation_id, message_id, reporter)
);
CREATE INDEX IF NOT EXISTS idx_shop_chat_reports_open ON shop_chat_reports(status, created_at DESC);
