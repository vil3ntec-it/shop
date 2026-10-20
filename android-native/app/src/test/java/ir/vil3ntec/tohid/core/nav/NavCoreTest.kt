package ir.vil3ntec.tohid.core.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** موتورِ خالصِ مسیر: الگو، پشتهٔ برگشت، پیوندها و حالت‌های برنامه */
class NavCoreTest {

  @Test
  fun `الگو پارامتر و پرسش را می‌گیرد و بخشِ ثابت باید عیناً برابر باشد`() {
    val p = RoutePattern("stock/{id}/adjust")
    val m = p.match("stock/42/adjust?from=home")!!
    assertEquals("42", m["id"])
    assertEquals("home", m["from"])
    assertNull(p.match("stock/42"))
    assertNull(p.match("stock//adjust"))
    assertNull(p.match("products/42/adjust"))
    assertEquals(2, p.literals)
    assertEquals("stock", p.base)
  }

  @Test
  fun `ساختنِ مسیر و پرسش با رمزگذاری`() {
    assertEquals("products/a%20b", RoutePattern("products/{id}").build("id" to "a b"))
    assertEquals("products/new?barcode=626001", RoutePaths.withQuery("products/new", "barcode" to "626001"))
    assertEquals("stock", RoutePaths.withQuery("stock", "filter" to null))
    assertEquals("stock?filter=low&q=%D8%B4%DB%8C%D8%B1", RoutePaths.withQuery("stock", "filter" to "low", "q" to "شیر"))
    assertEquals("شیر", RoutePattern("stock").match("stock?q=%D8%B4%DB%8C%D8%B1")!!["q"])
    assertEquals("settings", RoutePaths.base("/settings/security/"))
  }

  @Test
  fun `پشته: رفتن، برگشت، تب و برگشت به صفحه‌ای که پایین‌تر هست`() {
    var s = NavStack.of("home")
    s = s.push("more"); assertEquals(Move.PUSH, s.move)
    s = s.push("settings/security"); assertEquals(listOf("home", "more", "settings/security"), s.entries)
    s = s.pop(); assertEquals("more", s.current); assertEquals(Move.POP, s.move)
    s = s.push("settings/security").push("more")
    assertEquals(listOf("home", "more"), s.entries)
    assertEquals(Move.POP, s.move)
    assertEquals(Move.NONE, s.push("more").move)
    s = s.switchTab("products"); assertEquals(listOf("products"), s.entries); assertEquals(Move.SWITCH, s.move)
    assertFalse(s.pop().canPop)
  }

  @Test
  fun `پشته از اندازه نمی‌گذرد و ریشه می‌ماند؛ پرشِ هم‌سطحِ کامپیوتر`() {
    var s = NavStack.of("home")
    repeat(40) { s = s.push("p/$it") }
    assertEquals(NavStack.MAX_DEPTH, s.depth)
    assertEquals("home", s.root)
    assertEquals("p/39", s.current)
    val j = NavStack.of("home").push("reports").jump("stock")
    assertEquals(listOf("home", "stock"), j.entries)
    assertEquals(listOf("home"), j.jump("home").entries)
    assertEquals(listOf("home", "x"), NavStack.of("home").push("y").replaceTop("x").entries)
  }

  @Test
  fun `پیوندِ برنامه: vill3n__app با حالتِ درست`() {
    assertEquals(DeepLink(AppMode.SHOP, "settings/security"), DeepLinks.parse("vill3n://app/settings/security"))
    assertEquals(DeepLink(AppMode.SHOP, "products?filter=out"), DeepLinks.parse("vill3n://app/products?filter=out"))
    assertEquals(AppMode.SHOPPER, DeepLinks.parse("vill3n://app/shopper/home")!!.mode)
    assertEquals(AppMode.COURIER, DeepLinks.parse("vill3n://app/courier")!!.mode)
    assertEquals("vill3n://app/stock/42", DeepLinks.appLink("/stock/42"))
    assertNull(DeepLinks.parse("vill3n://other/x"))
    assertNull(DeepLinks.parse("vill3n://app/"))
    assertNull(DeepLinks.parse("ftp://vill3n.top/store/abc"))
    assertNull(DeepLinks.parse(null))
  }

  @Test
  fun `ویترین: همان دو شکلی که سرور می‌سازد ⇐ حالتِ خرید`() {
    assertEquals(DeepLink(AppMode.SHOPPER, "shopper/shop/karim-store"), DeepLinks.parse("https://karim-store.vill3n.top/"))
    assertEquals(DeepLink(AppMode.SHOPPER, "shopper/shop/karim-store"), DeepLinks.parse("https://vill3n.top/store/karim-store"))
    assertEquals(DeepLink(AppMode.SHOPPER, "shopper/shop/karim-store"), DeepLinks.parse("https://api.vill3n.top/store/Karim-Store?x=1"))
    assertEquals(DeepLink(AppMode.SHOPPER, "shopper/order/karim-store/01HX.abc"), DeepLinks.parse("https://vill3n.top/store/karim-store#/o/01HX.abc"))
    //  میزبانِ سرورِ همین ساخت (`API_BASE`)
    assertEquals(DeepLink(AppMode.SHOPPER, "shopper/shop/abc"), DeepLinks.parse("https://shop.example.af/store/abc", setOf("shop.example.af")))
    assertNull(DeepLinks.parse("https://shop.example.af/store/abc"))
    //  نام‌های رزرو، نشانی‌های دیگرِ سایت، کوتاه/بلندِ نادرست و `vill3n.top/<slug>` (سرور نمی‌سازد)
    assertNull(DeepLinks.parse("https://api.vill3n.top/"))
    assertNull(DeepLinks.parse("https://admin.vill3n.top/"))
    assertNull(DeepLinks.parse("https://vill3n.top/market"))
    assertNull(DeepLinks.parse("https://vill3n.top/karim-store"))
    assertNull(DeepLinks.parse("https://vill3n.top/store/a"))
    assertNull(DeepLinks.parse("https://vill3n.top/store/admin"))
    assertNull(DeepLinks.parse("https://evil.example/store/karim"))
    assertNull(DeepLinks.parse("https://karim.vill3n.top/admin"))
  }

  @Test
  fun `حالت‌ها: خرید همیشه؛ نصبِ پیشین همان رفتارِ امروز`() {
    val shop = ModeFacts(shopMember = true, courier = false)
    val courier = ModeFacts(shopMember = false, courier = true)
    val none = ModeFacts(shopMember = false, courier = false)
    assertEquals(listOf(AppMode.SHOP, AppMode.SHOPPER), AppModes.available(shop))
    assertEquals(listOf(AppMode.COURIER, AppMode.SHOPPER), AppModes.available(courier))
    assertEquals(listOf(AppMode.SHOPPER), AppModes.available(none))
    assertEquals(AppMode.SHOP, AppModes.resolve(null, shop))
    assertEquals(AppMode.COURIER, AppModes.resolve(null, courier))
    assertEquals(AppMode.COURIER, AppModes.resolve(AppMode.SHOP, courier))
    assertEquals(AppMode.SHOPPER, AppModes.resolve(AppMode.SHOPPER, shop))
    assertEquals(AppMode.SHOPPER, AppModes.resolve(null, none))
    assertEquals("home", AppModes.home(AppMode.SHOP))
    assertTrue(AppMode.entries.all { AppMode.of(it.id) == it })
  }

  @Test
  fun `دسترسی: نیاز و پرچم با هم`() {
    val a = Access(granted = setOf(Need.MANAGE), flags = setOf("module.x"))
    assertTrue(a.allows(setOf(Need.MANAGE), "module.x"))
    assertFalse(a.allows(setOf(Need.MANAGE, Need.CHAT)))
    assertFalse(a.allows(emptySet(), "module.y"))
    assertTrue(Access.all().allows(Need.entries.toSet()))
  }
}
