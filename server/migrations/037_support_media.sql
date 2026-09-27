-- ============================================================
--  رسانه در پشتیبانیِ پمپ — عکس، ویدیو و پیامِ صوتی، فقط در عبور
-- ------------------------------------------------------------
--  خواستهٔ صاحب سامانه (۱۴۰۵/۰۷/۱۵): «در چتِ پشتیبانیِ برنامهٔ پمپ عکس،
--  صدا و ویدیو نمی‌رود… ولی این‌ها نباید روی سرور بمانند — سرور فقط
--  به طرفِ دیگر می‌رساند و هر طرف روی دستگاهِ خودش نگه می‌دارد.»
--
--  پس `support_media` بایگانی نیست، جای عبور است:
--    • هر ردیف همان لحظه که **گیرنده** (طرفِ مقابلِ فرستنده) کاملش را
--      گرفت پاک می‌شود — `uploader` برای همین است.
--    • نگرفت ⇒ `chat-relay.sweep()` پس از CHAT_RELAY_DAYS می‌بردش، و
--      بارگذاریِ بی‌پیام پس از یک ساعت.
--
--  ⚠️ `support_messages.media_id` عمداً کلیدِ خارجی ندارد: رسانه پس از
--  رسیدن پاک می‌شود ولی پیام می‌ماند و شناسه‌اش همان است — برنامهٔ
--  فرستنده رسانهٔ خودش را با همین شناسه در دفترِ محلی‌اش پیدا می‌کند.
--  گرفتنِ دوباره ⇒ ۴۰۴ِ `media_gone`.
-- ============================================================

ALTER TABLE support_messages ADD COLUMN IF NOT EXISTS media_id text;

CREATE TABLE IF NOT EXISTS support_media (
  id          text    PRIMARY KEY,
  thread_id   text    NOT NULL REFERENCES support_threads(id) ON DELETE CASCADE,
  uploader    text    NOT NULL CHECK (uploader IN ('user','admin')),
  mime        text    NOT NULL,
  size        integer NOT NULL,
  data        bytea   NOT NULL,
  created_at  bigint  NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_support_media_thread  ON support_media(thread_id);
CREATE INDEX IF NOT EXISTS idx_support_media_created ON support_media(created_at);

--  «این رسانه هنوز پیامی دارد؟» — پرسشِ پاک‌سازیِ یتیم‌ها
CREATE INDEX IF NOT EXISTS idx_support_messages_media
  ON support_messages(media_id)
  WHERE media_id IS NOT NULL;
