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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.admin.net.AdminApi
import ir.vil3ntec.tohid.admin.net.Session
import ir.vil3ntec.tohid.admin.ui.*
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 *  ایمیل و پوش.
 *
 *  ── چرا مهم‌ترین صفحهٔ این برنامه است ───────────────────────────────
 *  کدِ شش‌رقمیِ ثبت‌نام از راه ایمیل می‌رود. تا امروز هیچ راهی برای تنظیمِ
 *  ایمیل نبود مگر دست بردن در فایلِ سرور — یعنی در عمل کد هیچ‌جا نمی‌رفت
 *  و هر ثبت‌نامی همان‌جا می‌ماند. همین صفحه آن را باز می‌کند.
 *
 *  ── رمز ─────────────────────────────────────────────────────────────
 *  رمزِ ایمیل هرگز از سرور برنمی‌گردد — نه اینجا، نه در لاگ. خالی
 *  گذاشتنش یعنی «همان قبلی بماند»، نه «پاکش کن».
 *
 *  ── پوش ─────────────────────────────────────────────────────────────
 *  بدون آن، پیامِ پشتیبانی فقط وقتی دیده می‌شود که برنامه باز باشد. با
 *  آن، به گوشیِ بسته هم می‌رسد — همان چیزی که خواسته شده بود.
 */
@Composable
fun EmailScreen(session: Session) {
  val c = Admin.colors
  val scope = rememberCoroutineScope()

  var settings by remember { mutableStateOf<JSONObject?>(null) }
  var pushCfg by remember { mutableStateOf<JSONObject?>(null) }
  var busy by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }
  var result by remember { mutableStateOf<String?>(null) }

  var provider by rememberSaveable { mutableStateOf("log") }
  var host by rememberSaveable { mutableStateOf("") }
  var port by rememberSaveable { mutableStateOf("587") }
  var secure by rememberSaveable { mutableStateOf("starttls") }
  var user by rememberSaveable { mutableStateOf("") }
  var pass by rememberSaveable { mutableStateOf("") }
  var from by rememberSaveable { mutableStateOf("") }
  var fromName by rememberSaveable { mutableStateOf("") }
  var apiUrl by rememberSaveable { mutableStateOf("") }
  var apiKey by rememberSaveable { mutableStateOf("") }
  var testTo by rememberSaveable { mutableStateOf("") }

  fun load() {
    val token = session.token ?: return
    busy = true
    scope.launch {
      val api = AdminApi(session.serverUrl)
      runCatching { api.emailSettings(token) }
        .onSuccess { s ->
          settings = s
          provider = s.optString("provider").ifBlank { "log" }
          host = s.optString("host")
          port = s.optString("port").ifBlank { "587" }
          secure = s.optString("secure").ifBlank { "starttls" }
          user = s.optString("user")
          from = s.optString("from")
          fromName = s.optString("fromName")
          apiUrl = s.optString("url")
          error = null
        }
        .onFailure { error = (it as? AdminApi.ApiError)?.message ?: "خوانده نشد" }
      runCatching { api.pushSettings(token) }.onSuccess { pushCfg = it }
      busy = false
    }
  }
  LaunchedEffect(Unit) { load() }

  Column(Modifier.fillMaxSize().background(c.bg).verticalScroll(rememberScrollState()).padding(16.dp)) {
    Text("ایمیل و پوش", style = MaterialTheme.typography.titleMedium, color = c.text, fontWeight = FontWeight.Bold)

    //  اول از همه: راه افتاده یا نه، و اگر نه، چه چیزی کم است
    settings?.let { s ->
      Spacer(Modifier.height(10.dp))
      Panel {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          Text(
            "وضعیت", style = MaterialTheme.typography.titleSmall,
            color = c.text, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
          )
          if (s.optBoolean("ready")) StatusChip("آماده", c.success)
          else if (s.optString("provider") == "log") StatusChip("خاموش", c.muted)
          else StatusChip("ناقص", c.danger)
        }
        Spacer(Modifier.height(6.dp))
        val missing = s.optJSONArray("missing")
        Text(
          when {
            s.optBoolean("ready") -> "کدِ ثبت‌نام و کدِ اشتراک از همین راه می‌روند."
            s.optString("provider") == "log" -> "ایمیل خاموش است — کدها فقط در لاگ سرور چاپ می‌شوند و به کسی نمی‌رسند."
            missing != null && missing.length() > 0 ->
              "کم است: " + (0 until missing.length()).joinToString("، ") { missing.optString(it) }
            else -> "تنظیمات ناقص است."
          },
          style = MaterialTheme.typography.bodySmall,
          color = if (s.optBoolean("ready")) c.muted else c.danger,
        )
      }
    }

    Spacer(Modifier.height(14.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Chip("خاموش", provider == "log") { provider = "log" }
      Chip("SMTP", provider == "smtp") { provider = "smtp" }
      Chip("سرویس API", provider == "api") { provider = "api" }
    }
    Text(
      when (provider) {
        "smtp" -> "با سرور ایمیلِ خودتان، یا Gmail و Zoho. برای Gmail «رمز اپلیکیشن» لازم است، نه رمز اصلی."
        "api" -> "سرویس‌هایی مثل Resend یا Brevo که با یک نشانی و کلید کار می‌کنند."
        else -> "چیزی فرستاده نمی‌شود. فقط برای سرورِ خانگی و آزمایش."
      },
      style = MaterialTheme.typography.labelSmall, color = c.muted,
      modifier = Modifier.padding(top = 6.dp),
    )

    if (provider != "log") {
      Spacer(Modifier.height(12.dp))
      Field(
        value = from, onValueChange = { from = it.trim() }, label = "ایمیل فرستنده",
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
      )
      Spacer(Modifier.height(10.dp))
      Field(value = fromName, onValueChange = { fromName = it }, label = "نام فرستنده")
    }

    if (provider == "smtp") {
      Spacer(Modifier.height(10.dp))
      Field(value = host, onValueChange = { host = it.trim() }, label = "سرور SMTP (مثلاً smtp.gmail.com)")
      Spacer(Modifier.height(10.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Field(
          value = port, onValueChange = { port = it.filter { ch -> ch.isDigit() } },
          label = "پورت", modifier = Modifier.weight(1f),
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
      }
      Spacer(Modifier.height(10.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip("۵۸۷ · STARTTLS", secure == "starttls") { secure = "starttls"; port = "587" }
        Chip("۴۶۵ · SSL", secure == "ssl") { secure = "ssl"; port = "465" }
      }
      Spacer(Modifier.height(10.dp))
      Field(
        value = user, onValueChange = { user = it.trim() }, label = "نام کاربری",
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
      )
      Spacer(Modifier.height(10.dp))
      Field(
        value = pass, onValueChange = { pass = it },
        label = if (settings?.optBoolean("passSet") == true) "رمز (خالی = همان قبلی)" else "رمز",
        visual = PasswordVisualTransformation(),
      )
    }

    if (provider == "api") {
      Spacer(Modifier.height(10.dp))
      Field(value = apiUrl, onValueChange = { apiUrl = it.trim() }, label = "نشانی سرویس")
      Spacer(Modifier.height(10.dp))
      Field(
        value = apiKey, onValueChange = { apiKey = it.trim() },
        label = if (settings?.optBoolean("keySet") == true) "کلید (خالی = همان قبلی)" else "کلید",
        visual = PasswordVisualTransformation(),
      )
    }

    Spacer(Modifier.height(14.dp))
    ErrorNote(error)
    result?.let {
      Text(
        it, style = MaterialTheme.typography.bodySmall,
        color = if (it.startsWith("نشد")) c.danger else c.success,
        modifier = Modifier.padding(bottom = 8.dp),
      )
    }

    PrimaryButton("ذخیره", Modifier.fillMaxWidth(), busy = busy) {
      val token = session.token ?: return@PrimaryButton
      busy = true
      scope.launch {
        val patch = JSONObject().apply {
          put("provider", provider)
          put("from", from)
          put("fromName", fromName)
          put("host", host)
          put("port", port.toIntOrNull() ?: 587)
          put("secure", secure)
          put("user", user)
          put("url", apiUrl)
          //  رمز و کلید فقط وقتی می‌روند که چیزی نوشته شده باشد
          if (pass.isNotBlank()) put("pass", pass)
          if (apiKey.isNotBlank()) put("key", apiKey)
        }
        runCatching { AdminApi(session.serverUrl).saveEmailSettings(token, patch) }
          .onSuccess { settings = it; pass = ""; apiKey = ""; result = "ذخیره شد."; error = null }
          .onFailure { error = (it as? AdminApi.ApiError)?.message ?: "ذخیره نشد" }
        busy = false
      }
    }

    Spacer(Modifier.height(16.dp))
    Panel {
      SectionTitle("آزمایش")
      Text(
        "یک ایمیل واقعی به نشانی خودتان. اگر نرسید، متنِ خودِ سرورِ ایمیل همین‌جا نشان داده می‌شود.",
        style = MaterialTheme.typography.bodySmall, color = c.muted,
      )
      Spacer(Modifier.height(10.dp))
      Field(
        value = testTo, onValueChange = { testTo = it.trim() }, label = "ایمیل شما",
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
      )
      Spacer(Modifier.height(10.dp))
      GhostButton("فرستادن آزمایشی", Modifier.fillMaxWidth(), enabled = testTo.contains("@")) {
        val token = session.token ?: return@GhostButton
        busy = true
        scope.launch {
          runCatching { AdminApi(session.serverUrl).testEmail(token, testTo) }
            .onSuccess {
              result = if (it.optBoolean("ok")) "رفت — صندوق ورودی (و پوشهٔ اسپم) را ببینید."
              else "نشد: ${it.optString("error")}"
            }
            .onFailure { error = (it as? AdminApi.ApiError)?.message ?: "نشد" }
          busy = false
        }
      }
    }

    Spacer(Modifier.height(16.dp))
    Panel {
      SectionTitle("پوش — پیام به گوشیِ بسته")
      val p = pushCfg
      Text(
        when {
          p == null -> "…"
          p.optBoolean("enabled") && p.optBoolean("configured") ->
            "روشن است. ${p.optInt("devices").fa()} دستگاه ثبت شده."
          p.optBoolean("configured") -> "تنظیم شده ولی خاموش است."
          else -> "تنظیم نشده. بدون این، پیام پشتیبانی فقط وقتی دیده می‌شود که برنامه باز باشد."
        },
        style = MaterialTheme.typography.bodySmall,
        color = if (p?.optBoolean("enabled") == true) c.muted else c.warn,
      )
      p?.optString("project")?.takeIf { it.isNotBlank() }?.let {
        Spacer(Modifier.height(6.dp))
        Row2("پروژهٔ Firebase", it)
      }
      Spacer(Modifier.height(10.dp))
      Text(
        "فایلِ «حساب سرویس» را از کنسول Firebase بگیرید و همان JSON را اینجا بگذارید. " +
          "روی سرور می‌ماند و هرگز برنمی‌گردد.",
        style = MaterialTheme.typography.labelSmall, color = c.muted,
      )
      Spacer(Modifier.height(10.dp))
      PushBox(session) { load() }
    }

    Spacer(Modifier.height(30.dp))
  }
}

@Composable
private fun PushBox(session: Session, onSaved: () -> Unit) {
  val c = Admin.colors
  val scope = rememberCoroutineScope()
  var json by rememberSaveable { mutableStateOf("") }
  var enabled by rememberSaveable { mutableStateOf(true) }
  var busy by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }

  Column {
    Field(
      value = json, onValueChange = { json = it },
      label = "JSON حساب سرویس (خالی = همان قبلی)", singleLine = false,
    )
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Text("پوش روشن باشد", style = MaterialTheme.typography.bodyMedium, color = c.text, modifier = Modifier.weight(1f))
      Switch(checked = enabled, onCheckedChange = { enabled = it })
    }
    ErrorNote(error)
    Spacer(Modifier.height(6.dp))
    GhostButton("ذخیرهٔ پوش", Modifier.fillMaxWidth(), enabled = !busy) {
      val token = session.token ?: return@GhostButton
      busy = true
      scope.launch {
        runCatching {
          AdminApi(session.serverUrl).savePushSettings(token, enabled, json.trim().ifBlank { null })
        }
          .onSuccess { json = ""; error = null; onSaved() }
          .onFailure { error = (it as? AdminApi.ApiError)?.message ?: "ذخیره نشد" }
        busy = false
      }
    }
  }
}
