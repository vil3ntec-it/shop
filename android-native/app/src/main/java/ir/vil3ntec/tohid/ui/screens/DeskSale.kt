package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.data.BarcodeMatch
import ir.vil3ntec.tohid.data.CartQuery
import ir.vil3ntec.tohid.data.CartStore
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.numberInvalid
import ir.vil3ntec.tohid.numericInput
import ir.vil3ntec.tohid.parseNumber
import ir.vil3ntec.tohid.qty
import ir.vil3ntec.tohid.scan.ScanFeedback
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
  val cartDebtorId by cartStore.debtorId.collectAsState()
  LaunchedEffect(d.products.size, d.debtors.size) { cartStore.prune(d) }

  var query by remember { mutableStateOf("") }
  var hint by remember { mutableStateOf(0) }
  var selected by remember { mutableStateOf<String?>(null) }
  var pickDebtor by remember { mutableStateOf(false) }
  var freeLine by remember { mutableStateOf(false) }
  var editLine by remember { mutableStateOf<String?>(null) }
  var lastInvoice by remember { mutableStateOf<Pair<ShopData, String>?>(null) }
  var showInvoice by remember { mutableStateOf(false) }

  val canDiscount = remember { ir.vil3ntec.tohid.data.ShopRole.canDiscount(context) }
  val blockExpired = remember { ir.vil3ntec.tohid.data.ShopPolicy.blockExpired(context) }
  val seller = remember { runCatching { ir.vil3ntec.tohid.sync.SyncStore(context).accountName }.getOrDefault("") }
  var payment by remember { mutableStateOf(SalesEngine.Payment.CASH) }
  var discountText by remember { mutableStateOf("") }
  var paidText by remember { mutableStateOf("") }
  LaunchedEffect(cartDebtorId) { if (cartDebtorId != null) payment = SalesEngine.Payment.CREDIT }

  val searchFocus = remember { FocusRequester() }
  LaunchedEffect(Unit) { runCatching { searchFocus.requestFocus() } }
  fun refocus() { runCatching { searchFocus.requestFocus() } }

  fun toast(text: String) { scope.launch { snackbar.showSnackbar(text) } }

  val index = remember(d.products) { ShopStore.barcodeIndex(d) }

  //  «۳*کد» ⇐ (۳، «کد») — رقمِ فارسی هم (F0-06، `CartQuery`)
  val parsed = remember(query) { CartQuery.parse(query).let { it.count to it.term } }
  val term = parsed.second.trim()
  val matches: List<Product> = remember(term, d.products) {
    if (term.isEmpty()) emptyList()
    else d.products.asSequence()
      .filter { it.active }
      .filter { p -> p.name.contains(term, ignoreCase = true) || CartQuery.barcodeStarts(p.barcodes, term) }
      .sortedWith(compareBy({ !it.name.startsWith(term, ignoreCase = true) }, { it.name }))
      .take(9)
      .toList()
  }
  LaunchedEffect(term) { hint = 0 }

  fun add(product: Product, count: Double) {
    val added = SalesEngine.addToCartCapped(cartStore.lines.value, product.id, count, ShopStore.stock(d, product.id))
    cartStore.set(added.cart)
    selected = product.id
    if (added.capped) {
      ScanFeedback.unknown(context)
      toast("${product.name} فقط ${qty(added.available)}${if (product.unit.isNotBlank()) " ${product.unit}" else ""} موجود است")
    } else ScanFeedback.ok(context)
  }

  /** Enter در کادر: بارکدِ کامل ⇐ همان؛ وگرنه پیشنهادِ برجسته */
  fun submit() {
    val (count, raw) = parsed
    val code = raw.trim()
    if (code.isEmpty()) return
    when (val hit = BarcodeMatch.find(code, index)) {
      is BarcodeMatch.Hit.Product -> {
        val p = d.products.firstOrNull { it.id == hit.id } ?: return
        if (!p.active) { toast("«${p.name}» غیرفعال است"); return }
        add(p, count); query = ""; return
      }
      else -> {}
    }
    val pick = matches.getOrNull(hint)
    if (pick != null) { add(pick, count); query = ""; return }
    ScanFeedback.unknown(context)
    scope.launch {
      val r = snackbar.showSnackbar("«$code» پیدا نشد", actionLabel = "ثبتِ کالا", duration = SnackbarDuration.Long)
      if (r == SnackbarResult.ActionPerformed) onRegisterBarcode(code)
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
    when (val hit = BarcodeMatch.find(code, ShopStore.barcodeIndex(dd))) {
      is BarcodeMatch.Hit.Product -> dd.products.firstOrNull { it.id == hit.id }?.let {
        if (!it.active) toast("«${it.name}» غیرفعال است") else add(it, 1.0)
      }
      else -> scope.launch {
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
  val totals = SalesEngine.totals(d, cart, checkout)
  val discountBad = canDiscount && numberInvalid(discountText)
  val paidBad = payment == SalesEngine.Payment.CREDIT && numberInvalid(paidText)

  fun finish() {
    if (cart.isEmpty()) { toast("سبد خالی است"); return }
    if (discountBad || paidBad) { toast("عددِ «${if (discountBad) "تخفیف" else "دریافتی"}» درست نیست"); return }
    if (payment == SalesEngine.Payment.CREDIT && cartDebtorId == null) { toast("فروشِ نسیه مشتری می‌خواهد — F2"); pickDebtor = true; return }
    when (val r = SalesEngine.record(d, cart, checkout, ir.vil3ntec.tohid.todayIso(), System.currentTimeMillis(), ::newId)) {
      is SalesEngine.Result.Failed -> toast(r.message)
      is SalesEngine.Result.Ok -> {
        scope.launch { store.save(r.data) }
        cartStore.clear()
        selected = null; discountText = ""; paidText = ""; payment = SalesEngine.Payment.CASH
        lastInvoice = r.data to r.saleId
        toast("فروش ${money(r.data.sales.last().finalTotal)} ؋ ثبت شد — F8 چاپ")
        refocus()
      }
    }
  }

  fun step(id: String, sign: Int) {
    val line = cart.find { it.productId == id } ?: return
    val unit = d.products.find { it.id == id }?.unit.orEmpty()
    cartStore.set(SalesEngine.setCartQty(cart, id, line.quantity + sign * SalesEngine.cartStep(unit)))
  }
  fun moveSel(delta: Int) {
    if (cart.isEmpty()) return
    val i = cart.indexOfFirst { it.productId == selected }
    selected = cart[((if (i < 0) cart.size else i) + delta).coerceIn(0, cart.size - 1)].productId
  }

  Row(
    Modifier
      .fillMaxSize()
      .onPreviewKeyEvent { e ->
        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        when {
          e.key == Key.F9 || e.key == Key.F12 || (e.isCtrlPressed && (e.key == Key.Enter || e.key == Key.NumPadEnter)) -> { finish(); true }
          e.key == Key.F2 -> { pickDebtor = true; true }
          e.key == Key.F3 -> { selected?.let { editLine = it }; true }
          e.key == Key.F4 -> { freeLine = true; true }
          e.key == Key.F8 -> { if (lastInvoice != null) showInvoice = true; true }
          else -> false
        }
      }
      .padding(12.dp),
  ) {
    /* ─────────── سمتِ کار: کادر، پیشنهادها، سبد ─────────── */
    Column(Modifier.weight(1f).fillMaxHeight()) {
      Box {
        OutlinedTextField(
          value = query,
          onValueChange = { query = it },
          singleLine = true,
          placeholder = { Text("بارکد را بخوانید یا نامِ کالا را بنویسید — ۳* پیش از آن یعنی سه تا") },
          modifier = Modifier
            .fillMaxWidth()
            .focusRequester(searchFocus)
            .onPreviewKeyEvent { e ->
              if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
              val empty = query.isEmpty()
              when (e.key) {
                Key.Enter, Key.NumPadEnter -> { if (!e.isCtrlPressed) { submit(); true } else false }
                Key.DirectionDown -> { if (matches.isNotEmpty()) hint = (hint + 1).coerceAtMost(matches.size - 1) else moveSel(1); true }
                Key.DirectionUp -> { if (matches.isNotEmpty()) hint = (hint - 1).coerceAtLeast(0) else moveSel(-1); true }
                Key.Escape -> { if (!empty) { query = ""; true } else false }
                Key.Delete -> if (empty && selected != null) {
                  cartStore.set(SalesEngine.setCartQty(cart, selected!!, 0.0)); moveSel(0); true
                } else false
                Key.Plus, Key.NumPadAdd, Key.Equals -> if (empty && selected != null) { step(selected!!, 1); true } else false
                Key.Minus, Key.NumPadSubtract -> if (empty && selected != null) { step(selected!!, -1); true } else false
                else -> false
              }
            },
        )
      }
      if (matches.isNotEmpty()) {
        Surface(
          color = Shop.colors.surfaceSolid,
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier.fillMaxWidth().padding(top = 4.dp).border(1.dp, Shop.colors.border, RoundedCornerShape(8.dp)),
        ) {
          Column {
            matches.forEachIndexed { i, p ->
              val stock = ShopStore.stock(d, p.id)
              Row(
                Modifier
                  .fillMaxWidth()
                  .background(if (i == hint) Shop.colors.primary.copy(alpha = 0.12f) else Shop.colors.surfaceSolid)
                  .clickable { add(p, parsed.first); query = ""; refocus() }
                  .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(p.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Shop.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(p.barcodes.firstOrNull().orEmpty(), Modifier.width(140.dp), style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted, maxLines = 1)
                Text("موجودی ${qty(stock)}", Modifier.width(110.dp), style = MaterialTheme.typography.bodySmall, color = if (stock <= 0) Shop.colors.danger else Shop.colors.muted)
                Text("${money(p.salePrice)} ؋", Modifier.width(110.dp), style = MaterialTheme.typography.bodyMedium, color = Shop.colors.text, textAlign = TextAlign.End)
              }
            }
          }
        }
      }

      Spacer(Modifier.height(10.dp))
      //  سرِ جدولِ سبد
      val head = MaterialTheme.typography.labelMedium
      Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)).background(Shop.colors.surface2).padding(horizontal = 10.dp, vertical = 8.dp),
      ) {
        Text("#", Modifier.width(32.dp), style = head, color = Shop.colors.muted)
        Text("کالا", Modifier.weight(1f), style = head, color = Shop.colors.muted)
        Text("تعداد", Modifier.width(120.dp), style = head, color = Shop.colors.muted, textAlign = TextAlign.Center)
        Text("قیمتِ واحد", Modifier.width(120.dp), style = head, color = Shop.colors.muted, textAlign = TextAlign.End)
        Text("جمع", Modifier.width(130.dp), style = head, color = Shop.colors.muted, textAlign = TextAlign.End)
      }
      val listState = rememberLazyListState()
      LaunchedEffect(selected, cart.size) {
        val i = cart.indexOfFirst { it.productId == selected }
        if (i >= 0) runCatching { listState.animateScrollToItem(i) }
      }
      LazyColumn(
        state = listState,
        modifier = Modifier.weight(1f).fillMaxWidth().border(1.dp, Shop.colors.border, RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)),
      ) {
        if (cart.isEmpty()) item {
          Text(
            "سبد خالی است.\nبارکد را بخوانید یا نام را بنویسید و Enter بزنید.",
            Modifier.fillMaxWidth().padding(32.dp),
            style = MaterialTheme.typography.bodyMedium, color = Shop.colors.muted, textAlign = TextAlign.Center,
          )
        }
        itemsIndexed(cart, key = { _, l -> l.productId }) { i, line ->
          val p = d.products.find { it.id == line.productId }
          val price = line.unitPrice ?: p?.salePrice ?: 0.0
          val on = line.productId == selected
          Row(
            Modifier
              .fillMaxWidth()
              .background(if (on) Shop.colors.primary.copy(alpha = 0.10f) else if (i % 2 == 1) Shop.colors.surface2.copy(alpha = 0.5f) else Shop.colors.surface)
              .clickable { selected = line.productId; refocus() }
              .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text("${i + 1}", Modifier.width(32.dp), style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
            Text(
              if (line.free) line.label else p?.name.orEmpty(),
              Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Shop.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Row(Modifier.width(120.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
              TextButton(onClick = { step(line.productId, -1); refocus() }, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(28.dp)) { Text("−") }
              Text("${qty(line.quantity)} ${p?.unit.orEmpty()}", style = MaterialTheme.typography.bodyMedium, color = Shop.colors.text, modifier = Modifier.padding(horizontal = 4.dp))
              TextButton(onClick = { step(line.productId, 1); refocus() }, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(28.dp)) { Text("+") }
            }
            Text(money(price), Modifier.width(120.dp).clickable { editLine = line.productId }, style = MaterialTheme.typography.bodyMedium, color = if (line.unitPrice != null) Shop.colors.primary else Shop.colors.text, textAlign = TextAlign.End)
            Text(money(SalesEngine.lineTotal(price, line.quantity)), Modifier.width(130.dp), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = Shop.colors.text, textAlign = TextAlign.End)
          }
        }
      }
      Text(
        "Enter افزودن · ↑↓ ردیف · + − تعداد · Delete حذف · F3 قیمت/تعداد · F4 قلمِ آزاد · F2 مشتری · F9 ثبت · F8 چاپِ آخر · Esc پاک",
        Modifier.padding(top = 6.dp), style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted,
      )
    }

    Spacer(Modifier.width(12.dp))

    /* ─────────── تسویه ─────────── */
    Surface(
      color = Shop.colors.surfaceSolid,
      shape = RoundedCornerShape(10.dp),
      modifier = Modifier.width(340.dp).fillMaxHeight().border(1.dp, Shop.colors.border, RoundedCornerShape(10.dp)),
    ) {
      Column(Modifier.padding(14.dp)) {
        Text("مبلغِ قابلِ پرداخت", style = MaterialTheme.typography.labelMedium, color = Shop.colors.muted)
        Text("${money(totals.finalTotal)} ؋", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Shop.colors.text)
        if (totals.discount > 0) Text("جمع ${money(totals.subtotal)} − تخفیف ${money(totals.discount)}", style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
        Text("${cart.size} ردیف", style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)

        Spacer(Modifier.height(14.dp))
        Text("مشتری (F2)", style = MaterialTheme.typography.labelMedium, color = Shop.colors.muted)
        val debtor = cartDebtorId?.let { id -> d.debtors.find { it.id == id } }
        Row(verticalAlignment = Alignment.CenterVertically) {
          OutlinedButton(onClick = { pickDebtor = true }, modifier = Modifier.weight(1f)) {
            Text(debtor?.name ?: "مشتریِ گذری", maxLines = 1, overflow = TextOverflow.Ellipsis)
          }
          if (debtor != null) TextButton(onClick = { cartStore.setDebtor(null); payment = SalesEngine.Payment.CASH }) { Text("برداشتن") }
        }
        debtor?.let { Text(debtStateText(d, it.id), style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted) }

        Spacer(Modifier.height(12.dp))
        Text("پرداخت", style = MaterialTheme.typography.labelMedium, color = Shop.colors.muted)
        Row {
          listOf("نقدی" to SalesEngine.Payment.CASH, "نسیه" to SalesEngine.Payment.CREDIT).forEach { (label, value) ->
            val on = payment == value
            if (on) Button(onClick = {}, modifier = Modifier.weight(1f)) { Text(label) }
            else OutlinedButton(onClick = { payment = value; if (value == SalesEngine.Payment.CREDIT && cartDebtorId == null) pickDebtor = true }, modifier = Modifier.weight(1f)) { Text(label) }
            Spacer(Modifier.width(6.dp))
          }
        }

        if (canDiscount) {
          Spacer(Modifier.height(10.dp))
          OutlinedTextField(
            value = discountText,
            onValueChange = { discountText = numericInput(it) },
            label = { Text("تخفیف (٪)") },
            singleLine = true,
            isError = discountBad,
            supportingText = if (discountBad) { { Text("عدد درست نیست") } } else null,
            modifier = Modifier.fillMaxWidth(),
          )
        }
        if (payment == SalesEngine.Payment.CREDIT) {
          Spacer(Modifier.height(8.dp))
          OutlinedTextField(
            value = paidText,
            onValueChange = { paidText = numericInput(it) },
            label = { Text("دریافتیِ همین حالا (؋)") },
            singleLine = true,
            isError = paidBad,
            supportingText = if (paidBad) { { Text("عدد درست نیست") } } else null,
            modifier = Modifier.fillMaxWidth(),
          )
        }

        Spacer(Modifier.weight(1f))
        Button(
          onClick = { finish() },
          enabled = cart.isNotEmpty(),
          modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("ثبتِ فروش  (F9)", style = MaterialTheme.typography.titleMedium) }
        Spacer(Modifier.height(6.dp))
        Row {
          OutlinedButton(onClick = { freeLine = true }, modifier = Modifier.weight(1f)) { Text("قلمِ آزاد F4") }
          Spacer(Modifier.width(6.dp))
          OutlinedButton(onClick = { if (lastInvoice != null) showInvoice = true }, enabled = lastInvoice != null, modifier = Modifier.weight(1f)) { Text("چاپِ آخر F8") }
        }
        if (cart.isNotEmpty()) {
          TextButton(onClick = { cartStore.clear(); selected = null; refocus() }, modifier = Modifier.fillMaxWidth()) {
            Text("خالی کردنِ سبد", color = Shop.colors.danger)
          }
        }
      }
    }
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
  if (showInvoice) lastInvoice?.let { (snap, id) ->
    snap.sales.find { it.id == id }?.let { sale ->
      InvoiceDialog(d = snap, sale = sale, onDismiss = { showInvoice = false; refocus() }, onMessage = ::toast)
    } ?: run { showInvoice = false }
  }
}
