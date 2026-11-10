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
import androidx.compose.material.icons.outlined.InsertChart
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
import ir.vil3ntec.tohid.core.i18n.StrOnlineReport
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.core.report.OnlineReport
import ir.vil3ntec.tohid.data.repo.OnlineReportRepository
import ir.vil3ntec.tohid.data.repo.OnlineReportSource
import ir.vil3ntec.tohid.ui.screens.ReportFreshModel
import ir.vil3ntec.tohid.ui.screens.onlineReportApi
import ir.vil3ntec.tohid.ui.screens.platformNow
import ir.vil3ntec.tohid.ui.screens.serverRefused
import ir.vil3ntec.tohid.ui.theme.PhoneV3
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
 *  RPT-14 — «گزارشِ فروشِ اینترنتی» در برنامهٔ مدیریت (`reports/online`): قیفِ سفارشِ ویترین، ازدست‌رفته به دلیل،
 *  زمان‌ها و جست‌وجوهای بی‌نتیجه، همه از `GET /api/shop/orders/online-report` (عدد فقط از سرور، `core/report/OnlineReport`).
 *  ⛔ پرچمِ خاموشِ `shop.orders.online_report` (پیش‌فرض) ⇒ سرور ۴۰۳ ⇒ نه راه در «گزارش‌ها»، نه صفحه (همان امروز).
 */

/** «دیده شود؟» برای راهِ صفحه در «گزارش‌ها» ِ گوشی و کامپیوتر — یک پرسشِ ۲۴ساعته؛ ردِ سرور ⇒ پنهان، بی‌اینترنت ⇒ همان پیش */
object OnlineReportLive {
  var visible by mutableStateOf(false)
    private set

  suspend fun probe(src: OnlineReportSource, nowMs: Long) {
    src.load(OnlineReport.SPANS.first(), nowMs)
      .onSuccess { visible = true }
      .onFailure { f -> if (serverRefused(f)) visible = false }
  }
}

/** حالِ صفحه: بازه، آخرین نما و خطا */
@Stable
class OnlineReportState(private val src: OnlineReportSource) {
  var days by mutableStateOf(OnlineReport.SPANS[1])
  var view by mutableStateOf<OnlineReport.View?>(null)
    private set
  var off by mutableStateOf(false)
    private set
  var failed by mutableStateOf(false)
    private set
  var busy by mutableStateOf(false)
    private set

  suspend fun load(nowMs: Long) {
    busy = true
    failed = false
    when (val r = src.load(days, nowMs)) {
      is ApiResult.Success -> { view = r.value; off = false }
      is ApiResult.Failure -> if (serverRefused(r.error)) { off = true; view = null } else failed = true
    }
    busy = false
  }
}

/** راهِ صفحه برای «گزارش‌ها»: با پرچمِ روشنِ سرور و کاربرِ واردشده */
@Composable
internal fun rememberOnlineReportVisible(): Boolean {
  val api = onlineReportApi()
  LaunchedEffect(api) { if (api != null) OnlineReportLive.probe(OnlineReportRepository(api), platformNow()) }
  return api != null && OnlineReportLive.visible
}

@Composable
internal fun OnlineReportPage() {
  val api = onlineReportApi()
  val state = remember(api) { api?.let { OnlineReportState(OnlineReportRepository(it)) } }
  var tick by remember { mutableStateOf(0) }
  if (state != null) LaunchedEffect(state, state.days, tick) { state.load(platformNow()) }
  Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
    V3ChipRow(contentPadding = PaddingValues(0.dp)) {
      OnlineReport.SPANS.forEach { d -> V3Chip(StrOnlineReport.span(d), state?.days == d, { state?.days = d }) }
    }
    val v = state?.view
    when {
      state == null || state.off -> V3EmptyState(Icons.Outlined.InsertChart, StrOnlineReport.off)
      state.failed -> V3EmptyState(Icons.Outlined.CloudOff, StrOnlineReport.failed, action = StrOnlineReport.retry, onAction = { tick++ })
      v == null -> Text(StrOnlineReport.loading, style = V3Type.callout, color = PhoneV3.colors.inkMuted, modifier = Modifier.padding(top = 16.dp))
      else -> OnlineReportBody(v)
    }
  }
}

/** برچسبِ هر کلیدِ پاسخ (گام، دلیل، زمان) — کلیدها همان `OnlineReport.STEPS`/`LOST`/`TIMES` */
private fun label(key: String): String = when (key) {
  "placed" -> StrOnlineReport.placed
  "accepted" -> StrOnlineReport.accepted
  "packed" -> StrOnlineReport.packed
  "handedOver" -> StrOnlineReport.handedOver
  "completed" -> StrOnlineReport.completed
  "rejected" -> StrOnlineReport.rejected
  "auto_rejected" -> StrOnlineReport.autoRejected
  "cancelled_by_customer" -> StrOnlineReport.cancelledByCustomer
  "cancelled" -> StrOnlineReport.cancelledByShop
  "returned_to_store" -> StrOnlineReport.returnedToStore
  OnlineReport.DELIVERY_FAILED -> StrOnlineReport.deliveryFailed
  "accept" -> StrOnlineReport.untilAccepted
  "prep" -> StrOnlineReport.preparation
  "delivery" -> StrOnlineReport.delivery
  else -> StrOnlineReport.total
}

private fun count(n: Int?): String = n?.toString() ?: "—"
private fun pct(bp: Long?): String = bp?.let { ReportFreshModel.percent(it) } ?: "—"
private fun duration(ms: Long?): String = OnlineReport.hoursMinutes(ms)?.let { (h, m) -> StrOnlineReport.duration(h, m) } ?: "—"

@Composable
private fun OnlineReportBody(v: OnlineReport.View) {
  val c = PhoneV3.colors
  Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
    V3Stat(StrOnlineReport.placed, (v.funnel["placed"] ?: 0).toDouble(), { it.toLong().toString() }, Modifier.weight(1f), hue = V3Hue.BLUE)
    V3Stat(StrOnlineReport.completedTotal, (v.completedTotalMinor ?: 0L) / 100.0, ::afn, Modifier.weight(1f), hue = V3Hue.BRAND)
  }
  if (v.empty) Text(StrOnlineReport.none, style = V3Type.callout, color = c.inkMuted, modifier = Modifier.padding(top = 12.dp))
  //  RPT-14: بالای قیفِ ویترینِ وب، همان عددِ سرور (`OnlineReport.Top`)؛ هنوز شمرده نشده ⇒ یادداشتِ پایینِ صفحه
  val top = v.top
  if (top != null) {
    V3Section(StrOnlineReport.webFunnel, footer = if (top.capped) StrOnlineReport.webFunnelCapped else StrOnlineReport.webFunnelNote) {
      V3ListRow(StrOnlineReport.visitors, value = count(top.visits), chevron = false)
      V3Divider()
      V3ListRow(StrOnlineReport.productViewers, value = count(top.productViews) + (top.viewBp?.let { " · " + pct(it) } ?: ""), chevron = false)
      V3Divider()
      V3ListRow(StrOnlineReport.cartAdders, value = count(top.cartAdds) + (top.cartBp?.let { " · " + pct(it) } ?: ""), chevron = false)
    }
  }
  V3Section(StrOnlineReport.funnel, footer = StrOnlineReport.funnelNote) {
    OnlineReport.STEPS.forEachIndexed { i, k ->
      if (i > 0) V3Divider()
      val rate = if (i == 0) null else OnlineReport.stepBp(v, k)
      V3ListRow(label(k), value = count(v.funnel[k]) + (rate?.let { " · " + pct(it) } ?: ""), chevron = false)
    }
  }
  V3Section(StrOnlineReport.lost, footer = StrOnlineReport.lostRate(pct(v.lostBp))) {
    OnlineReport.LOST.forEachIndexed { i, k ->
      if (i > 0) V3Divider()
      V3ListRow(label(k), value = count(v.lost[k]), chevron = false)
    }
    V3Divider()
    V3ListRow(
      StrOnlineReport.deliveryFailed, subtitle = StrOnlineReport.failRate(pct(v.deliveryFailBp)),
      value = count(v.lost[OnlineReport.DELIVERY_FAILED]), chevron = false,
    )
  }
  V3Section(StrOnlineReport.times, footer = StrOnlineReport.timesNote) {
    OnlineReport.TIMES.forEachIndexed { i, k ->
      if (i > 0) V3Divider()
      val t = v.times[k]
      V3ListRow(label(k), subtitle = StrOnlineReport.median(duration(t?.medianMs), duration(t?.p90Ms)), value = count(t?.n), chevron = false)
    }
  }
  V3Section(StrOnlineReport.misses, footer = StrOnlineReport.missesNote) {
    if (v.misses.isEmpty()) V3ListRow(StrOnlineReport.missesNone, chevron = false)
    v.misses.forEachIndexed { i, m ->
      if (i > 0) V3Divider()
      V3ListRow(m.q, value = StrOnlineReport.times(m.n), chevron = false)
    }
  }
  if (top == null) Text(StrOnlineReport.visitsNone, style = V3Type.footnote, color = c.inkMuted, modifier = Modifier.padding(top = 12.dp))
}
