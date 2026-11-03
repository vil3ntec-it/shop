package ir.vil3ntec.tohid.ui.v3.online.orders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.TwoWheeler
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrOnline
import ir.vil3ntec.tohid.core.i18n.StrOrdersHub
import ir.vil3ntec.tohid.core.model.OrderDto
import ir.vil3ntec.tohid.core.model.OrderPermsDto
import ir.vil3ntec.tohid.core.model.OrdersConfigDto
import ir.vil3ntec.tohid.data.ShopChatSync
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.ui.screens.DeliveryGate
import ir.vil3ntec.tohid.ui.screens.Haptics
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3Section
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.online.OnlinePaths
import ir.vil3ntec.tohid.ui.v3.online.chat.ChatHub
import kotlinx.coroutines.launch

/*
 *  پروندهٔ سکو (`*Android.kt`، قاعدهٔ «وابستگیِ پلتفرم فقط در *Android.kt»): دکمه‌ها `Context` می‌خواهند (چت، گذار، لرزش)؛
 *  منطقِ خالص در `OrdersHubModel.prep` است و آیفون همین پرونده را با شیم‌های همان API می‌سازد.
 */

/**
 *  «آماده‌سازی و تحویل» ِ یک سفارشِ آنلاین (خواستِ صاحب‌کار): روشِ تحویلِ مشتری به زبانِ ساده، «عکس برای مشتری» و
 *  «گفت‌وگو با مشتری» (رشتهٔ همین سفارش — عکس از همان راهِ امنِ بارگذاریِ چت با فشرده‌سازی و سقفِ حجم)، «بسته شد»
 *  (از «قبول‌شده» هم: جمع‌آوری ⇐ بسته) و پس از بسته‌بندی «سپردن به پیک» (میزِ تحویل اگر روشن است) یا «آماده؛ خودش
 *  می‌آید». هر گذار همان `OrdersHub.transition` ِ نوارِ پایین است و سرور دوباره می‌سنجد (`OrdersHubModel.prep`).
 */
@Composable
internal fun RouteScope.OrderPrepCardV3(o: OrderDto, cfg: OrdersConfigDto?) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val chat = ShopChatSync.config.value
  val chatOn = chat?.enabled == true
  val perms = cfg?.permissions ?: OrderPermsDto()
  val prep = OrdersHubModel.prep(o, perms, chatOn && chat?.permissions?.reply == true)
  if (prep.isEmpty) return
  var busy by remember(o.id) { mutableStateOf<String?>(null) }
  var error by remember(o.id) { mutableStateOf<String?>(null) }

  fun openChat(photo: Boolean) {
    if (busy != null) return
    busy = if (photo) "photo" else "chat"
    error = null
    scope.launch {
      Backend.shopChat(context).open(o.id)
        .onSuccess { c -> ChatHub.known[c.id] = c; nav.open(OnlinePaths.conversation(c.id, photo)) }
        .onFailure { error = if (it.code == "no_customer_phone") StrOrdersHub.noPhone else it.userMessage }
      busy = null
    }
  }

  fun steps(list: List<String>) {
    if (busy != null) return
    busy = list.last()
    error = null
    scope.launch {
      var cur = o
      for (to in list) {
        val r = OrdersHub.transition(context, store, cur, to, say = { toasts.show(it) })
        val next = r.valueOrNull()
        if (next == null) { r.onFailure { f -> error = if (f.code == "status_changed") StrOnline.staleStatus else f.userMessage }; break }
        cur = next
      }
      if (cur !== o) {
        Haptics.success(context)
        toasts.show(StrOnline.movedTo(cur.orderNo, StrOnline.orderStatus(cur.status)))
      }
      busy = null
    }
  }

  val courier = OrdersHubModel.courier(o)
  V3Section(StrOrdersHub.prepTitle, footer = if (o.status in setOf("new", "accepted")) StrOrdersHub.methodNote else null) {
    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Text(StrOrdersHub.methodLine(courier), style = V3Type.callout, color = PhoneV3.colors.ink)
      if (prep.photo) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          V3Button(
            StrOrdersHub.sendPhoto, { openChat(true) }, Modifier.weight(1f), kind = V3ButtonKind.SECONDARY,
            icon = Icons.Outlined.AddAPhoto, loading = busy == "photo", haptic = true,
          )
          V3Button(
            StrOrdersHub.chatWithCustomer, { openChat(false) }, Modifier.weight(1f), kind = V3ButtonKind.NEUTRAL,
            icon = Icons.Outlined.Forum, loading = busy == "chat",
          )
        }
        Text(StrOrdersHub.sendPhotoHint, style = V3Type.footnote, color = PhoneV3.colors.inkMuted)
      }
      prep.pack?.let { list ->
        V3Button(
          StrOrdersHub.markPacked, { steps(list) }, Modifier.fillMaxWidth(), kind = V3ButtonKind.PRIMARY,
          icon = Icons.Outlined.Inventory2, loading = busy == list.last(), haptic = true,
        )
      }
      if (prep.packed) V3Message(V3Tone.SUCCESS, StrOrdersHub.packedDone)
      prep.dispatch?.let { to ->
        val toDesk = to == "out_for_delivery" && DeliveryGate.visible
        V3Button(
          if (courier) StrOrdersHub.toCourier else StrOrdersHub.readyPickup,
          { if (toDesk) nav.open(OnlinePaths.deliveryAssign(o.id)) else steps(listOf(to)) },
          Modifier.fillMaxWidth(), kind = V3ButtonKind.PRIMARY,
          icon = if (courier) Icons.Outlined.TwoWheeler else Icons.Outlined.Storefront, loading = busy == to, haptic = true,
        )
      }
      if (!chatOn && o.status !in setOf("delivered", "picked_up")) Text(StrOrdersHub.chatOff, style = V3Type.footnote, color = PhoneV3.colors.inkMuted)
      error?.let { V3Message(V3Tone.ERROR, it) }
    }
  }
}
