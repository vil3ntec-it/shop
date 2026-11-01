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
import ir.vil3ntec.tohid.data.ReceiptFlag
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.SaleItem
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.LocaleChoice
import ir.vil3ntec.tohid.LocaleText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.StrPos

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

  fun render(
    context: Context,
    d: ShopData,
    sale: Sale,
    storeName: String,
    width: Int,
    /** HW-02: «چاپِ دوباره» زیرِ سربرگ — رسیدِ دوم با رسیدِ اول اشتباه نشود. `null` ⇒ مثلِ همیشه */
    copyMark: String? = null,
    /** RET-01 — متنِ QRِ رسید (`ReturnFlow.receiptCode`)؛ فقط با برگشتِ تازه. `null` ⇒ رسیدِ امروز، بی QR.
     *  پس از [copyMark]، چون `HwPages` آن را بی نام (ششمین) می‌دهد. */
    qr: String? = null,
  ): Bitmap {
    //  فاکتورِ نسخهٔ ۲ (`Receipts`، چیدمان در `core/receipt`) فقط با پرچمِ `shop.receipt_v2`؛
    //  خاموش ⇒ همین چیدمانِ پیشین، بیت‌به‌بیت. فروشِ بی توکن کدِ [qr] را (اگر هست) نگه می‌دارد.
    if (ReceiptFlag.enabled(context)) return Receipts.bitmap(context, d, sale, width, copyMark, fallbackName = storeName, legacyQr = qr)
    val regular = font(context, R.font.vazirmatn_regular)
    val bold = font(context, R.font.vazirmatn_bold)

    val pad = (width * 0.04f)
    val inner = width - pad * 2

    val title = paint(bold, width * 0.075f)
    val head = paint(bold, width * 0.048f)
    val body = paint(regular, width * 0.044f)
    val small = paint(regular, width * 0.038f)

    val items = d.saleItems.filter { it.saleId == sale.id }
    val debtor = sale.debtorId?.let { id -> d.debtors.find { it.id == id } }

    // یک بار برای اندازه‌گیری، یک بار برای کشیدن — تا ارتفاعِ تصویر
    // دقیقاً اندازهٔ محتوا باشد و کاغذِ خالی هدر نرود
    val height = draw(null, context, d, sale, debtor, items, storeName, width, pad, inner, title, head, body, small, qr, copyMark)
    val bitmap = Bitmap.createBitmap(width, height.toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.WHITE)
    draw(canvas, context, d, sale, debtor, items, storeName, width, pad, inner, title, head, body, small, qr, copyMark)
    return bitmap
  }

  /** برمی‌گرداند: ارتفاعِ لازم. اگر canvas داده شود، همان‌جا هم می‌کشد. */
  private fun draw(
    canvas: Canvas?,
    context: Context,
    d: ShopData,
    sale: Sale,
    debtor: Debtor?,
    items: List<SaleItem>,
    storeName: String,
    width: Int,
    pad: Float,
    inner: Float,
    title: TextPaint,
    head: TextPaint,
    body: TextPaint,
    small: TextPaint,
    qr: String? = null,
    copyMark: String? = null,
  ): Float {
    var y = pad

    fun line(text: String, paint: TextPaint, align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL) {
      val layout = layout(text, paint, inner.toInt(), align)
      if (canvas != null) {
        canvas.save()
        canvas.translate(pad, y)
        layout.draw(canvas)
        canvas.restore()
      }
      y += layout.height
    }

    /** یک ردیفِ دوستونی: برچسب راست، عدد چپ */
    fun row(label: String, value: String, paint: TextPaint) {
      val half = (inner / 2).toInt()
      val right = layout(label, paint, half, Layout.Alignment.ALIGN_NORMAL)
      val left = layout(value, paint, half, Layout.Alignment.ALIGN_OPPOSITE)
      //  راست‌به‌چپ: برچسب راست، مبلغ چپ؛ انگلیسی برعکس
      val rtl = I18n.lang.rtl
      if (canvas != null) {
        canvas.save(); canvas.translate(if (rtl) pad + inner - half else pad, y); right.draw(canvas); canvas.restore()
        canvas.save(); canvas.translate(if (rtl) pad else pad + inner - half, y); left.draw(canvas); canvas.restore()
      }
      y += maxOf(right.height, left.height)
    }

    fun rule(thick: Boolean = false) {
      y += pad * 0.4f
      if (canvas != null) {
        val p = Paint().apply {
          color = Color.BLACK
          strokeWidth = if (thick) width * 0.006f else width * 0.003f
        }
        canvas.drawLine(pad, y, width - pad, y, p)
      }
      y += pad * 0.4f
    }

    fun gap(factor: Float = 0.5f) { y += pad * factor }

    //  رقمِ فاکتور همان انتخابِ دکان است (LOC-05)؛ مبلغ بی اعشار با «افغانی» یا «؋» (LOC-07).
    //  پیش‌فرض (لاتین، «افغانی») دقیقاً همان متنِ پیشین است (`LocaleTextTest`).
    //  زبانِ فاکتور همان زبانِ برنامه است؛ انگلیسی همیشه رقمِ لاتین و میلادی (مگر خودِ دکان‌دار تقویم را عوض کرده باشد)
    val digits = LocaleChoice.screenDigits
    val symbol = LocaleChoice.symbol
    fun num(value: Double) = LocaleText.amount(value, digits)
    fun afn(value: Double) = I18n.money(value, I18n.lang, digits, symbol)
    //  S3 — پرچمِ `shop.promo_stamp`: خاموش ⇒ همان متن و ردیف‌های پیشین (`SaleReceipt`)
    val stamped = ir.vil3ntec.tohid.ui.PromoLive.stampOn(context)

    /* ------------------------------ سربرگ ------------------------------ */
    line(storeName.ifBlank { StrSale.storeFallback }, title, Layout.Alignment.ALIGN_CENTER)
    gap(0.3f)
    line(LocaleText.digitsIn(StrSale.saleInvoiceNo(sale.invoiceLabel() ?: "—"), digits), head, Layout.Alignment.ALIGN_CENTER)
    gap(0.3f)
    if (copyMark != null) { line(copyMark, head, Layout.Alignment.ALIGN_CENTER); gap(0.3f) }

    val time = runCatching {
      SimpleDateFormat("HH:mm", Locale.US).format(Date(if (sale.createdAt > 0) sale.createdAt else System.currentTimeMillis()))
    }.getOrDefault("")
    val kind = when {
      sale.status == "cancelled" -> StrSale.cancelled
      //  PAY (v3 A4): از `payments` اگر هست (کارت، حواله، کیف پول، ترکیبی…)؛ وگرنه همان امروز
      else -> ir.vil3ntec.tohid.data.PayFlow.payLabel(sale, cash = Str.cashSale)
    }
    line("${LocaleText.digitsIn(ir.vil3ntec.tohid.displayDate(sale.date), digits)} — ${LocaleText.digitsIn(time, digits)}   ($kind)", small, Layout.Alignment.ALIGN_CENTER)
    //  LOC-01: تاریخِ قمری فقط با تنظیمِ دکان (پیش‌فرض خاموش)
    LocaleChoice.lunarOn(sale.date)?.let { line(StrSale.hijriDate(it), small, Layout.Alignment.ALIGN_CENTER) }

    rule(thick = true)

    /* ------------------------------ اقلام ------------------------------ */
    items.forEach { item ->
      val product = d.products.find { it.id == item.productId }
      val name = ir.vil3ntec.tohid.data.SalesEngine.itemName(d, item)
      val unit = product?.unit.orEmpty()
      line(name, body)
      val detail = ir.vil3ntec.tohid.data.SaleReceipt.detail(item, unit, stamped, ::num,
        { LocaleText.unitAmount(it, digits) }, { LocaleText.digitsIn(ir.vil3ntec.tohid.qty(it), digits) })
      row(detail, num(item.totalPrice), small)
      if (item.returnedQty > 0) line(StrSale.returned(num(item.returnedQty)), small)
      //  POS-11: یادداشتِ ردیف؛ بی یادداشت ⇒ همان چیدمانِ پیشین
      item.note?.takeIf { it.isNotBlank() }?.let { line(it, small) }
      gap(0.25f)
    }

    rule()

    /* ------------------------------ جمع‌ها ------------------------------ */
    row(StrSale.itemsTotal, afn(sale.total), body)
    //  «جمع اقلام» = جمعِ ستونِ بالا؛ تخفیفِ خودکار فقط آگاهی است و دوباره کم نمی‌شود
    ir.vil3ntec.tohid.data.SaleReceipt.included(sale, items, stamped, ir.vil3ntec.tohid.ui.PromoLive::ruleName)
      ?.let { row(it.label, afn(it.amount), body) }
    row(Str.discount, afn(sale.discount), body)
    rule()
    row(StrSale.finalAmount, afn(sale.finalTotal), head)
    row(StrSale.paid, afn(sale.paidAmount), body)
    row(StrSale.remaining, afn(sale.remaining), body)
    //  PAY (v3 A4): ردیف‌های پرداختِ ترکیبی — فقط فاکتوری که `payments` دارد
    ir.vil3ntec.tohid.data.PayFlow.receiptLines(sale).forEach { (label, amount) -> row(label, afn(amount), body) }
    if (debtor != null) row(Str.debtor, debtor.name, body)
    sale.note?.takeIf { it.isNotBlank() }?.let { line("${StrSale.invoiceNote}: $it", small) }

    rule(thick = true)
    if (qr != null) {
      gap(0.4f)
      y += drawQr(canvas, qr, width, y)
      line(StrPos.showQrToReturn, small, Layout.Alignment.ALIGN_CENTER)
    }
    gap(0.4f)
    line(StrSale.thanks, small, Layout.Alignment.ALIGN_CENTER)
    y += pad * 1.5f

    return y
  }

  /**
   *  QRِ رسید، وسطِ کاغذ — ماتریس از `core/qr/QrCode` (همان که وب و آزمونِ zxing
   *  می‌سنجند)، هر خانه یک مربعِ سیاه، با چهار خانه حاشیهٔ سفید. برمی‌گرداند:
   *  ارتفاعِ مصرف‌شده.
   */
  private fun drawQr(canvas: Canvas?, text: String, width: Int, top: Float): Float {
    val rows = runCatching { ir.vil3ntec.tohid.core.qr.QrCode.encode(text) }.getOrNull() ?: return 0f
    val n = rows.size + 8
    val cell = maxOf(2, (width * 0.42f / n).toInt())
    val side = cell * n
    val left = (width - side) / 2f
    if (canvas != null) {
      val ink = Paint().apply { color = Color.BLACK }
      rows.forEachIndexed { y, row ->
        row.forEachIndexed { x, on ->
          if (on) {
            val l = left + (x + 4) * cell
            val t = top + (y + 4) * cell
            canvas.drawRect(l, t, l + cell, t + cell, ink)
          }
        }
      }
    }
    return side.toFloat()
  }

  /* ---------------------------- رسیدِ برگشت (RET-12) ---------------------------- */

  /**
   *  رسیدِ برگشت: سندِ `ReturnFlow` (ردیف‌ها، مقصدِ هر کالا، تقسیمِ بازپرداخت،
   *  دلیل و تأیید)؛ در تعویض، بخشِ دومِ همان رسید فاکتورِ تازه و «دریافت/پرداختِ
   *  تفاوت» است (S15: یک رسید با دو بخش).
   */
  fun renderReturn(context: Context, d: ShopData, docId: String, storeName: String, width: Int): Bitmap {
    //  رسیدِ برگشتِ نسخهٔ ۲ (چیدمانِ `core/receipt/ReturnReceiptLayout`، آزمون‌شده در `ReturnReceiptLayoutTest`)
    //  فقط با پرچمِ `shop.receipt_v2`؛ خاموش ⇒ همین چیدمانِ پیشین، بیت‌به‌بیت
    if (ReceiptFlag.enabled(context)) {
      return Receipts.returnBitmap(context, d, docId, width, fallbackName = storeName) ?: error(StrPos.returnDocNotFound)
    }
    val doc = ir.vil3ntec.tohid.data.ReturnFlow.doc(d, docId) ?: error(StrPos.returnDocNotFound)
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
    //  زبانِ فاکتور همان زبانِ برنامه است؛ انگلیسی همیشه رقمِ لاتین و میلادی (مگر خودِ دکان‌دار تقویم را عوض کرده باشد)
    val digits = LocaleChoice.screenDigits
    val symbol = LocaleChoice.symbol
    fun num(value: Double) = LocaleText.amount(value, digits)
    fun afn(value: Double) = I18n.money(value, I18n.lang, digits, symbol)
    fun line(text: String, paint: TextPaint, align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL) {
      val l = layout(text, paint, inner.toInt(), align)
      if (canvas != null) { canvas.save(); canvas.translate(pad, y); l.draw(canvas); canvas.restore() }
      y += l.height
    }
    fun row(label: String, value: String, paint: TextPaint) {
      val half = (inner / 2).toInt()
      val right = layout(label, paint, half, Layout.Alignment.ALIGN_NORMAL)
      val left = layout(value, paint, half, Layout.Alignment.ALIGN_OPPOSITE)
      //  راست‌به‌چپ: برچسب راست، مبلغ چپ؛ انگلیسی برعکس
      val rtl = I18n.lang.rtl
      if (canvas != null) {
        canvas.save(); canvas.translate(if (rtl) pad + inner - half else pad, y); right.draw(canvas); canvas.restore()
        canvas.save(); canvas.translate(if (rtl) pad else pad + inner - half, y); left.draw(canvas); canvas.restore()
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

    line(storeName.ifBlank { StrSale.storeFallback }, p.title, Layout.Alignment.ALIGN_CENTER)
    gap(0.3f)
    line(LocaleText.digitsIn(StrPos.returnReceipt(doc.label), digits), p.head, Layout.Alignment.ALIGN_CENTER)
    gap(0.2f)
    line(
      LocaleText.digitsIn(doc.sale?.let { StrPos.fromInvoice(it.invoiceLabel() ?: "—") } ?: StrPos.returnNoReceipt, digits),
      p.small, Layout.Alignment.ALIGN_CENTER,
    )
    val time = runCatching { SimpleDateFormat("HH:mm", Locale.US).format(Date(if (doc.createdAt > 0) doc.createdAt else System.currentTimeMillis())) }.getOrDefault("")
    line("${LocaleText.digitsIn(ir.vil3ntec.tohid.displayDate(doc.date), digits)} — ${LocaleText.digitsIn(time, digits)}", p.small, Layout.Alignment.ALIGN_CENTER)
    LocaleChoice.lunarOn(doc.date)?.let { line(StrSale.hijriDate(it), p.small, Layout.Alignment.ALIGN_CENTER) }
    rule(thick = true)

    doc.rows.forEach { r ->
      line(ir.vil3ntec.tohid.data.ReturnFlow.rowName(d, r), p.body)
      row("${num(r.quantity)} · ${ir.vil3ntec.tohid.data.ReturnFlow.dispositionName(r.disposition)}", num(r.amount), p.small)
      gap(0.25f)
    }
    rule()
    row(StrPos.returnAmount, afn(doc.total), p.head)
    if (doc.split.account > 0) row(StrPos.offDebt, afn(doc.split.account), p.body)
    if (doc.split.credit > 0) row(StrPos.storeCredit, afn(doc.split.credit), p.body)
    doc.split.methods.forEach { (m, a) -> row(StrPos.refundTo(ir.vil3ntec.tohid.data.ReturnFlow.methodName(m)), afn(a), p.body) }
    if (doc.split.cash > 0) row(StrPos.cashRefunded, afn(doc.split.cash), p.body)

    doc.exchange?.let { ex ->
      rule(thick = true)
      line(LocaleText.digitsIn(StrPos.newPurchaseInvoice(ex.invoiceLabel() ?: "—"), digits), p.head)
      d.saleItems.filter { it.saleId == ex.id }.forEach { si ->
        line(ir.vil3ntec.tohid.data.SalesEngine.itemName(d, si), p.body)
        row("${num(si.quantity)} × ${num(si.unitPrice)}", num(si.totalPrice), p.small)
      }
      rule()
      row(StrPos.newPurchaseTotal, afn(ex.finalTotal), p.body)
      val due = doc.due ?: 0.0
      row(if (due >= 0) StrPos.collectDifference else StrPos.payDifference, afn(kotlin.math.abs(due)), p.head)
    }

    rule(thick = true)
    line(StrPos.reasonLine(doc.reason), p.small)
    if (doc.approvedBy.isNotBlank()) line(StrPos.approvedBy(doc.approvedBy), p.small)
    if (doc.by.isNotBlank()) line(StrPos.recordedBy(doc.by), p.small)
    gap(0.4f)
    line(StrPos.returnSeparate, p.small, Layout.Alignment.ALIGN_CENTER)
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
        .setTextDirection(if (I18n.lang.rtl) android.text.TextDirectionHeuristics.RTL else android.text.TextDirectionHeuristics.LTR)
        .setIncludePad(false)
        .build()
    } else {
      StaticLayout(text, paint, w, align, 1f, 0f, false)
    }
  }
}
