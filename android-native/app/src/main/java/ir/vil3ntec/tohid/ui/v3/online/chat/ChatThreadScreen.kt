package ir.vil3ntec.tohid.ui.v3.online.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.chat.ChatConversation
import ir.vil3ntec.tohid.core.chat.ChatMessage
import ir.vil3ntec.tohid.core.chat.OutboxItem
import ir.vil3ntec.tohid.core.i18n.StrOnline
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Chip
import ir.vil3ntec.tohid.ui.v3.V3ChipRow
import ir.vil3ntec.tohid.ui.v3.V3MenuItem
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3Scaffold
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.v3AnimateItem

/** آنچه یک گفت‌وگو نشان می‌دهد (از `ChatThreadController` یا دادهٔ نمایشی) */
class ThreadView(
  val conversation: ChatConversation?,
  val server: List<ChatMessage>,
  val local: List<OutboxItem>,
  val banner: Pair<String, Boolean>?,
  val canReply: Boolean,
  val canModerate: Boolean,
  val imageOf: (ChatMessage) -> ImageBitmap?,
  val localImageOf: (OutboxItem) -> ImageBitmap?,
)

class ThreadActions(
  val onOrder: ((String) -> Unit)?,
  val onToggleStatus: () -> Unit,
  val onToggleBlock: () -> Unit,
  val onReport: (messageId: String?) -> Unit,
  val onImage: (ChatMessage) -> Unit,
  val onAudio: (ChatMessage) -> Unit,
  val onRetry: (OutboxItem) -> Unit,
  val onCancel: (OutboxItem) -> Unit,
  val loadThumb: suspend (ChatMessage) -> Unit,
  val loadLocal: suspend (OutboxItem) -> Unit,
)

/**
 *  یک گفت‌وگو (`chat/{id}`): سربرگ با نامِ مشتری و شماره، تراشهٔ «سفارشِ ۱۰۴۲» (⇐ همان سفارش)،
 *  «…» برای بستن/باز کردن، گزارش و مسدودی (فقط با همان دسترسی)، حباب‌های متن/عکس/صدا/فایل با رسید،
 *  حبابِ صفِ همین دستگاه با ساعت، و نوارِ نوشتن. بی اجازهٔ پاسخ ⇐ «فقط خواندن».
 */
@Composable
fun ChatThreadScreen(
  view: ThreadView,
  actions: ThreadActions,
  onBack: (() -> Unit)?,
  composer: @Composable () -> Unit,
  modifier: Modifier = Modifier,
) {
  val c = PhoneV3.colors
  val conv = view.conversation
  val list = rememberLazyListState()
  val total = view.server.size + view.local.size
  LaunchedEffect(total) { if (total > 0) list.animateScrollToItem((list.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)) }
  V3Scaffold(
    title = ChatModel.name(conv),
    subtitle = ChatModel.subtitle(conv).ifBlank { null },
    modifier = modifier,
    onBack = onBack,
    largeTitle = false,
    overflow = buildList {
      if (view.canReply && conv != null) add(
        V3MenuItem(if (conv.status == "open") Icons.Outlined.Lock else Icons.Outlined.LockOpen, if (conv.status == "open") StrOnline.ctCloseConv else StrOnline.ctReopen, onClick = actions.onToggleStatus),
      )
      add(V3MenuItem(Icons.Outlined.Flag, StrOnline.ctReportConv) { actions.onReport(null) })
      if (view.canModerate && conv != null) add(
        V3MenuItem(Icons.Outlined.Block, if (conv.blockedCustomer) StrOnline.ctUnblock else StrOnline.ctBlock, danger = !conv.blockedCustomer, onClick = actions.onToggleBlock),
      )
    },
    chips = conv?.orderId?.let { id ->
      val no = conv.orderNo
      if (no == null || actions.onOrder == null) null
      else ({ V3ChipRow { V3Chip(StrOnline.ctOrderChip(no), false, { actions.onOrder.invoke(id) }, icon = Icons.Outlined.ReceiptLong) } })
    },
    bottomBar = {
      Column(Modifier.fillMaxWidth().background(c.bg)) {
        when {
          !view.canReply -> Text(StrOnline.ctReadOnly, style = V3Type.footnote, color = c.inkMuted, modifier = Modifier.fillMaxWidth().padding(16.dp))
          else -> {
            if (conv?.customerBlockedShop == true) V3Message(V3Tone.ERROR, StrOnline.ctCustomerBlockedNote, Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
            composer()
          }
        }
      }
    },
  ) { pad ->
    Box(Modifier.fillMaxSize()) {
      LazyColumn(
        state = list,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = pad.calculateBottomPadding() + 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        view.banner?.let { (text, bad) -> item(key = "banner") { V3Message(if (bad) V3Tone.ERROR else V3Tone.WARNING, text) } }
        if (total == 0) item(key = "empty") {
          Text(StrOnline.ctNoMessageYet, style = V3Type.callout, color = c.inkMuted, modifier = Modifier.fillMaxWidth().padding(24.dp))
        }
        items(view.server, key = { "s-" + it.id }) { m ->
          if (m.kind == "image") LaunchedEffect(m.media?.thumbId ?: m.media?.id) { actions.loadThumb(m) }
          Box(Modifier.v3AnimateItem(this)) {
            ServerBubble(
              m = m,
              receipts = conv?.receipts,
              image = view.imageOf(m),
              onImage = { actions.onImage(m) },
              onAudio = { actions.onAudio(m) },
              onReport = { actions.onReport(m.id) },
            )
          }
        }
        items(view.local, key = { "q-" + it.clientId }) { x ->
          if (x.kind == "image") LaunchedEffect(x.thumbKey ?: x.blobKey) { actions.loadLocal(x) }
          Box(Modifier.v3AnimateItem(this)) {
            LocalBubble(x, view.localImageOf(x), onRetry = { actions.onRetry(x) }, onCancel = { actions.onCancel(x) })
          }
        }
        item(key = "end") { Box(Modifier.height(4.dp)) }
      }
    }
  }
}
