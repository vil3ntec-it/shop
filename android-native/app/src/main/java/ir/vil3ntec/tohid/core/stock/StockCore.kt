package ir.vil3ntec.tohid.core.stock

import java.math.BigInteger

/**
 *  هستهٔ موجودی و بهای تمام‌شده — PRD-15، PRD-16، INV-02، INV-19 (بخشِ ۳.۶ و ۳.۴).
 *
 *  ── چرا این‌جا ───────────────────────────────────────────────────
 *  Kotlinِ خالص است (نه Android، نه Compose، نه دیسک)، پس برنامهٔ کامپیوتر
 *  هم همین فایل را می‌سازد. همتای JSاش `license/stock-core.js` است و هر دو
 *  یک فایلِ بردارِ مرجع را می‌گذرانند: `license/test-vectors/stock-v1.json`
 *  (`StockCoreVectorsTest` این‌جا، `server/test/stock-core.test.js` آن‌جا).
 *  ⛔ هر تغییرِ رفتار در یکی، بی همان تغییر در دیگری و در بردارها، ممنوع.
 *
 *  ⚠️ هنوز به هیچ صفحه‌ای وصل نیست. موتورِ امروز (`ShopStore.stock`،
 *  `SaleItem.purchasePrice`، `ExpiryEngine`) دست نخورده است.
 *
 *  ── واحدها ──────────────────────────────────────────────────────
 *  پول = Longِ واحدِ کوچک (AFN یک‌صدم). مقدار = Longِ هزارم. مثلِ JS، هر عدد
 *  باید در ±(2^53 − 1) بماند تا دو پیاده‌سازی عددبه‌عدد برابر باشند.
 *
 *  ── قاعده‌ها (همان سرِ `stock-core.js`) ─────────────────────────
 *  ۱) میانگینِ موزونِ متحرک برای هر کالا در هر انبار؛ «ارزش» و «مقدار» نگه
 *     داشته می‌شوند، نه «بهای واحد»، پس آزمونِ تطبیق دقیق است.
 *  ۲) کمبود (موجودیِ منفی) با آخرین بهای معلوم؛ دریافتِ بعدی با بهای صریح
 *     تفاوت را به‌صورتِ `costAdjustMinor` (سندِ تعدیلِ بها) می‌دهد.
 *  ۳) لات فقط برای انقضا و FEFO است. لاتِ "" یعنی «بی لات» و فقط آن منفی می‌شود.
 *  ۴) LOCAL: خروجِ بیش از موجود رد می‌شود. RECORDED: برای کمبود **هرگز** رد
 *     نمی‌شود؛ موجودی منفی و «وظیفهٔ بررسیِ موجودی» برمی‌گردد.
 *  ──────────────────────────────────────────────────────────────────
 */

/** لاتِ همراهِ سندِ ورودی؛ تاریخ‌ها `yyyy-MM-dd` یا null */
data class LotRef(val lotId: String, val productionDate: String? = null, val expiryDate: String? = null)

/**
 *  یک سندِ حرکتِ کالا (INV-02): نوع، کاربر، زمان و دلیل.
 *  [qtyMilli] برای نوع‌های یک‌سویه مثبت است و برای adjustment/count_adjust/production
 *  علامت دارد. [costMinor] بهای کلِ ردیف است، نه بهای واحد.
 */
data class StockDoc(
  val id: String,
  val type: String,
  val productId: String,
  val warehouseId: String,
  val userId: String,
  val at: Long,
  val qtyMilli: Long? = null,
  val reason: String? = null,
  val costMinor: Long? = null,
  val landedMinor: Long? = null,
  val valueMinor: Long? = null,
  val lot: LotRef? = null,
  /** لاتِ ترجیحی برای خروج (اسکنِ سری) */
  val lotId: String? = null,
  val refId: String? = null,
)

data class Lot(
  val lotId: String,
  val productionDate: String?,
  val expiryDate: String?,
  val qtyMilli: Long,
  val seq: Int,
)

data class Pick(val lotId: String, val qtyMilli: Long)
data class PickResult(val picks: List<Pick>, val shortMilli: Long)

/** بهای واحد به‌صورتِ کسر: [num] واحدِ کوچکِ پول برای [den] هزارمِ مقدار */
data class UnitCost(val num: Long, val den: Long)

data class StockTotals(
  val purchasesMinor: Long = 0,
  val landedMinor: Long = 0,
  val cogsMinor: Long = 0,
  val wasteMinor: Long = 0,
  val supplierReturnsMinor: Long = 0,
  val adjustmentsMinor: Long = 0,
  val transfersMinor: Long = 0,
  val productionMinor: Long = 0,
  val revaluationMinor: Long = 0,
  val costAdjustMinor: Long = 0,
) {
  operator fun plus(o: StockTotals) = StockTotals(
    StockCore.add(purchasesMinor, o.purchasesMinor), StockCore.add(landedMinor, o.landedMinor),
    StockCore.add(cogsMinor, o.cogsMinor), StockCore.add(wasteMinor, o.wasteMinor),
    StockCore.add(supplierReturnsMinor, o.supplierReturnsMinor), StockCore.add(adjustmentsMinor, o.adjustmentsMinor),
    StockCore.add(transfersMinor, o.transfersMinor), StockCore.add(productionMinor, o.productionMinor),
    StockCore.add(revaluationMinor, o.revaluationMinor), StockCore.add(costAdjustMinor, o.costAdjustMinor),
  )

  /** ارزشی که موجودی باید داشته باشد (آزمونِ تطبیقِ بخشِ ۳.۶) */
  val expectedValueMinor: Long
    get() = purchasesMinor + landedMinor - cogsMinor - wasteMinor - supplierReturnsMinor +
      adjustmentsMinor + transfersMinor + productionMinor + revaluationMinor - costAdjustMinor
}

/** یک کالا در یک انبار */
data class Position(
  val productId: String,
  val warehouseId: String,
  val qtyMilli: Long = 0,
  val valueMinor: Long = 0,
  val reservedMilli: Long = 0,
  val lastCost: UnitCost? = null,
  /** همهٔ لات‌ها، با صفرها؛ برای نمایش [fefoLots] */
  val lots: List<Lot> = emptyList(),
  val nextSeq: Int = 0,
  val totals: StockTotals = StockTotals(),
) {
  /** موجودیِ قابل‌فروش = موجودی − رزرو (بخشِ ۳.۶) */
  val sellableMilli: Long get() = qtyMilli - reservedMilli

  /** لات‌های غیرِ صفر به ترتیبِ FEFO */
  val fefoLots: List<Lot> get() = lots.filter { it.qtyMilli != 0L }.sortedWith(StockCore.LOT_ORDER)

  val reconciles: Boolean get() = totals.expectedValueMinor == valueMinor
}

data class ReviewTask(
  val productId: String,
  val warehouseId: String,
  val docId: String,
  /** negative_stock · expired_sold · unknown_cost */
  val reasons: List<String>,
  val shortMilli: Long,
  val qtyAfterMilli: Long,
  val kind: String = "stock_review",
)

data class StockResult(
  val ok: Boolean,
  val error: String? = null,
  val costMinor: Long = 0,
  val costAdjustMinor: Long = 0,
  val picks: List<Pick> = emptyList(),
  val shortMilli: Long = 0,
  val reviewTask: ReviewTask? = null,
  val availableMilli: Long? = null,
  val expiredMilli: Long? = null,
)

data class StockBook(val positions: Map<String, Position> = emptyMap())

data class Applied(val book: StockBook, val result: StockResult)

data class DocFilter(
  val productId: String? = null,
  val warehouseId: String? = null,
  val types: Set<String>? = null,
  val userId: String? = null,
  /** میلی‌ثانیه، شامل */
  val from: Long? = null,
  /** میلی‌ثانیه، شامل */
  val to: Long? = null,
)

enum class StockMode { LOCAL, RECORDED }

object StockCore {
  const val VECTORS_FORMAT = "stock-v1"
  const val MAX_SAFE = 9_007_199_254_740_991L

  private enum class CostRule { NONE, OPTIONAL, REQUIRED }

  private class Spec(
    val dir: Int = 0,
    val reason: Boolean = false,
    val cost: CostRule = CostRule.NONE,
    val reserve: Int = 0,
    val value: Boolean = false,
  )

  /** نوع‌های حرکت (بخشِ ۳.۶) — همان `TYPES`ِ JS */
  private val TYPES: Map<String, Spec> = mapOf(
    "purchase_receipt" to Spec(dir = 1, cost = CostRule.REQUIRED),
    "sale" to Spec(dir = -1),
    "customer_return" to Spec(dir = 1, cost = CostRule.OPTIONAL),
    "supplier_return" to Spec(dir = -1),
    "adjustment" to Spec(dir = 0, reason = true, cost = CostRule.OPTIONAL),
    "count_adjust" to Spec(dir = 0, reason = true, cost = CostRule.OPTIONAL),
    "damage" to Spec(dir = -1, reason = true),
    "expired" to Spec(dir = -1, reason = true),
    "transfer_out" to Spec(dir = -1),
    "transfer_in" to Spec(dir = 1, cost = CostRule.REQUIRED),
    "reserve" to Spec(reserve = 1),
    "release" to Spec(reserve = -1),
    "production" to Spec(dir = 0, cost = CostRule.OPTIONAL),
    "cost_adjust" to Spec(reason = true, value = true),
  )

  val TYPE_NAMES: Set<String> get() = TYPES.keys

  val EXPORT_HEADER = listOf(
    "id", "at", "type", "productId", "warehouseId", "qtyMilli", "amountMinor", "userId", "reason", "lotId", "refId",
  )

  // ---------------------------------------------------------------- عدد

  private fun safe(x: Long) = x in -MAX_SAFE..MAX_SAFE

  internal fun add(a: Long, b: Long): Long {
    val r = Math.addExact(a, b)
    if (!safe(r)) throw ArithmeticException("stock-core: overflow")
    return r
  }

  /** round(a × b / c) با نیمه به سمتِ +∞ (همان Math.round) = floorDiv(2ab + c, 2c)؛ c > 0 */
  fun mulDivRound(a: Long, b: Long, c: Long): Long {
    require(c > 0) { "stock-core: mulDivRound needs c > 0" }
    return try {
      val n = Math.addExact(Math.multiplyExact(Math.multiplyExact(a, b), 2L), c)
      Math.floorDiv(n, Math.multiplyExact(c, 2L))
    } catch (e: ArithmeticException) {
      val bc = BigInteger.valueOf(c)
      val n = BigInteger.valueOf(a).multiply(BigInteger.valueOf(b)).shiftLeft(1).add(bc)
      val qr = n.divideAndRemainder(bc.shiftLeft(1))
      val q = if (qr[1].signum() < 0) qr[0].subtract(BigInteger.ONE) else qr[0]
      if (q.bitLength() > 63) throw ArithmeticException("stock-core: overflow")
      q.toLong()
    }
  }

  // ---------------------------------------------------------------- تاریخ

  private val ISO = Regex("""^(\d{4})-(\d{2})-(\d{2})$""")

  fun isoDateOk(s: String): Boolean {
    val m = ISO.matchEntire(s) ?: return false
    val y = m.groupValues[1].toInt()
    val mo = m.groupValues[2].toInt()
    val d = m.groupValues[3].toInt()
    if (mo !in 1..12 || d < 1) return false
    val leap = (y % 4 == 0 && y % 100 != 0) || y % 400 == 0
    val max = intArrayOf(31, if (leap) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)[mo - 1]
    return d <= max
  }

  private fun normDate(s: String?): String? = if (s.isNullOrEmpty()) null else s

  // ---------------------------------------------------------------- سند

  /** نخستین ایرادِ سند، یا null — ترتیبِ بررسی همان `validate`ِ JS است */
  fun validate(doc: StockDoc): String? {
    if (doc.id.isBlank()) return "id_required"
    val spec = TYPES[doc.type] ?: return "unknown_type"
    if (doc.productId.isBlank()) return "product_required"
    if (doc.warehouseId.isBlank()) return "warehouse_required"
    if (doc.userId.isBlank()) return "user_required"
    if (doc.at <= 0 || !safe(doc.at)) return "time_required"
    if (spec.reason && doc.reason.isNullOrBlank()) return "reason_required"

    val q = doc.qtyMilli
    when {
      spec.value -> if (q != null && q != 0L) return "bad_qty"
      spec.dir == 0 && spec.reserve == 0 -> if (q == null || q == 0L || !safe(q)) return "bad_qty"
      else -> if (q == null || q <= 0 || !safe(q)) return "bad_qty"
    }

    if (spec.value && (doc.valueMinor == null || doc.valueMinor == 0L || !safe(doc.valueMinor))) return "bad_cost"
    doc.costMinor?.let { if (it < 0 || !safe(it)) return "bad_cost" }
    doc.landedMinor?.let { if (it < 0 || !safe(it)) return "bad_cost" }
    if (spec.cost == CostRule.REQUIRED && doc.costMinor == null) return "cost_required"

    doc.lot?.let { lot ->
      if (lot.lotId.isBlank()) return "bad_lot"
      val pd = normDate(lot.productionDate)
      val ed = normDate(lot.expiryDate)
      if (pd != null && !isoDateOk(pd)) return "bad_date"
      if (ed != null && !isoDateOk(ed)) return "bad_date"
      if (pd != null && ed != null && pd > ed) return "bad_date"
    }
    return null
  }

  /** اثرِ سند روی موجودیِ دست (هزارم، با علامت). رزرو و تعدیلِ بها صفرند. */
  fun signedQtyMilli(doc: StockDoc): Long {
    val spec = TYPES[doc.type] ?: return 0
    if (spec.reserve != 0 || spec.value) return 0
    val q = doc.qtyMilli ?: 0
    return if (spec.dir == 0) q else spec.dir * q
  }

  // ---------------------------------------------------------------- لات و FEFO

  private fun cmpNullLast(a: String?, b: String?): Int = when {
    a == b -> 0
    a == null -> 1
    b == null -> -1
    else -> a.compareTo(b).coerceIn(-1, 1)
  }

  /** انقضای زودتر، بعد تولیدِ زودتر، بعد نوبتِ دریافت، بعد شناسه */
  val LOT_ORDER: Comparator<Lot> = Comparator { x, y ->
    cmpNullLast(normDate(x.expiryDate), normDate(y.expiryDate)).takeIf { it != 0 }
      ?: cmpNullLast(normDate(x.productionDate), normDate(y.productionDate)).takeIf { it != 0 }
      ?: x.seq.compareTo(y.seq).takeIf { it != 0 }
      ?: x.lotId.compareTo(y.lotId)
  }

  /** منقضی یعنی تاریخِ انقضا پیش از امروز؛ لاتی که امروز منقضی می‌شود هنوز سالم است */
  fun isExpired(lot: Lot, today: String?): Boolean {
    val e = normDate(lot.expiryDate)
    return !today.isNullOrEmpty() && e != null && e < today
  }

  /** برداشت به ترتیبِ FEFO؛ لاتِ [preferLotId] اول، مگر منقضی باشد و منقضی مجاز نباشد */
  fun pickFefo(
    lots: List<Lot>,
    qtyMilli: Long,
    today: String?,
    includeExpired: Boolean,
    preferLotId: String? = null,
  ): PickResult {
    val order = lots.filter { it.qtyMilli > 0 && (includeExpired || !isExpired(it, today)) }
      .sortedWith(LOT_ORDER).toMutableList()
    if (preferLotId != null) {
      val i = order.indexOfFirst { it.lotId == preferLotId }
      if (i > 0) order.add(0, order.removeAt(i))
    }
    var left = qtyMilli
    val picks = ArrayList<Pick>()
    for (l in order) {
      if (left <= 0) break
      val take = minOf(l.qtyMilli, left)
      picks += Pick(l.lotId, take)
      left -= take
    }
    return PickResult(picks, left)
  }

  // ---------------------------------------------------------------- جایگاه

  fun keyOf(productId: String, warehouseId: String) = productId + '\u0000' + warehouseId

  /** نسخهٔ کاریِ یک جایگاه؛ در پایانِ یک سند به [Position]ِ تغییرناپذیر برمی‌گردد */
  private class Work(p: Position) {
    val productId = p.productId
    val warehouseId = p.warehouseId
    var qty = p.qtyMilli
    var value = p.valueMinor
    var reserved = p.reservedMilli
    var lastCost = p.lastCost
    val lots = p.lots.toMutableList()
    var nextSeq = p.nextSeq
    var t = p.totals

    fun lotIndex(lotId: String) = lots.indexOfFirst { it.lotId == lotId }

    fun addToLot(lotId: String, delta: Long, productionDate: String?, expiryDate: String?) {
      val i = lotIndex(lotId)
      if (i >= 0) {
        lots[i] = lots[i].copy(qtyMilli = lots[i].qtyMilli + delta)
      } else {
        lots += Lot(lotId, normDate(productionDate), normDate(expiryDate), delta, nextSeq++)
      }
    }

    fun expiredQty(today: String?) = lots.filter { it.qtyMilli > 0 && isExpired(it, today) }.sumOf { it.qtyMilli }

    fun freeze() = Position(productId, warehouseId, qty, value, reserved, lastCost, lots.toList(), nextSeq, t)
  }

  private fun lastCostOf(w: Work, q: Long): Long = w.lastCost?.let { mulDivRound(it.num, q, it.den) } ?: 0

  /** موجودی − منقضی − رزرو؛ ایرادِ رد یا null */
  private fun saleShortage(w: Work, q: Long, today: String?): StockResult? {
    val expired = w.expiredQty(today)
    val available = w.qty - expired - w.reserved
    if (q <= available) return null
    return if (expired > 0 && q <= w.qty - w.reserved) {
      StockResult(false, "expired_stock", availableMilli = maxOf(0, available), expiredMilli = expired)
    } else {
      StockResult(false, "insufficient_stock", availableMilli = maxOf(0, available))
    }
  }

  /**
   *  یک سند را روی دفتر می‌نشاند. دفترِ ورودی دست نمی‌خورد؛ اگر نتیجه ok
   *  نباشد، همان دفترِ ورودی برمی‌گردد. [today] `yyyy-MM-dd` برای انقضا.
   */
  fun apply(book: StockBook, doc: StockDoc, mode: StockMode = StockMode.LOCAL, today: String? = null): Applied {
    validate(doc)?.let { return Applied(book, StockResult(false, it)) }
    val spec = TYPES.getValue(doc.type)
    val recorded = mode == StockMode.RECORDED
    val key = keyOf(doc.productId, doc.warehouseId)
    val w = Work(book.positions[key] ?: Position(doc.productId, doc.warehouseId))

    val result: StockResult = when {
      spec.value -> {
        val v = doc.valueMinor!!
        w.value = add(w.value, v)
        w.t = w.t.copy(revaluationMinor = add(w.t.revaluationMinor, v))
        StockResult(true, costMinor = v)
      }
      spec.reserve == 1 -> {
        val q = doc.qtyMilli!!
        if (!recorded) saleShortage(w, q, today)?.let { return Applied(book, it) }
        w.reserved = add(w.reserved, q)
        StockResult(true)
      }
      spec.reserve == -1 -> {
        val q = doc.qtyMilli!!
        if (q > w.reserved) return Applied(book, StockResult(false, "release_exceeds_reserved"))
        w.reserved -= q
        StockResult(true)
      }
      else -> {
        val signed = signedQtyMilli(doc)
        if (signed < 0) outbound(w, doc, -signed, recorded, today) else inbound(w, doc, signed)
      }
    }
    if (!result.ok) return Applied(book, result)
    if (w.qty > 0) w.lastCost = UnitCost(w.value, w.qty)
    return Applied(StockBook(book.positions + (key to w.freeze())), result)
  }

  private fun outbound(w: Work, doc: StockDoc, q: Long, recorded: Boolean, today: String?): StockResult {
    val isSale = doc.type == "sale"
    if (!recorded) {
      if (isSale) {
        saleShortage(w, q, today)?.let { return it }
      } else if (q > maxOf(0, w.qty)) {
        return StockResult(false, "insufficient_stock", availableMilli = maxOf(0, w.qty))
      }
    }
    val first = pickFefo(w.lots, q, today, includeExpired = !isSale, preferLotId = doc.lotId)
    var picks: List<Pick> = first.picks
    var short = first.shortMilli
    var expiredSold = false
    if (isSale && short > 0) {
      //  ثبت‌شده: کالا واقعاً رفته؛ بعد از لات‌های سالم، منقضی‌ها
      val rest = w.lots.map { l -> l.copy(qtyMilli = l.qtyMilli - picks.filter { it.lotId == l.lotId }.sumOf { it.qtyMilli }) }
      val second = pickFefo(rest, short, today, includeExpired = true)
      expiredSold = second.picks.isNotEmpty()
      picks = picks + second.picks
      short = second.shortMilli
    }

    //  بها: بخشِ پوشیده به میانگین، کمبود به آخرین بهای معلوم
    val covered = minOf(q, maxOf(w.qty, 0))
    val shortage = q - covered
    var cost = if (covered == 0L) 0L else if (covered == w.qty) w.value else mulDivRound(w.value, covered, w.qty)
    if (shortage > 0) cost = add(cost, lastCostOf(w, shortage))
    val unknown = shortage > 0 && w.lastCost == null

    for (p in picks) w.addToLot(p.lotId, -p.qtyMilli, null, null)
    if (short > 0) w.addToLot("", -short, null, null)
    w.qty -= q
    w.value = add(w.value, -cost)
    w.t = when (doc.type) {
      "sale" -> w.t.copy(cogsMinor = add(w.t.cogsMinor, cost))
      "supplier_return" -> w.t.copy(supplierReturnsMinor = add(w.t.supplierReturnsMinor, cost))
      "damage", "expired" -> w.t.copy(wasteMinor = add(w.t.wasteMinor, cost))
      "transfer_out" -> w.t.copy(transfersMinor = add(w.t.transfersMinor, -cost))
      "adjustment", "count_adjust" -> w.t.copy(adjustmentsMinor = add(w.t.adjustmentsMinor, -cost))
      "production" -> w.t.copy(productionMinor = add(w.t.productionMinor, -cost))
      else -> w.t
    }

    val review = if (short > 0 || expiredSold) {
      ReviewTask(
        productId = w.productId,
        warehouseId = w.warehouseId,
        docId = doc.id,
        reasons = buildList {
          if (short > 0) add("negative_stock")
          if (expiredSold) add("expired_sold")
          if (unknown) add("unknown_cost")
        },
        shortMilli = short,
        qtyAfterMilli = w.qty,
      )
    } else null
    return StockResult(true, costMinor = cost, picks = picks, shortMilli = short, reviewTask = review)
  }

  private fun inbound(w: Work, doc: StockDoc, q: Long): StockResult {
    val explicit = doc.costMinor != null
    //  هزینهٔ جانبیِ خرید جزوِ بهاست (بخشِ ۳.۶)
    val cost = if (explicit) {
      add(doc.costMinor!!, if (doc.type == "purchase_receipt") doc.landedMinor ?: 0L else 0L)
    } else when {
      w.qty > 0 -> mulDivRound(w.value, q, w.qty)
      w.qty < 0 -> {
        val shortage = -w.qty
        val covered = minOf(q, shortage)
        val pc = if (covered == shortage) -w.value else mulDivRound(-w.value, covered, shortage)
        add(pc, if (q > covered) lastCostOf(w, q - covered) else 0L)
      }
      else -> lastCostOf(w, q)
    }
    var adj = 0L
    if (explicit && w.qty < 0) {
      val shortage = -w.qty
      val s = minOf(q, shortage)
      val trueCost = if (s == q) cost else mulDivRound(cost, s, q)
      val provisional = if (s == shortage) -w.value else mulDivRound(-w.value, s, shortage)
      adj = trueCost - provisional
    }

    //  لات: اول کمبودِ لاتِ "" پر می‌شود، باقی به لاتِ همین سند
    val lotId = doc.lot?.lotId ?: doc.lotId ?: ""
    var left = q
    val blank = w.lotIndex("")
    if (lotId != "" && blank >= 0 && w.lots[blank].qtyMilli < 0) {
      val fill = minOf(left, -w.lots[blank].qtyMilli)
      w.addToLot("", fill, null, null)
      left -= fill
    }
    if (left > 0) w.addToLot(lotId, left, doc.lot?.productionDate, doc.lot?.expiryDate)

    w.qty = add(w.qty, q)
    w.value = add(add(w.value, cost), -adj)
    w.t = when (doc.type) {
      "purchase_receipt" -> w.t.copy(
        purchasesMinor = add(w.t.purchasesMinor, doc.costMinor!!),
        landedMinor = add(w.t.landedMinor, doc.landedMinor ?: 0),
      )
      "customer_return" -> w.t.copy(cogsMinor = add(w.t.cogsMinor, -cost))
      "transfer_in" -> w.t.copy(transfersMinor = add(w.t.transfersMinor, cost))
      "adjustment", "count_adjust" -> w.t.copy(adjustmentsMinor = add(w.t.adjustmentsMinor, cost))
      "production" -> w.t.copy(productionMinor = add(w.t.productionMinor, cost))
      else -> w.t
    }
    w.t = w.t.copy(costAdjustMinor = add(w.t.costAdjustMinor, adj))
    if (explicit && w.qty <= 0) w.lastCost = UnitCost(cost, q)
    return StockResult(true, costMinor = cost, costAdjustMinor = adj)
  }

  /** همهٔ سندها به همان ترتیبِ داده‌شده (سرور: ترتیبِ HLC) */
  fun replay(docs: List<StockDoc>, mode: StockMode = StockMode.LOCAL, today: String? = null): Pair<StockBook, List<StockResult>> {
    var book = StockBook()
    val results = ArrayList<StockResult>(docs.size)
    for (d in docs) {
      val a = apply(book, d, mode, today)
      book = a.book
      results += a.result
    }
    return book to results
  }

  // ---------------------------------------------------------------- خواندن

  fun position(book: StockBook, productId: String, warehouseId: String): Position? = book.positions[keyOf(productId, warehouseId)]

  fun positions(book: StockBook): List<Position> =
    book.positions.values.sortedWith(compareBy<Position> { it.productId }.thenBy { it.warehouseId })

  fun bookTotals(book: StockBook): StockTotals = book.positions.values.fold(StockTotals()) { acc, p -> acc + p.totals }

  // ---------------------------------------------------------------- دفترِ حرکت (INV-02)

  fun onHandMilli(docs: List<StockDoc>, productId: String, warehouseId: String): Long =
    docs.filter { it.productId == productId && it.warehouseId == warehouseId }
      .fold(0L) { s, d -> add(s, signedQtyMilli(d)) }

  fun filterDocs(docs: List<StockDoc>, f: DocFilter): List<StockDoc> = docs.filter { d ->
    (f.productId == null || d.productId == f.productId) &&
      (f.warehouseId == null || d.warehouseId == f.warehouseId) &&
      (f.userId == null || d.userId == f.userId) &&
      (f.types == null || d.type in f.types) &&
      (f.from == null || d.at >= f.from) &&
      (f.to == null || d.at <= f.to)
  }

  /** ردیف‌های خروجی به ترتیبِ [EXPORT_HEADER]؛ خانهٔ خالی "" است */
  fun exportRows(docs: List<StockDoc>): List<List<Any>> = docs.map { d ->
    val amount: Any = when {
      TYPES[d.type]?.value == true -> d.valueMinor ?: ""
      d.costMinor != null -> d.costMinor + (if (d.type == "purchase_receipt") d.landedMinor ?: 0L else 0L)
      else -> ""
    }
    val lotId = d.lot?.lotId?.takeIf { it.isNotEmpty() } ?: d.lotId ?: ""
    listOf(
      d.id, d.at, d.type, d.productId, d.warehouseId, signedQtyMilli(d), amount,
      d.userId, d.reason ?: "", lotId, d.refId ?: "",
    )
  }
}
