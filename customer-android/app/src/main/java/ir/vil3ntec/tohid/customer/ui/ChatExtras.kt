package ir.vil3ntec.tohid.customer.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.chat.VoiceFlow
import ir.vil3ntec.tohid.core.shopper.ChatActs
import ir.vil3ntec.tohid.core.shopper.ChatMessage
import ir.vil3ntec.tohid.core.shopper.ChatPlayState
import ir.vil3ntec.tohid.core.shopper.ChatThread
import ir.vil3ntec.tohid.core.shopper.ChatVoice
import ir.vil3ntec.tohid.core.shopper.ChatVoiceSay
import ir.vil3ntec.tohid.core.shopper.ShopperText
import kotlinx.coroutines.delay

/*
 *  تکه‌های صفحهٔ گفت‌وگو برای CHAT-14 (سنجاق، جست‌وجو، برچسبِ پاسخ/ویرایش، نقلِ پیامِ اصل) و CHAT-07 (نوارِ ضبط، پخش‌کنندهٔ
 *  حبابِ صدا) — فقط کشیدن؛ قاعده‌ها در `ChatActs`/`ChatVoice` ِ core و ترتیبِ کارها در `ChatActsModel`/`ChatVoiceModel`.
 *  هر دکمه هدفِ لمسِ ≥ ۴۸dp و نام برای صفحه‌خوان دارد؛ پیام‌های روشن ناحیهٔ زندهٔ مؤدب‌اند.
 */

private val Live = Modifier.semantics { liveRegion = LiveRegionMode.Polite }

/** نوارِ «سنجاق‌شده» بالای پیام‌ها — فقط از `conversation.pinned` ِ سرور؛ زدن ⇒ رفتن به همان حباب */
@Composable
fun PinsBar(c: ChatModel, t: ChatThread) {
  val pins = ChatActs.pins(t)
  if (pins.isEmpty()) return
  Row(
    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(ShopperText.chatPins, style = MaterialTheme.typography.labelMedium)
    for ((id, snip) in pins) {
      OutlinedButton(onClick = { c.acts.go(id) }, modifier = Modifier.heightIn(min = 48.dp).widthIn(max = 220.dp)) { Text(snip, maxLines = 1) }
    }
  }
}

/** جست‌وجو در پیام‌های همین دکان و همین شماره؛ نتیجهٔ همین رشته ⇒ رفتن به حباب، رشتهٔ دیگر فقط خواندنی */
@Composable
fun FindPanel(c: ChatModel) {
  val a = c.acts
  Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      OutlinedTextField(
        value = a.query, onValueChange = { a.query = it.take(ChatActs.QUERY_MAX) }, modifier = Modifier.weight(1f),
        placeholder = { Text(ShopperText.chatFind) }, singleLine = true,
      )
      Button(onClick = { a.find() }, modifier = Modifier.heightIn(min = 48.dp)) { Text(ShopperText.chatFindGo) }
    }
    if (a.findNote.isNotEmpty()) Text(a.findNote, style = MaterialTheme.typography.bodySmall, modifier = Live)
    a.hits?.forEach { h ->
      val line = a.hitLine(h)
      if (a.here(h)) TextButton(onClick = { a.open(h) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(line, maxLines = 2) }
      else Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
    }
  }
}

/** برچسبِ «پاسخ به …» یا «ویرایشِ پیامِ شما» بالای کادر، با «انصراف» */
@Composable
fun ComposeChip(c: ChatModel, t: ChatThread) {
  val k = c.compose
  val label = when {
    k.editing != null -> ShopperText.chatEditing
    k.replyTo != null -> ShopperText.chatReplyTo(ChatActs.snip(t.messages.firstOrNull { it.id == k.replyTo }))
    else -> return
  }
  Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
    Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 2, modifier = Modifier.weight(1f).then(Live))
    TextButton(onClick = { c.acts.stop() }, modifier = Modifier.heightIn(min = 48.dp)) { Text(ShopperText.chatStop) }
  }
}

/** نقلِ کوتاهِ پیامِ اصل بالای پاسخ؛ زدن ⇒ همان پیام */
@Composable
fun QuoteButton(c: ChatModel, t: ChatThread, replyTo: String) {
  TextButton(onClick = { c.acts.go(replyTo) }, modifier = Modifier.heightIn(min = 48.dp)) {
    Text(ChatActs.snip(t.messages.firstOrNull { it.id == replyTo }), style = MaterialTheme.typography.bodySmall, maxLines = 2)
  }
}

/** نوارِ ضبط: «پایان و فرستادن»/«لغو» در ضبط؛ پیش‌شنوایی، «فرستادن» (نه برای ضبطِ بزرگ‌تر از سقف) و «لغو» پس از ایستادن */
@Composable
fun VoiceBar(c: ChatModel) {
  val v = c.voice
  val r = v.rec ?: return
  val recording = r.phase == VoiceFlow.Phase.TAP
  if (recording) {
    LaunchedEffect(r.startedAt) {
      while (true) {
        delay(ChatVoice.TICK_MS)
        v.tick()
      }
    }
  }
  Surface(Modifier.fillMaxWidth().padding(horizontal = 8.dp).semantics { contentDescription = ShopperText.vcTitle }, tonalElevation = 2.dp) {
    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      if (recording || r.phase == VoiceFlow.Phase.READY) {
        val time = ShopperText.mmss(v.elapsed) + if (recording) " / " + ShopperText.mmss(ChatVoice.MAX_MS) else ""
        Text(time, style = MaterialTheme.typography.titleMedium)
        if (recording) {
          LinearProgressIndicator(progress = { (v.elapsed.toFloat() / ChatVoice.MAX_MS).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = ShopperText.vcLen })
        }
      }
      if (r.say != ChatVoiceSay.ASK) Text(ChatVoice.text(r.say), style = MaterialTheme.typography.bodyMedium)
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (recording) Button(onClick = { v.finish() }, modifier = Modifier.heightIn(min = 48.dp)) { Text(ShopperText.vcFinish) }
        if (r.phase == VoiceFlow.Phase.READY) {
          IconButton(onClick = { v.preview() }) {
            Icon(if (v.previewing) PauseIcon else Icons.Filled.PlayArrow, contentDescription = ShopperText.vcPrev)
          }
          if (r.say != ChatVoiceSay.BIG) Button(onClick = { v.send() }, modifier = Modifier.heightIn(min = 48.dp)) { Text(ShopperText.chatSend) }
        }
        TextButton(onClick = { v.cancel() }, modifier = Modifier.heightIn(min = 48.dp)) { Text(ShopperText.chatCancel) }
      }
    }
  }
}

/** حبابِ پیامِ صوتی: پخش/توقف، زمان (از `meta.durationMs` و سپس خودِ صدا) و نوارِ پیشرفت؛ هیچ بایتی پیش از «پخش» */
@Composable
fun VoiceBubble(c: ChatModel, m: ChatMessage) {
  val v = c.voice
  val p = v.play?.takeIf { it.messageId == m.id }
  var at by remember(m.id) { mutableLongStateOf(0L) }
  val playing = p?.state == ChatPlayState.PLAYING
  if (playing) {
    LaunchedEffect(m.id) {
      while (true) {
        at = v.position(m.id)
        delay(ChatVoice.TICK_MS)
      }
    }
  }
  val len = v.length(m)
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      val blocked = p?.state == ChatPlayState.GONE || p?.state == ChatPlayState.CODEC
      val label = when (p?.state) {
        ChatPlayState.PLAYING -> ShopperText.auPause
        ChatPlayState.RETRY -> ShopperText.auRetry
        else -> ShopperText.auPlay
      }
      IconButton(onClick = { v.toggle(m) }, enabled = !blocked && p?.state != ChatPlayState.LOADING) {
        Icon(if (playing) PauseIcon else Icons.Filled.PlayArrow, contentDescription = label)
      }
      Column(Modifier.widthIn(min = 120.dp, max = 200.dp)) {
        Text(ShopperText.chatAudio + " · " + (if (p != null && len > 0) ShopperText.mmss(at) + " / " else "") + ShopperText.mmss(len),
          style = MaterialTheme.typography.labelMedium)
        LinearProgressIndicator(progress = { if (len > 0) (at.toFloat() / len).coerceIn(0f, 1f) else 0f }, modifier = Modifier.fillMaxWidth())
      }
    }
    when (p?.state) {
      ChatPlayState.GONE -> Text(ShopperText.auGone, style = MaterialTheme.typography.bodySmall, modifier = Live)
      ChatPlayState.CODEC -> Text(ShopperText.auCodec, style = MaterialTheme.typography.bodySmall, modifier = Live)
      ChatPlayState.RETRY -> Text(p?.note.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Live)
      else -> Unit
    }
  }
}

/** میکروفون (Material «mic») — بسته‌ی آیکنِ پایه آن را ندارد و وابستگیِ تازه نمی‌آید */
val MicIcon: ImageVector by lazy {
  ImageVector.Builder("Mic", 24.dp, 24.dp, 24f, 24f).apply {
    path(fill = SolidColor(Color.Black)) {
      moveTo(12f, 14f)
      curveToRelative(1.66f, 0f, 2.99f, -1.34f, 2.99f, -3f)
      lineTo(15f, 5f)
      curveToRelative(0f, -1.66f, -1.34f, -3f, -3f, -3f)
      reflectiveCurveTo(9f, 3.34f, 9f, 5f)
      verticalLineToRelative(6f)
      curveToRelative(0f, 1.66f, 1.34f, 3f, 3f, 3f)
      close()
      moveTo(17.3f, 11f)
      curveToRelative(0f, 3f, -2.54f, 5.1f, -5.3f, 5.1f)
      reflectiveCurveTo(6.7f, 14f, 6.7f, 11f)
      lineTo(5f, 11f)
      curveToRelative(0f, 3.41f, 2.72f, 6.23f, 6f, 6.72f)
      lineTo(11f, 21f)
      horizontalLineToRelative(2f)
      verticalLineToRelative(-3.28f)
      curveToRelative(3.28f, -0.48f, 6f, -3.3f, 6f, -6.72f)
      horizontalLineToRelative(-1.7f)
      close()
    }
  }.build()
}

/** توقف (Material «pause») */
val PauseIcon: ImageVector by lazy {
  ImageVector.Builder("Pause", 24.dp, 24.dp, 24f, 24f).apply {
    path(fill = SolidColor(Color.Black)) {
      moveTo(6f, 19f)
      horizontalLineToRelative(4f)
      lineTo(10f, 5f)
      lineTo(6f, 5f)
      verticalLineToRelative(14f)
      close()
      moveTo(14f, 5f)
      verticalLineToRelative(14f)
      horizontalLineToRelative(4f)
      lineTo(18f, 5f)
      horizontalLineToRelative(-4f)
      close()
    }
  }.build()
}
