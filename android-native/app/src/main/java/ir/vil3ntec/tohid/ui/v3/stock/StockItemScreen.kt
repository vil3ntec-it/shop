package ir.vil3ntec.tohid.ui.v3.stock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.MoveToInbox
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.StrStock
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.WarehouseEngine
import ir.vil3ntec.tohid.formatDate
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.qty
import ir.vil3ntec.tohid.ui.screens.Haptics
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Space
import ir.vil3ntec.tohid.ui.theme.V3Status
import ir.vil3ntec.tohid.ui.v3.V3Action
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Card
import ir.vil3ntec.tohid.ui.v3.V3ConfirmSheet
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.V3Hue
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3MenuItem
import ir.vil3ntec.tohid.ui.v3.V3Scaffold
import ir.vil3ntec.tohid.ui.v3.V3Section
import ir.vil3ntec.tohid.ui.v3.V3StatusPill
import ir.vil3ntec.tohid.ui.v3.V3SwipeAction
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.rememberCountUp
import ir.vil3ntec.tohid.ui.v3.rememberV3Stagger
import ir.vil3ntec.tohid.ui.v3.v3Stagger
import ir.vil3ntec.tohid.ui.v3.V3Collapse
import ir.vil3ntec.tohid.ui.v3.v3Pressable

/** کارهای صفحهٔ یک کالا — همه مسیرند، جز حذفِ محصول که پس از تأییدِ همین صفحه می‌آید */
@Immutable
class StockItemActions(
  val edit: (productId: String) -> Unit,
  val view: (productId: String) -> Unit,
  val deleteProduct: (productId: String) -> Unit,
  val entry: (productId: String) -> Unit,
  val adjust: (productId: String) -> Unit,
  val deleteEntry: (entryId: String) -> Unit,
  val movements: (productId: String) -> Unit,
  val copied: () -> Unit,
)

/**
 *  `stock/{id}`: کارتِ عددها (موجودی درشت با قرصِ وضعیت، قیمت‌ها، حدِ کم بودن، بارکدِ
 *  کپی‌شدنی)، دو کارِ اصلی کنارِ هم، ورودهای این کالا (شش‌تا و بازشدنِ درجا؛ کشیدن ⇐
 *  حذف با تأییدِ موتور) و ردیفِ «حرکات انبار». `onBack` null ⇒ ستونِ کنارِ کامپیوتر.
 */
@Composable
fun StockItemScreen(
  data: ShopData,
  today: String,
  productId: String,
  actions: StockItemActions,
  onBack: (() -> Unit)?,
  modifier: Modifier = Modifier,
) {
  val item = remember(data, productId, today) { StockItemModel.of(data, productId, today) }
  if (item == null) {
    V3Scaffold(title = StrStock.productMissing, modifier = modifier, onBack = onBack, largeTitle = false) {
      V3EmptyState(Icons.Outlined.Inventory2, StrStock.productMissing, body = StrStock.productMissingBody, action = onBack?.let { StrKit.back }, onAction = onBack)
    }
    return
  }
  val p = item.product
  var confirmDelete by remember { mutableStateOf(false) }
  var expanded by rememberSaveable(productId) { mutableStateOf(false) }
  val stagger = rememberV3Stagger(productId)

  V3Scaffold(
    title = p.name,
    modifier = modifier,
    onBack = onBack,
    actions = listOf(V3Action(Icons.Outlined.Edit, StrStock.edit) { actions.edit(p.id) }),
    overflow = listOf(
      V3MenuItem(Icons.Outlined.Visibility, StrStock.viewProduct) { actions.view(p.id) },
      V3MenuItem(Icons.Outlined.DeleteOutline, StrStock.deleteProduct, danger = true) { confirmDelete = true },
    ),
  ) { pad ->
    LazyColumn(
      Modifier.fillMaxSize(),
      contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = pad.calculateBottomPadding()),
    ) {
      item(key = "figures") { Box(Modifier.v3Stagger(stagger, "figures", 0)) { FiguresCard(item, actions.copied) } }
      item(key = "actions") {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp).v3Stagger(stagger, "actions", 1), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          V3Button(StrStock.entryAction, { actions.entry(p.id) }, Modifier.weight(1f), icon = Icons.Outlined.MoveToInbox, haptic = true)
          V3Button(StrStock.adjustAction, { actions.adjust(p.id) }, Modifier.weight(1f), kind = V3ButtonKind.SECONDARY, icon = Icons.Outlined.Tune)
        }
      }
      item(key = "entries") {
        V3Section(StrStock.entriesTitle, modifier = Modifier.v3Stagger(stagger, "entries", 2)) {
          if (item.entries.isEmpty()) {
            V3ListRow(StrStock.noEntries, icon = Icons.Outlined.MoveToInbox, chevron = false)
          }
          StockItemModel.shown(item.entries, false).forEachIndexed { i, e ->
            if (i > 0) V3Divider(start = 64.dp)
            EntryRow(e, onDelete = { actions.deleteEntry(e.entry.id) })
          }
          //  ورودهای قدیمی‌تر درجا باز می‌شوند (`V3Collapse`، فنرِ کیت)
          V3Collapse(expanded) {
            Column {
              item.entries.drop(StockItemModel.PREVIEW).forEach { e ->
                V3Divider(start = 64.dp)
                EntryRow(e, onDelete = { actions.deleteEntry(e.entry.id) })
              }
            }
          }
          val hidden = StockItemModel.hiddenCount(item.entries, expanded)
          if (hidden > 0 || expanded && item.entries.size > StockItemModel.PREVIEW) {
            V3Divider(start = 64.dp)
            V3ListRow(
              title = if (hidden > 0) StrStock.olderEntries(money(hidden.toDouble())) else StrStock.showFewer,
              icon = if (hidden > 0) Icons.Outlined.ExpandMore else Icons.Outlined.ExpandLess,
              chevron = false,
              onClick = { expanded = !expanded },
            )
          }
        }
      }
      item(key = "movements") {
        V3Section(null, modifier = Modifier.v3Stagger(stagger, "movements", 3)) {
          V3ListRow(StrStock.movementsRow, icon = Icons.Outlined.SwapVert, hue = V3Hue.BLUE, onClick = { actions.movements(p.id) })
        }
        Spacer(Modifier.height(16.dp))
      }
    }
  }
  if (confirmDelete) {
    V3ConfirmSheet(
      title = StrStock.deleteProductTitle,
      message = WarehouseEngine.deleteWarning(data, p.id),
      confirm = StrStock.delete,
      icon = Icons.Outlined.DeleteOutline,
      onConfirm = { actions.deleteProduct(p.id) },
      onDismiss = { confirmDelete = false },
    )
  }
}

@Composable
private fun FiguresCard(item: StockItemUi, onCopied: () -> Unit) {
  val c = PhoneV3.colors
  val p = item.product
  val shown = rememberCountUp(item.stock)
  V3Card(radius = V3Space.featuredRadius, padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Text(StrStock.stock, style = V3Type.footnote, color = c.inkMuted)
        Text(
          listOf(qty(shown), p.unit).filter { it.isNotBlank() }.joinToString(" "),
          style = V3Type.largeTitle,
          color = c.ink,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      when (item.level) {
        StockLevel.OUT -> V3StatusPill(V3Status.CANCELLED, StrStock.pillOut)
        StockLevel.LOW -> V3StatusPill(V3Status.AWAITING, StrStock.pillLow)
        StockLevel.OK -> Unit
      }
    }
    Spacer(Modifier.height(10.dp))
    Box(Modifier.fillMaxWidth().height(1.dp).background(c.hairline))
    FigureRow(StrStock.purchasePrice, "${money(p.purchasePrice)} ؋")
    FigureRow(StrStock.salePriceLabel, "${money(p.salePrice)} ؋")
    FigureRow(StrStock.minStock, listOf(qty(p.minStock), p.unit).filter { it.isNotBlank() }.joinToString(" "))
    BarcodeRow(p.barcodes, onCopied)
  }
}

@Composable
private fun FigureRow(label: String, value: String) {
  val c = PhoneV3.colors
  Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
    Text(label, style = V3Type.callout, color = c.inkMuted, modifier = Modifier.weight(1f))
    Text(value, style = V3Type.body, color = c.ink, maxLines = 1)
  }
}

@Composable
private fun BarcodeRow(codes: List<String>, onCopied: () -> Unit) {
  val c = PhoneV3.colors
  val clipboard = LocalClipboardManager.current
  val context = LocalContext.current
  val code = codes.joinToString("، ")
  if (code.isBlank()) {
    FigureRow(StrStock.barcode, StrStock.noBarcode)
    return
  }
  Row(
    Modifier
      .fillMaxWidth()
      .v3Pressable(label = StrStock.copyBarcode) {
        clipboard.setText(AnnotatedString(codes.first()))
        Haptics.confirm(context)
        onCopied()
      }
      .padding(vertical = 9.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(StrStock.barcode, style = V3Type.callout, color = c.inkMuted, modifier = Modifier.weight(1f))
    Text(code, style = V3Type.body, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1.4f, fill = false))
    Spacer(Modifier.size(8.dp))
    Icon(Icons.Outlined.ContentCopy, contentDescription = StrStock.copyBarcode, tint = c.icon, modifier = Modifier.size(18.dp))
  }
}

/** یک ورود: «۵ کارتن × ۱۲ · ۶۰ عدد»، تاریخ و انقضا/سری، قیمت؛ کشیدن ⇐ حذف */
@Composable
private fun EntryRow(e: EntryUi, onDelete: () -> Unit) {
  val entry = e.entry
  val units = listOf(qty(kotlin.math.abs(entry.units)), entry.unit).filter { it.isNotBlank() }.joinToString(" ")
  val title = when {
    entry.isAdjustment -> "${StrStock.adjustmentEntry} ${if (entry.units >= 0) "+" else "−"}$units"
    entry.cartons > 0 -> "${StrStock.entryCartons(qty(entry.cartons), qty(entry.perCarton))} · $units"
    else -> units
  }
  val subtitle = listOfNotNull(
    formatDate(entry.date),
    entry.expiryDate.takeIf { it.isNotBlank() }?.let { StrStock.expiryOn(formatDate(it)) },
    e.lot?.let { StrStock.lotLeft(qty(it.remaining)) },
    entry.batch.takeIf { it.isNotBlank() }?.let { StrStock.batchOf(it) },
    entry.notes.takeIf { it.isNotBlank() && !entry.isAdjustment },
  ).joinToString(" · ")
  V3ListRow(
    title = title,
    subtitle = if (entry.isAdjustment && entry.notes.isNotBlank()) "$subtitle · ${entry.notes}" else subtitle,
    icon = if (entry.isAdjustment) Icons.Outlined.Tune else Icons.Outlined.MoveToInbox,
    hue = if (entry.isAdjustment) null else V3Hue.BRAND,
    value = if (entry.isAdjustment) null else "${money(entry.price)} ؋",
    chevron = false,
    endActions = listOf(V3SwipeAction(Icons.Outlined.DeleteOutline, StrStock.delete, V3ButtonKind.DANGER, onDelete)),
  )
}
