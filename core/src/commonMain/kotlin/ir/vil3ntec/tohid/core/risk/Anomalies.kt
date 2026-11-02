package ir.vil3ntec.tohid.core.risk

import ir.vil3ntec.tohid.core.orders.OrderText
import ir.vil3ntec.tohid.core.text.LocaleFormat

/**
 *  AI-08 — تشخیصِ ناهنجاری (قاعده‌ای و آماری) برای صندوق و موجودی (§۶.۲۲). خالص، روی دفترِ همین دستگاه، بی سرویسِ
 *  بیرونی و بی شبکه؛ فقط وقتی تنظیمِ خاموشِ دستگاه روشن است صدا زده می‌شود.
 *
 *  ── قاعده‌ها ────────────────────────────────────────────────────────
 *   • لغو، تخفیف و کشو بی فروشِ هر صندوقدار: همان هشدارهای RSK-10 (`CashierAlerts`، ستون‌های `FraudRow` ِ هستهٔ گزارش)؛
 *     دوباره شمرده نمی‌شوند، فقط یافته می‌شوند. نسبت دست‌کم دو برابرِ حد ⇒ «بالا».
 *   • اختلافِ صندوق: |شمرده − مورد انتظار| > [Policy.shiftToleranceMinor] (برابرِ حد نه)؛ بیش از سه برابرِ حد ⇒ «بالا».
 *   • کمِ موجودی با «اصلاح» (مقدارِ منفی): ارزش (مقدار × بها) > [Policy.stockLossMinor]؛ کالای بی بها با مقدار >
 *     [Policy.stockLossMilli]. بیش از پنج برابر ⇒ «بالا».
 *   • فروش زیرِ بها: جمعِ قلم‌های هر فاکتور که فروشش از بهایش کمتر است، زیان > [Policy.belowCostMinor]؛ فروش ≤ نیمِ
 *     بها ⇒ «بالا». قلمِ بی بها و قلمِ تخفیفِ خودکار (`promo`) را لایهٔ داده نمی‌فرستد.
 *   • ساعتِ غیرعادی: فاکتور در [[Policy.quietFrom]، [Policy.quietTo]) دقیقهٔ روز (از نیمه‌شب گذشتن مجاز)، هر کاربر و
 *     روز یک یافته؛ سه فاکتور یا بیشتر ⇒ «متوسط»، کمتر «کم».
 *
 *  ── آمار ────────────────────────────────────────────────────────────
 *  جمعِ روزانهٔ برگشتی، تخفیف و کمِ موجودی (کلِ دکان یا یک صندوقدار): حصارِ Tukey روی چارک‌ها (درون‌یابیِ خطی، همان
 *  نوعِ ۷ ِ R)، با کمینهٔ پهنا [Policy.minSpreadMinor] تا سریِ تقریباً ثابت از یک ریال نلرزد:
 *  بالاتر از Q3 + max(1.5×IQR، پهنا) ⇒ «متوسط»؛ بالاتر از Q3 + max(3×IQR، ۲×پهنا) ⇒ «بالا». کمتر از [Policy.minDays]
 *  روز ⇒ بی آمار. فقط روزهای از [Input.fromJdn] به بعد یافته می‌شوند؛ روزهای پیش‌تر پایه‌اند.
 *
 *  ⛔ متنِ هر یافته «نیازمندِ بررسی» است، نه اتهام: فقط نشان می‌دهد، چیزی را نمی‌بندد و عوض نمی‌کند.
 *  فقط عددِ صحیح (واحدِ کوچکِ پول و هزارمِ مقدار)؛ هر عدد به ±[MAX_AMOUNT] بریده می‌شود تا ضربِ میانی سرریز نکند.
 */
object Anomalies {
  const val VERSION = 1
  const val MAX_AMOUNT: Long = 1_000_000_000_000_000
  /** سقفِ مقدار (هزارم) و بها (واحدِ کوچک) در ضربِ کمِ موجودی و بهای قلم: حاصل زیرِ ۲^۶۳ */
  const val MAX_FACTOR: Long = 1_000_000_000
  const val MINUTES_PER_DAY = 1440
  const val REVIEW = "نیازمندِ بررسی"

  enum class Severity { LOW, MEDIUM, HIGH }

  enum class Kind(val id: String) {
    CASHIER_VOID("cashier_void"),
    CASHIER_DISCOUNT("cashier_discount"),
    CASHIER_NO_SALE("cashier_no_sale"),
    SHIFT_DIFF("shift_diff"),
    STOCK_LOSS("stock_loss"),
    BELOW_COST("below_cost"),
    ODD_HOUR("odd_hour"),
    DAY_RETURNS("day_returns"),
    DAY_DISCOUNTS("day_discounts"),
    DAY_ADJUST("day_adjust"),
  }

  data class Policy(
    /** ۱۰۰ ؋ */
    val shiftToleranceMinor: Long = 10_000,
    /** ۱٬۰۰۰ ؋ */
    val stockLossMinor: Long = 100_000,
    /** ۱۰ واحد، برای کالای بی بها */
    val stockLossMilli: Long = 10_000,
    /** ۱ ؋ — زیر این، گردکردن است نه زیان */
    val belowCostMinor: Long = 100,
    val quietFrom: Int = 0,
    val quietTo: Int = 360,
    val minDays: Int = 7,
    /** ۱ ؋ */
    val minSpreadMinor: Long = 100,
  )

  data class Shift(val id: String, val by: String, val dayJdn: Int, val expectedMinor: Long, val countedMinor: Long)

  /** یک «اصلاحِ» موجودی؛ [qtyMilli] منفی = کم شد */
  data class Adjust(
    val id: String, val productId: String, val name: String, val by: String, val dayJdn: Int,
    val qtyMilli: Long, val unitCostMinor: Long,
  )

  /** قلمِ فروش: [netMinor] جمعِ پس از تخفیفِ ردیف، [unitCostMinor] بهای هر واحد هنگامِ فروش */
  data class Line(
    val saleId: String, val label: String, val by: String, val dayJdn: Int,
    val qtyMilli: Long, val netMinor: Long, val unitCostMinor: Long,
  )

  /** فاکتور با دقیقهٔ روزِ محلی (۰ تا ۱۴۳۹) */
  data class SaleAt(val saleId: String, val by: String, val dayJdn: Int, val minute: Int, val totalMinor: Long)

  /** جمعِ روزانه از [startJdn]، روزِ بی رویداد صفر؛ [subject] خالی = کلِ دکان */
  data class Series(val kind: Kind, val subject: String, val startJdn: Int, val values: List<Long>)

  data class Input(
    /** همان هشدارهای RSK-10 (`ReportBoard.alertsOf` / `CashierAlerts.alerts`) */
    val cashierAlerts: List<CashierAlerts.Alert> = emptyList(),
    val cashierPolicy: CashierAlerts.Policy = CashierAlerts.Policy(),
    val shifts: List<Shift> = emptyList(),
    val adjusts: List<Adjust> = emptyList(),
    val lines: List<Line> = emptyList(),
    val sales: List<SaleAt> = emptyList(),
    val series: List<Series> = emptyList(),
    /** آمار فقط برای روزهای از این روز به بعد (بازهٔ گزارش) */
    val fromJdn: Int = Int.MIN_VALUE,
  )

  /** [evidence] عددهای پشتِ یافته (کلیدِ انگلیسی، به ترتیب)؛ [dayJdn] `null` = کلِ بازه */
  data class Finding(
    val kind: Kind,
    val severity: Severity,
    val subject: String,
    val dayJdn: Int?,
    val ref: String?,
    val evidence: Map<String, Long>,
    val reason: String,
  )

  private fun clamp(x: Long) = x.coerceIn(-MAX_AMOUNT, MAX_AMOUNT)
  private fun who(s: String) = s.trim().ifEmpty { "کاربرِ نامعلوم" }

  /** همهٔ یافته‌ها: شدیدتر بالا، سپس روزِ تازه‌تر، سپس نوع و موضوع */
  fun detect(i: Input, p: Policy = Policy(), digits: String = LocaleFormat.DIGITS_FA): List<Finding> {
    val t = Text(digits)
    val out = ArrayList<Finding>()
    out += cashier(i.cashierAlerts, i.cashierPolicy, t)
    out += shifts(i.shifts, p, t)
    out += stock(i.adjusts, p, t)
    out += belowCost(i.lines, p, t)
    out += oddHours(i.sales, p, t)
    for (s in i.series) out += outliers(s, i.fromJdn, p, t)
    return out.sortedWith(
      compareBy<Finding>({ -it.severity.ordinal }, { -(it.dayJdn ?: Int.MIN_VALUE).toLong() }, { it.kind.ordinal }, { it.subject }, { it.ref ?: "" }),
    )
  }

  /* ── قاعده‌ها ── */

  fun cashier(alerts: List<CashierAlerts.Alert>, policy: CashierAlerts.Policy = CashierAlerts.Policy(), t: Text = Text()): List<Finding> {
    val factor = policy.factorPct.coerceIn(100L, 1_000L)
    return alerts.map { a ->
      val kind = when (a.kind) {
        CashierAlerts.Kind.VOID -> Kind.CASHIER_VOID
        CashierAlerts.Kind.DISCOUNT -> Kind.CASHIER_DISCOUNT
        CashierAlerts.Kind.NO_SALE -> Kind.CASHIER_NO_SALE
      }
      val what = when (a.kind) {
        CashierAlerts.Kind.VOID -> "لغوِ فاکتور"
        CashierAlerts.Kind.DISCOUNT -> "تخفیف"
        CashierAlerts.Kind.NO_SALE -> "کشو بی فروش"
      }
      Finding(
        kind, if (a.ratioPct >= 2 * factor) Severity.HIGH else Severity.MEDIUM, a.key, null, null,
        linkedMapOf("count" to a.count, "invoices" to a.invoices, "peerCount" to a.peerCount, "peerInvoices" to a.peerInvoices, "ratioPct" to a.ratioPct),
        "${a.key}: ${t.n(a.count)} $what در ${t.n(a.invoices)} فاکتور، ${t.n(a.ratioPct)}٪ نرخِ بقیه " +
          "(${t.n(a.peerCount)} در ${t.n(a.peerInvoices)}) — $REVIEW",
      )
    }
  }

  fun shifts(rows: List<Shift>, p: Policy = Policy(), t: Text = Text()): List<Finding> {
    val tol = p.shiftToleranceMinor.coerceIn(0L, MAX_AMOUNT)
    return rows.mapNotNull { s ->
      val expected = clamp(s.expectedMinor)
      val counted = clamp(s.countedMinor)
      val diff = counted - expected
      val abs = if (diff < 0) -diff else diff
      if (abs <= tol) return@mapNotNull null
      val word = if (diff < 0) "کسری" else "اضافه"
      Finding(
        Kind.SHIFT_DIFF, if (abs > 3 * tol) Severity.HIGH else Severity.MEDIUM, s.by, s.dayJdn, s.id,
        linkedMapOf("expected" to expected, "counted" to counted, "diff" to diff, "tolerance" to tol),
        "صندوقِ ${who(s.by)}: $word ${t.money(abs)} (مورد انتظار ${t.money(expected)}، شمرده ${t.money(counted)})، " +
          "بیش از حدِ ${t.money(tol)} — $REVIEW",
      )
    }
  }

  fun stock(rows: List<Adjust>, p: Policy = Policy(), t: Text = Text()): List<Finding> = rows.mapNotNull { a ->
    if (a.qtyMilli >= 0) return@mapNotNull null
    val qty = (-a.qtyMilli).coerceAtMost(MAX_FACTOR)
    val cost = a.unitCostMinor.coerceIn(0L, MAX_FACTOR)
    val value = (qty * cost + 500) / 1000
    val (x, limit) = if (cost > 0) value to p.stockLossMinor.coerceIn(0L, MAX_AMOUNT) else qty to p.stockLossMilli.coerceIn(0L, MAX_AMOUNT)
    if (x <= limit) return@mapNotNull null
    Finding(
      Kind.STOCK_LOSS, if (x > 5 * limit) Severity.HIGH else Severity.MEDIUM, a.productId, a.dayJdn, a.id,
      linkedMapOf("qtyMilli" to -qty, "unitCost" to cost, "value" to value, "limit" to limit),
      "کمِ موجودیِ ${a.name.ifBlank { a.productId }} با «اصلاح»${if (a.by.isBlank()) "" else " (${a.by.trim()})"}: ${t.qty(qty)} واحد" +
        (if (cost > 0) "، حدودِ ${t.money(value)}" else "") + "، بیش از حدِ " +
        (if (cost > 0) t.money(limit) else "${t.qty(limit)} واحد") + " — $REVIEW",
    )
  }

  fun belowCost(rows: List<Line>, p: Policy = Policy(), t: Text = Text()): List<Finding> {
    class Acc(var label: String, var by: String, var day: Int, var net: Long = 0, var cost: Long = 0, var lines: Long = 0)
    val bySale = LinkedHashMap<String, Acc>()
    for (l in rows) {
      if (l.qtyMilli <= 0 || l.unitCostMinor <= 0) continue
      val cost = (l.qtyMilli.coerceAtMost(MAX_FACTOR) * l.unitCostMinor.coerceAtMost(MAX_FACTOR) + 500) / 1000
      val net = clamp(l.netMinor)
      if (net >= cost) continue
      val a = bySale.getOrPut(l.saleId) { Acc(l.label, l.by, l.dayJdn) }
      a.net = clamp(a.net + net)
      a.cost = clamp(a.cost + cost)
      a.lines++
    }
    val min = p.belowCostMinor.coerceIn(0L, MAX_AMOUNT)
    return bySale.mapNotNull { (id, a) ->
      val loss = a.cost - a.net
      if (loss <= min) return@mapNotNull null
      Finding(
        Kind.BELOW_COST, if (a.net * 2 <= a.cost) Severity.HIGH else Severity.MEDIUM, a.by, a.day, id,
        linkedMapOf("lines" to a.lines, "net" to a.net, "cost" to a.cost, "loss" to loss),
        "فاکتورِ ${a.label.ifBlank { id }} (${who(a.by)}): ${t.n(a.lines)} قلم زیرِ بها، فروش ${t.money(a.net)} " +
          "در برابرِ بهای ${t.money(a.cost)} (کمتر ${t.money(loss)}) — $REVIEW",
      )
    }
  }

  /** دقیقه در پنجرهٔ آرام؟ from == to ⇒ خاموش؛ from > to ⇒ از نیمه‌شب می‌گذرد */
  fun quiet(minute: Int, from: Int, to: Int): Boolean = when {
    from == to -> false
    from < to -> minute in from until to
    else -> minute >= from || minute < to
  }

  fun oddHours(rows: List<SaleAt>, p: Policy = Policy(), t: Text = Text()): List<Finding> {
    val from = p.quietFrom.mod(MINUTES_PER_DAY)
    val to = p.quietTo.mod(MINUTES_PER_DAY)
    class Acc(var count: Long = 0, var total: Long = 0, var first: Int = Int.MAX_VALUE, var ref: String = "")
    val groups = LinkedHashMap<Pair<String, Int>, Acc>()
    for (s in rows) {
      if (s.minute !in 0 until MINUTES_PER_DAY || !quiet(s.minute, from, to)) continue
      val a = groups.getOrPut(s.by.trim() to s.dayJdn) { Acc() }
      a.count++
      a.total = clamp(a.total + clamp(s.totalMinor))
      //  «نخستین» از آغازِ پنجرهٔ آرام، نه از نیمه‌شب
      val rank = (s.minute - from).mod(MINUTES_PER_DAY)
      if (a.first == Int.MAX_VALUE || rank < (a.first - from).mod(MINUTES_PER_DAY)) { a.first = s.minute; a.ref = s.saleId }
    }
    return groups.map { (k, a) ->
      Finding(
        Kind.ODD_HOUR, if (a.count >= 3) Severity.MEDIUM else Severity.LOW, k.first, k.second, a.ref,
        linkedMapOf("count" to a.count, "total" to a.total, "firstMinute" to a.first.toLong(), "quietFrom" to from.toLong(), "quietTo" to to.toLong()),
        "${who(k.first)}: ${t.n(a.count)} فاکتور میانِ ${t.clock(from)} و ${t.clock(to)} (نخستین ${t.clock(a.first)})، " +
          "جمعاً ${t.money(a.total)} — $REVIEW",
      )
    }
  }

  /* ── آمار ── */

  /** چارکِ p/4 ضربِ ۴ (درون‌یابیِ خطی روی فهرستِ مرتب)؛ q = ۱، ۲، ۳ */
  internal fun quartile4(sorted: List<Long>, q: Int): Long {
    val idx4 = q.toLong() * (sorted.size - 1)
    val lo = (idx4 / 4).toInt()
    val r = idx4 % 4
    return 4 * sorted[lo] + if (r == 0L) 0L else r * (sorted[lo + 1] - sorted[lo])
  }

  data class Fence(val median4: Long, val q1x4: Long, val q3x4: Long, val inner8: Long, val outer8: Long)

  fun fence(values: List<Long>, minSpread: Long): Fence {
    val v = values.map { it.coerceIn(0L, MAX_AMOUNT) }.sorted()
    val q1 = quartile4(v, 1)
    val q3 = quartile4(v, 3)
    val iqr = q3 - q1
    val spread = minSpread.coerceIn(0L, MAX_AMOUNT)
    return Fence(quartile4(v, 2), q1, q3, 2 * q3 + maxOf(3 * iqr, 8 * spread), 2 * q3 + maxOf(6 * iqr, 16 * spread))
  }

  fun outliers(s: Series, fromJdn: Int = Int.MIN_VALUE, p: Policy = Policy(), t: Text = Text()): List<Finding> {
    if (s.values.size < maxOf(2, p.minDays)) return emptyList()
    val f = fence(s.values, p.minSpreadMinor)
    val what = when (s.kind) {
      Kind.DAY_RETURNS -> "برگشتیِ این روز"
      Kind.DAY_DISCOUNTS -> "تخفیفِ این روز"
      Kind.DAY_ADJUST -> "کمِ موجودیِ «اصلاح»ِ این روز"
      else -> return emptyList()
    }
    val out = ArrayList<Finding>()
    for ((k, raw) in s.values.withIndex()) {
      val day = s.startJdn + k
      if (day < fromJdn) continue
      val x = raw.coerceIn(0L, MAX_AMOUNT)
      if (8 * x <= f.inner8) continue
      val median = (f.median4 + 2) / 4
      val usual = (f.inner8 + 4) / 8
      out += Finding(
        s.kind, if (8 * x > f.outer8) Severity.HIGH else Severity.MEDIUM, s.subject, day, null,
        linkedMapOf("value" to x, "median" to median, "q3" to (f.q3x4 + 2) / 4, "fence" to usual, "days" to s.values.size.toLong()),
        (if (s.subject.isBlank()) "" else "${s.subject}: ") + "$what ${t.money(x)}، بیرون از دامنهٔ معمولِ ${t.n(s.values.size.toLong())} روز " +
          "(میانه ${t.money(median)}، سقفِ معمول ${t.money(usual)}) — $REVIEW",
      )
    }
    return out
  }

  /* ── متن ── */

  class Text(private val digits: String = LocaleFormat.DIGITS_FA) {
    fun n(x: Long): String = LocaleFormat.displayDigits(x.toString(), digits)
    fun money(minor: Long): String = LocaleFormat.displayDigits(OrderText.money(minor), digits) + " ؋"
    /** هزارم ⇒ «۱۲»، «۱.۵» */
    fun qty(milli: Long): String {
      val whole = milli / 1000
      val frac = (milli % 1000).toString().padStart(3, '0').trimEnd('0')
      return LocaleFormat.displayDigits(whole.toString() + (if (frac.isEmpty()) "" else ".$frac"), digits)
    }
    fun clock(minute: Int): String =
      LocaleFormat.displayDigits((minute / 60).toString().padStart(2, '0') + ":" + (minute % 60).toString().padStart(2, '0'), digits)
  }

  fun severityText(s: Severity): String = when (s) {
    Severity.HIGH -> "اهمیتِ بالا"
    Severity.MEDIUM -> "اهمیتِ متوسط"
    Severity.LOW -> "اهمیتِ کم"
  }
}
