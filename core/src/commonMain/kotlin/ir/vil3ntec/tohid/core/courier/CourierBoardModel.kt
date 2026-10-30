package ir.vil3ntec.tohid.core.courier

/*
 *  خانه، «نقدِ من»، آمار و پیگیریِ حالتِ پیک — منطقِ خالص، بی android.* و بی JSON.
 *
 *  همهٔ عددها از سرور (`GET /api/courier/summary`، `missions[].timeline`) یا از
 *  صفِ خودِ گوشی می‌آیند؛ این‌جا فقط انتخاب و چیدن است، نه ساختنِ داده.
 *  آزمون: `CourierBoardModelTest`.
 */

/** یک رویدادِ خطِ زمانیِ مأموریت به زمانِ سرور — `timeline`ِ `courierView` */
data class MissionStep(
  val kind: String,
  val to: String,
  val at: Long,
  val reason: String = "",
  /** «courier» = کارِ خودِ پیک، «shop» = دکان */
  val by: String = "",
)

data class PeriodStats(
  val from: Long = 0,
  val delivered: Int = 0,
  val failed: Int = 0,
  val collectedCashMinor: Long = 0,
  val fareMinor: Long = 0,
)

data class UnsettledDelivery(
  val orderNo: Long,
  val deliveredAt: Long,
  val expectedCollectMinor: Long,
  val cashCollectedMinor: Long,
  val fareMinor: Long,
)

data class CourierSettlement(
  val settleNo: Long,
  val at: Long,
  val status: String,
  val assignmentsCount: Int,
  val collectedCashMinor: Long,
  val fareTotalMinor: Long,
  val handoverDueMinor: Long,
  val receivedMinor: Long,
  val shortageMinor: Long,
  val excessMinor: Long,
  val reason: String = "",
)

/** پاسخِ `GET /api/courier/summary` — فقط دادهٔ همین پیک */
data class CourierSummary(
  val shopName: String = "",
  val shopPhone: String = "",
  val cashInHandMinor: Long = 0,
  val liveExpectedMinor: Long = 0,
  val cashCapMinor: Long = 0,
  val headroomMinor: Long = 0,
  val unsettled: List<UnsettledDelivery> = emptyList(),
  val settlements: List<CourierSettlement> = emptyList(),
  val today: PeriodStats = PeriodStats(),
  val week: PeriodStats = PeriodStats(),
  val serverTime: Long = 0,
)

/** یک ردیفِ «جزئیاتِ پیگیری»: رویدادِ سرور یا کارِ هنوز در صفِ گوشی */
data class TrackRow(
  val to: String,
  val kind: String,
  val at: Long,
  val by: String,
  val reason: String,
  /** هنوز به دکان نرسیده (زمانِ دستگاه) */
  val pending: Boolean,
  /** همین حالا همین‌جاست */
  val current: Boolean,
)

/** زنگِ پیک: فقط چیزهای واقعیِ همین گوشی */
data class BellItems(val newMissions: Int, val recalls: Int, val rejectedEvents: Int) {
  val total: Int get() = newMissions + recalls + rejectedEvents
}

object CourierBoardModel {

  private const val DAY = 86_400_000L

  /** ترتیبِ «مأموریتِ جاری»: هر چه به مشتری نزدیک‌تر، جلوتر */
  private val PRIORITY = listOf("arrived", "moving", "picked_up", "assigned", "failed", "requested")

  /** مأموریتی که روی کارتِ تیرهٔ خانه می‌نشیند — از کارت‌های زنده (ترتیبِ سرور حفظ) */
  fun current(live: List<MissionCard>): MissionCard? =
    PRIORITY.firstNotNullOfOrNull { s -> live.firstOrNull { it.status == s } }

  /** «کارهای اخیر»: زنده و پایان‌یافته، تازه‌ترین تغییر اول */
  fun recent(live: List<MissionCard>, finished: List<MissionCard>, limit: Int = 5): List<MissionCard> =
    (live + finished).distinctBy { it.id }.sortedByDescending { it.updatedAt }.take(limit)

  /**
   *  جست‌وجوی «شمارهٔ سفارش…»: رقمِ فارسی/عربی هم؛ شماره باید از آغاز جور شود
   *  (۱۰۴ ⇐ ۱۰۴۳)، و متن در نشانه یا نشانی.
   */
  fun search(cards: List<MissionCard>, query: String): List<MissionCard> {
    val q = query.trim()
    if (q.isEmpty()) return emptyList()
    val digits = CourierQueue.latinDigits(q)
    val onlyDigits = digits.isNotEmpty() && q.all { it.isDigit() || it == '#' || it.isWhitespace() }
    return cards.filter { c ->
      if (onlyDigits) c.orderNo.toString().startsWith(digits)
      else c.landmark.contains(q, ignoreCase = true) || c.addressText.contains(q, ignoreCase = true) ||
        c.orderNo.toString() == digits
    }
  }

  fun bell(live: List<MissionCard>, state: QueueState): BellItems = BellItems(
    newMissions = live.count { it.status == "requested" },
    recalls = live.count { it.recallRequested },
    rejectedEvents = state.rejected.size,
  )

  /**
   *  «جزئیاتِ پیگیری»: رویدادهای سرور به ترتیبِ زمان + کارهای هنوز در صف (زمانِ
   *  دستگاه)، تازه‌ترین بالا. ردیفِ «جاری» همان آخرین وضعیتی است که کارت نشان
   *  می‌دهد. رویدادهای بی تغییرِ وضعیت (عکس، پس‌خواستن) هم می‌آیند.
   */
  fun track(card: MissionCard, state: QueueState): List<TrackRow> {
    val server = card.timeline.sortedBy { it.at }.map {
      TrackRow(it.to, it.kind, it.at, it.by, it.reason, pending = false, current = false)
    }
    var s = card.timeline.lastOrNull { it.kind == "status" || it.kind == "created" }?.to ?: card.status
    val queued = ArrayList<TrackRow>()
    for (ev in CourierQueue.pendingFor(state, card.id)) {
      if (!CourierFlow.canSend(s, ev.type)) continue
      s = ev.type
      queued += TrackRow(ev.type, "status", ev.deviceTime, "courier", ev.reason, pending = true, current = false)
    }
    val all = (server + queued).reversed()
    val idx = all.indexOfFirst { (it.kind == "status" || it.kind == "created") && it.to == card.status }
    return all.mapIndexed { i, r -> if (i == idx) r.copy(current = true) else r }
  }

  /** آغازِ روزِ گوشی (`offsetMillis` = اختلافِ منطقهٔ زمانی با UTC) */
  fun dayStart(now: Long, offsetMillis: Long): Long =
    (now + offsetMillis).floorDiv(DAY) * DAY - offsetMillis

  /** آغازِ هفته از شنبه (تقویمِ افغانستان) */
  fun weekStart(now: Long, offsetMillis: Long): Long {
    val day = dayStart(now, offsetMillis)
    val epochDay = (day + offsetMillis).floorDiv(DAY)
    //  ۱۹۷۰/۰۱/۰۳ شنبه بود (روزِ ۲ از آغازِ شمارش)
    val sinceSaturday = (epochDay - 2).mod(7L)
    return day - sinceSaturday * DAY
  }

  /** پُریِ سقف: (در دست + در راه) / سقف — ۰ تا ۱، بی سقف ۰ */
  fun capUse(s: CourierSummary): Float =
    if (s.cashCapMinor <= 0) 0f
    else ((s.cashInHandMinor + s.liveExpectedMinor).toDouble() / s.cashCapMinor).coerceIn(0.0, 1.0).toFloat()
}
