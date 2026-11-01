package ir.vil3ntec.tohid.core

import ir.vil3ntec.tohid.Jalali
import ir.vil3ntec.tohid.core.calendar.BusinessDay
import ir.vil3ntec.tohid.core.calendar.CivilDays
import ir.vil3ntec.tohid.core.calendar.LunarHijri
import ir.vil3ntec.tohid.core.calendar.SolarHijri
import ir.vil3ntec.tohid.core.calendar.WeekRules
import ir.vil3ntec.tohid.core.calendar.Ymd
import ir.vil3ntec.tohid.core.text.LocaleFormat
import ir.vil3ntec.tohid.core.text.TextNormalizer
import ir.vil3ntec.tohid.latinDigits
import ir.vil3ntec.tohid.money
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 *  آزمون‌های مرجعِ مشترک (بخشِ ۳.۱۵): همان فایل‌های پوشهٔ `core/test-vectors` که
 *  `server/test/locale-vectors.test.js` روی `license/text-core.js` و `license/calendar-core.js` ِ وب می‌دواند،
 *  این‌جا روی `core` ِ کاتلین — و برای اثباتِ برابری، روی کدِ قدیمیِ
 *  `Jalali`، `latinDigits` و `money` هم.
 */
class CoreVectorsTest {

  private fun vectors(name: String): JsonObject {
    var dir: File? = File(System.getProperty("user.dir")).absoluteFile
    while (dir != null) {
      val f = File(dir, "core/test-vectors/$name")
      if (f.isFile) return Json.parseToJsonElement(f.readText()).jsonObject
      dir = dir.parentFile
    }
    error("core/test-vectors/$name پیدا نشد")
  }

  private val text = vectors("text-normalize.json")
  private val loc = vectors("locale-format.json")
  private val cal = vectors("calendar.json")

  private fun JsonObject.list(key: String) = getValue(key).jsonArray.map { it.jsonObject }
  private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content
  private fun JsonObject.strOrNull(key: String) = getValue(key).let { if (it is JsonNull) null else it.jsonPrimitive.content }
  private fun JsonObject.num(key: String) = getValue(key).jsonPrimitive.int
  private fun JsonElement.ints() = jsonArray.map { it.jsonPrimitive.int }
  private fun Ymd?.ints() = this?.let { listOf(year, month, day) }

  private fun rules(o: JsonObject): WeekRules {
    val r = o["rules"]
    if (r == null || r is JsonNull) return WeekRules()
    val m = r.jsonObject
    val d = WeekRules()
    return WeekRules(
      weekStart = m["weekStart"]?.jsonPrimitive?.int ?: d.weekStart,
      weekend = m["weekend"]?.ints()?.toSet() ?: d.weekend,
      holidays = m["holidays"]?.jsonArray?.map { it.jsonPrimitive.content }?.toSet() ?: d.holidays,
    )
  }

  @Test
  fun `کلید جست و جو و کلید مقایسه`() {
    for (v in text.list("searchKey")) assertEquals("$v", v.str("out"), TextNormalizer.searchKey(v.str("in")))
    for (v in text.list("compareKey")) assertEquals("$v", v.str("out"), TextNormalizer.compareKey(v.str("in")))
  }

  @Test
  fun `جورشدن مترادف و غلط تایپی`() {
    for (v in text.list("matches")) {
      val syn = (v["synonyms"] as? JsonArray)?.map { g -> g.jsonArray.map { it.jsonPrimitive.content } }
      assertEquals("$v", v.getValue("out").jsonPrimitive.boolean, TextNormalizer.matches(v.str("text"), v.str("query"), syn))
    }
    for (v in text.list("editDistance")) assertEquals("$v", v.num("out"), TextNormalizer.editDistance(v.str("a"), v.str("b"), v.num("max")))
    for (v in text.list("typoBudget")) assertEquals("$v", v.num("out"), TextNormalizer.typoBudget(v.num("len")))
    for (v in text.list("fuzzyMatches")) {
      assertEquals("$v", v.getValue("out").jsonPrimitive.boolean, TextNormalizer.fuzzyMatches(v.str("text"), v.str("query")))
    }
  }

  @Test
  fun `ارقام افغانی و تلفن`() {
    for (v in loc.list("asciiDigits")) {
      assertEquals("$v", v.str("out"), LocaleFormat.asciiDigits(v.str("in")))
      assertEquals("legacy $v", v.str("legacyLatinDigits"), latinDigits(v.str("in")))
    }
    for (v in loc.list("displayDigits")) assertEquals("$v", v.str("out"), LocaleFormat.displayDigits(v.str("in"), v.str("style")))
    for (v in loc.list("formatAfghani")) {
      val amount = v.getValue("amount").jsonPrimitive.double
      assertEquals("$v", v.str("out"), LocaleFormat.formatAfghani(amount, v.str("symbol"), v.str("digits")))
      //  بی نماد و لاتین = عیناً همان money() ِ امروز
      assertEquals("legacy $v", money(amount), LocaleFormat.formatAfghani(amount, "none", "latn"))
    }
    for (v in loc.list("normalizePhone")) assertEquals("$v", v.strOrNull("out"), LocaleFormat.normalizePhone(v.str("in")))
    for (v in loc.list("formatPhone")) assertEquals("$v", v.str("out"), LocaleFormat.formatPhone(v.str("in")))
  }

  @Test
  fun `خورشیدی تاریخ های شناخته شده کبیسه نام ماه ها و قالب`() {
    for (v in cal.list("solar")) {
      val iso = v.str("iso")
      val want = v.getValue("solar").ints()
      assertEquals(iso, want, SolarHijri.toSolar(iso).ints())
      assertEquals(iso, iso, SolarHijri.fromSolar(want[0], want[1], want[2]))
      //  کدِ قدیمی همان را می‌گوید
      assertEquals(iso, want, Jalali.ofIso(iso)?.let { listOf(it.year, it.month, it.day) })
      assertEquals(iso, iso, Jalali.toIso(want[0], want[1], want[2]))
    }
    for (e in cal.getValue("invalidIso").jsonArray) assertEquals("$e", null, SolarHijri.toSolar(e.jsonPrimitive.content))
    for (e in cal.getValue("fromSolarInvalid").jsonArray) {
      val s = e.ints()
      assertEquals("$s", null, SolarHijri.fromSolar(s[0], s[1], s[2]))
      assertEquals("legacy $s", null, Jalali.toIso(s[0], s[1], s[2]))
    }
    for (v in cal.list("fromSolarExtra")) {
      val s = v.getValue("solar").ints()
      assertEquals("$v", v.str("iso"), SolarHijri.fromSolar(s[0], s[1], s[2]))
    }
    assertEquals(cal.getValue("leapYears1395to1415").ints(), (1395..1415).filter(SolarHijri::isLeap))
    for (v in cal.list("monthLength")) assertEquals("$v", v.num("out"), SolarHijri.monthLength(v.num("y"), v.num("m")))
    val names = cal.getValue("monthNames").jsonObject
    for (m in 1..12) {
      assertEquals(names.getValue("fa").jsonArray[m - 1].jsonPrimitive.content, SolarHijri.monthName(m, "fa"))
      assertEquals(names.getValue("ps").jsonArray[m - 1].jsonPrimitive.content, SolarHijri.monthName(m, "ps"))
    }
    for (v in cal.list("formatSolarNumeric")) assertEquals("$v", v.str("out"), SolarHijri.formatNumeric(v.str("iso"), v.str("digits")))
    for (v in cal.list("formatSolarLong")) {
      assertEquals("$v", v.str("out"), SolarHijri.formatLong(v.str("iso"), v.str("lang"), v.str("digits")))
    }
    for (v in cal.list("fiscalYear")) assertEquals("$v", v.str("from") to v.str("to"), SolarHijri.fiscalYearRange(v.num("y")))
  }

  @Test
  fun `LOC-02 سه‌ماههٔ مالی و سه‌ماههٔ یک روز`() {
    for (v in cal.list("fiscalQuarter")) {
      val want = v.strOrNull("from")?.let { it to v.str("to") }
      assertEquals("$v", want, SolarHijri.fiscalQuarterRange(v.num("y"), v.num("q")))
    }
    for (v in cal.list("fiscalQuarterOf")) {
      val y = v.getValue("y").let { if (it is JsonNull) null else it.jsonPrimitive.int }
      assertEquals("$v", y?.let { it to v.num("q") }, SolarHijri.fiscalQuarterOf(v.str("iso")))
    }
    //  چهار سه‌ماهه بی شکاف و بی هم‌پوشانی همان سالِ مالی‌اند (۱۳۰۰ تا ۱۵۰۰) — همان سنجشِ JS
    for (y in 1300..1500) {
      val year = SolarHijri.fiscalYearRange(y)!!
      val qs = (1..4).map { SolarHijri.fiscalQuarterRange(y, it)!! }
      assertEquals("$y", year.first, qs[0].first)
      assertEquals("$y", year.second, qs[3].second)
      for (q in 1..3) assertEquals("$y/$q", qs[q].first, CivilDays.addDays(qs[q - 1].second, 1))
      for (q in 1..4) {
        assertEquals(y to q, SolarHijri.fiscalQuarterOf(qs[q - 1].first))
        assertEquals(y to q, SolarHijri.fiscalQuarterOf(qs[q - 1].second))
      }
    }
  }

  @Test
  fun `قمری روز هفته و روز کاری`() {
    for (v in cal.list("lunar")) {
      assertEquals("$v", v.getValue("lunar").ints(), LunarHijri.toLunar(v.str("iso"), v.num("adjust")).ints())
    }
    for (v in cal.list("formatLunarLong")) {
      assertEquals("$v", v.str("out"), LunarHijri.formatLong(v.str("iso"), v.str("digits"), v.num("adjust")))
    }
    for (v in cal.list("weekday")) assertEquals("$v", v.num("out"), CivilDays.isoWeekday(v.str("iso")))
    for (v in cal.list("addDays")) assertEquals("$v", v.str("out"), CivilDays.addDays(v.str("iso"), v.num("n")))
    for (v in cal.list("weekStart")) assertEquals("$v", v.str("out"), BusinessDay.weekStartOf(v.str("iso"), rules(v)))
    for (v in cal.list("dayKind")) assertEquals("$v", v.strOrNull("out"), BusinessDay.dayKind(v.str("iso"), rules(v))?.code)
    for (v in cal.list("businessDate")) {
      assertEquals("$v", v.strOrNull("out"), BusinessDay.businessDate(v.str("local"), v.num("dayStart")))
    }
    for (v in cal.list("businessDateAt")) {
      val at = v.getValue("epochMs").jsonPrimitive.long
      assertEquals("$v", v.str("out"), BusinessDay.businessDateAt(at, v.num("offset"), v.num("dayStart")))
    }
  }

  /** سال‌های ۱۳۰۰ تا ۱۵۰۰ (بخشِ ۳.۱۴): هر روز رفت‌وبرگشت، پیوسته، و برابر با `Jalali` ِ قدیمی */
  @Test
  fun `خورشیدی ۱۳۰۰ تا ۱۵۰۰ هر روز`() {
    val first = CivilDays.jdnOfIso(SolarHijri.fromSolar(1300, 1, 1)!!)!!
    val last = CivilDays.jdnOfIso(SolarHijri.fromSolar(1500, 12, SolarHijri.monthLength(1500, 12))!!)!!
    var prev: Ymd? = null
    for (jdn in first..last) {
      val iso = CivilDays.isoOf(jdn)
      val s = SolarHijri.toSolar(iso)!!
      assertEquals(iso, iso, SolarHijri.fromSolar(s.year, s.month, s.day))
      val g = CivilDays.gregorianOf(jdn)
      val old = Jalali.of(g.year, g.month, g.day)
      assertEquals(iso, listOf(old.year, old.month, old.day), s.ints())
      prev?.let { p ->
        val next = when {
          p.day < SolarHijri.monthLength(p.year, p.month) -> Ymd(p.year, p.month, p.day + 1)
          p.month < 12 -> Ymd(p.year, p.month + 1, 1)
          else -> Ymd(p.year + 1, 1, 1)
        }
        assertEquals(iso, next, s)
      }
      prev = s
    }
    assertEquals(73414, last - first + 1)
  }
}
