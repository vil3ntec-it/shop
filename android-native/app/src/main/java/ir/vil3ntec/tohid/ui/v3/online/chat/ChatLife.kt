package ir.vil3ntec.tohid.ui.v3.online.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Icon
import ir.vil3ntec.tohid.core.chat.ChatConversation
import ir.vil3ntec.tohid.core.chat.ChatLife
import ir.vil3ntec.tohid.core.i18n.StrOrdersHub
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3Hue
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3Section
import ir.vil3ntec.tohid.ui.v3.online.orders.OrdersModel

/**
 *  چرخهٔ عمرِ رشتهٔ گفت‌وگو در برنامهٔ دکان (یک رشته برای هر سفارش؛ سرور: `lib/shop-chat-life.js`): پیامِ «بسته شد» به‌جای
 *  نوارِ نوشتن (هر دو طرف فقط می‌خوانند)، «مشتری «مشکل دارم» زد» بالای نوار، و رشته‌های پیشینِ همین مشتری جمع‌شده
 *  بالای رشتهٔ کنونی تا زمینه گم نشود. منطقِ متن خالص است (`ChatLifeModelTest`).
 */
object ChatLifeModel {

  /** پیامِ وضعِ رشته؛ باز ⇒ null. `issueUntil` را سرور فقط تا بازیِ مهلت می‌دهد (پس از آن null ⇒ «مهلت گذشت») */
  fun banner(life: ChatLife, stamp: (Long) -> String = OrdersModel::stamp): Pair<String, V3Tone>? = when {
    life.closed && life.kind == "general" -> StrOrdersHub.closedIdle to V3Tone.INFO
    life.closed -> StrOrdersHub.closedOrder(life.issueUntil?.let(stamp)) to V3Tone.WARNING
    life.reopened -> StrOrdersHub.reopenedNote to V3Tone.WARNING
    else -> null
  }

  /** نامِ رشته در فهرستِ پیشین: «سفارشِ #۱۲» یا «گفت‌وگوی عمومی» */
  fun title(c: ChatConversation): String = c.orderNo?.let(StrOrdersHub::orderThread) ?: StrOrdersHub.generalThread

  /** برچسبِ وضع: «بسته شد» / «مشتری «مشکل دارم» زد» / هیچ */
  fun state(c: ChatConversation): String? = when {
    c.life.closed -> StrOrdersHub.lifeClosed
    c.life.reopened -> StrOrdersHub.lifeReopened
    else -> null
  }

  /** خطِ زیرِ نام: وضع و پیش‌نمایشِ آخرین پیام */
  fun subtitle(c: ChatConversation): String =
    listOfNotNull(state(c), c.lastMessage?.preview?.takeIf { it.isNotBlank() }).joinToString(" · ").ifBlank { StrOrdersHub.earlierEmpty }
}

/** پیامِ وضعِ رشته («بسته شد» / «مشکل دارم») — جای نوارِ نوشتن یا بالای آن */
@Composable
internal fun ChatLifeBanner(life: ChatLife, modifier: Modifier = Modifier) {
  val (text, tone) = ChatLifeModel.banner(life) ?: return
  V3Message(tone, text, modifier)
}

/** «گفت‌وگوهای پیشینِ این مشتری (n)» — جمع‌شده؛ زدن بازش می‌کند و هر رشته همان رشته را (فقط خواندنی اگر بسته است) */
@Composable
internal fun ChatEarlierThreads(list: List<ChatConversation>, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
  if (list.isEmpty()) return
  var open by rememberSaveable { mutableStateOf(false) }
  V3Section(null, modifier) {
    V3ListRow(
      StrOrdersHub.earlier(list.size),
      icon = Icons.Outlined.History,
      hue = V3Hue.PURPLE,
      chevron = false,
      trailing = { Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null) },
      onClick = { open = !open },
    )
    AnimatedVisibility(open) {
      Column(Modifier.fillMaxWidth()) {
        list.forEach { c ->
          V3Divider(start = 64.dp)
          V3ListRow(
            ChatLifeModel.title(c),
            subtitle = ChatLifeModel.subtitle(c),
            icon = if (c.life.closed) Icons.Outlined.Lock else if (c.life.reopened) Icons.Outlined.ReportProblem else Icons.Outlined.History,
            hue = if (c.life.reopened) V3Hue.ORANGE else V3Hue.BLUE,
            onClick = { onOpen(c.id) },
          )
        }
      }
    }
  }
}
