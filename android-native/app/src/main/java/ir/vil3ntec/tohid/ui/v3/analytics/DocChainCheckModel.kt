package ir.vil3ntec.tohid.ui.v3.analytics

import ir.vil3ntec.tohid.core.audit.DocChainAudit
import ir.vil3ntec.tohid.core.audit.DocChainDocs
import ir.vil3ntec.tohid.core.i18n.StrDocChain

/**
 *  SEC-02 — گزارشِ `DocChainAudit` ⇐ نوشته‌های صفحهٔ «بررسیِ یکپارچگیِ اسناد» (خالص؛ آزمون: `DocChainCheckModelTest`).
 *  سالم ⇐ پیامِ سبز با شمارِ اسناد و تأییدِ سرور؛ ناسازگار ⇐ پیامِ قرمز و فهرستِ «عوض شده» و «در دفتر نیست». «در انتظار» و
 *  «بی لنگر» خرابی نیستند، جدا گفته می‌شوند.
 */
object DocChainCheckModel {

  data class Row(val label: String, val ref: String)

  data class View(
    val ok: Boolean,
    val title: String,
    val body: String,
    /** شمارِ هر نوع و نشان‌ها («تا کجا»، آخرین فاکتور) */
    val facts: List<String>,
    /** خطِ هشدارِ غیرخرابی (در انتظار، بی لنگر، ایستاده) */
    val notes: List<String>,
    val changed: List<Row>,
    val missing: List<Row>,
  )

  private val ORDER = listOf("sale", "void", DocChainDocs.RETURN, DocChainDocs.DEBT, DocChainDocs.PURCHASE, DocChainDocs.SUPPLIER_PAY)

  fun label(type: String): String = when (type) {
    "sale" -> StrDocChain.typeSale
    "void" -> StrDocChain.typeVoid
    DocChainDocs.RETURN -> StrDocChain.typeReturn
    DocChainDocs.DEBT -> StrDocChain.typeDebt
    DocChainDocs.PURCHASE -> StrDocChain.typePurchase
    DocChainDocs.SUPPLIER_PAY -> StrDocChain.typeSupplierPay
    else -> type
  }

  /** [time] زمانِ ms ⇐ نوشتهٔ تاریخ و ساعت */
  fun view(r: DocChainAudit.Report, time: (Long) -> String): View {
    val broken = !r.links.ok
    val title = if (r.ok) StrDocChain.okTitle else StrDocChain.badTitle
    val body = if (broken) StrDocChain.brokenAt((if (r.links.index >= 0) r.links.index else r.links.length) + 1)
    else StrDocChain.okBody(r.total, r.acked)
    val types = r.byType.keys.sortedBy { ORDER.indexOf(it).let { i -> if (i < 0) ORDER.size else i } }
    val facts = types.map { StrDocChain.count(label(it), r.byType.getValue(it)) } +
      listOfNotNull(
        r.lastNo.takeIf { it > 0 && (r.byType["sale"] ?: 0) > 0 }?.let { StrDocChain.lastInvoice(it) },
        r.docFrom.takeIf { it >= 0 }?.let { StrDocChain.since(time(it)) },
        r.docAt.takeIf { r.docFrom >= 0 && it > r.docFrom }?.let { StrDocChain.upTo(time(it)) },
      )
    val notes = listOfNotNull(
      StrDocChain.halted.takeIf { r.halted.isNotEmpty() },
      r.waiting.takeIf { it > 0 }?.let { StrDocChain.waiting(it) },
      r.unanchored.takeIf { it > 0 }?.let { StrDocChain.unanchored(it) },
    )
    return View(
      ok = r.ok, title = title, body = body, facts = facts, notes = notes,
      changed = r.changed.map { Row(label(it.type), it.ref) },
      missing = r.missing.map { Row(label(it.type), it.ref) },
    )
  }
}
