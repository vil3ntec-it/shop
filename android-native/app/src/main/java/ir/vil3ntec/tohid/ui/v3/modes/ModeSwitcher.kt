package ir.vil3ntec.tohid.ui.v3.modes

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.nav.AppMode
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Section
import androidx.compose.foundation.layout.size

/**
 *  سوییچرِ حالت برای «حساب من»: فقط حالت‌هایی که این کاربر
 *  واقعاً دارد (`ModeState.available`) — حالتِ فعلی با تیکِ برند؛ زدنِ دیگری کلِ
 *  برنامه را با حرکت به آن حالت می‌برد (`MainActivity`). کسی که دکانی ندارد ردیفِ
 *  «دکان دارم» را می‌بیند که به ورود/ساختِ دکان می‌رود.
 */
@Composable
fun ModeSwitcher(modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val c = PhoneV3.colors
  val current = ModeState.current(context)
  val modes = remember(ModeState.chosen.value) { ModeState.available(context) }
  V3Section(title = StrKit.switchMode, modifier = modifier) {
    modes.forEachIndexed { i, mode ->
      if (i > 0) V3Divider(start = 64.dp)
      val look = lookOf(mode)
      V3ListRow(
        title = look.title(),
        subtitle = look.body(),
        icon = look.icon,
        hue = look.hue,
        trailing = if (mode == current) ({
          Icon(Icons.Outlined.Check, contentDescription = StrKit.currentMode, tint = PhoneV3Roles.brandText(c), modifier = Modifier.size(22.dp))
        }) else null,
        chevron = mode != current,
        onClick = if (mode == current) null else ({ ModeState.choose(context, mode) }),
      )
    }
    if (AppMode.SHOP !in modes) {
      V3Divider(start = 64.dp)
      V3ListRow(
        title = StrKit.openShopMode,
        icon = Icons.Outlined.Storefront,
        hue = lookOf(AppMode.SHOP).hue,
        onClick = { ModeState.startShop(context) },
      )
    }
  }
}
