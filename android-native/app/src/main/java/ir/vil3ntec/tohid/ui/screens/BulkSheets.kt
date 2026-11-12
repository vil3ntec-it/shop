package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ir.vil3ntec.tohid.data.CountFilter
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.WarehouseEngine
import androidx.compose.foundation.lazy.items
import ir.vil3ntec.tohid.data.ReportCsv
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.plain
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.theme.Radius
import ir.vil3ntec.tohid.ui.theme.Shop

/**
 *  ثبتِ دسته‌جمعی — «محصول جدید» و «ورود کالا به انبار».
 *
 *  فروشنده جنس را کارتن‌کارتن تحویل می‌گیرد، نه یکی‌یکی. تا حالا باید
 *  برای هر قلم یک فرم باز و بسته می‌کرد؛ ده قلم یعنی ده بار. اینجا مثل
 *  نسخهٔ وب، ردیف‌ها زیرِ هم‌اند و آخرش یک دکمه.
 *
 *  دو تصمیم که عمدی‌اند:
 *
 *   ۱) **یا همه یا هیچ‌کدام.** سنجش در `WarehouseEngine` است و پیش از هر
 *      نوشتنی همهٔ ردیف‌ها بررسی می‌شوند. ثبتِ نیمه‌کاره بدترین حالت است:
 *      کاربر نمی‌داند کدام رفت، دوباره می‌زند و نصفشان دو بار می‌نشیند.
 *
 *   ۲) **ردیفِ دست‌نخورده نادیده گرفته می‌شود.** ردیفی که هیچ چیزش پر
 *      نشده، خطا نیست — کاربر «افزودن ردیف» را زده و بعد پشیمان شده.
 *
 *  صفحهٔ کامل است نه کادرِ وسطِ صفحه: ردیف‌ها بلندند و صفحه‌کلید که بالا
 *  می‌آید، از یک کادرِ کوچک چیزی نمی‌ماند.
 */

/* ======================= محصول جدید، چند ردیفی ======================= */

/** یک ردیفِ محصول در حالتِ دسته‌جمعی */
private data class ProductRow(
  val key: Int,
  val name: String = "",
  val category: String = "",
  val unit: String = "",
  val purchase: String = "",
  val sale: String = "",
  val minStock: String = "",
  val barcode: String = "",
) {
  /** ردیفی که هیچ چیزش پر نشده — نادیده گرفته می‌شود، خطا نیست */
  val untouched: Boolean
    get() = name.isBlank() && purchase.isBlank() && sale.isBlank() &&
      barcode.isBlank() && minStock.isBlank()

  /** آمادهٔ ثبت است؟ همان شرطِ `bpReadyRows` نسخهٔ وب */
  val ready: Boolean
    get() = name.isNotBlank() && (purchase.toDoubleOrNull() ?: -1.0) >= 0 &&
      (sale.toDoubleOrNull() ?: -1.0) >= 0 && unit.isNotBlank() && category.isNotBlank()

  fun draft() = WarehouseEngine.ProductDraft(
    name = name,
    category = category,
    unit = unit,
    purchasePrice = purchase.toDoubleOrNull() ?: 0.0,
    salePrice = sale.toDoubleOrNull() ?: -1.0,
    minStock = minStock.toDoubleOrNull() ?: 0.0,
    barcode = barcode,
  )
}

@Composable
fun BulkProductSheet(
  d: ShopData,
  onDismiss: () -> Unit,
  onSave: (List<WarehouseEngine.ProductDraft>) -> Unit,
) {
  var seq by remember { mutableStateOf(1) }
  var rows by remember { mutableStateOf(listOf(ProductRow(key = 0))) }
  var error by remember { mutableStateOf<String?>(null) }
  // کلیدِ ردیفی که دوربین برایش باز است. `null` یعنی بسته.
  var scanFor by remember { mutableStateOf<Int?>(null) }

  fun update(key: Int, change: (ProductRow) -> ProductRow) {
    rows = rows.map { if (it.key == key) change(it) else it }
  }

  val ready = rows.filter { it.ready }

  scanFor?.let { key ->
    BarcodeScanSheet(
      onDismiss = { scanFor = null },
      onCode = { code -> update(key) { it.copy(barcode = code) } },
    )
  }

  BulkFrame(
    title = ir.vil3ntec.tohid.core.i18n.StrCatalogPages.newProductTitle,
    subtitle = ir.vil3ntec.tohid.core.i18n.StrCatalogPages.newProductsSub,
    error = error,
    summary = listOf(
      ir.vil3ntec.tohid.core.i18n.StrCatalogPages.readyItems to plain(ready.size),
      ir.vil3ntec.tohid.core.i18n.StrCatalogPages.allRows to plain(rows.size),
    ),
    saveEnabled = ready.isNotEmpty(),
    onAddRow = { rows = rows + ProductRow(key = seq++) },
    onDismiss = onDismiss,
    onSave = {
      // ردیفِ نیمه‌پر خطاست، ردیفِ دست‌نخورده نه
      val broken = rows.indexOfFirst { !it.untouched && !it.ready }
      if (broken >= 0) {
        error = ir.vil3ntec.tohid.core.i18n.StrProducts.rowIncomplete(plain(broken + 1))
      } else if (ready.isEmpty()) {
        error = ir.vil3ntec.tohid.core.i18n.StrProducts.needOneRow
      } else {
        error = null
        onSave(ready.map { it.draft() })
      }
    },
  ) {
    itemsIndexed(rows, key = { _, r -> r.key }) { index, row ->
      BulkRow(
        number = index + 1,
        canRemove = rows.size > 1,
        onRemove = { rows = rows.filterNot { it.key == row.key } },
        onDuplicate = {
          val copy = row.copy(key = seq++, barcode = "")
          rows = rows.toMutableList().also { it.add(index + 1, copy) }
        },
      ) {
        EntryField(
          value = row.name,
          onValueChange = { v -> update(row.key) { it.copy(name = v) } },
          label = ir.vil3ntec.tohid.core.i18n.StrCatalogPages.productName,
          placeholder = ir.vil3ntec.tohid.core.i18n.StrProducts.namePlaceholder,
          modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          EntryNumberField(
            value = row.purchase,
            onValueChange = { v -> update(row.key) { it.copy(purchase = v) } },
            label = ir.vil3ntec.tohid.core.i18n.StrProducts.purchasePrice,
            modifier = Modifier.weight(1f),
          )
          EntryNumberField(
            value = row.sale,
            onValueChange = { v -> update(row.key) { it.copy(sale = v) } },
            label = ir.vil3ntec.tohid.core.i18n.StrProducts.salePrice,
            modifier = Modifier.weight(1f),
          )
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          EntryField(
            value = row.barcode,
            onValueChange = { v -> update(row.key) { it.copy(barcode = v) } },
            label = ir.vil3ntec.tohid.core.i18n.StrProducts.barcode,
            placeholder = ir.vil3ntec.tohid.core.i18n.StrProducts.barcodePlaceholder,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
            trailing = { ScanIconButton { scanFor = row.key } },
          )
          EntryNumberField(
            value = row.minStock,
            onValueChange = { v -> update(row.key) { it.copy(minStock = v) } },
            label = ir.vil3ntec.tohid.core.i18n.StrProducts.minStock,
            modifier = Modifier.weight(1f),
          )
        }

        Spacer(Modifier.height(12.dp))
        ChipPicker(
          title = ir.vil3ntec.tohid.core.i18n.StrProducts.unit,
          options = d.productUnits,
          selected = row.unit,
          onSelect = { v -> update(row.key) { it.copy(unit = v) } },
          placeholder = ir.vil3ntec.tohid.core.i18n.StrCatalogPages.newUnit,
        )

        Spacer(Modifier.height(12.dp))
        ChipPicker(
          title = ir.vil3ntec.tohid.core.i18n.StrProducts.category,
          options = d.productCategories,
          selected = row.category,
          onSelect = { v -> update(row.key) { it.copy(category = v) } },
          placeholder = ir.vil3ntec.tohid.core.i18n.StrFields.newCategory,
        )

        // بازتابِ ردیف — همان `.bulk-echo` وب: سودِ هر واحد، همان‌جا
        val buy = row.purchase.toDoubleOrNull()
        val sell = row.sale.toDoubleOrNull()
        if (row.name.isNotBlank()) {
          Spacer(Modifier.height(10.dp))
          val text = buildString {
            append(row.name.trim())
            if (row.unit.isNotBlank()) append(ir.vil3ntec.tohid.core.i18n.StrCatalogPages.eachUnit(row.unit))
            if (buy != null && sell != null && buy >= 0 && sell >= 0) {
              append(ir.vil3ntec.tohid.core.i18n.StrCatalogPages.profitPer(row.unit.ifBlank { ir.vil3ntec.tohid.core.i18n.StrProducts.unitWord }, money(sell - buy)))
            }
          }
          RowEcho(text, warn = row.unit.isBlank() || row.category.isBlank())
        }
      }
    }
  }
}

/* ==================== ورود کالا به انبار، چند ردیفی ==================== */

private data class EntryRow(
  val key: Int,
  val productId: String = "",
  val newProduct: Boolean = false,
  val newName: String = "",
  val newCategory: String = "",
  val newBarcode: String = "",
  val qty: String = "",
  val price: String = "",
  val unit: String = "",
) {
  val untouched: Boolean
    get() = !newProduct && productId.isBlank() && qty.isBlank() && price.isBlank()

  val ready: Boolean
    get() = (if (newProduct) newName.isNotBlank() else productId.isNotBlank()) &&
      (qty.toDoubleOrNull() ?: 0.0) > 0 && (price.toDoubleOrNull() ?: -1.0) >= 0

  val amount: Double get() = qty.toDoubleOrNull() ?: 0.0
  val value: Double get() = amount * (price.toDoubleOrNull() ?: 0.0)
}

@Composable
fun BulkEntrySheet(
  d: ShopData,
  onDismiss: () -> Unit,
  onSave: (List<WarehouseEngine.BulkEntry>, String) -> Unit,
) {
  var seq by remember { mutableStateOf(1) }
  var rows by remember { mutableStateOf(listOf(EntryRow(key = 0, unit = d.products.firstOrNull()?.unit.orEmpty()))) }
  var date by remember { mutableStateOf(todayIso()) }
  var error by remember { mutableStateOf<String?>(null) }
  // کلیدِ ردیفی که دوربین برایش باز است. `null` یعنی بسته.
  var scanFor by remember { mutableStateOf<Int?>(null) }
  var scanMiss by remember { mutableStateOf<String?>(null) }
  //  «اسکن‌های در انتظار» ⇒ ورودِ کالا: هر کالا یک ردیف با شمارش، بارکدِ ناشناس ردیفِ «محصولِ تازه» (`ScanApply.receive`)
  LaunchedEffect(Unit) {
    val added = ir.vil3ntec.tohid.scan.ScanHandoff.take(ir.vil3ntec.tohid.core.hw.ScanTarget.RECEIVE)?.let { h -> ir.vil3ntec.tohid.scan.ScanApply.legacyRows(d, h.scans) }.orEmpty()
      .map { r -> EntryRow(key = seq++, productId = r.productId.orEmpty(), newProduct = r.productId == null, newBarcode = r.barcode, qty = r.qty, price = r.price, unit = r.unit) }
    if (added.isNotEmpty()) rows = rows.filterNot { it.untouched } + added
  }

  fun update(key: Int, change: (EntryRow) -> EntryRow) {
    rows = rows.map { if (it.key == key) change(it) else it }
  }

  val ready = rows.filter { it.ready }

  // بارکد ← کالا. یک بار ساخته می‌شود، نه برای هر اسکن.
  val byBarcode = remember(d.products) {
    buildMap {
      d.products.forEach { p -> p.barcodes.forEach { code -> put(code.trim(), p) } }
    }
  }

  scanFor?.let { key ->
    BarcodeScanSheet(
      title = ir.vil3ntec.tohid.core.i18n.StrCatalogPages.scanProductBarcode,
      onDismiss = { scanFor = null },
      onCode = { code ->
        val hit = byBarcode[code]
        if (hit != null) {
          // کالای شناخته‌شده: همان‌جا انتخاب می‌شود، با واحد و قیمت خریدش —
          // دقیقاً همان کاری که زدن روی تراشه‌اش می‌کند
          scanMiss = null
          update(key) {
            it.copy(
              productId = hit.id,
              newProduct = false,
              unit = hit.unit.ifBlank { it.unit },
              price = if (it.price.isBlank() && hit.purchasePrice > 0)
                hit.purchasePrice.toLong().toString() else it.price,
            )
          }
        } else {
          // بارکدِ ناشناس گم نمی‌شود: ردیف به حالتِ «محصول جدید» می‌رود و
          // همان بارکد داخلش می‌نشیند. کاربر فقط نامش را می‌نویسد.
          scanMiss = code
          update(key) { it.copy(newProduct = true, productId = "", newBarcode = code) }
        }
      },
    )
  }

  BulkFrame(
    title = ir.vil3ntec.tohid.core.i18n.StrStock.entryTitle,
    subtitle = ir.vil3ntec.tohid.core.i18n.StrCatalogPages.entrySub,
    error = error,
    summary = listOf(
      ir.vil3ntec.tohid.core.i18n.StrCatalogPages.readyRows to plain(ready.size),
      ir.vil3ntec.tohid.core.i18n.StrCatalogPages.totalQty to money(ready.sumOf { it.amount }),
      ir.vil3ntec.tohid.core.i18n.StrCatalogPages.totalValue to "${money(ready.sumOf { it.value })} ؋",
    ),
    saveEnabled = ready.isNotEmpty(),
    onAddRow = { rows = rows + EntryRow(key = seq++, unit = d.products.firstOrNull()?.unit.orEmpty()) },
    onDismiss = onDismiss,
    onSave = {
      val broken = rows.indexOfFirst { !it.untouched && !it.ready }
      if (broken >= 0) {
        error = ir.vil3ntec.tohid.core.i18n.StrStock.errRow(plain(broken + 1))
      } else if (ready.isEmpty()) {
        error = ir.vil3ntec.tohid.core.i18n.StrStock.errNoRows
      } else {
        error = null
        onSave(
          ready.map { row ->
            WarehouseEngine.BulkEntry(
              entry = WarehouseEngine.EntryDraft(
                productId = row.productId,
                units = row.amount,
                unit = row.unit,
                price = row.price.toDoubleOrNull() ?: 0.0,
                date = date,
              ),
              newProduct = if (!row.newProduct) null else WarehouseEngine.ProductDraft(
                name = row.newName,
                category = row.newCategory,
                unit = row.unit.ifBlank { d.productUnits.firstOrNull() ?: "عدد" }, // داده: واحدِ پیش‌فرضِ کالای تازه (داده)
                purchasePrice = row.price.toDoubleOrNull() ?: 0.0,
                salePrice = 0.0,
                barcode = row.newBarcode,
              ),
            )
          },
          date,
        )
      }
    },
    header = {
      // همان رفتارِ قبلی — «امروز»، «دیروز» و تاریخِ خورشیدیِ زیرِ کادر —
      // فقط با کادرِ دیدنیِ این دو صفحه
      EntryDateField(
        value = date,
        onValueChange = { date = it },
        label = ir.vil3ntec.tohid.core.i18n.StrCatalogPages.dateAllRows,
        modifier = Modifier.fillMaxWidth(),
      )
      Spacer(Modifier.height(14.dp))
    },
  ) {
    itemsIndexed(rows, key = { _, r -> r.key }) { index, row ->
      BulkRow(
        number = index + 1,
        canRemove = rows.size > 1,
        onRemove = { rows = rows.filterNot { it.key == row.key } },
        onDuplicate = {
          val copy = row.copy(key = seq++, newBarcode = "")
          rows = rows.toMutableList().also { it.add(index + 1, copy) }
        },
      ) {
        // انتخابِ محصول، یا ساختِ محصولِ تازه — همان دو حالتِ کشویی وب.
        // تراشه‌ها و کارشان دست‌نخورده‌اند؛ فقط داخلِ یک کادر نشسته‌اند تا
        // مثلِ بقیهٔ خانه‌های همین صفحه دیده شوند.
        EntryFieldBox(ir.vil3ntec.tohid.core.i18n.Str.pickProduct, trailing = { ScanIconButton { scanFor = row.key } }) {
          Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            d.products.forEach { p ->
              FilterChip(
                selected = !row.newProduct && row.productId == p.id,
                onClick = {
                  // واحد و قیمت خریدِ همان کالا پیش‌فرض می‌آید
                  update(row.key) {
                    it.copy(
                      productId = p.id,
                      newProduct = false,
                      unit = p.unit.ifBlank { it.unit },
                      price = if (it.price.isBlank() && p.purchasePrice > 0)
                        p.purchasePrice.toLong().toString() else it.price,
                    )
                  }
                },
                label = { Text(p.name) },
              )
            }
            FilterChip(
              selected = row.newProduct,
              onClick = { update(row.key) { it.copy(newProduct = !it.newProduct, productId = "") } },
              label = { Text(ir.vil3ntec.tohid.core.i18n.StrCatalogPages.plusNewProduct) },
            )
          }
        }
        if (scanMiss != null && row.newProduct && row.newBarcode == scanMiss) {
          Spacer(Modifier.height(6.dp))
          Text(
            ir.vil3ntec.tohid.core.i18n.StrStock.scanMiss,
            style = MaterialTheme.typography.labelSmall,
            color = Shop.colors.muted2,
          )
        }

        if (row.newProduct) {
          Spacer(Modifier.height(10.dp))
          EntryField(
            value = row.newName,
            onValueChange = { v -> update(row.key) { it.copy(newName = v) } },
            label = ir.vil3ntec.tohid.core.i18n.StrCatalogPages.newProductName,
            placeholder = ir.vil3ntec.tohid.core.i18n.StrStock.newProductNameHint,
            modifier = Modifier.fillMaxWidth(),
          )
          Spacer(Modifier.height(12.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            EntryField(
              value = row.newCategory,
              onValueChange = { v -> update(row.key) { it.copy(newCategory = v) } },
              label = ir.vil3ntec.tohid.core.i18n.StrProducts.category,
              modifier = Modifier.weight(1f),
            )
            EntryField(
              value = row.newBarcode,
              onValueChange = { v -> update(row.key) { it.copy(newBarcode = v) } },
              label = ir.vil3ntec.tohid.core.i18n.StrProducts.barcode,
              placeholder = ir.vil3ntec.tohid.core.i18n.StrProducts.barcodePlaceholder,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              modifier = Modifier.weight(1f),
              trailing = { ScanIconButton { scanFor = row.key } },
            )
          }
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          EntryNumberField(
            value = row.qty,
            onValueChange = { v -> update(row.key) { it.copy(qty = v) } },
            label = ir.vil3ntec.tohid.core.i18n.StrMoney.quantity,
            modifier = Modifier.weight(1f),
          )
          EntryNumberField(
            value = row.price,
            onValueChange = { v -> update(row.key) { it.copy(price = v) } },
            label = ir.vil3ntec.tohid.core.i18n.StrCatalogPages.purchasePriceAf,
            modifier = Modifier.weight(1f),
          )
        }

        Spacer(Modifier.height(12.dp))
        ChipPicker(
          title = ir.vil3ntec.tohid.core.i18n.StrProducts.unit,
          options = d.productUnits,
          selected = row.unit,
          onSelect = { v -> update(row.key) { it.copy(unit = v) } },
          placeholder = ir.vil3ntec.tohid.core.i18n.StrCatalogPages.newUnit,
        )

        if (row.amount > 0) {
          Spacer(Modifier.height(10.dp))
          val text = buildString {
            append("${money(row.amount)} ${row.unit}".trim())
            if (row.value > 0) append(ir.vil3ntec.tohid.core.i18n.StrCatalogPages.valueOf(money(row.value)))
          }
          RowEcho(text, warn = row.unit.isBlank())
        }
      }
    }
  }
}

/* ============================ قابِ مشترک ============================ */

/**
 *  قابِ صفحهٔ دسته‌جمعی: سربرگ، ردیف‌ها، «افزودن ردیف»، خلاصه و دکمه‌ها.
 *
 *  خلاصه و دکمه‌ها به پایینِ صفحه چسبیده‌اند و با ردیف‌ها اسکرول نمی‌شوند؛
 *  در فهرستِ ده ردیفی، کاربر نباید تا ته برود تا «ذخیره همه» را ببیند.
 */
@Composable
private fun BulkFrame(
  title: String,
  subtitle: String,
  error: String?,
  summary: List<Pair<String, String>>,
  saveEnabled: Boolean,
  /** `null` ⇒ ردیف افزودنی نیست (انبارگردانی: ردیف‌ها همان کالاها هستند) */
  onAddRow: (() -> Unit)?,
  onDismiss: () -> Unit,
  onSave: () -> Unit,
  saveText: String = ir.vil3ntec.tohid.core.i18n.StrCatalogPages.saveAll,
  extraAction: (@Composable () -> Unit)? = null,
  header: @Composable ColumnScope.() -> Unit = {},
  rows: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
  /*
   *  دکمهٔ «ذخیره همه» زیرِ نوارِ ناوبریِ گوشی می‌رفت و قابلِ زدن نبود.
   *
   *  علتش این بود که `Dialog` پنجرهٔ جداگانهٔ خودش را دارد و آنجا
   *  `WindowInsets.systemBars` صفر گزارش می‌شود — پس فاصله‌ای که داخلِ
   *  Dialog می‌گذاشتیم هیچ کاری نمی‌کرد.
   *
   *  بلندیِ نوارها **بیرونِ** Dialog خوانده می‌شود، جایی که درست است، و
   *  به شکلِ عدد به داخل برده می‌شود؛ و پنجرهٔ Dialog تمام‌صفحه می‌شود تا
   *  خودش هم دوباره فاصله نگذارد و دو بار حساب نشود.
   */
  val bars = WindowInsets.systemBars.asPaddingValues()
  val topBar = bars.calculateTopPadding()
  val bottomBar = bars.calculateBottomPadding()

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(
      usePlatformDefaultWidth = false,
      decorFitsSystemWindows = false,
    ),
  ) {
    Surface(color = Shop.colors.bg, modifier = Modifier.fillMaxSize()) {
      // دو چیز که در عکس‌های دستگاه معلوم بود:
      //  • دکمهٔ «ذخیره همه» زیرِ نوارِ ناوبریِ گوشی می‌رفت و نصفش بریده
      //    می‌شد؛ حالا فاصلهٔ نوارهای سیستم کنار گذاشته می‌شود
      //  • روی تبلت فرم تمام‌عرض کش می‌آمد و دو طرفش خالی می‌ماند؛ حالا
      //    وسط می‌ماند و پهنایش محدود است
      PageWidth {
      Column(
        Modifier
          .fillMaxSize()
          .padding(top = topBar)
          .imePadding()
          .padding(bottom = bottomBar)
          .padding(horizontal = 16.dp)
      ) {
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = Shop.colors.text)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
          }
          TextButton(onClick = onDismiss) { Text(ir.vil3ntec.tohid.core.i18n.Str.cancel) }
        }
        Spacer(Modifier.height(12.dp))
        header()

        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          rows()
          if (onAddRow != null) item {
            OutlinedButton(onClick = onAddRow, modifier = Modifier.fillMaxWidth()) {
              Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(17.dp))
              Spacer(Modifier.width(6.dp))
              Text(ir.vil3ntec.tohid.core.i18n.StrProducts.addRow)
            }
            Spacer(Modifier.height(8.dp))
          }
        }

        if (error != null) {
          Text(
            error,
            style = MaterialTheme.typography.labelMedium,
            color = Shop.colors.danger,
            modifier = Modifier.padding(vertical = 6.dp),
          )
        }

        /*
         *  پاورقی — یک ردیف، نه دو بلوک.
         *
         *  قبلاً کارتِ خلاصه و دکمهٔ «ذخیره همه» هرکدام یک ردیفِ کامل
         *  بودند و با پاورقی و فاصله‌ها نزدیکِ صد نقطه از پایینِ صفحه را
         *  می‌گرفتند. روی گوشی، وقتی صفحه‌کلید هم بالا می‌آمد، از خودِ
         *  فرم چیزی نمی‌ماند که در آن بنویسی.
         *
         *  حالا خلاصه و دکمه در یک ردیف‌اند و خلاصه با نقطه از هم جدا
         *  می‌شود؛ همان اطلاعات، یک‌سومِ ارتفاع.
         */
        Spacer(Modifier.height(8.dp))
        Row(
          Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Text(
            summary.joinToString(" · ") { (label, value) -> "$label: $value" },
            style = MaterialTheme.typography.labelSmall,
            color = Shop.colors.muted,
            maxLines = 2,
            modifier = Modifier.weight(1f),
          )
          extraAction?.invoke()
          TohidButton(
            text = saveText,
            onClick = onSave,
            enabled = saveEnabled,
          )
        }
        Spacer(Modifier.height(10.dp))
      }
      }
    }
  }
}

/** یک ردیف، با شمارهٔ خودش و دکمه‌های تکثیر و حذف */
@Composable
private fun BulkRow(
  number: Int,
  canRemove: Boolean,
  onRemove: () -> Unit,
  onDuplicate: () -> Unit,
  content: @Composable ColumnScope.() -> Unit,
) {
  Column(
    Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(Radius.md))
      .background(Shop.colors.surface)
      .border(1.dp, Shop.colors.border, RoundedCornerShape(Radius.md))
      .padding(14.dp)
  ) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Box(
        Modifier
          .size(24.dp)
          .clip(RoundedCornerShape(8.dp))
          .background(Shop.colors.primaryTint),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          plain(number),
          style = MaterialTheme.typography.labelMedium,
          color = Shop.colors.primary,
          fontWeight = FontWeight.Bold,
        )
      }
      Spacer(Modifier.weight(1f))
      IconButton(onClick = onDuplicate, modifier = Modifier.size(32.dp)) {
        Icon(
          Icons.Filled.ContentCopy,
          contentDescription = ir.vil3ntec.tohid.core.i18n.StrProducts.duplicateRow,
          tint = Shop.colors.muted,
          modifier = Modifier.size(16.dp),
        )
      }
      IconButton(onClick = onRemove, enabled = canRemove, modifier = Modifier.size(32.dp)) {
        Icon(
          Icons.Filled.DeleteOutline,
          contentDescription = ir.vil3ntec.tohid.core.i18n.StrProducts.removeRow,
          tint = if (canRemove) Shop.colors.danger else Shop.colors.muted2,
          modifier = Modifier.size(17.dp),
        )
      }
    }
    Spacer(Modifier.height(8.dp))
    content()
  }
}

/** بازتابِ زیرِ ردیف — همان `.bulk-echo` نسخهٔ وب */
@Composable
private fun RowEcho(text: String, warn: Boolean) {
  Text(
    text,
    style = MaterialTheme.typography.labelSmall,
    color = if (warn) Shop.colors.warning else Shop.colors.muted,
  )
}

/* ============================ انبارگردانی ============================ */

/** یک ردیفِ تراشهٔ صافی (INV-05): «همه» و هر گزینه؛ `null` ⇒ همه */
/** INV-05: برچسبِ تراشهٔ قفسه — «راهرو ۲ · قفسه ۱» به زبانِ صفحه (`CountFilter.shelfKey`) */
internal fun countShelfLabel(key: String): String = CountFilter.shelfLabel(key)

/** INV-05: تراشه‌های برند و قفسه — هر کدام فقط وقتی دست‌کم یک کالا آن را دارد */
@Composable
internal fun CountBrandShelfChips(brands: List<String>, brand: String?, onBrand: (String?) -> Unit, shelves: List<String>, shelf: String?, onShelf: (String?) -> Unit) {
  if (brands.isNotEmpty()) CountFilterChips(ir.vil3ntec.tohid.core.i18n.StrCount.allBrands, brands.map { it to it }, brand, onBrand)
  if (shelves.isNotEmpty()) CountFilterChips(ir.vil3ntec.tohid.core.i18n.StrCount.allShelves, shelves.map { it to countShelfLabel(it) }, shelf, onShelf)
}

@Composable
internal fun CountFilterChips(all: String, options: List<Pair<String, String>>, picked: String?, onPick: (String?) -> Unit) {
  Row(
    Modifier.fillMaxWidth().padding(top = 6.dp).horizontalScroll(rememberScrollState()),
    horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    FilterChip(selected = picked == null, onClick = { onPick(null) }, label = { Text(all) })
    options.forEach { (id, label) ->
      FilterChip(selected = picked == id, onClick = { onPick(if (picked == id) null else id) }, label = { Text(label, maxLines = 1) })
    }
  }
}

/**
 *  انبارگردانی — هر کالا را بشمار، برنامه اختلاف را نشان می‌دهد و با یک
 *  «ثبت» همه را «اصلاحِ موجودی» با دلیلِ «انبارگردانی» می‌کند.
 *
 *  کالایی که خانه‌اش خالی بماند شمرده نشده و دست نمی‌خورد — دکان‌دار
 *  می‌تواند فقط یک قفسه را بشمارد. گزارشِ اختلاف پیش از ثبت به CSV می‌رود.
 */
@Composable
fun StockCountSheet(
  d: ShopData,
  onDismiss: () -> Unit,
  onExport: (ReportCsv.Sheet) -> Unit,
  onSave: (Map<String, Double>, String) -> Unit,
) {
  var counts by remember { mutableStateOf(mapOf<String, String>()) }
  //  «اسکن‌های در انتظار» ⇒ انبارگردانی (`ScanApply.counts`)
  LaunchedEffect(Unit) { ir.vil3ntec.tohid.scan.ScanHandoff.take(ir.vil3ntec.tohid.core.hw.ScanTarget.COUNT)?.let { counts = ir.vil3ntec.tohid.scan.ScanApply.counts(counts, d, it.scans).counts } }
  var search by remember { mutableStateOf("") }
  var note by remember { mutableStateOf("") }
  var error by remember { mutableStateOf<String?>(null) }
  //  INV-05: صافیِ دسته و تأمین‌کننده — فقط فهرستِ دیده‌شده؛ شمرده‌ها و ثبت همان (`CountFilter`)
  var category by remember { mutableStateOf<String?>(null) }
  var supplierId by remember { mutableStateOf<String?>(null) }
  var brand by remember { mutableStateOf<String?>(null) }
  var shelf by remember { mutableStateOf<String?>(null) }

  val parsed: Map<String, Double> = remember(counts) {
    counts.mapNotNull { (id, text) ->
      val t = ir.vil3ntec.tohid.latinDigits(text.trim()).replace('٫', '.').replace(',', '.')
      if (t.isEmpty()) null else t.toDoubleOrNull()?.let { id to it } ?: (id to Double.NaN)
    }.toMap()
  }
  val lines = remember(d, parsed) { WarehouseEngine.countLines(d, parsed.filterValues { !it.isNaN() && it >= 0 }) }
  val stock = remember(d) { d.products.associate { it.id to ShopStore.stock(d, it.id) } }
  val term = search.trim()
  val shown = remember(d, term, category, supplierId, brand, shelf) { CountFilter.shown(d.products, term, category, supplierId, brand, shelf) }
  val categories = remember(d) { CountFilter.categories(d.products) }
  val suppliers = remember(d) { CountFilter.suppliers(d.products, d.suppliers) }
  val brands = remember(d) { CountFilter.brands(d.products) }
  val shelves = remember(d) { CountFilter.shelves(d.products) }

  BulkFrame(
    title = ir.vil3ntec.tohid.core.i18n.StrStock.menuCount,
    subtitle = ir.vil3ntec.tohid.core.i18n.StrStock.countHelper,
    error = error,
    summary = listOf(
      ir.vil3ntec.tohid.core.i18n.StrStock.counted to plain(parsed.size),
      ir.vil3ntec.tohid.core.i18n.StrDrawers.varianceWord to plain(lines.size),
      ir.vil3ntec.tohid.core.i18n.StrCatalogPages.diffValue to "${money(lines.sumOf { it.value })} ؋",
    ),
    saveEnabled = lines.isNotEmpty(),
    onAddRow = null,
    onDismiss = onDismiss,
    saveText = ir.vil3ntec.tohid.core.i18n.StrCatalogPages.saveDiffsAll,
    extraAction = {
      OutlinedButton(onClick = { onExport(ReportCsv.stockCount(lines, todayIso())) }, enabled = lines.isNotEmpty()) {
        Text("CSV")
      }
    },
    onSave = {
      val bad = parsed.entries.firstOrNull { it.value.isNaN() || it.value < 0 }
      if (bad != null) {
        error = ir.vil3ntec.tohid.core.i18n.StrStock.badNumber(d.products.find { it.id == bad.key }?.name.orEmpty())
      } else {
        error = null
        onSave(parsed, note)
      }
    },
    header = {
      OutlinedTextField(
        value = search, onValueChange = { search = it },
        placeholder = { Text(ir.vil3ntec.tohid.core.i18n.StrStock.searchHint) },
        singleLine = true, modifier = Modifier.fillMaxWidth(),
      )
      if (categories.isNotEmpty()) CountFilterChips(ir.vil3ntec.tohid.core.i18n.StrCount.allCategories, categories.map { it to it }, category) { category = it }
      if (suppliers.isNotEmpty()) CountFilterChips(ir.vil3ntec.tohid.core.i18n.StrCount.allSuppliers, suppliers.map { it.id to it.name.ifBlank { it.id } }, supplierId) { supplierId = it }
      CountBrandShelfChips(brands, brand, { brand = it }, shelves, shelf, { shelf = it }) // INV-05: برند و قفسه
      Spacer(Modifier.height(8.dp))
      OutlinedTextField(
        value = note, onValueChange = { note = it },
        placeholder = { Text(ir.vil3ntec.tohid.core.i18n.StrCount.note) },
        singleLine = true, modifier = Modifier.fillMaxWidth(),
      )
      Spacer(Modifier.height(10.dp))
    },
  ) {
    items(shown, key = { it.id }) { p ->
      val sys = stock[p.id] ?: 0.0
      val c = parsed[p.id]
      val diff = if (c != null && !c.isNaN()) c - sys else null
      Row(
        Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(10.dp))
          .background(Shop.colors.surface)
          .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(Modifier.weight(1f)) {
          Text(p.name, style = MaterialTheme.typography.bodyMedium, color = Shop.colors.text, maxLines = 1)
          Text(
            ir.vil3ntec.tohid.core.i18n.StrCount.system(ir.vil3ntec.tohid.qty(sys), p.unit) + when {
              diff == null || kotlin.math.abs(diff) < 0.0005 -> ""
              diff > 0 -> " · +${ir.vil3ntec.tohid.qty(diff)}"
              else -> " · −${ir.vil3ntec.tohid.qty(-diff)}"
            },
            style = MaterialTheme.typography.labelSmall,
            color = when {
              diff == null || kotlin.math.abs(diff) < 0.0005 -> Shop.colors.muted
              diff > 0 -> Shop.colors.success
              else -> Shop.colors.danger
            },
          )
        }
        OutlinedTextField(
          value = counts[p.id].orEmpty(),
          onValueChange = { v -> counts = counts + (p.id to v) },
          placeholder = { Text(ir.vil3ntec.tohid.core.i18n.StrStock.counted) },
          singleLine = true,
          isError = c != null && (c.isNaN() || c < 0),
          keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
          ),
          modifier = Modifier.width(110.dp),
        )
      }
    }
  }
}
