package ir.vil3ntec.tohid.ui.v3.products

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import ir.vil3ntec.tohid.Jalali
import ir.vil3ntec.tohid.data.ExpiryEngine
import ir.vil3ntec.tohid.data.OpeningStock
import ir.vil3ntec.tohid.ui.v3.products.ProductFormModel.Field
import ir.vil3ntec.tohid.ui.v3.products.ProductFormModel.Problem

/**
 *  بخشِ «اختیاری» ِ فرمِ کالای تازه — «موجودی در انبار»، «تاریخِ تولید» و «تاریخِ انقضا» — خالص (آزمون: `NewProductStockTest`).
 *
 *  تاریخ هشت رقمِ پشتِ هم است (`14050824`) و کادر آن را «۱۴۰۵/۰۸/۲۴» نشان می‌دهد ([DateMask])؛ خورشیدی، و سالِ ≥ ۱۷۰۰
 *  میلادی (همان `Jalali.parseTyped` ِ فرمِ «ورودِ کالا»). قاعده‌ها، درجا زیرِ همان کادر:
 *   • موجودیِ خالی یا ۰ ⇒ بی ورودِ انبار؛ عددِ نادرست ⇒ خطای کادر.
 *   • تاریخ بی موجودی معنا ندارد (روی محمولهٔ همین موجودی می‌نشیند) ⇒ خطای «موجودی».
 *   • تاریخِ ناقص یا نادرست ⇒ خطای همان کادر؛ تولید پس از انقضا ⇒ خطای «تاریخِ تولید» (همان قاعدهٔ موتور).
 *  تاریخِ انقضای گذشته رد نمی‌شود (جنسِ رسیده همان است که هست) ولی زیرِ کادر «منقضی» گفته می‌شود و هشدار می‌گیرد.
 */
object NewProductStock {

  const val DATE_DIGITS = 8

  /** فقط رقم (فارسی/عربی ⇒ لاتین)، حداکثر هشت */
  fun dateDigits(raw: String): String = ir.vil3ntec.tohid.latinDigits(raw).filter { it in '0'..'9' }.take(DATE_DIGITS)

  /** هشت رقم ⇒ `yyyy-MM-dd` ِ دفتر؛ خالی ⇒ ""؛ ناقص یا روزِ نادرست ⇒ null */
  fun iso(digits: String): String? {
    val d = digits.trim()
    if (d.isEmpty()) return ""
    if (d.length != DATE_DIGITS || d.any { it !in '0'..'9' }) return null
    val out = Jalali.parseTyped("${d.substring(0, 4)}-${d.substring(4, 6)}-${d.substring(6, 8)}") ?: return null
    return out.takeIf { ExpiryEngine.validDate(it) }
  }

  /** مقدارِ موجودی: خالی ⇒ ۰؛ نادرست یا منفی ⇒ null */
  fun qty(r: ProductRowDraft): Double? = if (r.stock.isBlank()) 0.0 else r.stock.toDoubleOrNull()?.takeIf { it >= 0 && !it.isNaN() }

  /** خطاهای بخشِ «اختیاری» */
  fun errors(r: ProductRowDraft): Map<Field, Problem> = buildMap {
    val q = qty(r)
    val made = iso(r.made)
    val exp = iso(r.expiry)
    when {
      q == null -> put(Field.STOCK, Problem.NOT_A_NUMBER)
      q == 0.0 && (r.made.isNotBlank() || r.expiry.isNotBlank()) -> put(Field.STOCK, Problem.NEEDS_STOCK)
    }
    if (made == null) put(Field.MADE, Problem.BAD_DATE)
    if (exp == null) put(Field.EXPIRY, Problem.BAD_DATE)
    if (!made.isNullOrEmpty() && !exp.isNullOrEmpty() && made > exp) put(Field.MADE, Problem.MADE_AFTER_EXPIRY)
  }

  /** موجودیِ آغازِ همین ردیف برای موتور؛ بی مقدار (یا خراب) ⇒ null */
  fun stockOf(r: ProductRowDraft): OpeningStock.Stock? {
    val q = qty(r)?.takeIf { it > 0 } ?: return null
    if (errors(r).isNotEmpty()) return null
    return OpeningStock.Stock(q, productionDate = iso(r.made).orEmpty(), expiryDate = iso(r.expiry).orEmpty())
  }

  /** جمعِ ارزشِ موجودیِ آغاز به قیمتِ خرید — برای راهنمای زیرِ کادر؛ null تا هر دو خوانا نیستند */
  fun total(r: ProductRowDraft): Double? {
    val q = qty(r)?.takeIf { it > 0 } ?: return null
    val buy = r.purchase.toDoubleOrNull()?.takeIf { it >= 0 } ?: return null
    return q * buy
  }

  /** حالِ تاریخِ انقضای نوشته‌شده نسبت به [today]: روزهای مانده (منفی = گذشته)، یا null تا تاریخ کامل نیست */
  fun expiryDays(r: ProductRowDraft, today: String): Long? =
    iso(r.expiry)?.takeIf { it.isNotEmpty() }?.let { ExpiryEngine.daysLeft(it, today) }
}

/**
 *  نمایشِ هشت رقمِ تاریخ با جداکننده: `14050824` ⇒ `1405/08/24` (همان شکلِ `formatDate`). مقدارِ خام فقط رقم است؛
 *  جای مکان‌نما با [OffsetMapping] جابه‌جا می‌شود تا پاک کردن و تایپ از وسط درست بماند.
 */
object DateMask : VisualTransformation {
  fun text(raw: String): String = buildString {
    raw.forEachIndexed { i, ch ->
      if (i == 4 || i == 6) append('/')
      append(ch)
    }
  }

  override fun filter(text: AnnotatedString): TransformedText {
    val raw = text.text
    val shown = text(raw)
    return TransformedText(
      AnnotatedString(shown),
      object : OffsetMapping {
        override fun originalToTransformed(offset: Int): Int =
          (offset + (if (offset > 4) 1 else 0) + (if (offset > 6) 1 else 0)).coerceIn(0, shown.length)

        override fun transformedToOriginal(offset: Int): Int =
          (offset - shown.take(offset).count { it == '/' }).coerceIn(0, raw.length)
      },
    )
  }
}
