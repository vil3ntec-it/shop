package ir.vil3ntec.tohid.ui.v3.online.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.chat.ChatConversation
import ir.vil3ntec.tohid.core.i18n.StrOnline
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Palette
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Card
import ir.vil3ntec.tohid.ui.v3.V3Chip
import ir.vil3ntec.tohid.ui.v3.V3ChipRow
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.V3Hue
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3Scaffold
import ir.vil3ntec.tohid.ui.v3.V3SearchField
import ir.vil3ntec.tohid.ui.v3.V3Skeleton
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.online.DraftTag
import ir.vil3ntec.tohid.ui.v3.online.orders.OrdersModel
import ir.vil3ntec.tohid.ui.v3.rememberV3Stagger
import ir.vil3ntec.tohid.ui.v3.v3AnimateItem
import ir.vil3ntec.tohid.ui.v3.v3Stagger

data class InboxState(
  val list: List<ChatConversation> = emptyList(),
  val loading: Boolean = true,
  /** خطای شبکه (نوارِ «اتصال نیست») */
  val error: String? = null,
  val status: String = "open",
  val query: String = "",
  val now: Long = 0L,
)

/**
 *  صندوقِ گفت‌وگو (`chat`): جست‌وجوی «نامِ مشتری یا شماره…»، تراشه‌های «باز · بسته · همه»، نوارِ
 *  اتصال، و هر گفت‌وگو یک ردیف با آواتارِ حرفِ اول، پیش‌نمایشِ آخرین پیام (عکس/صدا/فایل با نامِ
 *  گونه)، زمان، شمارِ نخوانده (برند) و «مسدود». روی کامپیوترِ پهن، ستونِ اولِ دو ستون.
 */
@Composable
fun ChatInboxScreen(
  state: InboxState,
  onQuery: (String) -> Unit,
  onStatus: (String) -> Unit,
  onOpen: (ChatConversation) -> Unit,
  onBack: (() -> Unit)?,
  modifier: Modifier = Modifier,
  selectedId: String? = null,
) {
  val stagger = rememberV3Stagger(state.status)
  V3Scaffold(
    title = StrOnline.ctTitle,
    subtitle = StrOnline.ctCaption,
    modifier = modifier,
    onBack = onBack,
    search = { V3SearchField(state.query, onQuery, StrOnline.ctSearch) },
    chips = {
      V3ChipRow { ChatModel.STATUSES.forEach { s -> V3Chip(ChatModel.statusLabel(s), state.status == s, { onStatus(s) }) } }
    },
  ) { pad ->
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = pad.calculateBottomPadding())) {
      if (state.error != null) item(key = "offline") { V3Message(V3Tone.WARNING, state.error, Modifier.padding(bottom = 12.dp)) }
      when {
        state.list.isEmpty() && state.loading -> item(key = "skeleton") { V3Skeleton(rows = 5) }
        state.list.isEmpty() && state.query.isNotEmpty() -> item(key = "nomatch") { V3EmptyState(Icons.Outlined.SearchOff, StrOnline.ctNoMatch) }
        state.list.isEmpty() -> item(key = "empty") { V3EmptyState(Icons.Outlined.Forum, StrOnline.ctEmptyTitle, body = StrOnline.ctEmptyBody) }
        else -> itemsIndexed(state.list, key = { _, c -> c.id }) { i, c ->
          V3Card(
            Modifier.padding(bottom = 8.dp).v3AnimateItem(this).v3Stagger(stagger, c.id, i),
            padding = PaddingValues(0.dp),
          ) { ConversationRow(c, state.now, selected = c.id == selectedId) { onOpen(c) } }
        }
      }
    }
  }
}

@Composable
private fun ConversationRow(c: ChatConversation, now: Long, selected: Boolean, onClick: () -> Unit) {
  V3ListRow(
    title = ChatModel.name(c),
    subtitle = ChatModel.preview(c),
    subtitleLines = 1,
    leading = { ChatAvatar(ChatModel.initial(c)) },
    value = OrdersModel.scheduleLabel(ChatModel.whenOf(c), now),
    trailing = {
      Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (c.unread > 0) UnreadBadge(c.unread)
        if (c.blockedCustomer) DraftTag(StrOnline.ctBlocked, tone = V3Tone.ERROR)
        else if (c.customerBlockedShop) DraftTag(StrOnline.ctCustomerBlockedShop, tone = V3Tone.ERROR)
      }
    },
    selected = if (selected) true else null,
    onClick = onClick,
  )
}

/** آواتارِ حرفِ اولِ مشتری روی زمینهٔ نرمِ رنگِ همراه */
@Composable
internal fun ChatAvatar(letter: String, size: androidx.compose.ui.unit.Dp = 44.dp) {
  val c = PhoneV3.colors
  Box(Modifier.size(size).clip(CircleShape).background(PhoneV3Palette.soft(c, V3Hue.CORAL)), contentAlignment = Alignment.Center) {
    Text(letter, style = V3Type.headline, color = PhoneV3Palette.glyph(c, V3Hue.CORAL))
  }
}

/** شمارِ نخوانده — قرصِ برند */
@Composable
private fun UnreadBadge(n: Int) {
  val pair = PhoneV3Roles.button(PhoneV3.colors, V3ButtonKind.PRIMARY)
  Row(
    Modifier.widthIn(min = 22.dp).clip(RoundedCornerShape(50)).background(pair.bg).padding(horizontal = 7.dp, vertical = 2.dp),
    horizontalArrangement = Arrangement.Center,
  ) { Text(n.toString(), style = V3Type.caption.copy(fontWeight = FontWeight.Bold), color = pair.fg) }
}
