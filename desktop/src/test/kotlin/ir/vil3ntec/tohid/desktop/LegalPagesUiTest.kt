package ir.vil3ntec.tohid.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrAccount
import ir.vil3ntec.tohid.core.i18n.StrAuth
import ir.vil3ntec.tohid.core.legal.Legal
import ir.vil3ntec.tohid.core.legal.LegalKind
import ir.vil3ntec.tohid.desktop.shots.MoneyDemo
import ir.vil3ntec.tohid.ui.courier.CourierAccountTab
import ir.vil3ntec.tohid.ui.courier.CourierLegal
import ir.vil3ntec.tohid.ui.courier.CourierSubPage
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import ir.vil3ntec.tohid.ui.v3.onboarding.LegalPage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 *  «حریمِ خصوصی» و «شرایط و ضوابط» درونِ برنامهٔ دکان (`LegalPage`، منبعِ یکتای `core/legal`) — پیش‌تر «حریمِ خصوصی» صفحهٔ
 *  GitHub Pages در مرورگر بود:
 *   • هر دو سند در فارسی، دری، انگلیسی و پشتو کشیده می‌شوند: عنوان، خطِ زیرِ عنوان، نخستین و واپسین بخش و دکمهٔ پایین به
 *     همان زبان (دری از لایهٔ واژه)؛
 *   • «درباره» ⇐ ردیفِ «حریمِ خصوصی» همان صفحهٔ درونِ برنامه را باز می‌کند (مسیرِ `legal/privacy`)، و `legal/terms` همان شرایط است؛
 *   • حالتِ پیک: دو ردیفِ برگهٔ «حساب» و زیرصفحهٔ همان متن.
 */
@OptIn(ExperimentalTestApi::class)
class LegalPagesUiTest {

  @get:Rule val rule = createComposeRule()

  @After fun reset() {
    I18n.source = { Lang.FA }
  }

  private fun has(text: String) = rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

  private fun content(body: @androidx.compose.runtime.Composable () -> Unit) = rule.setContent {
    CompositionLocalProvider(LocalContext provides DesktopContext, LocalConfiguration provides Configuration(360, 760)) {
      TohidTheme(ThemeChoice.LIGHT) { body() }
    }
  }

  @Test fun bothDocumentsRenderInEveryAppLanguage() {
    val shown = mutableStateOf(Lang.FA to LegalKind.PRIVACY)
    //  زبان حالتِ Compose است: عوض شدنش صفحه را با همان زبان از نو می‌کشد (مثلِ `LocaleChoice` ِ برنامه)
    I18n.source = { shown.value.first }
    //  `key`: هر (زبان، سند) صفحهٔ تازه با پیمایشِ از بالا، مثلِ باز کردنِ دوبارهٔ مسیر
    content { key(shown.value) { LegalPage(shown.value.second, onClose = {}) } }
    for (lang in listOf(Lang.FA, Lang.PRS, Lang.EN, Lang.PS)) for (kind in LegalKind.entries) {
      shown.value = lang to kind
      rule.waitForIdle()
      val doc = Legal.doc(kind, lang.code)
      val at = "$lang $kind"
      assertTrue("$at: عنوان", has(doc.title))
      assertTrue("$at: خطِ زیرِ عنوان", has(Legal.subtitle(doc, lang.code)))
      assertTrue("$at: نخستین بخش", has(doc.sections.first().title))
      assertTrue("$at: دکمهٔ پایین", has(StrAuth.readClose))
      rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(doc.sections.last().title))
      assertTrue("$at: واپسین بخش", has(doc.sections.last().title))
    }
    //  سه زبانِ نوشته واقعاً جدا‌اند (نه همان فارسی)
    val titles = listOf("fa", "en", "ps").map { Legal.doc(LegalKind.PRIVACY, it).title }
    assertEquals(3, titles.toSet().size)
  }

  @Test fun acceptingTheTermsAtSignUpShowsTheAcceptButton() {
    var accepted = false
    content { LegalPage(LegalKind.TERMS, onClose = {}, accepting = true, onAccept = { accepted = true }) }
    rule.onAllNodes(hasText(StrAuth.iAccept) and hasClickAction())[0].performSemanticsAction(SemanticsActions.OnClick)
    rule.waitForIdle()
    assertTrue(accepted)
  }

  @Test fun aboutOpensThePrivacyPolicyInsideTheApp() {
    RouteUiHarness(rule).use { h ->
      h.show("settings/about", MoneyDemo.full())
      rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(StrAccount.privacyLine))
      h.click(StrAccount.privacyLine)
      h.await(Legal.doc(LegalKind.PRIVACY, "fa").title)
      assertTrue(h.has(Legal.doc(LegalKind.PRIVACY, "fa").sections.first().title))
    }
  }

  @Test fun theTermsAliasOpensTheSameInAppPage() {
    RouteUiHarness(rule).use { h ->
      h.show("legal/terms", MoneyDemo.full())
      h.await(Legal.doc(LegalKind.TERMS, "fa").title)
    }
  }

  @Test fun courierAccountTabOpensBothDocuments() {
    val opened = ArrayList<String>()
    val page = mutableStateOf<String?>(null)
    content {
      val key = page.value
      if (key == null) {
        CourierAccountTab(
          name = "پیک", summary = null, syncing = false, waiting = 0, lastError = null, lastSyncAt = 0L,
          onRefresh = {}, onCallShop = {}, onCash = {}, onSignOut = {}, onLegal = { opened += it; page.value = it },
        )
      } else {
        CourierSubPage(key, null, null, false, onBack = { page.value = null }, onRefresh = {})
      }
    }
    rule.onAllNodes(hasText(StrAccount.privacyPolicy) and hasClickAction())[0].performSemanticsAction(SemanticsActions.OnClick)
    rule.waitForIdle()
    assertTrue(has(Legal.doc(LegalKind.PRIVACY, "fa").title))
    rule.onAllNodes(hasText(StrAuth.readClose) and hasClickAction())[0].performSemanticsAction(SemanticsActions.OnClick)
    rule.waitForIdle()
    rule.onAllNodes(hasText(StrAuth.termsShort) and hasClickAction())[0].performSemanticsAction(SemanticsActions.OnClick)
    rule.waitForIdle()
    assertTrue(has(Legal.doc(LegalKind.TERMS, "fa").title))
    assertEquals(listOf(CourierLegal.PRIVACY, CourierLegal.TERMS), opened)
  }
}
