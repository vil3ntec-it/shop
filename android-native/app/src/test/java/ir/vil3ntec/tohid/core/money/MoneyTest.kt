package ir.vil3ntec.tohid.core.money

import ir.vil3ntec.tohid.data.SalesEngine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 *  پول و مقدار (بخشِ ۳.۴) — همان بردارهای مرجعی که `server/test/money.test.js`
 *  برای `license/money.js` می‌خواند (`license/test-vectors/money-v1.json`).
 *
 *  ستونِ `legacyMinor` خروجیِ `lineTotal`ِ امروزِ وب است؛ این‌جا سنجیده می‌شود
 *  که `SalesEngine.lineTotal`ِ امروزِ اندروید هم دقیقاً همان را می‌دهد، و
 *  `Rounding` همان عددِ قاعده را که `money.js` می‌دهد. هر فرقِ موتورِ امروز با
 *  قاعده در فایل ثبت است؛ فرقِ تازه یعنی تغییرِ رفتارِ بی‌صدا ⇒ آزمون می‌شکند.
 *
 *  همین پوشهٔ آزمون در CIِ اندروید (`app/`) و کامپیوتر (`desktop/`) اجرا می‌شود.
 */
class MoneyTest {

  private val v: JsonObject = run {
    val f = listOf(
      "../../license/test-vectors/money-v1.json",   // android-native/app
      "../license/test-vectors/money-v1.json",      // desktop
      "license/test-vectors/money-v1.json",         // ریشهٔ مخزن
    ).map { File(it) }.firstOrNull { it.isFile }
      ?: error("بردارهای پول پیدا نشد (cwd=${File(".").absoluteFile})")
    Json.parseToJsonElement(f.readText()).jsonObject
  }

  private fun rows(el: JsonElement?): List<JsonArray> = el!!.jsonArray.map { it.jsonArray }
  private fun JsonArray.l(i: Int): Long = this[i].jsonPrimitive.long
  private fun JsonArray.lOrNull(i: Int): Long? = if (this[i] is JsonNull) null else l(i)

  private val afn = Currency.AFN
  private fun afn(minor: Long, step: Long = 100) = Money(minor, afn.withStep(step))

  /** موتورِ امروز، به واحدِ کوچک: قیمت و مقدار همان Doubleهایی‌اند که امروز در داده‌اند */
  private fun legacyMinor(qtyMilli: Long, priceMinor: Long): Long =
    (SalesEngine.lineTotal(priceMinor / 100.0, qtyMilli / 1000.0) * 100).roundToLong()

  private fun isTie(q: Long, p: Long): Boolean = abs((q * p) % 100_000) == 50_000L

  private inline fun <reified T : Throwable> assertThrows(what: String, block: () -> Unit) {
    try { block() } catch (e: Throwable) {
      if (e is T) return
      fail("$what: خطای ${T::class.simpleName} انتظار می‌رفت، آمد ${e::class.simpleName}")
    }
    fail("$what: باید خطا می‌داد")
  }

  @Test
  fun `جدولِ ارزها با بردارها یکی است`() {
    val table = v["currencies"]!!.jsonArray.map {
      val o = it.jsonObject
      Triple(o["code"]!!.jsonPrimitive.content, o["decimals"]!!.jsonPrimitive.int, o["stepMinor"]!!.jsonPrimitive.long)
    }
    assertEquals(table, Currency.TABLE.map { Triple(it.code, it.decimals, it.stepMinor) })
    assertEquals(100L, Currency.of("AFN").minorPerMajor)
    assertEquals(500L, Currency.AFN.withStep(500).stepMinor)
    assertThrows<IllegalArgumentException>("ارزِ ناشناخته") { Currency.of("XYZ") }
  }

  @Test
  fun `مبلغِ ردیف - Rounding روی همهٔ بردارها همان قاعده را می‌دهد`() {
    for (r in rows(v["lineTotal"]!!.jsonObject["rows"])) {
      val (q, p, step) = Triple(r.l(0), r.l(1), r.l(2))
      val minor = r.l(4)
      assertEquals("$q × $p (پله $step)", minor, Rounding.lineTotalMinor(q, p, step))
      assertEquals(minor, Rounding.lineTotal(Qty(q), afn(p, step)).minor)
    }
    //  نمونهٔ مشخصات: ۱٫۵ × ۱۱۵ = ۱۷۲٫۵ ⇒ ۱۷۳ ؛ منفی: −۱۷۲٫۵ ⇒ −۱۷۲ (نیمه به سمتِ +∞)
    assertEquals(17300L, Rounding.lineTotalMinor(1500, 11500, 100))
    assertEquals(-17200L, Rounding.lineTotalMinor(-1500, 11500, 100))
    assertEquals(-17200L, Rounding.lineTotalMinor(1500, -11500, 100))
  }

  @Test
  fun `SalesEngine lineTotalِ امروز = ستونِ legacy؛ فرق فقط در نیمه‌های دقیق`() {
    var diffs = 0
    for (r in rows(v["lineTotal"]!!.jsonObject["rows"])) {
      val old = r.lOrNull(3) ?: continue
      val (q, p) = r.l(0) to r.l(1)
      assertEquals(100L, r.l(2))
      assertEquals("$q × $p", old, legacyMinor(q, p))
      if (old != r.l(4)) {
        diffs++
        assertTrue("فرقِ بی‌نیمه: $q × $p", isTie(q, p))
        assertEquals("قاعده فقط نیمه را بالا می‌برد", 100L, r.l(4) - old)
      }
    }
    assertTrue("بردارها باید نمونهٔ فرق را هم داشته باشند", diffs > 0)
  }

  @Test
  fun `جاروبِ کامل - همهٔ فرق‌های SalesEngine با قاعده همان فهرستِ ثبت‌شده است`() {
    val s = v["sweep"]!!.jsonObject
    val pr = s["priceMinor"]!!.jsonArray.map { it.jsonPrimitive.long }
    val qr = s["qtyMilli"]!!.jsonArray.map { it.jsonPrimitive.long }
    val step = s["stepMinor"]!!.jsonPrimitive.long
    val found = ArrayList<List<Long>>()
    for (p in pr[0]..pr[1]) {
      for (q in qr[0]..qr[1]) {
        val a = legacyMinor(q, p)
        val b = Rounding.lineTotalMinor(q, p, step)
        if (a != b) {
          assertTrue("فرقِ بی‌نیمه: $q × $p", isTie(q, p))
          found += listOf(q, p, a, b)
        }
      }
    }
    assertEquals(rows(s["diffs"]).map { r -> (0..3).map { r.l(it) } }, found)
  }

  @Test
  fun `بیرون از بازهٔ دقیق خطا می‌دهد، نه گردکردنِ بی‌صدا`() {
    for (r in rows(v["lineTotalOverflow"]!!.jsonObject["rows"])) {
      assertThrows<ArithmeticException>("${r.l(0)} × ${r.l(1)}") { Rounding.lineTotalMinor(r.l(0), r.l(1), r.l(2)) }
    }
    assertThrows<ArithmeticException>("۲^۵۳") { Money(MAX_EXACT + 1, afn) }
    assertThrows<ArithmeticException>("جمع") { Money(MAX_EXACT, afn) + Money(1, afn) }
    assertThrows<ArithmeticException>("مقدار") { Qty(-MAX_EXACT - 1) }
  }

  @Test
  fun `مرزِ دادهٔ امروز - fromLegacy همان Math round است`() {
    val fl = v["fromLegacy"]!!.jsonObject
    for (r in rows(fl["money"])) assertEquals("${r[0]}", r.l(1), Money.fromLegacy(r[0].jsonPrimitive.double, afn).minor)
    for (r in rows(fl["qty"])) assertEquals("${r[0]}", r.l(1), Qty.fromLegacy(r[0].jsonPrimitive.double).milli)
    assertThrows<IllegalArgumentException>("NaN") { Money.fromLegacy(Double.NaN, afn) }
    assertThrows<IllegalArgumentException>("∞") { Qty.fromLegacy(Double.POSITIVE_INFINITY) }
    assertThrows<ArithmeticException>("بزرگ") { Money.fromLegacy(1e300, afn) }
  }

  @Test
  fun `تخفیفِ فاکتور - بزرگ‌ترین باقی‌مانده، جمعِ دقیق، مضربِ پله`() {
    for (el in v["allocateDiscount"]!!.jsonArray) {
      val c = el.jsonObject
      val step = c["stepMinor"]!!.jsonPrimitive.long
      val d = c["discount"]!!.jsonPrimitive.long
      val lines = c["lines"]!!.jsonArray.map { it.jsonPrimitive.long }
      val want = c["shares"]!!.jsonArray.map { it.jsonPrimitive.long }
      val got = Rounding.allocateDiscount(afn(d, step), lines.map { afn(it, step) }).map { it.minor }
      assertEquals("$c", want, got)
      assertEquals(d, got.sum())
      got.forEachIndexed { i, s ->
        assertEquals(0L, s % step)
        assertTrue(s in 0..lines[i])
      }
    }
    assertThrows<IllegalArgumentException>("بیش از جمع") { Rounding.allocateDiscount(afn(500), listOf(afn(100), afn(300))) }
    assertThrows<IllegalArgumentException>("مضربِ پله نیست") { Rounding.allocateDiscount(afn(150), listOf(afn(1000))) }
    assertThrows<IllegalArgumentException>("ردیفِ منفی") { Rounding.allocateDiscount(afn(100), listOf(afn(-100), afn(300))) }
    assertThrows<CurrencyMismatchException>("دو ارز") {
      Rounding.allocateDiscount(afn(100), listOf(Money(300, Currency.USD)))
    }
  }

  @Test
  fun `گردکردنِ نقدی - ردیفی جدا با نوعِ rounding`() {
    for (r in rows(v["cashRounding"]!!.jsonObject["rows"])) {
      val (t, step, adjust) = Triple(r.l(0), r.l(1), r.l(2))
      val line = Rounding.cashRounding(afn(t), step)
      if (adjust == 0L) { assertNull("$t / $step", line); continue }
      assertEquals("rounding", line!!.type)
      assertEquals(Rounding.ROUNDING_LINE_TYPE, line.type)
      assertEquals("$t / $step", adjust, line.amount.minor)
      assertEquals(0L, (t + adjust) % step)
      assertTrue(abs(adjust) * 2 <= step)
    }
    //  ۱۷۲٫۵ افغانی به پلهٔ ۵ ⇒ ۱۷۵ (ردیفِ +۲٫۵)
    assertEquals(250L, Rounding.cashRounding(afn(17250), 500)!!.amount.minor)
  }

  @Test
  fun `دو ارزِ متفاوت خطای زمانِ اجراست؛ جمع و مقایسه دقیق‌اند`() {
    val a = afn(17300)
    val b = afn(200)
    assertEquals(17500L, (a + b).minor)
    assertEquals(-17100L, (b - a).minor)
    assertEquals(0L, (-afn(0)).minor)
    assertTrue(a > b)
    assertEquals("هویتِ ارز فقط کد است", 0, a.compareTo(afn(17300, 500)))
    val usd = Money(1, Currency.USD)
    assertThrows<CurrencyMismatchException>("جمع") { a + usd }
    assertThrows<CurrencyMismatchException>("تفریق") { a - usd }
    assertThrows<CurrencyMismatchException>("مقایسه") { a.compareTo(usd) }
  }

  @Test
  fun `roundHalfUp - نیمه به سمتِ +∞ برای مثبت و منفی`() {
    assertEquals(3L, Rounding.roundHalfUp(5, 2))     //  ۲٫۵ ⇒ ۳
    assertEquals(-2L, Rounding.roundHalfUp(-5, 2))   // −۲٫۵ ⇒ −۲
    assertEquals(1L, Rounding.roundHalfUp(7, 5))     //  ۱٫۴ ⇒ ۱ (مقسوم‌علیهِ فرد)
    assertEquals(2L, Rounding.roundHalfUp(8, 5))     //  ۱٫۶ ⇒ ۲
    assertEquals(-2L, Rounding.roundHalfUp(-8, 5))   // −۱٫۶ ⇒ −۲
    for (n in -20_000L..20_000L) {
      assertEquals("$n / 1000", Math.round(n / 1000.0), Rounding.roundHalfUp(n, 1000))
    }
    assertThrows<IllegalArgumentException>("صفر") { Rounding.roundHalfUp(1, 0) }
  }
}
