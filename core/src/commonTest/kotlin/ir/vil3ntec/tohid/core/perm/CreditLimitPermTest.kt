package ir.vil3ntec.tohid.core.perm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 *  CRM-03 / §۱۰.۱ — «تغییرِ سقفِ اعتبار» پیش از ذخیره (`CreditLimitPerm`): همان قاعدهٔ `server/src/lib/credit-limit-perm.js`
 *  (فقط تغییرِ مشتریِ موجود؛ «بی سقف» یا عددِ نادرست ⇒ بی‌نهایت؛ «نیازمند تأیید» ندارد) و فقط با پرچمِ روشنِ دکان.
 */
class CreditLimitPermTest {
  private val key = CreditLimitPerm.PERM
  private val deny = listOf(mapOf(key to mapOf("mode" to "deny")))
  private fun cap(minor: Double) = listOf(mapOf(key to mapOf("caps" to mapOf("maxAmount" to minor))))
  private fun on(role: String, layers: List<Any?>) = CreditLimitPerm.Rules(role, layers, enforced = true)

  @Test
  fun flagOffOwnerUnknownRoleOrNewCustomerMeanToday() {
    val off = CreditLimitPerm.Rules("staff", deny, enforced = false)
    assertFalse(off.active)
    assertNull(CreditLimitPerm.check(off, 1000.0, 9000.0, existing = true))
    assertEquals(CreditLimitPerm.Field.OPEN, CreditLimitPerm.field(off, existing = true))
    //  صاحبِ دکان هرگز سنجیده نمی‌شود، حتی با لایهٔ «ممنوع»
    assertFalse(on("owner", deny).active)
    assertNull(CreditLimitPerm.check(on("owner", deny), 1000.0, null, existing = true))
    //  نقشی که این نسخه نمی‌شناسد: سرور خودش می‌سنجد
    assertFalse(on("courier", deny).active)
    //  مشتریِ تازه سنجیده نمی‌شود (همان سرور)
    assertNull(CreditLimitPerm.check(on("staff", deny), null, 5000.0, existing = false))
    assertEquals(CreditLimitPerm.Field.OPEN, CreditLimitPerm.field(on("staff", deny), existing = false))
    assertFalse(CreditLimitPerm.Rules.OFF.active)
  }

  @Test
  fun onlyARealChangeIsANeedAndNoLimitIsInfinite() {
    assertNull(CreditLimitPerm.need(1000.0, 1000.0, existing = true))
    assertNull(CreditLimitPerm.need(null, null, existing = true))
    //  همان Minor (گردِ ۱۰۰×) ⇒ تغییری نیست
    assertNull(CreditLimitPerm.need(1000.0, 1000.004, existing = true))
    assertEquals(CreditLimitPerm.Need(100_000, 50_000, 50_000), CreditLimitPerm.need(1000.0, 500.0, existing = true))
    assertEquals(CreditLimitPerm.Need(null, 50_000, 50_000), CreditLimitPerm.need(null, 500.0, existing = true))
    //  برداشتنِ سقف، منفی یا عددِ نادرست ⇒ بی‌نهایت
    assertEquals(CreditLimitPerm.Need(100_000, null, CreditLimitPerm.INF), CreditLimitPerm.need(1000.0, null, existing = true))
    assertEquals(CreditLimitPerm.INF, CreditLimitPerm.need(1000.0, -1.0, existing = true)!!.amount)
    assertEquals(CreditLimitPerm.INF, CreditLimitPerm.need(1000.0, Double.NaN, existing = true)!!.amount)
    assertEquals(CreditLimitPerm.INF, CreditLimitPerm.need(1000.0, 1e300, existing = true)!!.amount)
    assertNull(CreditLimitPerm.need(1000.0, 2000.0, existing = false))
    assertEquals(mapOf("amount" to 50_000.0), CreditLimitPerm.req(CreditLimitPerm.need(null, 500.0, existing = true)!!))
  }

  @Test
  fun denyModeLocksTheFieldAndRefusesAnyChange() {
    val r = on("staff", deny)
    assertTrue(r.active)
    assertEquals(CreditLimitPerm.Field(locked = true, capMinor = null), CreditLimitPerm.field(r, existing = true))
    val d = assertNotNull(CreditLimitPerm.check(r, 1000.0, 500.0, existing = true))
    assertEquals("deny", d.decision)
    assertEquals("denied", d.reason)
    //  سقفِ عوض‌نشده (مثلاً فقط نام) ⇒ آزاد
    assertNull(CreditLimitPerm.check(r, 1000.0, 1000.0, existing = true))
  }

  @Test
  fun capAllowsUpToTheCapAndRefusesAboveOrNoLimit() {
    val r = on("cashier", cap(500_000.0))
    assertEquals(CreditLimitPerm.Field(locked = false, capMinor = 500_000), CreditLimitPerm.field(r, existing = true))
    assertNull(CreditLimitPerm.check(r, null, 5000.0, existing = true))
    assertNull(CreditLimitPerm.check(r, 1000.0, 0.0, existing = true))
    val over = assertNotNull(CreditLimitPerm.check(r, null, 5000.01, existing = true))
    assertEquals("over_cap", over.reason)
    assertEquals("maxAmount", over.cap)
    assertEquals(500_000.0, over.limit)
    assertEquals("over_cap", CreditLimitPerm.check(r, 1000.0, null, existing = true)?.reason)
  }

  @Test
  fun defaultsAreTodayAndApproveIsNotOffered() {
    //  پیش‌فرضِ شاگرد و صندوق‌دار: مجاز بی سقف — همان امروز، حتی «بی سقف»
    assertNull(CreditLimitPerm.check(on("staff", emptyList()), 1000.0, null, existing = true))
    assertEquals(CreditLimitPerm.Field.OPEN, CreditLimitPerm.field(on("manager", emptyList()), existing = true))
    //  انباردار پیش‌فرض «ممنوع» (همان `perm-v1.json`)
    assertTrue(CreditLimitPerm.field(on("storekeeper", emptyList()), existing = true).locked)
    assertEquals("deny", CreditLimitPerm.check(on("storekeeper", emptyList()), null, 10.0, existing = true)?.decision)
    //  «نیازمند تأیید» برای این کلید نیست ⇒ ممنوع؛ هیچ تصمیمِ «approve» بیرون نمی‌آید
    val approve = on("staff", listOf(mapOf(key to mapOf("mode" to "approve"))))
    assertTrue(CreditLimitPerm.field(approve, existing = true).locked)
    assertEquals("deny", CreditLimitPerm.check(approve, 1000.0, 500.0, existing = true)?.decision)
    assertEquals("deny", CreditLimitPerm.check(on("staff", cap(100.0)), null, 500.0, existing = true)?.decision)
  }
}
