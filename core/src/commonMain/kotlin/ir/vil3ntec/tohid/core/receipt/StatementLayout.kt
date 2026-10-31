package ir.vil3ntec.tohid.core.receipt

/**
 *  صورت‌حسابِ مشتری به شکلِ تصویر — CRM-05 («چاپ، تصویر یا PDF»).
 *
 *  همان ردیف‌های «صورت‌حساب»ِ صفحهٔ حساب (`ReportCsv.statement`): هر برد و رسید به ترتیبِ زمان با
 *  ماندهٔ پس از آن. خروجی یک [ReceiptDoc] است تا همان راهِ فاکتور (`ReceiptRaster` ⇐ PNG/PDF،
 *  چاپگرِ حرارتی) آن را بکشد؛ نوشته‌ها را سکو با قلمِ برنامه می‌کشد.
 *
 *  پول همه به **پول** (واحدِ کوچک، عددِ صحیح) است و فقط در لحظهٔ نوشتن به [ReceiptFormat.money]
 *  داده می‌شود. فقط چیدمان است: مانده‌ها را صدازننده از دفتر حساب کرده؛ این‌جا فقط جمعِ ستون‌ها
 *  و ماندهٔ پایانی (همان ماندهٔ ردیفِ آخر) خوانده می‌شود. Kotlin خالص — بی android.*، بی java.*.
 */
data class StatementLine(
  /** `YYYY-MM-DD` */
  val dateIso: String,
  val note: String,
  /** برد (قرض داده شد) به پول؛ ۰ برای رسید */
  val giveMinor: Long,
  /** رسید (پول گرفته شد) به پول؛ ۰ برای برد */
  val receiveMinor: Long,
  /** ماندهٔ پس از همین ردیف به پول؛ مثبت = بدهکار */
  val balanceMinor: Long,
)

data class StatementInput(
  val shop: ShopHeader,
  val customer: String,
  val phone: String = "",
  /** روزِ صدور، `YYYY-MM-DD` */
  val todayIso: String,
  val lines: List<StatementLine>,
)

data class StatementLabels(
  val fallbackShop: String = "فروشگاه",
  val title: String = "صورت‌حسابِ مشتری",
  val customer: String = "مشتری",
  val phone: String = "تلفن",
  val date: String = "تاریخ",
  val give: String = "برد",
  val receive: String = "رسید",
  val balance: String = "مانده",
  val totalGive: String = "جمعِ برد",
  val totalReceive: String = "جمعِ رسید",
  val owes: String = "بدهکار",
  val credit: String = "طلبکار",
  val settled: String = "صاف",
  val empty: String = "هنوز ردیفی نیست",
  val rows: String = "ردیف",
  val footer: String = "این صورت‌حساب از دفترِ دکان ساخته شده است",
) {
  companion object {
    val FA = StatementLabels()
  }
}

object StatementLayout {

  /** ماندهٔ پایانی = ماندهٔ ردیفِ آخر؛ بی ردیف ⇒ ۰ */
  fun finalMinor(lines: List<StatementLine>): Long = lines.lastOrNull()?.balanceMinor ?: 0L

  /** پول ⇒ افغانی فقط برای نوشتن (۱۲۳۴۵ ⇒ 123.45) */
  private fun afn(minor: Long): Double = minor / 100.0

  private fun abs(v: Long): Long = if (v < 0) -v else v

  fun build(
    input: StatementInput,
    widthDots: Int,
    fmt: ReceiptFormat,
    labels: StatementLabels = StatementLabels.FA,
  ): ReceiptDoc {
    val m = ReceiptMetrics.of(widthDots)
    val b = ArrayList<ReceiptBlock>()
    fun text(t: String, style: TextStyle = TextStyle.BODY, center: Boolean = true) {
      if (t.isNotBlank()) b += ReceiptBlock.Text(t.trim(), style, center)
    }
    fun row(label: String, value: String, style: TextStyle = TextStyle.BODY) { b += ReceiptBlock.Row(label, value, style) }

    /* ------------------------------ سربرگ ------------------------------ */
    text(input.shop.name.ifBlank { labels.fallbackShop }, TextStyle.TITLE)
    text(input.shop.address, TextStyle.SMALL)
    if (input.shop.phone.isNotBlank()) text("${labels.phone}: ${fmt.phone(input.shop.phone)}", TextStyle.SMALL)
    b += ReceiptBlock.Rule()
    text(labels.title, TextStyle.TOTAL)
    row(labels.customer, input.customer.trim())
    if (input.phone.isNotBlank()) row(labels.phone, fmt.phone(input.phone))
    row(labels.date, fmt.date(input.todayIso))
    b += ReceiptBlock.Rule()

    /* ------------------------------ ردیف‌ها ------------------------------ */
    if (input.lines.isEmpty()) text(labels.empty, TextStyle.SMALL)
    input.lines.forEach { l ->
      val give = l.giveMinor > 0
      val kind = if (give) labels.give else labels.receive
      val amount = if (give) l.giveMinor else l.receiveMinor
      val name = listOf(fmt.date(l.dateIso), l.note.trim().ifBlank { kind }).joinToString(" — ")
      b += ReceiptBlock.Item(name, "$kind: ${fmt.money(afn(amount))}", "${labels.balance}: ${fmt.money(afn(l.balanceMinor))}")
    }
    b += ReceiptBlock.Rule()
    text("${fmt.digits(input.lines.size.toString())} ${labels.rows}", TextStyle.SMALL, center = false)

    /* ------------------------------ جمع‌ها ------------------------------ */
    row(labels.totalGive, fmt.money(afn(input.lines.sumOf { it.giveMinor })))
    row(labels.totalReceive, fmt.money(afn(input.lines.sumOf { it.receiveMinor })))
    b += ReceiptBlock.Rule(dashed = false)
    val last = finalMinor(input.lines)
    val state = when {
      last > 0 -> labels.owes
      last < 0 -> labels.credit
      else -> labels.settled
    }
    row("${labels.balance} ($state)", fmt.money(afn(abs(last))), TextStyle.TOTAL)
    b += ReceiptBlock.Rule()
    text(input.shop.footer.ifBlank { labels.footer }, TextStyle.SMALL)
    return ReceiptDoc(widthDots, m, b)
  }
}
