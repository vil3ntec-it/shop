package ir.vil3ntec.tohid.core.i18n

/**
 *  فهرستِ همهٔ جدول‌های نوشته — برای آزمونِ پوشش (`I18nCoverageTest`).
 *  جدولِ تازه (`object StrX`) این‌جا هم اضافه شود؛ آزمون جاافتاده را می‌گیرد.
 *
 *  بازسازیِ VILL3N: هر گروه جدولِ خودش را دارد (`ORDER.md`)؛ جدول‌های خالیِ گروه‌ها
 *  و `StrKit` (واژه‌های کیتِ نسخهٔ ۳) از پیش این‌جا ثبت‌اند.
 */
object I18nTables {
  val all: List<Any> get() = listOf(
    Str, StrNav, StrAuth, StrSettings, StrHome, StrSale,
    StrKit, StrProducts, StrStock, StrMoney, StrReports, StrAccount, StrOnline, StrSearch,
  )
}
