package ir.vil3ntec.tohid.admin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.admin.net.AdminApi
import ir.vil3ntec.tohid.admin.net.Session
import ir.vil3ntec.tohid.admin.ui.*
import kotlinx.coroutines.launch
import org.json.JSONArray

/**
 *  سابقهٔ عملیات.
 *
 *  هر کارِ مدیر اینجا می‌ماند: چه کسی، چه کرد، روی چه چیزی، کِی. این
 *  فهرست از خودِ سرور می‌آید و از این برنامه پاک‌شدنی نیست — که همان
 *  فایده‌اش است.
 */
@Composable
fun AuditScreen(session: Session) {
  val c = Admin.colors
  val scope = rememberCoroutineScope()

  var rows by remember { mutableStateOf<JSONArray?>(null) }
  var busy by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }

  fun load() {
    val token = session.token ?: return
    busy = true
    scope.launch {
      runCatching { AdminApi(session.serverUrl).audit(token) }
        .onSuccess { rows = it; error = null }
        .onFailure { error = (it as? AdminApi.ApiError)?.message ?: "خوانده نشد" }
      busy = false
    }
  }
  LaunchedEffect(Unit) { load() }

  Column(Modifier.fillMaxSize().background(c.bg).padding(16.dp)) {
    Row(Modifier.fillMaxWidth()) {
      Text("سابقهٔ عملیات", style = MaterialTheme.typography.titleMedium, color = c.text, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
      TextButton(onClick = { load() }, enabled = !busy) {
        Text("تازه کردن", style = MaterialTheme.typography.labelMedium, color = c.primary)
      }
    }
    Spacer(Modifier.height(8.dp))
    ErrorNote(error)

    val list = rows
    if (list == null || list.length() == 0) {
      Panel {
        Text(
          if (busy) "در حال خواندن…" else "چیزی ثبت نشده.",
          style = MaterialTheme.typography.bodySmall,
          color = c.muted,
        )
      }
    } else {
      Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
        Panel {
          for (i in 0 until list.length()) {
            val row = list.optJSONObject(i) ?: continue
            Column(Modifier.padding(vertical = 7.dp)) {
              Text(
                actionText(row.optString("action")),
                style = MaterialTheme.typography.bodySmall,
                color = c.text,
                fontWeight = FontWeight.Medium,
              )
              Text(
                listOfNotNull(
                  row.optString("actor_type").takeIf { it.isNotBlank() },
                  row.optString("target_type").takeIf { it.isNotBlank() },
                  jalali(row.optLong("created_at")),
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = c.muted,
              )
            }
            if (i < list.length() - 1) HorizontalDivider(color = c.border)
          }
        }
        Spacer(Modifier.height(24.dp))
      }
    }
  }
}

/** نامِ فارسیِ کارها — کدِ خام برای کسی که پشتِ دخل است معنی ندارد */
private fun actionText(action: String): String = when (action) {
  "admin.login" -> "ورود مدیر"
  "admin.user_status" -> "تغییر وضعیت حساب"
  "admin.subscription_granted" -> "صدور یا تمدید اشتراک"
  "admin.subscription_status" -> "تغییر وضعیت اشتراک"
  "admin.subscription_updated" -> "ویرایش اشتراک"
  "auth.login" -> "ورود کاربر"
  "auth.register" -> "ثبت‌نام"
  "staff.joined" -> "پیوستن شاگرد"
  "staff_code.created" -> "ساخت کد شاگرد"
  "staff_code.rotated" -> "عوض کردن کد دکان"
  "staff_code.revoked" -> "باطل کردن کد"
  else -> action
}
