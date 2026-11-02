package ir.vil3ntec.tohid.core.staff

import ir.vil3ntec.tohid.core.staff.TillSwitch.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** S46 — تعویضِ کاربر روی صندوق: نقش، قفل، سبدِ جدا و نامِ سابقه */
class TillSwitchTest {

  private val owner = User("u-owner", "صاحب", "owner")
  private val ali = User("u-ali", "علی", "staff")

  @Test
  fun tillUserNeverGetsMoreThanTheDeviceAccount() {
    assertEquals("staff", TillSwitch.effectiveRole("owner", "staff"))
    assertEquals("manager", TillSwitch.effectiveRole("owner", "manager"))
    //  مدیر پشتِ صندوقِ شاگرد ⇒ همان شاگرد
    assertEquals("staff", TillSwitch.effectiveRole("staff", "manager"))
    assertEquals("staff", TillSwitch.effectiveRole("staff", "owner"))
    assertEquals("owner", TillSwitch.effectiveRole("owner", null))
    assertEquals("owner", TillSwitch.effectiveRole("owner", " "))
  }

  @Test
  fun locksOnlyWhenTheDeviceAccountCanComeBack() {
    assertTrue(TillSwitch.canLock(true, listOf(owner, ali), "u-owner", deviceCode = false))
    assertTrue(TillSwitch.canLock(true, listOf(ali), "u-owner", deviceCode = true))
    assertFalse(TillSwitch.canLock(true, listOf(ali), "u-owner", deviceCode = false))
    assertFalse(TillSwitch.canLock(true, listOf(owner.copy(locked = true), ali), "u-owner", deviceCode = false))
    assertFalse(TillSwitch.canLock(false, listOf(owner, ali), "u-owner", deviceCode = true))
    assertFalse(TillSwitch.canLock(true, emptyList(), "u-owner", deviceCode = true))
  }

  @Test
  fun eachUserHasOwnCartSlot() {
    assertNull(TillSwitch.activeAfter("u-owner", "u-owner"))
    assertEquals("u-ali", TillSwitch.activeAfter("u-owner", "u-ali"))
    assertEquals(TillSwitch.Swap("", "u-ali"), TillSwitch.swap(null, "u-ali"))
    assertEquals(TillSwitch.Swap("u-ali", ""), TillSwitch.swap("u-ali", null))
    assertNull(TillSwitch.swap("u-ali", "u-ali"))
    assertNull(TillSwitch.swap(null, ""))
  }

  @Test
  fun stashFileNamesAreSafeAndDistinct() {
    assertEquals("device.json", TillSwitch.fileName(""))
    val a = TillSwitch.fileName("u-ali")
    val b = TillSwitch.fileName("../u-ali")
    assertTrue(Regex("^u-[0-9a-f]+\\.json$").matches(a), a)
    assertTrue(Regex("^u-[0-9a-f]+\\.json$").matches(b), b)
    assertTrue(a != b)
  }

  @Test
  fun historyCarriesTheTillUsersName() {
    assertEquals("علی", TillSwitch.seller("صاحب", ali))
    assertEquals("صاحب", TillSwitch.seller("صاحب", null))
    assertEquals("صاحب", TillSwitch.seller("صاحب", ali.copy(name = "  ")))
    assertEquals(listOf("u-owner", "u-ali"), TillSwitch.lockList(listOf(ali, owner), "u-owner").map { it.userId })
  }
}
