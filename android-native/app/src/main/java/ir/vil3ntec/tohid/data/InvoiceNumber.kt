package ir.vil3ntec.tohid.data

/**
 *  شمارهٔ فاکتورِ فروش — فقط از همین‌جا (`SalesEngine.record`). جدا از `SalesEngine.kt` تا آن پرونده از سقفِ اندازه نگذرد.
 */
object InvoiceNumber {

  /**
   *  شمارهٔ فاکتورِ بعدی در سریِ [series] — `nextInvoiceNo` ِ دفتر، ولی هرگز نه پایین‌تر از بلندترین شمارهٔ
   *  **همان سری** + ۱ (سریِ خالی = فاکتورهای بی سری).
   *
   *  ── چه اشکالی را می‌بندد (سفرِ J2b، `server/test/e2e/journeys-a.e2e.mjs`) ──────────────────────
   *  `nextInvoiceNo` همگام نمی‌شود و سری مالِ **دستگاه** است، نه حساب (`server/src/lib/doc-series.js`). صاحب روی گوشی
   *  تا `A1-5` فروخت و خارج شد؛ شاگرد با کدِ شاگرد روی **همان گوشی** وارد شد (دفترِ جدا از Snapshot ⇒ `nextInvoiceNo = 1`،
   *  `SyncV1Engine.restoreFromServer` شمارنده را جلو نمی‌برد) و همان سریِ `A1` را گرفت ⇒ فاکتورِ دوم با شمارهٔ `A1-1`.
   *  صاحب که برگشت، فروش‌های شاگرد را از همگام‌سازی گرفت (`SyncCommit.absorb` هم جلو نمی‌برد) و دوباره همان شماره‌ها
   *  را زد. شمارهٔ فاکتور در حسابِ قرض‌دار، رسید و سابقه نوشته می‌شود؛ تکرارش «فاکتورِ گم‌شده/دوتایی» نشان می‌داد.
   *  همین سنجه هر راهی را که دفتر از آن ساخته شود (Snapshot، بازیابی، همگام‌سازی، نصبِ دوباره) با هم می‌بندد؛
   *  فقط سریِ خودِ همین دستگاه شمرده می‌شود ⇒ فاکتورِ دستگاه‌های دیگر «پرشِ» بیهوده در سریِ این دستگاه نمی‌سازد.
   */
  fun next(d: ShopData, series: String): Int {
    val s = series.trim()
    val top = d.sales.asSequence().filter { (it.series?.trim() ?: "") == s }.maxOfOrNull { it.invoiceNumber ?: 0 } ?: 0
    return maxOf(d.nextInvoiceNo, top + 1)
  }
}
