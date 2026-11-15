package ir.vil3ntec.tohid.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.PointOfSale
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrHistory
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.StrMoney
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.desktop.shots.MoneyDemo
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.ui.components.common.ViewModePref
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import ir.vil3ntec.tohid.ui.v3.SummaryFold
import ir.vil3ntec.tohid.ui.v3.V3Action
import ir.vil3ntec.tohid.ui.v3.V3Scaffold
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

/**
 *  ۶۵s (صاحب‌کار): سربرگِ هر بخش —
 *   • عنوان بالا در نوار و وسطِ برگشت و کارها (پیش‌فرضِ `V3Scaffold`)، فقط یک بار و سرخطِ صفحه‌خوان؛ با کارهای پهنِ یک سو
 *     هرگز زیرِ آیکن نمی‌رود؛
 *   • «مصارف»: کادرِ کشوییِ ماه در گوشهٔ کارتِ خلاصه (نه بالای آن) و نمودارِ دسته‌ها درونِ کارت؛ زدنِ ردیفِ دسته فهرست را
 *     پالایه می‌کند؛ «تاریخچهٔ فروش»: کادرِ کشوییِ دوره و «نقد · نسیه · دیگر» درونِ همان کارت.
 */
class SectionHeaderUiTest {

  @get:Rule val rule = createComposeRule()

  private lateinit var h: RouteUiHarness
  private val ctx = DesktopContext

  private fun forget() =
    ctx.getSharedPreferences(ViewModePref.PREFS, 0).edit().remove(SummaryFold.key("expenses")).remove(SummaryFold.key("sales")).commit()

  @Before fun up() { I18n.source = { Lang.FA }; forget(); ShopRole.remember(ctx, ShopRole.OWNER); h = RouteUiHarness(rule) }

  @After fun down() { h.close(); forget() }

  private fun px(dp: Float) = with(rule.density) { dp.dp.toPx() }

  private val heading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)
  private val dropdown = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.DropdownList)
  private val header = SemanticsMatcher.keyIsDefined(SemanticsProperties.StateDescription) and hasClickAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)

  private fun nodes(m: SemanticsMatcher, unmerged: Boolean = false): List<SemanticsNode> =
    rule.onAllNodes(m, useUnmergedTree = unmerged).fetchSemanticsNodes()

  private fun inside(a: Rect, b: Rect) = a.left >= b.left - 1f && a.right <= b.right + 1f && a.top >= b.top - 1f && a.bottom <= b.bottom + 1f

  private fun bar(title: String, back: Boolean, actions: Int) {
    I18n.source = { Lang.FA }
    val icons = listOf(Icons.Outlined.Tune, Icons.Outlined.PointOfSale, Icons.Outlined.Print)
    rule.setContent {
      CompositionLocalProvider(LocalContext provides ctx) {
        TohidTheme(ThemeChoice.LIGHT) {
          CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Box(Modifier.width(360.dp)) {
              V3Scaffold(
                title = title,
                onBack = if (back) ({}) else null,
                actions = icons.take(actions).mapIndexed { i, ic -> V3Action(ic, "کار $i") {} },
              ) { }
            }
          }
        }
      }
    }
    rule.waitForIdle()
  }

  private fun titleNode(title: String): SemanticsNode = nodes(hasText(title) and heading, unmerged = true).single()

  @Test fun titleSitsCentredInTheBarOnceAsHeading() {
    bar(Str.expenses, back = true, actions = 1)
    val t = titleNode(Str.expenses).boundsInRoot
    assertEquals("یک عنوان، نه عنوانِ درشتِ دوم", 1, nodes(hasText(Str.expenses), unmerged = true).size)
    assertTrue("در نوارِ بالا: $t", t.bottom <= px(56f) + 1f)
    assertTrue("وسطِ نوار", abs(t.center.x - px(180f)) <= px(1.5f))
  }

  @Test fun wideActionsNeverCoverTheTitle() {
    bar(Str.salesHistory, back = false, actions = 3)
    val t = titleNode(Str.salesHistory).boundsInRoot
    //  دو کارِ نخست پیدا و بقیه زیرِ «…» (`V3HeaderActions.split`) — سه آیکن در یک سو
    val icons = listOf("کار 0", "کار 1", StrKit.more).map { d -> nodes(hasContentDescription(d) and hasClickAction()).single().boundsInRoot }
    icons.forEach { i -> assertTrue("عنوان زیرِ آیکن نیست: $t / $i", t.right <= i.left || t.left >= i.right) }
    assertTrue("در نوار", t.bottom <= px(56f) + 1f)
  }

  @Test fun expensesMonthDropdownAndCategoryChartAreInsideTheSummaryCard() {
    val d = MoneyDemo.full()
    h.show("expenses", d)
    h.await(StrMoney.allExpenses)
    //  عنوان در نوار، یک بار
    val t = titleNode(Str.expenses).boundsInRoot
    assertTrue(t.bottom <= px(64f))
    val head = nodes(header).single().boundsInRoot
    val drop = nodes(dropdown).single().boundsInRoot
    assertTrue("کادرِ کشوییِ ماه درونِ سرخطِ کارت: $drop در $head", inside(drop, head))
    //  گوشه، نه وسط (جهت را پوستهٔ برنامه می‌دهد؛ این‌جا فقط «در یک سو») — جهتِ راست‌به‌چپ در `SummaryCardUiTest`
    assertTrue("در گوشه، نه وسط", abs(drop.center.x - head.center.x) > px(60f))
    assertTrue("ردیفِ بالای کارت", drop.top - head.top <= px(12f))
    //  ستونِ «کرایه» درونِ کارت، زیرِ سرخط؛ زدن ⇒ پالایهٔ همان دسته
    val rent = nodes(hasContentDescription("کرایه: " + money(6000.0)) and hasClickAction()).single()
    assertTrue("نمودار زیرِ سرخطِ همان کارت", rent.boundsInRoot.top >= head.bottom - 1f)
    rule.onAllNodes(hasContentDescription("کرایه: " + money(6000.0)) and hasClickAction())[0].performSemanticsAction(SemanticsActions.OnClick)
    rule.waitForIdle()
    h.await(StrMoney.categoryExpenses("کرایه"))
  }

  @Test fun historyPeriodDropdownAndPaymentSplitAreInsideTheSummaryCard() {
    h.show("sales", MoneyDemo.full())
    h.awaitThat { nodes(header).isNotEmpty() }
    val head = nodes(header).first().boundsInRoot
    val drop = nodes(dropdown).single().boundsInRoot
    assertTrue("کادرِ کشوییِ دوره درونِ سرخطِ کارت: $drop در $head", inside(drop, head))
    val cash = nodes(SemanticsMatcher("نقد") { n ->
      n.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().any { it.startsWith(StrHistory.cash + ": ") }
    }).single().boundsInRoot
    assertTrue("«نقد» درونِ همان کارت، زیرِ سرخط", cash.top >= head.bottom - 1f)
    assertTrue("کارتِ جدای «نقد · نسیه · دیگر» دیگر نیست", nodes(hasText(StrHistory.cash), unmerged = true).size == 1)
  }
}
