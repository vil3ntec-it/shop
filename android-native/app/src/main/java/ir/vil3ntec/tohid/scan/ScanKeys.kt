package ir.vil3ntec.tohid.scan

/**
 *  F0-06 — نویسهٔ بارکد از یک کلید، بی وابستگی به چیدمانِ صفحه‌کلید.
 *
 *  بارکدخوانِ USB/بلوتوث «کلید» می‌فرستد، نه «نویسه»: کلیدِ ۶ِ ردیفِ بالا.
 *  سیستم با چیدمانِ فارسی یا پشتو از آن «۶» می‌سازد و از کلیدِ A «ش».
 *  پنجرهٔ کامپیوتر پیش از این فقط ASCII ۳۳ تا ۱۲۶ را برمی‌داشت، پس با
 *  چیدمانِ فارسی اسکن بی‌صدا گم می‌شد.
 *
 *  قاعده: اگر نویسه‌ای که سیستم ساخته ASCII است، همان (رفتارِ قبلی، بی
 *  تغییر). وگرنه **کلیدِ فیزیکی** خوانده می‌شود: رقمِ ردیفِ بالا/ماشین‌حساب
 *  ⇐ همان رقم، حرف ⇐ همان حرفِ لاتین (Shift ⇐ بزرگ). رقمِ فارسی/عربی بی
 *  کلیدِ شناخته هم لاتین می‌شود.
 *
 *  خالص است (بی Compose) تا بی صفحه‌کلید و بی بارکدخوان سنجیده شود؛
 *  خواندنِ کلید از رویداد در `ScanKeyEvent.kt` است.
 */
object ScanKeys {

  /**
   *  @param digit رقمِ کلیدِ فیزیکی (ردیفِ بالا یا ماشین‌حساب)، یا `-1`
   *  @param letter شمارهٔ حرفِ کلیدِ فیزیکی (`0` = A … `25` = Z)، یا `-1`
   *  @param shift Shift پایین است
   *  @param codePoint نویسه‌ای که چیدمانِ سیستم ساخته (`0` یعنی هیچ)
   *  @param allowSpace فاصله هم نویسهٔ بارکد حساب شود (صفحهٔ فروشِ گوشی)
   *  @return نویسه، یا `null` اگر این کلید به کارِ بارکد نمی‌آید
   */
  fun charOf(digit: Int, letter: Int, shift: Boolean, codePoint: Int, allowSpace: Boolean = false): Char? {
    if (codePoint in 33..126) return codePoint.toChar()
    if (codePoint == 32) return if (allowSpace) ' ' else null
    if (digit in 0..9 && !shift) return '0' + digit
    if (letter in 0..25) return (if (shift) 'A' else 'a') + letter
    return when (codePoint) {
      in 0x06F0..0x06F9 -> '0' + (codePoint - 0x06F0)   // ۰..۹ (فارسی و پشتو)
      in 0x0660..0x0669 -> '0' + (codePoint - 0x0660)   // ٠..٩ (عربی)
      else -> null
    }
  }
}
