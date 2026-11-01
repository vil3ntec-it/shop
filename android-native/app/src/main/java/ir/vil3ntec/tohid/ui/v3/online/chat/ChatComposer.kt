package ir.vil3ntec.tohid.ui.v3.online.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrOnline
import ir.vil3ntec.tohid.ui.screens.MotionSpec
import ir.vil3ntec.tohid.ui.screens.MotionTokens
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.v3.LocalV3SystemBottom
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.v3Pressable

/**
 *  نوارِ نوشتن: پیوستِ عکس، کادرِ «پیام…» (تا چهار خط)، و دکمهٔ گردِ «فرستادن» — وقتی کادر خالی است
 *  جایش دکمهٔ صدا (`mic`، نگه‌داشتن ⇐ ضبط) با جابه‌جاییِ نرم می‌نشیند. `status` ردیفِ ضبط/آمادهٔ
 *  فرستادنِ صداست. بالای صفحه‌کلید و نوارِ سیستم.
 */
@Composable
fun ChatComposer(
  draft: String,
  onDraft: (String) -> Unit,
  enabled: Boolean,
  onSend: () -> Unit,
  onPhoto: () -> Unit,
  mic: (@Composable () -> Unit)?,
  status: (@Composable () -> Unit)?,
) {
  val c = PhoneV3.colors
  Column(
    Modifier
      .fillMaxWidth()
      .background(c.bg)
      .windowInsetsPadding(if (LocalV3SystemBottom.current != null) WindowInsets.ime.exclude(WindowInsets.navigationBars) else WindowInsets.ime.union(WindowInsets.navigationBars)),
  ) {
    Box(Modifier.fillMaxWidth().height(1.dp).background(c.hairline))
    status?.invoke()
    Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      RoundIcon(Icons.Outlined.AddPhotoAlternate, StrOnline.ctSendPhoto, enabled, primary = false, onClick = onPhoto)
      val shape = RoundedCornerShape(22.dp)
      BasicTextField(
        value = draft,
        onValueChange = onDraft,
        enabled = enabled,
        maxLines = 4,
        textStyle = V3Type.body.copy(color = c.ink),
        cursorBrush = SolidColor(PhoneV3Roles.focus(c)),
        modifier = Modifier.weight(1f).semantics { contentDescription = StrOnline.ctMessage },
        decorationBox = { inner ->
          Box(
            Modifier.heightIn(min = 46.dp).clip(shape).background(PhoneV3Roles.inputFill(c)).border(1.dp, PhoneV3Roles.inputBorder(c), shape)
              .padding(horizontal = 14.dp, vertical = 12.dp),
            contentAlignment = Alignment.CenterStart,
          ) {
            if (draft.isEmpty()) Text(StrOnline.ctMessage, style = V3Type.body, color = c.inkMuted)
            inner()
          }
        },
      )
      AnimatedContent(
        draft.isBlank() && mic != null,
        transitionSpec = { (fadeIn(MotionSpec.tweenOf(MotionTokens.SHORT)) + scaleIn(MotionSpec.morph(), initialScale = 0.7f)) togetherWith fadeOut(MotionSpec.tweenOf(90)) },
        label = "sendMorph",
      ) { useMic ->
        if (useMic && mic != null) mic() else RoundIcon(Icons.AutoMirrored.Outlined.Send, StrOnline.ctSend, enabled && draft.isNotBlank(), primary = true, onClick = onSend)
      }
    }
  }
}

/** دکمهٔ گردِ نوار (اصلی = برند، دیگر = خنثی)؛ فشار ۰٫۹۲ و لرزش روی فرستادن */
@Composable
internal fun RoundIcon(icon: ImageVector, label: String, enabled: Boolean, primary: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
  val c = PhoneV3.colors
  val pair = if (!enabled) PhoneV3Roles.disabled(c) else PhoneV3Roles.button(c, if (primary) V3ButtonKind.PRIMARY else V3ButtonKind.NEUTRAL)
  Box(
    modifier.size(46.dp).v3Pressable(enabled = enabled, label = label, scale = 0.92f, haptic = primary, onClick = onClick).clip(CircleShape).background(pair.bg),
    contentAlignment = Alignment.Center,
  ) { Icon(icon, contentDescription = label, tint = pair.fg, modifier = Modifier.size(22.dp)) }
}
