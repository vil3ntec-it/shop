package ir.vil3ntec.tohid.core.shopper

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** پول، مقدار، رقم و پیامِ خطای برنامهٔ خرید در هر چهار زبان. */
class ShopperFormatTest {

  @AfterTest fun reset() {
    ShopperText.lang = ShopperLang.FA
  }

  @Test fun moneyAndQuantityUseLocalDigitsAndSeparators() {
    ShopperText.lang = ShopperLang.FA
    assertEquals("۱۲۰٫۵ افغانی", ShopperText.money(12050))
    assertEquals("۱٬۲۵۰ افغانی", ShopperText.money(125000))
    assertEquals("۰٫۰۵ افغانی", ShopperText.money(5))
    assertEquals("۱٫۵", ShopperText.qty(1500))
    assertEquals("۲", ShopperText.qty(2000))
    ShopperText.lang = ShopperLang.EN
    assertEquals("1,250 AFN", ShopperText.money(125000))
    assertEquals("0.125", ShopperText.qty(125))
    ShopperText.lang = ShopperLang.PS
    assertEquals("۳۰ افغانۍ", ShopperText.money(3000))
    ShopperText.lang = ShopperLang.PRS
    assertEquals("۳۰ افغانی", ShopperText.money(3000))
    assertEquals("−۱٬۰۰۰", ShopperText.num(-1000))
  }

  @Test fun languageCodesAndDirection() {
    assertEquals(ShopperLang.PRS, ShopperLang.of("prs"))
    assertEquals(ShopperLang.FA, ShopperLang.of("xx"))
    assertEquals(ShopperLang.FA, ShopperLang.of(null))
    assertTrue(ShopperLang.PS.rtl)
    assertTrue(!ShopperLang.EN.rtl)
  }

  @Test fun dariOverlayChangesAppWordsButNeverInterpolatedValues() {
    ShopperText.lang = ShopperLang.PRS
    //  «فروشگاه» ⇒ «دکان» در متنِ برنامه؛ نامِ کالا/دکانِ مشتری همان می‌ماند
    assertEquals("این دکان فعلاً سفارشِ آنلاین نمی‌پذیرد", ShopperText.notAccepting)
    val name = "فروشگاه خیابان"
    assertTrue(name in ShopperText.addOne(name), ShopperText.addOne(name))
    assertTrue(name in ShopperText.nothingFound(name))
  }

  @Test fun dariErrorsAreWholeDariSentences() {
    //  LOC-08 دورِ ۶: «دوباره بزنید»، «اینترنت را ببینید» و «مهلت» جملهٔ کاملِ دری می‌گیرند (`DariText.PHRASES`)؛ فارسی همان
    ShopperText.lang = ShopperLang.PRS
    assertEquals("اتصال برقرار نشد؛ انترنت را بررسی کنید و دوباره کوشش کنید.", ShopperText.error(ShopperError(0, ShopperError.NETWORK)))
    assertEquals("میعادِ این کد ختم شده است؛ کدِ جدید بخواهید.", ShopperText.error(ShopperError(400, "otp_expired")))
    assertEquals("خطا؛ دوباره کوشش کنید.", ShopperText.error(ShopperError(400, "brand_new_code")))
    assertEquals("دوباره کوشش کنید", ShopperText.retry)
    ShopperText.lang = ShopperLang.FA
    assertEquals("اتصال برقرار نشد؛ اینترنت را ببینید و دوباره بزنید.", ShopperText.error(ShopperError(0, ShopperError.NETWORK)))
    assertEquals("دوباره بزنید", ShopperText.retry)
  }

  @Test fun errorsUseOwnWordsThenServerMessageThenGeneric() {
    ShopperText.lang = ShopperLang.EN
    assertEquals("No connection — check your internet and try again.", ShopperText.error(ShopperError(0, ShopperError.NETWORK)))
    assertEquals("That code isn't right.", ShopperText.error(ShopperError(400, "otp_wrong", "کد درست نیست")))
    assertEquals("Too many requests — please try again in a moment.", ShopperText.error(ShopperError(429, "unknown_rate")))
    assertEquals("Something went wrong — please try again.", ShopperText.error(ShopperError(400, "brand_new_code", "پیامِ فارسی")))
    ShopperText.lang = ShopperLang.FA
    assertEquals("پیامِ تازهٔ سرور", ShopperText.error(ShopperError(400, "brand_new_code", "پیامِ تازهٔ سرور")))
    assertEquals(ShopperText.problem(CheckoutProblem.VERIFY), ShopperText.error(ShopperError(403, "phone_not_verified")))
  }

  @Test fun everyCheckoutProblemAndOrderStatusHasText() {
    for (l in ShopperLang.entries) {
      ShopperText.lang = l
      for (p in CheckoutProblem.entries) assertTrue(ShopperText.problem(p).isNotBlank(), "$l $p")
      for (s in ir.vil3ntec.tohid.core.orders.OrderStates.STATUSES) {
        assertTrue(ShopperText.status(s).isNotBlank() && s !in ShopperText.status(s), "$l $s بی متن")
      }
      for (id in listOf("placed", "accepted", "prep", "ready", "way", "picked", "delivered")) assertTrue(ShopperText.step(id).isNotBlank())
    }
  }
}
