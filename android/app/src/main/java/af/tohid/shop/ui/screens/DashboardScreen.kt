package af.tohid.shop.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import af.tohid.shop.TohidApp
import af.tohid.shop.data.repo.Shortage
import af.tohid.shop.util.Format

@Composable
fun DashboardScreen() {
    val app = TohidApp.instance
    var productCount by remember { mutableStateOf(0) }
    var shortages by remember { mutableStateOf<List<Shortage>>(emptyList()) }

    LaunchedEffect(Unit) {
        productCount = app.db.products().all().size
        shortages = app.stock.shortages(emptyMap())
    }

    ScreenScaffold(
        title = "داشبورد",
        subtitle = if (app.session.shopName().isNotBlank()) app.session.shopName() else null,
    ) {
        // کسری موجودی: مهم‌ترین چیزی که دکاندار باید فوری ببیند
        shortages.forEach { sh ->
            InfoPanel(
                title = "کسری موجودی — ${sh.productName}",
                body = app.stock.message(sh),
                tone = Tone.Danger,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("تعداد کالا", Format.toFa(productCount.toString()), Modifier.weight(1f))
            StatCard("آخرین همگام‌سازی", Format.ago(app.session.lastSyncAt()), Modifier.weight(1f))
        }

        if (productCount == 0) {
            EmptyState("هنوز کالایی ثبت نشده است.")
        }
    }
}
