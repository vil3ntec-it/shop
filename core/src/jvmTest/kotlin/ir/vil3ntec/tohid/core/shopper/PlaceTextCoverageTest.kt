package ir.vil3ntec.tohid.core.shopper

import java.lang.reflect.Method
import java.lang.reflect.Modifier
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 *  همهٔ نوشته‌های [PlaceText] (نقشه، دفترِ نشانیِ حساب، عکسِ دروازه/یادداشتِ صوتی) با بازتاب — همان سنجهٔ
 *  `ShopperTextCoverageTest`: هر سه زبان پُر، انگلیسی بی حرفِ فارسی، و مقدارِ جای خالی در دری از لایهٔ واژه نمی‌گذرد.
 */
class PlaceTextCoverageTest {

  @AfterTest fun reset() {
    ShopperText.lang = ShopperLang.FA
    ShopperText.audit = null
  }

  private fun texts(): List<Method> = PlaceText::class.java.declaredMethods.filter {
    Modifier.isPublic(it.modifiers) && !it.isSynthetic && it.returnType == String::class.java &&
      it.parameterTypes.all { p -> p in listOf(String::class.java, Integer.TYPE, ShopperError::class.java) }
  }

  private fun call(m: Method, text: String): String = m.invoke(PlaceText, *m.parameterTypes.map {
    when (it) {
      String::class.java -> text
      Integer.TYPE -> 3
      else -> ShopperError(415, "unsupported_media")
    }
  }.toTypedArray()) as String

  @Test fun everyPlaceTextHasFaEnPsAndEnglishHasNoPersianLetters() {
    val methods = texts()
    assertTrue(methods.size > 40, "بازتاب نوشته‌ها را پیدا نکرد: ${methods.size}")
    for (l in ShopperLang.entries) {
      ShopperText.lang = l
      val seen = mutableListOf<Triple<String, String, String>>()
      ShopperText.audit = { fa, en, ps -> seen += Triple(fa, en, ps) }
      for (m in methods) assertTrue(call(m, "Ab").isNotBlank(), "${m.name} در $l خالی است")
      ShopperText.audit = null
      for ((fa, en, ps) in seen) {
        assertTrue(fa.isNotBlank() && en.isNotBlank() && ps.isNotBlank(), "جای خالی: $fa")
        if (l == ShopperLang.EN) assertFalse(en.any { it in '؀'..'ۿ' }, "انگلیسیِ نیمه‌فارسی: $en")
      }
    }
  }

  @Test fun dariNeverRewritesInterpolatedValues() {
    ShopperText.lang = ShopperLang.PRS
    val value = "فروشگاه خیابان"
    val withText = texts().filter { m -> m.parameterTypes.any { it == String::class.java } }
    assertTrue(withText.size >= 3, withText.joinToString { it.name })
    for (m in withText) {
      val out = call(m, value)
      assertTrue(value in out, "${m.name}: مقدار در دری عوض شد ⇒ asIs(…) نیست — $out")
    }
  }
}
