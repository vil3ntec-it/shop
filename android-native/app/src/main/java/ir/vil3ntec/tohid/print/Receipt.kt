package ir.vil3ntec.tohid.print

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.ShopData

/**
 *  فاکتور، به‌صورت تصویر.
 *
 *  چاپگرهای حرارتیِ دکان جدولِ نویسهٔ فارسی ندارند؛ متنِ فارسی را
 *  به‌هم‌ریخته یا علامتِ سؤال چاپ می‌کنند. پس فاکتور را خودِ برنامه
 *  می‌کشد — با همان فونتِ وزیرمتن و همان چیدمانِ راست‌به‌چپ — و آن تصویر
 *  چاپ می‌شود. چیزی که روی کاغذ می‌آید همان است که روی صفحه دیده می‌شود.
 *
 *  از فاکتورِ نسخهٔ ۲ چیدمان در هسته است (`core/receipt`) و این‌جا فقط درِ قدیمیِ
 *  «تصویرِ فاکتور» و ابزارهای قلم می‌ماند؛ شرح در `Receipts`.
 *
 *  عرض بر حسب «نقطه» است نه پیکسل: کاغذِ ۵۸ میلی‌متری ۳۸۴ نقطه دارد و
 *  ۸۰ میلی‌متری ۵۷۶. تصویر دقیقاً به همان عرض کشیده می‌شود تا چاپگر
 *  مجبور نباشد آن را بزرگ یا کوچک کند.
 */
object Receipt {

  /**
   *  تصویرِ فاکتورِ نسخهٔ ۲ به پهنای [width] — همان `Receipts.bitmap`.
   *  [storeName] فقط وقتی به کار می‌آید که سربرگِ دکان نامی ندارد.
   *  [copyMark] — HW-02: «چاپِ دوباره» زیرِ سربرگ؛ `null` ⇒ مثلِ همیشه.
   */
  fun render(
    context: Context,
    d: ShopData,
    sale: Sale,
    storeName: String,
    width: Int,
    copyMark: String? = null,
  ): Bitmap = Receipts.bitmap(context, d, sale, width, copyMark, fallbackName = storeName)

  /* ------------------------------ ابزارها ------------------------------ */

  internal fun font(context: Context, id: Int): Typeface =
    runCatching { ResourcesCompat.getFont(context, id) }.getOrNull() ?: Typeface.DEFAULT

  internal fun paint(typeface: Typeface, size: Float) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
    this.typeface = typeface
    textSize = size
    color = Color.BLACK
  }

  @Suppress("DEPRECATION")
  internal fun layout(text: String, paint: TextPaint, width: Int, align: Layout.Alignment): StaticLayout {
    val w = width.coerceAtLeast(1)
    return if (android.os.Build.VERSION.SDK_INT >= 23) {
      StaticLayout.Builder.obtain(text, 0, text.length, paint, w)
        .setAlignment(align)
        // متن فارسی است، پس جهتِ پایه راست‌به‌چپ است — وگرنه نقطه و
        // پرانتز و عدد سرِ جای اشتباه می‌افتند
        .setTextDirection(android.text.TextDirectionHeuristics.RTL)
        .setIncludePad(false)
        .build()
    } else {
      StaticLayout(text, paint, w, align, 1f, 0f, false)
    }
  }
}
