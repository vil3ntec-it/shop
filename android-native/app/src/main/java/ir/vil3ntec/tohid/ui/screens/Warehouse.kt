package ir.vil3ntec.tohid.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.WarehouseEngine
import ir.vil3ntec.tohid.formatDate
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.plain
import ir.vil3ntec.tohid.qty
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.theme.Radius
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.launch

/**
 *  انبار.
 *
 *  فهرستِ کالاها با موجودیِ همان لحظه، ورودِ جنس، اصلاحِ موجودی و ثبتِ
 *  کالای تازه. موجودی هیچ‌جا ذخیره نمی‌شود؛ همیشه از «آنچه وارد شده منهای
 *  آنچه فروخته شده» حساب می‌شود — همان‌طور که نسخهٔ وب می‌کرد. عددی که
 *  جداگانه نگه داشته شود، بالاخره یک روز با واقعیت نمی‌خواند.
 */
@Composable
fun WarehouseScreen(
  store: ShopStore,
  d: ShopData,
  snackbar: SnackbarHostState,
  openProductId: String? = null,
  newBarcode: String? = null,
  onConsumed: () -> Unit = {},
  /** «دفترِ موجودی» — فقط وقتی موتورِ تازهٔ موجودی و بها روشن است */
  onStockBook: () -> Unit = {},
) {
  val stockOn = remember(d.auditLog, ir.vil3ntec.tohid.data.StockLedger.serverFlag) { ir.vil3ntec.tohid.data.StockLedger.isOn(d) }
  val scope = rememberCoroutineScope()

  var search by rememberSaveable { mutableStateOf("") }
  var category by rememberSaveable { mutableStateOf<String?>(null) }
  var expanded by rememberSaveable { mutableStateOf<String?>(null) }

  var productForm by remember { mutableStateOf<ProductFormState?>(null) }
  var entryFor by remember { mutableStateOf<String?>(null) }
  var bulkProduct by remember { mutableStateOf(false) }
  var bulkEntry by remember { mutableStateOf(false) }
  var confirmEntry by remember { mutableStateOf<String?>(null) }
  var adjustFor by remember { mutableStateOf<String?>(null) }
  var confirmDelete by remember { mutableStateOf<Product?>(null) }
  var movementsFor by remember { mutableStateOf<Product?>(null) }
  var stockCount by remember { mutableStateOf(false) }
  //  چاپِ برچسب و بارکدِ داخلی (PRD-23، PRD-05) — پشتِ `LabelToolsPref`، پیش‌فرض خاموش
  var labels by remember { mutableStateOf(false) }
  //  ورودِ گروهیِ عکس (v3 ۳۶.۸) — کارِ صاحب/مدیر
  var imageBulk by remember { mutableStateOf(false) }
  //  ورودِ گروهی از CSV — پیش‌نمایش پیش از ثبت
  var importPlan by remember { mutableStateOf<ir.vil3ntec.tohid.data.ProductImport.Plan?>(null) }
  //  PRD-20 (پشتِ `ShopPolicy.productTools`): xlsx یا CSV، پیش‌نمایشِ کامل، فایلِ ردیف‌های رد و سابقه
  var importFile by remember { mutableStateOf<ir.vil3ntec.tohid.data.ProductImport.Picked?>(null) }
  var importHistory by remember { mutableStateOf(false) }
  //  فیلترِ انقضا: null همه، "expired" منقضی، "near" نزدیک به انقضا
  var expiryFilter by rememberSaveable { mutableStateOf<String?>(null) }

  val context = androidx.compose.ui.platform.LocalContext.current
  val labelTools = remember { ir.vil3ntec.tohid.data.LabelToolsPref.on(context) }
  //  انبارگردانیِ تازه با اسکن، چند دستگاه و شمارشِ کور (INV-06/07/08) — پشتِ `CountToolsPref`، پیش‌فرض خاموش
  val countTools = remember { ir.vil3ntec.tohid.data.CountToolsPref.on(context) }
  var pendingCsv by remember { mutableStateOf<ir.vil3ntec.tohid.data.ReportCsv.Sheet?>(null) }
  val saveCsv = androidx.activity.compose.rememberLauncherForActivityResult(
    androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/csv")
  ) { uri ->
    val sheet = pendingCsv
    pendingCsv = null
    if (uri == null || sheet == null) return@rememberLauncherForActivityResult
    scope.launch {
      runCatching {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
          context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(ir.vil3ntec.tohid.data.ReportCsv.text(sheet).toByteArray(Charsets.UTF_8))
          } ?: error("فایل ساخته نشد")
        }
      }
    }
  }

  fun toast(text: String) {
    scope.launch { snackbar.showSnackbar(text) }
  }

  val productTools = remember { ir.vil3ntec.tohid.data.ShopPolicy.productTools(context) }
  var pendingReport by remember { mutableStateOf<ByteArray?>(null) }
  val saveReport = androidx.activity.compose.rememberLauncherForActivityResult(
    androidx.activity.result.contract.ActivityResultContracts.CreateDocument(
      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    )
  ) { uri ->
    val bytes = pendingReport
    pendingReport = null
    if (uri == null || bytes == null) return@rememberLauncherForActivityResult
    scope.launch {
      val ok = runCatching {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
          context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("فایل ساخته نشد")
        }
      }.isSuccess
      toast(if (ok) "فایلِ ردیف‌های رد ذخیره شد — درستش کنید و دوباره وارد کنید" else "فایل ذخیره نشد")
    }
  }

  /** یک نتیجه را می‌نویسد یا دلیلِ ردش را می‌گوید */
  fun apply(result: WarehouseEngine.Result, done: String, after: () -> Unit = {}) {
    when (result) {
      is WarehouseEngine.Result.Failed -> toast(result.message)
      is WarehouseEngine.Result.Ok -> {
        scope.launch { store.save(result.data) }
        toast(done)
        after()
      }
    }
  }

  // بارکدِ ناشناسی که در صفحهٔ فروش خوانده شد، همین‌جا کالای تازه می‌شود
  LaunchedEffect(newBarcode) {
    if (!newBarcode.isNullOrBlank()) {
      productForm = ProductFormState(barcode = newBarcode)
      onConsumed()
    }
  }
  LaunchedEffect(openProductId) {
    if (openProductId != null) {
      expanded = openProductId
      //  کامپیوتر: از اسکن یا دکمهٔ «ورودِ انبار» آمده ⇒ فرمِ ورودِ همین کالا همان لحظه باز
      if (ir.vil3ntec.tohid.ui.Platform.DESKTOP) entryFor = openProductId
      onConsumed()
    }
  }

  val openCsv = androidx.activity.compose.rememberLauncherForActivityResult(
    androidx.activity.result.contract.ActivityResultContracts.GetContent()
  ) { uri ->
    if (uri == null) return@rememberLauncherForActivityResult
    if (productTools) {
      scope.launch {
        //  بیش از سقف خوانده نمی‌شود — فایلِ بزرگ پیش از رسیدن به حافظه رد می‌شود
        val read = runCatching {
          kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
              val out = java.io.ByteArrayOutputStream()
              val buf = ByteArray(64 * 1024)
              var total = 0
              while (total <= ir.vil3ntec.tohid.core.product.ImportTable.MAX_FILE_BYTES) {
                val n = input.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                total += n
              }
              out.toByteArray()
            } ?: error("فایل خوانده نشد")
            ir.vil3ntec.tohid.data.FileName.of(context, uri) to ir.vil3ntec.tohid.data.ProductImport.read(bytes)
          }
        }.getOrNull()
        if (read == null) { toast("فایل خوانده نشد"); return@launch }
        when (val r = read.second) {
          is ir.vil3ntec.tohid.core.product.ImportTable.Read.Failed -> toast(r.reason)
          is ir.vil3ntec.tohid.core.product.ImportTable.Read.Table ->
            importFile = ir.vil3ntec.tohid.data.ProductImport.Picked(read.first.ifBlank { "فایل" }, r.rows)
        }
      }
      return@rememberLauncherForActivityResult
    }
    scope.launch {
      val text = runCatching {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
          context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
        }
      }.getOrNull()
      if (text == null) { toast("فایل خوانده نشد"); return@launch }
      val plan = ir.vil3ntec.tohid.data.ProductImport.plan(d, text)
      if (plan.missingName) toast("ستونِ «نام محصول» در سطرِ اولِ فایل پیدا نشد")
      else importPlan = plan
    }
  }

  val term = search.trim()
  //  محموله‌های منقضی و نزدیک به انقضا — یک بار برای هر تغییرِ دفتر
  val expiryIds = remember(d) {
    val alerts = ir.vil3ntec.tohid.data.ExpiryEngine.alerts(d, todayIso())
    mapOf(
      "expired" to alerts.filter { it.expired }.map { it.product.id }.toSet(),
      "near" to alerts.filter { !it.expired }.map { it.product.id }.toSet(),
    )
  }
  val shown = remember(d, term, category, expiryFilter) {
    d.products.filter { p ->
      (expiryFilter == null || p.id in expiryIds[expiryFilter].orEmpty()) &&
      (category == null || p.category == category) &&
        (term.isBlank() || p.name.contains(term, ignoreCase = true) ||
          p.barcodes.any { it.contains(term) })
    }
  }
  val summary = remember(d) { WarehouseEngine.summary(d) }

  Box(Modifier.fillMaxSize()) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp)) {
      item {
        Text(
          "موجودی کالاهای فروشگاه",
          style = MaterialTheme.typography.bodySmall,
          color = Shop.colors.muted,
        )
        Spacer(Modifier.height(12.dp))
        // همان دکمهٔ بالای صفحهٔ انبار در نسخهٔ وب: چند قلم را یک‌جا وارد کن
        Button(onClick = { bulkEntry = true }, modifier = Modifier.fillMaxWidth()) {
          Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(6.dp))
          Text("ورود کالا به انبار")
        }
        Spacer(Modifier.height(8.dp))
        //  موتورِ تازه روشن ⇒ شمارش با بازبینی در «دفترِ موجودی» (INV-09/10)؛ خاموش ⇒ همان برگهٔ امروز
        OutlinedButton(
          onClick = { if (stockOn) { StockBookNav.tab.value = "count"; onStockBook() } else stockCount = true },
          modifier = Modifier.fillMaxWidth(),
        ) {
          Icon(Icons.Filled.Inventory2, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(6.dp))
          Text("انبارگردانی")
        }
        if (labelTools) {
          Spacer(Modifier.height(8.dp))
          OutlinedButton(onClick = { labels = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(ir.vil3ntec.tohid.core.i18n.StrLabel.openButton)
          }
        }
        if (stockOn) {
          Spacer(Modifier.height(8.dp))
          OutlinedButton(onClick = onStockBook, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Inventory2, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("دفترِ موجودی — بها، سری، محل و دفترِ حرکت")
          }
          Spacer(Modifier.height(8.dp))
          StockStrip(d, onStockBook)
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
          onClick = { openCsv.launch("*/*") },
          modifier = Modifier.fillMaxWidth(),
        ) {
          Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(6.dp))
          Text(if (productTools) "ورود کالا از فایل اکسل (xlsx یا CSV)" else "ورود کالا از فایل اکسل (CSV)")
        }
        val imported = remember(d.auditLog) { d.auditLog.any { it.type == ir.vil3ntec.tohid.data.ProductImport.AUDIT_TYPE } }
        if (productTools && imported) {
          TextButton(onClick = { importHistory = true }, modifier = Modifier.fillMaxWidth()) {
            Text("سابقهٔ ورود از فایل")
          }
        }
        if (ir.vil3ntec.tohid.data.ShopRole.current(context) != ir.vil3ntec.tohid.data.ShopRole.STAFF) {
          Spacer(Modifier.height(8.dp))
          OutlinedButton(onClick = { imageBulk = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("ورودِ گروهیِ عکس‌ها")
          }
        }
        Spacer(Modifier.height(14.dp))
      }

      /* ------------------------- خلاصه ------------------------- */
      item {
        Row(
          Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Chip("قلم کالا", plain(summary.products))
          Chip("کارتن", qty(summary.cartons))
          Chip("واحد", qty(summary.units))
          Chip("ارزش تقریبی", "${money(summary.value)} ؋")
        }
        Spacer(Modifier.height(10.dp))
        if (summary.low > 0 || summary.out > 0) {
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (summary.out > 0) StatusPill("${plain(summary.out)} تمام‌شده", Shop.colors.danger, Shop.colors.dangerTint)
            if (summary.low > 0) StatusPill("${plain(summary.low)} موجودی کم", Shop.colors.warning, Shop.colors.warningTint)
          }
          Spacer(Modifier.height(10.dp))
        }
      }

      /* ------------------------- جستجو و فیلتر ------------------------- */
      item {
        OutlinedTextField(
          value = search,
          onValueChange = { search = it },
          placeholder = { Text("جستجوی کالا یا بارکد…") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        if (expiryIds.values.any { it.isNotEmpty() } || expiryFilter != null) {
          Spacer(Modifier.height(8.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(null to "همه", "expired" to "منقضی", "near" to "نزدیک به انقضا").forEach { (key, label) ->
              val n = key?.let { expiryIds[it]?.size ?: 0 }
              FilterChip(
                selected = expiryFilter == key,
                onClick = { expiryFilter = key },
                label = { Text(if (n == null) label else "$label (${plain(n)})") },
              )
            }
          }
        }
        if (d.productCategories.isNotEmpty()) {
          Spacer(Modifier.height(8.dp))
          CategoryFilter(
            categories = d.productCategories,
            selected = category,
            onSelect = { category = it },
          )
        }
        Spacer(Modifier.height(14.dp))
      }

      /* ------------------------- فهرستِ کالاها ------------------------- */
      if (d.products.isEmpty()) {
        item {
          Panel {
            TohidEmptyState(
              icon = Icons.Filled.Inventory2,
              title = "انبار خالی است",
              description = "کالاهای دکان را ثبت کنید تا موجودی، ارزش انبار و هشدار کم‌بودن کار کند.",
              actionText = "ثبت کالای تازه",
              onAction = { bulkProduct = true },
            )
          }
        }
      } else if (shown.isEmpty()) {
        item {
          Panel {
            TohidEmptyState(
              icon = Icons.Filled.Search,
              title = "چیزی پیدا نشد",
              description = "کالایی مطابق این جستجو یا فیلتر نیست.",
            )
          }
        }
      } else {
        itemsIndexed(shown, key = { _, p -> p.id }) { index, p ->
          StaggeredItem(index) {
          ProductRow(
            d = d,
            product = p,
            open = expanded == p.id,
            onClick = { expanded = if (expanded == p.id) null else p.id },
            onEdit = { productForm = ProductFormState.of(p) },
            onEntry = { entryFor = p.id },
            onDeleteEntry = { confirmEntry = it },
            onAdjust = { adjustFor = p.id },
            onDelete = { confirmDelete = p },
            onMovements = { movementsFor = p },
          )
          Spacer(Modifier.height(8.dp))
        }
        }
      }
    }

    AddFab(
      label = "کالای تازه",
      containerColor = Shop.colors.button.primary.bg,
      onClick = { bulkProduct = true },
      modifier = Modifier.align(Alignment.BottomStart).padding(16.dp).popIn(),
    )
  }

  /* ---------------------------- پنجره‌ها ---------------------------- */

  confirmEntry?.let { entryId ->
    AlertDialog(
      onDismissRequest = { confirmEntry = null },
      containerColor = Shop.colors.surfaceSolid,
      title = { Text("حذف این ورودی انبار؟", color = Shop.colors.text) },
      text = {
        Text(
          WarehouseEngine.deleteEntryWarning(d, entryId),
          style = MaterialTheme.typography.bodySmall,
          color = Shop.colors.muted,
        )
      },
      confirmButton = {
        TextButton(onClick = {
          apply(
            WarehouseEngine.deleteEntry(d, entryId, todayIso(), System.currentTimeMillis(), ::newId),
            "با موفقیت حذف شد",
          )
          confirmEntry = null
        }) { Text("حذف", color = Shop.colors.danger) }
      },
      dismissButton = { TextButton(onClick = { confirmEntry = null }) { Text("انصراف") } },
    )
  }

  if (bulkProduct) {
    BulkProductSheet(
      d = d,
      onDismiss = { bulkProduct = false },
      onSave = { drafts ->
        apply(
          WarehouseEngine.addProducts(d, drafts, System.currentTimeMillis(), ::newId),
          "${plain(drafts.size)} کالا ثبت شد",
        ) { bulkProduct = false }
      },
    )
  }

  if (imageBulk) ImageBulkImportSheet(store, d) { imageBulk = false }

  importFile?.let { file ->
    ProductImportDialog(
      d = d,
      file = file,
      onDismiss = { importFile = null },
      onErrorReport = { bytes, name -> pendingReport = bytes; saveReport.launch(name) },
      onDone = { result ->
        importFile = null
        apply(result, "ورود از فایل ثبت شد")
      },
    )
  }
  if (importHistory) {
    ProductImportHistoryDialog(ir.vil3ntec.tohid.data.ProductImport.history(d)) { importHistory = false }
  }

  importPlan?.let { plan ->
    val similarWarn = remember { ir.vil3ntec.tohid.data.ShopPolicy.productTools(context) }
    AlertDialog(
      onDismissRequest = { importPlan = null },
      containerColor = Shop.colors.surfaceSolid,
      title = { Text("ورود کالا از فایل", color = Shop.colors.text) },
      text = {
        Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
          Text(
            "${plain(plan.drafts.size)} کالا آمادهٔ ثبت" +
              if (plan.skipped.isEmpty()) "" else " · ${plain(plan.skipped.size)} ردیف ثبت نمی‌شود:",
            style = MaterialTheme.typography.bodyMedium,
            color = Shop.colors.text,
          )
          plan.skipped.take(50).forEach { sk ->
            Text(
              "ردیف ${plain(sk.row)}${if (sk.name.isNotBlank()) " «${sk.name}»" else ""}: ${sk.reason}",
              style = MaterialTheme.typography.labelSmall,
              color = Shop.colors.danger,
            )
          }
          if (plan.skipped.size > 50) {
            Text("و ${plain(plan.skipped.size - 50)} ردیفِ دیگر", style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
          }
          //  PRD-19 (پیش‌فرض خاموش): ثبت می‌شوند، ولی شاید همان کالای دیگرند
          if (similarWarn && plan.similar.isNotEmpty()) {
            Text(
              "${plain(plan.similar.size)} ردیف ثبت می‌شود ولی نامش مشابه است — اگر همان کالاست، از فایل بردارید:",
              style = MaterialTheme.typography.bodySmall,
              color = Shop.colors.warning,
            )
            plan.similar.take(50).forEach { sm ->
              Text(
                "ردیف ${plain(sm.row)} «${sm.name}» ≈ «${sm.other}»${if (sm.inFile) " (در همین فایل)" else ""}",
                style = MaterialTheme.typography.labelSmall,
                color = Shop.colors.warning,
              )
            }
          }
        }
      },
      confirmButton = {
        TextButton(enabled = plan.drafts.isNotEmpty(), onClick = {
          apply(
            WarehouseEngine.addProducts(d, plan.drafts, System.currentTimeMillis(), ::newId),
            "${plain(plan.drafts.size)} کالا از فایل ثبت شد",
          ) { importPlan = null }
        }) { Text("ثبت ${plain(plan.drafts.size)} کالا") }
      },
      dismissButton = { TextButton(onClick = { importPlan = null }) { Text("انصراف") } },
    )
  }

  if (stockCount) {
    val onCountSave: (Map<String, Double>, Map<String, Long>, String) -> Unit = { counted, at, note ->
      val shift = ir.vil3ntec.tohid.data.CountShift.of(d, at) // INV-10: پنجرهٔ قدیم بی لحظه ⇒ همان امروز
      val n = WarehouseEngine.countLines(d, counted, shift).size
      apply(
        WarehouseEngine.stockCount(d, counted, note, todayIso(), System.currentTimeMillis(), ::newId, shift),
        "انبارگردانی ثبت شد — ${plain(n)} اختلاف اصلاح شد",
      ) { stockCount = false }
    }
    val onCountExport: (ir.vil3ntec.tohid.data.ReportCsv.Sheet) -> Unit = { sheet -> pendingCsv = sheet; saveCsv.launch("${sheet.name}.csv") }
    if (countTools) StockCountToolsWired(context, d, onDismiss = { stockCount = false }, onExport = onCountExport, onSave = onCountSave)
    else StockCountSheet(d = d, onDismiss = { stockCount = false }, onExport = onCountExport, onSave = { c, note -> onCountSave(c, emptyMap(), note) })
  }

  if (labels) LabelSheetWired(context, d, onDismiss = { labels = false }) { r, done -> apply(r, done) }

  if (bulkEntry) {
    BulkEntrySheet(
      d = d,
      onDismiss = { bulkEntry = false },
      onSave = { rows, date ->
        apply(
          WarehouseEngine.addEntries(d, rows, date, System.currentTimeMillis(), ::newId),
          "${plain(rows.size)} ردیف ثبت شد",
        ) { bulkEntry = false }
      },
    )
  }

  productForm?.let { form ->
    ProductDialog(
      d = d,
      state = form,
      onDismiss = { productForm = null },
      onSave = { draft ->
        val result = if (form.editingId == null) {
          WarehouseEngine.addProduct(d, draft, System.currentTimeMillis(), ::newId)
        } else {
          WarehouseEngine.editProduct(d, form.editingId, draft, todayIso(), System.currentTimeMillis(), ::newId)
        }
        apply(result, if (form.editingId == null) "کالا ثبت شد" else "با موفقیت ویرایش شد") {
          productForm = null
          // کالای تازه بدونِ ورودِ انبار موجودی ندارد؛ همان‌جا پیشنهاد می‌شود
          if (form.editingId == null) entryFor = (result as WarehouseEngine.Result.Ok).id
        }
      },
    )
  }

  entryFor?.let { productId ->
    EntryDialog(
      d = d,
      productId = productId,
      onDismiss = { entryFor = null },
      onSave = { draft ->
        apply(
          WarehouseEngine.addEntry(d, draft, todayIso(), System.currentTimeMillis(), ::newId),
          "ورود کالا ثبت شد",
        ) { entryFor = null }
      },
    )
  }

  adjustFor?.let { productId ->
    AdjustDialog(
      d = d,
      productId = productId,
      onDismiss = { adjustFor = null },
      onSave = { quantity, increase, reason, kind, adjKind ->
        apply(
          WarehouseEngine.adjustStock(
            d, productId, quantity, increase, reason, kind,
            todayIso(), System.currentTimeMillis(), ::newId,
            adjKind = adjKind,
            userId = if (adjKind != null) runCatching { ir.vil3ntec.tohid.sync.SyncStore(context).accountName }.getOrNull() else null,
          ),
          "با موفقیت ثبت شد",
        ) { adjustFor = null }
      },
    )
  }

  confirmDelete?.let { p ->
    AlertDialog(
      onDismissRequest = { confirmDelete = null },
      containerColor = Shop.colors.surfaceSolid,
      title = { Text("حذف محصول؟", color = Shop.colors.text) },
      text = {
        Text(
          WarehouseEngine.deleteWarning(d, p.id),
          style = MaterialTheme.typography.bodySmall,
          color = Shop.colors.muted,
        )
      },
      confirmButton = {
        TextButton(onClick = {
          apply(
            WarehouseEngine.deleteProduct(d, p.id, todayIso(), System.currentTimeMillis(), ::newId),
            "با موفقیت حذف شد",
          ) { confirmDelete = null; expanded = null }
        }) { Text("حذف", color = Shop.colors.danger) }
      },
      dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("انصراف") } },
    )
  }

  movementsFor?.let { p ->
    MovementsDialog(d = d, product = p, onDismiss = { movementsFor = null })
  }
}

/* ============================ تکه‌های رابط ============================ */

@Composable
private fun Chip(label: String, value: String) {
  Column(
    Modifier
      .clip(RoundedCornerShape(Radius.sm))
      .background(Shop.colors.surface)
      .border(1.dp, Shop.colors.border, RoundedCornerShape(Radius.sm))
      .padding(horizontal = 14.dp, vertical = 10.dp)
  ) {
    Text(label, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
    Spacer(Modifier.height(3.dp))
    Text(value, style = MaterialTheme.typography.titleSmall, color = Shop.colors.text)
  }
}

@Composable
private fun StatusPill(text: String, tint: Color, background: Color) {
  Box(
    Modifier
      .clip(RoundedCornerShape(999.dp))
      .background(background)
      .padding(horizontal = 12.dp, vertical = 6.dp)
  ) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = tint)
  }
}

@Composable
private fun ProductRow(
  d: ShopData,
  product: Product,
  open: Boolean,
  onClick: () -> Unit,
  onEdit: () -> Unit,
  onEntry: () -> Unit,
  onAdjust: () -> Unit,
  onDelete: () -> Unit,
  onMovements: () -> Unit,
  onDeleteEntry: (String) -> Unit,
) {
  val stock = ShopStore.stock(d, product.id)
  val status = ShopStore.stockStatus(d, product)
  val tint = when (status) {
    "out" -> Shop.colors.danger
    "low" -> Shop.colors.warning
    else -> Shop.colors.success
  }
  val label = when (status) {
    "out" -> "تمام‌شده"
    "low" -> "موجودی کم"
    else -> "موجودی کافی"
  }

  Column(
    Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(Radius.md))
      .background(Shop.colors.surface)
      .border(1.dp, if (open) Shop.colors.primary else Shop.colors.border, RoundedCornerShape(Radius.md))
      .clickable(onClick = onClick)
      .padding(14.dp)
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Text(product.name, style = MaterialTheme.typography.titleSmall, color = Shop.colors.text)
        Spacer(Modifier.height(3.dp))
        Text(
          buildString {
            append("${money(product.salePrice)} ؋")
            if (product.category.isNotBlank()) append(" — ${product.category}")
          },
          style = MaterialTheme.typography.labelSmall,
          color = Shop.colors.muted,
        )
      }
      Column(horizontalAlignment = Alignment.End) {
        Text(
          "${qty(stock)}${if (product.unit.isNotBlank()) " ${product.unit}" else ""}",
          style = MaterialTheme.typography.titleSmall,
          color = tint,
          fontWeight = FontWeight.Bold,
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint)
      }
    }

    AnimatedVisibility(visible = open) {
      Column(Modifier.padding(top = 12.dp)) {
        DetailRow("قیمت خرید", "${money(product.purchasePrice)} ؋")
        DetailRow("حد کم بودن", qty(product.minStock))
        if (product.barcodes.isNotEmpty()) {
          DetailRow("بارکد", product.barcodes.joinToString("، ") { it })
        }

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          Button(onClick = onEntry, modifier = Modifier.weight(1f)) { Text("ورود کالا") }
          OutlinedButton(onClick = onAdjust) { Icon(Icons.Filled.Tune, contentDescription = "اصلاح موجودی") }
          OutlinedButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "ویرایش") }
          OutlinedButton(
            onClick = onDelete,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Shop.colors.danger),
          ) { Icon(Icons.Filled.DeleteOutline, contentDescription = "حذف") }
        }
        // ورودی‌های همین کالا — تا حالا فقط ثبت می‌شدند و هیچ‌جا دیده
        // نمی‌شدند؛ کاربر نمی‌توانست ورودیِ اشتباه را پیدا و پاک کند
        val entries = d.warehouseEntries.filter { it.productId == product.id }.sortedByDescending { it.createdAt }
        if (entries.isNotEmpty()) {
          Spacer(Modifier.height(12.dp))
          Text("ورودهای این کالا", style = MaterialTheme.typography.labelMedium, color = Shop.colors.muted)
          Spacer(Modifier.height(6.dp))
          entries.take(6).forEach { entry ->
            Row(
              Modifier.fillMaxWidth().padding(vertical = 5.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(Modifier.weight(1f)) {
                Text(
                  "${qty(entry.units)}${if (entry.unit.isNotBlank()) " ${entry.unit}" else ""} — ${money(entry.price)} ؋",
                  style = MaterialTheme.typography.bodySmall,
                  color = Shop.colors.text,
                )
                Text(
                  buildString {
                    append(formatDate(entry.date))
                    if (entry.cartons > 0) append(" — ${qty(entry.cartons)} کارتن × ${qty(entry.perCarton)}")
                    if (entry.notes.isNotBlank()) append(" — ${entry.notes}")
                  },
                  style = MaterialTheme.typography.labelSmall,
                  color = Shop.colors.muted2,
                )
              }
              IconButton(onClick = { onDeleteEntry(entry.id) }, modifier = Modifier.size(32.dp)) {
                Icon(
                  Icons.Filled.DeleteOutline,
                  contentDescription = "حذف این ورودی",
                  tint = Shop.colors.danger,
                  modifier = Modifier.size(16.dp),
                )
              }
            }
          }
          if (entries.size > 6) {
            Text(
              "و ${plain(entries.size - 6)} ورودِ قدیمی‌تر",
              style = MaterialTheme.typography.labelSmall,
              color = Shop.colors.muted2,
            )
          }
        }

        Spacer(Modifier.height(4.dp))
        TextButton(onClick = onMovements) { Text("حرکات انبار", color = Shop.colors.primary) }
      }
    }
  }
}

@Composable
private fun DetailRow(label: String, value: String) {
  Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
    Text(label, style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
    Text(value, style = MaterialTheme.typography.bodySmall, color = Shop.colors.text)
  }
}

/** حرکاتِ یک کالا — چه وارد شده، چه فروخته، چه اصلاح */
@Composable
private fun MovementsDialog(d: ShopData, product: Product, onDismiss: () -> Unit) {
  val moves = d.stockMovements.filter { it.productId == product.id }.sortedByDescending { it.createdAt }

  androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
    Surface(color = Shop.colors.surfaceSolid, shape = RoundedCornerShape(Radius.lg), modifier = Modifier.fillMaxWidth()) {
      Column(Modifier.padding(16.dp).heightIn(max = 520.dp)) {
        Text("حرکات انبار — ${product.name}", style = MaterialTheme.typography.titleMedium, color = Shop.colors.text)
        Spacer(Modifier.height(12.dp))

        if (moves.isEmpty()) {
          EmptyNote("هنوز حرکتی برای این کالا ثبت نشده")
        } else {
          LazyColumn(Modifier.weight(1f, fill = false)) {
            items(moves, key = { it.id }) { m ->
              val positive = m.qty >= 0
              Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                  Text(movementLabel(m.type), style = MaterialTheme.typography.bodyMedium, color = Shop.colors.text)
                  Text(
                    "${formatDate(m.date)}${if (m.notes.isNotBlank()) " — ${m.notes}" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Shop.colors.muted,
                  )
                }
                Text(
                  "${if (positive) "+" else "−"}${qty(kotlin.math.abs(m.qty))}",
                  style = MaterialTheme.typography.titleSmall,
                  color = if (positive) Shop.colors.success else Shop.colors.danger,
                )
              }
              HorizontalDivider(color = Shop.colors.border)
            }
          }
        }

        Spacer(Modifier.height(12.dp))
        Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("بستن") }
      }
    }
  }
}

/** همان برچسب‌های STOCK_MOVEMENT_LABELS نسخهٔ وب */
private fun movementLabel(type: String): String = when (type) {
  "purchase_in" -> "ورود خرید"
  "sale" -> "فروش"
  "customer_return" -> "مرجوعی مشتری"
  "sale_cancel" -> "لغو فروش"
  "supplier_return" -> "برگشت به تأمین‌کننده"
  "adjustment" -> "اصلاح موجودی"
  "damage" -> "ضایعات"
  //  انتقال بین محل‌ها (موتورِ تازهٔ موجودی): دو ردیف، خروجیِ منفی و ورودیِ مثبت
  "transfer_out" -> "انتقالِ خروجی"
  "transfer_in" -> "انتقالِ ورودی"
  "transfer" -> "انتقال"
  else -> type
}
