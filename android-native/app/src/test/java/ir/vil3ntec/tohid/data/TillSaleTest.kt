package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.promo.Loyalty
import ir.vil3ntec.tohid.ui.v3.sales.checkout.CheckoutModel
import ir.vil3ntec.tohid.ui.v3.sales.history.ReceiptModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  ثبتِ فاکتورِ صندوقِ گوشیِ نسخهٔ ۳ (`CheckoutRoute` ⇐ `TillSale.record`) — همان زنجیرهٔ صندوقِ کامپیوتر: یادداشتِ فاکتور
 *  (POS-11) و مصرفِ امتیاز (PRM-13) روی همان فروشِ ثبت‌شده می‌نشینند، جمع از همان `SalesEngine` پایین می‌آید، ردیف‌های
 *  امتیاز در همان دفتر (همگام با بقیهٔ دفتر) و رسید هر دو را نشان می‌دهد. سیم‌کشیِ صفحه: `PhoneTillWiringTest`.
 */
class TillSaleTest {
  private var seq = 0
  private val newId: () -> String = { "id${++seq}" }
  private val today = "2026-11-02"
  private val now = 1_800_000_000_000L
  private val rule = Loyalty.Rule(everyAfn = 100, pointAfn = 1, maxSharePct = 20, silverAfn = 50_000, goldAfn = 200_000)

  private val base = ShopData(
    products = listOf(
      Product(id = "p1", name = "روغن", salePrice = 1000.0, purchasePrice = 700.0, unit = "عدد"),
      Product(id = "p2", name = "برنج", salePrice = 500.0, purchasePrice = 300.0, unit = "عدد"),
    ),
    warehouseEntries = listOf(WarehouseEntry(id = "w1", productId = "p1", units = 50.0), WarehouseEntry(id = "w2", productId = "p2", units = 50.0)),
    debtors = listOf(Debtor(id = "d1", name = "نوشا")),
    //  ماندهٔ ۵۰۰ امتیاز از خریدهای پیشین
    auditLog = listOf(AuditEntry(id = "seed", type = LoyaltyPoints.EARN, refId = "old", payRef = "d1", points = 500, createdAt = 1)),
  )
  private val cart = listOf(SalesEngine.CartLine("p1", 1.0), SalesEngine.CartLine("p2", 1.0))
  private val sale = SalesEngine.Checkout(seller = "رضا", debtorId = "d1")

  private fun record(points: TillSale.Points?, note: String?, saleId: String = "s1") =
    TillSale.record(base, cart, sale, note, null, points, today, now, newId, saleId = saleId)

  private fun ok(r: SalesEngine.Result): SalesEngine.Result.Ok = r as? SalesEngine.Result.Ok ?: error("ثبت نشد: $r")

  @Test
  fun `یادداشتِ فاکتور روی فروشِ ثبت‌شده می‌نشیند و در رسید هست`() {
    val r = ok(record(null, "کادو بپیچید"))
    val s = r.data.sales.single { it.id == r.saleId }
    assertEquals("کادو بپیچید", s.note)
    assertTrue(ReceiptModel.extras(r.data, s).any { it.endsWith("کادو بپیچید") })
    //  بی یادداشت ⇒ همان فروشِ پیشین
    val plain = ok(record(null, null, saleId = "s2"))
    assertNull(plain.data.sales.single { it.id == plain.saleId }.note)
  }

  @Test
  fun `مصرفِ امتیاز جمع را از همان موتور کم می‌کند و روی همان فروش و دفتر می‌ماند`() {
    val applied = LoyaltyPoints.apply(base, cart, rule, "d1", 300)!!
    val points = TillSale.Points(on = true, rule = rule, plan = applied.plan, cart = applied.cart)
    assertEquals(applied.cart, TillSale.cart(cart, points))
    val r = ok(record(points, "با امتیاز"))
    val s = r.data.sales.single { it.id == r.saleId }
    //  همان `SalesEngine`: قیمتِ ردیف‌ها پایین آمد، نه تخفیفِ فاکتور
    assertEquals(1200.0, s.finalTotal, 0.0)
    assertEquals(0.0, s.discount, 0.0)
    assertEquals("با امتیاز", s.note)
    val rows = r.data.auditLog.filter { LoyaltyPoints.isRow(it) && it.refId == s.id }
    val spend = rows.single { it.type == LoyaltyPoints.SPEND }
    assertEquals("${s.id}-lp-s", spend.id)
    assertEquals(300L, spend.points)
    assertEquals("رضا", spend.by)
    //  کسب فقط روی ۱٬۲۰۰ ِ پرداخت‌شده؛ مانده = ۵۰۰ − ۳۰۰ + ۱۲
    assertEquals(12L, rows.single { it.type == LoyaltyPoints.EARN }.points)
    assertEquals(212L, LoyaltyPoints.balance(r.data, "d1"))
    //  رسید: «مصرفِ امتیاز (۳۰۰)» و «کسبِ امتیاز (۱۲)» و یادداشت
    assertEquals(3, ReceiptModel.extras(r.data, s).size)
  }

  @Test
  fun `بی مصرف هم امتیاز کسب می‌شود و قاعدهٔ خاموش همان فروش است`() {
    val earn = ok(record(TillSale.Points(on = true, rule = rule), null))
    assertEquals(15L, earn.data.auditLog.single { it.type == LoyaltyPoints.EARN && it.refId == earn.saleId }.points)
    val off = ok(record(TillSale.Points(on = false, rule = rule), null, saleId = "s2"))
    assertTrue(off.data.auditLog.none { LoyaltyPoints.isRow(it) && it.refId == off.saleId })
  }

  @Test
  fun `برگهٔ پرداختِ گوشی ردیفِ «مصرفِ امتیاز» را مثلِ کامپیوتر دارد`() {
    val n = CheckoutModel.numbers(base, LoyaltyPoints.apply(base, cart, rule, "d1", 300)!!.cart, CheckoutModel.start("d1", base.debtors, ""), sale)
    val rows = CheckoutModel.rows(n, null, payV2 = true, points = 300, pointsOff = 300.0)
    //  «جمع اقلام» پیش از امتیاز، سپس ردیفِ منفیِ امتیاز، و «مبلغ نهایی» همان جمعِ موتور
    assertEquals(1500.0, rows[0].amount, 0.0)
    assertEquals(-300.0, rows[1].amount, 0.0)
    assertEquals(1200.0, rows.last().amount, 0.0)
    assertTrue(CheckoutModel.heroLine(n, null, { it.toString() }, pointsOff = 300.0).contains("1500.0"))
    //  بی امتیاز ⇒ همان ردیف‌های پیشین
    assertEquals(CheckoutModel.rows(n, null, payV2 = true), CheckoutModel.rows(n, null, payV2 = true, points = 0, pointsOff = 0.0))
  }

  @Test
  fun `برنامهٔ امتیاز بی سبدِ پس از امتیاز یا با قاعدهٔ خاموش ثبت نمی‌شود`() {
    val applied = LoyaltyPoints.apply(base, cart, rule, "d1", 300)!!
    //  سبدِ پایین‌نیامده با برنامهٔ مصرف ⇒ «عوض شد»؛ هیچ فروشی نمی‌نشیند
    assertTrue(record(TillSale.Points(on = true, rule = rule, plan = applied.plan), null) is SalesEngine.Result.Failed)
    assertTrue(record(TillSale.Points(on = false, rule = rule, plan = applied.plan, cart = applied.cart), null) is SalesEngine.Result.Failed)
  }
}
