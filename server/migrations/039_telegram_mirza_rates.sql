-- ============================================================
--  باتِ تلگرام — «💬 چت‌های میرزا» و «نرخِ اتحادیه از تلگرام»
--
--  خواستهٔ صاحب سامانه (۱۴۰۵/۰۷/۱۶): «توی منو قابلیتِ جدید بذار به اسمِ
--  چت‌های میرزا… بات کدِ هشت‌رقمیِ برنامه رو بخواد… وقتی برنامهٔ کامپیوتر
--  خاموشه، پیام‌های مشتری‌های کیو‌آر بیاد توی تلگرام… و نرخِ اتحادیه رو از
--  تلگرام بنویسم و اتومات توی برنامهٔ کامپیوتر لایف بشینه… روی حساب‌های
--  کاربرانِ دیگه تأثیری نذاره.»
-- ============================================================

--  کدام گفت‌وگوی تلگرام پیامِ مشتری‌های کیو‌آرِ کدام پمپ را می‌گیرد.
--  ⛔ یک پمپ، یک مقصد (کلید = پمپ) — دو مقصد یعنی دو نفر به یک مشتری جواب
--  می‌دهند و هیچ‌کدام از دیگری خبر ندارد.
CREATE TABLE IF NOT EXISTS telegram_relays (
  station_id  text   PRIMARY KEY REFERENCES stations(id) ON DELETE CASCADE,
  chat_id     text   NOT NULL REFERENCES telegram_chats(chat_id) ON DELETE CASCADE ON UPDATE CASCADE,
  --  شناسهٔ تلگرامیِ کسی که کد را زد — فقط برای دفتر، نه برای دسترسی
  by_tg       text   NOT NULL DEFAULT '',
  linked_at   bigint NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_telegram_relays_chat ON telegram_relays(chat_id);

--  پیامِ تلگرامی که از یک مشتری آمده ⇒ جوابی که روی آن «Reply» شود به همان
--  مشتری برمی‌گردد. ⛔ کلید (گفت‌وگو، پیام) است، پس پیامِ گفت‌وگوی دیگر هرگز
--  به گفت‌وگوی مشتریِ این پمپ نمی‌رسد.
CREATE TABLE IF NOT EXISTS telegram_relay_msgs (
  chat_id     text   NOT NULL,
  message_id  bigint NOT NULL,
  station_id  text   NOT NULL REFERENCES stations(id) ON DELETE CASCADE,
  acct        text   NOT NULL,
  created_at  bigint NOT NULL,
  PRIMARY KEY (chat_id, message_id)
);
CREATE INDEX IF NOT EXISTS idx_telegram_relay_msgs_age ON telegram_relay_msgs(created_at);

--  فرمانِ «نرخِ اتحادیه» برای **یک** پمپ. برنامهٔ کامپیوترِ همان پمپ با
--  توکنِ دستگاهِ خودش می‌گیرد، می‌نشاند و «نشست» می‌گوید.
--  status: pending ⇒ applied | rejected | superseded
CREATE TABLE IF NOT EXISTS station_rate_cmds (
  id          text    PRIMARY KEY,
  station_id  text    NOT NULL REFERENCES stations(id) ON DELETE CASCADE,
  petrol      numeric,
  diesel      numeric,
  status      text    NOT NULL DEFAULT 'pending',
  chat_id     text    NOT NULL DEFAULT '',
  by_name     text    NOT NULL DEFAULT '',
  note        text    NOT NULL DEFAULT '',
  created_at  bigint  NOT NULL,
  done_at     bigint
);
CREATE INDEX IF NOT EXISTS idx_station_rate_cmds_pending
  ON station_rate_cmds(station_id, created_at) WHERE status = 'pending';

--  «کدِ هشت‌رقمی» در گفت‌وگویی که منتظرش است — شمارندهٔ جدا از کدِ ایمیل،
--  تا حدس زدنِ کدِ پمپ از تلگرام سقف داشته باشد.
ALTER TABLE telegram_chats ADD COLUMN IF NOT EXISTS relay_tries  integer NOT NULL DEFAULT 0;
ALTER TABLE telegram_chats ADD COLUMN IF NOT EXISTS relay_window bigint  NOT NULL DEFAULT 0;
ALTER TABLE telegram_chats ADD COLUMN IF NOT EXISTS relay_by     text    NOT NULL DEFAULT '';

--  ⛔ هشِ همان کدِ هشت‌رقمی که با آن وصل شد: صاحبِ پمپ که کد را در پروفایل
--  عوض کند، همهٔ «چت‌های میرزا»ی قبلی همان لحظه خاموش می‌شوند.
ALTER TABLE telegram_relays ADD COLUMN IF NOT EXISTS code_hash text NOT NULL DEFAULT '';

--  👮 مدیرانِ نرخ — فقط این شناسه‌های تلگرام نرخِ اتحادیهٔ همین پمپ را عوض
--  می‌کنند (خواستهٔ صاحب سامانه: «هر کس و ناکس نتونه بگه این نرخ رو بذار»).
--  صاحبِ پمپ (با ایمیل وصل) فهرست را می‌سازد و خودش هم همیشه مجاز است.
--  ⚠️ شناسهٔ عددی مرجع است؛ نامِ کاربری (@…) عوض‌شدنی است و فقط کمکی است.
CREATE TABLE IF NOT EXISTS station_rate_admins (
  id          text   PRIMARY KEY,
  station_id  text   NOT NULL REFERENCES stations(id) ON DELETE CASCADE,
  tg_id       text   NOT NULL DEFAULT '',
  tg_username text   NOT NULL DEFAULT '',
  label       text   NOT NULL DEFAULT '',
  added_by    text   NOT NULL DEFAULT '',
  added_at    bigint NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_station_rate_admins
  ON station_rate_admins(station_id, tg_id, tg_username);
CREATE INDEX IF NOT EXISTS idx_station_rate_admins_tg ON station_rate_admins(tg_id) WHERE tg_id <> '';

--  گفت‌وگویی که منتظرِ «شناسه‌های مدیرِ نرخِ کدام پمپ» است
ALTER TABLE telegram_chats ADD COLUMN IF NOT EXISTS pending_station text NOT NULL DEFAULT '';
