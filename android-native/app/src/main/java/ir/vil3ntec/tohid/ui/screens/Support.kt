package ir.vil3ntec.tohid.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.model.SupportMessageDto
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.formatMillis
import ir.vil3ntec.tohid.sync.SyncStore
import ir.vil3ntec.tohid.ui.theme.Radius
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 *  پشتیبانی — نوشتن به کسی که برنامه را ساخته.
 *
 *  ── چرا این صفحه هست ───────────────────────────────────────────────
 *  تا دیروز اگر کسی مشکلی داشت، تنها راهش واتساپ بود: بیرون از برنامه،
 *  بی هیچ ردی که کنارِ پروندهٔ دکانش بماند، و برای کسی که هنوز ثبت‌نام
 *  نکرده اصلاً پیدا نبود.
 *
 *  ── بدونِ حساب هم کار می‌کند ───────────────────────────────────────
 *  همان کسی که همان اولِ کار گیر کرده — «چرا ثبت‌نام نمی‌شود؟» — بیشتر
 *  از همه به این نیاز دارد. گفت‌وگو به شناسهٔ همین گوشی بسته می‌شود و
 *  هر وقت حساب ساخت، به حسابش می‌چسبد. پس از اول توضیح نمی‌دهد.
 *
 *  ── وقتی برنامه بسته است ───────────────────────────────────────────
 *  پاسخِ پشتیبانی به شکلِ پوش به گوشی می‌رسد، اگر روی سرور تنظیم شده
 *  باشد. اگر نه، پیام گم نمی‌شود؛ دفعهٔ بعد که برنامه باز شد اینجاست و
 *  نقطهٔ قرمزش هم روی همان دکمه‌ای که از آن آمده‌اید.
 */
@Composable
fun SupportScreen(onBack: () -> Unit) {
  val context = LocalContext.current
  val colors = Shop.colors
  val scope = rememberCoroutineScope()
  val state = remember { SyncStore(context) }
  val listState = rememberLazyListState()

  var messages by remember { mutableStateOf<List<SupportMessageDto>>(emptyList()) }
  var greeting by remember { mutableStateOf("") }
  var draft by rememberSaveable { mutableStateOf("") }
  var loading by remember { mutableStateOf(true) }
  var sending by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }
  var lastAt by remember { mutableLongStateOf(0L) }

  BackHandler { onBack() }

  suspend fun load(after: Long) {
    Backend.support(context).thread(state.deviceUid, after)
      .onSuccess { view ->
        val fresh = view.messages
        messages = if (after == 0L) fresh else messages + fresh
        if (fresh.isNotEmpty()) lastAt = fresh.last().createdAt
        if (view.greeting.isNotBlank()) greeting = view.greeting
        error = null
      }
      .onFailure { error = it.userMessage }
    loading = false
  }

  LaunchedEffect(Unit) {
    load(0)
    //  باز کردنِ صفحه یعنی «دیدم» — نقطهٔ قرمز پاک می‌شود
    Backend.support(context).markRead(state.deviceUid)
  }

  //  فقط پیام‌های تازه، نه کلِ گفت‌وگو دوباره — روی نتِ دکان فرقش زیاد است
  LaunchedEffect(lastAt) {
    while (true) {
      delay(8_000)
      load(lastAt)
    }
  }

  LaunchedEffect(messages.size) {
    if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
  }

  Column(Modifier.fillMaxSize().background(colors.bg)) {
    Row(
      Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(onClick = onBack) {
        Icon(Icons.Filled.ArrowForward, contentDescription = "بازگشت", tint = colors.primary)
      }
      Column(Modifier.weight(1f)) {
        Text(
          "پشتیبانی",
          style = MaterialTheme.typography.titleMedium,
          color = colors.text,
          fontWeight = FontWeight.Bold,
        )
        Text(
          "معمولاً همان روز جواب می‌گیرید",
          style = MaterialTheme.typography.labelSmall,
          color = colors.muted,
        )
      }
    }
    HorizontalDivider(color = colors.border)

    error?.let {
      Text(
        it,
        style = MaterialTheme.typography.labelSmall,
        color = colors.danger,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
      )
    }

    if (messages.isEmpty() && !loading) {
      //  صفحهٔ خالی سرد است؛ یک جمله می‌گوید اینجا برای چیست
      Column(
        Modifier.weight(1f).fillMaxWidth().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Text(
          greeting.ifBlank { "هر مشکلی یا سؤالی دارید همین‌جا بنویسید — پاسخ می‌دهیم." },
          style = MaterialTheme.typography.bodyMedium,
          color = colors.muted,
        )
      }
    } else {
      LazyColumn(
        state = listState,
        modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
      ) {
        items(messages) { m -> Bubble(m) }
      }
    }

    HorizontalDivider(color = colors.border)
    Row(
      Modifier.fillMaxWidth().padding(10.dp),
      verticalAlignment = Alignment.Bottom,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      OutlinedTextField(
        value = draft,
        onValueChange = { draft = it },
        placeholder = { Text("پیام شما…", style = MaterialTheme.typography.bodySmall) },
        shape = RoundedCornerShape(Radius.sm),
        maxLines = 4,
        modifier = Modifier.weight(1f),
      )
      FilledIconButton(
        onClick = {
          val body = draft.trim()
          if (body.isEmpty()) return@FilledIconButton
          sending = true
          scope.launch {
            Backend.support(context)
              .sendMessage(state.deviceUid, state.accountName, body)
              .onSuccess { draft = ""; load(lastAt) }
              .onFailure { error = it.userMessage }
            sending = false
          }
        },
        enabled = !sending && draft.isNotBlank(),
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = colors.primary),
        modifier = Modifier.size(52.dp),
      ) {
        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "فرستادن", tint = Color.White)
      }
    }
  }
}

/**
 *  یک پیام.
 *
 *  سه شکل: پیامِ خودم (راست، رنگی)، پاسخِ پشتیبانی (چپ، سطحِ کارت)، و
 *  خبرِ خودکارِ سامانه — مثلاً «اشتراکت سه روز دیگر تمام می‌شود» — که
 *  عمداً رنگِ هشدار دارد تا با گفت‌وگوی آدم‌ها قاطی نشود.
 */
@Composable
private fun Bubble(m: SupportMessageDto) {
  val colors = Shop.colors
  val mine = m.sender == "user"
  val system = m.sender == "system"

  Row(
    Modifier.fillMaxWidth(),
    horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
  ) {
    Column(
      Modifier
        .widthIn(max = 300.dp)
        .clip(RoundedCornerShape(Radius.sm))
        .background(
          when {
            mine -> colors.primaryTint
            system -> colors.warningTint
            else -> colors.surface
          }
        )
        .then(
          if (mine || system) Modifier
          else Modifier.border(1.dp, colors.border, RoundedCornerShape(Radius.sm))
        )
        .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
      if (system) {
        Text(
          "خبر از توحید",
          style = MaterialTheme.typography.labelSmall,
          color = colors.warning,
          fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(3.dp))
      } else if (!mine) {
        Text(
          m.senderName.ifBlank { "پشتیبانی" },
          style = MaterialTheme.typography.labelSmall,
          color = colors.primary,
          fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(3.dp))
      }
      Text(m.body, style = MaterialTheme.typography.bodyMedium, color = colors.text)
      Spacer(Modifier.height(3.dp))
      Text(
        formatMillis(m.createdAt),
        style = MaterialTheme.typography.labelSmall,
        color = colors.muted2,
      )
    }
  }
}
