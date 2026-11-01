package ir.vil3ntec.tohid.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/*
 *  `CoreVectorsCommonTest` (commonTest؛ روی JVM و شبیه‌سازِ iOS) سطرهای بردارهای مرجع را
 *  در خودِ پرونده نوشته است، نه از JSON می‌خواند. این آزمون (فقط JVM) هر جدولِ همان پرونده
 *  را از متنش بیرون می‌کشد و با JSON ِ امروزِ `core/test-vectors` و `license/test-vectors`
 *  سطربه‌سطر می‌سنجد. سطرِ افزوده، برداشته یا عوض‌شده در JSON — یا بخشِ تازه‌ای که در
 *  `sections` نیامده — همین‌جا می‌شکند تا آزمونِ iOS بی‌صدا کهنه نماند.
 *  درمان: CoreVectorsCommonTest را با همان JSON هم‌خوان کن، نه این آزمون را.
 */
class CoreVectorsCommonParityTest {

  private val root: File = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
    .firstOrNull { File(it, "core/test-vectors").isDirectory && File(it, "license/test-vectors").isDirectory }
    ?: error("ریشهٔ مخزن (core/test-vectors و license/test-vectors) پیدا نشد")

  private val source: String =
    File(root, "core/src/commonTest/kotlin/ir/vil3ntec/tohid/core/CoreVectorsCommonTest.kt").readText().replace("\r\n", "\n")

  private fun json(path: String): JsonObject = Json.parseToJsonElement(File(root, path).readText()).jsonObject

  /** بدنهٔ `@Test fun name()` ِ CoreVectorsCommonTest */
  private fun body(name: String): String =
    Regex("""\n  @Test fun $name\(\) \{\n(.*?)\n  }\n""", RegexOption.DOT_MATCHES_ALL).find(source)?.groupValues?.get(1)
      ?: fail("@Test fun $name() در CoreVectorsCommonTest پیدا نشد")

  private fun rows(body: String, pattern: String): List<List<String>> =
    Regex(pattern, RegexOption.MULTILINE).findAll(body).map { it.groupValues.drop(1) }.toList()

  /** رشتهٔ Kotlin («"…"» با escape) یا `null` ⇒ مقدار */
  private fun kt(literal: String): String? {
    if (literal == "null") return null
    check(literal.length >= 2 && literal.first() == '"' && literal.last() == '"') { "رشتهٔ Kotlin نیست: $literal" }
    val s = literal.substring(1, literal.length - 1)
    val out = StringBuilder()
    var i = 0
    while (i < s.length) {
      val c = s[i++]
      if (c != '\\') {
        out.append(c)
        continue
      }
      when (val e = s[i++]) {
        'u' -> {
          out.append(s.substring(i, i + 4).toInt(16).toChar())
          i += 4
        }
        't' -> out.append('\t')
        'n' -> out.append('\n')
        'r' -> out.append('\r')
        'b' -> out.append('\b')
        '"', '\\', '\'', '$' -> out.append(e)
        else -> fail("escape ِ ناشناخته \\$e در $literal")
      }
    }
    return out.toString()
  }

  private fun longs(text: String): List<Long> = Regex("""($I)L""").findAll(text).map { it.groupValues[1].toLong() }.toList()

  private fun JsonObject.list(key: String) = getValue(key).jsonArray.map { it.jsonObject }
  private fun JsonElement.text(): String? = if (this is JsonNull) null else jsonPrimitive.content
  private fun JsonElement.long(): Long = jsonPrimitive.content.toLong()
  private fun JsonElement.double(): Double = jsonPrimitive.content.toDouble()
  private fun JsonObject.s(key: String): String? = getValue(key).text()
  private fun JsonObject.l(key: String): Long = getValue(key).long()
  private fun JsonObject.d(key: String): Double = getValue(key).double()
  private fun JsonObject.longs(key: String): List<Long> = getValue(key).jsonArray.map { it.long() }

  /** هم‌شمار و هم‌ترتیب؛ اختلاف ⇒ نخستین سطرِ ناهم‌خوان، نه کلِ دو فهرست */
  private fun same(what: String, fromJson: List<List<Any?>>, fromSource: List<List<Any?>>) {
    assertTrue(fromJson.isNotEmpty(), "$what: در JSON سطری نیست")
    val i = (0 until maxOf(fromJson.size, fromSource.size)).firstOrNull { fromJson.getOrNull(it) != fromSource.getOrNull(it) } ?: return
    fail(
      "$what: CoreVectorsCommonTest با JSON هم‌خوان نیست — سطرِ ${i + 1}: JSON ${fromJson.getOrNull(i)}، " +
        "پرونده ${fromSource.getOrNull(i)} (JSON ${fromJson.size} سطر، پرونده ${fromSource.size}).",
    )
  }

  @Test fun money() {
    val m = json("license/test-vectors/money-v1.json")
    val legacy = m.getValue("fromLegacy").jsonObject
    val fromLegacy = body("moneyFromLegacy")
    same(
      "money-v1 fromLegacy.money",
      legacy.getValue("money").jsonArray.map { r -> r.jsonArray.let { listOf(it[0].double(), it[1].long()) } },
      rows(fromLegacy, """assertEquals\(($I)L, Money\.fromLegacy\(($D), Currency\.AFN\)\.minor, $S\)""")
        .map { listOf(it[1].toDouble(), it[0].toLong()) },
    )
    same(
      "money-v1 fromLegacy.qty",
      legacy.getValue("qty").jsonArray.map { r -> r.jsonArray.let { listOf(it[0].double(), it[1].long()) } },
      rows(fromLegacy, """assertEquals\(($I)L, Qty\.fromLegacy\(($D)\)\.milli, $S\)""")
        .map { listOf(it[1].toDouble(), it[0].toLong()) },
    )

    fun table(key: String, vararg columns: String): List<List<Long>> {
      val t = m.getValue(key).jsonObject
      val names = t.getValue("columns").jsonArray.map { it.text() }
      val at = columns.map { c -> names.indexOf(c).also { assertTrue(it >= 0, "money-v1 $key: ستونِ $c نیست ($names)") } }
      return t.getValue("rows").jsonArray.map { r -> r.jsonArray.let { row -> at.map { row[it].long() } } }
    }
    val array = Regex("""\n  private val lineTotals = longArrayOf\(\n(.*?)\n  \)\n""", RegexOption.DOT_MATCHES_ALL).find(source)
      ?.groupValues?.get(1) ?: fail("lineTotals در CoreVectorsCommonTest پیدا نشد")
    val numbers = Regex(I).findAll(array).map { it.value.toLong() }.toList()
    assertEquals(0, numbers.size % 4, "lineTotals: شمارِ عددها مضربِ ۴ نیست")
    same("money-v1 lineTotal", table("lineTotal", "qtyMilli", "priceMinor", "stepMinor", "minor"), numbers.chunked(4))
    same(
      "money-v1 lineTotalOverflow",
      table("lineTotalOverflow", "qtyMilli", "priceMinor", "stepMinor"),
      rows(body("lineTotal"), """assertFailsWith<ArithmeticException> \{ Rounding\.lineTotalMinor\(($I)L, ($I)L, ($I)L\) }""")
        .map { r -> r.map { it.toLong() } },
    )
    same(
      "money-v1 cashRounding",
      table("cashRounding", "totalMinor", "stepMinor", "adjustMinor"),
      rows(
        body("cashRounding"),
        """assertEquals\(($I)L, Rounding\.cashRounding\(Money\(($I)L, Currency\.AFN\), ($I)L\)\?\.amount\?\.minor \?: 0L, $S\)""",
      ).map { listOf(it[1].toLong(), it[2].toLong(), it[0].toLong()) },
    )
    same(
      "money-v1 allocateDiscount",
      m.list("allocateDiscount").map { listOf(it.l("stepMinor"), it.l("discount"), it.longs("lines"), it.longs("shares")) },
      rows(
        body("allocateDiscount"),
        """Currency\.AFN\.withStep\(($I)L\)\.let \{ c -> assertEquals\(listOf<Long>\(([^)]*)\), """ +
          """Rounding\.allocateDiscount\(Money\(($I)L, c\), listOf\(([^\n]*?)\)\)\.map \{ it\.minor }\) }""",
      ).map { listOf(it[0].toLong(), it[2].toLong(), longs(it[3]), longs(it[1])) },
    )
  }

  @Test fun receipt() {
    val r = json("core/test-vectors/receipt-v1.json")
    same(
      "receipt-v1 tags",
      r.list("tags").map { listOf(it.s("shopId"), it.s("tag")) },
      rows(body("receiptTags"), """assertEquals\(($S), ReceiptTokens\.shopTag\(($S)\)\)""").map { listOf(kt(it[1]), kt(it[0])) },
    )
  }

  @Test fun calendar() {
    val c = json("core/test-vectors/calendar.json")
    val b = body("calendar")
    val solar = c.list("solar").map { o -> listOf(o.s("iso")) + o.longs("solar") }
    same(
      "calendar solar ⇒ toSolar",
      solar,
      rows(b, """assertEquals\(Ymd\(($I), ($I), ($I)\), SolarHijri\.toSolar\(($S)\)\)""")
        .map { listOf(kt(it[3]), it[0].toLong(), it[1].toLong(), it[2].toLong()) },
    )
    same(
      "calendar solar ⇒ fromSolar",
      solar,
      rows(b, """assertEquals\(($S), SolarHijri\.fromSolar\(($I), ($I), ($I)\)\)""")
        .map { listOf(kt(it[0]), it[1].toLong(), it[2].toLong(), it[3].toLong()) },
    )
    same(
      "calendar fromSolarInvalid",
      c.getValue("fromSolarInvalid").jsonArray.map { e -> e.jsonArray.map { it.long() } },
      rows(b, """assertEquals\(null, SolarHijri\.fromSolar\(($I), ($I), ($I)\)\)""").map { r -> r.map { it.toLong() } },
    )
    same(
      "calendar invalidIso",
      c.getValue("invalidIso").jsonArray.map { listOf(it.text()) },
      rows(b, """assertEquals\(null, CivilDays\.jdnOfIso\(($S)\)\)""").map { listOf(kt(it[0])) },
    )
    same(
      "calendar lunar",
      c.list("lunar").map { o -> listOf(o.s("iso"), o.l("adjust")) + o.longs("lunar") },
      rows(b, """assertEquals\(Ymd\(($I), ($I), ($I)\), LunarHijri\.toLunar\(($S), ($I)\)\)""")
        .map { listOf(kt(it[3]), it[4].toLong(), it[0].toLong(), it[1].toLong(), it[2].toLong()) },
    )
    same(
      "calendar formatLunarLong",
      c.list("formatLunarLong").map { listOf(it.s("iso"), it.s("digits"), it.l("adjust"), it.s("out")) },
      rows(b, """assertEquals\(($S), LunarHijri\.formatLong\(($S), ($S), ($I)\)\)""")
        .map { listOf(kt(it[1]), kt(it[2]), it[3].toLong(), kt(it[0])) },
    )
    same(
      "calendar formatSolarLong",
      c.list("formatSolarLong").map { listOf(it.s("iso"), it.s("lang"), it.s("digits"), it.s("out")) },
      rows(b, """assertEquals\(($S), SolarHijri\.formatLong\(($S), ($S), ($S)\)\)""")
        .map { listOf(kt(it[1]), kt(it[2]), kt(it[3]), kt(it[0])) },
    )
    same(
      "calendar weekday",
      c.list("weekday").map { listOf(it.s("iso"), it.l("out")) },
      rows(b, """assertEquals\(($I), CivilDays\.isoWeekday\(($S)\)\)""").map { listOf(kt(it[1]), it[0].toLong()) },
    )
    same(
      "calendar addDays",
      c.list("addDays").map { listOf(it.s("iso"), it.l("n"), it.s("out")) },
      rows(b, """assertEquals\(($S), CivilDays\.addDays\(($S), ($I)\)\)""").map { listOf(kt(it[1]), it[2].toLong(), kt(it[0])) },
    )
    same(
      "calendar weekStart",
      c.list("weekStart").map { o ->
        val rules = o.getValue("rules").let { if (it is JsonNull) emptyMap() else it.jsonObject.mapValues { (_, v) -> v.text() } }
        listOf(o.s("iso"), rules, o.s("out"))
      },
      rows(b, """assertEquals\(($S), BusinessDay\.weekStartOf\(($S), WeekRules\(([^)]*)\)\)\)""").map { r ->
        val rules = r[2].split(", ").filter { it.isNotBlank() }.associate { arg ->
          arg.split(" = ", limit = 2).let { (k, v) -> k to v }
        }
        listOf(kt(r[1]), rules, kt(r[0]))
      },
    )
    same(
      "calendar businessDate",
      c.list("businessDate").map { listOf(it.s("local"), it.l("dayStart"), it.s("out")) },
      rows(b, """assertEquals\(($SN), BusinessDay\.businessDate\(($S), ($I)\)\)""").map { listOf(kt(it[1]), it[2].toLong(), kt(it[0])) },
    )
    same(
      "calendar businessDateAt",
      c.list("businessDateAt").map { listOf(it.l("epochMs"), it.l("offset"), it.l("dayStart"), it.s("out")) },
      rows(b, """assertEquals\(($SN), BusinessDay\.businessDateAt\(($I)L, ($I), ($I)\)\)""")
        .map { listOf(it[1].toLong(), it[2].toLong(), it[3].toLong(), kt(it[0])) },
    )
  }

  @Test fun localeFormat() {
    val l = json("core/test-vectors/locale-format.json")
    val b = body("localeFormat")
    same(
      "locale-format asciiDigits",
      l.list("asciiDigits").map { listOf(it.s("in"), it.s("out")) },
      rows(b, """assertEquals\(($S), LocaleFormat\.asciiDigits\(($S)\)\)""").map { listOf(kt(it[1]), kt(it[0])) },
    )
    same(
      "locale-format formatAfghani",
      l.list("formatAfghani").map { listOf(it.d("amount"), it.s("symbol"), it.s("digits"), it.s("out")) },
      rows(b, """assertEquals\(($S), LocaleFormat\.formatAfghani\(($D), ($S), ($S)\)\)""")
        .map { listOf(it[1].toDouble(), kt(it[2]), kt(it[3]), kt(it[0])) },
    )
    same(
      "locale-format normalizePhone",
      l.list("normalizePhone").map { listOf(it.s("in"), it.s("out")) },
      rows(b, """assertEquals\(($SN), LocaleFormat\.normalizePhone\(($S)\)\)""").map { listOf(kt(it[1]), kt(it[0])) },
    )
    same(
      "locale-format formatPhone",
      l.list("formatPhone").map { listOf(it.s("in"), it.s("out")) },
      rows(b, """assertEquals\(($S), LocaleFormat\.formatPhone\(($S)\)\)""").map { listOf(kt(it[1]), kt(it[0])) },
    )
  }

  @Test fun textNormalizer() {
    val t = json("core/test-vectors/text-normalize.json")
    val b = body("textNormalizer")
    for (key in listOf("searchKey", "compareKey")) {
      same(
        "text-normalize $key",
        t.list(key).map { listOf(it.s("in"), it.s("out")) },
        rows(b, """assertEquals\(($S), TextNormalizer\.$key\(($S)\)\)""").map { listOf(kt(it[1]), kt(it[0])) },
      )
    }
    same(
      "text-normalize matches",
      t.list("matches").map { o ->
        val synonyms = o["synonyms"]?.takeUnless { it is JsonNull }?.jsonArray?.map { g -> g.jsonArray.map { it.text() } }
        listOf(o.s("text"), o.s("query"), synonyms, o.s("out"))
      },
      rows(b, """^\s*assertEquals\((true|false), TextNormalizer\.matches\(($S), ($S), (null|listOf\(.*\))\)\)$""").map { r ->
        val synonyms = if (r[3] == "null") {
          null
        } else {
          Regex("""listOf\(($S(?:, $S)*)\)""").findAll(r[3]).map { g -> Regex(S).findAll(g.groupValues[1]).map { kt(it.value) }.toList() }.toList()
        }
        listOf(kt(r[1]), kt(r[2]), synonyms, r[0])
      },
    )
    same(
      "text-normalize editDistance",
      t.list("editDistance").map { listOf(it.s("a"), it.s("b"), it.l("max"), it.l("out")) },
      rows(b, """assertEquals\(($I), TextNormalizer\.editDistance\(($S), ($S), ($I)\)\)""")
        .map { listOf(kt(it[1]), kt(it[2]), it[3].toLong(), it[0].toLong()) },
    )
    same(
      "text-normalize typoBudget",
      t.list("typoBudget").map { listOf(it.l("len"), it.l("out")) },
      rows(b, """assertEquals\(($I), TextNormalizer\.typoBudget\(($I)\)\)""").map { listOf(it[1].toLong(), it[0].toLong()) },
    )
    same(
      "text-normalize fuzzyMatches",
      t.list("fuzzyMatches").map { listOf(it.s("text"), it.s("query"), it.s("out")) },
      rows(b, """assertEquals\((true|false), TextNormalizer\.fuzzyMatches\(($S), ($S)\)\)""").map { listOf(kt(it[1]), kt(it[2]), it[0]) },
    )
  }

  /**
   *  هر بخشِ این پنج JSON یا بالا با CoreVectorsCommonTest سنجیده می‌شود (`mirrored`)، یا
   *  آگاهانه در آن نیست (`notMirrored`، آزمونش فقط روی JVM است). بخشِ تازه ⇒ یکی از این دو.
   */
  @Test fun sections() {
    val sections = mapOf(
      "license/test-vectors/money-v1.json" to Pair(
        setOf("fromLegacy", "lineTotal", "lineTotalOverflow", "cashRounding", "allocateDiscount"),
        setOf("currencies", "sweep"),
      ),
      "core/test-vectors/receipt-v1.json" to Pair(
        setOf("tags"),
        setOf("alphabet", "encode", "normalize", "urls"),
      ),
      "core/test-vectors/calendar.json" to Pair(
        setOf(
          "solar", "invalidIso", "fromSolarInvalid", "lunar", "formatLunarLong", "formatSolarLong",
          "weekday", "addDays", "weekStart", "businessDate", "businessDateAt",
        ),
        setOf(
          "fromSolarExtra", "leapYears1395to1415", "monthLength", "monthNames", "formatSolarNumeric", "fiscalYear", "dayKind",
          //  LOC-02: مثلِ fiscalYear فقط روی JVM (android-native CoreVectorsTest) و JS (locale-vectors.test.js)
          "fiscalQuarter", "fiscalQuarterOf",
        ),
      ),
      "core/test-vectors/locale-format.json" to Pair(
        setOf("asciiDigits", "formatAfghani", "normalizePhone", "formatPhone"),
        setOf("displayDigits"),
      ),
      "core/test-vectors/text-normalize.json" to Pair(
        setOf("searchKey", "compareKey", "matches", "editDistance", "typoBudget", "fuzzyMatches"),
        emptySet(),
      ),
    )
    for ((path, plan) in sections) {
      val (mirrored, notMirrored) = plan
      val keys = json(path).keys.filterNot { it.startsWith("_") || it == "about" || it == "version" }.toSet()
      assertEquals(
        (mirrored + notMirrored).sorted(),
        keys.sorted(),
        "$path: بخش‌های JSON با فهرستِ این آزمون نمی‌خوانند — بخشِ تازه را در CoreVectorsCommonTest بیاور و در mirrored بنویس، " +
          "یا اگر آگاهانه فقط JVM است، در notMirrored.",
      )
    }
  }

  private companion object {
    /** رشتهٔ Kotlin با escape */
    const val S = "\"(?:[^\"\\\\]|\\\\.)*\""
    const val SN = "(?:$S|null)"
    const val I = "-?\\d+"
    const val D = "-?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?"
  }
}
