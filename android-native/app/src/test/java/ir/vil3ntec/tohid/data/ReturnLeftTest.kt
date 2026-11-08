package ir.vil3ntec.tohid.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  سفرِ JB5 — ماندهٔ قابلِ برگشتِ هر قلم از دادهٔ همگام‌شده (`ReturnLeft` ⇐ `ReturnCap` ِ core).
 *
 *  دستگاهِ دیگر همان قلم را برگردانده و ردیفِ برگشتش با همگام‌سازی رسیده، ولی شمارندهٔ `returnedQty` ِ این دستگاه (ادغامِ
 *  فیلد، نه جمع) آن را نشمرده است. صفحهٔ برگشت و هر دو راهِ ثبت باید همان ردیف را ببینند و بیش از مانده نپذیرند.
 */
class ReturnLeftTest {

  private var seq = 0
  private fun newId(): String = "x${seq++}"
  private val today = "2026-11-08"
  private val now = 1_790_000_000_000L

  private val oil = Product(id = "p1", name = "روغن", unit = "عدد", purchasePrice = 400.0, salePrice = 500.0)

  /** فاکتورِ ۲ روغن به ۵۰۰ (جمع ۱۰۰۰) */
  private fun sold(): Pair<ShopData, String> {
    val d = ShopData(products = listOf(oil), warehouseEntries = listOf(WarehouseEntry(id = "w1", productId = "p1", units = 10.0)))
    val r = SalesEngine.record(d, listOf(SalesEngine.CartLine("p1", 2.0)), SalesEngine.Checkout(), today, now, ::newId) as SalesEngine.Result.Ok
    return r.data to r.saleId
  }

  /** ردیفِ برگشتی که از دستگاهِ دیگر رسیده؛ شمارندهٔ قلمِ این دستگاه دست نخورده */
  private fun fromOtherDevice(d: ShopData, saleId: String, qty: Double, amount: Double): ShopData {
    val item = d.saleItems.single { it.saleId == saleId }
    return d.copy(saleReturns = d.saleReturns + SaleReturn(id = "other-${seq++}", saleId = saleId, saleItemId = item.id, productId = "p1", quantity = qty, amount = amount))
  }

  @Test
  fun `برگشتِ کاملِ دستگاهِ دیگر هیچ چیزی برای برگشت نمی‌گذارد`() {
    val (d0, saleId) = sold()
    val d = fromOtherDevice(d0, saleId, 2.0, 1000.0)
    val item = d.saleItems.single()
    assertEquals(0.0, item.returnedQty, 0.0)
    assertEquals(0.0, ReturnLeft.left(d, item), 0.0)
    assertEquals(2.0, ReturnLeft.returned(d, item), 0.0)
    val q = ReturnFlow.quote(d, saleId, mapOf(item.id to ReturnFlow.Pick(1.0)))
    assertTrue("$q", q is ReturnFlow.Outcome.Bad && q.code == ReturnFlow.Code.QTY_EXCEEDS)
    assertTrue(SalesEngine.recordReturn(d, saleId, mapOf(item.id to 2.0), "خراب", today, now, ::newId) is SalesEngine.Result.Failed)
  }

  @Test
  fun `برگشتِ تکه‌ایِ دستگاهِ دیگر فقط مانده را می‌گذارد و پولش را درست می‌دهد`() {
    val (d0, saleId) = sold()
    val d = fromOtherDevice(d0, saleId, 1.0, 500.0)
    val item = d.saleItems.single()
    assertEquals(1.0, ReturnLeft.left(d, item), 0.0)
    val over = ReturnFlow.quote(d, saleId, mapOf(item.id to ReturnFlow.Pick(2.0)))
    assertTrue("$over", over is ReturnFlow.Outcome.Bad && over.code == ReturnFlow.Code.QTY_EXCEEDS)
    val ok = ReturnFlow.quote(d, saleId, mapOf(item.id to ReturnFlow.Pick(1.0)))
    assertTrue("$ok", ok is ReturnFlow.Outcome.Ok)
    //  پایه همان برگشتِ دستگاهِ دیگر است، نه شمارندهٔ صفرِ این دستگاه ⇒ ۵۰۰، نه صفر
    assertEquals(500.0, (ok as ReturnFlow.Outcome.Ok).value.total, 0.0)
    //  مرجوعیِ ساده هم فقط تا مانده (۲ خواسته ⇒ ۱ ثبت)
    val r = SalesEngine.recordReturn(d, saleId, mapOf(item.id to 2.0), "خراب", today, now, ::newId) as SalesEngine.Result.Ok
    assertEquals(1.0, r.data.saleReturns.filter { !it.id.startsWith("other-") }.sumOf { it.quantity }, 0.0)
  }

  @Test
  fun `صفحهٔ برگشت آنچه پیش‌تر برگشته را نشان می‌دهد`() {
    val (d0, saleId) = sold()
    assertEquals("", ReturnLeft.note(d0, d0.saleItems.single(), "عدد"))
    val d = fromOtherDevice(d0, saleId, 1.0, 500.0)
    val note = ReturnLeft.note(d, d.saleItems.single(), "عدد")
    assertTrue(note, note.isNotEmpty() && note.contains("1") && note.contains("2"))
  }

  @Test
  fun `شمارندهٔ قلمِ همین دستگاه هم شمرده می‌شود`() {
    val (d0, saleId) = sold()
    val item = d0.saleItems.single()
    val r = SalesEngine.recordReturn(d0, saleId, mapOf(item.id to 1.0), "خراب", today, now, ::newId) as SalesEngine.Result.Ok
    val after = r.data.saleItems.single()
    assertEquals(1.0, after.returnedQty, 0.0)
    assertEquals(1.0, ReturnLeft.left(r.data, after), 0.0)
    assertEquals(1.0, ReturnLeft.returned(r.data, after), 0.0)
  }
}
