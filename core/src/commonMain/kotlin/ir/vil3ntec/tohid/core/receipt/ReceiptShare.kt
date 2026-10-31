package ir.vil3ntec.tohid.core.receipt

/**
 *  متنِ همراهِ رسیدِ دیجیتال (POS-32) — همان که با تصویر یا PDF ِ فاکتور به واتس‌اپ، تلگرام یا
 *  پیامک می‌رود. منطقِ خالص؛ هیچ مبلغی این‌جا نیست (عددها روی خودِ تصویرند).
 *
 *  قاعده‌ها:
 *    • بی پیوند ⇒ همان یک خطِ امروز: «فاکتور #<شماره> — <نامِ دکان>».
 *    • با پیوند (فروشِ نسخهٔ ۲ با توکنِ QR، پرچمِ `shop.receipt_v2` و QR ِ روشنِ سربرگ — همان
 *      `Receipts.publicUrl`) ⇒ دو خطِ دیگر: «فاکتورِ آنلاین:» و خودِ نشانی **تنها در خطِ آخر**، تا
 *      برنامه‌ها پیوندش کنند و راست‌به‌چپ آن را به هم نریزد.
 *    • فقط `http`/`https` بی فاصله و بی خطِ تازه فرستاده می‌شود ([safeLink])؛ هر چیزِ دیگر کنار
 *      گذاشته می‌شود و متن همان متنِ بی پیوند است.
 */
object ReceiptShare {

  const val LINK_LABEL = "فاکتورِ آنلاین:"

  private val LINK_RE = Regex("^https?://[^\\s]+$", RegexOption.IGNORE_CASE)

  /** نشانیِ قابلِ فرستادن، یا null */
  fun safeLink(raw: String?): String? = raw?.trim()?.takeIf { LINK_RE.matches(it) }

  fun caption(invoiceLabel: String?, storeName: String, publicUrl: String?): String {
    val name = storeName.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")
    val head = "فاکتور #${invoiceLabel.orEmpty()}" + (if (name.isNotEmpty()) " — $name" else "")
    val link = safeLink(publicUrl) ?: return head
    return "$head\n$LINK_LABEL\n$link"
  }
}
