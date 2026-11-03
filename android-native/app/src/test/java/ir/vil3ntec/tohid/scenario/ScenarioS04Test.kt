package ir.vil3ntec.tohid.scenario

import ir.vil3ntec.tohid.core.hw.WeightBarcode
import ir.vil3ntec.tohid.core.money.Rounding
import ir.vil3ntec.tohid.data.ProductInfoMap
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ScaleLabels
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.WarehouseEntry
import ir.vil3ntec.tohid.ui.components.pos.PosHit
import ir.vil3ntec.tohid.ui.components.pos.PosSearch
import ir.vil3ntec.tohid.ui.v3.sales.cart.LineModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToLong

/**
 *  S04 ⌨ — کالای وزنی (POS-29، PRD-08)، سر تا ته روی همان راهِ صندوقِ کامپیوتر (`DeskSale.submit`):
 *  کادرِ اسکن ⇐ برچسبِ ترازو (`ScaleLabels.match`، قاعدهٔ «21» + کدِ کالا + وزن + رقمِ کنترل) یا «۰٫۷۵*کد» (`PosSearch.resolve`)
 *  ⇐ `SalesEngine.addToCartCapped` ⇐ جمع ⇐ `SalesEngine.record` (فاکتور، اقلام، موجودی). در گوشی همان وزنِ دستی از برگهٔ ردیف
 *  (`LineModel`). قاعدهٔ برچسبِ ترازو در برنامه پیش‌فرض خاموش است (`ScaleLabels.rules` خالی).
 *
 *  پیش‌تر فقط خواندنِ برچسب سنجیده می‌شد (`ScaleLabelsTest`)؛ «وزنِ دستیِ 0.75 و گردکردنِ مبلغ» آزمونی نداشت.
 *  گردکردن (§۳.۴): مبلغِ هر ردیف همان لحظه به افغانیِ کامل، نیمه رو به بالا — همان `Rounding.lineTotalMinor` ِ هسته
 *  (پلهٔ ۱۰۰ واحدِ کوچک). وزن‌ها این‌جا کسرِ دودوییِ دقیق‌اند (۱٫۲۵، ۰٫۷۵)، پس موتورِ Double ِ امروز و هسته یکی‌اند؛
 *  تفاوتِ شناختهٔ نیمه‌های نادقیق (`Rounding` بالای پرونده) تصمیمِ جداست و این‌جا سنجیده نمی‌شود.
 */
class ScenarioS04Test {

  private var seq = 0
  private fun newId() = "s04-${seq++}"

  /** برنجِ کیلویی ۸۷ با کدِ ترازوی 00150؛ شکرِ کیلویی ۷۰ بی برچسب (بارکدِ بسته‌اش برای «۰٫۷۵*») */
  private val rice = Product(id = "rice", name = "برنج", unit = "کیلو", salePrice = 87.0, barcodes = listOf("00150"))
  private val sugar = Product(id = "sugar", name = "شکر", unit = "کیلو", salePrice = 70.0, barcodes = listOf("6260000000750"))
  private val d = ShopData(
    products = listOf(rice, sugar),
    warehouseEntries = listOf(WarehouseEntry(id = "w1", productId = "rice", units = 50.0), WarehouseEntry(id = "w2", productId = "sugar", units = 20.0)),
  )
  private val index = ShopStore.barcodeIndex(d)
  private val rules = listOf(WeightBarcode.Rule(prefix = "21"))

  /** همان `DeskSale.submit`: اول برچسبِ ترازو، وگرنه `PosSearch`؛ برچسبِ ردشده ⇒ پیام و سبدِ دست‌نخورده */
  private fun submit(cart: List<SalesEngine.CartLine>, query: String): Pair<List<SalesEngine.CartLine>, String?> {
    when (val m = ScaleLabels.match(rules, query, index, d.products)) {
      is ScaleLabels.Match.Weighed ->
        return SalesEngine.addToCartCapped(cart, m.product.id, m.quantity, ProductInfoMap.sellable(d, m.product.id)).cart to null
      is ScaleLabels.Match.Rejected -> return cart to m.message
      null -> Unit
    }
    val hit = PosSearch.resolve(query, index, d.products, emptyList(), 0)
    assertTrue("$query ⇐ $hit", hit is PosHit.Add)
    hit as PosHit.Add
    return SalesEngine.addToCartCapped(cart, hit.product.id, hit.count, ProductInfoMap.sellable(d, hit.product.id)).cart to null
  }

  /** قاعدهٔ §۳.۴ از هسته: مقدار به هزارم، قیمت به واحدِ کوچک، پلهٔ AFN = ۱۰۰ */
  private fun coreLine(qty: Double, price: Double): Double =
    Rounding.lineTotalMinor((qty * 1000).roundToLong(), (price * 100).roundToLong(), 100) / 100.0

  @Test
  fun `S04 — برچسبِ ۱٫۲۵۰ کیلو برنج و ۰٫۷۵ کیلو شکرِ دستی — وزن، مبلغِ گردشدهٔ هر ردیف، جمع، فاکتور و موجودی`() {
    var cart = emptyList<SalesEngine.CartLine>()
    cart = submit(cart, "2100150012507").first
    //  وزنِ دستی با چیدمانِ فارسی: «۰٫۷۵*» پیش از بارکدِ شکر
    cart = submit(cart, "۰٫۷۵*6260000000750").first
    assertEquals(listOf("rice" to 1.25, "sugar" to 0.75), cart.map { it.productId to it.quantity })

    //  ۱٫۲۵ × ۸۷ = ۱۰۸٫۷۵ ⇐ ۱۰۹؛ ۰٫۷۵ × ۷۰ = ۵۲٫۵ ⇐ ۵۳ (نیمه رو به بالا)
    assertEquals(109.0, SalesEngine.lineTotal(87.0, 1.25), 0.0)
    assertEquals(53.0, SalesEngine.lineTotal(70.0, 0.75), 0.0)
    assertEquals(coreLine(1.25, 87.0), SalesEngine.lineTotal(87.0, 1.25), 0.0)
    assertEquals(coreLine(0.75, 70.0), SalesEngine.lineTotal(70.0, 0.75), 0.0)
    assertEquals(162.0, SalesEngine.cartTotal(d, cart), 0.0)

    val r = SalesEngine.record(d, cart, SalesEngine.Checkout(), "2026-11-03", 1_000, ::newId)
    assertTrue(r.toString(), r is SalesEngine.Result.Ok)
    val after = (r as SalesEngine.Result.Ok).data
    val sale = after.sales.single()
    assertEquals(162.0, sale.total, 0.0)
    assertEquals(162.0, sale.finalTotal, 0.0)
    val items = after.saleItems.filter { it.saleId == sale.id }.associateBy { it.productId }
    assertEquals(1.25, items.getValue("rice").quantity, 0.0)
    assertEquals(109.0, items.getValue("rice").totalPrice, 0.0)
    assertEquals(0.75, items.getValue("sugar").quantity, 0.0)
    assertEquals(53.0, items.getValue("sugar").totalPrice, 0.0)
    assertEquals(sale.total, items.values.sumOf { it.totalPrice }, 0.0)
    //  موجودی به همان وزن کم شد
    assertEquals(48.75, ShopStore.stock(after, "rice"), 1e-9)
    assertEquals(19.25, ShopStore.stock(after, "sugar"), 1e-9)
  }

  @Test
  fun `S04 — رقمِ کنترلِ غلط ⇒ برچسب رد و سبد دست‌نخورده، نه «کالای ناشناس»`() {
    val cart = listOf(SalesEngine.CartLine("sugar", 0.75))
    val (after, message) = submit(cart, "2100150012508")
    assertNotNull(message)
    assertTrue(after === cart)
  }

  @Test
  fun `S04 — گوشی — وزنِ دستیِ «۰٫۷۵» در برگهٔ ردیف همان ردیف و همان مبلغِ صندوقِ کامپیوتر است`() {
    val line = SalesEngine.CartLine("sugar", 1.0)
    val info = LineModel.info(d, line)
    val start = LineModel.start(info)
    val form = start.copy(qtyText = "۰٫۷۵")
    assertTrue(LineModel.valid(form, info))
    assertEquals(0.75, LineModel.quantity(form, info)!!, 0.0)
    assertEquals(53.0, LineModel.total(form, info, start), 0.0)
    val cart = LineModel.apply(listOf(line), info, form, start)
    assertEquals(listOf("sugar" to 0.75), cart.map { it.productId to it.quantity })
    assertEquals(53.0, SalesEngine.cartTotal(d, cart), 0.0)
  }
}
