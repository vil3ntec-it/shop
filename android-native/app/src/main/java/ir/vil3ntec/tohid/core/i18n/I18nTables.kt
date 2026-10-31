package ir.vil3ntec.tohid.core.i18n

/**
 *  فهرستِ همهٔ جدول‌های نوشته — برای آزمونِ پوشش (`I18nCoverageTest`).
 *  جدولِ تازه (`object StrX`) این‌جا هم اضافه شود؛ آزمون جاافتاده را می‌گیرد.
 */
object I18nTables {
  val all: List<Any> get() = listOf(Str, StrNav, StrAuth, StrSettings, StrHome, StrSale, StrShell, StrPos, StrExpenses, StrFields, StrInvoices, StrSecurity, StrLine, StrOrderSlip)
}
