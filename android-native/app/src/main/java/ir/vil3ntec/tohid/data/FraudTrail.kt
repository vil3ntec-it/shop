package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.audit.LineVoid

/**
 *  RPT-09 — «سابقهٔ ضدتقلب»، پشتِ `FraudTrailPref` (`tohid-policy` ‹`fraud_trail`›، پیش‌فرض **خاموش**،
 *  فقط همین دستگاه؛ خواندنِ تنظیم و نامِ کاربر در `FraudTrailAndroid.kt`).
 *
 *  زبانهٔ «ضدتقلب» ِ گزارشِ تازه (`ReportCore.fraud`) کاربرِ هر رویداد را فقط از فیلدِ صریحِ
 *  همان رویداد می‌گیرد. تا امروز برنامه آن فیلدها را نمی‌نوشت: لغو و مرجوعیِ پنجرهٔ قدیم
 *  «نامعلوم» و حذفِ ردیف همیشه صفر بود. روشن ⇒
 *  - لغوِ فاکتور: `by` روی ردیفِ `cancel_sale` (`SalesEngine.cancel`)؛
 *  - مرجوعیِ پنجرهٔ قدیم: `by` روی هر `SaleReturn` (`SalesEngine.recordReturn`؛ جریانِ تازهٔ
 *    برگشت از پیش `by` می‌نوشت)؛
 *  - هر ردیفی که از فاکتورِ باز حذف شد (Delete، منفی تا صفر، حذف در پنجرهٔ ردیف، خالی کردنِ
 *    فاکتور): یک ردیفِ سابقهٔ `line_delete` با مبلغِ همان ردیف (`CartStore.onLinesRemoved`).
 *  خاموش ⇒ هیچ‌کدام: `by` همان `null` است که نوشته نمی‌شود (`EncodeDefault.NEVER`) و ردیفی ساخته نمی‌شود.
 */
object FraudTrail {

  /** ردیف‌هایی از [before] که در [after] نیستند (کلید = `productId`ِ ردیف؛ قاعده در `core` ‹`LineVoid`›) */
  fun removed(before: List<SalesEngine.CartLine>, after: List<SalesEngine.CartLine>): List<SalesEngine.CartLine> {
    val keys = LineVoid.removed(before.map { it.productId }, after.map { it.productId })
    return keys.mapNotNull { k -> before.firstOrNull { it.productId == k } }
  }

  /**
   *  ردیفِ سابقهٔ حذفِ یک ردیف. مبلغ همان جمعِ ردیف در سبد است (قیمتِ ردیف × مقدار، گردِ
   *  افغانی — `SalesEngine.lineTotal`) و `refId` کلیدِ همان فاکتورِ باز (`CartStore.saleKey`)،
   *  یعنی اگر فاکتور بعداً ثبت شد، همان شناسهٔ فروش.
   */
  fun lineDeleteRow(
    d: ShopData,
    line: SalesEngine.CartLine,
    draftKey: String?,
    by: String?,
    today: String,
    now: Long,
    id: String,
  ): AuditEntry {
    val name = if (line.free) line.label.trim() else d.products.find { it.id == line.productId }?.name ?: line.productId
    return AuditEntry(
      id = id,
      type = LineVoid.TYPE,
      date = today,
      refId = draftKey,
      notes = "حذفِ ردیف از فاکتورِ باز — $name × ${ir.vil3ntec.tohid.qty(line.quantity)}",
      createdAt = now,
      amount = SalesEngine.lineTotal(SalesEngine.linePrice(d, line), line.quantity),
      by = by,
    )
  }
}
