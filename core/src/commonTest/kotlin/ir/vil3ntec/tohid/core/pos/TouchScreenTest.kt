package ir.vil3ntec.tohid.core.pos

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 *  تشخیصِ صفحهٔ لمسیِ کامپیوتر و انتخابِ ذخیره‌شدهٔ همین دستگاه — POS-31 (§۵.۳ «تشخیص صفحه لمسی در کامپیوتر،
 *  همراه با کلید «حالت لمسی» در نوار بالا. انتخاب برای هر دستگاه ذخیره می‌شود»).
 *  نام‌ها camelCase اند: کامپایلرِ iOS نامِ آزمون با فاصله و نشانه نمی‌پذیرد.
 */
class TouchScreenTest {

  @Test
  fun windowsDigitizerNeedsTouchAndReady() {
    //  NID_INTEGRATED_TOUCH | NID_MULTI_INPUT | NID_READY — همان کامپیوترِ لمسیِ همه‌کاره
    assertTrue(TouchScreen.windows(0x01 or 0x40 or 0x80))
    //  صفحهٔ لمسیِ بیرونی و آماده
    assertTrue(TouchScreen.windows(0x02 or 0x80))
    //  فقط قلم (NID_INTEGRATED_PEN | NID_READY) لمسی نیست
    assertFalse(TouchScreen.windows(0x04 or 0x80))
    //  لمسی ولی آماده نیست (درایور/دستگاه جدا شده)
    assertFalse(TouchScreen.windows(0x01))
    assertFalse(TouchScreen.windows(0))
  }

  @Test
  fun linuxDirectMultitouchDeviceIsTouchScreen() {
    val devices = """
      I: Bus=0011 Vendor=0002 Product=0007 Version=01b1
      N: Name="SynPS/2 Synaptics TouchPad"
      H: Handlers=mouse0 event6
      B: PROP=5
      B: EV=b
      B: ABS=660800011000003

      I: Bus=0018 Vendor=04f3 Product=2a1c Version=0100
      N: Name="ELAN2514:00 04F3:2A1C"
      H: Handlers=mouse2 event5
      B: PROP=2
      B: EV=1b
      B: ABS=3273800000000003
    """.trimIndent()
    assertTrue(TouchScreen.linux(devices))
  }

  @Test
  fun linuxTouchpadMouseAndKeyboardAreNotTouchScreen() {
    val devices = """
      I: Bus=0011 Vendor=0002 Product=0007 Version=01b1
      N: Name="SynPS/2 Synaptics TouchPad"
      B: PROP=5
      B: ABS=660800011000003

      I: Bus=0003 Vendor=046d Product=c077 Version=0111
      N: Name="Logitech USB Optical Mouse"
      B: PROP=0
      B: EV=17

      I: Bus=0011 Vendor=0001 Product=0001 Version=ab41
      N: Name="AT Translated Set 2 keyboard"
      B: PROP=0
      B: EV=120013
    """.trimIndent()
    assertFalse(TouchScreen.linux(devices))
    assertFalse(TouchScreen.linux(""))
    //  مستقیم ولی بی محور (دکمهٔ سخت‌افزاری) لمسی نیست
    assertFalse(TouchScreen.linux("N: Name=\"x\"\nB: PROP=2\nB: EV=3\n"))
  }

  @Test
  fun linuxSingleTouchDirectDeviceAndMultiWordBitmap() {
    //  صفحهٔ لمسیِ تک‌لمسیِ قدیمی (فقط ABS_X/ABS_Y) با PROP ِ مستقیم
    assertTrue(TouchScreen.linux("N: Name=\"eGalax Inc.\"\nB: PROP=2\nB: ABS=3\n"))
    //  نقشهٔ بیتِ چندواژه‌ای: واژهٔ آخر بیت‌های پایین است
    assertTrue(TouchScreen.linux("N: Name=\"wide\"\nB: PROP=2\nB: ABS=1 3\n"))
    //  نامِ «Touchscreen» بی PROP (هستهٔ قدیمی که PROP ندارد) هم صفحهٔ لمسی است
    assertTrue(TouchScreen.linux("N: Name=\"Silead GSL1680 Touchscreen\"\nB: EV=b\nB: ABS=3\n"))
    //  متنِ خراب ⇒ نه
    assertFalse(TouchScreen.linux("N: Name=\"bad\"\nB: PROP=zz\nB: ABS=qq\n"))
  }

  @Test
  fun storedChoiceAlwaysWins() {
    for (detected in listOf(true, false)) {
      assertEquals(TouchMode.Choice(on = false, active = false), TouchMode.choice(storedOn = false, storedActive = false, detected = detected))
      assertEquals(TouchMode.Choice(on = true, active = false), TouchMode.choice(storedOn = true, storedActive = false, detected = detected))
      assertEquals(TouchMode.Choice(on = false, active = true), TouchMode.choice(storedOn = false, storedActive = true, detected = detected))
    }
  }

  @Test
  fun detectedScreenOnlyShowsTheToggleUntilTapped() {
    //  بی انتخاب و صفحهٔ لمسی ⇒ کلیدِ «حالتِ لمسی» در نوار دیده می‌شود، صندوق همان موشی تا صندوق‌دار بزند
    val c = TouchMode.choice(storedOn = null, storedActive = null, detected = true)
    assertEquals(TouchMode.Choice(on = true, active = false), c)
    assertTrue(TouchMode.showToggle(desktop = true, on = c.on))
    assertFalse(TouchMode.touch(desktop = true, on = c.on, active = c.active))
  }

  @Test
  fun noScreenAndNoChoiceIsTodaysMouseTill() {
    val c = TouchMode.choice(storedOn = null, storedActive = null, detected = false)
    assertFalse(c.on)
    assertFalse(TouchMode.showToggle(desktop = true, on = c.on))
    assertFalse(TouchMode.touch(desktop = true, on = c.on, active = c.active))
  }

  @Test
  fun turningTheSettingOnMakesTheTillTouchAtOnceLikeToday() {
    //  روشن کردنِ صریحِ تنظیم بی کلیدِ ذخیره‌شده ⇒ همان دم لمسی (رفتارِ پیشین)
    assertEquals(TouchMode.Choice(on = true, active = true), TouchMode.choice(storedOn = true, storedActive = null, detected = false))
    assertEquals(TouchMode.Choice(on = true, active = true), TouchMode.choice(storedOn = true, storedActive = null, detected = true))
  }
}
