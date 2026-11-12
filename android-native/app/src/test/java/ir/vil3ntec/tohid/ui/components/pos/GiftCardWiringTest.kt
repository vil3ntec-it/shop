package ir.vil3ntec.tohid.ui.components.pos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  PAY-24 گام ۲ — کارتِ هدیه و اعتبارِ فروشگاه در هر دو صندوق (حالتِ Compose، پس متن سنجیده می‌شود): نوار و روش‌های
 *  پنل فقط با `GiftCardPref` ِ همین دستگاه (پیش‌فرض خاموش)، فروشِ کارت فقط از `GiftCardPref.issue` (دفترِ همین لحظهٔ
 *  دیسک)، و خرج فقط درونِ `SalesEngine.record` (هیچ صفحه‌ای ردیفِ کارت نمی‌نویسد).
 */
class GiftCardWiringTest {

  private fun src(rel: String): String =
    listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
      .map { File(it, rel) }.firstOrNull { it.isFile }?.readText() ?: error("$rel پیدا نشد (cwd=${File(".").absoluteFile})")

  private fun count(rel: String, what: String) = Regex(Regex.escape(what)).findAll(src(rel)).count()

  private val bar = "ir/vil3ntec/tohid/ui/components/pos/GiftCardBarAndroid.kt"

  @Test fun `نوار فقط با تنظیمِ همین دستگاه و فروشِ کارت فقط از GiftCardPref issue`() {
    val s = src(bar)
    assertTrue(s.contains("val on = remember { GiftCardPref.on(context) }"))
    assertTrue(s.indexOf("if (!on) return") in 0 until s.indexOf("OutlinedButton("))
    for (x in listOf("store.save", "store.update", "SalesEngine.record", "DebtorEngine.", "GiftCards.forSale")) assertFalse(x, s.contains(x))
    assertEquals(1, count(bar, "GiftCardPref.issue(store, key,"))
    //  فقط نقشی که فاکتور می‌نویسد کارت می‌فروشد
    assertTrue(s.contains("ShopRole.canWrite(context, \"sales\")"))
  }

  @Test fun `صندوقِ گوشی و کامپیوتر و تنظیمات و پنلِ پرداخت`() {
    //  کامپیوتر: `DeskSale`؛ گوشیِ نسخهٔ ۳: ابزارهای سبدِ `PhoneTillTools` که `SaleRoute` زیرِ سبد می‌گذارد (`ui/screens/Sale.kt` فقط پیش‌نمایشِ DevShot است)
    assertEquals(1, count("ir/vil3ntec/tohid/ui/screens/DeskSale.kt", "GiftCardBar(d, store, ::toast"))
    assertEquals(1, count("ir/vil3ntec/tohid/ui/v3/sales/cart/PhoneTillTools.kt", "GiftCardBar(data, store, toast)"))
    assertEquals(1, count("ir/vil3ntec/tohid/ui/v3/sales/cart/SaleRoute.kt", "tools = { PhoneTillTools("))
    //  صفحه و ردیفِ تنظیماتِ نسخهٔ ۳ (گوشی و کامپیوتر) — جای `SettingsLegacy` و فهرستِ کامپیوترِ رابطِ قدیمِ پاک‌شده
    assertEquals(1, count("ir/vil3ntec/tohid/ui/v3/settings/sections/TillMoneyScreensAndroid.kt", "GiftCardPref.set(context, it)"))
    assertEquals(1, count("ir/vil3ntec/tohid/ui/screens/SettingsLayout.kt", "GIFT_CARD -> ir.vil3ntec.tohid.core.i18n.StrGiftCard.settingsTitle"))
    //  روش‌های پنل فقط با همان تنظیم؛ پیش‌سنجشِ مانده پیش از ثبت
    assertEquals(1, count("ir/vil3ntec/tohid/ui/screens/SaleDialogs.kt", "it.stored = ir.vil3ntec.tohid.data.GiftCardPref.on(context)"))
    assertEquals(1, count("ir/vil3ntec/tohid/ui/screens/DeskSale.kt", "stored = remember { ir.vil3ntec.tohid.data.GiftCardPref.on(context) }"))
    assertTrue(src("ir/vil3ntec/tohid/ui/screens/SaleDialogs.kt").contains("GiftCards.problem(d, r.value, debtorId)"))
    assertTrue(src("ir/vil3ntec/tohid/ui/components/pos/PayPanel.kt").contains("GiftCards.problem(ledger, r.value, customerId)"))
  }

  @Test fun `شارژ و باطل و چاپ و بدهی در همان نوار و کارت و اعتبار در صندوقِ v3 با همان تنظیم`() {
    val s = src(bar)
    assertEquals(1, count(bar, "GiftCardPref.recharge(store, key,"))
    assertEquals(1, count(bar, "GiftCardPref.void(store, key,"))
    //  باطل همان اجازهٔ لغوِ فاکتور؛ بدهی فقط برای نقشی که عددِ مالی می‌بیند؛ چاپ فقط از برگهٔ کارت
    assertTrue(s.contains("ShopRole.canCancelSale(context)"))
    assertTrue(s.contains("if (canMoney) TextButton(onClick = { owed = true })"))
    assertTrue(s.contains("GiftCardPrint.print(context, sheet)"))
    val v3 = "ir/vil3ntec/tohid/ui/v3/sales/checkout/"
    assertEquals(1, count(v3 + "CheckoutRoute.kt", "GiftCardPref.on(context)"))
    assertEquals(1, count(v3 + "CheckoutRoute.kt", "CheckoutStoredPay.settle(data, CheckoutSession.pay.draft("))
    assertEquals(1, count(v3 + "CheckoutRoute.kt", "giftLedger = data.takeIf { giftOn }"))
    val split = src(v3 + "CheckoutPaySplit.kt")
    assertTrue(split.contains("state.methods().forEach"))
    assertFalse(split.contains("PayFlow.panelMethods()"))
    for (rel in listOf("CheckoutPaySplit.kt", "CheckoutRoute.kt", "CheckoutSheet.kt")) {
      val t = src(v3 + rel)
      for (x in listOf("GiftCards.forSale", "GiftCards.issue(", "gift_card_redeem", "store.update")) assertFalse("$rel ⇐ $x", t.contains(x))
    }
  }

  @Test fun `خرج و برگشتِ کارت فقط در دفتر — هیچ صفحه‌ای ردیفِ کارت نمی‌سازد`() {
    val data = "ir/vil3ntec/tohid/data/"
    assertEquals(1, count(data + "SalesEngine.kt", "GiftCards.forSale("))
    assertEquals(1, count(data + "SalesEngine.kt", "GiftCards.forCancel("))
    assertEquals(1, count(data + "ReturnFlow.kt", "GiftCards.forReturn("))
    for (rel in listOf("ui/v3/sales/cart/PhoneTillTools.kt", "ui/v3/sales/cart/SaleRoute.kt", "ui/screens/DeskSale.kt", "ui/screens/SaleDialogs.kt", "ui/components/pos/PayPanel.kt")) {
      val s = src("ir/vil3ntec/tohid/$rel")
      for (x in listOf("GiftCards.forSale", "GiftCards.forReturn", "GiftCards.forCancel", "GiftCards.issue(", "gift_card_redeem")) assertFalse("$rel ⇐ $x", s.contains(x))
    }
  }
}
