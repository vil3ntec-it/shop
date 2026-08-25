package af.tohid.shop.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import af.tohid.shop.TohidApp
import af.tohid.shop.data.remote.ApiClient
import af.tohid.shop.data.remote.PlanDto
import af.tohid.shop.data.remote.PlansResponse
import af.tohid.shop.util.Format
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * دروازه‌ی قابلیت‌های اشتراکی.
 * اگر قابلیت باز باشد محتوا نشان داده می‌شود، وگرنه پیشنهاد اشتراک.
 *
 * پرداخت داخل برنامه انجام نمی‌شود؛ خرید از راه واتساپ هماهنگ می‌شود.
 */
@Composable
fun VipGate(
    feature: String,
    title: String,
    content: @Composable () -> Unit,
) {
    val app = TohidApp.instance
    var allowed by remember { mutableStateOf(app.entitlement.has(feature)) }

    LaunchedEffect(feature) {
        app.entitlement.refresh()
        allowed = app.entitlement.has(feature)
    }

    if (allowed) content() else LockedPanel(feature, title)
}

@Composable
private fun LockedPanel(feature: String, title: String) {
    val app = TohidApp.instance
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    var plans by remember { mutableStateOf<PlansResponse?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        plans = withContext(Dispatchers.IO) {
            runCatching { ApiClient.api(app.session)?.plans() }.getOrNull()
        }
        loading = false
    }

    ScreenScaffold(title) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                Modifier.padding(20.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("👑", style = MaterialTheme.typography.headlineMedium)
                Text(title, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(
                    app.entitlement.statusText(),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }

        when {
            loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
            plans == null -> InfoPanel(
                "پلن‌ها در دسترس نیست",
                "برای دیدن اشتراک‌ها، آدرس سرور را در «دکان و همگام‌سازی» وارد کنید.",
            )
            else -> {
                val data = plans!!
                Text("اشتراک‌ها", style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold)
                data.plans.forEach { plan -> PlanRow(plan, data.currency, uriHandler::openUri) }
                if (data.whatsapp.url.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = { uriHandler.openUri(data.whatsapp.url) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("تماس در واتساپ — ${Format.toFa(data.whatsapp.number)}") }
                    Text(
                        "برای خرید، پلن مورد نظر را بزنید تا در واتساپ هماهنگ شود.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlanRow(plan: PlanDto, currency: String, openUri: (String) -> Unit) {
    Card(
        onClick = { if (plan.whatsappUrl.isNotBlank()) openUri(plan.whatsappUrl) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(14.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(plan.title, style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold)
                    if (plan.badge.isNotBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiary,
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Text(plan.badge,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                }
                plan.pricePerDay?.let {
                    Text("روزی حدود ${Format.number(it)} $currency",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                if (plan.negotiable) "توافقی" else "${Format.money(plan.price.toDouble())} $currency",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
