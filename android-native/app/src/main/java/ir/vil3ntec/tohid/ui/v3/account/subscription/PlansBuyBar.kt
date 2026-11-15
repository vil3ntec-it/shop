package ir.vil3ntec.tohid.ui.v3.account.subscription

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.ui.screens.Motion
import ir.vil3ntec.tohid.ui.screens.MotionTokens
import ir.vil3ntec.tohid.ui.screens.v3Text
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Space
import ir.vil3ntec.tohid.ui.v3.LocalV3SystemBottom
import ir.vil3ntec.tohid.ui.v3.v3Pressable

/** نوشتهٔ دکمهٔ خرید و قیمتش (جهتِ لغزش: گران‌تر از پایین می‌آید، ارزان‌تر از بالا) */
private data class BuyLabel(val text: String, val price: Long)

/**
 *  نوارِ چسبانِ «گرفتن اشتراک …» — همان پوسته و جای `V3FormBar` (زمینه، خطِ مویی، پانویس، بالای صفحه‌کلید و نوارِ
 *  سیستم) و همان رنگ و اندازهٔ دکمهٔ اصلیِ کیت؛ فقط نوشتهٔ دکمه با عوض شدنِ پلن نرم جابه‌جا می‌شود (لغزشِ عمودیِ
 *  فنری و محو). زیرِ «کاهشِ حرکت» نوشتهٔ تازه بی‌درنگ می‌نشیند.
 */
@Composable
internal fun PlansBuyBar(label: String, price: Long, onBuy: () -> Unit, summary: String) {
  val c = PhoneV3.colors
  Column(
    Modifier
      .fillMaxWidth()
      .background(c.bg)
      .windowInsetsPadding(if (LocalV3SystemBottom.current != null) WindowInsets.ime.exclude(WindowInsets.navigationBars) else WindowInsets.ime.union(WindowInsets.navigationBars)),
  ) {
    Box(Modifier.fillMaxWidth().height(1.dp).background(c.hairline))
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 12.dp)) {
      Text(
        summary,
        style = v3Text(12, FontWeight.Medium, 17),
        color = c.inkMuted,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
      )
      BuyButton(BuyLabel(label, price), onBuy)
    }
  }
}

@Composable
private fun BuyButton(label: BuyLabel, onBuy: () -> Unit) {
  val pair = PhoneV3Roles.button(PhoneV3.colors, V3ButtonKind.PRIMARY)
  val shape = remember { RoundedCornerShape(V3Space.controlRadius) }
  Row(
    Modifier
      .fillMaxWidth()
      .testTag(PlansTags.BUY)
      .v3Pressable(label = label.text, role = Role.Button, haptic = true, onClick = onBuy)
      .heightIn(min = V3Space.control)
      .clip(shape)
      .background(pair.bg)
      .padding(horizontal = 18.dp),
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (!Motion.enabled) {
      BuyText(label.text, pair.fg)
    } else {
      AnimatedContent(
        label,
        transitionSpec = {
          val dir = if (targetState.price >= initialState.price) 1 else -1
          val slide = spring(dampingRatio = 0.86f, stiffness = 520f, visibilityThreshold = IntOffset.VisibilityThreshold)
          (slideInVertically(slide) { h -> dir * h / 2 } + fadeIn(tween(MotionTokens.MEDIUM))) togetherWith
            (slideOutVertically(slide) { h -> -dir * h / 2 } + fadeOut(tween(MotionTokens.SHORT))) using SizeTransform(clip = false)
        },
        contentKey = { it.text },
        contentAlignment = Alignment.Center,
        label = "plansBuyLabel",
      ) { BuyText(it.text, pair.fg) }
    }
  }
}

@Composable
private fun BuyText(text: String, color: Color) {
  Text(text, style = v3Text(15, FontWeight.SemiBold, 22), color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
}
