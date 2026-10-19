package ir.vil3ntec.tohid.core.perm

/**
 *  قفلِ خودکار پس از بی‌کاری — STF-05.
 *
 *  رفتارِ امروز: قفلِ برنامه فقط هنگامِ باز شدنِ برنامه می‌پرسد و تا برنامه
 *  باز است دوباره قفل نمی‌شود. پس پیش‌فرض `0` (خاموش) است و تا صاحب دکان
 *  دقیقه‌ای انتخاب نکند هیچ چیز عوض نمی‌شود.
 *
 *  زمان‌ها از ساعتِ یکنوا (مثلِ `System.nanoTime`) می‌آیند، نه ساعتِ دیواری؛
 *  عقب/جلو بردنِ ساعتِ دستگاه قفل را نه زودتر می‌اندازد نه دیرتر.
 */
object IdleLock {

  /** دقیقه‌های قابلِ انتخاب؛ ۰ = خاموش (پیش‌فرض) */
  val CHOICES: List<Int> = listOf(0, 1, 2, 5, 10, 30)

  /** عددِ ذخیره‌شدهٔ ناشناخته ⇒ خاموش، نه یک قفلِ ناخواسته */
  fun clamp(minutes: Int): Int = if (minutes in CHOICES) minutes else 0

  fun shouldLock(lockEnabled: Boolean, idleMinutes: Int, lastActivityMs: Long, nowMs: Long): Boolean {
    val m = clamp(idleMinutes)
    if (!lockEnabled || m == 0) return false
    return nowMs - lastActivityMs >= m * 60_000L
  }
}
