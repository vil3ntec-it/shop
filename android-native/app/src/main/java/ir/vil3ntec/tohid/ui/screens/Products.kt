package ir.vil3ntec.tohid.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
  var productForm by remember { mutableStateOf<ProductFormState?>(null) }
  var bulkProduct by remember { mutableStateOf(false) }
  var actionsFor by remember { mutableStateOf<Product?>(null) }
  var confirmDelete by remember { mutableStateOf<Product?>(null) }
  // محصولی که منتظرِ عکس است، و شمارنده‌ای که کارت‌ها را تازه می‌کند
  var photoFor by remember { mutableStateOf<String?>(null) }
  var photoVersion by remember { mutableStateOf(0) }
  val context = LocalContext.current

  val pickPhoto = rememberLauncherForActivityResult(
    ActivityResultContracts.PickVisualMedia()
  ) { uri ->
    val id = photoFor
    photoFor = null
    if (uri == null || id == null) return@rememberLauncherForActivityResult
    PhotoStore.save(context, id, uri)
      .onSuccess {
        // نشانهٔ عکس روی خودِ محصول می‌نشیند، همان فیلدی که وب می‌نویسد
        scope.launch {
          store.save(d.copy(products = d.products.map { if (it.id == id) it.copy(photo = true) else it }))
        }
        photoVersion++
        scope.launch { snackbar.showSnackbar("عکس محصول ثبت شد") }
      }
      .onFailure { scope.launch { snackbar.showSnackbar("عکس ذخیره نشد") } }
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

  val term = search.trim()
  val shown = d.products.filter { p ->
    (category == null || p.category == category) &&
      (term.isBlank() || p.name.contains(term, ignoreCase = true) ||
        p.category.contains(term, ignoreCase = true) ||
        p.barcodes.any { it.contains(term) })
  }

  // ارزش انبار به دو قیمت، و سودی که اگر همه فروخته شود به دست می‌آید
  val buyValue = d.products.sumOf { ShopStore.stock(d, it.id) * it.purchasePrice }
  val sellValue = d.products.sumOf { ShopStore.stock(d, it.id) * it.salePrice }

  Box(Modifier.fillMaxSize()) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp)) {
      item {
        Text("محصولات", style = MaterialTheme.typography.headlineMedium, color = Shop.colors.text)
        Text(
          "کالاهای فروشگاه، قیمت‌ها و سود هر قلم",
          style = MaterialTheme.typography.bodySmall,
          color = Shop.colors.muted,
        )
        Spacer(Modifier.height(14.dp))
      }

      /* ------------------------- خلاصه ------------------------- */
      item {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          StatTile(
            label = "قلم کالا",
            value = d.products.size.toString(),
            hint = if (category == null) "همهٔ دسته‌ها" else category,
            modifier = Modifier.weight(1f),
          )
          StatTile(
            label = "ارزش به قیمت خرید",
            value = money(buyValue),
            tint = Shop.colors.warning,
            hint = "افغانی",
            modifier = Modifier.weight(1f),
          )
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          StatTile(
            label = "ارزش به قیمت فروش",
            value = money(sellValue),
            hint = "افغانی",
            modifier = Modifier.weight(1f),
          )
          StatTile(
            label = "سود بالقوه",
            value = money(sellValue - buyValue),
            tint = Shop.colors.success,
            hint = "اگر همه فروخته شود",
            modifier = Modifier.weight(1f),
          )
        }
        Spacer(Modifier.height(14.dp))
      }

      /* ------------------------- جستجو و دسته ------------------------- */
      item {
        OutlinedTextField(
          value = search,
          onValueChange = { search = it },
          label = { Text("جستجوی نام، دسته یا بارکد") },
          leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
          singleLine = true,
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
          modifier = Modifier.fillMaxWidth(),
        )
        if (d.productCategories.isNotEmpty()) {
          Spacer(Modifier.height(10.dp))
          Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            FilterChip(
              selected = category == null,
              onClick = { category = null },
              label = { Text("همه") },
            )
            d.productCategories.forEach { c ->
              FilterChip(
                selected = category == c,
                onClick = { category = if (category == c) null else c },
                label = { Text(c) },
              )
            }
          }
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
      } else {
        itemsIndexed(shown, key = { _, p -> p.id }) { index, p ->
          StaggeredItem(index) {
            ProductCard(
              d = d,
              product = p,
              photoVersion = photoVersion,
              onClick = { onOpenProduct(p.id) },
              onLongClick = { actionsFor = p },
              onPhoto = {
                photoFor = p.id
                pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
              },
            )
          }
          Spacer(Modifier.height(10.dp))
        }
      }
    }

    ExtendedFloatingActionButton(
      onClick = { bulkProduct = true },
      containerColor = Shop.colors.primary,
      contentColor = Color.White,
      icon = { Icon(Icons.Filled.Add, contentDescription = null) },
      text = { Text("محصول جدید") },
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

  actionsFor?.let { p ->
    ModalBottomSheet(
      onDismissRequest = { actionsFor = null },
      containerColor = Shop.colors.surface,
    ) {
      Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 28.dp)) {
        Text(p.name, style = MaterialTheme.typography.titleMedium, color = Shop.colors.text)
        Text(
          "${money(p.salePrice)} افغانی" + if (p.category.isNotBlank()) " — ${p.category}" else "",
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
          pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
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
      containerColor = Shop.colors.surface,
      title = { Text("حذف «${p.name}»؟", color = Shop.colors.text) },
      text = { Text(WarehouseEngine.deleteWarning(d, p.id), color = Shop.colors.muted) },
      confirmButton = {
        TextButton(onClick = {
          apply(
            WarehouseEngine.deleteProduct(d, p.id, todayIso(), System.currentTimeMillis(), ::newId),
            "محصول حذف شد",
          ) {
            PhotoStore.delete(context, p.id)
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
  val profit = product.salePrice - product.purchasePrice

  Column(
    Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(Radius.md))
      .background(Shop.colors.surface)
      .border(1.dp, Shop.colors.border, RoundedCornerShape(Radius.md))
      .combinedClickable(onClick = onClick, onLongClick = onLongClick)
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
            .background(Shop.colors.primary),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            Icons.Filled.PhotoCamera,
            contentDescription = "عکس محصول",
            tint = Color.White,
            modifier = Modifier.size(11.dp),
          )
        }
      }
      Spacer(Modifier.width(12.dp))
      Column(Modifier.weight(1f)) {
        Text(product.name, style = MaterialTheme.typography.titleSmall, color = Shop.colors.text)
        Spacer(Modifier.height(3.dp))
        Text(
          "${money(product.salePrice)} افغانی",
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
    Row(
      Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      CardChip("خرید", "${money(product.purchasePrice)}", Shop.colors.muted)
      CardChip("سود هر واحد", money(profit), if (profit < 0) Shop.colors.danger else Shop.colors.success)
      if (product.category.isNotBlank()) CardChip("دسته", product.category, Shop.colors.muted)
    }
  }
}

@Composable
private fun CardChip(label: String, value: String, tint: Color) {
  Column(
    Modifier
      .clip(RoundedCornerShape(Radius.sm))
      .background(Shop.colors.surface2)
      .padding(horizontal = 10.dp, vertical = 6.dp)
  ) {
    Text(label, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
    Text(value, style = MaterialTheme.typography.labelMedium, color = tint, fontWeight = FontWeight.Bold)
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
