package ir.vil3ntec.tohid.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  RET — «برگشت و تعویضِ تازه» (`ReturnSettings`) پیش‌فرض خاموش است و هر درِ برگشت دقیقاً با همان کلید بین
 *  جریانِ امروز و جریانِ تازه انتخاب می‌کند: روشن ⇒ فرمِ تازه (`ReturnFlowDialog` ⇐ `ReturnFlow.post`)،
 *  حالتِ برگشتِ صندوقِ کامپیوتر (Shift+F9) و QR ِ رسید؛ خاموش ⇒ همان `ReturnDialog` و رسیدِ امروز.
 *  آزمون‌های `ReturnFlowTest`/`SalesReturnTest`/`return.e2e.mjs` همان جریانی را می‌سنجند که با کلیدِ روشن اجرا
 *  می‌شود؛ این آزمون می‌سنجد که کلید همان را روشن می‌کند و پیش‌فرض عوض نشده (حالتِ Compose ⇒ متن).
 */
class ReturnSettingsGateTest {

  private fun src(rel: String): String =
    listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
      .map { File(it, rel) }.firstOrNull { it.isFile }?.readText() ?: error("$rel پیدا نشد (cwd=${File(".").absoluteFile})")

  private fun count(s: String, x: String) = Regex(Regex.escape(x)).findAll(s).count()

  @Test fun `پیش‌فرض خاموش — کلیدِ همین دستگاه`() {
    val s = src("ir/vil3ntec/tohid/data/ReturnSettings.kt")
    assertTrue(s.contains("private const val ENABLED = \"returns_v2\""))
    assertTrue(s.contains("fun enabled(context: Context): Boolean = prefs(context).getBoolean(ENABLED, false)"))
  }

  @Test fun `تاریخچه — روشن فرمِ تازه، خاموش همان پنجرهٔ امروز، هرگز هر دو`() {
    val s = src("ir/vil3ntec/tohid/ui/screens/SalesHistory.kt")
    assertTrue(s.contains("val returnsV2 = remember { ir.vil3ntec.tohid.data.ReturnSettings.enabled(roleContext) }"))
    assertTrue(Regex("""if \(returnsV2\) \{\s*returnFor\?\.let \{ sale ->\s*ReturnFlowDialog\(""").containsMatchIn(s))
    assertTrue(Regex("""if \(!returnsV2\) returnFor\?\.let \{ sale ->\s*ReturnDialog\(""").containsMatchIn(s))
    //  هر دو فرمِ تازه (با رسید و بی رسید) فقط درونِ بلوکِ روشن؛ فرمِ امروز فقط بیرونِ آن
    val on = s.indexOf("if (returnsV2) {\n    returnFor?.let")
    val off = s.indexOf("if (!returnsV2) returnFor?.let")
    val flows = Regex(Regex.escape("ReturnFlowDialog(")).findAll(s).map { it.range.first }.toList()
    assertEquals(2, flows.size)
    assertTrue(flows.all { it in on until off })
    assertEquals(1, count(s, "    ReturnDialog("))
    assertTrue(s.indexOf("    ReturnDialog(") > off)
  }

  @Test fun `فرمِ تازه فقط از راهِ ReturnFlow post ثبت می‌کند`() {
    val s = src("ir/vil3ntec/tohid/ui/screens/ReturnsV2.kt")
    assertTrue(count(s, "ReturnFlow.post(") >= 1)
    assertEquals(0, count(s, "SalesEngine.recordReturn("))
  }

  /**
   *  RET-09، RET-10 — هر دو فرمِ تازه (کامپیوتر و برگهٔ نسخهٔ ۳) سد را از همان تابعی می‌گیرند که ثبت دوباره می‌سنجد
   *  (`ReturnDecision`، `ReturnDecisionTest`) و سیاستِ همین دستگاه را به ثبت می‌دهند. پیش از این برگهٔ نسخهٔ ۳
   *  `ReturnFlow.gate` را بی `priorMinor` و بی `policyBreach` و `Request` را بی `policy` می‌ساخت.
   */
  @Test fun `هر دو فرمِ تازه سیاست و برگشتِ پیشین را به سد و ثبت می‌دهند`() {
    for (rel in listOf("ir/vil3ntec/tohid/ui/screens/ReturnsV2.kt", "ir/vil3ntec/tohid/ui/v3/sales/returns/ReturnFlowSheet.kt")) {
      val s = src(rel)
      assertTrue(rel, s.contains("ReturnPolicyPrefs.of(context)"))
      assertTrue(rel, s.contains("ReturnDecision.of(d, sale, q, role, layers, limit, policy, todayIso())"))
      //  هیچ سدِ دستیِ جدا از ثبت
      assertEquals(rel, 0, count(s, "ReturnFlow.gate("))
      assertEquals(rel, 0, count(s, "ReturnFlow.permDecision("))
      //  ثبت همان سیاست و همان مجوز را می‌گیرد
      val i = s.indexOf("ReturnFlow.Request(")
      val call = s.substring(i, s.indexOf("val now = System.currentTimeMillis()", i))
      assertTrue(rel, call.contains("policy = policy"))
      assertTrue(rel, call.contains("perm = decision.perm"))
      //  RET-08: دلیل از فهرستِ همین دکان (برگهٔ نسخهٔ ۳ پیش‌تر فهرستِ ثابتِ `ReturnFlow.REASONS` را نشان می‌داد و نمی‌فرستاد)
      assertTrue(rel, call.contains("reasons = reasons"))
      assertTrue(rel, s.contains("ReturnSettings.reasons(context)"))
      assertEquals(rel, 0, count(s, "ReturnFlow.REASONS"))
      //  تأیید مالِ همان مبلغ و همان سد است؛ عوض شد ⇒ کنار می‌رود و دکمهٔ «درخواستِ تأیید» برمی‌گردد
      assertTrue(rel, s.contains("var approval by remember(ReturnFlow.minor(total), gate) { mutableStateOf<ReturnFlow.Approval?>(null) }"))
      //  هشدارِ «بیرون از سیاست» با همان حکم
      assertTrue(rel, s.contains("decision.verdict"))
    }
  }

  @Test fun `صندوقِ کامپیوتر و QR ِ رسید فقط با کلیدِ روشن`() {
    val desk = src("ir/vil3ntec/tohid/ui/screens/DeskSale.kt")
    assertTrue(desk.contains("val returnsV2 = remember { ir.vil3ntec.tohid.data.ReturnSettings.enabled(context) }"))
    assertTrue(desk.contains("if (returnsV2) CartHeaderAction(StrInvoices.returnItems"))
    val print = src("ir/vil3ntec/tohid/print/PrintJob.kt")
    assertTrue(print.contains("if (ir.vil3ntec.tohid.data.ReturnSettings.enabled(context) && sale.status != \"cancelled\") {"))
  }
}
