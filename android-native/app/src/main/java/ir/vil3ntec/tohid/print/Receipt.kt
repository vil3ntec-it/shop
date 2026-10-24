package ir.vil3ntec.tohid.print

import ir.vil3ntec.tohid.data.invoiceLabel
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import ir.vil3ntec.tohid.R
import ir.vil3ntec.tohid.data.Debtor
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.SaleItem
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.LocaleChoice
import ir.vil3ntec.tohid.LocaleText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 *  فاکتور، به‌صورت تصویر.
 *
 *  چاپگرهای حرارتیِ دکان جدولِ نویسهٔ فارسی ندارند؛ متنِ فارسی را
 *  به‌هم‌ریخته یا علامتِ سؤال چاپ می‌کنند. پس فاکتور را خودِ اندروید
 *  می‌کشد — با همان فونتِ وزیرمتن و همان چیدمانِ راست‌به‌چپ — و آن تصویر
 *  چاپ می‌شود. چیزی که روی کاغذ می‌آید همان است که روی صفحه دیده می‌شود.
 *
 *  عرض بر حسب «نقطه» است نه پیکسل: کاغذِ ۵۸ میلی‌متری ۳۸۴ نقطه دارد و
 *  ۸۰ میلی‌متری ۵۷۶. تصویر دقیقاً به همان عرض کشیده می‌شود تا چاپگر
 *  مجبور نباشد آن را بزرگ یا کوچک کند.
 */
object Receipt {

  /**
   *  تصویرِ فاکتورِ فروش، نسخهٔ ۲ — همان `Receipts.bitmap` (چیدمان در `core/receipt`).
   *  [storeName] فقط وقتی به کار می‌آید که سربرگِ دکان نامی ندارد.
   *  [copyMark] — HW-02: «چاپِ دوباره» زیرِ سربرگ.
   *  [qr] — RET-01: کدِ `TR1:` ِ برگشت، فقط برای فروشی که توکنِ QR ندارد (فروشِ پیش از نسخهٔ
   *  ۲)؛ فروشِ تازه یک QR دارد، نشانیِ عمومیِ همان فروش، که اسکنرِ برگشت هم می‌خواند.
   *  پس از [copyMark]، چون `HwPages` آن را بی نام (ششمین) می‌دهد.
   */
  fun render(
    context: Context,
    d: ShopData,
    sale: Sale,
    storeName: String,
    width: Int,
    copyMark: String? = null,
    qr: String? = null,
  ): Bitmap = Receipts.bitmap(context, d, sale, width, copyMark, fallbackName = storeName, legacyQr = qr)

  /**
   *  رسیدِ برگشت: سندِ `ReturnFlow` (ردیف‌ها، مقصدِ هر کالا، تقسیمِ بازپرداخت،
   *  دلیل و تأیید)؛ در تعویض، بخشِ دومِ همان رسید فاکتورِ تازه و «دریافت/پرداختِ
   *  تفاوت» است (S15: یک رسید با دو بخش).
   */
  fun renderReturn(context: Context, d: ShopData, docId: String, storeName: String, width: Int): Bitmap {
    val doc = ir.vil3ntec.tohid.data.ReturnFlow.doc(d, docId) ?: error("سندِ برگشت پیدا نشد")
    val regular = font(context, R.font.vazirmatn_regular)
    val bold = font(context, R.font.vazirmatn_bold)
    val pad = (width * 0.04f)
    val inner = width - pad * 2
    val p = Pens(paint(bold, width * 0.075f), paint(bold, width * 0.048f), paint(regular, width * 0.044f), paint(regular, width * 0.038f))
    val height = drawReturn(null, d, doc, storeName, width, pad, inner, p)
    val bitmap = Bitmap.createBitmap(width, height.toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.WHITE)
    drawReturn(canvas, d, doc, storeName, width, pad, inner, p)
    return bitmap
  }

  private class Pens(val title: TextPaint, val head: TextPaint, val body: TextPaint, val small: TextPaint)

  private fun drawReturn(
    canvas: Canvas?,
    d: ShopData,
    doc: ir.vil3ntec.tohid.data.ReturnFlow.Doc,
    storeName: String,
    width: Int,
    pad: Float,
    inner: Float,
    p: Pens,
  ): Float {
    var y = pad
    val digits = LocaleChoice.digits
    val symbol = LocaleChoice.symbol
    fun num(value: Double) = LocaleText.amount(value, digits)
    fun afn(value: Double) = LocaleText.afghani(value, digits, symbol)
    fun line(text: String, paint: TextPaint, align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL) {
      val l = layout(text, paint, inner.toInt(), align)
      if (canvas != null) { canvas.save(); canvas.translate(pad, y); l.draw(canvas); canvas.restore() }
      y += l.height
    }
    fun row(label: String, value: String, paint: TextPaint) {
      val half = (inner / 2).toInt()
      val right = layout(label, paint, half, Layout.Alignment.ALIGN_NORMAL)
      val left = layout(value, paint, half, Layout.Alignment.ALIGN_OPPOSITE)
      if (canvas != null) {
        canvas.save(); canvas.translate(pad + inner - half, y); right.draw(canvas); canvas.restore()
        canvas.save(); canvas.translate(pad, y); left.draw(canvas); canvas.restore()
      }
      y += maxOf(right.height, left.height)
    }
    fun rule(thick: Boolean = false) {
      y += pad * 0.4f
      if (canvas != null) {
        val ink = Paint().apply { color = Color.BLACK; strokeWidth = if (thick) width * 0.006f else width * 0.003f }
        canvas.drawLine(pad, y, width - pad, y, ink)
      }
      y += pad * 0.4f
    }
    fun gap(f: Float = 0.5f) { y += pad * f }

    line(storeName.ifBlank { "فروشگاه" }, p.title, Layout.Alignment.ALIGN_CENTER)
    gap(0.3f)
    line(LocaleText.digitsIn("رسید برگشت ${doc.label}", digits), p.head, Layout.Alignment.ALIGN_CENTER)
    gap(0.2f)
    line(
      LocaleText.digitsIn(doc.sale?.let { "از فاکتور #${it.invoiceLabel() ?: "—"}" } ?: "برگشتِ بی رسید", digits),
      p.small, Layout.Alignment.ALIGN_CENTER,
    )
    val time = runCatching { SimpleDateFormat("HH:mm", Locale.US).format(Date(if (doc.createdAt > 0) doc.createdAt else System.currentTimeMillis())) }.getOrDefault("")
    line("${LocaleText.date(doc.date, digits)} — ${LocaleText.digitsIn(time, digits)}", p.small, Layout.Alignment.ALIGN_CENTER)
    rule(thick = true)

    doc.rows.forEach { r ->
      line(ir.vil3ntec.tohid.data.ReturnFlow.rowName(d, r), p.body)
      row("${num(r.quantity)} · ${ir.vil3ntec.tohid.data.ReturnFlow.dispositionName(r.disposition)}", num(r.amount), p.small)
      gap(0.25f)
    }
    rule()
    row("مبلغ برگشت", afn(doc.total), p.head)
    if (doc.split.account > 0) row("کم از بدهی", afn(doc.split.account), p.body)
    if (doc.split.credit > 0) row("اعتبار فروشگاه", afn(doc.split.credit), p.body)
    doc.split.methods.forEach { (m, a) -> row("پس به ${ir.vil3ntec.tohid.data.ReturnFlow.methodName(m)}", afn(a), p.body) }
    if (doc.split.cash > 0) row("پس‌داده نقد", afn(doc.split.cash), p.body)

    doc.exchange?.let { ex ->
      rule(thick = true)
      line(LocaleText.digitsIn("خرید تازه — فاکتور #${ex.invoiceLabel() ?: "—"}", digits), p.head)
      d.saleItems.filter { it.saleId == ex.id }.forEach { si ->
        line(ir.vil3ntec.tohid.data.SalesEngine.itemName(d, si), p.body)
        row("${num(si.quantity)} × ${num(si.unitPrice)}", num(si.totalPrice), p.small)
      }
      rule()
      row("جمع خرید تازه", afn(ex.finalTotal), p.body)
      val due = doc.due ?: 0.0
      row(if (due >= 0) "دریافت تفاوت" else "پرداخت تفاوت", afn(kotlin.math.abs(due)), p.head)
    }

    rule(thick = true)
    line("دلیل: ${doc.reason}", p.small)
    if (doc.approvedBy.isNotBlank()) line("تأیید: ${doc.approvedBy}", p.small)
    if (doc.by.isNotBlank()) line("ثبت: ${doc.by}", p.small)
    gap(0.4f)
    line("سند برگشت جدا ثبت شد؛ فاکتور اصلی دست نخورده است", p.small, Layout.Alignment.ALIGN_CENTER)
    y += pad * 1.5f
    return y
  }

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
