package ir.vil3ntec.tohid.core.perm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 *  STF-02 لغو — «درخواستِ تأیید» فقط با «نیازمند تأیید» و فقط روی Sync v3؛ نیازِ برگه همان سند و مجوزِ سرور
 *  (`needOf` ِ `staff-approval.js`: `sales/<id>`، `sales.cancel`).
 */
class CancelApprovalTest {

  @Test fun offeredOnlyForApproveOnV3() {
    assertTrue(CancelApproval.offered("approve", v3 = true))
    assertFalse(CancelApproval.offered("approve", v3 = false), "دکانِ v1 همان امروز: لغو بسته")
    assertFalse(CancelApproval.offered("deny", v3 = true))
    assertFalse(CancelApproval.offered("allow", v3 = true), "مجاز اصلاً برگه نمی‌خواهد")
  }

  @Test fun needMatchesServerDoc() {
    val n = CancelApproval.need("s-77", 12_500)
    assertEquals("sales.cancel", n.perm)
    assertEquals("sales/s-77", n.doc)
    assertEquals(12_500L, n.amount)
    assertNull(n.pctBp)
    assertTrue(n.perm in ApprovalSlip.PERMS)
  }
}
