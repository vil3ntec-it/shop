package ir.vil3ntec.tohid.core.stock

import ir.vil3ntec.tohid.core.money.Rounding
import ir.vil3ntec.tohid.core.money.addExact
import ir.vil3ntec.tohid.core.money.mulExact

/*
 *  گزارشِ ضایعات و کسری — INV-17 (§۶.۵).
 *
 *  ورودی ردیف‌های دفترِ حرکتِ موجودی است که از دکان بیرون رفته یا بی خرید آمده‌اند،
 *  نه فروش، خرید یا برگشت به تأمین‌کننده:
 *   • ضایعات (`WASTE`): کالای خراب/شکسته — برگشتِ «ضایعات» و اصلاح با نوعِ `damage`؛
 *   • منقضی (`EXPIRED`) و گم‌شده (`LOST`): اصلاح با همان نوع؛
 *   • کسری و اضافهٔ شمارش (`SHORTAGE`/`SURPLUS`): اختلافِ انبارگردانی؛
 *   • اصلاحِ دستیِ بی نوع (`OTHER`): داده‌های پیش از INV-04 که فقط دلیلِ نوشته دارند.
 *
 *  ارزش = مقدار × بهای واحدِ همان کالا (همان بهایی که گزارشِ ارزشِ موجودی می‌گیرد)،
 *  با `Rounding.lineTotalMinor` و پلهٔ ۱ پول؛ بی Double. کالای بی‌بها ارزشِ صفر دارد و
 *  جدا شمرده می‌شود (`unpricedRows`) تا «زیانِ صفر» با «بها ثبت نشده» یکی نشود.
 *
 *  جمع‌ها:
 *   • `wasteMinor` = ضایعات + منقضی (کالایی که بود و دیگر فروختنی نیست)؛
 *   • `shrinkMinor` = کسریِ شمارش + گم‌شده + اصلاحِ دستیِ کاهشی − اضافهٔ شمارش − اصلاحِ دستیِ افزایشی
 *     (منفی ⇒ اضافه بیش از کسری بوده)؛
 *   • `netLossMinor` = هر دو با هم؛ `rateBp` همین نسبت به فروشِ خالصِ بازه، به ده‌هزارم.
 *
 *  منطقِ خالص (iOS هم)؛ آزمون: `ShrinkReportTest` (commonTest).
 */
object ShrinkReport {

  /** یادداشتِ ردیف‌های انبارگردانی با همین شروع می‌شود (`WarehouseEngine.stockCount`) — داده است، نه متنِ صفحه */
  const val COUNT_NOTE = "انبارگردانی"

  enum class Kind(val key: String) {
    WASTE("waste"),
    EXPIRED("expired"),
    LOST("lost"),
    SHORTAGE("shortage"),
    SURPLUS("surplus"),
    OTHER("other");

    /** ضایعات (و منقضی) جدا از کسری جمع می‌شوند */
    val isWaste: Boolean get() = this == WASTE || this == EXPIRED
  }

  /**
   *  نوعِ یک حرکتِ دفتر؛ `null` ⇒ جزوِ این گزارش نیست (فروش، خرید، برگشت از مشتری،
   *  برگشت به تأمین‌کننده، لغوِ فروش، یا مقدارِ صفر).
   *
   *  `type` همان `StockMovement.type`؛ `adjKind` نوعِ اصلاحِ ردیفِ انبارِ همان حرکت (INV-04) اگر هست.
   */
  fun classify(type: String, notes: String, adjKind: String?, qtyMilli: Long): Kind? {
    if (qtyMilli == 0L) return null
    return when (type) {
      "damage" -> Kind.WASTE
      "adjustment" -> when (adjKind) {
        "damage" -> Kind.WASTE
        "expired" -> Kind.EXPIRED
        "lost" -> Kind.LOST
        "count" -> if (qtyMilli < 0) Kind.SHORTAGE else Kind.SURPLUS
        "supplier_return" -> null
        else -> when {
          notes.trim().startsWith(COUNT_NOTE) -> if (qtyMilli < 0) Kind.SHORTAGE else Kind.SURPLUS
          else -> Kind.OTHER
        }
      }
      else -> null
    }
  }

  /** یک حرکت؛ `qtyMilli` با نشانه: منفی = از موجودی بیرون رفت */
  data class Move(
    val productId: String,
    val date: String,
    val qtyMilli: Long,
    val kind: Kind,
    val reason: String = "",
  )

  data class KindTotal(val kind: Kind, val rows: Int, val lossMinor: Long, val gainMinor: Long) {
    val netLossMinor: Long get() = lossMinor - gainMinor
  }

  data class ProductTotal(
    val productId: String,
    val rows: Int,
    /** مقدارِ بیرون‌رفته (مثبت) و آمده، به هزارمِ واحدِ همان کالا */
    val outMilli: Long,
    val inMilli: Long,
    val lossMinor: Long,
    val gainMinor: Long,
    val kinds: List<Kind>,
    val priced: Boolean,
  ) {
    val netLossMinor: Long get() = lossMinor - gainMinor
  }

  data class Reason(val kind: Kind, val text: String, val rows: Int, val lossMinor: Long)

  /** یک ردیفِ بازه با ارزشش: `lossMinor` مثبت = زیان، منفی = اضافه؛ بی‌بها ⇒ ۰ و `priced = false` */
  data class Line(val move: Move, val lossMinor: Long, val priced: Boolean)

  data class Report(
    /** ردیف‌های بازه به ترتیبِ تاریخ (برابر ⇒ ترتیبِ ورودی) — برای برگهٔ اکسل */
    val lines: List<Line>,
    val kinds: List<KindTotal>,
    val products: List<ProductTotal>,
    val reasons: List<Reason>,
    val wasteMinor: Long,
    val shrinkMinor: Long,
    val netLossMinor: Long,
    val rows: Int,
    val unpricedRows: Int,
    /** زیانِ خالص ÷ فروشِ خالص × ۱۰٬۰۰۰؛ `null` ⇒ فروش در بازه نبود */
    val rateBp: Long?,
  ) {
    val isEmpty: Boolean get() = rows == 0
  }

  private class Acc {
    var rows = 0
    var outMilli = 0L
    var inMilli = 0L
    var loss = 0L
    var gain = 0L
    var priced = true
    val kinds = LinkedHashSet<Kind>()
  }

  /**
   *  `from`/`to` تاریخِ `yyyy-MM-dd`، هر دو شامل. `costMinor` بهای واحدِ کالا به پول؛
   *  `null` یا صفر ⇒ بی‌بها. `salesMinor` فروشِ خالصِ همان بازه برای نرخ (اختیاری).
   */
  fun build(
    moves: List<Move>,
    from: String,
    to: String,
    costMinor: (String) -> Long?,
    salesMinor: Long? = null,
    topReasons: Int = 10,
  ): Report {
    val byKind = LinkedHashMap<Kind, Acc>()
    val byProduct = LinkedHashMap<String, Acc>()
    val byReason = LinkedHashMap<Pair<Kind, String>, Acc>()
    val lines = ArrayList<Line>()
    var rows = 0
    var unpriced = 0

    for (m in moves) {
      if (m.qtyMilli == 0L || m.date.isEmpty() || m.date < from || m.date > to) continue
      val cost = costMinor(m.productId)?.takeIf { it > 0 }
      val absQty = if (m.qtyMilli < 0) -m.qtyMilli else m.qtyMilli
      val value = if (cost == null) 0L else Rounding.lineTotalMinor(absQty, cost, 1)
      val out = m.qtyMilli < 0
      rows++
      if (cost == null) unpriced++
      lines += Line(m, if (out) value else -value, cost != null)

      fun add(a: Acc) {
        a.rows++
        if (out) {
          a.outMilli = addExact(a.outMilli, absQty); a.loss = addExact(a.loss, value)
        } else {
          a.inMilli = addExact(a.inMilli, absQty); a.gain = addExact(a.gain, value)
        }
        if (cost == null) a.priced = false
        a.kinds += m.kind
      }
      add(byKind.getOrPut(m.kind) { Acc() })
      add(byProduct.getOrPut(m.productId) { Acc() })
      if (out) add(byReason.getOrPut(m.kind to m.reason.trim()) { Acc() })
    }

    val kinds = Kind.entries.mapNotNull { k -> byKind[k]?.let { KindTotal(k, it.rows, it.loss, it.gain) } }
    val waste = kinds.filter { it.kind.isWaste }.fold(0L) { s, k -> addExact(s, k.netLossMinor) }
    val shrink = kinds.filter { !it.kind.isWaste }.fold(0L) { s, k -> addExact(s, k.netLossMinor) }
    val net = addExact(waste, shrink)

    val products = byProduct.map { (id, a) ->
      ProductTotal(id, a.rows, a.outMilli, a.inMilli, a.loss, a.gain, Kind.entries.filter { it in a.kinds }, a.priced)
    }.sortedWith(compareByDescending<ProductTotal> { it.netLossMinor }.thenByDescending { it.outMilli }.thenBy { it.productId })

    val reasons = byReason.map { (key, a) -> Reason(key.first, key.second, a.rows, a.loss) }
      .sortedWith(compareByDescending<Reason> { it.lossMinor }.thenByDescending { it.rows }.thenBy { it.text })
      .take(topReasons.coerceAtLeast(0))

    val rate = salesMinor?.takeIf { it > 0 }?.let { Rounding.roundHalfUp(mulExact(net, 10_000), it) }

    return Report(lines.sortedBy { it.move.date }, kinds, products, reasons, waste, shrink, net, rows, unpriced, rate)
  }
}
