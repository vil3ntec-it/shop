package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.WarehouseEngine
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.qty
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.launch

/**
 *  محصولاتِ کامپیوتر — جدول، نه کارت.
 *
 *  خواستهٔ صاحب مخزن: «چرا هر محصول به این بدبختی می‌شه رسوند». روی
 *  گوشی هر کالا یک پنجرهٔ جدا دارد؛ روی کامپیوتر:
 *
 *  • **ردیفِ «کالای تازه» بالای جدول**: نام، بارکد، دسته، واحد، خرید،
 *    فروش، موجودیِ اول. Tab بینِ خانه‌ها، **Enter ثبت** و ردیفِ بعدی آماده
 *    با فوکوس روی نام — ده کالا پشتِ سرِ هم بی یک کلیک. بارکدخوان هم
 *    می‌تواند مستقیم در خانهٔ بارکد بخواند (Enterاش همان ثبت است).
 *  • **جدولِ همهٔ کالاها** با جست‌وجوی هم‌زمان (Ctrl+F)، مرتب‌سازی با
 *    سرستون، و ↑↓ برای جابه‌جایی.
 *  • **پنلِ ویرایش کنارِ جدول** (کلیک یا Enter روی ردیف): تغییر ⇐ Enter یا
 *    Ctrl+S ثبت؛ Esc بستن. ورودِ انبار و جزئیات هم از همان‌جا.
 *
 *  منطق همان `WarehouseEngine` است که گوشی دارد — فقط ظاهر کامپیوتری است.
 */
@Composable
fun DeskProductsScreen(
  store: ShopStore,
  d: ShopData,
  snackbar: SnackbarHostState,
  onOpenWarehouse: (String?) -> Unit,
  onOpenProduct: (String) -> Unit,
) {
  val scope = rememberCoroutineScope()
  fun toast(t: String) { scope.launch { snackbar.showSnackbar(t) } }

  var search by remember { mutableStateOf("") }
  var sortBy by remember { mutableStateOf(0) }
  var asc by remember { mutableStateOf(true) }
  var openId by remember { mutableStateOf<String?>(null) }
  val searchFocus = remember { FocusRequester() }

  //  اسکن روی همین صفحه (`ScanRouter`): کالای ثبت‌شده ⇐ پنلِ ویرایش؛ ناشناس ⇐ ردیفِ «کالای تازه» با همان بارکد
  val incoming = remember { mutableStateOf<String?>(null) }
  val latest by rememberUpdatedState(d)
  val owner = remember { Any() }
  DisposableEffect(Unit) {
    ir.vil3ntec.tohid.scan.ScanRouter.register(owner) { code ->
      val id = ShopStore.barcodeIndex(latest)[code]
      if (id != null) { openId = id; search = "" } else { incoming.value = code; toast("بارکدِ تازه — نام و قیمت را بنویسید و Enter بزنید") }
      true
    }
    onDispose { ir.vil3ntec.tohid.scan.ScanRouter.unregister(owner) }
  }

  val stockIndex = remember(d) { d.products.associate { it.id to ShopStore.stock(d, it.id) } }
  val rows = remember(d.products, search, sortBy, asc, stockIndex) {
    val q = search.trim()
    val list = d.products.filter { p ->
      q.isEmpty() || p.name.contains(q, true) || p.category.contains(q, true) || p.barcodes.any { it.contains(q) }
    }
    val sorted = when (sortBy) {
      1 -> list.sortedBy { it.barcodes.firstOrNull().orEmpty() }
      2 -> list.sortedBy { it.category }
      3 -> list.sortedBy { it.purchasePrice }
      4 -> list.sortedBy { it.salePrice }
      5 -> list.sortedBy { stockIndex[it.id] ?: 0.0 }
      else -> list.sortedBy { it.name }
    }
    if (asc) sorted else sorted.reversed()
  }

  Column(
    Modifier
      .fillMaxSize()
      .onPreviewKeyEvent { e ->
        if (e.type == KeyEventType.KeyDown && e.isCtrlPressed && e.key == Key.F) { runCatching { searchFocus.requestFocus() }; true } else false
      }
      .padding(12.dp),
  ) {
    /* ─────────── نوارِ بالا ─────────── */
    Row(verticalAlignment = Alignment.CenterVertically) {
      OutlinedTextField(
        value = search,
        onValueChange = { search = it },
        singleLine = true,
        placeholder = { Text("جست‌وجو در نام، دسته یا بارکد (Ctrl+F)") },
        modifier = Modifier.weight(1f).focusRequester(searchFocus),
      )
      Spacer(Modifier.width(10.dp))
      Text(
        "${d.products.size} کالا · ${d.products.count { (stockIndex[it.id] ?: 0.0) <= it.minStock && it.active }} زیرِ حدِ موجودی",
        style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted,
      )
      Spacer(Modifier.width(10.dp))
      OutlinedButton(onClick = { onOpenWarehouse(null) }) { Text("ورودِ انبار") }
      Spacer(Modifier.width(6.dp))
      val context = LocalContext.current
      OutlinedButton(onClick = {
        val sheet = ir.vil3ntec.tohid.data.ReportCsv.of("products", d, "", "", ir.vil3ntec.tohid.todayIso())
        ir.vil3ntec.tohid.ui.Platform.saveFile("${sheet.name}.xlsx", ir.vil3ntec.tohid.data.Xlsx.of(sheet))?.let { toast("ذخیره شد: $it") }
      }) { Text("اکسل") }
      Spacer(Modifier.width(6.dp))
      OutlinedButton(onClick = {
        val sheet = ir.vil3ntec.tohid.data.ReportCsv.of("products", d, "", "", ir.vil3ntec.tohid.todayIso())
        ir.vil3ntec.tohid.print.ReportPrint.print(context, sheet, sheet.name)
      }) { Text("چاپ") }
    }
    Spacer(Modifier.height(10.dp))

    Row(Modifier.weight(1f)) {
      Column(Modifier.weight(1f).fillMaxHeight()) {
        QuickAddRow(store, d, ::toast, incoming)
        Spacer(Modifier.height(8.dp))

        /* ─────────── جدول ─────────── */
        val cols = listOf("نام" to 0.dp, "بارکد" to 150.dp, "دسته" to 130.dp, "خرید" to 100.dp, "فروش" to 100.dp, "موجودی" to 110.dp)
        Row(
          Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)).background(Shop.colors.surface2).padding(horizontal = 8.dp, vertical = 7.dp),
        ) {
          cols.forEachIndexed { i, (title, w) ->
            val mod = (if (w == 0.dp) Modifier.weight(1f) else Modifier.width(w)).clickable {
              if (sortBy == i) asc = !asc else { sortBy = i; asc = true }
            }
            Text(
              title + if (sortBy == i) (if (asc) " ▲" else " ▼") else "",
              mod, style = MaterialTheme.typography.labelMedium, color = Shop.colors.muted,
              textAlign = if (i >= 3) TextAlign.End else TextAlign.Start,
            )
          }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().border(1.dp, Shop.colors.border, RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))) {
          if (rows.isEmpty()) item {
            Text(
              if (d.products.isEmpty()) "هنوز کالایی نیست — در ردیفِ بالا بنویسید و Enter بزنید." else "چیزی با «$search» پیدا نشد.",
              Modifier.fillMaxWidth().padding(28.dp), color = Shop.colors.muted, textAlign = TextAlign.Center,
            )
          }
          itemsIndexed(rows, key = { _, p -> p.id }) { i, p ->
            val stock = stockIndex[p.id] ?: 0.0
            val low = p.active && stock <= p.minStock
            Row(
              Modifier
                .fillMaxWidth()
                .background(
                  when {
                    p.id == openId -> Shop.colors.primary.copy(alpha = 0.12f)
                    i % 2 == 1 -> Shop.colors.surface2.copy(alpha = 0.45f)
                    else -> Shop.colors.surface
                  }
                )
                .clickable { openId = p.id }
                .padding(horizontal = 8.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                p.name + if (!p.active) "  (غیرفعال)" else "",
                Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                color = if (p.active) Shop.colors.text else Shop.colors.muted,
              )
              Cell(p.barcodes.firstOrNull().orEmpty(), 150.dp)
              Cell(p.category, 130.dp)
              Cell(money(p.purchasePrice), 100.dp, end = true)
              Cell(money(p.salePrice), 100.dp, end = true, bold = true)
              Text(
                "${qty(stock)} ${p.unit}",
                Modifier.width(110.dp), textAlign = TextAlign.End, maxLines = 1,
                style = MaterialTheme.typography.bodyMedium,
                color = if (low) Shop.colors.danger else Shop.colors.text,
              )
            }
          }
        }
        Text(
          "ردیفِ بالا: Tab خانهٔ بعد · Enter ثبت · کلیک روی کالا ⇐ ویرایش کنارِ جدول · Ctrl+F جست‌وجو",
          Modifier.padding(top = 6.dp), style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted,
        )
      }

      /* ─────────── پنلِ ویرایش ─────────── */
      val open = openId?.let { id -> d.products.find { it.id == id } }
      if (open != null) {
        Spacer(Modifier.width(12.dp))
        key(open.id) {
          EditPanel(
            store, d, open, stockIndex[open.id] ?: 0.0, ::toast,
            onClose = { openId = null },
            onWarehouse = { onOpenWarehouse(open.id) },
            onDetail = { onOpenProduct(open.id) },
          )
        }
      }
    }
  }
}

@Composable
private fun RowScope.Cell(text: String, w: Dp, end: Boolean = false, bold: Boolean = false) {
  Text(
    text, Modifier.width(w).padding(horizontal = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
    textAlign = if (end) TextAlign.End else TextAlign.Start,
    style = MaterialTheme.typography.bodyMedium,
    fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
    color = Shop.colors.text,
  )
}

/** خانهٔ کوچکِ ردیفِ افزودن: Enter ⇐ ثبت */
@Composable
private fun QuickField(
  value: String,
  onChange: (String) -> Unit,
  label: String,
  modifier: Modifier,
  onEnter: () -> Unit,
  numeric: Boolean = false,
) {
  OutlinedTextField(
    value = value,
    onValueChange = { onChange(if (numeric) ir.vil3ntec.tohid.numericInput(it) else it) },
    label = { Text(label, maxLines = 1) },
    singleLine = true,
    textStyle = MaterialTheme.typography.bodyMedium,
    modifier = modifier.onPreviewKeyEvent { e ->
      if (e.type == KeyEventType.KeyDown && (e.key == Key.Enter || e.key == Key.NumPadEnter)) { onEnter(); true } else false
    },
  )
}

@Composable
private fun QuickAddRow(store: ShopStore, d: ShopData, toast: (String) -> Unit, incoming: MutableState<String?>) {
  val scope = rememberCoroutineScope()
  var name by remember { mutableStateOf("") }
  var barcode by remember { mutableStateOf("") }
  var category by remember { mutableStateOf("") }
  var unit by remember { mutableStateOf("دانه") }
  var buy by remember { mutableStateOf("") }
  var sell by remember { mutableStateOf("") }
  var count by remember { mutableStateOf("") }
  val nameFocus = remember { FocusRequester() }
  LaunchedEffect(incoming.value) {
    incoming.value?.let { barcode = it; incoming.value = null; runCatching { nameFocus.requestFocus() } }
  }

  fun save() {
    val draft = WarehouseEngine.ProductDraft(
      name = name, category = category.trim(), unit = unit.trim(),
      purchasePrice = buy.toDoubleOrNull() ?: 0.0, salePrice = sell.toDoubleOrNull() ?: 0.0,
      barcode = barcode.trim(),
    )
    when (val r = WarehouseEngine.addProduct(d, draft, System.currentTimeMillis(), ::newId)) {
      is WarehouseEngine.Result.Failed -> toast(r.message)
      is WarehouseEngine.Result.Ok -> {
        var next = r.data
        val n = count.toDoubleOrNull() ?: 0.0
        if (n > 0) {
          val e = WarehouseEngine.addEntry(
            next,
            WarehouseEngine.EntryDraft(productId = r.id, units = n, unit = unit.trim(), price = buy.toDoubleOrNull() ?: 0.0),
            ir.vil3ntec.tohid.todayIso(), System.currentTimeMillis(), ::newId,
          )
          if (e is WarehouseEngine.Result.Ok) next = e.data else if (e is WarehouseEngine.Result.Failed) toast(e.message)
        }
        scope.launch { store.save(next) }
        toast("«${name.trim()}» ثبت شد")
        //  دسته و واحد برای کالای بعدی می‌مانند — معمولاً چند کالای یک دسته پشتِ هم ثبت می‌شوند
        name = ""; barcode = ""; buy = ""; sell = ""; count = ""
        runCatching { nameFocus.requestFocus() }
      }
    }
  }

  Surface(
    color = Shop.colors.surfaceSolid,
    shape = RoundedCornerShape(8.dp),
    modifier = Modifier.fillMaxWidth().border(1.dp, Shop.colors.primary.copy(alpha = 0.35f), RoundedCornerShape(8.dp)),
  ) {
    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
      QuickField(name, { name = it }, "کالای تازه", Modifier.weight(1f).focusRequester(nameFocus), ::save)
      Spacer(Modifier.width(6.dp))
      QuickField(barcode, { barcode = it }, "بارکد", Modifier.width(150.dp), ::save)
      Spacer(Modifier.width(6.dp))
      QuickField(category, { category = it }, "دسته", Modifier.width(120.dp), ::save)
      Spacer(Modifier.width(6.dp))
      QuickField(unit, { unit = it }, "واحد", Modifier.width(80.dp), ::save)
      Spacer(Modifier.width(6.dp))
      QuickField(buy, { buy = it }, "خرید", Modifier.width(95.dp), ::save, numeric = true)
      Spacer(Modifier.width(6.dp))
      QuickField(sell, { sell = it }, "فروش", Modifier.width(95.dp), ::save, numeric = true)
      Spacer(Modifier.width(6.dp))
      QuickField(count, { count = it }, "موجودی", Modifier.width(90.dp), ::save, numeric = true)
      Spacer(Modifier.width(6.dp))
      Button(onClick = ::save, enabled = name.isNotBlank()) { Text("ثبت ↵") }
    }
  }
}

@Composable
private fun EditPanel(
  store: ShopStore,
  d: ShopData,
  p: Product,
  stock: Double,
  toast: (String) -> Unit,
  onClose: () -> Unit,
  onWarehouse: () -> Unit,
  onDetail: () -> Unit,
) {
  val scope = rememberCoroutineScope()
  val focus = LocalFocusManager.current
  var name by remember { mutableStateOf(p.name) }
  var barcode by remember { mutableStateOf(p.barcodes.firstOrNull().orEmpty()) }
  var category by remember { mutableStateOf(p.category) }
  var unit by remember { mutableStateOf(p.unit) }
  var buy by remember { mutableStateOf(qty(p.purchasePrice)) }
  var sell by remember { mutableStateOf(qty(p.salePrice)) }
  var min by remember { mutableStateOf(qty(p.minStock)) }
  var active by remember { mutableStateOf(p.active) }

  fun save() {
    val draft = WarehouseEngine.ProductDraft(
      name = name, category = category.trim(), unit = unit.trim(),
      purchasePrice = buy.toDoubleOrNull() ?: 0.0, salePrice = sell.toDoubleOrNull() ?: 0.0,
      minStock = min.toDoubleOrNull() ?: 0.0, barcode = barcode.trim(), active = active,
    )
    when (val r = WarehouseEngine.editProduct(d, p.id, draft, ir.vil3ntec.tohid.todayIso(), System.currentTimeMillis(), ::newId)) {
      is WarehouseEngine.Result.Failed -> toast(r.message)
      is WarehouseEngine.Result.Ok -> { scope.launch { store.save(r.data) }; toast("«${name.trim()}» ذخیره شد") }
    }
  }

  Surface(
    color = Shop.colors.surfaceSolid,
    shape = RoundedCornerShape(10.dp),
    modifier = Modifier
      .width(340.dp)
      .fillMaxHeight()
      .border(1.dp, Shop.colors.border, RoundedCornerShape(10.dp))
      .onPreviewKeyEvent { e ->
        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        when {
          e.key == Key.Escape -> { onClose(); true }
          e.isCtrlPressed && e.key == Key.S -> { save(); true }
          e.key == Key.Enter || e.key == Key.NumPadEnter -> { save(); true }
          else -> false
        }
      },
  ) {
    Column(Modifier.padding(14.dp).verticalScroll(rememberScrollState())) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text("ویرایشِ کالا", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = Shop.colors.text)
        TextButton(onClick = onClose) { Text("بستن Esc") }
      }
      Text("موجودی: ${qty(stock)} ${p.unit}", style = MaterialTheme.typography.bodySmall, color = if (stock <= p.minStock) Shop.colors.danger else Shop.colors.muted)
      Spacer(Modifier.height(10.dp))
      @Composable fun F(v: String, set: (String) -> Unit, label: String, numeric: Boolean = false) {
        OutlinedTextField(
          value = v, onValueChange = { set(if (numeric) ir.vil3ntec.tohid.numericInput(it) else it) },
          label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
      }
      F(name, { name = it }, "نام")
      F(barcode, { barcode = it }, "بارکد")
      F(category, { category = it }, "دسته")
      F(unit, { unit = it }, "واحد")
      Row {
        Box(Modifier.weight(1f)) { F(buy, { buy = it }, "قیمتِ خرید", true) }
        Spacer(Modifier.width(6.dp))
        Box(Modifier.weight(1f)) { F(sell, { sell = it }, "قیمتِ فروش", true) }
      }
      F(min, { min = it }, "حدِ هشدارِ موجودی", true)
      Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = active, onCheckedChange = { active = it })
        Text("فعال (در فروش دیده می‌شود)", style = MaterialTheme.typography.bodyMedium, color = Shop.colors.text)
      }
      Spacer(Modifier.height(10.dp))
      Button(onClick = { save(); focus.clearFocus() }, modifier = Modifier.fillMaxWidth()) { Text("ذخیره  (Enter / Ctrl+S)") }
      Spacer(Modifier.height(6.dp))
      Row {
        OutlinedButton(onClick = onWarehouse, modifier = Modifier.weight(1f)) { Text("ورودِ انبار") }
        Spacer(Modifier.width(6.dp))
        OutlinedButton(onClick = onDetail, modifier = Modifier.weight(1f)) { Text("جزئیات و عکس") }
      }
    }
  }
}
