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

  @Test fun `هر سه صندوق و تنظیمات و پنلِ پرداخت`() {
    val call = "GiftCardBar(d, store, ::toast"
    for (rel in listOf("ui/screens/Sale.kt", "ui/legacy/SaleLegacy.kt", "ui/screens/DeskSale.kt")) assertEquals(rel, 1, count("ir/vil3ntec/tohid/$rel", call))
    assertEquals(1, count("ir/vil3ntec/tohid/ui/legacy/SettingsLegacy.kt", "GiftCardSettingsWired(context)"))
    assertEquals(1, count("ir/vil3ntec/tohid/ui/screens/Settings.kt", "GiftCardSettingsWired(context)"))
    assertEquals(1, count("ir/vil3ntec/tohid/ui/legacy/AppRootLegacy.kt", "StrGiftCard.settingsTitle to"))
    //  روش‌های پنل فقط با همان تنظیم؛ پیش‌سنجشِ مانده پیش از ثبت
    assertEquals(1, count("ir/vil3ntec/tohid/ui/screens/SaleDialogs.kt", "it.stored = ir.vil3ntec.tohid.data.GiftCardPref.on(context)"))
    assertEquals(1, count("ir/vil3ntec/tohid/ui/screens/DeskSale.kt", "stored = remember { ir.vil3ntec.tohid.data.GiftCardPref.on(context) }"))
    assertTrue(src("ir/vil3ntec/tohid/ui/screens/SaleDialogs.kt").contains("GiftCards.problem(d, r.value, debtorId)"))
    assertTrue(src("ir/vil3ntec/tohid/ui/components/pos/PayPanel.kt").contains("GiftCards.problem(ledger, r.value, customerId)"))
  }

  @Test fun `خرج و برگشتِ کارت فقط در دفتر — هیچ صفحه‌ای ردیفِ کارت نمی‌سازد`() {
    val data = "ir/vil3ntec/tohid/data/"
    assertEquals(1, count(data + "SalesEngine.kt", "GiftCards.forSale("))
    assertEquals(1, count(data + "SalesEngine.kt", "GiftCards.forCancel("))
    assertEquals(1, count(data + "ReturnFlow.kt", "GiftCards.forReturn("))
    for (rel in listOf("ui/screens/Sale.kt", "ui/legacy/SaleLegacy.kt", "ui/screens/DeskSale.kt", "ui/screens/SaleDialogs.kt", "ui/components/pos/PayPanel.kt")) {
      val s = src("ir/vil3ntec/tohid/$rel")
      for (x in listOf("GiftCards.forSale", "GiftCards.forReturn", "GiftCards.forCancel", "GiftCards.issue(", "gift_card_redeem")) assertFalse("$rel ⇐ $x", s.contains(x))
    }
  }
}
