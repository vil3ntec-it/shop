package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrGoods
import ir.vil3ntec.tohid.core.i18n.StrStock
import ir.vil3ntec.tohid.ui.v3.products.ProductFormModel
import ir.vil3ntec.tohid.ui.v3.products.ProductRowDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 *  کالای تازه با «موجودی در انبار» از همان فرم (`OpeningStock.save`، ۱۴۰۵/۰۸/۲۴): یک کالا و یک ورودِ انبار با همان مقدار،
 *  واحد، فی و دو تاریخ؛ بی موجودی فقط کالا؛ یا همه یا هیچ؛ دوباره زدن چیزی نمی‌سازد؛ و خروجی **عیناً** همان زنجیرهٔ امروزِ
 *  `addProduct` ⇒ `addEntry` است (پس همگام‌سازی همان opهای «کالای تازه» و «ورودِ کالا» را می‌بیند).
 */
class OpeningStockTest {

  private var seq = 0
  private fun newId(): String = "o${seq++}"
  private val today = "2026-11-15"
  private val base = ShopData(productCategories = listOf("روغن"), productUnits = listOf("عدد", "کارتن"))

  @Before fun fa() { I18n.source = { Lang.FA } }

  private fun ok(r: WarehouseEngine.Result): ShopData = (r as? WarehouseEngine.Result.Ok)?.data ?: error("رد شد: $r")
  private fun failed(r: WarehouseEngine.Result): String = (r as? WarehouseEngine.Result.Failed)?.message ?: error("پذیرفته شد")

  private val oil = WarehouseEngine.ProductDraft(name = "روغن آفتاب ۵ لیتر", category = "روغن", unit = "کارتن", purchasePrice = 2400.0, salePrice = 2650.0, barcode = "6260011")
  private val twoCartons = OpeningStock.Stock(qty = 2.0, productionDate = "2026-09-01", expiryDate = "2027-03-01")

  @Test
  fun `کالا با موجودی ⇒ دقیقاً یک کالا و یک ورودِ انبار با همان مقدار، واحد، فی و تاریخ‌ها`() {
    val out = ok(OpeningStock.save(base, listOf(OpeningStock.Row(oil, twoCartons)), today, 7L, ::newId))
    val p = out.products.single()
    assertEquals("روغن آفتاب ۵ لیتر", p.name)
    assertEquals(listOf("6260011"), p.barcodes)
    val e = out.warehouseEntries.single()
    assertEquals(p.id, e.productId)
    assertEquals(2.0, e.units, 0.0)
    assertEquals("کارتن", e.unit)
    assertEquals(2400.0, e.price, 0.0)
    assertEquals("2026-09-01", e.productionDate)
    assertEquals("2027-03-01", e.expiryDate)
    assertEquals(today, e.date)
    assertEquals(OpeningStock.NOTE, e.notes)
    //  همان حرکتِ «ورودِ کالا»: یک `purchase_in` به همان مقدار، سرِ همان ردیف
    val m = out.stockMovements.single()
    assertEquals("purchase_in", m.type)
    assertEquals(e.id, m.refId)
    assertEquals(2.0, m.qty, 0.0)
    assertEquals(2.0, ShopStore.stock(out, p.id), 0.0)
  }

  @Test
  fun `بی موجودی فقط کالا ساخته می‌شود`() {
    val out = ok(OpeningStock.save(base, listOf(OpeningStock.Row(oil)), today, 7L, ::newId))
    assertEquals(1, out.products.size)
    assertTrue(out.warehouseEntries.isEmpty())
    assertTrue(out.stockMovements.isEmpty())
    assertEquals(0.0, ShopStore.stock(out, out.products.single().id), 0.0)
  }

  @Test
  fun `خروجی عیناً همان زنجیرهٔ امروزِ ثبتِ کالا و ورودِ کالاست`() {
    seq = 0
    val mine = ok(OpeningStock.save(base, listOf(OpeningStock.Row(oil, twoCartons)), today, 7L, ::newId))
    seq = 0
    val made = WarehouseEngine.addProduct(base, oil, 7L, ::newId) as WarehouseEngine.Result.Ok
    val entry = WarehouseEngine.EntryDraft(
      productId = made.id, units = 2.0, unit = "کارتن", price = 2400.0, notes = OpeningStock.NOTE,
      expiryDate = "2027-03-01", productionDate = "2026-09-01",
    )
    val chain = ok(WarehouseEngine.addEntry(made.data, entry, today, 7L, ::newId))
    assertEquals(chain, mine)
  }

  @Test
  fun `دوباره زدنِ ذخیره هیچ کالا یا موجودیِ دومی نمی‌سازد`() {
    val rows = listOf(OpeningStock.Row(oil, twoCartons))
    val once = ok(OpeningStock.save(base, rows, today, 7L, ::newId))
    val again = OpeningStock.save(once, rows, today, 8L, ::newId)
    assertEquals(StrGoods.rowWhy("1", StrGoods.nameTaken(oil.name)), failed(again))
    assertEquals(1, once.products.size)
    assertEquals(1, once.warehouseEntries.size)
    assertEquals(1, once.stockMovements.size)
  }

  @Test
  fun `یا همه یا هیچ — ردیفِ دومِ خراب ردیفِ نخست را هم نمی‌نویسد`() {
    val tea = WarehouseEngine.ProductDraft(name = "چای", category = "روغن", unit = "عدد", purchasePrice = 80.0, salePrice = 100.0)
    //  تاریخِ تولید پس از انقضا: همان قاعدهٔ «ورودِ کالا» ِ موتور
    val bad = OpeningStock.Stock(qty = 3.0, productionDate = "2027-05-01", expiryDate = "2027-01-01")
    val r = OpeningStock.save(base, listOf(OpeningStock.Row(oil, twoCartons), OpeningStock.Row(tea, bad)), today, 7L, ::newId)
    assertEquals(StrGoods.rowWhy("2", StrGoods.madeAfterExpiry), failed(r))
    //  بارکدِ تکراریِ درونِ همین دسته هم (ردیفِ دوم روی خروجیِ ردیفِ نخست سوار است)
    val twin = tea.copy(barcode = "6260011")
    assertEquals(StrGoods.rowWhy("2", StrGoods.barcodeTaken), failed(OpeningStock.save(base, listOf(OpeningStock.Row(oil), OpeningStock.Row(twin)), today, 7L, ::newId)))
    //  تاریخِ ناخوانا هم (`ExpiryEngine.validDate`)
    val r2 = OpeningStock.save(base, listOf(OpeningStock.Row(tea, OpeningStock.Stock(1.0, expiryDate = "?"))), today, 7L, ::newId)
    assertEquals(StrGoods.rowWhy("1", StrStock.errExpiry), failed(r2))
    assertTrue(OpeningStock.save(base, emptyList(), today, 7L, ::newId) is WarehouseEngine.Result.Failed)
  }

  @Test
  fun `از فرم تا دفتر — دو کارتن با تاریخِ خورشیدی، و ردیفِ بی موجودی در همان ذخیره`() {
    val oilRow = ProductRowDraft(key = 0, name = "روغن آفتاب ۵ لیتر", category = "روغن", unit = "کارتن", purchase = "2400", sale = "2650",
      stock = "2", made = "14050610", expiry = "14051210")
    val teaRow = ProductRowDraft(key = 1, name = "چای سبز", category = "روغن", unit = "عدد", purchase = "80", sale = "100")
    val check = ProductFormModel.check(listOf(oilRow, teaRow)) as ProductFormModel.Check.Ok
    assertEquals(OpeningStock.Stock(2.0, productionDate = "2026-09-01", expiryDate = "2027-03-01"), check.rows[0].stock)
    assertNull(check.rows[1].stock)
    //  «حداقلِ موجودی» و «قیمتِ عمده» در فرمِ تازه نیستند ⇒ ۰ و null (همان کالای تازهٔ پیشین)
    assertTrue(check.drafts.all { it.minStock == 0.0 && it.wholesalePrice == null })
    val out = ok(OpeningStock.save(base, check.rows, today, 7L, ::newId))
    assertEquals(2, out.products.size)
    val e = out.warehouseEntries.single()
    assertEquals(out.products.single { it.name.startsWith("روغن") }.id, e.productId)
    assertEquals("2027-03-01", e.expiryDate)
    assertEquals(0.0, out.products.single { it.name == "چای سبز" }.wholesalePrice, 0.0)
    //  واحدِ تازهٔ فرم (و دسته) مثلِ همیشه به فهرستِ دکان می‌رود
    assertTrue("کارتن" in out.productUnits && "روغن" in out.productCategories)
  }
}
