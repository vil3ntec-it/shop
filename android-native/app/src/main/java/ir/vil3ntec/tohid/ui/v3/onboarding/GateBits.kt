package ir.vil3ntec.tohid.ui.v3.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrAuth
import ir.vil3ntec.tohid.ui.screens.v3Text
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Collapse
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3PageSwitch
import ir.vil3ntec.tohid.ui.v3.V3SuccessCheck
import ir.vil3ntec.tohid.ui.v3.V3TextButton
import ir.vil3ntec.tohid.ui.v3.v3Shake

/** بلندیِ دکمهٔ اصلیِ دروازه (تمام‌عرض) */
internal val GATE_BUTTON = 52.dp

fun EmailIssue.text(): String = when (this) {
  EmailIssue.EMPTY -> StrAuth.enterEmail
  EmailIssue.TOO_LONG -> StrAuth.emailTooLong
  EmailIssue.INCOMPLETE -> StrAuth.emailIncomplete
}

fun PasswordIssue.text(): String = when (this) {
  PasswordIssue.EMPTY -> StrAuth.enterPassword
  PasswordIssue.SHORT -> StrAuth.passwordMin8
}

fun AuthNote.text(): String = when (this) {
  AuthNote.RESENT -> StrAuth.codeResent
  AuthNote.PREVIOUS_VALID -> StrAuth.previousCodeValid
  AuthNote.ACCOUNT_CREATED -> StrAuth.accountCreated
  AuthNote.PHONE_ACCOUNT -> StrAuth.phoneAccount
}

private fun AuthNote.tone(): V3Tone = if (this == AuthNote.ACCOUNT_CREATED) V3Tone.SUCCESS else V3Tone.INFO

/**
 *  دکمهٔ اصلیِ تمام‌عرضِ ۵۲ که پس از ورود با یک گذرِ کیت به تیکِ موفقیت تبدیل می‌شود
 *  (`V3SuccessCheck`: فنرِ شاد، کشیدنِ تیک، لرزشِ نرم) و بعد برنامه می‌آید.
 */
@Composable
internal fun SubmitButton(text: String, enabled: Boolean, loading: Boolean, done: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
  V3PageSwitch(target = done, depth = { 0 }, modifier = modifier.fillMaxWidth()) { ok ->
    if (ok) {
      Box(Modifier.fillMaxWidth().height(GATE_BUTTON), contentAlignment = Alignment.Center) {
        V3SuccessCheck(visible = true, size = GATE_BUTTON - 4.dp)
      }
    } else {
      V3Button(text, onClick, Modifier.fillMaxWidth(), enabled = enabled, loading = loading, haptic = true, height = GATE_BUTTON)
    }
  }
}

/** خطای سرور (با لرزشِ کیت) یا پیامِ آگاهی، داخلِ کارت؛ آمدن و رفتن نرم */
@Composable
internal fun GateMessages(error: String?, note: AuthNote?, shake: Int) {
  V3Collapse(visible = error != null) {
    V3Message(V3Tone.ERROR, error.orEmpty(), Modifier.padding(top = 14.dp).v3Shake(shake.takeIf { error != null && it > 0 }))
  }
  V3Collapse(visible = error == null && note != null) {
    note?.let { V3Message(it.tone(), it.text(), Modifier.padding(top = 14.dp)) }
  }
}

/** جداکنندهٔ «یا» میانِ راهِ اصلی و راه‌های دیگر */
@Composable
internal fun OrDivider() {
  val c = PhoneV3.colors
  Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.weight(1f).height(1.dp).background(c.hairline))
    Text(StrAuth.or, style = v3Text(13, FontWeight.Medium, 18), color = c.inkMuted, modifier = Modifier.padding(horizontal = 12.dp))
    Box(Modifier.weight(1f).height(1.dp).background(c.hairline))
  }
}

/** دکمهٔ متنی که خاموش هم می‌شود (شمارشِ «ارسالِ دوباره») — خاموش ⇐ نوشتهٔ کم‌رنگ، بی لمس */
@Composable
internal fun GateLink(text: String, onClick: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier) {
  if (enabled) V3TextButton(text, onClick, modifier)
  else Text(
    text,
    style = v3Text(15, FontWeight.SemiBold, 22),
    color = PhoneV3.colors.inkMuted,
    textAlign = TextAlign.Center,
    modifier = modifier.padding(horizontal = 8.dp, vertical = 6.dp),
  )
}

/**
 *  دو راهِ فرعیِ هم‌وزن (پای کارتِ دروازه): در یک خط با فاصله، و اگر جا نشد هر کدام خطِ خودش را
 *  می‌گیرد — هیچ واژه‌ای نمی‌شکند و جداکننده‌ای تنها سرِ خط نمی‌ماند.
 */
@Composable
internal fun GateLinks(vararg links: Pair<String, () -> Unit>) {
  FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
    links.forEach { (label, go) -> V3TextButton(label, go) }
  }
}
