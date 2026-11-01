package ir.vil3ntec.tohid.core.orders

/**
 *  ORD-10 — «برگهٔ سفارش» که پس از قبول روی چاپگرِ فاکتورِ همین دستگاه می‌رود (سناریوی S31:
 *  «قبول ⇒ چاپِ خودکارِ برگه ⇒ جمع‌آوری»). منطقِ خالص: ردیف‌ها این‌جا، نوشته‌ها از جدولِ
 *  سه‌زبانهٔ برنامه ([Texts])، کشیدن و چاپ در سمتِ پلتفرم (`OrderSlipAndroid.kt`).
 *
 *  قاعده‌ها:
 *    • مبلغ همیشه `Long` ِ واحدِ خرد (یک‌صدم) و فقط با [OrderText.money]؛ هیچ حسابِ تازه‌ای
 *      این‌جا نیست — همان رقم‌های سرور (`subtotal/discount/deliveryFee/total`) روی کاغذ می‌آیند.
 *    • ⛔ حریم: شمارهٔ مشتری و نشانیِ کامل روی کاغذ نمی‌آیند؛ فقط نام و «نشانه» ِ سفارشِ ارسالی
 *      (همان «اول نشانه» ِ کارتِ پیک).
 *    • سفارشِ آزمایشی نشانِ «آزمایشی» دارد (برای آزمودنِ صف و چاپ).
 */
object OrderSlip {

  data class Line(
    val name: String,
    val unit: String,
    val qtyMilli: Long,
    val unitPriceMinor: Long,
    val lineTotalMinor: Long,
    val note: String = "",
  )

  data class Order(
    val orderNo: Long,
    val fulfillment: String,
    val paymentMethod: String,
    val paymentStatus: String,
    val customerName: String,
    val landmark: String,
    val note: String,
    val scheduledFor: Long?,
    val readyAt: Long?,
    val isTest: Boolean,
    val lines: List<Line>,
    val subtotalMinor: Long,
    val discountMinor: Long,
    val deliveryFeeMinor: Long,
    val totalMinor: Long,
  )

  enum class Kind { TITLE, HEAD, BODY, SMALL }

  data class Row(val text: String, val kind: Kind = Kind.BODY, val center: Boolean = false)

  /** نوشته‌های برگه — جدولِ سه‌زبانهٔ برنامه (`StrOrderSlip`) پیاده‌اش می‌کند. */
  interface Texts {
    fun title(orderNo: String): String
    val test: String
    val delivery: String
    val pickup: String
    fun scheduled(at: String): String
    fun readyBy(at: String): String
    fun customer(name: String): String
    fun landmark(text: String): String
    val cod: String
    val paid: String
    val unpaid: String
    fun collect(amount: String): String
    fun line(name: String, qty: String, total: String): String
    fun lineNote(note: String): String
    fun subtotal(amount: String): String
    fun discount(amount: String): String
    fun deliveryFee(amount: String): String
    fun total(amount: String): String
    fun orderNote(note: String): String
    fun printedAt(at: String): String
  }

  /** ردیف‌های برگه به ترتیبِ کاغذ. `time` زمانِ سرور (میلی‌ثانیه) را به نوشتهٔ صفحه می‌برد. */
  fun rows(o: Order, tx: Texts, printedAt: Long, time: (Long) -> String): List<Row> {
    val out = ArrayList<Row>()
    out += Row(tx.title(o.orderNo.toString()), Kind.TITLE, center = true)
    if (o.isTest) out += Row(tx.test, Kind.HEAD, center = true)
    val delivery = o.fulfillment == "delivery"
    out += Row(if (delivery) tx.delivery else tx.pickup, Kind.HEAD, center = true)
    o.scheduledFor?.let { out += Row(tx.scheduled(time(it)), Kind.HEAD) }
    o.readyAt?.let { out += Row(tx.readyBy(time(it))) }
    if (o.customerName.isNotBlank()) out += Row(tx.customer(o.customerName.trim()))
    if (delivery && o.landmark.isNotBlank()) out += Row(tx.landmark(o.landmark.trim()), Kind.HEAD)
    val pay = when {
      o.paymentStatus == "paid" -> tx.paid
      o.paymentMethod == "cod" -> tx.cod
      else -> tx.unpaid
    }
    out += Row(pay, Kind.HEAD)
    if (o.paymentStatus != "paid" && o.paymentMethod == "cod") out += Row(tx.collect(OrderText.money(o.totalMinor)))
    out += Row(RULE, Kind.SMALL, center = true)
    for (l in o.lines) {
      val qty = OrderText.qty(l.qtyMilli) + if (l.unit.isNotBlank()) " ${l.unit.trim()}" else ""
      out += Row(tx.line(l.name.trim(), qty, OrderText.money(l.lineTotalMinor)))
      if (l.note.isNotBlank()) out += Row(tx.lineNote(l.note.trim()), Kind.SMALL)
    }
    out += Row(RULE, Kind.SMALL, center = true)
    out += Row(tx.subtotal(OrderText.money(o.subtotalMinor)))
    if (o.discountMinor > 0) out += Row(tx.discount(OrderText.money(o.discountMinor)))
    if (o.deliveryFeeMinor > 0) out += Row(tx.deliveryFee(OrderText.money(o.deliveryFeeMinor)))
    out += Row(tx.total(OrderText.money(o.totalMinor)), Kind.HEAD)
    if (o.note.isNotBlank()) out += Row(tx.orderNote(o.note.trim()))
    out += Row(tx.printedAt(time(printedAt)), Kind.SMALL, center = true)
    return out
  }

  const val RULE = "— — — — — — — —"
}

/**
 *  ORD-10 — کِی برگهٔ سفارش **خودکار** چاپ شود. تنظیمِ همین دستگاه، پیش‌فرض **خاموش**
 *  (خاموش ⇒ رفتارِ امروز، بیت‌به‌بیت: هیچ چاپی).
 *
 *    • فقط پس از پاسخِ موفقِ همین دستگاه به گذارِ «قبول» (`new`/… ⇒ `accepted`)؛ قبولی که
 *      دستگاهِ دیگری زد همان‌جا چاپ می‌شود، نه این‌جا — پس یک قبول یک برگه.
 *    • هر سفارش یک بار روی هر دستگاه ([printed]): دو ضربه، پاسخِ دوباره یا بازگشت به
 *      همان سفارش برگهٔ دوم نمی‌سازد. حافظه حدِ [MEMORY] دارد تا تنظیم بی‌اندازه بزرگ نشود.
 */
object OrderAutoPrint {

  const val MEMORY = 200
  private val ID = Regex("^[A-Za-z0-9_-]{1,64}$")

  fun shouldPrint(enabled: Boolean, fromStatus: String, toStatus: String, orderId: String, printed: List<String>): Boolean =
    enabled && toStatus == "accepted" && fromStatus != "accepted" && ID.matches(orderId) && orderId !in printed

  /** شناسه‌های چاپ‌شدهٔ ذخیره‌شده ⇐ فهرست؛ نبوده یا خراب ⇒ خالی (شناسهٔ ناجور دور ریخته می‌شود) */
  fun parse(raw: String?): List<String> =
    raw.orEmpty().split(',').map { it.trim() }.filter { ID.matches(it) }.distinct().takeLast(MEMORY)

  fun remember(printed: List<String>, orderId: String): List<String> = (printed - orderId + orderId).takeLast(MEMORY)

  fun encode(printed: List<String>): String = printed.joinToString(",")
}
