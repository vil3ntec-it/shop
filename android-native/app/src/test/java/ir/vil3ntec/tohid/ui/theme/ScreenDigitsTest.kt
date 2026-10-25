package ir.vil3ntec.tohid.ui.theme

import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.text.LocaleFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  رقمِ صفحه: انگلیسی همیشه لاتین است، فارسی و پشتو همان انتخابِ دکان (LOC-05، ۳۴۸).
 *  `TohidTheme` قلمِ رقم را از `LocaleChoice.screenDigits` می‌گیرد، نه از `LocaleChoice.digits` ِ خام —
 *  وگرنه انگلیسی با دکانِ «رقمِ فارسی» رقمِ فارسی نشان می‌داد (عکسِ home-fa-digits-en).
 */
class ScreenDigitsTest {

  private val src: File? = listOf(
    "src/main/java/ir/vil3ntec/tohid",
    "../android-native/app/src/main/java/ir/vil3ntec/tohid",
    "android-native/app/src/main/java/ir/vil3ntec/tohid",
  ).map { File(it) }.firstOrNull { it.isDirectory }

  @Test
  fun `انگلیسی همیشه رقمِ لاتین، فارسی و پشتو انتخابِ دکان`() {
    val fa = LocaleFormat.DIGITS_FA
    val latn = LocaleFormat.DIGITS_LATN
    assertEquals(latn, I18n.screenDigits(Lang.EN, fa))
    assertEquals(fa, I18n.screenDigits(Lang.FA, fa))
    assertEquals(fa, I18n.screenDigits(Lang.PS, fa))
    assertEquals(latn, I18n.screenDigits(Lang.FA, latn))
  }

  @Test
  fun `قلمِ رقمِ برنامه از screenDigits است`() {
    assertTrue("پوشهٔ کد پیدا نشد", src != null)
    val theme = File(src, "ui/theme/Theme.kt").readText()
    assertTrue("TohidTheme باید رقم را از LocaleChoice.screenDigits بخواند", "LocaleChoice.screenDigits" in theme)
    assertTrue("TohidTheme نباید LocaleChoice.digits ِ خام را بخواند", !Regex("""LocaleChoice\.digits\b""").containsMatchIn(theme))
  }

  @Test
  fun `صفحه‌های نسخهٔ ۳ رقمِ خامِ دکان را فقط در صفحهٔ همان تنظیم می‌خوانند`() {
    assertTrue("پوشهٔ کد پیدا نشد", src != null)
    val v3 = File(src, "ui/v3")
    //  «تاریخ و رقم» خودِ انتخاب را نشان می‌دهد؛ بقیهٔ صفحه‌ها رقم را از قلم (`screenDigits`) می‌گیرند
    val allowed = setOf("settings/sections/DigitsScreen.kt", "settings/SettingsRows.kt")
    val bad = v3.walkTopDown().filter { it.isFile && it.extension == "kt" }
      .filter { Regex("""LocaleChoice\.digits\b""").containsMatchIn(it.readText()) }
      .map { it.relativeTo(v3).invariantSeparatorsPath }
      .filterNot { it in allowed }
      .toList()
    assertEquals(emptyList<String>(), bad)
  }
}
