package ir.vil3ntec.tohid.ui.v3.account

import ir.vil3ntec.tohid.core.nav.Access
import ir.vil3ntec.tohid.core.nav.Need
import ir.vil3ntec.tohid.ui.v3.nav.RouteKind
import ir.vil3ntec.tohid.ui.v3.nav.V3Modules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** هر مسیرِ گروهِ حساب با پیوند به خودش می‌رسد، پشتهٔ برگشتِ درست دارد و نقش را رعایت می‌کند */
class AccountRoutesTest {
  private val table = V3Modules.table

  @Test
  fun `پیوندِ سرد به جای درست و برگشتِ درست`() {
    assertEquals(listOf("profile", "profile/edit"), table.chain("profile/edit"))
    assertEquals(listOf("profile", "profile/signout"), table.chain("profile/signout"))
    assertEquals(listOf("team", "team/members/7", "team/members/7/remove"), table.chain("team/members/7/remove"))
    assertEquals(listOf("team", "team/codes/reveal"), table.chain("team/codes/reveal"))
    assertEquals(listOf("subscription", "subscription/plans", "subscription/redeem"), table.chain("subscription/redeem"))
    assertEquals("subscription/plans", table.canonical("vip"))
    assertEquals("subscription", table.canonical("myplan"))
  }

  @Test
  fun `گونه‌ها و نقش`() {
    assertEquals(RouteKind.TAB, table.resolve("profile")!!.route.kind)
    assertEquals(RouteKind.SHEET, table.resolve("team/codes/new")!!.route.kind)
    assertEquals("7", table.resolve("team/members/7")!!.args["id"])
    val seller = Access(granted = Need.entries.toSet() - Need.MANAGE)
    for (p in listOf("team", "team/codes/new", "team/members/7", "team/devices/d/block")) assertFalse(p, table.resolve(p)!!.allowed(seller))
    for (p in listOf("profile", "profile/edit", "subscription", "subscription/plans", "support")) assertTrue(p, table.resolve(p)!!.allowed(seller))
  }
}
