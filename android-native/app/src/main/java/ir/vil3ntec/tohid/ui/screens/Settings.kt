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
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.History
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.AutoBackup
import ir.vil3ntec.tohid.data.BackupBundle
import ir.vil3ntec.tohid.data.BackupClock
import ir.vil3ntec.tohid.data.LedgerArchive
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.formatDate
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.plain
import ir.vil3ntec.tohid.sync.License
import ir.vil3ntec.tohid.core.config.AppConfig
import ir.vil3ntec.tohid.core.model.DeviceDto
import ir.vil3ntec.tohid.core.net.userText
import ir.vil3ntec.tohid.data.repo.Backend
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
  /*
   *  نشانیِ سرور هیچ‌جای برنامه دیده و زده نمی‌شود.
   *
   *  ── چه چیزی برداشته شد ───────────────────────────────────────────
   *  یک کادرِ «آدرس سرور» اینجا بود و یکی هم در صفحهٔ ورود. هر کسی که
   *  برنامه را باز می‌کرد نشانیِ سرور را می‌دید، و هر کسی می‌توانست
   *  برنامه را به سرورِ دیگری وصل کند — یعنی دفترِ دکان را به جایی
   *  بفرستد که صاحبش نمی‌داند کجاست.
   *  ──────────────────────────────────────────────────────────────────
   *
   *  نشانی فقط در زمانِ **ساخت** داخلِ برنامه می‌نشیند
   *  (`tohid.apiBase` در `gradle.properties`) و از آن به بعد قفل است.
   */
  val serverReady = AppConfig.isConfigured(context)
  var busy by remember { mutableStateOf(false) }
  var signedIn by remember { mutableStateOf(state.accessToken != null) }
  val lockStore = remember { ir.vil3ntec.tohid.data.LockStore(context) }
  var lockOn by remember { mutableStateOf(lockStore.enabled) }
  var askPin by remember { mutableStateOf(false) }
  var confirmClear by remember { mutableStateOf(false) }
  var confirmRotate by remember { mutableStateOf(false) }
  /*
   *  کدِ شاگرد از **سرور** می‌آید.
   *
   *  ── چه چیزی اینجا درست شد ────────────────────────────────────────
   *  تا دیروز برنامه خودش یک کدِ تصادفی می‌ساخت و همین‌جا نشانش
   *  می‌داد. سرور آن کد را نساخته بود و نمی‌شناختش؛ پس شاگردی که
   *  همان را می‌زد، جواب می‌گرفت «این کد معتبر نیست». کدِ واقعی همان
   *  کدِ ثابتِ دکان روی سرور است — همان که صفحهٔ «کارمندان دکان» هم
   *  نشان می‌دهد.
   *  ──────────────────────────────────────────────────────────────────
   *
   *  «کلید حساب» هم از اینجا برداشته شد: آن هم روی خودِ گوشی ساخته
   *  می‌شد و هیچ‌جا فرستاده نمی‌شد، پس فرستادنش برای فروشنده هیچ کاری
   *  نمی‌کرد. اشتراک از پنلِ مدیریت روی خودِ دکان فعال می‌شود.
   */
  val shops = remember(context) { Backend.shop(context) }
  var staffCode by remember { mutableStateOf("") }
  var staffCodeBusy by remember { mutableStateOf(false) }
  var cameraGranted by remember {
    mutableStateOf(
      androidx.core.content.ContextCompat.checkSelfPermission(
        context, android.Manifest.permission.CAMERA
      ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    )
  }
  // فایل خوانده و سنجیده شده، پیش از آنکه دفتر عوض شود
  var pendingRestore by remember { mutableStateOf<ShopData?>(null) }
  //  چند عکس داخلِ فایلِ پشتیبان بود — پیش از تأیید نشان داده می‌شود
  var restorePhotos by remember { mutableStateOf(0) }
  var confirmCompact by remember { mutableStateOf(false) }
  //  فهرستِ نسخه‌های شبانه و شمارشِ ردیف‌های قابلِ بایگانی — با هر تغییرِ
  //  دفتر از نو خوانده می‌شوند، نه در هر بار کشیده شدنِ صفحه
  val autoBackups = remember(d) { AutoBackup.list(context) }
  val archivePlan = remember(d) { LedgerArchive.plan(d) }
  var restoreError by remember { mutableStateOf<String?>(null) }
  var canUndo by remember { mutableStateOf(store.hasSafetyCopy()) }

  /*
   *  یک بار خوانده می‌شود، بی‌صدا.
   *
   *  نبودنِ اینترنت یا نبودنِ حساب خطا نیست: کادر خالی می‌ماند و
   *  همان‌جا نوشته می‌شود چرا.
   */
  LaunchedEffect(signedIn) {
    if (!signedIn || !Backend.isReady(context)) return@LaunchedEffect
    staffCode = shops.standingCode()
  }

  fun toast(text: String) {
    scope.launch { snackbar.showSnackbar(text) }
  }

  /* --------------------------- پشتیبان --------------------------- */

  /*
   *  پشتیبانِ کامل — دفتر **به‌علاوهٔ عکس‌ها**، در یک فایلِ ZIP.
   *
   *  تا دیروز فقط دفتر نوشته می‌شد و عکسِ کالاها جا می‌ماند. گوشی که عوض
   *  می‌شد، کاربر پشتیبانش را برمی‌گرداند و همهٔ عکس‌ها رفته بودند — در
   *  حالی که برنامه هنوز فکر می‌کرد عکس دارند. شرحش سرِ `BackupBundle`.
   */
  val exportFile = rememberLauncherForActivityResult(
    ActivityResultContracts.CreateDocument("application/zip")
  ) { uri ->
    if (uri == null) return@rememberLauncherForActivityResult
    scope.launch {
      runCatching {
        val ledger = store.exportBackup(storeName)
        val out = context.contentResolver.openOutputStream(uri)
          ?: error("این مسیر برای نوشتن باز نشد")
        out.use { BackupBundle.write(context, it, ledger).getOrThrow() }
      }.onSuccess { photos ->
        BackupClock.mark(context)
        toast(
          if (photos > 0) "پشتیبان ساخته شد — با ${plain(photos)} عکس"
          else "پشتیبان ساخته شد"
        )
      }
        .onFailure { toast("فایل پشتیبان ساخته نشد: ${it.message ?: "دلیل نامعلوم"}") }
    }
  }

  /** پشتیبانِ فقط‌دفتر — همان فایلی که نسخهٔ وب می‌خواند */
  val exportJson = rememberLauncherForActivityResult(
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
        toast("فایل دفتر ساخته شد — بدون عکس‌ها")
      }
        .onFailure { toast("فایل ساخته نشد: ${it.message ?: "دلیل نامعلوم"}") }
    }
  }

  //  بازیابی هر دو شکل را می‌شناسد: ZIPِ کامل و JSONِ ساده
  val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
    if (uri == null) return@rememberLauncherForActivityResult
    scope.launch {
      val opened = withContext(Dispatchers.IO) {
        runCatching {
          val input = context.contentResolver.openInputStream(uri)
            ?: error("این فایل باز نشد")
          input.use { BackupBundle.read(context, it) { text -> store.parseBackup(text) }.getOrThrow() }
        }
      }
      opened
        .onSuccess {
          pendingRestore = it.data
          restorePhotos = it.photos
          restoreError = null
        }
        .onFailure {
          BackupBundle.dropStaging(context)
          restoreError = it.message ?: "فایل پشتیبان معتبر نیست"
        }
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
        "هشدارها، ظاهر، داده‌ها و پشتیبان — حساب و نام دکان در «حساب من» است",
        style = MaterialTheme.typography.bodySmall,
        color = Shop.colors.muted,
      )
      Spacer(Modifier.height(4.dp))
    }

    /*
     *  حساب و نامِ فروشگاه اینجا نیستند — عمداً.
     *
     *  ── چه چیزی را می‌بندد ─────────────────────────────────────────
     *  گزارشِ صاحب مخزن: «کارتِ حساب الان آن بالا یکی است، این معنی
     *  ندارد؛ نام فروشگاه هم همین‌طور — این‌ها باید توی حساب من باشند
     *  نه این‌جا». درست بود: نامِ حساب، اشتراک، همگام‌سازی و خروج، هر
     *  چهار تا هم اینجا بودند و هم در «حساب من». یک چیز در دو جا یعنی
     *  کاربر باید حدس بزند کدام‌یک اصل است.
     *
     *  حالا هر چه به حساب بند است — خودِ حساب، اشتراک، همگام‌سازی،
     *  خروج و نامِ دکان — یک‌جا در «حساب من» نشسته، و تنظیمات فقط
     *  تنظیماتِ خودِ برنامه را دارد.
     */

    /* ============================== هشدارها ============================== */
    item {
      SettingsSection(
        icon = Icons.Filled.NotificationsActive,
        title = "هشدارها",
        subtitle = "قرض از ${money(debtLimit)} ؋ به بالا",
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
        title = "کد شاگرد",
        subtitle = "کدی که کارمندتان با آن روی همین دکان می‌آید",
        tint = Shop.colors.accent,
      ) {
        if (staffCode.isNotBlank()) {
          KeyLine("کد شاگرد", staffCode) {
            copyToClipboard(context, "کد شاگرد", staffCode)
            toast("کد شاگرد کپی شد")
          }
          Text(
            "این کد را به شاگردتان بدهید تا در صفحهٔ ورود بزند و روی همین دکان بیاید — " +
              "ایمیل و شماره لازم نیست. با کد، حسابِ خودش ساخته می‌شود و هر کارش در " +
              "«سابقه عملیات» به نامِ خودش می‌نشیند. تنظیمات و عددهای سود برای او بسته است.",
            style = MaterialTheme.typography.labelSmall,
            color = Shop.colors.muted2,
          )
          Spacer(Modifier.height(12.dp))
          TohidSecondaryButton(
            text = if (staffCodeBusy) "در حال ساخت…" else "ساخت کد تازه",
            onClick = { if (!staffCodeBusy) confirmRotate = true },
          )
        } else {
          Text(
            if (!signedIn)
              "کد شاگرد روی سرور ساخته می‌شود، پس اول وارد حساب شوید و دکانتان را بسازید."
            else
              "کد خوانده نشد — برای گرفتنش به اینترنت نیاز است. " +
                "همین کد در «بیشتر → کارمندان دکان» هم هست.",
            style = MaterialTheme.typography.labelSmall,
            color = Shop.colors.muted2,
          )
        }
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
          title = "گرفتن پشتیبان کامل",
          description = "دفتر و عکس‌های کالاها، در یک فایل",
          tint = Shop.colors.success,
          onClick = { exportFile.launch("tohid-backup-${todayIso()}.zip") },
        )
        SettingsRow(
          icon = Icons.Filled.Description,
          title = "پشتیبان فقط دفتر",
          description = "بدون عکس — همان فایلی که نسخهٔ وب می‌خواند",
          tint = Shop.colors.muted,
          onClick = { exportJson.launch("tohid-shop-backup-${todayIso()}.json") },
        )
        SettingsRow(
          icon = Icons.Filled.CloudDownload,
          title = "بازیابی از فایل",
          description = "جایگزینی اطلاعات فعلی با یک پشتیبان",
          tint = Shop.colors.primary,
          onClick = { pickFile.launch("*/*") },
        )

        /*
         *  نسخه‌های شبانه.
         *
         *  روی خودِ گوشی می‌مانند، پس گوشی که گم شود این‌ها هم می‌روند —
         *  برای آن، همان پشتیبانِ کامل بالا لازم است. این‌ها جلوی
         *  چیزهای دیگری را می‌گیرند: پاک شدنِ اشتباهیِ کالا یا فاکتور،
         *  بازیابیِ فایلِ غلط، خرابیِ دفتر.
         */
        if (autoBackups.isNotEmpty()) {
          Spacer(Modifier.height(12.dp))
          Text(
            "نسخه‌های خودکار شبانه",
            style = MaterialTheme.typography.labelMedium,
            color = Shop.colors.muted,
          )
          autoBackups.forEach { snap ->
            SettingsRow(
              icon = Icons.Filled.History,
              title = ir.vil3ntec.tohid.formatMillis(snap.at),
              description = "${plain((snap.bytes / 1024).toInt())} کیلوبایت — برای بازگرداندن بزنید",
              tint = Shop.colors.muted,
              onClick = {
                scope.launch {
                  val opened = withContext(Dispatchers.IO) {
                    runCatching { store.parseBackup(snap.file.readText()).getOrThrow() }
                  }
                  opened
                    .onSuccess { pendingRestore = it; restorePhotos = 0; restoreError = null }
                    .onFailure { restoreError = "این نسخه خوانده نشد" }
                }
              },
            )
          }
        }

        /*
         *  فشرده کردنِ دفتر.
         *
         *  دستی است و نه خودکار، چون یک‌طرفه است و چون با همگام‌سازی
         *  درگیر می‌شود — شرحش سرِ `LedgerArchive`.
         */
        if (archivePlan.total > 0) {
          SettingsRow(
            icon = Icons.Filled.Compress,
            title = "فشرده کردن دفتر",
            description = "${plain(archivePlan.total)} ردیف سابقهٔ کهنه به بایگانی می‌رود — دفتر سبک‌تر و ثبت فروش تندتر می‌شود",
            tint = Shop.colors.warning,
            onClick = { confirmCompact = true },
          )
        }
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
      onDismissRequest = { BackupBundle.dropStaging(context); pendingRestore = null; restorePhotos = 0 },
      containerColor = Shop.colors.surfaceSolid,
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
          Spacer(Modifier.height(8.dp))
          Text(
            if (restorePhotos > 0) "${plain(restorePhotos)} عکس هم در این فایل هست"
            else "این فایل عکسی ندارد — عکس‌های فعلی دست‌نخورده می‌مانند",
            style = MaterialTheme.typography.labelSmall,
            color = Shop.colors.muted2,
          )
        }
      },
      confirmButton = {
        TextButton(onClick = {
          val next = incoming
          pendingRestore = null
          scope.launch {
            store.keepSafetyCopy()
            //  اول عکس‌ها سرِ جایشان، بعد پرچمِ `photo` با واقعیتِ دیسک
            //  یکی می‌شود — وگرنه کاربر جای خالیِ عکس می‌بیند و فکر
            //  می‌کند برنامه خراب است
            val moved = withContext(Dispatchers.IO) { BackupBundle.commitPhotos(context) }
            val fixed = withContext(Dispatchers.IO) { BackupBundle.reconcilePhotoFlags(context, next) }
            store.save(fixed)
            canUndo = true
            restorePhotos = 0
            toast(
              if (moved > 0) "اطلاعات بازیابی شد — با ${plain(moved)} عکس"
              else "اطلاعات با موفقیت بازیابی شد"
            )
          }
        }) { Text("بازیابی", color = Shop.colors.danger) }
      },
      dismissButton = {
        TextButton(onClick = {
          BackupBundle.dropStaging(context)
          pendingRestore = null
          restorePhotos = 0
        }) { Text("انصراف") }
      },
    )
  }

  if (confirmCompact) {
    AlertDialog(
      onDismissRequest = { confirmCompact = false },
      containerColor = Shop.colors.surfaceSolid,
      title = { Text("دفتر فشرده شود؟", color = Shop.colors.text) },
      text = {
        Text(
          "${plain(archivePlan.total)} ردیف از حرکات انبار، سابقهٔ عملیات و تغییرات قیمت که بیش از یک سال " +
            "از آن‌ها گذشته، به یک فایل بایگانی کنار دفتر منتقل می‌شوند. پاک نمی‌شوند و با پشتیبان کامل " +
            "هم بیرون می‌روند — فقط از دفتری که هر فروش آن را بازنویسی می‌کند بیرون می‌آیند. " +
            "فاکتورها، کالاها، قرض‌داران و موجودی دست‌نخورده می‌مانند.",
          style = MaterialTheme.typography.bodySmall,
          color = Shop.colors.muted,
        )
      },
      confirmButton = {
        TextButton(onClick = {
          confirmCompact = false
          scope.launch {
            val done = withContext(Dispatchers.IO) { LedgerArchive.compact(context, d) }
            done
              .onSuccess {
                store.save(it.data)
                toast("${plain(it.moved.total)} ردیف بایگانی شد")
              }
              .onFailure { toast("فشرده کردن انجام نشد: ${it.message ?: "دلیل نامعلوم"}") }
          }
        }) { Text("فشرده کن") }
      },
      dismissButton = { TextButton(onClick = { confirmCompact = false }) { Text("بازگشت") } },
    )
  }

  if (confirmRotate) {
    AlertDialog(
      onDismissRequest = { confirmRotate = false },
      containerColor = Shop.colors.surfaceSolid,
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
          staffCodeBusy = true
          scope.launch {
            //  کد روی سرور عوض می‌شود، نه روی گوشی: شاگردی که فردا
            //  کدِ قبلی را بزند باید همان‌جا رد شود
            shops.rotateStandingCode()
              .onSuccess {
                staffCode = it.code.ifBlank { shops.standingCode() }
                toast("کد شاگرد تازه ساخته شد")
              }
              .onFailure { toast(it.userMessage) }
            staffCodeBusy = false
          }
        }) { Text("بساز") }
      },
      dismissButton = { TextButton(onClick = { confirmRotate = false }) { Text("انصراف") } },
    )
  }

  if (confirmClear) {
    AlertDialog(
      onDismissRequest = { confirmClear = false },
      containerColor = Shop.colors.surfaceSolid,
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
 *  خودِ کد چپ‌به‌راست نوشته می‌شود: در صفحهٔ راست‌به‌راست، دسته‌های
 *  `SHG-…` وارونه دیده می‌شوند و کاربر اشتباه می‌خواندشان.
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
    containerColor = colors.surfaceSolid,
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
