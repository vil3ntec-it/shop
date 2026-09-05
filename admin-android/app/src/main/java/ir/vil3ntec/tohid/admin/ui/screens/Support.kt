package ir.vil3ntec.tohid.admin.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.admin.net.AdminApi
import ir.vil3ntec.tohid.admin.net.Session
import ir.vil3ntec.tohid.admin.ui.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 *  پشتیبانی — گفت‌وگو با کسانی که در برنامه یا سایت پیام داده‌اند.
 *
 *  ── چرا این صفحه هست ───────────────────────────────────────────────
 *  تا امروز راهی نبود: کاربر مشکل داشت و فقط می‌توانست زنگ بزند یا
 *  واتساپ بفرستد — و آن پیام هیچ‌جا کنارِ پروندهٔ دکانش نمی‌نشست.
 *
 *  ── چه کسی اینجاست ─────────────────────────────────────────────────
 *  هم دکان‌دارها، هم مهمان‌هایی که هنوز حساب نساخته‌اند. دومی‌ها مهم‌ترین
 *  گروهند: کسی که همان اول کار گیر کرده و اگر جوابش را نگیرد، دیگر
 *  برنمی‌گردد.
 *
 *  ── تازه شدن ───────────────────────────────────────────────────────
 *  هر پنج ثانیه فقط پیام‌های **بعد از** آخرین پیام خوانده می‌شوند، نه
 *  کلِ گفت‌وگو. روی نتِ ضعیف فرقش زیاد است.
 */
@Composable
fun SupportScreen(session: Session, onUnreadChange: (Int) -> Unit = {}) {
  val c = Admin.colors
  val scope = rememberCoroutineScope()

  var threads by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
  var unread by remember { mutableIntStateOf(0) }
  var filter by rememberSaveable { mutableStateOf("") }
  var query by rememberSaveable { mutableStateOf("") }
  var busy by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }
  var open by remember { mutableStateOf<String?>(null) }
  var broadcasting by remember { mutableStateOf(false) }

  suspend fun load() {
    val token = session.token ?: return
    busy = true
    runCatching { AdminApi(session.serverUrl).supportThreads(token, filter, query.trim()) }
      .onSuccess { body ->
        val arr = body.optJSONArray("threads")
        threads = (0 until (arr?.length() ?: 0)).mapNotNull { arr?.optJSONObject(it) }
        unread = body.optInt("unread")
        onUnreadChange(unread)
        error = null
      }
      .onFailure { error = (it as? AdminApi.ApiError)?.message ?: "فهرست خوانده نشد" }
    busy = false
  }

  LaunchedEffect(filter, query) { delay(300); load() }

  //  تا وقتی این صفحه باز است، هر ده ثانیه یک بار نگاه می‌کنیم
  LaunchedEffect(open) {
    while (open == null) {
      delay(10_000)
      load()
    }
  }

  open?.let { id ->
    ThreadScreen(session, id) { open = null; scope.launch { load() } }
    return
  }

  if (broadcasting) {
    BroadcastSheet(session) { broadcasting = false }
    return
  }

  Column(Modifier.fillMaxSize().background(c.bg).padding(16.dp)) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Text("پشتیبانی", style = MaterialTheme.typography.titleMedium, color = c.text, fontWeight = FontWeight.Bold)
        Text(
          if (unread > 0) "${unread.fa()} پیام خوانده‌نشده" else "همه خوانده شده",
          style = MaterialTheme.typography.labelSmall,
          color = if (unread > 0) c.warn else c.muted,
        )
      }
      GhostButton("پیام همگانی") { broadcasting = true }
    }

    Spacer(Modifier.height(12.dp))
    Field(value = query, onValueChange = { query = it }, label = "جست‌وجو — نام یا متن پیام")

    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Chip("همه", filter == "") { filter = "" }
      Chip("باز", filter == "open") { filter = "open" }
      Chip("بسته", filter == "closed") { filter = "closed" }
    }

    Spacer(Modifier.height(12.dp))
    ErrorNote(error)

    if (threads.isEmpty()) {
      Panel {
        Text(
          if (busy) "در حال خواندن…" else "هنوز کسی پیامی نداده.",
          style = MaterialTheme.typography.bodySmall,
          color = c.muted,
        )
      }
    } else {
      Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
        Panel {
          threads.forEachIndexed { i, t ->
            val name = t.optString("accountName").ifBlank {
              t.optString("who").ifBlank { "مهمان" }
            }
            val guest = t.optString("userId").isBlank()
            ClickRow(
              title = if (guest) "$name (مهمان)" else name,
              subtitle = t.optString("lastMessage").take(60).ifBlank { "—" },
              trailing = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                  if (t.optInt("unreadAdmin") > 0) {
                    StatusChip(t.optInt("unreadAdmin").fa(), c.danger)
                  }
                  if (t.optString("status") == "closed") StatusChip("بسته", c.muted)
                }
              },
            ) { open = t.optString("id") }
            if (i < threads.size - 1) HorizontalDivider(color = c.border)
          }
        }
        Spacer(Modifier.height(24.dp))
      }
    }
  }
}

/**
 *  یک گفت‌وگو.
 *
 *  باز کردنش یعنی «دیدم» — سرور همان‌جا خوانده‌نشده‌ها را صفر می‌کند، پس
 *  نقطهٔ قرمز الکی نمی‌ماند.
 */
@Composable
private fun ThreadScreen(session: Session, threadId: String, onBack: () -> Unit) {
  val c = Admin.colors
  val scope = rememberCoroutineScope()
  val listState = rememberLazyListState()

  var thread by remember { mutableStateOf<JSONObject?>(null) }
  var messages by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
  var draft by rememberSaveable { mutableStateOf("") }
  var sending by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }
  var lastAt by remember { mutableLongStateOf(0L) }

  BackHandler { onBack() }

  suspend fun load(after: Long) {
    val token = session.token ?: return
    runCatching { AdminApi(session.serverUrl).supportThread(token, threadId, after) }
      .onSuccess { body ->
        thread = body.optJSONObject("thread")
        val arr = body.optJSONArray("messages")
        val fresh = (0 until (arr?.length() ?: 0)).mapNotNull { arr?.optJSONObject(it) }
        messages = if (after == 0L) fresh else messages + fresh
        if (fresh.isNotEmpty()) lastAt = fresh.last().optLong("createdAt")
        error = null
      }
      .onFailure { error = (it as? AdminApi.ApiError)?.message ?: "خوانده نشد" }
  }

  LaunchedEffect(threadId) { load(0) }

  //  فقط تازه‌ها؛ نه کلِ گفت‌وگو دوباره
  LaunchedEffect(threadId, lastAt) {
    while (true) {
      delay(5_000)
      load(lastAt)
    }
  }

  LaunchedEffect(messages.size) {
    if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
  }

  val t = thread
  Column(Modifier.fillMaxSize().background(c.bg)) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
      IconButton(onClick = onBack) {
        Icon(Icons.Filled.ArrowForward, contentDescription = "برگشت", tint = c.text)
      }
      Column(Modifier.weight(1f)) {
        val title = when {
          t == null -> "…"
          t.optString("accountName").isNotBlank() -> t.optString("accountName")
          t.optString("who").isNotBlank() -> t.optString("who")
          else -> "مهمان"
        }
        Text(title, style = MaterialTheme.typography.titleSmall, color = c.text, fontWeight = FontWeight.Bold)
        val sub = if (t == null) "" else listOfNotNull(
          t.optString("accountEmail").ifBlank { null },
          t.optString("shopName").ifBlank { null },
          if (t.optString("userId").isBlank()) "بدون حساب" else null,
        ).joinToString(" · ")
        if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.labelSmall, color = c.muted)
      }
      if (t != null) {
        val closed = t.optString("status") == "closed"
        GhostButton(if (closed) "باز کردن" else "بستن", tint = if (closed) c.success else c.muted) {
          val token = session.token ?: return@GhostButton
          scope.launch {
            runCatching {
              AdminApi(session.serverUrl).supportStatus(token, threadId, if (closed) "open" else "closed")
            }.onSuccess { load(0) }
          }
        }
      }
    }
    HorizontalDivider(color = c.border)

    ErrorNote(error)

    LazyColumn(
      state = listState,
      modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
      contentPadding = PaddingValues(vertical = 12.dp),
    ) {
      items(messages) { m -> Bubble(m) }
    }

    HorizontalDivider(color = c.border)
    Row(
      Modifier.fillMaxWidth().padding(10.dp),
      verticalAlignment = Alignment.Bottom,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Field(
        value = draft,
        onValueChange = { draft = it },
        label = "پاسخ شما",
        singleLine = false,
        modifier = Modifier.weight(1f),
      )
      FilledIconButton(
        onClick = {
          val token = session.token ?: return@FilledIconButton
          val body = draft.trim()
          if (body.isEmpty()) return@FilledIconButton
          sending = true
          scope.launch {
            runCatching { AdminApi(session.serverUrl).supportReply(token, threadId, body) }
              .onSuccess { draft = ""; load(lastAt) }
              .onFailure { error = (it as? AdminApi.ApiError)?.message ?: "فرستاده نشد" }
            sending = false
          }
        },
        enabled = !sending && draft.isNotBlank(),
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = c.primary),
        modifier = Modifier.size(52.dp),
      ) {
        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "فرستادن")
      }
    }
  }
}

@Composable
private fun Bubble(m: JSONObject) {
  val c = Admin.colors
  val sender = m.optString("sender")
  val mine = sender == "admin"
  val system = sender == "system"

  Row(
    Modifier.fillMaxWidth(),
    horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
  ) {
    Column(
      Modifier
        .widthIn(max = 300.dp)
        .clip(RoundedCornerShape(16.dp))
        .background(
          when {
            mine -> c.primary.copy(alpha = 0.20f)
            system -> c.warn.copy(alpha = 0.14f)
            else -> c.surface
          }
        )
        .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
      if (system) {
        Text("خبر خودکار", style = MaterialTheme.typography.labelSmall, color = c.warn, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(3.dp))
      }
      Text(m.optString("body"), style = MaterialTheme.typography.bodyMedium, color = c.text)
      Spacer(Modifier.height(3.dp))
      Text(jalali(m.optLong("createdAt")), style = MaterialTheme.typography.labelSmall, color = c.muted)
    }
  }
}

/**
 *  پیام همگانی.
 *
 *  عمداً پیش‌فرضش «کسانی که اشتراکشان رو به پایان است» است، نه «همه»:
 *  آن یکی همان کاری است که واقعاً لازم می‌شود، و «همه» یک اشتباهِ
 *  کوچک را به هزار پیام تبدیل می‌کند.
 */
@Composable
private fun BroadcastSheet(session: Session, onBack: () -> Unit) {
  val c = Admin.colors
  val scope = rememberCoroutineScope()
  var body by rememberSaveable { mutableStateOf("") }
  var target by rememberSaveable { mutableStateOf("expiring") }
  var busy by remember { mutableStateOf(false) }
  var result by remember { mutableStateOf<String?>(null) }
  var error by remember { mutableStateOf<String?>(null) }

  BackHandler { onBack() }

  Column(Modifier.fillMaxSize().background(c.bg).verticalScroll(rememberScrollState()).padding(16.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      IconButton(onClick = onBack) {
        Icon(Icons.Filled.ArrowForward, contentDescription = "برگشت", tint = c.text)
      }
      Text("پیام همگانی", style = MaterialTheme.typography.titleMedium, color = c.text, fontWeight = FontWeight.Bold)
    }

    Spacer(Modifier.height(12.dp))
    Panel {
      SectionTitle("به چه کسانی")
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip("رو به پایان", target == "expiring") { target = "expiring" }
        Chip("اشتراک‌دارها", target == "active") { target = "active" }
        Chip("همه", target == "all") { target = "all" }
      }
      Spacer(Modifier.height(8.dp))
      Text(
        when (target) {
          "expiring" -> "کسانی که اشتراکشان تا هفت روز دیگر تمام می‌شود."
          "active" -> "همهٔ دکان‌هایی که الان اشتراک فعال دارند."
          else -> "همهٔ دکان‌ها. با این یکی محتاط باشید."
        },
        style = MaterialTheme.typography.labelSmall,
        color = if (target == "all") c.warn else c.muted,
      )
    }

    Spacer(Modifier.height(12.dp))
    Field(value = body, onValueChange = { body = it }, label = "متن پیام", singleLine = false)

    Spacer(Modifier.height(14.dp))
    ErrorNote(error)
    result?.let {
      Text(it, style = MaterialTheme.typography.bodySmall, color = c.success, modifier = Modifier.padding(bottom = 8.dp))
    }

    PrimaryButton("فرستادن", Modifier.fillMaxWidth(), enabled = body.isNotBlank(), busy = busy) {
      val token = session.token ?: return@PrimaryButton
      busy = true
      scope.launch {
        runCatching { AdminApi(session.serverUrl).broadcast(token, body.trim(), target) }
          .onSuccess {
            result = "به ${it.optInt("sent").fa()} نفر رفت."
            body = ""
            error = null
          }
          .onFailure { error = (it as? AdminApi.ApiError)?.message ?: "فرستاده نشد" }
        busy = false
      }
    }
    Text(
      "پیام در گفت‌وگوی پشتیبانیِ هر کس می‌نشیند — در برنامه و سایت هر دو دیده می‌شود. اگر پوش تنظیم باشد، گوشیِ بسته را هم بیدار می‌کند.",
      style = MaterialTheme.typography.labelSmall,
      color = c.muted,
      modifier = Modifier.padding(top = 10.dp),
    )
    Spacer(Modifier.height(30.dp))
  }
}

/** برچسبِ انتخابی — همان چیزی که در چند صفحه لازم می‌شود */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
  val c = Admin.colors
  FilterChip(
    selected = selected,
    onClick = onClick,
    label = { Text(label, style = MaterialTheme.typography.labelMedium) },
    shape = RoundedCornerShape(20.dp),
    colors = FilterChipDefaults.filterChipColors(
      containerColor = c.surface,
      labelColor = c.muted,
      selectedContainerColor = c.primary.copy(alpha = 0.18f),
      selectedLabelColor = c.primary,
    ),
    border = FilterChipDefaults.filterChipBorder(
      enabled = true, selected = selected,
      borderColor = c.border, selectedBorderColor = c.primary.copy(alpha = 0.5f),
    ),
  )
}
