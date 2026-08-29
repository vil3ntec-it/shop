package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
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
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.WarehouseEngine
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

  fun update(key: Int, change: (ProductRow) -> ProductRow) {
    rows = rows.map { if (it.key == key) change(it) else it }
  }

  val ready = rows.filter { it.ready }

  BulkFrame(
    title = "محصول جدید",
    subtitle = "چند کالا را با هم ثبت کنید — هر ردیف یک کالا",
    error = error,
    summary = listOf(
      "کالای آماده" to plain(ready.size),
      "کل ردیف‌ها" to plain(rows.size),
    ),
    saveEnabled = ready.isNotEmpty(),
    onAddRow = { rows = rows + ProductRow(key = seq++) },
    onDismiss = onDismiss,
    onSave = {
      // ردیفِ نیمه‌پر خطاست، ردیفِ دست‌نخورده نه
      val broken = rows.indexOfFirst { !it.untouched && !it.ready }
      if (broken >= 0) {
        error = "ردیف ${plain(broken + 1)} کامل نیست — نام، دسته، واحد و هر دو قیمت لازم است"
      } else if (ready.isEmpty()) {
        error = "حداقل یک ردیف کامل لازم است"
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
          label = "نام محصول",
          placeholder = "مثلاً بیسکویت شکلاتی",
          modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          EntryNumberField(
            value = row.purchase,
            onValueChange = { v -> update(row.key) { it.copy(purchase = v) } },
            label = "قیمت خرید",
            modifier = Modifier.weight(1f),
          )
          EntryNumberField(
            value = row.sale,
            onValueChange = { v -> update(row.key) { it.copy(sale = v) } },
            label = "قیمت فروش",
            modifier = Modifier.weight(1f),
          )
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          EntryField(
            value = row.barcode,
            onValueChange = { v -> update(row.key) { it.copy(barcode = v) } },
            label = "بارکد",
            placeholder = "اسکن یا تایپ",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
          )
          EntryNumberField(
            value = row.minStock,
            onValueChange = { v -> update(row.key) { it.copy(minStock = v) } },
            label = "حداقل موجودی",
            modifier = Modifier.weight(1f),
          )
        }

        Spacer(Modifier.height(12.dp))
        ChipPicker(
          title = "واحد",
          options = d.productUnits,
          selected = row.unit,
          onSelect = { v -> update(row.key) { it.copy(unit = v) } },
          placeholder = "واحد تازه",
        )

        Spacer(Modifier.height(12.dp))
        ChipPicker(
          title = "دسته‌بندی",
          options = d.productCategories,
          selected = row.category,
          onSelect = { v -> update(row.key) { it.copy(category = v) } },
          placeholder = "دسته‌بندی تازه",
        )

        // بازتابِ ردیف — همان `.bulk-echo` وب: سودِ هر واحد، همان‌جا
        val buy = row.purchase.toDoubleOrNull()
        val sell = row.sale.toDoubleOrNull()
        if (row.name.isNotBlank()) {
          Spacer(Modifier.height(10.dp))
          val text = buildString {
            append(row.name.trim())
            if (row.unit.isNotBlank()) append(" — هر ${row.unit}")
            if (buy != null && sell != null && buy >= 0 && sell >= 0) {
              append(" · سود هر ${row.unit.ifBlank { "واحد" }}: ${money(sell - buy)} افغانی")
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

  fun update(key: Int, change: (EntryRow) -> EntryRow) {
    rows = rows.map { if (it.key == key) change(it) else it }
  }

  val ready = rows.filter { it.ready }

  BulkFrame(
    title = "ورود کالا به انبار",
    subtitle = "چند قلم را با هم وارد کنید — تاریخ برای همهٔ ردیف‌ها یکی است",
    error = error,
    summary = listOf(
      "ردیف آماده" to plain(ready.size),
      "مجموع مقدار" to money(ready.sumOf { it.amount }),
      "ارزش کل" to "${money(ready.sumOf { it.value })} افغانی",
    ),
    saveEnabled = ready.isNotEmpty(),
    onAddRow = { rows = rows + EntryRow(key = seq++, unit = d.products.firstOrNull()?.unit.orEmpty()) },
    onDismiss = onDismiss,
    onSave = {
      val broken = rows.indexOfFirst { !it.untouched && !it.ready }
      if (broken >= 0) {
        error = "ردیف ${plain(broken + 1)} کامل نیست — محصول، مقدار و قیمت خرید لازم است"
      } else if (ready.isEmpty()) {
        error = "هیچ ردیف کاملی برای ثبت نیست"
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
                unit = row.unit.ifBlank { d.productUnits.firstOrNull() ?: "عدد" },
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
        label = "تاریخ همهٔ ردیف‌ها",
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
        EntryFieldBox("انتخاب محصول") {
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
              label = { Text("+ محصول جدید") },
            )
          }
        }

        if (row.newProduct) {
          Spacer(Modifier.height(10.dp))
          EntryField(
            value = row.newName,
            onValueChange = { v -> update(row.key) { it.copy(newName = v) } },
            label = "نام محصول جدید",
            placeholder = "مثلاً برنج",
            modifier = Modifier.fillMaxWidth(),
          )
          Spacer(Modifier.height(12.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            EntryField(
              value = row.newCategory,
              onValueChange = { v -> update(row.key) { it.copy(newCategory = v) } },
              label = "دسته‌بندی",
              modifier = Modifier.weight(1f),
            )
            EntryField(
              value = row.newBarcode,
              onValueChange = { v -> update(row.key) { it.copy(newBarcode = v) } },
              label = "بارکد",
              placeholder = "اسکن یا تایپ",
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              modifier = Modifier.weight(1f),
            )
          }
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          EntryNumberField(
            value = row.qty,
            onValueChange = { v -> update(row.key) { it.copy(qty = v) } },
            label = "مقدار",
            modifier = Modifier.weight(1f),
          )
          EntryNumberField(
            value = row.price,
            onValueChange = { v -> update(row.key) { it.copy(price = v) } },
            label = "قیمت خرید (افغانی)",
            modifier = Modifier.weight(1f),
          )
        }

        Spacer(Modifier.height(12.dp))
        ChipPicker(
          title = "واحد",
          options = d.productUnits,
          selected = row.unit,
          onSelect = { v -> update(row.key) { it.copy(unit = v) } },
          placeholder = "واحد تازه",
        )

        if (row.amount > 0) {
          Spacer(Modifier.height(10.dp))
          val text = buildString {
            append("${money(row.amount)} ${row.unit}".trim())
            if (row.value > 0) append(" · ارزش ${money(row.value)} افغانی")
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
  onAddRow: () -> Unit,
  onDismiss: () -> Unit,
  onSave: () -> Unit,
  header: @Composable ColumnScope.() -> Unit = {},
  rows: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
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
          .windowInsetsPadding(WindowInsets.systemBars)
          .imePadding()
          .padding(horizontal = 16.dp)
      ) {
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = Shop.colors.text)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
          }
          TextButton(onClick = onDismiss) { Text("انصراف") }
        }
        Spacer(Modifier.height(12.dp))
        header()

        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          rows()
          item {
            OutlinedButton(onClick = onAddRow, modifier = Modifier.fillMaxWidth()) {
              Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(17.dp))
              Spacer(Modifier.width(6.dp))
              Text("افزودن ردیف")
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

        Row(
          Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.sm))
            .background(Shop.colors.surface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          summary.forEach { (label, value) ->
            Column {
              Text(label, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
              Text(
                value,
                style = MaterialTheme.typography.labelLarge,
                color = Shop.colors.text,
                fontWeight = FontWeight.Bold,
              )
            }
          }
        }

        Spacer(Modifier.height(10.dp))
        TohidButton(
          text = "ذخیره همه",
          onClick = onSave,
          enabled = saveEnabled,
          modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
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
          contentDescription = "تکثیر ردیف",
          tint = Shop.colors.muted,
          modifier = Modifier.size(16.dp),
        )
      }
      IconButton(onClick = onRemove, enabled = canRemove, modifier = Modifier.size(32.dp)) {
        Icon(
          Icons.Filled.DeleteOutline,
          contentDescription = "حذف ردیف",
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
