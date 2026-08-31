-- ============================================================
--  خبرهای دکان
--
--  قرار صاحب مخزن: «هر پیامی که در جای اعلانات برنامه‌ی شاگرد می‌آید،
--  برای من هم بیاید — هر جا که باشم — و درگاهش سرور خودم باشد.»
--
--  تا امروز هشدارها (کالای تمام‌شده، قرض‌دار، پشتیبان) فقط روی همان
--  گوشی حساب می‌شدند و از روی دفتر محلی. یعنی صاحب دکانی که خانه بود
--  نمی‌دانست شاگردش چه فروخته یا چه چیزی تمام شده.
--
--  حالا خبرها روی سرور می‌نشینند و هر عضو دکان می‌تواند بخواندشان.
-- ============================================================

CREATE TABLE IF NOT EXISTS shop_events (
  id          text   PRIMARY KEY,
  shop_id     text   NOT NULL REFERENCES shops(id) ON DELETE CASCADE,

  --  چه کسی باعثش شد. برای خبرهای خودکارِ سرور خالی است.
  user_id     text   NOT NULL DEFAULT '',
  user_name   text   NOT NULL DEFAULT '',

  --  sale | stock_out | low_stock | expense | note
  kind        text   NOT NULL,
  title       text   NOT NULL DEFAULT '',
  body        text   NOT NULL DEFAULT '',

  --  هر چیز دیگری که به درد نمایش می‌خورد (مبلغ، شناسه‌ی کالا، …)
  data        jsonb  NOT NULL DEFAULT '{}'::jsonb,

  --  شناسه‌ی رویداد در خود برنامه؛ با آن، فرستادن دوباره‌ی همان خبر
  --  ردیف تکراری نمی‌سازد. گوشی‌ای که آفلاین بوده و صف را یک‌جا
  --  می‌فرستد، دو بار خبر نمی‌دهد.
  client_id   text   NOT NULL DEFAULT '',

  created_at  bigint NOT NULL
);

--  خواندن همیشه «از این شماره به بعد، برای این دکان» است
CREATE INDEX IF NOT EXISTS idx_events_shop ON shop_events(shop_id, created_at DESC);
CREATE UNIQUE INDEX IF NOT EXISTS idx_events_client
  ON shop_events(shop_id, client_id) WHERE client_id <> '';

--  تا کجا خوانده‌ام — برای هر عضو جدا، تا نقطه‌ی قرمزِ زنگ درست باشد
CREATE TABLE IF NOT EXISTS shop_event_reads (
  shop_id   text   NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  user_id   text   NOT NULL,
  seen_at   bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (shop_id, user_id)
);
