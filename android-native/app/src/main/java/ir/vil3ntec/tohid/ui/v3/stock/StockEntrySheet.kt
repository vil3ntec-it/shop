package ir.vil3ntec.tohid.ui.v3.stock

import androidx.compose.animation.AnimatedVisibility
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
import kotlinx.coroutines.delay

/**
 *  `stock/entry?product=&barcode=` — برگهٔ «ورود کالا به انبار» (فرمِ نسخهٔ ۳ در برگه):
 *  کالا (انتخاب‌گرِ جست‌وجودار در همان برگه، با حرکتِ صفحه و تغییرِ ارتفاع)، مقدار
 *  (کارتن × در هر کارتن ⇄ واحد، با «= ۱۴۴ عدد»ِ زنده)، قیمتِ خرید با جمعِ زنده، و
 *  جزئیاتِ تاشوی محموله. «ثبت ورود» تیکِ موفقیت و لرزش دارد و برگه با حرکت بسته می‌شود.
 *  نوشتهٔ ذخیره‌نشده + بستن ⇐ «دور ریخته شود؟».
 *
 *  @param onSave نتیجهٔ موتور: null یعنی ثبت شد؛ وگرنه دلیلِ رد
 */
@Composable
fun StockEntrySheet(
  data: ShopData,
  product: String?,
  barcode: String?,
  onSave: (WarehouseEngine.EntryDraft) -> String?,
  onNewProduct: (barcode: String?) -> Unit,
  onClose: () -> Unit,
  /** حالتِ آغازِ دلخواه (عکس‌های پیش‌نمایش)؛ null ⇒ از مسیر */
  start: EntryForm? = null,
) {
  val initial = remember { start ?: EntryForm.start(data, product, barcode) }
  var form by remember { mutableStateOf(initial) }
  val unknown = barcode?.takeIf { it.isNotBlank() && initial.productId.isEmpty() }
  var step by remember { mutableStateOf(if (initial.productId.isEmpty() && unknown == null) EntryStep.PICK else EntryStep.FORM) }
  var showErrors by remember { mutableStateOf(false) }
  var failure by remember { mutableStateOf<String?>(null) }
  var saved by remember { mutableStateOf(false) }

  DraftFormSheet(
    title = if (step == EntryStep.PICK) StrStock.pickProduct else StrStock.entryTitle,
    dirty = form.dirty(initial) && !saved,
    onClose = onClose,
  ) { sheet ->
    if (saved) LaunchedEffect(Unit) { delay(MotionTokens.EMPHASIZED + 150L); sheet.finish(onClose) }
    V3PageSwitch(step, depth = { if (it == EntryStep.PICK) 1 else 0 }) { s ->
      when (s) {
        EntryStep.PICK -> ProductPickerList(
          data = data,
          selectedId = form.productId,
          onPick = { p -> form = form.pick(p); failure = null; step = EntryStep.FORM },
          onNew = { sheet.finish { onNewProduct(unknown) } },
        )
        EntryStep.FORM -> Column {
          Column(Modifier.weight(1f, fill = false).heightIn(max = 560.dp).verticalScroll(rememberScrollState())) {
            EntryFields(data, form, { form = it; failure = null }, if (showErrors) form.errors(data) else emptyMap(), unknown,
              onPick = { step = EntryStep.PICK }, onNewProduct = { sheet.finish { onNewProduct(unknown) } })
          }
          failure?.let { V3Message(V3Tone.ERROR, it, Modifier.padding(top = 12.dp)) }
          Spacer(Modifier.height(14.dp))
          Box(contentAlignment = Alignment.Center) {
            V3Button(StrStock.submitEntry, {
              showErrors = true
              if (form.errors(data).isEmpty()) {
                failure = onSave(form.draft())
                saved = failure == null
              }
            }, Modifier.fillMaxWidth(), enabled = !saved, haptic = true)
            V3SuccessCheck(saved, size = 44.dp)
          }
        }
      }
    }
  }
}

private enum class EntryStep { FORM, PICK }

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
  AnimatedVisibility(details, enter = DraftMotion.reveal(), exit = DraftMotion.hide()) {
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
