package ir.vil3ntec.tohid.ui.v3.sales.drawer

import ir.vil3ntec.tohid.core.gateway.HesabPayShop
import ir.vil3ntec.tohid.core.gateway.Payee
import ir.vil3ntec.tohid.core.gateway.PayeeTotal
import ir.vil3ntec.tohid.core.i18n.StrDrawers
import ir.vil3ntec.tohid.core.i18n.StrPay
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.core.pay.Drawers
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

  /**
   *  «ثبت از اسکنر» (کارتِ فرمان، `ScanCashSale`) در بازهٔ صندوق — یک خط برای صفحهٔ صندوق و گزارشِ X/Z؛ هیچ ⇒ `null`.
   *  شاگرد (شمارشِ کور) فقط شمار را می‌بیند، نه مبلغ.
   */
  fun scannerLine(d: ShopData, from: Long, to: Long, seesMoney: Boolean): String? {
    val s = ir.vil3ntec.tohid.data.ScanCashSale.summary(d, from, to)
    if (s.count == 0) return null
    val head = if (seesMoney) ir.vil3ntec.tohid.core.i18n.StrDevices.scannerSales(s.count, PhoneSaleText.amount(PayFlow.major(s.totalMinor)))
    else ir.vil3ntec.tohid.core.i18n.StrDevices.scannerSalesCount(s.count)
    return if (s.fromMemory > 0) head + " · " + ir.vil3ntec.tohid.core.i18n.StrDevices.scannerFromMemory(s.fromMemory) else head
  }

  /**
   *  X/Z: «پول رسید» ‌های حساب‌پیِ بازه به تفکیکِ حسابِ گیرنده (`SalePayment.payee`)، با نام و ۴ رقم از تاریخچهٔ حساب‌های
   *  سرور ([known] = `TillHesab.payees`). ⚠️ حسابی که مالِ این دکان نیست جدا و با نشان. شاگرد (شمارشِ کور) فقط شمار را می‌بیند.
   */
  fun hesabPayLines(d: ShopData, from: Long, to: Long, known: List<Payee>, seesMoney: Boolean): List<String> {
    val rows = d.sales.filter { it.status != "cancelled" && it.createdAt in from..to }
      .flatMap { s -> s.payments.orEmpty().filter { it.method == "hesabpay" && it.status == "confirmed" }.map { it.payee to it.amountMinor } }
    return HesabPayShop.byPayee(rows, known).map { p ->
      val label = when (p.kind) {
        PayeeTotal.ACTIVE -> "${p.name.ifBlank { "—" }} — ${p.last4}"
        PayeeTotal.RETIRED -> StrPay.xzRetired(p.name.ifBlank { "—" }, p.last4)
        PayeeTotal.UNKNOWN -> StrPay.xzUnknown
        else -> StrPay.xzNone
      }
      if (seesMoney) StrPay.xzPayee(label, PhoneSaleText.amount(PayFlow.major(p.amountMinor)), p.count) else StrPay.xzPayeeCount(label, p.count)
    }
  }

  /**
   *  PAY-10 — X/Z: ارزِ نقدِ دریافتیِ بازه به تفکیکِ ارز (`FxTillFlow.totals`) — همان اسکناسی که جدا از افغانی شمرده
   *  می‌شود؛ ارزشِ افغانی به نرخِ هر فاکتور. شاگرد (شمارشِ کور) فقط شمار را می‌بیند. بی ارز ⇒ هیچ خط.
   */
  fun fxLines(d: ShopData, from: Long, to: Long, seesMoney: Boolean): List<String> =
    ir.vil3ntec.tohid.data.FxTillFlow.totals(d, from, to).map { t ->
      if (seesMoney) ir.vil3ntec.tohid.core.i18n.StrFx.xzLine(
        t.currency, ir.vil3ntec.tohid.core.pay.FxTill.format(t.amount, t.currency), PhoneSaleText.amount(PayFlow.major(t.afnMinor)), t.count,
      ) else ir.vil3ntec.tohid.core.i18n.StrFx.xzCount(t.currency, t.count)
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

  /**
   *  PAY-13 — آنچه بیننده می‌بیند: بی حقِ دیدن (شاگرد، شمارشِ کور) نه «مورد انتظار» نه اختلاف — در Xِ
   *  میانِ شیفت هم، نه فقط در Zِ کور (`ShiftCore.cashierView` X را دست‌نخورده برمی‌گرداند). همان
   *  `ShiftSlip.forViewer` ِ #536؛ پس از رسیدنِ آن به این شاخه همان را صدا بزن.
   */
  fun forViewer(rep: ShiftReport, seesExpected: Boolean): ShiftReport =
    if (seesExpected) rep else rep.copy(expected = null, variance = null)

  fun shown(m: Long?): String = PhoneSaleText.amount(PayFlow.major(m ?: 0))

  private fun minor(v: Double): Long = if (v.isFinite()) kotlin.math.round(v * 100).toLong() else 0L

  /**
   *  PAY-17 — یک کشو برای «همهٔ کشوها با هم»: «موردِ انتظار» ِ نقد از همان راهِ صفحهٔ خودش — صندوقِ شیفت (`payV2`) از Z ِ
   *  ذخیره‌شده یا X ِ همین حالا (`PayFlow.report`)، صندوقِ ساده (یا بستنِ بی Z) از `CashShift.totalsOf`؛ شمرده همان مبلغِ
   *  ردیفِ بستن.
   */
  fun sumOf(d: ShopData, s: CashShift.Shift, now: Long, payV2: Boolean): Drawers.Sum {
    val rep = if (!payV2) null
    else s.close?.z?.let { PayFlow.reportOf(it) } ?: if (s.close == null) PayFlow.report(d, s.open, null, now).valueOrNull()?.report else null
    val expected = rep?.expected?.get(PayFlow.CASH) ?: minor(CashShift.totalsOf(d, s, now).expected)
    return Drawers.Sum(s.open.id, s.open.drawer, s.isOpen, minor(s.open.amount), expected, s.close?.let { minor(it.amount) })
  }

  /** PAY-17 — X/Z ِ همهٔ کشوهای امروز با هم: هر کشوی باز و هر کشوی بسته از [dayStart] (`core/pay/Drawers.combine`) */
  fun allDrawers(d: ShopData, now: Long, dayStart: Long, payV2: Boolean): Drawers.Combined =
    Drawers.combine(CashShift.list(d).filter { Drawers.inDay(it.close?.createdAt, dayStart) }.map { sumOf(d, it, now, payV2) })

  /** اختلافِ یک کشو یا جمع — «بی اختلاف» زیرِ نیم افغانی (همان [diff]) */
  fun varianceText(v: Long): String = when {
    abs(v) < 50 -> StrSale.noDiff
    v > 0 -> StrSale.overBy(shown(v))
    else -> StrSale.shortBy(shown(-v))
  }

  /** ردیف‌های «همهٔ کشوها با هم»: هر کشو (باز ⇐ موردِ انتظار؛ بسته ⇐ موردِ انتظار، شمرده، اختلاف) و سپس جمع‌ها */
  fun allLines(c: Drawers.Combined): List<ReportLine> = buildList {
    c.drawers.forEach { s ->
      add(ReportLine(StrDrawers.drawerState(s.label ?: StrDrawers.mainDrawer, s.open), shown(s.expectedMinor), strong = true))
      add(ReportLine(StrSale.openingCash, shown(s.openingMinor)))
      if (!s.open) {
        add(ReportLine(StrSale.countedCash, s.countedMinor?.let(::shown) ?: StrSale.notCounted))
        s.countedMinor?.let { add(ReportLine(StrDrawers.varianceWord, varianceText(it - s.expectedMinor))) }
      }
    }
    add(ReportLine(StrDrawers.totalOpening, shown(c.openingMinor)))
    add(ReportLine(StrDrawers.totalExpected, shown(c.expectedMinor), strong = true))
    if (c.closed > 0) {
      add(ReportLine(StrDrawers.totalCounted, shown(c.countedMinor)))
      add(ReportLine(StrDrawers.totalVariance, varianceText(c.varianceMinor), strong = true))
    }
  }

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

  /** [blind] (PAY-13): متنِ سابقه را همه می‌بینند؛ بستنِ کور مورد انتظار و اختلاف را نمی‌نویسد (Zِ کامل برای صاحب/مدیر هست) */
  fun close(expectedMinor: Long, countedMajor: Double, varianceMinor: Long, reason: String, me: String, blind: Boolean): String {
    val extra = listOf(reason.trim(), me.trim()).filter { it.isNotEmpty() }.joinToString(" — ")
    if (blind) return "بستنِ صندوق (شمارشِ کور): شمرده ${money(countedMajor)}" + (if (extra.isNotEmpty()) " — $extra" else "")
    return "بستنِ صندوق: مورد انتظار ${money(PayFlow.major(expectedMinor))}، شمرده ${money(countedMajor)}، " +
      (if (varianceMinor == 0L) "بی اختلاف" else if (varianceMinor > 0) "اضافه ${money(PayFlow.major(varianceMinor))}" else "کسری ${money(PayFlow.major(-varianceMinor))}") +
      (if (extra.isNotEmpty()) " — $extra" else "")
  }

  /** همان «صاحب/مدیر» ِ پیشین وقتی نامِ حساب خالی است */
  const val OWNER_OR_MANAGER = "صاحب/مدیر"

  /** PAY-07 — غیرنقدِ «در انتظار» و «نیازمند بررسی» ِ فاکتورهای این پنجره (به پول)؛ بیرون از جمعِ X */
  fun unreceived(d: ir.vil3ntec.tohid.data.ShopData, from: Long, to: Long): Long =
    ir.vil3ntec.tohid.data.PayStatusFlow.totals(d, from, to).let {
      (it[ir.vil3ntec.tohid.core.pay.PayStatus.PENDING] ?: 0L) + (it[ir.vil3ntec.tohid.core.pay.PayStatus.NEEDS_REVIEW] ?: 0L)
    }

  fun unreceivedNotes(minor: Long): List<String> =
    if (minor <= 0) emptyList()
    else listOf(ir.vil3ntec.tohid.core.i18n.StrPayStatus.unreceivedLine(ir.vil3ntec.tohid.money(PayFlow.major(minor))))
}
