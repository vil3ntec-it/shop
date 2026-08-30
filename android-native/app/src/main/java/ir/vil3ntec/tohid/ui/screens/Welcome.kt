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
import androidx.compose.ui.layout.layout
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
import ir.vil3ntec.tohid.sync.SavedLogins
import ir.vil3ntec.tohid.sync.ServerClient
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
private val INK = Color(0xFF17255A)
private val INK_SOFT = Color(0xFF5C6B90)
private val BLUE = Color(0xFF2563C9)
private val BLUE_DEEP = Color(0xFF1B4FA8)
private val FIELD_LINE = Color(0xFFE1E8F5)

/** طلای همان صفحهٔ اشتراک — تا ورود و اشتراک یک زبان داشته باشند */
private val GOLD_GLOW = Color(0xFFF6C93F)
private val GOLD_RING = Color(0xFFFFE9A8)

@Composable
fun WelcomeScreen(onDone: () -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val state = remember { SyncStore(context) }

  // phone | email
  var channel by rememberSaveable { mutableStateOf("phone") }
  // ایمیل: ورود یا ساختِ حساب
  var emailMode by rememberSaveable { mutableStateOf("login") }

  var server by rememberSaveable { mutableStateOf(state.serverUrl) }
  var showMore by rememberSaveable { mutableStateOf(state.serverUrl.isBlank()) }

  var name by rememberSaveable { mutableStateOf("") }
  var phone by rememberSaveable { mutableStateOf("") }
  var code by rememberSaveable { mutableStateOf("") }
  var codeSent by rememberSaveable { mutableStateOf(false) }

  var email by rememberSaveable { mutableStateOf("") }
  var password by rememberSaveable { mutableStateOf("") }
  var showPassword by rememberSaveable { mutableStateOf(false) }

  var staffCode by rememberSaveable { mutableStateOf("") }
  var busy by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }
  var note by remember { mutableStateOf<String?>(null) }
  var saved by remember { mutableStateOf(SavedLogins.read(context)) }

  fun fail(e: Throwable) {
    error = (e as? ServerClient.ServerError)?.message ?: "ارتباط با سرور برقرار نشد"
  }

  fun finish(identifier: String, session: ServerClient.Session) {
    state.serverUrl = server.trim().trimEnd('/')
    state.accessToken = session.accessToken
    state.refreshToken = session.refreshToken
    state.accountName = session.name.ifBlank { name.trim() }
    SavedLogins.remember(context, identifier, session.name.ifBlank { name.trim() })
    onDone()
  }

  val ready = server.isNotBlank()

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
        title = if (channel == "phone") "خوش آمدید" else
          if (emailMode == "login") "خوش آمدید" else "حساب تازه",
        subtitle = if (channel == "phone") "با شمارهٔ خودتان وارد شوید — رمز لازم نیست"
        else "با ایمیل و رمز وارد شوید",
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

        /* ------------------------ راهِ ورود ------------------------ */
        ChannelSwitch(
          channel = channel,
          onPick = { channel = it; error = null; note = null; codeSent = false; code = "" },
        )

        Spacer(Modifier.height(18.dp))

        // نام — در هر دو راه، چون حسابِ بی‌نام بعداً فقط یک شماره است
        PillField(
          value = name,
          onValueChange = { name = it; error = null },
          placeholder = "نام شما",
          icon = Icons.Filled.Person,
        )
        Spacer(Modifier.height(12.dp))

        if (channel == "phone") {
          PillField(
            value = phone,
            onValueChange = { phone = it; error = null },
            placeholder = "شماره موبایل",
            icon = Icons.Filled.PhoneAndroid,
            keyboardOptions = KeyboardOptions(
              keyboardType = KeyboardType.Phone,
              imeAction = ImeAction.Next,
            ),
            ltr = true,
            enabled = !codeSent,
          )

          AnimatedVisibility(
            visible = codeSent,
            enter = fadeIn() + expandVertically(),
            exit = shrinkVertically(),
          ) {
            Column {
              Spacer(Modifier.height(12.dp))
              PillField(
                value = code,
                // فقط شش رقم؛ حرف در کدِ پیامکی معنی ندارد
                onValueChange = { code = it.filter { c -> c.isDigit() }.take(6); error = null },
                placeholder = "کد شش‌رقمی",
                icon = Icons.Filled.Lock,
                keyboardOptions = KeyboardOptions(
                  keyboardType = KeyboardType.NumberPassword,
                  imeAction = ImeAction.Done,
                ),
                ltr = true,
              )
              Spacer(Modifier.height(6.dp))
              Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { codeSent = false; code = ""; note = null }) {
                  Text("شماره را عوض می‌کنم", style = MaterialTheme.typography.labelMedium, color = Shop.colors.muted)
                }
                TextButton(
                  enabled = !busy,
                  onClick = {
                    busy = true; error = null
                    scope.launch {
                      runCatching { ServerClient(server.trim().trimEnd('/')).otpRequest(phone.trim()) }
                        .onSuccess { note = "کد دوباره فرستاده شد" }
                        .onFailure { fail(it) }
                      busy = false
                    }
                  },
                ) {
                  Text("ارسال دوبارهٔ کد", style = MaterialTheme.typography.labelMedium, color = Shop.colors.primary)
                }
              }
            }
          }
        } else {
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
        val label = when {
          channel == "phone" && !codeSent -> "ارسال کد"
          channel == "phone" -> "ورود"
          emailMode == "register" -> "ساخت حساب"
          else -> "ورود به حساب"
        }
        val can = ready && !busy && name.isNotBlank() && when {
          channel == "phone" && !codeSent -> phone.isNotBlank()
          channel == "phone" -> code.length == 6
          else -> email.isNotBlank() && password.isNotBlank()
        }

        GradientButton(text = label, enabled = can, busy = busy) {
          busy = true; error = null; note = null
          val base = server.trim().trimEnd('/')
          scope.launch {
            val client = ServerClient(base)
            when {
              channel == "phone" && !codeSent ->
                runCatching { client.otpRequest(phone.trim()) }
                  .onSuccess {
                    state.serverUrl = base
                    codeSent = true
                    note = "کد به شمارهٔ شما فرستاده شد"
                  }
                  .onFailure { fail(it) }

              channel == "phone" ->
                runCatching { client.otpVerify(phone.trim(), code, name.trim()) }
                  .onSuccess { finish(phone.trim(), it) }
                  .onFailure { fail(it) }

              emailMode == "register" ->
                runCatching { client.register(name.trim(), email.trim(), "", password) }
                  .onSuccess {
                    state.serverUrl = base
                    note = "حساب ساخته شد — حالا وارد شوید"
                    emailMode = "login"
                    password = ""
                  }
                  .onFailure { fail(it) }

              else ->
                runCatching { client.login(email.trim(), password) }
                  .onSuccess { finish(email.trim(), it) }
                  .onFailure { fail(it) }
            }
            busy = false
          }
        }

        if (channel == "email") {
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
            TextButton(
              onClick = {
                note = "برای بازیابی رمز با پشتیبانی تماس بگیرید. اطلاعات دکان شما روی همین گوشی محفوظ است."
                error = null
              },
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text(
                "رمز عبور را فراموش کرده‌اید؟",
                color = Shop.colors.muted,
                style = MaterialTheme.typography.labelMedium,
              )
            }
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
            color = INK_SOFT,
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

        // جداکنندهٔ نازک با «یا» وسطش — مرزِ بینِ راهِ اصلی و کارهای فنی
        Spacer(Modifier.height(6.dp))
        Row(
          Modifier.fillMaxWidth().padding(horizontal = 30.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          HorizontalDivider(Modifier.weight(1f), color = FIELD_LINE)
          Text(
            "یا",
            style = MaterialTheme.typography.labelMedium,
            color = INK_SOFT,
            modifier = Modifier.padding(horizontal = 12.dp),
          )
          HorizontalDivider(Modifier.weight(1f), color = FIELD_LINE)
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
                if (entry.identifier.contains("@")) {
                  channel = "email"; emailMode = "login"; email = entry.identifier
                } else {
                  channel = "phone"; phone = entry.identifier; codeSent = false; code = ""
                }
                if (name.isBlank()) name = entry.shop
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
              tint = INK_SOFT,
              modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
              if (showMore) "بستن گزینه‌های بیشتر" else "کد شاگرد و تنظیم سرور",
              style = MaterialTheme.typography.labelMedium,
              color = INK_SOFT,
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
              placeholder = "کد شاگرد — SHG-XXXXX-XXXXX-XXXXX",
              icon = Icons.Filled.Storefront,
              ltr = true,
              trailing = {
                TextButton(
                  enabled = !busy && staffCode.isNotBlank(),
                  contentPadding = PaddingValues(horizontal = 8.dp),
                  onClick = {
                    val entered = staffCode.trim().uppercase()
                    if (!Regex("^SHG-[A-Z0-9]{5}(-[A-Z0-9]{5}){2}$").matches(entered)) {
                      error = "این کد درست نیست. کد باید مثل SHG-XXXXX-XXXXX-XXXXX باشد."
                      return@TextButton
                    }
                    val token = state.accessToken
                    if (token.isNullOrBlank()) {
                      error = "برای پیوستن به دکان، اول وارد حساب خود شوید."
                      return@TextButton
                    }
                    busy = true; error = null
                    scope.launch {
                      runCatching { ServerClient(state.serverUrl).joinShop(token, entered) }
                        .onSuccess { onDone() }
                        .onFailure { fail(it) }
                      busy = false
                    }
                  },
                ) { Text("پیوستن", color = Shop.colors.primary, style = MaterialTheme.typography.labelMedium) }
              },
            )
            Text(
              "کدی که صاحب دکان از تنظیمات برنامه‌اش به شما می‌دهد.",
              style = MaterialTheme.typography.labelSmall,
              color = Shop.colors.muted2,
              modifier = Modifier.padding(top = 6.dp, start = 4.dp),
            )

            Spacer(Modifier.height(12.dp))
            PillField(
              value = server,
              onValueChange = { server = it; error = null },
              placeholder = "آدرس سرور — https://…",
              icon = Icons.Filled.Tune,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
              ltr = true,
            )
            Text(
              "بدون آدرس سرور هم برنامه کامل کار می‌کند؛ سرور فقط برای حساب، اشتراک و همگام‌سازی است.",
              style = MaterialTheme.typography.labelSmall,
              color = Shop.colors.muted2,
              modifier = Modifier.padding(top = 6.dp, start = 4.dp),
            )
          }
        }

        Spacer(Modifier.height(28.dp))
      }
      }
    }
  }
}

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
        color = INK,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
      )
      Spacer(Modifier.height(10.dp))
      Text(
        subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = INK_SOFT,
        textAlign = TextAlign.Center,
      )
    }
  }
}

/**
 *  انتخابِ راهِ ورود — شماره یا ایمیل.
 *
 *  قرصِ انتخاب‌شده **سُر می‌خورد**، ظاهر و ناپدید نمی‌شود: حرکت می‌گوید
 *  که این دو، دو حالتِ یک چیزند، نه دو دکمهٔ جدا.
 */
@Composable
private fun ChannelSwitch(channel: String, onPick: (String) -> Unit) {
  val phone = channel == "phone"
  val slide by animateFloatAsState(
    targetValue = if (phone) 0f else 1f,
    animationSpec = tween(if (Motion.enabled) 260 else 0, easing = FastOutSlowInEasing),
    label = "channelSlide",
  )

  Box(
    Modifier
      .fillMaxWidth()
      .shadow(6.dp, RoundedCornerShape(30.dp), ambientColor = BLUE, spotColor = BLUE)
      .clip(RoundedCornerShape(30.dp))
      .background(Color.White)
      .padding(5.dp)
  ) {
    // قرصِ آبی، پشتِ متن‌ها، روی نیمهٔ انتخاب‌شده
    Box(
      Modifier
        .fillMaxWidth(0.5f)
        .align(Alignment.CenterStart)
        .offsetByFraction(slide)
        .clip(RoundedCornerShape(26.dp))
        .background(Brush.horizontalGradient(listOf(BLUE_DEEP, BLUE)))
        .padding(vertical = 13.dp)
    ) {}

    Row(Modifier.fillMaxWidth()) {
      ChannelTab("شماره موبایل", Icons.Filled.PhoneAndroid, phone, Modifier.weight(1f)) {
        onPick("phone")
      }
      ChannelTab("ایمیل", Icons.Filled.AlternateEmail, !phone, Modifier.weight(1f)) {
        onPick("email")
      }
    }
  }
}

/**
 *  جابه‌جاییِ قرص، به‌نسبتِ پهنای خودش.
 *
 *  با `offset` بر حسبِ نقطه، روی هر پهنای صفحه‌ای جای دیگری می‌ایستاد؛
 *  اینجا از پهنای واقعیِ خودِ قرص خوانده می‌شود.
 */
private fun Modifier.offsetByFraction(fraction: Float): Modifier =
  this.layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height) {
      placeable.placeRelative((placeable.width * fraction).toInt(), 0)
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
    targetValue = if (active) Color.White else INK_SOFT,
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
private fun PillField(
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
    targetValue = if (focused) BLUE else FIELD_LINE,
    animationSpec = tween(if (Motion.enabled) 200 else 0),
    label = "pillLine",
  )
  val lift by animateDpAsState(
    targetValue = if (focused) 9.dp else 4.dp,
    animationSpec = tween(if (Motion.enabled) 200 else 0),
    label = "pillLift",
  )

  val face = if (dark) colors.surface else Color.White
  val ink = if (dark) colors.text else INK
  val hint = if (dark) colors.muted2 else INK_SOFT

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
            color = if (enabled) ink else hint,
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
        else Brush.horizontalGradient(listOf(FIELD_LINE, FIELD_LINE))
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
        color = if (live) Color.White else INK_SOFT,
        fontWeight = FontWeight.Bold,
      )
    }
  }
}

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
    // شیشه: سطحِ نیمه‌شفاف با یک لبهٔ روشن، نه یک مربعِ توپر
    Box(
      Modifier
        .size(54.dp)
        .clip(RoundedCornerShape(19.dp))
        .background(colors.surface2.copy(alpha = 0.75f))
        .border(1.dp, GOLD_GLOW.copy(alpha = 0.45f), RoundedCornerShape(19.dp)),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        Icons.Filled.Storefront,
        contentDescription = null,
        tint = colors.primary,
        modifier = Modifier.size(26.dp),
      )
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
