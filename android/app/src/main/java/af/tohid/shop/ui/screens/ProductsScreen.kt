package af.tohid.shop.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import af.tohid.shop.TohidApp
import af.tohid.shop.data.db.ProductEntity
import af.tohid.shop.data.repo.OpResult
import af.tohid.shop.ui.components.*
import af.tohid.shop.ui.theme.T
import af.tohid.shop.util.Format
import af.tohid.shop.util.Ids

@Composable
fun ProductsScreen(addTick: Int = 0) {
    val app = TohidApp.instance
    val scope = rememberCoroutineScope()

    val products by app.db.products().observeAll().collectAsState(initial = emptyList())
    val warehouse by app.db.warehouse().observeAll().collectAsState(initial = emptyList())
    val sales by app.db.sales().observeAll().collectAsState(initial = emptyList())

    var stockOf by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }
    LaunchedEffect(products, warehouse, sales) {
        stockOf = products.associate { it.id to app.stock.stockOf(it.id) }
    }

    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<ProductEntity?>(null) }
    var isNew by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<Pair<String, Tone>?>(null) }

    // درخواست افزودن از دکمه‌ی شناور
    LaunchedEffect(addTick) {
        if (addTick > 0) {
            editing = ProductEntity(id = Ids.new(), name = "")
            isNew = true
        }
    }

    val categories = remember(products) {
        products.map { it.category.trim() }.filter { it.isNotEmpty() }.distinct().sorted()
    }

    val visible = remember(products, query, category, stockOf) {
        products.filter { p ->
            val q = query.trim()
            val matchQ = q.isEmpty() ||
                p.name.contains(q, true) ||
                p.category.contains(q, true) ||
                p.barcodes.contains(q, true)
            val matchC = category.isEmpty() || p.category.trim() == category
            matchQ && matchC
        }
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            PageToolbar(
                title = "محصولات",
                subtitle = "${Format.toFa(products.size.toString())} قلم کالا",
            )
            SearchField(query, { query = it }, "جستجوی کالا، دسته یا بارکد…")

            if (categories.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                LazyRowChips(
                    options = listOf("" to "همه") + categories.map { it to it },
                    selected = category,
                    onSelect = { category = it },
                )
            }

            notice?.let {
                Spacer(Modifier.height(12.dp))
                Notice(it.first, it.second)
            }
        }

        if (visible.isEmpty()) {
            EmptyStateFor(
                hasAny = products.isNotEmpty(),
                emptyTitle = "هنوز کالایی ثبت نشده",
                emptySub = "با افزودن اولین کالا، فهرست اجناس فروشگاه اینجا نمایش داده می‌شود.",
                onAdd = { editing = ProductEntity(id = Ids.new(), name = ""); isNew = true },
                addLabel = "+ افزودن کالا",
                icon = Icons.Outlined.Inventory2,
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(visible, key = { it.id }) { p ->
                    ProductRow(
                        product = p,
                        stock = stockOf[p.id] ?: 0.0,
                        onClick = { editing = p; isNew = false },
                    )
                }
            }
        }
    }

    editing?.let { draft ->
        ProductSheet(
            draft = draft,
            isNew = isNew,
            onDismiss = { editing = null },
            onSave = { p ->
                scope.launch {
                    when (val r = app.catalog.saveProduct(p, isNew)) {
                        is OpResult.Refused -> notice = r.message to Tone.Red
                        is OpResult.OkWithWarning -> { notice = r.message to Tone.Orange; editing = null }
                        OpResult.Ok -> { notice = null; editing = null }
                    }
                }
            },
            onDelete = { id ->
                scope.launch {
                    when (val r = app.catalog.deleteProduct(id)) {
                        is OpResult.Refused -> notice = r.message to Tone.Red
                        is OpResult.OkWithWarning -> { notice = r.message to Tone.Orange; editing = null }
                        OpResult.Ok -> { notice = null; editing = null }
                    }
                }
            },
            warningOf = { id -> app.catalog.productDeleteWarning(id) },
        )
    }
}

/* ------------------------------------------------------------------ */

@Composable
private fun ProductRow(product: ProductEntity, stock: Double, onClick: () -> Unit) {
    val tone = when {
        stock <= 0.0 -> Tone.Red
        stock <= product.minStock -> Tone.Orange
        else -> Tone.Green
    }
    val statusText = when (tone) {
        Tone.Red -> "تمام‌شده"
        Tone.Orange -> "موجودی کم"
        else -> "کافی"
    }

    TCard(Modifier.fillMaxWidth(), padding = 14.dp, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    product.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = T.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (product.category.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(product.category, fontSize = 11.5.sp, color = T.muted)
                }
            }
            Badge(statusText, tone)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            MiniStat("موجودی", "${Format.number(stock)} ${product.unit}".trim())
            MiniStat("فروش", "${Format.money(product.salePrice)} افغانی")
            if (product.purchasePrice > 0) {
                MiniStat("خرید", "${Format.money(product.purchasePrice)} افغانی")
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String) {
    Column {
        Text(label, fontSize = 10.5.sp, color = T.muted2)
        Spacer(Modifier.height(2.dp))
        Text(value, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = T.text, maxLines = 1)
    }
}

/* ------------------------------------------------------------------ */

@Composable
private fun ProductSheet(
    draft: ProductEntity,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (ProductEntity) -> Unit,
    onDelete: (String) -> Unit,
    warningOf: suspend (String) -> String?,
) {
    var name by remember(draft.id) { mutableStateOf(draft.name) }
    var category by remember(draft.id) { mutableStateOf(draft.category) }
    var unit by remember(draft.id) { mutableStateOf(draft.unit) }
    var purchase by remember(draft.id) { mutableStateOf(numText(draft.purchasePrice)) }
    var sale by remember(draft.id) { mutableStateOf(numText(draft.salePrice)) }
    var minStock by remember(draft.id) { mutableStateOf(numText(draft.minStock)) }
    var barcodes by remember(draft.id) { mutableStateOf(draft.barcodes) }

    var confirmDelete by remember { mutableStateOf(false) }
    var deleteWarning by remember { mutableStateOf<String?>(null) }

    FormSheet(if (isNew) "افزودن کالا" else "ویرایش کالا", onDismiss) {
        TField("نام کالا", name, { name = it }, placeholder = "مثلاً روغن ۵ لیتری")
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f)) { TField("دسته‌بندی", category, { category = it }, placeholder = "خوراکی") }
            Box(Modifier.weight(1f)) { TField("واحد", unit, { unit = it }, placeholder = "عدد / کیلو") }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f)) {
                TField("قیمت خرید", purchase, { purchase = digitsOnly(it) }, numeric = true)
            }
            Box(Modifier.weight(1f)) {
                TField("قیمت فروش", sale, { sale = digitsOnly(it) }, numeric = true)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f)) {
                TField("حداقل موجودی", minStock, { minStock = digitsOnly(it) }, numeric = true)
            }
            Box(Modifier.weight(1f)) {
                TField("بارکد", barcodes, { barcodes = it }, placeholder = "اختیاری")
            }
        }

        FormActions(
            confirmLabel = if (isNew) "ثبت کالا" else "ذخیره",
            onConfirm = {
                onSave(
                    draft.copy(
                        name = name,
                        category = category.trim(),
                        unit = unit.trim(),
                        purchasePrice = purchase.toDoubleOrNull() ?: 0.0,
                        salePrice = sale.toDoubleOrNull() ?: 0.0,
                        minStock = minStock.toDoubleOrNull() ?: 0.0,
                        barcodes = barcodes.trim(),
                    )
                )
            },
            onCancel = onDismiss,
            deleteLabel = if (isNew) null else "حذف کالا",
            onDelete = if (isNew) null else ({ confirmDelete = true }),
        )
    }

    if (confirmDelete) {
        LaunchedEffect(draft.id) { deleteWarning = warningOf(draft.id) }
        ConfirmDialog(
            title = "حذف «${draft.name}»؟",
            message = deleteWarning ?: "این کالا برای همیشه از دفتر پاک می‌شود.",
            confirmLabel = "حذف",
            danger = true,
            onConfirm = { confirmDelete = false; onDelete(draft.id) },
            onDismiss = { confirmDelete = false },
        )
    }
}

/* ------------------------------------------------------------------ */
/*  کمکی‌های مشترک صفحه‌های فهرست                                      */
/* ------------------------------------------------------------------ */

@Composable
fun LazyRowChips(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    androidx.compose.foundation.lazy.LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(options, key = { it.first.ifEmpty { "__all__" } }) { (value, label) ->
            FilterChip(label, selected == value) { onSelect(value) }
        }
    }
}

@Composable
fun EmptyStateFor(
    hasAny: Boolean,
    emptyTitle: String,
    emptySub: String,
    onAdd: () -> Unit,
    addLabel: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    TCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp), padding = 0.dp) {
        if (hasAny) {
            EmptyState(
                icon = icon,
                title = "نتیجه‌ای یافت نشد",
                subtitle = "با این جستجو یا فیلتر چیزی پیدا نشد.",
            )
        } else {
            EmptyState(
                icon = icon,
                title = emptyTitle,
                subtitle = emptySub,
                ctaLabel = addLabel,
                onCta = onAdd,
            )
        }
    }
}

/** فقط رقم و یک ممیز — تا ورودی عددی خراب نشود. */
fun digitsOnly(s: String): String {
    val cleaned = s.filter { it.isDigit() || it == '.' }
    val firstDot = cleaned.indexOf('.')
    if (firstDot < 0) return cleaned
    return cleaned.substring(0, firstDot + 1) + cleaned.substring(firstDot + 1).replace(".", "")
}

fun numText(v: Double): String =
    if (v == 0.0) "" else if (v == Math.floor(v)) v.toLong().toString() else v.toString()
