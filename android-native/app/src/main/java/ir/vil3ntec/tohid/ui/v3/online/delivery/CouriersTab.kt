package ir.vil3ntec.tohid.ui.v3.online.delivery

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeliveryDining
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.delivery.CourierCash
import ir.vil3ntec.tohid.core.delivery.CourierRow
import ir.vil3ntec.tohid.core.delivery.DeskConfig
import ir.vil3ntec.tohid.core.i18n.StrOnline
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Status
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Card
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3Springs
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.online.DraftTag
import ir.vil3ntec.tohid.ui.v3.rememberV3Stagger
import ir.vil3ntec.tohid.ui.v3.v3AnimateItem
import ir.vil3ntec.tohid.ui.v3.v3Stagger

class CouriersActions(val onSettle: (CourierRow) -> Unit, val onLedger: (CourierRow) -> Unit, val onCap: (CourierRow) -> Unit)

/**
 *  برگهٔ «پیک‌ها و نقد» (DLV-11/12): هر پیک یک کارت — وضعیت (آماده/به سقف رسیده/بالای سقف/غیرفعال)،
 *  نوارِ پرشدنِ سقف با درصد، «در دست»، «گرفتنی از مأموریت‌های زنده»، «سقف · جای خالی»، دلیلِ «خودکار
 *  نمی‌گیرد»، و تسویه / دفترِ نقد (فقط صاحب و مدیر) و سقف و وضعیت (`delivery.couriers`).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CouriersTab(d: DeskData, cfg: DeskConfig, writable: Boolean, actions: CouriersActions, pad: PaddingValues, header: @Composable () -> Unit) {
  val stagger = rememberV3Stagger()
  LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = pad.calculateBottomPadding())) {
    item(key = "head") { header() }
    if (d.couriers.isEmpty()) {
      item(key = "empty") { V3EmptyState(Icons.Outlined.DeliveryDining, StrOnline.dlNoCouriers, body = StrOnline.dlNoCouriersBody) }
      return@LazyColumn
    }
    if (!cfg.perms.settle) item(key = "owner") { V3Message(V3Tone.INFO, StrOnline.dlSettleOwner, Modifier.padding(top = 8.dp)) }
    itemsIndexed(d.couriers, key = { _, cr -> cr.id }) { i, cr ->
      V3Card(Modifier.padding(top = 10.dp).v3AnimateItem(this).v3Stagger(stagger, cr.id, i), padding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(cr.name, style = V3Type.headline, color = PhoneV3.colors.ink, modifier = Modifier.weight(1f))
          val (label, tone) = DeliveryModel.courierState(cr)
          DraftTag(label, tone = tone)
        }
        Spacer(Modifier.height(10.dp))
        DraftCapBar(CourierCash.usePercent(cr), CourierCash.atCap(cr), StrOnline.afn(cr.cashCapMinor))
        Spacer(Modifier.height(10.dp))
        Row {
          Figure(StrOnline.dlHeld, StrOnline.afn(cr.cashHeldMinor), Modifier.weight(1f))
          Figure(StrOnline.dlLiveExpected(cr.liveCount), StrOnline.afn(cr.liveExpectedMinor), Modifier.weight(1f))
          Figure(StrOnline.dlRoom, StrOnline.afn(CourierCash.headroom(cr)), Modifier.weight(1f))
        }
        CourierCash.autoBlockReason(cr)?.let {
          Spacer(Modifier.height(8.dp))
          DraftTag(it, tone = V3Tone.WARNING)
        }
        val buttons = buildList<Pair<String, () -> Unit>> {
          if (cfg.perms.settle && writable) add(StrOnline.dlSettle to { actions.onSettle(cr) })
          if (cfg.perms.settle) add(StrOnline.dlLedger to { actions.onLedger(cr) })
          if (cfg.perms.couriers && writable) add(StrOnline.dlCapAndState to { actions.onCap(cr) })
        }
        if (buttons.isNotEmpty()) {
          Spacer(Modifier.height(12.dp))
          FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            buttons.forEachIndexed { j, (label, go) ->
              V3Button(label, go, kind = if (j == 0 && cfg.perms.settle && writable) V3ButtonKind.PRIMARY else V3ButtonKind.SECONDARY, height = 38.dp)
            }
          }
        }
      }
    }
  }
}

/**
 *  نوارِ پرشدنِ سقفِ نقد — درصد کنارش نوشته است، نه فقط رنگ؛ پرشدن با فنرِ کیت (فقط مقیاسِ لایه،
 *  بی چیدمانِ دوباره). به سقف رسیده ⇐ رنگِ «در انتظار/هشدار». ⇐ `DraftCapBar` برای بردن به کیت.
 */
@Composable
internal fun DraftCapBar(percent: Int, full: Boolean, cap: String) {
  val c = PhoneV3.colors
  val fill by animateFloatAsState(percent.coerceIn(0, 100) / 100f, V3Springs.ui(), label = "capFill")
  val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
  val bar = if (full) PhoneV3Roles.status(c, V3Status.WARNING).fg else PhoneV3Roles.button(c, V3ButtonKind.PRIMARY).bg
  Row(verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(50)).background(c.tile)) {
      Box(
        Modifier
          .fillMaxWidth()
          .fillMaxHeight()
          .graphicsLayer {
            scaleX = fill
            transformOrigin = TransformOrigin(if (rtl) 1f else 0f, 0.5f)
          }
          .clip(RoundedCornerShape(50))
          .background(bar),
      )
    }
    Spacer(Modifier.width(8.dp))
    Text(StrOnline.dlCapPercent(percent, cap), style = V3Type.caption, color = c.inkMuted)
  }
}
