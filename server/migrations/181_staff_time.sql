-- STF-07 و STF-08: زدن‌های ورود و خروجِ کارمند و نرخ‌های پورسانتِ دکان
-- (lib/staff-time.js، پشتِ پرچمِ خاموشِ live_config ِ `shop.staff_time`).
-- فقط افزودنی — هیچ داده‌ای عوض نمی‌شود. برگشت: DROP TABLE هر دو.
--
-- `member_id` کلیدِ بیگانه ندارد: عضوِ برداشته‌شده سابقهٔ کارش را نباید با خود ببرد،
-- و زدن نباید جلوی برداشتنِ عضو را بگیرد. شناسهٔ زدن ساختهٔ دستگاه است و در هر دکان
-- یکتاست، پس فرستادنِ دوباره (صفِ آفلاین) ردیفِ دوم نمی‌سازد.
CREATE TABLE IF NOT EXISTS staff_punches (
  shop_id      text   NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  id           text   NOT NULL,
  member_id    text   NOT NULL,
  kind         text   NOT NULL CHECK (kind IN ('in','out')),
  at           bigint NOT NULL,
  device_uid   text   NOT NULL DEFAULT '',
  by_member_id text   NOT NULL,
  created_at   bigint NOT NULL,
  PRIMARY KEY (shop_id, id)
);
CREATE INDEX IF NOT EXISTS ix_staff_punches_member ON staff_punches(shop_id, member_id, at);
CREATE INDEX IF NOT EXISTS ix_staff_punches_at ON staff_punches(shop_id, at);

-- نرخ‌ها به پایه‌امتیاز (۰ تا ۱۰٬۰۰۰)، همان `core/staff/Commission.Plan`.
CREATE TABLE IF NOT EXISTS shop_commission_plans (
  shop_id     text    PRIMARY KEY REFERENCES shops(id) ON DELETE CASCADE,
  default_bp  integer NOT NULL DEFAULT 0 CHECK (default_bp BETWEEN 0 AND 10000),
  seller_bp   jsonb   NOT NULL DEFAULT '{}'::jsonb,
  category_bp jsonb   NOT NULL DEFAULT '{}'::jsonb,
  version     integer NOT NULL DEFAULT 0,
  updated_by  text    NOT NULL DEFAULT '',
  updated_at  bigint  NOT NULL
);
