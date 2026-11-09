package ir.vil3ntec.tohid.core

import ir.vil3ntec.tohid.core.calendar.BusinessDay
import ir.vil3ntec.tohid.core.calendar.CivilDays
import ir.vil3ntec.tohid.core.calendar.LunarHijri
import ir.vil3ntec.tohid.core.calendar.SolarHijri
import ir.vil3ntec.tohid.core.calendar.WeekRules
import ir.vil3ntec.tohid.core.calendar.Ymd
import ir.vil3ntec.tohid.core.money.Currency
import ir.vil3ntec.tohid.core.money.Money
import ir.vil3ntec.tohid.core.money.Qty
import ir.vil3ntec.tohid.core.money.ReturnCap
import ir.vil3ntec.tohid.core.money.ReturnShare
import ir.vil3ntec.tohid.core.money.Rounding
import ir.vil3ntec.tohid.core.promo.PromoCore
import ir.vil3ntec.tohid.core.text.LocaleFormat
import ir.vil3ntec.tohid.core.text.TextNormalizer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.io.File
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertTrue

/*
 *  فازِ تفاضلی — دوندهٔ Kotlin ِ هسته‌های `core/` (پول، سقفِ برگشت، تخفیف، تقویم، متن؛ و دورِ دوم در `CoreFuzzTwins`:
 *  برگهٔ تأیید، مجوز، زنجیرهٔ هش، پرداخت، جمع‌آوری، سفارشِ خرید).
 *  پیکره (`core/test-vectors/core-fuzz-v1.json`) را `server/scripts/core-fuzz.js` با هسته‌های JS می‌سازد:
 *  `out` خروجیِ JS است و این‌جا باید عیناً همان باشد (عدد با مقدار سنجیده می‌شود: 2 = 2.0).
 *  موجودی، گزارش و دفترِ قرض را `CoreFuzzAppTest` (android-native، روی کامپیوتر هم) می‌دواند.
 *  اجرای ژرف: CORE_FUZZ_CORPUS=<پیکرهٔ بزرگ>[:<پیکرهٔ بعدی>…] (سازنده: server/test/core-fuzz.test.js با CORE_FUZZ_OUT)؛
 *  چند پیکره (مثلاً یکی برای هر جفت با CORE_FUZZ_ONLY) پشتِ هم، هر بار یکی در حافظه.
 */
class CoreFuzzTest {

  private val deep: List<File>? = System.getenv("CORE_FUZZ_CORPUS")?.takeIf { it.isNotBlank() }?.split(File.pathSeparator)?.map(::File)

  private fun corpora(): List<File> = deep ?: listOf(
    generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
      .map { File(it, "core/test-vectors/core-fuzz-v1.json") }.firstOrNull { it.isFile }
      ?: error("core/test-vectors/core-fuzz-v1.json پیدا نشد"),
  )

  @Test
  fun `فاز — هر موردِ پول، سقفِ برگشت، تخفیف، تقویم، متن و هم‌زادهای دورِ دوم همان خروجیِ JS`() {
    val dump = System.getenv("CORE_FUZZ_DUMP")?.takeIf { it.isNotBlank() }?.let { File(it).bufferedWriter() }  // برای کمینه‌سازی: خروجیِ Kotlin ِ هر تفاوت
    val failures = corpora().flatMap { file ->
      val cases = Json.parseToJsonElement(file.readText()).jsonObject.getValue("cases").jsonArray.map { it.jsonObject }
      val mine = cases.filter { it.fn().substringBefore('.') in FAMILIES }
      //  پیکرهٔ مخزن هر خانواده را دست‌کم ۵ بار دارد؛ پیکرهٔ ژرفِ یک جفت (CORE_FUZZ_ONLY) شاید هیچ‌کدامِ این‌ها را نداشته باشد
      if (deep == null) {
        val count = mine.groupingBy { it.fn().substringBefore('.') }.eachCount()
        assertTrue(FAMILIES.all { (count[it] ?: 0) >= 5 }, "موردهای هر خانواده: $count")
      }
      mine.mapNotNull { c ->
        val want = c.getValue("out")
        val got = try { run(c.fn(), c.getValue("in").jsonObject) } catch (e: Exception) { THROWS }
        val ok = same(want, got) || CoreFuzzTwins.bothRefuse(c.fn(), want, got)
        if (dump != null && !ok) dump.write("{\"file\":\"${file.name}\",\"id\":${c["id"]},\"got\":$got}\n")
        if (ok) null else c.fn() to "#${c["id"]} ${c.fn()} ${c["in"].toString().take(400)}\n  JS:     $want\n  Kotlin: $got"
      }
    }
    dump?.close()
    val byFn = failures.groupBy({ it.first }, { it.second })
    assertTrue(failures.isEmpty(), "${failures.size} تفاوت ${byFn.mapValues { it.value.size }}\n" + byFn.values.flatMap { it.take(3) }.joinToString("\n"))
  }

  // ── اجرا ─────────────────────────────────────────────

  private fun run(fn: String, x: JsonObject): JsonElement = if (fn.substringBefore('.') in CoreFuzzTwins.FAMILIES) CoreFuzzTwins.run(fn, x) else when (fn) {
    "money.lineTotalMinor" -> n(Rounding.lineTotalMinor(x.l("q"), x.l("p"), x.l("step")))
    "money.roundHalfUp" -> n(Rounding.roundHalfUp(x.l("n"), x.l("d")))
    "money.allocateDiscount" -> {
      val cur = Currency.of("AFN").withStep(x.l("step"))
      JsonArray(Rounding.allocateDiscount(Money(x.l("discount"), cur), x.arr("lines").map { Money(it.jsonPrimitive.long, cur) }).map { n(it.minor) })
    }
    "money.cashRounding" -> Rounding.cashRounding(Money(x.l("total"), Currency.AFN), x.l("step"))?.let { n(it.amount.minor) } ?: JsonNull
    "money.fromLegacy" -> n(Money.fromLegacy(x.d("major"), Currency.AFN).minor)
    "money.qtyFromLegacy" -> n(Qty.fromLegacy(x.d("q")).milli)
    "money.returnShare" -> JsonPrimitive(
      if (x["refundedBefore"] == null) ReturnShare.amount(x.d("lineTotal"), x.d("quantity"), x.d("returnedBefore"), x.d("qty"), x.d("paidRatio"))
      else ReturnShare.amount(
        x.d("lineTotal"), x.d("quantity"), x.d("returnedBefore"), x.d("qty"), x.d("paidRatio"), x.d("refundedBefore"), x.d("refundedQty"),
      ),
    )

    "cap.milli" -> n(ReturnCap.milli(x.d("x")))
    "cap.minor" -> n(ReturnCap.minor(x.d("x")))
    "cap.capMinor" -> n(ReturnCap.capMinor(x.d("lineTotal"), x.d("paidRatio")))
    "cap.check" -> {
      val prior = x.arr("prior").map { p -> p.jsonArray.let { ReturnCap.Row(it[0].jsonPrimitive.long, it[1].jsonPrimitive.long) } }
      val req = x.arr("req").let { ReturnCap.Row(it[0].jsonPrimitive.long, it[1].jsonPrimitive.long) }
      val s = ReturnCap.state(x.l("sold"), x.l("cap"), prior, x.l("counter"))
      val v = ReturnCap.check(x.l("sold"), x.l("cap"), prior, req, x.l("tol"), x.l("counter"), x.l("extra"))
      obj(
        "returnedMilli" to n(s.returnedMilli), "returnedMinor" to n(s.returnedMinor), "leftMilli" to n(s.leftMilli),
        "leftMinor" to n(s.leftMinor), "verdict" to JsonPrimitive(v.name.lowercase()),
      )
    }

    "promo.evaluate" -> {
      val r = PromoCore.evaluate(PromoCore.cartOf(plain(x["cart"])), (plain(x["rules"]) as List<*>).map { PromoCore.ruleOf(it) })
      obj(
        "subtotalMinor" to n(r.subtotalMinor), "discountMinor" to n(r.discountMinor), "totalMinor" to n(r.totalMinor),
        "lines" to JsonArray(r.lines.map { obj("key" to s(it.key), "amountMinor" to n(it.amountMinor), "discountMinor" to n(it.discountMinor), "netMinor" to n(it.netMinor)) }),
        "applied" to JsonArray(r.applied.map { obj("id" to s(it.id), "name" to s(it.name), "discountMinor" to n(it.discountMinor)) }),
        "codes" to JsonArray(r.codes.map { obj("code" to s(it.code), "status" to s(it.status)) }),
      )
    }
    "promo.validate" -> {
      val step = (x["step"] as? JsonPrimitive)?.content?.toLongOrNull() ?: 100L
      JsonArray(PromoCore.validate(PromoCore.ruleOf(plain(x["rule"])), if (step > 0) step else 100L).map { s(it) })
    }
    "promo.inWindow" -> {
      val at = x["at"] as? JsonObject
      JsonPrimitive(
        PromoCore.inWindow(
          PromoCore.windowOf(plain(x["window"])),
          at?.let { PromoCore.At(it.str("date") ?: "", (it["minute"] as? JsonPrimitive)?.content?.toLongOrNull() ?: -1) },
        ),
      )
    }
    "promo.isoDow" -> n(PromoCore.isoDow(x.str("date")!!).toLong())
    "promo.normalizeCode" -> s(PromoCore.normalizeCode(x.str("s")!!))

    "cal.toSolar" -> obj(
      "solar" to ymd(SolarHijri.toSolar(x.str("iso")!!)),
      "quarter" to (SolarHijri.fiscalQuarterOf(x.str("iso")!!)?.let { obj("y" to n(it.first.toLong()), "q" to n(it.second.toLong())) } ?: JsonNull),
      "jdn" to (CivilDays.jdnOfIso(x.str("iso")!!)?.let { n(it.toLong()) } ?: JsonNull),
      "weekday" to (CivilDays.isoWeekday(x.str("iso")!!)?.let { n(it.toLong()) } ?: JsonNull),
    )
    "cal.fromSolar" -> obj(
      "iso" to (SolarHijri.fromSolar(x.i("y"), x.i("m"), x.i("d"))?.let(::s) ?: JsonNull),
      "monthLength" to n(SolarHijri.monthLength(x.i("y"), x.i("m")).toLong()),
    )
    "cal.solarYear" -> obj(
      "leap" to JsonPrimitive(SolarHijri.isLeap(x.i("y"))),
      "year" to range(SolarHijri.fiscalYearRange(x.i("y"))),
      "quarter" to range(SolarHijri.fiscalQuarterRange(x.i("y"), x.i("q"))),
    )
    "cal.days" -> CivilDays.addDays(x.str("iso")!!, x.i("n"))?.let(::s) ?: JsonNull
    "cal.jdn" -> s(CivilDays.isoOf(x.i("jdn")))
    "cal.lunar" -> ymd(LunarHijri.toLunar(x.str("iso")!!, x.i("adjust")))
    "cal.format" -> obj(
      "numeric" to s(SolarHijri.formatNumeric(x.str("iso")!!, x.str("digits")!!)),
      "long" to s(SolarHijri.formatLong(x.str("iso")!!, x.str("lang")!!, x.str("digits")!!)),
      "lunar" to s(LunarHijri.formatLong(x.str("iso")!!, x.str("digits")!!, x.i("adjust"))),
    )
    "cal.week" -> {
      val r = x["rules"] as? JsonObject
      val rules = WeekRules(
        weekStart = (r?.get("weekStart") as? JsonPrimitive)?.int ?: 6,
        weekend = (r?.get("weekend") as? JsonArray)?.map { it.jsonPrimitive.int }?.toSet() ?: setOf(5),
        holidays = (r?.get("holidays") as? JsonArray)?.map { it.jsonPrimitive.content }?.toSet() ?: emptySet(),
      )
      val iso = x.str("iso")!!
      obj(
        "weekStart" to (BusinessDay.weekStartOf(iso, rules)?.let(::s) ?: JsonNull),
        "kind" to (BusinessDay.dayKind(iso, rules)?.let { s(it.code) } ?: JsonNull),
        "working" to JsonPrimitive(BusinessDay.isWorkingDay(iso, rules)),
      )
    }
    "cal.businessDate" -> BusinessDay.businessDate(x.str("local")!!, x.i("start"))?.let(::s) ?: JsonNull
    "cal.businessDateAt" -> s(BusinessDay.businessDateAt(x.l("ms"), x.i("off"), x.i("start")))

    "text.key" -> obj(
      "search" to s(TextNormalizer.searchKey(x.str("s")!!)), "compare" to s(TextNormalizer.compareKey(x.str("s")!!)),
      "ascii" to s(LocaleFormat.asciiDigits(x.str("s")!!)),
    )
    "text.match" -> {
      val syn = (x["synonyms"] as? JsonArray)?.map { g -> g.jsonArray.map { it.jsonPrimitive.content } }
      val t = x.str("text")!!
      val q = x.str("query")!!
      obj(
        "matches" to JsonPrimitive(TextNormalizer.matches(t, q, syn)), "fuzzy" to JsonPrimitive(TextNormalizer.fuzzyMatches(t, q)),
        "distance" to n(TextNormalizer.editDistance(t, q, x.i("max")).toLong()), "budget" to n(TextNormalizer.typoBudget(q.length).toLong()),
      )
    }
    "text.digits" -> s(LocaleFormat.displayDigits(x.str("s")!!, x.str("style")!!))
    "text.afghani" -> s(LocaleFormat.formatAfghani(x.d("amount"), x.str("symbol")!!, x.str("digits")!!))
    "text.phone" -> {
      val raw = x.str("s")!!
      val nn = LocaleFormat.normalizePhone(raw)
      obj(
        "normalized" to (nn?.let(::s) ?: JsonNull), "formatted" to s(LocaleFormat.formatPhone(nn ?: raw)),
        "raw" to s(LocaleFormat.formatPhone(raw)),
      )
    }
    else -> error("تابعِ ناشناخته: $fn")
  }

  // ── کمک‌ها ───────────────────────────────────────────

  private fun JsonObject.fn() = getValue("fn").jsonPrimitive.content
  private fun JsonObject.l(k: String): Long = getValue(k).jsonPrimitive.content.toLong()
  private fun JsonObject.i(k: String): Int = getValue(k).jsonPrimitive.content.toInt()
  private fun JsonObject.arr(k: String): JsonArray = getValue(k).jsonArray
  private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content

  /** همان D ِ مولّد: رشته‌های NaN/Infinity/-0 */
  private fun JsonObject.d(k: String): Double {
    val p = getValue(k).jsonPrimitive
    return when (p.content) {
      "NaN" -> Double.NaN
      "Infinity" -> Double.POSITIVE_INFINITY
      "-Infinity" -> Double.NEGATIVE_INFINITY
      "-0" -> -0.0
      else -> p.content.toDouble()
    }
  }

  private fun n(v: Long) = JsonPrimitive(v)
  private fun s(v: String) = JsonPrimitive(v)
  private fun obj(vararg kv: Pair<String, JsonElement>) = JsonObject(linkedMapOf(*kv))
  private fun ymd(y: Ymd?): JsonElement = y?.let { obj("y" to n(it.year.toLong()), "m" to n(it.month.toLong()), "d" to n(it.day.toLong())) } ?: JsonNull
  private fun range(p: Pair<String, String>?): JsonElement = p?.let { obj("from" to s(it.first), "to" to s(it.second)) } ?: JsonNull

  /** JSON ⇒ درختِ Map/List/String/Long/Double/Boolean برای `PromoCore.ruleOf`/`cartOf` (همان چیزی که JSON.parse به JS می‌دهد) */
  private fun plain(e: JsonElement?): Any? = when (e) {
    null, JsonNull -> null
    is JsonObject -> e.mapValues { plain(it.value) }
    is JsonArray -> e.map { plain(it) }
    is JsonPrimitive -> when {
      e.isString -> e.content
      e.booleanOrNull != null -> e.booleanOrNull
      e.content.any { it == '.' || it == 'e' || it == 'E' } -> e.content.toDouble()
      else -> e.content.toLongOrNull() ?: e.content.toDouble()
    }
  }

  companion object {
    private val FAMILIES = setOf("money", "cap", "promo", "cal", "text") + CoreFuzzTwins.FAMILIES
    private val THROWS = JsonObject(mapOf("throws" to JsonPrimitive(true)))

    /**
     *  برابریِ JSON؛ عدد با مقدار، نه متن (2 = 2.0). درونِ ±۲^۵۳ دقیق (BigDecimal)؛ بیرون از آن عددِ JS
     *  خودش Double است و JSON کوتاه‌ترین متنِ همان Double را می‌نویسد، پس Long ِ Kotlin هم با همان گردِ Double سنجیده می‌شود.
     */
    fun same(a: JsonElement, b: JsonElement): Boolean = when {
      a is JsonObject && b is JsonObject -> a.keys == b.keys && a.keys.all { same(a.getValue(it), b.getValue(it)) }
      a is JsonArray && b is JsonArray -> a.size == b.size && a.indices.all { same(a[it], b[it]) }
      a is JsonNull || b is JsonNull -> a is JsonNull && b is JsonNull
      a is JsonPrimitive && b is JsonPrimitive && !a.isString && !b.isString && a.booleanOrNull == null && b.booleanOrNull == null ->
        sameNumber(a.content, b.content)
      a is JsonPrimitive && b is JsonPrimitive -> a.isString == b.isString && a.content == b.content
      else -> false
    }

    private val SAFE = BigDecimal("9007199254740991")

    private fun sameNumber(a: String, b: String): Boolean {
      val x = a.toBigDecimalOrNull() ?: return a == b
      val y = b.toBigDecimalOrNull() ?: return false
      return if (x.abs() <= SAFE && y.abs() <= SAFE) x.compareTo(y) == 0 else x.toDouble() == y.toDouble()
    }
  }
}
