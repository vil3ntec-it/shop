package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.ShoppingBasket
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrDeskWork
import ir.vil3ntec.tohid.core.i18n.StrPos
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.qty
import ir.vil3ntec.tohid.ui.components.common.ViewMode
import ir.vil3ntec.tohid.ui.components.common.ViewModeState
import ir.vil3ntec.tohid.ui.components.pos.PosCategory
import ir.vil3ntec.tohid.ui.components.pos.PosPayMethod
import ir.vil3ntec.tohid.ui.components.pos.PosProductCallbacks
import ir.vil3ntec.tohid.ui.components.pos.PosSummaryRow
import ir.vil3ntec.tohid.ui.components.pos.ProductThumb
import ir.vil3ntec.tohid.ui.desk.Desk
import ir.vil3ntec.tohid.ui.desk.DeskBadge
import ir.vil3ntec.tohid.ui.desk.DeskBadgeKind
import ir.vil3ntec.tohid.ui.desk.DeskButton
import ir.vil3ntec.tohid.ui.desk.DeskButtonKind
import ir.vil3ntec.tohid.ui.desk.DeskChip
import ir.vil3ntec.tohid.ui.desk.DeskChipRow
import ir.vil3ntec.tohid.ui.desk.DeskColumn
import ir.vil3ntec.tohid.ui.desk.DeskDivider
import ir.vil3ntec.tohid.ui.desk.DeskEmpty
import ir.vil3ntec.tohid.ui.desk.DeskHue
import ir.vil3ntec.tohid.ui.desk.DeskIconButton
import ir.vil3ntec.tohid.ui.desk.DeskIconTile
import ir.vil3ntec.tohid.ui.desk.DeskRadius
import ir.vil3ntec.tohid.ui.desk.DeskSectionTitle
import ir.vil3ntec.tohid.ui.desk.DeskSegmented
import ir.vil3ntec.tohid.ui.desk.DeskShortcut
import ir.vil3ntec.tohid.ui.desk.DeskSize
import ir.vil3ntec.tohid.ui.desk.DeskSpace
import ir.vil3ntec.tohid.ui.desk.DeskStepper
import ir.vil3ntec.tohid.ui.desk.DeskTableHead
import ir.vil3ntec.tohid.ui.desk.DeskType
import ir.vil3ntec.tohid.ui.desk.DeskValueRow
import ir.vil3ntec.tohid.ui.desk.deskCard
import ir.vil3ntec.tohid.ui.desk.deskClickable

/*
 *  اجزای صندوقِ **کامپیوتر** (`DeskSaleScreen`، `claude/e-desk-d3a`) روی سیستمِ طراحیِ `ui/desk`: کاتالوگِ جدولی/کارتی،
 *  جدولِ سبد (کالا، تعداد، قیمت، تخفیف، جمع)، گزینشِ مشتری، جمع‌ها، روشِ پرداخت و دکمه‌های بزرگِ ثبت/چاپ.
 *  ردیف آرام است (خواستِ صاحب‌کار ۱۴۰۵/۰۸/۲۴): یک خطِ عنوان، یک خطِ کم‌رنگ، بی نشانِ رنگی، حداکثر یک کار.
 *  همه فقط نمایش‌اند؛ هر کار و کلید از صفحه می‌آید (همان `PosKeys`/`KeyMap`). گوشی این‌ها را نمی‌کشد.
 */

/** چیدمانِ کامپیوتر: سبد نزدیکِ نیمِ پهنا تا نامِ کالا در جدولِ پنج‌ستونه جا شود؛ کاتالوگ همیشه بیشترِ جا را دارد */
internal object DeskSaleLayout {
  fun cartWidth(widthDp: Float): Float = (widthDp * 0.47f).coerceIn(440f, 640f)
}

/** نمای جدول/کارت با `DeskSegmented` (همان ترجیحِ ذخیره‌شدهٔ `ViewModeState`) */
@Composable
internal fun DeskViewToggle(state: ViewModeState, onDone: () -> Unit) {
  if (!state.toggleVisible) return
  DeskSegmented(
    options = listOf(ViewMode.TABLE to Str.tableView, ViewMode.CARD to Str.cardView),
    selected = state.mode,
    onSelect = { state.set(it); onDone() },
  )
}

/** دسته‌ها: «همه» و دسته‌های دکان با شمارِ کالای فعال — تراشهٔ آرام، انتخاب‌شده به رنگِ بخش */
@Composable
internal fun DeskCategoryChips(categories: List<PosCategory>, selected: String?, tone: DeskHue, onSelect: (String?) -> Unit, modifier: Modifier = Modifier) {
  DeskChipRow(modifier.selectableGroup()) {
    categories.forEach { cat -> DeskChip(cat.label, selected = cat.name == selected, onClick = { onSelect(cat.name) }, tone = tone, count = cat.count) }
  }
}

/** موجودیِ کاتالوگ — همان واژه‌های کاشیِ پیشین (`StockLine`)، ولی فقط نوشتهٔ آرام (رنگ تنها نیست، رنگی هم نیست) */
internal fun deskStockText(p: Product, stock: Double): String {
  val unit = if (p.unit.isNotBlank()) " ${p.unit}" else ""
  return when {
    p.stockless == true -> ir.vil3ntec.tohid.core.i18n.StrProduct.serviceTag
    stock <= 0 -> Str.outOfStock
    p.minStock > 0 && stock <= p.minStock -> StrSale.lowQty(qty(stock) + unit)
    else -> StrSale.inStockQty(qty(stock) + unit)
  }
}

private val COL_STOCK = 132.dp
private val COL_PRICE = 104.dp
private val COL_IN_CART = 52.dp

/**
 *  نتیجه‌ها به‌شکلِ جدول (پیش‌فرضِ صفحه‌کلید): کالا (نام + خطِ کم‌رنگِ دسته • بارکد)، موجودی، قیمت، «در سبد».
 *  ردیفِ برجسته همان پیشنهادِ Enter است (↑↓)؛ زدنِ ردیف ⇐ افزودن. فهرستِ تنبل — ۲۰ هزار کالا فقط ردیف‌های دیده‌شده.
 */
@Composable
internal fun DeskCatalogTable(
  items: List<Product>,
  highlight: Int,
  showPhotos: Boolean,
  cb: PosProductCallbacks,
  state: LazyListState,
  modifier: Modifier = Modifier,
) {
  LaunchedEffect(highlight, items) {
    runCatching { if (highlight in items.indices) state.animateScrollToItem(highlight) else state.scrollToItem(0) }
  }
  val c = Desk.colors
  val cols = listOf(
    DeskColumn(Str.product, weight = 1f),
    DeskColumn(StrSale.stock, width = COL_STOCK),
    DeskColumn(StrSale.price, width = COL_PRICE, end = true),
    DeskColumn(StrPos.inCart, width = COL_IN_CART, end = true),
  )
  Column(modifier) {
    DeskTableHead(cols)
    LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = state, contentPadding = PaddingValues(bottom = DeskSpace.x2)) {
      itemsIndexed(items, key = { _, p -> p.id }) { i, p ->
        val on = i == highlight
        val inCart = cb.qtyOf(p.id)
        val stock = cb.stockOf(p.id)
        Column {
          Row(
            Modifier
              .fillMaxWidth()
              .heightIn(min = DeskSize.tableRow + 4.dp)
              .deskClickable(label = StrSale.addToCart, selected = on, onClick = { cb.onAdd(p) })
              .semantics { selected = on }
              .padding(horizontal = DeskSpace.x3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DeskSpace.x3),
          ) {
            if (showPhotos) ProductThumb(p.id, p.name, 30.dp, true, tintKey = p.category)
            Column(Modifier.weight(1f)) {
              Text(p.name, style = DeskType.bodyStrong, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
              val meta = listOf(p.category, p.barcodes.firstOrNull().orEmpty()).filter { it.isNotBlank() }.joinToString(" • ")
              if (meta.isNotEmpty()) Text(meta, style = DeskType.caption.copy(fontWeight = DeskType.secondary.fontWeight), color = c.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(deskStockText(p, stock), Modifier.width(COL_STOCK), style = DeskType.secondary, color = if (stock <= 0 && p.stockless != true) c.inkMuted else c.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${money(ir.vil3ntec.tohid.data.ProductPlansMap.price(p))} ؋", Modifier.width(COL_PRICE), style = DeskType.number, color = c.ink, textAlign = TextAlign.End, maxLines = 1)
            Text(if (inCart > 0) "×${qty(inCart)}" else "", Modifier.width(COL_IN_CART), style = DeskType.number, color = c.brandInk, textAlign = TextAlign.End, maxLines = 1)
          }
          DeskDivider(Modifier.padding(horizontal = DeskSpace.x3))
        }
      }
    }
  }
}

/**
 *  نتیجه‌ها به‌شکلِ کارت: نام (دو خط)، خطِ کم‌رنگِ موجودی، قیمتِ درشت و «×n» ِ در سبد — بی رنگِ وضعیت. کارتِ برجسته
 *  حلقهٔ تمرکز دارد؛ کارتِ «در سبد» خطِ برند. شبکهٔ تنبل.
 */
@Composable
internal fun DeskCatalogGrid(
  items: List<Product>,
  highlight: Int,
  showPhotos: Boolean,
  cb: PosProductCallbacks,
  state: LazyGridState,
  minCell: Dp,
  modifier: Modifier = Modifier,
) {
  LaunchedEffect(highlight, items) {
    runCatching { if (highlight in items.indices) state.animateScrollToItem(highlight) else state.scrollToItem(0) }
  }
  val c = Desk.colors
  LazyVerticalGrid(
    columns = GridCells.Adaptive(minSize = minCell),
    state = state,
    modifier = modifier,
    contentPadding = PaddingValues(DeskSpace.x1),
    horizontalArrangement = Arrangement.spacedBy(DeskSpace.x3),
    verticalArrangement = Arrangement.spacedBy(DeskSpace.x3),
  ) {
    gridItemsIndexed(items, key = { _, p -> p.id }) { i, p ->
      val on = i == highlight
      val inCart = cb.qtyOf(p.id)
      val stock = cb.stockOf(p.id)
      val price = ir.vil3ntec.tohid.data.ProductPlansMap.price(p)
      val label = buildString {
        append(p.name); append(Str.listSep + " "); append(money(price)); append(" " + Str.afghani)
        if (inCart > 0) { append(Str.listSep + " " + StrPos.inCartColon + " "); append(qty(inCart)) }
      }
      Column(
        Modifier
          .fillMaxWidth()
          .deskCard(c, fill = if (on) c.selected else c.surface)
          .then(
            when {
              on -> Modifier.border(DeskSize.focusRing, c.focus, DeskRadius.cardShape)
              inCart > 0 -> Modifier.border(1.dp, c.brand, DeskRadius.cardShape)
              else -> Modifier
            },
          )
          .semantics { selected = on; contentDescription = label }
          .deskClickable(label = StrSale.addToCart, shape = DeskRadius.cardShape, onClick = { cb.onAdd(p) })
          .padding(DeskSpace.x3),
      ) {
        Row(verticalAlignment = Alignment.Top) {
          if (showPhotos) {
            ProductThumb(p.id, p.name, 36.dp, true, tintKey = p.category)
            Spacer(Modifier.width(DeskSpace.x2))
          }
          Text(p.name, Modifier.weight(1f), style = DeskType.bodyStrong, color = c.ink, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(DeskSpace.x1))
        Text(deskStockText(p, stock), style = DeskType.caption.copy(fontWeight = DeskType.secondary.fontWeight), color = if (stock <= 0 && p.stockless != true) c.inkMuted else c.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(DeskSpace.x2))
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text("${money(price)} ؋", Modifier.weight(1f), style = DeskType.number.copy(fontSize = DeskType.sectionTitle.fontSize), color = c.ink, maxLines = 1)
          if (inCart > 0) Text("×${qty(inCart)}", style = DeskType.number, color = c.brandInk, maxLines = 1)
        }
      }
    }
  }
}

/** حالتِ بی‌نتیجه/بی‌کالا ِ کاتالوگ */
@Composable
internal fun DeskCatalogEmpty(title: String, body: String, tone: DeskHue) {
  DeskEmpty(Icons.Outlined.Inventory2, title, body, tone)
}

/** یک ردیفِ سبد برای جدول */
@Immutable
internal class DeskCartLine(
  val id: String,
  val name: String,
  val unit: String,
  val quantity: Double,
  /** قیمتِ هر واحد در سبد (دستی یا فهرست) */
  val price: Double,
  /** قیمتِ فهرست — تخفیفِ ردیف = (فهرست − قیمت) × تعداد وقتی قیمت پایین‌تر است */
  val listPrice: Double,
  val customPrice: Boolean,
  val category: String,
) {
  val total: Double get() = SalesEngine.lineTotal(price, quantity)
  val discount: Double get() = if (price < listPrice) SalesEngine.lineTotal(listPrice - price, quantity) else 0.0
}

/** ستون‌های جدولِ سبد — پهنای ثابتِ عددها، باقی مالِ نام */
private val CART_QTY = 108.dp
private val CART_PRICE = 78.dp
private val CART_OFF = 64.dp
private val CART_TOTAL = 88.dp

internal fun deskCartColumns(): List<DeskColumn> = listOf(
  DeskColumn(Str.product, weight = 1f),
  DeskColumn(StrDeskWork.colQty, width = CART_QTY),
  DeskColumn(StrSale.price, width = CART_PRICE, end = true),
  DeskColumn(Str.discount, width = CART_OFF, end = true),
  DeskColumn(StrDeskWork.colLineTotal, width = CART_TOTAL, end = true),
)

/**
 *  جدولِ سبد: کالا (نام + «قیمتِ واحد × واحد» کم‌رنگ)، شمارندهٔ − n +، قیمت، تخفیفِ ردیف و جمع. ردیفِ انتخاب‌شده (↑↓)
 *  زمینهٔ انتخاب دارد و راهنمای کلیدهایش زیرِ نام؛ زدنِ قیمت یا عدد ⇐ ویرایشِ تعداد و قیمت (F3).
 */
@Composable
internal fun DeskCartTable(
  lines: List<DeskCartLine>,
  selected: String?,
  state: LazyListState,
  showPhotos: Boolean,
  onSelect: (String) -> Unit,
  onEdit: (String) -> Unit,
  onMinus: (String) -> Unit,
  onPlus: (String) -> Unit,
  modifier: Modifier = Modifier,
  rowHints: List<String> = emptyList(),
  footer: (@Composable () -> Unit)? = null,
) {
  val c = Desk.colors
  val cols = deskCartColumns()
  Column(modifier) {
    DeskTableHead(cols)
    if (lines.isEmpty()) {
      Column(Modifier.weight(1f).fillMaxWidth().padding(DeskSpace.x6), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Filled.ShoppingBasket, contentDescription = null, tint = c.inkMuted, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(DeskSpace.x2))
        Text(Str.cartEmpty, style = DeskType.bodyStrong, color = c.ink)
        Spacer(Modifier.height(DeskSpace.x1))
        Text(StrPos.scanHintCard, Modifier.widthIn(max = 320.dp), style = DeskType.secondary, color = c.inkSecondary, textAlign = TextAlign.Center)
      }
      return@Column
    }
    LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = state) {
      itemsIndexed(lines, key = { _, l -> l.id }) { _, line ->
        val on = line.id == selected
        Column {
          Row(
            Modifier
              .fillMaxWidth()
              .heightIn(min = DeskSize.tableRow + 4.dp)
              .deskClickable(label = StrSale.selectRow, selected = on, onClick = { onSelect(line.id) })
              .semantics { this.selected = on }
              .padding(horizontal = DeskSpace.x3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DeskSpace.x3),
          ) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
              if (showPhotos) {
                ProductThumb(line.id, line.name, 28.dp, true, tintKey = line.category)
                Spacer(Modifier.width(DeskSpace.x2))
              }
              Column(Modifier.weight(1f)) {
                Text(line.name, style = DeskType.bodyStrong, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                //  خطِ کم‌رنگ: دسته (قیمت ستونِ خودش را دارد)؛ قیمتِ دستی ⇒ «قیمتِ فهرست ۶۰ ؋»
                val meta = if (line.customPrice && line.listPrice > 0 && line.listPrice != line.price) StrDeskWork.listPrice(money(line.listPrice)) else line.category
                if (meta.isNotBlank()) Text(meta, style = DeskType.caption.copy(fontWeight = DeskType.secondary.fontWeight), color = c.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (on && rowHints.isNotEmpty()) {
                  Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(DeskSpace.x1)) { rowHints.forEach { DeskShortcut(it) } }
                }
              }
            }
            Box(Modifier.width(CART_QTY), contentAlignment = Alignment.CenterStart) {
              DeskStepper(
                text = if (line.unit.isNotBlank()) "${qty(line.quantity)} ${line.unit}" else qty(line.quantity),
                minusLabel = StrSale.oneLess(line.name), plusLabel = StrSale.oneMore(line.name),
                onMinus = { onMinus(line.id) }, onPlus = { onPlus(line.id) },
                minusEnabled = line.quantity > 0,
                valueLabel = StrSale.editQtyPrice, onValue = { onEdit(line.id) },
              )
            }
            //  زدنِ قیمت ⇐ برگهٔ تعداد و قیمت (همان F3)
            Box(Modifier.width(CART_PRICE), contentAlignment = Alignment.CenterEnd) {
              Text(
                money(line.price), Modifier.deskClickable(label = StrSale.editQtyPrice, onClick = { onEdit(line.id) }).padding(horizontal = DeskSpace.x1, vertical = 2.dp),
                style = DeskType.table, color = if (line.customPrice) c.brandInk else c.ink, textAlign = TextAlign.End, maxLines = 1,
              )
            }
            Text(if (line.discount > 0) money(line.discount) else "—", Modifier.width(CART_OFF), style = DeskType.table, color = c.inkSecondary, textAlign = TextAlign.End, maxLines = 1)
            Text(money(line.total), Modifier.width(CART_TOTAL), style = DeskType.number, color = c.ink, textAlign = TextAlign.End, maxLines = 1)
          }
          DeskDivider(Modifier.padding(horizontal = DeskSpace.x3))
        }
      }
      if (footer != null) item(key = "desk-cart-footer") { Box(Modifier.padding(horizontal = DeskSpace.x3, vertical = DeskSpace.x2)) { footer() } }
    }
  }
}

/** کارِ کوچکِ سربرگِ سبد (قلمِ آزاد، برگشت، تحویلِ بعدی، خالی کردن) — دکمهٔ بی‌زمینه با میان‌بر */
@Composable
internal fun DeskCartAction(label: String, icon: ImageVector, hint: String?, enabled: Boolean = true, onClick: () -> Unit) {
  DeskButton(label, onClick, icon = icon, kind = DeskButtonKind.GHOST, shortcut = hint, enabled = enabled)
}

/**
 *  گزینشِ مشتری: کاشیِ آدمک، نام (یا «مشتریِ گذری» کم‌رنگ)، یک خطِ کم‌رنگِ حسابِ قرض، F2 و × برای برداشتن. کلِ ردیف زدنی.
 */
@Composable
internal fun DeskCustomerPicker(name: String?, detail: String?, pickHint: String, tone: DeskHue, onPick: () -> Unit, onRemove: () -> Unit, modifier: Modifier = Modifier) {
  val c = Desk.colors
  Row(
    modifier
      .fillMaxWidth()
      .heightIn(min = 48.dp)
      .border(DeskSize.hairline, c.hairline, DeskRadius.controlShape)
      .deskClickable(label = StrPos.pickCustomer, rest = c.surface, onClick = onPick)
      .padding(horizontal = DeskSpace.x3),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (name != null) DeskIconTile(Icons.Filled.Person, tone, size = 30.dp, iconSize = 17.dp)
    else Box(Modifier.size(30.dp).clip(DeskRadius.tileShape).background(c.surfaceAlt), contentAlignment = Alignment.Center) {
      Icon(Icons.Filled.Person, contentDescription = null, tint = c.inkSecondary, modifier = Modifier.size(17.dp))
    }
    Spacer(Modifier.width(DeskSpace.x3))
    Column(Modifier.weight(1f)) {
      Text(name ?: Str.walkIn, style = if (name != null) DeskType.bodyStrong else DeskType.body, color = if (name != null) c.ink else c.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
      if (detail != null) Text(detail, style = DeskType.caption.copy(fontWeight = DeskType.secondary.fontWeight), color = c.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    DeskShortcut(pickHint)
    if (name != null) {
      Spacer(Modifier.width(DeskSpace.x1))
      DeskIconButton(Icons.Filled.Close, StrPos.removeCustomer, size = 28.dp, iconSize = 16.dp, onClick = onRemove)
    }
  }
}

/** قابِ سبد: کارت با سرخط (شمار و کارها)، مشتری، جدولِ اقلام، جمع‌ها، روشِ پرداخت و دکمه‌ها — هر بخش یک جا */
@Composable
internal fun DeskCartPanel(
  itemCount: Int,
  tone: DeskHue,
  modifier: Modifier = Modifier,
  headerActions: @Composable RowScope.() -> Unit,
  customer: @Composable ColumnScope.() -> Unit,
  items: @Composable ColumnScope.() -> Unit,
  summary: @Composable ColumnScope.() -> Unit,
  methods: @Composable ColumnScope.() -> Unit,
  actions: @Composable ColumnScope.() -> Unit,
) {
  val c = Desk.colors
  Column(modifier.deskCard(c)) {
    Column(Modifier.padding(start = DeskSpace.x4, end = DeskSpace.x4, top = DeskSpace.x3)) {
      Row(Modifier.fillMaxWidth().heightIn(min = DeskSize.control), verticalAlignment = Alignment.CenterVertically) {
        DeskIconTile(Icons.Filled.ShoppingBasket, tone, size = 28.dp, iconSize = 16.dp)
        Spacer(Modifier.width(DeskSpace.x2))
        DeskSectionTitle(StrSale.cart)
        Spacer(Modifier.width(DeskSpace.x2))
        Text(StrSale.linesCount(itemCount), style = DeskType.secondary, color = c.inkSecondary, maxLines = 1)
        Spacer(Modifier.width(DeskSpace.x2))
        //  کارهای سربرگ در پایانِ ردیف؛ جا نشد ⇒ افقی می‌لغزند (نه بریده)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
          Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically, content = headerActions)
        }
      }
      Spacer(Modifier.height(DeskSpace.x2))
      customer()
      Spacer(Modifier.height(DeskSpace.x2))
    }
    Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = DeskSpace.x2)) { items() }
    DeskDivider()
    Column(Modifier.fillMaxWidth().background(c.surfaceAlt.copy(alpha = if (c.isDark) 0.35f else 0.55f)).padding(horizontal = DeskSpace.x4, vertical = DeskSpace.x3)) {
      summary()
      methods()
      Spacer(Modifier.height(DeskSpace.x3))
      actions()
    }
  }
}

/** جمع‌ها: ردیف‌های موتور (جمع، تخفیف، مالیات، گرد) کم‌رنگ، جای کادرِ تخفیف، و «مبلغِ قابلِ پرداخت» با عددِ درشت */
@Composable
internal fun DeskTotals(rows: List<PosSummaryRow>, payable: Double, extra: @Composable ColumnScope.() -> Unit = {}) {
  val c = Desk.colors
  Column(Modifier.fillMaxWidth()) {
    rows.forEach { r ->
      DeskValueRow(r.label, "${if (r.negative) "−" else ""}${money(r.amount)} ؋", valueColor = if (r.negative) c.negative else null)
    }
    extra()
    Spacer(Modifier.height(DeskSpace.x2))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Text(StrSale.payable, Modifier.weight(1f), style = DeskType.bodyStrong, color = c.ink, maxLines = 1)
      Text("${money(payable)} ؋", style = DeskType.kpi, color = c.ink, maxLines = 1)
    }
  }
}

/**
 *  روشِ پرداخت — فقط روش‌هایی که موتور ثبت می‌کند (`PosPayments.configured()`): دکمه‌های بزرگِ ۴۴dp با آیکن، انتخاب‌شده به
 *  رنگِ بخش با خطِ همان. نسیه بی مشتری ⇒ صفحه فهرستِ مشتری را باز می‌کند.
 */
@Composable
internal fun DeskPayMethods(methods: List<PosPayMethod>, selected: SalesEngine.Payment, tone: DeskHue, onSelect: (PosPayMethod) -> Unit) {
  val c = Desk.colors
  Row(Modifier.fillMaxWidth().padding(top = DeskSpace.x3).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(DeskSpace.x2)) {
    methods.forEach { m ->
      val on = m.payment == selected
      val icon = if (m.payment == SalesEngine.Payment.CASH) Icons.Outlined.Payments else Icons.Outlined.AccountBalanceWallet
      Row(
        Modifier
          .weight(1f)
          .height(44.dp)
          .clip(DeskRadius.controlShape)
          .background(if (on) tone.tile else c.surface)
          .border(if (on) 1.5.dp else DeskSize.hairline, if (on) tone.solid else c.hairline, DeskRadius.controlShape)
          .selectable(selected = on, role = Role.RadioButton) { onSelect(m) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
      ) {
        Icon(icon, contentDescription = null, tint = if (on) tone.glyph else c.inkSecondary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(DeskSpace.x2))
        Text(m.label, style = DeskType.bodyStrong, color = if (on) tone.ink else c.ink, maxLines = 1)
      }
    }
  }
}

/**
 *  «ثبتِ فروش» (اصلی، ۵۲dp، تمام‌عرض، با میان‌بر) و «چاپِ آخر» کنارش — دکمهٔ ثبت در حینِ ثبت خاموش است (POS-14).
 *  [secondary] دکمهٔ دومِ اختیاری کنارِ ثبت (پنلِ پرداخت ⇒ «نقدِ دقیق»).
 */
@Composable
internal fun DeskPayActions(
  postLabel: String,
  postHint: String,
  printHint: String,
  canPost: Boolean,
  posting: Boolean,
  canPrint: Boolean,
  onPost: () -> Unit,
  onPrint: () -> Unit,
  secondary: (@Composable RowScope.() -> Unit)? = null,
) {
  val c = Desk.colors
  val enabled = canPost && !posting
  Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DeskSpace.x2)) {
    Row(
      Modifier
        .weight(1f)
        .height(52.dp)
        .deskClickable(
          label = postLabel, enabled = enabled, rest = if (enabled) c.primary else c.surfaceAlt,
          hover = androidx.compose.ui.graphics.lerp(c.primary, c.ink, if (c.isDark) 0.12f else 0.18f), onClick = onPost,
        )
        .padding(horizontal = DeskSpace.x4),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center,
    ) {
      Text(if (posting) StrSale.posting else postLabel, style = DeskType.sectionTitle, color = if (enabled) c.onPrimary else c.inkMuted, maxLines = 1)
      if (!posting) {
        Spacer(Modifier.width(DeskSpace.x3))
        DeskShortcut(postHint, onPrimary = enabled)
      }
    }
    secondary?.invoke(this)
    Row(
      Modifier
        .height(52.dp)
        .border(DeskSize.hairline, c.hairline, DeskRadius.controlShape)
        .deskClickable(label = StrPos.printLastInvoice, enabled = canPrint, rest = c.surface, onClick = onPrint)
        .padding(horizontal = DeskSpace.x3),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(Icons.Filled.Print, contentDescription = null, tint = if (canPrint) c.icon else c.inkMuted, modifier = Modifier.size(18.dp))
      Spacer(Modifier.width(DeskSpace.x2))
      Text(StrPos.printLastInvoice, style = DeskType.bodyStrong, color = if (canPrint) c.ink else c.inkMuted, maxLines = 1)
      Spacer(Modifier.width(DeskSpace.x2))
      DeskShortcut(printHint)
    }
  }
}

/**
 *  کادرِ عددیِ کوچک در یک ردیفِ جمع‌ها («تخفیف ٪»، «دریافتیِ همین حالا ؋»): برچسبِ کم‌رنگ، [lead] (مثلاً «٪/؋») و کادرِ
 *  ۳۰dp با ترازِ پایان. خطا: لبهٔ سرخ و «عدد درست نیست» (متن + رنگ). نامِ دسترسیِ کادر همان برچسب است.
 */
@Composable
internal fun DeskInlineNumber(
  label: String,
  value: String,
  onValue: (String) -> Unit,
  isError: Boolean,
  suffix: String,
  focus: androidx.compose.ui.focus.FocusRequester? = null,
  lead: (@Composable RowScope.() -> Unit)? = null,
) {
  val c = Desk.colors
  Row(Modifier.fillMaxWidth().padding(top = DeskSpace.x1), verticalAlignment = Alignment.CenterVertically) {
    Text(label, Modifier.weight(1f), style = DeskType.secondary, color = c.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    if (lead != null) {
      lead()
      Spacer(Modifier.width(DeskSpace.x2))
    }
    ir.vil3ntec.tohid.ui.desk.DeskTextField(
      value = value, onValueChange = onValue, modifier = Modifier.width(132.dp), height = DeskSize.controlSmall,
      focusRequester = focus, isError = isError, textStyle = DeskType.table, textAlign = TextAlign.End, description = label, suffix = suffix,
    )
  }
  if (isError) Text(StrSale.badNumberShort, Modifier.fillMaxWidth(), style = DeskType.caption, color = c.negative, textAlign = TextAlign.End)
}

/** حالِ «درخواستِ تأییدِ» تخفیف زیرِ کادر: نوشتهٔ آرام با رنگِ معنا (نه قرص) و دکمهٔ «درخواستِ تأیید» */
@Composable
internal fun DeskGateLine(text: String, color: Color, action: (@Composable RowScope.() -> Unit)? = null) {
  Row(Modifier.fillMaxWidth().padding(top = DeskSpace.x1).heightIn(min = 26.dp), verticalAlignment = Alignment.CenterVertically) {
    Text(text, Modifier.weight(1f), style = DeskType.label, color = color, maxLines = 2, overflow = TextOverflow.Ellipsis)
    action?.invoke(this)
  }
}

/**
 *  نوارِ کلیدهای پایینِ کاتالوگ — فقط کلیدهایی که صندوق می‌گیرد (`PosKeys`، `KeyMap`)، کلیدِ تازه‌ای نیست. F12 فقط با پنلِ
 *  پرداخت جدا از F9 است (بی آن F12 همان «ثبت» است).
 */
internal fun deskSaleKeyHints(payV2: Boolean, receive: Boolean = false, priceCheck: Boolean = false): List<ir.vil3ntec.tohid.ui.desk.DeskKeyHint> {
  val k = ir.vil3ntec.tohid.core.keymap.KeyMap
  fun h(keys: String, label: String) = ir.vil3ntec.tohid.ui.desk.DeskKeyHint(keys, label)
  //  به ترتیبِ اهمیت (پنجرهٔ باریک انتهای نوار را با پیمایش نشان می‌دهد)
  return listOfNotNull(
    h(k.SALE_ADD.hint, StrDeskWork.keyAdd),
    h(k.SALE_PAY_PANEL.hint, if (payV2) StrDeskWork.keyPayPanel else StrDeskWork.keyPay),
    if (payV2) h(k.SALE_FINISH_EXACT.hint, StrDeskWork.exactCash) else null,
    h(k.SALE_CUSTOMER.hint, StrDeskWork.keyCustomer),
    h(k.SALE_EDIT_ROW.hint, StrDeskWork.keyEditRow),
    h(k.SALE_FREE_ITEM.hint, StrDeskWork.keyFreeItem),
    h(k.SALE_REPRINT.hint, StrDeskWork.keyReprint),
    h("↑↓", StrDeskWork.keyRows),
    h("+ −", StrDeskWork.keyQty),
    //  F7 و Ctrl+I فقط وقتی کلیدِ همین دستگاه روشن است (خاموش ⇒ کلید مالِ صندوق نیست)
    if (receive) h(k.SALE_RECEIVE.hint, StrDeskWork.keyReceive) else null,
    if (priceCheck) h(k.SALE_PRICE_CHECK.hint, StrDeskWork.keyPriceCheck) else null,
    h("Esc", StrDeskWork.keyClear),
    h("F1", StrDeskWork.keyAll),
  )
}
