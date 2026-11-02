package ir.vil3ntec.tohid.scan

import ir.vil3ntec.tohid.core.hw.WebcamScan

/**
 *  HW-08 — وب‌کم به‌عنوانِ بارکدخوان: مرزِ سکو.
 *
 *  گوشی (اندروید و آیفون) دوربینِ خودش را با `CameraScanner` دارد و وب‌کمی برای فهرست کردن نیست؛ پس این‌جا
 *  «پشتیبانی نمی‌شود» و فهرستِ تهی. کامپیوتر همین API را با openpnp-capture پیاده می‌کند
 *  (`desktop/.../scan/WebcamPlatformDesk.kt`، فهرستِ `replaced` ِ `desktop/build.gradle.kts`).
 */
object WebcamPlatform {
  /** آیا این سکو وب‌کم را به‌عنوانِ بارکدخوان می‌شناسد */
  val supported: Boolean get() = false

  /** دوربین‌های وصل؛ گوشی ⇒ تهی */
  fun cameras(): List<WebcamScan.Camera> = emptyList()
}
