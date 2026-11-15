package ir.vil3ntec.tohid.ui.v3

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.ui.screens.SettingsLayout
import ir.vil3ntec.tohid.ui.theme.PhoneV3Colors
import ir.vil3ntec.tohid.ui.theme.PhoneV3Palette
import ir.vil3ntec.tohid.ui.theme.ShopTheme
import ir.vil3ntec.tohid.ui.theme.shopColors
import ir.vil3ntec.tohid.ui.v3.settings.SettingsHues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  «یک سبکِ ردیف برای همهٔ فهرست‌ها» (صاحب‌کار، ۶۵r: «دقیق همه چی منظم باشه شبیهِ اون عکسِ اول، حتی تک‌تک نوشته‌ها»):
 *   • «بیشتر»، صفحهٔ اول و دسته‌های تنظیمات، «حساب من» و مرکزِ مدیریت همان `V3NavRow` را می‌کشند و هر ردیفِ درونِ صفحهٔ
 *     تنظیمات `NAV` است (`SettingsPageFrame` ⇒ `LocalV3RowLook`)؛
 *   • هیچ ردیفِ تنظیمات آیکنِ خطیِ بی‌کاشی ندارد (`tile = false` نیست و `NAV` کاشی را همیشه می‌کشد)؛
 *   • عنوانِ صفحه‌های تنظیمات در سربرگ است، نه درشت در بدنه؛
 *   • رنگِ کاشی فقط از یک نقشه (`SettingsHues`)؛ در یک صفحه دو بخشِ هم‌رنگ نیست؛
 *   • اندازه‌ها: عنوان ≥ ۱۶، راهنما ≥ ۱۳، ردیف ≥ ۵۶، کاشی ۴۰؛ نوشته‌ها در همهٔ تم‌ها، روز و شب، AA.
 *  همان اندازه در گره‌های واقعی: `NavRowUiTest` ِ کامپیوتر.
 */
class V3NavRowGuardTest {
  private val repo: File = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "android-native").isDirectory }
  private val main = File(repo, "android-native/app/src/main/java/ir/vil3ntec/tohid")

  private fun text(path: String) = File(main, path).readText().replace("\r\n", "\n")

  private fun under(dir: String): Sequence<File> = File(main, dir).walkTopDown().filter { it.isFile && it.extension == "kt" }

  private fun body(src: String, head: String): String = src.substringAfter(head).substringBefore("\n}\n")

  @Test
  fun `«بیشتر» و تنظیمات و «حساب من» همان یک ردیف را می‌کشند`() {
    assertTrue("«بیشتر»", body(text("ui/screens/MorePhone.kt"), "internal fun MoreRow(").contains("V3NavRow("))
    assertTrue("«بیشتر» با `MoreRow`", body(text("ui/screens/MorePhone.kt"), "fun PhoneMoreScreen(").contains("MoreRow(item)"))
    val rows = text("ui/v3/settings/SettingsRows.kt")
    assertTrue("ردیفِ بخش", body(rows, "internal fun SettingsRow(").contains("V3NavRow("))
    assertTrue("ردیفِ ماژول", body(rows, "internal fun PartCard(").contains("V3NavRow("))
    val index = text("ui/v3/settings/SettingsIndexScreen.kt")
    assertTrue("شش دسته", body(index, "internal fun SettingsHomeCard(").contains("V3NavRow("))
    assertTrue("نتیجهٔ جست‌وجو", body(index, "internal fun LazyListScope.searchResults(").contains("V3NavRow("))
    listOf(rows, index).forEach { assertFalse("ردیفِ دیگری جز `V3NavRow`", it.contains("V3ListRow(")) }
    assertTrue("«حساب من»", text("ui/v3/account/profile/ProfileScreen.kt").contains("V3NavRow("))
    assertTrue("مرکزِ مدیریت", text("ui/v3/analytics/AdminScreen.kt").contains("V3NavRow("))
    //  همان ردیف = `V3ListRow` با نمای `NAV` (یک پیاده‌سازی، نه کپی)
    val kit = text("ui/v3/V3ListRow.kt")
    assertTrue(body(kit, "fun V3NavRow(").contains("look = V3RowLook.NAV"))
    assertTrue(kit.contains("style = if (nav) V3Type.rowTitle else V3Type.body"))
    assertTrue(kit.contains("maxLines = if (nav) 1 else subtitleLines"))
  }

  @Test
  fun `هر ردیفِ درونِ صفحهٔ تنظیمات همان ردیف است و هیچ آیکنِ بی‌کاشی ندارد`() {
    val frame = text("ui/v3/settings/SettingsPageFrame.kt")
    assertTrue("قابِ تنظیمات نمای `NAV` می‌دهد", frame.contains("LocalV3RowLook provides V3RowLook.NAV"))
    assertTrue("رنگِ کاشیِ صفحه از همان بخش", frame.contains("LocalV3RowHue provides pageHue"))
    val bare = (under("ui/v3/settings") + under("ui/v3/account/profile") + sequenceOf(File(main, "ui/screens/MorePhone.kt")))
      .filter { Regex("""\btile\s*=\s*false""").containsMatchIn(it.readText()) }
      .map { it.relativeTo(main).invariantSeparatorsPath }.toList()
    assertTrue("آیکنِ خطیِ بی‌کاشی در ردیفِ تنظیمات: $bare", bare.isEmpty())
    //  نمای `NAV` کاشی را حتی با `tile = false` می‌کشد
    assertTrue(text("ui/v3/V3ListRow.kt").contains("val tiled = icon != null && (tile || nav)"))
  }

  @Test
  fun `عنوانِ صفحه‌های تنظیمات در سربرگ است — نه درشت در بدنه`() {
    assertTrue(text("ui/v3/settings/SettingsPageFrame.kt").contains("largeTitle: Boolean = false,"))
    //  `SettingsPageFrame` خودش پارامتر را با پیش‌فرضِ `false` به `V3Scaffold` می‌دهد
    val big = under("ui/v3/settings").filter { it.name != "SettingsPageFrame.kt" }.flatMap { f ->
      val src = f.readText()
      Regex("""V3Scaffold\((?:[^()]|\([^()]*\))*\)""").findAll(src).filterNot { it.value.contains("largeTitle = false") }
        .map { f.name } + (if (src.contains("largeTitle = true")) sequenceOf(f.name) else emptySequence())
    }.toList()
    assertTrue("عنوانِ درشت در صفحهٔ تنظیمات: $big", big.isEmpty())
  }

  @Test
  fun `رنگِ کاشی از یک نقشه — هر بخش یک رنگ، در یک صفحه بی تکرار`() {
    //  رنگ‌های ثبت‌شدهٔ ردیف در صفحه‌های تنظیمات فقط در `SettingsHues` (و کاشیِ بزرگِ سرِ صفحهٔ «درباره»/«قفل»، و رنگِ ماژول)
    val allowed = setOf("SettingsHues.kt", "SettingsRoutes.kt", "AboutScreen.kt", "AppLockScreen.kt")
    val stray = under("ui/v3/settings").filter { it.name !in allowed && it.readText().contains("V3Hue.") }.map { it.name }.toList()
    assertTrue("رنگِ دستیِ ردیف بیرون از `SettingsHues`: $stray", stray.isEmpty())
    SettingsLayout.GROUPS.forEach { g ->
      val hues = g.parts.map { p -> SettingsHues.PART[p.id] ?: error("${p.id} رنگ ندارد") }
      assertEquals("بخشِ اولِ «${g.title}» همان رنگِ دسته", SettingsHues.GROUP[g.title], hues.first())
      //  در یک صفحه هر بخش رنگِ جدا (شمارهٔ رنگِ همراهِ جدا)؛ برند کنارِ آسمانی/بنفش نه (در تم‌های دیگر تقریباً یکی‌اند)
      val idx = hues.map { PhoneV3Palette.accentIndex(it) }
      assertEquals("«${g.title}»: $hues", idx.size, idx.toSet().size)
      if (null in idx) assertTrue("«${g.title}»: برند کنارِ آسمانی/بنفش", idx.none { it == 0 || it == 1 })
    }
    //  هر ردیفِ هر دسته رنگِ بخشِ خودش
    SettingsLayout.GROUPS.forEach { g -> g.parts.forEach { p -> p.rows.forEach { assertEquals(it, SettingsHues.PART[p.id], SettingsHues.of(it)) } } }
    //  «بیشتر ⇐ تنظیمات» و صفحهٔ اولِ تنظیمات همان رنگِ دسته
    SettingsLayout.GROUPS.forEach { assertEquals(it.title, SettingsHues.GROUP[it.title], SettingsHues.of(it.title)) }
  }

  @Test
  fun `اندازه‌ها همان «بیشتر» و خوانا`() {
    assertTrue("عنوان ≥ ۱۶", V3NavRowSpec.TITLE_SP >= 16)
    assertTrue("راهنما ≥ ۱۳", V3NavRowSpec.HELP_SP >= 13)
    assertTrue("ردیف ≥ ۵۶", V3NavRowSpec.MIN_HEIGHT >= 56.dp)
    assertEquals(40.dp, V3NavRowSpec.TILE)
    assertEquals(V3NavRowSpec.PAD_START + V3NavRowSpec.TILE + V3NavRowSpec.GAP, V3NavRowSpec.DIVIDER_START)
    //  دو خطِ ۲۲ + ۱۸ با ۸ بالا و پایین = ۵۶ ⇒ ردیفِ با راهنما و بی راهنما یک بلندی
    val base = text("ui/v3/V3Base.kt")
    assertTrue(base.contains("val rowTitle: TextStyle @Composable get() = v3Text(V3NavRowSpec.TITLE_SP, FontWeight.Medium, 22)"))
    assertTrue(base.contains("val rowHelp: TextStyle @Composable get() = v3Text(V3NavRowSpec.HELP_SP, FontWeight.Medium, 18)"))
    assertEquals(56.dp, 22.dp + 18.dp + V3NavRowSpec.PAD_V * 2)
  }

  private fun contrast(a: Color, b: Color): Float {
    val l1 = a.luminance() + 0.05f
    val l2 = b.luminance() + 0.05f
    return maxOf(l1, l2) / minOf(l1, l2)
  }

  @Test
  fun `عنوان، راهنما و مقدار در هر تم، روز و شب، AA`() {
    for (theme in ShopTheme.entries) for (dark in listOf(false, true)) {
      val c = PhoneV3Colors.from(shopColors(theme, dark))
      val name = "$theme ${if (dark) "شب" else "روز"}"
      //  کارت و پُرشدنِ هنگامِ فشار و انتخاب
      listOf(c.card, if (dark) c.tile else androidx.compose.ui.graphics.lerp(c.card, c.bg, 0.9f), PhoneV3Palette.selected(c)).forEach { bg ->
        assertTrue("$name عنوان ${contrast(c.ink, bg)}", contrast(c.ink, bg) >= 4.5f)
        assertTrue("$name راهنما/مقدار ${contrast(c.inkMuted, bg)}", contrast(c.inkMuted, bg) >= 4.5f)
      }
      assertTrue("$name ردیفِ خطر ${contrast(c.negative, c.card)}", contrast(c.negative, c.card) >= 4.5f)
    }
  }
}
