-- ============================================================
--  ۲۵۵ — انبارِ جداشدنیِ عکس (`lib/image-store.js`، `IMAGE_STORE`، docs/SCALING-fa.md §۳ «انبارِ عکس»).
--
--  هر جدولِ بایتِ عکس یک ستونِ `store_key` می‌گیرد: '' (پیش‌فرض) یعنی بایت در همان `data` است — رفتارِ امروز،
--  بیت‌به‌بیت. پر یعنی بایت در انبارِ شیء (دیسک یا S3) با همین کلید است؛ `data` یا null است (نوشتهٔ تازه در حالتِ
--  شیء، یا پس از `purge` ِ ابزار) یا هنوز رونوشتِ پایگاه (پس از `copy` و پیش از تأییدِ گرداننده).
--
--  `data` دیگر NOT NULL نیست؛ به جایش CHECK ِ «یکی از دو» (NOT VALID: ردیف‌های امروز همه بایت دارند و جدولِ
--  بزرگ برای سنجش دوباره خوانده نمی‌شود؛ ردیفِ تازه و هر UPDATE سنجیده می‌شوند). `product_photos.data` از آغاز
--  nullable بود (null = «برداشته»)؛ «برداشته» حالا `data IS NULL AND store_key = ''` است.
--
--  ستونِ تازه با پیش‌فرضِ ثابت فقط فراداده است (PostgreSQL ≥ ۱۱: بی بازنویسیِ جدول)؛ DROP NOT NULL هم.
--  ⛔ جدولِ پمپ (`station_chat_media`، مهاجرتِ ۰۱۳) دست نمی‌خورد.
--  rollback: پس از `node scripts/image-store-migrate.js rollback`، migrations/rollback/255_image_store.down.sql
-- ============================================================

ALTER TABLE product_photos     ADD COLUMN IF NOT EXISTS store_key text NOT NULL DEFAULT '';
ALTER TABLE photo_inbox        ADD COLUMN IF NOT EXISTS store_key text NOT NULL DEFAULT '';
ALTER TABLE storefront_media   ADD COLUMN IF NOT EXISTS store_key text NOT NULL DEFAULT '';
ALTER TABLE image_blobs        ADD COLUMN IF NOT EXISTS store_key text NOT NULL DEFAULT '';
ALTER TABLE order_issue_photos ADD COLUMN IF NOT EXISTS store_key text NOT NULL DEFAULT '';
ALTER TABLE order_pick_photos  ADD COLUMN IF NOT EXISTS store_key text NOT NULL DEFAULT '';
ALTER TABLE delivery_photos    ADD COLUMN IF NOT EXISTS store_key text NOT NULL DEFAULT '';

ALTER TABLE photo_inbox        ALTER COLUMN data DROP NOT NULL;
ALTER TABLE storefront_media   ALTER COLUMN data DROP NOT NULL;
ALTER TABLE image_blobs        ALTER COLUMN data DROP NOT NULL;
ALTER TABLE order_issue_photos ALTER COLUMN data DROP NOT NULL;
ALTER TABLE order_pick_photos  ALTER COLUMN data DROP NOT NULL;
ALTER TABLE delivery_photos    ALTER COLUMN data DROP NOT NULL;

ALTER TABLE photo_inbox        DROP CONSTRAINT IF EXISTS photo_inbox_bytes;
ALTER TABLE photo_inbox        ADD CONSTRAINT photo_inbox_bytes CHECK (data IS NOT NULL OR store_key <> '') NOT VALID;
ALTER TABLE storefront_media   DROP CONSTRAINT IF EXISTS storefront_media_bytes;
ALTER TABLE storefront_media   ADD CONSTRAINT storefront_media_bytes CHECK (data IS NOT NULL OR store_key <> '') NOT VALID;
ALTER TABLE image_blobs        DROP CONSTRAINT IF EXISTS image_blobs_bytes;
ALTER TABLE image_blobs        ADD CONSTRAINT image_blobs_bytes CHECK (data IS NOT NULL OR store_key <> '') NOT VALID;
ALTER TABLE order_issue_photos DROP CONSTRAINT IF EXISTS order_issue_photos_bytes;
ALTER TABLE order_issue_photos ADD CONSTRAINT order_issue_photos_bytes CHECK (data IS NOT NULL OR store_key <> '') NOT VALID;
ALTER TABLE order_pick_photos  DROP CONSTRAINT IF EXISTS order_pick_photos_bytes;
ALTER TABLE order_pick_photos  ADD CONSTRAINT order_pick_photos_bytes CHECK (data IS NOT NULL OR store_key <> '') NOT VALID;
ALTER TABLE delivery_photos    DROP CONSTRAINT IF EXISTS delivery_photos_bytes;
ALTER TABLE delivery_photos    ADD CONSTRAINT delivery_photos_bytes CHECK (data IS NOT NULL OR store_key <> '') NOT VALID;

--  `gc` ِ ابزار («این شیء ردیف دارد؟») و `verify` — فقط ردیف‌های کلیددار؛ در حالتِ `db` تهی و بی هزینه
CREATE INDEX IF NOT EXISTS ix_product_photos_store_key     ON product_photos (store_key)     WHERE store_key <> '';
CREATE INDEX IF NOT EXISTS ix_photo_inbox_store_key        ON photo_inbox (store_key)        WHERE store_key <> '';
CREATE INDEX IF NOT EXISTS ix_storefront_media_store_key   ON storefront_media (store_key)   WHERE store_key <> '';
CREATE INDEX IF NOT EXISTS ix_image_blobs_store_key        ON image_blobs (store_key)        WHERE store_key <> '';
CREATE INDEX IF NOT EXISTS ix_order_issue_photos_store_key ON order_issue_photos (store_key) WHERE store_key <> '';
CREATE INDEX IF NOT EXISTS ix_order_pick_photos_store_key  ON order_pick_photos (store_key)  WHERE store_key <> '';
CREATE INDEX IF NOT EXISTS ix_delivery_photos_store_key    ON delivery_photos (store_key)    WHERE store_key <> '';
