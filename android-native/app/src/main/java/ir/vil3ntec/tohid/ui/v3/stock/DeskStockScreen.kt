package ir.vil3ntec.tohid.ui.v3.stock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FactCheck
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.HourglassBottom
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.MoveToInbox
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.RemoveShoppingCart
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrDeskWork
import ir.vil3ntec.tohid.core.i18n.StrGrid
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.StrStock
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.StockActions
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.qty
import ir.vil3ntec.tohid.ui.desk.Desk
import ir.vil3ntec.tohid.ui.desk.DeskButton
import ir.vil3ntec.tohid.ui.desk.DeskButtonKind
import ir.vil3ntec.tohid.ui.desk.DeskChip
import ir.vil3ntec.tohid.ui.desk.DeskChipRow
import ir.vil3ntec.tohid.ui.desk.DeskColumn
import ir.vil3ntec.tohid.ui.desk.DeskDivider
import ir.vil3ntec.tohid.ui.desk.DeskDropdown
import ir.vil3ntec.tohid.ui.desk.DeskEmpty
import ir.vil3ntec.tohid.ui.desk.DeskHues
import ir.vil3ntec.tohid.ui.desk.DeskIconButton
import ir.vil3ntec.tohid.ui.desk.DeskOption
import ir.vil3ntec.tohid.ui.desk.DeskSearchField
import ir.vil3ntec.tohid.ui.desk.DeskSize
import ir.vil3ntec.tohid.ui.desk.DeskSortColumn
import ir.vil3ntec.tohid.ui.desk.DeskSortHead
import ir.vil3ntec.tohid.ui.desk.DeskSpace
import ir.vil3ntec.tohid.ui.desk.DeskStatTile
import ir.vil3ntec.tohid.ui.desk.DeskType
import ir.vil3ntec.tohid.ui.desk.deskCard
import ir.vil3ntec.tohid.ui.desk.deskClickable
import ir.vil3ntec.tohid.ui.desk.deskNegativeTone
import ir.vil3ntec.tohid.ui.desk.DeskTone
import ir.vil3ntec.tohid.ui.desk.deskTone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 *  «انبار» ِ **کامپیوتر** (`stock` و `stock/{id}` با `desk`، `claude/e-desk-d3a`) روی `ui/desk`: نوارِ ابزار (جست‌وجو با اسکن،
 *  دسته و محلِ کشویی، کارهای «…» به‌شکلِ دکمه و «ورود کالا» ِ اصلی با Ctrl+N)، ردیفِ کارتِ عدد به رنگ‌های برند (ارزش، کم،
 *  تمام‌شده، نزدیکِ انقضا، منقضی — هر کدام همان فیلتر)، تراشه‌های فیلتر با شمار، و جدولِ مرتب‌شدنیِ آرام (یک کارِ خنثی:
 *  «ورود»). زدنِ ردیف ⇐ همان صفحهٔ کالا (`StockItemScreen`) در پنلِ کنار. همهٔ عدد و فیلتر از `StockListModel`؛ گوشی دست نخورد.
 */

/** مرتب‌سازیِ جدولِ انبار */
internal enum class DeskStockSort { NAME, STOCK, COVER, EXPIRY }

/** مرتب‌سازیِ خالص (آزمون‌دار): بی‌عدد (`null`) همیشه آخر، هر جهتی */
internal fun deskStockSorted(rows: List<StockRowUi>, sort: DeskStockSort, asc: Boolean): List<StockRowUi> {
  fun <T : Comparable<T>> by(key: (StockRowUi) -> T?): List<StockRowUi> {
    val (has, none) = rows.partition { key(it) != null }
    val sorted = has.sortedBy { key(it) }
    return (if (asc) sorted else sorted.reversed()) + none
  }
  return when (sort) {
    DeskStockSort.NAME -> if (asc) rows.sortedBy { it.product.name } else rows.sortedByDescending { it.product.name }
    DeskStockSort.STOCK -> by { it.stock }
    DeskStockSort.COVER -> by { StockListModel.coverShown(it) }
    DeskStockSort.EXPIRY -> by { r -> if (r.expired) -1L else r.expiresInDays }
  }
}

/** متنِ خانهٔ «انقضا» */
internal fun deskExpiryText(row: StockRowUi): String = when {
  row.expired -> StrStock.pillExpired
  row.expiresInDays == null -> "—"
  row.expiresInDays <= 0 -> StrDeskWork.today
  else -> StrDeskWork.daysLeft(money(row.expiresInDays.toDouble()))
}

/** متنِ خانهٔ «کافی برای» (INV-14) */
internal fun deskCoverText(row: StockRowUi): String = StockListModel.coverShown(row)?.let { days ->
  if (row.coverBeyond) StrDeskWork.overDays(money(ir.vil3ntec.tohid.core.purchase.DemandForecast.MAX_COVER_DAYS.toDouble()))
  else StrDeskWork.daysLeft(money(days.toDouble()))
} ?: "—"

private val W_STOCK = 112.dp
private val W_CARTONS = 72.dp
private val W_PRICE = 96.dp
private val W_COVER = 104.dp
private val W_EXP = 112.dp
private val W_STATUS = 92.dp
private val W_ACTION = 88.dp
/** سهمِ وزنیِ ستونِ نام در پنجرهٔ خیلی پهن (بقیه ≈ ۱) */
private const val W_NAME = 3.2f

/**
 *  صفحهٔ انبارِ کامپیوتر. [selected] کالای بازِ پنل (`stock/{id}`)؛ [onClose] پنل را می‌بندد. [extrasLabel] دکمهٔ «…» ِ کارهای
 *  افزوده (`StockExtras`) فقط وقتی کاری هست.
 */
@Composable
internal fun DeskStockScreen(
  data: ShopData,
  today: String,
  actions: StockListActions,
  itemActions: StockItemActions,
  selected: String?,
  places: Boolean,
  initialFilter: StockFilter,
  initialQuery: String,
  onClose: () -> Unit,
) {
  val dc = Desk.colors
  val tone = DeskHues.tone("warehouse")
  var query by rememberSaveable { mutableStateOf(initialQuery) }
  var place by rememberSaveable { mutableStateOf<String?>(null) }
  var filterKey by rememberSaveable(initialFilter) { mutableStateOf(initialFilter.key) }
  var category by rememberSaveable { mutableStateOf<String?>(null) }
  var sort by rememberSaveable { mutableStateOf(DeskStockSort.NAME) }
  var asc by rememberSaveable { mutableStateOf(true) }
  val filter = StockFilter.of(filterKey)
  val overview by produceState<StockOverview?>(null, data, today, places) {
    value = withContext(Dispatchers.Default) { StockListModel.overview(data, today, places) }
  }
  val categories = remember(data) { StockListModel.usedCategories(data) }
  val searchFocus = remember { FocusRequester() }
  //  Ctrl+N ِ این صفحه (`AppNav.addAction`) ⇐ «ورود کالا»
  val entryNew = remember(actions) { { actions.entry(null) } }
  DisposableEffect(entryNew) {
    ir.vil3ntec.tohid.ui.AppNav.addAction.value = entryNew
    onDispose { if (ir.vil3ntec.tohid.ui.AppNav.addAction.value === entryNew) ir.vil3ntec.tohid.ui.AppNav.addAction.value = null }
  }

  Column(
    Modifier
      .fillMaxSize()
      .background(dc.app)
      .onPreviewKeyEvent { e ->
        if (e.type == KeyEventType.KeyDown && (e.isCtrlPressed || e.isMetaPressed) && e.key == Key.F) { runCatching { searchFocus.requestFocus() }; true } else false
      }
      .padding(horizontal = DeskSpace.page, vertical = DeskSpace.x4),
  ) {
    /* ─────────── ۱. نوارِ ابزار: جست‌وجو (با اسکن)، کارها و «ورود کالا» ِ اصلی ─────────── */
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DeskSpace.x2)) {
      DeskSearchField(
        query, { query = it }, StrStock.searchHint, Modifier.weight(1f).widthIn(max = 460.dp), focusRequester = searchFocus,
        clearLabel = StrStock.clearFilters,
        extra = { DeskIconButton(Icons.Outlined.QrCodeScanner, StrKit.scanBarcode, size = 26.dp, iconSize = 17.dp, onClick = actions.scan) },
      )
      Spacer(Modifier.weight(0.01f))
      DeskButton(StrStock.menuCount, actions.count, icon = Icons.AutoMirrored.Outlined.FactCheck)
      DeskButton(StrStock.menuBulk, actions.bulk, icon = Icons.AutoMirrored.Outlined.PlaylistAdd)
      MoreMenu(actions)
      DeskButton(StrStock.entryAction, { actions.entry(null) }, icon = Icons.Outlined.MoveToInbox, kind = DeskButtonKind.PRIMARY, shortcut = "Ctrl+N")
    }
    Spacer(Modifier.height(DeskSpace.x4))

    val ready = overview
    if (data.products.isEmpty()) {
      DeskEmpty(Icons.Outlined.Inventory2, StrStock.emptyTitle, StrStock.emptyBody, tone, action = StrStock.emptyAction, onAction = actions.newProduct)
      return@Column
    }

    /* ─────────── ۲. کارت‌های عدد — هر کدام همان فیلتر ─────────── */
    Row(horizontalArrangement = Arrangement.spacedBy(DeskSpace.gap)) {
      val counts = ready?.counts.orEmpty()
      val s = ready?.summary
      DeskStatTile(
        StrDeskWork.stockValue, if (s == null) "…" else "${money(s.value)} ؋", Icons.Outlined.Inventory2, dc.deskTone(DeskTone.NEUTRAL), Modifier.weight(1.3f),
        note = s?.let { StrDeskWork.stockValueNote(money(it.products.toDouble()), qty(it.units)) },
        selected = filter == StockFilter.ALL, onClick = { filterKey = StockFilter.ALL.key },
      )
      DeskStatTile(
        StrDeskWork.lowStock, money((counts[StockFilter.LOW] ?: 0).toDouble()), Icons.AutoMirrored.Outlined.TrendingDown, dc.deskTone(DeskTone.WARNING), Modifier.weight(1f),
        note = if ((counts[StockFilter.LOW] ?: 0) > 0) StrDeskWork.needsAction else StrDeskWork.allClear,
        selected = filter == StockFilter.LOW, onClick = { filterKey = StockFilter.LOW.key },
      )
      DeskStatTile(
        StrDeskWork.outStock, money((counts[StockFilter.OUT] ?: 0).toDouble()), Icons.Outlined.RemoveShoppingCart, dc.deskNegativeTone(), Modifier.weight(1f),
        note = if ((counts[StockFilter.OUT] ?: 0) > 0) StrDeskWork.needsAction else StrDeskWork.allClear,
        selected = filter == StockFilter.OUT, onClick = { filterKey = StockFilter.OUT.key },
      )
      DeskStatTile(
        StrDeskWork.expiring, money((counts[StockFilter.EXPIRING] ?: 0).toDouble()), Icons.Outlined.HourglassBottom, dc.deskTone(DeskTone.INFO), Modifier.weight(1f),
        note = if ((counts[StockFilter.EXPIRING] ?: 0) > 0) StrDeskWork.needsAction else StrDeskWork.allClear,
        selected = filter == StockFilter.EXPIRING, onClick = { filterKey = StockFilter.EXPIRING.key },
      )
      DeskStatTile(
        StrDeskWork.expiredLots, money((counts[StockFilter.EXPIRED] ?: 0).toDouble()), Icons.Outlined.EventBusy, dc.deskNegativeTone(), Modifier.weight(1f),
        note = if ((counts[StockFilter.EXPIRED] ?: 0) > 0) StrDeskWork.needsAction else StrDeskWork.allClear,
        selected = filter == StockFilter.EXPIRED, onClick = { filterKey = StockFilter.EXPIRED.key },
      )
    }
    Spacer(Modifier.height(DeskSpace.x3))

    /* ─────────── ۳. تراشه‌های فیلتر (با شمار) و نوارِ «دفترِ موجودی» ─────────── */
    val counts = ready?.counts.orEmpty()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DeskSpace.x3)) {
      DeskChipRow(Modifier.weight(1f)) {
        StockListModel.shownFilters(counts, filter).forEach { f ->
          DeskChip(filterLabel(f), selected = f == filter, onClick = { filterKey = f.key }, tone = tone, count = counts[f]?.takeIf { f != StockFilter.ALL && it > 0 })
        }
      }
      if (categories.isNotEmpty()) DeskDropdown(StrGrid.category, categories.map { DeskOption(it, it) }, category, { category = it }, StrStock.filterAll)
      //  INV-01: موجودیِ هر محل (فقط وقتی جز محلِ اصلی محلی هست)
      val placeList = ready?.let(StockListModel::placeChips).orEmpty()
      if (placeList.isNotEmpty()) DeskDropdown(StrDeskWork.place, placeList.map { DeskOption(it, StockActions.locationLabel(it)) }, place, { place = it }, StrStock.filterAll)
    }
    actions.header()
    Spacer(Modifier.height(DeskSpace.x3))

    /* ─────────── ۴. جدول | پنلِ کالا ─────────── */
    Row(Modifier.weight(1f).fillMaxWidth()) {
      Box(Modifier.weight(1f).fillMaxHeight().deskCard(dc).padding(DeskSpace.x2)) {
        if (ready == null) {
          Text("…", Modifier.align(Alignment.Center), style = DeskType.secondary, color = dc.inkSecondary)
        } else {
          val rows = remember(ready, filter, query, category, place, sort, asc) {
            deskStockSorted(StockListModel.visible(StockListModel.atPlace(ready, place), filter, query, category), sort, asc)
          }
          if (rows.isEmpty()) DeskEmpty(Icons.Outlined.SearchOff, StrKit.nothingFound, StrStock.noMatchBody, tone, action = StrStock.clearFilters, onAction = {
            query = ""; filterKey = StockFilter.ALL.key; category = null; place = null
          })
          else StockTable(rows, selected, sort, asc, onSort = { s -> if (sort == s) asc = !asc else { sort = s; asc = true } }, actions = actions)
        }
      }
      if (selected != null) {
        Spacer(Modifier.width(DeskSpace.gap))
        Box(Modifier.width(420.dp).fillMaxHeight().deskCard(dc)) {
          StockItemScreen(data, today, selected, itemActions, onBack = onClose)
        }
      }
    }
  }
}

/** «…»: ورود از فایل و کارهای افزودهٔ انبار (`StockExtras`) در فهرستِ کشویی — همان کارها، بی دکمهٔ مرده */
@Composable
private fun MoreMenu(actions: StockListActions) {
  var open by remember { mutableStateOf(false) }
  val c = Desk.colors
  Box {
    DeskButton(StrDeskWork.moreActions, { open = true }, icon = Icons.Outlined.MoreHoriz, kind = DeskButtonKind.GHOST)
    DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = c.surface) {
      DropdownMenuItem(
        text = { Text(StrStock.menuImport, style = DeskType.body, color = c.ink) },
        leadingIcon = { Icon(Icons.Outlined.UploadFile, null, tint = c.icon) },
        onClick = { open = false; actions.import() },
      )
      actions.extras.forEach { x ->
        DropdownMenuItem(
          text = { Text(x.label, style = DeskType.body, color = c.ink) },
          leadingIcon = { Icon(x.icon, null, tint = c.icon) },
          onClick = { open = false; x.onClick() },
        )
      }
    }
  }
}

@Composable
private fun StockTable(
  rows: List<StockRowUi>,
  selected: String?,
  sort: DeskStockSort,
  asc: Boolean,
  onSort: (DeskStockSort) -> Unit,
  actions: StockListActions,
) {
  val c = Desk.colors
  BoxWithConstraints(Modifier.fillMaxSize()) {
    val wide = maxWidth.value >= 900f
    //  پنجرهٔ خیلی پهن: ستون‌ها سهمِ وزنی می‌گیرند تا عددها کنارِ نام پخش شوند، نه همه ته جدول
    val xw = maxWidth.value >= 1300f
    fun col(title: String, w: Dp, weight: Float, end: Boolean = false) = if (xw) DeskColumn(title, weight = weight, end = end) else DeskColumn(title, width = w, end = end)
    val head = buildList {
      add(DeskSortColumn(DeskColumn(StrStock.productField, weight = if (xw) W_NAME else 1f), DeskStockSort.NAME))
      add(DeskSortColumn(col(StrDeskWork.colStock, W_STOCK, 1.2f, end = true), DeskStockSort.STOCK))
      if (wide) add(DeskSortColumn<DeskStockSort>(col(StrDeskWork.colCartons, W_CARTONS, 0.8f, end = true)))
      if (wide) add(DeskSortColumn<DeskStockSort>(col(StrDeskWork.colSalePrice, W_PRICE, 1f, end = true)))
      if (wide) add(DeskSortColumn(col(StrDeskWork.colCover, W_COVER, 1.1f), DeskStockSort.COVER))
      add(DeskSortColumn(col(StrDeskWork.colExpiry, W_EXP, 1.1f), DeskStockSort.EXPIRY))
      add(DeskSortColumn<DeskStockSort>(col(StrDeskWork.colStatus, W_STATUS, 1f)))
      add(DeskSortColumn<DeskStockSort>(col("", W_ACTION, 0.9f)))
    }
    fun RowScope.cell(w: Dp, weight: Float): Modifier = if (xw) Modifier.weight(weight) else Modifier.width(w)
    Column(Modifier.fillMaxSize()) {
      DeskSortHead(head, sort, asc, onSort, sortLabel = StrDeskWork::sortBy)
      LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(bottom = DeskSpace.x2)) {
        items(rows, key = { it.product.id }) { row ->
          val p = row.product
          val on = p.id == selected
          Column {
            Row(
              Modifier
                .fillMaxWidth()
                .heightIn(min = DeskSize.tableRow + 4.dp)
                .deskClickable(label = p.name, selected = on) { actions.open(p.id) }
                .semantics { this.selected = on }
                .padding(horizontal = DeskSpace.x3),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(DeskSpace.x3),
            ) {
              Column(Modifier.weight(if (xw) W_NAME else 1f)) {
                Text(p.name, style = DeskType.bodyStrong, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val meta = listOf(p.category, p.barcodes.firstOrNull().orEmpty()).filter { it.isNotBlank() }.joinToString(" • ")
                if (meta.isNotEmpty()) Text(meta, style = DeskType.caption.copy(fontWeight = DeskType.secondary.fontWeight), color = c.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
              }
              Text(listOf(qty(row.stock), p.unit).filter { it.isNotBlank() }.joinToString(" "), cell(W_STOCK, 1.2f), style = DeskType.number, color = c.ink, textAlign = TextAlign.End, maxLines = 1)
              if (wide) {
                Text(if (row.cartons > 0) qty(row.cartons) else "—", cell(W_CARTONS, 0.8f), style = DeskType.table, color = c.inkSecondary, textAlign = TextAlign.End, maxLines = 1)
                Text(money(p.salePrice), cell(W_PRICE, 1f), style = DeskType.table, color = c.inkSecondary, textAlign = TextAlign.End, maxLines = 1)
                Text(deskCoverText(row), cell(W_COVER, 1.1f), style = DeskType.table, color = c.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
              }
              Text(deskExpiryText(row), cell(W_EXP, 1.1f), style = if (row.expired) DeskType.label else DeskType.table, color = if (row.expired || row.expiresInDays != null) c.ink else c.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
              //  وضعیت واژهٔ آرام (همان `stockPill`)؛ عادی ⇒ خالی (بی خط‌تیرهٔ تکراری)
              val pill = stockPill(row)
              Text(pill?.text.orEmpty(), cell(W_STATUS, 1f), style = if (pill != null) DeskType.label else DeskType.table, color = if (pill != null) c.ink else c.inkMuted, maxLines = 1)
              //  تنها کارِ ردیف، خنثی: «ورود» ِ همین کالا (اصلاح در پنلِ کالا)
              Box(cell(W_ACTION, 0.9f), contentAlignment = Alignment.CenterEnd) {
                DeskButton(StrStock.swipeEntry, { actions.entry(p.id) }, kind = DeskButtonKind.GHOST)
              }
            }
            DeskDivider(Modifier.padding(horizontal = DeskSpace.x3))
          }
        }
      }
    }
  }
}
