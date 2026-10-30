package ir.vil3ntec.tohid.core.pick

import ir.vil3ntec.tohid.core.qr.QrCode

/**
 *  کدِ تحویل/دریافت به شکلِ عدد یا QR — PCK-11، DLV-15، سناریوی S43.
 *
 *  سرور کدِ شش‌رقمی را فقط یک بار نشان می‌دهد و فقط HMACش را نگه می‌دارد (`server/src/lib/delivery.js`)؛
 *  این‌جا هیچ رازی ساخته یا نگه داشته نمی‌شود. فقط دو کار:
 *
 *  ۱) **QR**: متنِ `VLNH-<شمارهٔ سفارش>-<کد>` تا دکان با اسکنِ گوشیِ مشتری هم سفارش را پیدا کند و هم
 *     کد را داشته باشد («اسکنِ کد، تطبیقِ نام، تحویل شد»). شمارهٔ سفارش راز نیست؛ کد همان کدی است که
 *     مشتری به هر حال نشان می‌دهد.
 *  ۲) **خواندن**: هر چه در کادر آمد — شش رقمِ تایپی، رقمِ فارسی/عربی، یا QRی که بارکدخوانِ صفحه‌کلیدی
 *     (HID) با هر چیدمانِ صفحه‌کلید زده — به کدِ شش‌رقمیِ لاتین (همان که سرور می‌خواهد) و شمارهٔ سفارش.
 *     بارکدخوان با چیدمانِ فارسی حرف‌های لاتین را عوض می‌کند، پس پیشوند و جداکننده فقط «غیرِ رقم»اند و
 *     هرگز سنجیده نمی‌شوند؛ تصمیم فقط با رقم‌هاست.
 *
 *  ⛔ منطقِ خالص (commonMain، بی `java.*`) تا آیفون و میزِ تحویلِ نسخهٔ ۳ (#400) همین را بخوانند.
 */
object HandoverCode {

  /** طولِ کد — همان `newCode` ِ سرور */
  const val LENGTH = 6

  /** پیشوندِ متنِ QR — فقط حرف (بی رقم) تا با رقم‌های پس از آن قاطی نشود */
  const val PREFIX = "VLNH"

  /** بیشترین شمارهٔ سفارشی که در QR می‌رود (۱۲ رقم) */
  const val MAX_ORDER_NO = 999_999_999_999L

  /** کادر بیش از این را نمی‌خواند (اسکنِ خراب یا چسباندنِ متنِ دراز) */
  private const val MAX_INPUT = 64

  /** نتیجهٔ خواندن: `code` شش رقمِ لاتین یا null؛ `orderNo` فقط وقتی QR بود */
  data class Scan(val code: String?, val orderNo: Long?) {
    val isPayload: Boolean get() = orderNo != null
  }

  /** آیا دقیقاً شش رقمِ لاتین است؟ */
  fun isCode(code: String): Boolean = code.length == LENGTH && code.all { it in '0'..'9' }

  /** متنِ QR. ورودیِ نامعتبر ⇒ IllegalArgumentException (کد را همیشه سرور می‌سازد). */
  fun payload(orderNo: Long, code: String): String {
    require(orderNo in 1..MAX_ORDER_NO) { "شمارهٔ سفارش برای QR معتبر نیست" }
    require(isCode(code)) { "کدِ تحویل باید شش رقم باشد" }
    return "$PREFIX-$orderNo-$code"
  }

  /** ماتریسِ QR (`rows[y][x]`، true = سیاه، بی حاشیه) یا null اگر ساخته نشود */
  fun matrix(orderNo: Long, code: String): Array<BooleanArray>? =
    runCatching { QrCode.encode(payload(orderNo, code)) }.getOrNull()

  /** رقمِ فارسی (۰–۹) و عربی (٠–٩) ⇒ لاتین؛ بقیه دست‌نخورده */
  fun latin(raw: String): String = buildString(raw.length) {
    for (ch in raw) append(
      when (ch) {
        in '۰'..'۹' -> '0' + (ch - '۰')
        in '٠'..'٩' -> '0' + (ch - '٠')
        else -> ch
      },
    )
  }

  /**
   *  هر ورودیِ کادر ⇒ `Scan`.
   *  - QR: دست‌کم دو گروهِ رقم که جدا کنندهٔ میانشان چیزی جز فاصله هم دارد و گروهِ آخر شش رقم است ⇒
   *    گروهِ آخر کد و گروهِ پیش از آن شمارهٔ سفارش (۱ تا ۱۲ رقم، بی صفرِ آغازین).
   *  - وگرنه همهٔ رقم‌ها پشتِ هم (تا «۱۲۳ ۴۵۶» هم کد باشد)؛ دقیقاً شش رقم ⇒ کد.
   */
  fun parse(raw: String): Scan {
    val text = latin(raw.take(MAX_INPUT))
    val groups = DIGITS.findAll(text).toList()
    if (groups.size >= 2) {
      val last = groups[groups.size - 1]
      val prev = groups[groups.size - 2]
      val gap = text.substring(prev.range.last + 1, last.range.first)
      if (last.value.length == LENGTH && gap.any { !it.isWhitespace() }) {
        val no = orderNoOf(prev.value)
        if (no != null) return Scan(last.value, no)
      }
    }
    val digits = groups.joinToString("") { it.value }
    return Scan(if (isCode(digits)) digits else null, null)
  }

  /**
   *  پالایهٔ کادرِ «کدِ تحویل»: اسکنِ QR ⇒ همان شش رقم؛ تایپ ⇒ فقط رقم‌ها (لاتین) تا شش‌تا.
   *  تایپِ رقم‌به‌رقم همان رفتارِ پیشین است (`filter { isDigit }.take(6)`) با رقمِ فارسی هم.
   */
  fun typed(raw: String): String {
    val s = parse(raw)
    if (s.code != null) return s.code
    return latin(raw.take(MAX_INPUT)).filter { it in '0'..'9' }.take(LENGTH)
  }

  /** QRِ سفارشِ دیگری اسکن شد؟ (فقط QR شمارهٔ سفارش دارد؛ عددِ تایپی هرگز «دیگری» نیست) */
  fun otherOrder(raw: String, orderNo: Long): Long? =
    parse(raw).orderNo?.takeIf { it != orderNo }

  /** نمایشِ کد سه‌تا‌سه‌تا («123 456») — خواناتر برای خواندن به مشتری */
  fun spaced(code: String): String =
    if (isCode(code)) "${code.substring(0, 3)} ${code.substring(3)}" else code

  private val DIGITS = Regex("[0-9]+")

  private fun orderNoOf(s: String): Long? {
    if (s.isEmpty() || s.length > 12 || s[0] == '0') return null
    return s.toLongOrNull()?.takeIf { it in 1..MAX_ORDER_NO }
  }
}
