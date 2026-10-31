package ir.vil3ntec.tohid.core.i18n

/**
 *  فهرستِ همهٔ جدول‌های نوشته — برای آزمونِ پوشش (`I18nCoverageTest`).
 *  جدولِ تازه (`object StrX`) این‌جا هم اضافه شود؛ آزمون جاافتاده را می‌گیرد.
 *
 *  بازسازیِ VILL3N: هر گروه جدولِ خودش را دارد (`ORDER.md`)؛ جدول‌های گروه‌ها و `StrKit` (واژه‌های کیتِ
 *  نسخهٔ ۳) هم این‌جا ثبت‌اند تا آزمونِ پوشش (`I18nCoverageTest`) آن‌ها را هم بشمارد.
 */
object I18nTables {
  val all: List<Any> get() = listOf(
    Str, StrNav, StrAuth, StrSettings, StrHome, StrSale, StrShell, StrPos,
    StrExpenses, StrFields, StrInvoices, StrSecurity,
    StrKit, StrProducts, StrStock, StrMoney, StrReports, StrAccount, StrOnline, StrSearch,
  )
}
