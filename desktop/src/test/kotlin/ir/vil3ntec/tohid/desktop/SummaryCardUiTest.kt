package ir.vil3ntec.tohid.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import ir.vil3ntec.tohid.ui.v3.V3SummaryBars
import ir.vil3ntec.tohid.ui.v3.V3Dropdown
import ir.vil3ntec.tohid.ui.v3.V3Choice
import ir.vil3ntec.tohid.ui.v3.V3Bar
import ir.vil3ntec.tohid.ui.theme.V3Section
import ir.vil3ntec.tohid.ui.theme.SectionColors
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrA11y
import ir.vil3ntec.tohid.core.i18n.StrLook
import ir.vil3ntec.tohid.ui.components.common.ViewModePref
import ir.vil3ntec.tohid.ui.screens.Motion
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import ir.vil3ntec.tohid.ui.v3.SummaryFold
import ir.vil3ntec.tohid.ui.v3.summaryFoldStore
import ir.vil3ntec.tohid.ui.v3.V3Summary
import ir.vil3ntec.tohid.ui.v3.V3SummaryAction
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

/**
 *  ۶۵s (صاحب‌کار: «همه سربرگ‌های توی برنامه همه‌شون وسط باشن و کشویی یعنی جمع بشن و بیان بیرون هر وقت که خواست») —
 *  کارتِ خلاصه (`V3Summary`) در پنجرهٔ ۳۶۰dp ِ راست‌به‌چپ، مثلِ گوشی:
 *   • برچسب، عدد، خطِ کم‌رنگ و کارِ کوچک همه وسطِ کارتِ تمام‌پهنا (مرزِ گره، نه عکس) — نه چسبیده به یک سو؛
 *   • زدنِ سرخط ⇒ یک خطِ «برچسب · عدد»، زدنِ دوباره ⇒ همهٔ کارت؛
 *   • حالت با بازترکیب و بیرون/درون رفتنِ کارت می‌ماند و از ترجیحِ همین دستگاه (`SummaryFold`) برمی‌گردد؛
 *   • با «کاهشِ حرکت» همان دم جمع می‌شود و با حرکت، بزرگ/کوچک شدن انیمیشن دارد؛
 *   • صفحه‌خوان: نقشِ «دکمه»، حالتِ «باز/بسته»، سرخط، نامِ کار، هدفِ لمسیِ دست‌کم ۴۸dp؛
 *   • بی `collapseKey` تاشو نیست ولی همان وسط‌چین.
 */
class SummaryCardUiTest {

  @get:Rule val rule = createComposeRule()

  private val ctx = DesktopContext
  private val keys = listOf("test65s/a", "test65s/b", "test65s/c", "test65s/d", "test65s/e", "test65s/f")

  private val label = "قرضِ ما به تأمین‌کننده‌ها"
  private val value = "0 ؋"
  private val action = "تأمین‌کنندهٔ جدید"

  private var caption by mutableStateOf("1 تأمین‌کننده — 0 خرید")
  private var shown by mutableStateOf(true)

  private fun forget() {
    ctx.getSharedPreferences(ViewModePref.PREFS, 0).edit().apply { keys.forEach { remove(SummaryFold.key(it)) } }.commit()
  }

  private fun forgetMotionChoice() = ctx.getSharedPreferences("tohid", 0).edit().remove("motion").commit()

  @Before fun up() {
    I18n.source = { Lang.FA }
    forget()
    forgetMotionChoice()
    Motion.reducedForTest = false
    Motion.load(ctx)
  }

  @After fun down() {
    forget()
    Motion.reducedForTest = null
    forgetMotionChoice()
    Motion.load(ctx)
  }

  private val picked = mutableListOf<String>()

  private fun show(key: String?, section: V3Section? = null, corner: Boolean = false, bars: List<V3Bar> = emptyList()) {
    rule.setContent {
      CompositionLocalProvider(LocalContext provides ctx) {
        TohidTheme(ThemeChoice.LIGHT) {
          CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Box(Modifier.width(360.dp)) {
              if (shown) {
                V3Summary(
                  label, value, Modifier.testTag(CARD),
                  caption = caption,
                  actions = listOf(V3SummaryAction(action) {}),
                  collapseKey = key,
                  section = section,
                  corner = if (!corner) null else ({
                    V3Dropdown(MONTH, listOf(V3Choice("m", MONTH), V3Choice("n", "میزان 1405")), "m", {}, label = "ماه")
                  }),
                  extra = if (bars.isEmpty()) null else ({ V3SummaryBars(bars, onPick = { picked += it }) }),
                )
              }
            }
          }
        }
      }
    }
    rule.waitForIdle()
  }

  private fun px(dp: Float) = with(rule.density) { dp.dp.toPx() }

  private fun card(): Rect = rule.onNodeWithTag(CARD).fetchSemanticsNode().boundsInRoot

  /** گرهِ خودِ نوشته (درختِ ادغام‌نشده): سرخطِ تاشو نوشته‌اش را در دکمهٔ خودش ادغام می‌کند */
  private fun textNodes(text: String): List<SemanticsNode> =
    rule.onAllNodes(hasText(text) and !hasClickAction(), useUnmergedTree = true).fetchSemanticsNodes()

  private fun textNode(text: String): SemanticsNode = textNodes(text).single()

  private val header = SemanticsMatcher.keyIsDefined(SemanticsProperties.StateDescription) and hasClickAction()

  private fun headerNode(): SemanticsNode = rule.onAllNodes(header).fetchSemanticsNodes().single()

  private fun state(): String? = headerNode().config.getOrNull(SemanticsProperties.StateDescription)

  private fun assertCentred(what: String, b: Rect, c: Rect = card()) {
    assertTrue("$what وسطِ کارت: ${b.center.x} در برابرِ ${c.center.x}", abs(b.center.x - c.center.x) <= px(1.5f))
  }

  private fun lines(n: SemanticsNode): Int {
    val out = mutableListOf<TextLayoutResult>()
    n.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(out)
    return out.single().lineCount
  }

  private fun collapsed() = "$label · $value"

  @Test fun everyPartIsCentredInTheFullWidthCard() {
    show("test65s/a")
    val c = card()
    assertEquals("کارت تمام‌پهناست", px(360f), c.width, 1f)
    assertCentred("برچسب", textNode(label).boundsInRoot, c)
    assertCentred("عدد", textNode(value).boundsInRoot, c)
    assertCentred("خطِ کم‌رنگ", textNode(caption).boundsInRoot, c)
    val pill = rule.onAllNodes(hasText(action) and hasClickAction()).fetchSemanticsNodes().single().boundsInRoot
    assertCentred("کارِ کوچک", pill, c)
    //  نه چسبیده به یک سو: نوشته از هر دو لبه فاصله دارد
    val l = textNode(label).boundsInRoot
    assertTrue("برچسب به راست نچسبیده", c.right - l.right > px(40f))
    assertTrue("برچسب به چپ نچسبیده", l.left - c.left > px(40f))
    assertEquals("پیش‌فرض باز است", StrA11y.expanded, state())
  }

  @Test fun tapCollapsesToOneCentredLineAndTapAgainExpands() {
    show("test65s/b")
    val open = card().height
    rule.onNode(header).performClick()
    rule.waitForIdle()
    assertEquals(StrA11y.collapsed, state())
    val line = textNode(collapsed())
    assertEquals("بسته فقط یک خط", 1, lines(line))
    assertCentred("خطِ بسته", line.boundsInRoot)
    assertTrue("عدد، خطِ کم‌رنگ و کار پنهان", textNodes(value).isEmpty() && textNodes(caption).isEmpty() && !rule.onAllNodes(hasText(action)).fetchSemanticsNodes().any())
    val h = card().height
    assertTrue("کارتِ بسته فشرده است: $h از $open", h <= px(56f) && h < open / 2)
    rule.onNode(header).performClick()
    rule.waitForIdle()
    assertEquals(StrA11y.expanded, state())
    assertTrue(textNodes(caption).size == 1 && textNodes(value).size == 1)
    assertEquals(open, card().height, 1f)
  }

  @Test fun stateSurvivesRecompositionAndComesBackFromDevicePrefs() {
    show("test65s/c")
    rule.onNode(header).performClick()
    rule.waitForIdle()
    assertTrue("در ترجیحِ همین دستگاه نوشته شد", SummaryFold.folded(summaryFoldStore(ctx), "test65s/c"))
    //  بازترکیب با دادهٔ تازه
    caption = "2 تأمین‌کننده — 5 خرید"
    rule.waitForIdle()
    assertEquals(StrA11y.collapsed, state())
    //  کارت بیرون می‌رود و برمی‌گردد (برگشت به صفحه، پیمایشِ فهرست) ⇒ از ترجیح خوانده می‌شود
    shown = false
    rule.waitForIdle()
    assertTrue(rule.onAllNodes(header).fetchSemanticsNodes().isEmpty())
    shown = true
    rule.waitForIdle()
    assertEquals(StrA11y.collapsed, state())
    assertEquals(1, textNodes(collapsed()).size)
  }

  @Test fun savedCollapsedStateOpensCollapsedOnTheFirstFrame() {
    //  مثلِ بازکردنِ دوبارهٔ برنامه: فقط ترجیحِ دیسک هست
    SummaryFold.save(summaryFoldStore(ctx), "test65s/d", true)
    show("test65s/d")
    assertEquals(StrA11y.collapsed, state())
    assertTrue(textNodes(caption).isEmpty())
    rule.onNode(header).performClick()
    rule.waitForIdle()
    assertFalse(SummaryFold.folded(summaryFoldStore(ctx), "test65s/d"))
  }

  @Test fun reducedMotionCollapsesAtOnceWhileMotionAnimatesTheSize() {
    //  با حرکت: چند قاب پس از زدن، کارت هنوز در راه است
    show("test65s/e")
    val open = card().height
    rule.mainClock.autoAdvance = false
    rule.onNode(header).performClick()
    repeat(2) { rule.mainClock.advanceTimeByFrame() }
    val mid = card().height
    assertTrue("با حرکت، اندازه انیمیشن دارد: $mid", mid > px(56f) && mid <= open)
    rule.mainClock.advanceTimeBy(2_000)
    assertTrue(card().height <= px(56f))
    //  «کاهشِ حرکت» ِ گوشی: همان دم
    Motion.reducedForTest = true
    Motion.load(ctx)
    assertFalse(Motion.enabled)
    rule.onNode(header).performClick()
    repeat(2) { rule.mainClock.advanceTimeByFrame() }
    assertEquals(StrA11y.expanded, state())
    assertEquals("کاهشِ حرکت: باز شدن بی انیمیشن", open, card().height, 1f)
    rule.onNode(header).performClick()
    repeat(2) { rule.mainClock.advanceTimeByFrame() }
    assertEquals(StrA11y.collapsed, state())
    assertTrue("کاهشِ حرکت: بسته شدن بی انیمیشن", card().height <= px(56f))
    rule.mainClock.autoAdvance = true
  }

  @Test fun headerIsAButtonWithStateHeadingNameAndTouchTarget() {
    show("test65s/f")
    val n = headerNode()
    assertEquals(Role.Button, n.config.getOrNull(SemanticsProperties.Role))
    assertTrue("سرخط", n.config.contains(SemanticsProperties.Heading))
    assertEquals(StrLook.foldSummary, n.config[SemanticsActions.OnClick].label)
    assertTrue("هدفِ لمسی ۴۸dp", n.boundsInRoot.height >= px(48f) - 0.5f)
    assertEquals("سرخط تمام‌پهنا", card().width - px(32f), n.boundsInRoot.width, 1f)
    rule.onNode(header).performClick()
    rule.waitForIdle()
    assertEquals(StrLook.unfoldSummary, headerNode().config[SemanticsActions.OnClick].label)
    assertEquals(StrA11y.collapsed, state())
  }

  @Test fun withoutKeyItIsStillCentredButNotCollapsible() {
    show(null)
    assertTrue(rule.onAllNodes(header).fetchSemanticsNodes().isEmpty())
    val l = textNode(label)
    assertTrue(l.config.contains(SemanticsProperties.Heading))
    assertCentred("برچسب", l.boundsInRoot)
    assertCentred("عدد", textNode(value).boundsInRoot)
  }


  @Test fun sectionColourTintsTheCardWithoutAGradient() {
    show("test65s/g", section = V3Section.EXPENSES)
    val tone = SectionColors.of(V3Section.EXPENSES, dark = false)
    val img = rule.onNodeWithTag(CARD).captureToImage().toPixelMap()
    //  زمینهٔ کارت در بالا (بالای نوشتهٔ سرخط) و پایین (زیرِ کارِ کوچک) همان تهِ‌رنگ — یک‌دست، نه گرادیان
    for (y in listOf(px(4f).toInt(), img.height - px(3f).toInt())) {
      val got = img[img.width / 2, y]
      assertTrue("تهِ‌رنگِ مصارف در y=$y: $got", near(got, tone.tint))
    }
    val rim = img[0, img.height / 2]
    assertTrue("مرزِ کمی پررنگ‌تر از تهِ‌رنگ: $rim", dist(rim, tone.edge) < dist(rim, tone.tint))
  }

  private fun dist(a: Color, b: Color): Float = abs(a.red - b.red) + abs(a.green - b.green) + abs(a.blue - b.blue)

  private fun near(a: Color, b: Color, tol: Int = 3): Boolean =
    abs((a.red - b.red) * 255) <= tol && abs((a.green - b.green) * 255) <= tol && abs((a.blue - b.blue) * 255) <= tol

  private val dropdown = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.DropdownList)

  @Test fun periodDropdownSitsInTheTopRowOppositeTheChevronAndDoesNotFold() {
    show("test65s/h", section = V3Section.SALES, corner = true)
    val c = card()
    val d = rule.onNode(dropdown).fetchSemanticsNode().boundsInRoot
    assertTrue("کادرِ کشویی درونِ کارت: $d در $c", d.left >= c.left && d.right <= c.right && d.top >= c.top && d.bottom <= c.bottom)
    assertTrue("در ردیفِ بالا", d.top - c.top <= px(12f))
    assertTrue("گوشهٔ پایان (چپ در فارسی)، مقابلِ پیکان", d.center.x < c.center.x)
    val l = textNode(label).boundsInRoot
    assertTrue("برچسب زیرِ ردیفِ بالا و وسط", l.top >= d.bottom - 1f)
    assertCentred("برچسب", l, c)
    //  کادرِ کشویی دکمهٔ خودش است: زدنش کارت را جمع نمی‌کند
    rule.onNode(dropdown).performClick()
    rule.waitForIdle()
    assertEquals(StrA11y.expanded, state())
    //  بسته ⇒ یک خط، بی کادرِ کشویی
    rule.onNode(header).performClick()
    rule.waitForIdle()
    assertEquals(StrA11y.collapsed, state())
    assertTrue(rule.onAllNodes(dropdown).fetchSemanticsNodes().isEmpty())
    assertEquals(1, textNodes(collapsed()).size)
  }

  @Test fun breakdownChartRowsAreInsideTheCardWithMoreAndPick() {
    val colors = listOf(Color.Red, Color.Blue, Color.Green, Color.Magenta, Color.Cyan, Color.Yellow, Color.Gray)
    val bars = (1..7).map { i -> V3Bar("k$i", "دسته $i", "${i * 100}", i * 100.0, colors[i - 1]) }.reversed()
    show("test65s/i", section = V3Section.EXPENSES, bars = bars)
    val c = card()
    fun row(i: Int) = rule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("دسته $i: ${i * 100}")))
      .fetchSemanticsNodes()
    //  پنج ردیفِ بزرگ پیدا، دو تا زیرِ «… ۲ مورد دیگر»
    (3..7).forEach { i -> val r = row(i).single().boundsInRoot; assertTrue("ردیفِ $i درونِ کارت", r.top >= c.top && r.bottom <= c.bottom && r.left >= c.left && r.right <= c.right) }
    assertTrue(row(1).isEmpty() && row(2).isEmpty())
    rule.onAllNodes(hasClickAction() and hasText(StrLook.moreRows(2)))[0].performClick()
    rule.waitForIdle()
    assertTrue("زدنِ «بیشتر» همه را نشان می‌دهد", row(1).size == 1 && row(2).size == 1)
    rule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("دسته 6: 600")))[0].performClick()
    rule.waitForIdle()
    assertEquals(listOf("k6"), picked)
    //  بسته ⇒ نمودار پنهان
    rule.onNode(header).performClick()
    rule.waitForIdle()
    assertTrue(row(6).isEmpty())
  }

  private companion object {
    const val CARD = "summary-card"
    const val MONTH = "عقرب 1405"
  }
}
