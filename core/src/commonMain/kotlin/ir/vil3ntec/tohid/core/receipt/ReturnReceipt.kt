package ir.vil3ntec.tohid.core.receipt

import ir.vil3ntec.tohid.core.hw.MonoImage
import kotlin.math.abs

/**
 *  رسیدِ چاپیِ برگشت (RET-12؛ سناریوهای S13 و S15) — همان بلوک‌های [ReceiptDoc] ِ فاکتورِ نسخهٔ ۲،
 *  پس کاغذِ حرارتی، PNG و PDF همه از [ReceiptRaster] می‌گذرند و سکو فقط نوشته را می‌کشد.
 *
 *  ⛔ هیچ مبلغی این‌جا حساب نمی‌شود: هر عدد همان سندِ ثبت‌شدهٔ برگشت است (`ReturnFlow.doc` در
 *  برنامه)؛ تنها کارِ هسته انتخابِ «دریافت/پرداختِ تفاوت» از علامتِ [ExchangeView.due] است.
 */
data class ReturnReceiptInput(
  val shop: ShopHeader,
  /** `R-1A2B3C` (`ReturnFlow.label`) */
  val label: String,
  /** شمارهٔ فاکتورِ اصل؛ `null` ⇒ برگشتِ بی رسید */
  val invoiceNo: String?,
  /** `YYYY-MM-DD` ِ میلادیِ دفتر */
  val dateIso: String,
  /** `14:35`؛ خالی ⇒ فقط تاریخ */
  val time: String,
  val lines: List<ReturnLineView>,
  /** جمعِ برگشت (جمعِ ردیف‌های سند) */
  val total: Double,
  /** سهمِ هر مقصدِ بازپرداخت، به ترتیبِ چاپ (کم از بدهی، اعتبار، روش‌های غیرنقد، نقد)؛ صفرها نمی‌آیند */
  val refunds: List<PaymentLine>,
  val reason: String,
  val approvedBy: String = "",
  val by: String = "",
  /** فقط در تعویض (S15): بخشِ دومِ همان رسید */
  val exchange: ExchangeView? = null,
  val logo: MonoImage? = null,
  val copyMark: String? = null,
)

/** یک ردیفِ برگشت؛ [damaged] ⇒ مقصدِ «ضایعات»، وگرنه «به موجودی» */
data class ReturnLineView(
  val name: String,
  val qty: Double,
  val unit: String,
  val amount: Double,
  val damaged: Boolean,
)

/** فروشِ تازهٔ تعویض */
data class ExchangeView(
  val invoiceNo: String,
  val items: List<LineView>,
  val total: Double,
  /** مثبت یا صفر: از مشتری گرفته شد؛ منفی: به مشتری پس داده شد (همان `ReturnFlow.Doc.due`) */
  val due: Double,
)

/**
 *  برچسب‌ها — پیش‌فرضِ دری؛ برنامه نسخهٔ زبانِ خودش را از `StrPos` می‌دهد. `{0}` جای مقدار است
 *  تا ترتیبِ واژه در هر زبان درست بماند (پشتو: «… ته بېرته»).
 */
data class ReturnReceiptLabels(
  val fallbackShop: String = "فروشگاه",
  val phone: String = "تلفن",
  val receipt: String = "رسید برگشت {0}",
  val fromInvoice: String = "از فاکتور #{0}",
  val noReceipt: String = "برگشتِ بی رسید",
  val restock: String = "به موجودی",
  val damaged: String = "ضایعات",
  val amount: String = "مبلغ برگشت",
  val newPurchase: String = "خرید تازه — فاکتور #{0}",
  val newPurchaseTotal: String = "جمع خرید تازه",
  val collect: String = "دریافت تفاوت",
  val pay: String = "پرداخت تفاوت",
  val reason: String = "دلیل: {0}",
  val approvedBy: String = "تأیید: {0}",
  val recordedBy: String = "ثبت: {0}",
  val separate: String = "سند برگشت جدا ثبت شد؛ فاکتور اصلی دست نخورده است",
) {
  companion object {
    val FA = ReturnReceiptLabels()

    fun fill(template: String, value: String): String = template.replace("{0}", value)
  }
}

/**
 *  چیدمانِ رسیدِ برگشت — از بالا به پایین:
 *  لوگو · نامِ دکان · نشانی و تلفن · ‒ · «رسید برگشت R-…» · «از فاکتور #…» یا «بی رسید» · تاریخ ·
 *  ‒ · ردیف‌ها (مقدار · مقصد) · ‒ · مبلغِ برگشت و سهمِ هر مقصد · [تعویض: فاکتورِ تازه، جمعش و
 *  دریافت/پرداختِ تفاوت] · ━ · دلیل، تأیید، ثبت‌کننده · «سندِ جدا؛ فاکتورِ اصل دست نخورده».
 */
object ReturnReceiptLayout {

  fun build(
    input: ReturnReceiptInput,
    widthDots: Int,
    fmt: ReceiptFormat,
    labels: ReturnReceiptLabels = ReturnReceiptLabels.FA,
  ): ReceiptDoc {
    val m = ReceiptMetrics.of(widthDots)
    val b = ArrayList<ReceiptBlock>()
    fun text(t: String, style: TextStyle = TextStyle.BODY, center: Boolean = true) {
      if (t.isNotBlank()) b += ReceiptBlock.Text(t.trim(), style, center)
    }
    fun row(label: String, value: String, style: TextStyle = TextStyle.BODY) { b += ReceiptBlock.Row(label, value, style) }
    fun fill(template: String, value: String) = ReturnReceiptLabels.fill(template, value)

    /* ------------------------------ سربرگ ------------------------------ */
    input.logo?.let { b += ReceiptBlock.Logo(it); b += ReceiptBlock.Gap(m.gap) }
    text(input.shop.name.ifBlank { labels.fallbackShop }, TextStyle.TITLE)
    text(input.shop.address, TextStyle.SMALL)
    if (input.shop.phone.isNotBlank()) text("${labels.phone}: ${fmt.phone(input.shop.phone)}", TextStyle.SMALL)
    input.copyMark?.let { text(it, TextStyle.SMALL) }
    b += ReceiptBlock.Rule()

    text(fmt.digits(fill(labels.receipt, input.label)), TextStyle.TOTAL)
    val origin = input.invoiceNo?.let { fmt.digits(fill(labels.fromInvoice, it.ifBlank { "—" })) } ?: labels.noReceipt
    text(origin, TextStyle.SMALL)
    val date = fmt.date(input.dateIso)
    text(if (input.time.isBlank()) date else "$date — ${fmt.digits(input.time)}", TextStyle.SMALL)
    b += ReceiptBlock.Rule()

    /* ------------------------------ ردیف‌ها ------------------------------ */
    input.lines.forEach { line ->
      val unit = if (line.unit.isNotBlank()) " ${line.unit}" else ""
      val where = if (line.damaged) labels.damaged else labels.restock
      b += ReceiptBlock.Item(line.name, "${fmt.qty(line.qty)}$unit · $where", fmt.amount(line.amount))
    }
    b += ReceiptBlock.Rule()
    row(labels.amount, fmt.money(input.total), TextStyle.TOTAL)
    input.refunds.filter { it.amount > 0 }.forEach { row(it.label, fmt.money(it.amount)) }

    /* ------------------------------ تعویض (S15) ------------------------------ */
    input.exchange?.let { ex ->
      b += ReceiptBlock.Rule(dashed = false)
      text(fmt.digits(fill(labels.newPurchase, ex.invoiceNo.ifBlank { "—" })), TextStyle.BODY, center = false)
      ex.items.forEach { line ->
        val unit = if (line.unit.isNotBlank()) " ${line.unit}" else ""
        b += ReceiptBlock.Item(line.name, "${fmt.qty(line.qty)}$unit × ${fmt.amount(line.unitPrice)}", fmt.amount(line.total))
      }
      b += ReceiptBlock.Rule()
      row(labels.newPurchaseTotal, fmt.money(ex.total))
      //  همان قاعدهٔ رسیدِ پیشین: صفر هم «دریافت» است (چیزی پس داده نشد)
      row(if (ex.due >= 0) labels.collect else labels.pay, fmt.money(abs(ex.due)), TextStyle.TOTAL)
    }

    /* ------------------------------ پایان ------------------------------ */
    b += ReceiptBlock.Rule(dashed = false)
    if (input.reason.isNotBlank()) text(fill(labels.reason, input.reason.trim()), TextStyle.SMALL, center = false)
    if (input.approvedBy.isNotBlank()) text(fill(labels.approvedBy, input.approvedBy.trim()), TextStyle.SMALL, center = false)
    if (input.by.isNotBlank()) text(fill(labels.recordedBy, input.by.trim()), TextStyle.SMALL, center = false)
    b += ReceiptBlock.Gap(m.gap)
    text(labels.separate, TextStyle.SMALL)
    return ReceiptDoc(widthDots, m, b)
  }
}
