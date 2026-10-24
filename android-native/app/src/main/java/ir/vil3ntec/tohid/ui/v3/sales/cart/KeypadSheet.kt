package ir.vil3ntec.tohid.ui.v3.sales.cart

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.qty
import ir.vil3ntec.tohid.ui.components.pos.KeypadInput
import ir.vil3ntec.tohid.ui.components.pos.PhoneSaleText
import ir.vil3ntec.tohid.ui.screens.MotionSpec
import ir.vil3ntec.tohid.ui.screens.MotionTokens
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Chip
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.v3Pressable
import ir.vil3ntec.tohid.ui.v3.v3Shake

/**
 *  صفحه‌کلیدِ مقدار/وزنِ یک کالا (`sale/keypad/{productId}` — نگه داشتنِ کاشی، یا زدنِ کالای
 *  وزنی): نام و قیمتِ واحد، نمایشگرِ درشتِ مقدار با واحد، مبلغِ همان مقدار یا خطا، مقدارهای
 *  آماده، ۳×۴ کلیدِ گرد روی سطحِ درونی (فشار ۰٫۹۴) و دکمهٔ «افزودن به سبد» / «ثبتِ مقدار» /
 *  «برداشتن از سبد». رقم‌ها مثلِ هر صفحه‌کلیدِ گوشی همیشه چپ‌به‌راست‌اند.
 */
@Composable
fun KeypadSheet(
  item: KeypadItem,
  onConfirm: (Double) -> Unit,
  onClose: () -> Unit,
  /** متنِ آغازِ دلخواه (عکس‌های پیش‌نمایش) */
  initial: String? = null,
) {
  val c = PhoneV3.colors
  var text by remember(item.product.id) { mutableStateOf(initial ?: KeypadModel.start(item)) }
  var shake by remember { mutableIntStateOf(0) }
  val value = KeypadModel.value(text)
  val problem = KeypadModel.problem(item, value)
  val action = KeypadModel.action(item, value)
  val unit = item.product.unit.ifBlank { StrSale.unitFallback }

  V3Sheet(onDismiss = onClose, detents = false) { closer ->
    Text(item.product.name, style = V3Type.title, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
    Text("${PhoneSaleText.amount(item.product.salePrice)} / $unit", style = V3Type.subhead, color = c.inkMuted)
    Spacer(Modifier.height(14.dp))

    Row(
      Modifier.fillMaxWidth().v3Shake(shake.takeIf { it > 0 }).semantics { liveRegion = LiveRegionMode.Polite },
      horizontalArrangement = Arrangement.Center,
      verticalAlignment = Alignment.Bottom,
    ) {
      Text(text.ifEmpty { "0" }, style = V3Type.largeTitle.copy(fontSize = 44.sp, lineHeight = 52.sp), color = c.ink, maxLines = 1)
      Spacer(Modifier.width(8.dp))
      Text(unit, style = V3Type.body, color = c.inkMuted, maxLines = 1, modifier = Modifier.padding(bottom = 8.dp))
    }
    AnimatedContent(
      targetState = problem ?: "= ${PhoneSaleText.amount(KeypadModel.amount(item, value))}",
      transitionSpec = { fadeIn(MotionSpec.tweenOf(MotionTokens.SHORT)) togetherWith fadeOut(MotionSpec.tweenOf(MotionTokens.SHORT)) },
      label = "keypadNote",
      modifier = Modifier.fillMaxWidth(),
    ) { note ->
      Text(
        note,
        style = V3Type.subhead,
        color = if (problem != null) PhoneV3Roles.errorText(c) else c.inkMuted,
        textAlign = TextAlign.Center,
        maxLines = 2,
        modifier = Modifier.fillMaxWidth(),
      )
    }
    Spacer(Modifier.height(14.dp))

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      KeypadModel.presets(item).forEach { p ->
        V3Chip(qty(p.toDouble()), text == p, { text = p }, Modifier.weight(1f))
      }
    }
    Spacer(Modifier.height(12.dp))

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        KeypadModel.keys(item).chunked(3).forEach { row ->
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { k -> Key(k, Modifier.weight(1f)) { text = KeypadModel.press(text, k, item) } }
          }
        }
      }
    }
    Spacer(Modifier.height(16.dp))
    V3Button(
      when (action) {
        KeypadAction.ADD -> StrSale.addToCart
        KeypadAction.SET -> StrSale.setQty
        KeypadAction.REMOVE -> StrSale.removeFromCartAlt
      },
      { if (KeypadModel.canConfirm(item, value)) closer.close { onConfirm(value) } else shake++ },
      Modifier.fillMaxWidth(),
      kind = if (action == KeypadAction.REMOVE) V3ButtonKind.DANGER else V3ButtonKind.PRIMARY,
      enabled = problem == null,
      haptic = true,
    )
  }
}

/** یک کلیدِ گرد (۱۴) روی سطحِ درونی؛ کلیدِ خالیِ کالای دانه‌ای جایش را نگه می‌دارد */
@Composable
private fun Key(k: Char, modifier: Modifier, onPress: () -> Unit) {
  val c = PhoneV3.colors
  val shape = RoundedCornerShape(PhoneV3.tileRadius)
  if (k == ' ') { Spacer(modifier.height(KEY_HEIGHT)); return }
  val label = if (k == KeypadInput.BACKSPACE) StrSale.backspace else k.toString()
  Box(
    modifier.height(KEY_HEIGHT).clip(shape).background(c.tile).v3Pressable(label = label, scale = 0.94f, onClick = onPress),
    contentAlignment = Alignment.Center,
  ) {
    if (k == KeypadInput.BACKSPACE) {
      Icon(Icons.AutoMirrored.Outlined.Backspace, contentDescription = null, tint = c.icon, modifier = Modifier.size(22.dp))
    } else {
      Text(k.toString(), style = V3Type.title.copy(fontSize = 22.sp, fontWeight = FontWeight.SemiBold), color = c.ink)
    }
  }
}

private val KEY_HEIGHT = 54.dp
