package ir.vil3ntec.tohid.ui.v3.account

import ir.vil3ntec.tohid.core.model.MemberDto
import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.core.net.HttpEngine
import ir.vil3ntec.tohid.core.net.TestHttpServer
import ir.vil3ntec.tohid.core.net.TokenStorage
import ir.vil3ntec.tohid.data.repo.TillPinMemberDto
import ir.vil3ntec.tohid.data.repo.TillPinRepository
import ir.vil3ntec.tohid.ui.v3.account.team.TillPinMemberModel
import ir.vil3ntec.tohid.ui.v3.account.team.TillPinMemberModel.State
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Collections

/**
 *  STF-04 — «PIN ِ صندوق» ِ هر کارمند در صفحهٔ کارمند (`/api/shop/till-pin/members`، `…/members/:id/unlock`،
 *  `DELETE …/members/:id`؛ پرچمِ `shop.till_pin`): حال از سرور، و «باز کردنِ قفل» / «برداشتنِ PIN» فقط با همان
 *  اختیارِ سرور (`till-pin.mayManage`: صاحب همه را، مدیر فقط نقش‌های پایین‌تر؛ هیچ‌کس برای خودش — خودش PIN ِ تازه
 *  می‌گذارد). سرور همان را دوباره می‌سنجد و تأییدِ دوبارهٔ ورود می‌خواهد.
 */
class TillPinMemberModelTest {

  private lateinit var server: TestHttpServer

  @Before fun start() { server = TestHttpServer() }

  @After fun stop() = server.close()

  private fun repo(): TillPinRepository {
    val tokens = object : TokenStorage {
      override var accessToken: String? = "t"
      override var refreshToken: String? = null
      override var accessExpiresAt: Long = Long.MAX_VALUE
      override fun save(access: String?, refresh: String?, expiresAt: Long) {}
      override fun clear() {}
    }
    return TillPinRepository(ApiClient(HttpEngine(baseUrl = { server.baseUrl }, allowInsecure = true), tokens))
  }

  private val staff = MemberDto(id = "m-staff", userId = "u-staff", name = "شاگرد", role = "staff")
  private val manager = MemberDto(id = "m-mgr", userId = "u-mgr", name = "مدیر", role = "manager")
  private fun pin(id: String, set: Boolean = true, locked: Boolean = false, void: Boolean = false) =
    TillPinMemberDto(memberId = id, pinSet = set, locked = locked, lockedUntil = if (locked) 9_000L else null, invalidated = void)

  @Test fun `پرچمِ خاموش یا بی اجازه ⇒ هیچ بخشی`() {
    assertNull(TillPinMemberModel.rowOf(null, staff, viewerRole = "owner", viewerUserId = "u-owner"))
    assertNull("عضوی که در فهرستِ سرور نیست", TillPinMemberModel.rowOf(listOf(pin("m-other")), staff, "owner", "u-owner"))
  }

  @Test fun `قفل — صاحب و مدیر (برای نقشِ پایین‌تر) باز می‌کنند؛ مدیر برای مدیر و هیچ‌کس برای خودش نه`() {
    val pins = listOf(pin("m-staff", locked = true), pin("m-mgr", locked = true))
    val byOwner = TillPinMemberModel.rowOf(pins, staff, "owner", "u-owner")!!
    assertEquals(State.LOCKED, byOwner.state)
    assertEquals(9_000L, byOwner.lockedUntil)
    assertTrue(byOwner.canUnlock)
    assertTrue(byOwner.canRemove)
    assertTrue(TillPinMemberModel.rowOf(pins, staff, "manager", "u-mgr")!!.canUnlock)
    assertFalse(TillPinMemberModel.rowOf(pins, manager, "manager", "u-other-mgr")!!.canUnlock)
    assertTrue(TillPinMemberModel.rowOf(pins, manager, "owner", "u-owner")!!.canUnlock)
    val self = TillPinMemberModel.rowOf(pins, manager, "manager", "u-mgr")!!
    assertFalse(self.canUnlock)
    assertFalse(self.canRemove)
    assertFalse(TillPinMemberModel.rowOf(pins, staff, "cashier", "u-c")!!.canUnlock)
  }

  @Test fun `گذاشته‌نشده، باطل و گذاشته`() {
    val none = TillPinMemberModel.rowOf(listOf(pin("m-staff", set = false)), staff, "owner", "u-owner")!!
    assertEquals(State.NONE, none.state)
    assertFalse(none.canUnlock)
    assertFalse(none.canRemove)
    assertEquals(State.INVALIDATED, TillPinMemberModel.rowOf(listOf(pin("m-staff", set = false, void = true)), staff, "owner", "u-owner")!!.state)
    val set = TillPinMemberModel.rowOf(listOf(pin("m-staff")), staff, "owner", "u-owner")!!
    assertEquals(State.SET, set.state)
    assertFalse("قفل نیست ⇒ چیزی برای باز کردن نیست", set.canUnlock)
    assertTrue(set.canRemove)
  }

  @Test fun `فهرست، باز کردنِ قفل و برداشتن همان درهای سرور`(): Unit = runBlocking {
    val seen = Collections.synchronizedList(ArrayList<TestHttpServer.Request>())
    server.on("/api/v1/shop/till-pin/members") { r ->
      seen += r
      200 to """{"members":[{"memberId":"m-staff","userId":"u-staff","name":"شاگرد","role":"staff","pinSet":true,"invalidated":false,"setAt":1,"locked":true,"lockedUntil":9000}]}"""
    }
    server.on("/api/v1/shop/till-pin/members/m-staff/unlock") { r -> seen += r; 200 to """{"ok":true}""" }
    server.on("/api/v1/shop/till-pin/members/m-staff") { r -> seen += r; 200 to """{"ok":true}""" }
    val r = repo()
    val list = r.members()
    assertTrue(list is ApiResult.Success)
    assertEquals(listOf(pin("m-staff", locked = true).copy(userId = "u-staff", name = "شاگرد", role = "staff", setAt = 1)), (list as ApiResult.Success).value)
    assertTrue(r.unlock("m-staff") is ApiResult.Success)
    assertTrue(r.remove("m-staff") is ApiResult.Success)
    assertEquals(listOf("GET", "POST", "DELETE"), seen.map { it.method })
  }
}
