package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.data.BarcodeMatch
import ir.vil3ntec.tohid.data.CartQuery
import ir.vil3ntec.tohid.data.CartStore
import ir.vil3ntec.tohid.data.DiscountGate
import ir.vil3ntec.tohid.data.SalePost
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.numberInvalid
import ir.vil3ntec.tohid.numericInput
import ir.vil3ntec.tohid.parseNumber
import ir.vil3ntec.tohid.qty
import ir.vil3ntec.tohid.core.keymap.KeyMap
import ir.vil3ntec.tohid.core.keymap.KeyName
import ir.vil3ntec.tohid.scan.ScanFeedback
import ir.vil3ntec.tohid.ui.components.common.ScreenKind
import ir.vil3ntec.tohid.ui.components.common.ViewMode
import ir.vil3ntec.tohid.ui.components.common.ViewModeToggle
import ir.vil3ntec.tohid.ui.components.common.rememberViewMode
import ir.vil3ntec.tohid.ui.components.pos.CartHeaderAction
import ir.vil3ntec.tohid.ui.components.pos.CategoryChips
import ir.vil3ntec.tohid.ui.components.pos.MiniNumberField
import ir.vil3ntec.tohid.ui.components.pos.PosHintLine
import ir.vil3ntec.tohid.ui.components.pos.posCardMin
import ir.vil3ntec.tohid.ui.components.pos.CustomerRow
import ir.vil3ntec.tohid.ui.components.pos.OrderActions
import ir.vil3ntec.tohid.ui.components.pos.OrderLineView
import ir.vil3ntec.tohid.ui.components.pos.OrderLines
import ir.vil3ntec.tohid.ui.components.pos.OrderPanel
import ir.vil3ntec.tohid.ui.components.pos.PayPanelDialog
import ir.vil3ntec.tohid.ui.components.pos.PaymentMethodGrid
import ir.vil3ntec.tohid.ui.components.pos.PaymentSummary
import ir.vil3ntec.tohid.ui.components.pos.PosAction
import ir.vil3ntec.tohid.ui.components.pos.PosCartOps
import ir.vil3ntec.tohid.ui.components.pos.PosCatalog
import ir.vil3ntec.tohid.ui.components.pos.PosEmpty
import ir.vil3ntec.tohid.ui.components.pos.PosHit
import ir.vil3ntec.tohid.ui.components.pos.PosKeys
import ir.vil3ntec.tohid.ui.components.pos.PosLayout
import ir.vil3ntec.tohid.ui.components.pos.PosPayments
import ir.vil3ntec.tohid.ui.components.pos.PosProductCallbacks
import ir.vil3ntec.tohid.ui.components.pos.PosSearch
import ir.vil3ntec.tohid.ui.components.pos.PosShot
import ir.vil3ntec.tohid.ui.components.pos.PromoCodeField
import ir.vil3ntec.tohid.ui.components.pos.PromoLines
import ir.vil3ntec.tohid.ui.components.pos.PromoSummary
import ir.vil3ntec.tohid.ui.components.pos.ProductGrid
import ir.vil3ntec.tohid.ui.components.pos.ProductTable
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.launch

/**
 *  صندوقِ فروشِ کامپیوتر — برای صفحه‌کلید و بارکدخوان، نه برای انگشت.
 *
 *  خواستهٔ صاحب مخزن: «برنامهٔ کامپیوتری نباید شبیهِ موبایل باشد… با
 *  اینتر کار نمی‌کند… اسکنرِ موبایل چرا باید توی کامپیوتر باشد». پس:
 *
 *  • **دوربین نیست.** بارکدخوانِ کابلی/بلوتوثی مثلِ صفحه‌کلید تایپ می‌کند
 *    و Enter می‌زند ⇐ کالا به سبد. گوشیِ جفت‌شده هم می‌تواند بارکد بفرستد
 *    (`PhoneScanner`) — کمکی، نه راهِ اصلی.
 *  • **یک کادر برای همه‌چیز**، همیشه با فوکوس: بارکد یا بخشی از نام.
 *    فهرستِ پیشنهاد با ↑↓ و Enter. «۳*» پیش از بارکد یا نام ⇐ سه تا.
 *  • **سبد جدول است**؛ ↑↓ ردیف را عوض می‌کند (وقتی کادر خالی است)،
 *    + و − تعداد، Delete حذف، F3 تعداد/قیمتِ دستی.
 *  • **تسویه کنارِ سبد است، نه پنجره**: نوعِ پرداخت، تخفیف، دریافتی؛
 *    F9 یا Ctrl+Enter ثبت. F2 مشتری، F4 قلمِ آزاد، F8 چاپِ فاکتورِ آخر،
 *    Esc پاک کردنِ کادر.
 *
 *  چیدمان — نسخهٔ ۲ (VISUAL-DIRECTION «نسخهٔ ۲»، RTL): کاتالوگ راست و پهن با
 *  کادرِ اسکن (تنها جست‌وجوی صفحه)، ردیفِ تراشهٔ دسته‌ها و کارت‌ها (یا جدول —
 *  §۱٫۵)؛ ناحیهٔ «سبد خرید» چپ با رنگِ خودش. زدنِ کارت = افزودن؛ − / + فقط در سبد.
 *  منوی کناری در صندوق خودبه‌خود فقط‌آیکن است (`AppRoot`).
 *  اجزا در `ui/components/pos/`؛ قاعده‌های خالص در `PosLogic.kt` (آزمون‌دار).
 *  کادرِ اسکن همیشه فوکوس دارد و هر کلیک فوکوس را به آن برمی‌گرداند.
 *
 *  فقط روی کامپیوتر (`Platform.DESKTOP`) به کار می‌رود؛ گوشی همان
 *  `SaleScreen` با دوربین را دارد. منطقِ فروش همان `SalesEngine` است —
 *  دو صفحه، یک دفتر.
 */
@Composable
fun DeskSaleScreen(
  store: ShopStore,
  cartStore: CartStore,
  d: ShopData,
  snackbar: SnackbarHostState,
  onRegisterBarcode: (String) -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val cart by cartStore.lines.collectAsState()
  //  HW-10: نمایشگرِ مشتری (پنجرهٔ دوم) — فقط وقتی باز است کاری می‌کند
  val customerOpen by ir.vil3ntec.tohid.hw.CustomerFacing.open
  LaunchedEffect(cart, d, customerOpen) { ir.vil3ntec.tohid.hw.CustomerFacing.showCart(d, cart) }
  val cartDebtorId by cartStore.debtorId.collectAsState()
  LaunchedEffect(d.products.size, d.debtors.size) { cartStore.prune(d) }
  //  PRM: تخفیف‌های خودکار — کلیدِ دکان خاموش ⇒ null و `priced` همان `cart` است
  val promo = ir.vil3ntec.tohid.ui.PromoLive.rememberOutcome(d, cart)
  val priced = ir.vil3ntec.tohid.data.PromoEngine.priced(cart, promo)
  val promoSnap = ir.vil3ntec.tohid.ui.PromoLive.snap.value

  var query by remember { mutableStateOf("") }
  var hint by remember { mutableStateOf(0) }
  var selected by remember { mutableStateOf<String?>(null) }
  var pickDebtor by remember { mutableStateOf(false) }
  var freeLine by remember { mutableStateOf(false) }
  var editLine by remember { mutableStateOf<String?>(null) }
  var lastInvoice by remember { mutableStateOf<Pair<ShopData, String>?>(null) }
  var showInvoice by remember { mutableStateOf(false) }
  //  تراشهٔ دستهٔ انتخابی: null = «همه»
  var category by remember { mutableStateOf<String?>(null) }
  var confirmClear by remember { mutableStateOf(false) }
  //  نمای کارتی/جدولی — ترجیحِ همین کاربر روی همین دستگاه (§۱٫۵)
  val view = rememberViewMode("sale", ScreenKind.SALE)
  val shotView = PosShot.view.value
  LaunchedEffect(shotView) { if (shotView != null) { view.set(shotView); PosShot.view.value = null } }
  //  لمسی (تبلت/گوشی) ⇐ هدفِ ۴۸؛ کامپیوتر ⇐ ۳۶ (۵.۳)
  val target = PosLayout.minTarget(touch = !ir.vil3ntec.tohid.ui.Platform.DESKTOP).dp
  PhotoPrefs.load(context)
  val showPhotos = PhotoPrefs.show.value
  val posting by cartStore.posting.collectAsState()

  //  «نیازمند تأیید» هم کادرِ تخفیف را نشان می‌دهد؛ فقط «ممنوع» پنهانش می‌کند (STF-02)
  val canDiscount = remember { ir.vil3ntec.tohid.data.ShopRole.mayDiscount(context) }
  val blockExpired = remember { ir.vil3ntec.tohid.data.ShopPolicy.blockExpired(context) }
  //  PAY (v3 A4): پرداختِ ترکیبی — فقط با پرچم؛ F9 پنلِ پرداخت را باز می‌کند، F12 نقدِ دقیق
  val payV2 = remember { ir.vil3ntec.tohid.data.ShopPolicy.payV2(context) }
  var payOpen by remember { mutableStateOf(false) }
  val seller = remember { runCatching { ir.vil3ntec.tohid.sync.SyncStore(context).accountName }.getOrDefault("") }
  var payment by remember { mutableStateOf(SalesEngine.Payment.CASH) }
  var discountText by remember { mutableStateOf("") }
  var paidText by remember { mutableStateOf("") }
  LaunchedEffect(cartDebtorId) { if (cartDebtorId != null) payment = SalesEngine.Payment.CREDIT }

  val searchFocus = remember { FocusRequester() }
  LaunchedEffect(Unit) { runCatching { searchFocus.requestFocus() } }
  fun refocus() { runCatching { searchFocus.requestFocus() } }

  fun toast(text: String) { scope.launch { snackbar.showSnackbar(text) } }

  //  HW-02/03: چاپِ خودکار یا کشوی پس از فروش که نشد همین‌جا، سرِ صندوق، گفته می‌شود
  val printNotice by ir.vil3ntec.tohid.print.PrintSpooler.tillNotice
  LaunchedEffect(printNotice) {
    printNotice?.let { n ->
      ir.vil3ntec.tohid.print.PrintSpooler.consumed(n)
      snackbar.showSnackbar(n.text, duration = SnackbarDuration.Long)
    }
  }

  val index = remember(d.products) { ShopStore.barcodeIndex(d) }

  //  «۳*کد» ⇐ (۳، «کد») — رقمِ فارسی هم (F0-06، `CartQuery`)
  val parsed = remember(query) { CartQuery.parse(query).let { it.count to it.term } }
  val term = parsed.second.trim()
  //  همان قاعدهٔ پیشنهادها، حالا در شبکه (`PosCatalog`): فعال، نام یا آغازِ بارکد
  val matches: List<Product> = remember(term, d.products) { PosCatalog.matches(d.products, term) }
  LaunchedEffect(term) { hint = 0 }
  val categories = remember(d.products, d.productCategories) { PosCatalog.categories(d) }
  LaunchedEffect(categories) { if (category != null && categories.none { it.name == category }) category = null }
  val shown: List<Product> = remember(term, category, d.products, matches) {
    if (term.isNotEmpty()) matches else PosCatalog.byCategory(d.products, category)
  }

  fun applyAdded(product: Product, added: SalesEngine.Added) {
    cartStore.set(added.cart)
    selected = product.id
    if (added.capped) {
      ScanFeedback.unknown(context)
      toast("${product.name} فقط ${qty(added.available)}${if (product.unit.isNotBlank()) " ${product.unit}" else ""} موجود است")
    } else ScanFeedback.ok(context)
  }
  fun add(product: Product, count: Double) =
    applyAdded(product, SalesEngine.addToCartCapped(cartStore.lines.value, product.id, count, ShopStore.stock(d, product.id)))

  /**
   *  موتورِ تازهٔ موجودی (روشن): کدی که «سری»ِ یک کالاست ⇒ همان کالا به سبد، و فروش اول از همین
   *  سری برداشته می‌شود (`StockActions.lotForCode`). خاموش یا ناشناس ⇒ false (رفتارِ امروز).
   */
  fun addLot(code: String, dd: ShopData = d): Boolean {
    val (pid, lot) = ir.vil3ntec.tohid.data.StockActions.lotForCode(dd, code) ?: return false
    val product = dd.products.firstOrNull { it.id == pid && it.active } ?: return false
    val added = SalesEngine.addToCartCapped(cartStore.lines.value, pid, 1.0, ShopStore.stock(dd, pid))
    applyAdded(product, added.copy(cart = SalesEngine.setLineLot(added.cart, pid, lot)))
    if (!added.capped) toast("${product.name} — سری $lot به سبد اضافه شد")
    return true
  }

  /**
   *  POS-29: برچسبِ ترازو (فقط اگر دکان قاعده‌اش را روشن کرده باشد) — وزن
   *  به سبد؛ رقمِ کنترلِ غلط رد. `false` ⇒ برچسبِ ترازو نیست، راهِ همیشگی.
   */
  fun scaleLabel(code: String, dd: ShopData): Boolean =
    when (val m = ir.vil3ntec.tohid.data.ScaleLabels.match(
      ir.vil3ntec.tohid.data.ScaleLabels.rules(context), code, ShopStore.barcodeIndex(dd), dd.products,
    )) {
      null -> false
      is ir.vil3ntec.tohid.data.ScaleLabels.Match.Rejected -> { ScanFeedback.unknown(context); toast(m.message); true }
      is ir.vil3ntec.tohid.data.ScaleLabels.Match.Weighed -> { add(m.product, m.quantity); true }
    }

  /** Enter در کادر: بارکدِ کامل ⇐ همان؛ وگرنه پیشنهادِ برجسته (`PosSearch`) */
  fun submit() {
    if (scaleLabel(query, d)) { query = ""; return }
    when (val hit = PosSearch.resolve(query, index, d.products, matches, hint)) {
      PosHit.None -> Unit
      is PosHit.Inactive -> toast("«${hit.product.name}» غیرفعال است")
      is PosHit.Add -> { add(hit.product, hit.count); query = "" }
      is PosHit.Unknown -> if (addLot(hit.code)) query = "" else {
        ScanFeedback.unknown(context)
        scope.launch {
          val r = snackbar.showSnackbar("«${hit.code}» پیدا نشد", actionLabel = "ثبتِ کالا", duration = SnackbarDuration.Long)
          if (r == SnackbarResult.ActionPerformed) onRegisterBarcode(hit.code)
        }
      }
    }
  }

  /*
   *  بارکد از بیرونِ کادر — بارکدخوانی که وقتی کادر فوکوس نداشت خواند، یا
   *  گوشیِ جفت‌شده (`ScanRouter`). تا این صفحه باز است همه به سبد می‌روند؛
   *  بارکدی که از صفحهٔ دیگری به صندوق فرستاده شد هم این‌جا برداشته می‌شود.
   */
  val latestD by rememberUpdatedState(d)
  fun fromScanner(code: String): Boolean {
    val dd = latestD
    if (scaleLabel(code, dd)) return true
    when (val hit = BarcodeMatch.find(code, ShopStore.barcodeIndex(dd))) {
      is BarcodeMatch.Hit.Product -> dd.products.firstOrNull { it.id == hit.id }?.let {
        if (!it.active) toast("«${it.name}» غیرفعال است") else add(it, 1.0)
      }
      else -> if (!addLot(code, dd)) scope.launch {
        ScanFeedback.unknown(context)
        val r = snackbar.showSnackbar("بارکدِ «$code» ثبت نشده", actionLabel = "ثبتِ کالا", duration = SnackbarDuration.Long)
        if (r == SnackbarResult.ActionPerformed) onRegisterBarcode(code)
      }
    }
    return true
  }
  val owner = remember { Any() }
  DisposableEffect(Unit) {
    ir.vil3ntec.tohid.scan.ScanRouter.register(owner) { fromScanner(it) }
    onDispose { ir.vil3ntec.tohid.scan.ScanRouter.unregister(owner) }
  }
  LaunchedEffect(Unit) {
    for (code in ir.vil3ntec.tohid.scan.ScanRouter.toSale) fromScanner(code)
  }

  val checkout = SalesEngine.Checkout(
    seller = seller,
    discountType = SalesEngine.DiscountType.PERCENT,
    //  F0-06: خالی ⇐ صفر؛ نامعتبر ⇐ خطای کنارِ کادر و `finish` ثبت نمی‌کند
    discountValue = if (canDiscount) parseNumber(discountText) ?: 0.0 else 0.0,
    payment = payment,
    paidAmount = parseNumber(paidText) ?: 0.0,
    debtorId = cartDebtorId,
    blockExpired = blockExpired,
  )
  val totals = SalesEngine.totals(d, priced, checkout)
  val discountBad = canDiscount && numberInvalid(discountText)
  val paidBad = payment == SalesEngine.Payment.CREDIT && numberInvalid(paidText)

  //  S08: تخفیفِ بیش از سقف ⇒ «درخواستِ تأیید» با PINِ مدیر — همان تصمیمِ سرور
  var approval by remember { mutableStateOf<ir.vil3ntec.tohid.core.perm.ApprovalSlip.Slip?>(null) }
  var askApproval by remember { mutableStateOf(false) }
  val saleKey = remember(cart) { if (cart.isEmpty()) null else cartStore.saleKey() }
  val discountDecision = ir.vil3ntec.tohid.data.ShopRole.decide(
    context, "sales.discount", DiscountGate.req(totals.discount, totals.subtotal),
  )
  val gate = DiscountGate.state(discountDecision.decision, saleKey, totals.discount, totals.subtotal, approval)

  /** `settled` = تسویهٔ پنلِ پرداخت (با پرچم)؛ با پرچم و بی آن (F12، Ctrl+Enter) ⇒ نقدِ دقیق */
  fun finish(settled: ir.vil3ntec.tohid.core.pay.Settlement? = null) {
    if (cart.isEmpty()) { toast("سبد خالی است"); return }
    if (discountBad || (!payV2 && paidBad)) { toast("عددِ «${if (discountBad) "تخفیف" else "دریافتی"}» درست نیست"); return }
    if (!payV2 && payment == SalesEngine.Payment.CREDIT && cartDebtorId == null) { toast("فروشِ نسیه مشتری می‌خواهد — F2"); pickDebtor = true; return }
    if (gate == DiscountGate.State.DENIED) { toast("این تخفیف برای شما ممنوع است"); return }
    if (gate == DiscountGate.State.NEEDS_APPROVAL) { askApproval = true; return }
    val signed = if (gate == DiscountGate.State.APPROVED) approval?.let(DiscountGate::json) else null
    val st = if (!payV2) null else settled ?: when (
      val r = ir.vil3ntec.tohid.data.PayFlow.checkout(
        ir.vil3ntec.tohid.data.PayFlow.Draft(totals.finalTotal, cash = totals.finalTotal.toBigDecimal().stripTrailingZeros().toPlainString(), customerId = cartDebtorId),
      )
    ) {
      is ir.vil3ntec.tohid.data.PayFlow.Outcome.Ok -> r.value
      is ir.vil3ntec.tohid.data.PayFlow.Outcome.Bad -> { toast(ir.vil3ntec.tohid.data.PayFlow.message(r.problem)); return }
    }
    val sale = checkout.copy(settlement = st, approval = signed)
    //  POS-14: F9ِ پشتِ‌سرِهم یک فروش می‌سازد؛ سبد فقط پس از نوشتنِ دفتر پاک می‌شود
    scope.launch { when (val posted = cartStore.post(store) { key ->
      SalesEngine.record(d, priced, sale, ir.vil3ntec.tohid.todayIso(), System.currentTimeMillis(), ::newId,
      series = ir.vil3ntec.tohid.sync.DocSeries.current(context), saleId = key) }) {
      SalePost.Outcome.Busy -> Unit
      SalePost.Outcome.NotSaved -> toast(SalePost.NOT_SAVED)
      is SalePost.Outcome.Refused -> toast(posted.message)
      is SalePost.Outcome.Done -> {
        val r = posted.ok
        ir.vil3ntec.tohid.hw.CustomerFacing.sold(r.data.sales.last().finalTotal)
        selected = null; discountText = ""; paidText = ""; payment = SalesEngine.Payment.CASH; approval = null
        payOpen = false
        lastInvoice = r.data to r.saleId
        val change = st?.change ?: 0L
        toast("فروش ${money(r.data.sales.last().finalTotal)} ؋ ثبت شد" + (if (change > 0) " — پولِ پس: ${money(ir.vil3ntec.tohid.data.PayFlow.major(change))} ؋" else "") + " — F8 چاپ")
        refocus()
      }
    } }
  }

  fun step(id: String, sign: Int) {
    if (cart.none { it.productId == id }) return
    val unit = d.products.find { it.id == id }?.unit.orEmpty()
    cartStore.set(PosCartOps.step(cart, id, unit, sign))
  }
  fun moveSel(delta: Int) {
    if (cart.isEmpty()) return
    val i = cart.indexOfFirst { it.productId == selected }
    selected = cart[((if (i < 0) cart.size else i) + delta).coerceIn(0, cart.size - 1)].productId
  }

  val debtor = cartDebtorId?.let { id -> d.debtors.find { it.id == id } }
  val lineViews = remember(cart, d.products) {
    cart.map { line ->
      val p = d.products.find { it.id == line.productId }
      OrderLineView(
        id = line.productId,
        name = if (line.free) line.label else p?.name.orEmpty(),
        unit = p?.unit.orEmpty(),
        quantity = line.quantity,
        price = line.unitPrice ?: p?.salePrice ?: 0.0,
        customPrice = line.unitPrice != null,
        category = p?.category.orEmpty(),
      )
    }
  }
  //  کارت و ردیفِ کالا: زدن ⇐ افزودن (با پیشوندِ «۳*» اگر نوشته شده). − / + فقط در سبد است
  val productCb = PosProductCallbacks(
    stockOf = { ShopStore.stock(d, it) },
    qtyOf = { id -> PosCartOps.qtyOf(cart, id) },
    onAdd = { p -> add(p, parsed.first); query = ""; refocus() },
  )
  val gridState = rememberLazyGridState()
  val tableState = rememberLazyListState()
  LaunchedEffect(category) {
    runCatching { gridState.scrollToItem(0) }
    runCatching { tableState.scrollToItem(0) }
  }
  val listState = rememberLazyListState()
  LaunchedEffect(selected, cart.size) {
    val i = cart.indexOfFirst { it.productId == selected }
    if (i >= 0) runCatching { listState.animateScrollToItem(i) }
  }
  val c = Shop.colors

  /* ─────────── کاتالوگ: کادرِ اسکن (تنها جست‌وجوی صفحه)، تراشهٔ دسته‌ها، کارت‌ها ─────────── */
  @Composable
  fun Catalogue(modifier: Modifier) {
    Column(modifier) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
          value = query,
          onValueChange = { query = it },
          singleLine = true,
          shape = ir.vil3ntec.tohid.ui.theme.Shape.field,
          colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = c.fieldBg,
            focusedContainerColor = c.fieldBg,
            unfocusedBorderColor = c.fieldBorder,
            focusedBorderColor = c.focus,
          ),
          leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = c.muted) },
          placeholder = { Text("بارکد را بخوانید یا نامِ کالا را بنویسید — ۳* پیش از آن یعنی سه تا", maxLines = 1, overflow = TextOverflow.Ellipsis, color = c.muted) },
          modifier = Modifier
            .weight(1f)
            .focusRequester(searchFocus)
            .onPreviewKeyEvent { e ->
              if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
              when (PosKeys.field(keyName(e.key), e.isPrimaryModifier, query.isEmpty(), matches.isNotEmpty(), selected != null)) {
                PosAction.SUBMIT -> { submit(); true }
                PosAction.HINT_DOWN -> { hint = (hint + 1).coerceAtMost(matches.size - 1); true }
                PosAction.HINT_UP -> { hint = (hint - 1).coerceAtLeast(0); true }
                PosAction.ROW_DOWN -> { moveSel(1); true }
                PosAction.ROW_UP -> { moveSel(-1); true }
                PosAction.CLEAR_QUERY -> { query = ""; true }
                PosAction.DELETE_ROW -> { cartStore.set(SalesEngine.setCartQty(cart, selected!!, 0.0)); moveSel(0); true }
                PosAction.QTY_UP -> { step(selected!!, 1); true }
                PosAction.QTY_DOWN -> { step(selected!!, -1); true }
                else -> false
              }
            },
        )
        Spacer(Modifier.width(10.dp))
        ViewModeToggle(view)
      }
      Spacer(Modifier.height(12.dp))
      if (term.isNotEmpty()) {
        //  با عبارت، نتیجه از همهٔ دسته‌هاست — تراشه‌ها جایشان را به همین خط می‌دهند
        Text(
          "${shown.size} نتیجه در همهٔ دسته‌ها · ↑↓ و Enter",
          Modifier.heightIn(min = target).padding(horizontal = 6.dp).wrapContentHeight(Alignment.CenterVertically),
          style = MaterialTheme.typography.labelLarge, color = c.muted,
        )
      } else {
        CategoryChips(
          categories = categories,
          selected = category,
          onSelect = { category = it; refocus() },
          target = target,
          modifier = Modifier.fillMaxWidth(),
        )
      }
      Spacer(Modifier.height(12.dp))
      BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
        val highlight = if (term.isNotEmpty()) hint else -1
        when {
          shown.isEmpty() && term.isNotEmpty() -> PosEmpty(
            "کالایی با «$term» در فهرست نیست",
            "اگر بارکد است Enter بزنید؛ بارکدِ ناشناخته راهِ «ثبتِ کالا» را باز می‌کند.",
          )
          shown.isEmpty() -> PosEmpty(
            "کالایی برای فروش نیست",
            if (d.products.none { it.active }) "از «کالاها» کالا اضافه کنید یا کالای غیرفعال را فعال کنید." else "در این دسته کالای فعالی نیست.",
          )
          view.mode == ViewMode.TABLE -> ProductTable(shown, highlight, target, showPhotos, productCb, tableState, Modifier.fillMaxSize())
          else -> ProductGrid(
            shown, highlight, showPhotos, productCb, gridState,
            minCell = posCardMin(maxWidth, touch = !ir.vil3ntec.tohid.ui.Platform.DESKTOP),
            modifier = Modifier.fillMaxSize(),
          )
        }
      }
      //  یادآوریِ کلیدها — باریک و کم‌رنگ؛ فهرستِ کامل در F1
      PosHintLine("Enter افزودن · ↑↓ ردیف · + − تعداد · Delete حذف · F3 قیمت/تعداد · F4 قلمِ آزاد · F2 مشتری · F9 ثبت · F8 چاپِ آخر · Esc پاک · F1 همهٔ کلیدها")
    }
  }

  /* ─────────── ناحیهٔ «سبد خرید» ─────────── */
  @Composable
  fun Cart(modifier: Modifier) {
    OrderPanel(
      itemCount = cart.size,
      modifier = modifier,
      headerActions = {
        CartHeaderAction("قلمِ آزاد", Icons.Filled.AddBox, KeyMap.SALE_FREE_ITEM.hint) { freeLine = true }
        CartHeaderAction("خالی", Icons.Filled.DeleteOutline, null, enabled = cart.isNotEmpty(), danger = true) { confirmClear = true }
      },
      customer = {
        CustomerRow(
          name = debtor?.name,
          detail = debtor?.let { debtStateText(d, it.id) },
          pickHint = KeyMap.SALE_CUSTOMER.hint,
          target = target,
          onPick = { pickDebtor = true },
          onRemove = { cartStore.setDebtor(null); payment = SalesEngine.Payment.CASH; refocus() },
        )
      },
      items = {
        OrderLines(
          lines = lineViews,
          selected = selected,
          target = target,
          state = listState,
          showPhotos = showPhotos,
          onSelect = { selected = it; refocus() },
          onEdit = { editLine = it },
          onMinus = { step(it, -1); refocus() },
          onPlus = { step(it, 1); refocus() },
          //  PRM-18: هر تخفیفِ اعمال‌شده یک ردیف با نامش و کادرِ کد — تهِ همان فهرست،
          //  با اسکرولِ اقلام، تا جای ردیف‌های سبد را نگیرند
          footer = if (promoSnap.active) {
            {
              promo?.let { PromoLines(it.applied) }
              if (promoSnap.hasCodes) {
                PromoCodeField(
                  codes = promo?.codes.orEmpty(),
                  entered = ir.vil3ntec.tohid.ui.PromoLive.coupons.value,
                  onAdd = { ir.vil3ntec.tohid.ui.PromoLive.addCoupon(it); refocus() },
                  onRemove = { ir.vil3ntec.tohid.ui.PromoLive.removeCoupon(it) },
                  modifier = Modifier.padding(top = 8.dp),
                )
              }
            }
          } else null,
        )
      },
      summary = {
        PaymentSummary(PromoSummary.rows(totals, promo), totals.finalTotal) {
          if (canDiscount) {
            MiniNumberField("تخفیف", discountText, { discountText = numericInput(it) }, discountBad, "٪")
            //  S08: تخفیفِ بیش از سقف ⇒ «درخواستِ تأیید» با PINِ مدیر — همان تصمیمِ سرور
            when (gate) {
              DiscountGate.State.NEEDS_APPROVAL -> Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                ir.vil3ntec.tohid.ui.components.common.StatusPill(
                  "نیازمند تأیید", ir.vil3ntec.tohid.ui.components.common.PillTone.WARNING,
                )
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = { askApproval = true }, enabled = !posting) { Text("درخواستِ تأیید (F9)") }
              }
              DiscountGate.State.APPROVED -> ir.vil3ntec.tohid.ui.components.common.StatusPill(
                "تأیید شد", ir.vil3ntec.tohid.ui.components.common.PillTone.SUCCESS, Modifier.padding(top = 6.dp),
              )
              DiscountGate.State.DENIED -> ir.vil3ntec.tohid.ui.components.common.StatusPill(
                "این تخفیف برای شما ممنوع است", ir.vil3ntec.tohid.ui.components.common.PillTone.DANGER,
                Modifier.padding(top = 6.dp),
              )
              DiscountGate.State.FREE -> Unit
            }
          }
        }
      },
      methods = {
        if (payV2) {
          //  PAY (v3 A4): روش‌ها در پنلِ پرداخت‌اند — نقد، کیف پول، کارت، حواله و به حساب
          Text(
            "پرداختِ ترکیبی: F9 پنلِ پرداخت (نقد، کیف پول، کارت، حواله، به حسابِ مشتری) — F12 نقدِ دقیق",
            style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted,
          )
        } else {
          PaymentMethodGrid(PosPayments.configured(), payment, target) { m ->
            if (m.payment != payment) {
              payment = m.payment
              if (m.needsCustomer && cartDebtorId == null) pickDebtor = true
            }
          }
          if (payment == SalesEngine.Payment.CREDIT) {
            MiniNumberField("دریافتیِ همین حالا", paidText, { paidText = numericInput(it) }, paidBad, "؋")
          }
        }
      },
      actions = {
        OrderActions(
          postHint = KeyMap.SALE_PAY_PANEL.hint,
          printHint = KeyMap.SALE_REPRINT.hint,
          canPost = cart.isNotEmpty(),
          posting = posting,
          canPrint = lastInvoice != null,
          onPost = { if (payV2) payOpen = true else finish() },
          onPrint = { if (lastInvoice != null) showInvoice = true },
        )
      },
    )
  }

  BoxWithConstraints(
    Modifier
      .fillMaxSize()
      .onPreviewKeyEvent { e ->
        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        when (PosKeys.screen(keyName(e.key), e.isPrimaryModifier)) {
          PosAction.FINISH -> { if (payV2 && keyName(e.key) == KeyName.F9) { if (cart.isNotEmpty()) payOpen = true } else finish(); true }
          PosAction.CUSTOMER -> { pickDebtor = true; true }
          PosAction.EDIT_ROW -> { selected?.let { editLine = it }; true }
          PosAction.FREE_ITEM -> { freeLine = true; true }
          PosAction.REPRINT -> { if (lastInvoice != null) showInvoice = true; true }
          else -> false
        }
      }
      .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
  ) {
    /*
     *  چیدمان (RTL): کاتالوگ راست و پهن، سبد چپ با پهنای پله‌ای (`PosLayout`).
     *  ستونِ دسته‌ها و نوارِ دکمه‌های پایین دیگر نیستند. پنجرهٔ باریک (تبلتِ
     *  ایستاده): سبد زیرِ کاتالوگ.
     */
    val w = maxWidth.value
    if (PosLayout.sideCart(w)) {
      Row(Modifier.fillMaxSize()) {
        Catalogue(Modifier.weight(1f).fillMaxHeight())
        Spacer(Modifier.width(16.dp))
        Cart(Modifier.width(PosLayout.cartWidth(w).dp).fillMaxHeight())
      }
    } else {
      Column(Modifier.fillMaxSize()) {
        Catalogue(Modifier.weight(1f).fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        Cart(Modifier.weight(1f).fillMaxWidth())
      }
    }
  }

  //  خالی کردنِ سبد کارِ پاک‌کننده است ⇐ تأیید، با فوکوسِ پیش‌فرض روی «انصراف» (۵.۱ بند ۱۱)
  if (confirmClear) {
    val safe = remember { FocusRequester() }
    AlertDialog(
      onDismissRequest = { confirmClear = false; refocus() },
      title = { Text("سبدِ تازه؟") },
      text = { Text("${cart.size} ردیفِ این سبد پاک می‌شود و فروشی ثبت نمی‌شود.") },
      confirmButton = {
        TextButton(onClick = { cartStore.clear(); selected = null; confirmClear = false; refocus() }) {
          Text("پاک شود", color = Shop.colors.danger)
        }
      },
      dismissButton = {
        TextButton(onClick = { confirmClear = false; refocus() }, modifier = Modifier.focusRequester(safe)) { Text("انصراف") }
        LaunchedEffect(Unit) { runCatching { safe.requestFocus() } }
      },
    )
  }

  if (pickDebtor) {
    DebtorPicker(d = d, selected = cartDebtorId, onClose = { pickDebtor = false; refocus() }, onPick = { id ->
      cartStore.setDebtor(id); pickDebtor = false; refocus()
    })
  }
  if (freeLine) {
    FreeLineDialog(onDismiss = { freeLine = false; refocus() }, onConfirm = { label, price, count ->
      cartStore.set(SalesEngine.addFreeLine(cart, label, price, count, ::newId)); freeLine = false; refocus()
    })
  }
  editLine?.let { id ->
    val line = cart.find { it.productId == id }
    if (line == null) editLine = null else {
      val p = d.products.find { it.id == id }
      val base = if (line.free) (line.unitPrice ?: 0.0) else (p?.salePrice ?: 0.0)
      CartLineDialog(
        name = if (line.free) line.label else p?.name.orEmpty(),
        unit = p?.unit.orEmpty(),
        quantity = line.quantity,
        unitPrice = line.unitPrice ?: base,
        basePrice = base,
        custom = line.free || line.unitPrice != null,
        saleUnit = p?.saleUnit.orEmpty(),
        unitFactor = p?.unitFactor ?: 1.0,
        onDismiss = { editLine = null; refocus() },
        onConfirm = { quantity, price ->
          var next = SalesEngine.setCartQty(cart, id, quantity)
          next = SalesEngine.setLinePrice(next, id, if (line.free) (price ?: line.unitPrice) else price)
          cartStore.set(next); editLine = null; refocus()
        },
      )
    }
  }
  if (askApproval && saleKey != null) {
    ir.vil3ntec.tohid.ui.components.staff.ApprovalDialog(
      title = "تأییدِ تخفیف",
      summary = "تخفیفِ ${money(totals.discount)} ؋ روی فاکتورِ ${money(totals.subtotal)} ؋" +
        if (discountDecision.cap == "maxPct") " — سقفِ صندوقدار ${qty(discountDecision.limit ?: 0.0)}٪" else "",
      shopId = ir.vil3ntec.tohid.data.ShopRole.shopId(context),
      requesterUserId = remember { runCatching { ir.vil3ntec.tohid.sync.SyncStore(context).accountId }.getOrDefault("") },
      need = DiscountGate.need(saleKey, totals.discount, totals.subtotal),
      onApproved = { approval = it; askApproval = false; refocus() },
      onDismiss = { askApproval = false; refocus() },
    )
  }
  if (payOpen) {
    PayPanelDialog(
      total = totals.finalTotal,
      customerId = cartDebtorId,
      customerName = debtor?.name,
      busy = posting,
      target = target,
      onDismiss = { payOpen = false; refocus() },
      onConfirm = { st -> finish(st) },
    )
  }
  if (showInvoice) lastInvoice?.let { (snap, id) ->
    snap.sales.find { it.id == id }?.let { sale ->
      InvoiceDialog(d = snap, sale = sale, onDismiss = { showInvoice = false; refocus() }, onMessage = ::toast)
    } ?: run { showInvoice = false }
  }
}

/** کلیدِ Compose ⇒ نامِ کلیدِ فیزیکی (`KeyName`) برای `PosKeys` — همان نگاشتِ `KeyBinding.kt` */
private fun keyName(k: Key): KeyName? = KeyName.entries.firstOrNull { it.matches(k) }
