package ir.vil3ntec.tohid.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.CloudDone
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
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.Restore
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
import ir.vil3ntec.tohid.data.repo.BackupRepository
import ir.vil3ntec.tohid.sync.SyncStore
import ir.vil3ntec.tohid.sync.Syncer
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.theme.Shop
import ir.vil3ntec.tohid.core.i18n.StrSettings
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrShell
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
  /** گوشی: برگشت از ریشهٔ تنظیمات؛ null ⇒ همان صفحه‌ای که تنظیمات را باز کرد (`SettingsNav.origin`) */
  onBack: (() -> Unit)? = null,
  onUpdates: () -> Unit,
) {
  //  پرچمِ نسخهٔ ۳ خاموش (پیش‌فرض) ⇒ همان رابطِ امروز (`ui/legacy`، `UiV3`)
  if (!ir.vil3ntec.tohid.ui.UiV3.on && onBack == null) { SettingsScreenLegacy(store, d, snackbar, theme, onTheme, onUpdates); return }
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
   *  ⚠️ تنها استثنا بخشِ «سرورها» (`ServersSettings`) پشتِ پرچمِ خاموشِ `app.multi_server` است — فقط صاحبِ دکان،
   *  با سنجاقِ هویت و دادهٔ جدا برای هر سرور (`docs/SERVERS-fa.md`).
   */
  val serverReady = AppConfig.isConfigured(context)
  var busy by remember { mutableStateOf(false) }
  var signedIn by remember { mutableStateOf(state.accessToken != null) }
  val lockStore = remember { ir.vil3ntec.tohid.data.LockStore(context) }
  var lockOn by remember { mutableStateOf(SettingsShot.lockOn ?: lockStore.enabled) }
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
  /*
   *  پشتیبان‌های **ابری** — همان‌هایی که روی سرور نشسته‌اند.
   *
   *  ⚠️ با نسخه‌های شبانه یکی نیستند: آن‌ها روی خودِ گوشی‌اند و با گم
   *  شدنِ گوشی می‌روند. این‌ها روی سرورند و از گوشیِ تازه هم دیده
   *  می‌شوند — همان چیزی که تا امروز اصلاً وجود نداشت.
   */
  var cloudBackups by remember { mutableStateOf<BackupRepository.Listing?>(null) }
  var cloudBusy by remember { mutableStateOf(false) }
  var cloudNote by remember { mutableStateOf<String?>(null) }
  var cloudReload by remember { mutableIntStateOf(0) }
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

  /*
   *  فهرستِ ابری — بی‌صدا. نبودنِ حساب یا نت خطا نیست؛ کادر همان
   *  «هنوز چیزی نرفته» را نشان می‌دهد و می‌گوید چرا.
   */
  LaunchedEffect(signedIn, cloudReload) {
    if (!signedIn || !Backend.isReady(context)) { cloudBackups = null; return@LaunchedEffect }
    Backend.backups(context).list()
      .onSuccess { cloudBackups = it }
      .onFailure { cloudBackups = null }
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
      //  زیپِ عکس‌ها سنگین است ⇐ بیرون از رشتهٔ اصلی، وگرنه «برنامه پاسخ نمی‌دهد»
      runCatching {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
          val ledger = store.exportBackup(storeName)
          val out = context.contentResolver.openOutputStream(uri)
            ?: error(StrSettings.pathNotWritable)
          out.use { BackupBundle.write(context, it, ledger).getOrThrow() }
        }
      }.onSuccess { photos ->
        BackupClock.mark(context)
        toast(
          if (photos > 0) StrSettings.backupMadeWithPhotos(plain(photos))
          else StrSettings.backupMade
        )
      }
        .onFailure { toast(StrSettings.backupFailed(it.message ?: Str.unknownReason)) }
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
          ?: error(StrSettings.pathNotWritable)
        out.use { it.write(store.exportBackup(storeName).toByteArray(Charsets.UTF_8)) }
      }.onSuccess {
        BackupClock.mark(context)
        toast(StrSettings.ledgerFileMade)
      }
        .onFailure { toast(StrSettings.fileFailed(it.message ?: Str.unknownReason)) }
    }
  }

  /*
   *  خروجیِ کاملِ داده برای مالک (OPS-08) — شرح سرِ `OwnerExport`.
   *  مشخصات ۲۱.۲: فقط مالک/نقشِ مجاز (شاگرد تنظیمات را اصلاً باز نمی‌کند؛
   *  این‌جا هم دوباره سنجیده می‌شود) و با ثبت در سابقهٔ عملیات. جز همان یک
   *  ردیفِ سابقه چیزی در دفتر نوشته نمی‌شود و مُهرِ پشتیبان (`BackupClock`) نمی‌خورد.
   */
  fun ownerExport(excel: Boolean) = { uri: android.net.Uri? ->
    if (uri != null && ir.vil3ntec.tohid.data.ShopRole.isStaff(context)) toast(StrSettings.exportOwnerOnly)
    else if (uri != null) scope.launch {
      runCatching {
        withContext(Dispatchers.IO) {
          val meta = ir.vil3ntec.tohid.data.OwnerExport.Meta(
            System.currentTimeMillis(), if (ir.vil3ntec.tohid.ui.Platform.DESKTOP) "desktop" else "android",
            BuildConfig.VERSION_NAME, storeName,
          )
          val archive = LedgerArchive.readArchive(context)
          val out = context.contentResolver.openOutputStream(uri) ?: error(StrSettings.pathNotWritable)
          out.use {
            if (excel) ir.vil3ntec.tohid.data.OwnerExport.writeXlsx(store.data.value, meta, archive, it)
            else ir.vil3ntec.tohid.data.OwnerExport.writeJson(store.data.value, meta, archive, it)
          }
        }
      }.onSuccess {
        val now = System.currentTimeMillis()
        val cur = store.data.value
        store.save(
          cur.copy(
            auditLog = cur.auditLog + ir.vil3ntec.tohid.data.AuditEntry(
              id = ir.vil3ntec.tohid.data.auditId(), type = "data_export", date = todayIso(),
              notes = if (excel) "خروجی کامل داده — اکسل" else "خروجی کامل داده — JSON", createdAt = now,
            ),
          ),
        )
        toast(if (excel) StrSettings.excelExportMade else StrSettings.jsonExportMade)
      }
        .onFailure { toast(StrSettings.exportFailed(it.message ?: Str.unknownReason)) }
    }
    Unit
  }
  val exportAllJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json"), ownerExport(false))
  val exportAllXlsx = rememberLauncherForActivityResult(
    ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"), ownerExport(true),
  )

  //  بازیابی هر دو شکل را می‌شناسد: ZIPِ کامل و JSONِ ساده
  val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
    if (uri == null) return@rememberLauncherForActivityResult
    scope.launch {
      val opened = withContext(Dispatchers.IO) {
        runCatching {
          val input = context.contentResolver.openInputStream(uri)
            ?: error(StrSettings.fileNotOpened)
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
          restoreError = it.message ?: StrSettings.invalidBackup
        }
    }
  }

  // اجازهٔ دوربین — همان دکمهٔ «درخواست دسترسی و تست دوربین» نسخهٔ وب
  val askCamera = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission()
  ) { granted ->
    cameraGranted = granted
    toast(if (granted) StrSettings.cameraReady else StrSettings.cameraDenied)
  }

  //  همگام‌سازی (از «حساب من» به این‌جا آمد) — همان موتورِ v1 و همان دکمه
  val v1 by ir.vil3ntec.tohid.sync.v1.SyncV1Engine.of(context).status.collectAsState()
  val lastSyncOk = maxOf(state.lastSyncAt, v1.lastOkAt)
  val syncNow = System.currentTimeMillis()

  /*
   *  بدنهٔ تنظیمات — یک بار نوشته شده و سه جا کشیده می‌شود: فهرستِ گوشی،
   *  صفحهٔ یک بخشِ گوشی، و ستونِ وسطِ کامپیوتر (شرح سرِ `SettingsParts.kt`).
   */
  val body: LazyListScope.() -> Unit = {

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

    /*
     *  گوشی (phone-ui، تصویرِ مرجعِ ۴): بخش‌ها زیرِ شش سرخط در کارت‌های گرد
     *  (`SettingsLayout`). هیچ تنظیمی برداشته یا نام‌عوض نشده و کارِ هیچ‌کدام
     *  عوض نشده — فقط جایشان. کامپیوتر گروه نمی‌بیند (`SettingsGroup`).
     */
    /* ============================ عمومی ============================ */
    item {
      SettingsGroup(SettingsLayout.GENERAL) {
        /* ============================== هشدارها ============================== */
        SettingsSection(
          icon = Icons.Filled.NotificationsActive,
          title = Str.alerts,
          subtitle = StrSettings.debtFrom(money(debtLimit)),
          tint = Shop.colors.warning,
        ) {
          Text(
            StrSettings.alertsNote,
            style = MaterialTheme.typography.bodySmall,
            color = Shop.colors.muted,
          )
          Spacer(Modifier.height(10.dp))
          TohidTextField(
            value = debtLimitText,
            onValueChange = { entered ->
              //  فقط رقم؛ خالی یعنی «هشدارِ قرض را نمی‌خواهم»
              val digits = ir.vil3ntec.tohid.normalizeDigits(entered).filter { it in '0'..'9' }
              debtLimitText = digits
              val value = digits.toDoubleOrNull() ?: 0.0
              debtLimit = value
              ir.vil3ntec.tohid.data.Watchman.setDebtLimit(context, value)
            },
            label = StrSettings.debtAlertLabel,
            keyboardOptions = KeyboardOptions(
              keyboardType = KeyboardType.Number,
              imeAction = ImeAction.Done,
            ),
          )
          Spacer(Modifier.height(8.dp))
          Text(
            StrSettings.zeroOff,
            style = MaterialTheme.typography.labelSmall,
            color = Shop.colors.muted2,
          )
        }

        /* ============================== ظاهر ============================== */
        SettingsSection(
          icon = Icons.Filled.Palette,
          title = Str.appearance,
          subtitle = "روشن، تیره، یا خودکار (مثل گوشی)",
          tint = Shop.colors.primary,
          value = themeLabel(theme),
        ) {
          SegmentedChoice(
            options = listOf(
              ThemeChoice.LIGHT.name to themeLabel(ThemeChoice.LIGHT),
              ThemeChoice.DARK.name to themeLabel(ThemeChoice.DARK),
              ThemeChoice.SYSTEM.name to themeLabel(ThemeChoice.SYSTEM),
            ),
            selected = theme.name,
            onSelect = { onTheme(ThemeChoice.valueOf(it)) },
          )
          Spacer(Modifier.height(14.dp))
          Text(
            StrSettings.reduceMotionNote,
            style = MaterialTheme.typography.labelSmall,
            color = Shop.colors.muted2,
          )
          Spacer(Modifier.height(8.dp))
          SegmentedChoice(
            options = listOf("full" to StrSettings.animationsOn, "auto" to StrSettings.likePhoneSetting),
            selected = Motion.choice,
            onSelect = { Motion.set(context, it) },
          )
        }

        /* ===================== نوارِ پایین (F2) — ویرایشگرش مالِ بخشِ نوار است ===================== */
        SettingsLink(SettingsLayout.NAV_BAR) { ir.vil3ntec.tohid.ui.AppNav.go(SettingsLayout.LINKS.getValue(SettingsLayout.NAV_BAR)) }

        /* ======================== زبانِ ماه‌ها، رقم و نماد (LOC-01، LOC-05، LOC-07) ======================== */
        ir.vil3ntec.tohid.ui.LanguageSection()
        LocaleSection()
      }
    }

    /* ============================ امنیت ============================ */
    item {
      SettingsGroup(SettingsLayout.SECURITY) {
        /* ========================= قفل برنامه ========================= */
        SettingsSection(
          icon = Icons.Filled.Lock,
          title = Str.appLock,
          subtitle = if (lockOn) StrSettings.lockOnPin else StrSettings.off,
          tint = if (lockOn) Shop.colors.success else Shop.colors.muted,
          //  کلیدِ سبزِ ردیف (r5): روشن ⇐ همان پنجرهٔ گذاشتنِ رمز؛ خاموش ⇐ همان «برداشتن رمز»
          toggle = lockOn,
          onToggle = { on ->
            if (on) askPin = true
            else { lockStore.set(""); lockOn = false; toast("قفل برداشته شد") }
          },
        ) {
          Text(
            StrSettings.lockNote,
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
                StrSettings.oldPin4,
                style = MaterialTheme.typography.labelSmall,
                color = Shop.colors.warning,
              )
              Spacer(Modifier.height(10.dp))
            }
            TohidButton(
              text = StrSettings.changePin,
              onClick = { askPin = true },
              modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            TohidSecondaryButton(
              text = StrSettings.removePin,
              onClick = { lockStore.set(""); lockOn = false; toast(StrSettings.lockRemoved) },
              modifier = Modifier.fillMaxWidth(),
            )
            IdleLockChoice(lockStore)   // STF-05
          } else {
            TohidButton(
              text = StrSettings.setPin,
              onClick = { askPin = true },
              modifier = Modifier.fillMaxWidth(),
            )
          }
        }

        /* ===================== امنیت (SEC-01) ===================== */
        SecuritySection(store, ::toast)

        /* ============================ کلیدها ============================ */
        SettingsSection(
          icon = Icons.Filled.Key,
          title = Str.staffCode,
          subtitle = StrSettings.staffCodeSub,
          tint = Shop.colors.accent,
        ) {
          if (staffCode.isNotBlank()) {
            KeyLine(Str.staffCode, staffCode) {
              copyToClipboard(context, Str.staffCode, staffCode)
              toast(StrSettings.staffCodeCopied)
            }
            Text(
              StrSettings.staffCodeNote,
              style = MaterialTheme.typography.labelSmall,
              color = Shop.colors.muted2,
            )
            Spacer(Modifier.height(12.dp))
            TohidSecondaryButton(
              text = if (staffCodeBusy) StrSettings.creating else StrSettings.newCode,
              onClick = { if (!staffCodeBusy) confirmRotate = true },
            )
          } else {
            Text(
              if (!signedIn)
                StrSettings.staffCodeNeedsAccount
              else
                StrSettings.staffCodeOffline,
              style = MaterialTheme.typography.labelSmall,
              color = Shop.colors.muted2,
            )
          }
        }

        /* ======================== دستگاه‌های وارد شده ======================== */
        //  همیشه در فهرست (بندِ C «دستگاه‌های وصل»)؛ فهرستِ واقعی روی سرور است و
        //  بیرون از حساب فقط همین را می‌گوید
        SettingsSection(
          icon = Icons.Filled.Devices,
          title = Str.signedInDevices,
          subtitle = if (signedIn) StrSettings.lostPhone else StrSettings.signedInDevicesHint,
          tint = Shop.colors.warning,
          value = if (signedIn) null else "وارد نشده",
        ) {
          if (signedIn) {
            DeviceList(state = state, snackbarToast = ::toast)
          } else {
            Text(
              "فهرستِ دستگاه‌ها و بستنِ نشستِ هر کدام روی سرور است؛ اول وارد حساب شوید.",
              style = MaterialTheme.typography.labelSmall,
              color = Shop.colors.muted2,
            )
          }
        }

        /* ========================= رمز حساب ========================= */
        if (signedIn) {
          SettingsSection(
            icon = Icons.Filled.Password,
            title = Str.changeAccountPassword,
            subtitle = StrSettings.serverPassword,
            tint = Shop.colors.primary,
          ) {
            PasswordChange(state = state, snackbarToast = ::toast)
          }
        }
      }
    }

    /* ============================ فروش و پرداخت ============================ */
    item {
      SettingsGroup(SettingsLayout.SALES) {
        /* ============================ انقضا ============================ */
        var blockExpired by remember { mutableStateOf(ir.vil3ntec.tohid.data.ShopPolicy.blockExpired(context)) }
        SettingsSection(
          icon = Icons.Filled.Lock,
          title = Str.expiredGoods,
          subtitle = if (blockExpired) StrSettings.notSold else StrSettings.warnOnly,
          tint = if (blockExpired) Shop.colors.danger else Shop.colors.warning,
          value = if (blockExpired) "فروخته نمی‌شود" else "فقط هشدار",
        ) {
          Text(
            StrSettings.expiryNote,
            style = MaterialTheme.typography.labelSmall,
            color = Shop.colors.muted2,
          )
          Spacer(Modifier.height(8.dp))
          SegmentedChoice(
            options = listOf("warn" to StrSettings.warnOnly, "block" to StrSettings.dontSell),
            selected = if (blockExpired) "block" else "warn",
            onSelect = {
              blockExpired = it == "block"
              ir.vil3ntec.tohid.data.ShopPolicy.setBlockExpired(context, blockExpired)
            },
          )
        }

        /* ============== قاعده‌های پیش از پایانِ فاکتور (POS-24، پیش‌فرض خاموش) ============== */
        var rules by remember { mutableStateOf(ir.vil3ntec.tohid.data.CheckoutRulesPref.get(context)) }
        CheckoutRulesSection(rules) { rules = it; ir.vil3ntec.tohid.data.CheckoutRulesPref.set(context, it) }

        /* ============ قیمت و تخفیفِ ردیف (POS-08/10/20/21، پیش‌فرض خاموش) ============ */
        ir.vil3ntec.tohid.ui.components.pos.LinePricingSettingsWired(context)
        /* ======================== برگشت و تعویض (RET، پیش‌فرض خاموش) ======================== */
        ReturnSettingsSection()
        /* =============== جست‌وجوی مقاومِ صندوق (POS-04، فقط کامپیوتر، پیش‌فرض خاموش) =============== */
        if (ir.vil3ntec.tohid.ui.Platform.DESKTOP) ir.vil3ntec.tohid.ui.components.pos.PosSearchSettingsWired(context)
        /* =================== پرداخت و صندوق (PAY، v3 A4) =================== */
        var payV2 by remember { mutableStateOf(ir.vil3ntec.tohid.data.ShopPolicy.payV2(context)) }
        SettingsSection(
          icon = Icons.Filled.PointOfSale,
          title = StrShell.payAndRegister,
          subtitle = if (payV2) StrShell.payMixed else StrShell.payLegacy,
          tint = if (payV2) Shop.colors.primary else Shop.colors.muted,
        ) {
          Text(
            StrShell.payNote,
            style = MaterialTheme.typography.labelSmall,
            color = Shop.colors.muted2,
          )
          Spacer(Modifier.height(8.dp))
          SegmentedChoice(
            options = listOf("legacy" to StrShell.payLegacy, "v2" to StrShell.payMixed),
            selected = if (payV2) "v2" else "legacy",
            onSelect = {
              payV2 = it == "v2"
              ir.vil3ntec.tohid.data.ShopPolicy.setPayV2(context, payV2)
            },
          )
        }

        /* ============== سقفِ اعتبار در صندوق (CRM-03، S07، پیش‌فرض خاموش) ============== */
        var creditGate by remember { mutableStateOf(ir.vil3ntec.tohid.data.ShopPolicy.creditGate(context)) }
        SettingsSection(
          icon = Icons.Filled.Lock,
          title = StrShell.creditGate,
          subtitle = if (creditGate) StrShell.creditGateOn else StrSettings.off,
          tint = if (creditGate) Shop.colors.warning else Shop.colors.muted,
        ) {
          Text(
            StrShell.creditGateNote,
            style = MaterialTheme.typography.labelSmall,
            color = Shop.colors.muted2,
          )
          Spacer(Modifier.height(8.dp))
          SegmentedChoice(
            options = listOf("off" to StrShell.switchOff, "on" to StrShell.switchOn),
            selected = if (creditGate) "on" else "off",
            onSelect = {
              creditGate = it == "on"
              ir.vil3ntec.tohid.data.ShopPolicy.setCreditGate(context, creditGate)
            },
          )
        }

        /* ===================== چاپگر و رسید ⇐ صفحهٔ «دستگاه‌های جانبی» ===================== */
        /* ===================== چند فاکتورِ هم‌زمان (POS-12) ===================== */
        ir.vil3ntec.tohid.ui.components.pos.HeldInvoiceSettings()

        /* ================= گزارش‌های مشتری (CRM-04/06/10/14) — فقط کامپیوتر ================= */
        //  فقط زبانه‌های «گزارش‌ها»ی کامپیوتر را نشان/پنهان می‌کند؛ گوشی هنوز این زبانه‌ها را ندارد،
        //  پس این‌جا دکمهٔ بی‌اثر نمی‌گذاریم. پیش‌فرض خاموش.
        if (ir.vil3ntec.tohid.ui.Platform.DESKTOP) {
          var crmReports by remember { mutableStateOf(ir.vil3ntec.tohid.data.ShopPolicy.crmReports(context)) }
          SettingsSection(
            icon = Icons.Filled.TableChart,
            title = StrShell.crmReports,
            subtitle = if (crmReports) StrShell.crmShownInReports else StrSettings.off,
            tint = if (crmReports) Shop.colors.success else Shop.colors.muted,
          ) {
            Text(
              StrShell.crmNote,
              style = MaterialTheme.typography.labelSmall,
              color = Shop.colors.muted2,
            )
            Spacer(Modifier.height(8.dp))
            SegmentedChoice(
              options = listOf("off" to StrShell.switchOff, "on" to StrShell.switchOn),
              selected = if (crmReports) "on" else "off",
              onSelect = {
                crmReports = it == "on"
                ir.vil3ntec.tohid.data.ShopPolicy.setCrmReports(context, crmReports)
              },
            )
          }
        }

        SettingsLink(SettingsLayout.PRINTER) { ir.vil3ntec.tohid.ui.AppNav.go(SettingsLayout.LINKS.getValue(SettingsLayout.PRINTER)) }
      }
    }

    /* ============================ دستگاه‌ها ============================ */
    item {
      SettingsGroup(SettingsLayout.DEVICES) {
        /* ============================== دوربین ============================== */
        SettingsSection(
          icon = Icons.Filled.PhotoCamera,
          title = StrSettings.cameraScanner,
          subtitle = if (cameraGranted) StrSettings.permitted else StrSettings.notPermitted,
          tint = if (cameraGranted) Shop.colors.success else Shop.colors.warning,
          value = if (cameraGranted) "آماده" else "بی‌اجازه",
        ) {
          Text(
            StrSettings.cameraNeeded,
            style = MaterialTheme.typography.bodySmall,
            color = Shop.colors.muted,
          )
          Spacer(Modifier.height(12.dp))
          TohidSecondaryButton(
            text = if (cameraGranted) StrSettings.testCamera else StrSettings.requestCamera,
            onClick = {
              if (cameraGranted) toast(StrSettings.cameraReady)
              else askCamera.launch(android.Manifest.permission.CAMERA)
            },
            modifier = Modifier.fillMaxWidth(),
          )
        }
      }
    }

    /* ============================ داده‌ها: همگام‌سازی، پشتیبان و بازگردانی ============================ */
    item {
      SettingsGroup(SettingsLayout.DATA) {
        /* ============================ همگام‌سازی ============================ */
        /*
         *  از «حساب من» به این‌جا آمد: کارتِ حسابِ نسخهٔ ۳ فقط r4 است (نام، نقش،
         *  آمار، اشتراک). همان موتورِ v1 و همان دکمه — ⛔ F0-02: دورِ Sync v1،
         *  نه راهِ قدیمِ `Syncer.run` (سنجه: `sync/LegacySyncCallTest.kt`).
         */
        SettingsSection(
          icon = Icons.Filled.Sync,
          title = "همگام‌سازی",
          subtitle = if (!signedIn) "برای همگام‌سازی اول وارد حساب شوید"
          else ir.vil3ntec.tohid.core.sync.SyncBadge.line("همگام‌سازی", v1.queued, lastSyncOk, v1.lastError, syncNow),
          tint = Shop.colors.accent,
          value = if (!signedIn) "وارد نشده" else ir.vil3ntec.tohid.core.sync.SyncBadge.since(lastSyncOk, syncNow),
        ) {
          Text(
            "دفترِ دکان روی سرور پشتیبان می‌گیرد و روی گوشیِ دیگر هم می‌آید. " +
              "آخرین همگام‌سازی: " + (if (lastSyncOk > 0) ir.vil3ntec.tohid.formatMillis(lastSyncOk) else "هنوز انجام نشده"),
            style = MaterialTheme.typography.labelSmall,
            color = Shop.colors.muted2,
          )
          if (v1.lastError.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(v1.lastError, style = MaterialTheme.typography.labelSmall, color = Shop.colors.danger)
          }
          Spacer(Modifier.height(12.dp))
          TohidButton(
            text = "همگام‌سازی حالا",
            enabled = signedIn,
            onClick = {
              runCatching { ir.vil3ntec.tohid.sync.v1.SyncV1Worker.now(context) }
                .onSuccess { toast("همگام‌سازی شروع شد؛ چند ثانیه بعد «آخرین همگام‌سازی» تازه می‌شود") }
                .onFailure { toast(it.userText("همگام‌سازی ناموفق بود")) }
            },
            modifier = Modifier.fillMaxWidth(),
          )
        }

        /* ============================== داده‌ها ============================== */
        SettingsSection(
          icon = Icons.Filled.Storage,
          title = Str.data,
          subtitle = BackupClock.text(context),
          tint = if (BackupClock.isStale(context)) Shop.colors.warning else Shop.colors.success,
        ) {
          Text(
            StrSettings.dataSummary(plain(d.products.size), plain(d.sales.size), plain(d.debtors.size)),
            style = MaterialTheme.typography.labelSmall,
            color = Shop.colors.muted2,
          )
          Spacer(Modifier.height(12.dp))
          SettingsRow(
            icon = Icons.Outlined.CloudUpload,
            title = StrSettings.fullBackup,
            description = StrSettings.fullBackupDesc,
            tint = Shop.colors.success,
            onClick = { exportFile.launch("tohid-backup-${todayIso()}.zip") },
          )
          SettingsRow(
            icon = Icons.Outlined.Description,
            title = StrSettings.ledgerOnly,
            description = StrSettings.ledgerOnlyDesc,
            tint = Shop.colors.muted,
            onClick = { exportJson.launch("tohid-shop-backup-${todayIso()}.json") },
          )
          SettingsRow(
            icon = Icons.Outlined.TableChart,
            title = StrSettings.exportExcel,
            description = StrSettings.exportExcelDesc,
            tint = Shop.colors.success,
            onClick = { exportAllXlsx.launch("tohid-export-${todayIso()}.xlsx") },
          )
          SettingsRow(
            icon = Icons.Outlined.Description,
            title = StrSettings.exportJson,
            description = StrSettings.exportJsonDesc,
            tint = Shop.colors.muted,
            onClick = { exportAllJson.launch("tohid-export-${todayIso()}.json") },
          )
          SettingsRow(
            icon = Icons.Outlined.CloudDownload,
            title = StrSettings.restoreFromFile,
            description = StrSettings.restoreDesc,
            tint = Shop.colors.primary,
            onClick = { pickFile.launch("*/*") },
          )

          /*
           *  نسخه‌های خودکار (نیم‌ساعته و پایانِ روز — OPS-02، `AutoBackup`).
           *
           *  روی خودِ گوشی می‌مانند، پس گوشی که گم شود این‌ها هم می‌روند —
           *  برای آن، همان پشتیبانِ کامل بالا لازم است. این‌ها جلوی
           *  چیزهای دیگری را می‌گیرند: پاک شدنِ اشتباهیِ کالا یا فاکتور،
           *  بازیابیِ فایلِ غلط، خرابیِ دفتر.
           */
          if (autoBackups.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
              StrSettings.autoSnapshots,
              style = MaterialTheme.typography.labelMedium,
              color = Shop.colors.muted,
            )
            autoBackups.forEach { snap ->
              SettingsRow(
                icon = Icons.Outlined.History,
                title = ir.vil3ntec.tohid.formatMillis(snap.at),
                description = StrSettings.snapshotDesc(plain((snap.bytes / 1024).toInt())),
                tint = Shop.colors.muted,
                onClick = {
                  scope.launch {
                    val opened = withContext(Dispatchers.IO) {
                      //  نسخهٔ خودکار رونوشتِ دفتر است؛ اگر دفتر رمزدار بود، این هم (SEC-01)
                      runCatching { store.parseBackup(ir.vil3ntec.tohid.data.AtRest.readText(context, snap.file)).getOrThrow() }
                    }
                    opened
                      .onSuccess { pendingRestore = it; restorePhotos = 0; restoreError = null }
                      .onFailure { restoreError = StrSettings.snapshotUnreadable }
                  }
                },
              )
            }
          }

          /*
           *  ── پشتیبانِ ابری ───────────────────────────────────────────
           *
           *  ⛔ **این تا امروز نبود.** نسخه‌های شبانه روی خودِ گوشی‌اند و
           *  پشتیبانِ دستی هم به حافظهٔ آدمی بسته بود که سرش شلوغ است.
           *  گوشی که گم یا آب می‌شد، کارِ چند سال با آن می‌رفت.
           *
           *  ⚠️ فهرست مالِ **همین دکان** است؛ شناسه‌اش از توکن می‌آید،
           *  نه از درخواست، پس هیچ دکانی پشتیبانِ دکانِ دیگری را نمی‌بیند.
           */
          Spacer(Modifier.height(12.dp))
          Text(
            StrSettings.cloudBackup,
            style = MaterialTheme.typography.labelMedium,
            color = Shop.colors.muted,
          )
          val cloud = cloudBackups
          SettingsRow(
            icon = Icons.Outlined.CloudUpload,
            title = if (cloudBusy) Str.sending else StrSettings.sendToServerNow,
            description = when {
              !signedIn -> StrSettings.needSignIn
              cloud == null -> StrSettings.nothingSentOrOffline
              cloud.stats.count == 0 -> StrSettings.nothingSent
              else -> StrSettings.cloudStats(
                plain(cloud.stats.count),
                plain((cloud.stats.usedBytes / 1024 / 1024).toInt()),
                plain((cloud.stats.quotaBytes / 1024 / 1024).toInt()),
              )
            },
            tint = Shop.colors.primary,
            onClick = {
              if (cloudBusy) return@SettingsRow
              if (!signedIn) { toast(StrSettings.signInFirst); return@SettingsRow }
              scope.launch {
                cloudBusy = true
                cloudNote = null
                val sent = AutoBackup.push(context, manual = true)
                cloudBusy = false
                cloudReload += 1
                toast(
                  if (sent) StrSettings.cloudSent
                  else StrSettings.cloudNotSent
                )
              }
            },
          )
          cloudNote?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
          }
          cloud?.items?.take(5)?.forEach { b ->
            SettingsRow(
              icon = Icons.Outlined.CloudDone,
              title = ir.vil3ntec.tohid.formatMillis(b.createdAt),
              description = StrSettings.kb(plain((b.bytes / 1024).toInt())) + " · " +
                (if (b.kind == "manual") StrSettings.manual else StrSettings.auto),
              tint = Shop.colors.muted,
              onClick = {
                //  ⚠️ برگرداندن از ابر عمداً این‌جا نیست: بازیابی کاری
                //  است که برنمی‌گردد و باید از همان درِ «بازیابی از فایل»
                //  برود تا کاربر اول ببیند داخلش چیست.
                toast(StrSettings.restoreFromPanel)
              },
            )
          }

          /*
           *  پشتیبانِ کامل در پوشه — `BackupMirror`.
           *  همان ZIPِ کامل (دفتر + عکس‌ها) در پوشه‌ای که دیده می‌شود؛ خودکار
           *  با هر پشتیبانِ ۱۲ ساعته، و این‌جا «همین حالا» (پوشه + سرور با هم).
           */
          Spacer(Modifier.height(10.dp))
          Text(StrSettings.folderBackup, style = MaterialTheme.typography.labelMedium, color = Shop.colors.muted)
          var mirrorTick by remember { mutableStateOf(0) }
          val mirrorDir = remember(mirrorTick) { ir.vil3ntec.tohid.data.BackupMirror.folder(context)?.path ?: "—" }
          val mirrorList = remember(mirrorTick) { ir.vil3ntec.tohid.data.BackupMirror.list(context) }
          var mirrorOn by remember { mutableStateOf(ir.vil3ntec.tohid.data.BackupMirror.enabled(context)) }
          var mirrorBusy by remember { mutableStateOf(false) }
          SettingsRow(
            icon = Icons.Outlined.CloudUpload,
            title = if (mirrorBusy) StrSettings.buildingDots else StrSettings.mirrorNow,
            description = StrSettings.mirrorDesc(mirrorList.size) +
              (mirrorList.firstOrNull()?.let { " · " + StrSettings.lastLabel + " " + ir.vil3ntec.tohid.formatMillis(it.lastModified()) } ?: ""),
            tint = Shop.colors.success,
            onClick = {
              if (mirrorBusy) return@SettingsRow
              mirrorBusy = true
              scope.launch {
                val local = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { ir.vil3ntec.tohid.data.BackupMirror.write(context) }
                val cloud = if (signedIn) AutoBackup.push(context, manual = true) else false
                mirrorBusy = false
                mirrorTick++; cloudReload += 1
                toast(
                  local.fold({ StrSettings.savedToFolder }, { StrSettings.folder(it.message ?: "") }) +
                    if (signedIn) " · " + (if (cloud) StrSettings.alsoOnServer else StrSettings.serverNotSent) else ""
                )
              }
            },
          )
          Text(StrSettings.folder(mirrorDir), style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
          Row(verticalAlignment = Alignment.CenterVertically) {
            if (ir.vil3ntec.tohid.ui.Platform.DESKTOP) {
              TextButton(onClick = {
                ir.vil3ntec.tohid.ui.Platform.pickFolder(mirrorDir)?.let { ir.vil3ntec.tohid.data.BackupMirror.setFolder(context, it); mirrorTick++ }
              }) { Text(StrSettings.chooseFolder) }
              TextButton(onClick = { if (!ir.vil3ntec.tohid.ui.Platform.openFolder(mirrorDir)) toast(StrSettings.folderNotOpened) }) { Text(StrSettings.openFolder) }
            }
            Spacer(Modifier.weight(1f))
            Text(StrSettings.every12h, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
            Switch(checked = mirrorOn, onCheckedChange = { mirrorOn = it; ir.vil3ntec.tohid.data.BackupMirror.setEnabled(context, it) })
          }

          /*
           *  فشرده کردنِ دفتر.
           *
           *  دستی است و نه خودکار، چون یک‌طرفه است و چون با همگام‌سازی
           *  درگیر می‌شود — شرحش سرِ `LedgerArchive`.
           */
          if (archivePlan.total > 0) {
            SettingsRow(
              icon = Icons.Outlined.Compress,
              title = StrSettings.compactLedger,
              description = StrSettings.compactDesc(plain(archivePlan.total)),
              tint = Shop.colors.warning,
              onClick = { confirmCompact = true },
            )
          }
          if (canUndo) {
            SettingsRow(
              icon = Icons.Outlined.Restore,
              title = StrSettings.undoRestore,
              description = StrSettings.undoRestoreDesc,
              tint = Shop.colors.warning,
              onClick = {
                scope.launch {
                  store.undoRestore()
                    .onSuccess { canUndo = false; toast(StrSettings.undone) }
                    .onFailure { toast(StrSettings.undoFailed) }
                }
              },
            )
          }
          restoreError?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.labelMedium, color = Shop.colors.danger)
          }
          //  پاک‌سازیِ کامل دفترِ دکان را روی همهٔ دستگاه‌ها و سرور خالی می‌کند ⇒ فقط صاحبِ دکان (نه مدیر)، دو مرحله (`WipeAllDialog`)
          if (ir.vil3ntec.tohid.data.ShopRole.current(context) == ir.vil3ntec.tohid.data.ShopRole.OWNER) {
            Spacer(Modifier.height(12.dp))
            TohidDangerButton(
              text = StrSettings.wipeAll,
              onClick = { confirmClear = true },
              modifier = Modifier.fillMaxWidth(),
            )
          }
        }
      }
    }

    /* ============================ درباره ============================ */
    item {
      SettingsGroup(SettingsLayout.ABOUT) {
        /* ============================ درباره ============================ */
        SettingsSection(
          icon = Icons.Filled.Info,
          title = Str.aboutApp,
          subtitle = Str.version(BuildConfig.VERSION_NAME),
          tint = Shop.colors.muted,
          value = BuildConfig.VERSION_NAME,
        ) {
          Text(
            StrSettings.aboutText,
            style = MaterialTheme.typography.bodySmall,
            color = Shop.colors.muted,
          )
          Spacer(Modifier.height(12.dp))
          SettingsRow(
            icon = Icons.Outlined.SystemUpdate,
            title = StrSettings.getLatest,
            description = StrSettings.installedVersion(BuildConfig.VERSION_NAME),
            tint = Shop.colors.primary,
            onClick = onUpdates,
          )
        }
      }
    }
  }

  val pick = LocalSettingsPick.current
  if (pick != null) {
    //  کامپیوتر: ستونِ وسطِ `DeskSettings` — همان که بود
    LazyColumn(
      Modifier.fillMaxSize(),
      contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 32.dp),
      verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
      item {
        Text(
          StrSettings.settingsIntro,
          style = MaterialTheme.typography.bodySmall,
          color = Shop.colors.muted,
        )
        Spacer(Modifier.height(4.dp))
      }
      body()
    }
  } else {
    PhoneSettingsFrame(onBack = onBack, body = body)
  }

  /* ---------------------------- پنجره‌ها ---------------------------- */

  pendingRestore?.let { incoming ->
    AlertDialog(
      onDismissRequest = { BackupBundle.dropStaging(context); pendingRestore = null; restorePhotos = 0 },
      containerColor = Shop.colors.surfaceSolid,
      title = { Text(StrSettings.restoreQ, color = Shop.colors.text) },
      text = {
        Column {
          Text(
            StrSettings.restoreWarn,
            style = MaterialTheme.typography.bodySmall,
            color = Shop.colors.muted,
          )
          Spacer(Modifier.height(12.dp))
          RestoreLine(Str.products, d.products.size, incoming.products.size)
          RestoreLine(Str.invoices, d.sales.size, incoming.sales.size)
          RestoreLine(Str.debtors, d.debtors.size, incoming.debtors.size)
          RestoreLine(Str.expenses, d.expenses.size, incoming.expenses.size)
          RestoreLine(StrSettings.warehouseEntries, d.warehouseEntries.size, incoming.warehouseEntries.size)
          Spacer(Modifier.height(8.dp))
          Text(
            if (restorePhotos > 0) StrSettings.restorePhotos(plain(restorePhotos))
            else StrSettings.restoreNoPhotos,
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
            //  ⚠️ بازیابیِ پشتیبان دادهٔ خودِ کاربر است؛ قفلِ نرمِ پایانِ اشتراک گروگانش نمی‌گیرد.
            //  S143: هرگز op نمی‌سازد؛ دستگاهِ همگام ⇒ اطلاعات از سرور. نشد (دیسک F0-01، شبکه، صف) ⇒
            //  دفترِ قبلی دست‌نخورده و دلیلش
            val r = store.restoreBackup(fixed)
            if (!r.ok) { toast(r.message); return@launch }
            canUndo = true
            restorePhotos = 0
            val done = if (moved > 0) StrSettings.restoredWithPhotos(plain(moved)) else StrSettings.restored
            toast(if (r.message.isEmpty()) done else "$done\n${r.message}")
          }
        }) { Text(StrSettings.restore, color = Shop.colors.danger) }
      },
      dismissButton = {
        TextButton(onClick = {
          BackupBundle.dropStaging(context)
          pendingRestore = null
          restorePhotos = 0
        }) { Text(Str.cancel) }
      },
    )
  }

  if (confirmCompact) {
    AlertDialog(
      onDismissRequest = { confirmCompact = false },
      containerColor = Shop.colors.surfaceSolid,
      title = { Text(StrSettings.compactQ, color = Shop.colors.text) },
      text = {
        Text(
          StrSettings.compactWarn(plain(archivePlan.total)),
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
                toast(StrSettings.archived(plain(it.moved.total)))
              }
              .onFailure { toast(StrSettings.compactFailed(it.message ?: Str.unknownReason)) }
          }
        }) { Text(StrSettings.compactDo) }
      },
      dismissButton = { TextButton(onClick = { confirmCompact = false }) { Text(Str.goBack) } },
    )
  }

  if (confirmRotate) {
    AlertDialog(
      onDismissRequest = { confirmRotate = false },
      containerColor = Shop.colors.surfaceSolid,
      title = { Text(StrSettings.rotateQ, color = Shop.colors.text) },
      text = {
        Text(
          StrSettings.rotateWarn,
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
                toast(StrSettings.newCodeMade)
              }
              .onFailure { toast(it.userMessage) }
            staffCodeBusy = false
          }
        }) { Text(StrSettings.make) }
      },
      dismissButton = { TextButton(onClick = { confirmRotate = false }) { Text(Str.cancel) } },
    )
  }

  if (confirmClear) {
    WipeAllDialog(
      shopName = storeName,
      onDismiss = { confirmClear = false },
      onConfirm = {
        confirmClear = false
        //  نقش در این فاصله عوض شد؟ دوباره سنجیده می‌شود
        if (ir.vil3ntec.tohid.data.ShopRole.current(context) != ir.vil3ntec.tohid.data.ShopRole.OWNER) toast(StrSettings.wipeOwnerOnly)
        else scope.launch {
          //  «پاک کردنِ همه» جایگزینیِ کامل است، نه ویرایشی روی پایه (`ShopStore.save`، پرچمِ `shop.ledger_merge`).
          //  نوشته نشد (قفلِ نرم، خطای دیسک) ⇒ پیامش را `AppRoot` می‌دهد؛ «پاک شد» دروغ نگوید
          if (store.save(ShopData(), replace = true)) toast(StrSettings.wiped)
        }
      },
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
        toast(if (had) StrSettings.passwordChanged else StrSettings.lockTurnedOn)
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
    TextButton(onClick = onCopy) { Text(Str.copy) }
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
  License.State.ACTIVE -> status.payload?.planTitle ?: StrSettings.active
  License.State.GRACE -> StrSettings.graceEnded
  License.State.EXPIRED -> StrSettings.ended
  License.State.PENDING -> StrSettings.notStarted
  License.State.INVALID -> StrSettings.licenseInvalid
  License.State.NONE -> StrSettings.noSubscription
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
    title = { Text(StrSettings.appPin, color = colors.text) },
    text = {
      Column {
        Text(
          StrSettings.pinNote,
          style = MaterialTheme.typography.bodySmall,
          color = colors.muted,
        )
        Spacer(Modifier.height(14.dp))
        NumberField(first, { if (it.length <= MAX_PIN) first = it }, StrSettings.pin)
        Spacer(Modifier.height(10.dp))
        NumberField(again, { if (it.length <= MAX_PIN) again = it }, StrSettings.pinAgain)
        if (again.isNotBlank() && first != again) {
          Spacer(Modifier.height(6.dp))
          Text(Str.passwordsDiffer, style = MaterialTheme.typography.labelSmall, color = colors.danger)
        }
      }
    },
    confirmButton = {
      TextButton(enabled = ready, onClick = { onSet(first) }) {
        Text(StrSettings.put, color = if (ready) colors.primary else colors.muted2)
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text(Str.cancel, color = colors.muted) } },
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
      StrSettings.pwdMin8,
      style = MaterialTheme.typography.bodySmall,
      color = Shop.colors.muted,
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
      value = current,
      onValueChange = { current = it },
      label = { Text(StrSettings.currentPassword) },
      singleLine = true,
      visualTransformation = PasswordVisualTransformation(),
      modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(10.dp))
    OutlinedTextField(
      value = fresh,
      onValueChange = { fresh = it },
      label = { Text(Str.newPassword) },
      singleLine = true,
      visualTransformation = PasswordVisualTransformation(),
      modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))
    TohidButton(
      text = StrSettings.changePassword,
      busy = busy,
      enabled = fresh.length >= 8,
      onClick = {
        busy = true
        scope.launch {
          auth.changePassword(current, fresh)
            .onSuccess { current = ""; fresh = ""; snackbarToast(StrSettings.passwordChanged) }
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
      Text(StrSettings.reading, style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
    } else if (devices.isEmpty()) {
      Text(
        StrSettings.noDevices,
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
                StrSettings.thisDevice,
                style = MaterialTheme.typography.labelSmall,
                color = Shop.colors.success,
              )
            }
          }
          // نشستِ همین گوشی را نمی‌بندیم؛ کاربر خودش را بیرون می‌کرد
          if (!isThis) {
            Text(
              StrSettings.closeSession,
              style = MaterialTheme.typography.labelMedium,
              color = Shop.colors.danger,
              modifier = Modifier
                .clip(Shape.chip)
                .clickable {
                  scope.launch {
                    account.revokeDevice(device.id)
                      .onSuccess { snackbarToast(StrSettings.sessionClosed); load() }
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
