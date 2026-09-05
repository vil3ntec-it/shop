package ir.vil3ntec.tohid.admin.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
 *  برنامه‌ها و سایت‌های دیگر.
 *
 *  ── قرار صاحب مخزن ─────────────────────────────────────────────────
 *  «این پنل فقط برای شاپ نباشد؛ برنامه‌ها و سایت‌های دیگرم را هم از
 *  همین‌جا اداره کنم.»
 *
 *  ── از هر برنامه چه می‌بینید ────────────────────────────────────────
 *  بالا هست یا نه، چند نفر آمده‌اند (چند تایشان مهمان)، و چند گفت‌وگوی
 *  پشتیبانیِ باز دارد.
 *
 *  ── چرا سلامت را سرور می‌سنجد ──────────────────────────────────────
 *  اگر این گوشی به سایت وصل می‌شد، سایتِ سالمی که پشت فیلتر یا روی نتِ
 *  ضعیف بود «خراب» نشان داده می‌شد. سرور همیشه همان‌جاست و جوابش
 *  یکی است.
 */
@Composable
fun AppsScreen(session: Session) {
  val c = Admin.colors
  val scope = rememberCoroutineScope()

  var apps by remember { mutableStateOf<JSONArray?>(null) }
  var busy by remember { mutableStateOf(false) }
  var checking by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }
  var adding by remember { mutableStateOf(false) }
  var editing by remember { mutableStateOf<JSONObject?>(null) }

  fun load() {
    val token = session.token ?: return
    busy = true
    scope.launch {
      runCatching { AdminApi(session.serverUrl).apps(token) }
        .onSuccess { apps = it; error = null }
        .onFailure { error = (it as? AdminApi.ApiError)?.message ?: "خوانده نشد" }
      busy = false
    }
  }
  LaunchedEffect(Unit) { load() }

  if (adding) {
    AppSheet(session, null) { changed -> adding = false; if (changed) load() }
    return
  }
  editing?.let { a ->
    AppSheet(session, a) { changed -> editing = null; if (changed) load() }
    return
  }

  Column(Modifier.fillMaxSize().background(c.bg).verticalScroll(rememberScrollState()).padding(16.dp)) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Text("برنامه‌ها", style = MaterialTheme.typography.titleMedium, color = c.text, fontWeight = FontWeight.Bold)
        Text("همهٔ برنامه‌ها و سایت‌های شما، یک‌جا.", style = MaterialTheme.typography.labelSmall, color = c.muted)
      }
      GhostButton("افزودن") { adding = true }
    }

    Spacer(Modifier.height(14.dp))
    ErrorNote(error)

    val list = apps
    if (list == null || list.length() == 0) {
      Panel {
        Text(
          if (busy) "در حال خواندن…" else "هنوز برنامه‌ای اضافه نکرده‌اید.",
          style = MaterialTheme.typography.bodySmall, color = c.muted,
        )
      }
    } else {
      for (i in 0 until list.length()) {
        val a = list.optJSONObject(i) ?: continue
        Panel {
          Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
              Text(
                a.optString("title").ifBlank { a.optString("slug") },
                style = MaterialTheme.typography.titleSmall, color = c.text, fontWeight = FontWeight.Bold,
              )
              Text(
                listOfNotNull(kindName(a.optString("kind")), a.optString("url").ifBlank { null }).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall, color = c.muted,
              )
            }
            //  سلامت: اگر نشانیِ سلامت نداده باشید، چیزی ادعا نمی‌شود
            when {
              a.optString("healthUrl").isBlank() -> {}
              a.isNull("lastOk") -> StatusChip("سنجیده نشده", c.muted)
              a.optBoolean("lastOk") -> StatusChip("بالا · ${a.optInt("lastMs").fa()}ms", c.success)
              else -> StatusChip("پایین", c.danger)
            }
          }

          Spacer(Modifier.height(10.dp))
          Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Stat("بازدید", a.optInt("visitors"))
            Stat("مهمان", a.optInt("guests"))
            Stat("پشتیبانی باز", a.optInt("openThreads"))
          }

          if (!a.optBoolean("lastOk", true) && a.optString("lastError").isNotBlank()) {
            Text(
              a.optString("lastError"),
              style = MaterialTheme.typography.labelSmall, color = c.danger,
              modifier = Modifier.padding(top = 6.dp),
            )
          }

          Spacer(Modifier.height(10.dp))
          GhostButton("ویرایش", Modifier.fillMaxWidth()) { editing = a }
        }
        Spacer(Modifier.height(10.dp))
      }
    }

    Spacer(Modifier.height(8.dp))
    PrimaryButton("سنجیدن سلامتِ همه", Modifier.fillMaxWidth(), busy = checking) {
      val token = session.token ?: return@PrimaryButton
      checking = true
      scope.launch {
        runCatching { AdminApi(session.serverUrl).checkApps(token) }
          .onSuccess { apps = it; error = null }
          .onFailure { error = (it as? AdminApi.ApiError)?.message ?: "سنجیده نشد" }
        checking = false
      }
    }
    Spacer(Modifier.height(30.dp))
  }
}

@Composable
private fun Stat(label: String, value: Int) {
  val c = Admin.colors
  Column {
    Text(value.fa(), style = MaterialTheme.typography.titleSmall, color = c.text, fontWeight = FontWeight.Bold)
    Text(label, style = MaterialTheme.typography.labelSmall, color = c.muted)
  }
}

/** افزودن یا ویرایش. `app == null` یعنی تازه. */
@Composable
private fun AppSheet(session: Session, app: JSONObject?, onDone: (Boolean) -> Unit) {
  val c = Admin.colors
  val scope = rememberCoroutineScope()
  val existing = app          //  برای اینکه Kotlin بتواند null نبودنش را بفهمد

  var slug by rememberSaveable { mutableStateOf(existing?.optString("slug") ?: "") }
  var title by rememberSaveable { mutableStateOf(existing?.optString("title") ?: "") }
  var kind by rememberSaveable { mutableStateOf(existing?.optString("kind") ?: "app") }
  var url by rememberSaveable { mutableStateOf(existing?.optString("url") ?: "") }
  var healthUrl by rememberSaveable { mutableStateOf(existing?.optString("healthUrl") ?: "") }
  var note by rememberSaveable { mutableStateOf(existing?.optString("note") ?: "") }
  var busy by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }
  var key by remember { mutableStateOf<String?>(null) }

  BackHandler { onDone(false) }

  Column(Modifier.fillMaxSize().background(c.bg).verticalScroll(rememberScrollState()).padding(16.dp)) {
    Header(if (existing == null) "برنامهٔ تازه" else "ویرایشِ ${existing.optString("title")}") { onDone(false) }

    Spacer(Modifier.height(12.dp))
    if (existing == null) {
      Field(
        value = slug,
        onValueChange = { slug = it.lowercase().filter { ch -> ch.isLetterOrDigit() || ch == '-' } },
        label = "نام کوتاه انگلیسی (my-site)",
      )
      Text(
        "همین نام در تپشِ بازدیدِ آن برنامه فرستاده می‌شود. بعداً عوض نمی‌شود.",
        style = MaterialTheme.typography.labelSmall, color = c.muted,
        modifier = Modifier.padding(top = 4.dp),
      )
      Spacer(Modifier.height(10.dp))
    }

    Field(value = title, onValueChange = { title = it }, label = "نام")
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Chip("برنامه", kind == "app") { kind = "app" }
      Chip("سایت", kind == "site") { kind = "site" }
      Chip("سرویس", kind == "service") { kind = "service" }
    }
    Spacer(Modifier.height(10.dp))
    Field(value = url, onValueChange = { url = it.trim() }, label = "نشانی")
    Spacer(Modifier.height(10.dp))
    Field(value = healthUrl, onValueChange = { healthUrl = it.trim() }, label = "نشانیِ سلامت (اختیاری)")
    Text(
      "اگر پرش کنید، سرور هر چند ساعت یک بار می‌سنجد که بالاست یا نه.",
      style = MaterialTheme.typography.labelSmall, color = c.muted,
      modifier = Modifier.padding(top = 4.dp),
    )
    Spacer(Modifier.height(10.dp))
    Field(value = note, onValueChange = { note = it }, label = "یادداشت", singleLine = false)

    Spacer(Modifier.height(14.dp))
    ErrorNote(error)

    PrimaryButton(if (existing == null) "افزودن" else "ذخیره", Modifier.fillMaxWidth(), busy = busy) {
      val token = session.token ?: return@PrimaryButton
      busy = true
      scope.launch {
        runCatching {
          val api = AdminApi(session.serverUrl)
          if (existing == null) api.createApp(token, slug.trim(), title.trim(), kind, url, healthUrl)
          else api.updateApp(
            token, existing.optString("id"),
            JSONObject().put("title", title.trim()).put("kind", kind)
              .put("url", url).put("healthUrl", healthUrl).put("note", note),
          )
        }
          .onSuccess { onDone(true) }
          .onFailure { error = (it as? AdminApi.ApiError)?.message ?: "انجام نشد"; busy = false }
      }
    }

    if (existing != null) {
      Spacer(Modifier.height(16.dp))
      Panel {
        SectionTitle("کلید")
        Text(
          "اگر آن برنامه بخواهد خودش به این سرور خبر بدهد (بازدید، خطا)، این کلید را لازم دارد. " +
            "کلید فقط همان لحظهٔ ساخت دیده می‌شود.",
          style = MaterialTheme.typography.bodySmall, color = c.muted,
        )
        key?.let {
          Spacer(Modifier.height(10.dp))
          SelectionContainerText(it)
        }
        Spacer(Modifier.height(10.dp))
        GhostButton(
          if (existing.optBoolean("keySet")) "کلید تازه (قبلی می‌میرد)" else "ساختن کلید",
          Modifier.fillMaxWidth(),
        ) {
          val token = session.token ?: return@GhostButton
          scope.launch {
            runCatching { AdminApi(session.serverUrl).rotateAppKey(token, existing.optString("id")) }
              .onSuccess { key = it }
              .onFailure { error = (it as? AdminApi.ApiError)?.message ?: "ساخته نشد" }
          }
        }
      }

      Spacer(Modifier.height(12.dp))
      GhostButton("بایگانی کردن", Modifier.fillMaxWidth(), tint = c.danger) {
        val token = session.token ?: return@GhostButton
        busy = true
        scope.launch {
          runCatching { AdminApi(session.serverUrl).archiveApp(token, existing.optString("id")) }
            .onSuccess { onDone(true) }
            .onFailure { error = (it as? AdminApi.ApiError)?.message ?: "انجام نشد"; busy = false }
        }
      }
      Text(
        "پاک نمی‌شود — بازدیدها و گفت‌وگوهایش می‌مانند، فقط از فهرست بیرون می‌رود.",
        style = MaterialTheme.typography.labelSmall, color = c.muted,
        modifier = Modifier.padding(top = 6.dp),
      )
    }
    Spacer(Modifier.height(30.dp))
  }
}

@Composable
private fun SelectionContainerText(text: String) {
  androidx.compose.foundation.text.selection.SelectionContainer {
    Text(
      text,
      style = MaterialTheme.typography.bodySmall,
      color = Admin.colors.text,
      fontWeight = FontWeight.Bold,
    )
  }
}

fun kindName(kind: String): String = when (kind) {
  "app" -> "برنامه"
  "site" -> "سایت"
  "service" -> "سرویس"
  else -> kind
}
