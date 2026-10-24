package ir.vil3ntec.tohid.ui.v3.account

import ir.vil3ntec.tohid.core.model.CatalogEntryDto
import ir.vil3ntec.tohid.core.model.DeviceMemberDto
import ir.vil3ntec.tohid.core.model.MemberDto
import ir.vil3ntec.tohid.core.model.MemberPermsDto
import ir.vil3ntec.tohid.core.model.PermStateDto
import ir.vil3ntec.tohid.core.model.ShopDeviceDto
import ir.vil3ntec.tohid.core.model.ShopPermsDto
import ir.vil3ntec.tohid.core.model.StaffCodeDto
import ir.vil3ntec.tohid.ui.theme.V3Status
import ir.vil3ntec.tohid.ui.v3.account.team.MemberModel
import ir.vil3ntec.tohid.ui.v3.account.team.NewCodeModel
import ir.vil3ntec.tohid.ui.v3.account.team.TeamModel
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** کارمندان، کدها، مجوز و دستگاه‌ها — همان قاعده‌های صفحهٔ پیشین */
class TeamModelTest {

  @Test
  fun `سقفِ اعضا فقط وقتی سرور گفته، صاحب برداشتنی نیست`() {
    assertEquals("کارمندان (3 از 5)", TeamModel.staffTitle(3, 5))
    assertEquals("کارمندان", TeamModel.staffTitle(3, 0))
    val owner = TeamModel.memberRow(MemberDto(id = "1", name = "احمد", role = "owner"))
    assertFalse(owner.removable); assertEquals("صاحب دکان", owner.role)
    val staff = TeamModel.memberRow(MemberDto(id = "2", name = " ", role = "staff", status = "disabled", phone = "0700"))
    assertTrue(staff.removable); assertFalse(staff.active); assertEquals("بی‌نام", staff.name); assertEquals("0700", staff.contact)
    assertEquals("این کارمند", TeamModel.removeName(null))
  }

  @Test
  fun `کدها — شمارِ استفاده، بی‌شمار، باطل پایین`() {
    val rows = TeamModel.codeRows(listOf(
      StaffCodeDto(id = "a", hint = "AB", role = "staff", maxUses = 5, usedCount = 5),
      StaffCodeDto(id = "b", hint = "CD", role = "manager", maxUses = 0),
      StaffCodeDto(id = "c", hint = "EF", role = "staff", maxUses = 3, usedCount = 2),
    ))
    assertEquals(listOf("b", "c", "a"), rows.map { it.id })
    assertEquals("CD••••", rows[0].hint); assertEquals("بی‌شمار", rows[0].uses); assertEquals("همیشه", rows[0].expiry)
    assertEquals("2 از 3 بار", rows[1].uses)
    assertTrue(rows[2].dead)
    assertTrue(TeamModel.dead(StaffCodeDto(status = "revoked")))
  }

  @Test
  fun `کدِ تازه — پیک فقط با اجازهٔ سرور و یک‌بارمصرف`() {
    assertEquals(listOf("staff", "manager"), NewCodeModel.roles(courierAllowed = false).map { it.role })
    assertEquals(listOf("staff", "manager", "courier"), NewCodeModel.roles(courierAllowed = true).map { it.role })
    assertEquals("1", NewCodeModel.usesAfterPick("courier", "9"))
    assertEquals("9", NewCodeModel.usesAfterPick("staff", "9"))
    assertEquals(NewCodeModel.Request("staff", 1, 0), NewCodeModel.request("staff", "x", ""))
    assertEquals(NewCodeModel.Request("manager", 0, 7), NewCodeModel.request("manager", "0", " 7 "))
    assertEquals(NewCodeModel.Request("staff", 3, 0), NewCodeModel.request("staff", "3", "0"))
  }

  @Test
  fun `دعوتِ پیک — یک‌بارمصرف و مهلتِ ۱ تا ۷ روز (سرور «همیشه» را رد می‌کند)`() {
    assertTrue(NewCodeModel.courierDays.all { it in 1..7 })
    assertEquals("3", NewCodeModel.daysAfterPick("courier", "0"))
    assertEquals("3", NewCodeModel.daysAfterPick("courier", "30"))
    assertEquals("5", NewCodeModel.daysAfterPick("courier", "5"))
    assertEquals("0", NewCodeModel.daysAfterPick("staff", "0"))
    assertEquals(NewCodeModel.Request("courier", 1, 3), NewCodeModel.request("courier", "9", "0"))
    assertEquals(NewCodeModel.Request("courier", 1, 7), NewCodeModel.request("courier", "1", "7"))
    assertEquals(NewCodeModel.Request("courier", 1, 3), NewCodeModel.request("courier", "1", "8"))
  }

  private val catalog = listOf(
    CatalogEntryDto("settings.write", edit = "restrict", defaults = mapOf("manager" to "allow", "staff" to "deny")),
    CatalogEntryDto("reports.money", edit = "full", defaults = mapOf("manager" to "allow", "staff" to "allow")),
    CatalogEntryDto("sales.discount", edit = "none"),
  )

  @Test
  fun `مجوز — فقط کلیدهایی که سرور می‌پذیرد و نقش دارد`() {
    val staff = MemberPermsDto(id = "2", userId = "u2", role = "staff", effective = mapOf("reports.money" to PermStateDto("allow")))
    val manager = MemberPermsDto(id = "3", userId = "u3", role = "manager", effective = mapOf("settings.write" to PermStateDto("deny")))
    val page = ShopPermsDto(catalog = catalog, members = listOf(staff, manager))
    assertEquals(listOf("reports.money"), MemberModel.permRows(page, staff).map { it.key })
    assertTrue(MemberModel.permRows(page, staff).single().on)
    assertEquals(listOf("settings.write", "reports.money"), MemberModel.permRows(page, manager).map { it.key })
    assertTrue(MemberModel.permRows(page, MemberPermsDto(role = "owner")).isEmpty())
    assertTrue(MemberModel.permRows(page, MemberPermsDto(role = "courier")).isEmpty())
    assertEquals(manager, MemberModel.permsOf(page, MemberDto(id = "x", userId = "u3")))
    assertNull(MemberModel.permsOf(page, MemberDto(id = "x", userId = "")))
  }

  @Test
  fun `یک کلید عوض می‌شود و بقیهٔ لایه می‌ماند`() {
    val layer = buildJsonObject { put("audit.view", buildJsonObject { put("mode", JsonPrimitive("allow")) }) }
    val m = MemberPermsDto(id = "2", role = "staff", layer = layer)
    val out: JsonObject = MemberModel.withMode(m, "reports.money", on = false)
    assertEquals("allow", out["audit.view"]!!.jsonObject["mode"]!!.jsonPrimitive.content)
    assertEquals("deny", out["reports.money"]!!.jsonObject["mode"]!!.jsonPrimitive.content)
  }

  @Test
  fun `دستگاه‌ها — همین کارمند، وضعیت با برچسب، کارِ صاحب دکان`() {
    val mine = ShopDeviceDto(id = "d1", name = "A52", member = DeviceMemberDto(userId = "u2"))
    val blocked = ShopDeviceDto(id = "d2", disabledAt = 5, blockedAttempts = 3, member = DeviceMemberDto(userId = "u2"))
    val current = ShopDeviceDto(id = "d3", current = true, member = DeviceMemberDto(userId = "u2"))
    val other = ShopDeviceDto(id = "d4", member = DeviceMemberDto(userId = "u9"))
    val list = MemberModel.devicesOf(listOf(mine, blocked, current, other), MemberDto(userId = "u2"))
    assertEquals(listOf("d1", "d2", "d3"), list.map { it.id })
    assertEquals(V3Status.DELIVERED to "فعال", MemberModel.pill(mine))
    assertEquals(V3Status.CANCELLED to "از کار افتاده · 3 تلاشِ ردشده", MemberModel.pill(blocked))
    assertEquals(MemberModel.DeviceAction.BLOCK, MemberModel.actionOf(mine, canEdit = true))
    assertEquals(MemberModel.DeviceAction.UNBLOCK, MemberModel.actionOf(blocked, canEdit = true))
    assertNull(MemberModel.actionOf(current, canEdit = true))
    assertNull(MemberModel.actionOf(mine, canEdit = false))
    assertEquals("دستگاه", MemberModel.deviceName(ShopDeviceDto()))
  }
}
