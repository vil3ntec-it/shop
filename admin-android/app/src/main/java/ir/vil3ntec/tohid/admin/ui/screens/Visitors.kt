package ir.vil3ntec.tohid.admin.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.admin.net.AdminApi
import ir.vil3ntec.tohid.admin.net.Session
import ir.vil3ntec.tohid.admin.ui.*
import kotlinx.coroutines.delay
import org.json.JSONObject

/**
 *  بازدیدکننده‌ها — کسانی که آمده‌اند، چه حساب ساخته باشند چه نه.
 *
 *  ── چه چیزی اینجا درست شد ──────────────────────────────────────────
 *  پنل فقط ثبت‌نام‌کرده‌ها را نشان می‌داد. کسی که برنامه را نصب کرده و باز
 *  کرده ولی هنوز حساب نساخته — یعنی دقیقاً همان کسی که باید دنبالش
 *  رفت — هیچ‌جا دیده نمی‌شد.
 *
 *  ── لوکیشن ─────────────────────────────────────────────────────────
 *  اگر دستگاه لوکیشن داده باشد، اینجا هست و با یک زدن روی نقشه باز
 *  می‌شود: «دکانِ این کجاست» بدون پرسیدن.
 */
@Composable
fun VisitorsScreen(session: Session) {
  val c = Admin.colors
  val context = LocalContext.current

  var rows by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
  var summary by remember { mutableStateOf<JSONObject?>(null) }
  var onlyGuests by rememberSaveable { mutableStateOf(false) }
  var query by rememberSaveable { mutableStateOf("") }
  var busy by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(onlyGuests, query) {
    val token = session.token ?: return@LaunchedEffect
    delay(300)
    busy = true
    runCatching { AdminApi(session.serverUrl).visitors(token, "", onlyGuests, query.trim()) }
      .onSuccess { body ->
        val arr = body.optJSONArray("visitors")
        rows = (0 until (arr?.length() ?: 0)).mapNotNull { arr?.optJSONObject(it) }
        summary = body.optJSONObject("summary")
        error = null
      }
      .onFailure { error = (it as? AdminApi.ApiError)?.message ?: "خوانده نشد" }
    busy = false
  }

  Column(Modifier.fillMaxSize().background(c.bg).padding(16.dp)) {
    Text("بازدیدکننده‌ها", style = MaterialTheme.typography.titleMedium, color = c.text, fontWeight = FontWeight.Bold)
    Text(
      "هر کس که برنامه یا سایت را باز کرده — حتی بدون حساب.",
      style = MaterialTheme.typography.labelSmall, color = c.muted,
    )

    summary?.let { s ->
      Spacer(Modifier.height(12.dp))
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MiniTile("همه", s.optInt("total"), c.primary, Modifier.weight(1f))
        MiniTile("مهمان", s.optInt("guests"), c.warn, Modifier.weight(1f))
        MiniTile("حساب‌دار", s.optInt("signedUp"), c.success, Modifier.weight(1f))
      }
      Spacer(Modifier.height(8.dp))
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MiniTile("امروز", s.optInt("today"), c.primary, Modifier.weight(1f))
        MiniTile("این هفته", s.optInt("week"), c.muted, Modifier.weight(1f))
        MiniTile("با لوکیشن", s.optInt("located"), c.muted, Modifier.weight(1f))
      }
    }

    Spacer(Modifier.height(12.dp))
    Field(value = query, onValueChange = { query = it }, label = "جست‌وجو — نام، ایمیل یا محل")

    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Chip("همه", !onlyGuests) { onlyGuests = false }
      Chip("فقط مهمان‌ها", onlyGuests) { onlyGuests = true }
    }

    Spacer(Modifier.height(12.dp))
    ErrorNote(error)

    if (rows.isEmpty()) {
      Panel {
        Text(
          if (busy) "در حال خواندن…" else "هنوز کسی نیامده.",
          style = MaterialTheme.typography.bodySmall, color = c.muted,
        )
      }
    } else {
      Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
        Panel {
          rows.forEachIndexed { i, v ->
            val guest = v.optBoolean("guest")
            val name = v.optString("accountName").ifBlank {
              v.optString("name").ifBlank { "مهمان" }
            }
            val place = v.optJSONObject("location")

            Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
              Column(Modifier.weight(1f)) {
                Text(
                  name, style = MaterialTheme.typography.bodyMedium,
                  color = c.text, fontWeight = FontWeight.Medium,
                )
                Text(
                  listOfNotNull(
                    platformName(v.optString("platform")),
                    v.optString("accountEmail").ifBlank { null },
                    v.optString("shopName").ifBlank { null },
                    "${v.optInt("visits").fa()} بار",
                    jalali(v.optLong("lastSeenAt")),
                  ).joinToString(" · "),
                  style = MaterialTheme.typography.labelSmall, color = c.muted,
                )
              }
              Column(horizontalAlignment = Alignment.End) {
                if (guest) StatusChip("مهمان", c.warn) else StatusChip("حساب", c.success)
                if (place != null) {
                  TextButton(
                    onClick = {
                      //  نقشه — با هر برنامه‌ای که روی گوشی هست
                      val uri = Uri.parse("geo:${place.optDouble("lat")},${place.optDouble("lng")}?q=${place.optDouble("lat")},${place.optDouble("lng")}($name)")
                      runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                    },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                  ) {
                    Text("نقشه", style = MaterialTheme.typography.labelSmall, color = c.primary)
                  }
                }
              }
            }
            if (i < rows.size - 1) HorizontalDivider(color = c.border)
          }
        }
        Spacer(Modifier.height(24.dp))
      }
    }
  }
}

@Composable
private fun MiniTile(label: String, value: Int, tint: Color, modifier: Modifier = Modifier) {
  val c = Admin.colors
  Column(
    modifier.clip(RoundedCornerShape(14.dp)).background(c.surface).padding(vertical = 12.dp, horizontal = 6.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(value.fa(), style = MaterialTheme.typography.titleMedium, color = tint, fontWeight = FontWeight.Bold)
    Text(label, style = MaterialTheme.typography.labelSmall, color = c.muted, textAlign = TextAlign.Center)
  }
}

fun platformName(p: String): String = when (p) {
  "web" -> "سایت"
  "android" -> "اندروید"
  "ios" -> "آیفون"
  "desktop" -> "کامپیوتر"
  "" -> "نامعلوم"
  else -> p
}
