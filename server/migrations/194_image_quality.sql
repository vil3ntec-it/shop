-- ============================================================
--  معیارهای کیفیتِ عکس پیش از انتشار — IMG-15 (§۳۴.۲، §۳۴.۴) — `lib/image-quality.js`.
--
--  image_blobs.dhash          dHash ِ ۶۴ بیتی (۱۶ رقمِ hex) برای «مشابهِ احتمالی»؛ '' = هنوز سنجیده
--                             نشده، '-' = سنجیده ولی بی هش (یک‌رنگ، WebP، progressive، خراب، بیش از سقف).
--  images.near_duplicate_of   JSON: تا ۵ شناسهٔ عکسِ مشابه در همان دامنهٔ دید (دکانِ خود، یا
--                             central+pending) — فقط نشان، نه ادغام یا حذف (IMG-BANK-13).
--
--  ⛔ پشتِ پرچمِ خاموشِ `image_bank.quality`: تا روشن نشود هیچ مقداری نوشته نمی‌شود و پاسخ‌ها همان
--  پیشین‌اند. فقط افزودنی. برگشت: rollback/194_image_quality.down.sql.
-- ============================================================

ALTER TABLE image_blobs ADD COLUMN IF NOT EXISTS dhash text NOT NULL DEFAULT '';
ALTER TABLE images ADD COLUMN IF NOT EXISTS near_duplicate_of text NOT NULL DEFAULT '[]';
