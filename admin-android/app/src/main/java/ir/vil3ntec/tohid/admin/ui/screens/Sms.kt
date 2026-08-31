package ir.vil3ntec.tohid.admin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.admin.net.AdminApi
import ir.vil3ntec.tohid.admin.net.Session
import ir.vil3ntec.tohid.admin.ui.*
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 *  سرویس پیامک — از همین‌جا، نه با دست بردن در فایلِ سرور.
 *
 *  تنظیمات روی سرور و در دیتابیس می‌نشیند، نه در این گوشی و نه در
 *  برنامهٔ مشتری. مسیرش این است:
 *
 *      این صفحه ──► سرور ──► دیتابیس
 *                    │
 *      برنامهٔ مشتری ─┘  (کد می‌خواهد؛ سرور می‌فرستد)
 *
 *  کلیدِ سرویس هرگز کامل به این صفحه نمی‌آید. سرور فقط چهار رقمِ آخرش
 *  را می‌دهد، و اگر مدیر چیزی ننویسد همان کلیدِ قبلی سرِ جایش می‌ماند.
 */
@Composable
fun SmsScreen(session: Session) {
  val c = Admin.colors
  val scope = rememberCoroutineScope()

  var provider by rememberSaveable { mutableStateOf("sms") }
  var url by rememberSaveable { mutableStateOf("") }
  var method by rememberSaveable { mutableStateOf("POST") }
  var sender by rememberSaveable { mutableStateOf("") }
  var headers by rememberSaveable { mutableStateOf("") }
  var body by rememberSaveable { mutableStateOf("") }
  var template by rememberSaveable { mutableStateOf("") }
  var key by rememberSaveable { mutableStateOf("") }
  var keyHint by rememberSaveable { mutableStateOf("") }

  var testTo by rememberSaveable { mutableStateOf("") }
  var busy by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }
  var done by remember { mutableStateOf<String?>(null) }

  fun apply(row: JSONObject) {
    provider = row.optString("provider").ifBlank { "sms" }
    url = row.optString("url")
    method = row.optString("method").ifBlank { "POST" }
    sender = row.optString("sender")
    headers = row.optString("headers")
    body = row.optString("body")
    template = row.optString("template")
    keyHint = row.optString("keyHint")
    key = ""
  }

  fun load() {
    val token = session.token ?: return
    busy = true
    scope.launch {
      runCatching { AdminApi(session.serverUrl).smsSettings(token) }
        .onSuccess { apply(it); error = null }
        .onFailure { error = (it as? AdminApi.ApiError)?.message ?: "خوانده نشد" }
      busy = false
    }
  }
  LaunchedEffect(Unit) { load() }

  Column(
    Modifier.fillMaxSize().background(c.bg).verticalScroll(rememberScrollState()).imePadding().padding(16.dp)
  ) {
    Text("سرویس پیامک", style = MaterialTheme.typography.titleMedium, color = c.text, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(6.dp))
    Text(
      "کد ورود از سرور فرستاده می‌شود، نه از گوشیِ کاربر. این تنظیمات روی سرور می‌نشیند و همان لحظه کار می‌کند — نصب نسخهٔ تازه لازم نیست.",
      style = MaterialTheme.typography.bodySmall,
      color = c.muted,
    )

    Spacer(Modifier.height(14.dp))
    ErrorNote(error)
    done?.let {
      Text(it, style = MaterialTheme.typography.bodySmall, color = c.success, modifier = Modifier.padding(bottom = 8.dp))
    }

    /* ---------------------- راه ارسال ---------------------- */
    SectionTitle("راه ارسال")
    Panel {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Choice("پیامک", provider == "sms", Modifier.weight(1f)) { provider = "sms" }
        Choice("واتساپ", provider == "whatsapp", Modifier.weight(1f)) { provider = "whatsapp" }
        Choice("فقط لاگ", provider == "log", Modifier.weight(1f)) { provider = "log" }
      }
      if (provider == "log") {
        Text(
          "«فقط لاگ» یعنی کد به هیچ گوشی‌ای نمی‌رود و فقط در لاگ سرور چاپ می‌شود. برای آزمایش خوب است، برای کاربر واقعی نه.",
          style = MaterialTheme.typography.labelSmall,
          color = c.warn,
          modifier = Modifier.padding(top = 8.dp),
        )
      }
    }

    if (provider == "sms") {
      Spacer(Modifier.height(14.dp))
      SectionTitle("سرویس")
      Panel {
        //  دکمهٔ آماده، تا کسی مجبور نباشد این‌ها را از روی راهنما تایپ کند
        GhostButton("پر کردن با EasySendSMS", Modifier.fillMaxWidth()) {
          url = "https://restapi.easysendsms.app/v1/rest/sms/send"
          method = "POST"
          headers = """{"apikey":"{key}","Accept":"application/json"}"""
          body = """{"from":"{sender}","to":"{to_plain}","text":"{message}","type":"1"}"""
          done = "پر شد — کلید و نام فرستنده را بگذارید و ذخیره کنید"
        }
        Spacer(Modifier.height(12.dp))

        Field(value = url, onValueChange = { url = it }, label = "آدرس سرویس")
        Spacer(Modifier.height(10.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Choice("POST", method == "POST", Modifier.weight(1f)) { method = "POST" }
          Choice("GET", method == "GET", Modifier.weight(1f)) { method = "GET" }
        }

        Spacer(Modifier.height(10.dp))
        Field(value = sender, onValueChange = { sender = it }, label = "نام یا شمارهٔ فرستنده")

        Spacer(Modifier.height(10.dp))
        Field(
          value = key,
          onValueChange = { key = it },
          label = if (keyHint.isNotBlank()) "کلید سرویس — الان: $keyHint" else "کلید سرویس",
        )
        Text(
          if (keyHint.isNotBlank())
            "کلید نمایش داده نمی‌شود. خالی بگذارید تا همان قبلی بماند."
          else "کلید را از پنل سرویس، بخش API بردارید.",
          style = MaterialTheme.typography.labelSmall,
          color = c.muted,
          modifier = Modifier.padding(top = 6.dp, start = 4.dp),
        )
      }

      Spacer(Modifier.height(14.dp))
      SectionTitle("شکل درخواست")
      Panel {
        Text(
          "این‌ها جایگزین می‌شوند: {to} شماره با +، {to_plain} بدون +، {code} کد، {message} متن، {sender} فرستنده، {key} کلید.",
          style = MaterialTheme.typography.labelSmall,
          color = c.muted,
        )
        Spacer(Modifier.height(10.dp))
        Field(value = headers, onValueChange = { headers = it }, label = "سرآیندها (JSON)", singleLine = false)
        Spacer(Modifier.height(10.dp))
        Field(value = body, onValueChange = { body = it }, label = "بدنه", singleLine = false)
        Spacer(Modifier.height(10.dp))
        Field(value = template, onValueChange = { template = it }, label = "متن پیام (اختیاری) — با {code}")
      }
    }

    Spacer(Modifier.height(16.dp))
    PrimaryButton("ذخیره", Modifier.fillMaxWidth(), busy = busy) {
      val token = session.token ?: return@PrimaryButton
      busy = true; error = null; done = null
      scope.launch {
        runCatching {
          AdminApi(session.serverUrl).saveSmsSettings(
            token, provider, url.trim(), method, sender.trim(),
            headers.trim(), body.trim(), template.trim(), key.trim().ifBlank { null },
          )
        }
          .onSuccess {
            it.optJSONObject("sms")?.let { row -> apply(row) }
            done = "ذخیره شد"
          }
          .onFailure { error = (it as? AdminApi.ApiError)?.message ?: "ذخیره نشد" }
        busy = false
      }
    }

    /* ---------------------- آزمایش ---------------------- */
    Spacer(Modifier.height(18.dp))
    SectionTitle("آزمایش")
    Panel {
      Text(
        "یک کد ساختگی به شمارهٔ خودتان می‌رود. جایی ثبت نمی‌شود و با آن نمی‌شود وارد شد — فقط معلوم می‌کند سرویس راه افتاده یا نه.",
        style = MaterialTheme.typography.bodySmall,
        color = c.muted,
      )
      Spacer(Modifier.height(10.dp))
      Field(
        value = testTo,
        onValueChange = { testTo = it },
        label = "شمارهٔ خودتان",
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
      )
      Spacer(Modifier.height(10.dp))
      GhostButton(
        "فرستادن پیامک آزمایشی",
        Modifier.fillMaxWidth(),
        enabled = testTo.isNotBlank() && !busy,
        tint = c.primary,
      ) {
        val token = session.token ?: return@GhostButton
        busy = true; error = null; done = null
        scope.launch {
          runCatching { AdminApi(session.serverUrl).testSms(token, testTo.trim()) }
            .onSuccess {
              if (it.optBoolean("ok")) done = "رفت ✅ اگر تا یک دقیقه نرسید، اعتبار یا تأیید حساب را ببینید."
              else error = it.optString("error").ifBlank { "نرفت" }
            }
            .onFailure { error = (it as? AdminApi.ApiError)?.message ?: "نرفت" }
          busy = false
        }
      }
    }

    Spacer(Modifier.height(30.dp))
  }
}

/** دکمهٔ انتخابِ یکی از چند تا */
@Composable
private fun Choice(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
  val c = Admin.colors
  if (selected) {
    PrimaryButton(text, modifier) { onClick() }
  } else {
    GhostButton(text, modifier, tint = c.muted) { onClick() }
  }
}
