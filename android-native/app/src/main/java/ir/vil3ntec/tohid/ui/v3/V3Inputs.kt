package ir.vil3ntec.tohid.ui.v3

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.heightIn
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.formatDate
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.screens.MotionSpec
import ir.vil3ntec.tohid.ui.screens.MotionTokens
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles

/**
 *  کادرِ جست‌وجو (یکی در هر صفحه): قرصِ سفید با ذره‌بین، دکمهٔ پاک کردن وقتی
 *  نوشته هست، و دکمه‌های اختیاریِ اسکن و صدا. حلقهٔ تمرکز برند.
 */
@Composable
fun V3SearchField(
  value: String,
  onValueChange: (String) -> Unit,
  placeholder: String,
  modifier: Modifier = Modifier,
  onScan: (() -> Unit)? = null,
  onVoice: (() -> Unit)? = null,
  onSubmit: (() -> Unit)? = null,
) {
  val c = PhoneV3.colors
  val source = remember { MutableInteractionSource() }
  val focused by source.collectIsFocusedAsState()
  val ring by animateColorAsState(if (focused) PhoneV3Roles.focus(c) else c.hairline, MotionSpec.tweenOf(MotionTokens.SHORT), label = "searchRing")
  val shape = RoundedCornerShape(14.dp)
  BasicTextField(
    value = value,
    onValueChange = onValueChange,
    singleLine = true,
    textStyle = V3Type.body.copy(color = c.ink),
    cursorBrush = SolidColor(PhoneV3Roles.focus(c)),
    interactionSource = source,
    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
    keyboardActions = KeyboardActions(onSearch = { onSubmit?.invoke() }),
    modifier = modifier.fillMaxWidth().semantics { contentDescription = placeholder },
    decorationBox = { inner ->
      Row(
        Modifier
          .fillMaxWidth()
          .heightIn(min = 46.dp)
          .clip(shape)
          .background(c.card)
          .border(if (focused) 2.dp else 1.dp, ring, shape)
          .padding(start = 12.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(Icons.Outlined.Search, contentDescription = null, tint = c.icon, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
          if (value.isEmpty()) Text(placeholder, style = V3Type.body, color = c.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
          inner()
        }
        AnimatedVisibility(value.isNotEmpty(), enter = fadeIn() + scaleIn(initialScale = 0.6f), exit = fadeOut() + scaleOut(targetScale = 0.6f)) {
          FieldIcon(Icons.Outlined.Close, StrKit.clear) { onValueChange("") }
        }
        if (onVoice != null) FieldIcon(Icons.Outlined.Mic, StrKit.voice, onVoice)
        if (onScan != null) FieldIcon(Icons.Outlined.QrCodeScanner, StrKit.scanBarcode, onScan)
      }
    },
  )
}

@Composable
private fun FieldIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
  val c = PhoneV3.colors
  Box(
    Modifier.size(36.dp).clip(CircleShape).v3Pressable(label = label, scale = 0.92f, onClick = onClick),
    contentAlignment = Alignment.Center,
  ) { Icon(icon, contentDescription = label, tint = c.icon, modifier = Modifier.size(20.dp)) }
}

/** ردیفِ کلید (روشن/خاموش) — زدنِ کلِ ردیف هم کلید را می‌زند */
@Composable
fun V3ToggleRow(
  title: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
  subtitle: String? = null,
  icon: ImageVector? = null,
  hue: V3Hue? = null,
  enabled: Boolean = true,
) {
  V3ListRow(
    title = title,
    modifier = modifier,
    subtitle = subtitle,
    icon = icon,
    hue = hue,
    chevron = false,
    trailing = { V3Toggle(checked, title, if (enabled) onCheckedChange else null) },
    onClick = if (enabled) ({ onCheckedChange(!checked) }) else null,
  )
}

/** ردیفِ انتخاب در فرم: برچسب، مقدارِ فعلی (یا راهنما) و فلش؛ زدن ⇐ برگهٔ انتخاب */
@Composable
fun V3PickerRow(
  label: String,
  value: String?,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  placeholder: String = "",
  icon: ImageVector? = null,
  error: String? = null,
) {
  val c = PhoneV3.colors
  Column(modifier) {
    V3ListRow(
      title = label,
      icon = icon,
      value = value?.takeIf { it.isNotBlank() } ?: placeholder,
      valueColor = if (value.isNullOrBlank()) c.inkMuted else PhoneV3Roles.brandText(c),
      onClick = onClick,
    )
    if (error != null) {
      Text(error, style = V3Type.footnote, color = PhoneV3Roles.errorText(c), modifier = Modifier.padding(start = 16.dp, bottom = 6.dp))
    }
  }
}

/**
 *  کادرِ تاریخ (`yyyy-MM-dd`ِ دفتر): نوشتنِ دستی، دو تراشهٔ «امروز/دیروز»، و تاریخِ
 *  خوانا زیرش (`formatDate`، همان تقویمِ برنامه).
 */
@Composable
fun V3DateField(
  value: String,
  onValueChange: (String) -> Unit,
  label: String,
  modifier: Modifier = Modifier,
  error: String? = null,
) {
  Column(modifier) {
    V3TextField(
      value = value,
      onValueChange = onValueChange,
      label = label,
      kind = V3FieldKind.DATE,
      placeholder = todayIso(),
      helper = value.takeIf { it.length >= 8 }?.let(::formatDate),
      error = error,
    )
    Spacer(Modifier.height(8.dp))
    Row {
      V3Chip(Str.today, selected = value == todayIso(), onClick = { onValueChange(todayIso()) })
      Spacer(Modifier.width(8.dp))
      val yesterday = yesterdayIso()
      V3Chip(StrKit.yesterday, selected = value == yesterday, onClick = { onValueChange(yesterday) })
    }
  }
}

private fun yesterdayIso(): String {
  val c = java.util.Calendar.getInstance()
  c.add(java.util.Calendar.DAY_OF_MONTH, -1)
  return "%04d-%02d-%02d".format(c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH) + 1, c.get(java.util.Calendar.DAY_OF_MONTH))
}
