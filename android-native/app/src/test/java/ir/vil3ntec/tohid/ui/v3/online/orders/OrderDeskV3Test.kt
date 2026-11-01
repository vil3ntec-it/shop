package ir.vil3ntec.tohid.ui.v3.online.orders

import ir.vil3ntec.tohid.core.model.OrderDto
import ir.vil3ntec.tohid.core.orders.OrderDelay
import ir.vil3ntec.tohid.data.repo.OrderDeskRepository.Desk
import ir.vil3ntec.tohid.ui.screens.DeskPauseForm
import ir.vil3ntec.tohid.ui.screens.DeskSettingsForm
import ir.vil3ntec.tohid.ui.screens.OrderDelayForm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  میزِ سفارشِ نسخهٔ ۳ (ORD-03/04/07/08/09/18/19): همان حالتِ رابطِ امروز — برگهٔ توقف، تنظیمِ قبول و توقفِ خودکار،
 *  «عقب انداختن» — و هر دو رابط فقط از همان حالت و همان نوشتن می‌کشند (متن با متن، مثلِ `QuoteWiringTest`).
 */
class OrderDeskV3Test {

  private fun src(rel: String): String =
    listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
      .map { File(it, "ir/vil3ntec/tohid/$rel") }.firstOrNull { it.isFile }?.readText() ?: error("$rel پیدا نشد (cwd=${File(".").absoluteFile})")

  private fun count(rel: String, what: String) = Regex(Regex.escape(what)).findAll(src(rel)).count()

  @Test
  fun `تنظیم قبول و توقف خودکار فقط با عددهای در بازه`() {
    val d = Desk(enabled = true, autoAcceptMaxOpen = 5, version = 7)
    val f = DeskSettingsForm(d)
    assertEquals(d, f.result(d))
    f.aaOn = true
    f.apMin = "45"
    assertEquals(d.copy(autoAcceptOn = true, autoPauseMinutes = 45), f.result(d))
    f.apMin = "4"
    assertNull(f.result(d))
    f.apMin = "30"
    f.aaMax = "0"
    assertNull(f.result(d))
    f.aaMax = "abc"
    assertNull(f.result(d))
  }

  @Test
  fun `توقف دلیل می خواهد و دلیل دیگر توضیح`() {
    val f = DeskPauseForm()
    assertFalse(f.ok)
    f.reason = "busy"
    assertTrue(f.ok)
    f.reason = "other"
    assertFalse(f.ok)
    f.edit("x".repeat(250))
    assertEquals(200, f.text.length)
    assertTrue(f.ok)
  }

  @Test
  fun `عقب انداختن همان قاعده هسته و یک کلید برای هر برگه`() {
    val f = OrderDelayForm(OrderDto(id = "o1", orderNo = 1042, status = "accepted"), 60, OrderDelay.REASONS)
    assertEquals(OrderDelay.minutes(60)[1], f.minutes)
    f.pick("courier")
    assertEquals("courier", f.reason)
    f.pick("courier")
    assertEquals("", f.reason)
    f.edit("y".repeat(400))
    assertEquals(300, f.note.length)
    assertEquals(OrderDelay.valid(f.minutes, f.reason, f.note, 60), f.valid)
    assertTrue(f.key.isNotBlank())
    assertFalse(f.busy)
  }

  @Test
  fun `صف و جزئیات نسخه ۳ همان میز و هشدار و گروهی و عقب انداختن را دارند`() {
    val route = "ui/v3/online/orders/OrdersRouteContent.kt"
    listOf("OrderDeskStrip(deskCtl)", "OrderAlertNotes(", "OrderBulkBarV3(", "OrderBulkToggleV3(", "OrderDelayEntryV3(", "bulk.tap(o.id)")
      .forEach { assertEquals(it, 1, count(route, it)) }
    //  هر دو رابط از همان حالت: هیچ نسخهٔ دومی از بار و نوشتن نیست
    assertEquals(1, count("ui/screens/OrderDeskPanel.kt", "rememberOrderDesk(api)"))
    assertEquals(1, count("ui/screens/OrderDelayUi.kt", "OrderDelayForm(o, maxMinutes, reasons)"))
    assertEquals(1, count("ui/v3/online/orders/OrderDelayV3.kt", "OrderDelayForm(o, maxMinutes, reasons)"))
    assertEquals(1, count("ui/screens/OrderBulkUi.kt", "s.send(repo, printer, items, selected, to, reason, note, ready, onMessage)"))
    assertEquals(1, count("ui/v3/online/orders/OrderBulkV3.kt", "s.send(repo, printer, items, selected, to, reason, note, ready, onMessage)"))
    assertEquals(1, count("ui/screens/OrderDeskState.kt", "repo.state().onSuccess { desk = it"))
    assertEquals(0, count("ui/v3/online/orders/OrderDeskStrip.kt", "repo.state()"))
  }
}
