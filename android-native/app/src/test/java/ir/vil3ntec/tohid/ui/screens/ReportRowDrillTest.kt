package ir.vil3ntec.tohid.ui.screens

import ir.vil3ntec.tohid.core.report.ReportCore
import ir.vil3ntec.tohid.core.report.ReportCoreVectorsTest
import ir.vil3ntec.tohid.core.report.ReportLedger
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.ShopData
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  RPT-13 — Enter (یا کلیک) در جدول‌های «امروز»، «بدهی‌ها» و «تقلب» ِ «گزارشِ تازه»: هر ردیف ریزِ خودش را نشان می‌دهد و
 *  جمعِ ریز همان عددِ ردیف است — روی دفترِ آزمون‌های مرجعِ هستهٔ گزارش (`kabul`)، برای همهٔ ردیف‌ها.
 */
class ReportRowDrillTest {

  private val l = ReportLedger.parse(
    Json.parseToJsonElement(ReportCoreVectorsTest.vectorFile().readText()).jsonObject.getValue("ledgers").jsonObject.getValue("kabul").jsonObject,
  )

  @Test fun `RPT-13 ریزِ هر روزِ «امروز» فاکتورهای همان روز است با همان فروش و شمار`() {
    var days = 0
    for (date in l.sales.map { it.date }.distinct()) {
      val r = ReportCore.today(l, date)
      val list = ReportCore.dayInvoices(l, date)
      assertEquals(date, r.today.sales, list.sumOf { it.sales })
      assertEquals(date, r.today.invoices, list.size)
      days++
    }
    assertTrue(days > 1)
    val v = ReportFreshModel.view(l, "today", "all", "product", "2026-10-14", null)
    val t = v.tables.single()
    t.rows.forEach { row ->
      val drill = ReportFreshModel.dayDrill(l, row, null) { "#$it" }!!
      assertEquals(ReportFreshModel.DAY_DRILL_ID, drill.id)
      assertEquals(row[3].jsonPrimitive.long, drill.total!![2].jsonPrimitive.long)
      assertEquals(row[2].jsonPrimitive.long, drill.rows.size.toLong())
    }
    assertNull(ReportFreshModel.dayDrill(l, listOf(JsonNull), null) { it })
  }

  @Test fun `RPT-13 ریزِ هر ردیفِ سنِ بدهی سندهای باز است و جمعش همان مانده`() {
    val r = ReportCore.debts(l, "2026-10-14")
    var rows = 0
    for ((supplier, part) in listOf(false to r.customers, true to r.suppliers)) {
      part.rows.forEachIndexed { i, x ->
        val open = ReportCore.openDebt(l, r.asOf, supplier, x.id)
        assertEquals(x.id, x.open, open.lots.sumOf { it.left })
        assertEquals(x.id, x.credit, open.credit)
        //  سنِ قدیمی‌ترین سندِ باز همان ستون
        assertEquals(x.id, x.oldestDays ?: -1L, open.lots.maxOfOrNull { it.ageDays } ?: -1L)
        val t = ReportFreshModel.debtDrill(l, r, supplier, i, x.name, null) { "#$it" }!!
        assertEquals(ReportFreshModel.DEBT_DRILL_ID, t.id)
        assertEquals(x.balance, t.total!![2].jsonPrimitive.long)
        assertTrue(t.rows.take(open.lots.size).all { it[1].content.startsWith("#") })
        rows++
      }
    }
    assertTrue("دفترِ مرجع ردیفِ بدهی دارد", rows >= 2)
    assertNull(ReportFreshModel.debtDrill(l, r, false, 99, "", null) { it })
    assertTrue(ReportCore.openDebt(l, r.asOf, false, "کسی-که-نیست").lots.isEmpty())
  }

  @Test fun `RPT-13 ریزِ هر کاربرِ ضدتقلب شمار و مبلغِ همان ستون‌ها را دارد`() {
    val f = ReportCore.fraud(l)
    assertTrue(f.rows.size > 1)
    f.rows.forEachIndexed { i, x ->
      val ev = ReportCore.fraudEvents(l, f.from, f.to, x.key)
      val own = ev.filter { !it.byOther }
      fun n(k: String) = own.count { it.kind == k }
      fun sum(k: String) = own.filter { it.kind == k }.sumOf { it.amount }
      assertEquals(x.key, x.discountCount, n("discount")); assertEquals(x.key, x.discount, sum("discount"))
      assertEquals(x.key, x.returnCount, n("return")); assertEquals(x.key, x.returns, sum("return"))
      assertEquals(x.key, x.voidCount, n("void")); assertEquals(x.key, x.voids, sum("void"))
      assertEquals(x.key, x.lineDeleteCount, n("line_delete")); assertEquals(x.key, x.lineDeletes, sum("line_delete"))
      assertEquals(x.key, x.noSaleCount, n("no_sale"))
      assertEquals(x.key, x.overrideCount, own.count { it.approvedBy != "" })
      assertEquals(x.key, x.approvalCount, ev.count { it.byOther })
      val t = ReportFreshModel.fraudDrill(l, f, i, null) { "#$it" }!!
      assertEquals(ReportFreshModel.FRAUD_DRILL_ID, t.id)
      assertEquals(ev.size, t.rows.size)
    }
    //  تأییدکننده‌ای که خودش رویدادی ندارد هم ریز دارد (`approvalCount`)
    assertTrue(f.rows.any { it.approvalCount > 0 })
    assertNull(ReportFreshModel.fraudDrill(l, f, -1, null) { it })
  }

  @Test fun `RPT-13 فاکتورِ بی تخفیفی که تأییدکننده دارد هم در ریزِ هر دو کاربر می‌آید`() {
    //  `fraud` آن را «نیازمند تأیید» ِ فروشنده و «تأیید داده» ِ تأییدکننده می‌شمارد، پس ریز هم باید داشته باشد
    val small = ReportLedger.parse(Json.parseToJsonElement(
      """{"products":[{"id":"p1","name":"چای","category":"","purchasePrice":10,"salePrice":20}],
        "sales":[{"id":"a1","invoiceNumber":1,"total":200,"discount":0,"finalTotal":200,"paymentMethod":"cash","paidAmount":200,
                  "status":"completed","seller":"کریم","approvedBy":"مدیر","date":"2026-10-13","createdAt":1791898200000}],
        "saleItems":[{"id":"i1","saleId":"a1","productId":"p1","quantity":10,"unitPrice":20,"purchasePrice":10,"totalPrice":200}]}""",
    ).jsonObject)
    val f = ReportCore.fraud(small)
    val seller = f.rows.single { it.key == "کریم" }
    val approver = f.rows.single { it.key == "مدیر" }
    assertEquals(1, seller.overrideCount)
    assertEquals(1, approver.approvalCount)
    val mine = ReportCore.fraudEvents(small, f.from, f.to, "کریم")
    assertEquals(listOf("approved" to "مدیر"), mine.map { it.kind to it.approvedBy })
    assertEquals(listOf(true), ReportCore.fraudEvents(small, f.from, f.to, "مدیر").map { it.byOther })
  }

  @Test fun `RPT-13 گزارش‌ها جدول‌های امروز و بدهی‌ها و تقلب را با همان کلیک یا Enter ریز می‌کنند و سند نام دارد`() {
    val src = listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
      .map { File(it, "ir/vil3ntec/tohid/ui/screens/ReportFresh.kt") }.firstOrNull { it.isFile }?.readText() ?: error("ReportFresh.kt پیدا نشد")
    assertTrue(src.contains("ReportFreshModel.dayDrill(ledger, row, clock, ::saleDoc)"))
    assertTrue(src.contains("ReportFreshModel.debtDrill(ledger, view.debts, t.id == \"debts.suppliers\""))
    assertTrue(src.contains("ReportFreshModel.fraudDrill(ledger, view.fraud, t.rows.indexOf(row), clock, ::saleDoc)"))
    assertTrue(src.contains("fun saleDoc(id: String) = DayEndModel.docLabel(d, \"sale:\$id\")"))
    //  نامِ فاکتور با پیشوندِ `sale:` می‌آید؛ شناسهٔ خام ستونِ «فاکتور» را خالی می‌گذاشت
    val d = ShopData(sales = listOf(Sale(id = "s1", invoiceNumber = 7)))
    assertEquals("", DayEndModel.docLabel(d, "s1"))
    assertTrue(DayEndModel.docLabel(d, "sale:s1").contains("7"))
  }
}
