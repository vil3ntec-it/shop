package ir.vil3ntec.tohid.ui.screens

import ir.vil3ntec.tohid.data.invoiceLabel
import ir.vil3ntec.tohid.data.isOnlineOrder
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.RowScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import ir.vil3ntec.tohid.core.audit.InvoiceVoid
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.displayDate
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.plain
import ir.vil3ntec.tohid.qty
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.pressMenu
import ir.vil3ntec.tohid.ui.theme.Radius
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.launch
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrInvoices
import ir.vil3ntec.tohid.core.i18n.StrNav
import ir.vil3ntec.tohid.core.i18n.StrPos
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.core.i18n.StrSettings

/**
 *  تاریخچهٔ فروش.
 *
 *  هر فاکتوری که ثبت شده اینجاست — از جمله لغوشده‌ها. فاکتور هیچ‌وقت پاک
 *  نمی‌شود، فقط لغو می‌شود؛ فروشی که افتاده حقیقتی است که در گزارشِ سود و
 *  حسابِ قرض‌دار اثر گذاشته، و برداشتنش آن عددها را عوض می‌کند.
 *
 *  // i18n: سه‌زبانه — نوشته‌ها در `StrInvoices` (LOC-06)؛ دلیلِ لغو و نامِ قلمِ آزاد داده‌اند.
 */
@Composable
fun SalesHistoryScreen(
  store: ShopStore,
  cartStore: ir.vil3ntec.tohid.data.CartStore,
  d: ShopData,
  snackbar: SnackbarHostState,
  onEditInCart: () -> Unit,
) {
  val scope = rememberCoroutineScope()
  var reopenFor by remember { mutableStateOf<Sale?>(null) }

  var filter by rememberSaveable { mutableStateOf("all") }
  //  جستجو و ماه — بدونِ این دو، پیدا کردنِ فاکتورِ دیروز در دکانی که یک
  //  سال کار کرده یعنی اسکرول کردنِ هجده هزار ردیف
  var search by rememberSaveable { mutableStateOf("") }
  var month by rememberSaveable { mutableStateOf(ALL_MONTHS) }
  var invoiceFor by remember { mutableStateOf<Sale?>(null) }
  var returnFor by remember { mutableStateOf<Sale?>(null) }
  var cancelFor by remember { mutableStateOf<Sale?>(null) }
  var payFixFor by remember { mutableStateOf<Sale?>(null) }
  //  STF-02: نقشِ «نیازمندِ تأیید» ِ `sales.cancel` روی Sync v3 ⇒ برگهٔ PIN ِ مدیر پیش از پنجرهٔ لغو (`CancelAsk`)؛ «اصلاح» نه
  var cancelAsk by remember { mutableStateOf<Sale?>(null) }
  var cancelSlip by remember { mutableStateOf<ir.vil3ntec.tohid.core.perm.ApprovalSlip.Slip?>(null) }

  val roleContext = androidx.compose.ui.platform.LocalContext.current
  val canCancel = remember { ir.vil3ntec.tohid.data.ShopRole.canCancelSale(roleContext) }
  val askCancel = remember { ir.vil3ntec.tohid.data.CancelAsk.offered(roleContext) }
  //  POS-40 — «اصلاحِ روشِ پرداخت» فقط با تنظیمِ روشنِ همین دستگاه (`PayFixPref`) و مجوزِ `sales.pay_fix` جز «ممنوع»
  val payFix = remember { ir.vil3ntec.tohid.data.PayFixPref.on(roleContext) && ir.vil3ntec.tohid.data.PayFixPref.decision(roleContext) != "deny" }
  //  POS-40: نشانِ «اصلاح‌شده» در ستونِ «نوع» (از هر دستگاه؛ دفترِ بی اصلاح ⇒ هیچ)
  val fixedSales = remember(d) { ir.vil3ntec.tohid.data.PayFixFlow.fixedSales(d) }
  //  RPT-09 — «سابقهٔ ضدتقلب» (`FraudTrailPref`، پیش‌فرض خاموش): نامِ همین کاربر روی لغو و مرجوعی؛ خاموش ⇒ null
  val trailBy = remember { ir.vil3ntec.tohid.data.FraudTrailPref.by(roleContext) }
  //  RET (v3 §۶.۸) — «برگشت و تعویضِ تازه»؛ خاموش ⇒ همان پنجرهٔ مرجوعیِ امروز
  val returnsShot = ReturnsShot.open.value
  val returnsV2 = remember { ir.vil3ntec.tohid.data.ReturnSettings.enabled(roleContext) } || returnsShot != null
  var noReceipt by remember { mutableStateOf(false) }
  var receiptFor by remember { mutableStateOf<String?>(null) }
  var scanReceipt by remember { mutableStateOf(false) }
  //  عکسِ CI (`DevShot`): فرمِ برگشتِ تازه‌ترین فاکتورِ زنده، یا برگشتِ بی رسید
  LaunchedEffect(returnsShot) {
    when (returnsShot) {
      "first" -> {
        noReceipt = false
        val left = ir.vil3ntec.tohid.data.ReturnLeft.Index(d)
        returnFor = d.sales.filter { left.canReturn(it) }.maxByOrNull { it.createdAt }
      }
      "none" -> { returnFor = null; noReceipt = true }
      "close" -> { returnFor = null; noReceipt = false }
    }
  }
  //  نشانِ همین دکان در QRِ فاکتورِ نسخهٔ ۲ (`ReceiptTokens.shopTag`)
  val myReceiptTag = remember { ir.vil3ntec.tohid.core.receipt.ReceiptTokens.shopTag(ir.vil3ntec.tohid.data.LedgerOwner.currentShop(roleContext)) }
  fun toast(text: String) {
    scope.launch { snackbar.showSnackbar(text) }
  }

  fun apply(result: SalesEngine.Result, done: String, after: () -> Unit = {}) {
    when (result) {
      is SalesEngine.Result.Failed -> toast(result.message)
      is SalesEngine.Result.Ok -> {
        scope.launch { store.save(d, result.data) }
        toast(done)
        after()
      }
    }
  }

  //  ماه‌هایی که واقعاً فاکتور دارند، تازه‌ترین اول
  val months = remember(d) {
    listOf(ALL_MONTHS) + d.sales.map { it.date.take(7) }.filter { it.length == 7 }.distinct().sortedDescending()
  }

  val term = search.trim()

  /*
   *  فهرست، **یک بار** به ازای هر دفتر و هر فیلتر.
   *
   *  تا دیروز این مرتب‌سازی بیرونِ `remember` بود و در هر بار کشیده شدنِ
   *  صفحه از نو انجام می‌شد — روی همهٔ فاکتورهای تاریخِ دکان.
   */
  val shown = remember(d, filter, term, month) {
    //  RET-01 — QRِ رسید (اسکنر یا دوربین) همان فاکتور را پیدا می‌کند
    if (returnsV2 && term.startsWith(ir.vil3ntec.tohid.data.ReturnFlow.CODE_PREFIX.take(3))) {
      return@remember ir.vil3ntec.tohid.data.ReturnFlow.find(d, term)
    }
    //  فاکتورِ نسخهٔ ۲: نشانیِ QRِ رسید، فقط فروشِ همین دکان
    if (returnsV2 && ir.vil3ntec.tohid.core.receipt.ReceiptToken.parse(term) != null) {
      return@remember ir.vil3ntec.tohid.data.ReturnFlow.find(d, term, shopTag = myReceiptTag)
    }
    val debtorNames = d.debtors.associate { it.id to it.name }
    d.sales
      .filter {
        when (filter) {
          //  PAY (v3 A4): گروه از `payments` اگر هست — کارت و کیف پول و حواله «نقدی» نیستند
          "cash", "credit", ir.vil3ntec.tohid.data.PayFlow.NON_CASH -> it.status != "cancelled" && ir.vil3ntec.tohid.data.PayFlow.payGroup(it) == filter
          "cancelled" -> it.status == "cancelled"
          else -> true
        }
      }
      .filter { month == ALL_MONTHS || it.date.startsWith(month) }
      .filter { sale ->
        if (term.isBlank()) return@filter true
        //  شمارهٔ فاکتور، یا نامِ مشتری
        val number = sale.invoiceLabel().orEmpty()
        number.contains(term, ignoreCase = true) ||
          debtorNames[sale.debtorId].orEmpty().contains(term, ignoreCase = true)
      }
      .sortedByDescending { it.createdAt }
  }

  val active = remember(d) { d.sales.filter { it.status != "cancelled" } }
  //  صندوق — باز یا بسته، و پنجرهٔ باز/بستن
  var shiftDialog by remember { mutableStateOf(false) }
  var shiftHistory by remember { mutableStateOf(false) }
  val me = remember { runCatching { ir.vil3ntec.tohid.sync.SyncStore(roleContext).accountName }.getOrDefault("") }
  val shift = remember(d) { ir.vil3ntec.tohid.data.CashShift.current(d) }
  //  PAY (v3 A4): صندوقِ شیفت با هستهٔ pay-core — فقط با پرچم (`ShiftPanelV2`)؛ خاموش ⇒ کارتِ امروز
  val payV2 = remember { ir.vil3ntec.tohid.data.ShopPolicy.payV2(roleContext) }
  val safeBook = remember { ir.vil3ntec.tohid.data.SafeBookPref.on(roleContext) } // PAY-18: گاوصندوق، پیش‌فرض خاموش ⇒ همان صفحهٔ امروز

  LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
    item {
      Text(
        StrNav.salesHistorySub,
        style = MaterialTheme.typography.bodySmall,
        color = Shop.colors.muted,
      )
      Spacer(Modifier.height(14.dp))
    }

    item {
      StatTile(
        label = StrInvoices.salesTotal,
        value = "${money(active.sumOf { it.finalTotal })} ؋",
        hint = StrInvoices.invoicesHint(plain(active.size), plain(d.sales.size - active.size)),
        modifier = Modifier.fillMaxWidth(),
      )
      Spacer(Modifier.height(12.dp))
    }

    if (payV2) item {
      ShiftPanelV2(d = d, store = store, me = me, toast = ::toast)
      Spacer(Modifier.height(12.dp))
    } else item {
      Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Column(Modifier.weight(1f)) {
            Text(
              if (shift == null) StrInvoices.registerClosed else StrInvoices.registerOpenSince(ir.vil3ntec.tohid.formatMillis(shift.open.createdAt)),
              style = MaterialTheme.typography.titleSmall,
              color = Shop.colors.text,
            )
            if (shift != null) {
              val t = ir.vil3ntec.tohid.data.CashShift.totalsOf(d, shift, System.currentTimeMillis())
              Text(
                StrInvoices.expectedInDrawer(money(t.expected)),
                style = MaterialTheme.typography.labelSmall,
                color = Shop.colors.muted,
              )
            }
          }
          TextButton(onClick = { shiftHistory = true }) { Text(StrInvoices.history) }
          Button(onClick = { shiftDialog = true }) { Text(if (shift == null) StrSettings.open else StrInvoices.closeRegister) }
        }
      }
      Spacer(Modifier.height(12.dp))
    }
    if (safeBook) item { SafePanel(d = d, store = store, me = me, toast = ::toast) }

    item {
      OutlinedTextField(
        value = search,
        onValueChange = { search = ir.vil3ntec.tohid.latinDigits(it) },
        placeholder = { Text(StrInvoices.searchHint) },
        singleLine = true,
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = Shop.colors.muted) },
        trailingIcon = {
          if (search.isNotEmpty()) {
            IconButton(onClick = { search = "" }) {
              Icon(Icons.Filled.Close, contentDescription = StrInvoices.clearSearch, tint = Shop.colors.muted)
            }
          } else if (returnsV2 && !ir.vil3ntec.tohid.ui.Platform.DESKTOP) {
            //  گوشی: QRِ رسید با دوربین؛ کامپیوتر: اسکنرِ دستی همین کادر را پر می‌کند
            ScanIconButton(onClick = { scanReceipt = true })
          }
        },
        modifier = Modifier.fillMaxWidth(),
      )
      if (returnsV2) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            StrInvoices.returnHint,
            style = MaterialTheme.typography.labelSmall,
            color = Shop.colors.muted,
            modifier = Modifier.weight(1f),
          )
          OutlinedButton(onClick = { noReceipt = true }) { Text(StrPos.returnNoReceipt) }
        }
      }
      Spacer(Modifier.height(10.dp))
    }

    item {
      Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        listOf("all" to Str.all, "cash" to Str.cashSale, "credit" to Str.credit, ir.vil3ntec.tohid.data.PayFlow.NON_CASH to StrInvoices.nonCash, "cancelled" to StrSale.cancelled).forEach { (id, label) ->
          FilterChip(selected = filter == id, onClick = { filter = id }, label = { Text(label) })
        }
      }
      Spacer(Modifier.height(8.dp))
      Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        months.forEach { m ->
          FilterChip(
            selected = month == m,
            onClick = { month = m },
            label = { Text(if (m == ALL_MONTHS) StrInvoices.allMonths else ir.vil3ntec.tohid.formatMonth(m)) },
          )
        }
      }
      Spacer(Modifier.height(14.dp))
    }

    if (shown.isEmpty()) {
      item { Panel { EmptyNote(StrInvoices.noMatch) } }
    } else {
      /*
       *  جدولِ فاکتورها.
       *
       *  یک سرستون در بالا و بعد ردیف‌ها — نه یک کارت برای هر فاکتور.
       *  کارت‌ها هر کدام سرستونِ خودشان را داشتند و برای مقایسهٔ دو
       *  فاکتور باید متن خوانده می‌شد. با ستونِ ثابت، چشم از بالا به
       *  پایین می‌رود و خودش مقایسه می‌کند.
       */
      item {
        Row(
          Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = Radius.sm, topEnd = Radius.sm))
            .background(Shop.colors.surface2)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
          InvoiceCell(StrInvoices.colNumber, 0.7f, head = true)
          InvoiceCell(StrInvoices.colDate, 1.1f, head = true)
          InvoiceCell(StrInvoices.colName, 1.3f, head = true)
          InvoiceCell(StrInvoices.colKind, 0.8f, head = true)
          InvoiceCell(StrPos.amount, 1.2f, head = true, end = true)
          Spacer(Modifier.width(28.dp))
        }
      }

      itemsIndexed(shown, key = { _, s -> s.id }) { index, sale ->
        SaleRow(
          d = d,
          sale = sale,
          striped = index % 2 == 1,
          last = index == shown.lastIndex,
          onInvoice = { invoiceFor = sale },
          onReturn = { returnFor = sale },
          returnsV2 = returnsV2,
          onReturnReceipt = { receiptFor = it },
          //  لغو و «ویرایش در سبد» (که خودش لغو است) فقط با صاحب و مدیر —
          //  سرور هم opِ لغوِ شاگرد را رد می‌کند (`sales.cancel`)
          onCancel = { if (canCancel) cancelFor = sale else if (askCancel) cancelAsk = sale else toast(StrInvoices.cancelDenied) },
          onReopen = { if (canCancel) reopenFor = sale else toast(StrInvoices.cancelDenied) },
          onPayFix = if (payFix) ({ payFixFor = sale }) else null,
          fixed = sale.id in fixedSales,
        )
      }
    }
  }

  /* ---------------------------- پنجره‌ها ---------------------------- */

  if (shiftDialog) {
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val t = shift?.let { ir.vil3ntec.tohid.data.CashShift.totalsOf(d, it, System.currentTimeMillis()) }
    AlertDialog(
      onDismissRequest = { shiftDialog = false },
      containerColor = Shop.colors.surfaceSolid,
      title = { Text(if (shift == null) StrInvoices.openRegisterTitle else StrInvoices.closeRegisterTitle, color = Shop.colors.text) },
      text = {
        Column {
          if (t != null) {
            (listOf(
              StrInvoices.openingFloat to t.opening, StrInvoices.cashSales to t.sales, StrInvoices.debtorReceipts to t.receipts,
              StrInvoices.returns to -t.returns, Str.expenses to -t.expenses, StrInvoices.supplierPayments to -t.suppliers,
            ) + listOfNotNull(
              //  PAY: ورود/خروجِ نقدِ صندوقِ pay v2 — داده است، با پرچمِ خاموش هم شمرده می‌شود
              (StrInvoices.cashIn to t.cashIn).takeIf { t.cashIn > 0 },
              (StrInvoices.cashOut to -t.cashOut).takeIf { t.cashOut > 0 },
            )).forEach { (label, v) ->
              Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
                Text("${if (v < 0) "−" else ""}${money(kotlin.math.abs(v))}", style = MaterialTheme.typography.bodySmall, color = Shop.colors.text)
              }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
              Text(StrInvoices.expected, style = MaterialTheme.typography.titleSmall, color = Shop.colors.text)
              Text("${money(t.expected)} ؋", style = MaterialTheme.typography.titleSmall, color = Shop.colors.text)
            }
            //  «ثبت از اسکنر» (کارتِ فرمان) در همین صندوق
            shift?.let { ir.vil3ntec.tohid.ui.v3.sales.drawer.DrawerModel.scannerLine(d, it.open.createdAt, Long.MAX_VALUE, seesMoney = true) }
              ?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted) }
          }
          OutlinedTextField(
            value = amount,
            onValueChange = { amount = ir.vil3ntec.tohid.latinDigits(it) },
            label = { Text(if (shift == null) StrInvoices.openingCash else StrInvoices.countedCash) },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
              keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
            ),
            modifier = Modifier.fillMaxWidth(),
          )
          if (shift != null) {
            val counted = amount.toDoubleOrNull()
            if (counted != null && t != null) {
              val diff = counted - t.expected
              Text(
                when {
                  kotlin.math.abs(diff) < 0.5 -> StrInvoices.noDifference
                  diff > 0 -> StrInvoices.overBy(money(diff))
                  else -> StrInvoices.shortBy(money(-diff))
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (kotlin.math.abs(diff) < 0.5) Shop.colors.success else Shop.colors.danger,
              )
            }
            OutlinedTextField(
              value = note, onValueChange = { note = it },
              label = { Text(StrInvoices.noteOptional) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
          }
        }
      },
      confirmButton = {
        TextButton(onClick = {
          val v = amount.toDoubleOrNull() ?: Double.NaN
          val r = if (shift == null) {
            ir.vil3ntec.tohid.data.CashShift.open(d, v, me, ir.vil3ntec.tohid.todayIso(), System.currentTimeMillis(), ::newId)
          } else {
            ir.vil3ntec.tohid.data.CashShift.close(d, v, note, me, ir.vil3ntec.tohid.todayIso(), System.currentTimeMillis(), ::newId)
          }
          when (r) {
            is ir.vil3ntec.tohid.data.CashShift.Result.Failed -> toast(r.message)
            is ir.vil3ntec.tohid.data.CashShift.Result.Ok -> {
              scope.launch { store.save(d, r.data) }
              toast(if (shift == null) StrInvoices.registerOpened else StrInvoices.registerClosedNow)
              shiftDialog = false
            }
          }
        }) { Text(if (shift == null) StrInvoices.openIt else StrInvoices.closeIt) }
      },
      dismissButton = { TextButton(onClick = { shiftDialog = false }) { Text(Str.cancel) } },
    )
  }

  if (shiftHistory) {
    val shifts = remember(d) { ir.vil3ntec.tohid.data.CashShift.list(d).take(60) }
    AlertDialog(
      onDismissRequest = { shiftHistory = false },
      containerColor = Shop.colors.surfaceSolid,
      title = { Text(StrInvoices.registerHistory, color = Shop.colors.text) },
      text = {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
          if (shifts.isEmpty()) Text(StrInvoices.noShiftsYet, color = Shop.colors.muted)
          shifts.forEach { sh ->
            Text(
              ir.vil3ntec.tohid.formatMillis(sh.open.createdAt) + " — " + sh.open.notes,
              style = MaterialTheme.typography.bodySmall, color = Shop.colors.text,
            )
            Text(
              sh.close?.let { ir.vil3ntec.tohid.formatMillis(it.createdAt) + " — " + it.notes } ?: StrInvoices.stillOpen,
              style = MaterialTheme.typography.labelSmall,
              color = if (sh.close == null) Shop.colors.warning else Shop.colors.muted,
            )
            HorizontalDivider(Modifier.padding(vertical = 6.dp), color = Shop.colors.border)
          }
        }
      },
      confirmButton = { TextButton(onClick = { shiftHistory = false }) { Text(Str.close) } },
    )
  }

  invoiceFor?.let { sale ->
    InvoiceDialog(d = d, sale = sale, onDismiss = { invoiceFor = null }, onMessage = ::toast)
  }

  //  RET (تنظیم روشن): فرمِ تازه و رسیدِ برگشت؛ خاموش ⇒ پایین، همان پنجرهٔ امروز
  if (returnsV2) {
    returnFor?.let { sale ->
      ReturnFlowDialog(
        d = d, sale = sale, store = store,
        onDismiss = { returnFor = null },
        onPosted = { id, note -> returnFor = null; toast(note); receiptFor = id },
        prefill = returnsShot == "first",
      )
    }
    if (noReceipt) {
      ReturnFlowDialog(
        d = d, sale = null, store = store,
        onDismiss = { noReceipt = false },
        onPosted = { id, note -> noReceipt = false; toast(note); receiptFor = id },
      )
    }
    receiptFor?.let { id -> ReturnReceiptDialog(d = d, docId = id, onDismiss = { receiptFor = null }, onMessage = ::toast) }
    if (scanReceipt) {
      BarcodeScanSheet(
        onDismiss = { scanReceipt = false },
        onCode = { code ->
          scanReceipt = false
          if (ir.vil3ntec.tohid.data.ReturnFlow.isForeignReceipt(code, myReceiptTag)) toast(ir.vil3ntec.tohid.data.ReturnFlow.FOREIGN_RECEIPT)
          search = code
          //  یک فاکتورِ زنده ⇒ همان لحظه فرمِ برگشت
          ir.vil3ntec.tohid.data.ReturnFlow.find(d, code, shopTag = myReceiptTag).singleOrNull()?.takeIf { it.status != "cancelled" }?.let { returnFor = it }
        },
        title = StrInvoices.scanReceiptQr,
        qr = true,
      )
    }
  }

  if (!returnsV2) returnFor?.let { sale ->
    ReturnDialog(
      d = d,
      sale = sale,
      onDismiss = { returnFor = null },
      onConfirm = { quantities, reason ->
        apply(
          SalesEngine.recordReturn(d, sale.id, quantities, reason, todayIso(), System.currentTimeMillis(), ::newId, by = trailBy),
          StrInvoices.returnRecorded,
        ) { returnFor = null }
      },
    )
  }

  /*
   *  SEC-03 — لغو و «اصلاح»: کنترل از `InvoiceVoid.check` (core؛ همان که `CancelSheet` ِ نسخهٔ ۳ می‌خواند)،
   *  اثرِ دفتری همان `SalesEngine.cancel`، و فقط پس از نشستنِ لغو در دفتر (نه با قفلِ نرم) سندِ معکوس در
   *  زنجیرهٔ اسنادِ دستگاه (`DocChainSync.recordVoid`؛ پرچمِ خاموشِ `shop.doc_chain` ⇒ هیچ). همان `SalesVoid.run` ِ نسخهٔ ۳:
   *  STF-02 برگهٔ لغو ([slip]) جای «اجازه» و روی `Sale.cancelApproval`؛ PRM-02 پس از لغو کدهای فاکتور آزاد (`PromoLive.releaseOnVoid`).
   */
  fun voidSale(sale: Sale, kind: InvoiceVoid.Kind, reason: String, done: String, slip: ir.vil3ntec.tohid.core.perm.ApprovalSlip.Slip? = null, after: () -> Unit) {
    val before = d
    when (val r = SalesVoid.run(before, sale.id, canCancel, kind, reason, todayIso(), System.currentTimeMillis(), ::newId, trailBy, StrInvoices.cancelDenied, slip?.let(ir.vil3ntec.tohid.data.DiscountGate::json))) {
      is SalesVoid.Outcome.Denied -> toast(r.message)
      is SalesVoid.Outcome.Failed -> toast(r.message)
      is SalesVoid.Outcome.Ok -> {
        scope.launch { if (store.save(before, r.data)) ir.vil3ntec.tohid.sync.platform.DocChainSync.recordVoid(roleContext, before, sale.id, kind, r.reason) }
        //  PRM-02: کدهای سقف‌دارِ فاکتور پس از نشستنِ لغو در دفتر آزاد می‌شوند (بی اینترنت ⇒ صفِ همین دستگاه)
        ir.vil3ntec.tohid.ui.PromoLive.releaseOnVoid(roleContext, store, sale)
        toast(done)
        after()
      }
    }
  }

  //  اصلاحِ فاکتور: لغو (سندِ معکوس) و بردنِ اقلام با همان قیمت به سبد — شرح سرِ `ReopenInvoiceDialog`
  reopenFor?.let { sale ->
    ReopenInvoiceDialog(sale, onDismiss = { reopenFor = null }) {
      //  همان راهِ گوشی (`SaleReopen`): نشان‌های ردیف، تخفیف، یادداشت و لیستِ فاکتور هم — پیش‌تر می‌افتادند
      val lines = ir.vil3ntec.tohid.data.SaleReopen.lines(d, sale, ::newId, ir.vil3ntec.tohid.todayIso())
      voidSale(sale, InvoiceVoid.Kind.CORRECTION, InvoiceVoid.CORRECTION_REASON, StrInvoices.reopenedToCart) {
        cartStore.set(lines)
        cartStore.setDebtor(sale.debtorId)
        cartStore.setNote(ir.vil3ntec.tohid.data.SaleReopen.note(sale))
        if (ir.vil3ntec.tohid.ui.components.pos.PriceListPrefs.on(roleContext)) {
          cartStore.setPriceList(ir.vil3ntec.tohid.data.CartPriceList.keep(d, lines, sale.debtorId?.let { id -> d.debtors.find { it.id == id }?.priceList.orEmpty() }))
        }
        //  تخفیفِ فاکتور به صندوقِ کامپیوتر (`DeskSale` همان را برای همین سبد برمی‌دارد)
        ir.vil3ntec.tohid.ui.v3.sales.cart.HeldSession.keep(cartStore, ir.vil3ntec.tohid.data.SaleReopen.extras(d, sale, lines))
        reopenFor = null
        onEditInCart()
      }
    }
  }

  payFixFor?.let { sale -> PayFixHost(d, store, sale, me, ::toast) { payFixFor = null } }
  cancelAsk?.let { sale ->
    CancelApprovalDialog(sale, remember { ir.vil3ntec.tohid.data.ShopRole.shopId(roleContext) }, remember { ir.vil3ntec.tohid.data.TillUser.meId(roleContext) },
      onApproved = { cancelAsk = null; cancelSlip = it; cancelFor = sale }) { cancelAsk = null }
  }
  cancelFor?.let { sale ->
    CancelInvoiceDialog(sale, onDismiss = { cancelFor = null; cancelSlip = null }) { reason ->
      voidSale(sale, InvoiceVoid.Kind.VOID, reason, StrInvoices.saleCancelled, cancelSlip) { cancelFor = null; cancelSlip = null }
    }
  }
}

/* ============================ تکه‌ها ============================ */

/**
 *  یک ردیفِ جدول.
 *
 *  زدنِ ردیف، فاکتور را باز می‌کند. مرجوعی و لغو زیرِ دکمهٔ سه‌نقطهٔ
 *  آخرِ ردیف‌اند: در یک جدول، سه دکمهٔ کنارِ هر ردیف همان شلوغی‌ای را
 *  برمی‌گرداند که جدول برای رفعش آمده بود.
 */
@Composable
private fun SaleRow(
  d: ShopData,
  sale: Sale,
  striped: Boolean,
  last: Boolean,
  onInvoice: () -> Unit,
  onReturn: () -> Unit,
  onCancel: () -> Unit,
  onReopen: () -> Unit,
  returnsV2: Boolean = false,
  onReturnReceipt: (String) -> Unit = {},
  onPayFix: (() -> Unit)? = null,
  /** POS-40: روشِ پرداختِ این فاکتور اصلاح شد ⇒ نشان در ستونِ «نوع» */
  fixed: Boolean = false,
) {
  val cancelled = sale.status == "cancelled"
  //  RET-12 — سندهای برگشتِ تازهٔ همین فاکتور، برای چاپِ دوبارهٔ رسید
  val returnDocs = if (returnsV2) d.saleReturns.filter { it.saleId == sale.id }.mapNotNull { it.docId }.distinct() else emptyList()
  //  ORD-20: فاکتورِ سریِ W را سرور هنگامِ تحویلِ سفارشِ اینترنتی ساخته — لغو و «اصلاح» ندارد
  val online = sale.isOnlineOrder()
  val debtor = sale.debtorId?.let { id -> d.debtors.find { it.id == id } }
  //  همان حسابِ پنجرهٔ برگشت (`ReturnLeft`، برگشتِ دستگاه‌های دیگر هم) — نه شمارندهٔ تنها
  val canReturn = ir.vil3ntec.tohid.data.ReturnLeft.canReturn(d, sale)
  var menu by remember { mutableStateOf(false) }

  val kindTint = when {
    cancelled -> Shop.colors.danger
    online -> Shop.colors.primary
    ir.vil3ntec.tohid.data.PayFlow.payGroup(sale) == "credit" -> Shop.colors.warning
    else -> Shop.colors.success
  }

  Column {
    Row(
      Modifier
        .fillMaxWidth()
        .then(
          if (last) Modifier.clip(RoundedCornerShape(bottomStart = Radius.sm, bottomEnd = Radius.sm))
          else Modifier
        )
        // ردیفِ یکی‌درمیانِ کم‌رنگ: چشم روی جدولِ بلند خط را گم نمی‌کند
        .background(if (striped) Shop.colors.surface2.copy(alpha = 0.45f) else Shop.colors.surface)
        //  کلیکِ راست (کامپیوتر) یا لمسِ طولانی (گوشی) ⇒ همان منوی کارها
        .pressMenu(onClick = onInvoice, onMenu = { menu = true })
        .padding(horizontal = 12.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      InvoiceCell(sale.invoiceLabel() ?: "0", 0.7f, bold = true)
      InvoiceCell(displayDate(sale.date), 1.1f)
      InvoiceCell(debtor?.name ?: Str.cashSale, 1.3f)
      InvoiceCell(
        when {
          cancelled -> StrSale.cancelled
          online -> StrInvoices.online
          else -> ir.vil3ntec.tohid.data.PayFixFlow.kindLabel(ir.vil3ntec.tohid.data.PayFlow.payLabel(sale, cash = Str.cashSale), fixed)
        },
        0.8f,
        tint = kindTint,
      )
      InvoiceCell(
        money(sale.finalTotal),
        1.2f,
        end = true,
        bold = true,
        tint = if (cancelled) Shop.colors.muted else Shop.colors.text,
      )
      Box {
        IconButton(onClick = { menu = true }, modifier = Modifier.size(28.dp)) {
          Icon(
            Icons.Filled.MoreVert,
            contentDescription = StrInvoices.actions,
            tint = Shop.colors.muted2,
            modifier = Modifier.size(17.dp),
          )
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
          DropdownMenuItem(text = { Text(StrInvoices.viewInvoice) }, onClick = { menu = false; onInvoice() })
          if (canReturn) {
            DropdownMenuItem(text = { Text(if (returnsV2) StrInvoices.returnOrExchange else StrInvoices.returnItems) }, onClick = { menu = false; onReturn() })
          }
          returnDocs.forEach { id ->
            DropdownMenuItem(
              text = { Text(StrInvoices.returnReceiptOf(ir.vil3ntec.tohid.data.ReturnFlow.label(id))) },
              onClick = { menu = false; onReturnReceipt(id) },
            )
          }
          if (!cancelled && !online) {
            onPayFix?.let { f -> DropdownMenuItem(text = { Text(ir.vil3ntec.tohid.core.i18n.StrPayFix.menu) }, onClick = { menu = false; f() }) }
            DropdownMenuItem(text = { Text(StrInvoices.correctInvoice) }, onClick = { menu = false; onReopen() })
            DropdownMenuItem(
              text = { Text(StrInvoices.cancelSale, color = Shop.colors.danger) },
              onClick = { menu = false; onCancel() },
            )
          }
        }
      }
    }
    if (!last) HorizontalDivider(color = Shop.colors.fieldBorder.copy(alpha = 0.35f))
  }
}

@Composable
private fun ReturnDialog(
  d: ShopData,
  sale: Sale,
  onDismiss: () -> Unit,
  onConfirm: (Map<String, Double>, String) -> Unit,
) {
  //  همان ماندهٔ دکمه و ثبت (`ReturnLeft`: برگشتِ دستگاه‌های دیگر هم، JB5) — نه شمارندهٔ تنها
  val left = remember(d, sale) { d.saleItems.filter { it.saleId == sale.id }.associate { it.id to ir.vil3ntec.tohid.data.ReturnLeft.left(d, it) } }
  val items = d.saleItems.filter { it.saleId == sale.id && (left[it.id] ?: 0.0) > 0 }
  var quantities by remember { mutableStateOf(mapOf<String, String>()) }
  var reason by remember { mutableStateOf("") }

  val total = items.sumOf { item ->
    val wanted = (quantities[item.id]?.toDoubleOrNull() ?: 0.0).coerceIn(0.0, left[item.id] ?: 0.0)
    SalesEngine.returnAmount(d, sale, item, wanted)   //  همان مبلغی که ثبت می‌شود (سهمِ تخفیف، گردِ تجمعی)
  }

  Dialog(onDismissRequest = onDismiss) {
    DialogEntry {
    Surface(color = Shop.colors.surfaceSolid, shape = RoundedCornerShape(Radius.lg), modifier = Modifier.fillMaxWidth()) {
      Column(Modifier.padding(18.dp).verticalScroll(rememberScrollState())) {
        Text(StrInvoices.returnTitle(sale.invoiceLabel() ?: "0"),
          style = MaterialTheme.typography.titleMedium, color = Shop.colors.text)
        Spacer(Modifier.height(12.dp))

        if (items.isEmpty()) {
          EmptyNote(StrInvoices.nothingToReturn)
        } else {
          items.forEach { item ->
            val product = d.products.find { it.id == item.productId }
            val allowed = left[item.id] ?: 0.0
            Column(Modifier.padding(bottom = 12.dp)) {
              Text(
                StrInvoices.returnable(SalesEngine.itemName(d, item), qty(allowed)) + (product?.unit?.takeIf { it.isNotBlank() }?.let { " $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = Shop.colors.muted,
              )
              Spacer(Modifier.height(6.dp))
              NumberField(
                value = quantities[item.id].orEmpty(),
                onValueChange = { quantities = quantities + (item.id to it) },
                label = StrInvoices.returnQty,
                modifier = Modifier.fillMaxWidth(),
              )
            }
          }

          if (total > 0) {
            Panel {
              Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(StrInvoices.returnAmount, style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
                Text("${money(total)} ؋", style = MaterialTheme.typography.titleSmall, color = Shop.colors.text)
              }
            }
            Spacer(Modifier.height(10.dp))
          }

          OutlinedTextField(
            value = reason,
            onValueChange = { reason = it },
            label = { Text(StrInvoices.reasonOptional) },
            modifier = Modifier.fillMaxWidth(),
          )
        }

        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text(Str.cancel) }
          Button(
            enabled = items.isNotEmpty(),
            onClick = {
              onConfirm(
                quantities.mapNotNull { (id, text) ->
                  text.toDoubleOrNull()?.takeIf { it > 0 }?.let { id to it }
                }.toMap(),
                reason.trim(),
              )
            },
            modifier = Modifier.weight(1f),
          ) { Text(StrInvoices.recordReturn) }
        }
      }
    }
    }
  }
}

/**
 *  یک خانهٔ جدولِ فاکتور.
 *
 *  پهنای هر ستون با `weight` است نه با عددِ ثابت: نامِ بلند ستونِ کناری
 *  را هل نمی‌دهد و ردیف‌ها روی هر پهنای صفحه هم‌تراز می‌مانند.
 */

@Composable
private fun RowScope.InvoiceCell(
  text: String,
  weight: Float,
  head: Boolean = false,
  end: Boolean = false,
  tint: Color? = null,
  bold: Boolean = false,
) {
  Text(
    text,
    style = if (head) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
    color = tint ?: if (head) Shop.colors.muted2 else Shop.colors.text,
    fontWeight = if (bold) FontWeight.Bold else null,
    maxLines = 1,
    overflow = TextOverflow.Ellipsis,
    textAlign = if (end) TextAlign.End else TextAlign.Start,
    modifier = Modifier.weight(weight).padding(end = 6.dp),
  )
}

/** «همهٔ ماه‌ها» — کلیدی که هیچ ماهی نمی‌تواند باشد */
private const val ALL_MONTHS = "*"
