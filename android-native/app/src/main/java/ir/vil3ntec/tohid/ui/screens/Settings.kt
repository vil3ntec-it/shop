package ir.vil3ntec.tohid.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import ir.vil3ntec.tohid.ui.theme.Shape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import ir.vil3ntec.tohid.BuildConfig
import ir.vil3ntec.tohid.data.AccountKeys
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.BackupClock
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.formatDate
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.plain
import ir.vil3ntec.tohid.sync.License
import ir.vil3ntec.tohid.core.config.ApiConfig
import ir.vil3ntec.tohid.core.config.AppConfig
import ir.vil3ntec.tohid.core.model.DeviceDto
import ir.vil3ntec.tohid.core.net.userText
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.sync.SavedLogins
import ir.vil3ntec.tohid.sync.SyncStore
import ir.vil3ntec.tohid.sync.Syncer
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.theme.Shop
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import kotlinx.coroutines.launch

/**
 *  تنظیمات.
 *
 *  سه چیز که هرکدام دلیلِ خودش را دارد:
 *    • **پشتیبان** — تمامِ دفتر در یک فایل، با همان قالبی که نسخهٔ وب
 *      می‌سازد و می‌خواند. گوشی گم می‌شود؛ دفترِ دکان نباید با آن برود.
 *    • **تم** — روشن، تاریک، یا هرچه گوشی گفت.
 *    • **حساب و همگام‌سازی** — وصل شدن به سرورِ خودِ کاربر، بدونِ دامنه و
 *      بدونِ سرویسِ بیرونی.
 */
@Composable
fun SettingsScreen(
  store: ShopStore,
  d: ShopData,
  snackbar: SnackbarHostState,
  theme: ThemeChoice,
  onTheme: (ThemeChoice) -> Unit,
  onUpdates: () -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val prefs = remember { context.getSharedPreferences("tohid", android.content.Context.MODE_PRIVATE) }
  val state = remember { SyncStore(context) }
  val syncer = remember { Syncer(store, state, context) }
  //  صفحه هیچ‌وقت خودش کارگزارِ شبکه نمی‌سازد و توکن را دست نمی‌زند
  val auth = remember(context) { Backend.auth(context) }
  val account = remember(context) { Backend.account(context) }

  var storeName by remember { mutableStateOf(prefs.getString("store_name", "") ?: "") }
  var debtLimit by remember {
    mutableStateOf(ir.vil3ntec.tohid.data.Watchman.debtLimit(context))
  }
  var debtLimitText by remember {
    mutableStateOf(if (debtLimit > 0) debtLimit.toLong().toString() else "")
  }
  //  فقط برای کادرِ ساختِ آزمایشی؛ در نسخهٔ منتشرشده دیده نمی‌شود
  var serverUrl by remember { mutableStateOf(state.serverUrl) }
  //  «می‌شود به سرور زد یا نه» را پیکربندی می‌گوید، نه خالی نبودنِ یک رشته
  val serverReady = ApiConfig.isValid(serverUrl, AppConfig.allowInsecure)
  var identifier by rememberSaveable { mutableStateOf("") }
  var password by rememberSaveable { mutableStateOf("") }
  var busy by remember { mutableStateOf(false) }
  var signedIn by remember { mutableStateOf(state.accessToken != null) }
  val lockStore = remember { ir.vil3ntec.tohid.data.LockStore(context) }
  var lockOn by remember { mutableStateOf(lockStore.enabled) }
  var askPin by remember { mutableStateOf(false) }
  var licenseStatus by remember { mutableStateOf(syncer.status()) }
  var confirmClear by remember { mutableStateOf(false) }
  var confirmRotate by remember { mutableStateOf(false) }
  var apiKey by remember { mutableStateOf(AccountKeys.apiKey(context)) }
  var staffCode by remember { mutableStateOf(AccountKeys.staffCode(context)) }
  var cameraGranted by remember {
    mutableStateOf(
      androidx.core.content.ContextCompat.checkSelfPermission(
        context, android.Manifest.permission.CAMERA
      ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    )
  }
  // فایل خوانده و سنجیده شده، پیش از آنکه دفتر عوض شود
  var pendingRestore by remember { mutableStateOf<ShopData?>(null) }
  var restoreError by remember { mutableStateOf<String?>(null) }
  var canUndo by remember { mutableStateOf(store.hasSafetyCopy()) }

  fun toast(text: String) {
    scope.launch { snackbar.showSnackbar(text) }
  }

  /* --------------------------- پشتیبان --------------------------- */

  val exportFile = rememberLauncherForActivityResult(
    ActivityResultContracts.CreateDocument("application/json")
  ) { uri ->
    if (uri == null) return@rememberLauncherForActivityResult
    scope.launch {
      runCatching {
        val out = context.contentResolver.openOutputStream(uri)
          ?: error("این مسیر برای نوشتن باز نشد")
        out.use { it.write(store.exportBackup(storeName).toByteArray(Charsets.UTF_8)) }
      }.onSuccess {
        BackupClock.mark(context)
        toast("فایل پشتیبان ساخته شد")
      }
        .onFailure { toast("فایل پشتیبان ساخته نشد: ${it.message ?: "دلیل نامعلوم"}") }
    }
  }

  val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
    if (uri == null) return@rememberLauncherForActivityResult
    scope.launch {
      val text = runCatching {
        val input = context.contentResolver.openInputStream(uri)
          ?: error("این فایل باز نشد")
        input.bufferedReader().use { it.readText() }
      }.getOrNull()
      if (text == null) {
        restoreError = "فایل خوانده نشد"
        return@launch
      }
      store.parseBackup(text)
        .onSuccess { pendingRestore = it; restoreError = null }
        .onFailure { restoreError = it.message ?: "فایل پشتیبان معتبر نیست" }
    }
  }

  // اجازهٔ دوربین — همان دکمهٔ «درخواست دسترسی و تست دوربین» نسخهٔ وب
  val askCamera = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission()
  ) { granted ->
    cameraGranted = granted
    toast(if (granted) "دوربین آماده است — بارکدخوان کار می‌کند" else "بدون اجازهٔ دوربین، بارکدخوان باز نمی‌شود")
  }

  LazyColumn(
    Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 32.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      Text(
        "حساب، ظاهر، داده‌ها و اطلاعات برنامه",
        style = MaterialTheme.typography.bodySmall,
        color = Shop.colors.muted,
      )
      Spacer(Modifier.height(4.dp))
    }

    /* ============================== حساب ============================== */
    item {
      SettingsSection(
        icon = Icons.Filled.PersonOutline,
        title = "حساب",
        subtitle = if (signedIn) state.accountName.ifBlank { "وارد شده" } else "وارد نشده‌اید",
        initiallyOpen = !signedIn,
      ) {
        if (signedIn) {
          SettingsRow(
            icon = Icons.Filled.Badge,
            title = "حساب من",
            description = state.accountName.ifBlank { "وارد شده" },
            value = licenseText(licenseStatus),
          )
          licenseStatus.payload?.let { p ->
            if (p.subscriptionEndsAt > 0) {
              SettingsRow(
                icon = Icons.Filled.Event,
                title = "پایان اشتراک",
                value = formatDate(isoOf(p.subscriptionEndsAt)),
                tint = Shop.colors.accent,
              )
            }
          }
          if (state.lastSyncAt > 0) {
            SettingsRow(
              icon = Icons.Filled.Sync,
              title = "آخرین همگام‌سازی",
              value = formatDate(isoOf(state.lastSyncAt)),
              tint = Shop.colors.accent,
            )
          }
          Spacer(Modifier.height(10.dp))
          TohidButton(
            text = "همگام‌سازی حالا",
            onClick = {
              busy = true
              scope.launch {
                runCatching { syncer.run() }
                  .onSuccess {
                    toast("همگام‌سازی شد — ${plain(it.pushed)} فرستاده، ${plain(it.pulled)} گرفته")
                    runCatching { licenseStatus = syncer.refreshLicense(android.os.Build.MODEL ?: "گوشی") }
                  }
                  .onFailure { toast(it.userText("همگام‌سازی ناموفق بود")) }
                busy = false
              }
            },
            busy = busy,
            modifier = Modifier.fillMaxWidth(),
          )
          Spacer(Modifier.height(8.dp))
          TohidDangerButton(
            text = "خروج از حساب",
            onClick = {
              state.signOut()
              signedIn = false
              licenseStatus = License.Status(License.State.NONE)
              toast("از حساب خارج شدید — اطلاعات دکان سر جایش است")
            },
            modifier = Modifier.fillMaxWidth(),
          )
        } else {
          /*
           *  نشانیِ سرور دیگر اینجا نیست.
           *
           *  کادرِ نشانی هم نشانِ سرور را به هر کسی که برنامه را باز
           *  می‌کرد نشان می‌داد، و هم اجازه می‌داد کاربر برنامه را به
           *  سرورِ دیگری وصل کند. حالا نشانی در زمانِ ساخت داخلِ برنامه
           *  می‌نشیند. فقط ساختِ خودی (بی‌نشانی) کادر را می‌بیند.
           */
          Text(
            if (AppConfig.isLocked)
              "به سرورِ توحید وصل می‌شود."
            else "این نسخه به سروری بسته نشده — نشانی را برای آزمایش بزنید.",
            style = MaterialTheme.typography.bodySmall,
            color = Shop.colors.muted,
          )
          Spacer(Modifier.height(10.dp))
          if (!AppConfig.isLocked) {
            TohidTextField(
              value = serverUrl,
              onValueChange = { serverUrl = it; state.serverUrl = it },
              label = "آدرس سرور (فقط ساختِ آزمایشی)",
              placeholder = "https://api.example.com",
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
            )
            Spacer(Modifier.height(10.dp))
          }
          TohidTextField(
            value = identifier,
            onValueChange = { identifier = it },
            label = "ایمیل یا شماره",
          )
          Spacer(Modifier.height(10.dp))
          TohidTextField(
            value = password,
            onValueChange = { password = it },
            label = "رمز عبور",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
          )
          Spacer(Modifier.height(12.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TohidSecondaryButton(
              text = "آزمایش اتصال",
              enabled = !busy && serverReady,
              onClick = {
                busy = true
                scope.launch {
                  auth.health()
                    .onSuccess { toast("سرور جواب داد") }
                    .onFailure { toast(it.userMessage) }
                  busy = false
                }
              },
              modifier = Modifier.weight(1f),
            )
            TohidButton(
              text = "ورود",
              enabled = !busy && serverReady && identifier.isNotBlank() && password.isNotBlank(),
              busy = busy,
              onClick = {
                busy = true
                scope.launch {
                  //  توکن را خودِ مخزن ذخیره می‌کند؛ صفحه فقط نتیجه را
                  //  می‌بیند
                  auth.login(identifier.trim(), password)
                    .onSuccess { session ->
                      state.accountName = session.user.name
                      SavedLogins.remember(context, identifier.trim(), session.user.name)
                      signedIn = true
                      password = ""
                      toast("وارد شدید")
                      runCatching { licenseStatus = syncer.refreshLicense(android.os.Build.MODEL ?: "گوشی") }
                    }
                    .onFailure { toast(it.userMessage) }
                  busy = false
                }
              },
              modifier = Modifier.weight(1f),
            )
          }
        }
      }
    }

    /* ============================= فروشگاه ============================= */
    item {
      SettingsSection(
        icon = Icons.Filled.Storefront,
        title = "فروشگاه",
        subtitle = storeName.ifBlank { "نام دکان تنظیم نشده" },
        tint = Shop.colors.accent,
      ) {
        Text(
          "این نام روی سربرگ فاکتور چاپ می‌شود.",
          style = MaterialTheme.typography.bodySmall,
          color = Shop.colors.muted,
        )
        Spacer(Modifier.height(10.dp))
        TohidTextField(
          value = storeName,
          onValueChange = {
            storeName = it
            prefs.edit().putString("store_name", it.trim()).apply()
          },
          label = "نام فروشگاه",
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )
      }
    }

    /* ============================== هشدارها ============================== */
    item {
      SettingsSection(
        icon = Icons.Filled.NotificationsActive,
        title = "هشدارها",
        subtitle = "قرض از ${money(debtLimit)} افغانی به بالا",
        tint = Shop.colors.warning,
      ) {
        Text(
          "وقتی کالایی تمام شود، قرضِ کسی از این مبلغ بگذرد، یا اشتراک رو " +
            "به پایان باشد، برنامه خبر می‌دهد — حتی وقتی بسته است.",
          style = MaterialTheme.typography.bodySmall,
          color = Shop.colors.muted,
        )
        Spacer(Modifier.height(10.dp))
        TohidTextField(
          value = debtLimitText,
          onValueChange = { entered ->
            //  فقط رقم؛ خالی یعنی «هشدارِ قرض را نمی‌خواهم»
            val digits = entered.filter { it.isDigit() }
            debtLimitText = digits
            val value = digits.toDoubleOrNull() ?: 0.0
            debtLimit = value
            ir.vil3ntec.tohid.data.Watchman.setDebtLimit(context, value)
          },
          label = "هشدار قرض از این مبلغ به بالا (افغانی)",
          keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done,
          ),
        )
        Spacer(Modifier.height(8.dp))
        Text(
          "صفر یعنی هشدارِ قرض خاموش. کالای تمام‌شده و اشتراک سرِ جایشان می‌مانند.",
          style = MaterialTheme.typography.labelSmall,
          color = Shop.colors.muted2,
        )
      }
    }

    /* ============================== ظاهر ============================== */
    item {
      SettingsSection(
        icon = Icons.Filled.Palette,
        title = "ظاهر",
        subtitle = when (theme) {
          ThemeChoice.LIGHT -> "روشن"
          ThemeChoice.DARK -> "تاریک"
          ThemeChoice.SYSTEM -> "مثل گوشی"
        },
        tint = Shop.colors.primary,
      ) {
        SegmentedChoice(
          options = listOf(
            ThemeChoice.LIGHT.name to "روشن",
            ThemeChoice.DARK.name to "تاریک",
            ThemeChoice.SYSTEM.name to "مثل گوشی",
          ),
          selected = theme.name,
          onSelect = { onTheme(ThemeChoice.valueOf(it)) },
        )
        Spacer(Modifier.height(14.dp))
        Text(
          "اگر گوشی «کاهش حرکت» یا حالت ذخیرهٔ باتری روشن باشد، انیمیشن‌ها خاموش می‌شوند. با این کلید همیشه روشن می‌مانند.",
          style = MaterialTheme.typography.labelSmall,
          color = Shop.colors.muted2,
        )
        Spacer(Modifier.height(8.dp))
        SegmentedChoice(
          options = listOf("full" to "انیمیشن روشن", "auto" to "مثل تنظیم گوشی"),
          selected = Motion.choice,
          onSelect = { Motion.set(context, it) },
        )
      }
    }

    /* ============================== دوربین ============================== */
    item {
      SettingsSection(
        icon = Icons.Filled.PhotoCamera,
        title = "دوربین بارکدخوان",
        subtitle = if (cameraGranted) "اجازه داده شده" else "هنوز اجازه داده نشده",
        tint = if (cameraGranted) Shop.colors.success else Shop.colors.warning,
      ) {
        Text(
          "برنامه برای اسکن بارکد محصولات به دوربین نیاز دارد.",
          style = MaterialTheme.typography.bodySmall,
          color = Shop.colors.muted,
        )
        Spacer(Modifier.height(12.dp))
        TohidSecondaryButton(
          text = if (cameraGranted) "آزمایش دوربین" else "درخواست دسترسی دوربین",
          onClick = {
            if (cameraGranted) toast("دوربین آماده است — بارکدخوان کار می‌کند")
            else askCamera.launch(android.Manifest.permission.CAMERA)
          },
          modifier = Modifier.fillMaxWidth(),
        )
      }
    }

    /* ========================= قفل برنامه ========================= */
    item {
      SettingsSection(
        icon = Icons.Filled.Lock,
        title = "قفل برنامه",
        subtitle = if (lockOn) "روشن — با رمز باز می‌شود" else "خاموش",
        tint = if (lockOn) Shop.colors.success else Shop.colors.muted,
      ) {
        Text(
          "تا امروز هر کسی گوشی را برمی‌داشت، حساب‌های دکان جلویش باز بود: طلب مشتری‌ها، سود، قیمت خرید. با رمز، تا زده نشود هیچ‌چیز دیده نمی‌شود.",
          style = MaterialTheme.typography.bodySmall,
          color = Shop.colors.muted,
        )
        Spacer(Modifier.height(12.dp))
        if (lockOn) {
          /*
           *  «عوض کردن رمز» تا دیروز نبود: یا رمز داشتی یا برش می‌داشتی.
           *  حالا لازم است — رمزِ تازه شش‌رقمی است و کسی که از نسخهٔ قبل
           *  رمزِ چهاررقمی دارد باید راهی برای بالا بردنش داشته باشد.
           */
          if (lockStore.isLegacyLength) {
            Text(
              "رمز شما چهار رقمی است. رمزهای تازه شش رقمی‌اند — اگر خواستید عوضش کنید.",
              style = MaterialTheme.typography.labelSmall,
              color = Shop.colors.warning,
            )
            Spacer(Modifier.height(10.dp))
          }
          TohidButton(
            text = "عوض کردن رمز",
            onClick = { askPin = true },
            modifier = Modifier.fillMaxWidth(),
          )
          Spacer(Modifier.height(8.dp))
          TohidSecondaryButton(
            text = "برداشتن رمز",
            onClick = { lockStore.set(""); lockOn = false; toast("قفل برداشته شد") },
            modifier = Modifier.fillMaxWidth(),
          )
        } else {
          TohidButton(
            text = "گذاشتن رمز",
            onClick = { askPin = true },
            modifier = Modifier.fillMaxWidth(),
          )
        }
      }
    }

    /* ========================= رمز حساب ========================= */
    if (signedIn) {
      item {
        SettingsSection(
          icon = Icons.Filled.Password,
          title = "تغییر رمز حساب",
          subtitle = "رمزِ ورود به سرور",
          tint = Shop.colors.primary,
        ) {
          PasswordChange(state = state, snackbarToast = ::toast)
        }
      }

      /* ======================== دستگاه‌ها ======================== */
      item {
        SettingsSection(
          icon = Icons.Filled.Devices,
          title = "دستگاه‌های وارد شده",
          subtitle = "گوشی گم شد؟ نشستش را ببندید",
          tint = Shop.colors.warning,
        ) {
          DeviceList(state = state, snackbarToast = ::toast)
        }
      }
    }

    /* ============================ کلیدها ============================ */
    item {
      SettingsSection(
        icon = Icons.Filled.Key,
        title = "کلیدهای حساب",
        subtitle = "کلید حساب و کد شاگرد",
        tint = Shop.colors.accent,
      ) {
        KeyLine("کلید حساب شما", apiKey) {
          copyToClipboard(context, "کلید حساب", apiKey)
          toast("کلید حساب کپی شد")
        }
        Text(
          "هنگام خرید اشتراک، همین را برای فروشنده بفرستید تا اشتراک روی حساب خودتان فعال شود.",
          style = MaterialTheme.typography.labelSmall,
          color = Shop.colors.muted2,
        )
        Spacer(Modifier.height(16.dp))
        KeyLine("کد شاگرد", staffCode) {
          copyToClipboard(context, "کد شاگرد", staffCode)
          toast("کد شاگرد کپی شد")
        }
        Text(
          "این کد را به شاگردهایتان بدهید تا در صفحهٔ ورود بزنند و روی همین دکان بیایند.",
          style = MaterialTheme.typography.labelSmall,
          color = Shop.colors.muted2,
        )
        Spacer(Modifier.height(12.dp))
        TohidSecondaryButton(text = "ساخت کد تازه", onClick = { confirmRotate = true })
      }
    }

    /* ============================== داده‌ها ============================== */
    item {
      SettingsSection(
        icon = Icons.Filled.Storage,
        title = "داده‌ها",
        subtitle = BackupClock.text(context),
        tint = if (BackupClock.isStale(context)) Shop.colors.warning else Shop.colors.success,
      ) {
        Text(
          "${plain(d.products.size)} کالا — ${plain(d.sales.size)} فاکتور — ${plain(d.debtors.size)} قرض‌دار",
          style = MaterialTheme.typography.labelSmall,
          color = Shop.colors.muted2,
        )
        Spacer(Modifier.height(12.dp))
        SettingsRow(
          icon = Icons.Filled.CloudUpload,
          title = "گرفتن پشتیبان",
          description = "یک فایل از تمام اطلاعات دکان",
          tint = Shop.colors.success,
          onClick = { exportFile.launch("tohid-shop-backup-${todayIso()}.json") },
        )
        SettingsRow(
          icon = Icons.Filled.CloudDownload,
          title = "بازیابی از فایل",
          description = "جایگزینی اطلاعات فعلی با یک پشتیبان",
          tint = Shop.colors.primary,
          onClick = { pickFile.launch("application/json") },
        )
        if (canUndo) {
          SettingsRow(
            icon = Icons.Filled.Undo,
            title = "برگرداندن به پیش از بازیابی",
            description = "اگر فایل اشتباهی بازیابی شد",
            tint = Shop.colors.warning,
            onClick = {
              scope.launch {
                store.undoRestore()
                  .onSuccess { canUndo = false; toast("به وضعیت پیش از بازیابی برگشت") }
                  .onFailure { toast("برگرداندن ممکن نشد") }
              }
            },
          )
        }
        restoreError?.let {
          Spacer(Modifier.height(8.dp))
          Text(it, style = MaterialTheme.typography.labelMedium, color = Shop.colors.danger)
        }
        Spacer(Modifier.height(12.dp))
        TohidDangerButton(
          text = "پاک‌سازی کامل اطلاعات",
          onClick = { confirmClear = true },
          modifier = Modifier.fillMaxWidth(),
        )
      }
    }

    /* ============================ درباره ============================ */
    item {
      SettingsSection(
        icon = Icons.Filled.Info,
        title = "درباره برنامه",
        subtitle = "نسخهٔ ${BuildConfig.VERSION_NAME}",
        tint = Shop.colors.muted,
      ) {
        Text(
          "توحید | مدیریت فروشگاه — برنامهٔ اندروید.\nتمام اطلاعات روی همین گوشی ذخیره می‌شود؛ هر چند وقت یک‌بار پشتیبان بگیرید.",
          style = MaterialTheme.typography.bodySmall,
          color = Shop.colors.muted,
        )
        Spacer(Modifier.height(12.dp))
        SettingsRow(
          icon = Icons.Filled.SystemUpdate,
          title = "دریافت آخرین نسخه",
          description = "نسخهٔ نصب‌شده: ${BuildConfig.VERSION_NAME}",
          tint = Shop.colors.primary,
          onClick = onUpdates,
        )
      }
    }
  }


  /* ---------------------------- پنجره‌ها ---------------------------- */

  pendingRestore?.let { incoming ->
    AlertDialog(
      onDismissRequest = { pendingRestore = null },
      containerColor = Shop.colors.surface,
      title = { Text("بازیابی اطلاعات؟", color = Shop.colors.text) },
      text = {
        Column {
          Text(
            "با بازیابی این فایل، تمام اطلاعات فعلی این دستگاه جایگزین می‌شود. یک نسخه از وضعیت فعلی نگه داشته می‌شود تا اگر اشتباه شد، برگردانده شود.",
            style = MaterialTheme.typography.bodySmall,
            color = Shop.colors.muted,
          )
          Spacer(Modifier.height(12.dp))
          RestoreLine("محصولات", d.products.size, incoming.products.size)
          RestoreLine("فاکتورها", d.sales.size, incoming.sales.size)
          RestoreLine("قرض‌داران", d.debtors.size, incoming.debtors.size)
          RestoreLine("مصارف", d.expenses.size, incoming.expenses.size)
          RestoreLine("ورودهای انبار", d.warehouseEntries.size, incoming.warehouseEntries.size)
        }
      },
      confirmButton = {
        TextButton(onClick = {
          val next = incoming
          pendingRestore = null
          scope.launch {
            store.keepSafetyCopy()
            store.save(next)
            canUndo = true
            toast("اطلاعات با موفقیت بازیابی شد")
          }
        }) { Text("بازیابی", color = Shop.colors.danger) }
      },
      dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("انصراف") } },
    )
  }

  if (confirmRotate) {
    AlertDialog(
      onDismissRequest = { confirmRotate = false },
      containerColor = Shop.colors.surface,
      title = { Text("کد شاگرد تازه ساخته شود؟", color = Shop.colors.text) },
      text = {
        Text(
          "کد فعلی از کار می‌افتد و هر شاگردی که با آن وارد شده، دفعهٔ بعد باید کد تازه را بزند.",
          style = MaterialTheme.typography.bodySmall,
          color = Shop.colors.muted,
        )
      },
      confirmButton = {
        TextButton(onClick = {
          confirmRotate = false
          staffCode = AccountKeys.rotateStaffCode(context)
          toast("کد شاگرد تازه ساخته شد")
        }) { Text("بساز") }
      },
      dismissButton = { TextButton(onClick = { confirmRotate = false }) { Text("انصراف") } },
    )
  }

  if (confirmClear) {
    AlertDialog(
      onDismissRequest = { confirmClear = false },
      containerColor = Shop.colors.surface,
      title = { Text("پاک‌سازی کامل اطلاعات؟", color = Shop.colors.text) },
      text = {
        Text(
          "همه چیز روی این دستگاه پاک می‌شود و برنمی‌گردد. اگر پشتیبان نگرفته‌اید، اول پشتیبان بگیرید.",
          style = MaterialTheme.typography.bodySmall,
          color = Shop.colors.muted,
        )
      },
      confirmButton = {
        TextButton(onClick = {
          confirmClear = false
          scope.launch {
            store.save(ShopData())
            toast("تمام اطلاعات پاک شد")
          }
        }) { Text("پاک کن", color = Shop.colors.danger) }
      },
      dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("انصراف") } },
    )
  }

  if (askPin) {
    PinDialog(
      onDismiss = { askPin = false },
      onSet = { pin ->
        val had = lockStore.enabled
        lockStore.set(pin)
        lockOn = true
        askPin = false
        toast(if (had) "رمز عوض شد" else "قفل برنامه روشن شد")
      },
    )
  }
}

/**
 *  یک کلید با دکمهٔ کپی — همان کادرِ `.settings-key` نسخهٔ وب.
 *
 *  خودِ کلید چپ‌به‌راست نوشته می‌شود: در صفحهٔ راست‌به‌راست، دسته‌های
 *  `TSH-…` وارونه دیده می‌شوند و کاربر اشتباه می‌خواندشان.
 */
@Composable
private fun KeyLine(label: String, value: String, onCopy: () -> Unit) {
  Text(label, style = MaterialTheme.typography.labelMedium, color = Shop.colors.muted)
  Spacer(Modifier.height(6.dp))
  Row(
    Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(10.dp))
      .background(Shop.colors.bg)
      .border(1.dp, Shop.colors.border, RoundedCornerShape(10.dp))
      .padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
      Text(
        value,
        style = MaterialTheme.typography.labelMedium,
        color = Shop.colors.text,
        textAlign = TextAlign.Start,
        modifier = Modifier.weight(1f),
      )
    }
    TextButton(onClick = onCopy) { Text("کپی") }
  }
  Spacer(Modifier.height(6.dp))
}

private fun copyToClipboard(context: android.content.Context, label: String, value: String) {
  val manager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
    as? android.content.ClipboardManager ?: return
  manager.setPrimaryClip(android.content.ClipData.newPlainText(label, value))
}

/**
 *  یک ردیفِ خلاصهٔ بازیابی: چند تا الان هست، چند تا بعدش می‌شود.
 *
 *  فقط تعدادِ فایل را نشان دادن کافی نیست؛ کاربر باید ببیند چه چیزی را
 *  دارد از دست می‌دهد — «۱۲۰ محصول ← ۳ محصول» حرفِ خودش را می‌زند.
 */
@Composable
private fun RestoreLine(label: String, now: Int, next: Int) {
  Row(
    Modifier.fillMaxWidth().padding(vertical = 3.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(label, style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
    Text(
      "${plain(now)} ← ${plain(next)}",
      style = MaterialTheme.typography.bodyMedium,
      color = if (next < now) Shop.colors.warning else Shop.colors.text,
    )
  }
}

private fun licenseText(status: License.Status): String = when (status.state) {
  License.State.ACTIVE -> status.payload?.planTitle ?: "فعال"
  License.State.GRACE -> "تمام شده — در مهلت"
  License.State.EXPIRED -> "تمام شده"
  License.State.PENDING -> "هنوز شروع نشده"
  License.State.INVALID -> "مجوز معتبر نیست"
  License.State.NONE -> "بدون اشتراک"
}

private fun isoOf(millis: Long): String {
  val c = java.util.Calendar.getInstance().apply { timeInMillis = millis }
  val y = c.get(java.util.Calendar.YEAR)
  val m = (c.get(java.util.Calendar.MONTH) + 1).toString().padStart(2, '0')
  val d = c.get(java.util.Calendar.DAY_OF_MONTH).toString().padStart(2, '0')
  return "$y-$m-$d"
}

/* ====================== اجزای تازهٔ صفحهٔ تنظیمات ====================== */

/**
 *  گذاشتنِ رمزِ برنامه.
 *
 *  دو بار پرسیده می‌شود، چون رمزی که اشتباه تایپ شده باشد یعنی قفل شدنِ
 *  خودِ صاحبِ دکان بیرونِ دفترش.
 */
@Composable
private fun PinDialog(onDismiss: () -> Unit, onSet: (String) -> Unit) {
  var first by remember { mutableStateOf("") }
  var again by remember { mutableStateOf("") }
  val colors = Shop.colors
  val ready = first.length == MAX_PIN && first == again

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = colors.surface,
    title = { Text("رمز برنامه", color = colors.text) },
    text = {
      Column {
        Text(
          "یک رمزِ شش رقمی. هر بار که برنامه باز شود همین پرسیده می‌شود.",
          style = MaterialTheme.typography.bodySmall,
          color = colors.muted,
        )
        Spacer(Modifier.height(14.dp))
        NumberField(first, { if (it.length <= MAX_PIN) first = it }, "رمز")
        Spacer(Modifier.height(10.dp))
        NumberField(again, { if (it.length <= MAX_PIN) again = it }, "دوباره")
        if (again.isNotBlank() && first != again) {
          Spacer(Modifier.height(6.dp))
          Text("دو رمز یکی نیستند", style = MaterialTheme.typography.labelSmall, color = colors.danger)
        }
      }
    },
    confirmButton = {
      TextButton(enabled = ready, onClick = { onSet(first) }) {
        Text("بگذار", color = if (ready) colors.primary else colors.muted2)
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف", color = colors.muted) } },
  )
}

/** تغییرِ رمزِ حساب — همان راهی که سرور از اول داشت و برنامه صدایش نمی‌زد */
@Composable
private fun PasswordChange(state: SyncStore, snackbarToast: (String) -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val auth = remember(context) { Backend.auth(context) }
  var current by remember { mutableStateOf("") }
  var fresh by remember { mutableStateOf("") }
  var busy by remember { mutableStateOf(false) }

  Column {
    Text(
      "رمزِ تازه دستِ‌کم هشت نویسه باشد.",
      style = MaterialTheme.typography.bodySmall,
      color = Shop.colors.muted,
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
      value = current,
      onValueChange = { current = it },
      label = { Text("رمز فعلی") },
      singleLine = true,
      visualTransformation = PasswordVisualTransformation(),
      modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(10.dp))
    OutlinedTextField(
      value = fresh,
      onValueChange = { fresh = it },
      label = { Text("رمز تازه") },
      singleLine = true,
      visualTransformation = PasswordVisualTransformation(),
      modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))
    TohidButton(
      text = "تغییر رمز",
      busy = busy,
      enabled = fresh.length >= 8,
      onClick = {
        busy = true
        scope.launch {
          auth.changePassword(current, fresh)
            .onSuccess { current = ""; fresh = ""; snackbarToast("رمز عوض شد") }
            .onFailure { snackbarToast(it.userMessage) }
          busy = false
        }
      },
      modifier = Modifier.fillMaxWidth(),
    )
  }
}

/**
 *  دستگاه‌هایی که با این حساب وارد شده‌اند.
 *
 *  گوشیِ گم‌شده تا امروز راهی برای بسته شدن نداشت: نشستش باز می‌ماند و
 *  هر کسی که آن را برمی‌داشت، دفترِ دکان را همگام می‌گرفت.
 */
@Composable
private fun DeviceList(state: SyncStore, snackbarToast: (String) -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val account = remember(context) { Backend.account(context) }
  var devices by remember { mutableStateOf<List<DeviceDto>>(emptyList()) }
  var loaded by remember { mutableStateOf(false) }

  suspend fun load() {
    account.devices().onSuccess { devices = it }
    loaded = true
  }

  LaunchedEffect(Unit) { load() }

  Column {
    if (!loaded) {
      Text("در حال خواندن…", style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
    } else if (devices.isEmpty()) {
      Text(
        "دستگاهی ثبت نشده است.",
        style = MaterialTheme.typography.bodySmall,
        color = Shop.colors.muted,
      )
    } else {
      devices.forEach { device ->
        //  «همین گوشی» از روی شناسهٔ دستگاه شناخته می‌شود، نه از روی نام
        val isThis = device.deviceUid.isNotBlank() && device.deviceUid == state.deviceUid
        Row(
          Modifier.fillMaxWidth().padding(vertical = 7.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(Modifier.weight(1f)) {
            Text(device.label, style = MaterialTheme.typography.bodyMedium, color = Shop.colors.text)
            if (isThis) {
              Text(
                "همین گوشی",
                style = MaterialTheme.typography.labelSmall,
                color = Shop.colors.success,
              )
            }
          }
          // نشستِ همین گوشی را نمی‌بندیم؛ کاربر خودش را بیرون می‌کرد
          if (!isThis) {
            Text(
              "ببند",
              style = MaterialTheme.typography.labelMedium,
              color = Shop.colors.danger,
              modifier = Modifier
                .clip(Shape.chip)
                .clickable {
                  scope.launch {
                    account.revokeDevice(device.id)
                      .onSuccess { snackbarToast("نشست بسته شد"); load() }
                      .onFailure { snackbarToast(it.userMessage) }
                  }
                }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            )
          }
        }
      }
    }
  }
}
