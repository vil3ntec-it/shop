package ir.vil3ntec.tohid.core.pos

/**
 *  صفحه‌کلیدِ **متنیِ** روی‌صفحه برای صندوقِ کامپیوترِ لمسی (POS-31، S18 گامِ ۳؛ §۵.۳) — جست‌وجوی کالا با نام بی
 *  کیبوردِ فیزیکی. سه چیدمان: فارسی/دری (همان حروفِ فارسی؛ دری همان متن است)، پښتو (حرف‌های ویژهٔ پښتو هم) و لاتین.
 *
 *  ── پیش‌فرض خاموش ─────────────────────────────────────────────────
 *  فقط کامپیوترِ لمسی ([TouchMode.touchExtras]: تنظیمِ «حالتِ لمسیِ صندوق» روشن و کلیدِ نوار روشن) کلیدِ
 *  «صفحه‌کلیدِ حروف» را می‌بیند، و صفحه‌کلید فقط با زدنِ همان کلید باز می‌شود. خاموش ⇒ صندوق بیت‌به‌بیت همان امروز.
 *
 *  Kotlin خالص است — بی java.*، بی android.*، بی کتابخانه. نوشته‌ها (نامِ کلیدهای ویژه) در `StrTouch` ِ برنامه‌اند.
 */
object TouchKeyboard {

  /** چیدمان‌ها به ترتیبِ چرخش با کلیدِ [Key.NextLayout] */
  enum class Layout { FA, PS, LATIN }

  sealed interface Key {
    /** یک حرف یا رقم */
    data class Letter(val text: String) : Key

    data object Space : Key

    /** نیم‌فاصله (ZWNJ) — فقط چیدمانِ فارسی/دری و پښتو */
    data object HalfSpace : Key

    data object Backspace : Key

    data object Clear : Key

    data object NextLayout : Key
  }

  const val ZWNJ = "‌"

  /** سقفِ نوشته — کادرِ جست‌وجو نامِ کالا می‌خواهد، نه متنِ بلند */
  const val MAX = 80

  private val FA_DIGITS = "۱۲۳۴۵۶۷۸۹۰"
  private val FA_ROWS = listOf("ضصثقفغعهخحجچ", "شسیبلاتنمکگ", "ظطزرذدپوژآئ")
  /** پښتو: «ګ» ِ پښتو به‌جای «گ»، و ردیفِ حرف‌های ویژه */
  private val PS_ROWS = listOf("ضصثقفغعهخحجچ", "شسیبلاتنمکګ", "ظطزرذدپوژآئ", "ټډړږښځڅڼېۍ")
  private val LATIN_ROWS = listOf("1234567890", "qwertyuiop", "asdfghjkl", "zxcvbnm")

  private fun letters(row: String): List<Key> = row.map { Key.Letter(it.toString()) }

  /** ردیف‌های کلید از بالا به پایین؛ ردیفِ آخر همیشه کلیدهای ویژه است */
  fun rows(layout: Layout): List<List<Key>> {
    val body = when (layout) {
      Layout.FA -> listOf(FA_DIGITS) + FA_ROWS
      Layout.PS -> listOf(FA_DIGITS) + PS_ROWS
      Layout.LATIN -> LATIN_ROWS
    }.map(::letters)
    val special = buildList {
      add(Key.NextLayout)
      if (layout != Layout.LATIN) add(Key.HalfSpace)
      add(Key.Space)
      add(Key.Backspace)
      add(Key.Clear)
    }
    return body + listOf(special)
  }

  /** کلیدِ «چیدمانِ بعد» به کجا می‌برد */
  fun next(layout: Layout): Layout = Layout.entries[(layout.ordinal + 1) % Layout.entries.size]

  /** چیدمانِ نخست از زبانِ برنامه: پښتو ⇒ پښتو، انگلیسی ⇒ لاتین، بقیه (فارسی، دری) ⇒ فارسی/دری */
  fun initial(lang: String): Layout = when (lang.lowercase()) {
    "ps" -> Layout.PS
    "en" -> Layout.LATIN
    else -> Layout.FA
  }

  /**
   *  نوشتهٔ تازهٔ کادر پس از زدنِ [key]. فاصله و نیم‌فاصله در آغاز یا پشتِ سرِ فاصلهٔ دیگر نمی‌نشینند؛ بیش از [max]
   *  نویسه پذیرفته نمی‌شود؛ «پاک کردنِ آخر» یک نویسه (حرف، رقم یا نیم‌فاصله) را برمی‌دارد.
   */
  fun apply(text: String, key: Key, max: Int = MAX): String = when (key) {
    is Key.Letter -> if (text.length + key.text.length > max) text else text + key.text
    Key.Space -> if (gapOk(text, max)) "$text " else text
    Key.HalfSpace -> if (gapOk(text, max)) text + ZWNJ else text
    Key.Backspace -> text.dropLast(1)
    Key.Clear -> ""
    Key.NextLayout -> text
  }

  private fun gapOk(text: String, max: Int): Boolean =
    text.isNotEmpty() && text.length < max && !text.endsWith(" ") && !text.endsWith(ZWNJ)
}
