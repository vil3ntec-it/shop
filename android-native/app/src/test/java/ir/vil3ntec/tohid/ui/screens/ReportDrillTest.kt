package ir.vil3ntec.tohid.ui.screens

import ir.vil3ntec.tohid.core.report.ReportCore
import ir.vil3ntec.tohid.core.report.ReportCoreVectorsTest
import ir.vil3ntec.tohid.core.report.ReportLedger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.long
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  RPT-13 — «Enter در جدول‌های دیگرِ گزارش»: هر ردیفِ تفکیکِ «فروش» و «سود و حاشیه» (کالا، دسته، فروشنده، ساعت، روش،
 *  کانال) با کلیک یا Enter فاکتورهای همان ردیف را نشان می‌دهد. سنجه روی دفترِ آزمون‌های مرجعِ هستهٔ گزارش: سهمِ هر فاکتور
 *  همان سهمی است که ستونِ «فروش» ِ ردیف جمع می‌زند (پس از تخفیفِ پخش‌شده)، پس جمعِ ریز = همان عددِ ردیف، برای همهٔ ردیف‌ها.
 */
class ReportDrillTest {

  private val l = ReportLedger.parse(
    Json.parseToJsonElement(ReportCoreVectorsTest.vectorFile().readText()).jsonObject.getValue("ledgers").jsonObject.getValue("kabul").jsonObject,
  )

  @Test fun `RPT-13 جمعِ ریزِ هر ردیفِ هر تفکیک همان فروش و شمارِ فاکتورِ همان ردیف است`() {
    val s = ReportCore.sales(l)
    var rows = 0
    for (dim in ReportCore.DIMS) {
      for (r in ReportFreshModel.dimRows(s, dim)) {
        val list = ReportCore.invoicesOf(l, s.from, s.to, dim, r.key)
        assertEquals("$dim/${r.key}", r.sales, list.sumOf { it.sales })
        assertEquals("$dim/${r.key}", r.invoices, list.size)
        assertEquals("$dim/${r.key}", list.map { it.saleId }.distinct().size, list.size)
        rows++
      }
    }
    assertTrue("دفترِ مرجع ردیف دارد", rows > 6)
    //  کلیدِ ناموجود یا تفکیکِ ناشناخته ⇒ هیچ
    assertTrue(ReportCore.invoicesOf(l, s.from, s.to, "user", "کسی-که-نیست").isEmpty())
    assertTrue(ReportCore.invoicesOf(l, s.from, s.to, "weather", "").isEmpty())
  }

  @Test fun `RPT-13 جدولِ ریز روز و فاکتور و مقدار و فروش دارد و جمعش همان ستونِ ردیف است`() {
    val v = ReportFreshModel.view(l, "sales", "all", "user", "2026-10-14", null)
    val s = v.sales!!
    val table = v.tables.single()
    val row = table.rows.first()
    val t = ReportFreshModel.drill(l, s, "user", table.rows.indexOf(row), row.first().content, null) { "#$it" }!!
    assertEquals(ReportFreshModel.DRILL_ID, t.id)
    assertEquals(listOf("date", "doc", "qty", "sales"), t.columns.map { it.key })
    assertTrue(t.title, t.title.contains(row.first().content) && t.title.contains("${t.rows.size} فاکتور"))
    assertEquals(s.user.first().sales, t.total!![3].jsonPrimitive.long)
    assertTrue(t.rows.all { it[1].content.startsWith("#") })
    //  ردیفِ بی‌جا ⇒ هیچ پنجره
    assertNull(ReportFreshModel.drill(l, s, "user", -1, "", null) { it })
    assertNull(ReportFreshModel.drill(l, s, "user", 10_000, "", null) { it })
  }

  @Test fun `RPT-13 گزارش‌ها جدول‌های فروش و سود را با همان کلیک یا Enter ریز می‌کنند`() {
    val src = listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
      .map { File(it, "ir/vil3ntec/tohid/ui/screens/ReportFresh.kt") }.firstOrNull { it.isFile }?.readText() ?: error("ReportFresh.kt پیدا نشد")
    assertTrue(src.contains("""sales != null && (t.id.startsWith("sales.") || t.id == "profit") -> { row ->"""))
    assertTrue(src.contains("ReportFreshModel.drill(ledger, sales, dimNow, t.rows.indexOf(row), row.first().content, clock) { DayEndModel.docLabel(d, it) }"))
    //  همان جدول و همان Enter ِ `reports.drill`
    assertTrue(src.contains("FreshTable(t, Modifier.weight(1f).fillMaxWidth(), onRow)"))
    assertTrue(src.contains("e.matches(KeyMap.REPORTS_DRILL)"))
  }
}
