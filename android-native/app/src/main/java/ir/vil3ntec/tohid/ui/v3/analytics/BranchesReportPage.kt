package ir.vil3ntec.tohid.ui.v3.analytics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrBranches
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.core.report.BranchesReport
import ir.vil3ntec.tohid.data.PayFlow
import ir.vil3ntec.tohid.data.repo.BranchesReportRepository
import ir.vil3ntec.tohid.data.repo.BranchesReportSource
import ir.vil3ntec.tohid.displayDate
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.screens.ReportFreshModel
import ir.vil3ntec.tohid.ui.screens.onlineReportApi
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.v3.V3Chip
import ir.vil3ntec.tohid.ui.v3.V3ChipRow
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.V3Hue
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Section
import ir.vil3ntec.tohid.ui.v3.V3Stat
import ir.vil3ntec.tohid.ui.v3.V3Type

/*
 *  RPT-18 — «گزارشِ همهٔ شعبه‌ها» در برنامهٔ مدیریت (`reports/branches`): برای صاحبی که چند دکان دارد، خلاصهٔ هر شعبه
 *  (همان گزارشِ خودکارِ صاحب) جدا و «جمعِ همه»، همه از `GET /api/shop/branches-report` (`core/report/BranchesReport`).
 *  ⛔ پرچمِ خاموشِ `shop.branches_report` (پیش‌فرض) ⇒ `{enabled:false}` ⇒ نه راه در «گزارش‌ها»، نه عدد (همان امروز).
 *  ⛔ مدیر/کارمند ⇒ `owner:false` ⇒ راه پنهان؛ نشانیِ مستقیم فقط «فقط برای صاحب» را نشان می‌دهد.
 */

/** «دیده شود؟» برای راهِ صفحه در «گزارش‌ها» ِ گوشی و کامپیوتر — فقط پاسخِ روشن با عدد؛ بی‌اینترنت ⇒ همان پیش */
object BranchesReportLive {
  var visible by mutableStateOf(false)
    private set

  suspend fun probe(src: BranchesReportSource, todayIso: String) {
    when (val r = src.load(1, todayIso)) {
      is ApiResult.Success -> visible = r.value is BranchesReport.Answer.Report
      is ApiResult.Failure -> if (ir.vil3ntec.tohid.ui.screens.serverRefused(r.error)) visible = false
    }
  }
}

/** حالِ صفحه: بازه، آخرین پاسخ و خطا */
@Stable
class BranchesReportState(private val src: BranchesReportSource) {
  var days by mutableStateOf(BranchesReport.SPANS.first())
  var answer by mutableStateOf<BranchesReport.Answer?>(null)
    private set
  var failed by mutableStateOf(false)
    private set
  var busy by mutableStateOf(false)
    private set

  suspend fun load(todayIso: String) {
    busy = true
    failed = false
    when (val r = src.load(days, todayIso)) {
      is ApiResult.Success -> answer = r.value
      is ApiResult.Failure ->
        if (ir.vil3ntec.tohid.ui.screens.serverRefused(r.error)) answer = BranchesReport.Answer.Off else failed = true
    }
    busy = false
  }
}

/** راهِ صفحه برای «گزارش‌ها»: با پرچمِ روشنِ سرور، کاربرِ صاحب و واردشده */
@Composable
internal fun rememberBranchesReportVisible(): Boolean {
  val api = onlineReportApi()
  LaunchedEffect(api) { if (api != null) BranchesReportLive.probe(BranchesReportRepository(api), todayIso()) }
  return api != null && BranchesReportLive.visible
}

@Composable
internal fun BranchesReportPage() {
  val api = onlineReportApi()
  val state = remember(api) { api?.let { BranchesReportState(BranchesReportRepository(it)) } }
  var tick by remember { mutableStateOf(0) }
  if (state != null) LaunchedEffect(state, state.days, tick) { state.load(todayIso()) }
  Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
    V3ChipRow(contentPadding = PaddingValues(0.dp)) {
      BranchesReport.SPANS.forEach { d -> V3Chip(StrBranches.span(d), state?.days == d, { state?.days = d }) }
    }
    Text(StrBranches.spanNote, style = V3Type.footnote, color = PhoneV3.colors.inkMuted, modifier = Modifier.padding(top = 8.dp))
    val a = state?.answer
    when {
      state == null || a == BranchesReport.Answer.Off -> V3EmptyState(Icons.Outlined.Storefront, StrBranches.off)
      a == BranchesReport.Answer.NotOwner -> V3EmptyState(Icons.Outlined.Storefront, StrBranches.ownerOnly)
      state.failed && a == null -> V3EmptyState(Icons.Outlined.CloudOff, StrBranches.failed, action = StrBranches.retry, onAction = { tick++ })
      a is BranchesReport.Answer.Report -> {
        if (state.failed) Text(StrBranches.failed, style = V3Type.footnote, color = PhoneV3.colors.inkMuted, modifier = Modifier.padding(top = 8.dp))
        BranchesBody(a.view)
      }
      else -> Text(StrBranches.loading, style = V3Type.callout, color = PhoneV3.colors.inkMuted, modifier = Modifier.padding(top = 16.dp))
    }
  }
}

private fun minor(v: Long?): Double = PayFlow.major(v ?: 0)
private fun count(v: Long?): String = v?.toString() ?: "—"
private fun shown(key: String, v: Long?): String =
  if (v == null) "—" else if (key in BranchesReport.COUNT_KEYS) count(v) else afn(minor(v))

private fun label(key: String): String = when (key) {
  "invoices" -> StrBranches.invoices
  "sales" -> StrBranches.sales
  "discount" -> StrBranches.discount
  "returnCount" -> StrBranches.returnCount
  "returns" -> StrBranches.returns
  "netSales" -> StrBranches.netSales
  "cashNet" -> StrBranches.cashNet
  else -> StrBranches.otherNet
}

private fun range(from: String?, to: String?): String? = when {
  from == null -> null
  to == null || to == from -> displayDate(from)
  else -> displayDate(from) + " — " + displayDate(to)
}

@Composable
private fun BranchesBody(v: BranchesReport.View) {
  val c = PhoneV3.colors
  Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
    V3Stat(StrBranches.netSales, minor(v.totals["netSales"]), ::afn, Modifier.weight(1f), hue = V3Hue.BRAND, caption = StrBranches.branchCount(v.branches.size))
    V3Stat(StrBranches.invoices, (v.totals["invoices"] ?: 0L).toDouble(), { it.toLong().toString() }, Modifier.weight(1f), hue = V3Hue.BLUE)
  }
  V3Section(StrBranches.allBranches, footer = StrBranches.totalsNote) {
    BranchesReport.SUM_KEYS.forEachIndexed { i, k ->
      if (i > 0) V3Divider()
      V3ListRow(label(k), value = shown(k, v.totals[k]), chevron = false)
    }
  }
  if (v.totals.partial) Text(StrBranches.partial, style = V3Type.footnote, color = PhoneV3Roles.errorText(c), modifier = Modifier.padding(top = 8.dp))
  v.branches.forEach { b -> BranchCard(b, v.totals) }
  if (v.skipped > 0) Text(StrBranches.skipped(v.skipped), style = V3Type.footnote, color = c.inkMuted, modifier = Modifier.padding(top = 12.dp))
  if (v.truncated) Text(StrBranches.truncated, style = V3Type.footnote, color = c.inkMuted, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun BranchCard(b: BranchesReport.Branch, totals: BranchesReport.Summary) {
  val s = b.summary
  val share = BranchesReport.shareBp(b, totals)?.let { StrBranches.share(ReportFreshModel.percent(it)) }
  val footer = listOfNotNull(range(b.from, b.to), share).joinToString(" · ").ifEmpty { null }
  V3Section(b.name.ifEmpty { b.shopId } + if (b.current) " · " + StrBranches.thisBranch else "", footer = footer) {
    if (s.empty) {
      V3ListRow(StrBranches.noneInRange, chevron = false)
    } else {
      BranchesReport.SUM_KEYS.forEachIndexed { i, k ->
        if (i > 0) V3Divider()
        V3ListRow(label(k), value = shown(k, s[k]), chevron = false)
      }
      if (s.payments.isNotEmpty()) {
        V3Divider()
        V3ListRow(
          StrBranches.payments, chevron = false, subtitleLines = 4,
          subtitle = s.payments.joinToString("، ") { p -> PayFlow.methodName(p.method.ifEmpty { "—" }) + " " + shown("netSales", p.netSales) },
        )
      }
      if (s.top.isNotEmpty()) {
        V3Divider()
        V3ListRow(
          StrBranches.top, chevron = false, subtitleLines = 4,
          subtitle = s.top.joinToString("، ") { t -> t.name + " (" + shown("netSales", t.netSales) + ")" },
        )
      }
    }
    s.lowStock?.let { n ->
      V3Divider()
      V3ListRow(StrBranches.lowStock(n), chevron = false)
    }
    if (s.partial) {
      V3Divider()
      V3ListRow(StrBranches.partial, chevron = false, danger = true)
    }
  }
}
