package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.data.PhotoStore
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.WarehouseEngine
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.plain
import ir.vil3ntec.tohid.qty
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.pressMenu
import ir.vil3ntec.tohid.ui.theme.Radius
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.launch

/**
 *  محصولات — همان صفحه‌ای که نسخهٔ وب دارد.
 *
 *  انبار دربارهٔ «چقدر داریم» است؛ اینجا دربارهٔ «چه داریم و به چند»:
 *  فهرست کالاها با قیمت خرید و فروش، سود هر قلم، دسته‌بندی و جستجو.
 *  ثبت و ویرایش و حذف هم از همین‌جا انجام می‌شود.
 *
 *  قاعده‌ها همان قاعده‌های `WarehouseEngine` هستند و اینجا تکرار نشده‌اند،
 *  وگرنه یک روز دو جا دو جور حساب می‌کردند.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductsScreen(
  store: ShopStore,
  d: ShopData,
  snackbar: SnackbarHostState,
  onOpenWarehouse: (String) -> Unit = {},
  onOpenProduct: (String) -> Unit = {},
) {
  val scope = rememberCoroutineScope()

  var search by rememberSaveable { mutableStateOf("") }
  var category by rememberSaveable { mutableStateOf<String?>(null) }
  //  مرتب‌سازی — همان `product-sort`ِ وب
  var sort by rememberSaveable { mutableStateOf("name") }
  var productForm by remember { mutableStateOf<ProductFormState?>(null) }
  var bulkProduct by remember { mutableStateOf(false) }
  var actionsFor by remember { mutableStateOf<Product?>(null) }
  var confirmDelete by remember { mutableStateOf<Product?>(null) }
  // محصولی که منتظرِ عکس است، و شمارنده‌ای که کارت‌ها را تازه می‌کند
  var photoFor by remember { mutableStateOf<String?>(null) }
  var photoVersion by remember { mutableStateOf(0) }
  val context = LocalContext.current

  /*
   *  عکس از دوربین یا از گالری — برگهٔ انتخاب همان را می‌پرسد.
   *
   *  تا دیروز یک‌راست گالری باز می‌شد؛ فروشنده‌ای که جنسِ تازه روی
   *  پیشخوان داشت، باید از برنامه بیرون می‌رفت تا عکس بگیرد.
   */
  //  عکسِ انتخاب‌شده اول به استودیو می‌رود (زمینهٔ سفید، برش، کیفیت)
  var studio by remember { mutableStateOf<Pair<String, android.net.Uri>?>(null) }
  PhotoSourceSheet(
    open = photoFor != null,
    onDismiss = { photoFor = null },
  ) { uri ->
    val id = photoFor ?: return@PhotoSourceSheet
    studio = id to uri
  }
  studio?.let { (id, uri) ->
    PhotoStudioDialog(source = uri, onDismiss = { studio = null }) { bitmap ->
      studio = null
      PhotoStore.saveBitmap(context, id, bitmap)
        .onSuccess {
          // نشانهٔ عکس روی خودِ محصول می‌نشیند، همان فیلدی که وب می‌نویسد
          scope.launch {
            store.save(d.copy(products = d.products.map { if (it.id == id) it.copy(photo = true) else it }))
          }
          //  همگام‌سازیِ امروز + بانکِ تصاویرِ روشن ⇒ عکسِ خودِ دکان به بانکِ همین دکان (در صف)
          ir.vil3ntec.tohid.data.ImageBank.photoSaved(context, id, own = true, scope = scope)
          photoVersion++
          scope.launch { snackbar.showSnackbar("عکس محصول ثبت شد") }
        }
        .onFailure { scope.launch { snackbar.showSnackbar("عکس ذخیره نشد") } }
    }
  }

  fun toast(text: String) {
    scope.launch { snackbar.showSnackbar(text) }
  }

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

  //  صفحهٔ پهن (کامپیوتر): جدولِ مرتب‌شونده با انتخابِ چندتایی، به‌جای کارت
  val wide = screenSize() == Screen.WIDE
  val stockIdx = remember(d) { ShopStore.index(d) }
  val tableColumns = remember(stockIdx) {
    listOf(
      TableColumn<Product>("نام", 240.dp, sortKey = { it.name }) { it.name + if (!it.active) " (غیرفعال)" else "" },
      TableColumn<Product>("دسته", 130.dp, sortKey = { it.category }) { it.category },
      TableColumn<Product>("موجودی", 110.dp, sortKey = { stockIdx.stock(it.id) }, numeric = true,
        tint = { p -> if (stockIdx.status(p) == "out") Shop.colors.danger else Shop.colors.text }) {
        "${ir.vil3ntec.tohid.qty(stockIdx.stock(it.id))} ${it.unit}"
      },
      TableColumn<Product>("قیمت خرید", 110.dp, sortKey = { it.purchasePrice }, numeric = true) { money(it.purchasePrice) },
      TableColumn<Product>("قیمت فروش", 110.dp, sortKey = { it.salePrice }, numeric = true) { money(it.salePrice) },
      TableColumn<Product>("وضعیت", 110.dp, sortKey = { stockIdx.status(it) }) {
        when (stockIdx.status(it)) { "out" -> "تمام‌شده"; "low" -> "موجودی کم"; else -> "کافی" }
      },
    )
  }
  val table = rememberTableState(tableColumns)
  var bulkCategory by remember { mutableStateOf(false) }
  var bulkPrice by remember { mutableStateOf(false) }
  val productTools = remember { ir.vil3ntec.tohid.data.ShopPolicy.productTools(context) }
  var bulkDelete by remember { mutableStateOf(false) }

  val term = search.trim()
  //  فیلتر و دو جمعِ انبار، یک بار به ازای هر دفتر و هر عبارتِ جستجو —
  //  نه در هر بار کشیده شدنِ صفحه
  //  PRD-02 (پشتِ `ProductInfoPrefs`، پیش‌فرض خاموش): نامِ پشتو/انگلیسی، مترادف و واژهٔ جست‌وجو هم
  val infoSearch = ir.vil3ntec.tohid.ui.components.product.productInfoOn()
  val shown = remember(d, term, category, sort, infoSearch) {
    val stock = ShopStore.index(d)
    val list = d.products.filter { p ->
      (category == null || p.category == category) &&
        (term.isBlank() || p.name.contains(term, ignoreCase = true) ||
          p.category.contains(term, ignoreCase = true) ||
          p.subcategory.contains(term, ignoreCase = true) ||
          p.barcodes.any { it.contains(term) } ||
          (infoSearch && ir.vil3ntec.tohid.data.ProductInfoMap.matches(p, term)))
    }
    when (sort) {
      "stock_asc" -> list.sortedBy { stock.stock(it.id) }
      "stock_desc" -> list.sortedByDescending { stock.stock(it.id) }
      "price_desc" -> list.sortedByDescending { it.salePrice }
      "newest" -> list.sortedByDescending { it.createdAt }
      else -> list.sortedBy { it.name }
    }
  }

  // ارزش انبار به دو قیمت، و سودی که اگر همه فروخته شود به دست می‌آید
  val (buyValue, sellValue) = remember(d) {
    val stock = ShopStore.index(d)
    var buy = 0.0
    var sell = 0.0
    d.products.forEach {
      val left = stock.stock(it.id)
      buy += left * it.purchasePrice
      sell += left * it.salePrice
    }
    buy to sell
  }

  Box(Modifier.fillMaxSize()) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp)) {
      item {
        Text(
          "کالاهای فروشگاه، قیمت‌ها و سود هر قلم",
          style = MaterialTheme.typography.bodySmall,
          color = Shop.colors.muted,
        )
        Spacer(Modifier.height(14.dp))
      }

      /* ------------------------- خلاصه ------------------------- */
      item {
        /*
         *  هر چهار عدد در یک ردیف.
         *
         *  دو در دو بودنشان دو ایراد داشت: نصفِ هر کاشی خالی می‌ماند —
         *  چون عدد کوتاه است و کاشی به‌اندازهٔ نصفِ صفحه پهن — و چهار
         *  عددی که با هم معنی دارند، دو طبقه می‌شدند و چشم باید بالا و
         *  پایین می‌رفت. یک ردیف، هم جای کمتری می‌گیرد هم یکجا خوانده
         *  می‌شود.
         *
         *  روی گوشی سهمِ هر کاشی حدودِ هشتاد نقطه است، پس کاشی‌ها
         *  جمع‌وجور می‌شوند؛ `IntrinsicSize.Min` هم هر چهار را هم‌قد
         *  نگه می‌دارد تا برچسبِ دوخطی، کاشیِ خودش را بلندتر نکند.
         */
        val tight = !isTablet()
        Row(
          Modifier.height(IntrinsicSize.Min),
          horizontalArrangement = Arrangement.spacedBy(if (tight) 7.dp else 10.dp),
        ) {
          StatTile(
            label = "قلم کالا",
            value = d.products.size.toString(),
            hint = if (category == null) "همهٔ دسته‌ها" else category,
            modifier = Modifier.weight(1f).fillMaxHeight(),
            compact = tight,
          )
          StatTile(
            label = "قیمت خرید",
            value = money(buyValue),
            tint = Shop.colors.warning,
            hint = "؋",
            modifier = Modifier.weight(1f).fillMaxHeight(),
            compact = tight,
          )
          StatTile(
            label = "قیمت فروش",
            value = money(sellValue),
            hint = "؋",
            modifier = Modifier.weight(1f).fillMaxHeight(),
            compact = tight,
          )
          StatTile(
            label = "سود بالقوه",
            value = money(sellValue - buyValue),
            tint = Shop.colors.success,
            // روی گوشی «اگر همه فروخته شود» در هشتاد نقطه جا نمی‌شود و
            // سه‌نقطه می‌خورد؛ آنجا همان واحد نوشته می‌شود
            hint = if (tight) "؋" else "اگر همه فروخته شود",
            modifier = Modifier.weight(1f).fillMaxHeight(),
            compact = tight,
          )
        }
        Spacer(Modifier.height(14.dp))
      }

      /* ------------------------- جستجو و دسته ------------------------- */
      item {
        VoiceSearchField(
          value = search,
          onValueChange = { search = it },
          label = "جستجوی نام، دسته یا بارکد",
        )
        Spacer(Modifier.height(10.dp))
        Row(
          Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          listOf(
            "name" to "نام", "stock_asc" to "کم‌موجودترین", "stock_desc" to "بیشترین موجودی",
            "price_desc" to "گران‌ترین", "newest" to "تازه‌ترین",
          ).forEach { (id, label) ->
            FilterChip(selected = sort == id, onClick = { sort = id }, label = { Text(label) })
          }
        }
        if (d.productCategories.isNotEmpty()) {
          Spacer(Modifier.height(10.dp))
          CategoryFilter(
            categories = d.productCategories,
            selected = category,
            onSelect = { category = it },
          )
        }
        Spacer(Modifier.height(14.dp))
      }

      /* ------------------------- فهرست ------------------------- */
      if (shown.isEmpty()) {
        item {
          Panel {
            if (d.products.isEmpty()) {
              TohidEmptyState(
                icon = Icons.Filled.ShoppingBag,
                title = "هنوز کالایی ثبت نشده",
                description = "کالاهای دکان را یک‌بار ثبت کنید تا در فروش، انبار و گزارش‌ها بیایند.",
                actionText = "ثبت محصول",
                onAction = { bulkProduct = true },
              )
            } else {
              TohidEmptyState(
                icon = Icons.Filled.Search,
                title = "چیزی پیدا نشد",
                description = "کالایی مطابق این جستجو یا فیلتر نیست. عبارت دیگری را امتحان کنید.",
              )
            }
          }
        }
      } else if (wide) {
        //  نوارِ کارِ گروهی — فقط وقتی چیزی انتخاب شده
        if (table.selected.isNotEmpty()) item(key = "bulk-bar") {
          Row(
            Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text("${plain(table.selected.size)} کالا انتخاب شد", style = MaterialTheme.typography.titleSmall,
              color = Shop.colors.text, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = {
              apply(WarehouseEngine.bulkUpdate(d, table.selected, todayIso(), System.currentTimeMillis(), ::newId) { it.copy(active = true) },
                "فعال شد") { table.selected = emptySet() }
            }) { Text("فعال") }
            OutlinedButton(onClick = {
              apply(WarehouseEngine.bulkUpdate(d, table.selected, todayIso(), System.currentTimeMillis(), ::newId) { it.copy(active = false) },
                "غیرفعال شد") { table.selected = emptySet() }
            }) { Text("غیرفعال") }
            OutlinedButton(onClick = { bulkCategory = true }) { Text("تغییر دسته") }
            //  PRD-21 (پیش‌فرض خاموش): قیمتِ گروهی با پیش‌نمایش، هشدار، تأیید و دلیل
            if (productTools) OutlinedButton(onClick = { bulkPrice = true }) { Text("قیمت") }
            OutlinedButton(onClick = { bulkDelete = true }) { Text("حذف", color = Shop.colors.danger) }
            TextButton(onClick = { table.selected = emptySet() }) { Text("لغو انتخاب") }
          }
        }
        dataTable(
          rows = sortRows(shown, tableColumns, table),
          columns = tableColumns,
          state = table,
          key = { it.id },
          onOpen = { onOpenProduct(it.id) },
          onMenu = { actionsFor = it },
        )
      } else {
        itemsIndexed(shown, key = { _, p -> p.id }) { index, p ->
          StaggeredItem(index) {
            ProductCard(
              d = d,
              product = p,
              photoVersion = photoVersion,
              onClick = { onOpenProduct(p.id) },
              onLongClick = { actionsFor = p },
              onPhoto = { photoFor = p.id },
            )
          }
          Spacer(Modifier.height(10.dp))
        }
      }
    }

    AddFab(
      label = "محصول جدید",
      containerColor = Shop.colors.button.primary.bg,
      onClick = { bulkProduct = true },
      modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).popIn(),
    )
  }

  /* ------------------------- کادرها ------------------------- */

  // «محصول جدید» در وب همین شیتِ چندردیفی است، نه فرمِ تکی
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
        }
      },
    )
  }

  if (bulkPrice) {
    BulkPriceDialog(d, table.selected, onDismiss = { bulkPrice = false }) { r ->
      apply(r, "قیمتِ ${plain(table.selected.size)} کالا عوض شد") { bulkPrice = false; table.selected = emptySet() }
    }
  }

  if (bulkCategory) {
    var cat by remember { mutableStateOf("") }
    AlertDialog(
      onDismissRequest = { bulkCategory = false },
      containerColor = Shop.colors.surfaceSolid,
      title = { Text("دستهٔ ${plain(table.selected.size)} کالا", color = Shop.colors.text) },
      text = {
        OutlinedTextField(value = cat, onValueChange = { cat = it }, singleLine = true,
          label = { Text("دستهٔ تازه") }, modifier = Modifier.fillMaxWidth())
      },
      confirmButton = {
        TextButton(onClick = {
          apply(WarehouseEngine.bulkUpdate(d, table.selected, todayIso(), System.currentTimeMillis(), ::newId) { it.copy(category = cat.trim()) },
            "دسته عوض شد") { table.selected = emptySet(); bulkCategory = false }
        }) { Text("ثبت") }
      },
      dismissButton = { TextButton(onClick = { bulkCategory = false }) { Text("انصراف") } },
    )
  }

  if (bulkDelete) {
    AlertDialog(
      onDismissRequest = { bulkDelete = false },
      containerColor = Shop.colors.surfaceSolid,
      title = { Text("حذفِ ${plain(table.selected.size)} کالا؟", color = Shop.colors.text) },
      text = {
        Text("کالاها و سوابقِ ورودشان از انبار حذف می‌شوند. فاکتورهای قبلی می‌مانند ولی نامِ کالا در آن‌ها «حذف‌شده» نشان داده می‌شود. برگشت ندارد.",
          style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
      },
      confirmButton = {
        TextButton(onClick = {
          apply(WarehouseEngine.bulkDelete(d, table.selected, todayIso(), System.currentTimeMillis(), ::newId),
            "${plain(table.selected.size)} کالا حذف شد") { table.selected = emptySet(); bulkDelete = false }
        }) { Text("حذف", color = Shop.colors.danger) }
      },
      dismissButton = { TextButton(onClick = { bulkDelete = false }) { Text("بازگشت") } },
    )
  }

  actionsFor?.let { p ->
    AdaptiveSheet(
      onDismissRequest = { actionsFor = null },
      containerColor = Shop.colors.surfaceSolid,
    ) {
      Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 28.dp)) {
        Text(p.name, style = MaterialTheme.typography.titleMedium, color = Shop.colors.text)
        Text(
          "${money(p.salePrice)} ؋" + if (p.category.isNotBlank()) " — ${p.category}" else "",
          style = MaterialTheme.typography.bodySmall,
          color = Shop.colors.muted,
        )
        Spacer(Modifier.height(16.dp))

        SheetAction(Icons.Filled.Edit, "ویرایش محصول") {
          productForm = ProductFormState.of(p)
          actionsFor = null
        }
        SheetAction(Icons.Filled.Image, "انتخاب عکس محصول") {
          photoFor = p.id
          actionsFor = null
        }
        SheetAction(Icons.Filled.Inventory2, "دیدن در انبار") {
          val id = p.id
          actionsFor = null
          onOpenWarehouse(id)
        }
        SheetAction(Icons.Filled.DeleteOutline, "حذف محصول", tint = Shop.colors.danger) {
          confirmDelete = p
          actionsFor = null
        }
      }
    }
  }

  confirmDelete?.let { p ->
    AlertDialog(
      onDismissRequest = { confirmDelete = null },
      containerColor = Shop.colors.surfaceSolid,
      title = { Text("حذف «${p.name}»؟", color = Shop.colors.text) },
      text = { Text(WarehouseEngine.deleteWarning(d, p.id), color = Shop.colors.muted) },
      confirmButton = {
        TextButton(onClick = {
          apply(
            WarehouseEngine.deleteProduct(d, p.id, todayIso(), System.currentTimeMillis(), ::newId),
            "محصول حذف شد",
          ) {
            PhotoStore.delete(context, p.id)
            ir.vil3ntec.tohid.sync.PhotoSync.removed(context, p.id)
            confirmDelete = null
          }
        }) { Text("حذف", color = Shop.colors.danger) }
      },
      dismissButton = {
        TextButton(onClick = { confirmDelete = null }) { Text("بازگشت", color = Shop.colors.muted) }
      },
    )
  }
}

/* ============================ کارت کالا ============================ */

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProductCard(
  d: ShopData,
  product: Product,
  photoVersion: Int,
  onClick: () -> Unit,
  onLongClick: () -> Unit,
  onPhoto: () -> Unit,
) {
  val stock = ShopStore.stock(d, product.id)
  val status = ShopStore.stockStatus(d, product)
  //  کالای غیرفعال پیش از هر چیز همین را نشان می‌دهد — در فروش نمی‌آید
  val tint = when {
    !product.active -> Shop.colors.muted
    status == "out" -> Shop.colors.danger
    status == "low" -> Shop.colors.warning
    else -> Shop.colors.success
  }
  val label = when {
    !product.active -> "غیرفعال"
    status == "out" -> "تمام‌شده"
    status == "low" -> "موجودی کم"
    else -> "موجودی کافی"
  }
  val profit = product.salePrice - product.purchasePrice

  Column(
    Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(Radius.md))
      .background(Shop.colors.surface)
      .border(1.dp, Shop.colors.border, RoundedCornerShape(Radius.md))
      .pressMenu(onClick = onClick, onMenu = onLongClick)
      .padding(14.dp)
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      /*
       *  عکس، خودش دکمهٔ عکس گرفتن است.
       *
       *  تا حالا این کار فقط پشتِ نگه‌داشتنِ انگشت روی کارت بود؛ کسی که
       *  نمی‌داند باید نگه دارد، هیچ‌وقت پیدایش نمی‌کند. حالا همان‌جا که
       *  عکس نیست، نشانِ دوربین هست و یک لمس کافی است.
       */
      Box(contentAlignment = Alignment.BottomEnd) {
        ProductPhoto(
          product.id,
          size = 52.dp,
          version = photoVersion,
          modifier = Modifier.clickable(onClick = onPhoto),
        )
        Box(
          Modifier
            .offset(x = (-3).dp, y = (-3).dp)
            .size(18.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(Shop.colors.button.primary.bg),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            Icons.Filled.PhotoCamera,
            contentDescription = "عکس محصول",
            tint = Shop.colors.button.primary.text,
            modifier = Modifier.size(11.dp),
          )
        }
      }
      Spacer(Modifier.width(12.dp))
      Column(Modifier.weight(1f)) {
        Text(product.name, style = MaterialTheme.typography.titleSmall, color = Shop.colors.text)
        Spacer(Modifier.height(3.dp))
        Text(
          "${money(product.salePrice)} ؋",
          style = MaterialTheme.typography.titleMedium,
          color = Shop.colors.primary,
          fontWeight = FontWeight.Bold,
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

    Spacer(Modifier.height(10.dp))
    /*
     *  سه کادرِ خرید، فایده و دسته — هم‌اندازه و کشیده تا کلِ عرضِ کارت.
     *
     *  تا حالا هرکدام به‌اندازهٔ متنِ خودش بود و کنارِ هم به یک لبه
     *  می‌چسبیدند؛ باقیِ ردیف خالی می‌ماند و سه کادرِ ناهم‌قواره،
     *  کارت را نامرتب نشان می‌داد. حالا با `weight` هر سه یک پهنا
     *  دارند و متنشان وسط است.
     */
    Row(
      Modifier.fillMaxWidth().height(IntrinsicSize.Min),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      CardChip("خرید", money(product.purchasePrice), Shop.colors.muted, Modifier.weight(1f))
      CardChip(
        "فایده",
        money(profit),
        if (profit < 0) Shop.colors.danger else Shop.colors.success,
        Modifier.weight(1f),
      )
      CardChip(
        "دسته",
        product.category.ifBlank { "—" },
        Shop.colors.muted,
        Modifier.weight(1f),
      )
    }
  }
}

@Composable
private fun CardChip(label: String, value: String, tint: Color, modifier: Modifier = Modifier) {
  Column(
    modifier
      .fillMaxHeight()
      .clip(RoundedCornerShape(Radius.sm))
      .background(Shop.colors.surface2)
      .padding(horizontal = 8.dp, vertical = 7.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Text(
      label,
      style = MaterialTheme.typography.labelSmall,
      color = Shop.colors.muted2,
      maxLines = 1,
      textAlign = TextAlign.Center,
    )
    Text(
      value,
      style = MaterialTheme.typography.labelMedium,
      color = tint,
      fontWeight = FontWeight.Bold,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      textAlign = TextAlign.Center,
    )
  }
}

@Composable
private fun SheetAction(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  text: String,
  tint: Color = Shop.colors.text,
  onClick: () -> Unit,
) {
  Row(
    Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(Radius.md))
      .clickable(onClick = onClick)
      .padding(vertical = 14.dp, horizontal = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(icon, contentDescription = null, tint = tint)
    Spacer(Modifier.width(12.dp))
    Text(text, style = MaterialTheme.typography.bodyLarge, color = tint)
  }
}
