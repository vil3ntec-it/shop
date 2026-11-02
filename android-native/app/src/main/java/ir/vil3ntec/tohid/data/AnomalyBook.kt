package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.calendar.CivilDays
import ir.vil3ntec.tohid.core.risk.Anomalies
import ir.vil3ntec.tohid.core.risk.CashierAlerts

/*
 *  AI-08 — دفترِ دکان ⇐ ورودیِ `core/risk/Anomalies` (تشخیصِ ناهنجاریِ صندوق و موجودی).
 *
 *  همهٔ قاعده و آمار در هسته است؛ این‌جا فقط از دفترِ همین دستگاه (ShopData) ساخته می‌شود:
 *   • هشدارهای RSK-10 همان `ReportBoard.alertsOf` ِ زبانهٔ «ضدتقلب» (از بیرون می‌آیند، دوباره شمرده نمی‌شوند)؛
 *   • صندوق‌های بسته در بازه: مورد انتظار همان `CashShift.totalsOf` و شمرده همان مبلغِ `shift_close`؛
 *   • «اصلاح» و «تعدیلِ شمارش» ِ منفیِ موجودی با بهای خریدِ کالا؛
 *   • قلم‌های فروشِ لغونشده با بهای همان قلم (بی قلمِ آزاد و بی قلمِ تخفیفِ خودکار)؛
 *   • ساعتِ هر فاکتور با اختلافِ ساعتِ همین دستگاه؛
 *   • جمعِ روزانهٔ برگشتی، تخفیف (کلِ دکان و هر فروشنده) و کمِ «اصلاح» در بازه و [BASELINE_DAYS] روزِ پیش از پایانش.
 *  کمینهٔ پهنای آمار ۲٪ ِ میانگینِ فروشِ روزانه (دست‌کم ۱ ؋)، تا یک برگشتیِ کوچک در دکانِ پرفروش «بیرون از معمول» نشود.
 *
 *  فقط خواندنی است: چیزی نمی‌نویسد، همگام نمی‌کند و سرور نمی‌خواهد. بی تنظیمِ `AnomalyPref` صدا زده نمی‌شود.
 */
object AnomalyBook {

  const val BASELINE_DAYS = 30
  const val MAX_RANGE_DAYS = 366
  val ADJUST_TYPES = setOf("adjustment", "count_adjust")

  /** خاموش ⇒ `null`: صفحه همان کادرِ RSK-10 ِ پیشین را نشان می‌دهد */
  fun view(
    on: Boolean, d: ShopData, from: String, to: String, alerts: List<CashierAlerts.Alert>, offsetMinutes: Int, today: String,
  ): List<Anomalies.Finding>? {
    if (!on) return null
    val (input, policy) = prepare(d, from, to, alerts, offsetMinutes, today)
    return Anomalies.detect(input, policy)
  }

  private fun minuteOf(createdAt: Long, offsetMinutes: Int): Int = (createdAt.floorDiv(60_000L) + offsetMinutes).mod(Anomalies.MINUTES_PER_DAY.toLong()).toInt()

  fun prepare(
    d: ShopData, from: String, to: String, alerts: List<CashierAlerts.Alert>, offsetMinutes: Int, today: String,
  ): Pair<Anomalies.Input, Anomalies.Policy> {
    val toJdn = CivilDays.jdnOfIso(to) ?: CivilDays.jdnOfIso(today) ?: return Anomalies.Input(cashierAlerts = alerts) to Anomalies.Policy()
    //  «همهٔ تاریخ‌ها» (`0000-01-01`) یا تاریخِ نادرست ⇒ تا [MAX_RANGE_DAYS] روزِ پیش از پایان
    val fromJdn = maxOf(CivilDays.jdnOfIso(from) ?: Int.MIN_VALUE, toJdn - MAX_RANGE_DAYS + 1).coerceAtMost(toJdn)
    val base = minOf(fromJdn, toJdn - BASELINE_DAYS + 1)
    val days = toJdn - base + 1
    fun inRange(j: Int) = j in fromJdn..toJdn
    val products = d.products.associateBy { it.id }

    val returns = LongArray(days)
    val discounts = LongArray(days)
    val adjusted = LongArray(days)
    val bySeller = HashMap<String, LongArray>()
    var salesTotal = 0L

    val live = HashMap<String, Sale>()
    val saleDay = HashMap<String, Int>()
    val times = ArrayList<Anomalies.SaleAt>()
    for (s in d.sales) {
      if (s.status == "cancelled") continue
      val j = CivilDays.jdnOfIso(s.date) ?: continue
      if (j !in base..toJdn) continue
      live[s.id] = s
      saleDay[s.id] = j
      salesTotal += PurchaseOrderBook.minor(s.finalTotal)
      val disc = PurchaseOrderBook.minor(s.discount)
      if (disc > 0) {
        discounts[j - base] += disc
        val who = s.seller.trim()
        if (who.isNotEmpty()) bySeller.getOrPut(who) { LongArray(days) }[j - base] += disc
      }
      if (inRange(j) && s.createdAt > 0) times += Anomalies.SaleAt(s.id, s.seller, j, minuteOf(s.createdAt, offsetMinutes), PurchaseOrderBook.minor(s.finalTotal))
    }

    val lines = ArrayList<Anomalies.Line>()
    for (i in d.saleItems) {
      val j = saleDay[i.saleId] ?: continue
      if (!inRange(j) || i.productId.isBlank() || i.promo.isNotEmpty()) continue
      val s = live.getValue(i.saleId)
      lines += Anomalies.Line(
        i.saleId, s.invoiceLabel()?.let { "#$it" } ?: "", s.seller, j,
        PurchaseOrderBook.milli(i.quantity), PurchaseOrderBook.minor(i.totalPrice), PurchaseOrderBook.minor(i.purchasePrice),
      )
    }

    for (r in d.saleReturns) {
      val j = CivilDays.jdnOfIso(r.date) ?: continue
      if (j in base..toJdn) returns[j - base] += maxOf(0L, PurchaseOrderBook.minor(r.amount))
    }

    val adjusts = ArrayList<Anomalies.Adjust>()
    for (m in d.stockMovements) {
      if (m.type !in ADJUST_TYPES || m.qty >= 0) continue
      val j = CivilDays.jdnOfIso(m.date) ?: continue
      if (j !in base..toJdn) continue
      val p = products[m.productId]
      val cost = PurchaseOrderBook.minor(p?.purchasePrice ?: 0.0)
      val qty = PurchaseOrderBook.milli(m.qty)
      if (cost > 0) adjusted[j - base] += (-qty).coerceAtMost(Anomalies.MAX_FACTOR) * cost.coerceAtMost(Anomalies.MAX_FACTOR) / 1000
      if (inRange(j)) adjusts += Anomalies.Adjust(m.id, m.productId, p?.name ?: "", m.userId ?: "", j, qty, cost)
    }

    val shifts = CashShift.list(d).mapNotNull { s ->
      val close = s.close ?: return@mapNotNull null
      val j = CivilDays.jdnOfIso(close.date) ?: return@mapNotNull null
      if (!inRange(j)) return@mapNotNull null
      Anomalies.Shift(
        s.open.id, (close.by ?: s.open.by ?: "").trim(), j,
        PurchaseOrderBook.minor(CashShift.totalsOf(d, s, close.createdAt).expected), PurchaseOrderBook.minor(close.amount),
      )
    }

    val series = listOf(
      Anomalies.Series(Anomalies.Kind.DAY_RETURNS, "", base, returns.toList()),
      Anomalies.Series(Anomalies.Kind.DAY_DISCOUNTS, "", base, discounts.toList()),
      Anomalies.Series(Anomalies.Kind.DAY_ADJUST, "", base, adjusted.toList()),
    ) + bySeller.keys.sorted().map { who -> Anomalies.Series(Anomalies.Kind.DAY_DISCOUNTS, who, base, bySeller.getValue(who).toList()) }

    val policy = Anomalies.Policy(minSpreadMinor = maxOf(100L, salesTotal / days / 50))
    return Anomalies.Input(alerts, CashierAlerts.Policy(), shifts, adjusts, lines, times, series, fromJdn) to policy
  }
}
