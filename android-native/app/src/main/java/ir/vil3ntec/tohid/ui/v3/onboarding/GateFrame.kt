package ir.vil3ntec.tohid.ui.v3.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrAuth
import ir.vil3ntec.tohid.ui.screens.Motion
import ir.vil3ntec.tohid.ui.screens.frosted
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Palette
import ir.vil3ntec.tohid.ui.v3.V3BackButton
import ir.vil3ntec.tohid.ui.v3.V3Card
import ir.vil3ntec.tohid.ui.v3.V3Springs
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.rememberV3Stagger
import ir.vil3ntec.tohid.ui.v3.v3Stagger

/** پهنای کارتِ دروازه روی تبلت و کامپیوتر (روی گوشی تمام‌عرض با حاشیهٔ ۲۰) */
internal val GATE_WIDTH = 440.dp

/** گوشهٔ کارتِ دروازه و کارت‌های گزینه — همان خانوادهٔ کارت‌های برنامه (THEME-V2: ۲۰) */
internal val GATE_CARD_RADIUS = PhoneV3.groupRadius

/** زمینهٔ مه‌گرفتهٔ همهٔ دروازه‌ها (زبانِ r4، رنگِ تمِ جاری) — ثابت می‌ماند و فقط محتوا جابه‌جا می‌شود */
@Composable
internal fun GateBackdrop(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
  Box(modifier.fillMaxSize().frosted(PhoneV3.colors), content = content)
}

/**
 *  قابِ یک دروازه (ورود، ساختِ دکان، قفل): نشانِ برنامه، عنوانِ درشتِ ۳۰ و زیرعنوانِ ۱۵ از
 *  آغاز، و محتوا در **یک** کارتِ ۱۶ با پهنای حداکثرِ ۴۴۰ که روی تبلت و کامپیوتر وسط می‌نشیند.
 *  صفحه با صفحه‌کلید بالا می‌رود (کادرِ فعال و دکمه بالای آن می‌مانند). بخش‌ها بارِ اول پلکانی می‌آیند.
 */
@Composable
internal fun GateFrame(
  title: String,
  subtitle: String?,
  onBack: (() -> Unit)?,
  modifier: Modifier = Modifier,
  stage: Int? = null,
  cardPadding: PaddingValues = PaddingValues(20.dp),
  /** true ⇒ محتوا بی کارتِ دربرگیرنده (صفحه کارت‌های خودش را دارد، مثلِ دو گزینهٔ «ساختِ دکان») */
  bare: Boolean = false,
  above: (@Composable ColumnScope.() -> Unit)? = null,
  footer: (@Composable ColumnScope.() -> Unit)? = null,
  card: @Composable ColumnScope.() -> Unit,
) {
  val c = PhoneV3.colors
  val stagger = rememberV3Stagger(title)
  Box(
    modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding().verticalScroll(rememberScrollState()),
    contentAlignment = Alignment.TopCenter,
  ) {
    Column(Modifier.widthIn(max = GATE_WIDTH).fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
      Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) V3BackButton(onBack)
      }
      Column(Modifier.v3Stagger(stagger, "head", 0)) {
        BrandMark()
        Spacer(Modifier.height(18.dp))
        Text(title, style = V3Type.largeTitle, color = c.ink)
        if (subtitle != null) {
          Spacer(Modifier.height(6.dp))
          Text(subtitle, style = V3Type.body, color = c.inkMuted)
        }
        if (stage != null) {
          Spacer(Modifier.height(16.dp))
          SignupProgress(stage)
        }
      }
      if (above != null) {
        Spacer(Modifier.height(20.dp))
        Column(Modifier.v3Stagger(stagger, "above", 1), content = above)
      }
      Spacer(Modifier.height(20.dp))
      if (bare) {
        Column(Modifier.fillMaxWidth().v3Stagger(stagger, "card", 2), content = card)
      } else {
        V3Card(
          Modifier.v3Stagger(stagger, "card", 2),
          radius = GATE_CARD_RADIUS,
          padding = cardPadding,
          content = card,
        )
      }
      if (footer != null) {
        Spacer(Modifier.height(14.dp))
        Column(Modifier.fillMaxWidth().v3Stagger(stagger, "footer", 3), horizontalAlignment = Alignment.CenterHorizontally, content = footer)
      }
      Spacer(Modifier.height(24.dp))
    }
  }
}

/**
 *  «۱ از ۳ · ایمیل و رمز» و نوارِ نازکی که از گامِ پیش تا همین گام پر می‌شود (فنرِ کیت؛
 *  بی‌حرکت ⇐ بی‌درنگ). رنگ: primaryِ تم روی خطِ مرز.
 */
@Composable
internal fun SignupProgress(stage: Int) {
  val c = PhoneV3.colors
  val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
  val total = AuthRules.SIGNUP_STAGES
  val fill = remember(stage) { Animatable(if (Motion.enabled) (stage - 1f) / total else stage.toFloat() / total) }
  LaunchedEffect(stage) { fill.animateTo(stage.toFloat() / total, V3Springs.ui()) }
  val label = when (stage) {
    1 -> StrAuth.stepEmail
    2 -> StrAuth.stepCode
    else -> StrAuth.stepLocation
  }
  Column(Modifier.fillMaxWidth()) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      Text(StrAuth.stepOf(stage, total), style = V3Type.subhead, color = PhoneV3Palette.glyph(c, PhoneV3Palette.Hue.BRAND))
      Text("·", style = V3Type.subhead, color = c.inkMuted)
      Text(label, style = V3Type.subhead, color = c.inkMuted)
    }
    Spacer(Modifier.height(8.dp))
    Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(c.hairline)) {
      Box(
        Modifier
          .fillMaxHeight()
          .fillMaxWidth()
          .graphicsLayer {
            scaleX = fill.value
            transformOrigin = TransformOrigin(if (rtl) 1f else 0f, 0.5f)
          }
          .clip(RoundedCornerShape(2.dp))
          .background(PhoneV3Palette.glyph(c, PhoneV3Palette.Hue.BRAND)),
      )
    }
  }
}

/** فاصلهٔ استانداردِ میانِ کادرهای یک کارت */
@Composable
internal fun FieldGap() = Spacer(Modifier.height(14.dp))
