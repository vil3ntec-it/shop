package ir.vil3ntec.tohid.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrSupportAsk
import ir.vil3ntec.tohid.core.model.OrderArrivalDto
import ir.vil3ntec.tohid.core.model.SupportAskDto
import ir.vil3ntec.tohid.core.model.SupportMessageDto
import ir.vil3ntec.tohid.core.nav.RoutePaths
import ir.vil3ntec.tohid.ui.AppNav
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import ir.vil3ntec.tohid.ui.v3.account.subscription.LockedFeature
import ir.vil3ntec.tohid.ui.v3.account.support.AskSupportButton
import ir.vil3ntec.tohid.ui.v3.account.support.SupportAskModel
import ir.vil3ntec.tohid.ui.v3.account.support.SupportContent
import ir.vil3ntec.tohid.ui.v3.account.support.SupportModel
import ir.vil3ntec.tohid.ui.v3.account.support.SupportUi
import ir.vil3ntec.tohid.ui.v3.online.orders.OrderArrivalCardV3
import ir.vil3ntec.tohid.ui.v3.online.orders.OrderSetupModel
import ir.vil3ntec.tohid.ui.v3.online.orders.OrderSetupRow
import ir.vil3ntec.tohid.ui.v3.settings.sections.SyncModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 *  «از پشتیبانی بخواه» (e-support): دکمهٔ یک‌لمسیِ هر حالتِ بسته‌ای که کارش با پشتیبانی است ⇒ گفت‌وگوی **همان** پشتیبانی با
 *  درخواستِ ساخت‌یافته (کد + واقعیت‌های مجاز). سنجیده می‌شود: دکمه و مقصدش (سفارشِ خاموش، کارتِ «چرا نمی‌رسد»، قفلِ اشتراک و
 *  دستگاه، گرهٔ همگام‌سازی)، نوارِ حالِ درخواست در گفت‌وگو (فرستاده می‌شود/رسید/پیش‌تر رفته/همکار/نرفت + «دوباره»)، و این‌که
 *  کد و نامِ درونیِ پرچم هرگز روی صفحه نمی‌آیند.
 */
class SupportAskUiTest {

  @get:Rule val rule = createComposeRule()

  @Before fun setUp() { I18n.source = { Lang.FA }; AppNav.request.value = null }
  @After fun tearDown() { I18n.source = { Lang.FA }; AppNav.request.value = null }

  @Composable
  private fun Phone(content: @Composable () -> Unit) {
    CompositionLocalProvider(
      LocalContext provides DesktopContext,
      LocalView provides remember { android.view.View(DesktopContext) },
      LocalConfiguration provides Configuration(360, 760),
    ) {
      TohidTheme(ThemeChoice.LIGHT) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
          Box(Modifier.requiredSize(360.dp, 760.dp)) { content() }
        }
      }
    }
  }

  private fun texts(): List<String> = rule.onAllNodes(SemanticsMatcher("any") { true }, useUnmergedTree = true).fetchSemanticsNodes()
    .flatMap { n -> n.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } }

  private fun noInternals() {
    val shown = texts()
    assertFalse("نامِ پرچم یا کد روی صفحه: $shown", shown.any { Regex("""shop\.[a-z]|flag_off|subscription_expired|device_limit|sync_""").containsMatchIn(it) })
  }

  private fun clickButton() {
    rule.onAllNodes(hasText(StrSupportAsk.button) and hasClickAction(), useUnmergedTree = false)[0].performClick()
    rule.waitForIdle()
  }

  @Test fun theButtonOpensSupportWithTheStructuredRequestAndNoInternalNames() {
    val opened = ArrayList<String>()
    rule.setContent { Phone { AskSupportButton("flag_off:shop.orders", facts = mapOf("screen" to "desk:off:orders", "email" to "a@b.cd"), onOpen = { opened += it }) } }
    rule.waitForIdle()
    noInternals()
    clickButton()
    assertEquals(1, opened.size)
    val (seg, q) = RoutePaths.parse(opened[0])
    assertEquals(listOf("support"), seg)
    assertEquals("flag_off:shop.orders", q["ask"])
    assertEquals("desk:off:orders", q["f.screen"])
    assertNull("واقعیتِ نامجاز به نشانی نمی‌رسد", q["f.email"])
    //  همان نشانی ⇒ همان درخواست (آن‌چه گفت‌وگو می‌فرستد)
    val p = SupportAskModel.pendingOf { q[it] }
    assertNotNull(p)
    assertEquals(mapOf("screen" to "desk:off:orders"), p!!.facts)
    assertEquals(
      mapOf("appVersion" to "2.14.5", "platform" to "android", "screen" to "desk:off:orders"),
      SupportAskModel.factsToSend(p, "2.14.5", "android"),
    )
  }

  @Test fun ordersOffRowAndArrivalCardAskSupportForTheSameGate() {
    var supports = 0
    val asks = ArrayList<String>()
    val arrival = OrderArrivalDto(ok = false, blockers = listOf(ir.vil3ntec.tohid.core.model.OrderArrivalItemDto("orders_off", "support")))
    rule.setContent {
      Phone {
        Column {
          OrderSetupRow(OrderSetupModel.Status.Off("orders_off"), onOpen = {}, onSupport = { supports++ })
          OrderArrivalCardV3(arrival, onFix = {}, onAsk = { asks += it })
        }
      }
    }
    rule.waitForIdle()
    noInternals()
    val buttons = rule.onAllNodes(hasText(StrSupportAsk.button) and hasClickAction()).fetchSemanticsNodes().size
    assertTrue("دو دکمه: ردیفِ صف و کارتِ تنظیمات ($buttons)", buttons >= 2)
    rule.onAllNodes(hasText(StrSupportAsk.button) and hasClickAction())[0].performClick()
    rule.onAllNodes(hasText(StrSupportAsk.button) and hasClickAction())[buttons - 1].performClick()
    rule.waitForIdle()
    assertEquals(1, supports)
    assertEquals(listOf("flag_off:shop.orders"), asks)
  }

  @Test fun lockedSubscriptionAndDeviceCapOfferAskSupportBesideTheirOwnWay() {
    rule.setContent { Phone { LockedFeature("فروش", signedIn = true, serverDenied = "device_limit") } }
    rule.waitForIdle()
    noInternals()
    clickButton()
    assertEquals("device_limit", RoutePaths.parse(AppNav.request.value.orEmpty()).second["ask"])
  }

  /** p0r3: بخشی که در اشتراک نیست کلیدِ خودِ قابلیت را می‌فرستد؛ اشتراکِ تمام‌شده همان `subscription_expired` */
  @Test fun lockedSectionAsksForItsOwnFeatureNotJustAnExpiredPlan() {
    val expired = androidx.compose.runtime.mutableStateOf(false)
    rule.setContent { Phone { LockedFeature("قرض‌داران", signedIn = true, feature = "debtors", expired = expired.value) } }
    rule.waitForIdle()
    noInternals()
    clickButton()
    assertEquals("plan_required:debtors", RoutePaths.parse(AppNav.request.value.orEmpty()).second["ask"])
    expired.value = true
    rule.waitForIdle()
    clickButton()
    assertEquals("subscription_expired", RoutePaths.parse(AppNav.request.value.orEmpty()).second["ask"])
  }

  /** p0r3: «حساب‌پی برای این دکان روشن نیست» ِ صندوق ⇒ `flag_off:pay.hesabpay` */
  @Test fun hesabPayOffAtTheTillAsksSupportToTurnTheFlagOn() {
    val opened = ArrayList<String>()
    rule.setContent { Phone { AskSupportButton(ir.vil3ntec.tohid.pay.HesabPayIntentFlow.HESAB_OFF, facts = mapOf("screen" to "till:hesabpay"), onOpen = { opened += it }) } }
    rule.waitForIdle()
    noInternals()
    clickButton()
    val q = RoutePaths.parse(opened.single()).second
    assertEquals("flag_off:pay.hesabpay", q["ask"])
    assertEquals("till:hesabpay", q["f.screen"])
  }

  @Test fun lockedWithoutAccountHasNoAskButton() {
    rule.setContent { Phone { LockedFeature("فروش", signedIn = false) } }
    rule.waitForIdle()
    assertEquals(0, rule.onAllNodes(hasText(StrSupportAsk.button)).fetchSemanticsNodes().size)
  }

  @Test fun syncKnotsThatNeedSupportCarryCodeAndCountOnly() {
    val rejected = SyncModel.diagnose("", holding = false, dropped = 3, needsShop = false, server = ir.vil3ntec.tohid.ui.screens.ServerPulse.State.UP)
    val ask = SyncModel.askOf(rejected, 3)
    assertEquals("sync_refused", ask?.code)
    assertEquals(3, ask?.facts?.get("rejected"))
    val noShop = SyncModel.diagnose("", holding = false, dropped = 0, needsShop = true, server = ir.vil3ntec.tohid.ui.screens.ServerPulse.State.UP)
    assertEquals("sync_no_shop", SyncModel.askOf(noShop, 0)?.code)
    val offline = SyncModel.diagnose("timeout", holding = false, dropped = 0, needsShop = false, server = ir.vil3ntec.tohid.ui.screens.ServerPulse.State.NO_NET)
    assertNull("بی‌اینترنتی کارِ پشتیبانی نیست", SyncModel.askOf(offline, 0))
  }

  @Test fun theChatShowsTheRequestStateAndRetriesOnlyAfterFailure() {
    val now = System.currentTimeMillis()
    val msgs = listOf(
      SupportMessageDto(id = "1", sender = "user", body = "لطفاً سفارشِ آنلاین را برای دکانِ من روشن کنید.", createdAt = now - 60_000),
      SupportMessageDto(id = "2", sender = "admin", senderName = "پشتیبانی", body = "پشتیبانی سفارشِ آنلاین را برای دکانِ شما روشن کرد.", createdAt = now),
    )
    var state by androidx.compose.runtime.mutableStateOf(SupportAskModel.ui("flag_off:shop.orders", SupportAskModel.State.SENT))
    var retries = 0
    rule.setContent {
      Phone {
        SupportContent(SupportUi(SupportModel.items(msgs, now), ask = state), onBack = null, onDraft = {}, onSend = {}, onRetryAsk = { retries++ })
      }
    }
    rule.waitForIdle()
    val shown = texts()
    assertTrue(shown.toString(), shown.contains("سفارشِ آنلاین برای این دکان خاموش است"))
    assertTrue(shown.contains(StrSupportAsk.sent))
    assertTrue(shown.contains("پشتیبانی سفارشِ آنلاین را برای دکانِ شما روشن کرد."))
    assertEquals(0, rule.onAllNodes(hasText(StrSupportAsk.retry)).fetchSemanticsNodes().size)
    noInternals()

    state = SupportAskModel.ui("flag_off:shop.orders", SupportAskModel.stateOf(SupportAskDto(created = false, sameShop = true)))
    rule.waitForIdle()
    assertTrue(texts().contains(StrSupportAsk.colleague))
    state = SupportAskModel.ui("flag_off:shop.orders", SupportAskModel.stateOf(SupportAskDto(created = false)))
    rule.waitForIdle()
    assertTrue(texts().contains(StrSupportAsk.already))
    state = SupportAskModel.ui("flag_off:shop.orders", SupportAskModel.State.FAILED)
    rule.waitForIdle()
    assertTrue(texts().contains(StrSupportAsk.failed))
    rule.onAllNodes(hasText(StrSupportAsk.retry) and hasClickAction())[0].performClick()
    rule.waitForIdle()
    assertEquals(1, retries)
  }

  @Test fun everyLanguageHasItsOwnButtonAndTitle() {
    for (lang in listOf(Lang.FA, Lang.PRS, Lang.EN, Lang.PS)) {
      I18n.source = { lang }
      assertTrue(StrSupportAsk.button.isNotBlank())
      val t = SupportAskModel.title("flag_off:shop.storefront")
      assertFalse("$lang: $t", t.contains("{name}") || t.contains("shop."))
    }
    I18n.source = { Lang.EN }
    assertEquals("Ask support", StrSupportAsk.button)
    assertEquals("Storefront and marketplace is turned off for this shop", SupportAskModel.title("flag_off:shop.storefront"))
    I18n.source = { Lang.PS }
    assertEquals("له ملاتړ وغواړئ", StrSupportAsk.button)
  }
}
