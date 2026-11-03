package ir.vil3ntec.tohid.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  PAY-17 — «چند کشو یا چند صندوقدار، و شیفتِ شناور»: هر کشوی نام‌دار صندوقِ جدای خودش را دارد و ردیف‌ها با مهرِ شیفت (نه
 *  دستگاه و نه ساعت) شمرده می‌شوند؛ بی «چند کشو» (پیش‌فرض) هیچ مهری نیست و همه چیز همان امروز.
 */
class MultiDrawerTest {

  @After fun reset() { TillDrawer.picked = null }

  private var seq = 0
  private fun newId(): String = "m${seq++}"

  private val rice = Product(id = "p1", name = "برنج", unit = "کیلو", purchasePrice = 200.0, salePrice = 300.0)
  private fun ledger() = ShopData(
    products = listOf(rice),
    warehouseEntries = listOf(WarehouseEntry(id = "w1", productId = "p1", units = 100.0)),
  )

  private fun ok(r: CashShift.Result): ShopData = (r as CashShift.Result.Ok).data
  private fun sold(r: SalesEngine.Result): ShopData = (r as SalesEngine.Result.Ok).data
  private fun sell(d: ShopData, units: Double, at: Long): ShopData =
    sold(SalesEngine.record(d, listOf(SalesEngine.CartLine("p1", units)), SalesEngine.Checkout(), "2026-11-03", at, ::newId))

  @Test
  fun `بی «چند کشو» هیچ مهری نیست و صندوق همان امروز است`() {
    var d = ok(CashShift.open(ledger(), 1000.0, "علی", "2026-11-03", 100, ::newId))
    val before = d
    d = sell(d, 2.0, 110)
    assertNull(d.sales.single().shiftId)
    assertSame("بی شیفتِ برگزیده همان شیء", d, TillDrawer.stampNew(before, d))
    val main = CashShift.current(d)!!
    assertNull(main.open.drawer)
    //  ۱۰۰۰ + ۶۰۰
    assertEquals(1600.0, CashShift.totalsOf(d, main, 200).expected, 1e-9)
    assertTrue("کشوی اصلی دو بار باز نمی‌شود", CashShift.open(d, 5.0, "", "2026-11-03", 120, ::newId) is CashShift.Result.Failed)
    //  حتی با شیفتِ برگزیدهٔ ناشناس (تنظیمِ کهنه) چیزی مهر نمی‌گیرد
    TillDrawer.picked = "gone"
    assertNull(TillDrawer.stamp(d))
    assertEquals(main.open.id, CashShift.current(d)!!.open.id)
  }

  @Test
  fun `دو صندوقدار هر کدام کشوی خودشان و کشوی اصلی جدا`() {
    var d = ok(CashShift.open(ledger(), 1000.0, "صاحب", "2026-11-03", 100, ::newId))
    d = ok(CashShift.openNamed(d, 200.0, "  کشوی احمد ", "احمد", "2026-11-03", 101, ::newId))
    val a = d.auditLog.last()
    assertEquals("کشوی احمد", a.drawer)
    assertEquals("احمد", a.by)
    assertTrue("نامِ تکراریِ کشوی باز", CashShift.openNamed(d, 0.0, "کشوی احمد", "", "2026-11-03", 102, ::newId) is CashShift.Result.Failed)
    assertTrue("بی نام", CashShift.openNamed(d, 0.0, "  ", "", "2026-11-03", 102, ::newId) is CashShift.Result.Failed)
    d = ok(CashShift.openNamed(d, 300.0, "کشوی محمود", "محمود", "2026-11-03", 103, ::newId))
    val m = d.auditLog.last()

    TillDrawer.picked = a.id
    d = sell(d, 1.0, 110)                                   // ۳۰۰ ⇒ احمد
    TillDrawer.picked = m.id
    d = sell(d, 2.0, 111)                                   // ۶۰۰ ⇒ محمود
    TillDrawer.picked = null
    d = sell(d, 4.0, 112)                                   // ۱۲۰۰ ⇒ اصلی
    d = d.copy(expenses = listOf(Expense(id = "e1", amount = 50.0, createdAt = 113)))
    assertEquals(listOf(a.id, m.id, null), d.sales.map { it.shiftId })

    fun expected(id: String) = CashShift.totalsOf(d, CashShift.list(d).first { it.open.id == id }, 200).expected
    assertEquals(500.0, expected(a.id), 1e-9)
    assertEquals(900.0, expected(m.id), 1e-9)
    //  کشوی اصلی: ۱۰۰۰ + ۱۲۰۰ − ۵۰ (مصرف فقط از کشوی اصلی)
    assertEquals(2150.0, expected(CashShift.list(d).first { it.open.drawer == null }.open.id), 1e-9)

    //  «جای امروز» ِ هر دستگاه
    TillDrawer.picked = a.id
    assertEquals(a.id, CashShift.current(d)!!.open.id)
    TillDrawer.picked = null
    assertNull(CashShift.current(d)!!.open.drawer)
    //  گردشِ نقدِ روز (خانه، بی شیفت) همهٔ کشوها با هم
    assertEquals(2050.0, CashShift.totals(d, 100, 200, 0.0).expected, 1e-9)
  }

  @Test
  fun `شیفتِ شناور روی دستگاهِ دیگر با ساعتِ عقب‌تر همان صندوق است و بستن آن را ثابت می‌کند`() {
    var d = ok(CashShift.openNamed(ledger(), 100.0, "کشوی ۲", "رضا", "2026-11-03", 1_000, ::newId))
    val sh = CashShift.list(d).first()
    //  دستگاهِ دوم: «ادامه روی این دستگاه» ⇒ سابقه، و ساعتش ۵ دقیقه عقب است
    val resume = TillDrawer.resumeRow(sh, "رضا", "2026-11-03", 900, "r1")
    assertEquals(TillDrawer.RESUME, resume.type)
    assertEquals(sh.open.id, resume.refId)
    d = d.copy(auditLog = d.auditLog + resume)
    TillDrawer.picked = sh.open.id
    d = sell(d, 1.0, 700)
    assertEquals(sh.open.id, d.sales.single().shiftId)
    assertEquals(400.0, CashShift.totalsOf(d, CashShift.current(d)!!, 2_000).expected, 1e-9)
    //  صندوقِ شیفت (pay-core): رویدادِ پیش از بازشدن نمی‌افتد
    val x = PayFlow.report(d, sh.open, null, 2_000).valueOrNull()!!
    assertEquals(0, x.skipped.size)
    assertEquals(40_000L, x.report.expected?.get(PayFlow.CASH))

    //  بستن روی دستگاهِ اول؛ دستگاهِ دوم هنوز نمی‌داند و یک فروشِ دیرتر ثبت می‌کند ⇒ مالِ کشوی اصلی، Z ِ «کشوی ۲» ثابت
    d = ok(CashShift.close(d, 400.0, "", "رضا", "2026-11-03", 3_000, ::newId))
    val stale = d
    d = d.copy(sales = d.sales + Sale(id = "late", finalTotal = 300.0, createdAt = 3_500, shiftId = sh.open.id))
    val closed = CashShift.list(d).first { it.open.id == sh.open.id }
    assertEquals(CashShift.totalsOf(stale, closed, 9_000).expected, CashShift.totalsOf(d, closed, 9_000).expected, 1e-9)
    assertNull("شیفتِ بسته مهر نمی‌دهد", TillDrawer.stamp(d))
    d = ok(CashShift.open(d, 0.0, "", "2026-11-03", 3_400, ::newId))
    TillDrawer.picked = null
    assertEquals(300.0, CashShift.totalsOf(d, CashShift.current(d)!!, 9_000).expected, 1e-9)
  }

  @Test
  fun `فقط ردیف‌های تازه مهر می‌گیرند — فروش، برگشت و رسید`() {
    var d = ok(CashShift.openNamed(ledger(), 0.0, "کشوی ۳", "", "2026-11-03", 10, ::newId))
    val id = d.auditLog.last().id
    val old = d.copy(
      sales = listOf(Sale(id = "s0", finalTotal = 10.0, createdAt = 5)),
      transactions = listOf(DebtTransaction("t0", "c", "receive", 5.0, createdAt = 5)),
    )
    TillDrawer.picked = id
    val after = old.copy(
      sales = old.sales + Sale(id = "s1", finalTotal = 20.0, createdAt = 20),
      saleReturns = listOf(SaleReturn(id = "r1", amount = 5.0, createdAt = 21)),
      transactions = old.transactions + DebtTransaction("t1", "c", "receive", 7.0, createdAt = 22),
    )
    val s = TillDrawer.stampNew(old, after)
    assertEquals(listOf(null, id), s.sales.map { it.shiftId })
    assertEquals(listOf(id), s.saleReturns.map { it.shiftId })
    assertEquals(listOf(null, id), s.transactions.map { it.shiftId })
    //  رسیدِ قرض‌دار از همان راهِ صندوق (`DebtEntry.change`)
    val d0 = old.copy(debtors = listOf(Debtor(id = "c", name = "کریم")))
    var failed: String? = null
    val made = DebtEntry.change(DebtEntry.Entry("c", DebtorEngine.Kind.RECEIVE, 50.0, "2026-11-03", ""), "2026-11-03", 30, ::newId) { failed = it }(d0, "rcv-1")
    assertNull(failed)
    assertEquals(id, made!!.transactions.first { it.id == "rcv-1" }.shiftId)
    assertFalse(d.auditLog.any { it.type == TillDrawer.RESUME })
  }

  @Test
  fun `همهٔ کشوهای امروز با هم — موردِ انتظارِ همه، شمرده و اختلاف فقط از کشوهای بسته`() {
    var d = ok(CashShift.open(ledger(), 1000.0, "صاحب", "2026-11-03", 100, ::newId))
    d = ok(CashShift.openNamed(d, 200.0, "کشوی احمد", "احمد", "2026-11-03", 101, ::newId))
    val a = d.auditLog.last()
    //  کشوی دیروز که دیروز بسته شد در «امروز» نیست
    d = ok(CashShift.openNamed(d, 50.0, "دیروز", "", "2026-11-02", 10, ::newId))
    val y = d.auditLog.last()
    TillDrawer.picked = y.id
    d = ok(CashShift.close(d, 50.0, "", "", "2026-11-02", 20, ::newId))
    TillDrawer.picked = a.id
    d = sell(d, 1.0, 110)                                   // ۳۰۰ ⇒ احمد
    TillDrawer.picked = null
    d = sell(d, 4.0, 112)                                   // ۱۲۰۰ ⇒ اصلی
    TillDrawer.picked = a.id
    d = ok(CashShift.close(d, 490.0, "", "احمد", "2026-11-03", 150, ::newId))   // موردِ انتظار ۵۰۰ ⇒ کسری ۱۰
    TillDrawer.picked = null

    val all = ir.vil3ntec.tohid.ui.v3.sales.drawer.DrawerModel.allDrawers(d, 200, dayStart = 100, payV2 = false)
    assertEquals(listOf(null, "کشوی احمد"), all.drawers.map { it.label })
    assertEquals(listOf(true, false), all.drawers.map { it.open })
    //  ۲۲۰۰ (اصلی) + ۵۰۰ (احمد)
    assertEquals(2700_00L, all.expectedMinor)
    assertEquals(1200_00L, all.openingMinor)
    assertEquals(490_00L, all.countedMinor)
    assertEquals(-10_00L, all.varianceMinor)
    val lines = ir.vil3ntec.tohid.ui.v3.sales.drawer.DrawerModel.allLines(all)
    assertTrue(lines.any { it.label == ir.vil3ntec.tohid.core.i18n.StrDrawers.totalVariance && it.value.contains("10") })
    assertFalse("کشوی دیروز", lines.any { it.label.contains("دیروز") })
    //  صندوقِ شیفت (pay-core) همان موردِ انتظار را می‌دهد
    assertEquals(2700_00L, ir.vil3ntec.tohid.ui.v3.sales.drawer.DrawerModel.allDrawers(d, 200, dayStart = 100, payV2 = true).expectedMinor)
  }
}
