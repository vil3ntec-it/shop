package af.tohid.shop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import af.tohid.shop.TohidApp
import af.tohid.shop.data.db.SupplierEntity
import af.tohid.shop.data.repo.OpResult
import af.tohid.shop.ui.components.*
import af.tohid.shop.ui.theme.Radius
import af.tohid.shop.ui.theme.T
import af.tohid.shop.util.Format
import af.tohid.shop.util.Ids

@Composable
fun PurchasingScreen() {
    val app = TohidApp.instance
    val scope = rememberCoroutineScope()

    val suppliers by app.db.suppliers().observeAll().collectAsState(initial = emptyList())
    val purchases by app.db.purchases().observeAll().collectAsState(initial = emptyList())
    val products by app.db.products().observeAll().collectAsState(initial = emptyList())

    var balances by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }
    LaunchedEffect(suppliers, purchases) {
        balances = suppliers.associate { it.id to app.catalog.supplierBalance(it.id) }
    }

    var editing by remember { mutableStateOf<SupplierEntity?>(null) }
    var isNew by remember { mutableStateOf(false) }
    var buyingFrom by remember { mutableStateOf<SupplierEntity?>(null) }
    var payingTo by remember { mutableStateOf<SupplierEntity?>(null) }
    var notice by remember { mutableStateOf<Pair<String, Tone>?>(null) }

    val totalOwed = remember(balances) { balances.values.filter { it > 0 }.sum() }

    Column(Modifier.fillMaxSize().background(T.surface)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            PageToolbar(
                title = "خریداری",
                subtitle = "بدهی شما به تأمین‌کننده‌ها: ${Format.money(totalOwed)} افغانی",
                actions = {
                    TButton(
                        "تأمین‌کننده جدید",
                        { editing = SupplierEntity(id = Ids.new(), name = ""); isNew = true },
                        small = true,
                    )
                },
            )
            notice?.let { Notice(it.first, it.second) }
        }

        if (suppliers.isEmpty()) {
            TCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp), padding = 0.dp) {
                EmptyState(
                    icon = Icons.Outlined.LocalShipping,
                    title = "هنوز تأمین‌کننده‌ای ثبت نشده",
                    subtitle = "کسانی که از آن‌ها جنس می‌خرید را اینجا ثبت کنید تا حساب و بدهی‌شان روشن بماند.",
                    ctaLabel = "+ افزودن تأمین‌کننده",
                    onCta = { editing = SupplierEntity(id = Ids.new(), name = ""); isNew = true },
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(suppliers, key = { it.id }) { sp ->
                    SupplierRow(
                        supplier = sp,
                        balance = balances[sp.id] ?: 0.0,
                        purchaseCount = purchases.count { it.supplierId == sp.id },
                        onBuy = { buyingFrom = sp },
                        onPay = { payingTo = sp },
                        onEdit = { editing = sp; isNew = false },
                    )
                }
            }
        }
    }

    editing?.let { draft ->
        SupplierSheet(
            draft = draft,
            isNew = isNew,
            onDismiss = { editing = null },
            onSave = { sp ->
                scope.launch {
                    when (val r = app.catalog.saveSupplier(sp, isNew)) {
                        is OpResult.Refused -> notice = r.message to Tone.Red
                        is OpResult.OkWithWarning -> { notice = r.message to Tone.Orange; editing = null }
                        OpResult.Ok -> { notice = null; editing = null }
                    }
                }
            },
            onDelete = { id ->
                scope.launch {
                    when (val r = app.catalog.deleteSupplier(id)) {
                        is OpResult.Refused -> { notice = r.message to Tone.Red; editing = null }
                        is OpResult.OkWithWarning -> { notice = r.message to Tone.Orange; editing = null }
                        OpResult.Ok -> { notice = null; editing = null }
                    }
                }
            },
        )
    }

    buyingFrom?.let { sp ->
        PurchaseSheet(
            supplierName = sp.name,
            products = products.map { it.id to it.name },
            onDismiss = { buyingFrom = null },
            onConfirm = { productId, qty, price, paid, note ->
                scope.launch {
                    when (val r = app.catalog.addPurchase(sp.id, productId, qty, price, paid, note)) {
                        is OpResult.Refused -> notice = r.message to Tone.Red
                        is OpResult.OkWithWarning -> { notice = r.message to Tone.Orange; buyingFrom = null }
                        OpResult.Ok -> {
                            notice = "خرید ثبت شد. برای اضافه شدن به موجودی، ورود کالا را در بخش انبار ثبت کنید." to Tone.Blue
                            buyingFrom = null
                        }
                    }
                }
            },
        )
    }

    payingTo?.let { sp ->
        AmountSheet(
            title = "پرداخت به «${sp.name}»",
            confirmLabel = "ثبت پرداخت",
            hint = "بدهی فعلی شما: ${Format.money(balances[sp.id] ?: 0.0)} افغانی",
            onDismiss = { payingTo = null },
            onConfirm = { amount, note ->
                scope.launch {
                    when (val r = app.catalog.paySupplier(sp.id, amount, note)) {
                        is OpResult.Refused -> notice = r.message to Tone.Red
                        is OpResult.OkWithWarning -> { notice = r.message to Tone.Orange; payingTo = null }
                        OpResult.Ok -> { notice = null; payingTo = null }
                    }
                }
            },
        )
    }
}

/* ------------------------------------------------------------------ */

@Composable
private fun SupplierRow(
    supplier: SupplierEntity,
    balance: Double,
    purchaseCount: Int,
    onBuy: () -> Unit,
    onPay: () -> Unit,
    onEdit: () -> Unit,
) {
    val tone = if (balance > 0) Tone.Orange else Tone.Green
    TCard(Modifier.fillMaxWidth(), padding = 14.dp, onClick = onEdit) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(Radius.md))
                    .background(toneBg(tone)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    supplier.name.trim().take(1),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = toneFg(tone),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    supplier.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = T.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    "${Format.toFa(purchaseCount.toString())} خرید" +
                        if (supplier.phone.isNotBlank()) " — ${Format.toFa(supplier.phone)}" else "",
                    fontSize = 11.5.sp,
                    color = T.muted,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    if (balance > 0) Format.money(balance) else "تسویه",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = toneFg(tone),
                )
                if (balance > 0) {
                    Spacer(Modifier.height(2.dp))
                    Text("افغانی بدهی", fontSize = 10.5.sp, color = T.muted)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TButton("ثبت خرید", onBuy, Modifier.weight(1f), kind = BtnKind.Secondary, small = true)
            TButton("پرداخت", onPay, Modifier.weight(1f), small = true)
        }
    }
}

/* ------------------------------------------------------------------ */

@Composable
private fun SupplierSheet(
    draft: SupplierEntity,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (SupplierEntity) -> Unit,
    onDelete: (String) -> Unit,
) {
    var name by remember(draft.id) { mutableStateOf(draft.name) }
    var phone by remember(draft.id) { mutableStateOf(draft.phone) }
    var notes by remember(draft.id) { mutableStateOf(draft.notes) }
    var confirmDelete by remember { mutableStateOf(false) }

    FormSheet(if (isNew) "افزودن تأمین‌کننده" else "ویرایش تأمین‌کننده", onDismiss) {
        TField("نام", name, { name = it }, placeholder = "مثلاً شرکت نور")
        Spacer(Modifier.height(12.dp))
        TField("شماره تماس", phone, { phone = digitsOnly(it) }, numeric = true, placeholder = "۰۷…")
        Spacer(Modifier.height(12.dp))
        TField("یادداشت", notes, { notes = it }, placeholder = "اختیاری")
        FormActions(
            confirmLabel = if (isNew) "ثبت" else "ذخیره",
            onConfirm = { onSave(draft.copy(name = name, phone = phone.trim(), notes = notes.trim())) },
            onCancel = onDismiss,
            deleteLabel = if (isNew) null else "حذف تأمین‌کننده",
            onDelete = if (isNew) null else ({ confirmDelete = true }),
        )
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "حذف «${draft.name}»؟",
            message = "اگر بدهی بازی به این تأمین‌کننده داشته باشید، حذف انجام نمی‌شود.",
            confirmLabel = "حذف",
            danger = true,
            onConfirm = { confirmDelete = false; onDelete(draft.id) },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun PurchaseSheet(
    supplierName: String,
    products: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onConfirm: (String, Double, Double, Double, String) -> Unit,
) {
    var productId by remember { mutableStateOf(products.firstOrNull()?.first.orEmpty()) }
    var qty by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var paid by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    val total = (qty.toDoubleOrNull() ?: 0.0) * (price.toDoubleOrNull() ?: 0.0)
    val debt = (total - (paid.toDoubleOrNull() ?: 0.0)).coerceAtLeast(0.0)

    FormSheet("ثبت خرید از «$supplierName»", onDismiss) {
        if (products.isNotEmpty()) {
            Text("کالا", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = T.muted)
            Spacer(Modifier.height(6.dp))
            LazyRowChips(products, productId) { productId = it }
            Spacer(Modifier.height(14.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f)) { TField("تعداد", qty, { qty = digitsOnly(it) }, numeric = true) }
            Box(Modifier.weight(1f)) { TField("قیمت واحد", price, { price = digitsOnly(it) }, numeric = true) }
        }
        Spacer(Modifier.height(12.dp))
        TField("مبلغ پرداختی", paid, { paid = digitsOnly(it) }, numeric = true, placeholder = "۰")
        Spacer(Modifier.height(14.dp))
        Notice(
            "جمع خرید: ${Format.money(total)} افغانی" +
                if (debt > 0) "  •  بدهی باقی‌مانده: ${Format.money(debt)} افغانی" else "  •  تسویه",
            if (debt > 0) Tone.Orange else Tone.Green,
        )
        Spacer(Modifier.height(12.dp))
        TField("یادداشت", note, { note = it }, placeholder = "اختیاری")
        FormActions(
            confirmLabel = "ثبت خرید",
            onConfirm = {
                onConfirm(
                    productId,
                    qty.toDoubleOrNull() ?: 0.0,
                    price.toDoubleOrNull() ?: 0.0,
                    paid.toDoubleOrNull() ?: 0.0,
                    note.trim(),
                )
            },
            onCancel = onDismiss,
        )
    }
}
