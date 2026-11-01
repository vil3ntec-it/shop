package ir.vil3ntec.tohid.ui.screens

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.chat.ChatBackoff
import ir.vil3ntec.tohid.core.chat.ChatConfig
import ir.vil3ntec.tohid.core.chat.ChatConversation
import ir.vil3ntec.tohid.core.chat.ChatMessage
import ir.vil3ntec.tohid.core.chat.ChatMeta
import ir.vil3ntec.tohid.core.chat.ChatQueue
import ir.vil3ntec.tohid.core.chat.ChatThread
import ir.vil3ntec.tohid.core.chat.OutboxItem
import ir.vil3ntec.tohid.core.i18n.StrChat
import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.data.ChatMedia
import ir.vil3ntec.tohid.data.ShopChatSync
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.formatMillis
import ir.vil3ntec.tohid.ui.Platform
import ir.vil3ntec.tohid.ui.components.common.PillTone
import ir.vil3ntec.tohid.ui.components.common.ScreenKind
import ir.vil3ntec.tohid.ui.components.common.SearchBox
import ir.vil3ntec.tohid.ui.components.common.SegmentTab
import ir.vil3ntec.tohid.ui.components.common.SegmentedTabs
import ir.vil3ntec.tohid.ui.components.common.StatusPill
import ir.vil3ntec.tohid.ui.components.common.TableToolbar
import ir.vil3ntec.tohid.ui.components.common.ViewMode
import ir.vil3ntec.tohid.ui.components.common.ViewModeState
import ir.vil3ntec.tohid.ui.components.common.rememberViewMode
import ir.vil3ntec.tohid.ui.theme.Radius
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 *  گفت‌وگو با مشتری — سمتِ کارمند (v3 §۳۳؛ اندروید و کامپیوتر از همین فایل).
 *
 *  ⛔ پیش‌فرض خاموش: تا `GET /api/shop/chat/config` نگوید `enabled`، نه ردیفی
 *  در «بیشتر» و نه این صفحه (باز شدن از راهِ دیگر ⇒ برمی‌گردد).
 *
 *    صندوق ─── جست‌وجو، باز/بسته/همه، نمای کارتی/جدولی (`ViewModePref`، هر
 *              کاربر و همین صفحه؛ گوشی همیشه کارتی). کامپیوتر/تبلت در نمای
 *              کارتی: فهرست راست و گفت‌وگو چپ (VISUAL-DIRECTION §۱ بندِ ۶).
 *    گفت‌وگو ─ long-poll با `after=<rev>`؛ «رسید» وقتی پیام به دستگاه آمد،
 *              «خوانده» فقط وقتی برنامه جلوی چشم است (CHAT-04).
 *    ارسال ─── صفِ دیسکی (`ShopChatSync`) با clientId و backoff — بی اینترنت
 *              نوشته می‌شود و پس از وصل یک بار می‌رود (CHAT-02/11).
 *    عکس ───── کوچک، چرخیده و بی EXIF روی همین دستگاه (`ChatMedia`)؛ عکسِ
 *              کامل فقط با زدن (کم‌مصرف، §۳۳.۲ بند ۶).
 *    صدا ───── اندروید: Opus/AAC؛ کامپیوتر رمزگذار ندارد ⇒ دکمهٔ ضبط پنهان و
 *              صدای رسیده «ذخیرهٔ فایل».
 *    شماره ─── کامل فقط با `orders.contact`، وگرنه همان شمارهٔ پوشیدهٔ سرور.
 */
@Composable
fun ShopChatScreen(onBack: () -> Unit) {
  val context = LocalContext.current
  val colors = Shop.colors
  val cfg = ShopChatSync.config.value
  var checked by remember { mutableStateOf(false) }
  LaunchedEffect(Unit) { ShopChatSync.refreshConfig(context); checked = true }

  if (cfg?.enabled != true) {
    BackHandler { onBack() }
    LaunchedEffect(checked, cfg) { if (checked && cfg?.enabled != true) onBack() }
    Box(Modifier.fillMaxSize().background(colors.bg), contentAlignment = Alignment.Center) {
      Text("در حالِ باز کردن…", color = colors.muted)
    }
    return
  }

  val view = rememberViewMode("chat-inbox", ScreenKind.MANAGEMENT)
  val split = screenSize() != Screen.PHONE && view.mode == ViewMode.CARD
  var openId by rememberSaveable { mutableStateOf<String?>(null) }
  val known = remember { mutableStateMapOf<String, ChatConversation>() }
  BackHandler { if (openId != null && !split) openId = null else onBack() }

  Column(Modifier.fillMaxSize().background(colors.bg)) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
      IconButton(onClick = { if (openId != null && !split) openId = null else onBack() }) {
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "بازگشت", tint = colors.primary)
      }
      Column(Modifier.weight(1f)) {
        Text("گفت‌وگو با مشتری", style = MaterialTheme.typography.titleMedium, color = colors.text, fontWeight = FontWeight.Bold)
        Text("پیام‌های مشتریانِ ویترین — بی اینترنت هم نوشته می‌شود و پس از وصل یک بار می‌رود",
          style = MaterialTheme.typography.labelSmall, color = colors.muted, maxLines = 2)
      }
    }
    HorizontalDivider(color = colors.border)
    val inbox: @Composable (Modifier) -> Unit = { m ->
      ChatInbox(view, openId, m, onList = { l -> l.forEach { known[it.id] = it } }) { openId = it.id }
    }
    val thread: @Composable (Modifier) -> Unit = { m ->
      val id = openId
      if (id == null) {
        Box(m, contentAlignment = Alignment.Center) { Text("یک گفت‌وگو را از فهرست باز کنید.", color = colors.muted) }
      } else {
        ChatThreadPane(cfg, id, known[id], m) { known[it.id] = it }
      }
    }
    when {
      split -> Row(Modifier.fillMaxSize()) {
        inbox(Modifier.weight(0.4f).fillMaxSize())
        Box(Modifier.width(1.dp).fillMaxSize().background(colors.border))
        thread(Modifier.weight(0.6f).fillMaxSize())
      }
      openId == null -> inbox(Modifier.fillMaxSize())
      else -> thread(Modifier.fillMaxSize())
    }
  }
}

// ════════════════════════════════════════════════════════════════════
//  صندوق
// ════════════════════════════════════════════════════════════════════

private val STATUS_TABS = listOf(SegmentTab("open", "باز"), SegmentTab("closed", "بسته"), SegmentTab("all", "همه"))
private val KIND_LABEL = mapOf("image" to "عکس", "audio" to "پیامِ صوتی", "file" to "فایل")

private fun previewOf(c: ChatConversation): String {
  val m = c.lastMessage ?: return "هنوز پیامی نیست"
  if (m.deleted) return "پیامِ پاک‌شده"
  val who = if (m.sender == "shop") "دکان: " else ""
  return who + (m.preview?.takeIf { it.isNotBlank() } ?: KIND_LABEL[m.kind].orEmpty())
}

private fun whenOf(c: ChatConversation): String = formatMillis(c.lastMessageAt ?: c.updatedAt)

@Composable
private fun ChatInbox(
  view: ViewModeState,
  selected: String?,
  modifier: Modifier,
  onList: (List<ChatConversation>) -> Unit,
  onOpen: (ChatConversation) -> Unit,
) {
  val context = LocalContext.current
  val colors = Shop.colors
  var status by rememberSaveable { mutableStateOf("open") }
  var q by rememberSaveable { mutableStateOf("") }
  var list by remember { mutableStateOf<List<ChatConversation>>(emptyList()) }
  var loading by remember { mutableStateOf(true) }
  var error by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(status, q) {
    if (q.isNotEmpty()) delay(300)
    while (isActive) {
      Backend.shopChat(context).conversations(status, q)
        .onSuccess { list = it.conversations; error = null; onList(it.conversations) }
        .onFailure { error = if (it.retryable) "اتصال نیست؛ فهرست پس از وصل شدن تازه می‌شود." else it.userMessage }
      loading = false
      delay(15_000)
    }
  }

  Column(modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
    if (screenSize() == Screen.PHONE) {
      SearchBox(q, { q = it }, "نامِ مشتری یا شماره…", Modifier.fillMaxWidth())
      Spacer(Modifier.padding(4.dp))
      SegmentedTabs(STATUS_TABS, status, { status = it })
    } else {
      TableToolbar(query = q, onQuery = { q = it }, placeholder = "نامِ مشتری یا شماره…", viewMode = view,
        tabs = { SegmentedTabs(STATUS_TABS, status, { status = it }) })
    }
    error?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = colors.danger, modifier = Modifier.padding(vertical = 6.dp)) }
    if (list.isEmpty()) {
      Text(
        when {
          loading -> "در حالِ خواندن…"
          q.isNotEmpty() -> "گفت‌وگویی با این نام یا شماره نیست."
          else -> "هنوز گفت‌وگویی نیست. وقتی مشتری از ویترین پیام بدهد، این‌جا می‌آید."
        },
        color = colors.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 24.dp),
      )
      return@Column
    }
    if (view.mode == ViewMode.TABLE) {
      val columns = remember {
        listOf<TableColumn<ChatConversation>>(
          TableColumn("مشتری", 160.dp, sortKey = { it.customer.name }) { it.customer.name.ifBlank { "مشتری" } },
          TableColumn("شماره", 140.dp) { it.customer.shownPhone },
          TableColumn("سفارش", 80.dp, sortKey = { it.orderNo ?: 0L }, numeric = true) { it.orderNo?.toString() ?: "—" },
          TableColumn("آخرین پیام", 220.dp) { previewOf(it) },
          TableColumn("زمان", 130.dp, sortKey = { it.lastMessageAt ?: it.updatedAt }) { whenOf(it) },
          TableColumn("وضعیت", 120.dp, sortKey = { it.unread }) {
            buildString {
              append(if (it.status == "open") "باز" else "بسته")
              if (it.unread > 0) append(" · ${it.unread} تازه")
              if (it.blockedCustomer) append(" · مسدود")
            }
          },
        )
      }
      val state = rememberTableState(columns)
      LazyColumn(Modifier.fillMaxSize()) {
        dataTable(sortRows(list, columns, state), columns, state, key = { it.id }, onOpen = onOpen, onMenu = onOpen)
      }
    } else {
      LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
        items(list, key = { it.id }) { c -> ConversationCard(c, c.id == selected) { onOpen(c) } }
      }
    }
  }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ConversationCard(c: ChatConversation, current: Boolean, onClick: () -> Unit) {
  val colors = Shop.colors
  Column(
    Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(Radius.sm))
      .background(if (current) colors.primaryTint else colors.surfaceSolid)
      .border(1.dp, if (current) colors.primary else colors.border, RoundedCornerShape(Radius.sm))
      .clickable(onClick = onClick)
      .heightIn(min = 48.dp)
      .padding(12.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(c.customer.name.ifBlank { "مشتری" }, fontWeight = FontWeight.Bold, color = colors.text, maxLines = 1,
        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
      Text(whenOf(c), style = MaterialTheme.typography.labelSmall, color = colors.muted)
    }
    Text(listOfNotNull(c.customer.shownPhone.ifBlank { null }, c.orderNo?.let { "سفارشِ $it" }).joinToString(" · "),
      style = MaterialTheme.typography.labelSmall, color = colors.muted)
    Text(previewOf(c), style = MaterialTheme.typography.bodySmall, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    Spacer(Modifier.padding(2.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      StatusPill(if (c.status == "open") "باز" else "بسته", if (c.status == "open") PillTone.SUCCESS else PillTone.NEUTRAL)
      if (c.unread > 0) StatusPill("${c.unread} تازه", PillTone.WARNING)
      if (c.blockedCustomer) StatusPill("مسدود", PillTone.DANGER)
      if (c.customerBlockedShop) StatusPill("مشتری بسته است", PillTone.DANGER)
    }
  }
}

// ════════════════════════════════════════════════════════════════════
//  گفت‌وگو
// ════════════════════════════════════════════════════════════════════

private val REASONS = listOf("spam" to "هرزنامه", "abuse" to "توهین یا آزار", "fraud" to "کلاه‌برداری", "other" to "دیگر")

private fun mmss(ms: Long): String { val s = (ms / 1000).toInt(); return "${s / 60}:${(s % 60).toString().padStart(2, '0')}" }
private fun kb(n: Long) = "${maxOf(1, (n + 512) / 1024)} کیلوبایت"

@Composable
private fun ChatThreadPane(
  cfg: ChatConfig,
  convId: String,
  initial: ChatConversation?,
  modifier: Modifier,
  onConversation: (ChatConversation) -> Unit,
) {
  val context = LocalContext.current
  val colors = Shop.colors
  val scope = rememberCoroutineScope()
  val repo = remember { Backend.shopChat(context) }
  val queue = remember { ShopChatSync.queue(context) }
  val thread = remember(convId) { ChatThread().also { it.conversation = initial } }
  var tick by remember(convId) { mutableIntStateOf(0) }
  var banner by remember(convId) { mutableStateOf<Pair<String, Boolean>?>(null) }
  var draft by rememberSaveable(convId) { mutableStateOf("") }
  var reportFor by remember(convId) { mutableStateOf<String?>(null) }
  val media = remember(convId) { mutableStateMapOf<String, ImageBitmap>() }
  val full = remember(convId) { mutableStateMapOf<String, Boolean>() }
  val queueVersion = ShopChatSync.version.intValue
  val foreground = ShopChatSync.foregroundState.value
  val listState = rememberLazyListState()
  val perms = cfg.permissions

  fun conv() = thread.conversation

  suspend fun receipts() {
    val need = thread.receiptNeed(ShopChatSync.foreground) ?: return
    repo.receipts(convId, need.deliveredSeq, need.readSeq).onSuccess { c ->
      thread.conversation = c; onConversation(c); tick++
    }
  }

  //  پیامی که از صف رفت بی انتظارِ long-poll دیده می‌شود
  DisposableEffect(convId) {
    ShopChatSync.onSent = { id, m -> if (id == convId && thread.add(m)) tick++ }
    onDispose { ShopChatSync.onSent = null }
  }

  LaunchedEffect(convId) {
    var first = true
    var fails = 0
    ShopChatSync.flush(context, convId)
    while (isActive) {
      val res = repo.changes(convId, thread.cursor, if (first) 0 else 20_000)
      val err = (res as? ir.vil3ntec.tohid.core.net.ApiResult.Failure)?.error
      if (err == null) {
        val ch = res.valueOrNull()!!
        first = false; fails = 0
        if (thread.apply(ch)) tick++
        queue?.reconcile(convId, ch.messages.filter { it.mine }.mapNotNull { it.clientId })?.forEach { ShopChatSync.delBlob(context, it) }
        thread.conversation?.let(onConversation)
        banner = null
        receipts()
        continue
      }
      if (err is ApiFailure.NotFound) {
        if (err.code == "chat_disabled") ShopChatSync.refreshConfig(context)
        banner = err.userMessage to true
        return@LaunchedEffect
      }
      fails++
      banner = (if (err.retryable) "اتصال نیست؛ پیام‌های نوشته‌شده در صف می‌مانند و پس از وصل می‌روند." else err.userMessage) to !err.retryable
      delay(ChatBackoff.delayMs(fails))
    }
  }

  //  برنامه دوباره جلوی چشم آمد ⇒ «خوانده» ِ پیام‌هایی که در پس‌زمینه رسیده بود
  LaunchedEffect(foreground) { if (foreground) receipts() }

  val server = remember(tick, convId) { thread.list() }
  val have = server.mapNotNull { it.clientId }.toSet()
  val local = remember(queueVersion, tick, convId) { queue?.forConv(convId)?.filter { it.clientId !in have }.orEmpty() }
  LaunchedEffect(server.size, local.size) { if (server.size + local.size > 0) listState.animateScrollToItem(server.size + local.size - 1) }

  suspend fun loadImage(key: String, fetch: suspend () -> ByteArray?) {
    if (media.containsKey(key)) return
    val bytes = fetch() ?: return
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { media[key] = it.asImageBitmap() }
  }

  val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
    if (uri == null) return@rememberLauncherForActivityResult
    banner = "آماده کردنِ عکس…" to false
    scope.launch {
      val prepared = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
        ChatMedia.prepareImage(context, uri, cfg.limits)
      }
      prepared.onSuccess { p ->
        val q = queue ?: return@onSuccess
        val id = ir.vil3ntec.tohid.core.chat.ChatIds.next(System.currentTimeMillis())
        ShopChatSync.putBlobs(context, "$id-main" to p.main, "$id-thumb" to p.thumb)?.let { banner = it to true; return@onSuccess }
        q.add(convId, kind = "image", body = draft.trim(), meta = ChatMeta(width = p.width, height = p.height),
          blobKey = "$id-main", thumbKey = "$id-thumb", size = p.main.size.toLong(), clientId = id)
        draft = ""
        banner = "عکس آماده شد (${kb(p.main.size.toLong())})" to false
        ShopChatSync.flush(context, convId)
      }.onFailure { banner = (it.message ?: "عکس آماده نشد") to true }
    }
  }

  val c = conv()
  val canWrite = perms.reply && (c?.canSend != false)

  Column(modifier) {
    //  ── سربرگ ──
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Text(c?.customer?.name?.ifBlank { null } ?: "مشتری", fontWeight = FontWeight.Bold, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(listOfNotNull(c?.customer?.shownPhone?.ifBlank { null }, c?.orderNo?.let { "سفارشِ $it" }).joinToString(" · "),
          style = MaterialTheme.typography.labelSmall, color = colors.muted)
      }
      var menu by remember { mutableStateOf(false) }
      OutlinedButton(onClick = { menu = true }, modifier = Modifier.heightIn(min = 44.dp)) { Text("کارها") }
      DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
        if (perms.reply && c != null) DropdownMenuItem(
          text = { Text(if (c.status == "open") "بستنِ گفت‌وگو" else "باز کردنِ گفت‌وگو") },
          onClick = {
            menu = false
            scope.launch {
              repo.setStatus(convId, if (c.status == "open") "closed" else "open")
                .onSuccess { thread.conversation = it; onConversation(it); tick++ }
                .onFailure { banner = it.userMessage to true }
            }
          },
        )
        DropdownMenuItem(text = { Text("گزارشِ این گفت‌وگو") }, onClick = { menu = false; reportFor = "" })
        if (perms.moderate && c != null) DropdownMenuItem(
          text = { Text(if (c.blockedCustomer) "برداشتنِ مسدودی" else "مسدود کردنِ مشتری", color = if (c.blockedCustomer) colors.text else colors.danger) },
          onClick = {
            menu = false
            scope.launch {
              repo.setBlock(convId, !c.blockedCustomer)
                .onSuccess { thread.conversation = it; onConversation(it); tick++ }
                .onFailure { banner = it.userMessage to true }
            }
          },
        )
      }
    }
    HorizontalDivider(color = colors.border)
    banner?.let { (text, bad) ->
      Text(text, style = MaterialTheme.typography.labelSmall, color = if (bad) colors.danger else colors.warning,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp))
    }
    reportFor?.let { mid ->
      ReportBox(isMessage = mid.isNotEmpty(), onCancel = { reportFor = null }) { reason, note ->
        scope.launch {
          repo.report(convId, reason, note, mid.ifEmpty { null })
            .onSuccess { dup -> reportFor = null; banner = (if (dup) "این مورد پیش‌تر گزارش شده بود." else "گزارش رسید؛ مدیرِ سامانه بررسی می‌کند.") to false }
            .onFailure { banner = it.userMessage to true }
        }
      }
    }

    //  ── پیام‌ها ──
    LazyColumn(
      state = listState,
      modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
      contentPadding = PaddingValues(vertical = 12.dp),
    ) {
      if (server.isEmpty() && local.isEmpty()) item { Text("هنوز پیامی نیست.", color = colors.muted, modifier = Modifier.padding(16.dp)) }
      items(server, key = { "s-" + it.id }) { m ->
        val thumbKey = m.media?.let { it.thumbId ?: it.id }
        if (m.kind == "image" && thumbKey != null) LaunchedEffect(thumbKey) { loadImage(thumbKey) { repo.media(convId, thumbKey).valueOrNull() } }
        ServerBubble(
          m = m,
          receipts = c?.receipts,
          image = thumbKey?.let { media[if (full[m.id] == true) m.media!!.id else it] ?: media[it] },
          onImage = {
            val id = m.media?.id ?: return@ServerBubble
            full[m.id] = true
            scope.launch { loadImage(id) { repo.media(convId, id).onFailure { e -> banner = e.userMessage to true }.valueOrNull() } }
          },
          onAudio = { m.media?.id?.let { id -> scope.launch { repo.media(convId, id).onSuccess { bytes -> playOrSave(context, id, bytes)?.let { banner = it to true } }.onFailure { banner = it.userMessage to true } } } },
          onReport = { reportFor = m.id },
        )
      }
      items(local, key = { "q-" + it.clientId }) { x ->
        val k = x.thumbKey ?: x.blobKey
        if (x.kind == "image" && k != null) LaunchedEffect(k) { loadImage("local-$k") { ShopChatSync.blob(context, k) } }
        LocalBubble(
          x = x,
          image = k?.let { media["local-$it"] },
          progress = ShopChatSync.progress[x.clientId],
          onRetry = { queue?.retry(x.clientId); ShopChatSync.flush(context, convId) },
          onCancel = { queue?.cancel(x.clientId)?.forEach { ShopChatSync.delBlob(context, it) } },
        )
      }
    }

    //  ── نوشتن ──
    HorizontalDivider(color = colors.border)
    if (!perms.reply) {
      Text("نقشِ شما اجازهٔ پاسخ ندارد؛ فقط خواندن.", color = colors.muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(12.dp))
      return@Column
    }
    if (c?.customerBlockedShop == true) {
      Text("مشتری پیامِ این دکان را بسته است.", color = colors.danger, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
    }
    VoiceBar(enabled = canWrite, limits = cfg.limits.audioMax, onBanner = { banner = it }) { bytes, ms ->
      val q = queue ?: return@VoiceBar
      val id = ir.vil3ntec.tohid.core.chat.ChatIds.next(System.currentTimeMillis())
      ShopChatSync.putBlobs(context, "$id-main" to bytes)?.let { banner = it to true; return@VoiceBar }
      q.add(convId, kind = "audio", meta = ChatMeta(durationMs = maxOf(1L, ms)), blobKey = "$id-main", size = bytes.size.toLong(), clientId = id)
      ShopChatSync.flush(context, convId)
    }
    Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      OutlinedTextField(
        value = draft,
        onValueChange = { if (it.length <= cfg.limits.bodyMax) draft = it },
        placeholder = { Text("پیام…", style = MaterialTheme.typography.bodySmall) },
        shape = RoundedCornerShape(Radius.sm),
        maxLines = 4,
        enabled = canWrite,
        modifier = Modifier.weight(1f),
      )
      IconButton(onClick = { pickImage.launch("image/*") }, enabled = canWrite, modifier = Modifier.size(48.dp)) {
        Icon(Icons.Filled.Image, contentDescription = "فرستادنِ عکس", tint = colors.primary)
      }
      FilledIconButton(
        onClick = {
          val body = draft.trim()
          if (body.isEmpty()) return@FilledIconButton
          draft = ""
          queue?.add(convId, kind = "text", body = body)
          ShopChatSync.flush(context, convId)
        },
        enabled = canWrite && draft.isNotBlank(),
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = colors.button.primary.bg, contentColor = colors.button.primary.text),
        modifier = Modifier.size(52.dp),
      ) {
        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "فرستادن", tint = colors.button.primary.text)
      }
    }
  }
}

/** صدا روی گوشی پخش می‌شود؛ کامپیوتر رمزگشای Opus/AAC ندارد ⇒ «ذخیرهٔ فایل». */
private var player: ChatMedia.Player? = null

/** @return پیامِ خطا برای نمایش، یا null */
private fun playOrSave(context: android.content.Context, id: String, bytes: ByteArray): String? {
  if (ChatMedia.voiceSupported) {
    val p = player ?: ChatMedia.Player(context.applicationContext).also { player = it }
    return runCatching { p.play(id, bytes) {} }.exceptionOrNull()?.let { e -> if (e is ChatMedia.LowStorage) e.message else "این پیامِ صوتی روی این گوشی پخش نشد" }
  } else {
    val ext = when {
      bytes.size > 4 && String(bytes, 0, 4, Charsets.ISO_8859_1) == "OggS" -> "ogg"
      bytes.size > 4 && bytes[0] == 0x1A.toByte() && bytes[1] == 0x45.toByte() -> "webm"
      else -> "m4a"
    }
    Platform.saveFile("voice-$id.$ext", bytes)
    return null
  }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ServerBubble(
  m: ChatMessage,
  receipts: ir.vil3ntec.tohid.core.chat.ChatReceipts?,
  image: ImageBitmap?,
  onImage: () -> Unit,
  onAudio: () -> Unit,
  onReport: () -> Unit,
) {
  val colors = Shop.colors
  val mine = m.sender == "shop"
  Bubble(mine) {
    if (m.deleted) {
      Text("این پیام پاک شد", color = colors.muted, style = MaterialTheme.typography.bodySmall)
    } else {
      when (m.kind) {
        "image" -> Box(
          Modifier.widthIn(max = 240.dp).heightIn(min = 64.dp, max = 320.dp).clip(RoundedCornerShape(10.dp))
            .background(colors.border).clickable(onClick = onImage).semantics { contentDescription = "عکس؛ برای نمایشِ کامل بزنید" },
        ) { image?.let { Image(it, contentDescription = null, contentScale = ContentScale.Fit) } }
        "audio" -> TextButton(onClick = onAudio, modifier = Modifier.heightIn(min = 44.dp)) {
          val dur = m.meta.durationMs?.let { " · " + mmss(it) }.orEmpty()
          Text((if (ChatMedia.voiceSupported) "پخشِ پیامِ صوتی" else "ذخیرهٔ پیامِ صوتی") + dur)
        }
        "file" -> Text("فایلِ PDF", color = colors.muted)
      }
      if (m.body.isNotBlank()) Text(m.body, style = MaterialTheme.typography.bodyMedium, color = colors.text)
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.Center) {
      Text(formatMillis(m.createdAt), style = MaterialTheme.typography.labelSmall, color = colors.muted2)
      if (m.editedAt != null) Text("ویرایش‌شده", style = MaterialTheme.typography.labelSmall, color = colors.muted2)
      if (mine && !m.deleted) {
        val st = ChatThread.statusOf(m, receipts)
        Text(ChatThread.LABEL[st].orEmpty(), style = MaterialTheme.typography.labelSmall, color = if (st == "read") colors.success else colors.muted2)
      }
      if (!mine && !m.deleted) Text("گزارش", style = MaterialTheme.typography.labelSmall, color = colors.primary,
        modifier = Modifier.clickable(onClick = onReport).padding(horizontal = 4.dp, vertical = 2.dp))
    }
  }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun LocalBubble(x: OutboxItem, image: ImageBitmap?, progress: Int?, onRetry: () -> Unit, onCancel: () -> Unit) {
  val colors = Shop.colors
  Bubble(mine = true) {
    when (x.kind) {
      "image" -> Box(Modifier.widthIn(max = 240.dp).heightIn(min = 64.dp, max = 320.dp).clip(RoundedCornerShape(10.dp)).background(colors.border)) {
        image?.let { Image(it, contentDescription = "عکسِ در صف", contentScale = ContentScale.Fit) }
      }
      "audio" -> Text("پیامِ صوتی" + (x.meta.durationMs?.let { " · " + mmss(it) }.orEmpty()), color = colors.text)
    }
    if (x.body.isNotBlank()) Text(x.body, style = MaterialTheme.typography.bodyMedium, color = colors.text)
    //  CHAT-05: درصدِ رفته فقط با `ShopPolicy.chatMediaGuard` (خاموش ⇒ `progress` همیشه null)
    val pct = progress?.takeIf { x.state == ChatQueue.SENDING }
    if (pct != null) LinearProgressIndicator(progress = { pct / 100f }, modifier = Modifier.width(200.dp).padding(top = 2.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(formatMillis(x.createdAt), style = MaterialTheme.typography.labelSmall, color = colors.muted2)
      val label = if (pct != null) StrChat.sending(pct)
        else ChatThread.LABEL[x.state].orEmpty() + if (x.state == ChatQueue.FAILED && x.error.isNotBlank()) " — ${x.error}" else ""
      Text(label, style = MaterialTheme.typography.labelSmall,
        color = when (x.state) { ChatQueue.FAILED, ChatQueue.AUTH -> colors.danger; ChatQueue.SENDING -> colors.muted2; else -> colors.warning })
      if (x.state != ChatQueue.SENDING) {
        if (x.state != ChatQueue.QUEUED) Text("دوباره", color = colors.primary, style = MaterialTheme.typography.labelSmall,
          modifier = Modifier.clickable(onClick = onRetry).padding(horizontal = 4.dp, vertical = 2.dp))
        Text("لغو", color = colors.primary, style = MaterialTheme.typography.labelSmall,
          modifier = Modifier.clickable(onClick = onCancel).padding(horizontal = 4.dp, vertical = 2.dp))
      }
    }
  }
}

@Composable
private fun Bubble(mine: Boolean, content: @Composable () -> Unit) {
  val colors = Shop.colors
  Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
    Column(
      Modifier
        .widthIn(max = 340.dp)
        .clip(RoundedCornerShape(Radius.sm))
        .background(if (mine) colors.primaryTint else colors.surface)
        .then(if (mine) Modifier else Modifier.border(1.dp, colors.border, RoundedCornerShape(Radius.sm)))
        .padding(horizontal = 12.dp, vertical = 9.dp),
      verticalArrangement = Arrangement.spacedBy(3.dp),
    ) { content() }
  }
}

@Composable
private fun ReportBox(isMessage: Boolean, onCancel: () -> Unit, onSend: (String, String) -> Unit) {
  val colors = Shop.colors
  var reason by remember { mutableStateOf("spam") }
  var note by remember { mutableStateOf("") }
  Column(
    Modifier.fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(Radius.sm)).border(1.dp, colors.border, RoundedCornerShape(Radius.sm)).padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(if (isMessage) "گزارشِ این پیام به مدیرِ سامانه" else "گزارشِ این گفت‌وگو به مدیرِ سامانه", fontWeight = FontWeight.Bold, color = colors.text)
    SegmentedTabs(REASONS.map { SegmentTab(it.first, it.second) }, reason, { reason = it })
    OutlinedTextField(note, { if (it.length <= 500) note = it }, placeholder = { Text("توضیح (اختیاری)") }, modifier = Modifier.fillMaxWidth(), maxLines = 3)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      androidx.compose.material3.Button(onClick = { onSend(reason, note) }) { Text("فرستادنِ گزارش") }
      OutlinedButton(onClick = onCancel) { Text("انصراف") }
    }
  }
}

/**
 *  ضبطِ پیامِ صوتی: اجازهٔ صریحِ میکروفون، زمان‌سنج، پیش از فرستادن مدت و
 *  حجم، «لغو». روی کامپیوتر اصلاً دیده نمی‌شود (رمزگذار نیست) و جمله‌ای
 *  کوتاه چرایش را می‌گوید.
 */
@Composable
private fun VoiceBar(enabled: Boolean, limits: Long, onBanner: (Pair<String, Boolean>?) -> Unit, onSend: (ByteArray, Long) -> Unit) {
  val context = LocalContext.current
  val colors = Shop.colors
  if (!ChatMedia.voiceSupported) {
    Text("ضبطِ صدا روی کامپیوتر پشتیبانی نمی‌شود؛ پیامِ صوتیِ مشتری را می‌توانید ذخیره و گوش کنید.",
      style = MaterialTheme.typography.labelSmall, color = colors.muted, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
    return
  }
  val recorder = remember { ChatMedia.Recorder(context) }
  var recording by remember { mutableStateOf(false) }
  var elapsed by remember { mutableLongStateOf(0L) }
  var ready by remember { mutableStateOf<Pair<ByteArray, Long>?>(null) }
  DisposableEffect(Unit) { onDispose { recorder.cancel() } }
  LaunchedEffect(recording) {
    while (recording) {
      elapsed = recorder.elapsedMs
      if (elapsed >= ChatMedia.VOICE_MAX_MS) { ready = recorder.stop(); recording = false }
      delay(300)
    }
  }
  fun start() {
    runCatching { recorder.start() }
      .onSuccess { recording = true; ready = null; onBanner(null) }
      .onFailure { onBanner("ضبط شروع نشد: ${it.message ?: ""}" to true) }
  }
  val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
    if (granted) start() else onBanner("بی اجازهٔ میکروفون صدا ضبط نمی‌شود." to true)
  }
  Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    when {
      recording -> {
        Text("● در حالِ ضبط ${mmss(elapsed)}", color = colors.danger, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = { ready = recorder.stop(); recording = false }) { Text("پایانِ ضبط") }
        TextButton(onClick = { recorder.cancel(); recording = false }) { Text("لغو") }
      }
      ready != null -> {
        val (bytes, ms) = ready!!
        val big = limits > 0 && bytes.size > limits
        Text("پیامِ صوتی ${mmss(ms)} · ${kb(bytes.size.toLong())}" + if (big) " — بزرگ‌تر از سقف" else "",
          color = if (big) colors.danger else colors.text, modifier = Modifier.weight(1f))
        androidx.compose.material3.Button(onClick = { onSend(bytes, ms); ready = null }, enabled = !big && enabled) { Text("فرستادنِ صدا") }
        TextButton(onClick = { ready = null }) { Text("لغو") }
      }
      else -> {
        Spacer(Modifier.weight(1f))
        IconButton(
          onClick = {
            val granted = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) ==
              android.content.pm.PackageManager.PERMISSION_GRANTED
            if (granted) start() else ask.launch(android.Manifest.permission.RECORD_AUDIO)
          },
          enabled = enabled,
          modifier = Modifier.size(48.dp),
        ) { Icon(Icons.Filled.Mic, contentDescription = "ضبطِ پیامِ صوتی", tint = colors.primary) }
      }
    }
  }
}
