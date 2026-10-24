package ir.vil3ntec.tohid.ui.v3.onboarding

/*
 *  حالتِ خالصِ قفلِ برنامه (`lock`، `lock/setup`) — همان رفتارِ `AppLockScreen`/`LockSetupScreen` ِ
 *  پیشین، با آزمون (`PinModelTest`). سنجش و نگهداریِ رمز همچنان با `LockStore` است.
 */

object PinRules {
  /** رمزِ غلط: نقطه‌ها و پیامِ خطا این‌قدر می‌مانند و بعد صفحه به حالِ عادی برمی‌گردد */
  const val WRONG_HOLD_MS = 700L

  /** رقمِ صفحه‌کلیدِ سخت‌افزاری (ردیفِ بالا، بخشِ عددی، رقمِ فارسی و عربی) ⇐ رقمِ لاتین؛ دیگر هیچ */
  fun keyDigit(c: Char): Char? = when (c) {
    in '0'..'9' -> c
    in '۰'..'۹' -> '0' + (c - '۰')
    in '٠'..'٩' -> '0' + (c - '٠')
    else -> null
  }
}

/** بازکردنِ قفل: طول از خودِ رمزِ ذخیره‌شده (رمزِ چهاررقمیِ قدیم هم باز می‌شود) */
data class PinEntry(val length: Int, val digits: String = "") {
  val full: Boolean get() = digits.length == length
  fun press(d: Char): PinEntry = if (digits.length < length) copy(digits = digits + d) else this
  fun erase(): PinEntry = copy(digits = digits.dropLast(1))
  fun clear(): PinEntry = copy(digits = "")
}

/**
 *  گذاشتنِ رمز: یک بار، بعد «همان را دوباره». یکی نبود ⇒ هر دو پاک و از اول (و پیامِ «دو بار یکی
 *  نبود» تا رمزِ درست می‌ماند). «پاک کردن» در آغازِ تکرار به رقمِ آخرِ بارِ اول برمی‌گردد.
 */
data class PinSetup(
  val length: Int,
  val first: String = "",
  val again: String = "",
  val confirming: Boolean = false,
  val mismatch: Boolean = false,
) {
  val shown: String get() = if (confirming) again else first

  /** رقمِ تازه ⇒ حالتِ بعد و، اگر دو بار یکی شد، خودِ رمز برای ذخیره */
  fun press(d: Char): Pair<PinSetup, String?> {
    if (!confirming) {
      if (first.length >= length) return this to null
      val f = first + d
      return copy(first = f, confirming = f.length == length) to null
    }
    if (again.length >= length) return this to null
    val a = again + d
    if (a.length < length) return copy(again = a) to null
    return if (a == first) copy(again = a) to first
    else copy(first = "", again = "", confirming = false, mismatch = true) to null
  }

  fun erase(): PinSetup = when {
    confirming && again.isEmpty() -> copy(confirming = false, first = first.dropLast(1))
    confirming -> copy(again = again.dropLast(1))
    first.isNotEmpty() -> copy(first = first.dropLast(1))
    else -> this
  }
}
