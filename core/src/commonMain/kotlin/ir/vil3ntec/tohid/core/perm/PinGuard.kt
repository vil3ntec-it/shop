package ir.vil3ntec.tohid.core.perm

/**
 *  PINِ شخصیِ هر کاربر روی دستگاه — STF-04 (بخشِ ۱۰.۲): شکل و قفل.
 *
 *  • PIN فقط رقم است (فارسی و عربی هم پذیرفته و به لاتین برگردانده می‌شود)،
 *    ۴ تا ۶ رقم؛ برنامه‌های تازه ۶ رقم می‌خواهند.
 *  • پس از ۵ خطای پشتِ سرِ هم قفل می‌شود و فقط با ورودِ آنلاین (گذاشتنِ PINِ
 *    تازه) باز می‌شود — نه با گذشتِ زمان، تا عقب بردنِ ساعت راهی نباشد.
 *  • هشِ PIN (PBKDF2، `PinCrypto`ِ jvm) هرگز همگام نمی‌شود.
 *
 *  Kotlin خالص است — بی android.*، بی کتابخانه.
 */
object PinGuard {

  const val MAX_FAILURES = 5
  const val MIN_LEN = 4
  const val NEW_LEN = 6

  data class State(val failures: Int = 0, val locked: Boolean = false)

  /** ارقامِ فارسی/عربی ⇐ لاتین؛ هر نویسهٔ دیگر می‌ماند تا `isWellFormed` ردش کند */
  fun normalize(pin: String): String = buildString(pin.length) {
    for (ch in pin) {
      append(
        when (ch) {
          in '۰'..'۹' -> '0' + (ch - '۰')
          in '٠'..'٩' -> '0' + (ch - '٠')
          else -> ch
        },
      )
    }
  }

  fun isWellFormed(pin: String): Boolean {
    val p = normalize(pin)
    return p.length in MIN_LEN..NEW_LEN && p.all { it in '0'..'9' }
  }

  /** PINِ تازه: دقیقاً ۶ رقم، نه همه یکسان و نه پشتِ سرِ هم (۱۲۳۴۵۶ / ۶۵۴۳۲۱) */
  fun newPinProblem(pin: String): String? {
    val p = normalize(pin)
    if (p.length != NEW_LEN || !p.all { it in '0'..'9' }) return "length"
    if (p.all { it == p[0] }) return "weak"
    val up = (1 until p.length).all { p[it] - p[it - 1] == 1 }
    val down = (1 until p.length).all { p[it - 1] - p[it] == 1 }
    if (up || down) return "weak"
    return null
  }

  fun canTry(s: State): Boolean = !s.locked

  fun remaining(s: State): Int = if (s.locked) 0 else maxOf(0, MAX_FAILURES - s.failures)

  /** نتیجهٔ یک تلاش ⇐ حالِ تازه. قفل‌شده با PINِ درست هم باز نمی‌شود. */
  fun after(s: State, ok: Boolean): State = when {
    s.locked -> s
    ok -> State()
    else -> (s.failures + 1).let { State(it, it >= MAX_FAILURES) }
  }
}
