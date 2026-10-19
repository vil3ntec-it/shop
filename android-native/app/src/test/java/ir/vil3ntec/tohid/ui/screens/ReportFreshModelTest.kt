package ir.vil3ntec.tohid.ui.screens

import ir.vil3ntec.tohid.core.report.ReportBoard
import ir.vil3ntec.tohid.core.report.ReportCoreVectorsTest
import ir.vil3ntec.tohid.core.report.ReportLedger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** «گزارشِ تازه»: زبانه ⇐ جدول، برش، قالب و خروجی — روی دفترِ آزمون‌های مرجع */
class ReportFreshModelTest {
  private val l = ReportLedger.parse(
    Json.parseToJsonElement(ReportCoreVectorsTest.vectorFile().readText()).jsonObject.getValue("ledgers").jsonObject.getValue("kabul").jsonObject,
  )
  private val today = "2026-10-14"

  @Test
  fun `هر زبانه جدولِ خودش را از هسته می‌گیرد`() {
    assertEquals(listOf("today"), ReportFreshModel.view(l, "today", "today", "", today, null).tables.map { it.id })
    assertEquals(listOf("sales.user"), ReportFreshModel.view(l, "sales", "month", "user", today, null).tables.map { it.id })
    assertEquals(listOf("sales.product"), ReportFreshModel.view(l, "sales", "month", "??", today, null).tables.map { it.id })
    assertEquals(listOf("debts.customers", "debts.suppliers"), ReportFreshModel.view(l, "debts", "all", "", today, null).tables.map { it.id })
    assertEquals(listOf("fraud"), ReportFreshModel.view(l, "fraud", "week", "", today, null).tables.map { it.id })
    assertTrue(ReportFreshModel.view(l, "nope", "today", "", today, null).tables.isEmpty())
  }

  @Test
  fun `سود و حاشیه فقط ستون‌های سود را دارد و برچسبِ تقریبی می‌خورد`() {
    val v = ReportFreshModel.view(l, "profit", "month", "category", today, null)
    val t = v.tables.single()
    assertEquals(listOf("name", "netSales", "cogs", "grossProfit", "marginBp"), t.columns.map { it.key })
    assertTrue(t.title.contains("تقریبی"))
    assertTrue(v.sales!!.totals.profitApproximate)
    //  جمعِ جدول همان جمعِ هسته است
    assertEquals(v.sales!!.totals.grossProfit, t.total!![3].long)
    assertEquals(v.sales!!.totals.netSales, t.total!![1].long)
  }

  @Test
  fun `کلیک روی کارتِ خانه همان عدد را در جمعِ فهرستِ مقصد نشان می‌دهد`() {
    val home = ReportBoard.build(l, today)
    for (k in home.kpis.filter { it.id == "netSales" || it.id == "grossProfit" }) {
      val t = ReportFreshModel.view(l, k.target.tab, k.target.span, k.target.dim, today, null).tables.first()
      val col = t.columns.indexOfFirst { it.key == k.id }
      assertEquals(k.id, k.value, t.total!![col].long)
    }
    val debts = home.kpis.first { it.id == "receivable" }
    val t = ReportFreshModel.view(l, debts.target.tab, debts.target.span, debts.target.dim, today, null).tables.first()
    assertEquals(debts.value, t.total!![1].long)
  }

  @Test
  fun `شاگرد فقط سنِ بدهی را می‌بیند`() {
    assertEquals(listOf("debts"), ReportFreshModel.tabs(false).map { it.id })
    assertEquals(5, ReportFreshModel.tabs(true).size)
  }

  @Test
  fun `قالبِ خانه‌ها`() {
    assertEquals("1,234", ReportFreshModel.display(JsonPrimitive(123_400L), "money"))
    assertEquals("-15", ReportFreshModel.display(JsonPrimitive(-1_550L), "money")) // بی اعشار، همان money()
    assertEquals("1.5", ReportFreshModel.display(JsonPrimitive(1_500L), "qty"))
    assertEquals("38.8٪", ReportFreshModel.display(JsonPrimitive(3_882L), "bp"))
    assertEquals("-0.1٪", ReportFreshModel.display(JsonPrimitive(-5L), "bp"))
    assertEquals("0٪", ReportFreshModel.display(JsonPrimitive(-4L), "bp"))
    assertEquals("—", ReportFreshModel.display(JsonNull, "bp"))
    assertEquals("بله", ReportFreshModel.display(JsonPrimitive(true), "flag"))
    assertEquals("x2026-10-14", ReportFreshModel.display(JsonPrimitive("2026-10-14"), "date") { "x$it" })
  }

  @Test
  fun `خروجی همان جدولِ ساده است، با عنوان وقتی چند جدول است`() {
    val one = ReportFreshModel.view(l, "fraud", "week", "", today, null).tables
    val rows = ReportFreshModel.exportRows(one)
    assertEquals(one.single().columns.map { it.label }, rows.first())
    assertEquals("جمع", rows.last().first())
    val two = ReportFreshModel.view(l, "debts", "all", "", today, null).tables
    val out = ReportFreshModel.exportRows(two)
    assertEquals(listOf("سن بدهی مشتری‌ها"), out.first())
    assertTrue(out.contains(listOf("طلب تأمین‌کنندگان")))
    assertTrue(out.contains(listOf("")))
  }

  @Test
  fun `نمای کارتی برای هر جدول ستونِ موجود دارد`() {
    for (tab in listOf("today", "sales", "profit", "debts", "fraud")) {
      for (t in ReportFreshModel.view(l, tab, "month", "product", today, null).tables) {
        val keys = ReportFreshModel.cardKeys(t.id)
        assertTrue(t.id, keys.isNotEmpty())
        assertTrue(t.id, keys.all { k -> t.columns.any { it.key == k } })
      }
    }
  }
}
