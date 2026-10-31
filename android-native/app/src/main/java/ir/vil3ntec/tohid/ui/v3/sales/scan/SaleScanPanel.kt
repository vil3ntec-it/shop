package ir.vil3ntec.tohid.ui.v3.sales.scan

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.plain
import ir.vil3ntec.tohid.ui.screens.MotionSpec
import ir.vil3ntec.tohid.ui.screens.MotionTokens
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Chip
import ir.vil3ntec.tohid.ui.v3.V3SearchField
import ir.vil3ntec.tohid.ui.v3.V3Springs
import ir.vil3ntec.tohid.ui.v3.V3Type

/**
 *  پایینِ برگهٔ اسکنِ صندوق: پاسخِ آخرین خواندن (قرصِ تیره با آیکنِ «خواند»/«نه» — هر پاسخِ
 *  تازه با فنرِ کوچک)، بارکدِ دستی و «هر اسکن ×۱ ×۲ ×۵ ×۱۰». همه همان کارهای قابِ اسکنِ
 *  صندوقِ پیشین؛ بارکدِ ناشناس ⇒ «ثبتِ کالا» همان‌جا.
 */
@Composable
fun SaleScanPanel(
  shown: SaleScan.Shown?,
  multiplier: Int,
  onMultiplier: (Int) -> Unit,
  onManual: (String) -> Unit,
  onRegister: (String) -> Unit,
) {
  val c = PhoneV3.colors
  var manual by remember { mutableStateOf("") }
  fun submit() { manual.trim().takeIf { it.isNotEmpty() }?.let { onManual(it); manual = "" } }

  Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    AnimatedContent(
      targetState = shown,
      transitionSpec = { (fadeIn(MotionSpec.tweenOf(MotionTokens.SHORT)) + scaleIn(V3Springs.playful(), initialScale = 0.92f)) togetherWith fadeOut(MotionSpec.tweenOf(MotionTokens.SHORT)) },
      contentKey = { it?.seq },
      label = "scanNote",
    ) { s ->
      if (s == null) Spacer(Modifier.heightIn(min = 44.dp))
      else {
        val ok = s.note.tone == V3Tone.SUCCESS
        Row(
          Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(percent = 50)).background(c.blackRaised)
            .padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(
            if (ok) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
            contentDescription = null,
            tint = if (ok) c.cart else PhoneV3Roles.dangerOnInverse,
            modifier = Modifier.size(20.dp),
          )
          Spacer(Modifier.width(8.dp))
          Text(s.note.text, style = V3Type.subhead, color = c.onBlack, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
          s.note.register?.let { code -> V3Button(StrSale.registerItemAlt, { onRegister(code) }, kind = V3ButtonKind.SECONDARY, height = 36.dp) }
        }
      }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
      Box(Modifier.weight(1f)) { V3SearchField(manual, { manual = it }, StrSale.manualBarcode, onSubmit = ::submit) }
      Spacer(Modifier.width(8.dp))
      V3Button(StrKit.add, ::submit, enabled = manual.isNotBlank(), height = 46.dp)
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(StrSale.perScan, style = V3Type.footnote, color = c.onBlackMuted)
      SaleScan.MULTIPLIERS.forEach { m -> V3Chip("×${plain(m)}", m == multiplier, { onMultiplier(m) }) }
    }
  }
}
