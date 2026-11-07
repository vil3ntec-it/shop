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
import androidx.compose.runtime.LaunchedEffect
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
import ir.vil3ntec.tohid.core.i18n.StrPurchasePages
import ir.vil3ntec.tohid.core.i18n.StrStock
import ir.vil3ntec.tohid.core.purchase.SupplierPricing
import ir.vil3ntec.tohid.data.LedgerEngine
import ir.vil3ntec.tohid.data.PurchaseBook
import ir.vil3ntec.tohid.data.PurchaseOrderBook
import ir.vil3ntec.tohid.formatDate
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.screens.v3Text
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Button
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
  //  هشت کادرِ ردیفِ نخست (با انقضا)، سپس چهارتایی‌های ردیف‌های افزوده (PUR-05)، و در پایان دوتاییِ «هزینهٔ جانبی» و
  //  «سررسید» — چهارتایی نیست، پس `chunked(4)` ِ ردیف‌ها کنارش می‌گذارد و همان‌جا خوانده می‌شود
  save = { listOf(it.supplierId.orEmpty(), it.productId.orEmpty(), it.quantity, it.price, it.paid, it.date, it.notes, it.expiry) +
    it.more.flatMap { r -> listOf(r.productId.orEmpty(), r.quantity, r.price, r.expiry) } + listOf(it.landed, it.due) },
  restore = { v ->
    PurchaseForm(v[0].ifEmpty { null }, v[1].ifEmpty { null }, v[2], v[3], v[4], v[5], v[6], expiry = v[7],
      more = v.drop(8).chunked(4).filter { it.size == 4 }.map { r -> PurchaseRow(r[0].ifEmpty { null }, r[1], r[2], r[3]) })
      .let { f -> if ((v.size - 8) % 4 == 2) f.copy(landed = v[v.size - 2], due = v[v.size - 1]) else f }
  },
)

/** PUR-05: تاریخِ انقضای محموله — همان کادرِ «ورودِ کالا» ِ انبار (شمسی، اختیاری) */
@Composable
private fun ExpiryField(value: String, onValue: (String) -> Unit, error: String?) {
  V3TextField(
    value, onValue, StrStock.expiry, Modifier.fillMaxWidth(),
    kind = V3FieldKind.DATE,
    placeholder = StrStock.datePlaceholder,
    helper = PurchaseFormModel.expiryIso(value)?.takeIf { it.isNotEmpty() }?.let(::formatDate) ?: StrStock.dateHelper,
    error = error,
  )
}

/**
 *  «ثبت خرید» (`purchasing/new?supplier=`، صفحه): گروه‌های «تأمین‌کننده» (پیش‌پُر از حسابِ همان
 *  تأمین‌کننده)، «کالا» (انبارِ خالی ⇒ راهنما و پیوندِ «کالای تازه»)، «مقدار و قیمت» با «جمع خرید»
 *  ِ زنده، «پرداخت» (خالی یعنی نسیه) با «باقی‌مانده» ِ زنده، تاریخ و توضیحات. دکمهٔ چسبانِ «ثبت خرید»؛
 *  نوشتهٔ ذخیره‌نشده ⇒ «دور ریخته شود؟». ثبت = `LedgerEngine.addPurchase` (اثرِ انبار همان امروز).
 *  PUR-05: «افزودنِ ردیفِ کالا» ⇒ فاکتورِ چندردیفه (کالا، مقدار و قیمتِ هر ردیف، یک پرداخت برای کل) و ثبت با همان
 *  `PurchaseBook.record` ِ فاکتورِ چندردیفهٔ کامپیوتر ([onSaveMany])؛ بی ردیفِ افزوده همان راهِ تک‌ردیفهٔ امروز.
 */
@Composable
internal fun PurchaseFormScreen(
  d: ShopData,
  supplierArg: String?,
  onBack: () -> Unit,
  onNewProduct: () -> Unit,
  /** `true` ⇐ موتور پذیرفت (صفحه بسته می‌شود)؛ ردِ موتور پیامِ خودش را دارد */
  onSave: (LedgerEngine.PurchaseDraft) -> Boolean,
  /**
   *  PUR-05: فاکتورِ دفترِ خرید (تأمین‌کننده، ردیف‌ها، پرداخت، تاریخ، توضیح، هزینهٔ جانبی و سررسید) ⇒ `PurchaseBook.record`؛
   *  `true` ⇐ پذیرفته
   */
  onSaveMany: (String, List<PurchaseBook.Line>, Double, String, String, PurchaseFormModel.Extras) -> Boolean,
  /**
   *  موتورِ تازهٔ موجودی (`StockLedger.isOn`) ⇒ کادرهای «هزینهٔ جانبی» (PUR-06) و «سررسیدِ پرداخت» (PUR-07) — همان شرطِ
   *  فرمِ خریدِ کامپیوتر (`DeskPurchasing`)؛ خاموش ⇒ همان فرمِ پیشین
   */
  extrasOn: Boolean = false,
) {
  val initial = remember(supplierArg) { PurchaseFormModel.initial(supplierArg?.takeIf { id -> d.suppliers.any { it.id == id } }, todayIso()) }
  //  نوشته‌ها با چرخشِ گوشی و برگشت از «کالای تازه» نمی‌روند
  var form by rememberSaveable(supplierArg, stateSaver = PurchaseFormSaver) { mutableStateOf(initial) }
  var tried by rememberSaveable { mutableStateOf(false) }
  //  کادرهای پنهان (موتورِ تازه خاموش) هیچ اثری ندارند — نوشتهٔ برگشته از چرخشِ صفحه هم پاک می‌شود
  LaunchedEffect(extrasOn) { if (!extrasOn && (form.landed.isNotEmpty() || form.due.isNotEmpty())) form = form.copy(landed = "", due = "") }
  var shake by remember { mutableStateOf(0) }
  var picker by remember { mutableStateOf<PurchasePicker?>(null) }
  //  کالای کدام ردیف انتخاب می‌شود: -1 = ردیفِ نخست، وگرنه شمارهٔ ردیفِ افزوده (PUR-05)
  var pickRow by remember { mutableStateOf(-1) }
  val errors = PurchaseFormModel.errors(form)
  val shownErrors = if (tried) errors else PurchaseFormModel.Errors()
  val product = d.products.find { it.id == form.productId }
  val supplier = d.suppliers.find { it.id == form.supplierId }
  val total = PurchaseFormModel.total(form)
  //  PUR-09: آخرین فیِ همین تأمین‌کننده برای همین کالا، زیرِ «قیمت هر واحد»
  val buys = remember(d) { PurchaseOrderBook.buys(d) }
  val lastPrice = remember(buys, form.productId, form.supplierId) {
    val pid = form.productId; val sid = form.supplierId
    if (pid == null || sid == null) null
    else SupplierPricing.last(buys, pid, sid)?.let { p ->
      StrMoney.lastSupplierPrice(MoneyText.afn(PurchaseOrderBook.asAfn(p.priceMinor)), formatDate(p.date))
    } ?: StrMoney.noSupplierBuyYet
  }

  V3Form(
    title = StrMoney.newPurchase,
    primary = StrMoney.newPurchase,
    onPrimary = {
      tried = true
      if (errors.any) shake++
      else if (!PurchaseFormModel.multi(form)) { if (onSave(PurchaseFormModel.draft(form, product?.unit.orEmpty()))) onBack() }
      else if (onSaveMany(
          form.supplierId.orEmpty(),
          PurchaseFormModel.lines(form) { id -> d.products.find { it.id == id }?.unit.orEmpty() },
          PurchaseFormModel.paid(form), form.date, form.notes.trim(), PurchaseFormModel.extras(form, extrasOn),
        )) onBack()
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
            onClick = { pickRow = -1; picker = PurchasePicker.PRODUCT },
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
          helper = lastPrice,
          error = shownErrors.price,
        )
        Spacer(Modifier.height(12.dp))
        ExpiryField(form.expiry, { form = form.copy(expiry = it) }, shownErrors.expiry)
        LiveLine(StrMoney.purchaseTotal, MoneyText.afn(total))
      }
      //  PUR-05: ردیف‌های افزوده — هر کدام کالا، مقدار و قیمتِ خودش
      form.more.forEachIndexed { i, row ->
        val rowProduct = d.products.find { it.id == row.productId }
        val rowErrors = if (tried) shownErrors.rows.getOrNull(i) ?: PurchaseFormModel.RowErrors() else PurchaseFormModel.RowErrors()
        V3Section(StrMoney.purchaseRow(i + 2), padding = PaddingValues(16.dp)) {
          V3PickerRow(
            label = StrMoney.item,
            value = rowProduct?.name,
            placeholder = StrMoney.pickItem,
            icon = Icons.Outlined.Inventory2,
            error = rowErrors.product,
            onClick = { pickRow = i; picker = PurchasePicker.PRODUCT },
          )
          Spacer(Modifier.height(12.dp))
          V3TextField(
            value = row.quantity,
            onValueChange = { form = PurchaseFormModel.setRow(form, i, row.copy(quantity = it)) },
            label = StrMoney.quantity,
            kind = V3FieldKind.NUMBER,
            required = true,
            suffix = rowProduct?.unit?.takeIf { it.isNotBlank() },
            error = rowErrors.quantity,
          )
          Spacer(Modifier.height(12.dp))
          V3TextField(
            value = row.price,
            onValueChange = { form = PurchaseFormModel.setRow(form, i, row.copy(price = it)) },
            label = StrMoney.unitPrice,
            kind = V3FieldKind.MONEY,
            required = true,
            error = rowErrors.price,
          )
          Spacer(Modifier.height(12.dp))
          ExpiryField(row.expiry, { form = PurchaseFormModel.setRow(form, i, row.copy(expiry = it)) }, rowErrors.expiry)
          Spacer(Modifier.height(12.dp))
          V3Button(StrMoney.removePurchaseRow, { form = PurchaseFormModel.removeRow(form, i) }, Modifier.fillMaxWidth(), kind = V3ButtonKind.NEUTRAL)
        }
      }
      if (d.products.isNotEmpty() && form.more.size + 1 < PurchaseFormModel.MAX_ROWS) {
        V3Button(
          StrMoney.addPurchaseRow, { form = PurchaseFormModel.addRow(form) },
          Modifier.fillMaxWidth().padding(vertical = 8.dp), kind = V3ButtonKind.SECONDARY,
        )
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
        if (extrasOn) {
          //  PUR-06: کرایه/گمرک روی بهای ردیف‌ها (نه بدهیِ تأمین‌کننده)؛ PUR-07: سررسیدِ کلِ فاکتور برای یادآورِ بدهی
          Spacer(Modifier.height(12.dp))
          V3TextField(
            value = form.landed,
            onValueChange = { form = form.copy(landed = it) },
            label = StrPurchasePages.landedAfn,
            kind = V3FieldKind.MONEY,
            helper = StrPurchasePages.landedNote,
            error = shownErrors.landed,
          )
          Spacer(Modifier.height(12.dp))
          V3TextField(
            form.due, { form = form.copy(due = it) }, StrPurchasePages.payDue, Modifier.fillMaxWidth(),
            kind = V3FieldKind.DATE,
            placeholder = StrStock.datePlaceholder,
            helper = PurchaseFormModel.dueIso(form)?.takeIf { it.isNotEmpty() }?.let(::formatDate) ?: StrPurchasePages.optionalReminder,
            error = shownErrors.due,
          )
        }
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
    PurchasePicker.PRODUCT -> {
      val current = if (pickRow < 0) form.productId else form.more.getOrNull(pickRow)?.productId
      IdPickerSheet(
        title = StrMoney.item,
        options = d.products.filter { it.active || it.id == current }.map { PickOption(it.id, it.name, it.barcodes.firstOrNull()) },
        selected = current,
        onPick = { id ->
          d.products.find { it.id == id }?.let { p ->
            form = if (pickRow < 0) PurchaseFormModel.pickProduct(form, p) else PurchaseFormModel.pickRowProduct(form, pickRow, p)
          }
        },
        onDismiss = { picker = null },
      )
    }
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
