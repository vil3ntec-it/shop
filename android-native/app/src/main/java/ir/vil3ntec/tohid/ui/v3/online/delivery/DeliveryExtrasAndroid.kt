package ir.vil3ntec.tohid.ui.v3.online.delivery

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.core.delivery.DeskConfig
import ir.vil3ntec.tohid.core.i18n.StrReach
import ir.vil3ntec.tohid.ui.screens.CashReportSheet
import ir.vil3ntec.tohid.ui.screens.DeliveryGate
import ir.vil3ntec.tohid.ui.screens.RatingsSheet
import ir.vil3ntec.tohid.ui.v3.V3MenuItem
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.online.OnlinePaths

/**
 *  «…» ِ میزِ تحویل: گزارشِ نقدِ پیک‌ها (RPT-15؛ همان «تسویه»‌گر) و امتیازِ تحویل‌ها (DLV-18؛ پرچمِ دکان و
 *  `couriers`) — همان دو برگهٔ میزِ تحویلِ پیشین (`ui/screens`)، روی میز.
 */
internal fun RouteScope.deliveryExtras(cfg: DeskConfig): List<V3MenuItem> = buildList {
  if (cfg.perms.settle) add(V3MenuItem(Icons.Outlined.Assessment, StrReach.cashReport) { nav.open(OnlinePaths.DELIVERY_CASH_REPORT) })
  if (cfg.ratings && cfg.perms.couriers) add(V3MenuItem(Icons.Outlined.StarOutline, StrReach.ratings) { nav.open(OnlinePaths.DELIVERY_RATINGS) })
}

@Composable
internal fun RouteScope.DeliveryCashReportRoute() {
  val context = LocalContext.current
  CashReportSheet(remember(context) { DeliveryGate.repository(context) }) { back() }
}

@Composable
internal fun RouteScope.DeliveryRatingsRoute() {
  val context = LocalContext.current
  RatingsSheet(remember(context) { DeliveryGate.repository(context) }) { back() }
}
