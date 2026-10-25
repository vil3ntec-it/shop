package ir.vil3ntec.tohid.ui.v3.money

import ir.vil3ntec.tohid.Jalali
import ir.vil3ntec.tohid.core.i18n.StrMoney
import ir.vil3ntec.tohid.data.DebtBook
import ir.vil3ntec.tohid.data.DebtTransaction
import ir.vil3ntec.tohid.displayDate
import ir.vil3ntec.tohid.qty
import ir.vil3ntec.tohid.ui.v3.V3InvoiceLine

/**
 *  برگهٔ جزئیاتِ یک تراکنش (`debtors/{id}/tx/{txId}`) — منطقِ خالص، زیرِ آزمون. ردیفِ فهرست فقط
 *  تاریخ، مبلغ و مانده دارد؛ بقیه این‌جاست («روش که بزنی بیاد که این چی‌ها رو برده و فاکتورِ خریدش
 *  این است»). همه از همان `DebtBook.Entry` — هیچ حسابِ تازه‌ای نیست.
 */
internal object DebtorTxModel {

  /** رنگِ قرصِ یک خط: تسویه سبز، باز در انتظار، سررسید آبی یا (گذشته) سرخ */
  enum class Pill { SETTLED, WAIT, INFO, OWE }

  data class Line(val label: String, val value: String, val pill: Pill? = null)

  /** خط‌های کارتِ جزئیات به ترتیبِ نمایش */
  fun lines(tx: DebtTransaction, entry: DebtBook.Entry?, book: DebtBook.Book?): List<Line> = buildList {
    add(Line(StrMoney.date, MoneyText.date(tx.date)))
    if (tx.notes.isNotBlank()) add(Line(StrMoney.notes, tx.notes))
    entry ?: return@buildList
    if (entry.give && entry.amount.minor > 0) {
      add(
        if (entry.paid) Line(StrMoney.status, StrMoney.paidOff, Pill.SETTLED)
        else Line(StrMoney.status, StrMoney.openAmount(DebtBook.show(entry.open)), Pill.WAIT),
      )
      val due = entry.dueDate
      add(
        when {
          due == null -> Line(StrMoney.due, StrMoney.noDue)
          entry.overdue -> Line(StrMoney.due, "${displayDate(due)} — ${StrMoney.pastDue}", Pill.OWE)
          else -> Line(StrMoney.due, displayDate(due), Pill.INFO)
        },
      )
    }
    if (!entry.give && entry.allocations.isNotEmpty() && book != null) {
      add(Line(StrMoney.forWhat, entry.allocations.joinToString("، ") { a ->
        val g = book.entry(a.txId)
        val what = g?.invoiceLabel?.let { StrMoney.invoiceNo(it) } ?: "${StrMoney.giveTag} ${g?.tx?.date?.let(::displayDate).orEmpty()}"
        "$what (${DebtBook.show(a.amount)})"
      }))
    }
    if (!entry.give && entry.open.minor > 0) add(Line(StrMoney.prepaid, DebtBook.show(entry.open)))
    add(Line(StrMoney.balanceAfter, MoneyText.state(entry.balanceAfter)))
  }

  /** بردِ فاکتوردار: «این چی‌ها رو برده» — اقلامِ همان فاکتور با قیمتِ همان لحظه */
  fun items(entry: DebtBook.Entry?): List<V3InvoiceLine> =
    if (entry == null || !entry.give) emptyList()
    else entry.lines.map { l ->
      V3InvoiceLine(
        name = l.name,
        detail = "${MoneyText.qtyUnit(l.quantity, l.unit)} × ${DebtBook.show(l.unitPrice)}",
        total = DebtBook.show(l.total),
        note = if (l.returned > 0) StrMoney.returned(qty(l.returned)) else null,
      )
    }

  /** «اقلام · فاکتور #۱۲ · ۱۴۰۵/۰۷/۲۰» */
  fun itemsTitle(entry: DebtBook.Entry): String =
    (listOf(StrMoney.items) + listOfNotNull(entry.invoiceLabel?.let { StrMoney.invoiceNo(it) }, entry.sale?.date?.let(::displayDate))).joinToString(" · ")

  /** تخفیف و پرداختِ همان لحظهٔ فاکتور، اگر بود */
  fun itemsFooter(entry: DebtBook.Entry): String? {
    val s = entry.sale ?: return null
    return listOfNotNull(
      s.discount.takeIf { it > 0 }?.let { StrMoney.discount(DebtBook.show(DebtBook.minor(it))) },
      s.paidAmount.takeIf { it > 0 }?.let { StrMoney.paidAtOnce(DebtBook.show(DebtBook.minor(it))) },
    ).joinToString(" · ").ifBlank { null }
  }

  /* ------------------------------ سررسید ------------------------------ */

  /** سه میان‌بُرِ سررسید (روز از امروز) — همان «یک هفته، دو هفته، یک ماه» ِ امروز */
  val DUE_PRESETS = listOf(7, 14, 30)

  /**
   *  نوشتهٔ کادرِ سررسید (خورشیدی، «۱۴۰۵/۰۸/۱۰») ⇒ `yyyy-MM-dd`؛ خالی ⇒ "" (سررسید ندارد)؛
   *  نوشتهٔ ناخوانا ⇒ null (خطای درجا، ذخیره نه).
   */
  fun parseDue(text: String): String? = if (text.isBlank()) "" else Jalali.parseTyped(text)
}
