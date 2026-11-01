package ir.vil3ntec.tohid.core.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** پیامِ صفِ بزرگ روی هر هدف (JVM و شبیه‌سازِ iOS)؛ بردارهای مشترک با وب: `jvmTest/…/OpBacklogVectorsTest.kt` */
class OpBacklogTest {

  @Test
  fun warningOnlyFromWarnAt() {
    assertEquals("", OpBacklog.warning(0))
    assertEquals("", OpBacklog.warning(OpBacklog.WARN_AT - 1))
    val w = OpBacklog.warning(OpBacklog.WARN_AT)
    assertTrue(w.startsWith("۱۰٬۰۰۰ تغییر هنوز به سرور نرسیده"), w)
    assertTrue(OpBacklog.warning(60_000).startsWith("۶۰٬۰۰۰ "))
  }

  @Test
  fun faCountGroupsThousands() {
    assertEquals("۰", OpBacklog.faCount(0))
    assertEquals("۹۹۹", OpBacklog.faCount(999))
    assertEquals("۱٬۰۰۰", OpBacklog.faCount(1000))
    assertEquals("۱٬۲۳۴٬۵۶۷", OpBacklog.faCount(1_234_567))
    assertEquals("۰", OpBacklog.faCount(-3))
  }
}
