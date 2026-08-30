package ir.vil3ntec.tohid.admin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.admin.net.AdminApi
import ir.vil3ntec.tohid.admin.net.Session
import ir.vil3ntec.tohid.admin.ui.*
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 *  داشبورد — عددهای واقعیِ سرور، نه چیزی که اینجا ساخته شده باشد.
 *
 *  هر عدد یک پرس‌وجوی `COUNT` روی دیتابیس است. اگر سرور نرسد، عددی نشان
 *  داده نمی‌شود؛ صفرِ ساختگی بدتر از «نمی‌دانم» است.
 */
@Composable
fun DashboardScreen(session: Session) {
  val c = Admin.colors
  val scope = rememberCoroutineScope()

  var stats by remember { mutableStateOf<JSONObject?>(null) }
  var serverUp by remember { mutableStateOf<Boolean?>(null) }
  var busy by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }

  fun load() {
    val token = session.token ?: return
    busy = true
    scope.launch {
      val api = AdminApi(session.serverUrl)
      runCatching { api.stats(token) }
        .onSuccess { stats = it; error = null }
        .onFailure { error = (it as? AdminApi.ApiError)?.message ?: "خوانده نشد" }
      serverUp = runCatching { api.health() }.isSuccess
      busy = false
    }
  }

  LaunchedEffect(Unit) { load() }

  Column(
    Modifier.fillMaxSize().background(c.bg).verticalScroll(rememberScrollState()).padding(16.dp)
  ) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Text("سلام ${session.adminName}", style = MaterialTheme.typography.titleMedium, color = c.text, fontWeight = FontWeight.Bold)
        Text(session.serverUrl, style = MaterialTheme.typography.labelSmall, color = c.muted)
      }
      when (serverUp) {
        true -> StatusChip("سرور بالاست", c.success)
        false -> StatusChip("سرور نمی‌رسد", c.danger)
        null -> {}
      }
    }

    Spacer(Modifier.height(16.dp))
    ErrorNote(error)

    val s = stats
    if (s == null) {
      Panel {
        Text(
          if (busy) "در حال خواندن…" else "چیزی خوانده نشد.",
          style = MaterialTheme.typography.bodySmall,
          color = c.muted,
        )
      }
    } else {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Tile("کاربران", s.optInt("users"), c.primary, Modifier.weight(1f))
        Tile("دکان‌ها", s.optInt("shops"), c.primary, Modifier.weight(1f))
      }
      Spacer(Modifier.height(10.dp))
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Tile("اشتراک فعال", s.optInt("activeSubscriptions"), c.success, Modifier.weight(1f))
        Tile("منقضی", s.optInt("expiredSubscriptions"), c.danger, Modifier.weight(1f))
      }
      Spacer(Modifier.height(10.dp))
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Tile("کارمندان فعال", s.optInt("members"), c.muted, Modifier.weight(1f))
        Tile("درخواست خرید", s.optInt("pendingRequests"), c.warn, Modifier.weight(1f))
      }

      Spacer(Modifier.height(16.dp))
      Panel {
        SectionTitle("ساعت سرور")
        Text(
          jalali(s.optLong("serverTime")),
          style = MaterialTheme.typography.titleMedium,
          color = c.text,
          fontWeight = FontWeight.Bold,
        )
        Text(
          "تاریخ اشتراک‌ها با همین ساعت حساب می‌شود، نه با ساعت گوشی کسی.",
          style = MaterialTheme.typography.labelSmall,
          color = c.muted,
          modifier = Modifier.padding(top = 6.dp),
        )
      }
    }

    Spacer(Modifier.height(16.dp))
    GhostButton("تازه کردن", Modifier.fillMaxWidth(), enabled = !busy) { load() }
    Spacer(Modifier.height(24.dp))
  }
}

@Composable
private fun Tile(label: String, value: Int, tint: Color, modifier: Modifier = Modifier) {
  val c = Admin.colors
  Column(
    modifier
      .clip(RoundedCornerShape(16.dp))
      .background(c.surface)
      .padding(vertical = 18.dp, horizontal = 12.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(value.fa(), style = MaterialTheme.typography.headlineSmall, color = tint, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(4.dp))
    Text(label, style = MaterialTheme.typography.labelMedium, color = c.muted, textAlign = TextAlign.Center)
  }
}
