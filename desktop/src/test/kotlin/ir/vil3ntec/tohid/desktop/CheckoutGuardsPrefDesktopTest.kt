package ir.vil3ntec.tohid.desktop

import ir.vil3ntec.tohid.core.credit.CreditGate
import ir.vil3ntec.tohid.core.i18n.StrLedger
import ir.vil3ntec.tohid.core.sale.CheckoutRules
import ir.vil3ntec.tohid.data.CheckoutRulesPref
import ir.vil3ntec.tohid.data.Debtor
import ir.vil3ntec.tohid.data.ExpiryPref
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopPolicy
import ir.vil3ntec.tohid.data.WarehouseEntry
import ir.vil3ntec.tohid.ui.v3.sales.checkout.CheckoutExpiry
import ir.vil3ntec.tohid.ui.v3.sales.checkout.CheckoutForm
import ir.vil3ntec.tohid.ui.v3.sales.checkout.CheckoutModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 *  POS-24 و S22 در صندوقِ گوشیِ نسخهٔ ۳ با **مقدارِ واقعیِ** تنظیمِ همین دستگاه (`tohid-policy` روی پیکربندیِ کامپیوتر):
 *  همان خواندن‌های `CheckoutRoute` (`CheckoutRulesPref.get`، `ExpiryPref.gate`، `ShopPolicy.blockExpired`) ⇐ `CheckoutModel.checkout`
 *  ⇐ `CheckoutExpiry.uncovered` و `SalesEngine.record` (همان `CheckoutModel.legacy` ِ پرداختِ ساده). سیم‌کشیِ خودِ برگه در
 *  `CheckoutRulesWiringTest` و `CheckoutExpiryWiringTest`.
 *  روی `claude/batch-56` (a5bd4667f) `CheckoutModel.checkout` نه `rules` داشت نه `expiryGate` ⇒ هر دو تنظیم در گوشی بی اثر بودند.
 */
class CheckoutGuardsPrefDesktopTest {

  private val prefs get() = DesktopContext.getSharedPreferences("tohid-policy", 0)
  private val keys = listOf("checkout_customer_from_minor", "checkout_block_zero_total", "expiry_approval", "block_expired")
  private var saved: Map<String, Any?> = emptyMap()

  @Before fun before() {
    saved = prefs.all.filterKeys { it in keys }
    clear()
  }

  @After fun after() {
    clear()
    val e = prefs.edit()
    for ((k, v) in saved) when (v) {
      is Long -> e.putLong(k, v)
      is Boolean -> e.putBoolean(k, v)
    }
    e.commit()
  }

  private fun clear() {
    val e = prefs.edit()
    keys.forEach { e.remove(it) }
    e.commit()
  }

  private val today = "2026-11-02"
  private val soap = Product(id = "soap", name = "صابون", unit = "عدد", salePrice = 50.0)
  private val milk = Product(id = "milk", name = "شیر", unit = "عدد", purchasePrice = 50.0, salePrice = 100.0)
  private val d = ShopData(
    products = listOf(soap, milk),
    warehouseEntries = listOf(
      WarehouseEntry(id = "w1", productId = "soap", units = 20.0),
      WarehouseEntry(id = "old", productId = "milk", units = 2.0, expiryDate = "2026-10-30"),
      WarehouseEntry(id = "far", productId = "milk", units = 10.0, expiryDate = "2026-12-12"),
    ),
    debtors = listOf(Debtor(id = "d1", name = "احمد")),
  )
  private var seq = 0

  /** همان `Checkout` ِ `CheckoutRoute` از تنظیمِ واقعیِ همین دستگاه */
  private fun phoneCheckout(form: CheckoutForm): SalesEngine.Checkout = CheckoutModel.checkout(
    form, canDiscount = true, blockExpired = ShopPolicy.blockExpired(DesktopContext), seller = "",
    rules = CheckoutRulesPref.get(DesktopContext), expiryGate = ExpiryPref.gate(DesktopContext),
  )

  private fun post(cart: List<SalesEngine.CartLine>, form: CheckoutForm, c: SalesEngine.Checkout = phoneCheckout(form)): SalesEngine.Result =
    SalesEngine.record(d, cart, CheckoutModel.legacy(c, CheckoutModel.numbers(d, cart, form, c), form), today, 1L, { "g${seq++}" })

  @Test fun `قاعدهٔ «مشتری از مبلغِ X» ِ تنظیم‌شده در گوشی اجرا می‌شود`() {
    val cash = CheckoutModel.start(null, d.debtors)
    val soap6 = listOf(SalesEngine.CartLine("soap", 6.0)) // ۳۰۰ ؋
    assertTrue("تنظیم‌نشده ⇒ همان امروز", post(soap6, cash) is SalesEngine.Result.Ok)
    CheckoutRulesPref.set(DesktopContext, CheckoutRules.of(20_000L, false)) // از ۲۰۰ ؋
    val no = post(soap6, cash)
    assertTrue(no is SalesEngine.Result.Failed && no.message.startsWith(StrLedger.customerFromRule("\u0000").substringBefore("\u0000")))
    assertTrue("با مشتری ثبت می‌شود", post(soap6, cash.copy(debtorId = "d1")) is SalesEngine.Result.Ok)
    assertTrue("زیرِ آستانه آزاد", post(listOf(SalesEngine.CartLine("soap", 3.0)), cash) is SalesEngine.Result.Ok)
  }

  @Test fun `قاعدهٔ «فاکتورِ صفر» ِ تنظیم‌شده در گوشی اجرا می‌شود`() {
    val free = CheckoutModel.start(null, d.debtors).copy(discountText = "100")
    val soap2 = listOf(SalesEngine.CartLine("soap", 2.0))
    assertTrue(post(soap2, free) is SalesEngine.Result.Ok)
    CheckoutRulesPref.set(DesktopContext, CheckoutRules.of(null, true))
    assertEquals(StrLedger.zeroTotalRule, (post(soap2, free) as SalesEngine.Result.Failed).message)
  }

  @Test fun `«با مجوزِ مدیر» ِ تنظیم‌شده فروشِ منقضی را در گوشی بی تأیید نمی‌گذارد`() {
    val cash = CheckoutModel.start(null, d.debtors)
    val milk11 = listOf(SalesEngine.CartLine("milk", 11.0)) // ده سالم، یکی از ماندهٔ منقضی
    //  «فقط هشدار» (پیش‌فرض) ⇒ همان امروز
    assertNull(CheckoutExpiry.uncovered(d, milk11, phoneCheckout(cash), today))
    assertTrue(post(milk11, cash) is SalesEngine.Result.Ok)
    ExpiryPref.setApproval(DesktopContext, true)
    val gate = phoneCheckout(cash)
    assertTrue(gate.expiryGate)
    assertEquals(mapOf("milk" to 1_000L), CheckoutExpiry.uncovered(d, milk11, gate, today))
    assertTrue("بی تأیید هیچ ثبتی", post(milk11, cash, gate) is SalesEngine.Result.Failed)
    val ok = CheckoutExpiry.approval(CreditGate.Approval("مدیر", "manager", "", 0L, "مشتری خواست"), mapOf("milk" to 1_000L))
    val sold = post(milk11, cash, gate.copy(expiryApproval = ok)) as SalesEngine.Result.Ok
    assertEquals("مدیر", sold.data.auditLog.single { it.type == "expired_sale" }.approvedBy)
    //  «فروخته نشود» روشن ⇒ دروازه خاموش و فروشِ منقضی همیشه بسته، حتی با تأیید
    ShopPolicy.setBlockExpired(DesktopContext, true)
    val blocked = phoneCheckout(cash)
    assertTrue(!blocked.expiryGate && blocked.blockExpired)
    assertTrue(post(milk11, cash, blocked.copy(expiryApproval = ok)) is SalesEngine.Result.Failed)
  }
}
