package ir.vil3ntec.tohid.bench

import ir.vil3ntec.tohid.core.audit.ChainEntry
import ir.vil3ntec.tohid.core.audit.HashChain
import ir.vil3ntec.tohid.core.money.Rounding
import ir.vil3ntec.tohid.core.report.ReportCore
import ir.vil3ntec.tohid.core.report.ReportLedger
import ir.vil3ntec.tohid.core.stock.StockCore
import ir.vil3ntec.tohid.core.stock.StockDoc
import ir.vil3ntec.tohid.core.text.TextNormalizer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import kotlin.math.roundToLong

/*
 *  ریزسنجشِ JVMِ موتورهای مشترک با دادهٔ مرجعِ بزرگ (۲۰k کالا، ۲۰۰k فاکتور، ۳۰۰k قلم).
 *
 *  JMH نیست — یک حلقهٔ ساده با گرم‌کردنِ JIT (WARM دور) و سپس RUNS دورِ سنجیده؛
 *  میانه و p95 گزارش می‌شود. هر نتیجه در `sink` جمع می‌شود تا JIT کار را حذف نکند.
 *  بودجه‌ها از بخشِ ۹.۲ (کامپیوترِ مرجع)؛ این ماشین مرجع نیست — عدد فقط خطِ پایهٔ نسبی است.
 */

private const val WARM = 3
private const val RUNS = 7
private const val REPLAY_CAP_MS = 60_000.0
private var sink = 0L

private data class Row(val name: String, val n: Int, val p50: Double, val p95: Double, val budgetMs: Double?, val note: String)

private fun <T> bench(name: String, budgetMs: Double? = null, note: String = "", warm: Int = WARM, runs: Int = RUNS, fn: () -> T): Row {
  repeat(warm) { sink += fn().hashCode() }
  val xs = DoubleArray(runs) {
    val t0 = System.nanoTime()
    sink += fn().hashCode()
    (System.nanoTime() - t0) / 1e6
  }.sorted()
  fun q(p: Double) = xs[(Math.ceil(p * xs.size).toInt() - 1).coerceIn(0, xs.size - 1)]
  return Row(name, runs, q(0.5), q(0.95), budgetMs, note).also { println(fmt(it)) }
}

//  هر سطر همان لحظه چاپ می‌شود تا اجرای بلندِ نیمه‌کاره عددهایش را از دست ندهد
private fun fmt(r: Row): String {
  val budget = r.budgetMs?.let { if (r.p95 <= it) "  ✅ ≤ ${it.toInt()}ms" else "  ❌ > ${it.toInt()}ms" } ?: ""
  return "  ${r.name.padEnd(34)} p50 ${"%9.1f".format(r.p50)}  p95 ${"%9.1f".format(r.p95)} ms$budget  ${r.note}"
}

private fun JsonObject.list(k: String) = this[k]?.jsonArray?.map { it.jsonObject } ?: emptyList()
private fun JsonObject.s(k: String) = (this[k] as? JsonPrimitive)?.content ?: ""
private fun JsonObject.d(k: String) = (this[k] as? JsonPrimitive)?.content?.toDoubleOrNull() ?: 0.0

fun main(args: Array<String>) {
  val path = args.firstOrNull { !it.startsWith("--") } ?: error("مسیرِ دفترِ مرجع لازم است (node bench/kotlin/ledger.mjs <out.json>)")
  val jsonOut = args.indexOf("--json").takeIf { it >= 0 }?.let { args.getOrNull(it + 1) }
  val rows = ArrayList<Row>()
  //  متنِ ۱۲۵ مگابایتی فقط یک بار خوانده و پس از تجزیه رها می‌شود (حافظهٔ ماشینِ سنجش کم است)
  val raw = run {
    val text = File(path).readText()
    val chars = text.length
    var parsed: JsonObject? = null
    rows += bench("parse JSON (kotlinx)", note = "${chars / 1_000_000}M نویسه", warm = 0, runs = 1) { Json.parseToJsonElement(text).jsonObject.also { parsed = it } }
    parsed!!
  }
  rows += bench("ReportLedger.parse", warm = 1, runs = 2) { ReportLedger.parse(raw) }
  val ledger = ReportLedger.parse(raw)
  val lastDay = ledger.sales.maxOf { it.date }
  val monthFrom = lastDay.substring(0, 8) + "01"
  val itemsInMonth = ledger.items.count { (ledger.saleById[it.saleId]?.date ?: "") >= monthFrom }

  // گزارشِ ماه (بودجهٔ ۹.۲: ≤ ۲s در پس‌زمینه با ۳۰۰ هزار ردیف) — و کلِ سال
  rows += bench("ReportCore.sales (ماه)", 2000.0, "$itemsInMonth قلمِ ماه از ${ledger.items.size}") { ReportCore.sales(ledger, monthFrom, lastDay) }
  rows += bench("ReportCore.sales (سال)", 2000.0, "${ledger.items.size} قلم") { ReportCore.sales(ledger) }
  rows += bench("ReportCore.sales (سال، MAVG)", 2000.0, "میانگینِ موزون") { ReportCore.sales(ledger, costing = ReportCore.Costing.MAVG) }
  rows += bench("ReportCore.debts", 2000.0, "${ledger.transactions.size} تراکنش") { ReportCore.debts(ledger, lastDay) }
  rows += bench("ReportCore.today", 300.0) { ReportCore.today(ledger, lastDay) }

  // دفترِ موجودی: هر ورودیِ انبار یک «دریافت» و هر قلمِ فروش یک «فروش»
  val docs = ArrayList<StockDoc>()
  raw.list("warehouseEntries").forEach { e ->
    val q = (e.d("units") * 1000).roundToLong()
    docs += StockDoc(e.s("id"), "purchase_receipt", e.s("productId"), "main", "bench", e.s("createdAt").toLong().coerceAtLeast(1),
      qtyMilli = q, costMinor = (e.d("price") * 100).roundToLong() * q / 1000)
  }
  val saleAt = raw.list("sales").associate { it.s("id") to it.s("createdAt").toLong() }
  val cancelled = raw.list("sales").filter { it.s("status") == "cancelled" }.map { it.s("id") }.toHashSet()
  raw.list("saleItems").forEach { i ->
    if (i.s("saleId") in cancelled) return@forEach
    docs += StockDoc(i.s("id"), "sale", i.s("productId"), "main", "bench", saleAt[i.s("saleId")] ?: 1L,
      qtyMilli = (i.d("quantity") * 1000).roundToLong())
  }
  docs.sortBy { it.at }
  //  بازپخشِ دفتر با پیشوندهای ×۴: اگر یک دور از سقف بگذرد، بقیه اجرا نمی‌شود و
  //  زمانِ کل از رشدِ دیده‌شده برآورد و «اجرا نشد» نوشته می‌شود — نه عددی ساختگی
  repeat(2) { StockCore.replay(docs.subList(0, minOf(5_000, docs.size))) }
  var book = StockCore.replay(docs.subList(0, minOf(5_000, docs.size))).first
  var size = 5_000
  var prevMs = 0.0
  var prevN = 0
  while (true) {
    val n = minOf(size, docs.size)
    val t0 = System.nanoTime()
    book = StockCore.replay(docs.subList(0, n)).first
    val ms = (System.nanoTime() - t0) / 1e6
    val growth = if (prevN > 0) " (×${"%.1f".format(ms / prevMs)} برای ×${"%.1f".format(n.toDouble() / prevN)} سند)" else ""
    rows += Row("StockCore.replay ×$n سند", 1, ms, ms, null, "${book.positions.size} کالا$growth").also { println(fmt(it)) }
    if (n == docs.size) break
    if (ms > REPLAY_CAP_MS) {
      val k = if (prevN > 0) Math.log(ms / prevMs) / Math.log(n.toDouble() / prevN) else 2.0
      val projected = ms * Math.pow(docs.size.toDouble() / n, k) / 1000
      rows += Row("StockCore.replay ×${docs.size} سند", 0, Double.NaN, Double.NaN, null,
        "اجرا نشد: برآورد ≈ ${"%.0f".format(projected)}s (رشدِ توانِ ${"%.1f".format(k)})").also { println(fmt(it)) }
      break
    }
    prevMs = ms; prevN = n
    size *= 4
  }
  rows += bench("StockCore.apply (یک فروش)", 100.0, "روی دفترِ ${book.positions.size} کالا") {
    StockCore.apply(book, StockDoc("x-sale", "sale", docs[0].productId, "main", "bench", Long.MAX_VALUE / 4, qtyMilli = 1000)).result
  }

  // جست‌وجوی کالا: هر حرف از «برنج ممتاز» روی ۲۰ هزار نام (بودجه: ≤ ۱۵۰ms برای هر حرف)
  val names = ledger.products.values.map { it.name }
  val query = "برنج ممتاز"
  for (k in listOf(1, 4, query.length)) {
    val q = query.take(k)
    rows += bench("TextNormalizer.matches «$q»", 150.0, "${names.size} نام") { names.count { TextNormalizer.matches(it, q) } }
  }
  rows += bench("TextNormalizer.searchKey ×نام‌ها", note = "نمایهٔ جست‌وجو") { names.sumOf { TextNormalizer.searchKey(it).length } }

  rows += bench("Rounding.lineTotalMinor ×۳۰۰k", note = "همهٔ قلم‌ها") {
    var s = 0L
    for (it in ledger.items) s += Rounding.lineTotalMinor(it.qty, it.unitCost, 100)
    s
  }

  val chain = ArrayList<ChainEntry>()
  rows += bench("HashChain.append ×۱۰k", runs = 3) {
    chain.clear()
    for (i in 0 until 10_000) chain += HashChain.append(chain, buildJsonObject { put("i", i); put("t", "sale"); put("amount", i * 100L) })
    chain.size
  }
  rows += bench("HashChain.verify ×۱۰k", runs = 3) { HashChain.verify(chain).ok }

  val rt = Runtime.getRuntime()
  System.gc()
  val heapMb = (rt.totalMemory() - rt.freeMemory()) / 1_048_576

  println("\n── موتورهای Kotlin (JVM ${System.getProperty("java.version")}، ${rt.availableProcessors()} هسته) — ${ledger.products.size} کالا، ${ledger.sales.size} فاکتور، ${ledger.items.size} قلم")
  for (r in rows) println(fmt(r))
  println("  حافظهٔ heap پس از GC: ${heapMb}MB   (sink=$sink)")

  if (jsonOut != null) {
    val out = buildString {
      append("{\"java\":\"").append(System.getProperty("java.version")).append("\",\"heapMB\":").append(heapMb).append(",\"rows\":[")
      rows.forEachIndexed { i, r ->
        if (i > 0) append(',')
        append("{\"name\":").append(Json.encodeToString(JsonPrimitive.serializer(), JsonPrimitive(r.name)))
          .append(",\"p50\":").append(if (r.p50.isNaN()) "null" else "%.1f".format(java.util.Locale.ROOT, r.p50))
          .append(",\"p95\":").append(if (r.p95.isNaN()) "null" else "%.1f".format(java.util.Locale.ROOT, r.p95))
          .append(",\"note\":").append(Json.encodeToString(JsonPrimitive.serializer(), JsonPrimitive(r.note)))
          .append(",\"budgetMs\":").append(r.budgetMs ?: "null").append('}')
      }
      append("]}")
    }
    File(jsonOut).writeText(out)
  }
}
