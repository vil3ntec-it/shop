package ir.vil3ntec.tohid.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrShift
import ir.vil3ntec.tohid.data.CashShift
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.print.PrinterRegistry
import ir.vil3ntec.tohid.ui.screens.ShiftPanelV2
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 *  PAY-19 — «کشو بی فروش» ِ صندوقِ شیفتِ **جدولِ فاکتورهای کامپیوتر** (`ShiftPanelV2`، پشتِ `payV2`) با همان مجوزِ صندوقِ گوشیِ
 *  نسخهٔ ۳ (`DrawerNoSale.phoneAction` با `ShopRole.canOpenDrawer` = `drawer.open`)، نه «شاگرد نیست»: صندوق‌داری که سیاستِ دکان
 *  کشو را برایش باز کرده ثبت می‌کند؛ مدیری که سیاست برایش بسته ثبت نمی‌کند. بی چاپگرِ کشودار ⇒ فقط ردیفِ `no_sale` با دلیل و تأیید.
 *  ⛔ روی `claude/batch-57` (e69b30114) دکمهٔ «ثبت» با `ShopRole.isStaff` بود: اولی بسته و دومی ردیف می‌نوشت.
 */
@OptIn(ExperimentalTestApi::class)
class ShiftNoSaleDeskUiTest {

  @get:Rule val rule = createComposeRule()

  private val ctx = DesktopContext
  private var ids = 0
  private val data = (CashShift.open(ShopData(), 0.0, "مینا", "2026-11-12", 1_000L) { "ns-${++ids}" } as CashShift.Result.Ok).data
  private lateinit var store: ShopStore
  private val toasts = mutableListOf<String>()

  private fun clean() {
    ctx.filesDir.listFiles().orEmpty().filter { it.name.startsWith("shop-data.json") }.forEach { it.deleteRecursively() }
    ShopRole.remember(ctx, ShopRole.OWNER)
    ShopRole.rememberPolicy(ctx, null)
  }

  @Before fun up() {
    I18n.source = { Lang.FA }
    clean()
    assumeTrue("چاپگرِ پیش‌فرضِ کشودار ⇒ راهِ ضربه، نه این آزمون", PrinterRegistry.defaultSaved(ctx)?.drawer != true)
  }

  @After fun down() = clean()

  private fun role(role: String, drawerOpen: String) {
    ShopRole.remember(ctx, role)
    ShopRole.rememberPolicy(ctx, """{"v":1,"layers":[{"drawer.open":{"mode":"$drawerOpen"}}]}""")
  }

  private fun show() {
    store = ShopStore(ctx).also { s -> runBlocking { s.load(); assertTrue(s.save(data)) } }
    rule.setContent {
      CompositionLocalProvider(LocalContext provides DesktopContext) {
        TohidTheme(ThemeChoice.LIGHT) {
          val live by store.data.collectAsState()
          ShiftPanelV2(d = live, store = store, me = "مینا") { toasts += it }
        }
      }
    }
    rule.waitUntil(15_000) { has(StrShift.noSale) }
    rule.onAllNodes(hasText(StrShift.noSale) and hasClickAction())[0].performSemanticsAction(SemanticsActions.OnClick)
    rule.waitUntil(10_000) { has(StrShift.noSaleReason) }
    rule.onAllNodes(hasSetTextAction() and (hasText(StrShift.noSaleReason) or hasContentDescription(StrShift.noSaleReason)))[0].performTextInput("پولِ خرد برای مشتری")
    rule.waitForIdle()
  }

  private fun has(text: String) = rule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()

  /** دکمهٔ «ثبت» ِ پنجره */
  private fun recordEnabled(): Boolean =
    rule.onAllNodes(hasText(StrShift.record) and hasClickAction()).fetchSemanticsNodes().single().config.getOrNull(SemanticsProperties.Disabled) == null

  private fun noSales() = store.data.value.auditLog.filter { it.type == "no_sale" }

  @Test fun aCashierThePolicyLetsOpenTheDrawerRecordsTheNoSale() {
    role(ShopRole.CASHIER, "allow")
    show()
    assertFalse("پیامِ «نیازمندِ تأیید» برای نقشی که مجوز دارد", has(StrShift.noSaleNeedsApproval))
    assertTrue("صندوق‌دارِ دارای `drawer.open` «ثبت» را می‌زند", recordEnabled())
    rule.onAllNodes(hasText(StrShift.record) and hasClickAction())[0].performSemanticsAction(SemanticsActions.OnClick)
    rule.waitUntil(10_000) { noSales().isNotEmpty() }
    val row = noSales().single()
    assertEquals("پولِ خرد برای مشتری", row.reason)
    assertEquals("مینا", row.approvedBy)
  }

  @Test fun aManagerThePolicyDeniesCannotRecordTheNoSale() {
    role(ShopRole.MANAGER, "deny")
    show()
    assertTrue("پیامِ «نیازمندِ تأیید»", has(StrShift.noSaleNeedsApproval))
    assertFalse("مدیرِ بی `drawer.open` «ثبت» را نمی‌زند", recordEnabled())
    assertTrue(noSales().isEmpty())
  }
}
