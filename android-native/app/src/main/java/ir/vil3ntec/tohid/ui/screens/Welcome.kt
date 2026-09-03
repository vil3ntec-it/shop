package ir.vil3ntec.tohid.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import ir.vil3ntec.tohid.core.config.AppConfig
import ir.vil3ntec.tohid.core.model.SessionDto
import ir.vil3ntec.tohid.core.net.userText
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.data.StaffCode
import ir.vil3ntec.tohid.data.LedgerOwner
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.sync.SavedLogins
import ir.vil3ntec.tohid.sync.SyncStore
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.launch

/**
 *  صفحهٔ ورود.
 *
 *  دو راه دارد و عمداً از هم جدا شده‌اند:
 *
 *   • **شماره** — رمز ندارد، کدِ شش‌رقمی دارد. کسی که شمارهٔ خودش را دارد
 *     پیامکِ کد را می‌گیرد و همان اثباتِ اوست؛ رمزِ اضافه فقط یک چیزِ
 *     دیگر است که فروشنده فراموشش می‌کند. اگر آن شماره حساب نداشته
 *     باشد، سرور همان‌جا با همان نام می‌سازدش — پس ورود و ثبت‌نام با
 *     شماره یک راه‌اند، نه دو تا.
 *
 *   • **ایمیل** — رمز دارد. ایمیل کدِ خودکار ندارد و بدونِ رمز هر کسی که
 *     نشانی را بداند می‌تواند وارد شود.
 *
 *  نام در هر دو راه پرسیده می‌شود؛ حساب بی‌نام در فهرستِ شاگردهای دکان
 *  فقط یک شماره است.
 *
 *  و ورود همچنان اختیاری است: «ادامه بدون حساب» همیشه سرِ جایش هست و
 *  تمامِ برنامه بدونِ حساب روی همین گوشی کار می‌کند.
 */
/*
 *  رنگ‌های صفحهٔ ورود.
 *
 *  از تم نمی‌آیند و عمداً: این صفحه یک فضای روشن و آبیِ ثابت دارد، مثلِ
 *  صفحهٔ ورودِ برنامه‌های بانکی. رنگِ تمِ برنامه بعد از ورود شروع می‌شود.
 *  در تمِ تاریک، خودِ پس‌زمینه تیره کشیده می‌شود و این‌ها روی آن می‌نشینند.
 */
private val INK_LIGHT = Color(0xFF17255A)
private val INK_SOFT_LIGHT = Color(0xFF5C6B90)
private val BLUE = Color(0xFF2563C9)
private val BLUE_DEEP = Color(0xFF1B4FA8)
private val FIELD_LINE_LIGHT = Color(0xFFE1E8F5)

/*
 *  متنِ روی پس‌زمینه — نه روی کادرها.
 *
 *  اولش این‌ها رنگِ ثابت بودند و در تمِ تاریک همان سرمه‌ایِ تیره روی
 *  زمینهٔ تیره می‌نشستند: عنوان و «یا» و «ادامه بدون حساب» تقریباً خوانده
 *  نمی‌شدند. داخلِ کادرها مشکلی نبود چون خودِ کادر سفید است — بیرونشان
 *  بود که می‌سوخت. حالا از تم می‌آیند.
 */
private val ink: Color
  @Composable get() = if (isDarkSurface()) Color.White else INK_LIGHT

private val inkSoft: Color
  @Composable get() = if (isDarkSurface()) Shop.colors.muted else INK_SOFT_LIGHT

private val fieldLine: Color
  @Composable get() = if (isDarkSurface()) Shop.colors.fieldBorder else FIELD_LINE_LIGHT

@Composable
private fun isDarkSurface(): Boolean {
  val bg = Shop.colors.bg
  return (0.299f * bg.red + 0.587f * bg.green + 0.114f * bg.blue) < 0.5f
}

/** طلای همان صفحهٔ اشتراک — تا ورود و اشتراک یک زبان داشته باشند */
private val GOLD_GLOW = Color(0xFFF6C93F)
private val GOLD_RING = Color(0xFFFFE9A8)

@Composable
fun WelcomeScreen(store: ShopStore, onDone: () -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val state = remember { SyncStore(context) }

  // phone | email
  // ایمیل: ورود یا ساختِ حساب
  var emailMode by rememberSaveable { mutableStateOf("login") }

  //  کشوی پایین فقط «کد شاگرد» است و بسته می‌آید؛ چیزی برای تنظیم
  //  کردن نمانده که خودش باز شود
  var showMore by rememberSaveable { mutableStateOf(false) }

  //  صفحه هیچ‌وقت خودش کارگزارِ شبکه نمی‌سازد
  val auth = remember(context) { Backend.auth(context) }
  val shops = remember(context) { Backend.shop(context) }

  var name by rememberSaveable { mutableStateOf("") }
  var phone by rememberSaveable { mutableStateOf("") }
  var code by rememberSaveable { mutableStateOf("") }
  var codeSent by rememberSaveable { mutableStateOf(false) }

  var email by rememberSaveable { mutableStateOf("") }
  var password by rememberSaveable { mutableStateOf("") }
  //  تکرارِ رمز — شرحش سرِ خودِ کادر
  var password2 by rememberSaveable { mutableStateOf("") }
  var showPassword by rememberSaveable { mutableStateOf(false) }

  var staffCode by rememberSaveable { mutableStateOf("") }
  var busy by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }
  var note by remember { mutableStateOf<String?>(null) }
  var saved by remember { mutableStateOf(SavedLogins.read(context)) }

  /*
   *  فاصلهٔ ارسال دوباره — روی گوشی نوشته می‌شود، نه در حافظهٔ صفحه.
   *
   *  هر پیامک پول دارد. قبلاً شمارش با بستنِ صفحه یا برنامه از صفر شروع
   *  می‌شد و کاربرِ عجول چند پیامک پشتِ هم می‌گرفت.
   */
  val cooldown = remember { ir.vil3ntec.tohid.sync.OtpCooldown(context) }

  /*
   *  بازیابیِ رمزِ فراموش‌شده.
   *
   *  تا امروز دکمه‌اش فقط می‌گفت «با پشتیبانی تماس بگیرید» — یعنی کسی
   *  که رمزش را فراموش می‌کرد عملاً از حسابش بیرون می‌ماند. حالا کد به
   *  همان ایمیل می‌رود و همان صفحهٔ کد باز می‌شود.
   */
  var resetting by rememberSaveable { mutableStateOf(false) }

  /*
   *  ورود با گوگل فقط وقتی نشان داده می‌شود که خودِ سرور بگوید روشن است.
   *
   *  دکمه‌ای که بخورد به خطا، از نبودنِ دکمه بدتر است. آدرسِ سرور هم مدام
   *  در حالِ تایپ شدن است، پس کمی صبر می‌کنیم تا دست از تایپ بردارد و بعد
   *  یک بار می‌پرسیم.
   */
  var googleId by remember { mutableStateOf("") }
  //  آیا سرور می‌تواند کد بفرستد — شرحش سرِ کلیدِ «ورود با کد»
  var otpReady by remember { mutableStateOf(false) }
  LaunchedEffect(Unit) {
    if (!AppConfig.isConfigured(context)) { googleId = ""; return@LaunchedEffect }
    googleId = auth.googleClientId()
    //  همان یک درخواست، دو جواب: کلیدِ گوگل و اینکه کد فرستادنی است
    otpReady = auth.otpEnabled()
  }

  fun fail(e: Throwable) {
    error = e.userText("ارتباط با سرور برقرار نشد")
  }

  /**
   *  ورود انجام شد.
   *
   *  توکن‌ها را مخزن ذخیره کرده؛ اینجا فقط چیزهای نمایشی می‌ماند.
   */
  fun finish(identifier: String, session: SessionDto) {
    val display = session.user.name.ifBlank { name.trim() }
    SavedLogins.remember(context, identifier, display)
    //  نقش را همین‌جا می‌دانیم؛ صبر کردن تا اولین پرسشِ سرور یعنی
    //  شاگرد یک لحظه تنظیمات را باز می‌بیند
    session.shop?.role?.let { ir.vil3ntec.tohid.data.ShopRole.remember(context, it) }
    /*
     *  پیش از هر چیز، دفترِ روی گوشی باید مالِ همین حساب باشد.
     *
     *  اگر حسابِ دیگری روی این گوشی کار کرده بود، دفترش همین‌جا بایگانی
     *  می‌شود و دفترِ این حساب باز. بدونِ این، اولین همگام‌سازی همهٔ
     *  ردیف‌های نفرِ قبلی را داخلِ دکانِ این یکی می‌ریخت.
     *
     *  `onDone` عمداً *بعد از* آن صدا زده می‌شود: صدازننده بلافاصله
     *  همگام‌سازی را راه می‌اندازد و اگر ترتیب برعکس باشد، همان نشتی که
     *  بسته شد یک بار اتفاق می‌افتد.
     */
    scope.launch {
      //  دکان هم فرستاده می‌شود: اگر همین حساب دکانش عوض شده باشد،
      //  دفترِ دکانِ قبلی نباید داخلِ دکانِ تازه برود
      runCatching {
        LedgerOwner.signedIn(context, store, session.user.id, session.shop?.id.orEmpty())
      }
      //  حساب *بعد از* آن نوشته می‌شود: جابه‌جاییِ حساب هرچه به حسابِ
      //  قبلی بسته بوده را پاک می‌کند و نامِ تازه هم قربانی می‌شد
      state.rememberAccount(session.user, display)
      onDone()
    }
  }

  //  «می‌شود به سرور زد یا نه» را پیکربندی می‌گوید — نه چیزی که کاربر
  //  تایپ کرده باشد
  val ready = AppConfig.isConfigured(context)

  /*
   *  کدِ شش‌رقمی، در صفحهٔ خودش.
   *
   *  قبلاً کادرِ کد زیرِ همان فرم باز می‌شد و صفحه شلوغ می‌ماند: نام و
   *  شماره و کد و دکمه‌ها همه با هم. حالا وقتی کد فرستاده شد، یک صفحهٔ
   *  جدا می‌آید که فقط یک کار دارد. منطقِ ورود همان است؛ فقط جایش عوض شده.
   */
  if (codeSent) {
    //  یک راهِ ورود مانده و آن ایمیل است؛ شماره اختیاریِ ثبت‌نام است
    val destination = email.trim()
    CodeScreen(
      destination = destination,
      busy = busy,
      error = error,
      note = note,
      askPassword = resetting,
      secondsLeft = { cooldown.secondsLeft(destination) },
      onBack = { codeSent = false; code = ""; note = null; error = null; resetting = false },
      onResend = {
        busy = true; error = null
        scope.launch {
          val asked =
            if (resetting) auth.forgotPassword(destination) else auth.requestOtp(destination)
          asked
            .onSuccess {
              cooldown.start(destination, it.resendSeconds)
              note = "کد دوباره فرستاده شد"
            }
            .onFailure { fail(it) }
          busy = false
        }
      },
      onSubmit = { entered, newPassword ->
        busy = true; error = null; note = null
        scope.launch {
          val done =
            if (resetting) auth.resetPassword(destination, entered, newPassword)
            else auth.verifyOtp(destination, entered, name.trim())
          done.onSuccess { resetting = false; finish(destination, it) }
            .onFailure { fail(it) }
          busy = false
        }
      },
    )
    return
  }

  Box(Modifier.fillMaxSize()) {
    // پس‌زمینه کشیده می‌شود، نه بارگذاری: چند مسیر و چند نقطه روی بوم
    WelcomeBackground()
    Column(
      Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .imePadding(),
    ) {
      GradientHeader(
        title = if (emailMode == "login") "خوش آمدید" else "حساب تازه",
        subtitle = if (emailMode == "login") "با ایمیل و رمز وارد شوید"
        else "ایمیل و رمز؛ شماره اختیاری است",
      )

      /*
       *  روی تبلت، فرم باید وسطِ صفحه بماند.
       *
       *  قبلاً `fillMaxWidth()` و بعد `widthIn(max = …)` پشتِ سرِ هم آمده
       *  بودند: اولی پهنا را به تمامِ صفحه می‌چسباند و `align` دیگر کاری
       *  نداشت بکند، و دومی محتوا را داخلِ همان پهنای تمام‌صفحه به لبه
       *  می‌راند. نتیجه‌اش روی تبلت یک ستونِ باریکِ چسبیده به کنار بود.
       *
       *  حالا یک جعبهٔ تمام‌عرض هست که محتوایش را وسط می‌گذارد، و پهنای
       *  خودِ فرم محدود است.
       */
      Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
      Column(
        Modifier
          .widthIn(max = 460.dp)
          .fillMaxWidth()
          .padding(horizontal = 22.dp),
      ) {
        Spacer(Modifier.height(6.dp))

        Spacer(Modifier.height(12.dp))

        // نام — در هر دو راه، چون حسابِ بی‌نام بعداً فقط یک شماره است
        PillField(
          value = name,
          onValueChange = { name = it; error = null },
          placeholder = "نام شما",
          icon = Icons.Filled.Person,
        )
        Spacer(Modifier.height(12.dp))

        run {
          PillField(
            value = email,
            onValueChange = { email = it; error = null },
            placeholder = "ایمیل",
            icon = Icons.Filled.AlternateEmail,
            keyboardOptions = KeyboardOptions(
              keyboardType = KeyboardType.Email,
              imeAction = ImeAction.Next,
            ),
            ltr = true,
          )
          Spacer(Modifier.height(12.dp))
          PillField(
            value = password,
            onValueChange = { password = it; error = null },
            placeholder = if (emailMode == "register") "رمز عبور (حداقل ۸ نویسه)" else "رمز عبور",
            icon = Icons.Filled.Lock,
            keyboardOptions = KeyboardOptions(
              keyboardType = KeyboardType.Password,
              imeAction = ImeAction.Done,
            ),
            visual = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            trailing = {
              IconButton(onClick = { showPassword = !showPassword }, modifier = Modifier.size(34.dp)) {
                Icon(
                  if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                  contentDescription = if (showPassword) "پنهان کردن رمز" else "نمایش رمز",
                  tint = Shop.colors.muted,
                  modifier = Modifier.size(19.dp),
                )
              }
            },
          )
          /*
           *  تکرارِ رمز — فقط در ثبتِ حسابِ تازه.
           *
           *  ── چرا لازم است ────────────────────────────────────────────
           *  گزارشِ خودِ صاحب مخزن: «اگر دستم اشتباهی خورده باشد، حساب
           *  رفته تو انبار کاه و رمز می‌شود سوزن». درست است: رمزی که یک
           *  بار و پنهان تایپ می‌شود، غلطش هیچ‌جا معلوم نمی‌شود — نه
           *  همان لحظه، نه فردا. سرور هم نمی‌تواند بفهمد؛ برای او آن
           *  رشته همان رمزِ کاربر است.
           *
           *  دفعهٔ بعد که بخواهد وارد شود، رمزی را می‌زند که فکر می‌کند
           *  گذاشته و کار نمی‌کند. راهِ برگشت هم ایمیل است، و اگر ایمیل
           *  را هم غلط زده باشد، حساب رفته.
           *  ──────────────────────────────────────────────────────────
           */
          if (emailMode == "register") {
            Spacer(Modifier.height(12.dp))
            PillField(
              value = password2,
              onValueChange = { password2 = it; error = null },
              placeholder = "تکرارِ رمز",
              icon = Icons.Filled.Lock,
              keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
              ),
              visual = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            )
            //  پیامِ زنده، نه خطای بعدِ زدنِ دکمه: تا اینجا هستی بفهم
            if (password2.isNotBlank()) {
              Spacer(Modifier.height(6.dp))
              val same = password == password2
              Text(
                if (same) "هر دو یکی‌اند" else "دو رمز یکی نیستند",
                style = MaterialTheme.typography.labelSmall,
                color = if (same) Shop.colors.success else Shop.colors.danger,
              )
            }

            /*
             *  شمارهٔ موبایل — اختیاری، و همین‌جا نوشته شده که اختیاری
             *  است.
             *
             *  سرور از اول یکی از این دو را کافی می‌دانست؛ پس شماره
             *  چیزی است که اگر بدهی، برای بازیابی و پیامک به کار
             *  می‌آید، و اگر ندهی جلوی هیچ‌چیز را نمی‌گیرد.
             */
            Spacer(Modifier.height(12.dp))
            PillField(
              value = phone,
              onValueChange = { phone = it; error = null },
              placeholder = "شماره موبایل (اختیاری)",
              icon = Icons.Filled.PhoneAndroid,
              keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Phone,
                imeAction = ImeAction.Done,
              ),
              ltr = true,
            )
          }
        }

        error?.let {
          Spacer(Modifier.height(10.dp))
          Text(it, style = MaterialTheme.typography.labelMedium, color = Shop.colors.danger)
        }
        note?.let {
          Spacer(Modifier.height(10.dp))
          Text(it, style = MaterialTheme.typography.labelMedium, color = Shop.colors.primary)
        }

        Spacer(Modifier.height(18.dp))

        /* ------------------------ دکمهٔ اصلی ------------------------ */
        val label = if (emailMode == "register") "ساخت حساب" else "ورود به حساب"
        val can = ready && !busy && name.isNotBlank() && when {
          //  در ثبت‌نام، تا دو رمز یکی نشوند دکمه باز نمی‌شود
          emailMode == "register" ->
            email.isNotBlank() && password.isNotBlank() && password == password2
          else -> email.isNotBlank() && password.isNotBlank()
        }

        GradientButton(text = label, enabled = can, busy = busy) {
          busy = true; error = null; note = null
          scope.launch {
            when {
              emailMode == "register" ->
                auth.register(name.trim(), email.trim(), phone.trim(), password)
                  .onSuccess {
                    //  سرور با ثبت‌نام نشست هم می‌دهد؛ آن‌وقت مرحلهٔ دومی
                    //  لازم نیست و همان‌جا داخل می‌شویم
                    if (it.isValid) {
                      finish(email.trim(), it)
                    } else {
                      note = "حساب ساخته شد — حالا وارد شوید"
                      emailMode = "login"
                      password = ""
                      password2 = ""
                    }
                  }
                  .onFailure { fail(it) }

              else ->
                auth.login(email.trim(), password)
                  .onSuccess { finish(email.trim(), it) }
                  .onFailure { fail(it) }
            }
            busy = false
          }
        }

        /*
         *  «ورود با کد» — فقط اگر سرور واقعاً کد می‌فرستد.
         *
         *  ── چه چیزی را می‌بندد ──────────────────────────────────────
         *  گزارش شد: «در صفحهٔ ورود یک چیزی نوشته ورود با کد؛ آن را
         *  درست کن یا نباشد». درست بود: تا وقتی سرور فرستندهٔ ایمیل یا
         *  پیامک ندارد (`OTP_PROVIDER=log`)، کد فقط در لاگِ سرور
         *  می‌نشیند و به دستِ کاربر نمی‌رسد. یعنی آن کلید دربِ بسته بود.
         *
         *  حالا خودِ سرور تعیین می‌کند: `/config` می‌گوید `otpEnabled`
         *  هست یا نه، و کلید فقط آن‌وقت ساخته می‌شود. سرور که فرستنده
         *  گرفت، کلید خودش پیدا می‌شود — بی نسخهٔ تازه.
         *  ────────────────────────────────────────────────────────────
         */
        if (otpReady) {
          Spacer(Modifier.height(6.dp))
          TextButton(
            enabled = ready && !busy && name.isNotBlank() && email.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            onClick = {
              busy = true; error = null; note = null
              scope.launch {
                val to = email.trim()
                if (cooldown.secondsLeft(to) > 0) {
                  codeSent = true
                  note = "کد قبلی هنوز معتبر است"
                } else {
                  auth.requestOtp(to)
                    .onSuccess {
                      cooldown.start(to, it.resendSeconds)
                      codeSent = true
                      note = null
                    }
                    .onFailure { fail(it) }
                }
                busy = false
              }
            },
          ) {
            Text(
              "ورود با کد به‌جای رمز",
              color = BLUE,
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.Bold,
            )
          }

        }
        /*
         *  «ثبت‌نام» و «رمز را فراموش کردم» — بیرونِ شرطِ «ورود با کد».
         *
         *  ── چه چیزی خراب شده بود ────────────────────────────────────
         *  این دو دکمه داخلِ همان `if` بودند که «ورود با کد» را
         *  می‌ساخت. وقتی آن شرط به `otpReady` بسته شد — و سرور کد
         *  نمی‌فرستد — این دو هم با آن ناپدید شدند. یعنی هیچ راهی برای
         *  **ثبت‌نام** روی صفحه نماند. خرابیِ خودم بود.
         *
         *  ربطی هم به هم نداشتند: ثبت‌نام با رمز کار می‌کند و به کدِ
         *  ایمیل هیچ نیازی ندارد.
         *  ────────────────────────────────────────────────────────────
         */
        Spacer(Modifier.height(4.dp))
        TextButton(
          onClick = { emailMode = if (emailMode == "login") "register" else "login"; error = null },
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              if (emailMode == "login") "حساب ندارید؟ ثبت‌نام کنید" else "حساب دارم — برگرد به ورود",
              color = Shop.colors.primary,
              style = MaterialTheme.typography.labelLarge,
            )
          }
          if (emailMode == "login") {
            /*
             *  بازیابیِ رمز.
             *
             *  تا امروز این دکمه فقط می‌گفت «با پشتیبانی تماس بگیرید» —
             *  یعنی کسی که رمزش را فراموش می‌کرد از حسابش بیرون می‌ماند.
             *  حالا کد به همان ایمیل می‌رود و همان صفحهٔ کد باز می‌شود،
             *  این بار با کادرِ رمزِ تازه.
             */
            TextButton(
              enabled = ready && !busy && email.isNotBlank(),
              onClick = {
                busy = true; error = null; note = null
                val to = email.trim()
                scope.launch {
                  auth.forgotPassword(to)
                    .onSuccess {
                      cooldown.start(to, it.resendSeconds)
                      resetting = true
                      codeSent = true
                    }
                    .onFailure { fail(it) }
                  busy = false
                }
              },
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text(
                if (email.isBlank()) "رمز را فراموش کرده‌اید؟ اول ایمیل را بزنید"
                else "رمز عبور را فراموش کرده‌اید؟",
                color = Shop.colors.muted,
                style = MaterialTheme.typography.labelMedium,
              )
            }
          }

        /* ------------------------ پایینِ صفحه ------------------------ */
        /*
         *  صفحه عمداً کوتاه نگه داشته می‌شود.
         *
         *  قبلاً کدِ شاگرد و آدرسِ سرور و فهرستِ حساب‌ها همه باز و زیرِ هم
         *  بودند و صفحه یک ستونِ بلندِ شلوغ می‌شد. کسی که برای اولین بار
         *  می‌آید فقط دو کادر و یک دکمه لازم دارد؛ بقیه سرِ راهش
         *  نمی‌ایستد و هر وقت خواست بازش می‌کند.
         */
        Spacer(Modifier.height(18.dp))
        Row(
          Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.Center,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            "حساب نمی‌خواهید؟",
            style = MaterialTheme.typography.labelMedium,
            color = inkSoft,
          )
          TextButton(onClick = onDone, contentPadding = PaddingValues(horizontal = 8.dp)) {
            Text(
              "ادامه بدون حساب",
              style = MaterialTheme.typography.labelLarge,
              color = BLUE,
              fontWeight = FontWeight.Bold,
            )
          }
        }

        // جداکنندهٔ نازک با «یا» وسطش — مرزِ بینِ راهِ اصلی و کارهای فنی.
        // فقط وقتی کشیده می‌شود که زیرش چیزی باشد، وگرنه خطی می‌ماند که
        // به هیچ‌جا مرز نمی‌زند.
        if (GOOGLE_LOGIN || saved.isNotEmpty()) {
          Spacer(Modifier.height(6.dp))
          Row(
            Modifier.fillMaxWidth().padding(horizontal = 30.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            HorizontalDivider(Modifier.weight(1f), color = fieldLine)
            Text(
              "یا",
              style = MaterialTheme.typography.labelMedium,
              color = inkSoft,
              modifier = Modifier.padding(horizontal = 12.dp),
            )
            HorizontalDivider(Modifier.weight(1f), color = fieldLine)
          }
        }

        /*
         *  ورود با ایمیلِ گوگلِ خودِ گوشی — بی رمز، بی کد.
         *
         *  تا دیروز این کلید فقط وقتی ساخته می‌شد که سرور کلیدِ گوگل را
         *  داده باشد. نتیجه‌اش این بود که روی سرورِ تازه‌راه‌افتاده، کلید
         *  **اصلاً دیده نمی‌شد** و صاحب دکان نمی‌دانست چنین راهی هست، چه
         *  رسد به اینکه بداند برای بازکردنش باید چه کند.
         *
         *  و حالا یک کلیدِ خاموش/روشن دارد (`GOOGLE_LOGIN`). خواسته شد
         *  «فعلاً دیده نشود، ولی پاک نشود» — چون تا در Google Cloud
         *  شناسه‌ای ساخته نشده، این کلید کاری از پیش نمی‌برد و بودنش
         *  فقط صفحهٔ ورود را شلوغ می‌کند. کدش سرِ جایش است؛ روزی که
         *  شناسه ساخته شد، `GOOGLE_LOGIN` را `true` کنید و همین کلید
         *  برمی‌گردد.
         */
        if (GOOGLE_LOGIN) {
          Spacer(Modifier.height(14.dp))
          GoogleButton(enabled = ready && !busy) {
            if (googleId.isBlank()) {
              note = null
              error = "ورود با گوگل روی این سرور باز نشده — در پروندهٔ .env مقدار " +
                "GOOGLE_CLIENT_ID را بگذارید و سرور را دوباره بالا بیاورید"
            } else {
              busy = true; error = null; note = null
              scope.launch {
                runCatching { ir.vil3ntec.tohid.data.GoogleSignIn.pick(context, googleId) }
                  .onSuccess { account ->
                    if (account == null) {
                      // کاربر خودش بست — نه خطا، نه پیام
                    } else {
                      auth.loginWithGoogle(account.idToken)
                        .onSuccess { finish(account.email, it) }
                        .onFailure { fail(it) }
                    }
                  }
                  .onFailure { error = it.userText("ورود با گوگل انجام نشد") }
                busy = false
              }
            }
          }
        }

        if (saved.isNotEmpty()) {
          Spacer(Modifier.height(14.dp))
          Text("ورود سریع", style = MaterialTheme.typography.labelMedium, color = Shop.colors.muted)
          Spacer(Modifier.height(8.dp))
          saved.forEach { entry ->
            SavedLoginRow(
              entry = entry,
              onPick = {
                error = null
                /*
                 *  ورودِ سریع، واقعاً سریع.
                 *
                 *  تا دیروز این ردیف فقط کادرِ ایمیل را پر می‌کرد و کاربر
                 *  باید رمز را از نو می‌زد — گزارش هم همین بود: «اسمِ
                 *  حسابم را نشان می‌دهد، رویش می‌زنم، ولی مرا داخل
                 *  نمی‌برد».
                 *
                 *  حالا اگر توکنِ همان حساب را داشته باشیم (هنگام خروج
                 *  کنارش گذاشته می‌شود) نشست همان‌جا برمی‌گردد. اگر سرور
                 *  ردش کرد — باطل شده یا مهلتش تمام — بی‌صدا به راهِ
                 *  همیشگی برمی‌گردیم و کادر پر می‌شود.
                 */
                fun fillIn() {
                  if (entry.identifier.contains("@")) {
                    emailMode = "login"
                    email = entry.identifier
                  } else {
                    //  حسابی که با شماره ساخته شده بود: کادرِ شماره از
                    //  صفحهٔ ورود برداشته شده، پس همان‌جا می‌گوییم چه کند
                    emailMode = "login"
                    note = "این حساب با شماره ساخته شده بود — با ایمیلِ همان حساب وارد شوید"
                  }
                  if (name.isBlank()) name = entry.shop
                }
                if (entry.refresh.isBlank() || !ready) {
                  fillIn()
                } else {
                  busy = true
                  scope.launch {
                    auth.resume(entry.refresh)
                      .onSuccess { session ->
                        //  نشستِ کامل — نام و دکانش از سرور آمده
                        finish(entry.identifier, session)
                      }
                      .onFailure {
                        fillIn()
                        note = "برای امنیت، این بار رمز یا کد لازم است"
                      }
                    busy = false
                  }
                }
              },
              onForget = {
                SavedLogins.forget(context, entry.identifier)
                saved = SavedLogins.read(context)
              },
            )
            Spacer(Modifier.height(6.dp))
          }
        }

        /* --------------------- گزینه‌های بیشتر --------------------- */
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
          TextButton(onClick = { showMore = !showMore }) {
            Icon(
              Icons.Filled.Tune,
              contentDescription = null,
              tint = inkSoft,
              modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
              if (showMore) "بستن" else "کد شاگرد دارم",
              style = MaterialTheme.typography.labelMedium,
              color = inkSoft,
            )
          }
        }

        AnimatedVisibility(
          visible = showMore,
          enter = fadeIn() + expandVertically(),
          exit = shrinkVertically(),
        ) {
          Column {
            Spacer(Modifier.height(6.dp))
            PillField(
              value = staffCode,
              onValueChange = { staffCode = it.uppercase(); error = null },
              placeholder = "کد شاگرد — ${StaffCode.HINT}",
              icon = Icons.Filled.Storefront,
              ltr = true,
              trailing = {
                TextButton(
                  enabled = !busy && staffCode.isNotBlank(),
                  contentPadding = PaddingValues(horizontal = 8.dp),
                  onClick = {
                    val entered = StaffCode.clean(staffCode)
                    if (!StaffCode.looksValid(entered)) {
                      error = "این کد درست نیست. کد باید مثل ${StaffCode.HINT} باشد."
                      return@TextButton
                    }
                    busy = true; error = null
                    scope.launch {
                      /*
                       *  کد خودش اعتبارنامه است.
                       *
                       *  تا دیروز اینجا نوشته بود «اول وارد حساب خود
                       *  شوید» — یعنی صاحب دکان که کد را به شاگردش
                       *  می‌داد، باید یک مرحلهٔ دیگر هم برایش توضیح
                       *  می‌داد، و شاگردی که ایمیل و شماره ندارد اصلاً
                       *  وارد نمی‌شد.
                       *
                       *  حالا سرور خودش برای همین دستگاه یک حسابِ
                       *  شاگرد می‌سازد و روی همان دکان می‌نشاندش. اگر
                       *  کسی از قبل واردِ حسابی باشد، همان حساب به
                       *  دکان می‌پیوندد و حسابِ تازه‌ای ساخته نمی‌شود.
                       */
                      if (Backend.tokens(context).signedIn) {
                        //  از قبل واردِ حسابی است — همان حساب به دکان
                        //  می‌پیوندد و حسابِ تازه‌ای ساخته نمی‌شود
                        shops.join(entered)
                          .onSuccess { shopState ->
                            //  نقش همین‌جا نوشته می‌شود، نه در تازه‌سازیِ
                            //  بعدی: کسی که با کد پیوسته «شاگرد» است و
                            //  نباید حتی یک لحظه تنظیمات را باز ببیند
                            shopState.role?.let {
                              ir.vil3ntec.tohid.data.ShopRole.remember(context, it)
                            }
                            runCatching {
                              LedgerOwner.shopChanged(
                                context, store, shopState.shop?.id.orEmpty(),
                              )
                            }
                            onDone()
                          }
                          .onFailure { fail(it) }
                      } else {
                        auth.loginWithStaffCode(
                          code = entered,
                          name = name.trim(),
                          deviceUid = state.deviceUid,
                          deviceName = android.os.Build.MODEL ?: "گوشی",
                        )
                          .onSuccess { session ->
                            SavedLogins.remember(context, entered, session.user.name)
                            session.shop?.role?.let {
                              ir.vil3ntec.tohid.data.ShopRole.remember(context, it)
                            }
                            //  دفترِ روی گوشی باید مالِ همین حساب و همین
                            //  دکان باشد، وگرنه دفترِ قبلی با اولین
                            //  همگام‌سازی صاف می‌رفت داخلِ دکانِ تازه
                            runCatching {
                              LedgerOwner.signedIn(
                                context, store, session.user.id, session.shop?.id.orEmpty(),
                              )
                            }
                            state.rememberAccount(session.user)
                            onDone()
                          }
                          .onFailure { fail(it) }
                      }
                      busy = false
                    }
                  },
                ) { Text("پیوستن", color = Shop.colors.primary, style = MaterialTheme.typography.labelMedium) }
              },
            )
            Text(
              "کدی که صاحب دکان از تنظیمات برنامه‌اش به شما می‌دهد. همین کافی است — ایمیل و شماره لازم نیست.",
              style = MaterialTheme.typography.labelSmall,
              color = Shop.colors.muted2,
              modifier = Modifier.padding(top = 6.dp, start = 4.dp),
            )

            /*
             *  نشانیِ سرور اینجا نیست و هیچ‌جای دیگر هم نیست.
             *
             *  نشانی در زمانِ ساخت داخلِ برنامه می‌نشیند و قفل است؛ نه
             *  دیده می‌شود، نه می‌شود برنامه را به سرورِ دیگری برد.
             *
             *  اگر نسخه‌ای بی‌نشانی ساخته شود، همین صفحه پایین‌تر
             *  می‌گوید که حساب کار نمی‌کند — به‌جای کادری که کاربر
             *  نمی‌داند در آن چه بنویسد.
             */
            if (!AppConfig.isConfigured(context)) {
              Spacer(Modifier.height(12.dp))
              Text(
                "این نسخه به سروری بسته نشده، پس حساب و همگام‌سازی در آن " +
                  "کار نمی‌کند. برنامه بدونِ حساب کامل کار می‌کند؛ برای حساب، " +
                  "نسخهٔ منتشرشده را نصب کنید.",
                style = MaterialTheme.typography.labelSmall,
                color = Shop.colors.warning,
                modifier = Modifier.padding(top = 6.dp, start = 4.dp),
              )
            }
          }
        }

        Spacer(Modifier.height(28.dp))
      }
      }
    }
  }
}

/* ============================== صفحهٔ کد ============================== */

/**
 *  صفحهٔ کدِ شش‌رقمی.
 *
 *  یک کار دارد و فقط همان را نشان می‌دهد. شش خانهٔ جدا، نه یک کادرِ دراز:
 *  کسی که کد را از پیامک می‌خواند، رقم‌به‌رقم می‌زند و باید ببیند کجاست.
 *
 *  صفحه‌کلیدِ عددی خودش بالا می‌آید و با کاملِ شش رقم، خودش می‌فرستد —
 *  یک زدنِ کمتر.
 */
@Composable
private fun CodeScreen(
  destination: String,
  busy: Boolean,
  error: String?,
  note: String?,
  askPassword: Boolean,
  secondsLeft: () -> Int,
  onBack: () -> Unit,
  onResend: () -> Unit,
  onSubmit: (code: String, newPassword: String) -> Unit,
) {
  var code by rememberSaveable { mutableStateOf("") }
  var fresh by rememberSaveable { mutableStateOf("") }
  var showFresh by rememberSaveable { mutableStateOf(false) }
  val focus = remember { FocusRequester() }
  val keyboard = LocalSoftwareKeyboardController.current

  /*
   *  شمارشِ معکوسِ ارسالِ دوباره.
   *
   *  عدد از روی مهلتی می‌آید که روی گوشی نوشته شده، نه از یک شمارندهٔ
   *  داخلِ همین صفحه. پس چرخاندنِ گوشی، برگشتن و آمدن، و حتی بستن و باز
   *  کردنِ برنامه، شمارش را از صفر شروع نمی‌کند — و یک پیامکِ اضافه از
   *  اعتبارِ شما نمی‌رود.
   */
  var wait by remember { mutableStateOf(secondsLeft()) }
  LaunchedEffect(Unit) {
    //  تا وقتی این صفحه باز است هر ثانیه می‌خوانیم. حلقه نمی‌شکند، چون
    //  «ارسال دوباره» مهلت را بعد از پاسخِ سرور می‌نویسد و ما باید همان
    //  لحظه ببینیمش — نه اینکه از قبل ایستاده باشیم.
    while (true) {
      wait = secondsLeft()
      kotlinx.coroutines.delay(1000)
    }
  }

  LaunchedEffect(Unit) { focus.requestFocus(); keyboard?.show() }

  BackHandler { onBack() }

  Box(Modifier.fillMaxSize()) {
    WelcomeBackground()
    Column(
      Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding(),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Spacer(Modifier.height(40.dp))
      BrandMark()
      Spacer(Modifier.height(22.dp))
      Text(
        if (askPassword) "رمز تازه" else "کد را بزنید",
        style = MaterialTheme.typography.headlineSmall,
        color = ink,
        fontWeight = FontWeight.Bold,
      )
      Spacer(Modifier.height(8.dp))
      Text(
        if (askPassword) "کد به $destination فرستاده شد. آن را بزنید و رمز تازه بگذارید."
        else "کد شش‌رقمی به $destination فرستاده شد",
        style = MaterialTheme.typography.bodyMedium,
        color = inkSoft,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 30.dp),
      )

      Spacer(Modifier.height(26.dp))

      Box(Modifier.widthIn(max = 400.dp).padding(horizontal = 22.dp)) {
        //  کادرِ واقعی نامرئی است و شش خانه فقط نمایشِ همان‌اند؛ این‌طور
        //  کپی و چسباندنِ کد از پیامک هم کار می‌کند
        BasicTextField(
          value = code,
          onValueChange = { raw ->
            val digits = raw.filter { it.isDigit() }.take(6)
            code = digits
            //  در حالتِ عادی با کاملِ شش رقم خودش می‌فرستد. در بازیابی نه،
            //  چون رمزِ تازه هم باید نوشته شود.
            if (digits.length == 6 && !busy && !askPassword) { keyboard?.hide(); onSubmit(digits, "") }
          },
          keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.NumberPassword,
            imeAction = ImeAction.Done,
          ),
          cursorBrush = SolidColor(Color.Transparent),
          textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.Transparent),
          modifier = Modifier.fillMaxWidth().height(62.dp).focusRequester(focus).alpha(0.01f),
        )
        Row(
          Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          //  چپ‌به‌راست، چون خودِ عدد چپ‌به‌راست خوانده می‌شود
          CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            repeat(6) { index ->
              CodeCell(
                digit = code.getOrNull(index)?.toString().orEmpty(),
                active = index == code.length,
                modifier = Modifier.weight(1f),
              )
            }
          }
        }
      }

      error?.let {
        Spacer(Modifier.height(14.dp))
        Text(it, style = MaterialTheme.typography.labelMedium, color = Shop.colors.danger)
      }
      note?.let {
        Spacer(Modifier.height(14.dp))
        Text(it, style = MaterialTheme.typography.labelMedium, color = BLUE)
      }

      if (askPassword) {
        Spacer(Modifier.height(18.dp))
        Box(Modifier.widthIn(max = 400.dp).fillMaxWidth().padding(horizontal = 22.dp)) {
          PillField(
            value = fresh,
            onValueChange = { fresh = it },
            placeholder = "رمز تازه (حداقل ۸ نویسه)",
            icon = Icons.Filled.Lock,
            keyboardOptions = KeyboardOptions(
              keyboardType = KeyboardType.Password,
              imeAction = ImeAction.Done,
            ),
            visual = if (showFresh) VisualTransformation.None else PasswordVisualTransformation(),
            trailing = {
              IconButton(onClick = { showFresh = !showFresh }, modifier = Modifier.size(34.dp)) {
                Icon(
                  if (showFresh) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                  contentDescription = if (showFresh) "پنهان کردن رمز" else "نمایش رمز",
                  tint = Shop.colors.muted,
                  modifier = Modifier.size(19.dp),
                )
              }
            },
          )
        }
      }

      Spacer(Modifier.height(24.dp))
      Box(Modifier.widthIn(max = 400.dp).fillMaxWidth().padding(horizontal = 22.dp)) {
        GradientButton(
          text = if (askPassword) "گذاشتن رمز و ورود" else "ورود",
          enabled = code.length == 6 && !busy && (!askPassword || fresh.length >= 8),
          busy = busy,
        ) { onSubmit(code, fresh) }
      }

      Spacer(Modifier.height(10.dp))
      TextButton(
        enabled = wait == 0 && !busy,
        //  مهلت را خودِ onResend بعد از پاسخِ سرور می‌نویسد؛ حلقهٔ بالا
        //  همان ثانیه می‌بیندش. اگر ارسال نشد، مهلتی هم نوشته نمی‌شود و
        //  کاربر می‌تواند دوباره بزند.
        onClick = onResend,
      ) {
        Text(
          if (wait > 0) "ارسال دوبارهٔ کد تا ${wait.faDigits()} ثانیه" else "ارسال دوبارهٔ کد",
          style = MaterialTheme.typography.labelLarge,
          color = if (wait == 0) BLUE else inkSoft,
        )
      }
      TextButton(onClick = onBack) {
        Text(
          "برگشت و عوض کردن نشانی",
          style = MaterialTheme.typography.labelMedium,
          color = inkSoft,
        )
      }
      Spacer(Modifier.height(30.dp))
    }
  }
}

/** یک خانهٔ کد */
@Composable
private fun CodeCell(digit: String, active: Boolean, modifier: Modifier = Modifier) {
  val face = if (isDarkSurface()) Shop.colors.surface else Color.White
  val edge by animateColorAsState(
    targetValue = if (active) BLUE else fieldLine,
    animationSpec = tween(if (Motion.enabled) 160 else 0),
    label = "cellEdge",
  )
  Box(
    modifier
      .height(58.dp)
      .shadow(if (active) 8.dp else 3.dp, RoundedCornerShape(16.dp), ambientColor = BLUE, spotColor = BLUE)
      .clip(RoundedCornerShape(16.dp))
      .background(face)
      .border(if (active) 2.dp else 1.dp, edge, RoundedCornerShape(16.dp)),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      digit,
      style = MaterialTheme.typography.headlineSmall,
      color = ink,
      fontWeight = FontWeight.Bold,
    )
  }
}

/** رقم‌های فارسی، فقط برای شمارش */
private fun Int.faDigits(): String =
  toString().map { c -> if (c.isDigit()) "۰۱۲۳۴۵۶۷۸۹"[c - '0'] else c }.joinToString("")

/* ============================== اجزا ============================== */

/**
 *  سربرگِ رنگیِ بالای صفحه، با لبهٔ موجی.
 *
 *  لبهٔ صاف، سربرگ را یک مستطیلِ چسبانده به بالای صفحه نشان می‌دهد؛ موج
 *  همان یک خط است که کاری می‌کند رنگ روی صفحه «ریخته» باشد نه «چسبانده».
 */
/**
 *  سربرگ — نشانِ گرد، عنوان، و یک خط توضیح.
 *
 *  دیگر بلوکِ رنگی نیست: پس‌زمینه‌ی خودِ صفحه زیرِ همه‌جا ادامه دارد و
 *  سربرگ فقط محتواست. بلوکِ تیره‌ی قبلی صفحه را دو تکه می‌کرد و بالای
 *  آن با پایینش حرف نمی‌زد.
 */
@Composable
private fun GradientHeader(title: String, subtitle: String) {
  val colors = Shop.colors
  Box(
    Modifier
      .fillMaxWidth()
      .windowInsetsPadding(WindowInsets.statusBars),
    contentAlignment = Alignment.TopCenter,
  ) {
    Column(
      Modifier
        .widthIn(max = 460.dp)
        .fillMaxWidth()
        .padding(horizontal = 26.dp)
        .padding(top = if (isTablet()) 30.dp else 18.dp, bottom = 6.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      WelcomeMark(size = if (isTablet()) 138.dp else 112.dp)
      Spacer(Modifier.height(if (isTablet()) 22.dp else 16.dp))
      Text(
        title,
        style = if (isTablet()) MaterialTheme.typography.displaySmall
        else MaterialTheme.typography.headlineMedium,
        color = ink,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
      )
      Spacer(Modifier.height(10.dp))
      Text(
        subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = inkSoft,
        textAlign = TextAlign.Center,
      )
    }
  }
}

@Composable
private fun ChannelTab(
  text: String,
  icon: ImageVector,
  active: Boolean,
  modifier: Modifier = Modifier,
  onClick: () -> Unit,
) {
  val tint by animateColorAsState(
    targetValue = if (active) Color.White else inkSoft,
    animationSpec = tween(if (Motion.enabled) 220 else 0),
    label = "tabTint",
  )
  Row(
    modifier
      .clip(RoundedCornerShape(26.dp))
      .clickable(onClick = onClick)
      .padding(vertical = 13.dp),
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(17.dp))
    Spacer(Modifier.width(6.dp))
    Text(
      text,
      style = MaterialTheme.typography.labelLarge,
      color = tint,
      fontWeight = FontWeight.Bold,
      maxLines = 1,
    )
  }
}

/**
 *  کادرِ قرصی‌شکل.
 *
 *  شماره و ایمیل و آدرس با `ltr` نوشته می‌شوند: رشتهٔ لاتین وسطِ صفحهٔ
 *  راست‌به‌چپ وارونه دیده می‌شود و کاربر فکر می‌کند چیزِ دیگری تایپ کرده.
 */
@Composable
/**
 *  کادرِ ورودیِ گِردِ صفحهٔ ورود.
 *
 *  `internal` است چون صفحهٔ «ساختِ دکان» هم از همین استفاده می‌کند: آن
 *  صفحه ادامهٔ همین مسیر است و نباید کادرهایش شکلِ دیگری داشته باشد.
 */
internal fun PillField(
  value: String,
  onValueChange: (String) -> Unit,
  placeholder: String,
  icon: ImageVector,
  modifier: Modifier = Modifier,
  keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
  visual: VisualTransformation = VisualTransformation.None,
  ltr: Boolean = false,
  enabled: Boolean = true,
  trailing: @Composable (() -> Unit)? = null,
) {
  val colors = Shop.colors
  val dark = colors.bg != Color(0xFFFFFFFF) && colors.text == Color.White
  val interaction = remember { MutableInteractionSource() }
  val focused by interaction.collectIsFocusedAsState()

  // خطِ دور و سایه هر دو با فوکوس عوض می‌شوند، ولی نرم: پرشِ ناگهانیِ
  // سایه، کادر را می‌پراند
  val line by animateColorAsState(
    targetValue = if (focused) BLUE else fieldLine,
    animationSpec = tween(if (Motion.enabled) 200 else 0),
    label = "pillLine",
  )
  val lift by animateDpAsState(
    targetValue = if (focused) 9.dp else 4.dp,
    animationSpec = tween(if (Motion.enabled) 200 else 0),
    label = "pillLift",
  )

  val face = if (dark) colors.surface else Color.White
  val fieldInk = if (dark) colors.text else INK_LIGHT
  val hint = if (dark) colors.muted2 else INK_SOFT_LIGHT

  val inner: @Composable () -> Unit = {
    Row(
      Modifier
        .fillMaxWidth()
        .heightIn(min = 60.dp)
        // سایهٔ آبیِ بسیار نرم، نه سیاه: سایهٔ سیاه روی زمینهٔ آبیِ روشن
        // خاکستری و کثیف دیده می‌شود
        .shadow(lift, RoundedCornerShape(30.dp), clip = false, ambientColor = BLUE, spotColor = BLUE)
        .clip(RoundedCornerShape(30.dp))
        .background(face)
        .border(if (focused) 1.5.dp else 1.dp, line, RoundedCornerShape(30.dp))
        .padding(horizontal = 20.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        icon,
        contentDescription = null,
        tint = if (focused) BLUE else hint,
        modifier = Modifier.size(19.dp),
      )
      Spacer(Modifier.width(12.dp))
      Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
        if (value.isEmpty()) {
          Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = hint)
        }
        BasicTextField(
          value = value,
          onValueChange = onValueChange,
          singleLine = true,
          enabled = enabled,
          interactionSource = interaction,
          keyboardOptions = keyboardOptions,
          visualTransformation = visual,
          textStyle = MaterialTheme.typography.bodyMedium.copy(
            color = if (enabled) fieldInk else hint,
          ),
          cursorBrush = SolidColor(BLUE),
          modifier = Modifier.fillMaxWidth(),
        )
      }
      if (trailing != null) {
        Spacer(Modifier.width(6.dp))
        trailing()
      }
    }
  }

  /*
   *  شماره و ایمیل و آدرس چپ‌به‌راست نوشته می‌شوند: رشتهٔ لاتین وسطِ
   *  صفحهٔ راست‌به‌چپ وارونه دیده می‌شود و کاربر فکر می‌کند چیزِ دیگری
   *  تایپ کرده.
   */
  Box(modifier.fillMaxWidth()) {
    if (ltr) {
      CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) { inner() }
    } else {
      inner()
    }
  }
}

/** دکمهٔ اصلی — قرصِ رنگی با شیب */
/**
 *  دکمهٔ اصلی.
 *
 *  شیبِ آبی، گوشه‌های کاملاً گرد، و سایه‌ای که رنگش خودِ آبی است نه سیاه.
 *  فشردن دیده می‌شود (کوچک شدنِ ملایم)، و حالتِ غیرفعال خاکستریِ تخت است
 *  تا با حالتِ فعال اشتباه نشود.
 */
@Composable
private fun GradientButton(
  text: String,
  enabled: Boolean,
  busy: Boolean,
  onClick: () -> Unit,
) {
  val interaction = remember { MutableInteractionSource() }
  val pressed by interaction.collectIsPressedAsState()
  val scale by animateFloatAsState(
    targetValue = if (pressed && enabled && !busy) 0.975f else 1f,
    animationSpec = tween(if (Motion.enabled) 120 else 0, easing = FastOutSlowInEasing),
    label = "ctaPress",
  )

  val shape = RoundedCornerShape(30.dp)
  val live = enabled && !busy
  // دکمهٔ غیرفعال هم باید در تمِ تاریک دیده شود، نه اینکه در زمینه گم شود
  val disabledFace = if (isDarkSurface()) Shop.colors.surface2 else FIELD_LINE_LIGHT

  Box(
    Modifier
      .fillMaxWidth()
      .height(60.dp)
      .graphicsLayer { scaleX = scale; scaleY = scale }
      .shadow(
        elevation = if (live) 12.dp else 0.dp,
        shape = shape,
        clip = false,
        ambientColor = BLUE,
        spotColor = BLUE,
      )
      .clip(shape)
      .background(
        if (live) Brush.horizontalGradient(listOf(BLUE_DEEP, BLUE, Color(0xFF3C7DE0)))
        else Brush.horizontalGradient(listOf(disabledFace, disabledFace))
      )
      .clickable(
        enabled = live,
        interactionSource = interaction,
        indication = null,
        onClick = onClick,
      ),
    contentAlignment = Alignment.Center,
  ) {
    if (busy) {
      CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
    } else {
      Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = if (live) Color.White else inkSoft,
        fontWeight = FontWeight.Bold,
      )
    }
  }
}

/**
 *  دکمهٔ «ورود با گوگل».
 *
 *  سفید و کم‌رنگ است، نه آبیِ پررنگ: راهِ اصلی همان شماره و ایمیل بالاست
 *  و این یکی نباید از آن بلندتر حرف بزند.
 */
@Composable
private fun GoogleButton(enabled: Boolean, onClick: () -> Unit) {
  val interaction = remember { MutableInteractionSource() }
  val pressed by interaction.collectIsPressedAsState()
  val scale by animateFloatAsState(
    targetValue = if (pressed && enabled) 0.975f else 1f,
    animationSpec = tween(if (Motion.enabled) 120 else 0, easing = FastOutSlowInEasing),
    label = "googlePress",
  )
  val shape = RoundedCornerShape(28.dp)
  val face = if (isDarkSurface()) Shop.colors.surface else Color.White

  Row(
    Modifier
      .fillMaxWidth()
      .height(54.dp)
      .graphicsLayer { scaleX = scale; scaleY = scale }
      .shadow(if (enabled) 6.dp else 0.dp, shape, clip = false, ambientColor = BLUE, spotColor = BLUE)
      .clip(shape)
      .background(face)
      .border(1.dp, fieldLine, shape)
      .clickable(
        enabled = enabled,
        interactionSource = interaction,
        indication = null,
        onClick = onClick,
      )
      .alpha(if (enabled) 1f else 0.5f),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.Center,
  ) {
    GoogleMark()
    Spacer(Modifier.width(10.dp))
    Text(
      "ورود با حساب گوگل",
      style = MaterialTheme.typography.titleSmall,
      color = ink,
      fontWeight = FontWeight.Bold,
    )
  }
}

/**
 *  نشانِ گوگل.
 *
 *  ── چه چیزی خراب بود ──────────────────────────────────────────────
 *  گزارش شد «آن مارک گوگل خراب درآمده». درست بود: نشان با چهار کمانِ
 *  یک حلقه به‌علاوهٔ یک خطِ افقی کشیده می‌شد — یعنی حدسی از روی شکلِ
 *  «G» گوگل، نه خودش. کمان‌ها هم‌اندازه بودند و خط از جای درست
 *  درنمی‌آمد؛ نتیجه یک دایرهٔ چهاررنگِ ناجور بود که هرکسی نشانِ گوگل را
 *  دیده باشد می‌فهمد بدل است.
 *  ──────────────────────────────────────────────────────────────────
 *
 *  ── چه چیزی جایش آمد ──────────────────────────────────────────────
 *  همان چهار تکهٔ نشانِ رسمی، عیناً از روی مسیرهای برداریِ خودِ گوگل در
 *  دستگاهِ ۴۸×۴۸، که با `PathParser` خوانده و به اندازهٔ کلید بزرگ
 *  می‌شوند. هنوز هیچ تصویری همراهِ برنامه حمل نمی‌شود — فقط چند رشته
 *  متن — پس حجمِ برنامه دست‌نخورده می‌ماند و نشان در هر تراکمِ صفحه‌ای
 *  تیز است.
 *  ──────────────────────────────────────────────────────────────────
 */
@Composable
private fun GoogleMark() {
  //  یک بار خوانده می‌شوند، نه با هر بار کشیده شدنِ کلید
  val marks = remember {
    listOf(
      Color(0xFF4285F4) to PathParser().parsePathString(G_BLUE).toPath(),
      Color(0xFF34A853) to PathParser().parsePathString(G_GREEN).toPath(),
      Color(0xFFFBBC05) to PathParser().parsePathString(G_YELLOW).toPath(),
      Color(0xFFEA4335) to PathParser().parsePathString(G_RED).toPath(),
    )
  }
  Canvas(Modifier.size(20.dp)) {
    val k = size.minDimension / 48f
    scale(k, k, pivot = Offset.Zero) {
      marks.forEach { (tint, path) -> drawPath(path, tint) }
    }
  }
}

//  مسیرهای نشانِ گوگل، در دستگاهِ ۴۸×۴۸
/**
 *  کلیدِ «ورود با حساب گوگل» — فعلاً خاموش.
 *
 *  گوگل برای این کار دو کلاینت می‌خواهد (Android و Web) و تا آن‌ها در
 *  Google Cloud ساخته نشوند و `GOOGLE_CLIENT_ID` روی سرور نشیند، زدنِ
 *  این کلید فقط یک پیامِ خطا می‌دهد. پس تا آن روز دیده نمی‌شود.
 *
 *  هیچ کدی پاک نشده: `GoogleButton`، `GoogleMark`، `GoogleSignIn` و
 *  مسیرِ سرور همه سرِ جایشان‌اند. این را `true` کنید و کلید برمی‌گردد.
 */
private const val GOOGLE_LOGIN = false

private const val G_BLUE =
  "M45.12 24.5c0-1.56-.14-3.06-.4-4.5H24v8.51h11.84c-.51 2.75-2.06 5.08-4.39 6.64v5.52h7.11" +
    "c4.16-3.83 6.56-9.47 6.56-16.17z"
private const val G_GREEN =
  "M24 46c5.94 0 10.92-1.97 14.56-5.33l-7.11-5.52c-1.97 1.32-4.49 2.1-7.45 2.1-5.73 0-10.58-3.87-12.31-9.07" +
    "H4.34v5.7C7.96 41.07 15.4 46 24 46z"
private const val G_YELLOW =
  "M11.69 28.18C11.25 26.86 11 25.45 11 24s.25-2.86.69-4.18v-5.7H4.34C2.85 17.09 2 20.45 2 24" +
    "s.85 6.91 2.34 9.88l7.35-5.7z"
private const val G_RED =
  "M24 10.75c3.23 0 6.13 1.11 8.41 3.29l6.31-6.31C34.91 4.18 29.93 2 24 2 15.4 2 7.96 6.93 4.34 14.12" +
    "l7.35 5.7c1.73-5.2 6.58-9.07 12.31-9.07z"

/** نشانِ برنامه با همان تپشِ ملایمِ نسخهٔ وب */
@Composable
private fun BrandMark() {
  val colors = Shop.colors
  val pulse = rememberInfiniteTransition(label = "brand")
  val breathe by pulse.animateFloat(
    initialValue = 0.35f,
    targetValue = 0.75f,
    animationSpec = infiniteRepeatable(
      tween(if (Motion.enabled) 1900 else 1, easing = EaseInOutSine),
      RepeatMode.Reverse,
    ),
    label = "glow",
  )
  // حلقهٔ طلایی که آرام دورِ نشان می‌چرخد
  val spin by pulse.animateFloat(
    initialValue = 0f,
    targetValue = 360f,
    animationSpec = infiniteRepeatable(
      // ۳۶۰ درجه همان ۰ درجه است، پس چرخش بی‌درز است
      tween(if (Motion.enabled) 7000 else 1, easing = LinearEasing),
      RepeatMode.Restart,
    ),
    label = "spin",
  )

  Box(contentAlignment = Alignment.Center) {
    Box(
      Modifier
        .size(72.dp)
        .alpha(breathe * 0.4f)
        .clip(RoundedCornerShape(24.dp))
        .background(colors.primary)
    )
    Canvas(Modifier.size(78.dp).graphicsLayer { rotationZ = spin }) {
      // یک کمانِ طلایی، نه یک حلقهٔ کامل: حلقهٔ کامل که بچرخد، دیده
      // نمی‌شود که می‌چرخد
      drawArc(
        brush = Brush.sweepGradient(
          listOf(Color.Transparent, GOLD_RING, GOLD_GLOW, Color.Transparent, Color.Transparent)
        ),
        startAngle = 0f,
        sweepAngle = 220f,
        useCenter = false,
        style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round),
        alpha = 0.55f + breathe * 0.35f,
      )
    }
    //  مربعِ سفیدِ گردگوشه، با همان نسبتی که آیکنِ روی صفحهٔ گوشی دارد.
    //  تا دیروز اینجا یک آیکنِ عمومیِ «مغازه» بود؛ کاربر روی صفحهٔ گوشی
    //  یک نشان می‌دید و داخلِ برنامه نشانِ دیگری.
    Box(
      Modifier
        .size(54.dp)
        .clip(RoundedCornerShape(19.dp))
        .background(Color.White)
        .border(1.dp, GOLD_GLOW.copy(alpha = 0.45f), RoundedCornerShape(19.dp)),
      contentAlignment = Alignment.Center,
    ) {
      TohidMark(Modifier.size(34.dp))
    }
  }
}

/**
 *  یک حسابِ ذخیره‌شده در فهرستِ ورودِ سریع.
 *
 *  شناسه چپ‌به‌راست نوشته می‌شود: شماره و ایمیل در صفحهٔ راست‌به‌چپ
 *  وارونه دیده می‌شوند و کاربر فکر می‌کند حسابِ دیگری است.
 */
@Composable
private fun SavedLoginRow(
  entry: SavedLogins.Entry,
  onPick: () -> Unit,
  onForget: () -> Unit,
) {
  Row(
    Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(20.dp))
      .background(Shop.colors.surface2)
      .clickable(onClick = onPick)
      .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
      Icons.Filled.Person,
      contentDescription = null,
      tint = Shop.colors.muted,
      modifier = Modifier.size(18.dp),
    )
    Spacer(Modifier.width(8.dp))
    Column(Modifier.weight(1f)) {
      CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Text(
          entry.identifier,
          style = MaterialTheme.typography.bodyMedium,
          color = Shop.colors.text,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      if (entry.shop.isNotBlank()) {
        Text(
          "فروشگاه: ${entry.shop}",
          style = MaterialTheme.typography.labelSmall,
          color = Shop.colors.muted2,
          maxLines = 1,
        )
      }
    }
    IconButton(onClick = onForget, modifier = Modifier.size(32.dp)) {
      Icon(
        Icons.Filled.Close,
        contentDescription = "حذف این حساب از فهرست",
        tint = Shop.colors.muted2,
        modifier = Modifier.size(15.dp),
      )
    }
  }
}
