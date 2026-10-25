package ir.vil3ntec.tohid.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import ir.vil3ntec.tohid.data.WarehouseEngine
import ir.vil3ntec.tohid.ui.components.pos.CartSummary
import ir.vil3ntec.tohid.ui.components.pos.PhoneCartAmount
import ir.vil3ntec.tohid.ui.components.pos.PhoneCartClear
import ir.vil3ntec.tohid.ui.components.pos.PhoneCartInfoRow
import ir.vil3ntec.tohid.ui.components.pos.PhoneCartPay
import ir.vil3ntec.tohid.ui.components.pos.PhoneCartRow
import ir.vil3ntec.tohid.ui.components.pos.PhoneCartShot
import ir.vil3ntec.tohid.ui.components.pos.PhoneCartTitle
import ir.vil3ntec.tohid.ui.components.pos.PhoneCartTotal
import ir.vil3ntec.tohid.ui.components.pos.PhoneCartUi
import ir.vil3ntec.tohid.ui.components.pos.PhoneCatalog
import ir.vil3ntec.tohid.ui.components.pos.PhoneCatalogItem
import ir.vil3ntec.tohid.ui.components.pos.PhoneSaleFrame
import ir.vil3ntec.tohid.ui.components.pos.PhoneSaleText
import ir.vil3ntec.tohid.ui.components.pos.CatalogSearch
import ir.vil3ntec.tohid.ui.components.pos.PhoneKeypadRequest
import ir.vil3ntec.tohid.ui.components.pos.PhoneQtyKeypad
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import ir.vil3ntec.tohid.scan.scanChar
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import ir.vil3ntec.tohid.data.CartStore
import ir.vil3ntec.tohid.data.SalePost
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.qty
import ir.vil3ntec.tohid.data.BarcodeMatch
import ir.vil3ntec.tohid.scan.HidScan
import ir.vil3ntec.tohid.scan.CameraScanner
import ir.vil3ntec.tohid.scan.ScanFeedback
import ir.vil3ntec.tohid.scan.ScanGate
import ir.vil3ntec.tohid.ui.theme.Radius
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.launch
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrPos

/**
 *  فروش (صندوق).
 *
 *  همان چیدمانِ نسخهٔ وب: دوربینِ بالا، سبد پایین، و ثبت با پنجرهٔ تسویه.
 *  سه چیزی که کاربر گزارش کرده بود اینجا درست است:
 *    • دوربین با ورود به این صفحه خودش روشن می‌شود.
 *    • بعد از یک اسکن نمی‌ایستد (فریم‌ها همیشه بسته می‌شوند).
 *    • وقتی دوربین خاموش است، عکسِ بی‌ربط نشان داده نمی‌شود؛ فقط یک
 *      کادرِ ساده با دکمهٔ روشن‌کردن.
 */
@Composable
fun SaleScreen(
  store: ShopStore,
  cartStore: CartStore,
  d: ShopData,
  snackbar: SnackbarHostState,
  onQuickSale: () -> Unit,
  /**
   *  گوشی (نسخهٔ ۳): پیکانِ برگشتِ سربرگ و دکمهٔ برگشتِ خودِ گوشی — null ⇐ خانه.
   *  تبلت و کامپیوتر به آن کاری ندارند.
   */
  onBack: (() -> Unit)? = null,
  /** «ثبت کالا»ِ بارکدِ ناشناس؛ null ⇐ دکمه‌ای نیست و بارکد در کادرِ دستیِ اسکن می‌ماند */
  onRegisterBarcode: ((String) -> Unit)?,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  val cart by cartStore.lines.collectAsState()
  val cartDebtorId by cartStore.debtorId.collectAsState()
  //  phone-ui: گوشی نوار و برگهٔ تیرهٔ سبد دارد (تصویرهای مرجعِ ۵ و ۶)؛ تبلت همان سبدِ پیشین
  val phone = screenSize() == Screen.PHONE
  var cartOpen by rememberSaveable { mutableStateOf(false) }
  LaunchedEffect(cart.isEmpty()) { if (cart.isEmpty()) cartOpen = false }
  //  PRM: تخفیف‌های خودکار — کلیدِ دکان خاموش ⇒ null و `priced` همان `cart` است
  val promo = ir.vil3ntec.tohid.ui.PromoLive.rememberOutcome(d, cart)
  val priced = ir.vil3ntec.tohid.data.PromoEngine.priced(cart, promo)
  //  POS-12: همان سبدی که `priced` از آن ساخته شد — ثبت با سبدِ عوض‌شده نه
  val pricedFrom = cart

  // کالای حذف‌شده نباید در سبد بماند
  LaunchedEffect(d.products.size, d.debtors.size) { cartStore.prune(d) }

  //  گوشی (نسخهٔ ۳): دوربین فقط با زدنِ «اسکن» در سربرگ روشن می‌شود؛ صفحه خودش کاتالوگ است
  var cameraOn by rememberSaveable { mutableStateOf(!phone) }
  //  «اسکنِ بارکد» از نوارِ پایینِ گوشی (phone v3 §F2) ⇐ دوربین روشن، حتی اگر خوابیده بود
  val scanAsked = ir.vil3ntec.tohid.ui.components.common.PhoneNav.scanRequest.value
  LaunchedEffect(scanAsked) {
    if (scanAsked) { cameraOn = true; ir.vil3ntec.tohid.ui.components.common.PhoneNav.scanRequest.value = false }
  }
  var granted by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    )
  }
  var status by remember { mutableStateOf(StrSale.preparingCamera) }
  var statusError by remember { mutableStateOf(false) }
  //  آخرین باری که چیزی خوانده شد — مبنای خوابیدنِ خودکارِ دوربین
  var lastScanAt by remember { mutableStateOf(System.currentTimeMillis()) }

  val askCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
    granted = ok
    if (!ok) {
      status = StrSale.cameraPermissionDenied
      statusError = true
    }
  }

  // دوربین با باز شدنِ صفحه خودش روشن می‌شود؛ همان چیزی که خواسته شده بود
  LaunchedEffect(Unit) {
    if (!granted && !phone) askCamera.launch(Manifest.permission.CAMERA)
  }

  /*
   *  دوربینِ بیکار می‌خوابد.
   *
   *  این تب در دکان تمامِ روز باز است و دوربین تا زدنِ «توقف دوربین»
   *  روشن می‌ماند — حتی وقتی فروشنده دارد سبد را دستی می‌بندد یا با
   *  مشتری حساب می‌کند. نتیجه‌اش گرم شدنِ گوشی و رفتنِ باتری بود.
   *
   *  با هر اسکن، شمارش از نو شروع می‌شود؛ پس وسطِ کار خاموش نمی‌شود.
   */
  LaunchedEffect(cameraOn, granted, lastScanAt) {
    if (!cameraOn || !granted) return@LaunchedEffect
    kotlinx.coroutines.delay(CAMERA_IDLE_MS)
    cameraOn = false
    status = StrSale.cameraIdleOff
    statusError = false
  }

  var multiplier by rememberSaveable { mutableStateOf(1) }
  var manual by rememberSaveable { mutableStateOf("") }
  var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
  var pickDebtor by rememberSaveable { mutableStateOf(false) }
  var checkout by rememberSaveable { mutableStateOf(false) }
  // عکسِ لحظهٔ ثبت نگه داشته می‌شود، نه فقط شناسه: فاکتور باید همان
  // چیزی را نشان دهد که ثبت شد، حتی اگر همان لحظه چیز دیگری عوض شود
  var invoice by remember { mutableStateOf<Pair<ShopData, String>?>(null) }

  //  ردیفی که کادرِ تعداد و قیمتش باز است، و پنجرهٔ قلمِ آزاد
  var editLine by remember { mutableStateOf<String?>(null) }
  var freeLine by rememberSaveable { mutableStateOf(false) }

  //  POS-12: چند فاکتورِ هم‌زمان (تنظیمِ «چند فاکتورِ هم‌زمان»، پیش‌فرض خاموش). تخفیف و
  //  پرداختِ گوشی در پنجرهٔ پرداخت است؛ آنچه با فاکتور آمد (مثلاً از کامپیوتر) فقط
  //  برای همان فاکتور می‌ماند — با نگه‌داشتنِ دوباره گم نمی‌شود و تخفیفِ پنجره از آن پر می‌شود
  var keptExtras by remember { mutableStateOf<Pair<String, ir.vil3ntec.tohid.data.HeldExtras>?>(null) }
  fun extrasNow() = keptExtras?.takeIf { it.first == cartStore.snapshot().key }?.second ?: ir.vil3ntec.tohid.data.HeldExtras()
  val dataLoaded by store.loaded.collectAsState()
  val held = ir.vil3ntec.tohid.ui.components.pos.rememberHeldInvoices(
    cartStore, d, snackbar, dataLoaded,
    extras = { extrasNow() },
    onSwitched = { x ->
      keptExtras = cartStore.snapshot().key?.let { it to x }
      selectedId = null; multiplier = 1
      //  پنجرهٔ پرداخت مالِ فاکتورِ قبلی بود
      checkout = false
    },
  )

  val gate = remember { ScanGate() }
  val index = remember(d.products) { ShopStore.barcodeIndex(d) }

  fun toast(text: String) {
    scope.launch { snackbar.showSnackbar(text) }
  }

  //  HW-02/03: چاپِ خودکار یا کشوی پس از فروش که نشد همین‌جا، سرِ صندوق، گفته می‌شود
  val printNotice by ir.vil3ntec.tohid.print.PrintSpooler.tillNotice
  LaunchedEffect(printNotice) {
    printNotice?.let { n ->
      ir.vil3ntec.tohid.print.PrintSpooler.consumed(n)
      snackbar.showSnackbar(n.text, duration = SnackbarDuration.Long)
    }
  }

  /** یک بارکد — چه از دوربین، چه دستی، چه از بارکدخوانِ سخت‌افزاری */
  fun onBarcode(raw: String, skipDedup: Boolean = false, fromPhone: Boolean = false) {
    val code = raw.trim()
    if (code.isEmpty()) return
    //  این گوشی بارکدخوانِ کامپیوتر است ⇒ بارکد به سبدِ کامپیوتر می‌رود
    if (!fromPhone && ir.vil3ntec.tohid.sync.PhoneScanner.sendCode.value != null) {
      if (!skipDedup && !gate.accept(code)) return
      if (ir.vil3ntec.tohid.sync.PhoneScanner.send(code)) {
        ScanFeedback.ok(context)
        toast(StrSale.sentToComputer(code))
        return
      }
      toast(StrSale.computerUnavailable)
    }
    //  کار در جریان است؛ دوربین نباید بخوابد
    lastScanAt = System.currentTimeMillis()
    if (!skipDedup && !gate.accept(code)) return

    //  POS-29: برچسبِ ترازو — فقط اگر دکان قاعده‌اش را روشن کرده باشد (پیش‌فرض خاموش)
    when (val w = ir.vil3ntec.tohid.data.ScaleLabels.match(
      ir.vil3ntec.tohid.data.ScaleLabels.rules(context), code, index, d.products,
    )) {
      null -> Unit
      is ir.vil3ntec.tohid.data.ScaleLabels.Match.Rejected -> { ScanFeedback.unknown(context); toast(w.message); return }
      is ir.vil3ntec.tohid.data.ScaleLabels.Match.Weighed -> {
        val added = SalesEngine.addToCartCapped(cartStore.lines.value, w.product.id, w.quantity, ShopStore.stock(d, w.product.id))
        //  POS-12: وسطِ جابه‌جاییِ فاکتور سبد قفل است ⇒ اضافه نشد
        if (!cartStore.set(added.cart)) { ScanFeedback.unknown(context); toast(CartStore.NOT_ADDED); return }
        selectedId = w.product.id
        if (added.capped) { ScanFeedback.unknown(context); toast(Str.onlyInStock(w.product.name, "${qty(added.available)} ${w.product.unit}")) }
        else { ScanFeedback.ok(context); toast(StrPos.weighedAdded(w.product.name, "${qty(w.quantity)} ${w.product.unit}")) }
        return
      }
    }

    /*
     *  شناختنِ کالا — کامل، و اگر نشد از نیمهٔ بارکد.
     *  قاعده‌اش سرِ `BarcodeMatch` نوشته شده؛ خطِ سرخش یکتا بودن است.
     */
    val hit = BarcodeMatch.find(code, index)
    if (hit is BarcodeMatch.Hit.Several) {
      ScanFeedback.unknown(context)
      manual = code
      toast(StrSale.partialBarcode(hit.count))
      return
    }
    val productId = (hit as? BarcodeMatch.Hit.Product)?.id
    if (productId == null) {
      ScanFeedback.unknown(context)
      // بارکد گم نمی‌شود: هم در کادرِ دستی می‌ماند، هم می‌شود همان‌جا
      // کالای تازه‌اش را ثبت کرد و برگشت
      manual = code
      scope.launch {
        val answer = snackbar.showSnackbar(
          message = StrSale.unknownBarcode,
          actionLabel = StrSale.registerItem,
          duration = SnackbarDuration.Long,
        )
        if (answer == SnackbarResult.ActionPerformed) onRegisterBarcode?.invoke(code)
      }
      return
    }
    val product = d.products.first { it.id == productId }
    if (!product.active) {
      ScanFeedback.unknown(context)
      toast(StrSale.inactiveActivate(product.name))
      return
    }
    // از خودِ انبارهٔ سبد خوانده می‌شود، نه از مقدارِ لحظهٔ ترکیب: دو
    // اسکنِ پشتِ سرِ هم نباید همدیگر را پاک کنند.
    // و سبد از موجودی جلو نمی‌زند — خطا نباید سرِ ثبت درآید.
    val added = SalesEngine.addToCartCapped(
      cartStore.lines.value, productId, multiplier.toDouble(), ShopStore.stock(d, productId),
    )
    //  POS-12: وسطِ جابه‌جاییِ فاکتور سبد قفل است ⇒ اضافه نشد
    if (!cartStore.set(added.cart)) { ScanFeedback.unknown(context); toast(CartStore.NOT_ADDED); return }
    selectedId = productId
    if (added.capped) {
      ScanFeedback.unknown(context)
      toast(Str.onlyInStock(product.name, qty(added.available) + (if (product.unit.isNotBlank()) " ${product.unit}" else "")))
    } else {
      ScanFeedback.ok(context)
      //  اگر از نیمهٔ بارکد شناخته شد، همان را بگو — فروشنده باید بداند
      //  که کدِ کامل خوانده نشده، تا اگر کالا آن نبود بفهمد
      val whole = (hit as? BarcodeMatch.Hit.Product)?.exact ?: true
      toast(if (whole) StrSale.addedToCart(product.name) else StrSale.halfBarcode(product.name))
    }
  }

  //  بارکدی که گوشیِ جفت‌شده خواند، همین‌جا به سبد می‌رود
  LaunchedEffect(Unit) {
    for (code in ir.vil3ntec.tohid.sync.PhoneScanner.inbox) onBarcode(code, skipDedup = true, fromPhone = true)
  }

  /*
   *  بارکدخوانِ سخت‌افزاری — کابلی یا بلوتوثی.
   *
   *  ── چرا اینجا و این‌شکلی ──────────────────────────────────────────
   *  این دستگاه‌ها خودشان را **صفحه‌کلید** معرفی می‌کنند و رقم‌ها را
   *  تایپ می‌کنند. پس باید جایی گرفته شوند که رویدادِ کلید می‌آید.
   *
   *  `onKeyEvent` است نه `onPreviewKeyEvent`، و این عمدی است:
   *  `onKeyEvent` روی یک نیای بالادست **فقط وقتی** صدا زده می‌شود که
   *  فرزندِ فوکوس‌دار خودش رویداد را برنداشته باشد. یعنی اگر فروشنده
   *  داخلِ کادرِ «بارکد دستی» یا نامِ مشتری تایپ می‌کند، نویسه‌ها به
   *  همان کادر می‌روند و اصلاً به اینجا نمی‌رسند — دقیقاً همان قاعده‌ای
   *  که نسخهٔ وب دارد (آنجا با دیدنِ `document.activeElement`).
   *
   *  با `preview` برعکس می‌شد: هر نویسه اول اینجا می‌آمد و نامِ مشتری
   *  را می‌بلعید.
   *
   *  `focusable` روی همین جعبه لازم است تا وقتی هیچ کادری باز نیست،
   *  رویدادِ کلید اصلاً به درختِ صفحه برسد.
   */
  /* ------------------ گوشی، نسخهٔ ۳: حالت و کارهای کاتالوگ ------------------ */

  var scanOpen by rememberSaveable { mutableStateOf(false) }
  var phoneSearch by rememberSaveable { mutableStateOf("") }
  var category by rememberSaveable { mutableStateOf<String?>(null) }
  var expandedLine by remember { mutableStateOf<String?>(null) }
  var newCategory by rememberSaveable { mutableStateOf<String?>(null) }
  var confirmDeleteCategory by remember { mutableStateOf<String?>(null) }
  //  دسته‌ای که پاک شد، انتخاب نمی‌ماند
  LaunchedEffect(d.productCategories) { if (category != null && category !in d.productCategories) category = null }

  //  هر بار که چیزی به سبد افزوده شد (کارت، اسکن، +، گوشیِ جفت‌شده) نوار می‌پرد
  val cartQty = cart.sumOf { it.quantity }
  var lastQty by remember { mutableStateOf(cartQty) }
  var pulse by remember { mutableStateOf(0) }
  LaunchedEffect(cartQty) {
    if (cartQty > lastQty) pulse++
    lastQty = cartQty
  }

  //  عکسِ خودکار (`PhoneCartShot`) — در نسخهٔ دستِ کاربر هرگز روشن نمی‌شود
  val shotOpen = PhoneCartShot.open.value
  LaunchedEffect(shotOpen) { if (phone && shotOpen) cartOpen = true }
  val shotExpand = PhoneCartShot.expand.value
  LaunchedEffect(shotExpand) { if (phone && shotExpand != null) expandedLine = shotExpand }

  fun openScanner() {
    scanOpen = true
    cameraOn = true
    gate.reset()
    lastScanAt = System.currentTimeMillis()
    if (!granted) askCamera.launch(Manifest.permission.CAMERA)
  }

  fun closeScanner() {
    scanOpen = false
    cameraOn = false
  }

  //  دکمهٔ سیاهِ بارکدِ نوارِ پایین (بیرون از این صفحه) صندوق را با دوربینِ باز می‌خواهد
  val scanAsk = PhoneCartUi.scanRequest.value
  LaunchedEffect(scanAsk) {
    if (phone && scanAsk) {
      openScanner()
      PhoneCartUi.scanRequest.value = false
    }
  }

  val goBack: () -> Unit = onBack ?: { ir.vil3ntec.tohid.ui.AppNav.go("dashboard") }

  /** زدنِ کارت: یک پله، با نگهبانِ موجودی — همان قاعدهٔ «انتخاب محصول» */
  fun phoneAdd(productId: String) {
    val p = d.products.find { it.id == productId } ?: return
    val added = SalesEngine.addToCartCapped(
      cartStore.lines.value, p.id, SalesEngine.cartStep(p.unit), ShopStore.stock(d, p.id),
    )
    if (added.capped) {
      Haptics.confirm(context)
      //  «تمام شد»: همان پیامِ موتورِ فروش (جنس هست؟ اول ورودیِ انبار)؛ بخشی ماند ⇐ چقدر هست
      toast(
        if (added.available <= 0) SalesEngine.shortageMessage(p, SalesEngine.cartStep(p.unit), added.available)
        else "${p.name} فقط ${qty(added.available)}${if (p.unit.isNotBlank()) " ${p.unit}" else ""} موجود است"
      )
      if (added.cart === cartStore.lines.value) return
    }
    cartStore.set(added.cart)
    selectedId = p.id
  }

  /** −/+ِ یک ردیفِ سبد. «+» کالا هم از موجودی جلو نمی‌زند؛ ردیفِ آزاد موجودی ندارد */
  fun phoneStep(row: CartSummary.Row, sign: Int) {
    val lines = cartStore.lines.value
    if (sign > 0 && row.hasProduct) {
      val p = d.products.find { it.id == row.id } ?: return
      val added = SalesEngine.addToCartCapped(lines, p.id, row.step, ShopStore.stock(d, p.id))
      if (added.capped) {
        toast("${p.name} فقط ${qty(added.available)}${if (p.unit.isNotBlank()) " ${p.unit}" else ""} موجود است")
      }
      cartStore.set(added.cart)
      return
    }
    val next = row.quantity + sign * row.step
    cartStore.set(SalesEngine.setCartQty(lines, row.id, next))
    if (next <= 0) {
      if (selectedId == row.id) selectedId = null
      if (expandedLine == row.id) expandedLine = null
    }
  }

  /*
   *  کاتالوگ: نمایهٔ جستجو (کلیدهای نرمال) و ترتیبِ پرفروش‌ترین فقط با تغییرِ کالاها یا
   *  فروش‌ها ساخته می‌شود، نه با هر حرف (سنجشِ کارایی: ۲۶۴–۴۴۹ms برای هر کلید). کالای
   *  غیرفعال در فروش نمی‌آید — سابقه‌اش در گزارش‌ها می‌ماند.
   */
  val popularity = remember(d.sales, d.saleItems, phone) {
    if (phone) CatalogSearch.popularity(d.sales, d.saleItems) else emptyMap()
  }
  val searchIndex = remember(d.products, popularity, phone) {
    CatalogSearch.index(if (phone) d.products else emptyList(), popularity)
  }
  val productById = remember(d.products) { d.products.associateBy { it.id } }
  val inCartQty = remember(cart) { cart.groupBy { it.productId }.mapValues { (_, l) -> l.sumOf { it.quantity } } }
  fun catalogItem(p: ir.vil3ntec.tohid.data.Product) = PhoneCatalogItem(
    id = p.id,
    name = p.name,
    price = p.salePrice,
    unit = p.unit,
    stock = ShopStore.stock(d, p.id),
    hasPhoto = p.photo,
    inCart = inCartQty[p.id] ?: 0.0,
    weighed = SalesEngine.isFractionalUnit(p.unit),
  )
  val shownIds = remember(searchIndex, phoneSearch, category) { CatalogSearch.search(searchIndex, phoneSearch, category) }
  val catalogItems = remember(shownIds, productById, inCartQty, d) { shownIds.mapNotNull { productById[it]?.let(::catalogItem) } }
  val bestsellers = remember(searchIndex, productById, inCartQty, d) {
    CatalogSearch.top(searchIndex).mapNotNull { productById[it]?.let(::catalogItem) }
  }

  //  کاشی یا فهرست — همان انبارِ `ViewModePref` (کارت = کاشی، جدول = فهرست)، برای هر کاربر
  val modeStore = remember(context) {
    ir.vil3ntec.tohid.ui.components.common.ViewModePref.Store.of(
      context.getSharedPreferences(ir.vil3ntec.tohid.ui.components.common.ViewModePref.PREFS, android.content.Context.MODE_PRIVATE)
    )
  }
  val modeKey = remember(context) {
    val user = runCatching { ir.vil3ntec.tohid.sync.SyncStore(context).accountId }.getOrDefault("")
    ir.vil3ntec.tohid.ui.components.common.ViewModePref.key(user, "sale-phone")
  }
  var listMode by remember {
    mutableStateOf(
      ir.vil3ntec.tohid.ui.components.common.ViewMode.parse(runCatching { modeStore.get(modeKey) }.getOrNull()) ==
        ir.vil3ntec.tohid.ui.components.common.ViewMode.TABLE
    )
  }
  fun toggleMode() {
    listMode = !listMode
    val mode = if (listMode) ir.vil3ntec.tohid.ui.components.common.ViewMode.TABLE else ir.vil3ntec.tohid.ui.components.common.ViewMode.CARD
    runCatching { modeStore.put(modeKey, mode.id) }
  }

  //  صفحه‌کلیدِ مقدار/وزن (نگه داشتن، یا زدنِ کالای وزنی)
  var keypadFor by remember { mutableStateOf<String?>(null) }
  fun phoneTap(item: PhoneCatalogItem) { if (item.weighed) keypadFor = item.id else phoneAdd(item.id) }
  fun phoneMinus(item: PhoneCatalogItem) {
    val p = productById[item.id] ?: return
    val lines = cartStore.lines.value
    val now = lines.find { it.productId == p.id }?.quantity ?: return
    val next = now - SalesEngine.cartStep(p.unit)
    cartStore.set(SalesEngine.setCartQty(lines, p.id, next))
    if (next <= 0 && selectedId == p.id) selectedId = null
  }

  //  عکس‌های خودکار: نما، جستجو و صفحه‌کلید
  val shotList = PhoneCartShot.list.value
  LaunchedEffect(shotList) { if (phone && shotList != null) listMode = shotList }
  val shotSearch = PhoneCartShot.search.value
  LaunchedEffect(shotSearch) { if (phone && shotSearch != null) phoneSearch = shotSearch }
  val shotKeypad = PhoneCartShot.keypad.value
  LaunchedEffect(shotKeypad) { if (phone && shotKeypad != null) keypadFor = shotKeypad }

  /*
   *  دوربین، بارکدِ دستی و «هر اسکن» — یک‌جا تا تبلت (کادرِ بالای صفحه) و گوشیِ
   *  نسخهٔ ۳ (کادرِ زیرِ سربرگ با زدنِ «اسکن») یک چیز را نشان دهند، نه دو نسخه.
   */
  val scanControls: @Composable () -> Unit = {
    ScannerFrame(
      on = cameraOn && granted,
      status = status,
      error = statusError,
      onGrant = { askCamera.launch(Manifest.permission.CAMERA) },
      granted = granted,
      onStart = { cameraOn = true },
      scanner = {
        CameraScanner(
          onCode = { onBarcode(it) },
          onStatus = { text, isError -> status = text; statusError = isError },
          modifier = Modifier.fillMaxSize(),
          //  سدِ خوانشِ دروغین از همین می‌فهمد کجا سخت بگیرد:
          //  کالای خودِ دکان همان فریمِ اول می‌رود توی سبد، ولی کدی
          //  که در فهرست نیست باید چند بار یکسان خوانده شود.
          //  خوانشِ نصفه تقریباً همیشه از دستهٔ دوم است.
          isKnown = { index.containsKey(it) },
        )
      },
    )

    Spacer(Modifier.height(12.dp))

    Row(verticalAlignment = Alignment.CenterVertically) {
      OutlinedTextField(
        value = manual,
        onValueChange = { manual = it },
        placeholder = { Text(StrSale.manualBarcode) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Search),
        /*
         *  ضربدرِ پاک‌کردن.
         *
         *  بارکدِ ناشناس خودش در این کادر می‌نشیند تا گم نشود؛ ولی
         *  تا حالا برای برداشتنش باید رقم‌به‌رقم پاک می‌شد. حالا یک
         *  زدن بس است. فقط وقتی چیزی نوشته شده نشان داده می‌شود تا
         *  کادرِ خالی شلوغ نشود.
         */
        trailingIcon = {
          if (manual.isNotEmpty()) {
            IconButton(onClick = { manual = "" }) {
              Icon(
                Icons.Filled.Close,
                contentDescription = StrSale.clearBarcode,
                tint = Shop.colors.muted,
              )
            }
          }
        },
        modifier = Modifier.weight(1f),
      )
      Spacer(Modifier.width(8.dp))
      Button(
        onClick = {
          val code = manual.trim()
          if (code.isNotEmpty()) {
            onBarcode(code, skipDedup = true)
            if (index.containsKey(code)) manual = ""
          }
        },
      ) { Text(StrSale.searchAlt) }
    }

    Spacer(Modifier.height(12.dp))

    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(StrSale.perScan, style = MaterialTheme.typography.labelMedium, color = Shop.colors.muted)
      Spacer(Modifier.width(10.dp))
      listOf(1, 2, 5, 10).forEach { m ->
        MultiplierChip(m, m == multiplier) { multiplier = m }
        Spacer(Modifier.width(6.dp))
      }
    }
  }

  /** «جستجو»ِ بارکدِ دستی — همان کارِ دکمهٔ تبلت */
  fun searchManual() {
    val code = manual.trim()
    if (code.isNotEmpty()) {
      onBarcode(code, skipDedup = true)
      if (index.containsKey(code)) manual = ""
    }
  }

  /*
   *  گوشی (نسخهٔ ۳): همان دوربینِ پیوسته، بارکدِ دستی، «هر اسکن» و «قلم آزاد» — با رنگ‌های
   *  خودِ سبد (سطحِ تیره، دکمهٔ رنگی، چیپ‌های خاکستری و انتخابِ رنگِ سبد)، نه رنگِ دیگرِ برنامه.
   */
  val phoneScanPanel: @Composable () -> Unit = {
    val v = PhoneV3.colors
    Column(
      Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(PhoneV3.tileRadius))
        .background(v.black)
        .padding(12.dp),
    ) {
      Box(
        Modifier
          .fillMaxWidth()
          .height(200.dp)
          .clip(RoundedCornerShape(16.dp))
          .background(v.blackRaised),
        contentAlignment = Alignment.Center,
      ) {
        if (cameraOn && granted) {
          CameraScanner(
            onCode = { onBarcode(it) },
            onStatus = { text, isError -> status = text; statusError = isError },
            modifier = Modifier.fillMaxSize(),
            isKnown = { index.containsKey(it) },
          )
          ScanLine(Modifier.fillMaxWidth(0.86f).height(120.dp))
          Text(
            status,
            color = if (statusError) v.negative else v.onBlack,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier
              .align(Alignment.BottomCenter)
              .fillMaxWidth()
              .background(v.black.copy(alpha = 0.55f))
              .padding(horizontal = 12.dp, vertical = 6.dp),
          )
        } else {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.PhotoCamera, contentDescription = null, tint = v.onBlackMuted, modifier = Modifier.size(30.dp))
            Spacer(Modifier.height(8.dp))
            Text(
              if (granted) "دوربین خاموش است" else "برای اسکن، اجازهٔ دوربین لازم است",
              color = v.onBlackMuted,
              fontSize = 13.sp,
            )
            Spacer(Modifier.height(10.dp))
            V3Pill(if (granted) "روشن کردن دوربین" else "اجازه دادن", v.cart, v.onCart) {
              if (granted) {
                cameraOn = true
                gate.reset()
                lastScanAt = System.currentTimeMillis()
              } else askCamera.launch(Manifest.permission.CAMERA)
            }
          }
        }
      }
      Spacer(Modifier.height(10.dp))
      Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
          Modifier
            .weight(1f)
            .height(44.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(v.blackRaised)
            .padding(start = 14.dp, end = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (manual.isEmpty()) Text("یا بارکد را دستی وارد کنید", color = v.onBlackMuted, fontSize = 14.sp, maxLines = 1)
            BasicTextField(
              value = manual,
              onValueChange = { manual = it },
              singleLine = true,
              textStyle = TextStyle(color = v.onBlack, fontSize = 15.sp),
              cursorBrush = SolidColor(v.cart),
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Search),
              keyboardActions = KeyboardActions(onSearch = { searchManual() }),
              modifier = Modifier.fillMaxWidth(),
            )
          }
          if (manual.isNotEmpty()) {
            IconButton(onClick = { manual = "" }, modifier = Modifier.size(36.dp)) {
              Icon(Icons.Filled.Close, contentDescription = "پاک کردن بارکد", tint = v.onBlackMuted, modifier = Modifier.size(18.dp))
            }
          }
        }
        Spacer(Modifier.width(8.dp))
        V3Pill("جستجو", v.cart, v.onCart) { searchManual() }
      }
      Spacer(Modifier.height(10.dp))
      Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Text("هر اسکن:", color = v.onBlackMuted, fontSize = 12.sp, maxLines = 1)
        listOf(1, 2, 5, 10).forEach { m ->
          val on = m == multiplier
          V3Pill("×${ir.vil3ntec.tohid.plain(m)}", if (on) v.cart else v.blackRaised, if (on) v.onCart else v.onBlack, height = 36.dp) {
            multiplier = m
          }
        }
      }
      Spacer(Modifier.height(10.dp))
      V3Pill("+ قلم آزاد", v.onBlack, v.black, height = 36.dp) { freeLine = true }
    }
  }

  val hid = remember { HidScan() }
  val hidFocus = remember { FocusRequester() }
  LaunchedEffect(Unit) { runCatching { hidFocus.requestFocus() } }
  DisposableEffect(Unit) { onDispose { hid.reset() } }

  Box(
    Modifier
      .fillMaxSize()
      .focusRequester(hidFocus)
      .focusable()
      .onKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
        val now = System.currentTimeMillis()

        if (event.key == Key.Enter || event.key == Key.NumPadEnter) {
          val code = hid.submit(now)
          if (code != null) {
            //  از بارکدخوان آمده: کامل، یک‌باره و از دستگاهی که اشتباه
            //  نمی‌خواند. سدِ تکرارِ دوربین اینجا به کار نمی‌آید و فقط
            //  جلوی اسکنِ دوبارهٔ همان کالا را می‌گرفت — که خودش یعنی
            //  «دو تا».
            onBarcode(code, skipDedup = true)
            return@onKeyEvent true
          }
          return@onKeyEvent false
        }

        //  فقط نویسه‌های دیدنی؛ کلیدهای کنترلی به کارِ بارکد نمی‌آیند.
        //  F0-06: با کلیدِ فیزیکی — چیدمانِ فارسی/پشتو «۶» و «ش» می‌سازد
        val ch = event.scanChar(allowSpace = true)
        if (ch != null) {
          hid.feed(ch, now)
          return@onKeyEvent true
        }
        false
      }
  ) {
    //  گوشی (نسخهٔ ۳) کاتالوگ و سبدِ r3/r2 را پایین‌تر می‌کشد؛ این فهرست فقط تبلت است
    if (!phone) LazyColumn(
      Modifier.fillMaxSize(),
      contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, if (cart.isEmpty()) 24.dp else if (phone) 104.dp else 200.dp),
    ) {
      item {
        Row(
          Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(Modifier.weight(1f)) {
            Text(
              StrSale.holdToCamera,
              style = MaterialTheme.typography.bodySmall,
              color = Shop.colors.muted,
            )
          }
          TextButton(onClick = {
            if (cameraOn) {
              cameraOn = false
              status = StrSale.cameraStopped
              statusError = false
            } else {
              cameraOn = true
              gate.reset()
              lastScanAt = System.currentTimeMillis()
              if (!granted) askCamera.launch(Manifest.permission.CAMERA)
            }
          }) {
            Text(if (cameraOn) StrSale.stopCamera else StrSale.startCamera, color = Shop.colors.primary)
          }
        }
        Spacer(Modifier.height(12.dp))
      }

      /* --------------------------- دوربین --------------------------- */
      item {
        Panel {
          scanControls()
        }
        Spacer(Modifier.height(16.dp))
      }

      /* ---------------------------- سبد ---------------------------- */
      item { ir.vil3ntec.tohid.ui.components.pos.HeldInvoiceBar(held, Modifier.padding(bottom = 8.dp)) }
      item {
        Row(
          Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(StrSale.cart, style = MaterialTheme.typography.titleMedium, color = Shop.colors.text)
          Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { freeLine = true }) {
              Icon(Icons.Filled.AddCircleOutline, contentDescription = null, tint = Shop.colors.primary)
              Spacer(Modifier.width(6.dp))
              Text(StrSale.freeItem, color = Shop.colors.primary)
            }
            TextButton(onClick = onQuickSale) {
              Icon(Icons.Filled.GridView, contentDescription = null, tint = Shop.colors.primary)
              Spacer(Modifier.width(6.dp))
              Text(Str.pickProduct, color = Shop.colors.primary)
            }
          }
        }
        Spacer(Modifier.height(8.dp))
      }

      if (cart.isEmpty()) {
        item {
          Panel {
            EmptyNote(StrSale.cartEmptyHint)
          }
        }
      } else if (!phone) {
        //  گوشی: ردیف‌ها در برگهٔ تیرهٔ سبد‌اند (`PhoneCartLayer` پایین)
        items(cart, key = { it.productId }) { line ->
          val product = d.products.find { it.id == line.productId }
          //  ردیفِ آزاد کالا ندارد ولی باید دیده شود
          if (product == null && line.free) {
            val price = line.unitPrice ?: 0.0
            CartRow(
              name = line.label,
              unit = "",
              quantity = line.quantity,
              lineTotal = SalesEngine.lineTotal(price, line.quantity),
              unitPrice = price,
              selected = selectedId == line.productId,
              customPrice = true,
              onClick = { selectedId = if (selectedId == line.productId) null else line.productId },
              onPlus = { cartStore.set(SalesEngine.setCartQty(cart, line.productId, line.quantity + 1)) },
              onMinus = { cartStore.set(SalesEngine.setCartQty(cart, line.productId, line.quantity - 1)) },
              onEdit = { editLine = line.productId },
              onRemove = {
                cartStore.set(SalesEngine.setCartQty(cart, line.productId, 0.0))
                if (selectedId == line.productId) selectedId = null
              },
            )
            Spacer(Modifier.height(8.dp))
          } else if (product != null) {
            val price = line.unitPrice ?: product.salePrice
            CartRow(
              name = product.name,
              unit = product.unit,
              quantity = line.quantity,
              lineTotal = SalesEngine.lineTotal(price, line.quantity),
              unitPrice = price,
              customPrice = line.unitPrice != null,
              onEdit = { editLine = line.productId },
              selected = selectedId == line.productId,
              onClick = { selectedId = if (selectedId == line.productId) null else line.productId },
              onPlus = {
                cartStore.set(
                  SalesEngine.setCartQty(cart, line.productId, line.quantity + SalesEngine.cartStep(product.unit))
                )
              },
              onMinus = {
                cartStore.set(
                  SalesEngine.setCartQty(cart, line.productId, line.quantity - SalesEngine.cartStep(product.unit))
                )
              },
              onRemove = {
                cartStore.set(SalesEngine.setCartQty(cart, line.productId, 0.0))
                if (selectedId == line.productId) selectedId = null
              },
            )
            Spacer(Modifier.height(8.dp))
          }
        }
        //  PRM-18: هر تخفیفِ اعمال‌شده یک ردیف با نامش، و کادرِ کد اگر قاعدهٔ کددار هست
        if (promo != null && promo.applied.isNotEmpty()) {
          item(key = "promo-lines") { ir.vil3ntec.tohid.ui.components.pos.PromoLines(promo.applied) }
        }
        if (ir.vil3ntec.tohid.ui.PromoLive.snap.value.active && ir.vil3ntec.tohid.ui.PromoLive.snap.value.hasCodes) {
          item(key = "promo-code") {
            Panel {
              ir.vil3ntec.tohid.ui.components.pos.PromoCodeField(
                codes = promo?.codes.orEmpty(),
                entered = ir.vil3ntec.tohid.ui.PromoLive.coupons.value,
                onAdd = { ir.vil3ntec.tohid.ui.PromoLive.addCoupon(it) },
                onRemove = { ir.vil3ntec.tohid.ui.PromoLive.removeCoupon(it) },
              )
            }
          }
        }
      }
    }

    /* ------------------------ نوارِ پایینِ سبد ------------------------ */
    if (!phone) AnimatedVisibility(
      visible = cart.isNotEmpty(),
      enter = slideInVertically(tween(220)) { it } + fadeIn(tween(220)),
      exit = slideOutVertically(tween(180)) { it } + fadeOut(tween(180)),
      modifier = Modifier.align(Alignment.BottomCenter),
    ) {
      CartBar(
        total = SalesEngine.cartTotal(d, priced),
        debtorName = cartDebtorId?.let { id -> d.debtors.find { it.id == id }?.name },
        debtorNote = cartDebtorId?.let { id -> debtStateText(d, id) },
        onPickDebtor = { pickDebtor = true },
        onClearDebtor = { cartStore.setDebtor(null) },
        onFinalize = { checkout = true },
      )
    }

    /* ------------- گوشی، نسخهٔ ۳: کاتالوگِ r3 و سبدِ r2 ------------- */
    if (phone) {
      //  دکمهٔ برگشتِ گوشی: اول کادرِ اسکن، بعد خانه (سبدِ باز را خودِ قاب می‌بندد)
      BackHandler(onBack = { if (scanOpen) closeScanner() else goBack() })
      PhoneSaleFrame(
        model = CartSummary.of(cart, d.products, SalesEngine.cartTotal(d, priced)),
        open = cartOpen,
        onOpen = { cartOpen = true },
        onClose = { cartOpen = false; expandedLine = null },
        pulse = pulse,
        catalog = { bottomGap ->
          PhoneCatalog(
            items = catalogItems,
            bestsellers = bestsellers,
            categories = d.productCategories,
            category = category,
            onCategory = { category = it },
            onCategoryHold = { confirmDeleteCategory = it },
            onAddCategory = { newCategory = "" },
            search = phoneSearch,
            onSearch = { phoneSearch = it },
            listMode = listMode,
            onToggleMode = { toggleMode() },
            onBack = goBack,
            onScan = { if (scanOpen) closeScanner() else openScanner() },
            scanning = scanOpen,
            scanPanel = { phoneScanPanel() },
            emptyText = when {
              d.products.isEmpty() -> "هنوز محصولی ثبت نشده."
              phoneSearch.isNotBlank() -> "کالایی با «${phoneSearch.trim()}» پیدا نشد"
              else -> "محصولی در این دسته‌بندی نیست"
            },
            bottomGap = bottomGap,
            onTap = { phoneTap(it) },
            onHold = { keypadFor = it.id },
            onMinus = { phoneMinus(it) },
          )
        },
        cart = { revealed, dragHandle ->
          val rows = CartSummary.rows(cart, d.products)
          Box(dragHandle.fillMaxWidth().padding(top = 62.dp, bottom = 38.dp)) { PhoneCartTitle() }
          //  ردیف‌ها می‌لغزند؛ «مشتری» و «تخفیف» زیرشان می‌مانند و هرگز پشتِ «جمع:» نمی‌روند
          Box(Modifier.weight(1f).fillMaxWidth()) {
            Column(Modifier.fillMaxWidth()) {
              LazyColumn(Modifier.weight(1f, fill = false).fillMaxWidth()) {
                itemsIndexed(rows, key = { _, r -> r.id }) { i, r ->
                  PhoneCartRow(
                    row = r,
                    index = i,
                    revealed = revealed,
                    expanded = expandedLine == r.id,
                    onToggle = { expandedLine = if (expandedLine == r.id) null else r.id },
                    onMinus = { phoneStep(r, -1) },
                    onPlus = { phoneStep(r, +1) },
                    onHold = {
                      Haptics.confirm(context)
                      cartStore.set(SalesEngine.setCartQty(cartStore.lines.value, r.id, 0.0))
                      if (selectedId == r.id) selectedId = null
                      if (expandedLine == r.id) expandedLine = null
                    },
                    onEdit = { editLine = r.id },
                  )
                }
              }
              val debtorName = cartDebtorId?.let { id -> d.debtors.find { it.id == id }?.name }
              PhoneCartInfoRow(
                icon = Icons.Outlined.Person,
                title = debtorName ?: "مشتری گذری",
                hint = cartDebtorId?.let { id -> debtStateText(d, id) } ?: "برای فروشِ نسیه، قرض‌دار را انتخاب کنید",
                index = rows.size,
                revealed = revealed,
                onClick = { pickDebtor = true },
                onClickLabel = "انتخابِ قرض‌دار",
                trailing = {
                  if (debtorName != null) PhoneCartClear("برداشتنِ قرض‌دار") { cartStore.setDebtor(null) }
                },
              )
              val off = CartSummary.manualDiscount(cart, d.products)
              if (off > 0) {
                PhoneCartInfoRow(
                  icon = Icons.Outlined.LocalOffer,
                  title = "تخفیف",
                  hint = "قیمتِ دستیِ کمتر از قیمتِ کالا — در جمع حساب شده است",
                  index = rows.size + 1,
                  revealed = revealed,
                  onClick = null,
                  //  «-99,999 ؋»: منفی در جداسازِ چپ‌به‌راست، تا «-» پسِ عدد نیفتد
                  trailing = { PhoneCartAmount(PhoneSaleText.amount(-off)) },
                )
              }
            }
          }
          Spacer(Modifier.height(16.dp))
          PhoneCartTotal(SalesEngine.cartTotal(d, priced))
          Spacer(Modifier.height(36.dp))
          PhoneCartPay("پرداخت", onClick = { checkout = true })
        },
      )
      PhoneQtyKeypad(
        request = keypadFor?.let { id -> productById[id] }?.let { p ->
          PhoneKeypadRequest(p.id, p.name, p.unit, p.salePrice, SalesEngine.isFractionalUnit(p.unit), inCartQty[p.id] ?: 0.0)
        },
        problem = { req, q ->
          val p = productById[req.id]
          val stock = ShopStore.stock(d, req.id)
          when {
            p == null -> null
            SalesEngine.quantityProblem(p, q) != null -> SalesEngine.quantityProblem(p, q)
            stock <= 0 -> SalesEngine.shortageMessage(p, q, stock)
            q > stock -> "فقط ${qty(stock)}${if (p.unit.isNotBlank()) " ${p.unit}" else ""} موجود است"
            else -> null
          }
        },
        onDismiss = { keypadFor = null },
        onConfirm = { req, q ->
          val lines = cartStore.lines.value
          val next = when {
            lines.any { it.productId == req.id } -> SalesEngine.setCartQty(lines, req.id, q)
            q > 0 -> SalesEngine.addToCart(lines, req.id, q)
            else -> lines
          }
          cartStore.set(next)
          if (q > 0) selectedId = req.id else if (selectedId == req.id) selectedId = null
          keypadFor = null
        },
      )
    }
  }

  /* ------------------ گوشی: دستهٔ تازه و حذفِ دسته ------------------ */

  newCategory?.let { value ->
    AlertDialog(
      onDismissRequest = { newCategory = null },
      containerColor = Shop.colors.surfaceSolid,
      title = { Text("دسته‌بندیِ تازه", color = Shop.colors.text) },
      text = {
        OutlinedTextField(
          value = value,
          onValueChange = { newCategory = it },
          placeholder = { Text("نام دسته‌بندی را بنویسید…") },
          singleLine = true,
        )
      },
      confirmButton = {
        TextButton(onClick = {
          when (val result = WarehouseEngine.addCategory(d, value)) {
            is WarehouseEngine.Result.Failed -> toast(result.message)
            is WarehouseEngine.Result.Ok -> {
              scope.launch { store.save(result.data) }
              toast("دسته‌بندی اضافه شد")
              category = value.trim()
              newCategory = null
            }
          }
        }) { Text("افزودن") }
      },
      dismissButton = { TextButton(onClick = { newCategory = null }) { Text("انصراف") } },
    )
  }

  confirmDeleteCategory?.let { name ->
    AlertDialog(
      onDismissRequest = { confirmDeleteCategory = null },
      containerColor = Shop.colors.surfaceSolid,
      title = { Text("حذف دسته‌بندی؟", color = Shop.colors.text) },
      text = {
        Text(
          "دسته‌بندی «$name» حذف می‌شود. محصولات این دسته حذف نمی‌شوند، فقط دیگر در این دسته‌بندی فیلتر نمی‌شوند.",
          style = MaterialTheme.typography.bodySmall,
          color = Shop.colors.muted,
        )
      },
      confirmButton = {
        TextButton(onClick = {
          when (val result = WarehouseEngine.removeCategory(d, name)) {
            is WarehouseEngine.Result.Failed -> toast(result.message)
            is WarehouseEngine.Result.Ok -> {
              scope.launch { store.save(result.data) }
              toast("دسته‌بندی حذف شد")
              if (category == name) category = null
            }
          }
          confirmDeleteCategory = null
        }) { Text("حذف", color = Shop.colors.danger) }
      },
      dismissButton = { TextButton(onClick = { confirmDeleteCategory = null }) { Text("انصراف") } },
    )
  }

  /* --------------------------- پنجره‌ها --------------------------- */

  if (pickDebtor) {
    DebtorPicker(
      d = d,
      selected = cartDebtorId,
      onClose = { pickDebtor = false },
      onPick = { id -> cartStore.setDebtor(id); pickDebtor = false },
    )
  }

  //  کادرِ تعداد و قیمتِ یک ردیف
  editLine?.let { id ->
    val line = cart.find { it.productId == id }
    if (line == null) editLine = null
    else {
      val product = d.products.find { it.id == id }
      val base = if (line.free) (line.unitPrice ?: 0.0) else (product?.salePrice ?: 0.0)
      CartLineDialog(
        name = if (line.free) line.label else (product?.name ?: ""),
        unit = product?.unit.orEmpty(),
        quantity = line.quantity,
        unitPrice = line.unitPrice ?: base,
        basePrice = base,
        //  ردیفِ آزاد همیشه قیمتِ خودش را دارد و برگشتی به «قیمتِ کالا» ندارد
        custom = line.free || line.unitPrice != null,
        saleUnit = product?.saleUnit.orEmpty(),
        unitFactor = product?.unitFactor ?: 1.0,
        onDismiss = { editLine = null },
        onConfirm = { quantity, price ->
          var next = SalesEngine.setCartQty(cart, id, quantity)
          //  قلمِ آزاد بدونِ قیمت معنی ندارد؛ اگر پاک شد، همان قبلی می‌ماند
          val wanted = if (line.free) (price ?: line.unitPrice) else price
          next = SalesEngine.setLinePrice(next, id, wanted)
          cartStore.set(next)
          editLine = null
        },
      )
    }
  }

  if (freeLine) {
    FreeLineDialog(
      onDismiss = { freeLine = false },
      onConfirm = { label, price, quantity ->
        cartStore.set(SalesEngine.addFreeLine(cart, label, price, quantity, ::newId))
        freeLine = false
        ScanFeedback.ok(context)
      },
    )
  }

  if (checkout) {
    CheckoutDialog(
      d = d,
      //  جمع و ثبت با قیمتِ پس از تخفیف‌های خودکار (خاموش ⇒ همان سبد)
      cart = priced,
      presetDebtorId = cartDebtorId,
      presetDiscount = extrasNow().discount,
      onDismiss = { checkout = false },
      busy = cartStore.posting.collectAsState().value,
      //  برگهٔ تأییدِ تخفیف (S08) به شناسهٔ همین فاکتور بسته است
      saleKey = remember(cart) { cartStore.saleKey() },
      onConfirm = { options ->
        //  دو ضربهٔ پشتِ سرِ هم پیش از بسته شدنِ پنجره: دومی همان سبدِ قبلی
        //  را دوباره ثبت می‌کرد. `checkout` همان لحظه خوانده می‌شود.
        if (!checkout) return@CheckoutDialog
        //  POS-12: سبد همین حالا با جابه‌جایی عوض شد ⇒ ردیف‌های قبلی با کلیدِ فاکتورِ دیگر ثبت نشوند
        if (!ir.vil3ntec.tohid.data.HeldRules.shownIsCurrent(pricedFrom, cartStore.lines.value)) {
          toast(StrPos.cartChangedRetry); return@CheckoutDialog
        }
        //  POS-14: یک ثبت در هر لحظه با کلیدِ فاکتور؛ سبد فقط پس از نوشتنِ دفتر پاک می‌شود
        scope.launch { when (val posted = cartStore.post(store) { key ->
          SalesEngine.record(
          d, priced, options,
          today = ir.vil3ntec.tohid.todayIso(),
          now = System.currentTimeMillis(),
          newId = ::newId,
          //  F0-03: «سری سند»ِ این دستگاه — `{سری}-{شماره}`
          series = ir.vil3ntec.tohid.sync.DocSeries.current(context),
          saleId = key,
          //  فاکتورِ نسخهٔ ۲: توکنِ QR فقط با پرچمِ `shop.receipt_v2`
          receiptToken = ir.vil3ntec.tohid.print.Receipts.newToken(context),
        ) }) {
          SalePost.Outcome.Busy -> Unit
          SalePost.Outcome.NotSaved -> toast(SalePost.NOT_SAVED)
          is SalePost.Outcome.Refused -> toast(posted.message)
          is SalePost.Outcome.Done -> {
            val result = posted.ok
            selectedId = null
            checkout = false
            Haptics.success(context)
            toast(StrSale.saleRecorded(money(result.data.sales.last().finalTotal)))
            invoice = result.data to result.saleId
          }
        } }
      },
    )
  }

  invoice?.let { (snapshot, id) ->
    val sale = snapshot.sales.find { it.id == id }
    if (sale == null) invoice = null
    else InvoiceDialog(d = snapshot, sale = sale, onDismiss = { invoice = null }, onMessage = ::toast)
  }
}

/**
 *  شناسهٔ یکتای ردیفِ تازه — ULID، همان `uid()`ِ نسخهٔ وب (`TohidSyncCore.ulid`).
 *
 *  پیش‌تر `"id" + زمان + Math.random` بود: حدودِ سی بیتِ تصادفیِ ناامن، و
 *  دو ردیفِ یک میلی‌ثانیه از دو دستگاه می‌توانستند یک شناسه بگیرند (F0-10).
 *  ⛔ شناسهٔ ردیف‌های موجود دست نمی‌خورد — فقط ردیفِ تازه.
 */
fun newId(): String = ir.vil3ntec.tohid.sync.v1.Ulid.next()

/** حالِ حسابِ یک قرض‌دار، با همان جمله‌های نسخهٔ وب */
fun debtStateText(d: ShopData, debtorId: String): String {
  val amount = ShopStore.debt(d, debtorId)
  return when {
    amount > 0 -> StrSale.owes(money(amount))
    amount < 0 -> StrSale.hasCredit(money(-amount))
    else -> StrSale.settled
  }
}

/* ============================ تکه‌های رابط ============================ */

/**
 *  کادرِ دوربین.
 *
 *  وقتی دوربین خاموش است هیچ تصویری اینجا نیست — فقط یک کادرِ هم‌رنگِ
 *  برنامه با یک دکمه. عکسِ پیش‌فرضی که قبلاً می‌آمد حذف شد.
 */
@Composable
private fun ScannerFrame(
  on: Boolean,
  status: String,
  error: Boolean,
  granted: Boolean,
  onGrant: () -> Unit,
  onStart: () -> Unit,
  scanner: @Composable () -> Unit,
) {
  Box(
    Modifier
      .fillMaxWidth()
      .height(240.dp)
      .clip(RoundedCornerShape(Radius.md))
      .background(if (on) Color.Black else Shop.colors.surface2)
      .border(1.dp, Shop.colors.border, RoundedCornerShape(Radius.md)),
    contentAlignment = Alignment.Center,
  ) {
    if (on) {
      scanner()
      //  کادرِ سفید برداشته شد؛ فقط خطِ اسکن می‌ماند. شرحش سرِ `ScanLine`.
      ScanLine(
        Modifier
          .fillMaxWidth(0.86f)
          .height(150.dp)
      )
      Box(
        Modifier
          .align(Alignment.BottomCenter)
          .fillMaxWidth()
          .background(Color.Black.copy(alpha = 0.45f))
          .padding(horizontal = 12.dp, vertical = 8.dp),
      ) {
        Text(
          status,
          style = MaterialTheme.typography.labelMedium,
          color = if (error) Color(0xFFFFB4AE) else Color.White,
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxWidth(),
        )
      }
    } else {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
          Icons.Filled.PhotoCamera,
          contentDescription = null,
          tint = Shop.colors.muted2,
          modifier = Modifier.size(34.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
          if (granted) StrSale.cameraOff else StrSale.needCameraPermission,
          style = MaterialTheme.typography.bodySmall,
          color = Shop.colors.muted,
        )
        Spacer(Modifier.height(10.dp))
        Button(onClick = { if (granted) onStart() else onGrant() }) {
          Text(if (granted) StrSale.turnOnCamera else StrSale.allow)
        }
      }
    }
  }
}

@Composable
private fun MultiplierChip(value: Int, active: Boolean, onClick: () -> Unit) {
  Box(
    Modifier
      .clip(RoundedCornerShape(Radius.sm))
      .background(if (active) Shop.colors.primary else Shop.colors.surface2)
      .border(1.dp, if (active) Shop.colors.primary else Shop.colors.border, RoundedCornerShape(Radius.sm))
      .clickable(onClick = onClick)
      .padding(horizontal = 14.dp, vertical = 8.dp),
  ) {
    Text(
      "×${ir.vil3ntec.tohid.plain(value)}",
      style = MaterialTheme.typography.labelLarge,
      color = if (active) Color.White else Shop.colors.text,
    )
  }
}

@Composable
fun CartRow(
  name: String,
  unit: String,
  quantity: Double,
  unitPrice: Double,
  lineTotal: Double,
  selected: Boolean,
  onClick: () -> Unit,
  onPlus: () -> Unit,
  onMinus: () -> Unit,
  onRemove: () -> Unit,
  /** باز کردنِ کادرِ تایپِ تعداد و قیمت. `null` یعنی این ردیف ویرایش ندارد. */
  onEdit: (() -> Unit)? = null,
  /** قیمتِ این ردیف دستی گذاشته شده — نشانه‌اش روی کارت دیده می‌شود */
  customPrice: Boolean = false,
) {
  Column(
    Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(Radius.md))
      .background(Shop.colors.surface)
      .border(
        1.dp,
        if (selected) Shop.colors.primary else Shop.colors.border,
        RoundedCornerShape(Radius.md),
      )
      .clickable(onClick = onClick)
      .padding(14.dp),
  ) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Text(name, style = MaterialTheme.typography.titleSmall, color = Shop.colors.text)
        Spacer(Modifier.height(3.dp))
        Text(
          "${qty(quantity)}${if (unit.isNotBlank()) " $unit" else ""} × ${money(unitPrice)}" +
            if (customPrice) "  " + StrSale.customPrice else "",
          style = MaterialTheme.typography.bodySmall,
          color = if (customPrice) Shop.colors.primary else Shop.colors.muted,
        )
      }
      Text(
        "${money(lineTotal)} ؋",
        style = MaterialTheme.typography.titleSmall,
        color = Shop.colors.text,
      )
    }

    AnimatedVisibility(visible = selected) {
      Row(
        Modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        OutlinedButton(onClick = onMinus, modifier = Modifier.weight(1f)) { Text("−") }
        OutlinedButton(onClick = onPlus, modifier = Modifier.weight(1f)) { Text("+") }
        //  زدنِ ۲۵ بار روی «+» برای فروشِ عمده، بدترین جای ممکن برای کند
        //  بودن است — اینجا تعداد و قیمت تایپ می‌شوند
        if (onEdit != null) {
          OutlinedButton(onClick = onEdit) {
            Icon(Icons.Filled.Edit, contentDescription = StrSale.qtyAndPrice)
          }
        }
        OutlinedButton(
          onClick = onRemove,
          colors = ButtonDefaults.outlinedButtonColors(contentColor = Shop.colors.danger),
        ) {
          Icon(Icons.Filled.DeleteOutline, contentDescription = StrSale.removeFromCart)
        }
      }
    }
  }
}

@Composable
fun CartBar(
  total: Double,
  debtorName: String?,
  debtorNote: String?,
  onPickDebtor: () -> Unit,
  onClearDebtor: () -> Unit,
  onFinalize: () -> Unit,
) {
  Surface(
    color = Shop.colors.surfaceSolid,
    tonalElevation = 0.dp,
    shadowElevation = 12.dp,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(Modifier.padding(14.dp)) {
      Row(
        Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(Radius.sm))
          .background(if (debtorName != null) Shop.colors.primaryTint else Shop.colors.surface2)
          .clickable(onClick = onPickDebtor)
          .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(
          Icons.Filled.PersonOutline,
          contentDescription = null,
          tint = if (debtorName != null) Shop.colors.primary else Shop.colors.muted,
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
          Text(
            if (debtorName != null) StrSale.onAccount(debtorName) else StrSale.putOnAccount,
            style = MaterialTheme.typography.titleSmall,
            color = Shop.colors.text,
          )
          Text(
            debtorNote ?: StrSale.pickDebtorForCredit,
            style = MaterialTheme.typography.labelSmall,
            color = Shop.colors.muted,
          )
        }
        if (debtorName != null) {
          IconButton(onClick = onClearDebtor) {
            Icon(Icons.Filled.Close, contentDescription = StrSale.removeDebtor, tint = Shop.colors.muted)
          }
        }
      }

      Spacer(Modifier.height(10.dp))

      Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
          Text(StrSale.total, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
          Text(
            "${money(total)} ؋",
            style = MaterialTheme.typography.headlineSmall,
            color = Shop.colors.text,
            fontWeight = FontWeight.Bold,
          )
        }
        Button(onClick = onFinalize) {
          Text(if (debtorName != null) StrSale.recordCredit else StrSale.recordSale)
        }
      }
    }
  }
}

/**
 *  دوربینِ بیکار بعد از این‌قدر می‌خوابد.
 *
 *  سه دقیقه: کوتاه‌تر از این، وسطِ حساب کردنِ یک فاکتورِ بزرگ خاموش
 *  می‌شود و آدم را عصبانی می‌کند؛ بلندتر از این، دیگر صرفه‌جویی نیست.
 */
private const val CAMERA_IDLE_MS = 3L * 60L * 1000L

/** قرصِ کوچکِ صندوقِ گوشیِ نسخهٔ ۳ (رنگِ سبد یا خاکستری روی سطحِ تیره) — کادرِ اسکن */
@Composable
private fun V3Pill(text: String, bg: Color, fg: Color, height: androidx.compose.ui.unit.Dp = 44.dp, onClick: () -> Unit) {
  Box(
    Modifier
      .height(height)
      .clip(RoundedCornerShape(percent = 50))
      .background(bg)
      .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
      .padding(horizontal = 16.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(text, color = fg, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
  }
}
