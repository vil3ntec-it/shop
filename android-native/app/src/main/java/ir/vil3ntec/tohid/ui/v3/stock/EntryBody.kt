package ir.vil3ntec.tohid.ui.v3.stock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrStock
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.WarehouseEngine
import ir.vil3ntec.tohid.formatDate
import ir.vil3ntec.tohid.Jalali
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.qty
import ir.vil3ntec.tohid.ui.screens.MotionTokens
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Card
import ir.vil3ntec.tohid.ui.v3.V3Chip
import ir.vil3ntec.tohid.ui.v3.V3ChipRow
import ir.vil3ntec.tohid.ui.v3.V3FieldKind
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3PageSwitch
import ir.vil3ntec.tohid.ui.v3.V3PickerRow
import ir.vil3ntec.tohid.ui.v3.V3Segmented
import ir.vil3ntec.tohid.ui.v3.V3SuccessCheck
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.V3Collapse
import ir.vil3ntec.tohid.ui.v3.v3Shake
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.delay

internal enum class EntryStep { FORM, PICK }

/**
 *  حالتِ یک بارِ «ورود کالا» — بیرونِ میزبان نگه داشته می‌شود تا همان فرم هم در برگهٔ
 *  مسیرِ `stock/entry` و هم در صفحهٔ «ورود کالا» که از دکمهٔ شناورِ انبار باز می‌شود
 *  (container transform) کار کند، و «دور ریخته شود؟» نوشته‌ها را نگه دارد.
 */
@Stable
internal class EntryFlow(val initial: EntryForm, val unknown: String?) {
  var form by mutableStateOf(initial)
  var step by mutableStateOf(if (initial.productId.isEmpty() && unknown == null) EntryStep.PICK else EntryStep.FORM)
  var showErrors by mutableStateOf(false)
  var failure by mutableStateOf<String?>(null)
  var saved by mutableStateOf(false)
  /** هر ردی یک بار می‌لرزاند (`v3Shake`) */
  var shake by mutableIntStateOf(0)

  val dirty: Boolean get() = form.dirty(initial) && !saved
  val title: String get() = if (step == EntryStep.PICK) StrStock.pickProduct else StrStock.entryTitle

  /** خطای کادرها ⇐ لرزش؛ وگرنه موتور: ثبت یا دلیلِ رد (و لرزش) */
  fun submit(data: ShopData, onSave: (WarehouseEngine.EntryDraft) -> String?) {
    showErrors = true
    if (form.errors(data).isNotEmpty()) { shake++; return }
    failure = onSave(form.draft())
    saved = failure == null
    if (!saved) shake++
  }
}

@Composable
internal fun rememberEntryFlow(data: ShopData, product: String?, barcode: String?, start: EntryForm? = null, key: Any? = null): EntryFlow =
  remember(key) {
    val initial = start ?: EntryForm.start(data, product, barcode)
    EntryFlow(initial, barcode?.takeIf { it.isNotBlank() && initial.productId.isEmpty() })
  }

/** پس از ثبت: تیکِ موفقیت دیده شود، بعد میزبان بسته شود */
internal const val ENTRY_CLOSE_DELAY = MotionTokens.EMPHASIZED + 150L

/** گامِ «انتخابِ کالا» */
@Composable
internal fun EntryPick(flow: EntryFlow, data: ShopData, onNew: () -> Unit) {
  ProductPickerList(
    data = data,
    selectedId = flow.form.productId,
    onPick = { p -> flow.form = flow.form.pick(p); flow.failure = null; flow.step = EntryStep.FORM },
    onNew = onNew,
  )
}

/** کادرهای فرم و پیامِ ردِ موتور — با لرزشِ کیت روی هر رد */
@Composable
internal fun EntryFormFields(flow: EntryFlow, data: ShopData, onNew: () -> Unit, modifier: Modifier = Modifier) {
  Column(modifier.v3Shake(flow.shake.takeIf { it > 0 })) {
    EntryFields(
      data, flow.form, { flow.form = it; flow.failure = null }, if (flow.showErrors) flow.form.errors(data) else emptyMap(), flow.unknown,
      onPick = { flow.step = EntryStep.PICK }, onNewProduct = onNew,
    )
    flow.failure?.let { V3Message(V3Tone.ERROR, it, Modifier.padding(top = 12.dp)) }
  }
}

@Composable
private fun ColumnScope.EntryFields(
  data: ShopData,
  form: EntryForm,
  onForm: (EntryForm) -> Unit,
  errors: Map<EntryField, String>,
  unknownBarcode: String?,
  onPick: () -> Unit,
  onNewProduct: () -> Unit,
) {
  val product = data.products.find { it.id == form.productId }
  val unit = form.unit.ifBlank { product?.unit.orEmpty() }
  if (unknownBarcode != null && product == null) {
    V3Message(V3Tone.INFO, StrStock.unknownBarcode(unknownBarcode), Modifier.padding(bottom = 12.dp), action = StrStock.newProduct, onAction = onNewProduct)
  }
  V3Card(padding = PaddingValues(0.dp)) {
    V3PickerRow(
      label = StrStock.productField,
      value = product?.name,
      placeholder = StrStock.pickProduct,
      icon = Icons.Outlined.Inventory2,
      error = errors[EntryField.PRODUCT],
      onClick = onPick,
    )
  }
  Spacer(Modifier.height(16.dp))
  V3Segmented(listOf(StrStock.modeCartons, StrStock.modeUnits), if (form.cartonMode) 0 else 1, { onForm(form.copy(cartonMode = it == 0)) })
  Spacer(Modifier.height(12.dp))
  val equals = form.amount.takeIf { it > 0 }?.let { StrStock.equalsUnits(listOf(qty(it), unit).filter { s -> s.isNotBlank() }.joinToString(" ")) }
  if (form.cartonMode) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      V3TextField(form.cartons, { onForm(form.copy(cartons = it)) }, StrStock.cartonCount, Modifier.weight(1f), kind = V3FieldKind.NUMBER, error = errors[EntryField.QUANTITY])
      V3TextField(form.perCarton, { onForm(form.copy(perCarton = it)) }, StrStock.perCarton, Modifier.weight(1f), kind = V3FieldKind.NUMBER, suffix = unit.ifBlank { null }, helper = equals)
    }
  } else {
    V3TextField(form.units, { onForm(form.copy(units = it)) }, StrStock.unitCount, Modifier.fillMaxWidth(), kind = V3FieldKind.NUMBER, suffix = unit.ifBlank { null }, error = errors[EntryField.QUANTITY], required = true)
  }
  Spacer(Modifier.height(12.dp))
  V3TextField(
    form.price, { onForm(form.copy(price = it)) }, StrStock.priceField, Modifier.fillMaxWidth(),
    kind = V3FieldKind.MONEY, required = true, error = errors[EntryField.PRICE],
    helper = form.total?.let { StrStock.total(money(it)) },
  )
  Spacer(Modifier.height(12.dp))
  var details by rememberSaveable { mutableStateOf(form.expiry.isNotBlank() || form.batch.isNotBlank() || errors.containsKey(EntryField.EXPIRY) || errors.containsKey(EntryField.MADE)) }
  if (errors.containsKey(EntryField.EXPIRY) || errors.containsKey(EntryField.MADE)) details = true
  V3Card(padding = PaddingValues(0.dp)) {
    V3ListRow(
      title = StrStock.details,
      icon = Icons.AutoMirrored.Outlined.ListAlt,
      chevron = false,
      trailing = { Icon(if (details) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null) },
      onClick = { details = !details },
    )
  }
  V3Collapse(details) {
    Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      DateText(form.expiry, { onForm(form.copy(expiry = it)) }, StrStock.expiry, errors[EntryField.EXPIRY])
      V3TextField(form.batch, { onForm(form.copy(batch = it)) }, StrStock.batch, Modifier.fillMaxWidth())
      DateText(form.made, { onForm(form.copy(made = it)) }, StrStock.made, errors[EntryField.MADE])
      V3TextField(form.notes, { onForm(form.copy(notes = it)) }, StrStock.notes, Modifier.fillMaxWidth(), singleLine = false)
      if (data.productUnits.isNotEmpty()) {
        Text(StrStock.unitField, style = V3Type.subhead, color = PhoneV3.colors.inkMuted)
        V3ChipRow(contentPadding = PaddingValues(0.dp)) {
          data.productUnits.forEach { u -> V3Chip(u, selected = form.unit == u, onClick = { onForm(form.copy(unit = u)) }) }
        }
      }
    }
  }
}

/** تاریخِ اختیاریِ محموله: خورشیدی یا میلادی؛ تاریخِ خوانده‌شده زیرِ کادر */
@Composable
private fun DateText(value: String, onValue: (String) -> Unit, label: String, error: String?) {
  val parsed = value.takeIf { it.isNotBlank() }?.let { Jalali.parseTyped(it) }
  V3TextField(
    value, onValue, label, Modifier.fillMaxWidth(),
    kind = V3FieldKind.DATE,
    placeholder = StrStock.datePlaceholder,
    helper = parsed?.let(::formatDate) ?: StrStock.dateHelper,
    error = error,
  )
}
