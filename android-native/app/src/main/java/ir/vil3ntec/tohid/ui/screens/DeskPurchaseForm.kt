package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import ir.vil3ntec.tohid.core.i18n.StrDeskData
import ir.vil3ntec.tohid.core.i18n.StrDeskWork
import ir.vil3ntec.tohid.core.i18n.StrInvoices
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.StrPlaces
import ir.vil3ntec.tohid.core.i18n.StrPurchasePages
import ir.vil3ntec.tohid.core.i18n.StrReturnPages
import ir.vil3ntec.tohid.core.i18n.StrStock
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.ui.desk.Desk
import ir.vil3ntec.tohid.ui.desk.DeskCell
import ir.vil3ntec.tohid.ui.desk.DeskColumn
import ir.vil3ntec.tohid.ui.desk.DeskDivider
import ir.vil3ntec.tohid.ui.desk.DeskIconButton
import ir.vil3ntec.tohid.ui.desk.DeskKeyBar
import ir.vil3ntec.tohid.ui.desk.DeskKeyHint
import ir.vil3ntec.tohid.ui.desk.DeskMenuRow
import ir.vil3ntec.tohid.ui.desk.DeskNote
import ir.vil3ntec.tohid.ui.desk.DeskRadius
import ir.vil3ntec.tohid.ui.desk.DeskSize
import ir.vil3ntec.tohid.ui.desk.DeskSpace
import ir.vil3ntec.tohid.ui.desk.DeskTextField
import ir.vil3ntec.tohid.ui.desk.DeskType

/*
 *  فرم‌های خریدِ کامپیوتر روی `ui/desk` (`claude/e-desk-d3c`) — فقط اجزای نمایشی: ردیفِ نوشتنیِ «فاکتورِ خریدِ تازه»، کادرِ
 *  برچسب‌دار، نوارِ کلیدها و فهرستِ پیشنهادِ کالا. منطقِ فرم (ردیف‌ها، اسکن، Enter ⇐ ردیفِ بعد، F9 ثبت با `PurchaseBook.record`)
 *  همان `NewPurchaseInvoice` در `DeskPurchasing.kt` است و این‌جا دست نخورد؛ گوشی هیچ‌کدام را نمی‌بیند.
 */

/** یک ردیفِ نوشتنیِ فاکتورِ خرید: کالا، (PRD-07) واحدِ خرید، مقدار، فی و انقضا — همان ردیفِ پیشینِ فرم */
internal class PurchaseDraft(product: Product? = null, unit: String? = null) {
  var productId by mutableStateOf(product?.id.orEmpty())
  /** PRD-07: نامِ واحدِ خرید؛ null = واحدِ پایه */
  var unitName by mutableStateOf(unit)
  var query by mutableStateOf(product?.name.orEmpty())
  var qty by mutableStateOf(if (product != null) "1" else "")
  var price by mutableStateOf(product?.let { purchaseDefaultPrice(it, unit) }.orEmpty())
  var expiry by mutableStateOf("")
  val focus = FocusRequester()
  val qtyFocus = FocusRequester()
  val priceFocus = FocusRequester()
  fun total(): Double = Math.round((qty.toDoubleOrNull() ?: 0.0) * (price.toDoubleOrNull() ?: 0.0)).toDouble()
}

/** قیمتِ پیش‌فرضِ ردیف: قیمتِ خریدِ واحدِ پایه، یا (PRD-07) همان × ضریبِ واحدِ خرید */
internal fun purchaseDefaultPrice(p: Product, unit: String?): String {
  val base = p.purchasePrice.takeIf { it > 0 } ?: return ""
  val f = ir.vil3ntec.tohid.core.product.PurchaseUnits.find(p.purchaseUnits?.map(ShopStore::coreOf), unit)?.factorMilli ?: 1000L
  return Math.round(base * f / 1000.0).toString()
}

/** ستون‌های جدولِ ردیف‌ها — سرِ جدول ([ir.vil3ntec.tohid.ui.desk.DeskTableHead]) و ردیف همین پهناها را می‌گیرند */
internal fun purchaseLineColumns(): List<DeskColumn> = listOf(
  DeskColumn("#", width = 32.dp),
  DeskColumn(StrPurchasePages.itemNameBarcode, weight = 1f),
  DeskColumn(StrPlaces.amount, width = 110.dp, end = true),
  DeskColumn(StrReturnPages.unitPriceAfn, width = 140.dp, end = true),
  DeskColumn(StrPurchasePages.expiryOptional, width = 150.dp),
  DeskColumn(StrInvoices.total, width = 130.dp, end = true),
  DeskColumn("", width = DeskSize.control),
)

private fun RowScope.cellOf(col: DeskColumn): Modifier = if (col.width != null) Modifier.width(col.width) else Modifier.weight(col.weight)

/** کادر/کنترل با برچسبِ کوچکِ بالا و یادداشتِ کم‌رنگِ زیر (فرم‌های کامپیوتر) */
@Composable
internal fun DeskFormLabeled(label: String, modifier: Modifier = Modifier, note: String? = null, content: @Composable () -> Unit) {
  val c = Desk.colors
  Column(modifier) {
    Text(label, Modifier.padding(bottom = DeskSpace.x1), style = DeskType.label, color = c.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    content()
    if (note != null) Text(note, Modifier.padding(top = DeskSpace.x1), style = DeskType.caption, color = c.inkSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
  }
}

/** فهرستِ پیشنهادِ کالا زیرِ کادر — کارتِ `ui/desk`؛ فوکوس را از کادرِ نوشتن نمی‌دزدد (تایپ ادامه دارد) */
@Composable
internal fun DeskSuggestMenu(open: Boolean, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
  val c = Desk.colors
  DropdownMenu(
    expanded = open,
    onDismissRequest = onDismiss,
    properties = PopupProperties(focusable = false),
    shape = DeskRadius.cardShape,
    containerColor = c.surface,
    tonalElevation = 0.dp,
    shadowElevation = 6.dp,
    border = BorderStroke(DeskSize.hairline, c.hairline),
    modifier = Modifier.widthIn(min = 280.dp).padding(horizontal = DeskSpace.x1),
    content = content,
  )
}

/** نوارِ کلیدهای فرمِ خرید — همان کلیدهایی که فرم می‌گیرد (Enter، F9، Ctrl+I، Esc) و «بارکدخوان ردیف می‌افزاید» */
@Composable
internal fun DeskPurchaseKeys(modifier: Modifier = Modifier) {
  Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    DeskKeyBar(
      listOf(
        DeskKeyHint("Enter", StrDeskData.keyNextRow),
        DeskKeyHint("F9", StrDeskWork.keyPay),
        DeskKeyHint("Ctrl+I", StrDeskData.keyPriceHistory),
        DeskKeyHint("Esc", StrKit.cancel),
      ),
      Modifier.weight(1f),
    )
    DeskNote(StrDeskData.scannerAddsRow)
  }
}

private fun enterKey(next: () -> Unit): (KeyEvent) -> Boolean = { e ->
  if (e.type == KeyEventType.KeyDown && (e.key == Key.Enter || e.key == Key.NumPadEnter)) { next(); true } else false
}

/**
 *  ردیفِ نوشتنیِ جدولِ «فاکتورِ خریدِ تازه»: کالا (نام یا بارکد، Enter ⇐ بارکدِ دقیق یا نخستین پیشنهاد)، مقدار (Enter ⇐ فی)،
 *  فی و انقضا (Enter ⇐ ردیفِ بعد)، جمع و «حذفِ ردیف». زیرش (PRD-07) واحدِ خرید و (PUR-09) آخرین فیِ همین تأمین‌کننده.
 */
@Composable
internal fun DeskPurchaseLineRow(
  i: Int,
  l: PurchaseDraft,
  d: ShopData,
  hint: String?,
  unitsOn: Boolean,
  supplierId: String?,
  onTouch: () -> Unit,
  onNext: () -> Unit,
  onRemove: () -> Unit,
) {
  var menu by remember { mutableStateOf(false) }
  val index = remember(d) { ShopStore.barcodeIndex(d) }
  val matches = remember(l.query, d) {
    val q = l.query.trim()
    if (q.isEmpty() || l.productId.isNotBlank()) emptyList()
    else d.products.filter { it.name.contains(q, true) || it.barcodes.any { b -> b == q } }.take(8)
  }
  fun pick(p: Product) {
    l.productId = p.id; l.query = p.name; menu = false; onTouch()
    //  PRD-07: واحدِ پیش‌فرضِ همین تأمین‌کننده (کارتنِ ۲۴ ِ ب)، وگرنه واحدِ پایه
    l.unitName = if (unitsOn) ir.vil3ntec.tohid.core.product.PurchaseUnits.defaultFor(p.purchaseUnits?.map(ShopStore::coreOf), supplierId)?.name else null
    if (l.qty.isBlank()) l.qty = "1"
    if (l.price.isBlank()) l.price = purchaseDefaultPrice(p, l.unitName)
    runCatching { l.qtyFocus.requestFocus() }
  }
  val cols = purchaseLineColumns()
  Column(Modifier.fillMaxWidth()) {
    Row(
      Modifier.fillMaxWidth().heightIn(min = DeskSize.tableRow).padding(horizontal = DeskSpace.x3, vertical = DeskSpace.x1),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(DeskSpace.x3),
    ) {
      Box(cellOf(cols[0])) { DeskCell("${i + 1}", muted = true) }
      Box(cellOf(cols[1])) {
        DeskTextField(
          l.query,
          { l.query = it; l.productId = ""; menu = true },
          Modifier.fillMaxWidth(),
          focusRequester = l.focus,
          isError = l.query.isNotBlank() && l.productId.isBlank() && matches.isEmpty(),
          description = StrPurchasePages.itemNameBarcode,
          onKey = enterKey {
            val q = l.query.trim()
            val byCode = index[q]?.let { id -> d.products.find { it.id == id } }
            (byCode ?: matches.firstOrNull())?.let(::pick)
          },
        )
        DeskSuggestMenu(open = menu && matches.isNotEmpty(), onDismiss = { menu = false }) {
          matches.forEach { p -> DeskMenuRow(StrPurchasePages.pickRow(p.name, qtyText(ShopStore.stock(d, p.id)), p.unit)) { pick(p) } }
        }
      }
      Box(cellOf(cols[2])) {
        DeskTextField(
          l.qty, { l.qty = ir.vil3ntec.tohid.numericInput(it); onTouch() }, Modifier.fillMaxWidth(),
          focusRequester = l.qtyFocus, textAlign = TextAlign.End, description = StrPlaces.amount,
          onKey = enterKey { runCatching { l.priceFocus.requestFocus() } },
        )
      }
      Box(cellOf(cols[3])) {
        DeskTextField(
          l.price, { l.price = ir.vil3ntec.tohid.numericInput(it); onTouch() }, Modifier.fillMaxWidth(),
          focusRequester = l.priceFocus, textAlign = TextAlign.End, description = StrReturnPages.unitPriceAfn, onKey = enterKey(onNext),
        )
      }
      Box(cellOf(cols[4])) {
        DeskTextField(l.expiry, { l.expiry = it.take(10) }, Modifier.fillMaxWidth(), placeholder = "۱۴۰۶/۰۳/۳۱", description = StrPurchasePages.expiryOptional, onKey = enterKey(onNext))
      }
      Box(cellOf(cols[5]), contentAlignment = Alignment.CenterEnd) {
        if (l.productId.isNotBlank()) DeskCell(money(l.total()), strong = true)
      }
      Box(cellOf(cols[6]), contentAlignment = Alignment.Center) { DeskIconButton(Icons.Outlined.Close, StrStock.removeRow, onRemove) }
    }
    //  PRD-07: واحدِ خریدِ همین ردیف و مقدار/بهای واحدِ پایه از هسته (`PurchaseUnits.baseCost`)
    val product = if (unitsOn && l.productId.isNotBlank()) d.products.find { it.id == l.productId } else null
    if (product != null && !product.purchaseUnits.isNullOrEmpty()) PurchaseUnitPicker(product, l.unitName, l.qty, l.total()) { u ->
      val before = purchaseDefaultPrice(product, l.unitName)
      l.unitName = u
      if (l.price.isBlank() || l.price == before) l.price = purchaseDefaultPrice(product, u)
      onTouch()
    }
    //  PUR-09: آخرین فیِ همین تأمین‌کننده برای همین کالا (هنگامِ ورود)
    if (hint != null) DeskNote(hint, Modifier.padding(start = DeskSpace.x3 * 2 + 32.dp, bottom = DeskSpace.x1))
    DeskDivider(Modifier.padding(horizontal = DeskSpace.x3))
  }
}
