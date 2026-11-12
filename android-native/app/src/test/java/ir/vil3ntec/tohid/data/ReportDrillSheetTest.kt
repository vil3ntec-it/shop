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

  /** همان [data] با خرج، برگشت، حرکتِ انبار و قرض‌دار — برای ریزِ «سود و زیان»، ردیفِ کالا و ردیفِ مشتری */
  private fun richer() = data().copy(
    saleItems = data().saleItems.map { it.copy(purchasePrice = it.unitPrice - 100) },
    expenses = listOf(
      Expense(id = "e1", title = "کرایه", amount = 700.0, date = "2026-08-05", createdAt = 1),
      Expense(id = "e2", title = "برق", amount = 120.0, date = "2026-08-20", createdAt = 2),
      Expense(id = "e0", title = "بیرون", amount = 9_999.0, date = "2026-07-01", createdAt = 0),
    ),
    saleReturns = listOf(
      SaleReturn(id = "r1", saleId = "s1", saleItemId = "i2", productId = "p2", quantity = 1.0, amount = 500.0, date = "2026-08-15", createdAt = 6_000),
      SaleReturn(id = "r2", saleId = "s4", saleItemId = "", productId = "p1", quantity = 1.0, amount = 300.0, date = "2026-08-15", createdAt = 6_100),
    ),
    stockMovements = listOf(
      StockMovement(id = "m1", productId = "p1", type = "purchase_in", qty = 10.0, date = "2026-08-01", createdAt = 10),
      StockMovement(id = "m2", productId = "p2", type = "purchase_in", qty = 5.0, date = "2026-08-01", createdAt = 11),
      StockMovement(id = "m3", productId = "p1", type = "sale", qty = -2.0, date = "2026-08-10", createdAt = 12),
    ),
    debtors = listOf(Debtor(id = "d1", name = "کریم", phone = "0700000001"), Debtor(id = "d2", name = "نادر", phone = "0700000002")),
    transactions = listOf(
      DebtTransaction(id = "t1", debtorId = "d1", type = "give", amount = 900.0, date = "2026-08-02", createdAt = 20),
      DebtTransaction(id = "t2", debtorId = "d1", type = "receive", amount = 400.0, date = "2026-08-09", createdAt = 21),
      DebtTransaction(id = "t3", debtorId = "d2", type = "give", amount = 250.0, date = "2026-08-03", createdAt = 22),
    ),
  )

  @Test fun `هر ردیفِ سود و زیان ریزی دارد که جمعِ ستونش همان مبلغِ ردیف است و سودها ریز ندارند`() {
    val d = richer()
    val body = ReportCsv.of("profit", d, from, to, to).rows.drop(1)
    assertEquals(9, body.size)
    fun col(r: Int, c: Int) = ReportDrillSheet.of("profit", d, from, to, r)!!.rows.drop(1).sumOf { it[c].toLong() }
    //  شمار = ردیف‌های ریز؛ ناخالص، تخفیف، خالص و بهای تمام‌شده = جمعِ همان ستون (لغوشده و بیرون از بازه نه)
    assertEquals(body[0][1].toInt(), ReportDrillSheet.of("profit", d, from, to, 0)!!.rows.drop(1).size)
    assertEquals(4, body[0][1].toInt())
    assertEquals("ناخالص", body[1][1].toLong(), col(1, 2))
    assertEquals("تخفیف", body[2][1].toLong(), col(2, 3))
    assertEquals("خالص", body[3][1].toLong(), col(3, 4))
    assertEquals(1700L, body[5][1].toLong())
    assertEquals("بهای تمام‌شده", body[5][1].toLong(), col(5, 5))
    //  مرجوعی: برگشتِ فاکتورِ لغوشده (s4) نه
    assertEquals(500L, body[4][1].toLong())
    assertEquals("مرجوعی", body[4][1].toLong(), col(4, 4))
    assertEquals("روغن", ReportDrillSheet.of("profit", d, from, to, 4)!!.rows[1][2])
    //  مصارف: همان برگهٔ «مصارف» ِ بازه
    assertEquals(820L, body[7][1].toLong())
    assertEquals("مصارف", body[7][1].toLong(), col(7, 3))
    assertNull("سودِ ناخالص حاصلِ ردیف‌های دیگر است", ReportDrillSheet.of("profit", d, from, to, 6))
    assertNull("سودِ خالص", ReportDrillSheet.of("profit", d, from, to, 8))
    assertNull(ReportDrillSheet.of("profit", d, from, to, 9))
  }

  @Test fun `ردیفِ کالا گردشِ همان کالا و ردیفِ قرض‌دار صورت‌حسابِ همان مشتری است`() {
    val d = richer()
    val products = ReportCsv.of("products", d, from, to, to).rows.drop(1)
    products.forEachIndexed { r, cells ->
      val moves = ReportDrillSheet.of("products", d, from, to, r)!!
      assertEquals(cells[0], moves.name)
      assertTrue("فقط حرکت‌های همان کالا", moves.rows.drop(1).all { it[1] == cells[0] })
    }
    assertEquals(2, ReportDrillSheet.of("products", d, from, to, 0)!!.rows.drop(1).size)
    assertEquals(1, ReportDrillSheet.of("products", d, from, to, 1)!!.rows.drop(1).size)
    assertNull(ReportDrillSheet.of("products", d, from, to, 2))

    val debtors = ReportCsv.of("debtors", d, from, to, to).rows.drop(1)
    assertEquals(2, debtors.size)
    debtors.forEachIndexed { r, cells ->
      val st = ReportDrillSheet.of("debtors", d, from, to, r, today = to)!!
      assertEquals(cells[0], st.name)
      assertEquals("ردیفِ $r: ماندهٔ آخرِ صورت‌حساب = «بدهی» ِ ردیف", cells[2], st.rows.last()[4])
    }
    val karim = ReportDrillSheet.of("debtors", d, from, to, 0)!!.rows.drop(1)
    assertEquals(listOf("900", ""), karim[0].subList(2, 4))
    assertEquals(listOf("", "400", "500"), karim[1].subList(2, 5))
    assertNull(ReportDrillSheet.of("debtors", d, from, to, 2))
    assertNull(ReportDrillSheet.of("debtors", d, from, to, -1))
  }

  @Test fun `ردیفِ کالا و مشتری در برگه‌های اختیاری هم همان کالا و همان مشتری است`() {
    val base = richer()
    val d = base.copy(
      products = base.products.map { it.copy(minStock = 100.0) },
      sales = base.sales.map { if (it.id == "s1" || it.id == "s2") it.copy(debtorId = "d1") else it },
    )
    //  ستونِ نام در هر برگه: تحلیلِ موجودی خانهٔ دوم (پس از «ردیف»)، بقیه خانهٔ اول
    for ((key, nameCol) in listOf("insight" to 1, "reorder" to 0, "aging" to 0, "reminders" to 0, "rfm" to 0)) {
      val body = ReportCsv.of(key, d, from, to, to).rows.drop(1)
      assertTrue("$key: ردیف دارد", body.isNotEmpty())
      body.forEachIndexed { r, cells ->
        val drill = ReportDrillSheet.of(key, d, from, to, r, today = to)
        assertNotNull("$key ردیفِ $r", drill)
        assertEquals("$key ردیفِ $r", cells[nameCol], drill!!.name)
      }
      assertNull(key, ReportDrillSheet.of(key, d, from, to, body.size, today = to))
    }
    //  «مشتریانِ ارزشمند»: شمار و جمعِ ریز = «تعداد خرید» و «مبلغِ خرید» ِ ردیف (برگشتِ s1 کم شده)
    val rfm = ReportCsv.of("rfm", d, from, to, to).rows.drop(1).single()
    val buys = ReportDrillSheet.of("rfm", d, from, to, 0, today = to)!!.rows.drop(1)
    assertEquals(rfm[5].toInt(), buys.size)
    assertEquals(rfm[6].toLong(), buys.sumOf { it[2].toLong() })
    assertEquals(1100L, rfm[6].toLong())
  }

  @Test fun `هر برگهٔ گزارش‌ها یا ریز دارد یا ردیفش تک‌سند است و صفحه همان کلیدِ ریز را دارد`() {
    for (key in ReportDrillSheet.RECORD_ROWS) assertNull(key, ReportDrillSheet.of(key, richer(), from, to, 0))
    assertTrue((ReportDrillSheet.KEYS intersect ReportDrillSheet.RECORD_ROWS).isEmpty())
    //  جدولِ «گزارش‌ها» ِ کامپیوتر: کلیک یا Enter ِ `reports.drill` روی ردیف (همان `FreshTable` ِ «گزارشِ تازه»)
    val repo = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }.first { File(it, "android-native").isDirectory }
    val ui = File(repo, "android-native/app/src/main/java/ir/vil3ntec/tohid/ui/v3/analytics")
    assertTrue(File(ui, "SheetTable.kt").readText().contains("e.matches(KeyMap.REPORTS_DRILL)"))
    val desk = File(ui, "ReportsDesk.kt").readText()
    assertTrue(desk.contains("ReportDrillSheet.of(rep.key, d, from, to, i, lineSeller, today)"))
    //  همهٔ زبانه‌های جدول‌دارِ `DeskRep` (جز «پرسشِ ساده» که جدول ندارد) در یکی از دو دسته‌اند
    val enumBody = desk.substringAfter("enum class DeskRep").substringBefore(";")
    val tabs = Regex("""[A-Z_]+\("([a-z]+)"""").findAll(enumBody).map { it.groupValues[1] }.toSet() - "ask"
    assertTrue("زبانه‌ها خوانده شدند: $tabs", tabs.size >= 17)
    assertEquals(tabs, ReportDrillSheet.KEYS + ReportDrillSheet.RECORD_ROWS)
  }
}
