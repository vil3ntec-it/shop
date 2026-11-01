package ir.vil3ntec.tohid.ui.screens

import ir.vil3ntec.tohid.data.Debtor
import ir.vil3ntec.tohid.data.ReturnFlow
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.ShopData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 *  RET-01/05 (S13/S15) — «حالتِ برگشت» ِ صندوق (Shift+F9): همان پیدا کردنِ «فاکتورها» و Enter فقط
 *  وقتی یک فاکتورِ زنده پیدا شد فرمِ برگشت را باز می‌کند.
 */
class ReturnModeTest {
  private val ahmad = Debtor(id = "d1", name = "احمد")
  private fun sale(id: String, no: Int, at: Long, status: String = "completed", debtor: String? = null) =
    Sale(id = id, invoiceNumber = no, finalTotal = 100.0, status = status, debtorId = debtor, createdAt = at, date = "2026-10-20")

  private val live = sale("s1", 154, 1_000)
  private val voided = sale("s2", 155, 2_000, status = "cancelled")
  private val older = sale("s3", 156, 3_000, debtor = "d1")
  private val newer = sale("s4", 157, 4_000, debtor = "d1")
  private val d = ShopData(debtors = listOf(ahmad), sales = listOf(live, voided, older, newer))

  @Test
  fun `کادرِ خالی هیچ`() {
    assertEquals(emptyList<Sale>(), ReturnMode.found(d, "", null))
    assertEquals(emptyList<Sale>(), ReturnMode.found(d, "   ", null))
    assertNull(ReturnMode.pick(emptyList()))
  }

  /** S13 بندِ ۱: اسکنِ QRِ رسید با اسکنر همان فاکتور است و Enter فرم را باز می‌کند */
  @Test
  fun `QRِ رسید و شمارهٔ فاکتور — با رقمِ فارسی هم — یک فاکتورِ زنده و Enter همان`() {
    assertEquals(listOf(live), ReturnMode.found(d, ReturnFlow.receiptCode(live), null))
    assertSame(live, ReturnMode.pick(ReturnMode.found(d, ReturnFlow.receiptCode(live), null)))
    assertSame(live, ReturnMode.pick(ReturnMode.found(d, "154", null)))
    assertSame(live, ReturnMode.pick(ReturnMode.found(d, "۱۵۴", null)))
  }

  @Test
  fun `فاکتورِ لغوشده پیدا می‌شود ولی Enter بازش نمی‌کند`() {
    assertEquals(listOf(voided), ReturnMode.found(d, "155", null))
    assertNull(ReturnMode.pick(ReturnMode.found(d, "155", null)))
  }

  @Test
  fun `نامِ مشتری چند فاکتور — تازه‌ترین اول و انتخاب با کاربر`() {
    val f = ReturnMode.found(d, "احمد", null)
    assertEquals(listOf(newer, older), f)
    assertNull(ReturnMode.pick(f))
  }

  @Test
  fun `فهرست کوتاه می‌ماند`() {
    val many = (1..12).map { sale("m$it", 200 + it, it * 10L, debtor = "d1") }
    val f = ReturnMode.found(ShopData(debtors = listOf(ahmad), sales = many), "احمد", null)
    assertEquals(ReturnMode.LIMIT, f.size)
    assertEquals("m12", f.first().id)
  }
}
