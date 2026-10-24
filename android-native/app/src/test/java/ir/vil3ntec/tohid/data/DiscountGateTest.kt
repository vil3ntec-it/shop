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
}
