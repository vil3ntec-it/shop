package ir.vil3ntec.tohid.ui.v3.money

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrMoney
import ir.vil3ntec.tohid.data.LedgerEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.screens.v3Text
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3DateField
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3FieldKind
import ir.vil3ntec.tohid.ui.v3.V3Form
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3PickerRow
import ir.vil3ntec.tohid.ui.v3.V3Section
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.v3Shake

private enum class PurchasePicker { SUPPLIER, PRODUCT }

private val PurchaseFormSaver = listSaver<PurchaseForm, String>(
  save = { listOf(it.supplierId.orEmpty(), it.productId.orEmpty(), it.quantity, it.price, it.paid, it.date, it.notes) },
  restore = { v -> PurchaseForm(v[0].ifEmpty { null }, v[1].ifEmpty { null }, v[2], v[3], v[4], v[5], v[6]) },
)

/**
 *  «ثبت خرید» (`purchasing/new?supplier=`، صفحه): گروه‌های «تأمین‌کننده» (پیش‌پُر از حسابِ همان
 *  تأمین‌کننده)، «کالا» (انبارِ خالی ⇒ راهنما و پیوندِ «کالای تازه»)، «مقدار و قیمت» با «جمع خرید»
 *  ِ زنده، «پرداخت» (خالی یعنی نسیه) با «باقی‌مانده» ِ زنده، تاریخ و توضیحات. دکمهٔ چسبانِ «ثبت خرید»؛
 *  نوشتهٔ ذخیره‌نشده ⇒ «دور ریخته شود؟». ثبت = `LedgerEngine.addPurchase` (اثرِ انبار همان امروز).
 */
@Composable
internal fun PurchaseFormScreen(
  d: ShopData,
  supplierArg: String?,
  onBack: () -> Unit,
  onNewProduct: () -> Unit,
  /** `true` ⇐ موتور پذیرفت (صفحه بسته می‌شود)؛ ردِ موتور پیامِ خودش را دارد */
  onSave: (LedgerEngine.PurchaseDraft) -> Boolean,
) {
  val initial = remember(supplierArg) { PurchaseFormModel.initial(supplierArg?.takeIf { id -> d.suppliers.any { it.id == id } }, todayIso()) }
  //  نوشته‌ها با چرخشِ گوشی و برگشت از «کالای تازه» نمی‌روند
  var form by rememberSaveable(supplierArg, stateSaver = PurchaseFormSaver) { mutableStateOf(initial) }
  var tried by rememberSaveable { mutableStateOf(false) }
  var shake by remember { mutableStateOf(0) }
  var picker by remember { mutableStateOf<PurchasePicker?>(null) }
  val errors = PurchaseFormModel.errors(form)
  val shownErrors = if (tried) errors else PurchaseFormModel.Errors()
  val product = d.products.find { it.id == form.productId }
  val supplier = d.suppliers.find { it.id == form.supplierId }
  val total = PurchaseFormModel.total(form)

  V3Form(
    title = StrMoney.newPurchase,
    primary = StrMoney.newPurchase,
    onPrimary = {
      tried = true
      if (errors.any) shake++
      else if (onSave(PurchaseFormModel.draft(form, product?.unit.orEmpty()))) onBack()
    },
    onBack = onBack,
    dirty = PurchaseFormModel.dirty(form, initial),
    summary = if (total > 0) "${StrMoney.purchaseTotal}: ${MoneyText.afn(total)}" else null,
  ) {
    Column(Modifier.v3Shake(shake)) {
      //  تأمین‌کننده و کالا در یک کارت — برچسبِ هر ردیف خودش سرخط است
      V3Section(null, padding = PaddingValues(vertical = 4.dp)) {
        V3PickerRow(
          label = StrMoney.supplierLabel,
          value = supplier?.name,
          placeholder = StrMoney.pickSupplier,
          icon = Icons.Outlined.LocalShipping,
          error = shownErrors.supplier,
          onClick = { picker = PurchasePicker.SUPPLIER },
        )
        V3Divider(start = 64.dp)
        if (d.products.isEmpty()) {
          Column(Modifier.padding(12.dp)) {
            V3Message(V3Tone.INFO, StrMoney.noProductsHint, action = StrMoney.addProduct, onAction = onNewProduct)
          }
        } else {
          V3PickerRow(
            label = StrMoney.item,
            value = product?.name,
            placeholder = StrMoney.pickItem,
            icon = Icons.Outlined.Inventory2,
            error = shownErrors.product,
            onClick = { picker = PurchasePicker.PRODUCT },
          )
        }
      }
      V3Section(StrMoney.qtyAndPrice, padding = PaddingValues(16.dp)) {
        V3TextField(
          value = form.quantity,
          onValueChange = { form = form.copy(quantity = it) },
          label = StrMoney.quantity,
          kind = V3FieldKind.NUMBER,
          required = true,
          suffix = product?.unit?.takeIf { it.isNotBlank() },
          error = shownErrors.quantity,
        )
        Spacer(Modifier.height(12.dp))
        V3TextField(
          value = form.price,
          onValueChange = { form = form.copy(price = it) },
          label = StrMoney.unitPrice,
          kind = V3FieldKind.MONEY,
          required = true,
          error = shownErrors.price,
        )
        LiveLine(StrMoney.purchaseTotal, MoneyText.afn(total))
      }
      V3Section(StrMoney.paidSection, padding = PaddingValues(16.dp)) {
        V3TextField(
          value = form.paid,
          onValueChange = { form = form.copy(paid = it) },
          label = StrMoney.paidField,
          kind = V3FieldKind.MONEY,
          helper = StrMoney.paidHelper,
          error = shownErrors.paid,
        )
        val left = PurchaseFormModel.remaining(form)
        LiveLine(StrMoney.remainingLabel, MoneyText.afn(left), if (left > 0) LedgerTone.OWE else LedgerTone.SETTLED)
        Spacer(Modifier.height(12.dp))
        V3DateField(form.date, { form = form.copy(date = it) }, StrMoney.date)
      }
      V3Section(StrMoney.notesSection, padding = PaddingValues(16.dp)) {
        V3TextField(form.notes, { form = form.copy(notes = it) }, StrMoney.notesField, singleLine = false)
      }
      Spacer(Modifier.height(16.dp))
    }
  }

  when (picker) {
    PurchasePicker.SUPPLIER -> IdPickerSheet(
      title = StrMoney.supplierLabel,
      options = d.suppliers.map { PickOption(it.id, it.name, it.phone.ifBlank { null }) },
      selected = form.supplierId,
      onPick = { id -> form = form.copy(supplierId = id) },
      onDismiss = { picker = null },
    )
    PurchasePicker.PRODUCT -> IdPickerSheet(
      title = StrMoney.item,
      options = d.products.filter { it.active || it.id == form.productId }.map { PickOption(it.id, it.name, it.barcodes.firstOrNull()) },
      selected = form.productId,
      onPick = { id -> d.products.find { it.id == id }?.let { p -> form = PurchaseFormModel.pickProduct(form, p) } },
      onDismiss = { picker = null },
    )
    null -> Unit
  }
}

/** خطِ زندهٔ زیرِ کادرها («جمع خرید»، «باقی‌مانده») */
@Composable
private fun LiveLine(label: String, value: String, tone: LedgerTone = LedgerTone.NEUTRAL) {
  val c = PhoneV3.colors
  Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
    Text(label, style = v3Text(13, FontWeight.Medium, 18), color = c.inkMuted, modifier = Modifier.weight(1f))
    Text(value, style = v3Text(15, FontWeight.Bold, 20), color = if (tone == LedgerTone.NEUTRAL) c.ink else tone.ink())
  }
}
