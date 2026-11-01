package ir.vil3ntec.tohid.ui.v3.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrAuth
import ir.vil3ntec.tohid.ui.screens.GoogleMark
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3FieldKind
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3TextButton
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.V3Type

/**
 *  کلیدِ «ورود با حساب گوگل» — فعلاً خاموش: تا کلاینت‌های Android و Web در Google Cloud ساخته نشوند
 *  و `GOOGLE_CLIENT_ID` روی سرور ننشیند، زدنش فقط خطا می‌دهد. هیچ کدی پاک نشده؛ `true` ⇒ برمی‌گردد.
 */
internal const val GOOGLE_LOGIN = false

/** دکمهٔ خنثیِ گوگل با نشانِ رسمیِ چهاررنگ در آغاز (راهِ اصلی نیست، پس آرام‌تر از دکمهٔ اصلی) */
@Composable
internal fun GoogleButton(enabled: Boolean, onClick: () -> Unit) {
  Box(Modifier.fillMaxWidth()) {
    V3Button(StrAuth.googleSignIn, onClick, Modifier.fillMaxWidth(), kind = V3ButtonKind.NEUTRAL, enabled = enabled, height = GATE_BUTTON)
    Box(Modifier.align(Alignment.CenterStart).padding(start = 18.dp)) { GoogleMark() }
  }
}

/**
 *  «این ایمیل از قبل حساب دارد» — وصل کردنِ گوگل به همان حساب. ⛔ برابر بودنِ ایمیل به‌تنهایی کافی
 *  نیست: رمزِ همان حساب، یا کدی که به ایمیلِ حساب می‌رود. بستن یعنی «وصل نکن» (هیچ حسابی ساخته یا
 *  عوض نمی‌شود)؛ تا درخواست در راه است برگه بسته نمی‌شود.
 */
@Composable
internal fun GoogleLinkSheet(
  ask: GoogleLinkAsk,
  busy: Boolean,
  onDismiss: () -> Unit,
  onSendCode: () -> Unit,
  onProve: (String) -> Unit,
) {
  val c = PhoneV3.colors
  var secret by remember(ask.codeSent) { mutableStateOf("") }
  V3Sheet(onDismiss = onDismiss, title = StrAuth.googleLinkTitle, dismissible = !busy, detents = false) { closer ->
    Text(ask.message.ifBlank { StrAuth.googleLinkBody }, style = V3Type.callout, color = c.inkMuted)
    Spacer(Modifier.height(6.dp))
    Text(ask.email, style = V3Type.headline, color = c.ink)
    Spacer(Modifier.height(14.dp))
    V3TextField(
      secret, { secret = it }, if (ask.codeSent) StrAuth.emailCode else StrAuth.thisAccountPassword,
      Modifier.fillMaxWidth(),
      kind = if (ask.codeSent) V3FieldKind.NUMBER else V3FieldKind.PASSWORD,
      enabled = !busy,
    )
    Spacer(Modifier.height(6.dp))
    if (!ask.codeSent) {
      if (!busy) V3TextButton(StrAuth.sendLinkCode, onSendCode)
    } else {
      Text(StrAuth.linkCodeSent, style = V3Type.footnote, color = c.inkMuted)
    }
    ask.problem?.let {
      Spacer(Modifier.height(10.dp))
      V3Message(V3Tone.ERROR, it)
    }
    Spacer(Modifier.height(18.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      V3Button(StrAuth.dontLink, { closer.close() }, Modifier.weight(1f), kind = V3ButtonKind.NEUTRAL, enabled = !busy)
      V3Button(StrAuth.linkAndSignIn, { onProve(secret) }, Modifier.weight(1f), enabled = !busy && secret.isNotBlank(), loading = busy, haptic = true)
    }
  }
}
