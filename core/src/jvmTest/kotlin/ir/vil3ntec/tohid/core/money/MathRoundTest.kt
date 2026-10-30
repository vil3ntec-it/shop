package ir.vil3ntec.tohid.core.money

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/** `mathRound` (commonMain) بیت‌به‌بیت همان `java.lang.Math.round` است */
class MathRoundTest {

  private fun same(x: Double) = assertEquals(Math.round(x), mathRound(x), "x = $x (${x.toRawBits()})")

  @Test fun edges() {
    val specials = doubleArrayOf(
      0.0, -0.0, 0.5, -0.5, 0.49999999999999994, -0.49999999999999994, 1.5, -1.5, 2.5, -2.5,
      Double.MIN_VALUE, -Double.MIN_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE,
      Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NaN,
      4503599627370496.0, 4503599627370497.0, 9007199254740991.0, 9007199254740993.0,
      9.223372036854775807E18, -9.223372036854775808E18, 1.0E19, -1.0E19, 1.0E-20,
    )
    for (x in specials) { same(x); same(Math.nextUp(x)); same(Math.nextDown(x)) }
    for (k in -2000..2000) { val h = k + 0.5; same(h); same(Math.nextUp(h)); same(Math.nextDown(h)) }
  }

  @Test fun random() {
    val r = Random(20261030)
    repeat(2_000_000) { same(Double.fromBits(r.nextLong())) }
    repeat(2_000_000) { same((r.nextDouble() - 0.5) * 2.0e6) }
    repeat(500_000) { same((r.nextLong(-1_000_000_000, 1_000_000_000) + 0.5) / 100.0 * 100.0) }
  }
}
