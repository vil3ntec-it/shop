package ir.vil3ntec.tohid.desktop

import ir.vil3ntec.tohid.core.sale.SaleTax
import ir.vil3ntec.tohid.core.staff.LineSeller
import ir.vil3ntec.tohid.data.CashRoundPref
import ir.vil3ntec.tohid.data.Debtor
import ir.vil3ntec.tohid.data.LineSellerPref
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.ReturnFlow
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.SaleTaxPref
import ir.vil3ntec.tohid.data.SaleTaxWiring
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.TillGuardsPref
import ir.vil3ntec.tohid.data.WarehouseEntry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 *  RET-05 — فاکتورِ «تعویض» همان فاکتورِ صندوقِ همین دستگاه است، از تنظیمِ واقعیِ دستگاه (`TillGuardsPref.get`، همان خوانندهٔ
 *  `DeskSale` و `CheckoutRoute`): POS-37 مالیاتِ فاکتور و PAY-09 گردِ نقدیِ **تفاوتی که مشتری نقد می‌دهد**؛ فروشندهٔ ردیف (POS-23)
 *  بی تغییر همان فروشندهٔ فاکتور است (شاهد، نه کمبود).
 *  ⛔ روی `claude/batch-57` (e69b30114) فروشِ تعویض نه مالیات داشت نه گرد: تعویضِ همان کالا با مالیاتِ ۱۰٪ ِ روشن ۱۰۰ افغانی به
 *  مشتری پس می‌داد (برگشت سهمِ مالیات را پس می‌دهد، فروشِ تازه نمی‌گرفت) و تفاوتِ ۲۱ افغانیِ نقد با پلهٔ ۵ گرد نمی‌شد.
 */
class ExchangeTillPricingTest {

  private val ctx = DesktopContext
  private var seq = 0
  private fun newId() = "xp-${seq++}"
  private val today = "2026-11-02"
  private val me = "صندوقدار"

  private val shoe = Product(id = "xp-shoe", name = "کفش", unit = "جفت", salePrice = 1000.0)
  private val belt = Product(id = "xp-belt", name = "کمربند", unit = "عدد", salePrice = 1002.0)
  private val bag = Product(id = "xp-bag", name = "کیف", unit = "عدد", salePrice = 1023.0)
  private val sock = Product(id = "xp-sock", name = "جوراب", unit = "عدد", salePrice = 977.0)
  private val d0 = ShopData(
    products = listOf(shoe, belt, bag, sock),
    warehouseEntries = listOf(shoe, belt, bag, sock).map { WarehouseEntry(id = "w-${it.id}", productId = it.id, units = 9.0) },
    debtors = listOf(Debtor(id = "xp-d1", name = "احمد")),
  )

  private fun clean() {
    SaleTaxPref.set(ctx, null)
    SaleTaxWiring.serverFlag = false
    CashRoundPref.set(ctx, 0L)
    LineSellerPref.set(ctx, false)
  }

  @Before fun up() = clean()

  @After fun down() = clean()

  private fun line(p: Product) = listOf(SalesEngine.CartLine(p.id, 1.0))

  /** فاکتورِ صندوق همان‌طور که `CheckoutRoute`/`DeskSale` با نقدِ کامل می‌سازند: مالیات و پلهٔ گردِ همین دستگاه */
  private fun till(d: ShopData, cart: List<SalesEngine.CartLine>, debtor: String? = null): Pair<ShopData, Sale> {
    val c = SalesEngine.Checkout(seller = me, debtorId = debtor, cashRounding = CashRoundPref.step(ctx), tax = SaleTaxPref.active(ctx))
    val out = (SalesEngine.record(d, cart, c, today, 1L, ::newId) as SalesEngine.Result.Ok).data
    return out to out.sales.last()
  }

  /** برگشتِ کلِ [sale] و فروشِ [cart] در یک سند، با سدهای صندوقِ همین دستگاه (همان دو برگهٔ برگشت) */
  private fun exchange(d: ShopData, sale: Sale, cart: List<SalesEngine.CartLine>, mode: String = ReturnFlow.ORIGINAL, credit: String? = null): Pair<ReturnFlow.Exchanged, Sale> {
    val item = d.saleItems.single { it.saleId == sale.id }
    val req = ReturnFlow.Request(
      docId = newId(), saleId = sale.id, picks = mapOf(item.id to ReturnFlow.Pick(1.0)), reason = "اندازه", mode = mode, customerId = credit, by = me,
    )
    val c = TillGuardsPref.get(ctx).exchange(d, me, sale.debtorId ?: credit, null)
    val r = ReturnFlow.exchange(d, req, cart, c, newId(), today, 2L, ::newId)
    val ok = (r as? ReturnFlow.Outcome.Ok)?.value ?: throw AssertionError("تعویض ثبت نشد: $r")
    return ok to ok.data.sales.single { it.id == ok.saleId }
  }

  @Test fun theExchangeInvoiceCarriesTheTillTaxSoASwapOfTheSameItemCostsNothing() {
    SaleTaxWiring.serverFlag = true
    SaleTaxPref.set(ctx, SaleTax.Config(SaleTax.Mode.INVOICE, 1_000)) // ۱۰٪
    val (d, sold) = till(d0, line(shoe))
    assertEquals("صندوق: ۱۰۰۰ + ۱۰۰ مالیات", 10_000L, sold.taxMinor)

    //  همان کفش دوباره: برگشت ۱۱۰۰ (با سهمِ مالیات) و فاکتورِ تعویض همان ۱۱۰۰ ِ صندوق ⇒ هیچ پولی جابه‌جا نمی‌شود
    val (ex, sale) = exchange(d, sold, line(shoe))
    val (_, tillAgain) = till(d, line(shoe))
    assertEquals(1_100.0, ex.returned, 0.0)
    assertEquals("مالیاتِ فاکتورِ تعویض = مالیاتِ صندوق", tillAgain.taxMinor, sale.taxMinor)
    assertEquals(tillAgain.finalTotal, sale.finalTotal, 0.0)
    assertEquals("تعویضِ هم‌کالا تفاوت ندارد", 0.0, ex.due, 0.0)

    //  کالای ارزان‌تر: ۹۷۷ + ۹۸ (۹۷٫۷ نیمه رو به بالا) ⇒ پس‌دادنی ۱۱۰۰ − ۱۰۷۵ = ۲۵
    val (cheaper, cheap) = exchange(d, sold, line(sock))
    assertEquals(9_800L, cheap.taxMinor)
    assertEquals(-25.0, cheaper.due, 0.0)
  }

  @Test fun theCashTheCustomerHandsOverForTheDifferenceIsRoundedLikeTheTill() {
    //  فاکتورِ اصل پیش از روشن شدنِ گرد: ۱۰۰۲ ِ نقد ⇒ برگشتِ نقدِ ۱۰۰۲ (مضربِ ۵ نیست)
    val (d, sold) = till(d0, line(belt))
    assertNull(sold.roundingMinor)
    CashRoundPref.set(ctx, 500L)

    //  کیفِ ۱۰۲۳: مشتری ۲۱ نقد می‌دهد ⇒ ۲۰ (ردیفِ گردِ −۱ روی فاکتورِ تعویض)، نه گردِ کلِ فاکتور (۱۰۲۵ ⇒ تفاوتِ ۲۳)
    val (up, upSale) = exchange(d, sold, line(bag))
    assertEquals(-100L, upSale.roundingMinor)
    assertEquals(1_022.0, upSale.finalTotal, 0.0)
    assertEquals(20.0, up.due, 0.0)

    //  پس‌دادنی هرگز گرد نمی‌شود (برگشت گرد ندارد): ۹۷۷ − ۱۰۰۲ ⇒ دقیقاً ۲۵ به مشتری
    val (down, downSale) = exchange(d, sold, line(sock))
    assertNull(downSale.roundingMinor)
    assertEquals(-25.0, down.due, 0.0)
  }

  @Test fun anExchangeWithNoCashBackIsRoundedExactlyLikeACashTillSale() {
    CashRoundPref.set(ctx, 500L)
    val (d, sold) = till(d0, line(shoe), debtor = "xp-d1")
    //  برگشت به «اعتبارِ فروشگاه» ⇒ نقدی پس داده نمی‌شود و کلِ فاکتورِ تعویض نقد است ⇒ همان گردِ صندوق برای ۱۰۲۳
    val (ex, sale) = exchange(d, sold, line(bag), mode = ReturnFlow.STORE_CREDIT, credit = "xp-d1")
    val (_, tillBag) = till(d, line(bag))
    assertEquals(200L, tillBag.roundingMinor)
    assertEquals(tillBag.roundingMinor, sale.roundingMinor)
    assertEquals(1_025.0, ex.due, 0.0)
  }

  @Test fun theExchangeIsBitForBitTheSameWhileTaxAndRoundingAreOff() {
    val (d, sold) = till(d0, line(belt))
    val (ex, sale) = exchange(d, sold, line(bag))
    assertNull(sale.taxMinor)
    assertNull(sale.roundingMinor)
    assertEquals(1_023.0, sale.finalTotal, 0.0)
    assertEquals(21.0, ex.due, 0.0)
  }

  @Test fun theLineSellerOfAnExchangeIsItsInvoiceSellerWithTheSettingOnOrOff() {
    //  شاهد (نه کمبود): سبدِ تعویض هیچ فروشندهٔ ردیفی ندارد ⇒ قلم‌ها بی `seller` و در گزارش/پورسانت به نامِ فروشندهٔ فاکتور،
    //  همان فاکتورِ صندوقی که ردیفش به کسی سپرده نشده (`LineSeller.stored`)
    for (on in listOf(false, true)) {
      LineSellerPref.set(ctx, on)
      val (d, sold) = till(d0, line(belt))
      val (ex, sale) = exchange(d, sold, line(bag))
      val items = ex.data.saleItems.filter { it.saleId == ex.saleId }
      assertTrue("بی فروشندهٔ ردیف ($on)", items.isNotEmpty() && items.all { it.seller == null })
      assertEquals(me, sale.seller)
      assertEquals(me, LineSeller.of(on, items.single().seller, sale.seller))
    }
  }
}
