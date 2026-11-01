package ir.vil3ntec.tohid.core.pos

import ir.vil3ntec.tohid.core.calendar.CivilDays

/**
 *  قیمت‌پرسی بی فروش (POS-15، Ctrl+I): قیمت‌ها، موجودی، آخرین خرید و نزدیک‌ترین انقضای یک کالا —
 *  بی افزودن به فاکتور و بی هیچ نوشتن.
 *
 *  ورودی را صدازننده از دفتر می‌سازد (خریدها و محموله‌های انقضادار با ماندهٔ FEFO ِ `ExpiryEngine`)؛
 *  این‌جا فقط انتخاب و حساب است. پول `Long` به پول (یک‌صدمِ افغانی)، مقدار به هزارم.
 *
 *  Kotlin خالص است — بی java.*، بی android.*، بی کتابخانه.
 */
object PriceCheck {

  /** «نزدیکِ انقضا» تا همین چند روز — همان `ExpiryEngine.NEAR_DAYS` ِ برنامه */
  const val NEAR_DAYS = 30

  data class Purchase(val date: String, val createdAt: Long, val unitMinor: Long, val qtyMilli: Long, val supplier: String = "")

  /** محمولهٔ انقضادار با ماندهٔ همان محموله */
  data class Lot(val expiry: String, val remainingMilli: Long)

  /**
   *  @param last آخرین خریدِ این کالا (تاریخ، و در یک روز آخرین ثبت)
   *  @param nearest نزدیک‌ترین محمولهٔ هنوز نگذشته با مانده؛ [nearestDays] روزِ مانده تا انقضا (۰ = امروز)
   *  @param expiredMilli ماندهٔ محموله‌های گذشته (فروختنی نیست وقتی «کالای منقضی فروخته نشود» روشن است)
   *  @param marginBp سودِ قیمتِ فروش روی قیمتِ خرید، به ده‌هزارم (۲۵۰۰ = ۲۵٪)؛ بی یکی از دو ⇒ `null`
   */
  data class Card(
    val last: Purchase? = null,
    val nearest: Lot? = null,
    val nearestDays: Int? = null,
    val expiredMilli: Long = 0,
    val marginBp: Long? = null,
  ) {
    val near: Boolean get() = nearestDays != null && nearestDays <= NEAR_DAYS
  }

  fun lastPurchase(list: List<Purchase>): Purchase? =
    list.filter { CivilDays.jdnOfIso(it.date.take(10)) != null }
      .maxWithOrNull(compareBy<Purchase>({ it.date.take(10) }, { it.createdAt }))

  /** سود روی فروش: (فروش − خرید) ÷ فروش، گرد به پایین؛ زیان منفی است */
  fun marginBp(saleMinor: Long, costMinor: Long): Long? {
    if (saleMinor <= 0 || costMinor <= 0) return null
    return (saleMinor - costMinor) * 10_000 / saleMinor
  }

  fun card(saleMinor: Long, costMinor: Long, purchases: List<Purchase>, lots: List<Lot>, today: String): Card {
    val t = CivilDays.jdnOfIso(today.take(10))
    var nearest: Lot? = null
    var nearestDays: Int? = null
    var expired = 0L
    if (t != null) for (l in lots) {
      if (l.remainingMilli <= 0) continue
      val e = CivilDays.jdnOfIso(l.expiry.take(10)) ?: continue
      val days = e - t
      if (days < 0) { expired += l.remainingMilli; continue }
      if (nearestDays == null || days < nearestDays) { nearest = l; nearestDays = days }
    }
    return Card(lastPurchase(purchases), nearest, nearestDays, expired, marginBp(saleMinor, costMinor))
  }
}
