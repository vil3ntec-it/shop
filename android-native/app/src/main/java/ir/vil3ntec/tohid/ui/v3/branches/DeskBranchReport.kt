package ir.vil3ntec.tohid.ui.v3.branches

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Store
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ir.vil3ntec.tohid.core.branch.BranchTally
import ir.vil3ntec.tohid.core.i18n.StrBranchDesk
import ir.vil3ntec.tohid.core.i18n.StrBranches
import ir.vil3ntec.tohid.core.report.BranchesReport
import ir.vil3ntec.tohid.ui.desk.DeskCell
import ir.vil3ntec.tohid.ui.desk.DeskChip
import ir.vil3ntec.tohid.ui.desk.DeskColumn
import ir.vil3ntec.tohid.ui.desk.DeskEmpty
import ir.vil3ntec.tohid.ui.desk.DeskGrid
import ir.vil3ntec.tohid.ui.desk.DeskHues
import ir.vil3ntec.tohid.ui.desk.DeskKpiRow
import ir.vil3ntec.tohid.ui.desk.DeskNote
import ir.vil3ntec.tohid.ui.desk.DeskPage
import ir.vil3ntec.tohid.ui.desk.DeskSkeleton
import ir.vil3ntec.tohid.ui.desk.DeskStatTile
import ir.vil3ntec.tohid.ui.desk.DeskToolbar

/*
 *  RPT-18 — «گزارشِ شعبه‌ها» ِ کامپیوتر روی `ui/desk` (همان `BranchReportState` ِ گوشی): دو کارتِ عدد (فروشِ خالص و فاکتور، جمعِ
 *  همه برای صاحب)، تراشهٔ بازه، جدولِ هر شعبه با ستون‌های همان خلاصهٔ سرور و ردیفِ جمعِ پایین — فقط وقتی سرور `totals` داد (صاحب).
 */
@Composable
internal fun DeskBranchReport(state: BranchReportState?, onRetry: () -> Unit) {
  val tone = DeskHues.tone("reports")
  DeskPage {
    val v = state?.view
    if (v != null) {
      val total = BranchReportFigures.total(v)
      DeskKpiRow {
        DeskStatTile(if (v.totals != null) StrBranchDesk.allTogether else StrBranchDesk.report, shown("netSales", total["netSales"]), Icons.Outlined.Payments, tone,
          Modifier.weight(1f), note = StrBranches.branchCount(v.rows.size))
        DeskStatTile(label("invoices"), shown("invoices", total["invoices"]), Icons.AutoMirrored.Outlined.ReceiptLong, tone, Modifier.weight(1f))
      }
    }
    DeskToolbar {
      BranchTally.SPANS.forEach { d -> DeskChip(StrBranches.span(d), state?.days == d, { state?.days = d }, tone = tone) }
    }
    when {
      state == null || state.off -> DeskEmpty(Icons.Outlined.Store, StrBranchDesk.report, StrBranchDesk.off, tone)
      v == null && state.failed -> DeskEmpty(Icons.Outlined.CloudOff, StrBranchDesk.report, StrBranchDesk.failed, tone, action = StrBranchDesk.retry, onAction = onRetry)
      v == null -> DeskSkeleton()
      else -> {
        val keys = BranchesReport.SUM_KEYS
        val columns = listOf(DeskColumn(StrBranchDesk.title, weight = 1.6f)) + keys.map { DeskColumn(label(it), weight = 1f, end = true) }
        val footer = v.totals?.let { t -> listOf(StrBranchDesk.allTogether) + keys.map { shown(it, t[it]) } }
        DeskGrid(
          columns, v.rows.size, Modifier.weight(1f).fillMaxSize(),
          key = { v.rows[it].id.ifEmpty { "main" } },
          rowLabel = { ir.vil3ntec.tohid.core.i18n.branchName(v.rows[it].name, v.rows[it].main) },
          footer = footer,
        ) { r, col ->
          val row = v.rows[r]
          if (col == 0) DeskCell(ir.vil3ntec.tohid.core.i18n.branchName(row.name, row.main) + if (row.archived) " · " + StrBranchDesk.archived else "", strong = true)
          else DeskCell(shown(keys[col - 1], row.summary[keys[col - 1]]), muted = row.summary.empty)
        }
        DeskNote(if (v.totals != null) StrBranches.totalsNote else StrBranchDesk.reportNote, maxLines = 2)
        if (v.totals?.partial == true) DeskNote(StrBranches.partial, maxLines = 2)
      }
    }
  }
}
