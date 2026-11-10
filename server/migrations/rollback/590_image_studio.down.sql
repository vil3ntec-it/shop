-- ============================================================
--  Rollback ِ 590_image_studio.sql — فقط با دست.
--
--  صفِ استودیوی عکس برداشته می‌شود. نسخه‌های ساخته‌شده (`image_versions` با `tool` = 'studio/1') و بلاب‌هایشان
--  می‌مانند: تاریخچهٔ عکس‌اند و شاید دکانی یکی را برگزیده باشد. پیش از اجرا پرچمِ `shop.image_studio` را
--  خاموش کنید (یا کدِ پیشین را برگردانید): کدِ تازه بی این دو جدول کار نمی‌کند.
--
--  اجرا (در یک تراکنش):
--    psql "$DATABASE_URL" -1 -f migrations/rollback/590_image_studio.down.sql
-- ============================================================

DROP TABLE IF EXISTS image_studio_jobs;
DROP TABLE IF EXISTS image_studio_batches;

DELETE FROM schema_migrations WHERE version = '590_image_studio.sql';
