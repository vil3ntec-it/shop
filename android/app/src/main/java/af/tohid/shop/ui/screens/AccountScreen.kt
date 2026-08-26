package af.tohid.shop.ui.screens

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import af.tohid.shop.TohidApp
import af.tohid.shop.data.remote.*
import af.tohid.shop.data.sync.SyncScheduler
import af.tohid.shop.ui.components.*
import af.tohid.shop.ui.theme.T
import af.tohid.shop.util.Format
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/* ================================================================== */
/*  حساب و دکان                                                        */
/*                                                                     */
/*  ورود با شماره (کد پیامکی)، حساب گوگل یا رمز؛ ساخت دکان یا پیوستن   */
/*  با کد شاگرد؛ مدیریت اعضا و همگام‌سازی.                              */
/* ================================================================== */

private fun deviceOf(app: TohidApp) = DeviceDto(
    deviceId = app.session.deviceId(),
    name = Build.MODEL ?: "",
    platform = "android",
)

/** ذخیره‌ی نشست و به‌روز کردن نقش، دسترسی‌ها و اشتراک. */
private suspend fun applyAuth(app: TohidApp, res: AuthResponse) {
    val label = res.user.name.ifBlank { res.user.phone ?: res.user.email ?: "" }
    app.session.saveSession(res.user.id, label, res.accessToken, res.refreshToken)
    app.session.setAuthSkipped(false)
    res.shop?.let { app.session.saveShop(it.id, it.name, it.role) }
    refreshMe(app)
}

/** خواندن «من» از سرور: نقش، دسترسی‌ها و وضعیت اشتراک. */
private suspend fun refreshMe(app: TohidApp) {
    val api = ApiClient.api(app.session) ?: return
    runCatching {
        val me = api.me()
        me.shop?.let { app.session.saveShop(it.id, it.name, it.role) }
        if (me.shop == null) app.session.clearShop()
        app.session.savePermissions(me.permissions)
        app.entitlement.update(me.entitlement)
    }
}

@Composable
fun AccountScreen() {
    val app = TohidApp.instance
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var tone by remember { mutableStateOf(Tone.Blue) }

    var loggedIn by remember { mutableStateOf(app.session.isLoggedIn()) }
    var shopTitle by remember { mutableStateOf(app.session.shopName()) }
    var role by remember { mutableStateOf(app.session.role()) }
    var config by remember { mutableStateOf<ServerConfigDto?>(null) }
    var members by remember { mutableStateOf<List<MemberDto>>(emptyList()) }
    var codes by remember { mutableStateOf<List<StaffCodeDto>>(emptyList()) }
    var freshCode by remember { mutableStateOf<String?>(null) }
    var pendingJoin by remember { mutableStateOf<String?>(null) }

    fun snapshot() {
        loggedIn = app.session.isLoggedIn()
        shopTitle = app.session.shopName()
        role = app.session.role()
    }

    /** اجرای یک کار شبکه‌ای با پیام و قفل دکمه‌ها. */
    fun submit(success: Tone = Tone.Green, block: suspend () -> String) {
        busy = true
        message = null
        scope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { block() } }
            busy = false
            snapshot()
            result.onSuccess { message = it; tone = success }
                .onFailure { message = ApiErrors.message(it); tone = Tone.Red }
        }
    }

    suspend fun loadShopSide() {
        val api = ApiClient.api(app.session) ?: return
        if (!app.session.isLoggedIn()) return
        if (app.session.can("members.view")) {
            runCatching { members = api.members().members }
        }
        if (app.session.can("staffcode.view")) {
            runCatching { codes = api.staffCodes().codes }
        }
    }

    // بار اول: تنظیمات سرور و وضعیت حساب
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val api = ApiClient.api(app.session)
            if (api != null) {
                runCatching { config = api.serverConfig() }
                if (app.session.isLoggedIn()) {
                    refreshMe(app)
                    app.entitlement.refresh()
                    loadShopSide()
                }
            }
        }
        snapshot()
    }

    ScreenScaffold("حساب و دکان", "ورود، شاگردها و همگام‌سازی با سرور") {

        ServerSection(app, busy) { msg, ok ->
            message = msg
            tone = if (ok) Tone.Green else Tone.Red
            scope.launch {
                withContext(Dispatchers.IO) {
                    runCatching { config = ApiClient.api(app.session)?.serverConfig() }
                }
            }
        }

        if (!ApiClient.isConfigured(app.session)) {
            Notice(
                "بدون آدرس سرور، برنامه کاملاً روی همین گوشی کار می‌کند؛ ولی " +
                    "همگام‌سازی، چند کاربر روی یک دکان و اشتراک نیاز به سرور دارند.",
                Tone.Orange,
            )
        } else if (!loggedIn) {
            LoginSection(
                app = app,
                busy = busy,
                config = config,
                context = context,
                submit = { block -> submit(Tone.Green, block) },
            )
        } else {
            AccountCard(app, shopTitle, role)

            if (shopTitle.isBlank()) {
                ShopStartSection(app, busy, submit = { block -> submit(Tone.Green, block) },
                    onJoinRequested = { code -> pendingJoin = code })
            } else {
                ShopSection(
                    app = app, busy = busy, members = members, codes = codes,
                    freshCode = freshCode,
                    onCodeMade = { freshCode = it },
                    reload = { scope.launch { withContext(Dispatchers.IO) { loadShopSide() } } },
                    submit = { block -> submit(Tone.Green, block) },
                )
            }

            TButton(
                "خروج از حساب",
                kind = BtnKind.Secondary,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    submit(Tone.Blue) {
                        val api = ApiClient.api(app.session)
                        runCatching { api?.logout(LogoutRequest(app.session.refreshToken())) }
                        app.session.clearSession()
                        app.entitlement.clear()
                        ApiClient.invalidate()
                        "از حساب خارج شدید. دفتر دکان روی این گوشی دست‌نخورده ماند."
                    }
                },
            )
        }

        message?.let { Notice(it, tone) }
    }

    // پیوستن به دکان: قبل از آن باید تکلیف دفتر این گوشی روشن شود
    pendingJoin?.let { code ->
        ConfirmDialog(
            title = "پیوستن به دکان",
            message = "اطلاعات دکان از سرور روی این گوشی می‌آید. یادداشت‌ها و " +
                "فروش‌هایی که تا حالا روی همین گوشی ثبت شده پاک می‌شوند تا با " +
                "دفتر دکان قاطی نشوند. ادامه می‌دهید؟",
            confirmLabel = "بله، بپیوند",
            onConfirm = {
                pendingJoin = null
                submit(Tone.Green) {
                    val api = ApiClient.api(app.session) ?: error("آدرس سرور تنظیم نشده است")
                    val res = api.joinShop(JoinShopRequest(code.trim()))
                    res.shop?.let { app.session.saveShop(it.id, it.name, res.role ?: "staff") }
                    app.session.savePermissions(res.permissions)
                    app.entitlement.update(res.entitlement)
                    app.sync.wipeLocal()
                    val pulled = app.sync.restoreAll()
                    "به دکان «${res.shop?.name.orEmpty()}» پیوستید — " +
                        "${Format.toFa(pulled.toString())} رکورد بارگیری شد."
                }
            },
            onDismiss = { pendingJoin = null },
        )
    }
}

/* ---------------- آدرس سرور ---------------- */

@Composable
private fun ServerSection(app: TohidApp, busy: Boolean, onResult: (String, Boolean) -> Unit) {
    var server by remember { mutableStateOf(app.session.serverUrl().orEmpty()) }
    var checking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    TPanel("سرور") {
        TField(
            label = "آدرس سرور",
            value = server,
            onValueChange = { server = it },
            placeholder = "مثلاً 192.168.1.10:3000 یا shop.example.com",
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "دامنه لازم نیست؛ آدرس IP هم کار می‌کند. اگر سرور را جابه‌جا کردید " +
                "فقط همین را عوض کنید — نصب دوباره‌ی برنامه لازم نیست.",
            fontSize = 11.5.sp, color = T.muted, lineHeight = 20.sp,
        )
        Spacer(Modifier.height(10.dp))
        TButton(
            if (checking) "در حال بررسی…" else "ذخیره و بررسی اتصال",
            enabled = !busy && !checking,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                checking = true
                app.session.setServerUrl(server.trim())
                ApiClient.invalidate()
                scope.launch {
                    val ok = withContext(Dispatchers.IO) {
                        runCatching { ApiClient.api(app.session)?.health() }.getOrNull()
                    }
                    checking = false
                    if (ok != null && ok.ok) {
                        onResult("سرور در دسترس است و دیتابیس وصل است.", true)
                    } else {
                        onResult("ارتباط با سرور برقرار نشد. آدرس را بررسی کنید.", false)
                    }
                }
            },
        )
    }
}

/* ---------------- ورود ---------------- */

@Composable
private fun LoginSection(
    app: TohidApp,
    busy: Boolean,
    config: ServerConfigDto?,
    context: android.content.Context,
    submit: ((suspend () -> String)) -> Unit,
) {
    var mode by remember { mutableStateOf("otp") }        // otp | password | register
    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var codeSent by remember { mutableStateOf(false) }
    var identifier by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    val googleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val account = runCatching {
            GoogleSignIn.getSignedInAccountFromIntent(result.data).getResult(ApiException::class.java)
        }.getOrNull()
        val idToken = account?.idToken
        if (idToken.isNullOrBlank()) {
            submit { "ورود با گوگل کامل نشد. دوباره تلاش کنید." }
        } else {
            submit {
                val api = ApiClient.api(app.session) ?: error("آدرس سرور تنظیم نشده است")
                val res = api.googleLogin(GoogleRequest(idToken, deviceOf(app)))
                applyAuth(app, res)
                "با حساب گوگل وارد شدید."
            }
        }
    }

    TPanel("ورود به حساب") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip("با کد پیامکی", mode == "otp") { mode = "otp"; codeSent = false }
            FilterChip("با رمز", mode == "password") { mode = "password" }
            FilterChip("حساب تازه", mode == "register") { mode = "register" }
        }
        Spacer(Modifier.height(12.dp))

        when (mode) {
            "otp" -> {
                TField("شماره موبایل", phone, { phone = it }, placeholder = "07xxxxxxxx", numeric = true)
                if (codeSent) {
                    Spacer(Modifier.height(8.dp))
                    TField("کد پیامک‌شده", code, { code = it }, placeholder = "۶ رقم", numeric = true)
                }
                Spacer(Modifier.height(10.dp))
                if (!codeSent) {
                    TButton(
                        "فرستادن کد", enabled = !busy, modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            submit {
                                val api = ApiClient.api(app.session) ?: error("آدرس سرور تنظیم نشده است")
                                val res = api.otpRequest(OtpRequest(phone.trim()))
                                codeSent = true
                                if (!res.devCode.isNullOrBlank()) {
                                    "کد فرستاده شد (سرور آزمایشی: ${res.devCode})"
                                } else "کد به شماره‌ی شما فرستاده شد."
                            }
                        },
                    )
                } else {
                    TButton(
                        "ورود", enabled = !busy, modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            submit {
                                val api = ApiClient.api(app.session) ?: error("آدرس سرور تنظیم نشده است")
                                val res = api.otpVerify(
                                    OtpVerifyRequest(phone.trim(), code.trim(), name.trim().ifBlank { null }, deviceOf(app))
                                )
                                applyAuth(app, res)
                                "وارد شدید."
                            }
                        },
                    )
                    Spacer(Modifier.height(6.dp))
                    TButton("شماره را عوض کن", kind = BtnKind.Ghost, small = true,
                        onClick = { codeSent = false; code = "" })
                }
            }

            "password" -> {
                TField("ایمیل یا شماره موبایل", identifier, { identifier = it })
                Spacer(Modifier.height(8.dp))
                TField("رمز عبور", password, { password = it }, password = true)
                Spacer(Modifier.height(10.dp))
                TButton(
                    "ورود", enabled = !busy, modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        submit {
                            val api = ApiClient.api(app.session) ?: error("آدرس سرور تنظیم نشده است")
                            val res = api.login(LoginRequest(identifier.trim(), password, deviceOf(app)))
                            applyAuth(app, res)
                            "وارد شدید."
                        }
                    },
                )
            }

            else -> {
                TField("نام شما", name, { name = it })
                Spacer(Modifier.height(8.dp))
                TField("شماره موبایل", phone, { phone = it }, placeholder = "اختیاری", numeric = true)
                Spacer(Modifier.height(8.dp))
                TField("ایمیل", email, { email = it }, placeholder = "اختیاری")
                Spacer(Modifier.height(8.dp))
                TField("رمز عبور", password, { password = it }, password = true)
                Spacer(Modifier.height(4.dp))
                Text(
                    "شماره یا ایمیل — یکی کافی است. هر دو را هم می‌توانید بدهید.",
                    fontSize = 11.5.sp, color = T.muted,
                )
                Spacer(Modifier.height(10.dp))
                TButton(
                    "ساخت حساب", enabled = !busy, modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        submit {
                            val api = ApiClient.api(app.session) ?: error("آدرس سرور تنظیم نشده است")
                            val res = api.register(
                                RegisterRequest(
                                    name = name.trim(),
                                    email = email.trim().ifBlank { null },
                                    phone = phone.trim().ifBlank { null },
                                    password = password,
                                    device = deviceOf(app),
                                )
                            )
                            applyAuth(app, res)
                            "حساب ساخته شد."
                        }
                    },
                )
            }
        }

        val clientId = config?.googleClientId.orEmpty()
        if (clientId.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Divider()
            Spacer(Modifier.height(12.dp))
            TButton(
                "ورود با حساب گوگل",
                kind = BtnKind.Secondary,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestIdToken(clientId)
                        .requestEmail()
                        .build()
                    val client = GoogleSignIn.getClient(context, options)
                    client.signOut()
                    googleLauncher.launch(client.signInIntent)
                },
            )
        }
    }
}

/* ---------------- کارت حساب ---------------- */

@Composable
private fun AccountCard(app: TohidApp, shopTitle: String, role: String) {
    TCard(Modifier.fillMaxWidth()) {
        Text(app.session.userLabel(), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = T.text)
        Spacer(Modifier.height(6.dp))
        if (shopTitle.isNotBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("دکان: $shopTitle", fontSize = 13.sp, color = T.text)
                Badge(
                    when (role) {
                        "owner" -> "صاحب دکان"
                        "manager" -> "مدیر"
                        else -> "شاگرد"
                    },
                    if (role == "owner") Tone.Green else Tone.Blue,
                )
            }
            Spacer(Modifier.height(6.dp))
        }
        Text(app.entitlement.statusText(), fontSize = 12.sp, color = T.muted)
        Spacer(Modifier.height(4.dp))
        Text("آخرین همگام‌سازی: ${Format.ago(app.session.lastSyncAt())}",
            fontSize = 12.sp, color = T.muted)
    }
}

/* ---------------- هنوز دکانی ندارد ---------------- */

@Composable
private fun ShopStartSection(
    app: TohidApp,
    busy: Boolean,
    submit: ((suspend () -> String)) -> Unit,
    onJoinRequested: (String) -> Unit,
) {
    var shopName by remember { mutableStateOf("") }
    var staffCode by remember { mutableStateOf("") }

    TPanel("دکان") {
        Text("اگر صاحب دکان هستید، دکان خود را بسازید:", fontSize = 12.5.sp, color = T.muted)
        Spacer(Modifier.height(8.dp))
        TField("نام دکان", shopName, { shopName = it }, placeholder = "مثلاً دکان توحید")
        Spacer(Modifier.height(10.dp))
        TButton(
            "ساخت دکان", enabled = !busy, modifier = Modifier.fillMaxWidth(),
            onClick = {
                submit {
                    val api = ApiClient.api(app.session) ?: error("آدرس سرور تنظیم نشده است")
                    val res = api.createShop(CreateShopRequest(shopName.trim().ifBlank { "دکان من" }))
                    res.shop?.let { app.session.saveShop(it.id, it.name, res.role ?: "owner") }
                    app.session.savePermissions(res.permissions)
                    app.entitlement.update(res.entitlement)
                    // دفتر همین گوشی به دکان تازه فرستاده می‌شود
                    val out = runCatching { app.sync.sync() }.getOrNull()
                    "دکان ساخته شد" + (out?.let { " — ${Format.toFa(it.pushed.toString())} رکورد فرستاده شد." } ?: ".")
                }
            },
        )

        Spacer(Modifier.height(16.dp))
        Divider()
        Spacer(Modifier.height(16.dp))

        Text("اگر شاگرد هستید، کدی که صاحب دکان داده را بزنید:",
            fontSize = 12.5.sp, color = T.muted)
        Spacer(Modifier.height(8.dp))
        TField("کد شاگرد", staffCode, { staffCode = it.uppercase() }, placeholder = "SHG-XXXX-XXXX-XXXX")
        Spacer(Modifier.height(10.dp))
        TButton(
            "پیوستن به دکان",
            kind = BtnKind.Secondary,
            enabled = !busy && staffCode.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            onClick = { onJoinRequested(staffCode) },
        )
    }
}

/* ---------------- دکان دارد ---------------- */

@Composable
private fun ShopSection(
    app: TohidApp,
    busy: Boolean,
    members: List<MemberDto>,
    codes: List<StaffCodeDto>,
    freshCode: String?,
    onCodeMade: (String) -> Unit,
    reload: () -> Unit,
    submit: ((suspend () -> String)) -> Unit,
) {
    var uses by remember { mutableStateOf("1") }
    var days by remember { mutableStateOf("7") }

    TPanel("همگام‌سازی") {
        TButton(
            "همگام‌سازی حالا", enabled = !busy, modifier = Modifier.fillMaxWidth(),
            onClick = {
                submit {
                    val r = app.sync.sync()
                    "همگام شد — ${Format.toFa(r.pushed.toString())} رفت، " +
                        "${Format.toFa(r.pulled.toString())} آمد."
                }
            },
        )
        Spacer(Modifier.height(8.dp))
        TButton(
            "همگام‌سازی در پس‌زمینه", kind = BtnKind.Secondary,
            modifier = Modifier.fillMaxWidth(),
            onClick = { SyncScheduler.syncNow(app) },
        )
        Spacer(Modifier.height(8.dp))
        TButton(
            "بارگیری دوباره‌ی همه‌چیز از سرور", kind = BtnKind.Ghost, small = true,
            enabled = !busy,
            onClick = {
                submit {
                    val n = app.sync.restoreAll()
                    "${Format.toFa(n.toString())} رکورد از سرور گرفته شد."
                }
            },
        )
    }

    if (app.session.can("staffcode.create")) {
        TPanel("کد شاگرد") {
            Text(
                "این کد را به شاگردتان بدهید تا روی همین دکان وارد شود. " +
                    "کد فقط همین یک بار نشان داده می‌شود.",
                fontSize = 12.sp, color = T.muted, lineHeight = 21.sp,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TField("چند بار قابل استفاده", uses, { uses = it }, numeric = true,
                    modifier = Modifier.weight(1f))
                TField("مهلت (روز)", days, { days = it }, numeric = true,
                    modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            TButton(
                "ساخت کد", enabled = !busy, modifier = Modifier.fillMaxWidth(),
                onClick = {
                    submit {
                        val api = ApiClient.api(app.session) ?: error("آدرس سرور تنظیم نشده است")
                        val res = api.createStaffCode(
                            StaffCodeRequest(
                                role = "staff",
                                maxUses = uses.toIntOrNull() ?: 1,
                                expiresInDays = days.toIntOrNull() ?: 0,
                            )
                        )
                        onCodeMade(res.code)
                        reload()
                        "کد ساخته شد."
                    }
                },
            )

            freshCode?.let { code ->
                Spacer(Modifier.height(12.dp))
                TCard(Modifier.fillMaxWidth()) {
                    Text(code, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = T.primary)
                    Spacer(Modifier.height(4.dp))
                    Text("همین حالا یادداشتش کنید؛ بعداً نشان داده نمی‌شود.",
                        fontSize = 11.5.sp, color = T.muted)
                }
            }

            if (codes.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                codes.forEach { c ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("…${c.hint}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = T.text)
                            Text(
                                "استفاده: ${Format.toFa(c.usedCount.toString())} از " +
                                    (if (c.maxUses == 0) "نامحدود" else Format.toFa(c.maxUses.toString())),
                                fontSize = 11.5.sp, color = T.muted,
                            )
                        }
                        if (c.status == "active") {
                            TButton(
                                "باطل کن", kind = BtnKind.Ghost, small = true, enabled = !busy,
                                onClick = {
                                    submit {
                                        val api = ApiClient.api(app.session) ?: error("آدرس سرور تنظیم نشده است")
                                        api.revokeStaffCode(c.id)
                                        reload()
                                        "کد باطل شد."
                                    }
                                },
                            )
                        } else {
                            Badge(if (c.status == "revoked") "باطل" else "تمام‌شده", Tone.Orange)
                        }
                    }
                }
            }
        }
    }

    if (app.session.can("members.view")) {
        TPanel("اعضای دکان") {
            if (members.isEmpty()) {
                Text("هنوز عضوی ثبت نشده است.", fontSize = 12.5.sp, color = T.muted)
            }
            members.forEach { m ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(m.name.ifBlank { m.phone.ifBlank { m.email } },
                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = T.text)
                        Text(
                            when (m.role) {
                                "owner" -> "صاحب دکان"
                                "manager" -> "مدیر"
                                else -> "شاگرد"
                            },
                            fontSize = 11.5.sp, color = T.muted,
                        )
                    }
                    if (m.role != "owner" && app.session.can("members.manage")) {
                        TButton(
                            "حذف", kind = BtnKind.Ghost, small = true, enabled = !busy,
                            onClick = {
                                submit {
                                    val api = ApiClient.api(app.session) ?: error("آدرس سرور تنظیم نشده است")
                                    api.removeMember(m.id)
                                    reload()
                                    "${m.name} از دکان حذف شد."
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
