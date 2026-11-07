package ir.vil3ntec.tohid.core.shopper

import java.lang.reflect.Method
import java.lang.reflect.Modifier
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 *  همهٔ نوشته‌های [ShopperText] (با بازتاب، تا نوشتهٔ تازه بی آزمون نماند): هر سه زبان پُر، انگلیسی بی حرفِ فارسی،
 *  و در دری هیچ مقدارِ جای خالی (نامِ کالا/دکان) از لایهٔ واژه نمی‌گذرد — همتای `DariWordsTest` ِ برنامهٔ دکان.
 */
class ShopperTextCoverageTest {

  @AfterTest fun reset() {
    ShopperText.lang = ShopperLang.FA
    ShopperText.audit = null
  }

  /** پارامترهایی که کد است، نه مقدارِ نمایشی (وضعیت، حالت، روش) — در آزمونِ دری کنار */
  private val codeParams = setOf("presence", "stock", "step", "status", "payState", "payCashButton")

  private fun texts(): List<Method> = ShopperText::class.java.declaredMethods.filter {
    Modifier.isPublic(it.modifiers) && !it.isSynthetic && it.returnType == String::class.java &&
      it.name !in setOf("t", "asIs", "num", "money", "qty") &&
      it.parameterTypes.all { p -> p in listOf(String::class.java, java.lang.Long.TYPE, Integer.TYPE, CheckoutProblem::class.java, ShopperError::class.java) }
  }

  private fun arg(type: Class<*>, text: String): Any = when (type) {
    String::class.java -> text
    java.lang.Long.TYPE -> 125_050L
    Integer.TYPE -> 3
    CheckoutProblem::class.java -> CheckoutProblem.ADDRESS
    else -> ShopperError(400, "otp_wrong")
  }

  private fun call(m: Method, text: String): String = m.invoke(ShopperText, *m.parameterTypes.map { arg(it, text) }.toTypedArray()) as String

  @Test fun everyTextHasFaEnPsAndEnglishHasNoPersianLetters() {
    val methods = texts()
    assertTrue(methods.size > 120, "بازتاب نوشته‌ها را پیدا نکرد: ${methods.size}")
    for (l in ShopperLang.entries) {
      ShopperText.lang = l
      val seen = mutableListOf<Triple<String, String, String>>()
      ShopperText.audit = { fa, en, ps -> seen += Triple(fa, en, ps) }
      for (m in methods) assertTrue(call(m, "Ab").isNotBlank(), "${m.name} در $l خالی است")
      ShopperText.audit = null
      for ((fa, en, ps) in seen) {
        assertTrue(fa.isNotBlank() && en.isNotBlank() && ps.isNotBlank(), "جای خالی: $fa")
        //  انگلیسیِ دیده‌شده فقط در زبانِ انگلیسی (مبلغِ درونش به زبانِ همان لحظه ساخته می‌شود)
        if (l == ShopperLang.EN) assertFalse(en.any { it in '؀'..'ۿ' }, "انگلیسیِ نیمه‌فارسی: $en")
      }
    }
  }

  @Test fun dariNeverRewritesInterpolatedValues() {
    ShopperText.lang = ShopperLang.PRS
    //  «فروشگاه» ⇒ «دکان» و «خیابان» ⇒ «سرک» در متنِ برنامه؛ همین در نامِ مشتری باید بماند
    val value = "فروشگاه خیابان"
    val withText = texts().filter { m -> m.name !in codeParams && m.parameterTypes.any { it == String::class.java } }
    assertTrue(withText.size >= 10, withText.joinToString { it.name })
    for (m in withText) {
      val out = call(m, value)
      assertTrue(value in out, "${m.name}: مقدار در دری عوض شد ⇒ asIs(…) نیست — $out")
    }
  }
}
