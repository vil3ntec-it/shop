package ir.vil3ntec.tohid.desktop.shots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import ir.vil3ntec.tohid.data.CashShift
import ir.vil3ntec.tohid.data.DiscountGate
import ir.vil3ntec.tohid.data.PayFlow
import ir.vil3ntec.tohid.data.PromoEngine
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.print.ThermalPrinter
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.components.pos.PayPanelState
import ir.vil3ntec.tohid.ui.components.pos.PhoneSaleText
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.sales.approval.ApprovalAsk
import ir.vil3ntec.tohid.ui.v3.sales.approval.ApprovalBody
import ir.vil3ntec.tohid.ui.v3.sales.approval.ApprovalStart
import ir.vil3ntec.tohid.ui.v3.sales.cart.CartNote
import ir.vil3ntec.tohid.ui.v3.sales.cart.SaleActions
import ir.vil3ntec.tohid.ui.v3.sales.cart.SaleMenuSheet
import ir.vil3ntec.tohid.ui.v3.sales.cart.SaleScreen
import ir.vil3ntec.tohid.ui.v3.sales.cart.SaleStart
import ir.vil3ntec.tohid.ui.v3.sales.checkout.CheckoutActions
import ir.vil3ntec.tohid.ui.v3.sales.checkout.CheckoutForm
import ir.vil3ntec.tohid.ui.v3.sales.checkout.CheckoutModel
import ir.vil3ntec.tohid.ui.v3.sales.checkout.CheckoutReceipt
import ir.vil3ntec.tohid.ui.v3.sales.checkout.CheckoutSheet
import ir.vil3ntec.tohid.ui.v3.sales.checkout.CheckoutView
import ir.vil3ntec.tohid.ui.v3.sales.checkout.CustomerPickerModel
import ir.vil3ntec.tohid.ui.v3.sales.drawer.DrawerActions
import ir.vil3ntec.tohid.ui.v3.sales.drawer.DrawerCloseSheet
import ir.vil3ntec.tohid.ui.v3.sales.drawer.DrawerCountSheet
import ir.vil3ntec.tohid.ui.v3.sales.drawer.DrawerScreen
import ir.vil3ntec.tohid.ui.v3.sales.drawer.DrawerView
import ir.vil3ntec.tohid.ui.v3.sales.history.CancelSheet
import ir.vil3ntec.tohid.ui.v3.sales.history.HistoryActions
import ir.vil3ntec.tohid.ui.v3.sales.history.HistoryFilter
import ir.vil3ntec.tohid.ui.v3.sales.history.HistoryModel
import ir.vil3ntec.tohid.ui.v3.sales.history.HistoryQuery
import ir.vil3ntec.tohid.ui.v3.sales.history.HistoryScreen
import ir.vil3ntec.tohid.ui.v3.sales.history.InvoiceActions
import ir.vil3ntec.tohid.ui.v3.sales.history.InvoiceScreen
import ir.vil3ntec.tohid.ui.v3.sales.drawer.DrawerModel
import ir.vil3ntec.tohid.ui.v3.sales.print.PrintBody
import ir.vil3ntec.tohid.ui.v3.sales.print.PrintForm
import ir.vil3ntec.tohid.ui.v3.sales.print.PrintKind
import ir.vil3ntec.tohid.ui.v3.sales.returns.ReturnFlowSheet
import ir.vil3ntec.tohid.ui.v3.sales.returns.ReturnSheet
import ir.vil3ntec.tohid.ui.v3.sales.scan.SaleScan
import ir.vil3ntec.tohid.ui.v3.sales.scan.SaleScanPanel
import ir.vil3ntec.tohid.data.Debtor
import ir.vil3ntec.tohid.data.DebtTransaction
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.SaleItem
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.SalesEngine.CartLine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.WarehouseEntry
import ir.vil3ntec.tohid.ui.v3.sales.cart.FreeLineForm
import ir.vil3ntec.tohid.ui.v3.sales.cart.FreeLineSheet
import ir.vil3ntec.tohid.ui.v3.sales.cart.KeypadModel
import ir.vil3ntec.tohid.ui.v3.sales.cart.KeypadSheet
import ir.vil3ntec.tohid.ui.v3.sales.cart.LineForm
import ir.vil3ntec.tohid.ui.v3.sales.cart.LineModel
import ir.vil3ntec.tohid.ui.v3.sales.cart.LineSheet
import ir.vil3ntec.tohid.ui.v3.sales.checkout.CustomerPickerSheet
import ir.vil3ntec.tohid.ui.v3.sales.draft.DraftSheet
import ir.vil3ntec.tohid.ui.v3.sales.quick.QuickSaleActions
import ir.vil3ntec.tohid.ui.v3.sales.quick.QuickSaleScreen
import ir.vil3ntec.tohid.ui.v3.sales.scan.CameraAccess
import ir.vil3ntec.tohid.ui.v3.sales.scan.ScanScreen

/**
 *  عکس‌های گروهِ G5 فروش (`sales.md`) — روی دفترِ نمایشیِ همین فایل (نه دادهٔ دکانِ واقعی):
 *  ۱۴ کالا در چهار دسته با نام‌های بلند، تمام‌شده و وزنی، سه قرض‌دار (بدهکار، صاف، پیش‌پرداخت)
 *  و سبدی با چهار قلم. صفحه‌ها مستقیم با همین دفتر کشیده می‌شوند؛ کارها بی‌اثرند.
 *  مسیرهای `route-*` همان پوستهٔ برنامه با دفترِ نمایشیِ CI است.
 */
object SalesShots : GroupShots {
  override val group = "sales"
  override val shots = listOf(
    Shot("route-sale", route = "sale"),
    Shot("route-history", route = "sales"),
    Shot("route-drawer", route = "sales/drawer"),
    Shot("sale", content = { SaleShot() }),
    Shot("sale-open", content = { SaleShot(SaleStart(open = true)) }),
    Shot("sale-menu", content = { SaleShot(SaleStart(menu = true)) }),
    Shot("sale-scan", content = { SaleScanShot() }),
    Shot("checkout-cash", content = { CheckoutShot(CheckoutForm(SalesEngine.Payment.CASH, null), promo = true) }),
    Shot("checkout-credit", content = { CheckoutShot(CheckoutForm(SalesEngine.Payment.CREDIT, SalesDemo.DEBTOR, paidText = "500")) }),
    Shot("checkout-discount", content = {
      CheckoutShot(CheckoutForm(SalesEngine.Payment.CASH, null, discountText = "15", discountOpen = true), gate = DiscountGate.State.NEEDS_APPROVAL)
    }),
    Shot("checkout-split", content = { CheckoutShot(CheckoutForm(SalesEngine.Payment.CASH, SalesDemo.DEBTOR), payV2 = true) }),
    Shot("checkout-done", content = { DoneShot() }),
    Shot("approval", content = { ApprovalShot() }),
    Shot("quick", content = { QuickShot(SalesDemo.ledger(), SalesDemo.cart) }),
    Shot("quick-empty-cart", content = { QuickShot(SalesDemo.ledger(), emptyList()) }),
    Shot("quick-nomatch", content = { QuickShot(SalesDemo.ledger(), SalesDemo.cart, query = "کالای ناموجود") }),
    Shot("line", content = { LineShot(SalesDemo.cart[0]) }),
    Shot("line-second-unit", content = { LineShot(CartLine(SalesDemo.CARTON, 1.0), LineForm("6", "1800", inSaleUnit = true)) }),
    Shot("line-error", content = { LineShot(SalesDemo.cart[1], LineForm("1.5", "450")) }),
    Shot("free-line-filled", content = { FreeShot(FreeLineForm("کیسهٔ بزرگِ پلاستیکی", "15", "3")) }),
    Shot("free-line-errors", content = { FreeShot(FreeLineForm(priceText = "0"), errors = true) }),
    Shot("keypad-weighed", content = { KeypadShot(SalesDemo.RICE, "2.5") }),
    Shot("keypad-in-cart", content = { KeypadShot(SalesDemo.OIL, null) }),
    Shot("customer", content = { CustomerShot("") }),
    Shot("customer-search", content = { CustomerShot("احمد") }),
    Shot("scan-live", content = { ScanScreen(onCode = {}, onClose = {}, camera = false, start = CameraAccess.GRANTED) }),
    Shot("scan-ask", content = { ScanScreen(onCode = {}, onClose = {}, camera = false, start = CameraAccess.ASK) }),
    Shot("scan-denied", content = { ScanScreen(onCode = {}, onClose = {}, camera = false, start = CameraAccess.DENIED) }),
    Shot("history", content = { HistoryShot(HistoryFilter.ALL) }),
    Shot("history-cancelled", content = { HistoryShot(HistoryFilter.CANCELLED) }),
    Shot("invoice", content = { InvoiceShot() }),
    Shot("return", content = { OverInvoice { ReturnSheet(History.ledger(), History.sale(), onConfirm = { _, _ -> }, onClose = {}, start = mapOf("h1-a" to "1")) } }),
    Shot("return-v2", content = { OverInvoice { ReturnFlowSheet(History.ledger(), History.sale(), save = { true }, onPosted = { _, _ -> }, onClose = {}, prefill = true) } }),
    Shot("print", content = { PrintShot(reason = false) }),
    Shot("print-reprint", content = { PrintShot(reason = true) }),
    Shot("cancel", content = { OverInvoice { CancelSheet(SalesEngine.cancelWarning(History.sale()), onConfirm = {}, onClose = {}, start = "مشتری پشیمان شد") } }),
    Shot("drawer", content = { DrawerShot() }),
    Shot("drawer-close", content = { DrawerCloseShot() }),
    Shot("drawer-count", content = { DrawerShot(); DrawerCountSheet(blind = false, expectedMinor = 445_000, error = null, onSubmit = { _, _ -> }, onClose = {}) }),
    Shot("draft", content = { DraftShot() }),
  )
}

private val noQuick = QuickSaleActions({ _, _ -> }, {}, {}, {}, {}, {}, {}, {})

@Composable
private fun QuickShot(d: ShopData, cart: List<CartLine>, query: String = "", category: String? = null) {
  QuickSaleScreen(d, cart, SalesEngine.cartTotal(d, cart), onBack = {}, actions = noQuick, initialQuery = query, initialCategory = category)
}

/** برگه روی همان «فروشِ سریع» که از آن باز می‌شود */
@Composable
private fun OverQuick(content: @Composable () -> Unit) {
  Box(Modifier.fillMaxSize()) {
    QuickShot(SalesDemo.ledger(), SalesDemo.cart)
    content()
  }
}

@Composable
private fun LineShot(line: CartLine, form: LineForm? = null) = OverQuick {
  LineSheet(LineModel.info(SalesDemo.ledger(), line), onSave = { _, _ -> }, onRemove = {}, onClose = {}, initial = form)
}

@Composable
private fun FreeShot(form: FreeLineForm, errors: Boolean = false) = OverQuick {
  FreeLineSheet(onAdd = {}, onClose = {}, initial = form, showErrors = errors)
}

@Composable
private fun KeypadShot(productId: String, text: String?) = OverQuick {
  val item = KeypadModel.item(SalesDemo.ledger(), productId, SalesDemo.cart)!!
  KeypadSheet(item, onConfirm = {}, onClose = {}, initial = text)
}

@Composable
private fun CustomerShot(query: String) = OverQuick {
  CustomerPickerSheet(SalesDemo.ledger(), selected = SalesDemo.DEBTOR, onPick = {}, onNew = {}, onClose = {}, initialQuery = query)
}

@Composable
private fun DraftShot() = OverQuick {
  DraftSheet(lines = 4, total = 2_350.0, dropped = 1, onContinue = {}, onDiscard = {}, onLater = {})
}

private val noSale = SaleActions({}, {}, {}, { _, _ -> }, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})

/** صندوقِ گوشی با سبدِ نمونه */
@Composable
private fun SaleShot(start: SaleStart = SaleStart()) {
  val d = SalesDemo.ledger()
  if (start.menu) {
    Box(Modifier.fillMaxSize()) {
      SaleScreen(d, SalesDemo.cart, SalesEngine.cartTotal(d, SalesDemo.cart), SalesDemo.DEBTOR, listMode = false, actions = noSale)
      SaleMenuSheet({}, {}, {}, {})
    }
  } else SaleScreen(d, SalesDemo.cart, SalesEngine.cartTotal(d, SalesDemo.cart), SalesDemo.DEBTOR, listMode = false, actions = noSale, promo = SalesDemo.promo(d).applied, start = start)
}

/** اسکنِ پیوستهٔ صندوق با پاسخِ آخرین خواندن */
@Composable
private fun SaleScanShot() {
  ScanScreen(onCode = {}, onClose = {}, camera = false, start = CameraAccess.GRANTED, continuous = true, bottom = {
    SaleScanPanel(SaleScan.Shown(1, CartNote("روغنِ آفتاب‌گردانِ ۱٫۸ لیتری به سبد اضافه شد", V3Tone.SUCCESS)), 2, {}, {}, {})
  })
}

private val noCheckout = CheckoutActions({}, {}, {}, {}, {}, {}, {}, {})

/** برگهٔ تسویه روی صندوق — عددها از همان `CheckoutModel` */
@Composable
private fun CheckoutShot(form: CheckoutForm, promo: Boolean = false, payV2: Boolean = false, gate: DiscountGate.State = DiscountGate.State.FREE) {
  val d = SalesDemo.ledger()
  val outcome = if (promo) SalesDemo.promo(d) else null
  val cart = PromoEngine.priced(SalesDemo.cart, outcome)
  val checkout = CheckoutModel.checkout(form, canDiscount = true, blockExpired = false, seller = "")
  val n = CheckoutModel.numbers(d, cart, form, checkout)
  val debtor = form.debtorId?.let { id -> d.debtors.find { it.id == id } }
  val pay = remember { PayPanelState().apply { if (payV2) { cash = "1000"; rows += PayFlow.DraftRow("hesabpay", "HP-48213", "800") } } }
  val discountError = CheckoutModel.discountError(form, true)
  val paidError = CheckoutModel.paidError(form, payV2)
  Box(Modifier.fillMaxSize()) {
    SaleShot()
    CheckoutSheet(
      view = CheckoutView(
        form = form, numbers = n, rows = CheckoutModel.rows(n, outcome, payV2), heroLine = CheckoutModel.heroLine(n, outcome, PhoneSaleText::amount),
        promo = outcome, showCodes = promo, coupons = if (promo) listOf("EID10") else emptyList(), canDiscount = true, payV2 = payV2, gate = gate,
        gateNote = CheckoutModel.gateNote("maxPct", 10.0), customerName = debtor?.name,
        customerBalance = debtor?.let { CustomerPickerModel.label(ShopStore.debt(d, it.id)) },
        busy = false, error = null, discountError = discountError, paidError = paidError, payError = null,
        canSubmit = CheckoutModel.canSubmit(false, discountError, paidError, gate), canAskApproval = true,
      ),
      pay = pay,
      actions = noCheckout,
      receipt = null,
    )
  }
}

/** پس از ثبت: همان برگه رسید شده */
@Composable
private fun DoneShot() = Box(Modifier.fillMaxSize()) {
  SaleShot()
  CheckoutSheet(view = null, pay = remember { PayPanelState() }, actions = noCheckout, receipt = {
    CheckoutReceipt(History.ledger(), History.sale(), "دکانِ نمونه", onPrint = {}, onSend = {}, onNewSale = {})
  })
}

@Composable
private fun ApprovalShot() = Box(Modifier.fillMaxSize()) {
  SaleShot()
  ApprovalBody(
    ask = ApprovalAsk("تأییدِ تخفیف", "تخفیفِ 300 ؋ روی فاکتورِ 2,045 ؋", DiscountGate.need("s-demo", 300.0, 2_045.0)),
    approvers = emptyList(),
    block = null,
    onPin = { _, _, _ -> null to null },
    onApproved = {},
    onClose = {},
    start = ApprovalStart(reason = "مشتریِ همیشگی", pin = "123"),
  )
}

private val noHistory = HistoryActions({}, {}, {}, {}, {}, {}, {}, {}, {}, null)

@Composable
private fun HistoryShot(filter: HistoryFilter) {
  val d = History.ledger()
  val today = todayIso()
  HistoryScreen(
    d, today, HistoryModel.yesterday(today), returnsV2 = true, canCancel = true,
    drawer = DrawerModel.line(d, History.now, blind = false),
    query = remember { HistoryQuery(filter, "", HistoryModel.ALL_MONTHS) },
    actions = noHistory,
  )
}

private val noInvoice = InvoiceActions({}, {}, {}, {}, {}, {}, {}, {})

@Composable
private fun InvoiceShot() = InvoiceScreen(History.ledger(), History.sale(), "دکانِ نمونه", returnsV2 = false, actions = noInvoice)

@Composable
private fun OverInvoice(content: @Composable () -> Unit) = Box(Modifier.fillMaxSize()) {
  InvoiceShot()
  content()
}

@Composable
private fun PrintShot(reason: Boolean) = OverInvoice {
  PrintBody(
    title = "چاپ فاکتور",
    form = PrintForm(kind = PrintKind.BLUETOOTH, bt = "00:11:22:33:44:55"),
    onForm = {},
    saved = emptyList(),
    granted = true,
    onAskBluetooth = {},
    paired = listOf(ThermalPrinter.Printer("XP-58 پیشخوان", "00:11:22:33:44:55"), ThermalPrinter.Printer("MTP-II", "66:77:88:99:AA:BB")),
    usb = emptyList(),
    needsReason = reason,
    onPrint = { _, _, _ -> "" },
    onDone = {},
    onClose = {},
    startReason = reason,
  )
}

private val noDrawer = DrawerActions({}, {}, {}, {}, {}, {}, {})

@Composable
private fun DrawerShot() {
  val d = History.ledger()
  val shift = CashShift.current(d)
  DrawerScreen(
    DrawerView(
      shift = shift, totals = shift?.let { CashShift.totalsOf(d, it, History.now) }, payV2 = false, blind = false,
      expectedMinor = null, reportError = null, skipped = 0, history = CashShift.list(d).take(60),
    ),
    noDrawer,
  )
}

@Composable
private fun DrawerCloseShot() = Box(Modifier.fillMaxSize()) {
  DrawerShot()
  val d = History.ledger()
  DrawerCloseSheet(CashShift.totalsOf(d, CashShift.current(d)!!, History.now), onSubmit = { _, _ -> }, onClose = {}, start = "4400")
}

/** دفترِ تاریخچه: فاکتورهای امروز و دیروز (نقد، نسیه، لغوشده، اینترنتی) و صندوقِ بازِ امروز */
private object History {
  val now: Long = System.currentTimeMillis()
  private val today = todayIso()
  private val yesterday = HistoryModel.yesterday(today)
  private const val H = 3_600_000L

  fun sale(): Sale = ledger().sales.first { it.id == "h1" }

  fun ledger(): ShopData {
    val base = SalesDemo.ledger()
    val sales = listOf(
      Sale("h1", 1245, total = 1_520.0, discount = 70.0, finalTotal = 1_450.0, paidAmount = 1_000.0, remaining = 450.0, paymentMethod = "credit", debtorId = SalesDemo.DEBTOR, createdAt = now - H, date = today),
      Sale("h2", 1244, total = 380.0, finalTotal = 380.0, paidAmount = 380.0, createdAt = now - 2 * H, date = today),
      Sale("h3", 1243, total = 2_700.0, finalTotal = 2_700.0, paidAmount = 2_700.0, status = "cancelled", createdAt = now - 3 * H, date = today),
      Sale("h4", 61, total = 900.0, finalTotal = 900.0, paidAmount = 900.0, series = "W", createdAt = now - 26 * H, date = yesterday),
      Sale("h5", 1242, total = 95.0, finalTotal = 95.0, paidAmount = 95.0, createdAt = now - 27 * H, date = yesterday),
    )
    val items = listOf(
      SaleItem("h1-a", "h1", SalesDemo.RICE, quantity = 2.5, unitPrice = 380.0, totalPrice = 950.0),
      SaleItem("h1-b", "h1", SalesDemo.OIL, quantity = 1.0, unitPrice = 450.0, totalPrice = 450.0),
      SaleItem("h1-c", "h1", "", name = "کیسه", quantity = 8.0, unitPrice = 15.0, totalPrice = 120.0),
      SaleItem("h2-a", "h2", SalesDemo.RICE, quantity = 1.0, unitPrice = 380.0, totalPrice = 380.0),
      SaleItem("h3-a", "h3", SalesDemo.CARTON, quantity = 1.5, unitPrice = 1_800.0, totalPrice = 2_700.0),
      SaleItem("h4-a", "h4", SalesDemo.OIL, quantity = 2.0, unitPrice = 450.0, totalPrice = 900.0),
      SaleItem("h5-a", "h5", "p-sugar", quantity = 1.0, unitPrice = 95.0, totalPrice = 95.0),
    )
    val d = base.copy(sales = base.sales + sales, saleItems = base.saleItems + items)
    return (CashShift.open(d, 2_000.0, "", today, now - 5 * H) { "shift-1" } as CashShift.Result.Ok).data
  }
}

/** دفترِ نمایشیِ عکس‌های فروش */
private object SalesDemo {
  const val RICE = "p-rice"
  const val OIL = "p-oil"
  const val SOAP = "p-soap"
  const val CARTON = "p-cola"
  const val DEBTOR = "d-ahmad"
  const val DRINKS = "نوشیدنی"

  private val products = listOf(
    Product(RICE, "برنجِ سلامِ هندیِ درجه‌یک (کیسهٔ پنج کیلویی)", "خوراکی", "کیلوگرم", salePrice = 380.0, barcodes = listOf("6260010071")),
    Product(OIL, "روغنِ آفتاب‌گردانِ ۱٫۸ لیتری", "خوراکی", "عدد", salePrice = 450.0),
    Product(SOAP, "صابونِ گلنار", "شوینده", "عدد", salePrice = 35.0),
    Product(CARTON, "نوشابهٔ قوطی", DRINKS, "کارتن", salePrice = 1_800.0, saleUnit = "قوطی", unitFactor = 1.0 / 24),
    Product("p-tea", "چای سبزِ احمد ۵۰۰ گرمی", DRINKS, "عدد", salePrice = 320.0),
    Product("p-water", "آبِ معدنی ۱٫۵ لیتری", DRINKS, "عدد", salePrice = 25.0),
    Product("p-juice", "آب‌میوهٔ انار", DRINKS, "عدد", salePrice = 60.0),
    Product("p-sugar", "شکر", "خوراکی", "کیلوگرم", salePrice = 95.0),
    Product("p-flour", "آردِ گندمِ کابل", "خوراکی", "کیلوگرم", salePrice = 70.0),
    Product("p-shampoo", "شامپوی کودک", "شوینده", "عدد", salePrice = 210.0),
    Product("p-pen", "خودکارِ آبی", "لوازم", "عدد", salePrice = 15.0),
    Product("p-notebook", "دفترِ صدبرگ", "لوازم", "عدد", salePrice = 85.0),
    Product("p-battery", "باتریِ قلمی (بستهٔ چهارتایی)", "لوازم", "بسته", salePrice = 120.0),
    Product("p-gone", "کبریت", "لوازم", "عدد", salePrice = 5.0),
  )

  private val stock = mapOf(
    RICE to 42.5, OIL to 18.0, SOAP to 24.0, CARTON to 6.0, "p-tea" to 12.0, "p-water" to 96.0, "p-juice" to 3.0,
    "p-sugar" to 60.0, "p-flour" to 120.0, "p-shampoo" to 9.0, "p-pen" to 200.0, "p-notebook" to 40.0, "p-battery" to 15.0,
  )

  /** فروش‌های گذشته — ترتیبِ «پرفروش‌ترین» از همین */
  private val sold = listOf(RICE, RICE, RICE, OIL, OIL, "p-water", "p-water", "p-tea", SOAP)

  val cart = listOf(
    CartLine(RICE, 2.5),
    CartLine(OIL, 2.0),
    CartLine("p-water", 6.0),
    CartLine("free-bag", 3.0, unitPrice = 15.0, label = "کیسه"),
  )

  /** یک تخفیفِ خودکارِ نمونه روی همان سبد (فقط نمایش) */
  fun promo(d: ShopData): PromoEngine.Outcome = PromoEngine.Outcome(
    gross = SalesEngine.cartTotal(d, cart),
    discount = 45.0,
    applied = listOf(PromoEngine.Applied("r1", "۵٪ِ برنج", 45.0)),
    codes = emptyList(),
    cart = cart.map { if (it.productId == RICE) it.copy(unitPrice = 362.0) else it },
  )

  fun ledger(): ShopData = ShopData(
    products = products,
    productCategories = listOf("خوراکی", DRINKS, "شوینده", "لوازم"),
    warehouseEntries = stock.map { (id, q) -> WarehouseEntry(id = "w-$id", productId = id, units = q) },
    sales = sold.indices.map { Sale(id = "s$it", invoiceNumber = 100 + it, status = "completed") },
    saleItems = sold.mapIndexed { i, id -> SaleItem(id = "i$i", saleId = "s$i", productId = id, quantity = 0.0) },
    debtors = listOf(
      Debtor(id = DEBTOR, name = "احمد ولی‌زاده", phone = "0700123456"),
      Debtor(id = "d-mahmood", name = "محمود (دکانِ کنارِ مسجد)"),
      Debtor(id = "d-karim", name = "کریم فروشگاهِ شمال", phone = "0799000111"),
    ),
    transactions = listOf(
      DebtTransaction(id = "t1", debtorId = DEBTOR, type = "give", amount = 12_450.0),
      DebtTransaction(id = "t2", debtorId = "d-karim", type = "receive", amount = 3_000.0),
    ),
  )
}
