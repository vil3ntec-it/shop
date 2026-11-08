package ir.vil3ntec.tohid.core.report

import ir.vil3ntec.tohid.core.stock.Position
import ir.vil3ntec.tohid.core.stock.StockBook
import ir.vil3ntec.tohid.core.stock.StockCore
import ir.vil3ntec.tohid.core.stock.StockDoc
import ir.vil3ntec.tohid.core.stock.StockMode
import ir.vil3ntec.tohid.core.stock.StockResult
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
 *  صفحه‌ها: «گزارشِ تازه» (`ReportFreshModel`)، صفحهٔ کالا (`stockCost`) و گزارشِ خودکارِ سرور (همان JS). موتورهای
 *  پیشین (`ReportEngine`، `DashboardEngine`) دست نخورده‌اند. میانگینِ موزون با `StockCore` (`mavg`، بردار: `cost-v1.json`).
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
    /** v3 ۱۵.۴ بند ۴: سود «تقریبی» است — D-23 هنوز ثبت نشده، بهای LINE، یا قلمِ بی‌بها */
    val profitApproximate: Boolean,
  )

  @Serializable
  data class Day(
    val date: String, val invoices: Int, val returnCount: Int, val gross: Long, val discount: Long, val sales: Long,
    val returns: Long, val netSales: Long, val cogs: Long, val grossProfit: Long, val marginBp: Long?,
    val avgTicket: Long,
    /** قلم‌های فروخته‌شدهٔ بازه که بهایشان معلوم نیست یا تخمینی است */
    val costEstimatedLines: Int,
    /** v3 ۱۵.۴ بند ۴: سود «تقریبی» است — D-23 هنوز ثبت نشده، بهای LINE، یا قلمِ بی‌بها */
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

  class Costs(val mode: Costing, val approved: Boolean, val line: Map<String, Long>, val ret: Map<String, Long>, val est: Set<String>)

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

  /* ---------------- بهای تمام‌شده (RPT-03، PRD-15) ---------------- */

  /**
   *  بهای هر قلم و هر برگشتی. LINE = `purchasePrice`ِ قلم × مقدار (انتخابِ همان دستگاه، همیشه «تقریبی»).
   *  MAVG = میانگینِ موزونِ متحرک با **همان** هستهٔ موجودی (`StockCore`، حالتِ RECORDED، یک جایگاه برای
   *  هر کالا) — شرحِ کامل سرِ `mavgReplay` ِ `license/report-core.js`؛ بردار: `core/test-vectors/cost-v1.json`.
   *  `approved` = همین روش مصوبِ D-23 است (`CostPolicy.approved`)؛ LINE هرگز سودِ قطعی نمی‌دهد.
   */
  fun costs(l: ReportLedger, mode: Costing, approved: Boolean = false): Costs {
    if (mode == Costing.MAVG) {
      val m = mavg(l)
      return Costs(Costing.MAVG, approved, m.line, m.ret, m.est)
    }
    val line = HashMap<String, Long>()
    val ret = HashMap<String, Long>()
    val est = HashSet<String>()
    l.items.forEach {
      if (it.unitCost == 0L) est += it.id
      line[it.id] = rnd(it.unitCost.toDouble() * it.qty.toDouble() / 1000.0)
    }
    l.returns.forEach { r ->
      val it = l.itemById[r.saleItemId]
      ret[r.id] = if (it != null) rnd(it.unitCost.toDouble() * r.qty.toDouble() / 1000.0) else 0L
    }
    return Costs(Costing.LINE, false, line, ret, est)
  }

  private const val STOCK_WH = "main"
  private const val STOCK_USER = "report"

  private fun safe(x: Long) = x in -StockCore.MAX_SAFE..StockCore.MAX_SAFE

  /** round(a × b ÷ c) ِ هستهٔ موجودی؛ عددِ نا‌امن (بیرون از ۲^۵۳) ⇒ null، نه خطا — همان `mdr` ِ JS */
  private fun mdr(a: Long, b: Long, c: Long): Long? {
    if (!safe(a) || !safe(b) || !safe(c) || c <= 0) return null
    val r = try { StockCore.mulDivRound(a, b, c) } catch (e: ArithmeticException) { return null }
    return if (safe(r)) r else null
  }

  private class Mavg(
    val line: Map<String, Long>, val ret: Map<String, Long>, val est: Set<String>,
    val books: Map<String, StockBook>, val unknown: Set<String>,
  )

  /** دوقلوی `mavgReplay` ِ JS — هر گام همان ترتیب و همان سند */
  private fun mavg(l: ReportLedger): Mavg {
    val line = HashMap<String, Long>()
    val ret = HashMap<String, Long>()
    val est = HashSet<String>()
    val books = HashMap<String, StockBook>()
    val unknown = HashSet<String>()
    fun lineMode(it: ReportLedger.Item): Long {
      if (it.unitCost == 0L) est += it.id
      return rnd(it.unitCost.toDouble() * it.qty.toDouble() / 1000.0)
    }
    fun step(pid: String, doc: StockDoc): StockResult? {
      val a = try { StockCore.apply(books[pid] ?: StockBook(), doc, StockMode.RECORDED) } catch (e: ArithmeticException) { return null }
      if (!a.result.ok) return null
      books[pid] = a.book
      return a.result
    }
    fun posOf(pid: String): Position? = books[pid]?.let { StockCore.position(it, pid, STOCK_WH) }
    fun afterOut(pid: String) { val p = posOf(pid); if (p != null && p.qtyMilli <= 0) unknown -= pid }
    fun doc(t: Long, type: String, pid: String, id: String, qty: Long, reason: String? = null, cost: Long? = null, landed: Long? = null) =
      StockDoc(id, type, pid, STOCK_WH, STOCK_USER, if (t > 0) t else 1L, qtyMilli = qty, reason = reason, costMinor = cost, landedMinor = landed)

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

    for (x in ev) {
      when (x.k) {
        0 -> {
          val e = x.e!!
          if (e.productId == "" || e.qty == 0L) continue
          val adjust = e.adjustment || e.qty < 0
          //  ارزشِ دقیقِ ردیف (`costMinor`)، نه مقدار × بهای گردشده: خرید (PRD-07) و خروجیِ تولید (INV-21، جمعِ بهای اجزا — همان
          //  سندِ `production` ِ `StockLedger`)؛ `value` فقط همین دو را دارد (`ReportLedger.parse`)
          val priced = e.qty > 0 && (!e.adjustment || e.unitCost > 0 || e.value != null)
          val exact = if (e.value != null && e.value >= 0) e.value else null
          val cost = if (priced) exact ?: mdr(e.qty, e.unitCost, 1000) ?: continue else null
          val p = posOf(e.productId)
          val blind = e.qty > 0 && cost == null && !(p != null && (p.qtyMilli > 0 || p.lastCost != null))
          val d = doc(
            x.t, if (adjust) "count_adjust" else "purchase_receipt", e.productId, "e:" + e.id, e.qty,
            reason = if (adjust) "adjustment" else null, cost = cost, landed = if (!adjust && e.landed > 0) e.landed else null,
          )
          step(e.productId, d) ?: continue
          if (blind) unknown += e.productId
          if (e.qty < 0) afterOut(e.productId)
        }
        1 -> {
          val it = x.it!!
          if (it.productId == "" || it.qty <= 0) { line[it.id] = lineMode(it); continue }
          val wasUnknown = it.productId in unknown
          val res = step(it.productId, doc(x.t, "sale", it.productId, "s:" + it.id, it.qty))
          if (res == null) { line[it.id] = lineMode(it); est += it.id; continue }
          var cost = res.costMinor
          if (res.shortMilli > 0) {
            est += it.id
            val guess = if (res.reviewTask?.reasons?.contains("unknown_cost") == true && it.unitCost > 0) mdr(res.shortMilli, it.unitCost, 1000) else null
            if (guess != null && safe(cost + guess)) cost += guess
          }
          if (wasUnknown) est += it.id
          line[it.id] = cost
          afterOut(it.productId)
        }
        else -> {
          val r = x.r!!
          val item = l.itemById[r.saleItemId]
          if (item == null || item.qty <= 0 || r.qty <= 0) { ret[r.id] = 0L; continue }
          val left = line[item.id] ?: rnd(item.unitCost.toDouble() * item.qty.toDouble() / 1000.0)
          val c = mdr(left, r.qty, item.qty)
          ret[r.id] = c ?: 0L
          if (item.productId == "" || c == null || c < 0) continue
          step(item.productId, doc(x.t, "customer_return", item.productId, "r:" + r.id, r.qty, cost = c))
        }
      }
    }
    l.items.forEach { if (it.id !in line) line[it.id] = lineMode(it) }
    return Mavg(line, ret, est, books, unknown)
  }

  /** PRD-15 — یک کالا: موجودی، ارزش و بهای میانگینِ یک واحد (همان `stockCost` ِ JS) */
  @Serializable
  data class StockCostRow(
    val productId: String, val name: String, val qtyMilli: Long, val valueMinor: Long,
    /** بهای یک واحد، واحدِ کوچک؛ null = هیچ بهای معلومی نیست */
    val unitCostMinor: Long?,
    /** موجودیِ صفر/منفی (بهای آخر) یا پایهٔ نامعلوم */
    val approximate: Boolean,
  )

  @Serializable
  data class StockCostTotals(val products: Int, val valueMinor: Long, val negative: Int, val approximate: Boolean)

  @Serializable
  data class StockCost(val rows: List<StockCostRow>, val totals: StockCostTotals)

  /**
   *  PRD-15 — بهای میانگینِ هر کالا و ارزشِ موجودیِ همین حالا از **همان** بازپخشِ میانگینِ موزون؛ فقط خواندنی.
   *  [products] فقط همان کالاها (عددشان همان است). شرحِ قاعده سرِ `stockCost` ِ JS.
   */
  fun stockCost(l: ReportLedger, products: Collection<String>? = null): StockCost {
    val m = mavg(l)
    val want = products?.takeIf { it.isNotEmpty() }?.toHashSet()
    val rows = ArrayList<StockCostRow>()
    var value = 0L
    var negative = 0
    var approx = false
    for (pid in m.books.keys.sorted()) {
      if (want != null && pid !in want) continue
      val p = StockCore.position(m.books.getValue(pid), pid, STOCK_WH) ?: continue
      val unit = when {
        p.qtyMilli > 0 -> mdr(p.valueMinor, 1000, p.qtyMilli)
        p.lastCost != null -> mdr(p.lastCost.num, 1000, p.lastCost.den)
        else -> null
      }
      val a = p.qtyMilli <= 0 || pid in m.unknown
      rows += StockCostRow(pid, l.products[pid]?.name ?: "", p.qtyMilli, p.valueMinor, unit, a)
      if (p.qtyMilli > 0) { value += p.valueMinor; if (a) approx = true } else if (p.qtyMilli < 0) negative += 1
    }
    return StockCost(rows, StockCostTotals(rows.size, value, negative, approx))
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

  /**
   *  POS-23 — «کاربر» ِ یک ردیف: با [lineSeller] (تنظیمِ خاموشِ دکان `line_seller`) فروشندهٔ ردیف (`saleItems[].seller`) و
   *  نبودش فروشندهٔ فاکتور؛ بی آن همیشه فروشندهٔ فاکتور (همان پیشین). همان `userOf` ِ `report-core.js`.
   */
  private fun userOf(item: ReportLedger.Item?, sale: ReportLedger.Sale?, lineSeller: Boolean): String =
    if (lineSeller && item != null && item.seller != "") item.seller else sale?.seller ?: ""

  private fun aggregate(
    l: ReportLedger, from: String, to: String, clock: ReportClock?, costs: Costs, withDims: Boolean, lineSeller: Boolean = false,
  ): Agg {
    var invoices = 0; var returnCount = 0
    var gross = 0L; var discount = 0L; var sales = 0L; var returns = 0L; var cogs = 0L; var estimated = 0
    val acc = DIMS.associateWith { HashMap<String, Acc>() }
    fun productName(pid: String, fallback: String) = l.products[pid]?.name?.takeIf { it != "" } ?: fallback
    fun categoryOf(pid: String) = l.products[pid]?.category ?: ""
    fun row(dim: String, key: String, name: String) = acc.getValue(dim).getOrPut(key) { Acc(key, name) }
    fun keysFor(pid: String, itemName: String, sale: ReportLedger.Sale?, hour: String, item: ReportLedger.Item?): Map<String, Pair<String, String>> {
      val seller = userOf(item, sale, lineSeller); val method = sale?.method ?: ""; val channel = sale?.channel ?: ""
      return mapOf(
        "product" to (pid to (if (pid == "") "" else productName(pid, itemName))),
        "category" to (categoryOf(pid) to categoryOf(pid)),
        "user" to (seller to seller),
        "hour" to (hour to hour),
        "payment" to (method to method),
        "channel" to (channel to channel),
      )
    }
    class Line(val pid: String, val name: String, val qty: Long, val gross: Long, val cost: Long, val est: Boolean, val item: ReportLedger.Item? = null)
    for (s in l.sales) {
      if (s.cancelled) continue
      val d = posDate(s, clock)
      if (!(d >= from && d <= to)) continue
      invoices++; gross += s.total; discount += s.discount; sales += s.finalTotal
      val lines = l.itemsBySale[s.id].orEmpty().map { Line(it.productId, it.name, it.qty, it.total, costs.line.getValue(it.id), it.id in costs.est, it) }
        .ifEmpty { listOf(Line("", "", 0L, s.total, 0L, true)) }
      val shares = allocate(lines.sumOf { it.gross } - s.finalTotal, lines.map { it.gross })
      val hour = posHour(s, clock)
      lines.forEachIndexed { i, ln ->
        cogs += ln.cost
        if (ln.est) estimated++
        if (!withDims) return@forEachIndexed
        val keys = keysFor(ln.pid, ln.name, s, hour, ln.item)
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
      //  POS-23: برگشت از فروشندهٔ **ردیفِ اصل** کم می‌شود (با `lineSeller`)
      val keys = keysFor(it?.productId ?: r.productId, it?.name ?: "", s, posHour(r, clock), it)
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
      estimated, !costs.approved || estimated > 0,
    )
    if (!withDims) return Agg(totals, emptyMap())
    val dims = DIMS.associateWith { dim ->
      val rows = acc.getValue(dim).values.map { it.finish() }
      if (dim == "hour") rows.sortedWith { a, b -> a.key.compareTo(b.key) }
      else rows.sortedWith { a, b -> if (b.netSales != a.netSales) b.netSales.compareTo(a.netSales) else a.key.compareTo(b.key) }
    }
    return Agg(totals, dims)
  }

  /**
   *  RPT-02/03 — فروشِ بازهٔ `from..to` (روزِ کاری، هر دو سر) به شش تفکیک، با سود و حاشیه.
   *  [lineSeller] (POS-23، تنظیمِ خاموشِ `line_seller`): تفکیکِ «کاربر» به فروشندهٔ هر ردیف و برگشت از فروشندهٔ ردیفِ اصل؛
   *  `false` ⇒ بیت‌به‌بیت پیشین. همان `opts.lineSeller` ِ `report-core.js`.
   */
  fun sales(
    l: ReportLedger, from: String? = null, to: String? = null, clock: ReportClock? = null,
    costing: Costing = Costing.LINE, costApproved: Boolean = false, lineSeller: Boolean = false,
  ): SalesReport {
    val f = from?.ifEmpty { null } ?: "0000-01-01"
    val t = to?.ifEmpty { null } ?: "9999-12-31"
    val a = aggregate(l, f, t, clock, costs(l, costing, costApproved), true, lineSeller)
    fun d(k: String) = a.dims.getValue(k)
    return SalesReport(f, t, costing.id, a.totals, d("product"), d("category"), d("user"), d("hour"), d("payment"), d("channel"))
  }

  /**
   *  RPT-13 — «ریز» ِ یک ردیفِ تفکیکِ فروش (Enter یا کلیک در «گزارش‌ها»): هر فاکتورِ بازه که سهمی در کلیدِ [key] از
   *  تفکیکِ [dim] دارد، با **همان** سهمِ «فروش» (پس از تخفیفِ پخش‌شده با `allocate`) و مقداری که ستون‌های همان ردیف جمع
   *  می‌زنند — جمعِ سهم‌ها = `Row.sales` و شمارشان = `Row.invoices`. برگشت‌ها جدا در ستونِ «برگشتی» ِ ردیف‌اند و این‌جا
   *  نیستند. فقط‌خواندنی؛ به ترتیبِ زمان. همان کلیدهای `aggregate` (کالا، دسته، فروشنده، ساعت، روش، کانال).
   */
  data class InvoiceShare(val saleId: String, val date: String, val createdAt: Long, val qty: Long, val sales: Long)

  /** RPT-13: «همهٔ فاکتورها» برای [invoicesOf] — ریزِ یک روزِ «امروز» ([dayInvoices]) */
  const val ALL = "all"

  fun invoicesOf(
    l: ReportLedger, from: String?, to: String?, dim: String, key: String, clock: ReportClock? = null, lineSeller: Boolean = false,
  ): List<InvoiceShare> {
    if (dim !in DIMS && dim != ALL) return emptyList()
    val f = from?.ifEmpty { null } ?: "0000-01-01"
    val t = to?.ifEmpty { null } ?: "9999-12-31"
    val out = ArrayList<InvoiceShare>()
    for (s in l.sales) {
      if (s.cancelled) continue
      val d = posDate(s, clock)
      if (!(d >= f && d <= t)) continue
      val items = l.itemsBySale[s.id].orEmpty()
      val users = items.map { userOf(it, s, lineSeller) }.ifEmpty { listOf(s.seller) }
      val pids = items.map { it.productId }.ifEmpty { listOf("") }
      val qtys = items.map { it.qty }.ifEmpty { listOf(0L) }
      val gross = items.map { it.total }.ifEmpty { listOf(s.total) }
      val shares = allocate(gross.sum() - s.finalTotal, gross)
      val hour = posHour(s, clock)
      var qty = 0L; var sales = 0L; var hit = false
      pids.forEachIndexed { i, pid ->
        val k = when (dim) {
          "product" -> pid
          "category" -> l.products[pid]?.category ?: ""
          "user" -> users[i]
          "hour" -> hour
          "payment" -> s.method
          ALL -> key
          else -> s.channel
        }
        if (k == key) { hit = true; qty += qtys[i]; sales += gross[i] - shares[i] }
      }
      if (hit) out += InvoiceShare(s.id, d, s.createdAt, qty, sales)
    }
    return out.sortedWith(compareBy({ it.date }, { it.createdAt }, { it.saleId }))
  }

  /**
   *  RPT-13 — ریزِ یک روزِ جدولِ «امروز» (امروز، دیروز، همان روزِ هفتهٔ پیش): هر فاکتورِ لغونشدهٔ همان روز با «فروش» ِ
   *  خودش (پس از تخفیف) — جمع = ستونِ «فروش» و شمار = «فاکتور» ِ همان ردیف ([today]).
   */
  fun dayInvoices(l: ReportLedger, date: String, clock: ReportClock? = null): List<InvoiceShare> =
    if (ReportDates.epochDay(date) == null) emptyList() else invoicesOf(l, date, date, ALL, "", clock)

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
  fun today(
    l: ReportLedger, date: String? = null, clock: ReportClock? = null, costing: Costing = Costing.LINE,
    now: Long = 0, costApproved: Boolean = false,
  ): TodayReport {
    val d0 = date?.ifEmpty { null } ?: (if (clock != null && now > 0) clock.date(now) else "")
    require(ReportDates.epochDay(d0) != null) { "report-core: today() needs opts.date or opts.now + opts.clock" }
    val costs = costs(l, costing, costApproved)
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

  private class OpenX(val id: String, val date: String, var left: Long)

  /** اولین‌وارد‌اولین‌پرداخت روی رویدادهای یک طرف ⇒ ماندهٔ بازِ هر سند و پیش‌پرداخت (همان `DebtorEngine.debtSince`) */
  private fun fifo(group: List<DebtEv>): Pair<List<OpenX>, Long> {
    val evs = group.sortedWith(compareBy<DebtEv>({ it.date }, { it.createdAt }, { it.k }, { it.id }))
    val lots = ArrayDeque<OpenX>()
    var credit = 0L
    for (e in evs) {
      if (e.debit) {
        var left = e.amount
        val used = min(credit, left)
        credit -= used; left -= used
        if (left > 0) lots.addLast(OpenX(e.id, e.date, left))
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
    return lots.toList() to credit
  }

  private fun ageing(groups: Map<String, List<DebtEv>>, names: Map<String, String>, asOf: String, buckets: List<Long>): Ageing {
    val asOfDay = ReportDates.epochDay(asOf)!!
    val rows = ArrayList<AgeRow>()
    var tBalance = 0L; var tOpen = 0L; var tCredit = 0L
    val tBuckets = LongArray(buckets.size + 1)
    for (id in groups.keys.sorted()) {
      val (lots, credit) = fifo(groups.getValue(id))
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
    val (cust, sup) = debtGroups(l, asOf, clock)
    return DebtReport(asOf, b, ageing(cust, l.debtors, asOf, b), ageing(sup, l.suppliers, asOf, b))
  }

  private fun debtGroups(l: ReportLedger, asOf: String, clock: ReportClock?): Pair<Map<String, List<DebtEv>>, Map<String, List<DebtEv>>> {
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
    return cust to sup
  }

  /** یک ماندهٔ بازِ سند: شناسهٔ سند (تراکنشِ قرض یا خرید)، روز، مانده و سن تا `asOf` (روزِ نامعلوم ⇒ -1) */
  data class OpenLot(val docId: String, val date: String, val left: Long, val ageDays: Long)

  /** ماندهٔ بازِ یک طرف: جمعِ `left` = `AgeRow.open` و [credit] = `AgeRow.credit` */
  data class OpenDebt(val lots: List<OpenLot>, val credit: Long)

  /**
   *  RPT-13 — ریزِ یک ردیفِ «سنِ بدهی» (Enter یا کلیک): ماندهٔ بازِ هر سندِ همان مشتری (یا با [supplier] همان
   *  تأمین‌کننده) به همان اولین‌وارد‌اولین‌پرداخت ِ [debts]، به ترتیبِ روز. فقط‌خواندنی.
   */
  fun openDebt(l: ReportLedger, asOf: String, supplier: Boolean, id: String, clock: ReportClock? = null): OpenDebt {
    val asOfDay = ReportDates.epochDay(asOf) ?: return OpenDebt(emptyList(), 0L)
    val (cust, sup) = debtGroups(l, asOf, clock)
    val group = (if (supplier) sup else cust)[id] ?: return OpenDebt(emptyList(), 0L)
    val (lots, credit) = fifo(group)
    return OpenDebt(lots.map { x -> OpenLot(x.id, x.date, x.left, ReportDates.epochDay(x.date)?.let { max(0L, asOfDay - it) } ?: -1L) }, credit)
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

  /**
   *  یک رویدادِ ضدتقلب: [kind] یکی از `discount` (فاکتورِ با تخفیف)، `approved` (فاکتورِ بی تخفیف که تأییدکننده دارد)،
   *  `return`، `void` (لغو)، `line_delete`، `no_sale`؛ [docId] شناسهٔ فاکتور (خالی ⇒ بی فاکتور)؛ [byOther] = این ردیف
   *  کارِ کاربرِ دیگری است که همین کاربر **تأییدش کرد** (`approvalCount`).
   */
  data class FraudEvent(
    val kind: String, val docId: String, val date: String, val createdAt: Long, val amount: Long,
    val user: String, val approvedBy: String, val byOther: Boolean,
  )

  /**
   *  RPT-13 — ریزِ ردیفِ کاربرِ [key] در «ضدتقلب»: همان انتخابِ [fraud] (همان بازه، همان کاربرِ صریحِ رویداد و همان
   *  `approvedBy`)، پس شمارِ هر نوع = ستونِ همان ردیف (`discountCount`، `returnCount`، `voidCount`، `lineDeleteCount`،
   *  `noSaleCount`)، جمعِ مبلغ‌ها = `discount`/`returns`/`voids`/`lineDeletes`، رویدادهای خودِ او که تأییدکننده دارند =
   *  `overrideCount` و [FraudEvent.byOther] = `approvalCount`. به ترتیبِ زمان؛ فقط‌خواندنی.
   */
  fun fraudEvents(l: ReportLedger, from: String?, to: String?, key: String, clock: ReportClock? = null): List<FraudEvent> {
    val f = from?.ifEmpty { null } ?: "0000-01-01"
    val t = to?.ifEmpty { null } ?: "9999-12-31"
    fun inRange(d: String) = d >= f && d <= t
    val out = ArrayList<FraudEvent>()
    fun add(kind: String, doc: String, date: String, at: Long, amount: Long, user: String, approver: String) {
      if (user == key) out += FraudEvent(kind, doc, date, at, amount, user, approver, byOther = false)
      if (approver != "" && approver == key) out += FraudEvent(kind, doc, date, at, amount, user, approver, byOther = true)
    }
    val cancelEntry = HashMap<String, ReportLedger.Audit>()
    l.audit.forEach { a -> if (a.type == "cancel_sale" && a.refId != "" && a.refId !in cancelEntry) cancelEntry[a.refId] = a }
    for (s in l.sales) {
      if (s.cancelled) {
        val a = cancelEntry[s.id]
        val d = if (a != null) posDate(a, clock) else posDate(s, clock)
        if (!inRange(d)) continue
        add("void", s.id, d, a?.createdAt ?: s.createdAt, s.finalTotal, if (s.cancelledBy != "") s.cancelledBy else a?.user ?: "", a?.approvedBy ?: "")
        continue
      }
      val d = posDate(s, clock)
      if (!inRange(d)) continue
      if (s.discount > 0) add("discount", s.id, d, s.createdAt, s.discount, s.seller, s.approvedBy)
      else if (s.approvedBy != "") add("approved", s.id, d, s.createdAt, 0L, s.seller, s.approvedBy)
    }
    for (r in l.returns) {
      if (l.saleById[r.saleId]?.cancelled == true) continue
      val d = posDate(r, clock)
      if (!inRange(d)) continue
      add("return", r.saleId, d, r.createdAt, r.amount, r.user, r.approvedBy)
    }
    for (a in l.audit) {
      if (a.type != "line_delete" && a.type != "no_sale") continue
      val d = posDate(a, clock)
      if (!inRange(d)) continue
      add(a.type, if (a.type == "line_delete") a.refId else "", d, a.createdAt, if (a.type == "line_delete") a.amount else 0L, a.user, a.approvedBy)
    }
    return out.sortedWith(compareBy({ it.date }, { it.createdAt }, { it.kind }, { it.docId }, { it.byOther }))
  }
}
