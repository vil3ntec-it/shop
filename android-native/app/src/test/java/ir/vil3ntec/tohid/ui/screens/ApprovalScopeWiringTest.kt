package ir.vil3ntec.tohid.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  تأیید فقط برای همان چیزی که تأیید شد (حالتِ Compose، پس آزمونِ JVM آن را نمی‌سازد و متن سنجیده می‌شود):
 *  - صندوقِ کامپیوتر: تأییدِ سقفِ اعتبار در حالت نمی‌ماند و فقط به همان یک تلاشِ `finish` می‌رود — مثلِ
 *    `QuickSale` و `Sale` ِ گوشی؛ وگرنه اگر آن تلاش ثبت نمی‌کرد، سبدِ دیگرِ همان مشتری تا همان مبلغ با تأییدِ
 *    کهنه ثبت می‌شد.
 *  - برگشت: تأیید به مبلغ و `gate` بسته است؛ مقدار که بیشتر شد «تأیید شد» نمی‌ماند.
 */
class ApprovalScopeWiringTest {

  private fun src(rel: String): String =
    listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
      .map { File(it, rel) }.firstOrNull { it.isFile }?.readText() ?: error("$rel پیدا نشد (cwd=${File(".").absoluteFile})")

  @Test fun `صندوقِ کامپیوتر تأییدِ سقفِ اعتبار را نگه نمی‌دارد`() {
    val s = src("ir/vil3ntec/tohid/ui/screens/DeskSale.kt")
    assertFalse("تأییدِ اعتبار نباید در remember بماند", Regex("""mutableStateOf<[\w.]*CreditGate\.Approval\?>""").containsMatchIn(s))
    assertTrue(s.contains("fun finish(settled: ir.vil3ntec.tohid.core.pay.Settlement? = null, creditApproval: ir.vil3ntec.tohid.core.credit.CreditGate.Approval? = null)"))
    assertTrue(s.contains("onApproved = { ok -> creditAsk = null; finish(pending, ok) }"))
    //  همان یک تأیید هم به فروش و هم به سنجشِ پیش از ثبت می‌رسد
    assertTrue(s.contains("creditApproval = creditApproval)"))
    assertTrue(s.contains("CreditGate.state(over, sale.debtorId.orEmpty(), creditApproval)"))
  }

  @Test fun `گوشی همان الگو را دارد — تأیید فقط روی همان فاکتورِ در انتظار`() {
    for (f in listOf("QuickSale.kt", "Sale.kt")) {
      val s = src("ir/vil3ntec/tohid/ui/screens/$f")
      assertTrue(f, s.contains("postSale(pending.copy(creditApproval = ok))"))
      assertFalse(f, Regex("""mutableStateOf<[\w.]*CreditGate\.Approval\?>""").containsMatchIn(s))
    }
  }

  @Test fun `تأییدِ برگشت با مبلغ یا gate ِ تازه کنار می‌رود`() {
    val s = src("ir/vil3ntec/tohid/ui/screens/ReturnsV2.kt")
    val keyed = "var approval by remember(ReturnFlow.minor(total), gate) { mutableStateOf<ReturnFlow.Approval?>(null) }"
    assertEquals(1, Regex(Regex.escape("var approval by remember")).findAll(s).count())
    assertTrue(s.contains(keyed))
    //  کلید پس از ساختنِ gate است، نه پیش از آن
    assertTrue(s.indexOf("val gate = ReturnFlow.gate(") in 0 until s.indexOf(keyed))
    //  سقفِ دستگاه هم تجمعی سنجیده می‌شود
    assertTrue(s.contains("ReturnFlow.minor(limit), priorMinor)"))
  }
}
