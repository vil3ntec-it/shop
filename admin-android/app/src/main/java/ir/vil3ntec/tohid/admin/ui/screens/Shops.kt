package ir.vil3ntec.tohid.admin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.admin.net.AdminApi
import ir.vil3ntec.tohid.admin.net.Session
import ir.vil3ntec.tohid.admin.ui.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 *  دکان‌ها — جایی که اشتراک داده می‌شود.
 *
 *  اشتراک به **دکان** بسته است نه به کاربر: یک دکان یک اشتراک دارد و
 *  شاگردهایش زیرِ همان کار می‌کنند. اگر به کاربر بسته بود، برای هر شاگرد
 *  باید جدا پول گرفته می‌شد.
 */
@Composable
fun ShopsScreen(session: Session) {
  val c = Admin.colors

  var query by rememberSaveable { mutableStateOf("") }
  var rows by remember { mutableStateOf<JSONArray?>(null) }
  var busy by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }
  var open by remember { mutableStateOf<String?>(null) }
  var reloadKey by remember { mutableStateOf(0) }

  LaunchedEffect(query, reloadKey) {
    val token = session.token ?: return@LaunchedEffect
    delay(350)
    busy = true
    runCatching { AdminApi(session.serverUrl).shops(token, query.trim()) }
      .onSuccess { rows = it; error = null }
      .onFailure { error = (it as? AdminApi.ApiError)?.message ?: "فهرست خوانده نشد" }
    busy = false
  }

  open?.let { id ->
    ShopSheet(session, id, onBack = { open = null; reloadKey += 1 })
    return
  }

  Column(Modifier.fillMaxSize().background(c.bg).padding(16.dp)) {
    Field(value = query, onValueChange = { query = it }, label = "جست‌وجو — نام دکان، صاحبش یا شماره")
    Spacer(Modifier.height(12.dp))
    ErrorNote(error)

    val list = rows
    if (list == null || list.length() == 0) {
      Panel {
        Text(
          if (busy) "در حال خواندن…" else "دکانی نیست.",
          style = MaterialTheme.typography.bodySmall,
          color = c.muted,
        )
      }
    } else {
      Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
        Panel {
          for (i in 0 until list.length()) {
            val s = list.optJSONObject(i) ?: continue
            val state = s.optString("sub_status")
            ClickRow(
              title = s.optString("name").ifBlank { "دکان" },
              subtitle = "${s.optString("owner_name")} · ${s.optString("owner_phone")}".fa(),
              trailing = { SubChip(state, s.optLong("ends_at")) },
            ) { open = s.optString("id") }
            if (i < list.length() - 1) HorizontalDivider(color = c.border)
          }
        }
        Spacer(Modifier.height(24.dp))
      }
    }
  }
}

@Composable
private fun SubChip(status: String, endsAt: Long) {
  val c = Admin.colors
  when (status) {
    "active" -> StatusChip("فعال تا ${jalali(endsAt)}", c.success)
    "trial" -> StatusChip("آزمایشی", c.warn)
    "suspended" -> StatusChip("معلق", c.warn)
    "expired" -> StatusChip("منقضی", c.danger)
    "cancelled" -> StatusChip("لغو شده", c.danger)
    "" -> StatusChip("بدون اشتراک", c.muted)
    else -> StatusChip(status, c.muted)
  }
}

/**
 *  پروندهٔ دکان: اشتراک، کارمندان، و دفترِ تغییرها.
 *
 *  همهٔ کارهای اشتراک از همین‌جا انجام می‌شود — دادن، تمدید، معلق کردن،
 *  لغو. تصمیمِ تاریخ سمتِ سرور گرفته می‌شود؛ این صفحه فقط می‌خواهد.
 */
@Composable
private fun ShopSheet(session: Session, shopId: String, onBack: () -> Unit) {
  val c = Admin.colors
  val scope = rememberCoroutineScope()

  var data by remember { mutableStateOf<JSONObject?>(null) }
  var plans by remember { mutableStateOf<JSONArray?>(null) }
  var history by remember { mutableStateOf<JSONArray?>(null) }
  var busy by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }
  var done by remember { mutableStateOf<String?>(null) }
  var granting by remember { mutableStateOf(false) }

  fun load() {
    val token = session.token ?: return
    busy = true
    scope.launch {
      val api = AdminApi(session.serverUrl)
      runCatching { api.shop(token, shopId) }
        .onSuccess { data = it; error = null }
        .onFailure { error = (it as? AdminApi.ApiError)?.message ?: "خوانده نشد" }
      plans = runCatching { api.plans(token) }.getOrNull()
      history = runCatching { api.shopHistory(token, shopId) }.getOrNull()
      busy = false
    }
  }
  LaunchedEffect(shopId) { load() }

  val d = data
  val live = d?.optJSONArray("subscriptions")?.optJSONObject(0)

  if (granting) {
    GrantSheet(
      plans = plans,
      current = live,
      busy = busy,
      onBack = { granting = false },
      onGrant = { plan, days, note ->
        val token = session.token ?: return@GrantSheet
        busy = true
        scope.launch {
          runCatching { AdminApi(session.serverUrl).grant(token, shopId, plan, days, note) }
            .onSuccess { granting = false; done = "اشتراک ثبت شد"; load() }
            .onFailure { error = (it as? AdminApi.ApiError)?.message ?: "ثبت نشد"; busy = false }
        }
      },
    )
    return
  }

  Column(
    Modifier.fillMaxSize().background(c.bg).verticalScroll(rememberScrollState()).padding(16.dp)
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      IconButton(onClick = onBack) {
        Icon(Icons.Filled.ArrowForward, contentDescription = "برگشت", tint = c.text)
      }
      Text("پروندهٔ دکان", style = MaterialTheme.typography.titleMedium, color = c.text, fontWeight = FontWeight.Bold)
    }
    Spacer(Modifier.height(10.dp))
    ErrorNote(error)
    done?.let {
      Text(it, style = MaterialTheme.typography.bodySmall, color = c.success, modifier = Modifier.padding(bottom = 8.dp))
    }

    if (d == null) {
      Panel { Text(if (busy) "در حال خواندن…" else "چیزی نیست.", style = MaterialTheme.typography.bodySmall, color = c.muted) }
      return@Column
    }

    val shop = d.optJSONObject("shop") ?: JSONObject()
    val ent = d.optJSONObject("entitlement")

    Panel {
      Text(shop.optString("name"), style = MaterialTheme.typography.titleMedium, color = c.text, fontWeight = FontWeight.Bold)
      Spacer(Modifier.height(8.dp))
      Row2("شناسه", shop.optString("id"))
      Row2("ساخته شده", jalali(shop.optLong("createdAt")))
    }

    Spacer(Modifier.height(14.dp))
    SectionTitle("اشتراک")
    Panel {
      if (live == null) {
        Text("این دکان اشتراکی ندارد.", style = MaterialTheme.typography.bodySmall, color = c.muted)
      } else {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          Text(
            planName(live.optString("plan")),
            style = MaterialTheme.typography.titleSmall,
            color = c.text,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
          )
          SubChip(live.optString("status"), live.optLong("ends_at"))
        }
        Spacer(Modifier.height(8.dp))
        Row2("شروع", jalali(live.optLong("starts_at")))
        Row2("پایان", jalali(live.optLong("ends_at")))
        ent?.let {
          Row2("سقف دستگاه", it.optInt("maxDevices", live.optInt("max_devices")).fa())
        }
        live.optString("note").takeIf { it.isNotBlank() }?.let { Row2("یادداشت", it) }
      }
    }

    Spacer(Modifier.height(12.dp))
    PrimaryButton(
      text = if (live == null) "فعال کردن اشتراک" else "تمدید یا تغییر",
      modifier = Modifier.fillMaxWidth(),
      enabled = !busy,
    ) { done = null; granting = true }

    if (live != null) {
      Spacer(Modifier.height(8.dp))
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val subId = live.optString("id")
        val suspended = live.optString("status") == "suspended"
        GhostButton(
          text = if (suspended) "برگرداندن" else "معلق کردن",
          modifier = Modifier.weight(1f),
          enabled = !busy,
          tint = c.warn,
        ) {
          val token = session.token ?: return@GhostButton
          busy = true
          scope.launch {
            runCatching {
              AdminApi(session.serverUrl).setSubscriptionStatus(token, subId, if (suspended) "active" else "suspended")
            }.onSuccess { done = "وضعیت عوض شد"; load() }
              .onFailure { error = (it as? AdminApi.ApiError)?.message ?: "انجام نشد"; busy = false }
          }
        }
        GhostButton(text = "لغو اشتراک", modifier = Modifier.weight(1f), enabled = !busy, tint = c.danger) {
          val token = session.token ?: return@GhostButton
          busy = true
          scope.launch {
            runCatching { AdminApi(session.serverUrl).setSubscriptionStatus(token, subId, "cancelled") }
              .onSuccess { done = "اشتراک لغو شد"; load() }
              .onFailure { error = (it as? AdminApi.ApiError)?.message ?: "انجام نشد"; busy = false }
          }
        }
      }
    }

    Spacer(Modifier.height(16.dp))
    SectionTitle("کارمندان")
    val members = d.optJSONArray("members") ?: JSONArray()
    Panel {
      if (members.length() == 0) {
        Text("کسی عضو نیست.", style = MaterialTheme.typography.bodySmall, color = c.muted)
      } else {
        for (i in 0 until members.length()) {
          val m = members.optJSONObject(i) ?: continue
          Row2(m.optString("name").ifBlank { "بی‌نام" }, roleName(m.optString("role")))
        }
      }
    }

    Spacer(Modifier.height(16.dp))
    SectionTitle("دفتر تغییرهای اشتراک")
    val h = history
    Panel {
      if (h == null || h.length() == 0) {
        Text("تغییری ثبت نشده.", style = MaterialTheme.typography.bodySmall, color = c.muted)
      } else {
        for (i in 0 until h.length()) {
          val row = h.optJSONObject(i) ?: continue
          val from = row.optLong("prev_ends_at")
          val to = row.optLong("new_ends_at")
          Column(Modifier.padding(vertical = 6.dp)) {
            Text(
              actionName(row.optString("action")) + " · " + planName(row.optString("plan")),
              style = MaterialTheme.typography.bodySmall,
              color = c.text,
              fontWeight = FontWeight.Medium,
            )
            Text(
              if (from > 0) "پایان: ${jalali(from)} ← ${jalali(to)}" else "پایان: ${jalali(to)}",
              style = MaterialTheme.typography.labelSmall,
              color = c.muted,
            )
            Text(jalali(row.optLong("created_at")), style = MaterialTheme.typography.labelSmall, color = c.muted)
          }
          if (i < h.length() - 1) HorizontalDivider(color = c.border)
        }
      }
    }
    Spacer(Modifier.height(30.dp))
  }
}

/**
 *  دادن یا تمدیدِ اشتراک.
 *
 *  پلن‌ها از دیتابیس می‌آیند — هیچ‌کدام اینجا نوشته نشده‌اند. اگر فردا
 *  پلنِ تازه‌ای اضافه کنید، همین‌جا خودش می‌آید.
 *
 *  «مدتِ دلخواه» هم هست، برای وقتی که با کسی جور دیگری حساب کرده‌اید.
 */
@Composable
private fun GrantSheet(
  plans: JSONArray?,
  current: JSONObject?,
  busy: Boolean,
  onBack: () -> Unit,
  onGrant: (plan: String, days: Int?, note: String) -> Unit,
) {
  val c = Admin.colors
  var picked by rememberSaveable { mutableStateOf("") }
  var customDays by rememberSaveable { mutableStateOf("") }
  var note by rememberSaveable { mutableStateOf("") }

  val custom = picked == "custom"
  val days = customDays.filter { it.isDigit() }.toIntOrNull()

  Column(
    Modifier.fillMaxSize().background(c.bg).verticalScroll(rememberScrollState()).imePadding().padding(16.dp)
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      IconButton(onClick = onBack) {
        Icon(Icons.Filled.ArrowForward, contentDescription = "برگشت", tint = c.text)
      }
      Text("اشتراک", style = MaterialTheme.typography.titleMedium, color = c.text, fontWeight = FontWeight.Bold)
    }

    if (current != null && current.optString("status") == "active") {
      Spacer(Modifier.height(8.dp))
      Panel {
        Text(
          "این دکان تا ${jalali(current.optLong("ends_at"))} اشتراک دارد. مدتِ تازه به همان اضافه می‌شود، نه از امروز.",
          style = MaterialTheme.typography.bodySmall,
          color = c.muted,
        )
      }
    }

    Spacer(Modifier.height(14.dp))
    SectionTitle("پلن")
    Panel {
      if (plans == null || plans.length() == 0) {
        Text("پلنی از سرور خوانده نشد.", style = MaterialTheme.typography.bodySmall, color = c.muted)
      } else {
        for (i in 0 until plans.length()) {
          val p = plans.optJSONObject(i) ?: continue
          val code = p.optString("code")
          PlanRow(
            title = p.optString("title").ifBlank { planName(code) },
            subtitle = periodText(p.optInt("amount"), p.optString("unit")),
            selected = picked == code,
          ) { picked = code }
        }
      }
      PlanRow(title = "مدت دلخواه", subtitle = "روزها را خودتان می‌زنید", selected = custom) { picked = "custom" }
    }

    if (custom) {
      Spacer(Modifier.height(12.dp))
      Field(
        value = customDays,
        onValueChange = { customDays = it.filter { ch -> ch.isDigit() }.take(4) },
        label = "چند روز",
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
      )
    }

    Spacer(Modifier.height(12.dp))
    Field(value = note, onValueChange = { note = it }, label = "یادداشت (اختیاری) — مثلاً شمارهٔ رسید")

    Spacer(Modifier.height(18.dp))
    PrimaryButton(
      text = "ثبت",
      modifier = Modifier.fillMaxWidth(),
      enabled = picked.isNotBlank() && (!custom || (days != null && days > 0)),
      busy = busy,
    ) { onGrant(picked, if (custom) days else null, note.trim()) }

    Text(
      "تاریخ‌ها را سرور حساب می‌کند، نه ساعت این گوشی.",
      style = MaterialTheme.typography.labelSmall,
      color = c.muted,
      modifier = Modifier.padding(top = 10.dp),
    )
    Spacer(Modifier.height(30.dp))
  }
}

@Composable
private fun PlanRow(title: String, subtitle: String, selected: Boolean, onPick: () -> Unit) {
  val c = Admin.colors
  Row(
    Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(12.dp))
      .background(if (selected) c.primary.copy(alpha = 0.12f) else Color.Transparent)
      .padding(vertical = 10.dp, horizontal = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    RadioButton(
      selected = selected,
      onClick = onPick,
      colors = RadioButtonDefaults.colors(selectedColor = c.primary, unselectedColor = c.muted),
    )
    Column(Modifier.weight(1f)) {
      Text(title, style = MaterialTheme.typography.bodyMedium, color = c.text, fontWeight = FontWeight.Medium)
      if (subtitle.isNotBlank()) {
        Text(subtitle, style = MaterialTheme.typography.labelSmall, color = c.muted)
      }
    }
  }
}

private fun periodText(amount: Int, unit: String): String {
  if (amount <= 0) return ""
  val name = when (unit) {
    "day" -> "روز"; "month" -> "ماه"; "year" -> "سال"; else -> unit
  }
  return "$amount $name".fa()
}

fun planName(code: String): String = when (code) {
  "custom" -> "دلخواه"
  "trial" -> "آزمایشی"
  "" -> "—"
  else -> code
}

private fun actionName(action: String): String = when (action) {
  "grant" -> "صدور"
  "renew" -> "تمدید"
  "status" -> "تغییر وضعیت"
  "expire" -> "انقضا"
  else -> action
}
