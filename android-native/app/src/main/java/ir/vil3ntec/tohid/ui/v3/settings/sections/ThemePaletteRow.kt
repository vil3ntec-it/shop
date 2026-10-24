package ir.vil3ntec.tohid.ui.v3.settings.sections

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.ui.screens.MotionSpec
import ir.vil3ntec.tohid.ui.screens.MotionTokens
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.v3Pressable

/** یک تم در ردیفِ «تم»: شناسهٔ ماندگار، نام (`StrSettings.themeSky|Ocean|Sunset`) و گرادیانِ کوچکِ پیش‌نمایش */
internal class ThemeOption(val id: String, val label: () -> String, val preview: Brush)

/**
 *  ردیفِ «تم» در «ظاهر» (THEME-V2): سه کارتِ پیش‌نمایش — گرادیانِ کوچک و نام. زدن ⇐ `onPick`؛
 *  کارتِ انتخاب‌شده حلقهٔ برند و تیک دارد و حلقه با محو جابه‌جا می‌شود. گرادیان‌ها و ذخیرهٔ تم
 *  را پایه می‌دهد (`ThemeStore`)؛ این‌جا فقط نمایش است.
 */
@Composable
internal fun ThemePaletteRow(options: List<ThemeOption>, selected: String, onPick: (String) -> Unit) {
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
    options.forEach { o -> ThemeCard(o, o.id == selected, Modifier.weight(1f)) { onPick(o.id) } }
  }
}

@Composable
private fun ThemeCard(option: ThemeOption, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
  val c = PhoneV3.colors
  val shape = RoundedCornerShape(14.dp)
  val ring by animateColorAsState(if (on) PhoneV3Roles.focus(c) else c.hairline, MotionSpec.tweenOf(MotionTokens.MEDIUM), label = "themeRing")
  val label = option.label()
  Column(modifier.v3Pressable(label = label, haptic = true, onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
    Box(
      Modifier.fillMaxWidth().height(56.dp).clip(shape).background(option.preview).border(if (on) 2.dp else 1.dp, ring, shape),
      contentAlignment = Alignment.TopEnd,
    ) {
      if (on) Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = c.onBlack, modifier = Modifier.padding(6.dp).size(18.dp))
    }
    Spacer(Modifier.height(6.dp))
    Text(label, style = V3Type.subhead, color = if (on) PhoneV3Roles.brandText(c) else c.ink, textAlign = TextAlign.Center)
  }
}
