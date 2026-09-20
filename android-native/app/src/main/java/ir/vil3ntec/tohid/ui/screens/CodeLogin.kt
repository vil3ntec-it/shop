package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.data.repo.CodeLoginRepository
import ir.vil3ntec.tohid.fa
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 *  ورود با ایمیل و کدِ شش‌رقمی — بندِ ۲۱٫۴.
 *
 *  دو پله: ایمیل، بعد شش خانه. خواسته‌های صریحِ بند:
 *  پرشِ خودکار · Paste · ارقامِ فارسی و انگلیسی · ارسالِ خودکار پس از
 *  رقمِ ششم · شمارشِ معکوسِ شصت ثانیه.
 *
 *  ⛔ **کادرِ نشانیِ سرور این‌جا نیست و نباید بیاید.** نشانی در
 *  `BuildConfig.API_BASE` قفل است و `AppConfig` مقدارِ روی گوشی را
 *  نادیده می‌گیرد — قاعدهٔ صاحبِ مخزن، بالاتر از متنِ پرامپت. شرح در
 *  `docs/SYNC-CLIENT-fa.md`.
 *
 *  ⚠️ **خانه‌ها در قابِ چپ‌به‌راست‌اند** حتی در صفحهٔ راست‌به‌چپ: کدِ
 *  شش‌رقمی از چپ خوانده می‌شود و برعکسش کاربر را گیج می‌کند.
 */
@Composable
fun CodeLoginSheet(onClose: () -> Unit, onSignedIn: () -> Unit) {
  val context = LocalContext.current
  val repo = remember { Backend.codeLogin(context) }
  val scope = rememberCoroutineScope()

  var email by remember { mutableStateOf(TextFieldValue("")) }
  var requestId by remember { mutableStateOf("") }
  var masked by remember { mutableStateOf("") }
  var digits by remember { mutableStateOf(List(6) { "" }) }
  var message by remember { mutableStateOf("") }
  var bad by remember { mutableStateOf(false) }
  var busy by remember { mutableStateOf(false) }
  var resendAt by remember { mutableStateOf(0L) }
  var left by remember { mutableStateOf(0) }

  val focus = remember { List(6) { FocusRequester() } }

  //  شمارشِ معکوسِ شصت ثانیه
  LaunchedEffect(resendAt) {
    while (resendAt > 0) {
      left = ((resendAt - System.currentTimeMillis()) / 1000).toInt().coerceAtLeast(0)
      if (left == 0) break
      delay(500)
    }
  }

  fun say(text: String, isBad: Boolean = false) { message = text; bad = isBad }

  suspend fun submit() {
    val code = digits.joinToString("")
    if (code.length != 6) { say("شش رقم را کامل بنویسید.", true); return }
    busy = true
    say("در حالِ بررسی…")
    val out = repo.verify(
      requestId = requestId,
      code = code,
      deviceId = ir.vil3ntec.tohid.sync.SyncStore(context).deviceUid,
      deviceName = android.os.Build.MODEL ?: "گوشی",
    )
    busy = false
    out.onSuccess { session ->
      //  ⛔ کلیدهای Sync به شناسهٔ همین حساب بسته می‌شوند، **پیش از**
      //  اولین همگام‌سازی. بی این، سایهٔ حسابِ قبلیِ همین گوشی
      //  ردیف‌هایش را داخلِ دکانِ این یکی می‌فرستاد.
      if (session.userId.isNotBlank()) {
        ir.vil3ntec.tohid.sync.v1.SyncStateV1.setCurrentAccount(context, session.userId)
      }
      ir.vil3ntec.tohid.sync.v1.SyncV1Engine.of(context).rebind()
      ir.vil3ntec.tohid.sync.v1.SyncV1Worker.now(context)
      say("وارد شدید.")
      onSignedIn()
    }.onFailure { failure ->
      //  کد که غلط بود، خانه‌ها خالی می‌شوند — وگرنه کاربر باید
      //  شش بار Backspace بزند
      digits = List(6) { "" }
      say(failure.userMessage, true)
      runCatching { focus[0].requestFocus() }
    }
  }

  /** چند رقم را از یک جا (Paste یا صفحه‌کلیدِ فارسی) پخش می‌کند. */
  fun spread(raw: String, from: Int) {
    val clean = CodeLoginRepository.digitsOnly(raw)
    if (clean.isEmpty()) return
    val next = digits.toMutableList()
    var at = from
    for (ch in clean) {
      if (at > 5) break
      next[at] = ch.toString()
      at++
    }
    digits = next
    runCatching { focus[at.coerceAtMost(5)].requestFocus() }
    if (next.all { it.isNotEmpty() }) scope.launch { submit() }
  }

  Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 28.dp)) {
    Text("ورود با ایمیل", style = MaterialTheme.typography.titleMedium, color = Shop.colors.text)
    Spacer(Modifier.height(8.dp))
    Text(
      "ایمیل‌تان را بنویسید؛ یک کدِ شش‌رقمی برایتان می‌فرستیم. همین ایمیل " +
        "روی گوشی و روی سایت یک حساب است.",
      style = MaterialTheme.typography.labelMedium,
      color = Shop.colors.muted,
    )
    Spacer(Modifier.height(14.dp))

    if (requestId.isBlank()) {
      CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        OutlinedTextField(
          value = email,
          onValueChange = { email = it },
          singleLine = true,
          label = { Text("you@example.com") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
          modifier = Modifier.fillMaxWidth(),
        )
      }
      Spacer(Modifier.height(12.dp))
      Button(
        onClick = {
          val value = email.text.trim()
          if (!value.contains('@') || !value.contains('.')) { say("ایمیل درست نیست.", true); return@Button }
          scope.launch {
            busy = true
            say("در حالِ فرستادن…")
            val out = repo.requestCode(value, android.os.Build.MODEL ?: "گوشی")
            busy = false
            out.onSuccess { r ->
              requestId = r.requestId
              masked = r.maskedEmail.ifBlank { value }
              resendAt = System.currentTimeMillis() + r.resendAfter * 1000L
              say("")
              runCatching { focus[0].requestFocus() }
              //  «ایمیل رفت یا نرفت» — چند بار می‌پرسیم و بعد رها
              repeat(6) {
                delay(2500)
                if (requestId.isBlank()) return@repeat
                repo.deliveryStatus(requestId).onSuccess { st ->
                  when (st.state) {
                    "failed" -> say("ایمیل فرستاده نشد (${st.reason}). دوباره تلاش کنید.", true)
                    "sent" -> say("ایمیل رفت.")
                  }
                }
              }
            }.onFailure { say(it.userMessage, true) }
          }
        },
        enabled = !busy,
        modifier = Modifier.fillMaxWidth().height(48.dp),
      ) { Text("فرستادنِ کد") }
    } else {
      Text(
        "کد به $masked فرستاده شد. شش رقم را بنویسید.",
        style = MaterialTheme.typography.labelMedium,
        color = Shop.colors.muted,
      )
      Spacer(Modifier.height(10.dp))

      CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
          Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          for (i in 0..5) {
            BasicTextField(
              value = TextFieldValue(digits[i], selection = androidx.compose.ui.text.TextRange(digits[i].length)),
              onValueChange = { v -> spread(v.text, i) },
              singleLine = true,
              enabled = !busy,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
              textStyle = TextStyle(
                color = Shop.colors.text,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
              ),
              modifier = Modifier
                .weight(1f)
                .height(56.dp)
                .focusRequester(focus[i])
                .clip(RoundedCornerShape(14.dp))
                .background(Shop.colors.surface2)
                .border(
                  1.5.dp,
                  if (digits[i].isNotEmpty()) Shop.colors.primary else Shop.colors.fieldBorder,
                  RoundedCornerShape(14.dp),
                ),
              decorationBox = { inner ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { inner() }
              },
            )
          }
        }
      }

      Spacer(Modifier.height(10.dp))
      Text(
        if (left > 0) "تا فرستادنِ دوبارهٔ کد: ${left.fa()} ثانیه"
        else "کد نیامد؟ پوشهٔ Spam را هم ببینید.",
        style = MaterialTheme.typography.labelSmall,
        color = Shop.colors.muted2,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
      )
      TextButton(
        onClick = {
          scope.launch {
            repo.requestCode(email.text.trim(), android.os.Build.MODEL ?: "گوشی")
              .onSuccess { resendAt = System.currentTimeMillis() + it.resendAfter * 1000L; say("کد دوباره فرستاده شد.") }
              .onFailure { say(it.userMessage, true) }
          }
        },
        enabled = left == 0 && !busy,
        modifier = Modifier.fillMaxWidth(),
      ) { Text("فرستادنِ دوبارهٔ کد") }

      Button(
        onClick = { scope.launch { submit() } },
        enabled = !busy,
        modifier = Modifier.fillMaxWidth().height(48.dp),
      ) { Text("ورود") }

      TextButton(
        onClick = { requestId = ""; digits = List(6) { "" }; say("") },
        modifier = Modifier.fillMaxWidth(),
      ) { Text("ایمیلِ دیگر") }
    }

    if (message.isNotBlank()) {
      Spacer(Modifier.height(8.dp))
      Text(
        message,
        style = MaterialTheme.typography.labelMedium,
        color = if (bad) Shop.colors.danger else Shop.colors.muted,
      )
    }

    Spacer(Modifier.height(6.dp))
    TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
      Text("بعداً — فعلاً بی حساب ادامه می‌دهم", color = Shop.colors.muted)
    }
  }
}
