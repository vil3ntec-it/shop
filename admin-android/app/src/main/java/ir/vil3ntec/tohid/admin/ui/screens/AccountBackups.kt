package ir.vil3ntec.tohid.admin.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.admin.net.AdminApi
import ir.vil3ntec.tohid.admin.net.Session
import ir.vil3ntec.tohid.admin.ui.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 *  ══ پشتیبانِ یک حساب — دکان یا پمپ ════════════════════════════════
 *
 *  ── چه چیزی را نشان می‌دهد ─────────────────────────────────────────
 *  فایل‌هایی که **خودِ برنامهٔ همان حساب** فرستاده: `AutoBackup`ِ
 *  برنامهٔ دکان و `BackupPusher`ِ برنامهٔ پمپ. با این‌ها می‌شود همان یک
 *  حساب را روی گوشی یا کامپیوترِ تازه برگرداند.
 *
 *  ⚠️ **با «پشتیبان‌های سامانه» یکی نیست.** آن یکی `pg_dump`ِ کلِ
 *  دیتابیس است؛ برگرداندنِ یک دکان از آن یعنی برگرداندنِ **همهٔ**
 *  مشتری‌ها به دیروز — کاری که هیچ‌کس نمی‌کند.
 *
 *  ⛔ **خودِ فایل این‌جا باز نمی‌شود.** دفترِ یک نفر است و پنل جای
 *  خواندنش نیست؛ فقط گفته می‌شود که هست، چقدر است و کِی آمده. اگر
 *  لازم شد، از پنلِ وب دانلود می‌شود.
 *
 *  ⚠️ «سهم» دو پله دارد و همین‌جا نوشته می‌شود، وگرنه مدیر نمی‌فهمد
 *  چرا حسابی سه نسخه دارد و حسابی بیست‌تا.
 */
@Composable
fun AccountBackupsPanel(session: Session, app: String, tenantId: String) {
  val c = Admin.colors
  val scope = rememberCoroutineScope()

  var list by remember { mutableStateOf<JSONArray?>(null) }
  var stats by remember { mutableStateOf<JSONObject?>(null) }
  var error by remember { mutableStateOf<String?>(null) }
  var busy by remember { mutableStateOf(false) }
  var reload by remember { mutableIntStateOf(0) }

  LaunchedEffect(app, tenantId, reload) {
    val token = session.token ?: return@LaunchedEffect
    busy = true
    runCatching { AdminApi(session.serverUrl).accountBackups(token, app, tenantId) }
      .onSuccess {
        list = it.optJSONArray("backups") ?: JSONArray()
        stats = it.optJSONObject("stats")
        error = null
      }
      .onFailure { error = (it as? AdminApi.ApiError)?.message ?: "خوانده نشد" }
    busy = false
  }

  SectionTitle("پشتیبان‌های این ${if (app == "pump") "پمپ" else "دکان"}")
  Panel {
    ErrorNote(error)
    val rows = list
    val s = stats

    if (s != null) {
      Row2("نسخه‌ها", "${s.optInt("count").fa()} از ${s.optInt("keep").fa()}")
      Row2("جا", "${mb(s.optLong("usedBytes"))} از ${mb(s.optLong("quotaBytes"))}")
      Row2("سهم", if (s.optBoolean("paid")) "اشتراک‌دار" else "بی‌اشتراک")
      if (s.optLong("lastAt") > 0) Row2("آخرین", jalali(s.optLong("lastAt")))
      Spacer(Modifier.height(6.dp))
      HorizontalDivider(color = c.border)
      Spacer(Modifier.height(6.dp))
    }

    if (rows == null || rows.length() == 0) {
      Text(
        if (busy) "در حال خواندن…"
        else "این حساب هنوز پشتیبانی نفرستاده است.",
        style = MaterialTheme.typography.bodySmall,
        color = c.muted,
      )
      return@Panel
    }

    for (i in 0 until rows.length()) {
      val b = rows.optJSONObject(i) ?: continue
      Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
          Text(
            jalali(b.optLong("createdAt")),
            style = MaterialTheme.typography.bodyMedium,
            color = c.text,
            fontWeight = FontWeight.Medium,
          )
          Text(
            buildString {
              append(mb(b.optLong("bytes")))
              append(" · ")
              append(if (b.optString("kind") == "manual") "دستی" else "خودکار")
              b.optString("label").takeIf { it.isNotBlank() }?.let { append(" · $it") }
              b.optString("appVersion").takeIf { it.isNotBlank() }?.let { append(" · نسخهٔ $it") }
            },
            style = MaterialTheme.typography.labelSmall,
            color = c.muted,
          )
        }
        TextButton(
          onClick = {
            val token = session.token ?: return@TextButton
            scope.launch {
              busy = true
              runCatching {
                AdminApi(session.serverUrl).deleteAccountBackup(token, app, tenantId, b.optString("id"))
              }
                .onSuccess { reload += 1 }
                .onFailure { error = (it as? AdminApi.ApiError)?.message ?: "پاک نشد"; busy = false }
            }
          },
          enabled = !busy,
        ) { Text("حذف", color = c.danger, style = MaterialTheme.typography.labelMedium) }
      }
      if (i < rows.length() - 1) HorizontalDivider(color = c.border)
    }
  }
}

/**
 *  بایت → مگابایت، با یک رقمِ اعشار.
 *
 *  ⚠️ کیلوبایت نمی‌نویسیم: پشتیبانِ یک پمپِ پنج‌ساله چند صد مگابایت
 *  است و عددِ شش‌رقمی به کسی چیزی نمی‌گوید.
 */
private fun mb(bytes: Long): String {
  if (bytes <= 0) return "۰"
  val m = bytes.toDouble() / (1024 * 1024)
  return if (m < 0.1) "زیرِ ۰٫۱ مگابایت"
  else String.format(java.util.Locale.US, "%.1f", m).fa().replace('.', '٫') + " مگابایت"
}
