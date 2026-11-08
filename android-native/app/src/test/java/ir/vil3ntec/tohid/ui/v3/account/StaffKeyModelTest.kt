package ir.vil3ntec.tohid.ui.v3.account

import ir.vil3ntec.tohid.core.model.MemberDto
import ir.vil3ntec.tohid.core.model.StaffKeyDto
import ir.vil3ntec.tohid.core.model.StaffKeysDto
import ir.vil3ntec.tohid.core.model.StaffRejoinHintDto
import ir.vil3ntec.tohid.core.net.ApiEndpoints
import ir.vil3ntec.tohid.core.net.ApiJson
import ir.vil3ntec.tohid.ui.v3.account.team.MemberModel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  دستگاه‌های ورود با کدِ هر کارمند (رازِ دستگاه، `GET /api/shop/staff-keys`) و نشانهٔ «شاید همان <نام> است با دستگاهِ
 *  تازه» — منطقِ خالصِ صفحهٔ کارمند (`MemberModel`). سرور مرجع است: «باطل کردن» فقط با `canRevoke` ِ سرور دیده می‌شود.
 */
class StaffKeyModelTest {

  private val old = MemberDto(id = "mem_old", userId = "usr_old", name = "سمیر", role = "staff")
  private val fresh = MemberDto(id = "mem_new", userId = "usr_new", name = "سمیر", role = "staff")
  private val owner = MemberDto(id = "mem_own", userId = "usr_own", name = "صاحب", role = "owner")

  private val page = StaffKeysDto(
    devices = listOf(
      StaffKeyDto(id = "sdk_1", userId = "usr_new", memberId = "mem_new", label = "گوشیِ سمیر", platform = "android",
        device = "life…ne-1", createdAt = 1_700_000_000_000, lastUsedAt = null),
      StaffKeyDto(id = "sdk_2", userId = "usr_new", memberId = "mem_new", label = " ", platform = "ios",
        device = "abcd…wxyz", createdAt = 1_700_000_500_000, lastUsedAt = 1_700_000_600_000),
      StaffKeyDto(id = "sdk_3", userId = "usr_other", memberId = "mem_other", label = "دیگری", device = "x", createdAt = 1),
    ),
    hints = listOf(
      StaffRejoinHintDto(memberId = "mem_new", userId = "usr_new", oldMemberId = "mem_old", oldUserId = "usr_old",
        oldName = "سمیر", sameName = true, device = "life…ne-1"),
      //  همان عضوِ کهنه از دستگاهِ دوم — یک بار
      StaffRejoinHintDto(memberId = "mem_new", userId = "usr_new", oldMemberId = "mem_old", oldUserId = "usr_old",
        oldName = "سمیر", sameName = true, device = "abcd…wxyz"),
      //  عضوِ کهنه‌ای که دیگر در فهرست نیست (برداشته شد) ⇒ نشانه نمی‌آید
      StaffRejoinHintDto(memberId = "mem_new", userId = "usr_new", oldMemberId = "mem_gone", oldName = "رفته", device = "q"),
    ),
    canRevoke = true,
  )

  @Test
  fun `دستگاه‌های هر عضو فقط مالِ خودش، تازه‌ترین بالا، با برچسب و شناسهٔ کوتاه`() {
    val keys = MemberModel.keysOf(page, fresh)
    assertEquals(listOf("sdk_2", "sdk_1"), keys.map { it.id })
    assertTrue(MemberModel.keysOf(page, old).isEmpty())
    val first = MemberModel.keyRow(keys[1])
    assertEquals("گوشیِ سمیر", first.title)
    assertTrue(first.meta, first.meta.startsWith("life…ne-1 · "))
    assertTrue("هنوز به کار نرفته: ${first.meta}", first.meta.contains("آخرین استفاده: هنوز نه"))
    assertTrue(first.meta.contains("ساخته: "))
    //  برچسبِ خالی ⇒ سکو
    val second = MemberModel.keyRow(keys[0])
    assertEquals("ios", second.title)
    assertFalse(second.meta.contains("هنوز نه"))
  }

  @Test
  fun `باطل کردن فقط با اجازهٔ سرور و هرگز روی صاحب دکان`() {
    assertTrue(MemberModel.canRevoke(page, fresh))
    assertFalse("مدیر: سرور canRevoke=false", MemberModel.canRevoke(page.copy(canRevoke = false), fresh))
    assertFalse("سرورِ قدیمی ⇒ بخش نیست", MemberModel.keysVisible(null, fresh))
    assertFalse(MemberModel.keysVisible(page, owner))
    assertFalse(MemberModel.canRevoke(page, owner))
    assertTrue(MemberModel.keysVisible(page.copy(canRevoke = false), fresh))
  }

  @Test
  fun `نشانهٔ «شاید همان» فقط روی عضوِ تازه، یک بار برای هر عضوِ کهنه، فقط وقتی عضوِ کهنه هنوز هست`() {
    val members = listOf(old, fresh)
    val hints = MemberModel.hintsOf(page, fresh, members)
    assertEquals(1, hints.size)
    val h = hints[0]
    assertEquals("mem_old", h.oldMemberId)
    assertEquals("شاید همان سمیر است با دستگاهِ تازه", h.title)
    assertTrue(h.body, h.body.contains("life…ne-1"))
    assertTrue("همان نام هم نشان داده می‌شود", h.body.endsWith("نامش هم یکی است."))
    assertTrue("روی عضوِ کهنه هیچ", MemberModel.hintsOf(page, old, members).isEmpty())
    assertTrue("عضوِ کهنهٔ برداشته‌شده ⇒ هیچ", MemberModel.hintsOf(page, fresh, listOf(fresh)).isEmpty())
    assertTrue("برداشته (وضعیت) ⇒ هیچ", MemberModel.hintsOf(page, fresh, listOf(old.copy(status = "removed"), fresh)).isEmpty())
    //  نامِ متفاوت: نشانه هست، جملهٔ «همان نام» نه؛ نامِ خالیِ سرور ⇒ نامِ فهرست
    val other = page.copy(hints = listOf(page.hints[0].copy(oldName = "", sameName = false)))
    val h2 = MemberModel.hintsOf(other, fresh, listOf(old.copy(name = "سمیرا"), fresh)).single()
    assertEquals("شاید همان سمیرا است با دستگاهِ تازه", h2.title)
    assertFalse(h2.body.contains("نامش هم یکی است"))
  }

  @Test
  fun `پاسخِ سرور همان شکلِ DTO است و نشانی‌ها درست ساخته می‌شوند`() {
    val body = Json.parseToJsonElement(
      """{"devices":[{"id":"sdk_1","userId":"usr_new","memberId":"mem_new","label":"گوشی","platform":"android",""" +
        """"device":"life…ne-1","createdAt":1700000000000,"lastUsedAt":null}],""" +
        """"hints":[{"memberId":"mem_new","userId":"usr_new","oldMemberId":"mem_old","oldUserId":"usr_old","oldName":"سمیر",""" +
        """"sameName":true,"device":"life…ne-1"}],"canRevoke":false}""",
    ) as JsonObject
    val dto = ApiJson.decode<StaffKeysDto>(body)
    assertEquals("sdk_1", dto.devices.single().id)
    assertEquals(null, dto.devices.single().lastUsedAt)
    assertEquals("mem_old", dto.hints.single().oldMemberId)
    assertFalse(dto.canRevoke)
    assertEquals("/shop/staff-keys", ApiEndpoints.Shop.STAFF_KEYS)
    assertEquals("/shop/staff-keys/sdk_1/revoke", ApiEndpoints.Shop.staffKeyRevoke("sdk_1"))
    assertEquals("/shop/staff-keys/members/a%2Fb/logout-all", ApiEndpoints.Shop.staffKeysLogoutAll("a/b"))
    assertEquals("team/members/mem_new/stale/mem_old", AccountPaths.removeStale("mem_new", "mem_old"))
    assertEquals("team/device-keys/sdk_1/revoke", AccountPaths.revokeDeviceKey("sdk_1"))
  }
}
