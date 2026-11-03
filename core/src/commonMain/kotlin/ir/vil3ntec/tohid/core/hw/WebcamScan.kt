package ir.vil3ntec.tohid.core.hw

/**
 *  HW-08 (§۶.۱۱) — وب‌کمِ کامپیوتر به‌عنوانِ بارکدخوان: تصمیم‌های خالص، بی دوربین و بی ساعت.
 *
 *  گرفتنِ فریم و رمزگشایی مرزِ پلتفرم است (کامپیوتر: `desktop/.../scan/WebcamPlatformDesk.kt`، openpnp-capture و
 *  ZXing)؛ پذیرشِ کد همان `BarcodeGuard` ِ دوربینِ گوشی است (رقمِ کنترلی، تکرار، خوانشِ نصفه). این‌جا فقط آنچه هر
 *  سکو یکسان می‌خواند: کدام دوربین، کدام اندازهٔ تصویر، و کی فریمِ بعدی رمزگشایی شود.
 *
 *  ⛔ پشتِ کلیدِ همین دستگاه (`WebcamPref`، پیش‌فرض **خاموش**، ردهٔ B: وابسته به دستگاهِ جانبی). خاموش ⇒ همان
 *  گیرندهٔ بارکدخوانِ USB/بلوتوث ِ امروز، بی هیچ دسترسی به دوربین.
 */
object WebcamScan {

  /** یک حالتِ تصویرِ دوربین (آنچه رانندهٔ دوربین گزارش می‌کند) */
  data class Mode(val width: Int, val height: Int, val fps: Int)

  /** یک دوربین: شناسهٔ پایدار (برای به‌یادسپاری) و نامِ خوانا */
  data class Camera(val id: String, val name: String)

  /** کم‌پهناتر از این، خط‌های نازکِ EAN در تصویرِ وب‌کم به هم می‌چسبند */
  const val MIN_WIDTH = 640

  /** پهن‌تر از این فقط رمزگشایی را کند می‌کند؛ بارکدِ دستِ صندوقدار در ۷۲۰p کامل خوانده می‌شود */
  const val MAX_WIDTH = 1280

  /** کمتر از این، تکانِ دست تصویر را تار می‌کند */
  const val MIN_FPS = 10

  /** فاصلهٔ کمینهٔ دو رمزگشایی — بیش از ده بار در ثانیه فقط پردازندهٔ صندوق را می‌سوزاند */
  const val DECODE_GAP_MS = 100L

  /**
   *  بهترین حالتِ تصویر برای بارکد.
   *
   *  ۱. در بازهٔ ۶۴۰ تا ۱۲۸۰ پیکسل و دست‌کم ۱۰ فریم ⇒ بزرگ‌ترین (جزئیاتِ بیشتر)، هم‌اندازه ⇒ فریمِ بیشتر.
   *  ۲. وگرنه کوچک‌ترینِ بزرگ‌تر از ۱۲۸۰ (کند ولی خوانا)، هم‌اندازه ⇒ فریمِ بیشتر.
   *  ۳. وگرنه بزرگ‌ترینِ موجود (دوربینِ کم‌کیفیت؛ بهتر از هیچ).
   *  فهرستِ تهی یا فقط حالت‌های بی‌معنا ⇒ null (دوربین باز نمی‌شود و همین گفته می‌شود).
   */
  fun pickMode(modes: List<Mode>): Mode? {
    val sane = modes.filter { it.width > 0 && it.height > 0 && it.fps >= 0 }
    if (sane.isEmpty()) return null
    val area = { m: Mode -> m.width.toLong() * m.height }
    val fit = sane.filter { it.width in MIN_WIDTH..MAX_WIDTH && it.fps >= MIN_FPS }
    if (fit.isNotEmpty()) return fit.maxWith(compareBy<Mode>({ area(it) }, { it.fps }))
    val bigger = sane.filter { it.width > MAX_WIDTH && it.fps >= MIN_FPS }
    if (bigger.isNotEmpty()) return bigger.minWith(compareBy<Mode>({ area(it) }, { -it.fps }))
    return sane.maxWith(compareBy<Mode>({ area(it) }, { it.fps }))
  }

  /**
   *  دوربینِ این صندوق: همانی که صاحب‌کار برگزیده (اگر هنوز وصل است)، وگرنه نخستین دوربینِ وصل.
   *  دوربینِ برگزیده‌ای که کشیده شده هرگز «یادِ» دوربینِ دیگر نمی‌شود؛ فقط این بار نخستین به کار می‌رود.
   */
  fun pickCamera(cameras: List<Camera>, saved: String): Camera? =
    cameras.firstOrNull { saved.isNotBlank() && it.id == saved } ?: cameras.firstOrNull()

  /** آیا وقتِ رمزگشاییِ فریمِ تازه رسیده — نخستین فریم همیشه */
  fun due(now: Long, lastAt: Long): Boolean = lastAt <= 0L || now - lastAt >= DECODE_GAP_MS
}
