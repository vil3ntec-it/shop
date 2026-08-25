package af.tohid.shop.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import af.tohid.shop.TohidApp
import af.tohid.shop.data.remote.*
import af.tohid.shop.data.sync.SyncScheduler
import af.tohid.shop.util.Format
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ورود به حساب، ساخت یا پیوستن به دکان، و همگام‌سازی دستی.
 * این صفحه همان کاری را می‌کند که پنل «دکان مشترک» در نسخه وب انجام می‌داد.
 */
@Composable
fun ShopScreen() {
    val app = TohidApp.instance
    val scope = rememberCoroutineScope()

    var server by remember { mutableStateOf(app.session.serverUrl() ?: "") }
    var identifier by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var shopName by remember { mutableStateOf("") }
    var inviteCode by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var loggedIn by remember { mutableStateOf(app.session.isLoggedIn()) }
    var shopTitle by remember { mutableStateOf(app.session.shopName()) }
    var generatedCode by remember { mutableStateOf<String?>(null) }

    fun run(block: suspend () -> String) {
        busy = true; message = null
        scope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { block() } }
            busy = false
            message = result.getOrElse { it.message ?: "خطای ناشناخته" }
            loggedIn = app.session.isLoggedIn()
            shopTitle = app.session.shopName()
        }
    }

    ScreenScaffold("دکان و همگام‌سازی", "دفتر دکان را بین گوشی‌ها یکی نگه می‌دارد") {

        OutlinedTextField(
            value = server, onValueChange = { server = it },
            label = { Text("آدرس سرور") },
            placeholder = { Text("https://shop.example.com") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "برای اینکه از هر جای دنیا وصل شود، آدرس باید عمومی باشد (مثلاً Cloudflare Tunnel).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (!loggedIn) {
            OutlinedTextField(
                value = identifier, onValueChange = { identifier = it },
                label = { Text("ایمیل یا شماره موبایل") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password, onValueChange = { password = it },
                label = { Text("رمز عبور") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    run {
                        app.session.setServerUrl(server)
                        ApiClient.invalidate()
                        val api = ApiClient.api(app.session) ?: error("آدرس سرور نامعتبر است")
                        val res = api.login(LoginRequest(identifier.trim(), password))
                        app.session.saveSession(
                            res.user.id,
                            res.user.name.ifBlank { res.user.email ?: res.user.phone ?: "" },
                            res.accessToken, res.refreshToken,
                        )
                        val me = api.shopMe()
                        me.shop?.let { app.session.saveShop(it.id, it.name, it.myRole) }
                        "وارد شدید."
                    }
                },
                enabled = !busy, modifier = Modifier.fillMaxWidth(),
            ) { Text(if (busy) "صبر کنید…" else "ورود") }
        } else {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("کاربر: ${app.session.userLabel()}", style = MaterialTheme.typography.bodyMedium)
                    if (shopTitle.isNotBlank()) {
                        Text("دکان: $shopTitle", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "نقش: " + if (app.session.role() == "owner") "صاحب دکان" else "شاگرد",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text("آخرین همگام‌سازی: ${Format.ago(app.session.lastSyncAt())}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (shopTitle.isBlank()) {
                OutlinedTextField(
                    value = shopName, onValueChange = { shopName = it },
                    label = { Text("نام دکان") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        run {
                            val api = ApiClient.api(app.session) ?: error("آدرس سرور نامعتبر است")
                            val res = api.createShop(CreateShopRequest(shopName.trim(), 5))
                            res.shop?.let { app.session.saveShop(it.id, it.name, it.myRole) }
                            "دکان ساخته شد."
                        }
                    },
                    enabled = !busy, modifier = Modifier.fillMaxWidth(),
                ) { Text("ساخت دکان") }

                Text("یا", style = MaterialTheme.typography.bodySmall)

                OutlinedTextField(
                    value = inviteCode, onValueChange = { inviteCode = it.uppercase() },
                    label = { Text("کد دعوت") }, placeholder = { Text("ABCD-1234") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(
                    onClick = {
                        run {
                            val api = ApiClient.api(app.session) ?: error("آدرس سرور نامعتبر است")
                            val res = api.joinShop(JoinShopRequest(inviteCode.trim()))
                            res.shop?.let { app.session.saveShop(it.id, it.name, it.myRole) }
                            "به دکان پیوستید."
                        }
                    },
                    enabled = !busy, modifier = Modifier.fillMaxWidth(),
                ) { Text("پیوستن به دکان") }
            } else {
                if (app.session.role() == "owner") {
                    OutlinedButton(
                        onClick = {
                            run {
                                val api = ApiClient.api(app.session) ?: error("آدرس سرور نامعتبر است")
                                val res = api.invite(InviteRequest("staff"))
                                generatedCode = res.code
                                "کد دعوت ساخته شد."
                            }
                        },
                        enabled = !busy, modifier = Modifier.fillMaxWidth(),
                    ) { Text("ساخت کد دعوت") }

                    generatedCode?.let { code ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp)) {
                                Text(code, style = MaterialTheme.typography.headlineSmall)
                                Text("این کد را به شاگردتان بدهید. یک هفته معتبر است.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        run {
                            val r = app.sync.sync()
                            "همگام شد — ${Format.toFa(r.pushed.toString())} رفت، " +
                                "${Format.toFa(r.pulled.toString())} آمد."
                        }
                    },
                    enabled = !busy, modifier = Modifier.fillMaxWidth(),
                ) { Text("همگام‌سازی حالا") }

                OutlinedButton(
                    onClick = { SyncScheduler.syncNow(app) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("همگام‌سازی در پس‌زمینه") }
            }

            OutlinedButton(
                onClick = { app.session.clearSession(); ApiClient.invalidate(); loggedIn = false
                            shopTitle = ""; message = "از حساب خارج شدید. دفتر دکان روی این گوشی می‌ماند." },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("خروج از حساب") }
        }

        message?.let {
            InfoPanel("پیام", it, PanelTone.Neutral)
        }
    }
}
