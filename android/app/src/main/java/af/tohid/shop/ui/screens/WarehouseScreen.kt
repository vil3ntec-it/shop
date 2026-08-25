package af.tohid.shop.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inventory
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
import af.tohid.shop.data.db.WarehouseEntryEntity
import af.tohid.shop.data.repo.OpResult
import af.tohid.shop.ui.components.*
import af.tohid.shop.ui.theme.T
import af.tohid.shop.util.Format
import af.tohid.shop.util.Ids

@Composable
fun WarehouseScreen(addTick: Int = 0) {
    val app = TohidApp.instance
    val scope = rememberCoroutineScope()

    val entries by app.db.warehouse().observeAll().collectAsState(initial = emptyList())
    val products by app.db.products().observeAll().collectAsState(initial = emptyList())
    val nameOf = remember(products) { products.associate { it.id to it.name } }

    var filterProduct by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<WarehouseEntryEntity?>(null) }
    var isNew by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<Pair<String, Tone>?>(null) }

    LaunchedEffect(addTick) {
        if (addTick > 0) {
            editing = WarehouseEntryEntity(id = Ids.new(), productId = products.firstOrNull()?.id.orEmpty())
            isNew = true
        }
    }

    val visible = remember(entries, filterProduct) {
        if (filterProduct.isEmpty()) entries else entries.filter { it.productId == filterProduct }
    }
    val totalUnits = remember(visible) { visible.sumOf { it.units } }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            PageToolbar(
                title = "انبار",
                subtitle = "ورود کالا به دکان — ${Format.number(totalUnits)} واحد در ${Format.toFa(visible.size.toString())} ثبت",
            )
            if (products.isNotEmpty()) {
                LazyRowChips(
                    options = listOf("" to "همه کالاها") + products.map { it.id to it.name },
                    selected = filterProduct,
                    onSelect = { filterProduct = it },
                )
            }
            notice?.let {
                Spacer(Modifier.height(12.dp))
                Notice(it.first, it.second)
            }
        }

        if (products.isEmpty()) {
            TCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp), padding = 0.dp) {
                EmptyState(
                    icon = Icons.Outlined.Inventory,
                    title = "اول کالا ثبت کنید",
                    subtitle = "برای ثبت ورود انبار، باید حداقل یک کالا در بخش محصولات وجود داشته باشد.",
                )
            }
        } else if (visible.isEmpty()) {
            EmptyStateFor(
                hasAny = entries.isNotEmpty(),
                emptyTitle = "هنوز ورودی انباری ثبت نشده",
                emptySub = "با ثبت اولین ورود کالا، موجودی فروشگاه محاسبه می‌شود.",
                onAdd = {
                    editing = WarehouseEntryEntity(id = Ids.new(), productId = products.first().id)
                    isNew = true
                },
                addLabel = "+ ثبت ورود کالا",
                icon = Icons.Outlined.Inventory,
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(visible, key = { it.id }) { e ->
                    EntryRow(e, nameOf[e.productId] ?: "کالای حذف‌شده") {
                        editing = e; isNew = false
                    }
                }
            }
        }
    }

    editing?.let { draft ->
        WarehouseSheet(
            draft = draft,
            isNew = isNew,
            products = products.map { it.id to it.name },
            unitOf = { id -> products.firstOrNull { it.id == id }?.unit.orEmpty() },
            onDismiss = { editing = null },
            onSave = { e ->
                scope.launch {
                    when (val r = app.catalog.saveWarehouseEntry(e, isNew)) {
                        is OpResult.Refused -> notice = r.message to Tone.Red
                        is OpResult.OkWithWarning -> { notice = r.message to Tone.Orange; editing = null }
                        OpResult.Ok -> { notice = null; editing = null }
                    }
                }
            },
            onDelete = { id ->
                scope.launch {
                    when (val r = app.catalog.deleteWarehouseEntry(id)) {
                        is OpResult.Refused -> { notice = r.message to Tone.Red; editing = null }
                        is OpResult.OkWithWarning -> { notice = r.message to Tone.Orange; editing = null }
                        OpResult.Ok -> { notice = null; editing = null }
                    }
                }
            },
        )
    }
}

/* ------------------------------------------------------------------ */

@Composable
private fun EntryRow(e: WarehouseEntryEntity, productName: String, onClick: () -> Unit) {
    TCard(Modifier.fillMaxWidth(), padding = 14.dp, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    productName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = T.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(Format.shortDate(e.date), fontSize = 11.5.sp, color = T.muted)
            }
            Badge(
                if (e.units >= 0) "+${Format.number(e.units)} ${e.unit}".trim()
                else "${Format.number(e.units)} ${e.unit}".trim(),
                if (e.units >= 0) Tone.Green else Tone.Orange,
            )
        }
        if (e.cartons > 0 && e.perCarton > 0) {
            Spacer(Modifier.height(10.dp))
            Text(
                "${Format.number(e.cartons)} کارتن × ${Format.number(e.perCarton)} = ${Format.number(e.units)}",
                fontSize = 11.5.sp,
                color = T.muted,
            )
        }
        if (e.notes.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(e.notes, fontSize = 11.5.sp, color = T.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/* ------------------------------------------------------------------ */

@Composable
private fun WarehouseSheet(
    draft: WarehouseEntryEntity,
    isNew: Boolean,
    products: List<Pair<String, String>>,
    unitOf: (String) -> String,
    onDismiss: () -> Unit,
    onSave: (WarehouseEntryEntity) -> Unit,
    onDelete: (String) -> Unit,
) {
    var productId by remember(draft.id) { mutableStateOf(draft.productId) }
    var cartons by remember(draft.id) { mutableStateOf(numText(draft.cartons)) }
    var perCarton by remember(draft.id) { mutableStateOf(numText(draft.perCarton)) }
    var extra by remember(draft.id) {
        val computed = (draft.cartons * draft.perCarton)
        mutableStateOf(numText(draft.units - computed))
    }
    var price by remember(draft.id) { mutableStateOf(numText(draft.price)) }
    var notes by remember(draft.id) { mutableStateOf(draft.notes) }
    var confirmDelete by remember { mutableStateOf(false) }

    val c = cartons.toDoubleOrNull() ?: 0.0
    val pc = perCarton.toDoubleOrNull() ?: 0.0
    val ex = extra.toDoubleOrNull() ?: 0.0
    val units = c * pc + ex

    FormSheet(if (isNew) "ثبت ورود کالا" else "ویرایش ورود کالا", onDismiss) {
        Text("کالا", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = T.muted)
        Spacer(Modifier.height(6.dp))
        LazyRowChips(products, productId) { productId = it }

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f)) {
                TField("تعداد کارتن", cartons, { cartons = digitsOnly(it) }, numeric = true)
            }
            Box(Modifier.weight(1f)) {
                TField("در هر کارتن", perCarton, { perCarton = digitsOnly(it) }, numeric = true)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f)) {
                TField("دانه‌ی اضافه", extra, { extra = digitsOnly(it) }, numeric = true)
            }
            Box(Modifier.weight(1f)) {
                TField("قیمت خرید واحد", price, { price = digitsOnly(it) }, numeric = true)
            }
        }

        Spacer(Modifier.height(14.dp))
        Notice(
            "مجموع ورودی: ${Format.number(units)} ${unitOf(productId)}".trim(),
            if (units > 0) Tone.Green else Tone.Orange,
        )

        Spacer(Modifier.height(12.dp))
        TField("یادداشت", notes, { notes = it }, placeholder = "اختیاری")

        FormActions(
            confirmLabel = if (isNew) "ثبت ورود" else "ذخیره",
            onConfirm = {
                onSave(
                    draft.copy(
                        productId = productId,
                        cartons = c, perCarton = pc, units = units,
                        unit = unitOf(productId),
                        price = price.toDoubleOrNull() ?: 0.0,
                        notes = notes.trim(),
                    )
                )
            },
            onCancel = onDismiss,
            deleteLabel = if (isNew) null else "حذف این ورودی",
            onDelete = if (isNew) null else ({ confirmDelete = true }),
        )
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "حذف ورودی انبار؟",
            message = "اگر این مقدار قبلاً فروخته شده باشد، حذف انجام نمی‌شود و پیام مربوطه را می‌بینید.",
            confirmLabel = "حذف",
            danger = true,
            onConfirm = { confirmDelete = false; onDelete(draft.id) },
            onDismiss = { confirmDelete = false },
        )
    }
}
