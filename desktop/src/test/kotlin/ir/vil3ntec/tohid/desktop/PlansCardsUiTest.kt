package ir.vil3ntec.tohid.desktop

import ir.vil3ntec.tohid.desktop.shots.demoPlanOptions
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrA11y
import ir.vil3ntec.tohid.core.i18n.StrAccount
import ir.vil3ntec.tohid.core.model.PlanDto
import ir.vil3ntec.tohid.core.model.PlansDto
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.ui.screens.Motion
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import ir.vil3ntec.tohid.ui.v3.account.AccountPaths
import ir.vil3ntec.tohid.ui.v3.account.subscription.PlansActions
import ir.vil3ntec.tohid.ui.v3.account.subscription.PlansContent
import ir.vil3ntec.tohid.ui.v3.account.subscription.PlansModel
import ir.vil3ntec.tohid.ui.v3.account.subscription.PlansTags
import ir.vil3ntec.tohid.ui.v3.account.subscription.PlansUi
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 *  «اشتراک و قیمت‌ها» با کارت‌های بازشونده (`PlansContent` ⇐ `PlanCard`/`PlanDetails`/`PlansBuyBar`):
 *   • هر کارت قیمت (از همان فهرستِ سرور یا پیش‌فرض)، واحد، روزی چند و صرفه‌جویی را نشان می‌دهد؛
 *   • زدنِ کارت آن را انتخاب و کشوی جزئیاتش را باز و بقیه را بسته می‌کند؛ زدنِ دوباره می‌بندد؛
 *   • نوشته و قیمتِ دکمهٔ چسبان دنبالِ انتخاب است و خرید همان پلن را می‌گیرد؛
 *   • «کد اشتراک دارم» هنوز برگهٔ کد را باز می‌کند؛
 *   • زیرِ «کاهشِ حرکت» همه در همان قاب درست است، و با حرکتِ روشن کشوی پیشین هنوز در حالِ بسته شدن است (مرزِ مقابل)؛
 *   • صفحه‌خوان «انتخاب‌شده» و «باز/بسته» را می‌شنود، هدفِ لمسی ≥ ۴۸dp و چیدمانِ راست‌به‌چپ.
 */
class PlansCardsUiTest {

  @get:Rule val rule = createComposeRule()

  private val ctx = DesktopContext
  private val cur = "افغانی"
  private var chosen by mutableStateOf<String?>(null)
  private val bought = ArrayList<String>()
  private val opened = ArrayList<String>()

  private fun forgetMotion() = ctx.getSharedPreferences("tohid", 0).edit().remove("motion").commit()

  @Before fun setUp() {
    I18n.source = { Lang.FA }
    forgetMotion()
    Motion.reducedForTest = false
    Motion.load(ctx)
  }

  @After fun tearDown() {
    Motion.reducedForTest = null
    forgetMotion()
    Motion.load(ctx)
  }

  private fun show(options: List<PlansModel.Option> = demoPlanOptions) {
    rule.setContent {
      CompositionLocalProvider(LocalContext provides ctx, LocalLayoutDirection provides LayoutDirection.Rtl) {
        TohidTheme(ThemeChoice.LIGHT) {
          val picked = PlansModel.pick(options, chosen)
          PlansContent(
            PlansUi(
              state = PlansModel.state(null, 0, 0, null, false, 0, false, 14, 7),
              options = options, picked = picked, currency = cur, signedIn = true, salesNumber = "0700123456", storeBuild = false,
            ),
            PlansActions(back = null, pick = { chosen = it }, buy = { picked?.let { bought += it.code } }, open = { opened += it }, whatsapp = {}),
          )
        }
      }
    }
    rule.waitForIdle()
  }

  private fun card(code: String) = rule.onNodeWithTag(PlansTags.card(code))
  private fun details(code: String) = rule.onNodeWithTag(PlansTags.details(code))
  private fun state(s: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, s)

  /**
   *  زدنِ سرِ کارتِ [code] با کارِ کلیکِ خودِ گره (همان `onClick` ِ لمس): پنجرهٔ آزمون ۷۶۸px است و کارتِ پایینی زیرِ کشوی
   *  باز و نوارِ چسبان، بیرون از قاب می‌افتد. لمسِ واقعی روی «ماهانه» ِ بالای فهرست جدا آزموده می‌شود.
   */
  private fun tap(code: String) { card(code).performSemanticsAction(SemanticsActions.OnClick) }

  @Test fun everyCardShowsItsPriceCurrencyPerDayAndComputedSaving() {
    val options = demoPlanOptions
    show(options)
    for (o in options) {
      card(o.code).assert(hasText(o.title)).assert(hasText(money(o.price.toDouble()))).assert(hasText(cur))
        .assert(hasText(StrAccount.perDay(money(o.perDay))))
      PlansModel.saving(o, options)?.let { card(o.code).assert(hasText(StrAccount.saveChip(it.percent))) }
    }
    card("m6").assert(hasText(StrAccount.badgeRecommended))
    card("y1").assert(hasText(StrAccount.badgeBestValue))
  }

  @Test fun serverPricesReachTheCardsAndTheButtonNotTheFallbackOnes() {
    val options = PlansModel.options(PlansDto(plans = listOf(
      PlanDto(code = "a", title = "ماهانه", price = 450, amount = 1, unit = "month", days = 30),
      PlanDto(code = "b", title = "۱ ساله", price = 3600, fullPrice = 4000, amount = 1, unit = "year", days = 365, badge = "بیشترین صرفه"),
    )))
    show(options)
    card("a").assert(hasText(money(450.0)))
    card("b").assert(hasText(money(3600.0))).assert(hasText(money(4000.0))).assert(hasText(StrAccount.saveChip(PlansModel.saving(options[1], options)!!.percent)))
    rule.onNodeWithTag(PlansTags.BUY).assert(hasText(PlansModel.cta(options[1], cur)))
  }

  @Test fun tappingACardExpandsItsDetailsAndCollapsesTheOthers() {
    show()
    details("m6").assertIsDisplayed()
    details("m1").assertDoesNotExist(); details("y1").assertDoesNotExist()
    card("m6").assertIsSelected().assert(state(StrA11y.expanded))

    tap("y1"); rule.waitForIdle()
    details("y1").assertExists()
    details("m6").assertDoesNotExist(); details("m1").assertDoesNotExist()
    card("y1").assertIsSelected().assert(state(StrA11y.expanded))
    card("m6").assert(state(StrA11y.collapsed))
    //  کشو همان قابلیت‌های واقعی و قاعدهٔ تمدید را دارد
    rule.onNodeWithText(StrAccount.featSale).assertExists()
    rule.onNodeWithText(StrAccount.featStock).assertExists()
    rule.onNodeWithText(StrAccount.renewEarly).assertExists()

    tap("y1"); rule.waitForIdle()
    details("y1").assertDoesNotExist()
    card("y1").assertIsSelected().assert(state(StrA11y.collapsed))

    //  لمسِ واقعی روی کارتِ بالای فهرست
    card("m1").performClick(); rule.waitForIdle()
    details("m1").assertIsDisplayed()
    details("y1").assertDoesNotExist(); details("m6").assertDoesNotExist()

    //  صفحه‌خوان: «بستن» و «باز کردن» ِ خودِ کشو
    card("m1").performSemanticsAction(SemanticsActions.Collapse); rule.waitForIdle()
    details("m1").assertDoesNotExist()
    card("m1").performSemanticsAction(SemanticsActions.Expand); rule.waitForIdle()
    details("m1").assertIsDisplayed()
  }

  @Test fun buttonLabelAndPriceFollowTheSelectionAndBuyThatPlan() {
    val options = demoPlanOptions
    show(options)
    val buy = rule.onNodeWithTag(PlansTags.BUY)
    buy.assert(hasText(PlansModel.cta(options[1], cur)))
    tap("y1"); rule.waitForIdle()
    buy.assert(hasText(PlansModel.cta(options[2], cur))).assert(!hasText(PlansModel.cta(options[1], cur)))
    buy.performClick()
    card("m1").performClick(); rule.waitForIdle()
    buy.assert(hasText(PlansModel.cta(options[0], cur)))
    buy.performClick()
    assertEquals(listOf("y1", "m1"), bought)
  }

  @Test fun theCodeEntryStillOpensTheCodeSheet() {
    show()
    //  ردیف زیرِ نوارِ چسبانِ پنجرهٔ آزمون می‌افتد ⇒ همان کارِ کلیکِ ردیف
    rule.onNodeWithText(StrAccount.haveCode).performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
    rule.onNodeWithText(StrAccount.noOnlinePay).assertExists()
    assertEquals(listOf(AccountPaths.REDEEM), opened)
  }

  /** زدنِ «۱ ساله» و [frames] قاب؛ ساعتِ آزمون دستی */
  private fun tapYearlyThen(frames: Int) {
    rule.mainClock.autoAdvance = false
    tap("y1")
    repeat(frames) { rule.mainClock.advanceTimeByFrame() }
  }

  @Test fun withAnimationsOffTheContentIsCorrectInTheSameFrame() {
    Motion.reducedForTest = true
    Motion.load(ctx)
    assertTrue(!Motion.enabled)
    val options = demoPlanOptions
    show(options)
    tapYearlyThen(2)
    details("y1").assertExists()
    details("m6").assertDoesNotExist()
    rule.onNodeWithTag(PlansTags.BUY).assert(hasText(PlansModel.cta(options[2], cur))).assert(!hasText(PlansModel.cta(options[1], cur)))
    card("y1").assertIsSelected().assert(state(StrA11y.expanded))
  }

  @Test fun withAnimationsOnThePreviousDrawerIsStillClosingAfterTwoFrames() {
    assertTrue(Motion.enabled)
    show()
    tapYearlyThen(2)
    details("m6").assertExists()
    rule.mainClock.autoAdvance = true
    rule.waitForIdle()
    details("m6").assertDoesNotExist()
    details("y1").assertExists()
  }

  @Test fun headersAreBigEnoughToTouchAndLaidOutRightToLeft() {
    val options = demoPlanOptions
    show(options)
    for (o in options) card(o.code).assertHeightIsAtLeast(48.dp)
    val title = rule.onNodeWithText(options[0].title, useUnmergedTree = true).getBoundsInRoot()
    val price = rule.onNodeWithText(money(options[0].price.toDouble()), useUnmergedTree = true).getBoundsInRoot()
    assertTrue("راست‌به‌چپ: نام سمتِ راست و قیمت سمتِ چپ", title.left > price.right)
  }
}
