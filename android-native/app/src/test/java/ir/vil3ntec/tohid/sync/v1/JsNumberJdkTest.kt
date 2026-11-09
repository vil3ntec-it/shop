package ir.vil3ntec.tohid.sync.v1

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonUnquotedLiteral
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

/**
 *  عددِ روی خط به نسخهٔ جاوا بسته نیست (DATA-SAFETY §۸). `Double.toString` ِ جاوا ۱۷ و اندروید (ART) گاهی رقمِ اضافه
 *  می‌نویسد (`5.6843418860808015E-14`، `0.0020`)؛ سرور همان عدد را با `String(x)` ِ جاوااسکریپت می‌نویسد، پس opِ آن
 *  دستگاه `hash_mismatch` می‌گرفت و هرگز نمی‌رسید. CI ِ جاوا ۱۷ همین را با `LedgerSyncCoverageTest` سرخ کرد، در حالی
 *  که جاوا ۲۱ سبز بود. این‌جا متن‌های جاوا ۱۷ مستقیم داده می‌شوند، پس آزمون روی هر جاوایی همان را می‌سنجد.
 */
@OptIn(ExperimentalSerializationApi::class)
class JsNumberJdkTest {

  private fun wire(javaText: String) = JsNumber.of(JsonUnquotedLiteral(javaText)).content

  @Test fun `متنِ جاوا ۱۷ همان متنِ جاوااسکریپت می‌شود`() {
    assertEquals("5.684341886080802e-14", wire("5.6843418860808015E-14"))
    assertEquals("-5.684341886080802e-14", wire("-5.6843418860808015E-14"))
    assertEquals("0.002", wire("0.0020"))
    assertEquals("282879384806159000", wire("2.82879384806159008E17"))
    assertEquals("0.30000000000000004", wire("0.30000000000000004"))
    assertEquals("9.689999999999998", wire("9.689999999999998"))
    assertEquals("12345678.5", wire("1.23456785E7"))
    assertEquals("2.5", wire("2.5"))
  }

  @Test fun `کوتاه‌ترین رقم‌ها همان مقدار را برمی‌گردانند و روی جاوا ۱۹ به بعد همان toString اند`() {
    val r = Random(20261109)
    //  از جاوا ۱۹ `Double.toString` خودش کوتاه‌ترین است (JDK-4511638) ⇒ داورِ مستقل
    val modern = Runtime.version().feature() >= 19
    var checked = 0
    repeat(20_000) {
      val d = when (it % 4) {
        0 -> Double.fromBits(r.nextLong())
        1 -> r.nextDouble() * 1e7
        2 -> r.nextLong(1, 10_000_000) / 100.0 * 1.1
        else -> r.nextDouble() * Math.pow(10.0, r.nextInt(-30, 30).toDouble())
      }
      if (!d.isFinite() || d == 0.0) return@repeat
      val s = JsNumber.shortest(d)
      assertEquals(s, d, s.toDouble(), 0.0)
      if (modern) assertEquals(d.toString(), JsNumber.text(d.toString()), JsNumber.text(s))
      checked++
    }
    assertEquals(true, checked > 15_000)
  }

  @Test fun `Double ِ محاسبه‌شده همان متنِ کوتاه‌ترین را می‌گیرد`() {
    for (d in listOf(0.1 + 0.2, 1.0 / 3, 2.0e-3, 5.684341886080802E-14, 1.0E21, 123456789.12345679, -0.0004)) {
      assertEquals(d.toString(), JsNumber.text(JsNumber.shortest(d)), JsNumber.of(JsonPrimitive(d)).content)
    }
  }
}
