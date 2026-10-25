package ir.vil3ntec.tohid.ui.screens

import ir.vil3ntec.tohid.core.calendar.CivilDays
import ir.vil3ntec.tohid.core.calendar.SolarHijri
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrHome
import ir.vil3ntec.tohid.core.sync.SyncBadge
import ir.vil3ntec.tohid.ui.v3.account.AccountPaths
import ir.vil3ntec.tohid.ui.v3.analytics.AnalyticsPaths
import ir.vil3ntec.tohid.ui.v3.money.MoneyPaths
import ir.vil3ntec.tohid.ui.v3.online.OnlinePaths
import ir.vil3ntec.tohid.ui.v3.products.ProductsPaths
import ir.vil3ntec.tohid.ui.v3.sales.SalesPaths
import ir.vil3ntec.tohid.ui.v3.shell.ShellTargets
import ir.vil3ntec.tohid.ui.v3.stock.StockPaths

/*
 *  خانهٔ گوشی، نسخهٔ ۳ (HOME-V2: خانهٔ ساده، کادرِ پنهانِ کششی، r8 آمار) — منطقِ خالص.
 *
 *  هیچ‌کدام Compose یا اندروید نمی‌خواهد تا با آزمونِ سادهٔ JVM سنجیده شود:
 *  ماشینِ حالتِ کادرِ کششی، کاشی‌های «بخش‌ها»، هشدارها و بازه‌های نمودار.
 */

/**
 *  کادرِ پنهانِ «صندوق / گاوصندوق» (HOME-V2 §۱) — فقط با کشیدنِ **به‌زورِ** صفحه به پایین
 *  **از بالای صفحه**.
 *
 *  • لمس که شروع شد، `touchDown(atTop)` می‌گوید صفحه همان لحظه بالا بود یا نه؛
 *    کشیدنی که از وسطِ صفحه شروع شده (اسکرولِ معمولی) هرگز کادر را نمی‌آورد.
 *  • کششِ لاستیکی (`stretch`): هر پیکسلِ انگشت کمتر از پیکسلِ پیش کادر را پایین می‌آورد
 *    (مقاومت با فاصله بیشتر می‌شود) و کشِ دیدنی هرگز از `Config.limit` نمی‌گذرد.
 *  • گذر از `threshold` ⇒ `latched` (صدازننده یک لرزشِ کوچک می‌دهد) و رها کردن کادر را باز
 *    نگه می‌دارد؛ برگشتِ انگشت به زیرِ نیمهٔ آستانه قفل را برمی‌دارد.
 *  • رها کردنِ بی‌قفل ⇒ با فنر پس می‌رود، مگر پرتابِ تند پس از کششِ کوتاه.
 *  • باز: کشیدن رو به بالا، اسکرول یا دکمهٔ برگشت ⇒ بسته (`dismiss`).
 */
object PullCard {
  /** `finger` پیکسلِ خامِ انگشت از آغازِ کشش؛ `armed` یعنی لمس از بالای صفحه شروع شد */
  data class State(
    val shown: Boolean = false,
    val finger: Float = 0f,
    val dragging: Boolean = false,
    val armed: Boolean = false,
    val latched: Boolean = false,
  )

  /**
   *  @param open بلندیِ کادرِ باز (پیکسل)
   *  @param threshold کششِ **دیدنی** که قفل می‌کند
   *  @param flingMin کمترین کششِ دیدنی برای «کشیدنِ تند»
   *  @param flingVelocity سرعتِ رو به پایین (px/s) که کششِ کوتاه را بس می‌کند
   */
  data class Config(val open: Float, val threshold: Float, val flingMin: Float, val flingVelocity: Float) {
    /** سقفِ کشِ دیدنی — کمی بیش از کادرِ باز، تا لاستیک حس شود */
    val limit: Float get() = open * OVERSHOOT
  }

  /** شیبِ آغازِ لاستیک (همان ۰٫۵۵ ِ لبهٔ iOS): پیکسلِ اولِ انگشت ۰٫۵۵ پیکسل کادر */
  const val RUBBER = 0.55f

  /** کشِ دیدنی حداکثر ۱۲٪ بیش از کادرِ باز */
  const val OVERSHOOT = 1.12f

  /** پیکسلِ دیدنی برای `finger` پیکسل کشش: `L·(1 − 1/(f·k/L + 1))` — یکنوا، شیب رو به کاهش، سقفِ L */
  fun stretch(finger: Float, c: Config): Float {
    val l = c.limit
    if (finger <= 0f || l <= 0f) return 0f
    return l * (1f - 1f / (finger * RUBBER / l + 1f))
  }

  /** وارونهٔ `stretch`: چند پیکسل انگشت تا کادر `visible` پیکسل دیده شود */
  fun fingerFor(visible: Float, c: Config): Float {
    val l = c.limit
    if (visible <= 0f || l <= 0f) return 0f
    val v = visible.coerceAtMost(l * 0.999f)
    return l / RUBBER * (v / (l - v))
  }

  private fun latch(was: Boolean, visible: Float, c: Config): Boolean =
    if (was) visible >= c.threshold * 0.5f else visible >= c.threshold

  fun touchDown(s: State, atTop: Boolean): State =
    if (s.shown) s else s.copy(armed = atTop, finger = 0f, dragging = false, latched = false)

  /** انگشت `dy` پیکسل پایین رفت و اسکرول جایی برای خوردنش نداشت */
  fun pullDown(s: State, dy: Float, c: Config): State {
    if (s.shown || !s.armed || dy <= 0f) return s
    val f = s.finger + dy
    return s.copy(finger = f, dragging = true, latched = latch(s.latched, stretch(f, c), c))
  }

  /**
   *  انگشت `dy` پیکسل بالا رفت. تا کادرِ نیمه‌کشیده هست، اول همان جمع می‌شود
   *  (و صفحه اسکرول نمی‌شود). دومی = چند پیکسل از حرکت خورده شد.
   */
  fun pullUp(s: State, dy: Float, c: Config): Pair<State, Float> {
    if (s.shown || s.finger <= 0f || dy <= 0f) return s to 0f
    val take = minOf(s.finger, dy)
    val f = s.finger - take
    return s.copy(finger = f, latched = latch(s.latched, stretch(f, c), c)) to take
  }

  /** انگشت رها شد؛ `velocityY` مثبت یعنی رو به پایین */
  fun release(s: State, velocityY: Float, c: Config): State {
    if (s.shown) return s.copy(dragging = false)
    val open = s.latched || (stretch(s.finger, c) >= c.flingMin && velocityY >= c.flingVelocity)
    return if (open) State(shown = true) else State()
  }

  /** کشیدن رو به بالا، اسکرول یا دکمهٔ برگشت */
  fun dismiss(@Suppress("UNUSED_PARAMETER") s: State): State = State()

  /** فقط برای پیش‌نمایش و عکس: باز، یا نیمه‌کشیده (`fraction` از بلندیِ باز) */
  fun forceShown(): State = State(shown = true)
  fun forcePulled(fraction: Float, c: Config): State =
    State(armed = true, dragging = true, finger = fingerFor(c.open * fraction, c)).let { it.copy(latched = latch(false, stretch(it.finger, c), c)) }

  /** پیکسلِ دیدنیِ کادر همین حالا */
  fun visible(s: State, c: Config): Float = if (s.shown) c.open else stretch(s.finger, c)

  /** ۰ = پنهان، ۱ = کامل پایین آمده (کشِ بیش از باز هم ۱) */
  fun progress(s: State, c: Config): Float =
    if (c.open <= 0f) 0f else (visible(s, c) / c.open).coerceIn(0f, 1f)
}

/**
 *  «بخش‌ها» — شبکهٔ ۴×۲ِ r7؛ دسترسیِ سریعِ صاحب دکان به هر بخش.
 *
 *  هشت خانه همیشه پر است و هشتمی «بیشتر». کارمندان فقط برای مدیر (همان سدِ
 *  «بیشتر»)، سفارش‌ها فقط وقتی سفارشِ آنلاین برای این دکان روشن است؛ جای
 *  خالی با خرید و بعد تاریخچه پر می‌شود. `id` رنگ و نشانهٔ کاشی را می‌گوید و
 *  `route` مسیرِ نوع‌دارِ همان بخش است؛ نام هر بار از جدولِ نوشته خوانده می‌شود.
 */
object HomeSections {
  data class Tile(val id: String, val route: String) {
    val label: String get() = labelOf(id)
  }

  private val CORE = listOf(
    Tile("products", ProductsPaths.LIST),
    Tile("warehouse", StockPaths.LIST),
    Tile("debtors", MoneyPaths.DEBTORS),
    Tile("expenses", MoneyPaths.EXPENSES),
    Tile("reports", AnalyticsPaths.REPORTS),
  )
  private val TEAM = Tile("team", AccountPaths.TEAM)
  private val ORDERS = Tile("orders", OnlinePaths.ORDERS)
  private val FILL = listOf(
    Tile("purchasing", MoneyPaths.PURCHASING),
    Tile("sales", SalesPaths.HISTORY),
    Tile("receipts", MoneyPaths.RECEIPTS),
  )
  val MORE = Tile("more", "more")

  fun tiles(canManage: Boolean, ordersLive: Boolean): List<Tile> {
    val out = CORE.toMutableList()
    if (canManage) out += TEAM
    if (ordersLive) out += ORDERS
    for (f in FILL) { if (out.size >= 7) break; out += f }
    return out.take(7) + MORE
  }

  fun labelOf(id: String): String = when (id) {
    "products" -> StrHome.tileProducts
    "warehouse" -> Str.warehouse
    "debtors" -> Str.debtors
    "expenses" -> Str.expenses
    "reports" -> Str.reports
    "team" -> StrHome.tileTeam
    "orders" -> Str.orders
    "purchasing" -> Str.purchases
    "sales" -> StrHome.history
    "receipts" -> Str.receipts
    else -> Str.more
  }
}

/**
 *  «هشدارها و پیام‌ها» — کارت‌های بزرگِ چرخانِ r7، همه از دادهٔ واقعی.
 *
 *  ترتیب = فوریت: سفارشِ آنلاینِ تازه، اشتراکِ تمام‌شده/رو به پایان، خطای
 *  همگام‌سازی، کالای تمام‌شده/کم، قرضِ عقب‌افتاده، انقضا، صفِ همگام‌سازی.
 *  هیچ‌چیز ⇒ فهرستِ خالی (صفحه کارتِ آرامِ «همه چیز مرتب است» را نشان می‌دهد).
 *
 *  هر هشدار مستقیم سرِ کادرِ خودش می‌رود (`ShellTargets`): همگام‌سازی ⇐ صفحهٔ
 *  همگام‌سازی (نه دستگاه‌های جانبی)، کالا ⇐ فهرستِ همان فیلتر، اشتراک ⇐ اشتراک و
 *  دکمهٔ «تمدید» ⇐ پلن‌ها (`actionRoute`). خطای همگام‌سازی دلیلِ واقعیِ موتور را
 *  (`syncError`) همان‌جا می‌گوید.
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
    /** دکمهٔ کارِ کارت (مثلاً «تمدید» ⇐ پلن‌ها)؛ پیش‌فرض همان مقصدِ کارت */
    val actionRoute: String = route,
    /** خانه فقط همین‌ها را نشان می‌دهد (HOME-V2 §۲٫۵): فوری‌ها و صفِ ماندهٔ بی‌اینترنت */
    val pressing: Boolean = urgent,
  )

  /** هشدارهای کارتِ یک‌خطیِ خانه — «وقتی چیزی نیست، هیچ» */
  fun pressing(all: List<Alert>): List<Alert> = all.filter { it.pressing }

  /** همگام‌سازی از نگاهِ هشدار */
  enum class Sync { OK, QUEUED, OFFLINE, ERROR, LOCAL_ONLY }

  /** خطای بلندِ موتور در یک کارت جا نمی‌شود (همان سقفِ نوارِ وضعیت) */
  const val MAX_ERROR = SyncBadge.MAX_ERROR

  data class Input(
    val ordersLive: Boolean = false,
    val newOrders: Int = 0,
    val subscriptionLocked: Boolean = false,
    /** روزهای مانده وقتی در بازهٔ هشدار است، وگرنه `null` */
    val subscriptionDaysLeft: Int? = null,
    val sync: Sync = Sync.OK,
    val queued: Int = 0,
    /** آخرین خطای موتورِ همگام‌سازی (`SyncStatus.lastError`) — دلیلِ واقعی */
    val syncError: String = "",
    val outOfStock: Int = 0,
    val lowStock: Int = 0,
    val overdueDebtors: Int = 0,
    val overdueTotal: String = "",
    val expiring: Int = 0,
  )

  /** دلیلِ واقعیِ خطا در یک خط، یا متنِ همیشگی وقتی موتور چیزی نگفته */
  fun syncReason(error: String): String {
    val e = error.trim().replace('\n', ' ')
    return when {
      e.isEmpty() -> StrHome.alertSyncErrorBody
      e.length <= MAX_ERROR -> e
      else -> e.take(MAX_ERROR - 1) + "…"
    }
  }

  fun build(i: Input): List<Alert> = buildList {
    if (i.ordersLive && i.newOrders > 0) add(
      Alert(Kind.ORDERS, StrHome.alertNewOrders(i.newOrders), StrHome.alertNewOrdersBody, StrHome.actionSee, ShellTargets.NEW_ORDERS, urgent = true),
    )
    if (i.subscriptionLocked) add(
      Alert(Kind.SUBSCRIPTION, StrHome.alertSubEnded, StrHome.alertSubEndedBody, Str.renew, ShellTargets.SUBSCRIPTION, urgent = true, actionRoute = ShellTargets.PLANS),
    ) else i.subscriptionDaysLeft?.let { left -> add(
      Alert(Kind.SUBSCRIPTION, StrHome.alertSubDays(left), StrHome.alertSubDaysBody, Str.renew, ShellTargets.SUBSCRIPTION, urgent = left <= 3, actionRoute = ShellTargets.PLANS),
    ) }
    when (i.sync) {
      Sync.ERROR -> add(Alert(Kind.SYNC, StrHome.alertSyncError, syncReason(i.syncError), StrHome.actionCheck, ShellTargets.SYNC, urgent = true))
      Sync.OFFLINE -> if (i.queued > 0) add(
        Alert(Kind.SYNC, StrHome.alertWaitingInternet(i.queued), StrHome.alertWaitingInternetBody, StrHome.actionCheck, ShellTargets.SYNC, pressing = true),
      )
      else -> Unit
    }
    if (i.outOfStock > 0 || i.lowStock > 0) add(
      Alert(
        Kind.STOCK,
        if (i.outOfStock > 0) StrHome.alertOut(i.outOfStock) else StrHome.alertLow(i.lowStock),
        if (i.outOfStock > 0 && i.lowStock > 0) StrHome.alertAlsoLow(i.lowStock) else StrHome.alertReorderBody,
        StrHome.actionSee, ShellTargets.stock(anyOut = i.outOfStock > 0), urgent = i.outOfStock > 0,
      ),
    )
    if (i.overdueDebtors > 0) add(
      Alert(Kind.DEBTS, StrHome.alertOverdue(i.overdueDebtors), StrHome.alertOverdueBody(i.overdueTotal), StrHome.actionFollowUp, ShellTargets.OVERDUE_DEBTS),
    )
    if (i.expiring > 0) add(
      Alert(Kind.EXPIRY, StrHome.alertExpiring(i.expiring), StrHome.alertExpiringBody, StrHome.actionSee, ShellTargets.EXPIRING),
    )
    if (i.sync == Sync.QUEUED && i.queued > 0) add(
      Alert(Kind.SYNC, StrHome.alertQueued(i.queued), StrHome.alertQueuedBody, StrHome.actionCheck, ShellTargets.SYNC),
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
