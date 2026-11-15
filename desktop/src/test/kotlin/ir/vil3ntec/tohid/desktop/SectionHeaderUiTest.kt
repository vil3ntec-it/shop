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

  /** گرهِ عنوانِ نوار هیچ آیکنِ زدنیِ نوار را نمی‌بُرد (مرزِ گره) */
  private fun assertTitleClear(where: String, title: String) {
    val t = titleNode(title).boundsInRoot
    assertTrue("$where: عنوان در نوار — $t", t.bottom <= px(64f))
    val icons = nodes(hasClickAction()).map { it.boundsInRoot }.filter { it.bottom <= px(64f) && it.width < px(80f) }
    icons.forEach { i -> assertTrue("$where: عنوانِ «$title» زیرِ آیکن — $t / $i", t.right <= i.left + 0.5f || t.left >= i.right - 0.5f) }
    assertTrue("$where: عنوان پیداست", t.width > px(20f))
  }

  @Test fun busyBarWithBackThreeActionsAndMenuNeverCoversTheTitle() {
    rule.setContent {
      CompositionLocalProvider(LocalContext provides ctx) {
        TohidTheme(ThemeChoice.LIGHT) {
          CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Box(Modifier.width(360.dp)) {
              V3Scaffold(
                title = Str.salesHistory,
                onBack = {},
                actions = listOf(Icons.Outlined.Tune, Icons.Outlined.PointOfSale, Icons.Outlined.Print).mapIndexed { i, ic -> V3Action(ic, "کار $i") {} },
                overflow = listOf(ir.vil3ntec.tohid.ui.v3.V3MenuItem(Icons.Outlined.Print, "منو") {}),
              ) { }
            }
          }
        }
      }
    }
    rule.waitForIdle()
    assertTitleClear("برگشت + سه کار + منو", Str.salesHistory)
  }

  @Test fun everyBusySectionKeepsItsBarTitleClear() {
    val money = MoneyDemo.full()
    val pages = listOf(
      "sales" to Str.salesHistory, "expenses" to Str.expenses, "purchasing" to Str.purchasing, "reports" to Str.reports,
      "receipts" to null, "stock" to null, "products" to null, "promos" to null,
    )
    val path = androidx.compose.runtime.mutableStateOf("sales")
    I18n.source = { Lang.FA }
    val store = RouteUiHarness.loadedStore(money)
    rule.setContent {
      CompositionLocalProvider(
        LocalContext provides ctx,
        androidx.compose.ui.platform.LocalView provides androidx.compose.runtime.remember { android.view.View(ctx) },
        androidx.compose.ui.platform.LocalConfiguration provides androidx.compose.ui.platform.Configuration(360, 760),
      ) {
        TohidTheme(ThemeChoice.LIGHT) {
          CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Box(Modifier.width(360.dp)) { ir.vil3ntec.tohid.desktop.shots.V3RoutePreview(path.value, store, dark = false) }
          }
        }
      }
    }
    for ((p, title) in pages) {
      path.value = p
      rule.waitForIdle()
      rule.waitUntil(15_000) { nodes(heading, unmerged = true).any { it.boundsInRoot.bottom <= px(64f) && it.config.contains(SemanticsProperties.Text) } }
      val bar = nodes(heading, unmerged = true).first { it.boundsInRoot.bottom <= px(64f) && it.config.contains(SemanticsProperties.Text) }
      val name = title ?: bar.config[SemanticsProperties.Text].joinToString("")
      assertTitleClear(p, name)
    }
  }

  /**
   *  صاحب‌کار: «همه سربرگ‌هایی که … نوشته‌هاشو وسط می‌زاری … لی‌اوتش به دیگه جهت نره حتی با عوض کردن زبان» — نوار و کارتِ خلاصه
   *  در فارسی و انگلیسی (چپ‌به‌راست) همان چیدمان: برگشت، کار، عنوانِ وسط، کادرِ کشوییِ گوشهٔ کارت و برچسبِ وسطِ آن سرِ جای خود.
   */
  @Test fun barAndSummaryKeepTheirArrangementInEveryLanguage() {
    I18n.source = { Lang.FA }
    val backFa = StrKit.back
    I18n.source = { Lang.EN }
    val backEn = StrKit.back
    I18n.source = { Lang.FA }
    ctx.getSharedPreferences(ViewModePref.PREFS, 0).edit().remove(SummaryFold.key("test65s/lang")).commit()
    val en = androidx.compose.runtime.mutableStateOf(false)
    rule.setContent {
      CompositionLocalProvider(LocalContext provides ctx) {
        TohidTheme(ThemeChoice.LIGHT) {
          CompositionLocalProvider(LocalLayoutDirection provides if (en.value) LayoutDirection.Ltr else LayoutDirection.Rtl) {
            Box(Modifier.width(360.dp)) {
              V3Scaffold(
                title = if (en.value) "Expenses" else "مصارف",
                onBack = {},
                actions = listOf(V3Action(Icons.Outlined.Tune, "کار") {}),
              ) {
                ir.vil3ntec.tohid.ui.v3.V3Summary(
                  label = if (en.value) "Month total" else "جمع ماه",
                  value = "16,450 ؋",
                  collapseKey = "test65s/lang",
                  section = ir.vil3ntec.tohid.ui.theme.V3Section.EXPENSES,
                  corner = {
                    ir.vil3ntec.tohid.ui.v3.V3Dropdown(if (en.value) "Nov 2026" else "عقرب 1405", emptyList(), null, {}, label = "ماه")
                  },
                )
              }
            }
          }
        }
      }
    }
    rule.waitForIdle()
    val backM = hasClickAction() and (hasContentDescription(backFa) or hasContentDescription(backEn))
    fun snap(title: String, label: String): List<Float> {
      val back = nodes(backM).single().boundsInRoot
      val act = nodes(hasContentDescription("کار") and hasClickAction()).single().boundsInRoot
      val t = titleNode(title).boundsInRoot
      val drop = nodes(dropdown).single().boundsInRoot
      val l = nodes(hasText(label) and !hasClickAction(), unmerged = true).single().boundsInRoot
      //  کادرِ کشویی به اندازهٔ نوشته‌اش است: لبهٔ بیرونیِ گوشه (چپ) همان جا می‌ماند
      return listOf(back.left, back.right, act.left, act.right, t.center.x, drop.left, l.center.x)
    }
    try {
      val fa = snap("مصارف", "جمع ماه")
      I18n.source = { Lang.EN }
      en.value = true
      rule.waitForIdle()
      val enPos = snap("Expenses", "Month total")
      val names = listOf("برگشت (چپ)", "برگشت (راست)", "کار (چپ)", "کار (راست)", "وسطِ عنوان", "کشویی (لبهٔ گوشه)", "وسطِ برچسب")
      names.forEachIndexed { i, n -> assertEquals("$n همان جا در انگلیسی", fa[i], enPos[i], px(1.5f)) }
      assertTrue("برگشت در راست (همان فارسی)", fa[0] > px(180f))
    } finally {
      I18n.source = { Lang.FA }
      ctx.getSharedPreferences(ViewModePref.PREFS, 0).edit().remove(SummaryFold.key("test65s/lang")).commit()
    }
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
