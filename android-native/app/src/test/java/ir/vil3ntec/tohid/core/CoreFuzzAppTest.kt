package ir.vil3ntec.tohid.core

import ir.vil3ntec.tohid.core.report.ReportClock
import ir.vil3ntec.tohid.core.report.ReportCore
import ir.vil3ntec.tohid.core.report.ReportLedger
import ir.vil3ntec.tohid.core.report.ReportTables
import ir.vil3ntec.tohid.core.stock.Lot
import ir.vil3ntec.tohid.core.stock.LotRef
import ir.vil3ntec.tohid.core.stock.StockBook
import ir.vil3ntec.tohid.core.stock.StockCore
import ir.vil3ntec.tohid.core.stock.StockDoc
import ir.vil3ntec.tohid.core.stock.StockMode
import ir.vil3ntec.tohid.core.stock.StockResult
import ir.vil3ntec.tohid.core.money.Money
import ir.vil3ntec.tohid.data.DebtBook
import ir.vil3ntec.tohid.data.ShopData
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.math.BigDecimal

/**
 *  فازِ تفاضلی — دوندهٔ Kotlin ِ هسته‌هایی که هنوز در `android-native` اند: موجودی (`StockCore`)،
 *  گزارش (`ReportCore`) و دفترِ قرض (`DebtBook`). همان پیکرهٔ `core/test-vectors/core-fuzz-v1.json`
 *  که `server/scripts/core-fuzz.js` با هسته‌های JS ساخته (`out` = خروجیِ JS) و `CoreFuzzTest` ِ core
 *  بقیه‌اش را می‌دواند. روی اندروید (`app/`) و کامپیوتر (`desktop/`) اجرا می‌شود.
 *  اجرای ژرف: CORE_FUZZ_CORPUS=<پیکرهٔ بزرگ>.
 */
class CoreFuzzAppTest {

  private val corpus: JsonObject by lazy {
    val deep = System.getenv("CORE_FUZZ_CORPUS")?.takeIf { it.isNotBlank() }?.let(::File)
    val file = deep ?: generateSequence(File(".").absoluteFile) { it.parentFile }
      .map { File(it, "core/test-vectors/core-fuzz-v1.json") }.firstOrNull { it.isFile }
      ?: error("core/test-vectors/core-fuzz-v1.json پیدا نشد (cwd=${File(".").absoluteFile})")
    Json.parseToJsonElement(file.readText()).jsonObject
  }

  private val json = Json { encodeDefaults = true }
  private val shop = Json { ignoreUnknownKeys = true; isLenient = true }

  @Test
  fun `فاز — هر موردِ موجودی، گزارش و دفترِ قرض همان خروجیِ JS`() {
    val cases = corpus.getValue("cases").jsonArray.map { it.jsonObject }
    val mine = cases.filter { it.fn().substringBefore('.') in FAMILIES }
    assertTrue("موردها ${mine.size} از ${cases.size}", mine.size >= cases.size / 4)
    val dump = System.getenv("CORE_FUZZ_DUMP")?.takeIf { it.isNotBlank() }?.let { File(it).bufferedWriter() }  // برای کمینه‌سازی: خروجیِ Kotlin ِ هر تفاوت
    val failures = mine.mapNotNull { c ->
      val want = c.getValue("out")
      val got = try { run(c.fn(), c.getValue("in").jsonObject) } catch (e: Exception) { THROWS }
      if (dump != null && !same(want, got)) dump.write("{\"id\":${c["id"]},\"got\":$got}\n")
      if (same(want, got)) null else c.fn() to "#${c["id"]} ${c.fn()} ${c["in"].toString().take(300)}\n  JS:     $want\n  Kotlin: $got"
    }
    dump?.close()
    val byFn = failures.groupBy({ it.first }, { it.second })
    assertTrue("${failures.size} تفاوت ${byFn.mapValues { it.value.size }}\n" + byFn.values.flatMap { it.take(3) }.joinToString("\n"), failures.isEmpty())
  }

  private fun run(fn: String, x: JsonObject): JsonElement = when (fn) {
    "stock.mulDivRound" -> JsonPrimitive(StockCore.mulDivRound(x.l("a"), x.l("b"), x.l("c")))
    "stock.ledger" -> {
      val today = x.str("today")
      var book = StockBook()
      val results = x.getValue("steps").jsonArray.map { s ->
        val step = s.jsonObject
        val mode = if (step.str("mode") == "recorded") StockMode.RECORDED else StockMode.LOCAL
        val a = StockCore.apply(book, doc(step.getValue("doc").jsonObject), mode, today)
        book = a.book
        result(a.result)
      }
      val positions = StockCore.positions(book).map { p ->
        obj(
          "productId" to s(p.productId), "warehouseId" to s(p.warehouseId), "qtyMilli" to n(p.qtyMilli), "valueMinor" to n(p.valueMinor),
          "reservedMilli" to n(p.reservedMilli), "sellableMilli" to n(p.sellableMilli),
          "lastCost" to (p.lastCost?.let { obj("num" to n(it.num), "den" to n(it.den)) } ?: JsonNull),
          "lots" to JsonArray(p.fefoLots.map { obj("lotId" to s(it.lotId), "productionDate" to sn(it.productionDate), "expiryDate" to sn(it.expiryDate), "qtyMilli" to n(it.qtyMilli)) }),
          "totals" to totals(p.totals), "reconciles" to JsonPrimitive(p.reconciles),
        )
      }
      obj("results" to JsonArray(results), "positions" to JsonArray(positions), "totals" to totals(StockCore.bookTotals(book)))
    }
    "stock.fefo" -> {
      val lots = x.getValue("lots").jsonArray.map { it.jsonObject }.map {
        Lot(it.str("lotId")!!, it.str("productionDate"), it.str("expiryDate"), it.l("qtyMilli"), it.l("seq").toInt())
      }
      val got = StockCore.pickFefo(
        lots, x.l("qtyMilli"), x.str("today"),
        includeExpired = x.getValue("includeExpired").jsonPrimitive.booleanOrNull!!, preferLotId = x.str("preferLotId"),
      )
      obj("picks" to picks(got.picks.map { it.lotId to it.qtyMilli }), "shortMilli" to n(got.shortMilli))
    }
    "stock.view" -> {
      val docs = x.getValue("docs").jsonArray.map { doc(it.jsonObject) }
      obj(
        "signed" to JsonArray(docs.map { n(StockCore.signedQtyMilli(it)) }),
        "onHand" to n(StockCore.onHandMilli(docs, x.str("product")!!, x.str("warehouse")!!)),
        "exportRows" to JsonArray(StockCore.exportRows(docs).map { row -> JsonArray(row.map { cell(it) }) }),
      )
    }
    "report.run" -> report(x)
    "debt.book" -> debt(x)
    else -> error("تابعِ ناشناخته: $fn")
  }

  // ── موجودی ───────────────────────────────────────────

  /** همان نگاشتِ `StockCoreVectorsTest` */
  private fun doc(o: JsonObject): StockDoc = StockDoc(
    id = o.str("id") ?: "", type = o.str("type") ?: "", productId = o.str("productId") ?: "", warehouseId = o.str("warehouseId") ?: "",
    userId = o.str("userId") ?: "", at = o.lOrNull("at") ?: 0, qtyMilli = o.lOrNull("qtyMilli"), reason = o.str("reason"),
    costMinor = o.lOrNull("costMinor"), landedMinor = o.lOrNull("landedMinor"), valueMinor = o.lOrNull("valueMinor"),
    lot = (o["lot"] as? JsonObject)?.let { LotRef(it.str("lotId") ?: "", it.str("productionDate"), it.str("expiryDate")) },
    lotId = o.str("lotId"), refId = o.str("refId"),
  )

  private fun result(r: StockResult): JsonElement = obj(
    "ok" to JsonPrimitive(r.ok), "error" to sn(r.error), "costMinor" to n(r.costMinor), "costAdjustMinor" to n(r.costAdjustMinor),
    "picks" to picks(r.picks.map { it.lotId to it.qtyMilli }), "shortMilli" to n(r.shortMilli),
    "availableMilli" to (r.availableMilli?.let(::n) ?: JsonNull), "expiredMilli" to (r.expiredMilli?.let(::n) ?: JsonNull),
    "reviewTask" to (
      r.reviewTask?.let {
        obj(
          "kind" to s(it.kind), "productId" to s(it.productId), "warehouseId" to s(it.warehouseId), "docId" to s(it.docId),
          "reasons" to JsonArray(it.reasons.map(::s)), "shortMilli" to n(it.shortMilli), "qtyAfterMilli" to n(it.qtyAfterMilli),
        )
      } ?: JsonNull
      ),
  )

  private fun totals(t: ir.vil3ntec.tohid.core.stock.StockTotals) = obj(
    "purchasesMinor" to n(t.purchasesMinor), "landedMinor" to n(t.landedMinor), "cogsMinor" to n(t.cogsMinor), "wasteMinor" to n(t.wasteMinor),
    "supplierReturnsMinor" to n(t.supplierReturnsMinor), "adjustmentsMinor" to n(t.adjustmentsMinor), "transfersMinor" to n(t.transfersMinor),
    "productionMinor" to n(t.productionMinor), "revaluationMinor" to n(t.revaluationMinor), "costAdjustMinor" to n(t.costAdjustMinor),
  )

  private fun picks(list: List<Pair<String, Long>>) = JsonArray(list.map { (id, q) -> obj("lotId" to s(id), "qtyMilli" to n(q)) })

  private fun cell(v: Any?): JsonElement = when (v) {
    null -> JsonNull
    is String -> s(v)
    is Long -> n(v)
    is Int -> n(v.toLong())
    else -> error("خانهٔ ناشناخته: $v")
  }

  // ── گزارش (همان اجرای `ReportCoreVectorsTest`) ───────

  private fun report(x: JsonObject): JsonElement {
    val l = ReportLedger.parse(x.getValue("ledger").jsonObject)
    val o = x["opts"] as? JsonObject ?: JsonObject(emptyMap())
    val clock = (o["clock"] as? JsonObject)?.let { ReportClock.fixed(it.getValue("offsetMinutes").jsonPrimitive.int, it.getValue("dayStartMinute").jsonPrimitive.int) }
    val costing = if (o.str("costing") == "mavg") ReportCore.Costing.MAVG else ReportCore.Costing.LINE
    val approved = (o["costApproved"] as? JsonPrimitive)?.contentOrNull == "true"
    fun today() = ReportCore.today(l, o.str("date"), clock, costing, (o["now"] as? JsonPrimitive)?.longOrNull ?: 0L, approved)
    fun sales() = ReportCore.sales(l, o.str("from"), o.str("to"), clock, costing, approved)
    fun debts() = ReportCore.debts(l, o.str("asOf")!!, (o["buckets"] as? JsonArray)?.map { it.jsonPrimitive.long } ?: emptyList(), clock)
    fun fraud() = ReportCore.fraud(l, o.str("from"), o.str("to"), clock)
    return when (val fn = x.str("fn")) {
      "today" -> json.encodeToJsonElement(ReportCore.TodayReport.serializer(), today())
      "sales" -> json.encodeToJsonElement(ReportCore.SalesReport.serializer(), sales())
      "debts" -> json.encodeToJsonElement(ReportCore.DebtReport.serializer(), debts())
      "fraud" -> json.encodeToJsonElement(ReportCore.FraudReport.serializer(), fraud())
      "tables" -> {
        val tables = when (val of = x.str("of")) {
          "sales" -> ReportTables.sales(sales())
          "today" -> ReportTables.today(today())
          "debts" -> ReportTables.debts(debts())
          "fraud" -> ReportTables.fraud(fraud())
          else -> error("نوعِ جدولِ ناشناخته: $of")
        }
        JsonArray(tables.map { json.encodeToJsonElement(ReportTables.Plain.serializer(), ReportTables.plain(it)) })
      }
      else -> error("تابعِ ناشناخته: $fn")
    }
  }

  // ── دفترِ قرض (همان شکلِ `debt-book-v1.json`) ───────

  private fun debt(x: JsonObject): JsonElement {
    val d = shop.decodeFromJsonElement(ShopData.serializer(), x.getValue("ledger"))
    val id = x.str("debtorId")!!
    val b = DebtBook.book(d, id, x.str("today")!!)
    val extra = x.lOrNull("extraMinor")
    val cc = extra?.let { DebtBook.creditCheck(d, id, Money(it, DebtBook.CURRENCY)) }
    val book = b?.let { bk ->
      obj(
        "balanceMinor" to n(bk.balance.minor), "overdueMinor" to n(bk.overdueAmount.minor),
        "overdue" to JsonArray(bk.overdue.map { s(it.tx.id) }), "nextDue" to sn(bk.nextDue?.tx?.id),
        "credit" to (bk.credit?.let { obj("limitMinor" to n(it.limit.minor), "balanceMinor" to n(it.balance.minor), "headroomMinor" to n(it.headroom.minor), "over" to JsonPrimitive(it.over)) } ?: JsonNull),
        "entries" to JsonArray(
          bk.entries.map { e ->
            obj(
              "id" to s(e.tx.id), "row" to n(e.row.toLong()), "give" to JsonPrimitive(e.give), "amountMinor" to n(e.amount.minor),
              "balanceBeforeMinor" to n(e.balanceBefore.minor), "balanceAfterMinor" to n(e.balanceAfter.minor), "openMinor" to n(e.open.minor),
              "overdue" to JsonPrimitive(e.overdue), "paid" to JsonPrimitive(e.paid), "due" to sn(e.dueDate), "sale" to sn(e.sale?.id),
              "invoiceLabel" to sn(e.invoiceLabel),
              "allocations" to JsonArray(e.allocations.map { JsonArray(listOf(s(it.txId), n(it.amount.minor))) }),
              "lines" to JsonArray(
                e.lines.map {
                  obj(
                    "name" to s(it.name), "quantity" to JsonPrimitive(it.quantity), "unit" to s(it.unit), "unitPriceMinor" to n(it.unitPrice.minor),
                    "totalMinor" to n(it.total.minor), "returned" to JsonPrimitive(it.returned),
                  )
                },
              ),
            )
          },
        ),
      )
    } ?: JsonNull
    return obj(
      "book" to book,
      "creditCheck" to (cc?.let { obj("limitMinor" to n(it.limit.minor), "balanceMinor" to n(it.balance.minor), "afterMinor" to n(it.after.minor), "over" to JsonPrimitive(it.over), "excessMinor" to n(it.excess.minor)) } ?: JsonNull),
    )
  }

  // ── کمک‌ها ───────────────────────────────────────────

  private fun JsonObject.fn() = getValue("fn").jsonPrimitive.content
  private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content
  private fun JsonObject.l(k: String): Long = getValue(k).jsonPrimitive.content.toLong()
  private fun JsonObject.lOrNull(k: String): Long? = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.longOrNull
  private fun n(v: Long) = JsonPrimitive(v)
  private fun s(v: String) = JsonPrimitive(v)
  private fun sn(v: String?): JsonElement = v?.let(::s) ?: JsonNull
  private fun obj(vararg kv: Pair<String, JsonElement>) = JsonObject(linkedMapOf(*kv))

  companion object {
    private val FAMILIES = setOf("stock", "report", "debt")
    private val THROWS = JsonObject(mapOf("throws" to JsonPrimitive(true)))
    private val SAFE = BigDecimal("9007199254740991")

    /** همان برابریِ `CoreFuzzTest` ِ core: عدد با مقدار؛ بیرون از ±۲^۵۳ با گردِ Double ِ JS */
    fun same(a: JsonElement, b: JsonElement): Boolean = when {
      a is JsonObject && b is JsonObject -> a.keys == b.keys && a.keys.all { same(a.getValue(it), b.getValue(it)) }
      a is JsonArray && b is JsonArray -> a.size == b.size && a.indices.all { same(a[it], b[it]) }
      a is JsonNull || b is JsonNull -> a is JsonNull && b is JsonNull
      a is JsonPrimitive && b is JsonPrimitive && !a.isString && !b.isString && a.booleanOrNull == null && b.booleanOrNull == null -> {
        val x = a.content.toBigDecimalOrNull()
        val y = b.content.toBigDecimalOrNull()
        if (x == null || y == null) a.content == b.content
        else if (x.abs() <= SAFE && y.abs() <= SAFE) x.compareTo(y) == 0 else x.toDouble() == y.toDouble()
      }
      a is JsonPrimitive && b is JsonPrimitive -> a.isString == b.isString && a.content == b.content
      else -> false
    }
  }
}
