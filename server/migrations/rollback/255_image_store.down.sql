-- ============================================================
--  Rollback ِ 255_image_store.sql — فقط با دست، و فقط پس از برگرداندنِ بایت‌ها به پایگاه.
--
--  ترتیب (docs/SCALING-fa.md §۳ «انبارِ عکس»، «برگشت»):
--    ۱) سرور با `IMAGE_STORE=db` و `IMAGE_STORE_READ=disk|s3` (همان پیکربندیِ انبار) ⇒ نوشتنِ تازه در پایگاه؛
--    ۲) `node scripts/image-store-migrate.js rollback` ⇒ هر بایتِ انبار به `data` و `store_key` = ''؛
--    ۳) `node scripts/image-store-migrate.js status` ⇒ «فقط در انبار» = ۰ در همهٔ جدول‌ها؛
--    ۴) کدِ پیش از این نسخه، سپس همین پرونده.
--  اگر هنوز ردیفی فقط در انبار است، `SET NOT NULL` پایین خطا می‌دهد و تراکنش هیچ تغییری نمی‌دهد (همین نگهبان است).
--  `product_photos.data` از آغاز nullable بود و همان می‌ماند.
--
--  اجرا (در یک تراکنش):
--    psql "$DATABASE_URL" -1 -f migrations/rollback/255_image_store.down.sql
-- ============================================================

DROP INDEX IF EXISTS ix_product_photos_store_key;
DROP INDEX IF EXISTS ix_photo_inbox_store_key;
DROP INDEX IF EXISTS ix_storefront_media_store_key;
DROP INDEX IF EXISTS ix_image_blobs_store_key;
DROP INDEX IF EXISTS ix_order_issue_photos_store_key;
DROP INDEX IF EXISTS ix_order_pick_photos_store_key;
DROP INDEX IF EXISTS ix_delivery_photos_store_key;

ALTER TABLE photo_inbox        DROP CONSTRAINT IF EXISTS photo_inbox_bytes;
ALTER TABLE storefront_media   DROP CONSTRAINT IF EXISTS storefront_media_bytes;
ALTER TABLE image_blobs        DROP CONSTRAINT IF EXISTS image_blobs_bytes;
ALTER TABLE order_issue_photos DROP CONSTRAINT IF EXISTS order_issue_photos_bytes;
ALTER TABLE order_pick_photos  DROP CONSTRAINT IF EXISTS order_pick_photos_bytes;
ALTER TABLE delivery_photos    DROP CONSTRAINT IF EXISTS delivery_photos_bytes;

--  نگهبانِ عکسِ کالا (ستونش NOT NULL نمی‌شود): عکسی که هنوز فقط در انبار است ⇒ همین CHECK ِ سنجیده خطا می‌دهد؛
--  بی آن، ستون برداشته می‌شد و عکس «برداشته» خوانده می‌شد
ALTER TABLE product_photos ADD CONSTRAINT product_photos_rollback_guard CHECK (data IS NOT NULL OR store_key = '');
ALTER TABLE product_photos DROP CONSTRAINT product_photos_rollback_guard;

ALTER TABLE photo_inbox        ALTER COLUMN data SET NOT NULL;
ALTER TABLE storefront_media   ALTER COLUMN data SET NOT NULL;
ALTER TABLE image_blobs        ALTER COLUMN data SET NOT NULL;
ALTER TABLE order_issue_photos ALTER COLUMN data SET NOT NULL;
ALTER TABLE order_pick_photos  ALTER COLUMN data SET NOT NULL;
ALTER TABLE delivery_photos    ALTER COLUMN data SET NOT NULL;

ALTER TABLE product_photos     DROP COLUMN IF EXISTS store_key;
ALTER TABLE photo_inbox        DROP COLUMN IF EXISTS store_key;
ALTER TABLE storefront_media   DROP COLUMN IF EXISTS store_key;
ALTER TABLE image_blobs        DROP COLUMN IF EXISTS store_key;
ALTER TABLE order_issue_photos DROP COLUMN IF EXISTS store_key;
ALTER TABLE order_pick_photos  DROP COLUMN IF EXISTS store_key;
ALTER TABLE delivery_photos    DROP COLUMN IF EXISTS store_key;

DELETE FROM schema_migrations WHERE version = '255_image_store.sql';
