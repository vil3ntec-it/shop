package ir.vil3ntec.tohid.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.data.CartStore
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.WarehouseEngine
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.qty
import ir.vil3ntec.tohid.scan.ScanFeedback
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.theme.Radius
import ir.vil3ntec.tohid.ui.theme.Shop
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

/**
 *  فروش سریع — «انتخاب محصول» نسخهٔ وب، به‌صورت یک صفحهٔ کامل.
 *
 *  چرا صفحهٔ جدا و نه همان کادرِ قبلی: فروشندهٔ دکانی که بارکد ندارد —
 *  و بیشترشان ندارند — تمامِ فروشش از همین‌جاست. کادرِ وسطِ صفحه جا کم
 *  می‌آورد، عکسِ کالا در آن نمی‌گنجد، و هر بار باید بسته و باز شود.
 *
 *  هدف، کمترین تعدادِ لمس برای یک فروشِ معمولی است:
 *
 *      دسته یا جستجو → زدن روی کالا (هر زدن یک عدد) → ثبت فروش
 *
 *  سه لمس برای یک قلم. سبد پایینِ صفحه می‌ماند و با زدن روی نوارِ آن باز
 *  می‌شود تا تعداد را کم و زیاد کنی یا ردیفی را برداری.
 */
@Composable
fun QuickSaleScreen(
  store: ShopStore,
  cartStore: CartStore,
  d: ShopData,
  snackbar: SnackbarHostState,
  onBack: () -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  val cart by cartStore.lines.collectAsState()
  val cartDebtorId by cartStore.debtorId.collectAsState()

  var category by rememberSaveable { mutableStateOf<String?>(null) }
  var search by rememberSaveable { mutableStateOf("") }
  var newCategory by rememberSaveable { mutableStateOf<String?>(null) }
  var confirmDeleteCategory by remember { mutableStateOf<String?>(null) }
  var cartOpen by rememberSaveable { mutableStateOf(false) }
  var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
  var pickDebtor by rememberSaveable { mutableStateOf(false) }
  var checkout by rememberSaveable { mutableStateOf(false) }
  var invoice by remember { mutableStateOf<Pair<ShopData, String>?>(null) }

  LaunchedEffect(d.products.size, d.debtors.size) { cartStore.prune(d) }

  fun toast(text: String) {
    scope.launch { snackbar.showSnackbar(text) }
  }

  fun applyResult(result: WarehouseEngine.Result, done: String, after: () -> Unit = {}) {
    when (result) {
      is WarehouseEngine.Result.Failed -> toast(result.message)
      is WarehouseEngine.Result.Ok -> {
        scope.launch { store.save(result.data) }
        toast(done)
        after()
      }
    }
  }

  val shown = d.products.filter { p ->
    (category == null || p.category == category) &&
      (search.isBlank() || p.name.contains(search.trim(), ignoreCase = true) ||
        p.barcodes.any { it.contains(search.trim()) })
  }

  Box(Modifier.fillMaxSize()) {
    LazyVerticalGrid(
      columns = GridCells.Adaptive(minSize = gridMinSize(phone = 112.dp, tablet = 140.dp)),
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, if (cart.isEmpty()) 24.dp else 180.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      item(span = { GridItemSpan(maxLineSpan) }) {
        Column {
          TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) {
            Icon(Icons.Filled.ArrowForward, contentDescription = null, tint = Shop.colors.primary)
            Spacer(Modifier.width(6.dp))
            Text("بازگشت به فروش", color = Shop.colors.primary)
          }
          Spacer(Modifier.height(6.dp))
          Text(
            "روی محصول بزنید تا یک عدد به سبد خرید اضافه شود",
            style = MaterialTheme.typography.bodySmall,
            color = Shop.colors.muted,
          )
          Spacer(Modifier.height(12.dp))

          OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            placeholder = { Text("جستجوی نام کالا یا بارکد…") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )

          Spacer(Modifier.height(10.dp))
          Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            FilterChip(
              selected = category == null,
              onClick = { category = null },
              label = { Text("همه دسته‌بندی‌ها") },
            )
            d.productCategories.forEach { c ->
              FilterChip(
                selected = category == c,
                onClick = { category = if (category == c) null else c },
                label = { Text(c) },
              )
            }
            // ساخت و حذف دسته‌بندی از همین‌جا — مثل دو دکمهٔ کنارِ کشویی وب
            AssistChip(
              onClick = { newCategory = "" },
              label = { Text("+ دسته‌بندی") },
            )
            if (category != null) {
              AssistChip(
                onClick = { confirmDeleteCategory = category },
                label = { Text("حذف این دسته") },
                leadingIcon = {
                  Icon(
                    Icons.Filled.DeleteOutline,
                    contentDescription = null,
                    tint = Shop.colors.danger,
                    modifier = Modifier.size(15.dp),
                  )
                },
              )
            }
          }

          newCategory?.let { value ->
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
              OutlinedTextField(
                value = value,
                onValueChange = { newCategory = it },
                placeholder = { Text("نام دسته‌بندی را بنویسید…") },
                singleLine = true,
                modifier = Modifier.weight(1f),
              )
              Button(onClick = {
                applyResult(WarehouseEngine.addCategory(d, value), "دسته‌بندی اضافه شد") {
                  category = value.trim()
                  newCategory = null
                }
              }) { Text("افزودن") }
            }
          }

          Spacer(Modifier.height(12.dp))
        }
      }

      if (shown.isEmpty()) {
        item(span = { GridItemSpan(maxLineSpan) }) {
          Panel {
            EmptyNote(
              if (d.products.isEmpty()) "هنوز محصولی ثبت نشده."
              else "محصولی در این دسته‌بندی نیست"
            )
          }
        }
      } else {
        itemsIndexed(shown, key = { _, p -> p.id }) { index, p ->
          val inCart = cart.find { it.productId == p.id }?.quantity ?: 0.0
          StaggeredItem(index) {
            QuickTile(
              name = p.name,
              price = p.salePrice,
              unit = p.unit,
              stock = ShopStore.stock(d, p.id),
              hasPhoto = p.photo,
              productId = p.id,
              inCart = inCart,
              onClick = {
                // سبد از موجودی جلو نمی‌زند؛ خطا سرِ پیشخوان درنمی‌آید
                val added = SalesEngine.addToCartCapped(
                  cart, p.id, SalesEngine.cartStep(p.unit), ShopStore.stock(d, p.id),
                )
                if (added.capped) {
                  scope.launch {
                    snackbar.showSnackbar(
                      "${p.name} فقط ${qty(added.available)}${if (p.unit.isNotBlank()) " ${p.unit}" else ""} موجود است"
                    )
                  }
                } else {
                  ScanFeedback.ok(context)
                }
                cartStore.set(added.cart)
                selectedId = p.id
              },
            )
          }
        }
      }
    }

    /* --------------------------- سبد پایین --------------------------- */
    AnimatedVisibility(
      visible = cart.isNotEmpty(),
      enter = slideInVertically(tween(220)) { it } + fadeIn(tween(220)),
      exit = slideOutVertically(tween(180)) { it } + fadeOut(tween(180)),
      modifier = Modifier.align(Alignment.BottomCenter),
    ) {
      Column {
        // فهرستِ سبد فقط وقتی باز می‌شود که کاربر بخواهد؛ وگرنه نصفِ صفحه
        // را می‌گیرد و جای کالاها را تنگ می‌کند
        AnimatedVisibility(visible = cartOpen) {
          Surface(color = Shop.colors.bg, shadowElevation = 8.dp, modifier = Modifier.fillMaxWidth()) {
            Column(
              Modifier
                .heightIn(max = 260.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
              cart.forEach { line ->
                val product = d.products.find { it.id == line.productId } ?: return@forEach
                CartRow(
                  name = product.name,
                  unit = product.unit,
                  quantity = line.quantity,
                  lineTotal = product.salePrice * line.quantity,
                  unitPrice = product.salePrice,
                  selected = selectedId == line.productId,
                  onClick = { selectedId = if (selectedId == line.productId) null else line.productId },
                  onPlus = {
                    cartStore.set(
                      SalesEngine.setCartQty(cart, line.productId, line.quantity + SalesEngine.cartStep(product.unit))
                    )
                  },
                  onMinus = {
                    cartStore.set(
                      SalesEngine.setCartQty(cart, line.productId, line.quantity - SalesEngine.cartStep(product.unit))
                    )
                  },
                  onRemove = {
                    cartStore.set(SalesEngine.setCartQty(cart, line.productId, 0.0))
                    if (selectedId == line.productId) selectedId = null
                  },
                )
                Spacer(Modifier.height(8.dp))
              }
            }
          }
        }

        Box(Modifier.clickable { cartOpen = !cartOpen }) {
          CartBar(
            total = SalesEngine.cartTotal(d, cart),
            debtorName = cartDebtorId?.let { id -> d.debtors.find { it.id == id }?.name },
            debtorNote = cartDebtorId?.let { id -> debtStateText(d, id) },
            onPickDebtor = { pickDebtor = true },
            onClearDebtor = { cartStore.setDebtor(null) },
            onFinalize = { checkout = true },
          )
        }
      }
    }
  }

  /* ---------------------------- پنجره‌ها ---------------------------- */

  confirmDeleteCategory?.let { name ->
    AlertDialog(
      onDismissRequest = { confirmDeleteCategory = null },
      containerColor = Shop.colors.surface,
      title = { Text("حذف دسته‌بندی؟", color = Shop.colors.text) },
      text = {
        Text(
          "دسته‌بندی «$name» حذف می‌شود. محصولات این دسته حذف نمی‌شوند، فقط دیگر در این دسته‌بندی فیلتر نمی‌شوند.",
          style = MaterialTheme.typography.bodySmall,
          color = Shop.colors.muted,
        )
      },
      confirmButton = {
        TextButton(onClick = {
          applyResult(WarehouseEngine.removeCategory(d, name), "دسته‌بندی حذف شد") {
            if (category == name) category = null
          }
          confirmDeleteCategory = null
        }) { Text("حذف", color = Shop.colors.danger) }
      },
      dismissButton = {
        TextButton(onClick = { confirmDeleteCategory = null }) { Text("انصراف") }
      },
    )
  }

  if (pickDebtor) {
    DebtorPicker(
      d = d,
      selected = cartDebtorId,
      onClose = { pickDebtor = false },
      onPick = { id -> cartStore.setDebtor(id); pickDebtor = false },
    )
  }

  if (checkout) {
    CheckoutDialog(
      d = d,
      cart = cart,
      presetDebtorId = cartDebtorId,
      onDismiss = { checkout = false },
      onConfirm = { options ->
        when (val result = SalesEngine.record(
          d, cart, options,
          today = todayIso(),
          now = System.currentTimeMillis(),
          newId = ::newId,
        )) {
          is SalesEngine.Result.Failed -> toast(result.message)
          is SalesEngine.Result.Ok -> {
            scope.launch { store.save(result.data) }
            cartStore.clear()
            selectedId = null
            checkout = false
            cartOpen = false
            Haptics.success(context)
            toast("فروش به مبلغ ${money(result.data.sales.last().finalTotal)} افغانی ثبت شد")
            invoice = result.data to result.saleId
          }
        }
      },
    )
  }

  invoice?.let { (snapshot, id) ->
    val sale = snapshot.sales.find { it.id == id }
    if (sale == null) invoice = null
    else InvoiceDialog(d = snapshot, sale = sale, onDismiss = { invoice = null }, onMessage = ::toast)
  }
}

/**
 *  کاشیِ یک کالا — همان `.quick-select-tile` نسخهٔ وب.
 *
 *  وقتی چیزی از این کالا در سبد باشد، کاشی رنگ می‌گیرد و مقدارش را زیرِ
 *  خودش می‌نویسد؛ فروشنده بدون باز کردنِ سبد می‌بیند چه زده است.
 */
@Composable
private fun QuickTile(
  name: String,
  price: Double,
  unit: String,
  stock: Double,
  hasPhoto: Boolean,
  productId: String,
  inCart: Double,
  onClick: () -> Unit,
) {
  val active = inCart > 0
  val out = stock <= 0

  Column(
    Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(Radius.md))
      .background(if (active) Shop.colors.primaryTint else Shop.colors.surface)
      .border(
        1.dp,
        if (active) Shop.colors.primary else Shop.colors.border,
        RoundedCornerShape(Radius.md),
      )
      .clickable(onClick = onClick)
      .padding(10.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    if (hasPhoto) {
      ProductPhoto(productId = productId, size = 54.dp)
      Spacer(Modifier.height(8.dp))
    }
    Text(
      name,
      style = MaterialTheme.typography.bodyMedium,
      color = Shop.colors.text,
      fontWeight = FontWeight.Bold,
      textAlign = TextAlign.Center,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
    )
    Spacer(Modifier.height(4.dp))
    Text(
      "${money(price)} افغانی",
      style = MaterialTheme.typography.labelMedium,
      color = Shop.colors.primary,
      maxLines = 1,
    )
    Spacer(Modifier.height(2.dp))
    Text(
      if (out) "موجود نیست" else "${qty(stock)}${if (unit.isNotBlank()) " $unit" else ""} مانده",
      style = MaterialTheme.typography.labelSmall,
      color = if (out) Shop.colors.danger else Shop.colors.muted2,
      maxLines = 1,
    )
    if (active) {
      Spacer(Modifier.height(6.dp))
      Box(
        Modifier
          .clip(RoundedCornerShape(8.dp))
          .background(Shop.colors.primary)
          .padding(horizontal = 8.dp, vertical = 3.dp),
      ) {
        Text(
          "${qty(inCart)}${if (unit.isNotBlank()) " $unit" else ""} در سبد",
          style = MaterialTheme.typography.labelSmall,
          color = Color.White,
          fontWeight = FontWeight.Bold,
        )
      }
    }
  }
}
