package ir.vil3ntec.tohid.data

/**
 *  هشدارِ انقضا — **یکی برای هر محموله**، با مرزِ روشنِ «منقضی‌شده» و «نزدیکِ انقضا» (خواستهٔ صاحب‌کار ۱۴۰۵/۰۸/۲۴:
 *  «هر کدام که تاریخشان کم مانده بود هشدار بیاید»).
 *
 *  منبع فقط [ExpiryEngine.alerts]: ماندهٔ FEFO ِ هر ورودِ انقضادار، همان که انبار، داشبورد و گزارش می‌خوانند، با همان
 *  «نزدیک به انقضا» ِ قابلِ تنظیمِ همین دستگاه (`ExpiryPref`، پیش‌فرض ۳۰ روز). پس محموله‌ای که فروخته، با اصلاح برداشته
 *  یا حذف شد مانده‌ای ندارد و هشدارش خودبه‌خود می‌رود — هیچ «دیده شد/پاک شد» ای نگه داشته نمی‌شود که کهنه بماند.
 *
 *  یکتایی: یک محموله = همان کالا + همان تاریخِ انقضا + همان سری (`batch`، بی فاصلهٔ دو سر). دو رسیدِ یک سری یک هشدارند
 *  با جمعِ مانده؛ رسیدهای بی‌سریِ یک کالا با یک انقضا هم یکی‌اند (برای دکان‌دار همان یک محموله است). آزمون: `ExpiryAlertsTest`.
 */
object ExpiryAlerts {

  data class Alert(
    val productId: String,
    val name: String,
    /** `yyyy-MM-dd` */
    val expiryDate: String,
    val batch: String,
    /** ماندهٔ همین محموله به واحدِ کالا */
    val remaining: Double,
    /** منفی ⇒ گذشته */
    val daysLeft: Long,
  ) {
    val expired: Boolean get() = daysLeft < 0
  }

  /** محموله‌های [ExpiryEngine.alerts] ⇒ یک هشدار برای هر محموله؛ زودترین اول، سپس نام */
  fun of(lots: List<ExpiryEngine.Lot>): List<Alert> =
    lots.groupBy { Triple(it.product.id, it.entry.expiryDate.trim(), it.entry.batch.trim()) }
      .map { (key, group) ->
        Alert(key.first, group.first().product.name, key.second, key.third, group.sumOf { it.remaining }, group.first().daysLeft)
      }
      .sortedWith(compareBy<Alert>({ it.daysLeft }, { it.name }, { it.batch }))

  /** همان [of] از دفتر: منقضی یا تا [days] روزِ دیگر */
  fun of(d: ShopData, today: String, days: Int = ExpiryEngine.nearDays): List<Alert> = of(ExpiryEngine.alerts(d, today, days))

  /** شمار و نامِ کالاها (یکتا، به ترتیبِ هشدارها) جدا برای «منقضی‌شده» و «نزدیکِ انقضا» */
  data class Counts(val expired: Int, val near: Int, val expiredNames: List<String>, val nearNames: List<String>)

  fun counts(alerts: List<Alert>): Counts {
    val (gone, near) = alerts.partition { it.expired }
    return Counts(gone.size, near.size, gone.map { it.name }.distinct(), near.map { it.name }.distinct())
  }

  /**
   *  یادآوریِ روزانه (`Reminders`) دفتر را نمی‌خواند، فقط خلاصهٔ کوچکِ کنارِ آن را (`LedgerSummary`): محموله‌هایی که تا
   *  [ExpiryEngine.MAX_NEAR_DAYS] روزِ دیگر منقضی می‌شوند، با تاریخِ خودشان — تا هر صبح «امروز» ِ همان صبح سنجیده شود.
   */
  fun lines(d: ShopData, today: String): List<LedgerSummary.ExpiryLine> =
    of(d, today, ExpiryEngine.MAX_NEAR_DAYS).map { LedgerSummary.ExpiryLine(it.productId, it.name, it.expiryDate, it.batch, it.remaining) }

  /** خطوطِ خلاصه ⇒ هشدارهای [today] با مرزِ [days] (تاریخِ ناخوانا کنار می‌رود) */
  fun ofLines(lines: List<LedgerSummary.ExpiryLine>, today: String, days: Int = ExpiryEngine.nearDays): List<Alert> =
    lines.mapNotNull { l ->
      if (!ExpiryEngine.validDate(l.expiry) || l.expiry.isBlank()) return@mapNotNull null
      Alert(l.id, l.name, l.expiry, l.batch, l.left, ExpiryEngine.daysLeft(l.expiry, today))
    }.filter { it.daysLeft <= days }.sortedWith(compareBy<Alert>({ it.daysLeft }, { it.name }, { it.batch }))
}
