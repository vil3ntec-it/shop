-- ============================================================
--  داده‌های تجاری هر دکان
--
--  هر جدول دقیقاً یک «مجموعه» از برنامه است و همیشه shop_id دارد،
--  پس هیچ پرس‌وجویی نمی‌تواند از مرز دکان بیرون بزند.
--
--  ستون‌های مشترک:
--    rev        شماره‌ی سراسری تغییر در آن دکان (برای همگام‌سازی تفاضلی)
--    version    شماره‌ی نسخه‌ی رکورد (برای تشخیص تعارض ویرایش همزمان)
--    updated_at زمان ویرایش سمت کلاینت (برای داوری «آخرین ویرایش برنده»)
--    deleted    حذف نرم؛ رکورد پاک نمی‌شود تا دستگاه‌های آفلاین خبردار شوند
--    data       بدنه‌ی رکورد به شکلی که خود برنامه می‌شناسد
--
--  نگاشت نام مجموعه در برنامه به جدول:
--    products→products            warehouseEntries→inventory
--    sales→sales                  saleItems→sale_items
--    returns→sale_returns         debtors→debtors
--    transactions→payments        expenses→expenses
--    suppliers→suppliers          purchases→purchases
--    supplierPayments→supplier_payments
--    stockMovements→stock_movements  priceHistory→price_history
--    auditLog→shop_audit_log
-- ============================================================

DO $$
DECLARE t text;
BEGIN
  FOREACH t IN ARRAY ARRAY[
    'products','inventory','sales','sale_items','sale_returns','debtors',
    'payments','expenses','suppliers','purchases','supplier_payments',
    'stock_movements','price_history','shop_audit_log'
  ] LOOP
    EXECUTE format($f$
      CREATE TABLE %I (
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
    EXECUTE format('CREATE INDEX %I ON %I (shop_id, rev)', 'idx_' || t || '_rev', t);
    EXECUTE format('CREATE INDEX %I ON %I (shop_id, updated_at DESC)', 'idx_' || t || '_updated', t);
    EXECUTE format('CREATE INDEX %I ON %I (shop_id) WHERE deleted = false', 'idx_' || t || '_live', t);
  END LOOP;
END $$;

-- جست‌وجوهای پرتکرار
CREATE INDEX idx_products_barcode ON products (shop_id, (data->>'barcode'));
CREATE INDEX idx_sale_items_sale  ON sale_items (shop_id, (data->>'saleId'));
CREATE INDEX idx_payments_debtor  ON payments (shop_id, (data->>'debtorId'));
CREATE INDEX idx_sales_time       ON sales (shop_id, ((data->>'at')::bigint) DESC);

-- ---------- شمارنده‌ی تغییر هر دکان ----------
CREATE TABLE shop_rev (
  shop_id   text PRIMARY KEY REFERENCES shops(id) ON DELETE CASCADE,
  last_rev  bigint NOT NULL DEFAULT 0
);

-- ---------- تنظیمات مشترک دکان ----------
CREATE TABLE shop_settings (
  shop_id     text PRIMARY KEY REFERENCES shops(id) ON DELETE CASCADE,
  data        jsonb  NOT NULL DEFAULT '{}'::jsonb,
  rev         bigint NOT NULL DEFAULT 0,
  updated_at  bigint NOT NULL
);

-- ---------- عملیات همگام‌سازی (جلوگیری از ثبت تکراری) ----------
-- هر عملیات مهم از گوشی یک شناسه‌ی یکتا همراه دارد. اگر همان درخواست
-- دوباره برسد (قطع شدن اینترنت وسط کار)، پاسخ قبلی برگردانده می‌شود و
-- رکورد دوباره ثبت نمی‌شود.
CREATE TABLE sync_operations (
  id                  text PRIMARY KEY,
  shop_id             text   NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  user_id             text   NOT NULL DEFAULT '',
  device_id           text   NOT NULL DEFAULT '',
  client_operation_id text   NOT NULL,
  request_hash        text   NOT NULL DEFAULT '',
  response            jsonb  NOT NULL DEFAULT '{}'::jsonb,
  created_at          bigint NOT NULL,
  UNIQUE (shop_id, client_operation_id)
);
CREATE INDEX idx_sync_ops_created ON sync_operations(created_at);
