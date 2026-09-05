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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
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
import ir.vil3ntec.tohid.sync.Syncer
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.unit.sp
import ir.vil3ntec.tohid.ui.theme.Shape
import ir.vil3ntec.tohid.ui.theme.Radius
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
  onSettings: () -> Unit = {},
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
  var confirmSignOut by remember { mutableStateOf(false) }

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
    /*
     *  یک راهِ برگشت، نه دو تا.
     *
     *  لینکِ «بازگشت» زیرِ سربرگِ برنامه تکراری بود — پیکانِ سربرگ همان
     *  کار را می‌کرد. جایش سربرگِ خودِ صفحه است: پیکان، نام، و
     *  چرخ‌دنده‌ی تنظیمات که از نوارِ بالا به اینجا آمد.
     */
    Row(
      Modifier.fillMaxWidth().padding(vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        Icons.Filled.ChevronRight,
        contentDescription = "بازگشت",
        tint = colors.muted,
        modifier = Modifier.size(22.dp).clickable(onClick = onBack),
      )
      Spacer(Modifier.width(6.dp))
      Text(
        "حساب من",
        fontSize = 17.sp,
        fontWeight = FontWeight.Bold,
        color = colors.text,
        modifier = Modifier.weight(1f),
      )
      Icon(
        Icons.Filled.Settings,
        contentDescription = "تنظیمات",
        tint = colors.muted,
        modifier = Modifier.size(20.dp).clickable(onClick = onSettings),
      )
    }

    /* ------------------------- عکس و نام ------------------------- */
    Column(
      Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(Radius.md))
        .background(colors.surface)
        .padding(vertical = 20.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Box(contentAlignment = Alignment.BottomEnd) {
        Avatar(
          accountId = state.accountId.ifBlank { "local" },
          version = avatarVersion,
          modifier = Modifier.clickable { pickOpen = true },
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
      Spacer(Modifier.height(12.dp))
      Text(
        name.ifBlank { "بی‌نام" },
        style = MaterialTheme.typography.titleLarge,
        color = colors.text,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
      )
      val contact = phone.ifBlank { email }
      if (contact.isNotBlank()) {
        Spacer(Modifier.height(2.dp))
        //  شماره و ایمیل چپ‌به‌راست خوانده می‌شوند، وگرنه `+93` ته خط می‌رود
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
          Text(contact, style = MaterialTheme.typography.bodySmall, color = colors.muted)
        }
      }
    }

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
    Spacer(Modifier.height(14.dp))
    //  ردیفِ شکسته نشان داده نمی‌شود: اگر روزِ ساختنِ حساب معلوم نیست،
    //  «— معلوم نیست» چیزی به کسی نمی‌گوید، فقط یک خطِ خالی است
    if (createdAt > 0) {
      MembershipRow(createdAt)
      Spacer(Modifier.height(8.dp))
    }
    SubscriptionRow(onClick = onSubscription)

    /* -------------------------- ویرایش -------------------------- */
    Spacer(Modifier.height(18.dp))
    SectionTitle("ویرایش پروفایل")

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
        "ایمیل و شماره پس از ثبت قفل می‌شوند — راه ورود و بازیابی رمز همین‌هاست.",
        fontSize = 10.5.sp,
        color = colors.muted2,
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

    /* ------------------------- دکان ------------------------- */
    Spacer(Modifier.height(20.dp))
    SectionTitle("دکان")
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

    /* ------------------------- همگام‌سازی ------------------------- */
    Spacer(Modifier.height(20.dp))
    SectionTitle("همگام‌سازی")
    /*
     *  یک ردیف، نه یک دکمه‌ی رقیبِ «ذخیره تغییرات».
     *
     *  دو دکمه‌ی هم‌شکل و هم‌رنگ در یک صفحه یعنی کاربر باید متنشان را
     *  بخواند تا بفهمد کدام کارِ اوست. همگام‌سازی یک کارِ گاه‌به‌گاه است،
     *  پس ردیف است نه دکمه.
     */
    Row(
      Modifier
        .fillMaxWidth()
        .clip(Shape.card)
        .background(colors.surface)
        .border(1.dp, colors.border, Shape.card)
        .clickable(enabled = !syncing) {
          syncing = true
          scope.launch {
            runCatching { Syncer(store, state, context).run() }
              .onSuccess {
                lastSyncAt = state.lastSyncAt
                toast("همگام‌سازی شد — ${it.pushed.fa()} فرستاده، ${it.pulled.fa()} گرفته")
              }
              .onFailure { toast(it.userText("همگام‌سازی ناموفق بود")) }
            syncing = false
          }
        }
        .padding(horizontal = 14.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        Modifier.size(36.dp).clip(RoundedCornerShape(13.dp)).background(colors.primaryTint),
        contentAlignment = Alignment.Center,
      ) {
        Icon(Icons.Filled.Sync, contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
      }
      Spacer(Modifier.width(12.dp))
      Column(Modifier.weight(1f)) {
        Text(
          if (syncing) "در حال همگام‌سازی…" else "همگام‌سازی حالا",
          fontSize = 13.sp,
          fontWeight = FontWeight.Bold,
          color = colors.text,
        )
        Text(
          if (lastSyncAt > 0) "آخرین بار: ${formatMillis(lastSyncAt)}" else "هنوز انجام نشده",
          fontSize = 10.5.sp,
          color = colors.muted2,
        )
      }
      Icon(
        Icons.Filled.ChevronLeft,
        contentDescription = null,
        tint = colors.muted2,
        modifier = Modifier.size(18.dp),
      )
    }
    Spacer(Modifier.height(4.dp))
    Text(
      "دفترِ دکان روی سرور پشتیبان می‌گیرد و روی گوشی دیگر هم می‌آید.",
      fontSize = 10.5.sp,
      color = colors.muted2,
    )

    /*
     *  خروج از حساب — ته صفحه، و با یک پرسش.
     *
     *  گزارشِ صاحب مخزن: این صفحه اصلاً خروج نداشت و برای بیرون آمدن
     *  باید سراغِ تنظیمات می‌رفت. حالا همین‌جاست، ولی نه چسبیده به
     *  «ذخیره تغییرات»: خروجِ ناخواسته یعنی دوباره وارد شدن و دوباره
     *  همگام‌سازی.
     */
    Spacer(Modifier.height(22.dp))
    Text(
      "خروج از حساب",
      fontSize = 14.sp,
      color = colors.danger,
      textAlign = TextAlign.Center,
      modifier = Modifier
        .fillMaxWidth()
        .clickable { confirmSignOut = true }
        .padding(vertical = 10.dp),
    )
    Spacer(Modifier.height(6.dp))
    Text(
      "دفترِ دکان روی همین گوشی می‌ماند و پاک نمی‌شود.",
      style = MaterialTheme.typography.labelSmall,
      color = colors.muted2,
      textAlign = TextAlign.Center,
      modifier = Modifier.fillMaxWidth(),
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
        TextButton(onClick = {
          confirmSignOut = false
          state.signOut()
          toast("از حساب خارج شدید")
          onBack()
        }) { Text("خروج", color = colors.danger) }
      },
      dismissButton = {
        TextButton(onClick = { confirmSignOut = false }) { Text("ماندن", color = colors.muted) }
      },
    )
  }
}

/* ============================ تکه‌ها ============================ */

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

  //  مربعِ نرمِ گرادینتی، نه دایره‌ی خاکستری: همان شکلی که آواتارِ
  //  داشبورد و ردیف‌های قرض‌داران دارند
  val shape = RoundedCornerShape(30.dp)
  Box(
    modifier
      .size(88.dp)
      .clip(shape)
      .background(Brush.linearGradient(listOf(colors.primary, colors.accent))),
    contentAlignment = Alignment.Center,
  ) {
    val image = bitmap
    if (image != null) {
      Image(
        bitmap = image.asImageBitmap(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.size(88.dp),
      )
    } else {
      Icon(
        Icons.Filled.Person,
        contentDescription = null,
        tint = Color.White,
        modifier = Modifier.size(42.dp),
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
 *  وضعیتِ اشتراک، در همین صفحه.
 *
 *  از یک هفته به پایین قرمز می‌شود — همان مرزی که نشانِ سربرگ و کارتِ
 *  صفحهٔ اشتراک با آن قرمز می‌شوند (`SUBSCRIPTION_WARN_DAYS`).
 */
@Composable
private fun SubscriptionRow(onClick: () -> Unit) {
  val context = LocalContext.current
  val colors = Shop.colors
  val status = remember {
    runCatching { LicenseGuard.status(context, SyncStore(context)) }.getOrNull()
  }
  val localDays = status?.daysLeft() ?: 0
  val expired = status?.state == License.State.EXPIRED || status?.state == License.State.GRACE
  val localActive = status?.state == License.State.ACTIVE

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

  val serverOn = SubscriptionPulse.active && SubscriptionPulse.days > 0
  val serverTrialOn = serverOn && SubscriptionPulse.trial

  val active = localActive || serverOn
  val days = when {
    localActive -> localDays
    serverOn -> SubscriptionPulse.days
    else -> 0
  }
  val trial = !localActive && serverTrialOn
  val urgent = expired || (active && days <= SUBSCRIPTION_WARN_DAYS)

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
  Row(
    Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(Radius.sm))
      .background(colors.surface)
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
