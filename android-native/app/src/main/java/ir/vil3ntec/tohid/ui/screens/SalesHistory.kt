package ir.vil3ntec.tohid.ui.screens

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
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.formatDate
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.plain
import ir.vil3ntec.tohid.qty
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.theme.Radius
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.launch

/**
 *  تاریخچهٔ فروش.
 *
 *  هر فاکتوری که ثبت شده اینجاست — از جمله لغوشده‌ها. فاکتور هیچ‌وقت پاک
 *  نمی‌شود، فقط لغو می‌شود؛ فروشی که افتاده حقیقتی است که در گزارشِ سود و
 *  حسابِ قرض‌دار اثر گذاشته، و برداشتنش آن عددها را عوض می‌کند.
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

  val roleContext = androidx.compose.ui.platform.LocalContext.current
  val canCancel = remember { ir.vil3ntec.tohid.data.ShopRole.canCancelSale(roleContext) }
  fun toast(text: String) {
    scope.launch { snackbar.showSnackbar(text) }
  }

  fun apply(result: SalesEngine.Result, done: String, after: () -> Unit = {}) {
    when (result) {
      is SalesEngine.Result.Failed -> toast(result.message)
      is SalesEngine.Result.Ok -> {
        scope.launch { store.save(result.data) }
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
    val debtorNames = d.debtors.associate { it.id to it.name }
    d.sales
      .filter {
        when (filter) {
          "cash" -> it.status != "cancelled" && it.paymentMethod == "cash"
          "credit" -> it.status != "cancelled" && it.paymentMethod == "credit"
          "cancelled" -> it.status == "cancelled"
          else -> true
        }
      }
      .filter { month == ALL_MONTHS || it.date.startsWith(month) }
      .filter { sale ->
        if (term.isBlank()) return@filter true
        //  شمارهٔ فاکتور، یا نامِ مشتری
        val number = sale.invoiceNumber?.toString().orEmpty()
        number.contains(term) ||
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

  LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
    item {
      Text(
        "فاکتورهای ثبت‌شده، مرجوعی و لغو فروش",
        style = MaterialTheme.typography.bodySmall,
        color = Shop.colors.muted,
      )
      Spacer(Modifier.height(14.dp))
    }

    item {
      StatTile(
        label = "جمع فروش",
        value = "${money(active.sumOf { it.finalTotal })} ؋",
        hint = "${plain(active.size)} فاکتور — ${plain(d.sales.size - active.size)} لغوشده",
        modifier = Modifier.fillMaxWidth(),
      )
      Spacer(Modifier.height(12.dp))
    }

    item {
      Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Column(Modifier.weight(1f)) {
            Text(
              if (shift == null) "صندوق بسته است" else "صندوق باز — از ${ir.vil3ntec.tohid.formatMillis(shift.open.createdAt)}",
              style = MaterialTheme.typography.titleSmall,
              color = Shop.colors.text,
            )
            if (shift != null) {
              val t = ir.vil3ntec.tohid.data.CashShift.totalsOf(d, shift, System.currentTimeMillis())
              Text(
                "نقدِ مورد انتظار در دخل: ${money(t.expected)} ؋",
                style = MaterialTheme.typography.labelSmall,
                color = Shop.colors.muted,
              )
            }
          }
          TextButton(onClick = { shiftHistory = true }) { Text("سابقه") }
          Button(onClick = { shiftDialog = true }) { Text(if (shift == null) "باز کردن" else "بستن صندوق") }
        }
      }
      Spacer(Modifier.height(12.dp))
    }

    item {
      OutlinedTextField(
        value = search,
        onValueChange = { search = ir.vil3ntec.tohid.latinDigits(it) },
        placeholder = { Text("شمارهٔ فاکتور یا نام مشتری") },
        singleLine = true,
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = Shop.colors.muted) },
        trailingIcon = {
          if (search.isNotEmpty()) {
            IconButton(onClick = { search = "" }) {
              Icon(Icons.Filled.Close, contentDescription = "پاک کردن جستجو", tint = Shop.colors.muted)
            }
          }
        },
        modifier = Modifier.fillMaxWidth(),
      )
      Spacer(Modifier.height(10.dp))
    }

    item {
      Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        listOf("all" to "همه", "cash" to "نقدی", "credit" to "نسیه", "cancelled" to "لغوشده").forEach { (id, label) ->
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
            label = { Text(if (m == ALL_MONTHS) "همهٔ ماه‌ها" else ir.vil3ntec.tohid.formatMonth(m)) },
          )
        }
      }
      Spacer(Modifier.height(14.dp))
    }

    if (shown.isEmpty()) {
      item { Panel { EmptyNote("فاکتوری با این جستجو یا فیلتر پیدا نشد.") } }
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
          InvoiceCell("شماره", 0.7f, head = true)
          InvoiceCell("تاریخ", 1.1f, head = true)
          InvoiceCell("نام", 1.3f, head = true)
          InvoiceCell("نوع", 0.8f, head = true)
          InvoiceCell("مبلغ", 1.2f, head = true, end = true)
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
          //  لغو و «ویرایش در سبد» (که خودش لغو است) فقط با صاحب و مدیر —
          //  سرور هم opِ لغوِ شاگرد را رد می‌کند (`sales.cancel`)
          onCancel = { if (canCancel) cancelFor = sale else toast(CANCEL_DENIED) },
          onReopen = { if (canCancel) reopenFor = sale else toast(CANCEL_DENIED) },
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
      title = { Text(if (shift == null) "باز کردنِ صندوق" else "بستنِ صندوق", color = Shop.colors.text) },
      text = {
        Column {
          if (t != null) {
            listOf(
              "اول صندوق" to t.opening, "فروش نقدی" to t.sales, "رسید قرض‌داران" to t.receipts,
              "مرجوعی" to -t.returns, "مصارف" to -t.expenses, "پرداخت به تأمین‌کننده و خرید" to -t.suppliers,
            ).forEach { (label, v) ->
              Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
                Text("${if (v < 0) "−" else ""}${money(kotlin.math.abs(v))}", style = MaterialTheme.typography.bodySmall, color = Shop.colors.text)
              }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
              Text("مورد انتظار", style = MaterialTheme.typography.titleSmall, color = Shop.colors.text)
              Text("${money(t.expected)} ؋", style = MaterialTheme.typography.titleSmall, color = Shop.colors.text)
            }
          }
          OutlinedTextField(
            value = amount,
            onValueChange = { amount = ir.vil3ntec.tohid.latinDigits(it) },
            label = { Text(if (shift == null) "پولِ اولِ صندوق" else "پولِ شمرده‌شده در دخل") },
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
                  kotlin.math.abs(diff) < 0.5 -> "بی اختلاف"
                  diff > 0 -> "اضافه: ${money(diff)} ؋"
                  else -> "کسری: ${money(-diff)} ؋"
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (kotlin.math.abs(diff) < 0.5) Shop.colors.success else Shop.colors.danger,
              )
            }
            OutlinedTextField(
              value = note, onValueChange = { note = it },
              label = { Text("یادداشت (اختیاری)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
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
              scope.launch { store.save(r.data) }
              toast(if (shift == null) "صندوق باز شد" else "صندوق بسته شد")
              shiftDialog = false
            }
          }
        }) { Text(if (shift == null) "باز کن" else "ببند") }
      },
      dismissButton = { TextButton(onClick = { shiftDialog = false }) { Text("انصراف") } },
    )
  }

  if (shiftHistory) {
    val shifts = remember(d) { ir.vil3ntec.tohid.data.CashShift.list(d).take(60) }
    AlertDialog(
      onDismissRequest = { shiftHistory = false },
      containerColor = Shop.colors.surfaceSolid,
      title = { Text("سابقهٔ صندوق", color = Shop.colors.text) },
      text = {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
          if (shifts.isEmpty()) Text("هنوز صندوقی باز نشده.", color = Shop.colors.muted)
          shifts.forEach { sh ->
            Text(
              ir.vil3ntec.tohid.formatMillis(sh.open.createdAt) + " — " + sh.open.notes,
              style = MaterialTheme.typography.bodySmall, color = Shop.colors.text,
            )
            Text(
              sh.close?.let { ir.vil3ntec.tohid.formatMillis(it.createdAt) + " — " + it.notes } ?: "هنوز باز است",
              style = MaterialTheme.typography.labelSmall,
              color = if (sh.close == null) Shop.colors.warning else Shop.colors.muted,
            )
            HorizontalDivider(Modifier.padding(vertical = 6.dp), color = Shop.colors.border)
          }
        }
      },
      confirmButton = { TextButton(onClick = { shiftHistory = false }) { Text("بستن") } },
    )
  }

  invoiceFor?.let { sale ->
    InvoiceDialog(d = d, sale = sale, onDismiss = { invoiceFor = null }, onMessage = ::toast)
  }

  returnFor?.let { sale ->
    ReturnDialog(
      d = d,
      sale = sale,
      onDismiss = { returnFor = null },
      onConfirm = { quantities, reason ->
        apply(
          SalesEngine.recordReturn(d, sale.id, quantities, reason, todayIso(), System.currentTimeMillis(), ::newId),
          "مرجوعی با موفقیت ثبت شد",
        ) { returnFor = null }
      },
    )
  }

  /*
   *  اصلاحِ فاکتور.
   *
   *  ویرایشِ مستقیمِ فاکتورِ ثبت‌شده ممکن نیست و نباید هم باشد: فاکتور
   *  سند است و شمارهٔ آن در حسابِ قرض‌دار و سابقهٔ عملیات نوشته شده.
   *
   *  ولی تا دیروز راهی هم برای اشتباهِ ساده نبود: فروشنده باید فاکتور را
   *  لغو می‌کرد و کلِ سبد را از نو می‌ساخت. حالا همان کار یک دکمه است —
   *  فاکتور لغو می‌شود (پس موجودی برمی‌گردد) و اقلامش با همان قیمتِ
   *  همان فاکتور دوباره در سبد می‌نشینند.
   */
  reopenFor?.let { sale ->
    AlertDialog(
      onDismissRequest = { reopenFor = null },
      containerColor = Shop.colors.surfaceSolid,
      title = { Text("اصلاح فاکتور؟", color = Shop.colors.text) },
      text = {
        Text(
          "فاکتور #${plain(sale.invoiceNumber ?: 0)} لغو می‌شود و اقلامش با همان قیمت به سبد خرید برمی‌گردند. " +
            "بعد از اصلاح، فروش را دوباره ثبت کنید — شمارهٔ فاکتور تازه‌ای می‌گیرد.",
          style = MaterialTheme.typography.bodySmall,
          color = Shop.colors.muted,
        )
      },
      confirmButton = {
        TextButton(onClick = {
          val lines = d.saleItems
            .filter { it.saleId == sale.id }
            .mapNotNull { item ->
              val left = item.quantity - item.returnedQty
              if (left <= 0) null
              else if (item.productId.isBlank()) SalesEngine.CartLine(
                productId = newId(),
                quantity = left,
                unitPrice = item.unitPrice,
                label = item.name.ifBlank { "قلم آزاد" },
              )
              else SalesEngine.CartLine(
                productId = item.productId,
                quantity = left,
                unitPrice = item.unitPrice,
              )
            }
          apply(
            SalesEngine.cancel(d, sale.id, todayIso(), System.currentTimeMillis(), ::newId, reason = "ویرایش در سبد"),
            "فاکتور لغو شد و اقلامش در سبد است",
          ) {
            cartStore.set(lines)
            cartStore.setDebtor(sale.debtorId)
            reopenFor = null
            onEditInCart()
          }
        }) { Text("لغو و بردن به سبد") }
      },
      dismissButton = { TextButton(onClick = { reopenFor = null }) { Text("بازگشت") } },
    )
  }

  cancelFor?.let { sale ->
    //  لغو بی دلیل ثبت نمی‌شود — دلیل در فاکتور و سابقه می‌ماند
    var reason by remember(sale.id) { mutableStateOf("") }
    AlertDialog(
      onDismissRequest = { cancelFor = null },
      containerColor = Shop.colors.surfaceSolid,
      title = { Text("لغو فروش؟", color = Shop.colors.text) },
      text = {
        Column {
          Text(
            SalesEngine.cancelWarning(sale),
            style = MaterialTheme.typography.bodySmall,
            color = Shop.colors.muted,
          )
          Spacer(Modifier.height(10.dp))
          OutlinedTextField(
            value = reason,
            onValueChange = { reason = it.take(120) },
            label = { Text("دلیلِ لغو") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
        }
      },
      confirmButton = {
        TextButton(
          enabled = reason.isNotBlank(),
          onClick = {
            apply(
              SalesEngine.cancel(d, sale.id, todayIso(), System.currentTimeMillis(), ::newId, reason = reason),
              "فروش لغو شد",
            ) { cancelFor = null }
          },
        ) { Text("لغو فروش", color = if (reason.isNotBlank()) Shop.colors.danger else Shop.colors.muted) }
      },
      dismissButton = { TextButton(onClick = { cancelFor = null }) { Text("بازگشت") } },
    )
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
) {
  val cancelled = sale.status == "cancelled"
  val debtor = sale.debtorId?.let { id -> d.debtors.find { it.id == id } }
  val canReturn = !cancelled && d.saleItems.any { it.saleId == sale.id && SalesEngine.returnable(it) > 0 }
  var menu by remember { mutableStateOf(false) }

  val kindTint = when {
    cancelled -> Shop.colors.danger
    sale.paymentMethod == "credit" -> Shop.colors.warning
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
        .clickable(onClick = onInvoice)
        .padding(horizontal = 12.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      InvoiceCell(plain(sale.invoiceNumber ?: 0), 0.7f, bold = true)
      InvoiceCell(formatDate(sale.date), 1.1f)
      InvoiceCell(debtor?.name ?: "نقدی", 1.3f)
      InvoiceCell(
        when {
          cancelled -> "لغوشده"
          sale.paymentMethod == "credit" -> "نسیه"
          else -> "نقدی"
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
            contentDescription = "کارها",
            tint = Shop.colors.muted2,
            modifier = Modifier.size(17.dp),
          )
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
          DropdownMenuItem(text = { Text("دیدن فاکتور") }, onClick = { menu = false; onInvoice() })
          if (canReturn) {
            DropdownMenuItem(text = { Text("مرجوعی") }, onClick = { menu = false; onReturn() })
          }
          if (!cancelled) {
            DropdownMenuItem(text = { Text("اصلاح فاکتور") }, onClick = { menu = false; onReopen() })
            DropdownMenuItem(
              text = { Text("لغو فروش", color = Shop.colors.danger) },
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
  val items = d.saleItems.filter { it.saleId == sale.id && SalesEngine.returnable(it) > 0 }
  var quantities by remember { mutableStateOf(mapOf<String, String>()) }
  var reason by remember { mutableStateOf("") }

  val total = items.sumOf { item ->
    val wanted = (quantities[item.id]?.toDoubleOrNull() ?: 0.0).coerceIn(0.0, SalesEngine.returnable(item))
    if (item.quantity > 0) Math.round(item.totalPrice / item.quantity * wanted).toDouble() else 0.0
  }

  Dialog(onDismissRequest = onDismiss) {
    DialogEntry {
    Surface(color = Shop.colors.surfaceSolid, shape = RoundedCornerShape(Radius.lg), modifier = Modifier.fillMaxWidth()) {
      Column(Modifier.padding(18.dp).verticalScroll(rememberScrollState())) {
        Text("مرجوعی — فاکتور #${plain(sale.invoiceNumber ?: 0)}",
          style = MaterialTheme.typography.titleMedium, color = Shop.colors.text)
        Spacer(Modifier.height(12.dp))

        if (items.isEmpty()) {
          EmptyNote("موردی برای مرجوعی وجود ندارد.")
        } else {
          items.forEach { item ->
            val product = d.products.find { it.id == item.productId }
            val allowed = SalesEngine.returnable(item)
            Column(Modifier.padding(bottom = 12.dp)) {
              Text(
                "${SalesEngine.itemName(d, item)} — قابل مرجوعی: ${qty(allowed)}${product?.unit?.takeIf { it.isNotBlank() }?.let { " $it" } ?: ""}",
                style = MaterialTheme.typography.bodySmall,
                color = Shop.colors.muted,
              )
              Spacer(Modifier.height(6.dp))
              NumberField(
                value = quantities[item.id].orEmpty(),
                onValueChange = { quantities = quantities + (item.id to it) },
                label = "مقدار مرجوعی",
                modifier = Modifier.fillMaxWidth(),
              )
            }
          }

          if (total > 0) {
            Panel {
              Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("مبلغ مرجوعی", style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
                Text("${money(total)} ؋", style = MaterialTheme.typography.titleSmall, color = Shop.colors.text)
              }
            }
            Spacer(Modifier.height(10.dp))
          }

          OutlinedTextField(
            value = reason,
            onValueChange = { reason = it },
            label = { Text("دلیل (اختیاری)") },
            modifier = Modifier.fillMaxWidth(),
          )
        }

        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("انصراف") }
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
          ) { Text("ثبت مرجوعی") }
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

private const val CANCEL_DENIED = "لغوِ فاکتور با صاحب یا مدیرِ دکان است"
