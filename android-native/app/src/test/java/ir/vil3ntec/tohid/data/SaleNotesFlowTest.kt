package ir.vil3ntec.tohid.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  POS-11 — یادداشتِ ردیف و فاکتور از سبد تا فاکتورِ ثبت‌شده، و با پیش‌نویس/فاکتورِ نگه‌داشته.
 *  فاکتورِ بی یادداشت باید همان JSON ِ امروز باشد (فیلد نوشته نمی‌شود).
 */
class SaleNotesFlowTest {

  private var seq = 0
  private fun newId(): String = "g${seq++}"

  private val rice = Product(id = "p1", name = "برنج", unit = "کیلو", purchasePrice = 200.0, salePrice = 300.0)
  private val tea = Product(id = "p2", name = "چای", unit = "بسته", purchasePrice = 50.0, salePrice = 80.0)

  private fun ledger() = ShopData(
    products = listOf(rice, tea),
    warehouseEntries = listOf(
      WarehouseEntry(id = "w1", productId = "p1", units = 100.0),
      WarehouseEntry(id = "w2", productId = "p2", units = 100.0),
    ),
  )

  private fun sell(cart: List<SalesEngine.CartLine>, note: String? = null) =
    SalesEngine.record(ledger(), cart, SalesEngine.Checkout(note = note), "2026-11-01", 1_700_000_000_000, ::newId) as SalesEngine.Result.Ok

  @Test
  fun `یادداشتِ ردیف و فاکتور تمیز روی فاکتور می‌نشیند`() {
    val cart = CartNotes.set(listOf(SalesEngine.CartLine("p1", 2.0), SalesEngine.CartLine("p2", 1.0)), "p1", "  بدون\nسنگ  ")
    val r = sell(cart, note = " تحویل  ساعت ۵ ")
    assertEquals("تحویل ساعت ۵", r.data.sales.single().note)
    val byProduct = r.data.saleItems.associateBy { it.productId }
    assertEquals("بدون سنگ", byProduct.getValue("p1").note)
    assertNull(byProduct.getValue("p2").note)
  }

  @Test
  fun `بی یادداشت — همان JSON ِ امروز، بی کلیدِ note`() {
    val r = sell(listOf(SalesEngine.CartLine("p1", 2.0)), note = "   ")
    val json = Json { encodeDefaults = true }
    assertFalse(json.encodeToString(r.data.sales.single()).contains("\"note\""))
    assertFalse(json.encodeToString(r.data.saleItems.single()).contains("\"note\""))
  }

  @Test
  fun `پیش‌نویس و فاکتورِ نگه‌داشته یادداشت‌ها را نگه می‌دارند؛ سبدِ خالی یادداشت ندارد`() {
    val cart = CartNotes.set(listOf(SalesEngine.CartLine("p1", 2.0)), "p1", "خرد شود")
    val d = SaleDraft.of("K1", cart, null, 1L, note = "برای همسایه")
    val back = SaleDraftCodec.decode(SaleDraftCodec.encode(d))!!
    assertEquals("برای همسایه", back.note)
    assertEquals("خرد شود", back.cart().single().note)
    assertNull(SaleDraft.of(null, emptyList(), null, 1L, note = "برای همسایه").note)
    //  پیش‌نویسِ پیش از POS-11 هم خوانده می‌شود
    val old = SaleDraftCodec.decode("""{"v":2,"key":"K0","lines":[{"productId":"p1","quantity":1.0}]}""")!!
    assertNull(old.note)
    assertNull(old.cart().single().note)
  }

  @Test
  fun `CartNotes فقط همان ردیف را عوض می‌کند و بی تغییر همان سبد را برمی‌گرداند`() {
    val cart = listOf(SalesEngine.CartLine("p1", 2.0), SalesEngine.CartLine("p2", 1.0, note = "قبلی"))
    val next = CartNotes.set(cart, "p1", "تازه")
    assertEquals("تازه", next[0].note)
    assertEquals("قبلی", next[1].note)
    assertSame(next, CartNotes.set(next, "p1", " تازه "))
    assertNull(CartNotes.set(next, "p2", "  ")[1].note)
    assertTrue(CartNotes.set(cart, "nope", "x") === cart)
  }
}
