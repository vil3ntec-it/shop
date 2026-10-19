package ir.vil3ntec.tohid.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.InsertChart
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import ir.vil3ntec.tohid.core.net.userText
import ir.vil3ntec.tohid.core.sync.LogoutGuard
import ir.vil3ntec.tohid.data.BackupMirror
import ir.vil3ntec.tohid.data.PhotoStore
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.formatMillis
import ir.vil3ntec.tohid.plain
import ir.vil3ntec.tohid.spanText
import ir.vil3ntec.tohid.sync.AutoSync
import ir.vil3ntec.tohid.sync.License
import ir.vil3ntec.tohid.sync.LicenseGuard
import ir.vil3ntec.tohid.sync.SyncStore
import ir.vil3ntec.tohid.sync.v1.SyncV1Engine
import ir.vil3ntec.tohid.sync.v1.SyncV1Worker
import ir.vil3ntec.tohid.ui.AppNav
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Colors
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 *  پیش‌نمایشِ عکس‌های خودکار (`PhonePreview`) — حسابِ نمایشی به‌جای `SyncStore`،
 *  تا «واردشده» و «مهمان» هر دو عکس بگیرند. در برنامهٔ دستِ کاربر همیشه null است.
 */
object ProfileShot {
  val account = mutableStateOf<ProfileModel.Account?>(null)
  val plan = mutableStateOf<ProfileModel.Plan?>(null)
}

private const val ROOT = "root"
private const val EDIT = "edit"

/**
 *  «حساب من» — گوشی نسخهٔ ۳، تصویرِ مرجعِ r4 (راست)، راست‌به‌چپ.
 *
 *  زمینهٔ مه‌گرفته، عنوانِ درشت و زنگِ گرد؛ عکسِ گرد که لبهٔ پایینش زیرِ کارتِ
 *  شیشه‌ای می‌رود؛ در کارت نام و شماره، نشانِ سیاهِ نقش، سه کادرِ سفیدِ آمار از
 *  دفترِ واقعی (فاکتور، کالا، قرض‌دار) و نوارِ تیرهٔ اشتراک با دکمهٔ سفید؛ زیرش
 *  فهرستِ سفید با آیکنِ خطی و خطِ مویی. «مهمان» (وارد نشده) همان چیدمان است با
 *  دکمهٔ «ورود».
 *
 *  ── چه چیزی کجا رفت (هیچ‌چیز برداشته نشد) ─────────────────────────
 *   • ویرایشِ نام، دیدنِ ایمیلِ قفل، ثبتِ یک‌بارهٔ شماره، نامِ دکان، عکسِ حساب و
 *     مدتِ عضویت ⇐ ردیفِ «ویرایش حساب و دکان» (زیرصفحهٔ همین صفحه).
 *   • اشتراک ⇐ نوارِ تیره (`ProfileModel.banner`) و ردیفِ «اشتراک و پرداخت».
 *   • همگام‌سازی ⇐ «تنظیمات ⇐ داده‌ها ⇐ همگام‌سازی».
 *   • خروج ⇐ ردیفِ سرخِ «خروج از حساب» با همان قفلِ SYN-10.
 *  ──────────────────────────────────────────────────────────────────
 *
 *  سه قاعدهٔ حساب سرِ جایش است: **نام عوض می‌شود**؛ **ایمیل قفل است** (راهِ ورود
 *  و بازیابیِ رمز)؛ **شماره یک بار ثبت می‌شود و بعد قفل** (سرور هم جدا می‌سنجد).
 *  بی‌اینترنت هم باز می‌شود: هرچه نشان می‌دهد از حافظهٔ گوشی است و اگر آنتن بود،
 *  همان لحظه از سرور تازه می‌شود.
 */
@Composable
fun ProfileScreen(
  store: ir.vil3ntec.tohid.data.ShopStore,
  snackbar: SnackbarHostState,
  onBack: () -> Unit,
  onSignIn: () -> Unit,
  onSubscription: () -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val state = remember { SyncStore(context) }
  val shot = ProfileShot.account.value
  val signedIn = shot?.signedIn ?: !state.accessToken.isNullOrBlank()

  var name by remember { mutableStateOf(state.accountName) }
  var email by remember { mutableStateOf(state.accountEmail) }
  var phone by remember { mutableStateOf(state.accountPhone) }
  var createdAt by remember { mutableStateOf(state.accountCreatedAt) }
  var newPhone by remember { mutableStateOf("") }
  var busy by remember { mutableStateOf(false) }
  val v1 by SyncV1Engine.of(context).status.collectAsState()
  var confirmSignOut by remember { mutableStateOf(false) }
  //  SYN-10: صفِ پُر ⇒ خروج بسته؛ «خروجِ اجباری» = پشتیبانِ دیدنی + دو تأیید
  var blockSignOut by remember { mutableStateOf(false) }
  var forcedBackup by remember { mutableStateOf<String?>(null) }
  var alertsOpen by remember { mutableStateOf(false) }

  /*
   *  نامِ دکان — همان‌جایی که تنظیمات هم از آن می‌خواند
   *  (`SharedPreferences("tohid")`، کلیدِ `store_name`). مالِ همین حساب است و
   *  روی سربرگِ فاکتورِ همین دکان چاپ می‌شود.
   */
  val prefs = remember { context.getSharedPreferences("tohid", android.content.Context.MODE_PRIVATE) }
  var shopName by remember { mutableStateOf(prefs.getString("store_name", "").orEmpty()) }

  //  عکسِ حساب — با هر تغییر این عدد بالا می‌رود تا نسخهٔ کش‌شده دور ریخته شود
  var avatarVersion by remember { mutableStateOf(0) }
  var pickOpen by remember { mutableStateOf(false) }

  var page by rememberSaveable { mutableStateOf(ROOT) }
  BackHandler(enabled = page != ROOT) { page = ROOT }

  fun toast(text: String) {
    scope.launch { snackbar.showSnackbar(text) }
  }

  /*
   *  تازه‌سازی از سرور — بی‌صدا. شکستش پیام ندارد: کسی که آفلاین پروفایلش را
   *  باز می‌کند نباید خطای شبکه ببیند.
   */
  LaunchedEffect(signedIn) {
    if (!signedIn || shot != null || !Backend.isReady(context)) return@LaunchedEffect
    Backend.account(context).me().onSuccess { me ->
      state.accountName = me.user.name
      state.accountEmail = me.user.email.orEmpty()
      state.accountPhone = me.user.phone.orEmpty()
      state.accountCreatedAt = me.user.createdAt
      name = me.user.name
      email = me.user.email.orEmpty()
      phone = me.user.phone.orEmpty()
      createdAt = me.user.createdAt
    }
  }

  PhotoSourceSheet(
    open = pickOpen,
    onDismiss = { pickOpen = false },
    note = "عکسِ حساب فقط روی همین گوشی می‌ماند",
  ) { uri ->
    PhotoStore.saveAvatar(context, state.accountId.ifBlank { "local" }, uri)
      .onSuccess { avatarVersion++; toast("عکس حساب ثبت شد") }
      .onFailure { toast("عکس ذخیره نشد") }
  }

  val ledger by store.data.collectAsState()
  val alerts = rememberAlerts(ledger)
  //  نقش فقط برای حسابِ واردشده معنا دارد (همان `ShopRole`ِ سدِ تنظیمات)
  val account = shot ?: ProfileModel.Account(
    signedIn = signedIn,
    name = name,
    email = email,
    phone = phone,
    role = if (signedIn) ir.vil3ntec.tohid.data.ShopRole.current(context) else "",
  )
  //  وضعیتِ اشتراک فقط برای حسابِ واردشده خوانده می‌شود
  val livePlan = if (signedIn && shot == null) rememberPlan() else null
  val plan = ProfileShot.plan.value ?: livePlan
  val canSettings = remember { ir.vil3ntec.tohid.data.ShopRole.canOpenSettings(context) }
  val accountId = state.accountId.ifBlank { "local" }

  fun signOutPressed() {
    when (LogoutGuard.decide(v1.queued, AutoSync.pendingCount)) {
      LogoutGuard.Allow -> confirmSignOut = true
      is LogoutGuard.Block -> blockSignOut = true
    }
  }

  val c = PhoneV3.colors
  Box(Modifier.fillMaxSize().frosted(c)) {
    V3Pages(page, depth = { if (it == ROOT) 0 else 1 }) { p ->
      if (p == ROOT) {
        ProfileRoot(
          account = account,
          shopName = shopName,
          accountId = accountId,
          avatarVersion = avatarVersion,
          stats = listOf(
            "فاکتور" to ledger.sales.size,
            "کالا" to ledger.products.size,
            "قرض‌دار" to ledger.debtors.size,
          ),
          banner = ProfileModel.banner(signedIn, plan, SUBSCRIPTION_WARN_DAYS, ::plain),
          menu = ProfileModel.menu(signedIn, canSettings),
          alerts = alerts.size,
          onBell = { alertsOpen = true },
          onAvatar = { pickOpen = true },
          onBanner = { action ->
            when (action) {
              ProfileModel.Action.SIGN_IN -> onSignIn()
              ProfileModel.Action.RENEW, ProfileModel.Action.PLANS -> onSubscription()
            }
          },
          onMenu = { item ->
            when {
              item.id == ProfileModel.EDIT -> page = EDIT
              item.id == ProfileModel.SIGN_OUT -> signOutPressed()
              item.route == "settings" -> SettingsNav.open(from = "profile")
              item.route != null -> AppNav.go(item.route)
            }
          },
        )
      } else {
        ProfileEditPage(
          accountId = accountId,
          avatarVersion = avatarVersion,
          initial = ProfileModel.initial(account),
          name = name,
          onName = { name = it },
          email = email,
          phone = phone,
          newPhone = newPhone,
          onNewPhone = { newPhone = it },
          shopName = shopName,
          onShopName = {
            shopName = it
            prefs.edit().putString("store_name", it.trim()).apply()
          },
          createdAt = createdAt,
          busy = busy,
          onBack = { page = ROOT },
          onPhoto = { pickOpen = true },
          onSave = save@{
            val cleanName = name.trim()
            if (cleanName.isBlank()) {
              toast("نام را بنویسید")
              return@save
            }
            //  نامِ دکان همین‌جا و همان لحظه می‌نشیند؛ روی گوشی است و اینترنت نمی‌خواهد
            prefs.edit().putString("store_name", shopName.trim()).apply()
            val claim = newPhone.trim()
            busy = true
            scope.launch {
              //  نام روی گوشی همان لحظه می‌نشیند: برنامه آفلاین هم کار می‌کند و
              //  همین نام است که در سربرگ و رسیدها دیده می‌شود
              state.accountName = cleanName
              name = cleanName

              if (!Backend.isReady(context) || !Backend.isOnline(context)) {
                busy = false
                toast(
                  if (claim.isBlank()) "نام روی گوشی ذخیره شد — برای ثبت روی سرور به اینترنت نیاز است"
                  else "ثبتِ شماره به اینترنت نیاز دارد"
                )
                return@launch
              }

              Backend.account(context).updateProfile(cleanName, claim)
                .onSuccess { done ->
                  state.accountName = done.user.name
                  state.accountEmail = done.user.email.orEmpty()
                  state.accountPhone = done.user.phone.orEmpty()
                  name = done.user.name
                  email = done.user.email.orEmpty()
                  phone = done.user.phone.orEmpty()
                  newPhone = ""
                  toast(if (claim.isNotBlank()) "شماره ثبت شد و قفل شد" else "ذخیره شد")
                }
                .onFailure { toast(it.userMessage) }
              busy = false
            }
          },
        )
      }
    }
  }

  /* ---------------------------- هشدارها (زنگ) ---------------------------- */
  if (alertsOpen) {
    AdaptiveSheet(onDismissRequest = { alertsOpen = false }, containerColor = c.bg) {
      Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 28.dp)) {
        Text("هشدارهای فروشگاه", style = v3Text(17, FontWeight.Bold, 24), color = c.ink, modifier = Modifier.padding(start = 6.dp, bottom = 12.dp))
        if (alerts.isEmpty()) {
          EmptyNote("همه‌چیز مرتب است — هشداری نیست.")
        } else {
          V3Card {
            alerts.forEach { alert ->
              //  زدنِ هشدار همان صفحه‌ای را باز می‌کند که کار در آن درست می‌شود
              V3Row(
                icon = null,
                label = alert.title,
                description = alert.detail,
                value = alert.value,
                onClick = { alertsOpen = false; AppNav.go(alert.target) },
              )
            }
          }
        }
      }
    }
  }

  fun signOutNow() {
    busy = true
    scope.launch {
      runCatching { Backend.auth(context).signOut() }
      state.signOut()
      busy = false
      page = ROOT
      toast("از حساب خارج شدید")
      onBack()
    }
  }

  /*
   *  SYN-10 — داده‌ای که هنوز به سرور نرفته، خروج را می‌بندد.
   *
   *  عدد از همان صفِ واقعیِ Sync v1 است و زنده تازه می‌شود: صف که خالی شد،
   *  همین پنجره «حالا می‌توانید خارج شوید» می‌گوید. راهِ دیگر فقط «خروجِ
   *  اجباری» است: اول پشتیبانِ کامل در پوشهٔ دیدنی (`BackupMirror`)، و اگر
   *  نوشته نشد خروجی هم در کار نیست؛ بعد پرسشِ دوم.
   */
  val colors = Shop.colors
  if (blockSignOut) {
    val verdict = LogoutGuard.decide(v1.queued, AutoSync.pendingCount)
    val online = remember(v1) { Backend.isOnline(context) }
    AlertDialog(
      onDismissRequest = { if (!busy) blockSignOut = false },
      containerColor = colors.bg,
      title = { Text(if (verdict is LogoutGuard.Block) LogoutGuard.TITLE else "همه‌چیز به سرور رسید", color = colors.text) },
      text = {
        Column {
          Text(
            when (verdict) {
              is LogoutGuard.Block -> LogoutGuard.message(verdict.pending, online)
              LogoutGuard.Allow -> "صف خالی است؛ حالا می‌توانید از حساب خارج شوید."
            },
            color = colors.muted,
          )
          if (v1.lastError.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(v1.lastError, style = MaterialTheme.typography.labelMedium, color = colors.danger)
          }
          if (verdict is LogoutGuard.Block) {
            Spacer(Modifier.height(8.dp))
            TextButton(enabled = !busy, onClick = {
              busy = true
              scope.launch {
                val written = withContext(Dispatchers.IO) { BackupMirror.write(context) }
                busy = false
                written
                  .onSuccess { forcedBackup = it.absolutePath; blockSignOut = false }
                  .onFailure { toast(it.userText("پشتیبان نوشته نشد؛ خروجِ اجباری انجام نشد")) }
              }
            }) { Text(if (busy) "در حالِ نوشتنِ پشتیبان…" else LogoutGuard.FORCE, color = colors.danger) }
          }
        }
      },
      confirmButton = {
        if (verdict is LogoutGuard.Block) {
          TextButton(enabled = !busy, onClick = {
            runCatching { SyncV1Worker.now(context) }
              .onSuccess { toast("همگام‌سازی شروع شد") }
              .onFailure { toast(it.userText("همگام‌سازی ناموفق بود")) }
          }) { Text(LogoutGuard.SYNC_NOW, color = colors.primary) }
        } else {
          TextButton(enabled = !busy, onClick = { blockSignOut = false; confirmSignOut = true }) {
            Text("خروج", color = colors.danger)
          }
        }
      },
      dismissButton = {
        TextButton(enabled = !busy, onClick = { blockSignOut = false }) { Text(LogoutGuard.STAY, color = colors.muted) }
      },
    )
  }

  forcedBackup?.let { path ->
    val pending = (LogoutGuard.decide(v1.queued, AutoSync.pendingCount) as? LogoutGuard.Block)?.pending ?: 0
    AlertDialog(
      onDismissRequest = { forcedBackup = null },
      containerColor = colors.bg,
      title = { Text(LogoutGuard.FORCE_TITLE, color = colors.text) },
      text = { Text(LogoutGuard.forceMessage(pending, path), color = colors.muted) },
      confirmButton = {
        TextButton(enabled = !busy, onClick = {
          forcedBackup = null
          //  کلیکِ «خروجِ اجباری» تأییدِ اول بود، این دومی است
          if (LogoutGuard.canForce(backupWritten = true, confirmations = 2)) signOutNow()
        }) { Text("باز هم خارج شو", color = colors.danger) }
      },
      dismissButton = {
        TextButton(onClick = { forcedBackup = null }) { Text(LogoutGuard.STAY, color = colors.muted) }
      },
    )
  }

  if (confirmSignOut) {
    AlertDialog(
      onDismissRequest = { confirmSignOut = false },
      containerColor = colors.bg,
      title = { Text("خروج از حساب؟", color = colors.text) },
      text = {
        Text(
          "برای برگشتن، ایمیل و رمزتان را دوباره می‌زنید. دفترِ دکان روی همین گوشی سرِ جایش می‌ماند.",
          color = colors.muted,
        )
      },
      confirmButton = {
        TextButton(enabled = !busy, onClick = {
          confirmSignOut = false
          /*
           *  خروج، واقعاً: اول به سرور می‌گوییم نشست را باطل کند و بعد گوشی را
           *  پاک می‌کنیم (`SyncStore.signOut` توکن را پاک می‌کند و بعد از آن
           *  چیزی برای فرستادن نمی‌ماند). نتیجهٔ سرور جلوی خروج را نمی‌گیرد:
           *  کسی که «خروج» زده باید خارج شود، حتی اگر نت نباشد.
           */
          //  میانِ دو کلیک چیزی ثبت شده باشد، قفل دوباره سر می‌رسد (SYN-10)
          if (LogoutGuard.decide(v1.queued, AutoSync.pendingCount) is LogoutGuard.Block) blockSignOut = true
          else signOutNow()
        }) { Text("خروج", color = colors.danger) }
      },
      dismissButton = {
        TextButton(onClick = { confirmSignOut = false }) { Text("ماندن", color = colors.muted) }
      },
    )
  }
}

/* ============================ صفحهٔ اصلی (r4) ============================ */

@Composable
private fun ProfileRoot(
  account: ProfileModel.Account,
  shopName: String,
  accountId: String,
  avatarVersion: Int,
  stats: List<Pair<String, Int>>,
  banner: ProfileModel.Banner,
  menu: List<ProfileModel.MenuItem>,
  alerts: Int,
  onBell: () -> Unit,
  onAvatar: () -> Unit,
  onBanner: (ProfileModel.Action) -> Unit,
  onMenu: (ProfileModel.MenuItem) -> Unit,
) {
  val c = PhoneV3.colors
  //  کامپیوتر سربرگِ خودش را دارد؛ عنوانِ درشت و زنگ فقط در عرضِ گوشی
  val ownHeader = !ir.vil3ntec.tohid.ui.Platform.DESKTOP || screenSize() == Screen.PHONE
  Column(
    Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .windowInsetsPadding(WindowInsets.statusBars),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Column(Modifier.widthIn(max = 520.dp).fillMaxWidth().padding(horizontal = 16.dp)) {
      if (ownHeader) {
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
          Text(
            "حساب من",
            style = v3Text(30, FontWeight.Bold, 40),
            color = c.ink,
            modifier = Modifier.weight(1f).padding(start = 2.dp),
          )
          V3RoundButton(Icons.Outlined.Notifications, "هشدارها", size = 54.dp, iconSize = 24.dp, dot = alerts > 0, onClick = onBell)
        }
      } else Spacer(Modifier.height(24.dp))

      /*
       *  عکس و کارت: عکسِ ۱۲۸dp وسط، و کارتِ شیشه‌ای ۲۲dp روی لبهٔ پایینش
       *  می‌نشیند (r4) — کارت بعد از عکس کشیده می‌شود تا رویش باشد.
       */
      val avatarSize = 128.dp
      val tuck = 22.dp
      Box(Modifier.fillMaxWidth().offset(y = if (ownHeader) (-12).dp else 0.dp)) {
        ProfileAvatar(
          accountId = accountId,
          version = avatarVersion,
          initial = ProfileModel.initial(account),
          size = avatarSize,
          modifier = Modifier
            .align(Alignment.TopCenter)
            .zIndex(0f)
            .clickable(role = Role.Button, onClickLabel = "عوض کردنِ عکس حساب", onClick = onAvatar),
        )
        ProfileCard(
          account = account,
          shopName = shopName,
          stats = stats,
          banner = banner,
          onBanner = onBanner,
          modifier = Modifier.padding(top = avatarSize - tuck).zIndex(1f),
        )
      }
      Spacer(Modifier.height(if (ownHeader) 0.dp else 12.dp))
      ProfileMenu(menu, onMenu)
      Spacer(Modifier.height(24.dp))
    }
  }
}

/** کارتِ شیشه‌ای: نام، شماره، نقش، سه کادرِ آمار، نوارِ تیرهٔ اشتراک */
@Composable
private fun ProfileCard(
  account: ProfileModel.Account,
  shopName: String,
  stats: List<Pair<String, Int>>,
  banner: ProfileModel.Banner,
  onBanner: (ProfileModel.Action) -> Unit,
  modifier: Modifier = Modifier,
) {
  val c = PhoneV3.colors
  val shape = RoundedCornerShape(PhoneV3.cardRadius)
  Column(
    modifier
      .fillMaxWidth()
      .shadow(18.dp, shape, clip = false, ambientColor = c.ink.copy(alpha = 0.10f), spotColor = c.ink.copy(alpha = 0.12f))
      .clip(shape)
      .background(c.cardGlass)
      .padding(start = 22.dp, end = 22.dp, top = 24.dp, bottom = 22.dp),
  ) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Text(
          ProfileModel.displayName(account),
          style = v3Text(20, FontWeight.Bold, 28),
          color = c.ink,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        val contact = ProfileModel.contact(account, shopName)
        if (contact.isNotBlank()) {
          //  شماره و ایمیل چپ‌به‌راست خوانده می‌شوند، وگرنه `+93` ته خط می‌رود
          val ltr = account.signedIn
          CompositionLocalProvider(LocalLayoutDirection provides if (ltr) LayoutDirection.Ltr else LocalLayoutDirection.current) {
            Text(
              contact,
              style = v3Text(13, FontWeight.Medium, 18),
              color = c.inkMuted,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }
      ProfileModel.badge(account)?.let { role ->
        Spacer(Modifier.width(10.dp))
        RoleBadge(role)
      }
    }
    Spacer(Modifier.height(14.dp))
    val shown = rememberAppear(60)
    Row(Modifier.fillMaxWidth().appear(shown), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      stats.forEach { (label, count) -> StatBox(label, count, Modifier.weight(1f)) }
    }
    Spacer(Modifier.height(14.dp))
    val shownBanner = rememberAppear(140)
    PlanBanner(banner, Modifier.appear(shownBanner)) { onBanner(banner.action) }
  }
}

/** نشانِ سیاهِ نقش با تاج (r4 «Premium»); در تمِ شب سفید با متنِ تیره */
@Composable
private fun RoleBadge(role: String) {
  val c = PhoneV3.colors
  val bg = c.ink
  val fg = c.card
  Row(
    Modifier
      .clip(RoundedCornerShape(percent = 50))
      .background(bg)
      .padding(start = 12.dp, end = 14.dp, top = 7.dp, bottom = 7.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(CrownIcon, contentDescription = null, tint = fg, modifier = Modifier.size(15.dp))
    Spacer(Modifier.width(6.dp))
    Text(role, style = v3Text(13, FontWeight.Bold, 18), color = fg, maxLines = 1)
  }
}

/** کادرِ سفیدِ آمار (r4): عددِ درشت و برچسبِ خاکستری */
@Composable
private fun StatBox(label: String, count: Int, modifier: Modifier = Modifier) {
  val c = PhoneV3.colors
  val shape = RoundedCornerShape(PhoneV3.groupRadius)
  Column(
    modifier
      .height(76.dp)
      .shadow(1.dp, shape, clip = false, ambientColor = c.ink.copy(alpha = 0.06f), spotColor = c.ink.copy(alpha = 0.06f))
      .clip(shape)
      .background(c.card)
      .semantics(mergeDescendants = true) {},
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Text(plain(count), style = v3Text(24, FontWeight.Bold, 30), color = c.ink, maxLines = 1)
    Spacer(Modifier.height(2.dp))
    Text(label, style = v3Text(12, FontWeight.Medium, 16), color = c.inkMuted, maxLines = 1)
  }
}

/**
 *  نوارِ تیرهٔ اشتراک (r4 «Kredin Tükendi!»): شیبِ خاکستری ⇐ سیاه، ستارهٔ سفید،
 *  عنوان و زیرعنوان، و دکمهٔ قرصیِ سفید. در راست‌به‌چپ سمتِ روشن در آغاز (راست).
 */
@Composable
private fun PlanBanner(b: ProfileModel.Banner, modifier: Modifier = Modifier, onClick: () -> Unit) {
  val c = PhoneV3.colors
  val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
  val light = lerp(c.black, c.onBlackMuted, 0.62f)
  val stops = if (rtl) listOf(c.black, c.black, light) else listOf(light, c.black, c.black)
  Row(
    modifier
      .fillMaxWidth()
      .heightIn(min = 58.dp)
      .clip(RoundedCornerShape(PhoneV3.groupRadius))
      .background(Brush.horizontalGradient(stops))
      .padding(start = 16.dp, end = 10.dp, top = 9.dp, bottom = 9.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(Icons.Filled.Star, contentDescription = null, tint = c.onBlack, modifier = Modifier.size(22.dp))
    Spacer(Modifier.width(12.dp))
    Column(Modifier.weight(1f)) {
      Text(b.title, style = v3Text(14, FontWeight.Bold, 20), color = c.onBlack, maxLines = 1, overflow = TextOverflow.Ellipsis)
      Text(b.subtitle, style = v3Text(11, FontWeight.Medium, 16), color = c.onBlack.copy(alpha = 0.78f), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
    Spacer(Modifier.width(8.dp))
    Box(
      Modifier
        .v3PressScale(onClick, b.button)
        .heightIn(min = 34.dp)
        .clip(RoundedCornerShape(percent = 50))
        .background(c.onBlack)
        .padding(horizontal = 16.dp, vertical = 7.dp),
      contentAlignment = Alignment.Center,
    ) {
      Text(b.button, style = v3Text(13, FontWeight.SemiBold, 18), color = c.black, maxLines = 1)
    }
  }
}

/** فهرستِ سفیدِ r4: آیکنِ خطی، نام، فلش؛ خطِ مویی میانِ ردیف‌ها */
@Composable
private fun ProfileMenu(items: List<ProfileModel.MenuItem>, onClick: (ProfileModel.MenuItem) -> Unit) {
  val c = PhoneV3.colors
  val shape = RoundedCornerShape(24.dp)
  Column(
    Modifier
      .fillMaxWidth()
      .shadow(12.dp, shape, clip = false, ambientColor = c.ink.copy(alpha = 0.08f), spotColor = c.ink.copy(alpha = 0.08f))
      .clip(shape)
      .background(c.cardGlass),
  ) {
    items.forEachIndexed { i, item ->
      if (i > 0) V3Divider()
      V3Row(
        icon = menuIcon(item.id),
        label = item.label,
        tile = false,
        height = 64.dp,
        danger = item.danger,
        chevron = !item.danger,
        onClick = { onClick(item) },
      )
    }
  }
}

private fun menuIcon(id: String): ImageVector = when (id) {
  ProfileModel.EDIT -> Icons.Outlined.ManageAccounts
  "devices" -> Icons.Outlined.Devices
  "plan" -> Icons.Outlined.StarBorder
  "stats" -> Icons.Outlined.InsertChart
  "settings" -> Icons.Outlined.Settings
  "support" -> Icons.Outlined.SupportAgent
  else -> Icons.AutoMirrored.Outlined.Logout
}

/**
 *  عکسِ حساب در حلقهٔ سفید؛ بی‌عکس، حرفِ اولِ نام روی دایرهٔ خاکستریِ نرم، و
 *  «مهمان» آدمکِ خطی.
 */
@Composable
private fun ProfileAvatar(accountId: String, version: Int, initial: String?, size: Dp, modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val c = PhoneV3.colors
  var bitmap by remember(accountId, version) { mutableStateOf<android.graphics.Bitmap?>(null) }
  //  خواندن از دیسک روی نخِ پس‌زمینه — هرچه کوچک، روی نخِ اصلی صفحه را می‌خواباند
  LaunchedEffect(accountId, version) {
    bitmap = withContext(Dispatchers.IO) { PhotoStore.loadAvatar(context, accountId) }
  }
  Box(
    modifier
      .size(size)
      .shadow(10.dp, CircleShape, clip = false, ambientColor = c.ink.copy(alpha = 0.12f), spotColor = c.ink.copy(alpha = 0.12f))
      .clip(CircleShape)
      .background(c.card)
      .padding(4.dp)
      .clip(CircleShape)
      .background(Brush.linearGradient(listOf(c.tile, lerp(c.tile, c.inkMuted, 0.28f)))),
    contentAlignment = Alignment.Center,
  ) {
    val image = bitmap
    when {
      image != null -> Image(
        bitmap = image.asImageBitmap(),
        contentDescription = "عکس حساب",
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
      )
      initial != null -> Text(initial, style = v3Text(46, FontWeight.Bold, 56), color = c.inkMuted)
      else -> Icon(Icons.Outlined.Person, contentDescription = null, tint = c.inkMuted, modifier = Modifier.size(size * 0.46f))
    }
  }
}

/* ============================ ویرایشِ حساب و دکان ============================ */

@Composable
private fun ProfileEditPage(
  accountId: String,
  avatarVersion: Int,
  initial: String?,
  name: String,
  onName: (String) -> Unit,
  email: String,
  phone: String,
  newPhone: String,
  onNewPhone: (String) -> Unit,
  shopName: String,
  onShopName: (String) -> Unit,
  createdAt: Long,
  busy: Boolean,
  onBack: () -> Unit,
  onPhoto: () -> Unit,
  onSave: () -> Unit,
) {
  val c = PhoneV3.colors
  Column(
    Modifier
      .fillMaxSize()
      .background(c.bg)
      .windowInsetsPadding(WindowInsets.statusBars)
      .imePadding(),
  ) {
    V3Header("ویرایش حساب و دکان", onBack)
    Column(
      Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Column(Modifier.widthIn(max = 520.dp).fillMaxWidth()) {
        /* --- عکس --- */
        Spacer(Modifier.height(8.dp))
        V3Card(padding = PaddingValues(14.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            ProfileAvatar(accountId, avatarVersion, initial, 64.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
              Text("عکسِ حساب", style = v3Text(15, FontWeight.SemiBold, 22), color = c.ink)
              Text("فقط روی همین گوشی می‌ماند", style = v3Text(12, FontWeight.Medium, 17), color = c.inkMuted)
            }
            V3PillButton("عوض کردن", Icons.Outlined.PhotoCamera, onPhoto)
          }
        }

        /* --- حساب --- */
        V3GroupTitle("حساب", Modifier.padding(top = 18.dp))
        V3Card(padding = PaddingValues(16.dp)) {
          OutlinedTextField(
            value = name,
            onValueChange = onName,
            label = { Text("نام و نام خانوادگی") },
            singleLine = true,
            colors = v3FieldColors(c),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
          )
          Spacer(Modifier.height(12.dp))
          /*
           *  ایمیل — دیده می‌شود، دست نمی‌خورد. کاربری که دو ایمیل دارد باید
           *  بتواند ببیند با کدام وارد شده.
           */
          LockedRow(
            icon = Icons.Outlined.AlternateEmail,
            label = "ایمیل",
            value = email.ifBlank { "ثبت نشده" },
            note = if (email.isBlank()) "شما با شماره وارد شده‌اید"
            else "ایمیلِ حساب عوض نمی‌شود — راهِ ورود و بازیابیِ رمز همین است",
            ltr = email.isNotBlank(),
          )
          Spacer(Modifier.height(12.dp))
          if (phone.isBlank()) {
            /*
             *  شماره ندارد — یک بار می‌شود ثبتش کرد: بازیابیِ رمز و خبرهای دکان
             *  از راهِ شماره می‌روند.
             */
            OutlinedTextField(
              value = newPhone,
              onValueChange = onNewPhone,
              label = { Text("شماره موبایل — یک بار ثبت می‌شود") },
              placeholder = { Text("07XXXXXXXX") },
              singleLine = true,
              keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Phone),
              colors = v3FieldColors(c),
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            Text("شماره پس از ثبت قفل می‌شود و از برنامه عوض نمی‌شود.", style = v3Text(12, FontWeight.Medium, 17), color = c.inkMuted)
          } else {
            LockedRow(
              icon = Icons.Outlined.Lock,
              label = "شماره موبایل",
              value = phone,
              note = "شماره ثبت شده و قفل است",
              ltr = true,
            )
          }
          Spacer(Modifier.height(16.dp))
          V3PrimaryButton(if (busy) "در حال ذخیره…" else "ذخیره تغییرات", enabled = !busy, onClick = onSave)
        }

        /* --- دکان --- */
        V3GroupTitle("دکان", Modifier.padding(top = 18.dp))
        V3Card(padding = PaddingValues(16.dp)) {
          OutlinedTextField(
            value = shopName,
            onValueChange = onShopName,
            label = { Text("نام فروشگاه") },
            leadingIcon = { Icon(Icons.Outlined.Storefront, contentDescription = null, tint = c.icon) },
            singleLine = true,
            colors = v3FieldColors(c),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
          )
          Spacer(Modifier.height(4.dp))
          Text("این نام روی سربرگ فاکتور چاپ می‌شود.", style = v3Text(12, FontWeight.Medium, 17), color = c.inkMuted)
        }

        /* --- عضویت --- */
        V3GroupTitle("عضویت", Modifier.padding(top = 18.dp))
        V3Card {
          V3Row(
            icon = Icons.Outlined.Schedule,
            label = "مدت عضویت",
            value = if (createdAt > 0) spanText(createdAt) else "—",
            description = if (createdAt > 0) "عضو از ${formatMillis(createdAt)}" else "روزِ ساختنِ حساب معلوم نیست",
          )
        }
      }
    }
  }
}

@Composable
private fun v3FieldColors(c: PhoneV3Colors) = OutlinedTextFieldDefaults.colors(
  focusedBorderColor = c.ink,
  unfocusedBorderColor = c.hairline,
  focusedLabelColor = c.ink,
  unfocusedLabelColor = c.inkMuted,
  cursorColor = c.ink,
  focusedTextColor = c.ink,
  unfocusedTextColor = c.ink,
)

/** دکمهٔ اصلیِ تک‌رنگ: قرصِ سیاه با متنِ سفید (در شب برعکس) */
@Composable
internal fun V3PrimaryButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
  val c = PhoneV3.colors
  Box(
    Modifier
      .then(if (enabled) Modifier.v3PressScale(onClick, text) else Modifier)
      .fillMaxWidth()
      .height(52.dp)
      .clip(RoundedCornerShape(percent = 50))
      .background(if (enabled) c.ink else c.ink.copy(alpha = 0.4f)),
    contentAlignment = Alignment.Center,
  ) {
    Text(text, style = v3Text(15, FontWeight.SemiBold, 22), color = c.card)
  }
}

/** دکمهٔ قرصیِ کوچکِ خاکستری با آیکن */
@Composable
internal fun V3PillButton(text: String, icon: ImageVector?, onClick: () -> Unit) {
  val c = PhoneV3.colors
  Row(
    Modifier
      .v3PressScale(onClick, text)
      .clip(RoundedCornerShape(percent = 50))
      .background(c.bg)
      .padding(horizontal = 14.dp, vertical = 9.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (icon != null) {
      Icon(icon, contentDescription = null, tint = c.icon, modifier = Modifier.size(18.dp))
      Spacer(Modifier.width(6.dp))
    }
    Text(text, style = v3Text(13, FontWeight.SemiBold, 18), color = c.ink)
  }
}

/**
 *  وضعیتِ اشتراکِ همین حساب — مجوزِ امضاشدهٔ روی گوشی، وگرنه حرفِ سرور.
 *  تصمیم در `ProfileModel.plan` است (خالص و آزمون‌شده) و نوارِ تیرهٔ کارت از
 *  همین می‌خواند. حرفِ سرور از همان کَشی می‌آید که سربرگ می‌خواند
 *  (`SubscriptionPulse`) — وگرنه دو جا دو حرف می‌زدند.
 */
@Composable
private fun rememberPlan(): ProfileModel.Plan {
  val context = LocalContext.current
  val status = remember {
    runCatching { LicenseGuard.status(context, SyncStore(context)) }.getOrNull()
  }
  LaunchedEffect(Unit) { SubscriptionPulse.refresh(context) }

  return ProfileModel.plan(
    localActive = status?.state == License.State.ACTIVE,
    localDays = status?.daysLeft() ?: 0,
    localExpired = status?.state == License.State.EXPIRED || status?.state == License.State.GRACE,
    serverActive = SubscriptionPulse.active,
    serverDays = SubscriptionPulse.days,
    serverTrial = SubscriptionPulse.trial,
    warnDays = SUBSCRIPTION_WARN_DAYS,
  )
}

/**
 *  کادرِ خواندنی — چیزی که هست ولی عوض نمی‌شود: خاکستری و با قفل، تا کاربر
 *  ببیند «اینجا نوشته شده» و بفهمد «اینجا نوشته نمی‌شود».
 */
@Composable
private fun LockedRow(
  icon: ImageVector,
  label: String,
  value: String,
  note: String,
  ltr: Boolean,
) {
  val c = PhoneV3.colors
  Column {
    Row(
      Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(12.dp))
        .background(c.bg)
        .padding(horizontal = 14.dp, vertical = 11.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(icon, contentDescription = null, tint = c.inkMuted, modifier = Modifier.size(18.dp))
      Spacer(Modifier.width(10.dp))
      Column(Modifier.weight(1f)) {
        Text(label, style = v3Text(11, FontWeight.Medium, 15), color = c.inkMuted)
        CompositionLocalProvider(LocalLayoutDirection provides if (ltr) LayoutDirection.Ltr else LocalLayoutDirection.current) {
          Text(value, style = v3Text(14, FontWeight.Medium, 20), color = c.ink, textAlign = TextAlign.Start)
        }
      }
      Icon(Icons.Outlined.Lock, contentDescription = "قابل تغییر نیست", tint = c.inkMuted, modifier = Modifier.size(15.dp))
    }
    Spacer(Modifier.height(4.dp))
    Text(note, style = v3Text(11, FontWeight.Medium, 16), color = c.inkMuted)
  }
}

/** تاجِ کوچکِ نشانِ نقش (r4) — Material تاج ندارد؛ سه دندانه و پایه */
private val CrownIcon: ImageVector by lazy {
  ImageVector.Builder("Crown", 24.dp, 24.dp, 24f, 24f).apply {
    path(fill = androidx.compose.ui.graphics.SolidColor(Color.Black)) {
      moveTo(3f, 7.5f)
      lineTo(8f, 11.5f)
      lineTo(12f, 5f)
      lineTo(16f, 11.5f)
      lineTo(21f, 7.5f)
      lineTo(19.2f, 17f)
      lineTo(4.8f, 17f)
      close()
      moveTo(5f, 18.5f)
      lineTo(19f, 18.5f)
      lineTo(19f, 20.5f)
      lineTo(5f, 20.5f)
      close()
    }
  }.build()
}
