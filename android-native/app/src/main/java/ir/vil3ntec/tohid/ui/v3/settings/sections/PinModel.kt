package ir.vil3ntec.tohid.ui.v3.settings.sections

import ir.vil3ntec.tohid.data.LockStore

/**
 *  گذاشتنِ رمزِ برنامه در برگهٔ رمز — دو بار پرسیده می‌شود، چون رمزی که اشتباه
 *  تایپ شده یعنی قفل شدنِ خودِ صاحبِ دکان بیرونِ دفترش. خالص و آزمون‌پذیر.
 *
 *  `mismatch` هر بار که دو رمز یکی نبودند یکی بالا می‌رود (برگه با آن می‌لرزد) و
 *  برگه دوباره از گامِ اول می‌پرسد.
 */
internal data class PinEntry(
  val first: String = "",
  val typed: String = "",
  val confirming: Boolean = false,
  val mismatch: Int = 0,
  /** رمزِ نهایی وقتی بارِ دوم هم همان زده شد */
  val done: String? = null,
)

internal object PinModel {
  const val LENGTH = LockStore.NEW_PIN_LEN

  fun type(e: PinEntry, digit: Char): PinEntry {
    if (e.done != null || digit !in '0'..'9' || e.typed.length >= LENGTH) return e
    val typed = e.typed + digit
    if (typed.length < LENGTH) return e.copy(typed = typed)
    return when {
      !e.confirming -> e.copy(first = typed, typed = "", confirming = true)
      typed == e.first -> e.copy(typed = typed, done = typed)
      else -> PinEntry(mismatch = e.mismatch + 1)
    }
  }

  fun erase(e: PinEntry): PinEntry = if (e.done != null || e.typed.isEmpty()) e else e.copy(typed = e.typed.dropLast(1))
}
