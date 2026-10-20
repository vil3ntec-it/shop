package ir.vil3ntec.tohid.core.delivery

import ir.vil3ntec.tohid.core.orders.OrderText

/**
 *  دفترِ نقدِ هر پیک (DLV-11) — همان قاعدهٔ سرور، بی شبکه.
 *
 *  «درگیر» = نقدِ گرفته و تسویه‌نشده + نقدی که از مأموریت‌های زنده باید
 *  بگیرد. سرور (`assign`) سپردنی را رد می‌کند که درگیر + نقدِ سفارشِ تازه
 *  از سقف بیشتر شود (`cash_cap_exceeded`)، مگر مدیر آگاهانه بپذیرد؛ این‌جا
 *  همان حساب است تا میز پیش از فرستادن بگوید و سپردنِ خودکار هرگز پیکِ
 *  پُر را انتخاب نکند. داور همیشه سرور است.
 */
object CourierCash {

  fun committed(c: CourierRow): Long = c.cashHeldMinor + c.liveExpectedMinor

  /** جای خالیِ نقد تا سقف (هرگز منفی نیست) */
  fun headroom(c: CourierRow): Long = maxOf(0L, c.cashCapMinor - committed(c))

  /** همان شرطِ سرور: درگیر + این سفارش ≤ سقف */
  fun fits(c: CourierRow, expectedCollectMinor: Long): Boolean =
    committed(c) + maxOf(0L, expectedCollectMinor) <= c.cashCapMinor

  /** به سقف رسیده ⇒ سفارشِ نقدیِ تازه نمی‌گیرد */
  fun atCap(c: CourierRow): Boolean = committed(c) >= c.cashCapMinor

  /** از سقف گذشته (با پذیرشِ آگاهانهٔ مدیر) ⇒ هیچ سفارشِ خودکاری، حتی پیش‌پرداخت */
  fun overCap(c: CourierRow): Boolean = committed(c) > c.cashCapMinor

  /** ۰ تا ۱۰۰ برای نوارِ پُر شدن؛ سقفِ صفر یعنی «نقد نگیرد» */
  fun usePercent(c: CourierRow): Int = when {
    c.cashCapMinor <= 0L -> if (committed(c) > 0L) 100 else 0
    else -> minOf(100L, committed(c) * 100 / c.cashCapMinor).toInt()
  }

  /** دلیلِ فارسیِ «سفارشِ خودکار نمی‌گیرد» — تهی یعنی می‌گیرد */
  fun autoBlockReason(c: CourierRow): String? = when {
    !c.active -> "غیرفعال است"
    overCap(c) -> "از سقفِ نقد گذشته (${money(committed(c))} از ${money(c.cashCapMinor)} افغانی) — تا تسویه، سفارشِ خودکار نمی‌گیرد"
    atCap(c) -> "به سقفِ نقد رسیده (${money(committed(c))} از ${money(c.cashCapMinor)} افغانی) — تا تسویه، سفارشِ نقدیِ خودکار نمی‌گیرد"
    else -> null
  }

  /**
   *  دفترِ یک پیک: گرفته (همهٔ تسویه‌ها + آن‌چه هنوز در دست دارد)، تحویل‌داده
   *  به دکان (شمرده‌شده در تسویه‌ها)، مانده در دستِ پیک، کسری و اضافه.
   */
  data class Ledger(
    val collectedMinor: Long,
    val handedBackMinor: Long,
    val outstandingMinor: Long,
    val faresMinor: Long,
    val shortageMinor: Long,
    val excessMinor: Long,
  )

  fun ledger(c: CourierRow, sums: SettlementSums): Ledger = Ledger(
    collectedMinor = sums.collectedCashMinor + c.cashHeldMinor,
    handedBackMinor = sums.receivedMinor,
    outstandingMinor = c.cashHeldMinor,
    faresMinor = sums.fareTotalMinor,
    shortageMinor = sums.shortageMinor,
    excessMinor = sums.excessMinor,
  )

  private fun money(minor: Long) = OrderText.money(minor)
}

/**
 *  حسابِ تسویهٔ پایانِ روز (DLV-12، DEL-02) — آینهٔ `settlementTotals`ِ
 *  `server/src/lib/delivery-states.js` تا مدیر پیش از تأیید همان عددی را
 *  ببیند که سرور ثبت می‌کند:
 *
 *    expected      = Σ مبلغِ موردِ انتظار از مشتری
 *    collected     = Σ آن‌چه پیک واقعاً گرفت
 *    collectionDiff= collected − expected  (اختلافِ مشتری؛ پنهان نمی‌ماند)
 *    handoverDue   = collected − (کرایه، اگر از همین نقد برداشته شد)
 *    difference    = received − handoverDue ⇒ کسری | اضافه
 *
 *  ⛔ هر اختلاف (کسری، اضافه یا اختلافِ مشتری) دلیل می‌خواهد — همان ۴۰۰ِ سرور.
 */
object SettlementMath {

  data class Totals(
    val count: Int,
    val expectedCashMinor: Long,
    val collectedCashMinor: Long,
    val collectionDiffMinor: Long,
    val fareTotalMinor: Long,
    val farePaidFromCash: Boolean,
    val handoverDueMinor: Long,
    val handedOverMinor: Long,
    val receivedMinor: Long,
    val differenceMinor: Long,
    val shortageMinor: Long,
    val excessMinor: Long,
  ) {
    /** همان شرطِ `reason_required`ِ سرور */
    val reasonRequired: Boolean get() = shortageMinor != 0L || excessMinor != 0L || collectionDiffMinor != 0L

    /** پیک چیزی را که می‌گوید داد با شمارشِ مدیر یکی نیست */
    val handedOverMismatch: Boolean get() = handedOverMinor != receivedMinor
  }

  fun totals(
    rows: List<SettleRow>,
    handedOverMinor: Long = 0,
    receivedMinor: Long = 0,
    farePaidFromCash: Boolean = false,
  ): Totals {
    var expected = 0L
    var collected = 0L
    var fares = 0L
    for (r in rows) {
      expected += r.expectedCollectMinor
      collected += r.cashCollectedMinor
      fares += r.fareMinor
    }
    val due = if (farePaidFromCash) collected - fares else collected
    val diff = receivedMinor - due
    return Totals(
      count = rows.size,
      expectedCashMinor = expected,
      collectedCashMinor = collected,
      collectionDiffMinor = collected - expected,
      fareTotalMinor = fares,
      farePaidFromCash = farePaidFromCash,
      handoverDueMinor = due,
      handedOverMinor = handedOverMinor,
      receivedMinor = receivedMinor,
      differenceMinor = diff,
      shortageMinor = if (diff < 0) -diff else 0L,
      excessMinor = if (diff > 0) diff else 0L,
    )
  }
}
