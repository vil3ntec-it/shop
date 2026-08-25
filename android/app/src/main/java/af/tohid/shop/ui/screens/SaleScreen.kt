package af.tohid.shop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import af.tohid.shop.TohidApp
import af.tohid.shop.data.db.DebtorEntity
import af.tohid.shop.data.db.ProductEntity
import af.tohid.shop.data.repo.CartLine
import af.tohid.shop.data.repo.SaleResult
import af.tohid.shop.util.Format
import kotlinx.coroutines.launch

/** صفحه فروش — پشت دروازه‌ی اشتراک. */
@Composable
fun SaleScreen() {
    VipGate(feature = "sales", title = "فروش") { SaleContent() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SaleContent() {
    val app = TohidApp.instance
    val scope = rememberCoroutineScope()

    var products by remember { mutableStateOf<List<ProductEntity>>(emptyList()) }
    var stockOf by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }
    var debtors by remember { mutableStateOf<List<DebtorEntity>>(emptyList()) }
    var cart by remember { mutableStateOf<List<CartLine>>(emptyList()) }

    var showCheckout by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        products = app.db.products().all()
        debtors = app.db.debtors().allOnce()
        stockOf = products.associate { it.id to app.stock.stockOf(it.id) }
    }

    val totals = app.sales.computeTotals(cart, "amount", 0.0)

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text("فروش", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("کالا را بزنید تا به سبد اضافه شود",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (products.isEmpty()) {
            EmptyState("هنوز کالایی ثبت نشده است. اول از بخش محصولات کالا اضافه کنید.")
            return@Column
        }

        LazyColumn(
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text("کالاها", style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold)
            }
            items(products, key = { it.id }) { product ->
                val available = stockOf[product.id] ?: 0.0
                val inCart = cart.find { it.product.id == product.id }?.quantity ?: 0.0
                ProductRow(
                    product = product, available = available, inCart = inCart,
                    onAdd = {
                        cart = if (inCart > 0) {
                            cart.map { if (it.product.id == product.id) it.copy(quantity = it.quantity + 1) else it }
                        } else cart + CartLine(product, 1.0)
                    },
                    onRemove = {
                        cart = cart.mapNotNull {
                            if (it.product.id != product.id) it
                            else if (it.quantity <= 1) null else it.copy(quantity = it.quantity - 1)
                        }
                    },
                )
            }

            if (cart.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(6.dp))
                    Text("سبد خرید", style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold)
                }
                items(cart, key = { it.product.id }) { line ->
                    CartRow(line) { cart = cart.filterNot { it.product.id == line.product.id } }
                }
            }
            item { Spacer(Modifier.height(90.dp)) }
        }

        if (cart.isNotEmpty()) {
            Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text("جمع کل", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${Format.money(totals.third)} افغانی",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold)
                    }
                    Button(onClick = { showCheckout = true }) { Text("تسویه") }
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
                    val result = app.sales.checkout(cart, discountType, discountValue, method, debtorId, paid)
                    when (result) {
                        is SaleResult.Success -> {
                            toast = "فروش ثبت شد — فاکتور #${Format.toFa(result.invoiceNumber.toString())}"
                            cart = emptyList()
                            showCheckout = false
                            products = app.db.products().all()
                            stockOf = products.associate { it.id to app.stock.stockOf(it.id) }
                        }
                        is SaleResult.NotEnoughStock -> { toast = result.message; showCheckout = false }
                        is SaleResult.Invalid -> toast = result.message
                    }
                }
            },
        )
    }

    toast?.let { message ->
        AlertDialog(
            onDismissRequest = { toast = null },
            confirmButton = { TextButton(onClick = { toast = null }) { Text("باشه") } },
            text = { Text(message) },
        )
    }
}

@Composable
private fun ProductRow(
    product: ProductEntity,
    available: Double,
    inCart: Double,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
) {
    val outOfStock = available <= 0.0
    Card(
        Modifier.fillMaxWidth().clickable(enabled = !outOfStock, onClick = onAdd),
    ) {
        Row(
            Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(product.name, style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold)
                Text(
                    "${Format.money(product.salePrice)} افغانی · موجودی ${Format.number(available)} ${product.unit}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (outOfStock) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (inCart > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onRemove) { Icon(Icons.Filled.Remove, "کم کردن") }
                    Text(Format.number(inCart), fontWeight = FontWeight.Bold)
                    IconButton(onClick = onAdd, enabled = inCart < available) {
                        Icon(Icons.Filled.Add, "افزودن")
                    }
                }
            } else if (!outOfStock) {
                IconButton(onClick = onAdd) { Icon(Icons.Filled.Add, "افزودن") }
            }
        }
    }
}

@Composable
private fun CartRow(line: CartLine, onDelete: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(line.product.name, style = MaterialTheme.typography.bodyMedium)
                Text("${Format.number(line.quantity)} × ${Format.money(line.product.salePrice)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("${Format.money(line.lineTotal)}", fontWeight = FontWeight.Bold)
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "حذف") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CheckoutSheet(
    cart: List<CartLine>,
    debtors: List<DebtorEntity>,
    onDismiss: () -> Unit,
    onConfirm: (String, Double, String, String?, Double) -> Unit,
) {
    val app = TohidApp.instance
    var discountType by remember { mutableStateOf("percent") }
    var discountText by remember { mutableStateOf("0") }
    var method by remember { mutableStateOf("cash") }
    var debtorId by remember { mutableStateOf<String?>(null) }
    var paidText by remember { mutableStateOf("") }

    val discountValue = discountText.toDoubleOrNull() ?: 0.0
    val (subtotal, discount, finalTotal) = app.sales.computeTotals(cart, discountType, discountValue)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("تسویه فروش", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = discountType == "percent",
                    onClick = { discountType = "percent" }, label = { Text("درصدی") })
                FilterChip(selected = discountType == "amount",
                    onClick = { discountType = "amount" }, label = { Text("مبلغی") })
            }
            OutlinedTextField(
                value = discountText, onValueChange = { discountText = it },
                label = { Text("تخفیف") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = method == "cash",
                    onClick = { method = "cash" }, label = { Text("نقدی") })
                FilterChip(selected = method == "credit",
                    onClick = { method = "credit" }, label = { Text("نسیه") })
            }

            if (method == "credit") {
                if (debtors.isEmpty()) {
                    Text("قرض‌داری ثبت نشده است. برای فروش نسیه اول قرض‌دار اضافه کنید.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error)
                } else {
                    debtors.forEach { d ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = debtorId == d.id, onClick = { debtorId = d.id })
                            Text(d.name)
                        }
                    }
                }
                OutlinedTextField(
                    value = paidText, onValueChange = { paidText = it },
                    label = { Text("مبلغ پرداختی") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            HorizontalDivider()
            SummaryRow("جمع", subtotal)
            if (discount > 0) SummaryRow("تخفیف", discount)
            SummaryRow("قابل پرداخت", finalTotal, bold = true)

            Button(
                onClick = {
                    onConfirm(discountType, discountValue, method, debtorId,
                        paidText.toDoubleOrNull() ?: 0.0)
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("ثبت فروش") }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: Double, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("${Format.money(value)} افغانی",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
    }
}
