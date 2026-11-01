package ir.vil3ntec.tohid.core.perm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 *  PAY-15 — مجوزِ جدای گزارشِ X و Z (`shift.report`): پیش‌فرض همان امروز (همه می‌بینند؛ شاگرد نمای کور)، صاحب برای
 *  مدیر یا شاگرد می‌بندد، «نیازمند تأیید» ندارد و بالاتر از نقش نمی‌رود. برابری با JS در بردارهای `perm-v1.json`.
 */
class ShiftReportPermTest {
  private val key = "shift.report"

  @Test
  fun defaultIsTodayForEveryRole() {
    assertTrue(key in PermCore.keys())
    //  سه نقشِ قدیم همان امروز؛ نقش‌های تازه (STF-01): صندوق‌دار و حسابدار می‌بینند، انباردار و جمع‌آور نه
    val want = mapOf(
      "owner" to "allow", "manager" to "allow", "staff" to "allow",
      "cashier" to "allow", "storekeeper" to "deny", "picker" to "deny", "accountant" to "allow",
    )
    assertEquals(want.keys, PermCore.ROLES.toSet())
    for (role in PermCore.ROLES) assertEquals(want[role], PermCore.defaultMode(role, key), role)
    assertEquals("allow", PermCore.evaluate("staff", emptyList(), key).decision)
  }

  @Test
  fun ownerCanCloseItForStaffOrManagerButNotForHimself() {
    val deny = listOf(mapOf(key to mapOf("mode" to "deny")))
    assertEquals("deny", PermCore.resolve("staff", deny, key).mode)
    assertEquals("deny", PermCore.resolve("manager", deny, key).mode)
    assertEquals("allow", PermCore.resolve("owner", deny, key).mode)
  }

  @Test
  fun approveIsNotOfferedAndBecomesDeny() {
    val approve = listOf(mapOf(key to mapOf("mode" to "approve")))
    assertEquals("deny", PermCore.resolve("staff", approve, key).mode)
    assertEquals("deny", PermCore.evaluate("staff", approve, key).decision)
  }
}
