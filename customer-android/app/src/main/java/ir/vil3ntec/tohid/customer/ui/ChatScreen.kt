package ir.vil3ntec.tohid.customer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.shopper.ChatMessage
import ir.vil3ntec.tohid.core.shopper.ChatOutItem
import ir.vil3ntec.tohid.core.shopper.ChatOutbox
import ir.vil3ntec.tohid.core.shopper.ChatThread
import ir.vil3ntec.tohid.core.shopper.ShopperText

/**
 *  گفت‌وگو با دکان (CHAT-02/06): رشتهٔ پیام‌ها از سرور (long-poll)، پیام‌های صف زیرِ رشته با حالِ روشن («در صف»،
 *  «در حالِ فرستادن»، «منتظرِ تأیید»، «نرفت» با «دوباره/لغو»)، رسیدِ پیامِ خودم، و فرستادنِ عکس (چرخیده و بی EXIF).
 *  «بسته/مشکل دارم/گفت‌وگوی تازه» فقط از `life` ِ سرور.
 */
@Composable
fun ChatScreen(model: CustomerModel, slug: String, orderNo: Long?) {
  val c = model.chat
  val title = when (val t = c.thread) {
    is Load.Ready -> t.value.conversation.shop.name.takeIf { it.isNotBlank() }?.let { ShopperText.chatTitle(it) }
    else -> null
  } ?: if (orderNo != null) ShopperText.chatOrder else ShopperText.chatShop
  Column(Modifier.fillMaxSize()) {
    TopBar(title, onBack = { model.back() })
    if (c.needVerify) {
      Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Note(ShopperText.chatVerify, Tone.WARN)
        VerifyBlock(model)
      }
      return@Column
    }
    when (val t = c.thread) {
      is Load.Ready -> ThreadView(model, t.value, Modifier.weight(1f))
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
  val total = t.messages.size + queued.size
  LaunchedEffect(total) { if (total > 0) list.animateScrollToItem(total - 1) }
  @Suppress("UNUSED_VARIABLE") val rev = c.mediaRev
  LazyColumn(modifier.fillMaxWidth(), state = list, contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    if (total == 0) item { Note(ShopperText.chatEmpty) }
    items(t.messages, key = { it.id }) { m -> Bubble(model, m) }
    items(queued, key = { "q-" + it.clientId }) { q -> QueuedBubble(model, q) }
  }
  c.note?.let { Note(it, Tone.WARN, Modifier.fillMaxWidth().padding(horizontal = 12.dp)) }
  if (!conv.canSend) {
    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Note(if (conv.life.state == "closed") ShopperText.chatClosed else ShopperText.chatBlocked, Tone.INFO)
      if (conv.life.canIssue) OutlinedButton(onClick = { c.issue() }, modifier = Modifier.fillMaxWidth()) { Text(ShopperText.chatIssue) }
      if (conv.life.canRestart) OutlinedButton(onClick = { c.retryOpen() }, modifier = Modifier.fillMaxWidth()) { Text(ShopperText.chatRestart) }
    }
    return
  }
  val platform = LocalPlatform.current
  Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
    if (platform.canPickPhoto) {
      IconButton(onClick = { c.preparing = true; platform.pickPhoto { c.sendPhoto(it) } }, enabled = !c.preparing) {
        Icon(Icons.Filled.AddCircle, contentDescription = ShopperText.chatPhoto)
      }
    }
    OutlinedTextField(
      value = c.draft, onValueChange = { c.draft = it.take(4000) }, modifier = Modifier.weight(1f),
      placeholder = { Text(if (c.preparing) ShopperText.chatPhotoBusy else ShopperText.chatHint) }, maxLines = 4,
    )
    IconButton(onClick = { c.send() }, enabled = c.draft.isNotBlank()) {
      Icon(Icons.AutoMirrored.Filled.Send, contentDescription = ShopperText.chatSend)
    }
  }
}

@Composable
private fun Bubble(model: CustomerModel, m: ChatMessage) {
  val mine = m.mine || m.sender == "customer"
  val colors = MaterialTheme.colorScheme
  Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
    Column(
      Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(14.dp))
        .background(if (mine) colors.primaryContainer else colors.surfaceVariant).padding(10.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      if (!mine) Text(ShopperText.chatShopSide, style = MaterialTheme.typography.labelSmall)
      when {
        m.deleted -> Text(ShopperText.chatDeleted, style = MaterialTheme.typography.bodyMedium)
        m.kind == "image" && m.media != null -> {
          val bytes = model.chat.mediaOf(m.media!!.thumbId ?: m.media!!.id)
          Picture(bytes, ShopperText.chatImage)
          if (m.body.isNotBlank()) Text(m.body, style = MaterialTheme.typography.bodyLarge)
        }
        else -> Text(m.body, style = MaterialTheme.typography.bodyLarge)
      }
      val meta = listOfNotNull(
        ShopperText.chatEdited.takeIf { m.editedAt != null && !m.deleted },
        when (m.status) { "read" -> ShopperText.chatRead; "delivered" -> ShopperText.chatDelivered; "sent" -> ShopperText.chatSent; else -> null }.takeIf { mine },
      )
      if (meta.isNotEmpty()) Text(meta.joinToString(" · "), style = MaterialTheme.typography.labelSmall)
    }
  }
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
      if (q.kind == "image") Picture(c.photoOf(q.clientId), ShopperText.chatImage) else Text(q.body, style = MaterialTheme.typography.bodyLarge)
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
