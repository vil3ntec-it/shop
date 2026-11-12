package ir.vil3ntec.tohid.scenario

import ir.vil3ntec.tohid.core.pay.CashKeys
import ir.vil3ntec.tohid.core.pos.Keypad
import ir.vil3ntec.tohid.core.pos.TouchKeyboard
import ir.vil3ntec.tohid.core.pos.TouchMode
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.WarehouseEntry
import ir.vil3ntec.tohid.ui.components.pos.PosCartOps
import ir.vil3ntec.tohid.ui.components.pos.PosCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  S18 — حالتِ لمسی (POS-31، POS-06، POS-07) روی قاعده‌های هسته (`TouchMode`، `Keypad`، `CashKeys`) و افزودنِ کاشیِ
 *  صندوقِ کامپیوتر (`PosCartOps.addStep`)؛ در برنامه پشتِ تنظیمِ خاموشِ همین دستگاه (`PosTouchPrefs`).
 *
 *  گامِ ۳ (جست‌وجوی کالا با نام فقط با صفحه‌کلیدِ روی‌صفحه): کلیدِ «حروف» ِ کادرِ اسکن (`TouchKeyboard`، فارسی/دری، پښتو،
 *  لاتین) نوشته را می‌سازد و همان جست‌وجوی صندوق (`PosCatalog.matches`) کالا را می‌یابد.
 *
 *  گامِ ۴ (مشتریِ تازه با نامِ دری): نامِ دری با نیم‌فاصله از همان کلیدها ⇒ همان `DebtorEngine.add` ِ «مشتریِ تازه» ِ
 *  گزینشِ مشتریِ صندوق؛ پنجرهٔ واقعیِ صندوق (فوکوسِ کادر، دو زدنِ تند ⇒ یک مشتری، دیسک): `TillNewCustomerUiTest` ِ desktop.
 *
 *  ماندهٔ سناریو (pending، نه سرخ): آزمونِ عملی روی کامپیوترِ لمسیِ واقعی (و شمارشِ لمس روی صفحهٔ واقعی) انجام نشده؛
 *  شمارشِ زیر روی همان قاعده‌هاست.
 */
class ScenarioS18Test {

  private val items = listOf(
    Product(id = "p1", name = "نان", unit = "عدد", salePrice = 20.0, category = "نان"),
    Product(id = "p2", name = "شیر", unit = "عدد", salePrice = 70.0, category = "لبنیات"),
    Product(id = "p3", name = "پنیر", unit = "عدد", salePrice = 260.0, category = "لبنیات"),
  )
  private val d = ShopData(products = items, warehouseEntries = items.map { WarehouseEntry(id = "w${it.id}", productId = it.id, units = 30.0) })

  @Test
  fun `S18 — حالتِ لمسی روشن ⇒ هدفِ ۴۸ و صفحه‌کلیدِ عددی و دکمهٔ صفحه`() {
    assertFalse("تنظیمِ خاموش ⇒ همان صندوقِ موشی", TouchMode.touch(desktop = true, on = false, active = true))
    assertTrue(TouchMode.touch(desktop = true, on = true, active = true))
    assertTrue(TouchMode.touchExtras(desktop = true, on = true, active = true))
    assertTrue(TouchMode.TOUCH_TARGET_DP >= 48)
    //  فهرستِ بلند: ۱۰۰ کالا، ۱۲ تا پیدا ⇒ «صفحهٔ بعد» ۱۲ جلو، آخرِ فهرست خاموش
    assertEquals(12, TouchMode.page(0, 12, 100, forward = true))
    assertEquals(96, TouchMode.page(84, 12, 100, forward = true))
    assertEquals(84, TouchMode.page(96, 12, 100, forward = false))
    assertFalse(TouchMode.canPage(88, 12, 100, forward = true))
  }

  @Test
  fun `S18 گامِ ۳ — جست‌وجوی کالا با نام فقط با صفحه‌کلیدِ حروفِ روی‌صفحه`() {
    assertFalse("تنظیمِ خاموش ⇒ کلیدِ «حروف» نیست", TouchMode.touchExtras(desktop = true, on = false, active = true))
    var layout = TouchKeyboard.initial("prs")
    val keys = TouchKeyboard.rows(layout).flatten()
    var query = ""
    var touches = 1 // کلیدِ «حروف»
    for (ch in "پنی") {
      query = TouchKeyboard.apply(query, keys.single { it == TouchKeyboard.Key.Letter(ch.toString()) }); touches++
    }
    assertEquals("پنی", query)
    assertEquals(listOf("p3"), PosCatalog.matches(d.products, query.trim(), false).map { it.id })
    //  غلط ⇒ پاک کردنِ حرفِ آخر؛ «پاک کردنِ همه» کادر را خالی می‌کند
    query = TouchKeyboard.apply(TouchKeyboard.apply(query, TouchKeyboard.Key.Letter("ز")), TouchKeyboard.Key.Backspace)
    assertEquals("پنی", query)
    assertEquals("", TouchKeyboard.apply(query, TouchKeyboard.Key.Clear))
    //  نامِ پښتو با چیدمانِ پښتو (کلیدِ چیدمان یک بار)
    layout = TouchKeyboard.next(layout)
    assertEquals(TouchKeyboard.Layout.PS, layout)
    assertTrue(TouchKeyboard.rows(layout).flatten().containsAll("ډړښ".map { TouchKeyboard.Key.Letter(it.toString()) }))
    assertTrue("لمس‌ها: $touches", touches <= 5)
  }

  /**
   *  POS-31 (§۵.۳: صفحه‌کلیدِ روی‌صفحه برای هر کادرِ متنیِ حالتِ لمسی) — گزینشِ مشتری در صندوقِ کامپیوترِ لمسی (F2 و
   *  «دریافت»): همان کلیدِ «حروف» و همان `TouchKeyboard` کنارِ کادرِ نامِ مشتری؛ گوشی و تنظیمِ خاموش همان پنجرهٔ امروز.
   */
  @Test
  fun `S18 — نامِ مشتری در صندوقِ کامپیوترِ لمسی هم با صفحه‌کلیدِ حروف`() {
    var query = ""
    val keys = TouchKeyboard.rows(TouchKeyboard.initial("prs")).flatten()
    for (ch in "احم") query = TouchKeyboard.apply(query, keys.single { it == TouchKeyboard.Key.Letter(ch.toString()) })
    val names = listOf("احمد ولی", "محمود", "زهرا")
    assertEquals(listOf("احمد ولی"), names.filter { it.contains(query.trim(), ignoreCase = true) })

    fun src(rel: String) = listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
      .map { java.io.File(it, rel) }.firstOrNull { it.isFile }?.readText() ?: error("$rel پیدا نشد")
    val picker = src("ir/vil3ntec/tohid/ui/screens/SaleDialogs.kt").substringAfter("fun DebtorPicker(").substringBefore("fun CartLineDialog(")
    assertTrue(picker.contains("touch: Boolean = false,"))
    assertTrue(picker.contains("TouchKeysToggle(kb, touch, 48.dp)"))
    assertTrue(picker.contains("TouchKeysPanel(kb, touch, 48.dp) { search = ir.vil3ntec.tohid.core.pos.TouchKeyboard.apply(search, it) }"))
    //  هر دو گزینشِ مشتریِ صندوقِ کامپیوتر (F2 و «دریافت») با `PosTouchPrefs.extras()`؛ گوشی پیش‌فرض (خاموش)
    assertEquals(2, Regex(Regex.escape("touch = touchExtras, onClose =")).findAll(src("ir/vil3ntec/tohid/ui/screens/DeskSale.kt")).count())
    //  گوشیِ نسخهٔ ۳ (گزینشِ مشتریِ برگهٔ پرداخت) بی صفحه‌کلیدِ لمسی؛ `ui/screens/Sale.kt` فقط پیش‌نمایشِ DevShot است
    for (rel in listOf("ui/v3/sales/checkout/CustomerPickerSheet.kt", "ui/v3/sales/checkout/CustomerRoute.kt")) {
      assertFalse(rel, src("ir/vil3ntec/tohid/$rel").contains("touchExtras"))
    }
  }

  @Test
  fun `S18 گامِ ۴ — مشتریِ تازه با نامِ دری فقط با صفحه‌کلیدِ حروف`() {
    val keys = TouchKeyboard.rows(TouchKeyboard.initial("prs")).flatten()
    fun letter(ch: Char) = keys.single { it == TouchKeyboard.Key.Letter(ch.toString()) }
    var name = ""
    var touches = 0
    for (ch in "ذبیح") { name = TouchKeyboard.apply(name, letter(ch)); touches++ }
    name = TouchKeyboard.apply(name, keys.single { it == TouchKeyboard.Key.HalfSpace }); touches++
    for (ch in "الله") { name = TouchKeyboard.apply(name, letter(ch)); touches++ }
    assertEquals("ذبیح\u200Cالله", name)
    val d = ShopData(debtors = listOf(ir.vil3ntec.tohid.data.Debtor(id = "a", name = "احمد")))
    val r = ir.vil3ntec.tohid.data.DebtorEngine.add(d, ir.vil3ntec.tohid.data.DebtorEngine.DebtorDraft(name = name), 1_000L) { "c-dari" }
    assertTrue("$r", r is ir.vil3ntec.tohid.data.DebtorEngine.Result.Ok)
    val made = (r as ir.vil3ntec.tohid.data.DebtorEngine.Result.Ok).data.debtors
    assertEquals(listOf("احمد", name), made.map { it.name })
    assertEquals("c-dari", r.id)
    //  کلیدِ «حروف» + ۹ لمسِ نام + «مشتریِ تازه»
    assertTrue("لمس‌ها: $touches", touches + 2 <= 12)
  }

  private var seq = 0

  @Test
  fun `S18 — فروشِ ۳ قلمی فقط با لمس و حداکثر ۱۰ لمس`() {
    var touches = 0
    var cart = emptyList<SalesEngine.CartLine>()
    //  شبکهٔ کالاها: هر کاشی یک لمس
    for (p in items) { cart = PosCartOps.addStep(cart, p, ShopStore.stock(d, p.id)).cart; touches++ }
    //  صفحه‌کلیدِ عددی: «۲» برای تعدادِ نان (یک لمس رقم + یک لمسِ ثبت)
    var k = Keypad.State(mode = Keypad.Mode.QTY)
    k = Keypad.press(k, Keypad.Key.D2); touches++
    val qty = Keypad.entry(k) as Keypad.Entry.Qty
    cart = SalesEngine.setCartQty(cart, "p1", qty.milli / 1000.0); touches++
    val total = SalesEngine.cartTotal(d, cart)
    assertEquals(2 * 20.0 + 70.0 + 260.0, total, 0.0)
    //  پرداخت: دکمهٔ پرداخت، اسکناسِ ۵۰۰، ثبت
    touches++
    val cash = CashKeys.addNote("", 50_000)!!
    touches++
    assertTrue(CashKeys.cashMinor(cash)!! >= (total * 100).toLong())
    touches++
    val sold = SalesEngine.record(d, cart, SalesEngine.Checkout(), "2026-11-02", 1_000, { "t${seq++}" })
    assertTrue(sold is SalesEngine.Result.Ok)
    assertTrue("لمس‌ها: $touches", touches <= 10)
  }
}
