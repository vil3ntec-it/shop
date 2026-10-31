package ir.vil3ntec.tohid.core.pos

/**
 *  صفحه‌کلیدِ عددیِ لمسیِ صندوق با حالت‌های «تعداد، قیمت، تخفیف» (POS-07؛ §۵.۳ «صندوقِ لمسی»).
 *
 *  فقط ورودی است: کدام کلید زده شد ⇒ متنِ تازه، و متن ⇒ عددِ دقیق. اثر روی ردیفِ سبد کارِ
 *  صدازننده است و از همان راهِ کادرِ «ردیفِ سبد» می‌گذرد (`LinePricing.edit` برای قیمت و تخفیف)،
 *  تا قاعدهٔ جهت، سقفِ تخفیفِ نقش و «درخواستِ تأیید» همان بمانند.
 *
 *  ── عدد بی Double ─────────────────────────────────────────────────
 *  متن رقم‌به‌رقم خوانده می‌شود (همان قاعدهٔ `CashKeys`): تعداد به هزارم (`qtyMilli`)، قیمت و
 *  مبلغِ تخفیف به پول (یک‌صدمِ افغانی) و درصدِ تخفیف به صدمِ درصد (۱۰۰٪ = `LinePricing.FULL_BP`).
 *  بیش از [MAX_WHOLE_DIGITS] رقمِ پیش از ممیز یا بیش از رقم‌های اعشارِ همان حالت پذیرفته نمی‌شود،
 *  پس هر عدد زیرِ ۲^۵۳ می‌ماند و روی آیفون (Kotlin/Native) و JVM یکی است.
 *
 *  Kotlin خالص است — بی java.*، بی android.*، بی کتابخانه.
 */
object Keypad {

  const val VERSION = 1

  /** بیشترین رقمِ پیش از ممیز: ۹ رقم × ۱۰۰۰ (هزارم) هنوز خیلی زیرِ ۲^۵۳ است */
  const val MAX_WHOLE_DIGITS = 9

  /** کدِ ردِ ورودی — صفحه متنِ خودش را می‌گذارد */
  const val BAD_NUMBER = "bad_number"
  const val ZERO = "zero"
  const val PERCENT_OVER = "percent_over"

  enum class Mode(val code: String) {
    QTY("qty"), PRICE("price"), DISCOUNT("discount");

    companion object {
      /** کدِ ناشناخته یا نبودن ⇒ تعداد (بی‌خطرترین: نه پول عوض می‌کند و نه تخفیف می‌دهد) */
      fun of(code: String?): Mode = entries.firstOrNull { it.code == code?.trim() } ?: QTY
    }
  }

  /** کلیدها؛ [digits] رقم‌هایی که کلید می‌افزاید (`null` ⇒ کلیدِ فرمان) */
  enum class Key(val digits: String?) {
    D0("0"), D1("1"), D2("2"), D3("3"), D4("4"), D5("5"), D6("6"), D7("7"), D8("8"), D9("9"),
    D00("00"), DOT(null), BACK(null), CLEAR(null),
  }

  /**
   *  @param text فقط رقمِ لاتین و حداکثر یک «.»؛ خالی ⇒ هنوز چیزی زده نشده
   *  @param percent فقط در حالتِ تخفیف: درصد (پیش‌فرض) یا مبلغ
   */
  data class State(val mode: Mode = Mode.QTY, val text: String = "", val percent: Boolean = true)

  /** رقم‌های اعشارِ هر حالت: تعداد تا هزارم (همان `qty`)، پول و درصد تا صدم */
  fun decimals(mode: Mode): Int = if (mode == Mode.QTY) 3 else 2

  fun press(s: State, key: Key): State = when (key) {
    Key.CLEAR -> s.copy(text = "")
    Key.BACK -> s.copy(text = s.text.dropLast(1))
    Key.DOT -> if ('.' in s.text) s else s.copy(text = s.text.ifEmpty { "0" } + ".")
    else -> {
      var t = s.text
      for (ch in key.digits.orEmpty()) t = append(t, ch, decimals(s.mode))
      s.copy(text = t)
    }
  }

  private fun append(t: String, ch: Char, dec: Int): String {
    val dot = t.indexOf('.')
    if (dot >= 0) return if (t.length - dot - 1 >= dec) t else t + ch
    //  صفرِ پیشین نمی‌ماند: «0» و «5» ⇒ «5»، و «0» و «0» همان «0»
    if (t == "0") return if (ch == '0') t else ch.toString()
    if (t.length >= MAX_WHOLE_DIGITS) return t
    return t + ch
  }

  /** حالتِ تازه با کادرِ خالی (عددِ حالتِ پیشین معنای دیگری داشت)؛ همان حالت ⇒ دست نمی‌خورد */
  fun mode(s: State, m: Mode): State = if (s.mode == m) s else State(mode = m, text = "", percent = s.percent)

  /** درصد ↔ مبلغِ تخفیف؛ رقم‌ها می‌مانند (هر دو تا صدم‌اند) */
  fun percent(s: State, on: Boolean): State = s.copy(percent = on)

  /**
   *  متن ⇒ عددِ صحیح با [dec] رقمِ اعشار. `null` ⇒ متن عدد نیست یا از بازه بیرون است.
   *  «12.5» با dec = 2 ⇒ ۱۲۵۰؛ «.» یا خالی ⇒ `null`.
   */
  fun scaled(text: String, dec: Int): Long? {
    if (dec < 0 || dec > 6) return null
    val dot = text.indexOf('.')
    if (dot >= 0 && text.indexOf('.', dot + 1) >= 0) return null
    val whole = if (dot < 0) text else text.substring(0, dot)
    val frac = if (dot < 0) "" else text.substring(dot + 1)
    if (whole.isEmpty() && frac.isEmpty()) return null
    if (whole.length > MAX_WHOLE_DIGITS || frac.length > dec) return null
    if (!whole.all { it in '0'..'9' } || !frac.all { it in '0'..'9' }) return null
    var v = 0L
    for (c in whole) v = v * 10 + (c - '0')
    for (i in 0 until dec) v = v * 10 + (if (i < frac.length) frac[i] - '0' else 0)
    return v
  }

  sealed interface Entry {
    /** هنوز چیزی زده نشده — «ثبت» کاری نمی‌کند */
    data object Empty : Entry
    /** تعدادِ تازهٔ ردیف به هزارم؛ همیشه مثبت (حذفِ ردیف دکمهٔ جدای خودش را دارد) */
    data class Qty(val milli: Long) : Entry
    /** قیمتِ واحدِ تازه به پول (صفر = رایگان، همان کادرِ ردیف) */
    data class Price(val minor: Long) : Entry
    /** تخفیفِ ردیف: [value] صدمِ درصد اگر [percent]، وگرنه پول؛ همیشه مثبت */
    data class Discount(val percent: Boolean, val value: Long) : Entry
    data class Invalid(val code: String) : Entry
  }

  fun entry(s: State): Entry {
    if (s.text.isEmpty()) return Entry.Empty
    val v = scaled(s.text, decimals(s.mode)) ?: return Entry.Invalid(BAD_NUMBER)
    return when (s.mode) {
      Mode.QTY -> if (v <= 0) Entry.Invalid(ZERO) else Entry.Qty(v)
      Mode.PRICE -> Entry.Price(v)
      Mode.DISCOUNT -> when {
        v <= 0 -> Entry.Invalid(ZERO)
        s.percent && v > LinePricing.FULL_BP -> Entry.Invalid(PERCENT_OVER)
        else -> Entry.Discount(s.percent, v)
      }
    }
  }

  /** عددِ صحیحِ [v] با [dec] رقمِ اعشار ⇒ متنِ ساده («1250»، 2 ⇒ «12.5»)؛ برای کادرهایی که متن می‌گیرند */
  fun plain(v: Long, dec: Int): String {
    require(v >= 0 && dec in 0..6)
    if (dec == 0) return v.toString()
    var div = 1L
    repeat(dec) { div *= 10 }
    val frac = (v % div).toString().padStart(dec, '0').trimEnd('0')
    return (v / div).toString() + if (frac.isEmpty()) "" else ".$frac"
  }
}
