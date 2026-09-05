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
  var overview by remember { mutableStateOf<JSONObject?>(null) }
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
      //  خلاصه — اشتراک‌های رو به پایان، پیام‌های خوانده‌نشده، وضعیت ایمیل
      runCatching { api.overview(token) }.onSuccess { overview = it }
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

      /*
       *  ── چیزهایی که باید *امروز* ببینید ────────────────────────────
       *  عددهای بالا وضعیت را می‌گویند؛ این‌ها کار را. اشتراکی که دارد
       *  تمام می‌شود و پیامی که جواب نگرفته، تا وقتی روی صفحهٔ اول
       *  نباشند، همیشه دیر دیده می‌شوند.
       */
      overview?.let { ov ->
        val expiring = ov.optJSONArray("expiring")
        val soon = (0 until (expiring?.length() ?: 0))
          .mapNotNull { expiring?.optJSONObject(it) }
        val unread = ov.optInt("supportUnread")

        if (unread > 0) {
          Spacer(Modifier.height(14.dp))
          Alert(
            title = "${unread.fa()} پیام پشتیبانیِ بی‌پاسخ",
            body = "کسی منتظر جواب است. از تبِ پشتیبانی ببینیدشان.",
            tint = c.warn,
          )
        }

        if (soon.isNotEmpty()) {
          Spacer(Modifier.height(14.dp))
          Panel {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
              Text(
                "اشتراک‌های رو به پایان",
                style = MaterialTheme.typography.titleSmall,
                color = c.text, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
              )
              StatusChip(soon.size.fa(), c.warn)
            }
            Spacer(Modifier.height(8.dp))
            soon.take(6).forEach { e ->
              val left = e.optInt("daysLeft")
              Row2(
                e.optString("shopName").ifBlank { e.optString("ownerName") },
                when {
                  left < 0 -> "${(-left).fa()} روز گذشته"
                  left == 0 -> "امروز"
                  else -> "${left.fa()} روز مانده"
                },
              )
            }
            if (soon.size > 6) {
              Text(
                "و ${(soon.size - 6).fa()} تای دیگر — در تبِ دکان‌ها.",
                style = MaterialTheme.typography.labelSmall, color = c.muted,
                modifier = Modifier.padding(top = 6.dp),
              )
            }
            Spacer(Modifier.height(10.dp))
            GhostButton("خبر دادن به همه‌شان", Modifier.fillMaxWidth(), tint = c.primary) {
              val token = session.token ?: return@GhostButton
              scope.launch {
                runCatching { AdminApi(session.serverUrl).notifyExpiring(token) }
                  .onSuccess { load() }
                  .onFailure { error = (it as? AdminApi.ApiError)?.message ?: "نشد" }
              }
            }
          }
        }

        //  ایمیلِ تنظیم‌نشده یعنی هیچ کدِ ثبت‌نامی به کسی نمی‌رسد — این
        //  را نباید در یک صفحهٔ تنظیماتِ دور دفن کرد
        val email = ov.optJSONObject("email")
        if (email != null && !email.optBoolean("ready")) {
          Spacer(Modifier.height(14.dp))
          Alert(
            title = "ایمیل تنظیم نیست",
            body = "کدِ ثبت‌نام و کدِ اشتراک به کسی نمی‌رسد. از «ایمیل و پوش» درستش کنید.",
            tint = c.danger,
          )
        }

        val visitors = ov.optJSONObject("visitors")
        if (visitors != null) {
          Spacer(Modifier.height(14.dp))
          Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile("بازدیدکننده", visitors.optInt("total"), c.primary, Modifier.weight(1f))
            Tile("مهمان", visitors.optInt("guests"), c.warn, Modifier.weight(1f))
          }
        }
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

/** کارتِ هشدار — چیزی که باید همین حالا دیده شود */
@Composable
private fun Alert(title: String, body: String, tint: Color) {
  val c = Admin.colors
  Column(
    Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(16.dp))
      .background(tint.copy(alpha = 0.12f))
      .padding(14.dp),
  ) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = tint, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(4.dp))
    Text(body, style = MaterialTheme.typography.bodySmall, color = c.muted)
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
