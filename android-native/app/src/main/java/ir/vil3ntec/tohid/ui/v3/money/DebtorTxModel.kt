package ir.vil3ntec.tohid.ui.v3.money

import ir.vil3ntec.tohid.Jalali
import ir.vil3ntec.tohid.core.i18n.StrMoney
import ir.vil3ntec.tohid.data.DebtBook
import ir.vil3ntec.tohid.data.DebtTransaction
import ir.vil3ntec.tohid.displayDate
import ir.vil3ntec.tohid.qty
import ir.vil3ntec.tohid.ui.v3.InvoicePiece
import ir.vil3ntec.tohid.ui.v3.InvoiceRefs
import ir.vil3ntec.tohid.ui.v3.V3InvoiceLine

/**
 *  برگهٔ جزئیاتِ یک تراکنش (`debtors/{id}/tx/{txId}`) — منطقِ خالص، زیرِ آزمون. ردیفِ فهرست فقط
 *  تاریخ، مبلغ و مانده دارد؛ بقیه این‌جاست («روش که بزنی بیاد که این چی‌ها رو برده و فاکتورِ خریدش
 *  این است»). همه از همان `DebtBook.Entry` — هیچ حسابِ تازه‌ای نیست.
 */
internal object DebtorTxModel {

  /** رنگِ قرصِ یک خط: تسویه سبز، باز در انتظار، سررسید آبی یا (گذشته) سرخ */
  enum class Pill { SETTLED, WAIT, INFO, OWE }

  /** [pieces]: همان [value] تکه‌تکه وقتی «فاکتور #n» دارد (زدنی، `InvoiceRef`)؛ null ⇒ نوشتهٔ ساده */
  data class Line(val label: String, val value: String, val pill: Pill? = null, val pieces: List<InvoicePiece>? = null)

  /** فاکتورِ شناختهٔ یک ردیف (بردِ فاکتوردار): شماره ⇒ شناسه — ارجاعِ یادداشت با شناسه باز می‌شود، نه با گشتنِ شماره */
  private fun known(entry: DebtBook.Entry?): Map<String, String> =
    entry?.let { e -> e.invoiceLabel?.let { l -> e.sale?.id?.let { mapOf(l to it) } } }.orEmpty()

  /** یادداشتِ داده تکه‌تکه، فقط اگر «فاکتور #n» دارد */
  private fun refPieces(text: String, known: Map<String, String>): List<InvoicePiece>? =
    InvoiceRefs.split(text, known).takeIf { p -> p.any { it.isRef } }

  /** خط‌های کارتِ جزئیات به ترتیبِ نمایش */
  fun lines(tx: DebtTransaction, entry: DebtBook.Entry?, book: DebtBook.Book?): List<Line> = buildList {
    add(Line(StrMoney.date, MoneyText.date(tx.date)))
    if (tx.notes.isNotBlank()) add(Line(StrMoney.notes, tx.notes, pieces = refPieces(tx.notes, known(entry))))
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
      //  «فاکتور #۱۲ (۲۰۰)، برد ۲۸ میزان (۵۰)» — هر «فاکتور #n» زدنی، با شناسهٔ فاکتورِ همان برد
      val pieces = buildList {
        entry.allocations.forEachIndexed { i, a ->
          if (i > 0) add(InvoiceRefs.plain("، "))
          val g = book.entry(a.txId)
          val label = g?.invoiceLabel
          if (label != null) add(InvoiceRefs.ref(label, g?.sale?.id))
          else add(InvoiceRefs.plain("${StrMoney.giveTag} ${g?.tx?.date?.let(::displayDate).orEmpty()}"))
          add(InvoiceRefs.plain(" (${DebtBook.show(a.amount)})"))
        }
      }
      add(Line(StrMoney.forWhat, InvoiceRefs.text(pieces), pieces = pieces.takeIf { p -> p.any { it.isRef } }))
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
  fun itemsTitle(entry: DebtBook.Entry): String = InvoiceRefs.text(itemsTitlePieces(entry))

  /** همان سرِ اقلام، تکه‌تکه: «فاکتور #۱۲» زدنی و با شناسهٔ همان فاکتور */
  fun itemsTitlePieces(entry: DebtBook.Entry): List<InvoicePiece> = buildList {
    add(InvoiceRefs.plain(StrMoney.items))
    entry.invoiceLabel?.let { add(InvoiceRefs.plain(" · ")); add(InvoiceRefs.ref(it, entry.sale?.id)) }
    entry.sale?.date?.let { add(InvoiceRefs.plain(" · " + displayDate(it))) }
  }

  /** زیرِ عنوانِ گامِ «سررسید»: «۱۴۰۵/۰۸/۲۰ — ۴۰۰ ؋ — فاکتور #۱۲» */
  fun dueSubtitle(entry: DebtBook.Entry): List<InvoicePiece> = buildList {
    add(InvoiceRefs.plain(MoneyText.date(entry.tx.date) + " — " + "${DebtBook.show(entry.amount)} ؋"))
    entry.invoiceLabel?.let { add(InvoiceRefs.plain(" — ")); add(InvoiceRefs.ref(it, entry.sale?.id)) }
  }

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

  /** برگهٔ «ثبتِ تراکنش» فقط «یک هفته · دو هفته» (+ «بی سررسید») — «یک ماه» را صاحب‌کار ۱۴۰۵/۰۸/۲۳ خط زد */
  val TX_DUE_PRESETS = listOf(7, 14)

  /**
   *  نوشتهٔ کادرِ سررسید (خورشیدی، «۱۴۰۵/۰۸/۱۰») ⇒ `yyyy-MM-dd`؛ خالی ⇒ "" (سررسید ندارد)؛
   *  نوشتهٔ ناخوانا ⇒ null (خطای درجا، ذخیره نه).
   */
  fun parseDue(text: String): String? = if (text.isBlank()) "" else Jalali.parseTyped(text)

  /** نوشتهٔ ناخوانای کادرِ سررسید ⇒ «ثبت» بسته (لرزش)، نه ذخیرهٔ بی‌صدای تاریخِ قبلی */
  fun dueBroken(text: String): Boolean = parseDue(text) == null
}
