package af.tohid.shop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Icon
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
import af.tohid.shop.data.db.DebtorEntity
import af.tohid.shop.data.db.ProductEntity
import af.tohid.shop.data.repo.CartLine
import af.tohid.shop.data.repo.SaleResult
import af.tohid.shop.ui.components.*
import af.tohid.shop.ui.theme.Radius
import af.tohid.shop.ui.theme.T
import af.tohid.shop.util.Format

/** صفحه فروش — پشت دروازه‌ی اشتراک. */
@Composable
fun SaleScreen() {
    VipGate(feature = "sales", title = "فروش") { SaleContent() }
}

@Composable
private fun SaleContent() {
    val app = TohidApp.instance
    val scope = rememberCoroutineScope()

    val products by app.db.products().observeAll().collectAsState(initial = emptyList())
    val warehouse by app.db.warehouse().observeAll().collectAsState(initial = emptyList())
    val sales by app.db.sales().observeAll().collectAsState(initial = emptyList())
    val debtors by app.db.debtors().observeAll().collectAsState(initial = emptyList())

    var stockOf by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }
    LaunchedEffect(products, warehouse, sales) {
        stockOf = products.associate { it.id to app.stock.stockOf(it.id) }
    }

    var cart by remember { mutableStateOf<List<CartLine>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var showCheckout by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<Pair<String, Tone>?>(null) }

    val visible = remember(products, query) {
        val q = query.trim()
        if (q.isEmpty()) products
        else products.filter { it.name.contains(q, true) || it.barcodes.contains(q, true) }
    }
    val totals = app.sales.computeTotals(cart, "amount", 0.0)

    Column(Modifier.fillMaxSize().background(T.surface)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            PageToolbar(
                title = "فروش",
                subtitle = if (cart.isEmpty()) "کالا را بزنید تا به سبد اضافه شود"
                           else "${Format.toFa(cart.size.toString())} قلم در سبد",
            )
            SearchField(query, { query = it }, "جستجوی کالا یا بارکد…")
            message?.let {
                Spacer(Modifier.height(12.dp))
                Notice(it.first, it.second)
            }
        }

        if (products.isEmpty()) {
            TCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp), padding = 0.dp) {
                EmptyState(
                    icon = Icons.Outlined.Inventory2,
                    title = "هنوز کالایی ثبت نشده",
                    subtitle = "برای فروش، اول از بخش محصولات کالا اضافه کنید و ورودی انبارش را ثبت کنید.",
                )
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (cart.isNotEmpty()) {
                item {
                    TPanel("سبد خرید", Modifier.fillMaxWidth()) {
                        cart.forEachIndexed { i, line ->
                            if (i > 0) Divider()
                            CartRow(line) {
                                cart = cart.filterNot { it.product.id == line.product.id }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(4.dp)) }
            }

            items(visible, key = { it.id }) { product ->
                val available = stockOf[product.id] ?: 0.0
                val inCart = cart.find { it.product.id == product.id }?.quantity ?: 0.0
                ProductPickRow(
                    product = product,
                    available = available,
                    inCart = inCart,
                    onAdd = {
                        if (inCart + 1 > available) {
                            message = shortStockMessage(product, available) to Tone.Orange
                        } else {
                            message = null
                            cart = if (inCart > 0) {
                                cart.map {
                                    if (it.product.id == product.id) it.copy(quantity = it.quantity + 1) else it
                                }
                            } else cart + CartLine(product, 1.0)
                        }
                    },
                    onRemove = {
                        cart = cart.mapNotNull {
                            if (it.product.id != product.id) it
                            else if (it.quantity <= 1) null else it.copy(quantity = it.quantity - 1)
                        }
                    },
                )
            }
            item { Spacer(Modifier.height(80.dp)) }
        }

        if (cart.isNotEmpty()) {
            Column {
                Divider()
                Row(
                    Modifier.fillMaxWidth().background(T.bg).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text("جمع کل", fontSize = 11.5.sp, color = T.muted)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "${Format.money(totals.third)} افغانی",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = T.text,
                        )
                    }
                    TButton("تسویه و ثبت", { showCheckout = true })
                }
            }
        }
    }

    if (showCheckout) {
        CheckoutSheet(
            cart = cart,
            debtors = debtors,
            onDismiss = { showCheckout = false },
            onConfirm = { discountType, discountValue, method, debtorId, paid ->
                scope.launch {
                    when (val result =
                        app.sales.checkout(cart, discountType, discountValue, method, debtorId, paid)) {
                        is SaleResult.Success -> {
                            message = "فروش ثبت شد — فاکتور #${Format.toFa(result.invoiceNumber.toString())}" to Tone.Green
                            cart = emptyList()
                            showCheckout = false
                        }
                        is SaleResult.NotEnoughStock -> {
                            message = result.message to Tone.Red
                            showCheckout = false
                        }
                        is SaleResult.Invalid -> message = result.message to Tone.Red
                    }
                }
            },
        )
    }
}

private fun shortStockMessage(product: ProductEntity, available: Double): String {
    val u = if (product.unit.isBlank()) "" else " ${product.unit}"
    return if (available <= 0.0) {
        "«${product.name}» موجودی ندارد. اگر جنس در دکان هست، اول ورودی انبار را ثبت کنید."
    } else {
        "«${product.name}»: فقط ${Format.number(available)}$u موجود است."
    }
}

/* ------------------------------------------------------------------ */

@Composable
private fun ProductPickRow(
    product: ProductEntity,
    available: Double,
    inCart: Double,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
) {
    val out = available <= 0.0
    TCard(Modifier.fillMaxWidth(), padding = 12.dp, onClick = if (out) null else onAdd) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    product.name,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (out) T.muted else T.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    "${Format.money(product.salePrice)} افغانی  •  موجودی ${Format.number(available)} ${product.unit}".trim(),
                    fontSize = 11.sp,
                    color = if (out) T.danger else T.muted,
                )
            }
            if (inCart > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StepButton(Icons.Outlined.Remove, "کم کردن", onRemove)
                    Text(
                        Format.number(inCart),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = T.text,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    StepButton(Icons.Outlined.Add, "افزودن", onAdd)
                }
            } else if (!out) {
                StepButton(Icons.Outlined.Add, "افزودن", onAdd)
            } else {
                Badge("تمام‌شده", Tone.Red)
            }
        }
    }
}

@Composable
private fun StepButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(Radius.sm))
            .background(T.primaryTint)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = T.primary, modifier = Modifier.size(17.dp))
    }
}

@Composable
private fun CartRow(line: CartLine, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                line.product.name,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = T.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "${Format.number(line.quantity)} × ${Format.money(line.product.salePrice)}",
                fontSize = 10.5.sp,
                color = T.muted,
            )
        }
        Text(
            Format.money(line.lineTotal),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = T.text,
        )
        Spacer(Modifier.width(10.dp))
        Icon(
            Icons.Outlined.DeleteOutline,
            contentDescription = "حذف",
            tint = T.danger,
            modifier = Modifier.size(19.dp).clickable { onDelete() },
        )
    }
}

/* ------------------------------------------------------------------ */

@Composable
private fun CheckoutSheet(
    cart: List<CartLine>,
    debtors: List<DebtorEntity>,
    onDismiss: () -> Unit,
    onConfirm: (String, Double, String, String?, Double) -> Unit,
) {
    val app = TohidApp.instance
    var discountType by remember { mutableStateOf("percent") }
    var discountText by remember { mutableStateOf("") }
    var method by remember { mutableStateOf("cash") }
    var debtorId by remember { mutableStateOf<String?>(null) }
    var paidText by remember { mutableStateOf("") }

    val discountValue = discountText.toDoubleOrNull() ?: 0.0
    val (subtotal, discount, finalTotal) = app.sales.computeTotals(cart, discountType, discountValue)
    val paid = if (method == "cash") finalTotal else (paidText.toDoubleOrNull() ?: 0.0)
    val remaining = (finalTotal - paid).coerceAtLeast(0.0)

    FormSheet("تسویه فروش", onDismiss) {
        Text("تخفیف", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = T.muted)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip("درصدی", discountType == "percent") { discountType = "percent" }
            FilterChip("مبلغی", discountType == "amount") { discountType = "amount" }
        }
        Spacer(Modifier.height(10.dp))
        TField(
            null, discountText, { discountText = digitsOnly(it) },
            numeric = true,
            placeholder = if (discountType == "percent") "درصد تخفیف" else "مبلغ تخفیف",
        )

        Spacer(Modifier.height(16.dp))
        Text("نوع پرداخت", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = T.muted)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip("نقدی", method == "cash") { method = "cash" }
            FilterChip("نسیه", method == "credit") { method = "credit" }
        }

        if (method == "credit") {
            Spacer(Modifier.height(14.dp))
            if (debtors.isEmpty()) {
                Notice("قرض‌داری ثبت نشده است. برای فروش نسیه اول از بخش قرض‌داران یک نفر اضافه کنید.", Tone.Red)
            } else {
                Text("قرض‌دار", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = T.muted)
                Spacer(Modifier.height(6.dp))
                LazyRowChips(debtors.map { it.id to it.name }, debtorId.orEmpty()) { debtorId = it }
                Spacer(Modifier.height(12.dp))
                TField("مبلغ پرداختی الان", paidText, { paidText = digitsOnly(it) }, numeric = true, placeholder = "۰")
            }
        }

        Spacer(Modifier.height(16.dp))
        Divider()
        SummaryRow("جمع اقلام", "${Format.money(subtotal)} افغانی")
        if (discount > 0) SummaryRow("تخفیف", "− ${Format.money(discount)} افغانی")
        if (method == "credit" && remaining > 0) {
            SummaryRow("باقی‌مانده (نسیه)", "${Format.money(remaining)} افغانی", valueColor = T.danger)
        }
        Divider()
        SummaryRow("قابل پرداخت", "${Format.money(finalTotal)} افغانی", bold = true)

        FormActions(
            confirmLabel = "ثبت فروش",
            onConfirm = {
                onConfirm(discountType, discountValue, method, debtorId, paidText.toDoubleOrNull() ?: 0.0)
            },
            onCancel = onDismiss,
        )
    }
}
