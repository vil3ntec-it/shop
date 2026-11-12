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
 *  - برگشت (هر دو فرم): تأیید به مبلغ و `gate` بسته است؛ مقدار که بیشتر شد «تأیید شد» نمی‌ماند.
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

  /**
   *  گوشیِ نسخهٔ ۳ (`ui/screens/Sale.kt` و `QuickSale.kt` ِ پیشین فقط پیش‌نمایشِ DevShot‌اند): تأیید در `CheckoutSession` است،
   *  ولی هر بار که صندوق یا فروشِ سریع برگهٔ پرداخت را باز می‌کند پاک می‌شود و سبد در برگهٔ پرداخت عوض نمی‌شود؛ هر ثبت
   *  دوباره با `CreditGate.state` می‌سنجد (همان مشتری و تا همان برد) و تأییدِ تازه فقط همان ثبتِ در انتظار را می‌زند.
   */
  @Test fun `گوشی همان الگو را دارد — تأیید فقط روی همان فاکتورِ در انتظار`() {
    val v3 = "ir/vil3ntec/tohid/ui/v3/sales/"
    for (f in listOf("cart/SaleRoute.kt", "quick/QuickSaleRoute.kt")) {
      assertTrue(f, Regex("""CheckoutSession\.reset\(\); nav\.open\(SalesPaths\.checkout\(""").containsMatchIn(src(v3 + f)))
    }
    val session = src(v3 + "checkout/CheckoutSession.kt")
    assertTrue(session.substring(session.indexOf("fun reset()")).contains("creditApproval = null"))
    val pay = src(v3 + "checkout/CheckoutRoute.kt")
    assertTrue(pay.contains("CheckoutModel.credited(gated, over, CheckoutSession.creditApproval)"))
    assertTrue(pay.contains("LaunchedEffect(freshCredit) { if (freshCredit != null && !submitted) submit() }"))
    assertTrue(src(v3 + "checkout/CheckoutModel.kt").contains("CreditGate.state(over, debtorId, approval) == CreditGate.State.APPROVED"))
    assertFalse(Regex("""mutableStateOf<[\w.]*CreditGate\.Approval\?>""").containsMatchIn(pay))
  }

  @Test fun `تأییدِ برگشت با مبلغ یا gate ِ تازه کنار می‌رود`() {
    val keyed = "var approval by remember(ReturnFlow.minor(total), gate) { mutableStateOf<ReturnFlow.Approval?>(null) }"
    //  هر دو فرمِ برگشت — کامپیوتر و برگهٔ نسخهٔ ۳ (RET-10: برگهٔ نسخهٔ ۳ تأیید را با `remember` ِ بی‌کلید نگه می‌داشت)
    for (rel in listOf("ir/vil3ntec/tohid/ui/screens/ReturnsV2.kt", "ir/vil3ntec/tohid/ui/v3/sales/returns/ReturnFlowSheet.kt")) {
      val s = src(rel)
      assertEquals(rel, 1, Regex(Regex.escape("var approval by remember")).findAll(s).count())
      assertTrue(rel, s.contains(keyed))
      //  کلید پس از ساختنِ gate است، نه پیش از آن
      assertTrue(rel, s.indexOf("val gate = decision.gate") in 0 until s.indexOf(keyed))
    }
    //  سقفِ دستگاه هم تجمعی سنجیده می‌شود — در همان سدی که ثبت هم می‌سنجد
    assertTrue(Regex("""ReturnFlow\.minor\(limit\),\s+priorMinor, policyBreach = !verdict\.ok""").containsMatchIn(src("ir/vil3ntec/tohid/data/ReturnDecision.kt")))
    assertTrue(src("ir/vil3ntec/tohid/data/ReturnFlow.kt").contains("ReturnDecision.gate(d, sale, quote.lines, quote.total, req.role, req.perm, req.limit, req.policy, today)"))
  }
}
