package ir.vil3ntec.tohid.ui.v3.online.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.chat.ChatMessage
import ir.vil3ntec.tohid.core.chat.ChatQueue
import ir.vil3ntec.tohid.core.chat.ChatReceipts
import ir.vil3ntec.tohid.core.chat.OutboxItem
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.StrOnline
import ir.vil3ntec.tohid.data.ChatMedia
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Palette
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.v3.V3TextButton
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.online.orders.OrdersModel
import ir.vil3ntec.tohid.ui.v3.v3Pressable

/** حبابِ پیامی که از سرور آمده: متن، عکس (زدن ⇐ کامل)، صدا (پخش/ذخیره)، رسید و «گزارش» برای پیامِ مشتری */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ServerBubble(
  m: ChatMessage,
  receipts: ChatReceipts?,
  image: ImageBitmap?,
  onImage: () -> Unit,
  onAudio: () -> Unit,
  onReport: () -> Unit,
) {
  val c = PhoneV3.colors
  val mine = m.sender == "shop"
  Bubble(mine) {
    if (m.deleted) Text(StrOnline.ctThisDeleted, style = V3Type.footnote, color = c.inkMuted)
    else {
      when (m.kind) {
        "image" -> Photo(image, StrOnline.ctTapFull, onImage)
        "audio" -> MediaChip(Icons.Outlined.GraphicEq, (if (ChatMedia.voiceSupported) StrOnline.ctPlayVoice else StrOnline.ctSaveVoice) + (m.meta.durationMs?.let { " · " + ChatModel.mmss(it) }.orEmpty()), onAudio)
        "file" -> MediaChip(Icons.Outlined.Description, StrOnline.ctPdf, null)
      }
      if (m.body.isNotBlank()) Text(m.body, style = V3Type.body, color = c.ink)
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.Center) {
      Text(OrdersModel.stamp(m.createdAt), style = V3Type.caption, color = c.inkMuted)
      if (m.editedAt != null) Text(StrOnline.ctEdited, style = V3Type.caption, color = c.inkMuted)
      if (mine && !m.deleted) {
        val read = ChatModel.isRead(m, receipts)
        Row(verticalAlignment = Alignment.CenterVertically) {
          if (read) Icon(Icons.Outlined.DoneAll, contentDescription = null, tint = PhoneV3Roles.brandText(c), modifier = Modifier.size(14.dp))
          Text(ChatModel.receipt(m, receipts), style = V3Type.caption, color = if (read) PhoneV3Roles.brandText(c) else c.inkMuted)
        }
      }
      if (!mine && !m.deleted) Text(
        StrOnline.ctReport, style = V3Type.caption, color = PhoneV3Roles.brandText(c),
        modifier = Modifier.v3Pressable(label = StrOnline.ctReport, onClick = onReport).padding(horizontal = 4.dp, vertical = 2.dp),
      )
    }
  }
}

/** حبابِ پیامِ صفِ همین دستگاه: آیکنِ ساعت تا برود، «دوباره» و «لغو» */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LocalBubble(x: OutboxItem, image: ImageBitmap?, onRetry: () -> Unit, onCancel: () -> Unit) {
  val c = PhoneV3.colors
  Bubble(mine = true) {
    when (x.kind) {
      "image" -> Photo(image, StrOnline.ctQueuedImage, null)
      "audio" -> MediaChip(Icons.Outlined.GraphicEq, StrOnline.ctKind("audio") + (x.meta.durationMs?.let { " · " + ChatModel.mmss(it) }.orEmpty()), null)
    }
    if (x.body.isNotBlank()) Text(x.body, style = V3Type.body, color = c.ink)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.Center) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Schedule, contentDescription = null, tint = c.inkMuted, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(4.dp))
        Text(OrdersModel.stamp(x.createdAt), style = V3Type.caption, color = c.inkMuted)
      }
      Text(
        ChatModel.queuedLabel(x.state, x.error), style = V3Type.caption,
        color = if (x.state == ChatQueue.FAILED || x.state == ChatQueue.AUTH) PhoneV3Roles.errorText(c) else c.inkMuted,
      )
      if (ChatModel.canRetry(x.state)) V3TextButton(StrKit.retry, onRetry)
      if (ChatModel.canCancel(x.state)) V3TextButton(StrKit.cancel, onCancel, danger = true)
    }
  }
}

/** حباب: مالِ دکان در پایان با زمینهٔ برندِ نرم، مالِ مشتری در آغاز روی کارت با مرز */
@Composable
private fun Bubble(mine: Boolean, content: @Composable () -> Unit) {
  val c = PhoneV3.colors
  val shape = if (mine) RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 6.dp)
  else RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 6.dp, bottomEnd = 18.dp)
  Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
    Column(
      Modifier
        .widthIn(max = 320.dp)
        .clip(shape)
        .background(if (mine) PhoneV3Palette.selected(c) else c.card)
        .border(1.dp, if (mine) PhoneV3Palette.selected(c) else c.hairline, shape)
        .padding(horizontal = 12.dp, vertical = 9.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) { content() }
  }
}

@Composable
private fun Photo(image: ImageBitmap?, label: String, onClick: (() -> Unit)?) {
  val c = PhoneV3.colors
  Box(
    Modifier
      .widthIn(max = 240.dp)
      .heightIn(min = 96.dp, max = 320.dp)
      .clip(RoundedCornerShape(12.dp))
      .background(c.tile)
      .then(if (onClick != null) Modifier.v3Pressable(label = label, onClick = onClick) else Modifier)
      .semantics { contentDescription = label },
  ) { image?.let { Image(it, contentDescription = null, contentScale = ContentScale.Fit) } }
}

@Composable
private fun MediaChip(icon: ImageVector, label: String, onClick: (() -> Unit)?) {
  val c = PhoneV3.colors
  Row(
    Modifier
      .clip(RoundedCornerShape(12.dp))
      .background(c.tile)
      .then(if (onClick != null) Modifier.v3Pressable(label = label, onClick = onClick) else Modifier)
      .heightIn(min = 44.dp)
      .padding(horizontal = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(icon, contentDescription = null, tint = PhoneV3Roles.brandText(c), modifier = Modifier.size(20.dp))
    Spacer(Modifier.width(8.dp))
    Text(label, style = V3Type.callout, color = c.ink)
  }
}
