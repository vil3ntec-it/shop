package ir.vil3ntec.tohid.core.receipt

import ir.vil3ntec.tohid.core.qr.QrCode

/**
 *  چیدمانِ فاکتورِ نسخهٔ ۲ (RECEIPT-V2 §۱.۳) — از بالا به پایین:
 *  لوگو · نامِ دکان · نشانی و تلفن · ‒ · «لغو شد» · شماره، تاریخ، فروشنده، نوع · ‒ ·
 *  قلم‌ها · ‒ · تعداد · جمع، تخفیف‌ها، مبلغِ نهایی · پرداخت‌ها، دریافتی، باقی‌پول، نسیه ·
 *  ‒ · QR · متنِ پایین.
 *
 *  فقط چیدمان است: نه مبلغی حساب می‌شود (همه از [ReceiptInput])، نه قلم و نه سکو.
 */
object ReceiptLayout {

  fun build(
    input: ReceiptInput,
    widthDots: Int,
    fmt: ReceiptFormat,
    labels: ReceiptLabels = ReceiptLabels.FA,
  ): ReceiptDoc {
    val m = ReceiptMetrics.of(widthDots)
    val b = ArrayList<ReceiptBlock>()
    fun text(t: String, style: TextStyle = TextStyle.BODY, center: Boolean = true) {
      if (t.isNotBlank()) b += ReceiptBlock.Text(t.trim(), style, center)
    }
    fun row(label: String, value: String, style: TextStyle = TextStyle.BODY) { b += ReceiptBlock.Row(label, value, style) }

    /* ------------------------------ سربرگ ------------------------------ */
    input.logo?.let { b += ReceiptBlock.Logo(it); b += ReceiptBlock.Gap(m.gap) }
    text(input.shop.name.ifBlank { labels.fallbackShop }, TextStyle.TITLE)
    text(input.shop.address, TextStyle.SMALL)
    if (input.shop.phone.isNotBlank()) text("${labels.phone}: ${fmt.digits(input.shop.phone)}", TextStyle.SMALL)
    input.copyMark?.let { text(it, TextStyle.SMALL) }
    b += ReceiptBlock.Rule()

    if (input.cancelled) text(labels.cancelled, TextStyle.TOTAL)
    row(labels.invoiceNo, "#" + fmt.digits(input.invoiceNo.ifBlank { "—" }))
    val date = fmt.date(input.dateIso)
    row(labels.date, if (input.time.isBlank()) date else "$date — ${fmt.digits(input.time)}")
    if (input.cashier.isNotBlank()) row(labels.cashier, input.cashier.trim())
    row(labels.kind, input.kind)
    b += ReceiptBlock.Rule()

    /* ------------------------------ اقلام ------------------------------ */
    input.items.forEach { line ->
      val unit = if (line.unit.isNotBlank()) " ${line.unit}" else ""
      val list = line.listPrice
      val notes = buildList {
        if (list != null && list > line.unitPrice) add("${labels.listPrice}: ${fmt.amount(list)}")
        if (line.returnedQty > 0) add("${labels.returned}: ${fmt.qty(line.returnedQty)}")
      }
      b += ReceiptBlock.Item(line.name, "${fmt.qty(line.qty)}$unit × ${fmt.amount(line.unitPrice)}", fmt.amount(line.total), notes)
    }
    b += ReceiptBlock.Rule()
    val whole = input.items.all { it.qty == kotlin.math.floor(it.qty) }
    val count = "${labels.count}: ${fmt.digits(input.items.size.toString())} ${labels.rows}" +
      if (whole && input.items.isNotEmpty()) " · ${fmt.qty(input.items.sumOf { it.qty })} ${labels.pieces}" else ""
    text(count, TextStyle.SMALL, center = false)

    /* ------------------------------ جمع‌ها ------------------------------ */
    row(labels.subtotal, fmt.money(input.subtotal))
    input.discounts.filter { it.amount > 0 }.forEach { row(it.label, "−" + fmt.money(it.amount)) }
    b += ReceiptBlock.Rule(dashed = false)
    row(labels.total, fmt.money(input.total), TextStyle.TOTAL)

    if (input.payments.isNotEmpty()) input.payments.forEach { row(it.label, fmt.money(it.amount)) }
    else row(labels.paid, fmt.money(input.paid))
    input.tendered?.let { row(labels.tendered, fmt.money(it)) }
    input.change?.let { row(labels.change, fmt.money(it)) }
    if (input.remaining > 0) {
      row(labels.remaining, fmt.money(input.remaining))
      input.debtorName?.takeIf { it.isNotBlank() }?.let { row(labels.debtor, it.trim()) }
    }

    /* ------------------------------ QR و پایان ------------------------------ */
    b += ReceiptBlock.Rule()
    input.qrUrl?.let { url ->
      text(labels.scan, TextStyle.SMALL)
      b += ReceiptBlock.Gap(m.gap / 2)
      b += ReceiptBlock.Qr(url, QrCode.encode(url))
      b += ReceiptBlock.Gap(m.gap / 2)
    }
    text(input.shop.footer.ifBlank { labels.footer }, TextStyle.SMALL)
    return ReceiptDoc(widthDots, m, b)
  }
}
