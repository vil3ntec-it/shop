package ir.vil3ntec.tohid.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.ui.AppNav
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.v3.analytics.StatsPage
import ir.vil3ntec.tohid.ui.v3.nav.LocalV3Nav

/**
 *  «آمار» — صفحهٔ دومِ خانه. محتوا `StatsPage` ِ گروهِ گزارش (`ui/v3/analytics`) است؛ این
 *  پوستهٔ نازک فقط امضای امروزِ ورقه‌های خانه (`PhoneHomeScreen`) را نگه می‌دارد تا
 *  `HomePager` ِ پایه برسد (فاز ۳ پاک می‌شود).
 */
@Composable
fun PhoneStatsPage(d: ShopData, bottomReserve: Dp, pages: Int, currentPage: Int, big: Boolean = false) {
  val p = PhoneV3.colors
  val context = LocalContext.current
  val nav = LocalV3Nav.current
  val canSeeMoney = remember { ShopRole.canSeeMoney(context) }
  StatsPage(
    d = d,
    canSeeMoney = canSeeMoney,
    periodArg = null,
    onOpen = { path -> nav?.open(path) ?: AppNav.go(path) },
    bottomReserve = bottomReserve,
    embedded = true,
    dots = { PagerDots(pages, currentPage, p.ink, p.chevron) },
    big = big,
  )
}
