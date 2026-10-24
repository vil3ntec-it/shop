package ir.vil3ntec.tohid.ui.v3.stock

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.StrStock
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.WarehouseEngine
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.qty
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Space
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Action
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Card
import ir.vil3ntec.tohid.ui.v3.V3Chip
import ir.vil3ntec.tohid.ui.v3.V3ChipRow
import ir.vil3ntec.tohid.ui.v3.V3DateField
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3FieldKind
import ir.vil3ntec.tohid.ui.v3.V3Form
import ir.vil3ntec.tohid.ui.v3.V3IconButton
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3PickerRow
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.rememberV3Stagger
import ir.vil3ntec.tohid.ui.v3.v3Stagger
import ir.vil3ntec.tohid.ui.v3.V3Collapse
import ir.vil3ntec.tohid.ui.v3.v3Shake
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.layout.Column

/**
 *  `stock/entry/bulk` — «ورودِ چند قلم» (فرمِ نسخهٔ ۳): تاریخِ همهٔ ردیف‌ها، کارتِ هر ردیف
 *  (کالا یا محصولِ تازه، مقدار، قیمتِ خرید ⇐ «ارزش ۲٬۸۸۰ ؋»، و «…» برای تکثیر/حذف)،
 *  نوارِ چسبانِ جمع‌ها و «ذخیرهٔ همه». اسکن در سربرگ برای هر بارکد یک ردیف می‌سازد.
 *  یا همه ثبت می‌شوند یا هیچ‌کدام (`WarehouseEngine.addEntries`).
 *
 *  @param onScan بارکدی می‌خواهد و با کدِ خوانده‌شده صدا می‌زند
 *  @param onSave null یعنی ثبت شد؛ وگرنه دلیلِ رد
 */
@Composable
fun BulkEntryScreen(
  data: ShopData,
  today: String,
  onScan: ((String) -> Unit) -> Unit,
  onSave: (List<WarehouseEngine.BulkEntry>, String) -> String?,
  onBack: () -> Unit,
  /** حالتِ آغازِ دلخواه (عکس‌های پیش‌نمایش) */
  start: BulkEntryState? = null,
) {
  var state by remember { mutableStateOf(start ?: BulkEntryState.start(data, today)) }
  var showErrors by remember { mutableStateOf(false) }
  var failure by remember { mutableStateOf<String?>(null) }
  var pickFor by remember { mutableStateOf<Int?>(null) }
  var menuFor by remember { mutableStateOf<Int?>(null) }
  val unit = BulkEntryState.defaultUnit(data)
  val stagger = rememberV3Stagger()
  var shake by remember { mutableIntStateOf(0) }
  val ready = state.ready

  fun change(next: BulkEntryState) { state = next; failure = null }

  V3Form(
    title = StrStock.bulkTitle,
    primary = StrStock.saveAll,
    onPrimary = {
      showErrors = true
      failure = state.problem() ?: onSave(state.entries(data), state.date)
      if (failure != null) shake++
    },
    onBack = onBack,
    actions = listOf(V3Action(Icons.Outlined.QrCodeScanner, StrKit.scanBarcode) { onScan { code -> change(state.scanned(data, code, unit)) } }),
    primaryEnabled = ready.isNotEmpty(),
    summary = StrStock.bulkSummary(money(ready.size.toDouble()), qty(ready.sumOf { it.amount }), money(ready.sumOf { it.value })),
    dirty = state.dirty,
  ) {
    Spacer(Modifier.height(8.dp))
    V3DateField(state.date, { change(state.copy(date = it)) }, StrStock.bulkDate, Modifier.fillMaxWidth())
    state.rows.forEachIndexed { i, row ->
      Spacer(Modifier.height(12.dp))
      RowCard(
        number = i + 1,
        row = row,
        data = data,
        broken = showErrors && !row.untouched && !row.ready,
        onChange = { next -> change(state.update(row.key) { next }) },
        onPick = { pickFor = row.key },
        onMenu = { menuFor = row.key },
        onScanBarcode = { onScan { code -> change(state.update(row.key) { it.asNew(code) }) } },
        modifier = Modifier.v3Stagger(stagger, row.key, i),
      )
    }
    Spacer(Modifier.height(12.dp))
    V3Button(StrStock.addRow, { change(state.add(unit)) }, Modifier.fillMaxWidth(), kind = V3ButtonKind.SECONDARY, icon = Icons.Outlined.Add)
    failure?.let { V3Message(V3Tone.ERROR, it, Modifier.padding(top = 12.dp).v3Shake(shake)) }
    Spacer(Modifier.height(16.dp))
  }

  pickFor?.let { key ->
    V3Sheet(onDismiss = { pickFor = null }, title = StrStock.pickProduct) { closer ->
      ProductPickerList(
        data = data,
        selectedId = state.rows.find { it.key == key }?.productId,
        onPick = { p -> closer.close { change(state.update(key) { it.pick(p) }) } },
        onNew = { closer.close { change(state.update(key) { it.asNew() }) } },
      )
    }
  }
  menuFor?.let { key ->
    V3Sheet(onDismiss = { menuFor = null }) { closer ->
      V3Card(padding = PaddingValues(vertical = 4.dp)) {
        V3ListRow(StrStock.duplicateRow, icon = Icons.Outlined.ContentCopy, chevron = false, onClick = { closer.close { change(state.duplicate(key)) } })
        if (state.rows.size > 1) {
          V3Divider(start = 64.dp)
          V3ListRow(StrStock.removeRow, icon = Icons.Outlined.DeleteOutline, danger = true, chevron = false, onClick = { closer.close { change(state.remove(key)) } })
        }
      }
    }
  }
}

@Composable
private fun RowCard(
  number: Int,
  row: BulkRow,
  data: ShopData,
  broken: Boolean,
  onChange: (BulkRow) -> Unit,
  onPick: () -> Unit,
  onMenu: () -> Unit,
  onScanBarcode: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val c = PhoneV3.colors
  val ring = if (broken) Modifier.border(2.dp, PhoneV3Roles.errorText(c), RoundedCornerShape(V3Space.cardRadius)) else Modifier
  V3Card(modifier.then(ring), radius = V3Space.cardRadius, padding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 14.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(StrStock.rowN(money(number.toDouble())), style = V3Type.headline, color = c.ink, modifier = Modifier.weight(1f))
      V3IconButton(Icons.Outlined.MoreHoriz, StrKit.more, size = 38.dp, iconSize = 20.dp, onClick = onMenu)
    }
    Spacer(Modifier.height(8.dp))
    val product = data.products.find { it.id == row.productId }
    V3Card(padding = PaddingValues(0.dp)) {
      V3PickerRow(
        label = StrStock.productField,
        value = if (row.newProduct) StrStock.newProduct else product?.name,
        placeholder = StrStock.pickProduct,
        icon = Icons.Outlined.Inventory2,
        onClick = onPick,
      )
    }
    //  «محصولِ تازه»: کادرهای نام/دسته/بارکد/واحد با فنرِ کیت باز و بسته می‌شوند
    V3Collapse(row.newProduct) {
    Column {
      if (row.scanMiss) Text(StrStock.scanMiss, style = V3Type.footnote, color = c.inkMuted, modifier = Modifier.padding(top = 6.dp))
      Spacer(Modifier.height(10.dp))
      V3TextField(row.newName, { onChange(row.copy(newName = it)) }, StrStock.newProductName, Modifier.fillMaxWidth(), placeholder = StrStock.newProductNameHint, required = true)
      Spacer(Modifier.height(10.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        V3TextField(row.newCategory, { onChange(row.copy(newCategory = it)) }, StrStock.category, Modifier.weight(1f))
        V3TextField(row.newBarcode, { onChange(row.copy(newBarcode = it)) }, StrStock.barcode, Modifier.weight(1f), kind = V3FieldKind.BARCODE, onScan = onScanBarcode)
      }
      if (data.productUnits.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        V3ChipRow(contentPadding = PaddingValues(0.dp)) {
          data.productUnits.forEach { u -> V3Chip(u, selected = row.unit == u, onClick = { onChange(row.copy(unit = u)) }) }
        }
      }
    }
    }
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      V3TextField(row.qty, { onChange(row.copy(qty = it)) }, StrStock.quantity, Modifier.weight(1f), kind = V3FieldKind.NUMBER, suffix = row.unit.ifBlank { null }, required = true)
      V3TextField(row.price, { onChange(row.copy(price = it)) }, StrStock.purchasePrice, Modifier.weight(1f), kind = V3FieldKind.MONEY, required = true)
    }
    V3Collapse(row.amount > 0) {
      Text(
        StrStock.rowValue(money(row.value)),
        style = V3Type.subhead,
        color = PhoneV3Roles.brandText(c),
        modifier = Modifier.padding(top = 8.dp),
      )
    }
    V3Collapse(broken) {
      Text(StrStock.errRow(money(number.toDouble())), style = V3Type.footnote, color = PhoneV3Roles.errorText(c), modifier = Modifier.padding(top = 6.dp))
    }
  }
}
