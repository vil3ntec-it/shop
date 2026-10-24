package ir.vil3ntec.tohid.ui.v3.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrAuth
import ir.vil3ntec.tohid.ui.screens.MotionSpec
import ir.vil3ntec.tohid.ui.screens.MotionTokens
import ir.vil3ntec.tohid.ui.screens.v3Text
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3Space
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Collapse
import ir.vil3ntec.tohid.ui.v3.v3Pressable

/**
 *  **Draft** برای کیت (پیشنهادِ ارتقا: `V3TextField(kind = PASSWORD)` با دکمهٔ «نمایش رمز» و
 *  `success`): همان شکلِ `V3TextField` — برچسب بالا، کادرِ ۱۰، حلقهٔ ۲dpِ تمرکز، خطای درجا — به‌علاوهٔ
 *  چشمِ «نمایش رمز / پنهان کردن رمز» (هدفِ لمسیِ ۴۴) و یک خطِ تأییدِ سبز («هر دو یکی‌اند»).
 */
@Composable
internal fun DraftSecretField(
  value: String,
  onValueChange: (String) -> Unit,
  label: String,
  modifier: Modifier = Modifier,
  placeholder: String? = null,
  helper: String? = null,
  error: String? = null,
  success: String? = null,
  enabled: Boolean = true,
  imeAction: ImeAction = ImeAction.Done,
  onImeAction: (() -> Unit)? = null,
) {
  val c = PhoneV3.colors
  var shown by rememberSaveable { mutableStateOf(false) }
  val source = remember { MutableInteractionSource() }
  val focused by source.collectIsFocusedAsState()
  val ring by animateColorAsState(
    when {
      error != null -> PhoneV3Roles.errorText(c)
      focused -> PhoneV3Roles.focus(c)
      else -> PhoneV3Roles.inputBorder(c)
    },
    MotionSpec.tweenOf(MotionTokens.SHORT), label = "secretRing",
  )
  val ringWidth by animateDpAsState(if (focused || error != null) 2.dp else 1.dp, MotionSpec.tweenOf(MotionTokens.SHORT), label = "secretRingW")
  val shape = RoundedCornerShape(V3Space.controlRadius)
  val toggle = if (shown) StrAuth.hidePassword else StrAuth.showPassword
  Column(modifier) {
    Text(
      label,
      style = v3Text(13, FontWeight.SemiBold, 18),
      color = if (focused) PhoneV3Roles.brandText(c) else c.inkMuted,
      modifier = Modifier.padding(start = 2.dp, bottom = 6.dp),
    )
    BasicTextField(
      value = value,
      onValueChange = onValueChange,
      enabled = enabled,
      singleLine = true,
      textStyle = v3Text(16, FontWeight.Medium, 22).copy(color = if (enabled) c.ink else PhoneV3Roles.disabled(c).fg),
      cursorBrush = SolidColor(PhoneV3Roles.focus(c)),
      interactionSource = source,
      visualTransformation = if (shown) VisualTransformation.None else PasswordVisualTransformation(),
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
      keyboardActions = KeyboardActions(onAny = { onImeAction?.invoke() }),
      modifier = Modifier.fillMaxWidth().semantics {
        contentDescription = label
        if (error != null) error(error)
      },
      decorationBox = { inner ->
        Row(
          Modifier
            .fillMaxWidth()
            .heightIn(min = V3Space.control)
            .clip(shape)
            .background(if (enabled) PhoneV3Roles.inputFill(c) else PhoneV3Roles.disabled(c).bg)
            .border(ringWidth, ring, shape)
            .padding(start = 14.dp, end = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Box(Modifier.weight(1f).padding(vertical = 12.dp)) {
            if (value.isEmpty() && placeholder != null) {
              Text(placeholder, style = v3Text(16, FontWeight.Normal, 22), color = c.inkMuted.copy(alpha = 0.75f), maxLines = 1)
            }
            inner()
          }
          Box(
            Modifier.size(44.dp).clip(CircleShape).v3Pressable(label = toggle, scale = 0.92f) { shown = !shown },
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              if (shown) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
              contentDescription = toggle,
              tint = c.icon,
              modifier = Modifier.size(20.dp),
            )
          }
        }
      },
    )
    FieldNote(error = error, success = success, helper = helper)
  }
}

/** خطِ زیرِ کادر: خطا (قرمز با آیکن) ⇐ تأیید (سبز با تیک) ⇐ راهنما؛ آمدن و رفتن نرم */
@Composable
internal fun FieldNote(error: String?, success: String? = null, helper: String? = null) {
  val c = PhoneV3.colors
  val text = error ?: success ?: helper
  V3Collapse(visible = text != null) {
    Row(Modifier.padding(start = 2.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
      val (icon, tint) = when {
        error != null -> Icons.Outlined.ErrorOutline to PhoneV3Roles.errorText(c)
        success != null -> Icons.Outlined.CheckCircle to PhoneV3Roles.toneIcon(c, V3Tone.SUCCESS)
        else -> null to c.inkMuted
      }
      if (icon != null) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(4.dp))
      }
      Text(text.orEmpty(), style = v3Text(12, FontWeight.Medium, 17), color = if (icon == null) c.inkMuted else tint)
    }
  }
}
