package ir.vil3ntec.tohid.ui.v3.account.team

import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrAccount
import ir.vil3ntec.tohid.core.i18n.StrPurchaseFx
import ir.vil3ntec.tohid.core.model.DeviceMemberDto
import ir.vil3ntec.tohid.core.model.ShopDeviceDto
import ir.vil3ntec.tohid.core.staff.DeviceBuilds
import ir.vil3ntec.tohid.core.staff.DeviceBuilds.State
import ir.vil3ntec.tohid.ui.theme.V3Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 *  نسخهٔ برنامهٔ دستگاه‌های دکان (`core/staff/DeviceBuilds`، `DeviceVersionModel`): شکلِ نسخه، «نسخهٔ کهنه» نسبت به ساختِ همین
 *  برنامه، «نامعلوم» برای دستگاهی که هرگز نگفته، خطِ دیگرِ ساخت (گوشی در برابرِ کامپیوتر) و پرسشِ کلیدِ «خرید به ارزِ دیگر».
 */
class DeviceVersionModelTest {

  @Before fun fa() { I18n.source = { Lang.FA } }

  private fun dev(id: String, v: String?, current: Boolean = false, status: String = "active", disabledAt: Long? = null, name: String = id) =
    ShopDeviceDto(id = id, name = name, status = status, disabledAt = disabledAt, current = current, appVersion = v, member = DeviceMemberDto("u-$id", "staff", "کارمندِ $id"))

  @Test fun `شکلِ نسخه — خطِ گوشی و کامپیوتر از عددِ نخست، بی شمارهٔ ساخت سنجیدنی نیست`() {
    assertEquals(DeviceBuilds.Build(DeviceBuilds.Line.PHONE, 2, 615), DeviceBuilds.parse("3.2.615"))
    assertEquals(DeviceBuilds.Build(DeviceBuilds.Line.DESKTOP, 0, 366), DeviceBuilds.parse("v1.0.366"))
    assertEquals(DeviceBuilds.Build(DeviceBuilds.Line.PHONE, 2, 700), DeviceBuilds.parse(" 3.2.700-preview "))
    for (bad in listOf(null, "", "3.2", "1.0", "2.14.5", "4.0.1", "abc", "3.2.x", "۳.۲.۶۱۵", "3.2.615 beta", "3.2.615-" + "x".repeat(17))) {
      assertNull(bad, DeviceBuilds.parse(bad))
    }
    //  ۳٫۳ ِ آینده بالاتر از هر ۳٫۲ است؛ در یک خط شمارهٔ ساخت
    assertTrue(DeviceBuilds.parse("3.3.1")!! > DeviceBuilds.parse("3.2.999")!!)
    assertTrue(DeviceBuilds.parse("3.2.10")!! > DeviceBuilds.parse("3.2.9")!!)
  }

  @Test fun `همان خط — کمتر از ساختِ همین برنامه کهنه، برابر و بیشتر به‌روز؛ نگفته نامعلوم؛ همین دستگاه همیشه به‌روز`() {
    val list = listOf(
      dev("me", null, current = true), dev("old", "3.2.600"), dev("same", "3.2.615"), dev("newer", "3.2.620-preview"),
      dev("never", null), dev("web", "3.2"),
    )
    val s = DeviceVersionModel.states(list, self = "3.2.615")
    assertEquals(State.CURRENT, s["me"])
    assertEquals(State.OLD, s["old"])
    assertEquals(State.CURRENT, s["same"])
    assertEquals(State.CURRENT, s["newer"])
    assertEquals(State.UNKNOWN, s["never"])
    assertEquals(State.UNKNOWN, s["web"])
  }

  @Test fun `خطِ دیگر — کمتر از تازه‌ترینِ همان خط در دکان کهنه، وگرنه سنجیده‌نشده`() {
    val list = listOf(dev("me", "3.2.615", current = true), dev("pc1", "1.0.360"), dev("pc2", "1.0.366"))
    val s = DeviceVersionModel.states(list, self = "3.2.615")
    assertEquals(State.OLD, s["pc1"])
    assertEquals(State.UNCHECKED, s["pc2"])
    //  و از کامپیوتر: گوشی‌ها خطِ دیگرند
    val fromDesk = DeviceVersionModel.states(listOf(dev("pc", "1.0.366", current = true), dev("p1", "3.2.600"), dev("p2", "3.2.615"), dev("pc0", "1.0.300")), "1.0.366")
    assertEquals(State.OLD, fromDesk["p1"])
    assertEquals(State.UNCHECKED, fromDesk["p2"])
    assertEquals(State.OLD, fromDesk["pc0"])
  }

  @Test fun `پرسشِ کلید — همه به‌روز مستقیم؛ کهنه، نامعلوم و سنجیده‌نشده فهرست؛ فهرست نرسید هشدار`() {
    val self = "3.2.615"
    assertEquals(DeviceBuilds.Gate.Direct, DeviceVersionModel.gate(listOf(dev("me", null, current = true), dev("a", "3.2.615"), dev("b", "3.2.640")), self))
    //  دستگاهِ از کار افتاده یا خارج‌شده پرسش نمی‌سازد (فعال نیست)
    assertEquals(
      DeviceBuilds.Gate.Direct,
      DeviceVersionModel.gate(listOf(dev("me", null, current = true), dev("lost", "3.2.100", status = "revoked", disabledAt = 1), dev("out", null, status = "revoked")), self),
    )
    val list = listOf(dev("me", null, current = true), dev("old", "3.2.600", name = "Redmi 9"), dev("ok", "3.2.615"), dev("never", null), dev("pc", "1.0.366", name = "کامپیوترِ دخل"))
    val g = DeviceVersionModel.gate(list, self)
    assertTrue(g is DeviceBuilds.Gate.Confirm)
    g as DeviceBuilds.Gate.Confirm
    assertEquals(listOf("old" to State.OLD, "never" to State.UNKNOWN, "pc" to State.UNCHECKED), g.devices.map { it.first.id to it.second })
    val rows = DeviceVersionModel.gateRows(g, list, self)
    assertEquals(listOf("Redmi 9 · کارمندِ old", "never · کارمندِ never", "کامپیوترِ دخل · کارمندِ pc"), rows.map { it.title })
    assertEquals(listOf("نسخهٔ 3.2.600", null, "نسخهٔ 1.0.366"), rows.map { it.version })
    assertEquals(listOf(StrAccount.versionOld, StrAccount.versionUnknown, StrPurchaseFx.notChecked), rows.map { it.label })
    assertEquals(DeviceBuilds.Gate.NoList, DeviceVersionModel.gate(null, self))
  }

  @Test fun `نوشته‌های فهرست — نسخه و آخرین بار در یک خط، قرص فقط برای کهنه و نامعلوم`() {
    val me = dev("me", "3.2.500", current = true)
    //  همین دستگاه ساختِ خودش را نشان می‌دهد، نه آنچه سرور پیش‌تر دیده بود
    assertEquals("نسخهٔ 3.2.615 · آخرین بار: دیروز", DeviceVersionModel.subtitle(me, State.CURRENT, "آخرین بار: دیروز", self = "3.2.615"))
    assertEquals("آخرین بار: دیروز", DeviceVersionModel.subtitle(dev("x", null), State.UNKNOWN, "آخرین بار: دیروز", self = "3.2.615"))
    assertNull(DeviceVersionModel.subtitle(dev("x", null), State.UNKNOWN, null, self = "3.2.615"))
    assertEquals(V3Status.WARNING to "نسخهٔ کهنه", DeviceVersionModel.pill(State.OLD))
    assertEquals(V3Status.INACTIVE to "نسخهٔ نامعلوم", DeviceVersionModel.pill(State.UNKNOWN))
    assertNull(DeviceVersionModel.pill(State.CURRENT))
    assertNull(DeviceVersionModel.pill(State.UNCHECKED))
    assertNull(DeviceVersionModel.pill(null))
  }
}
