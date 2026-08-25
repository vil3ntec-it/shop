package af.tohid.shop.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ReceiptLong
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
import af.tohid.shop.data.db.SaleEntity
import af.tohid.shop.data.db.SaleItemEntity
import af.tohid.shop.data.repo.OpResult
import af.tohid.shop.ui.components.*
import af.tohid.shop.ui.theme.T
import af.tohid.shop.util.Format

@Composable
fun SalesHistoryScreen() {
    val app = TohidApp.instance
    val scope = rememberCoroutineScope()

    val sales by app.db.sales().observeAll().collectAsState(initial = emptyList())
    val products by app.db.products().observeAll().collectAsState(initial = emptyList())
    val nameOf = remember(products) { products.associate { it.id to it.name } }

    var query by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("") }   // "" | completed | cancelled
    var openSale by remember { mutableStateOf<SaleEntity?>(null) }
    var notice by remember { mutableStateOf<Pair<String, Tone>?>(null) }

    val visible = remember(sales, query, statusFilter) {
        sales.filter { s ->
            val q = query.trim()
            val mq = q.isEmpty() ||
                s.invoiceNumber.toString().contains(q) ||
                Format.toFa(s.invoiceNumber.toString()).contains(q) ||
                s.date.contains(q)
            val ms = statusFilter.isEmpty() || s.status == statusFilter
            mq && ms
        }
    }
    val totalShown = remember(visible) {
        visible.filter { it.status != "cancelled" }.sumOf { it.finalTotal }
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            PageToolbar(
                title = "تاریخچه فروش",
                subtitle = "${Format.toFa(visible.size.toString())} فاکتور — ${Format.money(totalShown)} افغانی",
            )
            SearchField(query, { query = it }, "شماره فاکتور یا تاریخ…")
            Spacer(Modifier.height(10.dp))
            LazyRowChips(
                options = listOf("" to "همه", "completed" to "ثبت‌شده", "cancelled" to "لغوشده"),
                selected = statusFilter,
                onSelect = { statusFilter = it },
            )
            notice?.let {
                Spacer(Modifier.height(12.dp))
                Notice(it.first, it.second)
            }
        }

        if (visible.isEmpty()) {
            TCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp), padding = 0.dp) {
                EmptyState(
                    icon = Icons.Outlined.ReceiptLong,
                    title = if (sales.isEmpty()) "هنوز فروشی ثبت نشده" else "نتیجه‌ای یافت نشد",
                    subtitle = if (sales.isEmpty())
                        "پس از اولین فروش، فاکتورها اینجا نگهداری می‌شوند."
                    else "با این جستجو یا فیلتر فاکتوری پیدا نشد.",
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(visible, key = { it.id }) { s ->
                    SaleRow(s) { openSale = s }
                }
            }
        }
    }

    openSale?.let { sale ->
        SaleDetailSheet(
            sale = sale,
            nameOf = nameOf,
            onDismiss = { openSale = null },
            loadItems = { app.db.saleItems().forSale(sale.id) },
            onCancel = {
                scope.launch {
                    when (val r = app.sales.cancelSale(sale.id)) {
                        is OpResult.Refused -> notice = r.message to Tone.Red
                        is OpResult.OkWithWarning -> { notice = r.message to Tone.Orange; openSale = null }
                        OpResult.Ok -> {
                            notice = "فاکتور #${Format.toFa(sale.invoiceNumber.toString())} لغو شد و موجودی برگشت." to Tone.Green
                            openSale = null
                        }
                    }
                }
            },
            onReturn = { itemId, qty ->
                scope.launch {
                    when (val r = app.sales.returnItem(itemId, qty, "مرجوعی مشتری")) {
                        is OpResult.Refused -> notice = r.message to Tone.Red
                        is OpResult.OkWithWarning -> { notice = r.message to Tone.Orange; openSale = null }
                        OpResult.Ok -> { notice = "مرجوعی ثبت شد و موجودی برگشت." to Tone.Green; openSale = null }
                    }
                }
            },
        )
    }
}

/* ------------------------------------------------------------------ */

@Composable
private fun SaleRow(s: SaleEntity, onClick: () -> Unit) {
    val cancelled = s.status == "cancelled"
    TCard(Modifier.fillMaxWidth(), padding = 14.dp, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "فاکتور #${Format.toFa(s.invoiceNumber.toString())}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (cancelled) T.muted else T.text,
                )
                Spacer(Modifier.height(3.dp))
                Text(Format.shortDate(s.date), fontSize = 11.5.sp, color = T.muted)
            }
            Badge(
                when {
                    cancelled -> "لغوشده"
                    s.remaining > 0 -> "نسیه"
                    else -> "نقد"
                },
                when {
                    cancelled -> Tone.Red
                    s.remaining > 0 -> Tone.Orange
                    else -> Tone.Green
                },
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SmallStat("مبلغ کل", "${Format.money(s.finalTotal)} افغانی")
            if (s.discount > 0) SmallStat("تخفیف", "${Format.money(s.discount)} افغانی")
            if (s.remaining > 0) SmallStat("باقی‌مانده", "${Format.money(s.remaining)} افغانی")
        }
    }
}

@Composable
private fun SmallStat(label: String, value: String) {
    Column {
        Text(label, fontSize = 10.5.sp, color = T.muted2)
        Spacer(Modifier.height(2.dp))
        Text(value, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = T.text, maxLines = 1)
    }
}

/* ------------------------------------------------------------------ */

@Composable
private fun SaleDetailSheet(
    sale: SaleEntity,
    nameOf: Map<String, String>,
    onDismiss: () -> Unit,
    loadItems: suspend () -> List<SaleItemEntity>,
    onCancel: () -> Unit,
    onReturn: (String, Double) -> Unit,
) {
    var items by remember(sale.id) { mutableStateOf<List<SaleItemEntity>>(emptyList()) }
    var confirmCancel by remember { mutableStateOf(false) }
    var returning by remember { mutableStateOf<SaleItemEntity?>(null) }

    LaunchedEffect(sale.id) { items = loadItems() }

    FormSheet("فاکتور #${Format.toFa(sale.invoiceNumber.toString())}", onDismiss) {
        Text(Format.shortDate(sale.date), fontSize = 12.sp, color = T.muted)
        Spacer(Modifier.height(14.dp))

        items.forEach { it2 ->
            Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        nameOf[it2.productId] ?: "کالای حذف‌شده",
                        fontSize = 12.5.sp,
                        color = T.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${Format.number(it2.quantity)} × ${Format.money(it2.unitPrice)}" +
                            if (it2.returnedQty > 0) "  (مرجوعی: ${Format.number(it2.returnedQty)})" else "",
                        fontSize = 10.5.sp,
                        color = T.muted,
                    )
                }
                Text(
                    Format.money(it2.totalPrice),
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = T.text,
                )
                if (sale.status != "cancelled" && it2.quantity - it2.returnedQty > 0) {
                    Spacer(Modifier.width(8.dp))
                    TButton("مرجوعی", { returning = it2 }, kind = BtnKind.Secondary, small = true)
                }
            }
            Divider()
        }

        Spacer(Modifier.height(8.dp))
        SummaryRow("جمع اقلام", "${Format.money(sale.total)} افغانی")
        if (sale.discount > 0) SummaryRow("تخفیف", "− ${Format.money(sale.discount)} افغانی")
        if (sale.paidAmount > 0) SummaryRow("پرداخت‌شده", "${Format.money(sale.paidAmount)} افغانی")
        if (sale.remaining > 0) {
            SummaryRow("باقی‌مانده (نسیه)", "${Format.money(sale.remaining)} افغانی", valueColor = T.danger)
        }
        Divider()
        SummaryRow("مبلغ نهایی", "${Format.money(sale.finalTotal)} افغانی", bold = true)

        if (sale.status == "cancelled") {
            Spacer(Modifier.height(14.dp))
            Notice("این فاکتور لغو شده است. موجودی کالاها برگشته و در گزارش‌ها شمرده نمی‌شود.", Tone.Red)
            Spacer(Modifier.height(14.dp))
            TButton("بستن", onDismiss, Modifier.fillMaxWidth(), kind = BtnKind.Secondary)
        } else {
            FormActions(
                confirmLabel = "بستن",
                onConfirm = onDismiss,
                onCancel = onDismiss,
                deleteLabel = "لغو کامل فاکتور",
                onDelete = { confirmCancel = true },
            )
        }
    }

    if (confirmCancel) {
        ConfirmDialog(
            title = "لغو فاکتور #${Format.toFa(sale.invoiceNumber.toString())}؟",
            message = "همه‌ی کالاهای این فاکتور به موجودی برمی‌گردند و مبلغ آن از گزارش‌ها کم می‌شود." +
                if (sale.debtGiven > 0) " بدهی ثبت‌شده برای این فاکتور هم صفر می‌شود." else "",
            confirmLabel = "لغو فاکتور",
            danger = true,
            onConfirm = { confirmCancel = false; onCancel() },
            onDismiss = { confirmCancel = false },
        )
    }

    returning?.let { item ->
        val max = item.quantity - item.returnedQty
        AmountSheet(
            title = "مرجوعی «${nameOf[item.productId] ?: ""}»",
            confirmLabel = "ثبت مرجوعی",
            hint = "حداکثر ${Format.number(max)} واحد قابل مرجوع است. " +
                "این مقدار به موجودی انبار برمی‌گردد.",
            onDismiss = { returning = null },
            onConfirm = { qty, _ -> returning = null; onReturn(item.id, qty) },
        )
    }
}
