package ir.vil3ntec.tohid.ui.v3

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  STF-03 — سیم‌کشیِ پیش‌سنجیِ سقف در صندوقِ گوشیِ نسخهٔ ۳ و کامپیوتر (Compose با آزمونِ JVM ساخته نمی‌شود، پس مثلِ
 *  `RemoteApprovalWiringTest` با متنِ همان پرونده‌ها). منطق در `SaleCapGateTest` و `SaleCapsVectorsTest` (core).
 *  هر دو صندوق: پیش‌سنجی روی همان ثبت، برگهٔ تأیید (که «پرسیدن از مدیر» و PIN را دارد) با همان نیاز، برگه‌ها روی فاکتور، و
 *  آخرین سد پیش از نوشتنِ دفتر.
 */
class SaleCapWiringTest {

  private fun src(rel: String): String =
    listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
      .map { File(it, "ir/vil3ntec/tohid/$rel") }.firstOrNull { it.isFile }?.readText()
      ?: error("$rel پیدا نشد (cwd=${File(".").absoluteFile})")

  @Test
  fun `گوشیِ نسخهٔ ۳ — پیش‌سنجی، برگهٔ تأییدِ مشترک و آخرین سد`() {
    val route = src("ui/v3/sales/checkout/CheckoutRoute.kt")
    assertTrue(route.contains("val capRules = remember { ShopRole.saleCaps(context) }"))
    assertTrue(route.contains("SaleCapGate.check(capRules, it.data, it.saleId, capSlips).blocked"))
    assertTrue(route.contains("CheckoutSession.ask = ApprovalAsk(title = SaleCapGate.title(b.perm), summary = SaleCapGate.summary(b), need = b.need)"))
    assertTrue(route.contains("SaleCapGate.guard(capRules, TillSale.record("))
    assertTrue(route.contains("priceSlip = SaleCapGate.json(capSlips, SaleCaps.PRICE)"))
    //  برگهٔ قرض/قیمت جدا از برگهٔ تخفیف
    assertTrue(route.contains("if (s.perm == SaleCaps.DISCOUNT) CheckoutSession.approval = s else CheckoutSession.capSlips = CheckoutSession.capSlips + (s.perm to s)"))
  }

  @Test
  fun `کامپیوتر — همان پیش‌سنجی با پنجرهٔ تأییدِ مشترک`() {
    val desk = src("ui/screens/DeskSale.kt")
    assertTrue(desk.contains("val capRules = remember { ir.vil3ntec.tohid.data.ShopRole.saleCaps(context) }"))
    assertTrue(desk.contains("ir.vil3ntec.tohid.data.SaleCapGate.check(capRules, it.data, it.saleId, slips).blocked"))
    assertTrue(desk.contains("ir.vil3ntec.tohid.data.SaleCapGate.guard(capRules, recordAll(key,"))
    assertTrue(desk.contains("capAsk?.let { (ask, pending, credit) ->"))
    assertTrue(desk.contains("need = ask.need,"))
    //  همان پنجره‌ای که «پرسیدن از مدیر» دارد
    assertTrue(src("ui/components/staff/ApprovalDialog.kt").contains("val remote = rememberRemoteAsk(need, shopId)"))
  }

  @Test
  fun `سرور تنها داور است — کلیدِ اجراشده از policy و برگه روی فاکتور`() {
    assertTrue(src("data/ShopRole.kt").contains("price = enforces(context, \"sales.price\"), credit = enforces(context, \"sales.credit\")"))
    val engine = src("data/SalesEngine.kt")
    assertTrue(engine.contains("creditApproval = checkout.creditCapSlip, priceApproval = checkout.priceSlip"))
  }
}
