package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.i18n.StrSaleCaps
import ir.vil3ntec.tohid.core.perm.RemoteApproval
import ir.vil3ntec.tohid.core.perm.SaleCaps
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  STF-03 — پیش‌سنجیِ صندوق (`SaleCapGate`، گوشیِ نسخهٔ ۳ و کامپیوتر) روی فاکتورِ واقعیِ `SalesEngine.record`: همان قاعدهٔ
 *  سرور (`SaleCaps`، بردارِ `sale-caps-v1.json`)، فقط برای کلیدی که سرور اجرا می‌کند، برگهٔ «پرسیدن از مدیر» یا PIN
 *  همراهِ فاکتور، و آخرین سد (`guard`) پیش از نوشتنِ دفتر.
 */
class SaleCapGateTest {

  private val rice = Product(id = "p1", name = "برنج", unit = "کیلوگرم", salePrice = 1000.0, wholesalePrice = 800.0)
  private val oil = Product(id = "p2", name = "روغن", unit = "عدد", salePrice = 500.0)
  private var n = 0
  private val newId: () -> String = { "id-${++n}" }

  private fun ledger() = ShopData(
    products = listOf(rice, oil),
    debtors = listOf(Debtor(id = "d1", name = "احمد"), Debtor(id = "d2", name = "عمده‌فروش", priceList = "wholesale")),
    warehouseEntries = listOf(WarehouseEntry(id = "w1", productId = "p1", units = 50.0), WarehouseEntry(id = "w2", productId = "p2", units = 50.0)),
  )

  private fun record(cart: List<SalesEngine.CartLine>, checkout: SalesEngine.Checkout = SalesEngine.Checkout()): SalesEngine.Result.Ok =
    SalesEngine.record(ledger(), cart, checkout, "2026-11-08", 1_760_000_000_000, newId, saleId = "s-1") as SalesEngine.Result.Ok

  private val credit = SalesEngine.Checkout(payment = SalesEngine.Payment.CREDIT, paidAmount = 0.0, debtorId = "d1")
  private fun layer(key: String, v: Map<String, Any?>) = listOf(mapOf(key to v))
  private val creditCap = SaleCapGate.Rules("staff", layer("sales.credit", mapOf("caps" to mapOf("maxAmount" to 30_000.0))), price = false, credit = true)
  private val priceCap = SaleCapGate.Rules("staff", layer("sales.price", mapOf("caps" to mapOf("maxPct" to 5.0))), price = true, credit = false)

  private fun approved(perm: String, pctBp: Long?, amount: Long?) = RemoteApproval.slipOf(
    "shp",
    RemoteApproval.Request(
      id = "apr_" + "0".repeat(23) + "1", perm = perm, doc = "sales/s-1", pctBp = pctBp, amount = amount, reason = "مشتریِ همیشگی",
      status = RemoteApproval.Status.APPROVED, requesterId = "u-staff", createdAt = 1, expiresAt = 2, decidedBy = "u-owner",
    ),
  )!!

  @Test fun flagsOffMeanTodayEvenWithStoredLayers() {
    val r = record(listOf(SalesEngine.CartLine("p2", 1.0, unitPrice = 100.0)), credit)
    val denyAll = listOf(mapOf("sales.credit" to mapOf("mode" to "deny"), "sales.price" to mapOf("mode" to "deny")))
    for (rules in listOf(SaleCapGate.Rules.OFF, SaleCapGate.Rules("staff", denyAll, price = false, credit = false))) {
      assertFalse(rules.active)
      assertTrue(SaleCapGate.check(rules, r.data, r.saleId, emptyMap()).asks.isEmpty())
      assertEquals(r, SaleCapGate.guard(rules, r, emptyMap()))
    }
    //  صاحبِ دکان هرگز
    assertFalse(SaleCapGate.Rules("owner", denyAll, price = true, credit = true).active)
  }

  @Test fun creditOverCapAsksThenRemoteApprovalCoversExactlyThatAmount() {
    val r = record(listOf(SalesEngine.CartLine("p1", 1.0)), credit)
    val c = SaleCapGate.check(creditCap, r.data, r.saleId, emptyMap())
    val b = c.blocked!!
    assertEquals(SaleCaps.CREDIT, b.perm)
    assertEquals(SaleCaps.State.NEEDS_APPROVAL, b.state)
    assertEquals(ir.vil3ntec.tohid.core.perm.ApprovalSlip.Need("sales.credit", "sales/s-1", null, 100_000), b.need)
    assertTrue(SaleCapGate.summary(b).contains(ir.vil3ntec.tohid.money(1000.0)))
    assertTrue(SaleCapGate.summary(b).contains(ir.vil3ntec.tohid.money(300.0)))
    //  آخرین سد: بی برگه نوشته نمی‌شود
    assertEquals(SalesEngine.Result.Failed(StrSaleCaps.needsApproval(StrSaleCaps.creditTitle)), SaleCapGate.guard(creditCap, r, emptyMap()))
    //  «پرسیدن از مدیر» برای همان مبلغ ⇒ پذیرفته و ارجاعِ `{v:1, request}` روی فاکتور
    val slip = approved("sales.credit", null, 100_000)
    assertTrue(SaleCapGate.check(creditCap, r.data, r.saleId, mapOf(SaleCaps.CREDIT to slip)).ok)
    val json = SaleCapGate.json(mapOf(SaleCaps.CREDIT to slip), SaleCaps.CREDIT)!!
    assertEquals(slip.nonce, json.getValue("request").jsonPrimitive.content)
    val withSlip = record(listOf(SalesEngine.CartLine("p1", 1.0)), credit.copy(creditCapSlip = json))
    assertEquals(json, withSlip.data.sales.single().creditApproval)
    assertEquals(withSlip, SaleCapGate.guard(creditCap, withSlip, mapOf(SaleCaps.CREDIT to slip)))
    //  مبلغِ کوچک‌تر نمی‌پوشاند
    assertNotNull(SaleCapGate.check(creditCap, r.data, r.saleId, mapOf(SaleCaps.CREDIT to approved("sales.credit", null, 99_999))).blocked)
    //  نقد (بی قرض) آزاد
    assertTrue(SaleCapGate.check(creditCap, record(listOf(SalesEngine.CartLine("p1", 1.0))).data, "s-1", emptyMap()).ok)
  }

  @Test fun creditDeniedIsAMessageNotAnApproval() {
    val rules = creditCap.copy(layers = layer("sales.credit", mapOf("mode" to "deny")))
    val r = record(listOf(SalesEngine.CartLine("p2", 1.0)), credit)
    val b = SaleCapGate.check(rules, r.data, r.saleId, mapOf(SaleCaps.CREDIT to approved("sales.credit", null, 1_000_000))).blocked!!
    assertEquals(SaleCaps.State.DENIED, b.state)
    assertEquals(StrSaleCaps.creditDenied, SaleCapGate.message(b))
  }

  @Test fun priceChangeAndHiddenDiscountMatchTheServerRule() {
    //  ۲۰٪ بالاتر از فهرست ⇒ تأیید (سقفِ ۵٪)؛ برگهٔ قیمت در `priceApproval`
    val up = record(listOf(SalesEngine.CartLine("p2", 1.0, unitPrice = 600.0)))
    val b = SaleCapGate.check(priceCap, up.data, up.saleId, emptyMap()).blocked!!
    assertEquals(ir.vil3ntec.tohid.core.perm.ApprovalSlip.Need("sales.price", "sales/s-1", 2000, null), b.need)
    assertEquals(SaleCaps.State.NEEDS_APPROVAL, b.state)
    assertTrue(SaleCapGate.check(priceCap, up.data, up.saleId, mapOf(SaleCaps.PRICE to approved("sales.price", 2000, null))).ok)
    val withSlip = record(listOf(SalesEngine.CartLine("p2", 1.0, unitPrice = 600.0)), SalesEngine.Checkout(priceSlip = SaleCapGate.json(mapOf(SaleCaps.PRICE to approved("sales.price", 2000, null)), SaleCaps.PRICE)))
    assertNotNull(withSlip.data.sales.single().priceApproval)
    //  ۵٪ زیرِ سقف؛ قیمتِ عمده فقط برای مشتریِ لیستِ عمده نه تغییر است نه تخفیف (تصمیمِ ۱-۱۱ (۳)؛ آزمونِ پایین)
    assertTrue(SaleCapGate.check(priceCap, record(listOf(SalesEngine.CartLine("p2", 1.0, unitPrice = 525.0))).data, "s-1", emptyMap()).ok)
    assertTrue(SaleCapGate.check(priceCap, record(listOf(SalesEngine.CartLine("p1", 1.0, unitPrice = 800.0)), SalesEngine.Checkout(debtorId = "d2")).data, "s-1", emptyMap()).ok)
    //  «تخفیفِ پنهان»: قیمتِ ردیفِ پایین‌تر برای شاگردی که تخفیف ندارد ⇒ ممنوع (همان `discount_not_allowed` ِ سرور)
    val low = record(listOf(SalesEngine.CartLine("p2", 1.0, unitPrice = 450.0)))
    val asks = SaleCapGate.check(priceCap, low.data, low.saleId, emptyMap()).asks
    assertEquals(listOf("sales.discount" to SaleCaps.State.DENIED, "sales.price" to SaleCaps.State.NEEDS_APPROVAL), asks.map { it.perm to it.state })
    assertEquals(StrSaleCaps.hiddenDenied, SaleCapGate.message(asks.first()))
    assertEquals(5_000L, asks.first().need.amount)
    //  مصرفِ امتیازِ مشتری (ردیفِ `loyalty_spend` ِ همان ثبت) قیمت را پایین آورده بود ⇒ همان سرور، نه تخفیف نه تغییر
    val spend = AuditEntry(id = "s-1-lp-s", type = LoyaltyPoints.SPEND, date = "2026-11-08", refId = "s-1", amount = 50.0)
    assertTrue(SaleCapGate.check(priceCap, low.data.copy(auditLog = low.data.auditLog + spend), low.saleId, emptyMap()).ok)
    assertEquals(5_000L, SaleCapGate.sale(low.data.sales.single(), listOf(spend)).loyaltyMinor)
    //  بی پرچمِ قیمت تخفیف همان `DiscountGate` ِ امروز است و این‌جا شمرده نمی‌شود
    assertTrue(SaleCapGate.check(creditCap, low.data, low.saleId, emptyMap()).ok)
  }

  /** تصمیمِ ۱-۱۱ (۳): قیمتِ عمده فقط در فاکتورِ مشتریِ لیستِ عمده «فهرست» است — همان `sale-caps.isWholesaleDebtor` ِ سرور */
  @Test fun wholesalePriceIsAListPriceOnlyForAWholesaleCustomer() {
    val walkIn = record(listOf(SalesEngine.CartLine("p1", 1.0, unitPrice = 800.0)))
    val asks = SaleCapGate.check(priceCap, walkIn.data, walkIn.saleId, emptyMap()).asks
    assertEquals(listOf("sales.discount" to SaleCaps.State.DENIED, "sales.price" to SaleCaps.State.NEEDS_APPROVAL), asks.map { it.perm to it.state })
    assertEquals(20_000L, asks.first().need.amount)
    assertFalse(SaleCapGate.sale(walkIn.data.sales.single(), debtors = walkIn.data.debtors).wholesale)
    val retail = record(listOf(SalesEngine.CartLine("p1", 1.0, unitPrice = 800.0)), SalesEngine.Checkout(debtorId = "d1"))
    assertNotNull(SaleCapGate.check(priceCap, retail.data, retail.saleId, emptyMap()).blocked)
    val wholesale = record(listOf(SalesEngine.CartLine("p1", 1.0, unitPrice = 800.0)), SalesEngine.Checkout(debtorId = "d2"))
    assertTrue(SaleCapGate.sale(wholesale.data.sales.single(), debtors = wholesale.data.debtors).wholesale)
    assertTrue(SaleCapGate.check(priceCap, wholesale.data, wholesale.saleId, emptyMap()).ok)
  }

  /** تصمیمِ ۱-۱۱ (۲): قلمِ آزاد مجوزِ `sales.free_line` دارد؛ سقف روی قیمتِ واحد، برگه در `freeLineApproval` */
  @Test fun freeLineAsksForItsOwnPermission() {
    val cap = SaleCapGate.Rules("staff", layer("sales.free_line", mapOf("caps" to mapOf("maxAmount" to 5_000.0))), price = true, credit = false)
    val ok = record(listOf(SalesEngine.CartLine("", 3.0, unitPrice = 50.0, label = "کیسه")))
    assertTrue(SaleCapGate.check(cap, ok.data, ok.saleId, emptyMap()).ok)
    val over = record(listOf(SalesEngine.CartLine("", 1.0, unitPrice = 60.0, label = "کیسه")))
    val b = SaleCapGate.check(cap, over.data, over.saleId, emptyMap()).blocked!!
    assertEquals(ir.vil3ntec.tohid.core.perm.ApprovalSlip.Need(SaleCaps.FREE_LINE, "sales/s-1", null, 6_000), b.need)
    assertEquals(SaleCaps.State.NEEDS_APPROVAL, b.state)
    assertEquals(StrSaleCaps.needsApproval(StrSaleCaps.freeLineTitle), SaleCapGate.message(b))
    val slip = approved(SaleCaps.FREE_LINE, null, 6_000)
    assertTrue(SaleCapGate.check(cap, over.data, over.saleId, mapOf(SaleCaps.FREE_LINE to slip)).ok)
    val json = SaleCapGate.json(mapOf(SaleCaps.FREE_LINE to slip), SaleCaps.FREE_LINE)
    val withSlip = record(listOf(SalesEngine.CartLine("", 1.0, unitPrice = 60.0, label = "کیسه")), SalesEngine.Checkout(freeLineSlip = json))
    assertEquals(json, withSlip.data.sales.single().freeLineApproval)
    val denied = cap.copy(layers = layer("sales.free_line", mapOf("mode" to "deny")))
    val d = SaleCapGate.check(denied, ok.data, ok.saleId, emptyMap()).blocked!!
    assertEquals(SaleCaps.State.DENIED, d.state)
    assertEquals(StrSaleCaps.freeLineDenied, SaleCapGate.message(d))
    //  بی پرچمِ قیمت هیچ؛ کالای کارت‌دار قلمِ آزاد نیست
    assertTrue(SaleCapGate.check(denied.copy(price = false, credit = true), ok.data, ok.saleId, emptyMap()).ok)
    assertTrue(SaleCapGate.check(denied, record(listOf(SalesEngine.CartLine("p2", 1.0))).data, "s-1", emptyMap()).ok)
    assertTrue(SaleCaps.FREE_LINE in RemoteApproval.PERMS && SaleCaps.FREE_LINE in ir.vil3ntec.tohid.core.perm.ApprovalSlip.PERMS)
  }

  @Test fun saleJsonUnchangedWithoutSlipsAndPolicyEditableIsRead() {
    val s = record(listOf(SalesEngine.CartLine("p1", 1.0))).data.sales.single()
    val text = Json.encodeToString(Sale.serializer(), s)
    assertFalse(text.contains("creditApproval"))
    assertFalse(text.contains("priceApproval"))
    assertEquals(listOf("sales.discount", "sales.price"), PermPolicy.editable("""{"v":1,"layers":[{},{}],"editable":["sales.discount","sales.price"]}"""))
    assertEquals(emptyList<String>(), PermPolicy.editable(null))
    assertEquals(emptyList<String>(), PermPolicy.editable("{bad"))
    assertNull(SaleCapGate.json(emptyMap(), SaleCaps.CREDIT))
    assertTrue("sales.credit" in RemoteApproval.PERMS && "sales.price" in RemoteApproval.PERMS)
    //  ردیفِ مصرفِ امتیاز همان نامی است که سرور می‌خواند (`sale-caps.LOYALTY_SPEND`، بردار)
    assertEquals("loyalty_spend", LoyaltyPoints.SPEND)
  }
}
