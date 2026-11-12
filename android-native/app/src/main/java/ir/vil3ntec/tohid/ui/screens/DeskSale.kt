package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
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
import ir.vil3ntec.tohid.ui.components.pos.GridPageButtons
import ir.vil3ntec.tohid.ui.components.pos.ListPageButtons
import ir.vil3ntec.tohid.ui.components.pos.PosTouchPrefs
import ir.vil3ntec.tohid.ui.components.pos.TouchCartColumn
import ir.vil3ntec.tohid.ui.components.pos.TouchToggle
import ir.vil3ntec.tohid.ui.components.pos.rememberTouchPad
import ir.vil3ntec.tohid.ui.components.pos.PromoCodeField
import ir.vil3ntec.tohid.ui.components.pos.PromoLines
import ir.vil3ntec.tohid.ui.components.pos.PromoSummary
import ir.vil3ntec.tohid.ui.components.pos.ProductGrid
import ir.vil3ntec.tohid.ui.components.pos.ProductTable
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.launch
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrPos
import ir.vil3ntec.tohid.core.i18n.StrInvoices

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
 *    F9 یا Ctrl+Enter ثبت. F2 مشتری، F4 قلمِ آزاد، F8 چاپِ فاکتورِ آخر، Shift+F8 کشو بی فروش (PAY-19)،
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
  //  نمایشگرِ قطبی (`Pole`) هم از همین‌جا؛ درگاه و پرچمش پیش از نخستین سبد خوانده می‌شود
  LaunchedEffect(Unit) { ir.vil3ntec.tohid.print.HwTools.load(context); ir.vil3ntec.tohid.hw.Pole.load(context) }
  LaunchedEffect(cart, d, customerOpen) { ir.vil3ntec.tohid.hw.CustomerFacing.showCart(d, cart) }
  val cartDebtorId by cartStore.debtorId.collectAsState()
  LaunchedEffect(d.products.size, d.debtors.size) { cartStore.prune(d) }
  //  PRM: تخفیف‌های خودکار — کلیدِ دکان خاموش ⇒ null و `priced` همان `cart` است
  val promo = ir.vil3ntec.tohid.ui.PromoLive.rememberOutcome(d, cart, cartDebtorId) // PRM-11: گروهِ مشتری
  val priced = ir.vil3ntec.tohid.data.PromoEngine.priced(cart, promo)
  //  POS-12: همان سبدی که `priced` از آن ساخته شد — `finish` با سبدِ عوض‌شده ثبت نمی‌کند
  val pricedFrom = cart
  val promoSnap = ir.vil3ntec.tohid.ui.PromoLive.snap.value
  //  PRM-13: امتیازِ وفاداری (`LoyaltyPref`، پیش‌فرض خاموش ⇒ null و `loyal` همان `priced`)؛ سنجشِ تخفیفِ ردیف، سفارشِ ارسال و «چرا این قیمت؟» همان `priced`
  val loyalty = ir.vil3ntec.tohid.ui.components.pos.rememberLoyaltyTill(d, priced, cartDebtorId)
  val loyal = loyalty?.cart ?: priced

  var query by remember { mutableStateOf("") }
  var hint by remember { mutableStateOf(0) }
  var selected by remember { mutableStateOf<String?>(null) }
  var pickDebtor by remember { mutableStateOf(false) }
  var payAfterPick by remember { mutableStateOf(false) } // POS-41: F2 ِ پنلِ پرداخت ⇒ گزینشِ مشتری، سپس همان پنل دوباره
  //  POS-39: دریافت از مشتری (F7) — فقط با کلیدِ همین دستگاه (پیش‌فرض خاموش)
  val tillReceive = remember { ir.vil3ntec.tohid.data.TillReceivePref.on(context) }
  var receivePick by remember { mutableStateOf(false) }
  var receiveFor by remember { mutableStateOf<String?>(null) }
  var receiveKey by remember { mutableStateOf("") }
  var receiveBusy by remember { mutableStateOf(false) }
  var receiptOf by remember { mutableStateOf<Triple<ShopData, String, String>?>(null) }
  var freeLine by remember { mutableStateOf(false) }
  var editLine by remember { mutableStateOf<String?>(null) }; var unitNext by remember { mutableStateOf(false) }; var rowDiscount by remember { mutableStateOf(false) } // POS-41: F5 // S05: Ctrl+U ⇒ برگهٔ ردیف با واحدِ بعدی
  val quick = ir.vil3ntec.tohid.ui.components.pos.rememberQuickTill() // S03 (`TillGoodsPref.quickRegister`، خاموش ⇒ null و راهِ پیشین)
  var lastInvoice by remember { mutableStateOf<Pair<ShopData, String>?>(null) }
  var showInvoice by remember { mutableStateOf(false) }
  //  تراشهٔ دستهٔ انتخابی: null = «همه»
  var category by remember { mutableStateOf<String?>(null) }
  var confirmClear by remember { mutableStateOf(false) }
  val view = rememberViewMode("sale", ScreenKind.SALE) // نمای کارتی/جدولی — ترجیحِ همین کاربر روی همین دستگاه (§۱٫۵)
  val shotView = PosShot.view.value
  LaunchedEffect(shotView) { if (shotView != null) { view.set(shotView); PosShot.view.value = null } }
  //  لمسی (تبلت/گوشی) ⇐ هدفِ ۴۸؛ کامپیوتر ⇐ ۳۶ (۵.۳). POS-31: کامپیوترِ لمسی (پشتِ `PosTouchPrefs`،
  //  پیش‌فرض خاموش ⇒ همان `!DESKTOP` ِ امروز) ⇐ ۴۸، دکمه‌های صفحه و صفحه‌کلیدِ عددی (POS-07)
  PosTouchPrefs.load(context)
  val touch = PosTouchPrefs.touch()
  val touchExtras = PosTouchPrefs.extras()
  val target = PosLayout.minTarget(touch = touch).dp
  PhotoPrefs.load(context)
  val showPhotos = PhotoPrefs.show.value
  val posting by cartStore.posting.collectAsState()

  //  «نیازمند تأیید» هم کادرِ تخفیف را نشان می‌دهد؛ فقط «ممنوع» پنهانش می‌کند (STF-02)
  val canDiscount = remember { ir.vil3ntec.tohid.data.ShopRole.mayDiscount(context) }
  val tillGuards = remember { ir.vil3ntec.tohid.data.TillGuardsPref.get(context) } // «فروخته نشود»، S22 و POS-24 ِ همین دستگاه — همان سدهای فروشِ تعویض
  val blockExpired = tillGuards.blockExpired; val checkoutRules = tillGuards.rules // POS-24: قاعده‌های پیش از پایانِ فاکتور (پیش‌فرض خاموش)
  val cashStep = remember { ir.vil3ntec.tohid.data.CashRoundPref.step(context) } // PAY-09: گردکردنِ نقدی، پیش‌فرض خاموش
  //  POS-15 (پشتِ `PosPriceCheckPrefs`، پیش‌فرض روشن): Ctrl+I قیمت‌پرسی بی افزودن به فاکتور
  ir.vil3ntec.tohid.ui.components.pos.PosPriceCheckPrefs.load(context)
  var priceCheck by remember { mutableStateOf(false) }
  val payV2 = remember { ir.vil3ntec.tohid.data.ShopPolicy.payV2(context) } // PAY (v3 A4): پرداختِ ترکیبی — فقط با پرچم؛ F9 پنلِ پرداخت، F12 نقدِ دقیق
  var payOpen by remember { mutableStateOf(false) }
  val enterGate = remember { ir.vil3ntec.tohid.core.pay.EmptyEnterGate() } // S11 (§۵.۲): Enter در کادرِ خالی ⇒ پنل، ≥ ۵۰۰ ms پس از اسکن/Enter
  val seller = remember { ir.vil3ntec.tohid.data.TillUser.me(context) } // S46: کاربرِ صندوق؛ تنظیمِ خاموش ⇒ همان نامِ حساب
  val lineSellerOn = remember { ir.vil3ntec.tohid.data.LineSellerPref.on(context) } // POS-23: خاموش ⇒ بی دکمه و Shift+F2 همان «مشتری»
  var lineSellerOpen by remember { mutableStateOf(false) }; var lineSellerFor by remember { mutableStateOf<String?>(null) }
  val page0 = remember { cartStore.page.value?.takeIf { !cartStore.recovering }?.extras() } // POS-13: کادرهای همین فاکتور از پیش‌نویس (`CartStore.page`)؛ پرسشِ بی‌پاسخ ⇒ خالی تا `take`
  var payment by remember { mutableStateOf(page0?.payment ?: SalesEngine.Payment.CASH) }
  var discountText by remember { mutableStateOf(page0?.discount.orEmpty()) }
  //  POS-20: تخفیفِ کلِ فاکتور به مبلغ (افغانی) هم، فقط با تنظیمِ «قیمت و تخفیفِ ردیف» (`LinePolicyPrefs.on`، پیش‌فرض خاموش). خاموش ⇒
  //  هرگز `true` نمی‌شود — جز فاکتورِ نگه‌داشته‌ای که با تنظیمِ روشن به مبلغ نگه داشته شد: همان مبلغ می‌ماند (۵۰۰ ؋ هرگز ۱۰۰٪ نمی‌شود).
  val amountChoosable = remember { ir.vil3ntec.tohid.data.LinePolicyPrefs.on(context) }
  var discountAmount by remember { mutableStateOf(page0?.discountAmount == true) }; var paidText by remember { mutableStateOf(page0?.paid.orEmpty()) }
  var invoiceSeller by remember { mutableStateOf(page0?.seller.orEmpty()) } // «اصلاحِ فاکتور» (`SaleReopen`): فروشندهٔ همان فاکتور؛ خالی ⇒ [seller]
  LaunchedEffect(cart, ir.vil3ntec.tohid.ui.v3.sales.cart.HeldSession.kept) { ir.vil3ntec.tohid.ui.v3.sales.cart.HeldSession.take(cartStore)?.let { x -> // «اصلاحِ فاکتور» یا بازیابیِ پیش‌نویس: یک بار
    discountText = x.discount; discountAmount = x.discountAmount; invoiceSeller = x.seller; x.payment?.let { payment = it }; if (x.paid.isNotEmpty()) paidText = x.paid } }
  LaunchedEffect(discountText, discountAmount, paidText, payment, invoiceSeller, cart.isEmpty()) { cartStore.setPage(ir.vil3ntec.tohid.data.HeldExtras(discountText, payment, paidText, discountAmount, invoiceSeller)) }
  //  POS-12: مشتریِ فاکتوری که با جابه‌جایی آمد (یا روشِ برگشته از پیش‌نویس، `page0`) روشِ پرداختِ برگشته‌اش را عوض نمی‌کند
  var switchedDebtor by remember { mutableStateOf(if (page0?.payment != null) cartStore.debtorId.value else null) }
  LaunchedEffect(cartDebtorId) {
    val fromSwitch = cartDebtorId != null && cartDebtorId == switchedDebtor
    switchedDebtor = null
    if (cartDebtorId != null && !fromSwitch) payment = SalesEngine.Payment.CREDIT
  }

  //  RET-01/05 (S13/S15): «حالتِ برگشت» با Shift+F9 — فقط با برگشت و تعویضِ تازهٔ روشن (`ReturnSettings`)؛
  //  خاموش ⇒ کلید همان کارِ امروز را می‌کند و دکمه‌ای نیست
  val returnsV2 = remember { ir.vil3ntec.tohid.data.ReturnSettings.enabled(context) }
  val myReceiptTag = remember { ir.vil3ntec.tohid.core.receipt.ReceiptTokens.shopTag(ir.vil3ntec.tohid.data.LedgerOwner.currentShop(context)) }
  var returnMode by remember { mutableStateOf(false) }
  //  PAY-19: Shift+F8 ⇒ پنجرهٔ دلیلِ «کشو بی فروش» با چاپگرِ پیش‌فرضِ کشودار (همان `OpenDrawerDialog` ِ دستگاه‌ها)
  var noSaleDrawer by remember { mutableStateOf<ir.vil3ntec.tohid.print.PrinterRegistry.Saved?>(null) }
  //  POS-18: Ctrl+R ⇒ نشانِ «لیستِ قیمت» (`PriceListAtPos`)؛ همان کلیدِ دستگاه که نشان را نشان می‌دهد
  val priceListOn = remember { ir.vil3ntec.tohid.ui.components.pos.PriceListPrefs.on(context) }
  var priceListToggles by remember { mutableIntStateOf(0) }

  val searchFocus = remember { FocusRequester() }
  val moreKeys = ir.vil3ntec.tohid.ui.components.pos.PosMoreKeysPrefs.isOn(context); val discountFocus = remember { FocusRequester() } // POS-41، خاموش ⇒ همان
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
  //  PRD-04: بارکدِ بسته‌ها (کارتن ⇒ ۲۴ دانه) — جدا، فقط کامل
  val packIndex = remember(d.products) { ShopStore.packIndex(d) }

  //  «۳*کد» ⇐ (۳، «کد») — رقمِ فارسی هم (F0-06)؛ متنی که خودش بارکدِ کالا یا بسته است («12x34») بی پیشوند (POS-03، `PosSearch.parse`)
  val parsed = remember(query, index, packIndex) { PosSearch.parse(query, index, packIndex).let { it.count to it.term } }
  val term = parsed.second.trim()
  //  همان قاعدهٔ پیشنهادها، حالا در شبکه (`PosCatalog`): فعال، نام یا آغازِ بارکد
  //  POS-04 (پشتِ `PosSearchPrefs`، پیش‌فرض خاموش): جست‌وجوی مقاوم با هستهٔ متن؛ خاموش ⇒ همان `matches`
  ir.vil3ntec.tohid.ui.components.pos.PosSearchPrefs.load(context)
  val smartSearch = ir.vil3ntec.tohid.ui.components.pos.PosSearchPrefs.on.value
  //  PRD-02 (پشتِ `ProductInfoPrefs`، پیش‌فرض خاموش): نام‌های دیگر و واژه‌های جست‌وجو هم؛ خاموش ⇒ همان دو راهِ پیشین
  val infoSearch = ir.vil3ntec.tohid.ui.components.product.productInfoOn()
  val searchIndex = remember(d.products, smartSearch, infoSearch) { if (smartSearch) PosCatalog.searchIndex(d.products, infoSearch) else null }
  val byId = remember(d.products) { d.products.associateBy { it.id } }
  val matches: List<Product> = remember(term, d.products, searchIndex, infoSearch) {
    searchIndex?.let { PosCatalog.smartMatches(it, byId, term) } ?: PosCatalog.matches(d.products, term, infoSearch)
  }
  LaunchedEffect(term) { hint = 0 }
  val categories = remember(d.products, d.productCategories, ir.vil3ntec.tohid.LocaleChoice.lang) { PosCatalog.categories(d) }
  LaunchedEffect(categories) { if (category != null && categories.none { it.name == category }) category = null }
  val pins = remember { ir.vil3ntec.tohid.ui.v3.sales.cart.TillPinsPrefs.load(context); ir.vil3ntec.tohid.ui.v3.sales.cart.TillPinsPrefs.pins }.value // POS-06: سنجاقِ همین دستگاه سرِ شبکه، فقط بی جست‌وجو
  val shown: List<Product> = remember(term, category, d.products, matches, pins) { if (term.isNotEmpty()) matches else ir.vil3ntec.tohid.core.pos.TillPins.arrangeBy(PosCatalog.byCategory(d.products, category), pins) { it.id } }

  /** `false` ⇒ سبد قفل بود و چیزی اضافه نشد (پیامِ «اضافه شد» نباید بیاید) */
  fun applyAdded(product: Product, added: SalesEngine.Added): Boolean {
    //  POS-12: وسطِ جابه‌جاییِ فاکتور سبد قفل است ⇒ اضافه نشد؛ بوقِ خطا تا دوباره اسکن شود
    if (!cartStore.set(added.cart)) { ScanFeedback.unknown(context); toast(CartStore.NOT_ADDED); return false }
    selected = product.id; quick?.pending = null
    if (added.capped) {
      ScanFeedback.unknown(context)
      toast(Str.onlyInStock(product.name, qty(added.available) + (if (product.unit.isNotBlank()) " ${product.unit}" else "")) +
        //  PRD-24 (پشتِ `ProductInfoPrefs`، پیش‌فرض خاموش): جایگزینِ موجود
        ir.vil3ntec.tohid.data.ProductInfoMap.insteadSuffix(d, product, infoSearch))
    } else ScanFeedback.ok(context)
    return true
  }
  fun add(product: Product, count: Double) =
    applyAdded(product, SalesEngine.addToCartCapped(cartStore.lines.value, product.id, count, ir.vil3ntec.tohid.data.ProductInfoMap.sellable(d, product.id)))

  /** PRD-04/S05: بارکدِ بسته ⇒ اندازهٔ بسته × [count]، با «واحدِ بزرگ در صندوق» قیمتِ لیستِ بسته (`PackScan`، همان گوشی)؛ `false` ⇒ بسته نیست */
  fun addPack(code: String, count: Double, dd: ShopData, items: Map<String, String>): Boolean {
    val r = ir.vil3ntec.tohid.data.PackScan.scan(dd, cartStore.lines.value, code, count, items, ir.vil3ntec.tohid.data.TillGoodsPref.units(context), ir.vil3ntec.tohid.data.TillGoodsPref.wholePacks(context)) ?: return false
    if (r is ir.vil3ntec.tohid.data.PackScan.Result.Added) applyAdded(r.product, r.added) else { ScanFeedback.unknown(context); toast(ir.vil3ntec.tohid.data.PackScan.refusal(r)) }
    return true
  }

  /**
   *  موتورِ تازهٔ موجودی (روشن): کدی که «سری»ِ یک کالاست ⇒ همان کالا به سبد، و فروش اول از همین
   *  سری برداشته می‌شود (`StockActions.lotForCode`). خاموش یا ناشناس ⇒ false (رفتارِ امروز).
   */
  /** CRM-16: کدِ کارتِ مشتری ⇒ همان مشتری برای فاکتور؛ خاموش، کالا یا ناشناس ⇒ false (رفتارِ امروز) */
  fun pickCard(code: String, dd: ShopData): Boolean {
    val c = ir.vil3ntec.tohid.data.CustomerCards.atTill(ir.vil3ntec.tohid.data.CustomerCardPref.on(context), dd, code) ?: return false
    if (cartStore.setDebtor(c.id)) { ScanFeedback.ok(context); toast(ir.vil3ntec.tohid.core.i18n.StrCustomerCard.picked(c.name)) }
    else { ScanFeedback.unknown(context); toast(CartStore.NOT_ADDED) }
    return true
  }

  fun addLot(code: String, dd: ShopData = d): Boolean {
    val (pid, lot) = ir.vil3ntec.tohid.data.StockActions.lotForCode(dd, code) ?: return false
    val product = ShopStore.product(dd, pid)?.takeIf { it.active } ?: return false
    val added = SalesEngine.addToCartCapped(cartStore.lines.value, pid, 1.0, ir.vil3ntec.tohid.data.ProductInfoMap.sellable(dd, pid))
    if (applyAdded(product, added.copy(cart = SalesEngine.setLineLot(added.cart, pid, lot))) && !added.capped) toast(ir.vil3ntec.tohid.core.i18n.StrSalePages.lotAdded(product.name, lot))
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
    val pay = if (payV2 && query.isBlank()) enterGate.emptyEnter(cart.isEmpty()) else { enterGate.mark(); false }
    if (quick?.enter(query) == true) { query = ""; return } // S03: Enter پس از بارکدِ ناشناس ⇒ ثبتِ سریع
    if (scaleLabel(query, d)) { query = ""; return }
    if (payV2 && query.isBlank()) { if (pay) payOpen = true; return } // بی پنلِ پرداخت همان «هیچ کار» ِ امروز
    if (addPack(parsed.second, parsed.first, d, index)) { query = ""; return } // PRD-04/S05: «۲*کارتن» با قیمتِ لیستِ بسته
    when (val hit = PosSearch.resolve(query, index, d.products, matches, hint, packIndex)) {
      PosHit.None -> Unit
      is PosHit.Inactive -> toast(StrSale.inactive(hit.product.name))
      is PosHit.Ambiguous -> { ScanFeedback.unknown(context); toast(StrSale.ambiguousBarcode(hit.code)) }
      is PosHit.Add -> { add(hit.product, hit.count); query = "" }
      is PosHit.Unknown -> if (addLot(hit.code)) query = "" else if (quick != null) { ScanFeedback.unknown(context); quick.unknown(hit.code, scope, snackbar) { freeLine = true } } else {
        ScanFeedback.unknown(context)
        scope.launch {
          val r = snackbar.showSnackbar(StrSale.notFound(hit.code), actionLabel = StrSale.registerItemAlt, duration = SnackbarDuration.Long)
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
    enterGate.mark(); val dd = latestD
    if (scaleLabel(code, dd)) return true
    //  PRD-04: بارکدِ بسته ⇒ اندازهٔ بسته (S05: با «واحدِ بزرگ» قیمتِ لیستِ بسته)؛ همان کد بارکدِ کالا هم (تعارضِ آفلاین) ⇒ مبهم، هیچ‌کدام
    val items = ShopStore.barcodeIndex(dd)
    if (addPack(code, 1.0, dd, items)) return true
    when (val hit = BarcodeMatch.find(code, items)) {
      is BarcodeMatch.Hit.Product -> ShopStore.product(dd, hit.id)?.let {
        if (!it.active) toast(StrSale.inactive(it.name)) else add(it, 1.0)
      }
      //  CRM-16: کارتِ مشتری (`CustomerCardPref`، پیش‌فرض خاموش) — فقط کدی که هیچ کالایی نمی‌خواند
      else -> if (!addLot(code, dd) && !pickCard(code, dd)) if (quick != null) { ScanFeedback.unknown(context); quick.unknown(code, scope, snackbar) { freeLine = true } } else scope.launch {
        ScanFeedback.unknown(context)
        val r = snackbar.showSnackbar(StrSale.barcodeUnregistered(code), actionLabel = StrSale.registerItemAlt, duration = SnackbarDuration.Long)
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
    seller = invoiceSeller.ifBlank { seller },
    lineSellers = lineSellerOn, // POS-23: خاموش ⇒ قلم بی فروشندهٔ ردیف، همان امروز
    discountType = if (discountAmount) SalesEngine.DiscountType.AMOUNT else SalesEngine.DiscountType.PERCENT,
    //  F0-06: خالی ⇐ صفر؛ نامعتبر ⇐ خطای کنارِ کادر و `finish` ثبت نمی‌کند
    discountValue = if (canDiscount) parseNumber(discountText) ?: 0.0 else 0.0,
    payment = payment,
    paidAmount = parseNumber(paidText) ?: 0.0,
    debtorId = cartDebtorId,
    blockExpired = blockExpired,
    rules = checkoutRules,
    cashRounding = cashStep, tax = remember { ir.vil3ntec.tohid.data.SaleTaxPref.active(context) }, // POS-37: خاموش ⇒ null
  )
  val totals = SalesEngine.totals(d, loyal, checkout)
  //  PAY-09: نقدِ کاملِ پرداختِ ساده ⇒ ردیفِ گرد و «قابلِ پرداخت» با آن (همان حسابِ `SalesEngine.record`)
  val rounding = if (!payV2 && payment == SalesEngine.Payment.CASH) ir.vil3ntec.tohid.data.PayFlow.cashRounding(totals.finalTotal, cashStep) else 0.0
  //  POS-12: کادرهایی که `checkout` از آن‌ها ساخته شد (جابه‌جایی همین حالا عوضشان کرده باشد ⇒ ثبت نه)
  val inputsFrom = listOf<Any?>(discountText, discountAmount, paidText, payment)
  val discountBad = canDiscount && numberInvalid(discountText)
  val paidBad = payment == SalesEngine.Payment.CREDIT && numberInvalid(paidText)
  //  ⛔ نسیه بی مشتریِ موجود و فعال (`CreditDebtor`، همان قاعدهٔ `SalesEngine.record`) ⇒ F9 خاموش، پیام و F2 کنارِ «پرداختی»
  val deskCredit = if (payV2) null else ir.vil3ntec.tohid.data.CreditDebtor.tillBlock(d, cartDebtorId, ir.vil3ntec.tohid.data.CreditDebtor.plainOwed(payment == SalesEngine.Payment.CREDIT, totals.finalTotal - (parseNumber(paidText) ?: 0.0).coerceIn(0.0, totals.finalTotal)), split = false)

  //  S08: تخفیفِ بیش از سقف ⇒ «درخواستِ تأیید» با PINِ مدیر — همان تصمیمِ سرور
  var approval by remember { mutableStateOf<ir.vil3ntec.tohid.core.perm.ApprovalSlip.Slip?>(null) }
  var askApproval by remember { mutableStateOf(false) }
  val saleKey = remember(cart) { if (cart.isEmpty()) null else cartStore.saleKey() }
  //  POS-21 (پشتِ `LinePolicy.on`، پیش‌فرض خاموش): قیمتِ دستیِ زیرِ فهرست و تخفیفِ ردیف هم تخفیف‌اند
  val lineGate = remember { ir.vil3ntec.tohid.data.LinePolicyPrefs.on(context) }
  val basis = DiscountGate.basis(lineGate, d, priced, promo, totals)
  val discountDecision = ir.vil3ntec.tohid.data.ShopRole.decide(
    context, "sales.discount", DiscountGate.req(basis.discount, basis.subtotal),
  )
  val gate = DiscountGate.state(discountDecision.decision, saleKey, basis.discount, basis.subtotal, approval)

  //  PAY-21: درگاه‌های تسویه (حساب‌پی) — پیش از گرفتنِ پول، ثبت‌شدنیِ همین فاکتور سنجیده می‌شود
  val gateways = ir.vil3ntec.tohid.ui.components.pos.GatewayHook(remember { ir.vil3ntec.tohid.pay.platform.HesabPayEnv.of(context) }, saleKey) {
    (SalesEngine.record(d, loyal, checkout.copy(payment = SalesEngine.Payment.CASH), ir.vil3ntec.tohid.todayIso(), 0L, ::newId, saleId = saleKey) as? SalesEngine.Result.Failed)?.message
  }
  //  CRM-03 / S07 (پشتِ `ShopPolicy.creditGate`، پیش‌فرض خاموش): نسیهٔ بیش از سقف ⇒ «درخواستِ تأیید»
  val creditGateOn = remember { ir.vil3ntec.tohid.data.ShopPolicy.creditGate(context) }
  //  تأییدِ بیش از سقف در حالت نگه داشته نمی‌شود — مثلِ گوشی (`QuickSale`، `Sale`) فقط به همان یک تلاشِ
  //  `finish` می‌رود. اگر آن تلاش ثبت نکند (سبد عوض شد، Busy، NotSaved…)، سبدِ دیگرِ همان مشتری تا همان
  //  مبلغ با تأییدِ کهنه ثبت نمی‌شود.
  /** بیش از سقف و تسویهٔ همان لحظه (پنلِ پرداخت) تا پس از تأیید همان ثبت شود */
  var creditAsk by remember { mutableStateOf<Pair<ir.vil3ntec.tohid.core.credit.CreditGate.Over, ir.vil3ntec.tohid.core.pay.Settlement?>?>(null) }
  //  STF-03 — سقف‌هایی که سرور برای همین دکان اجرا می‌کند (قرض، تغییرِ قیمت، تخفیفِ پنهان؛ `SaleCapGate`)؛ پرچم‌های خاموش ⇒ هیچ.
  //  برگه‌های قرض و قیمت جدا از برگهٔ تخفیف؛ تأییدِ گرفته‌شده همان ثبت را دوباره می‌زند (با همان تسویه و تأییدِ اعتبار)
  val capRules = remember { ir.vil3ntec.tohid.data.ShopRole.saleCaps(context) }
  var capSlips by remember(saleKey) { mutableStateOf<Map<String, ir.vil3ntec.tohid.core.perm.ApprovalSlip.Slip>>(emptyMap()) }
  var capAsk by remember { mutableStateOf<Triple<ir.vil3ntec.tohid.data.SaleCapGate.Ask, ir.vil3ntec.tohid.core.pay.Settlement?, ir.vil3ntec.tohid.core.credit.CreditGate.Approval?>?>(null) }
  //  S22 (`ExpiryPref.gate`، پیش‌فرض خاموش): منقضی فقط با تأیید؛ تأیید در حالت نمی‌ماند و فقط به همان یک تلاشِ بعدیِ `finish` می‌رود
  val expiryGateOn = tillGuards.expiryGate; val expiryOk = remember { arrayOfNulls<ir.vil3ntec.tohid.core.stock.ExpiryGate.Approval>(1) }
  var expiryAsk by remember { mutableStateOf<Triple<Map<String, Long>, ir.vil3ntec.tohid.core.pay.Settlement?, ir.vil3ntec.tohid.core.credit.CreditGate.Approval?>?>(null) }

  /**
   *  `settled` = تسویهٔ پنلِ پرداخت (با پرچم)؛ با پرچم و بی آن (F12، Ctrl+Enter) ⇒ نقدِ دقیق.
   *  `creditApproval` فقط از «درخواستِ تأیید» ِ همین فروش و فقط برای همین یک تلاش.
   */
  fun finish(settled: ir.vil3ntec.tohid.core.pay.Settlement? = null, creditApproval: ir.vil3ntec.tohid.core.credit.CreditGate.Approval? = null) {
    val expiryApproval = expiryOk[0]; expiryOk[0] = null
    if (cart.isEmpty()) { toast(Str.cartEmpty); return }
    //  POS-12: صفحه هنوز سبدِ پیش از جابه‌جایی را دارد ⇒ ردیف‌هایش با کلیدِ فاکتورِ دیگر ثبت می‌شد
    if (!ir.vil3ntec.tohid.data.HeldRules.shownIsCurrent(pricedFrom, cartStore.lines.value) || checkout.debtorId != cartStore.debtorId.value ||
      inputsFrom != listOf<Any?>(discountText, discountAmount, paidText, payment)
    ) {
      toast(StrPos.cartChangedRetry); return
    }
    if (discountBad || (!payV2 && settled == null && paidBad)) { toast(StrSale.badNumber(if (discountBad) Str.discount else StrSale.received)); return }
    if (!payV2 && settled == null) deskCredit?.let { toast(it); pickDebtor = true; return }
    if (gate == DiscountGate.State.DENIED) { toast(StrPos.discountForbidden); return }
    if (gate == DiscountGate.State.NEEDS_APPROVAL) { askApproval = true; return }
    val signed = if (gate == DiscountGate.State.APPROVED) approval?.let(DiscountGate::json) else null
    //  PAY-21: تسویهٔ آمادهٔ حساب‌پی با پرچم و بی آن پذیرفته است
    val st = settled ?: if (!payV2) null else when (
      val r = ir.vil3ntec.tohid.data.PayFlow.checkout(
        //  PAY-09: «نقدِ دقیق» با گرد — همان مبلغی که مشتری می‌دهد
        (totals.finalTotal + ir.vil3ntec.tohid.data.PayFlow.cashRounding(totals.finalTotal, cashStep)).let { exact ->
          ir.vil3ntec.tohid.data.PayFlow.Draft(totals.finalTotal, cash = exact.toBigDecimal().stripTrailingZeros().toPlainString(), customerId = cartDebtorId, cashRounding = cashStep)
        },
      )
    ) {
      is ir.vil3ntec.tohid.data.PayFlow.Outcome.Ok -> r.value
      is ir.vil3ntec.tohid.data.PayFlow.Outcome.Bad -> { toast(ir.vil3ntec.tohid.data.PayFlow.message(r.problem)); return }
    }
    //  STF-03 — برگه‌های همین فاکتور (`SaleCapGate`): با پرچمِ قیمتِ سرور برگهٔ تخفیف «تخفیفِ پنهان» را هم می‌پوشاند (سرور برگهٔ
    //  نالازم را مصرف نمی‌کند)؛ برگهٔ قرض و قیمت جدا
    val slips = buildMap { approval?.let { put(ir.vil3ntec.tohid.core.perm.SaleCaps.DISCOUNT, it) }; putAll(capSlips) }
    val sale = checkout.copy(settlement = st, approval = signed ?: if (capRules.price) approval?.let(DiscountGate::json) else null, creditGate = creditGateOn, creditApproval = creditApproval)
      .copy(creditCapSlip = ir.vil3ntec.tohid.data.SaleCapGate.json(slips, ir.vil3ntec.tohid.core.perm.SaleCaps.CREDIT),
        priceSlip = ir.vil3ntec.tohid.data.SaleCapGate.json(slips, ir.vil3ntec.tohid.core.perm.SaleCaps.PRICE),
        freeLineSlip = ir.vil3ntec.tohid.data.SaleCapGate.json(slips, ir.vil3ntec.tohid.core.perm.SaleCaps.FREE_LINE))
    //  CRM-03 / S07: بی تأییدِ مدیر هیچ چیز ثبت نمی‌شود (`SalesEngine.record` هم دوباره می‌سنجد)
    val over = SalesEngine.creditOver(d, loyal, sale)
    if (over != null && ir.vil3ntec.tohid.core.credit.CreditGate.state(over, sale.debtorId.orEmpty(), creditApproval) ==
      ir.vil3ntec.tohid.core.credit.CreditGate.State.NEEDS_APPROVAL
    ) {
      payOpen = false
      creditAsk = over to st
      return
    }
    if (expiryGateOn) SalesEngine.expiredNeed(d, loyal, ir.vil3ntec.tohid.todayIso()).let { need ->
      if (!ir.vil3ntec.tohid.core.stock.ExpiryGate.covers(need, expiryApproval)) { payOpen = false; expiryAsk = Triple(need, st, creditApproval); return }
    }
    //  STF-03 — پیش‌سنجی روی **همان** فاکتوری که ثبت و فرستاده می‌شود (پس از تخفیفِ خودکار و امتیاز)، با همان قاعدهٔ سرور
    //  (`SaleCaps`): «ممنوع» ⇒ پیام؛ «نیازمندِ تأیید» ⇒ پنجرهٔ تأیید («پرسیدن از مدیر» یا PIN)
    fun recordAll(key: String, token: String?): SalesEngine.Result = ir.vil3ntec.tohid.ui.components.pos.LoyaltyAtTill.post(context, ir.vil3ntec.tohid.data.PromoEngine.stamp(SalesEngine.record(d, loyal, sale.copy(note = cartStore.note.value).copy(expiryGate = expiryGateOn, expiryApproval = expiryApproval), ir.vil3ntec.tohid.todayIso(), System.currentTimeMillis(), ::newId,
      series = ir.vil3ntec.tohid.sync.DocSeries.current(context), saleId = key, receiptToken = token), promo), loyalty, sale.seller)
    if (capRules.active && saleKey != null) {
      val trial = recordAll(saleKey, null) as? SalesEngine.Result.Ok
      val b = trial?.let { ir.vil3ntec.tohid.data.SaleCapGate.check(capRules, it.data, it.saleId, slips).blocked }
      if (b != null) {
        if (b.state == ir.vil3ntec.tohid.core.perm.SaleCaps.State.DENIED) { toast(ir.vil3ntec.tohid.data.SaleCapGate.message(b)); return }
        payOpen = false
        expiryOk[0] = expiryApproval
        capAsk = Triple(b, st, creditApproval)
        return
      }
    }
    //  POS-14: F9ِ پشتِ‌سرِهم یک فروش می‌سازد؛ سبد فقط پس از نوشتنِ دفتر پاک می‌شود
    //  S2: با تخفیفِ خودکار، نشانِ قیمتِ فهرست و سهمِ هر قاعده روی فاکتور (خاموش ⇒ همان)
    //  STF-03: آخرین سد — فاکتوری که سرور بی خبر رد می‌کرد نوشته نمی‌شود (`SaleCapGate.guard`)
    scope.launch { when (val posted = cartStore.post(store, d, gate = { key -> ir.vil3ntec.tohid.ui.PromoLive.redeemGate(context, key, promo) }) { key ->
      ir.vil3ntec.tohid.data.SaleCapGate.guard(capRules, recordAll(key, ir.vil3ntec.tohid.print.Receipts.newToken(context)), slips) }) {
      SalePost.Outcome.Busy -> Unit
      SalePost.Outcome.NotSaved -> toast(SalePost.NOT_SAVED)
      is SalePost.Outcome.Refused -> toast(posted.message)
      is SalePost.Outcome.Done -> {
        val r = posted.ok
        ir.vil3ntec.tohid.hw.CustomerFacing.sold(r.data.sales.last().finalTotal)
        selected = null; discountText = ""; discountAmount = false; paidText = ""; payment = SalesEngine.Payment.CASH; invoiceSeller = ""; approval = null; capSlips = emptyMap()
        payOpen = false
        lastInvoice = r.data to r.saleId
        val change = st?.change ?: 0L
        toast(StrPos.saleRecorded(money(r.data.sales.last().finalTotal)) + (if (change > 0) " — " + StrPos.changeDue(money(ir.vil3ntec.tohid.data.PayFlow.major(change))) else "") + " — " + StrPos.f8Print)
        refocus()
      }
    } }
  }

  fun step(id: String, sign: Int) {
    if (cart.none { it.productId == id }) return
    val unit = ShopStore.product(d, id)?.unit.orEmpty()
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
      val p = ShopStore.product(d, line.productId)
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
  val gridState = rememberLazyGridState()
  val tableState = rememberLazyListState()
  LaunchedEffect(category) { // فقط اگر از سر رفته: بارِ نخست (و فهرستِ پنهان) منتظرِ نخستین چیدمان نمی‌ماند (`ScenarioS01DesktopTest`)
    if (gridState.firstVisibleItemIndex + gridState.firstVisibleItemScrollOffset > 0) runCatching { gridState.scrollToItem(0) }
    if (tableState.firstVisibleItemIndex + tableState.firstVisibleItemScrollOffset > 0) runCatching { tableState.scrollToItem(0) }
  }
  val listState = rememberLazyListState()
  LaunchedEffect(selected, cart.size) {
    val i = cart.indexOfFirst { it.productId == selected }
    if (i >= 0) runCatching { listState.animateScrollToItem(i) }
  }
  val c = Shop.colors

  //  POS-12: چند فاکتورِ هم‌زمان (تنظیمِ «چند فاکتورِ هم‌زمان»، پیش‌فرض خاموش)
  val dataLoaded by store.loaded.collectAsState()
  val held = ir.vil3ntec.tohid.ui.components.pos.rememberHeldInvoices(
    cartStore, d, snackbar, dataLoaded,
    extras = { ir.vil3ntec.tohid.data.HeldExtras(discountText, payment, paidText, discountAmount, invoiceSeller) },
    onSwitched = { x ->
      discountText = x.discount; discountAmount = x.discountAmount; paidText = x.paid; invoiceSeller = x.seller; selected = null
      //  پنلِ پرداخت مالِ فاکتورِ قبلی بود
      payOpen = false
      //  روشِ پرداخت با فاکتور برمی‌گردد (نسیهٔ بی‌مشتری هم: `finish` مشتری می‌خواهد و فهرست را باز می‌کند)
      val debtorNow = cartStore.debtorId.value
      payment = ir.vil3ntec.tohid.data.HeldRules.paymentAfterSwitch(x.payment, debtorNow)
      switchedDebtor = debtorNow
    },
    onFocusBack = { refocus() },
  )
  //  POS-05/INV-03: «قابل‌فروش» = موجودی − نگه‌داشته‌های همین صندوق − رزروِ سفارشِ اینترنتی (همان گوشی؛ بی هر دو همان موجودی)
  val heldQty = ir.vil3ntec.tohid.ui.components.pos.rememberSellableHeld(held.store.book.collectAsState().value, d)
  //  کارت و ردیفِ کالا: زدن ⇐ افزودن (با پیشوندِ «۳*» اگر نوشته شده). − / + فقط در سبد است
  val productCb = PosProductCallbacks(stockOf = { ir.vil3ntec.tohid.data.HeldStock.sellable(ShopStore.stock(d, it), it, heldQty) },
    qtyOf = { id -> PosCartOps.qtyOf(cart, id) }, onAdd = { p -> add(p, parsed.first); query = ""; refocus() })

  //  POS-07 (فقط کامپیوترِ لمسی): صفحه‌کلیدِ عددی روی ردیفِ انتخاب‌شده — همان راهِ کادرِ ردیف (`PosKeypad`)
  val pad = rememberTouchPad(remember { ir.vil3ntec.tohid.data.LinePolicyPrefs.of(context) }, selected); val kb = ir.vil3ntec.tohid.ui.components.pos.rememberTouchKeyboard() // POS-31: صفحه‌کلیدِ حروف

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
          placeholder = { Text(StrSale.deskSearchHint, maxLines = 1, overflow = TextOverflow.Ellipsis, color = c.muted) },
          modifier = Modifier
            .weight(1f)
            .focusRequester(searchFocus)
            .onPreviewKeyEvent { e ->
              if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
              if (ir.vil3ntec.tohid.ui.components.pos.PosMoreKeysPrefs.field(moreKeys, e, query.isEmpty(), cart.isEmpty()) != null) { confirmClear = true; return@onPreviewKeyEvent true }
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
        ViewModeToggle(view); VisualSearchEntry(rememberVisualSearchApi(), d.products, ir.vil3ntec.tohid.core.product.VisualSearch.Place.TILL, Modifier.padding(start = 10.dp)) { t -> if (t is ir.vil3ntec.tohid.core.product.VisualSearch.Tap.Search) { query = t.query; refocus() } } // AI-11: «مشابه» فقط در کادرِ جست‌وجو، نه در سبد
        //  POS-31: کلیدِ «حالتِ لمسی» فقط با تنظیمِ روشن (خاموش ⇒ همان نوارِ امروز)
        if (PosTouchPrefs.showToggle()) {
          Spacer(Modifier.width(10.dp))
          TouchToggle(PosTouchPrefs.active.value, target) { PosTouchPrefs.setActive(context, it); refocus() }; ir.vil3ntec.tohid.ui.components.pos.TouchKeysToggle(kb, touchExtras, target) { refocus() }
        }
      }
      ir.vil3ntec.tohid.ui.components.pos.TouchKeysPanel(kb, touchExtras, target) { query = ir.vil3ntec.tohid.core.pos.TouchKeyboard.apply(query, it); refocus() }; Spacer(Modifier.height(12.dp))
      if (term.isNotEmpty()) {
        //  با عبارت، نتیجه از همهٔ دسته‌هاست — تراشه‌ها جایشان را به همین خط می‌دهند
        Text(
          StrSale.resultsAll(shown.size),
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
            StrSale.noMatch(term),
            StrSale.noMatchHint,
          )
          shown.isEmpty() -> PosEmpty(
            StrSale.nothingToSell,
            if (d.products.none { it.active }) StrSale.addItemsHint else StrSale.noActiveInCategory,
          )
          view.mode == ViewMode.TABLE -> ProductTable(shown, highlight, target, showPhotos, productCb, tableState, Modifier.fillMaxSize())
          else -> ProductGrid(
            shown, highlight, showPhotos, productCb, gridState,
            minCell = posCardMin(maxWidth, touch = touch),
            modifier = Modifier.fillMaxSize(),
          )
        }
      }
      //  POS-31: کشیدنِ انگشت در Compose Desktop اسکرول نمی‌کند ⇒ دکمهٔ صفحه (فقط کامپیوترِ لمسی)
      if (touchExtras && shown.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        if (view.mode == ViewMode.TABLE) ListPageButtons(tableState, target, onDone = { refocus() }) else GridPageButtons(gridState, target, onDone = { refocus() })
      }
      //  یادآوریِ کلیدها — باریک و کم‌رنگ؛ فهرستِ کامل در F1
      PosHintLine(StrPos.keysLineF1)
    }
  }

  //  POS-38: «تحویلِ بعدی» فقط با تنظیمِ روشن، بخشِ سفارشِ روشن و اجازهٔ ثبتِ سفارش
  val deliverLater = ir.vil3ntec.tohid.ui.components.pos.deliverLaterReady()
  var deliverOpen by remember { mutableStateOf(false) }

  /* ─────────── ناحیهٔ «سبد خرید» ─────────── */
  @Composable
  fun Cart(modifier: Modifier) {
    OrderPanel(
      itemCount = cart.size,
      modifier = modifier,
      headerActions = {
        CartHeaderAction(StrSale.freeItemAlt, Icons.Filled.AddBox, KeyMap.SALE_FREE_ITEM.hint) { freeLine = true }
        if (returnsV2) CartHeaderAction(StrInvoices.returnItems, Icons.Filled.SwapHoriz, KeyMap.SALE_RETURN_MODE.hint) { returnMode = true }
        CartHeaderAction(StrPos.emptyCart, Icons.Filled.DeleteOutline, if (moreKeys) KeyMap.SALE_CLEAR.hint else null, enabled = cart.isNotEmpty(), danger = true) { confirmClear = true }
        //  POS-38 (پشتِ `DeliverLaterPrefs`، پیش‌فرض خاموش): سبد ⇐ سفارشِ ارسال، بی فروش
        if (deliverLater) {
          CartHeaderAction(ir.vil3ntec.tohid.core.i18n.StrDeliver.button, Icons.Filled.LocalShipping, null, enabled = cart.isNotEmpty() && !posting) {
            deliverOpen = true
          }
        }
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
        ir.vil3ntec.tohid.ui.components.pos.InvoiceNoteButton(cartStore.note.collectAsState().value, cart.isNotEmpty()) { cartStore.setNote(it).also { refocus() } }
        ir.vil3ntec.tohid.ui.components.pos.LineSellerButton(cart.isNotEmpty()) { lineSellerFor = selected; lineSellerOpen = true } // POS-23 (Shift+F2)
        ir.vil3ntec.tohid.ui.components.pos.LoyaltyBar(loyalty, Modifier.padding(top = 6.dp)) // PRM-13
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
          rowHints = if (moreKeys) listOf(KeyMap.SALE_EDIT_ROW.hint, KeyMap.SALE_DISCOUNT_ROW.hint, "${KeyMap.SALE_QTY_UP.hint} ${KeyMap.SALE_QTY_DOWN.hint}", KeyMap.SALE_DELETE_ROW.hint) else emptyList(),
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
        PaymentSummary(ir.vil3ntec.tohid.ui.components.pos.LoyaltyAtTill.rows(PromoSummary.rows(totals, promo), promo, loyalty) +
          listOfNotNull(rounding.takeIf { it != 0.0 }?.let { ir.vil3ntec.tohid.ui.components.pos.PosSummaryRow(ir.vil3ntec.tohid.core.i18n.StrRounding.line, kotlin.math.abs(it), negative = it < 0) }), totals.finalTotal + rounding) {
          if (canDiscount) {
            //  POS-20: درصد یا مبلغ — عددِ نوشته‌شده می‌ماند و فقط واحدش عوض می‌شود (همان پنجرهٔ تسویهٔ گوشی)؛
            //  ردیفِ «تخفیف» ِ بالای کادر همان دم مبلغِ کم‌شده را نشان می‌دهد
            if (amountChoosable || discountAmount) {
              Spacer(Modifier.height(6.dp))
              SegmentedChoice(
                options = listOf("percent" to ir.vil3ntec.tohid.core.i18n.StrLine.percent, "amount" to ir.vil3ntec.tohid.core.i18n.StrLine.amount),
                selected = if (discountAmount) "amount" else "percent",
                onSelect = { discountAmount = it == "amount"; refocus() },
              )
            }
            MiniNumberField(if (moreKeys) Str.discount + " · " + KeyMap.SALE_DISCOUNT_INVOICE.hint else Str.discount, discountText, { discountText = numericInput(it) }, discountBad, if (discountAmount) "؋" else "٪", discountFocus)
            //  S08: تخفیفِ بیش از سقف ⇒ «درخواستِ تأیید» با PINِ مدیر — همان تصمیمِ سرور
            when (gate) {
              DiscountGate.State.NEEDS_APPROVAL -> Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                ir.vil3ntec.tohid.ui.components.common.StatusPill(
                  StrPos.needsApproval, ir.vil3ntec.tohid.ui.components.common.PillTone.AWAITING,
                )
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = { askApproval = true }, enabled = !posting) { Text(StrPos.requestApprovalF9) }
              }
              DiscountGate.State.APPROVED -> ir.vil3ntec.tohid.ui.components.common.StatusPill(
                StrPos.approved, ir.vil3ntec.tohid.ui.components.common.PillTone.SUCCESS, Modifier.padding(top = 6.dp),
              )
              DiscountGate.State.DENIED -> ir.vil3ntec.tohid.ui.components.common.StatusPill(
                StrPos.discountForbidden, ir.vil3ntec.tohid.ui.components.common.PillTone.DANGER,
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
            StrPos.mixedPayHint,
            style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted,
          )
        } else {
          PaymentMethodGrid(PosPayments.configured(), payment, target) { m ->
            if (m.payment != payment) { payment = m.payment; if (m.needsCustomer && cartDebtorId == null) pickDebtor = true }
          }
          if (payment == SalesEngine.Payment.CREDIT) {
            MiniNumberField(StrPos.paidNowShort, paidText, { paidText = numericInput(it) }, paidBad, "؋")
            ir.vil3ntec.tohid.ui.components.pos.CreditDebtorHint(deskCredit) { pickDebtor = true }
          }
          //  PAY-21: حساب‌پی (کلِ فاکتور؛ با پنلِ ترکیبی در همان پنل و برای باقی‌مانده)
          ir.vil3ntec.tohid.ui.components.pos.CheckoutGateways(
            hook = gateways, total = totals.finalTotal,
            panel = null, customerId = cartDebtorId, busy = posting, modifier = Modifier.padding(top = 8.dp), onPaid = { st -> finish(st) },
            enabled = !discountBad && gate != DiscountGate.State.NEEDS_APPROVAL && gate != DiscountGate.State.DENIED,
          )
        }
      },
      actions = {
        OrderActions(
          postHint = KeyMap.SALE_PAY_PANEL.hint,
          printHint = KeyMap.SALE_REPRINT.hint,
          canPost = cart.isNotEmpty() && deskCredit == null,
          posting = posting,
          canPrint = lastInvoice != null,
          onPost = { if (payV2) payOpen = true else finish() },
          onPrint = { if (lastInvoice != null) showInvoice = true },
        )
      },
    )
  }

  /** سبد؛ در کامپیوترِ لمسی (POS-31) با دکمه‌های صفحه و صفحه‌کلیدِ عددی (POS-07) زیرش */
  @Composable
  fun CartArea(modifier: Modifier) {
    val line = cart.find { it.productId == selected }
    val product = line?.let { l -> ShopStore.product(d, l.productId) }
    if (!touchExtras) Cart(modifier) else TouchCartColumn(
      pad, listState, line?.let { if (it.free) it.label else product?.name.orEmpty() }, target,
      onApply = { if (!pad.apply(cart, selected, product) { cartStore.set(it) }) toast(StrPos.cartChangedRetry) },
      onFocusBack = { refocus() }, modifier = modifier,
    ) { Cart(it) }
  }

  Column(
    Modifier
      .fillMaxSize()
      .onPreviewKeyEvent { e ->
        //  POS-12: پیش از هر شرطی — وسطِ جابه‌جاییِ فاکتور همهٔ کلیدها خورده می‌شوند
        if (held.onKey(e)) return@onPreviewKeyEvent true
        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        if (PosKeys.returnMode(keyName(e.key), e.isPrimaryModifier, e.isShiftPressed, e.isAltPressed, returnsV2)) {
          returnMode = true
          return@onPreviewKeyEvent true
        }
        //  PAY-19: Shift+F8 همیشه مالِ کشو است (دیگر چاپِ دوبارهٔ F8 نیست)؛ بی چاپگرِ کشودار یا بی `drawer.open` فقط پیام
        if (PosKeys.noSaleDrawer(keyName(e.key), e.isPrimaryModifier, e.isShiftPressed, e.isAltPressed)) {
          val printer = ir.vil3ntec.tohid.print.PrinterRegistry.defaultSaved(context)?.takeIf { it.drawer }
          ir.vil3ntec.tohid.data.DrawerNoSale.keyGate(printer != null, ir.vil3ntec.tohid.data.ShopRole.canOpenDrawer(context))
            ?.let(::toast) ?: run { noSaleDrawer = printer }
          return@onPreviewKeyEvent true
        }
        //  POS-23: Shift+F2 ⇒ فروشندهٔ ردیفِ انتخاب‌شده (بی ردیف ⇒ همهٔ ردیف‌ها)، فقط با «فروشندهٔ هر ردیف» ِ روشن
        if (PosKeys.lineSeller(keyName(e.key), e.isPrimaryModifier, e.isShiftPressed, e.isAltPressed, lineSellerOn)) {
          if (cart.isNotEmpty()) { lineSellerFor = selected; lineSellerOpen = true }; return@onPreviewKeyEvent true }
        //  POS-18: Ctrl+R ⇒ خرده ⇄ عمده، فقط با «لیستِ قیمت در صندوق» ِ روشن؛ خاموش ⇒ کلید مالِ صندوق نیست
        if (PosKeys.priceList(keyName(e.key), e.isPrimaryModifier, e.isShiftPressed, e.isAltPressed, priceListOn)) {
          priceListToggles++
          return@onPreviewKeyEvent true
        }
        when (ir.vil3ntec.tohid.ui.components.pos.PosMoreKeysPrefs.screen(moreKeys, e)) {
          ir.vil3ntec.tohid.core.pos.PosMoreKeys.Act.DISCOUNT_INVOICE -> { if (canDiscount) runCatching { discountFocus.requestFocus() } else toast(StrPos.discountForbidden); return@onPreviewKeyEvent true }
          //  POS-41: F5 ⇒ برگهٔ ردیفِ انتخاب‌شده با فوکوس روی «تخفیفِ ردیف»؛ بی ردیف کلید مالِ صندوق نیست
          ir.vil3ntec.tohid.core.pos.PosMoreKeys.Act.DISCOUNT_ROW -> selected?.let { rowDiscount = true; editLine = it; return@onPreviewKeyEvent true }
          else -> Unit
        }
        when (PosKeys.screen(keyName(e.key), e.isPrimaryModifier)) {
          PosAction.FINISH -> { if (payV2 && keyName(e.key) == KeyName.F9) { if (cart.isNotEmpty()) payOpen = true } else finish(); true }
          PosAction.CUSTOMER -> { pickDebtor = true; true }
          PosAction.EDIT_ROW -> { selected?.let { editLine = it }; true }
          PosAction.FREE_ITEM -> { freeLine = true; true }
          PosAction.REPRINT -> { if (lastInvoice != null) showInvoice = true; true }
          PosAction.PRICE_CHECK -> if (ir.vil3ntec.tohid.ui.components.pos.PosPriceCheckPrefs.on.value) { priceCheck = true; true } else false
          PosAction.UNIT -> if (selected != null && ir.vil3ntec.tohid.data.TillGoodsPref.units(context)) { unitNext = true; editLine = selected; true } else false // S05، خاموش ⇒ کلید مالِ صندوق نیست
          //  مشتریِ همین فاکتور ⇒ مستقیم؛ وگرنه اول انتخابِ مشتری. خاموش ⇒ کلید مالِ صندوق نیست
          PosAction.RECEIVE -> if (!tillReceive) false else {
            receiveKey = newId(); if (cartDebtorId != null) receiveFor = cartDebtorId else receivePick = true; true
          }
          else -> false
        }
      }
      .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
  ) {
    ir.vil3ntec.tohid.ui.components.pos.HeldInvoiceBar(held, Modifier.padding(bottom = 8.dp)); ir.vil3ntec.tohid.ui.components.pos.QuoteBar(d, cartStore, ::toast, Modifier.padding(bottom = 8.dp)); ir.vil3ntec.tohid.ui.components.pos.SpecialOrderBar(d, store, cartDebtorId, ::toast, Modifier.padding(bottom = 8.dp)) // پیش‌فاکتور (POS-25، `QuotePref`) و سفارشِ ویژه (POS-26، `SpecialOrderPref`)
    ir.vil3ntec.tohid.ui.components.pos.GiftCardBar(d, store, ::toast, Modifier.padding(bottom = 8.dp)); ir.vil3ntec.tohid.ui.components.pos.LayawayBar(d, store, cartStore, ::toast, Modifier.padding(bottom = 8.dp)) // کارتِ هدیه (PAY-24، `GiftCardPref`) و فروشِ امانی (POS-27، `LayawayPref`)
    ir.vil3ntec.tohid.ui.components.pos.PriceListAtPos(cartStore, d, cart, cartDebtorId, toggles = priceListToggles) // POS-18 (Ctrl+R)
    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
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
          CartArea(Modifier.width(PosLayout.cartWidth(w).dp).fillMaxHeight())
        }
      } else {
        Column(Modifier.fillMaxSize()) {
          Catalogue(Modifier.weight(1f).fillMaxWidth())
          Spacer(Modifier.height(12.dp))
          CartArea(Modifier.weight(1f).fillMaxWidth())
        }
      }
    }
  }

  //  خالی کردنِ سبد کارِ پاک‌کننده است ⇐ تأیید، با فوکوسِ پیش‌فرض روی «انصراف» (۵.۱ بند ۱۱)
  if (confirmClear) {
    val safe = remember { FocusRequester() }
    AlertDialog(
      onDismissRequest = { confirmClear = false; refocus() },
      title = { Text(StrSale.newCartQ) },
      text = { Text(StrSale.clearCartWarn(cart.size)) },
      confirmButton = {
        TextButton(onClick = { cartStore.clear(); selected = null; confirmClear = false; refocus() }) {
          Text(StrSale.clearIt, color = Shop.colors.danger)
        }
      },
      dismissButton = {
        TextButton(onClick = { confirmClear = false; refocus() }, modifier = Modifier.focusRequester(safe)) { Text(Str.cancel) }
        LaunchedEffect(Unit) { runCatching { safe.requestFocus() } }
      },
    )
  }

  //  POS-38: سفارشِ ارسال از همین سبد (با تخفیفِ خودکار، `priced`)؛ پس از ثبت سبد خالی — هیچ فروشی این‌جا نیست
  if (deliverOpen) {
    ir.vil3ntec.tohid.ui.components.pos.DeliverLaterSheetWired(d, priced, onClose = { deliverOpen = false; refocus() }) { o ->
      deliverOpen = false
      cartStore.clear()
      selected = null
      toast(ir.vil3ntec.tohid.core.i18n.StrDeliver.created(o.orderNo))
      refocus()
    }
  }

  if (receivePick) {
    DebtorPicker(d = d, selected = null, touch = touchExtras, onClose = { receivePick = false; refocus() }, onPick = { id -> receivePick = false; receiveFor = id })
  }
  receiveFor?.let { id ->
    val name = d.debtors.find { it.id == id }?.name
    if (name != null) {
      TillReceiveDialog(
        name = name,
        balanceMinor = ir.vil3ntec.tohid.data.TillReceiveFlow.balanceMinor(d, id),
        busy = receiveBusy,
        gate = { content -> VipGate(Str.debtors) { content() } },
        onDismiss = { receiveFor = null; refocus() },
        onSubmit = { amount, notes, prepay ->
          receiveBusy = true
          val key = receiveKey
          scope.launch {
            //  ⛔ روی دفترِ همین لحظهٔ دیسک (`DebtEntry.record`)، نه `d` ِ صفحه؛ همان کلید ⇒ یک ردیف، حتی با دو ضربه
            val r = ir.vil3ntec.tohid.data.TillReceivePref.record(store, id, amount, notes, key, prepay)
            receiveBusy = false
            //  ضربهٔ دوم (`again`) پیامِ دوم نمی‌گیرد؛ «ثبت نشد» ِ قفل/دیسک از `AppRoot`
            r.show(::toast, ir.vil3ntec.tohid.core.i18n.StrSalePages.received(money(ir.vil3ntec.tohid.data.PayFlow.major(amount ?: 0L)), name)) {
              receiveFor = null; receiptOf = Triple(store.data.value, id, key)
            }
          }
        },
      )
    }
  }
  //  رسیدِ همان دریافت — همان رسیدِ دفترِ قرض (چاپ/کپی)، روی دفترِ پس از ثبت
  receiptOf?.let { (data, debtorId, txId) ->
    DebtReceiptDialog(data, debtorId, txId, onDismiss = { receiptOf = null; refocus() })
  }
  if (pickDebtor) {
    DebtorPicker(d = d, selected = cartDebtorId, touch = touchExtras, onClose = { pickDebtor = false; refocus(); if (payAfterPick) { payAfterPick = false; payOpen = true } }, onPick = { id ->
      cartStore.setDebtor(id); pickDebtor = false; refocus(); if (payAfterPick) { payAfterPick = false; payOpen = true }
    }, onAdd = if (!touchExtras) null else { name -> tillNewCustomer(d, name, store) { id -> cartStore.setDebtor(id); pickDebtor = false; refocus(); if (payAfterPick) { payAfterPick = false; payOpen = true } } }) // POS-31/S18 گامِ ۴
  }
  if (lineSellerOpen && lineSellerOn) ir.vil3ntec.tohid.ui.components.pos.LineSellerSheet(d, cart, seller, lineSellerFor, onClose = { lineSellerOpen = false; refocus() }) { next ->
    cartStore.set(next).also { if (!it) toast(StrPos.cartChangedRetry) } } // POS-23 — همان برگهٔ صندوقِ گوشی
  ir.vil3ntec.tohid.ui.components.pos.QuickTillDialog(quick, store, cartStore) { m -> m?.let(::toast); refocus() } // S03
  if (freeLine) {
    FreeLineDialog(onDismiss = { freeLine = false; refocus() }, onConfirm = { label, price, count ->
      cartStore.set(SalesEngine.addFreeLine(cart, label, price, count, ::newId)); freeLine = false; refocus()
    }, linePolicy = remember { ir.vil3ntec.tohid.data.LinePolicyPrefs.of(context) })
  }
  editLine?.let { id ->
    val line = cart.find { it.productId == id }
    if (line == null) { editLine = null; rowDiscount = false } else {
      val p = ShopStore.product(d, id)
      val base = if (line.free) (line.unitPrice ?: 0.0) else (p?.salePrice ?: 0.0)
      CartLineDialog(
        name = if (line.free) line.label else p?.name.orEmpty(),
        unit = p?.unit.orEmpty(),
        quantity = line.quantity,
        unitPrice = line.unitPrice ?: base,
        basePrice = base,
        custom = line.free || line.unitPrice != null,
        free = line.free,
        linePolicy = remember { ir.vil3ntec.tohid.data.LinePolicyPrefs.of(context) },
        //  S45: ردِ قاعدهٔ جهت/سقفِ قلمِ آزاد ⇒ «درخواستِ تأیید» (فقط با «قیمت و تخفیفِ ردیف» ِ روشن)
        priceAsk = remember(id) { ir.vil3ntec.tohid.data.LinePolicyPrefs.ask(context, store, scope, if (line.free) line.label else p?.name.orEmpty(), if (line.free) null else id) },
        saleUnit = p?.saleUnit.orEmpty(),
        unitFactor = p?.unitFactor ?: 1.0, units = remember(id, unitNext) { ir.vil3ntec.tohid.data.TillGoodsPref.plan(context, p, line, unitNext) },
        //  POS-22 (پشتِ `PriceWhyPrefs`، پیش‌فرض خاموش)
        why = if (ir.vil3ntec.tohid.ui.components.pos.priceWhyOn()) ir.vil3ntec.tohid.data.PriceWhyLine.rows(d, cart, priced, promo, id) else null,
        note = ir.vil3ntec.tohid.ui.components.pos.posLineNote(line.note) { n -> cartStore.set(ir.vil3ntec.tohid.data.CartNotes.set(cartStore.lines.value, id, n)) }, serials = ir.vil3ntec.tohid.ui.components.pos.posLineSerials(d, line) { s -> cartStore.set(ir.vil3ntec.tohid.data.CartSerials.set(cartStore.lines.value, id, s)) }, // POS-28
        focusDiscount = rowDiscount,
        pinned = if (line.free || p == null) null else id in pins, onPin = { ir.vil3ntec.tohid.ui.v3.sales.cart.TillPinsPrefs.toggleSay(context, id, p?.name.orEmpty())?.let(::toast) }, // POS-06، قلمِ آزاد نه
        onDismiss = { editLine = null; unitNext = false; rowDiscount = false; refocus() },
        onConfirm = { quantity, price ->
          var next = SalesEngine.setCartQty(cart, id, quantity)
          next = SalesEngine.setLinePrice(next, id, if (line.free) (price ?: line.unitPrice) else price)
          cartStore.set(next); editLine = null; unitNext = false; rowDiscount = false; refocus()
        },
      )
    }
  }
  if (askApproval && saleKey != null) {
    ir.vil3ntec.tohid.ui.components.staff.ApprovalDialog(
      title = StrPos.approveDiscount,
      summary = StrPos.discountSummary(money(basis.discount), money(basis.subtotal)) +
        if (discountDecision.cap == "maxPct") " — " + StrPos.cashierCap(qty(discountDecision.limit ?: 0.0)) else "",
      shopId = ir.vil3ntec.tohid.data.ShopRole.shopId(context),
      requesterUserId = remember { ir.vil3ntec.tohid.data.TillUser.meId(context) },
      need = DiscountGate.need(saleKey, basis.discount, basis.subtotal),
      onApproved = { approval = it; askApproval = false; refocus() },
      onDismiss = { askApproval = false; refocus() },
    )
  }
  //  STF-03 — تأییدِ سقفِ قرض/قیمت/تخفیفِ پنهان: «پرسیدن از مدیر» یا PIN؛ با تأیید همان ثبت (همان تسویه و تأییدِ اعتبار)
  capAsk?.let { (ask, pending, credit) ->
    ir.vil3ntec.tohid.ui.components.staff.ApprovalDialog(
      title = ir.vil3ntec.tohid.data.SaleCapGate.title(ask.perm),
      summary = ir.vil3ntec.tohid.data.SaleCapGate.summary(ask),
      shopId = ir.vil3ntec.tohid.data.ShopRole.shopId(context),
      requesterUserId = remember { ir.vil3ntec.tohid.data.TillUser.meId(context) },
      need = ask.need,
      onApproved = { s ->
        capAsk = null
        if (s.perm == ir.vil3ntec.tohid.core.perm.SaleCaps.DISCOUNT) approval = s else capSlips = capSlips + (s.perm to s)
        finish(pending, credit)
      },
      onDismiss = { capAsk = null; expiryOk[0] = null; refocus() },
    )
  }
  creditAsk?.let { (over, pending) ->
    val debtorId = cartDebtorId
    if (debtorId != null) ir.vil3ntec.tohid.ui.components.staff.CreditApprovalDialog(
      summary = (debtor?.name?.let { "«$it» — " } ?: "") + SalesEngine.creditOverText(over),
      debtorId = debtorId,
      addMinor = over.addMinor,
      selfRole = remember { ir.vil3ntec.tohid.data.ShopRole.current(context) },
      selfName = seller,
      requesterUserId = remember { ir.vil3ntec.tohid.data.TillUser.meId(context) },
      //  با تأیید همان فروش (با همان تسویه) ثبت می‌شود؛ انصراف ⇒ هیچ چیز
      onApproved = { ok -> creditAsk = null; finish(pending, ok) },
      onDismiss = { creditAsk = null; refocus() },
    )
  }
  expiryAsk?.let { (need, pending, credit) ->
    ir.vil3ntec.tohid.ui.components.staff.CreditApprovalDialog(
      summary = SalesEngine.expiredNeedText(d, need), debtorId = "", addMinor = 0L,
      selfRole = remember { ir.vil3ntec.tohid.data.ShopRole.current(context) }, selfName = seller,
      requesterUserId = remember { runCatching { ir.vil3ntec.tohid.sync.SyncStore(context).accountId }.getOrDefault("") },
      onApproved = { ok -> expiryAsk = null; expiryOk[0] = ir.vil3ntec.tohid.core.stock.ExpiryGate.Approval(ok.by, ok.role, need, ok.reason); finish(pending, credit) },
      onDismiss = { expiryAsk = null; refocus() }, title = StrPos.expiredSaleTitle,
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
      onConfirm = { st -> finish(st) }, onPickCustomer = { payOpen = false; payAfterPick = true; pickDebtor = true }, // پس از گزینش، همان پنل (POS-41)
      //  PAY-21: حساب‌پی برای باقی‌ماندهٔ پنل — نه با تخفیفِ تأییدنشده (پول گرفته و فروش رد نشود)
      gateways = gateways.takeIf { !discountBad && gate != DiscountGate.State.NEEDS_APPROVAL && gate != DiscountGate.State.DENIED },
      //  PAY-24: کارتِ هدیه و اعتبارِ فروشگاه فقط با تنظیمِ همین دستگاه (پیش‌فرض خاموش ⇒ همان پنل)
      ledger = d,
      stored = remember { ir.vil3ntec.tohid.data.GiftCardPref.on(context) }, offMethods = remember { ir.vil3ntec.tohid.data.PayMethodsPref.off(context) }, fxRates = remember { ir.vil3ntec.tohid.data.FxTillPref.tillRates(context) }, // PAY-06: روش‌های برداشته؛ PAY-10: ارز فقط با تنظیمِ دکان
      cashRounding = cashStep, onCustomer = { payOpen = false; payAfterPick = true; pickDebtor = true }, print = cartStore.print.collectAsState().value.let { remember(it) { cartStore.printShown(it) } }, onTogglePrint = cartStore::togglePrint, // PAY-09؛ POS-41: F2 و Alt+P
    )
  }
  if (priceCheck) ir.vil3ntec.tohid.ui.components.pos.PriceCheckDialog(
    d, ir.vil3ntec.tohid.todayIso(), remember { ir.vil3ntec.tohid.data.ShopRole.canSeeMoney(context) }, onDismiss = { priceCheck = false; refocus() },
    //  S17: موجودیِ هر محل با «محل‌های نگه‌داری» ِ همین دستگاه
    places = remember { ir.vil3ntec.tohid.data.StockPlacesPref.places(context) },
  )
  if (showInvoice) lastInvoice?.let { (snap, id) ->
    snap.sales.find { it.id == id }?.let { sale ->
      InvoiceDialog(d = snap, sale = sale, onDismiss = { showInvoice = false; refocus() }, onMessage = ::toast)
    } ?: run { showInvoice = false }
  }
  if (returnMode) {
    ReturnModeHost(d = d, store = store, shopTag = myReceiptTag, onClose = { returnMode = false; refocus() }, onMessage = ::toast)
  }
  noSaleDrawer?.let { printer -> OpenDrawerDialog(printer, onDismiss = { noSaleDrawer = null; refocus() }, toast = ::toast) }
}

/** کلیدِ Compose ⇒ نامِ کلیدِ فیزیکی (`KeyName`) برای `PosKeys` — همان نگاشتِ `KeyBinding.kt` */
private fun keyName(k: Key): KeyName? = KeyName.entries.firstOrNull { it.matches(k) }
