package ir.vil3ntec.tohid.desktop

import ir.vil3ntec.tohid.core.i18n.StrOnline
import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.core.storefront.PresenceDeclaration
import ir.vil3ntec.tohid.core.storefront.StorefrontOwner
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.data.repo.PresenceResultDto
import ir.vil3ntec.tohid.data.repo.PresenceStatusDto
import ir.vil3ntec.tohid.data.repo.StorefrontQueue
import ir.vil3ntec.tohid.ui.v3.online.storefront.PresenceSender
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 *  برگهٔ «وضعیت» ِ ویترینِ نسخهٔ ۳ (`PresenceSender`، §۳۱.۲ بندِ ۲) روی دیسکِ واقعیِ کامپیوتر:
 *
 *   PRES-02  اعلامِ نرسیده در صف می‌ماند — به دکانِ همان نشست بسته. پیش‌تر یک خانهٔ بی‌دکان بود
 *            (`pending-presence`) و پس از عوض شدنِ حساب روی همین دستگاه، با باز شدنِ صفحه برای
 *            دکانِ دیگر فرستاده می‌شد (مثلاً «بسته» ِ دکانِ الف روی ویترینِ دکانِ ب).
 *   PRES-09  خطای سرور هرگز «ثبت شد» نمی‌گوید: بی‌نت، نشستِ تمام‌شده و ۴۲۹ صف را نگه می‌دارند
 *            («هنوز تأیید نشده» + «دوباره بفرست»)؛ ردِ ماندگار ⇒ پیامِ خودِ سرور و بیرون از صف.
 *            پیش‌تر نشستِ تمام‌شده و ۴۲۹ اعلام را دور می‌ریختند.
 *   PRES-08  همان `eventId` دوباره می‌رود؛ `applied: false` ⇒ «وضعیتِ تازه‌ترِ دستگاهِ دیگر می‌ماند».
 */
class PresenceSenderDesktopTest {

  private val ctx = DesktopContext
  private val tz = "Asia/Kabul"
  private val receivedAt = 1_760_000_000_000L
  private val sent = mutableListOf<PresenceDeclaration>()
  private var reply: (PresenceDeclaration) -> ApiResult<PresenceResultDto> = { ok() }

  private fun sender() = PresenceSender(ctx) { p -> sent += p; reply(p) }

  private fun ok(applied: Boolean? = true) =
    ApiResult.Success(PresenceResultDto(status = PresenceStatusDto(badge = "closed", mode = "closed"), applied = applied, serverReceivedAt = receivedAt))

  private fun fail(status: Int, body: String = "{}") = ApiResult.Failure(ApiFailure.fromHttp(status, body, authenticated = true))

  private fun signIn(shop: String) {
    ShopRole.forget(ctx)
    ShopRole.rememberShopId(ctx, shop)
  }

  @Before @After fun clean() {
    ctx.getSharedPreferences("tohid-storefront", 0).edit().remove("pending-presence").commit()
    StorefrontQueue.entries.value.forEach { StorefrontQueue.discard(ctx, it.id) }
    ShopRole.forget(ctx)
    sent.clear()
    reply = { ok() }
  }

  @Test fun `اعلامِ نرسیدهٔ دکانِ الف پس از ورود به دکانِ ب نه دیده می‌شود نه فرستاده`() = runBlocking {
    signIn("shop-a")
    reply = { ApiResult.Failure(ApiFailure.Offline()) }
    val a = sender()
    a.declare("closed", null, tz)
    val queued = assertNotNull_(a.pending)
    assertEquals(StrOnline.presenceNotConfirmed, a.notice)

    signIn("shop-b")
    reply = { ok() }
    val b = sender()
    assertNull("اعلامِ دکانِ الف در صفحهٔ دکانِ ب نیست", b.pending)
    b.flush(tz)
    assertEquals("هیچ اعلامی برای دکانِ ب فرستاده نشد", 1, sent.size)

    signIn("shop-a")
    val a2 = sender()
    assertEquals("دکانِ الف برگشت ⇒ همان اعلام هنوز در صف", queued.eventId, a2.pending?.eventId)
    a2.flush(tz)
    assertEquals(2, sent.size)
    assertEquals("همان شناسه دوباره رفت", queued.eventId, sent[1].eventId)
    assertNull(a2.pending)
    assertEquals(1, a2.confirmed)
  }

  @Test fun `نشستِ تمام‌شده و ۴۲۹ اعلام را دور نمی‌ریزند؛ «ثبت شد» فقط با پاسخِ سرور`() = runBlocking {
    signIn("shop-a")
    reply = { fail(401) }
    val s = sender()
    s.declare("online", null, tz)
    assertNotNull("نشستِ تمام‌شده ⇒ اعلام در صف می‌ماند", s.pending)
    assertEquals(StrOnline.presenceNotConfirmed, s.notice)
    assertEquals(0, s.confirmed)

    reply = { fail(429, """{"error":{"code":"rate_limited","message":"زیاد"}}""") }
    s.flush(tz)
    assertNotNull("۴۲۹ ⇒ اعلام در صف می‌ماند", s.pending)
    assertEquals(StrOnline.presenceNotConfirmed, s.notice)

    reply = { fail(503) }
    s.flush(tz)
    assertNotNull(s.pending)

    reply = { ok() }
    s.flush(tz)
    assertNull(s.pending)
    assertEquals(1, s.confirmed)
    assertEquals(StrOnline.presenceReceipt(StorefrontOwner.clock(receivedAt, tz)), s.notice)
    assertEquals("هر چهار بار همان رویداد", 1, sent.map { it.eventId }.distinct().size)
    assertEquals(4, sent.size)
  }

  @Test fun `ردِ ماندگارِ سرور — پیامِ خودِ سرور، بیرون از صف و بی «ثبت شد»`() = runBlocking {
    signIn("shop-a")
    reply = { fail(400, """{"error":{"code":"bad_mode","message":"وضعیت معتبر نیست"}}""") }
    val s = sender()
    s.declare("closed", null, tz)
    assertNull(s.pending)
    assertEquals("وضعیت معتبر نیست", s.notice)
    assertEquals(0, s.confirmed)
    assertNull("ردیفی در صفِ دیسک نمانده", StorefrontQueue.pendingAll(ctx).firstOrNull())
  }

  @Test fun `وضعیتِ تازه‌ترِ دستگاهِ دیگر جلوتر است ⇒ همین گفته می‌شود، نه رسیدِ ساده`() = runBlocking {
    signIn("shop-a")
    reply = { ok(applied = false) }
    val s = sender()
    s.declare("offline", null, tz)
    assertNull(s.pending)
    assertEquals(StrOnline.presenceOverridden, s.notice)
  }

  @Test fun `بی دکانِ معلوم هیچ اعلامی در صف نمی‌نشیند و فرستاده نمی‌شود`() = runBlocking {
    ShopRole.forget(ctx)
    val s = sender()
    s.declare("closed", null, tz)
    assertEquals(0, sent.size)
    assertNull(s.pending)
    assertEquals(StrOnline.presenceNoShop, s.notice)
  }

  private fun <T> assertNotNull_(v: T?): T {
    assertNotNull(v)
    return v!!
  }
}
