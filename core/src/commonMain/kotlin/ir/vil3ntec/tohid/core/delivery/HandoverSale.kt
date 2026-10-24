package ir.vil3ntec.tohid.core.delivery

/**
 *  ⚑ قلاب (HOOK) — «سفارش تحویل شد ⇒ برنامه فروش را ثبت می‌کند».
 *
 *  تصمیمِ صاحب (۲۰۲۶-۱۰-۱۹): سفارشِ آنلاین موجودیِ سرور را نه کم می‌کند و
 *  نه رزرو. کالا را دکان از برنامهٔ خودش تحویل می‌دهد و برنامه همان لحظه یک
 *  فروشِ عادی (با orderId/orderNo، یکتا: هر سفارش حداکثر یک فروش) ثبت
 *  می‌کند؛ همان فروش موجودیِ دستگاه را کم می‌کند و همگام می‌شود.
 *
 *  ثبتِ آن فروش: `OrdersLive.installHandoverHook` (#340) ⇒ `OrdersLive.postById`
 *  (پیش از ثبت از سرور می‌پرسد؛ سریِ O و شمارهٔ سفارش؛ یکتا با شناسهٔ سفارش). اگر
 *  ثبت همان لحظه نشد، برنامه آن را از فهرستِ «تحویلِ بی‌فروش» دوباره ثبت می‌کند —
 *  هیچ‌کس دستی در صندوق نمی‌زند (وگرنه دو فروش می‌شد).
 *
 *  ⛔ پیاده‌سازی باید با `orderId` یکتا باشد: صدا زدنِ دوباره (دو بار زدن،
 *     تلاشِ دوباره پس از قطعِ نت) همان فروش را برگرداند، نه دومی.
 */
fun interface HandoverSaleHook {
  suspend fun record(order: HandedOverOrder): HandoverSaleResult
}

/** کجا تحویل شد — فعلاً فقط دریافتِ حضوری از میزِ دکان صدا می‌زند */
enum class HandoverKind { PICKUP_AT_STORE }

data class HandedOverLine(
  val productId: String,
  val name: String,
  val qtyMilli: Long,
  val unitPriceMinor: Long,
  val lineTotalMinor: Long,
)

data class HandedOverOrder(
  val kind: HandoverKind,
  val orderId: String,
  val orderNo: Long,
  val assignmentId: String,
  val customerName: String,
  val paymentMethod: String,
  val paymentStatus: String,
  val subtotalMinor: Long,
  val discountMinor: Long,
  val deliveryFeeMinor: Long,
  val totalMinor: Long,
  /** نقدی که همین حالا در صندوق گرفته شد (۰ برای پیش‌پرداخت) */
  val collectedMinor: Long,
  val lines: List<HandedOverLine>,
)

sealed class HandoverSaleResult {
  data class Recorded(val saleId: String) : HandoverSaleResult()
  /** همین سفارش پیش‌تر فروش شده بود — همان فروش */
  data class AlreadyRecorded(val saleId: String) : HandoverSaleResult()
  data class Failed(val message: String) : HandoverSaleResult()
}

object HandoverSale {
  /** شاخهٔ online-stock هنگامِ آغازِ برنامه می‌گذارد؛ تهی ⇒ هنوز وصل نیست */
  var hook: HandoverSaleHook? = null
}
