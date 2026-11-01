package ir.vil3ntec.tohid.core.purchase

/**
 *  زمانِ تأمینِ هر تأمین‌کننده و پیشنهادِ مقدارِ سفارش — PUR-10 و بخشِ «از پیشنهادها» ِ PUR-03 (§۶.۶).
 *
 *  ── زمانِ تأمین ─────────────────────────────────────────────────────
 *  از خودِ سفارش‌های خرید سنجیده می‌شود، نه حدس: برای هر سفارشِ فرستاده‌ای که رسید گرفته، روزهای
 *  «فرستادن ⇐ نخستین رسید» (رو به بالا: چند ساعت = یک روز). میانه از تازه‌ترین [MAX_SAMPLES] سفارش؛
 *  شمارِ زوج ⇒ میانهٔ بالا (برای برنامه‌ریزی محتاطانه). سفارشی نیست ⇒ `null` و پیشنهاد با
 *  [DEFAULT_LEAD_DAYS] و اطمینانِ پایین‌تر.
 *
 *  ── مقدار ───────────────────────────────────────────────────────────
 *  مصرفِ روزانه = فروشِ واقعیِ پنجره ÷ روزهای پنجره (بی گرد تا آخر، با `mulDivRound` ِ هسته).
 *  نقطهٔ سفارش = مصرفِ زمانِ تأمین + «حدِ کم» (موجودیِ اطمینان). جایگاه = موجودی + در راه (باقی‌ماندهٔ
 *  سفارش‌های باز). جایگاه ≤ نقطهٔ سفارش ⇒ مقدار = مصرفِ (زمانِ تأمین + روزهای پوشش) + حدِ کم − جایگاه،
 *  رو به بالا تا واحدِ کامل. سفارشِ واقعی هرگز خودکار ثبت نمی‌شود (INV-13): این فقط پیشنهاد است.
 *  پول در کار نیست؛ مقدار هزارم.
 */
object ReorderCore {

  const val DAY_MS = 86_400_000L
  const val MAX_SAMPLES = 10
  const val DEFAULT_LEAD_DAYS = 7
  const val DEFAULT_COVER_DAYS = 14
  const val DEFAULT_WINDOW_DAYS = 30

  /** یک سفارشِ فرستاده و نخستین رسیدش (میلی‌ثانیه) */
  data class Delivery(val sentAt: Long, val firstReceiptAt: Long)

  data class Lead(
    /** میانهٔ روزها؛ `null` = هنوز سفارشِ رسیده‌ای نیست */
    val days: Int?,
    val samples: Int,
  )

  fun leadTime(deliveries: List<Delivery>): Lead {
    val days = deliveries.filter { it.sentAt > 0 && it.firstReceiptAt >= it.sentAt }
      .sortedByDescending { it.sentAt }
      .take(MAX_SAMPLES)
      .map { ((it.firstReceiptAt - it.sentAt + DAY_MS - 1) / DAY_MS).toInt() }
      .sorted()
    if (days.isEmpty()) return Lead(null, 0)
    return Lead(days[days.size / 2], days.size)
  }

  enum class Reason(val wire: String) {
    /** جایگاه به نقطهٔ سفارش رسیده (فروش و زمانِ تأمین) */
    REORDER_POINT("reorder_point"),
    /** فروشی در پنجره نیست، ولی موجودی زیرِ «حدِ کم» است */
    BELOW_MIN("below_min"),
    /** کافی است */
    ENOUGH("enough"),
  }

  enum class Confidence(val wire: String) { HIGH("high"), MEDIUM("medium"), LOW("low") }

  data class Input(
    val onHandMilli: Long,
    /** باقی‌ماندهٔ سفارش‌های خریدِ باز برای همین کالا */
    val onOrderMilli: Long,
    val minStockMilli: Long,
    /** فروشِ خالصِ (فروش − برگشت) [windowDays] روزِ گذشته */
    val soldMilli: Long,
    val windowDays: Int = DEFAULT_WINDOW_DAYS,
    /** `null` ⇒ زمانِ تأمینِ سنجیده نیست و [DEFAULT_LEAD_DAYS] به کار می‌رود */
    val leadDays: Int?,
    val coverDays: Int = DEFAULT_COVER_DAYS,
    /** کالای شمارشی (عدد، بسته…) ⇒ رو به بالا تا واحدِ کامل؛ وزنی/حجمی ⇒ هزارم */
    val wholeUnits: Boolean = true,
  )

  data class Suggestion(
    val qtyMilli: Long,
    val reason: Reason,
    val confidence: Confidence,
    val dailyMilli: Long,
    val reorderPointMilli: Long,
    val positionMilli: Long,
    val leadDaysUsed: Int,
  )

  fun suggest(i: Input): Suggestion {
    require(i.windowDays > 0 && i.coverDays >= 0) { "reorder-core: windowDays > 0, coverDays ≥ 0" }
    require(i.leadDays == null || i.leadDays >= 0) { "reorder-core: leadDays ≥ 0" }
    val lead = i.leadDays ?: DEFAULT_LEAD_DAYS
    val sold = maxOf(0L, i.soldMilli)
    val min = maxOf(0L, i.minStockMilli)
    val w = i.windowDays.toLong()
    val daily = PurchaseOrderCore.mulDivRound(sold, 1, w)
    val reorderPoint = PurchaseOrderCore.add(PurchaseOrderCore.mulDivRound(sold, lead.toLong(), w), min)
    val position = PurchaseOrderCore.add(i.onHandMilli, maxOf(0L, i.onOrderMilli))
    val confidence = when {
      sold > 0 && i.leadDays != null -> Confidence.HIGH
      sold > 0 || i.leadDays != null -> Confidence.MEDIUM
      else -> Confidence.LOW
    }
    val due = if (sold > 0) position <= reorderPoint else min > 0 && position < min
    if (!due) return Suggestion(0, Reason.ENOUGH, confidence, daily, reorderPoint, position, lead)
    val target = PurchaseOrderCore.add(PurchaseOrderCore.mulDivRound(sold, (lead + i.coverDays).toLong(), w), min)
    var need = maxOf(0L, target - position)
    if (i.wholeUnits) need = (need + PurchaseOrderCore.MILLI - 1) / PurchaseOrderCore.MILLI * PurchaseOrderCore.MILLI
    if (need == 0L) need = if (i.wholeUnits) PurchaseOrderCore.MILLI else 1L
    return Suggestion(need, if (sold > 0) Reason.REORDER_POINT else Reason.BELOW_MIN, confidence, daily, reorderPoint, position, lead)
  }
}
