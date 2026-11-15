package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrDeskWork
import ir.vil3ntec.tohid.core.i18n.StrGrid
import ir.vil3ntec.tohid.core.i18n.StrProductPages
import ir.vil3ntec.tohid.core.i18n.StrProducts
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.core.i18n.StrSettings
import ir.vil3ntec.tohid.data.ExpiryEngine
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.qty
import ir.vil3ntec.tohid.ui.components.common.Selection
import ir.vil3ntec.tohid.ui.components.pos.ProductThumb
import ir.vil3ntec.tohid.ui.desk.Desk
import ir.vil3ntec.tohid.ui.desk.DeskCheckbox
import ir.vil3ntec.tohid.ui.desk.DeskColumn
import ir.vil3ntec.tohid.ui.desk.DeskDivider
import ir.vil3ntec.tohid.ui.desk.DeskRadius
import ir.vil3ntec.tohid.ui.desk.DeskSize
import ir.vil3ntec.tohid.ui.desk.DeskSortColumn
import ir.vil3ntec.tohid.ui.desk.DeskSortHead
import ir.vil3ntec.tohid.ui.desk.DeskSpace
import ir.vil3ntec.tohid.ui.desk.DeskType
import ir.vil3ntec.tohid.ui.desk.deskCard
import ir.vil3ntec.tohid.ui.desk.deskClickable

/*
 *  جدول و کارت‌های «کالاها» ِ کامپیوتر (`DeskProductsScreen`، `claude/e-desk-d3a`) روی `ui/desk`: سرستون‌های مرتب‌شدنی،
 *  ستون‌ها به اندازهٔ پنجره (پهن ⇒ دسته، واحد، خرید و انقضا هم)، ردیفِ آرامِ ۴۸dp (نام + یک خطِ کم‌رنگ، وضعیت به‌شکلِ واژه
 *  نه قرصِ رنگی) و فهرستِ تنبل برای ۲۰ هزار کالا. زدنِ ردیف ⇐ پنلِ ویرایش؛ تیک ⇐ انتخابِ گروهی. فقط نمایش است.
 */

/** نشانِ انقضای یک کالا از محموله‌های نزدیک/گذشته (`ExpiryEngine.alerts`) — [daysLeft] کمترین؛ منفی ⇒ منقضی */
@Immutable
internal data class DeskExpiry(val daysLeft: Long) {
  val expired: Boolean get() = daysLeft < 0
  val label: String get() = if (expired) StrDeskWork.expired else StrDeskWork.daysLeft(money(daysLeft.coerceAtLeast(0).toDouble()))
}

/** کالا ⇐ نزدیک‌ترین انقضا — یک بار برای کلِ دفتر (نه برای هر ردیف) */
internal fun deskExpiryOf(d: ShopData, today: String): Map<String, DeskExpiry> =
  ExpiryEngine.alerts(d, today).groupBy { it.product.id }.mapValues { (_, lots) -> DeskExpiry(lots.minOf { it.daysLeft }) }

/** وضعیت به واژه (همان برچسب‌های قرصِ پیشین) */
internal fun deskStatusLabel(s: ProductListModel.Status): String = when (s) {
  ProductListModel.Status.ACTIVE -> StrSettings.active
  ProductListModel.Status.LOW -> Str.lowStock
  ProductListModel.Status.OUT -> Str.outOfStock
  ProductListModel.Status.INACTIVE -> StrProducts.filterInactive
}

/** ستون‌هایی که پهنا جا می‌دهد: پهن ⇒ همه؛ میانه ⇒ بی واحد و دسته (دسته زیرِ نام می‌آید)؛ باریک (پنلِ ویرایش باز) ⇒ کم‌ترین */
internal enum class DeskProductCols { WIDE, MEDIUM, NARROW;
  companion object {
    fun of(widthDp: Float): DeskProductCols = when {
      widthDp >= 1180f -> WIDE
      widthDp >= 860f -> MEDIUM
      else -> NARROW
    }
  }
}

private val W_CODE = 132.dp
private val W_CAT = 120.dp
private val W_UNIT = 64.dp
private val W_MONEY = 92.dp
private val W_STOCK = 104.dp
private val W_EXP = 84.dp
private val W_STATUS = 92.dp
private val W_CHECK = 28.dp
private const val W_NAME = 3f

/**
 *  جدولِ کالاها. [expiry] از [deskExpiryOf]؛ [selected] تیک‌خورده‌ها؛ [openId] ردیفِ بازِ پنل. سرستون ⇐ [onSort].
 */
@Composable
internal fun DeskProductsTable(
  rows: List<Product>,
  stockOf: (String) -> Double,
  expiry: Map<String, DeskExpiry>,
  selected: Set<String>,
  openId: String?,
  sortBy: ProductListModel.Sort,
  asc: Boolean,
  onSort: (ProductListModel.Sort) -> Unit,
  onToggle: (String) -> Unit,
  onToggleAll: () -> Unit,
  onOpen: (String) -> Unit,
  bottomSpace: Dp,
  photos: Boolean,
) {
  val c = Desk.colors
  val ids = remember(rows) { rows.map { it.id } }
  BoxWithConstraints(Modifier.fillMaxSize().deskCard(c).padding(DeskSpace.x2)) {
    val mode = DeskProductCols.of(maxWidth.value)
    //  پنجرهٔ خیلی پهن: سهمِ وزنی (عددها کنارِ نام پخش می‌شوند، نه همه ته جدول)
    val xw = mode == DeskProductCols.WIDE && maxWidth.value >= 1400f
    fun col(title: String, w: Dp, weight: Float, end: Boolean = false) = if (xw) DeskColumn(title, weight = weight, end = end) else DeskColumn(title, width = w, end = end)
    val head = buildList {
      add(DeskSortColumn(DeskColumn(StrGrid.name, weight = if (xw) W_NAME else 1f), ProductListModel.Sort.NAME))
      add(DeskSortColumn(col(StrDeskWork.colCode, W_CODE, 1.3f), ProductListModel.Sort.CODE))
      if (mode == DeskProductCols.WIDE) add(DeskSortColumn(col(StrDeskWork.colCategory, W_CAT, 1.1f), ProductListModel.Sort.CATEGORY))
      if (mode == DeskProductCols.WIDE) add(DeskSortColumn<ProductListModel.Sort>(col(StrDeskWork.colUnit, W_UNIT, 0.7f)))
      if (mode != DeskProductCols.NARROW) add(DeskSortColumn(col(StrDeskWork.colCost, W_MONEY, 0.9f, end = true), ProductListModel.Sort.BUY))
      add(DeskSortColumn(col(StrDeskWork.colPrice, W_MONEY, 0.9f, end = true), ProductListModel.Sort.SELL))
      add(DeskSortColumn(col(StrDeskWork.colStock, W_STOCK, 1f, end = true), ProductListModel.Sort.STOCK))
      if (mode != DeskProductCols.NARROW) add(DeskSortColumn<ProductListModel.Sort>(col(StrDeskWork.colExpiry, W_EXP, 0.8f)))
      add(DeskSortColumn(col(StrDeskWork.colStatus, W_STATUS, 0.9f), ProductListModel.Sort.STATUS))
    }
    fun RowScope.cell(w: Dp, weight: Float): Modifier = if (xw) Modifier.weight(weight) else Modifier.width(w)
    Column(Modifier.fillMaxSize()) {
      DeskSortHead(
        head, sortBy, asc, onSort,
        leading = {
          DeskCheckbox(
            state = when {
              Selection.allSelected(selected, ids) -> ToggleableState.On
              Selection.someSelected(selected, ids) -> ToggleableState.Indeterminate
              else -> ToggleableState.Off
            },
            label = StrProductPages.selectAllVisible,
            onClick = onToggleAll,
          )
        },
        leadingWidth = W_CHECK,
        sortLabel = StrDeskWork::sortBy,
      )
      LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(bottom = bottomSpace)) {
        items(rows, key = { it.id }) { p ->
          val stock = stockOf(p.id)
          val status = ProductListModel.status(p, stock)
          val open = p.id == openId
          val checked = p.id in selected
          Column {
            Row(
              Modifier
                .fillMaxWidth()
                .heightIn(min = DeskSize.tableRow + 4.dp)
                .deskClickable(label = StrProductPages.editItem + ": " + p.name, selected = open || checked, selectedFill = if (open) c.selected else c.surfaceAlt) { onOpen(p.id) }
                .semantics { this.selected = open }
                .padding(horizontal = DeskSpace.x3),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(DeskSpace.x3),
            ) {
              Box(Modifier.width(W_CHECK), contentAlignment = Alignment.Center) {
                DeskCheckbox(if (checked) ToggleableState.On else ToggleableState.Off, StrProductPages.selectName(p.name), { onToggle(p.id) })
              }
              Row(Modifier.weight(if (xw) W_NAME else 1f), verticalAlignment = Alignment.CenterVertically) {
                ProductThumb(p.id, p.name, 30.dp, photos, tintKey = p.category)
                Spacer(Modifier.width(DeskSpace.x3))
                Column(Modifier.weight(1f)) {
                  Text(p.name, style = DeskType.bodyStrong, color = if (p.active) c.ink else c.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                  //  یک خطِ کم‌رنگ: دسته (وقتی ستونش نیست) • تاریخِ ثبت
                  val meta = listOfNotNull(
                    if (mode != DeskProductCols.WIDE) p.category.ifBlank { StrSale.uncategorized } else null,
                    if (p.createdAt > 0) ir.vil3ntec.tohid.formatMillis(p.createdAt) else null,
                  ).joinToString(" • ")
                  if (meta.isNotEmpty()) Text(meta, style = DeskType.caption.copy(fontWeight = DeskType.secondary.fontWeight), color = c.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
              }
              Text(p.barcodes.firstOrNull().orEmpty().ifEmpty { "—" }, cell(W_CODE, 1.3f), style = DeskType.table, color = c.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
              if (mode == DeskProductCols.WIDE) {
                Text(p.category.ifBlank { StrSale.uncategorized }, cell(W_CAT, 1.1f), style = DeskType.table, color = c.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(p.unit.ifBlank { "—" }, cell(W_UNIT, 0.7f), style = DeskType.table, color = c.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
              }
              if (mode != DeskProductCols.NARROW) Text(money(p.purchasePrice), cell(W_MONEY, 0.9f), style = DeskType.table, color = c.inkSecondary, textAlign = TextAlign.End, maxLines = 1)
              Text(money(p.salePrice), cell(W_MONEY, 0.9f), style = DeskType.number, color = c.ink, textAlign = TextAlign.End, maxLines = 1)
              Text(
                if (mode == DeskProductCols.WIDE || p.unit.isBlank()) qty(stock) else "${qty(stock)} ${p.unit}",
                cell(W_STOCK, 1f), style = DeskType.table, color = c.ink, textAlign = TextAlign.End, maxLines = 1,
              )
              if (mode != DeskProductCols.NARROW) {
                val e = expiry[p.id]
                Text(e?.label ?: "—", cell(W_EXP, 0.8f), style = if (e?.expired == true) DeskType.label else DeskType.table, color = if (e != null) c.ink else c.inkMuted, maxLines = 1)
              }
              //  وضعیت واژهٔ آرام است؛ کم/تمام پررنگ‌تر (نه رنگی) تا چشم بی‌رنگ هم ببیند
              val loud = status == ProductListModel.Status.LOW || status == ProductListModel.Status.OUT
              Text(deskStatusLabel(status), cell(W_STATUS, 0.9f), style = if (loud) DeskType.label else DeskType.secondary, color = if (loud) c.ink else c.inkSecondary, maxLines = 1)
            }
            DeskDivider(Modifier.padding(horizontal = DeskSpace.x3))
          }
        }
      }
    }
  }
}

/** نمای کارتی — همان داده، همان تیک و همان وضعیتِ واژه‌ای؛ کارتِ باز/تیک‌خورده حلقهٔ تمرکز دارد */
@Composable
internal fun DeskProductsGrid(
  rows: List<Product>,
  stockOf: (String) -> Double,
  selected: Set<String>,
  openId: String?,
  onToggle: (String) -> Unit,
  onOpen: (String) -> Unit,
  bottomSpace: Dp,
  photos: Boolean,
) {
  val c = Desk.colors
  LazyVerticalGrid(
    columns = GridCells.Adaptive(minSize = 220.dp),
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(start = DeskSpace.x1, end = DeskSpace.x1, top = DeskSpace.x1, bottom = bottomSpace + DeskSpace.x2),
    horizontalArrangement = Arrangement.spacedBy(DeskSpace.x3),
    verticalArrangement = Arrangement.spacedBy(DeskSpace.x3),
  ) {
    gridItems(rows, key = { it.id }) { p ->
      val stock = stockOf(p.id)
      val checked = p.id in selected
      val open = p.id == openId
      Column(
        Modifier
          .deskCard(c, fill = if (open) c.selected else c.surface)
          .then(if (checked || open) Modifier.border(DeskSize.focusRing, c.focus, DeskRadius.cardShape) else Modifier)
          .deskClickable(label = StrProductPages.editItem + ": " + p.name, shape = DeskRadius.cardShape) { onOpen(p.id) }
          .padding(DeskSpace.x3),
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          DeskCheckbox(if (checked) ToggleableState.On else ToggleableState.Off, StrProductPages.selectName(p.name), { onToggle(p.id) })
          Spacer(Modifier.weight(1f))
          Text(deskStatusLabel(ProductListModel.status(p, stock)), style = DeskType.caption, color = c.inkSecondary, maxLines = 1)
        }
        Spacer(Modifier.height(DeskSpace.x1))
        Row(verticalAlignment = Alignment.Top) {
          ProductThumb(p.id, p.name, 40.dp, photos, tintKey = p.category)
          Spacer(Modifier.width(DeskSpace.x3))
          Column(Modifier.weight(1f)) {
            Text(p.name, style = DeskType.bodyStrong, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(p.category.ifBlank { StrSale.uncategorized }, style = DeskType.caption, color = c.inkSecondary, maxLines = 1)
          }
        }
        Spacer(Modifier.height(DeskSpace.x2))
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text("${money(p.salePrice)} ؋", Modifier.weight(1f), style = DeskType.number.copy(fontSize = DeskType.sectionTitle.fontSize), color = c.ink, maxLines = 1)
          Text("${qty(stock)} ${p.unit}", style = DeskType.secondary, color = c.inkSecondary, maxLines = 1)
        }
        Text(StrProductPages.buyPrice(money(p.purchasePrice)), style = DeskType.caption, color = c.inkSecondary, maxLines = 1)
      }
    }
  }
}
