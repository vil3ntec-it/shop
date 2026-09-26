-- ============================================================
--  باتِ تلگرام — چند شعبه (چند حسابِ پمپ) در یک گفت‌وگو
--
--  خواستهٔ صاحب سامانه (۱۴۰۵/۰۷/۱۴): «می‌خوام چندین حساب رو توی یک پمپ
--  بذارم… یکی شاید چندین شعبه داشته باشه و می‌خواد همه رو توی همون گروه یا
--  کانال ببینه… پیام‌ها و حساب‌ها قاطی نشن… دو اسمِ شبیه به هم دلیلی نمی‌شه
--  یکی باشن.»
--
--  هر ردیف یعنی «این گفت‌وگو هشدارهای این پمپ را می‌گیرد، به دستِ این
--  حساب». ⛔ کلیدِ هر ردیف (گفت‌وگو، پمپ) است، پس هر شعبه جدا می‌ماند و هیچ
--  دادهٔ دو شعبه با هم یکی نمی‌شود. `telegram_chats.station_id/user_id`
--  همان نخستین پیوند می‌ماند (برای سازگاری) — تصمیمِ «به کجا برود» از این‌جاست.
-- ============================================================
CREATE TABLE IF NOT EXISTS telegram_chat_links (
  chat_id        text   NOT NULL REFERENCES telegram_chats(chat_id) ON DELETE CASCADE ON UPDATE CASCADE,
  station_id     text   NOT NULL REFERENCES stations(id) ON DELETE CASCADE,
  user_id        text   NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  linked_at      bigint NOT NULL,
  --  اعلامیهٔ صبح: هر شعبه جدا، هر روز یک بار
  announced_day  text,
  PRIMARY KEY (chat_id, station_id)
);
CREATE INDEX IF NOT EXISTS idx_telegram_chat_links_station ON telegram_chat_links(station_id);

--  پیوندهای امروز همان لحظه این‌جا می‌آیند
INSERT INTO telegram_chat_links (chat_id, station_id, user_id, linked_at, announced_day)
SELECT chat_id, station_id, user_id, linked_at, announced_day
  FROM telegram_chats
 WHERE station_id IS NOT NULL AND user_id IS NOT NULL AND linked_at IS NOT NULL
ON CONFLICT DO NOTHING;

--  «افزودن به گروه» از گفت‌وگوی خصوصی‌ای ساخته می‌شود که شاید چند شعبه دارد:
--  همهٔ شعبه‌های همان گفت‌وگو به گروه می‌روند.
ALTER TABLE telegram_link_tokens ADD COLUMN IF NOT EXISTS from_chat text;
