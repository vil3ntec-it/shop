package ir.vil3ntec.tohid.ui.v3.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.Dp

/**
 *  درزِ خانه ⇄ آمار (G10 ⇄ G6) بی import ِ صفحهٔ گروهِ دیگر:
 *  • `page`: صفحهٔ خواسته‌شدهٔ ورقِ خانه — ۰ = خانه (r7)، ۱ = آمار (r8). مسیرِ
 *    `stats?period=` می‌گذاردش؛ ورقِ خانه تا آن‌جا می‌رود و بعد null می‌کند.
 *  • `stats`: محتوای صفحهٔ آمار؛ G6 ثبتش می‌کند و ورقِ خانهٔ G10 صدایش می‌زند.
 */
object HomePager {
  val page = mutableStateOf<Int?>(null)
  var stats: (@Composable (bottomReserve: Dp, pages: Int, current: Int) -> Unit)? = null
}
