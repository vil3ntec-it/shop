package ir.vil3ntec.tohid.ui.v3

import ir.vil3ntec.tohid.core.i18n.StrKit
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.numericInput
import ir.vil3ntec.tohid.normalizeDigits
import ir.vil3ntec.tohid.ui.screens.MotionSpec
import ir.vil3ntec.tohid.ui.screens.MotionTokens
import ir.vil3ntec.tohid.ui.screens.v3Text
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3Space

/** گونهٔ کادر: صفحه‌کلید، پالایشِ نوشته و نمایش */
enum class V3FieldKind {
  TEXT,
  /** عدد با اعشار (مقدار، کمینهٔ موجودی) — رقمِ فارسی لاتین می‌شود */
  NUMBER,
  /** پول: عدد با جداکنندهٔ هزارگان در نمایش و «؋» در پایان؛ مقدارِ خام بی جداکننده */
  MONEY,
  /** بارکد: فقط رقم و حرفِ لاتین، دکمهٔ اسکن در پایان */
  BARCODE,
  PHONE,
  EMAIL,
  /** رمز: نوشته پنهان */
  PASSWORD,
  /** تاریخِ `yyyy-MM-dd` (`V3DateField`) */
  DATE,
}

/**
 *  کادرِ ورودیِ نسخهٔ ۳ — برچسب بالای کادر، کادرِ گرد با مرزِ borderInput، و
 *  زیرش یا راهنما (`helper`) یا خطای درجا (`error`، با آیکن و رنگِ خطا، و برای
 *  صفحه‌خوان). حالت‌ها (§۱۴): عادی، تمرکز (حلقهٔ ۲dpِ برند)، خطا، غیرفعال.
 *  `required` ستارهٔ کوچکِ کنارِ برچسب.
 */
@Composable
fun V3TextField(
  value: String,
  onValueChange: (String) -> Unit,
  label: String,
  modifier: Modifier = Modifier,
  kind: V3FieldKind = V3FieldKind.TEXT,
  placeholder: String? = null,
  helper: String? = null,
  error: String? = null,
  enabled: Boolean = true,
  required: Boolean = false,
  suffix: String? = if (kind == V3FieldKind.MONEY) "؋" else null,
  onScan: (() -> Unit)? = null,
  imeAction: ImeAction = ImeAction.Next,
  onImeAction: (() -> Unit)? = null,
  singleLine: Boolean = true,
) {
  val c = PhoneV3.colors
  val source = remember { MutableInteractionSource() }
  val focused by source.collectIsFocusedAsState()
  val ring = when {
    error != null -> PhoneV3Roles.errorText(c)
    focused -> PhoneV3Roles.focus(c)
    else -> PhoneV3Roles.inputBorder(c)
  }
  val ringColor by animateColorAsState(ring, MotionSpec.tweenOf(MotionTokens.SHORT), label = "ring")
  val ringWidth by animateDpAsState(if (focused || error != null) 2.dp else 1.dp, MotionSpec.tweenOf(MotionTokens.SHORT), label = "ringW")
  val shape = RoundedCornerShape(V3Space.controlRadius)
  val fill = if (enabled) PhoneV3Roles.inputFill(c) else PhoneV3Roles.disabled(c).bg
  val ink = if (enabled) c.ink else PhoneV3Roles.disabled(c).fg

  Column(modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 2.dp, bottom = 6.dp)) {
      Text(
        label,
        style = v3Text(13, FontWeight.SemiBold, 18),
        color = if (focused) PhoneV3Roles.brandText(c) else c.inkMuted,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      if (required) Text(" *", style = v3Text(13, FontWeight.SemiBold, 18), color = PhoneV3Roles.errorText(c))
    }
    BasicTextField(
      value = value,
      onValueChange = { onValueChange(clean(kind, it)) },
      enabled = enabled,
      singleLine = singleLine,
      textStyle = v3Text(16, FontWeight.Medium, 22).copy(color = ink),
      cursorBrush = SolidColor(PhoneV3Roles.focus(c)),
      interactionSource = source,
      visualTransformation = when (kind) {
        V3FieldKind.MONEY -> MoneyMask
        V3FieldKind.PASSWORD -> PasswordVisualTransformation()
        else -> VisualTransformation.None
      },
      keyboardOptions = KeyboardOptions(keyboardType = keyboard(kind), imeAction = imeAction),
      keyboardActions = KeyboardActions(onAny = { onImeAction?.invoke() }),
      modifier = Modifier
        .fillMaxWidth()
        .semantics {
          contentDescription = label
          if (error != null) error(error)
        },
      decorationBox = { inner ->
        Row(
          Modifier
            .fillMaxWidth()
            .heightIn(min = V3Space.control)
            .clip(shape)
            .background(fill)
            .border(ringWidth, ringColor, shape)
            .padding(start = 14.dp, end = if (onScan != null) 6.dp else 14.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Box(Modifier.weight(1f).padding(vertical = 12.dp)) {
            if (value.isEmpty() && placeholder != null) {
              Text(placeholder, style = v3Text(16, FontWeight.Normal, 22), color = c.inkMuted.copy(alpha = 0.75f), maxLines = 1)
            }
            inner()
          }
          if (suffix != null) {
            Spacer(Modifier.width(6.dp))
            Text(suffix, style = v3Text(15, FontWeight.SemiBold, 20), color = c.inkMuted)
          }
          if (onScan != null) {
            Spacer(Modifier.width(6.dp))
            Box(
              Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(PhoneV3Roles.button(c, ir.vil3ntec.tohid.ui.theme.V3ButtonKind.PRIMARY).bg)
                .v3Pressable(label = StrKit.scanBarcode, haptic = true, onClick = onScan),
              contentAlignment = Alignment.Center,
            ) {
              Icon(Icons.Outlined.QrCodeScanner, contentDescription = StrKit.scanBarcode, tint = PhoneV3Roles.button(c, ir.vil3ntec.tohid.ui.theme.V3ButtonKind.PRIMARY).fg, modifier = Modifier.size(20.dp))
            }
          }
        }
      },
    )
    AnimatedContent(
      targetState = error ?: helper,
      transitionSpec = {
        (fadeIn(MotionSpec.tweenOf(MotionTokens.SHORT)) + expandVertically(MotionSpec.tweenOf(MotionTokens.SHORT)))
          .togetherWith(fadeOut(MotionSpec.tweenOf(90)) + shrinkVertically(MotionSpec.tweenOf(MotionTokens.SHORT)))
      },
      label = "fieldNote",
    ) { note ->
      if (note != null) {
        Row(Modifier.padding(start = 2.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
          if (error != null && note == error) {
            Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = PhoneV3Roles.errorText(c), modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(4.dp))
          }
          Text(
            note,
            style = v3Text(12, FontWeight.Medium, 17),
            color = if (error != null && note == error) PhoneV3Roles.errorText(c) else c.inkMuted,
          )
        }
      }
    }
  }
}

private fun keyboard(kind: V3FieldKind) = when (kind) {
  V3FieldKind.NUMBER, V3FieldKind.MONEY -> KeyboardType.Decimal
  V3FieldKind.BARCODE, V3FieldKind.DATE -> KeyboardType.Number
  V3FieldKind.PHONE -> KeyboardType.Phone
  V3FieldKind.EMAIL -> KeyboardType.Email
  V3FieldKind.PASSWORD -> KeyboardType.Password
  V3FieldKind.TEXT -> KeyboardType.Text
}

/** پالایشِ نوشته به گونهٔ کادر — رقمِ فارسی/عربی همیشه لاتین */
internal fun clean(kind: V3FieldKind, raw: String): String = when (kind) {
  V3FieldKind.NUMBER, V3FieldKind.MONEY -> V3Numbers.keepOneDot(numericInput(raw))
  V3FieldKind.BARCODE -> normalizeDigits(raw).filter { it.isLetterOrDigit() && it.code < 128 }
  V3FieldKind.PHONE -> normalizeDigits(raw).filter { it.isDigit() || it == '+' }
  V3FieldKind.DATE -> normalizeDigits(raw).filter { it in '0'..'9' || it == '-' }
  V3FieldKind.EMAIL -> raw.trim()
  V3FieldKind.TEXT, V3FieldKind.PASSWORD -> raw
}

/** کمک‌های خالصِ کادرِ عدد (آزمون: `V3NumbersTest`) */
object V3Numbers {
  /** فقط نخستین نقطه می‌ماند: «12.5.3» ⇐ «12.53» */
  fun keepOneDot(s: String): String {
    val i = s.indexOf('.')
    return if (i < 0) s else s.substring(0, i + 1) + s.substring(i + 1).replace(".", "")
  }

  /** جداکنندهٔ هزارگان فقط در بخشِ صحیح: «1234567.5» ⇐ «1,234,567.5» */
  fun group(raw: String): String {
    val int = raw.substringBefore('.')
    val frac = if ('.' in raw) "." + raw.substringAfter('.') else ""
    val out = StringBuilder()
    int.forEachIndexed { i, ch ->
      if (i > 0 && (int.length - i) % 3 == 0) out.append(',')
      out.append(ch)
    }
    return out.toString() + frac
  }

  /** جای مکان‌نما در نوشتهٔ جداشده، از جای آن در نوشتهٔ خام */
  fun toGrouped(raw: String, offset: Int): Int {
    val g = group(raw)
    if (offset <= 0) return 0
    var seen = 0
    g.forEachIndexed { i, ch ->
      if (ch != ',') {
        seen++
        if (seen == offset) return i + 1
      }
    }
    return g.length
  }

  /** وارونهٔ `toGrouped` */
  fun toRaw(grouped: String, offset: Int): Int = grouped.take(offset).count { it != ',' }
}

/** نمایشِ پول با جداکنندهٔ هزارگان؛ مقدارِ خام دست نمی‌خورد */
private object MoneyMask : VisualTransformation {
  override fun filter(text: AnnotatedString): TransformedText {
    val raw = text.text
    val grouped = V3Numbers.group(raw)
    return TransformedText(
      AnnotatedString(grouped),
      object : OffsetMapping {
        override fun originalToTransformed(offset: Int): Int = V3Numbers.toGrouped(raw, offset).coerceIn(0, grouped.length)
        override fun transformedToOriginal(offset: Int): Int = V3Numbers.toRaw(grouped, offset).coerceIn(0, raw.length)
      },
    )
  }
}
