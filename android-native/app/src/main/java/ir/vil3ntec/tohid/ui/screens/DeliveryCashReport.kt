package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.delivery.CashReport
import ir.vil3ntec.tohid.core.delivery.CashReportMath
import ir.vil3ntec.tohid.core.delivery.CashReportRow
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrCourierApp
import ir.vil3ntec.tohid.core.i18n.StrHistory
import ir.vil3ntec.tohid.core.i18n.StrInvoices
import ir.vil3ntec.tohid.core.i18n.StrOnline
import ir.vil3ntec.tohid.core.i18n.StrOrderPages
import ir.vil3ntec.tohid.core.report.ReportBoard
import ir.vil3ntec.tohid.core.report.ReportDates
import ir.vil3ntec.tohid.data.repo.DeliveryRepository
import ir.vil3ntec.tohid.isoMillis
import ir.vil3ntec.tohid.ui.components.common.PillTone
import ir.vil3ntec.tohid.ui.components.common.SegmentTab
import ir.vil3ntec.tohid.ui.components.common.SegmentedTabs
import ir.vil3ntec.tohid.ui.components.common.StatusPill
import ir.vil3ntec.tohid.ui.theme.Shop

/*
 *  RPT-15 — «نقدِ پیک‌ها و تسویه» در یک بازه، برای صاحب/مدیر (اجازهٔ `delivery.settle`).
 *  همهٔ عددها از سرور (`GET /shop/delivery/cash-report`)؛ این‌جا فقط بازه، برچسب و هشدارِ
 *  ناهم‌خوانی (`CashReportMath`). روزها همان روزهای «گزارشِ تازه»اند (نیمه‌شبِ همین دستگاه).
 */

private val SPANS: List<Pair<String, String>> get() = listOf("today" to Str.today, "week" to StrInvoices.last7Days, "month" to StrHistory.thisMonth)

/** [از، تا) ِ روزهای بازه به میلی‌ثانیه: نیمه‌شبِ روزِ اول تا نیمه‌شبِ پس از روزِ آخر (`isoMillis` ظهر است) */
internal fun cashReportWindow(span: String, today: String): Pair<Long, Long>? {
  val r = ReportBoard.range(span, today)
  val next = ReportDates.addDays(r.to, 1).ifEmpty { return null }
  val half = 12 * 3_600_000L
  val from = isoMillis(r.from) - half
  val to = isoMillis(next) - half
  return if (to > from) from to to else null
}

@Composable
private fun ReportLine(label: String, value: String, strong: Boolean = false, tone: Color? = null) {
  Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
    Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Shop.colors.muted)
    Text(
      value, style = MaterialTheme.typography.bodyMedium, color = tone ?: Shop.colors.text,
      fontWeight = if (strong) FontWeight.Bold else FontWeight.Normal,
    )
  }
}

@Composable
private fun Figures(x: CashReportRow) {
  val c = Shop.colors
  ReportLine(StrOrderPages.cashDeliveries, "${x.deliveries}")
  ReportLine(StrOrderPages.cashCollected, afn(x.collectedCashMinor))
  val diff = CashReportMath.customerDiff(x)
  if (diff != 0L) ReportLine(StrOrderPages.cashCustomerDiff, afn(diff), tone = c.warning)
  ReportLine(StrOrderPages.cashSettledPart, afn(CashReportMath.settledOfCollected(x)))
  ReportLine(StrOrderPages.cashUnsettledPart, afn(x.unsettledMinor), tone = if (x.unsettledMinor > 0) c.warning else null)
  ReportLine(StrOrderPages.cashSettlements(x.settlements), StrOrderPages.cashSettleLine(afn(x.handoverDueMinor), afn(x.receivedMinor)))
  if (x.shortageMinor > 0) ReportLine(StrOrderPages.cashShortage, afn(x.shortageMinor), tone = c.danger)
  if (x.excessMinor > 0) ReportLine(StrOrderPages.cashExcess, afn(x.excessMinor), tone = c.warning)
  if (x.fareTotalMinor > 0) ReportLine(StrOnline.dlFares, afn(x.fareTotalMinor))
  ReportLine(StrOrderPages.cashInHand, afn(x.inHandMinor), strong = true)
}

/** برگهٔ گزارشِ نقدِ پیک‌ها؛ بازه پیش‌فرض «امروز» */
@Composable
internal fun CashReportSheet(repo: DeliveryRepository, onDismiss: () -> Unit) {
  val c = Shop.colors
  val today = ir.vil3ntec.tohid.todayIso()
  var span by remember { mutableStateOf("today") }
  var report by remember { mutableStateOf<CashReport?>(null) }
  var err by remember { mutableStateOf<String?>(null) }
  var reload by remember { mutableIntStateOf(0) }
  LaunchedEffect(span, reload) {
    report = null
    err = null
    val w = cashReportWindow(span, today)
    if (w == null) {
      err = StrOrderPages.cashBadRange
      return@LaunchedEffect
    }
    repo.cashReport(w.first, w.second).onSuccess { report = it }.onFailure { err = it.userMessage }
  }
  TohidBottomSheet(title = StrOrderPages.cashTitle, onDismiss = onDismiss) {
    SheetBody {
      SegmentedTabs(SPANS.map { SegmentTab(it.first, it.second) }, span, { span = it })
      Spacer(Modifier.height(8.dp))
      val r = report
      if (r == null) {
        if (err != null) TohidErrorState(title = StrOrderPages.cashNoReport, description = err ?: "", onRetry = { reload++ })
        else TohidLoadingState(rows = 3)
        return@SheetBody
      }
      Row(verticalAlignment = Alignment.CenterVertically) {
        if (CashReportMath.consistent(r)) StatusPill(StrOrderPages.cashConsistent, PillTone.SUCCESS)
        else StatusPill(StrOrderPages.cashInconsistent, PillTone.DANGER)
        Spacer(Modifier.width(8.dp))
        Text(StrOrderPages.cashServerTime, style = MaterialTheme.typography.bodySmall, color = c.muted)
      }
      DeskSection(StrOrderPages.cashAllCouriers)
      Figures(r.totals)
      if (r.couriers.isEmpty()) {
        Text(StrOrderPages.cashNoFlow, style = MaterialTheme.typography.bodySmall, color = c.muted)
      }
      r.couriers.forEach { x ->
        Spacer(Modifier.height(8.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.surface2).padding(10.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(x.name.ifBlank { StrCourierApp.courier }, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, color = c.text)
            if (CashReportMath.attention(x)) StatusPill(StrOrderPages.cashNeedsLook, PillTone.WARNING) else StatusPill(StrOrderPages.cashOk, PillTone.SUCCESS)
          }
          Figures(x)
        }
      }
    }
  }
}
