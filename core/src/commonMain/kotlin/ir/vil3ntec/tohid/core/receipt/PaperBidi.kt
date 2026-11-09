package ir.vil3ntec.tohid.core.receipt

/**
 *  HW-01 — نشانهٔ عدد روی کاغذِ راست‌به‌چپ. «−۵۰ ؋» ِ تخفیف یا «-۱۵۰» ِ ماندهٔ منفی در پاراگرافِ راست‌به‌چپ بی‌نشانه
 *  «۵۰−» چاپ می‌شود (نشانه سمتِ راستِ رقم؛ قاعدهٔ W6/N1 ِ Unicode Bidi)، در حالی که صفحهٔ برنامه
 *  (`MoneyText.signed`) آن را چپِ رقم نشان می‌دهد. یک LRM (U+200E) پیش از نشانه آن را به رقمِ پس از خودش می‌چسباند:
 *  «؋ −۵۰» — همان `minusSign` ِ CLDR برای فارسی («−»)؛ جای «؋»/«افغانی» مثلِ هر مبلغِ دیگرِ کاغذ می‌ماند.
 *
 *  فقط نشانه‌ای که آغازِ عدد است (آغازِ متن یا پس از فاصله یا پرانتز) و پیش از رقمِ لاتین یا فارسی/پشتو (۰–۹)؛ خطِ
 *  تیرهٔ میانِ «B1-1» یا «2026-11-01» دست نمی‌خورد. دوباره زدن همان است. LRM بی‌پهنا و نویسهٔ کنترل است و کشیده
 *  نمی‌شود (شیمِ Java2D: `ReceiptPaperBidiDeskTest`؛ `StaticLayout` ِ اندروید آن را نادیده‌گرفتنی می‌داند). کاغذِ انگلیسی (چپ‌به‌راست) این را لازم ندارد و صدا نمی‌زند (`Receipt.layout`).
 */
object PaperBidi {

  const val LRM = '\u200E'

  private const val SIGNS = "-\u2212+"

  private fun digit(c: Char) = c in '0'..'9' || c in '\u06F0'..'\u06F9'

  /** پیش از آغازِ عدد: فاصله یا پرانتز (LRM ِ پیشین نه ⇒ دوباره زدن همان است) */
  private fun opens(c: Char) = c.isWhitespace() || c == '(' || c == '['

  fun signs(text: String): String {
    if (text.none { it in SIGNS }) return text
    val out = StringBuilder(text.length + 2)
    text.forEachIndexed { i, c ->
      if (c in SIGNS && i + 1 < text.length && digit(text[i + 1]) && (i == 0 || opens(text[i - 1]))) out.append(LRM)
      out.append(c)
    }
    return out.toString()
  }
}
