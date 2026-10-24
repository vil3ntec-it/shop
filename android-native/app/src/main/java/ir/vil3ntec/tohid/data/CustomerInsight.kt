package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.report.ReportCore
import ir.vil3ntec.tohid.core.report.ReportDates
import ir.vil3ntec.tohid.core.report.ReportLedger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.math.floor

/**
 *  نگاه به مشتریان — سنِ بدهی (CRM-04) و تحلیلِ دفعات و مبلغِ خرید، RFM (CRM-14).
 *
 *  هیچ چیزی ذخیره نمی‌شود؛ همه از همان `transactions` و `sales`.
 *
 *  ── سنِ بدهی ─────────────────────────────────────────────────────────
 *  حساب **همان** `ReportCore.debts` است (RPT-06؛ همزادِ `license/report-core.js` با
 *  بردارهای مرجع): اولین‌وارد‌اولین‌پرداخت، مرزهای ۳۰/۶۰/۹۰ روز. این‌جا فقط دفترِ
 *  برنامه به ورودیِ آن تبدیل می‌شود و عدد به افغانی برمی‌گردد — حسابِ دوم ساخته نمی‌شود.
 */
object CustomerInsight {

  private val json = Json { encodeDefaults = true }

  /** همان مرزهای §۶.۷: ۰–۳۰، ۳۱–۶۰، ۶۱–۹۰، ۹۱+ */
  val BUCKETS: List<Long> = listOf(30L, 60L, 90L)
  /** سرستون‌ها عمداً «روز» ندارند: `SheetTable` ستونی را که «روز» دارد جمع نمی‌زند */
  val BUCKET_LABELS: List<String> = listOf("سنِ ۰ تا ۳۰", "سنِ ۳۱ تا ۶۰", "سنِ ۶۱ تا ۹۰", "سنِ بیش از ۹۰")

  data class AgeLine(
    val id: String,
    val name: String,
    val phone: String,
    /** مانده = باز − پیش‌پرداخت (مثبت = بدهکار) */
    val balance: Double,
    /** چهار خانه، به ترتیبِ `BUCKETS`؛ جمعشان = بازِ بردها */
    val buckets: List<Double>,
    /** پیش‌پرداختی که روی هیچ بردی ننشسته */
    val credit: Double,
    /** سنِ کهنه‌ترین بردِ باز (روز)؛ `null` اگر بردِ باز ندارد، ‎-1 اگر تاریخش خراب است */
    val oldestDays: Long?,
  )

  data class Aging(val asOf: String, val lines: List<AgeLine>, val totals: List<Double>, val balance: Double)

  private fun afn(minor: Long): Double = minor / 100.0

  /** سنِ بدهیِ همهٔ مشتریانی که مانده دارند تا `asOf` (yyyy-MM-dd)، بدهکارترین اول */
  fun aging(d: ShopData, asOf: String): Aging {
    require(ReportDates.epochDay(asOf) != null) { "asOf باید yyyy-MM-dd باشد" }
    val ledger = ReportLedger.parse(json.encodeToJsonElement(ShopData.serializer(), d).jsonObject)
    val r = ReportCore.debts(ledger, asOf, BUCKETS).customers
    val phones = d.debtors.associate { it.id to it.phone }
    return Aging(
      asOf = asOf,
      lines = r.rows.map { row ->
        AgeLine(
          id = row.id, name = row.name, phone = phones[row.id].orEmpty(),
          balance = afn(row.balance), buckets = row.buckets.map(::afn), credit = afn(row.credit),
          oldestDays = row.oldestDays,
        )
      },
      totals = r.totals.buckets.map(::afn),
      balance = afn(r.totals.balance),
    )
  }

  /** سنِ بدهیِ یک مشتری — `null` اگر مانده‌ای ندارد */
  fun agingOf(d: ShopData, debtorId: String, asOf: String): AgeLine? =
    aging(d.copy(transactions = d.transactions.filter { it.debtorId == debtorId }), asOf).lines.firstOrNull()

  fun agingSheet(d: ShopData, today: String): ReportCsv.Sheet {
    val a = aging(d, today)
    fun r(v: Double) = Math.round(v).toString()
    val rows = mutableListOf(listOf("نام", "شماره تماس", "مانده") + BUCKET_LABELS + listOf("پیش‌پرداخت", "کهنه‌ترین (روز)"))
    a.lines.forEach { l ->
      rows += listOf(l.name, l.phone, r(l.balance)) + l.buckets.map(::r) +
        listOf(if (l.credit > 0) r(l.credit) else "", l.oldestDays?.takeIf { it >= 0 }?.toString().orEmpty())
    }
    return ReportCsv.Sheet("سن-بدهی-مشتریان-$today", rows)
  }

  /* ------------------------------ RFM (CRM-14) ------------------------------ */

  enum class Segment(val label: String) {
    CHAMPION("وفادارِ برتر"),
    LOYAL("وفادار"),
    NEW("تازه"),
    LOST("از دست رفته"),
    AT_RISK("در خطرِ رفتن"),
    SLEEPING("کم‌پیدا"),
    REGULAR("معمولی"),
  }

  data class Rfm(
    val id: String,
    val name: String,
    val phone: String,
    val firstPurchase: String,
    val lastPurchase: String,
    /** روز از آخرین خرید تا `asOf` */
    val recencyDays: Long,
    /** شمارِ فاکتورهای ثبت‌شده در بازه */
    val frequency: Int,
    /** جمعِ مبلغِ نهایی منهای مرجوعی‌ها در بازه (افغانی) */
    val monetary: Double,
    /** امتیازِ ۱ تا ۵ — ۵ بهترین */
    val r: Int,
    val f: Int,
    val m: Int,
    val segment: Segment,
  ) {
    /** «۵-۴-۳» — عمداً عدد نیست تا جدول جمعش نزند */
    val code: String get() = "$r-$f-$m"
  }

  /**
   *  امتیازِ ۱ تا ۵ با صدکِ میانه: مقدارها از بد به خوب مرتب می‌شوند و هم‌مقدارها یک
   *  امتیاز می‌گیرند. یک مشتریِ تنها (یا همه برابر) ۳ می‌گیرد، نه ۱.
   */
  fun scores(values: List<Double>): List<Int> {
    val n = values.size
    if (n == 0) return emptyList()
    val order = values.indices.sortedBy { values[it] }
    val out = IntArray(n)
    var i = 0
    while (i < n) {
      var j = i
      while (j + 1 < n && values[order[j + 1]] == values[order[i]]) j++
      val p = ((i + j) / 2.0 + 0.5) / n
      val s = (1 + floor(5 * p).toInt()).coerceIn(1, 5)
      for (k in i..j) out[order[k]] = s
      i = j + 1
    }
    return out.toList()
  }

  /**
   *  بخش‌بندی از روی امتیاز و چند مرزِ مطلق (تا «هفتهٔ گذشته» هرگز «از دست رفته» نشود):
   *  برتر R≥4،F≥4،M≥4 · وفادار F≥4،R≥3 · تازه نخستین خرید ≤ ۳۰ روز · از دست رفته R≤2،F≤2 و
   *  بیش از ۹۰ روز · در خطر R≤2،F≥3 و بیش از ۳۰ روز · کم‌پیدا R≤2 و بیش از ۳۰ روز · بقیه معمولی.
   */
  fun segmentOf(r: Int, f: Int, m: Int, recencyDays: Long, firstDays: Long): Segment = when {
    r >= 4 && f >= 4 && m >= 4 -> Segment.CHAMPION
    f >= 4 && r >= 3 -> Segment.LOYAL
    firstDays <= 30 -> Segment.NEW
    r <= 2 && f <= 2 && recencyDays > 90 -> Segment.LOST
    r <= 2 && f >= 3 && recencyDays > 30 -> Segment.AT_RISK
    r <= 2 && recencyDays > 30 -> Segment.SLEEPING
    else -> Segment.REGULAR
  }

  /**
   *  RFM ِ مشتریانِ فعال در `windowDays` روزِ پیش از `asOf` (پیش‌فرض یک سال). فاکتورِ
   *  لغوشده و فاکتورِ بی مشتری بیرون؛ فاکتورِ مشتریِ ادغام‌شده به ماندگار می‌رسد.
   *  مشتریِ بی خرید در بازه ردیف ندارد. بهترین اول.
   */
  fun rfm(d: ShopData, asOf: String, windowDays: Int = 365): List<Rfm> {
    val end = ReportDates.epochDay(asOf) ?: throw IllegalArgumentException("asOf باید yyyy-MM-dd باشد")
    val start = end - windowDays.coerceAtLeast(1) + 1
    val names = d.debtors.associateBy { it.id }
    val returned = d.saleReturns.groupBy { it.saleId }.mapValues { (_, l) -> l.sumOf { it.amount } }

    data class Acc(var first: Long = Long.MAX_VALUE, var last: Long = Long.MIN_VALUE, var count: Int = 0, var money: Double = 0.0)
    val acc = LinkedHashMap<String, Acc>()
    for (s in d.sales) {
      if (s.status == "cancelled") continue
      val raw = s.debtorId?.takeIf { it.isNotBlank() } ?: continue
      val day = ReportDates.epochDay(s.date) ?: continue
      if (day < start || day > end) continue
      val id = CustomerCrm.resolve(names, raw)
      if (names[id]?.let(CustomerCrm::isActive) != true) continue
      val a = acc.getOrPut(id) { Acc() }
      a.first = minOf(a.first, day); a.last = maxOf(a.last, day); a.count++
      a.money += s.finalTotal - (returned[s.id] ?: 0.0)
    }
    if (acc.isEmpty()) return emptyList()
    val ids = acc.keys.toList()
    val rec = ids.map { (end - acc.getValue(it).last).toDouble() }
    val rS = scores(rec.map { -it })                      // کمتر روز = بهتر
    val fS = scores(ids.map { acc.getValue(it).count.toDouble() })
    val mS = scores(ids.map { acc.getValue(it).money })
    fun iso(day: Long) = ReportDates.addDays(asOf, (day - end).toInt())
    return ids.mapIndexed { i, id ->
      val a = acc.getValue(id)
      val c = names.getValue(id)
      Rfm(
        id = id, name = c.name, phone = c.phone,
        firstPurchase = iso(a.first), lastPurchase = iso(a.last),
        recencyDays = end - a.last, frequency = a.count, monetary = a.money,
        r = rS[i], f = fS[i], m = mS[i],
        segment = segmentOf(rS[i], fS[i], mS[i], end - a.last, end - a.first),
      )
    }.sortedWith(
      compareByDescending<Rfm> { it.r + it.f + it.m }.thenByDescending { it.monetary }.thenBy { it.id },
    )
  }

  /** شمارِ هر بخش — ترتیبِ `Segment` */
  fun segmentCounts(rows: List<Rfm>): List<Pair<Segment, Int>> =
    Segment.values().map { s -> s to rows.count { it.segment == s } }.filter { it.second > 0 }

  fun rfmSheet(d: ShopData, today: String, windowDays: Int = 365): ReportCsv.Sheet {
    val rows = mutableListOf(
      listOf("نام", "شماره تماس", "بخش", "امتیاز R-F-M", "روز از آخرین خرید", "تعداد خرید", "مبلغِ خرید", "نخستین خرید", "آخرین خرید"),
    )
    rfm(d, today, windowDays).forEach { x ->
      rows += listOf(
        x.name, x.phone, x.segment.label, x.code, x.recencyDays.toString(), x.frequency.toString(),
        Math.round(x.monetary).toString(), x.firstPurchase, x.lastPurchase,
      )
    }
    return ReportCsv.Sheet("تحلیل-خرید-مشتریان-$today", rows)
  }
}
