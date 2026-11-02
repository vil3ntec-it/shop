package ir.vil3ntec.tohid.core.hw

import ir.vil3ntec.tohid.core.hw.WebcamScan.Camera
import ir.vil3ntec.tohid.core.hw.WebcamScan.Mode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** HW-08: انتخابِ حالتِ تصویر، دوربین و آهنگِ رمزگشاییِ وب‌کم — خالص، همان برای هر سکو. */
class WebcamScanTest {

  @Test fun `HW-08 حالتِ ۷۲۰p با ۳۰ فریم از ۱۰۸۰p و ۴۸۰p جلوتر است`() {
    val modes = listOf(Mode(1920, 1080, 30), Mode(640, 480, 30), Mode(1280, 720, 30), Mode(1280, 720, 10), Mode(320, 240, 30))
    assertEquals(Mode(1280, 720, 30), WebcamScan.pickMode(modes))
  }

  @Test fun `HW-08 فریمِ کمتر از ده در بازه پذیرفته نمی‌شود و حالتِ کوچک‌ترِ روان برنده است`() {
    val modes = listOf(Mode(1280, 720, 5), Mode(800, 600, 15))
    assertEquals(Mode(800, 600, 15), WebcamScan.pickMode(modes))
  }

  @Test fun `HW-08 دوربینِ فقط ۱۰۸۰p ⇒ کوچک‌ترینِ بزرگ‌تر با فریمِ بیشتر`() {
    val modes = listOf(Mode(2560, 1440, 30), Mode(1920, 1080, 15), Mode(1920, 1080, 30), Mode(320, 240, 30))
    assertEquals(Mode(1920, 1080, 30), WebcamScan.pickMode(modes))
  }

  @Test fun `HW-08 دوربینِ کم‌کیفیت ⇒ بزرگ‌ترینِ موجود و فهرستِ تهی یا بی‌معنا ⇒ هیچ`() {
    assertEquals(Mode(352, 288, 15), WebcamScan.pickMode(listOf(Mode(176, 144, 30), Mode(352, 288, 15))))
    assertNull(WebcamScan.pickMode(emptyList()))
    assertNull(WebcamScan.pickMode(listOf(Mode(0, 480, 30), Mode(640, -1, 30))))
  }

  @Test fun `HW-08 دوربینِ برگزیدهٔ وصل به کار می‌رود و کشیده‌شده ⇒ نخستینِ وصل`() {
    val cams = listOf(Camera("usb-1", "Logitech C270"), Camera("int-0", "Integrated Camera"))
    assertEquals("int-0", WebcamScan.pickCamera(cams, "int-0")?.id)
    assertEquals("usb-1", WebcamScan.pickCamera(cams, "gone-9")?.id)
    assertEquals("usb-1", WebcamScan.pickCamera(cams, "")?.id)
    assertNull(WebcamScan.pickCamera(emptyList(), "usb-1"))
  }

  @Test fun `HW-08 رمزگشایی حداکثر ده بار در ثانیه و نخستین فریم بی‌درنگ`() {
    assertTrue(WebcamScan.due(now = 5_000, lastAt = 0))
    assertFalse(WebcamScan.due(now = 5_050, lastAt = 5_000))
    assertTrue(WebcamScan.due(now = 5_100, lastAt = 5_000))
  }
}
