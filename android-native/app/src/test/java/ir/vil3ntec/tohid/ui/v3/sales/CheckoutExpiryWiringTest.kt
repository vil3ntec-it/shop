package ir.vil3ntec.tohid.ui.v3.sales

import ir.vil3ntec.tohid.core.credit.CreditGate
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.SalesEngine.CartLine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.WarehouseEntry
import ir.vil3ntec.tohid.ui.v3.sales.checkout.CheckoutExpiry
import ir.vil3ntec.tohid.ui.v3.sales.checkout.CheckoutModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  S22 «منقضی فقط با مجوزِ مدیر» در صندوقِ گوشیِ نسخهٔ ۳ (و فروشِ سریع، همان برگه) — همان راهِ کامپیوتر (`DeskSale`):
 *  `ExpiryPref.gate` ⇐ `Checkout.expiryGate`؛ پیش از ثبت نیازِ منقضیِ همین سبد (`SalesEngine.expiredNeed`) با تأییدِ گرفته‌شده
 *  (`ExpiryGate.covers`) سنجیده می‌شود و نپوشیده ⇒ برگهٔ تأییدِ مدیر با PIN (همان `CreditApprovalSheet`، نه کپیِ دوم)؛
 *  تأیید با `expiryApproval` همراهِ همان `Checkout` می‌رود و موتور دوباره می‌سنجد.
 *  روی `claude/batch-56` (a5bd4667f): برگه فقط `blockExpired` را می‌گذاشت و در `ui/v3` هیچ `expiryGate` نبود ⇒ با «با مجوزِ
 *  مدیر» (`blockExpired` خاموش) فروشِ منقضی بی هیچ تأییدی ثبت می‌شد (`SalesEngine.record` با `!expiryGate` سنجش را رد می‌کند).
 */
class CheckoutExpiryWiringTest {

  private fun src(rel: String): String =
    listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
      .map { File(it, "ir/vil3ntec/tohid/$rel") }.firstOrNull { it.isFile }?.readText() ?: error("$rel پیدا نشد (cwd=${File(".").absoluteFile})")

  private val route = src("ui/v3/sales/checkout/CheckoutRoute.kt")
  private val model = src("ui/v3/sales/checkout/CheckoutModel.kt")

  @Test
  fun `برگهٔ پرداختِ گوشی تنظیمِ «با مجوزِ مدیر» را به ثبت می‌دهد`() {
    //  تنظیمِ همین دستگاه از همان یک خواننده (`TillGuardsPref` ⇐ `ExpiryPref.gate`) که `DeskSale` و فروشِ تعویض هم می‌خوانند
    assertTrue(route.contains("val tillGuards = remember { ir.vil3ntec.tohid.data.TillGuardsPref.get(context) }") && route.contains("val expiryGateOn = tillGuards.expiryGate"))
    assertTrue(src("data/TillGuardsAndroid.kt").contains("ExpiryPref.gate(context)"))
    val checkout = route.substringAfter("val checkout = ", "").substringBefore("val n = ")
    assertTrue(checkout.startsWith("CheckoutModel.checkout(") && checkout.contains("expiryGate = expiryGateOn"))
    assertTrue("تأییدِ گرفته‌شده روی همان `Checkout`", checkout.contains("expiryApproval = CheckoutSession.expiryApproval"))
    assertTrue(Regex("""SalesEngine\.Checkout\([\s\S]*?expiryGate = expiryGate,""").containsMatchIn(model))
  }

  @Test
  fun `ثبتِ گوشی پیش از نوشتن نیازِ منقضی را می‌سنجد و برگهٔ تأییدِ مدیر را باز می‌کند`() {
    val submit = route.substringAfter("fun submit(").substringBefore("submitted = true")
    assertTrue("سنجش پیش از ثبت", submit.contains("CheckoutExpiry.uncovered(data, loyal, sale, todayIso())?.let { need -> askExpiry(need, resubmit = true); return }"))
    val ask = route.substringAfter("fun askExpiry(", "").substringBefore("\n    }")
    assertTrue(ask.contains("CheckoutSession.expiryPending = resubmit") && ask.contains("nav.open(SalesPaths.EXPIRY)"))
    //  پس از تأییدِ سقفِ اعتبار و پیش از پیش‌سنجیِ سقف‌های سرور — ترتیبِ `DeskSale.finish`
    assertTrue(submit.indexOf("CheckoutModel.credited(") < submit.indexOf("CheckoutExpiry.uncovered(") &&
      submit.indexOf("CheckoutExpiry.uncovered(") < submit.indexOf("if (capRules.active)"))
    //  حساب‌پی: پیش از گرفتنِ پول همان سنجش
    val hesab = route.substringAfter("hesabPay = {").substringBefore("nav.open(SalesPaths.HESAB)")
    assertTrue(hesab.contains("CheckoutExpiry.uncovered(data, loyal, checkout, todayIso())") && Regex("""askExpiry\(\w+, resubmit = false\)""").containsMatchIn(hesab))
    //  تأیید رسید ⇒ همان ثبت (با همان تسویهٔ حساب‌پی اگر بود)
    assertTrue(route.contains("if (freshExpiry != null && CheckoutSession.expiryPending && !submitted) { CheckoutSession.expiryPending = false; submit(CheckoutSession.hesabPaid) }"))
  }

  @Test
  fun `برگهٔ تأییدِ منقضی همان برگهٔ PIN ِ سقفِ اعتبار است و با هر پرداخت پاک می‌شود`() {
    val expiry = src("ui/v3/sales/checkout/CheckoutExpiry.kt")
    assertTrue(expiry.contains("CreditApprovalSheet("))
    assertTrue(expiry.contains("title = StrPos.expiredSaleTitle"))
    assertEquals("کپیِ دومِ بدنهٔ PIN نه", 0, Regex("""ApprovalPinBody\(|ApproverVault""").findAll(expiry).count())
    assertTrue(src("ui/v3/sales/SalesRoutes.kt").contains("{ ExpiryApprovalRoute() }"))
    val session = src("ui/v3/sales/checkout/CheckoutSession.kt").substringAfter("fun reset()")
    assertTrue(session.contains("expiry = null") && session.contains("expiryApproval = null") && session.contains("expiryPending = false"))
  }

  //  «شیر»: ۲ منقضی و ۱۰ سالم (همان ریختِ `ScenarioS22Test`)
  private val today = "2026-11-02"
  private val d = ShopData(
    products = listOf(Product(id = "milk", name = "شیر", unit = "عدد", purchasePrice = 50.0, salePrice = 100.0)),
    warehouseEntries = listOf(
      WarehouseEntry(id = "old", productId = "milk", units = 2.0, expiryDate = "2026-10-30"),
      WarehouseEntry(id = "far", productId = "milk", units = 10.0, expiryDate = "2026-12-12"),
    ),
  )
  private val form = CheckoutModel.start(null, emptyList())
  private var seq = 0

  /** همان زنجیرهٔ برگه: `CheckoutModel.checkout` ⇐ `legacy` ⇐ `SalesEngine.record` */
  private fun post(cart: List<CartLine>, c: SalesEngine.Checkout): SalesEngine.Result =
    SalesEngine.record(d, cart, CheckoutModel.legacy(c, CheckoutModel.numbers(d, cart, form, c), form), today, 1L, { "x${seq++}" })

  private fun manager(need: Map<String, Long>) = CheckoutExpiry.approval(CreditGate.Approval("مدیر", "manager", "", 0L, "مشتری خواست"), need)

  @Test
  fun `گوشی — منقضی بی تأییدِ مدیر ثبت نمی‌شود و تأیید به همان سبد بسته است`() {
    val gate = CheckoutModel.checkout(form, canDiscount = true, blockExpired = false, seller = "", expiryGate = true)
    val eleven = listOf(CartLine("milk", 11.0))
    assertEquals(mapOf("milk" to 1_000L), CheckoutExpiry.uncovered(d, eleven, gate, today))
    assertTrue("بی تأیید موتور هم رد می‌کند", post(eleven, gate) is SalesEngine.Result.Failed)
    //  تأییدِ برگهٔ PIN ⇒ همان کالا و تا همان مقدار؛ ثبت با نام و دلیل در سابقه
    val approved = gate.copy(expiryApproval = manager(mapOf("milk" to 1_000L)))
    assertNull(CheckoutExpiry.uncovered(d, eleven, approved, today))
    val sold = post(eleven, approved) as SalesEngine.Result.Ok
    assertEquals("مدیر", sold.data.auditLog.single { it.type == "expired_sale" }.approvedBy)
    //  سبد پس از تأیید منقضیِ بیشتری برمی‌دارد ⇒ تأییدِ کهنه نمی‌پوشاند: دوباره می‌پرسد و موتور هم رد می‌کند
    val twelve = listOf(CartLine("milk", 12.0))
    assertEquals(mapOf("milk" to 2_000L), CheckoutExpiry.uncovered(d, twelve, approved, today))
    assertTrue(post(twelve, approved) is SalesEngine.Result.Failed)
    //  تأییدِ شاگرد (بی حقِ تأیید) نمی‌پوشاند
    val staff = CheckoutExpiry.approval(CreditGate.Approval("رحیم", "staff", "", 0L, "خواست"), mapOf("milk" to 1_000L))
    assertNotNull(CheckoutExpiry.uncovered(d, eleven, gate.copy(expiryApproval = staff), today))
    //  بی منقضی ⇒ بی پرسش
    assertNull(CheckoutExpiry.uncovered(d, listOf(CartLine("milk", 10.0)), gate, today))
  }

  @Test
  fun `گوشی — «فقط هشدار» بی پرسش و «فروخته نشود» همیشه بسته`() {
    val eleven = listOf(CartLine("milk", 11.0))
    val warn = CheckoutModel.checkout(form, canDiscount = true, blockExpired = false, seller = "")
    assertNull(CheckoutExpiry.uncovered(d, eleven, warn, today))
    assertTrue(post(eleven, warn) is SalesEngine.Result.Ok)
    //  `ExpiryPref.gate` با «فروخته نشود» خاموش است؛ اگر هم روشن بیاید، بستن بر تأیید پیش است
    val block = CheckoutModel.checkout(form, canDiscount = true, blockExpired = true, seller = "", expiryGate = true)
    assertNull(CheckoutExpiry.uncovered(d, eleven, block, today))
    assertTrue(post(eleven, block.copy(expiryApproval = manager(mapOf("milk" to 1_000L)))) is SalesEngine.Result.Failed)
  }
}
