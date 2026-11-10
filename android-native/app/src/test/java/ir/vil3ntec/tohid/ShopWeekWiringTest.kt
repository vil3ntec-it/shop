package ir.vil3ntec.tohid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  LOC-03 — سیم‌کشیِ هفتهٔ دکان (حالتِ Compose، پس آزمونِ JVM صفحه را نمی‌سازد): همان منطقِ آزمودهٔ
 *  `core/calendar/ShopWeek` و `BusinessDay` با قاعدهٔ همین دکان (`ShopDayChoice.rules`) در هر صفحه‌ای که هفته، روزِ
 *  کاری یا «دیروز» نشان می‌دهد. منطقِ هر کدام در آزمونِ خودش است (`StatsModelTest`، `HomeHeroTest`،
 *  `HeatmapModelTest`، `PromoTextTest`، `ShopWeekTest`).
 */
class ShopWeekWiringTest {

  private val root: File by lazy {
    listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
      .map(::File).firstOrNull { it.isDirectory } ?: error("ریشهٔ کد پیدا نشد (cwd=${File(".").absoluteFile})")
  }

  private fun src(rel: String): String = File(root, rel).takeIf { it.isFile }?.readText() ?: error("$rel پیدا نشد")

  private val sources: Map<String, String> by lazy {
    root.walkTopDown().filter { it.isFile && it.extension == "kt" }
      .associate { it.relativeTo(root).invariantSeparatorsPath to it.readText() }
  }

  @Test fun `هیچ حسابِ هفته‌ای بی قاعدهٔ دکان صدا زده نمی‌شود`() {
    //  `BusinessDay.weekStartOf(day)` ِ تک‌آرگومانی همیشه شنبه و جمعه است؛ هر صفحه قاعدهٔ دکان را می‌دهد
    val bare = Regex("""BusinessDay\.(weekStartOf|dayKind|isWorkingDay)\(\s*[^,()]*\)""")
    val found = sources.flatMap { (path, text) -> bare.findAll(text).map { "$path: ${it.value}" }.toList() }
    assertEquals(emptyList<String>(), found)
  }

  @Test fun `آمار، خانه، نقشهٔ ساعت و روز و روزهای تخفیف هفتهٔ دکان را می‌گیرند`() {
    val stats = src("ir/vil3ntec/tohid/ui/v3/analytics/StatsPage.kt")
    assertTrue(stats.contains("val week = ShopDayChoice.rules"))
    assertTrue(stats.contains("StatsModel.bars(d, period, today, week)"))
    assertTrue(stats.contains("StatsModel.streak(d, today, week)"))
    val hero = src("ir/vil3ntec/tohid/ui/v3/shell/HomeHeroCard.kt")
    assertTrue(hero.contains("val week = ShopDayChoice.rules"))
    assertTrue(hero.contains("HomeHero.sales(d.sales, range, input.today, input.dayStartMs, input.nowMs, letters, week)"))
    assertTrue(hero.contains("HomeHero.safe(safeView.history, range, input.today, input.dayStartMs, input.nowMs, letters, week)"))
    assertTrue(src("ir/vil3ntec/tohid/ui/screens/PromoText.kt").contains("get() = days(ir.vil3ntec.tohid.ShopDayChoice.weekStart)"))
  }

  @Test fun `تنظیمِ نسخهٔ ۳ آغاز و روزهای کاری را می‌نویسد و آخرین روزِ کاری را نگه می‌دارد`() {
    val digits = src("ir/vil3ntec/tohid/ui/v3/settings/sections/DigitsScreen.kt")
    assertTrue(digits.contains("ShopDayChoice.setWeekStart(context, ShopWeek.STARTS[it])"))
    assertTrue(digits.contains("ShopDayChoice.setClosedDays(context, ShopWeek.toggle(closed, day))"))
    assertTrue(digits.contains("ShopWeek.order(ShopDayChoice.weekStart)"))
  }

  @Test fun `هر «دیروز» ِ کادرِ تاریخ همان دیروزِ روزِ کاری است`() {
    //  نه `Calendar` ِ دستگاه: با «پایانِ روزِ کاری» ساعتِ ۱:۳۰ «دیروز» ِ تقویمی همان «امروز» ِ دکان است
    val def = Regex("""fun\s+\w*[yY]esterdayIso\(\)[^\n]*""")
    val defs = sources.filterKeys { it != "ir/vil3ntec/tohid/Jalali.kt" }
      .flatMap { (path, text) -> def.findAll(text).map { path to it.value }.toList() }
    assertTrue("کادرِ تاریخِ نسخهٔ ۳ باید باشد", defs.any { it.first == "ir/vil3ntec/tohid/ui/v3/V3Inputs.kt" })
    for ((path, line) in defs) assertTrue("$path: $line", line.contains("= ir.vil3ntec.tohid.yesterdayIso()"))
  }

  /** LOC-04 — جدولِ تعطیلاتِ سرور به همان قاعدهٔ هفته می‌رسد؛ منطقِ خواندن در `HolidaysTest`/`HolidayVectorsTest` (core) */
  @Test fun `تعطیلاتِ رسمیِ سرور در قاعدهٔ هفته‌اند و روزی یک بار گرفته می‌شوند`() {
    val choice = src("ir/vil3ntec/tohid/ShopDayChoiceAndroid.kt")
    assertTrue(choice.contains("WeekRules(weekStart = weekStart, weekend = closedDays, holidays = holidays)"))
    assertTrue(choice.contains("Holidays.parse(holidayPrefs(context).getString(HOLIDAY_TABLE, null)).dates"))
    //  بی‌توکن و بی شناسهٔ دستگاه؛ پاسخِ بدشکل نسخهٔ ذخیره‌شده را پاک نمی‌کند
    assertTrue(choice.contains("getPublic(ApiEndpoints.Calendar.HOLIDAYS, anonymous = true)"))
    assertTrue(choice.contains("val table = Holidays.of(body) ?: return false"))
    assertTrue(choice.contains("if (!Holidays.due("))
    //  پوستهٔ مشترکِ اندروید و آیفون
    assertTrue(src("ir/vil3ntec/tohid/AppShellAndroid.kt").contains("LaunchedEffect(Unit) { ShopDayChoice.keepHolidaysFresh(context) }"))
    assertEquals("/calendar/holidays", ir.vil3ntec.tohid.core.net.ApiEndpoints.Calendar.HOLIDAYS)
    //  همان درِ سرور (`docs/api/routes.json`)
    val routes = listOf("../../docs/api/routes.json", "../docs/api/routes.json", "docs/api/routes.json").map(::File)
      .firstOrNull { it.isFile } ?: error("docs/api/routes.json پیدا نشد (cwd=${File(".").absoluteFile})")
    assertTrue(routes.readText().contains("\"method\":\"GET\",\"path\":\"/api/calendar/holidays\""))
  }
}

