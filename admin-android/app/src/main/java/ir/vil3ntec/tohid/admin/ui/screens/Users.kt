package ir.vil3ntec.tohid.admin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.admin.net.AdminApi
import ir.vil3ntec.tohid.admin.net.Session
import ir.vil3ntec.tohid.admin.ui.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 *  کاربران — با جست‌وجو روی نام، ایمیل و شماره.
 *
 *  جست‌وجو سمتِ سرور انجام می‌شود، نه با کشیدنِ همهٔ کاربران و صاف کردنشان
 *  اینجا: با هزار کاربر، راهِ دوم هم کند است هم بی‌دلیل همه‌چیز را روی
 *  گوشی می‌آورد.
 */
@Composable
fun UsersScreen(session: Session) {
  val c = Admin.colors
  val scope = rememberCoroutineScope()

  var query by rememberSaveable { mutableStateOf("") }
  var rows by remember { mutableStateOf<JSONArray?>(null) }
  var busy by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }
  var open by remember { mutableStateOf<String?>(null) }

  //  با هر حرف یک درخواست نمی‌رود؛ کمی صبر می‌کنیم تا دست از تایپ بردارد
  LaunchedEffect(query) {
    val token = session.token ?: return@LaunchedEffect
    delay(350)
    busy = true
    runCatching { AdminApi(session.serverUrl).users(token, query.trim()) }
      .onSuccess { rows = it; error = null }
      .onFailure { error = (it as? AdminApi.ApiError)?.message ?: "فهرست خوانده نشد" }
    busy = false
  }

  open?.let { id ->
    UserSheet(session, id) { open = null }
    return
  }

  Column(Modifier.fillMaxSize().background(c.bg).padding(16.dp)) {
    Field(value = query, onValueChange = { query = it }, label = "جست‌وجو — نام، ایمیل یا شماره")
    Spacer(Modifier.height(12.dp))
    ErrorNote(error)

    val list = rows
    if (list == null || list.length() == 0) {
      Panel {
        Text(
          if (busy) "در حال خواندن…" else if (query.isBlank()) "کاربری نیست." else "چیزی پیدا نشد.",
          style = MaterialTheme.typography.bodySmall,
          color = c.muted,
        )
      }
    } else {
      Text("${list.length().fa()} کاربر", style = MaterialTheme.typography.labelMedium, color = c.muted)
      Spacer(Modifier.height(8.dp))
      Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
        Panel {
          for (i in 0 until list.length()) {
            val u = list.optJSONObject(i) ?: continue
            val id = u.optString("id")
            val shop = u.optString("shop_name").ifBlank { "بدون دکان" }
            ClickRow(
              title = u.optString("name").ifBlank { "بی‌نام" },
              subtitle = listOf(u.optString("phone"), u.optString("email"))
                .filter { it.isNotBlank() && it != "null" }
                .joinToString(" · ").ifBlank { shop }.fa(),
              trailing = {
                if (u.optString("status") == "active") StatusChip("فعال", c.success)
                else StatusChip("بسته", c.danger)
              },
            ) { open = id }
            if (i < list.length() - 1) HorizontalDivider(color = c.border)
          }
        }
        Spacer(Modifier.height(24.dp))
      }
    }
  }
}

/**
 *  پروندهٔ یک کاربر: مشخصات، عضویت‌ها، دستگاه‌ها، و بستن یا باز کردنِ حساب.
 *
 *  بستنِ حساب همان لحظه همهٔ نشست‌هایش را هم می‌بندد — این کار سمتِ سرور
 *  انجام می‌شود، پس گوشی‌ای که همین حالا باز است هم بیرون می‌افتد.
 */
@Composable
private fun UserSheet(session: Session, userId: String, onBack: () -> Unit) {
  val c = Admin.colors
  val scope = rememberCoroutineScope()

  var data by remember { mutableStateOf<JSONObject?>(null) }
  var busy by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }

  fun load() {
    val token = session.token ?: return
    busy = true
    scope.launch {
      runCatching { AdminApi(session.serverUrl).user(token, userId) }
        .onSuccess { data = it; error = null }
        .onFailure { error = (it as? AdminApi.ApiError)?.message ?: "خوانده نشد" }
      busy = false
    }
  }
  LaunchedEffect(userId) { load() }

  Column(
    Modifier.fillMaxSize().background(c.bg).verticalScroll(rememberScrollState()).padding(16.dp)
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      IconButton(onClick = onBack) {
        Icon(Icons.Filled.ArrowForward, contentDescription = "برگشت", tint = c.text)
      }
      Text("پروندهٔ کاربر", style = MaterialTheme.typography.titleMedium, color = c.text, fontWeight = FontWeight.Bold)
    }
    Spacer(Modifier.height(10.dp))
    ErrorNote(error)

    val d = data
    if (d == null) {
      Panel { Text(if (busy) "در حال خواندن…" else "چیزی نیست.", style = MaterialTheme.typography.bodySmall, color = c.muted) }
      return@Column
    }

    val user = d.optJSONObject("user") ?: JSONObject()
    val status = user.optString("status")

    Panel {
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
          user.optString("name").ifBlank { "بی‌نام" },
          style = MaterialTheme.typography.titleMedium,
          color = c.text,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.weight(1f),
        )
        if (status == "active") StatusChip("فعال", c.success) else StatusChip("بسته", c.danger)
      }
      Spacer(Modifier.height(10.dp))
      Row2("شناسه", user.optString("id"))
      Row2("شماره", user.optString("phone").ifBlank { "—" }.fa())
      Row2("ایمیل", user.optString("email").ifBlank { "—" })
      Row2("ساخته شده", jalali(user.optLong("createdAt")))
      Row2("آخرین ورود", jalali(user.optLong("lastLoginAt")))
    }

    Spacer(Modifier.height(14.dp))
    SectionTitle("عضویت‌ها")
    val memberships = d.optJSONArray("memberships") ?: JSONArray()
    Panel {
      if (memberships.length() == 0) {
        Text("عضو هیچ دکانی نیست.", style = MaterialTheme.typography.bodySmall, color = c.muted)
      } else {
        for (i in 0 until memberships.length()) {
          val m = memberships.optJSONObject(i) ?: continue
          Row2(m.optString("shop_name").ifBlank { "دکان" }, roleName(m.optString("role")))
        }
      }
    }

    Spacer(Modifier.height(14.dp))
    SectionTitle("دستگاه‌های وصل")
    val devices = d.optJSONArray("devices") ?: JSONArray()
    Panel {
      if (devices.length() == 0) {
        Text("دستگاهی ثبت نشده.", style = MaterialTheme.typography.bodySmall, color = c.muted)
      } else {
        for (i in 0 until devices.length()) {
          val dev = devices.optJSONObject(i) ?: continue
          Row2(
            dev.optString("name").ifBlank { dev.optString("platform").ifBlank { "دستگاه" } },
            jalali(dev.optLong("last_seen_at")),
          )
        }
      }
    }

    Spacer(Modifier.height(18.dp))
    GhostButton(
      text = if (status == "active") "بستن این حساب" else "باز کردن این حساب",
      modifier = Modifier.fillMaxWidth(),
      enabled = !busy,
      tint = if (status == "active") c.danger else c.success,
    ) {
      val token = session.token ?: return@GhostButton
      busy = true
      scope.launch {
        val next = if (status == "active") "disabled" else "active"
        runCatching { AdminApi(session.serverUrl).setUserStatus(token, userId, next) }
          .onSuccess { load() }
          .onFailure { error = (it as? AdminApi.ApiError)?.message ?: "انجام نشد"; busy = false }
      }
    }
    Text(
      "بستنِ حساب همان لحظه همهٔ نشست‌هایش را می‌بندد؛ گوشیِ باز هم بیرون می‌افتد. اطلاعاتش پاک نمی‌شود.",
      style = MaterialTheme.typography.labelSmall,
      color = c.muted,
      modifier = Modifier.padding(top = 8.dp),
    )
    Spacer(Modifier.height(30.dp))
  }
}

fun roleName(role: String): String = when (role) {
  "owner" -> "صاحب دکان"
  "manager" -> "مدیر"
  "staff" -> "شاگرد"
  else -> role
}
