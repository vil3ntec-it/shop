package ir.vil3ntec.tohid.ui.v3.rate

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrRate
import ir.vil3ntec.tohid.core.rate.RateChoice
import ir.vil3ntec.tohid.core.rate.RateTarget
import ir.vil3ntec.tohid.ui.screens.v3Text
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Sheet

/**
 *  RATE-01 — برگهٔ کوچکِ «ستاره بدهید»: ستاره در دایرهٔ کهربایی، یک جملهٔ مهربان و سه دکمه.
 *
 *  ⛔ قاعدهٔ فروشگاه‌ها: هیچ پرسشی پیش از برگه یا درونِ آن («دوست دارید؟»)، هیچ شمارِ ستاره، هیچ جایزه؛ «ستاره می‌دهم» برای
 *  **هر** کس فروشگاه را باز می‌کند (میزبان: `RatePromptAndroid.kt`). [onChoice] دقیقاً یک بار، پس از رفتنِ برگه؛ کشیدن،
 *  زدنِ بیرون یا «برگشت» همان «بعداً» است. آزمون: `RateSheetUiTest`.
 */
@Composable
fun RateSheet(target: RateTarget, onChoice: (RateChoice) -> Unit) {
  val c = PhoneV3.colors
  var chosen by remember { mutableStateOf<RateChoice?>(null) }
  var done by remember { mutableStateOf(false) }
  V3Sheet(
    onDismiss = {
      if (!done) {
        done = true
        onChoice(chosen ?: RateChoice.LATER)
      }
    },
    title = StrRate.title,
    detents = false,
  ) { closer ->
    //  زدنِ دوباره در میانهٔ رفتنِ برگه انتخاب را عوض نمی‌کند و برگه را دو بار نمی‌بندد
    fun pick(choice: RateChoice) {
      if (chosen != null) return
      chosen = choice
      closer.close()
    }
    val tone = PhoneV3Roles.tone(c, V3Tone.WARNING)
    Box(
      Modifier.align(Alignment.CenterHorizontally).size(56.dp).clip(RoundedCornerShape(50)).background(tone.bg),
      contentAlignment = Alignment.Center,
    ) { Icon(Icons.Filled.Star, contentDescription = null, tint = tone.fg, modifier = Modifier.size(30.dp)) }
    Spacer(Modifier.height(10.dp))
    Text(
      rateBody(target),
      style = v3Text(15, FontWeight.Medium, 23),
      color = c.inkMuted,
      textAlign = TextAlign.Center,
      modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(20.dp))
    V3Button(StrRate.rate, { pick(RateChoice.RATE) }, Modifier.fillMaxWidth(), kind = V3ButtonKind.PRIMARY, haptic = true)
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      V3Button(StrRate.later, { pick(RateChoice.LATER) }, Modifier.weight(1f), kind = V3ButtonKind.NEUTRAL)
      V3Button(StrRate.never, { pick(RateChoice.NEVER) }, Modifier.weight(1f), kind = V3ButtonKind.NEUTRAL)
    }
  }
}

/** نامِ فروشگاهِ همین ساخت در جمله */
internal fun rateBody(target: RateTarget): String = when (target) {
  RateTarget.BAZAAR -> StrRate.bodyBazaar
  RateTarget.APP_STORE -> StrRate.bodyAppStore
  else -> StrRate.bodyPlay
}
