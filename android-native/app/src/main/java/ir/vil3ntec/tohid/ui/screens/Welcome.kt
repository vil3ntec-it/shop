package ir.vil3ntec.tohid.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Storefront
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.sync.ServerClient
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material.icons.filled.Close
import ir.vil3ntec.tohid.sync.SavedLogins
import ir.vil3ntec.tohid.sync.SyncStore
import ir.vil3ntec.tohid.ui.theme.Radius
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.launch

/**
 *  صفحهٔ «خوش آمدید» — همان دروازهٔ ورودِ نسخهٔ وب.
 *
 *  ورود اختیاری است و همیشه هم بوده: بدون حساب، تمامِ برنامه روی همین
 *  گوشی کار می‌کند و دفتر دکان جایی نمی‌رود. حساب فقط برای همگام‌سازی بین
 *  گوشی‌ها و برای اشتراک لازم است. پس این صفحه هیچ‌وقت راه را نمی‌بندد؛
 *  «ادامه بدون حساب» همیشه هست.
 */
@Composable
fun WelcomeScreen(onDone: () -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val state = remember { SyncStore(context) }

  var mode by rememberSaveable { mutableStateOf("login") }   // login | register
  var server by rememberSaveable { mutableStateOf(state.serverUrl) }
  var identifier by rememberSaveable { mutableStateOf("") }
  var password by rememberSaveable { mutableStateOf("") }
  var showPassword by rememberSaveable { mutableStateOf(false) }
  var name by rememberSaveable { mutableStateOf("") }
  var email by rememberSaveable { mutableStateOf("") }
  var phone by rememberSaveable { mutableStateOf("") }
  var staffCode by rememberSaveable { mutableStateOf("") }
  var busy by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }
  var note by remember { mutableStateOf<String?>(null) }
  var saved by remember { mutableStateOf(SavedLogins.read(context)) }

  fun fail(e: Throwable) {
    error = (e as? ServerClient.ServerError)?.message ?: "ارتباط با سرور برقرار نشد"
  }

  Box(
    Modifier
      .fillMaxSize()
      .background(
        Brush.verticalGradient(
          listOf(Shop.colors.primaryTint, Shop.colors.bg, Shop.colors.successTint)
        )
      )
  ) {
    Column(
      Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(20.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      AnimatedVisibility(
        visible = true,
        enter = fadeIn(tween(500)) + slideInVertically(tween(500)) { it / 6 },
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          BrandMark()
          Spacer(Modifier.height(14.dp))
          Text(
            "خوش آمدید",
            style = MaterialTheme.typography.headlineMedium,
            color = Shop.colors.text,
            fontWeight = FontWeight.Bold,
          )
          Spacer(Modifier.height(4.dp))
          Text(
            "برای استفاده از توحید، وارد حساب خود شوید",
            style = MaterialTheme.typography.bodyMedium,
            color = Shop.colors.muted,
            textAlign = TextAlign.Center,
          )
        }
      }

      Spacer(Modifier.height(20.dp))

      Column(
        Modifier
          .fillMaxWidth()
          .widthIn(max = 460.dp)
          .clip(RoundedCornerShape(Radius.lg))
          .background(Shop.colors.bg)
          .border(1.dp, Shop.colors.border, RoundedCornerShape(Radius.lg))
          .padding(18.dp)
      ) {
        /* ---------------------- آدرس سرور ---------------------- */
        OutlinedTextField(
          value = server,
          onValueChange = { server = it; error = null },
          label = { Text("آدرس سرور") },
          placeholder = { Text("https://…") },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
          modifier = Modifier.fillMaxWidth(),
        )
        Text(
          "بدون آدرس سرور هم برنامه کامل کار می‌کند؛ سرور فقط برای حساب، اشتراک و همگام‌سازی است.",
          style = MaterialTheme.typography.labelSmall,
          color = Shop.colors.muted2,
          modifier = Modifier.padding(top = 6.dp),
        )

        Spacer(Modifier.height(14.dp))

        if (mode == "register") {
          OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("نام شما") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
          Spacer(Modifier.height(10.dp))
          Text(
            "ایمیل یا شماره موبایل — هرکدام را داشتید کافی است.",
            style = MaterialTheme.typography.labelSmall,
            color = Shop.colors.muted2,
          )
          Spacer(Modifier.height(6.dp))
          OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("ایمیل") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
          )
          Spacer(Modifier.height(10.dp))
          OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text("شماره موبایل") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
          )
        } else {
          // حساب‌هایی که قبلاً از این گوشی وارد شده‌اند — یک لمس، کادر پر
          if (saved.isNotEmpty()) {
            Text(
              "ورود سریع",
              style = MaterialTheme.typography.labelMedium,
              color = Shop.colors.muted,
              modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            saved.forEach { entry ->
              SavedLoginRow(
                entry = entry,
                onPick = { identifier = entry.identifier; error = null },
                onForget = {
                  SavedLogins.forget(context, entry.identifier)
                  saved = SavedLogins.read(context)
                },
              )
              Spacer(Modifier.height(6.dp))
            }
            Spacer(Modifier.height(6.dp))
          }

          OutlinedTextField(
            value = identifier,
            onValueChange = { identifier = it; error = null },
            label = { Text("شماره موبایل یا ایمیل") },
            leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
          )
        }

        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
          value = password,
          onValueChange = { password = it; error = null },
          label = { Text(if (mode == "register") "رمز عبور (حداقل ۸ نویسه)" else "رمز عبور") },
          singleLine = true,
          visualTransformation =
            if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
          trailingIcon = {
            IconButton(onClick = { showPassword = !showPassword }) {
              Icon(
                if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                contentDescription = if (showPassword) "پنهان کردن رمز" else "نمایش رمز",
                tint = Shop.colors.muted,
              )
            }
          },
          modifier = Modifier.fillMaxWidth(),
        )

        error?.let {
          Spacer(Modifier.height(8.dp))
          Text(it, style = MaterialTheme.typography.labelMedium, color = Shop.colors.danger)
        }
        note?.let {
          Spacer(Modifier.height(8.dp))
          Text(it, style = MaterialTheme.typography.labelMedium, color = Shop.colors.primary)
        }

        Spacer(Modifier.height(14.dp))
        Button(
          enabled = !busy && server.isNotBlank() && password.isNotBlank() &&
            (if (mode == "register") name.isNotBlank() && (email.isNotBlank() || phone.isNotBlank())
            else identifier.isNotBlank()),
          onClick = {
            busy = true
            error = null
            note = null
            val base = server.trim().trimEnd('/')
            scope.launch {
              val client = ServerClient(base)
              if (mode == "register") {
                runCatching { client.register(name.trim(), email.trim(), phone.trim(), password) }
                  .onSuccess {
                    state.serverUrl = base
                    note = "حساب ساخته شد — حالا وارد شوید"
                    mode = "login"
                    identifier = email.trim().ifBlank { phone.trim() }
                    password = ""
                  }
                  .onFailure { fail(it) }
              } else {
                runCatching { client.login(identifier.trim(), password) }
                  .onSuccess { session ->
                    state.serverUrl = base
                    state.accessToken = session.accessToken
                    state.refreshToken = session.refreshToken
                    state.accountName = session.name
                    SavedLogins.remember(context, identifier.trim(), session.name)
                    onDone()
                  }
                  .onFailure { fail(it) }
              }
              busy = false
            }
          },
          colors = ButtonDefaults.buttonColors(containerColor = Shop.colors.primary),
          modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
          if (busy) {
            CircularProgressIndicator(
              color = Color.White,
              strokeWidth = 2.dp,
              modifier = Modifier.size(20.dp),
            )
          } else {
            Text(
              if (mode == "register") "ساخت حساب" else "ورود به حساب",
              style = MaterialTheme.typography.titleSmall,
              color = Color.White,
            )
          }
        }

        if (mode == "login") {
          TextButton(
            onClick = {
              note = "برای بازیابی رمز، با پشتیبانی تماس بگیرید. اطلاعات دکان شما روی همین گوشی محفوظ است."
              error = null
            },
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text("رمز عبور را فراموش کرده‌اید؟", color = Shop.colors.muted, style = MaterialTheme.typography.labelMedium)
          }
        }

        Spacer(Modifier.height(6.dp))
        TextButton(
          onClick = { mode = if (mode == "login") "register" else "login"; error = null },
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            if (mode == "login") "حساب ندارید؟ ثبت‌نام کنید" else "حساب دارم — برگرد به ورود",
            color = Shop.colors.primary,
          )
        }

        HorizontalDivider(Modifier.padding(vertical = 12.dp), color = Shop.colors.border)

        /* ---------------------- ادامه بدون حساب ---------------------- */
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          RoleCard(
            icon = Icons.Filled.Storefront,
            title = "فروشنده",
            detail = "ساخت حساب دکان",
            tint = Shop.colors.primary,
            modifier = Modifier.weight(1f),
          ) { mode = "register" }
          RoleCard(
            icon = Icons.Filled.ArrowBack,
            title = "ادامه بدون حساب",
            detail = "همه‌چیز روی همین گوشی",
            tint = Shop.colors.success,
            modifier = Modifier.weight(1f),
            onClick = onDone,
          )
        }

        /* ---------------------- کد شاگرد ---------------------- */
        HorizontalDivider(Modifier.padding(vertical = 14.dp), color = Shop.colors.border)
        Text(
          "شاگرد دکان هستید؟",
          style = MaterialTheme.typography.labelLarge,
          color = Shop.colors.text,
        )
        Spacer(Modifier.height(8.dp))
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          OutlinedTextField(
            value = staffCode,
            onValueChange = { staffCode = it.uppercase(); error = null },
            label = { Text("کد شاگرد") },
            placeholder = { Text("SHG-XXXXX-XXXXX-XXXXX") },
            singleLine = true,
            modifier = Modifier.weight(1f),
          )
          Button(
            enabled = !busy && staffCode.isNotBlank(),
            onClick = {
              val code = staffCode.trim().uppercase()
              if (!Regex("^SHG-[A-Z0-9]{5}(-[A-Z0-9]{5}){2}$").matches(code)) {
                error = "این کد درست نیست. کد باید مثل SHG-XXXXX-XXXXX-XXXXX باشد."
                return@Button
              }
              val token = state.accessToken
              if (token.isNullOrBlank()) {
                error = "برای پیوستن به دکان، اول وارد حساب خود شوید."
                return@Button
              }
              busy = true
              error = null
              scope.launch {
                runCatching { ServerClient(state.serverUrl).joinShop(token, code) }
                  .onSuccess { onDone() }
                  .onFailure { fail(it) }
                busy = false
              }
            },
            colors = ButtonDefaults.buttonColors(containerColor = Shop.colors.primary),
          ) { Text("ورود", color = Color.White) }
        }
        Text(
          "کدی که صاحب دکان از تنظیمات برنامه‌اش به شما می‌دهد.",
          style = MaterialTheme.typography.labelSmall,
          color = Shop.colors.muted2,
          modifier = Modifier.padding(top = 6.dp),
        )

        Spacer(Modifier.height(12.dp))
        Text(
          "ورود اختیاری است — بدون حساب هم می‌توانید از برنامه استفاده کنید.",
          style = MaterialTheme.typography.labelSmall,
          color = Shop.colors.muted2,
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxWidth(),
        )
      }

      Spacer(Modifier.height(24.dp))
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
    animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Reverse),
    label = "glow",
  )
  Box(contentAlignment = Alignment.Center) {
    Box(
      Modifier
        .size(84.dp)
        .alpha(glow * 0.35f)
        .clip(RoundedCornerShape(28.dp))
        .background(Shop.colors.primary)
    )
    Box(
      Modifier
        .size(66.dp)
        .clip(RoundedCornerShape(22.dp))
        .background(
          Brush.linearGradient(listOf(Shop.colors.primary, Shop.colors.primaryDark))
        ),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        Icons.Filled.Inventory2,
        contentDescription = null,
        tint = Color.White,
        modifier = Modifier.size(32.dp),
      )
    }
  }
}

@Composable
private fun RoleCard(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  title: String,
  detail: String,
  tint: Color,
  modifier: Modifier = Modifier,
  onClick: () -> Unit,
) {
  Column(
    modifier
      .clip(RoundedCornerShape(Radius.md))
      .background(Shop.colors.surface)
      .border(1.dp, Shop.colors.border, RoundedCornerShape(Radius.md))
      .clickable(onClick = onClick)
      .padding(vertical = 14.dp, horizontal = 10.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Box(
      Modifier.size(44.dp).clip(RoundedCornerShape(22.dp)).background(tint),
      contentAlignment = Alignment.Center,
    ) {
      Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
    }
    Spacer(Modifier.height(8.dp))
    Text(title, style = MaterialTheme.typography.labelLarge, color = Shop.colors.text)
    Text(
      detail,
      style = MaterialTheme.typography.labelSmall,
      color = Shop.colors.muted,
      textAlign = TextAlign.Center,
    )
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
      .clip(RoundedCornerShape(12.dp))
      .background(Shop.colors.surface2)
      .clickable(onClick = onPick)
      .padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
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
