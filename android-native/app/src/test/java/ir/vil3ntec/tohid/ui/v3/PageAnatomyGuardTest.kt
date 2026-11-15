package ir.vil3ntec.tohid.ui.v3

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  نگهبانِ متنیِ «کالبدِ یکسانِ صفحه» (`claude/e-phone-p0`، صاحب‌کار ۱۴۰۵/۰۸/۲۴: «یک بخش یک مدل، یک بخش مدلِ دیگه؛ یک سربرگ یک
 *  مدل … کجای این برنامه الان ساده است؟») — شرحِ کالبد در `V3Anatomy.kt` و `RULES-fa.md` («v3 · ظاهر و طراحی»). هر صفحهٔ گوشی:
 *  نوارِ `V3Scaffold` (عنوانِ وسط، بی عنوانِ درشتِ بدنه) · دست‌بالا یک `V3Summary` · پالایه‌ها یک ردیف زیرِ نوار · فهرست با
 *  `V3DataRow`/`V3NavRow` · کارِ اصلی یک `V3Fab` در جای همیشگی · خالی `V3EmptyState` · برگه فقط `V3Sheet`.
 *
 *  هر استثنا این‌جا با **دلیلِ نوشته** است؛ صفحهٔ تازه به فهرست نمی‌رود مگر دلیلی از همین جنس داشته باشد. نگهبانِ روی درختِ
 *  واقعیِ هر مسیر: `PageAnatomyUiTest` (کامپیوتر)؛ عکسِ همهٔ مسیرها: `PhoneAnatomyAuditTest` (`P0_AUDIT=<پوشه>`).
 */
class PageAnatomyGuardTest {

  private val main: File = listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
    .map { File(it, "ir/vil3ntec/tohid") }.first { it.isDirectory }

  private fun rel(f: File) = f.relativeTo(main).path.replace('\\', '/')

  private fun text(path: String) = File(main, path).readText().replace("\r\n", "\n")

  /** کدِ رابطِ گوشی: همهٔ `ui/` جز سربرگ/منوی کامپیوتر (`ui/desk`، `Desk*.kt`، کارِ عامل‌های کامپیوتر) */
  private fun phoneUi(): List<File> = File(main, "ui").walkTopDown()
    .filter { it.isFile && it.extension == "kt" && !rel(it).startsWith("ui/desk/") && !it.name.startsWith("Desk") }
    .toList()

  /** جاهای یک الگو در کدِ گوشی، بی شرح‌ها (خطِ `//` و `*`) */
  private fun uses(pattern: Regex, files: List<File> = phoneUi()): Map<String, Int> {
    val out = LinkedHashMap<String, Int>()
    for (f in files) {
      val n = f.readLines().count { line ->
        val t = line.trim()
        !t.startsWith("//") && !t.startsWith("*") && !t.startsWith("/*") && pattern.containsMatchIn(line.substringBefore("//"))
      }
      if (n > 0) out[rel(f)] = n
    }
    return out
  }

  @Test fun `عنوانِ درشت فقط در قابِ تنظیمات — هیچ صفحه‌ای عنوانش را درشت در بدنه نمی‌نویسد`() {
    //  `V3Scaffold(largeTitle = …)`: تنها روشن‌کننده `SettingsPageFrame` است (دسته‌های تنظیمات، ۶۵r) — بقیه عنوان را در نوار دارند
    val bad = uses(Regex("""largeTitle\s*=\s*(?!false\b)""")).keys - setOf("ui/v3/V3Scaffold.kt", "ui/v3/settings/SettingsPageFrame.kt", "ui/v3/V3Anatomy.kt")
    assertTrue("عنوانِ درشت بیرون از تنظیمات: $bad", bad.isEmpty())
    //  پیش‌فرضِ قاب همان «عنوان در نوار» است
    assertTrue(text("ui/v3/V3Scaffold.kt").contains("largeTitle: Boolean = false"))
  }

  /**
   *  `V3Type.largeTitle` (۳۰sp) نوشتهٔ عنوانِ درشت است و در بدنهٔ صفحه نمی‌آید — عددِ درشتِ سرِ صفحه `V3Summary` است. فقط جایی که
   *  خودِ صفحه/برگه «یک عدد یا رمز برای خواندن» است، یا دروازهٔ پیش از برنامه (پسندیدهٔ صاحب‌کار، دست‌نخورده).
   */
  private val largeTypeAllowed = mapOf(
    "ui/v3/V3Scaffold.kt" to "عنوانِ درشتِ قاب (فقط تنظیمات)",
    "ui/v3/analytics/AnalyticsParts.kt" to "`HeroFigure`: عددِ اصلیِ گزارش درونِ `V3SummaryFrame`",
    "ui/v3/sales/cart/KeypadSheet.kt" to "برگهٔ صفحه‌کلیدِ عدد: خودِ عددِ در حالِ نوشتن",
    "ui/v3/online/delivery/CodeOnceSheet.kt" to "برگهٔ رمزِ یک‌بارهٔ تحویل: رمز برای خواندن به پیک",
    "ui/v3/settings/devices/HubLanCardAndroid.kt" to "کدِ جفت‌کردنِ دستگاه برای خواندن",
    "ui/v3/settings/devices/PhoneLinkCard.kt" to "کدِ جفت‌کردنِ گوشی برای خواندن",
    "ui/v3/modes/ModeChooser.kt" to "دروازهٔ انتخابِ حالت پیش از برنامه (بی نوار)",
    "ui/v3/onboarding/GateFrame.kt" to "صفحهٔ ورودِ قدیمی — پسندیدهٔ صاحب‌کار، `LookGuardTest`",
    "ui/v3/onboarding/LockGate.kt" to "قفلِ رمز — همان زمینهٔ ورود، `LookGuardTest`",
    "ui/v3/analytics/StatsPage.kt" to "ورقهٔ دومِ خانه (`HomePager`) — خانه سربرگِ خودش را دارد",
    //  ⏳ مانده برای گامِ بعدیِ p0 (عددِ درشتِ موجودی ⇒ `V3Summary`)؛ با کوچ از این فهرست برمی‌دارید
    "ui/v3/products/ProductDetailParts.kt" to "⏳ گامِ بعدی: موجودیِ درشتِ جزئیاتِ کالا ⇒ V3Summary",
    "ui/v3/stock/StockItemScreen.kt" to "⏳ گامِ بعدی: موجودیِ درشتِ جزئیاتِ انبار ⇒ V3Summary",
  )

  @Test fun `نوشتهٔ عنوانِ درشت در بدنه نیست — عددِ سرِ صفحه خلاصه است`() {
    val bad = uses(Regex("""V3Type\.largeTitle\b""")).keys - largeTypeAllowed.keys
    assertTrue("V3Type.largeTitle در بدنه (به‌جایش V3Summary یا عنوانِ نوار): $bad", bad.isEmpty())
    //  فهرستِ استثنا کهنه نمی‌ماند
    val stale = largeTypeAllowed.keys.filter { !File(main, it).isFile || !text(it).contains("V3Type.largeTitle") }
    assertTrue("استثنای کهنه: $stale", stale.isEmpty())
  }

  @Test fun `کادرِ بزرگِ گرادیانی فقط در خانه`() {
    val allowed = setOf(
      "ui/v3/V3HeroCard.kt", "ui/v3/shell/HomeSalesCard.kt", "ui/v3/shell/HomeDrawerCard.kt",
      //  نمونهٔ تمِ همان کادر در «ظاهر»
      "ui/v3/gallery/ThemeGallery.kt",
    )
    val bad = uses(Regex("""\bV3HeroCard\(""")).keys - allowed
    assertTrue("V3HeroCard بیرون از خانه: $bad", bad.isEmpty())
  }

  @Test fun `هیچ سربرگ یا دکمهٔ شناورِ خامِ Material — همه از کیت`() {
    val chrome = Regex("""\b(TopAppBar|CenterAlignedTopAppBar|MediumTopAppBar|LargeTopAppBar|FloatingActionButton|ExtendedFloatingActionButton|SmallFloatingActionButton|LargeFloatingActionButton)\(""")
    val bad = uses(chrome)
    assertTrue("سربرگ/دکمهٔ شناورِ خامِ Material: $bad", bad.isEmpty())
    //  `Scaffold` ِ Material فقط پوستهٔ ریشه (سربرگِ کامپیوتر، پیام‌ها) و پوستهٔ حالتِ پیک (برنامه‌ای جدا در همان نصب، §۴۰ بندِ ۲) —
    //  هر صفحهٔ دکان `V3Scaffold`
    val scaffolds = uses(Regex("""(?<![\w.])Scaffold\(""")).keys - setOf("ui/AppRoot.kt", "ui/courier/CourierHome.kt")
    assertTrue("Scaffold ِ Material در صفحه: $scaffolds", scaffolds.isEmpty())
  }

  /** دکمهٔ شناورِ کارِ اصلی فقط در جای قاب (`V3Scaffold(fab = …)`) — همه‌جا همان گوشه و همان رفتارِ جمع‌شدن */
  private val fabOutsideSlot = mapOf(
    "ui/components/pos/PhoneCatalog.kt" to "صندوق: اسکن تنها دکمهٔ شناور (قاعدهٔ ۶۳w؛ صفحهٔ فروش کارِ عاملِ p1)",
    "ui/v3/V3Fab.kt" to "تعریفِ خودِ دکمه",
  )

  @Test fun `دکمهٔ شناور فقط در جای قاب`() {
    val bad = ArrayList<String>()
    for (f in phoneUi()) {
      val path = rel(f)
      if (path in fabOutsideSlot) continue
      val src = f.readText()
      Regex("""(?<![\w.])V3Fab\(""").findAll(src).forEach { m ->
        //  همان خط یا چند خطِ پیشش `fab =` ِ قاب است
        val before = src.substring(maxOf(0, m.range.first - 240), m.range.first)
        if (!Regex("""\bfab\s*=""").containsMatchIn(before)) bad += "$path:${src.substring(0, m.range.first).count { it == '\n' } + 1}"
      }
    }
    assertTrue("V3Fab بیرون از `V3Scaffold(fab = …)`: $bad", bad.isEmpty())
    //  جای دکمه یکی است: خودِ قاب آن را بالای نوار و در گوشهٔ پایان می‌گذارد
    val scaffold = text("ui/v3/V3Scaffold.kt")
    assertEquals(1, Regex("""\.align\(Alignment\.Bottom(End|Start)\)""").findAll(scaffold).count())
  }

  @Test fun `یک سبکِ برگه — V3Sheet`() {
    val allowed = mapOf(
      "ui/v3/V3Sheet.kt" to "خودِ برگهٔ کیت",
      "ui/screens/DebtorSheets.kt" to "برگه‌های پروندهٔ قرض‌دار — ظاهرِ پسندیدهٔ صاحب‌کار (۶۵q)، دست نخورد",
    )
    val bad = uses(Regex("""(?<![\w.])ModalBottomSheet\(""")).keys - allowed.keys
    assertTrue("برگهٔ خامِ Material (به‌جایش V3Sheet): $bad", bad.isEmpty())
    //  برگهٔ سازگارِ قدیم روی گوشی همان V3Sheet است
    val adaptive = text("ui/screens/AdaptiveSheet.kt")
    assertTrue(adaptive.contains("V3Sheet(onDismiss = onDismissRequest"))
  }

  @Test fun `نوارِ «نسخهٔ پیش‌نمایش» روی هیچ صفحه‌ای نیست`() {
    assertTrue("PreviewBanner برگشت: ${uses(Regex("""\bPreviewBanner\("""))}", uses(Regex("""\bPreviewBanner\(""")).isEmpty())
    val root = text("ui/AppRoot.kt")
    val notice = root.lines().filter { "LocalV3Notice provides" in it }
    assertTrue(notice.isNotEmpty())
    assertFalse("پیش‌نمایش در جای پیامِ صفحه: $notice", notice.any { "Preview" in it })
    //  نشانش کنارِ نسخه در «بیشتر» و «درباره» است
    assertTrue(text("ui/screens/MorePhone.kt").contains("previewVersion("))
    assertTrue(text("ui/v3/settings/sections/AboutScreen.kt").contains("previewVersion("))
    assertFalse("نوارِ رنگی", text("ui/PreviewBanner.kt").contains(".background("))
  }
}
