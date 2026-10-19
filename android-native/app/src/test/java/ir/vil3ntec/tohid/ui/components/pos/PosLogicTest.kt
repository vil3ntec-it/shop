package ir.vil3ntec.tohid.ui.components.pos

import ir.vil3ntec.tohid.core.keymap.KeyName
import ir.vil3ntec.tohid.data.CartQuery
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.WarehouseEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 *  صندوقِ سه‌ستونهٔ کامپیوتر (VISUAL-DIRECTION §۲) — منطقِ خالصی که صفحه
 *  (`DeskSaleScreen`) و اجزای `ui/components/pos` از آن می‌خوانند: دسته‌ها،
 *  شبکه، Enter، − / +، روش‌های پرداخت، خلاصه، چیدمان و کلیدها. آخرش فروشِ
 *  نقدیِ سه‌قلمی فقط با کیبورد و با شمارشِ §۷ (≤ ۸ کلید، ۱.۳ ردیف ۳).
 */
class PosLogicTest {

  private var seq = 0
  private fun newId(): String = "g${seq++}"

  private val rice = Product(id = "p1", name = "برنج", category = "خوراکی", unit = "کیلوگرم", salePrice = 120.0, barcodes = listOf("626001000"))
  private val oil = Product(id = "p2", name = "روغن", category = "خوراکی", unit = "عدد", salePrice = 1650.0, barcodes = listOf("626001001"))
  private val tea = Product(id = "p3", name = "چای سبز", category = "نوشیدنی", unit = "بسته", salePrice = 260.0, barcodes = listOf("626001003"), minStock = 10.0)
  private val soda = Product(id = "p4", name = "نوشابه", category = "نوشیدنی", unit = "عدد", salePrice = 80.0, barcodes = listOf("626001007"))
  private val paste = Product(id = "p5", name = "رب گوجه", category = "خوراکی", unit = "عدد", salePrice = 85.0, barcodes = listOf("626001011"), active = false)
  private val soap = Product(id = "p6", name = "صابون", category = "بهداشتی", unit = "عدد", salePrice = 50.0, barcodes = listOf("626001004"))
  private val loose = Product(id = "p7", name = "برگه", category = "", unit = "عدد", salePrice = 5.0)

  private fun ledger() = ShopData(
    products = listOf(rice, oil, tea, soda, paste, soap, loose),
    productCategories = listOf("نوشیدنی", "خوراکی", "لبنیات", "بهداشتی"),
    warehouseEntries = listOf(
      WarehouseEntry(id = "w1", productId = "p1", units = 50.0),
      WarehouseEntry(id = "w2", productId = "p2", units = 2.0),
      WarehouseEntry(id = "w3", productId = "p3", units = 40.0),
      WarehouseEntry(id = "w4", productId = "p4", units = 24.0),
      WarehouseEntry(id = "w5", productId = "p5", units = 10.0),
      WarehouseEntry(id = "w6", productId = "p6", units = 20.0),
    ),
  )

  /* ---------------- ستونِ دسته‌ها ---------------- */

  @Test
  fun `دسته‌ها — «همه» اول با شمارِ کالای فعال، بعد دسته‌های دفتر به ترتیبِ خودش`() {
    val c = PosCatalog.categories(ledger())
    assertEquals(PosCategory(null, "همه", 6), c.first())
    //  «لبنیات» کالای فعال ندارد ⇒ کاشی نمی‌گیرد؛ «رب گوجه» غیرفعال است و شمرده نمی‌شود
    assertEquals(listOf("نوشیدنی" to 2, "خوراکی" to 2, "بهداشتی" to 1), c.drop(1).map { it.name to it.count })
  }

  @Test
  fun `دسته‌ای که فقط روی کالا نوشته شده پس از دسته‌های دفتر می‌آید`() {
    val d = ledger().copy(products = ledger().products + Product(id = "p9", name = "قلم", category = "لوازم"))
    assertEquals("لوازم", PosCatalog.categories(d).last().name)
  }

  /* ---------------- شبکه و جست‌وجو ---------------- */

  @Test
  fun `کالای غیرفعال نه در شبکه می‌آید نه در جست‌وجو (PRD-18)`() {
    val d = ledger()
    assertFalse(PosCatalog.byCategory(d.products, null).any { it.id == "p5" })
    assertTrue(PosCatalog.matches(d.products, "رب").isEmpty())
  }

  @Test
  fun `بی عبارت ⇒ کالاهای دستهٔ انتخابی به ترتیبِ نام؛ با عبارت ⇒ همهٔ دسته‌ها`() {
    val d = ledger()
    //  ترتیبِ نام همان مقایسهٔ سادهٔ رشته است که جدولِ کالاها هم دارد
    assertEquals(listOf("چای سبز", "نوشابه").sorted(), PosCatalog.shown(d.products, "نوشیدنی", "").map { it.name })
    //  دستهٔ «نوشیدنی» انتخاب است، ولی «برنج» (خوراکی) با جست‌وجو پیدا می‌شود — Enter همان پیشین می‌ماند
    assertEquals(listOf("برنج", "برگه"), PosCatalog.shown(d.products, "نوشیدنی", "بر").map { it.name })
  }

  @Test
  fun `جست‌وجو — نامِ آغازشونده اول، و آغازِ بارکد با رقمِ فارسی هم`() {
    val d = ledger()
    val p = d.products + Product(id = "p8", name = "آب برنج", salePrice = 1.0)
    assertEquals(listOf("برنج", "آب برنج"), PosCatalog.matches(p, "برنج").map { it.name })
    assertEquals(listOf("p3"), PosCatalog.matches(d.products, "۶۲۶۰۰۱۰۰۳").map { it.id })
    assertTrue(PosCatalog.matches(d.products, "").isEmpty())
  }

  /* ---------------- Enter در کادر ---------------- */

  private fun resolve(d: ShopData, query: String, hint: Int = 0): PosHit {
    val term = CartQuery.parse(query).term.trim()
    return PosSearch.resolve(query, ShopStore.barcodeIndex(d), d.products, PosCatalog.matches(d.products, term), hint)
  }

  @Test
  fun `Enter — بارکدِ کامل همان کالا، پیشوندِ تعداد سه تا، نام پیشنهادِ برجسته`() {
    val d = ledger()
    assertEquals(PosHit.Add(oil, 1.0), resolve(d, "626001001"))
    assertEquals(PosHit.Add(tea, 3.0), resolve(d, "3*626001003"))
    assertEquals(PosHit.Add(tea, 2.0), resolve(d, "۲*۶۲۶۰۰۱۰۰۳"))
    assertEquals(PosHit.Add(rice, 1.0), resolve(d, "بر"))
    assertEquals(PosHit.Add(loose, 1.0), resolve(d, "بر", hint = 1))
  }

  @Test
  fun `Enter — کالای غیرفعال پیام می‌گیرد، ناشناخته «پیدا نشد»، کادرِ خالی هیچ`() {
    val d = ledger()
    assertEquals(PosHit.Inactive(paste), resolve(d, "626001011"))
    assertEquals(PosHit.Unknown("999"), resolve(d, "999"))
    assertEquals(PosHit.None, resolve(d, "   "))
    assertEquals(PosHit.None, resolve(d, "4*"))
  }

  /* ---------------- − / تعداد / + ---------------- */

  @Test
  fun `− روی کالای بیرونِ سبد کاری نمی‌کند و تعداد هرگز منفی نمی‌شود`() {
    val cart = listOf(SalesEngine.CartLine("p2", 1.0))
    assertSame(cart, PosCartOps.step(cart, "p6", "عدد", -1))
    val empty = PosCartOps.step(cart, "p2", "عدد", -1)
    assertTrue("ردیفِ صفرشده برداشته می‌شود", empty.isEmpty())
    assertEquals(0.0, PosCartOps.qtyOf(empty, "p2"), 0.0)
  }

  @Test
  fun `پلهٔ − و + همان cartStep است — کیلو ۰٫۱، دانه ۱`() {
    val cart = listOf(SalesEngine.CartLine("p1", 2.0), SalesEngine.CartLine("p2", 1.0))
    assertEquals(2.1, PosCartOps.qtyOf(PosCartOps.step(cart, "p1", "کیلوگرم", 1), "p1"), 1e-9)
    assertEquals(1.9, PosCartOps.qtyOf(PosCartOps.step(cart, "p1", "کیلوگرم", -1), "p1"), 1e-9)
    assertEquals(2.0, PosCartOps.qtyOf(PosCartOps.step(cart, "p2", "عدد", 1), "p2"), 0.0)
  }

  @Test
  fun `+ روی کاشی سقفِ موجودی دارد، مثلِ اسکن`() {
    var cart = emptyList<SalesEngine.CartLine>()
    repeat(2) { cart = PosCartOps.addStep(cart, oil, available = 2.0).cart }
    val third = PosCartOps.addStep(cart, oil, available = 2.0)
    assertTrue(third.capped)
    assertEquals(2.0, PosCartOps.qtyOf(third.cart, "p2"), 0.0)
    val none = PosCartOps.addStep(emptyList(), soap, available = 0.0)
    assertTrue(none.capped)
    assertTrue(none.cart.isEmpty())
  }

  /* ---------------- پرداخت و خلاصه ---------------- */

  @Test
  fun `شبکهٔ روش‌ها فقط روش‌های واقعیِ موتورِ فروش را دارد`() {
    val m = PosPayments.configured()
    assertEquals(SalesEngine.Payment.entries.toList(), m.map { it.payment })
    assertEquals(listOf("نقدی", "نسیه"), m.map { it.label })
    assertEquals(listOf(false, true), m.map { it.needsCustomer })
    assertTrue("روشِ ساخته‌نشده دکمه نمی‌گیرد", m.none { it.label.contains("کارت") || it.label.contains("حواله") })
  }

  @Test
  fun `خلاصه — جمع همیشه، تخفیف فقط اگر هست، و قابلِ پرداخت همان finalTotal`() {
    val d = ledger()
    val cart = listOf(SalesEngine.CartLine("p2", 1.0), SalesEngine.CartLine("p6", 2.0))
    val plain = SalesEngine.totals(d, cart, SalesEngine.Checkout())
    assertEquals(listOf(PosSummaryRow("جمع", 1750.0)), PosSummary.rows(plain))
    assertEquals(1750.0, plain.finalTotal, 0.0)

    val off = SalesEngine.totals(d, cart, SalesEngine.Checkout(discountType = SalesEngine.DiscountType.PERCENT, discountValue = 10.0))
    assertEquals(listOf(PosSummaryRow("جمع", 1750.0), PosSummaryRow("تخفیف", 175.0, negative = true)), PosSummary.rows(off))
    assertEquals(off.subtotal - off.discount, off.finalTotal, 0.0)
    val labels = PosSummary.rows(off).map { it.label }
    assertTrue("حمل، مالیات و گردکردن هنوز در موتور نیستند", labels.none { it in setOf("حمل", "مالیات", "گردکردن") })
  }

  /* ---------------- چیدمان ---------------- */

  @Test
  fun `ستونِ دسته‌ها از ۹۰۰dp ثابت است و کمتر تاشونده؛ لمس ۴۸ و کامپیوتر دست‌کم ۳۴`() {
    assertTrue(PosLayout.railFixed(1200f))
    assertTrue(PosLayout.railFixed(900f))
    assertFalse(PosLayout.railFixed(899f))
    assertEquals(48, PosLayout.minTarget(touch = true))
    assertTrue(PosLayout.minTarget(touch = false) >= 34)
  }

  /* ---------------- کلیدها ---------------- */

  @Test
  fun `کلیدهای سطحِ صفحه همان پیشین‌اند`() {
    assertEquals(PosAction.FINISH, PosKeys.screen(KeyName.F9, primary = false))
    assertEquals(PosAction.FINISH, PosKeys.screen(KeyName.F12, primary = false))
    assertEquals(PosAction.FINISH, PosKeys.screen(KeyName.Enter, primary = true))
    assertNull("Enter بی Ctrl مالِ کادر است", PosKeys.screen(KeyName.Enter, primary = false))
    assertEquals(PosAction.CUSTOMER, PosKeys.screen(KeyName.F2, false))
    assertEquals(PosAction.EDIT_ROW, PosKeys.screen(KeyName.F3, false))
    assertEquals(PosAction.FREE_ITEM, PosKeys.screen(KeyName.F4, false))
    assertEquals(PosAction.REPRINT, PosKeys.screen(KeyName.F8, false))
    assertNull("F10 هیچ کاری ندارد (۵.۱ بند ۴)", PosKeys.screen(KeyName.F10, false))
  }

  @Test
  fun `کلیدهای کادر — Esc هرگز سبد را پاک نمی‌کند و + − Delete فقط با کادرِ خالی`() {
    assertEquals(PosAction.SUBMIT, PosKeys.field(KeyName.Enter, false, queryEmpty = false, hasMatches = true, hasSelection = false))
    assertNull(PosKeys.field(KeyName.Enter, true, false, true, false))
    assertEquals(PosAction.HINT_DOWN, PosKeys.field(KeyName.Down, false, false, hasMatches = true, hasSelection = true))
    assertEquals(PosAction.ROW_UP, PosKeys.field(KeyName.Up, false, true, hasMatches = false, hasSelection = true))
    assertEquals(PosAction.CLEAR_QUERY, PosKeys.field(KeyName.Escape, false, queryEmpty = false, hasMatches = false, hasSelection = true))
    assertNull(PosKeys.field(KeyName.Escape, false, queryEmpty = true, hasMatches = false, hasSelection = true))
    assertNull(PosKeys.field(KeyName.Delete, false, queryEmpty = true, hasMatches = false, hasSelection = false))
    assertEquals(PosAction.DELETE_ROW, PosKeys.field(KeyName.Delete, false, true, false, true))
    assertEquals(PosAction.QTY_UP, PosKeys.field(KeyName.Plus, false, true, false, true))
    assertEquals(PosAction.QTY_UP, PosKeys.field(KeyName.Equals, false, true, false, true))
    assertEquals(PosAction.QTY_DOWN, PosKeys.field(KeyName.Minus, false, true, false, true))
    assertNull("وقتی کادر نوشته دارد، − نویسه است", PosKeys.field(KeyName.Minus, false, queryEmpty = false, hasMatches = false, hasSelection = true))
  }

  /* ---------------- فروشِ نقدیِ سه‌قلمی، فقط کیبورد ---------------- */

  /**
   *  صندوقِ بی‌صفحه: همان جدولِ `PosKeys`، همان `PosSearch`/`PosCatalog`،
   *  همان `addToCartCapped` و `record` که `DeskSaleScreen` صدا می‌زند.
   *  شمارش طبقِ §۷: هر key-down یک کلید، Shift و Ctrl هم؛ رقم، حرفِ عبارت
   *  و کلیدهای بارکدخوان شمرده نمی‌شوند.
   */
  private class Till(val d: ShopData, val newId: () -> String) {
    var query = ""
    var hint = 0
    var selected: String? = null
    var cart = emptyList<SalesEngine.CartLine>()
    var keys = 0
    var posted: SalesEngine.Result.Ok? = null

    private fun term() = CartQuery.parse(query).term.trim()
    private fun matches() = PosCatalog.matches(d.products, term())

    /** نوشتنِ رقم یا حرف — شمرده نمی‌شود */
    fun type(text: String) { query += text }

    /** نویسه‌ای که Shift می‌خواهد («*» = Shift+8): دو key-down */
    fun typeShifted(text: String) { keys += 2; query += text }

    /** بارکدخوان: رقم‌ها و Enterِ پسوندش شمرده نمی‌شوند */
    fun scan(code: String) { type(code); press(KeyName.Enter, counted = false) }

    fun press(key: KeyName, primary: Boolean = false, counted: Boolean = true) {
      if (counted) keys += if (primary) 2 else 1
      val a = PosKeys.screen(key, primary)
        ?: PosKeys.field(key, primary, query.isEmpty(), matches().isNotEmpty(), selected != null)
        ?: return
      when (a) {
        PosAction.SUBMIT -> when (val hit = PosSearch.resolve(query, ShopStore.barcodeIndex(d), d.products, matches(), hint)) {
          is PosHit.Add -> {
            cart = SalesEngine.addToCartCapped(cart, hit.product.id, hit.count, ShopStore.stock(d, hit.product.id)).cart
            selected = hit.product.id
            query = ""
          }
          else -> fail("Enter باید کالا را به سبد بیفزاید: $hit")
        }
        PosAction.FINISH -> {
          val r = SalesEngine.record(d, cart, SalesEngine.Checkout(), "2026-10-17", 1_760_000_000_000, newId)
          posted = r as? SalesEngine.Result.Ok ?: run { fail("فروش ثبت نشد: $r"); null }
          cart = emptyList()
        }
        else -> fail("کلیدِ ناخواسته: $a")
      }
    }
  }

  @Test
  fun `فروشِ نقدیِ سه‌قلمی فقط با کیبورد — حداکثر ۸ کلید (۱٫۳ ردیف ۳، S01)`() {
    val till = Till(ledger(), ::newId)
    till.scan("626001000")                 // ۱) اسکنِ کالای A
    till.type("3"); till.typeShifted("*")  // ۲) «3*» …
    till.scan("626001003")                 //    … و اسکنِ کالای B
    till.type("نوشا"); till.press(KeyName.Enter) // ۳) نام و Enter روی پیشنهادِ اول
    assertEquals(listOf("p1" to 1.0, "p3" to 3.0, "p4" to 1.0), till.cart.map { it.productId to it.quantity })
    till.press(KeyName.F9)                 // ۴) پایان با نقدِ دقیق (رفتارِ امروزِ F9)

    val sale = till.posted?.data?.sales?.last() ?: return fail("فروش ثبت نشد")
    assertEquals(120.0 + 3 * 260.0 + 80.0, sale.finalTotal, 0.0)
    assertEquals(3, till.posted!!.data.saleItems.count { it.saleId == sale.id })
    assertTrue("سبد پس از ثبت خالی است", till.cart.isEmpty())
    assertTrue("کلیدِ شمرده‌شده ${till.keys} > ۸", till.keys <= 8)
    assertEquals(4, till.keys)
  }

  @Test
  fun `همان فروش با Ctrl+Enter هم زیرِ ۸ کلید می‌ماند`() {
    val till = Till(ledger(), ::newId)
    till.scan("626001000")
    till.type("3"); till.typeShifted("*"); till.scan("626001003")
    till.type("نوشا"); till.press(KeyName.Enter)
    till.press(KeyName.Enter, primary = true)
    assertTrue(till.posted != null)
    assertEquals(5, till.keys)
  }
}
