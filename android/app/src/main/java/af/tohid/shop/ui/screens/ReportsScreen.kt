package af.tohid.shop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import af.tohid.shop.TohidApp
import af.tohid.shop.data.db.SaleItemEntity
import af.tohid.shop.ui.components.*
import af.tohid.shop.ui.theme.T
import af.tohid.shop.util.Format
import java.util.Calendar

/** بازه‌های آماده‌ی گزارش. */
private enum class Range(val key: String, val label: String) {
    Today("today", "امروز"),
    Week("week", "۷ روز اخیر"),
    Month("month", "این ماه"),
    All("all", "همه"),
}

@Composable
fun ReportsScreen() {
    val app = TohidApp.instance

    val sales by app.db.sales().observeAll().collectAsState(initial = emptyList())
    val expenses by app.db.expenses().observeAll().collectAsState(initial = emptyList())
    val products by app.db.products().observeAll().collectAsState(initial = emptyList())
    val nameOf = remember(products) { products.associate { it.id to it.name } }

    var range by remember { mutableStateOf(Range.Month.key) }
    var items by remember { mutableStateOf<List<SaleItemEntity>>(emptyList()) }

    LaunchedEffect(sales) {
        items = app.db.saleItems().all()
    }

    val from = remember(range) { rangeStart(range) }

    val activeSales = remember(sales, from) {
        sales.filter { it.status != "cancelled" && it.date >= from }
    }
    val saleIds = remember(activeSales) { activeSales.map { it.id }.toSet() }
    val activeItems = remember(items, saleIds) { items.filter { it.saleId in saleIds } }

    val revenue = remember(activeSales) { activeSales.sumOf { it.finalTotal } }
    val expenseTotal = remember(expenses, from) {
        expenses.filter { it.date >= from }.sumOf { it.amount }
    }

    /*
     * سود ناخالص = (فروش خالص هر قلم) − (قیمت خرید همان تعداد خالص).
     * مرجوعی هم از فروش و هم از قیمت خرید کم می‌شود، پس دو بار جریمه نمی‌شود —
     * همان اصلاحی که در نسخه‌ی وب انجام شد.
     */
    val grossProfit = remember(activeItems) {
        activeItems.sumOf { i ->
            val netQty = i.quantity - i.returnedQty
            netQty * (i.unitPrice - i.purchasePrice)
        }
    }
    val netProfit = grossProfit - expenseTotal

    val topProducts = remember(activeItems) {
        activeItems.groupBy { it.productId }
            .map { (pid, list) ->
                val qty = list.sumOf { it.quantity - it.returnedQty }
                val amount = list.sumOf { (it.quantity - it.returnedQty) * it.unitPrice }
                Triple(pid, qty, amount)
            }
            .filter { it.second > 0 }
            .sortedByDescending { it.third }
            .take(8)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(T.surface)
            .verticalScrollCompat()
            .padding(16.dp),
    ) {
        PageToolbar("گزارش‌ها", "سود، فروش و مصارف")

        LazyRowChips(Range.entries.map { it.key to it.label }, range) { range = it }

        Spacer(Modifier.height(14.dp))
        StatRow {
            StatCard(
                "فروش", Format.money(revenue),
                Icons.Outlined.ShoppingCart, Tone.Blue, "افغانی", Modifier.weight(1f),
            )
            StatCard(
                "سود ناخالص", Format.money(grossProfit),
                Icons.Outlined.TrendingUp,
                if (grossProfit >= 0) Tone.Green else Tone.Red, "افغانی", Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(12.dp))
        StatRow {
            StatCard(
                "مصارف", Format.money(expenseTotal),
                Icons.Outlined.ReceiptLong, Tone.Orange, "افغانی", Modifier.weight(1f),
            )
            StatCard(
                "سود خالص", Format.money(netProfit),
                Icons.Outlined.AccountBalanceWallet,
                if (netProfit >= 0) Tone.Green else Tone.Red, "افغانی", Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(16.dp))
        TPanel("خلاصه‌ی حساب", Modifier.fillMaxWidth()) {
            SummaryRow("تعداد فاکتور", Format.toFa(activeSales.size.toString()))
            Divider()
            SummaryRow("فروش کل", "${Format.money(revenue)} افغانی")
            Divider()
            SummaryRow("سود ناخالص", "${Format.money(grossProfit)} افغانی")
            Divider()
            SummaryRow("مصارف", "− ${Format.money(expenseTotal)} افغانی")
            Divider()
            SummaryRow(
                "سود خالص", "${Format.money(netProfit)} افغانی",
                bold = true,
                valueColor = if (netProfit >= 0) T.success else T.danger,
            )
        }

        Spacer(Modifier.height(12.dp))
        TPanel("پرفروش‌ترین کالاها", Modifier.fillMaxWidth()) {
            if (topProducts.isEmpty()) {
                Text("در این بازه فروشی ثبت نشده است.", fontSize = 12.5.sp, color = T.muted)
            } else {
                topProducts.forEachIndexed { i, (pid, qty, amount) ->
                    if (i > 0) Divider()
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                nameOf[pid] ?: "کالای حذف‌شده",
                                fontSize = 12.5.sp,
                                color = T.text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text("${Format.number(qty)} واحد", fontSize = 10.5.sp, color = T.muted2)
                        }
                        Text(
                            "${Format.money(amount)} افغانی",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = T.text,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(90.dp))
    }
}

/** ابتدای بازه به شکل YYYY-MM-DD — مقایسه‌ی رشته‌ای روی این قالب درست کار می‌کند. */
private fun rangeStart(key: String): String = when (key) {
    "today" -> Format.today()
    "week" -> {
        val c = Calendar.getInstance()
        c.add(Calendar.DAY_OF_YEAR, -6)
        Format.isoOf(c.timeInMillis)
    }
    "month" -> Format.today().take(7) + "-01"
    else -> "0000-00-00"
}
