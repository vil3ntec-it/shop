-- ============================================================
--  گزارشِ مشکلِ سفارش با عکس (NTF-07) و امتیاز و نظرِ سفارش با بازبینی (NTF-09) — v3 §۶.۱۹
--
--  ⛔ پشتِ دو پرچمِ خاموشِ `shop.orders.issue_reports` (`lib/order-issues.js`) و `shop.orders.reviews`
--  (`lib/order-reviews.js`): تا روشن نشوند هیچ ردیفی نوشته نمی‌شود.
--
--  order_issues        مشتری با همان توکنِ پیگیری؛ حداکثر ۳ برای هر سفارش؛ دکان با پاسخ «رسیدگی شد» می‌زند.
--  order_issue_photos  حداکثر ۳ برای هر گزارش؛ نوع از محتوا، سقفِ اندازه و ابعاد، بی EXIF/GPS؛ فقط دکانِ همان
--                      سفارش بایت‌ها را می‌بیند (خصوصی، هرگز نشانیِ عمومی).
--  order_reviews       یک نظر برای هر سفارش (UNIQUE)، فقط پس از تحویل؛ پیش از تأییدِ دکان هیچ‌جا منتشر نمی‌شود.
--
--  فقط افزودنی. برگشت: DROP TABLE order_issue_photos, order_issues, order_reviews.
-- ============================================================

CREATE TABLE IF NOT EXISTS order_issues (
  id            text    PRIMARY KEY,                       -- ULID
  shop_id       text    NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  order_id      text    NOT NULL REFERENCES online_orders(id) ON DELETE CASCADE,
  kind          text    NOT NULL CHECK (kind IN ('missing_item','wrong_item','damaged','quality','late','other')),
  body          text    NOT NULL,
  status        text    NOT NULL DEFAULT 'open' CHECK (status IN ('open','resolved')),
  reply         text    NOT NULL DEFAULT '',
  resolved_by   text    NOT NULL DEFAULT '',
  resolved_at   bigint,
  request_key   text    NOT NULL,
  request_hash  text    NOT NULL,
  created_at    bigint  NOT NULL,
  UNIQUE (order_id, request_key)
);
CREATE INDEX IF NOT EXISTS ix_order_issues_shop ON order_issues(shop_id, status, created_at);

CREATE TABLE IF NOT EXISTS order_issue_photos (
  id          text     PRIMARY KEY,                        -- ULID
  shop_id     text     NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  issue_id    text     NOT NULL REFERENCES order_issues(id) ON DELETE CASCADE,
  mime        text     NOT NULL,
  size        integer  NOT NULL,
  width       integer  NOT NULL,
  height      integer  NOT NULL,
  hash        text     NOT NULL,                            -- sha256 ِ بایت‌های رسیده (تکرار ⇒ همان عکس)
  data        bytea    NOT NULL,                            -- بی فراداده
  created_at  bigint   NOT NULL,
  UNIQUE (issue_id, hash)
);

CREATE TABLE IF NOT EXISTS order_reviews (
  id            text      PRIMARY KEY,                     -- ULID
  shop_id       text      NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  order_id      text      NOT NULL UNIQUE REFERENCES online_orders(id) ON DELETE CASCADE,
  stars         smallint  NOT NULL CHECK (stars BETWEEN 1 AND 5),
  comment       text      NOT NULL DEFAULT '',
  status        text      NOT NULL DEFAULT 'pending' CHECK (status IN ('pending','approved','rejected')),
  request_hash  text      NOT NULL,
  moderated_by  text      NOT NULL DEFAULT '',
  moderated_at  bigint,
  created_at    bigint    NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_order_reviews_shop ON order_reviews(shop_id, status, created_at);
