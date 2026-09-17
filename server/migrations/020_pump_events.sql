-- ============================================================
--  خبرهای پمپ — همان دفتری که دکان از روزِ اول داشت
--
--  خواستهٔ صاحب مخزن: «ببین برنامه‌ها جوری استن که بسته هم باشن هر
--  اتفاقی که تو برنامه بوفته کم‌بودی یا هر چی به سرور ارسال میشه و
--  سرور وقتی که برنامه‌ها بسته هم باشن براشون میده پیام‌ها رو.»
--
--  ⛔ این تا امروز **فقط برای دکان** بود. بخشِ پمپ هیچ دفترِ خبری روی
--  ابر نداشت: «اضافه برد» و «کم مانده» فقط روی سرورِ خانگی می‌نشستند و
--  گوشیِ کارمند هر پانزده دقیقه از **همان شبکه** می‌پرسید. یعنی صاحبِ
--  پمپی که بیرون بود — یا مودمش خاموش بود — هیچ‌وقت خبر نمی‌گرفت، و
--  برنامهٔ بسته هم هیچ زنگی نداشت.
--
--  ⚠️ دو جدولِ جدا، نه یک جدول با ستونِ `app`: همان قاعدهٔ همیشگیِ این
--  سرور (`lib/tenancy.js`). ردیفی که کلیدِ خارجی‌اش به `stations`
--  می‌خورد هیچ‌وقت نمی‌تواند به دکانی برسد، و پاک شدنِ یک پمپ خبرهایش
--  را هم با خودش می‌برد.
-- ============================================================

CREATE TABLE IF NOT EXISTS station_events (
  id          text   PRIMARY KEY,
  station_id  text   NOT NULL REFERENCES stations(id) ON DELETE CASCADE,

  --  چه کسی باعثش شد. کامپیوترِ پمپ حساب ندارد، پس برای او خالی است و
  --  `device_uid` می‌گوید کدام کامپیوتر بود.
  user_id     text   NOT NULL DEFAULT '',
  user_name   text   NOT NULL DEFAULT '',
  device_uid  text   NOT NULL DEFAULT '',

  --  sale | stock_out | low_stock | expense | debt | note
  kind        text   NOT NULL,
  title       text   NOT NULL DEFAULT '',
  body        text   NOT NULL DEFAULT '',
  data        jsonb  NOT NULL DEFAULT '{}'::jsonb,

  --  کلیدِ خودِ برنامه. صفِ آفلاین که یک‌جا می‌رسد، ردیفِ تکراری
  --  نمی‌سازد — همان `k`ی `StationSnapshot.Alerts` که حال را هم در خود
  --  دارد، پس «کم مانده ⇒ تمام شد» خبرِ تازه است و همان حال دوباره نه.
  client_id   text   NOT NULL DEFAULT '',

  created_at  bigint NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_station_events_station
  ON station_events(station_id, created_at DESC);
CREATE UNIQUE INDEX IF NOT EXISTS idx_station_events_client
  ON station_events(station_id, client_id) WHERE client_id <> '';

--  تا کجا خوانده‌ام — برای هر عضو جدا
CREATE TABLE IF NOT EXISTS station_event_reads (
  station_id  text   NOT NULL REFERENCES stations(id) ON DELETE CASCADE,
  user_id     text   NOT NULL,
  seen_at     bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (station_id, user_id)
);

-- ============================================================
--  بازدیدکننده‌ها: پمپِ همان دستگاه هم ثبت شود
--
--  ⛔ `app_visitors` فقط `shop_id` داشت، و `routes/visit.js` عضویت را
--  همیشه از `lib/shops` می‌خواند. یعنی تپشِ برنامهٔ پمپ در پنل به شکلِ
--  «مهمانِ بی‌حساب» می‌نشست، حتی وقتی صاحبِ پمپ وارد شده بود — و مدیر
--  نمی‌توانست ببیند کدام پمپ زنده است.
-- ============================================================
ALTER TABLE app_visitors ADD COLUMN IF NOT EXISTS station_id text NOT NULL DEFAULT '';
CREATE INDEX IF NOT EXISTS idx_visitors_station ON app_visitors(station_id)
  WHERE station_id <> '';
