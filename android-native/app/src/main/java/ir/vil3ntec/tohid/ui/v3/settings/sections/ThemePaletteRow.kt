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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.ui.screens.MotionSpec
import ir.vil3ntec.tohid.ui.screens.MotionTokens
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.v3Pressable

/**
 *  یک تم در ردیفِ «تم»: شناسهٔ ماندگار، نام (`StrSettings.themeSky|Navy|Ivory|Graphite`)، گرادیانِ کادرِ خانه و رنگ‌های واقعیِ همان
 *  تم در حالتِ جاری (زمینه، کارت و خطش، نوشته، دکمهٔ اصلی) — پیش‌نمایش همان چیزی است که پس از زدن دیده می‌شود.
 */
internal class ThemeOption(
  val id: String,
  val label: () -> String,
  val preview: Brush,
  val page: Color,
  val card: Color,
  val edge: Color,
  val ink: Color,
  val muted: Color,
  val primary: Color,
)

/**
 *  ردیفِ «تم» در «ظاهر» (۶۹ p3): چهار کارتِ پیش‌نمایش — صفحهٔ کوچکِ همان تم (زمینه، کادرِ گرادیانی، کارتِ سفید/تیره با دو خط و
 *  دکمهٔ اصلی) و نام. زدن ⇐ `onPick`؛ کارتِ انتخاب‌شده حلقهٔ برند و تیک دارد و حلقه با محو جابه‌جا می‌شود. رنگ‌ها و ذخیرهٔ تم را
 *  پایه می‌دهد (`ThemeStore`)؛ این‌جا فقط نمایش است.
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
      Modifier.fillMaxWidth().height(84.dp).clip(shape).background(option.page).border(if (on) 2.dp else 1.dp, ring, shape),
      contentAlignment = Alignment.TopEnd,
    ) {
      Column(Modifier.fillMaxWidth().padding(6.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.fillMaxWidth().height(22.dp).clip(RoundedCornerShape(6.dp)).background(option.preview))
        Column(
          Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(option.card).border(1.dp, option.edge, RoundedCornerShape(6.dp)).padding(5.dp),
          verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
          Box(Modifier.fillMaxWidth(0.7f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(option.ink))
          Box(Modifier.fillMaxWidth(0.45f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(option.muted))
        }
        Box(Modifier.width(30.dp).height(10.dp).clip(RoundedCornerShape(5.dp)).background(option.primary))
      }
      if (on) Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.padding(8.dp).size(16.dp))
    }
    Spacer(Modifier.height(6.dp))
    Text(label, style = V3Type.subhead, color = if (on) PhoneV3Roles.brandText(c) else c.ink, textAlign = TextAlign.Center)
  }
}
