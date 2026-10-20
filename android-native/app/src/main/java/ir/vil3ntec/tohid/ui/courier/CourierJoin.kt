package ir.vil3ntec.tohid.ui.courier

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.vil3ntec.tohid.ui.screens.Motion
import ir.vil3ntec.tohid.ui.theme.PhoneV3

/**
 *  «ورودِ پیک» — DLV-01، v3 §۳.۱۱: ورود با کدِ شاگرد و نقشِ «پیک».
 *
 *  فقط ظاهر است؛ منطقِ پیوستن همان «کد شاگرد دارم»ِ صفحهٔ خوش‌آمد است
 *  (`WelcomeScreen` همان تابع را به `onJoin` می‌دهد). سرور از روی کد نقشِ
 *  «پیک» و توکنِ دامنه‌دار را می‌دهد و برنامه خودش به حالتِ پیک می‌رود.
 */
@Composable
internal fun CourierJoinScreen(
  name: String,
  onName: (String) -> Unit,
  code: String,
  onCode: (String) -> Unit,
  busy: Boolean,
  error: String?,
  onJoin: () -> Unit,
  onBack: () -> Unit,
) {
  val p = PhoneV3.colors
  androidx.activity.compose.BackHandler(onBack = onBack)
  //  کارت‌ها یکی‌یکی کمی بالا می‌آیند (بخشِ G)
  val rise = remember { Animatable(if (Motion.enabled) 0f else 1f) }
  LaunchedEffect(Unit) { rise.animateTo(1f, tween(360)) }
  Column(
    Modifier.fillMaxSize().background(p.bg).statusBarsPadding().navigationBarsPadding().imePadding(),
  ) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
      CourierRoundButton(Icons.AutoMirrored.Outlined.ArrowBack, "بازگشت", onBack)
    }
    Column(
      Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
        .graphicsLayer { alpha = rise.value; translationY = (1f - rise.value) * 16.dp.toPx() },
    ) {
      Spacer(Modifier.height(8.dp))
      Box(Modifier.size(72.dp).courierCard(p, CircleShape, elevation = 8.dp), contentAlignment = Alignment.Center) {
        Icon(Icons.Outlined.LocalShipping, contentDescription = null, tint = p.icon, modifier = Modifier.size(32.dp))
      }
      Spacer(Modifier.height(18.dp))
      Text("ورودِ پیک", style = courierText(30.sp, FontWeight.Bold), color = p.ink)
      Spacer(Modifier.height(6.dp))
      Text(
        "کدِ دعوتی را که صاحب دکان به شما داده بزنید. ایمیل و رمز لازم نیست.",
        style = courierText(15.sp), color = p.inkMuted,
      )
      Spacer(Modifier.height(20.dp))
      Column(Modifier.fillMaxWidth().courierCard(p).padding(18.dp)) {
        CourierField(name, onName, "نام شما", placeholder = "مثلاً کریم")
        Spacer(Modifier.height(14.dp))
        CourierField(
          code, onCode, "کدِ دعوت", placeholder = "SHG-XXXX-XXXX-XXXX", ltr = true,
          capitalization = KeyboardCapitalization.Characters,
        )
        if (!error.isNullOrBlank()) {
          Spacer(Modifier.height(10.dp))
          Text(error, style = courierText(14.sp, FontWeight.Medium), color = p.negative)
        }
      }
      Spacer(Modifier.height(16.dp))
      Column(Modifier.fillMaxWidth().courierCard(p, CourierGroupShape, elevation = 6.dp).padding(horizontal = 16.dp, vertical = 6.dp)) {
        JoinPoint(Icons.Outlined.Place, "فقط مأموریت‌های خودتان، با نشانه و نشانی", divider = true)
        JoinPoint(Icons.Outlined.CloudOff, "بی اینترنت هم ثبت می‌شود و بعد خودش می‌رود", divider = true)
        JoinPoint(Icons.Outlined.Payments, "نقدِ هر سفارش جدا ثبت می‌شود", divider = false)
      }
      Spacer(Modifier.height(20.dp))
    }
    Box(Modifier.fillMaxWidth().padding(16.dp)) {
      CourierPrimaryButton(
        "پیوستن", onJoin, Modifier.fillMaxWidth(),
        enabled = code.isNotBlank(), busy = busy,
      )
    }
  }
}

@Composable
private fun JoinPoint(icon: ImageVector, text: String, divider: Boolean) {
  val p = PhoneV3.colors
  Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
    Icon(icon, contentDescription = null, tint = p.icon, modifier = Modifier.size(22.dp))
    Spacer(Modifier.width(12.dp))
    Text(text, style = courierText(14.sp), color = p.ink, modifier = Modifier.weight(1f))
  }
  if (divider) Box(Modifier.fillMaxWidth().padding(start = 34.dp).height(1.dp).background(p.hairline))
}
