package ir.vil3ntec.tohid.ui.v3.settings.sections

import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.core.promo.PromoHeld
import ir.vil3ntec.tohid.ui.v3.nav.V3Modules
import ir.vil3ntec.tohid.ui.v3.settings.SettingsPaths
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  PRM-02 — صفحهٔ «کدهای تخفیفِ نگه‌داشته»: خاموش/بی اجازه/گذرا از پاسخِ سرور، آزاد کردن فقط یک بار در هر لحظه،
 *  و پس از آزاد شدن حالِ تخفیف‌ها و فهرست از نو؛ ردیف و مسیر در تنظیماتِ فروش ثبت‌اند.
 */
class PromoHeldStateTest {

  private class Fake(var list: ApiResult<PromoHeld.Page>, var reply: ApiResult<Int> = ApiResult.Success(1)) : PromoHeldSource {
    val released = ArrayList<String>()
    var changes = 0
    var loads = 0
    override suspend fun held(): ApiResult<PromoHeld.Page> { loads++; return list }
    override suspend fun release(saleId: String): ApiResult<Int> { released += saleId; return reply }
    override suspend fun changed() { changes++ }
  }

  private val page = PromoHeld.Page(listOf(PromoHeld.Row("S1", "EID5", "r1", 1000)), more = false)
  private fun fail(f: ApiFailure, status: Int): ApiResult<Nothing> = ApiResult.Failure(f.withStatus(status))

  @Test
  fun `بی درِ سرور یا بی اجازه فقط «صاحب یا مدیر»؛ پرچمِ خاموش یا سرورِ کهنه «روشن نیست»؛ گذرا «دوباره»`() = runBlocking {
    val none = PromoHeldState(null)
    none.load()
    assertEquals(PromoHeld.Access.DENIED, none.access)
    assertNull(none.release("S1"))

    val denied = PromoHeldState(Fake(fail(ApiFailure.Forbidden("نه", "release_not_allowed"), 403)))
    denied.load()
    assertEquals(PromoHeld.Access.DENIED, denied.access)

    val off = PromoHeldState(Fake(fail(ApiFailure.Forbidden("خاموش", "promo_uses_disabled"), 403)))
    off.load()
    assertEquals(PromoHeld.Access.OFF, off.access)
    val old = PromoHeldState(Fake(fail(ApiFailure.NotFound("نیست", "not_found"), 404)))
    old.load()
    assertEquals(PromoHeld.Access.OFF, old.access)

    val flaky = Fake(ApiResult.Failure(ApiFailure.Offline()))
    val s = PromoHeldState(flaky)
    s.load()
    assertTrue(s.failed)
    assertNull(s.access)
    assertNull(s.page)
    flaky.list = ApiResult.Success(page)
    s.load()
    assertFalse(s.failed)
    assertEquals(page, s.page)
  }

  @Test
  fun `آزاد کردن ⇒ حالِ تخفیف‌ها و فهرست از نو؛ ناموفق ⇒ همان فهرست`() = runBlocking {
    val src = Fake(ApiResult.Success(page))
    val s = PromoHeldState(src)
    s.load()
    assertEquals(PromoHeld.Done.RELEASED, s.release("S1"))
    assertEquals(listOf("S1"), src.released)
    assertEquals(1, src.changes)
    assertEquals(2, src.loads)
    assertNull(s.busy)

    src.reply = ApiResult.Success(0)
    assertEquals(PromoHeld.Done.NOTHING, s.release("S1"))

    src.reply = ApiResult.Failure(ApiFailure.Offline())
    assertEquals(PromoHeld.Done.FAILED, s.release("S1"))
    assertEquals("ناموفق ⇒ تازه‌سازی نه", 2, src.changes)
    assertEquals(page, s.page)

    src.reply = fail(ApiFailure.Forbidden("خاموش", "promo_uses_disabled"), 403)
    assertEquals(PromoHeld.Done.OFF, s.release("S1"))
    assertEquals(PromoHeld.Access.OFF, s.access)
    assertNull(s.page)
  }

  @Test
  fun `ردیفِ تنظیمات و مسیر — فقط با مدیریت، زیرِ فروش`() {
    val entry = V3Modules.ALL.flatMap { it.settings }.single { it.route == SettingsPaths.PROMO_HELD }
    assertTrue(entry.needs.isNotEmpty())
    assertTrue(!entry.subtitle().isNullOrBlank())
    val route = V3Modules.ALL.flatMap { it.routes }.single { it.pattern == SettingsPaths.PROMO_HELD }
    assertEquals(entry.needs, route.needs)
  }
}
