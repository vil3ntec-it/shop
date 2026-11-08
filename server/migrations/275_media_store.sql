-- ============================================================
--  275 — رسانهٔ چتِ دکان، رسانهٔ پشتیبانی و مدرکِ پروندهٔ اعتماد در انبارِ عکس (`lib/media-store.js`، `claude/scale-4`).
--
--  همان الگوی ۲۵۵ (docs/SCALING-fa.md §۳ «انبارِ عکس»): هر ردیف بایت را در `data` دارد (حالتِ `db`، پیش‌فرض و
--  بیت‌به‌بیت پیشین) یا نشانیِ شیء را در `store_key` (حالتِ `disk`/`s3`) — یا هر دو، پس از `copy` و پیش از `purge`.
--  ⛔ `station_chat_media` (پمپ) دست نمی‌خورد.
--  فقط افزودنی: ستونِ تازه با پیش‌فرضِ '' (بی بازنویسیِ جدول در PostgreSQL 11+)، `data` nullable با CHECK ِ «یکی از دو»
--  (NOT VALID: ردیف‌های امروز همه `data` دارند)، و نمایهٔ جزئیِ کلید برای `gc`.
--  برگشت: migrations/rollback/275_media_store.down.sql
-- ============================================================

ALTER TABLE shop_chat_media ADD COLUMN IF NOT EXISTS store_key text NOT NULL DEFAULT '';
ALTER TABLE support_media   ADD COLUMN IF NOT EXISTS store_key text NOT NULL DEFAULT '';
ALTER TABLE trust_evidence  ADD COLUMN IF NOT EXISTS store_key text NOT NULL DEFAULT '';

ALTER TABLE shop_chat_media ALTER COLUMN data DROP NOT NULL;
ALTER TABLE support_media   ALTER COLUMN data DROP NOT NULL;
ALTER TABLE trust_evidence  ALTER COLUMN data DROP NOT NULL;

ALTER TABLE shop_chat_media DROP CONSTRAINT IF EXISTS shop_chat_media_bytes;
ALTER TABLE shop_chat_media ADD CONSTRAINT shop_chat_media_bytes CHECK (data IS NOT NULL OR store_key <> '') NOT VALID;
ALTER TABLE support_media   DROP CONSTRAINT IF EXISTS support_media_bytes;
ALTER TABLE support_media   ADD CONSTRAINT support_media_bytes CHECK (data IS NOT NULL OR store_key <> '') NOT VALID;
ALTER TABLE trust_evidence  DROP CONSTRAINT IF EXISTS trust_evidence_bytes;
ALTER TABLE trust_evidence  ADD CONSTRAINT trust_evidence_bytes CHECK (data IS NOT NULL OR store_key <> '') NOT VALID;

CREATE INDEX IF NOT EXISTS ix_shop_chat_media_store_key ON shop_chat_media (store_key) WHERE store_key <> '';
CREATE INDEX IF NOT EXISTS ix_support_media_store_key   ON support_media (store_key)   WHERE store_key <> '';
CREATE INDEX IF NOT EXISTS ix_trust_evidence_store_key  ON trust_evidence (store_key)  WHERE store_key <> '';
