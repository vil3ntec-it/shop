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
  onRegisterBarcode: (String) -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  val cart by cartStore.lines.collectAsState()
  val cartDebtorId by cartStore.debtorId.collectAsState()
  //  PRM: تخفیف‌های خودکار — کلیدِ دکان خاموش ⇒ null و `priced` همان `cart` است
  val promo = ir.vil3ntec.tohid.ui.PromoLive.rememberOutcome(d, cart)
  val priced = ir.vil3ntec.tohid.data.PromoEngine.priced(cart, promo)
  //  POS-12: همان سبدی که `priced` از آن ساخته شد — ثبت با سبدِ عوض‌شده نه
  val pricedFrom = cart
  //  POS-38: «تحویلِ بعدی» فقط با تنظیمِ روشن، بخشِ سفارشِ روشن و اجازهٔ ثبتِ سفارش
  val deliverLater = ir.vil3ntec.tohid.ui.components.pos.deliverLaterReady()
  var deliverOpen by remember { mutableStateOf(false) }
  //  PRD-24 (پشتِ `ProductInfoPrefs`، پیش‌فرض خاموش): پیشنهادِ جایگزینِ موجود هنگامِ کمبود
  val infoOn = ir.vil3ntec.tohid.ui.components.product.productInfoOn()

  // کالای حذف‌شده نباید در سبد بماند
  LaunchedEffect(d.products.size, d.debtors.size) { cartStore.prune(d) }

  var cameraOn by rememberSaveable { mutableStateOf(true) }
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
    if (!granted) askCamera.launch(Manifest.permission.CAMERA)
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
  //  PRD-04: بارکدِ بسته‌ها (کارتن ⇒ ۲۴ دانه) — جدا، فقط کامل
  val packIndex = remember(d.products) { ShopStore.packIndex(d) }

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

    //  PRD-04: بارکدِ بسته (کارتنِ ۲۴ دانه‌ای) ⇒ «اندازهٔ بسته» از واحدِ کالا، با همان نگهبانِ موجودی؛
    //  همان کد بارکدِ کالا هم (تعارضِ آفلاین) ⇒ مبهم: نه بسته، نه کالا
    val packLookup = packIndex.lookup(code) { ir.vil3ntec.tohid.ui.components.pos.PosSearch.isItemCode(it, index) }
    if (packLookup == ir.vil3ntec.tohid.core.product.PackBarcodes.Lookup.Ambiguous) {
      ScanFeedback.unknown(context)
      manual = code
      toast(StrSale.ambiguousBarcode(code))
      return
    }
    val pack = (packLookup as? ir.vil3ntec.tohid.core.product.PackBarcodes.Lookup.Pack)?.hit
    val packProduct = pack?.let { h -> d.products.firstOrNull { it.id == h.productId } }
    if (pack != null && packProduct != null) {
      if (!packProduct.active) { ScanFeedback.unknown(context); toast(StrSale.inactiveActivate(packProduct.name)); return }
      val want = multiplier * pack.pack.sizeMilli / 1000.0
      val added = SalesEngine.addToCartCapped(cartStore.lines.value, packProduct.id, want, ShopStore.stock(d, packProduct.id))
      if (!cartStore.set(added.cart)) { ScanFeedback.unknown(context); toast(CartStore.NOT_ADDED); return }
      selectedId = packProduct.id
      if (added.capped) {
        ScanFeedback.unknown(context)
        toast(Str.onlyInStock(packProduct.name, "${qty(added.available)} ${packProduct.unit}"))
      } else {
        ScanFeedback.ok(context)
        toast(StrPos.weighedAdded(packProduct.name, "$multiplier × ${ir.vil3ntec.tohid.core.product.PackBarcodes.label(pack.pack, packProduct.unit)}"))
      }
      return
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
    //  موتورِ تازهٔ موجودی (روشن): بارکدِ ناشناس که «سری»ِ یک کالاست ⇒ همان کالا، و فروش اول از همین سری
    val lotHit = if (hit is BarcodeMatch.Hit.Product) null else ir.vil3ntec.tohid.data.StockActions.lotForCode(d, code)
    val productId = (hit as? BarcodeMatch.Hit.Product)?.id ?: lotHit?.first
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
        if (answer == SnackbarResult.ActionPerformed) onRegisterBarcode(code)
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
    val next = if (lotHit != null) SalesEngine.setLineLot(added.cart, productId, lotHit.second) else added.cart
    if (!cartStore.set(next)) { ScanFeedback.unknown(context); toast(CartStore.NOT_ADDED); return }
    selectedId = productId
    if (added.capped) {
      ScanFeedback.unknown(context)
      toast(Str.onlyInStock(product.name, qty(added.available) + (if (product.unit.isNotBlank()) " ${product.unit}" else "")) +
        //  PRD-24 (پشتِ `ProductInfoPrefs`، پیش‌فرض خاموش): جایگزینِ موجود
        ir.vil3ntec.tohid.data.ProductInfoMap.insteadSuffix(d, product, infoOn))
    } else {
      ScanFeedback.ok(context)
      //  اگر از نیمهٔ بارکد شناخته شد، همان را بگو — فروشنده باید بداند
      //  که کدِ کامل خوانده نشده، تا اگر کالا آن نبود بفهمد
      val whole = (hit as? BarcodeMatch.Hit.Product)?.exact ?: true
      toast(
        when {
          lotHit != null -> "${product.name} — سری ${lotHit.second} به سبد اضافه شد"
          whole -> StrSale.addedToCart(product.name)
          else -> StrSale.halfBarcode(product.name)
        }
      )
    }
  }

  //  بارکدی که گوشیِ جفت‌شده خواند، همین‌جا به سبد می‌رود
  LaunchedEffect(Unit) {
    for (code in ir.vil3ntec.tohid.sync.PhoneScanner.inbox) onBarcode(code, skipDedup = true, fromPhone = true)
  }

  //  HW-05 (فقط با «ابزارهای تازهٔ دستگاه»): اسکنی که در کادرِ قیمت/تعداد افتاد و نگهبانِ `NumberField`
  //  برش گرداند (`ScanRouter.dispatch`) به همین سبد می‌رسد — همان کارِ `DeskSale` در کامپیوتر
  val scanOwner = remember { Any() }
  val hwTools = ir.vil3ntec.tohid.print.HwTools.on.value
  DisposableEffect(hwTools) {
    if (hwTools) ir.vil3ntec.tohid.scan.ScanRouter.register(scanOwner) { onBarcode(it, skipDedup = true); true }
    onDispose { ir.vil3ntec.tohid.scan.ScanRouter.unregister(scanOwner) }
  }
  //  …و اسکنی که بیرون از صندوق از کادرِ عدد برگشت و صندوق را باز کرد (`ScanRouter.toSale`)
  LaunchedEffect(hwTools) {
    if (hwTools) for (code in ir.vil3ntec.tohid.scan.ScanRouter.toSale) onBarcode(code, skipDedup = true)
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
          //  HW-05: بارکدخوانی که به‌جای Enter پسوندِ تنظیم‌شده می‌زند همین‌جا تمام می‌شود
          hid.feed(ch, now)?.let { onBarcode(it, skipDedup = true) }
          return@onKeyEvent true
        }
        false
      }
  ) {
    LazyColumn(
      Modifier.fillMaxSize(),
      contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, if (cart.isEmpty()) 24.dp else 200.dp),
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
        Spacer(Modifier.height(16.dp))
      }

      /* ---------------------------- سبد ---------------------------- */
      item { ir.vil3ntec.tohid.ui.components.pos.HeldInvoiceBar(held, Modifier.padding(bottom = 8.dp)) }
      item { ir.vil3ntec.tohid.ui.components.pos.InvoiceNoteButton(cartStore.note.collectAsState().value, cart.isNotEmpty()) { cartStore.setNote(it) } }
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
      } else {
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
        //  POS-38 (پشتِ `DeliverLaterPrefs`، پیش‌فرض خاموش): سبد ⇐ سفارشِ ارسال، بی فروش
        if (deliverLater) {
          item(key = "deliver-later") {
            TohidSecondaryButton(
              ir.vil3ntec.tohid.core.i18n.StrDeliver.button,
              onClick = { deliverOpen = true },
              modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
              icon = Icons.Filled.LocalShipping,
            )
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
    AnimatedVisibility(
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
  }

  /* --------------------------- پنجره‌ها --------------------------- */
  //  POS-38: سفارشِ ارسال از همین سبد (با تخفیفِ خودکار، `priced`)؛ پس از ثبت سبد خالی — هیچ فروشی این‌جا نیست
  if (deliverOpen) {
    ir.vil3ntec.tohid.ui.components.pos.DeliverLaterSheetWired(d, priced, onClose = { deliverOpen = false }) { o ->
      deliverOpen = false
      cartStore.clear()
      selectedId = null
      toast(ir.vil3ntec.tohid.core.i18n.StrDeliver.created(o.orderNo))
    }
  }

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
        free = line.free,
        linePolicy = remember { ir.vil3ntec.tohid.data.LinePolicyPrefs.of(context) },
        saleUnit = product?.saleUnit.orEmpty(),
        unitFactor = product?.unitFactor ?: 1.0,
        //  POS-22 (پشتِ `PriceWhyPrefs`، پیش‌فرض خاموش)
        why = if (ir.vil3ntec.tohid.ui.components.pos.priceWhyOn()) ir.vil3ntec.tohid.data.PriceWhyLine.rows(d, cart, priced, promo, id) else null,
        note = ir.vil3ntec.tohid.ui.components.pos.posLineNote(line.note) { n -> cartStore.set(ir.vil3ntec.tohid.data.CartNotes.set(cartStore.lines.value, id, n)) },
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
      linePolicy = remember { ir.vil3ntec.tohid.data.LinePolicyPrefs.of(context) },
      onDismiss = { freeLine = false },
      onConfirm = { label, price, quantity ->
        cartStore.set(SalesEngine.addFreeLine(cart, label, price, quantity, ::newId))
        freeLine = false
        ScanFeedback.ok(context)
      },
    )
  }

  //  CRM-03 / S07 — سقفِ اعتبار در صندوق (`ShopPolicy.creditGate`، پیش‌فرض خاموش)
  val creditGateOn = remember { ir.vil3ntec.tohid.data.ShopPolicy.creditGate(context) }
  /** فاکتوری که از سقفِ مشتری می‌گذرد و منتظرِ تأیید است (گزینه‌های پنجرهٔ پرداخت + مقدارِ بیش از سقف) */
  var creditAsk by remember { mutableStateOf<Pair<SalesEngine.Checkout, ir.vil3ntec.tohid.core.credit.CreditGate.Over>?>(null) }

  /**
   *  ثبتِ همین سبد با گزینه‌های پنجرهٔ پرداخت — مستقیم، یا پس از تأییدِ سقفِ اعتبار.
   *  POS-14: یک ثبت در هر لحظه با کلیدِ فاکتور؛ سبد فقط پس از نوشتنِ دفتر پاک می‌شود.
   *  S2: با تخفیفِ خودکار، نشانِ قیمتِ فهرست و سهمِ هر قاعده روی فاکتور (خاموش ⇒ همان).
   */
  fun postSale(options: SalesEngine.Checkout) {
    scope.launch { when (val posted = cartStore.post(store, gate = { key -> ir.vil3ntec.tohid.ui.PromoLive.redeemGate(context, key, promo) }) { key -> ir.vil3ntec.tohid.data.PromoEngine.stamp(
      SalesEngine.record(
      d, priced, options.copy(note = cartStore.note.value),
      today = ir.vil3ntec.tohid.todayIso(),
      now = System.currentTimeMillis(),
      newId = ::newId,
      //  F0-03: «سری سند»ِ این دستگاه — `{سری}-{شماره}`
      series = ir.vil3ntec.tohid.sync.DocSeries.current(context),
      saleId = key,
      //  فاکتورِ نسخهٔ ۲: توکنِ QR فقط با پرچمِ `shop.receipt_v2`
      receiptToken = ir.vil3ntec.tohid.print.Receipts.newToken(context),
    ), promo) }) {
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
  }

  if (checkout) {
    CheckoutDialog(
      d = d,
      //  جمع و ثبت با قیمتِ پس از تخفیف‌های خودکار (خاموش ⇒ همان سبد)
      cart = priced,
      promo = promo,
      presetDebtorId = cartDebtorId,
      presetDiscount = extrasNow().discount, presetAmount = extrasNow().discountAmount,
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
        //  CRM-03 / S07 (پشتِ `ShopPolicy.creditGate`): نسیهٔ بیش از سقف ⇒ «درخواستِ تأیید»؛ بی تأیید
        //  هیچ چیز ثبت نمی‌شود (`SalesEngine.record` هم دوباره می‌سنجد)
        val sale = options.copy(creditGate = creditGateOn)
        val over = SalesEngine.creditOver(d, priced, sale)
        if (over != null) { creditAsk = sale to over; return@CheckoutDialog }
        postSale(sale)
      },
    )
  }

  creditAsk?.let { (pending, over) ->
    val debtorId = pending.debtorId
    if (debtorId != null) ir.vil3ntec.tohid.ui.components.staff.CreditApprovalDialog(
      summary = (d.debtors.find { it.id == debtorId }?.name?.let { "«$it» — " } ?: "") + SalesEngine.creditOverText(over),
      debtorId = debtorId,
      addMinor = over.addMinor,
      selfRole = remember { ir.vil3ntec.tohid.data.ShopRole.current(context) },
      selfName = pending.seller,
      requesterUserId = remember { runCatching { ir.vil3ntec.tohid.sync.SyncStore(context).accountId }.getOrDefault("") },
      //  با تأیید همان فاکتور با همان گزینه‌ها ثبت می‌شود؛ PINِ غلط یا انصراف ⇒ هیچ چیز
      onApproved = { ok -> creditAsk = null; postSale(pending.copy(creditApproval = ok)) },
      onDismiss = { creditAsk = null },
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
