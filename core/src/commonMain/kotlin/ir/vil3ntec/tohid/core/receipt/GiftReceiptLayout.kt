package ir.vil3ntec.tohid.core.receipt

/**
 *  رسیدِ هدیه — POS-34 (§۶.۱): همان فاکتور **بی هیچ قیمت**، برای کسی که کالا را هدیه می‌گیرد و
 *  شاید بخواهد عوضش کند. از بالا به پایین: لوگو · نامِ دکان · نشانی و تلفن · ‒ · «رسیدِ هدیه» ·
 *  شماره و تاریخ · ‒ · نامِ هر قلم با مقدار · ‒ · تعداد · ‒ · یادداشتِ «بی قیمت» · متنِ پایین.
 *
 *  ⛔ هیچ مبلغی روی کاغذ نمی‌آید: نه فیِ قلم، نه جمعِ قلم، نه «قیمتِ اصلی»، نه جمع، تخفیف، پرداخت،
 *  پولِ پس، نسیه و نامِ قرض‌دار؛ نه «نوعِ فاکتور» (نقدی/نسیه)؛ و **نه QR**: QR ِ فاکتور صفحهٔ عمومیِ
 *  همان فاکتور با همهٔ قیمت‌هاست. شمارهٔ فاکتور می‌ماند تا تعویض و برگشت فاکتور را پیدا کند.
 *  قلمِ برگشت‌خورده با مقدارِ ماندهٔ همان قلم می‌آید و قلمِ کاملاً برگشته نمی‌آید.
 *
 *  فقط چیدمان است؛ ورودی همان [ReceiptInput] ِ فاکتور (`Receipts.input`). صدازننده فاکتورِ لغوشده را
 *  رسیدِ هدیه نمی‌دهد. Kotlin خالص — آزمون: `GiftReceiptLayoutTest`.
 */
object GiftReceiptLayout {

  data class Labels(
    val title: String = "رسیدِ هدیه",
    val note: String = "این رسید برای تعویض است و قیمت ندارد",
    val base: ReceiptLabels = ReceiptLabels.FA,
  ) {
    companion object {
      val FA = Labels()
    }
  }

  fun build(input: ReceiptInput, widthDots: Int, fmt: ReceiptFormat, labels: Labels = Labels.FA): ReceiptDoc {
    val l = labels.base
    val m = ReceiptMetrics.of(widthDots)
    val b = ArrayList<ReceiptBlock>()
    fun text(t: String, style: TextStyle = TextStyle.BODY, center: Boolean = true) {
      if (t.isNotBlank()) b += ReceiptBlock.Text(t.trim(), style, center)
    }
    fun row(label: String, value: String) { b += ReceiptBlock.Row(label, value, TextStyle.BODY) }

    /* ------------------------------ سربرگ ------------------------------ */
    input.logo?.let { b += ReceiptBlock.Logo(it); b += ReceiptBlock.Gap(m.gap) }
    text(input.shop.name.ifBlank { l.fallbackShop }, TextStyle.TITLE)
    text(input.shop.address, TextStyle.SMALL)
    if (input.shop.phone.isNotBlank()) text("${l.phone}: ${fmt.phone(input.shop.phone)}", TextStyle.SMALL)
    input.copyMark?.let { text(it, TextStyle.SMALL) }
    b += ReceiptBlock.Rule()

    text(labels.title, TextStyle.TOTAL)
    row(l.invoiceNo, "#" + fmt.digits(input.invoiceNo.ifBlank { "—" }))
    row(l.date, fmt.date(input.dateIso))
    b += ReceiptBlock.Rule()

    /* ------------------------- اقلام، بی قیمت ------------------------- */
    val kept = input.items.mapNotNull { line ->
      val q = line.qty - line.returnedQty
      if (q > 0) line to q else null
    }
    kept.forEach { (line, q) ->
      val unit = if (line.unit.isNotBlank()) " ${line.unit}" else ""
      b += ReceiptBlock.Item(line.name, "${fmt.qty(q)}$unit", "")
    }
    b += ReceiptBlock.Rule()
    val whole = kept.all { (_, q) -> q == kotlin.math.floor(q) }
    val count = "${l.count}: ${fmt.digits(kept.size.toString())} ${l.rows}" +
      if (whole && kept.isNotEmpty()) " · ${fmt.qty(kept.sumOf { it.second })} ${l.pieces}" else ""
    text(count, TextStyle.SMALL, center = false)
    b += ReceiptBlock.Rule()

    /* ------------------------------ پایان ------------------------------ */
    text(labels.note, TextStyle.SMALL)
    text(input.shop.footer.ifBlank { l.footer }, TextStyle.SMALL)
    return ReceiptDoc(widthDots, m, b)
  }
}
