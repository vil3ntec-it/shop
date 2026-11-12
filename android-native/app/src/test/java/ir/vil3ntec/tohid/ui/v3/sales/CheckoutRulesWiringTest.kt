package ir.vil3ntec.tohid.ui.v3.sales

import ir.vil3ntec.tohid.core.i18n.StrLedger
import ir.vil3ntec.tohid.core.sale.CheckoutRules
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.SalesEngine.CartLine
import ir.vil3ntec.tohid.ui.v3.sales.SalesTestLedger.ledger
import ir.vil3ntec.tohid.ui.v3.sales.checkout.CheckoutForm
import ir.vil3ntec.tohid.ui.v3.sales.checkout.CheckoutModel
import ir.vil3ntec.tohid.ui.v3.sales.checkout.HesabTillModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  POS-24 در صندوقِ گوشیِ نسخهٔ ۳ (و فروشِ سریع که همان برگهٔ `sale/checkout` است): قاعده‌های پیش از پایانِ فاکتورِ همین
 *  دستگاه (`CheckoutRulesPref`: «مشتری لازم از مبلغِ X»، «فاکتورِ صفر رد») باید همراهِ `Checkout` به `SalesEngine.record`
 *  برسند — همان راهِ کامپیوتر (`DeskSale`) و گوشیِ پیشین (`SaleDialogs`).
 *  روی `claude/batch-56` (a5bd4667f): `CheckoutModel.checkout` فیلدِ `rules` نداشت و `CheckoutRoute` فقط `lineSellers` و `tax` را
 *  روی آن می‌گذاشت ⇒ `rules` همیشه `CheckoutRules.OFF` و تنظیمِ صاحب‌کار در گوشی بی اثر بود.
 *  Compose است، پس سیم‌کشی با متن سنجیده می‌شود؛ رفتارِ خودِ قاعده در `CheckoutRulesTest` و `SalesEngineTest`.
 */
class CheckoutRulesWiringTest {

  private fun src(rel: String): String =
    listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
      .map { File(it, "ir/vil3ntec/tohid/$rel") }.firstOrNull { it.isFile }?.readText() ?: error("$rel پیدا نشد (cwd=${File(".").absoluteFile})")

  private val route = src("ui/v3/sales/checkout/CheckoutRoute.kt")
  private val model = src("ui/v3/sales/checkout/CheckoutModel.kt")

  @Test
  fun `برگهٔ پرداختِ گوشی قاعده‌های همین دستگاه را می‌خواند و به ثبت می‌دهد`() {
    //  خواندنِ تنظیمِ همین دستگاه از همان یک خواننده (`TillGuardsPref` ⇐ `CheckoutRulesPref.get`) که `DeskSale` و فروشِ تعویض هم می‌خوانند
    assertTrue(route.contains("val tillGuards = remember { ir.vil3ntec.tohid.data.TillGuardsPref.get(context) }") && route.contains("val checkoutRules = tillGuards.rules"))
    assertTrue(src("data/TillGuardsAndroid.kt").contains("CheckoutRulesPref.get(context)"))
    //  همان یک `Checkout` ِ برگه: جمع، پیش‌سنجیِ حساب‌پی (`HesabTillModel.precheck`) و ثبت همه از آن‌اند
    val checkout = route.substringAfter("val checkout = ", "").substringBefore("val n = ")
    assertTrue(checkout.startsWith("CheckoutModel.checkout("))
    assertTrue("قاعده‌ها باید در همان `Checkout` ِ برگه باشند", checkout.contains("rules = checkoutRules"))
    //  مدل قاعده‌ها را روی `SalesEngine.Checkout` می‌نشاند، نه پیش‌فرضِ خاموش
    assertTrue(Regex("""fun checkout\([^)]*rules: CheckoutRules""").containsMatchIn(model))
    assertTrue(Regex("""SalesEngine\.Checkout\([\s\S]*?rules = rules,""").containsMatchIn(model))
  }

  @Test
  fun `فروشِ سریع همان برگه است و هیچ ثبتِ دیگری در صندوقِ نسخهٔ ۳ نیست`() {
    assertTrue(src("ui/v3/sales/quick/QuickSaleRoute.kt").contains("nav.open(SalesPaths.checkout(quick = true))"))
    assertTrue(src("ui/v3/sales/SalesRoutes.kt").contains("V3Route(SalesPaths.CHECKOUT, RouteKind.SHEET, { StrSale.checkoutTitle }, parent = SalesPaths.SALE, chrome = Chrome.OWN) { CheckoutRoute() }"))
    //  `SalesEngine.Checkout(` ِ فروش در صندوقِ گوشی فقط از `CheckoutModel` ساخته می‌شود؛ تعویضِ برگشت از `TillGuards.exchange`
    //  (همان سدها؛ پیش‌تر `returns` بیرون می‌ماند و `ReturnFlowSheet` `Checkout` ِ خام بی قاعده و بی S22 می‌ساخت)
    val v3 = listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
      .map { File(it, "ir/vil3ntec/tohid/ui/v3/sales") }.first { it.isDirectory }
    val makers = v3.walkTopDown().filter { it.isFile && it.extension == "kt" }
      .filter { it.readText().contains("SalesEngine.Checkout(") }.map { it.name }.toList()
    assertEquals(listOf("CheckoutModel.kt"), makers)
  }

  private val cart = listOf(CartLine("soap", 2.0), CartLine("rice", 1.5)) // ۱۰۰ + ۱۸۰ = ۲۸۰ ؋
  private val cash = CheckoutModel.start(null, ledger.debtors)
  private var seq = 0

  /** همان زنجیرهٔ برگه: `CheckoutModel.checkout(rules = …)` ⇐ `legacy` ⇐ `SalesEngine.record` */
  private fun post(rules: CheckoutRules, form: CheckoutForm = cash): SalesEngine.Result {
    val c = CheckoutModel.checkout(form, canDiscount = true, blockExpired = false, seller = "", rules = rules)
    return SalesEngine.record(ledger, cart, CheckoutModel.legacy(c, CheckoutModel.numbers(ledger, cart, form, c), form), "2026-11-12", 1L, { "r${seq++}" })
  }

  @Test
  fun `قاعدهٔ «مشتری از مبلغِ X» از برگهٔ گوشی تا موتور می‌رسد و پیامِ روشن می‌دهد`() {
    assertTrue("خاموش ⇒ همان امروز", post(CheckoutRules.OFF) is SalesEngine.Result.Ok)
    val from200 = CheckoutRules.of(20_000L, false)
    val no = post(from200)
    //  همان پیامِ کامپیوتر (`SalesEngine.checkoutMessage`) که برگه بالای خودش نشان می‌دهد
    val prefix = StrLedger.customerFromRule("\u0000").substringBefore("\u0000")
    assertTrue(no is SalesEngine.Result.Failed && no.message.startsWith(prefix))
    assertTrue("با مشتری ثبت می‌شود", post(from200, cash.copy(debtorId = "d1")) is SalesEngine.Result.Ok)
    assertTrue("آستانهٔ بالاتر از فاکتور ⇒ آزاد", post(CheckoutRules.of(50_000L, false)) is SalesEngine.Result.Ok)
    //  حساب‌پی پیش از گرفتنِ پول همان قاعده را می‌سنجد (همان `checkout` ِ برگه)
    val c = CheckoutModel.checkout(cash, canDiscount = true, blockExpired = false, seller = "", rules = from200)
    assertTrue(HesabTillModel.precheck(ledger, cart, c, "hp-key", "2026-11-12").orEmpty().startsWith(prefix))
    assertNull(HesabTillModel.precheck(ledger, cart, c.copy(debtorId = "d1"), "hp-key", "2026-11-12"))
  }

  @Test
  fun `قاعدهٔ «فاکتورِ صفر» از برگهٔ گوشی تا موتور می‌رسد`() {
    val free = cash.copy(discountText = "100")
    assertTrue("خاموش ⇒ فاکتورِ صفر همان امروز ثبت می‌شود", post(CheckoutRules.OFF, free) is SalesEngine.Result.Ok)
    assertEquals(StrLedger.zeroTotalRule, (post(CheckoutRules.of(null, true), free) as SalesEngine.Result.Failed).message)
    assertTrue(post(CheckoutRules.of(null, true)) is SalesEngine.Result.Ok)
  }
}
