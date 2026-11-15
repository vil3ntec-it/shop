package ir.vil3ntec.tohid.ui.v3.settings.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
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
import ir.vil3ntec.tohid.core.i18n.StrHub
import ir.vil3ntec.tohid.core.qr.QrCode
import ir.vil3ntec.tohid.displayMillis
import ir.vil3ntec.tohid.platform.HubLinkV1Android
import ir.vil3ntec.tohid.platform.HubPairClient
import ir.vil3ntec.tohid.platform.HubPanel
import ir.vil3ntec.tohid.platform.ServerSwitchAndroid
import ir.vil3ntec.tohid.ui.components.pos.QrImage
import ir.vil3ntec.tohid.ui.screens.BarcodeScanSheet
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.LocalV3Toasts
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3FieldKind
import ir.vil3ntec.tohid.ui.v3.V3TextButton
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.V3Type
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 *  SYN-09/S29 — «هابِ شبکهٔ دکان». پرچمِ `app.multi_server` خاموش ⇒ هیچ کارتی (همان امروز).
 *  - کامپیوتر (`HubPanel.port` هست): روشن/خاموش، نشانی و اثرِ انگشت، کد و QR ِ یک‌باره (فقط صاحب/مدیر)،
 *    دستگاه‌های جفت‌شده با «همین حالا وصل»/آخرین بار و «جدا کردن»، شمارِ دسته‌های در انتظارِ سرور.
 *  - گوشی: کدِ شش‌رقمی با پیدا کردنِ mDNS یا خواندنِ QR؛ پس از جفت شدن نشانیِ هاب و «جدا شدن».
 */
@Composable
internal fun HubLanCard() {
  val context = LocalContext.current
  if (!remember { ServerSwitchAndroid.enabled(context) }) return
  val port = HubPanel.port
  if (port != null) HubHostCard(port) else HubJoinCard()
}

@Composable
private fun HubHostCard(port: HubPanel.Port) {
  val toasts = LocalV3Toasts.current
  val scope = rememberCoroutineScope()
  var state by remember { mutableStateOf(HubPanel.State.OFF) }
  var tick by remember { mutableStateOf(0) }
  LaunchedEffect(tick) {
    while (true) {
      state = withContext(Dispatchers.IO) { runCatching { port.state() }.getOrDefault(HubPanel.State.OFF) }
      delay(1_000)
    }
  }
  fun run(block: () -> String?) {
    scope.launch {
      val why = withContext(Dispatchers.IO) { runCatching(block).getOrElse { "error" } }
      if (why != null) toasts?.show(StrHub.why(why))
      tick++
    }
  }
  DeviceCard(StrHub.title, StrHub.caption, false, padding = PaddingValues(16.dp)) {
    val c = PhoneV3.colors
    if (!state.running) {
      Text(StrHub.off, style = V3Type.callout, color = c.inkMuted)
      Spacer(Modifier.height(8.dp))
      V3Button(StrHub.start, { run { port.start() } }, Modifier.fillMaxWidth())
      return@DeviceCard
    }
    Text(if (state.address.isEmpty()) StrHub.noAddress else StrHub.on(state.address), style = V3Type.callout, color = PhoneV3Roles.toneIcon(c, V3Tone.SUCCESS))
    if (state.pin.isNotEmpty()) {
      CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Text(StrHub.fingerprint(state.pin.chunked(4).take(4).joinToString(" ")), style = V3Type.footnote.copy(fontFamily = FontFamily.Monospace), color = c.inkMuted)
      }
    }
    if (!state.discoverable) Text(StrHub.notDiscoverable, style = V3Type.footnote, color = c.inkMuted)
    if (state.pending > 0) Text(StrHub.pending(state.pending), style = V3Type.footnote, color = c.inkMuted)
    if (state.forwarded > 0) Text(StrHub.forwarded(state.forwarded), style = V3Type.footnote, color = c.inkMuted)
    Spacer(Modifier.height(12.dp))
    V3Divider()
    Spacer(Modifier.height(12.dp))
    Text(StrHub.pairTitle, style = V3Type.subhead, color = c.ink)
    val pairing = state.pairing
    if (pairing == null) {
      Spacer(Modifier.height(8.dp))
      V3Button(StrHub.newPairing, { run { port.pair() } }, Modifier.fillMaxWidth(), kind = V3ButtonKind.SECONDARY)
    } else {
      PairingPanel(pairing)
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        V3TextButton(StrHub.newPairing, { run { port.pair() } })
        V3TextButton(StrHub.cancelPairing, { run { port.cancelPairing(); null } }, danger = true)
      }
    }
    Spacer(Modifier.height(12.dp))
    V3Divider()
    Spacer(Modifier.height(12.dp))
    Text(StrHub.devicesTitle(state.devices.size), style = V3Type.subhead, color = c.ink)
    if (state.devices.isEmpty()) Text(StrHub.noDevices, style = V3Type.footnote, color = c.inkMuted)
    val now = System.currentTimeMillis()
    for (d in state.devices) {
      Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Column(Modifier.weight(1f)) {
          CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Text(d.deviceId.take(24), style = V3Type.callout.copy(fontFamily = FontFamily.Monospace), color = c.ink)
          }
          val line = when {
            HubPanel.online(d, now) -> StrHub.deviceOnline
            d.lastSeenAt != null -> StrHub.deviceSeen(displayMillis(d.lastSeenAt))
            else -> StrHub.deviceNotSeen
          }
          Text(line, style = V3Type.footnote, color = if (HubPanel.online(d, now)) PhoneV3Roles.toneIcon(c, V3Tone.SUCCESS) else c.inkMuted)
        }
        V3TextButton(StrHub.unpair, { run { port.unpair(d.deviceId) } }, danger = true)
      }
    }
    Spacer(Modifier.height(12.dp))
    V3Button(StrHub.stop, { run { port.stop(); null } }, Modifier.fillMaxWidth(), kind = V3ButtonKind.SECONDARY)
  }
}

@Composable
private fun PairingPanel(p: HubPanel.Pairing) {
  val c = PhoneV3.colors
  val rows = remember(p.qr) { if (p.qr.isBlank()) null else runCatching { QrCode.encode(p.qr, QrCode.LARGE_MAX_VERSION) }.getOrNull() }
  var left by remember(p.expiresAt) { mutableStateOf(((p.expiresAt - System.currentTimeMillis()) / 1000).coerceAtLeast(0)) }
  LaunchedEffect(p.expiresAt) {
    while (left > 0) { delay(1_000); left = ((p.expiresAt - System.currentTimeMillis()) / 1000).coerceAtLeast(0) }
  }
  Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
    if (rows != null) Box(Modifier.size(168.dp)) { QrImage(rows, Modifier.size(168.dp)) }
    Column(Modifier.weight(1f)) {
      CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Text(DevicesModel.grouped(p.code), style = V3Type.largeTitle.copy(fontFamily = FontFamily.Monospace), color = PhoneV3Roles.brandText(c))
      }
      Text(StrHub.expiresIn(left), style = V3Type.footnote, color = c.inkMuted)
      Spacer(Modifier.height(6.dp))
      Text(StrHub.pairHint, style = V3Type.footnote, color = c.inkMuted)
    }
  }
}

@Composable
private fun HubJoinCard() {
  val context = LocalContext.current
  val toasts = LocalV3Toasts.current
  val scope = rememberCoroutineScope()
  var paired by remember { mutableStateOf(HubLinkV1Android.paired(context)) }
  var typed by remember { mutableStateOf("") }
  var busy by remember { mutableStateOf(false) }
  var scan by remember { mutableStateOf(false) }
  fun finish(r: HubPairClient.Result) {
    when (r) {
      is HubPairClient.Result.Ok -> { HubLinkV1Android.remember(context, r.paired); paired = true; typed = ""; toasts?.show(StrHub.joinedOk) }
      is HubPairClient.Result.Failed -> toasts?.show(StrHub.pairFailed(r.code))
    }
  }
  fun withCode() {
    if (busy) return
    busy = true
    scope.launch {
      toasts?.show(StrHub.searching)
      val r = withContext(Dispatchers.IO) {
        val found = ir.vil3ntec.tohid.platform.HubFind.hubs(context)
        if (found.isEmpty()) null
        else {
          val id = HubLinkV1Android.identityOf(context)
          var last: HubPairClient.Result = HubPairClient.Result.Failed("hub_unreachable")
          for (h in found) {
            last = HubPairClient.withCode("https://${h.ip}:${h.port}", typed, id)
            if (last is HubPairClient.Result.Ok) break
          }
          last
        }
      }
      busy = false
      if (r == null) toasts?.show(StrHub.notFound) else finish(r)
    }
  }
  if (scan) {
    BarcodeScanSheet(onDismiss = { scan = false }, onCode = { text ->
      scan = false
      scope.launch { finish(withContext(Dispatchers.IO) { HubPairClient.withQr(text, HubLinkV1Android.identityOf(context)) }) }
    }, title = StrHub.scanQr, qr = true)
  }
  DeviceCard(StrHub.joinTitle, StrHub.joinCaption, false, padding = PaddingValues(16.dp)) {
    if (paired) {
      Text(StrHub.joined(HubLinkV1Android.pairedUrl(context)), style = V3Type.callout, color = PhoneV3Roles.toneIcon(PhoneV3.colors, V3Tone.SUCCESS))
      Spacer(Modifier.height(8.dp))
      V3TextButton(StrHub.leave, { HubLinkV1Android.forget(context); paired = false }, danger = true)
      return@DeviceCard
    }
    V3Button(StrHub.scanQr, { scan = true }, Modifier.fillMaxWidth(), kind = V3ButtonKind.SECONDARY, icon = Icons.Outlined.QrCodeScanner)
    Spacer(Modifier.height(10.dp))
    V3TextField(typed, { typed = it.filter(Char::isDigit).take(6) }, StrHub.codeLabel, Modifier.fillMaxWidth(), kind = V3FieldKind.NUMBER, imeAction = ImeAction.Done, onImeAction = { withCode() })
    Spacer(Modifier.height(10.dp))
    V3Button(StrHub.findAndJoin, { withCode() }, Modifier.fillMaxWidth(), enabled = typed.length == 6 && !busy, loading = busy)
  }
}
