package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.promo.Loyalty
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 *  PRM-13 — «کسب روی پرداختِ بعدیِ نسیه» (`LoyaltyPoints.afterPayment`، از راهِ تنها درِ رسیدِ دستی `DebtEntry.change` و
 *  فرمِ گوشیِ نسخهٔ ۳): سهمِ نسیهٔ فاکتور هنگامِ ثبت امتیاز نمی‌گیرد؛ رسیدِ بعدیِ مشتری با FIFO روی بدهی‌ها همان امتیاز را
 *  می‌دهد — تجمعی، بی تکرار، تا سقفِ فاکتور، و با برگشت و لغو به همان نسبت پس گرفته می‌شود.
 */
class LoyaltyCreditPaymentTest {

  private var seq = 0
  private val newId: () -> String = { "id${++seq}" }
  private val today = "2026-11-03"
  private val now = 1_800_000_000_000L
  private val rule = Loyalty.Rule(everyAfn = 100, pointAfn = 1, maxSharePct = 20, silverAfn = 50_000, goldAfn = 200_000)

  private val base = ShopData(
    products = listOf(Product(id = "p1", name = "روغن", salePrice = 1000.0, purchasePrice = 700.0, unit = "عدد")),
    warehouseEntries = listOf(WarehouseEntry(id = "w1", productId = "p1", units = 50.0)),
    debtors = listOf(Debtor(id = "d1", name = "نوشا"), Debtor(id = "d2", name = "رضا")),
  )

  @Before fun on() { LoyaltyRules.override = LoyaltyRules.Config(true, rule, false) }
  @After fun off() { LoyaltyRules.override = null }

  /** فاکتورِ نسیهٔ ۲٬۰۰۰ با پرداختِ [paid] برای «نوشا» */
  private fun creditSale(d: ShopData, id: String, paid: Double, at: Long = now): ShopData {
    val r = SalesEngine.record(
      d, listOf(SalesEngine.CartLine("p1", 2.0)),
      SalesEngine.Checkout(debtorId = "d1", payment = SalesEngine.Payment.CREDIT, paidAmount = paid), today, at, newId, saleId = id,
    )
    return (LoyaltyPoints.post(r, true, rule, null, "رضا") as SalesEngine.Result.Ok).data
  }

  /** رسیدِ دستیِ مشتری از تنها درِ ثبت (`DebtEntry.change`) با کلیدِ [key] */
  private fun pay(d: ShopData, amount: Double, key: String, at: Long, debtor: String = "d1"): ShopData =
    DebtEntry.change(DebtEntry.Entry(debtor, DebtorEngine.Kind.RECEIVE, amount, today, ""), today, at, newId) { error(it) }(d, key)!!

  private fun earned(d: ShopData, sale: String) =
    d.auditLog.filter { it.type == LoyaltyPoints.EARN && it.refId == sale }.sumOf { it.points ?: 0L }

  @Test fun `PRM-13 رسیدِ بعدی امتیازِ سهمِ نسیهٔ پرداخت‌شده را می‌دهد، تجمعی و تا سقفِ فاکتور`() {
    var d = creditSale(base, "s1", 500.0)
    assertEquals("هنگامِ ثبت فقط ۵۰۰ ِ پرداخت‌شده", 5L, earned(d, "s1"))
    d = pay(d, 1000.0, "t1", now + 1)
    val row = d.auditLog.single { it.id == "t1-lp-s1" }
    assertEquals(Triple(LoyaltyPoints.EARN, "s1", "d1"), Triple(row.type, row.refId, row.payRef))
    assertEquals(10L, row.points)
    assertEquals(15L, LoyaltyPoints.balance(d, "d1"))
    d = pay(d, 300.0, "t2", now + 2)
    assertEquals(18L, earned(d, "s1"))
    //  بیش از نسیه (۲۰۰ ِ مانده + ۱٬۰۰۰ پیش‌پرداخت) ⇒ فقط تا ۲٬۰۰۰ ِ فاکتور
    d = pay(d, 1200.0, "t3", now + 3)
    assertEquals(20L, earned(d, "s1"))
    assertEquals(20L, LoyaltyPoints.balance(d, "d1"))
  }

  @Test fun `PRM-13 تلاشِ دوباره و رسیدِ پردازش‌شده هرگز امتیازِ دوم نمی‌سازد`() {
    var d = creditSale(base, "s1", 500.0)
    d = pay(d, 1000.0, "t1", now + 1)
    //  همان رسید دوباره ⇒ همان شیء
    assertSame(d, LoyaltyPoints.afterPayment(d, "t1", null))
    //  پیشوندِ رسید پاک شود هم (دفترِ دستگاهِ دیگر)، هدفِ تجمعی همان است ⇒ هیچ ردیفِ تازه
    val stripped = d.copy(auditLog = d.auditLog.map { if (it.id == "t1-lp-s1") it.copy(id = "elsewhere-lp-s1") else it })
    assertSame(stripped, LoyaltyPoints.afterPayment(stripped, "t1", null))
    //  درِ ثبت (`AppendOnce`) همان کلید را «هست» می‌بیند و دوباره نمی‌نویسد؛ امتیاز یک بار
    assertTrue(DebtEntry.has(d, "t1"))
    assertEquals(15L, earned(d, "s1"))
  }

  @Test fun `PRM-13 برگشت و لغو امتیازِ پرداختِ نسیه را هم به همان نسبت پس می‌گیرند`() {
    var d = creditSale(base, "s1", 500.0)
    d = pay(d, 1500.0, "t1", now + 1)
    assertEquals(20L, earned(d, "s1"))
    //  برگشتِ یک دانه از دو ⇒ نیمی از کسبِ کل (پرداختِ هنگامِ ثبت + پرداختِ بعدی)
    val item = d.saleItems.first { it.saleId == "s1" }.id
    val back = (ReturnFlow.post(d, ReturnFlow.Request(docId = "r1", saleId = "s1", picks = mapOf(item to ReturnFlow.Pick(1.0)), reason = "دیگر"), today, now + 2, newId)
      as ReturnFlow.Outcome.Ok).value.data
    val earnBack = back.auditLog.filter { it.type == LoyaltyPoints.EARN_BACK && it.refId == "s1" }.sumOf { it.points ?: 0L }
    assertEquals(10L, earnBack)
    assertEquals(10L, LoyaltyPoints.balance(back, "d1"))
    //  لغوِ همهٔ فاکتور ⇒ هرچه مانده پس
    val cancelled = (SalesEngine.cancel(d, "s1", today, now + 3, newId, reason = "اشتباه") as SalesEngine.Result.Ok).data
    assertEquals(20L, cancelled.auditLog.filter { it.type == LoyaltyPoints.EARN_BACK && it.refId == "s1" }.sumOf { it.points ?: 0L })
    assertEquals(0L, LoyaltyPoints.balance(cancelled, "d1"))
    //  رسیدِ پس از برگشت فقط تا خالصِ پس از برگشت (۱٬۰۰۰) کسب می‌دهد — بخشِ برگشته دوباره امتیاز نمی‌گیرد
    val after = pay(back, 500.0, "t2", now + 4)
    assertTrue(after.auditLog.none { it.id.startsWith("t2-lp-") })
    assertEquals(10L, LoyaltyPoints.balance(after, "d1"))
  }

  @Test fun `PRM-13 رسید اول بدهیِ کهنه‌تر و قرضِ دستی را می‌بندد و فاکتورِ پیش از آغازِ امتیاز چیزی نمی‌گیرد`() {
    //  قرضِ دستیِ ۱٬۰۰۰ پیش از فاکتور
    var d = DebtorEngine.addTransaction(base, "d1", DebtorEngine.Kind.GIVE, 1000.0, today, "قرض", today, now - 10, newId).let {
      (it as DebtorEngine.Result.Ok).data
    }
    d = creditSale(d, "s1", 500.0)
    d = pay(d, 1000.0, "t1", now + 1)
    assertEquals("۱٬۰۰۰ ِ نخست قرضِ کهنه را بست", 5L, earned(d, "s1"))
    d = pay(d, 500.0, "t2", now + 2)
    assertEquals(10L, earned(d, "s1"))

    //  فاکتورِ نسیه‌ای که پیش از نخستین ردیفِ امتیازِ دکان بود ⇒ هیچ
    val old = SalesEngine.record(
      base, listOf(SalesEngine.CartLine("p1", 2.0)),
      SalesEngine.Checkout(debtorId = "d2", payment = SalesEngine.Payment.CREDIT, paidAmount = 0.0), today, now - 100, newId, saleId = "old",
    ).let { (it as SalesEngine.Result.Ok).data }
    val later = creditSale(old, "s1", 500.0)
    val paid = pay(later, 2000.0, "t9", now + 5, debtor = "d2")
    assertTrue(paid.auditLog.none { it.id.startsWith("t9-lp-") })
  }

  @Test fun `PRM-13 امتیازِ خاموش یا قرضِ دستی ⇒ همان دفتر`() {
    val d = creditSale(base, "s1", 500.0)
    LoyaltyRules.override = LoyaltyRules.Config(false, rule, false)
    val paid = pay(d, 1000.0, "t1", now + 1)
    assertSame(paid, LoyaltyPoints.afterPayment(paid, "t1", null))
    assertTrue(paid.auditLog.none { it.id.startsWith("t1-lp-") })
    LoyaltyRules.override = LoyaltyRules.Config(true, rule, false)
    //  بردِ دستی (نه رسید) امتیازی نمی‌دهد
    val given = DebtEntry.change(DebtEntry.Entry("d1", DebtorEngine.Kind.GIVE, 100.0, today, ""), today, now + 2, newId) { error(it) }(d, "g1")!!
    assertTrue(given.auditLog.none { it.id.startsWith("g1-lp-") })
  }

  @Test fun `PRM-13 حذفِ رسید امتیازی را که داده بود پس می‌گیرد و رسیدِ بعدی از نو حساب می‌کند`() {
    var d = creditSale(base, "s1", 500.0)
    d = pay(d, 1000.0, "t1", now + 1)
    assertEquals(15L, LoyaltyPoints.balance(d, "d1"))
    d = (DebtorEngine.deleteTransaction(d, "t1", today, now + 2, newId) as DebtorEngine.Result.Ok).data
    val back = d.auditLog.single { it.id == "t1-lp-s1-x" }
    assertEquals(Triple(LoyaltyPoints.EARN_BACK, "s1", 10L), Triple(back.type, back.refId, back.points))
    assertEquals(5L, LoyaltyPoints.balance(d, "d1"))
    //  دوباره ⇒ همان
    assertSame(d, LoyaltyPoints.afterPaymentDeleted(d, "t1", today, now + 3))
    //  رسیدِ تازه از نو و تجمعی: همان ۱۰ امتیاز، نه بیشتر
    d = pay(d, 1000.0, "t2", now + 4)
    assertEquals(10L, d.auditLog.single { it.id == "t2-lp-s1" }.points)
    assertEquals(15L, LoyaltyPoints.balance(d, "d1"))
    //  حذفِ بردِ دستی یا رسیدِ بی امتیاز ⇒ هیچ ردیفِ امتیاز
    val given = DebtorEngine.addTransaction(d, "d1", DebtorEngine.Kind.GIVE, 50.0, today, "", today, now + 5, newId) as DebtorEngine.Result.Ok
    val removed = (DebtorEngine.deleteTransaction(given.data, given.id, today, now + 6, newId) as DebtorEngine.Result.Ok).data
    assertEquals(d.auditLog.count { LoyaltyPoints.isRow(it) }, removed.auditLog.count { LoyaltyPoints.isRow(it) })
  }
}
