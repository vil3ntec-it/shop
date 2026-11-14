-- ============================================================
--  912 — نشانی‌های دیگرِ ویترینِ یک دکان (دامنهٔ خودِ دکان؛ `lib/storefront-hosts.js`، docs/SERVERS-fa.md §۳).
--  فقط دکان. دو کلیدِ خاموش: `STOREFRONT_ALIAS_DOMAINS` ِ سرور و پرچمِ `shop.storefront.host_alias` ِ دکان؛ بی آن‌ها این
--  جدول خوانده نمی‌شود. `host` یکتا در کلِ سرور (کلیدِ اصلی): دو دکان یک نشانی ندارند. نامِ مرتب و کوچک (CHECK).
-- ============================================================
CREATE TABLE IF NOT EXISTS storefront_hosts (
  host        TEXT   PRIMARY KEY CHECK (host = lower(host) AND length(host) BETWEEN 3 AND 253),
  shop_id     TEXT   NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  created_at  BIGINT NOT NULL,
  created_by  TEXT
);
CREATE INDEX IF NOT EXISTS ix_storefront_hosts_shop ON storefront_hosts(shop_id);
