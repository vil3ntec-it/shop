package ir.vil3ntec.tohid.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

/**
 *  «شکلِ قدیم هرگز» (تصمیمِ صاحب‌کار ۱۴۰۵/۰۸/۱۱) — رابطِ قدیم (پوشهٔ `ui/legacy`، رونوشت‌های `…Legacy`، پرچمِ
 *  `UiV3` و کلیدِ «رابطِ تازه (نسخهٔ ۳)») از برنامه پاک شد؛ رابطِ نسخهٔ ۳ تنها رابطِ گوشی، کامپیوتر و آیفون است.
 *  این آزمون نمی‌گذارد هیچ‌کدام برگردد. (جای `UiV3Test` ِ پیشین.)
 *
 *  «پاک‌سازی، گامِ ۳»: صفحه‌های بی‌مرجعِ رابطِ امروز در `ui/screens` ([GONE_SCREENS]) و `…SettingsWired`هایی که هیچ
 *  صفحه‌ای صدا نمی‌زد پاک شدند؛ نه پرونده‌شان برمی‌گردد، نه تعریفشان، و `…SettingsWired` ِ تازه ساخته نمی‌شود — تنظیمِ تازه
 *  صفحهٔ `ui/v3/settings` می‌گیرد ([WIRED_LEFT] فقط کم می‌شود).
 */
class NoLegacyUiTest {

  private val repo: File = generateSequence(File("").absoluteFile) { it.parentFile }
    .first { File(it, "android-native").isDirectory }

  private val ui = File(repo, "android-native/app/src/main/java/ir/vil3ntec/tohid/ui")

  /** کدِ برنامه‌ها: اندروید، کامپیوتر (افزوده‌های خودش) و آیفون (شیم‌ها) */
  private val sources: List<File> =
    listOf("android-native/app/src/main/java", "desktop/src/main/kotlin", "ios/shared/src/iosMain/kotlin")
      .map { File(repo, it) }.filter { it.isDirectory }
      .flatMap { d -> d.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }

  @Test
  fun `پوشهٔ رابطِ قدیم و رونوشت‌های Legacy دیگر نیستند`() {
    assertFalse("ui/legacy برگشته است", File(ui, "legacy").exists())
    val copies = ui.walkTopDown().filter { it.isFile && it.name.endsWith("Legacy.kt") }
      .map { it.relativeTo(ui).invariantSeparatorsPath }.toList()
    assertEquals(emptyList<String>(), copies)
  }

  @Test
  fun `نه پرچمی، نه کلیدی، نه صفحهٔ رونوشتی که رابطِ قدیم را برگرداند`() {
    val copy = Regex("""\bfun (?:[A-Za-z0-9_.]+\.)?[A-Z]\w*Legacy\(""")
    val hits = sources.filter { f ->
      val t = f.readText()
      t.contains("UiV3") || t.contains("\"ui-v3\"") || t.contains("\"ui_v3\"") || copy.containsMatchIn(t)
    }.map { it.relativeTo(repo).invariantSeparatorsPath }
    assertEquals(emptyList<String>(), hits)
  }

  @Test
  fun `برگه‌ی ساخت‌های کامپیوتر و آیفون فایلِ پاک‌شده‌ای را نام نمی‌برند`() {
    val lists = listOf("ios/shared/ios-exclude.txt", "desktop/build.gradle.kts", "ios/shared/build.gradle.kts")
      .map { File(repo, it) }.filter { it.isFile }
    val hits = lists.filter { it.readText().contains("ui/legacy/") || GONE_SCREENS.keys.any { f -> it.readText().contains("ui/screens/$f") } }
      .map { it.name }
    assertEquals(emptyList<String>(), hits)
  }

  @Test
  fun `صفحه‌های بی‌مرجعِ پاک‌شدهٔ ui_screens برنمی‌گردند`() {
    val back = GONE_SCREENS.keys.filter { File(ui, "screens/$it").exists() }
    assertEquals("پروندهٔ پاک‌شده برگشته است", emptyList<String>(), back)
    val def = Regex("""\bfun (?:[A-Za-z0-9_.]+\.)?(${GONE_SCREENS.values.joinToString("|")})\(""")
    val again = sources.filter { def.containsMatchIn(it.readText()) }.map { it.relativeTo(repo).invariantSeparatorsPath }
    assertEquals("صفحهٔ پاک‌شده با نامِ دیگری از پرونده برگشته است", emptyList<String>(), again)
  }

  @Test
  fun `SettingsWired ِ تازه ساخته نمی‌شود — تنظیمِ تازه صفحهٔ نسخهٔ ۳ می‌گیرد`() {
    val wired = Regex("""\bfun (?:[A-Za-z0-9_.]+\.)?([A-Z]\w*SettingsWired)\(""")
    val found = sources.flatMap { f -> wired.findAll(f.readText()).map { it.groupValues[1] }.toList() }.sorted()
    val extra = found - WIRED_LEFT
    assertEquals("…SettingsWired ِ تازه (یا پاک‌شده‌ای که برگشت) — نوشتنِ تنظیم در `ui/v3/settings`", emptyList<String>(), extra)
    //  فهرست فقط کم می‌شود: هر کدام که پاک شد از این‌جا هم برداشته شود
    assertEquals("از WIRED_LEFT بردارید", emptyList<String>(), (WIRED_LEFT - found.toSet()).sorted())
  }

  private companion object {
    /** پرونده ⇐ صفحهٔ بی‌مرجعی که در «پاک‌سازی، گامِ ۳» پاک شد */
    val GONE_SCREENS: Map<String, String> = mapOf(
      "Products.kt" to "ProductsScreen",
      "Warehouse.kt" to "WarehouseScreen",
      "Team.kt" to "TeamScreen",
      "Management.kt" to "ManagementScreen",
      "DeskDashboard.kt" to "DeskDashboardScreen",
      "DeskReports.kt" to "DeskReportsScreen",
      "TopBar.kt" to "TohidTopBar",
    )

    /**
     *  `…SettingsWired`هایی که هنوز صدازننده دارند: `ExpiryDaysSettingsWired` در صفحهٔ «کالای منقضی» ِ نسخهٔ ۳؛ بقیه در
     *  تنظیماتِ رابطِ امروز (`ui/screens/Settings.kt`، فقط پیش‌نمایشِ گوشیِ `DevShot`) یا از درونِ همدیگر. فقط کم شود.
     */
    val WIRED_LEFT: Set<String> = setOf(
      "ExpiryDaysSettingsWired", "GiftCardSettingsWired", "LayawaySettingsWired", "LinePricingSettingsWired",
      "PayFixSettingsWired", "PosPriceCheckSettingsWired", "PosSearchSettingsWired", "PriceListSettingsWired",
      "QuoteSettingsWired", "ReturnPolicySettingsWired", "SafeBookSettingsWired", "SpecialOrderSettingsWired",
    )
  }
}
