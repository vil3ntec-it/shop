package ir.vil3ntec.tohid.ui.v3.sales.drawer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.PointOfSale
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.vil3ntec.tohid.core.i18n.StrDrawers
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.data.CashShift
import ir.vil3ntec.tohid.data.PayFlow
import ir.vil3ntec.tohid.ui.v3.sales.history.SaleTime
import ir.vil3ntec.tohid.ui.components.pos.PhoneSaleText
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Status
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Card
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3Hue
import ir.vil3ntec.tohid.ui.v3.V3IconTile
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Scaffold
import ir.vil3ntec.tohid.ui.v3.V3Section
import ir.vil3ntec.tohid.ui.v3.V3StatusPill
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.rememberCountUp
import ir.vil3ntec.tohid.ui.v3.sales.history.SaleTotals

/** کارهای صفحهٔ صندوق — هر کدام برگهٔ خودش */
@Stable
class DrawerActions(
  val open: () -> Unit,
  val close: () -> Unit,
  /** فقط صندوقِ شیفت (`payV2`) */
  val move: () -> Unit,
  val noSale: () -> Unit,
  val xReport: () -> Unit,
  val shift: (String) -> Unit,
  val back: (() -> Unit)?,
  /** PAY-17: «ادامه روی این دستگاه» (شناسهٔ شیفتِ نام‌دار) یا کشوی اصلی (`null`) */
  val pick: (CashShift.Shift?) -> Unit = {},
  /** PAY-17: «همهٔ کشوها با هم» — `null` ⇒ بی ردیف (بی «چند کشو» یا نمای کور) */
  val all: (() -> Unit)? = null,
)

/** آنچه صفحه نشان می‌دهد — از `DrawerRoute` */
data class DrawerView(
  val shift: CashShift.Shift?,
  /** صندوقِ ساده: ریزِ صندوقِ باز */
  val totals: CashShift.Totals?,
  val payV2: Boolean,
  /** شمارشِ کور (شاگرد با صندوقِ شیفت) */
  val blind: Boolean,
  /** صندوقِ شیفت: نقدِ مورد انتظار یا خطای گزارش */
  val expectedMinor: Long?,
  val reportError: String?,
  val skipped: Int,
  val history: List<CashShift.Shift>,
  /** «ثبت از اسکنر» در همین صندوق (`DrawerModel.scannerLine`) */
  val scanner: String? = null,
  /** PAY-17: کشوهای باز (فقط با «چند کشو» ِ روشن؛ خاموش ⇒ `null` و صفحه همان امروز) */
  val drawers: List<CashShift.Shift>? = null,
)

/**
 *  صندوقِ نقد (`sales/drawer`، VILL3N §G5): کارتِ حال «صندوق باز — از …» / «صندوق بسته است»،
 *  وقتی باز است ریزِ صندوق (اول صندوق · فروش نقدی · رسید قرض‌داران · مرجوعی · مصارف · پرداخت
 *  به تأمین‌کننده و خرید) و «مورد انتظار» درشت؛ کارِ اصلی «باز کردنِ صندوق» / «بستنِ صندوق»؛
 *  صندوقِ شیفت: ورود/خروجِ نقد، کشو بی فروش و گزارش X. زیرش «سابقهٔ صندوق» (هر ردیف ⇐ ریزش).
 */
@Composable
fun DrawerScreen(v: DrawerView, actions: DrawerActions) {
  val c = PhoneV3.colors
  val open = v.shift != null
  V3Scaffold(title = StrSale.drawerWord, onBack = actions.back) { pad ->
    Column(
      Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = pad.calculateBottomPadding() + 16.dp),
    ) {
      V3Card(radius = 16.dp, padding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          V3IconTile(Icons.Outlined.PointOfSale, hue = if (open) V3Hue.BRAND else null, size = 44.dp)
          Spacer(Modifier.width(12.dp))
          Column(Modifier.weight(1f)) {
            Text(
              v.shift?.open?.drawer?.let { StrDrawers.drawerTitle(it) } ?: if (open) StrSale.drawerOpen else StrSale.drawerClosed,
              style = V3Type.headline, color = c.ink,
            )
            v.shift?.let { Text(StrSale.sinceWhen(SaleTime.stamp(it.open.createdAt)), style = V3Type.footnote, color = c.inkMuted) }
          }
          V3StatusPill(if (open) V3Status.DELIVERED else V3Status.INACTIVE, if (open) StrSale.openWord else StrSale.closedWord)
        }
        val expected = v.totals?.expected ?: v.expectedMinor?.let { PayFlow.major(it) }
        if (open && expected != null && !v.blind) {
          Spacer(Modifier.height(14.dp))
          Text(StrSale.expectedInDrawer, style = V3Type.subhead, color = c.inkMuted)
          Text(PhoneSaleText.amount(rememberCountUp(expected)), style = V3Type.figure.copy(fontSize = 30.sp, lineHeight = 38.sp), color = c.ink)
        }
        if (open && v.blind) {
          Spacer(Modifier.height(10.dp))
          Text(StrSale.blindCount, style = V3Type.footnote, color = c.inkMuted)
        }
        v.reportError?.let { Spacer(Modifier.height(8.dp)); Text(it, style = V3Type.footnote, color = PhoneV3Roles.errorText(c)) }
        if (v.skipped > 0) { Spacer(Modifier.height(8.dp)); V3StatusPill(V3Status.WARNING, StrSale.skippedOuts(v.skipped.toString())) }
      }
      v.totals?.takeIf { open }?.let {
        Spacer(Modifier.height(12.dp))
        SaleTotals(DrawerModel.breakdown(it))
      }
      if (v.shift?.open?.drawer != null) { Spacer(Modifier.height(8.dp)); Text(StrDrawers.namedNote, style = V3Type.footnote, color = c.inkMuted) }
      v.scanner?.takeIf { open }?.let { Spacer(Modifier.height(8.dp)); Text(it, style = V3Type.footnote, color = c.inkMuted) }
      Spacer(Modifier.height(14.dp))
      V3Button(if (open) StrSale.closeDrawer else StrSale.openDrawer, if (open) actions.close else actions.open, Modifier.fillMaxWidth(), haptic = true)
      if (open && v.payV2) {
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          V3Button(StrSale.cashMove, actions.move, Modifier.weight(1f), kind = V3ButtonKind.SECONDARY, icon = Icons.Outlined.SwapVert, height = 44.dp)
          V3Button(StrSale.noSaleOpen, actions.noSale, Modifier.weight(1f), kind = V3ButtonKind.SECONDARY, icon = Icons.Outlined.LockOpen, height = 44.dp)
        }
        Spacer(Modifier.height(8.dp))
        V3Button(StrSale.xReport, actions.xReport, Modifier.fillMaxWidth(), kind = V3ButtonKind.NEUTRAL, icon = Icons.AutoMirrored.Outlined.ReceiptLong, height = 44.dp)
      }
      v.drawers?.let { list -> DrawersSection(list, v.shift?.open?.id, actions.pick, actions.open, actions.all) }
      V3Section(StrSale.drawerHistory) {
        if (v.history.isEmpty()) Text(StrSale.noShiftYet, style = V3Type.callout, color = c.inkMuted, modifier = Modifier.padding(14.dp))
        v.history.forEachIndexed { i, sh ->
          if (i > 0) V3Divider(start = 64.dp)
          V3ListRow(
            title = SaleTime.stamp(sh.open.createdAt),
            subtitle = sh.close?.let { SaleTime.stamp(it.createdAt) + " — " + it.notes } ?: StrSale.stillOpen,
            icon = Icons.Outlined.PointOfSale,
            hue = if (sh.close == null) V3Hue.BRAND else null,
            onClick = { actions.shift(sh.open.id) },
          )
        }
      }
    }
  }
}

/** PAY-17: کشوهای باز — کدام روی همین دستگاه است، و «ادامه روی این دستگاه» برای بقیه */
@Composable
private fun DrawersSection(list: List<CashShift.Shift>, mine: String?, pick: (CashShift.Shift?) -> Unit, openNew: () -> Unit, all: (() -> Unit)?) {
  val c = PhoneV3.colors
  V3Section(StrDrawers.drawers, footer = StrDrawers.drawersNote) {
    if (list.isEmpty()) Text(StrDrawers.noneOpen, style = V3Type.callout, color = c.inkMuted, modifier = Modifier.padding(14.dp))
    list.forEachIndexed { i, sh ->
      if (i > 0) V3Divider(start = 64.dp)
      val isMine = sh.open.id == mine
      V3ListRow(
        title = sh.open.drawer ?: StrDrawers.mainDrawer,
        subtitle = StrDrawers.since(SaleTime.stamp(sh.open.createdAt), sh.open.by ?: ""),
        icon = Icons.Outlined.PointOfSale,
        hue = if (isMine) V3Hue.BRAND else null,
        value = if (isMine) StrDrawers.thisDevice else StrDrawers.continueHere,
        chevron = !isMine,
        onClick = if (isMine) null else ({ pick(sh.takeIf { it.open.drawer != null }) }),
      )
    }
    if (mine != null && list.any { it.open.id == mine && it.open.drawer != null }) {
      V3Divider(start = 64.dp)
      V3ListRow(title = StrDrawers.useMain, icon = Icons.Outlined.PointOfSale, onClick = { pick(null) })
    }
    //  صندوقدارِ دوم روی همین دستگاه: کشوی خودش را باز می‌کند، بی بستنِ کشوی اولی
    if (list.isNotEmpty()) V3Divider(start = 64.dp)
    V3ListRow(title = StrDrawers.newDrawer, icon = Icons.Outlined.PointOfSale, onClick = openNew)
    all?.let { V3Divider(start = 64.dp); V3ListRow(title = StrDrawers.allTitle, icon = Icons.AutoMirrored.Outlined.ReceiptLong, chevron = true, onClick = it) }
  }
}
