package ir.vil3ntec.tohid.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  POS-40 — «اصلاحِ روشِ پرداخت» در فاکتورهای گذشته (همان `SalesHistoryScreen` ِ گوشی و کامپیوتر؛ Compose، پس متن
 *  سنجیده می‌شود): فقط با تنظیمِ همین دستگاه و مجوزِ جز «ممنوع»، ثبت فقط با `PayFixPref.record` (دفترِ همین لحظهٔ
 *  دیسک) با کلیدِ همان پنجره، «نیازمند تأیید» با `ApprovalDialog`، و فاکتور دست نمی‌خورد.
 */
class PayFixWiringTest {

  private fun src(rel: String): String =
    listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
      .map { File(it, rel) }.firstOrNull { it.isFile }?.readText() ?: error("$rel پیدا نشد (cwd=${File(".").absoluteFile})")

  private val history = "ir/vil3ntec/tohid/ui/screens/SalesHistory.kt"
  private val host = "ir/vil3ntec/tohid/ui/screens/PayFixHostAndroid.kt"

  @Test fun `منو فقط با تنظیمِ همین دستگاه و مجوز`() {
    val s = src(history)
    assertTrue(s.contains("PayFixPref.on(roleContext) && ir.vil3ntec.tohid.data.PayFixPref.decision(roleContext) != \"deny\""))
    assertTrue(s.contains("onPayFix = if (payFix) ({ payFixFor = sale }) else null,"))
    //  منو فقط برای فاکتورِ زنده و غیرِ اینترنتی، کنارِ «اصلاح» و «لغو»
    val gate = s.indexOf("if (!cancelled && !online) {")
    assertTrue(gate > 0 && s.indexOf("onPayFix?.let") > gate)
    assertEquals(1, Regex(Regex.escape("PayFixHost(d, store, sale, me, ::toast) { payFixFor = null }")).findAll(s).count())
  }

  @Test fun `ثبت فقط با PayFixPref record و کلیدِ همان پنجره`() {
    val s = src(host)
    assertEquals(1, Regex(Regex.escape("PayFixPref.record(store, key, r)")).findAll(s).count())
    assertTrue(s.contains("val key = remember(sale.id) { newId() }"))
    //  ⛔ نه حافظهٔ `ShopStore`، نه ویرایشِ فاکتور
    assertFalse(s.contains("store.update"))
    assertFalse(s.contains("store.save"))
    assertFalse(s.contains("sales ="))
    assertTrue(s.contains("need = ApprovalSlip.Need(PayFix.PERM, \"auditLog/\$key\", null, r.amountMinor)"))
  }

  @Test fun `تنظیم در Settings`() {
    assertEquals(1, Regex(Regex.escape("PayFixSettingsWired(context)")).findAll(src("ir/vil3ntec/tohid/ui/screens/Settings.kt")).count())
  }
}
