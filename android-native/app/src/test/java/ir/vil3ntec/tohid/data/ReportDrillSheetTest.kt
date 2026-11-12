package ir.vil3ntec.tohid.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  RPT-13 — «ریز» ِ ردیفِ برگه‌های «گزارش‌ها» ِ کامپیوتر (`ReportDrillSheet`): هر ردیفِ برگهٔ «فروش» و «فروشِ هر کاربر» همان
 *  ردیفِ `ReportCsv.of` است و جمعِ ریزش همان خانهٔ همان ردیف؛ برگهٔ بی ریز، ردیفِ بی‌جا و «فروشندهٔ هر ردیف» ⇒ null.
 */
class ReportDrillSheetTest {

  private val from = "2026-08-01"
  private val to = "2026-08-31"
  private val rice = Product(id = "p1", name = "برنج", unit = "کیلو", purchasePrice = 200.0, salePrice = 300.0)
  private val oil = Product(id = "p2", name = "روغن", unit = "عدد", purchasePrice = 400.0, salePrice = 500.0)

  private fun sale(id: String, n: Int, seller: String, date: String, at: Long, total: Double, discount: Double = 0.0, status: String = "completed") =
    Sale(id = id, invoiceNumber = n, total = total, discount = discount, finalTotal = total - discount, paidAmount = total - discount,
      status = status, createdAt = at, date = date, seller = seller)

  private fun data() = ShopData(
    products = listOf(rice, oil),
    sales = listOf(
      //  ترتیبِ ثبت عمداً با ترتیبِ فهرست فرق دارد: برگه به `createdAt` است
      sale("s3", 3, "مریم", "2026-08-12", 3_000, 500.0),
      sale("s1", 1, "احمد", "2026-08-10", 1_000, 1100.0, discount = 100.0),
      sale("s2", 2, "احمد", "2026-08-11", 2_000, 600.0),
      sale("s4", 4, "احمد", "2026-08-13", 4_000, 300.0, status = "cancelled"),
      sale("s5", 5, "", "2026-08-14", 5_000, 250.0),
      sale("s0", 9, "احمد", "2026-07-30", 500, 999.0),   // بیرون از بازه
    ),
    saleItems = listOf(
      SaleItem(id = "i1", saleId = "s1", productId = "p1", quantity = 2.0, unitPrice = 300.0, totalPrice = 600.0),
      SaleItem(id = "i2", saleId = "s1", productId = "p2", quantity = 1.0, unitPrice = 500.0, totalPrice = 500.0),
      SaleItem(id = "i3", saleId = "s2", productId = "", name = "قلمِ آزاد", quantity = 1.0, unitPrice = 600.0, totalPrice = 600.0),
      SaleItem(id = "i4", saleId = "s3", productId = "p2", quantity = 1.0, unitPrice = 500.0, totalPrice = 500.0),
    ),
  )

  @Test fun `هر ردیفِ برگهٔ فروش همان فاکتور است و جمعِ قلم‌هایش همان مبلغِ کلِ ردیف`() {
    val d = data()
    val sheet = ReportCsv.of("sales", d, from, to, to)
    val body = sheet.rows.drop(1)
    assertEquals(5, body.size)
    body.forEachIndexed { r, cells ->
      val drill = ReportDrillSheet.of("sales", d, from, to, r)
      assertNotNull("ردیفِ $r", drill)
      val lines = drill!!.rows.drop(1)
      assertEquals(listOf("کالا", "مقدار", "فی", "جمع"), drill.rows.first())
      if (lines.isNotEmpty()) assertEquals("ردیفِ $r: جمعِ قلم‌ها = «مبلغ کل»", cells[2].toLong(), lines.sumOf { it[3].toLong() })
      assertTrue("نامِ پنجره همان فاکتورِ ردیف: ${drill.name} / ${cells[0]}", drill.name.contains(cells[0]))
    }
    //  ردیفِ نخست (کم‌ترین `createdAt`) همان s1 با دو قلم؛ قلمِ آزاد نامِ خودش را دارد
    val first = ReportDrillSheet.of("sales", d, from, to, 0)!!.rows.drop(1)
    assertEquals(listOf("برنج", "روغن"), first.map { it[0] })
    assertEquals("قلمِ آزاد", ReportDrillSheet.of("sales", d, from, to, 1)!!.rows[1][0])
    assertNull("ردیفِ بی‌جا", ReportDrillSheet.of("sales", d, from, to, 5))
    assertNull(ReportDrillSheet.of("sales", d, from, to, -1))
  }

  @Test fun `هر ردیفِ فروشِ هر کاربر همان فروشنده است؛ شمار و جمعِ فاکتورها همان ستون‌های ردیف`() {
    val d = data()
    val body = ReportCsv.of("sellers", d, from, to, to).rows.drop(1)
    assertEquals(3, body.size)
    body.forEachIndexed { r, cells ->
      val drill = ReportDrillSheet.of("sellers", d, from, to, r)!!
      assertEquals(cells[0], drill.name)
      val invoices = drill.rows.drop(1)
      assertEquals("ردیفِ $r: «تعدادِ فاکتور»", cells[1].toInt(), invoices.size)
      assertEquals("ردیفِ $r: «فروشِ خالص»", cells[2].toLong(), invoices.sumOf { it[2].toLong() })
    }
    //  لغوشده و بیرون از بازه در ریز نیستند؛ فروشندهٔ خالی همان «نامعلوم» ِ برگه
    val ahmad = ReportDrillSheet.of("sellers", d, from, to, body.indexOfFirst { it[0] == "احمد" })!!.rows.drop(1)
    assertEquals(listOf("2026-08-10", "2026-08-11"), ahmad.map { it[0] })
    assertEquals(1, ReportDrillSheet.of("sellers", d, from, to, body.indexOfFirst { it[0] == "نامعلوم" })!!.rows.drop(1).size)
    //  «فروشندهٔ هر ردیف» (POS-23): فاکتور میانِ چند فروشنده پخش است ⇒ ریز نیست
    assertNull(ReportDrillSheet.of("sellers", d, from, to, 0, lineSeller = true))
  }

  @Test fun `برگهٔ بی ریز هیچ ریزی ندارد و صفحهٔ گزارش‌ها همان کلیدِ ریز را دارد`() {
    for (key in listOf("profit", "purchases", "expenses", "products", "debtors", "stock")) {
      assertNull(key, ReportDrillSheet.of(key, data(), from, to, 0))
    }
    assertEquals(setOf("sales", "sellers"), ReportDrillSheet.KEYS)
    //  جدولِ «گزارش‌ها» ِ کامپیوتر: کلیک یا Enter ِ `reports.drill` روی ردیف (همان `FreshTable` ِ «گزارشِ تازه»)
    val repo = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }.first { File(it, "android-native").isDirectory }
    val ui = File(repo, "android-native/app/src/main/java/ir/vil3ntec/tohid/ui/v3/analytics")
    assertTrue(File(ui, "SheetTable.kt").readText().contains("e.matches(KeyMap.REPORTS_DRILL)"))
    assertTrue(File(ui, "ReportsDesk.kt").readText().contains("ReportDrillSheet.of("))
  }
}
