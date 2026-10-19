package ir.vil3ntec.tohid.ui.components.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.ui.screens.Motion
import ir.vil3ntec.tohid.ui.theme.PhoneSheet
import ir.vil3ntec.tohid.ui.theme.Shop

/** یک تبِ نوارِ پایینِ گوشی */
data class NavTab(val id: String, val label: String, val icon: ImageVector)

/**
 *  نوارِ پایینِ گوشی — قرصِ شناور با تبِ فعالِ تیره (تصویرِ مرجعِ ۳، phone-ui).
 *
 *  تبِ فعال یک قرصِ پُر است با آیکن و برچسب؛ بقیه فقط آیکن‌اند و برچسبشان نامِ
 *  صفحه‌خوانِ همان آیکن است. قرص با «کاهشِ حرکت» (`Motion.enabled`) بی‌انیمیشن
 *  جابه‌جا می‌شود. هر تب ≥ ۴۸dp است (۵.۳) و نقشِ «زبانه» دارد.
 *
 *  فقط گوشی و تبلت این را می‌بینند: `AppRoot` روی کامپیوتر و صفحهٔ پهن منوی
 *  کناری می‌گذارد. ترتیب و مقصدِ تب‌ها همان `TABS` است — رفتار عوض نشده.
 */
@Composable
fun PhoneNavBar(
  tabs: List<NavTab>,
  current: String?,
  onPick: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = Shop.colors
  val shape = RoundedCornerShape(percent = 50)
  Row(
    modifier
      .fillMaxWidth()
      .windowInsetsPadding(WindowInsets.navigationBars)
      .padding(horizontal = 12.dp, vertical = 10.dp)
      .shadow(10.dp, shape, clip = false)
      .clip(shape)
      //  design v2: سایهٔ نرم به‌جای خطِ دورِ تیره
      .background(colors.surfaceSolid)
      .padding(4.dp)
      .selectableGroup(),
    horizontalArrangement = Arrangement.spacedBy(2.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    tabs.forEach { t ->
      NavPill(t, active = t.id == current) { onPick(t.id) }
    }
  }
}

@Composable
private fun RowScope.NavPill(tab: NavTab, active: Boolean, onClick: () -> Unit) {
  val colors = Shop.colors
  val pill = PhoneSheet.colors
  val ms = if (Motion.enabled) 260 else 0
  val bg by animateColorAsState(if (active) pill.navPill else Color.Transparent, tween(ms), label = "navPill")
  val fg by animateColorAsState(if (active) pill.onNavPill else colors.muted, tween(ms), label = "navFg")
  //  قرصِ فعال جای بیشتری می‌گیرد تا برچسب کامل جا شود
  val grow by animateFloatAsState(if (active) 2.6f else 1f, tween(ms), label = "navGrow")
  Row(
    Modifier
      .weight(grow)
      .height(52.dp)
      .clip(RoundedCornerShape(percent = 50))
      .background(bg)
      .selectable(selected = active, role = Role.Tab, onClick = onClick)
      .padding(horizontal = 10.dp),
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
      tab.icon,
      //  فعال: برچسبِ کنارش خوانده می‌شود؛ غیرفعال: همین نام است
      contentDescription = if (active) null else tab.label,
      tint = fg,
      modifier = Modifier.size(22.dp),
    )
    AnimatedVisibility(
      visible = active,
      enter = if (Motion.enabled) fadeIn(tween(200)) + expandHorizontally(tween(240)) else EnterTransition.None,
      exit = if (Motion.enabled) fadeOut(tween(120)) + shrinkHorizontally(tween(200)) else ExitTransition.None,
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.width(8.dp))
        Text(
          tab.label,
          style = MaterialTheme.typography.labelLarge,
          color = fg,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}
