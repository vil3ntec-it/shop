package ir.vil3ntec.tohid.ui.v3.money

import ir.vil3ntec.tohid.core.i18n.StrPurchases
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.ui.v3.V3Segmented
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import ir.vil3ntec.tohid.ui.screens.isPrimaryModifier
import ir.vil3ntec.tohid.ui.v3.nav.LocalDeskLayout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrPurchaseWorkflow as Workflow
import ir.vil3ntec.tohid.core.i18n.StrSheets
import ir.vil3ntec.tohid.core.i18n.StrMoney
import ir.vil3ntec.tohid.core.i18n.StrPurchaseFx
import ir.vil3ntec.tohid.core.i18n.StrSafeCur
import ir.vil3ntec.tohid.data.SafeCurrencies
import ir.vil3ntec.tohid.data.PoForms
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.ui.v3.V3Chip
import ir.vil3ntec.tohid.ui.v3.V3ChipRow
import androidx.compose.material.icons.outlined.CurrencyExchange
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
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3DateField
import ir.vil3ntec.tohid.ui.v3.V3FieldKind
import ir.vil3ntec.tohid.ui.v3.V3Form
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3PickerRow
import ir.vil3ntec.tohid.ui.v3.V3Section
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.v3Shake

private enum class PurchasePicker { SUPPLIER, PRODUCT, CURRENCY }

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
 *  PUR-11: ارز، نرخ و واحدِ نرخِ فاکتور کنارِ قالبِ [PurchaseFormFlowSaver] (که دست نمی‌خورد):
 *  `[قالبِ پیشین…] + [کدِ ارز یا خالی، نرخ، per] + [اندازهٔ قالبِ پیشین]`.
 */
private val PurchaseFormFxSaver = listSaver<PurchaseForm, String>(
  save = { f ->
    @Suppress("UNCHECKED_CAST")
    val base = with(PurchaseFormFlowSaver) { save(f) } as List<String>
    base + listOf(f.currency.orEmpty(), f.rate, f.per.toString(), base.size.toString())
  },
  restore = { v ->
    val n = v.lastOrNull()?.toIntOrNull()?.takeIf { it + 4 == v.size }
    if (n == null) PurchaseFormFlowSaver.restore(v)
    else PurchaseFormFlowSaver.restore(v.take(n))?.copy(currency = v[n].ifEmpty { null }, rate = v[n + 1], per = v[n + 2].toLongOrNull() ?: 1L)
  },
)

/** Append packaging metadata with a marker so previously saved forms still restore. */
private val PurchaseFormPackagingSaver = listSaver<PurchaseForm, String>(
  save = { form ->
    @Suppress("UNCHECKED_CAST")
    val base = with(PurchaseFormFxSaver) { save(form) } as List<String>
    base + (listOf(form.packaging) + form.more.map { it.packaging }).flatMap {
      listOf(if (it.carton) "1" else "0", it.pieces, it.production, it.barcode)
    } + listOf(base.size.toString(), "packages")
  },
  restore = { values ->
    if (values.lastOrNull() != "packages") PurchaseFormFxSaver.restore(values)
    else {
      val end = values[values.size - 2].toInt()
      val packs = values.subList(end, values.size - 2).chunked(4).map {
        PurchasePackaging(it[0] == "1", it[1], it[2], it[3])
      }
      PurchaseFormFxSaver.restore(values.take(end))?.let { f ->
        f.copy(packaging = packs.first(), more = f.more.zip(packs.drop(1)) { row, pack -> row.copy(packaging = pack) })
      }
    }
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
private fun RowUnit(d: ShopData, productId: String?, unitName: String?, quantity: String, price: String, error: String?, form: PurchaseForm, onPick: (String?) -> Unit) {
  val units = PurchaseFormModel.units(d, productId)
  if (units.isEmpty()) return
  PoUnitChips(units, d.products.find { it.id == productId }?.unit.orEmpty(), unitName, onPick)
  PurchaseFormModel.unitLine(d, productId, unitName, quantity, price, form)?.let {
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
  onCategory: ((String?, (String?) -> Unit) -> Unit)? = null,
  /**
   *  PUR-05: فاکتورِ دفترِ خرید (تأمین‌کننده، ردیف‌ها، پرداخت، تاریخ، توضیح، هزینهٔ جانبی و سررسید) ⇒ `PurchaseBook.record`؛
   *  `true` ⇐ پذیرفته
   */
  onSaveMany: suspend (String, List<PurchaseBook.Line>, Double, String, String, PurchaseFormModel.Extras) -> Boolean,
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
  /** PUR-12/AI-10: «از عکسِ فاکتور» (`ui/screens/InvoiceOcrUi.kt`)؛ null یا پرچمِ خاموش ⇒ نه دکمه، نه برگه */
  ocr: ir.vil3ntec.tohid.ui.screens.InvoiceOcrController? = null,
  /**
   *  PUR-11: ارزهای فاکتور (افغانی اول؛ همان فهرستِ ارزهای گاوصندوق + ارزهای دفتر) و نامِ هر کد؛ فقط افغانی ⇒ بی ردیفِ «ارز»
   *  (همان فرمِ پیشین). [lastRate] نرخِ پیش‌پُرِ هر ارز (آخرین نرخِ ثبت‌شده).
   */
  currencies: List<String> = listOf(ir.vil3ntec.tohid.core.purchase.PurchaseFx.AFN),
  currencyName: (String) -> String = { it },
  lastRate: (String) -> ir.vil3ntec.tohid.core.purchase.PurchaseFx.Rate? = { null },
  initialBarcode: String? = null,
) {
  val initial = remember(supplierArg) { PurchaseFormModel.initial(supplierArg?.takeIf { id -> d.suppliers.any { it.id == id } }, todayIso()) }
  //  نوشته‌ها با چرخشِ گوشی و برگشت از «کالای تازه» نمی‌روند
  var form by rememberSaveable(supplierArg, stateSaver = PurchaseFormPackagingSaver) { mutableStateOf(initial) }
  var busy by remember { mutableStateOf(false) }
  val scope = rememberCoroutineScope()
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
    }
  }
  var initialScanHandled by rememberSaveable(initialBarcode) { mutableStateOf(false) }
  LaunchedEffect(initialBarcode) {
    if (!initialScanHandled && !initialBarcode.isNullOrBlank()) {
      initialScanHandled = true
      onScanCode(initialBarcode)
    }
  }
  val scanOwner = remember { Any() }
  //  e-rows-63b: «افزودن با اسکن» همیشه (پیش‌تر فقط با پرچمِ واحدِ خرید): بارکدخوانِ وصل تا این صفحه باز است همین‌جا
  DisposableEffect(Unit) {
    ScanRouter.register(scanOwner) { code -> onScanCode(code); true }
    onDispose { ScanRouter.unregister(scanOwner) }
  }
  //  کالای تازه از همین فرم (فرمِ «کالای جدید» دست نمی‌خورد): کالایی که پس از رفتن به آن فرم پیدا شد، ردیفِ همان‌جا می‌شود
  var waitingProduct by rememberSaveable { mutableStateOf<ArrayList<String>?>(null) }
  var pickRowAtAdd by rememberSaveable { mutableStateOf(-1) }
  LaunchedEffect(d.products) {
    val before = waitingProduct ?: return@LaunchedEffect
    val known = before.toHashSet()
    val added = d.products.filter { it.id !in known }
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
  var deliverySettings by rememberSaveable { mutableStateOf(false) }
  var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }
  val errors = remember(form, d) { PurchaseFormModel.errors(form, d) }
  val shownErrors = if (tried) errors else PurchaseFormModel.Errors()
  val productById = remember(d.products) { d.products.associateBy { it.id } }
  val supplier = d.suppliers.find { it.id == form.supplierId }
  val total = PurchaseFormModel.total(form)
  //  PUR-11: فاکتورِ ارزی — جمع و پرداخت به همان ارز، معادلِ افغانی با نرخِ همین فاکتور
  val fxCode = form.currency
  val fxTotal = PurchaseFormModel.fxTotal(form)
  fun fx(minor: Long): String = SafeCurrencies.format(minor, fxCode ?: ir.vil3ntec.tohid.core.purchase.PurchaseFx.AFN)
  //  PUR-09: آخرین فیِ همین تأمین‌کننده برای همین کالا، زیرِ «قیمت هر واحد»
  val buys = remember(d.purchases) { PurchaseOrderBook.buys(d) }
  val lastPrice = remember(buys, form.productId, form.supplierId) {
    val pid = form.productId; val sid = form.supplierId
    if (pid == null || sid == null) null
    else SupplierPricing.last(buys, pid, sid)?.let { p ->
      StrMoney.lastSupplierPrice(MoneyText.afn(PurchaseOrderBook.asAfn(p.priceMinor)), formatDate(p.date))
    } ?: StrMoney.noSupplierBuyYet
  }

  val submit: () -> Unit = {
    val snapshot = form
    tried = true
    when {
      busy -> Unit
      PurchaseFormModel.errors(snapshot, d).any -> shake++
      //  e-rows-63b: نرسیده ⇒ سفارشِ خرید (سرور)؛ بی آن پیامِ «روشن نیست» و هیچ ثبت
      //  PUR-11: سفارشِ خرید فقط افغانی است
      snapshot.later && snapshot.currency != null -> { shake++; notify(V3Tone.ERROR, StrPurchaseFx.laterAfnOnly) }
      snapshot.later && !laterOn -> { shake++; notify(V3Tone.ERROR, StrPurchases.laterOff) }
      snapshot.later -> { busy = true; onSaveLater(snapshot) { ok -> busy = false; if (ok) onBack() } }
      //  e-rows-63b: هر خرید یک فاکتورِ شماره‌دار (`PurchaseBook.record`) — تک‌ردیفه هم، تا «خریدِ #n» و «پرداخت» ِ همان خرید
      //  (یادداشتِ `PurchaseBook.payNote`) کار کنند؛ اثرها همان `addPurchase` ِ هر ردیف است
      else -> {
        busy = true
        scope.launch {
          try {
            val saved = onSaveMany(
              snapshot.supplierId.orEmpty(),
              PurchaseFormModel.lines(snapshot) { id -> productById[id]?.unit.orEmpty() },
              PurchaseFormModel.paid(snapshot), snapshot.date, snapshot.notes.trim(), PurchaseFormModel.extras(snapshot, extrasOn),
            )
            if (saved) onBack()
          } finally {
            busy = false
          }
        }
      }
    }
  }

  val desktop = LocalDeskLayout.current
  val formModifier = Modifier.v3Shake(shake)
  val keyboardModifier = if (desktop) formModifier.onPreviewKeyEvent { event ->
    val save = event.type == KeyEventType.KeyDown &&
      (event.key == Key.F9 || (event.isPrimaryModifier && (event.key == Key.Enter || event.key == Key.S)))
    if (save) submit()
    save
  } else formModifier
  BackHandler(enabled = busy) {}
  V3Form(
    title = StrMoney.newPurchase,
    primary = StrMoney.newPurchase,
    onPrimary = submit,
    onBack = onBack.takeUnless { busy },
    dirty = PurchaseFormModel.dirty(form, initial),
    summary = when {
      fxCode != null && fxTotal > 0 -> "${StrMoney.purchaseTotal}: ${ir.vil3ntec.tohid.data.SupplierFx.ltr("${fx(fxTotal)} = ${MoneyText.preciseAfn(total)}")}"
      total > 0 -> "${StrMoney.purchaseTotal}: ${MoneyText.preciseAfn(total)}"
      else -> null
    },
    modifier = keyboardModifier,
    loading = busy,
    listContent = {
      item(key = "supplier-and-currency") { Column {
        //  PUR-12/AI-10: آغاز از عکسِ فاکتورِ کاغذی (بالای فرم، پیش از نوشتنِ ردیف‌ها) — فقط ردیف‌های تأییدشده به همین فرم می‌آیند؛
        //  ثبت همان «ثبت» است. پرچمِ خاموش یا بی اجازه ⇒ هیچ
        if (ocr != null && fxCode == null && d.products.isNotEmpty()) {
          ir.vil3ntec.tohid.ui.screens.InvoiceOcrPhoneButton(ocr, Modifier.fillMaxWidth().padding(bottom = 8.dp))
        }
        //  تأمین‌کننده و ارز برای همهٔ ردیف‌های فاکتور
        V3Section(null, padding = PaddingValues(vertical = 4.dp)) {
          V3PickerRow(
            label = StrMoney.supplierLabel,
            value = supplier?.name,
            placeholder = StrMoney.pickSupplier,
            icon = Icons.Outlined.LocalShipping,
            error = shownErrors.supplier,
            onClick = { picker = PurchasePicker.SUPPLIER },
          )
          //  PUR-11: ارزِ فاکتور — فقط وقتی دکان جز افغانی ارزی دارد (ارزهای گاوصندوق)؛ پیش‌فرض افغانی، همان فرمِ پیشین
          if (currencies.size > 1) {
            V3Divider(start = 64.dp)
            V3PickerRow(
              label = StrSafeCur.currency,
              value = currencyName(fxCode ?: ir.vil3ntec.tohid.core.purchase.PurchaseFx.AFN),
              icon = Icons.Outlined.CurrencyExchange,
              onClick = { picker = PurchasePicker.CURRENCY },
            )
          }
        }
        if (fxCode != null) {
          V3Section(StrPurchaseFx.section, padding = PaddingValues(16.dp)) {
            V3TextField(
              value = form.rate,
              onValueChange = { form = form.copy(rate = it) },
              label = StrPurchaseFx.rateLabel(ir.vil3ntec.tohid.core.pay.FxTill.afnText(form.per * 100), fxCode),
              kind = V3FieldKind.MONEY,
              required = true,
              helper = PurchaseFormModel.fxRate(form)?.let { StrPurchaseFx.rateLine(ir.vil3ntec.tohid.core.purchase.PurchaseFx.rateLine(it)) },
              error = shownErrors.rate,
            )
            Spacer(Modifier.height(8.dp))
            Text(StrPurchaseFx.per, style = V3Type.footnote, color = PhoneV3.colors.inkMuted)
            Spacer(Modifier.height(4.dp))
            V3ChipRow(contentPadding = PaddingValues(0.dp)) {
              ir.vil3ntec.tohid.core.pay.Pay.FX_PER.forEach { p ->
                V3Chip(ir.vil3ntec.tohid.data.SupplierFx.ltr("${ir.vil3ntec.tohid.core.pay.FxTill.afnText(p * 100)} $fxCode"), selected = form.per == p, onClick = { form = form.copy(per = p) })
              }
            }
            Spacer(Modifier.height(8.dp))
            Text(StrPurchaseFx.rateNote, style = V3Type.footnote, color = PhoneV3.colors.inkMuted)
          }
        }
      } }
      itemsIndexed(listOf(PurchaseFormModel.first(form)) + form.more) { index, row ->
        val i = index - 1
        val rowErrors = if (i < 0) PurchaseFormModel.RowErrors(
          shownErrors.product, shownErrors.quantity, shownErrors.price, shownErrors.expiry, shownErrors.unit, shownErrors.packaging, shownErrors.production,
        ) else shownErrors.rows.getOrNull(i) ?: PurchaseFormModel.RowErrors()
        PurchaseItemCard(
          row = row, product = productById[row.productId], number = index + 1, errors = rowErrors,
          form = form, d = d, unitsOn = unitsOn, lastPrice = lastPrice.takeIf { i < 0 },
          onChoose = { pickRow = i; picker = PurchasePicker.PRODUCT },
          onChange = { changed ->
            form = if (i < 0) PurchaseFormModel.withFirst(form, changed) else PurchaseFormModel.setRow(form, i, changed)
          },
          onPickUnit = { form = PurchaseFormModel.pickUnit(form, d, i, it) },
          onRemove = if (i >= 0) ({ form = PurchaseFormModel.removeRow(form, i) }) else null,
        )
      }
      item(key = "add-items") {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
          V3Button(StrMoney.addPurchaseRow, { form = PurchaseFormModel.addRow(form) }, Modifier.weight(1f), kind = V3ButtonKind.SECONDARY)
          if (onScan != null) V3Button(StrKit.scanBarcode, { onScan { code -> onScanCode(code) } }, Modifier.weight(1f), kind = V3ButtonKind.NEUTRAL, icon = Icons.Outlined.QrCodeScanner)
        }
      }
      item(key = "purchase-settlement") { Column {
        //  e-rows-63b: «الان رسیده؟» — بله ⇒ فاکتورِ دفتر (کالا همین حالا به انبار)؛ نه ⇒ سفارشِ خرید و «دریافتِ کالا» ِ بعدی
        if (laterOn || extrasOn) ir.vil3ntec.tohid.ui.v3.V3TextButton(Workflow.deliveryOptions, { deliverySettings = !deliverySettings })
        if (deliverySettings && laterOn) V3Section(StrPurchases.receivedNow, padding = PaddingValues(16.dp)) {
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
          if (fxCode != null) {
            LiveLine(StrPurchaseFx.totalIn(fxCode), fx(fxTotal))
            LiveLine(StrPurchaseFx.afnEquivalent, MoneyText.preciseAfn(total))
          } else LiveLine(StrMoney.purchaseTotal, MoneyText.preciseAfn(total))
          Spacer(Modifier.height(12.dp))
          V3Segmented(
            listOf(Workflow.paymentNow, Workflow.paymentLater), if (form.pay == PurchasePay.LATER) 1 else 0,
            { i ->
              if (i == 1) form = PurchaseFormModel.setPay(form, PurchasePay.LATER)
              else if (form.pay == PurchasePay.LATER) form = PurchaseFormModel.setPay(form, PurchasePay.FULL)
            },
          )
          if (form.pay != PurchasePay.LATER) {
            Spacer(Modifier.height(12.dp))
            V3TextField(
              value = if (form.pay == PurchasePay.FULL) {
                if (fxCode != null) PoForms.num(ir.vil3ntec.tohid.core.safe.SafeCurrency.major(fxTotal, fxCode)) else PoForms.num(total)
              } else form.paid,
              onValueChange = { form = form.copy(pay = PurchasePay.PART, paid = it) },
              label = fxCode?.let(StrPurchaseFx::paidIn) ?: Workflow.paidAmount,
              kind = V3FieldKind.MONEY, error = shownErrors.paid,
            )
          }
          val left = PurchaseFormModel.remaining(form)
          if (fxCode != null) {
            val fxLeft = (fxTotal - PurchaseFormModel.fxPaid(form)).coerceAtLeast(0L)
            LiveLine(StrMoney.remainingLabel, ir.vil3ntec.tohid.data.SupplierFx.ltr("${fx(fxLeft)} = ${MoneyText.preciseAfn(left)}"), if (fxLeft > 0) LedgerTone.OWE else LedgerTone.SETTLED)
            Spacer(Modifier.height(6.dp))
            Text(StrPurchaseFx.drawerNote, style = V3Type.footnote, color = PhoneV3.colors.inkMuted)
          } else LiveLine(StrMoney.remainingLabel, MoneyText.preciseAfn(left), if (left > 0) LedgerTone.OWE else LedgerTone.SETTLED)
          Spacer(Modifier.height(12.dp))
          V3DateField(form.date, { form = form.copy(date = it) }, StrMoney.date)
          if (extrasOn && !form.later && (deliverySettings || form.landed.isNotBlank())) {
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
      } }
    },
  )

  if (ocr != null && fxCode == null) {
    ir.vil3ntec.tohid.ui.screens.InvoiceOcrPhoneHost(ocr, d.products) { rows ->
      val (next, added) = PurchaseFormModel.withAccepted(form, rows, d)
      form = next
      if (added > 0) notify(V3Tone.SUCCESS, ir.vil3ntec.tohid.core.i18n.StrInvoiceOcr.added(added))
      if (added < rows.size) notify(V3Tone.WARNING, ir.vil3ntec.tohid.core.i18n.StrInvoiceOcr.notAdded(rows.size - added))
    }
  }

  when (picker) {
    PurchasePicker.SUPPLIER -> IdPickerSheet(
      title = StrMoney.supplierLabel,
      options = d.suppliers.sortedBy { it.name }.map { PickOption(it.id, it.name, listOf(it.phone, it.address).filter(String::isNotBlank).joinToString(" · ").ifBlank { null }) },
      searchAlways = true,
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
        options = d.products.filter { (it.active || it.id == current) && (selectedCategory == null || it.category == selectedCategory) }
          .map { PickOption(it.id, it.name, listOfNotNull(it.category.takeIf(String::isNotBlank), it.barcodes.firstOrNull()).joinToString(" · ")) },
        searchAlways = true,
        header = {
          if (d.productCategories.isNotEmpty()) V3ChipRow {
            V3Chip(Workflow.allCategories, selectedCategory == null, onClick = { selectedCategory = null })
            d.productCategories.forEach { category ->
              V3Chip(category, selectedCategory == category, onClick = { selectedCategory = category })
            }
          }
          onCategory?.let { open ->
            ir.vil3ntec.tohid.ui.v3.V3TextButton(ir.vil3ntec.tohid.core.i18n.StrProducts.categoriesTitle, {
              picker = null
              open(selectedCategory) { category ->
                if (category != null) selectedCategory = category
                picker = PurchasePicker.PRODUCT
              }
            })
          }
        },
        selected = current,
        onPick = { id ->
          d.products.find { it.id == id }?.let { p ->
            form = if (pickRow < 0) PurchaseFormModel.pickProduct(form, p, unitsOn) else PurchaseFormModel.pickRowProduct(form, pickRow, p, unitsOn)
          }
        },
        onDismiss = { picker = null },
        //  کالای تازه: همان فرمِ «کالای جدید» (۶۳k) — پس از ذخیره، همان ردیف همان کالا را می‌گیرد
        addLabel = StrMoney.addProduct,
        onAdd = { waitingProduct = d.products.mapTo(ArrayList()) { it.id }; pickRowAtAdd = pickRow; onNewProduct() },
      )
    }
    PurchasePicker.CURRENCY -> IdPickerSheet(
      title = StrSafeCur.currency,
      options = currencies.map { PickOption(it, currencyName(it), null) },
      selected = fxCode ?: ir.vil3ntec.tohid.core.purchase.PurchaseFx.AFN,
      onPick = { code -> form = PurchaseFormModel.setCurrency(form, code, code?.let(lastRate)) },
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

@Composable
private fun PurchaseItemCard(
  row: PurchaseRow,
  product: Product?,
  number: Int,
  errors: PurchaseFormModel.RowErrors,
  form: PurchaseForm,
  d: ShopData,
  unitsOn: Boolean,
  lastPrice: String?,
  onChoose: () -> Unit,
  onChange: (PurchaseRow) -> Unit,
  onPickUnit: (String?) -> Unit,
  onRemove: (() -> Unit)?,
) {
  val pack = row.packaging
  var details by rememberSaveable(row.productId, number) { mutableStateOf(false) }
  val gap = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)
  V3Section(StrMoney.purchaseRow(number), padding = PaddingValues(14.dp)) {
    Column(verticalArrangement = gap) {
      V3PickerRow(
        label = StrMoney.item, value = product?.name, placeholder = StrMoney.pickItem,
        icon = Icons.Outlined.Inventory2, error = errors.product, onClick = onChoose,
      )
      V3Segmented(listOf(Workflow.loose, Workflow.carton), if (pack.carton) 1 else 0, { selected ->
        if ((selected == 1) != pack.carton) {
          onChange(row.copy(unitName = null, quantity = "", price = "", packaging = pack.copy(carton = selected == 1)))
        }
      })
      if (pack.carton) {
        Row(horizontalArrangement = gap) {
          V3TextField(row.quantity, { onChange(row.copy(quantity = it)) }, Workflow.cartons, Modifier.weight(1f), kind = V3FieldKind.NUMBER, required = true, error = errors.quantity)
          V3TextField(pack.pieces, { onChange(row.copy(packaging = pack.copy(pieces = it))) }, Workflow.pieces, Modifier.weight(1f), kind = V3FieldKind.NUMBER, required = true, error = errors.packaging)
        }
        V3TextField(
          row.price, { onChange(row.copy(price = it)) }, Workflow.cartonPrice,
          kind = V3FieldKind.MONEY, required = true, error = errors.price,
          suffix = form.currency ?: "؋",
        )
        val pieces = pack.piecesPerCarton()
        val cartons = row.quantity.toLongOrNull()
        val price = row.price.toDoubleOrNull()
        if (pieces != null && cartons != null && cartons > 0 && cartons <= Long.MAX_VALUE / pieces && price != null && price.isFinite() && price >= 0) {
          val currency = form.currency ?: ir.vil3ntec.tohid.core.purchase.PurchaseFx.AFN
          val unitPrice = ir.vil3ntec.tohid.unitMoney(price / pieces)
          Text(Workflow.basePrice(ir.vil3ntec.tohid.qty((cartons * pieces).toDouble()), "$unitPrice $currency"), style = V3Type.subhead, color = PhoneV3.colors.ink)
        }
      } else {
        Row(horizontalArrangement = gap) {
          V3TextField(
            row.quantity, { onChange(row.copy(quantity = it)) }, StrMoney.quantity, Modifier.weight(1f),
            kind = V3FieldKind.NUMBER, required = true, suffix = row.unitName ?: product?.unit, error = errors.quantity,
          )
          V3TextField(
            row.price, { onChange(row.copy(price = it)) },
            form.currency?.let(StrPurchaseFx::priceIn) ?: row.unitName?.let(StrPurchaseUnits::priceIn) ?: StrMoney.unitPrice,
            Modifier.weight(1f), kind = V3FieldKind.MONEY, required = true, helper = lastPrice, error = errors.price,
          )
        }
        if (unitsOn) RowUnit(d, row.productId, row.unitName, row.quantity, row.price, errors.unit, form, onPickUnit)
      }
      if (!form.later) {
        ir.vil3ntec.tohid.ui.v3.V3TextButton(Workflow.productDates, { details = !details })
        if (details || errors.expiry != null || errors.production != null) {
          V3TextField(
            pack.production, { onChange(row.copy(packaging = pack.copy(production = it))) }, StrSheets.productionDate,
            kind = V3FieldKind.DATE, placeholder = StrStock.datePlaceholder, error = errors.production,
          )
          ExpiryField(row.expiry, { onChange(row.copy(expiry = it)) }, errors.expiry)
          V3TextField(
            pack.barcode, { onChange(row.copy(packaging = pack.copy(barcode = it.take(128)))) }, Workflow.optionalBarcode,
          )
        }
      }
      if (onRemove != null) ir.vil3ntec.tohid.ui.v3.V3TextButton(StrMoney.removePurchaseRow, onRemove, danger = true)
    }
  }
}
