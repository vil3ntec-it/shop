package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.sale.PriceList
import ir.vil3ntec.tohid.ui.components.pos.repriceCart
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  POS-12/POS-13 — وفاداریِ نگه‌داشتن، بازگرداندن و بازیابی: هر فیلدِ ردیفِ سبد (`SalesEngine.CartLine`) و هر فیلدِ
 *  فاکتور که پیش‌نویس نگه می‌دارد (مشتری، کدها، یادداشتِ فاکتور، لیستِ قیمتِ دستی) بایت‌به‌بایت برمی‌گردد — حتی پس از
 *  نصبِ دوباره (دفترِ تازه از همان فایل). پیش‌تر `specialPrice` (نشانِ «قیمتِ خاص» ِ رسید)، یادداشتِ فاکتور و لیستِ
 *  عمدهٔ دستی (پولِ فاکتور!) در راه می‌افتادند. پیش‌نویس و نگه‌داشتهٔ بی این فیلدها بایت‌به‌بایت همان پیشین است.
 */
class HeldFidelityTest {

  private val cart = listOf(
    SalesEngine.CartLine(
      "p1", 2.0, unitPrice = 95.0, label = "", lotId = "lot-7", note = "بی‌نمک",
      serials = listOf("SN-1", "SN-2"), specialPrice = 95.0, seller = "زهرا",
    ),
    SalesEngine.CartLine("free-1", 1.0, unitPrice = 5.0, label = "کیسه"),
  )

  private fun draft() = SaleDraft.of("K1", cart, "d1", 42L, coupons = listOf("EID5"), note = "فاکتورِ عمده", priceList = PriceList.WHOLESALE)
  private fun bytes(d: SaleDraft) = SaleDraftCodec.encode(d.copy(savedAt = 0))

  @Test
  fun `هر فیلدِ ردیفِ سبد در پیش‌نویس جا دارد — فیلدِ تازهٔ CartLine این آزمون را سرخ می‌کند`() {
    val fields = { c: Class<*> -> c.declaredFields.filterNot { java.lang.reflect.Modifier.isStatic(it.modifiers) }.map { it.name }.toSet() }
    val line = fields(SalesEngine.CartLine::class.java)
    assertEquals(setOf("productId", "quantity", "unitPrice", "label", "lotId", "note", "serials", "specialPrice", "seller"), line)
    assertTrue(fields(SaleDraft.Line::class.java).containsAll(line))
    //  هر فیلد مقدارِ غیرِپیش‌فرض دارد ⇒ آزمون‌های پایین هیچ کدام را بی‌صدا رد نمی‌کنند
    assertTrue(cart[0].toString(), listOf(cart[0].unitPrice, cart[0].lotId, cart[0].note, cart[0].serials, cart[0].specialPrice, cart[0].seller).all { it != null })
  }

  @Test
  fun `پیش‌نویس همهٔ فیلدهای ردیف و فاکتور را برمی‌گرداند`() {
    val back = SaleDraftCodec.decode(SaleDraftCodec.encode(draft()))!!
    assertEquals(cart, back.cart())
    assertEquals(draft(), back)
    assertEquals(SaleDraftCodec.encode(draft()), SaleDraftCodec.encode(back))
    assertEquals("فاکتورِ عمده", back.note)
    assertEquals(PriceList.WHOLESALE, back.priceList)
  }

  @Test
  fun `نگه‌داشتن و بازگرداندن بایت‌به‌بایت — حتی پس از نصبِ دوباره`() = runBlocking<Unit> {
    val disk = HeldInvoicesTest.MemDisk()
    val c = HeldInvoicesTest.MemCart(disk = draft())
    val s = HeldInvoiceStore(disk, device = { "A" }, clock = { 7L })
    //  فروشندهٔ فاکتورِ اصلاح‌شده (`SaleReopen`) هم — پیش‌تر `HeldInvoice` آن را نداشت
    val extras = HeldExtras(discount = " 500 ", payment = SalesEngine.Payment.CREDIT, paid = "200", discountAmount = true, seller = "زهرا")
    s.hold(c, extras, label = "آقای کریمی", note = "منتظر")
    assertTrue(c.snapshot().lines.isEmpty())
    //  دفترِ تازه از همان فایل (بسته شدنِ برنامه، نصبِ دوباره با پشتیبانِ همان فایل)
    val again = HeldInvoiceStore(disk, device = { "A" }, clock = { 8L })
    val o = again.resume(c, "K1", HeldExtras()) as HeldInvoiceStore.Outcome.Done
    val back = c.snapshot()
    assertEquals(cart, back.cart())
    assertEquals(bytes(draft()), bytes(back))
    //  حالتِ صفحه و برچسب هم
    assertEquals(HeldExtras("500", SalesEngine.Payment.CREDIT, "200", discountAmount = true, seller = "زهرا"), o.extras)
    assertEquals(HeldTag("K1", 1, "آقای کریمی", "منتظر"), again.book.value.current)
  }

  @Test
  fun `فاکتورِ جاری که با بازگرداندنِ دیگری خودکار نگه داشته شد هم چیزی گم نمی‌کند`() = runBlocking<Unit> {
    val disk = HeldInvoicesTest.MemDisk()
    val other = SaleDraft.of("K0", listOf(SalesEngine.CartLine("p2", 1.0)), null, 1L)
    val c = HeldInvoicesTest.MemCart(disk = other)
    val s = HeldInvoiceStore(disk, device = { "A" }, clock = { 7L })
    s.hold(c, HeldExtras())
    c.mem = draft(); c.disk = draft()
    //  «K0» برمی‌گردد و «K1» ِ پر خودکار کنار می‌رود
    s.resume(c, "K0", HeldExtras())
    assertEquals("K0", c.snapshot().key)
    //  پس از بسته شدنِ برنامه: «K1» از فایل برمی‌گردد
    HeldInvoiceStore(disk, clock = { 9L }).resume(c, "K1", HeldExtras())
    assertEquals(bytes(draft()), bytes(c.snapshot()))
  }

  @Test
  fun `لیستِ عمدهٔ دستیِ فاکتور پس از بازگشت عمده می‌ماند — پولِ فاکتور همان`() = runBlocking<Unit> {
    val tea = Product(id = "p9", name = "چای", salePrice = 100.0, wholesalePrice = 90.0)
    val shop = ShopData(products = listOf(tea))
    //  صندوق‌دار لیست را دستی عمده کرد (Ctrl+R): `CartStore.priceList`
    val hand = CartPriceList.hand(PriceList.toggle(PriceList.FRESH))
    assertEquals(PriceList.WHOLESALE, hand)
    val wholesale = SaleDraft.of("K2", listOf(SalesEngine.CartLine("p9", 3.0, unitPrice = 90.0)), null, 1L, priceList = hand)
    val disk = HeldInvoicesTest.MemDisk()
    val c = HeldInvoicesTest.MemCart(disk = wholesale)
    val s = HeldInvoiceStore(disk, device = { "A" }, clock = { 7L })
    s.hold(c, HeldExtras())
    s.resume(c, "K2", HeldExtras())
    val back = c.snapshot()
    //  صندوق پس از بازگشت حالت را از همان فاکتور می‌سازد و سبد را با آن دوباره قیمت می‌گذارد (`PriceListAtPos`)
    val state = CartPriceList.effective(CartPriceList.restore(back.priceList, customerList = null), back.priceList)
    assertEquals(PriceList.State(PriceList.WHOLESALE, byHand = true), state)
    assertEquals(90.0, repriceCart(shop, back.cart(), state.list).single().unitPrice)
    //  بی انتخابِ دستی همان فاکتورِ تازهٔ امروز
    assertEquals(PriceList.FRESH, CartPriceList.restore(null, null))
    assertEquals(PriceList.State(PriceList.WHOLESALE), CartPriceList.restore(null, PriceList.WHOLESALE))
    assertEquals(null, CartPriceList.hand(PriceList.State(PriceList.WHOLESALE)))
  }

  @Test
  fun `پیش‌نویس و نگه‌داشتهٔ بی فیلدهای تازه بایت‌به‌بایت همان شکلِ پیشین است`() {
    val plain = SaleDraft.of("K", listOf(SalesEngine.CartLine("p1", 1.0)), null, 42L)
    val old = """{"v":2,"key":"K","lines":[{"productId":"p1","quantity":1.0,"unitPrice":null,"label":"","lotId":null,"note":null,"serials":null}],"debtorId":null,"savedAt":42,"coupons":[],"note":null}"""
    assertEquals(old, SaleDraftCodec.encode(plain))
    assertEquals(old, SaleDraftCodec.encode(SaleDraftCodec.decode(old)!!))
    val book = """{"v":1,"items":[{"id":"X","number":1,"label":"","lines":[{"productId":"p1","quantity":1.0,"unitPrice":null,"label":"","lotId":null,"note":null,"serials":null}],"debtorId":null,"discount":"10","note":"","heldAt":0,"device":"","payment":"","paid":"","coupons":[]}],"current":null}"""
    assertEquals(book, HeldInvoiceCodec.encode(HeldInvoiceCodec.decode(book)!!))
    val held = HeldRules.hold(HeldBook(), plain, HeldExtras("10"), null, null, 0L, "") as HeldRules.Result.Ok
    val text = HeldInvoiceCodec.encode(held.book)
    for (k in listOf("specialPrice", "saleNote", "priceList", "seller", "discountAmount")) assertFalse(k, text.contains("\"$k\""))
  }
}
