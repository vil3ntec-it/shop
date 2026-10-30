package ir.vil3ntec.tohid.ui.courier

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.vil3ntec.tohid.ui.components.common.OrderStatusTone
import ir.vil3ntec.tohid.ui.components.common.PillTone
import ir.vil3ntec.tohid.ui.screens.Motion
import ir.vil3ntec.tohid.ui.theme.DarkColors
import ir.vil3ntec.tohid.ui.theme.LocalShopColors
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Colors
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Status
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3IconButton
import ir.vil3ntec.tohid.ui.v3.V3StatusPill
import ir.vil3ntec.tohid.ui.v3.V3TextButton

/*
 *  تکه‌های ظاهرِ حالتِ پیک: شکل از گوشیِ نسخهٔ ۳ و مرجعِ پیکِ صاحب محصول
 *  (design-ref/vill3n/courier-ref.jpg)، رنگ فقط از سیستمِ رنگِ VILL3N
 *  (`PhoneV3.colors` و `PhoneV3.ui`): زمینهٔ روشن، کارتِ سفید با مرزِ دیدنی و سایهٔ
 *  سبک، سطحِ تیرهٔ سرمه‌ای. هر حرکت با `Motion.enabled` خاموش.
 *
 *  تکه‌های عمومی (دکمه، دکمهٔ گرد، قرصِ وضعیت، زبانه، کادر، جست‌وجو، برگه، پیام) حالا
 *  همان کیتِ نسخهٔ ۳ است (`V3Button`، `V3IconButton`، `V3StatusPill`، `V3Segmented`،
 *  `V3TextField`، `V3SearchField`، `V3Sheet`، `V3Message`، `v3Stagger`، `v3Pressable`).
 *  این‌جا فقط چیزی می‌ماند که کیت ندارد یا خاصِ پیک است: سطحِ سرمه‌ای و کیتِ رویش
 *  (`CourierOnDark`)، دکمهٔ پله که برچسبش می‌لغزد، به‌روز کردن با حلقهٔ کار، برچسبِ
 *  اطلاعات، سرخطِ بخش و عددِ بزرگِ برگه.
 */

internal val CourierPillShape = RoundedCornerShape(percent = 50)
internal val CourierCardShape = RoundedCornerShape(20.dp)
internal val CourierGroupShape = RoundedCornerShape(16.dp)

/** مدتِ حرکت — صفر وقتی کاربر یا گوشی حرکت را خاموش کرده */
internal fun motionMs(ms: Int): Int = if (Motion.enabled) ms else 0

/** متنِ برنامه با همان قلمِ فارسیِ تم، فقط اندازه و وزن عوض می‌شود */
@Composable
internal fun courierText(size: TextUnit, weight: FontWeight = FontWeight.Normal): TextStyle =
  MaterialTheme.typography.bodyLarge.copy(
    fontSize = size,
    fontWeight = weight,
    lineHeight = size * 1.45f,
    letterSpacing = 0.sp,
  )

/** کارت: رنگ + مرزِ دیدنی + سایهٔ سبک (سیستمِ رنگ §۶: سایه هرگز جای مرز نیست) */
internal fun Modifier.courierCard(p: PhoneV3Colors, shape: Shape = CourierCardShape, elevation: Dp = 4.dp): Modifier =
  this
    .shadow(elevation, shape, clip = false, ambientColor = p.black.copy(alpha = 0.08f), spotColor = p.black.copy(alpha = 0.08f))
    .background(p.card, shape)
    .border(1.dp, p.hairline, shape)

/** سطحِ تیرهٔ سرمه‌ای (کارتِ «مأموریتِ جاری»، برگهٔ پیگیری) */
internal fun Modifier.courierDark(p: PhoneV3Colors, shape: Shape = CourierCardShape): Modifier =
  this.background(p.black, shape).border(1.dp, p.blackRaised, shape)

/**
 *  کیت روی سطحِ سرمه‌ای: سطح در هر دو تم تیره است، پس دکمه و قرص و زبانهٔ کیت که رویش
 *  می‌نشینند رنگِ شبِ همان توکن‌ها را می‌گیرند — همان `PhoneV3UiDark` که این سطح از پیش
 *  می‌خواند (دکمهٔ اصلیِ روشن‌تر، سطحِ برآمده، قرصِ شب).
 */
@Composable
internal fun CourierOnDark(content: @Composable () -> Unit) {
  CompositionLocalProvider(LocalShopColors provides DarkColors, content = content)
}

/** وضعیتِ مأموریت ⇐ وضعیتِ کیت — همان نگاشتِ یگانهٔ `OrderStatusTone.courier` (میزِ تحویل هم همین را می‌خواند) */
internal fun courierStatus(s: String): V3Status = when (OrderStatusTone.courier(s)) {
  PillTone.SUCCESS -> V3Status.DELIVERED
  PillTone.INFO -> V3Status.IN_DELIVERY
  PillTone.AWAITING -> V3Status.AWAITING
  PillTone.WARNING -> V3Status.WARNING
  PillTone.DANGER -> V3Status.CANCELLED
  PillTone.REVIEW -> V3Status.REVIEW
  PillTone.PREPARING -> V3Status.PREPARING
  PillTone.NEUTRAL -> V3Status.INACTIVE
}

/** قرصِ وضعیتِ مأموریت (کیت): برچسبِ پیک + رنگِ یگانه؛ با عوض شدنِ پله نرم جابه‌جا می‌شود */
@Composable
internal fun MissionStatus(status: String, modifier: Modifier = Modifier) {
  Crossfade(targetState = status, modifier = modifier, animationSpec = tween(motionMs(220)), label = "missionStatus") { s ->
    V3StatusPill(courierStatus(s), STATUS_LABEL[s] ?: s)
  }
}

/** «در صف»: کارِ همین گوشی که هنوز به دکان نرسیده — هشدار (کهربایی)، نه «در انتظارِ تأیید» */
@Composable
internal fun QueuedPill(modifier: Modifier = Modifier) {
  V3StatusPill(V3Status.WARNING, "در صف", modifier)
}

/**
 *  دکمهٔ هر پله — دکمهٔ کیت به بلندیِ ۵۶dp (زیرِ شست)؛ وقتی پله عوض می‌شود برچسب و دکمه
 *  نرم از پایین می‌آیند (online.md: «courier action button morph between states»).
 */
@Composable
internal fun StepButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  kind: V3ButtonKind = V3ButtonKind.PRIMARY,
  icon: ImageVector? = null,
  enabled: Boolean = true,
  loading: Boolean = false,
) {
  AnimatedContent(
    targetState = text,
    modifier = modifier,
    transitionSpec = {
      if (Motion.enabled) {
        (slideInVertically(tween(260)) { it / 2 } + fadeIn(tween(260))) togetherWith
          (slideOutVertically(tween(200)) { -it / 2 } + fadeOut(tween(200)))
      } else EnterTransition.None togetherWith ExitTransition.None
    },
    label = "stepButton",
  ) { label ->
    V3Button(label, onClick, Modifier.fillMaxWidth(), kind = kind, icon = icon, enabled = enabled, loading = loading, height = 56.dp)
  }
}

/** دکمهٔ دوم (خنثی) به بلندیِ ۵۲dp؛ کارِ منفی (رد، ناموفق، خروج) گونهٔ «خطر» ِ کیت */
@Composable
internal fun CourierSecondary(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  icon: ImageVector? = null,
  negative: Boolean = false,
  height: Dp = 52.dp,
) {
  V3Button(text, onClick, modifier, kind = if (negative) V3ButtonKind.DANGER else V3ButtonKind.NEUTRAL, icon = icon, height = height)
}

/** «به‌روز کردن» (دکمهٔ گردِ کیت): هنگامِ فرستادن حلقهٔ کار دورش می‌چرخد و زدنِ دوباره بی‌اثر است */
@Composable
internal fun RefreshButton(syncing: Boolean, onRefresh: () -> Unit) {
  Box(contentAlignment = Alignment.Center) {
    V3IconButton(Icons.Outlined.Refresh, "به‌روز کردن", onClick = { if (!syncing) onRefresh() })
    if (syncing) {
      CircularProgressIndicator(Modifier.size(44.dp), color = PhoneV3Roles.brandText(PhoneV3.colors), strokeWidth = 2.dp)
    }
  }
}

/**
 *  برچسبِ اطلاعات با آیکنِ خطی (نقد، کدِ تحویل، …) روی سطحِ درونی — کیت هنوز برچسبِ
 *  ایستا ندارد (`DraftTag` ِ گروهِ آنلاین همین است)؛ تا آن‌وقت همین‌جا می‌ماند.
 */
@Composable
internal fun CourierInfoChip(icon: ImageVector, text: String, positive: Boolean = false) {
  val p = PhoneV3.colors
  val ui = PhoneV3.ui
  val fg = if (positive) ui.success.fg else p.ink
  Row(
    Modifier.background(if (positive) ui.success.bg else p.tile, CourierPillShape)
      .border(1.dp, if (positive) ui.success.bg else p.hairline, CourierPillShape)
      .padding(horizontal = 10.dp, vertical = 5.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(icon, contentDescription = null, tint = if (positive) ui.success.fg else p.icon, modifier = Modifier.size(16.dp))
    Spacer(Modifier.width(6.dp))
    Text(text, style = courierText(13.sp, FontWeight.Medium), color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
  }
}

/** سرخطِ بخش (مثلِ «Current Tracking» ِ مرجع) با پیوندِ اختیاریِ «همه» (دکمهٔ متنیِ کیت) */
@Composable
internal fun CourierSectionHead(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
  val p = PhoneV3.colors
  Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
    Text(title, style = courierText(17.sp, FontWeight.Bold), color = p.ink, modifier = Modifier.weight(1f))
    if (action != null && onAction != null) V3TextButton(action, onAction)
  }
}

/** زیرعنوانِ برگه (عنوان خودِ `V3Sheet` است) */
@Composable
internal fun CourierSheetSub(text: String) {
  Text(text, style = courierText(14.sp), color = PhoneV3.colors.inkMuted)
}

/** عددِ وسطِ برگه (مبلغِ نقد) — بزرگ و پُررنگ */
@Composable
internal fun CourierBigAmount(label: String, amount: String) {
  val p = PhoneV3.colors
  Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
    Text(label, style = courierText(14.sp), color = p.inkMuted, textAlign = TextAlign.Center)
    Spacer(Modifier.height(2.dp))
    Text(amount, style = courierText(34.sp, FontWeight.Bold), color = p.ink, textAlign = TextAlign.Center, maxLines = 1)
  }
}
