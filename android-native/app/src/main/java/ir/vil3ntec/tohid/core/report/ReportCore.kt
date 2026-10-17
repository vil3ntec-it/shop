package ir.vil3ntec.tohid.core.report

import kotlinx.serialization.Serializable
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 *  هستهٔ گزارش — RPT-01 (امروز)، RPT-02 (تفکیکِ فروش)، RPT-03 (سودِ ناخالص و
 *  حاشیه)، RPT-06 (سنِ بدهی و طلبِ تأمین‌کننده)، RPT-09 (ضدتقلب).
 *
 *  خالص: نه Android، نه دیسک، نه ساعتِ دستگاه. دوقلوی `license/report-core.js`؛
 *  هر دو آزمون‌های مرجعِ `core/test-vectors/report-v1.json` را عیناً می‌گذرانند.
 *  پول واحدِ کوچک، مقدار هزارم، درصد بیسیس‌پوینت (۱۰۰ = ۱٪؛ null یعنی مخرج ≤ ۰).
 *
 *  ⛔ هر حسابِ اعشاری این‌جا عمداً با همان ترتیبِ عملیاتِ JS نوشته شده (Double
 *  در هر دو IEEE-754 است)؛ «ساده‌تر» کردنش برابریِ دو هسته را می‌شکند.
 *
 *  هنوز به هیچ صفحه‌ای وصل نیست: موتورهای امروز (`ReportEngine`، `DashboardEngine`)
 *  دست نخورده‌اند. تفاوت‌ها در شرحِ PR و `ReportCoreVectorsTest` ثبت شده‌اند.
 */
object ReportCore {
  enum class Costing(val id: String) { LINE("line"), MAVG("mavg") }

  @Serializable
  data class Totals(
    val invoices: Int, val returnCount: Int, val gross: Long, val discount: Long, val sales: Long,
    val returns: Long, val netSales: Long, val cogs: Long, val grossProfit: Long, val marginBp: Long?,
    val avgTicket: Long,
    /** قلم‌های فروخته‌شدهٔ بازه که بهایشان معلوم نیست یا تخمینی است */
    val costEstimatedLines: Int,
    /** v3 ۱۵.۴ بند ۴: سود «تقریبی» است — بهای 'line' (کپیِ قیمتِ خرید) یا قلمِ بی‌بها */
    val profitApproximate: Boolean,
  )

  @Serializable
  data class Day(
    val date: String, val invoices: Int, val returnCount: Int, val gross: Long, val discount: Long, val sales: Long,
    val returns: Long, val netSales: Long, val cogs: Long, val grossProfit: Long, val marginBp: Long?,
    val avgTicket: Long,
    /** قلم‌های فروخته‌شدهٔ بازه که بهایشان معلوم نیست یا تخمینی است */
    val costEstimatedLines: Int,
    /** v3 ۱۵.۴ بند ۴: سود «تقریبی» است — بهای 'line' (کپیِ قیمتِ خرید) یا قلمِ بی‌بها */
    val profitApproximate: Boolean,
  )

  @Serializable
  data class Row(
    val key: String, val name: String, val invoices: Int, val qty: Long, val gross: Long, val discount: Long,
    val sales: Long, val returns: Long, val netSales: Long, val cogs: Long, val grossProfit: Long, val marginBp: Long?,
  )

  @Serializable
  data class SalesReport(
    val from: String, val to: String, val costing: String, val totals: Totals,
    val product: List<Row>, val category: List<Row>, val user: List<Row>,
    val hour: List<Row>, val payment: List<Row>, val channel: List<Row>,
  )

  @Serializable
  data class Delta(val diff: Long, val bp: Long?)

  @Serializable
  data class TodayReport(
    val date: String, val today: Day, val yesterday: Day, val lastWeek: Day,
    val vsYesterday: Map<String, Delta>, val vsLastWeek: Map<String, Delta>,
  )

  @Serializable
  data class AgeRow(
    val id: String, val name: String, val balance: Long, val open: Long, val credit: Long,
    val buckets: List<Long>, val oldestDays: Long?,
  )

  @Serializable
  data class AgeTotals(val balance: Long, val open: Long, val credit: Long, val buckets: List<Long>)

  @Serializable
  data class Ageing(val rows: List<AgeRow>, val totals: AgeTotals)

  @Serializable
  data class DebtReport(val asOf: String, val buckets: List<Long>, val customers: Ageing, val suppliers: Ageing)

  @Serializable
  data class FraudRow(
    val key: String, val invoices: Int, val gross: Long, val discountCount: Int, val discount: Long,
    val discountBp: Long?, val returnCount: Int, val returns: Long, val voidCount: Int, val voids: Long,
    val lineDeleteCount: Int, val lineDeletes: Long, val noSaleCount: Int,
    /** رویدادهای این کاربر که تأییدکننده داشتند (v3 S08) */
    val overrideCount: Int,
    /** تأییدهایی که این کاربر داد */
    val approvalCount: Int,
  )

  @Serializable
  data class FraudReport(val from: String, val to: String, val rows: List<FraudRow>, val totals: FraudRow)

  class Costs(val mode: Costing, val line: Map<String, Long>, val ret: Map<String, Long>, val est: Set<String>)

  /* ---------------- عدد ---------------- */

  private fun rnd(x: Double): Long = Math.round(x)

  /** a/b گرد به نزدیک‌ترین، نیمه رو به +∞ (b > 0) */
  fun divRound(a: Long, b: Long): Long = Math.floorDiv(2 * a + b, 2 * b)

  fun bp(num: Long, den: Long): Long? = if (den > 0) divRound(num * 10000, den) else null

  /* ---------------- تاریخِ هر رویداد ---------------- */

  private fun posDate(r: ReportLedger.Dated, clock: ReportClock?) =
    if (clock != null && r.createdAt > 0) clock.date(r.createdAt) else r.date

  private fun posHour(r: ReportLedger.Dated, clock: ReportClock?) =
    if (clock != null && r.createdAt > 0) clock.hour(r.createdAt).toString().padStart(2, '0') else ""

  private fun docDate(r: ReportLedger.Dated, clock: ReportClock?) =
    if (r.date != "") r.date else if (clock != null && r.createdAt > 0) clock.date(r.createdAt) else ""

  private fun timeOf(r: ReportLedger.Dated): Long =
    if (r.createdAt > 0) r.createdAt else (ReportDates.epochDay(r.date)?.let { it * ReportDates.DAY_MS } ?: 0L)

  /* ---------------- بهای تمام‌شده (RPT-03) ---------------- */

  /**
   *  بهای هر قلم و هر برگشتی. LINE = همان امروز (`purchasePrice`ِ قلم × مقدار).
   *  MAVG = میانگینِ موزونِ متحرک با بازپخشِ ورودِ انبار، فروش و برگشتی به ترتیبِ
   *  زمان؛ پیش از نخستین ورود، بهای ثبت‌شده روی قلم. شرحِ کامل سرِ `costsOf`ِ JS.
   */
  fun costs(l: ReportLedger, mode: Costing): Costs {
    val line = HashMap<String, Long>()
    val ret = HashMap<String, Long>()
    val est = HashSet<String>()
    fun lineMode(it: ReportLedger.Item): Long {
      if (it.unitCost == 0L) est += it.id
      return rnd(it.unitCost.toDouble() * it.qty.toDouble() / 1000.0)
    }
    if (mode != Costing.MAVG) {
      l.items.forEach { line[it.id] = lineMode(it) }
      l.returns.forEach { r ->
        val it = l.itemById[r.saleItemId]
        ret[r.id] = if (it != null) rnd(it.unitCost.toDouble() * r.qty.toDouble() / 1000.0) else 0L
      }
      return Costs(Costing.LINE, line, ret, est)
    }
    class Ev(val t: Long, val k: Int, val id: String, val e: ReportLedger.Entry? = null, val it: ReportLedger.Item? = null, val r: ReportLedger.Return? = null)
    val ev = ArrayList<Ev>()
    l.entries.forEach { ev += Ev(timeOf(it), 0, it.id, e = it) }
    l.sales.forEach { s ->
      if (s.cancelled) return@forEach
      l.itemsBySale[s.id].orEmpty().forEach { ev += Ev(timeOf(s), 1, it.id, it = it) }
    }
    l.returns.forEach { r ->
      if (l.saleById[r.saleId]?.cancelled == true) return@forEach
      ev += Ev(timeOf(r), 2, r.id, r = r)
    }
    ev.sortWith(compareBy<Ev>({ it.t }, { it.k }, { it.id }))

    class Pool(var q: Long = 0, var v: Long = 0, var unit: Double? = null)
    val pools = HashMap<String, Pool>()
    fun pool(pid: String) = pools.getOrPut(pid) { Pool() }
    var short = false
    fun take(p: Pool, q: Long, fallbackUnit: Double): Long {
      short = false
      val cost = if (p.q > 0 && q <= p.q) rnd(p.v.toDouble() * q.toDouble() / p.q.toDouble())
      else {
        short = true
        val avail = if (p.q > 0) p.q else 0L
        val unit = p.unit ?: fallbackUnit
        (if (avail > 0) p.v else 0L) + rnd((q - avail).toDouble() * unit / 1000.0)
      }
      p.q -= q; p.v -= cost
      return cost
    }
    fun put(p: Pool, q: Long, value: Long) {
      if (p.q < 0) {
        p.q += q
        p.v = rnd(p.q.toDouble() * value.toDouble() / q.toDouble())
        p.unit = value.toDouble() * 1000.0 / q.toDouble()
        return
      }
      p.q += q; p.v += value
      if (p.q > 0) p.unit = p.v.toDouble() * 1000.0 / p.q.toDouble()
    }
    for (x in ev) {
      when (x.k) {
        0 -> {
          val e = x.e!!
          val p = pool(e.productId)
          if (e.qty > 0) {
            val u = if (!e.adjustment || e.unitCost > 0) e.unitCost.toDouble()
            else if (p.q > 0) p.v.toDouble() * 1000.0 / p.q.toDouble() else (p.unit ?: 0.0)
            put(p, e.qty, rnd(e.qty.toDouble() * u / 1000.0))
          } else if (e.qty < 0) {
            take(p, -e.qty, p.unit ?: e.unitCost.toDouble())
          }
        }
        1 -> {
          val it = x.it!!
          if (it.productId == "" || it.qty <= 0) line[it.id] = lineMode(it)
          else { line[it.id] = take(pool(it.productId), it.qty, it.unitCost.toDouble()); if (short) est += it.id }
        }
        else -> {
          val r = x.r!!
          val item = l.itemById[r.saleItemId]
          if (item == null || item.qty <= 0 || r.qty <= 0) { ret[r.id] = 0L; continue }
          val c = rnd(line.getValue(item.id).toDouble() * r.qty.toDouble() / item.qty.toDouble())
          ret[r.id] = c
          if (item.productId != "") put(pool(item.productId), r.qty, c)
        }
      }
    }
    l.items.forEach { if (it.id !in line) line[it.id] = lineMode(it) }
    return Costs(Costing.MAVG, line, ret, est)
  }

  /* ---------------- تخصیصِ تخفیف ---------------- */

  /** «بزرگ‌ترین باقی‌مانده» با پلهٔ یک واحدِ کوچک: جمعِ سهم‌ها دقیقاً D */
  fun allocate(d: Long, weights: List<Long>): List<Long> {
    val n = weights.size
    val out = LongArray(n)
    if (d == 0L || n == 0) return out.toList()
    val sign = if (d < 0) -1L else 1L
    val a = d * sign
    var w = 0L
    weights.forEach { if (it > 0) w += it }
    if (w <= 0) { out[0] = d; return out.toList() }
    class F(val i: Int, val f: Double)
    val frac = ArrayList<F>()
    var given = 0L
    for (i in 0 until n) {
      val wi = if (weights[i] > 0) weights[i] else 0L
      val exact = a.toDouble() * wi.toDouble() / w.toDouble()
      val base = floor(exact)
      out[i] = base.toLong(); given += base.toLong(); frac += F(i, exact - base)
    }
    frac.sortWith { x, y -> val df = y.f - x.f; if (df != 0.0) (if (df > 0) 1 else -1) else x.i - y.i }
    var rem = a - given
    var k = 0
    while (rem > 0) { out[frac[k % n].i] += 1; k++; rem-- }
    k = n - 1
    while (rem < 0) { if (out[frac[k].i] > 0) { out[frac[k].i] -= 1; rem++ }; k = (k + n - 1) % n }
    return out.map { it * sign }
  }

  /* ---------------- فروش (RPT-02 + RPT-03) ---------------- */

  val DIMS = listOf("product", "category", "user", "hour", "payment", "channel")

  private class Acc(val key: String, val name: String) {
    val inv = HashSet<String>()
    var qty = 0L; var gross = 0L; var sales = 0L; var returns = 0L; var cogs = 0L
    fun finish(): Row {
      val net = sales - returns
      return Row(key, name, inv.size, qty, gross, gross - sales, sales, returns, net, cogs, net - cogs, bp(net - cogs, net))
    }
  }

  private class Agg(val totals: Totals, val dims: Map<String, List<Row>>)

  private fun aggregate(l: ReportLedger, from: String, to: String, clock: ReportClock?, costs: Costs, withDims: Boolean): Agg {
    var invoices = 0; var returnCount = 0
    var gross = 0L; var discount = 0L; var sales = 0L; var returns = 0L; var cogs = 0L; var estimated = 0
    val acc = DIMS.associateWith { HashMap<String, Acc>() }
    fun productName(pid: String, fallback: String) = l.products[pid]?.name?.takeIf { it != "" } ?: fallback
    fun categoryOf(pid: String) = l.products[pid]?.category ?: ""
    fun row(dim: String, key: String, name: String) = acc.getValue(dim).getOrPut(key) { Acc(key, name) }
    fun keysFor(pid: String, itemName: String, sale: ReportLedger.Sale?, hour: String): Map<String, Pair<String, String>> {
      val seller = sale?.seller ?: ""; val method = sale?.method ?: ""; val channel = sale?.channel ?: ""
      return mapOf(
        "product" to (pid to (if (pid == "") "" else productName(pid, itemName))),
        "category" to (categoryOf(pid) to categoryOf(pid)),
        "user" to (seller to seller),
        "hour" to (hour to hour),
        "payment" to (method to method),
        "channel" to (channel to channel),
      )
    }
    class Line(val pid: String, val name: String, val qty: Long, val gross: Long, val cost: Long, val est: Boolean)
    for (s in l.sales) {
      if (s.cancelled) continue
      val d = posDate(s, clock)
      if (!(d >= from && d <= to)) continue
      invoices++; gross += s.total; discount += s.discount; sales += s.finalTotal
      val lines = l.itemsBySale[s.id].orEmpty().map { Line(it.productId, it.name, it.qty, it.total, costs.line.getValue(it.id), it.id in costs.est) }
        .ifEmpty { listOf(Line("", "", 0L, s.total, 0L, true)) }
      val shares = allocate(lines.sumOf { it.gross } - s.finalTotal, lines.map { it.gross })
      val hour = posHour(s, clock)
      lines.forEachIndexed { i, ln ->
        cogs += ln.cost
        if (ln.est) estimated++
        if (!withDims) return@forEachIndexed
        val keys = keysFor(ln.pid, ln.name, s, hour)
        for (dim in DIMS) {
          val (k, n) = keys.getValue(dim)
          val r = row(dim, k, n)
          r.inv += s.id; r.qty += ln.qty; r.gross += ln.gross; r.sales += ln.gross - shares[i]; r.cogs += ln.cost
        }
      }
    }
    for (r in l.returns) {
      val s = l.saleById[r.saleId]
      if (s?.cancelled == true) continue
      val d = posDate(r, clock)
      if (!(d >= from && d <= to)) continue
      val c = costs.ret[r.id] ?: 0L
      returnCount++; returns += r.amount; cogs -= c
      if (!withDims) continue
      val it = l.itemById[r.saleItemId]
      val keys = keysFor(it?.productId ?: r.productId, it?.name ?: "", s, posHour(r, clock))
      for (dim in DIMS) {
        val (k, n) = keys.getValue(dim)
        val x = row(dim, k, n)
        x.qty -= r.qty; x.returns += r.amount; x.cogs -= c
      }
    }
    val net = sales - returns
    val totals = Totals(
      invoices, returnCount, gross, discount, sales, returns, net, cogs, net - cogs, bp(net - cogs, net),
      if (invoices > 0) divRound(sales, invoices.toLong()) else 0L,
      estimated, costs.mode == Costing.LINE || estimated > 0,
    )
    if (!withDims) return Agg(totals, emptyMap())
    val dims = DIMS.associateWith { dim ->
      val rows = acc.getValue(dim).values.map { it.finish() }
      if (dim == "hour") rows.sortedWith { a, b -> a.key.compareTo(b.key) }
      else rows.sortedWith { a, b -> if (b.netSales != a.netSales) b.netSales.compareTo(a.netSales) else a.key.compareTo(b.key) }
    }
    return Agg(totals, dims)
  }

  /** RPT-02/03 — فروشِ بازهٔ `from..to` (روزِ کاری، هر دو سر) به شش تفکیک، با سود و حاشیه */
  fun sales(l: ReportLedger, from: String? = null, to: String? = null, clock: ReportClock? = null, costing: Costing = Costing.LINE): SalesReport {
    val f = from?.ifEmpty { null } ?: "0000-01-01"
    val t = to?.ifEmpty { null } ?: "9999-12-31"
    val a = aggregate(l, f, t, clock, costs(l, costing), true)
    fun d(k: String) = a.dims.getValue(k)
    return SalesReport(f, t, costing.id, a.totals, d("product"), d("category"), d("user"), d("hour"), d("payment"), d("channel"))
  }

  /* ---------------- داشبوردِ امروز (RPT-01) ---------------- */

  private fun delta(a: Day, b: Day): Map<String, Delta> {
    fun one(x: Long, y: Long) = Delta(x - y, bp(x - y, y))
    return linkedMapOf(
      "invoices" to one(a.invoices.toLong(), b.invoices.toLong()),
      "sales" to one(a.sales, b.sales),
      "returns" to one(a.returns, b.returns),
      "netSales" to one(a.netSales, b.netSales),
      "grossProfit" to one(a.grossProfit, b.grossProfit),
      "avgTicket" to one(a.avgTicket, b.avgTicket),
      "discount" to one(a.discount, b.discount),
    )
  }

  /** RPT-01 — امروز در برابرِ دیروز و همان روزِ هفتهٔ پیش. `date` یا `now` + `clock` لازم است. */
  fun today(l: ReportLedger, date: String? = null, clock: ReportClock? = null, costing: Costing = Costing.LINE, now: Long = 0): TodayReport {
    val d0 = date?.ifEmpty { null } ?: (if (clock != null && now > 0) clock.date(now) else "")
    require(ReportDates.epochDay(d0) != null) { "report-core: today() needs opts.date or opts.now + opts.clock" }
    val costs = costs(l, costing)
    fun day(d: String): Day {
      val t = aggregate(l, d, d, clock, costs, false).totals
      return Day(d, t.invoices, t.returnCount, t.gross, t.discount, t.sales, t.returns, t.netSales, t.cogs, t.grossProfit, t.marginBp, t.avgTicket,
        t.costEstimatedLines, t.profitApproximate)
    }
    val t = day(d0)
    val y = day(ReportDates.addDays(d0, -1))
    val w = day(ReportDates.addDays(d0, -7))
    return TodayReport(d0, t, y, w, delta(t, y), delta(t, w))
  }

  /* ---------------- سنِ بدهی (RPT-06) ---------------- */

  private class DebtEv(val id: String, val k: Int, val debit: Boolean, val amount: Long, val date: String, val createdAt: Long)

  private fun ageing(groups: Map<String, List<DebtEv>>, names: Map<String, String>, asOf: String, buckets: List<Long>): Ageing {
    val asOfDay = ReportDates.epochDay(asOf)!!
    val rows = ArrayList<AgeRow>()
    var tBalance = 0L; var tOpen = 0L; var tCredit = 0L
    val tBuckets = LongArray(buckets.size + 1)
    for (id in groups.keys.sorted()) {
      val evs = groups.getValue(id).sortedWith(compareBy<DebtEv>({ it.date }, { it.createdAt }, { it.k }, { it.id }))
      class Lot(val date: String, var left: Long)
      val lots = ArrayDeque<Lot>()
      var credit = 0L
      for (e in evs) {
        if (e.debit) {
          var left = e.amount
          val used = min(credit, left)
          credit -= used; left -= used
          if (left > 0) lots.addLast(Lot(e.date, left))
        } else {
          var pay = e.amount
          while (pay > 0 && lots.isNotEmpty()) {
            val take = min(pay, lots.first().left)
            lots.first().left -= take; pay -= take
            if (lots.first().left == 0L) lots.removeFirst()
          }
          credit += pay
        }
      }
      var open = 0L
      var oldest: Long? = null
      val bk = LongArray(buckets.size + 1)
      for (lot in lots) {
        val n = ReportDates.epochDay(lot.date)
        val age = if (n == null) null else max(0L, asOfDay - n)
        var i = 0
        if (age == null) i = buckets.size else while (i < buckets.size && age > buckets[i]) i++
        bk[i] += lot.left; open += lot.left
        val a = age ?: Long.MAX_VALUE
        if (oldest == null || a > oldest) oldest = a
      }
      if (open == 0L && credit == 0L) continue
      rows += AgeRow(id, names[id] ?: "", open - credit, open, credit, bk.toList(), if (oldest == Long.MAX_VALUE) -1L else oldest)
      tBalance += open - credit; tOpen += open; tCredit += credit
      for (i in bk.indices) tBuckets[i] += bk[i]
    }
    val sorted = rows.sortedWith { a, b -> if (b.balance != a.balance) b.balance.compareTo(a.balance) else a.id.compareTo(b.id) }
    return Ageing(sorted, AgeTotals(tBalance, tOpen, tCredit, tBuckets.toList()))
  }

  /**
   *  RPT-06 — بدهیِ مشتری‌ها (اولین‌وارد‌اولین‌پرداخت، مثلِ `DebtorEngine.debtSince`) و
   *  طلبِ تأمین‌کنندگان (مثلِ `getSupplierDebt`ِ وب) با سنِ هر مانده تا `asOf`.
   *  `buckets` مرزِ روزهاست: [30,60,90] ⇒ ۰–۳۰، ۳۱–۶۰، ۶۱–۹۰، ۹۱+. سندِ بعد از `asOf` بیرون است.
   */
  fun debts(l: ReportLedger, asOf: String, buckets: List<Long> = listOf(30L, 60L, 90L), clock: ReportClock? = null): DebtReport {
    require(ReportDates.epochDay(asOf) != null) { "report-core: debts() needs opts.asOf" }
    val b = buckets.ifEmpty { listOf(30L, 60L, 90L) }
    fun inScope(d: String) = ReportDates.epochDay(d) == null || d <= asOf
    val cust = LinkedHashMap<String, MutableList<DebtEv>>()
    val sup = LinkedHashMap<String, MutableList<DebtEv>>()
    for (t in l.transactions) {
      val d = docDate(t, clock)
      if (t.amount <= 0 || !inScope(d)) continue
      cust.getOrPut(t.debtorId) { ArrayList() } += DebtEv(t.id, 0, t.give, t.amount, d, t.createdAt)
    }
    for (p in l.purchases) {
      val d = docDate(p, clock)
      val owed = p.total - p.paid
      if (owed == 0L || !inScope(d)) continue
      sup.getOrPut(p.supplierId) { ArrayList() } += DebtEv(p.id, 0, owed > 0, if (owed > 0) owed else -owed, d, p.createdAt)
    }
    for (p in l.supplierPayments) {
      val d = docDate(p, clock)
      if (p.amount <= 0 || !inScope(d)) continue
      sup.getOrPut(p.supplierId) { ArrayList() } += DebtEv(p.id, 1, false, p.amount, d, p.createdAt)
    }
    return DebtReport(asOf, b, ageing(cust, l.debtors, asOf, b), ageing(sup, l.suppliers, asOf, b))
  }

  /* ---------------- ضدتقلب (RPT-09) ---------------- */

  private class FAcc(val key: String) {
    var invoices = 0; var gross = 0L; var discountCount = 0; var discount = 0L
    var returnCount = 0; var returns = 0L; var voidCount = 0; var voids = 0L
    var lineDeleteCount = 0; var lineDeletes = 0L; var noSaleCount = 0; var overrideCount = 0; var approvalCount = 0
    fun row() = FraudRow(key, invoices, gross, discountCount, discount, bp(discount, gross), returnCount, returns,
      voidCount, voids, lineDeleteCount, lineDeletes, noSaleCount, overrideCount, approvalCount)
  }

  /**
   *  RPT-09 — تخفیف (فروشنده)، برگشتی، لغو، حذفِ ردیف (`auditLog` نوعِ `line_delete`) و
   *  باز کردنِ کشو بی فروش (`no_sale`، همان نامِ pay-core) به تفکیکِ کاربر. کاربر فقط از فیلدِ
   *  صریحِ همان رویداد می‌آید؛ نبودش "" (نامعلوم) است و به فروشندهٔ فاکتور نسبت داده نمی‌شود.
   *  v3 S08: رویدادِ دارای `approvedBy` برای کننده `overrideCount` و برای تأییدکننده `approvalCount`.
   */
  fun fraud(l: ReportLedger, from: String? = null, to: String? = null, clock: ReportClock? = null): FraudReport {
    val f = from?.ifEmpty { null } ?: "0000-01-01"
    val t = to?.ifEmpty { null } ?: "9999-12-31"
    fun inRange(d: String) = d >= f && d <= t
    val users = HashMap<String, FAcc>()
    fun u(k: String) = users.getOrPut(k) { FAcc(k) }
    fun approved(x: FAcc, approver: String) { if (approver != "") { x.overrideCount++; u(approver).approvalCount++ } }
    val cancelEntry = HashMap<String, ReportLedger.Audit>()
    l.audit.forEach { a -> if (a.type == "cancel_sale" && a.refId != "" && a.refId !in cancelEntry) cancelEntry[a.refId] = a }
    for (s in l.sales) {
      if (s.cancelled) {
        val a = cancelEntry[s.id]
        if (!inRange(if (a != null) posDate(a, clock) else posDate(s, clock))) continue
        val v = u(if (s.cancelledBy != "") s.cancelledBy else a?.user ?: "")
        v.voidCount++; v.voids += s.finalTotal
        approved(v, a?.approvedBy ?: "")
        continue
      }
      if (!inRange(posDate(s, clock))) continue
      val x = u(s.seller)
      x.invoices++; x.gross += s.total
      if (s.discount > 0) { x.discountCount++; x.discount += s.discount }
      approved(x, s.approvedBy)
    }
    for (r in l.returns) {
      if (l.saleById[r.saleId]?.cancelled == true || !inRange(posDate(r, clock))) continue
      val x = u(r.user)
      x.returnCount++; x.returns += r.amount
      approved(x, r.approvedBy)
    }
    for (a in l.audit) {
      if ((a.type != "line_delete" && a.type != "no_sale") || !inRange(posDate(a, clock))) continue
      val x = u(a.user)
      if (a.type == "line_delete") { x.lineDeleteCount++; x.lineDeletes += a.amount } else x.noSaleCount++
      approved(x, a.approvedBy)
    }
    val rows = users.keys.sorted().map { users.getValue(it).row() }
    val tot = FAcc("")
    users.values.forEach { x ->
      tot.invoices += x.invoices; tot.gross += x.gross; tot.discountCount += x.discountCount; tot.discount += x.discount
      tot.returnCount += x.returnCount; tot.returns += x.returns; tot.voidCount += x.voidCount; tot.voids += x.voids
      tot.lineDeleteCount += x.lineDeleteCount; tot.lineDeletes += x.lineDeletes; tot.noSaleCount += x.noSaleCount
      tot.overrideCount += x.overrideCount; tot.approvalCount += x.approvalCount
    }
    return FraudReport(f, t, rows, tot.row())
  }
}
