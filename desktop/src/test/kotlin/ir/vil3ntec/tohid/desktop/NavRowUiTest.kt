package ir.vil3ntec.tohid.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.desktop.shots.StockDemo
import ir.vil3ntec.tohid.desktop.shots.V3RoutePreview
import ir.vil3ntec.tohid.ui.screens.MoreLayout
import ir.vil3ntec.tohid.ui.screens.MoreRow
import ir.vil3ntec.tohid.ui.screens.SettingsLayout
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import ir.vil3ntec.tohid.ui.v3.V3Hue
import ir.vil3ntec.tohid.ui.v3.V3NavRowSpec
import ir.vil3ntec.tohid.ui.v3.V3Section
import ir.vil3ntec.tohid.ui.v3.nav.V3Modules
import ir.vil3ntec.tohid.ui.v3.settings.CategoryPart
import ir.vil3ntec.tohid.ui.v3.settings.PartCard
import ir.vil3ntec.tohid.ui.v3.settings.SettingsFacts
import ir.vil3ntec.tohid.ui.v3.settings.SettingsHomeCard
import ir.vil3ntec.tohid.ui.v3.settings.SettingsRow
import ir.vil3ntec.tohid.ui.v3.settings.SyncState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 *  «یک سبکِ ردیف» (۶۵r) روی گره‌های واقعی، گوشیِ ۳۶۰dp:
 *   • ردیفِ «بیشتر» و ردیفِ تنظیمات (بخش و دسته) همان نوشتهٔ عنوان (۱۶)، همان کاشی (۴۰×۴۰) و همان کمینهٔ بلندی (۵۶) را دارند؛
 *     ردیفِ یک‌خطی درست ۵۶ و همهٔ ردیف‌های با خطِ راهنما یک بلندی؛
 *   • هر ردیفِ دیدنیِ تنظیمات در هر چهار زبان کاشی دارد، عنوانش یک خطِ کامل و خطِ راهنمایش یک خطِ ۱۳ است — بی «…»؛
 *   • هیچ صفحهٔ تنظیمات عنوانِ درشت در بدنه ندارد (عنوان در سربرگ) و هر ردیفش کاشی دارد.
 *  نگهبانِ متنی: `V3NavRowGuardTest`.
 */
@OptIn(ExperimentalTestApi::class)
class NavRowUiTest {

  @get:Rule val rule = createComposeRule()
  private lateinit var h: RouteUiHarness

  @Before fun up() {
    ShopRole.remember(DesktopContext, ShopRole.OWNER)
    h = RouteUiHarness(rule)
  }

  @After fun down() {
    h.close()
    I18n.source = { Lang.FA }
  }

  /** بدترین حالِ واقعی: هشدارِ قرض روشن، همگام، دو دستگاه، چاپگرِ نامعلوم */
  private val facts = SettingsFacts(
    debtLimit = 5000.0, signedIn = true, sessions = 2, sync = SyncState.OK, lastSyncOk = 1_000_000, now = 1_000_000 + 2 * 60_000,
    version = "2.14.5", serverOn = SettingsLayout.SERVER_GATED, owner = true,
  )

  @Composable
  private fun Phone(lang: Lang, content: @Composable () -> Unit) {
    CompositionLocalProvider(
      LocalContext provides DesktopContext,
      LocalView provides remember { android.view.View(DesktopContext) },
      LocalConfiguration provides Configuration(360, 760),
    ) {
      TohidTheme(ThemeChoice.LIGHT) {
        CompositionLocalProvider(LocalLayoutDirection provides if (lang == Lang.EN) LayoutDirection.Ltr else LayoutDirection.Rtl) {
          //  همان کناره‌های «بیشتر» و قابِ تنظیمات
          Column(Modifier.requiredWidth(360.dp).verticalScroll(rememberScrollState()).padding(horizontal = V3NavRowSpec.GUTTER)) { content() }
        }
      }
    }
  }

  private fun rows(): List<SemanticsNode> = rule.onAllNodesWithTag(V3NavRowSpec.ROW_TAG, useUnmergedTree = true).fetchSemanticsNodes()

  private fun SemanticsNode.descendants(): List<SemanticsNode> = children.flatMap { listOf(it) + it.descendants() }

  private fun SemanticsNode.texts(): List<SemanticsNode> = descendants().filter { it.config.getOrNull(SemanticsProperties.Text) != null }

  private fun SemanticsNode.tiles(): List<SemanticsNode> = descendants().filter { it.config.getOrNull(SemanticsProperties.TestTag) == V3NavRowSpec.TILE_TAG }

  private fun SemanticsNode.text(): String = config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()

  private fun SemanticsNode.layout(): TextLayoutResult {
    val list = ArrayList<TextLayoutResult>()
    config[SemanticsActions.GetTextLayoutResult].action!!.invoke(list)
    return list.first()
  }

  private fun SemanticsNode.fontSize(): TextUnit = layout().layoutInput.style.fontSize

  private fun dp(px: Int): Float = px / rule.density.density

  @Test
  fun `ردیفِ «بیشتر» و ردیفِ تنظیمات یک نوشته، یک کاشی و یک کمینهٔ بلندی دارند`() {
    I18n.source = { Lang.FA }
    rule.setContent {
      Phone(Lang.FA) {
        V3Section("بیشتر") { MoreRow(MoreLayout.Item("settings:shop", "دکان و امنیت", "settings/shop", icon = Icons.Outlined.Shield, hue = V3Hue.BRAND)) {} }
        V3Section("تنظیمات") { SettingsRow("هشدارها", facts, onClick = {}, onLockToggle = {}) } // داده: کلیدِ بخشِ تنظیمات
        SettingsHomeCard(selected = null) {}
      }
    }
    rule.waitForIdle()
    val all = rows()
    assertEquals("یک ردیفِ «بیشتر»، یک ردیفِ بخش و شش دسته", 8, all.size)
    val more = all.first()
    val heights = all.map { dp(it.size.height) }
    //  کمینهٔ همه همان ۵۶ ِ «بیشتر»؛ ردیفِ یک‌خطی درست ۵۶، و ردیف‌های با خطِ راهنما همه یک بلندی (بلندیِ طبیعیِ دو خطِ وزیرمتن)
    heights.forEach { assertTrue("بلندیِ ردیف $it < ۵۶", it >= 55.5f) }
    assertEquals("ردیفِ یک‌خطیِ «بیشتر»", 56f, dp(more.size.height), 0.6f)
    val twoLine = heights.drop(1)
    assertEquals("ردیف‌های تنظیمات یک بلندی ندارند: $twoLine", twoLine.first(), twoLine.maxOrNull()!!, 0.6f)
    assertEquals("ردیف‌های تنظیمات یک بلندی ندارند: $twoLine", twoLine.first(), twoLine.minOrNull()!!, 0.6f)
    all.forEach { r ->
      val tile = r.tiles().singleOrNull() ?: error("ردیفِ «${r.texts().firstOrNull()?.text()}» کاشی ندارد")
      assertEquals(40f, dp(tile.size.width), 0.6f)
      assertEquals(40f, dp(tile.size.height), 0.6f)
      val t = r.texts()
      assertEquals("عنوانِ «${t[0].text()}»", 16.sp, t[0].fontSize())
      //  خطِ راهنما (و مقدار) ۱۳ — همان «نوشتهٔ کم‌رنگِ» ردیفِ «بیشتر»
      t.drop(1).forEach { assertEquals("«${it.text()}»", 13.sp, it.fontSize()) }
    }
  }

  @Test
  fun `هر ردیفِ دیدنیِ تنظیمات در چهار زبان کاشی دارد و عنوان و راهنمایش در ۳۶۰dp یک خطِ کامل است`() {
    val lang = mutableStateOf(Lang.FA)
    //  با حساب (مقدارِ «۵٬۰۰۰ ؋ به بالا»، «۲ دقیقه پیش»، کلیدِ قفل) و بی حساب («وارد نشده» کنارِ پیکان)
    val state = mutableStateOf(facts)
    I18n.source = { lang.value }
    val extras = V3Modules.ALL.filter { it.id != "example" }.flatMap { it.settings }
    rule.setContent {
      val facts = state.value
      key(lang.value, facts) {
        Phone(lang.value) {
          SettingsHomeCard(selected = null) {}
          SettingsLayout.GROUPS.forEach { g ->
            val all = g.parts
            SettingsLayout.visibleParts(g.title, false, SettingsLayout.SERVER_GATED, SettingsLayout.FEATURES.toSet()).forEach { p ->
              PartCard(
                CategoryPart(p.id, p.rows, extras.filter { it.group == g.title && (it.part ?: all.last().id) == p.id }),
                facts, onRow = {}, onExtra = {}, onLockToggle = {},
              )
            }
          }
        }
      }
    }
    val bad = ArrayList<String>()
    var seen = 0
    for (f in listOf(facts, SettingsFacts(serverOn = SettingsLayout.SERVER_GATED, owner = true))) for (l in listOf(Lang.FA, Lang.PRS, Lang.EN, Lang.PS)) {
      state.value = f
      lang.value = l
      rule.waitForIdle()
      rows().forEach { r ->
        seen++
        val t = r.texts()
        val title = t.first()
        val tag = "$l${if (state.value.signedIn) "" else " (بی حساب)"}"
        if (r.tiles().size != 1) bad += "$tag بی کاشی: «${title.text()}»"
        title.layout().let { if (it.lineCount != 1 || it.isLineEllipsized(0)) bad += "$tag عنوان: «${title.text()}»" }
        t.getOrNull(1)?.let { help ->
          help.layout().let { if (it.lineCount != 1 || it.isLineEllipsized(0)) bad += "$tag راهنما: «${help.text()}» (${title.text()})" }
        }
      }
    }
    println("nav rows checked: $seen (fa, prs, en, ps × با حساب و بی حساب)")
    assertTrue("ردیف‌ها کشیده نشدند", seen > 100)
    assertTrue("در ۳۶۰dp بریده یا بی کاشی:\n" + bad.joinToString("\n"), bad.isEmpty())
  }

  @Test
  fun `صفحه‌های تنظیمات عنوانِ درشت در بدنه ندارند و هر ردیفشان کاشی دارد`() {
    val path = mutableStateOf("settings")
    I18n.source = { Lang.FA }
    val store = RouteUiHarness.loadedStore(StockDemo.full())
    rule.setContent {
      CompositionLocalProvider(
        LocalContext provides DesktopContext,
        LocalView provides remember { android.view.View(DesktopContext) },
        LocalConfiguration provides Configuration(360, 760),
      ) {
        TohidTheme(ThemeChoice.LIGHT) { V3RoutePreview(path.value, store, dark = false) }
      }
    }
    val pages = listOf("settings") +
      SettingsLayout.GROUPS.filter { it.title !in SettingsLayout.GROUP_LINKS }.map { "settings/" + SettingsLayout.GROUP_SLUGS.getValue(it.title) } +
      listOf("settings/shop/app-lock", "settings/data/sync", "settings/about")
    for (p in pages) {
      path.value = p
      rule.waitForIdle()
      val root = rule.onRoot(useUnmergedTree = true).fetchSemanticsNode()
      val big = (listOf(root) + root.descendants()).filter { n ->
        n.config.getOrNull(SemanticsProperties.Text) != null && n.sizeOrNull()?.let { it.isSp && it.value >= 28f } == true
      }
      assertTrue("$p: عنوانِ درشت در بدنه «${big.joinToString { it.text() }}»", big.isEmpty())
      //  هر ردیفِ صفحهٔ اول و صفحهٔ دسته همان ردیفِ «بیشتر» است (`NAV`) و کاشی دارد (صفحهٔ بخش شاید ردیفی نداشته باشد)
      if (p == "settings" || p.count { it == '/' } == 1) {
        val r = rows()
        assertTrue("$p: ردیفِ `NAV` ندارد", r.isNotEmpty())
        r.forEach { assertEquals("$p: «${it.texts().firstOrNull()?.text()}»", 1, it.tiles().size) }
      }
    }
  }

  private fun SemanticsNode.sizeOrNull(): TextUnit? {
    val get = config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action ?: return null
    val list = ArrayList<TextLayoutResult>()
    return if (runCatching { get(list) }.getOrDefault(false)) list.firstOrNull()?.layoutInput?.style?.fontSize else null
  }
}
