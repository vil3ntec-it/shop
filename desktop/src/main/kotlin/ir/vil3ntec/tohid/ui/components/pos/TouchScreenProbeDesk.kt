package ir.vil3ntec.tohid.ui.components.pos

import com.sun.jna.Native
import com.sun.jna.win32.StdCallLibrary
import ir.vil3ntec.tohid.core.pos.TouchScreen

/**
 *  POS-31 — «تشخیص صفحه لمسی در کامپیوتر» (§۵.۳). Compose Desktop لمس را رویدادِ موس می‌دهد، پس پرسش از خودِ
 *  سیستم‌عامل است و خواندنِ پاسخ در هسته (`TouchScreen`): ویندوز `GetSystemMetrics(SM_DIGITIZER)` با همان JNA ِ
 *  برنامه، لینوکس `/proc/bus/input/devices`، مک صفحهٔ لمسی ندارد. یک بار در هر اجرا؛ هر خطا ⇒ «نه» (همان صندوقِ
 *  امروز). نتیجه فقط پیش‌فرضِ تنظیمِ «حالتِ لمسیِ صندوق» است (`TouchMode.choice`).
 */
object TouchScreenProbe {
  val detected: Boolean by lazy { runCatching { probe() }.getOrDefault(false) }

  private const val SM_DIGITIZER = 94

  @Suppress("FunctionName")
  private interface User32 : StdCallLibrary {
    fun GetSystemMetrics(index: Int): Int
  }

  private fun probe(): Boolean {
    val os = System.getProperty("os.name").orEmpty().lowercase()
    return when {
      os.startsWith("windows") -> TouchScreen.windows(Native.load("user32", User32::class.java).GetSystemMetrics(SM_DIGITIZER))
      os.contains("linux") -> java.io.File("/proc/bus/input/devices").takeIf { it.canRead() }?.readText()?.let(TouchScreen::linux) == true
      else -> false
    }
  }
}
