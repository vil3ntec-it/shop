package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.report.CostVectorsTest
import ir.vil3ntec.tohid.core.report.ReportCore
import ir.vil3ntec.tohid.core.report.ReportCoreVectorsTest
import ir.vil3ntec.tohid.core.report.ReportLedger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 *  مرجعِ رفتارِ امروز: هستهٔ گزارش در برابرِ موتورهای واقعیِ برنامه، روی همان دفترِ
 *  آزمون‌های مرجع. هیچ موتوری عوض نشده؛ این آزمون فقط برابری‌ها را می‌پاید و
 *  تفاوت‌های عمدی را با عدد ثبت می‌کند (شرحش در PR).
 */
class ReportCoreParityTest {
  private val raw = Json.parseToJsonElement(ReportCoreVectorsTest.vectorFile().readText())
    .jsonObject.getValue("ledgers").jsonObject.getValue("kabul").jsonObject
  private val d: ShopData = Json { ignoreUnknownKeys = true }.decodeFromJsonElement(ShopData.serializer(), raw)
  private val l = ReportLedger.parse(raw)
  private fun minor(x: Double) = Math.round(x * 100)

  @Test
  fun `بی ساعت و با بهای ثبت‌شده همان ReportEngine_sales است`() {
    for ((from, to) in listOf("0000-01-01" to "9999-12-31", "2026-10-14" to "2026-10-14", "2026-10-07" to "2026-10-13")) {
      val old = ReportEngine.sales(d, from, to)
      val t = ReportCore.sales(l, from, to).totals
      assertEquals(from, old.count, t.invoices)
      assertEquals(from, minor(old.gross), t.gross)
      assertEquals(from, minor(old.discount), t.discount)
      assertEquals(from, minor(old.net), t.sales) // «net»ِ امروز = فروش پیش از برگشتی
      assertEquals(from, minor(old.returnAmount), t.returns)
      assertEquals(from, minor(old.grossProfit), t.grossProfit)
    }
  }

  @Test
  fun `ماندهٔ قرض‌دار همان ReportEngine_debtors و فروشِ هر کاربر همان bySeller`() {
    val core = ReportCore.debts(l, "9999-12-31").customers.rows.associateBy { it.id }
    ReportEngine.debtors(d).forEach { st -> assertEquals(st.debtor.id, minor(st.remaining), core[st.debtor.id]?.balance ?: 0L) }
    val users = ReportCore.sales(l, "2026-10-01", "2026-10-14").user.associateBy { it.key.ifEmpty { "نامعلوم" } }
    ReportEngine.bySeller(d, "2026-10-01", "2026-10-14").forEach { s ->
      assertEquals(s.seller, s.count, users.getValue(s.seller).invoices)
      assertEquals(s.seller, minor(s.net), users.getValue(s.seller).sales)
    }
  }

  /**
   *  تفاوتِ عمدی با DashboardEngine: داشبورد برگشتیِ هر فاکتور را از روزِ **فاکتور** کم
   *  می‌کند (پس عددِ روزِ بسته‌شده بعداً عوض می‌شود)؛ هسته آن را در روزِ **خودِ برگشتی**
   *  می‌نشاند، مثلِ ReportEngine.sales. برگشتیِ r1 (۱۴۰ افغانی، بها ۱۳۰) فاکتورِ ۱۳ام است.
   */
  @Test
  fun `داشبورد برگشتی را به روزِ فاکتور می‌برد، هسته به روزِ خودش`() {
    val now = java.time.Instant.parse("2026-10-14T12:00:00Z").toEpochMilli()
    val b14 = DashboardEngine.board(d, "2026-10-14", 1, now)
    val c14 = ReportCore.today(l, "2026-10-14").today
    assertEquals(62950L, minor(b14.todaySales))
    assertEquals(19950L, minor(b14.profit.value))
    assertEquals(48950L, c14.netSales) // ۶۲۹.۵ − ۱۴۰
    assertEquals(18950L, c14.grossProfit) // ۱۹۹.۵ − (۱۴۰ − ۱۳۰)
    val b13 = DashboardEngine.board(d, "2026-10-13", 1, now)
    assertEquals(56000L, minor(b13.todaySales)) // ۷۰۰ − ۱۴۰
    assertEquals(70000L, ReportCore.today(l, "2026-10-13").today.netSales)
  }

  /**
   *  INV-21 — موردِ G ِ `cost-v1.json` (ساختِ ۶ بسته از دستور): بهای هر قلم و ارزشِ موجودیِ گزارش (`ReportCore` MAVG، از
   *  دفترِ برنامه با `ReportBridge`) عیناً همان موتورِ موجودی (`StockLedger` ⇐ `StockCore`، سندِ `production` با `costMinor`)؛
   *  و ردیف‌های تولیدِ بردار همان‌اند که `Bom.build` روی همان خریدها می‌نویسد. پیش‌تر گزارش خروجی را ۶ × ۱۱٫۳۳ = ۶۷٫۹۸ می‌گرفت.
   */
  @Test
  fun `ساختِ بسته — بهای گزارش همان StockLedger و ردیف‌های بردار همان Bom_build`() {
    val raw = Json.parseToJsonElement(CostVectorsTest.vectorFile().readText()).jsonObject.getValue("cases").jsonArray
      .map { it.jsonObject }.single { it.getValue("id").jsonPrimitive.content == "G" }.getValue("ledger").jsonObject
    val rd: ShopData = Json { ignoreUnknownKeys = true }.decodeFromJsonElement(ShopData.serializer(), raw)
    val v = StockLedger.replay(rd)
    assertEquals(emptyList<Pair<String, String>>(), v.errors)
    val ledger = v.rows.filter { it.src == "sale" }.groupBy { it.eventId }.mapValues { (_, rs) -> rs.sumOf { it.costMinor } }
    assertEquals(mapOf("i1" to 4530L, "i2" to 1133L, "i3" to 1086L), ledger)
    assertEquals(6795L, v.rows.single { it.type == Repack.MOVE && it.qtyMilli > 0 }.costMinor)
    assertEquals(ledger, ReportCore.costs(ReportBridge.ledgerOf(rd), ReportCore.Costing.MAVG).line)
    val report = ReportBridge.stockCostOf(rd).rows.associate { it.productId to (it.qtyMilli to it.valueMinor) }
    for (pid in listOf("b", "c", "r")) {
      val pv = StockLedger.productView(v, pid)
      assertEquals(pid, pv.qtyMilli to pv.valueMinor, report[pid])
    }
    assertEquals(1000L to 1132L, report["b"])
    //  همان ردیف‌ها از خودِ `Bom.build` (هر بسته ۱ شیرِ خشک + ۱ روبان) روی خریدهای همان دفتر
    val made = rd.warehouseEntries.filter { it.adjKind == Repack.KIND }
    val base = rd.copy(warehouseEntries = rd.warehouseEntries - made.toSet(), sales = emptyList(), saleItems = emptyList())
    val day = made.first().date
    val d1 = (Bom.save(base, "bom1", "b", listOf(Repack.Part("c", 1_000), Repack.Part("r", 1_000)), 0, "کریم", day, 1) as StockPlaces.Result.Ok).data
    var n = 0
    val built = (Bom.build(d1, "op1", "b", 6_000, null, "", "کریم", day, made.first().createdAt, { "x" + (++n) }) as StockPlaces.Result.Ok).data
    fun key(w: WarehouseEntry) = listOf(w.productId, w.units, w.price, w.costMinor, w.isAdjustment, w.adjKind, w.lotId, w.notes, w.refId, w.userId, w.date, w.createdAt)
    assertEquals(made.map(::key), built.warehouseEntries.filter { it.refId == "op1" }.map(::key))
  }
}
