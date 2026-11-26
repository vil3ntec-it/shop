package ir.vil3ntec.tohid.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrFx
import ir.vil3ntec.tohid.core.i18n.StrHero
import ir.vil3ntec.tohid.core.i18n.StrSafe
import ir.vil3ntec.tohid.core.i18n.StrSafeCur
import ir.vil3ntec.tohid.core.safe.SafeCurrency
import ir.vil3ntec.tohid.data.SafeBook
import ir.vil3ntec.tohid.data.SafeBookPref
import ir.vil3ntec.tohid.data.SafeCurrencies
import ir.vil3ntec.tohid.data.SafeCurrencyPref
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.ui.components.common.PhoneNav
import ir.vil3ntec.tohid.ui.screens.SafeCurrencyButton
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import ir.vil3ntec.tohid.ui.v3.shell.HeroMoney
import ir.vil3ntec.tohid.ui.v3.shell.HeroMoneySheet
import ir.vil3ntec.tohid.ui.v3.shell.HomeHero
import ir.vil3ntec.tohid.ui.v3.shell.HomeScreen
import ir.vil3ntec.tohid.ui.v3.shell.HomeShot
import ir.vil3ntec.tohid.ui.v3.shell.SafeCurrencyChoice
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 *  گاوصندوقِ چندارزی در صفحه (خواستهٔ صاحب‌کار ۱۴۰۵/۰۸/۲۴): کادرِ «ارز» کنارِ مبلغِ برگهٔ «+» و پنجرهٔ کلاسیک، «+ ارزِ دیگر»
 *  (کد و نام، با سنجش)، و تراشهٔ ارزِ گوشهٔ نمودارِ کادرِ بالای خانه که مانده و آمارِ همان ارز را نشان می‌دهد.
 */
class SafeCurrencyUiTest {

  @get:Rule val rule = createComposeRule()

  @Before fun fa() {
    I18n.source = { Lang.FA }
  }

  @After fun reset() {
    SafeCurrencyPref.setViewed(DesktopContext, SafeCurrency.AFN)
    SafeCurrencyPref.saveCustom(DesktopContext, emptyList())
    SafeBookPref.set(DesktopContext, false)
  }

  /** کادرِ «ارز» با حالتِ بیرونی، همان که `HeroMoneyRoute` و پنجرهٔ کلاسیک می‌سازند */
  private class Picker {
    var code by mutableStateOf(SafeCurrency.AFN)
    var custom by mutableStateOf(emptyList<SafeCurrencies.Item>())
    fun choice() = SafeCurrencyChoice(
      code, SafeCurrencies.all(custom), { SafeCurrencies.name(it, custom) }, { code = it },
    ) { c, n ->
      when (val r = SafeCurrencies.add(custom, c, n)) {
        is SafeCurrencies.Added.Bad -> r.message
        is SafeCurrencies.Added.Ok -> { custom = r.list; code = r.code; null }
      }
    }
  }

  private fun sheet(p: Picker, submitted: MutableList<Pair<String, String>>) {
    rule.setContent {
      CompositionLocalProvider(LocalContext provides DesktopContext) {
        TohidTheme(ThemeChoice.LIGHT) {
          HeroMoneySheet(
            page = HomeHero.Page.SAFE, shiftClosed = false, busy = false, error = null,
            onSubmit = { _, amount, _, _ -> submitted += amount to p.code }, onOpenDrawer = {}, onClose = {},
            startAmount = "25.5", currency = p.choice(),
          )
        }
      }
    }
    rule.waitForIdle()
  }

  @Test fun formCurrencyDropdownPicksTheCurrencyOfTheAmount() {
    val p = Picker()
    val submitted = ArrayList<Pair<String, String>>()
    sheet(p, submitted)
    //  پیش‌فرض افغانی: همان برچسبِ امروز
    rule.onAllNodesWithText(StrSafe.amount).assertCountEquals(1)
    rule.onNodeWithContentDescription("${StrSafeCur.currency}: ${StrSafeCur.afn}").performClick()
    rule.waitForIdle()
    for (code in SafeCurrency.BUILT_IN) rule.onAllNodesWithText(SafeCurrencies.name(code)).assertCountEquals(1)
    rule.onNodeWithText(StrFx.usd).performClick()
    rule.waitForIdle()
    assertEquals("USD", p.code)
    rule.onAllNodesWithText(StrSafeCur.amountIn("USD")).assertCountEquals(1)
    rule.onNodeWithContentDescription("${StrSafeCur.currency}: ${StrFx.usd}").assertExists()
    //  دکمهٔ «اضافه» ِ پایینِ برگه (همان نوشته در «اضافه / برداشت» ِ بالا هم هست) — پایینِ قابِ آزمون، پس با کارِ معنایی
    rule.onAllNodesWithText(StrHero.add).filter(hasClickAction()).onLast().performSemanticsAction(SemanticsActions.OnClick)
    rule.waitForIdle()
    assertEquals(listOf("25.5" to "USD"), submitted)
    assertEquals(HeroMoney.Amount.Ok(2_550), HeroMoney.amount("25.5", p.code))
  }

  @Test fun addingACurrencyValidatesThenPicksIt() {
    val p = Picker()
    sheet(p, ArrayList())
    rule.onNodeWithContentDescription("${StrSafeCur.currency}: ${StrSafeCur.afn}").performClick()
    rule.waitForIdle()
    rule.onNodeWithText(StrSafeCur.addCurrency).performClick()
    rule.waitForIdle()
    rule.onAllNodesWithText(StrSafeCur.addTitle).assertCountEquals(1)
    //  کدِ همیشه‌آماده تکراری است
    rule.onNode(hasSetTextAction() and hasContentDescription(StrSafeCur.code)).performTextInput("usd")
    rule.onNode(hasSetTextAction() and hasContentDescription(StrSafeCur.name)).performTextInput("دالر")
    rule.onNodeWithText(StrSafeCur.add).performClick()
    rule.waitForIdle()
    rule.onAllNodesWithText(StrSafeCur.duplicate).assertCountEquals(1)
    assertEquals(SafeCurrency.AFN, p.code)
    assertTrue(p.custom.isEmpty())
  }

  @Test fun addingANewCurrencyStoresCodeAndName() {
    val p = Picker()
    sheet(p, ArrayList())
    rule.onNodeWithContentDescription("${StrSafeCur.currency}: ${StrSafeCur.afn}").performClick()
    rule.waitForIdle()
    rule.onNodeWithText(StrSafeCur.addCurrency).performClick()
    rule.waitForIdle()
    rule.onNode(hasSetTextAction() and hasContentDescription(StrSafeCur.code)).performTextInput("gbp")
    rule.onNode(hasSetTextAction() and hasContentDescription(StrSafeCur.name)).performTextInput("پوند")
    rule.onNodeWithText(StrSafeCur.add).performClick()
    rule.waitForIdle()
    assertEquals("GBP", p.code)
    assertEquals(listOf(SafeCurrencies.Item("GBP", "پوند")), p.custom)
    rule.onAllNodesWithText(StrSafeCur.addTitle).assertCountEquals(0)
    rule.onAllNodesWithText(StrSafeCur.amountIn("GBP")).assertCountEquals(1)
    //  فهرستِ کادر حالا ارزِ تازه را با نامِ دکان دارد
    rule.onNodeWithContentDescription("${StrSafeCur.currency}: پوند (GBP)").performClick()
    rule.waitForIdle()
    rule.onAllNodesWithText("پوند (GBP)").assertCountEquals(1)
  }

  @Test fun classicWindowDropdownAndAddDialog() {
    val p = Picker()
    rule.setContent {
      CompositionLocalProvider(LocalContext provides DesktopContext) {
        TohidTheme(ThemeChoice.LIGHT) { Column { SafeCurrencyButton(p.choice()) } }
      }
    }
    rule.waitForIdle()
    rule.onNodeWithContentDescription("${StrSafeCur.currency}: ${StrSafeCur.afn}").performClick()
    rule.waitForIdle()
    rule.onNodeWithText(StrSafeCur.eur).performClick()
    rule.waitForIdle()
    assertEquals("EUR", p.code)
    rule.onNodeWithContentDescription("${StrSafeCur.currency}: ${StrSafeCur.eur}").performClick()
    rule.waitForIdle()
    rule.onNodeWithText(StrSafeCur.addCurrency).performClick()
    rule.waitForIdle()
    rule.onNode(hasSetTextAction() and hasText(StrSafeCur.code)).performTextInput("chf")
    rule.onNode(hasSetTextAction() and hasText(StrSafeCur.name)).performTextInput("فرانک")
    rule.onNodeWithText(StrSafeCur.add).performClick()
    rule.waitForIdle()
    assertEquals("CHF", p.code)
    rule.onAllNodesWithText(StrSafeCur.addTitle).assertCountEquals(0)
  }

  /** افغانی ۵۰۰۰ شمرده و ۱۲۰۰ واریزِ امروز؛ ۲۵٫۵۰ دالرِ امروز */
  private fun ledger(): ShopData {
    val now = System.currentTimeMillis()
    var n = 0
    val ids = { "01UICUR${(++n).toString().padStart(19, '0')}" }
    var d = ShopData()
    fun step(r: SafeBook.Result) { d = (r as SafeBook.Result.Ok).data }
    step(SafeBook.count(d, 500_000, null, "صاحب", now - 3 * 24 * 60 * 60 * 1000L, ids))
    // دادهٔ «امروز» نباید در اجرای CI نزدیک نیمه‌شب به روز گذشته برود.
    step(SafeBook.deposit(d, 120_000, "deposit", null, "صاحب", now, ids))
    step(SafeBook.deposit(d, 2_550, "USD", "deposit", null, "صاحب", now, ids))
    return d
  }

  @Test fun chartCurrencyChipSwitchesTheBalanceAndTotals() {
    SafeBookPref.set(DesktopContext, true)
    SafeCurrencyPref.setViewed(DesktopContext, SafeCurrency.AFN)
    val d = ledger()
    rule.setContent {
      CompositionLocalProvider(LocalContext provides DesktopContext) {
        TohidTheme(ThemeChoice.LIGHT) { HomeScreen(remember { d }, {}, PhoneNav.RESERVE, HomeShot(reveal = 1f, drawerPage = 1)) }
      }
    }
    rule.waitForIdle()
    val chip = StrSafeCur.chartCurrency(StrSafeCur.afn)
    val picker = rule.onNodeWithContentDescription(chip).fetchSemanticsNode().boundsInRoot
    val chart = rule.onNodeWithTag("home-hero-chart").fetchSemanticsNode().boundsInRoot
    assertTrue("Currency picker sits above the chart", picker.bottom < chart.top)
    rule.onAllNodesWithText(SafeCurrencies.format(120_000, "AFN")).assertCountEquals(1)   // «واریزِ امروز» به افغانی
    rule.onNodeWithContentDescription(chip).performClick()
    rule.waitForIdle()
    rule.onNodeWithText(StrFx.usd).performClick()
    rule.mainClock.advanceTimeBy(2_000)
    rule.waitForIdle()
    assertEquals("USD", SafeCurrencyPref.viewed(DesktopContext))
    rule.onNodeWithContentDescription(StrSafeCur.chartCurrency(StrFx.usd)).assertExists()
    rule.onAllNodesWithText(SafeCurrencies.format(2_550, "USD")).assertCountEquals(1)   // واریزِ امروزِ دالر
    rule.onAllNodesWithText(SafeCurrencies.format(120_000, "AFN")).assertCountEquals(0)  // افغانی دیگر روی همین کادر نیست
    assertTrue("نشانهٔ ماندهٔ دالری", rule.onAllNodesWithText("USD").fetchSemanticsNodes().isNotEmpty())
  }

  @Test fun afnOnlyLedgerShowsNoChip() {
    SafeBookPref.set(DesktopContext, true)
    val now = System.currentTimeMillis()
    val d = (SafeBook.count(ShopData(), 500_000, null, "صاحب", now - 60_000L) { "01UICURAFNONLY00000000001" } as SafeBook.Result.Ok).data
    rule.setContent {
      CompositionLocalProvider(LocalContext provides DesktopContext) {
        TohidTheme(ThemeChoice.LIGHT) { HomeScreen(remember { d }, {}, PhoneNav.RESERVE, HomeShot(reveal = 1f, drawerPage = 1)) }
      }
    }
    rule.waitForIdle()
    rule.onAllNodes(hasContentDescription(StrSafeCur.chartCurrency(StrSafeCur.afn))).assertCountEquals(0)
  }
}
