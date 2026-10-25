package ir.vil3ntec.tohid.ui.v3.sales.drawer

import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.core.pay.Pay
import ir.vil3ntec.tohid.core.pay.ShiftReport
import ir.vil3ntec.tohid.data.CashShift
import ir.vil3ntec.tohid.data.PayFlow
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.ui.components.pos.PhoneSaleText
import ir.vil3ntec.tohid.ui.v3.sales.history.DrawerLine
import ir.vil3ntec.tohid.ui.v3.sales.history.SaleTime
import ir.vil3ntec.tohid.ui.v3.sales.history.TotalRow
import kotlin.math.abs

/** اختلافِ شمارش با «مورد انتظار» — متن و رنگ با هم (رنگ هرگز تنها نیست) */
enum class DiffKind { NONE, OVER, SHORT }

data class DrawerDiff(val kind: DiffKind, val amount: Double)

/** یک ردیفِ گزارشِ X/Z */
data class ReportLine(val label: String, val value: String, val strong: Boolean = false)

/**
 *  صندوقِ نقد (`sales/drawer`) — خالص، همان عددها و قاعده‌های کارتِ صندوقِ تاریخچهٔ پیشین:
 *  `CashShift.totalsOf` برای صندوقِ ساده و `PayFlow.report` (هستهٔ pay-core) با پرچمِ `payV2`؛
 *  «بی اختلاف» زیرِ نیم افغانی؛ شمارشِ کور برای شاگرد (مبلغِ مورد انتظار دیده نمی‌شود).
 */
object DrawerModel {

  /** خطِ صندوق در تاریخچه و سربرگِ همین صفحه */
  fun line(d: ShopData, now: Long, blind: Boolean): DrawerLine {
    val shift = CashShift.current(d) ?: return DrawerLine(false, StrSale.drawerClosed)
    val since = StrSale.drawerOpenSince(SaleTime.clock(shift.open.createdAt))
    if (blind) return DrawerLine(true, since)
    val expected = CashShift.totalsOf(d, shift, now).expected
    return DrawerLine(true, "$since · ${StrSale.expectedCash(PhoneSaleText.amount(expected))}")
  }

  /** ریزِ صندوقِ باز — همان ترتیبِ پنجرهٔ «بستنِ صندوق» ِ پیشین؛ منفی‌ها با علامت */
  fun breakdown(t: CashShift.Totals): List<TotalRow> = buildList {
    add(TotalRow(StrSale.openingCash, t.opening))
    add(TotalRow(StrSale.cashSales, t.sales))
    add(TotalRow(StrSale.debtorReceipts, t.receipts))
    add(TotalRow(StrSale.returnsWord, -t.returns))
    add(TotalRow(StrSale.expensesWord, -t.expenses))
    add(TotalRow(StrSale.suppliersPaid, -t.suppliers))
    //  PAY: ورود/خروجِ نقدِ صندوقِ pay v2 — داده است، با پرچمِ خاموش هم شمرده می‌شود
    if (t.cashIn > 0) add(TotalRow(StrSale.cashInWord, t.cashIn))
    if (t.cashOut > 0) add(TotalRow(StrSale.cashOutWord, -t.cashOut))
    add(TotalRow(StrSale.expectedWord, t.expected, strong = true))
  }

  fun diff(counted: Double?, expected: Double): DrawerDiff? {
    counted ?: return null
    val d = counted - expected
    return when {
      abs(d) < 0.5 -> DrawerDiff(DiffKind.NONE, 0.0)
      d > 0 -> DrawerDiff(DiffKind.OVER, d)
      else -> DrawerDiff(DiffKind.SHORT, -d)
    }
  }

  fun diffText(x: DrawerDiff): String = when (x.kind) {
    DiffKind.NONE -> StrSale.noDiff
    DiffKind.OVER -> StrSale.overBy(PhoneSaleText.amount(x.amount))
    DiffKind.SHORT -> StrSale.shortBy(PhoneSaleText.amount(x.amount))
  }

  /** نوشته ⇒ عدد؛ همان `toDoubleOrNull` ِ پیشین پس از رقمِ لاتین */
  fun amount(text: String): Double? = ir.vil3ntec.tohid.latinDigits(text).toDoubleOrNull()

  fun moveLabel(reason: String?): String = when (reason) {
    "deposit" -> StrSale.moveDeposit
    "owner_withdrawal" -> StrSale.moveOwner
    "petty_expense" -> StrSale.movePetty
    "other" -> StrSale.moveOther
    else -> reason.orEmpty()
  }

  /** «اسکناس‌ها»: بزرگ به کوچک (همان `Pay.denominations(false).reversed()`) */
  fun notes(): List<Long> = Pay.denominations(false).reversed()

  fun counts(texts: Map<String, String>): Map<String, Long> =
    texts.mapNotNull { (k, v) -> v.trim().takeIf { it.isNotEmpty() }?.let { k to (it.toLongOrNull() ?: -1L) } }.toMap()

  fun shown(m: Long?): String = PhoneSaleText.amount(PayFlow.major(m ?: 0))

  /** ردیف‌های گزارشِ X/Z — همان ترتیبِ `ShiftReportLines` ِ پیشین */
  fun report(rep: ShiftReport): List<ReportLine> = buildList {
    add(ReportLine(StrSale.openedAt, SaleTime.stamp(rep.openedAt)))
    add(ReportLine(if (rep.kind == "Z") StrSale.closedAt else StrSale.reportTime, SaleTime.stamp(rep.at)))
    rep.by?.let { add(ReportLine(StrSale.userWord, it)) }
    add(ReportLine(StrSale.openingCash, shown(rep.float)))
    rep.methods.forEach { (id, m) ->
      val name = PayFlow.methodName(id)
      if (m.sales != 0L) add(ReportLine(StrSale.methodSales(name), shown(m.sales)))
      if (m.receipts != 0L) add(ReportLine(StrSale.methodReceipts(name), shown(m.receipts)))
      if (m.refunds != 0L) add(ReportLine(StrSale.methodRefunds(name), "−" + shown(m.refunds)))
    }
    add(ReportLine(StrSale.cashInWord, shown(rep.moves.cashIn)))
    add(ReportLine(StrSale.cashOutWord, "−" + shown(rep.moves.cashOut)))
    add(ReportLine(StrSale.noSaleOpens, StrSale.timesCount(rep.events.noSale.toString())))
    rep.expected?.forEach { (id, v) -> add(ReportLine(StrSale.expectedOf(PayFlow.methodName(id)), shown(v), id == PayFlow.CASH)) }
    if (rep.kind == "Z") {
      rep.denominations?.entries?.sortedByDescending { it.key.toLongOrNull() ?: 0 }?.forEach { (k, n) ->
        add(ReportLine("${money(PayFlow.major(k.toLongOrNull() ?: 0))} × $n", shown((k.toLongOrNull() ?: 0) * n)))
      }
      add(ReportLine(StrSale.countedCash, shown(rep.counted?.get(PayFlow.CASH)), true))
      rep.variance?.forEach { (id, v) ->
        add(ReportLine(StrSale.varianceOf(PayFlow.methodName(id)), when {
          v == null -> StrSale.notCounted
          v == 0L -> StrSale.noDiff
          v > 0 -> StrSale.overBy(shown(v))
          else -> StrSale.shortBy(shown(-v))
        }))
      }
      rep.reason?.let { add(ReportLine(StrSale.diffReason, it)) }
    }
  }
}

/**
 *  یادداشتِ ردیف‌های صندوق در سابقهٔ دکان (`AuditEntry.notes`) — **داده**، نه متنِ رابط: همان
 *  جمله‌های فارسیِ پیشین کلمه‌به‌کلمه، تا سابقهٔ دکان در هر زبانِ برنامه یکی بماند.
 */
object DrawerNotes {
  private val MOVE = mapOf("deposit" to "واریز به دخل", "owner_withdrawal" to "برداشتِ مالک", "petty_expense" to "پرداختِ کوچک", "other" to "دیگر")

  fun noSale(note: String, approvedBy: String?): String = "کشو بی فروش — ${note.trim()}" + (approvedBy?.let { " — تأیید: $it" } ?: "")

  fun move(type: String, reason: String?, amount: Double, note: String?, me: String): String =
    "${if (type == "cash_in") "ورود به دخل" else "خروج از دخل"}: ${MOVE[reason] ?: reason} — ${money(amount)} افغانی" +
      (note?.let { " — $it" } ?: "") + (if (me.isNotBlank()) " — $me" else "")

  fun close(expectedMinor: Long, countedMajor: Double, varianceMinor: Long, reason: String, me: String): String {
    val extra = listOf(reason.trim(), me.trim()).filter { it.isNotEmpty() }.joinToString(" — ")
    return "بستنِ صندوق: مورد انتظار ${money(PayFlow.major(expectedMinor))}، شمرده ${money(countedMajor)}، " +
      (if (varianceMinor == 0L) "بی اختلاف" else if (varianceMinor > 0) "اضافه ${money(PayFlow.major(varianceMinor))}" else "کسری ${money(PayFlow.major(-varianceMinor))}") +
      (if (extra.isNotEmpty()) " — $extra" else "")
  }

  /** همان «صاحب/مدیر» ِ پیشین وقتی نامِ حساب خالی است */
  const val OWNER_OR_MANAGER = "صاحب/مدیر"
}
