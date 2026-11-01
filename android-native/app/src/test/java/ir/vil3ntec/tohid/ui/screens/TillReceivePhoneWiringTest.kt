package ir.vil3ntec.tohid.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  POS-39 — «دریافت از مشتری» در صندوقِ گوشی همان جریانِ F7 ِ کامپیوتر است (حالتِ Compose، پس متن سنجیده می‌شود):
 *  فقط با `TillReceivePref` ِ همین دستگاه، ثبت فقط با `TillReceivePref.record` (دفترِ همین لحظهٔ دیسک، #567) با
 *  کلیدِ ساخته‌شده هنگامِ زدنِ دکمه، و پنجره پشتِ `VipGate` ِ قرض‌داران؛ سبد دست نمی‌خورد.
 */
class TillReceivePhoneWiringTest {

  private fun src(rel: String): String =
    listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
      .map { File(it, rel) }.firstOrNull { it.isFile }?.readText() ?: error("$rel پیدا نشد (cwd=${File(".").absoluteFile})")

  private val phone = "ir/vil3ntec/tohid/ui/screens/TillReceivePhoneAndroid.kt"

  @Test fun `دکمه فقط با تنظیمِ همین دستگاه — خاموش هیچ`() {
    val s = src(phone)
    val gate = s.indexOf("if (!on) return")
    assertTrue(s.contains("val on = remember { ir.vil3ntec.tohid.data.TillReceivePref.on(context) }"))
    assertTrue("پیش از هر چیزِ دیگر", gate in 0 until s.indexOf("OutlinedButton("))
  }

  @Test fun `ثبت فقط از راهِ TillReceivePref record با کلیدِ همان ضربه`() {
    val s = src(phone)
    assertEquals(1, Regex(Regex.escape("TillReceivePref.record(store, id, amount, notes, k, prepay)")).findAll(s).count())
    assertTrue(s.contains("onClick = { key = newId();"))
    //  ⛔ نه حافظهٔ نمونهٔ `ShopStore`، نه راهِ دومِ ثبتِ بدهی
    assertFalse(s.contains("store.update"))
    assertFalse(s.contains("DebtorEngine.addTransaction"))
    assertFalse(s.contains("cartStore"))
    assertTrue(s.contains("gate = { content -> VipGate(Str.debtors) { content() } }"))
  }

  @Test fun `صندوقِ گوشی دکمه را دارد`() {
    val s = src("ir/vil3ntec/tohid/ui/screens/Sale.kt")
    assertEquals(1, Regex(Regex.escape("TillReceivePhone(d, store, cartDebtorId, ::toast)")).findAll(s).count())
  }
}
