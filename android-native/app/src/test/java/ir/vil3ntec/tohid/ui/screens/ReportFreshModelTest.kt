package ir.vil3ntec.tohid.ui.screens

import ir.vil3ntec.tohid.core.cost.CostMethod
import ir.vil3ntec.tohid.core.cost.CostPolicy
import ir.vil3ntec.tohid.core.report.ReportBoard
import ir.vil3ntec.tohid.core.report.ReportClock
import ir.vil3ntec.tohid.core.report.ReportCore
import ir.vil3ntec.tohid.core.report.ReportCoreVectorsTest
import ir.vil3ntec.tohid.core.report.ReportLedger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
  fun `سود و حاشیه فقط ستون‌های سود را دارد و قیمت روی قلم برچسب تقریبی می‌خورد`() {
    //  D-23: قیمتِ روی قلم (مصوب نیست) ⇒ همیشه «تقریبی»
    val v = ReportFreshModel.view(l, "profit", "month", "category", today, null, ReportCore.Costing.LINE)
    val t = v.tables.single()
    assertEquals(listOf("name", "netSales", "cogs", "grossProfit", "marginBp"), t.columns.map { it.key })
    assertTrue(t.title.contains("تقریبی"))
    assertTrue(v.sales!!.totals.profitApproximate)
    //  جمعِ جدول همان جمعِ هسته است
    assertEquals(v.sales!!.totals.grossProfit, t.total!![3].long)
    assertEquals(v.sales!!.totals.netSales, t.total!![1].long)
    //  پیش‌فرض (میانگینِ موزونِ مصوب): همان ستون‌ها، بی برچسبِ «تقریبی»
    val d = ReportFreshModel.view(l, "profit", "month", "category", today, null).tables.single()
    assertEquals(t.columns.map { it.key }, d.columns.map { it.key })
    assertFalse(d.title.contains("تقریبی"))
  }

  @Test
  fun `کلیک روی کارتِ خانه همان عدد را در جمعِ فهرستِ مقصد نشان می‌دهد`() {
    //  همان سیم‌کشیِ صفحه (`rememberHome`): کارت و فهرست با یک روش و یک تأیید — هر دو روش
    for (m in CostMethod.entries) {
      val c = ReportFreshModel.costingOf(m)
      val home = ReportBoard.build(l, today, null, c, CostPolicy.approved(m))
      for (k in home.kpis.filter { it.id == "netSales" || it.id == "grossProfit" }) {
        val t = ReportFreshModel.view(l, k.target.tab, k.target.span, k.target.dim, today, null, c).tables.first()
        val col = t.columns.indexOfFirst { it.key == k.id }
        assertEquals("${m.id} ${k.id}", k.value, t.total!![col].long)
      }
    }
    val home = ReportBoard.build(l, today)
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

  /**
   *  RPT-03 و D-23 (گرفته‌شده ۱۴۰۵/۰۸/۱۳: میانگینِ موزون): پیش‌فرضِ صفحه همان روشِ مصوب و با `costApproved` ِ هسته؛
   *  عددها همان بردارِ مرجعِ `report-v1.json` (مورد «فروشِ بازه — میانگینِ موزونِ متحرک»: بها ۹۲٬۶۰۵ در برابرِ
   *  ۹۹٬۰۰۰ ِ قیمتِ روی قلم). قیمتِ روی قلم انتخابی می‌ماند و همیشه «تقریبی» است.
   */
  @Test
  fun `روش بها راهبرد است و پیش‌فرض میانگین موزون مصوب است`() {
    val kabul = ReportClock.fixed(270, 240)
    val before = ReportFreshModel.view(l, "profit", "month", "product", today, kabul)
    val byDefault = ReportFreshModel.view(l, "profit", "month", "product", today, kabul, ReportFreshModel.costingOf(CostPolicy.DEFAULT))
    assertEquals(before, byDefault)
    assertEquals("mavg", before.sales!!.costing)
    assertEquals(92605L, before.sales!!.totals.cogs)
    val core = ReportCore.sales(l, "2026-10-01", today, kabul, ReportCore.Costing.MAVG, costApproved = true).totals
    assertEquals(core, before.sales!!.totals)
    assertEquals("مصوب ⇒ فقط قلمِ بی‌بها تقریبی می‌کند", core.costEstimatedLines > 0, before.sales!!.totals.profitApproximate)
    assertFalse(before.tables.single().title.contains("تقریبی"))
    //  جمعِ جدول همان جمعِ هسته با همان روش
    assertEquals(before.sales!!.totals.grossProfit, before.tables.single().total!![3].long)

    val line = ReportFreshModel.view(l, "profit", "month", "product", today, kabul, ReportFreshModel.costingOf(CostMethod.PURCHASE_PRICE))
    assertEquals("line", line.sales!!.costing)
    assertEquals(99000L, line.sales!!.totals.cogs)
    assertEquals(before.sales!!.totals.netSales, line.sales!!.totals.netSales)
    assertTrue(line.sales!!.totals.profitApproximate)
    assertTrue(line.tables.single().title.contains("تقریبی"))
    assertTrue(line.tables.single().title.contains("قیمت خرید روی قلم"))

    //  زبانهٔ امروز هم با همان روش و همان تأیید
    val todayDefault = ReportFreshModel.view(l, "today", "today", "", today, kabul)
    val todayLine = ReportFreshModel.view(l, "today", "today", "", today, kabul, ReportCore.Costing.LINE)
    val gp = todayLine.tables.single().columns.indexOfFirst { it.key == "grossProfit" }
    assertEquals(ReportCore.today(l, today, kabul, ReportCore.Costing.MAVG, 0, true).today.grossProfit, todayDefault.tables.single().rows[0][gp].long)
    assertEquals(ReportCore.today(l, today, kabul).today.grossProfit, todayLine.tables.single().rows[0][gp].long)

    //  هر شناسهٔ روش همان `costing` ِ هسته است
    CostMethod.entries.forEach { assertEquals(it.id, ReportFreshModel.costingOf(it).id) }
  }
}
