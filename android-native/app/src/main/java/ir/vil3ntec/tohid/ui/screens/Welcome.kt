package ir.vil3ntec.tohid.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Outline
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
import androidx.compose.ui.unit.Density
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
  var showServer by rememberSaveable { mutableStateOf(state.serverUrl.isBlank()) }

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

  Box(Modifier.fillMaxSize().background(Shop.colors.bg)) {
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

      Column(
        Modifier
          .fillMaxWidth()
          .widthIn(max = 520.dp)
          .align(Alignment.CenterHorizontally)
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

        /* --------------------- حساب‌های این گوشی --------------------- */
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

        Spacer(Modifier.height(18.dp))
        HorizontalDivider(color = Shop.colors.fieldBorder.copy(alpha = 0.6f))
        Spacer(Modifier.height(16.dp))

        /* ---------------------- ادامه بدون حساب ---------------------- */
        OutlinedPill(
          text = "ادامه بدون حساب",
          detail = "همه‌چیز روی همین گوشی کار می‌کند",
          icon = Icons.Filled.ArrowBack,
          onClick = onDone,
        )

        Spacer(Modifier.height(12.dp))

        /* ------------------------- کد شاگرد ------------------------- */
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          PillField(
            value = staffCode,
            onValueChange = { staffCode = it.uppercase(); error = null },
            placeholder = "کد شاگرد",
            icon = Icons.Filled.Storefront,
            ltr = true,
            modifier = Modifier.weight(1f),
          )
          TextButton(
            enabled = !busy && staffCode.isNotBlank(),
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
          ) { Text("پیوستن", color = Shop.colors.primary) }
        }
        Text(
          "کدی که صاحب دکان از تنظیمات برنامه‌اش به شما می‌دهد.",
          style = MaterialTheme.typography.labelSmall,
          color = Shop.colors.muted2,
          modifier = Modifier.padding(top = 6.dp),
        )

        /* ------------------------- آدرس سرور ------------------------- */
        Spacer(Modifier.height(14.dp))
        TextButton(onClick = { showServer = !showServer }) {
          Icon(
            Icons.Filled.Tune,
            contentDescription = null,
            tint = Shop.colors.muted,
            modifier = Modifier.size(16.dp),
          )
          Spacer(Modifier.width(6.dp))
          Text(
            if (showServer) "بستن تنظیم سرور" else "تنظیم سرور",
            style = MaterialTheme.typography.labelMedium,
            color = Shop.colors.muted,
          )
        }
        AnimatedVisibility(visible = showServer, enter = fadeIn() + expandVertically(), exit = shrinkVertically()) {
          Column {
            PillField(
              value = server,
              onValueChange = { server = it; error = null },
              placeholder = "https://…",
              icon = Icons.Filled.Tune,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
              ltr = true,
            )
            Text(
              "بدون آدرس سرور هم برنامه کامل کار می‌کند؛ سرور فقط برای حساب، اشتراک و همگام‌سازی است.",
              style = MaterialTheme.typography.labelSmall,
              color = Shop.colors.muted2,
              modifier = Modifier.padding(top = 6.dp),
            )
          }
        }

        Spacer(Modifier.height(28.dp))
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
@Composable
private fun GradientHeader(title: String, subtitle: String) {
  val colors = Shop.colors
  Box(
    Modifier
      .fillMaxWidth()
      .height(228.dp)
      .clip(WaveBottom())
      .background(Brush.linearGradient(listOf(colors.primary, colors.primaryDark)))
      .windowInsetsPadding(WindowInsets.statusBars),
  ) {
    Column(
      Modifier
        .align(Alignment.TopStart)
        .padding(start = 24.dp, end = 24.dp, top = 34.dp),
    ) {
      BrandMark()
      Spacer(Modifier.height(14.dp))
      Text(
        title,
        style = MaterialTheme.typography.displaySmall,
        color = Color.White,
        fontWeight = FontWeight.Bold,
      )
      Spacer(Modifier.height(4.dp))
      Text(
        subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = Color.White.copy(alpha = 0.86f),
      )
    }
  }
}

/** لبهٔ پایینِ موجی — یک منحنی از راست به چپ */
private class WaveBottom : Shape {
  override fun createOutline(
    size: androidx.compose.ui.geometry.Size,
    layoutDirection: LayoutDirection,
    density: Density,
  ): Outline {
    val dip = size.height * 0.16f
    val path = Path().apply {
      moveTo(0f, 0f)
      lineTo(size.width, 0f)
      lineTo(size.width, size.height - dip)
      // دو کمانِ پشتِ سرِ هم: یکی پایین می‌رود، یکی بالا
      cubicTo(
        size.width * 0.72f, size.height + dip * 0.55f,
        size.width * 0.30f, size.height - dip * 1.9f,
        0f, size.height - dip * 0.25f,
      )
      close()
    }
    return Outline.Generic(path)
  }
}

/** انتخابِ راهِ ورود — شماره یا ایمیل */
@Composable
private fun ChannelSwitch(channel: String, onPick: (String) -> Unit) {
  val colors = Shop.colors
  Row(
    Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(26.dp))
      .background(colors.surface2)
      .padding(4.dp),
  ) {
    ChannelTab("شماره موبایل", Icons.Filled.PhoneAndroid, channel == "phone", Modifier.weight(1f)) {
      onPick("phone")
    }
    ChannelTab("ایمیل", Icons.Filled.AlternateEmail, channel == "email", Modifier.weight(1f)) {
      onPick("email")
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
  val colors = Shop.colors
  Row(
    modifier
      .clip(RoundedCornerShape(22.dp))
      .background(if (active) colors.primary else Color.Transparent)
      .clickable(onClick = onClick)
      .padding(vertical = 11.dp),
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
      icon,
      contentDescription = null,
      tint = if (active) Color.White else colors.muted,
      modifier = Modifier.size(17.dp),
    )
    Spacer(Modifier.width(6.dp))
    Text(
      text,
      style = MaterialTheme.typography.labelLarge,
      color = if (active) Color.White else colors.muted,
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
  val body: @Composable () -> Unit = {
    OutlinedTextField(
      value = value,
      onValueChange = onValueChange,
      placeholder = { Text(placeholder, color = colors.muted2) },
      leadingIcon = {
        Icon(icon, contentDescription = null, tint = colors.muted, modifier = Modifier.size(19.dp))
      },
      trailingIcon = trailing,
      singleLine = true,
      enabled = enabled,
      shape = RoundedCornerShape(26.dp),
      keyboardOptions = keyboardOptions,
      visualTransformation = visual,
      colors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = colors.fieldBg,
        unfocusedContainerColor = colors.fieldBg,
        disabledContainerColor = colors.fieldBg,
        focusedBorderColor = colors.primary,
        unfocusedBorderColor = colors.fieldBorder,
        disabledBorderColor = colors.fieldBorder,
        focusedTextColor = colors.text,
        unfocusedTextColor = colors.text,
        disabledTextColor = colors.muted,
      ),
      modifier = Modifier.fillMaxWidth(),
    )
  }
  Box(modifier.fillMaxWidth()) {
    if (ltr) {
      CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) { body() }
    } else {
      body()
    }
  }
}

/** دکمهٔ اصلی — قرصِ رنگی با شیب */
@Composable
private fun GradientButton(
  text: String,
  enabled: Boolean,
  busy: Boolean,
  onClick: () -> Unit,
) {
  val colors = Shop.colors
  val brush = if (enabled) {
    Brush.linearGradient(listOf(colors.primary, colors.primaryDark))
  } else {
    Brush.linearGradient(listOf(colors.surface2, colors.surface2))
  }
  Box(
    Modifier
      .fillMaxWidth()
      .height(54.dp)
      .clip(RoundedCornerShape(27.dp))
      .background(brush)
      .clickable(enabled = enabled && !busy, onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    if (busy) {
      CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
    } else {
      Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = if (enabled) Color.White else colors.muted2,
      )
    }
  }
}

/** دکمهٔ خطی — «ادامه بدون حساب» */
@Composable
private fun OutlinedPill(
  text: String,
  detail: String,
  icon: ImageVector,
  onClick: () -> Unit,
) {
  val colors = Shop.colors
  Row(
    Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(26.dp))
      .background(colors.surface)
      .border(1.dp, colors.fieldBorder, RoundedCornerShape(26.dp))
      .clickable(onClick = onClick)
      .padding(horizontal = 16.dp, vertical = 13.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      Modifier.size(34.dp).clip(RoundedCornerShape(17.dp)).background(colors.successTint),
      contentAlignment = Alignment.Center,
    ) {
      Icon(icon, contentDescription = null, tint = colors.success, modifier = Modifier.size(17.dp))
    }
    Spacer(Modifier.width(12.dp))
    Column(Modifier.weight(1f)) {
      Text(text, style = MaterialTheme.typography.labelLarge, color = colors.text)
      Text(detail, style = MaterialTheme.typography.labelSmall, color = colors.muted)
    }
  }
}

/** نشانِ برنامه با همان تپشِ ملایمِ نسخهٔ وب */
@Composable
private fun BrandMark() {
  val pulse = rememberInfiniteTransition(label = "brand")
  val glow by pulse.animateFloat(
    initialValue = 0.45f,
    targetValue = 0.9f,
    animationSpec = infiniteRepeatable(
      tween(if (Motion.enabled) 1800 else 1, easing = LinearEasing),
      RepeatMode.Reverse,
    ),
    label = "glow",
  )
  Box(contentAlignment = Alignment.Center) {
    Box(
      Modifier
        .size(66.dp)
        .alpha(glow * 0.3f)
        .clip(RoundedCornerShape(22.dp))
        .background(Color.White)
    )
    Box(
      Modifier
        .size(52.dp)
        .clip(RoundedCornerShape(18.dp))
        .background(Color.White.copy(alpha = 0.18f))
        .border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(18.dp)),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        Icons.Filled.Storefront,
        contentDescription = null,
        tint = Color.White,
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
