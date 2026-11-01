package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import ir.vil3ntec.tohid.core.product.ProductBarcodes
import ir.vil3ntec.tohid.core.product.SimilarNames
import ir.vil3ntec.tohid.core.product.ProductBarcodes
import ir.vil3ntec.tohid.core.product.ProductLabels
import ir.vil3ntec.tohid.core.product.SimilarNames
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopPolicy
import ir.vil3ntec.tohid.data.WarehouseEngine
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.qty
import ir.vil3ntec.tohid.ui.theme.Radius
import ir.vil3ntec.tohid.ui.theme.Shop

/**
 *  فرم‌های انبار.
 *
 *  عددها همان‌جا که تایپ می‌شوند سنجیده نمی‌شوند — سنجش کارِ
 *  `WarehouseEngine` است. اینجا فقط جمع‌آوری می‌شود و دلیلِ رد شدن به
 *  کاربر نشان داده می‌شود؛ پس هرگز دو جای متفاوت دو قاعدهٔ متفاوت ندارند.
 */

/* ============================ فرمِ کالا ============================ */

data class ProductFormState(
  val editingId: String? = null,
  val name: String = "",
  val category: String = "",
  val unit: String = "",
  val purchase: String = "",
  val sale: String = "",
  val minStock: String = "",
  val barcode: String = "",
  val subcategory: String = "",
  val supplierId: String = "",
  val active: Boolean = true,
  val saleUnit: String = "",
  val unitFactor: String = "",
  /** PRD-04 (پشتِ `ShopPolicy.productTools`): همهٔ بارکدها با «،» — کالای تازه با بارکدِ اسکن‌شده همان را دارد */
  val barcodes: String = barcode,
  /** PRD-02/PRD-26 — فقط با `productInfoOn()` دیده و ذخیره می‌شود */
  val info: ir.vil3ntec.tohid.ui.components.product.ProductInfoForm = ir.vil3ntec.tohid.ui.components.product.ProductInfoForm(),
  /** PRD-04 (پشتِ `ShopPolicy.productTools`): همهٔ بارکدها با «،» — کالای تازه با بارکدِ اسکن‌شده همان را دارد */
  val barcodes: String = barcode,
  /** PRD-06 (همان پرچم): برند و برچسب‌ها با «،» */
  val brand: String = "",
  val tags: String = "",
  /** INV-12 — فقط وقتی «نقطهٔ سفارش و بیشینه» روشن است دیده و فرستاده می‌شوند؛ خالی = تعریف نشده */
  val reorder: String = "",
  val max: String = "",
) {
  companion object {
    fun of(p: Product) = ProductFormState(
      editingId = p.id,
      name = p.name,
      category = p.category,
      unit = p.unit,
      purchase = numberText(p.purchasePrice),
      sale = numberText(p.salePrice),
      minStock = if (p.minStock == 0.0) "" else numberText(p.minStock),
      barcode = p.barcodes.firstOrNull().orEmpty(),
      subcategory = p.subcategory,
      supplierId = p.supplierId,
      active = p.active,
      saleUnit = p.saleUnit,
      unitFactor = if (p.saleUnit.isBlank()) "" else p.unitFactor.toBigDecimal().stripTrailingZeros().toPlainString(),
      barcodes = p.barcodes.joinToString("، "),
      info = ir.vil3ntec.tohid.ui.components.product.ProductInfoForm.of(ir.vil3ntec.tohid.data.ProductInfoMap.of(p)),
      barcodes = p.barcodes.joinToString("، "),
      brand = p.brand.orEmpty(),
      tags = p.tags.orEmpty().joinToString("، "),
      reorder = p.reorderPoint?.let { numberText(it) }.orEmpty(),
      max = p.maxStock?.let { numberText(it) }.orEmpty(),
    )

    /** عدد را برای کادرِ ورودی می‌نویسد: بدونِ اعشارِ بی‌مورد و با رقمِ لاتین */
    private fun numberText(value: Double): String =
      if (value == Math.floor(value) && !value.isInfinite()) value.toLong().toString() else value.toString()
  }
}

@Composable
fun ProductDialog(
  d: ShopData,
  state: ProductFormState,
  onDismiss: () -> Unit,
  onSave: (WarehouseEngine.ProductDraft) -> Unit,
) {
  var form by remember(state.editingId, state.barcode) { mutableStateOf(state) }
  //  PRD-19 و PRD-04 — پیش‌فرض خاموش (تنظیمِ همین دستگاه)
  val context = LocalContext.current
  val tools = remember { ShopPolicy.productTools(context) }
  val similarIndex = remember(d.products, tools) { if (tools) SimilarNames.Index(d.products.map { it.name }) else null }
  val similar = remember(form.name, similarIndex) {
    similarIndex?.find(form.name, except = d.products.indexOfFirst { it.id == form.editingId }).orEmpty()
  }
  //  PRD-14: تغییرِ قیمت با نامِ کاربر و دلیل در سابقه (همان پرچم)
  val me = remember { runCatching { ir.vil3ntec.tohid.sync.SyncStore(context).accountName }.getOrDefault("").trim() }
  var priceReason by remember(state.editingId) { mutableStateOf("") }
  val priceChanged = form.editingId != null && (form.purchase != state.purchase || form.sale != state.sale)
  //  PRD-02/PRD-26: خاموش ⇒ همان فرمِ پیشین و `info = null` (فیلدها دست نمی‌خورند)
  val infoOn = ir.vil3ntec.tohid.ui.components.product.productInfoOn()
  //  PRD-19 و PRD-04 — پیش‌فرض خاموش (تنظیمِ همین دستگاه)
  val context = LocalContext.current
  val tools = remember { ShopPolicy.productTools(context) }
  val similarIndex = remember(d.products, tools) { if (tools) SimilarNames.Index(d.products.map { it.name }) else null }
  val similar = remember(form.name, similarIndex) {
    similarIndex?.find(form.name, except = d.products.indexOfFirst { it.id == form.editingId }).orEmpty()
  }
  //  PRD-14: تغییرِ قیمت با نامِ کاربر و دلیل در سابقه (همان پرچم)
  val me = remember { runCatching { ir.vil3ntec.tohid.sync.SyncStore(context).accountName }.getOrDefault("").trim() }
  var priceReason by remember(state.editingId) { mutableStateOf("") }
  val priceChanged = form.editingId != null && (form.purchase != state.purchase || form.sale != state.sale)
  val context = androidx.compose.ui.platform.LocalContext.current
  val levelsOn = remember { ir.vil3ntec.tohid.data.ShopPolicy.stockLevels(context) }

  Dialog(onDismissRequest = onDismiss) {
    DialogEntry {
    Surface(color = Shop.colors.surfaceSolid, shape = RoundedCornerShape(Radius.lg), modifier = Modifier.fillMaxWidth()) {
      Column(Modifier.padding(18.dp).verticalScroll(rememberScrollState())) {
        Text(
          if (form.editingId == null) "کالای تازه" else "ویرایش محصول",
          style = MaterialTheme.typography.titleMedium,
          color = Shop.colors.text,
        )
        Spacer(Modifier.height(14.dp))

        OutlinedTextField(
          value = form.name,
          onValueChange = { form = form.copy(name = it) },
          label = { Text("نام کالا") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        //  هشدار، نه رد: نامِ عیناً یکسان را موتور رد می‌کند؛ این فقط «شاید همان کالاست»
        if (similar.isNotEmpty()) {
          Text(
            "نامِ مشابه در دکان: " + similar.joinToString("، ") { "«${it.name}»" } + " — اگر همان کالاست، ثبتِ دوباره نکنید.",
            style = MaterialTheme.typography.labelSmall,
            color = Shop.colors.warning,
          )
        }

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          NumberField(
            value = form.purchase,
            onValueChange = { form = form.copy(purchase = it) },
            label = "قیمت خرید",
            modifier = Modifier.weight(1f),
          )
          NumberField(
            value = form.sale,
            onValueChange = { form = form.copy(sale = it) },
            label = "قیمت فروش",
            modifier = Modifier.weight(1f),
          )
        }

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          NumberField(
            value = form.minStock,
            onValueChange = { form = form.copy(minStock = it) },
            label = "حد کم بودن",
            modifier = Modifier.weight(1f),
          )
          if (tools) {
            OutlinedTextField(
              value = form.barcodes,
              onValueChange = { form = form.copy(barcodes = it) },
              label = { Text("بارکدها (با «،» جدا)") },
              singleLine = true,
              modifier = Modifier.weight(1f),
            )
          } else {
            OutlinedTextField(
              value = form.barcode,
              onValueChange = { form = form.copy(barcode = it) },
              label = { Text("بارکد") },
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              modifier = Modifier.weight(1f),
            )
          }
        }

        if (tools && priceChanged) {
          Spacer(Modifier.height(10.dp))
          OutlinedTextField(
            value = priceReason,
            onValueChange = { priceReason = it },
            label = { Text("دلیلِ تغییرِ قیمت (با نامِ شما در سابقه می‌ماند)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
        }
        //  INV-12: نقطهٔ سفارش و بیشینه — فقط با تنظیمِ «نقطهٔ سفارش و بیشینه» (پیش‌فرض خاموش)
        if (levelsOn) {
          Spacer(Modifier.height(10.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(
              value = form.reorder,
              onValueChange = { form = form.copy(reorder = it) },
              label = "نقطهٔ سفارش",
              modifier = Modifier.weight(1f),
            )
            NumberField(
              value = form.max,
              onValueChange = { form = form.copy(max = it) },
              label = "بیشینهٔ موجودی",
              modifier = Modifier.weight(1f),
            )
          }
        }

        Spacer(Modifier.height(14.dp))
        ChipPicker(
          title = "واحد",
          options = d.productUnits,
          selected = form.unit,
          onSelect = { form = form.copy(unit = it) },
          placeholder = "واحد تازه",
        )

        Spacer(Modifier.height(14.dp))
        ChipPicker(
          title = "دسته‌بندی",
          options = d.productCategories,
          selected = form.category,
          onSelect = { form = form.copy(category = it) },
          placeholder = "دسته‌بندی تازه",
        )

        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
          value = form.subcategory,
          onValueChange = { form = form.copy(subcategory = it) },
          label = { Text("زیردسته (اختیاری)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        if (tools) {
          Spacer(Modifier.height(10.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
              value = form.brand,
              onValueChange = { form = form.copy(brand = it) },
              label = { Text("برند (اختیاری)") },
              singleLine = true,
              modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
              value = form.tags,
              onValueChange = { form = form.copy(tags = it) },
              label = { Text("برچسب‌ها (با «،» جدا)") },
              singleLine = true,
              modifier = Modifier.weight(1f),
            )
          }
        }

        //  واحدِ فروشِ دوم — مثلاً موجودی «کیلوگرم»، فروش «گرم» با ضریبِ ۰٫۰۰۱
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = form.saleUnit,
            onValueChange = { form = form.copy(saleUnit = it) },
            label = { Text("واحدِ فروشِ دوم (اختیاری)") },
            placeholder = { Text("گرم") },
            singleLine = true,
            modifier = Modifier.weight(1f),
          )
          NumberField(
            value = form.unitFactor,
            onValueChange = { form = form.copy(unitFactor = it) },
            label = if (form.unit.isBlank()) "ضریب" else "هر واحد = چند ${form.unit}",
            modifier = Modifier.weight(1f),
          )
        }

        if (infoOn) {
          Spacer(Modifier.height(14.dp))
          ir.vil3ntec.tohid.ui.components.product.ProductInfoFields(form.info, d, form.editingId) { form = form.copy(info = it) }
        }

        if (d.suppliers.isNotEmpty()) {
          Spacer(Modifier.height(14.dp))
          val names = d.suppliers.map { it.name }
          ChipPicker(
            title = "تأمین‌کننده (اختیاری)",
            options = names,
            selected = d.suppliers.find { it.id == form.supplierId }?.name.orEmpty(),
            onSelect = { name -> form = form.copy(supplierId = d.suppliers.find { it.name == name }?.id.orEmpty()) },
            placeholder = "",
            allowNew = false,
          )
        }

        if (form.editingId != null) {
          Spacer(Modifier.height(14.dp))
          Text("وضعیت در فروش", style = MaterialTheme.typography.labelMedium, color = Shop.colors.muted)
          Spacer(Modifier.height(6.dp))
          SegmentedChoice(
            options = listOf("on" to "فعال", "off" to "غیرفعال"),
            selected = if (form.active) "on" else "off",
            onSelect = { form = form.copy(active = it == "on") },
          )
        }

        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("انصراف") }
          Button(
            onClick = {
              onSave(
                WarehouseEngine.ProductDraft(
                  name = form.name,
                  category = form.category,
                  unit = form.unit,
                  purchasePrice = form.purchase.toDoubleOrNull() ?: 0.0,
                  salePrice = form.sale.toDoubleOrNull() ?: -1.0,
                  minStock = form.minStock.toDoubleOrNull() ?: 0.0,
                  barcode = form.barcode,
                  subcategory = form.subcategory,
                  supplierId = form.supplierId,
                  active = form.active,
                  saleUnit = form.saleUnit,
                  //  خالی یا نادرست ⇒ NaN تا موتور با پیامِ روشن ردش کند
                  unitFactor = if (form.saleUnit.isBlank()) 1.0 else form.unitFactor.toDoubleOrNull() ?: Double.NaN,
                  barcodes = if (tools) ProductBarcodes.parse(form.barcodes) else null,
                  changedBy = if (tools) me else null,
                  reason = if (tools) priceReason else null,
                  info = if (infoOn) form.info.toInfo(ir.vil3ntec.tohid.data.ProductInfoMap.refs(d), form.editingId) else null,
                  barcodes = if (tools) ProductBarcodes.parse(form.barcodes) else null,
                  changedBy = if (tools) me else null,
                  reason = if (tools) priceReason else null,
                  brand = if (tools) form.brand else null,
                  tags = if (tools) ProductLabels.tags(form.tags) else null,
                  //  خالی ⇒ پاک؛ نادرست ⇒ NaN تا موتور با پیامِ روشن ردش کند
                  editLevels = levelsOn,
                  reorderPoint = if (levelsOn) levelNumber(form.reorder) else null,
                  maxStock = if (levelsOn) levelNumber(form.max) else null,
                )
              )
            },
            modifier = Modifier.weight(1f),
          ) { Text(if (form.editingId == null) "ثبت" else "ذخیره تغییرات") }
        }
      }
    }
    }
  }
}

/** کادرِ حد: خالی ⇒ `null` (تعریف نشده)، عددِ نادرست ⇒ NaN (موتور پیامِ روشن می‌دهد) */
internal fun levelNumber(text: String): Double? = text.trim().takeIf { it.isNotEmpty() }?.let { it.toDoubleOrNull() ?: Double.NaN }

/* ========================= فرمِ ورودِ کالا ========================= */

@Composable
fun EntryDialog(
  d: ShopData,
  productId: String,
  onDismiss: () -> Unit,
  onSave: (WarehouseEngine.EntryDraft) -> Unit,
) {
  val product = d.products.find { it.id == productId }
  var cartons by remember { mutableStateOf("") }
  var perCarton by remember { mutableStateOf("") }
  var units by remember { mutableStateOf("") }
  var price by remember { mutableStateOf(product?.purchasePrice?.takeIf { it > 0 }?.toLong()?.toString() ?: "") }
  var notes by remember { mutableStateOf("") }
  var unit by remember { mutableStateOf(product?.unit.orEmpty()) }
  //  انقضا و سری ساخت — هر دو اختیاری؛ تاریخ خورشیدی یا میلادی تایپ می‌شود
  var expiry by remember { mutableStateOf("") }
  var batch by remember { mutableStateOf("") }
  var made by remember { mutableStateOf("") }
  //  موتورِ تازهٔ موجودی (روشن): هزینهٔ جانبی (PUR-06) و محل (INV-20)؛ خاموش ⇒ فرمِ امروز
  val stockOn = remember(d.auditLog, ir.vil3ntec.tohid.data.StockLedger.serverFlag) { ir.vil3ntec.tohid.data.StockLedger.isOn(d) }
  var landed by remember { mutableStateOf("") }
  var location by remember { mutableStateOf(ir.vil3ntec.tohid.data.StockActions.MAIN_LABEL) }
  val context = androidx.compose.ui.platform.LocalContext.current

  /*
   * «۴ کارتن × ۲۵ تا» یعنی ۱۰۰ واحد. حساب کردنش دستِ خودِ برنامه است تا
   * کسی وسطِ تحویلِ جنس ضرب نکند و اشتباه ننویسد — ولی عدد قابلِ عوض
   * کردن می‌ماند، چون کارتنِ ناقص هم پیش می‌آید.
   */
  val computed = (cartons.toDoubleOrNull() ?: 0.0) * (perCarton.toDoubleOrNull() ?: 0.0)
  LaunchedEffect(cartons, perCarton) {
    if (computed > 0) units = if (computed == Math.floor(computed)) computed.toLong().toString() else computed.toString()
  }

  Dialog(onDismissRequest = onDismiss) {
    DialogEntry {
    Surface(color = Shop.colors.surfaceSolid, shape = RoundedCornerShape(Radius.lg), modifier = Modifier.fillMaxWidth()) {
      Column(Modifier.padding(18.dp).verticalScroll(rememberScrollState())) {
        Text("ثبت ورود کالا", style = MaterialTheme.typography.titleMedium, color = Shop.colors.text)
        if (product != null) {
          Spacer(Modifier.height(4.dp))
          Text(product.name, style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
        }

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          NumberField(cartons, { cartons = it }, "تعداد کارتن", Modifier.weight(1f))
          NumberField(perCarton, { perCarton = it }, "در هر کارتن", Modifier.weight(1f))
        }

        if (computed > 0) {
          Spacer(Modifier.height(6.dp))
          Text(
            "${qty(computed)}${if (unit.isNotBlank()) " $unit" else ""} می‌شود",
            style = MaterialTheme.typography.labelSmall,
            color = Shop.colors.primary,
          )
        }

        Spacer(Modifier.height(10.dp))
        NumberField(units, { units = it }, "تعداد واحد", Modifier.fillMaxWidth())

        Spacer(Modifier.height(10.dp))
        NumberField(price, { price = it }, "قیمت خرید (هر واحد)", Modifier.fillMaxWidth())

        if (units.toDoubleOrNull() != null && price.toDoubleOrNull() != null) {
          Spacer(Modifier.height(6.dp))
          Text(
            "جمع: ${money(units.toDouble() * price.toDouble())} ؋",
            style = MaterialTheme.typography.labelSmall,
            color = Shop.colors.muted,
          )
        }

        Spacer(Modifier.height(14.dp))
        ChipPicker(
          title = "واحد اندازه‌گیری",
          options = d.productUnits,
          selected = unit,
          onSelect = { unit = it },
          placeholder = "واحد تازه",
        )

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = expiry,
            onValueChange = { expiry = it },
            label = { Text("تاریخ انقضا (اختیاری)") },
            placeholder = { Text("۱۴۰۶/۰۱/۳۰") },
            singleLine = true,
            modifier = Modifier.weight(1f),
          )
          OutlinedTextField(
            value = batch,
            onValueChange = { batch = it },
            label = { Text("سری ساخت (اختیاری)") },
            singleLine = true,
            modifier = Modifier.weight(1f),
          )
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
          value = made,
          onValueChange = { made = it },
          label = { Text("تاریخ تولید (اختیاری)") },
          placeholder = { Text("۱۴۰۵/۰۱/۱۵") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )

        if (stockOn) {
          Spacer(Modifier.height(10.dp))
          NumberField(landed, { landed = it }, "هزینهٔ جانبی (کرایه، گمرک) — اختیاری، کلِ همین ورود", Modifier.fillMaxWidth())
          Text(
            "به بهای همین کالا اضافه می‌شود (میانگینِ موزون)؛ جدا در «مصارف» ثبتش نکنید.",
            style = MaterialTheme.typography.labelSmall,
            color = Shop.colors.muted,
          )
          Spacer(Modifier.height(10.dp))
          ChipPicker(
            title = "محلِ نگه‌داری",
            options = ir.vil3ntec.tohid.data.StockLedger.locations(d).map { ir.vil3ntec.tohid.data.StockActions.locationLabel(it) },
            selected = location,
            onSelect = { location = it },
            placeholder = "محلِ تازه",
          )
        }

        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
          value = notes,
          onValueChange = { notes = it },
          label = { Text("توضیحات (اختیاری)") },
          modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("انصراف") }
          Button(
            onClick = {
              onSave(
                WarehouseEngine.EntryDraft(
                  productId = productId,
                  cartons = cartons.toDoubleOrNull() ?: 0.0,
                  perCarton = perCarton.toDoubleOrNull() ?: 0.0,
                  units = units.toDoubleOrNull() ?: 0.0,
                  unit = unit,
                  price = price.toDoubleOrNull() ?: -1.0,
                  notes = notes.trim(),
                  //  خالی ⇒ بی انقضا؛ تایپِ نادرست ⇒ موتور با پیامِ روشن ردش می‌کند
                  expiryDate = if (expiry.isBlank()) "" else ir.vil3ntec.tohid.Jalali.parseTyped(expiry) ?: "?",
                  batch = batch.trim(),
                  productionDate = if (made.isBlank()) "" else ir.vil3ntec.tohid.Jalali.parseTyped(made) ?: "?",
                  //  خاموش ⇒ null: ردیف دقیقاً مثلِ امروز
                  landedCost = if (!stockOn) null else if (landed.isBlank()) 0.0 else landed.toDoubleOrNull() ?: -1.0,
                  locationId = if (!stockOn) null else ir.vil3ntec.tohid.data.StockActions.locationOf(location),
                  userId = if (!stockOn) null else runCatching { ir.vil3ntec.tohid.sync.SyncStore(context).accountName }.getOrNull(),
                )
              )
            },
            modifier = Modifier.weight(1f),
          ) { Text("ذخیره") }
        }
      }
    }
    }
  }
}

/* ======================== اصلاحِ موجودی ======================== */

@Composable
fun AdjustDialog(
  d: ShopData,
  productId: String,
  onDismiss: () -> Unit,
  /** آخری: نوعِ اصلاح (INV-04) وقتی موتورِ تازهٔ موجودی روشن است؛ خاموش ⇒ null */
  onSave: (Double, Boolean, String, WarehouseEngine.AdjustKind, String?) -> Unit,
) {
  val product = d.products.find { it.id == productId }
  val stockOn = remember(d.auditLog, ir.vil3ntec.tohid.data.StockLedger.serverFlag) { ir.vil3ntec.tohid.data.StockLedger.isOn(d) }
  var adjKind by remember { mutableStateOf("other") }
  var kind by remember { mutableStateOf(WarehouseEngine.AdjustKind.ADJUSTMENT) }
  var increase by remember { mutableStateOf(true) }
  var amount by remember { mutableStateOf("") }
  var reason by remember { mutableStateOf("") }

  Dialog(onDismissRequest = onDismiss) {
    DialogEntry {
    Surface(color = Shop.colors.surfaceSolid, shape = RoundedCornerShape(Radius.lg), modifier = Modifier.fillMaxWidth()) {
      Column(Modifier.padding(18.dp).verticalScroll(rememberScrollState())) {
        Text(
          if (kind == WarehouseEngine.AdjustKind.SUPPLIER_RETURN) "برگشت به تأمین‌کننده" else "اصلاح موجودی",
          style = MaterialTheme.typography.titleMedium,
          color = Shop.colors.text,
        )
        if (product != null) {
          Spacer(Modifier.height(4.dp))
          Text(product.name, style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
        }

        Spacer(Modifier.height(14.dp))
        Segment(
          options = listOf(
            "اصلاح موجودی" to WarehouseEngine.AdjustKind.ADJUSTMENT,
            "برگشت به تأمین‌کننده" to WarehouseEngine.AdjustKind.SUPPLIER_RETURN,
          ),
          selected = kind,
          onSelect = { kind = it },
        )

        Spacer(Modifier.height(12.dp))
        Segment(
          options = listOf("زیاد شد" to true, "کم شد" to false),
          selected = increase,
          onSelect = { increase = it },
        )

        if (stockOn && kind == WarehouseEngine.AdjustKind.ADJUSTMENT) {
          Spacer(Modifier.height(12.dp))
          Text("نوعِ اصلاح", style = MaterialTheme.typography.labelMedium, color = Shop.colors.muted)
          Spacer(Modifier.height(6.dp))
          Segment(
            options = ir.vil3ntec.tohid.data.StockActions.ADJ_KINDS.map { it.second to it.first },
            selected = adjKind,
            onSelect = { adjKind = it },
          )
          Text("خراب و منقضی در گزارشِ ضایعات جمع می‌شوند.", style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
        }

        Spacer(Modifier.height(12.dp))
        NumberField(amount, { amount = it }, "مقدار", Modifier.fillMaxWidth())

        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
          value = reason,
          onValueChange = { reason = it },
          label = { Text("دلیل") },
          supportingText = {
            Text("بدون دلیل، فردا معلوم نمی‌شود جنس کجا رفته.", style = MaterialTheme.typography.labelSmall)
          },
          modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("انصراف") }
          Button(
            onClick = { onSave(amount.toDoubleOrNull() ?: 0.0, increase, reason, kind, if (stockOn) adjKind else null) },
            modifier = Modifier.weight(1f),
          ) { Text("ثبت") }
        }
      }
    }
    }
  }
}

/* ============================ ریزه‌کاری ============================ */


@Composable
private fun <T> Segment(options: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit) {
  Row(
    Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(Radius.sm))
      .background(Shop.colors.surface2)
      .padding(3.dp),
  ) {
    options.forEach { (label, value) ->
      val active = value == selected
      Box(
        Modifier
          .weight(1f)
          .clip(RoundedCornerShape(Radius.sm))
          .background(if (active) Shop.colors.primary else Color.Transparent)
          .clickable { onSelect(value) }
          .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          label,
          style = MaterialTheme.typography.labelMedium,
          color = if (active) Color.White else Shop.colors.muted,
        )
      }
    }
  }
}
