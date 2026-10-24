package ir.vil3ntec.tohid.ui.v3.products

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.MoveToInbox
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.SyncAlt
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrProducts
import ir.vil3ntec.tohid.data.AuditEntry
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.StockMovement
import ir.vil3ntec.tohid.formatDate
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.qty
import ir.vil3ntec.tohid.ui.screens.ProductListModel.Status
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3Space
import ir.vil3ntec.tohid.ui.theme.V3Status
import ir.vil3ntec.tohid.ui.v3.V3Card
import ir.vil3ntec.tohid.ui.v3.V3Chevron
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.V3Hue
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Section
import ir.vil3ntec.tohid.ui.v3.V3StatusPill
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.products.ProductDetailModel.MoveKind
import ir.vil3ntec.tohid.ui.v3.rememberCountUp
import ir.vil3ntec.tohid.ui.v3.v3Pressable

private fun unitOf(p: Product) = if (p.unit.isNotBlank()) " ${p.unit}" else ""

private fun statusPill(s: Status): Pair<V3Status, String> = when (s) {
  Status.OUT -> V3Status.CANCELLED to StrProducts.statusOut
  Status.LOW -> V3Status.AWAITING to StrProducts.statusLow
  Status.INACTIVE -> V3Status.INACTIVE to StrProducts.statusInactive
  Status.ACTIVE -> V3Status.DELIVERED to StrProducts.statusOk
}

/** سرِ صفحه: عکسِ مربعِ ۲۴۰ (زدن ⇐ عکسِ کالا)، «دسته · واحد» و قرصِ وضعیت */
@Composable
internal fun ProductHero(p: Product, status: Status, onPhoto: () -> Unit) {
  val c = PhoneV3.colors
  Column(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
    DraftProductPhoto(
      p.id, p.name,
      Modifier.widthIn(max = 240.dp).fillMaxWidth(0.62f).aspectRatio(1f).v3Pressable(label = StrProducts.productPhoto, onClick = onPhoto),
      radius = V3Space.featuredRadius,
      force = true,
    )
    Spacer(Modifier.height(12.dp))
    val line = listOf(p.category, p.unit).filter { it.isNotBlank() }.joinToString(" · ")
    if (line.isNotEmpty()) Text(line, style = V3Type.subhead, color = c.inkMuted, textAlign = TextAlign.Center)
    Spacer(Modifier.height(6.dp))
    val (s, label) = statusPill(status)
    V3StatusPill(s, label)
  }
}

/** کارتِ عددها: موجودیِ فعلی (درشت، زدن ⇐ انبار) و زیرش قیمتِ فروش | قیمتِ خرید */
@Composable
internal fun FiguresCard(p: Product, f: ProductDetailModel.Figures, onStock: () -> Unit) {
  val c = PhoneV3.colors
  val stock = rememberCountUp(f.stock)
  val sale = rememberCountUp(p.salePrice)
  val buy = rememberCountUp(p.purchasePrice)
  Column(Modifier.padding(top = 16.dp)) {
    V3Card(radius = V3Space.featuredRadius, padding = PaddingValues(0.dp)) {
      Row(
        Modifier.fillMaxWidth().v3Pressable(label = StrProducts.stockNow, onClick = onStock).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(Modifier.weight(1f)) {
          Text(StrProducts.stockNow, style = V3Type.footnote, color = c.inkMuted)
          Text(qty(stock) + unitOf(p), style = V3Type.largeTitle, color = if (f.status == Status.OUT) PhoneV3Roles.errorText(c) else c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        V3Chevron()
      }
      V3Divider()
      Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        MoneyCell(StrProducts.salePrice, money(sale), Modifier.weight(1f))
        Spacer(Modifier.width(1.dp).fillMaxHeight().background(c.hairline))
        MoneyCell(StrProducts.purchasePrice, money(buy), Modifier.weight(1f))
      }
    }
  }
}

@Composable
private fun MoneyCell(label: String, value: String, modifier: Modifier) {
  val c = PhoneV3.colors
  Column(modifier.padding(16.dp)) {
    Text(label, style = V3Type.footnote, color = c.inkMuted, maxLines = 1)
    Text("$value ؋", style = V3Type.figure, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
  }
}

/** سودِ هر واحد، سودِ موجودی، حداقلِ موجودی، فروخته‌شده و سودِ کسب‌شده */
@Composable
internal fun ProfitCard(p: Product, f: ProductDetailModel.Figures) {
  val c = PhoneV3.colors
  fun tone(v: Double) = if (v < 0) PhoneV3Roles.errorText(c) else PhoneV3Roles.brandText(c)
  V3Section(null) {
    Line(StrProducts.unitProfit(p.unit.ifBlank { StrProducts.unitWord }), "${money(f.unitProfit)} ؋", tone(f.unitProfit))
    V3Divider(start = 16.dp)
    Line(StrProducts.stockProfit, "${money(f.stockProfit)} ؋", tone(f.stockProfit))
    V3Divider(start = 16.dp)
    Line(StrProducts.minStock, qty(p.minStock) + unitOf(p), null)
    V3Divider(start = 16.dp)
    Line(StrProducts.sold, qty(f.sold) + unitOf(p), null, StrProducts.soldCaption)
    V3Divider(start = 16.dp)
    Line(StrProducts.earned, "${money(f.earned)} ؋", tone(f.earned), StrProducts.earnedCaption)
  }
}

@Composable
private fun Line(title: String, value: String, color: androidx.compose.ui.graphics.Color?, caption: String? = null) {
  val c = PhoneV3.colors
  V3ListRow(
    title = title,
    subtitle = caption,
    tile = false,
    chevron = false,
    minHeight = 48.dp,
    trailing = { Text(value, style = V3Type.callout.copy(fontWeight = FontWeight.Bold), color = color ?: c.ink, maxLines = 1) },
  )
}

private fun moveLabel(k: MoveKind, raw: String): String = when (k) {
  MoveKind.PURCHASE_IN -> StrProducts.moveIn
  MoveKind.SALE -> StrProducts.moveSale
  MoveKind.CUSTOMER_RETURN -> StrProducts.moveReturn
  MoveKind.SALE_CANCEL -> StrProducts.moveCancel
  MoveKind.SUPPLIER_RETURN -> StrProducts.moveSupplierReturn
  MoveKind.ADJUSTMENT -> StrProducts.moveAdjust
  MoveKind.OTHER -> raw
}

private fun moveIcon(k: MoveKind): Pair<ImageVector, V3Hue> = when (k) {
  MoveKind.PURCHASE_IN -> Icons.Outlined.MoveToInbox to V3Hue.BRAND
  MoveKind.SALE -> Icons.Outlined.ShoppingBag to V3Hue.BLUE
  MoveKind.CUSTOMER_RETURN -> Icons.Outlined.Replay to V3Hue.TEAL
  MoveKind.SALE_CANCEL -> Icons.Outlined.Block to V3Hue.ORANGE
  MoveKind.SUPPLIER_RETURN -> Icons.Outlined.LocalShipping to V3Hue.PURPLE
  MoveKind.ADJUSTMENT -> Icons.Outlined.Tune to V3Hue.ORANGE
  MoveKind.OTHER -> Icons.Outlined.SyncAlt to V3Hue.TEAL
}

/** «حرکت‌های اخیر»: پنج ردیف (گونه، تاریخ، مقدارِ علامت‌دار با واحد) و «همه» ⇐ گردشِ انبار */
@Composable
internal fun MovesSection(p: Product, moves: List<StockMovement>, total: Int, onAll: () -> Unit) {
  val c = PhoneV3.colors
  V3Section(StrProducts.recentMoves, action = if (total > 0) Str.all else null, onAction = onAll) {
    if (moves.isEmpty()) {
      V3EmptyState(Icons.Outlined.SwapVert, StrProducts.movesEmpty, body = StrProducts.movesEmptyBody)
    } else moves.forEachIndexed { i, m ->
      if (i > 0) V3Divider(start = 64.dp)
      val kind = ProductDetailModel.kindOf(m.type)
      val (icon, hue) = moveIcon(kind)
      val sign = if (m.qty > 0) "+" else if (m.qty < 0) "−" else ""
      V3ListRow(
        title = moveLabel(kind, m.type),
        subtitle = formatDate(m.date) + if (m.notes.isNotBlank()) " — ${m.notes}" else "",
        subtitleLines = 1,
        icon = icon,
        hue = hue,
        chevron = false,
        trailing = {
          Text(
            sign + qty(kotlin.math.abs(m.qty)) + unitOf(p),
            style = V3Type.callout.copy(fontWeight = FontWeight.Bold),
            color = if (m.qty > 0) PhoneV3Roles.brandText(c) else c.ink,
            maxLines = 1,
          )
        },
      )
    }
  }
}

/** «تاریخچهٔ تغییرات» — ویرایش‌ها و تغییرِ قیمتِ خرید (`WarehouseEngine.productHistory`) */
@Composable
internal fun HistorySection(history: List<AuditEntry>) {
  V3Section(StrProducts.history) {
    history.forEachIndexed { i, a ->
      if (i > 0) V3Divider(start = 16.dp)
      V3ListRow(title = formatDate(a.date), subtitle = a.notes, subtitleLines = 3, tile = false, chevron = false)
    }
  }
}
