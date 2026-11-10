package ir.vil3ntec.tohid.ui.v3.online.orders

import ir.vil3ntec.tohid.core.i18n.StrOrdersHub
import ir.vil3ntec.tohid.core.model.OrderDto
import ir.vil3ntec.tohid.ui.theme.V3Tone

/**
 *  O-06 = ب: نگه‌داشتنِ موجودیِ سفارشِ آنلاین در برنامهٔ مدیریت — منطقِ خالص، بی Compose (آزمون: `OrderStockHoldTest`).
 *  نشانِ «موجودیِ رزروشده کم آمد — بررسی کنید» روی کارتِ صف (`stockReview`) و سرِ جزئیات (`stockHold.review`)، و یک
 *  پیام برای حالِ نگه‌داشتن در جزئیات. هر دو کلید را فقط سرور و فقط با پرچمِ `shop.stock_reserve` می‌فرستد؛ نبودشان
 *  (سرورِ پیشین، پرچمِ خاموش، سفارشِ بی نگه‌داشته) یا حالِ ناشناخته ⇒ هیچ نشان و هیچ پیامی. هیچ چیز این‌جا حساب
 *  نمی‌شود که سرور نگفته باشد (مهلت با ساعتِ گوشی سنجیده نمی‌شود).
 */
object OrderStockHold {

  /** نشانِ «بررسی کنید» روی کارت و سرِ جزئیات */
  fun review(o: OrderDto): Boolean = o.stockReview || o.stockHold?.review == true

  data class Note(val tone: V3Tone, val title: String, val body: String? = null)

  /** پیامِ جزئیات؛ null ⇒ بی بخش. [stamp] قالبِ زمانِ پایانِ مهلت است (`OrdersModel.stamp`) */
  fun note(o: OrderDto, stamp: (Long) -> String): Note? {
    val h = o.stockHold ?: return null
    if (h.review) return Note(V3Tone.WARNING, StrOrdersHub.stockReview, StrOrdersHub.stockReviewBody)
    val fresh = o.status == "new"
    return when (h.status) {
      "held" -> h.expiresAt?.let { Note(V3Tone.INFO, StrOrdersHub.stockHeldUntil(stamp(it)), StrOrdersHub.stockHeldAcceptHint.takeIf { fresh }) }
        ?: Note(V3Tone.INFO, StrOrdersHub.stockHeldOpen)
      "expired" -> Note(if (fresh) V3Tone.WARNING else V3Tone.INFO, StrOrdersHub.stockExpired, StrOrdersHub.stockExpiredHint.takeIf { fresh })
      "committed" -> Note(V3Tone.SUCCESS, StrOrdersHub.stockCommitted)
      "released" -> Note(V3Tone.INFO, StrOrdersHub.stockReleased)
      else -> null
    }
  }
}
