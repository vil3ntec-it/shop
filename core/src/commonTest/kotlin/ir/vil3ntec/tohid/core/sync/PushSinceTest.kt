package ir.vil3ntec.tohid.core.sync

import kotlin.test.Test
import kotlin.test.assertEquals

/** گردشِ `PushSince` روی هر هدف (JVM و شبیه‌سازِ iOS)؛ بردارهای مشترک با وب: `jvmTest/…/PushSinceVectorsTest.kt` */
class PushSinceTest {

  /** صفِ خالی، ثبتِ وسطِ pull، صفِ پر، تخلیه و دورِ تازه */
  @Test
  fun lifecycleIsConservative() {
    var noted = PushSince.NONE
    noted = PushSince.note(noted, queueWasEmpty = true, cursor = 40)      // ثبت وسطِ pull (cursor هنوز ۴۰)
    val afterPull = 55                                                    // pull ِ همان دور cursor را جلو برد
    assertEquals(40, PushSince.since(noted, afterPull.toLong()), "op پیش از دیدنِ ۴۱..۵۵ ساخته شد")
    noted = PushSince.note(noted, queueWasEmpty = false, cursor = 55)     // ثبتِ بعدی، صف هنوز پر
    assertEquals(40, PushSince.since(noted, 55))
    noted = PushSince.note(noted, queueWasEmpty = true, cursor = 60)      // صف خالی شد، ثبتِ تازه
    assertEquals(60, PushSince.since(noted, 60))
    assertEquals(30, PushSince.since(noted, 30), "cursor ِ پایین‌آمده (بازسازی) ⇒ همان cursor")
  }
}
