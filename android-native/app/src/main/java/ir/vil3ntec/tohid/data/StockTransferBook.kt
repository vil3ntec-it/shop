package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.stock.StockTransfer

/**
 *  INV-11 — گام‌های انتقالِ موجودیِ سرور در **دفترِ همین دکان**: هر گام یک ردیفِ `auditLog` با
 *  `type = stock_transfer` و شیءِ `xfer` (`StockTransfer.Step.xfer`)؛ کلیدِ گام = شناسهٔ ردیف.
 *
 *  همان صفِ همگام‌سازیِ امروز (v1 یا v3) ردیف را به سرور می‌برد و سرور هنگامِ خواندنِ دفترِ دستگاه داوری می‌کند
 *  (`server/src/lib/stock-transfers.js` ‹`ingestDevice`›) ⇒ بی اینترنت هم ثبت می‌شود و پس از وصل شدن یک بار می‌نشیند.
 *  ⛔ هیچ ردیفِ موجودیِ دفتر (ورود، فروش، حرکت) این‌جا ساخته نمی‌شود: جابه‌جایی و «در مسیر» فقط در سرور است، پس
 *     موجودیِ کلِ دستگاه همان می‌ماند و هیچ واحدی دو بار شمرده نمی‌شود.
 *  منطقِ خالص؛ نوشتن فقط از `StockTransferFlag.record` (دفترِ همین لحظهٔ دیسک، یک بار برای هر کلید). آزمون: `StockTransferBookTest`.
 */
object StockTransferBook {

  /** گام‌های دفتر، به ترتیبِ ثبت */
  fun steps(d: ShopData): List<StockTransfer.Step> = d.auditLog.asSequence()
    .filter { it.type == StockTransfer.AUDIT_TYPE }
    .mapNotNull { StockTransfer.stepOf(it.id, it.createdAt, it.xfer) }
    .sortedWith(compareBy<StockTransfer.Step> { it.at }.thenBy { it.key })
    .toList()

  fun has(d: ShopData, key: String): Boolean = d.auditLog.any { it.id == key }

  /** متنِ «سابقه» — فقط برای خواندنِ آدم؛ داده همان `xfer` است */
  fun notes(s: StockTransfer.Step, names: (String) -> String): String = when (s.step) {
    StockTransfer.SEND -> "انتقالِ موجودی: ${place(s.from)} ⇐ ${place(s.to)} — " + s.lines.joinToString("، ") { "${names(it.productId)} ${qtyText(it.qtyMilli)}" }
    StockTransfer.RECEIVE -> (if (s.final) "دریافتِ انتقال" else "دریافتِ ناقصِ انتقال") +
      (if (s.lines.isEmpty()) "" else " — " + s.lines.joinToString("، ") { "${names(it.productId)} ${qtyText(it.qtyMilli)}" }) +
      (if (s.reason.isEmpty()) "" else " (${s.reason})")
    else -> "ردِ انتقال (${s.reason})"
  }.take(500)

  fun row(s: StockTransfer.Step, user: String?, today: String, names: (String) -> String): AuditEntry =
    AuditEntry(id = s.key, type = StockTransfer.AUDIT_TYPE, date = today, notes = notes(s, names), createdAt = s.at, by = user, xfer = s.xfer())

  /** ثبتِ یک گام روی دفترِ همین لحظه: کلیدِ تکراری ⇒ `null` (هیچ نوشته نمی‌شود) */
  fun append(d: ShopData, s: StockTransfer.Step, user: String?, today: String): ShopData? {
    if (has(d, s.key)) return null
    val names = d.products.associate { it.id to it.name }
    return d.copy(auditLog = d.auditLog + row(s, user, today) { names[it] ?: it })
  }

  /** نامِ دادهٔ سابقه — فارسی می‌ماند (همان قاعدهٔ یادداشتِ ردیف‌های دفتر) */
  private fun place(loc: String): String = when (loc) {
    StockTransfer.SHOP_FLOOR -> "قفسهٔ فروشگاه"
    StockTransfer.WAREHOUSE -> "انبار"
    else -> loc
  }

  private fun qtyText(milli: Long): String = CountSheetModel.text(milli)
}
