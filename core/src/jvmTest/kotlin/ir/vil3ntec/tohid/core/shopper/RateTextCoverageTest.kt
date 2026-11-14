package ir.vil3ntec.tohid.core.shopper

import ir.vil3ntec.tohid.core.rate.RateTarget
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 *  RATE-01 — نوشته‌های [RateText] ِ برنامهٔ خرید: هر سه زبان پُر، انگلیسی بی حرفِ فارسی، و در هیچ زبانی (دری هم) شمارِ ستاره،
 *  پرسش (دروازه‌گذاری) یا جایزه نیست — قاعدهٔ فروشگاه‌ها.
 */
class RateTextCoverageTest {

  @AfterTest fun reset() {
    ShopperText.lang = ShopperLang.FA
    ShopperText.audit = null
  }

  private fun all(): List<String> = listOf(RateText.title, RateText.bodyPlay, RateText.bodyAppStore, RateText.rate, RateText.later, RateText.never)

  @Test fun everyRateTextHasFaEnPsAndNoStarCountQuestionOrReward() {
    for (l in ShopperLang.entries) {
      ShopperText.lang = l
      val seen = mutableListOf<Triple<String, String, String>>()
      ShopperText.audit = { fa, en, ps -> seen += Triple(fa, en, ps) }
      val out = all()
      ShopperText.audit = null
      assertEquals(6, seen.size)
      for ((fa, en, ps) in seen) {
        assertTrue(fa.isNotBlank() && en.isNotBlank() && ps.isNotBlank(), "جای خالی: $fa")
        assertFalse(en.any { it in '؀'..'ۿ' }, "انگلیسیِ نیمه‌فارسی: $en")
      }
      for (s in out) {
        assertTrue(s.isNotBlank(), "$l خالی")
        assertFalse(Regex("[0-9۰-۹٠-٩]|پنج|five", RegexOption.IGNORE_CASE).containsMatchIn(s), "$l: شمارِ ستاره — $s")
        assertFalse(s.contains('?') || s.contains('؟'), "$l: پرسش — $s")
        assertFalse(Regex("جایزه|هدیه|تخفیف|reward|gift|discount|bonus", RegexOption.IGNORE_CASE).containsMatchIn(s), "$l: جایزه — $s")
      }
    }
  }

  @Test fun bodyNamesTheStoreOfThisBuild() {
    ShopperText.lang = ShopperLang.FA
    assertTrue("گوگل‌پلی" in RateText.body(RateTarget.PLAY))
    assertTrue("اپ‌استور" in RateText.body(RateTarget.APP_STORE))
  }
}
