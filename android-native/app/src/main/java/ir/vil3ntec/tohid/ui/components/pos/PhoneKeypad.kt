package ir.vil3ntec.tohid.ui.components.pos

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backspace
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.qty
import ir.vil3ntec.tohid.ui.screens.Motion
import ir.vil3ntec.tohid.ui.theme.PhoneV3

/**
 *  صفحه‌کلیدِ مقدار/وزنِ یک کالا (نسخهٔ ۳) — برگهٔ پایینِ صندوقِ گوشی.
 *
 *  کالای وزنی (کیلو، لیتر، …) با زدن، و هر کالا با نگه داشتن این‌جا می‌آید: مقدار با
 *  رقم‌ها (یا یکی از مقدارهای آماده) نوشته می‌شود، مبلغِ همان مقدار زیرش دیده می‌شود و
 *  خطا (اعشار برای کالای دانه‌ای، بیش از موجودی) پیش از ثبت گفته می‌شود — نه سرِ ثبتِ
 *  فروش. «ثبت» مقدارِ همان کالا در سبد را **همین عدد** می‌کند (تازه ⇐ افزوده می‌شود).
 */
data class PhoneKeypadRequest(
  val id: String,
  val name: String,
  val unit: String,
  val price: Double,
  /** وزنی/پیمانه‌ای ⇐ اعشار و مقدارهای آمادهٔ ۰٫۲۵ تا ۲ */
  val weighed: Boolean,
  /** مقدارِ کنونی در سبد (۰ ⇐ هنوز نیست) */
  val inCart: Double,
)

/**
 *  باید آخرین فرزندِ `Box`ِ تمام‌صفحه باشد تا روی همه بنشیند.
 *  @param problem خطای این مقدار برای همین کالا (null ⇐ درست)
 */
@Composable
fun BoxScope.PhoneQtyKeypad(
  request: PhoneKeypadRequest?,
  problem: (PhoneKeypadRequest, Double) -> String?,
  onDismiss: () -> Unit,
  onConfirm: (PhoneKeypadRequest, Double) -> Unit,
) {
  val v = PhoneV3.colors
  val motion = Motion.enabled
  //  برای انیمیشنِ بسته شدن، آخرین درخواست می‌ماند
  val last = remember { mutableStateOf(request) }
  SideEffect { if (request != null) last.value = request }
  val shown = request ?: last.value
  BackHandler(enabled = request != null, onBack = onDismiss)

  AnimatedVisibility(
    visible = request != null,
    enter = fadeIn(if (motion) tween(180) else snap()),
    exit = fadeOut(if (motion) tween(160) else snap()),
    modifier = Modifier.matchParentSize(),
  ) {
    Box(
      Modifier
        .fillMaxSize()
        .background(PhoneV3.colors.black.copy(alpha = 0.5f))
        .clickable(
          interactionSource = remember { MutableInteractionSource() },
          indication = null,
          onClickLabel = "بستن",
          onClick = onDismiss,
        ),
    )
  }
  AnimatedVisibility(
    visible = request != null,
    enter = if (motion) slideInVertically(spring(dampingRatio = 0.86f, stiffness = 420f)) { it } else fadeIn(snap()),
    exit = if (motion) slideOutVertically(tween(200)) { it } else fadeOut(snap()),
    modifier = Modifier.align(Alignment.BottomCenter),
  ) {
    val req = shown ?: return@AnimatedVisibility
    KeypadSheet(req, problem, onDismiss, onConfirm)
  }
}

@Composable
private fun KeypadSheet(
  req: PhoneKeypadRequest,
  problem: (PhoneKeypadRequest, Double) -> String?,
  onDismiss: () -> Unit,
  onConfirm: (PhoneKeypadRequest, Double) -> Unit,
) {
  val v = PhoneV3.colors
  //  پوسته‌ای که خودش نوارِ سیستم را گرفته `LocalV3SystemBottom = 0.dp` می‌دهد (بی دوباره‌شماری)
  val navBottom = ir.vil3ntec.tohid.ui.v3.LocalV3SystemBottom.current ?: WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
  var text by remember(req.id) { mutableStateOf(if (req.inCart > 0) qty(req.inCart) else "") }
  val value = KeypadInput.value(text)
  val error = if (value > 0) problem(req, value) else null
  val ok = error == null && (value > 0 || req.inCart > 0)
  val unit = req.unit.ifBlank { "عدد" }

  Column(
    Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
      .background(v.card)
      //  زدن روی خودِ برگه آن را نمی‌بندد
      .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
      .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 16.dp + navBottom),
  ) {
    Box(Modifier.align(Alignment.CenterHorizontally).size(width = 36.dp, height = 4.dp).clip(CircleShape).background(v.hairline))
    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Text(
          req.name,
          color = v.ink,
          fontSize = 16.sp,
          fontWeight = FontWeight.Bold,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.semantics { heading() },
        )
        Text("${PhoneSaleText.amount(req.price)} / $unit", color = v.inkMuted, fontSize = 13.sp, maxLines = 1)
      }
      Box(
        Modifier.size(40.dp).clip(CircleShape).background(v.bg).clickable(role = Role.Button, onClick = onDismiss)
          .semantics { contentDescription = "بستن" },
        contentAlignment = Alignment.Center,
      ) {
        Icon(Icons.Outlined.Close, contentDescription = null, tint = v.icon, modifier = Modifier.size(20.dp))
      }
    }
    Spacer(Modifier.height(12.dp))

    //  مقدار و مبلغ
    Row(
      Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
      horizontalArrangement = Arrangement.Center,
      verticalAlignment = Alignment.Bottom,
    ) {
      Text(text.ifEmpty { "0" }, color = v.ink, fontSize = 40.sp, fontWeight = FontWeight.Bold, maxLines = 1)
      Spacer(Modifier.width(8.dp))
      Text(unit, color = v.inkMuted, fontSize = 16.sp, maxLines = 1, modifier = Modifier.padding(bottom = 8.dp))
    }
    Text(
      error ?: "= ${PhoneSaleText.amount(req.price * value)}",
      color = if (error != null) v.negative else v.inkMuted,
      fontSize = 13.sp,
      maxLines = 2,
      modifier = Modifier.align(Alignment.CenterHorizontally),
    )
    Spacer(Modifier.height(12.dp))

    //  مقدارهای آماده
    val presets = if (req.weighed) listOf("0.25", "0.5", "1", "2") else listOf("1", "2", "5", "10")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      presets.forEach { p ->
        val on = text == p
        Box(
          Modifier
            .weight(1f)
            .height(36.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(if (on) v.accent else v.bg)
            .clickable(role = Role.Button) { text = p },
          contentAlignment = Alignment.Center,
        ) {
          Text(qty(p.toDouble()), color = if (on) v.onAccent else v.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
      }
    }
    Spacer(Modifier.height(12.dp))

    //  رقم‌ها — مثلِ همهٔ صفحه‌کلیدهای گوشی، چپ‌به‌راست
    val rows = listOf("123", "456", "789", (if (req.weighed) "." else " ") + "0" + KeypadInput.BACKSPACE)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { k ->
              Box(
                Modifier
                  .weight(1f)
                  .height(52.dp)
                  .clip(RoundedCornerShape(16.dp))
                  .then(if (k == ' ') Modifier else Modifier.background(v.bg).clickable(role = Role.Button) {
                    text = KeypadInput.press(text, k, req.weighed)
                  })
                  .semantics { if (k == KeypadInput.BACKSPACE) contentDescription = "پاک کردن" },
                contentAlignment = Alignment.Center,
              ) {
                when (k) {
                  ' ' -> Unit
                  KeypadInput.BACKSPACE -> Icon(Icons.Outlined.Backspace, contentDescription = null, tint = v.icon, modifier = Modifier.size(22.dp))
                  else -> Text(k.toString(), color = v.ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                }
              }
            }
          }
        }
      }
    }
    Spacer(Modifier.height(14.dp))
    PhoneCartPay(
      label = when {
        req.inCart > 0 && value <= 0 -> "برداشتن از سبد"
        req.inCart > 0 -> "ثبتِ مقدار"
        else -> "افزودن به سبد"
      },
      onClick = { if (ok) onConfirm(req, value) },
      enabled = ok,
    )
  }
}
