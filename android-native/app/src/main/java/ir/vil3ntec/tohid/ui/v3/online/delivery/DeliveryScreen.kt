package ir.vil3ntec.tohid.ui.v3.online.delivery

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.delivery.DeskConfig
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.OnlineFmt
import ir.vil3ntec.tohid.core.i18n.StrOnline
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Action
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.V3Hue
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3PageSwitch
import ir.vil3ntec.tohid.ui.v3.V3Scaffold
import ir.vil3ntec.tohid.ui.v3.V3Skeleton
import ir.vil3ntec.tohid.ui.v3.V3Stat
import ir.vil3ntec.tohid.ui.v3.V3StatGrid
import ir.vil3ntec.tohid.ui.v3.online.CountTab
import ir.vil3ntec.tohid.ui.v3.online.DraftCountTabs

/**
 *  میزِ تحویل (`delivery?tab=`، فقط با `DeliveryGate`): چهار عددِ بالای میز با شمارنده (زدن ⇐ همان
 *  برگه)، نوارِ «فقط دیدن» بی اشتراک، تب‌های شمارش‌دار، و برگه‌ها با حرکتِ کیت. روی کامپیوترِ پهن
 *  چهار عدد در یک ردیف.
 */
@Composable
internal fun DeliveryScreen(
  cfg: DeskConfig,
  data: DeskData?,
  error: String?,
  loading: Boolean,
  tab: String,
  wide: Boolean,
  onTab: (String) -> Unit,
  onRefresh: () -> Unit,
  onBack: (() -> Unit)?,
  /** محتوای هر برگه؛ `header` (عددها و پیام‌ها) نخستین ردیفِ فهرستِ همان برگه است تا با آن بلغزد */
  pane: @Composable (tab: String, d: DeskData, pad: PaddingValues, header: @Composable () -> Unit) -> Unit,
) {
  val tabs = DeliveryModel.tabs(cfg)
  V3Scaffold(
    title = StrOnline.dlTitle,
    subtitle = StrOnline.dlCaption,
    onBack = onBack,
    actions = listOf(V3Action(Icons.Outlined.Refresh, StrOnline.reload, onClick = onRefresh)),
    chips = data?.let { d ->
      { DraftCountTabs(tabs.map { CountTab(it, DeliveryModel.label(it), DeliveryModel.count(it, d)) }, tab, onTab) }
    },
  ) { pad ->
    val d = data
    when {
      d == null && error != null -> V3EmptyState(Icons.Outlined.CloudOff, StrOnline.dlLoadFailed, body = error, action = StrKit.retry, onAction = onRefresh)
      d == null -> Column(Modifier.fillMaxSize().padding(16.dp)) { V3Skeleton(rows = 5) }
      else -> V3PageSwitch(tab, depth = { tabs.indexOf(it) }, modifier = Modifier.fillMaxSize()) { t ->
        pane(t, d, pad) {
          Column(Modifier.padding(bottom = 4.dp)) {
            if (!cfg.entitled) V3Message(V3Tone.WARNING, StrOnline.dlNotEntitled, Modifier.padding(bottom = 8.dp))
            if (error != null) V3Message(V3Tone.WARNING, StrOnline.dlLastFailed(error), Modifier.padding(bottom = 8.dp))
            DeskStats(d, wide, onTab)
          }
        }
      }
    }
  }
}

@Composable
private fun DeskStats(d: DeskData, wide: Boolean, onTab: (String) -> Unit) {
  val s = DeliveryModel.stats(d)
  V3StatGrid(
    columns = if (wide) 4 else 2,
    items = listOf(
      { m -> V3Stat(StrOnline.dlStatReady, s.ready.toDouble(), { it.toLong().toString() }, m, hue = V3Hue.AMBER, caption = StrOnline.dlNow, onClick = { onTab(DeliveryModel.ASSIGN) }) },
      { m -> V3Stat(StrOnline.dlStatLive, s.live.toDouble(), { it.toLong().toString() }, m, hue = V3Hue.SKY, caption = StrOnline.dlNow, onClick = { onTab(DeliveryModel.ASSIGN) }) },
      { m -> V3Stat(StrOnline.dlStatCash, s.heldMinor.toDouble(), { OnlineFmt.afn(it.toLong()) }, m, hue = V3Hue.MINT, caption = StrOnline.dlUnsettled, onClick = { onTab(DeliveryModel.COURIERS) }) },
      { m -> V3Stat(StrOnline.dlStatAtCap, s.atCap.toDouble(), { it.toLong().toString() }, m, hue = V3Hue.CORAL, caption = StrOnline.dlNoAuto, onClick = { onTab(DeliveryModel.COURIERS) }) },
    ),
  )
}
