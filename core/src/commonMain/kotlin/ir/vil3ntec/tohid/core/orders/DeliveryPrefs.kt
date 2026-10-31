package ir.vil3ntec.tohid.core.orders

/**
 *  دستورِ تحویلِ سفارش — CHK-14 («با اسکناسِ ۱۰۰۰ پرداخت می‌کنم» تا پیک پول خرد بیاورد) و
 *  CHK-24 (ویرایش تا پیش از ارسال). همان قاعده‌های `server/src/lib/delivery-prefs.js`؛ سرور
 *  دوباره همه را می‌سنجد و این‌جا فقط خطا کنارِ همان کادر و پیش از رفتن دیده می‌شود.
 *
 *  خالص (commonMain، بی `java.*`/`android.*`) تا برنامهٔ آیفون و حالتِ پیک همین را بخوانند (§۴۰.۳).
 *  پول فقط عددِ صحیحِ یک‌صدمِ افغانی.
 */
object DeliveryPrefs {
  /** بزرگ‌ترین اسکناسِ افغانی (۱۰۰۰) به یک‌صدم: پول خرد هرگز به یک اسکناسِ کامل نمی‌رسد */
  const val LARGEST_NOTE_MINOR = 100_000L

  /** وضعیت‌های پیش از حرکتِ بسته — ویرایش فقط در این‌ها */
  val EDITABLE = setOf("new", "accepted", "picking", "packed")

  fun editable(status: String, fulfillment: String): Boolean = fulfillment == "delivery" && status in EDITABLE

  /** پول خردی که پیک می‌آورد؛ داده کمتر از بدهی ⇒ null */
  fun changeOf(tenderMinor: Long, dueMinor: Long): Long? = if (tenderMinor >= dueMinor) tenderMinor - dueMinor else null

  sealed class Tender {
    /** کادرِ خالی ⇒ بی اسکناس */
    data object None : Tender()
    data class Ok(val minor: Long) : Tender()
    data class Error(val message: String) : Tender()
  }

  /** کادرِ «مشتری با چه مبلغی می‌پردازد؟» — افغانیِ کامل، رقمِ فارسی هم */
  fun parseTender(text: String): Tender {
    if (text.isBlank()) return Tender.None
    val afn = OrderDraft.scaled(text, 0) ?: return Tender.Error("مبلغِ اسکناس افغانیِ کامل است، مثلِ ۱۰۰۰")
    if (afn <= 0L || afn > 1_000_000_000L) return Tender.Error("مبلغِ اسکناس درست نیست")
    return Tender.Ok(afn * 100)
  }

  /** همان سنجشِ سرور در برابرِ جمعِ سفارش — null یعنی درست */
  fun tenderError(tenderMinor: Long?, totalMinor: Long): String? {
    if (tenderMinor == null) return null
    val change = changeOf(tenderMinor, totalMinor) ?: return "مبلغِ اسکناس از جمعِ سفارش کمتر است"
    if (change >= LARGEST_NOTE_MINOR) return "پول خرد از یک اسکناسِ ۱۰۰۰ بیشتر می‌شود؛ مبلغ را درست بنویسید"
    return null
  }

  /** خطِ کارتِ سفارش و پیک: «مشتری با ۱,۳۰۰ می‌پردازد · پول خرد: ۶۰» */
  fun tenderLine(tenderMinor: Long, dueMinor: Long): String {
    val change = changeOf(tenderMinor, dueMinor)
    val head = "مشتری با ${OrderText.money(tenderMinor)} افغانی می‌پردازد"
    return when {
      change == null -> "$head — از مبلغِ نقد کمتر است؛ با دکان تماس بگیرید"
      change == 0L -> "$head — پول خرد لازم نیست"
      else -> "$head — پول خرد: ${OrderText.money(change)} افغانی"
    }
  }
}
