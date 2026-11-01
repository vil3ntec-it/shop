package ir.vil3ntec.tohid.ui.v3.online.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.core.chat.ChatConfig
import ir.vil3ntec.tohid.core.chat.ChatConversation
import ir.vil3ntec.tohid.core.i18n.StrOnline
import ir.vil3ntec.tohid.data.ChatMedia
import ir.vil3ntec.tohid.data.ShopChatSync
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.V3TwoPane
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.online.OnlinePaths
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** گفت‌وگوهایی که صندوق دیده — سرِ گفت‌وگو پیش از رسیدنِ رشته از همین پُر می‌شود */
internal object ChatHub {
  val known = mutableStateMapOf<String, ChatConversation>()
}

/** `chat` — صندوق (هر ۱۵ ثانیه تازه)؛ کامپیوترِ پهن: صندوق | گفت‌وگو */
@Composable
internal fun RouteScope.ChatRoute() {
  val context = LocalContext.current
  LaunchedEffect(Unit) { ShopChatSync.refreshConfig(context) }
  var status by rememberSaveable { mutableStateOf("open") }
  var query by rememberSaveable { mutableStateOf("") }
  var list by remember { mutableStateOf<List<ChatConversation>>(emptyList()) }
  var loading by remember { mutableStateOf(true) }
  var error by remember { mutableStateOf<String?>(null) }
  LaunchedEffect(status, query) {
    if (query.isNotEmpty()) delay(SEARCH_DEBOUNCE_MS)
    while (isActive) {
      Backend.shopChat(context).conversations(status, query)
        .onSuccess { r -> list = r.conversations; error = null; r.conversations.forEach { ChatHub.known[it.id] = it } }
        .onFailure { error = if (it.retryable) StrOnline.ctOffline else it.userMessage }
      loading = false
      delay(INBOX_POLL_MS)
    }
  }
  val wide = desk
  var selected by rememberSaveable { mutableStateOf<String?>(null) }
  val state = InboxState(list, loading, error, status, query, System.currentTimeMillis())
  val inbox: @Composable () -> Unit = {
    ChatInboxScreen(
      state = state,
      onQuery = { query = it },
      onStatus = { status = it },
      onOpen = { c -> if (wide) selected = c.id else nav.open(OnlinePaths.conversation(c.id)) },
      onBack = backOrNull,
      selectedId = if (wide) selected else null,
    )
  }
  if (!wide) { inbox(); return }
  V3TwoPane(list = inbox, detail = {
    val id = selected
    if (id == null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { V3EmptyState(Icons.Outlined.Forum, StrOnline.ctSelect) }
    else ChatThreadRoute(id, embedded = true)
  })
}

/** `chat/{conversationId}` (یا ستونِ دومِ کامپیوتر) */
@Composable
internal fun RouteScope.ChatThreadRoute(id: String, embedded: Boolean = false) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val cfg = ShopChatSync.config.value ?: ChatConfig()
  val ctl = remember(id) { ChatThreadController(context, id, ChatHub.known[id], cfg) { ChatHub.known[it.id] = it } }
  DisposableEffect(id) {
    ShopChatSync.onSent = { cid, m -> ctl.onSent(cid, m) }
    onDispose { ShopChatSync.onSent = null }
  }
  LaunchedEffect(id) { ctl.run() }
  //  برنامه دوباره جلوی چشم آمد ⇒ «خوانده» ِ پیام‌هایی که در پس‌زمینه رسیده بود
  val foreground = ShopChatSync.foregroundState.value
  LaunchedEffect(foreground) { if (foreground) ctl.receipts() }

  var draft by rememberSaveable(id) { mutableStateOf("") }
  var reportFor by remember(id) { mutableStateOf<String?>(null) }
  var reporting by remember(id) { mutableStateOf(false) }
  val queueVersion = ShopChatSync.version.intValue
  val tick = ctl.tick
  val server = remember(tick, id) { ctl.messages() }
  val local = remember(queueVersion, tick, id) { ctl.local(server) }
  val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
    if (uri != null) scope.launch { if (ctl.sendImage(uri, draft)) draft = "" }
  }
  val voice = rememberVoiceState()
  val view = ThreadView(
    conversation = ctl.conversation,
    server = server,
    local = local,
    banner = ctl.banner,
    canReply = cfg.permissions.reply,
    canModerate = cfg.permissions.moderate,
    imageOf = ctl::imageOf,
    localImageOf = ctl::imageOf,
  )
  ChatThreadScreen(
    view = view,
    actions = ThreadActions(
      onOrder = { orderId -> nav.open(OnlinePaths.order(orderId)) },
      onToggleStatus = { scope.launch { ctl.toggleStatus() } },
      onToggleBlock = { scope.launch { ctl.toggleBlock() } },
      onReport = { mid -> reportFor = mid.orEmpty() },
      onImage = { m -> scope.launch { ctl.openFull(m) } },
      onAudio = { m -> scope.launch { ctl.audio(m) } },
      onRetry = ctl::retry,
      onCancel = ctl::cancel,
      loadThumb = { m -> ctl.thumb(m) },
      loadLocal = { x -> ctl.localThumb(x) },
    ),
    onBack = if (embedded) null else backOrNull,
    composer = {
      ChatComposer(
        draft = draft,
        onDraft = { if (it.length <= cfg.limits.bodyMax) draft = it },
        enabled = ctl.canWrite,
        onSend = { ctl.sendText(draft); draft = "" },
        onPhoto = { pickImage.launch("image/*") },
        mic = if (ChatMedia.voiceSupported) ({ VoiceMic(voice, ctl.canWrite) { ctl.banner = it } }) else null,
        status = { VoiceStatus(voice, cfg.limits.audioMax, ctl.canWrite) { bytes, ms -> ctl.sendVoice(bytes, ms) } },
      )
    },
  )
  reportFor?.let { mid ->
    ChatReportSheet(
      forMessage = mid.isNotEmpty(),
      busy = reporting,
      onDismiss = { reportFor = null },
      onSend = { reason, note ->
        reporting = true
        scope.launch {
          if (ctl.report(reason, note, mid.ifEmpty { null })) reportFor = null
          reporting = false
        }
      },
    )
  }
}

private const val INBOX_POLL_MS = 15_000L
private const val SEARCH_DEBOUNCE_MS = 300L
