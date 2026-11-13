package ir.vil3ntec.tohid.ui.v3.online.orders

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrOrderOps
import ir.vil3ntec.tohid.core.model.OrderDto
import ir.vil3ntec.tohid.core.model.OrdersConfigDto
import ir.vil3ntec.tohid.core.model.TrackLinkDto
import ir.vil3ntec.tohid.data.repo.OrdersRepository
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.v3.V3ConfirmSheet
import ir.vil3ntec.tohid.ui.v3.V3Section
import ir.vil3ntec.tohid.ui.v3.V3TextButton
import ir.vil3ntec.tohid.ui.v3.V3Type
import kotlinx.coroutines.launch

/**
 *  TEN-03 — «باطل کردنِ لینکِ پیگیری» در جزئیاتِ سفارشِ ویترین (`POST /api/shop/orders/:id/track-link/revoke`).
 *  ⛔ فقط با کلیدِ `trackLinkRevoke` ِ `/config` (پرچمِ `shop.storefront.link_expiry` + `orders.contact` ِ صاحب و مدیر؛
 *  سرور همان را دوباره می‌سنجد) و فقط سفارشِ ویترین (سفارشِ تلفنی/دستی لینک ندارد). تأیید پیش از باطل کردن؛ لینکِ تازه
 *  فقط در حافظهٔ همین صفحه (رونوشت یا واتس‌اپ ِ مشتری اگر نقش شماره را می‌بیند) — هیچ‌جا نوشته نمی‌شود.
 */
object OrderTrackLink {
  fun visible(o: OrderDto, c: OrdersConfigDto): Boolean = c.trackLinkRevoke && o.source == "storefront"
}

@Composable
internal fun OrderTrackLinkSection(o: OrderDto, c: OrdersConfigDto, repo: OrdersRepository, onMessage: (String) -> Unit) {
  if (!OrderTrackLink.visible(o, c)) return
  val scope = rememberCoroutineScope()
  val clipboard = LocalClipboardManager.current
  val uri = LocalUriHandler.current
  var asking by remember(o.id) { mutableStateOf(false) }
  var busy by remember(o.id) { mutableStateOf(false) }
  var fresh by remember(o.id) { mutableStateOf<TrackLinkDto?>(null) }
  V3Section(StrOrderOps.trackLinkTitle, footer = StrOrderOps.trackLinkHelp) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
      val got = fresh
      if (got != null && got.link.isNotBlank()) {
        SelectionContainer {
          Text(got.link, style = V3Type.footnote, color = PhoneV3.colors.inkMuted, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp))
        }
        V3TextButton(StrOrderOps.trackLinkCopy, {
          clipboard.setText(AnnotatedString(got.link))
          onMessage(StrOrderOps.trackLinkCopied)
        })
        if (got.whatsappUrl.isNotBlank()) V3TextButton(StrOrderOps.trackLinkWhatsApp, { runCatching { uri.openUri(got.whatsappUrl) } })
      }
      V3TextButton(StrOrderOps.trackLinkRevoke, { if (!busy) asking = true }, danger = true)
    }
  }
  if (asking) {
    V3ConfirmSheet(
      title = StrOrderOps.trackLinkConfirmTitle,
      message = StrOrderOps.trackLinkConfirmBody,
      confirm = StrOrderOps.trackLinkRevoke,
      icon = Icons.Outlined.LinkOff,
      onConfirm = {
        asking = false
        busy = true
        scope.launch {
          repo.revokeTrackLink(o.id)
            .onSuccess { fresh = it; onMessage(StrOrderOps.trackLinkRevoked) }
            .onFailure { onMessage(it.userMessage) }
          busy = false
        }
      },
      onDismiss = { asking = false },
    )
  }
}
