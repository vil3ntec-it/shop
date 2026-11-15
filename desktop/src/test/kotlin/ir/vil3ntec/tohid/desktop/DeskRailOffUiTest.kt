package ir.vil3ntec.tohid.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.LayoutDirection
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrDeskOff
import ir.vil3ntec.tohid.core.i18n.StrNav
import ir.vil3ntec.tohid.core.model.OrdersConfigDto
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.ui.OrdersLive
import ir.vil3ntec.tohid.ui.screens.DeliveryGate
import ir.vil3ntec.tohid.ui.screens.DeskRail
import ir.vil3ntec.tohid.ui.screens.StorefrontGate
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import ir.vil3ntec.tohid.ui.v3.account.AccountPaths
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 *  منوی کناریِ کامپیوتر (e-rows-63n، «مگه اینا رو نساختی؟ چرا نوشته در حالِ ساخت؟»): بخشِ ساخته‌شده هرگز «در دست ساخت»
 *  نیست؛ سفارش‌های خاموش ⇒ «خاموش» و زدنش برگهٔ «چطور روشن می‌شود» را باز می‌کند (صاحب: نامِ پرچم + پیام به پشتیبانی،
 *  شاگرد: «با پشتیبانی تماس بگیرید» بی نامِ پرچم)، و با سفارش‌های روشن «آماده‌سازی و تحویل» برگهٔ آماده‌سازیِ صف را باز می‌کند.
 */
class DeskRailOffUiTest {

  @get:Rule val rule = createComposeRule()

  private val ctx = DesktopContext
  private val picks = ArrayList<String>()
  private var role = ShopRole.OWNER

  @Before fun setUp() {
    I18n.source = { Lang.FA }
    role = ShopRole.current(ctx)
    OrdersLive.config.value = null
    StorefrontGate.config.value = null
    DeliveryGate.config.value = null
  }

  @After fun tearDown() {
    I18n.source = { Lang.FA }
    ShopRole.remember(ctx, role)
    OrdersLive.config.value = null
  }

  private fun show() = rule.setContent {
    CompositionLocalProvider(
      LocalContext provides ctx,
      LocalView provides remember { android.view.View(ctx) },
      LocalLayoutDirection provides LayoutDirection.Rtl,
    ) { TohidTheme(ThemeChoice.LIGHT) { DeskRail(current = "dashboard", onPick = { picks += it }) } }
  }

  private fun count(text: String, substring: Boolean = false) = rule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().size

  private fun click(text: String) {
    rule.onAllNodes(hasText(text) and hasClickAction())[0].performSemanticsAction(SemanticsActions.OnClick)
    rule.waitForIdle()
  }

  @Test fun ordersOffShowOffAndTheOwnerSheetExplainsHowToSwitchItOn() {
    ShopRole.remember(ctx, ShopRole.OWNER)
    show()
    rule.waitUntil(5_000) { count(StrNav.onlineOrders) > 0 }
    //  چهار بخشِ ساخته‌شده (سفارش، آماده‌سازی، ویترین، میزِ تحویل) «خاموش» اند و هیچ «در دست ساخت»ی نیست
    assertEquals(4, count(StrDeskOff.badge))
    assertEquals(0, count(StrNav.soon, substring = true))
    click(StrNav.onlineOrders)
    rule.waitUntil(5_000) { count(StrDeskOff.howTitle) > 0 }
    assertEquals(1, count(StrDeskOff.built))
    assertEquals(1, count(StrNav.ordersSub))
    assertEquals(1, count(StrDeskOff.stepScope))
    assertEquals(1, count(StrDeskOff.flagKey("shop.orders")))
    click(StrDeskOff.askSupport)
    rule.waitUntil(5_000) { picks.isNotEmpty() }
    assertEquals(listOf(AccountPaths.support(StrDeskOff.supportDraft(StrNav.onlineOrders))), picks)
    //  برگه با رفتن بسته می‌شود
    rule.waitUntil(5_000) { count(StrDeskOff.howTitle) == 0 }
  }

  @Test fun staffSeeNoFlagNameAndAreSentToSupport() {
    ShopRole.remember(ctx, ShopRole.STAFF)
    show()
    rule.waitUntil(5_000) { count(StrNav.fulfilment) > 0 }
    click(StrNav.fulfilment)
    rule.waitUntil(5_000) { count(StrDeskOff.howTitle) > 0 }
    assertEquals(1, count(StrDeskOff.builtStaff))
    assertEquals(1, count(StrDeskOff.callSupport))
    assertEquals(1, count(StrDeskOff.partOf(StrNav.onlineOrders)))
    assertEquals(0, count("shop.", substring = true))
    assertEquals(0, count(StrDeskOff.askSupport))
    click(Str.support)
    rule.waitUntil(5_000) { picks.isNotEmpty() }
    assertEquals(listOf(AccountPaths.SUPPORT), picks)
  }

  @Test fun ordersOnOpenFulfilmentOnTheQueuePreparingTab() {
    ShopRole.remember(ctx, ShopRole.OWNER)
    OrdersLive.config.value = OrdersConfigDto(flag = true, entitled = true, enabled = true)
    show()
    rule.waitUntil(5_000) { count(StrNav.fulfilment) > 0 }
    //  فقط ویترین و میزِ تحویل خاموش مانده‌اند
    assertEquals(2, count(StrDeskOff.badge))
    assertEquals(0, count(StrNav.soon, substring = true))
    click(StrNav.fulfilment)
    rule.waitUntil(5_000) { picks.isNotEmpty() }
    assertEquals(listOf("orders?tab=preparing"), picks)
    assertEquals(0, count(StrDeskOff.howTitle))
    click(StrNav.onlineOrders)
    assertEquals(listOf("orders?tab=preparing", "orders"), picks)
  }
}
