package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import ir.vil3ntec.tohid.ui.theme.PhoneSheet
import ir.vil3ntec.tohid.ui.theme.Shape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.data.PhotoStore
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.fa
import ir.vil3ntec.tohid.formatMillis
import ir.vil3ntec.tohid.spanText
import ir.vil3ntec.tohid.sync.License
import ir.vil3ntec.tohid.sync.LicenseGuard
import ir.vil3ntec.tohid.core.net.userText
import ir.vil3ntec.tohid.sync.SyncStore
import ir.vil3ntec.tohid.sync.v1.SyncV1Engine
import ir.vil3ntec.tohid.sync.v1.SyncV1Worker
import ir.vil3ntec.tohid.core.sync.LogoutGuard
import ir.vil3ntec.tohid.data.BackupMirror
import ir.vil3ntec.tohid.sync.AutoSync
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 *  پروفایلِ حساب — کیستم، از کِی، و تا کِی.
 *
 *  ── چه چیزی را می‌بندد ────────────────────────────────────────────
 *  تا امروز کلیدِ «حساب» در سربرگ، صفحهٔ **ورود** را باز می‌کرد — حتی
 *  برای کسی که سال‌ها وارد بوده. یعنی هیچ‌جای برنامه نمی‌شد دید که
 *  نامِ حساب چیست، با کدام ایمیل وارد شده‌ای، شماره‌ات ثبت شده یا نه،
 *  و از کِی عضوی. نامِ حساب فقط یک بار هنگام ثبت‌نام گرفته می‌شد و
 *  اگر غلط بود، غلط می‌ماند.
 *  ──────────────────────────────────────────────────────────────────
 *
 *  سه قاعده‌ی این صفحه:
 *
 *   • **نام عوض می‌شود.** اسمِ آدم شناسه‌ی حساب نیست.
 *
 *   • **ایمیل قفل است.** راهِ ورود و راهِ بازیابیِ رمز است؛ عوض شدنش
 *     یعنی حساب از دستِ صاحبش در می‌آید.
 *
 *   • **شماره یک بار ثبت می‌شود و بعد قفل.** کسی که با ایمیل آمده
 *     شماره ندارد و همین‌جا می‌تواند اضافه کند — یک بار. سرور هم
 *     همین را جدا می‌سنجد، نه فقط این صفحه.
 *
 *  بی‌اینترنت هم باز می‌شود: هرچه نشان می‌دهد از حافظهٔ گوشی می‌آید و
 *  اگر آنتن بود، همان لحظه از سرور تازه می‌شود.
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
  val colors = Shop.colors
  val state = remember { SyncStore(context) }
  val signedIn = !state.accessToken.isNullOrBlank()

  var name by remember { mutableStateOf(state.accountName) }
  var email by remember { mutableStateOf(state.accountEmail) }
  var phone by remember { mutableStateOf(state.accountPhone) }
  var createdAt by remember { mutableStateOf(state.accountCreatedAt) }
  var newPhone by remember { mutableStateOf("") }
  var busy by remember { mutableStateOf(false) }
  var syncing by remember { mutableStateOf(false) }
  var lastSyncAt by remember { mutableStateOf(state.lastSyncAt) }
  //  «آخرین همگام‌سازی» از موتورِ v1 می‌آید؛ `state.lastSyncAt` مالِ راهِ قدیم است
  val v1 by SyncV1Engine.of(context).status.collectAsState()
  var confirmSignOut by remember { mutableStateOf(false) }
  //  SYN-10: صفِ پُر ⇒ خروج بسته؛ «خروجِ اجباری» = پشتیبانِ دیدنی + دو تأیید
  var blockSignOut by remember { mutableStateOf(false) }
  var forcedBackup by remember { mutableStateOf<String?>(null) }

  /*
   *  نامِ دکان — همان‌جایی که تنظیمات هم از آن می‌خواند
   *  (`SharedPreferences("tohid")`، کلیدِ `store_name`).
   *
   *  چرا اینجا: قرارِ صاحب مخزن بود که نامِ فروشگاه کنارِ حساب بنشیند نه
   *  در تنظیمات. راست هم می‌گوید — نامِ دکان مالِ همین حساب است و روی
   *  سربرگِ فاکتورِ همین دکان چاپ می‌شود.
   */
  val prefs = remember { context.getSharedPreferences("tohid", android.content.Context.MODE_PRIVATE) }
  var shopName by remember { mutableStateOf(prefs.getString("store_name", "").orEmpty()) }

  //  عکسِ حساب — با هر تغییر این عدد بالا می‌رود تا نسخهٔ کش‌شده دور
  //  ریخته شود
  var avatarVersion by remember { mutableStateOf(0) }
  var pickOpen by remember { mutableStateOf(false) }

  fun toast(text: String) {
    scope.launch { snackbar.showSnackbar(text) }
  }

  /*
   *  تازه‌سازی از سرور — بی‌صدا.
   *
   *  شکستش پیام ندارد: کسی که آفلاین پروفایلش را باز می‌کند نباید
   *  خطای شبکه ببیند؛ همان چیزی که روی گوشی است نشان داده می‌شود.
   */
  LaunchedEffect(signedIn) {
    if (!signedIn || !Backend.isReady(context)) return@LaunchedEffect
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

  Column(
    Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(start = 16.dp, end = 16.dp, bottom = 28.dp)
      .imePadding(),
  ) {
    TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) {
      Icon(Icons.Filled.ArrowForward, contentDescription = null, tint = colors.primary)
      Spacer(Modifier.width(6.dp))
      Text("بازگشت", color = colors.primary)
    }

    /* --------------- عکس، نام، نقش، آمار و هشدار (تصویرِ مرجعِ ۳) --------------- */
    val ledger by store.data.collectAsState()
    //  وضعیتِ اشتراک فقط برای حسابِ واردشده خوانده می‌شود (همان پیشینِ `SubscriptionRow`)
    val plan = if (signedIn) rememberPlan() else null
    ProfileCard(
      avatar = {
        Box(contentAlignment = Alignment.BottomEnd) {
          Avatar(
            accountId = state.accountId.ifBlank { "local" },
            version = avatarVersion,
            modifier = Modifier.clickable(onClickLabel = "عوض کردنِ عکس حساب") { pickOpen = true },
          )
          Box(
            Modifier
              .size(30.dp)
              .clip(CircleShape)
              .background(colors.primary)
              .clickable { pickOpen = true },
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              Icons.Filled.PhotoCamera,
              contentDescription = "عکس حساب",
              tint = Color.White,
              modifier = Modifier.size(16.dp),
            )
          }
        }
      },
      name = name.ifBlank { "بی‌نام" },
      contact = phone.ifBlank { email },
      //  نقش فقط برای حسابِ واردشده معنا دارد (همان `ShopRole`ِ سدِ تنظیمات)
      role = if (signedIn) ProfileModel.roleLabel(ir.vil3ntec.tohid.data.ShopRole.current(context)) else null,
      //  عددها از دفترِ همین گوشی — همان سه عددِ «داده‌ها»ی تنظیمات
      stats = listOf(
        "کالا" to ledger.products.size,
        "فاکتور" to ledger.sales.size,
        "قرض‌دار" to ledger.debtors.size,
      ),
      notice = plan?.let { ProfileModel.notice(it, SUBSCRIPTION_WARN_DAYS) },
      onNotice = onSubscription,
    )

    /* ------------------------- وارد نشده ------------------------- */
    if (!signedIn) {
      Spacer(Modifier.height(14.dp))
      Panel {
        Text(
          "وارد حساب نشده‌اید",
          style = MaterialTheme.typography.titleSmall,
          color = colors.text,
          fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
          "برنامه بدونِ حساب هم کامل کار می‌کند و دفترِ دکان روی همین گوشی " +
            "می‌ماند. با حساب، اطلاعات روی سرور پشتیبان می‌گیرد و روی گوشیِ " +
            "دیگر هم می‌آید.",
          style = MaterialTheme.typography.bodySmall,
          color = colors.muted,
        )
        Spacer(Modifier.height(12.dp))
        Button(
          onClick = onSignIn,
          colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
          modifier = Modifier.fillMaxWidth(),
        ) { Text("ورود یا ساختنِ حساب", color = Color.White) }
      }
      return@Column
    }

    /* ------------------------ عضویت و اشتراک ------------------------ */
    Spacer(Modifier.height(20.dp))
    SettingsGroup("حساب و اشتراک") {
      plan?.let { SubscriptionRow(it, onClick = onSubscription) }
      MembershipRow(createdAt)
    }

    /* -------------------------- ویرایش -------------------------- */
    Spacer(Modifier.height(18.dp))
    SettingsGroup("ویرایش پروفایل", padded = true) {
      OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text("نام و نام خانوادگی") },
        leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null, tint = colors.muted) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
      )

      Spacer(Modifier.height(10.dp))

      /*
       *  ایمیل — دیده می‌شود، دست نمی‌خورد.
       *
       *  پنهان کردنش بدتر بود: کاربری که دو ایمیل دارد باید بتواند ببیند
       *  با کدام وارد شده. کادرِ خاکستری با قفل، خودش می‌گوید که خواندنی
       *  است.
       */
      LockedRow(
        icon = Icons.Filled.AlternateEmail,
        label = "ایمیل",
        value = email.ifBlank { "ثبت نشده" },
        note = if (email.isBlank()) "شما با شماره وارد شده‌اید"
        else "ایمیلِ حساب عوض نمی‌شود — راهِ ورود و بازیابیِ رمز همین است",
        ltr = email.isNotBlank(),
      )

      Spacer(Modifier.height(10.dp))

      if (phone.isBlank()) {
        /*
         *  شماره ندارد — یک بار می‌شود ثبتش کرد.
         *
         *  چرا مهم است: بازیابیِ رمز و خبرهای دکان از راهِ شماره می‌روند،
         *  و کسی که با ایمیل آمده هیچ‌کدام را ندارد.
         */
        OutlinedTextField(
          value = newPhone,
          onValueChange = { newPhone = it },
          label = { Text("شماره موبایل — یک بار ثبت می‌شود") },
          placeholder = { Text("07XXXXXXXX") },
          singleLine = true,
          keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Phone),
          modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        Text(
          "شماره پس از ثبت قفل می‌شود و از برنامه عوض نمی‌شود.",
          style = MaterialTheme.typography.labelSmall,
          color = colors.warning,
        )
      } else {
        LockedRow(
          icon = Icons.Filled.Lock,
          label = "شماره موبایل",
          value = phone,
          note = "شماره ثبت شده و قفل است",
          ltr = true,
        )
      }

      Spacer(Modifier.height(16.dp))

      Button(
        onClick = {
          val cleanName = name.trim()
          if (cleanName.isBlank()) {
            toast("نام را بنویسید")
            return@Button
          }
          //  نامِ دکان همین‌جا و همان لحظه می‌نشیند؛ روی گوشی است و
          //  اینترنت نمی‌خواهد
          prefs.edit().putString("store_name", shopName.trim()).apply()
          val claim = newPhone.trim()
          busy = true
          scope.launch {
            //  نام روی گوشی همان لحظه می‌نشیند: برنامه آفلاین هم کار
            //  می‌کند و همین نام است که در سربرگ و رسیدها دیده می‌شود
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
        enabled = !busy,
        colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(if (busy) "در حال ذخیره…" else "ذخیره تغییرات", color = Color.White)
      }
    }

    /* ------------------------- دکان ------------------------- */
    Spacer(Modifier.height(20.dp))
    SettingsGroup("دکان", padded = true) {
      OutlinedTextField(
        value = shopName,
        onValueChange = {
          shopName = it
          prefs.edit().putString("store_name", it.trim()).apply()
        },
        label = { Text("نام فروشگاه") },
        leadingIcon = { Icon(Icons.Filled.Storefront, contentDescription = null, tint = colors.muted) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
      )
      Spacer(Modifier.height(4.dp))
      Text(
        "این نام روی سربرگ فاکتور چاپ می‌شود.",
        style = MaterialTheme.typography.labelSmall,
        color = colors.muted2,
      )
    }

    /* ------------------------- همگام‌سازی ------------------------- */
    Spacer(Modifier.height(20.dp))
    SettingsGroup("همگام‌سازی") {
      InfoRow(
        icon = Icons.Filled.Sync,
        tint = colors.accent,
        title = "آخرین همگام‌سازی",
        value = maxOf(lastSyncAt, v1.lastOkAt).let { if (it > 0) formatMillis(it) else "هنوز انجام نشده" },
        detail = "دفترِ دکان روی سرور پشتیبان می‌گیرد و روی گوشیِ دیگر هم می‌آید",
      )
      Column(Modifier.fillMaxWidth().background(colors.surfaceSolid).padding(16.dp)) {
        Button(
          onClick = {
            /*
             *  ⛔ F0-02: این دکمه دورِ Sync v1 را می‌اندازد، نه راهِ قدیم.
             *
             *  تا این‌جا `Syncer.run` صدا زده می‌شد: push/pullِ قدیمی
             *  (`/api/v1/shop/sync`) که مستقیم در دفتر ادغام می‌کرد و سایهٔ v1
             *  را جلو نمی‌برد؛ v1 همان را «تغییرِ محلی» می‌دید و دوباره
             *  می‌فرستاد — فیلدهای `$inc` (پرداخت‌ها، مانده‌ها) دو بار جمع
             *  می‌شدند. `AutoSync` از قبل همین کار را می‌کرد؛ فقط این دکمه
             *  جا مانده بود. کلاسِ `Syncer` حذف نمی‌شود: `AutoSync` از
             *  `refreshLicense`ِ آن استفاده می‌کند.
             *  سنجه: `sync/LegacySyncCallTest.kt`.
             */
            runCatching { SyncV1Worker.now(context) }
              .onSuccess { toast("همگام‌سازی شروع شد؛ چند ثانیه بعد «آخرین همگام‌سازی» تازه می‌شود") }
              .onFailure { toast(it.userText("همگام‌سازی ناموفق بود")) }
          },
          enabled = !syncing,
          colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(if (syncing) "در حال همگام‌سازی…" else "همگام‌سازی حالا", color = Color.White)
        }
      }
    }

    /*
     *  خروج از حساب — ته صفحه، و با یک پرسش.
     *
     *  گزارشِ صاحب مخزن: این صفحه اصلاً خروج نداشت و برای بیرون آمدن
     *  باید سراغِ تنظیمات می‌رفت. حالا همین‌جاست، ولی نه چسبیده به
     *  «ذخیره تغییرات»: خروجِ ناخواسته یعنی دوباره وارد شدن و دوباره
     *  همگام‌سازی.
     */
    Spacer(Modifier.height(22.dp))
    OutlinedButton(
      onClick = {
        when (LogoutGuard.decide(v1.queued, AutoSync.pendingCount)) {
          LogoutGuard.Allow -> confirmSignOut = true
          is LogoutGuard.Block -> blockSignOut = true
        }
      },
      colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.danger),
      border = androidx.compose.foundation.BorderStroke(1.dp, colors.danger.copy(alpha = 0.6f)),
      modifier = Modifier.fillMaxWidth(),
    ) { Text("خروج از حساب") }
    Spacer(Modifier.height(6.dp))
    Text(
      "دفترِ دکان روی همین گوشی می‌ماند و پاک نمی‌شود.",
      style = MaterialTheme.typography.labelSmall,
      color = colors.muted2,
      textAlign = TextAlign.Center,
      modifier = Modifier.fillMaxWidth(),
    )
  }

  fun signOutNow() {
    busy = true
    scope.launch {
      runCatching { Backend.auth(context).signOut() }
      state.signOut()
      busy = false
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
           *  خروج، واقعاً.
           *
           *  ── چه چیزی خراب بود ────────────────────────────────────
           *  اینجا فقط `state.signOut()` بود، یعنی پاک کردنِ توکن از
           *  گوشی. سرور هیچ خبری نمی‌شد و همان نشست تا نود روز روی
           *  سرور **باز** می‌ماند: هر کسی که توکن را از گوشی — یا از
           *  پشتیبانِ گوشی — برمی‌داشت، هنوز واردِ حساب بود.
           *
           *  حالا اول به سرور می‌گوییم نشست را باطل کند و بعد گوشی را
           *  پاک می‌کنیم. ترتیب عمدی است: `SyncStore.signOut` توکن را
           *  پاک می‌کند و بعد از آن دیگر چیزی برای فرستادن نمی‌ماند.
           *
           *  نتیجهٔ سرور جلوی خروج را نمی‌گیرد: کسی که «خروج» زده باید
           *  خارج شود، حتی اگر نت نباشد. آن‌وقت توکن روی گوشی رفته و
           *  نشستِ سرور با گذشتِ مهلتش می‌میرد — که بدتر از آن است که
           *  کاربر را در حسابی نگه داریم که خواسته از آن بیرون بیاید.
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

/* ============================ تکه‌ها ============================ */

/**
 *  کارتِ بالای «حساب من» (phone-ui، تصویرِ مرجعِ ۳): عکسِ گرد روی لبهٔ کارت،
 *  نام و راهِ تماس، نشانِ نقش، سه تراشهٔ آمار از دفترِ واقعی، و نوارِ هشدارِ
 *  اشتراک **فقط وقتی واقعی است** (`ProfileModel.notice`). سایهٔ نرم، بی خطِ دور.
 */
@Composable
private fun ProfileCard(
  avatar: @Composable () -> Unit,
  name: String,
  contact: String,
  role: String?,
  stats: List<Pair<String, Int>>,
  notice: ProfileModel.Notice?,
  onNotice: () -> Unit,
) {
  val colors = Shop.colors
  val sheet = PhoneSheet.colors
  Box(Modifier.fillMaxWidth().padding(top = 4.dp)) {
    Column(
      Modifier
        .fillMaxWidth()
        .padding(top = 48.dp)
        .shadow(8.dp, Shape.card, clip = false, ambientColor = colors.text.copy(alpha = 0.10f), spotColor = colors.text.copy(alpha = 0.10f))
        .clip(Shape.card)
        .background(colors.surfaceSolid)
        .padding(start = 16.dp, end = 16.dp, top = 60.dp, bottom = 16.dp),
    ) {
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
          Text(
            name,
            style = MaterialTheme.typography.titleLarge,
            color = colors.text,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          if (contact.isNotBlank()) {
            //  شماره و ایمیل چپ‌به‌راست خوانده می‌شوند، وگرنه `+93` ته خط می‌رود
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
              Text(contact, style = MaterialTheme.typography.bodySmall, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
          }
        }
        if (role != null) {
          Spacer(Modifier.width(8.dp))
          Text(
            role,
            style = MaterialTheme.typography.labelMedium,
            color = sheet.onNavPill,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
              .clip(RoundedCornerShape(percent = 50))
              .background(sheet.navPill)
              .padding(horizontal = 12.dp, vertical = 6.dp),
          )
        }
      }
      Spacer(Modifier.height(14.dp))
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        stats.forEach { (label, count) ->
          Column(
            Modifier
              .weight(1f)
              .clip(RoundedCornerShape(12.dp))
              .background(colors.bg)
              .padding(vertical = 10.dp)
              .semantics(mergeDescendants = true) {},
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Text(ir.vil3ntec.tohid.plain(count), style = MaterialTheme.typography.titleMedium, color = colors.text, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = colors.muted)
          }
        }
      }
      if (notice != null) {
        Spacer(Modifier.height(12.dp))
        Row(
          Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(sheet.sheet)
            .padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(Icons.Filled.HourglassBottom, contentDescription = null, tint = sheet.accent, modifier = Modifier.size(20.dp))
          Spacer(Modifier.width(10.dp))
          Column(Modifier.weight(1f)) {
            Text(
              when (notice) {
                ProfileModel.Notice.Expired -> "اشتراک تمام شده"
                is ProfileModel.Notice.Ending ->
                  if (notice.days > 0) "${if (notice.trial) "آزمایشی" else "اشتراک"} — ${notice.days.fa()} روز مانده"
                  else "امروز آخرین روزِ اشتراک است"
              },
              style = MaterialTheme.typography.labelLarge,
              color = sheet.onSheet,
              fontWeight = FontWeight.Bold,
            )
            Text(
              "هیچ داده‌ای پاک نمی‌شود؛ خروجی و چاپ باز می‌ماند.",
              style = MaterialTheme.typography.labelSmall,
              color = sheet.onSheetMuted,
            )
          }
          Spacer(Modifier.width(8.dp))
          Text(
            if (notice is ProfileModel.Notice.Ending && notice.trial) "پلن‌ها" else "تمدید",
            style = MaterialTheme.typography.labelLarge,
            color = sheet.onAccent,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
              .heightIn(min = 40.dp)
              .clip(RoundedCornerShape(percent = 50))
              .background(sheet.accent)
              .clickable(role = Role.Button, onClick = onNotice)
              .padding(horizontal = 16.dp, vertical = 10.dp),
          )
        }
      }
    }
    Box(Modifier.align(Alignment.TopCenter)) { avatar() }
  }
}

/** عکسِ حساب، یا حرفِ اولِ نام وقتی عکسی نیست */
@Composable
private fun Avatar(accountId: String, version: Int, modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val colors = Shop.colors
  var bitmap by remember(accountId, version) {
    mutableStateOf<android.graphics.Bitmap?>(null)
  }

  //  خواندن از دیسک روی نخِ پس‌زمینه — عکس هرچه کوچک، خواندنش روی نخِ
  //  اصلی یک لحظه صفحه را می‌خواباند
  LaunchedEffect(accountId, version) {
    bitmap = withContext(Dispatchers.IO) { PhotoStore.loadAvatar(context, accountId) }
  }

  Box(
    modifier
      .size(96.dp)
      .clip(CircleShape)
      .background(colors.primaryTint)
      .border(2.dp, colors.primary.copy(alpha = 0.5f), CircleShape),
    contentAlignment = Alignment.Center,
  ) {
    val image = bitmap
    if (image != null) {
      Image(
        bitmap = image.asImageBitmap(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.size(96.dp),
      )
    } else {
      Icon(
        Icons.Filled.Person,
        contentDescription = null,
        tint = colors.primary,
        modifier = Modifier.size(46.dp),
      )
    }
  }
}

/** «از کِی عضوی» — مدت، و تاریخِ دقیقش کنارش */
@Composable
private fun MembershipRow(createdAt: Long) {
  val colors = Shop.colors
  InfoRow(
    icon = Icons.Filled.Schedule,
    tint = colors.accent,
    title = "مدت عضویت",
    value = if (createdAt > 0) spanText(createdAt) else "—",
    detail = if (createdAt > 0) "عضو از ${formatMillis(createdAt)}" else "روزِ ساختنِ حساب معلوم نیست",
  )
}

/**
 *  وضعیتِ اشتراکِ همین حساب — مجوزِ امضاشدهٔ روی گوشی، وگرنه حرفِ سرور.
 *  تصمیم در `ProfileModel.plan` است (خالص و آزمون‌شده)؛ ردیفِ «اشتراک» و
 *  نوارِ هشدارِ کارتِ بالا هر دو از همین می‌خوانند.
 */
@Composable
private fun rememberPlan(): ProfileModel.Plan {
  val context = LocalContext.current
  val status = remember {
    runCatching { LicenseGuard.status(context, SyncStore(context)) }.getOrNull()
  }
  /*
   *  و اگر مجوزی روی گوشی نیست، از خودِ سرور بپرس.
   *
   *  ── چه چیزی را می‌بندد ────────────────────────────────────────────
   *  گزارش شد: «حساب ساختم و برنامه نمی‌گوید هفت روز آزمایشی دارم» — و
   *  «بدون اشتراک» نوشته بود. علتش این بود که این ردیف فقط مجوزِ امضاشدهٔ
   *  روی گوشی را می‌خواند. آن مجوز با همگام‌سازیِ موفق می‌آید، و تا آن
   *  لحظه — یا اگر همگام‌سازی گیر کرده باشد — کاربر «بدون اشتراک» می‌دید،
   *  در حالی که سرور دورهٔ آزمایشی‌اش را از همان دقیقهٔ اول باز کرده بود.
   *
   *  حالا وضعیت از سرور هم پرسیده می‌شود و اگر سرور بگوید «آزمایشی، N
   *  روز مانده»، همان نوشته می‌شود. حرفِ سرور مقدم است چون صاحبِ اشتراک
   *  اوست، نه گوشی.
   *
   *  و همان پاسخ، از همان کَشی خوانده می‌شود که سربرگ می‌خواند
   *  (`SubscriptionPulse`) — وگرنه باز دو جا دو حرف می‌زدند: همان چیزی
   *  که باعث شد سربرگ آبی بماند و همین ردیف «۷ روز مانده» بنویسد.
   *  ──────────────────────────────────────────────────────────────────
   */
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
 *  وضعیتِ اشتراک، در همین صفحه.
 *
 *  از یک هفته به پایین قرمز می‌شود — همان مرزی که نشانِ سربرگ و کارتِ
 *  صفحهٔ اشتراک با آن قرمز می‌شوند (`SUBSCRIPTION_WARN_DAYS`).
 */
@Composable
private fun SubscriptionRow(plan: ProfileModel.Plan, onClick: () -> Unit) {
  val colors = Shop.colors
  val active = plan.active
  val expired = plan.expired
  val trial = plan.trial
  val days = plan.days
  val urgent = plan.urgent

  InfoRow(
    icon = if (urgent) Icons.Filled.HourglassBottom
    else if (active) Icons.Filled.CheckCircle else Icons.Filled.WorkspacePremium,
    tint = when {
      urgent -> colors.danger
      active -> colors.success
      else -> colors.warning
    },
    title = "اشتراک",
    value = when {
      expired -> "تمام شده"
      trial && days > 0 -> "آزمایشی — ${days.fa()} روز مانده"
      active && days > 0 -> "${days.fa()} روز مانده"
      active -> "امروز آخرین روز"
      else -> "بدون اشتراک"
    },
    detail = when {
      trial -> "دورهٔ آزمایشی؛ برای دیدنِ پلن‌ها بزنید"
      active || expired -> "برای تمدید بزنید"
      else -> "برای دیدنِ پلن‌ها بزنید"
    },
    onClick = onClick,
  )
}

/** یک ردیفِ اطلاعات با آیکنِ رنگی — کارتی، نه فهرستی */
@Composable
private fun InfoRow(
  icon: ImageVector,
  tint: Color,
  title: String,
  value: String,
  detail: String,
  onClick: (() -> Unit)? = null,
) {
  val colors = Shop.colors
  //  phone-ui: ردیفِ یک کارتِ گروه (`SettingsGroup`) — گوشه و خطِ دور مالِ کارت است
  Row(
    Modifier
      .fillMaxWidth()
      .background(colors.surfaceSolid)
      .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
      .padding(horizontal = 14.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(tint.copy(alpha = 0.16f)),
      contentAlignment = Alignment.Center,
    ) {
      Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
    }
    Spacer(Modifier.width(12.dp))
    Column(Modifier.weight(1f)) {
      Text(title, style = MaterialTheme.typography.labelMedium, color = colors.muted)
      Text(value, style = MaterialTheme.typography.bodyMedium, color = tint, fontWeight = FontWeight.Bold)
      Text(detail, style = MaterialTheme.typography.labelSmall, color = colors.muted2)
    }
    if (onClick != null) {
      Icon(
        Icons.Filled.ChevronLeft,
        contentDescription = null,
        tint = colors.muted2,
        modifier = Modifier.size(20.dp),
      )
    }
  }
}

/**
 *  کادرِ خواندنی — چیزی که هست ولی عوض نمی‌شود.
 *
 *  شکلش عمداً شبیهِ کادرِ ورودی است ولی خاکستری و با قفل: کاربر باید
 *  ببیند که «اینجا نوشته شده» و بفهمد که «اینجا نوشته نمی‌شود». کادرِ
 *  ورودیِ غیرفعالِ متریال همین را می‌گفت ولی زدنش صفحه‌کلید را باز
 *  می‌کرد و کاربر فکر می‌کرد خراب است.
 */
@Composable
private fun LockedRow(
  icon: ImageVector,
  label: String,
  value: String,
  note: String,
  ltr: Boolean,
) {
  val colors = Shop.colors
  Column {
    Row(
      Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(12.dp))
        .background(colors.surface2)
        .border(1.dp, colors.border, RoundedCornerShape(12.dp))
        .padding(horizontal = 14.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(icon, contentDescription = null, tint = colors.muted2, modifier = Modifier.size(18.dp))
      Spacer(Modifier.width(10.dp))
      Column(Modifier.weight(1f)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.muted2)
        if (ltr) {
          CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Text(value, style = MaterialTheme.typography.bodyMedium, color = colors.text)
          }
        } else {
          Text(value, style = MaterialTheme.typography.bodyMedium, color = colors.text)
        }
      }
      Icon(
        Icons.Filled.Lock,
        contentDescription = "قابل تغییر نیست",
        tint = colors.muted2,
        modifier = Modifier.size(15.dp),
      )
    }
    Spacer(Modifier.height(4.dp))
    Text(note, style = MaterialTheme.typography.labelSmall, color = colors.muted2)
  }
}
