package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.i18n.StrLoyalty
import ir.vil3ntec.tohid.core.promo.Loyalty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  PRM-13 — امتیازِ وفاداری روی دفترِ دکان (`LoyaltyPoints`): کسب، ضریبِ سطح، مصرف با سقفِ مانده و سهم، تلاشِ دوباره،
 *  برگشتِ تناسبی (`ReturnFlow.post` و `SalesEngine.recordReturn`)، لغو و نگهبانِ ماندهٔ منفی.
 */
class LoyaltyPointsTest {

  private var seq = 0
  private val newId: () -> String = { "id${++seq}" }
  private val today = "2026-11-02"
  private val now = 1_800_000_000_000L
  private val day = 24L * 60 * 60 * 1000
  private val rule = Loyalty.Rule(everyAfn = 100, pointAfn = 1, maxSharePct = 20, silverAfn = 50_000, goldAfn = 200_000)

  private val base = ShopData(
    products = listOf(
      Product(id = "p1", name = "روغن", salePrice = 1000.0, purchasePrice = 700.0, unit = "عدد"),
      Product(id = "p2", name = "برنج", salePrice = 500.0, purchasePrice = 300.0, unit = "عدد"),
    ),
    warehouseEntries = listOf(WarehouseEntry(id = "w1", productId = "p1", units = 50.0), WarehouseEntry(id = "w2", productId = "p2", units = 50.0)),
    debtors = listOf(Debtor(id = "d1", name = "نوشا"), Debtor(id = "d2", name = "نوشا (کهنه)", mergedInto = "d1")),
  )

  private fun line(id: String, q: Double) = SalesEngine.CartLine(id, q)

  private fun seed(d: ShopData, points: Long, customer: String = "d1") = d.copy(
    auditLog = d.auditLog + AuditEntry(id = "seed-$customer-$points", type = LoyaltyPoints.EARN, refId = "old", payRef = customer, points = points, createdAt = 1),
  )

  private fun sell(
    d: ShopData, cart: List<SalesEngine.CartLine>, saleId: String, debtorId: String? = "d1", plan: LoyaltyPoints.Plan? = null,
    checkout: SalesEngine.Checkout = SalesEngine.Checkout(debtorId = debtorId), at: Long = now,
  ): SalesEngine.Result = LoyaltyPoints.post(SalesEngine.record(d, cart, checkout, today, at, newId, saleId = saleId), true, rule, plan, "رضا")

  private fun ok(r: SalesEngine.Result): ShopData = (r as? SalesEngine.Result.Ok)?.data ?: error("ثبت نشد: $r")
  private fun ret(r: ReturnFlow.Outcome<ReturnFlow.Posted>): ShopData = (r as? ReturnFlow.Outcome.Ok)?.value?.data ?: error("برگشت ثبت نشد: $r")
  private fun rows(d: ShopData, saleId: String) = d.auditLog.filter { LoyaltyPoints.isRow(it) && it.refId == saleId }
  private fun bal(d: ShopData, c: String = "d1") = LoyaltyPoints.balance(d, c)

  @Test fun `کسب روی پولِ پرداخت‌شده و فقط با مشتری`() {
    var d = ok(sell(base, listOf(line("p1", 1.0), line("p2", 1.0)), "s1"))
    val e = rows(d, "s1").single()
    assertEquals(LoyaltyPoints.EARN, e.type)
    assertEquals("s1-lp-e", e.id)
    assertEquals("d1", e.payRef)
    assertEquals(15L, e.points)
    assertEquals(0.0, e.amount, 0.0)
    assertEquals(15L, bal(d))
    //  نسیهٔ ۲٬۰۰۰ با ۵۰۰ پرداخت ⇒ فقط ۵ امتیاز
    d = ok(sell(d, listOf(line("p1", 2.0)), "s2", checkout = SalesEngine.Checkout(debtorId = "d1", payment = SalesEngine.Payment.CREDIT, paidAmount = 500.0)))
    assertEquals(5L, rows(d, "s2").single().points)
    //  بی مشتری ⇒ همان نتیجه، بی ردیف
    val plain = SalesEngine.record(d, listOf(line("p2", 1.0)), SalesEngine.Checkout(), today, now, newId, saleId = "s3")
    assertSame(plain, LoyaltyPoints.post(plain, true, rule, null, null))
  }

  @Test fun `سطح از خریدِ دوازده ماهه ضریب می‌دهد`() {
    val past = Sale(id = "h1", total = 60_000.0, finalTotal = 60_000.0, paidAmount = 60_000.0, debtorId = "d2", createdAt = now - 10 * day, date = today)
    val silver = ok(sell(base.copy(sales = listOf(past)), listOf(line("p1", 1.0)), "s1"))
    assertEquals(12L, rows(silver, "s1").single().points)
    assertEquals(Loyalty.SILVER, LoyaltyPoints.view(silver, rule, "d1", now + 1).tier.key)
    //  بیش از ۱۲ ماه پیش ⇒ برنزی
    val old = ok(sell(base.copy(sales = listOf(past.copy(createdAt = now - 400 * day))), listOf(line("p1", 1.0)), "s1"))
    assertEquals(10L, rows(old, "s1").single().points)
    //  طلایی
    val gold = ok(sell(base.copy(sales = listOf(past.copy(total = 250_000.0, finalTotal = 250_000.0))), listOf(line("p1", 1.0)), "s1"))
    assertEquals(15L, rows(gold, "s1").single().points)
  }

  @Test fun `مصرف تا مانده و تا سهمِ فاکتور و از راهِ قیمتِ ردیف نه تخفیفِ فاکتور`() {
    val d0 = seed(base, 500)
    val cart = listOf(line("p1", 1.0), line("p2", 1.0))
    assertEquals(300L, LoyaltyPoints.capFor(d0, cart, rule, "d1"))
    assertNull(LoyaltyPoints.apply(d0, cart, rule, "d1", 301))
    val a = LoyaltyPoints.apply(d0, cart, rule, "d1", 300)!!
    assertEquals(listOf(800.0, 400.0), a.cart.map { it.unitPrice })
    assertEquals(LoyaltyPoints.Plan("d1", 300, 300_00, 1_500_00, 1_200_00), a.plan)
    val d = ok(sell(d0, a.cart, "s1", plan = a.plan))
    val sale = d.sales.single { it.id == "s1" }
    assertEquals(0.0, sale.discount, 0.0)
    assertEquals(1200.0, sale.finalTotal, 0.0)
    val spend = rows(d, "s1").single { it.type == LoyaltyPoints.SPEND }
    assertEquals("s1-lp-s", spend.id)
    assertEquals(300L, spend.points)
    assertEquals(300.0, spend.amount, 0.0)
    //  کسب فقط روی ۱٬۲۰۰ ِ پرداخت‌شده
    assertEquals(12L, rows(d, "s1").single { it.type == LoyaltyPoints.EARN }.points)
    assertEquals(500L - 300 + 12, bal(d))
    //  مانده کمتر از سهم ⇒ سقف همان مانده
    val small = seed(base, 50)
    assertEquals(50L, LoyaltyPoints.capFor(small, cart, rule, "d1"))
    assertNull(LoyaltyPoints.apply(small, cart, rule, "d1", 51))
    assertNull(LoyaltyPoints.apply(small, cart, rule, null, 10))
  }

  @Test fun `مصرفِ ناهمخوان ثبت نمی‌شود`() {
    val d0 = seed(base, 500)
    val a = LoyaltyPoints.apply(d0, listOf(line("p1", 1.0)), rule, "d1", 200)!!
    //  مشتریِ فاکتور عوض شد
    assertEquals(SalesEngine.Result.Failed(StrLoyalty.errCustomer), sell(d0, a.cart, "s1", debtorId = null, plan = a.plan))
    //  سبدِ بی امتیاز با برنامهٔ امتیاز
    assertEquals(SalesEngine.Result.Failed(StrLoyalty.errChanged), sell(d0, listOf(line("p1", 1.0)), "s1", plan = a.plan))
    //  مانده همین حالا کم شد (دستگاهِ دیگر)
    val spent = d0.copy(auditLog = d0.auditLog + AuditEntry(id = "x-lp-s", type = LoyaltyPoints.SPEND, refId = "x", payRef = "d1", points = 400, amount = 400.0))
    assertEquals(SalesEngine.Result.Failed(StrLoyalty.errBalance), sell(spent, a.cart, "s1", plan = a.plan))
    //  خاموش با برنامه ⇒ نه
    val r = SalesEngine.record(d0, a.cart, SalesEngine.Checkout(debtorId = "d1"), today, now, newId, saleId = "s1")
    assertEquals(SalesEngine.Result.Failed(StrLoyalty.errRule), LoyaltyPoints.post(r, false, rule, a.plan, null))
  }

  @Test fun `تلاشِ دوباره ردیفِ دوم نمی‌سازد`() {
    val d0 = seed(base, 500)
    val a = LoyaltyPoints.apply(d0, listOf(line("p1", 1.0)), rule, "d1", 200)!!
    val first = sell(d0, a.cart, "s1", plan = a.plan)
    val d = ok(first)
    //  همان نتیجه دوباره از قلاب ⇒ بی ردیفِ تازه
    val again = LoyaltyPoints.post(first, true, rule, null, "رضا")
    assertEquals(rows(d, "s1").map { it.id }, rows(ok(again), "s1").map { it.id })
    //  همان کلیدِ فاکتور ⇒ ثبت نمی‌شود و هیچ ردیفی
    val dup = sell(d, a.cart, "s1", plan = a.plan)
    assertTrue(dup is SalesEngine.Result.Failed)
    //  همان برگشت دو بار ⇒ یک بار
    val item = d.saleItems.single { it.saleId == "s1" }.id
    val back = ret(ReturnFlow.post(d, ReturnFlow.Request(docId = "r1", saleId = "s1", picks = mapOf(item to ReturnFlow.Pick(1.0)), reason = "دیگر"), today, now + 1, newId))
    assertSame(back, LoyaltyPoints.afterReturn(d, back, "s1", "r1", today, now + 2, null))
    assertTrue(ReturnFlow.post(back, ReturnFlow.Request(docId = "r1", saleId = "s1", picks = mapOf(item to ReturnFlow.Pick(1.0)), reason = "دیگر"), today, now + 3, newId) is ReturnFlow.Outcome.Bad)
  }

  @Test fun `برگشتِ تکه‌تکه به نسبتِ پول و جمعش همان برگشتِ کامل`() {
    val d0 = seed(base, 1000)
    val a = LoyaltyPoints.apply(d0, listOf(line("p2", 3.0)), rule, "d1", 300)!!
    var d = ok(sell(d0, a.cart, "s1", plan = a.plan))
    assertEquals(1000L - 300 + 12, bal(d))
    val item = d.saleItems.single { it.saleId == "s1" }.id
    //  یک از سه با برگشتِ تازه ⇒ ۴ امتیاز پس، ۱۰۰ امتیاز به مشتری
    d = ret(ReturnFlow.post(d, ReturnFlow.Request(docId = "r1", saleId = "s1", picks = mapOf(item to ReturnFlow.Pick(1.0)), reason = "دیگر"), today, now + 1, newId))
    assertEquals(4L, d.auditLog.single { it.id == "r1-lp-e" }.points)
    assertEquals(100L, d.auditLog.single { it.id == "r1-lp-s" }.points)
    assertEquals(100.0, d.auditLog.single { it.id == "r1-lp-s" }.amount, 0.0)
    //  یکی دیگر با مرجوعیِ امروز
    d = ok(SalesEngine.recordReturn(d, "s1", mapOf(item to 1.0), "دیگر", today, now + 2, newId))
    val second = rows(d, "s1").filter { it.createdAt == now + 2 }
    assertEquals(setOf(LoyaltyPoints.EARN_BACK to 4L, LoyaltyPoints.SPEND_BACK to 100L), second.map { it.type to it.points }.toSet())
    //  آخری ⇒ همهٔ باقی، و مانده درست همان پیش از فاکتور
    d = ok(SalesEngine.recordReturn(d, "s1", mapOf(item to 1.0), "دیگر", today, now + 3, newId))
    assertEquals(1000L, bal(d))
    assertEquals(12L, rows(d, "s1").filter { it.type == LoyaltyPoints.EARN_BACK }.sumOf { it.points ?: 0 })
    assertEquals(300L, rows(d, "s1").filter { it.type == LoyaltyPoints.SPEND_BACK }.sumOf { it.points ?: 0 })
  }

  @Test fun `لغو هرچه مانده برمی‌گرداند`() {
    val d0 = seed(base, 1000)
    val a = LoyaltyPoints.apply(d0, listOf(line("p2", 3.0)), rule, "d1", 300)!!
    var d = ok(sell(d0, a.cart, "s1", plan = a.plan))
    val item = d.saleItems.single { it.saleId == "s1" }.id
    d = ok(SalesEngine.recordReturn(d, "s1", mapOf(item to 1.0), "دیگر", today, now + 1, newId))
    d = ok(SalesEngine.cancel(d, "s1", today, now + 2, newId, reason = "اشتباه", by = "رضا"))
    assertEquals(8L, d.auditLog.single { it.id == "s1-lx-e" }.points)
    assertEquals(200L, d.auditLog.single { it.id == "s1-lx-s" }.points)
    assertEquals(1000L, bal(d))
    //  فاکتورِ بی امتیاز ⇒ لغوِ همان امروز (بی ردیفِ امتیاز)
    val plain = ok(SalesEngine.record(d, listOf(line("p1", 1.0)), SalesEngine.Checkout(), today, now + 3, newId, saleId = "s9"))
    assertTrue(rows(ok(SalesEngine.cancel(plain, "s9", today, now + 4, newId, reason = "اشتباه")), "s9").isEmpty())
  }

  @Test fun `برگشت یا لغو مانده را منفی نمی‌کند`() {
    var d = ok(sell(base, listOf(line("p1", 1.0), line("p2", 1.0)), "sA"))
    assertEquals(15L, bal(d))
    val a = LoyaltyPoints.apply(d, listOf(line("p1", 1.0)), rule, "d1", 15)!!
    d = ok(sell(d, a.cart, "sB", plan = a.plan))
    assertEquals(9L, bal(d))
    d = ok(SalesEngine.cancel(d, "sA", today, now + 1, newId, reason = "اشتباه"))
    assertEquals(9L, d.auditLog.single { it.id == "sA-lx-e" }.points)
    assertEquals(0L, bal(d))
    assertTrue(d.auditLog.single { it.id == "sA-lx-e" }.notes.contains("6"))
  }

  @Test fun `مشتریِ ادغام‌شده همان مشتریِ ماندگار است و سابقه و رسید از دفتر`() {
    val d0 = seed(seed(base, 40, "d2"), 10, "d1")
    assertEquals(50L, bal(d0, "d1"))
    assertEquals(50L, bal(d0, "d2"))
    val a = LoyaltyPoints.apply(d0, listOf(line("p1", 1.0)), rule, "d1", 30)!!
    val d = ok(sell(d0, a.cart, "s1", plan = a.plan))
    val h = LoyaltyPoints.history(d, "d1")
    assertEquals(4, h.size)
    assertEquals(listOf(-30L, 9L), h.filter { it.saleId == "s1" }.map { it.signed }.sorted())
    assertEquals(LoyaltyPoints.OnSale(9, 30, 30_00), LoyaltyPoints.onSale(d, "s1"))
    assertEquals(2, LoyaltyPoints.receiptLines(d, "s1").size)
    assertNotNull(LoyaltyPoints.receiptLines(d, "s1").first().second)
    assertTrue(LoyaltyPoints.receiptLines(d0, "s1").isEmpty())
  }
}
