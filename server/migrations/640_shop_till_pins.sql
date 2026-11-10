-- ============================================================
--  ۶۴۰ — PIN ِ شخصیِ صندوق در سرور و نوبت‌های سنجیدهٔ صندوق (STF-04، S46؛ ۱۴۰۵/۰۸/۱۹)
--
--  تا امروز PIN ِ کاربرِ صندوق فقط روی همان دستگاه بود (`ApproverVault`) و سرور هر op را به نامِ حسابِ دستگاه
--  می‌دید: «چه کسی پشتِ صندوق بود» در سرور یا ادعای بی‌سنجشِ دستگاه بود یا هیچ. حالا:
--    shop_till_pins      اثرِ PBKDF2 ِ PIN ِ هر عضو (هرگز خودِ PIN)، شمارِ خطا و قفلِ زمان‌دارِ سرور
--    shop_till_sessions  هر بار که سرور PIN ِ عضوی را روی دستگاهِ توکنی درست سنجید یک «نوبت» باز می‌شود و با
--                        تعویض، قفل، برداشتنِ PIN یا پایانِ عمر بسته می‌شود — دستگاه از ردیفِ توکن (`devices`)،
--                        نه از چیزی که برنامه می‌فرستد
--  ⛔ پشتِ پرچمِ خاموشِ `shop.till_pin` (lib/till-pin.js): خاموش ⇒ هیچ ردیفی نوشته یا خوانده نمی‌شود.
--  ⛔ `member_id` کلیدِ بیگانه ندارد (همان قاعدهٔ `staff_punches`): سابقهٔ نوبت با رفتنِ عضو نمی‌رود.
--  فقط افزودنی. rollback: migrations/rollback/640_shop_till_pins.down.sql
-- ============================================================

CREATE TABLE IF NOT EXISTS shop_till_pins (
  shop_id      text    NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  member_id    text    NOT NULL,
  --  pbkdf2-sha256$<دور>$<نمک>$<اثر> — خودِ PIN هیچ‌جا
  pin_hash     text    NOT NULL,
  failures     integer NOT NULL DEFAULT 0 CHECK (failures >= 0),
  locked_until bigint  NOT NULL DEFAULT 0,
  set_by       text    NOT NULL DEFAULT '',
  set_at       bigint  NOT NULL,
  PRIMARY KEY (shop_id, member_id)
);

CREATE TABLE IF NOT EXISTS shop_till_sessions (
  shop_id          text   NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  id               text   NOT NULL,
  till_member_id   text   NOT NULL,
  --  عضوِ توکنی که PIN را فرستاد (حسابِ دستگاه) و دستگاهِ همان توکن
  device_member_id text   NOT NULL,
  device_id        text   NOT NULL,
  device_uid       text   NOT NULL DEFAULT '',
  started_at       bigint NOT NULL,
  expires_at       bigint NOT NULL,
  ended_at         bigint,
  end_reason       text   NOT NULL DEFAULT ''
    CHECK (end_reason IN ('', 'switch', 'lock', 'self', 'pin_removed', 'expired')),
  PRIMARY KEY (shop_id, id)
);
--  حداکثر یک نوبتِ باز برای هر دستگاهِ دکان (تعویض نوبتِ پیشین را می‌بندد)
CREATE UNIQUE INDEX IF NOT EXISTS ux_shop_till_sessions_open
  ON shop_till_sessions(shop_id, device_id) WHERE ended_at IS NULL;
CREATE INDEX IF NOT EXISTS ix_shop_till_sessions_started ON shop_till_sessions(shop_id, started_at);
CREATE INDEX IF NOT EXISTS ix_shop_till_sessions_member ON shop_till_sessions(shop_id, till_member_id, started_at);
