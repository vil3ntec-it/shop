-- ============================================================
--  شناسهٔ عمومیِ پایدارِ دکان و نشانی‌های پیشین — S3 ِ قراردادِ بازار
--  (docs/MARKETPLACE-CONTRACT-fa.md §۳، §۱.۱؛ §۴۰.۴)
--
--  ۱) `storefronts.public_id`: ULID، یکتا، تغییرناپذیر. ردیف‌های موجود همین‌جا
--     پر می‌شوند (بخشِ زمانِ ULID = `created_at` ِ همان ردیف). ردیفِ تازه را
--     تریگر پر می‌کند، پس هیچ مسیرِ INSERT ِ امروز عوض نمی‌شود.
--     ⛔ تغییرِ آن را تریگر رد می‌کند: لینکِ کهنه، تبلیغ و API به آن تکیه می‌کنند.
--  ۲) `storefront_slug_redirects`: «نشانیِ پیشین ⇐ دکان». نشانیِ تازه هر بار
--     از `storefronts.slug` ِ همان دکان خوانده می‌شود، پس زنجیره ساخته نمی‌شود.
--     ⛔ نشانیِ زنده بر نشانیِ پیشین مقدم است: دکانی که نشانیِ آزادشده را بگیرد
--     ردیفِ پیشین پاک می‌شود (`lib/storefront.js`، `saveSettings`).
--
--  ⛔ پاسخ‌های امروز عوض نمی‌شوند: خواندنِ این دو فقط پشتِ پرچمِ
--  `market.public_id` (پیش‌فرض خاموش). نوشتنِ نشانیِ پیشین از همین حالا هست تا
--  روزِ روشن شدن، تاریخچه موجود باشد.
--
--  Rollback: migrations/rollback/131_storefront_public_id.down.sql
-- ============================================================

CREATE OR REPLACE FUNCTION storefront_public_ulid(ms bigint) RETURNS text
LANGUAGE plpgsql VOLATILE AS $$
DECLARE
  alphabet constant text := '0123456789ABCDEFGHJKMNPQRSTVWXYZ';
  t   bigint := GREATEST(COALESCE(ms, 0), 0);
  res text := '';
  rnd bytea;
  i   int;
BEGIN
  FOR i IN 1..10 LOOP
    res := substr(alphabet, (t % 32)::int + 1, 1) || res;
    t := t / 32;
  END LOOP;
  --  ۸۰ بیتِ تصادفی از gen_random_uuid (pg_strong_random)؛ بایت‌های نسخه و
  --  گونهٔ UUID (۶ و ۸) کنار می‌روند. ۲۵۶ بر ۳۲ بخش‌پذیر است ⇒ یکنواخت.
  rnd := decode(replace(gen_random_uuid()::text, '-', ''), 'hex');
  FOR i IN 0..15 LOOP
    IF i NOT IN (6, 8) THEN
      res := res || substr(alphabet, (get_byte(rnd, i) % 32) + 1, 1);
    END IF;
  END LOOP;
  rnd := decode(replace(gen_random_uuid()::text, '-', ''), 'hex');
  FOR i IN 0..1 LOOP
    res := res || substr(alphabet, (get_byte(rnd, i) % 32) + 1, 1);
  END LOOP;
  RETURN res;
END $$;

ALTER TABLE storefronts ADD COLUMN IF NOT EXISTS public_id text;
UPDATE storefronts SET public_id = storefront_public_ulid(created_at) WHERE public_id IS NULL;
ALTER TABLE storefronts ALTER COLUMN public_id SET NOT NULL;
ALTER TABLE storefronts ADD CONSTRAINT storefronts_public_id_format
  CHECK (public_id ~ '^[0-9A-HJKMNP-TV-Z]{26}$');
CREATE UNIQUE INDEX IF NOT EXISTS ux_storefronts_public_id ON storefronts(public_id);

CREATE OR REPLACE FUNCTION storefronts_public_id_guard() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  IF TG_OP = 'INSERT' THEN
    IF NEW.public_id IS NULL THEN
      NEW.public_id := storefront_public_ulid(NEW.created_at);
    END IF;
  ELSIF NEW.public_id IS DISTINCT FROM OLD.public_id THEN
    RAISE EXCEPTION 'storefronts.public_id is immutable' USING ERRCODE = 'check_violation';
  END IF;
  RETURN NEW;
END $$;

CREATE TRIGGER storefronts_public_id_guard
  BEFORE INSERT OR UPDATE ON storefronts
  FOR EACH ROW EXECUTE FUNCTION storefronts_public_id_guard();

CREATE TABLE IF NOT EXISTS storefront_slug_redirects (
  old_slug    text    PRIMARY KEY CHECK (old_slug ~ '^[a-z0-9][a-z0-9-]{1,30}[a-z0-9]$'),
  shop_id     text    NOT NULL REFERENCES storefronts(shop_id) ON DELETE CASCADE,
  created_at  bigint  NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_storefront_slug_redirects_shop ON storefront_slug_redirects(shop_id);
