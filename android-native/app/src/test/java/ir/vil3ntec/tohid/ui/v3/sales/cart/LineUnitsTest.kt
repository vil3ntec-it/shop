package ir.vil3ntec.tohid.ui.v3.sales.cart

import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.ProductPack
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.WarehouseEntry
import ir.vil3ntec.tohid.qty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 *  POS-09 (S05) در صندوقِ گوشیِ نسخهٔ ۳ — برگهٔ ردیفِ سبد (`sale/line/{i}`، `LineModel`) با «واحدِ بزرگ در صندوق» ِ همین
 *  دستگاه (`TillGoodsPref.units`، پیش‌فرض خاموش): همهٔ واحدهای کالا — پایه، واحدِ فروشِ دوم و بسته‌ها — با قیمتِ لیستِ
 *  هر بسته (`ProductPack.priceMinor`)، «فروشِ بخشی از بسته مجاز نیست» (`TillGoodsPref.wholePacks`) و قیمتِ دستیِ ردیف
 *  که با عوض شدنِ واحد می‌ماند — همان قاعدهٔ `CartLineDialog` ِ رابطِ پیشین (`UnitLine` ⇐ `UnitSale`). خاموش ⇒ همان برگهٔ
 *  امروز (دو واحد).
 */
class LineUnitsTest {

  //  روغن: پایه «دانه» ۱۵۰، «کارتن = ۱۲ دانه» به ۱٬۷۰۰ در لیستِ قیمت، «بسته = ۶ دانه» بی قیمتِ لیست
  private val oil = Product(
    id = "oil", name = "روغن", unit = "دانه", salePrice = 150.0, saleUnit = "کارتن", unitFactor = 12.0,
    packs = listOf(ProductPack("کارتن", 12_000, priceMinor = 170_000), ProductPack("بسته", 6_000)),
  )
  private val d = ShopData(products = listOf(oil), warehouseEntries = listOf(WarehouseEntry(id = "w1", productId = "oil", units = 100.0)))
  private fun line(q: Double, price: Double? = null) = SalesEngine.CartLine("oil", q, unitPrice = price)
  private fun on(l: SalesEngine.CartLine, whole: Boolean = false) = LineModel.info(d, l, units = true, wholeOnly = whole)
  private fun saved(info: LineInfo, form: LineForm, start: LineForm) =
    LineModel.apply(listOf(line(info.quantity, info.unitPrice)), info, form, start).single()

  @Test fun `خاموش ⇒ همان برگهٔ امروز با دو واحد و بی بسته`() {
    val info = LineModel.info(d, line(1.0))
    assertNull(info.units)
    assertEquals(emptyList<String>(), LineModel.unitLabels(info))
    val start = LineModel.start(info)
    assertEquals(LineForm(qty(1.0), qty(150.0)), start)
    //  همان `switchUnit` ِ امروز: «۲ کارتن» = ۲۴ دانه با قیمتِ ۱۲ × دانه
    val form = LineModel.switchUnit(start, info, toSale = true).copy(qtyText = "2")
    assertEquals(24.0, LineModel.quantity(form, info)!!, 0.0)
    assertEquals("بی برنامهٔ واحد ⇒ بی‌اثر", start, LineModel.switchTo(start, info, 1))
    assertEquals(2 * 12 * 150.0, LineModel.total(form, info, start), 0.0)
  }

  @Test fun `روشن ⇒ پایه، کارتن و بسته؛ دو کارتن از لیستِ قیمت و یک بسته با ۶ × دانه`() {
    val info = on(line(1.0))
    assertEquals(listOf("دانه", "کارتن", "بسته"), info.units!!.units.map { it.name })
    assertEquals(3, LineModel.unitLabels(info).size)
    val start = LineModel.start(info)
    assertEquals(0, start.unitAt)
    //  دو کارتن ⇒ ۲۴ دانه، قیمتِ هر دانه ۱٬۷۰۰ ÷ ۱۲ ⇒ جمع ۳٬۴۰۰
    val cartons = LineModel.switchTo(start, info, 1).copy(qtyText = "2")
    assertEquals("کارتن", LineModel.shownUnit(cartons, info))
    assertEquals(24.0, LineModel.quantity(cartons, info)!!, 0.0)
    assertEquals("= ${qty(24.0)} دانه", LineModel.qtyHelper(cartons, info))
    assertEquals(3400.0, LineModel.total(cartons, info, start), 1e-6)
    val l = saved(info, cartons, start)
    assertEquals(24.0, l.quantity, 0.0)
    assertEquals(1700.0 / 12, l.unitPrice!!, 1e-9)
    assertEquals(3400.0, SalesEngine.cartTotal(d, listOf(l)), 1e-6)
    //  بستهٔ بی قیمتِ لیست ⇒ قیمتِ کالا (null) و ۶ × ۱۵۰
    val pack = LineModel.switchTo(start, info, 2)
    assertEquals("مقدار به واحدِ تازه", qty(1.0 / 6.0), pack.qtyText)
    val one = pack.copy(qtyText = "1")
    assertNull(LineModel.wantedPrice(one, info, start))
    assertEquals(900.0, LineModel.total(one, info, start), 1e-6)
    assertEquals(StrSale.packListPrice("کارتن", ir.vil3ntec.tohid.ui.components.pos.PhoneSaleText.amount(1700.0)), LineModel.packPriceText(cartons, info))
    assertNull(LineModel.packPriceText(one, info))
  }

  @Test fun `عوض کردنِ واحد همان مقدار را نگه می‌دارد و ردیفِ کارتنی با کارتن باز می‌شود`() {
    val info = on(line(1.0))
    val start = LineModel.start(info)
    val cartons = LineModel.switchTo(start.copy(qtyText = "24"), info, 1)
    assertEquals(qty(2.0), cartons.qtyText)
    assertEquals("قیمتِ نانوشته ⇒ قیمتِ لیستِ واحدِ تازه", qty(1700.0 / 12), cartons.priceText)
    val back = LineModel.switchTo(cartons, info, 0)
    assertEquals(qty(24.0), back.qtyText)
    assertEquals(qty(150.0), back.priceText)
    assertNull("دوباره دانه ⇒ قیمتِ خودِ کالا", LineModel.wantedPrice(back, info, start))
    //  ردیفی که پیش‌تر دو کارتن با قیمتِ لیست ثبت شد ⇒ برگه با «کارتن» و «۲»
    val again = on(line(24.0, 1700.0 / 12))
    val s2 = LineModel.start(again)
    assertEquals(1, s2.unitAt)
    assertEquals(qty(2.0), s2.qtyText)
    assertEquals(1700.0 / 12, LineModel.wantedPrice(s2, again, s2)!!, 1e-9)
    assertNull("قیمتِ لیست دستی شمرده نمی‌شود", LineModel.wantedPrice(LineModel.switchTo(s2, again, 0), again, s2))
  }

  @Test fun `قیمتِ دستیِ ردیف و قیمتِ نوشته با عوض شدنِ واحد می‌مانند؛ «برگشت» ⇒ قیمتِ لیستِ همان واحد`() {
    val info = on(line(1.0, 120.0))
    val start = LineModel.start(info)
    val cartons = LineModel.switchTo(start, info, 1).copy(qtyText = "1")
    assertEquals(qty(120.0), cartons.priceText)
    assertEquals(120.0, LineModel.wantedPrice(cartons, info, start)!!, 0.0)
    assertEquals(12 * 120.0, LineModel.total(cartons, info, start), 1e-6)
    val typed = cartons.copy(priceText = "130", priceTouched = true)
    assertEquals(130.0, LineModel.wantedPrice(LineModel.switchTo(typed, info, 2), info, start)!!, 0.0)
    val reset = LineModel.resetPrice(cartons, info)
    assertEquals(qty(1700.0 / 12), reset.priceText)
    assertEquals(1700.0 / 12, LineModel.wantedPrice(reset, info, start)!!, 1e-9)
    assertNull("برگشت روی دانه ⇒ قیمتِ کالا", LineModel.wantedPrice(LineModel.switchTo(reset, info, 0), info, start))
  }

  @Test fun `فقط بستهٔ کامل ⇒ ۱٫۵ کارتن رد با نامِ واحد؛ دانه آزاد؛ صفر و نانوشته همان خطاهای امروز`() {
    val info = on(line(1.0), whole = true)
    val start = LineModel.start(info)
    val half = LineModel.switchTo(start, info, 1).copy(qtyText = "1.5")
    assertNull(LineModel.quantity(half, info))
    assertEquals(StrSale.partPackRejected("کارتن"), LineModel.qtyError(half, info))
    assertEquals(false, LineModel.valid(half, info))
    assertNull("دانه ۱٫۵ (کالای غیرِ وزنی را موتور می‌سنجد)", LineModel.qtyError(start.copy(qtyText = "1.5"), info))
    assertEquals(StrSale.positiveNeeded, LineModel.qtyError(half.copy(qtyText = "0"), info))
    assertEquals(StrSale.badNumberShort, LineModel.qtyError(half.copy(qtyText = "x"), info))
    val free = on(line(1.0), whole = false)
    val ok = LineModel.switchTo(LineModel.start(free), free, 1).copy(qtyText = "1.5")
    assertEquals(18.0, LineModel.quantity(ok, free)!!, 0.0)
  }

  @Test fun `قلمِ آزاد و کالای تک‌واحد ⇒ بی برنامهٔ واحد`() {
    assertNull(LineModel.info(d, SalesEngine.CartLine("free-1", 1.0, unitPrice = 50.0, label = "خدمت"), units = true).units)
    val salt = Product(id = "salt", name = "نمک", unit = "عدد", salePrice = 20.0)
    assertNull(LineModel.info(d.copy(products = listOf(salt)), SalesEngine.CartLine("salt", 1.0), units = true).units)
  }
}
