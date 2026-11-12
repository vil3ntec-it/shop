package ir.vil3ntec.tohid.core.keymap

import ir.vil3ntec.tohid.core.orders.OrderDesk
import ir.vil3ntec.tohid.ui.AppNav
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  سناریوی S42 (§۷.۴، ORD-08) — «Ctrl+B ‹شلوغم› برای ۳۰ دقیقه». سمتِ سرور (زمانِ تحویلِ ویترین و پایانِ خودکار)
 *  در `server/test/e2e/scenario-s42.e2e.mjs` است؛ این‌جا کلیدِ برنامهٔ کامپیوتر:
 *   • `orders.busy` در keymap.json زنده است، Mod+B، دامنهٔ «سفارش‌ها»، و هیچ کلیدِ سراسری یا همان صفحه Ctrl+B نیست؛
 *   • صفحهٔ سفارشِ نسخهٔ ۳ (`OrderDeskStrip`) کار را با `ScreenKey` می‌گذارد، فقط همان‌جا که دکمهٔ «شلوغم» هست
 *     (`OrderDesk.busyKey`: مجوزِ مدیریت و میزِ باز) و همان `repo.busy(d, 30)` ِ دکمه را می‌زند (`OrderDeskPanel` ِ
 *     رابطِ امروز بی‌راه بود و برداشته شد، `claude/e-rows-39m`)؛
 *   • پنجرهٔ کامپیوتر کلید را با `AppNav.dispatchKey` به کارِ ثبت‌شده می‌رساند؛ planned هرگز.
 */
class ScenarioS42KeyTest {

  private fun source(rel: String): File? = listOf(rel, "../$rel", "../../$rel").map { File(it) }.firstOrNull { it.isFile }

  @After fun after() { AppNav.screenKeys.clear() }

  @Test
  fun `S42 Ctrl+B زنده و فقط کارِ شلوغم است`() {
    val busy = KeyMap.ALL.single { it.id == "orders.busy" }
    assertEquals(KeyMap.ORDERS_BUSY, busy)
    assertEquals("orders", busy.scope)
    assertEquals(listOf(Chord(KeyName.B, mod = true)), busy.keys)
    assertTrue(busy.status != KeyStatus.PLANNED)
    val ctrlB = KeyMap.ALL.filter { Chord(KeyName.B, mod = true) in it.keys && (it.scope == "orders" || it.scope == "global") }
    assertEquals(listOf("orders.busy"), ctrlB.map { it.id })
    assertEquals(30, OrderDesk.busyKey(canManage = true, status = "open"))
  }

  @Test
  fun `S42 کلید به کارِ ثبت‌شدهٔ صفحه می‌رسد و planned نه`() {
    var hits = 0
    assertFalse("هیچ صفحه‌ای چیزی نگذاشته", AppNav.dispatchKey { it.id == "orders.busy" })
    AppNav.screenKeys[KeyMap.ORDERS_BUSY.id] = { hits++ }
    assertTrue(AppNav.dispatchKey { it.id == "orders.busy" })
    assertEquals(1, hits)
    assertFalse("کلیدِ دیگر", AppNav.dispatchKey { it.id == "orders.pause" })
    AppNav.screenKeys[KeyMap.ORDERS_PAUSE.id] = { hits += 10 }
    assertFalse("planned اجرا نمی‌شود", AppNav.dispatchKey { it.id == "orders.pause" })
    assertEquals(1, hits)
  }

  @Test
  fun `S42 صفحه‌های سفارش و پنجرهٔ کامپیوتر کلید را وصل کرده‌اند`() {
    val base = "android-native/app/src/main/java/ir/vil3ntec/tohid/ui"
    for (rel in listOf("$base/v3/online/orders/OrderDeskStrip.kt")) {
      val f = source(rel) ?: source(rel.removePrefix("android-native/app/"))
      assertNotNull(rel, f)
      val text = f!!.readText()
      assertTrue(rel, text.contains("val busyKey = OrderDesk.busyKey(d.canManage, status)"))
      assertTrue(rel, Regex("""ScreenKey\(ir\.vil3ntec\.tohid\.core\.keymap\.KeyMap\.ORDERS_BUSY, enabled = busyKey != null""").containsMatchIn(text))
      assertTrue(rel, text.contains("repo.busy(d, busyKey)"))
    }
    val main = source("desktop/src/main/kotlin/ir/vil3ntec/tohid/desktop/Main.kt") ?: source("src/main/kotlin/ir/vil3ntec/tohid/desktop/Main.kt")
    assertTrue(main!!.readText().contains("else -> ir.vil3ntec.tohid.ui.AppNav.dispatchKey { e.matches(it) }"))
  }
}
