-- ============================================================
--  Rollback ِ 275_media_store.sql — فقط با دست، و فقط پس از برگرداندنِ بایت‌ها به پایگاه.
--
--  ترتیب (docs/SCALING-fa.md §۳ «انبارِ عکس»، «برگشت»):
--    ۱) سرور با `IMAGE_STORE=db` و `IMAGE_STORE_READ=disk|s3` (همان پیکربندیِ انبار) ⇒ نوشتنِ تازه در پایگاه؛
--    ۲) `node scripts/image-store-migrate.js rollback --table=shop_chat_media,support_media,trust_evidence`
--       ⇒ هر بایتِ انبار به `data` و `store_key` = ''؛
--    ۳) `node scripts/image-store-migrate.js status` ⇒ «فقط در انبار» = ۰ در هر سه جدول؛
--    ۴) کدِ پیش از این نسخه، سپس همین پرونده.
--  اگر هنوز ردیفی فقط در انبار است، `SET NOT NULL` پایین خطا می‌دهد و تراکنش هیچ تغییری نمی‌دهد (همین نگهبان است).
--
--  اجرا (در یک تراکنش):
--    psql "$DATABASE_URL" -1 -f migrations/rollback/275_media_store.down.sql
-- ============================================================

DROP INDEX IF EXISTS ix_shop_chat_media_store_key;
DROP INDEX IF EXISTS ix_support_media_store_key;
DROP INDEX IF EXISTS ix_trust_evidence_store_key;

ALTER TABLE shop_chat_media DROP CONSTRAINT IF EXISTS shop_chat_media_bytes;
ALTER TABLE support_media   DROP CONSTRAINT IF EXISTS support_media_bytes;
ALTER TABLE trust_evidence  DROP CONSTRAINT IF EXISTS trust_evidence_bytes;

ALTER TABLE shop_chat_media ALTER COLUMN data SET NOT NULL;
ALTER TABLE support_media   ALTER COLUMN data SET NOT NULL;
ALTER TABLE trust_evidence  ALTER COLUMN data SET NOT NULL;

ALTER TABLE shop_chat_media DROP COLUMN IF EXISTS store_key;
ALTER TABLE support_media   DROP COLUMN IF EXISTS store_key;
ALTER TABLE trust_evidence  DROP COLUMN IF EXISTS store_key;

DELETE FROM schema_migrations WHERE version = '275_media_store.sql';
