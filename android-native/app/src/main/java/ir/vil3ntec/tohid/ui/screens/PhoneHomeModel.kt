package ir.vil3ntec.tohid.ui.screens

import ir.vil3ntec.tohid.core.calendar.CivilDays
import ir.vil3ntec.tohid.core.calendar.SolarHijri

/*
 *  خانهٔ گوشی، نسخهٔ ۳ (r7 خانه، r6 کارتِ کششی، r8 آمار) — منطقِ خالص.
 *
 *  هیچ‌کدام Compose یا اندروید نمی‌خواهد تا با آزمونِ سادهٔ JVM سنجیده شود:
 *  ماشینِ حالتِ کارتِ کششی، کاشی‌های «بخش‌ها»، هشدارها و بازه‌های نمودار.
 */

/**
 *  کارتِ سیاهِ کششی (r6) — فقط با کشیدنِ صفحه به پایین **از بالای صفحه**.
 *
 *  • لمس که شروع شد، `touchDown(atTop)` می‌گوید صفحه همان لحظه بالا بود یا نه؛
 *    کشیدنی که از وسطِ صفحه شروع شده (اسکرولِ معمولی) هرگز کارت را نمی‌آورد،
 *    حتی اگر در همان حرکت به بالا برسد و ادامه دهد.
 *  • فقط حرکتِ انگشت (نه پرتابِ اسکرول) شمرده می‌شود — `pullDown` را همان‌جا
 *    صدا می‌زنند که اسکرول دیگر جا نداشت و منبع «ورودیِ کاربر» بود.
 *  • رها کردن: از آستانه گذشته، یا پرتابِ تند پس از کششِ کوتاه ⇒ نمایان؛
 *    وگرنه برمی‌گردد.
 *  • نمایان: لمسِ بیرون، هل دادن به بالا یا دکمهٔ برگشت ⇒ پنهان.
 */
object PullCard {
  /** `pull` پیکسلِ کشیده‌شده (با مقاومت)، `armed` یعنی لمس از بالای صفحه شروع شد */
  data class State(
    val shown: Boolean = false,
    val pull: Float = 0f,
    val dragging: Boolean = false,
    val armed: Boolean = false,
  )

  /**
   *  @param threshold کششی که به‌تنهایی کارت را می‌آورد
   *  @param flingMin کمترین کشش برای «کشیدنِ تند»
   *  @param flingVelocity سرعتِ رو به پایین (px/s) که کششِ کوتاه را بس می‌کند
   */
  data class Config(val threshold: Float, val flingMin: Float, val flingVelocity: Float)

  /** مقاومتِ کش: هر پیکسلِ انگشت نیم پیکسل کارت را پایین می‌آورد */
  const val RESIST = 0.5f

  fun touchDown(s: State, atTop: Boolean): State =
    if (s.shown) s else s.copy(armed = atTop, pull = 0f, dragging = false)

  /** انگشت `dy` پیکسل پایین رفت و اسکرول جایی برای خوردنش نداشت */
  fun pullDown(s: State, dy: Float): State =
    if (s.shown || !s.armed || dy <= 0f) s else s.copy(pull = s.pull + dy * RESIST, dragging = true)

  /**
   *  انگشت `dy` پیکسل بالا رفت. تا کارتِ نیمه‌کشیده هست، اول همان جمع می‌شود
   *  (و صفحه اسکرول نمی‌شود). دومی = چند پیکسل از حرکت خورده شد.
   */
  fun pullUp(s: State, dy: Float): Pair<State, Float> {
    if (s.shown || s.pull <= 0f || dy <= 0f) return s to 0f
    val take = minOf(s.pull, dy * RESIST)
    return s.copy(pull = s.pull - take) to take / RESIST
  }

  /** انگشت رها شد؛ `velocityY` مثبت یعنی رو به پایین */
  fun release(s: State, velocityY: Float, c: Config): State {
    if (s.shown) return s.copy(dragging = false)
    val open = s.pull >= c.threshold || (s.pull >= c.flingMin && velocityY >= c.flingVelocity)
    return if (open) State(shown = true) else State()
  }

  /** لمسِ بیرونِ کارت، هل دادن به بالا، یا دکمهٔ برگشت */
  fun dismiss(@Suppress("UNUSED_PARAMETER") s: State): State = State()

  /** فقط برای پیش‌نمایش و عکس */
  fun forceShown(): State = State(shown = true)

  /** ۰ = پنهان، ۱ = کامل پایین آمده */
  fun progress(s: State, revealPx: Float): Float =
    if (s.shown) 1f else if (revealPx <= 0f) 0f else (s.pull / revealPx).coerceIn(0f, 1f)
}

/**
 *  «بخش‌ها» — شبکهٔ ۴×۲ِ r7؛ دسترسیِ سریعِ صاحب دکان به هر بخش.
 *
 *  هشت خانه همیشه پر است و هشتمی «بیشتر». کارمندان فقط برای مدیر (همان سدِ
 *  «بیشتر»)، سفارش‌ها فقط وقتی سفارشِ آنلاین برای این دکان روشن است؛ جای
 *  خالی با خرید و بعد تاریخچه پر می‌شود.
 */
object HomeSections {
  data class Tile(val id: String, val label: String, val route: String)

  private val CORE = listOf(
    Tile("products", "کالاها", "products"),
    Tile("warehouse", "انبار", "warehouse"),
    Tile("debtors", "قرض‌داران", "debtors"),
    Tile("expenses", "مصارف", "expenses"),
    Tile("reports", "گزارش‌ها", "reports"),
  )
  private val TEAM = Tile("team", "کارمندان", "team")
  private val ORDERS = Tile("orders", "سفارش‌ها", "orders")
  private val FILL = listOf(
    Tile("purchasing", "خرید", "purchasing"),
    Tile("sales", "تاریخچه", "sales"),
    Tile("receipts", "رسیدها", "receipts"),
  )
  val MORE = Tile("more", "بیشتر", "more")

  fun tiles(canManage: Boolean, ordersLive: Boolean): List<Tile> {
    val out = CORE.toMutableList()
    if (canManage) out += TEAM
    if (ordersLive) out += ORDERS
    for (f in FILL) { if (out.size >= 7) break; out += f }
    return out.take(7) + MORE
  }
}

/**
 *  «هشدارها و پیام‌ها» — کارت‌های بزرگِ چرخانِ r7، همه از دادهٔ واقعی.
 *
 *  ترتیب = فوریت: سفارشِ آنلاینِ تازه، اشتراکِ تمام‌شده/رو به پایان، خطای
 *  همگام‌سازی، کالای تمام‌شده/کم، قرضِ عقب‌افتاده، انقضا، صفِ همگام‌سازی.
 *  هیچ‌چیز ⇒ فهرستِ خالی (صفحه کارتِ آرامِ «همه چیز مرتب است» را نشان می‌دهد).
 */
object HomeAlerts {
  enum class Kind { ORDERS, SUBSCRIPTION, SYNC, STOCK, DEBTS, EXPIRY }

  data class Alert(
    val kind: Kind,
    val title: String,
    val body: String,
    val action: String,
    val route: String,
    /** فوری‌ها (سرخ‌ها) زمینهٔ تیره‌تر می‌گیرند */
    val urgent: Boolean = false,
  )

  /** همگام‌سازی از نگاهِ هشدار */
  enum class Sync { OK, QUEUED, OFFLINE, ERROR, LOCAL_ONLY }

  data class Input(
    val ordersLive: Boolean = false,
    val newOrders: Int = 0,
    val subscriptionLocked: Boolean = false,
    /** روزهای مانده وقتی در بازهٔ هشدار است، وگرنه `null` */
    val subscriptionDaysLeft: Int? = null,
    val sync: Sync = Sync.OK,
    val queued: Int = 0,
    val outOfStock: Int = 0,
    val lowStock: Int = 0,
    val overdueDebtors: Int = 0,
    val overdueTotal: String = "",
    val expiring: Int = 0,
  )

  fun build(i: Input): List<Alert> = buildList {
    if (i.ordersLive && i.newOrders > 0) add(
      Alert(Kind.ORDERS, "${i.newOrders} سفارشِ آنلاینِ تازه", "مشتری منتظرِ تأییدِ شماست", "دیدن", "orders", urgent = true),
    )
    if (i.subscriptionLocked) add(
      Alert(Kind.SUBSCRIPTION, "اشتراک تمام شده", "دفتر سرِ جایش است؛ فروشِ تازه با تمدید باز می‌شود", "تمدید", "vip", urgent = true),
    ) else i.subscriptionDaysLeft?.let { left -> add(
      Alert(Kind.SUBSCRIPTION, "$left روز تا پایانِ اشتراک", "پیش از قفل شدن تمدید کنید؛ هیچ داده‌ای پاک نمی‌شود", "تمدید", "vip", urgent = left <= 3),
    ) }
    when (i.sync) {
      Sync.ERROR -> add(Alert(Kind.SYNC, "همگام‌سازی خطا دارد", "کارهای این دستگاه نگه داشته شده‌اند", "بررسی", "devices", urgent = true))
      Sync.OFFLINE -> if (i.queued > 0) add(
        Alert(Kind.SYNC, "${i.queued} کار در انتظارِ اینترنت", "با وصل شدن خودکار فرستاده می‌شوند", "بررسی", "devices"),
      )
      else -> Unit
    }
    if (i.outOfStock > 0 || i.lowStock > 0) add(
      Alert(
        Kind.STOCK,
        if (i.outOfStock > 0) "${i.outOfStock} کالا تمام شده" else "${i.lowStock} کالا رو به پایان",
        if (i.outOfStock > 0 && i.lowStock > 0) "و ${i.lowStock} کالای دیگر کم مانده" else "پیش از خالی شدنِ قفسه سفارش دهید",
        "دیدن", "products", urgent = i.outOfStock > 0,
      ),
    )
    if (i.overdueDebtors > 0) add(
      Alert(Kind.DEBTS, "${i.overdueDebtors} قرضِ عقب‌افتاده", "بیش از یک ماه بی‌حرکت · ${i.overdueTotal}", "پیگیری", "debtors"),
    )
    if (i.expiring > 0) add(
      Alert(Kind.EXPIRY, "${i.expiring} محموله نزدیکِ انقضا", "زودتر بفروشید یا جدا کنید", "دیدن", "warehouse"),
    )
    if (i.sync == Sync.QUEUED && i.queued > 0) add(
      Alert(Kind.SYNC, "${i.queued} کار در صفِ همگام‌سازی", "در حالِ فرستادن به سرور", "بررسی", "devices"),
    )
  }
}

/**
 *  بازه‌های نمودارِ «آمار» (r8): هفته = ۷ روزِ تا امروز، ماه = ۶ ماهِ خورشیدیِ
 *  تا ماهِ جاری، سال = ۵ سالِ خورشیدی. هر بازه `from..to` به همان تاریخِ
 *  میلادیِ فایل است تا موتورِ گزارش (`ReportEngine`) همان را بشمارد.
 */
object StatsBuckets {
  enum class Period(val label: String) { WEEK("هفته"), MONTH("ماه"), YEAR("سال") }

  data class Bucket(val label: String, val from: String, val to: String)

  /** ISO ۱ دوشنبه … ۷ یکشنبه (`CivilDays.isoWeekday`) */
  private val WEEKDAYS = listOf("دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه", "شنبه", "یکشنبه")

  fun of(period: Period, today: String, monthNames: List<String>): List<Bucket> = when (period) {
    Period.WEEK -> (6 downTo 0).mapNotNull { back ->
      val day = CivilDays.addDays(today, -back) ?: return@mapNotNull null
      val wd = CivilDays.isoWeekday(day) ?: 1
      Bucket(WEEKDAYS[wd - 1], day, day)
    }
    Period.MONTH -> {
      val j = SolarHijri.toSolar(today)
      if (j == null) emptyList() else (5 downTo 0).mapNotNull { back ->
        var y = j.year
        var m = j.month - back
        while (m < 1) { m += 12; y -= 1 }
        val from = SolarHijri.fromSolar(y, m, 1) ?: return@mapNotNull null
        val last = SolarHijri.fromSolar(y, m, SolarHijri.monthLength(y, m)) ?: return@mapNotNull null
        Bucket(monthNames.getOrNull(m - 1) ?: m.toString(), from, minOf(last, today))
      }
    }
    Period.YEAR -> {
      val j = SolarHijri.toSolar(today)
      if (j == null) emptyList() else (4 downTo 0).mapNotNull { back ->
        val y = j.year - back
        val from = SolarHijri.fromSolar(y, 1, 1) ?: return@mapNotNull null
        val last = SolarHijri.fromSolar(y, 12, SolarHijri.monthLength(y, 12)) ?: return@mapNotNull null
        Bucket(y.toString(), from, minOf(last, today))
      }
    }
  }

  /**
   *  پلهٔ گردِ محورِ عمودی: بیشینه به ۱، ۲ یا ۵ ضربدرِ توانِ ده گرد می‌شود و
   *  چهار پله می‌خورد (مثلِ ۰، ۱۰k، ۲۰k، ۳۰k، ۴۰k در r8).
   */
  fun axisMax(max: Double): Double {
    if (max <= 0.0 || max.isNaN()) return 4.0
    val raw = max / 4.0
    var p = 1.0
    while (p * 10 <= raw) p *= 10
    val step = listOf(1.0, 2.0, 2.5, 5.0, 10.0).first { it * p >= raw } * p
    return step * 4
  }

  /** برچسبِ کوتاهِ محور: ۹۵۰ · ۱۲K · ۳.۵M · ۱.۲B */
  fun compact(v: Double): String {
    val a = kotlin.math.abs(v)
    fun f(x: Double, s: String): String {
      val r = Math.round(x * 10) / 10.0
      return (if (r == Math.floor(r)) r.toLong().toString() else r.toString()) + s
    }
    return when {
      a >= 1e9 -> f(v / 1e9, "B")
      a >= 1e6 -> f(v / 1e6, "M")
      a >= 1e3 -> f(v / 1e3, "K")
      else -> Math.round(v).toString()
    }
  }
}
