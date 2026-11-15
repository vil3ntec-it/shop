package ir.vil3ntec.tohid.ui.v3.money

import ir.vil3ntec.tohid.core.i18n.StrPurchases
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.ui.v3.V3Segmented
import ir.vil3ntec.tohid.money
import androidx.compose.material.icons.outlined.QrCodeScanner
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrMoney
import ir.vil3ntec.tohid.core.i18n.StrPurchasePages
import ir.vil3ntec.tohid.core.i18n.StrPurchaseUnits
import ir.vil3ntec.tohid.core.i18n.StrStock
import ir.vil3ntec.tohid.core.purchase.SupplierPricing
import ir.vil3ntec.tohid.data.LedgerEngine
import ir.vil3ntec.tohid.data.PurchaseBook
import ir.vil3ntec.tohid.data.PurchaseOrderBook
import ir.vil3ntec.tohid.formatDate
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.scan.ScanRouter
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.screens.purchaseUnitsOn
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
import ir.vil3ntec.tohid.ui.v3.V3Type
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

/**
 *  e-rows-63b: «پرداخت» و «الان رسیده؟» کنارِ قالبِ [PurchaseFormUnitsSaver] (که دست نمی‌خورد):
 *  `[قالبِ پیشین…] + [نامِ گزینهٔ پرداخت یا خالی، «1»/«0» ِ نرسیده] + [اندازهٔ قالبِ پیشین]`.
 */
private val PurchaseFormFlowSaver = listSaver<PurchaseForm, String>(
  save = { f ->
    @Suppress("UNCHECKED_CAST")
    val base = with(PurchaseFormUnitsSaver) { save(f) } as List<String>
    base + listOf(f.pay?.name.orEmpty(), if (f.later) "1" else "0", base.size.toString())
  },
  restore = { v ->
    val n = v.lastOrNull()?.toIntOrNull()?.takeIf { it + 3 == v.size }
    if (n == null) PurchaseFormUnitsSaver.restore(v)
    else PurchaseFormUnitsSaver.restore(v.take(n))?.copy(pay = PurchasePay.entries.firstOrNull { it.name == v[n] }, later = v[n + 1] == "1")
  },
)

/**
 *  PRD-07: واحدِ خریدِ ردیفِ نخست و هر ردیفِ افزوده کنارِ همان قالبِ [PurchaseFormSaver] (که دست نمی‌خورد):
 *  `[قالبِ پیشین…] + [واحدِ ردیفِ نخست، واحدِ هر ردیف…] + [اندازهٔ قالبِ پیشین]`؛ خالی = واحدِ پایه.
 */
private val PurchaseFormUnitsSaver = listSaver<PurchaseForm, String>(
  save = { f ->
    @Suppress("UNCHECKED_CAST")
    val base = with(PurchaseFormSaver) { save(f) } as List<String>
    base + listOf(f.unitName.orEmpty()) + f.more.map { it.unitName.orEmpty() } + listOf(base.size.toString())
  },
  restore = { v ->
    val n = v.lastOrNull()?.toIntOrNull()?.takeIf { it in 1 until v.size }
    val f = PurchaseFormSaver.restore(if (n == null) v else v.take(n))
    val u = if (n == null) emptyList() else v.subList(n, v.size - 1)
    f?.copy(unitName = u.getOrNull(0)?.ifEmpty { null }, more = f.more.mapIndexed { i, r -> r.copy(unitName = u.getOrNull(i + 1)?.ifEmpty { null }) })
  },
)

/**
 *  PRD-07: واحدِ خریدِ یک ردیف — همان تراشه‌های سفارشِ خرید ([PoUnitChips]) — و زیرش مقدارِ پایه و بهای هر واحدِ پایه از
 *  همان حسابِ ثبت (`PurchaseFormModel.unitLine`)؛ کالای بی واحدِ خرید ⇒ هیچ.
 */
@Composable
private fun RowUnit(d: ShopData, productId: String?, unitName: String?, quantity: String, price: String, error: String?, onPick: (String?) -> Unit) {
  val units = PurchaseFormModel.units(d, productId)
  if (units.isEmpty()) return
  PoUnitChips(units, d.products.find { it.id == productId }?.unit.orEmpty(), unitName, onPick)
  PurchaseFormModel.unitLine(d, productId, unitName, quantity, price)?.let {
    Text(it, style = V3Type.footnote, color = PhoneV3.colors.inkMuted, modifier = Modifier.padding(top = 4.dp))
  }
  error?.let { V3Message(V3Tone.ERROR, it, modifier = Modifier.padding(top = 8.dp)) }
  Spacer(Modifier.height(12.dp))
}

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
 *  نوشتهٔ ذخیره‌نشده ⇒ «دور ریخته شود؟».
 *  PUR-05: «افزودنِ ردیفِ کالا» ⇒ فاکتورِ چندردیفه (کالا، مقدار و قیمتِ هر ردیف، یک پرداخت برای کل).
 *  e-rows-63b (سادهٔ صاحب‌کار): تأمین‌کننده یا کالای تازه از همان برگهٔ انتخاب، افزودن با اسکن (دوربین و بارکدخوان)، «پرداخت»
 *  با سه گزینه (کامل / بخشی / بعداً با سررسید) و «الان رسیده؟». رسیده ⇒ **همیشه** یک فاکتورِ شماره‌دار با `PurchaseBook.record`
 *  ([onSaveMany]، اثرِ هر ردیف همان `addPurchase`: انبار، حرکت، بدهی، بهای خرید)، با یک کلید برای همین فرم (دو لمس یک خرید)؛
 *  نرسیده ⇒ سفارشِ خرید ([onSaveLater]) و کالا با «دریافتِ کالا» می‌آید.
 */
@Composable
internal fun PurchaseFormScreen(
  d: ShopData,
  supplierArg: String?,
  onBack: () -> Unit,
  onNewProduct: () -> Unit,
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
  /** پیامِ بارکدخوان (بارکدِ ناشناخته، واحدِ خریدِ خوانده‌شده، فاکتورِ پر) — صفحه آن را در نوارِ پایین نشان می‌دهد */
  notify: (V3Tone, String) -> Unit = { _, _ -> },
  /** e-rows-63b: «+ تأمین‌کنندهٔ تازه» در برگهٔ انتخاب ⇒ فرمِ تأمین‌کننده؛ شناسهٔ ساخته‌شده (یا null) برمی‌گردد */
  onNewSupplier: ((String?) -> Unit) -> Unit = {},
  /** e-rows-63b: دکمهٔ «اسکنِ بارکد» (دوربین) ⇒ همان افزودنِ ردیفِ بارکدخوان؛ null ⇒ نیست */
  onScan: (((String) -> Unit) -> Unit)? = null,
  /** e-rows-63b: «الان رسیده؟ نه» شدنی است؟ (پرچمِ سفارشِ خرید روشن، اینترنت، نقشِ مدیر) */
  laterOn: Boolean = false,
  /** e-rows-63b: کالای نرسیده ⇒ سفارشِ خرید (و پیش‌پرداخت)؛ پاسخ با `true` ⇐ فرستاده شد */
  onSaveLater: (PurchaseForm, (Boolean) -> Unit) -> Unit = { _, done -> done(false) },
) {
  val initial = remember(supplierArg) { PurchaseFormModel.initial(supplierArg?.takeIf { id -> d.suppliers.any { it.id == id } }, todayIso()) }
  //  نوشته‌ها با چرخشِ گوشی و برگشت از «کالای تازه» نمی‌روند
  var form by rememberSaveable(supplierArg, stateSaver = PurchaseFormFlowSaver) { mutableStateOf(initial) }
  var busy by remember { mutableStateOf(false) }
  var tried by rememberSaveable { mutableStateOf(false) }
  //  کادرهای پنهان (موتورِ تازه خاموش) هیچ اثری ندارند — نوشتهٔ برگشته از چرخشِ صفحه هم پاک می‌شود
  LaunchedEffect(extrasOn) {
    if (!extrasOn && form.landed.isNotEmpty()) form = form.copy(landed = "")
    //  سررسید با «بخشی/بعداً» همه‌جا پیداست (e-rows-63b)؛ فقط کادرِ پنهان پاک می‌شود
    if (!PurchaseFormModel.dueShown(form, extrasOn) && form.due.isNotEmpty()) form = form.copy(due = "")
  }
  //  PRD-07: واحدِ خریدِ ردیف فقط با `ShopPolicy.productTools` (همان فاکتور و سفارشِ خریدِ کامپیوتر)؛ خاموش ⇒ همه به واحدِ پایه،
  //  پس ردیف‌ها و بدنهٔ فاکتور همان پیشین‌اند
  val unitsOn = purchaseUnitsOn()
  LaunchedEffect(unitsOn) { if (!unitsOn && PurchaseFormModel.hasUnits(form)) form = PurchaseFormModel.withoutUnits(form, d) }
  //  بارکدخوانِ وصل یا گوشیِ جفت‌شده تا این صفحه باز است همین‌جا (نه صندوق) — همان راهِ دریافتِ سفارشِ خرید؛ فقط با همان
  //  پرچم (خاموش ⇒ همان صفحهٔ پیشین، بی بارکدخوان)
  val barcodes = remember(d) { ShopStore.barcodeIndex(d) }
  val unitIndex = remember(d, unitsOn) { if (unitsOn) ShopStore.purchaseUnitIndex(d) else null }
  val onScanCode by rememberUpdatedState<(String) -> Unit> { code ->
    when (val r = PurchaseFormModel.scan(form, d, code, barcodes, unitIndex)) {
      is PurchaseFormModel.Scan.Added -> { form = r.form; r.unit?.let { notify(V3Tone.SUCCESS, StrPurchaseUnits.scanned(r.name, it)) } }
      is PurchaseFormModel.Scan.Unknown -> notify(V3Tone.ERROR, StrPurchasePages.barcodeUnknown(r.code))
      PurchaseFormModel.Scan.Full -> notify(V3Tone.ERROR, StrMoney.purchaseRowsFull(PurchaseFormModel.MAX_ROWS))
    }
  }
  val scanOwner = remember { Any() }
  //  e-rows-63b: «افزودن با اسکن» همیشه (پیش‌تر فقط با پرچمِ واحدِ خرید): بارکدخوانِ وصل تا این صفحه باز است همین‌جا
  DisposableEffect(Unit) {
    ScanRouter.register(scanOwner) { code -> onScanCode(code); true }
    onDispose { ScanRouter.unregister(scanOwner) }
  }
  //  کالای تازه از همین فرم (فرمِ «کالای جدید» دست نمی‌خورد): کالایی که پس از رفتن به آن فرم پیدا شد، ردیفِ همان‌جا می‌شود
  var waitingProduct by remember { mutableStateOf<Set<String>?>(null) }
  var pickRowAtAdd by remember { mutableStateOf(-1) }
  LaunchedEffect(d.products) {
    val before = waitingProduct ?: return@LaunchedEffect
    val added = d.products.filter { it.id !in before }
    if (added.size == 1) {
      val p = added.single()
      form = if (pickRowAtAdd < 0) PurchaseFormModel.pickProduct(form, p, unitsOn) else PurchaseFormModel.pickRowProduct(form, pickRowAtAdd, p, unitsOn)
    }
    if (added.isNotEmpty()) waitingProduct = null
  }
  var shake by remember { mutableStateOf(0) }
  var picker by remember { mutableStateOf<PurchasePicker?>(null) }
  //  کالای کدام ردیف انتخاب می‌شود: -1 = ردیفِ نخست، وگرنه شمارهٔ ردیفِ افزوده (PUR-05)
  var pickRow by remember { mutableStateOf(-1) }
  val errors = PurchaseFormModel.errors(form, d)
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
      when {
        busy -> Unit
        errors.any -> shake++
        //  e-rows-63b: نرسیده ⇒ سفارشِ خرید (سرور)؛ بی آن پیامِ «روشن نیست» و هیچ ثبت
        form.later && !laterOn -> { shake++; notify(V3Tone.ERROR, StrPurchases.laterOff) }
        form.later -> { busy = true; onSaveLater(form) { ok -> busy = false; if (ok) onBack() } }
        //  e-rows-63b: هر خرید یک فاکتورِ شماره‌دار (`PurchaseBook.record`) — تک‌ردیفه هم، تا «خریدِ #n» و «پرداخت» ِ همان خرید
        //  (یادداشتِ `PurchaseBook.payNote`) کار کنند؛ اثرها همان `addPurchase` ِ هر ردیف است
        onSaveMany(
          form.supplierId.orEmpty(),
          PurchaseFormModel.lines(form) { id -> d.products.find { it.id == id }?.unit.orEmpty() },
          PurchaseFormModel.paid(form), form.date, form.notes.trim(), PurchaseFormModel.extras(form, extrasOn),
        ) -> onBack()
      }
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
          suffix = (form.unitName ?: product?.unit)?.takeIf { it.isNotBlank() },
          error = shownErrors.quantity,
        )
        Spacer(Modifier.height(12.dp))
        V3TextField(
          value = form.price,
          onValueChange = { form = form.copy(price = it) },
          label = form.unitName?.let(StrPurchaseUnits::priceIn) ?: StrMoney.unitPrice,
          kind = V3FieldKind.MONEY,
          required = true,
          helper = lastPrice,
          error = shownErrors.price,
        )
        Spacer(Modifier.height(12.dp))
        if (unitsOn) RowUnit(d, form.productId, form.unitName, form.quantity, form.price, shownErrors.unit) { u ->
          form = PurchaseFormModel.pickUnit(form, d, -1, u)
        }
        //  کالای نرسیده انقضایش هنگامِ «دریافتِ کالا» ثبت می‌شود
        if (!form.later) ExpiryField(form.expiry, { form = form.copy(expiry = it) }, shownErrors.expiry)
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
            suffix = (row.unitName ?: rowProduct?.unit)?.takeIf { it.isNotBlank() },
            error = rowErrors.quantity,
          )
          Spacer(Modifier.height(12.dp))
          V3TextField(
            value = row.price,
            onValueChange = { form = PurchaseFormModel.setRow(form, i, row.copy(price = it)) },
            label = row.unitName?.let(StrPurchaseUnits::priceIn) ?: StrMoney.unitPrice,
            kind = V3FieldKind.MONEY,
            required = true,
            error = rowErrors.price,
          )
          Spacer(Modifier.height(12.dp))
          if (unitsOn) RowUnit(d, row.productId, row.unitName, row.quantity, row.price, rowErrors.unit) { u ->
            form = PurchaseFormModel.pickUnit(form, d, i, u)
          }
          if (!form.later) ExpiryField(row.expiry, { form = PurchaseFormModel.setRow(form, i, row.copy(expiry = it)) }, rowErrors.expiry)
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
      //  e-rows-63b: افزودنِ ردیف با دوربین — همان راهِ بارکدخوان (`PurchaseFormModel.scan`)
      if (onScan != null && d.products.isNotEmpty()) {
        V3Button(
          StrKit.scanBarcode, { onScan { code -> onScanCode(code) } },
          Modifier.fillMaxWidth().padding(bottom = 8.dp), kind = V3ButtonKind.SECONDARY, icon = Icons.Outlined.QrCodeScanner,
        )
      }
      //  e-rows-63b: «الان رسیده؟» — بله ⇒ فاکتورِ دفتر (کالا همین حالا به انبار)؛ نه ⇒ سفارشِ خرید و «دریافتِ کالا» ِ بعدی
      V3Section(StrPurchases.receivedNow, padding = PaddingValues(16.dp)) {
        V3Segmented(
          listOf(StrPurchases.yesReceived, StrPurchases.laterReceived), if (form.later) 1 else 0,
          { i -> form = PurchaseFormModel.setLater(form, i == 1) },
        )
        if (form.later) {
          Spacer(Modifier.height(10.dp))
          V3Message(if (laterOn) V3Tone.INFO else V3Tone.WARNING, if (laterOn) StrPurchases.laterHow else StrPurchases.laterOff)
        }
      }
      V3Section(if (form.later) StrPurchases.prepay else StrMoney.paidSection, padding = PaddingValues(16.dp)) {
        //  e-rows-63b: سه گزینهٔ روشن به‌جای «خالی یعنی نسیه»
        val modes = PurchasePay.entries
        V3Segmented(
          listOf(StrPurchases.payFull, StrPurchases.payPart, StrPurchases.payLater),
          modes.indexOf(form.pay ?: PurchasePay.PART),
          { i -> form = PurchaseFormModel.setPay(form, modes[i]) },
        )
        Spacer(Modifier.height(12.dp))
        when {
          PurchaseFormModel.typesPaid(form) -> V3TextField(
            value = form.paid,
            onValueChange = { form = form.copy(paid = it) },
            label = StrMoney.paidField,
            kind = V3FieldKind.MONEY,
            helper = if (form.pay == null) StrMoney.paidHelper else null,
            error = shownErrors.paid,
          )
          form.pay == PurchasePay.FULL -> Text(StrPurchases.paidAll(money(total)), style = V3Type.subhead, color = PhoneV3.colors.inkMuted)
          else -> Text(StrPurchases.laterNote, style = V3Type.subhead, color = PhoneV3.colors.inkMuted)
        }
        val left = PurchaseFormModel.remaining(form)
        LiveLine(StrMoney.remainingLabel, MoneyText.afn(left), if (left > 0) LedgerTone.OWE else LedgerTone.SETTLED)
        Spacer(Modifier.height(12.dp))
        V3DateField(form.date, { form = form.copy(date = it) }, StrMoney.date)
        if (extrasOn && !form.later) {
          //  PUR-06: کرایه/گمرک روی بهای ردیف‌ها (نه بدهیِ تأمین‌کننده)
          Spacer(Modifier.height(12.dp))
          V3TextField(
            value = form.landed,
            onValueChange = { form = form.copy(landed = it) },
            label = StrPurchasePages.landedAfn,
            kind = V3FieldKind.MONEY,
            helper = StrPurchasePages.landedNote,
            error = shownErrors.landed,
          )
        }
        if (PurchaseFormModel.dueShown(form, extrasOn)) {
          //  PUR-07: سررسیدِ کلِ فاکتور برای یادآورِ بدهی — با «بخشی/بعداً» همه‌جا (e-rows-63b)
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
      addLabel = StrMoney.newSupplier,
      onAdd = { onNewSupplier { id -> if (id != null) form = form.copy(supplierId = id) } },
    )
    PurchasePicker.PRODUCT -> {
      val current = if (pickRow < 0) form.productId else form.more.getOrNull(pickRow)?.productId
      IdPickerSheet(
        title = StrMoney.item,
        options = d.products.filter { it.active || it.id == current }.map { PickOption(it.id, it.name, it.barcodes.firstOrNull()) },
        selected = current,
        onPick = { id ->
          d.products.find { it.id == id }?.let { p ->
            form = if (pickRow < 0) PurchaseFormModel.pickProduct(form, p, unitsOn) else PurchaseFormModel.pickRowProduct(form, pickRow, p, unitsOn)
          }
        },
        onDismiss = { picker = null },
        //  کالای تازه: همان فرمِ «کالای جدید» (۶۳k) — پس از ذخیره، همان ردیف همان کالا را می‌گیرد
        addLabel = StrMoney.addProduct,
        onAdd = { waitingProduct = d.products.mapTo(HashSet()) { it.id }; pickRowAtAdd = pickRow; onNewProduct() },
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
