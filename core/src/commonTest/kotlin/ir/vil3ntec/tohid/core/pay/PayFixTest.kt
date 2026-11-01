package ir.vil3ntec.tohid.core.pay

import ir.vil3ntec.tohid.core.perm.PermCore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 *  POS-40 — اصلاحِ روشِ پرداخت پس از فروش (`PayFix`): سندِ جدا، جمعِ فاکتور همان، روزِ بسته رد، مجوزِ
 *  `sales.pay_fix` ِ تازه. بردارهای مشترک با JS در `PayFixVectorsTest` (jvmTest)؛ این‌جا رفتارِ کلی روی
 *  JVM و شبیه‌سازِ iOS.
 */
class PayFixTest {
  private val t0 = 1_760_000_000_000L
  private val sale = PayFix.SaleInfo("s1", listOf(PayFix.Row("cash", 70_000), PayFix.Row("hesabpay", 30_000)))

  private fun input(id: String, from: String, to: String, amount: Long, existing: List<PayFix.Fix> = emptyList(), at: Long = t0) =
    PayFix.Input(
      id = id, sale = sale, from = from, to = to, amount = amount, at = at, by = "m1", decision = "allow",
      reason = "اشتباهِ صندوق", ref = if (to == "cash") null else "R-$id", existing = existing,
    )

  @Test
  fun chainOfFixesKeepsTheInvoiceTotalAndNeverGoesNegative() {
    val fixes = ArrayList<PayFix.Fix>()
    listOf(
      Triple("cash", "afpay_card", 50_000L), Triple("hesabpay", "cash", 30_000L), Triple("afpay_card", "hawala", 50_000L),
    ).forEachIndexed { i, (from, to, amount) ->
      val r = PayFix.check(input("f$i", from, to, amount, fixes, t0 + i))
      assertIs<PayResult.Ok<PayFix.Fix>>(r, "گام ${i + 1}: $r")
      fixes += r.value
    }
    val out = PayFix.apply("s1", sale.rows, fixes)
    assertEquals(listOf(PayFix.Row("cash", 50_000), PayFix.Row("hawala", 50_000)), out.rows)
    assertEquals(100_000L, out.rows.sumOf { it.amount })
    //  نقدِ ماندهٔ ۵۰۰ افغانی است؛ ۵۰۰٫۰۱ دیگر جا ندارد
    assertEquals("FIX_EXCEEDS", (PayFix.check(input("f9", "cash", "hesabpay", 50_001, fixes)) as PayResult.Err).code)
  }

  @Test
  fun closedDayIsRejectedEvenWithFullPermission() {
    val closed = PayFix.closed(t0, "2026-11-01", listOf(PayFix.Window(t0 - 1000, t0 + 1000, "2026-11-01")))
    assertTrue(closed)
    val r = PayFix.check(input("f1", "cash", "hesabpay", 1000).copy(closed = closed))
    assertEquals("DAY_CLOSED", (r as PayResult.Err).code)
  }

  @Test
  fun permissionDefaultsAreNewAndApprovable() {
    assertTrue(PayFix.PERM in PermCore.keys())
    val want = mapOf(
      "owner" to "allow", "manager" to "allow", "staff" to "approve", "cashier" to "approve",
      "storekeeper" to "deny", "picker" to "deny", "accountant" to "deny",
    )
    assertEquals(want.keys, PermCore.ROLES.toSet())
    for (role in PermCore.ROLES) assertEquals(want[role], PermCore.defaultMode(role, PayFix.PERM), role)
    //  لایه شاگرد را «مجاز» نمی‌کند؛ صاحب همیشه همه‌کاره
    assertEquals("approve", PermCore.evaluate("staff", listOf(mapOf(PayFix.PERM to mapOf("mode" to "allow"))), PayFix.PERM).decision)
    assertEquals("allow", PermCore.evaluate("owner", listOf(mapOf(PayFix.PERM to mapOf("mode" to "deny"))), PayFix.PERM).decision)
    //  تصمیمِ «نیازمند تأیید» ِ شاگرد بی تأییدکننده رد می‌شود
    val staff = input("f1", "cash", "hesabpay", 1000).copy(by = "st1", decision = PermCore.evaluate("staff", emptyList(), PayFix.PERM).decision)
    assertEquals("APPROVAL_REQUIRED", (PayFix.check(staff) as PayResult.Err).code)
    assertIs<PayResult.Ok<PayFix.Fix>>(PayFix.check(staff.copy(approvedBy = "m1")))
  }
}
