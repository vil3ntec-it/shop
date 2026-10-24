package ir.vil3ntec.tohid.ui.v3.online.delivery

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.delivery.CourierCash
import ir.vil3ntec.tohid.core.delivery.CourierRow
import ir.vil3ntec.tohid.core.delivery.SettlementPage
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.StrOnline
import ir.vil3ntec.tohid.data.repo.DeliveryRepository
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3Status
import ir.vil3ntec.tohid.ui.v3.V3Card
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.V3Hue
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Section
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3Skeleton
import ir.vil3ntec.tohid.ui.v3.online.orders.OrdersModel

/**
 *  دفترِ نقدِ یک پیک (`delivery/couriers/{id}/ledger`): گرفته، تحویلِ دکان، مانده، کرایه‌ها، کسری و
 *  اضافهٔ تسویه‌ها (`CourierCash.ledger`) و سی تسویهٔ اخیر — فقط دیدن.
 */
@Composable
internal fun LedgerSheet(cr: CourierRow, repo: DeliveryRepository, onDismiss: () -> Unit, start: SettlementPage? = null) {
  val c = PhoneV3.colors
  var page by remember { mutableStateOf(start) }
  var err by remember { mutableStateOf<String?>(null) }
  var reload by remember { mutableIntStateOf(0) }
  LaunchedEffect(reload) {
    if (start != null && reload == 0) return@LaunchedEffect
    repo.settlements(cr.id, RECENT).onSuccess { page = it; err = null }.onFailure { err = it.userMessage }
  }
  V3Sheet(onDismiss = onDismiss, title = StrOnline.dlLedgerTitle(cr.name)) { _ ->
    val p = page
    when {
      p == null && err != null -> V3EmptyState(Icons.Outlined.CloudOff, StrOnline.dlLedgerFailed, body = err, action = StrKit.retry, onAction = { reload++ })
      p == null -> V3Skeleton(rows = 3)
      else -> {
        val l = CourierCash.ledger(cr, p.sums)
        V3Card(padding = PaddingValues(vertical = 4.dp)) {
          Sum(StrOnline.dlCollectedAll, StrOnline.afn(l.collectedMinor))
          Sum(StrOnline.dlHandedBack, StrOnline.afn(l.handedBackMinor))
          Sum(StrOnline.dlOutstanding, StrOnline.afn(l.outstandingMinor), strong = true)
          Sum(StrOnline.dlFares, StrOnline.afn(l.faresMinor))
          if (l.shortageMinor > 0) Sum(StrOnline.dlShortages, StrOnline.afn(l.shortageMinor), tone = PhoneV3Roles.errorText(c))
          if (l.excessMinor > 0) Sum(StrOnline.dlExcesses, StrOnline.afn(l.excessMinor), tone = PhoneV3Roles.status(c, V3Status.WARNING).fg)
        }
        V3Section(StrOnline.dlRecent) {
          if (p.items.isEmpty()) V3ListRow(StrOnline.dlNoSettlements, tile = false, chevron = false)
          p.items.forEachIndexed { i, s ->
            if (i > 0) V3Divider(start = 64.dp)
            val extra = listOfNotNull(
              s.shortageMinor.takeIf { it > 0 }?.let { StrOnline.dlShortage(StrOnline.afn(it)) + " — " + s.reason },
              s.excessMinor.takeIf { it > 0 }?.let { StrOnline.dlExcess(StrOnline.afn(it)) + " — " + s.reason },
              s.collectionDiffMinor.takeIf { it != 0L }?.let { StrOnline.dlCustomerDiff + " " + StrOnline.afn(it) },
            )
            V3ListRow(
              StrOnline.dlSettleNo(s.settleNo),
              subtitle = (listOf(StrOnline.dlSettleLine(s.assignmentsCount, StrOnline.afn(s.handoverDueMinor), StrOnline.afn(s.receivedMinor))) + extra).joinToString("\n"),
              subtitleLines = 4,
              icon = Icons.Outlined.ReceiptLong,
              hue = V3Hue.VIOLET,
              value = OrdersModel.stamp(s.createdAt),
              chevron = false,
            )
          }
        }
      }
    }
  }
}

private const val RECENT = 30
