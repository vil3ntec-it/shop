-- ============================================================
--  F0-04 — دسته‌های کالا، واحدها و دسته‌های مصارف هم همگام می‌شوند
--
--  تا امروز این سه فهرست فقط روی همان دستگاه می‌ماندند: Sync v1 نامِ
--  جدولِ مجاز را از `lib/sync.js → TABLES` می‌گیرد و این سه آن‌جا
--  نبودند (سرور برایشان جدول نداشت). دسته‌ای که روی گوشی ساخته می‌شد،
--  روی کامپیوتر و وب دیده نمی‌شد.
--
--  هر نام یک ردیف است: `id` = ‎sha256(نام)‎ (۳۲ نویسهٔ اول) و
--  `data = {name}`. دو دستگاه که یک نام را بسازند یک ردیف می‌سازند.
--
--  همان شکلِ جدول‌های ۰۰۲ تا راهِ قدیم (`/api/sync`، `/api/data`) و
--  شمارشِ پنلِ مدیر هم بی استثنا کار کنند. Sync v1 در `sync_rows`
--  می‌نویسد، نه در این‌ها.
--
--    productCategories → product_categories
--    productUnits      → product_units
--    expenseCategories → expense_categories
-- ============================================================

DO $$
DECLARE t text;
BEGIN
  FOREACH t IN ARRAY ARRAY['product_categories','product_units','expense_categories'] LOOP
    EXECUTE format($f$
      CREATE TABLE IF NOT EXISTS %I (
        shop_id     text    NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
        id          text    NOT NULL,
        rev         bigint  NOT NULL,
        version     integer NOT NULL DEFAULT 1,
        created_at  bigint  NOT NULL,
        updated_at  bigint  NOT NULL,
        deleted     boolean NOT NULL DEFAULT false,
        device_id   text    NOT NULL DEFAULT '',
        user_id     text    NOT NULL DEFAULT '',
        data        jsonb   NOT NULL DEFAULT '{}'::jsonb,
        PRIMARY KEY (shop_id, id)
      )$f$, t);
    EXECUTE format('CREATE INDEX IF NOT EXISTS %I ON %I (shop_id, rev)', 'idx_' || t || '_rev', t);
    EXECUTE format('CREATE INDEX IF NOT EXISTS %I ON %I (shop_id) WHERE deleted = false', 'idx_' || t || '_live', t);
  END LOOP;
END $$;
