package ir.vil3ntec.tohid.ui.v3.online.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.chat.ChatActions
import ir.vil3ntec.tohid.core.chat.ChatActions.Action
import ir.vil3ntec.tohid.core.chat.ChatMessage
import ir.vil3ntec.tohid.core.i18n.OnlineFmt
import ir.vil3ntec.tohid.core.i18n.StrChat
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.StrOnline
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3TextButton
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.v3Pressable

// i18n: سه‌زبانه

/*
 *  CHAT-14 در گفت‌وگوی برنامهٔ مدیریت: برگهٔ «کارهای این پیام» (پاسخ، ویرایش، سنجاق)، نوارِ «پاسخ به / ویرایشِ پیام» بالای
 *  کادرِ نوشتن، نوارِ «سنجاق‌شده» بالای گفت‌وگو و نقلِ پیامِ پاسخ‌داده درونِ حباب. کدام کار برای کدام پیام: `ChatActions`.
 */

/** یک خطِ پیام برای نقل و سنجاق: متن، وگرنه نامِ گونه (عکس، صدا…) یا «پاک‌شده» */
internal fun chatLine(m: ChatMessage?): String = when {
  m == null -> StrChat.earlier
  m.deleted -> StrOnline.ctThisDeleted
  else -> ChatActions.snippet(m) ?: OnlineFmt.ctKind(m.kind)
}

@Composable
fun ChatMessageSheet(m: ChatMessage, actions: List<Action>, onAction: (Action) -> Unit, onDismiss: () -> Unit) {
  V3Sheet(onDismiss = onDismiss, title = StrChat.actionsTitle, detents = false) { closer ->
    Text(chatLine(m), style = V3Type.callout.copy(textDirection = TextDirection.Content), color = PhoneV3.colors.inkMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
    Spacer(Modifier.height(10.dp))
    for (a in actions) {
      val (icon, label) = when (a) {
        Action.REPLY -> Icons.AutoMirrored.Outlined.Reply to StrChat.reply
        Action.EDIT -> Icons.Outlined.Edit to StrChat.edit
        Action.PIN -> Icons.Outlined.PushPin to StrChat.pin
        Action.UNPIN -> Icons.Outlined.PushPin to StrChat.unpin
      }
      V3ListRow(
        title = label,
        subtitle = if (a == Action.PIN) StrChat.pinNote else null,
        icon = icon,
        chevron = false,
        onClick = { closer.close { onAction(a) } },
      )
    }
  }
}

/** نوارِ بالای کادر: «پاسخ به: …» یا «ویرایشِ پیام» با «لغو» */
@Composable
fun ChatReplyBar(replying: ChatMessage?, editing: ChatMessage?, onCancel: () -> Unit) {
  val text = when {
    editing != null -> StrChat.editing
    replying != null -> StrChat.replyingTo(chatLine(replying))
    else -> return
  }
  val c = PhoneV3.colors
  Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
    Icon(if (editing != null) Icons.Outlined.Edit else Icons.AutoMirrored.Outlined.Reply, contentDescription = null, tint = PhoneV3Roles.brandText(c), modifier = Modifier.size(18.dp))
    Spacer(Modifier.width(8.dp))
    Text(text, style = V3Type.footnote, color = c.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    V3TextButton(StrKit.cancel, onCancel)
  }
}

/** نوارِ «سنجاق‌شده» بالای گفت‌وگو — زدن ⇐ همان پیام */
@Composable
fun ChatPinnedStrip(pinned: List<ChatMessage>, onOpen: (ChatMessage) -> Unit) {
  if (pinned.isEmpty()) return
  val c = PhoneV3.colors
  Column(Modifier.fillMaxWidth().background(c.card).padding(horizontal = 12.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Text(StrChat.pinned, style = V3Type.caption, color = c.inkMuted)
    for (m in pinned) {
      val line = chatLine(m)
      Row(
        Modifier.fillMaxWidth().v3Pressable(label = line, onClick = { onOpen(m) }).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(Icons.Outlined.PushPin, contentDescription = null, tint = PhoneV3Roles.brandText(c), modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(line, style = V3Type.footnote.copy(textDirection = TextDirection.Content), color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
    }
  }
}

/** نقلِ پیامی که این پیام پاسخِ آن است — درونِ حباب، بالای متن */
@Composable
internal fun ChatQuote(text: String) {
  val c = PhoneV3.colors
  Row(Modifier.clip(RoundedCornerShape(8.dp)).background(c.tile).padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.width(3.dp).height(32.dp).background(PhoneV3Roles.brandText(c)))
    Spacer(Modifier.width(8.dp))
    Text(text, style = V3Type.footnote.copy(textDirection = TextDirection.Content), color = c.inkMuted, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(vertical = 4.dp))
  }
}
