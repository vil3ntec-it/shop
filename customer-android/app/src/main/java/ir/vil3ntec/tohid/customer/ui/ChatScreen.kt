package ir.vil3ntec.tohid.customer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.shopper.ChatAct
import ir.vil3ntec.tohid.core.shopper.ChatActs
import ir.vil3ntec.tohid.core.shopper.ChatDeleteScope
import ir.vil3ntec.tohid.core.shopper.ChatMessage
import ir.vil3ntec.tohid.core.shopper.ChatOutItem
import ir.vil3ntec.tohid.core.shopper.ChatOutbox
import ir.vil3ntec.tohid.core.shopper.ChatThread
import ir.vil3ntec.tohid.core.shopper.ShopperText

/**
 *  گفت‌وگو با دکان (CHAT-02/06): رشتهٔ پیام‌ها از سرور (long-poll)، پیام‌های صف زیرِ رشته با حالِ روشن («در صف»،
 *  «در حالِ فرستادن»، «منتظرِ تأیید»، «نرفت» با «دوباره/لغو»)، رسیدِ پیامِ خودم، و فرستادنِ عکس (چرخیده و بی EXIF).
 *  «بسته/مشکل دارم/گفت‌وگوی تازه» فقط از `life` ِ سرور. «کارهای این پیام» ⇐ «حذف برای من/برای همه» با پرسشِ تأیید
 *  (CHAT-08)، سپس «پاسخ»، «ویرایش» و «سنجاق» (CHAT-14)؛ جست‌وجو در سرِ صفحه؛ پیامِ صوتی (CHAT-07) با میکروفون فقط با
 *  پرچمِ دکان و پخش‌کنندهٔ حباب. قاعده‌ها در core (`ChatDeletes`، `ChatActs`، `ChatVoice`)؛ این‌جا فقط کشیدن.
 */
@Composable
fun ChatScreen(model: CustomerModel, slug: String, orderNo: Long?) {
  val c = model.chat
  val title = when (val t = c.thread) {
    is Load.Ready -> t.value.conversation.shop.name.takeIf { it.isNotBlank() }?.let { ShopperText.chatTitle(it) }
    else -> null
  } ?: if (orderNo != null) ShopperText.chatOrder else ShopperText.chatShop
  Column(Modifier.fillMaxSize()) {
    TopBar(title, onBack = { model.back() }) {
      if (c.thread is Load.Ready) IconButton(onClick = { c.acts.toggleFind() }) { Icon(Icons.Filled.Search, contentDescription = ShopperText.chatFind) }
    }
    if (c.needVerify) {
      Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Note(ShopperText.chatVerify, Tone.WARN)
        VerifyBlock(model)
      }
      return@Column
    }
    when (val t = c.thread) {
      is Load.Ready -> {
        if (c.acts.finding) FindPanel(c)
        ThreadView(model, t.value, Modifier.weight(1f))
      }
      is Load.Failed -> ErrorBox(t.error, onRetry = { c.retryOpen() })
      else -> BusyBox(Modifier.weight(1f))
    }
  }
}

@Composable
private fun ThreadView(model: CustomerModel, t: ChatThread, modifier: Modifier) {
  val c = model.chat
  val conv = t.conversation
  val queued = c.pending(conv.id)
  val list = rememberLazyListState()
  val composer = remember { FocusRequester() }
  val total = t.messages.size + queued.size
  LaunchedEffect(total) { if (total > 0) list.animateScrollToItem(total - 1) }
  //  پاسخ/سنجاق/جست‌وجو ⇒ رفتن به حبابِ همان پیام
  LaunchedEffect(c.acts.jump) {
    val id = c.acts.jump ?: return@LaunchedEffect
    ChatActs.indexOf(t, id)?.let { list.animateScrollToItem(it) }
    c.acts.jumped()
  }
  //  دستهٔ ۴۲: پس از حذفِ حباب، پاسخ یا ویرایش تمرکز به کادرِ نوشتن (نه گم شدن)
  LaunchedEffect(c.focus) {
    if (c.focus?.messageId == null && c.focus != null) {
      runCatching { composer.requestFocus() }
      c.focused()
    }
  }
  @Suppress("UNUSED_VARIABLE") val rev = c.mediaRev
  PinsBar(c, t)
  LazyColumn(modifier.fillMaxWidth(), state = list, contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    if (total == 0) item { Note(ShopperText.chatEmpty) }
    items(t.messages, key = { it.id }) { m -> Bubble(model, t, m, t.statusOf(m)) }
    items(queued, key = { "q-" + it.clientId }) { q -> QueuedBubble(model, q) }
  }
  c.note?.let { Note(it, Tone.WARN, Modifier.fillMaxWidth().padding(horizontal = 12.dp)) }
  c.deletedNote?.let { Note(it, Tone.OK, Modifier.fillMaxWidth().padding(horizontal = 12.dp).semantics { liveRegion = LiveRegionMode.Polite }) }
  c.acts.note?.let { Note(it, Tone.INFO, Modifier.fillMaxWidth().padding(horizontal = 12.dp).semantics { liveRegion = LiveRegionMode.Polite }) }
  if (c.voice.say.isNotEmpty() && c.voice.rec == null) {
    Text(c.voice.say, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 12.dp).semantics { liveRegion = LiveRegionMode.Polite })
  }
  c.act?.let { a -> t.messages.firstOrNull { it.id == a.messageId }?.let { m -> ActionsDialog(model.chat, a, m) } }
  if (!conv.canSend) {
    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Note(if (conv.life.state == "closed") ShopperText.chatClosed else ShopperText.chatBlocked, Tone.INFO)
      if (conv.life.canIssue) OutlinedButton(onClick = { c.issue() }, modifier = Modifier.fillMaxWidth()) { Text(ShopperText.chatIssue) }
      if (conv.life.canRestart) OutlinedButton(onClick = { c.retryOpen() }, modifier = Modifier.fillMaxWidth()) { Text(ShopperText.chatRestart) }
    }
    return
  }
  ComposeChip(c, t)
  VoiceBar(c)
  val platform = LocalPlatform.current
  val editing = c.compose.editing != null
  Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
    if (platform.canPickPhoto && !editing) {
      IconButton(onClick = { c.photoStart()?.let { forConv -> platform.pickPhoto { c.sendPhoto(it, forConv) } } }, enabled = !c.preparing) {
        Icon(Icons.Filled.AddCircle, contentDescription = ShopperText.chatPhoto)
      }
    }
    if (c.voice.canRecord() && c.voice.rec == null && !editing) {
      IconButton(onClick = { c.voice.start() }) { Icon(MicIcon, contentDescription = ShopperText.vcTitle) }
    }
    OutlinedTextField(
      value = c.draft, onValueChange = { c.draft = it }, modifier = Modifier.weight(1f).focusRequester(composer),
      placeholder = { Text(if (c.preparing) ShopperText.chatPhotoBusy else ShopperText.chatHint) }, maxLines = 4,
    )
    if (editing) {
      TextButton(onClick = { c.submit() }, enabled = c.draft.isNotBlank() && !c.acts.saving, modifier = Modifier.heightIn(min = 48.dp)) { Text(ShopperText.save) }
    } else {
      IconButton(onClick = { c.submit() }, enabled = c.draft.isNotBlank()) {
        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = ShopperText.chatSend)
      }
    }
  }
}

@Composable
private fun Bubble(model: CustomerModel, t: ChatThread, m: ChatMessage, status: String?) {
  val c = model.chat
  val mine = m.mine || m.sender == "customer"
  val colors = MaterialTheme.colorScheme
  val more = remember(m.id) { FocusRequester() }
  //  دستهٔ ۴۲: «حذف برای همه» ⇒ تمرکز به دکمهٔ «کارها» ِ همان سنگِ قبر
  if (c.focus?.messageId == m.id) {
    LaunchedEffect(m.id, m.rev) {
      runCatching { more.requestFocus() }
      c.focused()
    }
  }
  Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
    Column(
      Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(14.dp))
        .background(if (mine) colors.primaryContainer else colors.surfaceVariant).padding(10.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      if (!mine) Text(ShopperText.chatShopSide, style = MaterialTheme.typography.labelSmall)
      m.replyTo?.takeIf { !m.deleted }?.let { QuoteButton(c, t, it) }
      when {
        m.deleted -> Text(ShopperText.chatDeleted, style = MaterialTheme.typography.bodyMedium)
        m.kind == "image" && m.media != null -> {
          val bytes = c.mediaOf(m.media!!.thumbId ?: m.media!!.id)
          Picture(bytes, ShopperText.chatImage)
          if (m.body.isNotBlank()) Text(m.body, style = MaterialTheme.typography.bodyLarge)
        }
        m.kind == "audio" && m.media != null -> {
          VoiceBubble(c, m)
          if (m.body.isNotBlank()) Text(m.body, style = MaterialTheme.typography.bodyLarge)
        }
        else -> Text(m.body, style = MaterialTheme.typography.bodyLarge)
      }
      val meta = listOfNotNull(
        ShopperText.chatEdited.takeIf { m.editedAt != null && !m.deleted },
        //  `ChatThread.statusOf`: status ِ پیام یا رسیدِ تازه‌ترِ دکان (long-poll ِ بی پیام) — زنده، هرگز عقب (CHAT-04)
        when (status) { "read" -> ShopperText.chatRead; "delivered" -> ShopperText.chatDelivered; "sent" -> ShopperText.chatSent; else -> null }.takeIf { mine },
      )
      Row(verticalAlignment = Alignment.CenterVertically) {
        if (meta.isNotEmpty()) Text(meta.joinToString(" · "), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f, fill = false))
        //  IconButton ⇒ هدفِ لمسِ ۴۸dp؛ نام برای صفحه‌خوان
        IconButton(onClick = { c.actions(m) }, enabled = c.act?.busy != true, modifier = Modifier.focusRequester(more)) {
          Icon(Icons.Filled.MoreVert, contentDescription = ShopperText.chatActions)
        }
      }
    }
  }
}

/** فهرستِ کارها، سپس پرسشِ تأیید؛ تا پاسخ همه خاموش (دوبار زدن یک درخواست) و خطا همین‌جا روشن */
@Composable
private fun ActionsDialog(c: ChatModel, a: ChatAct, m: ChatMessage) {
  val big = Modifier.heightIn(min = 48.dp)
  AlertDialog(
    onDismissRequest = { c.closeActions() },
    title = { Text(ShopperText.chatActions) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (a.ask) {
          null -> {
            TextButton(onClick = { c.askDelete(ChatDeleteScope.ME) }, modifier = big.fillMaxWidth()) { Text(ShopperText.chatDeleteMe) }
            if (c.canDeleteForAll(m)) {
              TextButton(onClick = { c.askDelete(ChatDeleteScope.ALL) }, modifier = big.fillMaxWidth()) { Text(ShopperText.chatDeleteAll) }
            }
            //  CHAT-14: همان ترتیبِ `menu` ِ وب — پس از حذف؛ سنگِ قبر هیچ‌کدام
            if (c.acts.canReply(m)) TextButton(onClick = { c.acts.reply(m) }, modifier = big.fillMaxWidth()) { Text(ShopperText.chatReply) }
            if (c.acts.canEdit(m)) TextButton(onClick = { c.acts.edit(m) }, modifier = big.fillMaxWidth()) { Text(ShopperText.chatEdit) }
            if (c.acts.canPin(m)) {
              val on = !c.acts.pinned(m)
              TextButton(onClick = { c.acts.pin(m, on) }, enabled = !c.acts.pinning, modifier = big.fillMaxWidth()) {
                Text(if (on) ShopperText.chatPin else ShopperText.chatUnpin)
              }
            }
          }
          ChatDeleteScope.ALL -> Text(ShopperText.chatDeleteAllAsk)
          ChatDeleteScope.ME -> Text(ShopperText.chatDeleteMeAsk)
        }
        a.note?.let { Note(it, Tone.ERROR, Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) }
      }
    },
    confirmButton = {
      if (a.ask != null) {
        TextButton(onClick = { c.confirmDelete() }, enabled = !a.busy, modifier = big) {
          Text(if (a.busy) ShopperText.chatDeleteBusy else ShopperText.chatDeleteYes, color = MaterialTheme.colorScheme.error)
        }
      }
    },
    dismissButton = {
      TextButton(onClick = { if (a.ask != null) c.askDelete(null) else c.closeActions() }, enabled = !a.busy, modifier = big) {
        Text(if (a.ask != null) ShopperText.no else ShopperText.chatCancel)
      }
    },
  )
}

@Composable
private fun QueuedBubble(model: CustomerModel, q: ChatOutItem) {
  val c = model.chat
  val state = when (q.state) {
    ChatOutbox.SENDING -> ShopperText.chatSending
    ChatOutbox.VERIFY -> ShopperText.chatWaitVerify
    ChatOutbox.FAILED -> ShopperText.chatFailed
    else -> ShopperText.chatQueued
  }
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
    Column(
      Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(10.dp)
        .semantics { contentDescription = state },
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      when (q.kind) {
        "image" -> Picture(c.photoOf(q.clientId), ShopperText.chatImage)
        "audio" -> Text(ShopperText.chatAudio + (q.durationMs?.let { " · " + ShopperText.mmss(it) } ?: ""), style = MaterialTheme.typography.bodyLarge)
        else -> Text(q.body, style = MaterialTheme.typography.bodyLarge)
      }
      Text(state, style = MaterialTheme.typography.labelSmall, color = if (q.state == ChatOutbox.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
      if (q.state == ChatOutbox.FAILED) {
        Row {
          TextButton(onClick = { c.retry(q.clientId) }) { Text(ShopperText.chatRetry) }
          TextButton(onClick = { c.cancel(q.clientId) }) { Text(ShopperText.chatCancel) }
        }
      }
    }
  }
}

@Composable
private fun Picture(bytes: ByteArray?, label: String) {
  val m = Modifier.size(200.dp).clip(RoundedCornerShape(10.dp)).semantics { contentDescription = label }
  if (bytes == null) Box(m.background(MaterialTheme.colorScheme.surface)) else LocalPlatform.current.picture(bytes, m)
}
