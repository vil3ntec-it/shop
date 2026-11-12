package ir.vil3ntec.tohid.ui.v3.online.storefront

import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.OnlineFmt
import ir.vil3ntec.tohid.core.i18n.StrOnline
import ir.vil3ntec.tohid.core.storefront.StorefrontOwner
import ir.vil3ntec.tohid.data.repo.PresenceStatusDto
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 *  GATE-NEW-01 (§۳۵) روی رابطِ صاحبِ دکان: برگهٔ «وضعیت» ِ ویترین (`StatusPane`) چهار چیزِ جدا را جدا می‌گوید و هر کدام
 *  فقط از فیلدِ خودش در پاسخِ سرور (`lib/storefront-hours.js` ‏`presenceOf`):
 *    ساعتِ کاری      ⇐ `caption`        (localTime، hoursSet، holiday، today)
 *    حضورِ اعلام‌شده  ⇐ `headline`       (badge، declaredUntil، timezone)
 *    تپشِ دستگاه      ⇐ `connection`     (lastConfirmedAt و حکمِ `connection` ِ سرور)
 *    امکانِ سفارش     ⇐ `OnlineFmt.acceptance` (orderAcceptance)
 *  PRES-02، PRES-08 و PRES-09 (صف، دو دستگاه، خطای سرور) در `PresenceSenderDesktopTest`؛ PRES-10 و محاسبهٔ همه روی API در
 *  `storefront.test.js`.
 */
class PresenceUiTest {
  private val tz = "Asia/Kabul"
  private val at = 1_763_000_000_000L

  @Before fun fa() { I18n.source = { Lang.FA } }
  @After fun reset() { I18n.source = { Lang.FA } }

  private fun lines(st: PresenceStatusDto) =
    listOf(PresenceModel.caption(st), PresenceModel.headline(st), PresenceModel.connection(st), OnlineFmt.acceptance(st.orderAcceptance))

  @Test
  fun `PRES-01 اعلامِ «آنلاین تا» با ساعتِ دکان و چهار خطِ جدا`() {
    val until = at + 3 * 3_600_000L
    val st = PresenceStatusDto(
      badge = "declared_until", mode = "online_until", declaredUntil = until, connection = "confirmed", lastConfirmedAt = at,
      orderAcceptance = "accepting", timezone = tz, localTime = "10:15", hoursSet = true, today = listOf(listOf(480, 1200)),
    )
    val clock = StorefrontOwner.clock(until, tz)
    assertEquals(StrOnline.presShortUntil(clock), PresenceModel.headline(st))
    assertTrue(PresenceModel.sentence(st), PresenceModel.sentence(st).contains(clock))
    assertEquals(StrOnline.connection(StorefrontOwner.clock(at, tz)), PresenceModel.connection(st))
    assertEquals(StrOnline.acceptAccepting, OnlineFmt.acceptance(st.orderAcceptance))
    assertEquals("هر خط حرفِ خودش را می‌زند", 4, lines(st).toSet().size)
  }

  @Test
  fun `PRES-03 تپشِ کهنه یا رفته در خطِ اتصال هم گفته می‌شود، نه «آخرین تأیید» ِ ساده`() {
    val base = PresenceStatusDto(badge = "open_unconfirmed", lastConfirmedAt = at, timezone = tz, orderAcceptance = "delayed", hoursSet = true)
    val c = StorefrontOwner.clock(at, tz)
    val fresh = PresenceModel.connection(base.copy(badge = "open_online", connection = "confirmed", orderAcceptance = "accepting"))
    val stale = PresenceModel.connection(base.copy(connection = "unconfirmed"))
    val lost = PresenceModel.connection(base.copy(badge = "offline", connection = "lost", orderAcceptance = "delayed"))
    assertEquals(StrOnline.connection(c), fresh)
    assertEquals(OnlineFmt.connectionStale(c), stale)
    assertEquals(OnlineFmt.connectionLost(c), lost)
    assertEquals(3, setOf(fresh, stale, lost).size)
    listOf(stale, lost).forEach { assertTrue(it, it.contains(c)) }
    //  هرگز تماسی نبوده ⇒ همان «هنوز تأیید نشده»؛ سرورِ کهنهٔ بی `connection` ⇒ همان جملهٔ پیشین
    assertEquals(StrOnline.connection(null), PresenceModel.connection(base.copy(lastConfirmedAt = null, connection = "lost")))
    assertEquals(StrOnline.connection(c), PresenceModel.connection(base.copy(connection = "")))
    assertEquals(StrOnline.presShortOpenUnconfirmed, PresenceModel.headline(base.copy(connection = "unconfirmed")))
  }

  @Test
  fun `PRES-04 و PRES-05 پایانِ اعلام یا ساعتِ کاری — سرخط حکمِ سرور است، نه declaredUntil ِ مانده`() {
    val ended = PresenceStatusDto(
      badge = "closed", mode = "auto", declaredUntil = at - 60_000L, connection = "confirmed", lastConfirmedAt = at,
      orderAcceptance = "later", timezone = tz, localTime = "21:05", hoursSet = true, today = listOf(listOf(480, 1200)),
    )
    assertEquals(StrOnline.presShortClosed, PresenceModel.headline(ended))
    assertEquals(StrOnline.acceptLater, OnlineFmt.acceptance(ended.orderAcceptance))
    assertTrue(PresenceModel.caption(ended), PresenceModel.caption(ended).contains("21:05"))
    //  دستگاه هنوز وصل است: «بسته» ِ سرخط تپش را «قطع» نمی‌خواند
    assertEquals(StrOnline.connection(StorefrontOwner.clock(at, tz)), PresenceModel.connection(ended))
  }

  @Test
  fun `PRES-06 تعطیلِ استثنایی، روزِ بی ساعت و ساعتِ کاریِ تنظیم‌نشده جدا گفته می‌شوند`() {
    val st = PresenceStatusDto(badge = "closed", localTime = "09:00", timezone = tz)
    val holiday = PresenceModel.caption(st.copy(hoursSet = true, holiday = true, today = listOf(listOf(480, 1200))))
    val dayOff = PresenceModel.caption(st.copy(hoursSet = true))
    val unset = PresenceModel.caption(st)
    assertTrue(holiday, holiday.contains(StrOnline.todayHoliday))
    assertTrue(dayOff, dayOff.contains(StrOnline.todayClosed))
    assertTrue(unset, unset.contains(StrOnline.hoursNotSet))
    assertEquals(3, setOf(holiday, dayOff, unset).size)
    //  دو بازه در یک روز، هر کدام جدا
    val split = PresenceModel.caption(st.copy(hoursSet = true, today = listOf(listOf(480, 720), listOf(900, 1320))))
    assertTrue(split, split.contains("08:00-12:00") && split.contains("15:00-22:00"))
  }

  @Test
  fun `PRES-07 کامپیوترِ خاموش ولی سرور روشن — دستگاه آفلاین و سفارش ثبت می‌شود`() {
    val st = PresenceStatusDto(
      badge = "offline", mode = "auto", connection = "lost", lastConfirmedAt = at - 3_600_000L,
      orderAcceptance = "delayed", timezone = tz, localTime = "11:00", hoursSet = true, today = listOf(listOf(480, 1200)),
    )
    assertEquals(StrOnline.presShortOffline, PresenceModel.headline(st))
    assertEquals(StrOnline.acceptDelayed, OnlineFmt.acceptance(st.orderAcceptance))
    assertEquals(OnlineFmt.connectionLost(StorefrontOwner.clock(st.lastConfirmedAt!!, tz)), PresenceModel.connection(st))
    assertNotEquals(PresenceModel.headline(st), OnlineFmt.acceptance(st.orderAcceptance))
    assertEquals(4, lines(st).toSet().size)
  }
}
