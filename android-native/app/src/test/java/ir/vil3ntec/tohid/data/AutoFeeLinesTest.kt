package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.sale.AutoFees
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  POS-36 — هزینهٔ خودکار در سبد و فاکتور: بی قاعده همان سبد؛ با قاعده قلمِ آزادِ «fee:…» ته سبد، با هر
 *  تغییر از نو، و در ثبت مثلِ قلمِ آزاد (جمع، رسید، بی اثر بر موجودی).
 */
class AutoFeeLinesTest {

  private var seq = 0
  private fun newId(): String = "i${seq++}"

  private val rice = Product(id = "rice", name = "برنج", unit = "کیلوگرم", salePrice = 80.0)
  private val d = ShopData(products = listOf(rice), warehouseEntries = listOf(WarehouseEntry(id = "w", productId = "rice", units = 10.0)))
  private val rules = listOf(AutoFees.Rule("کیسه", 500), AutoFees.Rule("بسته‌بندی", 200, listOf("rice")))

  @Test
  fun `بی قاعده همان سبد و همان شیء`() {
    val cart = listOf(SalesEngine.CartLine("rice", 2.0))
    assertSame(cart, AutoFeeLines.apply(cart, emptyList()))
  }

  @Test
  fun `هزینه‌ها ته سبد می‌آیند و با سبد عوض می‌شوند`() {
    val one = AutoFeeLines.apply(listOf(SalesEngine.CartLine("rice", 3.0)), rules)
    assertEquals(listOf("rice", "fee:1", "fee:2"), one.map { it.productId })
    assertEquals(5.0, one[1].unitPrice!!, 0.0)
    assertEquals(3.0, one[2].quantity, 0.0)
    assertTrue(one.drop(1).all { AutoFeeLines.isFee(it) })
    //  مقدار عوض شد ⇒ هزینهٔ ردیف هم؛ همان سبد دوباره ⇒ همان شیء
    val two = AutoFeeLines.apply(one.map { if (it.productId == "rice") it.copy(quantity = 1.0) else it }, rules)
    assertEquals(1.0, two.last().quantity, 0.0)
    assertSame(two, AutoFeeLines.apply(two, rules))
    //  کالا برداشته شد ⇒ هزینه هم
    assertEquals(emptyList<SalesEngine.CartLine>(), AutoFeeLines.apply(two.filterNot { it.productId == "rice" }, rules))
  }

  @Test
  fun `فاکتور هزینه را مثلِ قلمِ آزاد ثبت می‌کند`() {
    val cart = AutoFeeLines.apply(listOf(SalesEngine.CartLine("rice", 3.0)), rules)
    val ok = SalesEngine.record(d, cart, SalesEngine.Checkout(), "2026-11-01", 1_700_000_000_000, ::newId) as SalesEngine.Result.Ok
    //  ۳ × ۸۰ + ۵ + ۳ × ۲
    assertEquals(251.0, ok.data.sales.single().finalTotal, 1e-9)
    val fee = ok.data.saleItems.first { it.name == "بسته‌بندی" }
    assertEquals("", fee.productId)
    assertEquals(6.0, fee.totalPrice, 1e-9)
    assertEquals(7.0, ShopStore.stock(ok.data, "rice"), 1e-9)
  }

  @Test
  fun `حذفِ هزینهٔ خودکار ردِ حذفِ قلم نیست`() {
    val before = AutoFeeLines.apply(listOf(SalesEngine.CartLine("rice", 3.0)), rules)
    val gone = FraudTrail.removed(before, emptyList()).filterNot(AutoFeeLines::isFee)
    assertEquals(listOf("rice"), gone.map { it.productId })
  }
}
