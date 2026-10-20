package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.report.ReportBoard
import ir.vil3ntec.tohid.core.report.ReportClock
import ir.vil3ntec.tohid.core.report.ReportCore
import ir.vil3ntec.tohid.core.report.ReportCoreVectorsTest
import ir.vil3ntec.tohid.core.report.ReportLedger
import ir.vil3ntec.tohid.core.report.ReportTables
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  سیم‌کشیِ گزارش: دفترِ برنامه (`ShopData`) ⇐ `ReportBridge.ledgerOf` ⇐ هسته، در برابرِ
 *  پاسخِ آزمون‌های مرجعِ `core/test-vectors/report-v1.json` (همان که JS و Kotlin می‌گذرانند).
 *
 *  هر جا مدلِ امروزِ برنامه فیلدِ دفترِ وب را دارد، پاسخ **عیناً** همان است. فیلدهایی که
 *  `ShopData` ندارد (کانالِ فروش، تأییدکننده، کاربرِ برگشتی و رویدادِ صندوقِ شکلِ pay-core)
 *  این‌جا با عدد ثبت شده‌اند تا اگر روزی مدل آن‌ها را گرفت، همین آزمون بگوید.
 */
class ReportBridgeParityTest {
  private val vectors = Json.parseToJsonElement(ReportCoreVectorsTest.vectorFile().readText()).jsonObject
  private val decoder = Json { ignoreUnknownKeys = true }
  private val json = Json { encodeDefaults = true }

  private fun raw(name: String) = vectors.getValue("ledgers").jsonObject.getValue(name).jsonObject
  private fun bridged(name: String): ReportLedger = ReportBridge.ledgerOf(decoder.decodeFromJsonElement(ShopData.serializer(), raw(name)))
  private fun s(o: JsonObject, k: String): String? = (o[k] as? JsonPrimitive)?.contentOrNull
  private fun clock(o: JsonObject): ReportClock? = (o["clock"] as? JsonObject)?.let {
    ReportClock.fixed(it.getValue("offsetMinutes").jsonPrimitive.int, it.getValue("dayStartMinute").jsonPrimitive.int)
  }
  private fun costing(o: JsonObject) = if (s(o, "costing") == "mavg") ReportCore.Costing.MAVG else ReportCore.Costing.LINE
  private fun cases(fn: String) = vectors.getValue("cases").jsonArray.map { it.jsonObject }.filter { it.getValue("fn").jsonPrimitive.content == fn }
  private fun opts(c: JsonObject) = c["opts"] as? JsonObject ?: JsonObject(emptyMap())
  private fun today(l: ReportLedger, o: JsonObject) =
    ReportCore.today(l, s(o, "date"), clock(o), costing(o), (o["now"] as? JsonPrimitive)?.longOrNull ?: 0L, s(o, "costApproved") == "true")
  private fun sales(l: ReportLedger, o: JsonObject) = ReportCore.sales(l, s(o, "from"), s(o, "to"), clock(o), costing(o), s(o, "costApproved") == "true")
  private fun debts(l: ReportLedger, o: JsonObject) =
    ReportCore.debts(l, s(o, "asOf")!!, (o["buckets"] as? JsonArray)?.map { it.jsonPrimitive.long } ?: listOf(30L, 60L, 90L), clock(o))

  @Test
  fun `امروز و سنِ بدهی از دفترِ برنامه عیناً همان پاسخِ مرجع است`() {
    val all = cases("today") + cases("debts")
    assertTrue(all.size >= 7)
    all.forEach { c ->
      val l = bridged(c.getValue("ledger").jsonPrimitive.content)
      val got = if (c.getValue("fn").jsonPrimitive.content == "today") json.encodeToJsonElement(ReportCore.TodayReport.serializer(), today(l, opts(c)))
      else json.encodeToJsonElement(ReportCore.DebtReport.serializer(), debts(l, opts(c)))
      assertEquals(c.getValue("name").jsonPrimitive.content, c.getValue("expect"), got)
    }
  }

  @Test
  fun `جدولِ امروز و بدهی برای خروجی هم عیناً همان است`() {
    cases("tables").filter { it.getValue("of").jsonPrimitive.content in setOf("today", "debts") }.forEach { c ->
      val l = bridged(c.getValue("ledger").jsonPrimitive.content)
      val o = opts(c)
      val tables = if (c.getValue("of").jsonPrimitive.content == "today") ReportTables.today(today(l, o)) else ReportTables.debts(debts(l, o))
      val got = JsonArray(tables.map { json.encodeToJsonElement(ReportTables.Plain.serializer(), ReportTables.plain(it)) })
      assertEquals(c.getValue("name").jsonPrimitive.content, c.getValue("expect"), got)
    }
  }

  /** مدلِ برنامه حالا `Sale.channel` دارد (#275/#321)، پس تفکیکِ کانال هم عیناً همان مرجع است */
  @Test
  fun `فروش با همهٔ تفکیک‌ها، کانال هم، همان است`() {
    cases("sales").forEach { c ->
      val name = c.getValue("name").jsonPrimitive.content
      val want = c.getValue("expect").jsonObject
      val r = sales(bridged(c.getValue("ledger").jsonPrimitive.content), opts(c))
      val got = json.encodeToJsonElement(ReportCore.SalesReport.serializer(), r).jsonObject
      for (k in listOf("from", "to", "costing", "totals", "product", "category", "user", "hour", "payment", "channel")) {
        assertEquals("$name · $k", want.getValue(k), got.getValue(k))
      }
      assertEquals(name, r.totals.netSales, r.channel.sumOf { it.netSales })
    }
  }

  /**
   *  ضدتقلب: جمعِ تخفیف، برگشتی، لغو و حذفِ ردیف همان است؛ کاربرِ برگشتی و تأییدکننده
   *  و رویدادِ کشوی شکلِ pay-core (`by`/`at`) در مدلِ برنامه نیست — به «نامعلوم» می‌رود
   *  یا نمی‌رسد. عددِ دقیقِ همین تفاوت این‌جا قفل است.
   */
  @Test
  fun `ضدتقلب از دفترِ برنامه — جمع‌ها همان و کاربرِ بی‌فیلد نامعلوم`() {
    val c = cases("fraud").single()
    val want = c.getValue("expect").jsonObject.getValue("totals").jsonObject
    val o = opts(c)
    val r = ReportCore.fraud(bridged("kabul"), s(o, "from"), s(o, "to"), clock(o))
    fun w(k: String) = want.getValue(k).jsonPrimitive.long
    assertEquals(w("invoices"), r.totals.invoices.toLong())
    assertEquals(w("gross"), r.totals.gross)
    assertEquals(w("discount"), r.totals.discount)
    assertEquals(w("returnCount"), r.totals.returnCount.toLong())
    assertEquals(w("returns"), r.totals.returns)
    assertEquals(w("voidCount"), r.totals.voidCount.toLong())
    assertEquals(w("voids"), r.totals.voids)
    assertEquals(w("lineDeleteCount"), r.totals.lineDeleteCount.toLong())
    assertEquals(w("lineDeletes"), r.totals.lineDeletes)
    //  a3 (`no_sale` با by/at و تأییدِ «مدیر») و تأییدِ تخفیفِ s2 در ShopData نیستند
    assertEquals(w("noSaleCount") - 1, r.totals.noSaleCount.toLong())
    assertEquals(0L, r.totals.overrideCount.toLong())
    assertEquals(0L, r.totals.approvalCount.toLong())
    //  کاربرِ برگشتی (`returns[].user`) و سابقه در مدل نیست ⇒ «نامعلوم»
    assertTrue(r.rows.any { it.key == "" && it.returnCount > 0 })
  }

  @Test
  fun `تابلوی خانه از دفترِ برنامه همان تابلوی دفترِ وب است`() {
    val date = "2026-10-14"
    val web = ReportBoard.build(ReportLedger.parse(raw("kabul")), date)
    val app = ReportBoard.build(bridged("kabul"), date)
    //  رویدادِ کشوی a3 تاریخ ندارد (فقط `at`) و در روزِ «۱۴» بی ساعت شمرده نمی‌شود؛ پس تابلو یکی است
    assertEquals(web, app)
  }
}
