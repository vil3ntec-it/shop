package af.tohid.shop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Warning
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
import af.tohid.shop.TohidApp
import af.tohid.shop.data.repo.Shortage
import af.tohid.shop.ui.components.*
import af.tohid.shop.ui.theme.Radius
import af.tohid.shop.ui.theme.T
import af.tohid.shop.util.Format

@Composable
fun DashboardScreen(onOpen: (String) -> Unit) {
    val app = TohidApp.instance

    val products by app.db.products().observeAll().collectAsState(initial = emptyList())
    val debtors by app.db.debtors().observeAll().collectAsState(initial = emptyList())
    val txns by app.db.transactions().observeAll().collectAsState(initial = emptyList())
    val expenses by app.db.expenses().observeAll().collectAsState(initial = emptyList())
    val sales by app.db.sales().observeAll().collectAsState(initial = emptyList())
    val warehouse by app.db.warehouse().observeAll().collectAsState(initial = emptyList())

    var shortages by remember { mutableStateOf<List<Shortage>>(emptyList()) }
    var lowStock by remember { mutableStateOf<List<Pair<String, Double>>>(emptyList()) }

    LaunchedEffect(products, warehouse, sales) {
        shortages = app.stock.shortages(emptyMap())
        lowStock = products.mapNotNull { p ->
            val s = app.stock.stockOf(p.id)
            if (s <= p.minStock) p.name to s else null
        }
    }

    val balances = remember(debtors, txns) {
        debtors.associate { d ->
            d.id to txns.filter { it.debtorId == d.id }
                .sumOf { if (it.type == "give") it.amount else -it.amount }
        }
    }
    val totalDebt = remember(balances) { balances.values.filter { it > 0 }.sum() }
    val debtorsWithDebt = remember(balances) { balances.values.count { it > 0 } }

    val monthPrefix = remember { Format.today().take(7) }
    val expenseMonth = remember(expenses) {
        expenses.filter { it.date.startsWith(monthPrefix) }.sumOf { it.amount }
    }
    val salesMonth = remember(sales) {
        sales.filter { it.status != "cancelled" && it.date.startsWith(monthPrefix) }
            .sumOf { it.finalTotal }
    }
    val salesToday = remember(sales) {
        val today = Format.today()
        sales.filter { it.status != "cancelled" && it.date == today }.sumOf { it.finalTotal }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(T.surface)
            .verticalScrollCompat()
            .padding(16.dp),
    ) {
        val shopName = app.session.shopName()
        PageToolbar(
            title = if (shopName.isBlank()) "داشبورد" else shopName,
            subtitle = "فروش امروز: ${Format.money(salesToday)} افغانی",
        )

        // مهم‌ترین چیزی که دکاندار باید فوری ببیند
        shortages.forEach { sh ->
            AlertCard("کسری موجودی — ${sh.productName}", app.stock.message(sh))
            Spacer(Modifier.height(10.dp))
        }

        StatRow {
            StatCard(
                "وضعیت انبار", Format.toFa(products.size.toString()),
                Icons.Outlined.Inventory2, Tone.Blue, "قلم کالا", Modifier.weight(1f),
            ) { onOpen("products") }
            StatCard(
                "قرض‌داران", Format.toFa(debtorsWithDebt.toString()),
                Icons.Outlined.People, Tone.Orange, "نفر", Modifier.weight(1f),
            ) { onOpen("debtors") }
        }
        Spacer(Modifier.height(12.dp))
        StatRow {
            StatCard(
                "مجموع بدهی", Format.money(totalDebt),
                Icons.Outlined.CreditCard, Tone.Red, "افغانی", Modifier.weight(1f),
            ) { onOpen("debtors") }
            StatCard(
                "مصارف این ماه", Format.money(expenseMonth),
                Icons.Outlined.ReceiptLong, Tone.Green, "افغانی", Modifier.weight(1f),
            ) { onOpen("expenses") }
        }
        Spacer(Modifier.height(12.dp))
        StatRow {
            StatCard(
                "فروش این ماه", Format.money(salesMonth),
                Icons.Outlined.TrendingUp, Tone.Blue, "افغانی", Modifier.weight(1f),
            ) { onOpen("sales") }
            StatCard(
                "کالای رو به اتمام", Format.toFa(lowStock.size.toString()),
                Icons.Outlined.Warning, if (lowStock.isEmpty()) Tone.Green else Tone.Orange,
                "قلم", Modifier.weight(1f),
            ) { onOpen("warehouse") }
        }

        Spacer(Modifier.height(16.dp))

        TPanel(
            title = "موجودی رو به اتمام",
            modifier = Modifier.fillMaxWidth(),
            actionLabel = "انبار",
            onAction = { onOpen("warehouse") },
        ) {
            if (lowStock.isEmpty()) {
                Text(
                    if (products.isEmpty()) "هنوز کالایی ثبت نشده است."
                    else "موجودی همه‌ی کالاها کافی است.",
                    fontSize = 12.5.sp,
                    color = T.muted,
                )
            } else {
                lowStock.take(6).forEachIndexed { i, (name, stock) ->
                    if (i > 0) Divider()
                    LowStockRow(name, stock)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        TPanel(
            title = "قرض‌داران",
            modifier = Modifier.fillMaxWidth(),
            actionLabel = "مشاهده همه",
            onAction = { onOpen("debtors") },
        ) {
            val top = debtors
                .map { it to (balances[it.id] ?: 0.0) }
                .filter { it.second > 0 }
                .sortedByDescending { it.second }
                .take(5)
            if (top.isEmpty()) {
                Text("هیچ بدهی بازی وجود ندارد.", fontSize = 12.5.sp, color = T.muted)
            } else {
                top.forEachIndexed { i, (d, balance) ->
                    if (i > 0) Divider()
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            d.name,
                            fontSize = 12.5.sp,
                            color = T.text,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${Format.money(balance)} افغانی",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = T.danger,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        TPanel(
            title = "مصارف اخیر",
            modifier = Modifier.fillMaxWidth(),
            actionLabel = "مشاهده همه",
            onAction = { onOpen("expenses") },
        ) {
            val recent = expenses.take(5)
            if (recent.isEmpty()) {
                Text("هنوز مصرفی ثبت نشده است.", fontSize = 12.5.sp, color = T.muted)
            } else {
                recent.forEachIndexed { i, e ->
                    if (i > 0) Divider()
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                e.title,
                                fontSize = 12.5.sp,
                                color = T.text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(Format.shortDate(e.date), fontSize = 10.5.sp, color = T.muted2)
                        }
                        Text(
                            "${Format.money(e.amount)} افغانی",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = T.warning,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(90.dp))
    }
}

/* ------------------------------------------------------------------ */

@Composable
private fun AlertCard(title: String, body: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(T.dangerTint)
            .padding(14.dp),
    ) {
        Icon(
            Icons.Outlined.Warning,
            contentDescription = null,
            tint = T.danger,
            modifier = Modifier.size(18.dp).padding(top = 2.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = T.danger)
            Spacer(Modifier.height(5.dp))
            Text(body, fontSize = 12.sp, color = T.text, lineHeight = 23.sp)
        }
    }
}

@Composable
private fun LowStockRow(name: String, stock: Double) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            name,
            fontSize = 12.5.sp,
            color = T.text,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Badge(
            if (stock <= 0) "تمام‌شده" else "${Format.number(stock)} مانده",
            if (stock <= 0) Tone.Red else Tone.Orange,
        )
    }
}
