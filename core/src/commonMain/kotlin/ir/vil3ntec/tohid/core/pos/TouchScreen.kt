package ir.vil3ntec.tohid.core.pos

/**
 *  تشخیصِ صفحهٔ لمسیِ کامپیوتر (POS-31، §۵.۳ «تشخیص صفحه لمسی در کامپیوتر»). Compose Desktop لمس را رویدادِ موس
 *  می‌دهد، پس تشخیص از خودِ سیستم‌عامل است و این‌جا فقط خواندنِ پاسخِ آن؛ خواندنِ سیستم در پروندهٔ مرزیِ کامپیوتر
 *  (`TouchScreenProbe`). نتیجه فقط پیش‌فرضِ تنظیم را می‌سازد ([TouchMode.choice]) — انتخابِ ذخیره‌شده همیشه برنده است.
 *
 *  Kotlin خالص است — بی java.*، بی android.*، بی کتابخانه.
 */
object TouchScreen {

  private const val NID_INTEGRATED_TOUCH = 0x01
  private const val NID_EXTERNAL_TOUCH = 0x02
  private const val NID_READY = 0x80

  /** ویندوز: پاسخِ `GetSystemMetrics(SM_DIGITIZER)` — صفحهٔ لمسیِ درونی یا بیرونی که آماده است (قلمِ تنها نه) */
  fun windows(digitizer: Int): Boolean =
    digitizer and (NID_INTEGRATED_TOUCH or NID_EXTERNAL_TOUCH) != 0 && digitizer and NID_READY != 0

  private const val INPUT_PROP_DIRECT = 1
  private const val ABS_X = 0
  private const val ABS_MT_POSITION_X = 0x35

  /**
   *  لینوکس: متنِ `/proc/bus/input/devices`. صفحهٔ لمسی = دستگاهی با محورِ X (`ABS_X` یا `ABS_MT_POSITION_X`) که یا
   *  `INPUT_PROP_DIRECT` دارد (روی خودِ صفحه، برخلافِ تاچ‌پد که `POINTER` است) یا هستهٔ قدیمی PROP نمی‌دهد و نامش
   *  «touchscreen» است. نقشهٔ بیت واژه‌های ۶۴بیتیِ هگزاست و واژهٔ آخر بیت‌های پایین؛ متنِ خراب ⇒ نه.
   */
  fun linux(devices: String): Boolean = devices.split(Regex("\\n\\s*\\n")).any { block ->
    val lines = block.lines().map { it.trim() }
    fun field(prefix: String) = lines.firstOrNull { it.startsWith(prefix) }?.substringAfter(prefix)?.trim()
    val abs = field("B: ABS=") ?: return@any false
    val hasX = bit(abs, ABS_X) || bit(abs, ABS_MT_POSITION_X)
    val direct = field("B: PROP=")?.let { bit(it, INPUT_PROP_DIRECT) }
    val named = field("N: Name=")?.lowercase()?.let { it.contains("touchscreen") || it.contains("touch screen") } == true
    hasX && (direct ?: named)
  }

  /** بیتِ [n] در نقشهٔ بیتِ هگزِ [map] («3273800000000003» یا «1 3»)؛ خراب ⇒ false */
  private fun bit(map: String, n: Int): Boolean {
    val words = map.split(' ').filter { it.isNotEmpty() }.reversed()
    val word = words.getOrNull(n / 64) ?: return false
    val value = word.toULongOrNull(16) ?: return false
    return (value shr (n % 64)) and 1uL == 1uL
  }
}
