package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.model.OrderCustomerDto
import ir.vil3ntec.tohid.core.model.OrderDto
import ir.vil3ntec.tohid.core.model.OrderEventDto
import ir.vil3ntec.tohid.core.model.OrderLineDto
import ir.vil3ntec.tohid.core.pos.SaleNotes
import ir.vil3ntec.tohid.core.sale.CustomerPrice
import ir.vil3ntec.tohid.core.sale.PriceList
import ir.vil3ntec.tohid.ui.components.pos.repriceCart
import ir.vil3ntec.tohid.ui.v3.sales.checkout.CheckoutModel
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  وفاداریِ هر راهی که از دادهٔ ذخیره‌شده سبد یا فروش می‌سازد (دنبالهٔ `HeldFidelityTest`): پیش‌فاکتور ⇒ فروش، اصلاحِ
 *  فاکتور ⇒ سبد، نگه‌داشتهٔ کامپیوتر ⇒ برگهٔ تسویهٔ گوشی، امانی ⇒ فاکتورِ تحویل، سفارشِ ویژه و سفارشِ اینترنتی ⇒ فروش،
 *  و برگشت/تعویض از فاکتورِ اصلی. هر فیلدی که پول یا رسید را می‌سازد پر می‌شود و پس از تبدیل پول به پول (یک‌صدم) سنجیده.
 *  نگهبانِ بالا: فیلدِ تازهٔ `CartLine`/`SaleItem` بی تصمیمِ «می‌رود» یا «عمداً نه» در هر راه، این آزمون را سرخ می‌کند.
 */
class ConvertFidelityTest {

  private val today = "2026-11-09"
  private val t0 = 1_762_650_000_000L
  private var seq = 0
  private fun newId() = "n${seq++}"

  private val tea = Product(id = "tea", name = "چای", salePrice = 100.0, wholesalePrice = 90.0, unit = "عدد")
  private val cup = Product(id = "cup", name = "پیاله", salePrice = 50.0, wholesalePrice = 45.0, unit = "عدد")
  private val phone = Product(id = "ph", name = "گوشی", salePrice = 1000.0, unit = "عدد", serialTracked = true)
  /** قیمتِ خاصِ علی برای پیاله ۴۰ (بی بازه) */
  private val ali = Debtor(id = "d1", name = "علی", specialPrices = listOf(SpecialPrice(productId = "cup", priceMinor = 4000)))
  private val shop = ShopData(
    debtors = listOf(ali),
    products = listOf(tea, cup, phone),
    warehouseEntries = listOf(
      WarehouseEntry(id = "w1", productId = "tea", units = 50.0),
      WarehouseEntry(id = "w2", productId = "cup", units = 50.0),
      WarehouseEntry(id = "w3", productId = "ph", units = 5.0),
    ),
  )

  /** عمده (۹۰ = عمدهٔ کالا) با یادداشت و فروشنده، قیمتِ خاص، قلمِ آزاد: ۲۷۰ + ۸۰ + ۵ = ۳۵۵ */
  private val cart = listOf(
    SalesEngine.CartLine("tea", 3.0, unitPrice = 90.0, note = "بی‌شکر", seller = "زهرا"),
    SalesEngine.CartLine("cup", 2.0, unitPrice = 40.0, specialPrice = 40.0),
    SalesEngine.CartLine("bag1", 1.0, unitPrice = 5.0, label = "کیسه", note = "رایگان نیست", seller = "زهرا"),
  )

  private fun fields(c: Class<*>) = c.declaredFields.filterNot { java.lang.reflect.Modifier.isStatic(it.modifiers) }.map { it.name }.toSet()
  private fun field(o: Any, name: String): Any? = o.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(o)
  private fun ok(r: SalesEngine.Result) = (r as SalesEngine.Result.Ok)
  private fun sale(r: SalesEngine.Result.Ok) = r.data.sales.first { it.id == r.saleId }
  private fun items(r: SalesEngine.Result.Ok) = r.data.saleItems.filter { it.saleId == r.saleId }

  /* ─────────────────────────────── نگهبان ─────────────────────────────── */

  /** هر فیلدِ ردیفِ سبد در هر راه: «می‌رود» یا «عمداً نه» (با دلیل) */
  private val all = setOf("productId", "quantity", "unitPrice", "label", "lotId", "note", "serials", "specialPrice", "seller")
  private val quoteKeeps = setOf("productId", "quantity", "unitPrice", "label", "note", "specialPrice", "seller")
  /** پیش‌فاکتور موجودی نمی‌گیرد: سری و سریال هنگامِ فروش از نو انتخاب می‌شوند */
  private val quoteFresh = setOf("lotId", "serials")
  private val reopenKeeps = all
  private val layawayKeeps = setOf("productId", "quantity", "unitPrice", "label", "note", "specialPrice")
  /** سری هنگامِ تحویل FEFO؛ کالای سریال‌دار امانی نمی‌شود؛ هر ثبتِ بیرون از صندوق فروشندهٔ ردیف را نادیده می‌گیرد (POS-23) */
  private val layawayFresh = setOf("lotId", "serials", "seller")

  /** قلمِ فاکتور ⇒ ردیفِ سبدِ «اصلاحِ فاکتور» (`SaleReopen`) */
  private val itemToLine = mapOf(
    "productId" to "productId", "name" to "label", "quantity" to "quantity", "returnedQty" to "quantity", "unitPrice" to "unitPrice",
    "lotId" to "lotId", "serials" to "serials", "note" to "note", "priceSource" to "specialPrice", "seller" to "seller",
  )
  /**
   *  عمداً از نو در فروشِ تازه: شناسه‌ها؛ بهای تمام‌شده و اجزای کیت همان لحظه (مثلِ هر فروش)؛ جمعِ ردیف از قیمت × مقدار
   *  (پایین برابر سنجیده می‌شود)؛ محلِ دستگاهی که دوباره ثبت می‌کند؛ نشانِ تخفیفِ خودکار (قیمتِ ردیف همان پس از تخفیف است و
   *  ردیفِ دستی تخفیفِ دوم نمی‌گیرد).
   */
  private val itemFresh = setOf("id", "saleId", "purchasePrice", "parts", "totalPrice", "locationId", "listPriceMinor", "promo")

  @Test
  fun `نگهبان — فیلدِ تازهٔ ردیفِ سبد یا قلمِ فاکتور بی تصمیم در هر راهِ تبدیل سرخ می‌کند`() {
    assertEquals(all, fields(SalesEngine.CartLine::class.java))
    assertEquals(all, quoteKeeps + quoteFresh)
    assertEquals(all, reopenKeeps)
    assertEquals(all, layawayKeeps + layawayFresh)
    assertEquals(fields(SaleItem::class.java), itemToLine.keys + itemFresh)
    assertTrue(all.containsAll(itemToLine.values))
    //  پیش‌فاکتور: هر فیلدِ «می‌رود» پس از ساختن و «به سبد» همان است
    val full = SalesEngine.CartLine("cup", 2.0, 40.0, "", "lot-1", "یادداشت", listOf("S1", "S2"), 40.0, "زهرا")
    val q = (Quotes.create(QuoteBook(), shop, listOf(full, cart[2]), "d1", null, today, 7, t0, "q0", "") as Quotes.Result.Ok).quote
    Quotes.cartLines(shop, q, today).zip(listOf(full, cart[2])).forEach { (back, was) ->
      for (f in quoteKeeps) assertEquals(f, field(was, f), field(back, f))
      for (f in quoteFresh) assertNull(f, field(back, f))
    }
    //  امانی: همان
    val lay = Layaways.linesOf(shop, listOf(full.copy(serials = null), cart[2]))!!
    lay.map { Layaways.cartLine(shop, it) }.zip(listOf(full, cart[2])).forEach { (back, was) ->
      for (f in layawayKeeps) assertEquals(f, field(was, f), field(back, f))
    }
  }

  /* ─────────────────────────── پیش‌فاکتور ⇒ فروش ─────────────────────────── */

  private fun quote(book: QuoteBook = QuoteBook(), hand: String? = PriceList.WHOLESALE) =
    (Quotes.create(book, shop, cart, "d1", "تحویل فردا", today, 7, t0, "q1", "مدیر", hand) as Quotes.Result.Ok).quote

  @Test
  fun `پیش‌فاکتور با لیستِ عمدهٔ دستی، قیمتِ خاص، یادداشت و فروشنده همان پول و همان رسید می‌شود`() {
    val q = quote()
    assertEquals(35_500L, q.totalMinor)
    assertEquals(PriceList.WHOLESALE, q.priceList)
    val lines = Quotes.cartLines(shop, q, today)
    assertEquals(cart, lines)
    val hand = Quotes.priceList(shop, q, lines, listsOn = true)
    assertEquals(PriceList.WHOLESALE, hand)
    //  صندوق پس از «به سبد» حالت را از همان سبد می‌سازد (`PriceListAtPos`) و بازقیمت‌گذاری هیچ ردیفی را عوض نمی‌کند
    val state = CartPriceList.effective(CartPriceList.restore(hand, customerList = ""), hand)
    assertSame(lines, repriceCart(shop, lines, state.list))
    assertSame(lines, CustomerPriceCart.reprice(shop, lines, state, "d1", today, manualAllowed = true).cart)
    //  پیش از رفع: لیستِ دستی نمی‌آمد ⇒ چای خرده ⇒ ۳۸۵ به جای ۳۵۵
    assertEquals(385.0, SalesEngine.cartTotal(shop, repriceCart(shop, lines, PriceList.RETAIL)), 0.0)
    val r = ok(SalesEngine.record(shop, lines, SalesEngine.Checkout(seller = "مدیر", debtorId = "d1", lineSellers = true, note = SaleNotes.clean(q.note)), today, t0, ::newId))
    val s = sale(r)
    assertEquals(q.totalMinor, PayFlow.minor(s.finalTotal))
    assertEquals("تحویل فردا", s.note)
    val (t, c, b) = items(r)
    assertEquals(listOf(270.0, 80.0, 5.0), listOf(t.totalPrice, c.totalPrice, b.totalPrice))
    assertEquals("زهرا", t.seller)
    assertEquals("بی‌شکر", t.note)
    assertEquals(CustomerPrice.SPECIAL, c.priceSource)
    assertEquals("کیسه", b.name)
    assertEquals("زهرا", b.seller)
  }

  @Test
  fun `پیش‌فاکتورِ کهنه بایت‌به‌بایت همان و لیستش از فی‌های ثابت پیدا می‌شود`() {
    val plain = SavedQuote(id = "q", number = 1, lines = listOf(SaleDraft.Line("tea", 1.0, 100.0)))
    val old = """{"v":1,"items":[{"id":"q","number":1,"lines":[{"productId":"tea","quantity":1.0,"unitPrice":100.0,"label":"","lotId":null,"note":null,"serials":null}],"debtorId":null,"note":null,"createdAt":0,"createdDay":"","validUntil":"","totalMinor":0,"by":"","cartKey":null}]}"""
    assertEquals(old, QuoteBookCodec.encode(QuoteBook(items = listOf(plain))))
    assertEquals(old, QuoteBookCodec.encode(QuoteBookCodec.decode(old)!!))
    //  بی لیستِ دستی (پیش‌فاکتورِ پیش از این نسخه): همان لیستی که هیچ فیِ ثابتی را عوض نمی‌کند — خاموش ⇒ هیچ
    val legacy = quote(hand = null)
    assertNull(legacy.priceList)
    val lines = Quotes.cartLines(shop, legacy, today)
    assertEquals(PriceList.WHOLESALE, Quotes.priceList(shop, legacy, lines, listsOn = true))
    assertNull(Quotes.priceList(shop, legacy, lines, listsOn = false))
    assertNull(CartPriceList.keep(shop, listOf(SalesEngine.CartLine("tea", 1.0, 100.0)), ""))
  }

  @Test
  fun `قیمتِ خاصِ پایان‌یافته فیِ پیش‌فاکتور را عوض نمی‌کند`() {
    val q = quote()
    val gone = shop.copy(debtors = listOf(ali.copy(specialPrices = listOf(SpecialPrice("cup", 4000, to = "2026-11-01")))))
    val lines = Quotes.cartLines(gone, q, today)
    assertNull(lines[1].specialPrice)
    val state = PriceList.State(PriceList.WHOLESALE, byHand = true)
    assertEquals(40.0, CustomerPriceCart.reprice(gone, lines, state, "d1", today, manualAllowed = true).cart[1].unitPrice)
    //  با نشانِ کهنه ردیف «از قیمتِ خاص» بود و به عمدهٔ ۴۵ ِ لیستِ فاکتور می‌رفت (۸۰ ⇐ ۹۰)
    val stale = lines.mapIndexed { i, l -> if (i == 1) l.copy(specialPrice = 40.0) else l }
    assertEquals(45.0, CustomerPriceCart.reprice(gone, stale, state, "d1", today, manualAllowed = true).cart[1].unitPrice)
  }

  /* ─────────────────────────── اصلاحِ فاکتور ⇒ سبد ─────────────────────────── */

  /** فاکتورِ عمده با ۱۰٪: ۲۷۰ + ۸۰ + ۱۰۰۰ + ۵ = ۱۳۵۵ − ۱۳۶ = ۱۲۱۹؛ سریِ چای (موتورِ تازهٔ موجودی) روی قلم */
  private fun posted(discount: SalesEngine.Checkout = SalesEngine.Checkout(discountType = SalesEngine.DiscountType.PERCENT, discountValue = 10.0)): Pair<ShopData, Sale> {
    val lines = cart.take(2) + SalesEngine.CartLine("ph", 1.0, serials = listOf("SN-1"), seller = "زهرا") + cart[2]
    val r = ok(SalesEngine.record(shop, lines, discount.copy(seller = "مدیر", debtorId = "d1", lineSellers = true, note = "فاکتورِ عمده"), today, t0, ::newId))
    val d = r.data.copy(saleItems = r.data.saleItems.map { if (it.productId == "tea") it.copy(lotId = "lot-7") else it })
    return d to sale(r)
  }

  @Test
  fun `اصلاحِ فاکتور همهٔ نشان‌های ردیف، تخفیف، یادداشت و لیستِ عمده را می‌برد و همان پول ثبت می‌شود`() {
    val (d, s) = posted()
    assertEquals(1219.0, s.finalTotal, 0.0)
    val lines = SaleReopen.lines(d, s, { "free-new" }, today)
    val expect = listOf(
      cart[0].copy(lotId = "lot-7"), cart[1],
      SalesEngine.CartLine("ph", 1.0, unitPrice = 1000.0, serials = listOf("SN-1"), seller = "زهرا"),
      cart[2].copy(productId = "free-new"),
    )
    assertEquals(expect, lines)
    val x = SaleReopen.extras(d, s, lines)
    //  فروشندهٔ فاکتور هم (پیش‌تر نمی‌آمد ⇒ فاکتورِ اصلاح‌شده به نامِ اصلاح‌کننده ثبت می‌شد)
    assertEquals(HeldExtras("10", seller = "مدیر"), x)
    assertEquals("فاکتورِ عمده", SaleReopen.note(s))
    //  لیست: چای به عمده بود ⇒ عمدهٔ دستی، وگرنه سبدِ خرده ۳۰ افغانی گران‌تر
    val hand = CartPriceList.keep(d, lines, "")
    assertEquals(PriceList.WHOLESALE, hand)
    val kept = repriceCart(d, lines, CartPriceList.restore(hand, "").list)
    assertEquals(lines.map { SalesEngine.linePrice(d, it) }, kept.map { SalesEngine.linePrice(d, it) })
    assertEquals(1385.0, SalesEngine.cartTotal(d, repriceCart(d, lines, PriceList.RETAIL)), 0.0)
    //  برگهٔ تسویهٔ گوشی همان تخفیف را می‌گیرد (`HeldSession` ⇐ `CheckoutModel.start`)
    val form = CheckoutModel.start("d1", d.debtors, x)
    assertEquals(SalesEngine.DiscountType.PERCENT, form.discountType)
    //  صندوقِ گوشی: فروشندهٔ فاکتورِ اصلاح‌شده، نه کاربرِ صندوق («شاگرد») — همان قاعدهٔ `CheckoutRoute`
    val checkout = CheckoutModel.checkout(form, canDiscount = true, blockExpired = false, seller = x.seller.ifBlank { "شاگرد" })
    assertEquals(1219.0, CheckoutModel.numbers(d, lines, form, checkout).finalTotal, 0.0)
    //  دوباره ثبت (فاکتورِ اصلی لغو) ⇒ همان پول و همان قلم‌ها
    val voided = d.copy(sales = d.sales.map { if (it.id == s.id) it.copy(status = "cancelled") else it })
    val again = ok(SalesEngine.record(voided, lines, checkout.copy(paidAmount = 0.0, payment = SalesEngine.Payment.CASH, debtorId = "d1", lineSellers = true, note = SaleReopen.note(s)), today, t0 + 1, ::newId))
    assertEquals(s.finalTotal, sale(again).finalTotal, 0.0)
    assertEquals(s.discount, sale(again).discount, 0.0)
    assertEquals(s.note, sale(again).note)
    assertEquals(s.seller, sale(again).seller)
    val was = d.saleItems.filter { it.saleId == s.id }
    items(again).zip(was).forEach { (n, o) ->
      assertEquals(listOf(o.productId, o.quantity, o.unitPrice, o.totalPrice, o.note, o.serials, o.priceSource, o.seller),
        listOf(n.productId, n.quantity, n.unitPrice, n.totalPrice, n.note, n.serials, n.priceSource, n.seller))
    }
  }

  @Test
  fun `اصلاحِ فاکتورِ نیمه‌برگشته سهمِ تخفیفِ مبلغیِ همان اقلام را می‌برد`() {
    val (d0, s) = posted(SalesEngine.Checkout(discountValue = 60.0))
    assertEquals(1295.0, s.finalTotal, 0.0)
    //  یک چای برگشت (سهمِ پرداخت ۱۲۹۵ ÷ ۱۳۵۵)
    val tea = d0.saleItems.first { it.saleId == s.id && it.productId == "tea" }
    val d = ok(SalesEngine.recordReturn(d0, s.id, mapOf(tea.id to 1.0), "خراب", today, t0 + 1, ::newId)).data
    val refunded = d.saleReturns.sumOf { it.amount }
    assertEquals(86.0, refunded, 0.0)
    val lines = SaleReopen.lines(d, s, ::newId, today)
    assertEquals(2.0, lines.first { it.productId == "tea" }.quantity, 0.0)
    //  سریال‌های قلمِ برگشت‌خورده معلوم نیست ⇒ از نو؛ قلمِ بی برگشت سریالش را می‌برد
    assertEquals(listOf("SN-1"), lines.first { it.productId == "ph" }.serials)
    val x = SaleReopen.extras(d, s, lines)
    //  round(۶۰ × ۱۲۶۵ ÷ ۱۳۵۵) = ۵۶ ⇒ ۱۲۰۹ (پیش از رفع: بی تخفیف ۱۲۶۵)
    assertEquals(HeldExtras("56", discountAmount = true, seller = "مدیر"), x)
    val form = CheckoutModel.start("d1", d.debtors, x)
    assertEquals(SalesEngine.DiscountType.AMOUNT, form.discountType)
    val n = CheckoutModel.numbers(d, lines, form, CheckoutModel.checkout(form, canDiscount = true, blockExpired = false, seller = ""))
    assertEquals(1209.0, n.finalTotal, 0.0)
    assertEquals(s.finalTotal - refunded - n.finalTotal, 0.0, 0.5)
  }

  @Test
  fun `فاکتورِ بی تخفیف تخفیفی نمی‌سازد و قیمتِ خاصِ پایان‌یافته نشان نمی‌گیرد`() {
    val (d, s) = posted(SalesEngine.Checkout())
    //  بی تخفیف ⇒ فقط فروشندهٔ فاکتور
    assertEquals(HeldExtras(seller = "مدیر"), SaleReopen.extras(d, s, SaleReopen.lines(d, s, ::newId, today)))
    val gone = d.copy(debtors = listOf(ali.copy(specialPrices = null)))
    val cupLine = SaleReopen.lines(gone, s, ::newId, today).first { it.productId == "cup" }
    assertEquals(40.0, cupLine.unitPrice)
    assertNull(cupLine.specialPrice)
  }

  /* ───────────────── نگه‌داشتهٔ کامپیوتر ⇒ برگهٔ تسویهٔ گوشی (POS-12/POS-20) ───────────────── */

  @Test
  fun `تخفیفِ مبلغیِ فاکتورِ نگه‌داشته در برگهٔ گوشی مبلغ می‌ماند نه درصد`() {
    val ten = listOf(SalesEngine.CartLine("tea", 10.0))
    val x = HeldExtras(discount = " 500 ", payment = SalesEngine.Payment.CREDIT, paid = "200", discountAmount = true)
    val form = CheckoutModel.start("d1", shop.debtors, x)
    assertEquals(SalesEngine.DiscountType.AMOUNT, form.discountType)
    assertEquals("500", form.discountText)
    assertEquals("200", form.paidText)
    val n = CheckoutModel.numbers(shop, ten, form, CheckoutModel.checkout(form, canDiscount = true, blockExpired = false, seller = ""))
    assertEquals(500.0, n.finalTotal, 0.0)
    assertEquals(300.0, n.remaining, 0.0)
    //  پیش از رفع: همان «۵۰۰» درصد خوانده می‌شد و تا ۱۰۰٪ بریده ⇒ کلِ ۱۰۰۰ رایگان
    val before = CheckoutModel.start("d1", shop.debtors, x.discount)
    assertEquals(0.0, CheckoutModel.numbers(shop, ten, before, CheckoutModel.checkout(before, true, false, "")).finalTotal, 0.0)
    //  نقد: دریافتیِ نوشته‌شده معنا ندارد
    assertEquals("", CheckoutModel.start(null, shop.debtors, x.copy(payment = SalesEngine.Payment.CASH)).paidText)
  }

  @Test
  fun `اصلاح و ثبتِ فاکتورِ ردشده همان تخفیف و همان دریافتیِ نسیه را به برگه می‌برد`() {
    val inv = ir.vil3ntec.tohid.core.sync.ConflictInbox.Invoice(
      number = 4, createdAt = t0, paymentMethod = "credit", debtorId = "d1", debtorName = "علی", seller = "زهرا",
      totalMinor = 35_500, discountMinor = 5_500, finalMinor = 30_000, paidMinor = 10_000, remainingMinor = 20_000, debtMinor = 20_000,
      items = listOf(
        ir.vil3ntec.tohid.core.sync.ConflictInbox.Line("چای", "tea", 3_000, 9_000, 27_000),
        ir.vil3ntec.tohid.core.sync.ConflictInbox.Line("پیاله", "cup", 2_000, 4_000, 8_000),
        ir.vil3ntec.tohid.core.sync.ConflictInbox.Line("کیسه", "", 1_000, 500, 500),
      ),
      returns = emptyList(), truncated = false,
    )
    //  همان نگاشتِ `fixAtTill`: کالای این دفتر با همان قیمت، بقیه قلمِ آزاد با همان نام
    val lines = ir.vil3ntec.tohid.core.sync.ConflictInbox.cartLines(inv).map {
      if (it.productId == "tea" || it.productId == "cup") SalesEngine.CartLine(it.productId, it.quantity, it.unitPrice)
      else SalesEngine.CartLine("free-x", it.quantity, it.unitPrice, it.name)
    }
    val x = SaleReopen.extras(inv)
    assertEquals(HeldExtras("55", SalesEngine.Payment.CREDIT, "100", discountAmount = true, seller = "زهرا"), x)
    val form = CheckoutModel.start("d1", shop.debtors, x)
    val n = CheckoutModel.numbers(shop, lines, form, CheckoutModel.checkout(form, canDiscount = true, blockExpired = false, seller = ""))
    //  پیش از رفع: بی تخفیف و بی دریافتی ⇒ ۳۵۵ همه به حساب، به جای ۳۰۰ و ۲۰۰
    assertEquals(listOf(inv.finalMinor, inv.paidMinor, inv.remainingMinor), listOf(n.finalTotal, n.paid, n.remaining).map(PayFlow::minor))
    assertEquals(HeldExtras(seller = "زهرا"), SaleReopen.extras(inv.copy(discountMinor = 0, paymentMethod = "cash")))
  }

  /* ─────────────────────────── امانی ⇒ فاکتورِ تحویل ─────────────────────────── */

  @Test
  fun `امانی یادداشت و نشانِ قیمتِ خاص را تا فاکتورِ تحویل می‌برد و پولش همان است`() {
    val lines = listOf(SalesEngine.CartLine("tea", 2.0, note = "کادو"), cart[1])
    val o = Layaways.NewLayaway("d1", lines, 4_000, 1, 30, "2026-12-09")
    val made = (Layaways.create(shop, "L1", o, today, t0, "رحیم") as Layaways.Built.Ok).data
    val doc = made.auditLog.first { it.id == "L1" }.layaway!!
    assertEquals(28_000L, doc.totalMinor)
    assertEquals(listOf("کادو", null), doc.lines!!.map { it.note })
    assertEquals(listOf(null, 4_000L), doc.lines!!.map { it.specialMinor })
    val v = Layaways.find(made, "L1", today)!!
    val paid = (Layaways.pay(made, Layaways.payKey(v), "L1", 24_000, today, t0 + 1, "رحیم") as Layaways.Built.Ok).data
    val key = Layaways.deliverKey(Layaways.find(paid, "L1", today)!!)
    val done = (Layaways.deliver(paid, key, "L1", today, t0 + 2, "رحیم", "B1", false, ::newId) as Layaways.Built.Ok).data
    val s = done.sales.first { it.id == key }
    assertEquals(280.0, s.finalTotal, 0.0)
    val its = done.saleItems.filter { it.saleId == key }
    assertEquals(listOf("کادو", null), its.map { it.note })
    assertEquals(listOf(null, CustomerPrice.SPECIAL), its.map { it.priceSource })
    //  سندِ کهنه بی این دو فیلد بایت‌به‌بایت همان
    val json = Json { encodeDefaults = true }
    assertEquals("""{"productId":"tea","name":"چای","qtyMilli":1000,"unitMinor":10000}""", json.encodeToString(LayawayLine.serializer(), LayawayLine("tea", name = "چای", unitMinor = 10_000)))
  }

  /* ───────────────────── سفارشِ ویژه و سفارشِ اینترنتی ⇒ فروش ───────────────────── */

  @Test
  fun `سفارشِ ویژه با جمعِ بخش‌ناپذیر و پیش‌پرداخت همان جمع و همان به‌حساب را می‌گیرد`() {
    val o = SpecialOrders.NewOrder("d1", "tea", "چای سبزِ ویژه", 3_000, 100_000, 30_000, "2026-11-20")
    val made = (SpecialOrders.create(shop, "S1", o, today, t0, "رحیم", ::newId) as SpecialOrders.Built.Ok).data
    val done = (SpecialOrders.deliver(made, "K1", "S1", today, t0 + 1, "رحیم", "B1", false, ::newId) as SpecialOrders.Built.Ok).data
    val s = done.sales.first { it.id == "K1" }
    //  ۱۰۰۰ ÷ ۳ بخش‌ناپذیر است ولی جمعِ قلم همان ۱۰۰۰؛ ۳۰۰ از پیش‌پرداخت به حساب، ۷۰۰ نقد
    assertEquals(listOf(1000.0, 300.0, 700.0), listOf(s.finalTotal, s.debtGiven, s.paidAmount))
    assertEquals(1000.0, done.saleItems.single { it.saleId == "K1" }.totalPrice, 0.0)
    assertEquals("d1", s.debtorId)
  }

  @Test
  fun `سفارشِ اینترنتی با تخفیف و هزینهٔ ارسال پول‌به‌پول همان سفارش است`() {
    val lines = listOf(
      OrderLineDto(lineNo = 1, productId = "tea", name = "چای", unit = "عدد", qtyMilli = 3000, unitPriceMinor = 3333, lineTotalMinor = 10_000, note = "سبز"),
      OrderLineDto(lineNo = 2, productId = "", name = "کیسه", qtyMilli = 1000, unitPriceMinor = 550, lineTotalMinor = 550),
    )
    val order = OrderDto(
      id = "01ORD", orderNo = 9, status = "picked_up", fulfillment = "pickup", paymentMethod = "cod", paymentStatus = "unpaid",
      customer = OrderCustomerDto(name = "علی"), subtotalMinor = 10_550, discountMinor = 1_000, deliveryFeeMinor = 250,
      totalMinor = 9_800, lines = lines, note = "زنگ نزنید",
      events = listOf(OrderEventDto(kind = "status", fromStatus = "ready_for_pickup", toStatus = "picked_up", at = 5_000L)),
    )
    val r = OrderFulfillment.fulfill(shop, order, today, t0) as OrderFulfillment.Result.Ok
    val s = r.data.sales.single()
    assertEquals(listOf(98.0, 10.0, 108.0), listOf(s.finalTotal, s.discount, s.total))
    assertEquals(listOf(100.0, 5.5, 2.5), r.data.saleItems.map { it.totalPrice })
    //  یادداشت‌های سفارش روی خودِ سفارش می‌مانند و فروش با شناسه به آن پیوسته است (عمداً بی رونوشت)
    assertEquals("01ORD", s.orderId)
  }

  /* ─────────────────────────── برگشت و تعویض ─────────────────────────── */

  /** ۳ × ۹۵ (دستی) + قیمتِ خاص + قلمِ آزاد = ۳۷۰، ۱۰٪ ⇒ ۳۳۳ */
  private fun forReturn(): Pair<ShopData, Sale> {
    val lines = listOf(SalesEngine.CartLine("tea", 3.0, unitPrice = 95.0, note = "بی‌شکر", seller = "زهرا"), cart[1], cart[2])
    val r = ok(SalesEngine.record(shop, lines, SalesEngine.Checkout(seller = "مدیر", discountType = SalesEngine.DiscountType.PERCENT, discountValue = 10.0, debtorId = "d1", lineSellers = true), today, t0, ::newId))
    val d = r.data.copy(saleItems = r.data.saleItems.map { if (it.productId == "tea") it.copy(lotId = "lot-7") else it })
    return d to sale(r)
  }

  @Test
  fun `برگشتِ همهٔ فاکتور دقیقاً پرداختی را پس می‌دهد و برگشتِ تکه‌تکه همان برگشتِ یک‌جاست`() {
    val (d, s) = forReturn()
    assertEquals(333.0, s.finalTotal, 0.0)
    val its = d.saleItems.filter { it.saleId == s.id }
    val all = (ReturnFlow.quote(d, s.id, its.associate { it.id to ReturnFlow.Pick(it.quantity) }) as ReturnFlow.Outcome.Ok).value
    //  ۲۵۶٫۵ ⇒ ۲۵۷، ۷۲، ۴٫۵ ⇒ ۵؛ اضافهٔ گرد از قلمِ آخر ⇒ ۴ و جمع همان ۳۳۳
    assertEquals(listOf(257.0, 72.0, 4.0), all.lines.map { it.amount })
    assertEquals(s.finalTotal, all.total, 0.0)
    //  تکه‌تکه: ۱ چای ۸۶، سپس ۲ چای ۱۷۱ = ۲۵۷
    val tea = its.first { it.productId == "tea" }
    val req = ReturnFlow.Request(docId = "r1", saleId = s.id, picks = mapOf(tea.id to ReturnFlow.Pick(1.0)), reason = "خراب")
    val one = (ReturnFlow.post(d, req, today, t0 + 1, ::newId) as ReturnFlow.Outcome.Ok).value
    assertEquals(86.0, one.total, 0.0)
    val two = (ReturnFlow.quote(one.data, s.id, mapOf(tea.id to ReturnFlow.Pick(2.0))) as ReturnFlow.Outcome.Ok).value
    assertEquals(171.0, two.total, 0.0)
    //  قلمِ فاکتور دست نمی‌خورد جز شمارندهٔ برگشت: سری (کالا به همان سری برمی‌گردد، `StockLedger`) و بهای تمام‌شده همان
    val after = one.data.saleItems.first { it.id == tea.id }
    assertEquals(tea.copy(returnedQty = 1.0), after)
    assertEquals("lot-7", after.lotId)
  }

  @Test
  fun `قاعدهٔ گردِ برگشت — برگشتِ کاملِ سه قلمِ ۱۰ با تخفیفِ ۲ هنوز ۲۷ پس می‌دهد نه ۲۸ (گزارش شد، عوض نشد)`() {
    val three = listOf(SalesEngine.CartLine("a1", 1.0, 10.0, "الف"), SalesEngine.CartLine("a2", 1.0, 10.0, "ب"), SalesEngine.CartLine("a3", 1.0, 10.0, "ج"))
    val r = ok(SalesEngine.record(shop, three, SalesEngine.Checkout(discountValue = 2.0), today, t0, ::newId))
    val s = sale(r)
    assertEquals(28.0, s.finalTotal, 0.0)
    val q = (ReturnFlow.quote(r.data, s.id, items(r).associate { it.id to ReturnFlow.Pick(1.0) }) as ReturnFlow.Outcome.Ok).value
    //  هر قلم round(۱۰ × ۲۸ ÷ ۳۰) = ۹ (SEC-DATA-01، هم‌زادِ `return-line-cap.js` با سقفِ همین ۹)؛ کفِ «برگشتِ کامل = پرداختی» نیست
    assertEquals(27.0, q.total, 0.0)
  }

  @Test
  fun `تعویض — برگشتِ قیمتِ خاص و فروشِ تازه در یک سند، مانده دقیق`() {
    val (d, s) = forReturn()
    val cupItem = d.saleItems.first { it.saleId == s.id && it.productId == "cup" }
    val req = ReturnFlow.Request(docId = "x1", saleId = s.id, picks = mapOf(cupItem.id to ReturnFlow.Pick(2.0)), reason = "خراب")
    val x = (ReturnFlow.exchange(d, req, listOf(SalesEngine.CartLine("tea", 1.0)), SalesEngine.Checkout(seller = "مدیر"), "K9", today, t0 + 1, ::newId) as ReturnFlow.Outcome.Ok).value
    assertEquals(72.0, x.returned, 0.0)
    assertEquals(100.0, x.sold, 0.0)
    assertEquals(28.0, x.due, 0.0)
    assertNotNull(x.data.sales.firstOrNull { it.id == "K9" })
    assertFalse(x.data.saleReturns.none { it.exchangeSaleId == "K9" })
  }
}
