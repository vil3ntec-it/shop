-- ============================================================
--  ۲۲۶ — جست‌وجوی نامِ کالای ویترین با نمایهٔ سه‌حرفی (`pg_trgm`)؛ docs/SCALING-fa.md («جست‌وجوی سه‌حرفی»).
--
--  جست‌وجوی هر ویترین (`GET /api/store/:slug/products?q=`، `lib/storefront.js` ⇒ catalog) همان
--  `<نامِ یکدست> LIKE '%…%'` است با همان گریزِ \ % _ و همان سقف‌ها. بی نمایه هر جست‌وجو **همهٔ** کالاهای دکان را
--  می‌خواند، نام را یکدست می‌کند و می‌سنجد (دکانِ ۲۰٬۰۰۰ کالایی ⇐ ۲۰٬۰۰۰ ردیف در هر جست‌وجو). عملگرِ `LIKE` عمداً
--  عوض نمی‌شود: ردهٔ `gin_trgm_ops` همان `LIKE` را از نمایه پاسخ می‌دهد ⇒ نتیجه بایت‌به‌بایت همان، فقط سریع‌تر
--  (شباهتِ `%` ِ pg_trgm نتیجه را «نزدیک» می‌کرد و قاعدهٔ جست‌وجو را عوض می‌کرد).
--
--  عبارتِ نمایه **دقیقاً** همان `SQL_NAME` ِ `lib/storefront.js` است (بی نامِ مستعار) و شرطِ جزئی همان `SHOP_ROWS` +
--  «حذف‌نشده»؛ `storefront-trgm.test.js` با EXPLAIN می‌سنجد که همین عبارتِ پرسش از نمایه پاسخ می‌گیرد.
--  نمایه دوستونی است — (account_id، نام) با `btree_gin` (contrib، کنارِ pg_trgm) — تا شرطِ دکان و واژه هر دو درونِ
--  یک نمایه بنشینند. سنجش (docs/PERF-LOAD-fa.md «جست‌وجوی سه‌حرفی»، ۲۲۰ هزار کالا در ۱۰۱ دکان): دکانِ ۲۰٬۰۰۰ کالایی
--  ۴۲ تا ۶۷ms ⇐ ۰٫۸ تا ۴٫۳ms، دکانِ ۲٬۰۰۰ کالایی ~۵ms ⇐ ۰٫۸ تا ۱٫۸ms. نمایهٔ تک‌ستونی (بی btree_gin) واژهٔ پرتکرار را
--  **کندتر** می‌کرد (۵ ⇐ ۲۱ms در دکانِ کوچک: فهرستِ همهٔ دکان‌ها خوانده می‌شد) ⇒ بی btree_gin هیچ نمایه‌ای ساخته نمی‌شود.
--  نبودِ هر کدام (PGlite، PostgreSQL ِ بی contrib) ⇒ همان LIKE ِ امروز؛ مهاجرت نمی‌شکند.
--
--  ⚠️ روی پایگاهِ بزرگِ در حالِ کار، پیش از به‌روزرسانی همین را با `CREATE INDEX CONCURRENTLY` (بیرون از تراکنش،
--     همان نام) بسازید تا نوشتن روی `sync_rows` قفل نشود؛ این مهاجرت با `IF NOT EXISTS` آن‌گاه کاری نمی‌کند.
--  هیچ ستون، جدول یا داده‌ای عوض نمی‌شود. rollback: migrations/rollback/226_storefront_name_trgm.down.sql
-- ============================================================
DO $$
BEGIN
  BEGIN
    CREATE EXTENSION IF NOT EXISTS pg_trgm;
  EXCEPTION WHEN OTHERS THEN
    RAISE NOTICE 'pg_trgm در دسترس نیست؛ جست‌وجوی ویترین بی نمایهٔ سه‌حرفی (همان LIKE)';
  END;
  IF NOT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'pg_trgm') THEN
    RETURN;
  END IF;
  BEGIN
    CREATE EXTENSION IF NOT EXISTS btree_gin;
  EXCEPTION WHEN OTHERS THEN
    RAISE NOTICE 'btree_gin در دسترس نیست؛ جست‌وجوی ویترین بی نمایهٔ سه‌حرفی (همان LIKE)';
  END;
  IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'btree_gin') THEN
    EXECUTE $i$CREATE INDEX IF NOT EXISTS ix_sync_rows_product_name_trgm ON sync_rows
      USING gin (account_id, (translate(lower(data->>'name'), 'يىكۀةأإآ' || chr(1600) || chr(8204), 'ییکههااا  ')) gin_trgm_ops)
      WHERE app = 'shop' AND account_kind = 'shop' AND table_name = 'products' AND deleted_at IS NULL$i$;
  END IF;
END
$$;
