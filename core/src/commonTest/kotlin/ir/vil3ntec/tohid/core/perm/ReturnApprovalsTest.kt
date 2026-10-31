package ir.vil3ntec.tohid.core.perm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 *  RET-04، RET-10، STF-02 (S14) — نیاز، سقفِ تجمعی و اختیارِ تأییدکنندهٔ برگشت.
 *  عددها از قاعدهٔ سرور (`groupReturns`، `cumulativeNeed`) نوشته شده‌اند، نه از خروجیِ کد.
 */
class ReturnApprovalsTest {

  private fun slip(doc: String, amount: Long?) = ApprovalSlip.Slip(
    shop = "shop-1", doc = doc, perm = "returns.create", pctBp = null, amount = amount,
    by = "u-owner", key = "k-1", iat = 1_000L, exp = 61_000L, nonce = "abcdefghijklmnop", reason = "کالای خراب", sig = "sig",
  )

  @Test
  fun needIsTheReturnDocumentOfTheServer() {
    val n = ReturnApprovals.need("doc-7", 36_000L)
    assertEquals("returns.create", n.perm)
    assertEquals("returns/doc-7", n.doc)
    assertEquals(36_000L, n.amount)
    assertNull(n.pctBp)
  }

  @Test
  fun slipCoversOnlyItsOwnDocumentUpToItsAmount() {
    assertNull(ReturnApprovals.covers(slip("returns/doc-7", 36_000L), "doc-7", 36_000L))
    assertNull(ReturnApprovals.covers(slip("returns/doc-7", 50_000L), "doc-7", 36_000L))
    assertEquals("approval_too_small", ReturnApprovals.covers(slip("returns/doc-7", 35_999L), "doc-7", 36_000L))
    assertEquals("approval_too_small", ReturnApprovals.covers(slip("returns/doc-7", null), "doc-7", 36_000L))
    assertEquals("approval_mismatch", ReturnApprovals.covers(slip("returns/doc-8", 36_000L), "doc-7", 36_000L))
    assertEquals("approval_mismatch", ReturnApprovals.covers(slip("sales/doc-7", 36_000L), "doc-7", 36_000L))
  }

  @Test
  fun capDecisionCountsEarlierReturnsOfTheSameInvoice() {
    //  سقفِ ۱۰۰ افغانی: ۸۰ پیش‌تر + ۳۰ حالا = ۱۱۰ ⇒ بالای سقف، هرچند خودِ این سند ۳۰ است
    assertEquals(11_000L, ReturnApprovals.cumulative(8_000L, 3_000L))
    assertEquals(3_000L, ReturnApprovals.cumulative(0L, 3_000L))
    assertEquals(3_000L, ReturnApprovals.cumulative(-500L, 3_000L))
    assertEquals(Long.MAX_VALUE, ReturnApprovals.cumulative(Long.MAX_VALUE - 1, 5L))
    val layers = listOf(mapOf("returns.create" to mapOf("mode" to "allow", "caps" to mapOf("maxAmount" to 10_000))))
    assertEquals("allow", PermCore.evaluate("staff", layers, "returns.create", mapOf("amount" to 3_000.0)).decision)
    val total = ReturnApprovals.cumulative(8_000L, 3_000L).toDouble()
    assertEquals("approve", PermCore.evaluate("staff", layers, "returns.create", mapOf("amount" to total)).decision)
  }

  @Test
  fun deviceLimitIsCumulativePerInvoiceLikeTheRoleCap() {
    //  سقفِ دستگاه ۱۰۰۰ افغانی: یک سندِ ۱۱۰۰ و سه سندِ ۳۰۰+۴۰۰+۴۰۰ هر دو بالای سقف‌اند
    assertTrue(ReturnApprovals.overDeviceLimit(0L, 110_000L, 100_000L))
    assertFalse(ReturnApprovals.overDeviceLimit(0L, 30_000L, 100_000L))
    assertFalse(ReturnApprovals.overDeviceLimit(30_000L, 40_000L, 100_000L))
    assertTrue(ReturnApprovals.overDeviceLimit(70_000L, 40_000L, 100_000L))
    //  درست همان سقف آزاد است؛ یک پول بیشتر نه
    assertFalse(ReturnApprovals.overDeviceLimit(70_000L, 30_000L, 100_000L))
    assertTrue(ReturnApprovals.overDeviceLimit(70_000L, 30_001L, 100_000L))
    //  سقفِ ۰ یا منفی ⇒ بی سقف؛ پیشینِ منفی صفر؛ جمعِ بزرگ سرریز نمی‌کند و بالای سقف است
    assertFalse(ReturnApprovals.overDeviceLimit(Long.MAX_VALUE, 1L, 0L))
    assertFalse(ReturnApprovals.overDeviceLimit(Long.MAX_VALUE, 1L, -5L))
    assertFalse(ReturnApprovals.overDeviceLimit(-50_000L, 30_000L, 30_000L))
    assertTrue(ReturnApprovals.overDeviceLimit(Long.MAX_VALUE - 1, 5L, 100_000L))
  }

  @Test
  fun onlyManagerOrOwnerApproveAndOverCapNeedsOwner() {
    assertTrue(ReturnApprovals.approverCovers("manager", "manager"))
    assertTrue(ReturnApprovals.approverCovers("owner", "manager"))
    assertTrue(ReturnApprovals.approverCovers("owner", "owner"))
    assertTrue(ReturnApprovals.approverCovers(" Owner ", "owner"))
    assertFalse(ReturnApprovals.approverCovers("manager", "owner"))
    assertFalse(ReturnApprovals.approverCovers("staff", "manager"))
    assertFalse(ReturnApprovals.approverCovers("cashier", "manager"))
    assertFalse(ReturnApprovals.approverCovers(null, "manager"))
    //  نیازِ ناشناخته ⇒ فقط صاحب
    assertFalse(ReturnApprovals.approverCovers("manager", "boss"))
    assertTrue(ReturnApprovals.approverCovers("owner", null))
  }
}
