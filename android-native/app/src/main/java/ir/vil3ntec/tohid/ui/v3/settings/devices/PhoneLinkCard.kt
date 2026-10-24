package ir.vil3ntec.tohid.ui.v3.settings.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrSettings
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.formatMillis
import ir.vil3ntec.tohid.sync.PhoneScanner
import ir.vil3ntec.tohid.ui.screens.Haptics
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.LocalV3Toasts
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3FieldKind
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3PickerSheet
import ir.vil3ntec.tohid.ui.v3.V3TextButton
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.V3Type
import kotlinx.coroutines.launch

/**
 *  گوشی ⇄ کامپیوتر (`PhoneScanner`) با یک کدِ شش‌رقمی: این دستگاه **گیرنده** (کامپیوترِ
 *  صندوق — ساختنِ کد، کدِ تازه، قطع، عکس‌های رسیده) یا **فرستنده** (گوشی — زدنِ کد،
 *  «وصل»، فرستادنِ عکس). همه همان کارهای امروز.
 */
@Composable
internal fun PhoneLinkCard(data: ShopData, highlight: Boolean) {
  val context = LocalContext.current
  val toasts = LocalV3Toasts.current
  val signedIn = remember { Backend.isReady(context) }
  val listen = PhoneScanner.listenCode.value
  val send = PhoneScanner.sendCode.value
  DeviceCard(StrSettings.phoneLink, if (signedIn) StrSettings.phoneLinkSub else StrSettings.phoneLinkNeedsAccount, highlight, padding = PaddingValues(16.dp)) {
    Text(StrSettings.asReceiver, style = V3Type.subhead, color = PhoneV3.colors.inkMuted)
    Spacer(Modifier.height(8.dp))
    if (listen == null) {
      V3Button(StrSettings.makePairCode, { PhoneScanner.startListening(context) }, Modifier.fillMaxWidth(), kind = V3ButtonKind.SECONDARY)
    } else {
      CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Text(DevicesModel.grouped(listen), style = V3Type.largeTitle.copy(fontFamily = FontFamily.Monospace), color = PhoneV3Roles.brandText(PhoneV3.colors))
      }
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        V3TextButton(StrSettings.newPairCode, { PhoneScanner.startListening(context) })
        V3TextButton(StrSettings.disconnect, { PhoneScanner.stopListening(context) }, danger = true)
      }
      Text(StrSettings.pairHowTo, style = V3Type.footnote, color = PhoneV3.colors.inkMuted)
      PhoneScanner.lastReceived.value?.let { (code, at) ->
        Text(StrSettings.lastFromPhone(code, formatMillis(at)), style = V3Type.footnote, color = PhoneV3Roles.toneIcon(PhoneV3.colors, V3Tone.SUCCESS), modifier = Modifier.padding(top = 4.dp))
      }
      Inbox(data)
    }
    Spacer(Modifier.height(14.dp))
    V3Divider()
    Spacer(Modifier.height(14.dp))
    Text(StrSettings.asSender, style = V3Type.subhead, color = PhoneV3.colors.inkMuted)
    Spacer(Modifier.height(8.dp))
    if (send == null) Sender { typed ->
      if (PhoneScanner.pairTo(context, typed)) { Haptics.success(context); toasts?.show(StrSettings.paired); true }
      else { toasts?.show(StrSettings.codeSixDigits); false }
    } else {
      Text(StrSettings.sendingTo(DevicesModel.grouped(send)), style = V3Type.callout, color = PhoneV3Roles.toneIcon(PhoneV3.colors, V3Tone.SUCCESS))
      Spacer(Modifier.height(8.dp))
      SendPhoto()
      V3TextButton(StrSettings.disconnect, { PhoneScanner.unpair(context) }, danger = true)
    }
  }
}

@Composable
private fun Sender(pair: (String) -> Boolean) {
  var typed by remember { mutableStateOf("") }
  V3TextField(typed, { typed = it.take(7) }, StrSettings.pcCode, Modifier.fillMaxWidth(), kind = V3FieldKind.NUMBER, imeAction = ImeAction.Done, onImeAction = { if (pair(typed)) typed = "" })
  Spacer(Modifier.height(10.dp))
  V3Button(StrSettings.connect, { if (pair(typed)) typed = "" }, Modifier.fillMaxWidth(), enabled = typed.isNotBlank())
}

@Composable
private fun SendPhoto() {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val toasts = LocalV3Toasts.current
  var pick by remember { mutableStateOf(false) }
  DraftPhotoSource(open = pick, onDismiss = { pick = false }) { uri ->
    toasts?.show(StrSettings.sendingDots)
    scope.launch { toasts?.show(PhoneScanner.sendPhoto(context, uri, "") ?: StrSettings.photoArrived) }
  }
  V3Button(StrSettings.sendPhoto, { pick = true }, Modifier.fillMaxWidth(), kind = V3ButtonKind.SECONDARY, icon = Icons.Outlined.PhotoCamera)
}

/** عکس‌هایی که گوشی بی کالای مشخص فرستاده — «گذاشتن روی کالا…» یا «دور انداختن» */
@Composable
private fun Inbox(data: ShopData) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val inbox = PhoneScanner.inboxItems
  var assign by remember { mutableStateOf<PhoneScanner.InboxItem?>(null) }
  if (inbox.isEmpty()) return
  assign?.let { item ->
    //  نامِ کالا ⇐ شناسه؛ نامِ تکراری با بارکدش جدا می‌شود
    val labels = remember(data.products) {
      val dup = data.products.groupingBy { it.name }.eachCount()
      data.products.associateBy { p -> p.barcodes.firstOrNull()?.takeIf { (dup[p.name] ?: 0) > 1 }?.let { "${p.name} · $it" } ?: p.name }
    }
    V3PickerSheet(
      title = StrSettings.putOnProduct,
      options = labels.keys.toList(),
      selected = null,
      onPick = { label -> labels[label]?.let { PhoneScanner.openNow.value = item.copy(productId = it.id) } },
      onDismiss = { assign = null },
      allowNew = false,
    )
  }
  Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
    Text(StrSettings.inboxTitle(inbox.size), style = V3Type.subhead, color = PhoneV3.colors.ink)
    inbox.toList().forEach { item ->
      V3ListRow(
        StrSettings.photoAt(formatMillis(item.at)),
        icon = Icons.Outlined.Image,
        tile = false,
        chevron = false,
        trailing = {
          Row {
            V3TextButton(StrSettings.putOnProductShort, { assign = item })
            V3TextButton(StrSettings.discardPhoto, { scope.launch { PhoneScanner.discard(context, item) } }, danger = true)
          }
        },
        endActions = listOf(ir.vil3ntec.tohid.ui.v3.V3SwipeAction(Icons.Outlined.Delete, StrSettings.discardPhoto, V3ButtonKind.DANGER) { scope.launch { PhoneScanner.discard(context, item) } }),
      )
    }
  }
}
