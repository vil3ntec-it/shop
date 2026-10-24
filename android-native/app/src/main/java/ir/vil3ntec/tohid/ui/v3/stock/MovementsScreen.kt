package ir.vil3ntec.tohid.ui.v3.stock

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.AssignmentReturn
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.MoveToInbox
import androidx.compose.material.icons.outlined.PointOfSale
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.StrStock
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.formatDate
import ir.vil3ntec.tohid.qty
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3Status
import ir.vil3ntec.tohid.ui.v3.V3Card
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.V3Hue
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Scaffold
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.rememberV3Stagger
import ir.vil3ntec.tohid.ui.v3.v3Stagger

internal fun moveLabel(k: MoveKind, type: String): String = when (k) {
  MoveKind.PURCHASE -> StrStock.movePurchase
  MoveKind.SALE -> StrStock.moveSale
  MoveKind.CUSTOMER_RETURN -> StrStock.moveCustomerReturn
  MoveKind.SALE_CANCEL -> StrStock.moveSaleCancel
  MoveKind.SUPPLIER_RETURN -> StrStock.moveSupplierReturn
  MoveKind.ADJUSTMENT -> StrStock.moveAdjustment
  MoveKind.OTHER -> type
}

private fun moveIcon(k: MoveKind): Pair<ImageVector, V3Hue?> = when (k) {
  MoveKind.PURCHASE -> Icons.Outlined.MoveToInbox to V3Hue.BRAND
  MoveKind.SALE -> Icons.Outlined.PointOfSale to V3Hue.BLUE
  MoveKind.CUSTOMER_RETURN -> Icons.AutoMirrored.Outlined.AssignmentReturn to V3Hue.TEAL
  MoveKind.SALE_CANCEL -> Icons.AutoMirrored.Outlined.Undo to V3Hue.ORANGE
  MoveKind.SUPPLIER_RETURN -> Icons.Outlined.LocalShipping to V3Hue.PURPLE
  MoveKind.ADJUSTMENT -> Icons.Outlined.Tune to null
  MoveKind.OTHER -> Icons.Outlined.SwapVert to null
}

/**
 *  `stock/{id}/movements` — «حرکات انبار»: سرخطِ چسبانِ هر روز (امروز، دیروز، تاریخ)، و هر
 *  حرکت با آیکنِ گونه، برچسب، یادداشت/شمارهٔ فاکتور و مقدارِ علامت‌دار. حرکتی که مقصد دارد
 *  (فاکتور، تأمین‌کننده) زدنی است و همان صفحه را باز می‌کند.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MovementsScreen(
  data: ShopData,
  productId: String,
  today: String,
  onOpen: (String) -> Unit,
  onBack: () -> Unit,
) {
  val c = PhoneV3.colors
  val product = data.products.find { it.id == productId }
  val days = remember(data, productId) { MovementsModel.days(data, productId) }
  val yesterday = remember(today) { MovementsModel.yesterday(today) }
  val stagger = rememberV3Stagger(productId)
  V3Scaffold(title = StrStock.movementsTitle(product?.name.orEmpty()), onBack = onBack) { pad ->
    if (days.isEmpty()) {
      V3EmptyState(Icons.Outlined.SwapVert, StrStock.noMovements)
      return@V3Scaffold
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = pad.calculateBottomPadding())) {
      days.forEach { day ->
        stickyHeader(key = "d-" + day.date) {
          Text(
            when (day.date) {
              today -> Str.today
              yesterday -> StrKit.yesterday
              else -> formatDate(day.date)
            },
            style = V3Type.subhead,
            color = c.inkMuted,
            modifier = Modifier.fillMaxWidth().background(c.bg).padding(start = 6.dp, top = 14.dp, bottom = 8.dp).semantics { heading() },
          )
        }
        item(key = "c-" + day.date) {
          V3Card(Modifier.v3Stagger(stagger, day.date, days.indexOf(day)), padding = PaddingValues(vertical = 4.dp)) {
            day.moves.forEachIndexed { i, m ->
              if (i > 0) V3Divider(start = 64.dp)
              val (icon, hue) = moveIcon(m.kind)
              val positive = m.move.qty >= 0
              V3ListRow(
                title = moveLabel(m.kind, m.move.type),
                subtitle = m.move.notes.ifBlank { null },
                icon = icon,
                hue = hue,
                value = (if (positive) "+" else "−") + qty(kotlin.math.abs(m.move.qty)),
                valueColor = if (positive) PhoneV3Roles.status(c, V3Status.DELIVERED).fg else c.ink,
                chevron = m.link != null,
                onClick = m.link?.let { link -> { onOpen(link) } },
              )
            }
          }
        }
      }
      item(key = "end") { Spacer(Modifier.height(16.dp)) }
    }
  }
}
