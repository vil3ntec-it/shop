package ir.vil3ntec.tohid.core.hw

/**
 *  بارکدِ برچسبِ کالا — EAN-13 و Code128 (HW-04، PRD-23).
 *
 *  خروجی «ماژول» است: یک آرایهٔ true/false که هر خانه‌اش یک باریکه‌ترین
 *  خط است. کشیدنِ آن روی برچسب (`render`) جداست تا اندازهٔ خط با چاپگر
 *  جور شود: روی ۲۰۳ نقطه در اینچ، ۲ نقطه = ۰٫۲۵ میلی‌متر — همان
 *  باریک‌ترین خطی که سناریوی S24 برای بارکدخوانِ ارزان می‌خواهد.
 *
 *  ⚠️ بارکدِ چاپ‌شده **همان** رشته‌ای است که روی کالا ثبت شده — هیچ رقمی
 *  افزوده یا کم نمی‌شود؛ وگرنه اسکنِ برچسب کالای دیگری را پیدا می‌کرد یا
 *  هیچ. پس فقط EAN-13ِ درست EAN-13 چاپ می‌شود و بقیه Code128.
 */
object Barcodes {

  enum class Kind { EAN13, CODE128 }

  data class Symbol(val kind: Kind, val text: String, val modules: BooleanArray)

  /** نوعِ مناسب برای کدِ یک کالا؛ کدی که در هیچ‌کدام نمی‌گنجد ⇒ `null` */
  fun forProduct(code: String): Symbol? {
    val c = code.trim()
    if (c.isEmpty()) return null
    if (Ean13.isValid(c)) return Symbol(Kind.EAN13, c, Ean13.modules(c))
    return runCatching { Symbol(Kind.CODE128, c, Code128.modules(c)) }.getOrNull()
  }

  /**
   *  ماژول‌ها به تصویر: هر ماژول `moduleDots` نقطه پهنا، با حاشیهٔ آرامِ
   *  `quiet` ماژول در دو طرف (بی آن بارکدخوان لبه را پیدا نمی‌کند).
   */
  fun render(modules: BooleanArray, moduleDots: Int, heightDots: Int, quiet: Int = 10): MonoImage {
    require(moduleDots >= 1 && heightDots >= 1)
    val img = MonoImage((modules.size + quiet * 2) * moduleDots, heightDots)
    modules.forEachIndexed { m, black -> if (black) img.fill((quiet + m) * moduleDots, 0, moduleDots, heightDots) }
    return img
  }

  fun modulesString(m: BooleanArray): String = buildString(m.size) { m.forEach { append(if (it) '1' else '0') } }
}

object Ean13 {

  private val L = arrayOf("0001101", "0011001", "0010011", "0111101", "0100011", "0110001", "0101111", "0111011", "0110111", "0001011")
  private val G = arrayOf("0100111", "0110011", "0011011", "0100001", "0011101", "0111001", "0000101", "0010001", "0001001", "0010111")
  private val R = arrayOf("1110010", "1100110", "1101100", "1000010", "1011100", "1001110", "1010000", "1000100", "1001000", "1110100")
  /** رقمِ اول با الگوی L/G شش رقمِ نیمهٔ چپ رمز می‌شود */
  private val PARITY = arrayOf("LLLLLL", "LLGLGG", "LLGGLG", "LLGGGL", "LGLLGG", "LGGLLG", "LGGGLG", "LGLGLG", "LGLGGL", "LGGLGL")

  /** رقمِ کنترلِ ۱۲ رقمِ اول */
  fun checkDigit(first12: String): Int {
    require(first12.length == 12 && first12.all { it in '0'..'9' }) { "EAN-13 دوازده رقم می‌خواهد" }
    var sum = 0
    first12.forEachIndexed { i, ch -> sum += (ch - '0') * (if (i % 2 == 0) 1 else 3) }
    return (10 - sum % 10) % 10
  }

  fun isValid(code: String): Boolean =
    code.length == 13 && code.all { it in '0'..'9' } && checkDigit(code.substring(0, 12)) == code[12] - '0'

  /** ۹۵ ماژول: 101 · ۶×۷ · 01010 · ۶×۷ · 101 */
  fun modules(code: String): BooleanArray {
    require(isValid(code)) { "EAN-13 نادرست: $code" }
    val parity = PARITY[code[0] - '0']
    val s = StringBuilder("101")
    for (i in 1..6) {
      val d = code[i] - '0'
      s.append(if (parity[i - 1] == 'L') L[d] else G[d])
    }
    s.append("01010")
    for (i in 7..12) s.append(R[code[i] - '0'])
    s.append("101")
    return BooleanArray(s.length) { s[it] == '1' }
  }
}

object Code128 {

  /** پهنای خط/فاصلهٔ هر نماد (۰ تا ۱۰۶)؛ ۱۰۶ = پایان، هفت پهنا */
  private val WIDTHS = arrayOf(
    "212222", "222122", "222221", "121223", "121322", "131222", "122213", "122312", "132212", "221213",
    "221312", "231212", "112232", "122132", "122231", "113222", "123122", "123221", "223211", "221132",
    "221231", "213212", "223112", "312131", "311222", "321122", "321221", "312212", "322112", "322211",
    "212123", "212321", "232121", "111323", "131123", "131321", "112313", "132113", "132311", "211313",
    "231113", "231311", "112133", "112331", "132131", "113123", "113321", "133121", "313121", "211331",
    "231131", "213113", "213311", "213131", "311123", "311321", "331121", "312113", "312311", "332111",
    "314111", "221411", "431111", "111224", "111422", "121124", "121421", "141122", "141221", "112214",
    "112412", "122114", "122411", "142112", "142211", "241211", "221114", "413111", "241112", "134111",
    "111242", "121142", "121241", "114212", "124112", "124211", "411212", "421112", "421211", "212141",
    "214121", "412121", "111143", "111341", "131141", "114113", "114311", "411113", "411311", "113141",
    "114131", "311141", "411131", "211412", "211214", "211232", "2331112",
  )

  const val START_B = 104
  const val START_C = 105
  const val TO_B = 100
  const val TO_C = 99
  const val STOP = 106

  fun widths(symbol: Int): String = WIDTHS[symbol]

  private fun digitsAt(s: String, i: Int): Int {
    var n = 0
    while (i + n < s.length && s[i + n] in '0'..'9') n++
    return n
  }

  /**
   *  نمادها، با رقمِ کنترل و پایان. دستهٔ C (دو رقم در یک نماد) برای
   *  رشته‌ای که با چهار رقم یا بیشتر شروع شود، یا میانه‌ای با شش رقم یا
   *  بیشتر (چهار رقم اگر تا آخر رشته باشد)؛ بقیه دستهٔ B.
   *
   *  فقط نویسه‌های ASCII ۳۲ تا ۱۲۶ — رقمِ فارسی را پیش از این باید به لاتین
   *  برگرداند؛ حرفِ فارسی در Code128 جا ندارد و خطا می‌دهد.
   */
  fun symbols(text: String): IntArray {
    require(text.isNotEmpty()) { "بارکدِ خالی" }
    text.forEach { require(it.code in 32..126) { "نویسهٔ «$it» در Code128 نمی‌گنجد" } }
    val n = text.length
    val out = ArrayList<Int>()
    val lead = digitsAt(text, 0)
    var setC = (lead == n && n >= 2 && n % 2 == 0) || lead >= 4
    out += if (setC) START_C else START_B
    var i = 0
    while (i < n) {
      if (setC) {
        if (digitsAt(text, i) >= 2) {
          out += (text[i] - '0') * 10 + (text[i + 1] - '0'); i += 2
        } else {
          out += TO_B; setC = false
        }
      } else {
        val run = digitsAt(text, i)
        if (run >= 6 || (run >= 4 && i + run == n)) {
          if (run % 2 == 1) { out += text[i].code - 32; i += 1 }
          out += TO_C; setC = true
        } else {
          out += text[i].code - 32; i += 1
        }
      }
    }
    var sum = out[0]
    for (k in 1 until out.size) sum += out[k] * k
    out += sum % 103
    out += STOP
    return out.toIntArray()
  }

  fun modules(text: String): BooleanArray {
    val s = StringBuilder()
    for (sym in symbols(text)) {
      WIDTHS[sym].forEachIndexed { k, w -> repeat(w - '0') { s.append(if (k % 2 == 0) '1' else '0') } }
    }
    return BooleanArray(s.length) { s[it] == '1' }
  }
}
