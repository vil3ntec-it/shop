package ir.vil3ntec.tohid.core.pos

import ir.vil3ntec.tohid.core.orders.OrderDraft

/**
 *  فروش با تحویلِ بعدی — POS-38 (§۶.۱): سبدِ صندوق ⇐ «سفارشِ ارسال» در بخشِ سفارش‌ها و تحویل.
 *
 *  ── چرا سفارش، نه فروش ─────────────────────────────────────────────
 *  تصمیمِ صاحب (`OrderFulfillment`، `lib/order-ledger.js`): کالای سفارش وقتی از دکان بیرون رفت
 *  (`out_for_delivery`/`delivered`/`picked_up`) برنامه **خودش** یک فروشِ `o-<سفارش>` ثبت می‌کند و
 *  موجودی همان‌جا کم می‌شود. پس صندوق این‌جا **هیچ فروشی** نمی‌زند (وگرنه دو فروش و دو کسر می‌شد):
 *  فقط سفارشِ دستیِ کارمند (`POST /shop/orders`، همان ORD-21، منبعِ `manual`، ارسال، نقد هنگامِ
 *  تحویل) با قلم‌های همین سبد — هر قلم با شناسهٔ کالا و واحدِ فروش، تا فروشِ تحویل همان کالا را
 *  کم کند — و سبد خالی می‌شود. سفارش به صفِ سفارش‌ها و از آن‌جا به میزِ تحویل و پیک می‌رود.
 *
 *  ── مبلغ ───────────────────────────────────────────────────────────
 *  هر عدد `Long`: مقدار به هزارم (`qtyMilli`)، پول به یک‌صدمِ افغانی. جمعِ هر قلم همان قاعدهٔ سرور
 *  (`order-states.lineTotalMinor`) و صندوق (`SalesEngine.lineTotal`): گرد به افغانیِ کامل، نیمه رو
 *  به بالا — پس جمعی که پیش از ثبت دیده می‌شود همان جمعِ سفارش است. سقف‌ها همان `lib/orders.js`.
 *
 *  ⛔ امروز سرور فقط «نقد هنگامِ تحویل» می‌پذیرد (پرداختِ آنلاین منتظرِ D-08)؛ پیش‌پرداخت در صندوق
 *  این‌جا ساخته نمی‌شود.
 *
 *  Kotlin خالص (commonMain) — بی java.* و android.*؛ آزمون: `DeliverLaterTest`.
 */
object DeliverLater {

  const val SOURCE = "manual"
  const val MAX_LINES = 200
  const val MAX_QTY_MILLI = 10_000_000L
  const val MAX_PRICE_MINOR = 100_000_000_000L
  const val MAX_FEE_MINOR = 100_000_000L
  const val MAX_NAME = 80
  const val MAX_PHONE = 20
  const val MAX_ADDRESS = 300
  const val MAX_LANDMARK = 200
  const val MAX_NOTE = 500
  const val MAX_LINE_NAME = 200
  const val MAX_UNIT = 30

  /** یک قلمِ سبد — `productId` تهی برای قلمِ آزاد (شناسهٔ ردیفِ آزاد کالا نیست و فرستاده نمی‌شود) */
  data class Line(val productId: String, val name: String, val unit: String, val qtyMilli: Long, val unitPriceMinor: Long)

  /** کادرهای پنجره — متنِ خام؛ تمیز کردن و سقف این‌جاست */
  data class Contact(
    val name: String = "",
    val phone: String = "",
    val addressText: String = "",
    val landmark: String = "",
    val fee: String = "",
    val note: String = "",
  )

  /** چرا ساخته نشد — متنش با زبانِ صفحه کارِ برنامه است */
  enum class Problem { EMPTY, TOO_MANY_LINES, NAME, PHONE, ADDRESS, FEE, LINE }

  sealed class Result {
    data class Ok(val body: OrderDraft.Body, val subtotalMinor: Long, val totalMinor: Long) : Result()
    /** [line] نامِ قلمِ نادرست برای [Problem.LINE] */
    data class Error(val problem: Problem, val line: String = "") : Result()
  }

  /** جمعِ یک قلم — همان `lineTotalMinor` ِ سرور: گرد به افغانیِ کامل، نیمه رو به بالا */
  fun lineTotalMinor(qtyMilli: Long, unitPriceMinor: Long): Long {
    require(qtyMilli in 0..MAX_QTY_MILLI && unitPriceMinor in 0..MAX_PRICE_MINOR) { "out of range" }
    return (qtyMilli * unitPriceMinor + 50_000L).floorDiv(100_000L) * 100L
  }

  /** مقدارِ اعشاری (واحدِ فروش) ⇐ هزارم؛ ۰٫۲۵ کیلو ⇐ ۲۵۰ */
  fun milli(quantity: Double): Long = if (quantity.isNaN() || quantity <= 0.0) 0L else kotlin.math.round(quantity * 1000.0).toLong()

  /** قیمتِ اعشاری (افغانی) ⇐ یک‌صدم */
  fun minor(price: Double): Long = if (price.isNaN() || price < 0.0) -1L else kotlin.math.round(price * 100.0).toLong()

  fun build(lines: List<Line>, contact: Contact): Result {
    if (lines.isEmpty()) return Result.Error(Problem.EMPTY)
    if (lines.size > MAX_LINES) return Result.Error(Problem.TOO_MANY_LINES)
    val name = contact.name.trim().take(MAX_NAME).trim()
    if (name.isEmpty()) return Result.Error(Problem.NAME)
    val phone = OrderDraft.latin(contact.phone).take(MAX_PHONE)
    if (phone.isEmpty()) return Result.Error(Problem.PHONE)
    val address = contact.addressText.trim().take(MAX_ADDRESS).trim()
    val landmark = contact.landmark.trim().take(MAX_LANDMARK).trim()
    if (address.isEmpty() && landmark.isEmpty()) return Result.Error(Problem.ADDRESS)
    val fee = if (contact.fee.isBlank()) 0L else OrderDraft.scaled(contact.fee, 2) ?: return Result.Error(Problem.FEE)
    if (fee > MAX_FEE_MINOR) return Result.Error(Problem.FEE)

    val out = ArrayList<OrderDraft.Line>(lines.size)
    var subtotal = 0L
    for (l in lines) {
      val ln = l.name.trim().take(MAX_LINE_NAME).trim()
      if (ln.isEmpty() || l.qtyMilli !in 1..MAX_QTY_MILLI || l.unitPriceMinor !in 0..MAX_PRICE_MINOR) {
        return Result.Error(Problem.LINE, l.name)
      }
      subtotal += lineTotalMinor(l.qtyMilli, l.unitPriceMinor)
      out += OrderDraft.Line(ln, l.qtyMilli, l.unitPriceMinor, productId = l.productId, unit = l.unit.trim().take(MAX_UNIT))
    }
    if (subtotal + fee > MAX_SAFE) return Result.Error(Problem.TOO_MANY_LINES)
    return Result.Ok(
      OrderDraft.Body(
        source = SOURCE,
        fulfillment = "delivery",
        customerName = name,
        customerPhone = phone,
        addressText = address,
        landmark = landmark,
        deliveryFeeMinor = fee,
        lines = out,
        note = contact.note.trim().take(MAX_NOTE).trim(),
        test = false,
      ),
      subtotalMinor = subtotal,
      totalMinor = subtotal + fee,
    )
  }

  /** همان `Number.MAX_SAFE_INTEGER` که سرور جمع را با آن می‌سنجد */
  private const val MAX_SAFE = 9_007_199_254_740_991L
}
