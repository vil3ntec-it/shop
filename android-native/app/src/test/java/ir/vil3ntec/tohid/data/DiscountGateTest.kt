package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.perm.ApprovalSlip
import ir.vil3ntec.tohid.core.perm.PermCore
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/** دروازهٔ تخفیفِ صندوق (S08) — همان حسابِ سرور */
class DiscountGateTest {

  private val capped = listOf(mapOf("sales.discount" to mapOf("caps" to mapOf("maxPct" to 10.0))))
  private fun decision(role: String, layers: List<Any?>, discount: Double, subtotal: Double) =
    PermCore.evaluate(role, layers, "sales.discount", DiscountGate.req(discount, subtotal)).decision

  private fun slip(pctBp: Long, amount: Long, doc: String = "sales/s-1") = ApprovalSlip.Slip(
    shop = "shp", doc = doc, perm = "sales.discount", pctBp = pctBp, amount = amount, by = "u", key = "ak",
    iat = 1, exp = 2, nonce = "n".repeat(16), reason = "r", sig = "x",
  )

  @Test fun underCapIsFree() {
    //  ۵٪ِ ۳۳۳ افغانی گرد می‌شود ۱۷ — با بخششِ نیم افغانی هنوز «۵٪» است
    assertEquals("allow", decision("manager", capped, 17.0, 333.0))
    assertEquals(DiscountGate.State.FREE, DiscountGate.state("allow", "s-1", 17.0, 333.0, null))
    assertEquals(DiscountGate.State.FREE, DiscountGate.state("deny", "s-1", 0.0, 333.0, null))
  }

  @Test fun overCapNeedsApprovalUntilCovered() {
    assertEquals("approve", decision("manager", capped, 20.0, 100.0))
    assertEquals(DiscountGate.State.NEEDS_APPROVAL, DiscountGate.state("approve", "s-1", 20.0, 100.0, null))
    assertEquals(DiscountGate.State.APPROVED, DiscountGate.state("approve", "s-1", 20.0, 100.0, slip(1950, 2000)))
    //  سبد بزرگ‌تر شد ⇒ مبلغ از برگه گذشت ⇒ دوباره تأیید
    assertEquals(DiscountGate.State.NEEDS_APPROVAL, DiscountGate.state("approve", "s-1", 25.0, 125.0, slip(1950, 2000)))
    assertEquals(DiscountGate.State.NEEDS_APPROVAL, DiscountGate.state("approve", "s-2", 20.0, 100.0, slip(1950, 2000)))
    assertEquals(DiscountGate.State.NEEDS_APPROVAL, DiscountGate.state("approve", null, 20.0, 100.0, slip(1950, 2000)))
  }

  @Test fun staffDefaultsUnchanged() {
    assertEquals("deny", decision("staff", emptyList(), 1.0, 100.0))
    assertEquals(DiscountGate.State.DENIED, DiscountGate.state("deny", "s-1", 1.0, 100.0, null))
    val approve = listOf(mapOf("sales.discount" to mapOf("mode" to "approve")))
    assertEquals("approve", decision("staff", approve, 1.0, 100.0))
  }

  @Test fun needAndJsonMatchServerShape() {
    assertEquals(ApprovalSlip.Need("sales.discount", "sales/s-1", 1950, 2000), DiscountGate.need("s-1", 20.0, 100.0))
    val j = DiscountGate.json(slip(1950, 2000))
    assertEquals("1950", j.getValue("pct_bp").jsonPrimitive.content)
    assertEquals("sales/s-1", j.getValue("doc").jsonPrimitive.content)
    assertEquals(13, j.size)
  }

  /**
   *  S08 سرتاسرِ برنامه: F5 روی ردیف (برگهٔ ردیف با کادرِ تخفیف) قیمتِ ردیف را ۱۰٪ پایین می‌آورد؛ با «قیمت و تخفیفِ ردیف» ِ روشن
   *  همان کاهش تخفیفِ فاکتور شمرده می‌شود ⇒ سقفِ ۵٪ «درخواستِ تأیید» می‌خواهد؛ برگهٔ مدیر آن را می‌پوشاند و روی فاکتور می‌نشیند و
   *  ضدتقلب (RPT-09) فروشنده را «با تأیید» و مدیر را «تأییدکننده» می‌شمارد. خاموش ⇒ همان امروز (ردیف شمرده نمی‌شود).
   */
  @Test fun s08RowDiscountOverCapAsksApprovalAndApproverReachesFraud() {
    val d = ShopData(products = listOf(Product(id = "p1", name = "چای", unit = "عدد", salePrice = 1000.0)))
    val cart = SalesEngine.setLinePrice(listOf(SalesEngine.CartLine("p1", 1.0)), "p1", 900.0)
    val totals = SalesEngine.Totals(subtotal = 900.0, discountValue = 0.0, discount = 0.0, finalTotal = 900.0)
    val b = DiscountGate.basis(true, d, cart, null, totals)
    assertEquals(100.0, b.discount, 0.0)
    assertEquals(1000.0, b.subtotal, 0.0)
    assertEquals(0.0, DiscountGate.basis(false, d, cart, null, totals).discount, 0.0)
    val five = listOf(mapOf("sales.discount" to mapOf("caps" to mapOf("maxPct" to 5.0))))
    val dec = decision("manager", five, b.discount, b.subtotal)
    assertEquals("approve", dec)
    assertEquals(DiscountGate.State.NEEDS_APPROVAL, DiscountGate.state(dec, "s-1", b.discount, b.subtotal, null))
    val ok = slip(1000, 10_000).copy(by = "مدیر")
    assertEquals(DiscountGate.State.APPROVED, DiscountGate.state(dec, "s-1", b.discount, b.subtotal, ok))
    val sale = Sale(
      id = "s-1", total = 900.0, finalTotal = 900.0, paidAmount = 900.0, status = "completed", date = "2026-10-10",
      createdAt = 1, seller = "کریم", approval = DiscountGate.json(ok),
    )
    val r = ir.vil3ntec.tohid.core.report.ReportCore.fraud(ReportBridge.ledgerOf(d.copy(sales = listOf(sale))))
    assertEquals(1, r.rows.single { it.key == "کریم" }.overrideCount)
    assertEquals(1, r.rows.single { it.key == "مدیر" }.approvalCount)
  }
}
