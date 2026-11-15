package ir.vil3ntec.tohid.ui.screens

import ir.vil3ntec.tohid.core.i18n.StrDebtor
import ir.vil3ntec.tohid.data.invoiceLabel
import ir.vil3ntec.tohid.data.isOnlineOrder
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.data.CustomerCrm
import ir.vil3ntec.tohid.data.CustomerEngine
import ir.vil3ntec.tohid.data.CustomerProfileForm
import ir.vil3ntec.tohid.data.DebtBook
import ir.vil3ntec.tohid.data.DebtEntry
import ir.vil3ntec.tohid.data.DebtorEngine
import ir.vil3ntec.tohid.data.ReportCsv
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopPolicy
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.Xlsx
import ir.vil3ntec.tohid.formatDate
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.ui.Platform
import ir.vil3ntec.tohid.ui.components.common.ScreenKind
import ir.vil3ntec.tohid.ui.components.common.ViewMode
import ir.vil3ntec.tohid.ui.components.common.ViewModeToggle
import ir.vil3ntec.tohid.ui.components.common.rememberViewMode
import ir.vil3ntec.tohid.ui.theme.Shop
import ir.vil3ntec.tohid.ui.theme.Shape
import ir.vil3ntec.tohid.ui.theme.softCard
import ir.vil3ntec.tohid.ui.theme.tabular
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.NorthEast
import androidx.compose.material.icons.outlined.PeopleOutline
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.SouthWest
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import ir.vil3ntec.tohid.core.i18n.StrDeskData
import ir.vil3ntec.tohid.plain
import ir.vil3ntec.tohid.ui.desk.Desk
import ir.vil3ntec.tohid.ui.desk.DeskButton
import ir.vil3ntec.tohid.ui.desk.DeskButtonKind
import ir.vil3ntec.tohid.ui.desk.DeskCalmRow
import ir.vil3ntec.tohid.ui.desk.DeskCell
import ir.vil3ntec.tohid.ui.desk.DeskColumn
import ir.vil3ntec.tohid.ui.desk.DeskDivider
import ir.vil3ntec.tohid.ui.desk.DeskDropdown
import ir.vil3ntec.tohid.ui.desk.DeskGrid
import ir.vil3ntec.tohid.ui.desk.DeskIconButton
import ir.vil3ntec.tohid.ui.desk.DeskInfoRow
import ir.vil3ntec.tohid.ui.desk.DeskKpiRow
import ir.vil3ntec.tohid.ui.desk.DeskLetterTile
import ir.vil3ntec.tohid.ui.desk.DeskLink
import ir.vil3ntec.tohid.ui.desk.DeskNote
import ir.vil3ntec.tohid.ui.desk.DeskOption
import ir.vil3ntec.tohid.ui.desk.DeskPage
import ir.vil3ntec.tohid.ui.desk.DeskPane
import ir.vil3ntec.tohid.ui.desk.DeskPaneHint
import ir.vil3ntec.tohid.ui.desk.DeskRadius
import ir.vil3ntec.tohid.ui.desk.DeskSearchField
import ir.vil3ntec.tohid.ui.desk.DeskSegmented
import ir.vil3ntec.tohid.ui.desk.DeskSize
import ir.vil3ntec.tohid.ui.desk.DeskSkeleton
import ir.vil3ntec.tohid.ui.desk.DeskSpace
import ir.vil3ntec.tohid.ui.desk.DeskStatBox
import ir.vil3ntec.tohid.ui.desk.DeskTextField
import ir.vil3ntec.tohid.ui.desk.DeskToolbar
import ir.vil3ntec.tohid.ui.desk.DeskType
import ir.vil3ntec.tohid.ui.desk.deskCard
import ir.vil3ntec.tohid.ui.desk.deskClickable
import ir.vil3ntec.tohid.ui.desk.rememberDeskLoad
import ir.vil3ntec.tohid.ui.theme.PhoneV3Debt
import ir.vil3ntec.tohid.ui.v3.RowWhen
import ir.vil3ntec.tohid.ui.v3.V3EmptyState

/*
 *  تکه‌های مشترکِ «مشتری‌ها» ِ کامپیوتر (`DeskCustomers.kt`) — اقلامِ فاکتورِ کنارِ جدول (`InvoicePeek`) و جدول/کارتِ عددِ نسخهٔ ۲ ِ
 *  پیشین (`DeskTable`، `TCol`، `DeskKpi`، `stateColor`) که برای صفحه‌های دیگر می‌مانند؛ جدا شدند تا پرونده از سقفِ اندازه نگذرد.
 */

@Composable
internal fun DeskKpi(label: String, value: String, tint: Color, modifier: Modifier = Modifier) {
  //  نسخهٔ ۲: کارتِ نرم (سایه، گوشهٔ ۱۶)، نقطهٔ رنگیِ معنا کنارِ عنوان و عددِ درشتِ هم‌پهنا
  Column(
    modifier
      .softCard(Shop.colors, Shape.card)
      .padding(16.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Box(Modifier.size(8.dp).clip(CircleShape).background(tint))
      Spacer(Modifier.width(8.dp))
      Text(label, style = MaterialTheme.typography.labelLarge, color = Shop.colors.muted, maxLines = 1)
    }
    Spacer(Modifier.height(8.dp))
    Text(value, style = MaterialTheme.typography.headlineSmall.tabular(), fontWeight = FontWeight.Bold, color = tint, maxLines = 1)
  }
}

@Composable
internal fun stateColor(balance: Double) = when {
  balance > 0.5 -> Shop.colors.danger
  balance < -0.5 -> Shop.colors.success
  else -> Shop.colors.muted
}

/** اقلامِ یک فاکتور کنارِ جدول، با دکمهٔ «نمایش و چاپ» که فاکتور را جدا باز می‌کند */
@Composable
internal fun InvoicePeek(d: ShopData, sale: Sale, modifier: Modifier, onShow: () -> Unit) {
  val lines = remember(d, sale.id) { d.saleItems.filter { it.saleId == sale.id } }
  val c = Desk.colors
  val debt = PhoneV3Debt.colors
  DeskPane(
    title = ir.vil3ntec.tohid.core.i18n.StrDebtPages.invoiceNo(sale.invoiceLabel() ?: "—"),
    subtitle = listOf(formatDate(sale.date), sale.seller.takeIf { it.isNotBlank() }?.let { ir.vil3ntec.tohid.core.i18n.StrCustomerPages.sellerIs(it) }).filterNotNull().joinToString(" • "),
    modifier = modifier,
    scroll = false,
    actions = { DeskButton(ir.vil3ntec.tohid.core.i18n.StrCustomerPages.showPrint, onShow, Modifier.weight(1f), kind = DeskButtonKind.PRIMARY, icon = Icons.Outlined.Print) },
  ) {
    LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
      itemsIndexed(lines, key = { _, l -> l.id }) { _, l ->
        Row(Modifier.fillMaxWidth().padding(vertical = DeskSpace.x2), verticalAlignment = Alignment.CenterVertically) {
          Column(Modifier.weight(1f)) {
            Text(SalesEngine.itemName(d, l), maxLines = 1, overflow = TextOverflow.Ellipsis, style = DeskType.body, color = c.ink)
            Text(qtyText(l.quantity) + " × " + money(l.unitPrice) + if (l.returnedQty > 0) ir.vil3ntec.tohid.core.i18n.StrCustomerPages.returnedQty(qtyText(l.returnedQty)) else "", style = DeskType.secondary, color = c.inkSecondary, maxLines = 1)
          }
          Text(money(l.totalPrice), style = DeskType.table, color = c.ink)
        }
        DeskDivider()
      }
    }
    DeskInfoRow(ir.vil3ntec.tohid.core.i18n.StrInvoices.total, money(sale.total))
    if (sale.discount > 0) DeskInfoRow(ir.vil3ntec.tohid.core.i18n.Str.discount, money(sale.discount))
    DeskInfoRow(ir.vil3ntec.tohid.core.i18n.StrCustomerPages.payable, "${money(sale.finalTotal)} ؋", strong = true)
    DeskInfoRow(ir.vil3ntec.tohid.core.i18n.StrInvoices.colPaid, money(sale.paidAmount), valueColor = debt.settled)
    if (sale.remaining > 0.5) DeskInfoRow(ir.vil3ntec.tohid.core.i18n.StrCustomerPages.remainingOnAccount, money(sale.remaining), strong = true, valueColor = debt.owe)
    if (sale.status == "cancelled") Text(ir.vil3ntec.tohid.core.i18n.StrCustomerPages.cancelledWhy(sale.cancelReason.ifBlank { ir.vil3ntec.tohid.core.i18n.StrShrink.noReason }), color = c.negative, style = DeskType.secondary)
  }
}

/* ══════════════════════════ جدولِ اکسلی ══════════════════════════ */

/** ستون — پهنای ۰ یعنی «باقی‌مانده»؛ `end` ⇒ عدد، راست‌چینِ انتها */
internal data class TCol(val title: String, val width: Dp = 0.dp, val end: Boolean = false)
internal data class TCell(val text: String, val color: Color? = null, val bold: Boolean = false, val link: Boolean = false)

/**
 *  جدولِ مدیریت: سرستون، ردیف‌های یکی‌درمیان، ردیفِ جمع در پایین. کلیک روی ردیف ⇐ `onClick`.
 *
 *  نسخهٔ ۲ (نقدِ ۱۴۰۵/۰۷/۲۷: «همه‌جا خطِ تیره»): خطِ عمودی و افقیِ خانه‌ها برداشته
 *  شد؛ ردیف‌ها را زمینهٔ یکی‌درمیان و ارتفاعِ راحت جدا می‌کند و کلِ جدول یک کارتِ نرم است.
 */
@Composable
internal fun DeskTable(
  cols: List<TCol>,
  count: Int,
  modifier: Modifier,
  empty: String,
  cell: @Composable (Int, Int) -> TCell,
  onClick: (Int) -> Unit = {},
  footer: List<String>? = null,
  selected: Int? = null,
) {
  @Composable fun RowScope.box(c: TCol, content: @Composable () -> Unit) {
    Box(
      (if (c.width == 0.dp) Modifier.weight(1f) else Modifier.width(c.width))
        .fillMaxHeight()
        .padding(horizontal = 12.dp, vertical = 9.dp),
      contentAlignment = if (c.end) Alignment.CenterEnd else Alignment.CenterStart,
    ) { content() }
  }
  Column(modifier.fillMaxWidth().softCard(Shop.colors, Shape.card)) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).background(Shop.colors.surface2)) {
      cols.forEach { c -> box(c) { Text(c.title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = Shop.colors.muted, maxLines = 1) } }
    }
    LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
      if (count == 0) item {
        Text(empty, Modifier.fillMaxWidth().padding(28.dp), color = Shop.colors.muted, textAlign = TextAlign.Center)
      }
      items(count) { i ->
        val bg = when {
          i == selected -> Shop.colors.primaryTint
          i % 2 == 1 -> Shop.colors.surface2
          else -> Shop.colors.surface
        }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).background(bg).clickable { onClick(i) }) {
          cols.forEachIndexed { ci, c ->
            val v = cell(i, ci)
            box(c) {
              //  ستونِ اصلی (پهنای ۰ = باقیِ جا، معمولاً نام) تا دو خط می‌شکند، بریده نمی‌شود
              Text(
                v.text, maxLines = if (c.width == 0.dp) 2 else 1, overflow = TextOverflow.Ellipsis,
                style = if (c.end) MaterialTheme.typography.bodyMedium.tabular() else MaterialTheme.typography.bodyMedium,
                fontWeight = if (v.bold) FontWeight.SemiBold else null,
                color = v.color ?: Shop.colors.text,
                textDecoration = if (v.link) androidx.compose.ui.text.style.TextDecoration.Underline else null,
              )
            }
          }
        }
      }
    }
    footer?.let { f ->
      Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).background(Shop.colors.surface2)) {
        cols.forEachIndexed { ci, c -> box(c) { Text(f.getOrElse(ci) { "" }, style = MaterialTheme.typography.bodyMedium.tabular(), fontWeight = FontWeight.Bold, color = Shop.colors.text, maxLines = 1) } }
      }
    }
  }
}

@Composable
internal fun HeadRow(cols: List<Pair<String, Dp>>) {
  Row(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)).background(Shop.colors.surface2).padding(horizontal = 8.dp, vertical = 7.dp),
  ) {
    cols.forEachIndexed { i, (t, w) ->
      Text(
        t, if (w == 0.dp) Modifier.weight(1f) else Modifier.width(w),
        style = MaterialTheme.typography.labelMedium, color = Shop.colors.muted,
        textAlign = if (i > 0 && w != 0.dp) TextAlign.End else TextAlign.Start,
      )
    }
  }
}

