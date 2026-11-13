package ir.vil3ntec.tohid.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrLedger
import ir.vil3ntec.tohid.core.i18n.StrOrderOps
import ir.vil3ntec.tohid.core.i18n.StrShift
import ir.vil3ntec.tohid.core.orders.OrderNotice
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 *  NOTIF-06 «اعلانِ داخلِ برنامه» (§۳۰.۱ بندِ ۳) روی صفحهٔ واقعیِ Compose ِ کامپیوتر: نوارِ بالای پنجره فقط وقتی زنگ می‌زند،
 *  با شمارهٔ سفارش؛ «دیدم» زنگ را می‌خواباند و چیزی باز نمی‌کند؛ «باز کردن» همان سفارش را باز می‌کند.
 */
class OrderAlarmBannerUiTest {

  @get:Rule val rule = createComposeRule()

  private class Port(private val list: List<OrderNotice>) : OrderTrayAlarm.Port {
    val acked = ArrayList<String>()
    val opened = ArrayList<String?>()
    override fun now() = 1_800_000_000_000L
    override suspend fun on() = true
    override suspend fun pending() = list
    override fun firstShown(arrived: List<OrderNotice>) = arrived
    override fun announce(arrived: List<OrderNotice>, ringing: Int) {}
    override fun chime() {}
    override fun ack(notices: List<OrderNotice>) { acked += notices.map { it.id } }
    override suspend fun flush() {}
    override fun open(orderId: String?) { opened += orderId }
  }

  @Before fun lang() { I18n.source = { Lang.FA } }

  private fun show(alarm: OrderTrayAlarm) = rule.setContent {
    CompositionLocalProvider(LocalContext provides DesktopContext) { TohidTheme(ThemeChoice.LIGHT) { OrderAlarmBanner(alarm) } }
  }

  private fun count(text: String) = rule.onAllNodesWithText(text).fetchSemanticsNodes().size

  @Test fun `نوار تا دیدم می‌ماند و دیدم چیزی باز نمی‌کند`() {
    val port = Port(listOf(OrderNotice("n1", "ord1", 12)))
    val alarm = OrderTrayAlarm(port)
    show(alarm)
    assertEquals("بی زنگ هیچ نواری نیست", 0, count(StrLedger.newOrderNo(12)))
    runBlocking { alarm.round() }
    rule.waitForIdle()
    assertEquals(1, count(StrLedger.newOrderNo(12)))
    rule.onNodeWithText(StrOrderOps.startSeen).performClick()
    rule.waitForIdle()
    assertEquals(0, count(StrLedger.newOrderNo(12)))
    assertEquals(listOf("n1"), port.acked)
    assertEquals(emptyList<String?>(), port.opened)
  }

  @Test fun `باز کردن همان سفارش را باز می‌کند`() {
    val port = Port(listOf(OrderNotice("n1", "ord1", 12)))
    val alarm = OrderTrayAlarm(port)
    show(alarm)
    runBlocking { alarm.round() }
    rule.waitForIdle()
    rule.onNodeWithText(StrShift.openRegister).performClick()
    rule.waitForIdle()
    assertEquals(listOf<String?>("ord1"), port.opened)
    assertEquals(0, count(StrLedger.newOrderNo(12)))
  }
}
